<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useSaveStore } from '@/entities/save/model/saveStore'
import { useDuckDBStore } from '@/shared/lib/duckdb/duckdbStore'
import StatusBar from '@/widgets/status-bar/StatusBar.vue'
import HintBar from '@/widgets/hint-bar/HintBar.vue'
import Sprite from '@/entities/coach/Sprite.vue'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'

const router = useRouter()
const saveStore = useSaveStore()
const duckDB = useDuckDBStore()

const save = saveStore.slots[saveStore.activeSlotId! - 1]
const hints = ['▲ ▼ ◀ ▶ NAVIGATE', 'A · MEDALS', '◆ EVOLUTION', 'B · GARAGE']

const totalSessions = ref(save?.sessions.length ?? 0)
const bestLapS = ref<number | null>(null)
const view = ref<'idle' | 'medals' | 'evolution'>('idle')
const loading = ref(true)

onMounted(async () => {
  window.addEventListener('keydown', handleKey)
  
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

onUnmounted(() => {
  window.removeEventListener('keydown', handleKey)
})

const handleKey = (e: KeyboardEvent) => {
  if (e.key === 'Escape' || e.key === 'Backspace') {
    if (view.value !== 'idle') {
      view.value = 'idle'
    } else {
      router.push('/garage')
    }
  } else if (e.key === 'Enter') {
    view.value = 'medals'
  }
}

const formatLap = (s: number | null) => {
  if (s === null) return '--:--.-'
  const min = Math.floor(s / 60)
  const sec = (s % 60).toFixed(1)
  return `${min}:${sec.padStart(4, '0')}`
}
</script>

<template>
  <div class="viewport pixelated relative w-full h-full bg-ink text-silver overflow-hidden">
    <StatusBar />
    
    <div class="trainer-bg absolute inset-0 z-0"></div>
    
    <div class="content flex flex-col h-full pt-[6vh] pb-[6vh] px-[3vw] relative z-10" v-if="save">
      <!-- Heading -->
      <div class="heading-block mb-[2vh] text-center">
        <h1 class="text-title font-title text-silver tracking-[0.3em]">TRAINER CARD</h1>
        <div class="heading-rule"></div>
      </div>
      
      <!-- Card -->
      <CyberPanel class="w-full flex gap-[3vw]">
        <div class="avatar-box">
          <Sprite sheet="avatars" animation="idle" />
        </div>
        
        <div class="card-meta text-body flex flex-col justify-center gap-[0.4vh]">
          <div class="text-ui-good text-title  font-bold">{{ save.driverName }}</div>
          <div class="text-silver">LV. {{ save.level }} · {{ save.skillLevel.toUpperCase() }}</div>
          <div class="text-silver">{{ totalSessions }} TRACK SESSIONS</div>
          <div class="text-ui-warn  mt-[0.5vh]">★ BEST {{ formatLap(bestLapS) }}</div>
        </div>
      </CyberPanel>
      
      <!-- Subview -->
      <div class="sub-view">
        <div v-if="loading" class="text-body animate-pulse text-slate">LOADING ANALYTICS...</div>
        <div v-else-if="view === 'idle'" class="text-body text-slate text-center">
          SKILL RADAR PLACEHOLDER
        </div>
        <div v-else-if="view === 'medals'" class="text-body text-slate text-center">
          MEDAL GRID PLACEHOLDER
        </div>
      </div>
      
      <!-- Coach sprite -->
      <Sprite :sheet="save.preferredCoach" animation="idle" class="absolute bottom-[8vh] right-[4vw]" />
    </div>
    
    <HintBar :hints="hints" />
  </div>
</template>

<style scoped>
.trainer-bg {
  background-color: var(--color-asphalt-deep);
}

.heading-block { text-align: center; }
.heading-rule {
  width: clamp(40px, 12vw, 120px);
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--color-slate), transparent);
  margin: clamp(4px, 1vmin, 10px) auto 0;
}

.avatar-box {
  width: clamp(48px, 12vmin, 80px);
  height: clamp(48px, 12vmin, 80px);
  border: 1px solid var(--color-slate);
  background: var(--color-ink);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.sub-view {
  margin-top: clamp(8px, 2vh, 20px);
  flex: 1;
  border: 1px solid var(--color-slate);
  background: linear-gradient(180deg, rgba(42, 47, 66, 0.5) 0%, rgba(31, 34, 48, 0.7) 100%);
  padding: clamp(6px, 1.5vmin, 16px);
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
