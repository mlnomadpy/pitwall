<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import { useSaveStore } from '@/entities/save/model/saveStore'
import PageShell from '@/shared/ui/PageShell.vue'
import { bridge } from '@/shared/api/bridge'

const router = useRouter()
const audio = useAudioStore()
const save = useSaveStore()

const tabIndex = ref(0)
const loading = ref(false)
const error = ref<string | null>(null)
const text = ref('')

const trackId = computed(() => save.activeSlot?.preferredTrack ?? 'sonoma')

const tabs = computed(() => [
  {
    id: 'markers',
    label: 'Markers',
    load: async () => bridge.get('/track/markers'),
  },
  {
    id: 'danger',
    label: 'Danger',
    load: async () => bridge.get('/track/danger_zones'),
  },
  {
    id: 'weather',
    label: 'Weather',
    load: async () => bridge.get('/track/weather'),
  },
  {
    id: 'elevation',
    label: 'Elevation',
    load: async () => bridge.get(`/track/${trackId.value}/elevation`),
  },
])

async function load() {
  loading.value = true
  error.value = null
  text.value = ''
  try {
    const fn = tabs.value[tabIndex.value]?.load
    if (!fn) return
    const raw = await fn()
    text.value = JSON.stringify(raw, null, 2)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(
  () => [tabIndex.value, trackId.value],
  () => load(),
)

useKeyboard((e: KeyboardEvent) => {
  if (e.key === '[') {
    tabIndex.value = (tabIndex.value + tabs.value.length - 1) % tabs.value.length
    audio.playSfx('cursor_move')
  } else if (e.key === ']') {
    tabIndex.value = (tabIndex.value + 1) % tabs.value.length
    audio.playSfx('cursor_move')
  } else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.push('/garage/analysis')
  }
})
</script>

<template>
  <PageShell title="TRACK REFERENCE" :hints="['[ · ] · TAB', 'B · HUB']" bg="cool">
    <div class="flex flex-col gap-[2vmin] p-[2vmin] max-w-[960px] mx-auto w-full">
      <div class="flex flex-wrap gap-2">
        <button
          v-for="(t, i) in tabs"
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
      <p class="text-small text-slate">
        GET /track/markers · /danger_zones · /weather · /{{ trackId }}/elevation
      </p>
      <div v-if="loading" class="text-ui-info animate-pulse">Loading…</div>
      <div v-else-if="error" class="text-ui-bad whitespace-pre-wrap">{{ error }}</div>
      <pre
        v-else
        class="bg-ink/90 border border-slate/40 rounded p-[2vmin] text-body overflow-auto max-h-[62vh] text-silver"
      >{{ text }}</pre>
    </div>
  </PageShell>
</template>
