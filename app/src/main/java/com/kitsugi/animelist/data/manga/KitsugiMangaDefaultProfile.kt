package com.kitsugi.animelist.data.manga

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.core.security.RepoVerifier
import com.kitsugi.animelist.data.manga.model.SourceHealthStatus
import com.kitsugi.animelist.data.manga.sources.golgebahcesi.GolgeBahcesiSource
import com.kitsugi.animelist.data.remote.MangaRepoClient
import org.json.JSONArray

/**
 * Kitsugi Manga varsayılan kaynak ve depo profili yöneticisi.
 *
 * Politika:
 * - Varsayılan olarak yalnızca tek manga kaynağı (**Gölge Bahçesi**) aktiftir.
 * - Kotatsu derleme zamanı kaynaklarının tamamı varsayılan profilde kapalıdır
 *   (`activeLangs = emptySet()`) ve eski Kotatsu `GOLGEBAHCESI` ayrıştırıcısı kalıcı olarak devre dışıdır.
 * - Önceden kurulmuş diğer üçüncü parti eklenti dosyaları silinmez; ilk profil geçişinde
 *   yalnızca devre dışı (`Disabled`) duruma alınır.
 * - Kullanıcının resmi manga deposu (`f26901964-eng/Kitsugi-Manga-Plugins`) varsayılan
 *   manga deposu olarak eklenir ve güvenilir işaretlenir.
 */
object KitsugiMangaDefaultProfile {

    private const val TAG = "KitsugiMangaProfile"
    private const val PROFILE_PREFS = "kitsugi_manga_default_profile"
    private const val KEY_SINGLE_GOLGE_APPLIED_V1 = "single_golgebahcesi_profile_v1"
    private const val KEY_DEFAULT_REPO_SEEDED_V1 = "default_kitsugi_manga_repo_seeded_v1"

    private const val MANGA_REPOS_PREFS = "manga_repos"
    private const val KEY_REPO_URLS = "repo_urls"

    const val DEFAULT_ACTIVE_SOURCE_PKG = GolgeBahcesiSource.PKG_NAME

    fun isDefaultActiveSource(source: MangaSource): Boolean {
        return source.pkgName.equals(DEFAULT_ACTIVE_SOURCE_PKG, ignoreCase = true) ||
            (source.isBuiltIn && source.name.equals(GolgeBahcesiSource.SOURCE_NAME, ignoreCase = true))
    }

    /**
     * Uygulama başlangıcında ve [MangaSourceRepository] kurulumunda idempotent olarak çalışır:
     * 1) Dahili [GolgeBahcesiSource] kaynağını kaydeder,
     * 2) Kotatsu dil tercihlerini yükler (ilk geçişte boş kümeye sıfırlar),
     * 3) İlk geçişte diğer kaynakları silmeden devre dışı bırakıp yalnızca Gölge Bahçesi'ni aktif tutar,
     * 4) Resmi `Kitsugi-Manga-Plugins` deposunu varsayılan depo listesine ekler.
     */
    @Synchronized
    fun ensureApplied(context: Context) {
        val appContext = context.applicationContext
        val golgeSource = MangaExtensionLoader.registerBuiltInSources(appContext)
        KotatsuExtensionAdapter.loadSavedPrefs(appContext)

        val prefs = appContext.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
        seedDefaultMangaRepoIfNeeded(appContext, prefs)

        if (prefs.getBoolean(KEY_SINGLE_GOLGE_APPLIED_V1, false)) {
            return
        }

        Log.i(TAG, "Tek-kaynak Gölge Bahçesi varsayılan profili uygulanıyor...")

        // 1) Kotatsu dillerini varsayılan profilde tamamen kapat (otomatik yeniden yüklenmesin)
        KotatsuExtensionAdapter.setActiveLangs(emptySet(), appContext)

        // 2) Yüklü diğer eklentileri silmeden devre dışı bırak; Gölge Bahçesi'ni sağlıklı/aktif yap
        runCatching {
            val stateStore = MangaSourceStateStore(appContext)
            stateStore.setHealthStatus(golgeSource, SourceHealthStatus.Healthy, reason = "default_active")

            MangaExtensionLoader.getExternalSourcesSnapshot().forEach { externalSource ->
                if (!isDefaultActiveSource(externalSource)) {
                    stateStore.setHealthStatus(
                        externalSource,
                        SourceHealthStatus.Disabled,
                        reason = "default_profile_disabled",
                    )
                    markExternalSourceDefaulted(appContext, externalSource)
                }
            }
        }.onFailure { e ->
            Log.w(TAG, "Varsayılan kaynak durumu uygulanırken uyarı: ${e.message}")
        }

        prefs.edit().putBoolean(KEY_SINGLE_GOLGE_APPLIED_V1, true).apply()
        Log.i(TAG, "Varsayılan manga profili tamamlandı: yalnızca Gölge Bahçesi aktif.")
    }

