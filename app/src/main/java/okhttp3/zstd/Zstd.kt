package okhttp3.zstd

import okhttp3.CompressionInterceptor
import okio.BufferedSource
import okio.Source

/**
 * Stub: `okhttp-zstd` (OkHttp 5.x) sinifi.
 *
 * KeiSource `CompressionInterceptor(Brotli, Gzip, Zstd)` cagrisinda bu nesneyi
 * parametre olarak gecirir. Kitsugi'da `com.squareup.okhttp3:okhttp-zstd`
 * bagimliligi olmadigi icin sinif yok -> NoClassDefFoundError.
 *
 * Zstd cozuco (zstd-jni) APK'ya eklenmedigi icin decompress() firlatir;
 * CompressionInterceptor stub'i "zstd" reklam etmedigi icin bu yol normalde calismaz.
 */
object Zstd : CompressionInterceptor.DecompressionAlgorithm {

    override val encoding: String get() = "zstd"

    override fun decompress(compressedSource: BufferedSource): Source =
        throw UnsupportedOperationException(
            "Zstd dekompresyonu desteklenmiyor (okhttp-zstd bagimliligi yok)"
        )
}
