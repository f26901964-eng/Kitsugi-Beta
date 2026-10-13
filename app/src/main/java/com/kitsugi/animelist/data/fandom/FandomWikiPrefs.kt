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
 * Kullanıcının eşleşme kararları: "Bu eşleşme doğru" (onay) ve "Yanlışsa bildir" (ret).
 * Reddedilen wiki/sayfa bir daha önerilmez; onaylanan eşleşme kesin kabul edilir.
 * Önbellek süresinden etkilenmesin diye ayrı bir SharedPreferences dosyasında tutulur;
 * yalnızca Ayarlar'daki "Wiki önbelleğini temizle" siler.
 */
object FandomUserChoices {

    private const val PREFS_NAME = "kitsugi_fandom_choices"
    private const val PAGE_PREFIX = "page:"
    private const val WIKI_PREFIX = "wiki:"
    private const val ACCEPTED_PAGE_PREFIX = "accepted-page:"
    private const val ACCEPTED_WIKI_PREFIX = "accepted-wiki:"
    private const val SEPARATOR = "\u001F"

    // ── Sayfa (karakter) ─────────────────────────────────────────────────────

    fun isPageRejected(context: Context, characterKey: String, wikiId: String, title: String): Boolean =
        prefs(context).getStringSet(PAGE_PREFIX + characterKey, emptySet())
            ?.contains(pageToken(wikiId, title)) == true

    @Synchronized
    fun rejectPage(context: Context, characterKey: String, wikiId: String, title: String) {
        val token = pageToken(wikiId, title)
        val prefs = prefs(context)
        val editor = prefs.edit()
        addToSet(prefs, editor, PAGE_PREFIX + characterKey, token)
        if (prefs.getString(ACCEPTED_PAGE_PREFIX + characterKey, null) == token) {
            editor.remove(ACCEPTED_PAGE_PREFIX + characterKey)
        }
        editor.apply()
    }

    /** Yanlışlıkla basılan "Hayır, yanlış"ı geri alır: sayfa tekrar önerilebilir. */
    @Synchronized
    fun unrejectPage(context: Context, characterKey: String, wikiId: String, title: String) {
        val key = PAGE_PREFIX + characterKey
        val prefs = prefs(context)
        val updated = HashSet(prefs.getStringSet(key, emptySet()).orEmpty())
        if (!updated.remove(pageToken(wikiId, title))) return
        prefs.edit().putStringSet(key, updated).apply()
    }

    fun isPageAccepted(context: Context, characterKey: String, wikiId: String, title: String): Boolean =
        prefs(context).getString(ACCEPTED_PAGE_PREFIX + characterKey, null) == pageToken(wikiId, title)

    @Synchronized
    fun acceptPage(context: Context, characterKey: String, wikiId: String, title: String) {
        prefs(context).edit().putString(ACCEPTED_PAGE_PREFIX + characterKey, pageToken(wikiId, title)).apply()
    }

    // ── Wiki (yapım) ─────────────────────────────────────────────────────────

    fun isWikiRejected(context: Context, mediaKey: String, wikiId: String): Boolean =
        prefs(context).getStringSet(WIKI_PREFIX + mediaKey, emptySet())?.contains(wikiId.lowercase()) == true

    @Synchronized
    fun rejectWiki(context: Context, mediaKey: String, wikiId: String) {
        val normalized = wikiId.lowercase()
        val prefs = prefs(context)
        val editor = prefs.edit()
        addToSet(prefs, editor, WIKI_PREFIX + mediaKey, normalized)
        if (prefs.getString(ACCEPTED_WIKI_PREFIX + mediaKey, null) == normalized) {
            editor.remove(ACCEPTED_WIKI_PREFIX + mediaKey)
        }
        editor.apply()
    }

    /** Yanlışlıkla basılan "Hayır, yanlış"ı geri alır: wiki bu yapım için tekrar önerilebilir. */
    @Synchronized
    fun unrejectWiki(context: Context, mediaKey: String, wikiId: String) {
        val key = WIKI_PREFIX + mediaKey
        val prefs = prefs(context)
        val updated = HashSet(prefs.getStringSet(key, emptySet()).orEmpty())
        if (!updated.remove(wikiId.lowercase())) return
        prefs.edit().putStringSet(key, updated).apply()
    }

    fun acceptedWiki(context: Context, mediaKey: String): String? =
        prefs(context).getString(ACCEPTED_WIKI_PREFIX + mediaKey, null)?.takeIf { it.isNotBlank() }

    /**
     * Yalnızca retleri siler (Ayarlar → "Reddedilen eşleşmeleri geri getir").
     * Kullanıcının onayladığı eşleşmeler korunur.
     */
    @Synchronized
    fun clearRejections(context: Context) {
        val prefs = prefs(context)
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith(PAGE_PREFIX) || it.startsWith(WIKI_PREFIX) }
            .forEach { editor.remove(it) }
        editor.apply()
    }

    @Synchronized
    fun acceptWiki(context: Context, mediaKey: String, wikiId: String) {
        prefs(context).edit().putString(ACCEPTED_WIKI_PREFIX + mediaKey, wikiId.lowercase()).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun pageToken(wikiId: String, title: String): String =
        "${wikiId.lowercase()}$SEPARATOR${title.trim().replace('_', ' ')}"

    private fun addToSet(
        prefs: android.content.SharedPreferences,
        editor: android.content.SharedPreferences.Editor,
        key: String,
        value: String
    ) {
        // getStringSet'in döndürdüğü küme değiştirilmemelidir; kopya üzerinde çalışılır.
        val updated = HashSet(prefs.getStringSet(key, emptySet()).orEmpty())
        updated += value
        editor.putStringSet(key, updated)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
