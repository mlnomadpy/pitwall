<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import { useSaveStore } from '@/entities/save/model/saveStore'
import PageShell from '@/shared/ui/PageShell.vue'
import { bridge } from '@/shared/api/bridge'
import { resolveSessionId } from '@/entities/session/model/sessionContext'

const route = useRoute()
const router = useRouter()
const audio = useAudioStore()
const save = useSaveStore()

const loading = ref(false)
const error = ref<string | null>(null)
const payload = ref<unknown>(null)

const heading = computed(() => String(route.meta.analysisTitle ?? 'Bridge').toUpperCase())
const subtitle = computed(() => (route.meta.analysisSubtitle as string | undefined) ?? undefined)

const sid = computed(() => resolveSessionId(save.activeSlot))

async function load() {
  loading.value = true
  error.value = null
  payload.value = null
  try {
    const getPath = route.meta.bridgeGet as string | undefined
    const suffix = route.meta.bridgeSessionSuffix as string | undefined
    if (getPath) {
      payload.value = await bridge.get(getPath.startsWith('/') ? getPath : `/${getPath}`)
    } else if (suffix) {
      const id = sid.value
      if (!id) throw new Error('Pick active session (Bridge sessions) or merge sessions into save.')
      const path = `/session/${id}/${suffix.replace(/^\//, '')}`
      payload.value = await bridge.get(path)
    } else {
      throw new Error('Route meta missing bridgeGet / bridgeSessionSuffix')
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(
  () => route.fullPath,
  () => load(),
)

useKeyboard((e: KeyboardEvent) => {
  if (e.key === 'r' || e.key === 'R') {
    audio.playSfx('cursor_select')
    load()
    return
  }
  if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.push('/garage/analysis')
  }
})
</script>

<template>
  <PageShell :title="heading" :hints="['R · RELOAD', 'B · ANALYSIS HUB']" bg="cool">
    <div class="flex flex-col gap-[2vmin] text-silver font-ui p-[2vmin] max-w-[900px] mx-auto">
      <p v-if="subtitle" class="text-small tracking-widest text-slate">{{ subtitle }}</p>
      <p class="text-small">
        Session · <span class="text-white font-mono">{{ sid ?? 'none' }}</span>
      </p>
      <div v-if="loading" class="text-ui-info animate-pulse">Loading…</div>
      <div v-else-if="error" class="text-ui-bad whitespace-pre-wrap">{{ error }}</div>
      <pre
        v-else
        class="bg-ink/90 border border-slate/40 rounded p-[2vmin] text-body overflow-auto max-h-[65vh] text-silver"
      >{{ JSON.stringify(payload, null, 2) }}</pre>
    </div>
  </PageShell>
</template>
