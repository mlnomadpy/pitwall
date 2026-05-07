<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import { useSaveStore } from '@/entities/save/model/saveStore'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import PageShell from '@/shared/ui/PageShell.vue'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'
import CoachFloat from '@/shared/ui/CoachFloat.vue'
import { useSwipeGesture } from '@/shared/lib/useSwipeGesture'

const router = useRouter()
const audio = useAudioStore()
const save = useSaveStore()

// We need a minimum of 5 sessions to show a meaningful trend
const hasEnoughSessions = ref(true)

const heroData = {
  firstBest: '1:50.5',
  latestBest: '1:46.8',
  improvement: '-3.7',
  sessionCount: 47,
  biggestGain: { corner: 'T11', deltaKmh: 8.2 }
}

const sessions = Array.from({ length: 47 }, (_, i) => {
  // Generate a downward trend with some noise
  const progress = i / 46
  const noise = (Math.random() - 0.5) * 1.5
  return {
    index: i + 1,
    bestLap: 110.5 - (progress * 3.7) + noise,
    medianLap: 111.5 - (progress * 3.5) + noise + 0.5
  }
})

const cornerPBs = [
  { id: 'T1', grades: [1, 1, 2, 3, 4] },
  { id: 'T4', grades: [0, 1, 1, 1, 2] },
  { id: 'T7', grades: [2, 2, 3, 3, 3] },
  { id: 'T11', grades: [0, 1, 2, 4, 4] }
]

const cursorIndex = ref(sessions.length - 1)
const cur = computed(() => sessions[cursorIndex.value])

