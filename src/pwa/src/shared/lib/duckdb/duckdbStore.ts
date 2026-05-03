import { defineStore } from 'pinia'
import * as duckdb from '@duckdb/duckdb-wasm'

export const useDuckDBStore = defineStore('duckdb', {
  state: () => ({
    db: null as duckdb.AsyncDuckDB | null,
    cachedSessionIds: new Set<string>(),
  }),
  actions: {
    async init() {
      if (this.db) return
      
      const JSDELIVR_BUNDLES = duckdb.getJsDelivrBundles()
      const bundle = await duckdb.selectBundle(JSDELIVR_BUNDLES)
      
      const worker = new Worker(bundle.mainWorker!)
      const logger = new duckdb.ConsoleLogger()
      this.db = new duckdb.AsyncDuckDB(logger, worker)
      
      await this.db.instantiate(bundle.mainModule!, bundle.pthreadWorker)
    },
    async ensureSession(sid: string) {
      if (this.cachedSessionIds.has(sid)) return
      // Fetch from bridge -> register with DuckDB-Wasm -> save to OPFS
      const r = await fetch(`http://127.0.0.1:8765/session/${sid}/export.parquet?table=telemetry`)
      const buf = new Uint8Array(await r.arrayBuffer())
      
      await this.db!.registerFileBuffer(`${sid}.parquet`, buf)
      
      // Persist to OPFS
      const root = await navigator.storage.getDirectory()
      const dir = await root.getDirectoryHandle('sessions', { create: true })
      const fh = await dir.getFileHandle(`${sid}.parquet`, { create: true })
      const ws = await fh.createWritable()
      await ws.write(buf)
      await ws.close()
      
      this.cachedSessionIds.add(sid)
    },
    async query(sql: string) {
      if (!this.db) throw new Error('DuckDB not initialized')
      const conn = await this.db.connect()
      const result = await conn.query(sql)
      await conn.close()
      return result
    }
  }
})
