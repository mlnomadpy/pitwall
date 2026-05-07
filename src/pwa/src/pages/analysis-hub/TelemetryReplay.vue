<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import PageShell from '@/shared/ui/PageShell.vue'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'
import TrackMap from '@/shared/ui/core/TrackMap.vue'

const router = useRouter()
const audio = useAudioStore()

const isPlaying = ref(false)
const progress = ref(0) // 0 to 100
const speedMulti = ref(1) // 1x, 2x, 4x

const trackPath = ref<SVGPathElement | null>(null)
const carCx = ref(0)
const carCy = ref(0)

const currentSpeed = computed(() => Math.floor(Math.sin(progress.value / 10) * 80 + 120))
const currentGear = computed(() => Math.max(1, Math.ceil(currentSpeed.value / 40)))
const currentRpm = computed(() => Math.floor(((currentSpeed.value % 40) / 40) * 8000 + 1000))

let playTimer: number | null = null

const togglePlay = () => {
  isPlaying.value = !isPlaying.value
  audio.playSfx(isPlaying.value ? 'cursor_select' : 'cancel')
  if (isPlaying.value && progress.value >= 100) progress.value = 0
}

const updatePlayback = () => {
  if (isPlaying.value) {
    progress.value += 0.5 * speedMulti.value
    if (progress.value >= 100) {
      progress.value = 100
      isPlaying.value = false
    }
  }

  if (trackPath.value) {
    const totalLength = trackPath.value.getTotalLength()
    const point = trackPath.value.getPointAtLength((progress.value / 100) * totalLength)
    carCx.value = point.x
    carCy.value = point.y
  }
}

useKeyboard((e: KeyboardEvent) => {
  if (e.key === ' ') {
    togglePlay()
  } else if (e.key === 'ArrowRight') {
    if (speedMulti.value < 4) speedMulti.value *= 2
    audio.playSfx('cursor_move')
  } else if (e.key === 'ArrowLeft') {
    if (speedMulti.value > 1) speedMulti.value /= 2
    audio.playSfx('cursor_move')
  } else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.back()
  }
})

onMounted(() => {
  playTimer = window.setInterval(updatePlayback, 50)
})

onUnmounted(() => {
  if (playTimer) clearInterval(playTimer)
})
</script>

<template>
  <PageShell title="TELEMETRY REPLAY" :hints="['SPACE · PLAY/PAUSE', 'B · BACK', '◀ ▶ SPEED']" bg="neutral">
    
    <template #heading>
      <div class="heading-block mb-[1.5vh] flex justify-between items-end relative z-10">
        <h1 class="text-title font-title text-silver tracking-[0.2em]">LAP PLAYBACK</h1>
        <div class="text-ui-warn font-bold text-title-sm mb-1 animate-pulse" v-if="isPlaying">● REC</div>
        <div class="text-slate font-bold text-title-sm mb-1" v-else>■ PAUSED</div>
      </div>
      <div class="heading-rule mb-4"></div>
    </template>

    <div class="flex-grow flex flex-col mx-2 pb-6 gap-4 relative z-10">
      
      <!-- Video / Track visualization area -->
      <div class="flex-grow flex items-center justify-center relative max-h-full min-h-0">
        <CyberPanel class="w-full aspect-video flex items-center justify-center bg-black border-slate relative overflow-hidden max-w-4xl max-h-full">
          
          <!-- VCR Effects -->
          <div class="absolute inset-0 pointer-events-none bg-[repeating-linear-gradient(0deg,transparent,transparent_2px,rgba(0,0,0,0.2)_2px,rgba(0,0,0,0.2)_4px)] z-50 mix-blend-overlay"></div>
          <div class="absolute inset-0 pointer-events-none vhs-overlay z-40"></div>
          
          <!-- Track rendering -->
          <TrackMap :carProgress="progress" strokeClass="stroke-slate stroke-[20] opacity-50 vhs-blur" />

          <!-- Overlays -->
          <div class="absolute top-4 left-4 font-mono text-ui-good font-bold tracking-widest px-2 py-1 z-50 vhs-text">
            CH 1: SPEED
          </div>
          
          <div class="absolute bottom-4 right-4 font-mono text-white text-title tracking-widest px-2 shadow-lg flex items-baseline gap-2 z-50 vhs-text">
            {{ currentSpeed }} <span class="text-small text-slate">KM/H</span>
          </div>
        
          <div class="absolute bottom-4 left-4 font-mono flex gap-4 px-3 py-2 shadow-lg z-50">
            <div class="flex flex-col items-center">
              <span class="text-small text-slate">GEAR</span>
              <span class="text-title-sm text-ui-warn vhs-text">{{ currentGear }}</span>
            </div>
            <div class="flex flex-col items-center">
              <span class="text-small text-slate">RPM</span>
              <span class="text-title-sm text-white vhs-text">{{ currentRpm }}</span>
            </div>
          </div>

        </CyberPanel>
      </div>

      <!-- Scrubber & Controls -->
      <CyberPanel class="h-24 shrink-0 flex flex-col justify-center px-4 bg-ink border-charcoal">
        <div class="flex items-center gap-4 w-full">
          
          <div class="font-bold text-title-sm cursor-pointer select-none text-silver hover:text-white transition-colors" @click="togglePlay">
            {{ isPlaying ? '⏸' : '▶' }}
          </div>

          <div class="flex-grow relative pt-4 pb-2">
            <!-- Timeline markers -->
            <div class="absolute top-0 left-0 right-0 flex justify-between px-1 text-[10px] text-slate font-mono">
              <span>0:00</span>
              <span>0:30</span>
              <span>1:00</span>
              <span>1:30</span>
            </div>
            
            <div class="h-4 border border-charcoal bg-ink relative overflow-hidden mt-4">
              <div class="h-full bg-ui-good transition-all duration-75" :style="`width: ${progress}%`"></div>
            </div>
          </div>

          <div class="font-bold text-body text-slate w-12 text-right">
            {{ speedMulti }}X
          </div>

        </div>
      </CyberPanel>

    </div>
  </PageShell>
</template>

<style scoped>
.vhs-overlay {
  background: linear-gradient(rgba(18, 16, 16, 0) 50%, rgba(0, 0, 0, 0.25) 50%), linear-gradient(90deg, rgba(255, 0, 0, 0.06), rgba(0, 255, 0, 0.02), rgba(0, 0, 255, 0.06));
  background-size: 100% 2px, 3px 100%;
  box-shadow: inset 0 0 50px rgba(0,0,0,0.5);
  animation: flicker 0.15s infinite;
}

.vhs-text {
  text-shadow: 2px 0 1px rgba(255,0,0,0.5), -2px 0 1px rgba(0,255,255,0.5);
}

.vhs-blur {
  filter: blur(0.5px);
}

@keyframes flicker {
  0% { opacity: 0.95; }
  100% { opacity: 1; }
}
</style>
