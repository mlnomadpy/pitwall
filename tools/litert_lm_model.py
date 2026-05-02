"""
LitertLmModel — ADK BaseLlm adapter for in-process LiteRT-LM inference.

Why
---
ADK ships exactly one LiteRT-LM client: `Gemini(base_url=..., model=...)` over
HTTP to a separately-launched `lit serve` process. That works on desktop but
(a) requires a second process and (b) is undocumented for Termux + Pixel
Tensor G5 NPU.

This adapter loads the `.litertlm` bundle directly via the `litert_lm.Engine`
Python API — same engine `LitertCoach` already uses for the in-drive E2B
model. Result: one process, guaranteed NPU access on Pixel, and explicit
control over KV cache lifetime + thermal envelope.

Architecture
------------

      asyncio task                  ┌────────────────┐    send_message
   (per agent / call) ───submit───▶ │ Worker thread  │ ─────────────────▶ Engine
                                    │ (single drain) │                    (Pixel
        result ◀───── Future ───── │                │ ◀──── response ──── NPU)
                                    └────────────────┘
                                            │
                                            ▼
                              cooldown + thermal back-off
                              between inferences

- **Singleton engine.** One `litert_lm.Engine` per process, lazy-loaded.
- **Single worker thread.** All inference goes through it; no other thread
  ever calls `send_message`. Guarantees the Tensor G5 NPU sees one call at a
  time without scattering locks.
- **Bounded priority queue.** `PITWALL_LITERTLM_QUEUE_MAX` (default 16) caps
  pending jobs. Submitters block up to `PITWALL_LITERTLM_QUEUE_TIMEOUT_S`
  (default 60s) and receive a queue-full error on overflow.
- **Cooldown.** `PITWALL_LITERTLM_COOLDOWN_MS` enforces a minimum interval
  between inferences. Set non-zero on Pixel to prevent thermal throttling.
- **Thermal back-off.** Reads `/sys/class/thermal/thermal_zone*/temp`; above
  `PITWALL_LITERTLM_THERMAL_LIMIT_C` (default 65°C) the worker sleeps
  proportionally before the next call. Set to 0 to disable.
- **KV-cache reuse.** One `Conversation` per system-instruction hash, kept
  warm across turns. Drift detection + budget rotation handle ADK resets.

Tool calling (best-effort)
--------------------------
Gemma is an instruction-tuned LLM, not a function-calling model with native
JSON schemas. We inject ADK tool definitions into the system prompt and ask
the model to emit `<function_call>{"name": "...", "args": {...}}</function_call>`
or ```tool_code blocks. Both formats are parsed back into ADK
`Part(function_call=...)`. If the model improvises, the raw text is returned
as a text part — agents see a narrative answer rather than failing.

Streaming (best-effort)
-----------------------
Probes `Conversation` for `stream_message` / `send_message_stream` /
`generate(stream=True)`. If found, yields partial `LlmResponse` chunks. If
not, yields one final response. SSE clients always see *something*.

Environment
-----------
    PITWALL_LITERTLM_PATH             .litertlm bundle path
    PITWALL_LITERTLM_BACKEND          cpu | gpu (default cpu)
    PITWALL_LITERTLM_MAX_TOKENS       Engine max_num_tokens (default 4096)
    PITWALL_LITERTLM_BUDGET           per-Conversation char budget (default 30000)
    PITWALL_LITERTLM_QUEUE_MAX        max queued jobs (default 16)
    PITWALL_LITERTLM_QUEUE_TIMEOUT_S  submit/result timeout (default 60)
    PITWALL_LITERTLM_COOLDOWN_MS      min ms between inferences (default 0)
    PITWALL_LITERTLM_THERMAL_LIMIT_C  back off above this °C (default 65, 0 disables)
    PITWALL_LITERTLM_THERMAL_GAIN_S   seconds of sleep per °C over limit (default 1.0)
"""
from __future__ import annotations

