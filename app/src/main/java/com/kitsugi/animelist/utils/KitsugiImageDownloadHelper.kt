package com.kitsugi.animelist.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.kitsugi.animelist.R
import com.kitsugi.animelist.core.network.KitsugiHttpClient
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

/**
 * İndirilen/paylaşılan görselin gerçek formatı.
 *
 * @param extension  Dosya uzantısı (ör. `gif`, `png`, `webp`)
 * @param mimeType   Alıcı uygulamaların süzdüğü MIME tipi (ör. `image/gif`)
 * @param isAnimated Hareketli içerik mi? (GIF / animasyonlu WebP / APNG)
 *                   Hareketli içerik ASLA statik bir forma dönüştürülmez —
 *                   "GIF indirirken animasyonun kaybolması" bu dönüşümden doğuyordu.
 * @param needsTranscode Paylaşan uygulamanın büyük olasılıkla çözemediği format mı?
 *                   (HEIF/HEIC, AVIF, BMP, TIFF) → paylaşmadan önce JPEG/PNG'ye çevrilir.
 */
data class ImageFormat(
    val extension: String,
    val mimeType: String,
    val isAnimated: Boolean = false,
    val needsTranscode: Boolean = false
)

/**
 * Görselin ORİJİNAL formatını baytlardan (magic number) tespit eder.
 * GIF hareketli kalsın diye GIF olarak, PNG şeffaflığı korunsun diye PNG olarak kaydedilir.
 * Tanınmazsa URL uzantısına / HTTP `Content-Type` başlığına, o da yoksa JPEG'e düşer.
 *
 * Desteklenen imzalar: GIF87a/89a (animasyon tespiti için GraphicsControlExtension +
 * çerçeve sayısı da taranır), PNG (+APNG: `acTL`), JPEG (+MPO), WebP (VP8/VP8L/VP8X animasyon),
 * AVIF/AVIS, HEIF/HEIC, BMP, TIFF.
 */
