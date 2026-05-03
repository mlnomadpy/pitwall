<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import Sprite from '@/entities/coach/Sprite.vue'
import { useDialogueStore } from '@/features/coach-interaction/model/dialogueStore'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'

const props = defineProps<{
  coachId: string
  emotion: string
  text: string
}>()

const emit = defineEmits(['done'])

const store = useDialogueStore()
const audio = useAudioStore()
const displayedText = ref('')
const isTalking = ref(true)

let typeInterval: number

const startTyping = () => {
  store.queue = [props.text]
  store.next()
  
  let i = 0
  displayedText.value = ''
  isTalking.value = true
  
  typeInterval = window.setInterval(() => {
    if (i < store.currentText.length) {
      displayedText.value += store.currentText.charAt(i)
      if (i % 2 === 0) audio.playSfx('dialogue_blip')
      i++
    } else {
      clearInterval(typeInterval)
      isTalking.value = false
    }
  }, 30) // Teletype speed
}

onMounted(() => {
  startTyping()
  window.addEventListener('keydown', handleKey)
})

onUnmounted(() => {
  clearInterval(typeInterval)
  window.removeEventListener('keydown', handleKey)
})

const handleKey = (e: KeyboardEvent) => {
  // Prevent GarageHub tile enter logic if dialogue is active
  e.stopPropagation()
  
  if (e.key === 'Enter') {
    if (isTalking.value) {
      // Skip typing to end
      clearInterval(typeInterval)
      displayedText.value = store.currentText
      isTalking.value = false
    } else {
      emit('done')
    }
  }
}
</script>

<template>
  <div class="dialogue-box pixelated" @keydown.stop>
    <div class="dialogue-inner">
      <!-- Portrait frame -->
      <div class="portrait-frame">
        <div class="portrait-glow"></div>
        <Sprite :sheet="coachId" :animation="isTalking ? 'talk' : emotion" :scale="0.5" class="portrait-sprite" />
      </div>
      
      <!-- Text area -->
      <div class="text-area">
        <div class="speaker-name ">{{ coachId.toUpperCase() }}</div>
        <div class="dialogue-text text-body">
          {{ displayedText }}
          <span v-if="!isTalking" class="advance-indicator">▼</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dialogue-box {
  position: absolute;
  bottom: clamp(26px, 5vh, 50px); /* above hint bar */
  left: 0;
  width: 100%;
  padding: 0 clamp(8px, 2vw, 24px) clamp(4px, 1vh, 12px);
  z-index: 50;
}

.dialogue-inner {
  border: 2px solid var(--color-slate);
  background: linear-gradient(
    180deg,
    rgba(26, 29, 46, 0.97) 0%,
    rgba(13, 13, 18, 0.98) 100%
  );
  padding: clamp(6px, 1.5vmin, 16px);
  display: flex;
  gap: clamp(8px, 2vmin, 20px);
  min-height: clamp(56px, 10vh, 100px);
  box-shadow:
    inset 0 0 0 1px rgba(61, 68, 88, 0.3),
    0 -4px 16px rgba(0, 0, 0, 0.4);
  position: relative;
  overflow: hidden;
}

/* Subtle top-edge highlight */
.dialogue-inner::before {
  content: '';
  position: absolute;
  top: 0;
  left: 10%;
  width: 80%;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(42, 161, 152, 0.3), transparent);
}

.portrait-frame {
  flex-shrink: 0;
  width: clamp(40px, 8vmin, 72px);
  height: clamp(40px, 8vmin, 72px);
  background: var(--color-charcoal);
  border: 1px solid var(--color-slate);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  position: relative;
}

.portrait-glow {
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at center, rgba(42, 161, 152, 0.05) 0%, transparent 70%);
  pointer-events: none;
}

.portrait-sprite {
  position: absolute;
  top: -10px;
}

.text-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: clamp(2px, 0.5vmin, 6px);
}

.speaker-name {
  font-size: clamp(9px, 2vmin, 16px);
  color: var(--color-ui-good);
  font-weight: bold;
  letter-spacing: 0.1em;
}

.dialogue-text {
  color: var(--color-silver);
  flex: 1;
}

.advance-indicator {
  color: var(--color-ui-info);
  margin-left: clamp(4px, 1vmin, 8px);
  animation: advance-blink 1s ease-in-out infinite;
}

@keyframes advance-blink {
  0%, 100% { opacity: 0.4; }
  50% { opacity: 1; }
}
</style>
