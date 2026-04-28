import 'dart:async';
import 'package:flutter/material.dart';
import 'package:fl_chart/fl_chart.dart';
import '../platform/pitwall_channel.dart';
import '../theme/pitwall_theme.dart';

/// Paddock/Engineer Mode — full analytics dashboard.
/// Auto-activates when speed < 5mph for > 30 seconds.
/// Tabs: Laps | Speed Trace | Corners | Friction Circle | Driver Profile | AI Debrief
class PaddockScreen extends StatefulWidget {
  final VoidCallback onReturnToTrack;
  final VoidCallback onReturnToSetup;
  const PaddockScreen({
    super.key,
    required this.onReturnToTrack,
    required this.onReturnToSetup,
  });

  @override
  State<PaddockScreen> createState() => _PaddockScreenState();
}

class _PaddockScreenState extends State<PaddockScreen>
    with SingleTickerProviderStateMixin {
  late final TabController _tabs;
  final List<LapRecord> _laps = [];
  final List<FlSpot> _speedTrace = [];
  final List<FlSpot> _frictionCircle = [];
  String _aiDebrief = 'Generating debrief…';
  int _frameCount = 0;

  StreamSubscription<TelemetryState>? _telemetrySub;
  StreamSubscription<CoachingEvent>? _coachingSub;

  // Sonoma Gold Standard (AJ's reference)
  static const _goldLapTime = 98.2;
  static const _goldCorners = {
    'Turn 3':  {'entry': 104.0, 'apex': 87.0,  'exit': 102.0},
    'Turn 6':  {'entry': 92.0,  'apex': 77.0,  'exit': 105.0},
    'Turn 9':  {'entry': 121.0, 'apex': 116.0, 'exit': 132.0},
    'Turn 10': {'entry': 106.0, 'apex': 73.0,  'exit': 108.0},
    'Turn 11': {'entry': 88.0,  'apex': 64.0,  'exit': 95.0},
  };

  @override
  void initState() {
    super.initState();
    _tabs = TabController(length: 6, vsync: this);

    _telemetrySub = PitwallChannel.telemetry.listen(_onFrame);
    _coachingSub  = PitwallChannel.coaching.listen(_onCoaching);

    // Mock some lap data for demonstration
    _seedMockLaps();
    _fetchDebrief();
  }

  void _onFrame(TelemetryState frame) {
    if (!mounted) return;
    _frameCount++;
    setState(() {});

    // Build speed trace (downsample to 1Hz for chart performance)
    if (_frameCount % 10 == 0) {
      _speedTrace.add(FlSpot(frame.distance / 1000, frame.speedKmh));
      if (_speedTrace.length > 500) _speedTrace.removeAt(0);
    }

    // Friction circle data
    _frictionCircle.add(FlSpot(frame.gLat, frame.gLong));
    if (_frictionCircle.length > 300) _frictionCircle.removeAt(0);

    // Return-to-track detection
    if (frame.speedMph > 20) widget.onReturnToTrack();
  }

  void _onCoaching(CoachingEvent event) {
    if (!mounted) return;
    if (event.source == 'WARM_PATH') {
      setState(() { _aiDebrief = event.text; });
    }
  }

  void _seedMockLaps() {
    _laps.addAll([
      LapRecord(1, 105.3, sectorTimes: [34.8, 36.1, 34.4]),
      LapRecord(2, 103.7, sectorTimes: [34.1, 35.6, 34.0]),
      LapRecord(3, 102.4, sectorTimes: [33.7, 35.2, 33.5]),
    ]);
  }

  Future<void> _fetchDebrief() async {
    // The real debrief arrives via the coaching EventChannel from Gemini warm path.
    await Future.delayed(const Duration(seconds: 2));
    if (mounted) {
      setState(() {
        _aiDebrief = 'Good session. Best lap 1:42.4, 4.2s behind AJ. '
            'Your Turn 3 improved — exit speed up this run. '
            'Focus for next session: Turn 10 entry — braking 20m too early.';
      });
    }
  }

  @override
  void dispose() {
    _telemetrySub?.cancel();
    _coachingSub?.cancel();
    _tabs.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: PitwallColors.background,
      appBar: AppBar(
        backgroundColor: PitwallColors.surface,
        elevation: 0,
        title: Row(
          children: [
            const Text('PITWALL', style: TextStyle(
              color: PitwallColors.gripGreen,
              fontSize: 14,
              fontWeight: FontWeight.w800,
              letterSpacing: 4,
            )),
            const SizedBox(width: 16),
            Text('ENGINEER MODE', style: TextStyle(
              color: PitwallColors.textDim,
              fontSize: 11,
              letterSpacing: 3,
            )),
          ],
        ),
        actions: [
          TextButton.icon(
            onPressed: widget.onReturnToTrack,
            icon: const Icon(Icons.speed, color: PitwallColors.gripGreen),
            label: const Text('BACK ON TRACK',
                style: TextStyle(color: PitwallColors.gripGreen, fontSize: 11, letterSpacing: 2)),
          ),
          TextButton.icon(
            onPressed: () async {
              await PitwallChannel.stopSession();
              widget.onReturnToSetup();
            },
            icon: const Icon(Icons.home_outlined, color: PitwallColors.textDim),
            label: const Text('MAIN MENU',
                style: TextStyle(color: PitwallColors.textDim, fontSize: 11, letterSpacing: 2)),
          ),
          const SizedBox(width: 8),
        ],
        bottom: TabBar(
          controller: _tabs,
          indicatorColor: PitwallColors.gripGreen,
          indicatorWeight: 2,
          labelColor: PitwallColors.textPrimary,
          unselectedLabelColor: PitwallColors.textDim,
          labelStyle: const TextStyle(fontSize: 11, fontWeight: FontWeight.w700, letterSpacing: 1.5),
          tabs: const [
            Tab(text: 'LAPS'),
            Tab(text: 'SPEED TRACE'),
            Tab(text: 'CORNERS'),
            Tab(text: 'FRICTION'),
            Tab(text: 'PROFILE'),
            Tab(text: 'AI DEBRIEF'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabs,
        children: [
          _LapsTab(laps: _laps, goldLapTime: _goldLapTime),
          _SpeedTraceTab(spots: _speedTrace),
          _CornersTab(laps: _laps, goldCorners: _goldCorners),
          _FrictionCircleTab(spots: _frictionCircle),
          _DriverProfileTab(laps: _laps),
          _AiDebriefTab(debrief: _aiDebrief),
        ],
      ),
    );
  }
}

// ── Lap Times ────────────────────────────────────────────────────────────────

class LapRecord {
  final int number;
  final double time;
  final List<double> sectorTimes;
  LapRecord(this.number, this.time, {this.sectorTimes = const []});

  double get bestSector => sectorTimes.isEmpty ? 0 : sectorTimes.reduce((a, b) => a < b ? a : b);
}

class _LapsTab extends StatelessWidget {
  final List<LapRecord> laps;
  final double goldLapTime;
  const _LapsTab({required this.laps, required this.goldLapTime});

  @override
  Widget build(BuildContext context) {
    final best = laps.isEmpty ? null : laps.map((l) => l.time).reduce((a, b) => a < b ? a : b);
    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Header
          Row(
            children: [
              _StatCard('BEST', best == null ? '--' : _fmtLap(best),
                  color: PitwallColors.gripGreen),
              const SizedBox(width: 16),
              _StatCard('vs AJ', best == null ? '--' :
              '+${(best - goldLapTime).toStringAsFixed(1)}s',
                  color: PitwallColors.goldStandard),
              const SizedBox(width: 16),
              _StatCard('LAPS', '${laps.length}', color: PitwallColors.speedBlue),
            ],
          ),
          const SizedBox(height: 24),
          // Table
          Expanded(
            child: _DataTable(
              headers: ['LAP', 'TIME', 'Δ BEST', 'Δ AJ', 'S1', 'S2', 'S3'],
              rows: laps.map((lap) {
                final delta = best != null ? lap.time - best : 0.0;
                final deltaAj = lap.time - goldLapTime;
                return [
                  '${lap.number}',
                  _fmtLap(lap.time),
                  delta == 0 ? '—' : '+${delta.toStringAsFixed(2)}',
                  deltaAj > 0 ? '+${deltaAj.toStringAsFixed(1)}' : deltaAj.toStringAsFixed(1),
                  ...lap.sectorTimes.map((s) => s.toStringAsFixed(1)),
                ];
              }).toList(),
              highlightRow: laps.indexWhere((l) => l.time == best),
            ),
          ),
        ],
      ),
    );
  }

  String _fmtLap(double s) {
    final mins = (s / 60).floor();
    final secs = (s % 60).toStringAsFixed(2).padLeft(5, '0');
    return '$mins:$secs';
  }
}

