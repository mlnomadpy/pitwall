/**
 * useSessionCapabilities — drop-in primitive for capability-gated UI.
 *
 * Wraps GET /session/<sid>/capabilities (the ADR-015 Phase-3 envelope)
 * with a small reactive cache so every consumer in a page shares one
 * fetch per session. The bridge endpoint is now lazy: it computes from
 * raw telemetry on first GET if the precomputed row is missing.
 *
 * Returned reactive state:
 *   loading              — true while the GET is in flight
 *   error                — string | null on failure
 *   capabilities         — Map<signal_name, CapabilityRow> | null
 *   coachesAvailable     — string[] (rule names whose gating conditions are met)
 *   coachesDisabled      — Array<{name, reasons[]}> (rules that can't fire)
 *   has(name)            — bool, true if a row exists for that signal
 *   meanHz(name)         — number | undefined, observed mean rate
 *   useful(name)         — bool, true if mean_hz >= min_useful_hz
 *   reload()             — force refresh (e.g. on bridge reconnect)
 *
 * Idempotent: calling useSessionCapabilities('foo') in two components
 * during the same render frame triggers one fetch.
 *
 * Doesn't depend on the synchroniser endpoint — separate concern.
 */

import { computed, reactive, readonly, ref, watch, type Ref } from 'vue'
import { bridge } from '@/shared/api/bridge'

export interface CapabilityRow {
  name: string
  n_samples: number
  mean_hz: number
  useful: boolean
}

export interface CapabilitiesResponse {
  session_id: string
  duration_s: number
  signals: CapabilityRow[]
  coaches_available?: string[]
  coaches_disabled?: Array<{ name: string; reasons?: string[] }>
  error?: string
}

interface SessionCacheEntry {
  loading: boolean
  error: string | null
  capabilities: Map<string, CapabilityRow> | null
  coachesAvailable: string[]
  coachesDisabled: Array<{ name: string; reasons: string[] }>
  fetchedAt: number
  inflight?: Promise<void>
}

// Module-scoped cache keyed by sid so multiple components share one fetch.
const _cache = reactive(new Map<string, SessionCacheEntry>())

function _emptyEntry(): SessionCacheEntry {
  return {
    loading: false,
    error: null,
    capabilities: null,
    coachesAvailable: [],
    coachesDisabled: [],
    fetchedAt: 0,
  }
}

async function _fetch(sid: string, force = false): Promise<void> {
  let entry = _cache.get(sid)
  if (!entry) {
    entry = _emptyEntry()
    _cache.set(sid, entry)
  }
  if (entry.inflight) return entry.inflight
  if (!force && entry.capabilities !== null && Date.now() - entry.fetchedAt < 30_000) {
    // Recent successful fetch — no-op.
    return
  }
  entry.loading = true
  entry.error = null

  const job = (async () => {
    try {
      const data = await bridge.get<CapabilitiesResponse>(`/session/${sid}/capabilities`)
      // Bridge returns { error: 'session not found' } for sessions with no data.
      if ((data as any).error) {
        entry!.error = String((data as any).error)
        entry!.capabilities = new Map()
        entry!.coachesAvailable = []
        entry!.coachesDisabled = []
      } else {
        const m = new Map<string, CapabilityRow>()
        for (const row of data.signals ?? []) {
          m.set(row.name, row)
        }
        entry!.capabilities = m
        entry!.coachesAvailable = data.coaches_available ?? []
        entry!.coachesDisabled = (data.coaches_disabled ?? []).map(c => ({
          name: c.name,
          reasons: c.reasons ?? [],
        }))
      }
      entry!.fetchedAt = Date.now()
    } catch (e) {
      entry!.error = e instanceof Error ? e.message : String(e)
      entry!.capabilities = new Map()
    } finally {
      entry!.loading = false
      entry!.inflight = undefined
    }
  })()
  entry.inflight = job
  return job
}

/**
 * Use the capabilities for a given session.
 *
 * `sid` can be a static string or a Ref so the composable reacts to
 * route changes. Pass `_live` to gate on the live session.
 */
export function useSessionCapabilities(sid: string | Ref<string | null | undefined>) {
  const sidRef = (typeof sid === 'string'
    ? ref(sid)
    : sid) as Ref<string | null | undefined>

  const _ensureEntry = (s: string) => {
    if (!_cache.has(s)) _cache.set(s, _emptyEntry())
    return _cache.get(s)!
  }

  // Reactive read-throughs against the cache entry for the current sid.
  const entry = computed(() => {
    const s = sidRef.value
    return s ? _ensureEntry(s) : null
  })

  const loading          = computed(() => entry.value?.loading ?? false)
  const error            = computed(() => entry.value?.error ?? null)
  const capabilities     = computed(() => entry.value?.capabilities ?? null)
  const coachesAvailable = computed(() => entry.value?.coachesAvailable ?? [])
  const coachesDisabled  = computed(() => entry.value?.coachesDisabled ?? [])

  // Helpers — null-safe so consumers don't have to guard for missing data.
  const has     = (name: string) => Boolean(capabilities.value?.has(name))
  const meanHz  = (name: string) => capabilities.value?.get(name)?.mean_hz
  const useful  = (name: string) => Boolean(capabilities.value?.get(name)?.useful)

  const reload = () => {
    const s = sidRef.value
    if (s) return _fetch(s, /* force */ true)
  }

  // Auto-fetch on first use and whenever sid changes.
  watch(
    sidRef,
    s => { if (s) _fetch(s) },
    { immediate: true },
  )

  return {
    loading: readonly(loading),
    error: readonly(error),
    capabilities: readonly(capabilities),
    coachesAvailable: readonly(coachesAvailable),
    coachesDisabled: readonly(coachesDisabled),
    has,
    meanHz,
    useful,
    reload,
  }
}
