<script setup lang="ts">
import CyberBox from '@/shared/ui/core/CyberBox.vue'
import CyberMedal from '@/shared/ui/core/CyberMedal.vue'

interface Medal {
  id: string
  tier: string
  name: string
  desc: string
  unlocked: boolean
}

const props = defineProps<{
  medals: Medal[]
  cursorIndex: number
}>()

const emit = defineEmits<{
  (e: 'select', index: number): void
}>()
</script>

<template>
  <div class="medal-grid-container">
    <CyberBox 
      v-for="(m, i) in medals" 
      :key="m.id"
      variant="ink"
      border="slate"
      :selected="cursorIndex === i"
      class="medal-cell"
      :class="[
        !m.unlocked ? 'locked' : '',
        cursorIndex === i ? 'focused' : ''
      ]"
      @click="emit('select', i)"
    >
      <CyberMedal 
        :tier="(m.tier as any)" 
        :unlocked="m.unlocked" 
      />
    </CyberBox>
  </div>
</template>

<style scoped>
.medal-grid-container {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: clamp(4px, 1vmin, 8px);
  align-content: start;
  overflow-y: auto;
  padding-bottom: clamp(4px, 1vmin, 8px);
  min-height: 0;
  height: 100%;
  scrollbar-width: none;
}

.medal-grid-container::-webkit-scrollbar { display: none; }

.medal-cell {
  position: relative;
  aspect-ratio: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.medal-cell.focused {
  transform: scale(1.08);
  box-shadow: 
    0 0 0 2px var(--color-ui-good),
    0 0 12px rgba(78, 205, 196, 0.4);
  z-index: 2;
}

.medal-cell.locked {
  opacity: 0.4;
}
</style>
