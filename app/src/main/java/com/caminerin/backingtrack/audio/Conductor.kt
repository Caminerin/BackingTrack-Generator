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
        return expanded.mapIndexed { idx, chord ->
            val isLast = idx == total - 1
            val barInCycle = idx % cycleLength
            val isTurnaround = barInCycle == cycleLength - 1
            // Energy rises across the cycle and peaks into the turnaround/fill.
            val phrasePos = if (cycleLength > 1) barInCycle.toFloat() / (cycleLength - 1) else 0f
            val baseEnergy = recipe.energy
            val energy = (baseEnergy * 0.8f + phrasePos * 0.2f).coerceIn(0.2f, 1f)
            // Fill at the end of every 4-bar phrase, at the turnaround, and the last bar.
            val isFill = (idx % 4 == 3) || isTurnaround || isLast
            BarPlan(
                index = idx,
                chord = chord,
                energy = energy,
                isFill = isFill,
                isLastBar = isLast,
                isTurnaround = isTurnaround,
                barInCycle = barInCycle,
                cycleLength = cycleLength,
            )
        }
    }
}
