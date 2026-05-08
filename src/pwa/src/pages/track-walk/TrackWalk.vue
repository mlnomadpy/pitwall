<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import { useRouter } from 'vue-router'
import { useSaveStore } from '@/entities/save/model/saveStore'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import { useSessionStore } from '@/entities/session/model/sessionStore'
import { bridge } from '@/shared/api/bridge'
import PageShell from '@/shared/ui/PageShell.vue'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'
import CoachFloat from '@/shared/ui/CoachFloat.vue'
import TrackMap from '@/shared/ui/core/TrackMap.vue'
import CornerScorecard from '@/shared/ui/core/CornerScorecard.vue'

import { useNotificationsStore } from '@/shared/api/notificationStore'

const router = useRouter()
const save = useSaveStore()
const audio = useAudioStore()
const sessionStore = useSessionStore()
const notifications = useNotificationsStore()

const state = ref<'idle' | 'corner-detail'>('idle')

interface Corner {
  id: string
  progress: number
  cx?: number
  cy?: number
  name: string
  tip: string
  grade: string
  entry?: number
  apex?: number
  exit?: number
  time?: number
  deltas: { entry: number, apex: number, exit: number, time: number }
  svgTurnId?: number
}

const corners = ref<Corner[]>([
  { id: 'T1', progress: 8, name: 'T1', tip: "Keep it pinned, eyes up the hill.", grade: '--', deltas: { entry: +2, apex: +1, exit: 0, time: -0.1 } },
  { id: 'T2', progress: 14, name: 'T2', tip: "Brake at the bridge, late apex.", grade: '--', deltas: { entry: -5, apex: -10, exit: -6, time: +0.8 } },
  { id: 'T3', progress: 18, name: 'T3', tip: "Crest the hill, don't lift.", grade: '--', deltas: { entry: -1, apex: -2, exit: 0, time: +0.2 } },
  { id: 'T4', progress: 24, name: 'T4', tip: "Downhill braking, tricky weight transfer.", grade: '--', deltas: { entry: +4, apex: -4, exit: -3, time: +0.4 } },
  { id: 'T5', progress: 30, name: 'T5', tip: "Don't rush the throttle.", grade: '--', deltas: { entry: 0, apex: 0, exit: -2, time: +0.1 } },
  { id: 'T6', progress: 45, name: 'The Carousel', tip: "Long constant radius, balance the car.", grade: '--', deltas: { entry: -2, apex: -2, exit: -2, time: +0.3 } },
  { id: 'T7', progress: 55, name: 'T7', tip: "Eyes up — late turn-in, late apex; second apex matters more.", grade: '--', deltas: { entry: -6, apex: -7, exit: -4, time: +0.6 } },
  { id: 'T8', progress: 65, name: 'T8', tip: "Esses begin here, rhythm is everything.", grade: '--', deltas: { entry: +2, apex: +2, exit: +3, time: -0.2 } },
  { id: 'T9', progress: 70, name: 'T9', tip: "Don't hit the inside kerb too hard.", grade: '--', deltas: { entry: +1, apex: +1, exit: +1, time: -0.1 } },
  { id: 'T10', progress: 80, name: 'T10', tip: "Fast left sweep.", grade: '--', deltas: { entry: -1, apex: 0, exit: 0, time: 0 } },
  { id: 'T11', progress: 90, name: 'T11', tip: "Heavy brake zone, get it stopped.", grade: '--', deltas: { entry: +15, apex: -15, exit: -10, time: +1.5 } },
])

const trackMapRef = ref<any>(null)

onMounted(async () => {
  // Try to enrich corner grades from real scorecard data
  const sid = sessionStore.activeSessionId ?? sessionStore.sessions.find(s => s.lap_count > 0)?.session_id
  if (sid) {
    try {
      const res = await bridge.get<{ session_id: string; scorecard: any }>(`/session/${sid}/scorecard`)
      const sc = res.scorecard
      if (sc?.corners) {
        // Build map of scorecard corners
        const cardMap = new Map<string, any>()
        for (const c of sc.corners) {
          cardMap.set(c.corner ?? c.name ?? '', c)
        }
        
        for (const match of corners.value) {
          const sc_corner = cardMap.get(match.name) ?? cardMap.get(`Turn ${match.id.replace('T', '')}`)
          if (sc_corner) {
            match.grade = sc_corner.grade ?? match.grade
            match.entry = sc_corner.entry_speed_kmh ?? sc_corner.avg_entry_kmh
            match.apex = sc_corner.apex_speed_kmh ?? sc_corner.min_speed_kmh
            match.exit = sc_corner.exit_speed_kmh ?? sc_corner.avg_exit_kmh
            match.time = sc_corner.time_s
            
            // Adjust deltas if delta_s is present
            if (sc_corner.delta_s != null) {
               match.deltas.time = sc_corner.delta_s
            }
          }
        }
      }
    } catch {
      // Backend offline — keep static grades
    }
  }

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
  if (grade.startsWith('A') || grade.startsWith('B')) return 'text-ui-good drop-shadow-[1px_1px_0_#000]'
  if (grade.startsWith('C')) return 'text-ui-warn'
  if (grade.startsWith('F')) return 'text-ui-bad font-bold drop-shadow-[1px_1px_0_#000]'
  return 'text-silver'
}

useKeyboard((e: KeyboardEvent) => {
  if (state.value === 'corner-detail') {
    if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
      audio.playSfx('cancel')
      state.value = 'idle'
    } else if (e.key === 'a' || e.key === 'Enter') {
      audio.playSfx('goal_complete')
      notifications.add({
        kind: 'track-unlock',
        title: `GOAL ADDED: ${selectedCorner.value.name}`,
        subText: 'Focused added to next session',
        timestamp: new Date().toISOString()
      })
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
    <Transition name="fade">
      <div v-if="state === 'corner-detail'" class="absolute inset-0 bg-ink/80 z-20 backdrop-blur-sm" @click="state = 'idle'"></div>
    </Transition>
    <Transition name="slide-up">
      <CornerScorecard 
        v-if="state === 'corner-detail'" 
        :corner="selectedCorner" 
        :coach-id="save.activeSlot?.preferredCoach ?? 'TROD'"
      />
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

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
