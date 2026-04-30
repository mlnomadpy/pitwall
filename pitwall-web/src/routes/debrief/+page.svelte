<script lang="ts">
  import { onMount } from 'svelte';
  import { marked } from 'marked';
  import {
    getSessions, getCoachDebrief, getHealth, getLapTimeTable, getIdealLap,
    getPedalBehavior, getBrakeAcceleration,
    type HealthResponse, type Session, type LapTimeTable, type IdealLap,
    type PedalBehavior, type BrakeAcceleration
  } from '$lib/api';

  let health = $state<HealthResponse | null>(null);
  let sessions = $state<Session[]>([]);
  let selectedSession = $state('');
  let loading = $state(false);

  // Debrief data
  let debriefText = $state('');
  let debriefEmotion = $state('');
  let debriefReady = $state(false);

  // Session analytics
  let lapTable = $state<LapTimeTable | null>(null);
  let idealLap = $state<IdealLap | null>(null);
  let pedals = $state<PedalBehavior | null>(null);
  let brakes = $state<BrakeAcceleration | null>(null);
  let expandedSection = $state<string | null>(null);

  onMount(async () => {
    try {
      const [h, s] = await Promise.all([getHealth(), getSessions(20)]);
      health = h;
      sessions = s.sessions;
      if (sessions.length > 0) selectedSession = sessions[0].session_id;
    } catch { /* ignore */ }
  });

  async function generateDebrief() {
    if (!selectedSession) return;
    loading = true;
    debriefText = '';
    debriefEmotion = '';
    debriefReady = false;
    try {
      const [debrief, laps, ideal, pedal, brake] = await Promise.allSettled([
        getCoachDebrief(selectedSession),
        getLapTimeTable(selectedSession),
        getIdealLap(selectedSession),
        getPedalBehavior(selectedSession),
        getBrakeAcceleration(selectedSession),
      ]);
      if (debrief.status === 'fulfilled') {
        const d = debrief.value as Record<string, unknown>;
        // Bundle may return: text, narrative_md, coach_narrative, or scorecard.narrative
        debriefText = String(
          d.text || d.narrative_md || d.coach_narrative ||
          (d.scorecard as Record<string,unknown>)?.narrative || 'Session analyzed.'
        );
        debriefEmotion = String(d.emotion || (d.scorecard as Record<string,unknown>)?.emotion || 'proud');
        debriefReady = true;
      } else {
        debriefText = 'Coach unavailable — check your connection and try again.';
        debriefEmotion = 'error';
        debriefReady = true;
      }
      if (laps.status === 'fulfilled') lapTable = laps.value;
      if (ideal.status === 'fulfilled') idealLap = ideal.value;
      if (pedal.status === 'fulfilled') pedals = pedal.value;
      if (brake.status === 'fulfilled') brakes = brake.value;
    } catch {
      debriefText = 'Coach unavailable. Try again.';
      debriefEmotion = 'error';
      debriefReady = true;
    } finally {
      loading = false;
    }
  }

  function fmt(s: number | null | undefined): string {
    if (s == null) return '—';
    const min = Math.floor(s / 60);
    const sec = (s % 60).toFixed(2);
    return `${min}:${sec.padStart(5, '0')}`;
  }

  function toggle(id: string) {
    expandedSection = expandedSection === id ? null : id;
  }

  // Derive highlights
  let bestLap = $derived(lapTable ? lapTable.laps.find(l => l.is_best) : null);
  let consistency = $derived(lapTable ? (
    lapTable.laps.length > 1
      ? (Math.max(...lapTable.laps.map(l => l.lap_time_s)) - Math.min(...lapTable.laps.map(l => l.lap_time_s)))
      : 0
  ) : 0);

  const emotionColor: Record<string, string> = {
    proud: 'var(--accent)',
    impressed: 'var(--accent)',
    encouraging: 'var(--accent)',
    excited: 'var(--warn)',
    analytical: 'var(--info)',
    concerned: 'var(--warn)',
    stern: 'var(--bad)',
    calm: 'var(--text-2)',
    focused: 'var(--info)',
    error: 'var(--bad)',
  };
</script>

<svelte:head>
  <title>Pitwall — Debrief</title>
</svelte:head>

