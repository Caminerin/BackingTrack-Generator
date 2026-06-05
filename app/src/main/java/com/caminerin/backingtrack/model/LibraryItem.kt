package com.caminerin.backingtrack.model

/**
 * A saved backing track in the local library.
 * Holds both the recipe (for re-generation) and the path to the rendered audio.
 */
data class LibraryItem(
    val id: String,
    val title: String,
    val recipe: JamRecipe,
    val audioFilePath: String = "",
    val durationMs: Long = 0,
    val format: String = "wav",
    val favorite: Boolean = false,
    val lastPlayed: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
)
