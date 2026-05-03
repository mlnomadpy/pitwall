<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useSaveStore } from '@/entities/save/model/saveStore'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import StatusBar from '@/widgets/status-bar/StatusBar.vue'
import HintBar from '@/widgets/hint-bar/HintBar.vue'
import CoachCard from './ui/CoachCard.vue'
import DialogueBox from '@/widgets/dialogue-box/DialogueBox.vue'

const router = useRouter()
const save = useSaveStore()
const audio = useAudioStore()

const coaches = [
  { id: 'trod', name: 'T-ROD', levelReq: 1, quote: "Distance is king. Smooth is fast." },
  { id: 'buddy', name: 'BUDDY', levelReq: 1, quote: "Hey pal, let's just keep the car out of the wall today." },
  { id: 'drill', name: 'DRILL SGT', levelReq: 5, quote: "YOU ARE BRAKING TOO EARLY! DO YOU WANT TO WIN?" },
  { id: 'bentley', name: 'BENTLEY', levelReq: 10, quote: "The slip angle vector is sub-optimal through Turn 4." },
  { id: 'calm', name: 'CALM PRO', levelReq: 15, quote: "Breathe. Feel the grip through your hands." }
]

const cursorIndex = ref(0)
const previewQuote = ref<string | null>(null)
const previewingId = ref<string | null>(null)
const lockedMessage = ref<string | null>(null)

const activeSlot = save.slots[save.activeSlotId! - 1]

onMounted(() => {
  if (activeSlot) {
    const idx = coaches.findIndex(c => c.id === activeSlot.preferredCoach)
    if (idx !== -1) cursorIndex.value = idx
  }
  window.addEventListener('keydown', handleKey)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKey)
})

const handleKey = (e: KeyboardEvent) => {
  if (lockedMessage.value || previewQuote.value) {
    // If dialogue is playing, let it consume the event (it has a stopPropagation)
    return
  }

  if (e.key === 'ArrowRight') {
    cursorIndex.value = (cursorIndex.value + 1) % coaches.length
    previewCoach(cursorIndex.value)
  } else if (e.key === 'ArrowLeft') {
    cursorIndex.value = (cursorIndex.value - 1 + coaches.length) % coaches.length
    previewCoach(cursorIndex.value)
  } else if (e.key === 'Enter') {
    trySelect(coaches[cursorIndex.value])
  } else if (e.key === 'Escape' || e.key === 'Backspace') {
    audio.playSfx('cancel')
    router.push('/garage')
  }
}

const previewCoach = (index: number) => {
  audio.playSfx('cursor_move')
  const c = coaches[index]
  if (activeSlot.level >= c.levelReq) {
    previewingId.value = c.id
    previewQuote.value = c.quote
  }
}

const trySelect = async (c: any) => {
  if (activeSlot.level < c.levelReq) {
    audio.playSfx('cancel')
    lockedMessage.value = `Reach LEVEL ${c.levelReq} to recruit ${c.name}.`
    return
  }
  
  if (activeSlot.preferredCoach === c.id) {
    audio.playSfx('cursor_select')
    router.push('/garage')
    return
  }
  
  audio.playSfx('cursor_select')
  audio.playVoice(activeSlot.preferredCoach, 'farewell_swap')
  
  activeSlot.preferredCoach = c.id
  
  setTimeout(() => {
    audio.playVoice(c.id, 'greet_morning')
    router.push('/garage')
  }, 1000)
}
</script>

<template>
  <div class="viewport pixelated relative w-full h-full bg-ink text-silver overflow-hidden">
    <StatusBar />
    
    <!-- Background -->
    <div class="coach-bg absolute inset-0 z-0"></div>
    
    <div class="content flex flex-col pt-[6vh] pb-[6vh] items-center relative z-10 h-full justify-center">
      <!-- Heading -->
      <div class="heading-block mb-[2vmin]">
        <h1 class="text-title font-title text-silver tracking-[0.3em]">COACHES</h1>
        <div class="heading-rule"></div>
      </div>
      
      <div class="grid grid-cols-3 gap-[1.5vmin] mb-[1.5vmin]">
        <CoachCard 
          v-for="(c, i) in coaches.slice(0, 3)" 
          :key="c.id"
          :id="c.id"
          :name="c.name"
          :level-req="c.levelReq"
          :focused="cursorIndex === i"
          :selected="activeSlot?.preferredCoach === c.id"
          :locked="activeSlot?.level < c.levelReq"
        />
      </div>
      
      <div class="grid grid-cols-2 gap-[1.5vmin]">
        <CoachCard 
          v-for="(c, i) in coaches.slice(3, 5)" 
          :key="c.id"
          :id="c.id"
          :name="c.name"
          :level-req="c.levelReq"
          :focused="cursorIndex === (i + 3)"
          :selected="activeSlot?.preferredCoach === c.id"
          :locked="activeSlot?.level < c.levelReq"
        />
      </div>
    </div>
    
    <DialogueBox 
      v-if="lockedMessage"
      :coach-id="'trod'"
      emotion="idle"
      :text="lockedMessage"
      @done="lockedMessage = null"
    />
    <DialogueBox 
      v-else-if="previewQuote && previewingId"
      :coach-id="previewingId"
      emotion="talk"
      :text="previewQuote"
      @done="previewQuote = null"
    />
    
    <HintBar :hints="['A · SELECT', 'B · GARAGE', '◀ ▶ MOVE']" />
  </div>
</template>

<style scoped>
.coach-bg {
  background: linear-gradient(
    180deg,
    var(--color-ink) 0%,
    var(--color-asphalt-deep) 50%,
    var(--color-ink) 100%
  );
}


</style>
