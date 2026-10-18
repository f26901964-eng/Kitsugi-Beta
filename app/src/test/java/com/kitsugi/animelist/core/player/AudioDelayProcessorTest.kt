package com.kitsugi.animelist.core.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AudioDelayProcessorTest {

    @Test
    fun targetDelay_clampsToMaxAndNonNegative() {
        val processor = AudioDelayProcessor()
        processor.setDelayMs(-500L)
        assertEquals(0L, processor.getDelayMs())

        processor.setDelayMs(1000L)
        assertEquals(1000L, processor.getDelayMs())

        processor.setDelayMs(10_000L)
        assertEquals(AudioDelayProcessor.MAX_DELAY_MS, processor.getDelayMs())
    }

    @Test
    fun configure_pcmEncodings_areSupported() {
        val processor = AudioDelayProcessor()

        val format16 = AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        val configured16 = processor.configure(format16)
        assertEquals(format16, configured16)

        val formatFloat = AudioFormat(48000, 2, C.ENCODING_PCM_FLOAT)
        val configuredFloat = processor.configure(formatFloat)
        assertEquals(formatFloat, configuredFloat)

        val format8 = AudioFormat(22050, 1, C.ENCODING_PCM_8BIT)
        val configured8 = processor.configure(format8)
        assertEquals(format8, configured8)

        val formatUnsupported = AudioFormat(44100, 2, C.ENCODING_INVALID)
        val configuredUnsupported = processor.configure(formatUnsupported)
        assertEquals(AudioFormat.NOT_SET, configuredUnsupported)
    }

    @Test
    fun gainProcessor_clampsGainDb() {
        val gain = GainAudioProcessor()
        gain.setGainDb(6.0f)
        assertEquals(6.0f, gain.getGainDb(), 0.001f)

        val format16 = AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        val configured = gain.configure(format16)
        assertEquals(format16, configured)
    }
}
