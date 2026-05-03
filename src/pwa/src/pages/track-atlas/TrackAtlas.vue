<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import { useSaveStore } from '@/entities/save/model/saveStore'
import StatusBar from '@/widgets/status-bar/StatusBar.vue'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'
import HintBar from '@/widgets/hint-bar/HintBar.vue'
import DialogueBox from '@/widgets/dialogue-box/DialogueBox.vue'

const router = useRouter()
const audio = useAudioStore()
const save = useSaveStore()

// Toggles
const showElevation = ref(true)
const showMarkers = ref(true)
const showDanger = ref(true)

const cursorIndex = ref(0)

// Mock markers around the track
const pointsOfInterest = [
  { id: 'T1', x: 25, y: 80, type: 'marker', name: 'the K-wall bend', text: 'Apex tight at the K-wall — bumpy on entry. Do not run wide.' },
  { id: 'T2', x: 15, y: 65, type: 'danger', name: 'Off-camber exit', text: 'T2 falls away from you. Get the turning done early or you are in the dirt.' },
  { id: 'T3', x: 20, y: 45, type: 'marker', name: 'Blind crest', text: 'Spot your braking point before the sky fills your windshield.' },
  { id: 'T4', x: 30, y: 20, type: 'marker', name: 'The chute', text: 'Downhill braking. Rear gets light. Trail brake gently.' },
  { id: 'T7', x: 90, y: 40, type: 'danger', name: 'T7 braking zone', text: 'Heavy Gs. If you lock up here, you are going straight into the tires.' },
  { id: 'T10', x: 80, y: 65, type: 'marker', name: 'Sweeping right', text: 'Fastest corner on the track. Need absolute commitment.' },
  { id: 'T11', x: 70, y: 80, type: 'danger', name: 'The Hairpin', text: 'Everyone tries to out-brake each other here. Focus on the exit.' }
]

const cur = computed(() => pointsOfInterest[cursorIndex.value])

// Filter out points based on toggles
const visiblePoints = computed(() => {
  return pointsOfInterest.filter(p => {
    if (p.type === 'marker' && !showMarkers.value) return false
    if (p.type === 'danger' && !showDanger.value) return false
    return true
  })
})

const currentVisibleIndex = computed(() => {
  const curId = cur.value.id
  return visiblePoints.value.findIndex(p => p.id === curId)
})

