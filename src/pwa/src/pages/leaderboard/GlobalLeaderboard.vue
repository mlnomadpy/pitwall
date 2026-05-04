<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import PageShell from '@/shared/ui/PageShell.vue'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'

const router = useRouter()
const audio = useAudioStore()

// Mock leaderboard data
const entries = [
  { rank: 1, initials: 'TRD', car: 'GT3_911', track: 'SONOMA', time: '1:34.210' },
  { rank: 2, initials: 'BTY', car: 'M4_GT3', track: 'SONOMA', time: '1:35.050' },
  { rank: 3, initials: 'DRL', car: 'AMG_GT3', track: 'SONOMA', time: '1:35.800' },
  { rank: 4, initials: 'YOU', car: 'GT3_911', track: 'SONOMA', time: '1:36.450' },
  { rank: 5, initials: 'CLM', car: '720S_GT3', track: 'SONOMA', time: '1:37.110' },
  { rank: 6, initials: 'BDY', car: 'M4_GT3', track: 'SONOMA', time: '1:38.000' },
  { rank: 7, initials: 'AI1', car: 'GT3_911', track: 'SONOMA', time: '1:38.500' },
  { rank: 8, initials: 'AI2', car: 'AMG_GT3', track: 'SONOMA', time: '1:39.100' },
]

const cursorIndex = ref(3) // Default to 'YOU'

useKeyboard((e: KeyboardEvent) => {
  if (e.key === 'ArrowDown') {
    cursorIndex.value = (cursorIndex.value + 1) % entries.length
    audio.playSfx('cursor_move')
  } else if (e.key === 'ArrowUp') {
    cursorIndex.value = (cursorIndex.value - 1 + entries.length) % entries.length
    audio.playSfx('cursor_move')
  } else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.push('/garage')
  }
})

const getRankColor = (rank: number) => {
  if (rank === 1) return 'text-[#fbbf24]' // Gold
  if (rank === 2) return 'text-[#94a3b8]' // Silver
  if (rank === 3) return 'text-[#b45309]' // Bronze
  return 'text-silver'
}
</script>

<template>
  <PageShell title="HIGH SCORES" :hints="['B · BACK', '▲ ▼ MOVE']" bg="neutral">
    <template #heading>
      <div class="heading-block mb-[1.5vh] text-center">
        <h1 class="text-title font-title text-ui-warn tracking-[0.2em] animate-pulse">RANKING</h1>
        <div class="heading-rule"></div>
      </div>
    </template>

    <CyberPanel class="flex-grow flex flex-col p-2 bg-ink border-slate overflow-hidden mx-2 pb-6">
      <table class="w-full text-left font-mono text-body">
        <thead>
          <tr class="text-charcoal-light border-b border-charcoal">
            <th class="pb-2 w-10 text-center">RK</th>
            <th class="pb-2 w-16">NAME</th>
            <th class="pb-2">CAR</th>
            <th class="pb-2 text-right">TIME</th>
          </tr>
        </thead>
        <tbody>
          <tr 
            v-for="(entry, i) in entries" 
            :key="i"
            class="transition-colors border-b border-charcoal/30"
            :class="cursorIndex === i ? 'bg-charcoal text-white font-bold' : 'text-slate'"
          >
            <td class="py-2 text-center" :class="getRankColor(entry.rank)">
              <span v-if="cursorIndex === i" class="text-ui-good mr-1 animate-pulse absolute -ml-4">▶</span>
              {{ entry.rank < 10 ? '0' + entry.rank : entry.rank }}
            </td>
            <td class="py-2" :class="getRankColor(entry.rank)">{{ entry.initials }}</td>
            <td class="py-2 truncate max-w-[80px]">{{ entry.car }}</td>
            <td class="py-2 text-right tracking-wider">{{ entry.time }}</td>
          </tr>
        </tbody>
      </table>
      
      <div class="flex-grow flex items-end justify-center pb-4 pointer-events-none mt-4">
        <span class="text-ui-warn text-title-sm font-bold tracking-[0.3em] animate-pulse">INSERT COIN</span>
      </div>
    </CyberPanel>
  </PageShell>
</template>

<style scoped>
/* No specific viewport styles, PageShell handles bounds */
</style>
