<script setup lang="ts">
import { ref } from 'vue'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import { useRouter } from 'vue-router'
import { useSaveStore } from '@/entities/save/model/saveStore'
import Tile from './ui/Tile.vue'
import type { TileData } from './ui/Tile.vue'
import CyberAvatar from '@/shared/ui/core/CyberAvatar.vue'
import CoachFloat from '@/shared/ui/CoachFloat.vue'
import PageShell from '@/shared/ui/PageShell.vue'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'

const router = useRouter()
const save = useSaveStore()
const audio = useAudioStore()

const tiles: TileData[] = [
  { id: 'track', title: 'TRACK', subText: 'GO RACING', route: '/briefing' },
  { id: 'pit-stall', title: 'PIT STALL', subText: 'CONNECT · TUNE', route: '/garage/pit-stall' },
  { id: 'trainer-card', title: 'TRAINER CARD', subText: 'STATS · MEDALS', route: '/garage/trainer' },
  { id: 'analysis', title: 'ANALYSIS', subText: 'LAPS · CORNERS', route: '/garage/analysis' },
  { id: 'coaches', title: 'COACHES', subText: '+1 AVAILABLE', route: '/garage/coach' },
  { id: 'leaderboard', title: 'HIGH SCORES', subText: 'GLOBAL RANKING', route: '/leaderboard' },
  { id: 'quests', title: 'QUEST LOG', subText: '0 ACTIVE GOALS', route: '/garage/quests' },
  { id: 'setup', title: 'CAR SETUP', subText: 'AERO · BRAKES', route: '/garage/setup' }
]

const cursorIndex = ref(0)
const greetingActive = ref(true)
const greetingText = "Welcome to the Garage! Ready to hit the track?"
const hints = ['▲ ▼ ◀ ▶ MOVE', 'A · ENTER', 'S · SETTINGS', 'B · TITLE']

useKeyboard((e: KeyboardEvent) => {
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
    if (greetingActive.value) return
    audio.playSfx('cursor_select')
    onSelect(tiles[cursorIndex.value].id)
  } else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    save.activeSlotId = null
    router.push('/')
  } else if (e.key === 'c' || e.key === 'C') {
    audio.playSfx('cursor_select')
    router.push('/calibration')
  } else if (e.key === 'h' || e.key === 'H') {
    audio.playSfx('cursor_select')
    router.push('/garage/pit-stall/hardware')
  } else if (e.key === 's' || e.key === 'S' || e.key === ' ') {
    audio.playSfx('cursor_select')
    router.push('/settings')
  }
})

const onSelect = (tileId: string) => {
  const tile = tiles.find(t => t.id === tileId)
  if (tile && !tile.disabled) {
    router.push(tile.route)
  }
}
</script>

<template>
  <PageShell title="GARAGE" :hints="hints" bg="neutral" :show-heading="false">
    <!-- Layered background overrides -->
    <div class="garage-bg absolute inset-0 z-0 pointer-events-none"></div>
    <div class="garage-floor absolute bottom-0 left-0 w-full z-1 pointer-events-none"></div>
    <div class="crt-overlay pointer-events-none"></div>
    
    <div class="content flex flex-col items-center justify-center relative z-10 w-full flex-grow">
      <!-- Section heading -->
      <div class="section-heading mb-[2vmin]">
        <span class="text-title text-slate tracking-[0.3em] font-title">GARAGE</span>
      </div>
      
      <div class="spatial-menu-container">
        <div 
          v-for="(t, i) in tiles" 
          :key="t.id"
          class="spatial-item"
          :class="{
            'focused': cursorIndex === i,
            'prev': cursorIndex === (i + 1) % tiles.length || (cursorIndex === 0 && i === tiles.length - 1),
            'next': cursorIndex === (i - 1 + tiles.length) % tiles.length || (cursorIndex === tiles.length - 1 && i === 0)
          }"
        >
          <Tile 
            :tile="t" 
            :focused="cursorIndex === i"
            @select="onSelect(t.id)"
            @hover="cursorIndex = i"
          />
        </div>
      </div>
      
      <!-- NPC Band -->
      <div class="npc-band absolute bottom-[6vh] left-0 w-full flex items-end px-[3vw]" style="height: clamp(48px, 10vh, 80px);">
        <div class="npc-ground flex items-end">
          <CyberAvatar :sheet="save.activeSlot?.preferredCoach ?? 'trod'" size="sm" variant="ghost" />
        </div>
      </div>
    </div>
    
    <template #floating>
      <CoachFloat 
        v-if="greetingActive"
        :coach-id="save.activeSlot?.preferredCoach ?? 'trod'"
        :text="greetingText"
        emotion="idle"
        @done="greetingActive = false" 
      />
    </template>
  </PageShell>
</template>

<style scoped>
.garage-bg {
  background-color: var(--color-ink);
}

.garage-floor {
  height: 20vh;
  background-color: var(--color-asphalt-mid);
  border-top: 2px solid var(--color-slate);
  /* Asphalt lane markings */
  background-image:
    repeating-linear-gradient(
      0deg,
      transparent,
      transparent 2px,
      rgba(0, 0, 0, 0.06) 2px,
      rgba(0, 0, 0, 0.06) 4px
    );
}

.section-heading {
  text-align: center;
  position: relative;
  z-index: 20;
}

.section-heading::after {
  content: '';
  display: block;
  width: clamp(60px, 18vw, 160px);
  height: clamp(4px, 0.8vmin, 8px);
  margin: clamp(4px, 1vmin, 10px) auto 0;
  background: repeating-linear-gradient(
    90deg,
    #c93838 0,
    #c93838 clamp(4px, 1vmin, 8px),
    #f5f5e8 clamp(4px, 1vmin, 8px),
    #f5f5e8 clamp(8px, 2vmin, 16px)
  );
}

/* 3D Spatial Menu */
.spatial-menu-container {
  position: relative;
  width: 100%;
  height: 60vh;
  perspective: 1200px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  z-index: 10;
}

.spatial-item {
  position: absolute;
  width: clamp(240px, 60vw, 400px);
  transition: transform 0.15s steps(4), opacity 0.15s steps(4);
  transform-origin: center center;
  opacity: 0;
  pointer-events: none;
}

.spatial-item.focused {
  transform: translateZ(100px) translateY(0);
  opacity: 1;
  z-index: 10;
  pointer-events: auto;
  filter: drop-shadow(8px 8px 0 rgba(0, 0, 0, 0.8));
}

.spatial-item.prev {
  transform: translateZ(-200px) translateY(-80px) rotateX(10deg);
  opacity: 0.5;
  z-index: 5;
}

.spatial-item.next {
  transform: translateZ(-200px) translateY(80px) rotateX(-10deg);
  opacity: 0.5;
  z-index: 5;
}

.npc-ground {
  position: relative;
}
.npc-ground::after {
  content: '';
  position: absolute;
  bottom: -2px;
  left: 50%;
  transform: translateX(-50%);
  width: 120%;
  height: 2px;
  background-color: var(--color-slate);
}
</style>

