<script setup lang="ts">
interface Props {
  disabled?: boolean
  subText?: string
  variant?: 'primary' | 'secondary' | 'info' | 'dark'
  size?: 'sm' | 'md' | 'lg'
  fluid?: boolean
  active?: boolean
  complex?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
  variant: 'primary',
  size: 'md',
  fluid: false,
  active: false,
  complex: false
})

const emit = defineEmits<{ (e: 'click', evt: Event): void }>()

const onClick = (e: Event) => {
  if (!props.disabled) {
    emit('click', e)
  }
}
</script>

<template>
  <button 
    class="cyber-button"
    :class="[size, variant, { fluid, active, disabled, complex }]"
    :disabled="disabled"
    @click="onClick"
  >
    <!-- Background glitch effect layer -->
    <div class="bg-scanline"></div>
    
    <div class="content-wrapper">
      <slot name="icon"></slot>
      
      <div v-if="complex" class="complex-content">
        <slot></slot>
      </div>
      
      <div v-else class="text-content">
        <span class="main-text"><slot></slot></span>
        <span v-if="subText" class="sub-text">{{ subText }}</span>
      </div>
      
      <slot name="icon-right"></slot>
    </div>
    
    <!-- Neon border overlay using clip-path -->
    <div class="neon-border"></div>
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
  border: 2px solid var(--theme-color, #4ecdc4);
  /* Snappy arcade transition */
  transition: transform 0.05s steps(2), box-shadow 0.05s steps(2), background-color 0.05s steps(2);
  /* GPU-accelerated cut corners */
  clip-path: polygon(
    12px 0,
    100% 0,
    100% calc(100% - 12px),
    calc(100% - 12px) 100%,
    0 100%,
    0 12px
  );
  overflow: hidden;
  z-index: 1;
  /* Hard pixel shadow */
  box-shadow: 4px 4px 0 rgba(0,0,0,0.8);
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
}

/* Sizing */
.cyber-button.lg {
  min-height: 72px;
  min-width: 240px;
}
.cyber-button.lg .main-text { font-size: 18px; }
.cyber-button.lg .sub-text { font-size: 14px; }

.cyber-button.md {
  min-height: 48px;
  min-width: 160px;
}
.cyber-button.md .main-text { font-size: 14px; }
.cyber-button.md .sub-text { font-size: 11px; }

.cyber-button.sm {
  min-height: 36px;
  min-width: 100px;
}
.cyber-button.sm .main-text { font-size: 11px; }
.cyber-button.sm .sub-text { font-size: 9px; }

/* The Neon Border */
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
  box-shadow: 6px 6px 0 rgba(0,0,0,0.8);
}

.cyber-button:active:not(.disabled) {
  transform: translate(2px, 2px);
  box-shadow: 2px 2px 0 rgba(0,0,0,0.8);
}

/* Variants using CSS vars for easy theming */
.cyber-button.primary {
  --theme-color: #ff4757;
  --theme-bg-hover: rgba(255, 71, 87, 0.2);
}

.cyber-button.secondary {
  --theme-color: #4ecdc4;
  --theme-bg-hover: rgba(78, 205, 196, 0.2);
}

.cyber-button.info {
  --theme-color: #feca57;
  --theme-bg-hover: rgba(254, 202, 87, 0.2);
}

.cyber-button.dark {
  --theme-color: #2c3e50;
  --theme-bg-hover: rgba(44, 62, 80, 0.4);
}
</style>
