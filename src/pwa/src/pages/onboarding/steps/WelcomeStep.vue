<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import DialogueBox from '@/widgets/dialogue-box/DialogueBox.vue'

const emit = defineEmits(['next'])
const audio = useAudioStore()

const handleKey = (e: KeyboardEvent) => {
  if (e.key === 'Enter') {
    audio.playSfx('cursor_select')
    emit('next')
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKey)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKey)
})
</script>

<template>
  <div class="welcome-step" @click="emit('next')">
    <!-- Center character label -->
    <div class="character-area">
      <div class="character-frame">
        <span class="text-title text-slate font-title">T-ROD</span>
      </div>
    </div>
    
    <DialogueBox 
      coach-id="trod"
      emotion="talk"
      text="Welcome to Pitwall, kid. Let's set you up."
    />
  </div>
</template>

<style scoped>
.welcome-step {
  display: flex;
  flex-direction: column;
  height: 100%;
  justify-content: flex-end;
  position: relative;
  cursor: pointer;
}

.character-area {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: -1;
  opacity: 0.3;
}

.character-frame {
  width: clamp(80px, 20vmin, 160px);
  height: clamp(80px, 20vmin, 160px);
  border: 1px solid var(--color-slate);
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
