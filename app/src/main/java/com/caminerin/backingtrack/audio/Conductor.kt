package com.caminerin.backingtrack.audio

import com.caminerin.backingtrack.model.JamRecipe
import com.caminerin.backingtrack.model.Chord

/**
 * Builds the bar-by-bar timeline for an arrangement: which chord sounds in each
 * bar, the energy level (0..1), and whether a drum fill happens at the end of
 * the bar.
 */
class Conductor(private val recipe: JamRecipe) {

    data class BarPlan(
        val index: Int,
        val chord: Chord,
        val energy: Float,
        val isFill: Boolean,
        val isLastBar: Boolean,
    )

    fun plan(): List<BarPlan> {
        val prog = if (recipe.progression.isNotEmpty()) recipe.progression
        else listOf(Chord(recipe.keySemitone, com.caminerin.backingtrack.model.ChordQuality.DOM7))

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
            // Energy rises slightly across each 4-bar phrase, peaks before fills.
            val phrasePos = (idx % 4) / 3f
            val baseEnergy = recipe.energy
            val energy = (baseEnergy * 0.85f + phrasePos * 0.15f).coerceIn(0.2f, 1f)
            // Fill at the end of every 4-bar phrase (and the last bar).
            val isFill = (idx % 4 == 3) || isLast
            BarPlan(idx, chord, energy, isFill, isLast)
        }
    }
}
