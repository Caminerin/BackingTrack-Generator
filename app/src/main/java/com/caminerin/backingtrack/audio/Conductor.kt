package com.caminerin.backingtrack.audio

import com.caminerin.backingtrack.model.JamRecipe
import com.caminerin.backingtrack.model.Chord

/**
 * Builds the bar-by-bar timeline for an arrangement: which chord sounds in each
 * bar, the energy level (0..1), whether a drum fill happens at the end of the
 * bar, and whether the bar is the turnaround (the last bar of a progression
 * cycle, where the band sets up the return to the top).
 */
class Conductor(private val recipe: JamRecipe) {

    data class BarPlan(
        val index: Int,
        val chord: Chord,
        val energy: Float,
        val isFill: Boolean,
        val isLastBar: Boolean,
        /** Last bar of a progression cycle (e.g. bar 12 of a 12-bar blues). */
        val isTurnaround: Boolean = false,
        /** Position of this bar within the current cycle (0-based). */
        val barInCycle: Int = 0,
        /** Length in bars of the current progression cycle. */
        val cycleLength: Int = 1,
        /** Opening bars of the song: instruments lay back / thin out. */
        val isIntro: Boolean = false,
        /** Closing bar(s) of the song: wind down toward the final hit. */
        val isOutro: Boolean = false,
        /** Position of this bar across the whole song, 0..1. */
        val songPos: Float = 0f,
    )

    fun plan(): List<BarPlan> {
        val prog = if (recipe.progression.isNotEmpty()) recipe.progression
        else listOf(Chord(recipe.keySemitone, com.caminerin.backingtrack.model.ChordQuality.DOM7))

        val cycleLength = prog.sumOf { it.durationBars.coerceAtLeast(1) }.coerceAtLeast(1)

        // Expand progression to fill durationBars, honouring per-chord bar lengths.
        val expanded = mutableListOf<Chord>()
        var i = 0
        while (expanded.size < recipe.durationBars) {
            val c = prog[i % prog.size]
            repeat(c.durationBars.coerceAtLeast(1)) {
                if (expanded.size < recipe.durationBars) expanded.add(c)
            }
            i++
        }

        val total = expanded.size
        // Song-level shape: a short intro that lays back, a body that builds, and
        // a one-bar outro, so the arrangement breathes instead of being flat.
        val introBars = if (total >= 8) minOf(2, cycleLength) else 0
        val outroBars = if (total >= 8) 1 else 0
        return expanded.mapIndexed { idx, chord ->
            val isLast = idx == total - 1
            val isIntro = idx < introBars
            val isOutro = idx >= total - outroBars
            val songPos = if (total > 1) idx.toFloat() / (total - 1) else 0f
            val barInCycle = idx % cycleLength
            val isTurnaround = barInCycle == cycleLength - 1
            // Energy rises across the cycle and peaks into the turnaround/fill.
            val phrasePos = if (cycleLength > 1) barInCycle.toFloat() / (cycleLength - 1) else 0f
            // Macro arc across the whole song: soft intro, building body.
            val macro = when {
                isIntro -> 0.55f
                isOutro -> 0.7f
                else -> 0.8f + 0.2f * songPos
            }
            val baseEnergy = recipe.energy * macro
            val energy = (baseEnergy * 0.8f + phrasePos * 0.2f).coerceIn(0.2f, 1f)
            // Fill at the end of every 4-bar phrase, at the turnaround, and the
            // last bar — but not during the intro (keep the opening simple).
            val isFill = !isIntro && ((idx % 4 == 3) || isTurnaround || isLast)
            BarPlan(
                index = idx,
                chord = chord,
                energy = energy,
                isFill = isFill,
                isLastBar = isLast,
                isTurnaround = isTurnaround,
                barInCycle = barInCycle,
                cycleLength = cycleLength,
                isIntro = isIntro,
                isOutro = isOutro,
                songPos = songPos,
            )
        }
    }
}
