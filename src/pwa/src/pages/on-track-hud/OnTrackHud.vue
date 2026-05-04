<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import { useRouter } from 'vue-router'
import { useCueStore } from '@/features/coach-interaction/model/cueStore'
import { useSessionStore } from '@/entities/session/model/sessionStore'
import { useTelemetryStore } from '@/entities/session/model/telemetryStore'
import GripBar from '@/widgets/hud/GripBar.vue'
import TrackMap from '@/widgets/hud/TrackMap.vue'
import CueBand from '@/features/coach-interaction/ui/CueBand.vue'
import CyberSplitView from '@/shared/ui/core/CyberSplitView.vue'

const router = useRouter()
const cueStore = useCueStore()
const session = useSessionStore()
const telemetry = useTelemetryStore()

const sid = session.activeSessionId ?? 'demo-session'

const paused = ref(false)

const distanceM = computed(() => telemetry.frame?.distance ?? 0)
// Compute friction from combo_g (assuming 1.2g is ~100% friction)
const frictionPct = computed(() => {
  const g = telemetry.frame?.combo_g ?? 0
  return Math.min(100, Math.max(0, (g / 1.2) * 100))
})
const overPct = computed(() => frictionPct.value > 80 ? (frictionPct.value - 80) * 5 : 0)
const aiOn = ref(true)

let simInterval: number
onMounted(async () => {
  try {
    if (document.documentElement.requestFullscreen) {
      await document.documentElement.requestFullscreen()
    }
  } catch (e) {
    console.warn('Fullscreen rejected', e)
  }

  cueStore.open(sid)
  telemetry.open(sid)
  
  simInterval = window.setInterval(() => {
    if (paused.value) return
    
    // Process cue queue
    if (!cueStore.activeCue && cueStore.queue.length > 0) {
      cueStore.activeCue = cueStore.queue.shift()!
      setTimeout(() => { cueStore.activeCue = null }, 3000)
    }
  }, 100)
})

onUnmounted(() => {
  cueStore.close()
  telemetry.close()
  clearInterval(simInterval)
  if (document.fullscreenElement) {
    document.exitFullscreen?.()
  }
})

useKeyboard((e: KeyboardEvent) => {
  if (e.key === 'Escape' || e.key === 'Backspace') {
    router.push('/stage-clear')
  } else if (e.key === 'b') {
    paused.value = !paused.value
  } else if (e.key === 'Enter' && paused.value) {
    router.push('/garage')
  }
})
</script>

