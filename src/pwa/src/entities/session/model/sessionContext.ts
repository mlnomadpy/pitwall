import type { SaveSlot } from '@/shared/types/save'

const KEY = 'pitwall:activeSessionId'

/** Mirrors Android [SessionHolder.activeSessionId] — drives HUD, analytics, coach calls. */
export function getActiveSessionId(): string | null {
  if (typeof sessionStorage === 'undefined') return null
  const v = sessionStorage.getItem(KEY)
  return v && v.length > 0 ? v : null
}

export function setActiveSessionId(sessionId: string | null) {
  if (typeof sessionStorage === 'undefined') return
  if (sessionId === null || sessionId === '') sessionStorage.removeItem(KEY)
  else sessionStorage.setItem(KEY, sessionId)
}

/** Prefer explicit selection; else newest session on the active save (by [startedAt]). */
export function pickFallbackSessionId(slot: SaveSlot | null): string | null {
  const sessions = slot?.sessions
  if (!sessions?.length) return null
  const sorted = [...sessions].sort((a, b) => (a.startedAt < b.startedAt ? 1 : -1))
  return sorted[0]?.sessionId ?? null
}

export function resolveSessionId(slot: SaveSlot | null): string | null {
  return getActiveSessionId() ?? pickFallbackSessionId(slot)
}
