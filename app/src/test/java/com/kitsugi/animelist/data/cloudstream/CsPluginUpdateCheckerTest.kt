package com.kitsugi.animelist.data.cloudstream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Güncelleme kararının sürüm numarasıyla SINIRLI OLMADIĞINI doğrular.
 *
 * Gerçek olay (Kitsugi-Plugins, 2026-10): `RoketDizi` yeniden derlendi ama kaynak
 * `build.gradle.kts` içindeki sürüm artırılmadı; yayınlanan `plugins.json` yine
 * `"version": 15` dedi. Uygulama yalnızca sürüm karşılaştırdığı için kullanıcı
 * düzeltilmiş eklentiyi almıyordu. Artık `fileHash` (içerik parmak izi) farkı da
 * güncelleme sayılır.
 */
class CsPluginUpdateCheckerTest {

    private val hashA = "sha256-" + "a".repeat(64)
    private val hashB = "sha256-" + "b".repeat(64)

    @Test
    fun `depodaki surum yuksekse VERSION`() {
        val reason = CsPluginUpdateChecker.decide(
            installedVersion = 15,
            installedHash = CsPluginUpdateChecker.normalizeHash(hashA),
            latestVersion = 16,
            latestHash = CsPluginUpdateChecker.normalizeHash(hashA)
        )
        assertEquals(CsPluginUpdateChecker.Reason.VERSION, reason)
    }

    @Test
    fun `surum ayni ama icerik farkliysa CONTENT`() {
        val reason = CsPluginUpdateChecker.decide(
            installedVersion = 15,
            installedHash = CsPluginUpdateChecker.normalizeHash(hashA),
            latestVersion = 15,
            latestHash = CsPluginUpdateChecker.normalizeHash(hashB)
        )
        assertEquals(CsPluginUpdateChecker.Reason.CONTENT, reason)
    }

    @Test
    fun `surum ve icerik ayniysa guncelleme yok`() {
        val reason = CsPluginUpdateChecker.decide(
            installedVersion = 15,
            installedHash = CsPluginUpdateChecker.normalizeHash(hashA),
            latestVersion = 15,
            latestHash = CsPluginUpdateChecker.normalizeHash(hashA)
        )
        assertEquals(CsPluginUpdateChecker.Reason.NONE, reason)
    }

    @Test
    fun `kurulu surum depodan yeniyse geri alma yapilmaz`() {
        val reason = CsPluginUpdateChecker.decide(
            installedVersion = 20,
            installedHash = CsPluginUpdateChecker.normalizeHash(hashA),
            latestVersion = 15,
            latestHash = CsPluginUpdateChecker.normalizeHash(hashB)
        )
        assertEquals(CsPluginUpdateChecker.Reason.NONE, reason)
    }

    @Test
    fun `hash bilgisi yoksa karar yalnizca surume dayanir`() {
        val noHashes = CsPluginUpdateChecker.decide(
            installedVersion = 15,
            installedHash = null,
            latestVersion = 15,
            latestHash = null
        )
        assertEquals(CsPluginUpdateChecker.Reason.NONE, noHashes)

        val newerVersion = CsPluginUpdateChecker.decide(
            installedVersion = 15,
            installedHash = null,
            latestVersion = 16,
            latestHash = null
        )
        assertEquals(CsPluginUpdateChecker.Reason.VERSION, newerVersion)
    }

    @Test
    fun `hash normalizasyonu onekleri ve buyuk harfi tolere eder`() {
        val upper = "SHA256-" + "A".repeat(64)
        assertEquals("a".repeat(64), CsPluginUpdateChecker.normalizeHash(upper))
        assertEquals("b".repeat(64), CsPluginUpdateChecker.normalizeHash("sha256:" + "B".repeat(64)))
        assertEquals("c".repeat(64), CsPluginUpdateChecker.normalizeHash("c".repeat(64)))
    }

    @Test
    fun `anlamsiz hash degerleri null doner`() {
        assertNull(CsPluginUpdateChecker.normalizeHash(null))
        assertNull(CsPluginUpdateChecker.normalizeHash(""))
        assertNull(CsPluginUpdateChecker.normalizeHash("   "))
        // Onaltılık olmayan içerik (base64 vb.) karşılaştırılamaz.
        assertNull(CsPluginUpdateChecker.normalizeHash("sha256-Zm9vYmFyYmF6"))
        assertNull(CsPluginUpdateChecker.normalizeHash("sha256-abc"))
    }

    @Test
    fun `stremio manifest eklentileri guncelleme denetimine girmez`() {
        assertTrue(CsPluginUpdateChecker.isManifestAddon("https://ornek.com/manifest.json"))
        assertTrue(CsPluginUpdateChecker.isManifestAddon("https://ornek.com/MANIFEST.JSON"))
        assertFalse(CsPluginUpdateChecker.isManifestAddon("https://raw.githubusercontent.com/x/y/builds/prebuilt/Ornek.cs3"))
        assertFalse(CsPluginUpdateChecker.isManifestAddon(null))
    }
}
