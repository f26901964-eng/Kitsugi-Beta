package okhttp3

import okio.BufferedSource
import okio.Source

/**
 * Stub: OkHttp 5.x sinifi.
 *
 * NEDEN GEREKLI
 * -------------
 * Keiyoushi extensionLib 1.6 (KeiSource) OkHttp **5.4.0** ile derlenir ve
 * `keiyoushi.source.KeiSource.client` icinde su satiri calistirir:
 *
 *   addInterceptor(CompressionInterceptor(Brotli, Gzip, Zstd))
 *
 * Kitsugi ise OkHttp **4.12.0** kullanir (app/build.gradle.kts) ve 4.x'te
 * `okhttp3.CompressionInterceptor` sinifi YOKTUR. ChildFirstPathClassLoader
 * "okhttp3." paketini parent-first yukledigi icin eklenti bu sinifi uygulamanin
 * OkHttp'unda arar ve NoClassDefFoundError alir -> eklenti hic calismaz.
 *
 * COZUM
 * -----
 * Imzasi OkHttp 5.4.0 ile birebir ayni olan (vararg DecompressionAlgorithm) bir stub.
 * intercept() bilincli olarak pass-through'tur: Accept-Encoding elle set edilmez,
 * boylece OkHttp 4'un kendi BridgeInterceptor'i "Accept-Encoding: gzip" ekleyip
 * yaniti seffaf sekilde acar. br/zstd reklam edilmez cunku cozucumuz yok; sunucu
 * bunlari gondermez.
 *
 * NOT: proguard-rules.pro icindeki `-keep class okhttp3.** { *; }` kurali bu stub'i
 * da kapsar; sinif adi degismez.
 */
open class CompressionInterceptor(
    vararg val algorithms: DecompressionAlgorithm,
) : Interceptor {

    /** OkHttp 5.4.0 ile ayni ic ice arayuz: `CompressionInterceptor$DecompressionAlgorithm`. */
    interface DecompressionAlgorithm {
        val encoding: String

        fun decompress(compressedSource: BufferedSource): Source
    }

    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
}
