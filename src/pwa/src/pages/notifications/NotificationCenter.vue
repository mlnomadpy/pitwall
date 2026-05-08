<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useKeyboard } from '@/shared/lib/useKeyboard'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useSaveStore } from '@/entities/save/model/saveStore'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'
import { useNotificationsStore } from '@/shared/api/notificationStore'
import PageShell from '@/shared/ui/PageShell.vue'
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'
import CyberListRow from '@/shared/ui/core/CyberListRow.vue'
import CyberEmptyState from '@/shared/ui/core/CyberEmptyState.vue'

const router = useRouter()
const save = useSaveStore()
const audio = useAudioStore()
const store = useNotificationsStore()
const { bridgeRows, bridgeStreamError, items } = storeToRefs(store)

const cursorIndex = ref(0)

const driverName = computed(() => save.activeSlot?.driverName?.trim() || '*')

const streamUrl = computed(() => store.streamUrlForDriver(driverName.value))

type NavEntry =
  | { kind: 'bridge'; i: number }
  | { kind: 'app'; i: number }

const navList = computed<NavEntry[]>(() => {
  const b = bridgeRows.value.map((_, i) => ({ kind: 'bridge' as const, i }))
  const a = items.value.map((_, i) => ({ kind: 'app' as const, i }))
  return [...b, ...a]
})

watch(navList, (list) => {
  if (cursorIndex.value >= list.length) {
    cursorIndex.value = Math.max(0, list.length - 1)
  }
})

function isBridgeFocused(bridgeIdx: number) {
  const e = navList.value[cursorIndex.value]
  return e?.kind === 'bridge' && e.i === bridgeIdx
}

function isAppFocused(appIdx: number) {
  const e = navList.value[cursorIndex.value]
  return e?.kind === 'app' && e.i === appIdx
}

function focusBridgeRow(bridgeIdx: number) {
  const j = navList.value.findIndex((e) => e.kind === 'bridge' && e.i === bridgeIdx)
  if (j >= 0) cursorIndex.value = j
}

function focusAppRow(appIdx: number) {
  const j = navList.value.findIndex((e) => e.kind === 'app' && e.i === appIdx)
  if (j >= 0) cursorIndex.value = j
}

function restartStream() {
  store.startBridgeStream(driverName.value)
}

onMounted(async () => {
  await save.hydrate()
  restartStream()
  audio.playSfx('cursor_select')
})

watch(driverName, () => {
  restartStream()
})

onBeforeUnmount(() => {
  store.stopBridgeStream()
})

const hasAny = computed(() => navList.value.length > 0)

useKeyboard((e: KeyboardEvent) => {
  if (e.key === 'c' || e.key === 'C') {
    store.clearBridgeLog()
    audio.playSfx('cursor_select')
    return
  }

  if (!hasAny.value) {
    if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
      audio.playSfx('cancel')
      router.back()
    }
    return
  }

  if (e.key === 'ArrowDown') {
    cursorIndex.value = (cursorIndex.value + 1) % navList.value.length
    audio.playSfx('cursor_move')
  } else if (e.key === 'ArrowUp') {
    cursorIndex.value = (cursorIndex.value - 1 + navList.value.length) % navList.value.length
    audio.playSfx('cursor_move')
  } else if (e.key === 'Enter' || e.key === 'a') {
    const entry = navList.value[cursorIndex.value]
    if (entry?.kind === 'app') {
      const item = items.value[entry.i]
      if (item) {
        store.markRead(item.id)
        audio.playSfx('cursor_select')
        if (item.route) router.push(item.route)
      }
    } else {
      audio.playSfx('cursor_select')
    }
  } else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'b') {
    audio.playSfx('cancel')
    router.back()
  } else if (e.key === ' ' || e.key === 'Spacebar') {
    store.markAllRead()
    audio.playSfx('cancel')
  }
})
</script>

<template>
  <PageShell
    :hints="['A · OPEN (APP)', 'B · BACK', '◆ MARK ALL READ', 'C · CLEAR STREAM']"
    bg="neutral"
  >
    <template #heading>
      <div class="heading-block mb-[1.5vh] text-center">
        <h1 class="text-title font-title text-silver tracking-[0.2em]">NOTIFICATIONS</h1>
        <p class="text-body text-slate max-w-[90vw] mx-auto break-all text-small">
          SSE · {{ streamUrl }}
        </p>
        <span
          class="text-body"
          :class="store.unreadCount > 0 ? 'text-ui-warn font-bold' : 'text-slate'"
          >{{ store.unreadCount }} app unread · {{ bridgeRows.length }} bridge events</span
        >
      </div>
    </template>

    <div v-if="bridgeStreamError" class="text-ui-bad text-small mb-2 px-2 text-center">
      {{ bridgeStreamError }}
    </div>

    <div class="flex flex-col gap-[2vmin] flex-grow min-h-0">
      <CyberPanel
        v-if="bridgeRows.length > 0"
        class="flex-1 overflow-y-auto flex flex-col p-2 bg-ink border border-slate/40"
      >
        <div
          v-for="(row, i) in bridgeRows"
          :key="row.id"
          :class="[isBridgeFocused(i) ? 'ring-1 ring-ui-info/60' : '']"
          class="mb-2 rounded border border-slate/30 bg-charcoal/40 p-2 text-left"
          @click="
            focusBridgeRow(i);
            audio.playSfx('cursor_select');
          "
        >
          <div class="flex items-start justify-between gap-2">
            <span class="text-silver text-small font-mono tracking-tight">{{ row.label }}</span>
            <span v-if="isBridgeFocused(i)" class="text-ui-good text-body shrink-0">▶</span>
          </div>
          <pre class="text-[11px] text-slate/90 whitespace-pre-wrap mt-1 font-ui">{{ row.summary }}</pre>
          <details class="mt-2">
            <summary class="text-small text-ui-info cursor-pointer select-none">raw JSON</summary>
            <pre
              class="text-[10px] text-silver mt-1 overflow-x-auto max-h-[28vh] whitespace-pre-wrap font-mono"
              >{{ row.rawJson }}</pre
            >
          </details>
        </div>
      </CyberPanel>

      <CyberPanel
        v-if="items.length > 0"
        class="flex-grow overflow-hidden flex flex-col p-2 bg-ink"
      >
        <div class="text-slate text-small tracking-widest mb-2 px-1">APP NOTIFICATIONS</div>
        <div
          v-for="(item, i) in items"
          :key="item.id"
          :class="[isAppFocused(i) ? 'bg-charcoal' : '', item.isRead ? 'opacity-50' : 'opacity-100']"
          class="px-2 pt-2 -mx-2 transition-opacity cursor-pointer"
          @click="
            focusAppRow(i);
            store.markRead(item.id);
            audio.playSfx('cursor_select');
            if (item.route) router.push(item.route);
          "
        >
          <CyberListRow
            :title="item.title"
            :detail="item.timestamp"
            :status-state="item.kind === 'hardware-warning' ? 'error' : 'none'"
            :sub-lines="[item.subText]"
          >
            <template #icon v-if="isAppFocused(i)">
              <span class="text-ui-good text-body animate-pulse">▶</span>
            </template>
          </CyberListRow>
        </div>
      </CyberPanel>

      <CyberEmptyState
        v-if="bridgeRows.length === 0 && items.length === 0 && !bridgeStreamError"
        icon="📡"
        title="Listening for bridge events"
        description="SSE connected — events appear as the bridge emits them. Start a session or trigger notifications."
        class="flex-grow"
      />
    </div>
  </PageShell>
</template>

<style scoped>
/* No hardcoded viewport dimensions — fullscreen is enforced by global.css */
</style>
