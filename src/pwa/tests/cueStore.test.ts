import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useCueStore } from '../src/features/coach-interaction/model/cueStore'

describe('cueStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('opens SSE connection', () => {
    const store = useCueStore()
    store.open('test-session')

    expect(store._es).toBeDefined()
  })

  it('closes SSE connection', () => {
    const store = useCueStore()
    store.open('test-session')
    
    const mockClose = store._es?.close

    store.close()

    expect(mockClose).toHaveBeenCalled()
    expect(store._es).toBeNull()
  })

  it('queues cue on message', () => {
    const store = useCueStore()
    store.open('test-session')

    const mockEvent = {
      data: JSON.stringify({ id: '1', text: 'Brake hard!', emotion: 'intense', timestamp: 123 })
    }

    store._es!.onmessage!(mockEvent as any)

    expect(store.queue.length).toBe(1)
    expect(store.queue[0]).toEqual({ id: '1', text: 'Brake hard!', emotion: 'intense', timestamp: 123 })
  })

  it('clears activeCue on error and schedules retry', () => {
    vi.useFakeTimers()
    const store = useCueStore()
    store.open('test-session')
    
    store.activeCue = { id: '1', text: 'Brake hard!', emotion: 'intense', timestamp: 123 }
    store._es!.onerror!(new Event('error'))

    expect(store.activeCue).toBeNull()
    expect(store._retryCount).toBe(1) // first retry scheduled
    vi.useRealTimers()
  })
})
