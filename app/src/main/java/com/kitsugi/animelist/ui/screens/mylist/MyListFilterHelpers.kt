package com.kitsugi.animelist.ui.screens.mylist

import com.kitsugi.animelist.ui.utils.plainLabel
import com.kitsugi.animelist.ui.utils.localizedLabel
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.kitsugi.animelist.R
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.WatchStatus

/**
 * Listem ekranındaki bütün istemci tarafı filtrelerini tek bir sözleşmede uygular.
 *
 * Kendi listemiz ve salt-okunur kullanıcı listeleri aynı yardımcıyı kullanır. Böylece
 * arama/filtre davranışı ekranlar arasında zamanla birbirinden kopmaz.
 */
internal fun filterMyListEntries(
    entries: List<MediaEntry>,
    searchQuery: String,
    selectedStatusFilterId: String,
    selectedTypeFilterId: String,
    selectedFavoriteFilterId: String,
    selectedScoreFilterId: String,
    selectedYearFilterId: String,
    selectedExtraFilterId: String
): List<MediaEntry> {
    val selectedStatus = statusFilters.firstOrNull { it.id == selectedStatusFilterId }?.status
    val selectedType = typeFilters.firstOrNull { it.id == selectedTypeFilterId }?.type
    val favoritesOnly = selectedFavoriteFilterId == "favorites" || selectedStatusFilterId == "favorites"
    val adultOnly = selectedStatusFilterId == "adult"
    val normalizedQuery = searchQuery.trim().lowercase()
    val normQuery = com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(normalizedQuery)

    return entries.asSequence()
        .filter { entry -> selectedStatus == null || entry.status == selectedStatus }
        .filter { entry -> selectedType == null || entry.type == selectedType }
        .filter { entry -> !favoritesOnly || entry.isFavorite }
        .filter { entry -> !adultOnly || entry.isAdult }
        .filter { entry ->
            when (selectedScoreFilterId) {
                "high" -> (entry.score ?: 0) >= 8
                "mid" -> (entry.score ?: 0) in 5..7
                "low" -> (entry.score ?: 0) in 1..4
                "unrated" -> entry.score == null || entry.score == 0
                else -> true
            }
        }
        .filter { entry ->
            when (selectedYearFilterId) {
                "new" -> (entry.year ?: 0) >= 2025
                "2020s" -> (entry.year ?: 0) in 2020..2024
                "2010s" -> (entry.year ?: 0) in 2010..2019
                "2000s" -> (entry.year ?: 0) in 2000..2009
                "classic" -> (entry.year ?: 0) in 1..1999
                else -> true
            }
        }
        .filter { entry ->
            when (selectedExtraFilterId) {
                "repeating" -> entry.isRepeating || entry.status == WatchStatus.Repeating
                "private" -> entry.isPrivate
                "ongoing" -> entry.status != WatchStatus.Completed && entry.status != WatchStatus.Dropped
                else -> true
            }
        }
        .filter { entry ->
            if (normalizedQuery.isBlank()) {
                true
            } else {
                entry.title.lowercase().contains(normalizedQuery) ||
                    entry.titleEnglish?.lowercase()?.contains(normalizedQuery) == true ||
                    entry.titleJapanese?.lowercase()?.contains(normalizedQuery) == true ||
                    entry.subtitle.lowercase().contains(normalizedQuery) ||
                    entry.type.name.lowercase().contains(normalizedQuery) ||
                    entry.status.plainLabel().lowercase().contains(normalizedQuery) ||
                    entry.source.lowercase().contains(normalizedQuery) ||
                    entry.year?.toString()?.contains(normalizedQuery) == true ||
                    entry.malId?.toString()?.contains(normalizedQuery) == true ||
                    // PERF: normalize edilmiş (NFKC + regex) başlık eşleşmesi eskiden her
                    // satır için ÖNDEN hesaplanıyordu; her tuş vuruşunda listenin tamamı
                    // için 3 normalizasyon ana iş parçacığında çalışıyordu. `||` zinkesini
                    // sona aldım: yalnızca ucuz `contains` denetimlerinin hiçbiri
                    // eşleşmezse hesaplanır. Sıra değişimi sonucu değiştirmez (tümü yan
                    // etkisiz boole ifadeleri).
                    (normQuery.length >= 2 && (
                        com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(entry.title).contains(normQuery) ||
                            entry.titleEnglish?.let {
                                com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(it).contains(normQuery)
                            } == true ||
                            entry.titleJapanese?.let {
                                com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(it).contains(normQuery)
                            } == true
                        )) ||
                    // AniHyou paritesi: "Fuzzy arama kullan" — yazım hatası/kısaltma toleransı
                    (
                        com.kitsugi.animelist.data.settings.KitsugiContentPrefs.fuzzySearchEnabled && (
                            com.kitsugi.animelist.data.settings.KitsugiContentPrefs.fuzzyMatches(entry.title, normalizedQuery) ||
                                com.kitsugi.animelist.data.settings.KitsugiContentPrefs.fuzzyMatches(entry.titleEnglish.orEmpty(), normalizedQuery) ||
                                com.kitsugi.animelist.data.settings.KitsugiContentPrefs.fuzzyMatches(entry.titleJapanese.orEmpty(), normalizedQuery)
                            )
                        )
            }
        }
        .toList()
}

