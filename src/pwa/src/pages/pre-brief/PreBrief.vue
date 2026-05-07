<script setup lang="ts">
import { ref } from 'vue'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import { useRouter } from 'vue-router'
import { useSaveStore } from '@/entities/save/model/saveStore'
import { useBridgeStore } from '@/shared/api/bridgeStore'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import PageShell from '@/shared/ui/PageShell.vue'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'
import CyberCheckbox from '@/shared/ui/core/CyberCheckbox.vue'
import DialogueBox from '@/widgets/dialogue-box/DialogueBox.vue'

const router = useRouter()
const save = useSaveStore()
const audio = useAudioStore()
const bridge = useBridgeStore()

const phase = ref<'loading' | 'briefing' | 'goals'>('briefing')

// Mocked goal suggestions
const suggestions = ref([
  { id: '1', desc: 'APEX SPEED AT T7', target: '+3 km/h target', selected: false },
  { id: '2', desc: 'BREAK 1:48', target: 'PB delta', selected: false },
  { id: '3', desc: 'TRAIL BRAKE EVERY ENTRY', target: '', selected: false },
  { id: '4', desc: 'SECTOR 2 SUB-37s', target: '', selected: false }
])

const cursorIndex = ref(0) // 0-3 for goals, 4 for confirm

useKeyboard((e: KeyboardEvent) => {
  if (phase.value !== 'goals') return

  if (e.key === 'ArrowDown') {
    cursorIndex.value = (cursorIndex.value + 1) % 5
    audio.playSfx('cursor_move')
  } else if (e.key === 'ArrowUp') {
    cursorIndex.value = (cursorIndex.value - 1 + 5) % 5
    audio.playSfx('cursor_move')
  } else if (e.key === 'Enter') {
    if (cursorIndex.value === 4) {
      confirmSelection()
    } else {
      toggleGoal(cursorIndex.value)
    }
  } else if (e.key === 'w' || e.key === 'W' || e.key === ' ') {
    audio.playSfx('cursor_select')
    router.push('/analysis/track')
  } else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.push('/garage')
  }
})

const confirmSelection = () => {
  audio.playSfx('cursor_select') // The wipe to HUD handles the rest
  router.push('/hud')
}

const toggleGoal = (index: number) => {
  const currentlySelected = suggestions.value.filter(g => g.selected).length
  const isSelected = suggestions.value[index].selected
  if (!isSelected && currentlySelected >= 3) {
    audio.playSfx('error_quiet')
    return
  }
  suggestions.value[index].selected = !isSelected
  cursorIndex.value = index
  audio.playSfx('cursor_select')
  
  const newSelectedCount = suggestions.value.filter(g => g.selected).length
  if (newSelectedCount === 3 && !isSelected) {
    audio.playSfx('goal_complete')
  }
}


</script>

<template>
  <PageShell title="PRE-SESSION BRIEFING · SONOMA RACEWAY" :hints="['A · TOGGLE / CONFIRM', 'W · WALK TRACK', 'B · BACK']" bg="cool" :show-heading="false">
    <h1 class="text-title ml-2 mb-1">PRE-SESSION BRIEFING · SONOMA RACEWAY</h1>
    
    <CyberPanel class="px-2 py-1 flex gap-4 text-body text-silver" :class="bridge.healthStatus === 'ok' ? 'border-ui-good' : 'border-ui-bad'">
      <span class="text-ui-info font-bold">PRE-FLIGHT</span>
      <span><span :class="bridge.healthStatus === 'ok' ? 'text-ui-good' : 'text-ui-bad'">{{ bridge.healthStatus === 'ok' ? '✓' : '✗' }}</span> BRIDGE</span>
      <span><span class="text-ui-good">✓</span> USB-CAN</span>
      <span><span class="text-ui-good">✓</span> DBC</span>
      <span><span class="text-ui-good">✓</span> CALIBRATION</span>
    </CyberPanel>
    
    <div v-if="phase === 'briefing'" class="flex-grow flex flex-col justify-end">
      <DialogueBox 
        :coach-id="save.activeSlot?.preferredCoach ?? 'trod'"
        emotion="talk"
        text="Settle in. Peak grip today, so we're going to be tight. T7 is costing you 0.4s vs last week."
        @done="phase = 'goals'"
      />
    </div>
    
    <div v-else-if="phase === 'goals'" class="flex-grow flex flex-col mt-2">
      <h2 class="text-body text-silver border-b border-slate pb-1 mb-2 ml-1">PICK YOUR GOALS (1-3)</h2>
      
      <CyberPanel class="p-2 flex-grow flex flex-col">
        <div class="flex flex-col gap-2">
          <CyberCheckbox 
            v-for="(g, i) in suggestions" 
            :key="g.id"
            :label="g.desc"
            :sub-label="g.target || undefined"
            :checked="g.selected"
            :focused="cursorIndex === i"
            @click="toggleGoal(i)"
          />
        </div>
        
        <div class="mt-auto pt-2 border-t border-slate text-center text-body">
          <span 
            class="px-8 py-1 transition-colors cursor-pointer"
            :class="cursorIndex === 4 ? 'bg-ui-good text-ink font-bold' : 'text-silver hover:text-white'"
            @click="confirmSelection"
            @mouseover="cursorIndex = 4"
          >
            <span v-if="cursorIndex === 4" class="mr-2">▶</span>CONFIRM
          </span>
        </div>
      </CyberPanel>
      
      <div class="flex gap-4 text-body text-silver mt-1 px-2 mb-1 border-t border-slate pt-2">
        <span class="flex items-center gap-2"><span class="text-ui-info text-[clamp(14px,3vmin,20px)]">☀</span> WEATHER <span class="text-ui-info mx-1">░</span> peak grip · 13:00</span>
        <span class="flex items-center gap-2"><span class="text-ui-info text-[clamp(14px,3vmin,20px)]">☷</span> TRACK <span class="text-ui-info mx-1">░</span> DRY · 21°C</span>
      </div>
    </div>
  </PageShell>
</template>

<style scoped>
/* No hardcoded viewport dimensions — fullscreen is enforced by global.css */
</style>
