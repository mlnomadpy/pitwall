<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import { useRouter } from 'vue-router'
import { useSaveStore } from '@/entities/save/model/saveStore'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import PageShell from '@/shared/ui/PageShell.vue'
import SlotCard from './ui/SlotCard.vue'

const router = useRouter()
const save = useSaveStore()
const audio = useAudioStore()
const cursor = ref(0)
const loading = ref(true)

const hints = ['▲ ▼ MOVE', 'A · LOAD / NEW', 'B · BACK']

onMounted(async () => {
  await save.hydrate()
  loading.value = false
})

useKeyboard((e: KeyboardEvent) => {
  if (e.key === 'ArrowDown') {
    cursor.value = (cursor.value + 1) % 3
    audio.playSfx('cursor_move')
  } else if (e.key === 'ArrowUp') {
    cursor.value = (cursor.value - 1 + 3) % 3
    audio.playSfx('cursor_move')
  } else if (e.key === 'Enter') {
    audio.playSfx('cursor_select')
    handleSelect((cursor.value + 1) as 1|2|3)
  } else if (e.key === 'Escape') {
    audio.playSfx('cancel')
  }
})

const handleSelect = (slotId: 1 | 2 | 3) => {
  save.activeSlotId = slotId
  cursor.value = slotId - 1
  const slotData = save.slots[slotId - 1]
  if (slotData) {
    router.push('/garage')
  } else {
    router.push('/onboarding/1')
  }
}
</script>

<template>
  <PageShell title="SELECT SAVE SLOT" :hints="hints" bg="neutral">
    <template #heading>
      <div class="heading-block mb-[3vh] text-center">
        <h1 class="text-title font-title text-silver tracking-[0.2em]">SELECT SAVE SLOT</h1>
        <div class="heading-rule"></div>
      </div>
    </template>
    
    <!-- Background overrides -->
    <div class="save-bg absolute inset-0 z-0 pointer-events-none"></div>
    <div class="crt-overlay pointer-events-none"></div>
    
    <div class="content flex flex-col items-center justify-center relative z-10 w-full flex-grow">
      
      <div v-if="loading" class="text-body text-slate animate-pulse">LOADING...</div>
      
      <div v-else class="slot-list w-full max-w-[92%]">
        <SlotCard 
          v-for="(slot, i) in save.slots" 
          :key="i"
          :slot-id="(i + 1) as 1|2|3"
          :slot-data="slot"
          :focused="cursor === i"
          @select="handleSelect((i + 1) as 1|2|3)"
          @mouseover="cursor = i"
        />
      </div>
    </div>
  </PageShell>
</template>

<style scoped>
.save-bg {
  background: linear-gradient(
    180deg,
    var(--color-ink) 0%,
    var(--color-asphalt-deep) 50%,
    var(--color-ink) 100%
  );
}
</style>
