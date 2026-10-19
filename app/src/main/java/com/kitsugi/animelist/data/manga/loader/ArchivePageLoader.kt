package com.kitsugi.animelist.data.manga.loader

import android.content.Context
import java.io.File
import java.util.zip.ZipFile

/**
 * Yerel ZIP/CBZ arşiv dosyasından manga sayfalarını okur.
 * RAR desteği ayrı bir native kütüphane gerektirdiği için bu versiyonda
 * ZIP ve CBZ (ZIP tabanlı) desteklenir; RAR dosyaları boş liste döner.
 */
class ArchivePageLoader(private val context: Context, private val archiveFile: File) {

    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")

    /** Arşiv içindeki sayfa resimlerinin giriş adlarını (entry name) sıralı olarak döner. */
    fun loadPageList(): List<String> {
        return if (isZipOrCbz()) loadZipPages() else emptyList()
    }

    private fun isZipOrCbz(): Boolean {
        val ext = archiveFile.extension.lowercase()
        return ext == "zip" || ext == "cbz"
    }

    private fun loadZipPages(): List<String> {
        val pages = mutableListOf<String>()
        ZipFile(archiveFile).use { zipFile ->
            val entries = zipFile.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.isDirectory) {
                    val name = entry.name
                    val ext = name.substringAfterLast('.').lowercase()
                    if (ext in imageExtensions) {
                        pages.add(name)
                    }
                }
            }
        }
        return pages.sorted()
    }

    /** Belirli bir giriş adının (entry name) resim input stream'ini döner. */
    fun loadPage(entryName: String): java.io.InputStream? {
        return if (isZipOrCbz()) loadZipPage(entryName) else null
    }

    private fun loadZipPage(entryName: String): java.io.InputStream? {
        return runCatching {
            ZipFile(archiveFile).use { zipFile ->
                val entry = zipFile.getEntry(entryName) ?: return@use null
                zipFile.getInputStream(entry).use { stream ->
                    stream.readBytes().inputStream()
                }
            }
        }.getOrNull()
    }
}
