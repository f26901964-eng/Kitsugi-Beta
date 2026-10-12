package com.kitsugi.animelist.data.fandom

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fandom Wiki tercihleri (Ayarlar → Tercihler → Liste ve puan → Fandom Wiki).
 *
 * Bağımsız bir SharedPreferences dosyasında tutulur; böylece ayarlar ekranındaki uzun
 * parametre zincirine dokunmadan veri katmanı ve arayüz aynı değeri okur.
 */
object FandomWikiPrefs {

    private const val PREFS_NAME = "kitsugi_fandom_wiki"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_NAME_FALLBACK = "name_fallback"
    private const val KEY_SPOILER_GUARD = "spoiler_guard"
    private const val KEY_PREFER_TURKISH = "prefer_turkish"

    data class State(
        /** Wiki sekmesi ve tüm Fandom istekleri. */
        val enabled: Boolean = true,
        /** Kimlikle bulunamazsa son çare isimle eşleştirme (kanıtla doğrulanır). */
        val nameFallback: Boolean = true,
        /** Durum alanı ve hikâye bölümleri dokunana kadar gizli. */
        val spoilerGuard: Boolean = true,
        /** Sayfanın Türkçe wiki'de karşılığı varsa o gösterilir. */
        val preferTurkish: Boolean = true
    )

    private val _state = MutableStateFlow(State())
    val stateFlow: StateFlow<State> = _state.asStateFlow()

    @Volatile
    private var loaded = false

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val prefs = prefs(context)
            _state.value = State(
                enabled = prefs.getBoolean(KEY_ENABLED, true),
                nameFallback = prefs.getBoolean(KEY_NAME_FALLBACK, true),
                spoilerGuard = prefs.getBoolean(KEY_SPOILER_GUARD, true),
                preferTurkish = prefs.getBoolean(KEY_PREFER_TURKISH, true)
            )
            loaded = true
        }
    }

    fun state(context: Context): State {
        ensureLoaded(context)
        return _state.value
    }

    fun update(context: Context, transform: (State) -> State) {
        ensureLoaded(context)
        val next = synchronized(this) {
            val updated = transform(_state.value)
            _state.value = updated
            updated
        }
        prefs(context).edit()
            .putBoolean(KEY_ENABLED, next.enabled)
            .putBoolean(KEY_NAME_FALLBACK, next.nameFallback)
            .putBoolean(KEY_SPOILER_GUARD, next.spoilerGuard)
            .putBoolean(KEY_PREFER_TURKISH, next.preferTurkish)
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

/**
 * Kullanıcının "Bu eşleşme yanlış" bildirimleri. Reddedilen wiki/sayfa bir daha önerilmez.
 * Önbellek temizliğinden etkilenmesin diye ayrı bir SharedPreferences dosyasında tutulur.
 */
object FandomUserChoices {

    private const val PREFS_NAME = "kitsugi_fandom_choices"
    private const val PAGE_PREFIX = "page:"
    private const val WIKI_PREFIX = "wiki:"
    private const val SEPARATOR = "\u001F"

    fun isPageRejected(context: Context, characterKey: String, wikiId: String, title: String): Boolean =
        prefs(context).getStringSet(PAGE_PREFIX + characterKey, emptySet())
            ?.contains(pageToken(wikiId, title)) == true

    fun rejectPage(context: Context, characterKey: String, wikiId: String, title: String) {
        addToSet(context, PAGE_PREFIX + characterKey, pageToken(wikiId, title))
    }

    fun isWikiRejected(context: Context, mediaKey: String, wikiId: String): Boolean =
        prefs(context).getStringSet(WIKI_PREFIX + mediaKey, emptySet())?.contains(wikiId) == true

    fun rejectWiki(context: Context, mediaKey: String, wikiId: String) {
        addToSet(context, WIKI_PREFIX + mediaKey, wikiId)
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun pageToken(wikiId: String, title: String): String =
        "${wikiId.lowercase()}$SEPARATOR${title.trim().replace('_', ' ')}"

    @Synchronized
    private fun addToSet(context: Context, key: String, value: String) {
        val prefs = prefs(context)
        // getStringSet'in döndürdüğü küme değiştirilmemelidir; kopya üzerinde çalışılır.
        val updated = HashSet(prefs.getStringSet(key, emptySet()).orEmpty())
        updated += value
        prefs.edit().putStringSet(key, updated).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
