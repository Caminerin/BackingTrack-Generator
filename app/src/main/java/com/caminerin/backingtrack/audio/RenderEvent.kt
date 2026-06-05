package com.caminerin.backingtrack.audio

/**
 * A single scheduled audio event produced by a generator.
 * Times are in absolute samples (44100 Hz). For tonal instruments [midi] is the
 * note; for drums [drumHit] names the hit and [midi] is ignored.
 */
data class RenderEvent(
    val startSample: Int,
    val midi: Int = 0,
    val drumHit: String? = null,
    val velocity: Float = 0.8f,
    val durationSamples: Int = 0,
)
