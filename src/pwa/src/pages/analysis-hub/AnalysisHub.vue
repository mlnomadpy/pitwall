<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useSaveStore } from '@/entities/save/model/saveStore'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import PageShell from '@/shared/ui/PageShell.vue'
import CyberTile from '@/shared/ui/core/CyberTile.vue'
import CyberSplitView from '@/shared/ui/core/CyberSplitView.vue'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'

const router = useRouter()
const save = useSaveStore()
const audio = useAudioStore()

const formatLap = (seconds: number) => {
  if (!seconds) return '--:--.---'
  const mins = Math.floor(seconds / 60)
  const secs = (seconds % 60).toFixed(3)
  return `${mins}:${secs.padStart(6, '0')}`
}

const lapCount = computed(() => save.activeSlot?.sessions?.reduce((acc, s) => acc + (s.laps?.length ?? 0), 0) ?? 0)
const sessionCount = computed(() => save.activeSlot?.sessions?.length ?? 0)
const bestLapTime = computed(() => save.activeSlot?.bestLapBySession?.['sonoma'] ?? 107.284) // fallback to static if none

/** Mirrors Android [AnalysisHubScreen] core + extended modules (route IDs aligned with [Routes.kt]). */
const tiles = computed(() => [
  { id: 'lap-times', title: 'LAP TIMES HALL', desc: `${lapCount.value} LAPS (SAVE)`, route: '/analysis/lap-times' },
  { id: 'corners', title: 'CORNER MASTERY', desc: 'SCORECARD A–F', route: '/analysis/corners' },
  { id: 'straights', title: 'STRAIGHTS & SPEED', desc: 'TOP SPEED / STRAIGHT', route: '/analysis/straights' },
  { id: 'track-map', title: 'TRACK MAP', desc: 'GPS · MARKERS', route: '/analysis/track' },
  { id: 'track-ref', title: 'TRACK REFERENCE', desc: 'MARKERS · WEATHER', route: '/analysis/track-reference' },
  { id: 'evolution', title: 'DRIVER EVOLUTION', desc: `${sessionCount.value} SESSIONS`, route: '/analysis/evolution' },
  { id: 'pedals', title: 'PEDAL PROFILE', desc: 'THROTTLE · BRAKE', route: '/analysis/pedals' },
  { id: 'ghosts', title: 'GHOST DATA', desc: 'PICK SESSION · OVERLAY', route: '/analysis/ghosts' },
  { id: 'replay', title: 'VCR REPLAY', desc: 'SIGNAL TIMELINE', route: '/analysis/replay' },
  { id: 'bundle', title: 'DEBRIEF BUNDLE', desc: 'POST COACH/DEBRIEF', route: '/analysis/bundle' },
  { id: 'insights', title: 'INSIGHTS', desc: 'COACHING GAPS', route: '/analysis/insights' },
  { id: 'session-score', title: 'SESSION GRADE', desc: 'POST /score · GEMINI', route: '/analysis/session-score' },
  { id: 'lap-dist', title: 'LAP DISTRIBUTION', desc: 'BOX PLOT', route: '/analysis/lap-distribution' },
  { id: 'sectors', title: 'SECTOR TIMES', desc: 'S1 · S2 · S3', route: '/analysis/sector-times' },
  { id: 'clips', title: 'SESSION CLIPS', desc: 'VIDEO CUTS', route: '/analysis/clips' },
  { id: 'brake-accel', title: 'BRAKE / EXIT ACCEL', desc: 'SCATTER', route: '/analysis/brake-acceleration' },
  { id: 'tbox', title: 'THROTTLE CORNER BOX', desc: 'PER CORNER', route: '/analysis/throttle-corner-box' },
  { id: 'corner-class', title: 'CORNER CLASSIFICATION', desc: 'SPEED BANDS', route: '/analysis/corner-classification' },
  { id: 'atlas', title: 'SESSION CORNERS', desc: 'AGGREGATES', route: '/analysis/atlas' },
  { id: 'compare', title: 'COMPARE PEDALS', desc: 'TWO SESSIONS', route: '/analysis/compare' },
  { id: 'coach-ask', title: 'ASK COACH', desc: 'Q&A', route: '/coach/ask' },
  { id: 'concepts', title: 'CONCEPTS', desc: 'BENTLEY CATALOG', route: '/coach/concepts' },
  { id: 'notifications', title: 'NOTIFICATIONS', desc: 'SSE INBOX', route: '/notifications' },
])

const cursorIndex = ref(0)
const hasSessions = ref((save.activeSlot?.sessions?.length ?? 0) > 0)

