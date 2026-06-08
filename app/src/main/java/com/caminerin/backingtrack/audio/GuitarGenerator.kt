package com.caminerin.backingtrack.audio

import com.caminerin.backingtrack.model.Feel
import com.caminerin.backingtrack.model.JamRecipe
import com.caminerin.backingtrack.model.Style
import com.caminerin.backingtrack.model.Chord
import com.caminerin.backingtrack.model.ChordQuality
import kotlin.random.Random

/**
 * Rhythm-guitar comping (Bruce Buckingham / "101 Blues Rhythm" vocabulary).
 *
 * Keeps out of the bass and lead's way by voicing compact guide-tone shapes
 * (3rd + b7 + colour) in the mid register, comping with space and dynamics
 * rather than strumming every beat — the silence is what makes it groove.
 * Swing feels place the off-beat chops on the triplet so it locks with the kit.
 */
class GuitarGenerator(
    private val recipe: JamRecipe,
    private val timing: Timing,
    private val humanizer: Humanizer,
) {
    private val rng = Random(recipe.seed xor 0x47545253)
    private val variant = rng.nextInt(0, 4)
    private val strumSpread = (SAMPLE_RATE * 0.012).toInt() // 12 ms across the voicing

    fun generate(bars: List<Conductor.BarPlan>): List<RenderEvent> {
        val out = ArrayList<RenderEvent>(bars.size * 12)
        for (bar in bars) addBar(out, bar)
        return out
    }

    /** Compact guide-tone voicing (3rd, 5th/6th, b7) in MIDI ~55-72. */
    private fun voicing(chord: Chord): List<Int> {
        val r = chord.rootSemitone
        val q = chord.quality
        val tones = mutableListOf<Int>()
        // 3rd defines major/minor.
        tones.add(r + q.intervals.getOrElse(1) { 4 })
        when (q) {
            ChordQuality.DOM7, ChordQuality.DOM9 -> { tones.add(r + 9); tones.add(r + 10) }
            ChordQuality.DOM13 -> { tones.add(r + 10); tones.add(r + 9) } // b7 + 13(=6)
            ChordQuality.DOM7SHARP9 -> { tones.add(r + 10); tones.add(r + 3) } // b7 + #9
            ChordQuality.MIN7, ChordQuality.MIN9 -> { tones.add(r + 7); tones.add(r + 10) }
            ChordQuality.MAJ7, ChordQuality.MAJ9 -> { tones.add(r + 7); tones.add(r + 11) }
            ChordQuality.HALF_DIM7 -> { tones.add(r + 6); tones.add(r + 10) } // b5 + b7
            ChordQuality.DIM7 -> { tones.add(r + 6); tones.add(r + 9) }
            ChordQuality.MAJ6, ChordQuality.SIX9 -> { tones.add(r + 7); tones.add(r + 9) }
            ChordQuality.MIN6 -> { tones.add(r + 7); tones.add(r + 9) }
            ChordQuality.POWER -> { tones.add(r + 7); tones.add(r + 12) }
            else -> { tones.add(r + 7) }
        }
        val base = 55
        return tones.map { n ->
            var x = base + (n % 12)
            while (x < 55) x += 12
            while (x > 72) x -= 12
            x
        }.distinct().sorted()
    }

    private fun strumAbs(out: MutableList<RenderEvent>, sample: Int, chord: Chord, vel: Float, up: Boolean) {
        val notes = voicing(chord)
        val order = if (up) notes.reversed() else notes
        val baseSample = sample + humanizer.offsetSamples()
        val perString = if (notes.size > 1) strumSpread / (notes.size - 1) else 0
        for ((k, midi) in order.withIndex()) {
            val s = baseSample + k * perString
            out.add(
                RenderEvent(
                    startSample = s.coerceAtLeast(0),
                    midi = midi,
                    velocity = humanizer.scaleVelocity(vel * (if (up) 0.8f else 1f)),
                )
            )
        }
    }

    private fun chop(out: MutableList<RenderEvent>, b: Int, beat: Int, up: Boolean, chord: Chord, vel: Float) =
        strumAbs(out, timing.eighth(b, beat, up), chord, vel, up)

    private fun addBar(out: MutableList<RenderEvent>, bar: Conductor.BarPlan) {
        val chord = bar.chord
        val e = bar.energy
        val b = bar.index
        // Intro: a single soft chord on the downbeat, leaving lots of air.
        if (bar.isIntro) {
            strumAbs(out, timing.at(b, 0.0), chord, 0.34f, up = false)
            return
        }
        when (recipe.style) {
            Style.BLUES, Style.BLUES_ROCK -> {
                when (variant) {
                    1 -> {
                        // Charleston: downbeat + the 'and' of 2 — lots of air.
                        chop(out, b, 0, false, chord, 0.52f + 0.1f * e)
                        chop(out, b, 2, true, chord, 0.42f)
                        if (e > 0.6f) chop(out, b, 3, true, chord, 0.34f)
                    }
                    2 -> {
                        // Shuffle chops on every off-beat (the 'and'/3rd triplet).
                        for (beat in 0 until 4) {
                            if (beat == 0 || beat == 2) chop(out, b, beat, false, chord, 0.46f + 0.08f * e)
                            chop(out, b, beat, true, chord, 0.36f)
                        }
                    }
                    3 -> {
                        // Long pad: one chord per bar (+ a push into 3) — maximum air.
                        chop(out, b, 0, false, chord, 0.5f + 0.1f * e)
                        if (e > 0.55f) chop(out, b, 2, true, chord, 0.34f)
                    }
                    else -> {
                        // Backbeat comp on 2 & 4 with a soft pickup — classic and spacious.
                        chop(out, b, 1, false, chord, 0.5f + 0.1f * e)
                        chop(out, b, 3, false, chord, 0.52f + 0.1f * e)
                        if (rng.nextFloat() < 0.5f) chop(out, b, 2, true, chord, 0.32f)
                    }
                }
            }
            Style.CLASSIC_ROCK -> {
                for (beat in 0 until 4) {
                    chop(out, b, beat, false, chord, 0.5f + 0.1f * e)
                    if (e > 0.5f) chop(out, b, beat, true, chord, 0.36f)
                }
            }
            Style.FUNK -> {
                // Tight 16th stabs, syncopated, with rests for funk.
                val hits = listOf(0.0, 0.75, 1.5, 2.0, 2.75, 3.5)
                for (h in hits) {
                    if (rng.nextFloat() < 0.85f) {
                        strumAbs(out, timing.at(b, h), chord, 0.48f, up = (h % 1.0 != 0.0))
                    }
                }
            }
            Style.SLOW_BALLAD -> {
                strumAbs(out, timing.at(b, 0.0), chord, 0.42f, up = false)
                strumAbs(out, timing.at(b, 2.0), chord, 0.38f, up = false)
                if (recipe.feel == Feel.TWELVE_EIGHT) strumAbs(out, timing.at(b, 3.0), chord, 0.34f, up = true)
            }
        }
    }
}