// ── Speed Trace ───────────────────────────────────────────────────────────────

class _SpeedTraceTab extends StatelessWidget {
  final List<FlSpot> spots;
  const _SpeedTraceTab({required this.spots});

  @override
  Widget build(BuildContext context) {
    if (spots.isEmpty) {
      return const Center(child: Text('Speed trace will appear as you drive.',
          style: TextStyle(color: PitwallColors.textDim)));
    }
    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const _SectionHeader('SPEED TRACE — Distance (km) vs Speed (km/h)'),
          const SizedBox(height: 16),
          Expanded(
            child: LineChart(
              LineChartData(
                backgroundColor: PitwallColors.surface,
                gridData: FlGridData(
                  horizontalInterval: 50,
                  getDrawingHorizontalLine: (v) => FlLine(
                      color: PitwallColors.border, strokeWidth: 0.5),
                  getDrawingVerticalLine: (v) => FlLine(
                      color: PitwallColors.border, strokeWidth: 0.5),
                ),
                borderData: FlBorderData(
                  border: const Border.fromBorderSide(
                      BorderSide(color: PitwallColors.border))),
                titlesData: FlTitlesData(
                  bottomTitles: AxisTitles(sideTitles: SideTitles(
                    showTitles: true, reservedSize: 28,
                    getTitlesWidget: (v, m) => Text('${v.toStringAsFixed(1)}km',
                        style: const TextStyle(color: PitwallColors.textDim, fontSize: 9)),
                  )),
                  leftTitles: AxisTitles(sideTitles: SideTitles(
                    showTitles: true, reservedSize: 40,
                    getTitlesWidget: (v, m) => Text('${v.toInt()}',
                        style: const TextStyle(color: PitwallColors.textDim, fontSize: 9)),
                  )),
                  topTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                  rightTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                ),
                lineBarsData: [
                  LineChartBarData(
                    spots: spots,
                    isCurved: true,
                    color: PitwallColors.speedBlue,
                    barWidth: 2,
                    dotData: const FlDotData(show: false),
                    belowBarData: BarAreaData(
                      show: true,
                      color: PitwallColors.speedBlue.withValues(alpha: 0.08),
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          Row(children: [
            _Legend(PitwallColors.speedBlue, 'This lap'),
            const SizedBox(width: 20),
            _Legend(PitwallColors.gripGreen, 'Personal best'),
            const SizedBox(width: 20),
            _Legend(PitwallColors.goldStandard, 'AJ (Gold Standard)'),
          ]),
        ],
      ),
    );
  }
}

// ── Corner Report Card ────────────────────────────────────────────────────────

class _CornersTab extends StatelessWidget {
  final List<LapRecord> laps;
  final Map<String, Map<String, double>> goldCorners;
  const _CornersTab({required this.laps, required this.goldCorners});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const _SectionHeader('CORNER REPORT CARD — vs AJ\'s Gold Standard'),
          const SizedBox(height: 16),
          _Legend(PitwallColors.gripGreen, 'A: within 5% of AJ'),
          const SizedBox(height: 4),
          _Legend(PitwallColors.speedBlue, 'B: within 15%'),
          const SizedBox(height: 4),
          _Legend(PitwallColors.gripYellow, 'C: within 25%'),
          const SizedBox(height: 4),
          _Legend(PitwallColors.gripRed, 'D: > 25% gap'),
          const SizedBox(height: 20),
          Expanded(
            child: _DataTable(
              headers: ['CORNER', 'ENTRY', 'APEX', 'EXIT', 'vs AJ EXIT', 'GRADE'],
              rows: goldCorners.entries.map((e) {
                // Mock driver values (85–95% of gold)
                final factor = 0.88 + (e.key.hashCode % 10) * 0.012;
                final aphFactor = 0.85 + (e.key.hashCode % 8) * 0.015;
                final driverEntry = (e.value['entry']! * factor).toStringAsFixed(0);
                final driverApex = (e.value['apex']! * aphFactor).toStringAsFixed(0);
                final driverExit = (e.value['exit']! * factor).toStringAsFixed(0);
                final exitDelta = e.value['exit']! * (factor - 1);
                final grade = factor > 0.95 ? 'A' : factor > 0.85 ? 'B' : factor > 0.75 ? 'C' : 'D';
                return [
                  e.key,
                  '$driverEntry km/h',
                  '$driverApex km/h',
                  '$driverExit km/h',
                  '${exitDelta.toStringAsFixed(1)} km/h',
                  grade,
                ];
              }).toList(),
            ),
          ),
        ],
      ),
    );
  }
}

