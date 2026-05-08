<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import { useRouter } from 'vue-router'
import { useSaveStore } from '@/entities/save/model/saveStore'
import { useBridgeStore } from '@/shared/api/bridgeStore'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import { useCoachStore } from '@/entities/coach/model/coachStore'
import { useSessionStore } from '@/entities/session/model/sessionStore'
import PageShell from '@/shared/ui/PageShell.vue'
import Frame from '@/shared/ui/core/Frame.vue'
import CyberCheckbox from '@/shared/ui/core/CyberCheckbox.vue'
import DialogueBox from '@/widgets/dialogue-box/DialogueBox.vue'

const router = useRouter()
const save = useSaveStore()
const audio = useAudioStore()
const bridgeStore = useBridgeStore()
const coach = useCoachStore()
const sessionStore = useSessionStore()

const phase = ref<'loading' | 'briefing' | 'goals'>('loading')
const briefText = ref('Settle in. Loading your session brief...')

// Goal suggestions — populated from backend focus items
const suggestions = ref([
  { id: '1', desc: 'APEX SPEED AT T7', target: '+3 km/h target', selected: false },
  { id: '2', desc: 'BREAK 1:48', target: 'PB delta', selected: false },
  { id: '3', desc: 'TRAIL BRAKE EVERY ENTRY', target: '', selected: false },
  { id: '4', desc: 'SECTOR 2 SUB-37s', target: '', selected: false }
])

onMounted(async () => {
  try {
    await coach.fetchBrief({
      driver: save.activeSlot?.driverName,
      sessionId: sessionStore.activeSessionId ?? undefined,
    })
    
    if (coach.brief) {
      briefText.value = coach.brief.narrative_md || 'Ready when you are.'
      
      if (coach.brief.focus?.length) {
        suggestions.value = coach.brief.focus.map((f, i) => ({
          id: String(i + 1),
          desc: f.toUpperCase(),
          target: '',
          selected: false,
        }))
        while (suggestions.value.length < 4) {
          suggestions.value.push({
            id: String(suggestions.value.length + 1),
            desc: suggestions.value.length === 3 ? 'SECTOR 2 SUB-37s' : 'CONSISTENCY',
            target: '',
            selected: false,
          })
        }
      }
    }
  } catch {
    briefText.value = 'Settle in. Peak grip today, so we\'re going to be tight. T7 is costing you 0.4s vs last week.'
  }
  
  phase.value = 'briefing'
})

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
  } else if (e.key === 's' || e.key === 'S') {
    confirmSelection()
  } else if (e.key === 'w' || e.key === 'W' || e.key === ' ') {
    audio.playSfx('cursor_select')
    router.push('/analysis/track')
  } else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.push('/garage')
  }
})


const confirmSelection = async () => {
  audio.playSfx('cursor_select')
  const selectedGoals = suggestions.value.filter(g => g.selected).map(g => g.desc)
  await sessionStore.startSession({
    driver: save.activeSlot?.driverName,
    track: 'Sonoma Raceway',
  })
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

const weatherText = ref('peak grip · 13:00')
const surfaceText = ref('DRY · 21°C')
</script>

<template>
  <PageShell 
    title="PRE-SESSION BRIEFING · SONOMA RACEWAY" 
    :actions="[
      { label: 'TOGGLE', key: 'Enter', keyLabel: 'A' },
      { label: 'START', key: 's', keyLabel: 'START', variant: 'primary' },
      { label: 'WALK', key: 'w', keyLabel: 'W' },
      { label: 'BACK', key: 'Escape', keyLabel: 'B', variant: 'warn' }
    ]" 
    bg="cool" 
    :show-heading="false"
  >
    <h1 class="text-title ml-2 mb-1">PRE-SESSION BRIEFING · SONOMA RACEWAY</h1>

    <div class="ml-2 mb-4 flex flex-wrap gap-x-4 gap-y-1 text-small tracking-[0.2em] text-slate uppercase">
      <span class="text-ui-info font-bold">PRE-FLIGHT</span>
      <span><span :class="bridgeStore.healthStatus === 'ok' ? 'text-ui-good' : 'text-ui-bad'">{{ bridgeStore.healthStatus === 'ok' ? '✓' : '✗' }}</span> BRIDGE</span>
      <span><span class="text-ui-good">✓</span> USB-CAN</span>
      <span><span class="text-ui-good">✓</span> DBC</span>
      <span><span class="text-ui-good">✓</span> CALIBRATION</span>
    </div>
    
    <div v-if="phase === 'loading'" class="flex-grow flex flex-col items-center justify-center text-slate text-body">
      <div class="animate-pulse">LOADING BRIEF...</div>
    </div>
    
    <div v-if="phase === 'briefing'" class="flex-grow flex flex-col justify-end">
      <DialogueBox 
        :coach-id="save.activeSlot?.preferredCoach ?? 'trod'"
        :emotion="coach.brief?.emotion ?? 'talk'"
        :text="briefText"
        @done="phase = 'goals'"
      />
    </div>
    
    <div v-else-if="phase === 'goals'" class="flex-grow flex flex-col mt-2">
      <h2 class="text-small text-slate tracking-widest border-b border-slate pb-1 mb-4 ml-2 uppercase">PICK YOUR GOALS (1-3)</h2>
      
      <Frame variant="default" padding="16px" class="flex-grow flex flex-col">
        <div class="flex flex-col gap-4">
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
        
        <div class="mt-auto pt-4 border-t border-slate text-center">
          <span 
            class="px-12 py-2 transition-all cursor-pointer inline-block text-title-sm tracking-[0.2em]"
            :class="cursorIndex === 4 ? 'bg-ui-good text-ink font-bold shadow-[0_0_15px_rgba(42,161,152,0.5)]' : 'text-silver hover:text-white'"
            @click="confirmSelection"
            @mouseover="cursorIndex = 4"
          >
            <span v-if="cursorIndex === 4" class="mr-2">▶</span>CONFIRM SELECTION
          </span>
        </div>
      </Frame>
      
      <div class="flex gap-8 text-small text-slate mt-4 px-2 mb-2 border-t border-slate pt-4 tracking-widest uppercase">
        <span class="flex items-center gap-2"><span class="text-ui-info text-body">☀</span> WEATHER <span class="text-slate/30 mx-1">|</span> {{ coach.brief?.weather_phase ?? weatherText }}</span>
        <span class="flex items-center gap-2"><span class="text-ui-info text-body">☷</span> TRACK <span class="text-slate/30 mx-1">|</span> {{ coach.brief?.surface_state ?? surfaceText }}</span>
      </div>
    </div>
  </PageShell>
</template>


<style scoped>
/* No hardcoded viewport dimensions — fullscreen is enforced by global.css */
</style>
