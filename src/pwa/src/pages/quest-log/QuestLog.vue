<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useSaveStore } from '@/entities/save/model/saveStore'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import StatusBar from '@/widgets/status-bar/StatusBar.vue'
import HintBar from '@/widgets/hint-bar/HintBar.vue'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'
import CyberBox from '@/shared/ui/core/CyberBox.vue'
import CyberTabs from '@/shared/ui/core/CyberTabs.vue'
import DialogueBox from '@/widgets/dialogue-box/DialogueBox.vue'
import CoachCodexMode from './ui/CoachCodexMode.vue'
import CyberButton from '@/shared/ui/core/CyberButton.vue'

const router = useRouter()
const save = useSaveStore()
const audio = useAudioStore()

const tabs = ['ALL', 'BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'RAINBOW']
const activeTab = ref(0)
const cursorIndex = ref(0)
const detailText = ref<string | null>(null)

const mode = ref<'MEDALS' | 'CODEX'>('MEDALS')
const codexRef = ref<InstanceType<typeof CoachCodexMode> | null>(null)

// Dummy medal data based on spec
const allMedals = Array.from({ length: 40 }).map((_, i) => ({
  id: `medal_${i}`,
  tier: i < 5 ? 'BRONZE' : i < 20 ? 'SILVER' : i < 35 ? 'GOLD' : i < 39 ? 'PLATINUM' : 'RAINBOW',
  name: `Medal ${i}`,
  desc: `Acquisition criteria for medal ${i}.`,
  unlocked: Math.random() > 0.6
}))

const filteredMedals = computed(() => {
  if (tabs[activeTab.value] === 'ALL') return allMedals
  return allMedals.filter(m => m.tier === tabs[activeTab.value])
})

const handleKey = (e: KeyboardEvent) => {
  if (detailText.value) return 

  if (e.key === 'c' || e.key === 'C') {
    mode.value = mode.value === 'MEDALS' ? 'CODEX' : 'MEDALS'
    audio.playSfx('cursor_select')
    return
  }

  if (mode.value === 'CODEX') {
    if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
      audio.playSfx('cancel')
      router.push('/garage')
      return
    }
    codexRef.value?.handleKey(e)
    return
  }

  const max = filteredMedals.value.length

  if (e.key === 'ArrowRight') {
    if (e.shiftKey) {
      activeTab.value = (activeTab.value + 1) % tabs.length
      cursorIndex.value = 0
      audio.playSfx('cursor_select')
    } else {
      cursorIndex.value = (cursorIndex.value + 1) % max
      audio.playSfx('cursor_move')
    }
  } else if (e.key === 'ArrowLeft') {
    if (e.shiftKey) {
      activeTab.value = (activeTab.value - 1 + tabs.length) % tabs.length
      cursorIndex.value = 0
      audio.playSfx('cursor_select')
    } else {
      cursorIndex.value = (cursorIndex.value - 1 + max) % max
      audio.playSfx('cursor_move')
    }
  } else if (e.key === 'ArrowDown') {
    cursorIndex.value = (cursorIndex.value + 8) % max
    audio.playSfx('cursor_move')
  } else if (e.key === 'ArrowUp') {
    cursorIndex.value = (cursorIndex.value - 8 + max) % max
    audio.playSfx('cursor_move')
  } else if (e.key === 'Enter') {
    const m = filteredMedals.value[cursorIndex.value]
    if (m?.unlocked) {
      audio.playSfx('cursor_select')
      detailText.value = m.desc
    } else {
      audio.playSfx('cancel')
      detailText.value = "Keep driving to unlock this."
    }
  } else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.push('/garage')
  }
}

const onCodexPlay = (phrase: any) => {
  detailText.value = phrase.text
}

const switchTab = (i: number) => {
  activeTab.value = i
  cursorIndex.value = 0
  audio.playSfx('cursor_select')
}

onMounted(() => {
  window.addEventListener('keydown', handleKey)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKey)
})
</script>

