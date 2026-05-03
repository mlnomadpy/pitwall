<script setup lang="ts">
import Sprite from '@/entities/coach/Sprite.vue'
import CyberTile from '@/shared/ui/core/CyberTile.vue'

defineProps<{
  id: string
  name: string
  levelReq: number
  focused: boolean
  selected: boolean
  locked: boolean
}>()
</script>

<template>
  <div class="coach-card font-ui">
    <CyberTile 
      :focused="focused && !locked"
      :locked="locked"
      :showKerb="selected"
      variant="glass"
      class="w-[clamp(72px,16vmin,140px)] h-[clamp(72px,16vmin,140px)] flex flex-col items-center justify-center p-0"
    >
      <div class="coach-name mt-2" :class="focused && !locked ? '' : ''">{{ name }}</div>
      
      <!-- Coach sprite -->
      <div class="sprite-area">
        <Sprite :sheet="id" :animation="focused && !locked ? 'talk' : 'idle'" class="scale-75" />
      </div>
      
      <!-- Status badge -->
      <div v-if="locked" class="status-badge badge-locked">
        LV {{ levelReq }}
      </div>
      <div v-else-if="selected" class="status-badge badge-active">
        ACTIVE
      </div>
    </CyberTile>
  </div>
</template>

<style scoped>

.coach-name {
  font-size: clamp(9px, 2.2vmin, 18px);
  color: var(--color-silver);
  font-weight: bold;
  z-index: 1;
  margin-bottom: clamp(2px, 0.5vmin, 4px);
  letter-spacing: 0.05em;
}

.sprite-area {
  position: absolute;
  top: clamp(16px, 4vmin, 32px);
}

.status-badge {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  text-align: center;
  font-size: clamp(8px, 1.8vmin, 14px);
  font-weight: bold;
  padding: clamp(2px, 0.4vh, 5px) 0;
  border-top: 1px solid var(--color-slate);
  z-index: 2;
}

.badge-locked {
  background: rgba(26, 29, 46, 0.9);
  color: var(--color-ui-bad);
}

.badge-active {
  background: rgba(26, 29, 46, 0.9);
  color: var(--color-ui-good);
  text-shadow: 0 0 4px rgba(42, 161, 152, 0.5);
}
</style>
