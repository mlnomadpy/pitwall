<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import StatusBar from '@/widgets/status-bar/StatusBar.vue'
import HintBar from '@/widgets/hint-bar/HintBar.vue'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'
import ConnRow from './ui/ConnRow.vue'
import LiveCarState from './ui/LiveCarState.vue'

const router = useRouter()
const audio = useAudioStore()

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

let seqTimeout: number
let liveInterval: number | null = null

onMounted(() => {
  window.addEventListener('keydown', handleKey)
  
  // Fake the connection sequence for UI demonstration
  seqTimeout = window.setTimeout(() => {
    bridgeState.value = 'ok'
    usbCanState.value = 'checking'
    audio.playSfx('goal_complete')
    
    seqTimeout = window.setTimeout(() => {
      usbCanState.value = 'ok'
      dbcState.value = 'checking'
      audio.playSfx('goal_complete')
      
      seqTimeout = window.setTimeout(() => {
        dbcState.value = 'ok'
        carState.value = 'checking'
        audio.playSfx('goal_complete')
        
        seqTimeout = window.setTimeout(() => {
          carState.value = 'ok'
          audio.playSfx('level_up')
          
          // Start fake live telemetry
          liveInterval = window.setInterval(() => {
            liveState.value = {
              rpm: Math.floor(800 + Math.random() * 200).toString(),
              gear: '1',
              speed: '0',
              oil: '94',
              coolant: '88',
              fuel: '62',
              throttle: '0',
              brake: '15',
              steer: '0',
              glat: '0.0',
              glong: '0.0',
              gcombo: '0.0'
            }
          }, 200)
          
        }, 1500)
      }, 1000)
    }, 1500)
  }, 1000)
})

onUnmounted(() => {
  clearTimeout(seqTimeout)
  if (liveInterval) clearInterval(liveInterval)
  window.removeEventListener('keydown', handleKey)
})

const handleKey = (e: KeyboardEvent) => {
  if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.push('/garage')
  }
}
</script>

<template>
  <div class="viewport pixelated relative w-full h-full bg-ink text-silver overflow-hidden">
    <StatusBar />
    
    <div class="pitstall-bg absolute inset-0 z-0"></div>
    
    <div class="content flex flex-col pt-[6vh] px-[3vw] relative z-10 h-full pb-[6vh]">
      <div class="heading-block mb-[1.5vh] text-center">
        <h1 class="text-title font-title text-silver tracking-[0.3em]">PIT STALL</h1>
        <div class="heading-rule"></div>
      </div>
      
      <CyberPanel class="mb-[1.5vh] p-[1.5vmin]">
        <div class="text-body text-silver mb-[0.8vh] border-b border-slate pb-[0.5vh] tracking-[0.1em]">CONNECTION CHAIN</div>
        <ConnRow title="BRIDGE" :state="bridgeState" status-text="ONLINE" :details="['127.0.0.1:8765', 'ENGINE sonic_model + LiteRT-LM', 'DUCKDB enabled · 47 sessions']" />
        <ConnRow title="USB-CAN" :state="usbCanState" status-text="STREAM" :details="['/dev/ttyACM0 CANable Pro', 'INTERFACE slcan @ 500 kbps', 'FRAMES/s 422']" />
        <ConnRow title="DBC" :state="dbcState" status-text="" :details="['pitwall.dbc + bmw_e46_m3.dbc', 'SIGNALS 29 + 64 = 93 known', 'UNKNOWN IDS 3 (logged, not decoded)']" />
        <ConnRow title="CAR" :state="carState" status-text="READY" :details="['BMW M3 (E46)', 'IGNITION ON']" />
      </CyberPanel>
      
      <CyberPanel class="p-[1.5vmin] flex-grow">
        <div class="text-body text-silver mb-[0.8vh] border-b border-slate pb-[0.5vh] tracking-[0.1em]">LIVE CAR STATE</div>
        <LiveCarState :state="liveState" />
      </CyberPanel>
    </div>
    
    <HintBar :hints="['A · BACK', 'B · BACK', '◆ HARDWARE INFO']" />
  </div>
</template>

<style scoped>
.pitstall-bg {
  background-color: var(--color-asphalt-deep);
}
</style>

