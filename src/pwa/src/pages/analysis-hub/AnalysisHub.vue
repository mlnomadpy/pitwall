<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useSaveStore } from '@/entities/save/model/saveStore'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import StatusBar from '@/widgets/status-bar/StatusBar.vue'
import HintBar from '@/widgets/hint-bar/HintBar.vue'
import CyberTile from '@/shared/ui/core/CyberTile.vue'

const router = useRouter()
const save = useSaveStore()
const audio = useAudioStore()

const tiles = [
  { id: 'lap-times', title: 'LAP TIMES HALL', desc: '24 LAPS THIS SEASON', route: '/analysis/lap-times' },
  { id: 'corners', title: 'CORNER MASTERY', desc: '11 CORNERS GRADED A-F', route: '/analysis/corners' },
  { id: 'straights', title: 'STRAIGHTS & SPEED', desc: '3 STRAIGHTS', route: '/analysis/straights' },
  { id: 'track', title: 'TRACK ATLAS', desc: 'ELEVATION · MARKERS', route: '/analysis/track' },
  { id: 'evolution', title: 'DRIVER EVOLUTION', desc: '47 SESSIONS', route: '/analysis/evolution' },
  { id: 'pedals', title: 'PEDAL PROFILE', desc: 'THROTTLE · BRAKE', route: '/analysis/pedals' }
]

const cursorIndex = ref(0)
const hasSessions = ref(save.slots[save.activeSlotId!-1]?.sessions?.length > 0)

const handleKey = (e: KeyboardEvent) => {
  if (e.key === 'ArrowRight') {
    cursorIndex.value = (cursorIndex.value + 1) % tiles.length
    audio.playSfx('cursor_move')
  } else if (e.key === 'ArrowLeft') {
    cursorIndex.value = (cursorIndex.value - 1 + tiles.length) % tiles.length
    audio.playSfx('cursor_move')
  } else if (e.key === 'ArrowDown') {
    cursorIndex.value = (cursorIndex.value + 2) % tiles.length
    audio.playSfx('cursor_move')
  } else if (e.key === 'ArrowUp') {
    cursorIndex.value = (cursorIndex.value - 2 + tiles.length) % tiles.length
    audio.playSfx('cursor_move')
  } else if (e.key === 'Enter') {
    if (hasSessions.value && tiles[cursorIndex.value].route) {
      audio.playSfx('cursor_select')
      router.push(tiles[cursorIndex.value].route as string)
    } else {
      audio.playSfx('cancel')
    }
  } else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.push('/garage')
  } else if (e.key === ' ' || e.key === 'Shift') {
    audio.playSfx('cursor_select')
    router.push('/analysis/sql')
  }
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
    
    <div class="page-bg"></div>
    
    <div class="content pt-[6vh] pb-[6vh] px-[3vw] h-full flex flex-col relative z-10">
      <div class="heading-block mb-[1.5vh] text-center">
        <h1 class="text-title font-title text-silver pixel-shadow tracking-[0.2em]">ANALYSIS HALL</h1>
        <div class="heading-rule"></div>
      </div>
      
      <div class="grid grid-cols-2 gap-[1.5vmin] flex-grow">
        <CyberTile
          v-for="(tile, i) in tiles" 
          :key="tile.id"
          :title="tile.title"
          :subText="tile.desc"
          :focused="cursorIndex === i"
          :locked="!hasSessions"
          :showKerb="true"
          variant="ink"
          @click="() => { cursorIndex = i; if (hasSessions) $router.push(tile.route as string); }"
          @hover="cursorIndex = i"
        />
      </div>
      
      <div v-if="!hasSessions" class="text-center text-ui-warn text-body mt-[1vh] mb-[1vh] pixel-shadow">
        "Drive a lap first."
      </div>
    </div>
    
    <HintBar :hints="['A · ENTER', 'B · GARAGE', '◆ SQL CONSOLE']" />
  </div>
</template>

<style scoped>
</style>

