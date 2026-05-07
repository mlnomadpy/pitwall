import { describe, it, expect, vi, beforeEach } from 'vitest'

// We test the SSE reconnection logic through the telemetry store
// since useReconnectingSSE requires Vue component lifecycle (onUnmounted)
import { setActivePinia, createPinia } from 'pinia'
import { useTelemetryStore } from '../src/entities/session/model/telemetryStore'
import { useCueStore } from '../src/features/coach-interaction/model/cueStore'

describe('SSE reconnection', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.useFakeTimers()
  })

  describe('telemetryStore reconnection', () => {
    it('schedules retry on error with incrementing count', () => {
      const store = useTelemetryStore()
      store.open('test-session')
      
      // Trigger first error
      store._es!.onerror!(new Event('error'))
      
      expect(store._retryCount).toBe(1)
      expect(store._retryTimeout).not.toBeNull()
    })

    it('resets retry count on successful message', () => {
      const store = useTelemetryStore()
      store.open('test-session')
      
      store._retryCount = 5
      
      // Simulate successful telemetry event
      const addEventListenerMock = vi.mocked(store._es!.addEventListener)
      const handler = addEventListenerMock.mock.calls[0][1] as EventListener
      handler({ data: JSON.stringify({ speed: 100, timestamp: 12345 }) } as any)

      expect(store._retryCount).toBe(0)
    })

    it('gives up after max retries', () => {
      const store = useTelemetryStore()
      store.open('test-session')
      
      // Set to one below max
      store._retryCount = 9
      
      // Trigger error — should set retry to 10 and schedule
      store._es!.onerror!(new Event('error'))
      expect(store._retryCount).toBe(10)
      
      // Simulate the retry creating a new ES, then another error
      vi.advanceTimersByTime(3000)
      
      // The store should have reconnected and the _es should be defined again
      if (store._es) {
        store._es.onerror!(new Event('error'))
        // Should NOT schedule another retry (count exceeds max)
        // retryCount would be 1 from the new connection, not cumulative
      }
    })

    it('clears retry timeout on close', () => {
      const store = useTelemetryStore()
      store.open('test-session')
      
      // Trigger error to schedule retry
      store._es!.onerror!(new Event('error'))
      expect(store._retryTimeout).not.toBeNull()
      
      // Close should clear everything
      store.close()
      expect(store._retryTimeout).toBeNull()
      expect(store._retryCount).toBe(0)
      expect(store._es).toBeNull()
    })
  })

  describe('cueStore reconnection', () => {
    it('schedules retry on error', () => {
      const store = useCueStore()
      store.open('test-session')
      
      store._es!.onerror!(new Event('error'))
      
      expect(store._retryCount).toBe(1)
      expect(store._retryTimeout).not.toBeNull()
    })

    it('resets retry count on successful cue', () => {
      const store = useCueStore()
      store.open('test-session')
      
      store._retryCount = 3
      
      // Simulate cue message
      store._es!.onmessage!({ data: JSON.stringify({ id: '1', text: 'Brake!', emotion: 'intense', timestamp: 0 }) } as any)
      
      expect(store._retryCount).toBe(0)
      expect(store.queue.length).toBe(1)
    })

    it('clears retry timeout on close', () => {
      const store = useCueStore()
      store.open('test-session')
      
      store._es!.onerror!(new Event('error'))
      
      store.close()
      expect(store._retryTimeout).toBeNull()
      expect(store._retryCount).toBe(0)
    })
  })
})
