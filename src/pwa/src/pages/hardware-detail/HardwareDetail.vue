<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import { useSaveStore } from '@/entities/save/model/saveStore'
import StatusBar from '@/widgets/status-bar/StatusBar.vue'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'
import CyberBox from '@/shared/ui/core/CyberBox.vue'
import HintBar from '@/widgets/hint-bar/HintBar.vue'

const router = useRouter()
const audio = useAudioStore()
const save = useSaveStore()

// Mock registry
const generateMockSignals = () => {
  const bases = [
    { name: 'rpm', unit: 'rpm', hz: 10.0, group: 'motion', dbc: 'pitwall', baseValue: 3000, variance: 500, type: 'int' },
    { name: 'speed_ms', unit: 'm/s', hz: 10.0, group: 'motion', dbc: 'pitwall', baseValue: 20, variance: 2, type: 'float' },
    { name: 'g_lat', unit: 'g', hz: 10.0, group: 'motion', dbc: 'pitwall', baseValue: 0.05, variance: 0.1, type: 'float' },
    { name: 'g_long', unit: 'g', hz: 10.0, group: 'motion', dbc: 'pitwall', baseValue: -0.1, variance: 0.2, type: 'float' },
    { name: 'brake_bar', unit: 'bar', hz: 10.0, group: 'driver', dbc: 'pitwall', baseValue: 5.0, variance: 20, type: 'float' },
    { name: 'throttle_pct', unit: '%', hz: 10.0, group: 'driver', dbc: 'pitwall', baseValue: 40, variance: 15, type: 'float' },
    { name: 'steering_deg', unit: 'deg', hz: 10.0, group: 'driver', dbc: 'pitwall', baseValue: 12, variance: 5, type: 'float' },
    { name: 'oil_temp_c', unit: 'C', hz: 2.0, group: 'power', dbc: 'bmw_e46', baseValue: 94.0, variance: 1, type: 'float' },
    { name: 'coolant_temp_c', unit: 'C', hz: 2.0, group: 'power', dbc: 'bmw_e46', baseValue: 88.0, variance: 0.5, type: 'float' },
    { name: 'clutch_pos_pct', unit: '%', hz: 50.0, group: 'drive', dbc: 'bmw_e46', baseValue: 0, variance: 0, type: 'float' },
    { name: 'tpms_fl_kpa', unit: 'kPa', hz: 1.0, group: 'tires', dbc: 'bmw_e46', baseValue: 230, variance: 2, type: 'float' },
    { name: 'tpms_fr_kpa', unit: 'kPa', hz: 1.0, group: 'tires', dbc: 'bmw_e46', baseValue: 231, variance: 2, type: 'float' },
    { name: 'tpms_rl_kpa', unit: 'kPa', hz: 1.0, group: 'tires', dbc: 'bmw_e46', baseValue: 228, variance: 2, type: 'float' },
    { name: 'tpms_rr_kpa', unit: 'kPa', hz: 1.0, group: 'tires', dbc: 'bmw_e46', baseValue: 229, variance: 2, type: 'float' },
    { name: 'afr_lambda', unit: 'ratio', hz: 5.0, group: 'power', dbc: 'bmw_e46', baseValue: 0.98, variance: 0.05, type: 'float' },
    { name: 'wheel_speed_fl', unit: 'm/s', hz: 10.0, group: 'motion', dbc: 'bmw_e46', baseValue: 20, variance: 2, type: 'float' },
    { name: 'wheel_speed_fr', unit: 'm/s', hz: 10.0, group: 'motion', dbc: 'bmw_e46', baseValue: 20, variance: 2, type: 'float' },
    { name: 'wheel_speed_rl', unit: 'm/s', hz: 10.0, group: 'motion', dbc: 'bmw_e46', baseValue: 20.2, variance: 2, type: 'float' },
    { name: 'wheel_speed_rr', unit: 'm/s', hz: 10.0, group: 'motion', dbc: 'bmw_e46', baseValue: 20.1, variance: 2, type: 'float' },
    { name: 'gear', unit: 'n', hz: 2.0, group: 'drive', dbc: 'bmw_e46', baseValue: 3, variance: 0, type: 'int' },
    { name: 'battery_v', unit: 'V', hz: 1.0, group: 'power', dbc: 'bmw_e46', baseValue: 14.1, variance: 0.1, type: 'float' },
  ]
  return bases.map((b, i) => ({ ...b, id: i, last: b.baseValue }))
}

