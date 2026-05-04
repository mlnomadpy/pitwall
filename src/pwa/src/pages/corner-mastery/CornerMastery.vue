<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import PageShell from '@/shared/ui/PageShell.vue'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'
import CyberSplitView from '@/shared/ui/core/CyberSplitView.vue'

const router = useRouter()
const audio = useAudioStore()

// Mock corner data
const corners = [
  { id: 'T1', name: 'Turn 1', grade: 'B', entry: 140, apex: 120, exit: 145, brake: 20, glat: 1.1, time: 2.1, delta: '+0.1', class: 'med' },
  { id: 'T2', name: 'Turn 2', grade: 'A', entry: 120, apex: 85, exit: 110, brake: 60, glat: 1.3, time: 3.4, delta: '-0.2', class: 'low' },
  { id: 'T3', name: 'Turn 3', grade: 'C+', entry: 140, apex: 115, exit: 135, brake: 35, glat: 1.2, time: 2.8, delta: '+0.4', class: 'med' },
  { id: 'T3a', name: 'Turn 3a', grade: 'B-', entry: 135, apex: 125, exit: 140, brake: 15, glat: 1.0, time: 1.5, delta: '+0.2', class: 'high' },
  { id: 'T4', name: 'Turn 4', grade: 'A+', entry: 160, apex: 130, exit: 155, brake: 40, glat: 1.4, time: 2.5, delta: '-0.4', class: 'med' },
  { id: 'T5', name: 'Turn 5', grade: 'B', entry: 140, apex: 105, exit: 125, brake: 50, glat: 1.2, time: 3.0, delta: '+0.1', class: 'low' },
  { id: 'T6', name: 'Carousel', grade: 'B+', entry: 150, apex: 110, exit: 135, brake: 45, glat: 1.3, time: 4.5, delta: '-0.1', class: 'med' },
  { id: 'T7', name: 'Turn 7', grade: 'F', entry: 165, apex: 82, exit: 105, brake: 85, glat: 1.1, time: 4.8, delta: '+0.8', class: 'low' },
  { id: 'T8', name: 'Esses 1', grade: 'A', entry: 155, apex: 140, exit: 160, brake: 10, glat: 1.3, time: 1.8, delta: '-0.2', class: 'high' },
  { id: 'T8a', name: 'Esses 2', grade: 'A-', entry: 160, apex: 145, exit: 165, brake: 5, glat: 1.2, time: 1.7, delta: '-0.1', class: 'high' },
  { id: 'T9', name: 'Turn 9', grade: 'B', entry: 170, apex: 135, exit: 150, brake: 30, glat: 1.1, time: 2.4, delta: '+0.2', class: 'med' },
  { id: 'T10', name: 'Turn 10', grade: 'C', entry: 180, apex: 110, exit: 130, brake: 65, glat: 1.2, time: 3.2, delta: '+0.5', class: 'low' },
  { id: 'T11', name: 'Hairpin', grade: 'B+', entry: 175, apex: 64, exit: 95, brake: 90, glat: 1.4, time: 5.1, delta: '-0.1', class: 'low' },
]

const cursorIndex = ref(0)
const cur = computed(() => corners[cursorIndex.value])

const getClassColor = (c: string) => {
  if (c === 'low') return 'text-ui-warn'
  if (c === 'med') return 'text-silver'
  if (c === 'high') return 'text-ui-good'
  return 'text-silver'
}

const getGradeColor = (g: string) => {
  if (g.startsWith('A')) return 'text-ui-good font-bold drop-shadow-[1px_1px_0_#000]'
  if (g.startsWith('B') || g.startsWith('C') || g.startsWith('D')) return 'text-silver'
  if (g.startsWith('F')) return 'text-ui-warn font-bold drop-shadow-[1px_1px_0_#000]'
  return 'text-silver'
}

useKeyboard((e: KeyboardEvent) => {
  if (e.key === 'ArrowRight') {
    cursorIndex.value = Math.min(cursorIndex.value + 1, corners.length - 1)
    
    if (corners[cursorIndex.value].grade.startsWith('A')) {
      audio.playSfx('goal_complete')
    } else if (corners[cursorIndex.value].grade === 'F') {
      audio.playSfx('error_quiet')
    } else {
      audio.playSfx('cursor_move')
    }
  } else if (e.key === 'ArrowLeft') {
    cursorIndex.value = Math.max(cursorIndex.value - 1, 0)
    
    if (corners[cursorIndex.value].grade.startsWith('A')) {
      audio.playSfx('goal_complete')
    } else if (corners[cursorIndex.value].grade === 'F') {
      audio.playSfx('error_quiet')
    } else {
      audio.playSfx('cursor_move')
    }
  } else if (e.key === 'Enter' || e.key === 'a') {
    audio.playSfx('cursor_select')
  } else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.push('/garage/analysis')
  }
})
</script>

