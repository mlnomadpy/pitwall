<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import { useRouter } from 'vue-router'
import { useSaveStore } from '@/entities/save/model/saveStore'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'
import DialogueBox from '@/widgets/dialogue-box/DialogueBox.vue'
import PageShell from '@/shared/ui/PageShell.vue'
import CyberBackground from '@/shared/ui/core/CyberBackground.vue'

const router = useRouter()
const save = useSaveStore()
const audio = useAudioStore()

const phase = ref(0)
let aborted = false
let navTimeout: number | null = null

const tally = [
  { label: 'SESSIONS', value: '3' },
  { label: 'TOTAL LAPS', value: '23' },
  { label: 'BEST LAP', value: '1:46.8 (NEW PB ✓)' },
  { label: 'MEDALS EARNED', value: '2 ★ ★' },
  { label: 'LEVEL PROGRESS', value: 'LV 12 → 13 (4 to go)' }
]

const progressSequence = async () => {
  for (let i = 1; i <= 5; i++) {
    if (phase.value > i || aborted) continue
    await new Promise(r => setTimeout(r, 400))
    if (phase.value > i || aborted) continue
    phase.value = i
    audio.playSfx('score_tick')
  }
  
  if (phase.value <= 5 && !aborted) {
    await new Promise(r => setTimeout(r, 800))
    if (phase.value <= 5 && !aborted) {
      phase.value = 6
    }
  }
}

onMounted(() => {
  progressSequence()
})

onUnmounted(() => {
  aborted = true
  if (navTimeout) clearTimeout(navTimeout)
})

useKeyboard((e: KeyboardEvent) => {
  if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.push('/garage')
  } else if (e.key === 'Enter' || e.key === 'a') {
    if (phase.value < 5) {
      phase.value = 5
      audio.playSfx('cursor_select')
    } else if (phase.value === 6) {
      onDialogueDone()
    }
  }
})

const onDialogueDone = () => {
  if (phase.value < 7) {
    phase.value = 7
    navTimeout = window.setTimeout(() => {
      save.activeSlotId = null
      router.push('/')
    }, 3000)
  }
}
</script>

<template>
  <PageShell :show-heading="false" bg="neutral" bgVariant="stars" :hints="['ENTER · NEXT', 'B · BACK']">
    <div class="relative w-full h-full flex flex-col items-center justify-center p-4 z-10" @click="phase < 5 ? (phase = 5, audio.playSfx('cursor_select')) : phase === 6 ? onDialogueDone() : null">
    
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
            <span class="text-slate">{{ item.label }}</span>
            <span class="font-bold text-white">{{ item.value }}</span>
          </div>
        </div>
      </CyberPanel>

      <!-- Tappable continue when tally is done -->
      <div v-if="phase >= 5 && phase < 6" class="mt-4 text-center cursor-pointer" @click.stop="phase = 6">
        <span class="text-ui-good font-bold text-body animate-pulse tracking-widest">TAP TO CONTINUE ▶</span>
      </div>
      
      <div v-if="phase >= 6" class="absolute bottom-[6vh] left-0 right-0 w-[clamp(300px,90vw,500px)] mx-auto">
        <!-- Floating Z -->
        <div class="absolute right-[4vw] -top-[2vh] text-white text-title font-bold floating-z z-20 drop-shadow-[2px_2px_0_#000]">
          Z
        </div>
        
        <DialogueBox 
          :coach-id="save.activeSlot?.preferredCoach ?? 'trod'"
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
  </PageShell>
</template>

<style scoped>
/* No hardcoded viewport dimensions — fullscreen is enforced by global.css */
@keyframes float-z {
  0%, 100% { transform: translateY(0) scale(1) rotate(-5deg); opacity: 0.8; }
  50% { transform: translateY(-10px) scale(1.1) rotate(5deg); opacity: 1; }
}

.floating-z {
  animation: float-z 3s ease-in-out infinite;
}
</style>
