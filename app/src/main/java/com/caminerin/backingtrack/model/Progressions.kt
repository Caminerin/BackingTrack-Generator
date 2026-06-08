package com.caminerin.backingtrack.model

/**
 * Built-in chord-progression presets, expressed as scale degrees relative to the
 * selected key so they transpose automatically. Each entry is (degreeSemitone,
 * quality, bars).
 */
object Progressions {

    data class Preset(val name: String, val style: Style, val degrees: List<Triple<Int, ChordQuality, Int>>)

    private val I = 0
    private val bII = 1
    private val II = 2
    private val bIII = 3
    private val III = 4
    private val IV = 5
    private val bV = 6
    private val V = 7
    private val bVI = 8
    private val VI = 9
    private val bVII = 10
    private val VII = 11

    // ----- BLUES -------------------------------------------------------
    private val blues = listOf(
        Preset(
            "12-Bar Blues", Style.BLUES,
            listOf(
                Triple(I, ChordQuality.DOM7, 4), Triple(IV, ChordQuality.DOM7, 2),
                Triple(I, ChordQuality.DOM7, 2), Triple(V, ChordQuality.DOM7, 1),
                Triple(IV, ChordQuality.DOM7, 1), Triple(I, ChordQuality.DOM7, 1),
                Triple(V, ChordQuality.DOM7, 1),
            ),
        ),
        Preset(
            "Quick-Change Blues", Style.BLUES,
            listOf(
                Triple(I, ChordQuality.DOM7, 1), Triple(IV, ChordQuality.DOM7, 1),
                Triple(I, ChordQuality.DOM7, 2), Triple(IV, ChordQuality.DOM7, 2),
                Triple(I, ChordQuality.DOM7, 2), Triple(V, ChordQuality.DOM7, 1),
                Triple(IV, ChordQuality.DOM7, 1), Triple(I, ChordQuality.DOM7, 1),
                Triple(V, ChordQuality.DOM7, 1),
            ),
        ),
        Preset(
            "Minor Blues", Style.BLUES,
            listOf(
                Triple(I, ChordQuality.MIN7, 4), Triple(IV, ChordQuality.MIN7, 2),
                Triple(I, ChordQuality.MIN7, 2), Triple(V, ChordQuality.MIN7, 1),
                Triple(IV, ChordQuality.MIN7, 1), Triple(I, ChordQuality.MIN7, 2),
            ),
        ),
        Preset(
            "Jazz Blues (turnaround)", Style.BLUES,
            listOf(
                Triple(I, ChordQuality.DOM9, 1), Triple(IV, ChordQuality.DOM9, 1),
                Triple(I, ChordQuality.DOM7, 2), Triple(IV, ChordQuality.DOM9, 2),
                Triple(I, ChordQuality.DOM7, 1), Triple(VI, ChordQuality.DOM7, 1),
                Triple(II, ChordQuality.MIN7, 1), Triple(V, ChordQuality.DOM7, 1),
                Triple(I, ChordQuality.DOM7, 1), Triple(V, ChordQuality.DOM7, 1),
            ),
        ),
        Preset(
            "Slow Blues 12/8", Style.BLUES,
            listOf(
                Triple(I, ChordQuality.DOM9, 4), Triple(IV, ChordQuality.DOM9, 2),
                Triple(I, ChordQuality.DOM7, 2), Triple(V, ChordQuality.DOM9, 1),
                Triple(IV, ChordQuality.DOM7, 1), Triple(I, ChordQuality.DOM7, 1),
                Triple(V, ChordQuality.DOM7, 1),
            ),
        ),
        Preset(
            "Stormy Monday", Style.BLUES,
            listOf(
                Triple(I, ChordQuality.DOM9, 1), Triple(IV, ChordQuality.DOM9, 1),
                Triple(I, ChordQuality.DOM9, 1), Triple(I, ChordQuality.DOM7, 1),
                Triple(IV, ChordQuality.DOM9, 2),
                Triple(I, ChordQuality.DOM9, 1), Triple(bIII, ChordQuality.DOM9, 1),
                Triple(II, ChordQuality.MIN7, 1), Triple(V, ChordQuality.DOM9, 1),
                Triple(I, ChordQuality.DOM9, 1), Triple(V, ChordQuality.DOM9, 1),
            ),
        ),
    )

