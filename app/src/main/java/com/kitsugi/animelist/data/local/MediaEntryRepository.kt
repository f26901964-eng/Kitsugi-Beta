package com.kitsugi.animelist.data.local

import com.kitsugi.animelist.data.auth.runSyncCatching
import android.content.Context
import com.kitsugi.animelist.data.auth.ExternalListSyncManager
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.Dispatchers
import com.kitsugi.animelist.utils.PreferenceHelpers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Bir liste içe aktarımının sonucu.
 *
 * [unchanged] alanı kritik: hiçbir şey değişmemişse veritabanına YAZILMAZ, Room yeni bir
 * emisyon üretmez ve Listem'in birleşik kütüphane hesabı boşuna baştan koşmaz.
 * [deferred] ise yerelde düzenlenmiş ama henüz uzak sunucuya gönderilememiş (çevrimdışı
 * kuyrukta bekleyen) satır sayısıdır — bu satırlar taze uzak veriyle ezilmez.
 */
data class ImportStats(
    val source: String,
    val fetched: Int = 0,
    val added: Int = 0,
    val updated: Int = 0,
    val unchanged: Int = 0,
    val deleted: Int = 0,
    val deferred: Int = 0
) {
    /** Yerel veride görünür bir değişiklik oldu mu? */
    val hasChanges: Boolean get() = added > 0 || updated > 0 || deleted > 0
}

