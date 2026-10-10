package com.kitsugi.animelist.data.auth

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Platform bazlı merkezi rate limiter.
 * Harici servislerin API limitlerine (AniList fiilen 30/dk, Kitsu 60/dk, Simkl 1/sn, MAL 150/dk)
 * tam uyum sağlayarak HTTP 429 hatalarını önler.
 *
 * Not: AniList dokümante edilen 90/dk limitini uzun süredir 30/dk'ya düşürmüş durumda
 * (yanıt başlığı `X-RateLimit-Limit: 30`). 750 ms aralık toplu eşitlemede 429 üretiyordu.
 */
object PlatformRateLimiter {
    private val mutexMap = ConcurrentHashMap<String, Mutex>()
    private val lastCallTime = ConcurrentHashMap<String, Long>()

    private val platformIntervals = mapOf(
        "anilist" to 2100L,    // ~28 req/dk (fiili limit 30/dk; 90/dk dokümante ama uygulanmıyor)
        "mal" to 450L,         // ~133 req/dk (Limit ~150/dk)
        "kitsu" to 1100L,      // ~54 req/dk (Limit ~60/dk)
        "shikimori" to 350L,   // ~170 req/dk (Limit 5 req/sn)
        "bangumi" to 400L,     // ~150 req/dk (Limit ~300/dk)
        "simkl" to 1250L       // 0.8 req/sn (Limit 1 req/sn)
        // Jikan burada YOK: kotası JikanGateway'de (3/sn, 55/dk, 429 soğuması, önbellek) yönetilir.
    )

    private fun getMutex(platform: String): Mutex {
        return mutexMap.computeIfAbsent(platform.lowercase()) { Mutex() }
    }

    suspend fun acquire(platform: String) {
        val key = platform.lowercase()
        val mutex = getMutex(key)
        mutex.withLock {
            val minInterval = platformIntervals[key] ?: 300L
            val now = System.currentTimeMillis()
            val last = lastCallTime[key] ?: 0L
            val elapsed = now - last
            if (elapsed < minInterval) {
                delay(minInterval - elapsed)
            }
            lastCallTime[key] = System.currentTimeMillis()
        }
    }

    suspend fun notifyRateLimited(platform: String, retryAfterSeconds: Long? = null) {
        val key = platform.lowercase()
        val waitMs = if (retryAfterSeconds != null && retryAfterSeconds > 0) {
            retryAfterSeconds * 1000L
        } else {
            3000L
        }
        val mutex = getMutex(key)
        mutex.withLock {
            lastCallTime[key] = System.currentTimeMillis() + waitMs
            delay(waitMs)
        }
    }
}
