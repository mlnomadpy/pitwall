import { onMounted, onUnmounted, ref } from 'vue'

export function useGamepad(onButtonDown: (btn: number) => void) {
  const isPolling = ref(false)
  let rafId: number | null = null
  const prevState = new Map<number, boolean>()

  const poll = () => {
    const gamepads = navigator.getGamepads()
    for (const gp of gamepads) {
      if (!gp) continue
      
      gp.buttons.forEach((btn, idx) => {
        const isPressed = btn.pressed
        if (isPressed && !prevState.get(idx)) {
          onButtonDown(idx)
        }
        prevState.set(idx, isPressed)
      })
    }
    rafId = requestAnimationFrame(poll)
  }

  const onConnected = () => {
    if (!isPolling.value) {
      isPolling.value = true
      poll()
    }
  }

  onMounted(() => {
    window.addEventListener('gamepadconnected', onConnected)
    if (navigator.getGamepads().some(gp => !!gp)) {
      onConnected()
    }
  })

  onUnmounted(() => {
    window.removeEventListener('gamepadconnected', onConnected)
    if (rafId) cancelAnimationFrame(rafId)
  })
}
