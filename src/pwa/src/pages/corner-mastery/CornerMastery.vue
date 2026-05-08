<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import { useSessionStore } from '@/entities/session/model/sessionStore'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import { bridge } from '@/shared/api/bridge'
import PageShell from '@/shared/ui/PageShell.vue'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'
import CyberSplitView from '@/shared/ui/core/CyberSplitView.vue'
import TrackMap from '@/shared/ui/core/TrackMap.vue'

const router = useRouter()
const audio = useAudioStore()
const session = useSessionStore()

interface Corner {
  id: string
  progress: number
  svgTurnId: number | undefined
  name: string
  grade: string
  entry: number
  apex: number
  exit: number
  brake: number
  glat: number
  time: number
  delta: string
  class: string
  throttle: { min: number; q1: number; med: number; q3: number; max: number }
}

// Default corner data — grades and stats overwritten when real scorecard is available
const corners = ref<Corner[]>([
  { id: 'T1', progress: 8, svgTurnId: undefined, name: 'Turn 1', grade: '--', entry: 0, apex: 0, exit: 0, brake: 0, glat: 0, time: 0, delta: '--', class: 'med', throttle: { min: 10, q1: 30, med: 60, q3: 85, max: 100 } },
  { id: 'T2', progress: 14, svgTurnId: undefined, name: 'Turn 2', grade: '--', entry: 0, apex: 0, exit: 0, brake: 0, glat: 0, time: 0, delta: '--', class: 'med', throttle: { min: 0, q1: 15, med: 45, q3: 70, max: 90 } },
  { id: 'T3', progress: 18, svgTurnId: undefined, name: 'Turn 3', grade: '--', entry: 0, apex: 0, exit: 0, brake: 0, glat: 0, time: 0, delta: '--', class: 'med', throttle: { min: 20, q1: 40, med: 55, q3: 80, max: 100 } },
  { id: 'T3a', progress: 21, svgTurnId: undefined, name: 'Turn 3a', grade: '--', entry: 0, apex: 0, exit: 0, brake: 0, glat: 0, time: 0, delta: '--', class: 'med', throttle: { min: 40, q1: 50, med: 70, q3: 90, max: 100 } },
  { id: 'T4', progress: 24, svgTurnId: undefined, name: 'Turn 4', grade: '--', entry: 0, apex: 0, exit: 0, brake: 0, glat: 0, time: 0, delta: '--', class: 'med', throttle: { min: 0, q1: 10, med: 30, q3: 60, max: 100 } },
  { id: 'T5', progress: 30, svgTurnId: undefined, name: 'Turn 5', grade: '--', entry: 0, apex: 0, exit: 0, brake: 0, glat: 0, time: 0, delta: '--', class: 'med', throttle: { min: 0, q1: 20, med: 40, q3: 70, max: 95 } },
  { id: 'T6', progress: 45, svgTurnId: undefined, name: 'Carousel', grade: '--', entry: 0, apex: 0, exit: 0, brake: 0, glat: 0, time: 0, delta: '--', class: 'med', throttle: { min: 10, q1: 30, med: 50, q3: 80, max: 100 } },
  { id: 'T7', progress: 55, svgTurnId: undefined, name: 'Turn 7', grade: '--', entry: 0, apex: 0, exit: 0, brake: 0, glat: 0, time: 0, delta: '--', class: 'med', throttle: { min: 0, q1: 5, med: 20, q3: 50, max: 80 } },
  { id: 'T8', progress: 65, svgTurnId: undefined, name: 'Esses 1', grade: '--', entry: 0, apex: 0, exit: 0, brake: 0, glat: 0, time: 0, delta: '--', class: 'med', throttle: { min: 30, q1: 60, med: 80, q3: 95, max: 100 } },
  { id: 'T8a', progress: 68, svgTurnId: undefined, name: 'Esses 2', grade: '--', entry: 0, apex: 0, exit: 0, brake: 0, glat: 0, time: 0, delta: '--', class: 'med', throttle: { min: 40, q1: 70, med: 85, q3: 95, max: 100 } },
  { id: 'T9', progress: 70, svgTurnId: undefined, name: 'Turn 9', grade: '--', entry: 0, apex: 0, exit: 0, brake: 0, glat: 0, time: 0, delta: '--', class: 'med', throttle: { min: 20, q1: 45, med: 65, q3: 85, max: 100 } },
  { id: 'T10', progress: 80, svgTurnId: undefined, name: 'Turn 10', grade: '--', entry: 0, apex: 0, exit: 0, brake: 0, glat: 0, time: 0, delta: '--', class: 'med', throttle: { min: 0, q1: 15, med: 35, q3: 65, max: 95 } },
  { id: 'T11', progress: 90, svgTurnId: undefined, name: 'Hairpin', grade: '--', entry: 0, apex: 0, exit: 0, brake: 0, glat: 0, time: 0, delta: '--', class: 'med', throttle: { min: 0, q1: 0, med: 10, q3: 40, max: 100 } },
])

