package com.kitsugi.animelist.data.manga

/**
 * Manga bölüm listesi sıralaması.
 *
 * Not: Eklentiler bölüm listesini **her zaman** en yeniden en eskiye döndürür
 * (bkz. [MangaSource.fetchChapterList]). Bu yüzden "en yeni" varsayılan ve en hızlı yoldur;
 * diğer seçenekler yalnızca listedeki alanlara göre sıralanır.
 */
enum class ChapterSortMode {
    /** Bölüm numarasına göre azalan (232 → 1). Varsayılan. */
    NewestFirst,

    /** Bölüm numarasına göre artan (1 → 232). */
    OldestFirst,

    /** Yükleme tarihine göre azalan; tarih yoksa bölüm numarasına düşer. */
    RecentlyUpdated;

    fun next(): ChapterSortMode = when (this) {
        NewestFirst -> OldestFirst
        OldestFirst -> RecentlyUpdated
        RecentlyUpdated -> NewestFirst
    }
}

/**
 * Bölüm listesini istenen sıralamaya göre dizer.
 *
 * Sıralama **kararlıdır**: bölüm numarası bilinmeyen (-1) kayıtlar kendi aralarında
 * eklentiden gelen sırayı korur, böylece her yenilemede liste zıplamaz.
 */
fun List<MangaChapter>.sortedForMode(mode: ChapterSortMode): List<MangaChapter> = when (mode) {
    ChapterSortMode.NewestFirst -> sortedWith(
        compareByDescending<MangaChapter> { it.chapterNumber }.thenByDescending { it.uploadDate }
    )

    ChapterSortMode.OldestFirst -> sortedWith(
        compareBy<MangaChapter> { it.chapterNumber }.thenBy { it.uploadDate }
    )

    ChapterSortMode.RecentlyUpdated -> sortedWith(
        compareByDescending<MangaChapter> { it.uploadDate }.thenByDescending { it.chapterNumber }
    )
}
