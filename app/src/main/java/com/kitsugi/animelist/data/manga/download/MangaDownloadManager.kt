package com.kitsugi.animelist.data.manga.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import com.kitsugi.animelist.core.download.MangaDownloadService
import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.MangaSourceRepository
import com.kitsugi.animelist.data.manga.MangaSourceResult
import com.kitsugi.animelist.data.manga.stableSourceKey
import com.kitsugi.animelist.data.settings.SettingsDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Persistent, chapter-oriented manga downloader.
 *
 * Page files are validated and renamed atomically, partial chapters survive process death and
 * every variant is keyed by source + chapter URL. Completed chapters can be read without network.
 */
object MangaDownloadManager {
    private const val PREFS_NAME = "manga_chapter_downloads"
    private const val KEY_RECORDS = "records_v1"
    private const val PAGE_TIMEOUT_MS = 45_000L
    private const val PAGE_LIST_TIMEOUT_MS = 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateLock = Any()
    private val schedulerLock = Any()
    private val jobs = mutableMapOf<String, Job>()
    private val runtimeSources = mutableMapOf<String, MangaSource>()
    private val runtimeDetails = mutableMapOf<String, MangaDetails>()

    private var appContext: Context? = null
    private var loaded = false
    private var pumpScheduled = false

    private val _downloads = MutableStateFlow<List<MangaDownload>>(emptyList())
    val downloads: StateFlow<List<MangaDownload>> = _downloads.asStateFlow()

    fun initialize(context: Context) {
        val applicationContext = context.applicationContext
        appContext = applicationContext
        synchronized(stateLock) {
            if (loaded) return
            loaded = true
            val restored = readRecords(applicationContext).map { record ->
                when {
                    record.status == MangaDownload.Status.COMPLETED && !contentExists(record) ->
                        record.copy(
                            status = MangaDownload.Status.ERROR,
                            error = "İndirilen dosya bulunamadı. Bölümü yeniden indirin.",
                        )
                    record.status == MangaDownload.Status.DOWNLOADING ||
                        record.status == MangaDownload.Status.QUEUE ->
                        record.copy(status = MangaDownload.Status.PAUSED)
                    else -> record
                }
            }
            _downloads.value = restored.sortedByDescending { it.createdAt }
            persistRecords(applicationContext, _downloads.value)
        }
    }

    fun enqueue(
        context: Context,
        source: MangaSource,
        details: MangaDetails,
        chapter: MangaChapter,
    ): MangaDownload {
        initialize(context)
        val created = MangaDownload.from(source, details, chapter)
        runtimeSources[created.id] = source
        runtimeDetails[created.id] = details

        val result: MangaDownload
        synchronized(stateLock) {
            val existing = _downloads.value.firstOrNull { it.id == created.id }
            result = when {
                existing?.status == MangaDownload.Status.COMPLETED && contentExists(existing) -> existing
                existing?.status == MangaDownload.Status.DOWNLOADING ||
                    existing?.status == MangaDownload.Status.QUEUE -> existing
                existing != null -> existing.copy(
                    sourceName = created.sourceName,
                    sourceBaseUrl = created.sourceBaseUrl,
                    mangaTitle = created.mangaTitle,
                    mangaPosterUrl = created.mangaPosterUrl,
                    chapterName = created.chapterName,
                    scanlator = created.scanlator,
                    status = MangaDownload.Status.QUEUE,
                    error = null,
                    updatedAt = System.currentTimeMillis(),
                )
                else -> created
            }
            if (existing == null || result != existing) {
                val next = _downloads.value.filterNot { it.id == result.id } + result
                commitLocked(next)
            }
        }
        if (result.isActive) MangaDownloadService.start(context)
        pumpQueue()
        return result
    }

    fun pause(context: Context, id: String) {
        initialize(context)
        update(id) {
            if (it.status == MangaDownload.Status.COMPLETED) it
            else it.copy(status = MangaDownload.Status.PAUSED, error = null)
        }
        val job = synchronized(schedulerLock) { jobs[id] }
        job?.cancel()
    }

