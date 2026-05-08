<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import PageShell from '@/shared/ui/PageShell.vue'
import { bridge } from '@/shared/api/bridge'
import type { SessionSummary } from '@/shared/types/bridge'
import { setActiveSessionId } from '@/entities/session/model/sessionContext'

const router = useRouter()
const audio = useAudioStore()

const loading = ref(true)
const error = ref<string | null>(null)
const sessions = ref<SessionSummary[]>([])

onMounted(async () => {
  loading.value = true
  error.value = null
  try {
    const env = await bridge.getSessions(80, false)
    sessions.value = env.sessions ?? []
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
})

function pick(sid: string) {
  setActiveSessionId(sid)
  audio.playSfx('cursor_select')
  router.push(`/session/${encodeURIComponent(sid)}`)
}

useKeyboard((e: KeyboardEvent) => {
  if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.push('/garage')
  }
})
</script>

<template>
  <PageShell title="BRIDGE SESSIONS" :hints="['TAP ROW · OPEN', 'B · GARAGE']" bg="neutral">
    <div class="flex flex-col gap-[2vmin] p-[2vmin] max-w-[960px] mx-auto w-full">
      <p class="text-small text-slate tracking-widest">
        GET /sessions — newest first. Selecting sets the active session for analytics / coach tools (stored in
        sessionStorage).
      </p>
      <div v-if="loading" class="text-ui-info animate-pulse">Loading…</div>
      <div v-else-if="error" class="text-ui-bad">{{ error }}</div>
      <div v-else class="flex flex-col gap-2">
        <button
          v-for="s in sessions"
          :key="s.session_id"
          type="button"
          class="text-left bg-ink/80 border border-slate/40 hover:border-ui-info/60 rounded px-4 py-3 transition-colors"
          @click="pick(s.session_id)"
        >
          <div class="flex justify-between gap-4 flex-wrap">
            <span class="text-white font-mono text-body">{{ s.session_id }}</span>
            <span class="text-silver text-small">{{ s.track }} · {{ s.driver }}</span>
          </div>
          <div class="text-small text-slate mt-1">
            laps {{ s.lap_count }}
            <span v-if="s.best_lap_s"> · best {{ s.best_lap_s?.toFixed(3) }}s</span>
          </div>
        </button>
        <p v-if="sessions.length === 0" class="text-ui-warn">No sessions returned — start the bridge or ingest laps.</p>
      </div>
    </div>
  </PageShell>
</template>
