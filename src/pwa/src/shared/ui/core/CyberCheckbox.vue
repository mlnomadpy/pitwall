<script setup lang="ts">
defineProps<{
  label: string
  checked: boolean
  focused: boolean
  subLabel?: string
}>()
</script>

<template>
  <div class="flex flex-col mt-1 mb-1">
    <div class="flex items-center">
      <span class="w-4" :class="focused ? 'text-ui-good' : ''">{{ focused ? '▶' : '' }}</span>
      <span class="text-ui-info mr-2">{{ checked ? '☑' : '☐' }}</span>
      <span :class="focused ? 'text-white' : 'text-silver'">{{ label }}</span>
    </div>
    <div v-if="subLabel" class="ml-10 text-xs text-slate">{{ subLabel }}</div>
  </div>
</template>
