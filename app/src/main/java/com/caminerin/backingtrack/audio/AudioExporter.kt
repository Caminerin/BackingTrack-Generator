package com.caminerin.backingtrack.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Writes a rendered interleaved-stereo float buffer to WAV (PCM-16) or M4A (AAC).
 */
object AudioExporter {

    fun writeWav(file: File, interleaved: FloatArray, sampleRate: Int, channels: Int = 2) {
        val numSamples = interleaved.size
        val dataSize = numSamples * 2 // 16-bit
        val raf = RandomAccessFile(file, "rw")
        raf.setLength(0)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(36 + dataSize)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1) // PCM
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(sampleRate * channels * 2)
        header.putShort((channels * 2).toShort())
        header.putShort(16)
        header.put("data".toByteArray())
        header.putInt(dataSize)
        raf.write(header.array())

        val buf = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
        for (f in interleaved) {
            val v = (f.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            buf.putShort(v)
        }
        raf.write(buf.array())
        raf.close()
    }

    /**
     * Encode to AAC in an M4A container via MediaCodec + MediaMuxer.
     */
    fun writeM4a(file: File, interleaved: FloatArray, sampleRate: Int, channels: Int = 2, bitRate: Int = 192000) {
        if (file.exists()) file.delete()
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false

        // Convert floats to 16-bit PCM bytes.
        val pcm = ByteBuffer.allocate(interleaved.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (f in interleaved) pcm.putShort((f.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        pcm.flip()

        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var presentationTimeUs = 0L
        val bytesPerUs = sampleRate.toDouble() * channels * 2 / 1_000_000.0

        while (true) {
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(10000)
                if (inIndex >= 0) {
                    val inBuf = codec.getInputBuffer(inIndex)!!
                    inBuf.clear()
                    val chunk = minOf(inBuf.capacity(), pcm.remaining())
                    if (chunk > 0) {
                        val slice = ByteArray(chunk)
                        pcm.get(slice)
                        inBuf.put(slice)
                        codec.queueInputBuffer(inIndex, 0, chunk, presentationTimeUs, 0)
                        presentationTimeUs += (chunk / bytesPerUs).toLong()
                    } else {
                        codec.queueInputBuffer(inIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    }
                }
            }

            val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
            if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                trackIndex = muxer.addTrack(codec.outputFormat)
                muxer.start()
                muxerStarted = true
            } else if (outIndex >= 0) {
                val outBuf = codec.getOutputBuffer(outIndex)!!
                if (bufferInfo.size > 0 && muxerStarted &&
                    (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                ) {
                    outBuf.position(bufferInfo.offset)
                    outBuf.limit(bufferInfo.offset + bufferInfo.size)
                    muxer.writeSampleData(trackIndex, outBuf, bufferInfo)
                }
                codec.releaseOutputBuffer(outIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        }

        codec.stop()
        codec.release()
        if (muxerStarted) {
            muxer.stop()
        }
        muxer.release()
    }
}
