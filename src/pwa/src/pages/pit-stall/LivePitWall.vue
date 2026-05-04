<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import PageShell from '@/shared/ui/PageShell.vue'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'
import TrackMap from '@/shared/ui/core/TrackMap.vue'

const router = useRouter()
const audio = useAudioStore()

const timer = ref(0)
let intervalId: number | null = null

const sectorTimes = ref([
  { id: 1, lap: 12, s1: '28.4', s2: '32.1', s3: '24.5', total: '1:25.0', state: 'purple' },
  { id: 2, lap: 13, s1: '28.5', s2: '32.0', s3: '24.6', total: '1:25.1', state: 'green' },
  { id: 3, lap: 14, s1: '28.8', s2: '32.4', s3: '24.8', total: '1:26.0', state: 'yellow' },
])

const carPos = ref(0)
const trackMapRef = ref<any>(null)
const activeTurnId = ref<number | null>(null)

const tick = () => {
  timer.value++
  carPos.value = (carPos.value + 1) % 100
  
  if (trackMapRef.value && trackMapRef.value.trackTurns) {
    const pt = trackMapRef.value.getPointAtProgress(carPos.value)
    let closest: any = null
    let minDist = Infinity
    trackMapRef.value.trackTurns.forEach((t: any) => {
      const dist = Math.hypot(t.cx - pt.x, t.cy - pt.y)
      if (dist < minDist) {
        minDist = dist
        closest = t
      }
    })
    // Highlight if within ~150 units of the SVG coordinate
    if (closest && minDist < 150) {
      activeTurnId.value = closest.id
    } else {
      activeTurnId.value = null
    }
  }
  
  if (timer.value % 20 === 0) {
    // simulate new lap
    const newLap = {
      id: timer.value,
      lap: sectorTimes.value[0].lap + 1,
      s1: (28 + Math.random()).toFixed(1),
      s2: (32 + Math.random()).toFixed(1),
      s3: (24 + Math.random()).toFixed(1),
      total: '1:25.x',
      state: Math.random() > 0.8 ? 'purple' : (Math.random() > 0.4 ? 'green' : 'yellow')
    }
    sectorTimes.value.unshift(newLap)
    if (sectorTimes.value.length > 8) sectorTimes.value.pop()
  }
}

onMounted(() => {
  intervalId = window.setInterval(tick, 200)
})

onUnmounted(() => {
  if (intervalId) clearInterval(intervalId)
})

useKeyboard((e: KeyboardEvent) => {
  if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.back()
  }
})

const getColorClass = (state: string) => {
  if (state === 'purple') return 'text-[#c084fc] font-bold'
  if (state === 'green') return 'text-ui-good'
  return 'text-ui-warn'
}
</script>

<template>
  <PageShell title="PIT WALL" :hints="['B · BACK', 'LIVE TELEMETRY ACTIVE']" bg="neutral">
    
    <div class="absolute inset-0 pointer-events-none bg-[radial-gradient(ellipse_at_center,rgba(0,0,0,0)_0%,rgba(0,0,0,0.8)_100%)] z-0"></div>

    <template #heading>
      <div class="heading-block mb-[1vh] flex justify-between items-end relative z-10">
        <h1 class="text-title font-title text-silver tracking-[0.2em]">RACE ENGINEER</h1>
        <div class="text-ui-warn font-bold text-small animate-pulse border border-ui-warn px-2 rounded">LIVE</div>
      </div>
    </template>

    <div class="flex-grow flex mx-2 pb-6 gap-2 relative z-10 font-mono min-h-0">
      
      <!-- Left: Track Map & Driver Status -->
      <div class="w-1/3 flex flex-col gap-2 min-h-0">
        <CyberPanel class="flex-grow flex flex-col items-center justify-center bg-black border-slate relative overflow-hidden p-2">
          <div class="absolute top-2 left-2 text-silver text-small font-bold uppercase z-10">TRACK MAP</div>
          
          <TrackMap ref="trackMapRef" :carProgress="carPos" :activeTurnId="activeTurnId" />
        </CyberPanel>

        <CyberPanel class="h-32 shrink-0 bg-ink border-slate p-2 flex flex-col justify-between">
          <div class="flex justify-between items-baseline">
            <span class="text-slate font-bold">DRIVER</span>
            <span class="text-white">P1</span>
          </div>
          <div class="flex justify-between items-baseline">
            <span class="text-slate font-bold">TYRES</span>
            <span class="text-ui-warn">SOFT (6 LAPS)</span>
          </div>
          <div class="flex justify-between items-baseline">
            <span class="text-slate font-bold">FUEL</span>
            <span class="text-ui-good">+2.4 KG</span>
          </div>
        </CyberPanel>
      </div>

      <!-- Right: Sector Times Ticker -->
      <CyberPanel class="w-2/3 flex flex-col bg-ink border-slate p-0 overflow-hidden min-h-0">
        <div class="bg-charcoal flex text-silver font-bold text-small px-3 py-2 border-b border-slate sticky top-0 z-10">
          <div class="w-1/6">LAP</div>
          <div class="w-1/5 text-right">S1</div>
          <div class="w-1/5 text-right">S2</div>
          <div class="w-1/5 text-right">S3</div>
          <div class="w-auto ml-auto text-right">TOTAL</div>
        </div>
        
        <div class="flex-grow overflow-y-auto min-h-0 p-2">
          <div 
            v-for="lap in sectorTimes" 
            :key="lap.id"
            class="flex items-center px-2 py-3 border-b border-charcoal text-body transition-all"
            :class="lap.id === sectorTimes[0].id ? 'bg-charcoal/50 animate-pulse' : ''"
          >
            <div class="w-1/6 text-silver font-bold">L{{ lap.lap }}</div>
            <div class="w-1/5 text-right" :class="getColorClass(lap.state)">{{ lap.s1 }}</div>
            <div class="w-1/5 text-right" :class="getColorClass(lap.state)">{{ lap.s2 }}</div>
            <div class="w-1/5 text-right" :class="getColorClass(lap.state)">{{ lap.s3 }}</div>
            <div class="w-auto ml-auto text-right font-bold text-white">{{ lap.total }}</div>
          </div>
        </div>
      </CyberPanel>

    </div>
  </PageShell>
</template>
