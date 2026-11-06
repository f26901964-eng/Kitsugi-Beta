package com.kitsugi.animelist.data.manga.download

import androidx.compose.runtime.Immutable
import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.data.manga.MangaChapterGrouping
import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.stableSourceKey
import java.security.MessageDigest

@Immutable
data class MangaDownload(
    val id: String,
    val sourceKey: String,
    val sourceName: String,
    val sourcePackage: String,
    val sourceBaseUrl: String,
    val sourceLanguage: String,
    val mangaUrl: String,
    val mangaTitle: String,
    val mangaPosterUrl: String?,
    val chapterUrl: String,
    val chapterName: String,
    val chapterNumber: Float,
    val scanlator: String?,
    val chapterUploadDate: Long,
    val status: Status = Status.QUEUE,
    val progress: Int = 0,
    val downloadedPages: Int = 0,
    val totalPages: Int = 0,
    val totalBytes: Long = 0L,
    /** Completed page directory or CBZ file. While running, this is the page work directory. */
    val localPath: String? = null,
    /** Stable manga/source/chapter folder. Kept separately so CBZ and pages never lose their parent. */
    val chapterPath: String? = null,
    val archivePath: String? = null,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    enum class Status {
        QUEUE,
        DOWNLOADING,
        PAUSED,
        COMPLETED,
        ERROR,
    }

    val isActive: Boolean
        get() = status == Status.QUEUE || status == Status.DOWNLOADING

    companion object {
        fun idFor(sourceKey: String, chapterUrl: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("$sourceKey|$chapterUrl".toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }.take(32)
        }

        fun from(
            source: MangaSource,
            details: MangaDetails,
            chapter: MangaChapter,
        ): MangaDownload {
            val sourceKey = source.stableSourceKey()
            return MangaDownload(
                id = idFor(sourceKey, chapter.url),
                sourceKey = sourceKey,
                sourceName = source.name,
                sourcePackage = source.pkgName,
                sourceBaseUrl = source.baseUrl,
                sourceLanguage = source.lang,
                mangaUrl = details.url,
                mangaTitle = details.title,
                mangaPosterUrl = details.thumbnailUrl,
                chapterUrl = chapter.url,
                chapterName = chapter.name,
                chapterNumber = MangaChapterGrouping.numberOf(details.title, chapter)?.toFloat()
                    ?: chapter.chapterNumber,
                scanlator = chapter.scanlator,
                chapterUploadDate = chapter.uploadDate,
            )
        }
    }
}
