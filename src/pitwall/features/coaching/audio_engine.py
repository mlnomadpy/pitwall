"""
Audio Engine — synthesizes tones from AudioCue objects, plus voice TTS.

Works on laptop (for development), Termux (for in-car phone use), and
plain Linux. Uses sounddevice for cross-platform tone output. Voice/TTS
is routed per-platform:

  - Termux (detected via /data/data/com.termux): subprocess termux-tts-speak
  - macOS:                                       subprocess `say`
  - Linux:                                       subprocess `espeak` if present
  - else:                                        prints to stdout

Falls back to silent mode if sounddevice is not available.
"""

import os
import shutil
import subprocess
import sys
import threading
import time
from collections import deque

try:
    import numpy as np
    import sounddevice as sd
    HAS_AUDIO = True
except ImportError:
    HAS_AUDIO = False


# ─── Platform detection ──────────────────────────────────────────────────────


def _detect_platform() -> str:
    if os.path.isdir("/data/data/com.termux") or os.environ.get("TERMUX_VERSION"):
        return "termux"
    if sys.platform == "darwin":
        return "macos"
    if sys.platform.startswith("linux"):
        return "linux"
    return "other"


PLATFORM = _detect_platform()


def _pick_tts():
    """Return a callable speak(text) -> None for this platform, or None."""
    if PLATFORM == "termux" and shutil.which("termux-tts-speak"):
        def speak(text: str):
            subprocess.Popen(
                ["termux-tts-speak", text],
                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
            )
        return speak
    if PLATFORM == "macos" and shutil.which("say"):
        def speak(text: str):
            subprocess.Popen(
                ["say", text],
                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
            )
        return speak
    if PLATFORM == "linux" and shutil.which("espeak"):
        def speak(text: str):
            subprocess.Popen(
                ["espeak", text],
                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
            )
        return speak
    return None


_TTS = _pick_tts()


class AudioEngine:
    """
    Real-time tone synthesizer. Receives AudioCue parameters and
    generates continuous audio output to the default audio device.
    """

    def __init__(self, sample_rate=44100, buffer_ms=50):
        self.sample_rate = sample_rate
        self.buffer_ms = buffer_ms
        self.buffer_size = int(sample_rate * buffer_ms / 1000)

        # Current tone state (updated by the main thread)
        self.layers = {}  # layer_name → {frequency, volume, pattern, phase}
        self.lock = threading.Lock()

        # Running state
        self.running = False
        self.stream = None

    def start(self):
        """Start audio output stream."""
        if not HAS_AUDIO:
            print("  Audio: sounddevice not available — silent mode")
            return

        self.running = True
        self.stream = sd.OutputStream(
            samplerate=self.sample_rate,
            channels=1,
            dtype='float32',
            blocksize=self.buffer_size,
            callback=self._audio_callback,
        )
        self.stream.start()
        print(f"  Audio: started ({self.sample_rate}Hz, {self.buffer_ms}ms buffer)")

    def stop(self):
        """Stop audio output."""
        self.running = False
        if self.stream:
            self.stream.stop()
            self.stream.close()
            self.stream = None

    def set_cues(self, cues):
        """
        Update active audio cues. Called from the main thread at 10Hz.
        Each cue becomes a layer in the audio mix.
        """
        with self.lock:
            new_layers = {}
            for cue in cues:
                layer = cue.layer
                # Preserve phase continuity if same layer continues
                old_phase = self.layers.get(layer, {}).get("phase", 0.0)

                new_layers[layer] = {
                    "frequency": cue.frequency,
                    "volume": cue.volume,
                    "pattern": cue.pattern.value if hasattr(cue.pattern, 'value') else str(cue.pattern),
                    "phase": old_phase,
                }
            self.layers = new_layers

    def silence(self):
        """Clear all active tones."""
        with self.lock:
            self.layers = {}

    def speak(self, text: str):
        """Speak a coaching line via the platform TTS. Non-blocking.
        On platforms without a TTS, falls back to stdout so the demo path
        still surfaces what would have been said.
        """
        if not text:
            return
        if _TTS is not None:
            try:
                _TTS(text)
                return
            except Exception:
                pass
        print(f"  \033[96m[voice]\033[0m {text}")

    def _audio_callback(self, outdata, frames, time_info, status):
        """Called by sounddevice to fill the audio buffer."""
        if not self.running:
            outdata[:] = 0
            return

        buf = np.zeros(frames, dtype=np.float32)
        t = np.arange(frames) / self.sample_rate

        with self.lock:
            for name, layer in self.layers.items():
                freq = layer["frequency"]
                vol = layer["volume"]
                pattern = layer["pattern"]
                phase = layer["phase"]

                if freq <= 0 or vol <= 0:
                    continue

                # Generate waveform based on pattern
                if pattern == "continuous":
                    wave = np.sin(2 * np.pi * freq * t + phase) * vol

                elif pattern == "pulse":
                    # 3Hz pulse (on 60%, off 40%)
                    pulse_freq = 3.0
                    envelope = (np.sin(2 * np.pi * pulse_freq * t + phase * pulse_freq / freq) > -0.2).astype(np.float32)
                    wave = np.sin(2 * np.pi * freq * t + phase) * vol * envelope

                elif pattern == "fast_pulse":
                    # 8Hz pulse
                    pulse_freq = 8.0
                    envelope = (np.sin(2 * np.pi * pulse_freq * t + phase * pulse_freq / freq) > -0.2).astype(np.float32)
                    wave = np.sin(2 * np.pi * freq * t + phase) * vol * envelope

                elif pattern == "buzz":
                    # Square wave — harsh warning
                    wave = np.sign(np.sin(2 * np.pi * freq * t + phase)) * vol * 0.5

                elif pattern in ("chime_up", "chime_down", "chime_neutral"):
                    # Short chime — play for 300ms then fade
                    duration_samples = int(0.3 * self.sample_rate)
                    envelope = np.zeros(frames, dtype=np.float32)
                    envelope[:min(duration_samples, frames)] = 1.0
                    # Fade out last 30%
                    fade_start = int(duration_samples * 0.7)
                    fade_len = duration_samples - fade_start
                    if fade_len > 0 and fade_start < frames:
                        fade_end = min(fade_start + fade_len, frames)
                        envelope[fade_start:fade_end] = np.linspace(1, 0, fade_end - fade_start)
                    wave = np.sin(2 * np.pi * freq * t + phase) * vol * envelope

                else:
                    wave = np.zeros(frames, dtype=np.float32)

                buf += wave

                # Update phase for continuity
                layer["phase"] = phase + 2 * np.pi * freq * frames / self.sample_rate

        # Clip to prevent distortion
        np.clip(buf, -0.8, 0.8, out=buf)
        outdata[:, 0] = buf


class SilentAudioEngine:
    """Fallback when sounddevice is not available. Logs cues + voice to console.
    Voice still routes through the platform TTS — only tones are silenced."""

    def start(self):
        tts = "TTS" if _TTS is not None else "stdout"
        print(f"  Audio: silent tones (no sounddevice). Voice → {tts} on {PLATFORM}.")

    def stop(self):
        pass

    def set_cues(self, cues):
        pass

    def silence(self):
        pass

    def speak(self, text: str):
        if not text:
            return
        if _TTS is not None:
            try:
                _TTS(text)
                return
            except Exception:
                pass
        print(f"  \033[96m[voice]\033[0m {text}")


def create_audio_engine():
    """Factory: returns real or silent engine based on availability."""
    if HAS_AUDIO:
        return AudioEngine()
    return SilentAudioEngine()
