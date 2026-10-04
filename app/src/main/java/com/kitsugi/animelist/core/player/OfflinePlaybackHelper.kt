package com.kitsugi.animelist.core.player

import android.content.Context
import android.util.Log
import java.io.File

/**
 * T1.14 – OfflinePlaybackHelper
 *
 * Yerel depolama alanında indirilen video/altyazı dosyalarını yönetir ve
 * bu dosyaları oynatmak için gerekli parametreleri hazırlar.
 *
 * ### Sorumluluğu
 * - İndirilen medya dosyalarını önbellek dizininde listeler.
 * - Bir lokal dosya URI'sini `PlayerEngine.prepare()` için uygun parametrelere
 *   çevirir.
 * - Silinmiş veya bozuk dosyaları tespit eder.
 *
 * ### Dizin Yapısı
 * ```
 * <cacheDir>/Kitsugi_downloads/
 *   <mediaId>/
 *     video.mp4  (veya .mkv, .m3u8 chunk dosyaları vb.)
 *     subs/
 *       tr.srt
 *       en.vtt
 *     metadata.json
 * ```
 */
object OfflinePlaybackHelper {

    private const val TAG = "OfflinePlaybackHelper"
    private const val DOWNLOADS_DIR = "Kitsugi_downloads"

    // ── Data classes ──────────────────────────────────────────────────────────

    /**
     * Oynatmaya hazır yerel medya tanımı.
     */
    data class LocalMedia(
        /** İçerik ID'si (AniList ID veya özel) */
        val mediaId: String,
        /** İnsan okunabilir başlık */
        val title: String,
        /** Yerel video dosyası URI'si (file:// prefix'li) */
        val videoUri: String,
        /** Varsa indirilen altyazı listesi */
        val subtitles: List<LocalSubtitle>,
        /** Video dosyası boyutu (bytes) */
        val fileSizeBytes: Long,
        /** İndirme tarihi (epoch ms) */
        val downloadedAtMs: Long,
        /** Toplam süre tahmini ms (metadata'dan okunur, 0 = bilinmiyor) */
        val durationMs: Long = 0L,
    )

    data class LocalSubtitle(
        val language: String,
        val label: String,
        val uri: String,  // file:// prefix'li
    )

    /**
     * Metadata dosyasında saklanan alan (JSON formatı).
     */
    data class DownloadMetadata(
        val mediaId: String = "",
        val title: String = "",
        val durationMs: Long = 0L,
        val downloadedAtMs: Long = 0L,
    )

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * İndirme kök dizinini döner.
     * Dizin yoksa oluşturulur.
     */
    fun getDownloadsDir(context: Context): File {
        val publicDir = File(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
            "Kitsugi/Video"
        )
        if (publicDir.exists() || publicDir.mkdirs()) {
            return publicDir
        }
        val extDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
        if (extDir != null) {
            val fallback = File(extDir, "Kitsugi/Video").also { it.mkdirs() }
            if (fallback.exists()) return fallback
        }
        return File(context.filesDir, DOWNLOADS_DIR).also { it.mkdirs() }
    }

    /**
     * Tüm indirilen medyaları listeler.
     * Bozuk veya eksik video dosyaları silindi/geçersiz olduğunda atlanır.
     */
    fun listDownloadedMedia(context: Context): List<LocalMedia> {
        val primaryRoot = getDownloadsDir(context)
        val legacyCacheRoot = File(context.cacheDir, DOWNLOADS_DIR)

        val primaryDirs = if (primaryRoot.exists()) primaryRoot.listFiles()?.filter { it.isDirectory }.orEmpty() else emptyList()
        val legacyDirs = if (legacyCacheRoot.exists()) legacyCacheRoot.listFiles()?.filter { it.isDirectory }.orEmpty() else emptyList()

        val allDirs = (primaryDirs + legacyDirs).distinctBy { it.name }
        return allDirs
            .mapNotNull { dir -> readLocalMedia(dir) }
            .sortedByDescending { it.downloadedAtMs }
    }

    /**
     * Belirli bir [mediaId] için yerel medyayı okur.
     * Dosya yoksa `null` döner.
     */
    fun getLocalMedia(context: Context, mediaId: String): LocalMedia? {
        val primaryDir = File(getDownloadsDir(context), mediaId)
        if (primaryDir.exists()) return readLocalMedia(primaryDir)

        val legacyDir = File(File(context.cacheDir, DOWNLOADS_DIR), mediaId)
        if (legacyDir.exists()) return readLocalMedia(legacyDir)

        return null
    }

