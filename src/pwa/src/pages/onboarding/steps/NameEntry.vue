<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'

const props = defineProps<{
  initialName: string
}>()

const emit = defineEmits(['next', 'back', 'update:name'])
const audio = useAudioStore()

const chars = [
  'A','B','C','D','E','F','G',
  'H','I','J','K','L','M','N',
  'O','P','Q','R','S','T','U',
  'V','W','X','Y','Z','.',
  '_','-',' ','DEL','END'
]

const cursorX = ref(0)
const cursorY = ref(0)
const maxCols = 7
const currentName = ref(props.initialName)
const errorMessage = ref('')

const handleKey = (e: KeyboardEvent) => {
  errorMessage.value = ''
  
  // Accept direct keyboard letter input
  if (e.key.length === 1 && /[a-zA-Z0-9 ._\-]/.test(e.key)) {
    if (currentName.value.length < 12) {
      currentName.value += e.key.toUpperCase()
      emit('update:name', currentName.value)
      audio.playSfx('cursor_select')
    }
    return
  }
  
  if (e.key === 'ArrowRight') {
    cursorX.value = (cursorX.value + 1) % maxCols
    audio.playSfx('cursor_move')
  } else if (e.key === 'ArrowLeft') {
    cursorX.value = (cursorX.value - 1 + maxCols) % maxCols
    audio.playSfx('cursor_move')
  } else if (e.key === 'ArrowDown') {
    cursorY.value = (cursorY.value + 1) % 5
    audio.playSfx('cursor_move')
  } else if (e.key === 'ArrowUp') {
    cursorY.value = (cursorY.value - 1 + 5) % 5
    audio.playSfx('cursor_move')
  } else if (e.key === 'Enter') {
    selectChar()
  } else if (e.key === 'Escape' || e.key === 'Backspace') {
    if (currentName.value.length > 0) {
      currentName.value = currentName.value.slice(0, -1)
      emit('update:name', currentName.value)
      audio.playSfx('cancel')
    } else {
      audio.playSfx('cancel')
      emit('back')
    }
  }
}

const selectChar = () => {
  const idx = cursorY.value * maxCols + cursorX.value
  const char = chars[idx]
  if (!char) return
  
  audio.playSfx('cursor_select')
  
  if (char === 'DEL') {
    currentName.value = currentName.value.slice(0, -1)
  } else if (char === 'END') {
    if (currentName.value.trim().length === 0) {
      errorMessage.value = 'NAME CANNOT BE EMPTY'
      audio.playSfx('cancel')
    } else {
      emit('next')
    }
  } else {
    if (currentName.value.length < 12) {
      currentName.value += char === ' ' ? ' ' : char
    }
  }
  emit('update:name', currentName.value)
}

const onCharClick = (index: number) => {
  cursorY.value = Math.floor(index / maxCols)
  cursorX.value = index % maxCols
  selectChar()
}

onMounted(() => {
  window.addEventListener('keydown', handleKey)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKey)
})
</script>

<template>
  <div class="name-entry">
    <div class="entry-label text-small text-silver tracking-[0.2em]">YOUR NAME</div>
    
    <!-- Name display field -->
    <div class="name-field">
      <span class="name-text text-title">{{ currentName }}<span class="cursor-blink">_</span></span>
    </div>
    
    <!-- Character grid -->
    <div class="char-grid">
      <div 
        v-for="(char, i) in chars" 
        :key="i"
        class="char-cell"
        :class="[
          cursorY * maxCols + cursorX === i 
            ? 'cell-focused' 
            : char === 'DEL' || char === 'END' ? 'cell-action' : 'cell-default'
        ]"
        @click="onCharClick(i)"
      >
        {{ char }}
      </div>
    </div>
    
    <!-- Keyboard hint -->
    <div class="keyboard-hint text-small text-slate">
      TYPE ON KEYBOARD · OR TAP LETTERS
    </div>
    
    <div v-if="errorMessage" class="error-msg text-body text-ui-warn">{{ errorMessage }}</div>
  </div>
</template>

<style scoped>
.name-entry {
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
  padding-top: clamp(8px, 2vh, 20px);
  font-family: var(--font-ui);
  gap: clamp(6px, 1.5vh, 16px);
}

.entry-label {
  margin-bottom: clamp(2px, 0.5vh, 6px);
}

.name-field {
  border: 2px solid var(--color-slate);
  background: linear-gradient(180deg, rgba(42, 47, 66, 0.8) 0%, rgba(31, 34, 48, 0.9) 100%);
  padding: clamp(6px, 1.5vh, 14px) clamp(12px, 3vw, 28px);
  min-width: clamp(140px, 40vw, 300px);
  text-align: center;
}

.name-text {
  font-weight: bold;
  color: white;
  letter-spacing: 0.15em;
}

.cursor-blink {
  animation: blink-cursor 0.6s steps(2) infinite;
  color: var(--color-ui-good);
}

@keyframes blink-cursor {
  0% { opacity: 1; }
  50% { opacity: 0; }
}

.char-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: clamp(3px, 0.8vmin, 8px);
  width: clamp(280px, 88vw, 560px);
}

.char-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: clamp(14px, 3.5vmin, 28px);
  min-height: clamp(32px, 7vmin, 52px);
  cursor: pointer;
  transition: all 0.1s ease;
  border: 1px solid transparent;
  user-select: none;
  -webkit-user-select: none;
  -webkit-tap-highlight-color: transparent;
}

.cell-default {
  color: var(--color-silver);
}

.cell-default:active {
  background: rgba(42, 161, 152, 0.2);
}

.cell-action {
  color: var(--color-ui-info);
  font-weight: bold;
}

.cell-action:active {
  background: rgba(74, 152, 200, 0.2);
}

.cell-focused {
  background: var(--color-ui-good);
  color: var(--color-ink);
  font-weight: bold;
  border-color: var(--color-ui-good);
  box-shadow: 0 0 8px rgba(42, 161, 152, 0.4);
}

.keyboard-hint {
  margin-top: clamp(2px, 0.5vh, 6px);
  letter-spacing: 0.1em;
}

.error-msg {
  margin-top: clamp(4px, 1vh, 10px);
  animation: shake 0.3s ease;
}

@keyframes shake {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-4px); }
  75% { transform: translateX(4px); }
}
</style>
