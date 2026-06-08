package com.caminerin.backingtrack.audio

import com.caminerin.backingtrack.model.Feel
import com.caminerin.backingtrack.model.JamRecipe
import com.caminerin.backingtrack.model.Style
import com.caminerin.backingtrack.model.Chord
import com.caminerin.backingtrack.model.ChordQuality
import kotlin.random.Random

/**
 * Piano comping with rootless / shell voicings (3rd, b7, 9th colour) placed in a
 * register above the rhythm guitar so the two don't turn to mud. The bass owns
 * the root, so the piano leaves it out. Comps with space and a soft hand.
 */
class KeysGenerator(
    private val recipe: JamRecipe,
    private val timing: Timing,
    private val humanizer: Humanizer,
) {
    private val rng = Random(recipe.seed xor 0x4B455953)

    /** Rootless voicing (3rd, 6th/7th, 9th) in MIDI ~60-81. */
    private fun voicing(chord: Chord): List<Int> {
        val r = chord.rootSemitone
        val q = chord.quality
        val tones = mutableListOf<Int>()
        tones.add(r + q.intervals.getOrElse(1) { 4 }) // 3rd
        when (q) {
            ChordQuality.DOM7, ChordQuality.DOM9 -> { tones.add(r + 10); tones.add(r + 14) } // b7 + 9
            ChordQuality.MIN7 -> { tones.add(r + 10); tones.add(r + 14) }
            ChordQuality.MAJ7 -> { tones.add(r + 11); tones.add(r + 14) }
            ChordQuality.MINOR -> { tones.add(r + 7); tones.add(r + 14) }
            ChordQuality.MAJOR -> { tones.add(r + 7); tones.add(r + 9) }
            else -> { tones.add(r + 7) }
        }
        val base = 60
        return tones.map { n ->
            var x = base + (n % 12)
            while (x < 60) x += 12
            while (x > 81) x -= 12
            x
        }.distinct().sorted()
    }

    private fun chordHit(out: MutableList<RenderEvent>, sample: Int, chord: Chord, vel: Float) {
        val notes = voicing(chord)
        val baseSample = sample + humanizer.offsetSamples()
        val roll = (SAMPLE_RATE * 0.006).toInt() // small natural roll
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
            val b = bar.index
            // Lay out during the intro so the opening has space.
            if (bar.isIntro) continue
            when (recipe.style) {
                Style.FUNK -> {
                    for (h in listOf(0.0, 1.5, 2.5, 3.5)) {
                        if (rng.nextFloat() < 0.65f) chordHit(out, timing.at(b, h), chord, 0.4f)
                    }
                }
                Style.CLASSIC_ROCK, Style.BLUES_ROCK -> {
                    chordHit(out, timing.at(b, 0.0), chord, 0.42f + 0.1f * e)
                    if (e > 0.55f) chordHit(out, timing.eighth(b, 2, true), chord, 0.34f)
                }
                Style.BLUES -> {
                    // Sparse off-beat comp, leaving room for guitar + lead.
                    chordHit(out, timing.eighth(b, 1, true), chord, 0.34f)
                    if (recipe.feel != Feel.STRAIGHT) chordHit(out, timing.eighth(b, 3, true), chord, 0.32f)
                }
                Style.SLOW_BALLAD -> {
                    chordHit(out, timing.at(b, 0.0), chord, 0.4f)
                    if (recipe.feel == Feel.TWELVE_EIGHT) chordHit(out, timing.trip(b, 2, 0), chord, 0.32f)
                }
            }
        }
        return out
    }
}
