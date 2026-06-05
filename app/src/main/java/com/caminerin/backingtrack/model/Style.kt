package com.caminerin.backingtrack.model

/** Musical feel / groove type. */
enum class Feel(val displayName: String) {
    STRAIGHT("Straight"),
    SHUFFLE("Shuffle"),
    SWING("Swing"),
    TWELVE_EIGHT("12/8"),
    HALF_TIME("Half-time"),
}

/** The five initial styles defined in the functional spec. */
enum class Style(
    val displayName: String,
    val defaultFeel: Feel,
    val defaultBpm: Int,
    val subStyles: List<String>,
) {
    BLUES(
        "Blues", Feel.SHUFFLE, 95,
        listOf("12-Bar", "Slow Blues", "Minor Blues", "Blues Shuffle", "12/8 Blues"),
    ),
    BLUES_ROCK(
        "Blues Rock", Feel.SHUFFLE, 120,
        listOf("Texas Shuffle", "Straight", "Minor", "Heavy", "Rock Ballad"),
    ),
    CLASSIC_ROCK(
        "Classic Rock", Feel.STRAIGHT, 130,
        listOf("Mid-tempo", "Driving 8ths", "Power Chord", "Rock Ballad", "Half-time"),
    ),
    FUNK(
        "Funk", Feel.STRAIGHT, 100,
        listOf("One-Chord Vamp", "Dominant 9", "Minor Funk", "Sparse", "Busy"),
    ),
    SLOW_BALLAD(
        "Slow Ballad / Soul", Feel.STRAIGHT, 68,
        listOf("Slow Minor", "6/8 Ballad", "12/8 Soul", "Soul Groove", "Clean Rock Ballad"),
    ),
}
