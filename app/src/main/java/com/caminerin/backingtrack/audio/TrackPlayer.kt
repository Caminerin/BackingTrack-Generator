package com.caminerin.backingtrack.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Plays a set of mono instrument stems, mixing them live with per-stem gain
 * (volume / mute) and stereo pan. Volume and mute changes therefore take effect
 * instantly without re-rendering. Loops the body only (the count-in is heard once).
 */
class TrackPlayer {

    /** One mono instrument bus with its (volatile) live gain and stereo placement. */
    class Stem(val name: String, val mono: FloatArray, val lpan: Float, val rpan: Float) {
        @Volatile var gain: Float = 0f
    }

    private var track: AudioTrack? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    @Volatile var isPlaying: Boolean = false
        private set
    @Volatile var loop: Boolean = true
    @Volatile private var positionFrames: Int = 0

    var onPosition: ((Float) -> Unit)? = null
    var onComplete: (() -> Unit)? = null
    var onError: ((Throwable) -> Unit)? = null

    @Volatile private var stems: List<Stem> = emptyList()
    private var countIn: FloatArray = FloatArray(0)
    private var totalFrames: Int = 0
    private var loopStartFrame: Int = 0
    private var sampleRate: Int = SAMPLE_RATE

    fun load(result: Renderer.RenderResult, gains: Map<String, Float>) {
        stop()
        stems = result.stems.map { (name, buf) ->
            val (lp, rp) = Renderer.panGains(result.pan[name] ?: 0f)
            Stem(name, buf, lp, rp).also { it.gain = gains[name] ?: 0f }
        }
        countIn = result.countIn
        totalFrames = result.totalSamples
        loopStartFrame = result.bodyStart.coerceIn(0, result.totalSamples)
        sampleRate = result.sampleRate
        positionFrames = 0
    }

    /** Live update of an instrument's gain (0 = muted). */
    fun setGain(name: String, gain: Float) {
        stems.firstOrNull { it.name == name }?.gain = gain
    }

    fun play() {
        if (isPlaying || stems.isEmpty() || totalFrames == 0) return

        // Prefer 32-bit float output; fall back to 16-bit PCM on devices that
        // don't support a float AudioTrack (avoids a hard crash on play).
        val floatResult = runCatching { buildTrack(AudioFormat.ENCODING_PCM_FLOAT) }
        val useFloat = floatResult.isSuccess
        val at: AudioTrack = floatResult.getOrElse { buildTrack(AudioFormat.ENCODING_PCM_16BIT) }
        track = at
        at.play()
        isPlaying = true

        val activeStems = stems
        val ci = countIn
        val total = totalFrames
        job = scope.launch {
            try {
                val chunkFrames = 2048
                val fchunk = FloatArray(chunkFrames * 2)
                val schunk = ShortArray(chunkFrames * 2)
                var frame = positionFrames
                while (isActive) {
                    var n = 0
                    while (n < chunkFrames && frame < total) {
                        var l = 0f
                        var r = 0f
                        for (st in activeStems) {
                            val g = st.gain
                            if (g != 0f) {
                                val v = st.mono[frame] * g
                                l += v * st.lpan
                                r += v * st.rpan
                            }
                        }
                        if (frame < ci.size) {
                            l += ci[frame]
                            r += ci[frame]
                        }
                        l = Renderer.softClip(l)
                        r = Renderer.softClip(r)
                        if (useFloat) {
                            fchunk[n * 2] = l
                            fchunk[n * 2 + 1] = r
                        } else {
                            schunk[n * 2] = (l.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                            schunk[n * 2 + 1] = (r.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                        }
                        n++
                        frame++
                    }
                    if (n > 0) {
                        if (useFloat) {
                            at.write(fchunk, 0, n * 2, AudioTrack.WRITE_BLOCKING)
                        } else {
                            at.write(schunk, 0, n * 2, AudioTrack.WRITE_BLOCKING)
                        }
                        positionFrames = frame
                        onPosition?.invoke(frame.toFloat() / total)
                    }
                    if (frame >= total) {
                        if (loop) {
                            frame = loopStartFrame
                            positionFrames = loopStartFrame
                        } else {
                            break
                        }
                    }
                }
            } catch (t: Throwable) {
                onError?.invoke(t)
            } finally {
                isPlaying = false
                onComplete?.invoke()
            }
        }
    }

    private fun buildTrack(encoding: Int): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            encoding,
        ).coerceAtLeast(8192)
        val at = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(encoding)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build(),
            minBuf,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        )
        if (at.state != AudioTrack.STATE_INITIALIZED) {
            at.release()
            throw IllegalStateException("AudioTrack not initialized for encoding=$encoding")
        }
        return at
    }

    fun pause() {
        isPlaying = false
        job?.cancel()
        job = null
        track?.pause()
    }

    fun stop() {
        isPlaying = false
        job?.cancel()
        job = null
        positionFrames = 0
        track?.let {
            try {
                it.pause()
                it.flush()
                it.stop()
            } catch (_: IllegalStateException) {
            }
            it.release()
        }
        track = null
    }

    fun seekTo(fraction: Float) {
        positionFrames = (fraction.coerceIn(0f, 1f) * totalFrames).toInt()
    }

    fun release() {
        stop()
        scope.cancel()
    }
}
