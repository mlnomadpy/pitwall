<script setup lang="ts">
import CyberPanel from '@/shared/ui/core/CyberPanel.vue'

// In a real implementation, we would pass an array of goals
// For now, we'll keep the dummy data from QuestLog but allow overriding
interface Goal {
  id: string
  name: string
  val: string
  status: 'pending' | 'success' | 'failed'
}

interface Props {
  title?: string
  goals?: Goal[]
}

const props = withDefaults(defineProps<Props>(), {
  title: 'ACTIVE GOALS (THIS SESSION)',
  goals: () => [
    { id: '1', name: 'APEX SPEED AT T7', val: '82 → 84 km/h (target +3)', status: 'pending' },
    { id: '2', name: 'BREAK 1:48', val: '1:46.8 ✓', status: 'success' },
    { id: '3', name: 'TRAIL EVERY ENTRY', val: '4 of 11', status: 'failed' }
  ]
})

const getStatusClass = (status: string) => {
  if (status === 'success') return 'text-ui-good'
  if (status === 'failed') return 'text-ui-bad'
  return 'text-ui-info'
}

const getStatusIcon = (status: string) => {
  if (status === 'success') return '✓'
  if (status === 'failed') return '✗'
  return '◐'
}
</script>

<template>
  <CyberPanel variant="glass" border="primary" class="goals-frame w-full h-full">
    <h2 class="section-label">{{ title }}</h2>
    
    <div class="goals-list flex flex-col gap-2">
      <div 
        v-for="goal in goals" 
        :key="goal.id"
        class="goal-row" 
        :class="getStatusClass(goal.status)"
      >
        <span class="goal-icon">{{ getStatusIcon(goal.status) }}</span>
        <span class="goal-name">{{ goal.name }}</span>
        <span class="goal-val">{{ goal.val }}</span>
      </div>
    </div>
  </CyberPanel>
</template>

<style scoped>
.goals-frame {
  padding: clamp(8px, 2vmin, 16px);
  display: flex;
  flex-direction: column;
}

.section-label {
  font-family: var(--font-title);
  font-size: clamp(10px, 2.5vmin, 20px);
  color: var(--color-silver);
  margin-bottom: clamp(6px, 1.5vmin, 12px);
  border-bottom: 1px solid var(--color-slate);
  padding-bottom: 4px;
}

.goals-list {
  flex: 1;
  overflow-y: auto;
}

.goal-row {
  display: flex;
  align-items: center;
  gap: clamp(6px, 1.5vw, 14px);
  font-size: clamp(10px, 2.3vmin, 18px);
  margin-bottom: clamp(2px, 0.4vh, 5px);
  background: rgba(0, 0, 0, 0.2);
  padding: clamp(4px, 1vmin, 8px) clamp(8px, 1.5vmin, 12px);
  border-radius: 4px;
}

.goal-icon { 
  flex: 0 0 auto; 
  font-size: clamp(12px, 2.5vmin, 22px); 
}

.goal-name { 
  flex: 1; 
  font-weight: bold;
  letter-spacing: 0.05em;
}

.goal-val { 
  flex: 0 0 auto; 
  text-align: right; 
  font-family: monospace;
}
</style>
