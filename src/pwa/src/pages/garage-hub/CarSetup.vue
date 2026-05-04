<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import PageShell from '@/shared/ui/PageShell.vue'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'
import CyberValuePicker from '@/shared/ui/core/CyberValuePicker.vue'
import CyberSplitView from '@/shared/ui/core/CyberSplitView.vue'

const router = useRouter()
const audio = useAudioStore()

const aeroOptions = [
  { id: -2, label: 'MINIMUM' },
  { id: -1, label: 'LOW' },
  { id: 0, label: 'BALANCED' },
  { id: 1, label: 'HIGH' },
  { id: 2, label: 'MAXIMUM' }
]

const brakeBiasOptions = [
  { id: -2, label: 'REAR ++' },
  { id: -1, label: 'REAR +' },
  { id: 0, label: '50 / 50' },
  { id: 1, label: 'FRONT +' },
  { id: 2, label: 'FRONT ++' }
]

const diffOptions = [
  { id: -2, label: 'OPEN' },
  { id: -1, label: 'LOOSE' },
  { id: 0, label: 'BALANCED' },
  { id: 1, label: 'TIGHT' },
  { id: 2, label: 'LOCKED' }
]

const aero = ref(0)
const brakeBias = ref(0)
const diff = ref(0)

const cursorIndex = ref(0)
const totalFields = 4 // 3 settings + APPLY button

const topSpeedDelta = computed(() => {
  return -(aero.value * 2) // Higher aero = lower top speed
})

const corneringDelta = computed(() => {
  return (aero.value * 1.5) + (diff.value * 0.5) // Higher aero = better cornering
})

const brakingDelta = computed(() => {
  return brakeBias.value === 0 ? 2 : -(Math.abs(brakeBias.value) * 1) // Balanced is best
})

useKeyboard((e: KeyboardEvent) => {
  if (e.key === 'ArrowDown') {
    cursorIndex.value = (cursorIndex.value + 1) % totalFields
    audio.playSfx('cursor_move')
  } else if (e.key === 'ArrowUp') {
    cursorIndex.value = (cursorIndex.value - 1 + totalFields) % totalFields
    audio.playSfx('cursor_move')
  } else if (e.key === 'ArrowLeft') {
    if (cursorIndex.value === 0) rotateValue(aero, aeroOptions, -1)
    else if (cursorIndex.value === 1) rotateValue(brakeBias, brakeBiasOptions, -1)
    else if (cursorIndex.value === 2) rotateValue(diff, diffOptions, -1)
  } else if (e.key === 'ArrowRight') {
    if (cursorIndex.value === 0) rotateValue(aero, aeroOptions, 1)
    else if (cursorIndex.value === 1) rotateValue(brakeBias, brakeBiasOptions, 1)
    else if (cursorIndex.value === 2) rotateValue(diff, diffOptions, 1)
  } else if (e.key === 'Enter' || e.key === 'a') {
    if (cursorIndex.value === 3) {
      audio.playSfx('cursor_select')
      router.back() // Apply & Return
    }
  } else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.back()
  }
})

const rotateValue = (model: any, options: any[], dir: number) => {
  const currentIdx = options.findIndex(o => o.id === model.value)
  let nextIdx = currentIdx + dir
  if (nextIdx < 0) nextIdx = options.length - 1
  if (nextIdx >= options.length) nextIdx = 0
  model.value = options[nextIdx].id
  audio.playSfx('cursor_move')
}

const formatDelta = (val: number) => {
  if (val > 0) return `+${val.toFixed(1)}%`
  if (val < 0) return `${val.toFixed(1)}%`
  return '0.0%'
}

const deltaColor = (val: number) => {
  if (val > 0) return 'text-ui-good'
  if (val < 0) return 'text-ui-warn'
  return 'text-slate'
}
</script>