internal fun groupMyListEntriesByStatus(
    entries: List<MediaEntry>
): List<Pair<WatchStatus, List<MediaEntry>>> {
    val statusOrder = listOf(
        WatchStatus.Watching,
        WatchStatus.Repeating,
        WatchStatus.Planned,
        WatchStatus.Paused,
        WatchStatus.Dropped,
        WatchStatus.Completed
    )
    return statusOrder.map { status -> status to entries.filter { it.status == status } }
        .filter { (_, statusEntries) -> statusEntries.isNotEmpty() }
}

/** Sıralama seçilmediğindeki varsayılan gezinme: durum bölümleri + bölüm içi son eklenen. */
internal const val MY_LIST_SORT_DEFAULT = "default"

/** Kullanıcı bilinçli bir sıralama seçti mi? (Varsayılan gezinme dışında her şey) */
internal fun isExplicitMyListSort(sortId: String): Boolean =
    sortId.isNotBlank() && sortId != MY_LIST_SORT_DEFAULT

/** "Son eklenen / İlk eklenen" tarih bölümleriyle (Bugün, Bu Hafta…) gösterilir. */
internal fun myListSortUsesAddedDateSections(sortId: String): Boolean =
    sortId == "newest" || sortId == "oldest"

/**
 * Liste düz (tek akış) mı, durum bölümlü mü çizilecek?
 *
 * KÖK NEDEN DÜZELTMESİ (2026-10-23): önceden sıralama önce uygulanıyor, sonra
 * liste HER ZAMAN durum bölümlerine (İzleniyor → Planlandı → … → Tamamlandı)
 * yeniden ayrılıyordu. Sonuç: kullanıcı "Son eklenen" (veya Puan, Ada göre…)
 * seçse bile global sıra bölümler tarafından eziliyor, sıralama yalnızca
 * bölüm İÇİNDE geçerli oluyordu — dün eklediğin "Tamamlandı" kaydı, aylar
 * önce eklenmiş "İzleniyor" kayıtlarının altında kalıyordu.
 *
 * Yeni sözleşme:
 * - Ayrılmış liste tarzı kapalıysa → her zaman düz (mevcut AniHyou paritesi).
 * - Tek bir durum filtresi aktifse → düz (tek bölümün başlığı gürültüdür;
 *   önceden yalnızca "Tamamlandı" için özel durum vardı, asimetri kalktı).
 * - Kullanıcı bir sıralama seçtiyse → düz; SIRALAMA KAZANIR, durum yalnızca
 *   filtre olarak kalır. "Son eklenen/İlk eklenen" düz akışta tarih
 *   bölümleriyle gösterilir ([myListSortUsesAddedDateSections]).
 * - Varsayılan sıralama + "Tümü" durumu → klasik durum bölümlü gezinme.
 */