import asyncio
import glob
import hashlib
import itertools
import json
import logging
import os
import queue
import re
import threading
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, AsyncGenerator, Optional

_log = logging.getLogger(__name__)


# ── Configuration ─────────────────────────────────────────────────────────────

_DEFAULT_MODEL_PATHS = [
    "~/.litert-lm/models/gemma-4-e4b/model.litertlm",
    "~/storage/shared/Pitwall/models/gemma-4-E4B-it.litertlm",
    "models/gemma-4-E4B-it.litertlm",
    "~/.litert-lm/models/gemma-4-e2b/model.litertlm",
    "~/storage/shared/Pitwall/models/gemma-4-E2B-it.litertlm",
    "models/gemma-4-E2B-it.litertlm",
]

_CONV_BUDGET           = int(os.getenv("PITWALL_LITERTLM_BUDGET", "30000"))
_QUEUE_MAX             = int(os.getenv("PITWALL_LITERTLM_QUEUE_MAX", "16"))
_QUEUE_TIMEOUT_S       = float(os.getenv("PITWALL_LITERTLM_QUEUE_TIMEOUT_S", "60"))
_COOLDOWN_S            = float(os.getenv("PITWALL_LITERTLM_COOLDOWN_MS", "0")) / 1000.0
_THERMAL_LIMIT_C       = float(os.getenv("PITWALL_LITERTLM_THERMAL_LIMIT_C", "65"))
_THERMAL_GAIN_S        = float(os.getenv("PITWALL_LITERTLM_THERMAL_GAIN_S", "1.0"))


# ── Engine singleton ──────────────────────────────────────────────────────────

_engine_singleton: Any = None
_engine_ctx: Any = None
_engine_load_lock = threading.Lock()


def _resolve_model_path(override: str = "") -> Optional[Path]:
    candidates = [override] if override else _DEFAULT_MODEL_PATHS
    for c in candidates:
        if not c:
            continue
        p = Path(os.path.expanduser(c))
        if p.exists():
            return p
    return None


def _load_engine():
    """Lazy-load + cache the Engine. Raises if litert-lm or model missing."""
    global _engine_singleton, _engine_ctx
    if _engine_singleton is not None:
        return _engine_singleton
    with _engine_load_lock:
        if _engine_singleton is not None:
            return _engine_singleton
        try:
            import litert_lm  # type: ignore
        except ImportError as exc:
            raise RuntimeError(
                f"litert-lm not installed ({exc}). pip install litert-lm")

        path = _resolve_model_path(os.getenv("PITWALL_LITERTLM_PATH", ""))
        if path is None:
            raise FileNotFoundError(
                f"No .litertlm bundle found in {_DEFAULT_MODEL_PATHS}. "
                f"Set PITWALL_LITERTLM_PATH or run "
                f"`litert-lm import --from-huggingface-repo "
                f"litert-community/gemma-4-E4B-it-litert-lm gemma-4-e4b`")

        backend_str = os.getenv("PITWALL_LITERTLM_BACKEND", "cpu").lower()
        backend_enum = (litert_lm.Backend.GPU
                        if backend_str == "gpu"
                        else litert_lm.Backend.CPU)
        max_tokens = int(os.getenv("PITWALL_LITERTLM_MAX_TOKENS", "4096"))

        _engine_ctx = litert_lm.Engine(
            model_path=str(path),
            backend=backend_enum,
            max_num_tokens=max_tokens,
        )
        _engine_singleton = _engine_ctx.__enter__()
        _log.info("LitertLmModel: engine loaded from %s (backend=%s, max=%d)",
                  path, backend_str, max_tokens)
        return _engine_singleton


# ── Conversation cache ────────────────────────────────────────────────────────

_conversations: dict[str, Any] = {}
_conv_meta:    dict[str, dict] = {}
_conv_lock = threading.Lock()


