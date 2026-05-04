<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import PageShell from '@/shared/ui/PageShell.vue'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'
import CyberSplitView from '@/shared/ui/core/CyberSplitView.vue'
import CoachFloat from '@/shared/ui/CoachFloat.vue'
import TrackMap from '@/shared/ui/core/TrackMap.vue'

const router = useRouter()
const audio = useAudioStore()

// Toggles
const showElevation = ref(true)
const showMarkers = ref(true)
const showDanger = ref(true)

const cursorIndex = ref(0)

// Mock markers around the track
const pointsOfInterest = ref([
  { id: 'T1', progress: 8, cx: 0, cy: 0, type: 'marker', name: 'the K-wall bend', text: 'Apex tight at the K-wall — bumpy on entry. Do not run wide.' },
  { id: 'T2', progress: 14, cx: 0, cy: 0, type: 'danger', name: 'Off-camber exit', text: 'T2 falls away from you. Get the turning done early or you are in the dirt.' },
  { id: 'T3', progress: 18, cx: 0, cy: 0, type: 'marker', name: 'Blind crest', text: 'Spot your braking point before the sky fills your windshield.' },
  { id: 'T4', progress: 24, cx: 0, cy: 0, type: 'marker', name: 'The chute', text: 'Downhill braking. Rear gets light. Trail brake gently.' },
  { id: 'T7', progress: 55, cx: 0, cy: 0, type: 'danger', name: 'T7 braking zone', text: 'Heavy Gs. If you lock up here, you are going straight into the tires.' },
  { id: 'T10', progress: 80, cx: 0, cy: 0, type: 'marker', name: 'Sweeping right', text: 'Fastest corner on the track. Need absolute commitment.' },
  { id: 'T11', progress: 90, cx: 0, cy: 0, type: 'danger', name: 'The Hairpin', text: 'Everyone tries to out-brake each other here. Focus on the exit.' }
])

const trackMapRef = ref<any>(null)

import { onMounted } from 'vue'
onMounted(() => {
  setTimeout(() => {
    if (trackMapRef.value) {
      pointsOfInterest.value.forEach(p => {
        const pt = trackMapRef.value.getPointAtProgress(p.progress)
        p.cx = pt.x
        p.cy = pt.y
      })
    }
  }, 100)
})

const cur = computed(() => pointsOfInterest.value[cursorIndex.value])

// Filter out points based on toggles
const visiblePoints = computed(() => {
  return pointsOfInterest.value.filter(p => {
    if (p.type === 'marker' && !showMarkers.value) return false
    if (p.type === 'danger' && !showDanger.value) return false
    return true
  })
})

const currentVisibleIndex = computed(() => {
  const curId = cur.value.id
  return visiblePoints.value.findIndex(p => p.id === curId)
})

const selectMarker = (id: string) => {
  const idx = pointsOfInterest.value.findIndex(p => p.id === id)
  if (idx !== -1) {
    cursorIndex.value = idx
    if (pointsOfInterest.value[idx].type === 'danger') {
      audio.playSfx('error_quiet')
    } else {
      audio.playSfx('cursor_select')
    }
  }
}

useKeyboard((e: KeyboardEvent) => {
  if (e.key === 'ArrowRight' || e.key === 'ArrowDown') {
    if (visiblePoints.value.length === 0) return
    const nextIdx = (currentVisibleIndex.value + 1) % visiblePoints.value.length
    const nextId = visiblePoints.value[nextIdx].id
    cursorIndex.value = pointsOfInterest.value.findIndex(p => p.id === nextId)
    
    if (cur.value.type === 'danger') {
      audio.playSfx('error_quiet')
    } else {
      audio.playSfx('cursor_move')
    }
  } else if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') {
    if (visiblePoints.value.length === 0) return
    const nextIdx = (currentVisibleIndex.value - 1 + visiblePoints.value.length) % visiblePoints.value.length
    const nextId = visiblePoints.value[nextIdx].id
    cursorIndex.value = pointsOfInterest.value.findIndex(p => p.id === nextId)
    
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
    cursorIndex.value = 0
    audio.playSfx('cursor_select')
  } else if (e.key === '3') {
    showDanger.value = !showDanger.value
    cursorIndex.value = 0
    audio.playSfx('cursor_select')
  } else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.push('/garage/analysis')
  }
})
</script>

