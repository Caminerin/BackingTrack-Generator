package com.caminerin.backingtrack.audio

import android.content.Context
import org.json.JSONObject

/**
 * Loads the bundled Core Pack from assets/packs/core/.
 * Provides lookup by instrument + MIDI note (or drum hit) + velocity + round-robin.
 */
class SamplePack(context: Context) {

    data class SampleEntry(
        val samples: FloatArray,
        val sampleRate: Int,
        val rootMidi: Int,
        val loVel: Int,
        val hiVel: Int,
        val seq: Int,
    )

    data class DrumEntry(
        val samples: FloatArray,
        val sampleRate: Int,
        val hit: String,
        val vel: String,
        val seq: Int,
    )

    private val bassSamples = mutableListOf<SampleEntry>()
    private val guitarSamples = mutableListOf<SampleEntry>()
    private val pianoSamples = mutableListOf<SampleEntry>()
    private val drumSamples = mutableListOf<DrumEntry>()

    init {
        val am = context.assets
        val manifest = JSONObject(am.open("packs/core/manifest.json").bufferedReader().readText())

        loadTonal(am, manifest.getJSONObject("bass"), bassSamples)
        loadTonal(am, manifest.getJSONObject("guitar"), guitarSamples)
        if (manifest.has("piano")) loadTonal(am, manifest.getJSONObject("piano"), pianoSamples)
        loadDrums(am, manifest.getJSONObject("drums"))
    }

    private fun loadTonal(
        am: android.content.res.AssetManager,
        inst: JSONObject,
        dest: MutableList<SampleEntry>,
    ) {
        val arr = inst.getJSONArray("samples")
        for (i in 0 until arr.length()) {
            val s = arr.getJSONObject(i)
            val path = "packs/core/${s.getString("file")}"
            val wav = WavDecoder.decode(am.open(path)) ?: continue
            dest.add(
                SampleEntry(
                    samples = wav.samples,
                    sampleRate = wav.sampleRate,
                    rootMidi = s.getInt("rootMidi"),
                    loVel = s.optInt("loVel", 0),
                    hiVel = s.optInt("hiVel", 127),
                    seq = s.optInt("seq", 1),
                )
            )
        }
    }

    private fun loadDrums(am: android.content.res.AssetManager, inst: JSONObject) {
        val arr = inst.getJSONArray("samples")
        for (i in 0 until arr.length()) {
            val s = arr.getJSONObject(i)
            val path = "packs/core/${s.getString("file")}"
            val wav = WavDecoder.decode(am.open(path)) ?: continue
            drumSamples.add(
                DrumEntry(
                    samples = wav.samples,
                    sampleRate = wav.sampleRate,
                    hit = s.getString("hit"),
                    vel = s.getString("vel"),
                    seq = s.getInt("seq"),
                )
            )
        }
    }

    // ---- Lookup ----

    private var bassRr = 0
    private var guitarRr = 0
    private val drumRr = mutableMapOf<String, Int>()

    /**
     * Get a bass sample for a MIDI note at a given velocity (0..127).
     * Picks the closest rootMidi and cycles round-robins.
     */
    fun bass(midi: Int, velocity: Int = 100): SampleEntry? {
        val matching = bassSamples.filter { velocity in it.loVel..it.hiVel }
        if (matching.isEmpty()) return bassSamples.minByOrNull { kotlin.math.abs(it.rootMidi - midi) }
        val closest = matching.groupBy { kotlin.math.abs(it.rootMidi - midi) }
            .minByOrNull { it.key }?.value ?: return null
        bassRr = (bassRr + 1) % closest.size
        return closest[bassRr]
    }

    /**
     * Get a guitar sample for a MIDI note + velocity.
     */
    fun guitar(midi: Int, velocity: Int = 100): SampleEntry? {
        val matching = guitarSamples.filter { velocity in it.loVel..it.hiVel }
        if (matching.isEmpty()) return guitarSamples.minByOrNull { kotlin.math.abs(it.rootMidi - midi) }
        val closest = matching.groupBy { kotlin.math.abs(it.rootMidi - midi) }
            .minByOrNull { it.key }?.value ?: return null
        guitarRr = (guitarRr + 1) % closest.size
        return closest[guitarRr]
    }

    /**
     * Get a piano sample for a MIDI note + velocity.
     */
    fun piano(midi: Int, velocity: Int = 100): SampleEntry? {
        if (pianoSamples.isEmpty()) return null
        val matching = pianoSamples.filter { velocity in it.loVel..it.hiVel }
        if (matching.isEmpty()) return pianoSamples.minByOrNull { kotlin.math.abs(it.rootMidi - midi) }
        return matching.minByOrNull { kotlin.math.abs(it.rootMidi - midi) }
    }

    val hasPiano: Boolean get() = pianoSamples.isNotEmpty()

    /**
     * Get a drum sample for a hit name (kick, snare, hh_closed, …)
     * at a velocity tier (soft/mid/hard). Cycles round-robins.
     */
    fun drum(hit: String, velTier: String = "mid"): DrumEntry? {
        val matching = drumSamples.filter { it.hit == hit && it.vel == velTier }
        if (matching.isEmpty()) {
            return drumSamples.filter { it.hit == hit }.firstOrNull()
        }
        val key = "$hit/$velTier"
        val idx = drumRr.getOrDefault(key, 0)
        drumRr[key] = (idx + 1) % matching.size
        return matching[idx % matching.size]
    }
}
