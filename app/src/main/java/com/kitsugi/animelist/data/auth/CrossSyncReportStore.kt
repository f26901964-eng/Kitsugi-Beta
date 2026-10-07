package com.kitsugi.animelist.data.auth

import android.content.ContentValues
import android.content.Context
import androidx.annotation.RequiresApi
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.kitsugi.animelist.model.CrossSyncProgressState
import java.io.File
import java.io.IOException

/** Persists each sync report privately and, on scoped-storage Android, in Downloads. */
object CrossSyncReportStore {
    private const val REPORT_DIRECTORY = "cross_sync_reports"
    private const val LATEST_REPORT = "Kitsugi_CrossSync_Report_Latest.txt"

    /** Returns a user-facing location, or null if even the private copy could not be written. */
    fun save(context: Context, state: CrossSyncProgressState): String? {
        val timestamp = state.finishedAt ?: System.currentTimeMillis()
        val filename = CrossSyncReportFormatter.fileName(timestamp)
        val report = CrossSyncReportFormatter.format(state)

        val privateDirectory = File(context.filesDir, REPORT_DIRECTORY)
        if (!privateDirectory.exists() && !privateDirectory.mkdirs()) return null
        val privateFile = File(privateDirectory, filename)
        return try {
            privateFile.writeText(report, Charsets.UTF_8)
            File(privateDirectory, LATEST_REPORT).writeText(report, Charsets.UTF_8)

            val publicLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching { saveToDownloads(context, filename, report) }.getOrNull()
            } else {
                saveToAppExternalDocuments(context, filename, report)
            }
            publicLocation ?: "Uygulama verileri/$REPORT_DIRECTORY/$filename"
        } catch (_: Exception) {
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToDownloads(context: Context, filename: String, report: String): String? {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/Kitsugi/CrossSyncReports"
            )
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("İndirilenler kaydı oluşturulamadı")
        try {
            context.contentResolver.openOutputStream(uri, "w")?.bufferedWriter(Charsets.UTF_8)?.use {
                it.write(report)
            } ?: throw IOException("İndirilenler dosyası açılamadı")
            val published = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            context.contentResolver.update(uri, published, null, null)
            return "Downloads/Kitsugi/CrossSyncReports/$filename"
        } catch (error: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw error
        }
    }

    private fun saveToAppExternalDocuments(context: Context, filename: String, report: String): String? {
        return try {
            val base = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return null
            val directory = File(base, "Kitsugi/CrossSyncReports")
            if (!directory.exists() && !directory.mkdirs()) return null
            File(directory, filename).writeText(report, Charsets.UTF_8)
            "Android/data/${context.packageName}/files/Documents/Kitsugi/CrossSyncReports/$filename"
        } catch (_: Exception) {
            null
        }
    }
}
