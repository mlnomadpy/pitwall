/**
 * Bridge API Client
 * Wraps native fetch calls to the Python HTTP backend.
 */

const API_BASE = '/api'

export const bridge = {
  async get(endpoint: string) {
    const res = await fetch(`${API_BASE}${endpoint}`)
    if (!res.ok) throw new Error(`Bridge GET ${endpoint} failed: ${res.statusText}`)
    return res.json()
  },
  
  async post(endpoint: string, body: any) {
    const res = await fetch(`${API_BASE}${endpoint}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    })
    if (!res.ok) throw new Error(`Bridge POST ${endpoint} failed: ${res.statusText}`)
    return res.json()
  }
}
