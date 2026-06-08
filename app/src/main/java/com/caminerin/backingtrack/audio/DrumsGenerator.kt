package com.caminerin.backingtrack.audio

import com.caminerin.backingtrack.model.Feel
import com.caminerin.backingtrack.model.JamRecipe
import com.caminerin.backingtrack.model.Style
import kotlin.random.Random

/**
 * Generates drum events per bar according to style + feel.
 *
 * Grooves follow standard kit vocabulary (The Drummer's Bible / The Greatest
 * Drum Beats & Grooves):
 *  - Shuffles place the hi-hat / ride on the 1st and 3rd triplet of every beat,
 *    a hard backbeat on 2 & 4, and optional snare ghost notes on the middle
 *    triplet (the "Purdie" feel) which is what makes a shuffle breathe.
 *  - Straight rock uses eighth-note hats with accented down-beats.
 *  - Funk uses sixteenth hats with accents, a syncopated kick and ghost snares.
 *  - Dynamics (down-beat / back-beat accents, soft off-beats, ghost notes) and
 *    seed-driven variants keep it from sounding like a drum machine.
 */
class DrumsGenerator(
    private val recipe: JamRecipe,
    private val timing: Timing,
    private val humanizer: Humanizer,
) {
    private val rng = Random(recipe.seed xor 0x44524D53)
    // Seed-derived groove variant so the "random" button changes the feel,
    // not just the micro-timing.
    private val variant = rng.nextInt(0, 4)
    private val swung = timing.isSwung
    private val halfTime = recipe.feel == Feel.HALF_TIME
    // 0 (fast) .. 1 (slow): drives extra subdivisions/ornaments at slow tempos.
    private val dense = timing.slowness

    fun generate(bars: List<Conductor.BarPlan>): List<RenderEvent> {
        val out = ArrayList<RenderEvent>(bars.size * 20)
        for (bar in bars) {
            when {
                bar.isIntro -> introGroove(out, bar)
                bar.isFill && rng.nextFloat() < 0.85f -> addFill(out, bar)
                else -> addGroove(out, bar)
            }
            // Crash to mark the top of every new cycle (after the turnaround fill).
            if (bar.barInCycle == 0 && bar.index > 0 && !bar.isIntro) {
                hitAbs(out, timing.at(bar.index, 0.0), "crash", 0.7f)
            }
            // Final downbeat accent to close the song.
            if (bar.isLastBar) hitAbs(out, timing.at(bar.index, 0.0), "crash", 0.8f)
        }
        return out
    }

    /** Stripped-back opening: soft ride/hat + backbeat, no ghost notes. */
    private fun introGroove(out: MutableList<RenderEvent>, bar: Conductor.BarPlan) {
        val b = bar.index
        val cym = if (recipe.style == Style.BLUES || recipe.style == Style.SLOW_BALLAD) "ride" else "hh_closed"
        for (beat in 0 until 4) {
            hitAbs(out, timing.trip(b, beat, 0), cym, 0.34f)
            if (swung) hitAbs(out, timing.trip(b, beat, 2), cym, 0.24f)
        }
        hitAbs(out, timing.at(b, 0.0), "kick", 0.6f)
        backbeat(out, b, 0.55f)
    }

    /** Emit a hit at an absolute sample position. */
    private fun hitAbs(out: MutableList<RenderEvent>, sample: Int, hit: String, vel: Float) {
        val s = sample + humanizer.offsetSamples()
        out.add(RenderEvent(startSample = s.coerceAtLeast(0), drumHit = hit, velocity = humanizer.scaleVelocity(vel)))
    }

    private fun straightEighth(out: MutableList<RenderEvent>, b: Int, beat: Int, up: Boolean, hit: String, vel: Float) =
        hitAbs(out, timing.eighth(b, beat, up), hit, vel)

    private fun addGroove(out: MutableList<RenderEvent>, bar: Conductor.BarPlan) {
        val b = bar.index
        val e = bar.energy
        when (recipe.style) {
            Style.BLUES -> bluesShuffle(out, b, e, ride = true)
            Style.BLUES_ROCK -> texasShuffle(out, b, e)
            Style.CLASSIC_ROCK -> rockGroove(out, b, e)
            Style.FUNK -> funkGroove(out, b, e)
            Style.SLOW_BALLAD -> balladGroove(out, b, e)
        }
        // Occasional open-hat "lift" on the last off-beat of a phrase.
        if (bar.index % 4 == 3 && !bar.isFill && rng.nextFloat() < 0.5f) {
            hitAbs(out, timing.eighth(b, 3, true), "hh_open", 0.45f)
        }
    }

    // --- Blues shuffle: triplet ride/hat, backbeat 2&4, ghost notes ---------
    private fun bluesShuffle(out: MutableList<RenderEvent>, b: Int, e: Float, ride: Boolean) {
        val cym = if (ride) "ride" else "hh_closed"
        for (beat in 0 until 4) {
            // 1st and 3rd triplet of each beat = the shuffle pulse.
            hitAbs(out, timing.trip(b, beat, 0), cym, 0.46f + 0.12f * e + if (beat == 0) 0.08f else 0f)
            if (swung) hitAbs(out, timing.trip(b, beat, 2), cym, 0.32f)
            else hitAbs(out, timing.eighth(b, beat, true), cym, 0.32f)
        }
        // Kick on 1 (& 3), backbeat snare on 2 & 4.
        hitAbs(out, timing.trip(b, 0, 0), "kick", 0.85f)
        hitAbs(out, timing.trip(b, 2, 0), "kick", 0.68f)
        backbeat(out, b, 0.82f)
        // Ghost notes / extra kicks by variant — this is the "feel".
        when (variant) {
            1 -> { // a touch busier: kick pickup on the 3rd triplet of 4
                hitAbs(out, timing.trip(b, 3, 2), "kick", 0.5f)
            }
            2 -> { // Purdie-style ghost snares on the middle triplet
                hitAbs(out, timing.trip(b, 1, 1), "snare", 0.14f)
                hitAbs(out, timing.trip(b, 3, 1), "snare", 0.16f)
            }
            3 -> { // double-shuffle: kick on the 3rd triplet of 1 & 3
                hitAbs(out, timing.trip(b, 0, 2), "kick", 0.45f)
                hitAbs(out, timing.trip(b, 2, 2), "kick", 0.45f)
                hitAbs(out, timing.trip(b, 1, 1), "snare", 0.13f)
            }
        }
        if (e > 0.6f) hitAbs(out, timing.trip(b, 1, 1), "snare", 0.12f)
        // Slow blues breathes with ghost snares on every middle triplet.
        if (dense > 0.5f) {
            for (beat in 0 until 4) hitAbs(out, timing.trip(b, beat, 1), "snare", 0.10f + 0.04f * dense)
        }
    }

    // --- Texas shuffle: driving hi-hat shuffle, strong backbeat -------------
    private fun texasShuffle(out: MutableList<RenderEvent>, b: Int, e: Float) {
        for (beat in 0 until 4) {
            hitAbs(out, timing.trip(b, beat, 0), "hh_closed", 0.5f + 0.12f * e)
            if (swung) hitAbs(out, timing.trip(b, beat, 2), "hh_closed", 0.36f)
            else hitAbs(out, timing.eighth(b, beat, true), "hh_closed", 0.36f)
        }
        hitAbs(out, timing.trip(b, 0, 0), "kick", 0.9f)
        hitAbs(out, timing.trip(b, 2, 0), "kick", 0.78f)
        if (variant != 1) hitAbs(out, timing.trip(b, 2, 2), "kick", 0.5f)
        if (variant == 3) { hitAbs(out, timing.trip(b, 1, 0), "kick", 0.55f); hitAbs(out, timing.trip(b, 3, 0), "kick", 0.55f) }
        backbeat(out, b, 0.85f)
        // Snare shuffle pickups for drive on busier variants.
        if (variant == 2 || e > 0.65f) {
            hitAbs(out, timing.trip(b, 0, 2), "snare", 0.16f)
            hitAbs(out, timing.trip(b, 2, 2), "snare", 0.18f)
        }
    }

    // --- Classic rock: straight eighth hats, accented down-beats ------------
    private fun rockGroove(out: MutableList<RenderEvent>, b: Int, e: Float) {
        for (beat in 0 until 4) {
            straightEighth(out, b, beat, up = false, "hh_closed", 0.55f + 0.1f * e + if (beat == 0) 0.06f else 0f)
            straightEighth(out, b, beat, up = true, "hh_closed", 0.4f)
        }
        // Slow rock fills the gaps with 16th-note hats (double-time feel).
        if (dense > 0.55f) {
            for (beat in 0 until 4) {
                hitAbs(out, timing.at(b, beat + 0.25), "hh_closed", 0.22f)
                hitAbs(out, timing.at(b, beat + 0.75), "hh_closed", 0.22f)
            }
        }
        hitAbs(out, timing.at(b, 0.0), "kick", 0.88f)
        when (variant) {
            0 -> { hitAbs(out, timing.at(b, 1.5), "kick", 0.62f); hitAbs(out, timing.at(b, 2.0), "kick", 0.7f) }
            1 -> { hitAbs(out, timing.at(b, 2.0), "kick", 0.72f); if (e > 0.55f) hitAbs(out, timing.at(b, 3.5), "kick", 0.55f) }
            2 -> { hitAbs(out, timing.at(b, 1.5), "kick", 0.6f); hitAbs(out, timing.at(b, 2.5), "kick", 0.58f) }
            else -> { hitAbs(out, timing.at(b, 2.0), "kick", 0.7f); hitAbs(out, timing.at(b, 3.0), "kick", 0.55f) }
        }
        backbeat(out, b, 0.85f)
    }

    // --- Funk: sixteenth hats with accents, syncopated kick, ghosts ---------
    private fun funkGroove(out: MutableList<RenderEvent>, b: Int, e: Float) {
        // At fast tempos 16th hats get frantic — drop to eighths to stay tight.
        val steps = if (dense < 0.2f) 8 else 16
        val stepBeat = 4.0 / steps
        for (i in 0 until steps) {
            val pos = i * stepBeat
            val accent = (i * (16 / steps)) % 4 == 0
            hitAbs(out, timing.at(b, pos), "hh_closed", if (accent) 0.5f + 0.08f * e else 0.26f)
        }
        hitAbs(out, timing.at(b, 0.0), "kick", 0.88f)
        hitAbs(out, timing.at(b, 0.75), "kick", 0.6f)
        hitAbs(out, timing.at(b, 2.5), "kick", 0.72f)
        if (variant == 1) hitAbs(out, timing.at(b, 1.75), "kick", 0.55f)
        if (variant == 2) hitAbs(out, timing.at(b, 3.25), "kick", 0.5f)
        if (variant == 3) { hitAbs(out, timing.at(b, 1.5), "kick", 0.55f); hitAbs(out, timing.at(b, 3.5), "kick", 0.52f) }
        backbeat(out, b, 0.82f)
        // Ghost snares — the funk syncopation.
        if (e > 0.4f) {
            hitAbs(out, timing.at(b, 0.5), "snare", 0.14f)
            hitAbs(out, timing.at(b, 1.75), "snare", 0.16f)
            hitAbs(out, timing.at(b, 3.25), "snare", 0.15f)
        }
    }

    // --- Slow ballad / soul: 12/8 triplet ride or sparse backbeat ----------
    private fun balladGroove(out: MutableList<RenderEvent>, b: Int, e: Float) {
        val twelve = recipe.feel == Feel.TWELVE_EIGHT || swung
        for (beat in 0 until 4) {
            hitAbs(out, timing.trip(b, beat, 0), "ride", 0.4f + if (beat == 0) 0.06f else 0f)
            if (twelve) {
                hitAbs(out, timing.trip(b, beat, 1), "ride", 0.24f)
                hitAbs(out, timing.trip(b, beat, 2), "ride", 0.28f)
            }
        }
        hitAbs(out, timing.at(b, 0.0), "kick", 0.7f)
        if (variant != 2) hitAbs(out, timing.at(b, 2.0), "kick", 0.58f)
        if (variant == 3) hitAbs(out, timing.trip(b, 3, 2), "kick", 0.45f)
        backbeat(out, b, 0.62f)
    }

    /** Backbeat snare: on 2 & 4, or only on 3 for half-time. */
    private fun backbeat(out: MutableList<RenderEvent>, b: Int, vel: Float) {
        if (halfTime) {
            hitAbs(out, timing.at(b, 2.0), "snare", vel)
        } else {
            hitAbs(out, timing.at(b, 1.0), "snare", vel * 0.96f)
            hitAbs(out, timing.at(b, 3.0), "snare", vel)
        }
    }

    private fun addFill(out: MutableList<RenderEvent>, bar: Conductor.BarPlan) {
        val b = bar.index
        val e = bar.energy
        // Keep the groove for the first half, then fill the last beat or two.
        when (recipe.style) {
            Style.BLUES, Style.BLUES_ROCK -> {
                for (beat in 0 until 2) {
                    hitAbs(out, timing.trip(b, beat, 0), "ride", 0.45f)
                    if (swung) hitAbs(out, timing.trip(b, beat, 2), "ride", 0.32f)
                }
                hitAbs(out, timing.trip(b, 0, 0), "kick", 0.82f)
                hitAbs(out, timing.at(b, 1.0), "snare", 0.78f)
            }
            else -> {
                for (beat in 0 until 2) {
                    straightEighth(out, b, beat, false, "hh_closed", 0.5f)
                    straightEighth(out, b, beat, true, "hh_closed", 0.35f)
                }
                hitAbs(out, timing.at(b, 0.0), "kick", 0.82f)
                hitAbs(out, timing.at(b, 1.0), "snare", 0.78f)
            }
        }
        // Tom/snare fill over beats 3-4, triplet or sixteenth depending on feel.
        val toms = listOf("snare", "tom_hi", "tom_mid", "tom_lo", "tom_floor")
        var ti = 0
        if (swung) {
            for (beat in 2 until 4) for (t in 0 until 3) {
                hitAbs(out, timing.trip(b, beat, t), toms[ti % toms.size], 0.55f + 0.25f * e)
                ti++
            }
        } else {
            val sub = if (e > 0.6f) 0.25 else 0.5
            var beat = 2.0
            while (beat < 4.0) {
                hitAbs(out, timing.at(b, beat), toms[ti % toms.size], 0.55f + 0.25f * e)
                beat += sub; ti++
            }
        }
    }
}
