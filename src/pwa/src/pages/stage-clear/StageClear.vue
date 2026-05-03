<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import { useSaveStore } from '@/entities/save/model/saveStore'
import DialogueBox from '@/widgets/dialogue-box/DialogueBox.vue'
import HintBar from '@/widgets/hint-bar/HintBar.vue'

const router = useRouter()
const audio = useAudioStore()
const save = useSaveStore()

const phase = ref(0)
const displayedScore = ref(0)
const finalScore = 8420

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

let sequenceTimeouts: number[] = []

const advancePhase = (p: number, timeMs: number, sfx?: string) => {
  const t = window.setTimeout(() => {
    phase.value = p
    if (sfx) audio.playSfx(sfx)
  }, timeMs)
  sequenceTimeouts.push(t)
}

const skipSequence = () => {
  sequenceTimeouts.forEach(clearTimeout)
  sequenceTimeouts = []
  phase.value = 99
  displayedScore.value = finalScore
}

const handleKey = (e: KeyboardEvent) => {
  if (e.key === 'a' || e.key === 'Enter') {
    if (phase.value < 13) {
      skipSequence()
    } else {
      audio.playSfx('cursor_select')
      router.push('/garage')
    }
  } else if (e.key === 'b' || e.key === 'Escape' || e.key === 'Backspace') {
    if (phase.value < 13) {
      skipSequence()
    } else {
      audio.playSfx('cancel')
      router.push('/garage')
    }
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKey)
  audio.playMusic('garage_loop') // Stub since score_fanfare isn't mapped

  // t=200: banner
  advancePhase(1, 200)
  // t=600: score block appears
  advancePhase(2, 600)
  
  // count score
  const tCount = window.setTimeout(() => {
    let current = 0
    const duration = 1200
    const steps = 24
    const stepTime = duration / steps
    const stepAmt = finalScore / steps
    
    let stepCount = 0
    const interval = window.setInterval(() => {
      current += stepAmt
      displayedScore.value = Math.min(Math.round(current), finalScore)
      audio.playSfx('cursor_move') 
      stepCount++
      if (stepCount >= steps) {
        clearInterval(interval)
        displayedScore.value = finalScore
        audio.playSfx('level_up') 
      }
    }, stepTime)
    sequenceTimeouts.push(interval)
  }, 800)
  sequenceTimeouts.push(tCount)

  // t=2200: metric row 1
  advancePhase(3, 2200)
  // t=2300: metric row 2
  advancePhase(4, 2300)
  // t=2400: metric row 3
  advancePhase(5, 2400)
  // t=2500: metric row 4
  advancePhase(6, 2500)
  // t=2600: metric row 5
  advancePhase(7, 2600)
  
  // t=2700: goals
  advancePhase(8, 2700, 'goal_complete')
  
  // t=3200: medals
  advancePhase(9, 3200, 'cursor_select')
  
  // t=4700: coach slide
  advancePhase(10, 4700)
  
  // t=4900: coach talks
  advancePhase(11, 4900)
  
  // t=7000: hint bar
  advancePhase(13, 7000)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKey)
  sequenceTimeouts.forEach(clearTimeout)
})
</script>

<template>
  <div class="viewport pixelated relative w-full h-full bg-ink text-silver overflow-hidden font-ui">
    
    <!-- Background -->
    <div class="stage-bg absolute inset-0 z-0"></div>
    
    <div class="content pt-[2vh] pb-[2vh] px-[3vw] h-full flex flex-col relative z-10">
      
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
        <div class="text-title-lg text-ui-warn tracking-widest  font-title">
          {{ displayedScore }}
        </div>
      </div>
      
      <!-- Metrics -->
      <div class="metrics-block">
        <div v-for="(m, i) in metrics" :key="m.label" 
             class="metric-row transition-opacity duration-200"
             :class="(phase >= 3 + i || phase === 99) ? 'opacity-100' : 'opacity-0'">
          <span class="metric-label">{{ m.label }}</span>
          <span class="metric-val">{{ m.val }}</span>
          <span class="metric-sub" :class="m.subClass">{{ m.sub }}</span>
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
      
      <!-- Coach -->
      <div 
        class="absolute bottom-[6vh] left-[2vw] right-[2vw] transition-transform duration-200"
        :class="(phase >= 10 || phase === 99) ? 'translate-y-0' : 'translate-y-32'"
      >
        <DialogueBox 
          v-if="phase >= 11 || phase === 99"
          :coach-id="save.slots[save.activeSlotId!-1]?.preferredCoach ?? 'trod'"
          emotion="victory"
          text="Now THAT was distance."
        />
      </div>

    </div>
    
    <HintBar v-if="phase >= 13 || phase === 99" :hints="['A · CONTINUE', 'B · HOME', '◆ SHARE']" />
  </div>
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

.metric-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: clamp(10px, 2.3vmin, 20px);
  padding: clamp(1px, 0.3vh, 4px) 0;
  border-bottom: 1px solid rgba(61, 68, 88, 0.2);
}

.metric-label { width: 35%; color: var(--color-silver); }
.metric-val { width: 25%; text-align: center; font-weight: bold; color: white; }
.metric-sub { width: 25%; text-align: right; }

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

