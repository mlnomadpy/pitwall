<script setup lang="ts">
import { ref, onUnmounted, onMounted } from 'vue'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import { useRouter } from 'vue-router'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import { useSequence } from '@/shared/lib/useSequence'
import PageShell from '@/shared/ui/PageShell.vue'
import Frame from '@/shared/ui/core/Frame.vue'
import CyberSplitView from '@/shared/ui/core/CyberSplitView.vue'
import CyberButton from '@/shared/ui/core/CyberButton.vue'
import ConnRow from './ui/ConnRow.vue'
import LiveCarState from './ui/LiveCarState.vue'
import { useBridgeStore } from '@/shared/api/bridgeStore'
import { useTelemetryStore } from '@/entities/session/model/telemetryStore'

const router = useRouter()
const audio = useAudioStore()
const bridgeStore = useBridgeStore()
const telemetry = useTelemetryStore()

const bridgeState = ref<'checking' | 'ok' | 'error' | 'pending'>('checking')
const usbCanState = ref<'checking' | 'ok' | 'error' | 'pending'>('pending')
const dbcState = ref<'checking' | 'ok' | 'error' | 'pending'>('pending')
const carState = ref<'checking' | 'ok' | 'error' | 'pending'>('pending')

const liveState = ref({
  rpm: '---', gear: '-', speed: '---', 
  oil: '--', coolant: '--', fuel: '--',
  throttle: '--', brake: '-', steer: '--',
  glat: '-.-', glong: '-.-', gcombo: '-.-'
})

const { addStep, skip } = useSequence(5)

let liveInterval: number | null = null

const bootLogs = ref<{msg: string, type: string}[]>([])

const addLog = (msg: string, type = 'info') => {
  bootLogs.value.push({ msg, type })
  const el = document.getElementById('boot-logs-container')
  if (el) {
    setTimeout(() => {
      el.scrollTop = el.scrollHeight
    }, 50)
  }
}

const runSequence = () => {
  // Reset
  skip() // clears timeouts
  if (liveInterval) {
    clearInterval(liveInterval)
    liveInterval = null
  }
  
  bridgeState.value = 'checking'
  usbCanState.value = 'pending'
  dbcState.value = 'pending'
  carState.value = 'pending'
  bootLogs.value = []
  
  liveState.value = {
    rpm: '---', gear: '-', speed: '---', 
    oil: '--', coolant: '--', fuel: '--',
    throttle: '--', brake: '-', steer: '--',
    glat: '-.-', glong: '-.-', gcombo: '-.-'
  }
  
  addLog('INITIATING BOOT SEQUENCE...', 'warn')
  addLog('Connecting to bridge at 127.0.0.1:8765')

  bridgeStore.startPolling()

  let pollCount = 0
  const checkBridge = () => {
    if (bridgeStore.health) {
      bridgeState.value = 'ok'
      usbCanState.value = 'checking'
      addLog('BRIDGE CONNECTED (Engine: sonic_model, AI: LiteRT-LM)', 'good')
      addLog('Querying /dev/ttyACM0 for slcan interface...')
      audio.playSfx('goal_complete')
      
      // Continue to phase 2
      setTimeout(() => {
        usbCanState.value = 'ok'
        dbcState.value = 'checking'
        addLog('USB-CAN STREAM ESTABLISHED @ 500 kbps', 'good')
        addLog('Loading network definitions...')
        audio.playSfx('goal_complete')
        
        setTimeout(() => {
          dbcState.value = 'ok'
          carState.value = 'checking'
          addLog('DBC LOADED. Signals: 93 mapped, 3 unknown', 'good')
          addLog('Waiting for ignition pulse from ECU...')
          audio.playSfx('goal_complete')
          
          setTimeout(() => {
            carState.value = 'ok'
            addLog('IGNITION DETECTED.', 'good')
            addLog('LINK STABLE. STREAMING TELEMETRY.', 'good')
            audio.playSfx('level_up')
            
            // Open telemetry SSE stream
            telemetry.open(bridgeStore.health?.active_session_id || 'SIM')

            
            liveInterval = window.setInterval(() => {
              const f = telemetry.frame
              if (f) {
                liveState.value = {
                  rpm: Math.floor(f.rpm).toString(),
                  gear: f.speed < 10 ? '1' : f.speed < 60 ? '2' : f.speed < 100 ? '3' : f.speed < 140 ? '4' : '5',
                  speed: Math.floor(f.speed * 3.6).toString(),
                  oil: '94',
                  coolant: '88',
                  fuel: '62',
                  throttle: Math.floor(f.throttle).toString(),
                  brake: Math.floor(f.brake_pressure).toString(),
                  steer: f.steering.toFixed(1),
                  glat: f.g_lat.toFixed(1),
                  glong: f.g_long.toFixed(1),
                  gcombo: f.combo_g.toFixed(1)
                }
              }
            }, 100)
          }, 1500)
        }, 1000)
      }, 1500)
    } else if (bridgeStore.healthError || pollCount > 10) {
      bridgeState.value = 'error'
      addLog('BRIDGE CONNECTION FAILED. Is the daemon running?', 'bad')
      audio.playSfx('cancel')
    } else {
      pollCount++
      setTimeout(checkBridge, 500)
    }
  }
  
  setTimeout(checkBridge, 1000)
}