// ── Friction Circle ────────────────────────────────────────────────────────────

class _FrictionCircleTab extends StatelessWidget {
  final List<FlSpot> spots;
  const _FrictionCircleTab({required this.spots});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const _SectionHeader('FRICTION CIRCLE — G-Lateral vs G-Longitudinal'),
          const SizedBox(height: 8),
          Text('Diameter = grip available. Utilisation: ${_utilisation(spots)}% mean.',
              style: const TextStyle(color: PitwallColors.textSecondary, fontSize: 12)),
          const SizedBox(height: 16),
          Expanded(
            child: spots.isEmpty
                ? const Center(child: Text('Drive to see the friction circle.',
                style: TextStyle(color: PitwallColors.textDim)))
                : ScatterChart(
              ScatterChartData(
                scatterSpots: spots.map((s) => ScatterSpot(
                  s.x, s.y,
                  dotPainter: FlDotCirclePainter(
                    radius: 2,
                    color: _spotColor(s),
                  ),
                )).toList(),
                minX: -2.0, maxX: 2.0,
                minY: -2.0, maxY: 2.0,
                gridData: FlGridData(
                  getDrawingHorizontalLine: (v) => FlLine(
                      color: v == 0 ? PitwallColors.border.withValues(alpha: 0.8) : PitwallColors.border.withValues(alpha: 0.3),
                      strokeWidth: v == 0 ? 1 : 0.3),
                  getDrawingVerticalLine: (v) => FlLine(
                      color: v == 0 ? PitwallColors.border.withValues(alpha: 0.8) : PitwallColors.border.withValues(alpha: 0.3),
                      strokeWidth: v == 0 ? 1 : 0.3),
                ),
                borderData: FlBorderData(border: const Border.fromBorderSide(
                    BorderSide(color: PitwallColors.border))),
                titlesData: FlTitlesData(
                  bottomTitles: AxisTitles(sideTitles: SideTitles(
                    showTitles: true, reservedSize: 24,
                    getTitlesWidget: (v, m) => Text('${v.toStringAsFixed(1)}G',
                        style: const TextStyle(color: PitwallColors.textDim, fontSize: 9)),
                  )),
                  leftTitles: AxisTitles(sideTitles: SideTitles(
                    showTitles: true, reservedSize: 36,
                    getTitlesWidget: (v, m) => Text('${v.toStringAsFixed(1)}',
                        style: const TextStyle(color: PitwallColors.textDim, fontSize: 9)),
                  )),
                  topTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                  rightTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Color _spotColor(FlSpot s) {
    final combo = (s.x * s.x + s.y * s.y);
    if (combo > 1.8) return PitwallColors.gripRed;
    if (combo > 1.2) return PitwallColors.gripYellow;
    return PitwallColors.speedBlue.withValues(alpha: 0.6);
  }

  String _utilisation(List<FlSpot> spots) {
    if (spots.isEmpty) return '0';
    final avg = spots.map((s) => (s.x * s.x + s.y * s.y)).reduce((a, b) => a + b) / spots.length;
    return (avg / (2.29 * 2.29) * 100).toStringAsFixed(0);
  }
}

// ── Driver Profile ────────────────────────────────────────────────────────────

class _DriverProfileTab extends StatelessWidget {
  final List<LapRecord> laps;
  const _DriverProfileTab({required this.laps});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const _SectionHeader('DRIVER PROFILE — Skill Radar'),
          const SizedBox(height: 16),
          Expanded(
            child: GridView.count(
              crossAxisCount: 3,
              crossAxisSpacing: 16, mainAxisSpacing: 16,
              children: [
                _SkillCard('Braking', 0.72, PitwallColors.brakeRed),
                _SkillCard('Trail Brake', 0.45, PitwallColors.gripYellow),
                _SkillCard('Corner Speed', 0.68, PitwallColors.speedBlue),
                _SkillCard('Throttle App.', 0.61, PitwallColors.throttleGreen),
                _SkillCard('Consistency', 0.78, PitwallColors.gripGreen),
                _SkillCard('Line Accuracy', 0.55, PitwallColors.gForceOrange),
              ],
            ),
          ),
          const SizedBox(height: 16),
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: PitwallColors.surface,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: PitwallColors.border),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('ARCHETYPE', style: TextStyle(
                    color: PitwallColors.textDim, fontSize: 10, letterSpacing: 2)),
                const SizedBox(height: 6),
                const Text('Smooth Early-Braker', style: TextStyle(
                    color: PitwallColors.textPrimary, fontSize: 18,
                    fontWeight: FontWeight.w700)),
                const SizedBox(height: 4),
                Text('Prioritises consistency and clean entries. '
                    'Coaching focus: trail braking + corner speed.',
                    style: const TextStyle(color: PitwallColors.textSecondary, fontSize: 12)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _SkillCard extends StatelessWidget {
  final String label;
  final double score;    // 0.0–1.0
  final Color color;
  const _SkillCard(this.label, this.score, this.color);

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: PitwallColors.surface,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: color.withValues(alpha: 0.3)),
      ),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text(label.toUpperCase(), style: TextStyle(
              color: PitwallColors.textDim, fontSize: 10, letterSpacing: 1.5)),
          const SizedBox(height: 8),
          Text('${(score * 100).toInt()}', style: TextStyle(
              color: color, fontSize: 36, fontWeight: FontWeight.w800)),
          const SizedBox(height: 8),
          LinearProgressIndicator(
            value: score, backgroundColor: color.withValues(alpha: 0.15),
            valueColor: AlwaysStoppedAnimation<Color>(color),
          ),
        ],
      ),
    );
  }
}

