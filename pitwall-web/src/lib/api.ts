/**
 * Pitwall Bridge API client.
 * All requests go through the Vite proxy (/api → http://127.0.0.1:8765).
 */

const BASE = '/api';

async function get<T>(path: string): Promise<T> {
  const res = await fetch(`${BASE}${path}`);
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}: ${path}`);
  return res.json();
}

async function post<T>(path: string, body?: unknown): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined
  });
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}: ${path}`);
  return res.json();
}

// ── Health ──
export interface HealthResponse {
  status: string;
  version: string;
  engine: string;
  coach: string | null;
  driver_level: string;
  track: string | null;
  duckdb: boolean;
  timestamp: string;
}

export function getHealth(): Promise<HealthResponse> {
  return get('/health');
}

// ── Sessions ──
export interface Session {
  session_id: string;
  driver: string;
  driver_level: string;
  track: string;
  car: string;
  started_at: string;
  ended_at: string | null;
  note: string;
  lap_count: number;
  best_lap_s: number | null;
}

export function getSessions(limit = 50): Promise<{ count: number; sessions: Session[] }> {
  return get(`/sessions?limit=${limit}`);
}

// ── Session Detail ──
export interface SessionDetail {
  session: Session;
  laps: Lap[];
  notes: CoachingNote[];
  lap_count: number;
  best_lap_s: number | null;
}

export interface Lap {
  lap_number: number;
  lap_time_s: number;
  best_sector: number;
  avg_speed_kmh: number;
  max_combo_g: number;
  coast_pct: number;
  recorded_at: string;
}

export interface CoachingNote {
  burst_id: number;
  distance_m: number;
  text: string;
  source: string;
  recorded_at: string;
}

export function getSession(sid: string): Promise<SessionDetail> {
  return get(`/session/${sid}`);
}

// ── Lap Time Table ──
export interface SectorTime {
  name: string;
  time_s: number;
  is_best: boolean;
}

export interface LapTimeRow {
  lap_number: number;
  lap_time_s: number;
  delta_to_best_s: number;
  is_best: boolean;
  sectors: SectorTime[];
}

export interface LapTimeTable {
  session_id: string;
  lap_count: number;
  best_lap_s: number;
  best_lap_number: number;
  laps: LapTimeRow[];
}

export function getLapTimeTable(sid: string): Promise<LapTimeTable> {
  return get(`/session/${sid}/lap_time_table`);
}

// ── Ideal Lap ──
export interface IdealLap {
  session_id: string;
  ideal_lap_s: number;
  best_actual_lap_s: number;
  gain_potential_s: number;
  best_sectors: { name: string; time_s: number; from_lap: number }[];
}

export function getIdealLap(sid: string): Promise<IdealLap> {
  return get(`/session/${sid}/ideal_lap`);
}

// ── Lap Time Distribution ──
export interface LapTimeDistribution {
  session_id: string;
  lap_count: number;
  min_s: number;
  max_s: number;
  q1_s: number;
  median_s: number;
  q3_s: number;
  iqr_s: number;
  whisker_low_s: number;
  whisker_high_s: number;
  outliers: { lap_number: number; lap_time_s: number }[];
  mean_s: number;
  stddev_s: number;
}

export function getLapTimeDistribution(sid: string): Promise<LapTimeDistribution> {
  return get(`/session/${sid}/lap_time_distribution`);
}

// ── Corners ──
export interface CornerData {
  name: string;
  n_passes: number;
  grade?: string;
  best_pass?: Record<string, unknown>;
  avg?: Record<string, unknown>;
}

export function getCorners(sid: string): Promise<{ session_id: string; corners: CornerData[] }> {
  return get(`/session/${sid}/corners`);
}

// ── Throttle Corner Box ──
export interface ThrottleCornerBox {
  name: string;
  n_passes: number;
  n_samples: number;
  min_pct: number;
  q1_pct: number;
  median_pct: number;
  q3_pct: number;
  max_pct: number;
  mean_pct: number;
}

export function getThrottleCornerBox(sid: string): Promise<{ session_id: string; corners: ThrottleCornerBox[] }> {
  return get(`/session/${sid}/throttle_corner_box`);
}

// ── Corner Classification ──
export interface CornerBand {
  band: string;
  corners: string[];
  mean_apex_kmh: number;
  median_apex_kmh: number;
}

export function getCornerClassification(sid: string): Promise<{ session_id: string; bands: CornerBand[] }> {
  return get(`/session/${sid}/corner_classification`);
}

// ── Pedal Behavior ──
export interface PedalState {
  frames: number;
  pct: number;
  time_s: number;
}

export interface PedalBehavior {
  session_id: string;
  frame_count: number;
  frame_dt_s: number;
  states: {
    throttle_only: PedalState;
    brake_only: PedalState;
    trail_brake: PedalState;
    coast: PedalState;
  };
}

export function getPedalBehavior(sid: string): Promise<PedalBehavior> {
  return get(`/session/${sid}/pedal_behavior`);
}

// ── Brake & Acceleration ──
export interface BrakeZone {
  corner: string;
  max_decel_g: number;
  duration_s: number;
  n_passes: number;
}

export interface CornerExit {
  corner: string;
  max_long_accel_g: number;
  exit_speed_kmh: number;
  n_passes: number;
}

