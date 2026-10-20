package com.kitsugi.animelist.data.local

import android.content.Context
import com.kitsugi.animelist.data.auth.CrossProviderIdentityCache
import com.kitsugi.animelist.data.auth.CrossProviderIds
import com.kitsugi.animelist.data.remote.BangumiLocalMappingCache
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaIdentity
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * BİRLEŞİK KÜTÜPHANE (Listem → Tümü) İÇİN KAYNAKLARARASI KİMLİK KÖPRÜSÜ.
 *
 * ## Kök neden
 * Kitsugi'de `MediaEntry.malId` alanı **satırın kendi kaynağının** kimliğini taşır
 * (AniList 100M, Simkl 150M, Kitsu 300M, Shikimori 400M, Bangumi 500M ofsetleri).
 * [MediaIdentity.keys] bu yüzden her kimliği kaynak uzayına yazar: içe aktarılmış bir
 * Bangumi satırı yalnızca `Anime:bangumi:<subject_id>` üretir, aynı yapımın AniList satırı
 * `Anime:mal:<id>` / `Anime:anilist:<id>` üretir. **Kesişim boş** → birleşik listede iki satır
 * yalnızca BAŞLIK ile eşleşebilir. Bangumi'nin liste API'si `name`/`name_cn` (Çince) döndürdüğü
 * için `Sousou no Frieren` ile `葬送的芙莉莲` hiç eşmez; sonuçta Bangumi'deki her kayıt "Tümü"
 * sekmesinde ayrı bir kart olur ve sayaç şişer (rapor: 786 tekil yapım → 1502 kart).
 *
 * ## Çözüm
 * Çapraz eşitlemenin ZATEN çözüp kalıcı yazdığı iki kimlik kaynağı okunur — ağa çıkılmaz:
 *  1. [CrossProviderIdentityCache] (`cross_provider_identity_v1`): satır başına altı kimlik.
 *  2. [BangumiLocalMappingCache] (`bangumi_id_mapping`): `bgm_a_<malId> → subject_id`.
 *     Bangumi'nin resmî MAL eşleme ucu olmadığı için tek güvenilir köprü budur; burada **tersine**
 *     çevrilir (`subject_id → malId`). Bir eşleme birden fazla MAL ID'ye çıkıyorsa **kullanılmaz**
 *     (belirsiz eşleme, yanlış yapımı birleştirmektense ayrı bırakmayı yeğleriz).
 *
 * Bulunan kimlikler satıra YAZILMAZ; eşitleme anında "ek kimlik anahtarı" olarak verilir. Böylece
 *  • platform sekmeleri ve dış API yazmaları kendi kaynağının kimliğini kullanmaya devam eder,
 *  • `MediaEntry.malId` sözleşmesi (ve [isBangumiListMember] gibi aralık kontrolleri) bozulmaz,
 *  • çözülememiş kayıtlar eski davranışını korur.
 *
 * Bu köprü yalnızca *zaten doğrulanmış* eşleşmeleri taşır: `CrossProviderIds` hiç "aramada ilk
 * sonuç" üretmez, `BangumiLocalMappingCache` ise yalnızca tek ve net başlık eşleşmesinde yazılır.
 */
object MyListIdentityCrosswalk {

    private val bangumiMappingKey = Regex("^bgm_([am])_(\\d+)$")

    /**
     * `MediaEntry.id` → o satır için bilinen ek kanonik kimlik anahtarları.
     * Yalnızca kendi uzayında eşleştirilebilir kimliği olmayan satırlar döner; boş harita
     * "köprü gerekmiyor/yok" demektir ve çağıran taraf davranışı değiştirmez.
     */
    suspend fun build(context: Context, entries: List<MediaEntry>): Map<Int, Set<String>> {
        if (entries.isEmpty()) return emptyMap()
        return withContext(Dispatchers.IO) {
            val pending = entries.filter { needsCrosswalk(it) }
            if (pending.isEmpty()) return@withContext emptyMap()

            val bangumiSubjectToMal = runCatching { BangumiLocalMappingCache.snapshot(context) }
                .getOrNull()
                ?.let { reverseBangumiMapping(it) }
                .orEmpty()

            val result = LinkedHashMap<Int, Set<String>>(pending.size)
            pending.forEach { entry ->
                val extras = extraKeysFor(entry, cachedIds(context, entry), bangumiSubjectToMal)
                if (extras.isNotEmpty()) result[entry.id] = extras
            }
            result
        }
    }

    /**
     * Satırın kendi kimliği bir eşitleme eksenine yetiyor mu?
     * Anime/manga için MAL ekseni, film/dizi için TMDB/Simkl ekseni aranır — [MediaIdentity.keys]
     * film/dizi satırlarında MAL uzayı üretmediğinden eksenler türle birlikte gelir.
     */
    internal fun needsCrosswalk(entry: MediaEntry): Boolean {
        val namespaces = MediaIdentity.keys(entry).mapTo(HashSet()) { it.substringBeforeLast(':') }
        val prefix = entry.type.name
        return when (entry.type) {
            MediaType.Anime, MediaType.Manga -> "$prefix:mal" !in namespaces
            else -> "$prefix:tmdb" !in namespaces && "$prefix:simkl" !in namespaces
        }
    }

