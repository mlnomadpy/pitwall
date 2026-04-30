<script lang="ts">
  import '../app.css';
  import { onMount } from 'svelte';
  import { getHealth } from '$lib/api';

  let { children } = $props();
  let health = $state<{ status: string; track: string | null; engine: string; version: string } | null>(null);
  let clock = $state('');
  let currentPath = $state('/');

  const tabs = [
    { href: '/', label: 'Garage', icon: 'garage' },
    { href: '/brief', label: 'Brief', icon: 'brief' },
    { href: '/hud', label: 'HUD', icon: 'hud' },
    { href: '/debrief', label: 'Debrief', icon: 'debrief' },
  ];

  onMount(() => {
    currentPath = window.location.pathname;
    getHealth().then(h => health = h).catch(() => {});
    const tick = setInterval(() => {
      clock = new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false });
    }, 1000);
    clock = new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false });
    return () => clearInterval(tick);
  });

  function handleNav(href: string) {
    currentPath = href;
  }
</script>

<div class="shell">
  <!-- Top Bar -->
  <header class="topbar safe-top">
    <div class="topbar-left">
      <div class="logo">P</div>
      {#if health}
        <div class="dot dot-ok"></div>
        <span class="topbar-track truncate">{health.track || 'No track'}</span>
      {:else}
        <div class="dot dot-err"></div>
        <span class="topbar-track text-bad">Offline</span>
      {/if}
    </div>
    <span class="topbar-clock font-mono">{clock}</span>
  </header>

  <!-- Main Content -->
  <main class="content">
    {@render children()}
  </main>

  <!-- Bottom Nav — 4 tabs: flow matches race day sequence -->
  <nav class="bottomnav safe-bottom">
    {#each tabs as tab}
      <a
        href={tab.href}
        class="tab"
        class:active={currentPath === tab.href || (tab.href !== '/' && currentPath.startsWith(tab.href))}
        onclick={() => handleNav(tab.href)}
      >
        <div class="tab-icon">
          {#if tab.icon === 'garage'}
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M3 9l9-7 9 7v11a2 2 0 01-2 2H5a2 2 0 01-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>
          {:else if tab.icon === 'brief'}
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
          {:else if tab.icon === 'hud'}
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg>
          {:else if tab.icon === 'debrief'}
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"/></svg>
          {/if}
        </div>
        <span class="tab-label">{tab.label}</span>
        {#if tab.icon === 'hud' && health}
          <div class="tab-dot"></div>
        {/if}
      </a>
    {/each}
  </nav>
</div>

<style>
  .shell {
    display: flex;
    flex-direction: column;
    height: 100dvh;
    overflow: hidden;
  }

  /* ── Top Bar ── */
  .topbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: var(--sp-3) var(--sp-4);
    background: var(--surface-1);
    border-bottom: 1px solid var(--surface-3);
    flex-shrink: 0;
    min-height: 44px;
  }

  .topbar-left {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
    min-width: 0;
  }

  .logo {
    width: 28px;
    height: 28px;
    border-radius: 6px;
    background: var(--accent);
    color: var(--surface-0);
    font-family: 'Space Mono', monospace;
    font-weight: 700;
    font-size: 16px;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }

  .topbar-track {
    font-size: 13px;
    color: var(--text-2);
    max-width: 200px;
  }

  .topbar-clock {
    font-size: 13px;
    color: var(--text-3);
    flex-shrink: 0;
  }

  /* ── Main Content ── */
  .content {
    flex: 1;
    overflow-y: auto;
    overflow-x: hidden;
    padding: var(--sp-4);
    padding-bottom: calc(var(--sp-4) + 72px); /* space for fixed nav */
    -webkit-overflow-scrolling: touch;
  }

  /* ── Bottom Nav (fixed, full-width, always visible) ── */
  .bottomnav {
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    z-index: 100;
    display: flex;
    justify-content: space-around;
    align-items: stretch;
    background: var(--surface-1);
    border-top: 1px solid var(--surface-3);
    padding: var(--sp-1) 0;
    padding-bottom: max(var(--sp-2), env(safe-area-inset-bottom));
    width: 100%;
    box-sizing: border-box;
  }

  .tab {
    flex: 1 1 0%;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 2px;
    padding: var(--sp-2) 0;
    color: var(--text-3);
    text-decoration: none;
    transition: color 0.15s;
    position: relative;
    min-height: 52px;
    min-width: 0;
    -webkit-tap-highlight-color: transparent;
  }

  .tab:active { color: var(--text-2); }
  .tab.active { color: var(--accent); }

  .tab.active .tab-icon {
    background: var(--accent-glow);
    border-radius: var(--r-lg);
  }

  .tab-icon {
    width: 32px;
    height: 28px;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 2px 8px;
    transition: background 0.2s;
  }

  .tab-icon svg { width: 20px; height: 20px; }

  .tab-label {
    font-size: 10px;
    font-weight: 500;
    white-space: nowrap;
  }

  .tab-dot {
    position: absolute;
    top: 6px;
    right: calc(50% - 18px);
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: var(--accent);
    animation: pulse-dot 2s ease-in-out infinite;
  }

  /* ── Tablet+ (constrain content, not the nav backdrop) ── */
  @media (min-width: 768px) {
    .content {
      padding: var(--sp-6) var(--sp-8);
      padding-bottom: calc(var(--sp-6) + 72px);
      max-width: 960px;
      margin: 0 auto;
      width: 100%;
    }

    /* Nav stays full-width backdrop, but tabs constrained */
    .bottomnav {
      /* keep full-width background, just center tab distribution */
      max-width: none;
      margin: 0;
    }

    /* On very wide screens, tabs don't need to stretch forever */
    .tab {
      max-width: 160px;
    }
  }
</style>
