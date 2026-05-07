<script setup lang="ts">
import { onMounted, onUnmounted, watch } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import { useSaveStore } from '@/entities/save/model/saveStore'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import { useBridgeStore } from '@/shared/api/bridgeStore'
import { usePauseStore } from '@/shared/lib/pauseStore'
import BridgeOfflineBanner from '@/widgets/bridge-offline/BridgeOfflineBanner.vue'
import PauseMenu from '@/widgets/pause-menu/PauseMenu.vue'
import ParticleBackground from '@/shared/ui/ParticleBackground.vue'
import UpdateToast from '@/widgets/update-toast/UpdateToast.vue'
import TransitionWipe from '@/widgets/transition-wipe/TransitionWipe.vue'
import { useTouchNavigation } from '@/shared/lib/useTouchNavigation'

const saveStore = useSaveStore()
const audioStore = useAudioStore()
const bridgeStore = useBridgeStore()
const pauseStore = usePauseStore()
const route = useRoute()

const handleGlobalKey = (e: KeyboardEvent) => {
  // Allow toggling pause with Escape anywhere except Title screen
  if (e.key === 'Escape' && route.path !== '/' && !pauseStore.isVisible) {
    audioStore.playSfx('transition_wipe') // ducking sound logic could go here
    pauseStore.togglePause()
  }
}

useTouchNavigation()

watch(() => saveStore.activeSlot?.settings?.display?.reducedMotion, (reduce) => {
  if (reduce) {
    document.body.classList.add('reduced-motion')
  } else {
    document.body.classList.remove('reduced-motion')
  }
}, { immediate: true })

onMounted(async () => {
  await saveStore.hydrate()
  bridgeStore.startPolling()
  
  window.addEventListener('keydown', handleGlobalKey)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleGlobalKey)
})
</script>

<template>
  <div class="app-root">
    <!-- The application container is now permanently full screen -->
    <div class="app-container relative">
      <!-- CRT screen overlay -->
      <div class="crt-overlay" v-if="!route.meta.performance"></div>
  
      <RouterView v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </RouterView>
  
      <!-- Global Overlays -->
      <ParticleBackground v-if="!route.meta.performance" />
      <BridgeOfflineBanner />
      <PauseMenu />
      <UpdateToast />
      <TransitionWipe />
    </div>

    <!-- Portrait Mode Warning -->
    <div class="portrait-warning">
      <svg xmlns="http://www.w3.org/2000/svg" class="w-16 h-16 mb-4 animate-bounce text-ui-warn" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <rect x="5" y="2" width="14" height="20" rx="2" ry="2"></rect>
        <line x1="12" y1="18" x2="12.01" y2="18"></line>
        <path d="M19 8l-4-4-4 4"></path>
        <path d="M15 4v10"></path>
      </svg>
      <span class="text-white relative z-10 p-2 bg-ink/80 border-l-4 border-ui-good">PLEASE ROTATE YOUR DEVICE</span>
    </div>
  </div>
</template>

<style scoped>
.app-root {
  width: 100vw;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #050505;
  overflow: hidden;
  /* Hardware casing texture */
  background-image: url('data:image/svg+xml;utf8,<svg width="40" height="40" xmlns="http://www.w3.org/2000/svg"><path d="M0 0h40v40H0z" fill="none"/><path d="M0 0h1v1H0zm39 39h1v1h-1z" fill="rgba(255,255,255,0.02)"/></svg>');
}

.app-container {
  width: 100vw;
  height: 100vh;
  background-color: var(--color-ink);
  overflow: hidden;
  position: relative;
  /* Simulated CRT Bezel */
  border-radius: 0px;
  box-shadow: 
    0 0 0 4px #1a1a1a,
    0 0 0 12px #0a0a0a,
    inset 0 0 20px rgba(0,0,0,0.8),
    0 20px 40px rgba(0,0,0,0.9);
}

/* Screen Glare */
.app-container::after {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 150%;
  height: 150%;
  background: linear-gradient(
    135deg,
    rgba(255,255,255,0.1) 0%,
    rgba(255,255,255,0) 40%,
    rgba(255,255,255,0) 100%
  );
  pointer-events: none;
  z-index: 200;
  transform: translateY(-20%) translateX(-20%);
}

@media screen and (min-width: 1024px) {
  .app-container {
    width: calc(100vw - 40px);
    height: calc(100vh - 40px);
    border-radius: 24px;
    max-width: 1600px;
    max-height: 900px;
  }
}

.portrait-warning {
  display: none;
  position: absolute;
  inset: 0;
  background-color: var(--color-ink);
  color: white;
  z-index: 9999;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  font-family: var(--font-title);
  font-size: clamp(14px, 3.5vmin, 28px);
  text-align: center;
  padding: clamp(16px, 4vmin, 32px);
}

@media screen and (orientation: portrait) and (max-width: 768px) {
  .portrait-warning {
    display: flex;
  }
}

/* Base fade transition for routes */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
