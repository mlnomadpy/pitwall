<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useSaveStore } from '@/entities/save/model/saveStore'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import { useNotificationsStore } from '@/shared/api/notificationStore'
import StatusBar from '@/widgets/status-bar/StatusBar.vue'
import HintBar from '@/widgets/hint-bar/HintBar.vue'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'
import CyberBox from '@/shared/ui/core/CyberBox.vue'

const router = useRouter()
const save = useSaveStore()
const audio = useAudioStore()
const store = useNotificationsStore()

const cursorIndex = ref(0)
const items = store.items

const handleKey = (e: KeyboardEvent) => {
  if (items.length === 0) {
    if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
      audio.playSfx('cancel')
      router.back()
    }
    return
  }

  if (e.key === 'ArrowDown') {
    cursorIndex.value = (cursorIndex.value + 1) % items.length
    audio.playSfx('cursor_move')
  } else if (e.key === 'ArrowUp') {
    cursorIndex.value = (cursorIndex.value - 1 + items.length) % items.length
    audio.playSfx('cursor_move')
  } else if (e.key === 'Enter' || e.key === 'a') {
    const item = items[cursorIndex.value]
    store.markRead(item.id)
    audio.playSfx('cursor_select')
    if (item.route) {
      router.push(item.route)
    }
  } else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.back()
  } else if (e.key === ' ') { // Space for ◆
    store.markAllRead()
    audio.playSfx('cancel') // Using cancel as a "sweep" sound based on spec
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKey)
  audio.playSfx('cursor_select') // Sound on enter
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKey)
})
</script>

<template>
  <div class="viewport pixelated relative w-full h-full bg-ink text-silver overflow-hidden  font-ui flex flex-col">
    <StatusBar />
    
    <div class="page-bg"></div>
    
    <div class="pt-[6vh] px-4 flex-grow flex flex-col z-0 relative pb-[6vh]">
      <div class="heading-block mb-[1.5vh] text-center">
        <h1 class="text-title font-title text-silver tracking-[0.2em]">NOTIFICATIONS</h1>
        <span class="text-body" :class="store.unreadCount > 0 ? 'text-ui-warn font-bold' : 'text-slate'">{{ store.unreadCount }} new</span>
      </div>

      <template v-if="items.length > 0">
        <CyberPanel class="flex-grow overflow-hidden flex flex-col p-2 bg-ink">
          <div 
            v-for="(item, i) in items" 
            :key="item.id"
            class="flex gap-2 py-2 border-b border-charcoal last:border-0 transition-opacity"
            :class="[
              cursorIndex === i ? 'bg-charcoal' : '',
              item.isRead ? 'opacity-50' : 'opacity-100'
            ]"
          >
            <div class="w-[8px] flex items-center justify-center shrink-0">
              <span v-if="cursorIndex === i" class="text-ui-good text-body animate-pulse">▶</span>
            </div>
            
            <div class="flex flex-col flex-grow min-w-0">
              <div class="flex justify-between items-start">
                <span class="text-body font-bold text-white truncate" :class="{'text-ui-warn': item.kind === 'hardware-warning'}">
                  {{ item.title }}
                </span>
                <span class="text-body text-slate shrink-0 ml-2">{{ item.timestamp }}</span>
              </div>
              <div class="text-body text-silver truncate">{{ item.subText }}</div>
            </div>
          </div>
        </CyberPanel>
      </template>
      
      <template v-else>
        <div class="flex-grow flex items-center justify-center text-center">
          <div class="flex flex-col items-center max-w-[clamp(140px,35vw,280px)]">
            <CyberBox variant="charcoal" border="slate" class="w-[clamp(36px,8vmin,64px)] h-[clamp(36px,8vmin,64px)] overflow-hidden mb-4 relative">
               <img :src="`/sprites/coaches/${save.slots[save.activeSlotId?-1:0]?.preferredCoach ?? 'trod'}.png`" class="w-full h-auto object-cover opacity-80 mix-blend-screen scale-[1.5] origin-top-left" style="image-rendering: pixelated; filter: grayscale(1) sepia(1) hue-rotate(180deg) saturate(3);" />
            </CyberBox>
            <p class="text-body text-slate leading-relaxed">
              No new notifications. Drive a session to fill this up.
            </p>
          </div>
        </div>
      </template>

    </div>
    
    <HintBar :hints="['A · OPEN', 'B · BACK', '◆ MARK ALL READ']" />
  </div>
</template>

<style scoped>
/* No hardcoded viewport dimensions — fullscreen is enforced by global.css */
</style>