useKeyboard((e: KeyboardEvent) => {
  if (e.key === 'ArrowRight') {
    cursorIndex.value = (cursorIndex.value + 1) % tiles.value.length
    audio.playSfx('cursor_move')
  } else if (e.key === 'ArrowLeft') {
    cursorIndex.value = (cursorIndex.value - 1 + tiles.value.length) % tiles.value.length
    audio.playSfx('cursor_move')
  } else if (e.key === 'ArrowDown') {
    cursorIndex.value = (cursorIndex.value + 2) % tiles.value.length
    audio.playSfx('cursor_move')
  } else if (e.key === 'ArrowUp') {
    cursorIndex.value = (cursorIndex.value - 2 + tiles.value.length) % tiles.value.length
    audio.playSfx('cursor_move')
  } else if (e.key === 'Enter') {
    const route = tiles.value[cursorIndex.value]?.route
    if (route) {
      audio.playSfx('cursor_select')
      router.push(route as string)
    } else {
      audio.playSfx('cancel')
    }
  } else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.push('/garage')
  } else if (e.key === ' ' || e.key === 'Shift') {
    audio.playSfx('cursor_select')
    router.push('/analysis/sql')
  }
})
</script>

<template>
  <PageShell title="ANALYSIS HALL" :hints="['A · ENTER', 'B · GARAGE', '◆ SQL CONSOLE']" bg="cool">
    
    <div class="flex-grow w-full flex flex-col h-full relative z-10">
      <CyberSplitView split="60-40" gap="md" class="h-full pb-[2vh]">
        
        <!-- Left Pane: Hero Section -->
        <template #left>
          <CyberPanel class="h-full bg-ink/80 border-slate flex flex-col relative overflow-hidden group p-0">
            <div class="absolute inset-0 bg-ui-info/5 pointer-events-none"></div>
            
            <div class="p-[3vmin] flex flex-col h-full z-10">
              <div class="text-silver tracking-[0.2em] mb-[1vh] border-b border-slate/50 pb-2">LATEST SESSION OVERVIEW</div>
              
              <div v-if="hasSessions" class="flex flex-col gap-[3vh] flex-grow justify-center">
                <div class="flex flex-col">
                  <span class="text-slate text-small tracking-widest">BEST LAP</span>
                  <span class="text-title font-nums text-ui-good pixel-shadow-2">{{ formatLap(bestLapTime) }}</span>
                </div>
                
                <div class="flex gap-[4vw]">
                  <div class="flex flex-col">
                    <span class="text-slate text-small tracking-widest">TOTAL LAPS</span>
                    <span class="text-title-sm text-white">{{ lapCount }}</span>
                  </div>
                  <div class="flex flex-col">
                    <span class="text-slate text-small tracking-widest">TRACK</span>
                    <span class="text-title-sm text-white uppercase">{{ save.activeSlot?.preferredTrack ?? 'SONOMA' }}</span>
                  </div>
                </div>
                
                <div class="flex flex-col mt-auto pt-[2vh] border-t border-slate/30">
                  <span class="text-slate text-small tracking-widest mb-1">COACH FEEDBACK</span>
                  <p class="text-body text-silver italic">"Braking early into Turn 7 cost you 0.4s. Carry the speed."</p>
                </div>
              </div>
              
              <div v-else class="flex items-center justify-center h-full">
                <span class="text-ui-warn text-body animate-pulse">NO DATA RECORDED</span>
              </div>
            </div>
            
            <!-- Decorative background element -->
            <div class="absolute -bottom-10 -right-10 text-[150px] text-slate/5 font-bold leading-none select-none">
              #01
            </div>
          </CyberPanel>
        </template>
        
        <!-- Right Pane: Analysis Modules -->
        <template #right>
          <div class="h-full flex flex-col">
            <div class="text-silver tracking-[0.2em] mb-[1vh] px-2">MODULES</div>
            <div class="grid grid-cols-2 gap-[1.5vmin] flex-grow content-start overflow-y-auto no-scrollbar pb-2 px-1">
              <CyberTile
                v-for="(tile, i) in tiles" 
                :key="tile.id"
                :title="tile.title"
                :subText="tile.desc"
                :focused="cursorIndex === i"
                :showKerb="true"
                variant="ink"
                @click="() => { cursorIndex = i; $router.push(tile.route as string); }"
                @hover="cursorIndex = i"
              />
            </div>
          </div>
        </template>
        
      </CyberSplitView>
    </div>
  </PageShell>
</template>