const signals = ref(generateMockSignals())
const unknownIds = ref([
  { id: '0x4F1', hz: 3 },
  { id: '0x523', hz: 1 },
  { id: '0x6A0', hz: 8 }
])

const page = ref(0)
const rowsPerPage = 10
const totalPages = computed(() => Math.ceil(signals.value.length / rowsPerPage))

const cursorIndex = ref(0)

const drilling = ref<typeof signals.value[0] | null>(null)

const paginatedSignals = computed(() => {
  const start = page.value * rowsPerPage
  return signals.value.slice(start, start + rowsPerPage)
})

let liveTimer: number | null = null

const updateLiveValues = () => {
  signals.value = signals.value.map(s => {
    // Only update sometimes based on hz to look more organic
    if (Math.random() < s.hz / 20) {
      let v = s.baseValue + (Math.random() * s.variance * 2) - s.variance
      if (v < 0 && s.name.includes('tpms')) v = s.baseValue
      if (v < 0 && s.name.includes('rpm')) v = 0
      
      const newV = s.type === 'int' ? Math.round(v) : Number(v.toFixed(2))
      return { ...s, last: newV }
    }
    return s
  })
}

const handleKey = (e: KeyboardEvent) => {
  if (drilling.value) {
    if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
      audio.playSfx('cancel')
      drilling.value = null
    }
    return
  }

  if (e.key === 'ArrowDown') {
    if (cursorIndex.value < paginatedSignals.value.length - 1) {
      cursorIndex.value++
      audio.playSfx('cursor_move')
    } else if (page.value < totalPages.value - 1) {
      page.value++
      cursorIndex.value = 0
      audio.playSfx('cursor_move')
    }
  } else if (e.key === 'ArrowUp') {
    if (cursorIndex.value > 0) {
      cursorIndex.value--
      audio.playSfx('cursor_move')
    } else if (page.value > 0) {
      page.value--
      cursorIndex.value = rowsPerPage - 1
      audio.playSfx('cursor_move')
    }
  } else if (e.key === 'ArrowRight') {
    if (page.value < totalPages.value - 1) {
      page.value++
      cursorIndex.value = 0
      audio.playSfx('cursor_move')
    }
  } else if (e.key === 'ArrowLeft') {
    if (page.value > 0) {
      page.value--
      cursorIndex.value = 0
      audio.playSfx('cursor_move')
    }
  } else if (e.key === 'Enter' || e.key === 'a') {
    audio.playSfx('cursor_select')
    drilling.value = paginatedSignals.value[cursorIndex.value]
  } else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.back()
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKey)
  liveTimer = window.setInterval(updateLiveValues, 200) // 5Hz UI update
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKey)
  if (liveTimer) window.clearInterval(liveTimer)
})

// Histogram logic
const histBars = [' ', '▂', '▃', '▄', '▅', '▆', '▇', '█']
const drillHist = ref('')
let histTimer: number | null = null

const generateHist = () => {
  let s = ''
  for(let i=0; i<30; i++) {
    s += histBars[Math.floor(Math.random() * histBars.length)]
  }
  drillHist.value = s
}

// Watch drilling to start/stop histogram
watch(drilling, (n) => {
  if (n) {
    generateHist()
    histTimer = window.setInterval(generateHist, 500)
  } else {
    if (histTimer) window.clearInterval(histTimer)
  }
})

</script>

