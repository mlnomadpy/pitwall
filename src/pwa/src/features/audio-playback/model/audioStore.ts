import { defineStore } from 'pinia'
import { Howl, Howler } from 'howler'

export const useAudioStore = defineStore('audio', {
  state: () => ({
    sfx: new Map<string, Howl>(),
    music: new Map<string, Howl>(),
    voice: new Map<string, Howl>(),
    ttsDucked: false,
    _duckUntil: 0
  }),
  actions: {
    playSfx(id: string) {
      let h = this.sfx.get(id)
      if (!h) {
        // We use dummy URLs; Howler will fail silently if the mp3 is missing
        h = new Howl({ src: [`/sfx/${id}.mp3`], volume: 0.3 })
        this.sfx.set(id, h)
      }
      h.play()
    },
    
    playMusic(track: string) {
      let h = this.music.get(track)
      if (!h) {
        h = new Howl({ src: [`/music/${track}.mp3`], loop: true, volume: 0.15 })
        this.music.set(track, h)
      }
      
      this.music.forEach((m, k) => {
        if (k !== track && m.playing()) {
          m.fade(m.volume(), 0, 500)
          setTimeout(() => m.pause(), 500)
        }
      })
      
      if (!h.playing()) {
        h.play()
        h.fade(0, 0.15, 500)
      }
    },
    
    playVoice(coachId: string, phraseId: string, hintMs = 0) {
      const key = `${coachId}/${phraseId}`
      let h = this.voice.get(key)
      if (!h) {
        h = new Howl({ src: [`/audio/coaches/${key}.mp3`], volume: 1.0 })
        this.voice.set(key, h)
      }
      
      this.duckMusic(true)
      const durationMs = hintMs || (h.duration() ? h.duration() * 1000 : 1500)
      this.duckTactical(true, durationMs)
      
      h.once('end', () => {
        this.duckMusic(false)
      })
      
      // Stop all other voices
      this.voice.forEach((v) => v.stop())
      h.play()
    },
    
    duckMusic(ducked: boolean) {
      this.music.forEach(m => {
        if (m.playing()) {
          m.fade(m.volume(), ducked ? 0.05 : 0.15, 200)
        }
      })
    },
    
    duckTactical(ducked: boolean, holdMs = 0) {
      if (ducked) {
        this._duckUntil = Math.max(this._duckUntil, performance.now() + holdMs)
        this.ttsDucked = true
        // For actual implementation, this adjusts Web Audio API gain nodes
        setTimeout(() => this._maybeUnduck(), holdMs + 16)
      }
    },
    
    _maybeUnduck() {
      if (performance.now() >= this._duckUntil - 8) {
        this.ttsDucked = false
      }
    }
  }
})
