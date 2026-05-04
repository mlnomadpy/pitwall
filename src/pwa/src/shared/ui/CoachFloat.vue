<script setup lang="ts">
import { computed } from 'vue'
import DialogueBox from '@/widgets/dialogue-box/DialogueBox.vue'
import { useSaveStore } from '@/entities/save/model/saveStore'

interface Props {
  emotion: string
  text: string
  coachId?: string
}

const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'done'): void
}>()

const save = useSaveStore()

const resolvedCoachId = computed(() => {
  return props.coachId ?? save.activeSlot?.preferredCoach ?? 'trod'
})
</script>

<template>
  <div class="coach-float">
    <DialogueBox
      :coach-id="resolvedCoachId"
      :emotion="emotion"
      :text="text"
      class="scale-[0.85] origin-bottom-left w-[117%]"
      @done="$emit('done')"
    />
  </div>
</template>

<style scoped>
.coach-float {
  position: absolute;
  bottom: clamp(20px, 5vh, 40px);
  left: clamp(8px, 2vw, 16px);
  right: clamp(8px, 2vw, 16px);
  z-index: 20;
}
</style>
