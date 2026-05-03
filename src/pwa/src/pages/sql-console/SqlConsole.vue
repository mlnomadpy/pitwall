<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import { useSaveStore } from '@/entities/save/model/saveStore'
import StatusBar from '@/widgets/status-bar/StatusBar.vue'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'
import HintBar from '@/widgets/hint-bar/HintBar.vue'
import CyberButton from '@/shared/ui/core/CyberButton.vue'
import CyberBox from '@/shared/ui/core/CyberBox.vue'

const router = useRouter()
const audio = useAudioStore()
const save = useSaveStore()

const state = ref<'editor' | 'running' | 'result' | 'error' | 'examples'>('editor')

const editorText = ref(`-- Where did I lose time at T7 best vs second-best
SELECT distance_m, speed_best * 3.6 AS speed_best,
       speed_2nd * 3.6 AS speed_2nd,
       (speed_2nd - speed_best) * 3.6 AS delta_kmh
FROM compare_laps('sonoma-001', 7, 5)
WHERE distance_m BETWEEN 1620 AND 1820
ORDER BY delta_kmh DESC LIMIT 10;`)

const textareaRef = ref<HTMLTextAreaElement | null>(null)

const resultData = ref<any[] | null>(null)
const errorMsg = ref<string | null>(null)
const resultTime = ref('0.0')

const examples = [
  { title: 'Time Lost at T7', query: `-- Where did I lose time at T7 best vs second-best\nSELECT distance_m, speed_best * 3.6 AS speed_best,\n       speed_2nd * 3.6 AS speed_2nd,\n       (speed_2nd - speed_best) * 3.6 AS delta_kmh\nFROM compare_laps('sonoma-001', 7, 5)\nWHERE distance_m BETWEEN 1620 AND 1820\nORDER BY delta_kmh DESC LIMIT 10;` },
  { title: 'Top 5 Laps', query: `SELECT lap_num, lap_time_ms, sector_1, sector_2, sector_3\nFROM laps\nORDER BY lap_time_ms ASC\nLIMIT 5;` },
  { title: 'Trail-Brake Quality', query: `SELECT corner_id, MAX(brake_bar) as max_brk, MAX(g_lat) as max_g\nFROM telemetry\nWHERE brake_bar > 5 AND g_lat > 0.4\nGROUP BY corner_id;` }
]
const exampleIndex = ref(0)

const coachEmotion = computed(() => {
  if (state.value === 'running') return 'analyzing'
  if (state.value === 'error') return 'concerned'
  if (state.value === 'result') return 'encouraging'
  return 'intense'
})

const hints = computed(() => {
  if (state.value === 'editor') return ['◆ EXAMPLES', 'ENTER · RUN', 'ESC · BLUR']
  if (state.value === 'examples') return ['▲ ▼ SELECT', 'A · LOAD', 'B · CANCEL']
  return ['A · RUN', 'B · BACK', '◆ EXAMPLES']
})

const runQuery = () => {
  state.value = 'running'
  errorMsg.value = null
  resultData.value = null
  audio.playSfx('cursor_select')
  textareaRef.value?.blur()
  
  setTimeout(() => {
    // Mock success or failure
    if (editorText.value.toLowerCase().includes('select')) {
      state.value = 'result'
      resultTime.value = (Math.random() * 0.8 + 0.1).toFixed(1)
      resultData.value = [
        { distance_m: 1720, speed_best: 92.1, speed_2nd: 99.4, delta_kmh: '+7.3' },
        { distance_m: 1715, speed_best: 91.8, speed_2nd: 98.2, delta_kmh: '+6.4' },
        { distance_m: 1710, speed_best: 91.2, speed_2nd: 96.5, delta_kmh: '+5.3' },
        { distance_m: 1705, speed_best: 90.0, speed_2nd: 94.0, delta_kmh: '+4.0' }
      ]
      audio.playSfx('goal_complete')
    } else {
      state.value = 'error'
      errorMsg.value = 'Parser Error: syntax error at or near "' + editorText.value.substring(0, 10) + '..."'
      audio.playSfx('error_quiet')
    }
  }, 1000)
}

const handleKey = (e: KeyboardEvent) => {
  if (state.value === 'examples') {
    e.preventDefault()
    if (e.key === 'ArrowDown') {
      exampleIndex.value = (exampleIndex.value + 1) % examples.length
      audio.playSfx('cursor_move')
    } else if (e.key === 'ArrowUp') {
      exampleIndex.value = (exampleIndex.value - 1 + examples.length) % examples.length
      audio.playSfx('cursor_move')
    } else if (e.key === 'Enter' || e.key === 'a') {
      editorText.value = examples[exampleIndex.value].query
      state.value = 'editor'
      audio.playSfx('cursor_select')
      nextTick(() => textareaRef.value?.focus())
    } else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
      state.value = 'editor'
      audio.playSfx('cancel')
      nextTick(() => textareaRef.value?.focus())
    }
    return
  }
  
  if (state.value === 'editor' && document.activeElement === textareaRef.value) {
    if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
      e.preventDefault()
      runQuery()
    } else if (e.key === 'Escape') {
      textareaRef.value?.blur()
      // manually update state if they just hit escape to blur
      // let it stay 'editor' conceptually, but UI shows hints for blurred state
    } else if (e.key === 'Tab') {
      e.preventDefault() // prevent tabbing out
      const start = textareaRef.value!.selectionStart
      const end = textareaRef.value!.selectionEnd
      editorText.value = editorText.value.substring(0, start) + '  ' + editorText.value.substring(end)
      nextTick(() => {
        textareaRef.value!.selectionStart = textareaRef.value!.selectionEnd = start + 2
      })
    }
    return
  }

  // Not focused or not in editor state
  if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.back()
  } else if (e.key === 'Enter' || e.key === 'a') {
    runQuery()
  } else if (e.key === 'Shift') {
    state.value = 'examples'
    audio.playSfx('cursor_select')
  }
}

