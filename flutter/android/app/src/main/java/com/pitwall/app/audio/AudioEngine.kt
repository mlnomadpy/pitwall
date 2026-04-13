package com.pitwall.app.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.pitwall.app.data.AudioCue
import com.pitwall.app.data.Pattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.sin

private const val TAG = "AudioEngine"
private const val SAMPLE_RATE = 44_100

/**
 * Audio Engine — two output channels:
 *   1. TTS (coaching messages) → Pixel Earbuds via Bluetooth A2DP
 *   2. Sonic tones (grip/brake/trail) → AudioTrack PCM float synthesis
 *
 * Sonic tone synthesis: pure sine waves mixed from active AudioCues.
 * Tone buffer is updated every 100ms (10Hz, matching telemetry rate).
 */
class AudioEngine(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var currentTrack: AudioTrack? = null
    private var isSpeaking = false

    // ── TTS ───────────────────────────────────────────────────────────────────

    fun initializeTts(onReady: () -> Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.apply {
                    language = Locale.US
                    setSpeechRate(1.1f)   // slightly faster for race conditions
                    setPitch(1.0f)
                }
                ttsReady = true
                Log.i(TAG, "TTS ready")
                onReady()
            } else {
                Log.e(TAG, "TTS init failed: $status")
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { isSpeaking = true }
            override fun onDone(utteranceId: String?) { isSpeaking = false }
            override fun onError(utteranceId: String?) { isSpeaking = false }
        })
    }

    /** Speak a coaching message. Interrupts any lower priority speech. */
    fun speak(text: String, priority: Int) {
        if (!ttsReady) { Log.w(TAG, "TTS not ready — dropping: $text"); return }
        val flushMode = if (priority >= 3) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts?.speak(text, flushMode, null, "cue_${System.currentTimeMillis()}")
    }

    // ── Sonic Tones ───────────────────────────────────────────────────────────

    /**
     * Play a collection of AudioCues mixed as a tone burst.
     * Cues are mixed additively (sum of sine waves, normalised to avoid clipping).
     * Duration: 100ms per frame.
     */
    suspend fun playTones(cues: List<AudioCue>) = withContext(Dispatchers.IO) {
        if (cues.all { it.pattern == Pattern.SILENT }) return@withContext

        val durationMs = 100
        val numSamples = SAMPLE_RATE * durationMs / 1000  // 4410 samples

        val buffer = FloatArray(numSamples)

        for (cue in cues) {
            if (cue.pattern == Pattern.SILENT || cue.frequency <= 0f) continue
            val amplitude = cue.volume * (1f / cues.size.coerceAtLeast(1))
            addTone(buffer, cue.frequency, amplitude, cue.pattern, numSamples)
        }

        // Normalise
        val maxAmp = buffer.maxOrNull()?.let { maxOf(it, 0.001f) } ?: 1f
        if (maxAmp > 0.95f) {
            val scale = 0.95f / maxAmp
            for (i in buffer.indices) buffer[i] *= scale
        }

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(numSamples * 4)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(buffer, 0, numSamples, AudioTrack.WRITE_NON_BLOCKING)
        track.play()
        currentTrack?.release()
        currentTrack = track
    }

    private fun addTone(
        buffer: FloatArray,
        frequency: Float,
        amplitude: Float,
        pattern: Pattern,
        numSamples: Int,
    ) {
        val angularFreq = 2.0 * Math.PI * frequency / SAMPLE_RATE.toDouble()

        for (i in 0 until numSamples) {
            val sample = (amplitude * sin(angularFreq * i)).toFloat()
            val envelope = when (pattern) {
                Pattern.PULSE -> pulseEnvelope(i, numSamples, pulseWidth = 0.3f)
                Pattern.FAST_PULSE -> pulseEnvelope(i, numSamples, pulseWidth = 0.15f)
                Pattern.SHARP -> sharpAttackEnvelope(i, numSamples)
                Pattern.BUZZ -> buzzEnvelope(i, numSamples)
                Pattern.CHIME_UP -> chimeUpEnvelope(i, numSamples)
                Pattern.CHIME_DOWN -> chimeDownEnvelope(i, numSamples)
                Pattern.CHIME_NEUTRAL -> chimeNeutralEnvelope(i, numSamples)
                Pattern.CONTINUOUS -> 1f
                Pattern.SILENT -> 0f
            }
            buffer[i] += sample * envelope
        }
    }

    // Amplitude envelopes
    private fun pulseEnvelope(i: Int, n: Int, pulseWidth: Float): Float {
        val t = i.toFloat() / n
        return if (t < pulseWidth) {
            // Attack
            sin(Math.PI.toFloat() * t / pulseWidth)
        } else 0f
    }

    private fun sharpAttackEnvelope(i: Int, n: Int): Float {
        val t = i.toFloat() / n
        return (1f - t).coerceAtLeast(0f)
    }

    private fun buzzEnvelope(i: Int, n: Int): Float {
        val t = i.toFloat() / n
        // Fast on/off oscillation (buzz texture)
        return if ((i / (n / 20)) % 2 == 0) 1f - t * 0.3f else 0.1f
    }

    private fun chimeUpEnvelope(i: Int, n: Int): Float {
        val t = i.toFloat() / n
        return sin(Math.PI.toFloat() * t) * (1f + t * 0.5f)
    }

    private fun chimeDownEnvelope(i: Int, n: Int): Float {
        val t = i.toFloat() / n
        return sin(Math.PI.toFloat() * t) * (1.5f - t * 0.5f)
    }

    private fun chimeNeutralEnvelope(i: Int, n: Int): Float {
        val t = i.toFloat() / n
        return sin(Math.PI.toFloat() * t)
    }

    fun release() {
        tts?.shutdown()
        currentTrack?.release()
        ttsReady = false
    }
}
