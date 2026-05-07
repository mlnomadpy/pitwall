<script setup lang="ts">
import TrackMap from '@/shared/ui/core/TrackMap.vue'

defineProps<{
  track: string
  posM: number
}>()
</script>

<template>
  <div class="track-map">
    <div class="map-frame">
      <span class="map-label text-small text-silver/60">{{ track.toUpperCase() }} MAP</span>
      
      <div class="absolute inset-2">
        <!-- 4032m is approximate length of Sonoma gp -->
        <TrackMap :car-progress="(posM / 4032) * 100" stroke-class="text-slate opacity-40" />
      </div>
      
      <!-- Position indicator -->
      <div class="pos-indicator text-body text-ui-info font-nums">
        ▶ {{ Math.floor(posM) }}m
      </div>
    </div>
  </div>
</template>

<style scoped>
.track-map {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.map-frame {
  width: clamp(140px, 35vw, 280px);
  height: clamp(100px, 25vh, 200px);
  border: 1px solid var(--color-slate);
  background: linear-gradient(180deg, rgba(42, 47, 66, 0.4) 0%, rgba(31, 34, 48, 0.6) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

.map-label {
  position: absolute;
  top: clamp(4px, 1vmin, 10px);
  left: clamp(6px, 1.5vmin, 12px);
  letter-spacing: 0.1em;
  z-index: 10;
}

.pos-indicator {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  text-shadow: 0 0 6px rgba(74, 152, 200, 0.6);
  animation: pos-pulse 2s ease-in-out infinite;
}

@keyframes pos-pulse {
  0%, 100% { opacity: 0.7; }
  50% { opacity: 1; }
}
</style>
