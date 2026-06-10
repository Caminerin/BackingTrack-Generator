package com.caminerin.backingtrack.model

/** Metronome subdivisions the user can pick (clicks per quarter-note beat). */
enum class Subdivision(val label: String, val symbol: String, val perBeat: Int) {
    QUARTER("Negras", "♩", 1),
    EIGHTH("Corcheas", "♫", 2),
    TRIPLET("Tresillos", "♪³", 3),
    SIXTEENTH("Semicorcheas", "♬", 4),
}

/**
 * One instrument track inside a backing (e.g. "Batería").
 * [asset] is the base asset name without quality/extension
 * (e.g. "blues_a_60_even.drums"); the concrete file downloaded is
 * "<asset>.<quality>.ogg" (see [Quality]).
 */
data class Stem(
    val instrument: String,
    val asset: String,
)

/** Download quality tier the user can pick (Opus bitrate). */
enum class Quality(val id: String, val label: String) {
    LOW("low", "Datos bajos"),
    STD("std", "Estándar"),
    HIGH("high", "Alta");

    companion object {
        fun fromId(id: String?): Quality = entries.firstOrNull { it.id == id } ?: STD
    }
}

/** A chord placed at a given quarter-note beat (0-based) from the song start. */
data class ChordEvent(
    val beat: Int,
    val name: String,
)

data class Track(
    val id: String,
    val title: String,
    val styleId: String,
    val styleName: String,
    val subStyle: String,
    val key: String,
    val bpm: Int,
    val durationSec: Int,
    val timeSignature: String,
    val feel: String,
    val stems: List<Stem>,
    val chords: List<ChordEvent>,
    val free: Boolean,
    /** Base asset name of the 15 s preview clip ("<id>.preview"); may be empty. */
    val preview: String = "",
)

data class Style(
    val id: String,
    val name: String,
    val tracks: List<Track>,
)

const val FAVORITES_ID = "favorites"
const val FREE_ID = "free"
