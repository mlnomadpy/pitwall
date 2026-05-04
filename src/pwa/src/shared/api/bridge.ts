/**
 * Bridge API Client
 * Wraps native fetch calls to the Python HTTP backend.
 * All stores should use this instead of raw fetch().
 */

import { API_BASE } from '@/shared/config/api'

export interface ApiError {
  message: string
}

export const bridge = {
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
  }
}