    // ----- BLUES ROCK --------------------------------------------------
    private val bluesRock = listOf(
        Preset(
            "Texas Shuffle", Style.BLUES_ROCK,
            listOf(
                Triple(I, ChordQuality.DOM7, 4), Triple(IV, ChordQuality.DOM7, 2),
                Triple(I, ChordQuality.DOM7, 2), Triple(V, ChordQuality.DOM7, 1),
                Triple(IV, ChordQuality.DOM7, 1), Triple(I, ChordQuality.DOM7, 1),
                Triple(V, ChordQuality.DOM7, 1),
            ),
        ),
        Preset(
            "Straight Blues Rock", Style.BLUES_ROCK,
            listOf(
                Triple(I, ChordQuality.POWER, 4), Triple(IV, ChordQuality.POWER, 2),
                Triple(I, ChordQuality.POWER, 2), Triple(V, ChordQuality.POWER, 1),
                Triple(IV, ChordQuality.POWER, 1), Triple(I, ChordQuality.POWER, 1),
                Triple(V, ChordQuality.POWER, 1),
            ),
        ),
        Preset(
            "Heavy Shuffle", Style.BLUES_ROCK,
            listOf(
                Triple(I, ChordQuality.DOM7, 2), Triple(bVII, ChordQuality.DOM7, 1),
                Triple(IV, ChordQuality.DOM7, 1),
            ),
        ),
        Preset(
            "Minor Blues Rock", Style.BLUES_ROCK,
            listOf(
                Triple(I, ChordQuality.MIN7, 4), Triple(IV, ChordQuality.MIN7, 2),
                Triple(I, ChordQuality.MIN7, 2), Triple(bVI, ChordQuality.MAJOR, 1),
                Triple(V, ChordQuality.DOM7, 1), Triple(I, ChordQuality.MIN7, 2),
            ),
        ),
        Preset(
            "Rock Ballad I-V-IV", Style.BLUES_ROCK,
            listOf(
                Triple(I, ChordQuality.MAJOR, 2), Triple(V, ChordQuality.MAJOR, 1),
                Triple(IV, ChordQuality.MAJOR, 1),
            ),
        ),
    )

    // ----- CLASSIC ROCK ------------------------------------------------
    private val classicRock = listOf(
        Preset(
            "I-bVII-IV Rock", Style.CLASSIC_ROCK,
            listOf(
                Triple(I, ChordQuality.MAJOR, 2), Triple(bVII, ChordQuality.MAJOR, 1),
                Triple(IV, ChordQuality.MAJOR, 1),
            ),
        ),
        Preset(
            "Power Rock I-IV-V", Style.CLASSIC_ROCK,
            listOf(
                Triple(I, ChordQuality.POWER, 2), Triple(IV, ChordQuality.POWER, 1),
                Triple(V, ChordQuality.POWER, 1),
            ),
        ),
        Preset(
            "Pop-Rock I-V-vi-IV", Style.CLASSIC_ROCK,
            listOf(
                Triple(I, ChordQuality.MAJOR, 1), Triple(V, ChordQuality.MAJOR, 1),
                Triple(VI, ChordQuality.MINOR, 1), Triple(IV, ChordQuality.MAJOR, 1),
            ),
        ),
        Preset(
            "50s I-vi-IV-V", Style.CLASSIC_ROCK,
            listOf(
                Triple(I, ChordQuality.MAJOR, 1), Triple(VI, ChordQuality.MINOR, 1),
                Triple(IV, ChordQuality.MAJOR, 1), Triple(V, ChordQuality.MAJOR, 1),
            ),
        ),
        Preset(
            "Minor i-bVII-bVI-V", Style.CLASSIC_ROCK,
            listOf(
                Triple(I, ChordQuality.MINOR, 1), Triple(bVII, ChordQuality.MAJOR, 1),
                Triple(bVI, ChordQuality.MAJOR, 1), Triple(V, ChordQuality.MAJOR, 1),
            ),
        ),
        Preset(
            "Driving I-IV-bVII", Style.CLASSIC_ROCK,
            listOf(
                Triple(I, ChordQuality.POWER, 2), Triple(IV, ChordQuality.POWER, 1),
                Triple(bVII, ChordQuality.POWER, 1),
            ),
        ),
    )

