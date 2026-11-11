package com.osutiles.game.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Result of decoding: mono float samples (-1..1) plus sample rate. */
data class DecodedAudio(val samples: FloatArray, val sampleRate: Int)

/**
 * Decodes an arbitrary audio file (any codec Android can read: mp3, aac/m4a,
 * ogg/vorbis, flac, wav, ...) into mono PCM floats for onset analysis.
 *
 * To keep memory and analysis time sane for arbitrarily large user uploads it
 * decodes at most [maxSeconds] of audio and down-mixes to mono. The full track
 * is still played back untouched by [android.media.MediaPlayer] during the game.
 */
object PcmDecoder {

    fun decode(context: Context, uri: Uri, maxSeconds: Int = 180): DecodedAudio? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val trackIndex = selectAudioTrack(extractor) ?: return null
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)

            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null

            val maxSamples = sampleRate.toLong() * maxSeconds

            // Raw PCM (e.g. WAV) has no decoder on many devices; read it directly.
            if (mime == "audio/raw") {
                val pcmEncoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING))
                    format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                else
                    AudioFormat.ENCODING_PCM_16BIT
                return decodeRaw(extractor, channels, sampleRate, maxSamples, pcmEncoding)
            }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val out = FloatArrayBuilder(initialCapacity = (sampleRate * 8))
            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false

            while (!sawOutputEos && out.size < maxSamples) {
                if (!sawInputEos) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex) ?: continue
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outIndex >= 0) {
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawOutputEos = true
                    }
                    if (bufferInfo.size > 0) {
                        val outBuf = codec.getOutputBuffer(outIndex)
                        if (outBuf != null) {
                            appendMono(outBuf, bufferInfo, channels, out)
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                }
            }

            return DecodedAudio(out.toArray(), sampleRate)
        } catch (t: Throwable) {
            return null
        } finally {
            try { codec?.stop() } catch (_: Throwable) {}
            try { codec?.release() } catch (_: Throwable) {}
            try { extractor.release() } catch (_: Throwable) {}
        }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) return i
        }
        return null
    }

    /**
     * Read already-decoded PCM straight from the extractor (no codec needed).
     * Returns null for encodings we don't understand so callers fall back to a
     * steady-rhythm map instead of analyzing garbage.
     */
    private fun decodeRaw(
        extractor: MediaExtractor,
        channels: Int,
        sampleRate: Int,
        maxSamples: Long,
        pcmEncoding: Int
    ): DecodedAudio? {
        if (pcmEncoding != AudioFormat.ENCODING_PCM_16BIT &&
            pcmEncoding != AudioFormat.ENCODING_PCM_8BIT &&
            pcmEncoding != AudioFormat.ENCODING_PCM_FLOAT
        ) return null
        val out = FloatArrayBuilder(initialCapacity = sampleRate * 8)
        val buffer = java.nio.ByteBuffer.allocate(64 * 1024)
        val info = MediaCodec.BufferInfo()
        while (out.size < maxSamples) {
            buffer.clear()
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            info.offset = 0
            info.size = size
            appendMono(buffer, info, channels, pcmEncoding, out)
            if (!extractor.advance()) break
        }
        return DecodedAudio(out.toArray(), sampleRate)
    }

    /** Decoder/raw output is interleaved PCM; down-mix to mono floats. */
    private fun appendMono(
        buf: ByteBuffer,
        info: MediaCodec.BufferInfo,
        channels: Int,
        out: FloatArrayBuilder
    ) = appendMono(buf, info, channels, AudioFormat.ENCODING_PCM_16BIT, out)

    private fun appendMono(
        buf: ByteBuffer,
        info: MediaCodec.BufferInfo,
        channels: Int,
        pcmEncoding: Int,
        out: FloatArrayBuilder
    ) {
        buf.position(info.offset)
        buf.limit(info.offset + info.size)
        buf.order(ByteOrder.LITTLE_ENDIAN)
        val ch = channels.coerceAtLeast(1)
        when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_8BIT -> {
                // 8-bit PCM is unsigned, centered at 128.
                val frames = info.size / ch
                for (f in 0 until frames) {
                    var sum = 0f
                    for (c in 0 until ch) sum += ((buf.get().toInt() and 0xFF) - 128) / 128f
                    out.add(sum / ch)
                }
            }
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val floats = buf.asFloatBuffer()
                val frames = floats.remaining() / ch
                for (f in 0 until frames) {
                    var sum = 0f
                    for (c in 0 until ch) sum += floats.get()
                    out.add(sum / ch)
                }
            }
            else -> {
                val shorts = buf.asShortBuffer()
                val frames = shorts.remaining() / ch
                for (f in 0 until frames) {
                    var sum = 0f
                    for (c in 0 until ch) sum += shorts.get() / 32768f
                    out.add(sum / ch)
                }
            }
        }
    }

    /** Small growable float buffer to avoid boxing and repeated List reallocation. */
    private class FloatArrayBuilder(initialCapacity: Int) {
        private var data = FloatArray(initialCapacity.coerceAtLeast(1024))
        var size = 0L
            private set

        fun add(v: Float) {
            if (size >= data.size) {
                data = data.copyOf((data.size * 2))
            }
            data[size.toInt()] = v
            size++
        }

        fun toArray(): FloatArray = data.copyOf(size.toInt())
    }
}
