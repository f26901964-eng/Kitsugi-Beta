package com.kitsugi.animelist.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL

object KitsugiImageDownloadHelper {

    private const val CHANNEL_ID    = "kitsugi_image_downloads"
    private const val CHANNEL_NAME  = "Resim İndirmeleri"
    private const val NOTIF_ID_BASE = 50000

    // ─────────────────────────────────────────────────────────────────────────
    // İndirme index'i — aynı pencerenin/resmin tekrar indirilmesini önler
    // ─────────────────────────────────────────────────────────────────────────
    //
    // İndirilen her resmin URL'si uygulama içi dosyada (filesDir) tutulur:
    //   kitsugi_downloaded_images.json  →  { url: { fileName, customUri, timestamp } }
    //
    // Böylece:
    //  • Aynı URL ikinci kez indirilmek istendiğinde engellenir ("zaten indirilmiş").
    //  • Kullanıcı dosyayı İndirmeler ekranından silerse index'ten de düşülür,
    //    böylece isterse resmi tekrar indirebilir.
    //  • Galeri arayüzü bu index'i StateFlow ile takip edip "indirildi" rozeti gösterir.
    private const val INDEX_FILE_NAME = "kitsugi_downloaded_images.json"

    private data class DownloadRecord(
        val fileName: String,
        val customUri: String = "",
        val timestamp: Long = 0L
    )

    private val indexLock = Any()

    @Volatile
    private var indexCache: MutableMap<String, DownloadRecord>? = null

    private val inFlightLock = Any()
    private val inFlightDownloads = mutableSetOf<String>()

    private val _downloadedUrls = MutableStateFlow<Set<String>>(emptySet())

    /** İndirilmiş (ve hâlâ diskte duran) resim URL'leri. Galeri arayüzü bunu dinler. */
    val downloadedUrls: StateFlow<Set<String>> = _downloadedUrls

    private fun indexFile(context: Context): File = File(context.filesDir, INDEX_FILE_NAME)

