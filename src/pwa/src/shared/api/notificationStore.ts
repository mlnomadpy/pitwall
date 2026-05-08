import { defineStore } from 'pinia'
import { bridgeApiRoot } from '@/shared/api/bridge'
import { abortableDelay } from '@/shared/lib/abortableDelay'
import { compactSummary, formatBridgeNotificationLabel } from '@/shared/lib/jsonCompactSummary'

export type NotificationKind =
  | 'debrief-ready'
  | 'medal-earned'
  | 'level-up'
  | 'affinity-tier'
  | 'track-unlock'
  | 'hardware-warning'
  | 'evolution-ready'
  | 'session-saved'

export interface AppNotification {
  id: string
  kind: NotificationKind
  title: string
  subText: string
  timestamp: string
  isRead: boolean
  route?: string
}

/** Live SSE rows — mirrors Android [NotificationRow]. */
export interface BridgeNotificationRow {
  id: string
  label: string
  summary: string
  rawJson: string
}

const MAX_BRIDGE_ROWS = 300
const NOTIFICATIONS_RECONNECT_MS = 3000

let notificationsStreamAbort: AbortController | null = null

export const useNotificationsStore = defineStore('notifications', {
  state: () => ({
    items: [] as AppNotification[],
    bridgeRows: [] as BridgeNotificationRow[],
    bridgeStreamError: null as string | null,
  }),
  getters: {
    unreadCount: (state) => state.items.filter((i) => !i.isRead).length,
  },
  actions: {
    streamUrlForDriver(driverName: string): string {
      const q = driverName.trim() || '*'
      return `${bridgeApiRoot()}/notifications?driver=${encodeURIComponent(q)}`
    },

    /** GET SSE — same contract as Android [NotificationsViewModel.startStream]. Auto-reconnects until stopped. */
    startBridgeStream(driverName: string) {
      this.stopBridgeStream()
      this.bridgeStreamError = null
      const ac = new AbortController()
      notificationsStreamAbort = ac
      const url = this.streamUrlForDriver(driverName)

      ;(async () => {
        while (!ac.signal.aborted) {
          try {
            const res = await fetch(url, { signal: ac.signal })
            if (!res.ok) {
              this.bridgeStreamError = `HTTP ${res.status}`
              await abortableDelay(NOTIFICATIONS_RECONNECT_MS, ac.signal)
              continue
            }

            const reader = res.body?.getReader()
            if (!reader) {
              this.bridgeStreamError = 'empty body'
              await abortableDelay(NOTIFICATIONS_RECONNECT_MS, ac.signal)
              continue
            }

            this.bridgeStreamError = null
            const decoder = new TextDecoder()
            let buffer = ''
            while (!ac.signal.aborted) {
              const { done, value } = await reader.read()
              if (done) break
              buffer += decoder.decode(value, { stream: true })
              const lines = buffer.split('\n')
              buffer = lines.pop() ?? ''
              for (const rawLine of lines) {
                this.ingestNotificationSseLine(rawLine)
              }
            }
            if (buffer.trim()) {
              for (const rawLine of buffer.split('\n')) {
                if (rawLine.trim()) this.ingestNotificationSseLine(rawLine)
              }
            }
          } catch (e) {
            if (e instanceof DOMException && e.name === 'AbortError') return
            if (!ac.signal.aborted) {
              this.bridgeStreamError = e instanceof Error ? e.message : String(e)
            }
          }

          if (ac.signal.aborted) return
          try {
            await abortableDelay(NOTIFICATIONS_RECONNECT_MS, ac.signal)
          } catch {
            return
          }
        }
      })()
    },

    ingestNotificationSseLine(rawLine: string) {
      const trimmed = rawLine.trim()
      if (!trimmed.startsWith('data:')) return
      const payload = trimmed.slice(5).trimStart()
      if (!payload) return
      try {
        const obj = JSON.parse(payload) as Record<string, unknown>
        const row: BridgeNotificationRow = {
          id: `${Date.now()}_${Math.random().toString(36).slice(2, 10)}`,
          label: formatBridgeNotificationLabel(obj),
          summary: compactSummary(obj, 24),
          rawJson: JSON.stringify(obj, null, 2),
        }
        this.bridgeRows = [...this.bridgeRows, row].slice(-MAX_BRIDGE_ROWS)
      } catch {
        const row: BridgeNotificationRow = {
          id: `${Date.now()}_${Math.random().toString(36).slice(2, 10)}`,
          label: payload.slice(0, 120),
          summary: '',
          rawJson: payload,
        }
        this.bridgeRows = [...this.bridgeRows, row].slice(-MAX_BRIDGE_ROWS)
      }
    },

    stopBridgeStream() {
      notificationsStreamAbort?.abort()
      notificationsStreamAbort = null
    },

    clearBridgeLog() {
      this.bridgeRows = []
    },

    markRead(id: string) {
      const item = this.items.find((i) => i.id === id)
      if (item) item.isRead = true
    },
    markAllRead() {
      this.items.forEach((i) => {
        i.isRead = true
      })
    },
    add(notification: Omit<AppNotification, 'id' | 'isRead'>) {
      this.items.unshift({
        ...notification,
        id: Math.random().toString(36).substr(2, 9),
        isRead: false,
      })
    },
  },
})
