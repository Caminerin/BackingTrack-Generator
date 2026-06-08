package com.caminerin.backingtrack.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import kotlin.math.pow

/**
 * Plays one backing track from assets. Tempo (BPM) and key (transpose) are
 * changed independently in real time via [PlaybackParams] (speed + pitch),
 * which Android implements with a time-stretch/pitch-shift engine, so changing
 * the key does not change the speed and vice-versa.
 */
class BackingPlayer(private val context: Context) {

    private var mp: MediaPlayer? = null
    private var originalBpm: Int = 120
    private var semitones: Int = 0
    private var targetBpm: Int = 120
    private var loop: Boolean = false
    private var wantPlaying: Boolean = false

    var onReady: (() -> Unit)? = null
    var onCompletion: (() -> Unit)? = null

    val isReady: Boolean get() = mp != null && prepared
    private var prepared = false

    fun load(assetName: String, bpm: Int) {
        release()
        originalBpm = bpm
        targetBpm = bpm
        semitones = 0
        prepared = false
        val player = MediaPlayer()
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        context.assets.openFd(assetName).use { afd ->
            player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
        }
        player.setOnPreparedListener {
            prepared = true
            it.isLooping = loop
            applyParams()
            if (wantPlaying) it.start()
            onReady?.invoke()
        }
        player.setOnCompletionListener {
            if (!loop) {
                wantPlaying = false
                onCompletion?.invoke()
            }
        }
        player.prepareAsync()
        mp = player
    }

    private fun applyParams() {
        val player = mp ?: return
        if (!prepared) return
        val speed = targetBpm.toFloat() / originalBpm.toFloat()
        val pitch = 2.0.pow(semitones / 12.0).toFloat()
        val wasPlaying = player.isPlaying
        val params = (runCatching { player.playbackParams }.getOrNull() ?: PlaybackParams())
            .setSpeed(speed)
            .setPitch(pitch)
        player.playbackParams = params
        // Setting playbackParams can auto-start playback; honor the paused state.
        if (!wantPlaying && player.isPlaying && !wasPlaying) player.pause()
    }

    fun play() {
        wantPlaying = true
        val player = mp ?: return
        if (prepared && !player.isPlaying) player.start()
    }

    fun pause() {
        wantPlaying = false
        mp?.let { if (it.isPlaying) it.pause() }
    }

    fun togglePlay() = if (wantPlaying) pause() else play()

    val isPlaying: Boolean get() = wantPlaying

    fun setLoop(enabled: Boolean) {
        loop = enabled
        mp?.isLooping = enabled
    }

    fun loopEnabled() = loop

    fun setSemitones(n: Int) {
        semitones = n.coerceIn(-12, 12)
        applyParams()
    }

    fun semitones() = semitones

    fun setBpm(bpm: Int) {
        targetBpm = bpm.coerceIn(30, 300)
        applyParams()
    }

    fun currentBpm() = targetBpm
    fun baseBpm() = originalBpm

    fun positionMs(): Int = runCatching { mp?.currentPosition ?: 0 }.getOrDefault(0)
    fun durationMs(): Int = runCatching { mp?.duration ?: 0 }.getOrDefault(0)

    fun seekTo(ms: Int) {
        mp?.seekTo(ms.coerceAtLeast(0))
    }

    fun release() {
        mp?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        mp = null
        prepared = false
        wantPlaying = false
    }
}
