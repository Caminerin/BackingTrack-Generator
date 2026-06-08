package com.caminerin.backingtrack.data

import android.content.Context
import com.caminerin.backingtrack.model.Stem
import com.caminerin.backingtrack.model.Style
import com.caminerin.backingtrack.model.Track
import org.json.JSONObject

/** Loads the bundled catalog.json (10 styles x 10 tracks) from assets. */
object CatalogRepository {
    fun load(context: Context): List<Style> {
        val text = context.assets.open("catalog.json")
            .bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        val stylesJson = root.getJSONArray("styles")
        val styles = ArrayList<Style>(stylesJson.length())
        for (s in 0 until stylesJson.length()) {
            val so = stylesJson.getJSONObject(s)
            val sid = so.getString("id")
            val sname = so.getString("name")
            val tracksJson = so.getJSONArray("tracks")
            val tracks = ArrayList<Track>(tracksJson.length())
            for (t in 0 until tracksJson.length()) {
                val to = tracksJson.getJSONObject(t)
                val stems = ArrayList<Stem>()
                val stemsJson = to.optJSONArray("stems")
                if (stemsJson != null) {
                    for (i in 0 until stemsJson.length()) {
                        val sj = stemsJson.getJSONObject(i)
                        stems.add(Stem(sj.getString("instrument"), sj.getString("audio")))
                    }
                } else {
                    // Backwards-compatible single mixed file.
                    stems.add(Stem("Pista", to.getString("audio")))
                }
                tracks.add(
                    Track(
                        id = to.getString("id"),
                        title = to.getString("title"),
                        styleId = sid,
                        styleName = sname,
                        key = to.getString("key"),
                        bpm = to.getInt("bpm"),
                        durationSec = to.getInt("durationSec"),
                        stems = stems,
                        free = to.optBoolean("free", t < 2),
                    )
                )
            }
            styles.add(Style(sid, sname, tracks))
        }
        return styles
    }
}
