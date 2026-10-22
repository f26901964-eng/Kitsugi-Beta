package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaIdentity
import com.kitsugi.animelist.model.WatchStatus

/**
 * TOPLU ÇAPRAZ EŞİTLEME İÇİN SAF BİRLEŞTİRME KURALI.
 *
 * Aynı yapının altı kaynaktaki kayıtları tek bir "doğru" duruma indirgenirken **hangi
 * değerin kazandığı** burada, tek bir yerde ve yan etkisiz olarak tanımlanır. Amaç:
 *
 *  - **Hiçbir alan gerilemez**: ilerleme asla düşmez, cilt ilerlemesi asla düşmez,
 *    tekrar sayısı asla düşmez.
 *  - **Tamamlanmış kayıt "planlandı"ya dönmez**: ilerleme toplamı yakalamışsa durum
 *    `Completed`tir; `Planned` bildiren bir kaynak, ilerlemesi olan kayıtları ezemez.
 *  - **Çelişen durumlarda son kullanıcı işlemi kazanır**: `Dropped` / `Paused` / `Watching`
 *    gibi birbirini dışlayan durumlarda en son güncellenen "anlamlı" kayıt (en büyük
 *    `updatedAt`, `Planned` hariç) geçerlidir. Eskiden sabit bir öncelik sırası vardı
 *    (Watching > Paused > Dropped); bu, kullanıcı bir yapımı bilerek bıraktığında (Dropped)
 *    eski bir "izliyorum" kaydının onu geri açmasına yol açıyordu. Ayrıca `Dropped` /
 *    `Paused`, ilerleme toplamı yakalamış olsa bile `Completed`'a yükseltilmez: 12/12
 *    izleyip yine de bırakan kullanıcının kararı korunur.
 *  - **Tarihler deterministik**: başlangıç = en erken, bitiş = en geç tarih (ISO
 *    sözlük sırası = kronolojik sıra). Eskiden "aday listesinde ilk dolu olan"
 *    kullanılıyordu; sonuç kaynak sırasına bağlıydı ve her turda değişebiliyordu.
 *  - **Puan**: sıfırdan büyük puanlar arasından en son güncellenen; eşitlikte yüksek olan.
 *
 * Yazım tarafında yalnızca **gerçekten değişmesi gereken alanlar** döner
 * ([dirtyFields]); böylece bir kaynağın kendi notu/etiketi/önceliği başka bir kaynağın
 * değeriyle ezilmez. (Not/etiket/öncelik bilinçli olarak birleştirilmez: her sağlayıcının
 * metin biçimi ve öncelik ölçeği farklıdır — MAL 0..2, AniList 0..255, Bangumi yok.)
 */
object CrossSyncMergePolicy {

    /** Birleştirilmiş hedef değerler. */
    data class Merged(
        val status: WatchStatus,
        /** Yazılabilir ilerleme: [rawMaxProgress] toplam biliniyorsa toplama kırpılır. */
        val progress: Int,
        /** Kaynaklardaki en yüksek ilerleme (kırpılmamış; raporlama için). */
        val rawMaxProgress: Int,
        val volumeProgress: Int,
        val score: Int?,
        val startDate: String?,
        val endDate: String?,
        val repeatCount: Int,
        val isRepeating: Boolean,
        val isFavorite: Boolean,
        /** Çıkarım için kullanılan toplam (Simkl'in "yayınlanmış bölüm" sayısı hariç). */
        val total: Int?
    )

    /**
     * @param candidates aynı yapının kaynak kayıtları (aynı grupta eşleşmiş satırlar).
     *                   Boş liste bir programlama hatasıdır; çağıran taraf boş grubu atlar.
     */
    fun merge(candidates: List<MediaEntry>): Merged {
        require(candidates.isNotEmpty()) { "merge() en az bir aday ister" }

        val total = totalOf(candidates)
        val rawMaxProgress = candidates.maxOf { it.progress }
        val rewatchRows = candidates.filter { it.isRewatchInProgress() }

        // Toplam biliniyorsa daha fazlası yazılamaz: MAL `num_watched_episodes` toplamı
        // aşınca 400 döner, Kitsu 422 verir. Kırpma, yazımın tümden düşmesini engeller.
        //
        // TEKRAR İZLENİYORSA ilerleme yeniden sayılır: 12/12 bitmiş bir yapımı tekrar
        // izlemeye başlayan kullanıcıda MAL 12, AniList 2 bildirir. `max` almak AniList'e
        // 12 yazıp tekrarı anında "bitmiş" gösterirdi. Bu yüzden tekrar sürerken EN GÜNCEL
        // tekrar satırının ilerlemesi esastır (MAL-Sync de tekrar sırasında ilerlemeyi
        // kaynak satırın kendi değeriyle yazar).
        val progress = when {
            rewatchRows.isNotEmpty() -> {
                val rewatchProgress = rewatchRows.maxByOrNull { it.updatedAt }?.progress
                    ?: rawMaxProgress
                if (total != null) rewatchProgress.coerceAtMost(total) else rewatchProgress
            }
            total != null -> rawMaxProgress.coerceAtMost(total)
            else -> rawMaxProgress
        }

        return Merged(
            status = resolveStatus(candidates, total, rawMaxProgress),
            progress = progress,
            rawMaxProgress = rawMaxProgress,
            volumeProgress = candidates.maxOf { it.volumeProgress }.coerceAtLeast(0),
            score = resolveScore(candidates),
            startDate = candidates.mapNotNull { it.startDate?.takeIf(String::isNotBlank) }.minOrNull(),
            endDate = candidates.mapNotNull { it.endDate?.takeIf(String::isNotBlank) }.maxOrNull(),
            repeatCount = candidates.maxOf { it.repeatCount }.coerceAtLeast(0),
            isRepeating = rewatchRows.isNotEmpty(),
            isFavorite = candidates.any { it.isFavorite },
            total = total
        )
    }