    fun resume(
        context: Context,
        id: String,
        source: MangaSource? = null,
        details: MangaDetails? = null,
    ) {
        initialize(context)
        val record = get(id) ?: return
        if (source != null) runtimeSources[id] = source
        if (details != null) runtimeDetails[id] = details
        if (record.status == MangaDownload.Status.COMPLETED && contentExists(record)) return
        update(id) {
            it.copy(
                status = MangaDownload.Status.QUEUE,
                error = null,
                localPath = it.localPath?.takeIf { path -> File(path).exists() },
                archivePath = it.archivePath?.takeIf { path -> File(path).exists() },
            )
        }
        MangaDownloadService.start(context)
        pumpQueue()
    }

    fun delete(context: Context, id: String, deleteFiles: Boolean = true) {
        initialize(context)
        val record = get(id)
        val job = synchronized(schedulerLock) { jobs[id] }
        job?.cancel()
        if (deleteFiles && record != null) {
            record.localPath?.let(::deleteFileOrDirectory)
            record.archivePath?.let(::deleteFileOrDirectory)
        }
        runtimeSources.remove(id)
        runtimeDetails.remove(id)
        synchronized(stateLock) {
            commitLocked(_downloads.value.filterNot { it.id == id })
        }
    }

    fun get(id: String): MangaDownload? = _downloads.value.firstOrNull { it.id == id }

    fun get(source: MangaSource, chapter: MangaChapter): MangaDownload? {
        val id = MangaDownload.idFor(source.stableSourceKey(), chapter.url)
        return get(id)
    }

    fun localContent(
        context: Context,
        source: MangaSource,
        chapter: MangaChapter,
    ): File? {
        initialize(context)
        val record = get(source, chapter) ?: return null
        if (record.status != MangaDownload.Status.COMPLETED) return null
        return record.localPath?.let(::File)?.takeIf { contentExists(record) }
    }

    fun contentExists(record: MangaDownload): Boolean {
        val file = record.localPath?.let(::File) ?: return false
        return when {
            file.isFile -> file.length() > 0L
            file.isDirectory -> pageFiles(file).isNotEmpty()
            else -> false
        }
    }

    fun chapterOf(record: MangaDownload): MangaChapter = MangaChapter(
        url = record.chapterUrl,
        name = record.chapterName,
        chapterNumber = record.chapterNumber,
        scanlator = record.scanlator,
        uploadDate = record.chapterUploadDate,
        mangaUrl = record.mangaUrl,
    )

    fun detailsOf(record: MangaDownload): MangaDetails = MangaDetails(
        url = record.mangaUrl,
        title = record.mangaTitle,
        thumbnailUrl = record.mangaPosterUrl,
        source = record.sourceName,
    )

    /** A metadata-only source used when a completed chapter is opened from Downloads. */
    fun offlineSourceOf(record: MangaDownload): MangaSource = object : MangaSource {
        override val name: String = record.sourceName
        override val baseUrl: String = record.sourceBaseUrl.ifBlank { "https://offline.local" }
        override val lang: String = record.sourceLanguage.ifBlank { "tr" }
        // stableSourceKey prefers pkgName. Keeping the persisted key here also supports host-based keys.
        override val pkgName: String = record.sourcePackage.ifBlank { record.sourceKey }

        override suspend fun fetchPopularManga(page: Int): MangaSourceResult =
            MangaSourceResult(emptyList(), false)

        override suspend fun fetchSearchManga(page: Int, query: String): MangaSourceResult =
            MangaSourceResult(emptyList(), false)

        override suspend fun fetchMangaDetails(mangaUrl: String): MangaDetails = detailsOf(record)

        override suspend fun fetchChapterList(mangaUrl: String): List<MangaChapter> =
            listOf(chapterOf(record))

        override suspend fun fetchPageList(chapter: MangaChapter) =
            throw IOException("Çevrimdışı bölüm dosyası bulunamadı.")
    }

