package com.kitsugi.animelist.ui.screens.explore

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.KitsugiApplication
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.remote.AiringEntry
import com.kitsugi.animelist.data.remote.KitsugiAiringCalendarClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Calendar

class KitsugiAiringCalendarViewModel : ViewModel() {

    private val client = KitsugiAiringCalendarClient()

    /** Gün → yayın listesi. Key = Calendar.DAY_OF_WEEK (1=Paz, 2=Pzt, …, 7=Cmt) */
    val weekSchedule = mutableStateMapOf<Int, List<AiringEntry>>()

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** Prevent an empty-state flash before the first LaunchedEffect starts fetching. */
    var hasLoaded by mutableStateOf(false)
        private set

    /** Seçili gün (Calendar.DAY_OF_WEEK). Varsayılan: bugün */
    var selectedDay by mutableIntStateOf(Calendar.getInstance().get(Calendar.DAY_OF_WEEK))
        private set

    private var lastLoadedSource: String? = null

    /** Son BAŞARILI yüklemenin zamanı — TTL kontrolü için (2026-10-21). */
    private var lastLoadedAtMs: Long = 0L

    /**
     * Uçuştaki önceki isteği iptal etmek için Job referansı.
     * Race condition önleme: init yerine LaunchedEffect(preferredSource) başlatır.
     * init { loadSchedule() } KASTEN KALDIRILDI:
     *  - init null/AniList isteği başlatır
     *  - LaunchedEffect aynı anda TMDB isteği başlatır
     *  - Hangisi geç biterse öncekinin verisini siler → yanlış veri gösterilir
     * Çözüm: Her platform kendi ViewModel key'ine sahip, LaunchedEffect yönetir.
     */
    private var loadJob: Job? = null

    fun loadSchedule(accessToken: String? = null, preferredSource: String? = null, force: Boolean = false) {
        // TTL (2026-10-21): Eskiden veri bir kez geldiyse ekran her açılışta onu
        // kullanıyordu; gün/hafta değişince bayat takvim gösteriliyordu. Artık aynı
        // kaynak için 10 dakika içinde tekrar açılışta ağa çıkılmaz, sonrasında
        // arka planda tazelenir (eski veri ekranda kalır, boş ekran görünmez).
        val freshEnough = weekSchedule.isNotEmpty() &&
            (System.currentTimeMillis() - lastLoadedAtMs) < SCHEDULE_TTL_MS
        if (!force && lastLoadedSource == preferredSource && freshEnough) return

        // Önceki uçuştaki isteği iptal et — race condition önlemek için kritik
        loadJob?.cancel()

        lastLoadedSource = preferredSource
        loadJob = viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val schedule = client.fetchWeeklySchedule(accessToken, preferredSource)
                weekSchedule.clear()
                weekSchedule.putAll(schedule)
                lastLoadedAtMs = System.currentTimeMillis()
                hasLoaded = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                hasLoaded = true
                val detail = e.message.orEmpty()
                val context = KitsugiApplication.getInstance()
                errorMessage = if (detail.isBlank()) {
                    context?.getString(R.string.explore_calendar_load_failed_title)
                        ?: "Calendar could not be loaded"
                } else {
                    context?.getString(R.string.explore_calendar_load_failed, detail)
                        ?: "Calendar could not be loaded: $detail"
                }
            } finally {
                isLoading = false
            }
        }
    }

    fun selectDay(day: Int) {
        selectedDay = day
    }

    /** Seçili gün için yayın listesi — boş liste yerine emptyList() döner */
    fun entriesForSelectedDay(): List<AiringEntry> =
        weekSchedule[selectedDay] ?: emptyList()

    /** Haftada yayın olan gün sayısı (rozet için) */
    fun totalAiringCount(): Int = weekSchedule.values.sumOf { it.size }

    private companion object {
        /** Başarılı bir takvim 10 dakika boyunca taze sayılır (ekran yeniden açılışında). */
        const val SCHEDULE_TTL_MS = 10 * 60 * 1000L
    }
}
