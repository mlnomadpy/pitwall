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
  }
}