    private fun pumpQueue() {
        val context = appContext ?: return
        synchronized(schedulerLock) {
            if (pumpScheduled) return
            pumpScheduled = true
        }
        scope.launch {
            try {
                val slots = SettingsDataStore(context).settingsFlow.first()
                    .downloads.numberOfDownloads.coerceIn(1, 4)
                val toStart = synchronized(schedulerLock) {
                    val freeSlots = (slots - jobs.size).coerceAtLeast(0)
                    if (freeSlots == 0) return@synchronized emptyList<String>()
                    _downloads.value
                        .filter { it.status == MangaDownload.Status.QUEUE }
                        .sortedBy { it.createdAt }
                        .take(freeSlots)
                        .map { record ->
                            val job = scope.launch(start = CoroutineStart.LAZY) {
                                runDownload(record.id)
                            }
                            jobs[record.id] = job
                            job.invokeOnCompletion {
                                synchronized(schedulerLock) {
                                    if (jobs[record.id] === job) jobs.remove(record.id)
                                }
                                pumpQueue()
                            }
                            record.id
                        }
                }
                toStart.forEach { id ->
                    synchronized(schedulerLock) { jobs[id] }?.start()
                }
            } finally {
                val shouldPumpAgain = synchronized(schedulerLock) {
                    pumpScheduled = false
                    val active = jobs.values.any { it.isActive }
                    !active && _downloads.value.any { it.status == MangaDownload.Status.QUEUE }
                }
                if (shouldPumpAgain) pumpQueue()
            }
        }
    }

    private suspend fun runDownload(id: String) {
        try {
            val initial = get(id) ?: return
            // Lazy queue job may have been paused/deleted before it received a slot.
            if (initial.status != MangaDownload.Status.QUEUE &&
                initial.status != MangaDownload.Status.DOWNLOADING
            ) return
            val source = runtimeSources[id] ?: resolveSource(initial)
                ?: throw IOException("Manga kaynağı şu anda kullanılamıyor. Manga sayfasını açıp tekrar deneyin.")
            runtimeSources[id] = source
            val details = runtimeDetails[id] ?: detailsOf(initial).also { runtimeDetails[id] = it }
            val chapter = chapterOf(initial)

            update(id) { it.copy(status = MangaDownload.Status.DOWNLOADING, error = null) }
            awaitAllowedNetwork(id)

            val pages = withTimeout(PAGE_LIST_TIMEOUT_MS) { source.fetchPageList(chapter) }
            currentCoroutineContext().ensureActive()
            if (pages.isEmpty()) throw IOException("Kaynak bu bölüm için sayfa döndürmedi.")

            val workDir = workDirectory(initial)
            if (!workDir.exists() && !workDir.mkdirs()) {
                throw IOException("Manga indirme klasörü oluşturulamadı: ${workDir.absolutePath}")
            }
            update(id) {
                it.copy(
                    localPath = workDir.absolutePath,
                    totalPages = pages.size,
                    downloadedPages = 0,
                    progress = 0,
                )
            }

            val expectedPageNames = pages.indices.map(::pageBaseName).toSet()
            pageFiles(workDir)
                .filter { it.nameWithoutExtension !in expectedPageNames }
                .forEach { it.delete() }
            var downloadedBytes = pageFiles(workDir)
                .filter { it.nameWithoutExtension in expectedPageNames }
                .sumOf { it.length() }
            pages.forEachIndexed { index, page ->
                ensureRunning(id)
                currentCoroutineContext().ensureActive()
                val existing = findExistingPage(workDir, index)
                if (existing == null) {
                    if (page.imageUrl.isNullOrBlank()) {
                        page.imageUrl = withTimeout(PAGE_TIMEOUT_MS) { source.fetchImageUrl(page) }
                    }
                    if (page.imageUrl.isNullOrBlank()) {
                        throw IOException("${index + 1}. sayfanın görsel adresi çözülemedi.")
                    }
                    val downloaded = withTimeout(PAGE_TIMEOUT_MS) {
                        source.getImage(page).use { stream -> writePage(workDir, index, stream) }
                    }
                    downloadedBytes += downloaded.length()
                }

                val completedPages = index + 1
                update(id) {
                    it.copy(
                        downloadedPages = completedPages,
                        totalPages = pages.size,
                        progress = (completedPages * 100 / pages.size).coerceIn(0, 99),
                        totalBytes = downloadedBytes,
                        error = null,
                    )
                }
            }

            ensureRunning(id)
            val settings = SettingsDataStore(requireContext()).settingsFlow.first().downloads
            val completedFile = if (settings.saveChaptersAsCBZ) {
                val archive = createCbz(workDir, details, chapter, source.name)
                downloadedBytes = archive.length()
                workDir.deleteRecursively()
                archive
            } else {
                workDir
            }

            update(id) {
                it.copy(
                    status = MangaDownload.Status.COMPLETED,
                    progress = 100,
                    downloadedPages = pages.size,
                    totalPages = pages.size,
                    totalBytes = downloadedBytes,
                    localPath = completedFile.absolutePath,
                    archivePath = completedFile.takeIf { file -> file.extension.equals("cbz", true) }?.absolutePath,
                    error = null,
                )
            }
        } catch (error: TimeoutCancellationException) {
            update(id) {
                if (it.status == MangaDownload.Status.PAUSED) it
                else it.copy(status = MangaDownload.Status.ERROR, error = "İndirme zaman aşımına uğradı. Devam ettirerek kaldığı sayfadan sürdürebilirsin.")
            }
        } catch (error: CancellationException) {
            // Explicit pause/delete owns the visible state. Never overwrite PAUSED with ERROR.
            update(id) {
                if (it.status == MangaDownload.Status.DOWNLOADING) {
                    it.copy(status = MangaDownload.Status.PAUSED)
                } else it
            }
        } catch (error: Exception) {
            update(id) {
                if (it.status == MangaDownload.Status.PAUSED) it
                else it.copy(
                    status = MangaDownload.Status.ERROR,
                    error = error.localizedMessage ?: error.message ?: "Manga bölümü indirilemedi.",
                )
            }
        }
    }

