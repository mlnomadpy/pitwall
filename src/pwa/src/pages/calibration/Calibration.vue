<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import { useSaveStore } from '@/entities/save/model/saveStore'
import StatusBar from '@/widgets/status-bar/StatusBar.vue'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'
import HintBar from '@/widgets/hint-bar/HintBar.vue'
import DialogueBox from '@/widgets/dialogue-box/DialogueBox.vue'
import CyberProgress from '@/shared/ui/core/CyberProgress.vue'

const router = useRouter()
const audio = useAudioStore()
const save = useSaveStore()

const state = ref<'intro' | 'recording' | 'evaluating' | 'success' | 'failure'>('intro')
const remainingS = ref(10)

const bars = ref({
  gps: 0,
  imu: 0,
  rpm: 0,
  pedals: 0
})

const status = ref({
  gps: 'pending',
  imu: 'pending',
  rpm: 'pending',
  pedals: 'pending'
})

const hints = computed(() => {
  if (state.value === 'success') return ['A · DONE']
  if (state.value === 'failure') return ['A · RETRY', 'B · CANCEL']
  return ['B · CANCEL']
})

const phaseEmotion = computed(() => {
  if (state.value === 'success') return 'encouraging'
  if (state.value === 'failure') return 'concerned'
  return 'analyzing'
})

const phaseText = computed(() => {
  if (state.value === 'intro') return 'Hold tight. Checking the basics.'
  if (state.value === 'recording') return 'Acquiring stationary baseline...'
  if (state.value === 'evaluating') return 'Evaluating sensor consensus...'
  if (state.value === 'success') return 'Sensors zeroed and locked. You are clear to pull out.'
  
  // Failure text based on which failed
  if (status.value.imu === 'fail') return 'IMU noise floor too high. Keep the car perfectly still.'
  if (status.value.gps === 'fail') return 'GPS drift detected. Move to open sky.'
  return 'Sensor drift detected. Calibration failed.'
})

let timer: number | null = null
let barTimer: number | null = null

const startCalibration = () => {
  state.value = 'recording'
  remainingS.value = 10
  
  bars.value = { gps: 0, imu: 0, rpm: 0, pedals: 0 }
  status.value = { gps: 'pending', imu: 'pending', rpm: 'pending', pedals: 'pending' }
  
  audio.playSfx('cursor_move')

  timer = window.setInterval(() => {
    remainingS.value--
    if (remainingS.value <= 0) {
      if (timer) window.clearInterval(timer)
      evaluate()
    }
  }, 1000)
  
  barTimer = window.setInterval(() => {
    if (state.value === 'recording') {
      const target = ((10 - remainingS.value) / 10) * 100
      bars.value.gps = Math.min(100, bars.value.gps + (Math.random() * 5))
      bars.value.imu = Math.min(100, bars.value.imu + (Math.random() * 5))
      bars.value.rpm = Math.min(100, bars.value.rpm + (Math.random() * 5))
      bars.value.pedals = Math.min(100, bars.value.pedals + (Math.random() * 5))
      
      // Cap at target
      bars.value.gps = Math.min(bars.value.gps, target)
      bars.value.imu = Math.min(bars.value.imu, target)
      bars.value.rpm = Math.min(bars.value.rpm, target)
      bars.value.pedals = Math.min(bars.value.pedals, target)
    }
  }, 100)
}

const evaluate = () => {
  if (barTimer) window.clearInterval(barTimer)
  
  state.value = 'evaluating'
  bars.value = { gps: 100, imu: 100, rpm: 100, pedals: 100 }
  
  setTimeout(() => {
    // Randomize failure 20% of time for testing
    const fails = Math.random() < 0.2
    
    if (fails) {
      status.value = {
        gps: Math.random() < 0.5 ? 'fail' : 'pass',
        imu: Math.random() < 0.5 ? 'fail' : 'pass',
        rpm: 'pass',
        pedals: 'pass'
      }
      // Ensure at least one fails if fails is true
      if (status.value.gps === 'pass' && status.value.imu === 'pass') {
        status.value.imu = 'fail'
      }
      state.value = 'failure'
      audio.playSfx('error_quiet')
    } else {
      status.value = { gps: 'pass', imu: 'pass', rpm: 'pass', pedals: 'pass' }
      state.value = 'success'
      audio.playSfx('level_up')
    }
  }, 1000)
}

const handleKey = (e: KeyboardEvent) => {
  if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    if (state.value === 'success') return // forced to press A
    audio.playSfx('cancel')
    router.push('/garage')
  } else if (e.key === 'Enter' || e.key === 'a') {
    if (state.value === 'success') {
      audio.playSfx('cursor_select')
      router.push('/garage')
    } else if (state.value === 'failure') {
      audio.playSfx('cursor_select')
      startCalibration()
    }
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKey)
  
  // Intro delay
  setTimeout(() => {
    startCalibration()
  }, 2000)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKey)
  if (timer) window.clearInterval(timer)
  if (barTimer) window.clearInterval(barTimer)
})
</script>

