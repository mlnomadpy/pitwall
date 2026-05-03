<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  value: number
  max?: number
  variant?: 'good' | 'warn' | 'bad' | 'info'
  thickness?: 'sm' | 'md' | 'lg'
  animated?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  max: 100,
  variant: 'good',
  thickness: 'md',
  animated: true
})

const percentage = computed(() => {
  return Math.min(100, Math.max(0, (props.value / props.max) * 100))
})
</script>

<template>
  <div 
    class="cyber-progress-container bg-charcoal border-slate border"
    :class="[`thickness-${thickness}`]"
  >
    <div 
      class="cyber-progress-fill"
      :class="[
        `fill-${variant}`,
        { 'animate-pulse-slow': animated && percentage > 0 }
      ]"
      :style="{ width: `${percentage}%` }"
    ></div>
    
    <!-- Optional markers for visual flair -->
    <div class="absolute inset-0 pointer-events-none flex justify-between px-[10%] opacity-20">
      <div class="h-full w-[1px] bg-white"></div>
      <div class="h-full w-[1px] bg-white"></div>
      <div class="h-full w-[1px] bg-white"></div>
    </div>
  </div>
</template>

<style scoped>
.cyber-progress-container {
  position: relative;
  width: 100%;
  overflow: hidden;
  display: flex;
}

/* Thickness */
.thickness-sm { height: 4px; }
.thickness-md { height: 8px; }
.thickness-lg { height: 16px; }

/* Fill Layer */
.cyber-progress-fill {
  height: 100%;
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
}

/* Variants */
.fill-good {
  background: var(--color-ui-good, #4ecdc4);
  box-shadow: 0 0 8px rgba(78, 205, 196, 0.6);
}

.fill-warn {
  background: var(--color-ui-warn, #feca57);
  box-shadow: 0 0 8px rgba(254, 202, 87, 0.6);
}

.fill-bad {
  background: var(--color-ui-bad, #ff4757);
  box-shadow: 0 0 8px rgba(255, 71, 87, 0.6);
}

.fill-info {
  background: var(--color-ui-info, #45b7d1);
  box-shadow: 0 0 8px rgba(69, 183, 209, 0.6);
}

.animate-pulse-slow {
  animation: pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}

@keyframes pulse {
  0%, 100% { filter: brightness(1); }
  50% { filter: brightness(1.2); }
}
</style>
