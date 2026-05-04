<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import { useRouter } from 'vue-router'
import { useSaveStore } from '@/entities/save/model/saveStore'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import PageShell from '@/shared/ui/PageShell.vue'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'
import CyberAvatar from '@/shared/ui/core/CyberAvatar.vue'
import CoachFloat from '@/shared/ui/CoachFloat.vue'
import TrackMap from '@/shared/ui/core/TrackMap.vue'

const router = useRouter()
const save = useSaveStore()
const audio = useAudioStore()

const state = ref<'idle' | 'corner-detail'>('idle')

interface Corner {
  id: string
  progress: number
  cx?: number
  cy?: number
  name: string
  tip: string
  grade: 'A' | 'B' | 'C' | 'D' | 'F'
  deltas: { entry: number, apex: number, exit: number, time: number }
  svgTurnId?: number
}

const corners = ref<Corner[]>([
  { id: 'T1', progress: 8, name: 'T1', tip: "Keep it pinned, eyes up the hill.", grade: 'A', deltas: { entry: +2, apex: +1, exit: 0, time: -0.1 } },
  { id: 'T2', progress: 14, name: 'T2', tip: "Brake at the bridge, late apex.", grade: 'D', deltas: { entry: -5, apex: -10, exit: -6, time: +0.8 } },
  { id: 'T3', progress: 18, name: 'T3', tip: "Crest the hill, don't lift.", grade: 'B', deltas: { entry: -1, apex: -2, exit: 0, time: +0.2 } },
  { id: 'T4', progress: 24, name: 'T4', tip: "Downhill braking, tricky weight transfer.", grade: 'C', deltas: { entry: +4, apex: -4, exit: -3, time: +0.4 } },
  { id: 'T5', progress: 30, name: 'T5', tip: "Don't rush the throttle.", grade: 'B', deltas: { entry: 0, apex: 0, exit: -2, time: +0.1 } },
  { id: 'T6', progress: 45, name: 'The Carousel', tip: "Long constant radius, balance the car.", grade: 'C', deltas: { entry: -2, apex: -2, exit: -2, time: +0.3 } },
  { id: 'T7', progress: 55, name: 'T7', tip: "Eyes up — late turn-in, late apex; second apex matters more.", grade: 'D', deltas: { entry: -6, apex: -7, exit: -4, time: +0.6 } },
  { id: 'T8', progress: 65, name: 'T8', tip: "Esses begin here, rhythm is everything.", grade: 'A', deltas: { entry: +2, apex: +2, exit: +3, time: -0.2 } },
  { id: 'T9', progress: 70, name: 'T9', tip: "Don't hit the inside kerb too hard.", grade: 'A', deltas: { entry: +1, apex: +1, exit: +1, time: -0.1 } },
  { id: 'T10', progress: 80, name: 'T10', tip: "Fast left sweep.", grade: 'B', deltas: { entry: -1, apex: 0, exit: 0, time: 0 } },
  { id: 'T11', progress: 90, name: 'T11', tip: "Heavy brake zone, get it stopped.", grade: 'F', deltas: { entry: +15, apex: -15, exit: -10, time: +1.5 } },
])

const trackMapRef = ref<any>(null)

onMounted(() => {
  setTimeout(() => {
    if (trackMapRef.value && trackMapRef.value.trackTurns) {
      corners.value.forEach(c => {
        const pt = trackMapRef.value.getPointAtProgress(c.progress)
        
        let closest: any = null
        let minDist = Infinity
        trackMapRef.value.trackTurns.forEach((t: any) => {
          const dist = Math.hypot(t.cx - pt.x, t.cy - pt.y)
          if (dist < minDist) {
            minDist = dist
            closest = t
          }
        })
        
        if (closest) {
          c.svgTurnId = closest.id
        }
      })
    }
  }, 100)
})

const cursorIndex = ref(0)
const selectedCorner = computed(() => corners.value[cursorIndex.value])

const getGradeColor = (grade: string) => {
  if (grade === 'A' || grade === 'B') return 'text-ui-good'
  if (grade === 'C') return 'text-ui-warn'
  return 'text-[#ef4444]'
}



useKeyboard((e: KeyboardEvent) => {
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
    cursorIndex.value = (cursorIndex.value + 1) % corners.value.length
    audio.playSfx('cursor_move')
  } else if (e.key === 'ArrowLeft') {
    cursorIndex.value = (cursorIndex.value - 1 + corners.value.length) % corners.value.length
    audio.playSfx('cursor_move')
  } else if (e.key === 'Enter' || e.key === 'a') {
    audio.playSfx('cursor_select')
    state.value = 'corner-detail'
  } else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.back()
  }
})


</script>

<template>
  <PageShell title="TRACK WALK · SONOMA" :hints="state === 'idle' ? ['A · ENTER CORNER', 'B · BACK', '◀ ▶ NEXT'] : ['A · ADD AS GOAL', 'B · BACK']" bg="neutral">
    <template #heading>
      <div class="heading-block mb-[1.5vh] text-center">
        <h1 class="text-title font-title text-silver tracking-[0.2em]">TRACK WALK · SONOMA</h1>
      </div>
    </template>

    <!-- Map Area -->
    <div class="relative flex-grow mx-2 border border-slate bg-charcoal/20 overflow-hidden">
      
      <TrackMap ref="trackMapRef" 
                class="opacity-60 text-slate" 
                :activeTurnId="selectedCorner.svgTurnId"
                @turn-click="(id: number) => { const idx = corners.findIndex(c => c.svgTurnId === id); if (idx !== -1) { cursorIndex = idx; state = 'corner-detail'; } }"
      >
      </TrackMap>
      
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
                <CyberAvatar :sheet="save.activeSlot?.preferredCoach ?? 'trod'" size="sm" variant="ghost" class="shrink-0" />
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
    
    <template #floating>
      <!-- Bottom Coach Area (Idle) -->
      <CoachFloat 
        v-if="state === 'idle'"
        emotion="relaxed"
        text="Tap any corner to walk through it. Red means you're losing time."
      />
    </template>
  </PageShell>
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
