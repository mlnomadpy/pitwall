<script setup lang="ts">
defineProps<{
  label: string
  value: string | number
  subText?: string
  subClass?: string
}>()
</script>

<template>
  <div class="cyber-metric-row flex justify-between items-center py-[clamp(1px,0.3vh,4px)] border-b border-slate/20">
    <span class="w-[35%] text-silver">{{ label }}</span>
    <span class="w-[25%] text-center font-bold text-white">{{ value }}</span>
    <span class="w-[25%] text-right" :class="subClass">{{ subText }}</span>
  </div>
</template>

<style scoped>
.cyber-metric-row {
  font-size: clamp(10px, 2.3vmin, 20px);
}
</style>
