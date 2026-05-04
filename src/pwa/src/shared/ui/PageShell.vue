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
}

withDefaults(defineProps<Props>(), {
  bg: 'default',
  bgVariant: 'grid',
  showHeading: true,
  headingAlign: 'center',
  hideStatus: false
})
</script>

<template>
  <div class="viewport pixelated relative w-full h-full bg-ink text-silver overflow-hidden font-ui">
    <StatusBar v-if="!hideStatus" :extra="statusExtra" />

    <CyberBackground :variant="bgVariant" :color="bg" />

    <div class="shell-content">
      <!-- Heading -->
      <slot name="heading">
        <PageHeading
          v-if="showHeading && title"
          :title="title"
          :subtitle="subtitle"
          :align="headingAlign"
          class="mb-[1.5vh]"
        />
      </slot>

      <!-- Main page content -->
      <slot></slot>

      <!-- Floating content above HintBar (e.g. CoachFloat) -->
      <slot name="floating"></slot>
    </div>

    <HintBar :hints="hints" />
  </div>
</template>

<style scoped>
.shell-content {
  position: relative;
  z-index: 1;
  padding-top: clamp(26px, 6vh, 52px);
  padding-left: clamp(8px, 2vw, 16px);
  padding-right: clamp(8px, 2vw, 16px);
  padding-bottom: clamp(28px, 6vh, 48px);
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: clamp(4px, 1vmin, 8px);
}
</style>