const activeSessionId = computed(() => {
  // Pick the most recent session with laps
  const withLaps = session.sessions.filter(s => s.lap_count > 0)
  return session.activeSessionId ?? withLaps[0]?.session_id ?? null
})

// Fetch real scorecard from backend and merge into corners
async function fetchScorecard() {
  const sid = activeSessionId.value
  if (!sid) return
  try {
    const data = await bridge.get<any>(`/session/${sid}/scorecard`)
    if (!data?.corners) return
    // Corner name → scorecard data map
    const cardMap = new Map<string, any>()
    for (const c of data.corners) {
      // Map scorecard corner names to our IDs (Turn 1 → T1, The Carousel → T6, etc.)
      const name: string = c.corner ?? c.name ?? ''
      cardMap.set(name, c)
    }

    corners.value.forEach(corner => {
      // Try matching by corner name
      const card = cardMap.get(corner.name) ?? cardMap.get(`Turn ${corner.id.replace('T', '')}`)
      if (card) {
        corner.grade = card.grade ?? corner.grade
        corner.entry = card.entry_speed_kmh ?? card.avg_entry_kmh ?? corner.entry
        corner.apex = card.apex_speed_kmh ?? card.min_speed_kmh ?? corner.apex
        corner.exit = card.exit_speed_kmh ?? card.avg_exit_kmh ?? corner.exit
        corner.brake = card.peak_brake_bar ?? corner.brake
        corner.glat = card.peak_g_lat ?? card.max_lateral_g ?? corner.glat
        corner.time = card.time_s ?? corner.time
        corner.delta = card.delta_s != null ? `${card.delta_s >= 0 ? '+' : ''}${card.delta_s.toFixed(1)}` : corner.delta
        corner.class = card.grade?.startsWith('A') ? 'high' : card.grade?.startsWith('F') ? 'low' : 'med'
      }
    })
  } catch {
    // Scorecard not available — keep defaults
  }
}

const cursorIndex = ref(0)
const cur = computed(() => corners.value[cursorIndex.value])

const trackMapRef = ref<any>(null)