<template>
  <div class="viewport pixelated relative w-full h-full bg-ink text-silver overflow-hidden font-ui">
    <StatusBar />
    
    <div class="quest-bg absolute inset-0 z-0"></div>
    
    <div class="content pt-[6vh] pb-[6vh] px-[2vw] h-full flex flex-col gap-[1vh] relative z-10">
      <!-- Header -->
      <div class="quest-header flex items-center justify-between mb-2">
        <div class="heading-block">
          <h1 class="text-title font-title text-silver pixel-shadow tracking-[0.2em]">
            {{ mode === 'MEDALS' ? 'QUEST LOG' : 'COACH CODEX' }}
          </h1>
          <div class="heading-rule"></div>
        </div>
        <CyberButton size="sm" variant="info" @click="mode = mode === 'MEDALS' ? 'CODEX' : 'MEDALS'">
          <template #icon>
            <span class="mr-1 text-charcoal">C ·</span>
          </template>
          {{ mode === 'MEDALS' ? 'CODEX' : 'MEDALS' }}
        </CyberButton>
      </div>
      
      <template v-if="mode === 'MEDALS'">
        <!-- Active Goals -->
        <CyberPanel variant="glass" border="primary" class="goals-frame">
          <h2 class="section-label">ACTIVE GOALS (THIS SESSION)</h2>
          <div class="goal-row text-ui-info">
            <span class="goal-icon">◐</span>
            <span class="goal-name">APEX SPEED AT T7</span>
            <span class="goal-val">82 → 84 km/h (target +3)</span>
          </div>
          <div class="goal-row text-ui-good">
            <span class="goal-icon">✓</span>
            <span class="goal-name">BREAK 1:48</span>
            <span class="goal-val">1:46.8 ✓</span>
          </div>
          <div class="goal-row text-ui-bad">
            <span class="goal-icon">✗</span>
            <span class="goal-name">TRAIL EVERY ENTRY</span>
            <span class="goal-val">4 of 11</span>
          </div>
        </CyberPanel>
        
        <!-- Kerb stripe divider -->
        <div class="kerb-stripe"></div>
        
        <!-- Medal tier tabs -->
        <CyberTabs :tabs="tabs" v-model="activeTab" @change="switchTab" class="mb-1" />
        
        <!-- Medal grid -->
        <CyberPanel variant="glass" border="secondary" class="medal-frame">
          <h2 class="section-label">
            MEDALS {{ allMedals.filter(m => m.unlocked).length }} / 40
          </h2>
          
          <div class="grid grid-cols-8 gap-1 flex-1 overflow-y-auto content-start no-scrollbar">
            <CyberBox 
              v-for="(m, i) in filteredMedals" 
              :key="m.id"
              variant="ink"
              border="slate"
              :selected="cursorIndex === i"
              class="aspect-square flex items-center justify-center cursor-pointer transition-opacity"
              :class="[!m.unlocked ? 'opacity-35' : '']"
              @click="() => { cursorIndex = i; }"
            >
              <span v-if="m.unlocked" class="text-[clamp(14px,3.5vmin,28px)] text-ui-warn drop-shadow-[1px_1px_0_#0d0d12]">★</span>
              <span v-else class="text-[clamp(10px,2.5vmin,20px)] text-slate">?</span>
            </CyberBox>
          </div>
          
          <!-- Selected medal name -->
          <div class="medal-preview">
            <span class="text-ui-good mr-[4px]">▶</span>
            {{ filteredMedals[cursorIndex]?.unlocked ? filteredMedals[cursorIndex]?.name : '???' }}
          </div>
        </CyberPanel>
      </template>

      <CoachCodexMode ref="codexRef" :active="mode === 'CODEX'" @play="onCodexPlay" />
    </div>
    
    <DialogueBox 
      v-if="detailText"
      :coach-id="save.slots[save.activeSlotId?-1:0]?.preferredCoach ?? 'trod'"
      emotion="idle"
      :text="detailText"
      @done="detailText = null"
      class="absolute bottom-[6vh] left-0 right-0 z-20"
    />
    
    <HintBar :hints="mode === 'MEDALS' ? ['A · DETAIL', 'SHIFT+◀ ▶ TAB', 'C · CODEX', 'B · GARAGE'] : ['A · PLAY', 'SHIFT+◀ ▶ COACH', 'C · QUESTS', 'B · GARAGE']" />
  </div>
</template>

<style scoped>
.quest-bg {
  background-color: var(--color-asphalt-deep);
}

.quest-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.mode-toggle {
  font-family: var(--font-ui);
  font-size: clamp(10px, 2.3vmin, 20px);
  color: #f5f5e8;
  font-weight: bold;
  background: var(--color-charcoal);
  border: 2px solid var(--color-slate);
  padding: clamp(3px, 0.6vh, 8px) clamp(8px, 2vw, 16px);
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
  box-shadow: 2px 2px 0 0 #0d0d12;
}

.mode-toggle:active {
  box-shadow: none;
  transform: translate(2px, 2px);
}

.goals-frame { padding: clamp(6px, 1.5vmin, 14px); }

.goal-row {
  display: flex;
  align-items: center;
  gap: clamp(6px, 1.5vw, 14px);
  font-size: clamp(10px, 2.3vmin, 20px);
  margin-bottom: clamp(2px, 0.4vh, 5px);
}

.goal-icon { flex: 0 0 auto; font-size: clamp(11px, 2.5vmin, 22px); }
.goal-name { flex: 1; }
.goal-val { flex: 0 0 auto; text-align: right; font-weight: bold; }



.medal-frame {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.no-scrollbar::-webkit-scrollbar { display: none; }
.no-scrollbar { scrollbar-width: none; }

.medal-preview {
  font-size: clamp(10px, 2.3vmin, 20px);
  margin-top: clamp(4px, 0.8vh, 8px);
  padding-top: clamp(4px, 0.8vh, 8px);
  border-top: 2px solid var(--color-slate);
  color: var(--color-silver);
}
</style>

