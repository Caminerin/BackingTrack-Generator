package com.caminerin.backingtrack.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.caminerin.backingtrack.model.Subdivision
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Click track layered on top of playback. Recomputes the interval every tick
 * so it follows live BPM changes; accents the first click of each beat.
 */
class Metronome(context: Context) {

    private val pool: SoundPool
    private var hiId = 0
    private var loId = 0
    private var loaded = 0

    private var job: Job? = null

    /** Provides the current effective BPM (track BPM after any tempo change). */
    var bpmProvider: () -> Int = { 120 }
    var subdivision: Subdivision = Subdivision.QUARTER

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attrs).build()
        pool.setOnLoadCompleteListener { _, _, _ -> loaded++ }
        hiId = pool.load(context.assets.openFd("click_hi.wav"), 1)
        loId = pool.load(context.assets.openFd("click_lo.wav"), 1)
    }

    val isRunning: Boolean get() = job?.isActive == true

    fun start(scope: CoroutineScope) {
        if (isRunning) return
        job = scope.launch(Dispatchers.Default) {
            var idx = 0
            while (isActive) {
                val perBeat = subdivision.perBeat
                val accent = idx % perBeat == 0
                if (loaded >= 2) {
                    if (accent) pool.play(hiId, 1f, 1f, 1, 0, 1f)
                    else pool.play(loId, 0.7f, 0.7f, 0, 0, 1f)
                }
                val bpm = bpmProvider().coerceAtLeast(20)
                val intervalMs = (60000.0 / (bpm * perBeat)).toLong().coerceAtLeast(20)
                idx = (idx + 1) % perBeat
                delay(intervalMs)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun release() {
        stop()
        pool.release()
    }
}