<template>
  <PageShell title="CAR SETUP" :hints="['A · APPLY', 'B · BACK', '▲ ▼ ◀ ▶ TUNE']" bg="neutral">
    <template #heading>
      <div class="heading-block mb-[1.5vh]">
        <h1 class="text-title font-title text-silver tracking-[0.2em]">SETUP & TUNING</h1>
        <div class="heading-rule"></div>
      </div>
    </template>

    <CyberSplitView split="60-40" class="flex-grow mx-2 pb-6 min-h-0">
      
      <template #left>
        <CyberPanel class="flex flex-col h-full overflow-hidden p-4 min-h-0 relative group border-charcoal">
          <!-- Wireframe Car Background Overlay -->
          <div class="absolute inset-0 opacity-10 pointer-events-none flex items-center justify-center">
            <svg viewBox="0 0 100 50" class="w-[80%] h-auto stroke-white fill-none stroke-2">
              <path d="M 10 35 L 20 20 L 40 15 L 70 15 L 90 25 L 95 35 Z" />
              <circle cx="25" cy="35" r="8" />
              <circle cx="80" cy="35" r="8" />
              <line x1="20" y1="20" x2="40" y2="15" />
            </svg>
          </div>

          <div class="text-silver font-bold uppercase mb-6 border-b border-charcoal pb-2 tracking-widest z-10 relative">
            PARAMETERS
          </div>

          <div class="flex flex-col gap-6 z-10 relative">
            <div class="p-2 border border-transparent transition-colors" :class="{ 'bg-charcoal border-slate': cursorIndex === 0 }">
              <CyberValuePicker 
                label="AERODYNAMICS"
                :value="aeroOptions.find(o => o.id === aero)?.label || ''"
                :focused="cursorIndex === 0"
                :editing="true"
                labelWidth="140px"
                class="flex-grow"
              />
            </div>

            <div class="p-2 border border-transparent transition-colors" :class="{ 'bg-charcoal border-slate': cursorIndex === 1 }">
              <CyberValuePicker 
                label="BRAKE BIAS"
                :value="brakeBiasOptions.find(o => o.id === brakeBias)?.label || ''"
                :focused="cursorIndex === 1"
                :editing="true"
                labelWidth="140px"
                class="flex-grow"
              />
            </div>

            <div class="p-2 border border-transparent transition-colors" :class="{ 'bg-charcoal border-slate': cursorIndex === 2 }">
              <CyberValuePicker 
                label="DIFFERENTIAL"
                :value="diffOptions.find(o => o.id === diff)?.label || ''"
                :focused="cursorIndex === 2"
                :editing="true"
                labelWidth="140px"
                class="flex-grow"
              />
            </div>

            <div class="mt-8 p-3 border-t border-charcoal text-center" :class="{ 'bg-ui-good text-ink font-bold animate-pulse': cursorIndex === 3, 'text-silver': cursorIndex !== 3 }">
              <span v-if="cursorIndex === 3">▶ </span>APPLY TO ECU<span v-if="cursorIndex === 3"> ◀</span>
            </div>
          </div>
        </CyberPanel>
      </template>

      <template #right>
        <CyberPanel class="flex flex-col h-full bg-ink border-slate p-3">
          <div class="text-silver font-bold uppercase mb-4 border-b border-charcoal pb-2">PREDICTED IMPACT</div>
          
          <div class="flex flex-col gap-6 font-mono text-body mt-2">
            <div class="flex flex-col">
              <span class="text-slate text-small mb-1">TOP SPEED</span>
              <span class="font-bold text-title-sm" :class="deltaColor(topSpeedDelta)">{{ formatDelta(topSpeedDelta) }}</span>
            </div>
            
            <div class="flex flex-col">
              <span class="text-slate text-small mb-1">CORNERING G</span>
              <span class="font-bold text-title-sm" :class="deltaColor(corneringDelta)">{{ formatDelta(corneringDelta) }}</span>
            </div>
            
            <div class="flex flex-col">
              <span class="text-slate text-small mb-1">BRAKING EFFICIENCY</span>
              <span class="font-bold text-title-sm" :class="deltaColor(brakingDelta)">{{ formatDelta(brakingDelta) }}</span>
            </div>
          </div>

          <div class="mt-auto pt-4 border-t border-charcoal text-small text-slate">
            These projections are calculated by the onboard AI telemetry processor based on theoretical track conditions.
          </div>
        </CyberPanel>
      </template>

    </CyberSplitView>
  </PageShell>
</template>
