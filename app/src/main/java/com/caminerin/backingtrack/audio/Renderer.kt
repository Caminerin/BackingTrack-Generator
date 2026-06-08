package com.caminerin.backingtrack.audio

import com.caminerin.backingtrack.model.GenerationQuality
import com.caminerin.backingtrack.model.JamRecipe
import com.caminerin.backingtrack.model.midiToHz

/**
 * Offline renderer. Generates per-instrument events and mixes the real samples
 * into separate MONO buses, one per instrument. The buses ("stems") are returned
 * untouched so the player can mix them live with per-instrument volume / mute and
 * stereo pan — changing the mix never requires a re-render.
 *
 * Tonal notes are gated to the next onset with a short release so chords and bass
 * notes stop instead of ringing their full one-shot length on top of each other
 * (which otherwise turns the track into a muddy, dissonant wash).
 */
class Renderer(private val pack: SamplePack) {

    /** Multi-stem render result; every stem is mono at [sampleRate]. */
    data class RenderResult(
        val stems: Map<String, FloatArray>,
        val pan: Map<String, Float>,
        val countIn: FloatArray,
        val totalSamples: Int,
        val sampleRate: Int,
        val durationMs: Long,
        val bodyStart: Int,
    )

    /** Per-bus pan in [-1,1] (L..R) and base gain. */
    private data class BusConfig(val pan: Float, val gain: Float)

    private val busConfigs = mapOf(
        "drums" to BusConfig(0f, 0.92f),
        "bass" to BusConfig(0f, 1.0f),
        "guitar" to BusConfig(-0.28f, 0.72f),
        "keys" to BusConfig(0.28f, 0.62f),
    )

    /** Reverb wet amount per bus (bass kept nearly dry to avoid low-end mud). */
    private val reverbWet = mapOf(
        "drums" to 0.10f,
        "bass" to 0.03f,
        "guitar" to 0.18f,
        "keys" to 0.20f,
    )

    fun render(recipe: JamRecipe, progress: ((Float) -> Unit)? = null): RenderResult {
        val timing = Timing(recipe.bpm, recipe.timeSignatureBeats, recipe.feel)
        val bars = Conductor(recipe).plan()
        val humanAmount = if (recipe.quality == GenerationQuality.BEST) 1.0f else 0.5f

        val totalBars = bars.size
        val countInBars = 1
        val bodyStart = countInBars * timing.samplesPerBar
        val tailSamples = (SAMPLE_RATE * 2.0).toInt() // let final notes ring
        val totalSamples = bodyStart + totalBars * timing.samplesPerBar + tailSamples

        // Count-in clicks (own short mono buffer, plays once — skipped on loop).
        val countIn = FloatArray(bodyStart)
        renderCountIn(countIn, timing)

        // Render every bus into its own mono buffer regardless of the enabled
        // flag, so the live mixer can un-mute an instrument without re-rendering.
        val stems = LinkedHashMap<String, FloatArray>()
        val pans = LinkedHashMap<String, Float>()
        val buses = busConfigs.keys.toList()
        var done = 0
        for (inst in buses) {
            val buf = FloatArray(totalSamples)
            val cfg = busConfigs.getValue(inst)
            if (inst != "keys" || pack.hasPiano) {
                val human = Humanizer(recipe.seed + inst.hashCode(), humanAmount)
                val events = when (inst) {
                    "drums" -> DrumsGenerator(recipe, timing, human).generate(bars)
                    "bass" -> BassGenerator(recipe, timing, human).generate(bars)
                    "guitar" -> GuitarGenerator(recipe, timing, human).generate(bars)
                    "keys" -> KeysGenerator(recipe, timing, human).generate(bars)
                    else -> emptyList()
                }
                val gates = if (inst == "drums") null else computeGates(events)
                for ((idx, ev) in events.withIndex()) {
                    val entry = sampleFor(inst, ev) ?: continue
                    val gate = gates?.get(idx) ?: Int.MAX_VALUE
                    mixEventMono(buf, ev, entry, bodyStart, cfg.gain, gate)
                }
            }
            // Light room reverb for glue (skipped in QUICK mode for speed).
            if (recipe.quality == GenerationQuality.BEST) {
                applyReverb(buf, reverbWet[inst] ?: 0f)
            }
            stems[inst] = buf
            pans[inst] = cfg.pan
            done++
            progress?.invoke(done.toFloat() / buses.size)
        }

        normalizeStems(stems, countIn, recipe)

        val durationMs = (totalSamples * 1000L) / SAMPLE_RATE
        return RenderResult(stems, pans, countIn, totalSamples, SAMPLE_RATE, durationMs, bodyStart)
    }

