package com.kitsugi.animelist.data.auth

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Platform bazlı merkezi rate limiter.
 * Harici servislerin API limitlerine (AniList 90/dk, Kitsu 60/dk, Simkl 1/sn, MAL 150/dk)
 * tam uyum sağlayarak HTTP 429 hatalarını önler.
 */
object PlatformRateLimiter {
    private val mutexMap = ConcurrentHashMap<String, Mutex>()
    private val lastCallTime = ConcurrentHashMap<String, Long>()

    private val platformIntervals = mapOf(
        "anilist" to 750L,     // ~80 req/dk (Limit ~90/dk)
        "mal" to 450L,         // ~133 req/dk (Limit ~150/dk)
        "kitsu" to 1100L,      // ~54 req/dk (Limit ~60/dk)
        "shikimori" to 350L,   // ~170 req/dk (Limit 5 req/sn)
        "simkl" to 1250L,      // 0.8 req/sn (Limit 1 req/sn)
        "jikan" to 400L        // 2.5 req/sn (Limit 3 req/sn)
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
