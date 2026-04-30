<script lang="ts">
  import { onMount } from 'svelte';
  import { getHealth, getSessions, type HealthResponse, type Session } from '$lib/api';

  let health = $state<HealthResponse | null>(null);
  let sessions = $state<Session[]>([]);
  let loading = $state(true);
  let error = $state('');

  onMount(async () => {
    try {
      const [h, s] = await Promise.all([getHealth(), getSessions(50)]);
      health = h;
      sessions = s.sessions;
    } catch (e) {
      error = e instanceof Error ? e.message : 'Bridge offline';
    }
    loading = false;
  });

  function ago(iso: string): string {
    const diff = Date.now() - new Date(iso).getTime();
    const h = Math.floor(diff / 3600000);
    if (h < 1) return 'just now';
    if (h < 24) return `${h}h ago`;
    const d = Math.floor(h / 24);
    return `${d}d ago`;
  }

  function fmt(s: number | null): string {
    if (!s) return '—';
    const min = Math.floor(s / 60);
    const sec = (s % 60).toFixed(1).padStart(4, '0');
    return `${min}:${sec}`;
  }

  // Feature groups for the "All Features" section
  const features = [
    {
      category: 'Race Day',
      items: [
        { label: 'Pre-Race Brief', href: '/brief', desc: 'Checklist & coach focus plan' },
        { label: 'On-Track HUD', href: '/hud', desc: 'Live telemetry & track map' },
        { label: 'Post-Race Debrief', href: '/debrief', desc: 'Highlights & analytics' },
      ]
    },
    {
      category: 'Session Analysis',
      items: [
        { label: 'Lap Time Table', href: '#session', desc: 'Sector splits & deltas' },
        { label: 'Ideal Lap', href: '#session', desc: 'Theoretical best from best sectors' },
        { label: 'Pedal Behavior', href: '#session', desc: 'Throttle / brake / coast breakdown' },
        { label: 'Corner Analysis', href: '#session', desc: 'Per-corner grades & passes' },
        { label: 'Brake Zones', href: '#session', desc: 'Decel G & duration by corner' },
        { label: 'Lap Distribution', href: '#session', desc: 'Box plot: spread & outliers' },
      ]
    },
    {
      category: 'Advanced',
      items: [
        { label: 'Friction Circle', href: '#session', desc: 'G-G scatter plot' },
        { label: 'Hustle Map', href: '#session', desc: 'Track overlay with intensity' },
        { label: 'Straight Line Speed', href: '#session', desc: 'Top speed by straight' },
        { label: 'Corner Classification', href: '#session', desc: 'Slow/medium/fast bands' },
        { label: 'Throttle Corner Box', href: '#session', desc: 'Throttle % per corner (box plot)' },
        { label: 'End of Braking', href: '#session', desc: 'Where you lift off brake' },
      ]
    },
    {
      category: 'Coach & Signals',
      items: [
        { label: 'Coach Concepts', href: '#concepts', desc: '9 Bentley driving rules' },
        { label: 'Track Markers', href: '#markers', desc: 'Brake / apex / track-out points' },
        { label: 'Signal Registry', href: '#signals', desc: 'All available telemetry channels' },
        { label: 'Session Capabilities', href: '#session', desc: 'What data a session has' },
        { label: 'Driver Evolution', href: '#driver', desc: 'Best lap over time' },
        { label: 'AI Session Score', href: '#session', desc: 'Session grade 0–100' },
      ]
    },
  ];
</script>

<svelte:head>
  <title>Pitwall — Garage</title>
</svelte:head>

