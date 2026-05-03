<script setup lang="ts">
import type { SaveSlot } from '@/shared/types/save'
import Sprite from '@/entities/coach/Sprite.vue'
import CyberButton from '@/shared/ui/core/CyberButton.vue'
import CyberBox from '@/shared/ui/core/CyberBox.vue'

defineProps<{
  slotId: 1 | 2 | 3
  slotData: SaveSlot | null
  focused: boolean
}>()

const emit = defineEmits(['select', 'delete'])
</script>

<template>
  <CyberButton 
    fluid 
    complex
    size="sm"
    :variant="slotData ? 'primary' : 'dark'" 
    :active="focused"
    @click="emit('select')"
    :class="[
      'mb-[clamp(4px,1vmin,10px)] w-full',
      slotData ? 'h-[clamp(60px,10vh,90px)]' : 'h-[clamp(36px,6vh,48px)]'
    ]"
  >
    <div class="slot-content w-full flex items-center justify-between text-left relative z-10 px-2 pointer-events-none">
      
      <!-- Filled slot -->
      <template v-if="slotData">
        <div class="flex items-center gap-[clamp(8px,2vmin,20px)] w-full">
          <div class="cursor-col shrink-0 w-[clamp(12px,3vmin,24px)] text-white text-[clamp(10px,2.5vmin,20px)] font-bold">
            <span v-if="focused" class="animate-pulse">▶</span>
          </div>
          
          <div class="avatar-col shrink-0">
            <CyberBox variant="ink" border="none" class="avatar-frame w-[clamp(32px,8vmin,56px)] h-[clamp(32px,8vmin,56px)] border-2 border-white/20 flex items-center justify-center overflow-hidden">
              <Sprite sheet="avatars" animation="idle" :scale="0.6" />
            </CyberBox>
          </div>
          
          <div class="info-col flex-1 flex flex-col justify-center gap-1">
            <div class="slot-label text-[clamp(8px,1.8vmin,12px)] text-white/70 tracking-[0.1em]">SLOT {{ slotId }}</div>
            <div class="driver-name text-[clamp(16px,4vmin,28px)] text-white font-bold leading-none">
              {{ slotData.driverName.toUpperCase() }}
            </div>
            <div class="driver-meta text-[clamp(9px,2vmin,14px)] text-white/80">
              LV.{{ slotData.level }} · {{ slotData.skillLevel.toUpperCase() }} · {{ slotData.car }}
            </div>
          </div>
          
          <div class="time-col shrink-0 text-right pr-4">
            <span class="last-played text-[clamp(8px,1.8vmin,14px)] text-white/60 font-mono">{{ new Date(slotData.lastPlayedAt).toLocaleDateString() }}</span>
          </div>
        </div>
      </template>
      
      <!-- Empty slot -->
      <template v-else>
        <div class="flex items-center gap-[clamp(8px,2vmin,20px)] w-full py-2">
          <div class="cursor-col shrink-0 w-[clamp(12px,3vmin,24px)] text-white text-[clamp(10px,2.5vmin,20px)] font-bold">
            <span v-if="focused" class="animate-pulse">▶</span>
          </div>
          <div class="empty-content flex flex-col justify-center gap-1">
            <div class="slot-label text-[clamp(8px,1.8vmin,12px)] text-white/50 tracking-[0.1em]">SLOT {{ slotId }}</div>
            <div class="new-driver-text text-[clamp(14px,3vmin,24px)] text-white/80 animate-pulse">NEW DRIVER</div>
          </div>
        </div>
      </template>
      
    </div>
  </CyberButton>
</template>

<style scoped>
/* Layout styling handled by tailwind classes inside PixelButton */
</style>

