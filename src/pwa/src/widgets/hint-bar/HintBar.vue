<script setup lang="ts">
export interface HintAction {
  /** Display label (e.g., "BACK", "SELECT", "COMPARE") */
  label: string
  /** The keyboard key this originally mapped to (for dispatching) */
  key?: string
  /** Optional icon character (e.g., "◀", "▶", "◆") */
  icon?: string
  /** Visual style */
  variant?: 'default' | 'primary' | 'warn' | 'subtle'
}

const props = defineProps<{ 
  /** Legacy string hints for backward compatibility */
  hints?: string[]
  /** New structured actions */
  actions?: HintAction[]
}>()

const emit = defineEmits<{
  (e: 'action', action: HintAction): void
}>()

/** Parse legacy hint strings like "A · SELECT" into HintAction objects */
const parsedActions = (): HintAction[] => {
  if (props.actions && props.actions.length > 0) return props.actions
  if (!props.hints) return []

  return props.hints.map(hint => {
    // Pattern: "KEY · LABEL" or just "LABEL"
    const parts = hint.split(' · ')
    if (parts.length >= 2) {
      const keyPart = parts[0].trim()
      const labelPart = parts.slice(1).join(' · ').trim()
      
      // Determine the key to dispatch
      const keyMap: Record<string, string> = {
        'A': 'Enter',
        'B': 'Escape',
        'C': 'c',
        'D': 'd',
        '◆': 'Shift',
        '◀ ▶': '',      // directional, not a single action
        '▲ ▼': '',      // directional, not a single action
        '▲ ▼ ◀ ▶': '',  // directional, not a single action
        'SHIFT+◀ ▶': '',
        'SPACE': ' ',
      }

      const mappedKey = keyMap[keyPart] ?? keyPart.toLowerCase()
      
      // Directional hints are informational only
      const isDirectional = !mappedKey
      
      // Determine variant
      let variant: HintAction['variant'] = 'default'
      if (keyPart === 'A' || keyPart === 'ENTER') variant = 'primary'
      if (keyPart === 'B') variant = 'warn'
      if (isDirectional) variant = 'subtle'

      return {
        label: labelPart,
        key: isDirectional ? undefined : mappedKey,
        icon: isDirectional ? keyPart : undefined,
        variant,
      }
    }
    return { label: hint, variant: 'subtle' as const }
  })
}

const handleTap = (action: HintAction) => {
  if (!action.key) return // Directional hints are not tappable
  
  // Dispatch the corresponding keyboard event so existing handlers fire
  window.dispatchEvent(new KeyboardEvent('keydown', { 
    key: action.key, 
    bubbles: true 
  }))

  emit('action', action)
}
</script>

<template>
  <div class="hint-bar pixelated" role="toolbar" aria-label="Actions">
    <div class="hints-inner no-scrollbar">
      <button 
        v-for="(action, index) in parsedActions()" 
        :key="index" 
        class="hint-btn"
        :class="[
          `variant-${action.variant || 'default'}`,
          { tappable: !!action.key }
        ]"
        :aria-label="action.label"
        :disabled="!action.key"
        @click="handleTap(action)"
      >
        <span v-if="action.icon" class="hint-icon" aria-hidden="true">{{ action.icon }}</span>
        <span class="hint-label">{{ action.label }}</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.hint-bar {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  height: clamp(28px, 5.5vh, 48px);
  background: linear-gradient(180deg, rgba(26, 29, 46, 0.85) 0%, rgba(26, 29, 46, 0.95) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 clamp(4px, 1vw, 12px);
  font-family: var(--font-ui);
  font-size: clamp(8px, 1.8vmin, 15px);
  border-top: 1px solid var(--color-slate);
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.3);
  z-index: var(--z-sticky, 10);
  color: var(--color-silver);
}

.hints-inner {
  display: flex;
  align-items: center;
  gap: clamp(2px, 0.5vw, 6px);
  width: 100%;
  overflow: hidden;
  flex-wrap: nowrap;
  justify-content: center;
}

.hint-btn {
  display: flex;
  align-items: center;
  gap: clamp(2px, 0.4vw, 4px);
  white-space: nowrap;
  flex-shrink: 1;
  min-width: 0;
  background: none;
  border: 1px solid transparent;
  outline: none;
  cursor: default;
  font: inherit;
  color: inherit;
  padding: clamp(2px, 0.4vh, 4px) clamp(4px, 1vw, 8px);
  transition: all var(--duration-fast, 150ms) ease;
  -webkit-tap-highlight-color: transparent;
  user-select: none;
  -webkit-user-select: none;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* Tappable buttons get interactive styles */
.hint-btn.tappable {
  cursor: pointer;
  border-color: color-mix(in srgb, var(--color-slate) 40%, transparent);
  border-radius: 2px;
  flex-shrink: 0; /* Tappable buttons should stay visible */
}

.hint-btn.tappable:active {
  transform: scale(0.95);
  opacity: 0.7;
}

/* Variants */
.variant-primary {
  color: var(--color-ui-good);
}
.variant-primary.tappable {
  border-color: color-mix(in srgb, var(--color-ui-good) 40%, transparent);
  background: color-mix(in srgb, var(--color-ui-good) 8%, transparent);
}

.variant-warn {
  color: var(--color-ui-warn);
}
.variant-warn.tappable {
  border-color: color-mix(in srgb, var(--color-ui-warn) 30%, transparent);
  background: color-mix(in srgb, var(--color-ui-warn) 5%, transparent);
}

.variant-default {
  color: var(--color-silver);
}

.variant-subtle {
  color: var(--color-slate);
  opacity: 0.5;
  font-size: 0.85em;
  flex-shrink: 1; /* Subtle hints shrink first */
}

.hint-icon {
  opacity: 0.5;
  font-size: 0.85em;
}

.hint-label {
  letter-spacing: 0.04em;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
