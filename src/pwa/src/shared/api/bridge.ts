/**
 * Bridge API Client
 * Wraps native fetch calls to the Python HTTP backend.
 * All stores should use this instead of raw fetch().
 */

import { API_BASE } from '@/shared/config/api'
import type * as Types from '@/shared/types/bridge'

export interface ApiError {
  message: string
}

function apiRoot(): string {
  return API_BASE.replace(/\/+$/, '')
}

/** Public base URL for SSE / manual fetch (matches Android [BuildConfig] + path). */
export function bridgeApiRoot(): string {
  return apiRoot()
}

/** GET /cues/stream — optional session filter (Android [NetworkModule.cuesStreamUrl]). */
export function cuesStreamUrl(sessionId: string | null | undefined): string {
  const base = `${bridgeApiRoot()}/cues/stream`
  const sid = sessionId?.trim()
  if (!sid) return base
  return `${base}?session_id=${encodeURIComponent(sid)}`
}

export type CoachAskDoneMeta = { qa_key?: string; turn?: number }

/** Matches Android [CoachAskStreamReader.parseChunk] — SSE lines `data: {"delta"|"done"|"error":…}`. */
function parseCoachAskSseDataLine(
  rawLine: string,
  handlers: {
    onDelta: (delta: string) => void
    onDone: (
      answer: string,
      emotion: string | null | undefined,
      meta?: CoachAskDoneMeta,
    ) => void
    onError: (message: string) => void
  },
): void {
  const line = rawLine.trim()
  if (!line.startsWith('data:')) return
  const data = line.slice(5).trimStart()
  if (!data) return
  let obj: Record<string, unknown>
  try {
    obj = JSON.parse(data) as Record<string, unknown>
  } catch {
    handlers.onError('parse: invalid JSON')
    return
  }
  const err = obj.error
  if (err != null && String(err).length > 0) {
    handlers.onError(String(err))
    return
  }
  const delta = obj.delta
  if (typeof delta === 'string' && delta.length > 0) {
    handlers.onDelta(delta)
  }
  const done =
    obj.done === true ||
    obj.done === 'true' ||
    (typeof obj.done === 'string' && obj.done.toLowerCase() === 'true')
  if (done) {
    const answer = typeof obj.answer === 'string' ? obj.answer : ''
    const emotion = obj.emotion != null && obj.emotion !== '' ? String(obj.emotion) : undefined
    const qa_key = typeof obj.qa_key === 'string' && obj.qa_key.length > 0 ? obj.qa_key : undefined
    let turn: number | undefined
    if (typeof obj.turn === 'number' && !Number.isNaN(obj.turn)) turn = obj.turn
    else if (typeof obj.turn === 'string') {
      const t = parseInt(obj.turn, 10)
      if (!Number.isNaN(t)) turn = t
    }
    const meta =
      qa_key != null || turn != null ? { qa_key, turn } satisfies CoachAskDoneMeta : undefined
    handlers.onDone(answer, emotion ?? null, meta)
  }
}

export const bridge = {
  async getHealth(): Promise<Types.HealthResponse> {
    return this.get<Types.HealthResponse>('/health')
  },

  async analyze(data: Types.AnalyzeRequest): Promise<Types.AnalyzeResponse> {
    return this.post<Types.AnalyzeResponse>('/analyze', data)
  },

  async getSessions(limit = 50, activeOnly = false): Promise<{ sessions: Types.SessionSummary[]; count: number }> {
    return this.get<{ sessions: Types.SessionSummary[]; count: number }>(`/sessions?limit=${limit}&active_only=${activeOnly}`)
  },

  async getSession(sid: string): Promise<Types.SessionDetailResponse> {
    return this.get<Types.SessionDetailResponse>(`/session/${sid}`)
  },

  async startSession(data: Types.StartSessionRequest): Promise<Types.StartSessionResponse> {
    return this.post<Types.StartSessionResponse>('/session/start', data)
  },

  async endSession(sid: string): Promise<Types.EndSessionResponse> {
    return this.post<Types.EndSessionResponse>(`/session/${sid}/end`, {})
  },

  async get<T = unknown>(endpoint: string): Promise<T> {
    const res = await fetch(`${API_BASE}${endpoint}`)
    if (!res.ok) throw new Error(`Bridge GET ${endpoint} failed: ${res.statusText}`)
    return res.json() as Promise<T>
  },
  
  async getBuffer(endpoint: string): Promise<Uint8Array> {
    const res = await fetch(`${API_BASE}${endpoint}`)
    if (!res.ok) throw new Error(`Bridge GET ${endpoint} failed: ${res.statusText}`)
    return new Uint8Array(await res.arrayBuffer())
  },
  
  async getRaw(endpoint: string): Promise<Response> {
    const res = await fetch(`${API_BASE}${endpoint}`)
    if (!res.ok) throw new Error(`Bridge GET ${endpoint} failed: ${res.statusText}`)
    return res
  },
  
  async post<T = unknown>(endpoint: string, body: unknown): Promise<T> {
    const res = await fetch(`${API_BASE}${endpoint}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    })
    if (!res.ok) throw new Error(`Bridge POST ${endpoint} failed: ${res.statusText}`)
    return res.json() as Promise<T>
  },

  async coachAsk(body: { question: string; driver_id?: string; session_id?: string; intent?: string | null }) {
    return this.post<{
      answer?: string | null
      emotion?: string | null
      error?: string | null
      qa_key?: string | null
      turn?: number | null
    }>('/coach/ask', body)
  },

  /**
   * POST /coach/ask/stream — incremental SSE `data:` JSON (delta / done / error).
   * Aligns with Android [CoachAskStreamReader].
   */
  async coachAskStream(
    body: { question: string; driver_id?: string; session_id?: string; intent?: string | null },
    handlers: {
      onDelta: (delta: string) => void
      onDone: (
        answer: string,
        emotion: string | null | undefined,
        meta?: CoachAskDoneMeta,
      ) => void
      onError: (message: string) => void
    },
    options?: { signal?: AbortSignal },
  ): Promise<void> {
    const url = `${apiRoot()}/coach/ask/stream`
    let res: Response
    try {
      res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
        signal: options?.signal,
      })
    } catch (e) {
      if (e instanceof DOMException && e.name === 'AbortError') return
      handlers.onError(e instanceof Error ? e.message : String(e))
      return
    }
    if (!res.ok) {
      const snippet = (await res.text().catch(() => '')).trim().slice(0, 400)
      handlers.onError(
        snippet ? `HTTP ${res.status}: ${snippet}` : `HTTP ${res.status}`,
      )
      return
    }
    const reader = res.body?.getReader()
    if (!reader) {
      handlers.onError('empty body')
      return
    }
    const decoder = new TextDecoder()
    let buffer = ''
    try {
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() ?? ''
        for (const line of lines) {
          parseCoachAskSseDataLine(line, handlers)
        }
      }
      if (buffer.trim()) {
        for (const line of buffer.split('\n')) {
          if (line.trim()) parseCoachAskSseDataLine(line, handlers)
        }
      }
    } catch (e) {
      if (e instanceof DOMException && e.name === 'AbortError') return
      handlers.onError(e instanceof Error ? e.message : String(e))
    }
  },

  async coachAskEnd(body: { driver_id?: string; session_id?: string }) {
    return this.post<{ flushed: number; qa_key: string }>('/coach/ask/end', body)
  },

  async scoreSession(body: { session_id: string; focus?: string; driver_level?: string }) {
    return this.post<{
      session_id: string
      score: number
      why: string
      model?: string | null
      focus?: string | null
    }>('/score', body)
  }
}

