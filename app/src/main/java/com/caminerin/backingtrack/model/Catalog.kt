package com.caminerin.backingtrack.model

/** Metronome subdivisions the user can pick (clicks per quarter-note beat). */
enum class Subdivision(val label: String, val perBeat: Int) {
    QUARTER("Negras", 1),
    EIGHTH("Corcheas", 2),
    TRIPLET("Tresillos", 3),
    SIXTEENTH("Semicorcheas", 4),
}

data class Track(
    val id: String,
    val title: String,
    val styleId: String,
    val styleName: String,
    val key: String,
    val bpm: Int,
    val durationSec: Int,
    val audio: String,
    val free: Boolean,
)

data class Style(
    val id: String,
    val name: String,
    val tracks: List<Track>,
)

const val FAVORITES_ID = "favorites"
