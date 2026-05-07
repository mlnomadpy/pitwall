<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import PageShell from '@/shared/ui/PageShell.vue'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'
import CyberSplitView from '@/shared/ui/core/CyberSplitView.vue'
import CoachFloat from '@/shared/ui/CoachFloat.vue'
import TrackMap from '@/shared/ui/core/TrackMap.vue'

const router = useRouter()
const audio = useAudioStore()

// Mock data
const straights = [
  { id: 'front', name: 'FRONT STRAIGHT', speed: 198.4, lap: 7, delta: '+2.1', deltaType: 'up', note: '', sparkline: 'M0,20 L10,18 L20,12 L30,10 L50,5 L70,2 L100,0' },
  { id: 't4', name: 'T4 RUN', speed: 138.7, lap: 12, delta: '-1.0', deltaType: 'down', note: '(-cost on T3a exit)', sparkline: 'M0,15 L20,12 L40,10 L60,11 L80,13 L100,15' },
  { id: 't7', name: 'T7 → T8a', speed: 187.2, lap: 11, delta: '0', deltaType: 'flat', note: '', sparkline: 'M0,20 L20,15 L40,10 L60,5 L80,2 L100,2' },
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
            class="p-2 border transition-colors flex flex-col gap-1 cursor-pointer"
            :class="cursorIndex === i ? 'border-ui-good bg-charcoal' : 'border-slate bg-ink'"
            @click="cursorIndex = i; audio.playSfx('cursor_move')"
          >
            <div class="flex justify-between items-end">
              <div class="font-bold">
                <span v-if="cursorIndex === i" class="text-ui-good mr-1">▶</span>
                <span :class="cursorIndex === i ? 'text-white' : 'text-silver'">░░ {{ s.name }} ░░</span>
              </div>
              <div class="text-body text-silver">LAP {{ s.lap }}</div>
            </div>
            
            <div class="flex gap-4 items-center pl-4">
              <span class="text-title font-nums font-bold text-white">
                {{ s.speed }} km/h
              </span>
              
              <div class="flex-grow h-8 relative mr-4 ml-4">
                <svg viewBox="0 0 100 20" class="w-full h-full preserve-aspect-ratio-none overflow-visible">
                  <path :d="s.sparkline" fill="none" :stroke="s.deltaType === 'down' ? '#ef4444' : s.deltaType === 'up' ? '#5EED71' : '#A0AAB5'" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
                <div class="absolute inset-0 bg-gradient-to-r from-transparent to-ink/20 pointer-events-none"></div>
              </div>
              
              <span class="text-body flex items-center gap-1 flex-shrink-0"
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
        <CyberPanel class="h-full relative flex items-center justify-center bg-[#1A252C] overflow-hidden p-0 border-b border-slate">
          <TrackMap class="absolute inset-[-10%] w-[120%] h-[120%] opacity-80" />
          <div class="absolute top-2 left-2 text-small text-silver font-bold z-10">
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
