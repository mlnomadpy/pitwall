<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useSaveStore } from '@/entities/save/model/saveStore'
import { useNotificationsStore } from '@/shared/api/notificationStore'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import CyberBadge from '@/shared/ui/core/CyberBadge.vue'

const saveStore = useSaveStore()
const notificationStore = useNotificationsStore()
const audio = useAudioStore()
const router = useRouter()

const activeSlot = computed(() => {
  if (saveStore.activeSlotId === null) return null
  return saveStore.slots[saveStore.activeSlotId - 1]
})

const time = ref('')

const handleKey = (e: KeyboardEvent) => {
  // Global shortcut to open notifications from anywhere
  if ((e.key === 'n' || e.key === 'N') && document.activeElement?.tagName !== 'TEXTAREA') {
    audio.playSfx('cursor_select')
    router.push('/notifications')
  }
}

const props = defineProps<{ extra?: string }>()

let timer: number
onMounted(() => {
  const updateTime = () => {
    const d = new Date()
    time.value = `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
  }
  updateTime()
  timer = window.setInterval(updateTime, 1000)
  window.addEventListener('keydown', handleKey)
})

onUnmounted(() => {
  clearInterval(timer)
  window.removeEventListener('keydown', handleKey)
})
</script>

<template>
  <div class="status-bar pixelated">
    <!-- Left: Driver info -->
    <div class="bar-left" v-if="activeSlot">
      <span class="driver-name ">{{ activeSlot.driverName.toUpperCase() }}</span>
      <span class="bar-separator">·</span>
      <span class="driver-level">LV.{{ activeSlot.level }}</span>
    </div>
    <div class="bar-left" v-else>
      <span class="text-silver/50">NO DRIVER</span>
    </div>

    <!-- Center: Coach badge -->
    <div class="bar-center" v-if="activeSlot">
      <CyberBadge variant="bad" pulse>
        <span class="coach-dot"></span>
        {{ activeSlot.preferredCoach.toUpperCase() }}
      </CyberBadge>
    </div>

    <!-- Right: Clock + notifications -->
    <div class="bar-right">
      <span v-if="notificationStore.unreadCount > 0" class="notif-badge">{{ notificationStore.unreadCount }}</span>
      <span class="clock font-nums">{{ time }}</span>
    </div>
  </div>
</template>

<style scoped>
.status-bar {
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  width: clamp(280px, 80%, 600px);
  height: clamp(32px, 6vh, 52px);
  background: linear-gradient(180deg, var(--color-ink) 0%, rgba(11, 12, 16, 0.8) 100%);
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 clamp(16px, 4vw, 40px);
  font-family: var(--font-ui);
  font-size: clamp(10px, 2.5vmin, 22px);
  z-index: 10;
  
  /* Aggressive tech shape */
  clip-path: polygon(
    0 0,
    100% 0,
    calc(100% - 20px) 100%,
    20px 100%
  );
  
  /* Glowing bottom edge effect via a pseudo element (since drop-shadow on clip-path can be tricky) */
}

.status-bar::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 20px;
  right: 20px;
  height: 2px;
  background: var(--color-ui-good);
  box-shadow: 0 0 10px var(--color-ui-good);
  opacity: 0.8;
}

.bar-left, .bar-center, .bar-right {
  display: flex;
  align-items: center;
  gap: clamp(4px, 1vw, 10px);
  z-index: 2;
}

.bar-left { flex: 1; }
.bar-center { flex: 0 0 auto; }
.bar-right { flex: 1; justify-content: flex-end; }

.driver-name {
  color: var(--color-ui-info);
  font-weight: bold;
  text-shadow: 0 0 4px rgba(69, 183, 209, 0.5);
}

.driver-level {
  color: var(--color-silver);
}

.bar-separator {
  color: var(--color-slate);
}

.coach-badge {
  display: inline-flex;
  align-items: center;
  gap: clamp(3px, 0.6vw, 8px);
  color: var(--color-ui-coach);
  font-weight: bold;
  padding: clamp(2px, 0.4vh, 4px) clamp(8px, 2vw, 20px);
  border: 1px solid rgba(255, 71, 87, 0.4);
  background: rgba(255, 71, 87, 0.1);
  font-size: clamp(10px, 2.4vmin, 20px);
  clip-path: polygon(4px 0, 100% 0, calc(100% - 4px) 100%, 0 100%);
  text-shadow: 0 0 8px rgba(255, 71, 87, 0.6);
}

.coach-dot {
  width: clamp(4px, 0.8vmin, 8px);
  height: clamp(4px, 0.8vmin, 8px);
  background: var(--color-ui-coach);
  box-shadow: 0 0 6px var(--color-ui-coach);
  animation: dot-pulse 1s ease-in-out infinite alternate;
}

@keyframes dot-pulse {
  0% { opacity: 0.4; transform: scale(0.8); }
  100% { opacity: 1; transform: scale(1.2); }
}

.clock {
  color: var(--color-silver);
  letter-spacing: 0.1em;
  text-shadow: 0 0 4px rgba(197, 198, 199, 0.4);
}

.notif-badge {
  background: var(--color-ui-warn);
  color: var(--color-ink);
  font-size: clamp(8px, 1.8vmin, 14px);
  font-weight: bold;
  width: clamp(16px, 3.5vmin, 24px);
  height: clamp(16px, 3.5vmin, 24px);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  clip-path: polygon(50% 0%, 100% 25%, 100% 75%, 50% 100%, 0% 75%, 0% 25%);
  animation: dot-pulse 1s infinite alternate;
  box-shadow: 0 0 10px var(--color-ui-warn);
}
</style>
