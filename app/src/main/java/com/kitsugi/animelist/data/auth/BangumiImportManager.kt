package com.kitsugi.animelist.data.auth

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.data.remote.BangumiIdNamespace
import com.kitsugi.animelist.data.remote.BangumiLocalizedName
import com.kitsugi.animelist.data.remote.BangumiNameLocalizer
import com.kitsugi.animelist.data.remote.BangumiTitleCache
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

/**
 * Bangumi (bgm.tv) kütüphanesini içe aktarma yöneticisi.
 *
 * Shikimori/Kitsu içe aktarmalarıyla aynı sözleşme:
 * `fetchAllLists(...)` → `List<MediaEntry>` → `repository.smartImport("bangumi", list)`.
 *
 * Kimlik: `MediaEntry.malId` alanında Bangumi **stableId** taşınır
 * (`subject_id + 500_000_000`, bkz. [BangumiIdNamespace]). Böylece aynı sayısal ID'ye sahip
 * bir MAL anime'si ile bir Bangumi条目'su birleşik kütüphanede karışmaz.
 *
 * MAL eşleşmesi: Bangumi v0 API'sinde MAL/AniList ID eşleme ucu **yoktur**. Eşleşme,
 *条目'nin `infobox` wiki alanlarındaki dış bağlantılardan veya sıkı başlık aramasından
 * türetilir; bulunan eşleşmeler [com.kitsugi.animelist.data.remote.BangumiLocalMappingCache]
 * içinde saklanır ve çapraz eşitleme (cross-sync) bunları tekrar kullanır.
 */
object BangumiImportManager {
    private const val TAG = "BangumiImportManager"

    data class BangumiUserProfile(
        val id: Long,
        val username: String,
        val nickname: String,
        val avatarUrl: String?
    )

    /** İçe aktarım satırının başlık üçlüsü: ana (romaji/Latin) ad, İngilizce ad, özgün ad. */
    internal data class BangumiImportTitles(
        val title: String,
        val titleEnglish: String?,
        val titleJapanese: String?
    )

    /**
     * Kısa subject adlarını kalıcı Latin ad önbelleğiyle birleştirir.
     *
     * Başlık dili sözleşmesi (bkz. `BangumiLibraryTitleHydrator`): kütüphane satırında
     * `title` romaji/Latin ana adı, `titleEnglish` gerçek İngilizce adı, `titleJapanese`
     * özgün adı tutar; gösterim seçili dile göre bu üçlüden seçilir. Koleksiyon ucu
     * infobox döndürmediğinden [localized] çoğu zaman yalnızca özgün adı taşır — önbellekte
     * daha önce çözülmüş bir karşılık varsa satır ilk andan itibaren dile uygun yazılır.
     * Önbellekte yoksa davranış eskisiyle aynıdır; eksik, içe aktarım sonrası çalışan
     * tamamlayıcıyla giderilir. Ağ/DB'ye dokunmayan saf fonksiyondur (birim testiyle doğrulanır).
     */
    internal fun resolveImportTitles(
        localized: BangumiLocalizedName?,
        cached: BangumiTitleCache.LatinTitles?,
        fallbackTitle: String
    ): BangumiImportTitles {
        val cachedRomaji = cached?.romaji?.trim()?.takeIf {
            com.kitsugi.animelist.utils.PreferenceHelpers.isLatinText(it)
        }
        val cachedEnglish = cached?.english?.trim()?.takeIf {
            com.kitsugi.animelist.utils.PreferenceHelpers.isLatinText(it)
        }
        return BangumiImportTitles(
            title = cachedRomaji ?: cachedEnglish ?: localized?.display ?: fallbackTitle,
            titleEnglish = cachedEnglish ?: localized?.english,
            titleJapanese = localized?.native
                ?: cached?.native?.trim()?.takeIf { it.isNotEmpty() }
        )
    }

    /** Giriş sonrası profil özeti (ayarlar ekranındaki kart için). */
    suspend fun fetchUserProfile(token: String): BangumiUserProfile = withContext(Dispatchers.IO) {
        val user = BangumiApiClient.getMe(token)
        BangumiUserProfile(
            id = user.id,
            username = user.username,
            nickname = user.nickname,
            avatarUrl = user.avatarUrl
        )
    }

