<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import { useRouter } from 'vue-router'
import { useCueStore } from '@/features/coach-interaction/model/cueStore'
import { useSessionStore } from '@/entities/session/model/sessionStore'
import { useTelemetryStore } from '@/entities/session/model/telemetryStore'
import { useSaveStore } from '@/entities/save/model/saveStore'
import { resolveSessionId } from '@/entities/session/model/sessionContext'
import { useCuesStreamLogStore } from '@/features/hud/model/cuesStreamLogStore'
import { cuesStreamUrl } from '@/shared/api/bridge'
import GripBar from '@/widgets/hud/GripBar.vue'
import HudTrackMap from '@/widgets/hud/HudTrackMap.vue'
import CueBand from '@/features/coach-interaction/ui/CueBand.vue'
import Frame from '@/shared/ui/core/Frame.vue'
import CyberButton from '@/shared/ui/core/CyberButton.vue'

const router = useRouter()
const cueStore = useCueStore()
const session = useSessionStore()
const telemetry = useTelemetryStore()
const save = useSaveStore()
const cuesLog = useCuesStreamLogStore()

/** Prefer sessionStorage active session / save slot sessions — aligns with Android SessionHolder + bridge. */
const hudSessionId = computed(() => resolveSessionId(save.activeSlot) ?? session.activeSessionId ?? null)
const sidForCue = computed(() => hudSessionId.value ?? 'demo-session')
const cuesStreamUrlLabel = computed(() => cuesStreamUrl(hudSessionId.value ?? undefined))

const paused = ref(false)

const distanceM = computed(() => telemetry.frame?.distance ?? 0)
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

  await save.hydrate()
  cuesLog.start(hudSessionId.value)
  cueStore.open(sidForCue.value)
  telemetry.open(sidForCue.value)
  
  simInterval = window.setInterval(() => {
    if (paused.value) return
    if (!cueStore.activeCue && cueStore.queue.length > 0) {
      cueStore.activeCue = cueStore.queue.shift()!
      setTimeout(() => { cueStore.activeCue = null }, 3000)
    }
  }, 100)
})

watch(hudSessionId, (id) => {
  cuesLog.start(id)
  cueStore.close()
  cueStore.open(sidForCue.value)
  telemetry.close()
  telemetry.open(sidForCue.value)
})

onUnmounted(() => {
  cuesLog.stop()
  cueStore.close()
  telemetry.close()
  clearInterval(simInterval)
  if (document.fullscreenElement) {
    document.exitFullscreen?.()
  }
})

useKeyboard((e: KeyboardEvent) => {
  if (e.key === 'Escape' || e.key === 'Backspace') {
    paused.value = !paused.value
  } else if (e.key === 'b') {
    paused.value = !paused.value
  } else if (e.key === 'Enter' && paused.value) {
    router.push('/garage')
  }
})
</script>

