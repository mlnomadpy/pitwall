<script setup lang="ts">
interface Props {
  /** Section title text */
  title: string
  /** Color accent for the bottom border */
  variant?: 'default' | 'good' | 'warn' | 'info' | 'bad'
  /** Optional count badge (e.g. "3 items") */
  count?: number
  /** Optional subtitle below the title */
  subtitle?: string
}

withDefaults(defineProps<Props>(), {
  variant: 'default',
})
</script>

<template>
  <div class="cyber-section-header" :class="[`variant-${variant}`]">
    <div class="header-content">
      <h3 class="header-title">
        {{ title }}
        <span v-if="count !== undefined" class="header-count">{{ count }}</span>
      </h3>
      <span v-if="subtitle" class="header-subtitle">{{ subtitle }}</span>
    </div>
    <slot name="action"></slot>
  </div>
</template>

<style scoped>
.cyber-section-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding-bottom: var(--space-xs);
  margin-bottom: var(--space-sm);
  border-bottom: 2px solid var(--color-slate);
  gap: var(--space-sm);
}

.header-content {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.header-title {
  font-size: clamp(10px, 2.2vmin, 18px);
  color: var(--color-silver);
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  margin: 0;
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.header-count {
  font-size: clamp(8px, 1.5vmin, 12px);
  background: var(--color-charcoal);
  color: var(--color-slate);
  padding: 1px clamp(4px, 1vmin, 8px);
  border-radius: 2px;
  font-weight: 600;
  line-height: 1.4;
}

.header-subtitle {
  font-size: clamp(8px, 1.5vmin, 12px);
  color: var(--color-slate);
  margin-top: 1px;
}

/* Variants — border color */
.variant-default { border-bottom-color: var(--color-slate); }
.variant-good { border-bottom-color: var(--color-ui-good); }
.variant-warn { border-bottom-color: var(--color-ui-warn); }
.variant-info { border-bottom-color: var(--color-ui-info); }
.variant-bad { border-bottom-color: var(--color-ui-bad); }

.variant-good .header-title { color: var(--color-ui-good); }
.variant-warn .header-title { color: var(--color-ui-warn); }
.variant-info .header-title { color: var(--color-ui-info); }
.variant-bad .header-title { color: var(--color-ui-bad); }
</style>
