<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import Sprite from '@/entities/coach/Sprite.vue'

const props = defineProps<{
  initialSkill: string
}>()

const emit = defineEmits(['next', 'back', 'update:skill'])
const audio = useAudioStore()

const skills = [
  { id: 'beginner', label: 'BEGINNER', desc: '"We\'ll start from the absolute basics."' },
  { id: 'intermediate', label: 'INTERMEDIATE', desc: '"You know the racing line. Let\'s find time."' },
  { id: 'pro', label: 'PRO', desc: '"We are chasing tenths. No mercy."' },
]

const cursorIndex = ref(skills.findIndex(s => s.id === props.initialSkill) !== -1 ? skills.findIndex(s => s.id === props.initialSkill) : 0)

const handleKey = (e: KeyboardEvent) => {
  if (e.key === 'ArrowRight' || e.key === 'ArrowDown') {
    cursorIndex.value = (cursorIndex.value + 1) % skills.length
    audio.playSfx('cursor_move')
    emit('update:skill', skills[cursorIndex.value].id)
  } else if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') {
    cursorIndex.value = (cursorIndex.value - 1 + skills.length) % skills.length
    audio.playSfx('cursor_move')
    emit('update:skill', skills[cursorIndex.value].id)
  } else if (e.key === 'Enter') {
    audio.playSfx('cursor_select')
    emit('next')
  } else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    emit('back')
  }
}

const selectSkill = (index: number) => {
  cursorIndex.value = index
  emit('update:skill', skills[index].id)
  audio.playSfx('cursor_select')
  emit('next')
}

onMounted(() => {
  window.addEventListener('keydown', handleKey)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKey)
})
</script>

<template>
  <div class="skill-select">
    <div class="select-label text-small text-silver tracking-[0.2em]">SELECT EXPERIENCE</div>
    
    <div class="skill-list">
      <div 
        v-for="(skill, i) in skills" 
        :key="i"
        class="skill-option"
        :class="cursorIndex === i ? 'option-focused' : 'option-default'"
        @click="selectSkill(i)"
      >
        <span v-if="cursorIndex === i" class="option-cursor">▶</span>
        <span v-else class="option-cursor option-invisible">&nbsp;</span>
        <span class="option-label text-body">{{ skill.label }}</span>
      </div>
    </div>
    
    <!-- Coach preview -->
    <div class="coach-preview">
      <Sprite sheet="trod" :animation="cursorIndex === 0 ? 'talk' : 'idle'" class="scale-75" />
      <div class="coach-quote text-body text-silver">
        {{ skills[cursorIndex].desc }}
      </div>
    </div>
  </div>
</template>

<style scoped>
.skill-select {
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
  padding-top: clamp(8px, 2vh, 20px);
  font-family: var(--font-ui);
  gap: clamp(8px, 2vh, 20px);
}

.select-label {
  margin-bottom: clamp(2px, 0.5vh, 6px);
}

.skill-list {
  display: flex;
  flex-direction: column;
  gap: clamp(4px, 1vh, 12px);
  width: clamp(220px, 80vw, 450px);
}

.skill-option {
  display: flex;
  align-items: center;
  gap: clamp(6px, 1.5vw, 14px);
  border: 2px solid var(--color-slate);
  background: linear-gradient(180deg, rgba(42, 47, 66, 0.8) 0%, rgba(31, 34, 48, 0.9) 100%);
  padding: clamp(8px, 2vh, 18px) clamp(10px, 2.5vw, 24px);
  cursor: pointer;
  transition: all 0.15s ease;
  text-transform: uppercase;
  -webkit-tap-highlight-color: transparent;
}

.option-focused {
  border-color: var(--color-ui-good);
  background: linear-gradient(180deg, rgba(42, 161, 152, 0.12) 0%, rgba(31, 34, 48, 0.95) 100%);
  box-shadow: 0 0 0 1px var(--color-ui-good), 0 0 12px rgba(42, 161, 152, 0.25);
  color: white;
  font-weight: bold;
}

.option-default {
  color: var(--color-silver);
}

.option-default:active {
  background: rgba(42, 161, 152, 0.1);
}

.option-cursor {
  color: var(--color-ui-good);
  text-shadow: 0 0 6px rgba(42, 161, 152, 0.6);
  font-size: clamp(10px, 2.5vmin, 20px);
  flex-shrink: 0;
  animation: cursor-bounce 0.25s steps(2) infinite;
}

.option-invisible { visibility: hidden; }

@keyframes cursor-bounce {
  0%, 100% { transform: translateX(0); }
  50% { transform: translateX(2px); }
}

.coach-preview {
  display: flex;
  align-items: center;
  gap: clamp(8px, 2vw, 20px);
  margin-top: auto;
  margin-bottom: clamp(4px, 1vh, 12px);
  padding: 0 clamp(12px, 3vw, 28px);
}

.coach-quote {
  max-width: clamp(160px, 50vw, 320px);
  font-style: italic;
}
</style>