// ── AI Debrief ────────────────────────────────────────────────────────────────

class _AiDebriefTab extends StatelessWidget {
  final String debrief;
  const _AiDebriefTab({required this.debrief});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(32),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(children: [
            const _SectionHeader('AI DEBRIEF'),
            const SizedBox(width: 12),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
              decoration: BoxDecoration(
                color: PitwallColors.speedBlue.withValues(alpha: 0.15),
                borderRadius: BorderRadius.circular(4),
                border: Border.all(color: PitwallColors.speedBlue.withValues(alpha: 0.4)),
              ),
              child: const Text('Gemini 3.0', style: TextStyle(
                  color: PitwallColors.speedBlue, fontSize: 10, letterSpacing: 1)),
            ),
          ]),
          const SizedBox(height: 24),
          Container(
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              color: PitwallColors.surface,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: PitwallColors.border),
            ),
            child: Text(
              debrief,
              style: const TextStyle(
                color: PitwallColors.textPrimary,
                fontSize: 16,
                height: 1.7,
                fontWeight: FontWeight.w400,
              ),
            ),
          ),
          const SizedBox(height: 16),
          Text('Generated from Antigravity telemetry burst via Vertex AI',
              style: const TextStyle(color: PitwallColors.textDim, fontSize: 11)),
        ],
      ),
    );
  }
}

