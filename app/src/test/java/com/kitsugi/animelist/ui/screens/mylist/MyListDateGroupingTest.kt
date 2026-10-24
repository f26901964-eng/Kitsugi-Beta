package com.kitsugi.animelist.ui.screens.mylist

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * "Son eklenen / İlk eklenen" sıralaması ve tarih bölümleri.
 *
 * Regresyon kaynağı: sıralama yerel satır `id`'sine dayanıyordu; içe aktarma sırası
 * değiştiğinde aynı yapım bir sekmede "Bugün", diğerinde "Dün" görünüyordu.
 */
class MyListDateGroupingTest {

    private val zone: ZoneId = ZoneId.of("Europe/Istanbul")

    /** 2026-10-24 Cumartesi (kullanıcının bildirdiği tarih). */
    private val today: LocalDate = LocalDate.of(2026, 10, 24)

    private fun secondsAt(date: LocalDate, hour: Int = 12): Long =
        ZonedDateTime.of(date.atTime(hour, 0), zone).toEpochSecond()

    private fun entry(
        id: Int,
        title: String = "Başlık $id",
        addedAt: Long = 0L,
        updatedAt: Long = 0L
    ) = MediaEntry(
        id = id,
        title = title,
        type = MediaType.Anime,
        status = WatchStatus.Planned,
        score = null,
        progress = 0,
        total = null,
        updatedAt = updatedAt,
        addedAt = addedAt
    )

    private fun bucketOf(date: LocalDate): MyListDateBucket =
        myListDateBucketOf(secondsAt(date), zone, today)

    @Test
    fun buckets_areComputedFromCalendarDays() {
        assertEquals(MyListDateBucket.TODAY, bucketOf(today))
        assertEquals(MyListDateBucket.TODAY, bucketOf(today.plusDays(1)))      // saat kayması
        assertEquals(MyListDateBucket.YESTERDAY, bucketOf(today.minusDays(1)))
        assertEquals(MyListDateBucket.THIS_WEEK, bucketOf(LocalDate.of(2026, 10, 21))) // Çarşamba
        assertEquals(MyListDateBucket.THIS_WEEK, bucketOf(LocalDate.of(2026, 10, 19))) // Pazartesi
        assertEquals(MyListDateBucket.LAST_WEEK, bucketOf(LocalDate.of(2026, 10, 18))) // Pazar
        assertEquals(MyListDateBucket.LAST_WEEK, bucketOf(LocalDate.of(2026, 10, 13)))
        assertEquals(MyListDateBucket.LAST_WEEK, bucketOf(LocalDate.of(2026, 10, 12))) // pazartesi
        assertEquals(MyListDateBucket.OLDER, bucketOf(LocalDate.of(2026, 10, 11)))
        assertEquals(MyListDateBucket.UNKNOWN, myListDateBucketOf(0L, zone, today))
    }

    @Test
    fun sections_areOrderedNewestFirst_andUnknownLast() {
        val entries = listOf(
            entry(1, "Eski", addedAt = secondsAt(LocalDate.of(2020, 3, 1))),
            entry(2, "Bugün", addedAt = secondsAt(today)),
            entry(3, "Bilinmiyor", addedAt = 0L),
            entry(4, "Dün", addedAt = secondsAt(today.minusDays(1))),
            entry(5, "Bu hafta", addedAt = secondsAt(LocalDate.of(2026, 10, 20)))
        )

        val grouped = groupMyListEntriesByAddedDate(entries, zone = zone, today = today)

        assertEquals(
            listOf(
                MyListDateBucket.TODAY,
                MyListDateBucket.YESTERDAY,
                MyListDateBucket.THIS_WEEK,
                MyListDateBucket.OLDER,
                MyListDateBucket.UNKNOWN
            ),
            grouped.map { it.first }
        )
        assertEquals(listOf(2), grouped.first { it.first == MyListDateBucket.TODAY }.second.map { it.id })
    }

    @Test
    fun newestSort_usesAddedAtInsteadOfRowId() {
        // En yüksek id eski kayıt: id'ye göre sıralama yanlış sonuç verirdi.
        val entries = listOf(
            entry(99, "Dün eklenen", addedAt = secondsAt(today.minusDays(1))),
            entry(1, "Bugün eklenen", addedAt = secondsAt(today)),
            entry(7, "Bilinmiyor", addedAt = 0L, updatedAt = 0L)
        )

        val newest = applySort(entries, "newest")
        val oldest = applySort(entries, "oldest")

        assertEquals(listOf(1, 99, 7), newest.map { it.id })
        assertEquals(listOf(99, 1, 7), oldest.map { it.id })
    }

    @Test
    fun addedAt_fallsBackToUpdatedAtForLegacyRows() {
        val legacy = entry(5, "Eski yedek", addedAt = 0L, updatedAt = secondsAt(today.minusDays(1)))
        val fresh = entry(6, "Bugün", addedAt = secondsAt(today))

        val newest = applySort(listOf(legacy, fresh), "newest")

        assertEquals(listOf(6, 5), newest.map { it.id })
        assertEquals(MyListDateBucket.YESTERDAY, myListDateBucketOf(addedSortTimestamp(legacy), zone, today))
    }
}
