package com.caminerin.backingtrack.model

/** Metronome subdivisions the user can pick (clicks per quarter-note beat). */
enum class Subdivision(val label: String, val symbol: String, val perBeat: Int) {
    QUARTER("Negras", "♩", 1),
    EIGHTH("Corcheas", "♫", 2),
    TRIPLET("Tresillos", "♪³", 3),
    SIXTEENTH("Semicorcheas", "♬", 4),
}

/** One instrument track inside a backing (e.g. "Batería" -> drums.mp3). */
data class Stem(
    val instrument: String,
    val audio: String,
)

data class Track(
    val id: String,
    val title: String,
    val styleId: String,
    val styleName: String,
    val key: String,
    val bpm: Int,
    val durationSec: Int,
    val timeSignature: String,
    val feel: String,
    val stems: List<Stem>,
    val free: Boolean,
)

data class Style(
    val id: String,
    val name: String,
    val tracks: List<Track>,
)

const val FAVORITES_ID = "favorites"
