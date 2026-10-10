package okhttp3.brotli

import okhttp3.CompressionInterceptor
import okhttp3.Gzip

/**
 * Stub: `okhttp-brotli` (OkHttp 5.x) icindeki `BrotliInterceptor` nesnesi.
 *
 * KeiSource client'i kurarken host client'in network interceptor listesinde bu
 * sinifin OLMAMASINI dogrular:
 *
 *   check(networkInterceptors().none { it is BrotliInterceptor })
 *
 * `is` kontrolunun derlenebilmesi icin sinifin var olmasi sart; yoksa
 * NoClassDefFoundError. Upstream ile ayni sekilde CompressionInterceptor turevidir.
 */
object BrotliInterceptor : CompressionInterceptor(Brotli, Gzip)