<template>
  <div class="viewport pixelated relative w-full h-full bg-asphalt-deep text-silver font-ui overflow-hidden">
    
    <CyberSplitView split="60-40" gap="md" class="h-full p-[2vmin]">
      
      <!-- Left Pane: Track Map -->
      <template #left>
        <div class="flex flex-col h-full bg-ink/80 border border-slate p-[2vmin] relative justify-center">
          <!-- Top Left Info -->
          <div class="absolute top-[2vmin] left-[2vmin] flex gap-[3vw]">
            <div class="flex flex-col">
              <span class="text-small text-slate tracking-widest">LAP</span>
              <span class="text-title-sm text-white font-bold">3 / 8</span>
            </div>
            <div class="flex flex-col">
              <span class="text-small text-slate tracking-widest">TIME</span>
              <span class="text-title-sm font-nums text-ui-good">1:47.2</span>
            </div>
          </div>
          
          <TrackMap track="sonoma" :pos-m="distanceM" class="track-center self-center" />
          
          <div class="absolute bottom-[2vmin] left-0 w-full text-center text-small text-slate tracking-[0.2em]">
            SONOMA RACEWAY
          </div>
        </div>
      </template>
      
      <!-- Right Pane: Telemetry Sidebar -->
      <template #right>
        <div class="flex flex-col h-full gap-[2vmin]">
          
          <!-- AI Status Panel -->
          <div class="bg-ink/80 border border-slate p-[1.5vmin] flex justify-between items-center">
            <span class="text-body tracking-wider text-silver">COACH AI</span>
            <span class="ai-indicator" :class="aiOn ? 'ai-on' : 'ai-off'">
              <span class="ai-dots">●●●●●</span>
              {{ aiOn ? 'ACTIVE' : 'OFF' }}
            </span>
          </div>
          
          <!-- Grip & Friction -->
          <div class="bg-ink/80 border border-slate p-[1.5vmin] flex gap-[2vw] justify-around py-[3vh] flex-grow">
            <GripBar :pct="frictionPct" :is-over="false" label="GRIP" />
            <GripBar :pct="overPct" :is-over="true" label="OVER" />
          </div>
          
          <!-- Telemetry Stream / Data Grid -->
          <div class="bg-ink/80 border border-slate p-[1.5vmin]">
            <div class="flex justify-between items-center mb-[1vh] border-b border-slate pb-[0.5vh]">
              <span class="text-small text-slate tracking-[0.1em]">LIVE TELEMETRY</span>
              <span class="text-[10px] uppercase font-bold" :class="telemetry.frame ? 'text-ui-good' : 'text-ui-warn animate-pulse'">
                {{ telemetry.frame ? 'STREAMING' : 'WAITING' }}
              </span>
            </div>
            <div class="grid grid-cols-2 gap-x-4 gap-y-2 font-monospace text-body">
              <div class="flex justify-between">
                <span class="text-slate">FRC</span>
                <span class="text-white">{{ frictionPct.toFixed(1) }} %</span>
              </div>
              <div class="flex justify-between">
                <span class="text-slate">OVR</span>
                <span class="text-white">{{ overPct.toFixed(1) }} %</span>
              </div>
              <div class="flex justify-between">
                <span class="text-slate">POS</span>
                <span class="text-white">{{ distanceM.toFixed(0) }} m</span>
              </div>
              <div class="flex justify-between">
                <span class="text-slate">SPD</span>
                <span class="text-white">{{ ((telemetry.frame?.speed ?? 0) * 2.237).toFixed(1) }} mph</span>
              </div>
            </div>
          </div>
        </div>
      </template>
      
    </CyberSplitView>
    
    <CueBand :cue="cueStore.activeCue" class="cue-override" />
    
    <!-- Pause Overlay -->
    <div v-if="paused" class="pause-overlay">
      <h2 class="text-title-lg text-ui-warn font-title mb-[2vh] animate-pulse">PAUSED</h2>
      <p class="text-body mb-[4vh] text-silver">SYS HALT</p>
      <div class="flex gap-[4vw] text-body">
        <span class="text-silver hover:text-white cursor-pointer" @click="router.push('/garage')">ENTER · QUIT</span>
        <span class="text-silver hover:text-white cursor-pointer" @click="paused = false">B · RESUME</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ai-indicator {
  display: flex;
  align-items: center;
  gap: clamp(4px, 1vw, 8px);
  font-size: clamp(10px, 2.5vmin, 20px);
  font-weight: bold;
}

.ai-on {
  color: var(--color-ui-good);
  text-shadow: 1px 1px 0 rgba(0, 0, 0, 0.8);
}

.ai-off {
  color: var(--color-ui-bad);
  text-shadow: 1px 1px 0 rgba(0, 0, 0, 0.8);
}

.ai-dots {
  letter-spacing: -0.1em;
  font-size: clamp(6px, 1.5vmin, 12px);
}

.track-center {
  filter: drop-shadow(4px 4px 0 rgba(0,0,0,0.8));
}

.viewport .cue-override {
  top: 50%;
  transform: translateY(-50%);
  box-shadow: 4px 4px 0 rgba(0, 0, 0, 0.8);
}

.pause-overlay {
  position: absolute;
  inset: 0;
  background: rgba(11, 12, 16, 0.95);
  z-index: 100;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  /* Scanline effect */
  background-image: repeating-linear-gradient(0deg, transparent, transparent 2px, rgba(255,255,255,0.05) 2px, rgba(255,255,255,0.05) 4px);
}
</style>
