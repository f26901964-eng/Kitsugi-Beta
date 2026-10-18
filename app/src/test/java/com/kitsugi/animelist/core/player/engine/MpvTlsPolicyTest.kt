package com.kitsugi.animelist.core.player.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-10-18 KÖK NEDEN regresyon testi — "HDFilmCehennemi / FilmMakinesi ne yaparsak
 * yapalım oynatılmıyor, oynatıcı hata veriyor".
 *
 * Kök neden: `KitsugiMpvSurfaceView.initOptions()` şu iki seçeneği veriyordu:
 *
 *     tls-verify  = yes
 *     tls-ca-file = <filesDir>/cacert.pem
 *
 * `cacert.pem` dosyası HİÇBİR ZAMAN oluşturulmuyordu:
 *  - `app/src/main/assets` içinde `.pem` dosyası yok,
 *  - `Utils.copyAssets(context)` mpv-android'in kendi asset'lerini kopyalar, CA paketi üretmez,
 *  - depoda bu dosyayı yazan/indiren başka hiçbir kod yolu yok (`grep -rn cacert` → tek satır).
 *
 * mpv, `tls-ca-file` ile açıkça verilen yolu yüklemeye çalışır; dosya yoksa CA paketi BOŞ
 * kalır ve `tls-verify=yes` ile yapılan **her HTTPS el sıkışması** başarısız olur. Türkçe
 * kaynakların tamamı HTTPS (rapidrame, playmix.uno, closeload, vmeas, vmnow,
 * hdplayersystem …) olduğu için MPV motorunda HİÇBİR kaynak açılmıyordu →
 * "MPV dosyayı açamadı" (kod 9001). `AppSettings.playerPreference` varsayılanı `"MPV"`
 * olduğundan bu, uygulamanın ANA oynatma yoluydu — bu yüzden "ne yaparsak yapalım
 * oynatılmıyor" olarak görünüyordu.
 *
 * Bu test, doğrulamanın bir daha CA dosyası olmadan AÇIK bırakılmasını engeller.
 */
class MpvTlsPolicyTest {

    // ─── CA dosyası yokken (şu anki gerçek durum) ────────────────────────────

    @Test
    fun noCaFile_verificationIsDisabled() {
        val options = mpvTlsOptions(null).toMap()
        assertEquals("no", options["tls-verify"])
    }

    @Test
    fun noCaFile_caFileOptionIsNeverSet() {
        // Var olmayan bir dosyaya işaret etmek mpv'nin CA paketini boş bırakıyordu.
        // Bu seçenek HİÇ yazılmamalı.
        val options = mpvTlsOptions(null).toMap()
        assertFalse(
            "tls-ca-file, CA dosyası yokken asla ayarlanmamalı",
            options.containsKey("tls-ca-file")
        )
        assertNull(options["tls-ca-file"])
    }

    @Test
    fun blankCaFile_treatedSameAsMissing() {
        for (blank in listOf(null, "", "   ", "\t")) {
            val options = mpvTlsOptions(blank).toMap()
            assertEquals("boş CA yolu '$blank' için doğrulama kapatılmalı", "no", options["tls-verify"])
            assertFalse(options.containsKey("tls-ca-file"))
        }
    }

    /**
     * ASIL REGRESYON: `tls-verify=yes` yalnızca gerçek bir CA dosyası gösterildiğinde
     * yazılabilir. Eski hata tam olarak buydu — doğrulama açık, CA paketi boş.
     */
    @Test
    fun neverEnablesVerification_withoutUsableCaFile() {
        val options = mpvTlsOptions(null)
        val verify = options.firstOrNull { it.first == "tls-verify" }?.second
        assertTrue(
            "tls-verify 'yes' olursa CA dosyası ZORUNLU (eski kök neden hatası)",
            verify != "yes"
        )
    }

    @Test
    fun noCaFile_emitsExactlyOneOption() {
        val options = mpvTlsOptions(null)
        assertEquals(1, options.size)
        assertEquals("tls-verify" to "no", options[0])
    }

    // ─── Gerçek bir CA dosyası verilirse ─────────────────────────────────────

    @Test
    fun realCaFile_enablesVerificationAndPointsAtIt() {
        val caPath = "/data/user/0/com.kitsugi.animelist/files/cacert.pem"
        val options = mpvTlsOptions(caPath).toMap()
        assertEquals("yes", options["tls-verify"])
        assertEquals(caPath, options["tls-ca-file"])
    }

    @Test
    fun realCaFile_verificationNeverEnabledWithBlankPath() {
        // Doğrulama açıksa yol mutlaka dolu olmalı — boş yol = boş CA paketi = tüm HTTPS ölü.
        val options = mpvTlsOptions("  ").toMap()
        if (options["tls-verify"] == "yes") {
            assertTrue(
                "tls-verify=yes iken tls-ca-file boş olamaz",
                !options["tls-ca-file"].isNullOrBlank()
            )
        }
    }

    // ─── applyMpvTlsOptions: mpv'ye yazılan seçenekler ───────────────────────

    @Test
    fun applyWritesOnlyTlsVerify_whenNoCaFile() {
        val written = LinkedHashMap<String, String>()
        applyMpvTlsOptions(caFilePath = null) { name, value -> written[name] = value }

        assertEquals(mapOf("tls-verify" to "no"), written)
        assertFalse(written.containsKey("tls-ca-file"))
    }

    @Test
    fun applyWritesBothOptions_whenCaFilePresent() {
        val written = LinkedHashMap<String, String>()
        val caPath = "/sdcard/Android/data/files/cacert.pem"
        applyMpvTlsOptions(caFilePath = caPath) { name, value -> written[name] = value }

        assertEquals("yes", written["tls-verify"])
        assertEquals(caPath, written["tls-ca-file"])
        assertEquals(2, written.size)
    }

    @Test
    fun applyDefaultArgument_isNoCaFile() {
        // Varsayılan argüman CA dosyası YOK demek olmalı: initOptions bunu kullanıyor.
        val written = LinkedHashMap<String, String>()
        applyMpvTlsOptions { name, value -> written[name] = value }
        assertEquals("no", written["tls-verify"])
        assertFalse(written.containsKey("tls-ca-file"))
    }

    @Test
    fun applyDoesNotTouchUnrelatedOptions() {
        val written = LinkedHashMap<String, String>()
        applyMpvTlsOptions(caFilePath = null) { name, value -> written[name] = value }
        // Politika yalnızca tls-* seçeneklerini yazmalı; hwdec/vo/user-agent'a dokunmamalı.
        assertTrue(written.keys.all { it.startsWith("tls-") })
    }
}
