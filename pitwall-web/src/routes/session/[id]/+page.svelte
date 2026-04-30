<script lang="ts">
  import { onMount } from 'svelte';
  import { page } from '$app/state';
  import { getLapTimeTable, getIdealLap, getPedalBehavior, getThrottleCornerBox, getBrakeAcceleration, type LapTimeTable, type IdealLap, type PedalBehavior, type ThrottleCornerBox, type BrakeAcceleration } from '$lib/api';

  let sid = $derived(decodeURIComponent(page.params.id));
  let lapTable = $state<LapTimeTable | null>(null);
  let ideal = $state<IdealLap | null>(null);
  let pedals = $state<PedalBehavior | null>(null);
  let corners = $state<ThrottleCornerBox[] | null>(null);
  let brakes = $state<BrakeAcceleration | null>(null);
  let loading = $state(true);
  let error = $state('');
  let activeTab = $state<'laps' | 'pedals' | 'corners' | 'brakes'>('laps');

  onMount(async () => {
    try {
      const results = await Promise.allSettled([
        getLapTimeTable(sid),
        getIdealLap(sid),
        getPedalBehavior(sid),
        getThrottleCornerBox(sid),
        getBrakeAcceleration(sid),
      ]);
      if (results[0].status === 'fulfilled') lapTable = results[0].value;
      if (results[1].status === 'fulfilled') ideal = results[1].value;
      if (results[2].status === 'fulfilled') pedals = results[2].value;
      if (results[3].status === 'fulfilled') corners = results[3].value.corners;
      if (results[4].status === 'fulfilled') brakes = results[4].value;
    } catch (e) {
      error = e instanceof Error ? e.message : 'Failed to load';
    } finally {
      loading = false;
    }
  });

  function fmt(s: number): string {
    const min = Math.floor(s / 60);
    const sec = (s % 60).toFixed(2);
    return `${min}:${sec.padStart(5, '0')}`;
  }

  function delta(s: number): string {
    if (s === 0) return '—';
    return `+${s.toFixed(2)}`;
  }
</script>

<svelte:head>
  <title>Pitwall — Session</title>
</svelte:head>