<template>
  <PageShell title="CORNER MASTERY" subtitle="session 2026-04-29-1503" :hints="['A · DRILL DOWN (SOON)', '◀ ▶ MOVE', 'B · BACK']" bg="cool">
    <!-- Stylized Track Map -->
    <CyberPanel class="flex items-center justify-center py-2 px-1 text-body overflow-hidden whitespace-nowrap bg-charcoal">
      <div class="flex gap-[6px]">
        <div 
          v-for="(c, i) in corners" 
          :key="c.id"
          class="flex flex-col items-center justify-center transition-transform"
          :class="cursorIndex === i ? 'scale-125 font-bold z-10' : 'opacity-70'"
        >
          <span v-if="cursorIndex === i" class="text-ui-good text-small absolute -top-2">▼</span>
          <span class="text-body" :class="getClassColor(c.class)">
            {{ c.class === 'low' ? '◓' : c.class === 'med' ? '◐' : '◑' }}
          </span>
          <span :class="cursorIndex === i ? 'text-white' : 'text-silver'">{{ c.id }}</span>
        </div>
      </div>
    </CyberPanel>
    
    <!-- Drill Panel -->
    <CyberPanel class="p-2 min-h-[50px] relative transition-colors duration-200"
           :class="cur.grade.startsWith('A') ? 'border-ui-good/50 bg-ui-good/10' : cur.grade === 'F' ? 'border-ui-warn/50 bg-ui-warn/10' : ''">
      <div class="flex justify-between items-end mb-2 border-b border-slate pb-1">
        <span class="font-bold text-body"><span class="text-ui-info mr-1">▶</span>{{ cur.id }}  {{ cur.name.toUpperCase() }}</span>
        <span class="text-title-lg leading-none" :class="getGradeColor(cur.grade)">{{ cur.grade }}</span>
      </div>
      
      <div class="grid grid-cols-3 gap-2 text-body">
        <div>ENTRY <span class="font-bold text-white ml-1">{{ cur.entry }}</span> km/h</div>
        <div>APEX <span class="font-bold text-white ml-1">{{ cur.apex }}</span> km/h</div>
        <div>EXIT <span class="font-bold text-white ml-1">{{ cur.exit }}</span> km/h</div>
        
        <div>PEAK BRAKE <span class="font-bold text-white ml-1">{{ cur.brake }}</span> bar</div>
        <div>MAX gLat <span class="font-bold text-white ml-1">{{ cur.glat }}</span></div>
        <div>TIME <span class="font-bold text-white ml-1">{{ cur.time }}</span>s</div>
      </div>
      
      <div class="absolute bottom-2 right-2 text-body text-silver">
        GOLD DELTA <span class="font-bold" :class="cur.delta.startsWith('-') ? 'text-ui-good' : 'text-ui-warn'">{{ cur.delta }}</span>s
      </div>
    </CyberPanel>
    
    <!-- Throttle/Brake Mock Charts -->
    <CyberSplitView split="50-50" gap="sm" class="flex-grow min-h-0">
      <template #left>
        <CyberPanel class="h-full flex flex-col text-body overflow-hidden p-2">
          <div class="text-silver mb-1">THROTTLE % BY CORNER</div>
          <div class="flex flex-col gap-1 overflow-hidden">
            <!-- Mock box plots -->
            <div v-for="c in corners.slice(0, 5)" :key="c.id" class="flex items-center gap-2">
              <span class="w-4 text-silver">{{ c.id }}</span>
              <div class="flex-grow h-[6px] relative border-b border-slate">
                <!-- Box -->
                <div class="absolute h-1 bg-charcoal border border-silver top-[2px]" :style="{ left: '20%', width: '40%' }"></div>
                <!-- Median -->
                <div class="absolute w-[1px] h-1 bg-ui-warn top-[2px]" :style="{ left: '40%' }"></div>
              </div>
            </div>
            <div class="text-slate text-center mt-1">...</div>
          </div>
        </CyberPanel>
      </template>
      
      <template #right>
        <CyberPanel class="h-full flex flex-col text-body overflow-hidden p-2">
          <div class="text-silver mb-1">BRAKE / ACCEL ZONES</div>
          <div class="flex flex-col gap-2 mt-2">
            <div class="flex gap-2 items-center">
              <span class="text-ui-warn">▼</span> <span class="w-8">DECEL</span>
              <div class="flex-grow h-2 bg-charcoal flex items-center">
                <div class="h-full bg-ui-warn w-1/3"></div>
                <span class="ml-1 text-small">T7</span>
              </div>
            </div>
            <div class="flex gap-2 items-center">
              <span class="text-ui-good">▲</span> <span class="w-8">ACCEL</span>
              <div class="flex-grow h-2 bg-charcoal flex items-center">
                <div class="h-full bg-ui-good w-1/2"></div>
                <span class="ml-1 text-small">T11</span>
              </div>
            </div>
          </div>
        </CyberPanel>
      </template>
    </CyberSplitView>
  </PageShell>
</template>
