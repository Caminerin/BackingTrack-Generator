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
 * Plays an interleaved-stereo float PCM buffer via AudioTrack, with looping and
 * play-head reporting. Streaming write so we can loop a long buffer cheaply.
 */
class TrackPlayer {

    private var track: AudioTrack? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    @Volatile var isPlaying: Boolean = false
        private set
    @Volatile var loop: Boolean = true
    @Volatile private var positionFrames: Int = 0

    var onPosition: ((Float) -> Unit)? = null
    var onComplete: (() -> Unit)? = null

    private var pcm: FloatArray = FloatArray(0)
    private var sampleRate: Int = SAMPLE_RATE

    fun load(interleaved: FloatArray, sampleRate: Int) {
        stop()
        this.pcm = interleaved
        this.sampleRate = sampleRate
    }

    fun play() {
        if (isPlaying || pcm.isEmpty()) return
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_FLOAT,
        ).coerceAtLeast(8192)

        val at = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build(),
            minBuf,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        )
        track = at
        at.play()
        isPlaying = true

        val totalFrames = pcm.size / 2
        job = scope.launch {
            val chunkFrames = 2048
            val chunk = FloatArray(chunkFrames * 2)
            var frame = positionFrames
            while (isActive) {
                var n = 0
                while (n < chunkFrames && frame < totalFrames) {
                    chunk[n * 2] = pcm[frame * 2]
                    chunk[n * 2 + 1] = pcm[frame * 2 + 1]
                    n++
                    frame++
                }
                if (n > 0) {
                    at.write(chunk, 0, n * 2, AudioTrack.WRITE_BLOCKING)
                    positionFrames = frame
                    onPosition?.invoke(frame.toFloat() / totalFrames)
                }
                if (frame >= totalFrames) {
                    if (loop) {
                        frame = 0
                        positionFrames = 0
                    } else {
                        break
                    }
                }
            }
            isPlaying = false
            onComplete?.invoke()
        }
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
        val totalFrames = pcm.size / 2
        positionFrames = (fraction.coerceIn(0f, 1f) * totalFrames).toInt()
    }

    fun release() {
        stop()
        scope.cancel()
    }
}
