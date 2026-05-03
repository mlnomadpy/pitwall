<script setup lang="ts">
import { ref, computed } from 'vue'
import { useSaveStore } from '@/entities/save/model/saveStore'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'
import CyberTabs from '@/shared/ui/core/CyberTabs.vue'
import CyberBox from '@/shared/ui/core/CyberBox.vue'

const props = defineProps<{
  active: boolean
}>()

const emit = defineEmits<{
  (e: 'play', phrase: any): void
}>()

const save = useSaveStore()
const audio = useAudioStore()

const coaches = [
  { id: 'trod', name: 'T-ROD', tab: 'T-RD' },
  { id: 'bentley', name: 'BENTLEY', tab: 'BNTLY' },
  { id: 'drill', name: 'DRILL', tab: 'DRILL' },
  { id: 'calm', name: 'CALM', tab: 'CALM' },
  { id: 'buddy', name: 'BUDDY', tab: 'BUDDY' }
]

const activeCoachIndex = ref(0)
const activeCoach = computed(() => coaches[activeCoachIndex.value])

// Mock phrases
const phrases = Array.from({ length: 50 }).map((_, i) => ({
  id: `phrase_${i}`,
  key: i === 0 ? 'distance_is_king' : i === 1 ? 'trail_brake' : `phrase_key_${i}`,
  text: i === 0 ? "Distance is king" : i === 1 ? "Roll the brake to apex" : `Mock phrase text for ${i}`,
  emotion: i % 3 === 0 ? '🅴' : i % 3 === 1 ? '🅸' : '🅼',
  unlocked: i < 47, // First 47 unlocked
}))

const cursorIndex = ref(0)

const handleKey = (e: KeyboardEvent) => {
  if (!props.active) return

  if (e.key === 'ArrowRight') {
    if (e.shiftKey) {
      activeCoachIndex.value = (activeCoachIndex.value + 1) % coaches.length
      cursorIndex.value = 0
      audio.playSfx('cursor_select')
    }
  } else if (e.key === 'ArrowLeft') {
    if (e.shiftKey) {
      activeCoachIndex.value = (activeCoachIndex.value - 1 + coaches.length) % coaches.length
      cursorIndex.value = 0
      audio.playSfx('cursor_select')
    }
  } else if (e.key === 'ArrowDown') {
    if (!e.shiftKey) {
      cursorIndex.value = (cursorIndex.value + 1) % phrases.length
      audio.playSfx('cursor_move')
      // auto scroll logic would normally go here if not native
      const el = document.getElementById(`phrase-${cursorIndex.value}`)
      el?.scrollIntoView({ block: 'nearest' })
    }
  } else if (e.key === 'ArrowUp') {
    if (!e.shiftKey) {
      cursorIndex.value = (cursorIndex.value - 1 + phrases.length) % phrases.length
      audio.playSfx('cursor_move')
      const el = document.getElementById(`phrase-${cursorIndex.value}`)
      el?.scrollIntoView({ block: 'nearest' })
    }
  } else if (e.key === 'Enter' || e.key === 'a') {
    const p = phrases[cursorIndex.value]
    if (p.unlocked) {
      audio.playSfx('cursor_select')
      emit('play', { coachId: activeCoach.value.id, text: p.text })
    } else {
      audio.playSfx('cancel')
    }
  }
}

defineExpose({ handleKey })
</script>

<template>
  <div class="flex-grow flex flex-col pt-2 h-[clamp(120px,30vh,240px)]" v-if="active">
    <!-- Coach Tabs -->
    <CyberTabs :tabs="coaches.map(c => c.tab)" v-model="activeCoachIndex" class="mb-2" />
    
    <div class="text-body text-silver mb-2 flex justify-between px-1 font-bold">
      <span>{{ activeCoach.name }} · 47 / 50 PHRASES HEARD</span>
      <span>94 %</span>
    </div>

    <!-- Phrase List -->
    <CyberPanel class="p-2 flex-grow flex flex-col overflow-hidden relative border-slate">
      <div class="overflow-y-auto flex-grow flex flex-col gap-1 pr-2" id="phrase-list" style="max-height: clamp(80px, 20vh, 160px);">
        <div 
          v-for="(p, i) in phrases" 
          :key="p.id"
          class="flex items-center text-body py-[2px] transition-colors"
          :class="[
            cursorIndex === i ? 'bg-charcoal text-white' : 'text-silver',
            !p.unlocked ? 'opacity-50 text-slate' : ''
          ]"
          :id="`phrase-${i}`"
        >
          <span class="w-4 flex-shrink-0 text-ui-good" v-if="cursorIndex === i">▶</span>
          <span class="w-4 flex-shrink-0" v-else></span>
          
          <span class="w-4 flex-shrink-0 text-ui-good" v-if="p.unlocked">✓</span>
          <span class="w-4 flex-shrink-0 text-charcoal-light" v-else>▒</span>

          <span class="w-4 flex-shrink-0 text-ui-info mr-1" v-if="p.unlocked">{{ p.emotion }}</span>
          <span class="w-4 flex-shrink-0 text-slate mr-1" v-else>??</span>

          <span class="w-[clamp(60px,15vw,120px)] flex-shrink-0 font-mono">{{ p.unlocked ? p.key : '???' }}</span>
          <span class="truncate ml-2 italic">"{{ p.unlocked ? p.text : '──────' }}"</span>
        </div>
      </div>
    </CyberPanel>
    
    <!-- Coach Avatar inline -->
    <CyberBox variant="charcoal" border="slate" class="absolute right-4 bottom-10 w-[clamp(48px,10vmin,80px)] h-[clamp(48px,10vmin,80px)] overflow-hidden pointer-events-none z-10 shadow-lg" v-if="active">
      <!-- Fallback image logic for coach, we use trod if image doesn't exist -->
      <img src="/sprites/coaches/trod.png" class="w-full h-auto object-cover opacity-80 mix-blend-screen scale-[1.5] origin-top-left" style="image-rendering: pixelated; filter: grayscale(1) sepia(1) hue-rotate(180deg) saturate(3);" />
    </CyberBox>
  </div>
</template>

<style scoped>
/* Hide scrollbar */
::-webkit-scrollbar {
  display: none;
}
</style>
