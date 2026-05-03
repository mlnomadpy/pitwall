<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useSaveStore } from '@/entities/save/model/saveStore'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import StatusBar from '@/widgets/status-bar/StatusBar.vue'
import HintBar from '@/widgets/hint-bar/HintBar.vue'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'
import CyberTabs from '@/shared/ui/core/CyberTabs.vue'
import CyberBox from '@/shared/ui/core/CyberBox.vue'
import DialogueBox from '@/widgets/dialogue-box/DialogueBox.vue'

const router = useRouter()
const save = useSaveStore()
const audio = useAudioStore()

const tabs = ['AUDIO', 'DISPLAY', 'CONTROLS', 'CAR', 'DRIVER'] as const
type Tab = typeof tabs[number]
const activeTab = ref<Tab>('AUDIO')
const tabIndex = computed(() => tabs.indexOf(activeTab.value))

const cursorIndex = ref(0)
const editMode = ref(false)
const confirmingDestructive = ref(false)

// Mock settings data
const settings = ref({
  audio: {
    master: 80,
    music: 50,
    sfx: 100,
    coach: 100,
    muteAll: false,
    muteCoach: false
  },
  display: {
    nightMode: false,
    reducedMotion: false,
    fpsCounter: false,
    scale: 'Auto'
  },
  controls: {
    layout: 'Arrows',
    swapAB: false
  },
  car: {
    current: 'BMW M3 (E46)'
  },
  driver: {
    name: save.slots[save.activeSlotId ? save.activeSlotId - 1 : 0]?.driverName ?? 'TAHA',
    level: 'PRO'
  }
})

const tabCounts: Record<Tab, number> = {
  'AUDIO': 6,
  'DISPLAY': 4,
  'CONTROLS': 3,
  'CAR': 3,
  'DRIVER': 3
}

const coachQuips: Record<Tab, string> = {
  'AUDIO': "Volume's your call, kid.",
  'DISPLAY': "Make sure you can read the dash.",
  'CONTROLS': "Set it up however your hands like it.",
  'CAR': "Don't mess with the tune too much.",
  'DRIVER': "Identity is everything out there."
}

watch(activeTab, () => {
  cursorIndex.value = 0
  editMode.value = false
  confirmingDestructive.value = false
})

const handleKey = (e: KeyboardEvent) => {
  if (confirmingDestructive.value) {
    if (e.key === 'y' || e.key === 'Y' || e.key === 'Enter') {
      audio.playSfx('cancel') // heavy
      save.activeSlotId = null
      router.push('/')
    } else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'n' || e.key === 'N') {
      confirmingDestructive.value = false
      audio.playSfx('cursor_move')
    }
    return
  }

  if (editMode.value) {
    if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b' || e.key === 'Enter' || e.key === 'a') {
      editMode.value = false
      audio.playSfx('cursor_select')
    } else if (e.key === 'ArrowRight') {
      adjustValue(1)
    } else if (e.key === 'ArrowLeft') {
      adjustValue(-1)
    }
    return
  }

  if (e.key === 'ArrowRight') {
    activeTab.value = tabs[(tabIndex.value + 1) % tabs.length]
    audio.playSfx('cursor_move')
  } else if (e.key === 'ArrowLeft') {
    activeTab.value = tabs[(tabIndex.value - 1 + tabs.length) % tabs.length]
    audio.playSfx('cursor_move')
  } else if (e.key === 'ArrowDown') {
    cursorIndex.value = (cursorIndex.value + 1) % tabCounts[activeTab.value]
    audio.playSfx('cursor_move')
  } else if (e.key === 'ArrowUp') {
    cursorIndex.value = (cursorIndex.value - 1 + tabCounts[activeTab.value]) % tabCounts[activeTab.value]
    audio.playSfx('cursor_move')
  } else if (e.key === 'Enter' || e.key === 'a') {
    handleSelect()
  } else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.back()
  }
}