<div class="garage fade-up">
  <!-- Hero Header -->
  <div class="hero">
    <h1 class="hero-title">Pitwall</h1>
    <p class="hero-sub">AI-Powered Track Coaching Platform</p>
  </div>

  <!-- System Status -->
  <div class="status-strip" class:offline={!health}>
    {#if loading}
      <div class="spinner-sm"></div>
      <span class="text-2">Connecting…</span>
    {:else if health}
      <div class="status-dot online"></div>
      <div class="status-info">
        <span class="status-track">{health.track || 'No track'}</span>
        <span class="status-detail">{health.engine} · v{health.version} · {health.driver_level}</span>
      </div>
      <span class="status-badge ok">Online</span>
    {:else}
      <div class="status-dot offline-dot"></div>
      <span class="text-bad">Bridge offline</span>
      <span class="status-badge err">Error</span>
    {/if}
  </div>

  <!-- Quick Stats -->
  {#if health}
    <div class="stat-grid">
      <div class="stat-card">
        <span class="stat-label">Status</span>
        <span class="stat-value text-accent">● OK</span>
      </div>
      <div class="stat-card">
        <span class="stat-label">Engine</span>
        <span class="stat-value font-mono">{health.engine}</span>
      </div>
      <div class="stat-card">
        <span class="stat-label">Version</span>
        <span class="stat-value font-mono">{health.version}</span>
      </div>
      <div class="stat-card">
        <span class="stat-label">Sessions</span>
        <span class="stat-value font-mono">{sessions.length}</span>
      </div>
    </div>
  {/if}

  <!-- Quick Actions -->
  <div class="section">
    <h2 class="section-title">Quick Actions</h2>
    <div class="action-grid">
      <a href="/brief" class="action-card action-brief">
        <svg class="action-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
        <span class="action-label">Pre-Brief</span>
        <span class="action-desc">Get ready</span>
      </a>
      <a href="/hud" class="action-card action-hud">
        <svg class="action-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg>
        <span class="action-label">HUD</span>
        <span class="action-desc">Go live</span>
      </a>
      <a href="/debrief" class="action-card action-debrief">
        <svg class="action-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M22 11.08V12a10 10 0 11-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
        <span class="action-label">Debrief</span>
        <span class="action-desc">Review</span>
      </a>
    </div>
  </div>

  <!-- Sessions List -->
  <div class="section">
    <h2 class="section-title">Recent Sessions</h2>
    {#if sessions.length === 0 && !loading}
      <div class="empty-card">
        <p class="text-2">No sessions yet. Import a VBO file to get started.</p>
      </div>
    {:else}
      <div class="session-list">
        {#each sessions.slice(0, 8) as s, i}
          <a
            href="/session/{s.session_id}"
            class="session-row fade-up"
            style="animation-delay: {i * 0.03}s"
          >
            <div class="sr-left">
              <span class="sr-name">{s.session_id.replace('sonoma-import-', '')}</span>
              <span class="sr-meta text-3">
                {s.lap_count > 0 ? `${s.lap_count} laps` : 'No laps'} ·
                {s.started_at ? ago(s.started_at) : '—'}
              </span>
            </div>
            <div class="sr-right">
              {#if s.best_lap_s}
                <span class="sr-best font-mono">{fmt(s.best_lap_s)}</span>
              {/if}
              <span class="sr-badge" class:live={!s.ended_at}>{s.ended_at ? 'Done' : 'Live'}</span>
            </div>
          </a>
        {/each}
      </div>
      {#if sessions.length > 8}
        <p class="text-3 text-sm" style="text-align: center; margin-top: var(--sp-3);">
          + {sessions.length - 8} more sessions
        </p>
      {/if}
    {/if}
  </div>

  <!-- All Features (grouped) -->
  <div class="section">
    <h2 class="section-title">All Features</h2>

    {#each features as group}
      <div class="feature-group">
        <h3 class="fg-title">{group.category}</h3>
        <div class="fg-grid">
          {#each group.items as item}
            <a
              href={item.href.startsWith('#') ? (sessions.length > 0 ? `/session/${sessions[0].session_id}` : '#') : item.href}
              class="feature-card"
            >
              <div class="fc-text">
                <span class="fc-label">{item.label}</span>
                <span class="fc-desc">{item.desc}</span>
              </div>
            </a>
          {/each}
        </div>
      </div>
    {/each}
  </div>
</div>

<style>
  .garage { padding-bottom: var(--sp-10); }

  /* ── Hero ── */
  .hero {
    text-align: center;
    padding: var(--sp-6) 0 var(--sp-4);
  }

  .hero-title {
    font-family: 'Space Mono', monospace;
    font-size: 36px;
    font-weight: 700;
    letter-spacing: -0.03em;
    color: var(--accent);
    margin-bottom: 4px;
  }

  .hero-sub {
    font-size: 14px;
    color: var(--text-3);
  }

  /* ── Status Strip ── */
  .status-strip {
    display: flex;
    align-items: center;
    gap: var(--sp-3);
    padding: var(--sp-3) var(--sp-4);
    background: var(--surface-2);
    border: 1px solid var(--surface-3);
    border-radius: var(--r-md);
    margin-bottom: var(--sp-5);
  }

  .status-dot {
    width: 10px; height: 10px;
    border-radius: 50%;
    flex-shrink: 0;
  }

  .status-dot.online {
    background: var(--accent);
    box-shadow: 0 0 8px var(--accent);
    animation: pulse-dot 2s ease-in-out infinite;
  }

  .status-dot.offline-dot {
    background: var(--bad);
    box-shadow: 0 0 8px var(--bad);
  }

  .status-info {
    display: flex;
    flex-direction: column;
    gap: 1px;
    flex: 1;
    min-width: 0;
  }

  .status-track {
    font-size: 14px;
    font-weight: 600;
    color: var(--text-1);
  }

  .status-detail {
    font-size: 11px;
    color: var(--text-3);
  }

  .status-badge {
    font-size: 11px;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.06em;
    padding: 2px 8px;
    border-radius: 4px;
    flex-shrink: 0;
  }

  .status-badge.ok {
    background: rgba(45, 212, 168, 0.12);
    color: var(--accent);
  }

  .status-badge.err {
    background: rgba(239, 68, 68, 0.12);
    color: var(--bad);
  }

  .spinner-sm {
    width: 14px; height: 14px;
    border: 2px solid var(--surface-4);
    border-top-color: var(--accent);
    border-radius: 50%;
    animation: spin 0.7s linear infinite;
  }

  @keyframes spin { to { transform: rotate(360deg); } }

  /* ── Stat Grid ── */
  .stat-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: var(--sp-3);
    margin-bottom: var(--sp-5);
  }

  .stat-card {
    padding: var(--sp-4);
    background: var(--surface-2);
    border: 1px solid var(--surface-3);
    border-radius: var(--r-md);
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .stat-label {
    font-size: 10px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.08em;
    color: var(--text-3);
  }

  .stat-value {
    font-size: 18px;
    font-weight: 700;
    color: var(--text-1);
  }

  /* ── Sections ── */
  .section { margin-bottom: var(--sp-6); }

  .section-title {
    font-size: 12px;
    font-weight: 700;
    color: var(--text-3);
    text-transform: uppercase;
    letter-spacing: 0.1em;
    margin-bottom: var(--sp-3);
  }

  /* ── Quick Actions ── */
  .action-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: var(--sp-3);
  }

  .action-card {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: var(--sp-2);
    padding: var(--sp-4) var(--sp-2);
    background: var(--surface-2);
    border: 1px solid var(--surface-3);
    border-radius: var(--r-md);
    text-decoration: none;
    transition: all 0.15s;
    min-height: 100px;
    justify-content: center;
  }

  .action-card:active { transform: scale(0.96); }

  .action-brief { border-color: rgba(45, 212, 168, 0.2); }
  .action-hud { border-color: rgba(96, 165, 250, 0.2); }
  .action-debrief { border-color: rgba(240, 168, 48, 0.2); }

  .action-brief:hover { background: rgba(45, 212, 168, 0.04); }
  .action-hud:hover { background: rgba(96, 165, 250, 0.04); }
  .action-debrief:hover { background: rgba(240, 168, 48, 0.04); }

  .action-svg { width: 24px; height: 24px; flex-shrink: 0; }
  .action-brief .action-svg { color: var(--accent); }
  .action-hud .action-svg { color: var(--info); }
  .action-debrief .action-svg { color: rgb(240, 168, 48); }
  .action-label { font-size: 14px; font-weight: 600; color: var(--text-1); }
  .action-desc { font-size: 11px; color: var(--text-3); }

  /* ── Session List ── */
  .session-list { display: flex; flex-direction: column; gap: var(--sp-2); }

  .session-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: var(--sp-3) var(--sp-4);
    background: var(--surface-2);
    border: 1px solid var(--surface-3);
    border-radius: var(--r-sm);
    text-decoration: none;
    min-height: 52px;
    transition: all 0.15s;
  }

  .session-row:active { transform: scale(0.98); background: var(--surface-3); }

  .sr-left { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
  .sr-name { font-size: 14px; font-weight: 600; color: var(--text-1); }
  .sr-meta { font-size: 12px; }

  .sr-right { display: flex; align-items: center; gap: var(--sp-3); flex-shrink: 0; }
  .sr-best { font-size: 13px; color: var(--accent); }

  .sr-badge {
    font-size: 10px; font-weight: 700; text-transform: uppercase;
    letter-spacing: 0.06em; padding: 2px 8px; border-radius: 4px;
    background: var(--surface-3); color: var(--text-3);
  }

  .sr-badge.live {
    background: rgba(45, 212, 168, 0.12);
    color: var(--accent);
  }

  .empty-card {
    text-align: center;
    padding: var(--sp-8) var(--sp-4);
    background: var(--surface-2);
    border: 1px solid var(--surface-3);
    border-radius: var(--r-md);
  }

  /* ── Feature Groups ── */
  .feature-group { margin-bottom: var(--sp-5); }

  .fg-title {
    font-size: 13px;
    font-weight: 700;
    color: var(--text-2);
    margin-bottom: var(--sp-3);
    padding-left: var(--sp-1);
  }

  .fg-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: var(--sp-2);
  }

  .feature-card {
    display: flex;
    align-items: flex-start;
    gap: var(--sp-2);
    padding: var(--sp-3);
    background: var(--surface-2);
    border: 1px solid var(--surface-3);
    border-radius: var(--r-sm);
    text-decoration: none;
    transition: all 0.15s;
    min-height: 48px;
  }

  .feature-card:active { background: var(--surface-3); }

  .fc-text { display: flex; flex-direction: column; gap: 1px; min-width: 0; }
  .fc-label { font-size: 12px; font-weight: 600; color: var(--text-1); }
  .fc-desc { font-size: 10px; color: var(--text-3); line-height: 1.3; }



  @media (min-width: 768px) {
    .stat-grid { grid-template-columns: repeat(4, 1fr); }
    .fg-grid { grid-template-columns: repeat(3, 1fr); }
    .api-desc { max-width: none; }
  }
</style>
