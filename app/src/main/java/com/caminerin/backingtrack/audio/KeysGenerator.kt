package com.caminerin.backingtrack.audio

import com.caminerin.backingtrack.model.Feel
import com.caminerin.backingtrack.model.JamRecipe
import com.caminerin.backingtrack.model.Style
import com.caminerin.backingtrack.model.Chord
import kotlin.random.Random

/**
 * Generates piano comping. Plays sustained / rhythmic chord voicings in a register
 * above the bass and slightly apart from the rhythm guitar, supporting the harmony
 * without crowding the lead.
 */
class KeysGenerator(
    private val recipe: JamRecipe,
    private val timing: Timing,
    private val humanizer: Humanizer,
) {
    private val rng = Random(recipe.seed xor 0x4B455953)

    /** Piano voicing in the mid register (MIDI ~55-76). */
    private fun voicing(chord: Chord): List<Int> {
        val base = 55 + chord.rootSemitone
        val notes = chord.quality.intervals.map { base + it }
        return notes.map { n ->
            var x = n
            while (x > 76) x -= 12
            while (x < 55) x += 12
            x
        }.distinct().sorted()
    }

    private fun chordHit(out: MutableList<RenderEvent>, barIdx: Int, beat: Double, chord: Chord, vel: Float) {
        val notes = voicing(chord)
        val baseSample = timing.sampleAt(barIdx, beat) + humanizer.offsetSamples()
        // Small roll across the voicing for a natural feel.
        val roll = (SAMPLE_RATE * 0.006).toInt()
        for ((k, midi) in notes.withIndex()) {
            out.add(
                RenderEvent(
                    startSample = (baseSample + k * roll).coerceAtLeast(0),
                    midi = midi,
                    velocity = humanizer.scaleVelocity(vel),
                )
            )
        }
    }

    fun generate(bars: List<Conductor.BarPlan>): List<RenderEvent> {
        val out = ArrayList<RenderEvent>(bars.size * 6)
        for (bar in bars) {
            val chord = bar.chord
            val e = bar.energy
            when (recipe.style) {
                Style.FUNK -> {
                    // Stabs on syncopated points.
                    for (h in listOf(0.0, 1.5, 2.5, 3.5)) {
                        if (rng.nextFloat() < 0.7f) chordHit(out, bar.index, h, chord, 0.42f)
                    }
                }
                Style.CLASSIC_ROCK, Style.BLUES_ROCK -> {
                    chordHit(out, bar.index, 0.0, chord, 0.45f + 0.1f * e)
                    chordHit(out, bar.index, 2.0, chord, 0.4f)
                    if (e > 0.6f) chordHit(out, bar.index, 3.0, chord, 0.35f)
                }
                Style.BLUES -> {
                    chordHit(out, bar.index, 0.0, chord, 0.4f)
                    if (recipe.feel != Feel.STRAIGHT) chordHit(out, bar.index, 2.5, chord, 0.3f)
                }
                Style.SLOW_BALLAD -> {
                    // Sustained whole-note pad-like chords.
                    chordHit(out, bar.index, 0.0, chord, 0.4f)
                }
            }
        }
        return out
    }
}
