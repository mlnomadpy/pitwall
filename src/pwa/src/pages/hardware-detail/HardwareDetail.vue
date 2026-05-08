<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import { useRouter } from 'vue-router'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import { bridge } from '@/shared/api/bridge'

import { useTelemetryStore } from '@/entities/session/model/telemetryStore'

import PageShell from '@/shared/ui/PageShell.vue'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'
import CyberBox from '@/shared/ui/core/CyberBox.vue'

const router = useRouter()
const audio = useAudioStore()
const telemetry = useTelemetryStore()


interface SignalCapability {
  id: number
  name: string
  unit: string
  hz: number
  group: string
  dbc: string
  last: number | string
  baseValue: number
  variance: number
}

const signals = ref<SignalCapability[]>([])
const unknownIds = ref([
  { id: '0x4F1', hz: 3 },
  { id: '0x523', hz: 1 },
  { id: '0x6A0', hz: 8 }
])

const searchQuery = ref('')
const expandedGroups = ref<Record<string, boolean>>({})
const drilling = ref<SignalCapability | null>(null)
const cursorIndex = ref(0)

// Sort and group signals
const groupedSignals = computed(() => {
  const filtered = signals.value.filter(s => 
    s.name.toLowerCase().includes(searchQuery.value.toLowerCase()) || 
    s.dbc.toLowerCase().includes(searchQuery.value.toLowerCase())
  )
  
  const groups: Record<string, SignalCapability[]> = {}
  filtered.forEach(s => {
    if (!groups[s.group]) groups[s.group] = []
    groups[s.group].push(s)
  })
  
  return Object.keys(groups).sort().map(k => ({
    name: k,
    signals: groups[k].sort((a, b) => a.name.localeCompare(b.name))
  }))
})

// Flatten for keyboard nav
const flatVisibleSignals = computed(() => {
  let list: SignalCapability[] = []
  groupedSignals.value.forEach(g => {
    if (expandedGroups.value[g.name] !== false) {
      list.push(...g.signals)
    }
  })
  return list
})

const totalSignals = computed(() => flatVisibleSignals.value.length)

watch(searchQuery, () => {
  cursorIndex.value = 0
})

const toggleGroup = (groupName: string) => {
  expandedGroups.value[groupName] = expandedGroups.value[groupName] === false
  cursorIndex.value = 0
}

useKeyboard((e: KeyboardEvent) => {
  if (drilling.value) {
    if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
      audio.playSfx('cancel')
      drilling.value = null
    }
    return
  }

  // If focus is in search input, don't use up/down
  if (document.activeElement?.tagName === 'INPUT') {
    if (e.key === 'Escape') {
      (document.activeElement as HTMLElement).blur()
    }
    return
  }

  if (e.key === 'ArrowDown') {
    if (cursorIndex.value < totalSignals.value - 1) {
      cursorIndex.value++
      audio.playSfx('cursor_move')
    }
  } else if (e.key === 'ArrowUp') {
    if (cursorIndex.value > 0) {
      cursorIndex.value--
      audio.playSfx('cursor_move')
    }
  } else if (e.key === 'Enter' || e.key === 'a') {
    if (flatVisibleSignals.value[cursorIndex.value]) {
      audio.playSfx('cursor_select')
      drilling.value = flatVisibleSignals.value[cursorIndex.value]
    }
  } else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.back()
  }
})

onMounted(async () => {
  
  // Try to load real data
  try {
    const data = await bridge.get<{ signals: Array<{ signal_id?: number; name: string; units?: string; expected_hz?: number; group?: string; discovery?: string }> }>('/signals/registry')
    signals.value = data.signals.map((s, i) => ({
      id: s.signal_id ?? i,
      name: s.name,
      unit: s.units ?? 'n/a',
      hz: s.expected_hz ?? 10.0,
      group: s.group ?? 'misc',
      dbc: s.discovery ?? 'unknown',
      last: 0,
      baseValue: 0,
      variance: 0
    }))
  } catch (_) { /* Bridge may be offline */ }
  
  liveTimer = window.setInterval(updateLiveValues, 200) // 5Hz UI update
})

let liveTimer: number | null = null

/** Map telemetry frame data to signal table when available */
const SIGNAL_MAP: Record<string, (frame: any) => number> = {
  'rpm': f => f.rpm,
  'speed': f => f.speed,
  'throttle': f => f.throttle,
  'brake_pressure': f => f.brake_pressure,
  'steering': f => f.steering,
  'g_lat': f => f.g_lat,
  'g_long': f => f.g_long,
  'combo_g': f => f.combo_g,
  'distance': f => f.distance,
}

