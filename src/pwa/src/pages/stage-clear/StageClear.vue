<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import { useRouter } from 'vue-router'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import { useSaveStore } from '@/entities/save/model/saveStore'
import { useSessionStore } from '@/entities/session/model/sessionStore'
import { useLapTimeStore } from '@/entities/lap-time/model/lapTimeStore'
import { useMedalStore } from '@/entities/quest/model/medalStore'
import { useSequence } from '@/shared/lib/useSequence'
import { bridge } from '@/shared/api/bridge'
import PageShell from '@/shared/ui/PageShell.vue'
import Frame from '@/shared/ui/core/Frame.vue'
import CoachFloat from '@/shared/ui/CoachFloat.vue'
import CyberMetricRow from '@/shared/ui/core/CyberMetricRow.vue'

const router = useRouter()
const audio = useAudioStore()
const save = useSaveStore()
const session = useSessionStore()
const lapTime = useLapTimeStore()
const medal = useMedalStore()

const displayedScore = ref(0)
const { phase, addStep, addCustomInterval, skip } = useSequence(99)

const bestLapFormatted = computed(() => {
  if (!lapTime.bestLapS) return '--:--.--'
  const mins = Math.floor(lapTime.bestLapS / 60)
  const secs = (lapTime.bestLapS % 60).toFixed(1)
  return `${mins}:${secs.padStart(4, '0')}`
})

const idealLapFormatted = computed(() => {
  if (lapTime.laps.length < 2) return '--:--.--'
  const sectorCount = lapTime.laps[0]?.sectors?.length ?? 0
  if (sectorCount === 0) return bestLapFormatted.value
  let idealTotal = 0
  for (let s = 0; s < sectorCount; s++) {
    const bestSector = Math.min(...lapTime.laps.map(l => l.sectors?.[s]?.time_s ?? Infinity))
    idealTotal += bestSector === Infinity ? 0 : bestSector
  }
  const mins = Math.floor(idealTotal / 60)
  const secs = (idealTotal % 60).toFixed(1)
  return `${mins}:${secs.padStart(4, '0')}`
})

const idealGain = computed(() => {
  if (!lapTime.bestLapS || lapTime.laps.length < 2) return '--'
  const sectorCount = lapTime.laps[0]?.sectors?.length ?? 0
  if (sectorCount === 0) return '--'
  let idealTotal = 0
  for (let s = 0; s < sectorCount; s++) {
    const bestSector = Math.min(...lapTime.laps.map(l => l.sectors?.[s]?.time_s ?? Infinity))
    idealTotal += bestSector === Infinity ? 0 : bestSector
  }
  const diff = lapTime.bestLapS - idealTotal
  return diff > 0 ? `${diff.toFixed(1)}s gain` : 'at ideal'
})

const consistencyInfo = computed(() => {
  const times = lapTime.laps.map(l => l.lap_time_s).filter(t => t > 0)
  if (times.length < 2) return { stars: '★☆☆☆☆', sigma: '?' }
  const mean = times.reduce((a, b) => a + b, 0) / times.length
  const variance = times.reduce((a, t) => a + (t - mean) ** 2, 0) / times.length
  const sigma = Math.sqrt(variance)
  const rating = sigma < 0.5 ? 5 : sigma < 1 ? 4 : sigma < 2 ? 3 : sigma < 4 ? 2 : 1
  const stars = '★'.repeat(rating) + '☆'.repeat(5 - rating)
  return { stars, sigma: `σ=${sigma.toFixed(1)}s` }
})

const pedalStats = ref<{ throttle_pct: number; brake_pct: number; coast_pct: number } | null>(null)

