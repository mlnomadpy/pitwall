<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import StatusBar from '@/widgets/status-bar/StatusBar.vue'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'
import HintBar from '@/widgets/hint-bar/HintBar.vue'
import DialogueBox from '@/widgets/dialogue-box/DialogueBox.vue'
import { useSaveStore } from '@/entities/save/model/saveStore'

const router = useRouter()
const audio = useAudioStore()
const save = useSaveStore()

// Mock data
const bestLap = '1:46.8'
const idealLap = '1:46.4'
const gain = '0.4s'

const laps = [
  { num: 1, total: '1:48.2', s1: '33.1', s2: '38.4', s3: '36.7', delta: '+1.4', best: false },
  { num: 2, total: '1:47.5', s1: '32.9', s2: '38.2', s3: '36.4', delta: '+0.7', best: false },
  { num: 3, total: '1:46.8', s1: '32.4', s2: '38.1', s3: '36.3', delta: '-', best: true },
  { num: 4, total: '1:48.0', s1: '33.0', s2: '38.5', s3: '36.5', delta: '+1.2', best: false },
  { num: 5, total: '1:47.3', s1: '32.8', s2: '38.2', s3: '36.3', delta: '+0.5', best: false },
  { num: 6, total: '1:50.1', s1: '34.2', s2: '39.0', s3: '36.9', delta: '+3.3', best: false, outlier: true },
  { num: 7, total: '1:47.0', s1: '32.5', s2: '38.0', s3: '36.5', delta: '+0.2', best: false },
  { num: 8, total: '1:46.9', s1: '32.6', s2: '38.0', s3: '36.3', delta: '+0.1', best: false },
]

const distStats = {
  min: 106.8, // 1:46.8
  q1: 107.0,
  median: 107.5,
  q3: 108.2,
  max: 108.5,
  outliers: [110.1], // Lap 6 (1:50.1)
  stddev: '0.6s'
}

const cursorIndex = ref(0)
const visibleRows = 5
const scrollOffset = computed(() => {
  if (cursorIndex.value < 2) return 0
  if (cursorIndex.value > laps.length - 3) return Math.max(0, laps.length - visibleRows)
  return cursorIndex.value - 2
})

let pbPlayed = false

const handleKey = (e: KeyboardEvent) => {
  if (e.key === 'ArrowDown') {
    cursorIndex.value = Math.min(cursorIndex.value + 1, laps.length - 1)
    if (laps[cursorIndex.value].best && !pbPlayed) {
      audio.playSfx('pb_unlock')
      pbPlayed = true
    } else if (laps[cursorIndex.value].outlier) {
      audio.playSfx('error_quiet')
    } else {
      audio.playSfx('cursor_move')
    }
  } else if (e.key === 'ArrowUp') {
    cursorIndex.value = Math.max(cursorIndex.value - 1, 0)
    if (laps[cursorIndex.value].best && !pbPlayed) {
      audio.playSfx('pb_unlock')
      pbPlayed = true
    } else if (laps[cursorIndex.value].outlier) {
      audio.playSfx('error_quiet')
    } else {
      audio.playSfx('cursor_move')
    }
  } else if (e.key === 'Enter' || e.key === 'a') {
    audio.playSfx('cursor_select')
    // router.push('/replay') // Replay feature is post-MVP
  } else if (e.key === 'c' || e.key === 'C') {
    audio.playSfx('cursor_select')
    router.push('/analysis/compare')
  } else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.push('/garage/analysis')
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKey)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKey)
})

// Convert mm:ss.s string to seconds for the box plot scale
const parseLapToSeconds = (lapStr: string) => {
  const [minStr, secStr] = lapStr.split(':')
  return parseInt(minStr) * 60 + parseFloat(secStr)
}

const distScale = (val: number) => {
  const range = 111.0 - 106.0 // min plot to max plot (1:46.0 to 1:51.0)
  const pct = (val - 106.0) / range
  return `${pct * 100}%`
}
</script>