const handleKey = (e: KeyboardEvent) => {
  if (e.key === 'ArrowRight' || e.key === 'ArrowDown') {
    if (visiblePoints.value.length === 0) return
    const nextIdx = (currentVisibleIndex.value + 1) % visiblePoints.value.length
    const nextId = visiblePoints.value[nextIdx].id
    cursorIndex.value = pointsOfInterest.findIndex(p => p.id === nextId)
    
    if (cur.value.type === 'danger') {
      audio.playSfx('error_quiet')
    } else {
      audio.playSfx('cursor_move')
    }
  } else if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') {
    if (visiblePoints.value.length === 0) return
    const nextIdx = (currentVisibleIndex.value - 1 + visiblePoints.value.length) % visiblePoints.value.length
    const nextId = visiblePoints.value[nextIdx].id
    cursorIndex.value = pointsOfInterest.findIndex(p => p.id === nextId)
    
    if (cur.value.type === 'danger') {
      audio.playSfx('error_quiet')
    } else {
      audio.playSfx('cursor_move')
    }
  } else if (e.key === '1') {
    showElevation.value = !showElevation.value
    audio.playSfx('cursor_select')
  } else if (e.key === '2') {
    showMarkers.value = !showMarkers.value
    audio.playSfx('cursor_select')
    
    // Auto adjust cursor if current is hidden
    if (cur.value.type === 'marker' && !showMarkers.value && visiblePoints.value.length > 0) {
       cursorIndex.value = pointsOfInterest.findIndex(p => p.id === visiblePoints.value[0].id)
    }
  } else if (e.key === '3') {
    showDanger.value = !showDanger.value
    audio.playSfx('cursor_select')
    
    // Auto adjust cursor if current is hidden
    if (cur.value.type === 'danger' && !showDanger.value && visiblePoints.value.length > 0) {
       cursorIndex.value = pointsOfInterest.findIndex(p => p.id === visiblePoints.value[0].id)
    }
  } else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.push('/garage/analysis')
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
    
    <div class="content pt-[6vh] px-2 flex flex-col h-full z-0 relative gap-2">
      <div class="heading-block mb-[1.5vh]">
        <h1 class="text-title text-silver font-bold">TRACK ATLAS · SONOMA RACEWAY</h1>
      </div>

      <!-- Toggles & Weather -->
      <div class="flex justify-between items-center text-body mx-2">
        <div class="flex gap-4">
          <div class="flex items-center gap-1" :class="showElevation ? 'text-ui-good' : 'text-slate'">
            <span>[{{ showElevation ? 'x' : ' ' }}]</span> <span class="uppercase">Elevation <span class="text-silver text-small opacity-50">(1)</span></span>
          </div>
          <div class="flex items-center gap-1" :class="showMarkers ? 'text-ui-good' : 'text-slate'">
            <span>[{{ showMarkers ? 'x' : ' ' }}]</span> <span class="uppercase">Markers <span class="text-silver text-small opacity-50">(2)</span></span>
          </div>
          <div class="flex items-center gap-1" :class="showDanger ? 'text-ui-warn' : 'text-slate'">
            <span>[{{ showDanger ? 'x' : ' ' }}]</span> <span class="uppercase">Danger Zones <span class="text-silver text-small opacity-50">(3)</span></span>
          </div>
        </div>
        <div class="text-silver">
          WEATHER: peak grip · DRY · 21 °C
        </div>
      </div>

      <div class="grid grid-cols-[2fr_1fr] gap-2 flex-grow min-h-0 pb-16">
        
        <!-- Track Map Area -->
        <CyberPanel class="relative flex items-center justify-center bg-[#1A252C] overflow-hidden p-2">
          <!-- Sonoma-ish SVG shape -->
          <svg viewBox="0 0 100 100" class="w-full h-full drop-shadow-[2px_2px_0_#000]">
            <!-- Track outline with fake elevation coloring -->
            <path d="M 25 80 L 15 65 L 20 45 L 30 20 L 70 10 L 90 40 L 80 65 L 70 80 Z" fill="none" 
                  :stroke="showElevation ? 'url(#elevationGradient)' : '#A0AAB5'" 
                  stroke-width="3" stroke-linejoin="round"/>
                  
            <defs v-if="showElevation">
              <linearGradient id="elevationGradient" x1="0%" y1="100%" x2="0%" y2="0%">
                <stop offset="0%" stop-color="#3B82F6" /> <!-- Low -->
                <stop offset="50%" stop-color="#F59E0B" /> <!-- Mid -->
                <stop offset="100%" stop-color="#EF4444" /> <!-- High -->
              </linearGradient>
            </defs>
            
            <!-- Markers & Danger Zones -->
            <template v-for="p in pointsOfInterest" :key="p.id">
              <!-- Render if marker toggle is on OR danger toggle is on, depending on type -->
              <g v-if="(p.type === 'marker' && showMarkers) || (p.type === 'danger' && showDanger)" 
                 :class="{'animate-bounce': cur.id === p.id}"
                 style="transform-origin: center; transform-box: fill-box;">
                
                <circle 
                  :cx="p.x" :cy="p.y" 
                  :r="cur.id === p.id ? 4 : 2" 
                  :fill="p.type === 'danger' ? '#EF4444' : '#5EED71'"
                  :stroke="cur.id === p.id ? '#FFFFFF' : '#1A252C'"
                  stroke-width="1"
                />
                
                <!-- Danger Zone Shading (rough mock) -->
                <circle v-if="p.type === 'danger' && showDanger"
                  :cx="p.x" :cy="p.y" 
                  r="8" 
                  fill="#EF4444"
                  opacity="0.2"
                />
              </g>
            </template>
          </svg>
          
          <div class="absolute bottom-2 left-2 text-body text-silver">
            <span v-if="visiblePoints.length > 0">
              <span class="text-ui-info font-bold">▶ Selected:</span> 
              <span :class="cur.type === 'danger' ? 'text-ui-warn' : 'text-white'">"{{ cur.name }}" ({{ cur.id }})</span>
            </span>
            <span v-else class="text-slate">NO OVERLAYS ACTIVE</span>
          </div>
        </CyberPanel>

        <!-- Elevation Chart Side Panel -->
        <CyberPanel class="flex flex-col text-body p-2 overflow-hidden relative">
          <div class="text-silver mb-1">ELEVATION PROFILE</div>
          
          <div v-if="showElevation" class="flex-grow relative flex items-end border-l border-b border-slate pb-1 pl-1 mt-2">
            <!-- Mock pixel-line graph for elevation -->
            <svg viewBox="0 0 100 100" class="w-full h-full preserve-aspect-ratio-none">
              <!-- Grid lines -->
              <line x1="0" y1="25" x2="100" y2="25" stroke="#4A5568" stroke-width="0.5" stroke-dasharray="2,2" opacity="0.5"/>
              <line x1="0" y1="50" x2="100" y2="50" stroke="#4A5568" stroke-width="0.5" stroke-dasharray="2,2" opacity="0.5"/>
              <line x1="0" y1="75" x2="100" y2="75" stroke="#4A5568" stroke-width="0.5" stroke-dasharray="2,2" opacity="0.5"/>
              
              <!-- Elevation line (T1 to T11) -->
              <path d="M 0 90 L 10 85 L 20 60 L 30 20 L 40 40 L 50 45 L 60 70 L 70 80 L 80 85 L 90 90 L 100 90" 
                    fill="none" stroke="#5EED71" stroke-width="1.5" stroke-linejoin="bevel"/>
              
              <!-- Fill under curve -->
              <path d="M 0 90 L 10 85 L 20 60 L 30 20 L 40 40 L 50 45 L 60 70 L 70 80 L 80 85 L 90 90 L 100 90 L 100 100 L 0 100 Z" 
                    fill="#5EED71" opacity="0.1"/>
            </svg>
            
            <div class="absolute bottom-0 left-1 text-small text-slate">start</div>
            <div class="absolute bottom-0 right-1 text-small text-slate">finish</div>
            <div class="absolute top-0 left-[-6px] text-small text-slate">▲</div>
          </div>
          <div v-else class="flex-grow flex items-center justify-center text-slate text-center">
            ELEVATION<br>HIDDEN
          </div>
        </CyberPanel>

      </div>
      
      <div class="absolute bottom-6 left-2 right-2">
        <DialogueBox 
          v-if="visiblePoints.length > 0"
          :coach-id="save.slots[save.activeSlotId!-1]?.preferredCoach ?? 'trod'"
          :emotion="cur.type === 'danger' ? 'talk' : 'idle'"
          :text="cur.text"
          class="scale-[0.85] origin-bottom-left w-[117%]"
          :key="cur.id"
        />
      </div>
      
    </div>
    
    <HintBar :hints="['1/2/3 · TOGGLE', '◀ ▶ MOVE', 'B · BACK']" />
  </div>
</template>

<style scoped>
/* No hardcoded viewport dimensions — fullscreen is enforced by global.css */
</style>