internal fun myListUsesFlatSortedLayout(
    sortId: String,
    selectedStatusFilterId: String,
    separatedListStyle: Boolean
): Boolean {
    if (!separatedListStyle) return true
    if (selectedStatusFilterId != "all") return true
    return isExplicitMyListSort(sortId)
}

/** "Son eklenen" görünümünün tarih bölümleri. Etiketler strings.xml'den çözülür. */
internal enum class MyListAddedPeriod(@androidx.annotation.StringRes val titleRes: Int) {
    TODAY(R.string.mylist_added_today),
    YESTERDAY(R.string.mylist_added_yesterday),
    THIS_WEEK(R.string.mylist_added_this_week),
    LAST_WEEK(R.string.mylist_added_last_week),
    THIS_MONTH(R.string.mylist_added_this_month),
    LAST_MONTH(R.string.mylist_added_last_month),
    LAST_3_MONTHS(R.string.mylist_added_last_3_months),
    THIS_YEAR(R.string.mylist_added_this_year),
    LAST_YEAR(R.string.mylist_added_last_year),
    OLDER(R.string.mylist_added_older),
    UNKNOWN(R.string.mylist_added_unknown)
}

/**
 * (Zaten eklenme tarihine göre sıralı) listeyi dönem bölümlerine ayırır.
 * Bölüm sırası, girdide ilk görülme sırasıdır — böylece azalan sıralamada
 * "Bugün" önce, artan sıralamada en eski dönem önce gelir; iki yönde de
 * ek bir bölüm-sırası tablosuna gerek yoktur.
 */
internal fun groupMyListEntriesByAddedPeriod(
    entries: List<MediaEntry>,
    nowEpochSeconds: Long = System.currentTimeMillis() / 1000L
): List<Pair<MyListAddedPeriod, List<MediaEntry>>> {
    if (entries.isEmpty()) return emptyList()
    val zone = java.time.ZoneId.systemDefault()
    val today = java.time.Instant.ofEpochSecond(nowEpochSeconds).atZone(zone).toLocalDate()
    val groups = LinkedHashMap<MyListAddedPeriod, MutableList<MediaEntry>>()
    entries.forEach { entry ->
        val addedSec = com.kitsugi.animelist.model.normalizeToEpochSeconds(entry.addedAt)
        val period = if (addedSec <= 0L) {
            MyListAddedPeriod.UNKNOWN
        } else {
            val date = java.time.Instant.ofEpochSecond(addedSec).atZone(zone).toLocalDate()
            val days = java.time.temporal.ChronoUnit.DAYS.between(date, today)
            when {
                days <= 0L -> MyListAddedPeriod.TODAY // saat dilimi farkıyla "yarın" damgalılar da bugün sayılır
                days == 1L -> MyListAddedPeriod.YESTERDAY
                days < 7L -> MyListAddedPeriod.THIS_WEEK
                days < 14L -> MyListAddedPeriod.LAST_WEEK
                date.year == today.year && date.month == today.month -> MyListAddedPeriod.THIS_MONTH
                date.year == today.minusMonths(1).year && date.month == today.minusMonths(1).month ->
                    MyListAddedPeriod.LAST_MONTH
                days < 90L -> MyListAddedPeriod.LAST_3_MONTHS
                date.year == today.year -> MyListAddedPeriod.THIS_YEAR
                date.year == today.year - 1 -> MyListAddedPeriod.LAST_YEAR
                else -> MyListAddedPeriod.OLDER
            }
        }
        groups.getOrPut(period) { mutableListOf() } += entry
    }
    return groups.entries.map { (period, periodEntries) -> period to periodEntries.toList() }
}

/** Sıralama anahtarları satır başına BİR kez hesaplanır; karşılaştırıcı yalnızca okur. */
private class MyListSortKeys(
    val entry: MediaEntry,
    /** Ekranda görülen başlığın (başlık dili uygulanmış) küçük harfli hali. */
    val titleKey: String,
    val addedAtSec: Long,
    val updatedAtSec: Long,
    /** Puanı olmayan (null/0) kayıt null — iki yönde de sona düşer. */
    val scoreKey: Int?,
    /** 0..1 arası tamamlanma oranı; toplam bilinmiyorsa null. */
    val progressRatio: Float?,
    val startDateKey: String?,
    val endDateKey: String?,
    val yearKey: Int?
)