<template>
  <div class="viewport pixelated relative w-full h-full bg-ink text-silver overflow-hidden  font-ui">
    <StatusBar />
    
    <div class="page-bg"></div>
    
    <div class="content pt-[6vh] px-2 flex flex-col h-full z-0 relative gap-2">
      <div class="heading-block mb-[1.5vh]">
        <h1 class="text-title text-silver font-bold">LAP TIMES HALL</h1>
        <span class="text-body text-silver">session 2026-04-29-1503 ◀ ▶</span>
      </div>

      <!-- Headline -->
      <CyberPanel class="flex justify-around items-center py-2 text-body">
        <div class="flex flex-col items-center">
          <span class="text-body text-silver">BEST LAP</span>
          <span class="text-title font-bold text-ui-good">{{ bestLap }}</span>
        </div>
        <div class="flex flex-col items-center">
          <span class="text-body text-silver">IDEAL LAP</span>
          <span class="text-title font-bold">{{ idealLap }}</span>
        </div>
        <div class="flex flex-col items-center">
          <span class="text-body text-silver">GAIN</span>
          <span class="text-title font-bold text-ui-good">{{ gain }}</span>
        </div>
      </CyberPanel>
      
      <div class="grid grid-cols-[13fr_11fr] gap-2 flex-grow min-h-0 pb-20">
        <!-- Lap Table -->
        <CyberPanel class="flex flex-col text-body overflow-hidden">
          <div class="flex text-silver border-b border-slate px-1 pb-1 mb-1 bg-charcoal">
            <span class="w-3 text-center">#</span>
            <span class="w-9 text-center">TOTAL</span>
            <span class="w-6 text-center">S1</span>
            <span class="w-6 text-center">S2</span>
            <span class="w-6 text-center">S3</span>
            <span class="w-6 text-center">Δ</span>
          </div>
          <div class="flex-grow relative mt-1">
            <div 
              class="absolute top-0 left-0 right-0 flex flex-col transition-transform duration-100"
              :style="{ transform: `translateY(-${scrollOffset * 16}px)` }"
            >
              <div 
                v-for="(lap, i) in laps" :key="lap.num"
                class="flex items-center px-1 h-[clamp(12px,3vmin,24px)] transition-colors"
                :class="[
                  cursorIndex === i ? 'bg-charcoal text-white' : 'text-silver',
                  lap.best ? 'text-ui-good' : ''
                ]"
              >
                <span class="w-3 text-center relative font-bold">
                  <span v-if="cursorIndex === i" class="absolute -left-2 text-body">▶</span>
                  {{ lap.num }}
                </span>
                <span class="w-9 text-center font-bold">{{ lap.total }}</span>
                <span class="w-6 text-center">{{ lap.s1 }}</span>
                <span class="w-6 text-center">{{ lap.s2 }}</span>
                <span class="w-6 text-center">{{ lap.s3 }}</span>
                <span class="w-6 text-center">{{ lap.delta }}</span>
                <span v-if="lap.best" class="ml-[2px] text-body">★</span>
                <span v-if="lap.outlier" class="ml-[2px] text-body">○</span>
              </div>
            </div>
          </div>
        </CyberPanel>
        
        <!-- Distribution Box-Plot & Coach -->
        <div class="flex flex-col gap-2">
          <CyberPanel class="flex flex-col text-body relative p-2 h-[clamp(60px,12vh,100px)]">
            <div class="text-silver mb-2">DISTRIBUTION</div>
            <div class="relative w-full h-[60px] mt-1">
              <!-- Whisker line -->
              <div class="absolute h-[1px] bg-silver top-[10px]"
                   :style="{ left: distScale(distStats.min), right: `calc(100% - ${distScale(distStats.max)})` }"></div>
              <!-- Whisker caps -->
              <div class="absolute w-[1px] h-3 bg-silver top-[5px]" :style="{ left: distScale(distStats.min) }"></div>
              <div class="absolute w-[1px] h-3 bg-silver top-[5px]" :style="{ left: distScale(distStats.max) }"></div>
              
              <!-- Box (IQR) -->
              <div class="absolute h-4 border border-silver bg-charcoal top-[3px]"
                   :style="{ 
                     left: distScale(distStats.q1), 
                     width: `calc(${distScale(distStats.q3)} - ${distScale(distStats.q1)})` 
                   }"></div>
              <!-- Median line -->
              <div class="absolute w-[2px] h-4 bg-ui-warn top-[3px]" :style="{ left: distScale(distStats.median) }"></div>
              
              <!-- Outliers -->
              <div v-for="out in distStats.outliers" :key="out"
                   class="absolute w-[3px] h-[3px] rounded-full border border-silver top-[9px] -ml-[1px]"
                   :style="{ left: distScale(out) }"></div>
              
              <!-- Cursor Marker -->
              <div class="absolute text-ui-good font-bold text-body top-[18px] transition-all duration-200 -ml-1 drop-shadow-[1px_1px_0_#000]"
                   :style="{ left: distScale(parseLapToSeconds(laps[cursorIndex].total)) }">
                ▲
              </div>
            </div>
            
            <div class="mt-auto text-silver flex justify-between">
              <span>median {{ Math.floor(distStats.median / 60) }}:{{ (distStats.median % 60).toFixed(1) }}</span>
              <span>σ={{ distStats.stddev }}</span>
            </div>
          </CyberPanel>
          
          <DialogueBox 
            v-if="laps[cursorIndex].outlier"
            :coach-id="save.slots[save.activeSlotId!-1]?.preferredCoach ?? 'trod'"
            emotion="talk"
            text="You hit traffic at T11 on this one."
            class="scale-[0.8] origin-top-left w-[125%]"
          />
          <DialogueBox 
            v-else-if="laps[cursorIndex].best"
            :coach-id="save.slots[save.activeSlotId!-1]?.preferredCoach ?? 'trod'"
            emotion="victory"
            text="Nailed the exit out of T2 on this lap."
            class="scale-[0.8] origin-top-left w-[125%]"
          />
          <DialogueBox 
            v-else
            :coach-id="save.slots[save.activeSlotId!-1]?.preferredCoach ?? 'trod'"
            emotion="idle"
            text="Solid consistency. Kept the variance low."
            class="scale-[0.8] origin-top-left w-[125%]"
          />
        </div>
      </div>
      
    </div>
    
    <HintBar :hints="['A · REPLAY (SOON)', 'C · COMPARE', '◀ ▶ SESSION', 'B · BACK']" />
  </div>
</template>

<style scoped>
/* No hardcoded viewport dimensions — fullscreen is enforced by global.css */
</style>
