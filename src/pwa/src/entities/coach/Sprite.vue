<script setup lang="ts">
import { computed } from 'vue'
import { useSpriteStore } from './model/spriteStore'

interface Props {
  sheet: 'trod' | 'bentley' | 'drill' | 'calm' | 'buddy' | 'avatars' | 'medals' | string
  animation: string
  scale?: number
  paused?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  scale: 1,
  paused: false
})

const store = useSpriteStore()

const style = computed(() => {
  return store.cssFor(props.sheet, props.animation, {
    scale: props.scale,
    paused: props.paused
  })
})

const emit = defineEmits<{ (e: 'animationend'): void }>()

const onAnimEnd = () => {
  emit('animationend')
}
</script>

<template>
  <div
    :class="['sprite', `sprite-${sheet}`, `sprite-${animation}`]"
    :style="style"
    @animationend="onAnimEnd"
  ></div>
</template>

<style scoped>
.sprite {
  image-rendering: pixelated;
  background-repeat: no-repeat;
  /* width/height/animation/backgroundImage set inline via store.cssFor() */
}
</style>