<template>
  <div class="viewport pixelated relative w-full h-full bg-ink text-silver overflow-hidden  font-ui">
    <StatusBar />
    
    <div class="page-bg"></div>
    
    <div class="content pt-[6vh] px-4 flex flex-col h-full z-0 relative gap-2">
      <div class="heading-block mb-[1.5vh]">
        <h1 class="text-title font-title text-silver tracking-[0.2em]">CALIBRATION</h1>
        <span class="text-body text-ui-info font-bold">{{ remainingS }} / 10 s</span>
      </div>

      <!-- Center Stage -->
      <div class="flex flex-col items-center justify-center py-2 relative">
        <CyberPanel class="w-[clamp(80px,20vw,160px)] h-[clamp(48px,10vmin,80px)] flex flex-col items-center justify-center text-body border-ui-info bg-[#1A252C] relative">
          <div class="text-white font-bold opacity-50">▓▓ CAR ▓▓</div>
          <div class="text-slate">stationary</div>
          <div v-if="state === 'success'" class="absolute inset-0 flex items-center justify-center bg-ui-good/20 border-2 border-ui-good z-10">
            <span class="text-ui-good font-bold text-body drop-shadow-[1px_1px_0_#000]">CALIBRATED</span>
          </div>
        </CyberPanel>
      </div>

      <!-- Progress Bars -->
      <div class="flex flex-col gap-2 mt-2 px-8">
        <!-- GPS -->
        <div class="flex flex-col gap-1">
          <div class="text-body text-silver font-bold">GPS LOCK</div>
          <div class="flex items-center gap-2">
            <CyberProgress :value="bars.gps" :max="100" variant="info" thickness="sm" class="flex-grow" />
            <span v-if="status.gps === 'pass'" class="text-ui-good text-body font-bold w-3 text-center">✓</span>
            <span v-else-if="status.gps === 'fail'" class="text-ui-warn text-body font-bold w-3 text-center">✗</span>
            <span v-else class="text-slate text-body w-3 text-center">…</span>
          </div>
        </div>
        
        <!-- IMU -->
        <div class="flex flex-col gap-1">
          <div class="text-body text-silver font-bold">IMU NOISE FLOOR</div>
          <div class="flex items-center gap-2">
            <CyberProgress :value="bars.imu" :max="100" variant="info" thickness="sm" class="flex-grow" />
            <span v-if="status.imu === 'pass'" class="text-ui-good text-body font-bold w-3 text-center">✓</span>
            <span v-else-if="status.imu === 'fail'" class="text-ui-warn text-body font-bold w-3 text-center">✗</span>
            <span v-else class="text-slate text-body w-3 text-center">…</span>
          </div>
        </div>
        
        <!-- RPM -->
        <div class="flex flex-col gap-1">
          <div class="text-body text-silver font-bold">RPM ZERO BASELINE</div>
          <div class="flex items-center gap-2">
            <CyberProgress :value="bars.rpm" :max="100" variant="info" thickness="sm" class="flex-grow" />
            <span v-if="status.rpm === 'pass'" class="text-ui-good text-body font-bold w-3 text-center">✓</span>
            <span v-else-if="status.rpm === 'fail'" class="text-ui-warn text-body font-bold w-3 text-center">✗</span>
            <span v-else class="text-slate text-body w-3 text-center">…</span>
          </div>
        </div>
        
        <!-- Pedals -->
        <div class="flex flex-col gap-1">
          <div class="text-body text-silver font-bold">BRAKE / THROTTLE ZERO</div>
          <div class="flex items-center gap-2">
            <CyberProgress :value="bars.pedals" :max="100" variant="info" thickness="sm" class="flex-grow" />
            <span v-if="status.pedals === 'pass'" class="text-ui-good text-body font-bold w-3 text-center">✓</span>
            <span v-else-if="status.pedals === 'fail'" class="text-ui-warn text-body font-bold w-3 text-center">✗</span>
            <span v-else class="text-slate text-body w-3 text-center">…</span>
          </div>
        </div>
      </div>
      
      <div class="absolute bottom-6 left-2 right-2">
        <DialogueBox 
          :coach-id="save.slots[save.activeSlotId?-1:0]?.preferredCoach ?? 'trod'"
          :emotion="phaseEmotion"
          :text="phaseText"
          class="scale-[0.85] origin-bottom-left w-[117%]"
          :key="phaseText"
        />
      </div>
      
    </div>
    
    <HintBar :hints="hints" />
  </div>
</template>

<style scoped>
/* No hardcoded viewport dimensions — fullscreen is enforced by global.css */
</style>
