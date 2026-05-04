<script setup lang="ts">
import { ref } from 'vue'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import { useRouter } from 'vue-router'
import { useSaveStore } from '@/entities/save/model/saveStore'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import PageShell from '@/shared/ui/PageShell.vue'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'
import CyberBox from '@/shared/ui/core/CyberBox.vue'
import Sprite from '@/entities/coach/Sprite.vue'

const router = useRouter()
const save = useSaveStore()
const audio = useAudioStore()

const state = ref<'selecting' | 'loading' | 'displaying'>('displaying')

// Mock lap selection
const leftSel = ref('session-20260423 · LAP 7  · 1:46.8')
const rightSel = ref('session-20260415 · LAP 5  · 1:48.2')
const activePicker = ref<'none' | 'left' | 'right'>('none')

const cursorPos = ref(50) // 0 to 100 percentage
const resolution = 40 // columns in chart

// Generate mock chart data
const generateCurve = (base: number, freq: number, phase: number) => {
  const data = []
  for (let i = 0; i < resolution; i++) {
    data.push(base + Math.sin((i / resolution) * freq * Math.PI + phase) * 0.5 + (Math.random() * 0.1))
  }
  return data
}

const leftSpeed = generateCurve(0.6, 4, 0)
const rightSpeed = generateCurve(0.5, 4, 0.2)
const leftBrake = generateCurve(0.2, 6, 1)
const rightBrake = generateCurve(0.3, 6, 1.2)
const leftGLat = generateCurve(0.5, 8, 0)
const rightGLat = generateCurve(0.5, 8, 0.4)

useKeyboard((e: KeyboardEvent) => {
  if (state.value === 'displaying' && activePicker.value === 'none') {
    if (e.key === 'ArrowRight') {
      cursorPos.value = Math.min(100, cursorPos.value + (100 / resolution))
      // Rate limit sound to avoid spam
      if (Math.random() > 0.5) audio.playSfx('cursor_move')
    } else if (e.key === 'ArrowLeft') {
      cursorPos.value = Math.max(0, cursorPos.value - (100 / resolution))
      if (Math.random() > 0.5) audio.playSfx('cursor_move')
    } else if (e.key === 'ArrowUp') {
      activePicker.value = 'left'
      audio.playSfx('cursor_move')
    } else if (e.key === 'a' || e.key === 'Enter') {
      audio.playSfx('cursor_select')
    } else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
      audio.playSfx('cancel')
      router.back()
    }
  } else if (activePicker.value !== 'none') {
    if (e.key === 'ArrowDown') {
      if (activePicker.value === 'left') {
        activePicker.value = 'right'
        audio.playSfx('cursor_move')
      } else {
        activePicker.value = 'none'
        audio.playSfx('cursor_move')
      }
    } else if (e.key === 'ArrowUp') {
      if (activePicker.value === 'right') {
        activePicker.value = 'left'
        audio.playSfx('cursor_move')
      }
    } else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
      activePicker.value = 'none'
      audio.playSfx('cancel')
    }
  }
})

const getBarHeight = (val: number) => {
  // val is roughly 0 to 1
  const v = Math.max(0, Math.min(1, val))
  return `${v * 100}%`
}
</script>

