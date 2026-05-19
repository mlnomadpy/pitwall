---
hide:
  - navigation
  - toc
---

<div class="gds-page" markdown>

<div class="gds-hero" markdown>

<span class="eyebrow">Built with the Google developer stack</span>

# Pitwall runs on **<span class="grad">on-device AI</span>**, shipped with Google's developer tools.

<p class="lede">
Pitwall is a real-time AI racing coach that ingests CAN telemetry over USB and
coaches a track-day driver in-cabin — at 130 mph, with no cloud in the loop.
The bridge, the agents, the on-device LLM, the Android stack, and the IDE
that built it: every layer is part of Google's developer ecosystem.
</p>

<div class="cta-row" markdown>
[Read the architecture](architecture.md){ .gds-btn .gds-btn-primary }
[See the ADK topology](adk-agent-architecture.md){ .gds-btn .gds-btn-ghost }
[Project explainer](https://storage.googleapis.com/pitwall-demo/pitwall-explainer.html){ .gds-btn .gds-btn-ghost }
</div>

<div class="gds-chips" markdown>
<span class="gds-chip blue"><span class="dot"></span>Antigravity IDE</span>
<span class="gds-chip red"><span class="dot"></span>Gemini CLI</span>
<span class="gds-chip yellow"><span class="dot"></span>Android Studio</span>
<span class="gds-chip green"><span class="dot"></span>Jetpack Compose</span>
<span class="gds-chip blue"><span class="dot"></span>Google ADK</span>
<span class="gds-chip green"><span class="dot"></span>Gemma 4 · LiteRT-LM</span>
<span class="gds-chip red"><span class="dot"></span>LocalLLM (on-phone)</span>
</div>

</div>

## The stack at a glance

<div class="gds-grid" markdown>

<div class="gds-card blue" markdown>
<div class="role">Agentic IDE</div>
### Antigravity
The agentic IDE used to author the bridge, refactor 21 ADRs of architecture, and pair-build the 18-agent backend.
</div>

<div class="gds-card red" markdown>
<div class="role">Developer CLI</div>
### Gemini CLI
Terminal-native model access for one-off codegen, doc rewrites, and design-doc reviews across 54 PWA spec files.
</div>

<div class="gds-card yellow" markdown>
<div class="role">Android tooling</div>
### Android Studio
Builds the v1 Pixel 10 client, profiles the on-device coach, and packages the foreground service for Termux deployment.
</div>

<div class="gds-card green" markdown>
<div class="role">Android UI</div>
### Jetpack Compose
Declarative Kotlin UI for the in-cabin coach surface — 42 composables across the v1 app: HUD, coach card, telemetry panels.
</div>

<div class="gds-card blue" markdown>
<div class="role">Multi-agent runtime</div>
### Google ADK
Powers the paddock tier: 18 specialist agents, 15 SQL-safe tools, **pluggable local-LLM backend** (in-process, `lit serve`, or any OpenAI-compatible server — Ollama, LM Studio, llama.cpp, vLLM).
</div>

<div class="gds-card green" markdown>
<div class="role">On-device inference</div>
### Gemma 4 + LiteRT-LM
Gemma 4 E2B in the warm path (&lt;100 ms cues), Gemma 4 E4B in the paddock — both running on the Pixel 10's Tensor G5 NPU.
</div>

<div class="gds-card red" markdown>
<div class="role">On-phone LLM server</div>
### [LocalLLM](https://www.tahabouhsine.com/localllm/)
Sibling Apache-2.0 Android APK that hosts LiteRT-LM in a native Android process and exposes an OpenAI-compatible HTTP server on `127.0.0.1:8099/v1`. Pitwall's paddock tier dials it over localhost.
</div>

</div>

---

## How each piece earns its keep

<div class="gds-feature blue" markdown>

<div class="badge">Antigravity</div>

<div markdown>
<span class="role-tag">Agentic IDE · used to build Pitwall</span>
## The IDE that built the bridge

Antigravity is Google's agentic development environment — the IDE that drove the
day-to-day build of Pitwall: 56 endpoints across the Flask bridge, 21 architecture
decision records, and the multi-agent ADK refactor that landed in three back-to-back
ADRs (019 → 020 → 021).

Where most agentic tools lose the plot on a multi-day refactor, Antigravity kept the
plan, the diff, and the test loop in one frame. The ADK rewrite shipped **ahead of
the original post-Sonoma schedule** because the IDE could hold the topology in head.

<div class="stat-row" markdown>
<div class="stat"><span class="num">56</span><span class="lbl">HTTP endpoints</span></div>
<div class="stat"><span class="num">21</span><span class="lbl">ADRs authored</span></div>
<div class="stat"><span class="num">358</span><span class="lbl">tests passing</span></div>
</div>
</div>

</div>

<div class="gds-feature red" markdown>

<div class="badge">Gemini&nbsp;CLI</div>

<div markdown>
<span class="role-tag">Developer CLI · workflow glue</span>
## Model access where the developer already lives — the terminal

Gemini CLI is the everyday companion to Antigravity: terminal-native access to
Google models for the small, fast tasks. Generating canonical phrase variants for
the hot-path coach. Rewriting an ADR for clarity. Sweeping the 54-file Vue PWA
design corpus for inconsistencies before sign-off.

It belongs in the same toolchain as `git` and `pytest` — and it gets used the
same way.

<div class="stat-row" markdown>
<div class="stat"><span class="num">54</span><span class="lbl">PWA spec files reviewed</span></div>
<div class="stat"><span class="num">38</span><span class="lbl">screen specs polished</span></div>
</div>
</div>

</div>

<div class="gds-feature yellow" markdown>

<div class="badge">Android&nbsp;Studio</div>

<div markdown>
<span class="role-tag">Android tooling · profile + ship</span>
## From APK to track day

Android Studio is the entry point to everything that runs on the Pixel 10 in the
car: the v1 native app, the Compose UI, the LLM service, and the Termux
foreground-service package that hosts the bridge through a four-hour session.

When the Tensor G5 NPU needs to be profiled, when the wake-lock behaviour
needs to be debugged at 4 a.m. before a track day, when the APK needs to be
signed and sideloaded — Android Studio is where it happens.

<div class="stat-row" markdown>
<div class="stat"><span class="num">Pixel 10</span><span class="lbl">target hardware</span></div>
<div class="stat"><span class="num">Tensor G5</span><span class="lbl">NPU profiled</span></div>
<div class="stat"><span class="num">4 hr</span><span class="lbl">session uptime target</span></div>
</div>
</div>

</div>

<div class="gds-feature green" markdown>

<div class="badge">Jetpack&nbsp;Compose</div>

<div markdown>
<span class="role-tag">Android UI · declarative</span>
## A coach UI that updates at the cadence of the car

The v1 Pitwall client is built in Jetpack Compose — declarative Kotlin UI that
maps cleanly to a 10 Hz telemetry stream. Composables for the HUD, the coach
card, the cue inbox, the per-corner grade strip — all reactive, all driven by
the same state graph that the bridge emits over SSE.

The Vue PWA is the long-term frontend, but the Compose v1 was how the coach
loop was first proven on real hardware in cabin.

<div class="stat-row" markdown>
<div class="stat"><span class="num">42</span><span class="lbl">@Composable functions</span></div>
<div class="stat"><span class="num">10 Hz</span><span class="lbl">telemetry refresh</span></div>
<div class="stat"><span class="num">SSE</span><span class="lbl">live cue stream</span></div>
</div>
</div>

</div>

<div class="gds-feature blue" markdown>

<div class="badge">Google&nbsp;ADK</div>

<div markdown>
<span class="role-tag">Multi-agent runtime · paddock tier</span>
## 18 specialist agents, deterministic routing, real telemetry

The paddock tier — pre-brief, post-session debrief, multi-turn Q&A — is built on
the [Google Agent Development Kit](https://adk.dev/). A `PitwallOrchestrator`
routes by deterministic keyword (no LLM-as-router fragility). A `DebriefPipeline`
fans out three data agents in parallel via `ParallelAgent`, then funnels results
into a `NarrativeAgent` via `SequentialAgent`.

Every tool is read-only DuckDB with `LIMIT 500` enforced. Every agent run is
logged to an `agent_traces` table by a `PitwallTracingPlugin`. Persistent
sessions per driver keep the KV cache warm across calls — the warm request
path is **30–50% cheaper** in prefill than the cold one.

The model client is **pluggable across three local-only backends**
([ADR-022](adr/022-openai-compatible-backend-selector.md)): in-process via
`LitertLmModel`, HTTP to `lit serve` via ADK's `Gemini(base_url=...)`, or
HTTP to **any OpenAI-compatible server** (Ollama, LM Studio, llama.cpp,
vLLM) via ADK's `LiteLlm` wrapper. Same agents, same tools, same DuckDB —
swap the runtime with one env var.

<div class="stat-row" markdown>
<div class="stat"><span class="num">18</span><span class="lbl">specialist agents</span></div>
<div class="stat"><span class="num">15</span><span class="lbl">SQL-safe tools</span></div>
<div class="stat"><span class="num">3</span><span class="lbl">local-LLM backends, one selector</span></div>
</div>

[Read the ADK architecture →](adk-agent-architecture.md){ .gds-btn .gds-btn-ghost }

</div>

</div>

<div class="gds-feature red" markdown>

<div class="badge">LocalLLM</div>

<div markdown>
<span class="role-tag">On-phone LLM server · sibling project</span>
## The other half of the on-device promise

Pitwall coaches the driver. **[LocalLLM](https://www.tahabouhsine.com/localllm/)** —
a separate Apache-2.0 Android APK from the same author
([github.com/mlnomadpy/localllm](https://github.com/mlnomadpy/localllm)) —
hosts the actual LLM. It runs LiteRT-LM in a native Android process, downloads
`.litertlm` Gemma 4 bundles from the [`litert-community`](https://huggingface.co/litert-community)
HuggingFace collection through its in-app catalog, and exposes an
**OpenAI-compatible HTTP server** on `127.0.0.1:8099/v1/chat/completions` with
SSE streaming and signed-bearer-token auth.

From Pitwall's side, this is just a `LiteLlm(api_base="http://localhost:8099/v1", …)`
call inside ADK. From the driver's side, this is two icons on the Pixel home
screen: LocalLLM (model server) and Pitwall (coach). Two APKs, one phone, one
localhost hop, zero cloud.

LocalLLM gets GPU/NPU access through LiteRT's AUTO delegate — which Termux
processes generally can't reach — and owns the model lifecycle (download,
switch, unload) so the bridge doesn't have to. A crash in the model runtime
no longer takes the coach down with it.

<div class="stat-row" markdown>
<div class="stat"><span class="num">:8099/v1</span><span class="lbl">OpenAI-compat endpoint</span></div>
<div class="stat"><span class="num">SSE</span><span class="lbl">streaming responses</span></div>
<div class="stat"><span class="num">Apache 2.0</span><span class="lbl">open source</span></div>
</div>

[Visit LocalLLM →](https://www.tahabouhsine.com/localllm/){ .gds-btn .gds-btn-ghost }
[Source on GitHub →](https://github.com/mlnomadpy/localllm){ .gds-btn .gds-btn-ghost }
</div>

</div>

<div class="gds-feature green" markdown>

<div class="badge">Gemma&nbsp;4 ·<br/>LiteRT-LM</div>

<div markdown>
<span class="role-tag">On-device inference · warm + paddock</span>
## A 130-mph LLM that lives entirely on the phone

Pitwall does not call a cloud LLM during driving. The warm-path coach is **Gemma
4 E2B** loaded in-process via `litert_lm.Engine`, emitting rally-style pace
notes in under 100 ms on a straight. The paddock tier runs **Gemma 4 E4B** via
`lit serve` on the Tensor G5 NPU, fielding briefings, debriefs, and Q&A in
2–15 seconds — without giving up cellular dead-zone resilience.

LiteRT-LM is what makes this practical: a cross-platform runtime (macOS, Linux,
Termux on Android) that consumes `.litertlm` model files and runs them on the
device's accelerator. It is the reason the privacy promise — *no telemetry
leaves the car* — is keepable.

<div class="stat-row" markdown>
<div class="stat"><span class="num">&lt; 100 ms</span><span class="lbl">warm-path cue</span></div>
<div class="stat"><span class="num">2–15 s</span><span class="lbl">paddock tier</span></div>
<div class="stat"><span class="num">0</span><span class="lbl">cloud LLM calls in-drive</span></div>
</div>
</div>

</div>

<div class="gds-band" markdown>

## Why this stack — and why now

Pitwall could have been built as a cloud-attached app. It wasn't, on purpose.

- **The driver doesn't have signal.** Sonoma's hot pit has marginal LTE on a good day. A cloud-attached coach is a coach that goes silent in turn 6.
- **The latency budget is brutal.** A useful in-corner cue is &lt; 100 ms. A round-trip to a hosted model isn't.
- **The data is sensitive.** CAN bus output is a fingerprint of how a driver drives. It belongs on the device, not in someone else's data lake.

The Google stack — **Antigravity** to build it, **Gemini CLI** for the workflow
glue, **Android Studio** + **Jetpack Compose** for the device, **ADK** for the
agent topology, **Gemma 4** through **LiteRT-LM** for the inference, and
**[LocalLLM](https://www.tahabouhsine.com/localllm/)** to host the model right
next to the coach on the same phone — is the stack that makes "trustable AI
at 130 mph" something you can ship, not just something you can pitch.

<div class="cta-row" markdown>
[Project explainer](https://storage.googleapis.com/pitwall-demo/pitwall-explainer.html){ .gds-btn .gds-btn-primary }
[Repo on GitHub](https://github.com/mlnomadpy/pitwall){ .gds-btn .gds-btn-ghost }
</div>

</div>

</div>
