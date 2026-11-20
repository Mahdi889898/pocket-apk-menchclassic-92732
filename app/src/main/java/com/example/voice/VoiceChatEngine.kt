package com.example.voice

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure Android SDK real-time voice chat engine using 8kHz G.711 mu-law compression
 * (~10.7 KB/s Base64) + synthesized wooden tabletop sound effects.
 * Supports both Push-to-Talk (hold to talk) and Open-Mic Voice Activity Detection (VAD).
 */
class VoiceChatEngine(private val scope: CoroutineScope) {

    companion object {
        const val SAMPLE_RATE = 8000
        private const val TAG = "VoiceChatEngine"
        private const val MAX_PTT_SECONDS = 6
        private const val OPEN_MIC_CHUNK_MS = 1600
        private const val VAD_RMS_THRESHOLD = 420.0
    }

    private var recordJob: Job? = null
    private var openMicJob: Job? = null
    private var pttBuffer: ByteArrayOutputStream? = null
    private var pttStartMs: Long = 0L

    @Volatile
    var speakerMuted: Boolean = false

    @SuppressLint("MissingPermission")
    fun startPushToTalk(onAmplitude: (Float) -> Unit) {
        stopOpenMic()
        recordJob?.cancel()
        val buffer = ByteArrayOutputStream()
        pttBuffer = buffer
        pttStartMs = System.currentTimeMillis()

        recordJob = scope.launch(Dispatchers.IO) {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(1600)

            val recorder = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf * 2
                )
            } catch (e: Exception) {
                Log.w(TAG, "AudioRecord init failed: ${e.message}")
                return@launch
            }

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                return@launch
            }

            val pcmChunk = ShortArray(800) // 100ms chunks
            try {
                recorder.startRecording()
                val maxBytes = SAMPLE_RATE * MAX_PTT_SECONDS
                while (isActive && buffer.size() < maxBytes) {
                    val read = recorder.read(pcmChunk, 0, pcmChunk.size)
                    if (read > 0) {
                        var sumSq = 0.0
                        val encoded = ByteArray(read)
                        for (i in 0 until read) {
                            val s = pcmChunk[i]
                            sumSq += s.toDouble() * s.toDouble()
                            encoded[i] = linearToMuLaw(s)
                        }
                        synchronized(buffer) {
                            buffer.write(encoded)
                        }
                        val rms = sqrt(sumSq / read)
                        val normalized = (rms / 6500.0).toFloat().coerceIn(0f, 1f)
                        onAmplitude(normalized)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "PTT capture error: ${e.message}")
            } finally {
                try {
                    recorder.stop()
                } catch (_: Exception) {
                }
                recorder.release()
                onAmplitude(0f)
            }
        }
    }

    fun stopPushToTalkAndEncode(): Pair<String, Int>? {
        recordJob?.cancel()
        recordJob = null
        val buffer = pttBuffer ?: return null
        pttBuffer = null
        val bytes = synchronized(buffer) { buffer.toByteArray() }
        if (bytes.size < 1200) return null // shorter than 150ms
        val durationMs = ((bytes.size.toLong() * 1000L) / SAMPLE_RATE).toInt().coerceIn(150, 8000)
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return encoded to durationMs
    }

    @SuppressLint("MissingPermission")
    fun startOpenMic(
        onAmplitude: (Float) -> Unit,
        onVoiceChunkReady: (audioBase64: String, durationMs: Int) -> Unit
    ) {
        stopPushToTalkAndEncode()
        openMicJob?.cancel()

        openMicJob = scope.launch(Dispatchers.IO) {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(1600)

            val recorder = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf * 2
                )
            } catch (e: Exception) {
                Log.w(TAG, "Open-mic AudioRecord init failed: ${e.message}")
                return@launch
            }

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                return@launch
            }

            val pcmChunk = ShortArray(800) // 100ms frame
            val activeSegment = ByteArrayOutputStream()
            var speakingFrames = 0
            var silentTrailingFrames = 0
            val maxChunkBytes = (SAMPLE_RATE * OPEN_MIC_CHUNK_MS) / 1000

            try {
                recorder.startRecording()
                while (isActive) {
                    val read = recorder.read(pcmChunk, 0, pcmChunk.size)
                    if (read <= 0) continue

                    var sumSq = 0.0
                    val muBytes = ByteArray(read)
                    for (i in 0 until read) {
                        val s = pcmChunk[i]
                        sumSq += s.toDouble() * s.toDouble()
                        muBytes[i] = linearToMuLaw(s)
                    }
                    val rms = sqrt(sumSq / read)
                    onAmplitude((rms / 6500.0).toFloat().coerceIn(0f, 1f))

                    if (rms >= VAD_RMS_THRESHOLD) {
                        activeSegment.write(muBytes)
                        speakingFrames++
                        silentTrailingFrames = 0
                    } else if (speakingFrames > 0) {
                        activeSegment.write(muBytes)
                        silentTrailingFrames++
                    }

                    val reachedChunkLimit = activeSegment.size() >= maxChunkBytes
                    val finishedUtterance = speakingFrames >= 2 && silentTrailingFrames >= 4
                    if (reachedChunkLimit || finishedUtterance) {
                        val payloadBytes = activeSegment.toByteArray()
                        activeSegment.reset()
                        speakingFrames = 0
                        silentTrailingFrames = 0
                        if (payloadBytes.size >= 1600) {
                            val durationMs = ((payloadBytes.size.toLong() * 1000L) / SAMPLE_RATE)
                                .toInt().coerceIn(200, 8000)
                            val b64 = Base64.encodeToString(payloadBytes, Base64.NO_WRAP)
                            onVoiceChunkReady(b64, durationMs)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Open-mic capture error: ${e.message}")
            } finally {
                try {
                    recorder.stop()
                } catch (_: Exception) {
                }
                recorder.release()
                onAmplitude(0f)
            }
        }
    }

    fun stopOpenMic() {
        openMicJob?.cancel()
        openMicJob = null
    }

    suspend fun playVoiceBase64(audioBase64: String, sampleRate: Int = SAMPLE_RATE) {
        if (speakerMuted) return
        withContext(Dispatchers.IO) {
            try {
                val muBytes = Base64.decode(audioBase64, Base64.DEFAULT)
                if (muBytes.isEmpty()) return@withContext
                val pcm = ShortArray(muBytes.size) { idx -> muLawToLinear(muBytes[idx]) }
                playPcm(pcm, sampleRate, AudioAttributes.USAGE_VOICE_COMMUNICATION)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Voice playback error: ${e.message}")
            }
        }
    }

    fun playDiceRollSound() {
        if (speakerMuted) return
        scope.launch(Dispatchers.IO) {
            // Crisp wooden dice tumble clicks
            val rate = 16000
            val samples = ShortArray(rate / 3)
            val clickTimes = listOf(0.02, 0.08, 0.15, 0.23)
            for (tClick in clickTimes) {
                val startIdx = (tClick * rate).toInt()
                for (i in 0 until 450) {
                    val idx = startIdx + i
                    if (idx >= samples.size) break
                    val t = i.toDouble() / rate
                    val env = exp(-t * 95.0)
                    val tone = sin(2.0 * PI * 420.0 * t) + 0.5 * sin(2.0 * PI * 980.0 * t)
                    samples[idx] = (samples[idx] + (tone * env * 9000.0).toInt())
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
            }
            playPcm(samples, rate, AudioAttributes.USAGE_GAME)
        }
    }

    fun playPawnStepSound(isCapture: Boolean = false) {
        if (speakerMuted) return
        scope.launch(Dispatchers.IO) {
            val rate = 16000
            val len = if (isCapture) rate / 2 else rate / 6
            val samples = ShortArray(len)
            for (i in samples.indices) {
                val t = i.toDouble() / rate
                val env = exp(-t * (if (isCapture) 8.0 else 36.0))
                val freq = if (isCapture) {
                    if (t < 0.15) 523.25 else 783.99
                } else {
                    290.0
                }
                val v = sin(2.0 * PI * freq * t) * env * 11000.0
                samples[i] = v.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(samples, rate, AudioAttributes.USAGE_GAME)
        }
    }

    private fun playPcm(pcm: ShortArray, sampleRate: Int, usage: Int) {
        val byteLen = pcm.size * 2
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(byteLen)

        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(usage)
                        .setContentType(
                            if (usage == AudioAttributes.USAGE_VOICE_COMMUNICATION)
                                AudioAttributes.CONTENT_TYPE_SPEECH
                            else AudioAttributes.CONTENT_TYPE_SONIFICATION
                        )
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } catch (e: Exception) {
            Log.w(TAG, "AudioTrack init failed: ${e.message}")
            return
        }

        try {
            if (track.state == AudioTrack.STATE_INITIALIZED) {
                track.write(pcm, 0, pcm.size)
                track.play()
                val sleepMs = ((pcm.size.toLong() * 1000L) / sampleRate) + 40L
                Thread.sleep(sleepMs)
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            Log.w(TAG, "AudioTrack play error: ${e.message}")
        } finally {
            try {
                track.stop()
            } catch (_: Exception) {
            }
            track.release()
        }
    }

    fun release() {
        recordJob?.cancel()
        openMicJob?.cancel()
        recordJob = null
        openMicJob = null
        pttBuffer = null
    }

    // Standard ITU-T G.711 mu-law 16-bit PCM -> 8-bit encoder
    private fun linearToMuLaw(sample: Short): Byte {
        val bias = 0x84
        val clip = 32635
        var s = sample.toInt()
        val sign = if (s < 0) 0x80 else 0
        if (s < 0) s = -s
        if (s > clip) s = clip
        s += bias

        var exponent = 7
        var expMask = 0x4000
        while (exponent > 0 && (s and expMask) == 0) {
            exponent--
            expMask = expMask shr 1
        }
        val mantissa = (s shr (exponent + 3)) and 0x0F
        return (sign or (exponent shl 4) or mantissa).inv().toByte()
    }

    // Standard ITU-T G.711 mu-law 8-bit -> 16-bit PCM decoder
    private fun muLawToLinear(muByte: Byte): Short {
        val mu = muByte.toInt().inv() and 0xFF
        val sign = mu and 0x80
        val exponent = (mu shr 4) and 0x07
        val mantissa = mu and 0x0F
        var t = ((mantissa shl 3) + 0x84) shl exponent
        t -= 0x84
        val sample = if (sign != 0) -t else t
        return sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
}
