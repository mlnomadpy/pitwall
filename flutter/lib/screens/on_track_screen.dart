import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../platform/pitwall_channel.dart';
import '../theme/pitwall_theme.dart';

/// On-Track Screen — Signal Light HUD.
///
/// Exactly as specified in ux.md:
///   Left bar  (green): grip available → height = unused friction circle
///   Right bar (red):   over-limit    → appears only when comboG > 95% of max
///   Everything else: audio via earbuds, no text, no numbers, no graphs.
///
/// Auto-switches to PaddockScreen when speed < 5 mph for > 30 seconds.
class OnTrackScreen extends StatefulWidget {
  final VoidCallback onEnterPaddock;
  final VoidCallback onReturnToSetup;
  const OnTrackScreen({super.key, required this.onEnterPaddock, required this.onReturnToSetup});

  @override
  State<OnTrackScreen> createState() => _OnTrackScreenState();
}

class _OnTrackScreenState extends State<OnTrackScreen>
    with TickerProviderStateMixin {
  TelemetryState _frame = TelemetryState.empty;
  CoachingEvent? _lastCoaching;

  // Animation controllers for smooth bar transitions
  late final AnimationController _gripAnimCtrl;
  late final AnimationController _redAnimCtrl;
  late final AnimationController _coachingAnimCtrl;
  late Animation<double> _gripAnim;
  late Animation<double> _redAnim;
  late Animation<double> _coachingOpacity;

  // Stream subscriptions — stored so we can cancel on dispose
  StreamSubscription<TelemetryState>? _telemetrySub;
  StreamSubscription<CoachingEvent>? _coachingSub;

  // Paddock detection
  DateTime? _slowSince;
  static const _paddockSpeedThresholdMph = 5.0;
  static const _paddockDurationSec = 30;

  @override
  void initState() {
    super.initState();

    // Force landscape + keep screen on when coaching
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
    SystemChrome.setPreferredOrientations([
      DeviceOrientation.landscapeLeft,
      DeviceOrientation.landscapeRight,
    ]);

    _gripAnimCtrl = AnimationController(
      vsync: this, duration: const Duration(milliseconds: 80));
    _redAnimCtrl = AnimationController(
      vsync: this, duration: const Duration(milliseconds: 80));
    _coachingAnimCtrl = AnimationController(
      vsync: this, duration: const Duration(milliseconds: 300));

    _gripAnim = Tween<double>(begin: 1.0, end: 1.0).animate(
      CurvedAnimation(parent: _gripAnimCtrl, curve: Curves.easeOut));
    _redAnim = Tween<double>(begin: 0.0, end: 0.0).animate(
      CurvedAnimation(parent: _redAnimCtrl, curve: Curves.easeOut));
    _coachingOpacity = Tween<double>(begin: 0.0, end: 1.0).animate(
      CurvedAnimation(parent: _coachingAnimCtrl, curve: Curves.easeInOut));

    // Subscribe to telemetry
    _telemetrySub = PitwallChannel.telemetry.listen(
      _onFrame,
      onError: (e) => debugPrint('Telemetry stream error: $e'),
    );
    _coachingSub = PitwallChannel.coaching.listen(
      _onCoaching,
      onError: (e) => debugPrint('Coaching stream error: $e'),
    );
  }

  void _onFrame(TelemetryState frame) {
    if (!mounted) return;

    // Animate grip bar (green)
    final gripAvailable = (1.0 - frame.gripUsage).clamp(0.0, 1.0);
    _gripAnim = Tween<double>(
      begin: _gripAnim.value, end: gripAvailable,
    ).animate(CurvedAnimation(parent: _gripAnimCtrl, curve: Curves.easeOut));
    _gripAnimCtrl.forward(from: 0);

    // Animate over-limit bar (red)
    final overLimit = (frame.gripUsage - 0.95).clamp(0.0, 0.3) / 0.3;
    _redAnim = Tween<double>(
      begin: _redAnim.value, end: overLimit,
    ).animate(CurvedAnimation(parent: _redAnimCtrl, curve: Curves.easeOut));
    _redAnimCtrl.forward(from: 0);

    setState(() { _frame = frame; });

    // Paddock mode detection
    if (frame.speedMph < _paddockSpeedThresholdMph) {
      _slowSince ??= DateTime.now();
      if (DateTime.now().difference(_slowSince!).inSeconds >= _paddockDurationSec) {
        widget.onEnterPaddock();
      }
    } else {
      _slowSince = null;
    }
  }

  void _onCoaching(CoachingEvent event) {
    if (!mounted) return;
    setState(() { _lastCoaching = event; });
    _coachingAnimCtrl.forward(from: 0).then((_) async {
      await Future.delayed(const Duration(seconds: 4));
      if (mounted) _coachingAnimCtrl.reverse();
    });
  }

  @override
  void dispose() {
    _telemetrySub?.cancel();
    _coachingSub?.cancel();
    _gripAnimCtrl.dispose();
    _redAnimCtrl.dispose();
    _coachingAnimCtrl.dispose();
    SystemChrome.setPreferredOrientations([]);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: PitwallColors.background,
      body: Stack(
        children: [
          // ── Main HUD: two vertical bars ────────────────────────────────────
          _SignalLightHud(
            gripAnim: _gripAnim,
            redAnim: _redAnim,
            frame: _frame,
          ),

          // ── Minimal status strip (top) ────────────────────────────────
          Positioned(
            top: 0, left: 0, right: 0,
            child: _StatusStrip(frame: _frame),
          ),


          // ── Corner indicator (bottom centre) ────────────────────────────
          Positioned(
            bottom: 24, left: 0, right: 0,
            child: _CornerIndicator(frame: _frame),
          ),

          // ── Coaching toast (appears briefly after delivery) ────────────────
          Positioned(
            bottom: 80, left: 40, right: 40,
            child: FadeTransition(
              opacity: _coachingOpacity,
              child: _CoachingToast(event: _lastCoaching),
            ),
          ),

          // ── Back to menu — LAST in stack so nothing overlaps its touch target
          Positioned(
            top: 0, left: 0,
            child: SafeArea(
              child: IconButton(
                icon: const Icon(Icons.home_outlined),
                color: PitwallColors.textDim,
                iconSize: 22,
                tooltip: 'Back to menu',
                style: IconButton.styleFrom(
                  backgroundColor: PitwallColors.surface.withValues(alpha: 0.6),
                  shape: const RoundedRectangleBorder(
                    borderRadius: BorderRadius.only(
                      bottomRight: Radius.circular(8),
                    ),
                  ),
                  minimumSize: const Size(48, 48),
                ),
                onPressed: () => _confirmReturnToSetup(context),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _confirmReturnToSetup(BuildContext context) async {
    // Restore UI chrome so dialog is visible
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: PitwallColors.surface,
        title: const Text('End session?',
            style: TextStyle(color: PitwallColors.textPrimary)),
        content: const Text('Return to the main menu and stop recording.',
            style: TextStyle(color: PitwallColors.textDim)),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('KEEP DRIVING',
                style: TextStyle(color: PitwallColors.gripGreen)),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('END SESSION',
                style: TextStyle(color: PitwallColors.gripRed)),
          ),
        ],
      ),
    );
    if (confirmed == true) {
      await PitwallChannel.stopSession();
      widget.onReturnToSetup();
    } else {
      // Re-enter immersive mode if they chose to keep driving
      SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
    }
  }
}

/// The two vertical signal bars — the primary visual output.
class _SignalLightHud extends StatelessWidget {
  final Animation<double> gripAnim;
  final Animation<double> redAnim;
  final TelemetryState frame;

  const _SignalLightHud({
    required this.gripAnim,
    required this.redAnim,
    required this.frame,
  });

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: Listenable.merge([gripAnim, redAnim]),
      builder: (context, _) {
        final grip = gripAnim.value;
        final over = redAnim.value;
        final nearLimit = frame.gripUsage > 0.80;

        return Row(
          children: [
            // Green bar (left) — grip available
            Expanded(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 48, vertical: 24),
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Expanded(
                      child: _VerticalBar(
                        fill: grip,
                        fillColor: nearLimit
                            ? PitwallColors.gripYellow
                            : PitwallColors.gripGreen,
                        emptyColor: PitwallColors.gripGreenDim,
                        glowColor: PitwallColors.gripGreen,
                        showGlow: grip > 0.7,
                      ),
                    ),
                    const SizedBox(height: 12),
                    Text(
                      'GRIP',
                      style: TextStyle(
                        color: PitwallColors.gripGreen.withValues(alpha: 0.6),
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                        letterSpacing: 3,
                      ),
                    ),
                  ],
                ),
              ),
            ),

            // Thin centre divider
            Container(width: 1, color: PitwallColors.border),

            // Red bar (right) — over limit
            Expanded(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 48, vertical: 24),
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Expanded(
                      child: _VerticalBar(
                        fill: over,
                        fillColor: PitwallColors.gripRed,
                        emptyColor: PitwallColors.gripRedDim.withValues(alpha: 0.3),
                        glowColor: PitwallColors.gripRed,
                        showGlow: over > 0.1,
                        fillFromTop: true,   // red bar fills from top
                      ),
                    ),
                    const SizedBox(height: 12),
                    Text(
                      'LIMIT',
                      style: TextStyle(
                        color: over > 0.05
                            ? PitwallColors.gripRed
                            : PitwallColors.textDim,
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                        letterSpacing: 3,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        );
      },
    );
  }
}

