<script setup lang="ts">
import { onMounted, ref } from 'vue'
import CoachCard from '@/shared/ui/CoachCard.vue'
import { useTypewriter } from '@/shared/lib/useTypewriter'
import { useKeyboard } from '@/shared/lib/useKeyboard'

const props = defineProps<{
  coachId: string
  emotion: string
  text: string
  compact?: boolean
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
  <div class="dialogue-box pixelated" :class="{ compact }" @keydown.stop>
    <div class="dialogue-layout" @click="handleClick">
      <!-- Coach NPC — stands next to speech bubble -->
      <div class="npc-column">
        <div class="npc-character">
          <CoachCard 
            :id="coachId" 
            :animation="emotion || 'idle'" 
            :paused="!isTyping"
            portrait-only
          />
        </div>
        <div class="npc-shadow" aria-hidden="true"></div>
      </div>
      
      <!-- Speech bubble -->
      <div class="speech-bubble">
        <div class="speech-header">
          <span class="speaker-name">{{ coachId.toUpperCase() }}</span>
          <span v-if="isTyping" class="typing-dots" aria-label="typing">···</span>
        </div>
        <div class="dialogue-text text-body">
          {{ displayedText }}
          <span v-if="!isTyping" class="advance-indicator">▼</span>
        </div>
        
        <!-- Voice Input / Mic -->
        <button 
          class="mic-button"
          :class="isListening ? 'mic-active' : ''"
          @click="toggleListen"
          aria-label="Toggle voice input"
        >
          <svg xmlns="http://www.w3.org/2000/svg" class="mic-icon" :class="isListening ? 'text-ui-bad animate-pulse' : 'text-silver'" viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 14c1.66 0 3-1.34 3-3V5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3z"/>
            <path d="M17 11c0 2.76-2.24 5-5 5s-5-2.24-5-5H5c0 3.53 2.61 6.43 6 6.92V21h2v-3.08c3.39-.49 6-3.39 6-6.92h-2z"/>
          </svg>
        </button>
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
  pointer-events: none; /* Let clicks pass through to tiles behind */
}

.dialogue-box.compact {
  transform: scale(0.85);
  transform-origin: bottom left;
  width: 117%;
}

.dialogue-layout {
  display: flex;
  align-items: flex-end;
  gap: 0;
  pointer-events: auto; /* Only the actual bubble captures clicks */
  width: fit-content; /* Don't stretch to full width */
}

/* ── NPC Character Column ── */
.npc-column {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  z-index: 2;
  margin-right: clamp(-4px, -0.5vw, -2px); /* Overlap speech bubble slightly */
}

.npc-character {
  width: clamp(52px, 12vmin, 88px);
  height: clamp(52px, 12vmin, 88px);
  display: flex;
  align-items: flex-end;
  justify-content: center;
  filter: drop-shadow(0 4px 8px rgba(0, 0, 0, 0.6));
  transition: transform 0.2s ease;
}

.dialogue-box:active .npc-character {
  transform: scale(0.95);
}

.npc-shadow {
  width: clamp(36px, 8vmin, 56px);
  height: clamp(4px, 0.8vmin, 6px);
  background: radial-gradient(ellipse at center, rgba(0, 0, 0, 0.6) 0%, transparent 70%);
  margin-top: -2px;
  filter: blur(2px);
}

/* ── Speech Bubble ── */
.speech-bubble {
  flex: 1;
  border: 2px solid var(--color-slate);
  background-color: var(--color-ink);
  padding: clamp(8px, 2vmin, 16px);
  display: flex;
  flex-direction: column;
  gap: clamp(4px, 0.8vmin, 8px);
  min-height: clamp(48px, 8vh, 80px);
  box-shadow: 4px 4px 0 rgba(0, 0, 0, 0.8);
  position: relative;
  clip-path: polygon(8px 0, 100% 0, 100% calc(100% - 8px), calc(100% - 8px) 100%, 0 100%, 0 8px);
}

/* Speech tail pointing to NPC */
.speech-bubble::before {
  content: '';
  position: absolute;
  left: -8px;
  bottom: clamp(12px, 2.5vmin, 20px);
  width: 0;
  height: 0;
  border-top: 6px solid transparent;
  border-bottom: 6px solid transparent;
  border-right: 8px solid var(--color-slate);
}

.speech-bubble::after {
  content: '';
  position: absolute;
  left: -5px;
  bottom: clamp(13px, 2.6vmin, 21px);
  width: 0;
  height: 0;
  border-top: 5px solid transparent;
  border-bottom: 5px solid transparent;
  border-right: 6px solid var(--color-ink);
}

.speech-header {
  display: flex;
  align-items: center;
  gap: clamp(4px, 1vmin, 8px);
}

.speaker-name {
  font-size: clamp(9px, 2vmin, 16px);
  color: var(--color-ui-good);
  font-weight: bold;
  letter-spacing: 0.1em;
}

.typing-dots {
  color: var(--color-ui-info);
  animation: dots-blink 1s steps(3) infinite;
  font-size: 1.2em;
  letter-spacing: 2px;
}

.dialogue-text {
  color: var(--color-silver);
  flex: 1;
  line-height: 1.5;
}

.advance-indicator {
  color: var(--color-ui-info);
  margin-left: clamp(4px, 1vmin, 8px);
  animation: advance-blink 1s steps(2) infinite;
}

/* Mic button */
.mic-button {
  position: absolute;
  top: clamp(6px, 1.5vmin, 12px);
  right: clamp(6px, 1.5vmin, 12px);
  width: clamp(28px, 6vmin, 36px);
  height: clamp(28px, 6vmin, 36px);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(44, 62, 80, 0.3);
  border: 1px solid var(--color-slate);
  cursor: pointer;
  transition: all 0.2s ease;
  -webkit-tap-highlight-color: transparent;
}

.mic-active {
  background: rgba(255, 71, 87, 0.15);
  border-color: var(--color-ui-bad);
  box-shadow: 0 0 12px rgba(255, 71, 87, 0.3);
}

.mic-icon {
  width: 50%;
  height: 50%;
}

@keyframes advance-blink {
  0%, 100% { opacity: 0; }
  50% { opacity: 1; }
}

@keyframes dots-blink {
  0% { opacity: 0.3; }
  50% { opacity: 1; }
  100% { opacity: 0.3; }
}
</style>
