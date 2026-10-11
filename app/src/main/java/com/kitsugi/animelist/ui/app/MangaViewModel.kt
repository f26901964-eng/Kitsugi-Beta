package com.kitsugi.animelist.ui.app

import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.kitsugi.animelist.core.notifications.AddonInstallNotifier
import com.kitsugi.animelist.core.security.TrustManager
import com.kitsugi.animelist.core.security.RepoVerifier
import java.io.File
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.local.MangaSourceStateEntity
import com.kitsugi.animelist.data.manga.MangaExtensionLoader
import com.kitsugi.animelist.data.manga.MangaExtensionAutoUpdater
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.MangaSourceRepository
import com.kitsugi.animelist.data.manga.stableSourceKey
import com.kitsugi.animelist.data.manga.model.SourceHealthStatus
import com.kitsugi.animelist.data.manga.model.SourceRuntimeStats
import com.kitsugi.animelist.data.remote.MangaExtensionInfo
import com.kitsugi.animelist.data.remote.MangaRepoClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

class MangaViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    val mangaRepository = MangaSourceRepository(context)
    private val mangaRepoClient = MangaRepoClient(context)
    private val mangaRepoPrefs = context.getSharedPreferences("manga_repos", android.content.Context.MODE_PRIVATE)

    var mangaSources by mutableStateOf(mangaRepository.getAvailableSources())
        private set

    var mangaReposState by mutableStateOf<List<String>>(emptyList())
        private set

    var mangaRepoExtensionsState by mutableStateOf<Map<String, List<MangaExtensionInfo>?>>(emptyMap())
    var mangaRepoLoadingState by mutableStateOf<Map<String, Boolean>>(emptyMap())

    /**
     * Güncelleme mevcut olan eklentilerin paket adları (pkg).
     * Mihon'un Extension.Installed.hasUpdate alanına karşılık gelir.
     * versionCode karşılaştırması ile doldurulur.
     */
    var extensionsWithUpdates by mutableStateOf<Set<String>>(emptySet())
        private set

    var mangaBulkInstallRepoUrl by mutableStateOf<String?>(null)
    var mangaBulkInstallDone by mutableStateOf(0)
    var mangaBulkInstallTotal by mutableStateOf(0)
    var mangaBulkInstallCurrentName by mutableStateOf("")

    private fun notifyMangaBulkProgress(repoName: String = mangaBulkInstallRepoUrl.orEmpty()) {
        AddonInstallNotifier.showProgress(
            context = context,
            repoName = repoName,
            done = mangaBulkInstallDone,
            total = mangaBulkInstallTotal,
            currentName = mangaBulkInstallCurrentName
        )
    }

    private fun finishMangaBulkNotification(message: String, success: Boolean) {
        AddonInstallNotifier.showResult(
            context = context,
            message = message
        )
    }

    var onShowMessage: ((String) -> Unit)? = null

    var untrustedRepoToConfirm by mutableStateOf<String?>(null)
        private set

    var untrustedSignatureToConfirm by mutableStateOf<Pair<MangaExtensionInfo, String>?>(null)
        private set

    fun clearUntrustedRepoConfirm() {
        untrustedRepoToConfirm = null
    }

    fun clearUntrustedSignatureConfirm() {
        untrustedSignatureToConfirm = null
    }

    var mangaSourceBusyKeys by mutableStateOf<Set<String>>(emptySet())
        private set

    var mangaSourceStateReport by mutableStateOf<List<MangaSourceStateEntity>>(emptyList())
        private set

    init {
        // Load saved repo URLs
        val saved = mangaRepoPrefs.getString("repo_urls", null)
        val urls: List<String> = if (!saved.isNullOrBlank()) {
            try {
                val arr = org.json.JSONArray(saved)
                (0 until arr.length()).map { arr.getString(it) }
            } catch (_: Exception) { emptyList() }
        } else emptyList()
        mangaReposState = urls

        // Repo verilerini otomatik çek ve ardından güncelleme kontrolü yap
        viewModelScope.launch {
            urls.forEach { url ->
                if (!mangaRepoExtensionsState.containsKey(url) && mangaRepoLoadingState[url] != true) {
                    fetchMangaRepo(url)
                }
            }
            if (urls.isNotEmpty()) {
                checkAllUpdates()
            }
        }

        viewModelScope.launch {
            mangaRepository.observeSourceStateReport().collect { report ->
                mangaSourceStateReport = report
            }
        }
    }

    private fun saveMangaRepos(urls: List<String>) {
        mangaRepoPrefs.edit().putString("repo_urls", org.json.JSONArray(urls).toString()).apply()
    }

    fun fetchMangaRepo(repoUrl: String) {
        viewModelScope.launch {
            mangaRepoLoadingState = mangaRepoLoadingState + (repoUrl to true)
            val extensions = mangaRepoClient.fetchExtensionsAutoDetect(repoUrl)
            mangaRepoExtensionsState = mangaRepoExtensionsState + (repoUrl to extensions)
            mangaRepoLoadingState = mangaRepoLoadingState + (repoUrl to false)
            // Bu repo yenilendikten sonra güncelleme durumunu yeniden hesapla
            checkAllUpdates()
        }
    }

    /**
     * Kurulu APK'nın versionCode'unu döndürür.
     * Mihon'daki PackageInfoCompat.getLongVersionCode() ile eşdeğer.
     * @return -1L → kurulu değil veya okunamadı
     */
    fun getInstalledVersionCode(pkg: String): Long =
        MangaExtensionLoader.getInstalledVersionCode(context, pkg)

    /**
     * Uzak repodaki versionCode ile yerel APK'yı karşılaştırır.
     * Mihon'daki hasUpdatedVer / hasUpdatedLib mantığına karşılık gelir.
     */
    fun hasUpdateAvailable(pkg: String, remoteVersionCode: Int): Boolean {
        val local = getInstalledVersionCode(pkg)
        return local >= 0L && remoteVersionCode.toLong() > local
    }

    /**
     * Tüm kayıtlı repolar üzerinden güncelleme kontrolü yapar.
     * Mihon'daki ExtensionApi.checkForUpdates() mantığına karşılık gelir:
     *   installedVer < remoteVer → extensionsWithUpdates'e ekle
     *
     * Uygulama açılışında ve her repo yenilemesinde çağrılır.
     */
    fun checkAllUpdates() {
        viewModelScope.launch(Dispatchers.IO) {
            val updatable = mutableSetOf<String>()
            mangaRepoExtensionsState.values.forEach { extensions ->
                extensions?.forEach { ext ->
                    if (hasUpdateAvailable(ext.pkg, ext.versionCode)) {
                        updatable.add(ext.pkg)
                        Log.d("MangaViewModel", "Güncelleme mevcut: ${ext.name} (local=${getInstalledVersionCode(ext.pkg)} < remote=${ext.versionCode})")
                    }
                }
            }
            withContext(Dispatchers.Main) {
                extensionsWithUpdates = updatable
                if (updatable.isNotEmpty()) {
                    Log.i("MangaViewModel", "${updatable.size} eklenti için güncelleme mevcut")
                }
            }
        }
    }

    fun addMangaRepo(urlsInput: String, force: Boolean = false) {
        viewModelScope.launch {
            val newUrls = urlsInput.split(Regex("[,\\n\\r\\s]+"))
                .map { it.trim() }
                .filter { it.isNotBlank() && !mangaReposState.contains(it) }
            if (newUrls.isEmpty()) return@launch

            if (!force) {
                val untrusted = newUrls.firstOrNull { !RepoVerifier.isUrlTrusted(context, it) }
                if (untrusted != null) {
                    untrustedRepoToConfirm = urlsInput
                    return@launch
                }
            }

            untrustedRepoToConfirm = null
            newUrls.forEach { RepoVerifier.trustRepo(context, it) }

            val updatedList = mangaReposState + newUrls
            mangaReposState = updatedList
            saveMangaRepos(updatedList)
            newUrls.forEach { fetchMangaRepo(it) }
        }
    }

    fun deleteMangaRepo(url: String) {
        val updatedList = mangaReposState.filter { it != url }
        mangaReposState = updatedList
        saveMangaRepos(updatedList)
        mangaRepoExtensionsState = mangaRepoExtensionsState - url
        mangaRepoLoadingState = mangaRepoLoadingState - url
    }

    fun installMangaExtension(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = mangaRepository.installExtensionFromUri(uri)
            withContext(Dispatchers.Main) {
                mangaSources = mangaRepository.getAvailableSources()
                if (result.isSuccess) {
                    onShowMessage?.invoke(KitsugiStrings.get(R.string.manga_eklentisi_yuklendi_x, result.getOrNull()?.name))
                } else {
                    onShowMessage?.invoke(KitsugiStrings.get(R.string.eklenti_yuklenemedi_x, result.exceptionOrNull()?.message))
                }
            }
        }
    }

    fun deleteMangaExtension(source: MangaSource) {
        viewModelScope.launch(Dispatchers.IO) {
            mangaRepository.deleteExtension(source)
            withContext(Dispatchers.Main) {
                mangaSources = mangaRepository.getAvailableSources()
                onShowMessage?.invoke(KitsugiStrings.get(R.string.eklenti_kaldirildi_x_5014f43, source.name))
            }
        }
    }

    fun toggleMangaSource(source: MangaSource, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val status = if (enabled) {
                com.kitsugi.animelist.data.manga.model.SourceHealthStatus.Healthy
            } else {
                com.kitsugi.animelist.data.manga.model.SourceHealthStatus.Disabled
            }
            mangaRepository.setSourceHealthStatus(source, status)
            withContext(Dispatchers.Main) {
                mangaSources = mangaRepository.getAvailableSources()
                onShowMessage?.invoke(
                    if (enabled) KitsugiStrings.get(R.string.eklenti_etkinlestirildi_x, source.name)
                    else "⏸️ Eklenti devre dışı bırakıldı: ${source.name}"
                )
            }
        }
    }

    fun installMangaApk(ext: MangaExtensionInfo, onResult: ((Boolean) -> Unit)?) {
        installMangaApk(ext, force = false, onResult = onResult)
    }

    fun installMangaApk(ext: MangaExtensionInfo, force: Boolean, onResult: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val extensionId = ext.pkg.replace(".", "_")
            var signatureHashToPrompt: String? = null
            
            val success = withContext(Dispatchers.IO) {
                val downloaded = MangaExtensionLoader.downloadExtension(context, extensionId, ext.apkUrl)
                if (downloaded) {
                    val apkFile = MangaExtensionLoader.apkFile(context, extensionId)
                    val sigHash = TrustManager.getApkSignatureHash(context, apkFile)
                    if (sigHash != null) {
                        if (force) {
                            TrustManager.trustSignature(context, sigHash)
                        } else if (!TrustManager.isSignatureTrusted(context, sigHash)) {
                            signatureHashToPrompt = sigHash
                            return@withContext false
                        }
                    }
                    try {
                        MangaExtensionLoader.loadExtension(context, extensionId) != null
                    } catch (e: SecurityException) {
                        val hash = e.message?.substringAfterLast(": ")?.trim() ?: ""
                        signatureHashToPrompt = hash.ifEmpty { sigHash ?: "" }
                        false
                    }
                } else false
            }

            if (signatureHashToPrompt != null && !force) {
                untrustedSignatureToConfirm = Pair(ext, signatureHashToPrompt!!)
                onResult?.invoke(false)
            } else {
                untrustedSignatureToConfirm = null
                mangaSources = mangaRepository.getAvailableSources()
                onResult?.invoke(success)
                onShowMessage?.invoke(
                    if (success) KitsugiStrings.get(R.string.yuklendi_x, ext.name) else KitsugiStrings.get(R.string.indirilemedi_x, ext.name)
                )
                if (success) checkAllUpdates()
            }
        }
    }

    fun installAllMangaExtensions(repoUrl: String, extensions: List<MangaExtensionInfo>) {
        viewModelScope.launch {
            val toInstall = extensions.filter { ext ->
                mangaSources.none { (it.pkgName.isNotEmpty() && it.pkgName == ext.pkg) || it.name == ext.name }
            }
            if (toInstall.isEmpty()) {
                onShowMessage?.invoke(KitsugiStrings.get(R.string.bu_repodaki_tum_eklentiler_zaten_kurulu))
                return@launch
            }

            mangaBulkInstallRepoUrl = repoUrl
            mangaBulkInstallTotal = toInstall.size
            mangaBulkInstallDone = 0
            mangaBulkInstallCurrentName = toInstall.firstOrNull()?.name ?: ""
            notifyMangaBulkProgress()

            val semaphore = kotlinx.coroutines.sync.Semaphore(2) // Max 2 parallel downloads
            val doneCounter = java.util.concurrent.atomic.AtomicInteger(0)
            val successCounter = java.util.concurrent.atomic.AtomicInteger(0)

            val jobs = toInstall.map { ext ->
                launch {
                    semaphore.acquire()
                    try {
                        val extensionId = ext.pkg.replace(".", "_")
                        val success = withContext(Dispatchers.IO) {
                            val downloaded = MangaExtensionLoader.downloadExtension(context, extensionId, ext.apkUrl)
                            if (downloaded) {
                                MangaExtensionLoader.loadExtension(context, extensionId) != null
                            } else false
                        }
                        if (success) {
                            successCounter.incrementAndGet()
                        }
                        val completedCount = doneCounter.incrementAndGet()
                        mangaBulkInstallCurrentName = ext.name
                        mangaBulkInstallDone = completedCount
                        notifyMangaBulkProgress()
                        mangaSources = mangaRepository.getAvailableSources()
                    } finally {
                        semaphore.release()
                    }
                }
            }

            jobs.forEach { it.join() }

            val installedMsg = "✅ ${successCounter.get()} eklenti başarıyla kuruldu!"
            onShowMessage?.invoke(installedMsg)
            finishMangaBulkNotification(installedMsg, success = successCounter.get() > 0)
            mangaBulkInstallRepoUrl = null
            mangaBulkInstallDone = 0
            mangaBulkInstallTotal = 0
            mangaBulkInstallCurrentName = ""
            checkAllUpdates()
        }
    }

    fun updateAllMangaExtensions(repoUrl: String, extensions: List<MangaExtensionInfo>) {
        viewModelScope.launch {
            val toUpdate = extensions.filter { ext ->
                mangaSources.any { (it.pkgName.isNotEmpty() && it.pkgName == ext.pkg) || it.name == ext.name }
            }
            if (toUpdate.isEmpty()) {
                onShowMessage?.invoke(KitsugiStrings.get(R.string.guncellenecek_eklenti_bulunamadi))
                return@launch
            }

            mangaBulkInstallRepoUrl = repoUrl
            mangaBulkInstallTotal = toUpdate.size
            mangaBulkInstallDone = 0
            mangaBulkInstallCurrentName = toUpdate.firstOrNull()?.name ?: ""
            notifyMangaBulkProgress()

            val semaphore = kotlinx.coroutines.sync.Semaphore(2) // Max 2 parallel downloads
            val doneCounter = java.util.concurrent.atomic.AtomicInteger(0)
            val successCounter = java.util.concurrent.atomic.AtomicInteger(0)

            val jobs = toUpdate.map { ext ->
                launch {
                    semaphore.acquire()
                    try {
                        val extensionId = ext.pkg.replace(".", "_")
                        val success = withContext(Dispatchers.IO) {
                            val downloaded = MangaExtensionLoader.downloadExtension(context, extensionId, ext.apkUrl)
                            if (downloaded) {
                                MangaExtensionLoader.loadExtension(context, extensionId) != null
                            } else false
                        }
                        if (success) {
                            successCounter.incrementAndGet()
                        }
                        val completedCount = doneCounter.incrementAndGet()
                        mangaBulkInstallCurrentName = ext.name
                        mangaBulkInstallDone = completedCount
                        notifyMangaBulkProgress()
                        mangaSources = mangaRepository.getAvailableSources()
                    } finally {
                        semaphore.release()
                    }
                }
            }

            jobs.forEach { it.join() }

            val updatedMsg = "✅ ${successCounter.get()} eklenti başarıyla güncellendi!"
            onShowMessage?.invoke(updatedMsg)
            finishMangaBulkNotification(updatedMsg, success = successCounter.get() > 0)
            mangaBulkInstallRepoUrl = null
            mangaBulkInstallDone = 0
            mangaBulkInstallTotal = 0
            mangaBulkInstallCurrentName = ""
            checkAllUpdates()
        }
    }

    fun getInstalledMangaExtensionVersion(pkg: String): String? {
        val extensionId = pkg.replace(".", "_")
        val apkFile = File(MangaExtensionLoader.extensionDir(context), "$extensionId.apk")
        if (!apkFile.exists()) return null
        return try {
            val pm = context.packageManager
            val flags = 0
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
            }
            packageInfo?.versionName
        } catch (_: Exception) {
            null
        }
    }

    fun getSourceHealthStatus(source: MangaSource): SourceHealthStatus =
        mangaRepository.getSourceHealthStatus(source)

    fun getSourceRuntimeStats(source: MangaSource): SourceRuntimeStats =
        mangaRepository.getSourceRuntimeStats(source)

    fun getSourceFailureStreak(source: MangaSource): Int =
        mangaRepository.getSourceFailureStreak(source)

    fun getSourceCooldownUntil(source: MangaSource): Long =
        mangaRepository.getSourceCooldownUntil(source)

    fun getSourceLastErrorCode(source: MangaSource): String? =
        mangaRepository.getSourceLastErrorCode(source)

    fun getSourceLastReason(source: MangaSource): String? =
        mangaRepository.getSourceLastReason(source)

    fun getConfiguredSourceDomain(source: MangaSource): String? =
        mangaRepository.getConfiguredDomain(source)

    fun getConfiguredSourceBaseUrl(source: MangaSource): String =
        mangaRepository.getConfiguredBaseUrl(source)

    fun getSourceUserAgentOverride(source: MangaSource): String? =
        mangaRepository.getSourceUserAgentOverride(source)

    fun getSourceSlowdownEnabled(source: MangaSource): Boolean =
        mangaRepository.getSourceSlowdownEnabled(source)

    fun setSourceDomainOverride(source: MangaSource, value: String?) {
        val ok = mangaRepository.setConfiguredDomain(source, value)
        mangaSources = mangaRepository.getAvailableSources()
        onShowMessage?.invoke(
            when {
                value.isNullOrBlank() -> "🧹 Domain temizlendi: ${source.name}"
                ok -> "🌐 Domain kaydedildi: ${source.name}"
                else -> "❌ Geçersiz domain: ${source.name}"
            }
        )
    }

    fun setSourceUserAgentOverride(source: MangaSource, value: String?) {
        val ok = mangaRepository.setSourceUserAgentOverride(source, value)
        mangaSources = mangaRepository.getAvailableSources()
        onShowMessage?.invoke(
            when {
                value.isNullOrBlank() -> "🧹 User-Agent temizlendi: ${source.name}"
                ok -> "💾 User-Agent kaydedildi: ${source.name}"
                else -> "❌ Geçersiz User-Agent: ${source.name}"
            }
        )
    }

    fun setSourceSlowdownEnabled(source: MangaSource, enabled: Boolean) {
        mangaRepository.setSourceSlowdownEnabled(source, enabled)
        mangaSources = mangaRepository.getAvailableSources()
        onShowMessage?.invoke(
            if (enabled) KitsugiStrings.get(R.string.slowdown_acildi_x, source.name) else KitsugiStrings.get(R.string.slowdown_kapatildi_x, source.name)
        )
    }

    fun resetSourceDiagnostics(source: MangaSource) {
        mangaRepository.resetSourceDiagnostics(source)
        mangaSources = mangaRepository.getAvailableSources()
        onShowMessage?.invoke(KitsugiStrings.get(R.string.source_istatistikleri_sifirlandi_x, source.name))
    }

    fun clearAllSourceDiagnostics() {
        mangaRepository.clearAllSourceDiagnostics()
        onShowMessage?.invoke(KitsugiStrings.get(R.string.tum_source_rapor_verileri_temizlendi))
    }

    fun clearAllSourceConfigs() {
        mangaRepository.clearAllSourceConfigs()
        mangaSources = mangaRepository.getAvailableSources()
        onShowMessage?.invoke(KitsugiStrings.get(R.string.tum_source_config_ayarlari_temizlendi))
    }

    fun isSourceBusy(source: MangaSource): Boolean =
        source.stableSourceKey() in mangaSourceBusyKeys

    fun quickCheckSource(source: MangaSource) {
        val key = source.stableSourceKey()
        if (key in mangaSourceBusyKeys) return
        mangaSourceBusyKeys = mangaSourceBusyKeys + key
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    mangaRepository.quickCheckSourceHealth(source)
                }
            }
            mangaSources = mangaRepository.getAvailableSources()
            mangaSourceBusyKeys = mangaSourceBusyKeys - key
            result.onSuccess { status ->
                onShowMessage?.invoke("🔎 ${source.name}: ${status.name}")
            }.onFailure { error ->
                onShowMessage?.invoke("❌ ${source.name}: ${error.message ?: "Sağlık testi başarısız"}")
            }
        }
    }

    fun refreshSourceMirror(source: MangaSource) {
        val key = source.stableSourceKey()
        if (key in mangaSourceBusyKeys) return
        mangaSourceBusyKeys = mangaSourceBusyKeys + key
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    mangaRepository.tryRefreshSourceMirror(source)
                }
            }
            mangaSources = mangaRepository.getAvailableSources()
            mangaSourceBusyKeys = mangaSourceBusyKeys - key
            result.onSuccess { success ->
                onShowMessage?.invoke(
                    if (success) KitsugiStrings.get(R.string.mirror_guncellendi_x, source.name)
                    else "⚠️ Mirror bulunamadı: ${source.name}"
                )
            }.onFailure { error ->
                onShowMessage?.invoke("❌ ${source.name}: ${error.message ?: "Mirror testi başarısız"}")
            }
        }
    }

    fun clearSourceMirror(source: MangaSource) {
        mangaRepository.clearConfiguredDomain(source)
        mangaSources = mangaRepository.getAvailableSources()
        onShowMessage?.invoke(KitsugiStrings.get(R.string.domain_temizlendi_x, source.name))
    }

    fun forceCheckMangaUpdates(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = MangaExtensionAutoUpdater.runIfNeeded(context, forceCheck = true)
            val msg = when (result) {
                is MangaExtensionAutoUpdater.UpdateResult.Success -> {
                    mangaSources = mangaRepository.getAvailableSources()
                    checkAllUpdates()
                    if (result.updated > 0) {
                        "✅ ${result.updated} eklenti güncellendi: ${result.updatedNames.joinToString(", ")}"
                    } else {
                        "✅ Tüm eklentiler güncel (${result.checked} kontrol edildi)"
                    }
                }
                is MangaExtensionAutoUpdater.UpdateResult.Skipped -> {
                    "ℹ️ Atlandı: ${result.reason}"
                }
                is MangaExtensionAutoUpdater.UpdateResult.Failed -> {
                    "❌ Hata: ${result.reason}"
                }
            }
            onResult(msg)
        }
    }
}