const metrics = computed(() => {
  const bestPb = session.detail?.best_lap_s
  const pbDelta = bestPb && lapTime.bestLapS
    ? `${(lapTime.bestLapS - bestPb) < 0 ? '' : '+'}${(lapTime.bestLapS - bestPb).toFixed(1)}s`
    : '--'
  const pbClass = bestPb && lapTime.bestLapS && lapTime.bestLapS <= bestPb
    ? 'text-ui-good' : 'text-silver'

  return [
    { label: 'BEST LAP', val: bestLapFormatted.value, sub: `${pbDelta} PB`, subClass: pbClass },
    { label: 'IDEAL LAP', val: idealLapFormatted.value, sub: idealGain.value, subClass: 'text-silver' },
    { label: 'CONSISTENCY', val: consistencyInfo.value.stars, sub: consistencyInfo.value.sigma, subClass: 'text-silver' },
    { label: 'THROTTLE', val: pedalStats.value ? `${pedalStats.value.throttle_pct.toFixed(0)}%` : '--%', sub: 'on-throttle', subClass: 'text-ui-good' },
    { label: 'COAST TIME', val: pedalStats.value ? `${pedalStats.value.coast_pct.toFixed(0)}%` : '--%', sub: pedalStats.value && pedalStats.value.coast_pct < 15 ? 'GOOD' : 'work on it', subClass: pedalStats.value && pedalStats.value.coast_pct < 15 ? 'text-ui-good' : 'text-ui-bad' },
  ]
})

const finalScore = computed(() => {
  const lapCount = lapTime.laps.length
  const times = lapTime.laps.map(l => l.lap_time_s).filter(t => t > 0)
  const mean = times.length ? times.reduce((a, b) => a + b, 0) / times.length : 200
  const sigma = times.length >= 2
    ? Math.sqrt(times.reduce((a, t) => a + (t - mean) ** 2, 0) / times.length)
    : 5
  const consistencyBonus = Math.max(0, 100 - sigma * 20)
  const throttleBonus = pedalStats.value ? pedalStats.value.throttle_pct * 0.3 : 20
  return Math.round(lapCount * 500 + consistencyBonus * 30 + throttleBonus * 20)
})

const grade = computed(() => {
  const s = finalScore.value
  if (s >= 8000) return 'S'
  if (s >= 6000) return 'A'
  if (s >= 4000) return 'B'
  if (s >= 2000) return 'C'
  return 'D'
})

const goals = computed(() => {
  const g: { desc: string; res: string; ok: boolean }[] = []
  const count = lapTime.laps.length
  g.push({ desc: `COMPLETE ${count >= 5 ? 5 : count + 1}+ LAPS`, res: `got ${count}`, ok: count >= 5 })
  const best = lapTime.bestLapS
  if (best) {
    const target = Math.ceil(best / 10) * 10 
    g.push({ desc: `BREAK ${Math.floor(target / 60)}:${String(target % 60).padStart(2, '0')}`, res: `got ${bestLapFormatted.value}`, ok: best < target })
  }
  if (pedalStats.value) {
    g.push({ desc: 'COAST UNDER 15%', res: `${pedalStats.value.coast_pct.toFixed(0)}%`, ok: pedalStats.value.coast_pct < 15 })
  }
  return g
})

const medals = computed(() => medal.earned.map(e => e.label))

useKeyboard((e: KeyboardEvent) => {
  if (e.key === 'a' || e.key === 'Enter') {
    if (phase.value < 13) {
      skip(() => { displayedScore.value = finalScore.value })
    } else {
      audio.playSfx('cursor_select')
      router.push('/garage')
    }
  } else if (e.key === 'b' || e.key === 'Escape' || e.key === 'Backspace') {
    if (phase.value < 13) {
      skip(() => { displayedScore.value = finalScore.value })
    } else {
      audio.playSfx('cancel')
      router.push('/garage')
    }
  }
})

