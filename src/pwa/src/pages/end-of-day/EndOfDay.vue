<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useSaveStore } from '@/entities/save/model/saveStore'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'
import DialogueBox from '@/widgets/dialogue-box/DialogueBox.vue'

const router = useRouter()
const save = useSaveStore()
const audio = useAudioStore()

const phase = ref(0)
const maxPhase = 8

// Mock data
const tally = [
  { label: 'SESSIONS', value: '3' },
  { label: 'TOTAL LAPS', value: '23' },
  { label: 'BEST LAP', value: '1:46.8 (NEW PB ✓)' },
  { label: 'MEDALS EARNED', value: '2 ★ ★' },
  { label: 'LEVEL PROGRESS', value: 'LV 12 → 13 (4 to go)' }
]

const progressSequence = async () => {
  for (let i = 1; i <= 5; i++) {
    if (phase.value > i) continue
    await new Promise(r => setTimeout(r, 400))
    if (phase.value > i) continue // check if skipped
    phase.value = i
    audio.playSfx('score_tick')
  }
  
  if (phase.value <= 5) {
    await new Promise(r => setTimeout(r, 800))
    if (phase.value <= 5) {
      phase.value = 6
    }
  }
}

onMounted(() => {
  progressSequence()
  window.addEventListener('keydown', handleKey)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKey)
})

const handleKey = (e: KeyboardEvent) => {
  if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    // Cancel
    audio.playSfx('cancel')
    router.push('/garage')
  } else if (e.key === 'Enter' || e.key === 'a') {
    // Skip
    if (phase.value < 5) {
      phase.value = 5
      audio.playSfx('cursor_select')
    } else if (phase.value === 6) {
      // Skipping dialogue
      onDialogueDone()
    }
  }
}

const onDialogueDone = () => {
  if (phase.value < 7) {
    phase.value = 7
    // Mock play night_chime sound
    setTimeout(() => {
      // End day
      save.activeSlotId = null
      router.push('/')
    }, 2000)
  }
}
</script>

<template>
  <div class="viewport pixelated relative w-full h-full bg-ink text-silver overflow-hidden  font-ui flex flex-col items-center justify-center p-4">
    <!-- Starry background mock -->
    <div class="absolute inset-0 z-0 bg-asphalt">
       <div class="absolute w-[2px] h-[2px] bg-white rounded-full left-[20%] top-[30%] opacity-50"></div>
       <div class="absolute w-[2px] h-[2px] bg-white rounded-full left-[80%] top-[40%] opacity-80 animate-pulse"></div>
       <div class="absolute w-[2px] h-[2px] bg-white rounded-full left-[50%] top-[20%] opacity-30"></div>
       <div class="absolute w-[2px] h-[2px] bg-white rounded-full left-[30%] top-[70%] opacity-60"></div>
       <div class="absolute w-[2px] h-[2px] bg-white rounded-full left-[70%] top-[80%] opacity-40"></div>
    </div>
    
    <div class="relative z-10 w-full h-full flex flex-col items-center">
      <h1 class="text-title-lg text-white font-bold mb-4 mt-8 tracking-widest drop-shadow-[2px_2px_0_#000]">END OF DAY</h1>

      <CyberPanel class="w-[clamp(240px,75vw,400px)] p-4 flex flex-col gap-2 bg-ink border-slate">
        <h2 class="text-body text-silver border-b border-slate pb-1 mb-1 font-bold">TODAY</h2>
        
        <div class="flex flex-col gap-1 text-body">
          <div 
            v-for="(item, i) in tally" 
            :key="item.label"
            class="flex justify-between transition-opacity duration-300"
            :class="phase > i ? 'opacity-100' : 'opacity-0'"
          >
            <span class="text-charcoal-light">{{ item.label }}</span>
            <span class="font-bold text-white">{{ item.value }}</span>
          </div>
        </div>
      </CyberPanel>
      
      <div v-if="phase >= 6" class="absolute bottom-[6vh] left-0 right-0 w-[clamp(300px,90vw,500px)] mx-auto">
        <!-- Floating Z -->
        <div class="absolute right-[4vw] -top-[2vh] text-white text-title font-bold animate-bounce z-20 drop-shadow-[2px_2px_0_#000]">
          Z
        </div>
        
        <DialogueBox 
          :coach-id="save.slots[save.activeSlotId?-1:0]?.preferredCoach ?? 'trod'"
          emotion="idle"
          text="Same time tomorrow, kid."
          @done="onDialogueDone"
        />
      </div>
    </div>

    <!-- Fade to Night Overlay -->
    <div 
      class="absolute inset-0 bg-ink pointer-events-none z-50 transition-opacity duration-[1500ms]"
      :class="phase >= 7 ? 'opacity-100' : 'opacity-0'"
    ></div>
  </div>
</template>

<style scoped>
/* No hardcoded viewport dimensions — fullscreen is enforced by global.css */
</style>
