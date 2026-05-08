/**
 * Web Speech API TTS wrapper.
 * Falls back to browser voices if pre-rendered coach mp3s are missing.
 */

export const voice = {
  speak(text: string, rate = 1.0, pitch = 1.0) {
    if (!window.speechSynthesis) return

    // Cancel any ongoing speech
    window.speechSynthesis.cancel()

    const utterance = new SpeechSynthesisUtterance(text)
    utterance.rate = rate
    utterance.pitch = pitch

    // Try to find a good "coaching" voice (e.g., Google UK English Male)
    const voices = window.speechSynthesis.getVoices()
    const preferred = voices.find(v => v.lang === 'en-GB' || v.lang === 'en-US')
    if (preferred) utterance.voice = preferred

    window.speechSynthesis.speak(utterance)
  },

  stop() {
    if (window.speechSynthesis) window.speechSynthesis.cancel()
  }
}