internal fun detectImageFormat(bytes: ByteArray, url: String = "", contentType: String? = null): ImageFormat {
    fun startsWith(vararg sig: Int, offset: Int = 0): Boolean {
        if (bytes.size < offset + sig.size) return false
        return sig.indices.all { i -> (bytes[offset + i].toInt() and 0xFF) == sig[i] }
    }
    fun ascii(text: String, offset: Int = 0): Boolean {
        if (bytes.size < offset + text.length) return false
        return text.indices.all { i -> bytes[offset + i] == text[i].code.toByte() }
    }

    // GIF: animasyon, en az iki GraphicsControlExtension (0x21 0xF9) bloklu çerçeve demektir.
    // Devasa GIF'lerde taramayı sınırlamak için ilk 4 MB yeter (ilk kareler baştadır).
    fun gifFrameCount(): Int {
        var count = 0
        val limit = minOf(bytes.size - 3, 4 * 1024 * 1024)
        var i = 0
        while (i <= limit && count < 3) {
            if (bytes[i].toInt() and 0xFF == 0x21 && bytes[i + 1].toInt() and 0xFF == 0xF9) count++
            i++
        }
        return count
    }

    // WebP VP8X kutusunda (animasyonlu biçim) 2. bayt 'A' bitini taşır.
    fun webpAnimated(): Boolean {
        if (!ascii("RIFF") || !ascii("WEBP", 8)) return false
        if (bytes.size < 30) return false
        if (!ascii("VP8X", 12)) return false
        return bytes[20].toInt() and 0x02 != 0
    }

    // PNG uzantı zincirinde acTL varsa APNG'dir (hareketli PNG). Chunk yapısı gezilir:
    // [4 bayt uzunluk][4 bayt tür][veri][4 bayt CRC] — ham veri içinde "acTL" aramak
    // IDAT belleğinde yanlış pozitife yol açabilirdi.
    fun pngHasAcTL(): Boolean {
        if (bytes.size < 24 || !startsWith(0x89, 0x50, 0x4E, 0x47)) return false
        var pos = 8 // imza (8 bayt) atlanır
        while (pos + 8 <= bytes.size) {
            val length = ((bytes[pos].toInt() and 0xFF) shl 24) or
                ((bytes[pos + 1].toInt() and 0xFF) shl 16) or
                ((bytes[pos + 2].toInt() and 0xFF) shl 8) or
                (bytes[pos + 3].toInt() and 0xFF)
            if (length < 0) return false
            if (bytes[pos + 4] == 'a'.code.toByte() && bytes[pos + 5] == 'c'.code.toByte() &&
                bytes[pos + 6] == 'T'.code.toByte() && bytes[pos + 7] == 'L'.code.toByte()
            ) return true
            if (bytes[pos + 4] == 'I'.code.toByte() && bytes[pos + 5] == 'E'.code.toByte() &&
                bytes[pos + 6] == 'N'.code.toByte() && bytes[pos + 7] == 'D'.code.toByte()
            ) return false // dosya sonu — acTL yok
            pos += 12 + length
        }
        return false
    }

    fun heifBrand(): Boolean =
        ascii("ftyp", 4) && bytes.size >= 12 && when {
            ascii("mif1", 8) || ascii("msf1", 8) || ascii("heic", 8) || ascii("heix", 8) ||
                ascii("heim", 8) || ascii("heis", 8) || ascii("hevc", 8) || ascii("hevx", 8) ||
                ascii("hevm", 8) || ascii("avc1", 8) || ascii("avcs", 8) -> true
            else -> false
        }

    val detected: ImageFormat? = when {
        ascii("GIF87a") || ascii("GIF89a") ->
            ImageFormat("gif", "image/gif", isAnimated = gifFrameCount() >= 2)
        startsWith(0x89, 0x50, 0x4E, 0x47) -> {
            val animatedPng = pngHasAcTL()
            ImageFormat("png", "image/png", isAnimated = animatedPng)
        }
        startsWith(0xFF, 0xD8, 0xFF) -> ImageFormat("jpg", "image/jpeg")
        ascii("RIFF") && ascii("WEBP", 8) ->
            ImageFormat("webp", "image/webp", isAnimated = webpAnimated())
        ascii("ftyp", 4) && (ascii("avif", 8) || ascii("avis", 8)) ->
            ImageFormat("avif", "image/avif", isAnimated = ascii("avis", 8), needsTranscode = true)
        heifBrand() -> ImageFormat("heic", "image/heif", needsTranscode = true)
        ascii("BM") -> ImageFormat("bmp", "image/bmp", needsTranscode = true)
        ascii("II") && startsWith(0x2A, 0x00, offset = 2) ->
            ImageFormat("tiff", "image/tiff-sample", needsTranscode = true)
        ascii("MM") && startsWith(0x00, 0x2A, offset = 2) ->
            ImageFormat("tiff", "image/tiff-sample", needsTranscode = true)
        else -> null
    }
    if (detected != null) return detected

    // İmza tanınmadı → önce HTTP Content-Type, sonra URL uzantısı.
    val mime = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
    fun fromMime(m: String): ImageFormat? = when {
        m == "image/gif" -> ImageFormat("gif", "image/gif")
        m == "image/png" -> ImageFormat("png", "image/png")
        m == "image/jpeg" || m == "image/jpg" || m == "image/pjpeg" -> ImageFormat("jpg", "image/jpeg")
        m == "image/webp" -> ImageFormat("webp", "image/webp")
        m == "image/avif" -> ImageFormat("avif", "image/avif", needsTranscode = true)
        m == "image/heic" || m == "image/heif" -> ImageFormat("heic", "image/heif", needsTranscode = true)
        m == "image/bmp" -> ImageFormat("bmp", "image/bmp", needsTranscode = true)
        m == "image/apng" -> ImageFormat("png", "image/png", isAnimated = true)
        else -> null
    }
    fromMime(mime)?.let { return it }

    val ext = url.substringBefore('?').substringAfterLast('.', "").lowercase()
    return when (ext) {
        "gif" -> ImageFormat("gif", "image/gif")
        "png" -> ImageFormat("png", "image/png")
        "webp" -> ImageFormat("webp", "image/webp")
        "avif" -> ImageFormat("avif", "image/avif", needsTranscode = true)
        "heic", "heif" -> ImageFormat("heic", "image/heif", needsTranscode = true)
        "bmp" -> ImageFormat("bmp", "image/bmp", needsTranscode = true)
        "tif", "tiff" -> ImageFormat("tiff", "image/tiff-sample", needsTranscode = true)
        else -> ImageFormat("jpg", "image/jpeg")
    }
}

/**
 * Paylaşmadan önce alıcı uygulamaların okuyabileceği bir forma indirger.
 *
 * GIF/WebP animasyonu ve PNG şeffaflığı OLDUĞU GİBİ geçer (baytlara dokunulmaz);
 * yalnızca birçok uygulamanın çözemediği HEIF/AVIF/BMP/TIFF yeniden kodlanır.
 * Decode edilemezse (ör. cihaz formatı desteklemiyor) orijinal baytlar döner —
 * paylaşma denemesi tamamen başarısız olmaktansa ham dosya gitmiş olur.
 */