export interface BrakeAcceleration {
  session_id: string;
  brake_zones: BrakeZone[];
  corner_exits: CornerExit[];
}

export function getBrakeAcceleration(sid: string): Promise<BrakeAcceleration> {
  return get(`/session/${sid}/brake_acceleration`);
}

// ── Elevation ──
export function getElevation(trackId: string): Promise<unknown> {
  return get(`/track/${trackId}/elevation`);
}

// ── Coach ──

/**
 * Pre-brief: GET /coach/brief?driver=&session_id=
 * Returns narrative_md + focus + emotion
 */
export interface CoachBriefResponse {
  driver_id: string;
  date: string;
  weather_phase: string;
  surface_state: string;
  weather_note: string;
  weakest_recent_corner: string | null;
  biggest_recent_improvement: string | null;
  danger_zones_today: string[];
  narrative_md: string;
  focus: string[];
  emotion: string;
}

export function getCoachBrief(sid: string, driver = ''): Promise<CoachBriefResponse> {
  return get(`/coach/brief?driver=${encodeURIComponent(driver)}&session_id=${encodeURIComponent(sid)}`);
}

/**
 * Debrief: POST /coach/debrief {session_id}
 */
export interface CoachDebriefResponse {
  text: string;
  emotion?: string;
}

export function getCoachDebrief(sid: string): Promise<CoachDebriefResponse> {
  return post('/coach/debrief', { session_id: sid });
}

// ── SSE Stream ──
export function openCueStream(
  sessionId: string,
  onCue: (data: Record<string, unknown>) => void,
  onError?: (err: Event) => void,
  onConnect?: () => void
): EventSource {
  const es = new EventSource(`${BASE}/cues/stream?session_id=${sessionId}`);

  // Bridge sends named events: 'hello' (connection ack) and 'cue' (coaching data)
  es.addEventListener('hello', (e: MessageEvent) => {
    try {
      if (onConnect) onConnect();
      // hello event carries {session_id} — we don't need it but parse to confirm
      JSON.parse(e.data);
    } catch { /* ignore */ }
  });

  es.addEventListener('cue', (e: MessageEvent) => {
    try {
      onCue(JSON.parse(e.data));
    } catch { /* ignore parse errors */ }
  });

  // Also handle unnamed messages as fallback
  es.onmessage = (e) => {
    try {
      onCue(JSON.parse(e.data));
    } catch { /* ignore */ }
  };

  if (onError) es.onerror = onError;
  return es;
}

// ── Tracks ──
export function getTracks(): Promise<unknown> {
  return get('/tracks');
}

// ── Coach concepts ──
export function getCoachConcepts(): Promise<unknown> {
  return get('/coach/concepts');
}

// ── Markers ──
export function getMarkers(): Promise<unknown> {
  return get('/markers');
}

// ── Session Analytics ──
export function getSessionScorecard(sid: string): Promise<unknown> {
  return get(`/session/${sid}/scorecard`);
}

export function getSessionHighlights(sid: string): Promise<unknown> {
  return get(`/session/${sid}/highlights`);
}

export function getSessionStats(sid: string): Promise<unknown> {
  return get(`/session/${sid}/stats`);
}

export function getFrictionCircle(sid: string): Promise<unknown> {
  return get(`/session/${sid}/friction_circle`);
}

export function getHustleMap(sid: string): Promise<unknown> {
  return get(`/session/${sid}/hustle_map`);
}

export function getEndOfBraking(sid: string): Promise<unknown> {
  return get(`/session/${sid}/eob`);
}

export function getIncidents(sid: string): Promise<unknown> {
  return get(`/session/${sid}/incidents`);
}

export function getSessionMap(sid: string): Promise<unknown> {
  return get(`/session/${sid}/map`);
}

export function getClips(sid: string): Promise<unknown> {
  return get(`/session/${sid}/clips`);
}

export function getSectorTimes(sid: string): Promise<unknown> {
  return get(`/session/${sid}/sector_times`);
}

export function getStraightLineSpeed(sid: string): Promise<unknown> {
  return get(`/session/${sid}/straight_line_speed`);
}

export function getSessionCapabilities(sid: string): Promise<unknown> {
  return get(`/session/${sid}/capabilities`);
}

export function getSignals(sid: string, names: string[]): Promise<unknown> {
  return get(`/session/${sid}/signals?names=${names.join(',')}`);
}

export function getSignalRegistry(): Promise<unknown> {
  return get('/signals/registry');
}

// ── Driver ──
export function getDriverProfile(driverId: string): Promise<unknown> {
  return get(`/driver/${driverId}/profile`);
}

export function getDriverEvolution(driverId: string, track?: string): Promise<unknown> {
  const qs = track ? `?track=${encodeURIComponent(track)}` : '';
  return get(`/driver/${driverId}/evolution${qs}`);
}

// ── Insights ──
export function getInsights(): Promise<unknown> {
  return get('/insights');
}

// ── Notifications ──
export function getNotifications(): Promise<unknown> {
  return get('/notifications');
}

// ── LLM Score ──
export function postScore(sid: string, focus?: string): Promise<unknown> {
  return post('/score', { session_id: sid, focus });
}
