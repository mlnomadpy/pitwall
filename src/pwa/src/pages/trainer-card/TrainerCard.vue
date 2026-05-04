<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import { useRouter } from 'vue-router'
import { useSaveStore } from '@/entities/save/model/saveStore'
import { useDuckDBStore } from '@/shared/lib/duckdb/duckdbStore'
import PageShell from '@/shared/ui/PageShell.vue'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'
import CyberSplitView from '@/shared/ui/core/CyberSplitView.vue'
import CyberTabs from '@/shared/ui/core/CyberTabs.vue'
import CyberAvatar from '@/shared/ui/core/CyberAvatar.vue'
import MedalGrid from '@/widgets/medal-grid/MedalGrid.vue'
import CyberRadarChart from '@/shared/ui/core/CyberRadarChart.vue'

// Dummy medal data
const allMedals = Array.from({ length: 40 }).map((_, i) => ({
  id: `medal_${i}`,
  tier: i < 5 ? 'BRONZE' : i < 20 ? 'SILVER' : i < 35 ? 'GOLD' : i < 39 ? 'PLATINUM' : 'RAINBOW',
  name: `Medal ${i}`,
  desc: `Acquisition criteria for medal ${i}.`,
  unlocked: Math.random() > 0.6
}))

// Radar stats
const driverStats = ref([
  { label: 'BRAKING', value: 85 },
  { label: 'CONSISTENCY', value: 72 },
  { label: 'APEX', value: 90 },
  { label: 'SPEED', value: 88 },
  { label: 'VISION', value: 65 }
])

const tabs = ['SKILLS', 'MEDALS']
const activeTab = ref(0)

const switchTab = (val: number) => {
  activeTab.value = val
}

const router = useRouter()
const saveStore = useSaveStore()
const duckDB = useDuckDBStore()

const save = saveStore.activeSlot
const hints = ['◀ ▶ TABS', 'B · GARAGE']

const totalSessions = ref(save?.sessions.length ?? 0)
const bestLapS = ref<number | null>(null)
const loading = ref(true)

onMounted(async () => {
  if (!duckDB.db) {
    try {
      await duckDB.init()
    } catch (e) {
      console.error('Failed to init DuckDB', e)
    }
  }
  
  if (save && Object.keys(save.bestLapBySession).length > 0) {
    bestLapS.value = Math.min(...Object.values(save.bestLapBySession))
  }
  
  loading.value = false
})

useKeyboard((e: KeyboardEvent) => {
  if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    router.push('/garage')
  } else if (e.key === 'ArrowRight' || e.key === 'ArrowLeft') {
    activeTab.value = activeTab.value === 0 ? 1 : 0
  }
})

const formatLap = (s: number | null) => {
  if (s === null) return '--:--.-'
  const min = Math.floor(s / 60)
  const sec = (s % 60).toFixed(1)
  return `${min}:${sec.padStart(4, '0')}`
}
</script>

<template>
  <PageShell title="TRAINER CARD" :hints="hints" bg="neutral">
    <template #heading>
      <div class="heading-block mb-[2vh] text-center">
        <h1 class="text-title font-title text-silver tracking-[0.3em]">TRAINER CARD</h1>
        <div class="heading-rule"></div>
      </div>
    </template>
    
    <div class="content flex flex-col h-full relative z-10 w-full pb-[6vh]" v-if="save">
      
      <CyberSplitView split="40-60" gap="md" class="h-full min-h-0">
        <!-- Left Pane: Profile -->
        <template #left>
          <CyberPanel class="h-full flex flex-col min-h-0 p-3">
            <div class="text-body text-silver mb-[1.5vh] border-b border-slate pb-[1vh] tracking-[0.1em]">
              DRIVER PROFILE
            </div>
            
            <div class="flex flex-col items-center flex-grow justify-center gap-[2vmin]">
              <CyberAvatar :sheet="save.driverAvatar || 'avatar_a'" size="lg" variant="glow" />
              
              <div class="text-center w-full px-2">
                <div class="text-ui-good text-[clamp(14px,3.5vmin,24px)] font-bold mb-1 font-title">{{ save.driverName }}</div>
                <div class="text-silver text-body mb-[2vmin]">LV. {{ save.level }} · {{ save.skillLevel.toUpperCase() }}</div>
                
                <div class="grid grid-cols-2 gap-y-2 gap-x-4 text-small border-t border-slate pt-[2vmin] mt-[1vmin] w-full text-left">
                  <div class="text-slate">SESSIONS:</div>
                  <div class="text-silver text-right">{{ totalSessions }}</div>
                  <div class="text-slate">COACH:</div>
                  <div class="text-silver text-right uppercase">{{ save.preferredCoach }}</div>
                  <div class="text-slate">BEST LAP:</div>
                  <div class="text-ui-warn text-right font-bold">{{ formatLap(bestLapS) }}</div>
                </div>
              </div>
            </div>
          </CyberPanel>
        </template>
        
        <!-- Right Pane: Tabs (Skills / Medals) -->
        <template #right>
          <div class="h-full flex flex-col min-h-0 w-full">
            <CyberTabs :tabs="tabs" v-model="activeTab" @change="switchTab" class="mb-[1.5vh]" />
            
            <CyberPanel variant="glass" border="secondary" class="flex-1 flex flex-col overflow-hidden min-h-0 p-3">
              <div v-if="loading" class="text-body animate-pulse text-slate w-full h-full flex items-center justify-center">
                LOADING ANALYTICS...
              </div>
              
              <template v-else>
                <!-- SKILLS TAB -->
                <div v-if="activeTab === 0" class="w-full h-full flex flex-col min-h-0">
                  <div class="text-small text-silver mb-2 border-b border-slate pb-1 flex justify-between flex-shrink-0">
                    <span>DRIVING STYLE ANALYSIS</span>
                    <span class="text-ui-info font-bold">BALANCED</span>
                  </div>
                  <div class="flex-grow flex items-center justify-center p-2 min-h-0">
                    <CyberRadarChart :stats="driverStats" />
                  </div>
                </div>
                
                <!-- MEDALS TAB -->
                <div v-if="activeTab === 1" class="w-full h-full flex flex-col min-h-0">
                  <div class="text-small text-silver mb-2 border-b border-slate pb-1 flex justify-between flex-shrink-0">
                    <span>MEDAL DATABASE</span>
                    <span class="text-ui-good font-bold">{{ allMedals.filter(m => m.unlocked).length }} / 40</span>
                  </div>
                  <MedalGrid 
                    :medals="allMedals" 
                    :cursorIndex="-1" 
                    @select="() => {}" 
                    class="flex-grow"
                  />
                </div>
              </template>
            </CyberPanel>
          </div>
        </template>
      </CyberSplitView>
      
    </div>
  </PageShell>
</template>

<style scoped>
.heading-block { text-align: center; }
</style>
