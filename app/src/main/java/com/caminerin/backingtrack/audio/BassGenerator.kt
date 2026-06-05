package com.caminerin.backingtrack.audio

import com.caminerin.backingtrack.model.Feel
import com.caminerin.backingtrack.model.JamRecipe
import com.caminerin.backingtrack.model.Style
import com.caminerin.backingtrack.model.midiNote
import kotlin.random.Random

/**
 * Generates a musical bass line following the chord progression.
 * Uses root / fifth / octave / chromatic approach notes depending on style.
 */
class BassGenerator(
    private val recipe: JamRecipe,
    private val timing: Timing,
    private val humanizer: Humanizer,
) {
    private val rng = Random(recipe.seed xor 0x42415353)
    private val variant = rng.nextInt(0, 3)

    fun generate(bars: List<Conductor.BarPlan>): List<RenderEvent> {
        val out = ArrayList<RenderEvent>(bars.size * 8)
        for ((i, bar) in bars.withIndex()) {
            val nextChord = bars.getOrNull(i + 1)?.chord
            addBar(out, bar, nextChord)
        }
        return out
    }

    private fun emit(out: MutableList<RenderEvent>, barIdx: Int, beat: Double, midi: Int, vel: Float) {
        val s = timing.sampleAt(barIdx, beat) + humanizer.offsetSamples()
        out.add(
            RenderEvent(
                startSample = s.coerceAtLeast(0),
                midi = midi,
                velocity = humanizer.scaleVelocity(vel),
            )
        )
    }

    private fun addBar(out: MutableList<RenderEvent>, bar: Conductor.BarPlan, next: com.caminerin.backingtrack.model.Chord?) {
        val chord = bar.chord
        val root = chord.bassRoot(octave = 2)
        val fifth = root + 7
        val octave = root + 12
        val third = root + chord.quality.intervals.getOrElse(1) { 4 }

        when (recipe.style) {
            Style.BLUES, Style.BLUES_ROCK -> {
                // Walking-ish shuffle: root, third, fifth, sixth pattern.
                val sixth = root + 9
                emit(out, bar.index, 0.0, root, 0.8f)
                // Walking variants keep the random button musically distinct.
                when (variant) {
                    0 -> {
                        emit(out, bar.index, 1.0, fifth, 0.7f)
                        emit(out, bar.index, 2.0, sixth, 0.72f)
                        emit(out, bar.index, 3.0, fifth, 0.68f)
                    }
                    1 -> {
                        emit(out, bar.index, 1.0, octave, 0.7f)
                        emit(out, bar.index, 2.0, fifth, 0.72f)
                        emit(out, bar.index, 3.0, sixth, 0.68f)
                    }
                    else -> {
                        emit(out, bar.index, 1.0, third, 0.7f)
                        emit(out, bar.index, 2.0, fifth, 0.72f)
                        emit(out, bar.index, 3.0, sixth, 0.68f)
                    }
                }
                if (bar.energy > 0.55f) emit(out, bar.index, 3.5, approach(next, root), 0.5f)
            }
            Style.CLASSIC_ROCK -> {
                // Driving eighths on the root with octave accents.
                for (beat in 0 until 4) {
                    emit(out, bar.index, beat.toDouble(), root, 0.8f)
                    emit(out, bar.index, beat + 0.5, root, 0.6f)
                }
                if (bar.energy > 0.6f) emit(out, bar.index, 3.5, approach(next, root), 0.55f)
            }
            Style.FUNK -> {
                // Syncopated root/octave with a fifth pop.
                emit(out, bar.index, 0.0, root, 0.85f)
                emit(out, bar.index, 0.75, root, 0.55f)
                emit(out, bar.index, 1.5, octave, 0.6f)
                emit(out, bar.index, 2.0, root, 0.8f)
                emit(out, bar.index, 2.5, fifth, 0.55f)
                emit(out, bar.index, 3.5, approach(next, root), 0.5f)
            }
            Style.SLOW_BALLAD -> {
                // Whole / half notes, gentle.
                emit(out, bar.index, 0.0, root, 0.7f)
                emit(out, bar.index, 2.0, fifth, 0.6f)
                if (recipe.feel == Feel.TWELVE_EIGHT) emit(out, bar.index, 3.0, third, 0.55f)
            }
        }
    }

    /** Chromatic / scale approach note leading to the next chord's root. */
    private fun approach(next: com.caminerin.backingtrack.model.Chord?, currentRoot: Int): Int {
        if (next == null) return currentRoot
        val target = next.bassRoot(octave = 2)
        return if (rng.nextBoolean()) target - 1 else target + 1
    }
}
