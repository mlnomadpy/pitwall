<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useCueStore } from '@/features/coach-interaction/model/cueStore'
import { useSessionStore } from '@/entities/session/model/sessionStore'
import GripBar from '@/widgets/hud/GripBar.vue'
import TrackMap from '@/widgets/hud/TrackMap.vue'
import CueBand from '@/features/coach-interaction/ui/CueBand.vue'

const router = useRouter()
const cueStore = useCueStore()
const session = useSessionStore()

const sid = session.activeSessionId ?? 'demo-session'

const frictionPct = ref(0)
const overPct = ref(0)
const distanceM = ref(0)
const aiOn = ref(true)
const paused = ref(false)

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
  window.addEventListener('keydown', handleKey)
  
  simInterval = window.setInterval(() => {
    if (paused.value) return
    distanceM.value = (distanceM.value + 5) % 4000
    frictionPct.value = Math.min(100, Math.max(0, 50 + Math.sin(Date.now() / 500) * 40))
    overPct.value = frictionPct.value > 80 ? (frictionPct.value - 80) * 5 : 0
    
    if (Math.random() < 0.05 && !cueStore.activeCue) {
      cueStore.activeCue = { id: 'test', text: 'ROLL THE BRAKE TO THE APEX', emotion: 'talk', timestamp: Date.now() }
      setTimeout(() => { cueStore.activeCue = null }, 3000)
    }
  }, 100)
})

onUnmounted(() => {
  cueStore.close()
  window.removeEventListener('keydown', handleKey)
  clearInterval(simInterval)
  if (document.fullscreenElement) {
    document.exitFullscreen?.()
  }
})

const handleKey = (e: KeyboardEvent) => {
  if (e.key === 'Escape' || e.key === 'Backspace') {
    router.push('/stage-clear')
  } else if (e.key === 'b') {
    paused.value = !paused.value
  } else if (e.key === 'Enter' && paused.value) {
    router.push('/garage')
  }
}
</script>

<template>
  <div class="viewport pixelated relative w-full h-full bg-asphalt-deep text-silver font-ui overflow-hidden">
    
    <!-- Dashboard Top Left: Speed/Lap Data -->
    <div class="dash-panel top-left">
      <div class="dash-data">
        <span class="text-small text-slate">LAP</span>
        <span class="text-body text-white">3 / 8</span>
      </div>
      <div class="dash-data">
        <span class="text-small text-slate">TIME</span>
        <span class="text-body font-nums text-ui-good">1:47.2</span>
      </div>
    </div>

    <!-- Dashboard Top Right: AI Status -->
    <div class="dash-panel top-right">
      <span class="ai-indicator" :class="aiOn ? 'ai-on' : 'ai-off'">
        <span class="ai-dots">●●●●●</span>
        AI {{ aiOn ? 'ON' : 'OFF' }}
      </span>
    </div>
    
    <!-- Main HUD Area -->
    <div class="hud-main">
      <div class="dash-gauge left">
        <GripBar :pct="frictionPct" :is-over="false" label="GRIP" />
      </div>
      
      <TrackMap track="sonoma" :pos-m="distanceM" class="track-center" />
      
      <div class="dash-gauge right">
        <GripBar :pct="overPct" :is-over="true" label="OVER" />
      </div>
    </div>
    
    <!-- Telemetry Stream (Faked data scrolling) -->
    <div class="telemetry-stream">
      <div>FRC: {{ frictionPct.toFixed(1) }}</div>
      <div>OVR: {{ overPct.toFixed(1) }}</div>
      <div>POS: {{ distanceM.toFixed(0) }}m</div>
      <div class="text-ui-good">SYNC OK</div>
    </div>
    
    <CueBand :cue="cueStore.activeCue" class="cue-override" />
    
    <!-- Pause Overlay -->
    <div v-if="paused" class="pause-overlay">
      <h2 class="text-title-lg text-ui-warn font-title mb-[2vh] animate-pulse">PAUSED</h2>
      <p class="text-body mb-[4vh] text-silver">SYS HALT</p>
      <div class="flex gap-[4vw] text-body">
        <span class="text-silver hover:text-white cursor-pointer">A · QUIT</span>
        <span class="text-silver hover:text-white cursor-pointer">B · RESUME</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dash-panel {
  position: absolute;
  top: 0;
  padding: clamp(8px, 2vh, 16px) clamp(16px, 3vw, 24px);
  background: rgba(11, 12, 16, 0.9);
  border-bottom: 2px solid var(--color-ui-good);
  z-index: 10;
  display: flex;
  gap: clamp(10px, 2vw, 20px);
  box-shadow: 0 4px 15px rgba(78, 205, 196, 0.2);
}

.top-left {
  left: 0;
  clip-path: polygon(0 0, 100% 0, calc(100% - 16px) 100%, 0 100%);
  padding-right: 32px;
}

.top-right {
  right: 0;
  clip-path: polygon(0 0, 100% 0, 100% 100%, 16px 100%);
  padding-left: 32px;
  border-bottom-color: var(--color-ui-warn);
  box-shadow: 0 4px 15px rgba(254, 202, 87, 0.2);
}

.dash-data {
  display: flex;
  flex-direction: column;
}

.ai-indicator {
  display: flex;
  align-items: center;
  gap: clamp(4px, 1vw, 8px);
  font-size: clamp(10px, 2.5vmin, 20px);
  font-weight: bold;
}

.ai-on {
  color: var(--color-ui-good);
  text-shadow: 0 0 6px rgba(78, 205, 196, 0.6);
}

.ai-off {
  color: var(--color-ui-bad);
  text-shadow: 0 0 6px rgba(255, 71, 87, 0.6);
}

.ai-dots {
  letter-spacing: -0.1em;
  font-size: clamp(6px, 1.5vmin, 12px);
}

.hud-main {
  position: absolute;
  inset: 0;
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  padding: clamp(20px, 5vh, 40px);
  pointer-events: none;
}

.dash-gauge {
  background: rgba(11, 12, 16, 0.8);
  padding: 10px;
  border: 1px solid rgba(78, 205, 196, 0.3);
  clip-path: polygon(10px 0, 100% 0, 100% calc(100% - 10px), calc(100% - 10px) 100%, 0 100%, 0 10px);
}

.dash-gauge.left {
  transform: perspective(400px) rotateY(15deg);
}

.dash-gauge.right {
  transform: perspective(400px) rotateY(-15deg);
}

.track-center {
  margin-bottom: auto;
  margin-top: 10vh;
  filter: drop-shadow(0 0 20px rgba(78, 205, 196, 0.3));
}

.telemetry-stream {
  position: absolute;
  bottom: 10px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 20px;
  font-size: 10px;
  color: rgba(255, 255, 255, 0.5);
  font-family: monospace;
}

.cue-override {
  top: 50% !important;
  transform: translateY(-50%) !important;
  box-shadow: 0 0 40px rgba(255, 71, 87, 0.5) !important;
}

.pause-overlay {
  position: absolute;
  inset: 0;
  background: rgba(11, 12, 16, 0.9);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  z-index: 100;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  /* Scanline effect */
  background-image: repeating-linear-gradient(0deg, transparent, transparent 2px, rgba(255,255,255,0.05) 2px, rgba(255,255,255,0.05) 4px);
}
</style>