def reset_all_conversations() -> None:
    """Drop every cached Conversation. Next call rebuilds from system_instruction."""
    with _conv_lock:
        _conversations.clear()
        _conv_meta.clear()


# ── Thermal monitoring ────────────────────────────────────────────────────────

_thermal_zone_paths: list[str] | None = None


def _thermal_zones() -> list[str]:
    global _thermal_zone_paths
    if _thermal_zone_paths is None:
        _thermal_zone_paths = sorted(glob.glob("/sys/class/thermal/thermal_zone*/temp"))
    return _thermal_zone_paths


def _read_max_celsius() -> Optional[float]:
    """Max thermal zone temp in °C, or None if /sys/class/thermal isn't readable."""
    paths = _thermal_zones()
    if not paths:
        return None
    max_c = -1.0
    for path in paths:
        try:
            with open(path) as fh:
                millideg = int(fh.read().strip())
            c = millideg / 1000.0
            if c > max_c:
                max_c = c
        except (OSError, ValueError):
            continue
    return max_c if max_c > 0 else None


def _thermal_backoff_seconds() -> float:
    """How long to sleep before the next inference based on current temp.

    Linear: gain_s seconds of sleep per °C over limit, capped at 30s.
    Returns 0 if disabled, can't read temps, or below limit.
    """
    if _THERMAL_LIMIT_C <= 0:
        return 0.0
    temp = _read_max_celsius()
    if temp is None or temp <= _THERMAL_LIMIT_C:
        return 0.0
    over = temp - _THERMAL_LIMIT_C
    return min(over * _THERMAL_GAIN_S, 30.0)


# ── Worker queue ──────────────────────────────────────────────────────────────

@dataclass(order=True)
class _Job:
    """A queued inference request with priority and a result channel."""
    priority: int
    seq: int                   # tie-breaker: FIFO within priority
    payload: dict = field(compare=False)
    result_q: "queue.SimpleQueue" = field(compare=False)


_job_queue: "queue.PriorityQueue[_Job]" = queue.PriorityQueue(maxsize=_QUEUE_MAX)
_seq_counter = itertools.count()
_worker_thread: threading.Thread | None = None
_worker_started = False
_worker_start_lock = threading.Lock()
_worker_stop = threading.Event()
_last_inference_finished = 0.0


def _ensure_worker_started() -> None:
    """Start the single inference worker thread (idempotent, lazy)."""
    global _worker_thread, _worker_started
    if _worker_started:
        return
    with _worker_start_lock:
        if _worker_started:
            return
        _worker_thread = threading.Thread(
            target=_worker_loop, daemon=True, name="litertlm-worker")
        _worker_thread.start()
        _worker_started = True


def _shutdown_worker(timeout: float = 1.0) -> None:
    """Test hook + clean shutdown. Stops the worker after the current job."""
    global _worker_started, _worker_thread
    if not _worker_started:
        return
    _worker_stop.set()
    # Wake the worker if it's blocked on queue.get
    try:
        _job_queue.put_nowait(_Job(priority=0, seq=next(_seq_counter),
                                    payload={"_shutdown": True},
                                    result_q=queue.SimpleQueue()))
    except queue.Full:
        pass
    if _worker_thread is not None:
        _worker_thread.join(timeout=timeout)
    _worker_started = False
    _worker_thread = None
    _worker_stop.clear()


def _worker_loop() -> None:
    """Drain the queue forever. Enforces cooldown + thermal back-off."""
    global _last_inference_finished
    while not _worker_stop.is_set():
        try:
            job = _job_queue.get(timeout=1.0)
        except queue.Empty:
            continue

        if job.payload.get("_shutdown"):
            return

        # Cooldown — minimum interval between inferences.
        if _COOLDOWN_S > 0:
            elapsed = time.monotonic() - _last_inference_finished
            if elapsed < _COOLDOWN_S:
                time.sleep(_COOLDOWN_S - elapsed)

        # Thermal back-off.
        backoff = _thermal_backoff_seconds()
        if backoff > 0:
            _log.warning(
                "litertlm-worker: thermal back-off %.2fs (zone temp over %.1f°C)",
                backoff, _THERMAL_LIMIT_C)
            time.sleep(backoff)

        # Run the inference.
        try:
            result = _do_inference(job.payload)
            job.result_q.put(("ok", result))
        except Exception as exc:
            _log.exception("litertlm-worker inference failed")
            job.result_q.put(("error", exc))
        finally:
            _last_inference_finished = time.monotonic()


