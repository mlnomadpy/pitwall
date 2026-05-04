<script setup lang="ts">
import CyberProgress from '@/shared/ui/core/CyberProgress.vue'

defineProps<{
  label: string
  value: number
  status: 'pending' | 'pass' | 'fail'
}>()
</script>

<template>
  <div class="flex flex-col gap-1">
    <div class="text-body text-silver font-bold">{{ label }}</div>
    <div class="flex items-center gap-2">
      <CyberProgress :value="value" :max="100" variant="info" thickness="sm" class="flex-grow" />
      <span v-if="status === 'pass'" class="text-ui-good text-body font-bold w-3 text-center">✓</span>
      <span v-else-if="status === 'fail'" class="text-ui-warn text-body font-bold w-3 text-center">✗</span>
      <span v-else class="text-slate text-body w-3 text-center">…</span>
    </div>
  </div>
</template>
