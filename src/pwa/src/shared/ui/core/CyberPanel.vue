<script setup lang="ts">
interface Props {
  variant?: 'solid' | 'glass' | 'ghost'
  border?: 'primary' | 'secondary' | 'warn' | 'none'
  interactive?: boolean
}

withDefaults(defineProps<Props>(), {
  variant: 'solid',
  border: 'primary',
  interactive: false
})
</script>

<template>
  <div 
    class="cyber-panel"
    :class="[variant, `border-${border}`, { interactive }]"
  >
    <div class="panel-content">
      <slot></slot>
    </div>
  </div>
</template>

<style scoped>
.cyber-panel {
  position: relative;
  width: 100%;
  height: 100%;
  padding: clamp(12px, 3vmin, 24px);
  color: #fff;
  transition: all 0.2s ease-out;
  /* Use simple straight lines and GPU-friendly clip-path instead of massive drop-shadow filters */
  clip-path: polygon(
    16px 0,
    100% 0,
    100% calc(100% - 16px),
    calc(100% - 16px) 100%,
    0 100%,
    0 16px
  );
  z-index: 1;
}

.panel-content {
  position: relative;
  z-index: 5;
  width: 100%;
  height: 100%;
}

/* Base variants */
.cyber-panel.solid {
  background: rgba(11, 12, 16, 0.95);
}

.cyber-panel.glass {
  background: linear-gradient(135deg, rgba(31, 40, 51, 0.8) 0%, rgba(11, 12, 16, 0.9) 100%);
  backdrop-filter: blur(12px) brightness(1.2) saturate(1.5);
  -webkit-backdrop-filter: blur(12px) brightness(1.2) saturate(1.5);
  box-shadow: 0 8px 32px 0 rgba(0, 0, 0, 0.37);
}

.cyber-panel.ghost {
  background: transparent;
}

/* Border pseudo-element instead of slow drop-shadow filters */
.cyber-panel::after {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 2;
  transition: border-color 0.2s ease;
  clip-path: polygon(
    16px 0,
    100% 0,
    100% calc(100% - 16px),
    calc(100% - 16px) 100%,
    0 100%,
    0 16px
  );
}

.cyber-panel.border-primary::after {
  border: 2px solid rgba(78, 205, 196, 0.5); /* Cyber Cyan */
  border-bottom: 4px solid rgba(78, 205, 196, 0.8);
}

.cyber-panel.border-secondary::after {
  border: 2px solid rgba(44, 62, 80, 0.5); /* Slate */
  border-bottom: 4px solid rgba(44, 62, 80, 0.8);
}

.cyber-panel.border-warn::after {
  border: 2px solid rgba(255, 71, 87, 0.5); /* Neon Red */
  border-bottom: 4px solid rgba(255, 71, 87, 0.8);
}

.cyber-panel.border-none::after {
  border: none;
}

/* Optional Scanline texture */
.cyber-panel::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: repeating-linear-gradient(
    0deg,
    transparent,
    transparent 2px,
    rgba(255, 255, 255, 0.02) 2px,
    rgba(255, 255, 255, 0.02) 4px
  );
  z-index: 1;
}

/* Interactivity (Hover states if used as a massive button/card) */
.cyber-panel.interactive:hover {
  transform: translateY(-2px);
}

.cyber-panel.interactive.border-primary:hover::after {
  border-color: rgba(78, 205, 196, 1);
  box-shadow: inset 0 0 20px rgba(78, 205, 196, 0.2);
}

.cyber-panel.interactive.border-secondary:hover::after {
  border-color: rgba(149, 165, 166, 1);
  box-shadow: inset 0 0 20px rgba(149, 165, 166, 0.2);
}

.cyber-panel.interactive.border-warn:hover::after {
  border-color: rgba(255, 71, 87, 1);
  box-shadow: inset 0 0 20px rgba(255, 71, 87, 0.2);
}

@keyframes panel-fade-up {
  0% { opacity: 0; transform: translateY(10px); }
  100% { opacity: 1; transform: translateY(0); }
}

.cyber-panel {
  animation: panel-fade-up 0.4s cubic-bezier(0.25, 1, 0.5, 1) both;
}
</style>
