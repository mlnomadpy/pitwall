<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import { useRouter } from 'vue-router'
import { useSaveStore } from '@/entities/save/model/saveStore'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import { useNotificationsStore } from '@/shared/api/notificationStore'
import PageShell from '@/shared/ui/PageShell.vue'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'
import CyberListRow from '@/shared/ui/core/CyberListRow.vue'
import CyberAvatar from '@/shared/ui/core/CyberAvatar.vue'

const router = useRouter()
const save = useSaveStore()
const audio = useAudioStore()
const store = useNotificationsStore()

const cursorIndex = ref(0)
const items = store.items

useKeyboard((e: KeyboardEvent) => {
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
})

onMounted(() => {
  audio.playSfx('cursor_select') // Sound on enter
})

</script>

<template>
  <PageShell :hints="['A · OPEN', 'B · BACK', '◆ MARK ALL READ']" bg="neutral">
    <template #heading>
      <div class="heading-block mb-[1.5vh] text-center">
        <h1 class="text-title font-title text-silver tracking-[0.2em]">NOTIFICATIONS</h1>
        <span class="text-body" :class="store.unreadCount > 0 ? 'text-ui-warn font-bold' : 'text-slate'">{{ store.unreadCount }} new</span>
      </div>
    </template>

    <template v-if="items.length > 0">
      <CyberPanel class="flex-grow overflow-hidden flex flex-col p-2 bg-ink">
        <div 
          v-for="(item, i) in items" 
          :key="item.id"
          :class="[
            cursorIndex === i ? 'bg-charcoal' : '',
            item.isRead ? 'opacity-50' : 'opacity-100'
          ]"
          class="px-2 pt-2 -mx-2 transition-opacity"
        >
          <CyberListRow 
            :title="item.title"
            :detail="item.timestamp"
            :status-state="item.kind === 'hardware-warning' ? 'error' : 'none'"
            :sub-lines="[item.subText]"
          >
            <template #icon v-if="cursorIndex === i">
              <span class="text-ui-good text-body animate-pulse">▶</span>
            </template>
          </CyberListRow>
        </div>
      </CyberPanel>
    </template>
    
    <template v-else>
      <div class="flex-grow flex items-center justify-center text-center">
        <div class="flex flex-col items-center max-w-[clamp(140px,35vw,280px)]">
          <CyberAvatar :sheet="save.activeSlot?.preferredCoach ?? 'trod'" size="md" variant="ghost" class="mb-4" />
          <p class="text-body text-slate leading-relaxed">
            No new notifications. Drive a session to fill this up.
          </p>
        </div>
      </div>
    </template>
  </PageShell>
</template>

<style scoped>
/* No hardcoded viewport dimensions — fullscreen is enforced by global.css */
</style>
