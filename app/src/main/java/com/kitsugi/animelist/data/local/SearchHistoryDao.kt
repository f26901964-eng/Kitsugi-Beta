package com.kitsugi.animelist.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {

    @Query(
        "SELECT * FROM search_history " +
            "ORDER BY CASE WHEN type LIKE '%:PIN:%' THEN 1 ELSE 0 END DESC, timestamp DESC " +
            "LIMIT 50"
    )
    fun getRecentSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Query("SELECT * FROM search_history WHERE LOWER(TRIM(`query`)) = LOWER(TRIM(:query)) LIMIT 1")
    suspend fun findByQueryNormalized(query: String): SearchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchQuery(query: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE `query` = :query")
    suspend fun deleteSearchQuery(query: String)

    @Query("DELETE FROM search_history WHERE LOWER(TRIM(`query`)) = LOWER(TRIM(:query))")
    suspend fun deleteByQueryNormalized(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()

    @Query("DELETE FROM search_history WHERE type NOT LIKE '%:PIN:%'")
    suspend fun clearUnpinnedSearchHistory()

    /**
     * Arama geçmişini en yeni [keep] kayda indirir; sabitlenen aramalar korunur.
     */
    @Query(
        "DELETE FROM search_history WHERE type NOT LIKE '%:PIN:%' AND rowid NOT IN " +
            "(SELECT rowid FROM search_history ORDER BY timestamp DESC LIMIT :keep)"
    )
    suspend fun pruneToLimit(keep: Int): Int
}
