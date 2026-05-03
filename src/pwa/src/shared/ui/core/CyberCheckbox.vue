<script setup lang="ts">
interface Props {
  checked: boolean
  label?: string
  interactive?: boolean
}

withDefaults(defineProps<Props>(), {
  interactive: false
})

const emit = defineEmits(['update:checked', 'toggle'])

const toggle = () => {
  if (interactive) {
    emit('update:checked', !checked)
    emit('toggle', !checked)
  }
}
</script>

<template>
  <div 
    class="cyber-checkbox-wrapper flex items-center gap-2"
    :class="{ 'cursor-pointer': interactive }"
    @click="toggle"
  >
    <div 
      class="cyber-checkbox w-4 h-4 border flex items-center justify-center text-body transition-colors"
      :class="checked ? 'bg-ui-good border-ui-good text-ink font-bold shadow-[0_0_8px_rgba(78,205,196,0.5)]' : 'bg-charcoal border-slate text-transparent'"
    >
      <span v-if="checked">✓</span>
    </div>
    <span v-if="label" class="text-silver text-body" :class="{ 'text-white font-bold': checked }">
      {{ label }}
    </span>
  </div>
</template>

<style scoped>
.cyber-checkbox-wrapper:hover .cyber-checkbox {
  filter: brightness(1.2);
}
</style>
