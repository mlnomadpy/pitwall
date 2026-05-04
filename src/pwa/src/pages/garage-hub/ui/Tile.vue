<script setup lang="ts">
import CyberButton from '@/shared/ui/core/CyberButton.vue'

export interface TileData {
  id: string
  title: string
  subText: string
  route: string
  disabled?: boolean
}

defineProps<{
  tile: TileData
  focused: boolean
}>()

const emit = defineEmits<{
  (e: 'select'): void
  (e: 'hover'): void
}>()
</script>

<template>
  <CyberButton
    :disabled="tile.disabled"
    fluid
    variant="secondary"
    :active="focused"
    :subText="tile.subText"
    @click="!tile.disabled && emit('select')"
    @mouseover="!tile.disabled && emit('hover')"
    class="w-full h-[clamp(60px,12vh,100px)]"
  >
    <template #icon>
      <span v-if="focused" class="cursor-bounce text-ui-warn absolute left-[10px]">▶</span>
    </template>
    {{ tile.title }}
  </CyberButton>
</template>

<style scoped>
/* Scoped styles removed because PixelButton handles the full presentation now */
.cursor-bounce {
  text-shadow: 1px 1px 0 #0d0d12;
  font-size: clamp(14px, 3vmin, 20px);
}
</style>

