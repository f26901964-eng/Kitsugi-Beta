package com.kitsugi.animelist.core.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

class GainAudioProcessor : BaseAudioProcessor() {

    private var gainDb = 0f
    private var linearGain = 1f

    fun setGainDb(db: Float) {
        if (gainDb != db) {
            gainDb = db
            linearGain = Math.pow(10.0, db.toDouble() / 20.0).toFloat()
        }
    }

    fun getGainDb(): Float = gainDb

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        return when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT,
            C.ENCODING_PCM_FLOAT,
            C.ENCODING_PCM_24BIT,
            C.ENCODING_PCM_32BIT,
            C.ENCODING_PCM_8BIT -> inputAudioFormat
            else -> AudioFormat.NOT_SET
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outputBuffer = replaceOutputBuffer(remaining)

        if (linearGain == 1f) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        outputBuffer.order(inputBuffer.order())

        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> {
                val shortsCount = remaining / 2
                for (i in 0 until shortsCount) {
                    val sample = inputBuffer.short
                    val boosted = (sample * linearGain).toInt()
                    val clamped = boosted.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    outputBuffer.putShort(clamped)
                }
                while (inputBuffer.hasRemaining()) outputBuffer.put(inputBuffer.get())
            }
            C.ENCODING_PCM_FLOAT -> {
                val floatsCount = remaining / 4
                for (i in 0 until floatsCount) {
                    val sample = inputBuffer.float
                    val boosted = sample * linearGain
                    val clamped = boosted.coerceIn(-1.0f, 1.0f)
                    outputBuffer.putFloat(clamped)
                }
                while (inputBuffer.hasRemaining()) outputBuffer.put(inputBuffer.get())
            }
            C.ENCODING_PCM_8BIT -> {
                // 8-bit PCM işaretsizdir (0..255, merkez 128).
                val bytesCount = remaining
                for (i in 0 until bytesCount) {
                    val unsigned = inputBuffer.get().toInt() and 0xFF
                    val centered = (unsigned - 128) * linearGain
                    val clamped = centered.toInt().coerceIn(-128, 127)
                    outputBuffer.put((clamped + 128).toByte())
                }
            }
            C.ENCODING_PCM_24BIT -> {
                // 24-bit little-endian, işaretli.
                val frames = remaining / 3
                for (i in 0 until frames) {
                    val b0 = inputBuffer.get().toInt() and 0xFF
                    val b1 = inputBuffer.get().toInt() and 0xFF
                    val b2 = inputBuffer.get().toInt() and 0xFF
                    var sample = b0 or (b1 shl 8) or (b2 shl 16)
                    if (sample and 0x800000 != 0) sample = sample or -0x1000000
                    val amplified = (sample * linearGain)
                        .toInt()
                        .coerceIn(-8_388_608, 8_388_607)
                    outputBuffer.put((amplified and 0xFF).toByte())
                    outputBuffer.put(((amplified shr 8) and 0xFF).toByte())
                    outputBuffer.put(((amplified shr 16) and 0xFF).toByte())
                }
                // Kare sınırına uymayan artık baytları aynen taşı.
                while (inputBuffer.hasRemaining()) outputBuffer.put(inputBuffer.get())
            }
            C.ENCODING_PCM_32BIT -> {
                val frames = remaining / 4
                for (i in 0 until frames) {
                    val sample = inputBuffer.int
                    val amplified = (sample.toDouble() * linearGain)
                        .coerceIn(Int.MIN_VALUE.toDouble(), Int.MAX_VALUE.toDouble())
                        .toInt()
                    outputBuffer.putInt(amplified)
                }
                while (inputBuffer.hasRemaining()) outputBuffer.put(inputBuffer.get())
            }
            else -> {
                outputBuffer.put(inputBuffer)
            }
        }
        outputBuffer.flip()
    }
}