const handleEditorClick = () => {
  if (state.value !== 'editor') {
    state.value = 'editor'
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKey)
  nextTick(() => textareaRef.value?.focus())
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKey)
})
</script>

<template>
  <div class="viewport pixelated relative w-full h-full bg-ink text-silver overflow-hidden  font-ui">
    <StatusBar :extra="`░ DUCKDB ${state === 'running' ? 'EXECUTING' : 'ON'}`" />
    
    <div class="page-bg"></div>
    
    <div class="content pt-[6vh] px-2 flex flex-col h-full z-0 relative gap-2">
      <div class="heading-block mb-[1.5vh]">
        <h1 class="text-title font-title text-silver tracking-[0.2em]">SQL CONSOLE</h1>
      </div>

      <div class="flex flex-col gap-2 flex-grow min-h-0 mx-2 pb-6 relative">
        
        <!-- Editor -->
        <CyberPanel variant="glass" :border="state === 'editor' ? 'primary' : 'secondary'" class="flex flex-col text-body p-2 relative h-[clamp(60px,15vh,120px)] shrink-0 transition-colors">
          <textarea
            ref="textareaRef"
            v-model="editorText"
            class="w-full h-full bg-transparent text-white font-mono resize-none focus:outline-none placeholder-slate/50 selection:bg-ui-good selection:text-ink leading-tight"
            spellcheck="false"
            @click="handleEditorClick"
            @focus="state = 'editor'"
          ></textarea>
        </CyberPanel>

        <!-- Toolbar -->
        <div class="flex gap-4 text-body font-bold mt-2">
          <CyberButton @click="runQuery" size="sm" variant="info">
            <template #icon>
              <span class="mr-1" :class="{'animate-spin': state === 'running'}">
                {{ state === 'running' ? '⚙' : '▶' }}
              </span>
            </template>
            {{ state === 'running' ? 'RUNNING' : 'RUN' }}
          </CyberButton>
          <CyberButton size="sm" variant="dark">☆ SAVE</CyberButton>
          <CyberButton size="sm" variant="dark">📂 LOAD</CyberButton>
          <CyberButton @click="editorText = ''" size="sm" variant="primary">🗑 CLEAR</CyberButton>
        </div>

        <!-- Result/Error Panel -->
        <CyberPanel v-if="state === 'result' || state === 'error' || state === 'running'" variant="glass" border="none" class="flex flex-col text-body p-2 flex-grow overflow-hidden relative">
          <div v-if="state === 'running'" class="absolute inset-0 flex items-center justify-center">
            <span class="animate-spin text-title-lg text-ui-good">⚙</span>
          </div>
          
          <template v-else-if="state === 'result' && resultData">
            <div class="text-slate mb-2">RESULT {{ resultData.length }} rows · {{ resultTime }} s</div>
            <table class="w-full text-left font-mono">
              <thead>
                <tr class="text-ui-info border-b border-charcoal">
                  <th v-for="key in Object.keys(resultData[0])" :key="key" class="pb-1 pr-2">{{ key }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, i) in resultData" :key="i" class="text-silver">
                  <td v-for="key in Object.keys(row)" :key="key" class="pr-2">{{ row[key] }}</td>
                </tr>
              </tbody>
            </table>
          </template>

          <template v-else-if="state === 'error'">
            <div class="text-ui-warn font-bold mb-2">EXECUTION FAILED</div>
            <div class="font-mono text-ui-warn whitespace-pre-wrap">{{ errorMsg }}</div>
          </template>
        </CyberPanel>
        
        <!-- Examples Modal -->
        <div v-if="state === 'examples'" class="absolute inset-0 bg-black/80 z-50 flex items-center justify-center p-4">
          <CyberPanel variant="solid" border="primary" class="w-full bg-ink p-2 text-body flex flex-col">
            <div class="text-ui-info font-bold mb-2 border-b border-charcoal pb-1">EXAMPLE QUERIES</div>
            <div class="flex flex-col gap-1">
              <div v-for="(ex, i) in examples" :key="i" 
                   class="px-2 py-1 flex justify-between items-center"
                   :class="exampleIndex === i ? 'bg-charcoal text-white font-bold' : 'text-slate'">
                <span>{{ exampleIndex === i ? '▶ ' : '' }}{{ ex.title }}</span>
              </div>
            </div>
          </CyberPanel>
        </div>

      </div>
      
      <!-- Coach -->
      <div class="absolute bottom-[6vh] right-2 flex flex-col items-end gap-1">
        <CyberBox variant="charcoal" border="slate" class="text-small px-2 py-1 text-slate">{{ save.slots[save.activeSlotId?-1:0]?.preferredCoach?.toUpperCase() ?? 'T-ROD' }}</CyberBox>
        <CyberBox variant="charcoal" border="slate" class="w-[clamp(36px,8vmin,64px)] h-[clamp(36px,8vmin,64px)] overflow-hidden relative">
           <!-- Placeholder for coach sprite since we aren't using the full Sprite component to keep it simpler here -->
           <div class="absolute inset-0 flex items-center justify-center font-bold"
                :class="{
                  'text-ui-warn': coachEmotion === 'concerned',
                  'text-ui-good': coachEmotion === 'encouraging',
                  'text-ui-info': coachEmotion === 'analyzing',
                  'text-silver': coachEmotion === 'intense'
                }">
             {{ coachEmotion }}
           </div>
        </CyberBox>
      </div>
    </div>
    
    <HintBar :hints="hints" />
  </div>
</template>

<style scoped>
/* No hardcoded viewport dimensions — fullscreen is enforced by global.css */
</style>
