<script setup lang="ts">
interface Props {
  /** Visual style of the divider */
  variant?: 'default' | 'kerb' | 'checkered' | 'subtle'
  /** Vertical spacing around the divider */
  spacing?: 'none' | 'sm' | 'md' | 'lg'
  /** Orientation of the divider */
  orientation?: 'horizontal' | 'vertical'
}

withDefaults(defineProps<Props>(), {
  variant: 'default',
  spacing: 'md',
  orientation: 'horizontal',
})
</script>

<template>
  <div 
    class="cyber-divider" 
    :class="[`variant-${variant}`, `spacing-${spacing}`, orientation]"
    role="separator"
    :aria-orientation="orientation"
  ></div>
</template>

<style scoped>
.cyber-divider {
  flex-shrink: 0;
}

/* Orientation */
.cyber-divider.horizontal {
  width: 100%;
}

.cyber-divider.vertical {
  height: 100%;
  align-self: stretch;
}

/* Spacing */
.spacing-none.horizontal { margin: 0; }
.spacing-sm.horizontal { margin: var(--space-xs) 0; }
.spacing-md.horizontal { margin: var(--space-sm) 0; }
.spacing-lg.horizontal { margin: var(--space-md) 0; }

.spacing-none.vertical { margin: 0; }
.spacing-sm.vertical { margin: 0 var(--space-xs); }
.spacing-md.vertical { margin: 0 var(--space-sm); }
.spacing-lg.vertical { margin: 0 var(--space-md); }

/* Variants — Horizontal */
.variant-default.horizontal {
  height: 1px;
  background: var(--color-slate);
  opacity: 0.3;
}

.variant-subtle.horizontal {
  height: 1px;
  background: linear-gradient(
    90deg,
    transparent 0%,
    var(--color-slate) 20%,
    var(--color-slate) 80%,
    transparent 100%
  );
  opacity: 0.2;
}

.variant-kerb.horizontal {
  height: clamp(3px, 0.6vmin, 5px);
  background: repeating-linear-gradient(
    90deg,
    #c93838 0,
    #c93838 clamp(6px, 1.5vmin, 10px),
    #f5f5e8 clamp(6px, 1.5vmin, 10px),
    #f5f5e8 clamp(12px, 3vmin, 20px)
  );
}

.variant-checkered.horizontal {
  height: clamp(6px, 1.5vmin, 10px);
  background-image: repeating-conic-gradient(
    var(--color-slate) 0% 25%,
    var(--color-ink) 0% 50%
  );
  background-size: clamp(6px, 1.5vmin, 10px) clamp(6px, 1.5vmin, 10px);
}

/* Variants — Vertical */
.variant-default.vertical {
  width: 1px;
  background: var(--color-slate);
  opacity: 0.3;
}

.variant-subtle.vertical {
  width: 1px;
  background: linear-gradient(
    180deg,
    transparent 0%,
    var(--color-slate) 20%,
    var(--color-slate) 80%,
    transparent 100%
  );
  opacity: 0.2;
}

.variant-kerb.vertical {
  width: clamp(3px, 0.6vmin, 5px);
  background: repeating-linear-gradient(
    180deg,
    #c93838 0,
    #c93838 clamp(6px, 1.5vmin, 10px),
    #f5f5e8 clamp(6px, 1.5vmin, 10px),
    #f5f5e8 clamp(12px, 3vmin, 20px)
  );
}
</style>
