// Fake network boundary: repository/queue code is real; no provider API is contacted.
package com.kitsugi.animelist.data.auth
import android.content.Context
import com.kitsugi.animelist.model.MediaEntry
object ExternalListSyncManager {
    data class SyncResult(val messages: List<String>, val errors: List<String> = emptyList(), val aniListEntryId: Int? = null)
    var handler: suspend (MediaEntry) -> SyncResult = { SyncResult(emptyList()) }
    suspend fun syncEntry(context: Context, entry: MediaEntry, advancedScores: List<Double>? = null) = handler(entry)
    suspend fun deleteEntry(context: Context, entry: MediaEntry) = handler(entry)
}
