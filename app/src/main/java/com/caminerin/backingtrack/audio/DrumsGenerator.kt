package com.caminerin.backingtrack.audio

import com.caminerin.backingtrack.model.Feel
import com.caminerin.backingtrack.model.JamRecipe
import com.caminerin.backingtrack.model.Style
import kotlin.random.Random

/**
 * Generates drum events per bar according to style + feel.
 * Patterns are expressed on an eighth-note grid (8 slots per 4/4 bar) and the
 * [Timing] applies swing so shuffle/swing feels groove correctly.
 */
class DrumsGenerator(
    private val recipe: JamRecipe,
    private val timing: Timing,
    private val humanizer: Humanizer,
) {
    private val rng = Random(recipe.seed xor 0x44524D53)
    // Seed-derived groove variant so the "random" button changes the feel,
    // not just the micro-timing.
    private val variant = rng.nextInt(0, 3)

    fun generate(bars: List<Conductor.BarPlan>): List<RenderEvent> {
        val out = ArrayList<RenderEvent>(bars.size * 16)
        for (bar in bars) {
            if (bar.isFill && rng.nextFloat() < 0.85f) {
                addFill(out, bar)
            } else {
                addGroove(out, bar)
            }
        }
        return out
    }

    private fun emit(out: MutableList<RenderEvent>, barIdx: Int, beat: Double, hit: String, vel: Float) {
        val s = timing.sampleAt(barIdx, beat) + humanizer.offsetSamples()
        out.add(RenderEvent(startSample = s.coerceAtLeast(0), drumHit = hit, velocity = humanizer.scaleVelocity(vel)))
    }

    private fun velTier(v: Float): String = when {
        v < 0.4f -> "soft"
        v < 0.75f -> "mid"
        else -> "hard"
    }

    private fun addGroove(out: MutableList<RenderEvent>, bar: Conductor.BarPlan) {
        val b = bar.index
        val e = bar.energy
        val swung = recipe.feel != Feel.STRAIGHT

        when (recipe.style) {
            Style.BLUES, Style.BLUES_ROCK -> {
                // Shuffle ride/hat groove, backbeat on 2 & 4, kick on 1 & 3.
                val cym = if (recipe.style == Style.BLUES_ROCK) "hh_closed" else "ride"
                for (beat in 0 until 4) {
                    emit(out, b, beat.toDouble(), cym, 0.5f + 0.1f * e)
                    if (swung) emit(out, b, beat + 0.5, cym, 0.32f)
                }
                emit(out, b, 0.0, "kick", 0.8f)
                emit(out, b, 2.0, "kick", 0.72f)
                if (e > 0.55f) emit(out, b, 2.5, "kick", 0.5f)
                when (variant) {
                    1 -> emit(out, b, 3.5, "kick", 0.5f)
                    2 -> emit(out, b, 1.5, "kick", 0.45f)
                }
                emit(out, b, 1.0, "snare", 0.78f)
                emit(out, b, 3.0, "snare", 0.82f)
            }
            Style.CLASSIC_ROCK -> {
                for (beat in 0 until 4) {
                    emit(out, b, beat.toDouble(), "hh_closed", 0.55f + 0.1f * e)
                    emit(out, b, beat + 0.5, "hh_closed", 0.4f)
                }
                emit(out, b, 0.0, "kick", 0.85f)
                emit(out, b, 1.5, "kick", 0.6f)
                emit(out, b, 2.0, "kick", 0.7f)
                if (e > 0.6f) emit(out, b, 3.5, "kick", 0.5f)
                if (variant == 2) emit(out, b, 2.5, "kick", 0.5f)
                emit(out, b, 1.0, "snare", 0.82f)
                emit(out, b, 3.0, "snare", 0.85f)
            }
            Style.FUNK -> {
                // Sixteenth hats, syncopated kick, tight backbeat + ghost snares.
                for (i in 0 until 8) {
                    val beat = i * 0.5
                    emit(out, b, beat, "hh_closed", if (i % 2 == 0) 0.55f else 0.35f)
                }
                emit(out, b, 0.0, "kick", 0.85f)
                emit(out, b, 0.75, "kick", 0.6f)
                emit(out, b, 2.5, "kick", 0.7f)
                emit(out, b, 1.0, "snare", 0.8f)
                emit(out, b, 3.0, "snare", 0.82f)
                if (e > 0.5f) {
                    emit(out, b, 1.75, "snare", 0.25f) // ghost
                    emit(out, b, 3.5, "snare", 0.22f)
                }
            }
            Style.SLOW_BALLAD -> {
                // 6/8-ish or slow backbeat with ride; sparse kick.
                for (beat in 0 until 4) {
                    emit(out, b, beat.toDouble(), "ride", 0.42f)
                    if (recipe.feel == Feel.TWELVE_EIGHT) {
                        emit(out, b, beat + 0.33, "ride", 0.28f)
                        emit(out, b, beat + 0.66, "ride", 0.3f)
                    }
                }
                emit(out, b, 0.0, "kick", 0.7f)
                emit(out, b, 2.0, "kick", 0.6f)
                emit(out, b, 1.0, "snare", 0.6f)
                emit(out, b, 3.0, "snare", 0.65f)
            }
        }

        // Occasional open hat accent at phrase ends.
        if (bar.index % 4 == 3 && !bar.isFill && rng.nextFloat() < 0.4f) {
            emit(out, b, 3.5, "hh_open", 0.45f)
        }
    }

    private fun addFill(out: MutableList<RenderEvent>, bar: Conductor.BarPlan) {
        val b = bar.index
        // Keep the groove for the first half, fill the last beat or two.
        for (beat in 0 until 2) {
            emit(out, b, beat.toDouble(), "hh_closed", 0.5f)
            emit(out, b, beat + 0.5, "hh_closed", 0.35f)
        }
        emit(out, b, 0.0, "kick", 0.8f)
        emit(out, b, 1.0, "snare", 0.78f)

        val toms = listOf("snare", "tom_hi", "tom_mid", "tom_lo", "tom_floor")
        val sub = if (bar.energy > 0.6f) 0.25 else 0.5
        var beat = 2.0
        var ti = 0
        while (beat < 4.0) {
            val hit = toms[ti % toms.size]
            emit(out, b, beat, hit, 0.6f + 0.25f * bar.energy)
            beat += sub
            ti++
        }
        // Crash on the downbeat of the next phrase (emit at end-of-bar -> next bar handled by groove).
        if (bar.isLastBar) emit(out, b, 3.5, "crash", 0.7f)
    }
}