const handleSelect = () => {
  audio.playSfx('cursor_select')
  if (activeTab.value === 'AUDIO' && cursorIndex.value < 4) {
    editMode.value = true
  } else if (activeTab.value === 'AUDIO' && cursorIndex.value === 4) {
    settings.value.audio.muteAll = !settings.value.audio.muteAll
  } else if (activeTab.value === 'AUDIO' && cursorIndex.value === 5) {
    settings.value.audio.muteCoach = !settings.value.audio.muteCoach
  } else if (activeTab.value === 'DISPLAY' && cursorIndex.value < 3) {
    const keys = ['nightMode', 'reducedMotion', 'fpsCounter'] as const
    settings.value.display[keys[cursorIndex.value]] = !settings.value.display[keys[cursorIndex.value]]
  } else if (activeTab.value === 'DISPLAY' && cursorIndex.value === 3) {
    editMode.value = true
  } else if (activeTab.value === 'CONTROLS' && cursorIndex.value === 0) {
    editMode.value = true
  } else if (activeTab.value === 'CONTROLS' && cursorIndex.value === 1) {
    settings.value.controls.swapAB = !settings.value.controls.swapAB
  } else if (activeTab.value === 'CAR' && cursorIndex.value === 0) {
    // Current car picker
  } else if (activeTab.value === 'CAR' && cursorIndex.value === 1) {
    router.push('/calibration') // Changed from /garage/pit-stall to match calibration flow
  } else if (activeTab.value === 'DRIVER' && cursorIndex.value === 1) {
    editMode.value = true
  } else if (activeTab.value === 'DRIVER' && cursorIndex.value === 2) {
    confirmingDestructive.value = true
  }
}

const adjustValue = (dir: number) => {
  if (activeTab.value === 'AUDIO') {
    const keys = ['master', 'music', 'sfx', 'coach'] as const
    const key = keys[cursorIndex.value]
    if (key) {
      settings.value.audio[key] = Math.max(0, Math.min(100, settings.value.audio[key] + (dir * 5)))
      // Debounce sound or just play it
      if (Math.random() > 0.5) audio.playSfx('cursor_move')
    }
  } else if (activeTab.value === 'DISPLAY' && cursorIndex.value === 3) {
    const scales = ['Auto', '1x', '2x', '3x', '4x', '5x']
    const cur = scales.indexOf(settings.value.display.scale)
    settings.value.display.scale = scales[Math.max(0, Math.min(scales.length - 1, cur + dir))]
    audio.playSfx('cursor_move')
  } else if (activeTab.value === 'CONTROLS' && cursorIndex.value === 0) {
    const layouts = ['Arrows', 'WASD', 'IJKL']
    const cur = layouts.indexOf(settings.value.controls.layout)
    settings.value.controls.layout = layouts[Math.max(0, Math.min(layouts.length - 1, cur + dir))]
    audio.playSfx('cursor_move')
  } else if (activeTab.value === 'DRIVER' && cursorIndex.value === 1) {
    const levels = ['BEGINNER', 'INTERMEDIATE', 'PRO']
    const cur = levels.indexOf(settings.value.driver.level)
    settings.value.driver.level = levels[Math.max(0, Math.min(levels.length - 1, cur + dir))]
    audio.playSfx('cursor_move')
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKey)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKey)
})

const renderBar = (val: number) => {
  const filled = Math.round(val / 5)
  return '█'.repeat(filled) + '░'.repeat(20 - filled)
}
</script>

