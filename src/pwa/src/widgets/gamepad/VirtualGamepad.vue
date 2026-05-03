<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'

const isMobile = ref(false)

const checkMobile = () => {
  // Simple check for touch devices
  isMobile.value = ('ontouchstart' in window) || (navigator.maxTouchPoints > 0)
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
})

const dispatchMappedKey = (logicalKey: string, type: 'keydown' | 'keyup') => {
  const event = new KeyboardEvent(type, {
    key: logicalKey,
    code: logicalKey === 'Enter' ? 'Enter' : logicalKey === 'Escape' ? 'Escape' : logicalKey === 'Shift' ? 'ShiftLeft' : logicalKey,
    keyCode: logicalKey === 'Enter' ? 13 : logicalKey === 'Escape' ? 27 : logicalKey === 'ArrowUp' ? 38 : logicalKey === 'ArrowDown' ? 40 : logicalKey === 'ArrowLeft' ? 37 : logicalKey === 'ArrowRight' ? 39 : logicalKey === 'Shift' ? 16 : 0,
    bubbles: true,
    cancelable: true
  })
  window.dispatchEvent(event)
}

const activeKeys = ref<Record<string, boolean>>({})

const onTouchStart = (e: TouchEvent, logicalKey: string) => {
  e.preventDefault()
  if (!activeKeys.value[logicalKey]) {
    activeKeys.value[logicalKey] = true
    dispatchMappedKey(logicalKey, 'keydown')
  }
}

const onTouchEnd = (e: TouchEvent, logicalKey: string) => {
  e.preventDefault()
  if (activeKeys.value[logicalKey]) {
    activeKeys.value[logicalKey] = false
    dispatchMappedKey(logicalKey, 'keyup')
  }
}

const onTouchCancel = (e: TouchEvent, logicalKey: string) => {
  e.preventDefault()
  if (activeKeys.value[logicalKey]) {
    activeKeys.value[logicalKey] = false
    dispatchMappedKey(logicalKey, 'keyup')
  }
}
</script>

<template>
  <div v-if="isMobile" class="virtual-gamepad">
    <!-- Left Side: D-Pad -->
    <div class="dpad-container pointer-events-auto">
      <div class="dpad">
        <!-- Up -->
        <div 
          class="dpad-btn dpad-up"
          :class="{ pressed: activeKeys['ArrowUp'] }"
          @touchstart="onTouchStart($event, 'ArrowUp')"
          @touchend="onTouchEnd($event, 'ArrowUp')"
          @touchcancel="onTouchCancel($event, 'ArrowUp')"
        >
          <span class="dpad-arrow" style="transform: rotate(-90deg)">▶</span>
        </div>
        <!-- Down -->
        <div 
          class="dpad-btn dpad-down"
          :class="{ pressed: activeKeys['ArrowDown'] }"
          @touchstart="onTouchStart($event, 'ArrowDown')"
          @touchend="onTouchEnd($event, 'ArrowDown')"
          @touchcancel="onTouchCancel($event, 'ArrowDown')"
        >
          <span class="dpad-arrow" style="transform: rotate(90deg)">▶</span>
        </div>
        <!-- Left -->
        <div 
          class="dpad-btn dpad-left"
          :class="{ pressed: activeKeys['ArrowLeft'] }"
          @touchstart="onTouchStart($event, 'ArrowLeft')"
          @touchend="onTouchEnd($event, 'ArrowLeft')"
          @touchcancel="onTouchCancel($event, 'ArrowLeft')"
        >
          <span class="dpad-arrow" style="transform: rotate(180deg)">▶</span>
        </div>
        <!-- Right -->
        <div 
          class="dpad-btn dpad-right"
          :class="{ pressed: activeKeys['ArrowRight'] }"
          @touchstart="onTouchStart($event, 'ArrowRight')"
          @touchend="onTouchEnd($event, 'ArrowRight')"
          @touchcancel="onTouchCancel($event, 'ArrowRight')"
        >
          <span class="dpad-arrow">▶</span>
        </div>
        <!-- Center hub -->
        <div class="dpad-center"></div>
      </div>
    </div>

    <!-- Right Side: A / B Buttons -->
    <div class="ab-container pointer-events-auto">
      <!-- B Button -->
      <div 
        class="gba-btn btn-b"
        :class="{ pressed: activeKeys['b'] }"
        @touchstart="onTouchStart($event, 'b')"
        @touchend="onTouchEnd($event, 'b')"
        @touchcancel="onTouchCancel($event, 'b')"
      >
        <span class="btn-label">B</span>
      </div>
      
      <!-- A Button -->
      <div 
        class="gba-btn btn-a"
        :class="{ pressed: activeKeys['Enter'] }"
        @touchstart="onTouchStart($event, 'Enter')"
        @touchend="onTouchEnd($event, 'Enter')"
        @touchcancel="onTouchCancel($event, 'Enter')"
      >
        <span class="btn-label">A</span>
      </div>
    </div>

    <!-- Bottom Center: Select / Start -->
    <div class="select-start-container pointer-events-auto">
      <div class="meta-btn-group">
        <div 
          class="meta-btn"
          :class="{ pressed: activeKeys['Shift'] }"
          @touchstart="onTouchStart($event, 'Shift')"
          @touchend="onTouchEnd($event, 'Shift')"
          @touchcancel="onTouchCancel($event, 'Shift')"
        ></div>
        <span class="meta-label">SELECT</span>
      </div>
      
      <div class="meta-btn-group">
        <div 
          class="meta-btn"
          :class="{ pressed: activeKeys['Escape'] }"
          @touchstart="onTouchStart($event, 'Escape')"
          @touchend="onTouchEnd($event, 'Escape')"
          @touchcancel="onTouchCancel($event, 'Escape')"
        ></div>
        <span class="meta-label">START</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.virtual-gamepad {
  -webkit-user-select: none;
  user-select: none;
  -webkit-touch-callout: none;
  position: fixed;
  inset: 0;
  z-index: 9000;
  pointer-events: none;
  overflow: hidden;
}

