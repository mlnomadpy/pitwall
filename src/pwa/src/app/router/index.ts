import { createRouter, createWebHistory } from 'vue-router'
import { useTransitionStore } from '@/shared/lib/transition/transitionStore'
import { useSaveStore } from '@/entities/save/model/saveStore'
import type { TransitionDirection } from '@/shared/lib/transition/transitionStore'

// Import page components using FSD paths
import TitleScreen from '@/pages/home/TitleScreen.vue'
import SaveSelect from '@/pages/save-select/SaveSelect.vue'
import GarageHub from '@/pages/garage-hub/GarageHub.vue'
import TrainerCard from '@/pages/trainer-card/TrainerCard.vue'
import CoachSelect from '@/pages/coach-select/CoachSelect.vue'
import PitStall from '@/pages/pit-stall/PitStall.vue'
import QuestLog from '@/pages/quest-log/QuestLog.vue'
import AnalysisHub from '@/pages/analysis-hub/AnalysisHub.vue'
import LapTimesHall from '@/pages/lap-times-hall/LapTimesHall.vue'
import CornerMastery from '@/pages/corner-mastery/CornerMastery.vue'
import StraightsAndSpeed from '@/pages/straights-and-speed/StraightsAndSpeed.vue'
import TrackWalk from '@/pages/track-walk/TrackWalk.vue'
import DriverEvolution from '@/pages/driver-evolution/DriverEvolution.vue'
import PedalProfile from '@/pages/pedal-profile/PedalProfile.vue'
import SqlConsole from '@/pages/sql-console/SqlConsole.vue'
import PreBrief from '@/pages/pre-brief/PreBrief.vue'
import OnTrackHud from '@/pages/on-track-hud/OnTrackHud.vue'
import StageClear from '@/pages/stage-clear/StageClear.vue'
import Calibration from '@/pages/calibration/Calibration.vue'
import HardwareDetail from '@/pages/hardware-detail/HardwareDetail.vue'
import NotificationCenter from '@/pages/notifications/NotificationCenter.vue'
import ComparisonView from '@/pages/comparison-view/ComparisonView.vue'
import Settings from '@/pages/settings/Settings.vue'
import OnboardingFlow from '@/pages/onboarding/OnboardingFlow.vue'
import EndOfDay from '@/pages/end-of-day/EndOfDay.vue'

const routes = [
  { path: '/', name: 'title', component: TitleScreen },
  { path: '/save', name: 'save', component: SaveSelect, meta: { wipe: 'right' } },
  { path: '/onboarding/:step', name: 'onboarding', component: OnboardingFlow, meta: { wipe: 'right' } },
  { path: '/garage', name: 'garage', component: GarageHub, meta: { wipe: 'right', requiresSave: true } },
  { path: '/garage/trainer', name: 'trainer-card', component: TrainerCard, meta: { wipe: 'up', requiresSave: true } },
  { path: '/garage/coach', name: 'coach-select', component: CoachSelect, meta: { wipe: 'left', requiresSave: true } },
  { path: '/garage/pit-stall', name: 'pit-stall', component: PitStall, meta: { wipe: 'up', requiresSave: true } },
  { path: '/garage/pit-stall/hardware', name: 'hardware', component: HardwareDetail, meta: { wipe: 'left', requiresSave: true } },
  { path: '/garage/quests', name: 'quests', component: QuestLog, meta: { wipe: 'down', requiresSave: true } },
  { path: '/garage/analysis', name: 'analysis', component: AnalysisHub, meta: { wipe: 'left', requiresSave: true } },
  { path: '/analysis/lap-times', name: 'lap-times', component: LapTimesHall, meta: { wipe: 'up', requiresSave: true } },
  { path: '/analysis/compare', name: 'compare', component: ComparisonView, meta: { wipe: 'up', requiresSave: true } },
  { path: '/analysis/corners', name: 'corners', component: CornerMastery, meta: { wipe: 'up', requiresSave: true } },
  { path: '/analysis/straights', name: 'straights', component: StraightsAndSpeed, meta: { wipe: 'up', requiresSave: true } },
  { path: '/analysis/track', name: 'track', component: TrackWalk, meta: { wipe: 'up', requiresSave: true } },
  { path: '/analysis/evolution', name: 'evolution', component: DriverEvolution, meta: { wipe: 'up', requiresSave: true } },
  { path: '/analysis/pedals', name: 'pedals', component: PedalProfile, meta: { wipe: 'up', requiresSave: true } },
  { path: '/analysis/sql', name: 'sql', component: SqlConsole, meta: { wipe: 'down', requiresSave: true } },
  { path: '/briefing', name: 'briefing', component: PreBrief, meta: { wipe: 'up', requiresSave: true } },
  { path: '/hud', name: 'hud', component: OnTrackHud, meta: { wipe: 'down', requiresSave: true } },
  { path: '/stage-clear', name: 'stage-clear', component: StageClear, meta: { wipe: 'up', requiresSave: true } },
  { path: '/calibration', name: 'calibration', component: Calibration, meta: { wipe: 'down', requiresSave: true } },
  { path: '/notifications', name: 'notifications', component: NotificationCenter, meta: { wipe: 'up', requiresSave: true } },
  { path: '/settings', name: 'settings', component: Settings, meta: { wipe: 'down', requiresSave: true } },
  { path: '/end-of-day', name: 'end-of-day', component: EndOfDay, meta: { wipe: 'down', requiresSave: true } },
  
  // Catch-all route to redirect back to title
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

export const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to, from, next) => {
  const save = useSaveStore()
  if (to.meta.requiresSave && save.activeSlotId === null) {
    return next({ name: 'title' }) // No save -> back to title
  }
  
  const trans = useTransitionStore()
  const wipe = (to.meta.wipe ?? 'right') as TransitionDirection
  if (wipe && from.name !== undefined) {
    await trans.play(wipe)
  }
  next()
})
