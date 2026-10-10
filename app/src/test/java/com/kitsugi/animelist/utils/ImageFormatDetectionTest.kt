package com.kitsugi.animelist.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Resim/GIF format tespiti ve paylaşım öncesi normalizasyon kuralları.
 *
 * Gerçek dosya indirmeden (saf bayt seviyesinde) test edilir: Magic number okuma
 * mantığı bozulursa galeride "0 KB JPG", "hareketsiz GIF" veya "açılmayan HEIC"
 * şikâyetleri geri gelir.
 */
class ImageFormatDetectionTest {

    private fun bytes(vararg values: Int): ByteArray =
        ByteArray(values.size) { i -> (values[i] and 0xFF).toByte() }

    private fun ascii(text: String): ByteArray =
        ByteArray(text.length) { text[it].code.toByte() }

    private fun concat(vararg parts: ByteArray): ByteArray {
        val out = ByteArray(parts.sumOf { it.size })
        var pos = 0
        parts.forEach { part ->
            System.arraycopy(part, 0, out, pos, part.size)
            pos += part.size
        }
        return out
    }

    @Test
    fun detectsAnimatedGifAndKeepsItAsGif() {
        val data = concat(
            ascii("GIF89a"),
            ByteArray(10),
            // iki GraphicsControlExtension → birden çok kare = animasyonlu
            bytes(0x21, 0xF9, 0x04, 0x00, 0x00, 0x00, 0x00),
            ByteArray(20),
            bytes(0x21, 0xF9, 0x04, 0x00, 0x00, 0x00, 0x00),
            ByteArray(20)
        )
        val format = detectImageFormat(data, "https://media.tenor.com/xyz.gif")
        assertEquals("gif", format.extension)
        assertEquals("image/gif", format.mimeType)
        assertTrue("GIF kareleri bulunmalı", format.isAnimated)
        assertFalse(format.needsTranscode)
    }

    @Test
    fun singleFrameGifIsNotReportedAsAnimated() {
        val data = concat(ascii("GIF87a"), ByteArray(64))
        val format = detectImageFormat(data, "https://example.com/still.gif")
        assertEquals("gif", format.extension)
        assertFalse(format.isAnimated)
    }

    @Test
    fun pngWithActLChunkIsAnimatedPng() {
        val signature = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        // uzunluk = 0, tür = acTL
        val chunkHeader = bytes(0x00, 0x00, 0x00, 0x00) + ascii("acTL")
        val data = concat(signature, chunkHeader, ByteArray(16))
        val format = detectImageFormat(data, "https://example.com/loop.png")
        assertEquals("png", format.extension)
        assertTrue(format.isAnimated)
    }

    @Test
    fun plainPngStaysPng() {
        val data = concat(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A), ByteArray(40))
        val format = detectImageFormat(data, "https://example.com/poster.png")
        assertEquals("png", format.extension)
        assertEquals("image/png", format.mimeType)
        assertFalse(format.isAnimated)
    }

    @Test
    fun jpegSignatureWinsOverUrlExtension() {
        val data = concat(bytes(0xFF, 0xD8, 0xFF, 0xE0), ByteArray(40))
        val format = detectImageFormat(data, "https://example.com/backdrop.webp")
        assertEquals("jpg", format.extension)
    }

    @Test
    fun animatedWebpIsDetectedFromVp8xFlags() {
        val data = concat(
            ascii("RIFF"),
            bytes(0x00, 0x00, 0x00, 0x00),
            ascii("WEBP"),
            ascii("VP8X"),
            bytes(0x0A, 0x00, 0x00, 0x00),
            // flags baytı: animasyon biti = 0x02
            bytes(0x02),
            ByteArray(32)
        )
        val format = detectImageFormat(data, "https://example.com/loop.webp")
        assertEquals("webp", format.extension)
        assertTrue(format.isAnimated)
    }

    @Test
    fun heifAndBmpAndAvifAreMarkedForTranscoding() {
        val heic = concat(
            bytes(0x00, 0x00, 0x00, 0x18),
            ascii("ftyp"),
            ascii("heic"),
            ByteArray(16)
        )
        assertTrue(detectImageFormat(heic, "").needsTranscode)
        assertEquals("heic", detectImageFormat(heic, "").extension)

        val bmp = concat(ascii("BM"), ByteArray(40))
        assertTrue(detectImageFormat(bmp, "").needsTranscode)

        val avif = concat(
            bytes(0x00, 0x00, 0x00, 0x1C),
            ascii("ftyp"),
            ascii("avif"),
            ByteArray(16)
        )
        assertEquals("avif", detectImageFormat(avif, "").extension)
        assertTrue(detectImageFormat(avif, "").needsTranscode)

        val avis = concat(
            bytes(0x00, 0x00, 0x00, 0x1C),
            ascii("ftyp"),
            ascii("avis"),
            ByteArray(16)
        )
        val animatedAvif = detectImageFormat(avis, "")
        assertTrue(animatedAvif.needsTranscode)
        assertTrue(animatedAvif.isAnimated)
    }

    @Test
    fun fallsBackToContentTypeThenUrlExtension() {
        val garbage = ascii("<?php echo 1; ?>")
        // İmza yok → Content-Type belirleyici
        assertEquals("gif", detectImageFormat(garbage, "https://x/y", "image/gif; charset=utf-8").extension)
        // Content-Type yok → URL uzantısı
        assertEquals("webp", detectImageFormat(garbage, "https://x/y.webp", null).extension)
        // Hiçbiri yok → jpeg varsayımı
        assertEquals("jpg", detectImageFormat(garbage, "https://x/y", null).extension)
    }

    @Test
    fun signatureProbeRejectsNonImagePayloads() {
        assertFalse(KitsugiImageDownloadHelper.hasImageSignature(ascii("<!DOCTYPE html><html>")))
        assertFalse(KitsugiImageDownloadHelper.hasImageSignature(ByteArray(0)))
        assertTrue(KitsugiImageDownloadHelper.hasImageSignature(bytes(0xFF, 0xD8, 0xFF, 0xE0) + ByteArray(20)))
        assertTrue(KitsugiImageDownloadHelper.hasImageSignature(concat(ascii("GIF89a"), ByteArray(20))))
    }

    @Test
    fun knownFormatsArePassedThroughUntouched() {
        val gif = concat(ascii("GIF89a"), ByteArray(20))
        val (gifBytes, gifFormat) = normalizeBytesForSharing(gif, detectImageFormat(gif, "a.gif"))
        assertSame("Animasyonlu GIF yeniden kodlanmamalı", gif, gifBytes)
        assertEquals("gif", gifFormat.extension)

        val png = concat(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A), ByteArray(20))
        val (pngBytes, pngFormat) = normalizeBytesForSharing(png, detectImageFormat(png, "a.png"))
        assertSame("Şeffaflık taşıyan PNG bozulmamalı", png, pngBytes)
        assertEquals("png", pngFormat.extension)

        val jpeg = concat(bytes(0xFF, 0xD8, 0xFF, 0xE0), ByteArray(20))
        val (jpegBytes, _) = normalizeBytesForSharing(jpeg, detectImageFormat(jpeg, "a.jpg"))
        assertSame(jpeg, jpegBytes)
    }
}
