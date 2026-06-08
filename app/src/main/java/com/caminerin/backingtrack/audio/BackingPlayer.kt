package com.caminerin.backingtrack.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import kotlin.math.abs
import kotlin.math.pow

/**
 * Plays a backing track made of one or more synchronized stems (separate audio
 * files: drums, bass, guitar, piano...). All stems share the same tempo (BPM)
 * and key (transpose), changed in real time via [PlaybackParams]. Individual
 * stems can be muted/un-muted without losing sync (muted stems keep playing at
 * volume 0). Drift between stems is corrected periodically via [resync].
 */
class BackingPlayer(private val context: Context) {

    private class Stem(
        val instrument: String,
        val player: MediaPlayer,
        var enabled: Boolean = true,
        var prepared: Boolean = false,
    )

    private val stems = ArrayList<Stem>()
    private var originalBpm: Int = 120
    private var semitones: Int = 0
    private var targetBpm: Int = 120
    private var loop: Boolean = false
    private var sectionActive: Boolean = false
    private var sectionStartMs: Int = 0
    private var sectionEndMs: Int = 0
    private var wantPlaying: Boolean = false
    private var readyFired = false

    var onReady: (() -> Unit)? = null
    var onCompletion: (() -> Unit)? = null

    private val allPrepared: Boolean
        get() = stems.isNotEmpty() && stems.all { it.prepared }

    val isReady: Boolean get() = allPrepared

    /** [items] = list of (instrument label, asset file name). */
    fun load(items: List<Pair<String, String>>, bpm: Int) {
        release()
        originalBpm = bpm
        targetBpm = bpm
        semitones = 0
        readyFired = false
        items.forEachIndexed { index, (instrument, assetName) ->
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
            val stem = Stem(instrument, player)
            val isMaster = index == 0
            player.setOnPreparedListener {
                stem.prepared = true
                it.isLooping = loop
                it.setVolume(if (stem.enabled) 1f else 0f, if (stem.enabled) 1f else 0f)
                onAllMaybePrepared()
            }
            if (isMaster) {
                player.setOnCompletionListener {
                    if (!loop) {
                        wantPlaying = false
                        onCompletion?.invoke()
                    }
                }
            }
            player.prepareAsync()
            stems.add(stem)
        }
    }

    private fun onAllMaybePrepared() {
        if (!allPrepared || readyFired) return
        readyFired = true
        applyParams()
        if (wantPlaying) startAll()
        onReady?.invoke()
    }

    private fun startAll() {
        // Start every stem as close together as possible.
        stems.forEach { if (!it.player.isPlaying) it.player.start() }
        resync(force = true)
    }

    private fun applyParams() {
        if (!allPrepared) return
        val speed = targetBpm.toFloat() / originalBpm.toFloat()
        val pitch = 2.0.pow(semitones / 12.0).toFloat()
        stems.forEach { stem ->
            val p = stem.player
            val wasPlaying = p.isPlaying
            val params = (runCatching { p.playbackParams }.getOrNull() ?: PlaybackParams())
                .setSpeed(speed)
                .setPitch(pitch)
            p.playbackParams = params
            if (!wantPlaying && p.isPlaying && !wasPlaying) p.pause()
        }
    }

    /** Re-aligns secondary stems to the master if they have drifted. */
    fun resync(force: Boolean = false) {
        if (!allPrepared || stems.size < 2) return
        val master = stems[0].player
        if (!master.isPlaying && !force) return
        val pos = runCatching { master.currentPosition }.getOrDefault(0)
        for (i in 1 until stems.size) {
            val p = stems[i].player
            val sp = runCatching { p.currentPosition }.getOrDefault(0)
            if (force || abs(sp - pos) > 120) {
                runCatching { p.seekTo(pos) }
            }
        }
    }

    fun play() {
        wantPlaying = true
        if (allPrepared) startAll()
    }

    fun pause() {
        wantPlaying = false
        stems.forEach { if (it.player.isPlaying) it.player.pause() }
    }

    fun togglePlay() = if (wantPlaying) pause() else play()

    val isPlaying: Boolean get() = wantPlaying

    fun setLoop(enabled: Boolean) {
        loop = enabled
        // Native whole-file looping; disabled while an A-B section loop is active.
        stems.forEach { it.player.isLooping = enabled && !sectionActive }
    }

    fun loopEnabled() = loop

    /** Repeat only the [startMs]..[endMs] region. */
    fun setSectionLoop(startMs: Int, endMs: Int) {
        sectionStartMs = startMs.coerceAtLeast(0)
        sectionEndMs = endMs.coerceAtLeast(startMs + 200)
        sectionActive = true
        stems.forEach { it.player.isLooping = false }
    }

    fun clearSectionLoop() {
        sectionActive = false
        stems.forEach { it.player.isLooping = loop }
    }

    /** Called periodically: jumps back to A when reaching B. */
    fun maybeLoopSection() {
        if (!sectionActive || !wantPlaying) return
        if (positionMs() >= sectionEndMs) seekTo(sectionStartMs)
    }

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

    fun stemInstruments(): List<String> = stems.map { it.instrument }
    fun stemEnabled(): List<Boolean> = stems.map { it.enabled }

    fun setStemEnabled(index: Int, enabled: Boolean) {
        val stem = stems.getOrNull(index) ?: return
        stem.enabled = enabled
        val v = if (enabled) 1f else 0f
        runCatching { stem.player.setVolume(v, v) }
    }

    fun positionMs(): Int = runCatching { stems.firstOrNull()?.player?.currentPosition ?: 0 }.getOrDefault(0)
    fun durationMs(): Int = runCatching { stems.firstOrNull()?.player?.duration ?: 0 }.getOrDefault(0)

    fun seekTo(ms: Int) {
        val target = ms.coerceAtLeast(0)
        stems.forEach { runCatching { it.player.seekTo(target) } }
    }

    fun release() {
        stems.forEach {
            runCatching { it.player.stop() }
            runCatching { it.player.release() }
        }
        stems.clear()
        wantPlaying = false
        readyFired = false
    }
}