class MediaEntryRepository(
    private val dao: MediaEntryDao,
    private val pendingSyncDao: PendingSyncDao? = null,
    val context: Context? = null,
    private val onExternalSyncMessage: ((String) -> Unit)? = null
) {
    /**
     * Liste içe aktarımlarını SERİLEŞTİRİR.
     *
     * Aynı kaynak için iki import eşzamanlı çalışabilir: sekmeyi yenileme (synchronizer
     * single-flight'ı yalnızca kendi yolunu korur), çapraz eşitleme doğrulama turu,
     * bildirim işçisinin Simkl birleştirmesi ve açılış senkronu farklı yollardan gelir.
     * İkisi de aynı "mevcut satırlar" anlık görüntüsünü okuyup aynı kaydı "yok" sanırsa
     * **çift kayıt** oluşur. Kilit, karşılaştırma+yazma bloğunu atomik hâle getirir.
     */
    private val importMutex = Mutex()

    /**
     * PERF (2026-10-20): [withCrossSourceAdultFlags] tüm tabloyu entity→domain'e çevirip
     * satır başına kimlik anahtarları hesaplar; büyük kütüphanelerde onlarca-milisaniyelik
     * iştir. Eski sürüm bu işi TOPLAYICI iş parçacığında (stateIn(viewModelScope) → Main)
     * çalıştırıyordu: hesap senkronu/kayit yazımı sırasında her tablo değişimi ana iş
     * parçacığını kilitliyor ve Listem/Keşfet donuyordu. Artık hesap [Dispatchers.Default]
     * üzerinde yapılır; [distinctUntilChanged] içerik değişmemiş emisyonları eler ve
     * [conflate] ardışık yazım patlamalarında (ör. +18 geri yazma turu satır satır günceller)
     * yalnızca EN GÜNCEL anlık görüntünün işlenmesini garanti eder.
     */
    val entriesFlow: Flow<List<MediaEntry>> = kotlinx.coroutines.flow.flow {
        dao.observeAll().collect { entities ->
            emit(entities.withCrossSourceAdultFlags())
        }
    }
        .distinctUntilChanged()
        .conflate()
        .flowOn(Dispatchers.Default)


    /**
     * @param skipBangumiWrite kayıt için Bangumi yazması çağrıda zaten yapıldıysa `true`;
     * canlı çapraz eşitleme diğer beş kaynağa yine yayılır.
     */
    suspend fun insert(
        entry: MediaEntry,
        syncExternal: Boolean = true,
        skipBangumiWrite: Boolean = false
    ) {
        val entryWithTime = if (entry.updatedAt == 0L) {
            entry.copy(updatedAt = System.currentTimeMillis() / 1000L)
        } else {
            entry
        }
        val newId = dao.insert(entryWithTime.copy(id = 0).toEntity())
        // DB'de oluşan gerçek id ile sync yap (AniList entryId geri yazımı doğru entity'yi yakalar)
        val persistedEntry = dao.getById(newId.toInt())?.toDomain() ?: entryWithTime.copy(id = newId.toInt())
        // Aynı yapımın başka kaynaklardaki yerel satırları da aynı kullanıcı değerlerini alsın.
        mirrorUserFieldsToSiblings(persistedEntry)
        if (syncExternal) syncEntryIfPossible(persistedEntry, skipBangumiWrite = skipBangumiWrite)
    }

    suspend fun insertAll(entries: List<MediaEntry>) {
        if (entries.isEmpty()) return
        val entriesWithTime = entries.map { entry ->
            if (entry.updatedAt == 0L) {
                entry.copy(updatedAt = System.currentTimeMillis() / 1000L)
            } else {
                entry
            }
        }
        dao.insertAll(entriesWithTime.map { it.copy(id = 0).toEntity() })
    }

    /**
     * Granüler import: mevcut kayıtlarla karşılaştırır, sadece
     * gerçekten değişen kayıtları günceller. Tüm işlem tek bir
     * @Transaction içinde çalışır → Room Flow yalnızca 1 kez
     * tetiklenir ve Compose yalnızca değişen kartları yeniden çizer.
     *
     * FAKAT (2026-10-23): "tek transaction" tek başına yeterli değildi — eski sürüm
     * eşleşen HER satırı, içeriği birebir aynı olsa bile `updates` listesine koyuyordu.
     * Yani hiçbir şey değişmemiş bir yenileme bile tüm tabloyu yeniden yazıp Room'un
     * TÜM kütüphaneyi yeniden yayınlamasına, dolayısıyla Listem'in birleşik
     * hesaplamasının (kimlik köprüsü + gruplama, 1000+ satır) baştan koşmasına yol
     * açıyordu. Artık satırlar `updatedAt` dışındaki alanlarda karşılaştırılır; fark
     * yoksa ne `updateAll` çağrılır ne de transaction açılır.
     *
     * @param source  "anilist", "mal", "simkl" vb.
     * @param importedEntries  Uzak API'dan gelen güncel liste
     * @return kaç kaydın eklendiği/güncellendiği/aynı kaldığı ([ImportStats])
     */
    suspend fun smartImport(
        source: String,
        importedEntries: List<MediaEntry>,
        allowDelete: Boolean = true,
        keepAdultRows: Boolean = false,
        guardAgainstPartialPayload: Boolean = false,
        isFullPull: Boolean = false
    ): ImportStats = importMutex.withLock {
        smartImportInternal(
            source = source,
            importedEntries = importedEntries,
            allowDelete = allowDelete,
            keepAdultRows = keepAdultRows,
            guardAgainstPartialPayload = guardAgainstPartialPayload,
            isFullPull = isFullPull
        )
    }

    /**
     * Listenin kendisi: karşılaştırma, fark yazımı, silme kuralları.
     * **Yalnızca [smartImport] üzerinden çağrılır** (kütüphane yazımları sırayla çalışsın).
     */
    private suspend fun smartImportInternal(
        source: String,
        importedEntries: List<MediaEntry>,
        allowDelete: Boolean,
        keepAdultRows: Boolean,
        guardAgainstPartialPayload: Boolean,
        isFullPull: Boolean
    ): ImportStats {
        // PERF: yalnızca bu kaynağın satırları okunur (getAll + Kotlin süzgeci tüm
        // kütüphaneyi her yenilemede kopyalıyordu). Kaynak kapsamlı import için aynı sonuç.
        val existing = dao.getBySource(source)
        val pendingLocalIdsEarly = pendingLocalEntryIdsFor(source)
        // Yarış koruması: bu turun BAŞLANGIÇ anı. Bundan sonra yerelde değiştirilen satır
        // (kullanıcı ilerlemeyi güncelledi) taze uzak veriyle EZİLMEZ — okuma anındaki
        // bayat kopyayla yazmak, aynı saniyelerde yapılan düzenlemeyi kaybettiriyordu.
        val refreshStartedAtSec = System.currentTimeMillis() / 1000L

        if (importedEntries.isEmpty()) {
            // BOŞ YANIT KÜTÜPHANEYİ ASLA TEMİZLEMEZ.
            //
            // Eski davranış (allowDelete + boş liste → deleteBySource) tek bir 401/kota/kesik
            // yanıtta kullanıcının tüm kaynak listesini siliyordu. Boş liste gerçekten
            // "kullanıcı hepsini kaldırdı" anlamına gelse bile bunu yapmanın doğru yolu,
            // satır satır silmeyi üç kapıdan geçiren normal yoldur; boş yanıt ise kanıt
            // değildir. Bu turda hiçbir şey yazılmaz, defter de bozulmaz; kullanıcı
            // gerçekten listesini boşalttıysa bir sonraki (boş olmayan) yanıtta satırlar
            // normal kurallarla düşer.
            android.util.Log.w(
                "MediaEntryRepository",
                "$source: boş liste yanıtı yok sayıldı (yerelde ${existing.size} satır korunuyor)"
            )
            return ImportStats(source = source)
        }

        val updates = linkedMapOf<Int, MediaEntryEntity>()
        val inserts = mutableListOf<MediaEntry>()
        val matchedIds = mutableSetOf<Int>()
        // Çevrimdışıyken yapılmış ve henüz uzak sunucuya GÖNDERİLEMEMİŞ düzenlemeler
        // taze uzak veriyle ezilmez (outbox / pending-safe uzlaştırma). Aksi halde
        // çevrimdışı girilen bölüm/puan, bağlantı gelip liste çekilince sessizce geri
        // alınıyordu. Bu satırlar bir sonraki kuyruk boşaltmasında zaten gönderilecek.
        val pendingLocalIds = pendingLocalIdsEarly
        // Yerelde silinmiş ama DELETE'i henüz uzaklara ulaşmamış kayıtlar: liste yenilendiğinde
        // sunucu onları hâlâ döndürür, "yeni" sanılıp geri eklenmemeleri gerekir.
        val pendingDeleteKeys = deletionTombstones()
        var unchanged = 0
        var deferred = 0
        for (entry in importedEntries) {
            val imported = entry.copy(source = source)
            val matches = existing.filter {
                com.kitsugi.animelist.model.MediaIdentity.sameMedia(it.toDomain(), imported)
            }
            val match = when {
                matches.isEmpty() -> null
                matches.size == 1 -> matches.single()
                else -> {
                    // Birden fazla eşleşme durumunda en doğru kaydı tespit et (çökmeyi engelle)
                    val importedKeys = com.kitsugi.animelist.model.MediaIdentity.keys(imported)
                    val keyMatch = matches.firstOrNull { m ->
                        val mKeys = com.kitsugi.animelist.model.MediaIdentity.keys(m.toDomain())
                        importedKeys.intersect(mKeys).isNotEmpty()
                    }
                    val exactTitleMatch = matches.firstOrNull { m ->
                        val normLocal = com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(m.title)
                        val normImp = com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(imported.title)
                        normLocal.isNotBlank() && normLocal == normImp
                    }
                    val exactEngMatch = matches.firstOrNull { m ->
                        val engLocal = m.titleEnglish?.let { com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(it) }
                        val engImp = imported.titleEnglish?.let { com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(it) }
                        !engLocal.isNullOrBlank() && engLocal == engImp
                    }
                    keyMatch ?: exactTitleMatch ?: exactEngMatch ?: matches.maxByOrNull { it.updatedAt } ?: matches.first()
                }
            }
            if (match != null) {
                matchedIds.add(match.id)
                if (match.id in pendingLocalIds) {
                    // Yerel düzenleme henüz uzak sunucuya gitmedi → taze uzak veri onu ezmez.
                    deferred++
                    continue
                }
                if (match.updatedAt > refreshStartedAtSec) {
                    // Satır, bu yenileme BAŞLADIKTAN sonra yerelde değiştirildi (kullanıcı
                    // ilerleme/puan girdi). Bayat uzak kopyayla ezilmez; bir sonraki turda
                    // karşılaştırma zaten güncel yerel değerle yapılır.
                    deferred++
                    continue
                }
                // +18 işareti GERİ ALINMAZ: örneğin Simkl liste API'si yetişkin bayrağını
                // taşımıyor; güncel kayıt `isAdult = false` getirse bile AniList/MAL gibi
                // birinci elden bilgilendirilmiş satırın işaretini silmek bulanıklığı
                // kaybeder (rapor: "Simkl listesinde 18+ blur uygulanmıyor").
                // Çözülmüş başlıklar da GERİ ALINMAZ: Bangumi koleksiyon ucu yalnızca özgün
                // (Japonca/Çince) adı döndürdüğü için her yeniden içe aktarım, daha önce
                // tamamlanmış Latin/İngilizce adları CJK adla eziyordu — liste, başlık dili
                // İngilizce seçili olsa bile yeniden Japonca/Çinceye dönüyordu (v2.4.315).
                val candidate = preserveResolvedTitles(match, imported).copy(
                    id = match.id,
                    isAdult = imported.isAdult || match.isAdult
                ).toEntity()
                // `updatedAt` yerel defter zamanıdır; yalnızca o değiştiyse satır AYNI
                // sayılır (yoksa her yenileme tüm tabloyu "değişmiş" gösterirdi).
                if (candidate.samePayloadAs(match)) {
                    unchanged++
                } else {
                    updates[match.id] = candidate
                }
            } else {
                if (com.kitsugi.animelist.data.sync.ImportReconciler.isResurrection(
                        importedKeys = identityKeysFor(imported),
                        pendingDeleteKeys = pendingDeleteKeys
                    )
                ) {
                    // Kullanıcı bu kaydı sildi, silme işi kuyrukta bekliyor → diriltme.
                    deferred++
                    continue
                }
                val prior = inserts.indexOfFirst {
                    com.kitsugi.animelist.model.MediaIdentity.sameMedia(it, imported, allowTitle = false)
                }
                if (prior >= 0) inserts[prior] = imported else inserts.add(imported)
            }
        }
        // allowDelete=false must really mean NO deletion, including alleged title duplicates.
        // Silme kararı tek yerden verilir: eşleşmeyen + bekleyen düzenlemesi olmayan satırlar,
        // yarım yanıt korumasından geçtikten sonra. +18 süzgeçli kaynaklarda (MAL nsfw=false)
        // o satırlar hiç aday olmaz.
        // "Sunucuda en son ne vardı?" defteri: yalnızca orada görülmüş satırlar silinebilir.
        // Böylece yerelde eklenmiş ama sunucuya henüz gönderilememiş (ya da gönderimi
        // "atlandı" denen) kayıtlar, hiçbir süzgeç kombinasyonunda silinemez.
        val snapshotKeys = if (allowDelete) snapshotStore()?.read(source).orEmpty() else emptySet()
        val eligibleForDeletion = existing
            .filter { !(keepAdultRows && it.isAdult) }
            .filter { row ->
                com.kitsugi.animelist.data.sync.ImportReconciler.serverOriginConfirmed(
                    keys = com.kitsugi.animelist.model.MediaIdentity.keys(row.toDomain()),
                    previousSnapshot = snapshotKeys
                )
            }
            .map { it.id }
        val deletions = if (
            allowDelete && com.kitsugi.animelist.data.sync.ImportReconciler.coverageAllowsDeletion(
                existingCount = eligibleForDeletion.size,
                fetchedCount = importedEntries.size,
                enforce = guardAgainstPartialPayload
            )
        ) {
            com.kitsugi.animelist.data.sync.ImportReconciler.deletionCandidateIds(
                eligibleIds = eligibleForDeletion,
                matchedIds = matchedIds,
                protectedIds = pendingLocalIds
            )
        } else {
            emptyList()
        }

        // PERF: hiçbir şey değişmediyse transaction AÇILMAZ. Room yalnızca yazım olduğunda
        // invalidation yayar; boş bir updateAll bile tüm kütüphanenin yeniden
        // yayınlanmasına (ve Listem'in baştan hesaplanmasına) neden olurdu.
        // Import sürerken kullanıcı bir kaydı SİLMİŞ olabilir: tombstone'lar turun başında
        // okunduğu için o satır "yeni" sanılıp geri eklenebilirdi. Yazma anında tazelenir.
        val freshTombstones = if (inserts.isNotEmpty()) deletionTombstones() else pendingDeleteKeys
        val finalInserts = if (freshTombstones.isEmpty() || freshTombstones == pendingDeleteKeys) {
            inserts
        } else {
            inserts.filterNot { identityKeysFor(it).any { key -> key in freshTombstones } }
        }

        if (finalInserts.isNotEmpty() || updates.isNotEmpty() || deletions.isNotEmpty()) {
            // İçe aktarımda listeden DÜŞEN satırların bayat kuyruk işleri de düşürülür:
            // eski kopya sonradan uzaklara geri yazılsa import taze veriyi silerdi.
            if (deletions.isNotEmpty()) clearPendingJobsFor(entryIds = deletions)
            dao.smartImportTransaction(
                finalInserts.map { it.copy(id = 0).toEntity() },
                updates.values.toList(),
                deletions
            )
        }

        // Tam çekim başarıyla uygulandı → "sunucuda ne vardı" defteri tazelenir.
        // Yarım yanıt koruması devredeyken (silme yapılmayan tur) defter yine yazılır:
        // yanıt eksikse defter küçülür, bu yalnızca silmeyi GECİKTİRİR, veri kaybı yaratmaz.
        if (isFullPull) {
            snapshotStore()?.write(source, importedEntries)
        }

        val stats = ImportStats(
            source = source,
            fetched = importedEntries.size,
            added = finalInserts.size,
            updated = updates.size,
            unchanged = unchanged,
            deleted = deletions.size,
            deferred = deferred
        )

        if (sourceNeedsAdultRescan(source)) {
            // Liste yeni çekildi → +18 işaretlerini kimliklerden tamamla.
            val appContext = context ?: com.kitsugi.animelist.KitsugiApplication.getInstance()?.applicationContext
            if (appContext != null) {
                runCatching {
                    com.kitsugi.animelist.ui.screens.mylist.AdultFlagBackfillMigration.requestRescan(appContext)
                }
            }
        }

        return stats
    }

    /**
     * Kaynağın taze listesini, +18 işaretlerini KORUYARAK yerelle birleştirir.
     *
     * Eski `replaceSourcePreservingAdultFlags` sil-yeniden-yaz yapıyordu
     * (`deleteBySource` + `insertAll`): her arka plan yenilemesinde o kaynağın TÜM
     * satırları silinip yeni id'lerle geri yazılıyordu. Sonuçları:
     *  - Room iki kez invalidation yayıyor, Listem bir-iki kare boşalıp doluyordu,
     *  - satır id'leri değiştiği için bekleyen senkron işleri ve kaydırma konumu
     *    referansları anlamsızlaşıyordu,
     *  - okuma sırasında kütüphane yarım görünüyordu.
     * Artık fark-tabanlı [smartImport] kullanılır: yalnızca gerçekten değişen satırlar
     * yazılır, silme yapılmaz. Simkl/Shikimori/Bangumi liste uçları `adult` alanını
     * taşımadığı için eski +18 işaretleri kanonik kimlikler üzerinden geri yazılır.
     */
    suspend fun mergeSourcePreservingAdultFlags(source: String, entries: List<MediaEntry>): ImportStats {
        if (entries.isEmpty()) return ImportStats(source = source)

        // PERF: kaynak kapsamlı sorgu (tüm kütüphaneyi okuyup süzmek yerine).
        val preservedKeys = runCatching {
            dao.getBySource(source)
                .filter { it.isAdult }
                .map { it.toDomain() }
                .flatMap { com.kitsugi.animelist.model.MediaIdentity.keys(it) }
                .toSet()
        }.getOrDefault(emptySet())

        val enriched = if (preservedKeys.isEmpty()) entries else entries.map { entry ->
            if (entry.isAdult ||
                com.kitsugi.animelist.model.MediaIdentity.keys(entry).any { it in preservedKeys }
            ) entry.copy(isAdult = true) else entry
        }

        return smartImport(source = source, importedEntries = enriched, allowDelete = false)
    }

    /** Bir kaynağın yereldeki satır sayısı (yenileme kararları için hafif sorgu). */
    suspend fun countForSource(source: String): Int =
        runSyncCatching { dao.countBySource(source) }.getOrDefault(0)

    suspend fun updateAllDirect(entries: List<MediaEntry>) {
        if (entries.isEmpty()) return
        dao.updateAll(entries.map { it.toEntity() })
    }

    suspend fun update(
        entry: MediaEntry,
        syncExternal: Boolean = true,
        advancedScores: List<Double>? = null,
        skipBangumiWrite: Boolean = false
    ) {
        val entryWithTime = entry.copy(updatedAt = System.currentTimeMillis() / 1000L)
        dao.update(
            entryWithTime.toEntity()
        )

        // Kardeş satırlar (aynı yapımın diğer kaynak kopyaları) anında aynı değerleri görür.
        mirrorUserFieldsToSiblings(entryWithTime)

        if (syncExternal) {
            syncEntryIfPossible(entryWithTime, advancedScores, skipBangumiWrite)
        }
    }

    suspend fun deleteById(id: Int, syncExternal: Boolean = true) {
        val entry = dao.getById(id)?.toDomain()

        // Uzak silme zaten tüm kaynaklara yayılıyor; aynı yapımın yerel kardeş satırları da
        // silinir, yoksa silinen kayıt diğer kaynak sekmelerinde hayalet gibi kalıyordu.
        val siblingIds = entry?.let { findSiblingRows(it).map { sibling -> sibling.id } }.orEmpty()

        // Yerelde silinen satırların BEKLEYEN kuyruk işleri düşürülür: sonradan drain
        // edilen bayat bir UPDATE, az önce silinen kaydı uzaklarda GERİ DİRİTEBİLİRDİ
        // (özellikle DELETE ilk denemede başarılıysa kuyruğa DELETE de düşmezdi).
        clearPendingJobsFor(entryIds = listOf(id) + siblingIds)

        dao.deleteById(id)
        siblingIds.forEach { siblingId -> dao.deleteById(siblingId) }

        if (syncExternal && entry != null) {
            syncDeleteIfPossible(entry)
        }
    }

    suspend fun deleteBySource(source: String) {
        clearPendingJobsFor(source = source)
        dao.deleteBySource(source)
    }

    suspend fun deleteAll() {
        dao.deleteAll()
        // Tüm liste temizlendi: kuyrukta bayat UPDATE/DELETE işi kalırsa eski yerel
        // kopyalar uzaklara geri yazılmış (dirilmiş) olurdu — kullanıcı niyeti: hepsi gitsin.
        runSyncCatching { pendingSyncDao?.clearAll() }
        resetRefreshLedgers()
    }

    suspend fun replaceAll(entries: List<MediaEntry>) {
        val now = System.currentTimeMillis() / 1000L
        val entities = entries.map { it.copy(id = 0, updatedAt = it.updatedAt.takeIf { value -> value > 0 } ?: now).toEntity() }
        dao.replaceAllTransaction(entities)
        // Yedek geri yükleme_REPLACE: önceden başarısız olmuş bayat işler, TAZE veriyi
        // eski yerel kopyayla ezerek geri sürerdi — kuyruk temizlenir.
        runSyncCatching { pendingSyncDao?.clearAll() }
        // Yedek geri yükleme farklı bir kütüphane getirir: "son senkron ne zamandı" defteri
        // sıfırlanmazsa yeni veri "taze" sayılıp hiç çekilmeyebilirdi.
        resetRefreshLedgers()
    }

    /**
     * Yenileme defterlerini (tazelik + "sunucuda en son ne vardı" anlık görüntüsü) sıfırlar.
     * Kütüphane tamamen değiştiğinde (temizleme, yedek geri yükleme) çağrılır; aksi halde
     * yeni kütüphane eski defter yüzünden "zaten güncel" sanılıp hiç senkronlanmazdı.
     */
    private fun resetRefreshLedgers() {
        val ctx = context ?: com.kitsugi.animelist.KitsugiApplication.getInstance()?.applicationContext
            ?: return
        runSyncCatching {
            com.kitsugi.animelist.data.sync.SourceListSynchronizer.resetState(ctx, null)
        }
    }

    /**
     * Bekleyen kuyruğu boşaltır. AppViewModel.init'ten çağrılır.
     * @param freshBudget true ise yeniden deneme sayaçları önce sıfırlanır
     * (bağlantı geri geldiğinde çağrılır; 5 denemeyi aşmış işler kalıcı donmasın).
     * @return Başarıyla gönderilen işlem sayısı
     */
    suspend fun drainPendingQueue(freshBudget: Boolean = false): Int {
        val ctx = context ?: return 0
        val queueDao = pendingSyncDao ?: return 0
        return PendingSyncDrainer.drain(ctx, queueDao, freshBudget = freshBudget)
    }

    // ────────────────────────────────────────────────
    // Kardeş satır tutarlılığı (aynı yapım, farklı kaynak kopyaları)
    // ────────────────────────────────────────────────

    /** Bir satırın kimlik kümesi: kendi alanı + kalıcı çapraz kimlik önbelleği (ağ yok). */
    private fun resolvedIdsFor(appContext: Context, row: MediaEntry): com.kitsugi.animelist.data.auth.CrossProviderIds {
        val decoded = com.kitsugi.animelist.data.auth.CrossProviderIds.decode(
            storedId = row.malId,
            source = row.source,
            type = row.type,
            entrySimklId = row.simklId,
            entryTmdbId = row.tmdbId
        )
        val key = com.kitsugi.animelist.data.auth.CrossProviderIds.cacheKey(
            type = row.type,
            storedId = row.malId,
            title = row.title,
            titleEnglish = row.titleEnglish,
            year = row.year,
            source = row.source
        )
        val cached = com.kitsugi.animelist.data.auth.CrossProviderIdentityCache.read(appContext, key)?.ids
        return decoded.mergedWith(cached)
    }

    /**
     * [entry] ile aynı yapımı gösteren diğer yerel satırlar. Eşleşme yalnızca doğrulanmış
     * kimlik kesişimiyle olur (aynı tür + ortak MAL/AniList/Kitsu/Shikimori/Bangumi/Simkl
     * kimliği; film/dizide TMDB de). Başlık benzerliği asla kullanılmaz.
     */
    private suspend fun findSiblingRows(entry: MediaEntry): List<MediaEntry> {
        val appContext = context ?: return emptyList()
        if (entry.id <= 0) return emptyList()
        val entryIds = resolvedIdsFor(appContext, entry)
        if (entryIds.isEmpty && entry.aniListEntryId == null) return emptyList()
        val includeTmdb = entry.type == MediaType.Movie || entry.type == MediaType.TvShow
        return dao.getAll()
            .map { it.toDomain() }
            .filter { other ->
                other.id != entry.id && other.type == entry.type &&
                    (
                        (entry.aniListEntryId != null && entry.aniListEntryId == other.aniListEntryId) ||
                            resolvedIdsFor(appContext, other).sharesAnyIdWith(entryIds, includeTmdb = includeTmdb)
                        )
            }
    }

    /**
     * Kullanıcı değerlerini (durum/puan/ilerleme/tarih/not/etiket/favori/gizlilik/tekrar)
     * kardeş satırlara yerel olarak yansıtır. Kimlik/başlık/görsel alanlarına dokunulmaz;
     * uzak eşitleme ayrıca çalışır (bu yalnızca yerel önbellek tutarlılığıdır).
     */
    private suspend fun mirrorUserFieldsToSiblings(entry: MediaEntry) {
        runSyncCatching {
            findSiblingRows(entry).forEach { sibling ->
                dao.update(
                    sibling.copy(
                        status = entry.status,
                        score = entry.score,
                        progress = entry.progress,
                        volumeProgress = entry.volumeProgress,
                        startDate = entry.startDate,
                        endDate = entry.endDate,
                        notes = entry.notes,
                        tags = entry.tags,
                        isFavorite = entry.isFavorite,
                        isPrivate = entry.isPrivate,
                        isHiddenFromStatusLists = entry.isHiddenFromStatusLists,
                        priority = entry.priority,
                        isRepeating = entry.isRepeating,
                        repeatCount = entry.repeatCount,
                        repeatValue = entry.repeatValue,
                        updatedAt = entry.updatedAt
                    ).toEntity()
                )
            }
        }
    }

    // ────────────────────────────────────────────────
    // Private Sync Helpers
    // ────────────────────────────────────────────────

    private suspend fun syncEntryIfPossible(
        entry: MediaEntry,
        advancedScores: List<Double>? = null,
        skipBangumiWrite: Boolean = false
    ) {
        val appContext = context ?: return
        // Senkronizasyon için geçerli koşullar:
        // 1) malId var (MAL veya gerçek bir MAL ID'si olan AniList kaydı)
        // 2) aniListEntryId var (kaynak ne olursa olsun AniList liste kaydı)
        // 3) kaynak anilist (mediaId henüz bilinmese bile AniList'e push edilebilir)
        // 4) simklId var (Simkl kaydı)
        val hasAniListLink = entry.aniListEntryId != null || entry.source == "anilist"
        val hasMalLink = entry.malId != null
        val hasSimklLink = entry.simklId != null && entry.simklId > 0
        val hasTmdbLink = entry.tmdbId != null && entry.tmdbId > 0
        val hasTitle = entry.title.isNotBlank() || !entry.titleEnglish.isNullOrBlank()
        if (!hasAniListLink && !hasMalLink && !hasSimklLink && !hasTmdbLink && !hasTitle) return

        val result = runSyncCatching {
            ExternalListSyncManager.syncEntry(
                context = appContext,
                entry = entry,
                advancedScores = advancedScores,
                skipBangumiWrite = skipBangumiWrite
            )
        }

        val syncResult = result.getOrNull()
        // "Atlandı" (kimlik güvenle çözülemedi) başarısızlık sayılmaz: kaynak listeye göre
        // katalogda hiç karşılığı olmayan yapımlar kuyrukta birikip her açılışta tekrar denenirdi.
        val success = result.isSuccess && syncResult != null && syncResult.errors.isEmpty()

        if (success) {
            val messages = syncResult?.messages.orEmpty()
            if (messages.isNotEmpty()) removeFromQueueIfPresent(entry, "UPDATE")
            notifySyncResult(messages)

            // Dual-write: AniList'ten dönen list entry ID'yi yerel DB'ye kaydet
            val newAniListEntryId = syncResult?.aniListEntryId
            if (newAniListEntryId != null && newAniListEntryId != entry.aniListEntryId) {
                val updatedEntry = (dao.getById(entry.id)?.toDomain() ?: entry).copy(aniListEntryId = newAniListEntryId)
                dao.update(updatedEntry.toEntity())
            }

            if (messages.isNotEmpty()) drainPendingQueue()
        } else {
            enqueuePending("UPDATE", entry)
            val allErrors = syncResult?.errors.orEmpty().joinToString(" | ")
            val isAuthError = allErrors.contains("401") || allErrors.contains("token geçersiz") || allErrors.contains("Unauthorized")
            val displayMessage = if (isAuthError) {
                "🔑 Oturum süresi doldu, lütfen ayarlardan tekrar bağlanın"
            } else {
                "📵 Çevrimdışı kaydedildi, bağlantı gelince gönderilecek"
            }
            notifySyncResult(
                syncResult?.messages.orEmpty() + listOf(displayMessage) +
                    listOfNotNull(syncOutcomeNote(syncResult?.outcomes))
            )
        }
    }

    private suspend fun syncDeleteIfPossible(entry: MediaEntry) {
        val appContext = context ?: return
        // Silme için geçerli koşullar (syncEntryIfPossible ile aynı mantık)
        val hasAniListLink = entry.aniListEntryId != null || entry.source == "anilist"
        val hasMalLink = entry.malId != null
        val hasSimklLink = entry.simklId != null && entry.simklId > 0
        val hasTmdbLink = entry.tmdbId != null && entry.tmdbId > 0
        val hasTitle = entry.title.isNotBlank() || !entry.titleEnglish.isNullOrBlank()
        if (!hasAniListLink && !hasMalLink && !hasSimklLink && !hasTmdbLink && !hasTitle) return

        val result = runSyncCatching {
            ExternalListSyncManager.deleteEntry(
                context = appContext,
                entry = entry
            )
        }

        val syncResult = result.getOrNull()
        val success = result.isSuccess && syncResult != null && syncResult.errors.isEmpty()

        if (success) {
            val messages = syncResult?.messages.orEmpty()
            if (messages.isNotEmpty()) removeFromQueueIfPresent(entry, "DELETE")
            notifySyncResult(messages)
            if (messages.isNotEmpty()) drainPendingQueue()
        } else {
            enqueuePending("DELETE", entry)
            val allErrors = syncResult?.errors.orEmpty().joinToString(" | ")
            val isAuthError = allErrors.contains("401") || allErrors.contains("token geçersiz") || allErrors.contains("Unauthorized")
            val displayMessage = if (isAuthError) {
                "🔑 Oturum süresi doldu, lütfen ayarlardan tekrar bağlanın"
            } else {
                "📵 Çevrimdışı kaydedildi, bağlantı gelince gönderilecek"
            }
            notifySyncResult(
                syncResult?.messages.orEmpty() + listOf(displayMessage) +
                    listOfNotNull(syncOutcomeNote(syncResult?.outcomes))
            )
        }
    }

    private suspend fun enqueuePending(operation: String, entry: MediaEntry) {
        val queueDao = pendingSyncDao ?: return
        PendingSyncDrainer.enqueue(queueDao, operation, entry)
    }

    /**
     * Aynı yerel kayıt + kaynak + tür + işlem için bekleyen kaydı temizle
     * (örn. çevrimiçiyken yeniden güncelleme yapınca eski UPDATE'i temizle)
     */
    /**
     * Yerelde silinen/yeniden yazılan satırların bekleyen kuyruk işlerini düşürür.
     *
     * [PendingSyncDrainer] işi JSON'daki `entry.id`'yle eşleştirir; satır silindikten
     * sonra drain edilen bayat bir UPDATE, uzak kayda ESKİ yerel kopyayı geri yazar
     * (silineni diriltir ya da taze içe aktarımı geri sürer). Yalnızca hedef satırların
     * işleri silinir; başka kaynakların geçerli işleri kuyrukta kalır.
     */
    private suspend fun clearPendingJobsFor(
        entryIds: Collection<Int> = emptyList(),
        source: String? = null
    ) {
        val queueDao = pendingSyncDao ?: return
        if (entryIds.isEmpty() && source == null) return
        runSyncCatching {
            queueDao.getAll().forEach { job ->
                val matches = runSyncCatching {
                    val json = org.json.JSONObject(job.entryJson)
                    (entryIds.isNotEmpty() && json.optInt("id", -1) in entryIds) ||
                        (source != null && json.optString("source").equals(source, ignoreCase = true))
                }.getOrDefault(false)
                if (matches) queueDao.deleteById(job.id)
            }
        }
    }

    private var cachedSnapshotStore: com.kitsugi.animelist.data.sync.ServerSnapshotStore? = null

    private fun snapshotStore(): com.kitsugi.animelist.data.sync.ServerSnapshotStore? {
        val ctx = context ?: com.kitsugi.animelist.KitsugiApplication.getInstance()?.applicationContext
            ?: return null
        return cachedSnapshotStore ?: com.kitsugi.animelist.data.sync.ServerSnapshotStore(ctx)
            .also { cachedSnapshotStore = it }
    }

    /**
     * **Bekleyen DELETE işlerinin** kimlik anahtarları (tombstone kümesi).
     *
     * Silinen satır artık tabloda olmadığı için [pendingLocalEntryIdsFor] dirilişi
     * engelleyemez; işin JSON'undan kaydın kendisi çözülür.
     *
     * Kaynak süzgeci BİLİNÇLİ olarak yoktur: yerelden silinen kayıt çoğu zaman katalog
     * kaynaklıdır (`source = "jikan"`/`"tmdb"`) ama uzak kopyası AniList/MAL satırı olarak
     * geri gelebilir ("sildiğim şey geri geldi"). Bu yüzden anahtarlar **çapraz sağlayıcı**
     * kimliklerden üretilir ve tür önekiyle ayrılır (`Anime:mal:21`), böylece anime/manga
     * karışmaz.
     */
    private suspend fun deletionTombstones(): Set<String> {
        val queueDao = pendingSyncDao ?: return emptySet()
        return runSyncCatching {
            queueDao.getAll()
                .asSequence()
                .filter { it.operation.equals("DELETE", ignoreCase = true) }
                .mapNotNull { job ->
                    runSyncCatching {
                        // Kuyruktaki JSON tek bir nesnedir (yedek dosyası gibi `entries`
                        // dizisi değil) → ters dönüşüm için sarmalanır.
                        val item = org.json.JSONObject(job.entryJson)
                        val wrapped = org.json.JSONObject()
                            .put("schemaVersion", 2)
                            .put("entries", org.json.JSONArray().put(item))
                        MediaEntryBackup.importFromJson(wrapped.toString()).firstOrNull()
                    }.getOrNull()
                }
                .flatMap { identityKeysFor(it).asSequence() }
                .toSet()
        }.getOrDefault(emptySet())
    }

    /**
     * Bir kaydın **tüm sağlayıcı uzaylarındaki** kimlik anahtarları (tür önekli).
     *
     * [com.kitsugi.animelist.model.MediaIdentity.keys] yalnızca satırın kendi kaynak uzayını
     * görür; diriliş karşılaştırmasında silinen yerel satır ile sunucudan gelen satırın
     * kaynakları farklı olabilir. [com.kitsugi.animelist.data.auth.CrossProviderIds.decode]
     * depolanan kimliği (offset'li stable ID dahil) her sağlayıcıya açar.
     */
    private fun identityKeysFor(entry: MediaEntry): Set<String> {
        val prefix = entry.type.name
        val decoded = com.kitsugi.animelist.data.auth.CrossProviderIds.decode(
            storedId = entry.malId,
            source = entry.source,
            type = entry.type,
            entrySimklId = entry.simklId,
            entryTmdbId = entry.tmdbId
        )
        // Kalıcı çapraz kimlik önbelleği (ağ yok): silinen katalog satırı ile sunucudan gelen
        // AniList/MAL satırı çoğu zaman yalnızca bu köprü üzerinden eşleşir.
        val ids = context?.let { ctx -> decoded.mergedWith(resolvedIdsFor(ctx, entry)) } ?: decoded
        return buildSet {
            addAll(com.kitsugi.animelist.model.MediaIdentity.keys(entry))
            ids.malId?.let { add("$prefix:mal:$it") }
            ids.aniListId?.let { add("$prefix:anilist:$it") }
            ids.kitsuId?.let { add("$prefix:kitsu:$it") }
            ids.shikimoriId?.let { add("$prefix:shikimori:$it") }
            ids.bangumiSubjectId?.let { add("$prefix:bangumi:$it") }
            ids.tmdbId?.let { add("$prefix:tmdb:$it") }
            ids.simklId?.let { add("$prefix:simkl:$it") }
            entry.aniListEntryId?.takeIf { it > 0 }?.let { add("$prefix:anilist-entry:$it") }
        }
    }

    /**
     * Bekleyen senkron kuyruğundaki (henüz uzak sunucuya gönderilememiş) yerel
     * satırların id'leri. Bu satırlar liste içe aktarımında EZİLMEZ.
     */
    private suspend fun pendingLocalEntryIdsFor(source: String): Set<Int> {
        val queueDao = pendingSyncDao ?: return emptySet()
        return runSyncCatching {
            queueDao.getAll().mapNotNull { job ->
                val json = org.json.JSONObject(job.entryJson)
                if (!json.optString("source").equals(source, ignoreCase = true)) {
                    null
                } else {
                    json.optInt("id", 0).takeIf { it > 0 }
                }
            }.toSet()
        }.getOrDefault(emptySet())
    }

    private suspend fun removeFromQueueIfPresent(entry: MediaEntry, operation: String) {
        val queueDao = pendingSyncDao ?: return
        val all = queueDao.getAll()
        all.filter { it.operation == operation }.forEach { pending ->
            // Bare MAL IDs are not unique across anime, manga, or platform libraries.
            runSyncCatching {
                val json = org.json.JSONObject(pending.entryJson)
                val sameLocalRecord = entry.id > 0 && json.optInt("id", 0) == entry.id &&
                    json.optString("type") == entry.type.name && json.optString("source") == entry.source
                if (sameLocalRecord) queueDao.deleteById(pending.id)
            }
        }
    }

    /**
     * Hata durumunda hangi kaynağın neden eşitlenemediğini tek satırda özetler
     * (ör. "AniList: eşitlendi · Kitsu: kimlik çözülemedi") — kullanıcı "neden yok"
     * sorusunu raporu açmadan görebilsin diye.
     */
    private fun syncOutcomeNote(outcomes: Map<String, String>?): String? {
        if (outcomes.isNullOrEmpty()) return null
        val note = com.kitsugi.animelist.data.auth.CrossSyncPolicy.summarize(outcomes)
        return note.takeIf { it.isNotBlank() }
    }

    private fun notifySyncResult(messages: List<String>) {
        val message = messages
            .filter { it.isNotBlank() }
            .joinToString(" • ")

        if (message.isNotBlank()) {
            onExternalSyncMessage?.invoke(message)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// +18 (NSFW blur) işaretleri — kaynaklar arası tutarlılık
// ─────────────────────────────────────────────────────────────────────────────

/**
 * +18 işaretini KAYNAKLAR ARASI yayar.
 *
 * `isAdult` satır bazlı tutulur; oysa +18 bir YAPIM özelliğidir. AniList satırı
 * `isAdult = true` derken Simkl/Shikimori/Bangumi satırı aynı yapım için `false`
 * olabilir (bu kaynakların liste API'leri `adult` alanını taşımıyor). Sonuç: blur
 * ayarı açıkken kullanıcı aynı yapımı bir sekmede bulanık, diğerinde çıplak
 * görüyordu — rapor: "Simkl listem sayfası üzerinde kutucuklara 18+ blur
 * uygulanmıyor".
 *
 * Bu yüzden kanonik kimlikleri ([com.kitsugi.animelist.model.MediaIdentity.keys])
 * eşleşen tüm satırlar +18 sayılır. Yalnızca false→true yönünde çalışır; asla
 * işareti kaldırmaz ve kimliği çakışan (farklı yapım) satırlara dokunmaz.
 */
internal fun List<MediaEntryEntity>.withCrossSourceAdultFlags(): List<MediaEntry> {
    if (isEmpty()) return emptyList()
    val domains = map { it.toDomain() }
    // PERF: kimlik anahtarlarını satır başına BİR kez hesapla. Eski sürüm yetişkin satırlar
    // için bir, yetişkin bayrağı eksik her satır için iki kez hesaplıyordu (bayrak
    // yayarken + kontrol ederken); 2000 satırlık kütüphanede emisyon başına binlerce
    // gereksiz MediaIdentity.keys çağrısı anlamına geliyordu.
    if (domains.none { it.isAdult }) return domains
    val keysByRow = domains.map { entry -> com.kitsugi.animelist.model.MediaIdentity.keys(entry) }
    val adultKeys = HashSet<String>()
    domains.forEachIndexed { index, entry ->
        if (entry.isAdult) adultKeys += keysByRow[index]
    }
    if (adultKeys.isEmpty()) return domains
    return domains.mapIndexed { index, entry ->
        if (entry.isAdult) entry
        else if (keysByRow[index].any { it in adultKeys }) {
            entry.copy(isAdult = true)
        } else entry
    }
}

/**
 * İçe aktarım güncellemesinde **çözülmüş başlıkları korur** (`smartImport` güncelleme kolu).
 *
 * Başlık dili sözleşmesi kütüphane satırında üç varyantla yaşar: `title` (romaji/Latin ana
 * ad), `titleEnglish`, `titleJapanese` (özgün ad). Bazı liste uçları her seferinde yalnızca
 * özgün adı getirir — Bangumi koleksiyon ucu (`name` / `name_cn`, infobox yok) bunun tipik
 * örneğidir ve İngilizce/romaji adlar sonradan
 * [com.kitsugi.animelist.data.remote.BangumiLibraryTitleHydrator] ile tamamlanır.
 * İçe aktarım satırı körü körüne yazılırsa tamamlanmış her ad bir sonraki senkronda CJK ada
 * geri döner ve seçili başlık dili (örn. İngilizce) kalıcı olarak yok sayılırdı.
 *
 * Kurallar (yalnızca yükseltme var, geriletme yok):
 * - `title`: gelen ad CJK iken mevcut ad Latin ise mevcut korunur; gelen Latin ad her
 *   zaman kazanır (kaynak düzeltmesi olabilir); boş gelen ad mevcutla değiştirilmez.
 * - `titleEnglish`: Latin bir İngilizce ad gelmedikçe mevcut korunur.
 * - `titleJapanese`: gelen özgün ad önceliklidir; yoksa mevcut, o da yoksa gelen CJK ana
 *   başlık özgün ad olarak saklanır (sonraki çözümlemelerin arama terimi kaybolmaz).
 */
internal fun preserveResolvedTitles(existing: MediaEntryEntity, imported: MediaEntry): MediaEntry {
    val importedTitle = imported.title.trim()
    val existingTitle = existing.title.trim()
    val title = when {
        importedTitle.isEmpty() -> existing.title
        PreferenceHelpers.hasCjkCharacters(importedTitle) &&
            PreferenceHelpers.isLatinText(existingTitle) &&
            !existingTitle.startsWith("Bangumi #") -> existing.title
        else -> imported.title
    }
    val titleEnglish = imported.titleEnglish?.takeIf { PreferenceHelpers.isLatinText(it) }
        ?: existing.titleEnglish
        ?: imported.titleEnglish
    val titleJapanese = imported.titleJapanese?.takeIf { it.isNotBlank() }
        ?: existing.titleJapanese?.takeIf { it.isNotBlank() }
        ?: importedTitle.takeIf { PreferenceHelpers.hasCjkCharacters(it) }
    return imported.copy(
        title = title,
        titleEnglish = titleEnglish,
        titleJapanese = titleJapanese
    )
}

/**
 * Bu kaynakların liste API'leri +18 bilgisini taşımaz; içe aktarımdan sonra
 * kimlik üzerinden yeniden sorgulanmaları gerekir
 * (bkz. [com.kitsugi.animelist.ui.screens.mylist.AdultFlagBackfillMigration]).
 */
internal fun sourceNeedsAdultRescan(source: String): Boolean =
    source.equals("simkl", ignoreCase = true) ||
        source.equals("shikimori", ignoreCase = true) ||
        source.equals("bangumi", ignoreCase = true)

/**
 * İki satırın **içerik olarak** aynı olup olmadığını söyler.
 *
 * `updatedAt` bilinçli olarak dışlanır: yerel defter zamanı, uzak listenin her
 * çekilişinde değişebilir; bunu "değişiklik" saymak her yenilemede tüm tabloyu
 * yeniden yazdırırdı. Diğer tüm alanlar (yeni alanlar dâhil) data class eşitliğiyle
 * karşılaştırılır, böylece ileride eklenecek alanlar da otomatik kapsanır.
 */
internal fun MediaEntryEntity.samePayloadAs(other: MediaEntryEntity): Boolean =
    copy(updatedAt = 0L) == other.copy(updatedAt = 0L)
