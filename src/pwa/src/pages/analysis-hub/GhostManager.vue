<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import PageShell from '@/shared/ui/PageShell.vue'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'
import CyberSplitView from '@/shared/ui/core/CyberSplitView.vue'

const router = useRouter()
const audio = useAudioStore()

const ghosts = ref([
  { id: 'ghost_1', track: 'SONOMA', time: '1:34.210', date: '2026-05-01', size: '2.4MB', active: true },
  { id: 'ghost_2', track: 'SONOMA', time: '1:35.050', date: '2026-04-28', size: '2.3MB', active: false },
  { id: 'ghost_3', track: 'SONOMA', time: '1:36.100', date: '2026-04-20', size: '2.5MB', active: false },
  { id: 'ghost_4', track: 'LAGUNA', time: '1:28.400', date: '2026-04-15', size: '2.1MB', active: false },
  { id: 'ghost_5', track: 'LAGUNA', time: '1:30.000', date: '2026-04-10', size: '2.0MB', active: false },
])

const cursorIndex = ref(0)
const selectedGhost = ref(ghosts.value[0])
const processing = ref(false)

useKeyboard((e: KeyboardEvent) => {
  if (processing.value) return

  if (e.key === 'ArrowDown') {
    cursorIndex.value = (cursorIndex.value + 1) % ghosts.value.length
    selectedGhost.value = ghosts.value[cursorIndex.value]
    audio.playSfx('cursor_move')
  } else if (e.key === 'ArrowUp') {
    cursorIndex.value = (cursorIndex.value - 1 + ghosts.value.length) % ghosts.value.length
    selectedGhost.value = ghosts.value[cursorIndex.value]
    audio.playSfx('cursor_move')
  } else if (e.key === 'Enter' || e.key === 'a') {
    audio.playSfx('cursor_select')
    processing.value = true
    setTimeout(() => {
      ghosts.value.forEach((g, i) => {
        if (i === cursorIndex.value) g.active = !g.active
        else if (g.track === selectedGhost.value.track) g.active = false // Only one active ghost per track
      })
      processing.value = false
    }, 500)
  } else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.back()
  } else if (e.key === 'Delete' || e.key === 'd') {
    if (ghosts.value.length > 1) {
      audio.playSfx('cancel')
      processing.value = true
      setTimeout(() => {
        ghosts.value.splice(cursorIndex.value, 1)
        if (cursorIndex.value >= ghosts.value.length) cursorIndex.value = ghosts.value.length - 1
        selectedGhost.value = ghosts.value[cursorIndex.value]
        processing.value = false
      }, 500)
    }
  }
})
</script>

<template>
  <PageShell title="GHOST MANAGER" :hints="['A · TOGGLE ACTIVE', 'B · BACK', 'D · DELETE', '▲ ▼ NAVIGATE']" bg="neutral">
    <template #heading>
      <div class="heading-block mb-[1.5vh]">
        <h1 class="text-title font-title text-silver tracking-[0.2em]">GHOST DATA EXPLORER</h1>
        <div class="heading-rule"></div>
      </div>
    </template>

    <CyberSplitView split="60-40" class="flex-grow mx-2 pb-6 min-h-0">
      
      <template #left>
        <CyberPanel class="flex flex-col h-full overflow-hidden p-0 bg-ink min-h-0">
          <div class="bg-charcoal px-3 py-2 border-b border-slate font-bold text-silver text-small sticky top-0 z-10 flex justify-between">
            <span>FILE</span>
            <span>STATUS</span>
          </div>
          
          <div class="flex-grow overflow-y-auto min-h-0 p-2">
            <div 
              v-for="(g, i) in ghosts" 
              :key="g.id"
              class="mb-1"
            >
              <div 
                class="flex items-center px-2 py-2 border transition-colors cursor-pointer"
                :class="cursorIndex === i ? 'bg-charcoal border-white text-white' : 'border-transparent text-silver'"
                @click="cursorIndex = i; selectedGhost = g; audio.playSfx('cursor_select'); if (!processing) { processing = true; setTimeout(() => { ghosts.forEach((gh, gi) => { if (gi === i) gh.active = !gh.active; else if (gh.track === g.track) gh.active = false; }); processing = false; }, 500) }"
              >
                <span class="w-6 shrink-0 font-bold text-ui-good" v-if="cursorIndex === i">▶</span>
                <span class="w-6 shrink-0" v-else></span>
                
                <div class="flex-grow flex flex-col">
                  <span class="font-bold">{{ g.track }} · {{ g.time }}</span>
                  <span class="text-small text-slate font-mono">{{ g.date }} · {{ g.size }}</span>
                </div>
                
                <span v-if="g.active" class="text-ui-good text-small border border-ui-good px-2 py-1 rounded bg-ui-good/20 font-bold">ACTIVE</span>
              </div>
            </div>
          </div>
        </CyberPanel>
      </template>

      <template #right>
        <CyberPanel class="flex flex-col h-full bg-charcoal border-slate p-3">
          <div class="text-silver font-bold uppercase mb-2 border-b border-slate pb-1">FILE DETAILS</div>
          
          <div class="flex flex-col gap-2 font-mono text-body mt-2">
            <div class="flex justify-between">
              <span class="text-slate">ID</span>
              <span class="text-white">{{ selectedGhost.id.toUpperCase() }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-slate">TRACK</span>
              <span class="text-white">{{ selectedGhost.track }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-slate">TIME</span>
              <span class="text-ui-good font-bold">{{ selectedGhost.time }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-slate">SIZE</span>
              <span class="text-white">{{ selectedGhost.size }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-slate">DATE</span>
              <span class="text-white">{{ selectedGhost.date }}</span>
            </div>
          </div>
          
          <div class="mt-auto pt-4 border-t border-slate text-center text-small text-slate">
            <span v-if="processing" class="text-ui-warn animate-pulse font-bold tracking-widest">PROCESSING...</span>
            <span v-else>Use A to toggle this ghost on track. Only one ghost can be active per track.</span>
          </div>
        </CyberPanel>
      </template>

    </CyberSplitView>
  </PageShell>
</template>