    private fun defaultImagesDir(): File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "Kitsugi/Images"
    )

    private fun legacyImagesDir(): File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "Kitsugi"
    )

    private fun loadIndex(context: Context): MutableMap<String, DownloadRecord> {
        indexCache?.let { return it }
        return synchronized(indexLock) {
            indexCache?.let { return it }
            val map = mutableMapOf<String, DownloadRecord>()
            try {
                val f = indexFile(context)
                if (f.exists()) {
                    val json = JSONObject(f.readText())
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val url = keys.next()
                        val obj = json.optJSONObject(url) ?: continue
                        val fileName = obj.optString("fileName").trim()
                        if (fileName.isBlank()) continue
                        map[url] = DownloadRecord(
                            fileName = fileName,
                            customUri = obj.optString("customUri", ""),
                            timestamp = obj.optLong("timestamp", 0L)
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            indexCache = map
            map
        }
    }

    private fun saveIndex(context: Context, map: Map<String, DownloadRecord>) {
        synchronized(indexLock) {
            indexCache = map.toMutableMap()
            try {
                val json = JSONObject()
                map.forEach { (url, rec) ->
                    json.put(url, JSONObject().apply {
                        put("fileName", rec.fileName)
                        put("customUri", rec.customUri)
                        put("timestamp", rec.timestamp)
                    })
                }
                indexFile(context).writeText(json.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** Index'teki kaydın dosyası hâlâ diskte mi kontrol eder (SAF konumu dahil). */
    private fun recordFileExists(context: Context, record: DownloadRecord): Boolean {
        return try {
            val custom = record.customUri
            when {
                custom.startsWith("mediastore:") -> {
                    // Android 10+ MediaStore kaydı — URI hâlâ geçerli mi?
                    val uri = Uri.parse(custom.removePrefix("mediastore:"))
                    try {
                        context.contentResolver.query(
                            uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null
                        )?.use { cursor -> cursor.moveToFirst() } ?: false
                    } catch (_: Throwable) {
                        false
                    }
                }
                custom.isNotBlank() -> {
                    // SAF (kullanıcı klasörü) kaydı
                    val dir = DocumentFile.fromTreeUri(context, Uri.parse(custom))
                    dir?.findFile(record.fileName)?.exists() == true
                }
                else -> {
                    File(defaultImagesDir(), record.fileName).exists() ||
                        File(legacyImagesDir(), record.fileName).exists()
                }
            }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Index'i diskten tazeler; dosyası silinmiş (stale) kayıtları temizler ve
     * [downloadedUrls] akışını günceller. Arayüz açıldığında arka planda çağrılır.
     */
    fun refreshDownloadedUrls(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val appContext = context.applicationContext
            val index = loadIndex(appContext)
            val valid = mutableMapOf<String, DownloadRecord>()
            var changed = false
            index.forEach { (url, rec) ->
                if (recordFileExists(appContext, rec)) {
                    valid[url] = rec
                } else {
                    changed = true
                }
            }
            if (changed) saveIndex(appContext, valid)
            _downloadedUrls.value = valid.keys.toSet()
        }
    }

    /**
     * Verilen URL daha önce indirilmiş ve dosyası hâlâ duruyor mu?
     * (Çağıran IO context'inde olmalıdır — disk kontrolü yapar.)
     */
    fun isImageDownloaded(context: Context, url: String): Boolean {
        if (url.isBlank()) return false
        val appContext = context.applicationContext
        val rec = loadIndex(appContext)[url] ?: return false
        val exists = recordFileExists(appContext, rec)
        if (!exists) {
            // Stale kayıt — dosya silinmiş, index'ten düş
            synchronized(indexLock) {
                val current = indexCache
                if (current != null && current.remove(url) != null) {
                    saveIndex(appContext, current)
                }
            }
            _downloadedUrls.value = _downloadedUrls.value - url
        }
        return exists
    }

    /** İndirme varsayılan konuma kaydedildiyse dosyayı döner; SAF konumunda null döner. */
    fun findDownloadedImageFile(context: Context, url: String): File? {
        val rec = loadIndex(context.applicationContext)[url] ?: return null
        if (rec.customUri.isNotBlank()) return null
        return File(defaultImagesDir(), rec.fileName).takeIf { it.exists() }
            ?: File(legacyImagesDir(), rec.fileName).takeIf { it.exists() }
    }

    /** Başarılı indirme sonrası URL'yi index'e işler. */
    fun markImageDownloaded(context: Context, url: String, fileName: String, customUri: String) {
        if (url.isBlank() || fileName.isBlank()) return
        val appContext = context.applicationContext
        synchronized(indexLock) {
            val current = indexCache ?: loadIndex(appContext)
            current[url] = DownloadRecord(fileName, customUri, System.currentTimeMillis())
            saveIndex(appContext, current)
        }
        _downloadedUrls.value = _downloadedUrls.value + url
    }

    /**
     * İndirmeler ekranından silinen bir dosya için index kaydını düşürür;
     * böylece kullanıcı isterse aynı resmi tekrar indirebilir.
     */
    fun unmarkImageDownloadedByFileName(context: Context, fileName: String) {
        if (fileName.isBlank()) return
        val appContext = context.applicationContext
        synchronized(indexLock) {
            val current = indexCache ?: return
            val staleKeys = current.filterValues { it.fileName == fileName }.keys
            if (staleKeys.isEmpty()) return
            staleKeys.forEach { current.remove(it) }
            saveIndex(appContext, current)
            _downloadedUrls.value = _downloadedUrls.value - staleKeys
        }
    }

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
            // Yerel dosya (örn. İndirilenler galerisinden açılan) tekrar indirilmez
            if (url.startsWith("file://") || url.startsWith("content://")) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Bu resim zaten cihazda kayıtlı.", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            // Daha önce indirilmiş içerik tekrar indirilmez
            if (isImageDownloaded(context, url)) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Bu resim zaten indirilmiş.", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            // Çökme raporu için iz (sessiz ölümlerde "ne yapıyordu" sorusunun cevabı)
            try {
                com.kitsugi.animelist.core.diagnostics.KitsugiSessionSupervisor
                    .noteAction("resim indirme BAŞLADI: ${title.take(60)}")
            } catch (_: Throwable) {}

            // Aynı anda aynı URL için ikinci istek engellenir (çift dokunuş vb.)
            val alreadyInFlight = synchronized(inFlightLock) { !inFlightDownloads.add(url) }
            if (alreadyInFlight) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Bu resim zaten indiriliyor.", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            try {
                val uriToUse = customUriString ?: runCatching {
                    SettingsDataStore(context).settingsFlow.first().customImageDownloadUri
                }.getOrDefault("")

                // Unicode harfleri koru (Türkçe/Japonca içerik adları düzgün kalsın)
                val sanitizedTitle = title
                    .replace(Regex("[^\\p{L}\\p{N}_]"), "_")
                    .replace(Regex("_+"), "_")
                    .trim('_')
                val filename       = "Kitsugi_${sanitizedTitle}_${System.currentTimeMillis()}.jpg"

                // Show "downloading…" notification
                val notifId = (url.hashCode() and 0x7fffffff) + NOTIF_ID_BASE
                showProgressNotification(context, notifId, title, filename)

                // Download bytes — MAKSİMUM 48 MB (devasa görseller bellek tüketip
                // LMKD/OOM ile süreci sessizce öldürüyordu: "resim indirirken pat diye kapanma")
                val maxBytes = 48L * 1024 * 1024
                val bytes: ByteArray? = runCatching {
                    val connection = URL(url).openConnection()
                    connection.connectTimeout = 15_000
                    connection.readTimeout    = 15_000
                    val declared = connection.contentLengthLong
                    if (declared > maxBytes) error("Görsel çok büyük ($declared bayt)")
                    val data = connection.inputStream.use { it.readBytes() }
                    if (data.size > maxBytes) error("Görsel çok büyük (${data.size} bayt)")
                    data
                }.getOrNull()

                if (bytes == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "İndirme başarısız oldu.", Toast.LENGTH_LONG).show()
                    }
                    cancelNotification(context, notifId)
                    return@launch
                }

                // Bildirim simgesi için KÜÇÜLTÜLMÜŞ bitmap — tam boy decode (ör. 4000x6000 JPEG)
                // 96 MB'a kadar bellek istiyor ve süreci OOM ile öldürebiliyordu.
                val thumbnail: Bitmap? = decodeSampledThumbnail(bytes)

                // 1. Save to custom SAF folder
                if (uriToUse.isNotBlank()) {
                    val saved = saveToCustumUri(context, uriToUse, filename, bytes)
                    if (saved) {
                        markImageDownloaded(context, url, filename, uriToUse)
                        withContext(Dispatchers.Main) {
                            showCompletedNotification(context, notifId, title, filename, bytes.size.toLong(), thumbnail)
                        }
                        return@launch
                    }
                }

                // 2. Android 10+ → MediaStore ile İndirilenler/Kitsugi/Images (izin gerekmez,
                //    dosya galeri uygulamalarında ve dosya yöneticilerinde görünür)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val savedUri = saveImageViaMediaStore(context, filename, bytes)
                    if (savedUri != null) {
                        markImageDownloaded(context, url, filename, "mediastore:$savedUri")
                        try {
                            com.kitsugi.animelist.core.diagnostics.KitsugiSessionSupervisor
                                .noteAction("resim indirme TAMAM (MediaStore): $filename")
                        } catch (_: Throwable) {}
                        withContext(Dispatchers.Main) {
                            showCompletedNotification(context, notifId, title, filename, bytes.size.toLong(), thumbnail)
                        }
                        return@launch
                    }
                }

                // 3. Android 9 ve altı (veya MediaStore başarısız olduysa) → doğrudan dosya
                val imagesDir = defaultImagesDir().also { it.mkdirs() }

                val imageFile = File(imagesDir, filename)
                try {
                    imageFile.writeBytes(bytes)
                    markImageDownloaded(context, url, filename, "")
                    try {
                        com.kitsugi.animelist.core.diagnostics.KitsugiSessionSupervisor
                            .noteAction("resim indirme TAMAM (dosya): $filename")
                    } catch (_: Throwable) {}
                    withContext(Dispatchers.Main) {
                        showCompletedNotification(context, notifId, title, filename, bytes.size.toLong(), thumbnail)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Dosya kaydedilemedi: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            } finally {
                synchronized(inFlightLock) { inFlightDownloads.remove(url) }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Android 10+ için MediaStore tabanlı görsel kaydı. Başarılıysa content:// URI döner. */
    private fun saveImageViaMediaStore(context: Context, filename: String, bytes: ByteArray): Uri? {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Kitsugi/Images")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            val written = try {
                resolver.openOutputStream(uri)?.use { out ->
                    out.write(bytes)
                    out.flush()
                }
                true
            } catch (_: Throwable) {
                false
            }
            if (!written) {
                try { resolver.delete(uri, null, null) } catch (_: Throwable) {}
                return null
            }
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            try { resolver.update(uri, done, null, null) } catch (_: Throwable) {}
            uri
        } catch (_: Throwable) {
            null
        }
    }

    /** Bildirim simgesi için bellek dostu (örneklemeli) bitmap decode. */
    private fun decodeSampledThumbnail(bytes: ByteArray, targetPx: Int = 512): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth > 0 && bounds.outHeight > 0 &&
                (bounds.outWidth / (sample * 2)) >= targetPx &&
                (bounds.outHeight / (sample * 2)) >= targetPx
            ) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (_: Throwable) {
            null
        }
    }

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
