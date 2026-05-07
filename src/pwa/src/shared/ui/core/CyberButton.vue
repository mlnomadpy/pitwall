<script setup lang="ts">
interface Props {
  disabled?: boolean
  subText?: string
  variant?: 'primary' | 'secondary' | 'info' | 'dark' | 'danger'
  size?: 'sm' | 'md' | 'lg'
  fluid?: boolean
  active?: boolean
  complex?: boolean
  /** Shows a pulsing loading indicator and disables interaction */
  loading?: boolean
  /** Accessible label for icon-only buttons */
  ariaLabel?: string
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
  variant: 'primary',
  size: 'md',
  fluid: false,
  active: false,
  complex: false,
  loading: false,
})

const emit = defineEmits<{ (e: 'click', evt: Event): void }>()

const onClick = (e: Event) => {
  if (!props.disabled && !props.loading) {
    emit('click', e)
  }
}
</script>

<template>
  <button 
    class="cyber-button"
    :class="[size, variant, { fluid, active, disabled: disabled || loading, complex, loading }]"
    :disabled="disabled || loading"
    :aria-label="ariaLabel"
    :aria-busy="loading"
    @click="onClick"
  >
    <!-- Background scanline effect layer -->
    <div class="bg-scanline" aria-hidden="true"></div>
    
    <div class="content-wrapper">
      <slot name="icon"></slot>
      
      <!-- Loading indicator -->
      <div v-if="loading" class="loading-indicator" aria-hidden="true">
        <span class="loading-dot"></span>
        <span class="loading-dot"></span>
        <span class="loading-dot"></span>
      </div>

      <template v-else>
        <div v-if="complex" class="complex-content">
          <slot></slot>
        </div>
        
        <div v-else class="text-content">
          <span class="main-text"><slot></slot></span>
          <span v-if="subText" class="sub-text">{{ subText }}</span>
        </div>
      </template>
      
      <slot name="icon-right"></slot>
    </div>
    
    <!-- Neon border overlay -->
    <div class="neon-border" aria-hidden="true"></div>
  </button>
</template>

<style scoped>
.cyber-button {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--color-ink);
  color: #fff;
  font-family: var(--font-title);
  cursor: pointer;
  outline: none;
  border: 2px solid var(--theme-color, var(--color-ui-good));
  transition: transform var(--duration-instant, 50ms) var(--ease-snap, steps(2)),
              box-shadow var(--duration-instant, 50ms) var(--ease-snap, steps(2)),
              background-color var(--duration-instant, 50ms) var(--ease-snap, steps(2));
  clip-path: polygon(
    clamp(6px, 1vmin, 12px) 0,
    100% 0,
    100% calc(100% - clamp(6px, 1vmin, 12px)),
    calc(100% - clamp(6px, 1vmin, 12px)) 100%,
    0 100%,
    0 clamp(6px, 1vmin, 12px)
  );
  overflow: hidden;
  z-index: var(--z-content, 1);
  box-shadow: var(--shadow-hard, 4px 4px 0 rgba(0,0,0,0.8));
}

.cyber-button.fluid {
  display: flex;
  width: 100%;
}

.cyber-button.disabled {
  opacity: 0.5;
  cursor: not-allowed;
  filter: grayscale(1);
}

.cyber-button.loading {
  cursor: wait;
}

/* Internal Layouts */
.content-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  height: 100%;
  z-index: 3;
  padding: 0 16px;
}

.complex-content {
  flex: 1;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
}

.text-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.main-text {
  text-transform: uppercase;
  letter-spacing: 2px;
  text-shadow: 1px 1px 0 rgba(0,0,0,0.8);
}

.sub-text {
  font-family: var(--font-ui);
  opacity: 0.7;
  margin-top: 4px;
  letter-spacing: 1px;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Loading indicator */
.loading-indicator {
  display: flex;
  gap: 4px;
  align-items: center;
}

.loading-dot {
  width: 6px;
  height: 6px;
  background: var(--theme-color, var(--color-ui-good));
  animation: dot-pulse 1s ease-in-out infinite;
}

.loading-dot:nth-child(2) { animation-delay: 150ms; }
.loading-dot:nth-child(3) { animation-delay: 300ms; }

@keyframes dot-pulse {
  0%, 100% { opacity: 0.3; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1.2); }
}

/* Sizing */
.cyber-button.lg {
  min-height: clamp(56px, 10vmin, 80px);
  min-width: clamp(200px, 30vw, 320px);
}
.cyber-button.lg .main-text { font-size: clamp(14px, 2.5vmin, 20px); }
.cyber-button.lg .sub-text { font-size: clamp(10px, 1.5vmin, 14px); }

.cyber-button.md {
  min-height: clamp(40px, 7vmin, 56px);
  min-width: clamp(120px, 20vw, 200px);
}
.cyber-button.md .main-text { font-size: clamp(12px, 2vmin, 16px); }
.cyber-button.md .sub-text { font-size: clamp(9px, 1.2vmin, 12px); }

.cyber-button.sm {
  min-height: clamp(32px, 5vmin, 40px);
  min-width: clamp(80px, 15vw, 120px);
}
.cyber-button.sm .main-text { font-size: clamp(10px, 1.5vmin, 12px); }
.cyber-button.sm .sub-text { font-size: clamp(8px, 1vmin, 10px); }

/* Neon Border */
.neon-border {
  display: none; /* Disabled for strict retro look */
}

.bg-scanline {
  position: absolute;
  inset: 0;
  background: repeating-linear-gradient(
    0deg,
    transparent,
    transparent 2px,
    rgba(255,255,255,0.03) 2px,
    rgba(255,255,255,0.03) 4px
  );
  z-index: 1;
}

/* Hover and Active States */
.cyber-button:hover:not(.disabled),
.cyber-button.active:not(.disabled) {
  background-color: var(--theme-bg-hover, rgba(78, 205, 196, 0.2));
  transform: translate(-2px, -2px);
  box-shadow: var(--shadow-hard-hover, 6px 6px 0 rgba(0,0,0,0.8));
}

.cyber-button:active:not(.disabled) {
  transform: translate(2px, 2px);
  box-shadow: var(--shadow-hard-active, 2px 2px 0 rgba(0,0,0,0.8));
}

.cyber-button:focus-visible {
  outline: 2px solid var(--theme-color, var(--color-ui-good));
  outline-offset: 4px;
}

/* Variants */
.cyber-button.primary {
  --theme-color: var(--color-ui-bad);
  --theme-bg-hover: rgba(255, 71, 87, 0.2);
}

.cyber-button.secondary {
  --theme-color: var(--color-ui-good);
  --theme-bg-hover: rgba(78, 205, 196, 0.2);
}

.cyber-button.info {
  --theme-color: var(--color-ui-warn);
  --theme-bg-hover: rgba(254, 202, 87, 0.2);
}

.cyber-button.dark {
  --theme-color: var(--color-asphalt-light);
  --theme-bg-hover: rgba(44, 62, 80, 0.4);
}

.cyber-button.danger {
  --theme-color: var(--color-ui-bad);
  --theme-bg-hover: rgba(255, 71, 87, 0.3);
  border-width: 2px;
}

@media (prefers-reduced-motion: reduce) {
  .loading-dot { animation: none; opacity: 0.6; }
}
</style>
