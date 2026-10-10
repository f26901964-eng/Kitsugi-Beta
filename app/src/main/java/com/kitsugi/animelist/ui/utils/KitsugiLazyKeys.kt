package com.kitsugi.animelist.ui.utils

import com.kitsugi.animelist.data.remote.JikanSearchResult

/**
 * LazyColumn / LazyRow / LazyVerticalGrid öğeleri için TEK merkezden üretilen kimlik anahtarı.
 *
 * NEDEN: Kaynakların id uzayları medya türüne göre AYRIDIR — aynı sayısal kimlik hem anime
 * hem manga (MAL/Jikan, Kitsu, Shikimori, Simkl) veya hem film hem dizi (TMDB) olabilir.
 * Örneğin MAL anime #21 ile MAL manga #21 iki FARKLI yapımdır.
 *
 * Liste anahtarı yalnızca `source + malId` ile kurulursa "Tümü" (karma anime+manga) veya
 * karma arama listelerinde iki farklı öğe **aynı anahtarı** alır. Compose aynı anahtarlı
 * öğeleri tek bir slot sanıp üst üste çizer (kutucuklar üst üste biner), kaydırma konumu ve
 * öğe geri dönüşümü bozulur; ayrıca "Key was already used" çökmesi üretir.
 *
 * Bu yüzden anahtar şu üç kimlik alanını birlikte taşımak ZORUNDADIR:
 *   `source` (hangi API) + `type` (anime/manga/film/dizi) + sayısal kimlik (`malId`/`tmdbId`).
 * `cs3Url` yalnızca eklenti (CS3) sonuçlarında dolduğu için hash'i son ek olarak korunur.
 *
 * Kullanım: `key = { _, item -> item.kitsugiLazyKey("grid") }` veya `key = { it.kitsugiLazyKey("tv") }`
 * (prefix o listenin okunabilirlik etiketidir; farklı listeler aynı öğeyi paylaşabilir).
 */
fun JikanSearchResult.kitsugiLazyKey(prefix: String): String =
    "${prefix}_${source.lowercase()}_${type.name.lowercase()}_${malId}_${tmdbId ?: 0}_${cs3Url.orEmpty().hashCode()}"