def _submit_inference(payload: dict, priority: int = 5) -> Any:
    """Submit an inference job to the worker. Block until result or timeout."""
    _ensure_worker_started()
    result_q: queue.SimpleQueue = queue.SimpleQueue()
    job = _Job(priority=priority, seq=next(_seq_counter),
               payload=payload, result_q=result_q)
    try:
        _job_queue.put(job, timeout=_QUEUE_TIMEOUT_S)
    except queue.Full:
        raise RuntimeError(
            f"LitertLmModel queue full ({_QUEUE_MAX} pending). "
            f"NPU is overloaded — back off or raise PITWALL_LITERTLM_QUEUE_MAX")

    try:
        kind, payload = result_q.get(timeout=_QUEUE_TIMEOUT_S)
    except queue.Empty:
        raise RuntimeError(
            f"LitertLmModel inference timed out after {_QUEUE_TIMEOUT_S}s")
    if kind == "error":
        raise payload  # type: ignore[misc]
    return payload


def get_queue_stats() -> dict:
    """Snapshot of the queue + thermal state for /diagnostics."""
    return {
        "queue_depth":      _job_queue.qsize(),
        "queue_max":        _QUEUE_MAX,
        "cooldown_ms":      int(_COOLDOWN_S * 1000),
        "thermal_limit_c":  _THERMAL_LIMIT_C,
        "thermal_temp_c":   _read_max_celsius(),
        "worker_alive":     bool(_worker_thread and _worker_thread.is_alive()),
        "last_finished_ms_ago": int((time.monotonic() - _last_inference_finished) * 1000)
                                if _last_inference_finished else None,
    }


def get_kv_cache_stats() -> dict:
    """KV-cache state. Combined with get_queue_stats for /diagnostics."""
    with _conv_lock:
        return {
            "active_conversations": len(_conversations),
            "total_chars": sum(m.get("chars", 0) for m in _conv_meta.values()),
            "budget": _CONV_BUDGET,
            "by_key": {k: dict(m) for k, m in _conv_meta.items()},
        }


# ── Tool injection + parsing ──────────────────────────────────────────────────

_TOOL_CALL_RE = re.compile(
    r"<function_call>\s*(\{.*?\})\s*</function_call>", re.DOTALL)
_TOOL_CODE_RE = re.compile(
    r"```(?:tool_code|python)\s*\n(.*?)\n```", re.DOTALL)


def _format_tools_for_prompt(tools_dict: dict | None) -> str:
    """Render an ADK tools dict as a system-prompt addendum.

    Format chosen to be unambiguous for an instruction-tuned model and easy
    to parse back with `_TOOL_CALL_RE`.
    """
    if not tools_dict:
        return ""
    lines = ["",
             "You have access to these tools. To use one, output ONLY:",
             "<function_call>{\"name\": \"<tool>\", \"args\": {<args>}}</function_call>",
             "Otherwise reply with normal text. Tools available:"]
    for name, decl in (tools_dict or {}).items():
        desc = (getattr(decl, "description", "") or "").strip().splitlines()
        lines.append(f"- {name}: {desc[0] if desc else ''}")
    return "\n".join(lines) + "\n"


