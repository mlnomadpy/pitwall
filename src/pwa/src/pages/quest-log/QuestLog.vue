<script setup lang="ts">
import { ref, computed } from 'vue'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import { useRouter } from 'vue-router'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import PageShell from '@/shared/ui/PageShell.vue'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'
import CyberSplitView from '@/shared/ui/core/CyberSplitView.vue'
import CyberTabs from '@/shared/ui/core/CyberTabs.vue'
import CoachFloat from '@/shared/ui/CoachFloat.vue'
import CoachCodexMode from './ui/CoachCodexMode.vue'
import CyberButton from '@/shared/ui/core/CyberButton.vue'
import SessionGoalsPanel from '@/widgets/session-goals/SessionGoalsPanel.vue'
import MedalGrid from '@/widgets/medal-grid/MedalGrid.vue'

const router = useRouter()
const audio = useAudioStore()

const tabs = ['ALL', 'BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'RAINBOW']
const activeTab = ref(0)
const cursorIndex = ref(0)
const detailText = ref<string | null>(null)

const mode = ref<'MEDALS' | 'CODEX'>('MEDALS')

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

useKeyboard((e: KeyboardEvent) => {
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
    return
  }

  const max = filteredMedals.value.length
  const COLS = 4 // Matched to grid-cols-4

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
    cursorIndex.value = (cursorIndex.value + COLS) % max
    audio.playSfx('cursor_move')
  } else if (e.key === 'ArrowUp') {
    cursorIndex.value = (cursorIndex.value - COLS + max) % max
    audio.playSfx('cursor_move')
  } else if (e.key === 'Enter') {
    viewDetails()
  } else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.push('/garage')
  }
})

const viewDetails = (index = cursorIndex.value) => {
  cursorIndex.value = index
  const m = filteredMedals.value[index]
  if (m?.unlocked) {
    audio.playSfx('cursor_select')
    detailText.value = m.desc
  } else {
    audio.playSfx('cancel')
    detailText.value = "Keep driving to unlock this."
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
</script>

<template>
  <PageShell :title="mode === 'MEDALS' ? 'QUEST LOG' : 'COACH CODEX'" :hints="mode === 'MEDALS' ? ['A · DETAIL', 'SHIFT+◀ ▶ TAB', 'C · CODEX', 'B · GARAGE'] : ['A · PLAY', 'SHIFT+◀ ▶ COACH', 'C · QUESTS', 'B · GARAGE']" bg="warm">
    <template #heading>
      <div class="quest-header flex items-center justify-between mb-2">
        <div class="heading-block">
          <h1 class="text-title font-title text-silver pixel-shadow tracking-[0.2em]">
            {{ mode === 'MEDALS' ? 'QUEST LOG' : 'COACH CODEX' }}
          </h1>
          <div class="heading-rule"></div>
        </div>
        <div class="flex gap-2">
          <CyberButton size="sm" variant="primary" @click="$router.push('/garage/sponsors')">
            <template #icon>
              <span class="mr-1 text-charcoal">S ·</span>
            </template>
            SPONSORS
          </CyberButton>
          <CyberButton size="sm" variant="info" @click="mode = mode === 'MEDALS' ? 'CODEX' : 'MEDALS'">
            <template #icon>
              <span class="mr-1 text-charcoal">C ·</span>
            </template>
            {{ mode === 'MEDALS' ? 'CODEX' : 'MEDALS' }}
          </CyberButton>
        </div>
      </div>
    </template>
    
    <div class="content h-full flex flex-col relative z-10">
      <template v-if="mode === 'MEDALS'">
        <!-- 2-Column Layout via CyberSplitView -->
        <CyberSplitView split="40-60" gap="sm" class="h-full">
          
          <!-- Left Column: Goals and Preview -->
          <template #left>
            <div class="flex flex-col gap-4 h-full">
              <!-- Active Goals -->
              <SessionGoalsPanel class="flex-shrink-0" />
              
              <!-- Medal Detail Panel -->
              <CyberPanel variant="solid" border="secondary" class="flex-1 flex flex-col justify-center">
                <h2 class="section-label mb-2 text-ui-warn">MEDAL INTEL</h2>
                <div class="medal-preview">
                  <span class="text-ui-good mr-[4px]">▶</span>
                  <span class="font-bold text-[clamp(14px,2.5vmin,22px)]">
                    {{ filteredMedals[cursorIndex]?.unlocked ? filteredMedals[cursorIndex]?.name : 'CLASSIFIED' }}
                  </span>
                  <p class="mt-2 text-slate text-[clamp(10px,2vmin,16px)]">
                    {{ filteredMedals[cursorIndex]?.unlocked ? 'Press A to view full acquisition criteria.' : 'Requirements unknown. Keep driving.' }}
                  </p>
                </div>
              </CyberPanel>
            </div>
          </template>
          
          <!-- Right Column: Tabs and Grid -->
          <template #right>
            <div class="flex flex-col h-full overflow-hidden">
              <!-- Medal tier tabs -->
              <CyberTabs :tabs="tabs" v-model="activeTab" @change="switchTab" class="mb-2" />
              
              <!-- Medal grid -->
              <CyberPanel variant="glass" border="secondary" class="flex-1 flex flex-col overflow-hidden min-h-0">
                <div class="flex justify-between items-end mb-2 border-b border-slate pb-1">
                  <h2 class="section-label m-0">MEDAL DATABASE</h2>
                  <span class="text-ui-good text-sm font-bold">{{ allMedals.filter(m => m.unlocked).length }} / 40 UNLOCKED</span>
                </div>
                
                <MedalGrid 
                  :medals="filteredMedals" 
                  :cursorIndex="cursorIndex" 
                  @select="viewDetails" 
                  class="flex-1"
                />
              </CyberPanel>
            </div>
          </template>
        </CyberSplitView>
      </template>

      <CoachCodexMode :active="mode === 'CODEX'" @play="onCodexPlay" class="flex-1" />
    </div>
    
    <template #floating>
      <CoachFloat 
        v-if="detailText"
        emotion="idle"
        :text="detailText"
        @done="detailText = null"
      />
    </template>
  </PageShell>
</template>

<style scoped>
.quest-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.section-label {
  font-family: var(--font-title);
  font-size: clamp(10px, 2.5vmin, 20px);
  color: var(--color-silver);
  letter-spacing: 0.1em;
}

.medal-preview {
  color: var(--color-silver);
}
</style>