    // ----- FUNK --------------------------------------------------------
    private val funk = listOf(
        Preset(
            "Funk Dom9 Vamp", Style.FUNK,
            listOf(Triple(I, ChordQuality.DOM9, 4)),
        ),
        Preset(
            "Minor Funk II-V", Style.FUNK,
            listOf(
                Triple(I, ChordQuality.MIN7, 2), Triple(IV, ChordQuality.DOM9, 2),
            ),
        ),
        Preset(
            "Hendrix 7#9", Style.FUNK,
            listOf(Triple(I, ChordQuality.DOM7SHARP9, 4)),
        ),
        Preset(
            "Dorian Groove", Style.FUNK,
            listOf(
                Triple(I, ChordQuality.MIN9, 2), Triple(IV, ChordQuality.DOM9, 2),
            ),
        ),
        Preset(
            "Funk Two-Chord", Style.FUNK,
            listOf(
                Triple(I, ChordQuality.DOM9, 2), Triple(bVII, ChordQuality.DOM9, 2),
            ),
        ),
        Preset(
            "Chromatic Funk", Style.FUNK,
            listOf(
                Triple(I, ChordQuality.DOM9, 1), Triple(bII, ChordQuality.DOM9, 1),
                Triple(I, ChordQuality.DOM9, 2),
            ),
        ),
    )

    // ----- SLOW BALLAD / SOUL ------------------------------------------
    private val ballad = listOf(
        Preset(
            "Soul I-vi-IV-V", Style.SLOW_BALLAD,
            listOf(
                Triple(I, ChordQuality.MAJ7, 1), Triple(VI, ChordQuality.MIN7, 1),
                Triple(IV, ChordQuality.MAJ7, 1), Triple(V, ChordQuality.DOM7, 1),
            ),
        ),
        Preset(
            "Minor Ballad i-VI-III-VII", Style.SLOW_BALLAD,
            listOf(
                Triple(I, ChordQuality.MINOR, 1), Triple(bVI, ChordQuality.MAJOR, 1),
                Triple(bIII, ChordQuality.MAJOR, 1), Triple(bVII, ChordQuality.MAJOR, 1),
            ),
        ),
        Preset(
            "Doo-Wop I-vi-ii-V", Style.SLOW_BALLAD,
            listOf(
                Triple(I, ChordQuality.MAJ7, 1), Triple(VI, ChordQuality.MIN7, 1),
                Triple(II, ChordQuality.MIN7, 1), Triple(V, ChordQuality.DOM7, 1),
            ),
        ),
        Preset(
            "Gospel I-IV-I-V", Style.SLOW_BALLAD,
            listOf(
                Triple(I, ChordQuality.MAJ7, 2), Triple(IV, ChordQuality.MAJ7, 2),
                Triple(I, ChordQuality.MAJ7, 2), Triple(V, ChordQuality.DOM9, 2),
            ),
        ),
        Preset(
            "Pop Ballad I-V-vi-IV", Style.SLOW_BALLAD,
            listOf(
                Triple(I, ChordQuality.MAJ7, 1), Triple(V, ChordQuality.MAJOR, 1),
                Triple(VI, ChordQuality.MIN7, 1), Triple(IV, ChordQuality.MAJ9, 1),
            ),
        ),
        Preset(
            "12-Bar Soul Blues", Style.SLOW_BALLAD,
            listOf(
                Triple(I, ChordQuality.DOM9, 4), Triple(IV, ChordQuality.DOM9, 2),
                Triple(I, ChordQuality.DOM7, 2), Triple(V, ChordQuality.DOM9, 1),
                Triple(IV, ChordQuality.DOM7, 1), Triple(I, ChordQuality.DOM7, 1),
                Triple(V, ChordQuality.DOM7, 1),
            ),
        ),
    )

    val presets: List<Preset> = blues + bluesRock + classicRock + funk + ballad

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

    /** Pick a deterministic-but-varied preset from seed (for the dice button). */
    fun randomFor(style: Style, keySemitone: Int, seed: Long): List<Chord> {
        val pool = forStyle(style).ifEmpty { listOf(presets.first()) }
        val p = pool[(seed % pool.size).toInt().let { if (it < 0) it + pool.size else it }]
        return realise(p, keySemitone)
    }
}
