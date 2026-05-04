<script setup lang="ts">
import { ref, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import PageShell from '@/shared/ui/PageShell.vue'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'
import CyberSplitView from '@/shared/ui/core/CyberSplitView.vue'
import CoachFloat from '@/shared/ui/CoachFloat.vue'

const router = useRouter()
const audio = useAudioStore()

// Thresholds
const thrTh = ref(5) // throttle %
const brkTh = ref(1.0) // brake bar
const activeSlider = ref<'throttle' | 'brake'>('throttle')

// Base simulated data
const baseDist = {
  throttle: 54.7,
  brake: 15.0,
  trail: 10.2,
  coast: 20.1
}

// Reactively skew the distribution based on thresholds
const distribution = computed(() => {
  // If we raise throttle threshold, coast goes up, throttle goes down
  const thrSkew = (thrTh.value - 5) * 0.5
  
  // If we raise brake threshold, coast goes up, brake goes down, trail goes down
  const brkSkew = (brkTh.value - 1.0) * 2.0
  
  const throttle = Math.max(0, baseDist.throttle - thrSkew)
  const brake = Math.max(0, baseDist.brake - brkSkew)
  const trail = Math.max(0, baseDist.trail - brkSkew * 0.5)
  const coast = Math.max(0, 100 - throttle - brake - trail)
  
  return { throttle, brake, trail, coast }
})

const cornerStates = [
  { id: 'T1', pattern: ['T', 'T', 'T', 'C', 'B', 'B'] },
  { id: 'T2', pattern: ['T', 'T', 'T', 'T', 'C', 'C'] },
  { id: 'T3', pattern: ['B', 'B', 'TR', 'TR', 'T', 'T'] },
  { id: 'T4', pattern: ['C', 'TR', 'TR', 'TR', 'T', 'T'] },
  { id: 'T7', pattern: ['B', 'B', 'B', 'TR', 'TR', 'T'] },
  { id: 'T11', pattern: ['B', 'B', 'B', 'B', 'C', 'T'] }
]

const getCoachLine = () => {
  const c = distribution.value.coast
  const tr = distribution.value.trail
  const th = distribution.value.throttle
  
  if (c > 20) return `You're coasting ${c.toFixed(1)}% of the time. Trail-brake to corner entry instead of lifting early.`
  if (tr < 5) return `Less than 5% trail-brake. Front tires aren't loaded — you're sliding.`
  if (tr > 15) return `That's pro-level trail-braking. Don't lose it.`
  if (th > 60) return `You're committed. Good.`
  if (th < 40) return `Tentative on power. Find the exits.`
  return `Solid pedal overlap for an intermediate.`
}

const getEmotion = () => {
  const c = distribution.value.coast
  const tr = distribution.value.trail
  const th = distribution.value.throttle
  
  if (c > 20 || tr < 5 || th < 40) return 'talk'
  if (tr > 15 || th > 60) return 'victory'
  return 'idle'
}

let timeoutId: number | null = null

useKeyboard((e: KeyboardEvent) => {
  if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
    activeSlider.value = activeSlider.value === 'throttle' ? 'brake' : 'throttle'
    audio.playSfx('cursor_move')
  } else if (e.key === 'ArrowRight') {
    if (activeSlider.value === 'throttle') {
      thrTh.value = Math.min(100, thrTh.value + 1)
    } else {
      brkTh.value = Number((Math.min(100, brkTh.value + 0.5)).toFixed(1))
    }
    audio.playSfx('cursor_move')
    
    // Simulate reload
    if (timeoutId) window.clearTimeout(timeoutId)
    timeoutId = window.setTimeout(() => {
      if (getEmotion() === 'talk') audio.playSfx('error_quiet')
    }, 300)
    
  } else if (e.key === 'ArrowLeft') {
    if (activeSlider.value === 'throttle') {
      thrTh.value = Math.max(0, thrTh.value - 1)
    } else {
      brkTh.value = Number((Math.max(0.5, brkTh.value - 0.5)).toFixed(1))
    }
    audio.playSfx('cursor_move')
    
    // Simulate reload
    if (timeoutId) window.clearTimeout(timeoutId)
    timeoutId = window.setTimeout(() => {
      if (getEmotion() === 'talk') audio.playSfx('error_quiet')
    }, 300)
    
  } else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.push('/garage/analysis')
  }
})

onUnmounted(() => {
  if (timeoutId) window.clearTimeout(timeoutId)
})
</script>

