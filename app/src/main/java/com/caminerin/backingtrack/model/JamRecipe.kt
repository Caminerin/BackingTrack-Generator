package com.caminerin.backingtrack.model

import java.util.UUID

/**
 * Generation quality mode.
 * Quick   – fewer humanisation layers, basic fills, faster render.
 * Best    – more round-robins, complex humanisation, refined mix.
 */
enum class GenerationQuality { QUICK, BEST }

/**
 * Instrument channel flags + volume for the mixer.
 */
data class InstrumentMix(
    val enabled: Boolean = true,
    val volume: Float = 0.75f,
)

/**
 * Everything needed to (re-)generate a backing track deterministically.
 * Serialised to JSON when saved in the local library.
 */
data class JamRecipe(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val style: Style = Style.BLUES,
    val keySemitone: Int = 9, // A
    val bpm: Int = 95,
    val feel: Feel = Feel.SHUFFLE,
    val timeSignatureBeats: Int = 4,
    val progression: List<Chord> = emptyList(),
    val sections: List<Section> = emptyList(),
    val durationBars: Int = 12,
    val instruments: Map<String, InstrumentMix> = defaultInstruments(),
    val quality: GenerationQuality = GenerationQuality.BEST,
    val density: Float = 0.6f,
    val energy: Float = 0.6f,
    val variationLevel: Float = 0.5f,
    val seed: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        fun defaultInstruments() = mapOf(
            "drums" to InstrumentMix(),
            "bass" to InstrumentMix(),
            "guitar" to InstrumentMix(volume = 0.55f),
            "keys" to InstrumentMix(enabled = false, volume = 0.5f), // piano; off by default, user enables
        )
    }
}
