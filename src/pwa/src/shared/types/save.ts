export type CoachId = 'trod' | 'bentley' | 'drill' | 'calm' | 'buddy'
export type TrackId = 'sonoma' | 'laguna' | 'thunderhill' | 'buttonwillow'

export interface SaveSlot {
  schemaVersion: 1
  id: 1 | 2 | 3
  createdAt: string
  lastPlayedAt: string

  driverName: string
  driverAvatar?: string
  skillLevel: 'beginner' | 'intermediate' | 'pro'
  car: string
  avatarSlot: 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8

  preferredCoach: CoachId
  preferredTrack: TrackId

  level: number
  sessions: SessionSummary[]
  bestLapBySession: Record<string, number>

  coachAffinity: Record<CoachId, number>

  unlockedTracks: TrackId[]
  unlockedAvatars: number[]
  unlockedCoaches: CoachId[]

  medals: { id: string; awardedAt: string; sessionId?: string }[]
  goalsHistory: SessionGoal[]

  settings: SaveSettings
}

export interface SessionSummary {
  sessionId: string
  startedAt: string
  trackId: string
  bestLapS: number | null
  lapCount: number
  coachId: CoachId
  goalsHit: number
  goalsTotal: number
  totalScore: number
  pbAchieved: boolean
}

export interface SessionGoal {
  id: string
  kind: 'corner_focus' | 'lap_time' | 'technique'
  description: string
  targetValue: number
  achievedAt?: string
  result?: 'hit' | 'partial' | 'miss'
}

export interface SaveSettings {
  audio: {
    masterVolume: number
    musicVolume: number
    sfxVolume: number
    voiceVolume: number
    coachMute: boolean
  }
  display: {
    nightMode: boolean
    reducedMotion: boolean
    showFps: boolean
  }
  controls: {
    keyboardLayout: 'wasd' | 'arrows' | 'igdk'
    swapAB: boolean
  }
  ux: {
    typewriterSpeed: 'off' | 'fast' | 'normal' | 'slow'
    hapticFeedback: boolean
  }
}