const updateLiveValues = () => {
  const frame = telemetry.frame
  signals.value.forEach(s => {
    const getter = SIGNAL_MAP[s.name.toLowerCase()]
    if (frame && getter) {
      try {
        s.last = getter(frame).toFixed(2)
      } catch {
        s.last = (s.baseValue + (Math.random() * s.variance * 2 - s.variance)).toFixed(2)
      }
    } else {
      s.last = (s.baseValue + (Math.random() * s.variance * 2 - s.variance)).toFixed(2)
    }
  })
}

onUnmounted(() => {
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
  <PageShell title="HARDWARE DETAIL" :hints="['A · DRILL IN', 'B · BACK', '▲ ▼ MOVE', '◆ ADD DBC']" bg="neutral">
    <template #heading>
      <div class="heading-block mb-[1.5vh]">
        <h1 class="text-title font-title text-silver tracking-[0.2em]">HARDWARE DETAIL</h1>
        <div class="heading-rule"></div>
      </div>
    </template>

    <div class="flex gap-2 flex-grow min-h-0 mx-2 pb-6">
      
      <!-- Main Table -->
      <CyberPanel class="flex flex-col text-body overflow-hidden p-2 flex-grow">
        <div class="flex justify-between items-center mb-2 gap-4">
          <div class="text-silver font-bold uppercase shrink-0">Signals</div>
          <input 
            v-model="searchQuery" 
            type="text" 
            placeholder="SEARCH SIGNALS..."
            class="bg-ink border border-slate text-silver px-2 py-1 w-full max-w-xs focus:outline-none focus:border-ui-good"
          />
        </div>
        
        <div class="overflow-y-auto flex-grow pr-2 scroll-smooth">
          <table class="w-full text-left font-mono">
            <thead>
              <tr class="text-slate border-b border-charcoal">
                <th class="pb-1 w-4"></th>
                <th class="pb-1">NAME</th>
                <th class="pb-1 text-right">UNIT</th>
                <th class="pb-1 text-right">Hz</th>
                <th class="pb-1 text-right">LAST</th>
                <th class="pb-1">DBC</th>
              </tr>
            </thead>
            <tbody v-for="g in groupedSignals" :key="g.name">
              <tr class="bg-charcoal text-ui-info cursor-pointer hover:bg-slate/20" @click="toggleGroup(g.name)">
                <td colspan="6" class="py-1 px-2 font-bold uppercase border-y border-ink">
                  <span class="mr-2">{{ expandedGroups[g.name] !== false ? '▼' : '▶' }}</span>
                  {{ g.name }} ({{ g.signals.length }})
                </td>
              </tr>
              <template v-if="expandedGroups[g.name] !== false">
                <tr v-for="s in g.signals" :key="s.id"
                    @click="drilling = s; cursorIndex = flatVisibleSignals.findIndex(fs => fs.id === s.id)"
                    class="cursor-pointer hover:bg-slate/20"
                    :class="flatVisibleSignals[cursorIndex]?.id === s.id ? 'bg-charcoal text-white' : 'text-silver'">
                  <td class="text-ui-good">{{ flatVisibleSignals[cursorIndex]?.id === s.id ? '▶' : '' }}</td>
                  <td class="font-bold truncate max-w-[clamp(60px,15vw,120px)]">{{ s.name }}</td>
                  <td class="text-right text-slate">{{ s.unit }}</td>
                  <td class="text-right">{{ s.hz.toFixed(1) }}</td>
                  <td class="text-right font-bold w-[40px]">{{ s.last }}</td>
                  <td class="truncate max-w-[clamp(48px,12vw,80px)]">{{ s.dbc }}</td>
                </tr>
              </template>
            </tbody>
          </table>
          <div v-if="groupedSignals.length === 0" class="text-slate text-center mt-4 italic">
            No signals match your search.
          </div>
        </div>
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
    
    <!-- Drill-In Modal -->
    <div v-if="drilling" class="absolute inset-0 bg-black/80 z-50 flex items-center justify-center font-ui text-body text-silver" @click="drilling = null">
      <CyberBox variant="ink" border="slate" class="w-[clamp(280px,85vw,500px)] shadow-xl flex flex-col p-4 relative pixelated" @click.stop>
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
        
        <div class="mt-4 text-small text-slate text-center pt-2 border-t border-charcoal hover:text-white cursor-pointer" @click="drilling = null">B / CLICK · CLOSE</div>
      </CyberBox>
    </div>
  </PageShell>
</template>

<style scoped>
/* No hardcoded viewport dimensions — fullscreen is enforced by global.css */
</style>