<template>
  <PageShell title="TRACK ATLAS · SONOMA RACEWAY" :hints="['1/2/3 · TOGGLE', '◀ ▶ MOVE', 'B · BACK']" bg="cool">
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

    <CyberSplitView split="60-40" gap="sm" class="flex-grow min-h-0">
      
      <template #left>
        <!-- Track Map Area -->
        <CyberPanel class="h-full relative flex items-center justify-center bg-[#1A252C] overflow-hidden p-2">
          <TrackMap ref="trackMapRef" :strokeClass="showElevation ? 'stroke-[url(#elevationGradient)] stroke-[20]' : 'stroke-[#A0AAB5] stroke-[20]'">
            <defs v-if="showElevation">
              <linearGradient id="elevationGradient" x1="0%" y1="100%" x2="0%" y2="0%">
                <stop offset="0%" stop-color="#3B82F6" />
                <stop offset="50%" stop-color="#F59E0B" />
                <stop offset="100%" stop-color="#EF4444" />
              </linearGradient>
            </defs>
            
            <!-- Markers & Danger Zones -->
            <template v-for="p in pointsOfInterest" :key="p.id">
              <g v-if="((p.type === 'marker' && showMarkers) || (p.type === 'danger' && showDanger)) && p.cx !== 0" 
                 :class="[cur.id === p.id ? 'animate-bounce' : '', 'cursor-pointer touch-target']"
                 style="transform-origin: center; transform-box: fill-box;"
                 @click="selectMarker(p.id)">
                
                <!-- Invisible large touch target -->
                <circle :cx="p.cx" :cy="p.cy" r="60" fill="transparent" />
                
                <circle 
                  :cx="p.cx" :cy="p.cy" 
                  :r="cur.id === p.id ? 40 : 20" 
                  :fill="p.type === 'danger' ? '#EF4444' : '#5EED71'"
                  :stroke="cur.id === p.id ? '#FFFFFF' : '#1A252C'"
                  stroke-width="5"
                  class="pointer-events-none transition-all"
                />
                
                <circle v-if="p.type === 'danger' && showDanger"
                  :cx="p.cx" :cy="p.cy" 
                  r="80" 
                  fill="#EF4444"
                  opacity="0.2"
                  class="pointer-events-none transition-all"
                />
              </g>
            </template>
          </TrackMap>
          
          <div class="absolute bottom-2 left-2 text-body text-silver">
            <span v-if="visiblePoints.length > 0">
              <span class="text-ui-info font-bold">▶ Selected:</span> 
              <span :class="cur.type === 'danger' ? 'text-ui-warn' : 'text-white'">"{{ cur.name }}" ({{ cur.id }})</span>
            </span>
            <span v-else class="text-slate">NO OVERLAYS ACTIVE</span>
          </div>
        </CyberPanel>
      </template>

      <template #right>
        <!-- Elevation Chart Side Panel -->
        <CyberPanel class="h-full flex flex-col text-body p-2 overflow-hidden relative">
          <div class="text-silver mb-1">ELEVATION PROFILE</div>
          
          <div v-if="showElevation" class="flex-grow relative flex items-end border-l border-b border-slate pb-1 pl-1 mt-2">
            <svg viewBox="0 0 100 100" class="w-full h-full preserve-aspect-ratio-none">
              <line x1="0" y1="25" x2="100" y2="25" stroke="#4A5568" stroke-width="0.5" stroke-dasharray="2,2" opacity="0.5"/>
              <line x1="0" y1="50" x2="100" y2="50" stroke="#4A5568" stroke-width="0.5" stroke-dasharray="2,2" opacity="0.5"/>
              <line x1="0" y1="75" x2="100" y2="75" stroke="#4A5568" stroke-width="0.5" stroke-dasharray="2,2" opacity="0.5"/>
              
              <path d="M 0 90 L 10 85 L 20 60 L 30 20 L 40 40 L 50 45 L 60 70 L 70 80 L 80 85 L 90 90 L 100 90" 
                    fill="none" stroke="#5EED71" stroke-width="1.5" stroke-linejoin="bevel"/>
              
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
      </template>

    </CyberSplitView>
    
    <template #floating>
      <CoachFloat
        v-if="visiblePoints.length > 0"
        :emotion="cur.type === 'danger' ? 'talk' : 'idle'"
        :text="cur.text"
        :key="cur.id"
      />
    </template>
  </PageShell>
</template>