    private suspend fun resolveSource(record: MangaDownload): MangaSource? {
        val sources = runCatching { MangaSourceRepository(requireContext()).getAvailableSources() }
            .getOrDefault(emptyList())
        return sources.firstOrNull { it.stableSourceKey() == record.sourceKey }
            ?: sources.firstOrNull {
                record.sourcePackage.isNotBlank() && it.pkgName.equals(record.sourcePackage, true)
            }
    }

    private suspend fun awaitAllowedNetwork(id: String) {
        val onlyWifi = SettingsDataStore(requireContext()).settingsFlow.first()
            .downloads.downloadOnlyOverWifi
        if (!onlyWifi) return
        var announced = false
        while (!hasUnmeteredNetwork()) {
            ensureRunning(id)
            if (!announced) {
                update(id) { it.copy(error = "Wi-Fi bağlantısı bekleniyor…") }
                announced = true
            }
            delay(3_000L)
        }
        if (announced) update(id) { it.copy(error = null) }
    }

    private fun hasUnmeteredNetwork(): Boolean {
        val manager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            (!manager.isActiveNetworkMetered ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
    }

    private fun ensureRunning(id: String) {
        val status = get(id)?.status
        if (status != MangaDownload.Status.DOWNLOADING) {
            throw CancellationException("Manga indirme duraklatıldı")
        }
    }

    private fun writePage(directory: File, index: Int, input: InputStream): File {
        val temp = File(directory, ".page-${index + 1}-${System.nanoTime()}.tmp")
        try {
            BufferedInputStream(input).use { source ->
                FileOutputStream(temp).buffered().use { target -> source.copyTo(target) }
            }
            val extension = detectImageExtension(temp)
                ?: throw IOException("${index + 1}. sayfa geçerli bir görsel değil (HTML/boş yanıt).")
            val target = File(directory, "${pageBaseName(index)}.$extension")
            if (target.exists() && !target.delete()) throw IOException("Eski sayfa dosyası değiştirilemedi.")
            if (!temp.renameTo(target)) throw IOException("${index + 1}. sayfa kalıcı dosyaya taşınamadı.")
            return target
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun detectImageExtension(file: File): String? {
        if (!file.isFile || file.length() < 12L) return null
        val header = ByteArray(16)
        FileInputStream(file).use { it.read(header) }
        fun ascii(offset: Int, length: Int) = String(header, offset, length, Charsets.US_ASCII)
        val extension = when {
            header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() -> "jpg"
            header[0] == 0x89.toByte() && ascii(1, 3) == "PNG" -> "png"
            ascii(0, 3) == "GIF" -> "gif"
            ascii(0, 4) == "RIFF" && ascii(8, 4) == "WEBP" -> "webp"
            ascii(0, 2) == "BM" -> "bmp"
            ascii(4, 4) == "ftyp" && ascii(8, 4) in setOf("avif", "avis") -> "avif"
            else -> null
        }
        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
        val isDecodable = options.outWidth > 0 && options.outHeight > 0
        if (extension != null) {
            // AVIF decoding depends on API level; its container signature is still validated above.
            return if (extension == "avif" || isDecodable) extension else null
        }
        return if (isDecodable) "img" else null
    }

    private fun createCbz(
        workDir: File,
        details: MangaDetails,
        chapter: MangaChapter,
        sourceName: String,
    ): File {
        val target = File(workDir.parentFile, "${workDir.name}.cbz")
        val temp = File(workDir.parentFile, ".${workDir.name}.cbz.tmp")
        if (temp.exists()) temp.delete()
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(temp))).use { zip ->
                pageFiles(workDir).forEach { page ->
                    zip.putNextEntry(ZipEntry(page.name))
                    FileInputStream(page).use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
                zip.putNextEntry(ZipEntry("ComicInfo.xml"))
                val xml = buildString {
                    append("<?xml version=\"1.0\" encoding=\"utf-8\"?><ComicInfo>")
                    append("<Series>${xmlEscape(details.title)}</Series>")
                    append("<Title>${xmlEscape(chapter.name)}</Title>")
                    if (chapter.chapterNumber >= 0) append("<Number>${chapter.chapterNumber}</Number>")
                    append("<Writer>${xmlEscape(chapter.scanlator.orEmpty())}</Writer>")
                    append("<Publisher>${xmlEscape(sourceName)}</Publisher>")
                    append("<Web>${xmlEscape(chapter.url)}</Web>")
                    append("</ComicInfo>")
                }
                zip.write(xml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            if (target.exists() && !target.delete()) throw IOException("Eski CBZ dosyası değiştirilemedi.")
            if (!temp.renameTo(target)) throw IOException("CBZ arşivi kalıcı dosyaya taşınamadı.")
            return target
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun workDirectory(record: MangaDownload): File {
        record.localPath?.let(::File)?.takeIf { it.isDirectory }?.let { return it }
        val mangaDirectory = File(storageRoot(), safeName(record.mangaTitle).ifBlank { "Manga" })
        val variant = buildString {
            append(safeName(record.chapterName).ifBlank { "Bölüm" })
            record.scanlator?.trim()?.takeIf { it.isNotBlank() }?.let {
                append(" - ")
                append(safeName(it))
            }
            append(" - ")
            append(record.id.take(8))
        }
        return File(mangaDirectory, variant)
    }

    private fun storageRoot(): File {
        val public = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Kitsugi/Manga",
        )
        if (canUseDirectory(public)) return public
        val external = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?.let { File(it, "Kitsugi/Manga") }
        if (external != null && canUseDirectory(external)) return external
        return File(requireContext().filesDir, "manga_downloads").also { it.mkdirs() }
    }

    /** mkdirs/canWrite alone can report success on scoped-storage devices where creation still fails. */
    private fun canUseDirectory(directory: File): Boolean {
        if (!(directory.isDirectory || directory.mkdirs())) return false
        val probe = File(directory, ".write-test-${System.nanoTime()}")
        return try {
            FileOutputStream(probe).use { }
            true
        } catch (_: Exception) {
            false
        } finally {
            probe.delete()
        }
    }

    private fun pageBaseName(index: Int): String = String.format(Locale.ROOT, "%04d", index + 1)

    private fun pageFiles(directory: File): List<File> = directory.listFiles()
        ?.filter { it.isFile && it.extension.lowercase(Locale.ROOT) in IMAGE_EXTENSIONS }
        ?.sortedBy { it.name }
        .orEmpty()

    private fun findExistingPage(directory: File, index: Int): File? {
        val prefix = pageBaseName(index)
        return pageFiles(directory).firstOrNull {
            it.nameWithoutExtension == prefix && it.length() > 0L
        }
    }

    private fun update(id: String, transform: (MangaDownload) -> MangaDownload) {
        synchronized(stateLock) {
            val current = _downloads.value.firstOrNull { it.id == id } ?: return
            val updated = transform(current).copy(updatedAt = System.currentTimeMillis())
            if (updated == current) return
            commitLocked(_downloads.value.map { if (it.id == id) updated else it })
        }
    }

    private fun commitLocked(records: List<MangaDownload>) {
        val sorted = records.sortedByDescending { it.createdAt }
        _downloads.value = sorted
        appContext?.let { persistRecords(it, sorted) }
    }

    private fun readRecords(context: Context): List<MangaDownload> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_RECORDS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    runCatching { array.optJSONObject(index)?.toDownload() }
                        .getOrNull()
                        ?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persistRecords(context: Context, records: List<MangaDownload>) {
        val array = JSONArray()
        records.forEach { array.put(it.toJson()) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_RECORDS, array.toString()).apply()
    }

    private fun MangaDownload.toJson() = JSONObject().apply {
        put("id", id)
        put("sourceKey", sourceKey)
        put("sourceName", sourceName)
        put("sourcePackage", sourcePackage)
        put("sourceBaseUrl", sourceBaseUrl)
        put("sourceLanguage", sourceLanguage)
        put("mangaUrl", mangaUrl)
        put("mangaTitle", mangaTitle)
        put("mangaPosterUrl", mangaPosterUrl)
        put("chapterUrl", chapterUrl)
        put("chapterName", chapterName)
        put("chapterNumber", chapterNumber.toDouble())
        put("scanlator", scanlator)
        put("chapterUploadDate", chapterUploadDate)
        put("status", status.name)
        put("progress", progress)
        put("downloadedPages", downloadedPages)
        put("totalPages", totalPages)
        put("totalBytes", totalBytes)
        put("localPath", localPath)
        put("archivePath", archivePath)
        put("error", error)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    private fun JSONObject.toDownload(): MangaDownload? {
        val id = optString("id")
        val chapterUrl = optString("chapterUrl")
        if (id.isBlank() || chapterUrl.isBlank()) return null
        val status = runCatching {
            MangaDownload.Status.valueOf(optString("status"))
        }.getOrDefault(MangaDownload.Status.ERROR)
        return MangaDownload(
            id = id,
            sourceKey = optString("sourceKey"),
            sourceName = optString("sourceName"),
            sourcePackage = optString("sourcePackage"),
            sourceBaseUrl = optString("sourceBaseUrl"),
            sourceLanguage = optString("sourceLanguage"),
            mangaUrl = optString("mangaUrl"),
            mangaTitle = optString("mangaTitle"),
            mangaPosterUrl = optNullableString("mangaPosterUrl"),
            chapterUrl = chapterUrl,
            chapterName = optString("chapterName"),
            chapterNumber = optDouble("chapterNumber", -1.0).toFloat(),
            scanlator = optNullableString("scanlator"),
            chapterUploadDate = optLong("chapterUploadDate"),
            status = status,
            progress = optInt("progress"),
            downloadedPages = optInt("downloadedPages"),
            totalPages = optInt("totalPages"),
            totalBytes = optLong("totalBytes"),
            localPath = optNullableString("localPath"),
            archivePath = optNullableString("archivePath"),
            error = optNullableString("error"),
            createdAt = optLong("createdAt", System.currentTimeMillis()),
            updatedAt = optLong("updatedAt", System.currentTimeMillis()),
        )
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    private fun safeName(value: String): String = value
        .replace(Regex("[^\\p{L}\\p{N}._ -]+"), "_")
        .trim()
        .take(80)
        .trim('.', ' ')

    private fun xmlEscape(value: String): String = buildString {
        value.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '\"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(char)
            }
        }
    }

    private fun deleteFileOrDirectory(path: String) {
        val file = File(path)
        if (file.isDirectory) file.deleteRecursively() else file.delete()
    }

    private fun requireContext(): Context = appContext
        ?: throw IllegalStateException("MangaDownloadManager.initialize çağrılmadı")

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "avif", "img")
}
