import { onMounted, onUnmounted } from 'vue'

/**
 * Composable that registers a keydown listener on mount and cleans it up on unmount.
 * Replaces the boilerplate `onMounted(() => window.addEventListener('keydown', handler))`
 * pattern used across 28+ pages.
 *
 * @param handler - The keyboard event handler function
 * @param options - Optional addEventListener options (e.g. { capture: true })
 */
export function useKeyboard(
  handler: (e: KeyboardEvent) => void,
  options?: AddEventListenerOptions
) {
  onMounted(() => {
    window.addEventListener('keydown', handler, options)
  })

  onUnmounted(() => {
    window.removeEventListener('keydown', handler, options)
  })
}