// ── Shared Widgets ────────────────────────────────────────────────────────────

class _SectionHeader extends StatelessWidget {
  final String text;
  const _SectionHeader(this.text);
  @override
  Widget build(BuildContext context) => Text(
    text,
    style: const TextStyle(
      color: PitwallColors.textDim,
      fontSize: 11,
      fontWeight: FontWeight.w700,
      letterSpacing: 2,
    ),
  );
}

class _Legend extends StatelessWidget {
  final Color color;
  final String label;
  const _Legend(this.color, this.label);
  @override
  Widget build(BuildContext context) => Row(
    mainAxisSize: MainAxisSize.min,
    children: [
      Container(width: 12, height: 3, color: color,
          decoration: BoxDecoration(borderRadius: BorderRadius.circular(2))),
      const SizedBox(width: 6),
      Text(label, style: const TextStyle(
          color: PitwallColors.textDim, fontSize: 11)),
    ],
  );
}

class _StatCard extends StatelessWidget {
  final String label;
  final String value;
  final Color color;
  const _StatCard(this.label, this.value, {required this.color});
  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
    decoration: BoxDecoration(
      color: color.withValues(alpha: 0.08),
      borderRadius: BorderRadius.circular(10),
      border: Border.all(color: color.withValues(alpha: 0.3)),
    ),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: TextStyle(color: color.withValues(alpha: 0.7), fontSize: 9, letterSpacing: 2)),
        const SizedBox(height: 4),
        Text(value, style: TextStyle(color: color, fontSize: 22, fontWeight: FontWeight.w800,
            fontFamily: 'RobotoMono')),
      ],
    ),
  );
}

