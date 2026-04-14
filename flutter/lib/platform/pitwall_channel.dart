import 'dart:async';
import 'package:flutter/services.dart';

/// Platform channel bridge — Dart side.
/// Connects Flutter UI to the native Kotlin engine via MethodChannel + EventChannels.
class PitwallChannel {
  static const _control = MethodChannel('com.pitwall.app/control');
  static const _telemetryChannel = EventChannel('com.pitwall.app/telemetry');
  static const _coachingChannel = EventChannel('com.pitwall.app/coaching');

  // Cached streams (lazy singletons)
  static Stream<TelemetryState>? _telemetryStream;
  static Stream<CoachingEvent>? _coachingStream;

  static Stream<TelemetryState> get telemetry {
    _telemetryStream ??= _telemetryChannel
        .receiveBroadcastStream()
        .map((data) => TelemetryState.fromMap(Map<String, dynamic>.from(data as Map)));
    return _telemetryStream!;
  }

  static Stream<CoachingEvent> get coaching {
    _coachingStream ??= _coachingChannel
        .receiveBroadcastStream()
        .map((data) => CoachingEvent.fromMap(Map<String, dynamic>.from(data as Map)));
    return _coachingStream!;
  }

  static Future<void> startSession({String? replayPath}) async {
    await _control.invokeMethod('startSession', {'replayPath': replayPath});
  }

  static Future<void> stopSession() async {
    await _control.invokeMethod('stopSession');
    // Reset cached streams so next session gets a fresh EventChannel subscription.
    // Without this the dead stream (closed by Android on stop) is reused and
    // no telemetry events ever arrive after returning to the setup screen.
    _telemetryStream = null;
    _coachingStream  = null;
  }

  static Future<void> setDriverLevel(String level) async {
    await _control.invokeMethod('setDriverLevel', {'level': level});
  }

  static Future<Map<String, dynamic>?> getSessionStats() async {
    final result = await _control.invokeMethod('getSessionStats');
    return result != null ? Map<String, dynamic>.from(result as Map) : null;
  }

  static Future<bool> isOnline() async {
    return await _control.invokeMethod('isOnline') ?? false;
  }
}

/// Dart model matching TelemetryFrame.toChannelMap() on the Kotlin side.
class TelemetryState {
  final double timestamp;
  final double speedMs;
  final double speedKmh;
  final double speedMph;
  final double gLat;
  final double gLong;
  final double comboG;
  final double gripUsage;      // comboG / MAX_COMBO_G
  final double throttle;
  final double brake;
  final double rpm;
  final double steering;
  final double coolantTemp;
  final double oilTemp;
  final int gear;
  final int lap;
  final double lapTime;
  final double distance;
  final double cornerProximity;
  final String? currentCorner;
  final bool pastApex;
  final String? sector;
  final bool inCorner;
  final bool isCoasting;
  final double speedConf;
  final double gLatConf;
  final double brakeConf;

  const TelemetryState({
    required this.timestamp,
    required this.speedMs,
    required this.speedKmh,
    required this.speedMph,
    required this.gLat,
    required this.gLong,
    required this.comboG,
    required this.gripUsage,
    required this.throttle,
    required this.brake,
    required this.rpm,
    required this.steering,
    required this.coolantTemp,
    required this.oilTemp,
    required this.gear,
    required this.lap,
    required this.lapTime,
    required this.distance,
    required this.cornerProximity,
    required this.currentCorner,
    required this.pastApex,
    required this.sector,
    required this.inCorner,
    required this.isCoasting,
    required this.speedConf,
    required this.gLatConf,
    required this.brakeConf,
  });

  factory TelemetryState.fromMap(Map<String, dynamic> m) => TelemetryState(
    timestamp: (m['timestamp'] as num).toDouble(),
    speedMs: (m['speedMs'] as num).toDouble(),
    speedKmh: (m['speedKmh'] as num).toDouble(),
    speedMph: (m['speedMph'] as num).toDouble(),
    gLat: (m['gLat'] as num).toDouble(),
    gLong: (m['gLong'] as num).toDouble(),
    comboG: (m['comboG'] as num).toDouble(),
    gripUsage: (m['gripUsage'] as num).toDouble(),
    throttle: (m['throttle'] as num).toDouble(),
    brake: (m['brake'] as num).toDouble(),
    rpm: (m['rpm'] as num).toDouble(),
    steering: (m['steering'] as num).toDouble(),
    coolantTemp: (m['coolantTemp'] as num).toDouble(),
    oilTemp: (m['oilTemp'] as num).toDouble(),
    gear: (m['gear'] as int),
    lap: (m['lap'] as int),
    lapTime: (m['lapTime'] as num).toDouble(),
    distance: (m['distance'] as num).toDouble(),
    cornerProximity: (m['cornerProximity'] as num).toDouble(),
    currentCorner: m['currentCorner'] as String?,
    pastApex: m['pastApex'] as bool,
    sector: m['sector'] as String?,
    inCorner: m['inCorner'] as bool,
    isCoasting: m['isCoasting'] as bool,
    speedConf: (m['speedConf'] as num).toDouble(),
    gLatConf: (m['gLatConf'] as num).toDouble(),
    brakeConf: (m['brakeConf'] as num).toDouble(),
  );

  static TelemetryState get empty => const TelemetryState(
    timestamp: 0, speedMs: 0, speedKmh: 0, speedMph: 0,
    gLat: 0, gLong: 0, comboG: 0, gripUsage: 0,
    throttle: 0, brake: 0, rpm: 0, steering: 0,
    coolantTemp: 85, oilTemp: 95, gear: 0, lap: 0, lapTime: 0,
    distance: 0, cornerProximity: 999, currentCorner: null, pastApex: false,
    sector: null, inCorner: false, isCoasting: false,
    speedConf: 0, gLatConf: 0, brakeConf: 0,
  );
}

class CoachingEvent {
  final String text;
  final int priority;
  final String source;
  final String? targetCorner;

  const CoachingEvent({
    required this.text,
    required this.priority,
    required this.source,
    this.targetCorner,
  });

  factory CoachingEvent.fromMap(Map<String, dynamic> m) => CoachingEvent(
    text: m['text'] as String,
    priority: m['priority'] as int,
    source: m['source'] as String,
    targetCorner: m['targetCorner'] as String?,
  );
}
