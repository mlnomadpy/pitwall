<script setup lang="ts">
import { onMounted, ref } from 'vue'
import CoachCard from '@/shared/ui/CoachCard.vue'
import { useTypewriter } from '@/shared/lib/useTypewriter'
import { useKeyboard } from '@/shared/lib/useKeyboard'

const props = defineProps<{
  coachId: string
  emotion: string
  text: string
}>()

const emit = defineEmits<{ (e: 'done'): void }>()

const { displayedText, isTyping, start, complete } = useTypewriter()
const isListening = ref(false)

onMounted(() => {
  start(props.text)
})

useKeyboard((e: KeyboardEvent) => {
  if (e.key === 'Enter') {
    // Only intercept Enter — let Escape and other keys propagate to PauseMenu etc.
    e.stopPropagation()
    
    if (isTyping.value) {
      complete()
    } else {
      emit('done')
    }
  }
})

const handleClick = () => {
  if (isTyping.value) {
    complete()
  } else {
    emit('done')
  }
}

const toggleListen = (e: Event) => {
  e.stopPropagation()
  isListening.value = !isListening.value
}
</script>

<template>
  <div class="dialogue-box pixelated" @keydown.stop @click="handleClick">
    <div class="dialogue-inner flex items-center">
      <!-- Portrait frame -->
      <div class="portrait-frame">
        <CoachCard 
          :id="coachId" 
          :animation="emotion || 'idle'" 
          :paused="!isTyping"
          portrait-only
        />
      </div>
      
      <!-- Text area -->
      <div class="text-area">
        <div class="speaker-name">{{ coachId.toUpperCase() }}</div>
        <div class="dialogue-text text-body">
          {{ displayedText }}
          <span v-if="!isTyping" class="advance-indicator">▼</span>
        </div>
      </div>
      
      <!-- Voice Input Bar / Microphone -->
      <button 
        class="mic-button flex-shrink-0 w-[clamp(36px,8vmin,48px)] h-[clamp(36px,8vmin,48px)] rounded-full flex items-center justify-center transition-all duration-300 ml-2"
        :class="isListening ? 'bg-ui-bad/20 border-2 border-ui-bad shadow-[0_0_15px_rgba(255,71,87,0.4)]' : 'bg-slate/20 border border-slate hover:bg-slate/40'"
        @click="toggleListen"
      >
        <svg xmlns="http://www.w3.org/2000/svg" class="w-1/2 h-1/2" :class="isListening ? 'text-ui-bad animate-pulse' : 'text-silver'" viewBox="0 0 24 24" fill="currentColor">
          <path d="M12 14c1.66 0 3-1.34 3-3V5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3z"/>
          <path d="M17 11c0 2.76-2.24 5-5 5s-5-2.24-5-5H5c0 3.53 2.61 6.43 6 6.92V21h2v-3.08c3.39-.49 6-3.39 6-6.92h-2z"/>
        </svg>
      </button>
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
  background-color: var(--color-ink);
  padding: clamp(6px, 1.5vmin, 16px);
  display: flex;
  gap: clamp(8px, 2vmin, 20px);
  min-height: clamp(56px, 10vh, 100px);
  box-shadow: 4px 4px 0 rgba(0, 0, 0, 0.8);
  position: relative;
  overflow: hidden;
}

/* Subtle top-edge highlight (Removed for strict retro) */

.portrait-frame {
  flex-shrink: 0;
  width: clamp(40px, 8vmin, 72px);
  height: clamp(40px, 8vmin, 72px);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
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
  animation: advance-blink 1s steps(2) infinite;
}

@keyframes advance-blink {
  0%, 100% { opacity: 0; }
  50% { opacity: 1; }
}
</style>
