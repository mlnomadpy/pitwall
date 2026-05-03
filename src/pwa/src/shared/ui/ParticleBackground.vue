<script setup lang="ts">
</script>

<template>
  <div class="floating-particles">
    <div class="particle particle-1"></div>
    <div class="particle particle-2"></div>
    <div class="particle particle-3"></div>
    <div class="particle particle-4"></div>
    <div class="particle particle-5"></div>
    <div class="particle particle-6"></div>
    <div class="particle particle-7"></div>
    <div class="particle particle-8"></div>
  </div>
</template>

<style scoped>
.floating-particles {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 1;
  overflow: hidden;
}

.particle {
  position: absolute;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  animation: float-particle 8s infinite ease-in-out;
}

/* Mix of Synthwave colors */
.particle-1 { top: 20%; left: 10%; background: #ff4757; box-shadow: 0 0 10px #ff4757; animation-delay: 0s; }
.particle-2 { top: 60%; left: 85%; background: #4ecdc4; box-shadow: 0 0 10px #4ecdc4; animation-delay: 1s; }
.particle-3 { top: 30%; left: 70%; background: #c44569; box-shadow: 0 0 10px #c44569; animation-delay: 2s; }
.particle-4 { top: 80%; left: 20%; background: #feca57; box-shadow: 0 0 10px #feca57; animation-delay: 3s; }
.particle-5 { top: 10%; left: 50%; background: #45b7d1; box-shadow: 0 0 10px #45b7d1; animation-delay: 4s; }
.particle-6 { top: 75%; left: 55%; background: #ff6b6b; box-shadow: 0 0 10px #ff6b6b; animation-delay: 2.5s; }
.particle-7 { top: 40%; left: 30%; background: #4ecdc4; box-shadow: 0 0 10px #4ecdc4; animation-delay: 1.5s; }
.particle-8 { top: 90%; left: 90%; background: #c44569; box-shadow: 0 0 10px #c44569; animation-delay: 0.5s; }

@keyframes float-particle {
  0%, 100% {
    transform: translateY(0) scale(1);
    opacity: 0.2;
  }
  50% {
    transform: translateY(-20px) scale(1.5);
    opacity: 0.8;
  }
}
</style>
