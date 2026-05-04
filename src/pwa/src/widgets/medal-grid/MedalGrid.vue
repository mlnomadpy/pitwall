<script setup lang="ts">
import CyberBox from '@/shared/ui/core/CyberBox.vue'

interface Medal {
  id: string
  tier: string
  name: string
  desc: string
  unlocked: boolean
}

const props = defineProps<{
  medals: Medal[]
  cursorIndex: number
}>()

const emit = defineEmits<{
  (e: 'select', index: number): void
}>()
</script>

<template>
  <div class="medal-grid-container grid grid-cols-4 sm:grid-cols-5 gap-2 content-start overflow-y-auto no-scrollbar pb-2 min-h-0 h-full">
    <CyberBox 
      v-for="(m, i) in medals" 
      :key="m.id"
      variant="ink"
      border="slate"
      :selected="cursorIndex === i"
      class="medal-box aspect-square flex items-center justify-center cursor-pointer transition-all duration-200"
      :class="[
        !m.unlocked ? 'opacity-30 grayscale' : 'hover:scale-105',
        cursorIndex === i ? 'ring-2 ring-ui-good ring-offset-2 ring-offset-charcoal z-10 scale-105' : ''
      ]"
      @click="emit('select', i)"
    >
      <div v-if="m.unlocked" class="medal-icon-wrapper">
        <span class="medal-icon text-ui-warn drop-shadow-[2px_2px_0_#0d0d12]">★</span>
        <div class="medal-glow" :class="`tier-${m.tier.toLowerCase()}`"></div>
      </div>
      <span v-else class="text-[clamp(12px,3vmin,24px)] text-slate">?</span>
    </CyberBox>
  </div>
</template>

<style scoped>
.no-scrollbar::-webkit-scrollbar { display: none; }
.no-scrollbar { scrollbar-width: none; }

.medal-box {
  position: relative;
  overflow: hidden;
}

.medal-icon-wrapper {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
}

.medal-icon {
  font-size: clamp(20px, 5vmin, 40px);
  z-index: 2;
}

.medal-glow {
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at center, rgba(255,255,255,0.2) 0%, transparent 60%);
  z-index: 1;
}

.tier-bronze { background: radial-gradient(circle at center, rgba(205, 127, 50, 0.3) 0%, transparent 70%); }
.tier-silver { background: radial-gradient(circle at center, rgba(192, 192, 192, 0.3) 0%, transparent 70%); }
.tier-gold { background: radial-gradient(circle at center, rgba(255, 215, 0, 0.3) 0%, transparent 70%); }
.tier-platinum { background: radial-gradient(circle at center, rgba(229, 228, 226, 0.4) 0%, transparent 70%); }
.tier-rainbow { background: radial-gradient(circle at center, rgba(255, 105, 180, 0.4) 0%, transparent 70%); }
</style>
