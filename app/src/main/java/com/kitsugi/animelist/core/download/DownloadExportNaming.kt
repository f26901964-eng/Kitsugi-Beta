package com.kitsugi.animelist.core.download

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import java.io.File

/**
 * İndirilen bölümlerin **dışa aktarılan (public) dosya adlarının** tek kaynağı.
 *
 * `AnimeDownloader` bir bölümü indirdiğinde videoyu iki yere yazar:
 *  1. uygulama içi çalışma kopyası → `Download/Kitsugi/Video/<mediaId>/video.mp4`,
 *  2. kullanıcının dosya yöneticisinden de görebildiği dışa aktarım →
 *     `Download/Kitsugi/Video/<Başlık>_Bölüm_N.mp4` ve altyazılar
 *     `Download/Kitsugi/Video|Subtitles/<Başlık>_Bölüm_N.<dil>.<uzantı>`.
 *
 * Bu nesne olmadan ikinci ad grubu yalnızca indirme anında hesaplanıyordu; bir bölüm
 * silindiğinde (tek tek ya da toplu) bu kopyalar diskte kalıyor ve görünmez bir şişme
 * yaratıyordu. Ad üretimi ile temizlik artık aynı fonksiyonlardan beslenir.
 */
object DownloadExportNaming {

    private const val TAG = "DownloadExportNaming"

    /** Dosya adlarında yasak karakterler: \ / : * ? " < > | */
    private val ILLEGAL_CHARS = Regex("[\\\\/:*?\"<>|]")

    private val SUBTITLE_EXTENSIONS = setOf("srt", "vtt", "ass", "ssa", "sub")

    /** `Download/Kitsugi` kök klasörü. */
    fun publicRoot(): File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "Kitsugi"
    )

    /** Başlığı dosya adına uygun hâle getirir (boşsa "Video"). */
    fun sanitizeTitle(title: String): String =
        title.replace(ILLEGAL_CHARS, "_").trim().ifBlank { "Video" }

    /** Ör. `Naruto_Bölüm_12.mp4`; film ise `Naruto.mp4`. */
    fun exportFileName(animeTitle: String, episode: Int): String {
        val cleanTitle = sanitizeTitle(animeTitle)
        val isMovie = episode <= 1 &&
            (animeTitle.contains("Film", ignoreCase = true) || animeTitle.contains("Movie", ignoreCase = true))
        return if (isMovie && !cleanTitle.contains("Bölüm", ignoreCase = true)) {
            "$cleanTitle.mp4"
        } else {
            "${cleanTitle}_Bölüm_${episode}.mp4"
        }
    }

    /** Uzantısız taban ad (`Naruto_Bölüm_12`) — altyazı dosyaları `<taban>.<dil>.<uzantı>` olur. */
    fun exportBaseName(animeTitle: String, episode: Int): String =
        exportFileName(animeTitle, episode).removeSuffix(".mp4")

    /**
     * Bir bölümün dışa aktarılmış kopyalarını (video + yanındaki altyazılar) siler.
     * Uygulama içi `«mediaId»/` klasörüne dokunmaz — orası [com.kitsugi.animelist.core.player.OfflinePlaybackHelper]
     * tarafından yönetilir.
     *
     * @return silinen dosya sayısı
     */
    fun deleteExports(context: Context, animeTitle: String, episode: Int): Int {
        val baseName = exportBaseName(animeTitle, episode)
        val appContext = context.applicationContext

        val roots = buildList {
            add(publicRoot())
            appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { add(File(it, "Kitsugi")) }
        }
        val dirs = roots
            .flatMap { listOf(File(it, "Video"), File(it, "Subtitles")) }
            .filter { it.isDirectory }

        var removed = 0
        val deletedPaths = ArrayList<String>()
        for (dir in dirs) {
            val files = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (file in files) {
                if (!file.isFile) continue
                val isVideoCopy = file.name.equals("$baseName.mp4", ignoreCase = true)
                val isSubtitleCopy = file.name.startsWith("$baseName.", ignoreCase = true) &&
                    file.extension.lowercase() in SUBTITLE_EXTENSIONS
                if (!isVideoCopy && !isSubtitleCopy) continue

                val deleted = runCatching { file.delete() && !file.exists() }.getOrDefault(false)
                if (deleted) {
                    removed++
                    deletedPaths += file.absolutePath
                }
            }
        }

        if (deletedPaths.isNotEmpty()) {
            runCatching {
                MediaScannerConnection.scanFile(appContext, deletedPaths.toTypedArray(), null, null)
            }.onFailure { Log.w(TAG, "MediaScanner güncellemesi başarısız: ${it.message}") }
        }
        Log.i(TAG, "Dışa aktarılan kopyalar silindi: $removed dosya (taban: $baseName)")
        return removed
    }
}
