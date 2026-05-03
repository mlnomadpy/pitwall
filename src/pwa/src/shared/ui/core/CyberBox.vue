<script setup lang="ts">
interface Props {
  variant?: 'ink' | 'charcoal' | 'ghost' | 'glass'
  border?: 'slate' | 'silver' | 'primary' | 'good' | 'warn' | 'none'
  interactive?: boolean
  selected?: boolean
}

withDefaults(defineProps<Props>(), {
  variant: 'charcoal',
  border: 'slate',
  interactive: false,
  selected: false
})
</script>

<template>
  <div 
    class="cyber-box"
    :class="[
      `bg-${variant}`, 
      `border-${border}`, 
      { interactive, selected }
    ]"
  >
    <slot></slot>
  </div>
</template>

<style scoped>
.cyber-box {
  position: relative;
  display: flex;
  box-sizing: border-box;
  transition: all 0.2s ease;
  border-width: 1px;
  border-style: solid;
}

/* Backgrounds */
.bg-ink { background: linear-gradient(135deg, rgba(20, 22, 36, 0.9) 0%, rgba(13, 14, 26, 0.95) 100%); }
.bg-charcoal { background: linear-gradient(135deg, rgba(38, 44, 62, 0.9) 0%, rgba(26, 29, 40, 0.95) 100%); }
.bg-ghost { background-color: transparent; }
.bg-glass {
  background: linear-gradient(135deg, rgba(31, 40, 51, 0.6) 0%, rgba(11, 12, 16, 0.8) 100%);
  backdrop-filter: blur(12px) brightness(1.2);
  -webkit-backdrop-filter: blur(12px) brightness(1.2);
  box-shadow: 0 4px 16px 0 rgba(0, 0, 0, 0.3);
}

/* Borders */
.border-slate { border-color: var(--color-slate, #2c3e50); }
.border-silver { border-color: var(--color-silver, #bdc3c7); }
.border-primary { border-color: var(--color-ui-info, #45b7d1); }
.border-good { border-color: var(--color-ui-good, #4ecdc4); }
.border-warn { border-color: var(--color-ui-warn, #feca57); }
.border-none { border-color: transparent; border-width: 0; }

/* Interactive States */
.cyber-box.interactive {
  cursor: pointer;
}

.cyber-box.interactive:hover {
  filter: brightness(1.2);
  transform: translateY(-2px);
  box-shadow: 0 0 16px rgba(78, 205, 196, 0.2);
}

/* Selected State overrides */
.cyber-box.selected {
  border-color: var(--color-ui-good, #4ecdc4);
  background: linear-gradient(135deg, rgba(42, 161, 152, 0.15) 0%, rgba(26, 29, 40, 0.95) 100%);
  box-shadow: inset 0 0 12px rgba(78, 205, 196, 0.2), 0 0 16px rgba(78, 205, 196, 0.3);
}
</style>
