package com.caminerin.backingtrack.audio

import kotlin.random.Random

/**
 * Adds micro-timing offsets and velocity variation to make the backing track
 * feel "played" rather than machine-gun quantised.
 *
 * All jitter is deterministic given a seed, so the same recipe+seed produces
 * the same humanised result every time.
 */
class Humanizer(seed: Long, private val amount: Float = 1.0f) {

    private val rng = Random(seed)

    /** Max timing offset in samples at 44100 Hz (~±20 ms at amount=1). */
    private val maxTimingOffset: Int
        get() = (44100 * 0.020 * amount).toInt()

    /** Max velocity variation as a fraction (±15 % at amount=1). */
    private val maxVelFraction: Float
        get() = 0.15f * amount

    fun offsetSamples(): Int {
        if (maxTimingOffset == 0) return 0
        return rng.nextInt(-maxTimingOffset, maxTimingOffset + 1)
    }

    fun scaleVelocity(vel: Float): Float {
        val jitter = 1f + (rng.nextFloat() * 2f - 1f) * maxVelFraction
        return (vel * jitter).coerceIn(0.05f, 1f)
    }
}
