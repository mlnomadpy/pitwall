<script lang="ts">
  import { onMount } from 'svelte';
  import { marked } from 'marked';
  import {
    getSessions, getCoachBrief, getHealth, getCorners, getPedalBehavior,
    type HealthResponse, type Session, type CornerData, type PedalBehavior
  } from '$lib/api';

  let health = $state<HealthResponse | null>(null);
  let sessions = $state<Session[]>([]);
  let selectedSession = $state('');
  let loading = $state(false);
  let pageLoading = $state(true);

  // Brief data
  let briefText = $state('');
  let briefEmotion = $state('');
  let briefReady = $state(false);

  // Session context (pulled alongside brief)
  let corners = $state<CornerData[]>([]);
  let pedals = $state<PedalBehavior | null>(null);

  // Checklist items (driver self-check)
  let checks = $state([
    { id: 'mirrors', label: 'Mirrors adjusted', done: false },
    { id: 'harness', label: 'Harness tight', done: false },
    { id: 'fuel', label: 'Fuel level checked', done: false },
    { id: 'tires', label: 'Tire pressures set', done: false },
    { id: 'comms', label: 'Radio / comms on', done: false },
  ]);

  let allChecked = $derived(checks.every(c => c.done));

  onMount(async () => {
    try {
      const [h, s] = await Promise.all([getHealth(), getSessions(20)]);
      health = h;
      sessions = s.sessions;
      if (sessions.length > 0) selectedSession = sessions[0].session_id;
    } catch { /* ignore */ }
    pageLoading = false;
  });

  async function generateBrief() {
    if (!selectedSession) return;
    loading = true;
    briefText = '';
    briefEmotion = '';
    briefReady = false;
    try {
      // Fetch brief + context in parallel
      const [brief, cornerData, pedalData] = await Promise.allSettled([
        getCoachBrief(selectedSession),
        getCorners(selectedSession),
        getPedalBehavior(selectedSession),
      ]);
      if (brief.status === 'fulfilled') {
        const b = brief.value as Record<string, unknown>;
        briefEmotion = String(b.emotion || 'focused');
        // If narrative_md is empty, synthesize from available fields
        let md = String(b.narrative_md || '');
        if (!md.trim()) {
          const parts: string[] = [];
          if (b.weather_note) parts.push(`**Conditions:** ${b.weather_note}`);
          if (b.surface_state) parts.push(`**Surface:** ${b.surface_state}`);
          const dangers = b.danger_zones_today as string[] | undefined;
          if (dangers?.length) parts.push(`**Danger Zones:**\n${dangers.map(d => `- ${d}`).join('\n')}`);
          const focus = b.focus as string[] | undefined;
          if (focus?.length) parts.push(`**Focus Areas:**\n${focus.map(f => `- ${f}`).join('\n')}`);
          if (b.weakest_recent_corner) parts.push(`**Work on:** ${b.weakest_recent_corner}`);
          if (b.biggest_recent_improvement) parts.push(`**Recent win:** ${b.biggest_recent_improvement}`);
          md = parts.length > 0 ? parts.join('\n\n') : 'Session loaded. Good luck out there!';
        }
        briefText = md;
        briefReady = true;
      } else {
        briefText = 'Coach unavailable — check your connection and try again.';
        briefEmotion = 'error';
        briefReady = true;
      }
      if (cornerData.status === 'fulfilled') corners = cornerData.value?.corners || [];
      if (pedalData.status === 'fulfilled') pedals = pedalData.value;
    } catch (e) {
      briefText = `Coach unavailable: ${e instanceof Error ? e.message : 'unknown error'}`;
      briefEmotion = 'error';
      briefReady = true;
    } finally {
      loading = false;
    }
  }

  function toggleCheck(id: string) {
    checks = checks.map(c => c.id === id ? { ...c, done: !c.done } : c);
  }

  // Weather stub (would come from real API)
  const weather = { temp: '72°F', condition: 'Clear', wind: '8 mph NW', grip: 'Dry' };
</script>

<svelte:head>
  <title>Pitwall — Pre-Brief</title>
</svelte:head>