class _DataTable extends StatelessWidget {
  final List<String> headers;
  final List<List<String>> rows;
  final int highlightRow;
  const _DataTable({required this.headers, required this.rows, this.highlightRow = -1});

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: PitwallColors.surface,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: PitwallColors.border),
      ),
      child: SingleChildScrollView(
        child: Table(
          border: TableBorder.symmetric(
            inside: const BorderSide(color: PitwallColors.border, width: 0.5),
          ),
          children: [
            TableRow(
              decoration: const BoxDecoration(
                color: PitwallColors.surfaceElevated,
                borderRadius: BorderRadius.vertical(top: Radius.circular(12)),
              ),
              children: headers.map((h) => Padding(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                child: Text(h, style: const TextStyle(
                    color: PitwallColors.textDim, fontSize: 10,
                    fontWeight: FontWeight.w700, letterSpacing: 1.2)),
              )).toList(),
            ),
            ...rows.asMap().entries.map((entry) {
              final isHighlight = entry.key == highlightRow;
              return TableRow(
                decoration: BoxDecoration(
                  color: isHighlight
                      ? PitwallColors.gripGreen.withValues(alpha: 0.07)
                      : Colors.transparent,
                ),
                children: entry.value.map((cell) => Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  child: Text(cell, style: TextStyle(
                    color: isHighlight ? PitwallColors.gripGreen : PitwallColors.textPrimary,
                    fontSize: 12,
                    fontWeight: isHighlight ? FontWeight.w700 : FontWeight.w400,
                    fontFamily: 'RobotoMono',
                  )),
                )).toList(),
              );
            }),
          ],
        ),
      ),
    );
  }
}

