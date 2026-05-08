/**
 * Mirrors Android [JsonObject.compactSummary] / [formatNotification] for bridge SSE payloads.
 */

export function compactSummary(obj: Record<string, unknown>, maxKeys = 24): string {
  const entries = Object.entries(obj)
  const lines = entries.slice(0, maxKeys).map(([k, v]) => {
    let val: string
    if (v !== null && typeof v === 'object' && !Array.isArray(v)) {
      val = `{${Object.keys(v as object).length} keys}`
    } else if (Array.isArray(v)) {
      val = `[${v.length} items]`
    } else if (typeof v === 'string' || typeof v === 'number' || typeof v === 'boolean') {
      val = String(v)
    } else {
      val = String(v)
    }
    return `${k}: ${val}`
  })
  const extra = entries.length > maxKeys ? `\n… (${entries.length} keys total)` : ''
  return lines.join('\n') + extra
}

/** Same label format as Android [NotificationsViewModel.formatNotification]. */
export function formatBridgeNotificationLabel(obj: Record<string, unknown>): string {
  const kind = typeof obj.kind === 'string' ? obj.kind : 'event'
  const ts = typeof obj.ts === 'number' ? obj.ts : typeof obj.ts === 'string' ? parseFloat(obj.ts) : null
  const time =
    ts != null && !Number.isNaN(ts)
      ? new Date(ts * 1000).toISOString().replace('T', ' ').replace(/\.\d{3}Z$/, ' UTC')
      : '?'
  const driver = typeof obj.driver === 'string' ? obj.driver : ''
  const driverSuffix = driver && driver !== '*' ? ` · ${driver}` : ''
  return `${time} · ${kind}${driverSuffix}`
}
