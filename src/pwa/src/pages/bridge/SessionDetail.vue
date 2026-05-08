<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import PageShell from '@/shared/ui/PageShell.vue'
import { bridge } from '@/shared/api/bridge'
import type { SessionDetailResponse } from '@/shared/types/bridge'
import { setActiveSessionId } from '@/entities/session/model/sessionContext'

const route = useRoute()
const router = useRouter()
const audio = useAudioStore()

const sid = computed(() => decodeURIComponent(route.params.sid as string))

const loading = ref(true)
const error = ref<string | null>(null)
const detail = ref<SessionDetailResponse | null>(null)

async function load() {
  loading.value = true
  error.value = null
  detail.value = null
  try {
    detail.value = await bridge.getSession(sid.value)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(sid, () => load())

function activateSession() {
  setActiveSessionId(sid.value)
  audio.playSfx('cursor_select')
}

useKeyboard((e: KeyboardEvent) => {
  if (e.key === 'a' || e.key === 'A') {
    activateSession()
    return
  }
  if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.push('/bridge/sessions')
  }
})
</script>

<template>
  <PageShell title="SESSION DETAIL" :hints="['A · SET ACTIVE', 'B · SESSION LIST']" bg="cool">
    <div class="flex flex-col gap-[2vmin] p-[2vmin] max-w-[960px] mx-auto w-full text-silver font-ui">
      <p class="font-mono text-white">{{ sid }}</p>
      <div v-if="loading" class="text-ui-info animate-pulse">Loading…</div>
      <div v-else-if="error" class="text-ui-bad whitespace-pre-wrap">{{ error }}</div>
      <template v-else-if="detail">
        <div class="flex flex-wrap gap-3 items-center">
          <button
            type="button"
            class="px-4 py-2 bg-ui-good text-ink rounded font-bold tracking-wide"
            @click="activateSession"
          >
            Use as active session
          </button>
          <span class="text-small text-slate">Stores selection for POST /score, /coach/ask, analytics GETs.</span>
        </div>
        <pre class="bg-ink/90 border border-slate/40 rounded p-[2vmin] text-body overflow-auto max-h-[62vh]">{{ JSON.stringify(detail, null, 2) }}</pre>
      </template>
    </div>
  </PageShell>
</template>