/* ═══════════════════════════════
 * D-PAD — Chunky pixel-art cross
 * ═══════════════════════════════ */
.dpad-container {
  position: absolute;
  bottom: 4vh;
  left: 3vw;
  width: clamp(100px, 20vmin, 180px);
  height: clamp(100px, 20vmin, 180px);
}

.dpad {
  position: relative;
  width: 100%;
  height: 100%;
}

.dpad-btn {
  position: absolute;
  width: 34%;
  height: 34%;
  background-color: var(--color-charcoal);
  border: 2px solid #0d0d12;
  display: flex;
  align-items: center;
  justify-content: center;
  /* Pixel 3D bevel */
  box-shadow:
    inset 1px 1px 0 0 var(--color-slate),
    inset -1px -1px 0 0 var(--color-ink);
}

.dpad-btn.pressed {
  background-color: var(--color-asphalt-deep);
  box-shadow:
    inset -1px -1px 0 0 var(--color-slate),
    inset 1px 1px 0 0 var(--color-ink);
}

.dpad-up    { top: 0;    left: 33%; }
.dpad-down  { bottom: 0; left: 33%; }
.dpad-left  { top: 33%;  left: 0; }
.dpad-right { top: 33%;  right: 0; }

.dpad-center {
  position: absolute;
  top: 33%;
  left: 33%;
  width: 34%;
  height: 34%;
  background-color: var(--color-charcoal);
  border: 2px solid #0d0d12;
}

.dpad-arrow {
  color: var(--color-silver);
  font-family: var(--font-ui);
  font-size: clamp(12px, 2.8vmin, 22px);
  font-weight: bold;
  text-shadow: 1px 1px 0 #0d0d12;
  line-height: 1;
}

/* ═══════════════════════════════
 * A / B BUTTONS — GBA pill shape
 * ═══════════════════════════════ */
.ab-container {
  position: absolute;
  bottom: 6vh;
  right: 5vw;
  width: clamp(120px, 22vmin, 200px);
  height: clamp(90px, 16vmin, 150px);
}

.gba-btn {
  position: absolute;
  width: clamp(44px, 9vmin, 72px);
  height: clamp(44px, 9vmin, 72px);
  border: 3px solid #0d0d12;
  display: flex;
  align-items: center;
  justify-content: center;
  image-rendering: pixelated;
}

/* A button — curb red (confirm) */
.btn-a {
  top: 0;
  right: 0;
  background-color: #c93838;
  /* 3D pixel bevel: lighter top-left, darker bottom-right */
  box-shadow:
    inset 2px 2px 0 0 #e85858,
    inset -2px -2px 0 0 #8a2828,
    3px 3px 0 0 #0d0d12;
}

.btn-a.pressed {
  box-shadow:
    inset -2px -2px 0 0 #e85858,
    inset 2px 2px 0 0 #8a2828;
  transform: translate(3px, 3px);
}

/* B button — slate (cancel) */
.btn-b {
  bottom: 0;
  left: 0;
  background-color: var(--color-slate);
  box-shadow:
    inset 2px 2px 0 0 var(--color-silver),
    inset -2px -2px 0 0 var(--color-charcoal),
    3px 3px 0 0 #0d0d12;
}

.btn-b.pressed {
  box-shadow:
    inset -2px -2px 0 0 var(--color-silver),
    inset 2px 2px 0 0 var(--color-charcoal);
  transform: translate(3px, 3px);
}

.btn-label {
  color: #f5f5e8;
  font-family: var(--font-title);
  font-size: clamp(14px, 3.5vmin, 28px);
  font-weight: bold;
  text-shadow: 1px 1px 0 #0d0d12;
  line-height: 1;
}

/* ═══════════════════════════════
 * SELECT / START — Small pills
 * ═══════════════════════════════ */
.select-start-container {
  position: absolute;
  bottom: 2vh;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: clamp(24px, 8vw, 60px);
}

.meta-btn-group {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: clamp(2px, 0.4vh, 5px);
}

.meta-btn {
  width: clamp(40px, 8vw, 70px);
  height: clamp(10px, 1.8vh, 18px);
  background-color: var(--color-slate);
  border: 2px solid #0d0d12;
  transform: skewX(-8deg);
  box-shadow:
    inset 1px 1px 0 0 var(--color-silver),
    inset -1px -1px 0 0 var(--color-charcoal),
    2px 2px 0 0 #0d0d12;
}

.meta-btn.pressed {
  background-color: var(--color-charcoal);
  box-shadow:
    inset -1px -1px 0 0 var(--color-silver),
    inset 1px 1px 0 0 var(--color-charcoal);
  transform: skewX(-8deg) translate(2px, 2px);
}

.meta-label {
  font-family: var(--font-ui);
  font-size: clamp(8px, 1.8vmin, 14px);
  color: var(--color-silver);
  font-weight: bold;
  letter-spacing: 0.2em;
  text-shadow: 1px 1px 0 #0d0d12;
}
</style>