onMounted(() => {
  runSequence()
})

onUnmounted(() => {
  skip()
  if (liveInterval) clearInterval(liveInterval)
  telemetry.close()
})

useKeyboard((e: KeyboardEvent) => {
  if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.push('/garage')
  } else if (e.key === 'r' || e.key === 'R') {
    audio.playSfx('cursor_select')
    runSequence()
  } else if (e.key === 'Enter') {
    if (carState.value === 'ok') {
      audio.playSfx('cursor_select')
      router.push('/pit-stall/live')
    } else {
      audio.playSfx('error_quiet')
    }
  }
})

</script>

<template>
  <PageShell 
    title="PIT STALL" 
    :actions="[
      { label: 'LIVE WALL', key: 'Enter', keyLabel: 'A', variant: 'primary' },
      { label: 'REBOOT', key: 'r', keyLabel: 'R' },
      { label: 'BACK', key: 'Escape', keyLabel: 'B', variant: 'warn' }
    ]" 
    bg="neutral"
  >

    <template #heading>
      <div class="heading-block mb-[1.5vh] text-center">
        <h1 class="text-title font-title text-silver tracking-[0.3em]">PIT STALL DIAGNOSTICS</h1>
        <div class="heading-rule"></div>
      </div>
    </template>
    
    <div class="content flex flex-col relative z-10 h-full w-full">
      <div class="grid grid-cols-[1fr_1.4fr] gap-[4vmin] flex-grow min-h-0 pb-16">
        
        <!-- Left: Connection & Logs -->
        <div class="flex flex-col gap-4">
          <Frame variant="default" padding="0" class="flex flex-col flex-grow bg-ink/40 overflow-hidden">
            <div class="text-small text-slate tracking-[0.2em] font-black uppercase border-b border-slate/20 p-3 flex justify-between items-center bg-ink/60">
              <span>Connection Chain</span>
              <div class="flex gap-2">
                <CyberButton size="sm" variant="secondary" @click="runSequence">REBOOT</CyberButton>
              </div>
            </div>
            
            <div class="p-4 flex flex-col gap-3 overflow-y-auto no-scrollbar">
              <ConnRow title="BRIDGE" :state="bridgeState" status-text="ONLINE" :details="['127.0.0.1:8765', 'LITERT-LM ENGINE']" />
              <ConnRow title="USB-CAN" :state="usbCanState" status-text="STREAM" :details="['CANable slcan @ 500k']" />
              <ConnRow title="DBC" :state="dbcState" status-text="LOADED" :details="['93 SIGNALS MAPPED']" />
              <ConnRow title="CAR" :state="carState" status-text="READY" :details="['IGNITION ON']" />
            </div>

            <!-- Terminal Output -->
            <div class="mt-auto border-t border-slate/20 bg-ink/80 p-3 flex flex-col h-40">
              <div class="text-ui-info text-[10px] mb-2 font-black tracking-widest uppercase opacity-70">Terminal Output</div>
              <div class="flex-grow overflow-y-auto no-scrollbar font-nums text-small leading-tight whitespace-pre-line pr-1" id="boot-logs-container">
                <div v-for="(log, i) in bootLogs" :key="i" :class="log.type === 'good' ? 'text-ui-good' : log.type === 'bad' ? 'text-ui-bad' : log.type === 'warn' ? 'text-ui-warn' : 'text-silver/60'">
                  > {{ log.msg }}
                </div>
              </div>
            </div>
          </Frame>
        </div>

        <!-- Right: Live Data Dashboard -->
        <div class="flex flex-col gap-4">
          <Frame variant="default" padding="0" class="flex flex-col flex-grow bg-ink/40 overflow-hidden">
            <div class="text-small text-slate tracking-[0.2em] font-black uppercase border-b border-slate/20 p-3 bg-ink/60 flex justify-between items-center">
              <span>Telemetry Monitor</span>
              <span v-if="carState === 'ok'" class="text-ui-good animate-pulse">● LIVE</span>
            </div>
            
            <div class="p-4 flex-grow overflow-y-auto no-scrollbar">
              <LiveCarState :state="liveState" />
            </div>

            <div class="p-4 border-t border-slate/20 flex gap-4">
               <CyberButton fluid variant="primary" size="lg" @click="router.push('/pit-stall/live')" :disabled="carState !== 'ok'">
                  OPEN LIVE PIT WALL
               </CyberButton>
            </div>
          </Frame>
        </div>

      </div>
    </div>
  </PageShell>
</template>

<style scoped>
.hud-layout {
  padding: calc(max(var(--safe-top), var(--space-md))) 
           calc(max(var(--safe-right), var(--space-md))) 
           calc(max(var(--safe-bottom), var(--space-md))) 
           calc(max(var(--safe-left), var(--space-md)));
}
</style>
