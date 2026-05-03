<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useSaveStore } from '@/entities/save/model/saveStore'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import StatusBar from '@/widgets/status-bar/StatusBar.vue'
import HintBar from '@/widgets/hint-bar/HintBar.vue'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'
import CyberBox from '@/shared/ui/core/CyberBox.vue'
import DialogueBox from '@/widgets/dialogue-box/DialogueBox.vue'

const router = useRouter()
const save = useSaveStore()
const audio = useAudioStore()

const state = ref<'idle' | 'corner-detail'>('idle')

interface Corner {
  id: string
  x: number
  y: number
  name: string
  tip: string
  grade: 'A' | 'B' | 'C' | 'D' | 'F'
  deltas: { entry: number, apex: number, exit: number, time: number }
}

const corners: Corner[] = [
  { id: 'T1', x: 25, y: 35, name: 'T1', tip: "Keep it pinned, eyes up the hill.", grade: 'A', deltas: { entry: +2, apex: +1, exit: 0, time: -0.1 } },
  { id: 'T2', x: 50, y: 25, name: 'T2', tip: "Brake at the bridge, late apex.", grade: 'D', deltas: { entry: -5, apex: -10, exit: -6, time: +0.8 } },
  { id: 'T3', x: 70, y: 30, name: 'T3', tip: "Crest the hill, don't lift.", grade: 'B', deltas: { entry: -1, apex: -2, exit: 0, time: +0.2 } },
  { id: 'T4', x: 75, y: 55, name: 'T4', tip: "Downhill braking, tricky weight transfer.", grade: 'C', deltas: { entry: +4, apex: -4, exit: -3, time: +0.4 } },
  { id: 'T5', x: 65, y: 70, name: 'T5', tip: "Don't rush the throttle.", grade: 'B', deltas: { entry: 0, apex: 0, exit: -2, time: +0.1 } },
  { id: 'T6', x: 80, y: 70, name: 'The Carousel', tip: "Long constant radius, balance the car.", grade: 'C', deltas: { entry: -2, apex: -2, exit: -2, time: +0.3 } },
  { id: 'T7', x: 55, y: 80, name: 'T7', tip: "Eyes up — late turn-in, late apex; second apex matters more.", grade: 'D', deltas: { entry: -6, apex: -7, exit: -4, time: +0.6 } },
  { id: 'T8', x: 45, y: 80, name: 'T8', tip: "Esses begin here, rhythm is everything.", grade: 'A', deltas: { entry: +2, apex: +2, exit: +3, time: -0.2 } },
  { id: 'T9', x: 40, y: 65, name: 'T9', tip: "Don't hit the inside kerb too hard.", grade: 'A', deltas: { entry: +1, apex: +1, exit: +1, time: -0.1 } },
  { id: 'T10', x: 35, y: 55, name: 'T10', tip: "Fast left sweep.", grade: 'B', deltas: { entry: -1, apex: 0, exit: 0, time: 0 } },
  { id: 'T11', x: 20, y: 45, name: 'T11', tip: "Heavy brake zone, get it stopped.", grade: 'F', deltas: { entry: +15, apex: -15, exit: -10, time: +1.5 } },
]

const cursorIndex = ref(0)
const selectedCorner = computed(() => corners[cursorIndex.value])

const getGradeColor = (grade: string) => {
  if (grade === 'A' || grade === 'B') return 'text-ui-good'
  if (grade === 'C') return 'text-ui-warn'
  return 'text-[#ef4444]'
}

const getGradeBg = (grade: string) => {
  if (grade === 'A' || grade === 'B') return 'bg-ui-good'
  if (grade === 'C') return 'bg-ui-warn'
  return 'bg-[#ef4444]'
}