class _VerticalBar extends StatelessWidget {
  final double fill;           // 0.0–1.0
  final Color fillColor;
  final Color emptyColor;
  final Color glowColor;
  final bool showGlow;
  final bool fillFromTop;

  const _VerticalBar({
    required this.fill,
    required this.fillColor,
    required this.emptyColor,
    required this.glowColor,
    required this.showGlow,
    this.fillFromTop = false,
  });

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(builder: (context, constraints) {
      return Container(
        width: constraints.maxWidth,
        decoration: BoxDecoration(
          color: emptyColor,
          borderRadius: BorderRadius.circular(8),
          boxShadow: showGlow ? [
            BoxShadow(color: glowColor.withValues(alpha: 0.3), blurRadius: 24, spreadRadius: 4),
          ] : null,
        ),
        child: Align(
          alignment: fillFromTop ? Alignment.topCenter : Alignment.bottomCenter,
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 60),
            width: double.infinity,
            height: constraints.maxHeight * fill.clamp(0.0, 1.0),
            decoration: BoxDecoration(
              color: fillColor,
              borderRadius: BorderRadius.circular(8),
              gradient: LinearGradient(
                begin: fillFromTop ? Alignment.topCenter : Alignment.bottomCenter,
                end: fillFromTop ? Alignment.bottomCenter : Alignment.topCenter,
                colors: [fillColor, fillColor.withValues(alpha: 0.7)],
              ),
            ),
          ),
        ),
      );
    });
  }
}

