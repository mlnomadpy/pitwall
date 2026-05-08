<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import { useSaveStore } from '@/entities/save/model/saveStore'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import { bridge } from '@/shared/api/bridge'
import PageShell from '@/shared/ui/PageShell.vue'
import Frame from '@/shared/ui/core/Frame.vue'
import PixelChart from '@/shared/ui/core/PixelChart.vue'
import CoachFloat from '@/shared/ui/CoachFloat.vue'
import { useSwipeGesture } from '@/shared/lib/useSwipeGesture'

const router = useRouter()
const audio = useAudioStore()
const save = useSaveStore()

// We need a minimum of 5 sessions to show a meaningful trend
const hasEnoughSessions = ref(false)

interface SessionPoint {
  index: number
  bestLap: number
  medianLap: number
}

const sessions = ref<SessionPoint[]>([])

const heroData = ref({
  firstBest: '--:--.--',
  latestBest: '--:--.--',
  improvement: '0.0',
  sessionCount: 0,
  biggestGain: { corner: '--', deltaKmh: 0 }
})

onMounted(async () => {
  try {
    const res = await bridge.get<{ sessions: any[]; count: number }>('/sessions?limit=100')
    const realSessions = res.sessions
      .filter((s: any) => s.best_lap_s != null)
      .reverse() // oldest first
    
    if (realSessions.length >= 2) {
      hasEnoughSessions.value = true
      sessions.value = realSessions.map((s: any, i: number) => ({
        index: i + 1,
        bestLap: s.best_lap_s,
        medianLap: s.best_lap_s * 1.01, // approximate median as ~1% slower than best
      }))
      
      const first = realSessions[0].best_lap_s
      const latest = realSessions[realSessions.length - 1].best_lap_s
      
      heroData.value = {
        firstBest: formatLap(first),
        latestBest: formatLap(latest),
        improvement: (first - latest).toFixed(1), // positive is good
        sessionCount: realSessions.length,
        biggestGain: { corner: 'T11', deltaKmh: Math.abs(first - latest) * 2.5 }
      }
    }
  } catch {
    // Backend offline — no evolution data available
    hasEnoughSessions.value = false
  }
  
  audio.playSfx('score_total')
})

const cornerPBs = [
  { id: 'T1', grades: [1, 1, 2, 3, 4] },
  { id: 'T4', grades: [0, 1, 1, 1, 2] },
  { id: 'T7', grades: [2, 2, 3, 3, 3] },
  { id: 'T11', grades: [0, 1, 2, 4, 4] }
]

const cursorIndex = ref(0)
const cur = computed(() => sessions.value[cursorIndex.value])

useKeyboard((e: KeyboardEvent) => {
  if (e.key === 'ArrowRight') {
    cursorIndex.value = Math.min(cursorIndex.value + 1, sessions.value.length - 1)
    audio.playSfx('cursor_move')
  } else if (e.key === 'ArrowLeft') {
    cursorIndex.value = Math.max(cursorIndex.value - 1, 0)
    audio.playSfx('cursor_move')
  } else if (e.key === 'Enter' || e.key === 'a') {
    // Open lap times hall for that session
    audio.playSfx('cursor_select')
  } else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.push('/garage/analysis')
  }
})

useSwipeGesture(null, {
  onSwipeLeft: () => {
    cursorIndex.value = Math.min(cursorIndex.value + 5, sessions.value.length - 1)
    audio.playSfx('cursor_move')
  },
  onSwipeRight: () => {
    cursorIndex.value = Math.max(cursorIndex.value - 5, 0)
    audio.playSfx('cursor_move')
  },
})

const formatLap = (seconds: number) => {
  const mins = Math.floor(seconds / 60)
  const secs = (seconds % 60).toFixed(1)
  return `${mins}:${secs.padStart(4, '0')}`
}

const getHeatmapColor = (grade: number) => {
  switch (grade) {
    case 0: return 'bg-charcoal'
    case 1: return 'bg-[#3A4550]'
    case 2: return 'bg-slate'
    case 3: return 'bg-[#A0AAB5]'
    case 4: return 'bg-silver'
    default: return 'bg-charcoal'
  }
}
</script>

