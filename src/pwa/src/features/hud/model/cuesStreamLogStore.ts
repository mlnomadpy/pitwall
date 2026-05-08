import { defineStore } from 'pinia'
import { cuesStreamUrl } from '@/shared/api/bridge'
import { abortableDelay } from '@/shared/lib/abortableDelay'

/** Mirrors Android [HudViewModel] raw cue SSE lines (takeLast 200). */
const MAX_LINES = 200
const RECONNECT_MS = 3000

let cuesStreamAbort: AbortController | null = null

async function pumpLinesFromResponse(
  res: Response,
  onLine: (line: string) => void,
  signal: AbortSignal,
): Promise<void> {
  const reader = res.body?.getReader()
  if (!reader) throw new Error('empty body')
  const decoder = new TextDecoder()
  let buffer = ''
  while (!signal.aborted) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() ?? ''
    for (const line of lines) {
      onLine(line)
    }
  }
  if (buffer.trim()) {
    for (const line of buffer.split('\n')) {
      if (line.trim()) onLine(line)
    }
  }
}

export const useCuesStreamLogStore = defineStore('cuesStreamLog', {
  state: () => ({
    rawLines: [] as string[],
    connectionError: null as string | null,
    reconnectAttempt: 0,
  }),
  actions: {
    appendLine(line: string) {
      this.rawLines = [...this.rawLines, line].slice(-MAX_LINES)
    },

    clearLog() {
      this.rawLines = []
    },

    /**
     * Long-lived GET stream with reconnect — aligns with Android cue pipe plus resilient UX.
     */
    start(sessionId: string | null) {
      this.stop()
      this.connectionError = null
      this.reconnectAttempt = 0

      const ac = new AbortController()
      cuesStreamAbort = ac
      const url = cuesStreamUrl(sessionId ?? undefined)
      const store = this

      ;(async () => {
        while (!ac.signal.aborted) {
          try {
            const res = await fetch(url, { signal: ac.signal })
            if (!res.ok) {
              store.connectionError = `HTTP ${res.status}`
              store.reconnectAttempt++
              await abortableDelay(RECONNECT_MS, ac.signal)
              continue
            }
            store.connectionError = null
            store.reconnectAttempt = 0
            await pumpLinesFromResponse(
              res,
              (line) => store.appendLine(line),
              ac.signal,
            )
          } catch (e) {
            if (e instanceof DOMException && e.name === 'AbortError') return
            store.connectionError = e instanceof Error ? e.message : String(e)
            store.reconnectAttempt++
          }

          if (ac.signal.aborted) return
          try {
            await abortableDelay(RECONNECT_MS, ac.signal)
          } catch {
            return
          }
        }
      })()
    },

    stop() {
      cuesStreamAbort?.abort()
      cuesStreamAbort = null
    },
  },
})
