package com.kitsugi.animelist.data.local

import android.content.Context
import com.kitsugi.animelist.data.model.WatchHistoryEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * İzleme geçmişi yöneticisi.
 *
 * ## Performans notları (2026-10-11)
 *  1. **Açılış:** `init()` daha önce `Gson.fromJson()` ile tüm geçmişi Application.onCreate
 *     içinde (ana iş parçacığında, senkron) ayrıştırıyordu. Artık yalnızca depo bağlanır,
 *     okuma IO'da yapılır; UI akışı doldukça kendini günceller.
 *  2. **Yazma:** Oynatıcı ilerlemesi her tikte diske yazılıyordu (tüm liste yeniden serialize
 *     edilip SharedPreferences XML'i baştan oluşturuluyordu). [updateProgress] artık
 *     [WatchHistoryStore.saveHistoryDebounced] kullanır; son konum 2,5 sn'de bir toplu yazılır.
 *     Bölüm başlangıcı gibi kritik olaylarda ([record], [remove], [clearAll]) anında yazılır.
 */
object WatchHistoryManager {

    private const val MAX_ENTRIES = 200

    private lateinit var store: WatchHistoryStore
    private val _history = MutableStateFlow<List<WatchHistoryEntry>>(emptyList())
    val history: StateFlow<List<WatchHistoryEntry>> = _history.asStateFlow()

    /** Diskteki listeyi ana iş parçacığını bloklamadan okuyabilmek için. */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Disk okuması tamamlanmadan yerel bir yazma olduysa işaretlenir (bkz. init). */
    @Volatile
    private var localWriteBeforeLoad = false

    fun init(context: Context) {
        store = WatchHistoryStore(context.applicationContext)
        ioScope.launch {
            val loaded = store.getHistory()
            if (localWriteBeforeLoad) {
                // Okuma sürerken bir kayıt eklendiyse diskteki eski listeyi eklemek yerine birleştir;
                // aksi hâlde o sırada yazılan kayıt geçmişi tek elemana düşürürdü (veri kaybı).
                val merged = merge(_history.value, loaded)
                _history.value = merged
                store.saveHistory(merged)
            } else {
                _history.value = loaded
            }
        }
    }

    /**
     * Bir bölümü izleme geçmişine ekler veya mevcut kaydı günceller.
     * Aynı animeId + episode kombinasyonu için en güncel kayıt tutulur.
     */
    fun record(entry: WatchHistoryEntry) {
        localWriteBeforeLoad = true
        val current = _history.value.toMutableList()
        // Var olan kaydı sil (aynı bölüm)
        current.removeAll { it.animeId == entry.animeId && it.episode == entry.episode }
        // Başa ekle (en yeni en üstte)
        current.add(0, entry)
        // Max kayıt sınırı
        val trimmed = current.take(MAX_ENTRIES)
        _history.value = trimmed
        store.saveHistory(trimmed)
    }

    /**
     * Güncel izleme konumunu ve süresini kaydeder.
     *
     * Oynatıcı bunu saniyede birkaç kez çağırabildiği için yazma **toplulaştırılır** (debounce).
     */
    fun updateProgress(animeId: String, episode: Int, positionMs: Long, durationMs: Long) {
        localWriteBeforeLoad = true
        val current = _history.value.toMutableList()
        val index = current.indexOfFirst { it.animeId == animeId && it.episode == episode }
        if (index != -1) {
            val oldEntry = current[index]
            val updatedEntry = oldEntry.copy(
                positionMs = positionMs,
                durationMs = durationMs,
                watchedAtMs = System.currentTimeMillis()
            )
            current.removeAt(index)
            current.add(0, updatedEntry)
            _history.value = current
            store.saveHistoryDebounced(current)
        }
    }

    /**
     * Çözümlenmiş stream URL'sini ve başlıklarını geçmiş kaydına yazar.
     * Bir sonraki seferde geçmişten direkt oynatma mümkün olur.
     */
    fun updateStreamUrl(
        animeId: String,
        episode: Int,
        streamUrl: String,
        streamHeaders: Map<String, String>?
    ) {
        localWriteBeforeLoad = true
        val current = _history.value.toMutableList()
        val index = current.indexOfFirst { it.animeId == animeId && it.episode == episode }
        if (index != -1) {
            val updated = current[index].copy(
                streamUrl = streamUrl,
                streamHeaders = streamHeaders
            )
            current[index] = updated
            _history.value = current
            store.saveHistory(current)
        }
    }

    /**
     * Bekleyen toplulaştırılmış yazmayı hemen diske indirir.
     * Oynatıcıdan çıkarken / süreç arka plana geçerken çağrılabilir.
     */
    fun flush() {
        if (::store.isInitialized) store.flush()
    }

    /**
     * Belirli bir kaydı sil.
     */
    fun remove(animeId: String, episode: Int) {
        localWriteBeforeLoad = true
        val updated = _history.value.filterNot { it.animeId == animeId && it.episode == episode }
        _history.value = updated
        store.saveHistory(updated)
    }

    /**
     * Tüm geçmişi temizle.
     */
    fun clearAll() {
        localWriteBeforeLoad = true
        _history.value = emptyList()
        store.saveHistory(emptyList())
    }

    /**
     * [primary] (bellekteki güncel liste) önce, [secondary] (diskten okunan) içindeki çakışmayan
     * kayıtlar sonra gelecek şekilde birleştirir ve [MAX_ENTRIES] ile sınırlar.
     */
    private fun merge(
        primary: List<WatchHistoryEntry>,
        secondary: List<WatchHistoryEntry>
    ): List<WatchHistoryEntry> {
        val seen = primary.mapTo(HashSet()) { it.animeId to it.episode }
        val extra = secondary.filterNot { seen.contains(it.animeId to it.episode) }
        return (primary + extra).take(MAX_ENTRIES)
    }
}
