package com.caminerin.backingtrack.model

/** Chromatic note names (sharps). Index = semitone offset from C. */
val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

/** Flat equivalents for display. */
val NOTE_NAMES_FLAT = arrayOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")

fun noteNameToSemitone(name: String): Int {
    val sharp = NOTE_NAMES.indexOf(name)
    if (sharp >= 0) return sharp
    val flat = NOTE_NAMES_FLAT.indexOf(name)
    if (flat >= 0) return flat
    return 0
}

/** MIDI note number for a given note name and octave. C4 = 60. */
fun midiNote(semitone: Int, octave: Int): Int = 12 * (octave + 1) + semitone

/** Frequency in Hz for a MIDI note number (A4 = 69 = 440 Hz). */
fun midiToHz(midi: Int): Double = 440.0 * Math.pow(2.0, (midi - 69) / 12.0)

/** Interval sets for chord qualities (semitones from root). */
enum class ChordQuality(val displayName: String, val intervals: IntArray) {
    MAJOR("", intArrayOf(0, 4, 7)),
    MINOR("m", intArrayOf(0, 3, 7)),
    DOM7("7", intArrayOf(0, 4, 7, 10)),
    MIN7("m7", intArrayOf(0, 3, 7, 10)),
    MAJ7("maj7", intArrayOf(0, 4, 7, 11)),
    DIM("dim", intArrayOf(0, 3, 6)),
    AUG("aug", intArrayOf(0, 4, 8)),
    SUS2("sus2", intArrayOf(0, 2, 7)),
    SUS4("sus4", intArrayOf(0, 5, 7)),
    DOM9("9", intArrayOf(0, 4, 7, 10, 14)),
    POWER("5", intArrayOf(0, 7));
}

data class Chord(
    val rootSemitone: Int,
    val quality: ChordQuality,
    val durationBars: Int = 1,
) {
    val displayName: String
        get() = NOTE_NAMES[rootSemitone % 12] + quality.displayName

    fun midiNotes(octave: Int = 3): List<Int> =
        quality.intervals.map { midiNote(rootSemitone, octave) + it }

    fun bassRoot(octave: Int = 2): Int = midiNote(rootSemitone, octave)
    fun bassFifth(octave: Int = 2): Int = midiNote(rootSemitone, octave) + 7
}

/**
 * A section of the arrangement (A, B, intro, outro).
 * Each section contains a chord progression (list of [Chord]).
 */
data class Section(
    val label: String,
    val chords: List<Chord>,
    val repeatCount: Int = 1,
)