<template>
  <div class="viewport relative w-full h-full bg-[#050508] text-silver font-ui overflow-hidden">
    
    <!-- HIGH VISIBILITY COCKPIT GRID -->
    <div class="hud-layout grid grid-cols-[1fr_1.2fr_1fr] gap-[4vmin] h-full">
      
      <!-- LEFT: NAVIGATION & SESSION -->
      <div class="flex flex-col justify-between h-full">
        <Frame variant="inset" padding="16px" class="flex flex-col gap-2 border-slate/30 bg-ink/40">
          <span class="text-[clamp(12px,2.5vmin,20px)] text-slate tracking-[0.3em] font-bold uppercase">Lap Position</span>
          <div class="flex items-baseline gap-2">
            <span class="text-[clamp(40px,10vmin,80px)] font-nums font-black text-white leading-none">
              {{ telemetry.frame?.lap_number ?? 1 }}
            </span>
            <span class="text-title-sm text-slate/50">/ {{ session.totalLaps ?? 8 }}</span>
          </div>
        </Frame>
        
        <div class="flex-grow flex items-center justify-center py-4 relative">
           <!-- Increased track map size -->
           <HudTrackMap track="sonoma" :pos-m="distanceM" class="track-minimap scale-125 origin-center" />
        </div>

        <Frame variant="inset" padding="12px" class="text-center bg-ink/40 border-slate/30">
          <span class="text-[clamp(12px,2.5vmin,20px)] text-slate tracking-widest font-bold uppercase">Sonoma GP</span>
        </Frame>
      </div>

      <!-- CENTER: PRIMARY TELEMETRY (Glanceable) -->
      <div class="flex flex-col items-center justify-center gap-[6vmin] h-full relative">
        <div class="speed-display flex flex-col items-center">
          <div class="flex items-baseline">
            <span class="text-[clamp(100px,26vmin,240px)] font-nums font-black text-white leading-none tracking-tighter drop-shadow-[0_0_40px_rgba(255,255,255,0.15)]">
              {{ ((telemetry.frame?.speed ?? 0) * 2.237).toFixed(0) }}
            </span>
          </div>
          <span class="text-[clamp(24px,5vmin,48px)] text-slate tracking-[0.6em] font-black uppercase -mt-2 opacity-80">MPH</span>
        </div>

        <!-- LARGE GRIP GAUGES -->
        <div class="flex gap-[12vw] items-center">
           <GripBar :pct="frictionPct" :is-over="false" label="GRIP" class="scale-[2.2]" />
           <GripBar :pct="overPct" :is-over="true" label="SLIP" class="scale-[2.2]" />
        </div>
        
        <!-- CRT Scanline Intensity for HUD -->
        <div class="absolute inset-0 pointer-events-none opacity-20 bg-[radial-gradient(circle,transparent_20%,#000_120%)]"></div>
      </div>

      <!-- RIGHT: ANALYSIS & STATUS -->
      <div class="flex flex-col justify-between h-full">
        <Frame variant="inset" padding="16px" class="flex flex-col items-end gap-2 border-slate/30 bg-ink/40">
          <span class="text-[clamp(12px,2.5vmin,20px)] text-slate tracking-[0.3em] font-bold uppercase">Delta / Time</span>
          <span class="text-[clamp(40px,10vmin,80px)] font-nums font-black text-ui-good leading-none">1:47.2</span>
        </Frame>

        <div class="flex-grow flex flex-col justify-center gap-6">
          <Frame variant="default" padding="24px" class="bg-ink/60 border-slate/40 backdrop-blur-sm">
            <div class="flex flex-col gap-6 text-[clamp(16px,3.5vmin,28px)] font-black uppercase tracking-[0.1em]">
              <div class="flex justify-between items-center">
                <span class="text-slate/60">Friction</span>
                <div class="flex items-center gap-2">
                  <span class="text-white">{{ frictionPct.toFixed(0) }}</span>
                  <span class="text-slate text-body">%</span>
                </div>
              </div>
              <div class="h-[2px] bg-slate/10 w-full"></div>
              <div class="flex justify-between items-center">
                <span class="text-slate/60">Odometer</span>
                <div class="flex items-center gap-2">
                  <span class="text-white">{{ (distanceM / 1000).toFixed(2) }}</span>
                  <span class="text-slate text-body">km</span>
                </div>
              </div>
            </div>
          </Frame>
        </div>

        <Frame variant="inset" padding="16px" class="flex justify-between items-center bg-ink/40 border-slate/30">
          <span class="text-[clamp(12px,2.5vmin,20px)] tracking-[0.2em] text-slate font-black uppercase">Stream</span>
          <div class="flex items-center gap-3">
            <div class="status-pip" :class="telemetry.frame ? 'active' : 'inactive'"></div>
            <span class="text-[clamp(12px,2.5vmin,20px)] font-black tracking-widest" :class="telemetry.frame ? 'text-ui-good' : 'text-ui-bad animate-pulse'">
              {{ telemetry.frame ? 'LIVE' : 'LOST' }}
            </span>
          </div>
        </Frame>
      </div>
      
    </div>

    
    <CueBand :cue="cueStore.activeCue" />

    <!-- Bridge cues SSE log — parity with Android [HudScreen] raw line feed (+ reconnect). -->
    <div
      class="fixed bottom-0 left-0 right-0 z-[90] flex max-h-[min(28vh,280px)] flex-col border-t border-slate/40 bg-[#050508]/95 font-mono text-[clamp(9px,1.6vmin,11px)] backdrop-blur-md"
    >
      <div class="flex shrink-0 flex-wrap items-center justify-between gap-2 border-b border-slate/30 px-2 py-1 text-slate">
        <span class="min-w-0 flex-1 truncate" :title="cuesStreamUrlLabel">{{ cuesStreamUrlLabel }}</span>
        <span v-if="cuesLog.reconnectAttempt > 0" class="text-ui-warn shrink-0">retry {{ cuesLog.reconnectAttempt }}</span>
        <button
          type="button"
          class="shrink-0 rounded border border-slate/50 px-2 py-0.5 text-silver hover:border-ui-info/60"
          @click="cuesLog.clearLog()"
        >
          Clear log
        </button>
      </div>
      <div v-if="cuesLog.connectionError" class="shrink-0 px-2 py-0.5 text-ui-bad">{{ cuesLog.connectionError }}</div>
      <pre
        class="min-h-0 flex-1 overflow-y-auto whitespace-pre-wrap px-2 py-1 leading-snug text-silver"
      >{{ cuesLog.rawLines.join('\n') }}</pre>
    </div>
    
    <!-- HIGH CONTRAST PAUSE MODAL -->
    <div v-if="paused" class="pause-overlay">
      <h2 class="text-[clamp(40px,10vmin,80px)] text-ui-warn font-title mb-12 animate-pulse tracking-[0.4em] font-black">PAUSED</h2>
      <div class="flex gap-12">
        <CyberButton @click="router.push('/garage')" variant="dark" size="lg">EXIT TO GARAGE</CyberButton>
        <CyberButton @click="paused = false" variant="primary" size="lg">RESUME SESSION</CyberButton>
      </div>
    </div>
  </div>
</template>

<style scoped>
.hud-layout {
  padding: calc(max(var(--safe-top), var(--space-md))) 
           calc(max(var(--safe-right), var(--space-md))) 
           calc(max(var(--safe-bottom), var(--space-md))) 
           calc(max(var(--safe-left), var(--space-md)));
}

.track-minimap {
  width: clamp(180px, 45vmin, 320px);
  height: clamp(180px, 45vmin, 320px);
  filter: drop-shadow(0 0 30px rgba(0,0,0,0.8));
}

.speed-display {
  text-shadow: 4px 4px 0 #000, 0 0 40px rgba(255,255,255,0.1);
}

.status-pip {
  width: 10px;
  height: 10px;
  border-radius: 2px;
}

.status-pip.active {
  background-color: var(--color-ui-good);
  box-shadow: 0 0 12px var(--color-ui-good);
  animation: pulse-pip 2s ease-in-out infinite;
}

.status-pip.inactive {
  background-color: var(--color-ui-bad);
}

@keyframes pulse-pip {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.6; transform: scale(0.8); }
}

.pause-overlay {
  position: absolute;
  inset: 0;
  background: rgba(5, 5, 8, 0.98);
  z-index: 200;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(20px);
}
</style>