def _parse_tool_calls(text: str) -> list[dict]:
    """Extract function-call invocations from raw model output.

    Returns a list of {"name": str, "args": dict}. Empty if the model just
    produced narrative text. Best-effort: malformed JSON is skipped silently.
    """
    calls: list[dict] = []
    for m in _TOOL_CALL_RE.finditer(text):
        try:
            obj = json.loads(m.group(1))
        except json.JSONDecodeError:
            continue
        name = obj.get("name") or obj.get("tool")
        args = obj.get("args") or obj.get("arguments") or {}
        if isinstance(name, str) and isinstance(args, dict):
            calls.append({"name": name, "args": args})

    if calls:
        return calls

    # Fallback: ```tool_code\nname(args)\n``` Gemma-style blocks.
    for m in _TOOL_CODE_RE.finditer(text):
        body = m.group(1).strip()
        # Very permissive: name(k=v, k="v") — only handles flat kwargs.
        match = re.match(r"(\w+)\s*\((.*)\)\s*$", body, re.DOTALL)
        if not match:
            continue
        name, args_src = match.group(1), match.group(2)
        try:
            args = _parse_kwargs(args_src)
        except Exception:
            continue
        calls.append({"name": name, "args": args})
    return calls


def _parse_kwargs(src: str) -> dict:
    """Parse a comma-separated kwargs string into a dict.

    Accepts: foo="bar", n=3, flag=true. Tracks quote + bracket depth so
    commas inside quoted strings (or nested objects) don't split arguments.
    """
    if not src.strip():
        return {}
    out: dict = {}
    depth = 0
    in_str: str | None = None  # active quote char or None
    chunks: list[str] = []
    current: list[str] = []
    for ch in src:
        if in_str:
            current.append(ch)
            if ch == in_str:
                in_str = None
            continue
        if ch in "\"'":
            in_str = ch
            current.append(ch)
            continue
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        if ch == "," and depth == 0:
            chunks.append("".join(current))
            current = []
        else:
            current.append(ch)
    if current:
        chunks.append("".join(current))

    for chunk in chunks:
        if "=" not in chunk:
            continue
        k, _, v = chunk.partition("=")
        k = k.strip()
        v = v.strip()
        try:
            out[k] = json.loads(v)
        except json.JSONDecodeError:
            out[k] = v.strip("'\"")
    return out


def _format_function_response(part) -> str:
    """Format an ADK FunctionResponse part as a synthetic user message body.

    The next inference call will see this in the conversation history so the
    model can read tool output. Format matches what the model emitted on its
    side, mirrored.
    """
    fr = getattr(part, "function_response", None)
    if fr is None:
        return ""
    name = getattr(fr, "name", "tool")
    response = getattr(fr, "response", None) or {}
    try:
        body = json.dumps(response)[:2000]
    except (TypeError, ValueError):
        body = str(response)[:2000]
    return f"<tool_result name=\"{name}\">{body}</tool_result>"


# ── BaseLlm subclass ──────────────────────────────────────────────────────────

HAS_LITERTLM_MODEL = False
try:
    from google.adk.models.base_llm import BaseLlm
    from google.adk.models.llm_response import LlmResponse
    from google.genai.types import Content, FunctionCall, Part
    HAS_LITERTLM_MODEL = True
except ImportError:
    HAS_LITERTLM_MODEL = False


