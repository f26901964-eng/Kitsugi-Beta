package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Istekte User-Agent yoksa varsayilan User-Agent'i ekler.
 *
 * ZORUNLU: extensionLib 1.6 (KeiSource) tabanli eklentiler host client'inda bu sinif
 * adinin bulunmasini sart kosar (KeiSource.client icindeki `check(...)` blogu):
 *
 *   check(interceptors().any { it.javaClass.simpleName == "UserAgentInterceptor" }) {
 *       "UserAgentInterceptor must be present in default client"
 *   }
 *
 * Yoksa eklenti ilk istekte IllegalStateException ile patlar.
 */
class UserAgentInterceptor(
    private val defaultUserAgentProvider: () -> String,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        return if (originalRequest.header("User-Agent").isNullOrEmpty()) {
            val newRequest = originalRequest
                .newBuilder()
                .removeHeader("User-Agent")
                .addHeader("User-Agent", defaultUserAgentProvider())
                .build()
            chain.proceed(newRequest)
        } else {
            chain.proceed(originalRequest)
        }
    }
}
