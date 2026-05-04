<script setup lang="ts">
import { computed } from 'vue'
import CyberDataGrid from '@/shared/ui/core/CyberDataGrid.vue'
import CyberGauge from '@/shared/ui/core/CyberGauge.vue'

const props = defineProps<{
  state: Record<string, string | number>
}>()

const engineItems = computed(() => [
  { label: 'RPM', value: props.state.rpm },
  { label: 'GEAR', value: props.state.gear },
  { label: 'SPEED', value: props.state.speed, unit: 'km/h' },
  { label: 'OIL', value: props.state.oil, unit: '°C' },
  { label: 'COOLANT', value: props.state.coolant, unit: '°C' },
  { label: 'FUEL', value: props.state.fuel, unit: '%' },
])

const dynamicsItems = computed(() => [
  { label: 'STEER', value: props.state.steer, unit: '°' },
  { label: 'G-LAT', value: props.state.glat, unit: 'g' },
  { label: 'G-LONG', value: props.state.glong, unit: 'g' },
  { label: 'COMBO', value: props.state.gcombo, unit: 'g' },
])
</script>

<template>
  <div class="live-car-state text-body font-ui flex flex-col gap-4">
    <div>
      <div class="mb-1 text-silver tracking-wider font-bold text-small border-b border-slate pb-1">POWERTRAIN</div>
      <CyberDataGrid :items="engineItems" :columns="3" />
    </div>

    <div>
      <div class="mb-1 text-silver tracking-wider font-bold text-small border-b border-slate pb-1 mt-2">DYNAMICS</div>
      <CyberDataGrid :items="dynamicsItems" :columns="2" />
    </div>

    <div>
      <div class="mb-1 text-silver tracking-wider font-bold text-small border-b border-slate pb-1 mt-2">DRIVER INPUTS</div>
      <div class="flex flex-col gap-2 mt-2">
        <div class="flex items-center gap-2">
          <span class="w-[clamp(60px,15vw,100px)] text-silver text-[clamp(10px,2.5vmin,14px)] font-bold">THROTTLE</span>
          <CyberGauge :value="state.throttle" variant="good" class="flex-grow" />
          <span class="w-[40px] text-right text-ui-good font-mono text-[clamp(10px,2.5vmin,16px)]">{{ state.throttle }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="w-[clamp(60px,15vw,100px)] text-silver text-[clamp(10px,2.5vmin,14px)] font-bold">BRAKE</span>
          <CyberGauge :value="state.brake" variant="bad" class="flex-grow" />
          <span class="w-[40px] text-right text-ui-bad font-mono text-[clamp(10px,2.5vmin,16px)]">{{ state.brake }}</span>
        </div>
      </div>
    </div>
    
    <div class="coaches-available pt-2 border-t border-slate border-dashed mt-2">
      <div class="mb-2 text-silver tracking-wider font-bold text-small">AVAILABLE COACHES</div>
      <div class="grid grid-cols-2 gap-x-2 gap-y-1 text-ui-good text-small">
        <div class="flex items-center gap-2"><span class="text-ui-good">✓</span> base_pace_note</div>
        <div class="flex items-center gap-2"><span class="text-ui-good">✓</span> trail_brake_score</div>
        <div class="flex items-center gap-2"><span class="text-ui-good">✓</span> oil_temp_warning</div>
        <div class="flex items-center gap-2 text-ui-bad"><span class="text-ui-bad">✗</span> clutch_balance (no signal)</div>
      </div>
    </div>
  </div>
</template>