<div class="brief-page fade-up">
  <!-- Header: Calm, focused energy -->
  <div class="brief-header">
    <div class="brief-header-icon">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" width="28" height="28"><path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z"/><line x1="4" y1="22" x2="4" y2="15"/></svg>
    </div>
    <div>
      <h1 class="brief-title">Pre-Race Brief</h1>
      <p class="text-2 text-sm">Focus. Prepare. Execute.</p>
    </div>
  </div>

  <!-- Track & Conditions at a glance -->
  <div class="conditions-strip">
    <div class="cond-item">
      <span class="cond-label">Track</span>
      <span class="cond-value">{health?.track || 'Sonoma'}</span>
    </div>
    <div class="cond-divider"></div>
    <div class="cond-item">
      <span class="cond-label">Temp</span>
      <span class="cond-value">{weather.temp}</span>
    </div>
    <div class="cond-divider"></div>
    <div class="cond-item">
      <span class="cond-label">Grip</span>
      <span class="cond-value cond-good">{weather.grip}</span>
    </div>
    <div class="cond-divider"></div>
    <div class="cond-item">
      <span class="cond-label">Wind</span>
      <span class="cond-value">{weather.wind}</span>
    </div>
  </div>

  <!-- Pre-flight Checklist -->
  <section class="section">
    <div class="section-header">
      <h2 class="section-title">Pre-flight</h2>
      <span class="section-badge" class:complete={allChecked}>
        {checks.filter(c => c.done).length}/{checks.length}
      </span>
    </div>
    <div class="checklist">
      {#each checks as check}
        <button class="check-row" class:checked={check.done} onclick={() => toggleCheck(check.id)}>
          <div class="check-box" class:filled={check.done}>
            {#if check.done}
              <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="3.5 8.5 6.5 11.5 12.5 4.5"/></svg>
            {/if}
          </div>
          <span class="check-label">{check.label}</span>
        </button>
      {/each}
    </div>
  </section>

  <!-- Session picker + Generate -->
  <section class="section">
    <h2 class="section-title">Session Focus</h2>
    <select bind:value={selectedSession}>
      <option value="">Choose your session…</option>
      {#each sessions as s}
        <option value={s.session_id}>
          {s.session_id.replace('sonoma-import-', '')}
          {s.best_lap_s ? `· ${Math.floor(s.best_lap_s / 60)}:${(s.best_lap_s % 60).toFixed(1).padStart(4, '0')}` : ''}
        </option>
      {/each}
    </select>

    <button
      class="btn btn-brief"
      onclick={generateBrief}
      disabled={!selectedSession || loading}
    >
      {#if loading}
        <span class="spinner"></span>
        <span>Consulting your coach…</span>
      {:else}
        <span>Get Race Brief</span>
      {/if}
    </button>
  </section>

  <!-- Loading state -->
  {#if loading}
    <div class="loading-card">
      <div class="loading-pulse"></div>
      <div class="loading-lines">
        <div class="skel skel-1"></div>
        <div class="skel skel-2"></div>
        <div class="skel skel-3"></div>
        <div class="skel skel-4"></div>
      </div>
      <p class="text-3 text-xs" style="text-align:center; margin-top: var(--sp-4);">
        On-device Gemma inference · analyzing your recent laps
      </p>
    </div>
  {/if}

  <!-- Coach Brief Response -->
  {#if briefReady && !loading}
    <section class="brief-response fade-up">
      <!-- Emotion indicator -->
      <div class="brief-emotion-bar" data-emotion={briefEmotion}>
        <div class="emotion-dot"></div>
        <span class="emotion-label">{briefEmotion}</span>
        <span class="emotion-source">via {health?.engine || 'sonic_model'}</span>
      </div>

      <!-- Coach message (rendered markdown) -->
      <div class="brief-message md-body">
        {@html marked.parse(briefText || '')}
      </div>
    </section>

    <!-- Focus Corners (from telemetry) -->
    {#if corners.length > 0}
      <section class="section fade-up" style="animation-delay: 0.1s;">
        <h2 class="section-title">Corner Focus</h2>
        <p class="text-2 text-sm" style="margin-bottom: var(--sp-3);">Based on your recent data</p>
        <div class="corner-grid">
          {#each corners.slice(0, 6) as c}
            <div class="corner-chip" class:graded={c.grade}>
              <span class="corner-name">{c.name}</span>
              <span class="corner-passes text-3">{c.n_passes} passes</span>
              {#if c.grade}
                <span class="corner-grade" class:good={c.grade === 'A' || c.grade === 'B'} class:warn={c.grade === 'C'} class:bad={c.grade === 'D' || c.grade === 'F'}>
                  {c.grade}
                </span>
              {/if}
            </div>
          {/each}
        </div>
      </section>
    {/if}

    <!-- Pedal Reminder -->
    {#if pedals && pedals.states.coast.pct > 10}
      <div class="callout callout-warn fade-up" style="animation-delay: 0.2s;">
        <div class="callout-icon" style="font-weight:700; color: var(--warn);">!</div>
        <div>
          <strong>Coasting at {pedals.states.coast.pct.toFixed(1)}%</strong>
          <p class="text-sm text-2" style="margin-top: 4px;">Focus: Pick a pedal. Trail-brake into corners, get back on throttle sooner.</p>
        </div>
      </div>
    {/if}
  {/if}

  <!-- Empty state -->
  {#if !briefReady && !loading}
    <div class="empty-brief">
      <div class="empty-track-art">
        <svg viewBox="0 0 200 100" fill="none">
          <path d="M 30,80 Q 10,60 15,40 Q 25,15 60,12 Q 100,8 140,15 Q 175,25 185,55 Q 190,75 170,85 Q 140,90 100,88 Q 60,85 40,88 Z"
                stroke="var(--surface-4)" stroke-width="2" stroke-linecap="round" stroke-dasharray="6 4" opacity="0.5"/>
        </svg>
      </div>
      <p class="text-2">Complete your checklist and tap <strong>Get Race Brief</strong></p>
      <p class="text-3 text-xs" style="margin-top: 4px;">Your coach will analyze recent laps and set focus areas.</p>
    </div>
  {/if}
</div>

<style>
  .brief-page { padding-bottom: var(--sp-10); }

  /* ── Header ── */
  .brief-header {
    display: flex;
    align-items: center;
    gap: var(--sp-4);
    margin-bottom: var(--sp-5);
  }

  .brief-header-icon {
    width: 52px;
    height: 52px;
    border-radius: var(--r-md);
    background: var(--surface-2);
    border: 1px solid var(--surface-4);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 24px;
    flex-shrink: 0;
  }

  .brief-title {
    font-family: 'Space Mono', monospace;
    font-size: 24px;
    font-weight: 700;
    letter-spacing: -0.02em;
    color: var(--text-1);
  }

  /* ── Conditions Strip ── */
  .conditions-strip {
    display: flex;
    align-items: center;
    gap: var(--sp-3);
    padding: var(--sp-3) var(--sp-4);
    background: var(--surface-2);
    border: 1px solid var(--surface-3);
    border-radius: var(--r-md);
    margin-bottom: var(--sp-5);
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
  }

  .cond-item {
    display: flex;
    flex-direction: column;
    gap: 1px;
    min-width: 0;
    flex-shrink: 0;
  }

  .cond-label {
    font-size: 10px;
    color: var(--text-3);
    text-transform: uppercase;
    letter-spacing: 0.08em;
    font-weight: 600;
  }

  .cond-value {
    font-size: 14px;
    font-weight: 600;
    color: var(--text-1);
    white-space: nowrap;
  }

  .cond-good { color: var(--accent); }

  .cond-divider {
    width: 1px;
    height: 28px;
    background: var(--surface-4);
    flex-shrink: 0;
  }

  /* ── Sections ── */
  .section {
    margin-bottom: var(--sp-5);
  }

  .section-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: var(--sp-3);
  }

  .section-title {
    font-size: 12px;
    font-weight: 700;
    color: var(--text-3);
    text-transform: uppercase;
    letter-spacing: 0.1em;
  }

  .section-badge {
    font-size: 12px;
    font-weight: 600;
    color: var(--text-3);
    font-family: 'JetBrains Mono', monospace;
  }

  .section-badge.complete { color: var(--accent); }

  /* ── Checklist ── */
  .checklist {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .check-row {
    display: flex;
    align-items: center;
    gap: var(--sp-3);
    padding: var(--sp-3) var(--sp-4);
    background: var(--surface-2);
    border: 1px solid var(--surface-3);
    border-radius: var(--r-sm);
    min-height: 48px;
    cursor: pointer;
    transition: all 0.15s;
    font-family: 'Space Grotesk', sans-serif;
    font-size: 15px;
    color: var(--text-1);
  }

  .check-row:active { transform: scale(0.98); }

  .check-row.checked {
    border-color: rgba(45, 212, 168, 0.2);
    background: rgba(45, 212, 168, 0.04);
  }

  .check-row.checked .check-label { color: var(--text-2); }

  .check-box {
    width: 22px;
    height: 22px;
    border: 2px solid var(--surface-4);
    border-radius: 5px;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all 0.15s;
  }

  .check-box.filled {
    background: var(--accent);
    border-color: var(--accent);
  }

  .check-box svg {
    width: 14px;
    height: 14px;
    color: var(--surface-0);
  }

  .check-label { flex: 1; }

  /* ── Brief Button ── */
  .btn-brief {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: var(--sp-2);
    width: 100%;
    min-height: 56px;
    margin-top: var(--sp-3);
    border: none;
    border-radius: var(--r-md);
    background: linear-gradient(135deg, var(--accent) 0%, #1aaa88 100%);
    color: var(--surface-0);
    font-family: 'Space Grotesk', sans-serif;
    font-size: 16px;
    font-weight: 700;
    cursor: pointer;
    transition: all 0.2s;
    box-shadow: 0 4px 20px rgba(45, 212, 168, 0.25);
  }

  .btn-brief:active { transform: scale(0.97); box-shadow: 0 2px 10px rgba(45, 212, 168, 0.15); }
  .btn-brief:disabled { opacity: 0.5; cursor: default; transform: none; }

  .spinner {
    width: 18px; height: 18px;
    border: 2px solid rgba(0,0,0,0.15);
    border-top-color: var(--surface-0);
    border-radius: 50%;
    animation: spin 0.7s linear infinite;
  }

  @keyframes spin { to { transform: rotate(360deg); } }

  /* ── Loading skeleton ── */
  .loading-card {
    padding: var(--sp-6);
    background: var(--surface-2);
    border: 1px solid var(--surface-3);
    border-radius: var(--r-md);
    margin-bottom: var(--sp-4);
  }

  .loading-pulse {
    width: 48px; height: 6px;
    background: var(--accent);
    border-radius: 3px;
    opacity: 0.4;
    animation: pulse-glow 1.5s ease-in-out infinite;
    margin-bottom: var(--sp-5);
  }

  .loading-lines { display: flex; flex-direction: column; gap: var(--sp-3); }

  .skel {
    height: 14px;
    background: var(--surface-4);
    border-radius: 4px;
    animation: skel-shimmer 1.5s ease-in-out infinite;
  }

  .skel-1 { width: 90%; }
  .skel-2 { width: 75%; animation-delay: 0.1s; }
  .skel-3 { width: 85%; animation-delay: 0.2s; }
  .skel-4 { width: 60%; animation-delay: 0.3s; }

  @keyframes skel-shimmer {
    0%, 100% { opacity: 0.3; }
    50% { opacity: 0.6; }
  }

  /* ── Brief Response ── */
  .brief-response {
    margin-bottom: var(--sp-4);
  }

  .brief-emotion-bar {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
    padding: var(--sp-3) var(--sp-4);
    background: var(--surface-2);
    border: 1px solid var(--surface-3);
    border-radius: var(--r-sm) var(--r-sm) 0 0;
  }

  .emotion-dot {
    width: 8px; height: 8px;
    border-radius: 50%;
    background: var(--info);
    flex-shrink: 0;
  }

  [data-emotion="focused"] .emotion-dot { background: var(--info); }
  [data-emotion="calm"] .emotion-dot { background: var(--text-2); }
  [data-emotion="stern"] .emotion-dot { background: var(--bad); }
  [data-emotion="encouraging"] .emotion-dot { background: var(--accent); }
  [data-emotion="concerned"] .emotion-dot { background: var(--warn); }

  .emotion-label {
    font-size: 12px;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.06em;
    color: var(--text-2);
  }

  .emotion-source {
    font-size: 11px;
    color: var(--text-3);
    margin-left: auto;
  }

  .brief-message {
    padding: var(--sp-5);
    background: var(--surface-1);
    border: 1px solid var(--surface-3);
    border-top: none;
    border-radius: 0 0 var(--r-sm) var(--r-sm);
    font-size: 15px;
    line-height: 1.75;
    color: var(--text-1);
  }

  .brief-message p { margin-bottom: var(--sp-3); }
  .brief-message p:last-child { margin-bottom: 0; }

  /* ── Corner Grid ── */
  .corner-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: var(--sp-2);
  }

  .corner-chip {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 2px;
    padding: var(--sp-3);
    background: var(--surface-2);
    border: 1px solid var(--surface-3);
    border-radius: var(--r-sm);
    text-align: center;
  }

  .corner-name {
    font-size: 13px;
    font-weight: 600;
    color: var(--text-1);
  }

  .corner-passes {
    font-size: 10px;
    font-family: 'JetBrains Mono', monospace;
  }

  .corner-grade {
    font-size: 12px;
    font-weight: 800;
    font-family: 'JetBrains Mono', monospace;
    margin-top: 2px;
  }

  .corner-grade.good { color: var(--accent); }
  .corner-grade.warn { color: var(--warn); }
  .corner-grade.bad { color: var(--bad); }

  /* ── Callout ── */
  .callout {
    display: flex;
    gap: var(--sp-3);
    padding: var(--sp-4);
    border-radius: var(--r-md);
    margin-bottom: var(--sp-4);
  }

  .callout-warn {
    background: rgba(240, 168, 48, 0.06);
    border: 1px solid rgba(240, 168, 48, 0.2);
  }

  .callout-icon {
    font-size: 20px;
    flex-shrink: 0;
    line-height: 1;
  }

  /* ── Empty State ── */
  .empty-brief {
    text-align: center;
    padding: var(--sp-8) var(--sp-4);
  }

  .empty-track-art {
    margin: 0 auto var(--sp-4);
    max-width: 200px;
    opacity: 0.4;
  }
</style>