    /**
     * Kullanıcının desteklenen koleksiyon türlerini (动画, 书籍 ve REAL film/dizileri) çeker.
     * Oyun ve müzik gibi Kitsugi'nin desteklemediği subject türleri atlanır.
     *
     * @param username Boş/`"-"` → token sahibi. Başka bir kullanıcının **herkese açık**
     *                 koleksiyonu da bu uçla okunabilir (özel收藏'lar hariç).
     * @param subjectType null = tüm türler; [BangumiApiClient.SubjectType.ANIME] / `.BOOK`.
     * @param resolveAdult NSFW bayrağını条目 bazında doğrulamak için ek `GET /v0/subjects/{id}`
     *        istekleri atılır. Koleksiyon listesi kısaltılmış条目 döndürdüğü için `nsfw`
     *        alanı içermez; bu nedenle varsayılan olarak açıktır. Maliyeti sınırlamak için
     *        en fazla [maxAdultLookups] desteklenen条目 sorgulanır.
     */
    suspend fun fetchAllLists(
        context: Context,
        token: String,
        username: String? = null,
        subjectType: Int? = null,
        resolveAdult: Boolean = true,
        maxAdultLookups: Int = 60
    ): List<MediaEntry> = withContext(Dispatchers.IO) {
        // ÖNEMLİ: `GET /v0/users/-/collections` Bangumi'de 404 verir ("-" yalnızca yazma
        // uçlarında geçerli). Okuma için gerçek kullanıcı adı (ya da sayısal ID) gerekir.
        val effectiveUsername = username?.takeIf { it.isNotBlank() && it != "-" }
            ?: BangumiAuthStore.resolveUsername(context)
            ?: throw BangumiApiClient.BangumiApiException(
                code = 401,
                title = "Unauthorized",
                description = null,
                message = KitsugiStrings.get(R.string.bangumi_kullanici_adi_cozulemedi_lutfen_bang)
            )
        val collections = fetchCollectionsWithRetry(context, token, effectiveUsername, subjectType)
        Log.i(TAG, "Bangumi içe aktarma: ${collections.size} koleksiyon kaydı çekildi")
        // Daha önce herhangi bir ekranda çözülmüş Latin/İngilizce adlar kalıcı önbellekte
        // durur; satırları yazarken kullan ki liste ilk çizimden itibaren seçili başlık
        // diline uysun. Önbellek henüz yüklenmediyse bu adım en iyi çabadır — eksikler içe
        // aktarım sonrası çalışan BangumiLibraryTitleHydrator ile tamamlanır.
        BangumiTitleCache.warmUp()

        val adultSubjectIds = if (resolveAdult && collections.isNotEmpty()) {
            val supportedSubjectIds = collections.asSequence()
                .filter { BangumiIdNamespace.supportsSubjectType(it.subjectType) }
                .map { it.subjectId }
                .distinct()
                .take(maxAdultLookups.coerceAtLeast(0))
                .toList()
            resolveNsfwSubjectIds(token, supportedSubjectIds)
        } else {
            emptySet()
        }

        collections.mapNotNull { collection ->
            val slim = collection.subject
            if (!BangumiIdNamespace.supportsSubjectType(collection.subjectType)) {
                return@mapNotNull null
            }
            val stableId = BangumiIdNamespace.stableIdFromRaw(collection.subjectId)
            if (stableId == null) {
                Log.w(TAG, "Bangumi subject_id ${collection.subjectId} stableId penceresi dışında, atlanıyor")
                return@mapNotNull null
            }
            val mediaType = BangumiIdNamespace.mediaTypeFor(collection.subjectType, slim?.platform)
            val isBookSubject = collection.subjectType == BangumiApiClient.SubjectType.BOOK
            // Koleksiyon yanıtı kısa subject döndürür; eldeki English/romaji/özgün alanları
            // kaybetmeden sakla ki Listem ekranı da Başlık Dili ayarına uyabilsin.
            // Kısa subject'te infobox olmadığından English/romaji çoğu zaman boştur; daha
            // önce çözülmüş adlar kalıcı önbellekten anında tamamlanır.
            val localizedTitle = slim?.let { BangumiNameLocalizer.entity(it.name, it.nameCn) }
            val importTitles = resolveImportTitles(
                localized = localizedTitle,
                cached = BangumiTitleCache.get(collection.subjectId),
                fallbackTitle = "Bangumi #${collection.subjectId}"
            )

            // İlerleme: kitap条目'larında `ep_status` güvenilirdir.动画条目'larında sunucu,
            // bölüm işaretlerinden (`打格子`) tamamlanma oranını hesaplar ve aynı alanda tutar.
            val progress = collection.epStatus.takeIf { it > 0 } ?: 0
            val total = slim?.eps?.takeIf { it > 0 }

            MediaEntry(
                id = 0,
                title = importTitles.title,
                subtitle = BangumiIdNamespace.SOURCE,
                titleEnglish = importTitles.titleEnglish,
                titleJapanese = importTitles.titleJapanese,
                imageUrl = BangumiApiClient.absoluteImageUrl(slim?.images?.poster) ?: "",
                type = mediaType,
                status = BangumiSyncManager.bangumiStatusToWatchStatus(collection.type),
                progress = progress,
                total = total,
                score = collection.rate.takeIf { it > 0 },
                isAdult = collection.subjectId in adultSubjectIds,
                malId = stableId,
                aniListEntryId = null,
                source = BangumiIdNamespace.SOURCE,
                updatedAt = parseBangumiTimestamp(collection.updatedAt),
                year = slim?.date?.take(4)?.toIntOrNull(),
                isPrivate = collection.private,
                tags = collection.tags.takeIf { it.isNotEmpty() }?.joinToString(", "),
                notes = collection.comment,
                volumeProgress = if (isBookSubject) collection.volStatus else 0
            )
        }
    }

