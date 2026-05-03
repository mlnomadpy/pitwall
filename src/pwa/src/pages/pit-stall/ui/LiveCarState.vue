<script setup lang="ts">
defineProps<{
  state: Record<string, string | number>
}>()
</script>

<template>
  <div class="live-car-state text-body font-ui">
    <div class="grid grid-cols-3 gap-x-4 gap-y-1 mb-2">
      <div>RPM <span class="float-right text-silver">{{ state.rpm }}</span></div>
      <div>GEAR <span class="float-right text-silver">{{ state.gear }}</span></div>
      <div>SPEED <span class="float-right text-silver">{{ state.speed }} km/h</span></div>
      
      <div>OIL TEMP <span class="float-right text-silver">{{ state.oil }}°C</span></div>
      <div>COOLANT <span class="float-right text-silver">{{ state.coolant }}°C</span></div>
      <div>FUEL <span class="float-right text-silver">{{ state.fuel }} %</span></div>
      
      <div>THROTTLE <span class="float-right text-silver">{{ state.throttle }} %</span></div>
      <div>BRAKE <span class="float-right text-silver">{{ state.brake }} bar</span></div>
      <div>STEER <span class="float-right text-silver">{{ state.steer }}°</span></div>
      
      <div>G-LAT <span class="float-right text-silver">{{ state.glat }} g</span></div>
      <div>G-LONG <span class="float-right text-silver">{{ state.glong }} g</span></div>
      <div>COMBO <span class="float-right text-silver">{{ state.gcombo }} g</span></div>
    </div>
    
    <div class="coaches-available pt-1 border-t border-slate border-dashed">
      <div class="mb-1 text-silver">AVAILABLE COACHES</div>
      <div class="grid grid-cols-2 gap-x-2 text-ui-good">
        <div>✓ base_pace_note</div>
        <div>✓ trail_brake_score</div>
        <div>✓ oil_temp_warning</div>
        <div class="text-ui-bad">✗ clutch_balance (no signal)</div>
      </div>
    </div>
  </div>
</template>
