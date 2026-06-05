package com.caminerin.backingtrack.audio

import com.caminerin.backingtrack.model.GenerationQuality
import com.caminerin.backingtrack.model.JamRecipe
import com.caminerin.backingtrack.model.midiToHz

/**
 * Offline renderer. Generates per-instrument events, mixes the real samples into
 * separate stereo buses (with pan + gain), then sums to a master bus with a soft
 * limiter and peak normalisation.
 *
 * Output is interleaved stereo Float (L,R,L,R…) at 44100 Hz.
 */
class Renderer(private val pack: SamplePack) {

    data class RenderResult(
        val pcm: FloatArray,
        val sampleRate: Int,
        val durationMs: Long,
    )

    /** Per-bus pan in [-1,1] (L..R) and base gain. */
    private data class BusConfig(val pan: Float, val gain: Float)

    private val busConfigs = mapOf(
        "drums" to BusConfig(0f, 0.9f),
        "bass" to BusConfig(0f, 1.0f),
        "guitar" to BusConfig(-0.25f, 0.7f),
        "keys" to BusConfig(0.25f, 0.6f),
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

        val left = FloatArray(totalSamples)
        val right = FloatArray(totalSamples)

        // Count-in clicks (sticks) — uses real hi-hat sample as a click.
        renderCountIn(left, right, timing)

        val activeBuses = busConfigs.keys.filter { recipe.instruments[it]?.enabled == true }
        var done = 0
        for (inst in activeBuses) {
            val human = Humanizer(recipe.seed + inst.hashCode(), humanAmount)
            val events = when (inst) {
                "drums" -> DrumsGenerator(recipe, timing, human).generate(bars)
                "bass" -> BassGenerator(recipe, timing, human).generate(bars)
                "guitar" -> GuitarGenerator(recipe, timing, human).generate(bars)
                "keys" -> if (pack.hasPiano) KeysGenerator(recipe, timing, human).generate(bars) else emptyList()
                else -> emptyList()
            }
            val cfg = busConfigs.getValue(inst)
            val userVol = recipe.instruments[inst]?.volume ?: 0.75f
            val gain = cfg.gain * userVol
            val (lPan, rPan) = panGains(cfg.pan)

            for (ev in events) {
                val entry = sampleFor(inst, ev) ?: continue
                mixEvent(left, right, ev, entry, bodyStart, gain * lPan, gain * rPan)
            }
            done++
            progress?.invoke(done.toFloat() / activeBuses.size)
        }

        masterBus(left, right)

        val interleaved = FloatArray(totalSamples * 2)
        for (i in 0 until totalSamples) {
            interleaved[i * 2] = left[i]
            interleaved[i * 2 + 1] = right[i]
        }
        val durationMs = (totalSamples * 1000L) / SAMPLE_RATE
        return RenderResult(interleaved, SAMPLE_RATE, durationMs)
    }

    private fun renderCountIn(left: FloatArray, right: FloatArray, timing: Timing) {
        val click = pack.drum("hh_closed", "mid") ?: return
        for (beat in 0 until timing.beatsPerBar) {
            val start = beat * timing.samplesPerBeat
            val vel = if (beat == 0) 0.7f else 0.5f
            addSample(left, start, click.samples, vel * 0.6f)
            addSample(right, start, click.samples, vel * 0.6f)
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

    private fun mixEvent(
        left: FloatArray,
        right: FloatArray,
        ev: RenderEvent,
        data: SampleData,
        bodyStart: Int,
        gainL: Float,
        gainR: Float,
    ) {
        val start = bodyStart + ev.startSample
        val amp = ev.velocity
        if (data.pitchRatio == null || kotlin.math.abs(data.pitchRatio - 1.0) < 1e-4) {
            addSample(left, start, data.samples, amp * gainL)
            addSample(right, start, data.samples, amp * gainR)
        } else {
            addResampled(left, start, data.samples, data.pitchRatio, amp * gainL)
            addResampled(right, start, data.samples, data.pitchRatio, amp * gainR)
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

    /** Pitch-shift by linear-interpolated resampling (ratio>1 = higher pitch). */
    private fun addResampled(buf: FloatArray, start: Int, src: FloatArray, ratio: Double, gain: Float) {
        if (start >= buf.size) return
        val outLen = (src.size / ratio).toInt()
        val maxN = minOf(outLen, buf.size - start)
        var i = if (start < 0) -start else 0
        while (i < maxN) {
            val pos = i * ratio
            val idx = pos.toInt()
            if (idx + 1 >= src.size) break
            val frac = (pos - idx).toFloat()
            val s = src[idx] * (1 - frac) + src[idx + 1] * frac
            buf[start + i] += s * gain
            i++
        }
    }

    private fun panGains(pan: Float): Pair<Float, Float> {
        // Equal-power panning.
        val p = (pan.coerceIn(-1f, 1f) + 1f) / 2f
        val l = kotlin.math.cos(p * Math.PI / 2).toFloat()
        val r = kotlin.math.sin(p * Math.PI / 2).toFloat()
        return l to r
    }

    /** Soft limiter + normalise to -1.5 dB peak. */
    private fun masterBus(left: FloatArray, right: FloatArray) {
        var peak = 0f
        for (i in left.indices) {
            val l = softClip(left[i])
            val r = softClip(right[i])
            left[i] = l
            right[i] = r
            val m = maxOf(kotlin.math.abs(l), kotlin.math.abs(r))
            if (m > peak) peak = m
        }
        if (peak <= 0f) return
        val target = 0.84f // ~ -1.5 dB
        val norm = if (peak > target) target / peak else 1f
        if (norm != 1f) {
            for (i in left.indices) {
                left[i] *= norm
                right[i] *= norm
            }
        }
    }

    /** tanh-style soft clip for glue without hard distortion. */
    private fun softClip(x: Float): Float {
        val t = 0.7f
        return if (kotlin.math.abs(x) <= t) x
        else {
            val sign = if (x > 0) 1f else -1f
            sign * (t + (1 - t) * kotlin.math.tanh((kotlin.math.abs(x) - t) / (1 - t)))
        }
    }
}
