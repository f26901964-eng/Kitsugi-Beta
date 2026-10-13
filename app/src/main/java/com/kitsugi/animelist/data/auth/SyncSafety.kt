package com.kitsugi.animelist.data.auth

import java.util.concurrent.CancellationException

/** Cancellation is control flow, never an offline-sync error to retry. */
inline fun <T> runSyncCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}

object SyncScores {
    /** MediaEntry stores an integer 0..10; AniList scoreRaw is independent of profile format. */
    fun aniListRaw(score: Int?): Int = (score ?: 0).coerceIn(0, 10) * 10
    fun tenPoint(score: Int?): Int? = score?.takeIf { it > 0 }?.let {
        if (it > 10) kotlin.math.round(it / 10.0).toInt().coerceIn(1, 10) else it.coerceIn(1, 10)
    }
    fun kitsuTwenty(score: Int?): Int? = tenPoint(score)?.times(2)
}
