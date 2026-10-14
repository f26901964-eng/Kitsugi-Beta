package com.kitsugi.animelist.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PersistentDetailCacheDao {
    @Query("SELECT * FROM persistent_details_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun getDetail(key: String): PersistentDetailCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetail(cache: PersistentDetailCacheEntity)

    @Query("DELETE FROM persistent_details_cache WHERE cacheKey = :key")
    suspend fun deleteDetail(key: String)

    @Query("DELETE FROM persistent_details_cache")
    suspend fun clearAll()

    @Query("DELETE FROM persistent_details_cache WHERE cachedAtMs < :threshold")
    suspend fun deleteOlderThan(threshold: Long)

    /**
     * Belirli önekle başlayan kayıtları siler (ör. Fandom Wiki önbelleği: `fandom:v1:`).
     * Önek LIKE joker karakteri (`%`, `_`) içermemelidir.
     */
    @Query("DELETE FROM persistent_details_cache WHERE cacheKey LIKE :prefix || '%'")
    suspend fun deleteByPrefix(prefix: String)

    /**
     * [prefix] ile başlayan kayıtların **mantıksal** boyutu (JSON metinlerinin toplam uzunluğu).
     *
     * Ayarlar > Depolama ekranındaki "Wiki önbelleği" kalemi bu ölçümü kullanır: SQLite,
     * silme sonrası dosya boyutunu kendiliğinden küçültmediği için (VACUUM gerekir) kalemin
     * "öncesi/sonrası" farkı ancak mantıksal veri boyutuyla anlamlı olur. Önek LIKE joker
     * karakteri (`%`, `_`) içermemelidir.
     */
    @Query("SELECT COALESCE(SUM(LENGTH(detailJson)), 0) FROM persistent_details_cache WHERE cacheKey LIKE :prefix || '%'")
    suspend fun sizeByPrefix(prefix: String): Long

    /** [prefix] ile başlayan kayıt sayısı (Depolama ekranındaki "N dosya" bilgisi). */
    @Query("SELECT COUNT(*) FROM persistent_details_cache WHERE cacheKey LIKE :prefix || '%'")
    suspend fun countByPrefix(prefix: String): Int
}