    /**
     * İlk tek-kaynak geçişinden sonra yeni kurulan harici eklentileri yalnızca bir kez
     * varsayılan olarak devre dışı bırakır; kullanıcının sonraki manuel etkinleştirmesi
     * süreç yeniden başlayınca korunur.
     */
    fun onExternalSourcesLoaded(context: Context, sources: List<MangaSource>) {
        val appContext = context.applicationContext
        val profilePrefs = appContext.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
        if (!profilePrefs.getBoolean(KEY_SINGLE_GOLGE_APPLIED_V1, false)) return

        val stateStore = MangaSourceStateStore(appContext)
        sources.forEach { source ->
            if (isDefaultActiveSource(source) || source.isBuiltIn) return@forEach
            val markerKey = externalSourceMarker(source)
            if (profilePrefs.getBoolean(markerKey, false)) return@forEach
            stateStore.setHealthStatus(source, SourceHealthStatus.Disabled, reason = "default_profile_disabled")
            profilePrefs.edit().putBoolean(markerKey, true).apply()
        }
    }

    private fun markExternalSourceDefaulted(context: Context, source: MangaSource) {
        context.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(externalSourceMarker(source), true)
            .apply()
    }

    private fun externalSourceMarker(source: MangaSource): String =
        "disabled_default_source_${source.stableSourceKey()}"

    /**
     * Kayıtlı manga depo listesini okur; ilk kurulumda veya güncellemede
     * `f26901964-eng/Kitsugi-Manga-Plugins` resmi deposunu listenin en başına ekler.
     */
    fun loadOrSeedMangaRepos(context: Context): List<String> {
        val appContext = context.applicationContext
        val profilePrefs = appContext.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
        seedDefaultMangaRepoIfNeeded(appContext, profilePrefs)

        val repoPrefs = appContext.getSharedPreferences(MANGA_REPOS_PREFS, Context.MODE_PRIVATE)
        return readRepoUrls(repoPrefs)
    }

    private fun seedDefaultMangaRepoIfNeeded(
        appContext: Context,
        profilePrefs: android.content.SharedPreferences,
    ) {
        val officialUrl = MangaRepoClient.KITSUGI_MANGA_REPO_URL
        runCatching {
            RepoVerifier.trustRepo(appContext, officialUrl)
            RepoVerifier.trustRepo(appContext, "https://raw.githubusercontent.com")
        }

        val repoPrefs = appContext.getSharedPreferences(MANGA_REPOS_PREFS, Context.MODE_PRIVATE)
        val existing = readRepoUrls(repoPrefs)
            .map { MangaRepoClient.normalizeMangaRepoUrl(it) }
            .distinct()

        val alreadySeeded = profilePrefs.getBoolean(KEY_DEFAULT_REPO_SEEDED_V1, false)
        if (!alreadySeeded) {
            val merged = buildList {
                add(officialUrl)
                existing.forEach { url ->
                    if (!MangaRepoClient.isKitsugiMangaRepoUrl(url)) {
                        add(url)
                    }
                }
            }
            writeRepoUrls(repoPrefs, merged)
            profilePrefs.edit().putBoolean(KEY_DEFAULT_REPO_SEEDED_V1, true).apply()
        } else if (existing != readRepoUrls(repoPrefs)) {
            writeRepoUrls(repoPrefs, existing)
        }
    }

    private fun readRepoUrls(repoPrefs: android.content.SharedPreferences): List<String> {
        val saved = repoPrefs.getString(KEY_REPO_URLS, null)
        if (saved.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(saved)
            buildList {
                for (i in 0 until arr.length()) {
                    val u = arr.optString(i, "").trim()
                    if (u.isNotEmpty()) add(u)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeRepoUrls(repoPrefs: android.content.SharedPreferences, urls: List<String>) {
        repoPrefs.edit().putString(KEY_REPO_URLS, JSONArray(urls).toString()).apply()
    }
}
