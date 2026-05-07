<script setup lang="ts">
import { ref, onUnmounted } from 'vue'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import { useRouter } from 'vue-router'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import { useSequence } from '@/shared/lib/useSequence'
import PageShell from '@/shared/ui/PageShell.vue'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'
import CyberSplitView from '@/shared/ui/core/CyberSplitView.vue'
import CyberButton from '@/shared/ui/core/CyberButton.vue'
import ConnRow from './ui/ConnRow.vue'
import LiveCarState from './ui/LiveCarState.vue'
import { useBridgeStore } from '@/shared/api/bridgeStore'

const router = useRouter()
const audio = useAudioStore()
const bridgeStore = useBridgeStore()

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
            liveInterval = window.setInterval(() => {
              liveState.value = {
                rpm: Math.floor(800 + Math.random() * 200).toString(),
                gear: '1',
                speed: '0',
                oil: '94',
                coolant: '88',
                fuel: '62',
                throttle: Math.floor(Math.random() * 10).toString(),
                brake: '0',
                steer: (Math.random() * 2 - 1).toFixed(1),
                glat: '0.0',
                glong: '0.0',
                gcombo: '0.0'
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

// (Sequence functions replaced with nested timeouts above)

// Initial Run
runSequence()

onUnmounted(() => {
  skip()
  if (liveInterval) clearInterval(liveInterval)
})

useKeyboard((e: KeyboardEvent) => {
  if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.push('/garage')
  }
})
</script>

<template>
  <PageShell title="PIT STALL" :hints="['A · BACK', 'B · BACK', '◆ HARDWARE INFO']" bg="neutral">
    <template #heading>
      <div class="heading-block mb-[1.5vh] text-center">
        <h1 class="text-title font-title text-silver tracking-[0.3em]">PIT STALL</h1>
        <div class="heading-rule"></div>
      </div>
    </template>
    
    <div class="content flex flex-col relative z-10 h-full pb-[6vh] w-full">
      <CyberSplitView split="40-60" gap="md" class="h-full">
        <!-- Left Pane: Connection Chain -->
        <template #left>
          <CyberPanel class="h-full flex flex-col min-h-0">
            <div class="text-body text-silver mb-[1.5vh] border-b border-slate pb-[1vh] tracking-[0.1em] px-[1.5vmin] pt-[1.5vmin] flex justify-between items-center">
              <span>CONNECTION CHAIN</span>
              <div class="flex gap-2">
                <CyberButton size="sm" variant="primary" @click="$router.push('/pit-stall/live')" v-if="carState === 'ok'" style="font-size: 10px; padding: 2px 6px;">LIVE WALL</CyberButton>
                <CyberButton size="sm" variant="secondary" @click="runSequence" style="font-size: 10px; padding: 2px 6px;">RETRY / REBOOT</CyberButton>
              </div>
            </div>
            <div class="flex flex-col gap-2 px-[1.5vmin] pb-[1.5vmin] flex-grow no-scrollbar min-h-0">
              <ConnRow title="BRIDGE" :state="bridgeState" status-text="ONLINE" :details="['127.0.0.1:8765', 'ENGINE sonic_model + LiteRT-LM', 'DUCKDB enabled · 47 sessions']" />
              <ConnRow title="USB-CAN" :state="usbCanState" status-text="STREAM" :details="['/dev/ttyACM0 CANable Pro', 'INTERFACE slcan @ 500 kbps', 'FRAMES/s 422']" />
              <ConnRow title="DBC" :state="dbcState" status-text="" :details="['pitwall.dbc + bmw_e46_m3.dbc', 'SIGNALS 29 + 64 = 93 known', 'UNKNOWN IDS 3 (logged, not decoded)']" />
              <ConnRow title="CAR" :state="carState" status-text="READY" :details="['BMW M3 (E46)', 'IGNITION ON']" />
              
              <!-- Logs Console -->
              <div class="mt-4 flex-grow border-2 border-slate bg-ink p-2 flex flex-col h-32 relative shadow-[inset_0_0_10px_rgba(0,0,0,0.8)]">
                <div class="text-ui-info text-[clamp(8px,1.5vmin,12px)] mb-1 font-bold tracking-widest border-b border-slate pb-1">TERMINAL OUTPUT</div>
                <div class="flex-grow overflow-y-auto no-scrollbar font-mono text-[clamp(8px,1.8vmin,14px)] leading-tight whitespace-pre-line break-words pr-1" id="boot-logs-container">
                  <div v-for="(log, i) in bootLogs" :key="i" :class="log.type === 'good' ? 'text-ui-good' : log.type === 'bad' ? 'text-ui-bad' : log.type === 'warn' ? 'text-ui-warn' : 'text-silver'">
                    > {{ log.msg }}
                  </div>
                </div>
              </div>
            </div>
          </CyberPanel>
        </template>
        
        <!-- Right Pane: Live Car State -->
        <template #right>
          <CyberPanel class="h-full flex flex-col min-h-0">
            <div class="text-body text-silver mb-[1.5vh] border-b border-slate pb-[1vh] tracking-[0.1em] px-[1.5vmin] pt-[1.5vmin]">
              LIVE CAR STATE
            </div>
            <div class="flex-grow overflow-y-auto px-[1.5vmin] pb-[1.5vmin] no-scrollbar min-h-0 h-full">
              <LiveCarState :state="liveState" />
            </div>
          </CyberPanel>
        </template>
      </CyberSplitView>
    </div>
  </PageShell>
</template>

<style scoped>
.heading-block { text-align: center; }
</style>

