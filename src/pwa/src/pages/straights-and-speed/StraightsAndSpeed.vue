<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import PageShell from '@/shared/ui/PageShell.vue'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'
import CyberSplitView from '@/shared/ui/core/CyberSplitView.vue'
import CoachFloat from '@/shared/ui/CoachFloat.vue'

const router = useRouter()
const audio = useAudioStore()

// Mock data
const straights = [
  { id: 'front', name: 'FRONT STRAIGHT', speed: 198.4, lap: 7, delta: '+2.1', deltaType: 'up', note: '' },
  { id: 't4', name: 'T4 RUN', speed: 138.7, lap: 12, delta: '-1.0', deltaType: 'down', note: '(-cost on T3a exit)' },
  { id: 't7', name: 'T7 → T8a', speed: 187.2, lap: 11, delta: '0', deltaType: 'flat', note: '' },
]

const cursorIndex = ref(0)
const cur = computed(() => straights[cursorIndex.value])

const getCoachLine = () => {
  if (cur.value.deltaType === 'up') {
    return `You carried incredible momentum onto the ${cur.value.name}.`
  } else if (cur.value.deltaType === 'down') {
    return `${cur.value.name} dropped ${cur.value.delta.replace('-', '')} km/h — that's from the previous corner exit. Tighten it. Don't open up.`
  } else {
    return `Flat on the ${cur.value.name}. No gains, but no losses.`
  }
}

const getEmotion = () => {
  if (cur.value.deltaType === 'up') return 'victory'
  if (cur.value.deltaType === 'down') return 'talk'
  return 'idle'
}

useKeyboard((e: KeyboardEvent) => {
  if (e.key === 'ArrowDown') {
    cursorIndex.value = Math.min(cursorIndex.value + 1, straights.length - 1)
    
    if (straights[cursorIndex.value].deltaType === 'down') {
      audio.playSfx('error_quiet')
    } else {
      audio.playSfx('cursor_move')
    }
  } else if (e.key === 'ArrowUp') {
    cursorIndex.value = Math.max(cursorIndex.value - 1, 0)
    
    if (straights[cursorIndex.value].deltaType === 'down') {
      audio.playSfx('error_quiet')
    } else {
      audio.playSfx('cursor_move')
    }
  } else if (e.key === 'Enter' || e.key === 'a') {
    audio.playSfx('cursor_select')
    // Replay logic
  } else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.push('/garage/analysis')
  }
})
</script>

<template>
  <PageShell title="STRAIGHTS & SPEED" subtitle="session 2026-04-29-1503" :hints="['A · OPEN REPLAY (SOON)', '▲ ▼ MOVE', 'B · BACK']" bg="cool">
    <CyberSplitView split="60-40" gap="sm" class="flex-grow min-h-0">
      <template #left>
        <CyberPanel class="h-full flex flex-col text-body p-2 gap-2">
          <div 
            v-for="(s, i) in straights" 
            :key="s.id"
            class="p-2 border transition-colors flex flex-col gap-1"
            :class="cursorIndex === i ? 'border-ui-good bg-charcoal' : 'border-slate bg-ink'"
          >
            <div class="flex justify-between items-end">
              <div class="font-bold">
                <span v-if="cursorIndex === i" class="text-ui-good mr-1">▶</span>
                <span :class="cursorIndex === i ? 'text-white' : 'text-silver'">░░ {{ s.name }} ░░</span>
              </div>
              <div class="text-body text-silver">LAP {{ s.lap }}</div>
            </div>
            
            <div class="flex gap-4 items-center pl-4">
              <span class="text-title font-bold text-white">
                {{ s.speed }} km/h
              </span>
              
              <span class="text-body flex items-center gap-1"
                    :class="{
                      'text-ui-good': s.deltaType === 'up',
                      'text-ui-warn': s.deltaType === 'down',
                      'text-silver': s.deltaType === 'flat'
                    }">
                <span v-if="s.deltaType === 'up'">▲</span>
                <span v-else-if="s.deltaType === 'down'">▼</span>
                <span v-else>=</span>
                
                {{ s.delta }} km/h vs session-1
              </span>
            </div>
            
            <div v-if="s.note" class="text-body text-silver pl-4">
              {{ s.note }}
            </div>
          </div>
        </CyberPanel>
      </template>
      
      <template #right>
        <!-- Mock Track Highlight -->
        <CyberPanel class="h-full relative flex items-center justify-center bg-[#1A252C] overflow-hidden p-2">
          <!-- Sonoma-ish SVG shape -->
          <svg viewBox="0 0 100 100" class="w-full h-full opacity-50 drop-shadow-[2px_2px_0_#000]">
            <!-- Track outline -->
            <path d="M 20 80 L 10 50 L 30 20 L 70 10 L 90 40 L 70 80 Z" fill="none" stroke="#A0AAB5" stroke-width="2" stroke-linejoin="round"/>
            
            <!-- Highlights based on selected straight -->
            <path v-if="cur.id === 'front'" d="M 70 80 L 20 80" fill="none" stroke="#5EED71" stroke-width="4" class="animate-pulse" stroke-linecap="round"/>
            <path v-if="cur.id === 't4'" d="M 30 20 L 70 10" fill="none" stroke="#5EED71" stroke-width="4" class="animate-pulse" stroke-linecap="round"/>
            <path v-if="cur.id === 't7'" d="M 90 40 L 70 80" fill="none" stroke="#5EED71" stroke-width="4" class="animate-pulse" stroke-linecap="round"/>
          </svg>
          <div class="absolute bottom-2 right-2 text-body text-silver font-bold bg-ink px-1 rounded">
            TRACK MAP
          </div>
        </CyberPanel>
      </template>
    </CyberSplitView>
    
    <template #floating>
      <CoachFloat
        :emotion="getEmotion()"
        :text="getCoachLine()"
        :key="cur.id"
      />
    </template>
  </PageShell>
</template>