/**
 * Tarih metnini sözlüksel karşılaştırmaya uygun `yyyy-MM-dd` biçimine normalize
 * eder (tek haneli ay/gün sıfırla doldurulur; saat eki atılır). Boş/geçersiz → null.
 */
private fun sortableDateKey(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty() || value.equals("null", ignoreCase = true)) return null
    val parts = value.split('-')
    if (parts.size < 3) return value // bilinmeyen biçim: olduğu gibi karşılaştır, "var" say
    val year = parts[0].takeWhile { it.isDigit() }.padStart(4, '0')
    val month = parts[1].takeWhile { it.isDigit() }.padStart(2, '0')
    val day = parts[2].takeWhile { it.isDigit() }.padStart(2, '0')
    if (year.isBlank() || month.isBlank() || day.isBlank()) return value
    return "$year-$month-$day"
}

/**
 * Listem sıralama motoru — her seçeneğin KENDİ mantığı vardır:
 *
 * - `newest` / `oldest` ("Son/İlk eklenen"): gerçek eklenme zamanı
 *   ([MediaEntry.addedAt]). Eskiden yerel satır `id`'si kullanılıyordu; toplu
 *   içe aktarımda id sırası kullanıcının ekleme sırası DEĞİLDİR (AniList sayfaları
 *   MEDIA_ID sırasıyla gelir) — "çalışmıyor" şikâyetinin kökü buydu. Eklenme
 *   zamanı bilinmeyen kayıt iki yönde de sona düşer; bilinmeyenler kendi içinde
 *   güncelleme zamanı + yerel id ile sıralanır (en iyi vekil).
 * - `title` / `title_desc`: EKRANDA GÖRÜLEN başlığa göre (başlık dili uygulanır);
 *   önceden hep depolanan romaji başlık kullanılıyordu, İngilizce başlık dilinde
 *   sıra ekrandakiyle uyuşmuyordu. Aynı adlı kayıtlarda (sezonlar) yayın yılı artan.
 * - `score` / `score_asc`: puansız kayıt iki yönde de SONDA (önceden artan
 *   sıralamada puansızlar başa kaçabiliyordu).
 * - `progress` / `progress_asc`: tamamlanma ORANI (progress/total). Önceden ham
 *   bölüm sayısıydı: 100/1000'lik manga, bitmiş 12/12'lik animenin üstündeydi.
 *   Toplamı bilinmeyen kayıt oran sırasında sonda, kendi içinde bölüm sayısına göre.
 * - `favorites`: favoriler önde; her küme kendi içinde son eklenene göre
 *   (önceden yalnızca ada göreydi — yeni favoriler arada kayboluyordu).
 * - `start_date_*` / `end_date_*` / `year_*` / `updated_*` / `priority_*`:
 *   değeri olmayan kayıt iki yönde de sonda.
 * - `updated_*`: Bangumi'nin milisaniye yazdığı değerler saniyeye normalize
 *   edilir; yoksa Bangumi satırları (×1000) her zaman haksızca en üstteydi.
 * - [MY_LIST_SORT_DEFAULT]: bölüm içi gezinme sırası = son eklenen mantığı.
 *
 * Tüm seçeneklerde son eşitlik bozma deterministiktir (başlık → kimlik);
 * anahtarlar satır başına bir kez hesaplanır (Schwartzian transform — karşılaştırıcı
 * içinde `lowercase()`/tarih ayrıştırma tekrar tekrar çalışmaz).
 */