    /**
     * For each (tonal) event, the gate length in OUTPUT samples = time until the
     * next onset that is more than [minGap] later (so the staggered notes of one
     * strum/chord are treated as a single hit). Int.MAX_VALUE = ring fully.
     */
    private fun computeGates(events: List<RenderEvent>): IntArray {
        val n = events.size
        val gates = IntArray(n) { Int.MAX_VALUE }
        if (n == 0) return gates
        val order = (0 until n).sortedBy { events[it].startSample }
        val minGap = (SAMPLE_RATE * 0.06).toInt()
        val minGate = (SAMPLE_RATE * 0.10).toInt()
        for (oi in order.indices) {
            val i = order[oi]
            val startI = events[i].startSample
            var gate = Int.MAX_VALUE
            for (oj in oi + 1 until order.size) {
                val d = events[order[oj]].startSample - startI
                if (d > minGap) { gate = d; break }
            }
            if (gate != Int.MAX_VALUE && gate < minGate) gate = minGate
            gates[i] = gate
        }
        return gates
    }

    private fun renderCountIn(mono: FloatArray, timing: Timing) {
        val click = pack.drum("hh_closed", "mid") ?: return
        for (beat in 0 until timing.beatsPerBar) {
            val start = beat * timing.samplesPerBeat
            val vel = if (beat == 0) 0.7f else 0.5f
            addSample(mono, start, click.samples, vel * 0.6f)
        }
    }

    private fun sampleFor(inst: String, ev: RenderEvent): SampleData? {
        return when (inst) {
            "drums" -> {
                val tier = velTier(ev.velocity)
                pack.drum(ev.drumHit ?: return null, tier)?.let {
                    SampleData(it.samples, it.sampleRate, null)
                }
            }
            "bass" -> pack.bass(ev.midi, (ev.velocity * 127).toInt())?.let {
                SampleData(it.samples, it.sampleRate, pitchRatio(it.rootMidi, ev.midi))
            }
            "guitar" -> pack.guitar(ev.midi, (ev.velocity * 127).toInt())?.let {
                SampleData(it.samples, it.sampleRate, pitchRatio(it.rootMidi, ev.midi))
            }
            "keys" -> pack.piano(ev.midi, (ev.velocity * 127).toInt())?.let {
                SampleData(it.samples, it.sampleRate, pitchRatio(it.rootMidi, ev.midi))
            }
            else -> null
        }
    }

    private data class SampleData(val samples: FloatArray, val sampleRate: Int, val pitchRatio: Double?)

    private fun pitchRatio(rootMidi: Int, targetMidi: Int): Double =
        midiToHz(targetMidi) / midiToHz(rootMidi)

    private fun velTier(v: Float): String = when {
        v < 0.4f -> "soft"
        v < 0.75f -> "mid"
        else -> "hard"
    }

    /**
     * Mix one event into a mono bus, optionally pitch-shifted, with an amplitude
     * envelope that holds until [gateSamples] then releases over [RELEASE_SAMPLES].
     */
    private fun mixEventMono(
        buf: FloatArray,
        ev: RenderEvent,
        data: SampleData,
        bodyStart: Int,
        busGain: Float,
        gateSamples: Int,
    ) {
        val start = bodyStart + ev.startSample
        if (start >= buf.size) return
        val amp = ev.velocity * busGain
        val ratio = data.pitchRatio
        val src = data.samples
        val straight = ratio == null || kotlin.math.abs(ratio - 1.0) < 1e-4
        val outLen = if (straight) src.size else (src.size / ratio!!).toInt()
        val gatedLen = if (gateSamples == Int.MAX_VALUE) outLen
            else minOf(outLen, gateSamples + RELEASE_SAMPLES)
        val maxN = minOf(gatedLen, buf.size - start)
        var i = if (start < 0) -start else 0
        while (i < maxN) {
            val s: Float = if (straight) {
                src[i]
            } else {
                val pos = i * ratio!!
                val idx = pos.toInt()
                if (idx + 1 >= src.size) break
                val frac = (pos - idx).toFloat()
                src[idx] * (1 - frac) + src[idx + 1] * frac
            }
            val env = if (gateSamples == Int.MAX_VALUE || i < gateSamples) 1f
                else 1f - (i - gateSamples).toFloat() / RELEASE_SAMPLES
            if (env <= 0f) break
            buf[start + i] += s * amp * env
            i++
        }
    }

    private fun addSample(buf: FloatArray, start: Int, src: FloatArray, gain: Float) {
        if (start >= buf.size) return
        var i = if (start < 0) -start else 0
        val n = minOf(src.size, buf.size - start)
        while (i < n) {
            buf[start + i] += src[i] * gain
            i++
        }
    }