    /**
     * Yerel medyayı `PlayerEngine.prepare()` için hazır parametrelere çevirir.
     *
     * @return `null` eğer video dosyası artık mevcut değilse.
     */
    fun buildPlayerParams(media: LocalMedia): PlayerParams? {
        val videoFile = File(media.videoUri.removePrefix("file://"))
        if (!videoFile.exists() || !videoFile.isFile) {
            Log.w(TAG, "Video file missing for mediaId=${media.mediaId}: ${media.videoUri}")
            return null
        }
        return PlayerParams(
            videoUri   = media.videoUri,
            audioUri   = null,
            headers    = emptyMap(),
            subtitles  = media.subtitles.map {
                SubtitleInput(url = it.uri, lang = it.language, name = it.label)
            },
            title      = media.title,
            isOffline  = true,
        )
    }

    /**
     * Bir indirme dizinini tamamen siler.
     *
     * @return Silme başarılıysa `true`.
     */
    fun deleteDownload(context: Context, mediaId: String): Boolean {
        val dir = File(getDownloadsDir(context), mediaId)
        if (!dir.exists()) return false
        return dir.deleteRecursively().also { success ->
            Log.i(TAG, "Deleted download mediaId=$mediaId success=$success")
        }
    }

    /**
     * Toplam kullanılan disk alanını bytes cinsinden döner.
     */
    fun totalDiskUsageBytes(context: Context): Long {
        val root = getDownloadsDir(context)
        return root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * Belirli bir medya için indirme ilerleme dosyasının varlığını kontrol eder.
     * Yarım kalmış indirme = `.partial` dosyası mevcuttur.
     */
    fun isPartialDownload(context: Context, mediaId: String): Boolean {
        val dir = File(getDownloadsDir(context), mediaId)
        return dir.listFiles()?.any { it.extension == "partial" } ?: false
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun readLocalMedia(dir: File): LocalMedia? {
        val videoFile = findVideoFile(dir) ?: run {
            Log.d(TAG, "No video file in ${dir.name}")
            return null
        }
        val metadata = readMetadata(dir)
        val subtitles = readSubtitles(dir)

        return LocalMedia(
            mediaId        = dir.name,
            title          = metadata?.title?.takeIf { it.isNotBlank() } ?: dir.name,
            videoUri       = "file://${videoFile.absolutePath}",
            subtitles      = subtitles,
            fileSizeBytes  = videoFile.length(),
            downloadedAtMs = metadata?.downloadedAtMs ?: videoFile.lastModified(),
            durationMs     = metadata?.durationMs ?: 0L,
        )
    }

    private fun findVideoFile(dir: File): File? {
        val videoExtensions = setOf("mp4", "mkv", "avi", "webm", "ts", "m2ts")
        return dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in videoExtensions }
            ?.maxByOrNull { it.length() } // Biggest file is likely the video
    }

    private fun readSubtitles(dir: File): List<LocalSubtitle> {
        val subsDir = File(dir, "subs")
        if (!subsDir.exists()) return emptyList()
        val subExtensions = setOf("srt", "vtt", "ass", "ssa")
        return subsDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in subExtensions }
            ?.map { file ->
                val name = file.nameWithoutExtension
                val lang = name.substringBefore("_", "en")
                val rest = name.substringAfter("_", "").replace("_", " ").trim().replace(Regex("\\s+"), " ")
                val label = if (rest.isNotEmpty()) rest else when (lang) {
                    "tr" -> "Türkçe"
                    "en" -> "İngilizce"
                    "ja" -> "Japonca"
                    else -> lang.uppercase()
                }
                LocalSubtitle(language = lang, label = label, uri = "file://${file.absolutePath}")
            }
            ?: emptyList()
    }

    private fun readMetadata(dir: File): DownloadMetadata? {
        val metaFile = File(dir, "metadata.json")
        if (!metaFile.exists()) return null
        return try {
            val json = metaFile.readText()
            // Simple manual parse – avoid heavy Gson dependency in this layer
            DownloadMetadata(
                mediaId       = extractJsonString(json, "mediaId") ?: dir.name,
                title         = extractJsonString(json, "title") ?: "",
                durationMs    = extractJsonLong(json, "durationMs") ?: 0L,
                downloadedAtMs = extractJsonLong(json, "downloadedAtMs") ?: 0L,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse metadata for ${dir.name}: ${e.message}")
            null
        }
    }

    /** Minimal JSON string field extractor (no external dependency). */
    private fun extractJsonString(json: String, key: String): String? {
        val regex = Regex(""""$key"\s*:\s*"([^"]*)"""")
        return regex.find(json)?.groupValues?.getOrNull(1)
    }

    /** Minimal JSON long field extractor. */
    private fun extractJsonLong(json: String, key: String): Long? {
        val regex = Regex(""""$key"\s*:\s*(\d+)""")
        return regex.find(json)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    // ── PlayerParams ─────────────────────────────────────────────────────────

    /**
     * Oynatıcıya iletilecek hazır parametre paketi.
     * [PlayerEngine.prepare] imzasıyla uyumludur.
     */
    data class PlayerParams(
        val videoUri: String,
        val audioUri: String?,
        val headers: Map<String, String>,
        val subtitles: List<SubtitleInput>,
        val title: String,
        val isOffline: Boolean,
    )
}
