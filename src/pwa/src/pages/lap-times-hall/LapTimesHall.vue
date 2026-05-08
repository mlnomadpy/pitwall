<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import { useSaveStore } from '@/entities/save/model/saveStore'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import PageShell from '@/shared/ui/PageShell.vue'
import Frame from '@/shared/ui/core/Frame.vue'
import CyberSplitView from '@/shared/ui/core/CyberSplitView.vue'
import DialogueBox from '@/widgets/dialogue-box/DialogueBox.vue'
import ErrorBoundary from '@/shared/ui/ErrorBoundary.vue'
import CyberSkeleton from '@/shared/ui/core/CyberSkeleton.vue'
import CyberTooltip from '@/shared/ui/core/CyberTooltip.vue'
import { useLapTimeStore } from '@/entities/lap-time/model/lapTimeStore'
import { onMounted } from 'vue'

const router = useRouter()
const audio = useAudioStore()
const store = useLapTimeStore()
const save = useSaveStore()

onMounted(() => {
  store.fetchLapTimes()
})

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
  if (cursorIndex.value > store.laps.length - 3) return Math.max(0, store.laps.length - visibleRows)
  return cursorIndex.value - 2
})

let pbPlayed = false

useKeyboard((e: KeyboardEvent) => {
  if (store.isLoading || store.laps.length === 0) return

  if (e.key === 'ArrowDown') {
    cursorIndex.value = Math.min(cursorIndex.value + 1, store.laps.length - 1)
    if (store.laps[cursorIndex.value].best && !pbPlayed) {
      audio.playSfx('pb_unlock')
      pbPlayed = true
    } else if (store.laps[cursorIndex.value].outlier) {
      audio.playSfx('error_quiet')
    } else {
      audio.playSfx('cursor_move')
    }
  } else if (e.key === 'ArrowUp') {
    cursorIndex.value = Math.max(cursorIndex.value - 1, 0)
    if (store.laps[cursorIndex.value].best && !pbPlayed) {
      audio.playSfx('pb_unlock')
      pbPlayed = true
    } else if (store.laps[cursorIndex.value].outlier) {
      audio.playSfx('error_quiet')
    } else {
      audio.playSfx('cursor_move')
    }
  } else if (e.key === 'Enter' || e.key === 'a') {
    audio.playSfx('cursor_select')
  } else if (e.key === 'c' || e.key === 'C') {
    audio.playSfx('cursor_select')
    router.push('/analysis/compare')
  } else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.push('/garage/analysis')
  }
})

// Convert mm:ss.s string to seconds for the box plot scale
const parseLapToSeconds = (lapStr: string) => {
  const [minStr, secStr] = lapStr.split(':')
  return parseInt(minStr) * 60 + parseFloat(secStr)
}

// Headline computed values
const bestLap = computed(() => {
  if (store.laps.length === 0) return '--:--.---'
  const validLaps = store.laps.filter(l => l.valid)
  if (validLaps.length === 0) return '--:--.---'
  const times = validLaps.map(l => parseLapToSeconds(l.time))
  const minTime = Math.min(...times)
  const idx = times.indexOf(minTime)
  return validLaps[idx].time
})

const idealLap = computed(() => {
  if (store.laps.length === 0) return '--:--.---'
  const validLaps = store.laps.filter(l => l.valid && l.sectors.every(s => s !== '--'))
  if (validLaps.length === 0) return '--:--.---'
  const sectorCount = validLaps[0].sectors.length
  let total = 0
  for (let s = 0; s < sectorCount; s++) {
    const sectorTimes = validLaps.map(l => parseFloat(l.sectors[s])).filter(n => !isNaN(n))
    total += Math.min(...sectorTimes)
  }
  const mins = Math.floor(total / 60)
  const secs = (total % 60).toFixed(3)
  return `${mins}:${secs.padStart(6, '0')}`
})

const gain = computed(() => {
  if (store.laps.length === 0) return '--'
  const bestSec = parseLapToSeconds(bestLap.value)
  const idealSec = parseLapToSeconds(idealLap.value)
  if (isNaN(bestSec) || isNaN(idealSec)) return '--'
  const diff = (bestSec - idealSec).toFixed(3)
  return `${diff}s`
})

const distScale = (val: number) => {
  const range = 111.0 - 106.0 // min plot to max plot
  const pct = (val - 106.0) / range
  return `${pct * 100}%`
}
</script>

