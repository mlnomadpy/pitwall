<script setup lang="ts">
import CyberGlassPanel from './CyberGlassPanel.vue'

interface Corner {
  id: string
  name: string
  tip: string
  grade: string
  entry?: number
  apex?: number
  exit?: number
  time?: number
  deltas: { entry: number, apex: number, exit: number, time: number }
}

const props = defineProps<{
  corner: Corner
  coachId: string
}>()

const getGradeColor = (grade: string) => {
  if (grade.startsWith('A') || grade.startsWith('B')) return 'text-ui-good drop-shadow-[1px_1px_0_#000]'
  if (grade.startsWith('C')) return 'text-ui-warn'
  if (grade.startsWith('F')) return 'text-ui-bad font-bold drop-shadow-[1px_1px_0_#000]'
  return 'text-silver'
}
</script>

<template>
  <div class="absolute inset-x-2 bottom-[6vh] top-[6vh] z-30 flex flex-col pointer-events-none">
    <CyberGlassPanel class="flex-grow flex flex-col shadow-2xl p-2 border-slate pointer-events-auto">
      <div class="flex justify-between border-b border-slate pb-1 mb-2 items-center">
        <span class="text-white font-bold text-[clamp(14px,3vw,24px)]">{{ corner.id }} · "{{ corner.name }}"</span>
        <span class="text-title font-bold" :class="getGradeColor(corner.grade)">Grade: {{ corner.grade }}</span>
      </div>

      <div class="flex flex-col gap-2 flex-grow text-body">
        <CyberGlassPanel class="p-2 border-slate">
          <span class="text-slate text-small font-bold tracking-wider">COACH SAYS</span>
          <div class="mt-1 flex gap-2">
            <span class="text-ui-good font-bold text-body shrink-0">{{ coachId.toUpperCase() }}:</span>
            <div class="text-silver italic">
              "{{ corner.tip }}"
            </div>
          </div>
        </CyberGlassPanel>

        <div class="text-slate mb-1 mt-2 tracking-wider text-small font-bold">YOUR BEST AT {{ corner.id }}</div>
        <CyberGlassPanel class="flex flex-col gap-2 p-3 border-slate">
          <div class="flex justify-between items-center">
            <span class="w-[clamp(40px,10vw,60px)] text-silver text-small">ENTRY</span>
            <span class="font-bold text-[clamp(14px,3vw,20px)]">{{ corner.entry ? `${corner.entry} km/h` : '---' }}</span>
            <span class="w-[clamp(16px,4vw,32px)] text-right font-bold text-small" :class="corner.deltas.entry < 0 ? 'text-ui-bad' : 'text-ui-good'">
              {{ corner.deltas.entry < 0 ? '▼' : '▲' }}{{ Math.abs(corner.deltas.entry) }}
            </span>
          </div>
          <div class="flex justify-between items-center">
            <span class="w-[clamp(40px,10vw,60px)] text-silver text-small">APEX</span>
            <span class="font-bold text-[clamp(14px,3vw,20px)]">{{ corner.apex ? `${corner.apex} km/h` : '---' }}</span>
            <span class="w-[clamp(16px,4vw,32px)] text-right font-bold text-small" :class="corner.deltas.apex < 0 ? 'text-ui-bad' : 'text-ui-good'">
              {{ corner.deltas.apex < 0 ? '▼' : '▲' }}{{ Math.abs(corner.deltas.apex) }}
            </span>
          </div>
          <div class="flex justify-between items-center">
            <span class="w-[clamp(40px,10vw,60px)] text-silver text-small">EXIT</span>
            <span class="font-bold text-[clamp(14px,3vw,20px)]">{{ corner.exit ? `${corner.exit} km/h` : '---' }}</span>
            <span class="w-[clamp(16px,4vw,32px)] text-right font-bold text-small" :class="corner.deltas.exit < 0 ? 'text-ui-bad' : 'text-ui-good'">
              {{ corner.deltas.exit < 0 ? '▼' : '▲' }}{{ Math.abs(corner.deltas.exit) }}
            </span>
          </div>
          <div class="w-full h-[1px] bg-slate/30 my-1"></div>
          <div class="flex justify-between items-center text-white">
            <span class="w-[clamp(40px,10vw,60px)] text-silver text-small">TIME</span>
            <span class="font-bold text-[clamp(14px,3vw,20px)]">{{ corner.time ? `${corner.time} s` : '---' }}</span>
            <span class="w-[clamp(16px,4vw,32px)] text-right font-bold text-small" :class="corner.deltas.time > 0 ? 'text-ui-bad' : 'text-ui-good'">
              {{ corner.deltas.time > 0 ? '▼' : '▲' }}{{ Math.abs(corner.deltas.time) }}
            </span>
          </div>
        </CyberGlassPanel>
      </div>
    </CyberGlassPanel>
  </div>
</template>
