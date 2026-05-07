<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import { useRouter } from 'vue-router'
import { useSaveStore } from '@/entities/save/model/saveStore'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import PageShell from '@/shared/ui/PageShell.vue'

import CyberTabs from '@/shared/ui/core/CyberTabs.vue'
import CyberBox from '@/shared/ui/core/CyberBox.vue'
import CoachFloat from '@/shared/ui/CoachFloat.vue'
import CyberCheckbox from '@/shared/ui/core/CyberCheckbox.vue'
import CyberValuePicker from '@/shared/ui/core/CyberValuePicker.vue'
import CyberProgressBar from '@/shared/ui/core/CyberProgressBar.vue'
import CyberConfirmDialog from '@/shared/ui/core/CyberConfirmDialog.vue'
import { useSwipeGesture } from '@/shared/lib/useSwipeGesture'

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

// Initialize settings from save store (or sensible defaults)
const slot = save.activeSlot
const savedSettings = slot?.settings

const settings = ref({
  audio: {
    master: savedSettings?.audio.masterVolume ?? 80,
    music: savedSettings?.audio.musicVolume ?? 50,
    sfx: savedSettings?.audio.sfxVolume ?? 100,
    coach: savedSettings?.audio.voiceVolume ?? 100,
    muteAll: false,
    muteCoach: savedSettings?.audio.coachMute ?? false
  },
  display: {
    nightMode: savedSettings?.display.nightMode ?? false,
    reducedMotion: savedSettings?.display.reducedMotion ?? false,
    fpsCounter: savedSettings?.display.showFps ?? false,
    scale: 'Auto'
  },
  controls: {
    layout: (savedSettings?.controls.keyboardLayout === 'wasd' ? 'WASD'
           : savedSettings?.controls.keyboardLayout === 'igdk' ? 'IJKL' 
           : 'Arrows'),
    swapAB: savedSettings?.controls.swapAB ?? false
  },
  car: {
    current: slot?.car ?? 'BMW M3 (E46)'
  },
  driver: {
    name: slot?.driverName ?? 'DRIVER',
    level: (slot?.skillLevel?.toUpperCase() ?? 'PRO')
  }
})

// Sync settings changes back to save store with debounce
let syncTimeout: number | null = null
watch(settings, () => {
  if (!save.activeSlotId) return
  const s = save.slots[save.activeSlotId - 1]
  if (!s) return
  
  const layoutMap: Record<string, 'arrows' | 'wasd' | 'igdk'> = { 'Arrows': 'arrows', 'WASD': 'wasd', 'IJKL': 'igdk' }
  
  s.settings = {
    audio: {
      masterVolume: settings.value.audio.master,
      musicVolume: settings.value.audio.music,
      sfxVolume: settings.value.audio.sfx,
      voiceVolume: settings.value.audio.coach,
      coachMute: settings.value.audio.muteCoach,
    },
    display: {
      nightMode: settings.value.display.nightMode,
      reducedMotion: settings.value.display.reducedMotion,
      showFps: settings.value.display.fpsCounter,
    },
    controls: {
      keyboardLayout: layoutMap[settings.value.controls.layout] ?? 'arrows',
      swapAB: settings.value.controls.swapAB,
    }
  }
  
  // Debounce the IDB write (don't spam disk on slider drags)
  if (syncTimeout) clearTimeout(syncTimeout)
  syncTimeout = window.setTimeout(() => save.save(), 500)
}, { deep: true })

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