<div class="debrief-page fade-up">
  <!-- Header: celebratory energy -->
  <div class="debrief-header">
    <div class="debrief-header-icon">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" width="28" height="28"><path d="M22 11.08V12a10 10 0 11-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
    </div>
    <div>
      <h1 class="debrief-title">Post-Race Debrief</h1>
      <p class="text-2 text-sm">Review. Celebrate. Grow.</p>
    </div>
  </div>

  <!-- Session picker -->
  <div class="section">
    <select bind:value={selectedSession}>
      <option value="">Choose session to review…</option>
      {#each sessions as s}
        <option value={s.session_id}>
          {s.session_id.replace('sonoma-import-', '')}
          {s.lap_count > 0 ? `· ${s.lap_count} laps` : ''}
          {s.best_lap_s ? `· ${fmt(s.best_lap_s)}` : ''}
        </option>
      {/each}
    </select>

    <button class="btn btn-debrief" onclick={generateDebrief} disabled={!selectedSession || loading}>
      {#if loading}
        <span class="spinner"></span>
        <span>Analyzing your session…</span>
      {:else}
        <span>Get Debrief</span>
      {/if}
    </button>
  </div>

  <!-- Loading -->
  {#if loading}
    <div class="loading-card">
      <div class="loading-bar">
        <div class="loading-bar-fill"></div>
      </div>
      <p class="text-2 text-sm" style="margin-top: var(--sp-4); text-align: center;">
        Crunching your lap data…
      </p>
      <p class="text-3 text-xs" style="text-align: center; margin-top: var(--sp-2);">
        Analyzing {selectedSession.replace('sonoma-import-', '')}
      </p>
    </div>
  {/if}

  {#if debriefReady && !loading}
    <!-- ── HIGHLIGHT REEL FIRST ── -->
    {#if lapTable && bestLap}
      <div class="highlight-card fade-up">
        <div class="highlight-label">Highlight Reel</div>
        <div class="highlight-grid">
          <div class="hl-item hl-hero">
            <span class="hl-value font-mono">{fmt(bestLap.lap_time_s)}</span>
            <span class="hl-label">Best Lap (#{bestLap.lap_number})</span>
          </div>
          <div class="hl-item">
            <span class="hl-value font-mono">{lapTable.lap_count}</span>
            <span class="hl-label">Total Laps</span>
          </div>
          {#if idealLap}
            <div class="hl-item">
              <span class="hl-value font-mono text-accent">{fmt(idealLap.ideal_lap_s)}</span>
              <span class="hl-label">Theoretical Best</span>
            </div>
            <div class="hl-item">
              <span class="hl-value font-mono" style="color: var(--warn);">-{(idealLap.gain_potential_s ?? 0).toFixed(2)}s</span>
              <span class="hl-label">Time to Find</span>
            </div>
          {/if}
        </div>

        {#if idealLap}
          <div class="potential-bar">
            <div class="pb-track">
              <div class="pb-fill" style="width: {Math.min(100, ((idealLap.ideal_lap_s ?? 1) / (idealLap.best_actual_lap_s || 1)) * 100)}%;"></div>
              <div class="pb-marker" style="left: {Math.min(100, ((idealLap.ideal_lap_s ?? 1) / (idealLap.best_actual_lap_s || 1)) * 100)}%;"></div>
            </div>
            <div class="pb-labels">
              <span class="text-xs text-accent">Ideal</span>
              <span class="text-xs text-2">Actual</span>
            </div>
          </div>
        {/if}
      </div>
    {/if}

    <!-- Coach Message -->
    <div class="coach-card fade-up" style="animation-delay: 0.1s; border-left-color: {emotionColor[debriefEmotion] || 'var(--surface-4)'};">
      <div class="coach-header">
        <div class="coach-avatar">🧑‍🏫</div>
        <div>
          <span class="coach-name">Coach</span>
          <span class="coach-emotion" style="color: {emotionColor[debriefEmotion] || 'var(--text-3)'};">{debriefEmotion}</span>
        </div>
      </div>
      <div class="coach-body md-body">
        {@html marked.parse(debriefText || '')}
      </div>
    </div>

    <!-- ── ANALYTICS SECTIONS (collapsible) ── -->

    <!-- Lap Consistency -->
    {#if lapTable && lapTable.laps.length > 1}
      <div class="analytics-section fade-up" style="animation-delay: 0.15s;">
        <button class="analytics-header" onclick={() => toggle('laps')}>
          <div class="ah-left">
            <span class="ah-icon">⏱</span>
            <span class="ah-title">Lap Consistency</span>
          </div>
          <div class="ah-right">
            <span class="ah-summary font-mono">{(consistency ?? 0).toFixed(2)}s spread</span>
            <svg class="ah-chevron" class:open={expandedSection === 'laps'} viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2"><polyline points="4 6 8 10 12 6"/></svg>
          </div>
        </button>
        {#if expandedSection === 'laps'}
          <div class="analytics-body">
            <div class="lap-bars">
              {#each lapTable.laps as lap}
                {@const range = lapTable.laps.length > 0 ? Math.max(...lapTable.laps.map(l => l.lap_time_s)) - Math.min(...lapTable.laps.map(l => l.lap_time_s)) : 1}
                {@const minTime = Math.min(...lapTable.laps.map(l => l.lap_time_s))}
                {@const pct = range > 0 ? ((lap.lap_time_s - minTime) / range) : 0}
                <div class="lb-row">
                  <span class="lb-num font-mono">{lap.lap_number}</span>
                  <div class="lb-bar-track">
                    <div class="lb-bar" style="width: {100 - pct * 60}%; background: {lap.is_best ? 'var(--accent)' : 'var(--surface-4)'};"></div>
                  </div>
                  <span class="lb-time font-mono" style="color: {lap.is_best ? 'var(--accent)' : 'var(--text-2)'};">{fmt(lap.lap_time_s)}</span>
                </div>
              {/each}
            </div>
          </div>
        {/if}
      </div>
    {/if}

    <!-- Pedal Analysis -->
    {#if pedals}
      <div class="analytics-section fade-up" style="animation-delay: 0.2s;">
        <button class="analytics-header" onclick={() => toggle('pedals')}>
          <div class="ah-left">
            <span class="ah-icon">P</span>
            <span class="ah-title">Pedal Work</span>
          </div>
          <div class="ah-right">
            <span class="ah-summary font-mono">{(pedals.states?.coast?.pct ?? 0).toFixed(0)}% coast</span>
            <svg class="ah-chevron" class:open={expandedSection === 'pedals'} viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2"><polyline points="4 6 8 10 12 6"/></svg>
          </div>
        </button>
        {#if expandedSection === 'pedals'}
          <div class="analytics-body">
            {#each [
              { label: 'Throttle', pct: pedals.states.throttle_only.pct, color: 'var(--accent)' },
              { label: 'Brake', pct: pedals.states.brake_only.pct, color: 'var(--bad)' },
              { label: 'Trail Brake', pct: pedals.states.trail_brake.pct, color: 'var(--warn)' },
              { label: 'Coast', pct: pedals.states.coast.pct, color: 'var(--info)' },
            ] as item}
              <div class="pedal-row">
                <span class="pr-label">{item.label}</span>
                <div class="pr-bar-track">
                  <div class="pr-bar" style="width: {item.pct}%; background: {item.color};"></div>
                </div>
                <span class="pr-pct font-mono">{(item.pct ?? 0).toFixed(1)}%</span>
              </div>
            {/each}
          </div>
        {/if}
      </div>
    {/if}

    <!-- Brake Zones -->
    {#if brakes && brakes.brake_zones.length > 0}
      <div class="analytics-section fade-up" style="animation-delay: 0.25s;">
        <button class="analytics-header" onclick={() => toggle('brakes')}>
          <div class="ah-left">
            <span class="ah-icon">B</span>
            <span class="ah-title">Brake Zones</span>
          </div>
          <div class="ah-right">
            <span class="ah-summary font-mono">{brakes.brake_zones.length} zones</span>
            <svg class="ah-chevron" class:open={expandedSection === 'brakes'} viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2"><polyline points="4 6 8 10 12 6"/></svg>
          </div>
        </button>
        {#if expandedSection === 'brakes'}
          <div class="analytics-body">
            {#each brakes.brake_zones as bz}
              <div class="brake-row">
                <span class="bz-corner">{bz.corner}</span>
                <div class="bz-bar-track">
                  <div class="bz-bar" style="width: {Math.min(Math.abs(bz.max_decel_g) / 2 * 100, 100)}%;"></div>
                </div>
                <span class="bz-val font-mono">{(bz.max_decel_g ?? 0).toFixed(2)}g</span>
              </div>
            {/each}
          </div>
        {/if}
      </div>
    {/if}
  {/if}

  <!-- Empty state -->
  {#if !debriefReady && !loading}
    <div class="empty-debrief">
      <div class="empty-flag">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" width="32" height="32"><path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z"/><line x1="4" y1="22" x2="4" y2="15"/></svg>
      </div>
      <h3 style="font-size: 18px; margin-bottom: 4px;">Session Complete?</h3>
      <p class="text-2 text-sm">Select your session and tap <strong>Get Debrief</strong></p>
      <p class="text-3 text-xs" style="margin-top: var(--sp-2);">
        Your coach will start with highlights, then dig into the data.
      </p>
    </div>
  {/if}
</div>

<style>
  .debrief-page { padding-bottom: var(--sp-10); }

  /* ── Header ── */
  .debrief-header {
    display: flex;
    align-items: center;
    gap: var(--sp-4);
    margin-bottom: var(--sp-5);
  }

  .debrief-header-icon {
    width: 52px; height: 52px;
    border-radius: var(--r-md);
    background: rgba(240, 168, 48, 0.1);
    border: 1px solid rgba(240, 168, 48, 0.2);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 24px;
    flex-shrink: 0;
  }

  .debrief-title {
    font-family: 'Space Mono', monospace;
    font-size: 22px;
    font-weight: 700;
    letter-spacing: -0.02em;
    color: var(--text-1);
  }

  .section { margin-bottom: var(--sp-5); }

  /* ── Debrief Button ── */
  .btn-debrief {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: var(--sp-2);
    width: 100%;
    min-height: 56px;
    margin-top: var(--sp-3);
    border: none;
    border-radius: var(--r-md);
    background: linear-gradient(135deg, #f0a830 0%, #e8962a 100%);
    color: var(--surface-0);
    font-family: 'Space Grotesk', sans-serif;
    font-size: 16px;
    font-weight: 700;
    cursor: pointer;
    transition: all 0.2s;
    box-shadow: 0 4px 20px rgba(240, 168, 48, 0.2);
  }

  .btn-debrief:active { transform: scale(0.97); }
  .btn-debrief:disabled { opacity: 0.5; cursor: default; }

  .spinner {
    width: 18px; height: 18px;
    border: 2px solid rgba(0,0,0,0.15);
    border-top-color: var(--surface-0);
    border-radius: 50%;
    animation: spin 0.7s linear infinite;
  }

  @keyframes spin { to { transform: rotate(360deg); } }

  /* ── Loading ── */
  .loading-card {
    padding: var(--sp-6);
    background: var(--surface-2);
    border: 1px solid var(--surface-3);
    border-radius: var(--r-md);
    margin-bottom: var(--sp-5);
  }

  .loading-bar {
    height: 4px;
    background: var(--surface-4);
    border-radius: 2px;
    overflow: hidden;
  }

  .loading-bar-fill {
    height: 100%;
    width: 40%;
    background: var(--warn);
    border-radius: 2px;
    animation: progress-slide 1.5s ease-in-out infinite;
  }

  @keyframes progress-slide {
    0% { transform: translateX(-100%); }
    100% { transform: translateX(350%); }
  }

  /* ── Highlight Reel ── */
  .highlight-card {
    background: var(--surface-1);
    border: 1px solid var(--accent-dim);
    border-radius: var(--r-lg);
    padding: var(--sp-5);
    margin-bottom: var(--sp-4);
    box-shadow: 0 0 24px var(--accent-glow);
  }

  .highlight-label {
    font-size: 11px;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.1em;
    color: var(--accent);
    margin-bottom: var(--sp-4);
  }

  .highlight-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: var(--sp-3);
  }

  .hl-item {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .hl-hero {
    grid-column: 1 / -1;
    text-align: center;
    padding: var(--sp-4) 0;
    background: rgba(45, 212, 168, 0.04);
    border-radius: var(--r-sm);
    margin-bottom: var(--sp-2);
  }

  .hl-hero .hl-value {
    font-size: 36px;
    color: var(--accent);
  }

  .hl-value {
    font-size: 20px;
    font-weight: 700;
    color: var(--text-1);
    line-height: 1.1;
  }

  .hl-label {
    font-size: 11px;
    color: var(--text-3);
    font-weight: 500;
  }

  .potential-bar { margin-top: var(--sp-4); }

  .pb-track {
    height: 6px;
    background: var(--surface-4);
    border-radius: 3px;
    position: relative;
    overflow: visible;
  }

  .pb-fill {
    height: 100%;
    background: var(--accent);
    border-radius: 3px;
  }

  .pb-marker {
    position: absolute;
    top: -3px;
    width: 3px;
    height: 12px;
    background: var(--text-1);
    border-radius: 1px;
    transform: translateX(-50%);
  }

  .pb-labels {
    display: flex;
    justify-content: space-between;
    margin-top: 4px;
  }

  /* ── Coach Card ── */
  .coach-card {
    background: var(--surface-2);
    border: 1px solid var(--surface-3);
    border-left: 3px solid var(--accent);
    border-radius: var(--r-md);
    padding: var(--sp-5);
    margin-bottom: var(--sp-4);
  }

  .coach-header {
    display: flex;
    align-items: center;
    gap: var(--sp-3);
    margin-bottom: var(--sp-4);
  }

  .coach-avatar {
    width: 40px; height: 40px;
    border-radius: var(--r-sm);
    background: var(--surface-3);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 20px;
    flex-shrink: 0;
  }

  .coach-name {
    font-size: 14px;
    font-weight: 700;
    color: var(--text-1);
    display: block;
  }

  .coach-emotion {
    font-size: 11px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.06em;
  }

  .coach-body {
    font-size: 15px;
    line-height: 1.75;
    color: var(--text-1);
  }

  .coach-body p { margin-bottom: var(--sp-3); }
  .coach-body p:last-child { margin-bottom: 0; }

  /* ── Analytics Accordion ── */
  .analytics-section {
    background: var(--surface-2);
    border: 1px solid var(--surface-3);
    border-radius: var(--r-md);
    margin-bottom: var(--sp-3);
    overflow: hidden;
  }

  .analytics-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    width: 100%;
    padding: var(--sp-4);
    min-height: 52px;
    background: transparent;
    border: none;
    color: var(--text-1);
    cursor: pointer;
    font-family: 'Space Grotesk', sans-serif;
  }

  .analytics-header:active { background: var(--surface-3); }

  .ah-left {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
  }

  .ah-icon { font-size: 16px; }

  .ah-title {
    font-size: 14px;
    font-weight: 600;
  }

  .ah-right {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
  }

  .ah-summary {
    font-size: 12px;
    color: var(--text-3);
  }

  .ah-chevron {
    width: 16px; height: 16px;
    color: var(--text-3);
    transition: transform 0.2s;
  }

  .ah-chevron.open { transform: rotate(180deg); }

  .analytics-body {
    padding: 0 var(--sp-4) var(--sp-4);
  }

  /* ── Lap bars ── */
  .lap-bars { display: flex; flex-direction: column; gap: var(--sp-2); }

  .lb-row {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
  }

  .lb-num {
    font-size: 12px;
    color: var(--text-3);
    width: 20px;
    text-align: right;
    flex-shrink: 0;
  }

  .lb-bar-track {
    flex: 1;
    height: 16px;
    background: var(--surface-3);
    border-radius: 4px;
    overflow: hidden;
  }

  .lb-bar {
    height: 100%;
    border-radius: 4px;
    transition: width 0.4s ease;
  }

  .lb-time {
    font-size: 12px;
    width: 60px;
    text-align: right;
    flex-shrink: 0;
  }

  /* ── Pedal rows ── */
  .pedal-row {
    display: flex;
    align-items: center;
    gap: var(--sp-3);
    margin-bottom: var(--sp-3);
  }

  .pedal-row:last-child { margin-bottom: 0; }

  .pr-label {
    font-size: 13px;
    width: 80px;
    flex-shrink: 0;
    color: var(--text-2);
  }

  .pr-bar-track {
    flex: 1;
    height: 12px;
    background: var(--surface-3);
    border-radius: 6px;
    overflow: hidden;
  }

  .pr-bar {
    height: 100%;
    border-radius: 6px;
    transition: width 0.5s ease;
  }

  .pr-pct {
    font-size: 12px;
    color: var(--text-2);
    width: 44px;
    text-align: right;
    flex-shrink: 0;
  }

  /* ── Brake rows ── */
  .brake-row {
    display: flex;
    align-items: center;
    gap: var(--sp-3);
    margin-bottom: var(--sp-3);
  }

  .brake-row:last-child { margin-bottom: 0; }

  .bz-corner {
    font-size: 13px;
    width: 48px;
    flex-shrink: 0;
    font-weight: 600;
  }

  .bz-bar-track {
    flex: 1;
    height: 12px;
    background: var(--surface-3);
    border-radius: 6px;
    overflow: hidden;
  }

  .bz-bar {
    height: 100%;
    background: var(--bad);
    border-radius: 6px;
  }

  .bz-val {
    font-size: 12px;
    color: var(--text-2);
    width: 48px;
    text-align: right;
    flex-shrink: 0;
  }

  /* ── Empty ── */
  .empty-debrief {
    text-align: center;
    padding: var(--sp-10) var(--sp-4);
  }

  .empty-flag {
    font-size: 48px;
    margin-bottom: var(--sp-4);
  }
</style>
