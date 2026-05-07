<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  /** Array of numeric values to chart */
  data: number[]
  /** SVG viewBox width */
  width?: number
  /** SVG viewBox height */
  height?: number
  /** Stroke color (CSS value or design token) */
  color?: string
  /** Fill area under the line */
  fill?: boolean
  /** Stroke width */
  strokeWidth?: number
  /** Show dot on the last value */
  showEnd?: boolean
  /** Accessible label */
  label?: string
}

const props = withDefaults(defineProps<Props>(), {
  width: 100,
  height: 24,
  color: 'var(--color-ui-good)',
  fill: false,
  strokeWidth: 1.5,
  showEnd: false,
})

const path = computed(() => {
  if (props.data.length < 2) return ''
  
  const min = Math.min(...props.data)
  const max = Math.max(...props.data)
  const range = max - min || 1 // Avoid divide-by-zero
  const padding = 2 // px padding inside viewBox
  const drawW = props.width - padding * 2
  const drawH = props.height - padding * 2

  return props.data
    .map((val, i) => {
      const x = padding + (i / (props.data.length - 1)) * drawW
      const y = padding + (1 - (val - min) / range) * drawH
      return `${i === 0 ? 'M' : 'L'}${x.toFixed(1)} ${y.toFixed(1)}`
    })
    .join(' ')
})

const fillPath = computed(() => {
  if (!props.fill || props.data.length < 2) return ''
  const padding = 2
  const drawW = props.width - padding * 2
  const bottomY = props.height - padding
  const startX = padding
  const endX = padding + drawW
  return `${path.value} L${endX.toFixed(1)} ${bottomY} L${startX.toFixed(1)} ${bottomY} Z`
})

const lastPoint = computed(() => {
  if (props.data.length < 2) return null
  const min = Math.min(...props.data)
  const max = Math.max(...props.data)
  const range = max - min || 1
  const padding = 2
  const drawW = props.width - padding * 2
  const drawH = props.height - padding * 2
  const last = props.data[props.data.length - 1]
  return {
    x: padding + drawW,
    y: padding + (1 - (last - min) / range) * drawH,
  }
})
</script>

<template>
  <svg 
    :viewBox="`0 0 ${width} ${height}`"
    class="cyber-sparkline"
    preserveAspectRatio="none"
    :aria-label="label ?? 'Sparkline chart'"
    role="img"
  >
    <!-- Gradient fill -->
    <defs v-if="fill">
      <linearGradient id="spark-grad" x1="0" x2="0" y1="0" y2="1">
        <stop offset="0%" :stop-color="color" stop-opacity="0.3" />
        <stop offset="100%" :stop-color="color" stop-opacity="0.02" />
      </linearGradient>
    </defs>

    <!-- Fill area -->
    <path 
      v-if="fill && fillPath"
      :d="fillPath"
      fill="url(#spark-grad)"
    />

    <!-- Line -->
    <path 
      v-if="path"
      :d="path"
      fill="none"
      :stroke="color"
      :stroke-width="strokeWidth"
      stroke-linecap="round"
      stroke-linejoin="round"
    />

    <!-- End dot -->
    <circle
      v-if="showEnd && lastPoint"
      :cx="lastPoint.x"
      :cy="lastPoint.y"
      r="2"
      :fill="color"
      :stroke="color"
      stroke-width="0.5"
    >
      <animate
        attributeName="opacity"
        values="1;0.4;1"
        dur="2s"
        repeatCount="indefinite"
      />
    </circle>
  </svg>
</template>

<style scoped>
.cyber-sparkline {
  width: 100%;
  height: 100%;
  overflow: visible;
  display: block;
}
</style>
