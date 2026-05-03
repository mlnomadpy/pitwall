import { defineStore } from 'pinia'

// Map known sheet IDs to their subdirectories in /sprites/
const SHEET_DIRS: Record<string, string> = {
  trod: 'coaches', bentley: 'coaches', drill: 'coaches', calm: 'coaches', buddy: 'coaches',
  avatars: 'drivers',
  frames: 'ui', medals: 'ui',
  'sonoma-map': 'tracks', 'sonoma-corners': 'tracks', 'sonoma-world-pin': 'tracks', 'sonoma-bg': 'tracks',
  wipes: 'effects', confetti: 'effects', dust: 'effects'
}

interface SpriteData {
  frames: Record<string, {
    frame: { x: number, y: number, w: number, h: number },
    spriteSourceSize: { x: number, y: number, w: number, h: number },
    sourceSize: { w: number, h: number }
  }>
  animations: Record<string, {
    frames: string[],
    fps: number,
    loop: boolean
  }>
  meta: { image: string }
}

export const useSpriteStore = defineStore('sprites', {
  state: () => ({
    loadedSheets: new Set<string>(),
    sheetData: {} as Record<string, SpriteData>,
    injectedStyles: null as HTMLStyleElement | null
  }),
  actions: {
    async loadSheet(sheet: string) {
      if (this.loadedSheets.has(sheet)) return
      
      const dir = SHEET_DIRS[sheet] || 'ui'
      try {
        const response = await fetch(`/sprites/${dir}/${sheet}.json`)
        if (!response.ok) throw new Error(`Failed to load ${sheet}.json`)
        
        const data: SpriteData = await response.json()
        this.sheetData[sheet] = data
        
        this.injectKeyframes(sheet, dir, data)
        this.loadedSheets.add(sheet)
      } catch (e) {
        console.warn(`[spriteStore] Could not load sheet '${sheet}':`, e)
      }
    },
    
    injectKeyframes(sheet: string, dir: string, data: SpriteData) {
      if (!this.injectedStyles) {
        this.injectedStyles = document.createElement('style')
        this.injectedStyles.id = 'pitwall-sprite-keyframes'
        document.head.appendChild(this.injectedStyles)
      }
      
      let cssText = ''
      
      for (const [animId, animObj] of Object.entries(data.animations || {})) {
        const step = 100 / animObj.frames.length
        cssText += `@keyframes sprite-${sheet}-${animId} {\n`
        
        for (let i = 0; i < animObj.frames.length; i++) {
          const frameId = animObj.frames[i]
          const fData = data.frames[frameId]
          if (!fData) continue
          
          const startPct = (i * step).toFixed(2)
          const endPct = ((i + 1) * step - 0.01).toFixed(2)
          
          // Generate discrete keyframe steps
          cssText += `  ${startPct}%, ${endPct}% { `
          cssText += `background-position: -${fData.frame.x}px -${fData.frame.y}px; `
          cssText += `width: ${fData.frame.w}px; `
          cssText += `height: ${fData.frame.h}px; `
          // Note: transform overrides Sprite.vue scale. Instead we use margins or positioning if trimming is heavily used.
          // For now, we assume bounding box handles it or we inject custom vars.
          cssText += `}\n`
        }
        cssText += `}\n`
      }
      
      this.injectedStyles.appendChild(document.createTextNode(cssText))
    },
    
    cssFor(sheet: string, animation: string, options: { scale: number, paused: boolean }) {
      if (!this.loadedSheets.has(sheet)) {
        this.loadSheet(sheet)
        return { display: 'none' } // Hide until loaded
      }
      
      const data = this.sheetData[sheet]
      const animDef = data?.animations?.[animation]
      
      if (!animDef) {
        // Fallback if animation missing
        return {
          backgroundImage: `url(/sprites/${SHEET_DIRS[sheet] || 'ui'}/${sheet}.png)`,
          display: 'inline-block'
        }
      }
      
      const durationMs = (animDef.frames.length / animDef.fps) * 1000
      
      return {
        backgroundImage: `url(/sprites/${SHEET_DIRS[sheet] || 'ui'}/${sheet}.png)`,
        animationName: `sprite-${sheet}-${animation}`,
        animationDuration: `${durationMs}ms`,
        animationTimingFunction: 'linear', // Discrete steps are handled inside the keyframes
        animationIterationCount: animDef.loop ? 'infinite' : '1',
        animationPlayState: options.paused ? 'paused' : 'running',
        transform: `scale(${options.scale})`,
        transformOrigin: 'top left', // Ensure scaling anchors properly
        display: 'inline-block'
      }
    }
  }
})
