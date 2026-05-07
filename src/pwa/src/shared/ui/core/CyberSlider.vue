<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'

interface Props {
  /** Label displayed to the left */
  label: string
  /** Current value (0–100 by default) */
  modelValue: number
  /** Minimum value */
  min?: number
  /** Maximum value */
  max?: number
  /** Step increment */
  step?: number
  /** Whether the slider is focused/selected */
  focused?: boolean
  /** Color of the filled track */
  color?: 'good' | 'warn' | 'bad' | 'info'
  /** Display the numeric value */
  showValue?: boolean
  /** Suffix appended to value display */
  suffix?: string
  /** Label width override */
  labelWidth?: string
  /** Disable interaction */
  disabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  min: 0,
  max: 100,
  step: 1,
  focused: false,
  color: 'good',
  showValue: true,
  suffix: '',
  disabled: false,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: number): void
}>()

const trackRef = ref<HTMLDivElement | null>(null)
const isDragging = ref(false)

const percentage = computed(() => {
  return ((props.modelValue - props.min) / (props.max - props.min)) * 100
})

const formattedValue = computed(() => {
  const val = props.step < 1 ? props.modelValue.toFixed(1) : String(props.modelValue)
  return `${val}${props.suffix}`
})

const colorVar = computed(() => {
  const map: Record<string, string> = {
    good: 'var(--color-ui-good)',
    warn: 'var(--color-ui-warn)',
    bad: 'var(--color-ui-bad)',
    info: 'var(--color-ui-info)',
  }
  return map[props.color] ?? map.good
})

const updateFromPosition = (clientX: number) => {
  if (props.disabled || !trackRef.value) return
  const rect = trackRef.value.getBoundingClientRect()
  const pct = Math.max(0, Math.min(1, (clientX - rect.left) / rect.width))
  const raw = props.min + pct * (props.max - props.min)
  const stepped = Math.round(raw / props.step) * props.step
  const clamped = Math.max(props.min, Math.min(props.max, stepped))
  emit('update:modelValue', Number(clamped.toFixed(10)))
}

const handleClick = (e: MouseEvent) => {
  updateFromPosition(e.clientX)
}

const handleTouchStart = (e: TouchEvent) => {
  isDragging.value = true
  updateFromPosition(e.touches[0].clientX)
}

const handleTouchMove = (e: TouchEvent) => {
  if (isDragging.value) {
    e.preventDefault()
    updateFromPosition(e.touches[0].clientX)
  }
}

const handleTouchEnd = () => {
  isDragging.value = false
}

const handleMouseDown = (e: MouseEvent) => {
  isDragging.value = true
  updateFromPosition(e.clientX)
  document.addEventListener('mousemove', handleMouseMove)
  document.addEventListener('mouseup', handleMouseUp)
}

const handleMouseMove = (e: MouseEvent) => {
  if (isDragging.value) updateFromPosition(e.clientX)
}

const handleMouseUp = () => {
  isDragging.value = false
  document.removeEventListener('mousemove', handleMouseMove)
  document.removeEventListener('mouseup', handleMouseUp)
}

onUnmounted(() => {
  document.removeEventListener('mousemove', handleMouseMove)
  document.removeEventListener('mouseup', handleMouseUp)
})

const increment = (dir: number) => {
  const newVal = Math.max(props.min, Math.min(props.max, props.modelValue + dir * props.step))
  emit('update:modelValue', Number(newVal.toFixed(10)))
}
</script>

<template>
  <div 
    class="cyber-slider"
    :class="{ focused, disabled, dragging: isDragging }"
    role="slider"
    :aria-valuenow="modelValue"
    :aria-valuemin="min"
    :aria-valuemax="max"
    :aria-label="label"
    tabindex="0"
    @keydown.left.prevent="increment(-1)"
    @keydown.right.prevent="increment(1)"
  >
    <span 
      class="slider-label"
      :style="{ width: labelWidth || 'clamp(60px,15vw,120px)' }"
    >
      <span v-if="focused" class="slider-cursor" aria-hidden="true">▶</span>
      {{ label }}
    </span>

    <div 
      ref="trackRef"
      class="slider-track"
      @click="handleClick"
      @mousedown="handleMouseDown"
      @touchstart.passive="handleTouchStart"
      @touchmove="handleTouchMove"
      @touchend.passive="handleTouchEnd"
    >
      <div 
        class="slider-fill" 
        :style="{ width: `${percentage}%`, backgroundColor: colorVar }"
      ></div>
      <div 
        class="slider-thumb"
        :style="{ left: `${percentage}%`, borderColor: colorVar }"
      ></div>
      <!-- Notches -->
      <div class="slider-notches">
        <div v-for="i in 5" :key="i" class="slider-notch"></div>
      </div>
    </div>

    <span v-if="showValue" class="slider-value">{{ formattedValue }}</span>
  </div>
</template>

<style scoped>
.cyber-slider {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-xs) 0;
  user-select: none;
  -webkit-user-select: none;
  touch-action: none;
}

.cyber-slider.focused .slider-label {
  color: white;
}

.cyber-slider:not(.focused) .slider-label {
  color: var(--color-silver);
}

.slider-label {
  display: inline-flex;
  align-items: center;
  font-size: clamp(11px, 2.5vmin, 22px);
  flex-shrink: 0;
  white-space: nowrap;
}

.slider-cursor {
  color: var(--color-ui-good);
  margin-right: 4px;
  animation: cursor-bounce 0.25s steps(2) infinite;
}

.slider-track {
  flex: 1;
  height: clamp(10px, 2vmin, 16px);
  background: var(--color-charcoal);
  border: 1px solid var(--color-charcoal-light, #4A5568);
  position: relative;
  cursor: pointer;
  overflow: hidden;
  /* Cyber cut corner */
  clip-path: polygon(4px 0, 100% 0, 100% calc(100% - 4px), calc(100% - 4px) 100%, 0 100%, 0 4px);
}

.slider-fill {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  transition: width 0.05s linear;
}

.slider-thumb {
  position: absolute;
  top: 50%;
  transform: translate(-50%, -50%);
  width: clamp(6px, 1.2vmin, 10px);
  height: 120%;
  background: white;
  border: 2px solid var(--color-ui-good);
  box-shadow: 0 0 6px rgba(78, 205, 196, 0.5);
  transition: left 0.05s linear;
  z-index: 2;
}

.dragging .slider-thumb {
  box-shadow: 0 0 12px rgba(78, 205, 196, 0.8);
  transform: translate(-50%, -50%) scaleY(1.2);
}

.slider-notches {
  position: absolute;
  inset: 0;
  display: flex;
  justify-content: space-between;
  padding: 0 2px;
  pointer-events: none;
}

.slider-notch {
  width: 1px;
  height: 100%;
  background: rgba(255, 255, 255, 0.08);
}

.slider-value {
  width: clamp(36px, 9vw, 64px);
  text-align: right;
  font-family: var(--font-nums, monospace);
  font-size: clamp(11px, 2.5vmin, 20px);
  color: var(--color-silver);
  flex-shrink: 0;
}

.disabled {
  opacity: 0.4;
  pointer-events: none;
}

@keyframes cursor-bounce {
  0%, 100% { transform: translateX(0); }
  50% { transform: translateX(2px); }
}
</style>
