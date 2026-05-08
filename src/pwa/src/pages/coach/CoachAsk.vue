<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import { useSaveStore } from '@/entities/save/model/saveStore'
import PageShell from '@/shared/ui/PageShell.vue'
import { bridge } from '@/shared/api/bridge'
import { resolveSessionId } from '@/entities/session/model/sessionContext'

type Turn = {
  role: 'user' | 'coach'
  text: string
  emotion?: string
  qa_key?: string
  turn?: number
}

const STREAM_KEY = 'pitwall:coachAskUseStream'

const router = useRouter()
const audio = useAudioStore()
const save = useSaveStore()

const input = ref('')
const busy = ref(false)
const lastError = ref<string | null>(null)
const turns = ref<Turn[]>([])
const useStream = ref(
  typeof sessionStorage !== 'undefined' && sessionStorage.getItem(STREAM_KEY) === '1',
)
/** Abort current POST /coach/ask/stream read (same role as cancelling OkHttp call). */
const streamAbort = ref<AbortController | null>(null)

watch(useStream, (v) => {
  if (typeof sessionStorage !== 'undefined') {
    sessionStorage.setItem(STREAM_KEY, v ? '1' : '0')
  }
})

const sid = computed(() => resolveSessionId(save.activeSlot))
const driverId = computed(() => save.activeSlot?.driverName?.trim() || 'driver')

const endpointHint = computed(() =>
  useStream.value ? 'POST /coach/ask/stream · SSE' : 'POST /coach/ask',
)

async function send() {
  const q = input.value.trim()
  if (!q || busy.value) return

  streamAbort.value?.abort()
  streamAbort.value = null

  busy.value = true
  lastError.value = null
  input.value = ''

  const body = {
    question: q,
    driver_id: driverId.value,
    session_id: sid.value ?? '',
  }

  if (useStream.value) {
    turns.value = [...turns.value, { role: 'user', text: q }, { role: 'coach', text: '' }]
    const ac = new AbortController()
    streamAbort.value = ac
    try {
      await bridge.coachAskStream(
        body,
        {
          onDelta: (d) => {
            const list = [...turns.value]
            const last = list[list.length - 1]
            if (last?.role === 'coach') {
              list[list.length - 1] = { ...last, text: last.text + d }
              turns.value = list
            }
          },
          onDone: (answer, emotion, meta) => {
            const list = [...turns.value]
            const last = list[list.length - 1]
            const streamed = last?.role === 'coach' ? last.text : ''
            const finalText = answer.trim().length > 0 ? answer : streamed
            const extras =
              meta?.qa_key != null || meta?.turn != null
                ? { qa_key: meta.qa_key, turn: meta.turn }
                : {}
            if (last?.role === 'coach') {
              list[list.length - 1] = {
                role: 'coach',
                text: finalText,
                emotion: emotion ?? undefined,
                ...extras,
              }
            } else {
              list.push({
                role: 'coach',
                text: finalText,
                emotion: emotion ?? undefined,
                ...extras,
              })
            }
            turns.value = list
          },
          onError: (msg) => {
            lastError.value = msg
            const list = [...turns.value]
            const last = list[list.length - 1]
            if (last?.role === 'coach') {
              list[list.length - 1] = { role: 'coach', text: `Stream failed: ${msg}` }
            } else {
              list.push({ role: 'coach', text: `Stream failed: ${msg}` })
            }
            turns.value = list
          },
        },
        { signal: ac.signal },
      )
    } finally {
      busy.value = false
      if (streamAbort.value === ac) streamAbort.value = null
    }
    return
  }

  turns.value = [...turns.value, { role: 'user', text: q }]
  try {
    const r = await bridge.coachAsk(body)
    const coachText =
      r.error && r.error.length > 0
        ? r.error
        : r.answer && r.answer.length > 0
          ? r.answer
          : '(empty response)'
    const coachTurn: Turn = {
      role: 'coach',
      text: coachText,
      emotion: r.emotion ?? undefined,
    }
    if (r.qa_key) coachTurn.qa_key = r.qa_key
    if (typeof r.turn === 'number') coachTurn.turn = r.turn
    turns.value = [...turns.value, coachTurn]
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e)
    lastError.value = msg
    turns.value = [...turns.value, { role: 'coach', text: `Request failed: ${msg}` }]
  } finally {
    busy.value = false
  }
}

async function clearChat() {
  streamAbort.value?.abort()
  streamAbort.value = null
  try {
    await bridge.coachAskEnd({
      driver_id: driverId.value,
      session_id: sid.value ?? '',
    })
  } catch {
    /* bridge may be down — still clear UI */
  }
  turns.value = []
  lastError.value = null
}

onBeforeUnmount(() => {
  streamAbort.value?.abort()
})

useKeyboard((e: KeyboardEvent) => {
  if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.push('/garage')
  }
})
</script>

<template>
  <PageShell title="ASK COACH" :hints="['B · GARAGE', 'STREAM · SSE TOGGLE']" bg="cool">
    <div class="flex flex-col gap-[2vmin] p-[2vmin] max-w-[800px] mx-auto w-full h-[min(72vh,640px)]">
      <p class="text-small text-slate">
        {{ endpointHint }} · Session ·
        <span class="text-white font-mono">{{ sid ?? 'none' }}</span>
      </p>

      <label
        class="flex items-center justify-between gap-4 px-3 py-2 rounded border border-slate/40 bg-ink/60 cursor-pointer select-none"
      >
        <span class="text-small tracking-widest text-silver">Stream (SSE)</span>
        <input v-model="useStream" type="checkbox" class="h-5 w-5 accent-[#2aa198]" :disabled="busy" />
      </label>

      <p v-if="lastError" class="text-ui-warn text-small">{{ lastError }}</p>
      <div class="flex-1 overflow-y-auto border border-slate/40 rounded bg-ink/80 p-3 space-y-3 font-ui text-body">
        <div v-for="(t, i) in turns" :key="i" :class="t.role === 'user' ? 'text-right' : 'text-left'">
          <span
            class="inline-block max-w-[85%] px-3 py-2 rounded text-left"
            :class="
              t.role === 'user'
                ? 'bg-ui-info/20 text-white border border-ui-info/40'
                : 'bg-charcoal text-silver border border-slate/40'
            "
          >
            {{ t.text }}
            <span
              v-if="t.role === 'coach' && (t.emotion || t.qa_key || t.turn != null)"
              class="block mt-1 text-small text-slate italic space-y-0.5"
            >
              <span v-if="t.emotion">{{ t.emotion }}</span>
              <span v-if="t.qa_key || t.turn != null" class="block font-mono text-[11px] text-slate/90">
                <template v-if="t.turn != null">turn {{ t.turn }}</template>
                <template v-if="t.qa_key"> · {{ t.qa_key }}</template>
              </span>
            </span>
          </span>
        </div>
      </div>
      <div class="flex gap-2 flex-wrap items-end">
        <textarea
          v-model="input"
          rows="2"
          class="flex-1 min-w-[200px] bg-ink border border-slate/50 rounded px-3 py-2 text-body text-white font-ui"
          placeholder="Ask about this session…"
          :disabled="busy"
          @keydown.enter.exact.prevent="send"
        />
        <button
          type="button"
          class="px-4 py-2 bg-ui-good text-ink font-bold rounded disabled:opacity-50"
          :disabled="busy"
          @click="send"
        >
          Send
        </button>
        <button type="button" class="px-4 py-2 border border-slate/50 rounded text-small" @click="clearChat">
          Clear + sync
        </button>
      </div>
    </div>
  </PageShell>
</template>
