package com.caminerin.backingtrack.audio

import com.caminerin.backingtrack.model.Feel

const val SAMPLE_RATE = 44100

/**
 * Converts musical positions to sample positions and applies swing/shuffle.
 */
class Timing(val bpm: Int, val beatsPerBar: Int, val feel: Feel) {

    val samplesPerBeat: Int = (SAMPLE_RATE * 60.0 / bpm).toInt()
    val samplesPerBar: Int = samplesPerBeat * beatsPerBar

    /** Swing ratio for the off-beat (the 'and' of each beat). */
    private val swingRatio: Double = when (feel) {
        Feel.SHUFFLE -> 0.667   // triplet feel
        Feel.SWING -> 0.62
        Feel.TWELVE_EIGHT -> 0.667
        else -> 0.5             // straight
    }

    /** Absolute sample for a position given in beats (can be fractional). */
    fun sampleAt(barIndex: Int, beat: Double): Int {
        val barStart = barIndex * samplesPerBar
        val whole = beat.toInt()
        val frac = beat - whole
        val beatStart = barStart + whole * samplesPerBeat
        // Apply swing to the eighth-note subdivision.
        val offset = if (frac <= 0.5) {
            (frac / 0.5) * (swingRatio * samplesPerBeat)
        } else {
            swingRatio * samplesPerBeat + ((frac - 0.5) / 0.5) * ((1 - swingRatio) * samplesPerBeat)
        }
        return beatStart + offset.toInt()
    }
}
