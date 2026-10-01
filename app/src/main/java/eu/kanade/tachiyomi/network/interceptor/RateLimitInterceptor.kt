package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Stub: Rate limit interceptor simi -- KeiSource/Madara/Themesia eklentileri bu sinifi yukler.
 * Gercek rate limiting NetworkHelper uzerinden yapilir; bu stub NoClassDefFoundError'u onler.
 */
open class RateLimitInterceptor(
    private val permits: Int = 1,
    private val period: Long = 1,
    private val unit: TimeUnit = TimeUnit.SECONDS,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
}

/**
 * Stub: Belirli bir host icin rate limit interceptor.
 */
open class SpecificHostRateLimitInterceptor(
    private val host: String,
    private val permits: Int = 1,
    private val period: Long = 1,
    private val unit: TimeUnit = TimeUnit.SECONDS,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
}
