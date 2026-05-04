<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import { useRouter } from 'vue-router'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import { useSaveStore } from '@/entities/save/model/saveStore'
import { useSequence } from '@/shared/lib/useSequence'
import PageShell from '@/shared/ui/PageShell.vue'
import CoachFloat from '@/shared/ui/CoachFloat.vue'
import CyberMetricRow from '@/shared/ui/core/CyberMetricRow.vue'

const router = useRouter()
const audio = useAudioStore()
const save = useSaveStore()

const displayedScore = ref(0)
const finalScore = 8420

const { phase, addStep, addCustomInterval, skip } = useSequence(99)

// Mock data
const metrics = [
  { label: 'BEST LAP', val: '1:46.8', sub: '-0.4s PB', subClass: 'text-ui-good' },
  { label: 'IDEAL LAP', val: '1:46.4', sub: '0.4s gain', subClass: 'text-silver' },
  { label: 'CONSISTENCY', val: '★★★★☆', sub: 'σ=0.6s', subClass: 'text-silver' },
  { label: 'TRAIL BRAKE %', val: '78%', sub: '+5 vs last', subClass: 'text-ui-good' },
  { label: 'COAST TIME', val: '8%', sub: '↓4 GOOD', subClass: 'text-ui-good' }
]

const goals = [
  { desc: 'APEX SPEED T7', res: '82 → 86 km/h', ok: true },
  { desc: 'BREAK 1:48', res: 'got 1:46.8', ok: true },
  { desc: 'TRAIL BRAKE EVERY', res: '4 of 11 entries', ok: false }
]

const medals = ['Sub-1:47', 'Trail Brake Apprentice']

useKeyboard((e: KeyboardEvent) => {
  if (e.key === 'a' || e.key === 'Enter') {
    if (phase.value < 13) {
      skip(() => { displayedScore.value = finalScore })
    } else {
      audio.playSfx('cursor_select')
      router.push('/garage')
    }
  } else if (e.key === 'b' || e.key === 'Escape' || e.key === 'Backspace') {
    if (phase.value < 13) {
      skip(() => { displayedScore.value = finalScore })
    } else {
      audio.playSfx('cancel')
      router.push('/garage')
    }
  }
})

onMounted(() => {
  audio.playMusic('garage_loop') 

  addStep({ phase: 1, timeMs: 200 })
  addStep({ phase: 2, timeMs: 600 })
  
  // count score
  addStep({
    phase: 2, 
    timeMs: 800,
    callback: () => {
      let current = 0
      const steps = 24
      const stepAmt = finalScore / steps
      let stepCount = 0
      
      addCustomInterval(1200 / steps, () => {
        current += stepAmt
        displayedScore.value = Math.min(Math.round(current), finalScore)
        audio.playSfx('cursor_move') 
        stepCount++
        if (stepCount >= steps) {
          displayedScore.value = finalScore
          audio.playSfx('level_up')
          return true // clear interval
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
    bg="neutral" 
    :show-heading="false"
    :hide-status="true"
  >
    <!-- Background overrides -->
    <div class="stage-bg absolute inset-0 z-0 pointer-events-none"></div>
    
    <div class="content h-full flex flex-col relative z-10 w-full">
      
      <!-- Banner -->
      <div 
        class="text-center transition-transform duration-200 mt-[1vh]"
        :class="phase >= 1 ? 'translate-y-0' : '-translate-y-16'"
      >
        <span class="banner-tag">
          STAGE CLEAR !
        </span>
      </div>
      
      <!-- Score -->
      <div v-if="phase >= 2 || phase === 99" class="text-center mt-[3vh] mb-[2vh]">
        <div class="text-small text-slate tracking-[0.2em] mb-[0.5vh]">TOTAL SCORE</div>
        <div class="text-title-lg text-ui-warn tracking-widest font-title">
          {{ displayedScore }}
        </div>
      </div>
      
      <!-- Metrics -->
      <div class="metrics-block">
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
      </div>
      
      <!-- Goals -->
      <div v-if="phase >= 8 || phase === 99" class="goals-block">
        <div class="section-label">GOALS</div>
        <div v-for="g in goals" :key="g.desc" class="goal-row">
          <span class="goal-icon" :class="g.ok ? 'text-ui-good ' : 'text-ui-bad '">{{ g.ok ? '✓' : '✗' }}</span>
          <span class="goal-desc" :class="g.ok ? '' : 'text-slate'">{{ g.desc }}</span>
          <span class="goal-res">{{ g.res }}</span>
        </div>
      </div>
      
      <!-- Medals -->
      <div v-if="(phase >= 9 || phase === 99) && medals.length > 0" class="medals-block">
        <div class="section-label text-ui-warn">★ NEW MEDALS</div>
        <div class="medals-row">
          <div v-for="m in medals" :key="m" class="medal-badge">
            <span class="text-ui-warn mr-1">★</span>{{ m }}
          </div>
        </div>
      </div>
      
    </div>
    
    <template #floating>
      <!-- Coach -->
      <div 
        class="absolute bottom-[6vh] left-[2vw] right-[2vw] transition-transform duration-200 pointer-events-none"
        :class="(phase >= 10 || phase === 99) ? 'translate-y-0' : 'translate-y-32'"
      >
        <CoachFloat 
          v-if="phase >= 11 || phase === 99"
          :coach-id="save.activeSlot?.preferredCoach ?? 'trod'"
          emotion="victory"
          text="Now THAT was distance."
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
  font-size: clamp(12px, 3vmin, 24px);
  padding: clamp(3px, 0.6vh, 8px) clamp(12px, 4vw, 32px);
  letter-spacing: 0.1em;
  box-shadow:
    0 0 12px rgba(42, 161, 152, 0.4),
    inset 0 -1px 0 rgba(0,0,0,0.2);
}

.metrics-block {
  display: flex;
  flex-direction: column;
  gap: clamp(2px, 0.6vh, 8px);
  padding: 0 clamp(8px, 4vw, 32px);
}

.goals-block {
  margin-top: clamp(6px, 1.5vh, 16px);
  padding: 0 clamp(4px, 1vw, 12px);
}

.section-label {
  font-size: clamp(9px, 2vmin, 16px);
  color: var(--color-silver);
  border-bottom: 1px solid var(--color-slate);
  padding-bottom: clamp(2px, 0.4vh, 5px);
  margin-bottom: clamp(3px, 0.6vh, 8px);
  letter-spacing: 0.1em;
}

.goal-row {
  display: flex;
  align-items: center;
  gap: clamp(4px, 1vw, 10px);
  font-size: clamp(10px, 2.2vmin, 18px);
  margin-bottom: clamp(1px, 0.3vh, 4px);
}

.goal-icon { flex: 0 0 auto; }
.goal-desc { flex: 1; }
.goal-res { flex: 0 0 auto; text-align: right; color: var(--color-silver); }

.medals-block {
  margin-top: clamp(6px, 1.2vh, 14px);
  padding: 0 clamp(4px, 1vw, 12px);
}

.medals-row {
  display: flex;
  gap: clamp(4px, 1vw, 12px);
  flex-wrap: wrap;
}

.medal-badge {
  border: 1px solid var(--color-ui-warn);
  background: rgba(181, 137, 0, 0.08);
  padding: clamp(2px, 0.4vh, 6px) clamp(6px, 1.5vw, 14px);
  font-size: clamp(9px, 2vmin, 16px);
  color: var(--color-silver);
  box-shadow: 0 0 6px rgba(181, 137, 0, 0.15);
}
</style>

