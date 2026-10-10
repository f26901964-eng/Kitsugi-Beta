package com.kitsugi.animelist.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kitsugi.animelist.data.model.WatchHistoryEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * İzleme geçmişinin kalıcı deposu (SharedPreferences + Gson).
 *
 * ## Neden debounce
 * Oynatıcı ilerlemeyi saniyede birkaç kez bildiriyor; her bildirimde 200 kayıtlık listenin
 * tamamı serialize edilip SharedPreferences XML'i baştan yazılıyordu. Zayıf cihazlarda bu,
 * tam ekran oynatma sırasında mikro takılmalara ve gereksiz disk yazımına yol açıyordu.
 * [saveHistoryDebounced] aynı pencere içindeki yazmaları tek bir G/Ç işlemine indirger
 * (Gson serialize + dosya yazımı tamamen IO dispatcher'ında).
 *
 * Kritik anlar için [saveHistory] (anında) ve bekleyeni zorlayan [flush] korunmuştur.
 */
class WatchHistoryStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("watch_history", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var pendingJob: Job? = null
    private var pendingEntries: List<WatchHistoryEntry>? = null

    fun getHistory(): List<WatchHistoryEntry> {
        val json = prefs.getString("history_list", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<WatchHistoryEntry>>() {}.type
            gson.fromJson<List<WatchHistoryEntry>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Anında (senkron serialize, IO'da yazma) kaydeder. */
    fun saveHistory(entries: List<WatchHistoryEntry>) {
        val snapshot = entries.toList()
        saveScope.launch { writeNow(snapshot) }
    }

    /**
     * Yazmayı [debounceMs] boyunca toplulaştırır. Aynı pencere içinde gelen son liste kazanır.
     */
    fun saveHistoryDebounced(entries: List<WatchHistoryEntry>, debounceMs: Long = DEBOUNCE_MS) {
        val snapshot = entries.toList()
        synchronized(lock) {
            pendingEntries = snapshot
            if (pendingJob?.isActive == true) return
            pendingJob = saveScope.launch {
                delay(debounceMs)
                val toWrite = synchronized(lock) {
                    pendingEntries.also { pendingEntries = null }
                } ?: return@launch
                writeNow(toWrite)
            }
        }
    }

    /** Bekleyen toplulaştırılmış yazmayı hemen uygular (oynatıcıdan çıkış, arka plana geçiş). */
    fun flush() {
        val toWrite = synchronized(lock) {
            pendingJob?.cancel()
            pendingJob = null
            pendingEntries.also { pendingEntries = null }
        } ?: return
        saveScope.launch { writeNow(toWrite) }
    }

    private fun writeNow(entries: List<WatchHistoryEntry>) {
        try {
            val json = gson.toJson(entries)
            prefs.edit().putString("history_list", json).apply()
        } catch (_: Exception) {
            // Geçmiş yazılamazsa sessizce yut: oynatma akışını kesmemeli.
        }
    }

    companion object {
        /** Oynatıcı ilerleme bildirimleri için toplulaştırma penceresi. */
        const val DEBOUNCE_MS = 2500L
    }
}