    /**
     * Bu kaynağa yazılması gereken alanlar.
     *
     * Kural: **yalnızca eksik/geride olan** alanlar yazılır. Kaynağın kendi değeri
     * hedef değerle aynıysa hiç istek üretilmez; kaynak daha ileriyse (ör. başka bir
     * istemciden 13. bölüm işlenmişse) geri yazılmaz.
     *
     * Dönen küme sağlayıcıya göre süzülmelidir: `fields.narrowTo("mal")`.
     */
    fun dirtyFields(current: MediaEntry, merged: Merged): Set<SyncField> {
        val fields = mutableSetOf<SyncField>()
        if (current.status != merged.status) fields += SyncField.STATUS
        if (current.progress < merged.progress) fields += SyncField.PROGRESS
        // Cilt ilerlemesi yalnızca ARTABİLİR; toplam bilinmediğinde kırpılmaz.
        if (current.volumeProgress < merged.volumeProgress) fields += SyncField.VOLUME_PROGRESS
        if (merged.score != null && current.score != merged.score) fields += SyncField.SCORE
        if (current.startDate.isNullOrBlank() && !merged.startDate.isNullOrBlank()) {
            fields += SyncField.START_DATE
        }
        if (current.endDate.isNullOrBlank() && !merged.endDate.isNullOrBlank()) {
            fields += SyncField.END_DATE
        }
        if (current.repeatCount < merged.repeatCount) fields += SyncField.REWATCH_COUNT
        // Favori yalnız TEK YÖNDE: bir yerde favorilendirilmişse diğer hedeflerde de
        // favorilenir. Ters yön bilinçli olarak yapılmaz; hiçbir sağlayıcı "favori değil"
        // bilgisini güvenilir biçimde bildirmez (MAL/Kitsu/Shikimori/Bangumi/Simkl'de
        // favori alanı hiç yoktur), kaldırma işlemi veri kaybı riski taşır.
        if (merged.isFavorite && !current.isFavorite) fields += SyncField.FAVORITE
        // NOT / TAGS / PRIORITY / FAVORITE bilinçli olarak YOK:
        //  - Not ve etiket her sağlayıcıda serbest metin; birleştirmek bir kaynağın
        //    metnini diğeriyle ezer (MAL-Sync de notu yalnız kaynak değiştiğinde yazar).
        //  - Öncelik ölçekleri uyumsuz (MAL 0..2, AniList 0..255).
        //  - Favori yalnız AniList'te var; oradan diğerlerine yazacak alan yok.
        return fields
    }

    /**
     * [provider] hedefine yazılacak alanlar (yeteneğe göre süzülmüş).
     *
     * Ek kural: "tekrar izleniyor" bazı hedeflerde AYRI BİR DURUMDUR (AniList `REPEATING`,
     * Shikimori `rewatching`), bazılarında bayraktır (MAL `is_rewatching`, Kitsu
     * `reconsuming`). Bayrak-tabanlı olmayan hedefe yalnızca bayrak farkı yüzünden durum
     * yazmak **sonsuz yazım döngüsü** üretir: hedef yazılan durumu geri okur, `isRepeating`
     * bilgisi hiç oluşmaz (AniList `repeat` alanı "şu an tekrar izleniyor" demek değildir),
     * her eşitlemede aynı istek yeniden atılır. Bu yüzden bayrak-tabanlı olmayan hedeflerde
     * durum ancak durumun KENDİSİ değiştiyse yazılır.
     */
    fun dirtyFieldsFor(current: MediaEntry, merged: Merged, provider: String): Set<SyncField> {
        val capabilities = ProviderCapabilitiesCatalog.of(provider)
        // Durum karşılaştırması hedefin KENDİ sözlüğünde yapılır: `Repeating`, MAL'de
        // `completed`, Kitsu'da `current`, Bangumi/Simkl'de hiçbir şey demektir.
        val providerStatus = capabilities.statusFor(current, merged.status)
        val fields = capabilities.narrow(
            dirtyFields(current, merged.copy(status = providerStatus))
        ).toMutableSet()
        // "Tekrar izleniyor" farkı yalnız BAYRAK taşıyan hedeflerde durum yazımıyla taşınır
        // (MAL `is_rewatching`, Kitsu `reconsuming`). Bayrak taşımayan hedeflerde bu fark
        // yazılabilir bir alan değildir; eklemek her turda aynı isteği üretirdi.
        if (capabilities.rewatchModel == ProviderCapabilities.RewatchModel.AS_FLAG &&
            SyncField.STATUS in capabilities.supports &&
            current.isRepeating != merged.isRepeating
        ) {
            fields += SyncField.STATUS
        }
        return fields
    }

