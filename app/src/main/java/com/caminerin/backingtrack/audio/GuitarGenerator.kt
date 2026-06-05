package com.caminerin.backingtrack.audio

import com.caminerin.backingtrack.model.Feel
import com.caminerin.backingtrack.model.JamRecipe
import com.caminerin.backingtrack.model.Style
import com.caminerin.backingtrack.model.Chord
import kotlin.random.Random

/**
 * Generates rhythm-guitar comping: closed chord voicings strummed with a slight
 * per-string delay (down/up strokes), kept in a mid register so it leaves room
 * for the player's lead guitar.
 */
class GuitarGenerator(
    private val recipe: JamRecipe,
    private val timing: Timing,
    private val humanizer: Humanizer,
) {
    private val rng = Random(recipe.seed xor 0x47545253)
    private val variant = rng.nextInt(0, 3)
    private val strumSpread = (SAMPLE_RATE * 0.012).toInt() // 12 ms across the voicing

    fun generate(bars: List<Conductor.BarPlan>): List<RenderEvent> {
        val out = ArrayList<RenderEvent>(bars.size * 12)
        for (bar in bars) addBar(out, bar)
        return out
    }

    /** Build a 3-4 note guitar voicing in the mid register (MIDI ~52-67). */
    private fun voicing(chord: Chord): List<Int> {
        val base = 48 + chord.rootSemitone // root around C3-B3
        val notes = chord.quality.intervals.map { base + it }.toMutableList()
        // Keep it tight: drop into 52..72 window.
        val mapped = notes.map { n ->
            var x = n
            while (x < 52) x += 12
            while (x > 72) x -= 12
            x
        }.distinct().sorted()
        return mapped
    }

    private fun strum(out: MutableList<RenderEvent>, barIdx: Int, beat: Double, chord: Chord, vel: Float, up: Boolean) {
        val notes = voicing(chord)
        val order = if (up) notes.reversed() else notes
        val baseSample = timing.sampleAt(barIdx, beat) + humanizer.offsetSamples()
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

    private fun addBar(out: MutableList<RenderEvent>, bar: Conductor.BarPlan) {
        val chord = bar.chord
        val e = bar.energy
        when (recipe.style) {
            Style.BLUES, Style.BLUES_ROCK -> {
                when (variant) {
                    // Charleston-style: downbeat + the 'and' of 2, lots of room.
                    1 -> {
                        strum(out, bar.index, 0.0, chord, 0.5f + 0.1f * e, up = false)
                        strum(out, bar.index, 2.5, chord, 0.4f, up = true)
                        if (recipe.feel != Feel.STRAIGHT && rng.nextFloat() < 0.5f) {
                            strum(out, bar.index, 3.5, chord, 0.32f, up = true)
                        }
                    }
                    else -> {
                        // Comp on the off-beats with a shuffle; sparser = more room.
                        for (beat in 0 until 4) {
                            if (beat == 0 || beat == 2 || rng.nextFloat() < 0.5f) {
                                strum(out, bar.index, beat.toDouble(), chord, 0.5f + 0.1f * e, up = false)
                            }
                            if (recipe.feel != Feel.STRAIGHT && rng.nextFloat() < 0.6f) {
                                strum(out, bar.index, beat + 0.5, chord, 0.34f, up = true)
                            }
                        }
                    }
                }
            }
            Style.CLASSIC_ROCK -> {
                // Driving down-strokes on each beat, up-strokes on the and.
                for (beat in 0 until 4) {
                    strum(out, bar.index, beat.toDouble(), chord, 0.55f + 0.1f * e, up = false)
                    if (e > 0.5f) strum(out, bar.index, beat + 0.5, chord, 0.38f, up = true)
                }
            }
            Style.FUNK -> {
                // Tight 16th stabs, muted feel, syncopated.
                val hits = listOf(0.0, 0.75, 1.5, 2.0, 2.75, 3.5)
                for (h in hits) {
                    if (rng.nextFloat() < 0.85f) {
                        strum(out, bar.index, h, chord, 0.5f, up = (h % 1.0 != 0.0))
                    }
                }
            }
            Style.SLOW_BALLAD -> {
                // Sustained chords, one per half-note, soft.
                strum(out, bar.index, 0.0, chord, 0.45f, up = false)
                strum(out, bar.index, 2.0, chord, 0.4f, up = false)
                if (recipe.feel == Feel.TWELVE_EIGHT) strum(out, bar.index, 3.0, chord, 0.35f, up = true)
            }
        }
    }
}