<template>
  <div class="viewport pixelated relative w-full h-full bg-ink text-silver overflow-hidden  font-ui">
    <StatusBar />
    
    <div class="page-bg"></div>
    
    <div class="content pt-[6vh] px-2 flex flex-col h-full z-0 relative gap-2">
      <div class="heading-block mb-[1.5vh]">
        <h1 class="text-title font-title text-silver tracking-[0.2em]">HARDWARE DETAIL</h1>
        <span class="text-body text-slate">page {{ page + 1 }} of {{ totalPages }}</span>
      </div>

      <div class="flex gap-2 flex-grow min-h-0 mx-2 pb-6">
        
        <!-- Main Table -->
        <CyberPanel class="flex flex-col text-body overflow-hidden p-2 flex-grow">
          <div class="flex justify-between mb-2">
            <div class="text-silver font-bold uppercase">Signals</div>
          </div>
          
          <table class="w-full text-left font-mono">
            <thead>
              <tr class="text-slate border-b border-charcoal">
                <th class="pb-1 w-4"></th>
                <th class="pb-1">NAME</th>
                <th class="pb-1 text-right">UNIT</th>
                <th class="pb-1 text-right">Hz</th>
                <th class="pb-1 text-right">LAST</th>
                <th class="pb-1 pl-4">GROUP</th>
                <th class="pb-1">DBC</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(s, i) in paginatedSignals" :key="s.id"
                  :class="cursorIndex === i ? 'bg-charcoal text-white' : 'text-silver'">
                <td class="text-ui-good">{{ cursorIndex === i ? '▶' : '' }}</td>
                <td class="font-bold truncate max-w-[clamp(60px,15vw,120px)]">{{ s.name }}</td>
                <td class="text-right text-slate">{{ s.unit }}</td>
                <td class="text-right">{{ s.hz.toFixed(1) }}</td>
                <td class="text-right font-bold w-[40px]">{{ s.last }}</td>
                <td class="pl-4 text-slate truncate max-w-[clamp(48px,12vw,80px)]">{{ s.group }}</td>
                <td class="truncate max-w-[clamp(48px,12vw,80px)]">{{ s.dbc }}</td>
              </tr>
            </tbody>
          </table>
        </CyberPanel>

        <!-- Unknown CAN IDs -->
        <CyberPanel class="flex flex-col text-body p-2 w-[clamp(80px,20vw,160px)] shrink-0 overflow-hidden">
          <div class="text-silver font-bold uppercase mb-2 border-b border-charcoal pb-1">Unknown CAN IDs</div>
          <div class="flex flex-col gap-1 font-mono">
            <div v-for="u in unknownIds" :key="u.id" class="flex flex-col text-slate">
              <div class="flex justify-between text-white">
                <span>{{ u.id }}</span>
                <span>{{ u.hz }} Hz</span>
              </div>
              <span class="text-small">(no DBC entry)</span>
            </div>
          </div>
        </CyberPanel>

      </div>
    </div>
    
    <!-- Drill-In Modal -->
    <div v-if="drilling" class="absolute inset-0 bg-black/80 z-50 flex items-center justify-center font-ui text-body text-silver">
      <CyberBox variant="ink" border="slate" class="w-[clamp(280px,85vw,500px)] shadow-xl flex flex-col p-4 relative pixelated">
        <div class="text-body font-bold text-white uppercase border-b border-slate pb-1 mb-2">
          SIGNAL · {{ drilling.name }}
        </div>
        
        <div class="flex gap-4">
          <table class="w-1/2 text-left">
            <tr><td class="text-slate font-bold pb-1 w-[clamp(48px,12vw,80px)]">UNITS</td><td class="text-white pb-1">{{ drilling.unit }}</td></tr>
            <tr><td class="text-slate font-bold pb-1">SEMANTICS</td><td class="text-white pb-1">variable</td></tr>
            <tr><td class="text-slate font-bold pb-1">GROUP</td><td class="text-white pb-1">{{ drilling.group }}</td></tr>
            <tr><td class="text-slate font-bold pb-1">EXPECTED</td><td class="text-white pb-1">{{ drilling.hz }} Hz</td></tr>
            <tr><td class="text-slate font-bold pb-1">DISCOVERY</td><td class="text-white pb-1">static_obd2</td></tr>
            <tr><td class="text-slate font-bold">DBC</td><td class="text-white">{{ drilling.dbc }}</td></tr>
          </table>
          
          <CyberBox variant="charcoal" border="none" class="w-1/2 flex flex-col border border-charcoal p-2">
            <div class="text-slate font-bold mb-1">LAST 50 SAMPLES (live)</div>
            <div class="font-mono text-ui-good text-title leading-none mb-2 overflow-hidden whitespace-nowrap">{{ drillHist }}</div>
            <div class="flex justify-between text-small">
              <span class="text-slate">MIN <span class="text-white font-bold">{{ (drilling.baseValue - drilling.variance).toFixed(1) }}</span></span>
              <span class="text-slate">AVG <span class="text-white font-bold">{{ drilling.baseValue.toFixed(1) }}</span></span>
              <span class="text-slate">MAX <span class="text-white font-bold">{{ (drilling.baseValue + drilling.variance).toFixed(1) }}</span></span>
            </div>
          </CyberBox>
        </div>
        
        <div class="mt-4 text-small text-slate text-center pt-2 border-t border-charcoal">B · CLOSE</div>
      </CyberBox>
    </div>
    
    <HintBar :hints="['A · DRILL IN', 'B · BACK', '◀ ▶ PAGE', '◆ ADD DBC']" />
  </div>
</template>

<style scoped>
/* No hardcoded viewport dimensions — fullscreen is enforced by global.css */
</style>
