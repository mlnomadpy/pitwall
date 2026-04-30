<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import { openCueStream, getSessions } from '$lib/api';

  let es: EventSource | null = null;
  let cues = $state<{ text: string; ts: number }[]>([]);
  let activeCue = $state('');
  let connected = $state(false);
  let sessionId = $state('');
  let sessions = $state<{ session_id: string }[]>([]);

  let speed = $state(0);
  let grip = $state(0);
  let overGrip = $state(0);
  let lap = $state(0);
  let lapTime = $state('0:00.00');
  let corner = $state('');
  let showLog = $state(false);

  onMount(async () => {
    try {
      const s = await getSessions(10);
      sessions = s.sessions;
      if (sessions.length > 0) sessionId = sessions[0].session_id;
    } catch { /* ignore */ }
  });

  function connectStream() {
    if (!sessionId) return;
    if (es) es.close();
    es = openCueStream(
      sessionId,
      (data) => {
        const text = (data.text as string) || JSON.stringify(data);
        activeCue = text;
        cues = [{ text, ts: Date.now() }, ...cues.slice(0, 49)];
        if (data.speed) speed = data.speed as number;
        if (data.friction_circle_pct) grip = data.friction_circle_pct as number;
        if (data.over_pct) overGrip = data.over_pct as number;
        if (data.lap) lap = data.lap as number;
        if (data.corner) corner = data.corner as string;
      },
      () => { connected = false; },
      () => { connected = true; }  // onConnect — fired on 'hello' event
    );
  }

  function disconnect() {
    if (es) { es.close(); es = null; }
    connected = false;
    activeCue = '';
  }

  onDestroy(() => { if (es) es.close(); });
</script>

<svelte:head>
  <title>Pitwall — HUD</title>
</svelte:head>