useKeyboard((e: KeyboardEvent) => {
  if (e.key === 'ArrowRight') {
    cursorIndex.value = Math.min(cursorIndex.value + 1, sessions.length - 1)
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

onMounted(() => {
  audio.playSfx('score_total')
})

useSwipeGesture(null, {
  onSwipeLeft: () => {
    cursorIndex.value = Math.min(cursorIndex.value + 5, sessions.length - 1)
    audio.playSfx('cursor_move')
  },
  onSwipeRight: () => {
    cursorIndex.value = Math.max(cursorIndex.value - 5, 0)
    audio.playSfx('cursor_move')
  },
})

// Chart coordinates
const getX = (index: number) => {
  return ((index - 1) / (sessions.length - 1)) * 100
}

const getY = (val: number) => {
  const min = 106.0
  const max = 113.0
  return 100 - (((val - min) / (max - min)) * 100)
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

const formatLap = (seconds: number) => {
  const mins = Math.floor(seconds / 60)
  const secs = (seconds % 60).toFixed(1)
  return `${mins}:${secs.padStart(4, '0')}`
}
</script>

<template>
  <PageShell :hints="['A · OPEN SESSION (SOON)', '◀ ▶ SCRUB', 'B · BACK']" bg="cool">
    <template #heading>
      <div class="heading-block mb-[1.5vh]">
        <h1 class="text-title font-title text-silver tracking-[0.2em]">DRIVER EVOLUTION · {{ save.activeSlot?.driverName ?? 'DRIVER' }} AT SONOMA</h1>
        <div class="heading-rule"></div>
      </div>
    </template>

      <template v-if="hasEnoughSessions">
        <CyberPanel class="p-2 relative">
          <div class="text-title font-bold text-white">
            FIRST {{ heroData.firstBest }} <span class="text-slate mx-1">→</span> LATEST {{ heroData.latestBest }}
          </div>
          <div class="text-body text-ui-good font-bold mt-1 drop-shadow-[1px_1px_0_#000]">
            ▼ {{ heroData.improvement }}s OVER {{ heroData.sessionCount }} SESSIONS
          </div>
          <div class="text-body text-silver mt-1">
            <span class="text-ui-warn">⚡</span> {{ heroData.biggestGain.corner }} apex speed: <span class="text-ui-good">+{{ heroData.biggestGain.deltaKmh }} km/h</span> since session #1
          </div>
        </CyberPanel>
        
        <div class="grid grid-cols-[2fr_1fr] gap-2 flex-grow min-h-0 pb-16">
          <CyberPanel class="relative p-2 flex flex-col overflow-hidden">
            <div class="text-body text-silver mb-2 flex justify-between">
              <div>
                <span class="text-ui-good font-bold mr-2">─ BEST</span>
                <span class="text-slate font-bold">─ MEDIAN</span>
              </div>
              <div class="font-bold text-white">SESSION #{{ cur.index }}</div>
            </div>
            
            <div class="flex-grow relative border-l border-b border-slate ml-5 mb-4 mt-2">
              <!-- Grid lines -->
              <div class="absolute w-full h-[1px] bg-ui-info/20 top-[25%]"></div>
              <div class="absolute w-full h-[1px] bg-ui-info/20 top-[50%]"></div>
              <div class="absolute w-full h-[1px] bg-ui-info/20 top-[75%]"></div>
              
              <svg viewBox="0 0 100 100" class="w-full h-full preserve-aspect-ratio-none overflow-visible cursor-pointer" @click="(e: MouseEvent) => { const rect = (e.currentTarget as SVGElement).getBoundingClientRect(); const pct = (e.clientX - rect.left) / rect.width; cursorIndex = Math.round(pct * (sessions.length - 1)); audio.playSfx('cursor_move') }">
                <!-- Median line -->
                <path :d="`M ` + sessions.map(s => `${getX(s.index)} ${getY(s.medianLap)}`).join(' L ')" 
                      fill="none" stroke="var(--color-charcoal-light)" stroke-width="1.5" stroke-linejoin="bevel" stroke-dasharray="2 2"/>
                      
                <!-- Best line -->
                <path :d="`M ` + sessions.map(s => `${getX(s.index)} ${getY(s.bestLap)}`).join(' L ')" 
                      fill="none" stroke="var(--color-neon-green)" stroke-width="1.5" stroke-linejoin="bevel"/>
                      
                <!-- Cursor line -->
                <line :x1="getX(cur.index)" y1="0" :x2="getX(cur.index)" y2="100" stroke="#FFFFFF" stroke-width="0.5" opacity="0.5"/>
                
                <!-- Active points -->
                <circle :cx="getX(cur.index)" :cy="getY(cur.medianLap)" r="1.5" fill="var(--color-charcoal-light)" stroke="#FFFFFF" stroke-width="0.5"/>
                <circle :cx="getX(cur.index)" :cy="getY(cur.bestLap)" r="1.5" fill="var(--color-neon-green)" stroke="#FFFFFF" stroke-width="0.5"/>
              </svg>
              
              <div class="absolute -left-[22px] top-[-4px] text-small text-silver">1:46</div>
              <div class="absolute -left-[22px] bottom-[-4px] text-small text-silver">1:53</div>
              <div class="absolute -bottom-5 left-0 text-small text-silver">#1</div>
              <div class="absolute -bottom-5 right-0 text-small text-silver">#{{ sessions.length }}</div>
              
              <!-- Selected Lap Display -->
              <div class="absolute -bottom-5 left-1/2 -translate-x-1/2 text-body font-bold flex gap-2">
                <span class="text-ui-good">{{ formatLap(cur.bestLap) }}</span>
              </div>
            </div>
          </CyberPanel>
          
          <CyberPanel class="p-2 flex flex-col text-body overflow-hidden relative">
            <div class="text-silver mb-2 leading-tight font-bold">PER-CORNER<br>HEATMAP</div>
            <div class="flex flex-col gap-1">
              <div class="flex justify-between text-small text-slate mb-1">
                <span>#1</span><span>#47</span>
              </div>
              <div v-for="c in cornerPBs" :key="c.id" class="flex gap-1 items-center">
                <span class="w-4 text-silver">{{ c.id }}</span>
                <div class="flex flex-grow gap-[1px]">
                  <div v-for="(g, i) in c.grades" :key="i" class="h-3 flex-grow" :class="getHeatmapColor(g)"></div>
                </div>
              </div>
            </div>
            <div class="absolute bottom-2 text-center w-full text-small text-slate pr-4">
              better → █
            </div>
          </CyberPanel>
        </div>
      </template>
      <template v-else>
        <!-- Placeholder for less than 5 sessions -->
      </template>
      
      <template #floating>
        <CoachFloat
          emotion="victory"
          text="T11's where you found half a second overall. Don't lose it."
        />
      </template>
  </PageShell>
</template>
