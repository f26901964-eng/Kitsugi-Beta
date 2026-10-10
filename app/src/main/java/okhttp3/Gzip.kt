package okhttp3

import okhttp3.CompressionInterceptor.DecompressionAlgorithm
import okio.BufferedSource
import okio.GzipSource
import okio.Source

/**
 * Stub: OkHttp 5.x `okhttp3.Gzip` nesnesi.
 *
 * KeiSource `CompressionInterceptor(Brotli, Gzip, Zstd)` cagrisinda bu nesneyi
 * parametre olarak gecirir; sinif yoksa NoClassDefFoundError olusur.
 * Cozme islemi gercek (okio.GzipSource) — ancak Kitsugi stub CompressionInterceptor'i
 * pass-through oldugu icin pratikte OkHttp 4'un kendi seffaf gzip cozumu kullanilir.
 */
object Gzip : DecompressionAlgorithm {

    override val encoding: String get() = "gzip"

    override fun decompress(compressedSource: BufferedSource): Source = GzipSource(compressedSource)
}