onMounted(async () => {
  // Fetch sessions if not already loaded
  if (session.sessions.length === 0) await session.fetchSessions()

  // Fetch real corner grades
  await fetchScorecard()

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


const getGradeColor = (g: string) => {
  if (g.startsWith('A')) return 'text-ui-good font-bold drop-shadow-[1px_1px_0_#000]'
  if (g.startsWith('B') || g.startsWith('C') || g.startsWith('D')) return 'text-silver'
  if (g.startsWith('F')) return 'text-ui-warn font-bold drop-shadow-[1px_1px_0_#000]'
  return 'text-silver'
}

useKeyboard((e: KeyboardEvent) => {
  if (e.key === 'ArrowRight') {
    cursorIndex.value = Math.min(cursorIndex.value + 1, corners.value.length - 1)
    
    if (corners.value[cursorIndex.value].grade.startsWith('A')) {
      audio.playSfx('goal_complete')
    } else if (corners.value[cursorIndex.value].grade === 'F') {
      audio.playSfx('error_quiet')
    } else {
      audio.playSfx('cursor_move')
    }
  } else if (e.key === 'ArrowLeft') {
    cursorIndex.value = Math.max(cursorIndex.value - 1, 0)
    
    if (corners.value[cursorIndex.value].grade.startsWith('A')) {
      audio.playSfx('goal_complete')
    } else if (corners.value[cursorIndex.value].grade === 'F') {
      audio.playSfx('error_quiet')
    } else {
      audio.playSfx('cursor_move')
    }
  } else if (e.key === 'Enter' || e.key === 'a') {
    audio.playSfx('cursor_select')
  } else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.push('/garage/analysis')
  }
})
</script>

<template>
  <PageShell title="CORNER MASTERY" subtitle="session 2026-04-29-1503" :hints="['A · DRILL DOWN', '◀ ▶ MOVE', 'B · BACK']" bg="cool">
    
    <div class="grid grid-cols-[1.2fr_1fr] gap-[2vw] flex-grow min-h-0">
      
      <!-- Left: Map & Drill -->
      <div class="flex flex-col gap-[2vh]">
        <!-- Interactive Track Minimap -->
        <Frame variant="inset" padding="0" class="h-[35vh] flex items-center justify-center bg-[#1A252C] overflow-hidden relative border-slate/30">
          <TrackMap ref="trackMapRef" 
                    class="absolute inset-[-10%] w-[120%] h-[120%] opacity-80"
                    :activeTurnId="cur.svgTurnId"
                    @turn-click="(id: number) => { const idx = corners.findIndex(c => c.svgTurnId === id); if (idx !== -1) { cursorIndex = idx; audio.playSfx('cursor_select'); } }" />
          <div class="absolute top-2 left-2 text-small text-slate font-bold z-10 tracking-widest uppercase">Track Navigation</div>
        </Frame>
        
        <!-- Drill Panel -->
        <Frame variant="default" padding="16px" class="relative transition-colors duration-300 flex-grow"
               :class="cur.grade.startsWith('A') ? 'bg-ui-good/5 border-ui-good/50' : cur.grade === 'F' ? 'bg-ui-bad/5 border-ui-bad/50' : 'bg-ink/40'">
          <div class="flex justify-between items-end mb-4 border-b border-slate/30 pb-2">
            <span class="font-bold text-title-sm"><span class="text-ui-info mr-2">▶</span>{{ cur.id }}  {{ cur.name.toUpperCase() }}</span>
            <span class="text-[clamp(32px,8vmin,56px)] leading-none font-bold" :class="getGradeColor(cur.grade)">{{ cur.grade }}</span>
          </div>
          
          <div class="grid grid-cols-2 gap-x-8 gap-y-2 text-small tracking-wider">
            <div class="flex justify-between border-b border-slate/10 pb-1"><span>ENTRY</span> <span class="font-bold text-white">{{ cur.entry }} km/h</span></div>
            <div class="flex justify-between border-b border-slate/10 pb-1"><span>PEAK BRAKE</span> <span class="font-bold text-white">{{ cur.brake }} bar</span></div>
            <div class="flex justify-between border-b border-slate/10 pb-1"><span>APEX</span> <span class="font-bold text-white">{{ cur.apex }} km/h</span></div>
            <div class="flex justify-between border-b border-slate/10 pb-1"><span>MAX gLAT</span> <span class="font-bold text-white">{{ cur.glat }}</span></div>
            <div class="flex justify-between border-b border-slate/10 pb-1"><span>EXIT</span> <span class="font-bold text-white">{{ cur.exit }} km/h</span></div>
            <div class="flex justify-between border-b border-slate/10 pb-1"><span>TIME</span> <span class="font-bold text-white">{{ cur.time }}s</span></div>
          </div>
          
          <div class="absolute bottom-4 right-4 text-small font-bold uppercase tracking-widest">
            Delta <span :class="cur.delta.startsWith('-') ? 'text-ui-good' : 'text-ui-bad'">{{ cur.delta }}s</span>
          </div>
        </Frame>
      </div>

      <!-- Right: Charts & Analysis -->
      <div class="flex flex-col gap-[2vh]">
        <Frame variant="default" padding="16px" class="h-[50%] flex flex-col overflow-hidden bg-ink/40">
          <div class="text-small text-slate tracking-widest uppercase mb-4 border-b border-slate/30 pb-1">Throttle Profile</div>
          <div class="flex flex-col gap-2 overflow-y-auto no-scrollbar flex-grow pr-2">
            <div v-for="c in corners" :key="c.id" 
                 class="flex items-center gap-4 transition-all py-1 px-2 rounded"
                 :class="cur.id === c.id ? 'bg-slate/20' : 'opacity-60'"
                 @click="() => { const idx = corners.findIndex(x => x.id === c.id); if (idx !== -1) { cursorIndex = idx; audio.playSfx('cursor_select'); } }"
            >
              <span class="w-8 text-small font-black text-center" :class="cur.id === c.id ? 'text-white' : 'text-slate'">{{ c.id }}</span>
              <div class="flex-grow h-3 relative border-b border-slate/30">
                <div class="absolute w-[1px] h-full bg-slate/50" :style="{ left: c.throttle.min + '%' }"></div>
                <div class="absolute w-[1px] h-full bg-slate/50" :style="{ left: c.throttle.max + '%' }"></div>
                <div class="absolute h-[1px] bg-slate/30 top-1/2 w-full" :style="{ left: c.throttle.min + '%', width: (c.throttle.max - c.throttle.min) + '%' }"></div>
                <div class="absolute h-full bg-charcoal-mid border border-slate/40" :style="{ left: c.throttle.q1 + '%', width: (c.throttle.q3 - c.throttle.q1) + '%' }"></div>
                <div class="absolute w-1 h-full bg-ui-warn" :style="{ left: c.throttle.med + '%' }"></div>
              </div>
            </div>
          </div>
        </Frame>

        <Frame variant="default" padding="16px" class="flex-grow flex flex-col bg-ink/40">
          <div class="text-small text-slate tracking-widest uppercase mb-4 border-b border-slate/30 pb-1">Brake Consistency</div>
          <div class="flex-grow flex items-center justify-center">
             <PixelChart 
                :data="[20, 45, 80, 85, 70, 30, 10, 0]" 
                color="var(--color-ui-bad)"
                :height="80"
                :width="300"
                :stroke-width="3"
             />
          </div>
          <div class="mt-4 text-small text-center italic text-slate/80">
            "Roll the brake to the apex, don't square-wave it."
          </div>
        </Frame>
      </div>

    </div>
  </PageShell>
</template>

