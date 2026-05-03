<script setup lang="ts">
import { ref, watch, onUnmounted } from 'vue'
import type { Cue } from '../model/cueStore'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'

const props = defineProps<{
  cue: Cue | null
}>()

const audio = useAudioStore()
const displayedText = ref('')
let typeInterval: number | null = null

watch(() => props.cue, (newCue) => {
  if (newCue) {
    if (typeInterval) clearInterval(typeInterval)
    let i = 0
    displayedText.value = ''
    typeInterval = window.setInterval(() => {
      if (i < newCue.text.length) {
        displayedText.value += newCue.text.charAt(i)
        if (i % 2 === 0) audio.playSfx('dialogue_blip')
        i++
      } else {
        clearInterval(typeInterval!)
      }
    }, 30)
  } else {
    displayedText.value = ''
  }
}, { immediate: true })

onUnmounted(() => {
  if (typeInterval) clearInterval(typeInterval)
})
</script>

<template>
  <div class="cue-band">
    <div v-if="cue" class="cue-text text-body text-ui-info ">
      {{ displayedText }}
    </div>
    <div v-else class="cue-idle text-body text-silver/40">
      NO CUES YET...
    </div>
  </div>
</template>

<style scoped>
.cue-band {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  height: clamp(40px, 8vh, 64px);
  background: linear-gradient(180deg, rgba(42, 47, 66, 0.9) 0%, rgba(26, 29, 46, 0.95) 100%);
  border-top: 2px solid var(--color-slate);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 clamp(10px, 3vw, 28px);
  font-family: var(--font-ui);
  z-index: 50;
  box-shadow: 0 -4px 12px rgba(0, 0, 0, 0.3);
}

.cue-text {
  font-weight: bold;
  letter-spacing: 0.05em;
}

.cue-idle {
  font-style: italic;
}
</style>
