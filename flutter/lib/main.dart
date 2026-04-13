import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:file_picker/file_picker.dart';
import 'platform/pitwall_channel.dart';
import 'screens/on_track_screen.dart';
import 'screens/paddock_screen.dart';
import 'theme/pitwall_theme.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  // Force landscape for the HUD
  SystemChrome.setPreferredOrientations([
    DeviceOrientation.landscapeLeft,
    DeviceOrientation.landscapeRight,
  ]);
  runApp(const PitwallApp());
}

class PitwallApp extends StatelessWidget {
  const PitwallApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Pitwall',
      debugShowCheckedModeBanner: false,
      theme: PitwallTheme.dark,
      home: const _SessionShell(),
    );
  }
}

/// Top-level shell that owns mode switching between On-Track HUD and Paddock.
class _SessionShell extends StatefulWidget {
  const _SessionShell();

  @override
  State<_SessionShell> createState() => _SessionShellState();
}

class _SessionShellState extends State<_SessionShell> {
  _AppMode _mode = _AppMode.setup;

  @override
  Widget build(BuildContext context) {
    return switch (_mode) {
      _AppMode.setup   => _SetupScreen(onStart: _onSessionStart),
      _AppMode.onTrack => OnTrackScreen(
        onEnterPaddock: _onEnterPaddock,
        onReturnToSetup: _onReturnToSetup,
      ),
      _AppMode.paddock => PaddockScreen(
        onReturnToTrack: _onReturnToTrack,
        onReturnToSetup: _onReturnToSetup,
      ),
    };
  }

  Future<void> _onSessionStart({String? replayPath, String level = 'intermediate'}) async {
    await PitwallChannel.startSession(replayPath: replayPath);
    await PitwallChannel.setDriverLevel(level);
    setState(() => _mode = _AppMode.onTrack);
  }

  void _onEnterPaddock() => setState(() => _mode = _AppMode.paddock);
  void _onReturnToTrack() => setState(() => _mode = _AppMode.onTrack);
  void _onReturnToSetup() => setState(() => _mode = _AppMode.setup);
}

enum _AppMode { setup, onTrack, paddock }

/// Session setup screen — shown on cold start before driving begins.
class _SetupScreen extends StatefulWidget {
  final Future<void> Function({String? replayPath, String level}) onStart;
  const _SetupScreen({required this.onStart});

  @override
  State<_SetupScreen> createState() => _SetupScreenState();
}

class _SetupScreenState extends State<_SetupScreen> {
  String _driverLevel = 'intermediate';
  bool _starting = false;
  bool _isOnline = false;

  @override
  void initState() {
    super.initState();
    _checkConnectivity();
  }

  Future<void> _checkConnectivity() async {
    final online = await PitwallChannel.isOnline();
    if (mounted) setState(() => _isOnline = online);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: PitwallColors.background,
      body: SingleChildScrollView(
        padding: const EdgeInsets.symmetric(vertical: 40),
        child: Center(
          child: SizedBox(
            width: 480,
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Logo
                RichText(
                  text: const TextSpan(children: [
                    TextSpan(text: 'PIT', style: TextStyle(
                      color: PitwallColors.gripGreen,
                      fontSize: 48, fontWeight: FontWeight.w900, letterSpacing: -2,
                    )),
                    TextSpan(text: 'WALL', style: TextStyle(
                      color: PitwallColors.textPrimary,
                      fontSize: 48, fontWeight: FontWeight.w900, letterSpacing: -2,
                    )),
                  ]),
                ),
                const SizedBox(height: 4),
                const Text(
                  'AI Racing Coach — Sonoma Raceway Sprint',
                  style: TextStyle(color: PitwallColors.textDim, fontSize: 12, letterSpacing: 1),
                ),

                const SizedBox(height: 48),
                const Text('DRIVER LEVEL', style: TextStyle(
                    color: PitwallColors.textDim, fontSize: 10, letterSpacing: 2)),
                const SizedBox(height: 8),
                _LevelSelector(
                  selected: _driverLevel,
                  onSelected: (l) => setState(() => _driverLevel = l),
                ),

                const SizedBox(height: 40),
                // Hardware status
                _HardwareStatus(isOnline: _isOnline),

                const SizedBox(height: 40),
                Row(children: [
                  // Live session
                  Expanded(
                    child: _PrimaryButton(
                      label: 'START SESSION',
                      icon: Icons.flag,
                      loading: _starting,
                      onPressed: () async {
                        setState(() => _starting = true);
                        await widget.onStart(level: _driverLevel);
                        if (mounted) setState(() => _starting = false);
                      },
                    ),
                  ),
                  const SizedBox(width: 16),
                  // Replay mode — opens file picker filtered to .vbo
                  _SecondaryButton(
                    label: 'REPLAY VBO',
                    icon: Icons.replay,
                    onPressed: () async {
                      final result = await FilePicker.platform.pickFiles(
                        dialogTitle: 'Select a .vbo telemetry file',
                        type: FileType.any,   // VBO has no registered MIME type
                        allowMultiple: false,
                      );

                      if (result == null || result.files.isEmpty) {
                        if (context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(
                              content: Text('No file selected.'),
                              backgroundColor: PitwallColors.surface,
                              duration: Duration(seconds: 2),
                            ),
                          );
                        }
                        return;
                      }

                      final path = result.files.single.path;
                      if (path == null) return;

                      if (!path.toLowerCase().endsWith('.vbo')) {
                        if (context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(
                              content: Text('Not a VBO file: ${result.files.single.name}'),
                              backgroundColor: PitwallColors.gripRed,
                              duration: const Duration(seconds: 3),
                            ),
                          );
                        }
                        return;
                      }

                      await widget.onStart(
                        replayPath: path,
                        level: _driverLevel,
                      );
                    },
                  ),
                ]),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _LevelSelector extends StatelessWidget {
  final String selected;
  final ValueChanged<String> onSelected;
  const _LevelSelector({required this.selected, required this.onSelected});

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        for (final level in ['beginner', 'intermediate', 'pro'])
          Padding(
            padding: const EdgeInsets.only(right: 8),
            child: ChoiceChip(
              label: Text(level.toUpperCase()),
              selected: selected == level,
              onSelected: (v) { if (v) onSelected(level); },
              selectedColor: PitwallColors.gripGreen.withValues(alpha: 0.2),
              backgroundColor: PitwallColors.surface,
              labelStyle: TextStyle(
                color: selected == level ? PitwallColors.gripGreen : PitwallColors.textDim,
                fontSize: 11, fontWeight: FontWeight.w700, letterSpacing: 1,
              ),
              side: BorderSide(
                color: selected == level ? PitwallColors.gripGreen : PitwallColors.border,
              ),
            ),
          ),
      ],
    );
  }
}

