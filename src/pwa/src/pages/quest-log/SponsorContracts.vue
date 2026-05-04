<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import PageShell from '@/shared/ui/PageShell.vue'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'
import CyberSplitView from '@/shared/ui/core/CyberSplitView.vue'
import CyberButton from '@/shared/ui/core/CyberButton.vue'

const router = useRouter()
const audio = useAudioStore()

const sponsors = ref([
  { 
    id: 's1', 
    name: 'NEO-DYNAMICS', 
    type: 'AERO CORP', 
    desc: 'Focuses on smooth steering and high minimum corner speeds.',
    goal: 'Complete 3 laps at Sonoma without exceeding 1.2G lateral.',
    reward: '250 CR · Aero Part LV2',
    active: true
  },
  { 
    id: 's2', 
    name: 'SYNTHO-TIRE', 
    type: 'RUBBER SYNDICATE', 
    desc: 'Aggressive braking and tire management.',
    goal: 'Zero lockups in Turn 7 and 11 over a 5 lap stint.',
    reward: '500 CR · Soft Compound',
    active: false
  },
  { 
    id: 's3', 
    name: 'OMNI-FUEL', 
    type: 'ENERGY CONGLOMERATE', 
    desc: 'Efficiency and throttle application.',
    goal: 'Maintain >80% throttle application smoothness score.',
    reward: '150 CR',
    active: false
  }
])

const cursorIndex = ref(0)
const selectedSponsor = ref(sponsors.value[0])

useKeyboard((e: KeyboardEvent) => {
  if (e.key === 'ArrowDown') {
    cursorIndex.value = (cursorIndex.value + 1) % sponsors.value.length
    selectedSponsor.value = sponsors.value[cursorIndex.value]
    audio.playSfx('cursor_move')
  } else if (e.key === 'ArrowUp') {
    cursorIndex.value = (cursorIndex.value - 1 + sponsors.value.length) % sponsors.value.length
    selectedSponsor.value = sponsors.value[cursorIndex.value]
    audio.playSfx('cursor_move')
  } else if (e.key === 'Enter' || e.key === 'a') {
    audio.playSfx('cursor_select')
    sponsors.value.forEach((s, i) => {
      s.active = i === cursorIndex.value
    })
  } else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.back()
  }
})
</script>

<template>
  <PageShell title="CONTRACTS" :hints="['A · SIGN CONTRACT', 'B · BACK', '▲ ▼ BROWSE']" bg="neutral">
    
    <template #heading>
      <div class="heading-block mb-[1.5vh]">
        <h1 class="text-title font-title text-[#eab308] tracking-[0.2em] drop-shadow-[2px_2px_0_#ca8a04]">SPONSORS</h1>
        <div class="heading-rule bg-[#eab308]"></div>
      </div>
    </template>

    <CyberSplitView split="40-60" class="flex-grow mx-2 pb-6 min-h-0">
      
      <template #left>
        <CyberPanel class="flex flex-col h-full overflow-hidden p-0 bg-ink min-h-0">
          <div class="bg-[#ca8a04] px-3 py-2 border-b border-slate font-bold text-ink text-small sticky top-0 z-10 flex justify-between tracking-widest">
            <span>CORPORATION</span>
            <span>STATUS</span>
          </div>
          
          <div class="flex-grow overflow-y-auto min-h-0 p-2">
            <div 
              v-for="(s, i) in sponsors" 
              :key="s.id"
              class="mb-2"
            >
              <div 
                class="flex items-center px-2 py-3 border transition-colors cursor-pointer"
                :class="cursorIndex === i ? 'bg-charcoal border-[#eab308] text-white shadow-[0_0_10px_rgba(234,179,8,0.2)]' : 'border-transparent text-silver'"
                @click="cursorIndex = i; selectedSponsor = s"
              >
                <span class="w-6 shrink-0 font-bold text-[#eab308]" v-if="cursorIndex === i">▶</span>
                <span class="w-6 shrink-0" v-else></span>
                
                <div class="flex-grow flex flex-col">
                  <span class="font-bold tracking-widest">{{ s.name }}</span>
                  <span class="text-small text-slate font-mono">{{ s.type }}</span>
                </div>
                
                <span v-if="s.active" class="text-ink text-small px-2 py-1 bg-[#eab308] font-bold">SIGNED</span>
              </div>
            </div>
          </div>
        </CyberPanel>
      </template>

      <template #right>
        <CyberPanel class="flex flex-col h-full bg-ink border-slate p-4 relative overflow-hidden group">
          <!-- Background corporate logo placeholder -->
          <div class="absolute inset-0 opacity-[0.03] pointer-events-none flex items-center justify-center font-title text-[150px] leading-none whitespace-nowrap -rotate-12 select-none">
            {{ selectedSponsor.name }}
          </div>

          <div class="text-silver font-bold uppercase mb-4 border-b border-slate pb-2 tracking-widest z-10">CONTRACT DETAILS</div>
          
          <div class="flex flex-col gap-6 font-mono text-body mt-2 z-10">
            <div class="flex gap-4 items-start">
              <div class="w-16 h-16 bg-charcoal border border-slate flex items-center justify-center text-title font-bold text-slate shrink-0">
                {{ selectedSponsor.name.substring(0, 1) }}
              </div>
              <div class="flex flex-col">
                <span class="text-white font-bold text-title-sm tracking-widest">{{ selectedSponsor.name }}</span>
                <span class="text-slate italic">"{{ selectedSponsor.desc }}"</span>
              </div>
            </div>

            <div class="flex flex-col bg-charcoal/50 p-3 border border-slate/50">
              <span class="text-[#eab308] text-small mb-1 font-bold tracking-widest">CURRENT DIRECTIVE</span>
              <span class="font-bold text-white leading-relaxed">{{ selectedSponsor.goal }}</span>
            </div>
            
            <div class="flex flex-col">
              <span class="text-slate text-small mb-1 font-bold tracking-widest">COMPLETION REWARD</span>
              <span class="font-bold text-title-sm text-ui-good">{{ selectedSponsor.reward }}</span>
            </div>
          </div>

          <div class="mt-auto pt-4 border-t border-slate text-center z-10">
            <CyberButton 
              :variant="selectedSponsor.active ? 'secondary' : 'primary'"
              fluid
              @click="sponsors.forEach(s => s.active = s.id === selectedSponsor.id)"
            >
              {{ selectedSponsor.active ? 'CONTRACT ACTIVE' : 'SIGN CONTRACT' }}
            </CyberButton>
          </div>
        </CyberPanel>
      </template>

    </CyberSplitView>
  </PageShell>
</template>