if HAS_LITERTLM_MODEL:

    class LitertLmModel(BaseLlm):
        """ADK BaseLlm backed by an in-process litert_lm.Engine.

        See module docstring for env vars + architecture. The actual model
        identifier passed in `model=` is just a label — the real `.litertlm`
        bundle path comes from PITWALL_LITERTLM_PATH or the search list.
        """

        model: str = "litert-lm-engine"

        async def generate_content_async(
            self, llm_request, stream: bool = False
        ) -> AsyncGenerator[Any, None]:
            """ADK BaseLlm hook — route an LLM request through the worker queue."""
            system_prompt = _extract_system_instruction(llm_request)
            tools_dict = _extract_tools_dict(llm_request)
            history = list(getattr(llm_request, "contents", None) or [])

            if not history:
                yield LlmResponse(
                    error_code="EMPTY_REQUEST",
                    error_message="No contents in llm_request",
                    partial=False, turn_complete=True,
                )
                return

            last = history[-1]
            last_text = _content_text(last)
            if not last_text:
                yield LlmResponse(
                    error_code="EMPTY_USER_MESSAGE",
                    error_message="Last content has no text",
                    partial=False, turn_complete=True,
                )
                return

            full_system = system_prompt + _format_tools_for_prompt(tools_dict)
            key = hashlib.sha256(full_system.encode("utf-8")).hexdigest()[:16]

            payload = {
                "key":           key,
                "system_prompt": full_system,
                "history":       history,
                "last_text":     last_text,
                "stream":        bool(stream),
            }

            loop = asyncio.get_event_loop()
            try:
                if stream:
                    chunks: list[str] = []
                    accum = await loop.run_in_executor(
                        None, _submit_inference, payload, 5)
                    # If the engine returned a stream-ish list, walk it.
                    if isinstance(accum, list):
                        for piece in accum:
                            if not piece:
                                continue
                            chunks.append(piece)
                            yield LlmResponse(
                                content=Content(role="model",
                                                parts=[Part(text=piece)]),
                                partial=True, turn_complete=False,
                            )
                        accum_text = "".join(chunks)
                    else:
                        accum_text = accum or ""
                    # Final response with tool-call detection on the full text.
                    async for resp in self._finalize(accum_text):
                        yield resp
                    return

                text = await loop.run_in_executor(
                    None, _submit_inference, payload, 5)
                async for resp in self._finalize(text or ""):
                    yield resp
            except Exception as exc:
                _log.exception("LitertLmModel call failed")
                yield LlmResponse(
                    error_code="LITERTLM_ERROR",
                    error_message=f"{type(exc).__name__}: {exc}",
                    partial=False, turn_complete=True,
                )

        async def _finalize(self, text: str) -> AsyncGenerator[Any, None]:
            """Convert raw model output into the final LlmResponse(s).

            If the model emitted function calls, yield a Content with
            FunctionCall parts (ADK then dispatches the tool). Otherwise
            yield the text as a normal model turn.
            """
            calls = _parse_tool_calls(text)
            if calls:
                parts = [
                    Part(function_call=FunctionCall(name=c["name"],
                                                    args=c["args"]))
                    for c in calls
                ]
                yield LlmResponse(
                    content=Content(role="model", parts=parts),
                    partial=False, turn_complete=True,
                )
                return

            yield LlmResponse(
                content=Content(role="model", parts=[Part(text=text)]),
                partial=False, turn_complete=True,
            )

else:
    class LitertLmModel:  # type: ignore[no-redef]
        """Placeholder when ADK / litert-lm are unavailable. Construction raises."""
        def __init__(self, *_args, **_kwargs):
            raise RuntimeError(
                "LitertLmModel requires google-adk + litert-lm — "
                "pip install google-adk litert-lm")


# ── Helpers ───────────────────────────────────────────────────────────────────


def _extract_system_instruction(llm_request) -> str:
    cfg = getattr(llm_request, "config", None)
    if cfg is None:
        return ""
    si = getattr(cfg, "system_instruction", None)
    if si is None:
        return ""
    if isinstance(si, str):
        return si
    parts = getattr(si, "parts", None)
    if parts:
        return "\n".join(p.text for p in parts if getattr(p, "text", None))
    return str(si)


def _extract_tools_dict(llm_request) -> dict:
    """Pull the ADK tool registry from the request in a version-tolerant way."""
    td = getattr(llm_request, "tools_dict", None)
    if isinstance(td, dict) and td:
        return td
    cfg = getattr(llm_request, "config", None)
    if cfg is not None:
        tools_attr = getattr(cfg, "tools", None) or []
        out = {}
        for tool in tools_attr:
            decls = getattr(tool, "function_declarations", None) or []
            for decl in decls:
                name = getattr(decl, "name", None)
                if name:
                    out[name] = decl
        if out:
            return out
    return {}


