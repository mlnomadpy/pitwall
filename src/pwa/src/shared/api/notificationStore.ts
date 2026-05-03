import { defineStore } from 'pinia'

export type NotificationKind = 'debrief-ready' | 'medal-earned' | 'level-up' | 'affinity-tier' | 'track-unlock' | 'hardware-warning' | 'evolution-ready' | 'session-saved'

export interface AppNotification {
  id: string
  kind: NotificationKind
  title: string
  subText: string
  timestamp: string
  isRead: boolean
  route?: string
}

export const useNotificationsStore = defineStore('notifications', {
  state: () => ({
    items: [
      {
        id: '1',
        kind: 'debrief-ready',
        title: '⭕ DEBRIEF READY',
        subText: 'session-20260423-1004 · Tap to read',
        timestamp: '2 min ago',
        isRead: false,
        route: '/stage-clear'
      },
      {
        id: '2',
        kind: 'medal-earned',
        title: '⭐ NEW MEDAL · TRAIL BRAKE APPRENTICE',
        subText: 'Tap to view in Quest Log',
        timestamp: '12 min ago',
        isRead: false,
        route: '/garage/quests'
      },
      {
        id: '3',
        kind: 'hardware-warning',
        title: '⚠️ HARDWARE WARNING',
        subText: 'TPMS data rate dropped — replace battery?',
        timestamp: '1 hr ago',
        isRead: false,
        route: '/garage/pit-stall/hardware'
      },
      {
        id: '4',
        kind: 'evolution-ready',
        title: '📈 EVOLUTION SNAPSHOT READY',
        subText: 'Last 5 sessions analysed · Tap to view',
        timestamp: '3 hr ago',
        isRead: true,
        route: '/analysis/evolution'
      },
      {
        id: '5',
        kind: 'session-saved',
        title: '✓ SESSION SAVED',
        subText: 'session-20260423-0904 · 18 laps · in DuckDB',
        timestamp: '5 hr ago',
        isRead: true,
        route: '/garage'
      }
    ] as AppNotification[]
  }),
  getters: {
    unreadCount: (state) => state.items.filter(i => !i.isRead).length
  },
  actions: {
    markRead(id: string) {
      const item = this.items.find(i => i.id === id)
      if (item) item.isRead = true
    },
    markAllRead() {
      this.items.forEach(i => i.isRead = true)
    },
    add(notification: Omit<AppNotification, 'id' | 'isRead'>) {
      this.items.unshift({
        ...notification,
        id: Math.random().toString(36).substr(2, 9),
        isRead: false
      })
    }
  }
})
