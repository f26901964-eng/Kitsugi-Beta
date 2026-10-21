package com.kitsugi.animelist.core.network

import com.kitsugi.animelist.data.platform.PlatformFailureRegistry
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Liste kaynaklarının (Simkl, Bangumi, TMDB, Shikimori, Jikan, AniList, Kitsu) HTTP hatalarını
 * [PlatformFailureRegistry]'ye bildirir.
 *
 * Neden ağ katmanında? Kaynak istemcilerinin çoğu başarısız yanıtta `null` döndürüp hatayı
 * yutuyor; ağ katmanı ise her kaynağın her uç noktasındaki gerçek HTTP kodunu tek yerden görür.
 *
 * Notlar:
 *  - Yalnızca 400+ yanıtlar kaydedilir; 304 (önbellek) gibi yanıtlar hata sayılmaz.
 *  - Yalnızca alan adı ve yol kaydedilir; sorgu dizesi (API anahtarları) asla kaydedilmez.
 *  - Gövde okunmaz, yanıt değiştirilmez.
 */
class PlatformFailureInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.code >= 400) {
            runCatching {
                PlatformFailureRegistry.reportHttp(
                    host = request.url.host,
                    httpCode = response.code,
                    path = request.url.encodedPath,
                )
            }
        }
        return response
    }
}
