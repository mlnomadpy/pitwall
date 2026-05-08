<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import { useSaveStore } from '@/entities/save/model/saveStore'
import PageShell from '@/shared/ui/PageShell.vue'
import { bridge } from '@/shared/api/bridge'
import { resolveSessionId } from '@/entities/session/model/sessionContext'

const router = useRouter()
const audio = useAudioStore()
const save = useSaveStore()

const focus = ref('')
const loading = ref(false)
const error = ref<string | null>(null)
const result = ref<{ score: number; why: string; session_id?: string; model?: string | null } | null>(null)

const sid = computed(() => resolveSessionId(save.activeSlot))

async function grade() {
  const id = sid.value
  if (!id) {
    error.value = 'Select an active session first (Bridge sessions).'
    result.value = null
    return
  }
  loading.value = true
  error.value = null
  result.value = null
  try {
    const level = save.activeSlot?.skillLevel ?? 'intermediate'
    result.value = await bridge.scoreSession({
      session_id: id,
      focus: focus.value.trim(),
      driver_level: level,
    })
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

useKeyboard((e: KeyboardEvent) => {
  if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.push('/garage')
  }
})
</script>

<template>
  <PageShell title="SESSION GRADE" :hints="['B · GARAGE']" bg="neutral">
    <div class="flex flex-col gap-[2vmin] p-[2vmin] max-w-[720px] mx-auto w-full text-silver font-ui">
      <p class="text-small text-slate tracking-widest">
        POST /score · Gemini (503 if API key unset). Session ·
        <span class="text-white font-mono">{{ sid ?? 'none' }}</span>
      </p>
      <label class="flex flex-col gap-2 text-small">
        <span class="text-slate tracking-widest">FOCUS (OPTIONAL)</span>
        <textarea
          v-model="focus"
          rows="3"
          class="bg-ink border border-slate/50 rounded px-3 py-2 text-body text-white font-ui resize-y"
          placeholder="e.g. braking, consistency"
        />
      </label>
      <button
        type="button"
        :disabled="loading"
        class="px-4 py-3 rounded bg-ui-info text-ink font-bold tracking-widest disabled:opacity-50"
        @click="grade"
      >
        {{ loading ? 'Grading…' : 'Grade session' }}
      </button>
      <p v-if="error" class="text-ui-bad whitespace-pre-wrap">{{ error }}</p>
      <div v-else-if="result" class="border border-slate/40 rounded p-4 bg-ink/80">
        <p class="text-title font-nums text-ui-good">{{ result.score }}</p>
        <p class="text-body mt-2">{{ result.why }}</p>
        <p v-if="result.model" class="text-small text-slate mt-2">model · {{ result.model }}</p>
      </div>
    </div>
  </PageShell>
</template>