onMounted(async () => {
  audio.playMusic('garage_loop') 
  const sid = session.activeSessionId ?? session.sessions[0]?.session_id
  if (sid) {
    await lapTime.fetchLapTimes(sid)
    try {
      const pedal = await bridge.get<any>(`/session/${sid}/pedal_behavior`)
      if (pedal?.states) {
        pedalStats.value = {
          throttle_pct: pedal.states.throttle_only?.pct ?? 0,
          brake_pct: pedal.states.brake_only?.pct ?? 0,
          coast_pct: pedal.states.coast?.pct ?? 0,
        }
      }
    } catch { }
  }

  addStep({ phase: 1, timeMs: 200 })
  addStep({ phase: 2, timeMs: 600 })
  addStep({
    phase: 2, 
    timeMs: 800,
    callback: () => {
      let current = 0
      const steps = 24
      const stepAmt = finalScore.value / steps
      let stepCount = 0
      addCustomInterval(1200 / steps, () => {
        current += stepAmt
        displayedScore.value = Math.min(Math.round(current), finalScore.value)
        audio.playSfx('score_tick') 
        stepCount++
        if (stepCount >= steps) {
          displayedScore.value = finalScore.value
          audio.playSfx('level_up')
          return true
        }
        return false
      })
    }
  })

  addStep({ phase: 3, timeMs: 2200 })
  addStep({ phase: 4, timeMs: 2300 })
  addStep({ phase: 5, timeMs: 2400 })
  addStep({ phase: 6, timeMs: 2500 })
  addStep({ phase: 7, timeMs: 2600 })
  addStep({ phase: 8, timeMs: 2700, sfx: 'goal_complete' })
  addStep({ phase: 9, timeMs: 3200, sfx: 'cursor_select' })
  addStep({ phase: 10, timeMs: 4700 })
  addStep({ phase: 11, timeMs: 4900 })
  addStep({ phase: 13, timeMs: 7000 })
})
</script>