<div class="hud fade-up">
  <!-- Session Picker -->
  {#if !connected}
    <div class="connect-card card-glow">
      <h2 class="font-display text-lg" style="margin-bottom: var(--sp-3);">On-Track HUD</h2>
      <select bind:value={sessionId}>
        <option value="">Select session…</option>
        {#each sessions as s}
          <option value={s.session_id}>{s.session_id.replace('sonoma-import-', '')}</option>
        {/each}
      </select>
      <button class="btn btn-accent" style="width: 100%; margin-top: var(--sp-3);" onclick={connectStream} disabled={!sessionId}>
        Connect to Stream
      </button>
    </div>
  {/if}

  <!-- HUD Display -->
  <div class="hud-panel" class:dimmed={!connected}>
    <!-- Row 1: Lap + Time + Status -->
    <div class="hud-header">
      <div class="metric">
        <span class="metric-label">Lap</span>
        <span class="metric-value font-mono">{lap || '—'}</span>
      </div>
      <div class="hud-timer font-mono">{lapTime}</div>
      <div class="hud-ai-badge" class:on={connected}>
        <div class="dot" class:dot-ok={connected} class:dot-err={!connected}></div>
        <span>{connected ? 'AI ON' : 'OFF'}</span>
      </div>
    </div>

    <!-- Row 2: Grip + Track + Speed -->
    <div class="hud-body">
      <!-- Grip Bar -->
      <div class="hud-col">
        <span class="hud-col-label">Grip</span>
        <div class="grip-track">
          <div class="vbar-track">
            <div class="vbar-fill" style="height: {grip}%; background: {grip > 85 ? 'var(--warn)' : 'var(--accent)'}"></div>
          </div>
        </div>
        <span class="hud-col-val font-mono">{grip}%</span>
      </div>

      <!-- Track Map -->
      <div class="hud-map">
        <svg viewBox="0 0 200 140" fill="none">
          <path d="M 30,120 Q 10,100 15,70 Q 20,40 50,25 Q 80,10 120,15 Q 160,20 180,50 Q 195,80 175,110 Q 160,130 120,125 Q 80,120 50,130 Z"
                stroke="var(--surface-4)" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M 30,120 Q 10,100 15,70 Q 20,40 50,25 Q 80,10 120,15 Q 160,20 180,50 Q 195,80 175,110 Q 160,130 120,125 Q 80,120 50,130 Z"
                stroke="var(--accent-dim)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" opacity="0.4"/>
          <text x="8" y="68" fill="var(--text-3)" font-size="9" font-family="Space Grotesk">T1</text>
          <text x="52" y="18" fill="var(--text-3)" font-size="9" font-family="Space Grotesk">T3</text>
          <text x="125" y="12" fill="var(--text-3)" font-size="9" font-family="Space Grotesk">T6</text>
          <text x="183" y="55" fill="var(--text-3)" font-size="9" font-family="Space Grotesk">T7</text>
          <text x="170" y="128" fill="var(--text-3)" font-size="9" font-family="Space Grotesk">T10</text>
          <circle cx="30" cy="120" r="5" fill="var(--accent)">
            <animate attributeName="r" values="5;7;5" dur="1.5s" repeatCount="indefinite"/>
            <animate attributeName="opacity" values="1;0.5;1" dur="1.5s" repeatCount="indefinite"/>
          </circle>
          <circle cx="30" cy="120" r="12" fill="none" stroke="var(--accent)" stroke-width="1" opacity="0.3">
            <animate attributeName="r" values="12;18;12" dur="2s" repeatCount="indefinite"/>
            <animate attributeName="opacity" values="0.3;0;0.3" dur="2s" repeatCount="indefinite"/>
          </circle>
        </svg>
        {#if corner}
          <div class="corner-tag">{corner}</div>
        {/if}
      </div>

      <!-- Over / Speed -->
      <div class="hud-col">
        <span class="hud-col-label">Speed</span>
        <div class="speed-display">
          <span class="speed-num font-mono">{speed || '—'}</span>
          <span class="speed-unit">km/h</span>
        </div>
        <div class="over-section">
          <span class="hud-col-label" style="color: var(--bad);">Over</span>
          <span class="font-mono text-sm" style="color: {overGrip > 0 ? 'var(--bad)' : 'var(--text-3)'};">{overGrip}%</span>
        </div>
      </div>
    </div>

    <!-- Cue Band -->
    <div class="cue-band" class:active={connected && activeCue}>
      {#if activeCue}
        <p class="cue-text">{activeCue}</p>
      {:else}
        <p class="cue-text text-3">Waiting for cues…</p>
      {/if}
    </div>

    <!-- Disconnect -->
    {#if connected}
      <button class="btn btn-sm" style="width: 100%; margin-top: var(--sp-3); color: var(--text-3);" onclick={disconnect}>
        Disconnect
      </button>
    {/if}
  </div>

  <!-- Cue Log Toggle -->
  {#if cues.length > 0}
    <button class="btn btn-sm btn-ghost" style="width: 100%; margin-top: var(--sp-3);" onclick={() => showLog = !showLog}>
      {showLog ? 'Hide' : 'Show'} Cue Log ({cues.length})
    </button>

    {#if showLog}
      <div class="cue-log card" style="margin-top: var(--sp-2);">
        {#each cues as cue, i}
          <div class="log-entry" style="opacity: {1 - i * 0.015}">
            <span class="text-3 font-mono text-xs">{new Date(cue.ts).toLocaleTimeString('en-US', { hour12: false })}</span>
            <span class="text-sm">{cue.text}</span>
          </div>
        {/each}
      </div>
    {/if}
  {/if}
</div>

<style>
  .hud {
    padding-bottom: var(--sp-6);
  }

  .connect-card {
    margin-bottom: var(--sp-4);
  }

  .hud-panel {
    background: var(--surface-1);
    border: 1px solid var(--surface-3);
    border-radius: var(--r-lg);
    overflow: hidden;
    transition: opacity 0.3s;
  }

  .hud-panel.dimmed {
    opacity: 0.5;
  }

  .hud-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: var(--sp-4);
    border-bottom: 1px solid var(--surface-3);
  }

  .hud-timer {
    font-size: 28px;
    font-weight: 700;
    color: var(--accent);
    letter-spacing: 1px;
  }

  .hud-ai-badge {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;
    font-weight: 600;
    color: var(--text-3);
    text-transform: uppercase;
    letter-spacing: 0.05em;
  }

  .hud-ai-badge.on {
    color: var(--accent);
  }

  /* ── Body: 3-column layout ── */
  .hud-body {
    display: flex;
    align-items: stretch;
    padding: var(--sp-4);
    gap: var(--sp-3);
    min-height: 200px;
  }

  .hud-col {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: var(--sp-2);
    width: 56px;
    flex-shrink: 0;
  }

  .hud-col-label {
    font-size: 10px;
    font-weight: 600;
    color: var(--text-3);
    text-transform: uppercase;
    letter-spacing: 0.08em;
  }

  .hud-col-val {
    font-size: 13px;
    color: var(--text-2);
  }

  .grip-track {
    flex: 1;
    width: 20px;
    border-radius: var(--r-sm);
  }

  .hud-map {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    position: relative;
    min-height: 160px;
  }

  .hud-map svg {
    width: 100%;
    max-width: 220px;
  }

  .corner-tag {
    position: absolute;
    bottom: 0;
    padding: 4px 12px;
    background: var(--surface-2);
    border: 1px solid var(--info);
    border-radius: var(--r-sm);
    font-size: 12px;
    font-weight: 600;
    color: var(--info);
  }

  .speed-display {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
  }

  .speed-num {
    font-size: 28px;
    font-weight: 700;
    color: var(--text-1);
    line-height: 1;
  }

  .speed-unit {
    font-size: 10px;
    color: var(--text-3);
    margin-top: 2px;
  }

  .over-section {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 2px;
  }

  /* ── Cue Band ── */
  .cue-band {
    padding: var(--sp-4);
    background: var(--surface-2);
    border-top: 1px solid var(--surface-3);
    min-height: 56px;
    display: flex;
    align-items: center;
    transition: border-color 0.3s;
  }

  .cue-band.active {
    border-top-color: var(--accent-dim);
    background: rgba(45, 212, 168, 0.04);
  }

  .cue-text {
    font-size: 14px;
    line-height: 1.4;
  }

  /* ── Cue Log ── */
  .cue-log {
    max-height: 240px;
    overflow-y: auto;
    padding: var(--sp-3);
  }

  .log-entry {
    display: flex;
    gap: var(--sp-3);
    padding: var(--sp-2) 0;
    border-bottom: 1px solid var(--surface-3);
    align-items: baseline;
  }

  .log-entry:last-child { border-bottom: none; }
</style>