<div class="session-page fade-up">
  <!-- Back + Title -->
  <div style="margin-bottom: var(--sp-4);">
    <a href="/" class="back-link">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
      Back
    </a>
  </div>

  <h1 class="page-title" style="font-size: 22px;">Session</h1>
  <p class="text-2 text-sm truncate" style="margin-bottom: var(--sp-4); max-width: 100%;">{sid.replace('sonoma-import-', '')}</p>

  {#if loading}
    <div class="center-state">
      <div class="spinner-lg"></div>
      <p class="text-2 text-sm" style="margin-top: 12px;">Loading telemetry…</p>
    </div>
  {:else if error}
    <div class="card" style="text-align: center; padding: var(--sp-8);">
      <p class="text-bad">{error}</p>
    </div>
  {:else}
    <!-- Ideal Lap Banner -->
    {#if ideal}
      <div class="ideal-banner card-glow" style="margin-bottom: var(--sp-4);">
        <div class="ideal-row">
          <div class="metric">
            <span class="metric-label">Ideal</span>
            <span class="metric-value accent" style="font-size: 22px;">{fmt(ideal.ideal_lap_s)}</span>
          </div>
          <div class="metric" style="text-align: right;">
            <span class="metric-label">Gap</span>
            <span class="metric-value warn" style="font-size: 18px;">{(ideal.gain_potential_s ?? 0).toFixed(2)}s</span>
          </div>
        </div>
        <div class="bar-track" style="margin-top: var(--sp-3);">
          <div class="bar-fill bar-fill-accent" style="width: {Math.min(100, ((ideal.ideal_lap_s ?? 1) / (ideal.best_actual_lap_s || 1)) * 100)}%;"></div>
        </div>
        <div class="row" style="justify-content: space-between; margin-top: var(--sp-1);">
          <span class="text-xs text-accent">Ideal: {fmt(ideal.ideal_lap_s)}</span>
          <span class="text-xs text-2">Actual: {fmt(ideal.best_actual_lap_s)}</span>
        </div>
      </div>
    {/if}

    <!-- Tab Bar -->
    <div class="tab-bar">
      {#each [['laps', 'Laps'], ['pedals', 'Pedals'], ['corners', 'Corners'], ['brakes', 'Brakes']] as [key, label]}
        <button class="tab-item" class:active={activeTab === key} onclick={() => activeTab = key as typeof activeTab}>{label}</button>
      {/each}
    </div>

    <!-- TAB: Laps -->
    {#if activeTab === 'laps'}
      {#if lapTable && lapTable.laps?.length}
        <div class="card" style="padding: 0; overflow: hidden;">
          <div class="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>#</th>
                  <th>Time</th>
                  <th>Delta</th>
                </tr>
              </thead>
              <tbody>
                {#each lapTable.laps as lap}
                  <tr class:best={lap.is_best}>
                    <td class="font-mono">{lap.lap_number}</td>
                    <td class="font-mono" style="color: {lap.is_best ? 'var(--accent)' : 'var(--text-1)'};">{fmt(lap.lap_time_s)}</td>
                    <td class="font-mono text-3">{delta(lap.delta_to_best_s)}</td>
                  </tr>
                {/each}
              </tbody>
            </table>
          </div>
        </div>
      {:else}
        <div class="empty-tab">
          <p class="empty-title">No Laps Detected</p>
          <p class="text-2 text-sm">Complete at least one full lap to see lap time data and delta analysis.</p>
        </div>
      {/if}
    {/if}

    <!-- TAB: Pedals -->
    {#if activeTab === 'pedals'}
      {#if pedals?.states}
        <div class="card stack gap-4">
          <h3 class="section-title" style="margin-bottom: 0;">Pedal Distribution</h3>
          {#each [
            { label: 'Throttle', pct: pedals.states.throttle_only?.pct ?? 0, time: pedals.states.throttle_only?.time_s ?? 0, cls: 'bar-fill-accent' },
            { label: 'Brake', pct: pedals.states.brake_only?.pct ?? 0, time: pedals.states.brake_only?.time_s ?? 0, cls: 'bar-fill-bad' },
            { label: 'Trail Brake', pct: pedals.states.trail_brake?.pct ?? 0, time: pedals.states.trail_brake?.time_s ?? 0, cls: 'bar-fill-warn' },
            { label: 'Coast', pct: pedals.states.coast?.pct ?? 0, time: pedals.states.coast?.time_s ?? 0, cls: 'bar-fill-info' },
          ] as item}
            <div>
              <div class="row" style="justify-content: space-between; margin-bottom: 4px;">
                <span class="text-sm">{item.label}</span>
                <span class="font-mono text-sm text-2">{item.pct.toFixed(1)}%</span>
              </div>
              <div class="bar-track" style="height: 12px;">
                <div class="{item.cls} bar-fill" style="width: {item.pct}%;"></div>
              </div>
            </div>
          {/each}

          {#if (pedals.states.coast?.pct ?? 0) > 15}
            <div class="alert-card">
              <span class="text-bad text-sm" style="font-weight: 600;">⚠ Coast at {(pedals.states.coast?.pct ?? 0).toFixed(1)}%</span>
              <p class="text-2 text-xs" style="margin-top: 4px;">Pick a pedal. Trail-brake into corners instead of lifting early.</p>
            </div>
          {/if}
        </div>
      {:else}
        <div class="empty-tab">
          <p class="empty-title">No Pedal Data</p>
          <p class="text-2 text-sm">Pedal behaviour analysis requires throttle/brake telemetry from a completed session.</p>
        </div>
      {/if}
    {/if}

    <!-- TAB: Corners -->
    {#if activeTab === 'corners'}
      {#if corners && corners.length > 0}
        <div class="stack gap-2">
          {#each corners as c}
            <div class="card" style="padding: var(--sp-3) var(--sp-4);">
              <div class="row" style="justify-content: space-between; margin-bottom: 6px;">
                <span style="font-weight: 600; font-size: 14px;">{c.name}</span>
                <span class="text-xs text-3">{c.n_passes} passes</span>
              </div>
              <div class="bar-track" style="height: 20px; position: relative; background: var(--surface-3);">
                <!-- Whisker -->
                <div style="position: absolute; left: {c.min_pct}%; width: {c.max_pct - c.min_pct}%; top: 8px; height: 4px; background: var(--surface-4); border-radius: 2px;"></div>
                <!-- IQR box -->
                <div style="position: absolute; left: {c.q1_pct}%; width: {Math.max(c.q3_pct - c.q1_pct, 2)}%; top: 2px; height: 16px; background: var(--accent); opacity: 0.5; border-radius: 3px;"></div>
                <!-- Median -->
                <div style="position: absolute; left: {c.median_pct}%; top: 0; width: 2px; height: 20px; background: var(--text-1); border-radius: 1px;"></div>
              </div>
              <div class="row" style="justify-content: space-between; margin-top: 4px;">
                <span class="text-xs text-3">Min {(c.min_pct ?? 0).toFixed(0)}%</span>
                <span class="text-xs font-mono text-accent">Med {(c.median_pct ?? 0).toFixed(0)}%</span>
                <span class="text-xs text-3">Max {(c.max_pct ?? 0).toFixed(0)}%</span>
              </div>
            </div>
          {/each}
        </div>
      {:else}
        <div class="empty-tab">
          <p class="empty-title">No Corner Data</p>
          <p class="text-2 text-sm">Corner analysis requires multiple passes through detected turns. Complete a few laps first.</p>
        </div>
      {/if}
    {/if}

    <!-- TAB: Brakes -->
    {#if activeTab === 'brakes'}
      {#if brakes?.brake_zones?.length}
        <div class="stack gap-4">
          <div class="card">
            <h3 class="section-title" style="margin-bottom: var(--sp-3);">Brake Zones</h3>
            {#each brakes.brake_zones as bz}
              <div class="brake-item">
                <span class="text-sm" style="width: 60px; flex-shrink: 0;">{bz.corner}</span>
                <div class="bar-track" style="flex: 1; height: 12px;">
                  <div class="bar-fill bar-fill-bad" style="width: {Math.min(Math.abs(bz.max_decel_g ?? 0) / 2 * 100, 100)}%;"></div>
                </div>
                <span class="font-mono text-sm text-2" style="width: 50px; text-align: right;">{(bz.max_decel_g ?? 0).toFixed(2)}g</span>
              </div>
            {/each}
          </div>

          {#if brakes.corner_exits?.length}
            <div class="card">
              <h3 class="section-title" style="margin-bottom: var(--sp-3);">Exit Speed</h3>
              {#each brakes.corner_exits as ce}
                <div class="brake-item">
                  <span class="text-sm" style="width: 60px; flex-shrink: 0;">{ce.corner}</span>
                  <div class="bar-track" style="flex: 1; height: 12px;">
                    <div class="bar-fill bar-fill-accent" style="width: {Math.min((ce.max_long_accel_g ?? 0) / 1.5 * 100, 100)}%;"></div>
                  </div>
                  <span class="font-mono text-sm text-2" style="width: 50px; text-align: right;">{(ce.max_long_accel_g ?? 0).toFixed(2)}g</span>
                </div>
              {/each}
            </div>
          {/if}
        </div>
      {:else}
        <div class="empty-tab">
          <p class="empty-title">No Brake Data</p>
          <p class="text-2 text-sm">Brake zone analysis requires deceleration data from detected corners. Complete some laps to generate this.</p>
        </div>
      {/if}
    {/if}
  {/if}
</div>

<style>
  .session-page {
    padding-bottom: var(--sp-8);
  }

  .page-title {
    font-family: 'Space Mono', monospace;
    font-weight: 700;
    letter-spacing: -0.02em;
    color: var(--text-1);
    margin-bottom: 2px;
  }

  .back-link {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    color: var(--text-2);
    text-decoration: none;
    font-size: 14px;
    font-weight: 500;
    min-height: 44px;
  }

  .back-link:active { color: var(--accent); }

  .center-state {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    min-height: 200px;
  }

  .spinner-lg {
    width: 32px; height: 32px;
    border: 3px solid var(--surface-4);
    border-top-color: var(--accent);
    border-radius: 50%;
    animation: spin 0.8s linear infinite;
  }

  @keyframes spin { to { transform: rotate(360deg); } }

  .ideal-banner .ideal-row {
    display: flex;
    justify-content: space-between;
    align-items: flex-end;
  }

  .section-title {
    font-size: 12px;
    font-weight: 600;
    color: var(--text-3);
    text-transform: uppercase;
    letter-spacing: 0.1em;
  }

  /* ── Tab Bar ── */
  .tab-bar {
    display: flex;
    gap: 2px;
    background: var(--surface-2);
    border-radius: var(--r-sm);
    padding: 3px;
    margin-bottom: var(--sp-4);
    overflow-x: auto;
  }

  .tab-item {
    flex: 1;
    min-height: 40px;
    min-width: 0;
    border: none;
    border-radius: 6px;
    background: transparent;
    color: var(--text-3);
    font-family: 'Space Grotesk', sans-serif;
    font-size: 13px;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.15s;
    white-space: nowrap;
  }

  .tab-item.active {
    background: var(--surface-4);
    color: var(--accent);
  }

  .tab-item:active { transform: scale(0.96); }

  /* ── Table ── */
  .table-scroll {
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
  }

  tr.best td {
    background: rgba(45, 212, 168, 0.06);
  }

  /* ── Alert ── */
  .alert-card {
    padding: var(--sp-3) var(--sp-4);
    background: rgba(239, 68, 68, 0.08);
    border: 1px solid rgba(239, 68, 68, 0.2);
    border-radius: var(--r-sm);
  }

  /* ── Brake items ── */
  .brake-item {
    display: flex;
    align-items: center;
    gap: var(--sp-3);
    margin-bottom: var(--sp-3);
  }

  .brake-item:last-child { margin-bottom: 0; }

  /* ── Empty States ── */
  .empty-tab {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    text-align: center;
    padding: var(--sp-8) var(--sp-4);
    background: var(--surface-1);
    border: 1px dashed var(--surface-4);
    border-radius: var(--r-md);
    min-height: 180px;
  }

  .empty-title {
    font-family: 'Space Mono', monospace;
    font-size: 16px;
    font-weight: 700;
    color: var(--text-2);
    margin-bottom: var(--sp-2);
  }
</style>
