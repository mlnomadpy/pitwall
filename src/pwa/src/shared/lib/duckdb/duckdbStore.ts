import { defineStore } from 'pinia'
import { markRaw } from 'vue'
import * as duckdb from '@duckdb/duckdb-wasm'
import { bridge } from '@/shared/api/bridge'

export const useDuckDBStore = defineStore('duckdb', {
  state: () => ({
    db: null as duckdb.AsyncDuckDB | null,
    cachedSessionIds: markRaw(new Set<string>()),
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
      const buf = await bridge.getBuffer(`/session/${sid}/export.parquet?table=telemetry`)
      
      await this.db!.registerFileBuffer(`${sid}.parquet`, buf)
      
      // Persist to OPFS
      const root = await navigator.storage.getDirectory()
      const dir = await root.getDirectoryHandle('sessions', { create: true })
      const fh = await dir.getFileHandle(`${sid}.parquet`, { create: true })
      const ws = await fh.createWritable()
      await ws.write(buf.buffer as ArrayBuffer)
      await ws.close()
      
      const conn = await this.db!.connect()
      await conn.query(`CREATE OR REPLACE VIEW telemetry AS SELECT * FROM '${sid}.parquet'`)
      await conn.close()
      
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
