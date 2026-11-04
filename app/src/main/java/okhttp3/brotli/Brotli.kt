package okhttp3.brotli

import okhttp3.CompressionInterceptor
import okio.BufferedSource
import okio.Source

/**
 * Stub: `okhttp-brotli` (OkHttp 5.x) sinifi.
 *
 * KeiSource `CompressionInterceptor(Brotli, Gzip, Zstd)` cagrisinda bu nesneyi
 * parametre olarak gecirir. Kitsugi'da `com.squareup.okhttp3:okhttp-brotli`
 * bagimliligi olmadigi icin sinif yok -> NoClassDefFoundError.
 *
 * Brotli cozuco (org.brotli.dec) APK'ya eklenmedigi icin decompress() bilincli olarak
 * firlatir; Manga-TR hostlarinda NetworkHelper varsayilan olarak identity ister. Farkli
 * sunucular br gonderirse hata sessiz veri bozulmasi yerine exception olur.
 */
object Brotli : CompressionInterceptor.DecompressionAlgorithm {

    override val encoding: String get() = "br"

    override fun decompress(compressedSource: BufferedSource): Source =
        throw UnsupportedOperationException(
            "Brotli dekompresyonu desteklenmiyor (okhttp-brotli bagimliligi yok)"
        )
}
