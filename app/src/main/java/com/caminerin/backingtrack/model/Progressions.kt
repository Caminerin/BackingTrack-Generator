package com.caminerin.backingtrack.model

/**
 * Built-in chord-progression presets, expressed as scale degrees relative to the
 * selected key so they transpose automatically. Each entry is (degreeSemitone,
 * quality, bars).
 */
object Progressions {

    data class Preset(val name: String, val style: Style, val degrees: List<Triple<Int, ChordQuality, Int>>)

    private val I = 0
    private val II = 2
    private val III = 4
    private val IV = 5
    private val V = 7
    private val VI = 9
    private val bVII = 10

    val presets: List<Preset> = listOf(
        Preset(
            "12-Bar Blues", Style.BLUES,
            listOf(
                Triple(I, ChordQuality.DOM7, 4),
                Triple(IV, ChordQuality.DOM7, 2),
                Triple(I, ChordQuality.DOM7, 2),
                Triple(V, ChordQuality.DOM7, 1),
                Triple(IV, ChordQuality.DOM7, 1),
                Triple(I, ChordQuality.DOM7, 1),
                Triple(V, ChordQuality.DOM7, 1),
            ),
        ),
        Preset(
            "Quick-Change Blues", Style.BLUES,
            listOf(
                Triple(I, ChordQuality.DOM7, 1),
                Triple(IV, ChordQuality.DOM7, 1),
                Triple(I, ChordQuality.DOM7, 2),
                Triple(IV, ChordQuality.DOM7, 2),
                Triple(I, ChordQuality.DOM7, 2),
                Triple(V, ChordQuality.DOM7, 1),
                Triple(IV, ChordQuality.DOM7, 1),
                Triple(I, ChordQuality.DOM7, 1),
                Triple(V, ChordQuality.DOM7, 1),
            ),
        ),
        Preset(
            "Minor Blues", Style.BLUES,
            listOf(
                Triple(I, ChordQuality.MIN7, 4),
                Triple(IV, ChordQuality.MIN7, 2),
                Triple(I, ChordQuality.MIN7, 2),
                Triple(V, ChordQuality.MIN7, 1),
                Triple(IV, ChordQuality.MIN7, 1),
                Triple(I, ChordQuality.MIN7, 2),
            ),
        ),
        Preset(
            "Texas Shuffle", Style.BLUES_ROCK,
            listOf(
                Triple(I, ChordQuality.DOM7, 4),
                Triple(IV, ChordQuality.DOM7, 2),
                Triple(I, ChordQuality.DOM7, 2),
                Triple(V, ChordQuality.DOM7, 1),
                Triple(IV, ChordQuality.DOM7, 1),
                Triple(I, ChordQuality.DOM7, 1),
                Triple(V, ChordQuality.DOM7, 1),
            ),
        ),
        Preset(
            "I-bVII-IV Rock", Style.CLASSIC_ROCK,
            listOf(
                Triple(I, ChordQuality.MAJOR, 2),
                Triple(bVII, ChordQuality.MAJOR, 1),
                Triple(IV, ChordQuality.MAJOR, 1),
            ),
        ),
        Preset(
            "Power Rock I-IV-V", Style.CLASSIC_ROCK,
            listOf(
                Triple(I, ChordQuality.POWER, 2),
                Triple(IV, ChordQuality.POWER, 1),
                Triple(V, ChordQuality.POWER, 1),
            ),
        ),
        Preset(
            "Funk Dom9 Vamp", Style.FUNK,
            listOf(
                Triple(I, ChordQuality.DOM9, 4),
            ),
        ),
        Preset(
            "Minor Funk II-V", Style.FUNK,
            listOf(
                Triple(I, ChordQuality.MIN7, 2),
                Triple(IV, ChordQuality.DOM9, 2),
            ),
        ),
        Preset(
            "Soul I-vi-IV-V", Style.SLOW_BALLAD,
            listOf(
                Triple(I, ChordQuality.MAJOR, 1),
                Triple(VI, ChordQuality.MINOR, 1),
                Triple(IV, ChordQuality.MAJOR, 1),
                Triple(V, ChordQuality.MAJOR, 1),
            ),
        ),
        Preset(
            "Minor Ballad i-VI-III-VII", Style.SLOW_BALLAD,
            listOf(
                Triple(I, ChordQuality.MINOR, 1),
                Triple(VI, ChordQuality.MAJOR, 1),
                Triple(III, ChordQuality.MAJOR, 1),
                Triple(bVII, ChordQuality.MAJOR, 1),
            ),
        ),
    )

    fun forStyle(style: Style): List<Preset> = presets.filter { it.style == style }

    /** Realise a preset in a concrete key (root semitone 0..11). */
    fun realise(preset: Preset, keySemitone: Int): List<Chord> =
        preset.degrees.map { (deg, quality, bars) ->
            Chord((keySemitone + deg) % 12, quality, bars)
        }

    fun defaultFor(style: Style, keySemitone: Int): List<Chord> {
        val p = forStyle(style).firstOrNull() ?: presets.first()
        return realise(p, keySemitone)
    }
}
