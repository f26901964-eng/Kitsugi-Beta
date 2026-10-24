package com.kitsugi.animelist.ui.screens.mylist

import androidx.annotation.StringRes
import com.kitsugi.animelist.R
import com.kitsugi.animelist.model.MediaEntry
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Listem'in "eklenme tarihi" bölümleri.
 *
 * Sıralama çipleri "Son eklenen / İlk eklenen" seçiliyken liste, durum grupları yerine
 * kullanıcının yapımı listesine **ne zaman eklediğine** göre bölümlenir:
 *
 * ```
 * Bugün
 * Dün
 * Bu Hafta
 * Geçen Hafta
 * Daha Eski
 * Eklendiği Tarih Bilinmiyor
 * ```
 *
 * Kök neden notu: bölümler daha önce yerel satır `id`'sine (autoincrement) göre
 * hesaplanıyordu. Her içe aktarma satırları sildiği/yeniden eklediği için aynı yapım bir
 * sekmede "Bugün", diğerinde "Dün" görünüyordu. Artık tek kaynak
 * [MediaEntry.addedAt] (sağlayıcının `created_at` / `added_to_watchlist_at` değeri).
 */
internal enum class MyListDateBucket(@StringRes val labelRes: Int) {
    TODAY(R.string.mylist_date_today),
    YESTERDAY(R.string.mylist_date_yesterday),
    THIS_WEEK(R.string.mylist_date_this_week),
    LAST_WEEK(R.string.mylist_date_last_week),
    OLDER(R.string.mylist_date_older),
    UNKNOWN(R.string.mylist_date_unknown)
}

/** Hafta başlangıcı Pazartesi'dir (ISO-8601, AniHyou/AniList alışkanlığı). */
private val DESCENDING_BUCKETS = listOf(
    MyListDateBucket.TODAY,
    MyListDateBucket.YESTERDAY,
    MyListDateBucket.THIS_WEEK,
    MyListDateBucket.LAST_WEEK,
    MyListDateBucket.OLDER,
    MyListDateBucket.UNKNOWN
)

private val ASCENDING_BUCKETS = listOf(
    MyListDateBucket.OLDER,
    MyListDateBucket.LAST_WEEK,
    MyListDateBucket.THIS_WEEK,
    MyListDateBucket.YESTERDAY,
    MyListDateBucket.TODAY,
    MyListDateBucket.UNKNOWN
)

/** "Son eklenen / İlk eklenen" ailesindeki sıralama kimlikleri. */
internal fun usesAddedDateGrouping(sortId: String): Boolean = when (sortId) {
    "newest", "oldest", "added", "added_desc", "added_asc" -> true
    else -> false
}

/**
 * Bir zaman damgasının (epoch saniye) hangi bölüme düştüğünü söyler.
 *
 * `timestampSec <= 0` bilinmeyen demektir. Gelecekteki damgalar (cihaz saati kayması)
 * "Bugün" sayılır: kullanıcıyı "Daha Eski" bölümüne atan bir saat kayması göstermeyiz.
 */
internal fun myListDateBucketOf(
    timestampSec: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    today: LocalDate = LocalDate.now(zone)
): MyListDateBucket {
    if (timestampSec <= 0L) return MyListDateBucket.UNKNOWN
    val date = runCatching {
        Instant.ofEpochSecond(timestampSec).atZone(zone).toLocalDate()
    }.getOrNull() ?: return MyListDateBucket.UNKNOWN

    if (!date.isBefore(today)) return MyListDateBucket.TODAY
    if (date == today.minusDays(1)) return MyListDateBucket.YESTERDAY

    val thisWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    if (!date.isBefore(thisWeekStart)) return MyListDateBucket.THIS_WEEK

    val lastWeekStart = thisWeekStart.minusWeeks(1)
    if (!date.isBefore(lastWeekStart)) return MyListDateBucket.LAST_WEEK

    return MyListDateBucket.OLDER
}

/**
 * Satırları eklenme tarihi bölümlerine ayırır. Girdi sırası korunur; bölümler
 * [ascending] parametresine göre kronolojik/yeni-önce sıralanır. Boş bölüm üretilmez.
 */
internal fun groupMyListEntriesByAddedDate(
    entries: List<MediaEntry>,
    ascending: Boolean = false,
    zone: ZoneId = ZoneId.systemDefault(),
    today: LocalDate = LocalDate.now(zone)
): List<Pair<MyListDateBucket, List<MediaEntry>>> {
    if (entries.isEmpty()) return emptyList()

    val buckets = LinkedHashMap<MyListDateBucket, MutableList<MediaEntry>>()
    entries.forEach { entry ->
        val bucket = myListDateBucketOf(addedSortTimestamp(entry), zone, today)
        buckets.getOrPut(bucket) { mutableListOf() }.add(entry)
    }

    val order = if (ascending) ASCENDING_BUCKETS else DESCENDING_BUCKETS
    return order.mapNotNull { bucket ->
        buckets[bucket]?.takeIf { it.isNotEmpty() }?.let { bucket to it.toList() }
    }
}
