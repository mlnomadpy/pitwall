"""bridge.bp_diagnostics — Blueprint: LLM friction + CAN state."""

import json
from flask import Blueprint, request, jsonify
from pitwall.state import state
from pitwall.db import get_db

bp = Blueprint("diagnostics", __name__)


@bp.route("/diagnostics/llm_friction", methods=["GET"])
def diagnostics_llm_friction():
    """ADR-018: surface LitertCoach edge friction."""
    if not state.has_duckdb:
        return jsonify({"error": "duckdb not available"}), 503
    sid = (request.args.get("session_id") or "").strip()
    role = (request.args.get("role") or "").strip()
    try:
        limit = max(1, min(int(request.args.get("limit", 100)), 1000))
    except ValueError:
        limit = 100
    try:
        since_min = float(request.args.get("since_minutes", 0) or 0)
    except ValueError:
        since_min = 0.0
    where = []
    params: list = []
    if sid:
        where.append("session_id = ?"); params.append(sid)
    if role:
        where.append("role = ?"); params.append(role)
    if since_min > 0:
        where.append("ts >= now() - INTERVAL (?) MINUTE"); params.append(since_min)
    where_sql = (" WHERE " + " AND ".join(where)) if where else ""
    with state.db_lock:
        conn = get_db()
        if conn is None:
            return jsonify({"error": "duckdb not available"}), 503
        try:
            rows = conn.execute(
                f"""SELECT id, session_id, role, mode, backend,
                          prompt_chars, completion_chars, latency_ms,
                          truncated, fell_back, error, emotion, ts
                   FROM llm_friction {where_sql} ORDER BY ts DESC LIMIT ?""",
                [*params, limit]).fetchall()
            agg_overall = conn.execute(
                f"""SELECT COUNT(*), quantile_cont(latency_ms, 0.5),
                       quantile_cont(latency_ms, 0.95),
                       AVG(CASE WHEN error IS NOT NULL AND error <> '' THEN 1.0 ELSE 0.0 END),
                       AVG(CASE WHEN fell_back THEN 1.0 ELSE 0.0 END),
                       AVG(CASE WHEN truncated THEN 1.0 ELSE 0.0 END)
                   FROM llm_friction {where_sql}""", params).fetchone()
            agg_by_role = conn.execute(
                f"""SELECT role, COUNT(*), quantile_cont(latency_ms, 0.5),
                       AVG(CASE WHEN fell_back THEN 1.0 ELSE 0.0 END)
                   FROM llm_friction {where_sql} GROUP BY role ORDER BY role""",
                params).fetchall()
        finally:
            conn.close()
    out_rows = [
        {"id": r[0], "session_id": r[1], "role": r[2], "mode": r[3],
         "backend": r[4], "prompt_chars": r[5], "completion_chars": r[6],
         "latency_ms": float(r[7]) if r[7] is not None else 0.0,
         "truncated": bool(r[8]), "fell_back": bool(r[9]),
         "error": r[10] or "", "emotion": r[11] or "",
         "ts": str(r[12]) if r[12] is not None else ""}
        for r in rows
    ]
    n, p50, p95, err_rate, fb_rate, trunc_rate = agg_overall or (0, None, None, 0, 0, 0)
    return jsonify({
        "count": int(n or 0),
        "p50_latency_ms": float(p50) if p50 is not None else None,
        "p95_latency_ms": float(p95) if p95 is not None else None,
        "error_rate": float(err_rate or 0.0),
        "fallback_rate": float(fb_rate or 0.0),
        "truncation_rate": float(trunc_rate or 0.0),
        "by_role": [{"role": r[0], "count": int(r[1]),
                     "p50_latency_ms": float(r[2]) if r[2] is not None else None,
                     "fallback_rate": float(r[3] or 0.0)} for r in agg_by_role],
        "rows": out_rows,
    })


# ── CAN state ─────────────────────────────────────────────────────────────────

_USB_CAN_DEVICE_DB: dict[tuple[str, str], dict] = {
    ("1d50", "606f"): {"model": "CANable / OpenLink", "kind": "slcan"},
    ("1d50", "604b"): {"model": "Korlan USB2CAN", "kind": "slcan"},
    ("2341", "8051"): {"model": "Macchina M2", "kind": "slcan"},
    ("0c72", "000c"): {"model": "PEAK PCAN-USB", "kind": "pcan"},
    ("0bfd", "0117"): {"model": "Kvaser USBcan", "kind": "kvaser"},
    ("0403", "6001"): {"model": "FTDI USB-serial (ELM327?)", "kind": "obd2"},
    ("1a86", "7523"): {"model": "CH340 USB-serial (clone)", "kind": "slcan"},
}


def _detect_usb_can_devices() -> list[dict]:
    """Enumerate currently-connected USB serial devices that look like CAN adapters."""
    try:
        from serial.tools import list_ports
    except ImportError:
        return []
    out: list[dict] = []
    for p in list_ports.comports():
        vid = f"{p.vid:04x}" if p.vid else None
        pid = f"{p.pid:04x}" if p.pid else None
        match = _USB_CAN_DEVICE_DB.get((vid, pid)) if vid and pid else None
        likely_can = bool(match) or (
            p.device.startswith(("/dev/ttyACM", "/dev/ttyUSB"))
            or "ACM" in (p.device or ""))
        if not likely_can:
            continue
        out.append({
            "device": p.device,
            "vid": f"0x{vid}" if vid else None,
            "pid": f"0x{pid}" if pid else None,
            "description": p.description or "",
            "manufacturer": p.manufacturer or "",
            "model": match["model"] if match else "Unknown serial device",
            "kind": match["kind"] if match else "unknown",
            "is_known": bool(match),
        })
    return out


def can_state_snapshot() -> dict:
    """Snapshot for the Pit Stall Setup screen."""
    if state.can_reader is None:
        reader_state = {
            "loaded": False, "connected": False, "interface": None,
            "channel": None, "bitrate": None, "session_id": None,
            "frames_total": 0, "frames_unknown": 0,
            "frames_per_second": 0.0, "last_frame_age_s": None,
            "unknown_ids": [],
        }
    else:
        reader_state = state.can_reader.state()
    reader_state["usb_devices"] = _detect_usb_can_devices()
    return reader_state
