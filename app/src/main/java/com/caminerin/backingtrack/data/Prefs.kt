package com.caminerin.backingtrack.data

import android.content.Context

/** Tiny persistence for favorites and the (demo) premium unlock flag. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("backingtrack", Context.MODE_PRIVATE)

    fun favorites(): Set<String> =
        sp.getStringSet(KEY_FAV, emptySet())?.toSet() ?: emptySet()

    fun setFavorite(id: String, fav: Boolean) {
        val cur = favorites().toMutableSet()
        if (fav) cur.add(id) else cur.remove(id)
        sp.edit().putStringSet(KEY_FAV, cur).apply()
    }

    var premium: Boolean
        get() = sp.getBoolean(KEY_PREMIUM, false)
        set(value) = sp.edit().putBoolean(KEY_PREMIUM, value).apply()

    companion object {
        private const val KEY_FAV = "favorites"
        private const val KEY_PREMIUM = "premium"
    }
}
