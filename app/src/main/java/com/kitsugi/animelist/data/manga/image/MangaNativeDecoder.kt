package com.kitsugi.animelist.data.manga.image

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.DecodeUtils
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.pxOrElse
import java.io.File
import java.io.InputStream
import tachiyomi.decoder.Format
import tachiyomi.decoder.ImageDecoder

/** The same native decoder family used by Mihon for formats absent on older Android versions. */
internal object MangaNativeFormats {
    private val formats = setOf(Format.Avif, Format.Heif, Format.Jxl)

    fun supports(stream: InputStream): Boolean = try {
        val header = ByteArray(32)
        val read = stream.read(header)
        read > 0 && (ImageDecoder.findType(header)?.format?.let { it in formats } == true)
    } catch (_: Exception) {
        false
    } catch (_: LinkageError) {
        false // A missing ABI must not crash the rest of the image pipeline.
    }

    fun hasValidBounds(file: File): Boolean {
        return try {
            if (!file.inputStream().use(::supports)) return false
            val decoder = file.inputStream().use { ImageDecoder.newInstance(it) } ?: return false
            try { decoder.width > 0 && decoder.height > 0 } finally { decoder.recycle() }
        } catch (_: Exception) {
            false
        } catch (_: LinkageError) {
            false
        }
    }
}

/** Native decode is restricted to AVIF/HEIF/JXL; standard and animated formats stay with Coil. */
internal class MangaNativeDecoder(private val source: ImageSource, private val options: Options) : Decoder {
    override suspend fun decode(): DecodeResult {
        val decoder = source.source().use { ImageDecoder.newInstance(it.inputStream()) }
            ?: throw java.io.IOException("Görsel decoder başlatılamadı")
        try {
            check(decoder.width > 0 && decoder.height > 0) { "Geçersiz görsel boyutu" }
            // Bound the preview even if no target size is supplied. Telephoto may display it
            // directly when Android's region decoder cannot tile the native image format.
            val width = options.size.width.pxOrElse { decoder.width }.coerceIn(1, 4096)
            val height = options.size.height.pxOrElse { decoder.height }.coerceIn(1, 4096)
            val sample = DecodeUtils.calculateInSampleSize(
                decoder.width, decoder.height, width, height, options.scale,
            )
            val bitmap = decoder.decode(sampleSize = sample)
                ?: throw java.io.IOException("Görsel çözümlenemedi")
            return DecodeResult(bitmap.asImage(), isSampled = sample > 1)
        } finally {
            decoder.recycle()
        }
    }

    class Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
            val supported = result.source.source().peek().inputStream().use(MangaNativeFormats::supports)
            return if (supported) MangaNativeDecoder(result.source, options) else null
        }
    }
}
