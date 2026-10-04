package com.kitsugi.animelist.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.settings.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

object KitsugiImageDownloadHelper {

    private const val CHANNEL_ID    = "kitsugi_image_downloads"
    private const val CHANNEL_NAME  = "Resim İndirmeleri"
    private const val NOTIF_ID_BASE = 50000

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    fun shareImage(context: Context, url: String, title: String) {
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Resim hazırlanıyor…", Toast.LENGTH_SHORT).show()
            }
            runCatching {
                val cacheDir = File(context.cacheDir, "shared_images").also { it.mkdirs() }
                val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9_]"), "_")
                val imageFile = File(cacheDir, "Kitsugi_${sanitizedTitle}_${System.currentTimeMillis()}.jpg")

                val connection = URL(url).openConnection()
                connection.connectTimeout = 15_000
                connection.readTimeout    = 15_000
                connection.inputStream.use { input ->
                    imageFile.outputStream().use { output -> input.copyTo(output) }
                }

                val contentUri: Uri = FileProvider.getUriForFile(
                    context,
                    "com.kitsugi.animelist.fileprovider",
                    imageFile
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_TEXT, "$title\n$url")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(shareIntent, "Resmi Paylaş")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                withContext(Dispatchers.Main) { context.startActivity(chooser) }

            }.onFailure { e ->
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Resim paylaşılamadı: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun hasWritePermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getRequiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        else emptyArray()

    /**
     * Downloads [url] and saves it to:
     *   1. Custom SAF URI (if configured in settings), or
     *   2. Downloads/Kitsugi/Images/<filename>
     *
     * Shows a rich Android notification with:
     *  - thumbnail large icon (the downloaded image itself)
     *  - meaningful title: "[title] • Resim İndirildi"
     *  - text: filename + file-size
     */
    fun downloadImage(
        context: Context,
        url: String,
        title: String,
        customUriString: String? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val uriToUse = customUriString ?: runCatching {
                SettingsDataStore(context).settingsFlow.first().customImageDownloadUri
            }.getOrDefault("")

            val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9_]"), "_")
            val filename       = "Kitsugi_${sanitizedTitle}_${System.currentTimeMillis()}.jpg"

            // Show "downloading…" notification
            val notifId = (url.hashCode() and 0x7fffffff) + NOTIF_ID_BASE
            showProgressNotification(context, notifId, title, filename)

            // Download bytes
            val bytes: ByteArray? = runCatching {
                val connection = URL(url).openConnection()
                connection.connectTimeout = 15_000
                connection.readTimeout    = 15_000
                connection.inputStream.use { it.readBytes() }
            }.getOrNull()

            if (bytes == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "İndirme başarısız oldu.", Toast.LENGTH_LONG).show()
                }
                cancelNotification(context, notifId)
                return@launch
            }

            val thumbnail: Bitmap? = runCatching {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()

            // 1. Save to custom SAF folder
            if (uriToUse.isNotBlank()) {
                val saved = saveToCustumUri(context, uriToUse, filename, bytes)
                if (saved) {
                    withContext(Dispatchers.Main) {
                        showCompletedNotification(context, notifId, title, filename, bytes.size.toLong(), thumbnail)
                    }
                    return@launch
                }
            }

            // 2. Save to Downloads/Kitsugi/Images
            val imagesDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Kitsugi/Images"
            ).also { it.mkdirs() }

            val imageFile = File(imagesDir, filename)
            try {
                imageFile.writeBytes(bytes)
                withContext(Dispatchers.Main) {
                    showCompletedNotification(context, notifId, title, filename, bytes.size.toLong(), thumbnail)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Dosya kaydedilemedi: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun saveToCustumUri(
        context: Context,
        uriString: String,
        filename: String,
        bytes: ByteArray
    ): Boolean {
        return try {
            val treeUri  = Uri.parse(uriString)
            val pickedDir = DocumentFile.fromTreeUri(context, treeUri)
            if (pickedDir != null && pickedDir.exists() && pickedDir.isDirectory) {
                val imageFile = pickedDir.createFile("image/jpeg", filename)
                if (imageFile != null) {
                    context.contentResolver.openOutputStream(imageFile.uri)?.use { out ->
                        out.write(bytes)
                    }
                    return true
                }
            }
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Galeri resim indirmelerini takip eder."
                    setShowBadge(true)
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    private fun showProgressNotification(
        context: Context,
        notifId: Int,
        title: String,
        filename: String
    ) {
        ensureChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Small icon: use app icon or a system download icon
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("📥 $title")
            .setContentText("İndiriliyor: $filename")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(0, 0, true)       // indeterminate
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        nm.notify(notifId, notif)
    }

    private fun showCompletedNotification(
        context: Context,
        notifId: Int,
        title: String,
        filename: String,
        fileSizeBytes: Long,
        thumbnail: Bitmap?
    ) {
        ensureChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val sizeStr = formatBytes(fileSizeBytes)

        // Intent: open the Downloads folder in Files app
        val openIntent = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pi = PendingIntent.getActivity(
            context, notifId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("✅ $title")
            .setContentText("Kaydedildi • $sizeStr")
            .setSubText("Resim İndirildi")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)

        // Large icon = the downloaded image thumbnail
        if (thumbnail != null) {
            // Scale down to avoid memory issues
            val scaled = scaleBitmap(thumbnail, 256, 256)
            builder.setLargeIcon(scaled)

            // Big picture style: shows the image expanded in the notification shade
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(scaled)
                    .bigLargeIcon(null as Bitmap?)   // hide large icon when expanded
                    .setSummaryText("$title • $sizeStr")
            )
        }

        nm.notify(notifId, builder.build())
    }

    private fun cancelNotification(context: Context, notifId: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(notifId)
    }

    private fun scaleBitmap(src: Bitmap, maxW: Int, maxH: Int): Bitmap {
        val ratio = minOf(maxW.toFloat() / src.width, maxH.toFloat() / src.height)
        if (ratio >= 1f) return src
        return Bitmap.createScaledBitmap(
            src,
            (src.width * ratio).toInt(),
            (src.height * ratio).toInt(),
            true
        )
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val exp   = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        return String.format("%.1f %s", bytes / Math.pow(1024.0, exp.toDouble()), units[exp])
    }
}