/// Minimal status strip at top — just enough to confirm the system is alive.
class _StatusStrip extends StatelessWidget {
  final TelemetryState frame;
  const _StatusStrip({required this.frame});

  @override
  Widget build(BuildContext context) {
    return Container(
      color: Colors.transparent,
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 8),
      child: Row(
        children: [
          _Dot(color: PitwallColors.gripGreen, label: 'LIVE'),
          const Spacer(),
          Text(
            'LAP ${frame.lap}',
            style: const TextStyle(
              color: PitwallColors.textDim,
              fontSize: 12,
              fontWeight: FontWeight.w600,
              letterSpacing: 2,
            ),
          ),
          const SizedBox(width: 24),
          _LapTimer(seconds: frame.lapTime),
          const Spacer(),
          Text(
            '${frame.speedKmh.toInt()} km/h',
            style: const TextStyle(
              color: PitwallColors.textDim,
              fontSize: 12,
              fontWeight: FontWeight.w500,
              fontFamily: 'RobotoMono',
            ),
          ),
        ],
      ),
    );
  }
}

class _LapTimer extends StatelessWidget {
  final double seconds;
  const _LapTimer({required this.seconds});

  @override
  Widget build(BuildContext context) {
    final mins = (seconds / 60).floor();
    final secs = (seconds % 60).toStringAsFixed(1).padLeft(4, '0');
    return Text(
      '$mins:$secs',
      style: const TextStyle(
        color: PitwallColors.textDim,
        fontSize: 12,
        fontFamily: 'RobotoMono',
        letterSpacing: 1,
      ),
    );
  }
}

class _Dot extends StatelessWidget {
  final Color color;
  final String label;
  const _Dot({required this.color, required this.label});

  @override
  Widget build(BuildContext context) => Row(
    mainAxisSize: MainAxisSize.min,
    children: [
      Container(
        width: 6, height: 6,
        decoration: BoxDecoration(color: color, shape: BoxShape.circle,
          boxShadow: [BoxShadow(color: color.withValues(alpha: 0.6), blurRadius: 4)]),
      ),
      const SizedBox(width: 6),
      Text(label, style: TextStyle(color: color, fontSize: 10, letterSpacing: 2,
          fontWeight: FontWeight.w700)),
    ],
  );
}

class _CornerIndicator extends StatelessWidget {
  final TelemetryState frame;
  const _CornerIndicator({required this.frame});

  @override
  Widget build(BuildContext context) {
    if (!frame.inCorner && frame.cornerProximity > 200) return const SizedBox.shrink();
    final label = frame.currentCorner ?? '${frame.cornerProximity.toInt()}m';
    final color = frame.inCorner ? PitwallColors.gripYellow : PitwallColors.textDim;
    return Center(
      child: Text(
        label.toUpperCase(),
        style: TextStyle(color: color, fontSize: 13, letterSpacing: 3,
            fontWeight: FontWeight.w700),
      ),
    );
  }
}

class _CoachingToast extends StatelessWidget {
  final CoachingEvent? event;
  const _CoachingToast({this.event});

  @override
  Widget build(BuildContext context) {
    if (event == null) return const SizedBox.shrink();
    final borderColor = switch (event!.priority) {
      3 => PitwallColors.gripRed,
      2 => PitwallColors.gripYellow,
      _ => PitwallColors.speedBlue,
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
      decoration: BoxDecoration(
        color: PitwallColors.surface.withValues(alpha: 0.92),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: borderColor, width: 1.5),
        boxShadow: [BoxShadow(color: borderColor.withValues(alpha: 0.2), blurRadius: 16)],
      ),
      child: Text(
        event!.text,
        textAlign: TextAlign.center,
        style: TextStyle(
          color: PitwallColors.textPrimary,
          fontSize: 15,
          fontWeight: FontWeight.w500,
          height: 1.4,
        ),
      ),
    );
  }
}
