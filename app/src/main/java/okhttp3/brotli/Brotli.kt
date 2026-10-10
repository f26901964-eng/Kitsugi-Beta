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
 * firlatir; CompressionInterceptor stub'i "br" reklam etmedigi icin bu yol normalde
 * hic calismaz. Bir sunucu yine de br gonderirse hata, sessiz bozuk veri yerine
 * loglanabilir bir exception olarak yuzeye cikar.
 */
object Brotli : CompressionInterceptor.DecompressionAlgorithm {

    override val encoding: String get() = "br"

    override fun decompress(compressedSource: BufferedSource): Source =
        throw UnsupportedOperationException(
            "Brotli dekompresyonu desteklenmiyor (okhttp-brotli bagimliligi yok)"
        )
}
