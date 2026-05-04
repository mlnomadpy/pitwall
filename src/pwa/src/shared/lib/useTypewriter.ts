import { ref, onUnmounted } from 'vue'
import { useAudioStore } from '@/features/audio-playback/model/audioStore'

export function useTypewriter(speedMs: number = 30) {
  const displayedText = ref('')
  const isTyping = ref(false)
  const audio = useAudioStore()
  
  let typeInterval: number | undefined
  let fullText = ''

  const clear = () => {
    if (typeInterval) {
      clearInterval(typeInterval)
      typeInterval = undefined
    }
  }

  const start = (text: string) => {
    clear()
    fullText = text
    displayedText.value = ''
    isTyping.value = true
    
    let i = 0
    typeInterval = window.setInterval(() => {
      if (i < fullText.length) {
        displayedText.value += fullText.charAt(i)
        if (i % 2 === 0) audio.playSfx('dialogue_blip')
        i++
      } else {
        clear()
        isTyping.value = false
      }
    }, speedMs)
  }

  const complete = () => {
    if (isTyping.value) {
      clear()
      displayedText.value = fullText
      isTyping.value = false
    }
  }

  onUnmounted(() => {
    clear()
  })

  return {
    displayedText,
    isTyping,
    start,
    complete
  }
}
