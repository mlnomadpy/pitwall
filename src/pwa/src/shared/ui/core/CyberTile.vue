<script setup lang="ts">
import CyberBox from './CyberBox.vue'

interface Props {
  title?: string
  subText?: string
  focused?: boolean
  locked?: boolean
  showKerb?: boolean
  variant?: 'ink' | 'charcoal' | 'glass'
}

withDefaults(defineProps<Props>(), {
  focused: false,
  locked: false,
  showKerb: false,
  variant: 'ink'
})

const emit = defineEmits<{ (e: 'click'): void; (e: 'hover'): void }>()
</script>

<template>
  <CyberBox 
    :variant="focused ? 'charcoal' : variant" 
    :border="focused ? 'good' : 'slate'"
    interactive
    :class="['cyber-tile', { locked, focused }]"
    @click="$emit('click')"
    @mouseenter="$emit('hover')"
  >
    <!-- Focus Marker -->
    <div v-if="focused && !locked" class="focus-marker">▼</div>
    
    <!-- Kerb Accent -->
    <div v-if="showKerb && focused" class="tile-kerb"></div>
    
    <div class="tile-content w-full h-full flex flex-col justify-center">
      <slot>
        <!-- Default layout if no slot provided -->
        <div class="font-bold text-body flex items-center pixel-shadow">
          <span v-if="focused" class="text-curb-red mr-1 cursor-bounce">▶</span>
          {{ title }}
        </div>
        <div v-if="subText" class="text-body text-silver ml-3">{{ subText }}</div>
      </slot>
    </div>
  </CyberBox>
</template>

<style scoped>
.cyber-tile {
  width: 100%;
  height: 100%;
  padding: clamp(6px, 1.5vmin, 16px);
  overflow: hidden;
  position: relative;
}

.cyber-tile.locked {
  opacity: 0.4;
  filter: saturate(0.2) brightness(0.7);
  pointer-events: none;
}

.tile-kerb {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  height: clamp(3px, 0.6vmin, 5px);
  background: repeating-linear-gradient(
    90deg,
    #c93838 0,
    #c93838 clamp(4px, 1vmin, 8px),
    #f5f5e8 clamp(4px, 1vmin, 8px),
    #f5f5e8 clamp(8px, 2vmin, 16px)
  );
  z-index: 10;
}

.focus-marker {
  position: absolute;
  top: clamp(2px, 0.5vmin, 6px);
  left: clamp(6px, 1.5vmin, 14px);
  color: var(--color-ui-good);
  font-size: clamp(8px, 1.8vmin, 14px);
  text-shadow: 1px 1px 0 rgba(0, 0, 0, 0.8);
  animation: retro-blink 1s steps(2) infinite;
  z-index: 10;
}

@keyframes retro-blink {
  0%, 100% { opacity: 0; }
  50% { opacity: 1; }
}

@keyframes cursor-bounce {
  0%, 100% { transform: translateX(0); }
  50% { transform: translateX(2px); }
}

.cursor-bounce {
  animation: cursor-bounce 0.25s steps(2) infinite;
}
</style>
