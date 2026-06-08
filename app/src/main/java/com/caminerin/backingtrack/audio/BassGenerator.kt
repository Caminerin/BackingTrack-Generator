package com.caminerin.backingtrack.audio

import com.caminerin.backingtrack.model.Feel
import com.caminerin.backingtrack.model.JamRecipe
import com.caminerin.backingtrack.model.Style
import kotlin.random.Random

/**
 * Generates a musical bass line that follows the chord progression.
 *
 * Two things make the bass sound real instead of robotic:
 *  1. Register: the sampled bass only covers MIDI 28..39 (every semitone), so we
 *     fold every note into that window. That means notes play their *own* sample
 *     at unity pitch — no chipmunk pitch-stretching, which is what made the old
 *     bass sound bad.
 *  2. Lines: walking / boogie movement using chord tones (root, 3rd, 5th, 6th,
 *     b7) plus chromatic or scale approach notes that lead into the next chord's
 *     root (Ray Brown style), with a shuffle feel where appropriate.
 */
class BassGenerator(
    private val recipe: JamRecipe,
    private val timing: Timing,
    private val humanizer: Humanizer,
) {
    private val rng = Random(recipe.seed xor 0x42415353)
    private val variant = rng.nextInt(0, 4)
    private val swung = timing.isSwung

    fun generate(bars: List<Conductor.BarPlan>): List<RenderEvent> {
        val out = ArrayList<RenderEvent>(bars.size * 8)
        for ((i, bar) in bars.withIndex()) {
            val nextChord = bars.getOrNull(i + 1)?.chord
            addBar(out, bar, nextChord)
        }
        return out
    }

    /** Low register root (MIDI 28..39) for a pitch class, so it plays unity-pitch. */
    private fun foldLow(pc: Int): Int = 28 + ((pc - 4) % 12 + 12) % 12

    /** Keep a note in a tight low bass register (avoid big pitch stretch). */
    private fun reg(n: Int): Int {
        var x = n
        while (x > 43) x -= 12
        while (x < 28) x += 12
        return x
    }

    /** Emit at integer/fractional beat; [up] selects the swung off-beat. */
    private fun emit(out: MutableList<RenderEvent>, b: Int, beat: Int, up: Boolean, midi: Int, vel: Float) {
        val s = timing.eighth(b, beat, up) + humanizer.offsetSamples()
        out.add(RenderEvent(startSample = s.coerceAtLeast(0), midi = midi, velocity = humanizer.scaleVelocity(vel)))
    }

    private fun emitAt(out: MutableList<RenderEvent>, b: Int, beat: Double, midi: Int, vel: Float) {
        val s = timing.at(b, beat) + humanizer.offsetSamples()
        out.add(RenderEvent(startSample = s.coerceAtLeast(0), midi = midi, velocity = humanizer.scaleVelocity(vel)))
    }

    private fun addBar(out: MutableList<RenderEvent>, bar: Conductor.BarPlan, next: com.caminerin.backingtrack.model.Chord?) {
        val chord = bar.chord
        val root = foldLow(chord.rootSemitone)
        val third = reg(root + chord.quality.intervals.getOrElse(1) { 4 })
        val fifth = reg(root + 7)
        val sixth = reg(root + 9)
        val b7 = reg(root + 10)
        val b = bar.index
        val e = bar.energy

        // Intro: lay back on roots so the band can build from there.
        if (bar.isIntro) {
            emitAt(out, b, 0.0, root, 0.7f)
            if (recipe.style != Style.SLOW_BALLAD) emitAt(out, b, 2.0, fifth, 0.58f)
            return
        }

        // Approach note that leads into the next bar's root (chromatic/scale).
        val approachNote = approach(next, chord.rootSemitone)

        when (recipe.style) {
            Style.BLUES, Style.BLUES_ROCK -> {
                // Quarter-note walking boogie: root / 3 / 5 / 6 with variants.
                emitAt(out, b, 0.0, root, 0.85f)
                when (variant) {
                    0 -> { emitAt(out, b, 1.0, third, 0.7f); emitAt(out, b, 2.0, fifth, 0.74f); emitAt(out, b, 3.0, sixth, 0.7f) }
                    1 -> { emitAt(out, b, 1.0, fifth, 0.7f); emitAt(out, b, 2.0, sixth, 0.74f); emitAt(out, b, 3.0, b7, 0.7f) }
                    2 -> { emitAt(out, b, 1.0, fifth, 0.7f); emitAt(out, b, 2.0, root, 0.72f); emitAt(out, b, 3.0, third, 0.7f) }
                    else -> { emitAt(out, b, 1.0, sixth, 0.7f); emitAt(out, b, 2.0, fifth, 0.72f); emitAt(out, b, 3.0, third, 0.7f) }
                }
                // Shuffle skip on the 'and' of 4 leading to the next root.
                if (bar.isTurnaround) emitAt(out, b, 3.0, approachNote, 0.7f)
                if (swung && (e > 0.5f || bar.isTurnaround)) emit(out, b, 3, true, approachNote, 0.55f)
                else if (e > 0.6f) emitAt(out, b, 3.5, approachNote, 0.5f)
            }
            Style.CLASSIC_ROCK -> {
                // Driving eighth notes on the root with octave/fifth colour.
                for (beat in 0 until 4) {
                    emit(out, b, beat, false, root, 0.82f)
                    val upNote = if (beat == 3) approachNote else if (variant == 1 && beat % 2 == 1) fifth else root
                    emit(out, b, beat, true, upNote, 0.58f)
                }
            }
            Style.FUNK -> {
                // Syncopated root with octave pop and fifth, leaving space.
                emitAt(out, b, 0.0, root, 0.9f)
                emitAt(out, b, 0.75, root, 0.55f)
                emitAt(out, b, 1.5, reg(root + 12), 0.62f)
                emitAt(out, b, 2.0, root, 0.82f)
                if (variant != 2) emitAt(out, b, 2.75, fifth, 0.55f)
                emitAt(out, b, 3.5, approachNote, 0.55f)
            }
            Style.SLOW_BALLAD -> {
                emitAt(out, b, 0.0, root, 0.72f)
                emitAt(out, b, 2.0, fifth, 0.6f)
                if (recipe.feel == Feel.TWELVE_EIGHT) emitAt(out, b, 3.0, third, 0.55f)
                if (bar.isTurnaround) emitAt(out, b, 3.0, approachNote, 0.55f)
            }
        }
    }

    /** Chromatic approach note leading to the next chord's root, kept low. */
    private fun approach(next: com.caminerin.backingtrack.model.Chord?, currentPc: Int): Int {
        val targetPc = next?.rootSemitone ?: currentPc
        val target = foldLow(targetPc)
        // Lead in by a half-step from below or above (Ray Brown style).
        return reg(if (rng.nextBoolean()) target - 1 else target + 1)
    }
}
