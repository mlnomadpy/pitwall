<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useSaveStore } from '@/entities/save/model/saveStore'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import StatusBar from '@/widgets/status-bar/StatusBar.vue'
import HintBar from '@/widgets/hint-bar/HintBar.vue'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'
import CyberCheckbox from '@/shared/ui/core/CyberCheckbox.vue'
import DialogueBox from '@/widgets/dialogue-box/DialogueBox.vue'

const router = useRouter()
const save = useSaveStore()
const audio = useAudioStore()

const phase = ref<'loading' | 'briefing' | 'goals'>('briefing')

// Mocked goal suggestions
const suggestions = ref([
  { id: '1', desc: 'APEX SPEED AT T7', target: '+3 km/h target', selected: false },
  { id: '2', desc: 'BREAK 1:48', target: 'PB delta', selected: false },
  { id: '3', desc: 'TRAIL BRAKE EVERY ENTRY', target: '', selected: false },
  { id: '4', desc: 'SECTOR 2 SUB-37s', target: '', selected: false }
])

const cursorIndex = ref(0) // 0-3 for goals, 4 for confirm

const handleKey = (e: KeyboardEvent) => {
  if (phase.value !== 'goals') return

  if (e.key === 'ArrowDown') {
    cursorIndex.value = (cursorIndex.value + 1) % 5
    audio.playSfx('cursor_move')
  } else if (e.key === 'ArrowUp') {
    cursorIndex.value = (cursorIndex.value - 1 + 5) % 5
    audio.playSfx('cursor_move')
  } else if (e.key === 'Enter') {
    if (cursorIndex.value === 4) {
      // Confirm
      audio.playSfx('cursor_select') // The wipe to HUD handles the rest
      router.push('/hud')
    } else {
      // Toggle goal
      suggestions.value[cursorIndex.value].selected = !suggestions.value[cursorIndex.value].selected
      audio.playSfx('cursor_select')
      
      const selectedCount = suggestions.value.filter(g => g.selected).length
      if (selectedCount === 3 && suggestions.value[cursorIndex.value].selected) {
        audio.playSfx('goal_complete')
      }
    }
  } else if (e.key === 'w' || e.key === 'W' || e.key === ' ') {
    audio.playSfx('cursor_select')
    router.push('/analysis/track')
  } else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.push('/garage')
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKey)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKey)
})
</script>

<template>
  <div class="viewport pixelated relative w-full h-full bg-ink text-silver overflow-hidden  font-ui">
    <StatusBar />
    
    <div class="page-bg"></div>
    
    <div class="content pt-[6vh] pb-[6vh] px-2 h-full flex flex-col gap-1 relative z-10">
      <h1 class="text-title ml-2 mb-1">PRE-SESSION BRIEFING · SONOMA RACEWAY</h1>
      
      <CyberPanel class="px-2 py-1 flex gap-4 text-body text-silver border-ui-good">
        <span class="text-ui-info font-bold">PRE-FLIGHT</span>
        <span><span class="text-ui-good">✓</span> BRIDGE</span>
        <span><span class="text-ui-good">✓</span> USB-CAN</span>
        <span><span class="text-ui-good">✓</span> DBC</span>
        <span><span class="text-ui-good">✓</span> CALIBRATION</span>
      </CyberPanel>
      
      <div v-if="phase === 'briefing'" class="flex-grow flex flex-col justify-end">
        <DialogueBox 
          :coach-id="save.slots[save.activeSlotId!-1]?.preferredCoach ?? 'trod'"
          emotion="talk"
          text="Settle in. Peak grip today, so we're going to be tight. T7 is costing you 0.4s vs last week."
          @done="phase = 'goals'"
        />
      </div>
      
      <div v-else-if="phase === 'goals'" class="flex-grow flex flex-col mt-2">
        <h2 class="text-body text-silver border-b border-slate pb-1 mb-2 ml-1">PICK YOUR GOALS (1-3)</h2>
        
        <CyberPanel class="p-2 flex-grow flex flex-col">
          <div class="flex flex-col gap-2">
            <div 
              v-for="(g, i) in suggestions" 
              :key="g.id"
              class="flex items-center text-body gap-2 transition-colors px-1"
              :class="cursorIndex === i ? 'text-ui-good bg-charcoal' : 'text-silver'"
            >
              <span class="w-4 text-center">
                <template v-if="cursorIndex === i">▶</template>
              </span>
              <CyberCheckbox :checked="g.selected" />
              <span class="w-[clamp(140px,35vw,260px)]">{{ g.desc }}</span>
              <span class="text-body text-silver ml-auto">{{ g.target }}</span>
            </div>
          </div>
          
          <div class="mt-auto pt-2 border-t border-slate text-center text-body">
            <span 
              class="px-8 py-1 transition-colors"
              :class="cursorIndex === 4 ? 'bg-ui-good text-ink font-bold' : 'text-silver'"
            >
              <span v-if="cursorIndex === 4" class="mr-2">▶</span>CONFIRM
            </span>
          </div>
        </CyberPanel>
        
        <div class="flex gap-4 text-body text-silver mt-1 px-2 mb-1">
          <span>WEATHER <span class="text-ui-info mx-1">░</span> peak grip · 13:00</span>
          <span>TRACK <span class="text-ui-info mx-1">░</span> DRY · 21°C</span>
        </div>
      </div>
    </div>
    
    <HintBar :hints="['A · TOGGLE / CONFIRM', 'W · WALK TRACK', 'B · BACK']" />
  </div>
</template>

<style scoped>
/* No hardcoded viewport dimensions — fullscreen is enforced by global.css */
</style>