    /**
     * Scale every stem by a single shared factor so the full default mix (all
     * buses at their default volume) peaks at a safe level. Live volume changes
     * then ride on top and are soft-clipped by the player.
     */
    private fun normalizeStems(stems: Map<String, FloatArray>, countIn: FloatArray, recipe: JamRecipe) {
        val n = stems.values.firstOrNull()?.size ?: return
        val vols = stems.keys.associateWith { recipe.instruments[it]?.volume ?: 0.75f }
        var peak = 0f
        for (i in 0 until n) {
            var sum = 0f
            for ((name, buf) in stems) sum += buf[i] * (vols[name] ?: 0.75f)
            val a = kotlin.math.abs(sum)
            if (a > peak) peak = a
        }
        for (v in countIn) {
            val a = kotlin.math.abs(v)
            if (a > peak) peak = a
        }
        if (peak <= 0f) return
        val target = 0.9f
        val factor = if (peak > target) target / peak else 1f
        if (factor == 1f) return
        for (buf in stems.values) for (i in buf.indices) buf[i] *= factor
        for (i in countIn.indices) countIn[i] *= factor
    }

    /**
     * In-place light room reverb (reduced Freeverb: 4 parallel comb filters into
     * 2 series all-pass filters). [wet] is the dry/wet blend; the dry signal is
     * preserved so live mixing still works on the stem.
     */
    private fun applyReverb(buf: FloatArray, wet: Float, roomSize: Float = 0.72f, damp: Float = 0.4f) {
        if (wet <= 0f) return
        val combTunings = intArrayOf(1116, 1188, 1277, 1356)
        val apTunings = intArrayOf(556, 441)
        val feedback = roomSize * 0.28f + 0.7f
        val n = buf.size
        val wetBuf = FloatArray(n)
        // Parallel comb filters with one-pole low-pass damping in the feedback.
        for (tuning in combTunings) {
            val delay = FloatArray(tuning)
            var idx = 0
            var lp = 0f
            for (i in 0 until n) {
                val out = delay[idx]
                lp = out * (1 - damp) + lp * damp
                delay[idx] = buf[i] + lp * feedback
                idx++; if (idx >= tuning) idx = 0
                wetBuf[i] += out
            }
        }
        val combScale = 1f / combTunings.size
        for (i in 0 until n) wetBuf[i] *= combScale
        // Series all-pass filters to diffuse the tail.
        for (tuning in apTunings) {
            val delay = FloatArray(tuning)
            var idx = 0
            val g = 0.5f
            for (i in 0 until n) {
                val bufOut = delay[idx]
                val input = wetBuf[i]
                delay[idx] = input + bufOut * g
                wetBuf[i] = bufOut - input * g
                idx++; if (idx >= tuning) idx = 0
            }
        }
        for (i in 0 until n) buf[i] = buf[i] + wetBuf[i] * wet
    }

    companion object {
        /** Samples of release fade applied after a note's gate (~40 ms). */
        private val RELEASE_SAMPLES = (SAMPLE_RATE * 0.04).toInt()

        /** Mix stems down to interleaved stereo using the given per-stem gains. */
        fun mixdown(result: RenderResult, gains: Map<String, Float>): FloatArray {
            val n = result.totalSamples
            val out = FloatArray(n * 2)
            for ((name, buf) in result.stems) {
                val g = gains[name] ?: 0f
                if (g <= 0f) continue
                val (lp, rp) = panGains(result.pan[name] ?: 0f)
                for (i in 0 until n) {
                    val v = buf[i] * g
                    out[i * 2] += v * lp
                    out[i * 2 + 1] += v * rp
                }
            }
            val ci = result.countIn
            for (i in ci.indices) {
                out[i * 2] += ci[i]
                out[i * 2 + 1] += ci[i]
            }
            for (i in out.indices) out[i] = softClip(out[i])
            return out
        }

        /** Equal-power panning. */
        fun panGains(pan: Float): Pair<Float, Float> {
            val p = (pan.coerceIn(-1f, 1f) + 1f) / 2f
            val l = kotlin.math.cos(p * Math.PI / 2).toFloat()
            val r = kotlin.math.sin(p * Math.PI / 2).toFloat()
            return l to r
        }

        /** tanh-style soft clip for glue without hard distortion. */
        fun softClip(x: Float): Float {
            val t = 0.7f
            return if (kotlin.math.abs(x) <= t) x
            else {
                val sign = if (x > 0) 1f else -1f
                sign * (t + (1 - t) * kotlin.math.tanh((kotlin.math.abs(x) - t) / (1 - t)))
            }
        }
    }
}
