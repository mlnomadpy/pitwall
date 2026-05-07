<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import { useRouter } from 'vue-router'
import { useSaveStore } from '@/entities/save/model/saveStore'
import Tile from './ui/Tile.vue'
import type { TileData } from './ui/Tile.vue'
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
const hints = ['▲ ▼ SWIPE', 'A · ENTER', 'S · SETTINGS', 'B · TITLE']

const moveCursor = (dir: number) => {
  cursorIndex.value = (cursorIndex.value + dir + tiles.length) % tiles.length
  audio.playSfx('cursor_move')
}

useKeyboard((e: KeyboardEvent) => {
  if (e.key === 'ArrowRight' || e.key === 'ArrowDown') {
    moveCursor(1)
  } else if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') {
    moveCursor(-1)
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

// ── Touch drag navigation ──
const menuRef = ref<HTMLDivElement | null>(null)
let touchStartY = 0
let touchStartX = 0
let touchStartTime = 0
let hasMoved = false

const onTouchStart = (e: TouchEvent) => {
  touchStartY = e.touches[0].clientY
  touchStartX = e.touches[0].clientX
  touchStartTime = Date.now()
  hasMoved = false
}

const onTouchMove = (e: TouchEvent) => {
  const dy = e.touches[0].clientY - touchStartY
  const dx = e.touches[0].clientX - touchStartX
  // Prevent page scroll when dragging vertically on the menu
  if (Math.abs(dy) > 10 && Math.abs(dy) > Math.abs(dx)) {
    e.preventDefault()
  }
}

const onTouchEnd = (e: TouchEvent) => {
  const dy = e.changedTouches[0].clientY - touchStartY
  const dx = e.changedTouches[0].clientX - touchStartX
  const dt = Date.now() - touchStartTime
  const absDy = Math.abs(dy)
  const absDx = Math.abs(dx)

  // Must be a deliberate gesture (>30px within 500ms)
  if (dt > 500) return

  if (absDy > 30 && absDy > absDx) {
    // Vertical swipe — scroll through tiles
    if (dy < 0) {
      moveCursor(1) // Swipe up → next
    } else {
      moveCursor(-1) // Swipe down → prev
    }
    hasMoved = true
  } else if (absDx > 50 && absDx > absDy && dx > 0) {
    // Swipe right → enter focused tile
    if (!greetingActive.value) {
      audio.playSfx('cursor_select')
      onSelect(tiles[cursorIndex.value].id)
    }
    hasMoved = true
  }
}

onMounted(() => {
  const el = menuRef.value
  if (el) {
    el.addEventListener('touchstart', onTouchStart, { passive: true })
    el.addEventListener('touchmove', onTouchMove, { passive: false })
    el.addEventListener('touchend', onTouchEnd, { passive: true })
  }
})

onUnmounted(() => {
  const el = menuRef.value
  if (el) {
    el.removeEventListener('touchstart', onTouchStart)
    el.removeEventListener('touchmove', onTouchMove)
    el.removeEventListener('touchend', onTouchEnd)
  }
})
</script>

<template>
  <PageShell title="GARAGE" :hints="hints" bg="neutral" :show-heading="false">

    <div class="content flex flex-col items-center justify-center relative z-10 w-full flex-grow">
      <!-- Section heading -->
      <div class="section-heading mb-[2vmin]">
        <span class="text-title text-slate tracking-[0.3em] font-title">GARAGE</span>
      </div>
      
      <div ref="menuRef" class="spatial-menu-container">
        <div 
          v-for="(t, i) in tiles" 
          :key="t.id"
          :class="[
            'spatial-item',
            (i - cursorIndex + tiles.length) % tiles.length === 0 ? 'focused' :
            (i - cursorIndex + tiles.length) % tiles.length === 1 ? 'next' :
            (i - cursorIndex + tiles.length) % tiles.length === 2 ? 'next-2' :
            (cursorIndex - i + tiles.length) % tiles.length === 1 ? 'prev' :
            (cursorIndex - i + tiles.length) % tiles.length === 2 ? 'prev-2' : 'hidden-item'
          ]"
          @click="cursorIndex = i; if (!t.disabled) onSelect(t.id)"
        >
          <Tile 
            :tile="t" 
            :focused="cursorIndex === i"
            @select="onSelect(t.id)"
            @hover="cursorIndex = i"
          />
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
  touch-action: none; /* We handle swipe ourselves */
  user-select: none;
  -webkit-user-select: none;
}

.spatial-item {
  position: absolute;
  width: clamp(240px, 60vw, 400px);
  transition: transform 0.15s steps(4), opacity 0.15s steps(4);
  transform-origin: center center;
  opacity: 0;
  pointer-events: none;
  cursor: pointer;
}

.spatial-item.focused {
  transform: translateZ(100px) translateY(0);
  opacity: 1;
  z-index: 10;
  pointer-events: auto;
  filter: drop-shadow(8px 8px 0 rgba(0, 0, 0, 0.8));
}

.spatial-item.prev {
  transform: translateZ(-150px) translateY(-60px) rotateX(5deg);
  opacity: 0.6;
  z-index: 5;
  pointer-events: auto;
}

.spatial-item.prev-2 {
  transform: translateZ(-300px) translateY(-120px) rotateX(10deg);
  opacity: 0.2;
  z-index: 4;
  pointer-events: auto;
}

.spatial-item.next {
  transform: translateZ(-150px) translateY(60px) rotateX(-5deg);
  opacity: 0.6;
  z-index: 5;
  pointer-events: auto;
}

.spatial-item.next-2 {
  transform: translateZ(-300px) translateY(120px) rotateX(-10deg);
  opacity: 0.2;
  z-index: 4;
  pointer-events: auto;
}

.hidden-item {
  opacity: 0;
}
</style>

