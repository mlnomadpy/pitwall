<script setup lang="ts">
import StatusBar from '@/widgets/status-bar/StatusBar.vue'
import HintBar from '@/widgets/hint-bar/HintBar.vue'
import PageHeading from '@/shared/ui/PageHeading.vue'
import CyberBackground from '@/shared/ui/core/CyberBackground.vue'

interface Props {
  title?: string
  subtitle?: string
  hints: string[]
  bg?: 'default' | 'warm' | 'cool' | 'danger' | 'neutral'
  bgVariant?: 'grid' | 'landscape' | 'stars'
  showHeading?: boolean
  headingAlign?: 'center' | 'left'
  statusExtra?: string
  hideStatus?: boolean
  performanceMode?: boolean
}

withDefaults(defineProps<Props>(), {
  bg: 'default',
  bgVariant: 'grid',
  showHeading: true,
  headingAlign: 'center',
  hideStatus: false,
  performanceMode: false
})
</script>

<template>
  <div class="viewport pixelated relative w-full h-full bg-ink text-silver overflow-hidden font-ui" role="application" :aria-label="title ?? 'Pitwall'">
    <StatusBar v-if="!hideStatus" :extra="statusExtra" />

    <CyberBackground v-if="!performanceMode" :variant="bgVariant" :color="bg" />

    <div class="shell-content">
      <!-- Heading -->
      <div class="stagger-1">
        <slot name="heading">
          <PageHeading
            v-if="showHeading && title"
            :title="title"
            :subtitle="subtitle"
            :align="headingAlign"
            class="mb-[1.5vh]"
          />
        </slot>
      </div>

      <!-- Main page content -->
      <div class="stagger-2 flex-grow min-h-0 flex flex-col" role="main">
        <slot></slot>
      </div>

      <!-- Floating content above HintBar (e.g. CoachFloat) -->
      <div class="stagger-3" aria-live="polite">
        <slot name="floating"></slot>
      </div>
    </div>

    <HintBar :hints="hints" />
  </div>
</template>

<style scoped>
.shell-content {
  position: relative;
  z-index: 1;
  padding-top: clamp(36px, 7vh, 56px); /* Must clear StatusBar: clamp(32px, 6vh, 52px) + gap */
  padding-left: clamp(8px, 2vw, 16px);
  padding-right: clamp(8px, 2vw, 16px);
  padding-bottom: clamp(36px, 7vh, 56px); /* Must clear HintBar: clamp(28px, 5.5vh, 48px) + gap */
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: clamp(4px, 1vmin, 8px);
}
</style>