<template>
  <PageShell 
    :hints="phase >= 13 || phase === 99 ? ['A · CONTINUE', 'B · HOME', '◆ SHARE'] : []" 
    bg="warm" bgVariant="stars" 
    :show-heading="false"
    :hide-status="true"
  >
    <div class="stage-bg absolute inset-0 z-0 pointer-events-none"></div>
    
    <div class="content h-full flex flex-col relative z-10 w-full" @click="phase < 13 ? skip(() => { displayedScore = finalScore }) : (audio.playSfx('cursor_select'), router.push('/garage'))">
      
      <div 
        class="text-center transition-transform duration-300 mt-[2vh] mb-[2vh]"
        :class="phase >= 1 ? 'translate-y-0' : '-translate-y-16'"
      >
        <span class="banner-tag uppercase tracking-[0.4em] font-title">
          Stage Clear !
        </span>
      </div>
      
      <div class="grid grid-cols-[1fr_auto_1fr] gap-[4vw] px-[4vw] items-center flex-grow min-h-0">
        <!-- Left: Metrics -->
        <div class="flex flex-col gap-[2vh]">
          <div class="text-small text-slate tracking-widest uppercase border-b border-slate/30 pb-1">Performance</div>
          <Frame variant="default" padding="12px" class="flex flex-col gap-[1vh]">
            <div v-for="(m, i) in metrics" :key="m.label" 
                 class="transition-opacity duration-200"
                 :class="(phase >= 3 + i || phase === 99) ? 'opacity-100' : 'opacity-0'">
              <CyberMetricRow 
                :label="m.label" 
                :value="m.val" 
                :sub-text="m.sub" 
                :sub-class="m.subClass" 
              />
            </div>
          </Frame>
        </div>

        <!-- Center: Score & Grade -->
        <div v-if="phase >= 2 || phase === 99" class="flex flex-col items-center justify-center min-w-[25vw] relative">
          <div class="text-small text-slate tracking-widest mb-2 uppercase">Total Score</div>
          <div class="text-[clamp(40px,10vmin,80px)] text-ui-warn tracking-widest font-title leading-none mb-4">
            {{ displayedScore }}
          </div>
          
          <div v-if="phase >= 8 || phase === 99" class="grade-badge animate-stamp">
            {{ grade }}
          </div>
        </div>

        <!-- Right: Goals & Medals -->
        <div class="flex flex-col gap-[4vh]">
           <div v-if="phase >= 8 || phase === 99" class="flex flex-col gap-2">
              <div class="text-small text-slate tracking-widest uppercase border-b border-slate/30 pb-1">Goals</div>
              <Frame variant="default" padding="12px" class="flex flex-col gap-2">
                <div v-for="g in goals" :key="g.desc" class="flex items-center gap-3 text-small">
                  <span class="font-bold" :class="g.ok ? 'text-ui-good' : 'text-ui-bad'">{{ g.ok ? '✓' : '✗' }}</span>
                  <span class="flex-grow tracking-wider" :class="g.ok ? 'text-white' : 'text-slate'">{{ g.desc }}</span>
                </div>
              </Frame>
           </div>

           <div v-if="(phase >= 9 || phase === 99) && medals.length > 0" class="flex flex-col gap-2">
              <div class="text-small text-ui-warn tracking-widest uppercase border-b border-ui-warn/30 pb-1">★ Medals</div>
              <div class="flex flex-wrap gap-2">
                <div v-for="m in medals" :key="m" class="bg-ui-warn/10 border border-ui-warn px-2 py-1 text-[10px] text-white tracking-widest">
                  {{ m.toUpperCase() }}
                </div>
              </div>
           </div>
        </div>
      </div>
      
    </div>

    <div v-if="phase >= 13 || phase === 99" class="absolute bottom-[10vh] left-0 right-0 text-center z-20 cursor-pointer" @click="audio.playSfx('cursor_select'); router.push('/garage')">
      <span class="text-ui-good font-bold text-title-sm tracking-[0.4em] animate-pulse border-2 border-ui-good px-10 py-3 bg-ink/90 shadow-[0_0_20px_rgba(42,161,152,0.4)]">
        Tap to Continue ▶
      </span>
    </div>
    
    <template #floating>
      <div 
        class="absolute bottom-[4vh] left-[4vw] right-[4vw] transition-transform duration-500 pointer-events-none"
        :class="(phase >= 10 || phase === 99) ? 'translate-y-0' : 'translate-y-48'"
      >
        <CoachFloat 
          v-if="phase >= 11 || phase === 99"
          :coach-id="save.activeSlot?.preferredCoach ?? 'trod'"
          emotion="victory"
          text="Outstanding performance. You're mastering the flow."
        />
      </div>
    </template>
  </PageShell>
</template>


<style scoped>
.stage-bg {
  background: linear-gradient(
    180deg,
    rgba(42, 161, 152, 0.06) 0%,
    var(--color-ink) 30%,
    var(--color-asphalt-deep) 100%
  );
}

.banner-tag {
  display: inline-block;
  background: var(--color-ui-good);
  color: var(--color-ink);
  font-weight: bold;
  font-size: clamp(16px, 4vmin, 32px);
  padding: 8px 48px;
  box-shadow:
    0 0 20px rgba(42, 161, 152, 0.4),
    inset 0 -2px 0 rgba(0,0,0,0.2);
}

.grade-badge {
  font-size: 64px;
  color: var(--color-ui-good);
  text-shadow: 2px 2px 0 #000, 0 0 15px var(--color-ui-good);
  border: 4px solid var(--color-ui-good);
  border-radius: 50%;
  width: 96px;
  height: 96px;
  display: flex;
  align-items: center;
  justify-content: center;
  transform: rotate(15deg);
  background: rgba(42, 161, 152, 0.1);
}

.animate-stamp {
  animation: stamp 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275) forwards;
}

@keyframes stamp {
  0% { transform: scale(4) rotate(0deg); opacity: 0; }
  100% { transform: scale(1) rotate(15deg); opacity: 1; }
}
</style>

