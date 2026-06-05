package com.caminerin.backingtrack.data

import com.caminerin.backingtrack.model.Chord
import com.caminerin.backingtrack.model.ChordQuality
import com.caminerin.backingtrack.model.Feel
import com.caminerin.backingtrack.model.GenerationQuality
import com.caminerin.backingtrack.model.InstrumentMix
import com.caminerin.backingtrack.model.JamRecipe
import com.caminerin.backingtrack.model.LibraryItem
import com.caminerin.backingtrack.model.Style
import org.json.JSONArray
import org.json.JSONObject

/** (De)serialises domain models to/from JSON using org.json (no extra deps). */
object RecipeJson {

    fun chordToJson(c: Chord): JSONObject = JSONObject().apply {
        put("root", c.rootSemitone)
        put("quality", c.quality.name)
        put("bars", c.durationBars)
    }

    fun chordFromJson(o: JSONObject): Chord = Chord(
        rootSemitone = o.getInt("root"),
        quality = ChordQuality.valueOf(o.getString("quality")),
        durationBars = o.optInt("bars", 1),
    )

    fun recipeToJson(r: JamRecipe): JSONObject = JSONObject().apply {
        put("id", r.id)
        put("name", r.name)
        put("style", r.style.name)
        put("keySemitone", r.keySemitone)
        put("bpm", r.bpm)
        put("feel", r.feel.name)
        put("timeSignatureBeats", r.timeSignatureBeats)
        put("progression", JSONArray(r.progression.map { chordToJson(it) }))
        put("durationBars", r.durationBars)
        put("instruments", JSONObject().apply {
            r.instruments.forEach { (k, v) ->
                put(k, JSONObject().apply {
                    put("enabled", v.enabled)
                    put("volume", v.volume.toDouble())
                })
            }
        })
        put("quality", r.quality.name)
        put("density", r.density.toDouble())
        put("energy", r.energy.toDouble())
        put("variationLevel", r.variationLevel.toDouble())
        put("seed", r.seed)
        put("createdAt", r.createdAt)
        put("updatedAt", r.updatedAt)
    }

    fun recipeFromJson(o: JSONObject): JamRecipe {
        val prog = mutableListOf<Chord>()
        val arr = o.optJSONArray("progression") ?: JSONArray()
        for (i in 0 until arr.length()) prog.add(chordFromJson(arr.getJSONObject(i)))

        val inst = mutableMapOf<String, InstrumentMix>()
        val io = o.optJSONObject("instruments")
        if (io != null) {
            for (key in io.keys()) {
                val v = io.getJSONObject(key)
                inst[key] = InstrumentMix(v.optBoolean("enabled", true), v.optDouble("volume", 0.75).toFloat())
            }
        }

        return JamRecipe(
            id = o.getString("id"),
            name = o.optString("name", ""),
            style = Style.valueOf(o.getString("style")),
            keySemitone = o.getInt("keySemitone"),
            bpm = o.getInt("bpm"),
            feel = Feel.valueOf(o.getString("feel")),
            timeSignatureBeats = o.optInt("timeSignatureBeats", 4),
            progression = prog,
            durationBars = o.optInt("durationBars", 12),
            instruments = if (inst.isEmpty()) JamRecipe.defaultInstruments() else inst,
            quality = GenerationQuality.valueOf(o.optString("quality", "BEST")),
            density = o.optDouble("density", 0.6).toFloat(),
            energy = o.optDouble("energy", 0.6).toFloat(),
            variationLevel = o.optDouble("variationLevel", 0.5).toFloat(),
            seed = o.optLong("seed", System.currentTimeMillis()),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
        )
    }

    fun itemToJson(item: LibraryItem): JSONObject = JSONObject().apply {
        put("id", item.id)
        put("title", item.title)
        put("recipe", recipeToJson(item.recipe))
        put("audioFilePath", item.audioFilePath)
        put("durationMs", item.durationMs)
        put("format", item.format)
        put("favorite", item.favorite)
        put("lastPlayed", item.lastPlayed)
        put("createdAt", item.createdAt)
    }

    fun itemFromJson(o: JSONObject): LibraryItem = LibraryItem(
        id = o.getString("id"),
        title = o.getString("title"),
        recipe = recipeFromJson(o.getJSONObject("recipe")),
        audioFilePath = o.optString("audioFilePath", ""),
        durationMs = o.optLong("durationMs", 0),
        format = o.optString("format", "wav"),
        favorite = o.optBoolean("favorite", false),
        lastPlayed = o.optLong("lastPlayed", 0),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
    )
}
