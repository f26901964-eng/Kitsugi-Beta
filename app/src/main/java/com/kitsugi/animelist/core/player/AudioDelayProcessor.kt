package com.kitsugi.animelist.core.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

/**
 * Media3 için gerçek ses gecikmesi (lip-sync) işlemcisi.
 *
 * Media3 / ExoPlayer'ın `Player` arayüzünde `setAudioDelay` gibi bir API yoktur; bu yüzden
 * oynatıcı panelindeki "Ses gecikmesi" değeri uzun süre no-op idi. Bu işlemci, çözülmüş PCM
 * verisinin başına sessizlik ekleyerek (veya gecikme azaltılırken örnek atlayarak) gecikmeyi
 * gerçekten uygular:
 *
 *  - delay > 0 → ses, görüntünün N ms gerisinde kalır (başa N ms sessizlik eklenir)
 *  - delay azaltılırken → fazlalık örnekler atlanır
 *
 * Yalnızca PCM (8/16/24/32-bit ve float) desteklenir; desteklenmeyen formatlarda işlemci
 * passthrough yapar, böylece ses asla bozulmaz.
 *
 * İşlemci, ses hattında [GainAudioProcessor]'dan sonra çalışır. Değer değiştiğinde bir sonraki
 * PCM arabelleğinden itibaren geçerli olur; uygulama yeniden tamponlanmaz.
 */
class AudioDelayProcessor : BaseAudioProcessor() {

    companion object {
        private const val TAG = "AudioDelayProcessor"

        /** Aniyomi ile aynı üst sınır: ±5 sn. */
        const val MAX_DELAY_MS = 5_000L
    }

    @Volatile
    private var targetDelayMs: Long = 0L

    /** Şu ana kadar uygulanmış gecikme (bayt, işaretli). */
    private var appliedDelayBytes: Long = 0L

    private var bytesPerFrame: Int = 0
    private var zeroFill = ByteArray(0)

    /** Gecikmeyi milisaniye cinsinden ayarlar. Negatif değerler 0'a sabitlenir. */
    fun setDelayMs(delayMs: Long) {
        targetDelayMs = delayMs.coerceIn(0L, MAX_DELAY_MS)
    }

    fun getDelayMs(): Long = targetDelayMs

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        val bytesPerSample = when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_8BIT -> 1
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            C.ENCODING_PCM_32BIT -> 4
            C.ENCODING_PCM_FLOAT -> 4
            else -> return AudioFormat.NOT_SET
        }
        bytesPerFrame = bytesPerSample * inputAudioFormat.channelCount.coerceAtLeast(1)
        appliedDelayBytes = 0L
        return inputAudioFormat
    }

    override fun onFlush() {
        // Seek/flush sonrası ses hattı yeniden başlar; gecikme yeni konumda yeniden uygulanmalı.
        appliedDelayBytes = 0L
    }

    override fun onReset() {
        appliedDelayBytes = 0L
        bytesPerFrame = 0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        var remaining = inputBuffer.remaining()
        if (remaining <= 0) return

        if (bytesPerFrame <= 0) {
            val passthrough = replaceOutputBuffer(remaining)
            passthrough.put(inputBuffer)
            passthrough.flip()
            return
        }

        val sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(1)
        val desiredDelayBytes =
            targetDelayMs * sampleRate.toLong() * bytesPerFrame.toLong() / 1_000L
        val frameAlignedDesired = (desiredDelayBytes / bytesPerFrame) * bytesPerFrame
        var delta = frameAlignedDesired - appliedDelayBytes

        // Gecikme azaltılırken fazla örnekleri at.
        if (delta < 0L) {
            val skip = -delta
            // Atlama, örnek/kare sınırında olmalı; aksi halde PCM kanalları kayar.
            val maxSkippable = (minOf(skip, remaining.toLong()) / bytesPerFrame) * bytesPerFrame
            val skipped = maxSkippable.toInt()
            if (skipped > 0) {
                inputBuffer.position(inputBuffer.position() + skipped)
                appliedDelayBytes -= skipped.toLong()
            }
            remaining = inputBuffer.remaining()
            delta = frameAlignedDesired - appliedDelayBytes
            if (!inputBuffer.hasRemaining()) {
                // Bu arabelleğin tamamı atıldı; ses hattına boş çıktı ver.
                val empty = replaceOutputBuffer(0)
                empty.flip()
                return
            }
        }

        val silenceBytes = if (delta > 0L) delta else 0L
        val outputSize = remaining + silenceBytes
        if (outputSize > Int.MAX_VALUE) {
            // Teorik olarak imkânsız; güvenlik için gecikmeyi uygulamadan geçir.
            val passthrough = replaceOutputBuffer(remaining)
            passthrough.put(inputBuffer)
            passthrough.flip()
            return
        }

        val outputBuffer = replaceOutputBuffer(outputSize.toInt())
        if (silenceBytes > 0L) {
            val silence = obtainZeroFill(silenceBytes.toInt())
            outputBuffer.put(silence, 0, silenceBytes.toInt())
            appliedDelayBytes += silenceBytes
        }
        outputBuffer.put(inputBuffer)
        outputBuffer.flip()
    }

    private fun obtainZeroFill(size: Int): ByteArray {
        if (zeroFill.size < size) {
            zeroFill = ByteArray(size)
        }
        return zeroFill
    }
}
