package com.kitsugi.animelist.data.cloudstream

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Eklenti yeteneği tespiti — "yüklü ama sessizce ölü" eklentileri ayıklar.
 *
 * SORUN (2026-10-25): Kullanıcı OpenSubtitles v3, AltyaziDB, YTS Subtitles, AniSub gibi
 * **altyazı sağlayıcı** eklentilerini kuruyor. Ekranda "Yüklü Akış Eklentileri" altında
 * görünüyorlar, akış gibi sorgulanıyorlar ve hiç sonuç vermiyorlar. Kullanıcı için sonuç:
 * "altyazı eklentim yüklü ama çalışmıyor".
 *
 * NEDEN ÇALIŞMIYOR: Bu projedeki CloudStream çalışma zamanı
 * (`app/libs/library-android-1.0.2-local.aar`) `com.lagradost.cloudstream3.SubtitleAPI`
 * sınıfını **içermiyor** (doğrulandı: classes.jar içinde yalnız `SubtitleFile`,
 * `newSubtitleFile`, `SubtitleHelper` var). Altyazı eklentisi sınıfı yüklenirken bu ata
 * sınıfı bulamıyor → 0 API dönüyor → sessiz "kaynak hatası".
 *
 * ÇÖZÜM (dürüst davranış): Eklenti dosyası (.cs3 = ham DEX) içinde `SubtitleAPI`
 * referansı aranır. Varsa eklenti **altyazı eklentisi** olarak işaretlenir ve akış
 * sağlayıcısı gibi sorgulanmaz; kullanıcıya gerçek neden gösterilir. Böylece
 * "9 eklenti tarandı, 5'i sonuç buldu" sayacı da yanıltıcı olmaz.
 *
 * Not: Bu bir sınıflandırmadır, destek değildir. Gerçek destek için çalışma zamanına
 * `SubtitleAPI` köprüsü gerekir (PLAN_TASK §9.3, R11).
 */
object CsPluginCapability {

    private const val TAG = "CsPluginCapability"

    /** DEX içinde tip tanımlayıcısı olarak geçen imza. */
    private val SUBTITLE_API_MARKER = "cloudstream3/SubtitleAPI".toByteArray(Charsets.UTF_8)

    /** Dosya başına sonuç önbelleği: "id@boyut@değişimZamanı" → altyazı eklentisi mi? */
    private val cache = ConcurrentHashMap<String, Boolean>()

    /** Son tespitte okunan dosya yolu (tanılama için). */
    fun pluginFile(context: Context, pluginId: String): File =
        File(context.filesDir, "cs_extensions/$pluginId.cs3")

    /**
     * Eklenti bir altyazı sağlayıcısı mı?
     *
     * Dosya yoksa `false` döner (yükleme hatası ayrı ele alınır); tespit hatası da
     * akışı engellemez — yalnızca emin olduğumuzda işaretleriz.
     */
    fun isSubtitlePlugin(context: Context, pluginId: String): Boolean {
        return try {
            val file = pluginFile(context, pluginId)
            if (!file.isFile) return false
            val key = "$pluginId@${file.length()}@${file.lastModified()}"
            cache[key]?.let { return it }
            val detected = containsMarker(file)
            cache[key] = detected
            if (detected) {
                Log.i(TAG, "[$pluginId] altyazı eklentisi tespit edildi (SubtitleAPI referansı)")
            }
            detected
        } catch (e: Throwable) {
            Log.w(TAG, "[$pluginId] yetenek tespiti yapılamadı: ${e.message}")
            false
        }
    }

    /**
     * `.cs3` dosyası **ZIP** (APK benzeri: `classes.dex` + `manifest.json`) olduğu için
     * önce arşiv içindeki girdiler taranır; arşiv değilse ham baytlar taranır.
     * Bellek şişmesin diye parça parça okunur.
     */
    private fun containsMarker(file: File): Boolean {
        // 1) ZIP olarak dene
        try {
            java.util.zip.ZipFile(file).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val name = entry.name.lowercase()
                    // Yalnız çalıştırılabilir içerik ilgilendirir.
                    if (!name.endsWith(".dex") && !name.endsWith(".jar")) continue
                    zip.getInputStream(entry).use { stream ->
                        if (scan(stream)) return true
                    }
                }
                return false
            }
        } catch (_: Throwable) {
            // ZIP değil → ham DEX olabilir, aşağıda taranır.
        }
        // 2) Ham dosya
        return file.inputStream().use { scan(it) }
    }

    private fun scan(input: java.io.InputStream): Boolean {
        val chunkSize = 1 shl 16
        val overlap = SUBTITLE_API_MARKER.size - 1
        val buffer = ByteArray(chunkSize + overlap)
        var carry = 0
        while (true) {
            val read = input.read(buffer, carry, chunkSize)
            if (read <= 0) return false
            val total = carry + read
            if (indexOf(buffer, total, SUBTITLE_API_MARKER) >= 0) return true
            // Son `overlap` baytı bir sonraki parçanın başına taşı (sınırda eşleşme).
            carry = minOf(overlap, total)
            System.arraycopy(buffer, total - carry, buffer, 0, carry)
        }
    }

    private fun indexOf(haystack: ByteArray, length: Int, needle: ByteArray): Int {
        if (needle.isEmpty() || length < needle.size) return -1
        val first = needle[0]
        var i = 0
        val limit = length - needle.size
        while (i <= limit) {
            if (haystack[i] == first) {
                var j = 1
                while (j < needle.size && haystack[i + j] == needle[j]) j++
                if (j == needle.size) return i
            }
            i++
        }
        return -1
    }

    /** Önbelleği temizle (eklenti güncellenince/silinince çağrılabilir). */
    fun clearCache() {
        cache.clear()
    }
}
