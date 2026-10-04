package com.kitsugi.animelist.core.network

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object KitsugiHttpClient {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(IPv4FirstDns())
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .connectionPool(
                ConnectionPool(
                    maxIdleConnections = 10,
                    keepAliveDuration  = 5,
                    timeUnit           = TimeUnit.MINUTES
                )
            )
            .dispatcher(
                Dispatcher().apply {
                    maxRequests        = 64
                    maxRequestsPerHost = 8
                }
            )
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(RetryInterceptor(maxRetries = 2))
            .addInterceptor(
                Interceptor { chain ->
                    val original = chain.request()
                    val reqBuilder = original.newBuilder()
                    if (original.header("User-Agent") == null) {
                        reqBuilder.header("User-Agent", NuvioOkHttpProvider.USER_AGENT)
                    }
                    if (original.header("Accept-Language") == null) {
                        reqBuilder.header("Accept-Language", "tr-TR,tr;q=0.9,en;q=0.8")
                    }
                    chain.proceed(reqBuilder.build())
                }
            )
            .build()
    }
}
