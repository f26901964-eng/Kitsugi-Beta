package com.kitsugi.animelist.ui.screens.fullscreen.runtime

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.local.toDomain
import com.kitsugi.animelist.data.local.toEntity
import com.kitsugi.animelist.model.EntryAutoRules
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.WatchStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * T1.6: PlayerScrobbleController
 *
 * AniList / MAL izleme ilerlemesini oynatıcıdan alıp uzak servislere iletir.
 * Dağınık VM kodundan buraya taşındı; PlayerRuntimeOrchestrator ile yönetilir.
 *
 * Scrobble mantığı:
 *  - Bölüm %80 izlendiğinde (veya 20dk sonra) tamamlandı işareti
 *  - Her 30sn'de bir pozisyon backend'e gönderilmez; sadece bölüm tamamı kaydedilir
 *  - Offline scrob kuyruğu için SimklSyncRepository.outbox benzer bir yapı
 */
class PlayerScrobbleController(
    private val scope: CoroutineScope,
    private val context: Context,
    private val getAniListToken: () -> String?,
    private val onScrobbleSuccess: (mediaId: Int, episode: Int) -> Unit = { _, _ -> },
    private val onScrobbleFail: (reason: String) -> Unit = {},
    private val scrobbleThreshold: Float = com.kitsugi.animelist.core.player.ScrobblePolicy.DEFAULT_THRESHOLD
) {
    private val TAG = "ScrobbleController"

    // Scrobble durumu
    private var currentMediaEntry: MediaEntry? = null
    private var currentEpisode: Int = 0
    private var durationMs: Long = 0L
    private var scrobbled: Boolean = false
    private var scrobbleJob: Job? = null

    /** Mutable threshold so callers can update it at runtime (Aniyomi parity). */
    @Volatile
    var threshold: Float = scrobbleThreshold
        set(value) { field = value.coerceIn(0f, 1f) }

    /** Yeni bölüm başladığında çağrılır */
    fun onEpisodeStarted(entry: MediaEntry, episode: Int, durationMs: Long) {
        scrobbleJob?.cancel()
        currentMediaEntry = entry
        currentEpisode = episode
        this.durationMs = durationMs
        scrobbled = false
        Log.d(TAG, "Episode started: mediaId=${entry.id} ep=$episode dur=${durationMs}ms")
    }

    /**
     * Pozisyon güncellemesinde çağrılır.
     * @param positionMs Anlık pozisyon
     * @param durationMs Toplam bölüm süresi (ms)
     */
    fun onPositionUpdate(positionMs: Long, durationMs: Long = 0L) {
        if (durationMs > 0L) {
            this.durationMs = durationMs
        }
        if (scrobbled || this.durationMs <= 0L) return
        val progress = positionMs.toFloat() / this.durationMs.toFloat()
        // Aniyomi varsayılanı %85; kullanıcı tercihi threshold ile değiştirilebilir.
        if (com.kitsugi.animelist.core.player.ScrobblePolicy.shouldScrobble(progress, threshold)) {
            scrobble()
        }
    }

    /** Manuel olarak scrobble tetikle (bölüm "Tamamlandı" butonu) */
    fun forceScrobble() = scrobble()

    private fun scrobble() {
        if (scrobbled) return
        scrobbled = true
        val entry = currentMediaEntry ?: return

        scrobbleJob = scope.launch(Dispatchers.IO) {
            try {
                val nextStatus = if (currentEpisode >= (entry.total ?: Int.MAX_VALUE)) {
                    WatchStatus.Completed
                } else {
                    WatchStatus.Watching
                }
                // Tarih otomasyonu: "izlemeye başla" → başlangıç bugün, "tamamla" → bitiş bugün.
                // (Diğer ekleme/düzenleme yollarıyla AYNI kural motoru.)
                val auto = EntryAutoRules.resolveOnStatusChange(
                    status = nextStatus,
                    startDate = entry.startDate,
                    endDate = entry.endDate,
                    progress = maxOf(entry.progress, currentEpisode),
                    total = entry.total,
                    statusChanged = nextStatus != entry.status
                )
                val updatedEntry = entry.copy(
                    progress = auto.progress,
                    status = nextStatus,
                    startDate = auto.startDate,
                    endDate = auto.endDate
                )
                // ── Yerel satır GÜNCELLENİR ───────────────────────────────────────────
                // Eski kod yalnız uzak hedeflere yazıyor, DB'yi güncellemiyordu: liste eski
                // bölümü göstermeye devam ediyor ve sonraki bir tam yazım ESKİ ilerlemeyi
                // tüm kaynaklara geri yazabiliyordu (ilerleme geri düşüyordu).
                runCatching {
                    val dao = KitsugiDatabase.getDatabase(context).mediaEntryDao()
                    val stored = dao.getById(entry.id)?.toDomain()
                    if (stored != null) {
                        val mergedProgress = maxOf(stored.progress, updatedEntry.progress)
                        // Tamamlanmış / tekrar izlenen kayıt oynatıcı yüzünden geri düşmesin.
                        val mergedStatus = when (stored.status) {
                            WatchStatus.Completed, WatchStatus.Repeating -> stored.status
                            else -> updatedEntry.status
                        }
                        dao.update(
                            stored.copy(
                                progress = mergedProgress,
                                status = mergedStatus,
                                startDate = updatedEntry.startDate ?: stored.startDate,
                                endDate = updatedEntry.endDate ?: stored.endDate,
                                updatedAt = System.currentTimeMillis() / 1000L
                            ).toEntity()
                        )
                    }
                }

                // ── İlerleme artık YALNIZCA AniList'e değil, BAĞLI TÜM KAYNAKLARA yazılır ──
                // Eski kod tek hedefe (AniList) yazıyordu ve AniList oturumu yoksa hiçbir
                // şey yapmıyordu: MAL/Kitsu/Shikimori/Bangumi/Simkl'de izleme ilerlemesi
                // oynatıcıdan ASLA güncellenmiyordu. Aynı çapraz eşitleme hattı (eşzamanlı,
                // kaynak başına zaman aşımı, kimlik kapısı) burada da kullanılır.
                val scrobbleFields = buildSet {
                    add(com.kitsugi.animelist.data.auth.SyncField.PROGRESS)
                    add(com.kitsugi.animelist.data.auth.SyncField.STATUS)
                    // Tarih yalnızca bu turda DOLDURULDUYSA gönderilir (boş alan ezmez).
                    if (updatedEntry.startDate != entry.startDate) {
                        add(com.kitsugi.animelist.data.auth.SyncField.START_DATE)
                    }
                    if (updatedEntry.endDate != entry.endDate) {
                        add(com.kitsugi.animelist.data.auth.SyncField.END_DATE)
                    }
                }
                val outcome = com.kitsugi.animelist.data.auth.ExternalListSyncManager.syncEntry(
                    context = context,
                    entry = updatedEntry,
                    // Oynatıcı puan/not/etiket/tarihe (doldurduğu tarih hariç) dokunmaz.
                    fields = scrobbleFields
                )
                val aniListEntryId = outcome.aniListEntryId
                if (aniListEntryId != null) {
                    Log.d(TAG, "Scrobble SUCCESS: aniListId=$aniListEntryId ep=$currentEpisode")
                    onScrobbleSuccess(aniListEntryId, currentEpisode)
                } else if (outcome.anySynced) {
                    Log.d(TAG, "Scrobble SUCCESS (diğer kaynaklar): ${outcome.outcomes} ep=$currentEpisode")
                    onScrobbleSuccess(entry.id, currentEpisode)
                } else {
                    Log.w(TAG, "Scrobble hiçbir kaynağa yazılamadı: ${outcome.outcomes} ${outcome.skipped}")
                    onScrobbleFail(outcome.errors.firstOrNull() ?: "Hiçbir kaynak eşitlenmedi")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Scrobble failed", e)
                scrobbled = false // Tekrar dene
                onScrobbleFail(e.message ?: "Unknown error")
            }
        }
    }

    /** Controller temizle (VM.onCleared) */
    fun clear() {
        scrobbleJob?.cancel()
        currentMediaEntry = null
        scrobbled = false
    }
}
