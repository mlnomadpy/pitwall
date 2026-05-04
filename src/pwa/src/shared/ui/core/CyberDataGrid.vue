<script setup lang="ts">
interface DataItem {
  label: string
  value: string | number
  unit?: string
  status?: 'good' | 'bad' | 'warn' | 'info' | 'ghost'
}

defineProps<{
  items: DataItem[]
  columns?: 1 | 2 | 3 | 4
}>()
</script>

<template>
  <div class="cyber-data-grid" :class="`cols-${columns || 2}`">
    <div 
      v-for="(item, i) in items" 
      :key="i"
      class="data-cell"
      :class="item.status ? `status-${item.status}` : 'status-default'"
    >
      <div class="cell-label">{{ item.label }}</div>
      <div class="cell-value-row">
        <span class="cell-value">{{ item.value }}</span>
        <span v-if="item.unit" class="cell-unit">{{ item.unit }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.cyber-data-grid {
  display: grid;
  gap: clamp(8px, 2vmin, 16px);
  width: 100%;
}

.cols-1 { grid-template-columns: 1fr; }
.cols-2 { grid-template-columns: repeat(2, 1fr); }
.cols-3 { grid-template-columns: repeat(3, 1fr); }
.cols-4 { grid-template-columns: repeat(4, 1fr); }

/* On mobile, collapse columns slightly if 3 or 4 are requested to avoid squishing */
@media (max-width: 600px) {
  .cols-3, .cols-4 { grid-template-columns: repeat(2, 1fr); }
}

.data-cell {
  background: rgba(11, 12, 16, 0.6);
  border-left: 2px solid;
  padding: clamp(6px, 1.5vmin, 12px) clamp(10px, 2vmin, 16px);
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.cell-label {
  font-family: var(--font-ui);
  font-size: clamp(9px, 2vmin, 12px);
  color: var(--color-slate);
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.cell-value-row {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.cell-value {
  font-family: var(--font-nums);
  font-size: clamp(16px, 4vmin, 24px);
  font-weight: bold;
}

.cell-unit {
  font-family: var(--font-ui);
  font-size: clamp(10px, 2.2vmin, 14px);
  opacity: 0.7;
}

/* Status Colors */
.status-default {
  border-color: var(--color-slate);
  color: var(--color-silver);
}

.status-good {
  border-color: var(--color-ui-good);
  color: var(--color-ui-good);
  background: rgba(78, 205, 196, 0.1);
}

.status-warn {
  border-color: var(--color-ui-warn);
  color: var(--color-ui-warn);
  background: rgba(254, 202, 87, 0.1);
}

.status-bad {
  border-color: var(--color-ui-bad);
  color: var(--color-ui-bad);
  background: rgba(255, 71, 87, 0.1);
}

.status-info {
  border-color: var(--color-ui-info);
  color: var(--color-ui-info);
  background: rgba(69, 183, 209, 0.1);
}

.status-ghost {
  border-color: transparent;
  color: white;
}
</style>