class _HardwareStatus extends StatelessWidget {
  final bool isOnline;
  const _HardwareStatus({required this.isOnline});

  @override
  Widget build(BuildContext context) {
    // These would be populated from Bluetooth status via a method channel
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text('HARDWARE', style: TextStyle(
            color: PitwallColors.textDim, fontSize: 10, letterSpacing: 2)),
        const SizedBox(height: 8),
        _StatusRow('Racelogic Mini', true),
        _StatusRow('OBDLink MX', true),
        _StatusRow('Pixel Earbuds', true),
        _StatusRow('5G / Vertex AI', isOnline),
      ],
    );
  }
}

class _StatusRow extends StatelessWidget {
  final String label;
  final bool connected;
  const _StatusRow(this.label, this.connected);

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 4),
    child: Row(
      children: [
        Icon(
          connected ? Icons.check_circle_outline : Icons.radio_button_unchecked,
          color: connected ? PitwallColors.gripGreen : PitwallColors.textDim,
          size: 16,
        ),
        const SizedBox(width: 10),
        Text(label, style: TextStyle(
          color: connected ? PitwallColors.textPrimary : PitwallColors.textDim,
          fontSize: 13,
        )),
      ],
    ),
  );
}

class _PrimaryButton extends StatelessWidget {
  final String label;
  final IconData icon;
  final bool loading;
  final VoidCallback onPressed;
  const _PrimaryButton({required this.label, required this.icon,
    required this.loading, required this.onPressed});

  @override
  Widget build(BuildContext context) => FilledButton.icon(
    onPressed: loading ? null : onPressed,
    icon: loading
        ? const SizedBox(width: 16, height: 16,
        child: CircularProgressIndicator(strokeWidth: 2, color: Colors.black))
        : Icon(icon, size: 18),
    label: Text(label, style: const TextStyle(
        fontWeight: FontWeight.w700, letterSpacing: 2, fontSize: 12)),
    style: FilledButton.styleFrom(
      backgroundColor: PitwallColors.gripGreen,
      foregroundColor: Colors.black,
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
    ),
  );
}

class _SecondaryButton extends StatelessWidget {
  final String label;
  final IconData icon;
  final VoidCallback onPressed;
  const _SecondaryButton({required this.label, required this.icon, required this.onPressed});

  @override
  Widget build(BuildContext context) => OutlinedButton.icon(
    onPressed: onPressed,
    icon: Icon(icon, size: 16),
    label: Text(label, style: const TextStyle(
        fontWeight: FontWeight.w700, letterSpacing: 2, fontSize: 11)),
    style: OutlinedButton.styleFrom(
      foregroundColor: PitwallColors.textSecondary,
      side: const BorderSide(color: PitwallColors.border),
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
    ),
  );
}