useKeyboard((e: KeyboardEvent) => {
  if (confirmingDestructive.value) {
    if (e.key === 'y' || e.key === 'Y' || e.key === 'Enter') {
      audio.playSfx('cancel') // heavy
      if (save.activeSlotId) {
        save.deleteSlot(save.activeSlotId)
      }
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
})

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

useSwipeGesture(null, {
  onSwipeLeft: () => {
    if (!editMode.value) {
      activeTab.value = tabs[(tabIndex.value + 1) % tabs.length]
      audio.playSfx('cursor_move')
    }
  },
  onSwipeRight: () => {
    if (!editMode.value) {
      activeTab.value = tabs[(tabIndex.value - 1 + tabs.length) % tabs.length]
      audio.playSfx('cursor_move')
    }
  }
})




</script>

<template>
  <PageShell title="SETTINGS" :hints="editMode ? ['◀ ▶ ADJUST', 'A · CONFIRM'] : ['A · SELECT/ADJUST', '◀ ▶ TAB', 'B · GARAGE']" bg="neutral">
    <template #heading>
      <div class="heading-block mb-[1.5vh] text-center">
        <h1 class="text-title font-title text-silver tracking-[0.3em]">SETTINGS</h1>
        <div class="heading-rule"></div>
      </div>
    </template>

    <!-- Tab Strip -->
    <CyberTabs :tabs="[...tabs]" :modelValue="tabIndex" @update:modelValue="(i) => activeTab = tabs[i]" class="mb-4 mx-2" />

    <!-- Content -->
    <div class="px-[2vw] text-body flex flex-col gap-[1vh] flex-grow">
      
      <!-- AUDIO -->
      <template v-if="activeTab === 'AUDIO'">
        <div class="flex flex-col gap-1">
          <CyberProgressBar 
            v-for="(k, i) in ['MASTER', 'MUSIC', 'SFX', 'COACH VOICE']" :key="k"
            :label="k"
            :value="settings.audio[Object.keys(settings.audio)[i] as keyof typeof settings.audio] as number"
            :focused="cursorIndex === i"
            :editing="editMode"
            @click="cursorIndex = i; editMode = true"
          />
        </div>
        <div class="mt-2 flex flex-col gap-1">
          <CyberCheckbox 
            label="MUTE ALL" 
            :checked="settings.audio.muteAll" 
            :focused="cursorIndex === 4" 
            @change="(v: boolean) => { cursorIndex = 4; settings.audio.muteAll = v; audio.playSfx('cursor_select') }"
          />
          <CyberCheckbox 
            label="MUTE COACH VOICE" 
            sub-label="(silence mode)"
            :checked="settings.audio.muteCoach" 
            :focused="cursorIndex === 5" 
            @change="(v: boolean) => { cursorIndex = 5; settings.audio.muteCoach = v; audio.playSfx('cursor_select') }"
          />
        </div>
      </template>

      <!-- DISPLAY -->
      <template v-else-if="activeTab === 'DISPLAY'">
        <CyberCheckbox label="NIGHT MODE" :checked="settings.display.nightMode" :focused="cursorIndex === 0" @change="(v: boolean) => { cursorIndex = 0; settings.display.nightMode = v; audio.playSfx('cursor_select') }" />
        <CyberCheckbox label="REDUCED MOTION" :checked="settings.display.reducedMotion" :focused="cursorIndex === 1" @change="(v: boolean) => { cursorIndex = 1; settings.display.reducedMotion = v; audio.playSfx('cursor_select') }" />
        <CyberCheckbox label="SHOW FPS COUNTER" :checked="settings.display.fpsCounter" :focused="cursorIndex === 2" @change="(v: boolean) => { cursorIndex = 2; settings.display.fpsCounter = v; audio.playSfx('cursor_select') }" />
        <div class="mt-2">
          <CyberValuePicker 
            label="SCALE" 
            :value="settings.display.scale" 
            :focused="cursorIndex === 3" 
            :editing="editMode" 
            @click="cursorIndex = 3; editMode = true"
          />
        </div>
      </template>

      <!-- CONTROLS -->
      <template v-else-if="activeTab === 'CONTROLS'">
        <CyberValuePicker 
          label="KEYBOARD" 
          :value="settings.controls.layout" 
          :focused="cursorIndex === 0" 
          :editing="editMode"
          label-width="clamp(70px,18vw,140px)"
          @click="cursorIndex = 0; editMode = true"
        />
        <CyberCheckbox 
          label="SWAP A/B (LEFT-HANDED)" 
          :checked="settings.controls.swapAB" 
          :focused="cursorIndex === 1" 
          @change="(v: boolean) => { cursorIndex = 1; settings.controls.swapAB = v; audio.playSfx('cursor_select') }"
        />
        <CyberBox :selected="cursorIndex === 2" interactive class="mt-4 p-2 w-max" @click="cursorIndex = 2; handleSelect()">
          <span v-if="cursorIndex === 2" class="text-ui-good mr-1">▶</span>TEST INPUT
        </CyberBox>
      </template>

      <!-- CAR -->
      <template v-else-if="activeTab === 'CAR'">
        <div class="flex items-center mb-2 cursor-pointer" @click="cursorIndex = 0; handleSelect()">
          <span class="w-[clamp(60px,15vw,120px)]" :class="cursorIndex === 0 ? 'text-white' : 'text-silver'">
            <span v-if="cursorIndex === 0" class="text-ui-good">▶ </span>CURRENT CAR
          </span>
          <span class="font-bold text-white">
            {{ settings.car.current }}
          </span>
        </div>
        <CyberBox :selected="cursorIndex === 1" interactive class="mt-2 p-2 mb-1 w-max" @click="cursorIndex = 1; handleSelect()">
          <span v-if="cursorIndex === 1" class="text-ui-good mr-1">▶</span>RUN HARDWARE TEST
        </CyberBox>
        <br>
        <CyberBox :selected="cursorIndex === 2" interactive class="p-2 w-max" @click="cursorIndex = 2; handleSelect()">
          <span v-if="cursorIndex === 2" class="text-ui-good mr-1">▶</span>HOW IS MY SCORE CALCULATED?
        </CyberBox>
      </template>

      <!-- DRIVER -->
      <template v-else-if="activeTab === 'DRIVER'">
        <div class="flex items-center mb-2 cursor-pointer" @click="cursorIndex = 0; handleSelect()">
          <span class="w-[clamp(60px,15vw,120px)]" :class="cursorIndex === 0 ? 'text-white' : 'text-silver'">
            <span v-if="cursorIndex === 0" class="text-ui-good">▶ </span>NAME
          </span>
          <span class="font-bold text-white">{{ settings.driver.name }}</span>
        </div>
        <div class="mb-4">
          <CyberValuePicker 
            label="SKILL TIER" 
            :value="settings.driver.level" 
            :focused="cursorIndex === 1" 
            :editing="editMode" 
            @click="cursorIndex = 1; editMode = true"
          />
        </div>
        
        <CyberBox variant="charcoal" :border="cursorIndex === 2 ? 'none' : 'warn'" :selected="false" interactive class="p-2 w-max" :class="cursorIndex === 2 ? 'bg-ui-warn text-ink font-bold' : 'text-ui-warn'" @click="cursorIndex = 2; handleSelect()">
          <span v-if="cursorIndex === 2" class="mr-1">▶</span>DELETE SAVE DATA
        </CyberBox>
      </template>

    </div>
    
    <template #floating>
      <CoachFloat
        v-if="confirmingDestructive"
        emotion="concerned"
        text="Whoa, wait. You want to wipe everything? (Y/N)"
      />
      <CoachFloat
        v-else
        emotion="idle"
        :text="coachQuips[activeTab]"
      />
    </template>
  </PageShell>

  <CyberConfirmDialog
    :open="confirmingDestructive"
    title="DELETE SAVE"
    message="This will permanently erase all your progress. Are you sure?"
    confirmLabel="DELETE"
    cancelLabel="KEEP"
    variant="danger"
    @confirm="audio.playSfx('cancel'); save.activeSlotId && save.deleteSlot(save.activeSlotId); router.push('/')"
    @cancel="confirmingDestructive = false; audio.playSfx('cursor_move')"
  />
</template>

<style scoped>
.heading-block { text-align: center; }
</style>