    /**
     * Bangumi zaman damgası (`2022-06-19T18:44:13.6140127+08:00`) → epoch millis.
     *
     * API notu: bu alan koleksiyon **oluşturma** zamanını değil son değişiklik zamanını
     * temsil eder ve puan/yorum değişikliklerinde güncellenmemesi bir hata olarak
     * belgelenmiştir. Bu yüzden `MediaEntry.updatedAt` için "en iyi çaba" değeri sayılır.
     */
    fun parseBangumiTimestamp(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        return runCatching {
            val normalized = value
                // `android.time` ayrıştırıcısı 7 haneli kesri kabul etmez → 3 haneye indir.
                .replace(Regex("(\\.\\d{3})\\d+"), "$1")
                // `+08:00` biçimi ISO_OFFSET_DATE_TIME ile uyumludur; 'Z' yoksa sorun değil.
            java.time.OffsetDateTime.parse(normalized).toInstant().toEpochMilli()
        }.getOrElse {
            runCatching {
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                    .parse(value.take(19))?.time ?: 0L
            }.getOrDefault(0L)
        }
    }

    /**
     * Verilen `subject_id` kümesinden NSFW olanları ayıklar.
     * Başarılı pozitif/negatif sonuçlar import ve Listem'in artalan taraması arasında paylaşılır.
     */
    private suspend fun resolveNsfwSubjectIds(token: String, ids: List<Int>): Set<Int> =
        BangumiAdultResolver.resolveAdultFlags(token, ids).filterValues { it }.keys

    /**
     * Koleksiyon çekimini dener; HTTP 401 alırsa refresh token ile zorla yeni access token
     * alır ve bir kez daha dener (Shikimori `fetchRatesWithRetry` ile aynı desen).
     */
    private suspend fun fetchCollectionsWithRetry(
        context: Context,
        token: String,
        username: String,
        subjectType: Int?
    ): List<BangumiApiClient.BangumiUserCollection> {
        return try {
            BangumiApiClient.getAllUserCollections(token, username, subjectType)
        } catch (e: BangumiApiClient.BangumiApiException) {
            if (!e.isUnauthorized) throw e
            Log.w(TAG, "Koleksiyon çekimi 401 – token yenileme deneniyor")
            val newToken = BangumiAuthStore.forceRefresh(context)
            if (newToken.isNullOrBlank()) {
                throw BangumiApiClient.BangumiApiException(
                    code = 401,
                    title = "Unauthorized",
                    description = null,
                    message = KitsugiStrings.get(R.string.bangumi_oturumu_sona_erdi_http_401_lutfen_ba)
                )
            }
            BangumiApiClient.getAllUserCollections(newToken, username, subjectType)
        }
    }

    /**
     * Bir条目的 `infobox` alanından dış site bağlantılarını çıkarır (MAL/AniList eşlemesi için).
     *
     * Bangumi wiki infobox'ında `MAL` / `AniList` / `TVDB` gibi anahtarlar doğrudan
     * bulunmaz; bunun yerine条目 adı çevirileri ve resmî site yer alır. Bu yüzden
     * pratikte eşleme için en güvenilir yol:
     *  1. [com.kitsugi.animelist.data.remote.BangumiLocalMappingCache] (önceden çözülmüş),
     *  2. `bangumi-data/bangumi-data` (CC BY 4.0) veya `bangumi/Archive` dökümleri,
     *  3. sıkı başlık + yıl araması (bkz. [BangumiIdNamespace.resolveBangumiId]).
     */
    fun extractAliasTitles(subject: BangumiApiClient.BangumiSubject): List<String> {
        val aliases = mutableListOf<String>()
        subject.infobox.forEach { (key, values) ->
            if (key.contains("别名") || key.equals("alias", ignoreCase = true)) aliases += values
        }
        return aliases.distinct()
    }
}