<template>
  <PageShell title="PEDAL PROFILE" subtitle="session 2026-04-29-1503" :hints="['▲ ▼ SELECT', '◀ ▶ ADJUST', 'B · BACK']" bg="cool">
    <!-- Session Distribution -->
    <CyberPanel class="p-2 relative">
      <div class="text-body text-silver mb-2 font-bold uppercase">Session Distribution</div>
      
      <!-- Stacked Bar -->
      <div class="w-full h-4 flex mt-2 border border-slate">
        <div class="h-full bg-ui-good transition-all duration-300 flex items-center overflow-hidden" :style="{ width: `${distribution.throttle}%` }">
          <span v-if="distribution.throttle > 10" class="text-small text-ink font-bold ml-1">THROTTLE</span>
        </div>
        <div class="h-full bg-ui-warn transition-all duration-300 flex items-center overflow-hidden" :style="{ width: `${distribution.brake}%` }">
          <span v-if="distribution.brake > 10" class="text-small text-ink font-bold ml-1">BRAKE</span>
        </div>
        <div class="h-full bg-[#F59E0B] transition-all duration-300 flex items-center overflow-hidden" :style="{ width: `${distribution.trail}%` }">
          <span v-if="distribution.trail > 10" class="text-small text-ink font-bold ml-1">TRAIL</span>
        </div>
        <div class="h-full bg-charcoal transition-all duration-300 flex items-center overflow-hidden" :style="{ width: `${distribution.coast}%` }">
          <span v-if="distribution.coast > 10" class="text-small text-white font-bold ml-1">COAST</span>
        </div>
      </div>
      
      <div class="flex justify-between text-body mt-2 font-bold">
        <span class="text-ui-good">THROTTLE {{ distribution.throttle.toFixed(1) }}%</span>
        <span class="text-ui-warn">BRAKE {{ distribution.brake.toFixed(1) }}%</span>
        <span class="text-[#F59E0B]">TRAIL {{ distribution.trail.toFixed(1) }}%</span>
        <span class="text-silver">COAST {{ distribution.coast.toFixed(1) }}%</span>
      </div>
    </CyberPanel>
    
    <CyberSplitView split="60-40" gap="sm" class="flex-grow min-h-0 mt-2">
      <template #left>
        <!-- Per Corner Grid -->
        <CyberPanel class="h-full flex flex-col text-body overflow-hidden p-2">
          <div class="flex justify-between mb-2">
            <div class="text-silver font-bold uppercase">Per-Corner Pedal State</div>
          </div>
          
          <div class="flex flex-col gap-1 overflow-hidden pr-2">
            <div v-for="c in cornerStates" :key="c.id" class="flex gap-2 items-center">
              <span class="w-4 text-silver">{{ c.id }}</span>
              <div class="flex flex-grow gap-[2px]">
                <div v-for="(state, i) in c.pattern" :key="i" class="h-4 flex-grow"
                     :class="{
                       'bg-ui-good': state === 'T',
                       'bg-ui-warn': state === 'B',
                       'bg-[#F59E0B]': state === 'TR',
                       'bg-charcoal': state === 'C'
                     }">
                </div>
              </div>
            </div>
          </div>
          
          <div class="mt-auto flex justify-around text-small text-slate pt-2 border-t border-charcoal">
            <span class="flex items-center gap-1"><span class="w-2 h-2 bg-ui-good inline-block"></span> throttle</span>
            <span class="flex items-center gap-1"><span class="w-2 h-2 bg-charcoal inline-block"></span> coast</span>
            <span class="flex items-center gap-1"><span class="w-2 h-2 bg-[#F59E0B] inline-block"></span> trail</span>
            <span class="flex items-center gap-1"><span class="w-2 h-2 bg-ui-warn inline-block"></span> brake</span>
          </div>
        </CyberPanel>
      </template>

      <template #right>
        <!-- Thresholds -->
        <CyberPanel class="h-full flex flex-col text-body p-2 overflow-hidden relative">
          <div class="text-silver mb-2 font-bold uppercase">Thresholds</div>
          
          <div class="flex flex-col gap-4">
            <div class="flex flex-col gap-1" :class="activeSlider === 'throttle' ? 'text-white' : 'text-slate'">
              <div class="flex justify-between items-end relative">
                <span class="flex items-center relative">
                  <span v-if="activeSlider === 'throttle'" class="text-ui-good mr-1 absolute -left-3 text-body">▶</span>
                  THROTTLE %
                </span>
                <span class="font-bold bg-charcoal px-1">{{ thrTh }}</span>
              </div>
              <div class="w-full h-2 bg-charcoal relative mt-1 border border-slate">
                <div class="absolute h-full bg-ui-good" :style="{ width: `${thrTh}%` }"></div>
              </div>
            </div>
            
            <div class="flex flex-col gap-1" :class="activeSlider === 'brake' ? 'text-white' : 'text-slate'">
              <div class="flex justify-between items-end relative">
                <span class="flex items-center relative">
                  <span v-if="activeSlider === 'brake'" class="text-ui-good mr-1 absolute -left-3 text-body">▶</span>
                  BRAKE bar
                </span>
                <span class="font-bold bg-charcoal px-1">{{ brkTh.toFixed(1) }}</span>
              </div>
              <div class="w-full h-2 bg-charcoal relative mt-1 border border-slate">
                <div class="absolute h-full bg-ui-warn" :style="{ width: `${brkTh * 5}%` }"></div> <!-- fake scale -->
              </div>
            </div>
          </div>
          
          <div class="mt-auto text-small text-slate text-center leading-tight pt-2 border-t border-charcoal">
            Use ◀ ▶ to adjust.<br>Affects Coast classification.
          </div>
        </CyberPanel>
      </template>
    </CyberSplitView>
    
    <template #floating>
      <CoachFloat
        :emotion="getEmotion()"
        :text="getCoachLine()"
        :key="getCoachLine()"
      />
    </template>
  </PageShell>
</template>