    private fun cachedIds(context: Context, entry: MediaEntry): CrossProviderIds? = runCatching {
        // Anahtar, CrossProviderIdentityResolver'ın yazdığıyla birebir aynı olmalı.
        CrossProviderIdentityCache.read(
            context = context,
            key = CrossProviderIds.cacheKey(
                type = entry.type,
                storedId = entry.malId,
                title = entry.title,
                titleEnglish = entry.titleEnglish,
                year = entry.year,
                source = entry.source
            )
        )
    }.getOrNull()?.ids

    /**
     * Eşitlemeye katılacak ek anahtarlar. [ids] çapraz kimlik önbelleğinden, [bangumiSubjectToMal]
     * ters çevrilmiş Bangumi eşleme önbelleğinden gelir.
     *
     * Kural: satırın **zaten taşıdığı** bir uzay asla ezilmez. Bir satır kendi `malId` alanında
     * gerçek MAL ID'si taşıyorsa (mal/jikan/manual satırları) oradaki değer birinci eldir; önbellek
     * bayatlamış olabilir. Aynı uzaya ikinci bir değer eklemek, çakışma denetiminde iki farklı ID
     * gibi görünüp o eksen üzerindeki **tüm** birleşmeleri engellerdi; bu filtre bunu önler.
     */
    internal fun extraKeysFor(
        entry: MediaEntry,
        ids: CrossProviderIds?,
        bangumiSubjectToMal: Map<String, Int> = emptyMap()
    ): Set<String> {
        val prefix = entry.type.name
        val ownedNamespaces = MediaIdentity.keys(entry).mapTo(HashSet()) { it.substringBeforeLast(':') }
        val malId = ids?.malId ?: bangumiMalIdOfBangumiRow(entry, prefix, bangumiSubjectToMal)

        val candidates = listOfNotNull(
            malId?.let { "$prefix:mal:$it" },
            ids?.aniListId?.let { "$prefix:anilist:$it" },
            ids?.kitsuId?.let { "$prefix:kitsu:$it" },
            ids?.shikimoriId?.let { "$prefix:shikimori:$it" },
            ids?.bangumiSubjectId?.let { "$prefix:bangumi:$it" },
            ids?.simklId?.let { "$prefix:simkl:$it" },
            ids?.tmdbId?.let { "$prefix:tmdb:$it" }
        )
        if (candidates.isEmpty()) return emptySet()
        return candidates.filterTo(LinkedHashSet()) { it.substringBeforeLast(':') !in ownedNamespaces }
    }

    /** Bangumi satırının `subject_id`'si için önbellekten bulunan gerçek MAL ID. */
    private fun bangumiMalIdOfBangumiRow(
        entry: MediaEntry,
        typePrefix: String,
        bangumiSubjectToMal: Map<String, Int>
    ): Int? {
        if (bangumiSubjectToMal.isEmpty()) return null
        // Yalnızca Anime/Manga tarafında MAL ekseni üretilir (MediaIdentity.keys sözleşmesi).
        if (entry.type != MediaType.Anime && entry.type != MediaType.Manga) return null
        val subjectId = MediaIdentity.bangumiId(entry) ?: return null
        return bangumiSubjectToMal["$typePrefix:$subjectId"]
    }

    /**
     * `bgm_a_<malId> → subject_id` (animeler) ve `bgm_m_<malId> → subject_id` (kitaplar) kayıtlarını
     * `"<Tür>:<subject_id>" → malId` biçimine ters çevirir.
     *
     * Aynı条目 iki farklı MAL kaydına bağlanmışsa o eşleme atılır: belirsiz köprü, yanlış iki
     * yapımı birleştirmektense ikisini ayrı bırakmak daha az zarar verir.
     */
    internal fun reverseBangumiMapping(raw: Map<String, Int>): Map<String, Int> {
        if (raw.isEmpty()) return emptyMap()
        val malIdsBySubject = HashMap<String, MutableSet<Int>>()
        raw.forEach { (key, subjectId) ->
            if (subjectId <= 0) return@forEach
            val match = bangumiMappingKey.matchEntire(key) ?: return@forEach
            val malId = match.groupValues[2].toIntOrNull()
                ?.takeIf { it in CrossProviderIds.REAL_MAL_MIN..CrossProviderIds.REAL_MAL_MAX }
                ?: return@forEach
            val typePrefix = if (match.groupValues[1] == "a") MediaType.Anime.name else MediaType.Manga.name
            malIdsBySubject.getOrPut("$typePrefix:$subjectId") { LinkedHashSet() } += malId
        }
        return malIdsBySubject.mapNotNull { (subjectKey, malIds) ->
            malIds.singleOrNull()?.let { subjectKey to it }
        }.toMap()
    }
}
