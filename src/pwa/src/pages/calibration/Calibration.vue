<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import { useRouter } from 'vue-router'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import { useSequence } from '@/shared/lib/useSequence'
import PageShell from '@/shared/ui/PageShell.vue'
import CoachFloat from '@/shared/ui/CoachFloat.vue'

const router = useRouter()
const audio = useAudioStore()
const { addDelay } = useSequence(0)

const state = ref<'intro' | 'throttle' | 'brake' | 'evaluating' | 'success'>('intro')

const bars = ref({
  throttle: 0,
  brake: 0,
})

const hints = computed(() => {
  if (state.value === 'success') return ['A · DONE']
  if (state.value === 'throttle') return ['▲ · HOLD THROTTLE']
  if (state.value === 'brake') return ['▼ · HOLD BRAKE']
  return ['B · CANCEL']
})

const phaseEmotion = computed(() => {
  if (state.value === 'success') return 'encouraging'
  if (state.value === 'throttle' || state.value === 'brake') return 'idle'
  return 'analyzing'
})

const phaseText = computed(() => {
  if (state.value === 'intro') return 'Establishing link to ECU.'
  if (state.value === 'throttle') return 'Hold UP ARROW to calibrate throttle at 100%.'
  if (state.value === 'brake') return 'Hold DOWN ARROW to calibrate brake pressure.'
  if (state.value === 'evaluating') return 'Evaluating sensor consensus...'
  return 'Sensors zeroed and locked. You are clear to pull out.'
})


const advance = () => {
  if (state.value === 'intro') {
    state.value = 'throttle'
    audio.playSfx('cursor_move')
  } else if (state.value === 'throttle') {
    state.value = 'brake'
    audio.playSfx('level_up')
  } else if (state.value === 'brake') {
    state.value = 'evaluating'
    audio.playSfx('level_up')
    setTimeout(() => {
      state.value = 'success'
      audio.playSfx('goal_complete')
    }, 2000)
  }
}

useKeyboard((e: KeyboardEvent) => {
  if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    if (state.value === 'success') return 
    audio.playSfx('cancel')
    router.push('/garage')
  } else if (e.key === 'Enter' || e.key === 'a') {
    if (state.value === 'success') {
      audio.playSfx('cursor_select')
      router.push('/garage')
    }
  }
})

// Listen to keydown and keyup for holding
const handleKeydown = (e: KeyboardEvent) => {
  if (e.key === 'ArrowUp' && state.value === 'throttle') {
    bars.value.throttle = Math.min(100, bars.value.throttle + 5)
    if (bars.value.throttle === 100) advance()
  }
  if (e.key === 'ArrowDown' && state.value === 'brake') {
    bars.value.brake = Math.min(100, bars.value.brake + 5)
    if (bars.value.brake === 100) advance()
  }
}

onMounted(() => {
  addDelay(1500, advance)
  window.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <PageShell title="CALIBRATION" :hints="hints" bg="neutral">
    <template #heading>
      <div class="heading-block mb-[1.5vh]">
        <h1 class="text-title font-title text-silver tracking-[0.2em]">CALIBRATION</h1>
        <div class="heading-rule"></div>
      </div>
    </template>

    <!-- Center Stage -->
    <div class="flex flex-col items-center justify-center py-2 relative flex-grow min-h-0">
      
      <div class="flex flex-col gap-8 w-full max-w-sm px-4">
        <div class="flex flex-col gap-2">
          <div class="flex justify-between font-mono text-small text-silver font-bold tracking-widest">
            <span>THROTTLE SENSOR</span>
            <span :class="bars.throttle === 100 ? 'text-ui-good' : ''">{{ bars.throttle }}%</span>
          </div>
          <div class="h-4 border border-charcoal bg-ink relative overflow-hidden">
            <div class="h-full bg-ui-good" :style="`width: ${bars.throttle}%`"></div>
            <div class="absolute inset-0 bg-[repeating-linear-gradient(45deg,transparent,transparent_4px,rgba(0,0,0,0.2)_4px,rgba(0,0,0,0.2)_8px)]"></div>
          </div>
        </div>

        <div class="flex flex-col gap-2">
          <div class="flex justify-between font-mono text-small text-silver font-bold tracking-widest">
            <span>BRAKE PRESSURE</span>
            <span :class="bars.brake === 100 ? 'text-ui-warn' : ''">{{ bars.brake }}%</span>
          </div>
          <div class="h-4 border border-charcoal bg-ink relative overflow-hidden">
            <div class="h-full bg-ui-warn" :style="`width: ${bars.brake}%`"></div>
            <div class="absolute inset-0 bg-[repeating-linear-gradient(45deg,transparent,transparent_4px,rgba(0,0,0,0.2)_4px,rgba(0,0,0,0.2)_8px)]"></div>
          </div>
        </div>
      </div>

    </div>
    
    <template #floating>
      <CoachFloat
        :emotion="phaseEmotion"
        :text="phaseText"
        :key="phaseText"
      />
    </template>
  </PageShell>
</template>

<style scoped>
/* No hardcoded viewport dimensions — fullscreen is enforced by global.css */
</style>
