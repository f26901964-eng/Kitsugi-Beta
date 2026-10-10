package com.kitsugi.animelist.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {

    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 20")
    fun getRecentSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchQuery(query: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE `query` = :query")
    suspend fun deleteSearchQuery(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()

    /**
     * Arama geçmişini en yeni [keep] kayda indirir.
     *
     * UI yalnızca son 20 kaydı gösterse de tablo her aramada bir satır büyüyordu ve hiçbir
     * budama yoktu (bulut senkronizasyonu ile birleşen kayıtlar dâhil). Zaman damgası kolonu
     * bulunduğu için en eskiler atılır; kullanıcı deneyimi değişmez.
     */
    @Query(
        "DELETE FROM search_history WHERE rowid NOT IN " +
            "(SELECT rowid FROM search_history ORDER BY timestamp DESC LIMIT :keep)"
    )
    suspend fun pruneToLimit(keep: Int): Int
}
