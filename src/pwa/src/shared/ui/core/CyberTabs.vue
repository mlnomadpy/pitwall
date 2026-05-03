<script setup lang="ts">
interface Props {
  tabs: string[]
  modelValue: number
}

const props = defineProps<Props>()
const emit = defineEmits(['update:modelValue', 'change'])

const selectTab = (index: number) => {
  if (props.modelValue !== index) {
    emit('update:modelValue', index)
    emit('change', index)
  }
}
</script>

<template>
  <div class="cyber-tabs">
    <div 
      v-for="(t, i) in tabs" 
      :key="t"
      class="cyber-tab"
      :class="modelValue === i ? 'tab-active' : 'tab-default'"
      @click="selectTab(i)"
    >
      <span v-if="modelValue === i" class="text-ui-good mr-[4px]">▶</span>
      {{ t }}
    </div>
  </div>
</template>

<style scoped>
.cyber-tabs {
  display: flex;
  border: 2px solid var(--color-slate, #2c3e50);
  background-color: var(--color-charcoal, #1a1d28);
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}

.cyber-tabs::-webkit-scrollbar {
  display: none;
}

.cyber-tab {
  flex: 1;
  text-align: center;
  font-size: clamp(10px, 2.2vmin, 18px);
  padding: clamp(4px, 0.8vh, 10px) clamp(8px, 1.5vw, 16px);
  cursor: pointer;
  white-space: nowrap;
  -webkit-tap-highlight-color: transparent;
  font-family: var(--font-ui);
  text-transform: uppercase;
  transition: all 0.2s ease;
  user-select: none;
}

.cyber-tab:hover:not(.tab-active) {
  background-color: rgba(255, 255, 255, 0.05);
}

.tab-active {
  background-color: var(--color-ink, #0d0e1a);
  color: #fff;
  font-weight: bold;
  box-shadow: inset 0 -2px 0 var(--color-ui-good, #4ecdc4);
}

.tab-default {
  color: var(--color-slate, #2c3e50);
}
</style>
