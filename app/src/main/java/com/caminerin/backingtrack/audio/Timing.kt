package com.caminerin.backingtrack.audio

import com.caminerin.backingtrack.model.Feel

const val SAMPLE_RATE = 44100

/**
 * Converts musical positions to sample positions.
 *
 * Two placement modes are exposed so the generators can choose the right grid:
 *  - [at]    : linear (no swing). Use for straight feels and precise placement.
 *  - [swung] : eighth-note swing (legacy helper, kept for compatibility).
 *  - [trip]  : exact triplet subdivision. Use for shuffle / 12-8 grooves so the
 *              hi-hat lands on the 1st and 3rd triplet and ghost notes can sit on
 *              the middle triplet — the authentic shuffle feel.
 */
class Timing(val bpm: Int, val beatsPerBar: Int, val feel: Feel) {

    val samplesPerBeat: Int = (SAMPLE_RATE * 60.0 / bpm).toInt()
    val samplesPerBar: Int = samplesPerBeat * beatsPerBar

    /** True when eighth notes should be played with a triplet/shuffle swing. */
    val isSwung: Boolean = feel == Feel.SHUFFLE || feel == Feel.SWING || feel == Feel.TWELVE_EIGHT

    /**
     * How "slow" the tempo is, 0 (fast, >=140 bpm) .. 1 (slow, <=60 bpm).
     * Generators use this to add subdivisions/ornaments at slow tempos and play
     * sparser at fast tempos, so the groove adapts to the tempo instead of being
     * the same pattern just sped up.
     */
    val slowness: Float = ((140f - bpm) / 80f).coerceIn(0f, 1f)

    /**
     * Swing ratio for the off-beat (the 'and' of each beat). Shuffle/12-8 are a
     * hard triplet; jazz swing eases toward straight as the tempo climbs.
     */
    private val swingRatio: Double = when (feel) {
        Feel.SHUFFLE -> 0.667   // triplet feel
        Feel.TWELVE_EIGHT -> 0.667
        Feel.SWING -> (0.667 - ((bpm - 110).coerceIn(-40, 80)) * 0.0010).coerceIn(0.56, 0.667)
        else -> 0.5             // straight
    }

    /** Linear sample position for a fractional beat (no swing). */
    fun at(barIndex: Int, beat: Double): Int =
        barIndex * samplesPerBar + (beat * samplesPerBeat).toInt()

    /**
     * Sample position for triplet [t] (0,1,2) of integer [beat].
     * t=0 → on the beat, t=1 → middle triplet, t=2 → last triplet.
     */
    fun trip(barIndex: Int, beat: Int, t: Int): Int =
        barIndex * samplesPerBar + beat * samplesPerBeat + (t * samplesPerBeat) / 3

    /**
     * Place an eighth-note subdivision honouring the feel: when swung, the
     * up-beat ('and') becomes the last triplet; when straight it sits exactly
     * halfway. [beat] is the integer beat, [up] selects the off-beat.
     */
    fun eighth(barIndex: Int, beat: Int, up: Boolean): Int {
        val base = barIndex * samplesPerBar + beat * samplesPerBeat
        return if (!up) base
        else base + (swingRatio * samplesPerBeat).toInt()
    }

    /** Absolute sample for a position given in beats (swung eighths). Legacy. */
    fun swung(barIndex: Int, beat: Double): Int {
        val barStart = barIndex * samplesPerBar
        val whole = beat.toInt()
        val frac = beat - whole
        val beatStart = barStart + whole * samplesPerBeat
        val offset = if (frac <= 0.5) {
            (frac / 0.5) * (swingRatio * samplesPerBeat)
        } else {
            swingRatio * samplesPerBeat + ((frac - 0.5) / 0.5) * ((1 - swingRatio) * samplesPerBeat)
        }
        return beatStart + offset.toInt()
    }

    /** Backwards-compatible alias used by older callers. */
    fun sampleAt(barIndex: Int, beat: Double): Int = swung(barIndex, beat)
}
