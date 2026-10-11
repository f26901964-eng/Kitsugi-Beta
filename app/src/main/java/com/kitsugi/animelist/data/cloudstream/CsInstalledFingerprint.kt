package com.kitsugi.animelist.data.cloudstream

import android.content.Context
import android.util.Log
import java.io.File
import java.security.MessageDigest

/**
 * Kurulu `.cs3` eklenti dosyalarının içerik parmak izini (SHA-256) tutar.
 *
 * ## Neden gerekli
 * Depo manifesti (`plugins.json`) her eklenti için `fileHash` alanı taşır ve bu alan
 * **depodaki dosyanın içeriğini** tanımlar. Sürüm alanı (`version`) ise kaynak depoda
 * `build.gradle.kts` içinde ELLE artırılır.
 *
 * Gerçek olay (Kitsugi-Plugins, 2026-10): bir eklenti yeniden derlenip `builds` dalına
 * yüklendiğinde (ör. domain düzeltmesi, ayrıştırıcı fix'i) kaynak sürüm numarası
 * artırılmadıysa `plugins.json` yine `"version": 15` diyordu. Uygulama yalnızca
 * `latest.version > installed.version` karşılaştırması yaptığı için "güncelleme yok"
 * sonucuna varıyor, kullanıcı düzeltilmiş eklentiyi asla almıyordu.
 *
 * İçerik parmak izi bu körlüğü kapatır: dosyanın gerçek SHA-256'sı depodaki `fileHash`
 * ile karşılaştırılır; dosya değiştiyse sürüm numarası aynı kalsa bile güncelleme görünür.
 *
 * ## Performans
 * Parmak izi her çağrıda yeniden hesaplanmaz. Kayıt `boyut:sonDeğişim:hash` biçiminde
 * saklanır; dosyanın boyutu ve `lastModified` değeri değişmediği sürece önbellekten
 * okunur. Yeniden indirme sonrası [invalidate] çağrılır (bkz. `CsPluginLoader`).
 */
object CsInstalledFingerprint {

    private const val TAG = "CsInstalledFingerprint"
    private const val PREFS_NAME = "cs_installed_fingerprint"

    /** Süreç içi ek önbellek: paylaşılan tercihlerin senkron okunmasını en aza indirir. */
    private val memory = HashMap<String, String>()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun fileFor(context: Context, pluginId: String): File =
        File(context.filesDir, "cs_extensions/$pluginId.cs3")

    /** `<boyut>:<lastModified>:<sha256>` biçimindeki kaydı ayrıştırır. */
    private fun parse(record: String?): Triple<Long, Long, String>? {
        if (record.isNullOrBlank()) return null
        val parts = record.split(":")
        if (parts.size != 3) return null
        val size = parts[0].toLongOrNull() ?: return null
        val modified = parts[1].toLongOrNull() ?: return null
        val hash = parts[2].takeIf { it.isNotBlank() } ?: return null
        return Triple(size, modified, hash)
    }

    private fun sha256Of(file: File): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            var read = input.read(buffer)
            while (read != -1) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        Log.w(TAG, "Parmak izi hesaplanamadı: ${file.name} — ${e.message}")
        null
    }

    /**
     * Kurulu eklenti dosyasının SHA-256 parmak izi (küçük harf hex).
     *
     * @return Dosya yoksa/okunamıyorsa `null` (bu durumda içerik karşılaştırması yapılmaz,
     *         güncelleme kararı yalnızca sürüm numarasına dayanır).
     */
    fun sha256(context: Context, pluginId: String): String? {
        val file = fileFor(context, pluginId)
        if (!file.exists()) {
            // Eklenti kaldırıldıysa bayat kayıt kalmasın.
            invalidate(context, pluginId)
            return null
        }

        val size = file.length()
        val modified = file.lastModified()

        memory[pluginId]?.let { record ->
            parse(record)?.let { (cachedSize, cachedModified, hash) ->
                if (cachedSize == size && cachedModified == modified) return hash
            }
        }

        val stored = prefs(context).getString(pluginId, null)
        parse(stored)?.let { (cachedSize, cachedModified, hash) ->
            if (cachedSize == size && cachedModified == modified) {
                memory[pluginId] = stored!!
                return hash
            }
        }

        val hash = sha256Of(file) ?: return null
        val record = "$size:$modified:$hash"
        memory[pluginId] = record
        try {
            prefs(context).edit().putString(pluginId, record).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Parmak izi kaydedilemedi: $pluginId — ${e.message}")
        }
        return hash
    }

    /**
     * Kaydı geçersiz kılar. Yeni bir `.cs3` indirildikten/kaldırıldıktan sonra çağrılmalıdır;
     * aksi halde bir sonraki denetim eski parmak izini "güncel" sanıp güncellemeyi kaçırır.
     */
    fun invalidate(context: Context, pluginId: String) {
        memory.remove(pluginId)
        try {
            prefs(context).edit().remove(pluginId).apply()
        } catch (_: Exception) {
        }
    }

    /** Tüm kayıtları temizler (depo değişimi / toplu kaldırma sonrası bakım için). */
    fun invalidateAll(context: Context) {
        memory.clear()
        try {
            prefs(context).edit().clear().apply()
        } catch (_: Exception) {
        }
    }
}
