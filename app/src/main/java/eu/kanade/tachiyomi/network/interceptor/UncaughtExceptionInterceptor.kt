package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Zincirin devaminda firlatilan beklenmeyen exception'lari yakalayip IOException olarak
 * yeniden firlatir; boylece eklenti kaynakli bir hata uygulamayi oldurmez.
 *
 * ZORUNLU: extensionLib 1.6 (KeiSource) tabanli eklentiler, host uygulamanin varsayilan
 * OkHttpClient'inda bu interceptor'in BULUNMASINI sart kosar. KeiSource.client ilk
 * erisimde soyle bir kontrol calistirir:
 *
 *   check(interceptors().any { it.javaClass.simpleName == "UncaughtExceptionInterceptor" }) {
 *       "UncaughtExceptionInterceptor must be present in default client"
 *   }
 *
 * Sinif adi degistirilirse (R8 dahil) bu kontrol IllegalStateException firlatir ve
 * eklenti hicbir istek yapamaz. Bu yuzden proguard-rules.pro icinde -keep altindadir.
 *
 * Zincirin ILK interceptor'i olmali.
 */
class UncaughtExceptionInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        return try {
            chain.proceed(chain.request())
        } catch (e: Exception) {
            if (e is IOException) {
                throw e
            } else {
                throw IOException(e)
            }
        }
    }
}
