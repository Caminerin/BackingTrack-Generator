package com.caminerin.backingtrack.data

import android.content.Context
import com.caminerin.backingtrack.model.ChordEvent
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
                        // New catalog uses "name" (base asset); fall back to the
                        // legacy "audio" field for older catalogs.
                        val asset = if (sj.has("name")) sj.getString("name")
                            else sj.getString("audio")
                        stems.add(Stem(sj.getString("instrument"), asset))
                    }
                } else {
                    // Backwards-compatible single mixed file.
                    stems.add(Stem("Pista", to.getString("audio")))
                }
                val chords = ArrayList<ChordEvent>()
                val chordsJson = to.optJSONArray("chords")
                if (chordsJson != null) {
                    for (i in 0 until chordsJson.length()) {
                        val cj = chordsJson.getJSONObject(i)
                        chords.add(ChordEvent(cj.getInt("beat"), cj.getString("name")))
                    }
                }
                tracks.add(
                    Track(
                        id = to.getString("id"),
                        title = to.getString("title"),
                        styleId = sid,
                        styleName = sname,
                        subStyle = to.optString("subStyle", ""),
                        key = to.getString("key"),
                        bpm = to.getInt("bpm"),
                        durationSec = to.getInt("durationSec"),
                        timeSignature = to.optString("timeSignature", "4/4"),
                        feel = to.optString("feel", "Straight"),
                        stems = stems,
                        chords = chords,
                        free = to.optBoolean("free", t < 2),
                        preview = to.optString("preview", ""),
                    )
                )
            }
            styles.add(Style(sid, sname, tracks))
        }
        return styles
    }
}