<template>
  <PageShell title="COMPARE" :hints="['A · OPEN REPLAY', 'B · BACK', '◀ ▶ SCRUB', '▲ ▼ PICK']" bg="cool" :show-heading="false">
    <div class="flex justify-between items-end border-b border-slate pb-1 mb-2 mx-2">
      <h1 class="text-title font-title text-silver tracking-[0.2em]">COMPARE</h1>
    </div>

    <!-- Pickers -->
    <div class="px-2 mb-2 text-body flex flex-col gap-1">
      <div class="flex items-center gap-2">
        <span class="w-[clamp(24px,6vw,48px)] text-slate">LEFT</span>
        <span class="text-ui-info" v-if="activePicker === 'left'">▼</span>
        <span class="text-slate" v-else>▼</span>
        <CyberPanel class="px-2 py-0.5 bg-ink flex-grow border" :class="activePicker === 'left' ? 'border-ui-good text-white' : 'border-slate text-silver'">
          [ {{ leftSel }} ]
        </CyberPanel>
      </div>
      <div class="flex items-center gap-2">
        <span class="w-[clamp(24px,6vw,48px)] text-slate">RIGHT</span>
        <span class="text-ui-info" v-if="activePicker === 'right'">▼</span>
        <span class="text-slate" v-else>▼</span>
        <CyberPanel class="px-2 py-0.5 bg-ink flex-grow border" :class="activePicker === 'right' ? 'border-ui-good text-white' : 'border-slate text-silver'">
          [ {{ rightSel }} ]
        </CyberPanel>
      </div>
    </div>

    <!-- Delta Summary -->
    <div class="px-2 mb-2 text-body flex items-center gap-2 text-ui-good font-bold">
      <span class="text-slate font-normal">DELTA total</span>
      <span>-1.4 s</span>
      <div class="h-[1px] bg-ui-good flex-grow mx-2"></div>
      <span>L FASTER</span>
    </div>

    <!-- Charts -->
    <div class="px-2 flex flex-col gap-1 flex-grow">
      <!-- Speed -->
      <CyberPanel class="border-slate p-1 flex flex-col relative h-[36px]">
        <div class="absolute top-0 left-2 text-small text-slate bg-ink px-1 -mt-[4px]">SPEED</div>
        <div class="flex-grow flex items-end justify-between relative mt-1">
          <div class="absolute inset-0 flex items-end">
            <div v-for="(v, i) in leftSpeed" :key="'ls'+i" class="flex-1 bg-ui-info opacity-50 mx-[0.5px]" :style="{height: getBarHeight(v)}"></div>
          </div>
          <div class="absolute inset-0 flex items-end">
            <div v-for="(v, i) in rightSpeed" :key="'rs'+i" class="flex-1 bg-ui-warn opacity-50 mx-[0.5px]" :style="{height: getBarHeight(v)}"></div>
          </div>
          <div class="absolute top-0 bottom-0 w-[1px] bg-white z-10" :style="{left: `${cursorPos}%`}"></div>
        </div>
        <div class="flex justify-between text-small text-slate mt-[1px]"><span>T1</span><span>T11</span></div>
      </CyberPanel>
      
      <!-- Brake -->
      <CyberPanel class="border-slate p-1 flex flex-col relative h-[28px]">
        <div class="absolute top-0 left-2 text-small text-slate bg-ink px-1 -mt-[4px]">BRAKE</div>
        <div class="flex-grow flex items-end justify-between relative mt-1">
          <div class="absolute inset-0 flex items-end">
            <div v-for="(v, i) in leftBrake" :key="'lb'+i" class="flex-1 bg-ui-info opacity-50 mx-[0.5px]" :style="{height: getBarHeight(v)}"></div>
          </div>
          <div class="absolute inset-0 flex items-end">
            <div v-for="(v, i) in rightBrake" :key="'rb'+i" class="flex-1 bg-ui-warn opacity-50 mx-[0.5px]" :style="{height: getBarHeight(v)}"></div>
          </div>
          <div class="absolute top-0 bottom-0 w-[1px] bg-white z-10" :style="{left: `${cursorPos}%`}"></div>
        </div>
      </CyberPanel>

      <!-- GLat -->
      <CyberPanel class="border-slate p-1 flex flex-col relative h-[28px]">
        <div class="absolute top-0 left-2 text-small text-slate bg-ink px-1 -mt-[4px]">G-LAT</div>
        <div class="flex-grow flex items-center justify-between relative mt-1">
          <!-- Center line for Glat -->
          <div class="absolute left-0 right-0 h-[1px] bg-slate/50 top-1/2"></div>
          <div class="absolute inset-0 flex items-end">
            <div v-for="(v, i) in leftGLat" :key="'lg'+i" class="flex-1 bg-ui-info opacity-50 mx-[0.5px]" :style="{height: getBarHeight(v)}"></div>
          </div>
          <div class="absolute inset-0 flex items-end">
            <div v-for="(v, i) in rightGLat" :key="'rg'+i" class="flex-1 bg-ui-warn opacity-50 mx-[0.5px]" :style="{height: getBarHeight(v)}"></div>
          </div>
          <div class="absolute top-0 bottom-0 w-[1px] bg-white z-10" :style="{left: `${cursorPos}%`}"></div>
        </div>
      </CyberPanel>
    </div>

    <!-- Top Deltas -->
    <div class="px-2 mt-2 text-body">
      <div class="text-slate mb-1">TOP 3 DELTA SECTIONS</div>
      <div class="flex flex-col gap-0.5 pl-2">
        <div class="flex justify-between w-[clamp(140px,35vw,280px)]"><span class="text-silver">• T7 entry (1620-1820 m)</span><span class="text-ui-good">+0.6 s on L</span></div>
        <div class="flex justify-between w-[clamp(140px,35vw,280px)]"><span class="text-silver">• T11 exit (4080-100 m)</span><span class="text-ui-good">+0.4 s on L</span></div>
        <div class="flex justify-between w-[clamp(140px,35vw,280px)]"><span class="text-silver">• Carousel (1294-1540 m)</span><span class="text-ui-good">+0.3 s on L</span></div>
      </div>
    </div>
    
    <!-- Coach -->
    <div class="absolute bottom-[6vh] right-2 flex flex-col items-end gap-1">
      <CyberBox variant="charcoal" border="slate" class="text-small px-2 py-1 text-slate">{{ save.activeSlot?.preferredCoach?.toUpperCase() ?? 'T-ROD' }}</CyberBox>
      <CyberBox variant="charcoal" border="slate" class="w-[clamp(36px,8vmin,64px)] h-[clamp(36px,8vmin,64px)] overflow-hidden relative">
         <Sprite :sheet="save.activeSlot?.preferredCoach ?? 'trod'" animation="idle" class="scale-150 origin-center opacity-80 mix-blend-screen" style="filter: grayscale(1) sepia(1) hue-rotate(180deg) saturate(3);" />
      </CyberBox>
    </div>
  </PageShell>
</template>