<template>
  <div class="viewport pixelated relative w-full h-full bg-ink text-silver overflow-hidden font-ui flex flex-col">
    <StatusBar />
    
    <div class="settings-bg absolute inset-0 z-0"></div>
    
    <div class="content pt-[6vh] px-[2vw] flex-grow flex flex-col z-10 relative pb-[6vh]">
      <div class="heading-block mb-[1.5vh] text-center">
        <h1 class="text-title font-title text-silver tracking-[0.3em]">SETTINGS</h1>
        <div class="heading-rule"></div>
      </div>

      <!-- Tab Strip -->
      <CyberTabs :tabs="[...tabs]" :modelValue="tabIndex" @update:modelValue="(i) => activeTab = tabs[i]" class="mb-4" />

      <!-- Content -->
      <div class="px-[2vw] text-body flex flex-col gap-[1vh] flex-grow">
        
        <!-- AUDIO -->
        <template v-if="activeTab === 'AUDIO'">
          <div class="flex flex-col gap-1">
            <div v-for="(k, i) in ['MASTER', 'MUSIC', 'SFX', 'COACH VOICE']" :key="k" class="flex items-center">
              <span class="w-[clamp(60px,15vw,120px)]" :class="cursorIndex === i ? 'text-white' : 'text-silver'">
                <span v-if="cursorIndex === i" class="text-ui-good">▶ </span>{{ k }}
              </span>
              <span class="font-mono text-ui-good" :class="editMode && cursorIndex === i ? 'animate-pulse' : ''">
                {{ renderBar(settings.audio[Object.keys(settings.audio)[i] as keyof typeof settings.audio]) }}
              </span>
              <span class="w-[clamp(30px,8vw,60px)] text-right font-mono">{{ settings.audio[Object.keys(settings.audio)[i] as keyof typeof settings.audio] }}%</span>
            </div>
          </div>
          <div class="mt-2 flex flex-col gap-1">
            <div class="flex items-center">
              <span class="w-4" :class="cursorIndex === 4 ? 'text-ui-good' : ''">{{ cursorIndex === 4 ? '▶' : '' }}</span>
              <span class="text-ui-info mr-2">{{ settings.audio.muteAll ? '☑' : '☐' }}</span>
              <span :class="cursorIndex === 4 ? 'text-white' : 'text-silver'">MUTE ALL</span>
            </div>
            <div class="flex items-center">
              <span class="w-4" :class="cursorIndex === 5 ? 'text-ui-good' : ''">{{ cursorIndex === 5 ? '▶' : '' }}</span>
              <span class="text-ui-info mr-2">{{ settings.audio.muteCoach ? '☑' : '☐' }}</span>
              <span :class="cursorIndex === 5 ? 'text-white' : 'text-silver'">MUTE COACH VOICE (silence mode)</span>
            </div>
          </div>
        </template>

        <!-- DISPLAY -->
        <template v-else-if="activeTab === 'DISPLAY'">
          <div class="flex items-center">
            <span class="w-4" :class="cursorIndex === 0 ? 'text-ui-good' : ''">{{ cursorIndex === 0 ? '▶' : '' }}</span>
            <span class="text-ui-info mr-2">{{ settings.display.nightMode ? '☑' : '☐' }}</span>
            <span :class="cursorIndex === 0 ? 'text-white' : 'text-silver'">NIGHT MODE</span>
          </div>
          <div class="flex items-center">
            <span class="w-4" :class="cursorIndex === 1 ? 'text-ui-good' : ''">{{ cursorIndex === 1 ? '▶' : '' }}</span>
            <span class="text-ui-info mr-2">{{ settings.display.reducedMotion ? '☑' : '☐' }}</span>
            <span :class="cursorIndex === 1 ? 'text-white' : 'text-silver'">REDUCED MOTION</span>
          </div>
          <div class="flex items-center">
            <span class="w-4" :class="cursorIndex === 2 ? 'text-ui-good' : ''">{{ cursorIndex === 2 ? '▶' : '' }}</span>
            <span class="text-ui-info mr-2">{{ settings.display.fpsCounter ? '☑' : '☐' }}</span>
            <span :class="cursorIndex === 2 ? 'text-white' : 'text-silver'">SHOW FPS COUNTER</span>
          </div>
          <div class="flex items-center mt-2">
            <span class="w-[clamp(60px,15vw,120px)]" :class="cursorIndex === 3 ? 'text-white' : 'text-silver'">
              <span v-if="cursorIndex === 3" class="text-ui-good">▶ </span>SCALE
            </span>
            <span class="font-bold text-ui-good" :class="editMode && cursorIndex === 3 ? 'animate-pulse' : ''">
              ◀ {{ settings.display.scale }} ▶
            </span>
          </div>
        </template>

        <!-- CONTROLS -->
        <template v-else-if="activeTab === 'CONTROLS'">
          <div class="flex items-center mb-2">
            <span class="w-[clamp(70px,18vw,140px)]" :class="cursorIndex === 0 ? 'text-white' : 'text-silver'">
              <span v-if="cursorIndex === 0" class="text-ui-good">▶ </span>KEYBOARD
            </span>
            <span class="font-bold text-ui-good" :class="editMode && cursorIndex === 0 ? 'animate-pulse' : ''">
              ◀ {{ settings.controls.layout }} ▶
            </span>
          </div>
          <div class="flex items-center">
            <span class="w-4" :class="cursorIndex === 1 ? 'text-ui-good' : ''">{{ cursorIndex === 1 ? '▶' : '' }}</span>
            <span class="text-ui-info mr-2">{{ settings.controls.swapAB ? '☑' : '☐' }}</span>
            <span :class="cursorIndex === 1 ? 'text-white' : 'text-silver'">SWAP A/B (LEFT-HANDED)</span>
          </div>
          <CyberBox :selected="cursorIndex === 2" class="mt-4 p-2 w-max">
            <span v-if="cursorIndex === 2" class="text-ui-good mr-1">▶</span>TEST INPUT
          </CyberBox>
        </template>

        <!-- CAR -->
        <template v-else-if="activeTab === 'CAR'">
          <div class="flex items-center mb-2">
            <span class="w-[clamp(60px,15vw,120px)]" :class="cursorIndex === 0 ? 'text-white' : 'text-silver'">
              <span v-if="cursorIndex === 0" class="text-ui-good">▶ </span>CURRENT CAR
            </span>
            <span class="font-bold text-white">
              {{ settings.car.current }}
            </span>
          </div>
          <CyberBox :selected="cursorIndex === 1" class="mt-2 p-2 mb-1 w-max">
            <span v-if="cursorIndex === 1" class="text-ui-good mr-1">▶</span>RUN HARDWARE TEST
          </CyberBox>
          <br>
          <CyberBox :selected="cursorIndex === 2" class="p-2 w-max">
            <span v-if="cursorIndex === 2" class="text-ui-good mr-1">▶</span>HOW IS MY SCORE CALCULATED?
          </CyberBox>
        </template>

        <!-- DRIVER -->
        <template v-else-if="activeTab === 'DRIVER'">
          <div class="flex items-center mb-2">
            <span class="w-[clamp(60px,15vw,120px)]" :class="cursorIndex === 0 ? 'text-white' : 'text-silver'">
              <span v-if="cursorIndex === 0" class="text-ui-good">▶ </span>NAME
            </span>
            <span class="font-bold text-white">{{ settings.driver.name }}</span>
          </div>
          <div class="flex items-center mb-4">
            <span class="w-[clamp(60px,15vw,120px)]" :class="cursorIndex === 1 ? 'text-white' : 'text-silver'">
              <span v-if="cursorIndex === 1" class="text-ui-good">▶ </span>SKILL TIER
            </span>
            <span class="font-bold text-ui-good" :class="editMode && cursorIndex === 1 ? 'animate-pulse' : ''">
              ◀ {{ settings.driver.level }} ▶
            </span>
          </div>
          
          <CyberBox variant="charcoal" :border="cursorIndex === 2 ? 'none' : 'warn'" :selected="false" class="p-2 w-max" :class="cursorIndex === 2 ? 'bg-ui-warn text-ink font-bold' : 'text-ui-warn'">
            <span v-if="cursorIndex === 2" class="mr-1">▶</span>DELETE SAVE DATA
          </CyberBox>
        </template>

      </div>
      
      <!-- Coach -->
      <DialogueBox 
        v-if="confirmingDestructive"
        :coach-id="save.slots[save.activeSlotId?-1:0]?.preferredCoach ?? 'trod'"
        emotion="concerned"
        text="Whoa, wait. You want to wipe everything? (Y/N)"
        class="absolute bottom-[6vh] left-0 right-0 z-20"
      />
      <DialogueBox 
        v-else
        :coach-id="save.slots[save.activeSlotId?-1:0]?.preferredCoach ?? 'trod'"
        emotion="idle"
        :text="coachQuips[activeTab]"
        class="absolute bottom-[6vh] left-0 right-0 z-10"
      />
    </div>
    
    <HintBar :hints="editMode ? ['◀ ▶ ADJUST', 'A · CONFIRM'] : ['A · SELECT/ADJUST', '◀ ▶ TAB', 'B · GARAGE']" />
  </div>
</template>

<style scoped>
.settings-bg {
  background-color: var(--color-asphalt-deep);
}
.heading-block { text-align: center; }
.heading-rule {
  width: clamp(40px, 12vw, 120px);
  height: 1px;
  /* now uses global .heading-rule with kerb stripe */
  margin: clamp(4px, 1vmin, 10px) auto 0;
}

</style>