const handleKey = (e: KeyboardEvent) => {
  if (state.value === 'corner-detail') {
    if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
      audio.playSfx('cancel')
      state.value = 'idle'
    } else if (e.key === 'a' || e.key === 'Enter') {
      audio.playSfx('goal_complete')
      // Goal added toast logic
      state.value = 'idle'
    }
    return
  }

  if (e.key === 'ArrowRight') {
    cursorIndex.value = (cursorIndex.value + 1) % corners.length
    audio.playSfx('cursor_move')
  } else if (e.key === 'ArrowLeft') {
    cursorIndex.value = (cursorIndex.value - 1 + corners.length) % corners.length
    audio.playSfx('cursor_move')
  } else if (e.key === 'Enter' || e.key === 'a') {
    audio.playSfx('cursor_select')
    state.value = 'corner-detail'
  } else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.back()
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKey)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKey)
})
</script>

<template>
  <div class="viewport pixelated relative w-full h-full bg-ink text-silver overflow-hidden  font-ui flex flex-col">
    <StatusBar />
    
    <div class="page-bg"></div>
    
    <div class="content pt-[6vh] px-2 flex-grow flex flex-col z-0 relative pb-[6vh]">
      <div class="heading-block mb-[1.5vh] text-center">
        <h1 class="text-title font-title text-silver tracking-[0.2em]">TRACK WALK · SONOMA</h1>
      </div>

      <!-- Map Area -->
      <div class="relative flex-grow mx-2 border border-slate bg-charcoal/20 overflow-hidden">
        <!-- SVG lines to connect corners -->
        <svg class="absolute inset-0 w-full h-full pointer-events-none opacity-30">
           <path d="M 25% 35% Q 50% 15% 50% 25% T 70% 30% T 75% 55% T 65% 70% T 80% 70% T 55% 80% T 45% 80% T 40% 65% T 35% 55% T 20% 45% Z" fill="none" stroke="currentColor" stroke-width="2" class="text-silver" />
        </svg>

        <!-- Pins -->
        <div 
          v-for="(c, i) in corners" 
          :key="c.id"
          class="absolute w-[clamp(12px,3vmin,24px)] h-[clamp(12px,3vmin,24px)] -ml-[clamp(6px,1.5vmin,12px)] -mt-[clamp(6px,1.5vmin,12px)] rounded-full border-2 flex items-center justify-center text-small font-bold shadow-lg transition-all"
          :class="[
            cursorIndex === i ? 'scale-150 z-20 shadow-[0_0_8px_#fff]' : 'scale-100 z-10 opacity-70',
            getGradeBg(c.grade),
            cursorIndex === i ? 'border-white text-ink' : 'border-ink text-ink'
          ]"
          :style="{ left: `${c.x}%`, top: `${c.y}%` }"
        >
          {{ c.id }}
        </div>
      </div>
      
      <!-- Bottom Coach Area (Idle) -->
      <DialogueBox 
        v-if="state === 'idle'"
        :coach-id="save.slots[save.activeSlotId?-1:0]?.preferredCoach ?? 'trod'"
        emotion="relaxed"
        text="Tap any corner to walk through it. Red means you're losing time."
        class="absolute bottom-[6vh] left-0 right-0 z-10 mx-2 mb-2"
      />
    </div>

    <!-- Corner Detail Modal -->
    <Transition name="slide-up">
      <div v-if="state === 'corner-detail'" class="absolute inset-x-2 bottom-[6vh] top-[6vh] z-30 flex flex-col">
        <CyberPanel class="flex-grow flex flex-col bg-ink shadow-2xl p-2 border-slate">
          <div class="flex justify-between border-b border-slate pb-1 mb-2">
            <span class="text-white font-bold text-body">{{ selectedCorner.id }} · "{{ selectedCorner.name }}"</span>
            <span class="text-body font-bold" :class="getGradeColor(selectedCorner.grade)">Grade: {{ selectedCorner.grade }}</span>
          </div>

          <div class="flex flex-col gap-2 flex-grow text-body">
            <CyberPanel class="bg-charcoal p-2 border-slate">
              <span class="text-slate">COACH SAYS</span>
              <div class="mt-1 flex gap-2">
                <CyberBox variant="charcoal" border="slate" class="w-[clamp(24px,6vmin,48px)] h-[clamp(24px,6vmin,48px)] overflow-hidden relative shrink-0">
                  <img :src="`/sprites/coaches/${save.slots[save.activeSlotId?-1:0]?.preferredCoach ?? 'trod'}.png`" class="w-full h-auto object-cover opacity-80 mix-blend-screen scale-[1.5] origin-top-left" style="image-rendering: pixelated; filter: grayscale(1) sepia(1) hue-rotate(180deg) saturate(3);" />
                </CyberBox>
                <div class="text-silver">
                  "{{ selectedCorner.tip }}"
                </div>
              </div>
            </CyberPanel>

            <div class="text-slate mb-1">YOUR BEST AT {{ selectedCorner.id }}</div>
            <CyberPanel class="flex flex-col gap-1 p-2 border-slate">
              <div class="flex justify-between">
                <span class="w-[clamp(24px,6vw,48px)] text-silver">ENTRY</span>
                <span class="font-bold">96 km/h</span>
                <span class="text-slate">── gold 102 km/h</span>
                <span class="w-[clamp(16px,4vw,32px)] text-right font-bold" :class="selectedCorner.deltas.entry < 0 ? 'text-[#ef4444]' : 'text-ui-good'">
                  {{ selectedCorner.deltas.entry < 0 ? '▼' : '▲' }}{{ Math.abs(selectedCorner.deltas.entry) }}
                </span>
              </div>
              <div class="flex justify-between">
                <span class="w-[clamp(24px,6vw,48px)] text-silver">APEX</span>
                <span class="font-bold">78 km/h</span>
                <span class="text-slate">── gold 85 km/h</span>
                <span class="w-[clamp(16px,4vw,32px)] text-right font-bold" :class="selectedCorner.deltas.apex < 0 ? 'text-[#ef4444]' : 'text-ui-good'">
                  {{ selectedCorner.deltas.apex < 0 ? '▼' : '▲' }}{{ Math.abs(selectedCorner.deltas.apex) }}
                </span>
              </div>
              <div class="flex justify-between">
                <span class="w-[clamp(24px,6vw,48px)] text-silver">EXIT</span>
                <span class="font-bold">94 km/h</span>
                <span class="text-slate">── gold 98 km/h</span>
                <span class="w-[clamp(16px,4vw,32px)] text-right font-bold" :class="selectedCorner.deltas.exit < 0 ? 'text-[#ef4444]' : 'text-ui-good'">
                  {{ selectedCorner.deltas.exit < 0 ? '▼' : '▲' }}{{ Math.abs(selectedCorner.deltas.exit) }}
                </span>
              </div>
              <div class="w-full h-[1px] bg-slate/50 my-1"></div>
              <div class="flex justify-between text-white">
                <span class="w-[clamp(24px,6vw,48px)]">TIME</span>
                <span class="font-bold">4.6 s</span>
                <span class="text-slate">── gold 4.0 s</span>
                <span class="w-[clamp(16px,4vw,32px)] text-right font-bold" :class="selectedCorner.deltas.time > 0 ? 'text-[#ef4444]' : 'text-ui-good'">
                  {{ selectedCorner.deltas.time > 0 ? '▼' : '▲' }}{{ Math.abs(selectedCorner.deltas.time) }}
                </span>
              </div>
            </CyberPanel>
          </div>

        </CyberPanel>
      </div>
    </Transition>
    
    <HintBar :hints="state === 'idle' ? ['A · ENTER CORNER', 'B · BACK', '◀ ▶ NEXT'] : ['A · ADD AS GOAL', 'B · BACK']" />
  </div>
</template>

<style scoped>
/* No hardcoded viewport dimensions — fullscreen is enforced by global.css */
.slide-up-enter-active,
.slide-up-leave-active {
  transition: transform 0.2s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}
.slide-up-enter-from,
.slide-up-leave-to {
  transform: translateY(100%);
}
</style>