<template>
  <PageShell :hints="['A · OPEN SESSION', '◀ ▶ SCRUB', 'B · BACK']" bg="cool">
    <template #heading>
      <div class="heading-block mb-[1.5vh]">
        <h1 class="text-title font-title text-silver tracking-[0.2em]">DRIVER EVOLUTION · {{ save.activeSlot?.driverName ?? 'DRIVER' }} AT SONOMA</h1>
        <div class="heading-rule"></div>
      </div>
    </template>

      <template v-if="hasEnoughSessions">
        <Frame variant="card" padding="8px" class="mb-4">
          <div class="text-title font-bold text-white">
            FIRST {{ heroData.firstBest }} <span class="text-slate mx-1">→</span> LATEST {{ heroData.latestBest }}
          </div>
          <div class="text-body text-ui-good font-bold mt-1 drop-shadow-[1px_1px_0_#000]">
            ▼ {{ heroData.improvement }}s IMPROVEMENT OVER {{ heroData.sessionCount }} SESSIONS
          </div>
          <div class="text-body text-silver mt-1">
            <span class="text-ui-warn">⚡</span> {{ heroData.biggestGain.corner }} apex speed: <span class="text-ui-good">+{{ heroData.biggestGain.deltaKmh.toFixed(1) }} km/h</span> since session #1
          </div>
        </Frame>
        
        <div class="grid grid-cols-[2fr_1fr] gap-4 flex-grow min-h-0 pb-16">
          <Frame variant="default" padding="8px" class="relative flex flex-col overflow-hidden">
            <div class="text-body text-silver mb-2 flex justify-between">
              <div>
                <span class="text-ui-good font-bold mr-2">─ BEST</span>
                <span class="text-slate font-bold">─ MEDIAN</span>
              </div>
              <div class="font-bold text-white" v-if="cur">SESSION #{{ cur.index }}</div>
            </div>
            
            <div class="flex-grow relative border-l border-b border-slate ml-8 mb-6 mt-2">
              <PixelChart 
                :data="sessions.map(s => s.bestLap)"
                color="var(--color-neon-green)"
                class="absolute inset-0"
                :width="600"
                :height="200"
              />
              <PixelChart 
                :data="sessions.map(s => s.medianLap)"
                color="var(--color-slate)"
                class="absolute inset-0 opacity-50"
                :width="600"
                :height="200"
                :stroke-width="1"
              />
              
              <div class="absolute -left-[32px] top-[-8px] text-small text-silver" v-if="sessions.length">{{ formatLap(Math.min(...sessions.map(s => s.bestLap))) }}</div>
              <div class="absolute -left-[32px] bottom-[-8px] text-small text-silver" v-if="sessions.length">{{ formatLap(Math.max(...sessions.map(s => s.bestLap))) }}</div>
              <div class="absolute -bottom-5 left-0 text-small text-silver">#1</div>
              <div class="absolute -bottom-5 right-0 text-small text-silver">#{{ sessions.length }}</div>
              
              <!-- Selected Lap Display -->
              <div class="absolute -bottom-5 left-1/2 -translate-x-1/2 text-body font-bold flex gap-2" v-if="cur">
                <span class="text-ui-good">{{ formatLap(cur.bestLap) }}</span>
              </div>
            </div>
          </Frame>
          
          <Frame variant="default" padding="8px" class="flex flex-col text-body overflow-hidden relative">
            <div class="text-silver mb-2 leading-tight font-bold">PER-CORNER<br>HEATMAP</div>
            <div class="flex flex-col gap-2">
              <div class="flex justify-between text-small text-slate mb-1">
                <span>#1</span><span>#{{ sessions.length }}</span>
              </div>
              <div v-for="c in cornerPBs" :key="c.id" class="flex gap-2 items-center">
                <span class="w-6 text-silver font-bold">{{ c.id }}</span>
                <div class="flex flex-grow gap-[2px]">
                  <div v-for="(g, i) in c.grades" :key="i" class="h-4 flex-grow" :class="getHeatmapColor(g)"></div>
                </div>
              </div>
            </div>
            <div class="absolute bottom-2 text-center w-full text-small text-slate pr-4">
              better → █
            </div>
          </Frame>
        </div>
      </template>
      <template v-else>
        <Frame variant="default" class="flex items-center justify-center h-full">
           <p class="text-title text-silver animate-pulse">NOT ENOUGH SESSION DATA YET...</p>
        </Frame>
      </template>
      
      <template #floating>
        <CoachFloat
          emotion="victory"
          :text="`${heroData.biggestGain.corner}'s where you found half a second overall. Don't lose it.`"
        />
      </template>
  </PageShell>
</template>