<template>
  <PageShell title="LAP TIMES HALL" subtitle="session 2026-04-29-1503 ◀ ▶" :hints="['A · REPLAY', 'C · COMPARE', '◀ ▶ SESSION', 'B · BACK']" bg="cool">
    <!-- Headline -->
    <Frame variant="card" padding="8px" class="flex justify-around items-center mb-4">
      <div class="flex flex-col items-center">
        <span class="text-small text-slate tracking-widest">BEST LAP</span>
        <span class="text-title font-bold text-ui-good drop-shadow-[1px_1px_0_#000]">{{ bestLap }}</span>
      </div>
      <div class="flex flex-col items-center">
        <span class="text-small text-slate tracking-widest">IDEAL LAP</span>
        <span class="text-title font-bold text-white">{{ idealLap }}</span>
      </div>
      <div class="flex flex-col items-center">
        <span class="text-small text-slate tracking-widest">GAIN</span>
        <span class="text-title font-bold text-ui-good drop-shadow-[1px_1px_0_#000]">{{ gain }}</span>
      </div>
    </Frame>
    
    <CyberSplitView split="60-40" gap="md" class="flex-grow min-h-0">
      <template #left>
        <!-- Lap Table -->
        <Frame variant="default" padding="8px" class="h-full flex flex-col text-body overflow-hidden">
          <ErrorBoundary>
            <div v-if="store.isLoading" class="flex-grow flex flex-col items-center justify-center p-4">
              <CyberSkeleton variant="row" :count="5" />
            </div>
            
            <div v-else class="flex flex-col h-full overflow-hidden">
              <div class="flex text-small text-slate border-b border-slate px-2 pb-1 mb-2 tracking-widest uppercase">
                <span class="flex-1 text-center">#</span>
                <span class="flex-[3] text-center">TOTAL</span>
                <span class="flex-[2] text-center">S1</span>
                <span class="flex-[2] text-center">S2</span>
                <span class="flex-[2] text-center">S3</span>
                <span class="flex-[2] text-center">Δ BEST</span>
              </div>
              <div class="flex-grow relative overflow-hidden">
                <div 
                  class="absolute top-0 left-0 right-0 flex flex-col transition-transform duration-100"
                  :style="{ transform: `translateY(-${scrollOffset * 24}px)` }"
                >
                  <div 
                    v-for="(lap, i) in store.laps" :key="lap.lap"
                    class="flex items-center px-2 h-6 transition-colors relative cursor-pointer"
                    :class="[
                      cursorIndex === i ? 'bg-charcoal text-white' : 'text-silver',
                      lap.best ? 'text-ui-good' : ''
                    ]"
                    @click="cursorIndex = i; audio.playSfx('cursor_move')"
                  >
                    <span class="flex-1 text-center relative font-bold">
                      <span v-if="cursorIndex === i" class="absolute -left-2 text-ui-warn">▶</span>
                      {{ lap.lap }}
                    </span>
                    <span class="flex-[3] text-center font-bold">{{ lap.time }}</span>
                    <span class="flex-[2] text-center">{{ lap.sectors[0] }}</span>
                    <span class="flex-[2] text-center">{{ lap.sectors[1] }}</span>
                    <span class="flex-[2] text-center">{{ lap.sectors[2] }}</span>
                    <span class="flex-[2] text-center font-bold" :class="lap.delta && lap.delta.startsWith('-') ? 'text-ui-good' : lap.delta && lap.delta !== '--' ? 'text-ui-bad' : ''">{{ lap.delta }}</span>
                    <span v-if="lap.best" class="absolute right-2 text-ui-good drop-shadow-[1px_1px_0_#000]">★</span>
                    <span v-if="lap.outlier" class="absolute right-2 text-slate">○</span>
                  </div>
                </div>
              </div>
            </div>
          </ErrorBoundary>
        </Frame>
      </template>
      
      <template #right>
        <!-- Distribution Box-Plot & Coach -->
        <div class="h-full flex flex-col gap-4">
          <Frame variant="default" padding="12px" class="flex flex-col text-body relative min-h-[120px]">
            <div class="text-small text-slate tracking-widest mb-4">DISTRIBUTION</div>
            <div class="relative w-full h-8 mt-2">
              <!-- Whisker line -->
              <div class="absolute h-[1px] bg-slate top-4"
                   :style="{ left: distScale(distStats.min), right: `calc(100% - ${distScale(distStats.max)})` }"></div>
              <!-- Whisker caps -->
              <div class="absolute w-[2px] h-4 bg-slate top-2" :style="{ left: distScale(distStats.min) }"></div>
              <div class="absolute w-[2px] h-4 bg-slate top-2" :style="{ left: distScale(distStats.max) }"></div>
              
              <!-- Box (IQR) -->
              <div class="absolute h-6 border-2 border-silver bg-ink top-1"
                   :style="{ 
                     left: distScale(distStats.q1), 
                     width: `calc(${distScale(distStats.q3)} - ${distScale(distStats.q1)})` 
                   }"></div>
              <!-- Median line -->
              <div class="absolute w-1 h-6 bg-ui-warn top-1" :style="{ left: distScale(distStats.median) }"></div>
              
              <!-- Outliers -->
              <div v-for="out in distStats.outliers" :key="out"
                   class="absolute w-2 h-2 rounded-full border-2 border-slate top-3 -ml-1"
                   :style="{ left: distScale(out) }"></div>
              
              <!-- Cursor Marker -->
              <div class="absolute text-ui-good font-bold text-title-sm top-8 -ml-2 drop-shadow-[1px_1px_0_#000]"
                   v-if="store.laps.length > 0"
                   :style="{ left: distScale(parseLapToSeconds(store.laps[cursorIndex].time)) }">
                ▲
              </div>
            </div>
            
            <div class="mt-auto pt-4 text-small text-slate flex justify-between tracking-widest">
              <span>MEDIAN {{ Math.floor(distStats.median / 60) }}:{{ (distStats.median % 60).toFixed(1) }}</span>
              <span>σ={{ distStats.stddev }}</span>
            </div>
          </Frame>
          
          <div class="flex-grow flex flex-col justify-end">
            <DialogueBox 
              v-if="store.laps[cursorIndex]?.outlier"
              :coach-id="save.activeSlot?.preferredCoach ?? 'trod'"
              emotion="talk"
              text="You hit traffic at T11 on this one."
              compact
            />
            <DialogueBox 
              v-else-if="store.laps[cursorIndex]?.best"
              :coach-id="save.activeSlot?.preferredCoach ?? 'trod'"
              emotion="victory"
              text="Nailed the exit out of T2 on this lap."
              compact
            />
            <DialogueBox 
              v-else
              :coach-id="save.activeSlot?.preferredCoach ?? 'trod'"
              emotion="idle"
              text="Solid consistency. Kept the variance low."
              compact
            />
          </div>
        </div>
      </template>
    </CyberSplitView>
  </PageShell>
</template>