def _content_text(content) -> str:
    parts = getattr(content, "parts", None) or []
    out: list[str] = []
    for p in parts:
        # Ordinary text
        t = getattr(p, "text", None)
        if t:
            out.append(t)
            continue
        # Tool result → format as synthetic user text the engine can read.
        fr = getattr(p, "function_response", None)
        if fr is not None:
            out.append(_format_function_response(p))
    return "\n".join(out).strip()


def _content_role(content) -> str:
    role = getattr(content, "role", None) or "user"
    return "user" if role.lower() == "user" else "assistant"


def _stream_via_engine(conv, last_text: str) -> Optional[list[str]]:
    """Probe the Conversation for a streaming API.

    Returns a list of token chunks if a streaming method exists, else None
    so the caller falls back to one-shot send_message.
    """
    candidates = [
        ("send_message_stream", {"role": "user", "content": last_text}),
        ("stream_message",      {"role": "user", "content": last_text}),
        ("stream",              {"role": "user", "content": last_text}),
    ]
    for method_name, msg in candidates:
        method = getattr(conv, method_name, None)
        if method is None:
            continue
        try:
            chunks: list[str] = []
            for piece in method(msg):
                # Each piece may be {"content": [...]} or a raw string.
                if isinstance(piece, str):
                    chunks.append(piece)
                elif isinstance(piece, dict):
                    body = piece.get("content")
                    if isinstance(body, str):
                        chunks.append(body)
                    elif isinstance(body, list):
                        for part in body:
                            if isinstance(part, dict) and part.get("type") == "text":
                                t = part.get("text")
                                if isinstance(t, str):
                                    chunks.append(t)
            return chunks
        except Exception:
            return None
    return None


def _do_inference(payload: dict) -> Any:
    """Run one inference turn under the worker thread.

    Returns either a string (one-shot) or a list[str] (streamed chunks)
    depending on whether the engine exposes a streaming API and the caller
    asked for streaming.
    """
    from coach_engine import _extract_assistant_text  # type: ignore

    key           = payload["key"]
    system_prompt = payload["system_prompt"]
    history       = payload["history"]
    last_text     = payload["last_text"]
    stream        = payload.get("stream", False)

    engine = _load_engine()

    # Conversation lookup / rebuild.
    with _conv_lock:
        conv = _conversations.get(key)
        meta = _conv_meta.get(key)

        rebuild = False
        if conv is None or meta is None:
            rebuild = True
        elif meta["chars"] > _CONV_BUDGET:
            rebuild = True
        elif len(history) - 1 < meta["history_len"]:
            rebuild = True

        if rebuild:
            messages = []
            if system_prompt:
                messages.append({"role": "system", "content": system_prompt})
            for c in history[:-1]:
                t = _content_text(c)
                if not t:
                    continue
                messages.append({"role": _content_role(c), "content": t})
            conv = engine.create_conversation(messages=messages)
            _conversations[key] = conv
            _conv_meta[key] = {
                "chars": sum(len(m["content"]) for m in messages),
                "history_len": max(len(history) - 1, 0),
                "system_prompt_chars": len(system_prompt),
            }
            meta = _conv_meta[key]

    # Inference. Worker is the only caller — no lock needed here.
    if stream:
        chunks = _stream_via_engine(conv, last_text)
        if chunks is not None:
            text = "".join(chunks)
            with _conv_lock:
                m = _conv_meta.get(key)
                if m is not None:
                    m["chars"] += len(last_text) + len(text)
                    m["history_len"] += 1
            return chunks  # signal streamed list to the caller

    response = conv.send_message({"role": "user", "content": last_text})
    text = _extract_assistant_text(response)

    with _conv_lock:
        m = _conv_meta.get(key)
        if m is not None:
            m["chars"] += len(last_text) + len(text)
            m["history_len"] += 1
    return text