    /**
     * Çıkarım için kullanılacak toplam.
     *
     * Simkl'in `total` değeri "yayınlanmış bölüm sayısı"dır (devam eden dizilerde gerçek
     * toplam değildir) → tamamlanma çıkarımında KULLANILMAZ. AniList/MAL/Kitsu/Shikimori
     * toplamları güvenilirdir.
     */
    fun totalOf(candidates: List<MediaEntry>): Int? = candidates
        .asSequence()
        .filter { MediaIdentity.canonicalSource(it.source) != "simkl" }
        .mapNotNull { it.total?.takeIf { total -> total > 0 } }
        .firstOrNull()

    /**
     * Durum çözümü — belgelenmiş, deterministik sıra:
     *
     * 1. Toplam biliniyor ve en yüksek ilerleme toplamı yakalamış → `Completed`.
     * 2. En son güncellenen "anlamlı" kayıt `Completed` diyorsa → `Completed`.
     * 3. Aksi hâlde en son güncellenen "anlamlı" kaydın durumu.
     *
     * "Anlamlı" = `Planned` dışındaki durumlar. Bir kaynakta yalnızca "planlandı" varsa
     * (kullanıcı o serviste hiç işlem yapmamış) ve başka bir kaynakta ilerleme varsa,
     * planlandı kaydı durumu belirlemez.
     */
    fun resolveStatus(candidates: List<MediaEntry>, total: Int?, maxProgress: Int): WatchStatus {
        val byRecency = candidates.sortedWith(
            compareByDescending<MediaEntry> { it.updatedAt }
                // Eşit zaman damgasında (ör. içe aktarılmış satırlar) sıra deterministik olsun:
                // ilerleme yüksek olan, sonra kaynak adı.
            .thenByDescending { it.progress }
                .thenBy { it.source }
        )
        val meaningful = byRecency.firstOrNull { it.status != WatchStatus.Planned } ?: byRecency.first()
        // TEKRAR İZLENİYOR: bir kaynak "şu an tekrar izleniyor" diyorsa kullanıcının durumu
        // budur. İlerleme toplamı yakalamış olsa bile `Completed`'a yükseltilmez: 12/12
        // bitmiş bir yapımı yeniden izlemeye başlayan kullanıcı "tamamlandı" değildir.
        // (Eski kod tam burada `Completed` üretip AniList'teki `REPEATING` durumunu ve
        // tekrar ilerlemesini eziyordu.)
        if (candidates.any { it.isRewatchInProgress() }) return WatchStatus.Repeating
        return when {
            // Kullanıcının EN SON yaptığı işlem bir durum değişikliğiyse o kazanır. Özellikle
            // `Dropped`/`Paused`: eski bir "tamamlandı" kaydı bu kararı geri almamalı.
            meaningful.status == WatchStatus.Dropped || meaningful.status == WatchStatus.Paused ->
                meaningful.status
            // En güncel kayıt tamamlandı diyorsa tamamlandı.
            meaningful.status == WatchStatus.Completed -> WatchStatus.Completed
            // Hiçbir kayıt "bırakıldı/duraklatıldı" demiyorsa ve ilerleme toplamı yakaladıysa
            // kaynaklar arası en yaygın tutarsızlık budur: bir serviste 12/12 izlenmiş ama
            // durum "izliyorum" kalmış. Tamamlandı'ya yükselt.
            total != null && maxProgress >= total -> WatchStatus.Completed
            else -> meaningful.status
        }
    }

    /**
     * "Şu an tekrar izleniyor" mu?
     *
     * `Planned` / `Dropped` / `Paused` satırlarda bayrak artık kalmış olabilir; bunlar
     * tekrar durumu sayılmaz (aksi hâlde terk edilmiş bir yapım tekrar yüzünden yeniden
     * açılırdı). Yalnız `Watching`/`Completed` + bayrak ya da doğrudan `Repeating` sayılır.
     */
    fun MediaEntry.isRewatchInProgress(): Boolean = when (status) {
        WatchStatus.Repeating -> true
        WatchStatus.Watching, WatchStatus.Completed -> isRepeating
        else -> false
    }

    /** Sıfırdan büyük puanlar arasından en son güncellenen; eşitlikte yüksek olan. */
    private fun resolveScore(candidates: List<MediaEntry>): Int? = candidates
        .asSequence()
        .filter { (it.score ?: 0) > 0 }
        .sortedWith(
            compareByDescending<MediaEntry> { it.updatedAt }
                .thenByDescending { it.score ?: 0 }
                .thenBy { it.source }
        )
        .mapNotNull { it.score }
        .firstOrNull()
}
