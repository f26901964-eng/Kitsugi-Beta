package com.kitsugi.animelist.core.diagnostics

import android.content.Context
import java.io.File

/**
 * StartupStage — "uygulama açılmadan çöküyor" vakalarında kesin kanıt bırakan iz.
 *
 * NEDEN GEREKLİ?
 * ──────────────
 * Süreç, `Application.onCreate` / `attachBaseContext` sırasında ölürse Java
 * `UncaughtExceptionHandler` devreye giremez (native SIGSEGV, ABI/class-load hatası,
 * sistem tarafından zorla öldürme vb.). Bu durumda `crash_log.txt` dosyası **eski bir
 * çökmeyi** tutmaya devam eder ve kullanıcı bize saatler/günler önceki raporu gönderir.
 *
 * Bu nesne, açılışın her adımını `filesDir/startup_stage.txt` dosyasına yazar.
 * Ana ekran ilk kez çizildiğinde dosya silinir. Uygulama ana ekranı hiç açamadan
 * öldüyse dosya diskte kalır; çökme raporuna ve çökme ekranına "son başlatma aşaması"
 * olarak eklenir. Böylece tam olarak hangi init çağrısında öldüğü görülür.
 *
 * Tüm işlemler `try/catch` içindedir: bu sınıfın kendisi asla çökme sebebi olamaz.
 */
object StartupStage {

    private const val FILE_NAME = "startup_stage.txt"

    /** Son işaretlenen aşama (süreç içi, hızlı okuma için). */
    @Volatile
    private var lastStage: String? = null

    /**
     * Açılış aşamasını diske yazar. Dosya bilinçli olarak küçük ve senkrondur:
     * süreç birazdan ölebileceği için `apply()`/async yazım yeterli değildir.
     */
    fun mark(context: Context, stage: String) {
        lastStage = stage
        try {
            val dir = context.applicationContext?.filesDir ?: context.filesDir
            File(dir, FILE_NAME).writeText("$stage\n${System.currentTimeMillis()}")
        } catch (_: Throwable) {
            // Disk dolu/izin yok → teşhis izi olmadan devam et.
        }
    }

    /** Diskte kayıtlı son aşamayı döndürür (yoksa null). */
    fun read(context: Context): String? {
        return try {
            val context2 = context.applicationContext ?: context
            val file = File(context2.filesDir, FILE_NAME)
            if (!file.exists() || file.length() == 0L) return null
            file.readLines().firstOrNull()?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            lastStage
        }
    }

    /**
     * Ana ekran başarıyla çizildi → bayrak artık bir işe yaramaz.
     * Silinmezse her "temiz" açılışta rapora yanlış "başlatma aşaması" eklenir.
     */
    fun clear(context: Context) {
        lastStage = null
        try {
            val context2 = context.applicationContext ?: context
            File(context2.filesDir, FILE_NAME).delete()
        } catch (_: Throwable) {
        }
    }

    /**
     * Rapora eklenecek kısa blok. Dosya yalnızca uygulama ana ekranı açılmadan
     * öldüğünde mevcut olduğu için bu blok doğrudan "açılışta çökme" kanıtıdır.
     */
    fun reportBlock(context: Context): String {
        val stage = read(context) ?: return ""
        return buildString {
            appendLine()
            appendLine("▶ BAŞLATMA AŞAMASI (uygulama ana ekranı açılmadan önce)")
            appendLine("  Son aşama : $stage")
            appendLine("  NOT       : Bu dosya yalnızca ana ekran çizilemeden süreç öldüğünde kalır.")
            appendLine("              Uygulama bu adımdan sonra çöktüyse kök neden bu init zincirindedir.")
        }
    }
}
