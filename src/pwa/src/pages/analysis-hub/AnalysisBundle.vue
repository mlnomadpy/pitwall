<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import { useSaveStore } from '@/entities/save/model/saveStore'
import PageShell from '@/shared/ui/PageShell.vue'
import { bridge } from '@/shared/api/bridge'
import { resolveSessionId } from '@/entities/session/model/sessionContext'

const sections = [
  { id: 'highlights', label: 'Highlights', suffix: 'highlights' },
  { id: 'stats', label: 'Stats', suffix: 'stats' },
  { id: 'friction', label: 'Friction', suffix: 'friction_circle' },
  { id: 'hustle', label: 'Hustle', suffix: 'hustle_map' },
  { id: 'eob', label: 'EOB', suffix: 'eob' },
  { id: 'incidents', label: 'Incidents', suffix: 'incidents' },
] as const

const router = useRouter()
const audio = useAudioStore()
const save = useSaveStore()

const tabIndex = ref(0)
const loading = ref(false)
const error = ref<string | null>(null)
const text = ref('')

const sid = computed(() => resolveSessionId(save.activeSlot))

async function load() {
  const id = sid.value
  if (!id) {
    error.value = 'No session — open Bridge sessions and select a row, or set active on Session detail.'
    text.value = ''
    return
  }
  loading.value = true
  error.value = null
  text.value = ''
  try {
    const suffix = sections[tabIndex.value].suffix
    const raw = await bridge.get(`/session/${id}/${suffix}`)
    text.value = JSON.stringify(raw, null, 2)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(
  () => [tabIndex.value, sid.value],
  () => load(),
)

useKeyboard((e: KeyboardEvent) => {
  if (e.key === '[') {
    tabIndex.value = (tabIndex.value + sections.length - 1) % sections.length
    audio.playSfx('cursor_move')
  } else if (e.key === ']') {
    tabIndex.value = (tabIndex.value + 1) % sections.length
    audio.playSfx('cursor_move')
  } else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.push('/garage/analysis')
  }
})
</script>

<template>
  <PageShell title="DEBRIEF BUNDLE" :hints="['[ · ] · SECTION', 'B · HUB']" bg="cool">
    <div class="flex flex-col gap-[2vmin] p-[2vmin] max-w-[960px] mx-auto w-full">
      <p class="text-small text-slate tracking-widest">
        GET /session/&lt;sid&gt;/… bundle slices · Session ·
        <span class="text-white font-mono">{{ sid ?? 'none' }}</span>
      </p>
      <div class="flex flex-wrap gap-2">
        <button
          v-for="(t, i) in sections"
          :key="t.id"
          type="button"
          class="px-3 py-1 rounded border text-small tracking-widest transition-colors"
          :class="
            tabIndex === i
              ? 'border-ui-info text-white bg-ui-info/10'
              : 'border-slate/50 text-slate hover:border-slate'
          "
          @click="tabIndex = i"
        >
          {{ t.label }}
        </button>
      </div>
      <div v-if="loading" class="text-ui-info animate-pulse">Loading…</div>
      <div v-else-if="error" class="text-ui-bad whitespace-pre-wrap">{{ error }}</div>
      <pre
        v-else
        class="bg-ink/90 border border-slate/40 rounded p-[2vmin] text-body overflow-auto max-h-[62vh] text-silver"
      >{{ text }}</pre>
    </div>
  </PageShell>
</template>