internal fun applySort(
    entries: List<MediaEntry>,
    sortId: String,
    titleLanguage: String? = null
): List<MediaEntry> {
    if (entries.size < 2) return entries

    val decorated = entries.map { entry ->
        val displayTitle = if (titleLanguage != null) {
            with(com.kitsugi.animelist.utils.PreferenceHelpers) { entry.getDisplayTitle(titleLanguage) }
        } else {
            entry.title
        }
        MyListSortKeys(
            entry = entry,
            titleKey = displayTitle.trim().lowercase(),
            addedAtSec = com.kitsugi.animelist.model.normalizeToEpochSeconds(entry.addedAt),
            updatedAtSec = com.kitsugi.animelist.model.normalizeToEpochSeconds(entry.updatedAt),
            scoreKey = entry.score?.takeIf { it > 0 },
            progressRatio = entry.total?.takeIf { it > 0 }?.let { total ->
                (entry.progress.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            },
            startDateKey = sortableDateKey(entry.startDate),
            endDateKey = sortableDateKey(entry.endDate),
            yearKey = entry.year?.takeIf { it > 0 }
        )
    }

    // Ortak eşitlik zincirleri
    val byAddedDescThenTitle: Comparator<MyListSortKeys> =
        compareByDescending<MyListSortKeys> { it.addedAtSec }
            .thenBy { it.titleKey }
            .thenByDescending { it.entry.id }
    val byAddedAscThenTitle: Comparator<MyListSortKeys> =
        compareBy<MyListSortKeys> { it.addedAtSec <= 0L }
            .thenBy { it.addedAtSec }
            .thenBy { it.titleKey }
            .thenBy { it.entry.id }

    val newestComparator: Comparator<MyListSortKeys> =
        compareByDescending<MyListSortKeys> { it.addedAtSec }
            .thenByDescending { it.updatedAtSec }
            .thenByDescending { it.entry.id }
            .thenBy { it.titleKey }

    val comparator: Comparator<MyListSortKeys> = when (sortId) {
        // ── Eklenme tarihi ────────────────────────────────────────────────
        "newest", "added" -> newestComparator
        "oldest" ->
            compareBy<MyListSortKeys> { it.addedAtSec <= 0L }
                .thenBy { it.addedAtSec }
                .thenBy { it.updatedAtSec <= 0L }
                .thenBy { it.updatedAtSec }
                .thenBy { it.entry.id }
                .thenBy { it.titleKey }

        // ── Başlık ────────────────────────────────────────────────────────
        "title" ->
            compareBy<MyListSortKeys> { it.titleKey }
                .thenBy { it.yearKey == null }
                .thenBy { it.yearKey ?: 0 }
                .thenBy { it.entry.id }
        "title_desc" ->
            compareByDescending<MyListSortKeys> { it.titleKey }
                .thenBy { it.yearKey == null }
                .thenBy { it.yearKey ?: 0 }
                .thenBy { it.entry.id }

        // ── Puan (puansız her yönde sonda) ────────────────────────────────
        "score" ->
            compareByDescending<MyListSortKeys> { it.scoreKey ?: -1 }
                .then(byAddedDescThenTitle)
        "score_asc" ->
            compareBy<MyListSortKeys> { it.scoreKey == null }
                .thenBy { it.scoreKey ?: 0 }
                .then(byAddedDescThenTitle)

        // ── İlerleme (tamamlanma oranı; toplam bilinmiyorsa sonda) ───────
        "progress" ->
            compareByDescending<MyListSortKeys> { it.progressRatio ?: -1f }
                .thenByDescending { it.entry.progress }
                .then(byAddedDescThenTitle)
        "progress_asc" ->
            compareBy<MyListSortKeys> { it.progressRatio == null }
                .thenBy { it.progressRatio ?: 0f }
                .thenBy { it.entry.progress }
                .then(byAddedDescThenTitle)

        // ── Favoriler (önce favoriler; küme içinde son eklenen üste) ─────
        "favorites" ->
            compareByDescending<MyListSortKeys> { it.entry.isFavorite }
                .then(byAddedDescThenTitle)

        // ── Başlangıç / bitiş tarihi (tarihsiz her yönde sonda) ──────────
        "start_date_desc" ->
            compareByDescending<MyListSortKeys> { it.startDateKey ?: "" }
                .then(byAddedDescThenTitle)
        "start_date_asc" ->
            compareBy<MyListSortKeys> { it.startDateKey == null }
                .thenBy { it.startDateKey ?: "" }
                .then(byAddedAscThenTitle)
        "end_date_desc" ->
            compareByDescending<MyListSortKeys> { it.endDateKey ?: "" }
                .then(byAddedDescThenTitle)
        "end_date_asc" ->
            compareBy<MyListSortKeys> { it.endDateKey == null }
                .thenBy { it.endDateKey ?: "" }
                .then(byAddedAscThenTitle)

        // ── Yayın yılı (yılsız her yönde sonda; eşitlikte ada göre) ──────
        "year_desc" ->
            compareByDescending<MyListSortKeys> { it.yearKey ?: 0 }
                .thenBy { it.titleKey }
                .thenByDescending { it.entry.id }
        "year_asc" ->
            compareBy<MyListSortKeys> { it.yearKey == null }
                .thenBy { it.yearKey ?: 0 }
                .thenBy { it.titleKey }
                .thenBy { it.entry.id }

        // ── Son güncelleme (normalize edilmiş; zamansız sonda) ───────────
        "updated_desc" ->
            compareByDescending<MyListSortKeys> { it.updatedAtSec }
                .then(byAddedDescThenTitle)
        "updated_asc" ->
            compareBy<MyListSortKeys> { it.updatedAtSec <= 0L }
                .thenBy { it.updatedAtSec }
                .then(byAddedAscThenTitle)

        // ── Tekrar sayısı ─────────────────────────────────────────────────
        "repeat_desc" ->
            compareByDescending<MyListSortKeys> { it.entry.repeatCount }
                .then(byAddedDescThenTitle)
        "repeat_asc" ->
            compareBy<MyListSortKeys> { it.entry.repeatCount }
                .thenBy { it.titleKey }
                .thenBy { it.entry.id }

        // ── Öncelik (önceliksiz her yönde sonda; 0 gerçek değerdir) ──────
        "priority_desc" ->
            compareByDescending<MyListSortKeys> { it.entry.priority ?: -1 }
                .then(byAddedDescThenTitle)
        "priority_asc" ->
            compareBy<MyListSortKeys> { it.entry.priority == null }
                .thenBy { it.entry.priority ?: 0 }
                .thenBy { it.titleKey }
                .thenBy { it.entry.id }

        // Varsayılan gezinme ve bilinmeyen seçimler: son eklenen mantığı.
        else -> newestComparator
    }

    return decorated.sortedWith(comparator).map { it.entry }
}

@Composable
internal fun getSortTitle(sortId: String): String = when (sortId) {
    "score" -> "Puan (En yüksek)"
    "score_asc" -> "Puan (En düşük)"
    else -> stringResource(
        when (sortId) {
            MY_LIST_SORT_DEFAULT -> R.string.mylist_sort_default
            "newest", "added" -> R.string.mylist_sort_newest
            "oldest" -> R.string.mylist_sort_oldest
            "title" -> R.string.mylist_sort_title_asc
            "title_desc" -> R.string.mylist_sort_title_desc
            "progress" -> R.string.mylist_sort_progress_high
            "progress_asc" -> R.string.mylist_sort_progress_low
            "favorites" -> R.string.mylist_sort_favorites
            "start_date_desc" -> R.string.mylist_sort_start_new
            "start_date_asc" -> R.string.mylist_sort_start_old
            "end_date_desc" -> R.string.mylist_sort_end_new
            "end_date_asc" -> R.string.mylist_sort_end_old
            "year_desc" -> R.string.mylist_sort_year_new
            "year_asc" -> R.string.mylist_sort_year_old
            "updated_desc" -> R.string.mylist_sort_updated
            "updated_asc" -> R.string.mylist_sort_updated_old
            "repeat_desc" -> R.string.mylist_sort_repeat_high
            "repeat_asc" -> R.string.mylist_sort_repeat_low
            "priority_desc" -> R.string.mylist_sort_priority_high
            "priority_asc" -> R.string.mylist_sort_priority_low
            else -> R.string.mylist_sort_default
        }
    )
}

internal fun cardSpacingForLayout(layoutId: String): Dp {
    return when (layoutId) {
        "compact"    -> 8.dp
        "large"      -> 18.dp
        "grid_2col"  -> 8.dp
        else         -> 12.dp  // comfortable (varsayılan)
    }
}
