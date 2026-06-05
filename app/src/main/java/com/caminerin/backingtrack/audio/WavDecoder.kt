package com.caminerin.backingtrack.audio

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal WAV decoder that reads 16-bit PCM mono WAV files into a FloatArray
 * normalized to [-1, 1]. Handles non-standard chunk layouts by scanning for the
 * "data" chunk rather than assuming a fixed header size.
 */
object WavDecoder {

    data class WavData(
        val samples: FloatArray,
        val sampleRate: Int,
    )

    fun decode(input: InputStream): WavData? {
        val bytes = input.use { it.readBytes() }
        if (bytes.size < 44) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // Verify RIFF
        if (bytes[0] != 'R'.code.toByte() || bytes[1] != 'I'.code.toByte()) return null

        // Read sample rate from fmt chunk
        var sampleRate = 44100
        var pos = 12
        var dataStart = -1
        var dataSize = 0

        while (pos + 8 <= bytes.size) {
            val chunkId = String(bytes, pos, 4, Charsets.US_ASCII)
            buf.position(pos + 4)
            val chunkSize = buf.int

            when (chunkId) {
                "fmt " -> {
                    buf.position(pos + 8 + 4) // skip audioFormat + numChannels
                    sampleRate = buf.int
                }
                "data" -> {
                    dataStart = pos + 8
                    dataSize = chunkSize
                    break
                }
            }
            pos += 8 + chunkSize
            if (chunkSize % 2 != 0) pos++
        }

        if (dataStart < 0 || dataStart >= bytes.size) return null
        val actualSize = minOf(dataSize, bytes.size - dataStart)
        val numSamples = actualSize / 2
        if (numSamples <= 0) return null

        buf.position(dataStart)
        val samples = FloatArray(numSamples)
        for (i in 0 until numSamples) {
            samples[i] = buf.short.toFloat() / 32768f
        }
        return WavData(samples, sampleRate)
    }
}