internal fun normalizeBytesForSharing(
    bytes: ByteArray,
    format: ImageFormat
): Pair<ByteArray, ImageFormat> {
    if (!format.needsTranscode) return bytes to format
    return runCatching {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes to format
        // Şeffaflık korunacaksa PNG, değilse JPEG (daha küçük) tercih edilir.
        val hasAlpha = bitmap.hasAlpha()
        val out = java.io.ByteArrayOutputStream()
        val ok = if (hasAlpha) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        } else {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        runCatching { if (!bitmap.isRecycled) bitmap.recycle() }
        if (!ok || out.size() == 0) return bytes to format
        if (hasAlpha) out.toByteArray() to ImageFormat("png", "image/png")
        else out.toByteArray() to ImageFormat("jpg", "image/jpeg")
    }.getOrDefault(bytes to format)
}

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
        if (rec.customUri.isNotBlank() && !rec.customUri.startsWith("mediastore:")) return null
        val filename = rec.fileName
        val picturesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Kitsugi/Images")
        val appExtDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { File(it, "Kitsugi/Images") }
        return File(defaultImagesDir(), filename).takeIf { it.exists() }
            ?: File(legacyImagesDir(), filename).takeIf { it.exists() }
            ?: File(picturesDir, filename).takeIf { it.exists() }
            ?: appExtDir?.let { File(it, filename) }?.takeIf { it.exists() }
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
    // Public API — paylaşma
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Resmi paylaşır. Uygulamanın kendi OkHttp istemcisi kullanılır (User-Agent, Accept,
     * yönlendirme ve timeout'lar dahil), çünkü çoğu CDN'siz ham `URL.openConnection()`
     * isteği 403 ile düşüyordu ve `file://` / `content://` kaynakları hiç paylaşamıyordu.
     *
     * Format kuralları:
     *  • Zaten indirilmiş bir görsel varsa dosyası YENİDEN İNDİRİLMEDEN paylaşılır.
     *  • Animasyonlu GIF / animasyonlu WebP / APNG baytlarına dokunulmadan gönderilir
     *    (animasyon korunur), uzantı + MIME doğru yazılır.
     *  • HEIF/HEIC, AVIF, BMP, TIFF gibi alıcı uygulamaların çoğunun çözemediği
     *    formatlar JPEG/PNG'ye dönüştürülür.
     */
    fun shareImage(context: Context, url: String, title: String) {
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Resim hazırlanıyor…", Toast.LENGTH_SHORT).show()
            }
            val appContext = context.applicationContext
            var shareUri: Uri? = null
            var mimeType = "image/jpeg"
            try {
                // 1) İndirilmiş kopya varsa kaynak odur — ağa hiç dokunmayız.
                val downloaded = findDownloadedImageFile(appContext, url)
                val resolvedFile: File? = downloaded ?: when {
                    url.startsWith("file://") -> runCatching { File(Uri.parse(url).path ?: "") }
                        .getOrNull()?.takeIf { it.exists() }
                    else -> null
                }

                if (resolvedFile != null && resolvedFile.exists()) {
                    val head = readHead(resolvedFile, 32 * 1024)
                    val headFormat = detectImageFormat(head, resolvedFile.name)
                    if (headFormat.needsTranscode) {
                        val out = transcodeForShare(resolvedFile.readBytes(), headFormat)
                        shareUri = writeShareTemp(appContext, title, out.bytes, out.format)
                        mimeType = out.format.mimeType
                    } else {
                        // Yerel dosyayı kopyalamaya gerek yok: FileProvider ile doğrudan ver.
                        shareUri = FileProvider.getUriForFile(
                            appContext, FILEPROVIDER_AUTHORITY, resolvedFile
                        )
                        mimeType = headFormat.mimeType
                    }
                } else {
                    // 2) content:// veya uzak URL → baytları al, biçimi koru.
                    val fetched = fetchImage(appContext, url, MAX_TRANSFER_BYTES)
                        ?: error("Görsel alınamadı")
                    val out = transcodeForShare(fetched.bytes, fetched.format)
                    shareUri = writeShareTemp(appContext, title, out.bytes, out.format)
                    mimeType = out.format.mimeType
                }

                val contentUri = shareUri ?: error("Paylaşım adresi üretilemedi")

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    val caption = if (url.startsWith("http")) "$title\n$url" else title
                    putExtra(Intent.EXTRA_TEXT, caption)
                    putExtra(Intent.EXTRA_TITLE, title)
                    clipData = android.content.ClipData.newRawUri(null, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(shareIntent, "Resmi/GIF'i Paylaş").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                withContext(Dispatchers.Main) { appContext.startActivity(chooser) }

            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                // Ağ hatası: en azından bağlantıyı paylaş — kullanıcı hiçbir şey alamamasın.
                val remote = url.startsWith("http")
                withContext(Dispatchers.Main) {
                    if (remote) {
                        runCatching {
                            val textIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "$title\n$url")
                            }
                            appContext.startActivity(
                                Intent.createChooser(textIntent, "Bağlantıyı Paylaş")
                                    .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                            )
                        }
                        Toast.makeText(
                            appContext,
                            "Görsel indirilemedi, bağlantı paylaşıldı.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            appContext,
                            "Resim paylaşılamadı: ${e.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API — galeriden silme
    // ─────────────────────────────────────────────────────────────────────────

    /** [deleteImageForUrl] sonucu. */
    sealed interface GalleryDeleteOutcome {
        /** Kopya silindi; index ve galeri bildirimleri güncellendi. */
        data object Success : GalleryDeleteOutcome
        /** Silinecek bir şey yok (hiç indirilmemiş ya da dosya zaten gitmiş). */
        data object NothingToDelete : GalleryDeleteOutcome
        /** Sistem onayı gerekiyor (bizim oluşturmadığımız MediaStore kaydı). */
        data class RequiresSystemConsent(val message: String) : GalleryDeleteOutcome
        /** Silme denemesi hata ile sonuçlandı. */
        data class Failed(val message: String) : GalleryDeleteOutcome
    }

    /**
     * Bu görsel için silinebilir bir kopya var mı?
     * Disk eriştiği için IO context'inde çağrılmalıdır (galeri bunu sayfa değişiminde tazeler).
     */
    fun hasDeletableCopy(context: Context, url: String): Boolean {
        if (url.isBlank()) return false
        val appContext = context.applicationContext
        return try {
            when {
                url.startsWith("file://") -> {
                    val path = Uri.parse(url).path
                    !path.isNullOrBlank() && File(path).exists()
                }
                url.startsWith("content://") -> contentUriReadable(appContext, Uri.parse(url))
                else -> {
                    val rec = loadIndex(appContext)[url]
                    rec != null && recordFileExists(appContext, rec)
                }
            }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Onay penceresinde gösterilecek kısa konum etiketi
     * (ör. `İndirilenler/Kitsugi/Images · Kitsugi_Foo.gif`).
     */
    fun describeDeleteTarget(context: Context, url: String): String? {
        if (url.isBlank()) return null
        val appContext = context.applicationContext
        return try {
            when {
                url.startsWith("file://") -> {
                    val f = File(Uri.parse(url).path ?: return null)
                    humanizePath(f) + " · " + f.name
                }
                url.startsWith("content://") -> "Cihaz depolaması"
                else -> {
                    val rec = loadIndex(appContext)[url] ?: return null
                    val location = when {
                        rec.customUri.startsWith("mediastore:") || rec.customUri.isBlank() ->
                            "İndirilenler/Kitsugi/Images"
                        else -> "Seçtiğin klasör"
                    }
                    "$location · ${rec.fileName}"
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Görselin indirilmiş kopyasını (veya galeride açılan yerel dosyanın kendisini) siler.
     *
     * Başarılıysa index kaydı da düşürülür: "indirildi" rozeti kaybolur, kullanıcı
     * isterse aynı resmi tekrar indirebilir ve galeri uygulamaları dosyayı unutup
     * İndirmeler ekranı tazeler.
     */
    suspend fun deleteImageForUrl(context: Context, url: String): GalleryDeleteOutcome =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            if (url.isBlank()) return@withContext GalleryDeleteOutcome.NothingToDelete
            try {
                when {
                    url.startsWith("file://") -> {
                        val path = Uri.parse(url).path
                        if (path.isNullOrBlank()) GalleryDeleteOutcome.NothingToDelete
                        else deleteLocalFile(appContext, File(path))
                    }
                    url.startsWith("content://") -> deleteContentUri(appContext, Uri.parse(url))
                    else -> {
                        val rec = synchronized(indexLock) { loadIndex(appContext)[url] }
                        if (rec == null) {
                            // İndirme index'inde kaydı yok → silinecek bir kopya yok.
                            _downloadedUrls.value = _downloadedUrls.value - url
                            GalleryDeleteOutcome.NothingToDelete
                        } else {
                            deleteDownloadedRecord(appContext, url, rec)
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                GalleryDeleteOutcome.Failed(e.localizedMessage ?: "Bilinmeyen hata")
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
                // Show "downloading…" notification (dosya adı indirme bitince belli olur)
                val notifId = (url.hashCode() and 0x7fffffff) + NOTIF_ID_BASE
                showProgressNotification(context, notifId, title, "Kitsugi_${sanitizedTitle}")

                // Görsel baytları — MAKSİMUM 48 MB (devasa görseller bellek tüketip
                // LMKD/OOM ile süreci sessizce öldürüyordu: "resim indirirken pat diye kapanma").
                // Uygulamanın OkHttp istemcisi kullanılır: CDN'lerin çoğu appsız
                // (UA'sız) isteği 403 ile reddediyordu. Format da aynı geçişte okunur.
                val fetched = fetchImage(context, url, MAX_TRANSFER_BYTES)

                if (fetched == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "İndirme başarısız oldu.", Toast.LENGTH_LONG).show()
                    }
                    cancelNotification(context, notifId)
                    return@launch
                }
                val bytes = fetched.bytes

                // Orijinal format korunur (GIF → .gif, PNG → .png, ...). Hareketli GIF düz JPG'ye çevrilmez.
                val format   = fetched.format
                val filename = "Kitsugi_${sanitizedTitle}_${System.currentTimeMillis()}.${format.extension}"

                // Bildirim simgesi için KÜÇÜLTÜLMÜŞ bitmap — tam boy decode (ör. 4000x6000 JPEG)
                // 96 MB'a kadar bellek istiyor ve süreci OOM ile öldürebiliyordu.
                val thumbnail: Bitmap? = decodeSampledThumbnail(bytes)

                // 1. Save to custom SAF folder
                if (uriToUse.isNotBlank()) {
                    val saved = saveToCustumUri(context, uriToUse, filename, bytes, format.mimeType)
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
                    val savedUri = saveImageViaMediaStore(context, filename, bytes, format.mimeType)
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
                var targetFile = File(defaultImagesDir(), filename)
                var writeSuccess = false
                try {
                    targetFile.parentFile?.mkdirs()
                    targetFile.writeBytes(bytes)
                    writeSuccess = true
                } catch (e: Exception) {
                    Log.w(TAG, "Genel indirme klasörüne doğrudan yazılamadı: ${e.message}, uygulama klasörüne deneniyor...")
                    // Scoped Storage veya izin eksikliğinde garantili yedek: context.getExternalFilesDir
                    try {
                        val appExtDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                            ?: context.filesDir
                        val fallbackDir = File(appExtDir, "Kitsugi/Images").also { it.mkdirs() }
                        val fallbackFile = File(fallbackDir, filename)
                        fallbackFile.writeBytes(bytes)
                        targetFile = fallbackFile
                        writeSuccess = true
                    } catch (e2: Exception) {
                        e2.printStackTrace()
                    }
                }

                if (writeSuccess) {
                    markImageDownloaded(context, url, filename, "")
                    try {
                        MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), arrayOf(format.mimeType), null)
                    } catch (_: Throwable) {}
                    try {
                        com.kitsugi.animelist.core.diagnostics.KitsugiSessionSupervisor
                            .noteAction("resim indirme TAMAM (dosya): $filename")
                    } catch (_: Throwable) {}
                    withContext(Dispatchers.Main) {
                        showCompletedNotification(context, notifId, title, filename, bytes.size.toLong(), thumbnail)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Dosya kaydedilemedi: Depolama alanına erişilemiyor.", Toast.LENGTH_LONG).show()
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

    private const val TAG = "KitsugiImageDownloader"

    /**
     * Bir görselin belleğe/ağa alınırken izin verilen üst sınırı.
     * Devasa görseller LMKD/OOM ile süreci sessizce öldürüyordu.
     */
    private const val MAX_TRANSFER_BYTES = 48L * 1024 * 1024

    /** AndroidManifest.xml'deki `<provider android:authorities=...>` ile birebir aynı olmalı. */
    private const val FILEPROVIDER_AUTHORITY = "com.kitsugi.animelist.fileprovider"

    /** Paylaşım geçici dosyalarının saklandığı önbellek klasörü. */
    private const val SHARE_CACHE_DIR = "shared_images"

    /** Ham baytlar gerçekten bir resme mi benziyor? (magic number) */
    internal fun hasImageSignature(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        fun ascii(text: String, offset: Int = 0): Boolean {
            if (bytes.size < offset + text.length) return false
            return text.indices.all { i -> bytes[offset + i] == text[i].code.toByte() }
        }
        fun sig(vararg s: Int, offset: Int = 0): Boolean {
            if (bytes.size < offset + s.size) return false
            return s.indices.all { i -> (bytes[offset + i].toInt() and 0xFF) == s[i] }
        }
        return ascii("GIF87a") || ascii("GIF89a") ||
            sig(0x89, 0x50, 0x4E, 0x47) ||
            sig(0xFF, 0xD8, 0xFF) ||
            (ascii("RIFF") && ascii("WEBP", 8)) ||
            (ascii("ftyp", 4)) ||
            ascii("BM") ||
            (ascii("II") && sig(0x2A, 0x00, offset = 2)) ||
            (ascii("MM") && sig(0x00, 0x2A, offset = 2))
    }

    /** [fetchImage] sonucu: ham baytlar + üzerlerinden okunan format. */
    internal data class FetchedImage(val bytes: ByteArray, val format: ImageFormat)

    /**
     * Görsel baytlarını HER TÜR kaynaktan okur:
     *  • `http(s)://` → uygulamanın OkHttp istemcisi (User-Agent + Accept + Referer;
     *    `URL.openConnection()` kullanınca CDN'ler 403 veriyordu),
     *  • `file://`   → doğrudan dosya,
     *  • `content://`→ ContentResolver (SAF / MediaStore kaynaklı galeriler).
     *
     * İmzasız (ör. HTML hata sayfası) veya boyut sınırını aşan veri kabul edilmez.
     * Hata durumunda null döner; asla fırlatmaz.
     */
    internal fun fetchImage(context: Context, url: String, maxBytes: Long): FetchedImage? {
        if (url.isBlank()) return null
        val fetched = try {
            when {
                url.startsWith("file://") -> {
                    val path = Uri.parse(url).path
                    val file = if (path.isNullOrBlank()) null else File(path)
                    if (file == null || !file.exists() || file.length() > maxBytes) null
                    else runCatching {
                        val data = file.readBytes()
                        FetchedImage(data, detectImageFormat(data, file.name))
                    }.getOrNull()
                }
                url.startsWith("content://") -> {
                    val uri = Uri.parse(url)
                    val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
                    val data = runCatching {
                        context.contentResolver.openInputStream(uri)?.use { readBytesCapped(it, maxBytes) }
                    }.getOrNull()
                    if (data == null) null
                    else FetchedImage(data, detectImageFormat(data, url, mime))
                }
                url.startsWith("http://") || url.startsWith("https://") -> fetchHttpImage(url, maxBytes)
                else -> null
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "Görsel okunamadı (${url.take(120)}): ${e.message}")
            null
        } ?: return null

        // İmzasız veri = büyük olasılıkla HTML hata sayfası / bozuk indirme.
        // Galeride 0 KB'lık "kapak resmi" birikmesinin kokusu buydu.
        if (!hasImageSignature(fetched.bytes)) {
            Log.w(TAG, "Geçersiz görsel verisi atıldı: ${url.take(120)}")
            return null
        }
        return fetched
    }

    /** Uzak görseli uygulamanın OkHttp istemcisiyle indirir (UA/Referer başlıkları dahil). */
    private fun fetchHttpImage(url: String, maxBytes: Long): FetchedImage? {
        val builder = okhttp3.Request.Builder()
            .url(url)
            .header("Accept", "image/avif,image/webp,image/apng,image/gif,image/png,image/jpeg,image/*;q=0.9,*/*;q=0.5")
        runCatching {
            val origin = URL(url).let { "${it.protocol}://${it.host}/" }
            builder.header("Referer", origin)
        }
        return runCatching {
            KitsugiHttpClient.client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) return null
                val declared = response.body?.contentLength() ?: -1L
                if (declared > maxBytes) return null
                val data = response.body?.byteStream()?.use { readBytesCapped(it, maxBytes) } ?: return null
                FetchedImage(data, detectImageFormat(data, url, response.header("Content-Type")))
            }
        }.getOrNull()
    }

    /** Akışı [maxBytes] sınırına kadar okur; sınırı aşarsa null. */
    private fun readBytesCapped(input: java.io.InputStream, maxBytes: Long): ByteArray? {
        val limit = maxBytes.coerceAtMost(Int.MAX_VALUE.toLong() - 8).toInt()
        val out = java.io.ByteArrayOutputStream(minOf(input.available().coerceAtLeast(16 * 1024), 4 * 1024 * 1024))
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    /** Dosyanın yalnızca başını okur (format tespiti için yeterli, büyük dosyaları belleğe almamak için). */
    private fun readHead(file: File, bytes: Int): ByteArray {
        return runCatching {
            java.io.RandomAccessFile(file, "r").use { raf ->
                val n = minOf(bytes.toLong(), raf.length()).toInt()
                val buf = ByteArray(n)
                raf.readFully(buf)
                buf
            }
        }.getOrDefault(ByteArray(0))
    }

    /**
     * Paylaşım için baytları hazırlar: bilinen formatlar olduğu gibi geçer,
     * alıcının çözemediği formatlar (HEIF/AVIF/BMP/TIFF) JPEG/PNG'ye çevrilir.
     */
    private fun transcodeForShare(bytes: ByteArray, format: ImageFormat): FetchedImage {
        val (outBytes, outFormat) = normalizeBytesForSharing(bytes, format)
        return FetchedImage(outBytes, outFormat)
    }

    /** Paylaşılacak geçici dosyayı uygulama önbelleğine yazar ve URI'sini döner. */
    private fun writeShareTemp(context: Context, title: String, bytes: ByteArray, format: ImageFormat): Uri {
        val cacheDir = File(context.cacheDir, SHARE_CACHE_DIR).also { it.mkdirs() }
        prunedStaleShareFiles(cacheDir)
        val sanitized = title.replace(Regex("[^\\p{L}\\p{N}_]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(48)
            .ifBlank { "image" }
        val file = File(cacheDir, "Kitsugi_${sanitized}_${System.currentTimeMillis()}.${format.extension}")
        file.writeBytes(bytes)
        return FileProvider.getUriForFile(context, FILEPROVIDER_AUTHORITY, file)
    }

    /** Önbellek şişmesin: 1 saatten eski paylaşım kopyaları silinir. */
    private fun prunedStaleShareFiles(dir: File) {
        runCatching {
            val cutoff = System.currentTimeMillis() - 60 * 60 * 1000L
            dir.listFiles()?.forEach { f ->
                if (f.lastModified() < cutoff) runCatching { f.delete() }
            }
        }
    }

    /** content:// adresi hâlâ okunabiliyor mu? (silinmiş/silinemeyecek kayıt ayrımı için) */
    private fun contentUriReadable(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.openInputStream(uri)?.use { true } ?: false
    } catch (_: Throwable) {
        false
    }

    /**
     * Kullanıcıya gösterilecek okunabilir yol etiketi:
     * `/storage/emulated/0/Download/Kitsugi/Images` → `Download/Kitsugi/Images`
     */
    private fun humanizePath(file: File): String {
        val absolute = file.absolutePath
        val root = Environment.getExternalStorageDirectory().absolutePath
        return if (absolute.startsWith(root)) absolute.removePrefix(root).removePrefix("/") else absolute
    }

    /** Yerel dosyayı siler; Scoped Storage izin vermezse MediaStore kaydı üzerinden dener. */
    private fun deleteLocalFile(context: Context, file: File): GalleryDeleteOutcome {
        if (!file.exists()) return GalleryDeleteOutcome.NothingToDelete
        val direct = runCatching { file.delete() && !file.exists() }.getOrDefault(false)
        if (direct) {
            notifyMediaDeleted(context, file.absolutePath)
            return GalleryDeleteOutcome.Success
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            deleteViaMediaStoreByPath(context, file.absolutePath)
        ) {
            return GalleryDeleteOutcome.Success
        }
        return GalleryDeleteOutcome.Failed("Dosya silinemedi (depolama izni): ${file.name}")
    }

    /** MediaStore/SAF content:// kaydını siler. */
    private fun deleteContentUri(context: Context, uri: Uri): GalleryDeleteOutcome {
        return try {
            val rows = context.contentResolver.delete(uri, null, null)
            if (rows > 0) {
                notifyMediaDeleted(context, uri)
                GalleryDeleteOutcome.Success
            } else {
                GalleryDeleteOutcome.NothingToDelete
            }
        } catch (e: SecurityException) {
            // Başka bir uygulamanın oluşturduğu kayıt: sistem onayı gerekir.
            GalleryDeleteOutcome.RequiresSystemConsent(
                "Bu dosya başka bir uygulamaya ait — silmek için sistem dosya yöneticisini kullanın."
            )
        } catch (e: Throwable) {
            GalleryDeleteOutcome.Failed(e.localizedMessage ?: "Kayıt silinemedi")
        }
    }

    /** Index kaydındaki kopyayı siler ve index'i temizler. */
    private fun deleteDownloadedRecord(
        context: Context,
        url: String,
        record: DownloadRecord
    ): GalleryDeleteOutcome {
        val outcome = when {
            record.customUri.startsWith("mediastore:") ->
                deleteContentUri(context, Uri.parse(record.customUri.removePrefix("mediastore:")))
            record.customUri.isNotBlank() -> deleteSafFile(context, record)
            else -> deleteKnownImageFile(context, record.fileName)
        }
        // Dosya zaten yoksa (dışarıdan silinmiş) bayat kaydı yine de düşür.
        if (outcome is GalleryDeleteOutcome.Success || outcome is GalleryDeleteOutcome.NothingToDelete) {
            dropIndexRecordForUrl(context, url, record.fileName)
        }
        return outcome
    }

    /** Kullanıcının seçtiği SAF klasöründeki kopyayı siler. */
    private fun deleteSafFile(context: Context, record: DownloadRecord): GalleryDeleteOutcome {
        val dir = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(record.customUri)) }
            .getOrNull() ?: return GalleryDeleteOutcome.NothingToDelete
        val file = dir.findFile(record.fileName) ?: return GalleryDeleteOutcome.NothingToDelete
        val ok = runCatching { file.delete() }.getOrDefault(false)
        return if (ok) GalleryDeleteOutcome.Success
        else GalleryDeleteOutcome.Failed("Seçili klasörde silmeye izin verilmedi: ${record.fileName}")
    }

    /** İndirme klasörlerimizde (İndirilenler / Resimler / uygulama klasörü) duran dosyayı siler. */
    private fun deleteKnownImageFile(context: Context, fileName: String): GalleryDeleteOutcome {
        val picturesDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "Kitsugi/Images"
        )
        val appExtDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?.let { File(it, "Kitsugi/Images") }
        val candidates = buildList {
            add(File(defaultImagesDir(), fileName))
            add(File(legacyImagesDir(), fileName))
            add(File(picturesDir, fileName))
            appExtDir?.let { add(File(it, fileName)) }
        }.filter { it.exists() }

        if (candidates.isEmpty()) {
            // Dosya disinde yok ama MediaStore kaydı kalmış olabilir → kayıt üzerinden sil.
            val removed = listOf(
                File(defaultImagesDir(), fileName),
                File(picturesDir, fileName)
            ).any { deleteViaMediaStoreByPath(context, it.absolutePath) }
            return if (removed) GalleryDeleteOutcome.Success else GalleryDeleteOutcome.NothingToDelete
        }
        var anyDeleted = false
        candidates.forEach { f -> if (deleteLocalFile(context, f) is GalleryDeleteOutcome.Success) anyDeleted = true }
        return if (anyDeleted) GalleryDeleteOutcome.Success
        else GalleryDeleteOutcome.Failed("Dosya silinemedi: $fileName")
    }

    /**
     * Scoped Storage'ta doğrudan `File.delete()` reddedilebilir; o zaman dosyanın
     * MediaStore kaydını yoluyla bulup Resolver üzerinden sileriz.
     */
    private fun deleteViaMediaStoreByPath(context: Context, absolutePath: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val resolver = context.contentResolver
        return try {
            var deleted = false
            arrayOf(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            ).forEach { collection ->
                if (deleted) return@forEach
                runCatching {
                    resolver.query(
                        collection,
                        arrayOf(MediaStore.MediaColumns._ID),
                        "${MediaStore.MediaColumns.DATA} = ?",
                        arrayOf(absolutePath),
                        null
                    )?.use { cursor ->
                        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                        while (cursor.moveToNext()) {
                            val rowUri = android.content.ContentUris.withAppendedId(collection, cursor.getLong(idColumn))
                            if (runCatching { resolver.delete(rowUri, null, null) > 0 }.getOrDefault(false)) {
                                deleted = true
                            }
                        }
                    }
                }
            }
            if (deleted) notifyMediaDeleted(context, absolutePath)
            deleted
        } catch (_: Throwable) {
            false
        }
    }

    /** Silinen dosyayı medya tarifinden düşür (galeri uygulamaları "ölmüş" kare göstermesin). */
    private fun notifyMediaDeleted(context: Context, absolutePath: String) {
        runCatching {
            MediaScannerConnection.scanFile(context, arrayOf(absolutePath), null, null)
        }
        runCatching {
            context.contentResolver.notifyChange(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null
            )
        }
    }

    private fun notifyMediaDeleted(context: Context, uri: Uri) {
        runCatching { context.contentResolver.notifyChange(uri, null) }
        runCatching {
            context.contentResolver.notifyChange(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null)
        }
    }

    /** Silinen/yerinden edilen kayıt için index güncellemesi (rozeti de düşürür). */
    private fun dropIndexRecordForUrl(context: Context, url: String, fileName: String) {
        synchronized(indexLock) {
            val current = indexCache ?: loadIndex(context)
            val stale = (current.filterValues { it.fileName == fileName }.keys + url).toSet()
            if (stale.isEmpty()) return
            val before = current.size
            current.keys.removeAll(stale)
            if (current.size != before) saveIndex(context, current)
            _downloadedUrls.value = _downloadedUrls.value - stale
        }
    }


    /**
     * Android 10+ için MediaStore tabanlı görsel kaydı.
     * Öncelik:
     *  1. MediaStore.Downloads -> Download/Kitsugi/Images (Scoped Storage resmi indirme konumu)
     *  2. MediaStore.Images -> Pictures/Kitsugi/Images (Galeri albüm konumu)
     * Başarılıysa content:// URI döner.
     */
    private fun saveImageViaMediaStore(context: Context, filename: String, bytes: ByteArray, mimeType: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val resolver = context.contentResolver

        // 1. MediaStore.Downloads koleksiyonu (Android 10+ standart Download/Kitsugi/Images)
        val downloadUri = trySaveToMediaStoreCollection(
            context = context,
            resolver = resolver,
            collectionUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            relativePath = "${Environment.DIRECTORY_DOWNLOADS}/Kitsugi/Images",
            filename = filename,
            bytes = bytes,
            mimeType = mimeType
        )
        if (downloadUri != null) return downloadUri

        // 2. MediaStore.Images koleksiyonu (Pictures/Kitsugi/Images)
        val picturesUri = trySaveToMediaStoreCollection(
            context = context,
            resolver = resolver,
            collectionUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            relativePath = "${Environment.DIRECTORY_PICTURES}/Kitsugi/Images",
            filename = filename,
            bytes = bytes,
            mimeType = mimeType
        )
        if (picturesUri != null) return picturesUri

        return null
    }

    private fun trySaveToMediaStoreCollection(
        context: Context,
        resolver: ContentResolver,
        collectionUri: Uri,
        relativePath: String,
        filename: String,
        bytes: ByteArray,
        mimeType: String
    ): Uri? {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collectionUri, values) ?: return null
            val written = try {
                resolver.openOutputStream(uri)?.use { out ->
                    out.write(bytes)
                    out.flush()
                }
                true
            } catch (e: Throwable) {
                Log.w(TAG, "openOutputStream hatası ($uri): ${e.message}")
                false
            }
            if (!written) {
                try { resolver.delete(uri, null, null) } catch (_: Throwable) {}
                return null
            }
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            try { resolver.update(uri, done, null, null) } catch (_: Throwable) {}

            // Medya galerisinin dosyayı hemen tanıması için tarayıcıyı bilgilendir
            try {
                val primaryDir = relativePath.substringBefore("/")
                val subDir = relativePath.substringAfter("/", "")
                val targetDir = Environment.getExternalStoragePublicDirectory(primaryDir)
                val fullFile = if (subDir.isNotBlank()) File(targetDir, "$subDir/$filename") else File(targetDir, filename)
                if (fullFile.exists()) {
                    MediaScannerConnection.scanFile(context, arrayOf(fullFile.absolutePath), arrayOf(mimeType), null)
                }
            } catch (_: Throwable) {}

            uri
        } catch (e: Throwable) {
            Log.w(TAG, "MediaStore kayıt hatası ($collectionUri, $relativePath): ${e.message}")
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
        bytes: ByteArray,
        mimeType: String
    ): Boolean {
        return try {
            val treeUri  = Uri.parse(uriString)
            val pickedDir = DocumentFile.fromTreeUri(context, treeUri)
            if (pickedDir != null && pickedDir.exists() && pickedDir.isDirectory) {
                val imageFile = pickedDir.createFile(mimeType, filename)
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
