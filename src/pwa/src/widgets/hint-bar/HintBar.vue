<script setup lang="ts">
defineProps<{ hints: string[] }>()
</script>

<template>
  <div class="hint-bar pixelated">
    <div class="hints-inner">
      <span v-for="(hint, index) in hints" :key="index" class="hint-item">
        <span v-if="index > 0" class="hint-dot">·</span>
        {{ hint }}
      </span>
    </div>
  </div>
</template>

<style scoped>
.hint-bar {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  height: clamp(24px, 4.5vh, 44px);
  background: linear-gradient(180deg, rgba(26, 29, 46, 0.85) 0%, rgba(26, 29, 46, 0.95) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 clamp(10px, 2.5vw, 28px);
  font-family: var(--font-ui);
  font-size: clamp(9px, 2.2vmin, 20px);
  border-top: 1px solid var(--color-slate);
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.3);
  z-index: 10;
  color: var(--color-silver);
}

.hints-inner {
  display: flex;
  align-items: center;
  gap: clamp(6px, 1.5vw, 16px);
  opacity: 0.7;
}

.hint-dot {
  color: var(--color-slate);
  margin: 0 clamp(2px, 0.5vw, 6px);
}

.hint-item {
  letter-spacing: 0.04em;
  white-space: nowrap;
}
</style>
