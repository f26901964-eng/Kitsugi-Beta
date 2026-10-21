package com.kitsugi.animelist.core.diagnostics

import com.kitsugi.animelist.data.settings.AppSettings
import com.kitsugi.animelist.data.settings.DownloadAndMangaSettings
import com.kitsugi.animelist.data.settings.GestureAndSkipSettings
import com.kitsugi.animelist.data.settings.IntegrationsAndNotificationsSettings
import com.kitsugi.animelist.data.settings.MpvAndSubtitleSettings
import com.kitsugi.animelist.data.settings.PlayerOverlaySettings
import com.kitsugi.animelist.data.settings.PlayerPlaybackSettings
import com.kitsugi.animelist.data.settings.ProfileAndListSettings
import com.kitsugi.animelist.data.settings.ThemeAndAppUiSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Dalvik DEX 255-yazmaç taşması (VerifyError: expected 5 argument registers, method signature has 6 or more)
 * ve çökme raporlama hattındaki bayat native iz / post-mortem tanı hatalarına karşı regresyon kalkanı.
 */
class AppSettingsAndCrashDiagnosticsGuardTest {

    private fun countDalvikSlots(paramTypes: Array<Class<*>>, isStatic: Boolean): Int {
        var slots = if (isStatic) 0 else 1
        for (p in paramTypes) {
            slots += if (p == Long::class.javaPrimitiveType || p == Double::class.javaPrimitiveType) 2 else 1
        }
        return slots
    }

    @Test
    fun `all AppSettings and sub-model constructors and synthetic methods stay strictly below Dalvik 128-slot safety ceiling`() {
        val classes = listOf(
            AppSettings::class.java,
            ProfileAndListSettings::class.java,
            ThemeAndAppUiSettings::class.java,
            PlayerPlaybackSettings::class.java,
            PlayerOverlaySettings::class.java,
            MpvAndSubtitleSettings::class.java,
            GestureAndSkipSettings::class.java,
            IntegrationsAndNotificationsSettings::class.java,
            DownloadAndMangaSettings::class.java
        )

        for (cls in classes) {
            for (ctor in cls.declaredConstructors) {
                val slots = countDalvikSlots(ctor.parameterTypes, isStatic = false)
                assertTrue(
                    "Constructor ${cls.simpleName}(${ctor.parameterCount} params) uses $slots Dalvik register slots (limit 128, hard DEX ceiling 255)!",
                    slots <= 128
                )
            }
            for (method in cls.declaredMethods) {
                val isStatic = Modifier.isStatic(method.modifiers)
                val slots = countDalvikSlots(method.parameterTypes, isStatic = isStatic)
                assertTrue(
                    "Method ${cls.simpleName}.${method.name} uses $slots Dalvik register slots (limit 128, hard DEX ceiling 255)!",
                    slots <= 128
                )
            }
        }
    }

    @Test
    fun `AppSettings default construction and group copy execute cleanly and preserve values`() {
        val base = AppSettings()
        val updated = base.copy(
            themeUi = base.themeUi.copy(splashAnimationEnabled = false, splashSoundEnabled = false),
            mpvSubtitle = base.mpvSubtitle.copy(subtitleItalic = true, subtitlePos = 92)
        )
        assertFalse(updated.splashAnimationEnabled)
        assertFalse(updated.splashSoundEnabled)
        assertTrue(updated.subtitleItalic)
        assertEquals(92, updated.subtitlePos)
        assertEquals("MPV", updated.playerPreference)
    }

    @Test
    fun `NativeCrashBridge rejects stale native_crash from older PID and extracts latest entry`() {
        val staleAppendedDump = """
            === KITSUGI NATIVE CRASH ===
            epoch=1791889108
            signal=SIGSEGV (11)
            pid=29747
            tid=29835
            thread=RenderThread
            === END NATIVE CRASH ===
            === PROC MAPS (/proc/self/maps) ===
            7f5a000000-7f5a200000 r-xp 00000000 fc:03 1 /system/lib64/libhwui.so
            === END PROC MAPS ===
        """.trimIndent()

        val meta = NativeCrashBridge.parseLatestNativeCrash(staleAppendedDump)
        assertNotNull(meta)
        assertEquals(1791889108L, meta!!.epochSec)
        assertEquals(29747, meta.pid)

        // Yeni süreç PID=20612 (v2.4.322 Java çökmesi) eski PID=29747'nin izini ASLA almamalı
        assertFalse(
            NativeCrashBridge.matchesSession(
                meta = meta,
                expectedPid = 20612,
                sessionStartMs = 1_792_541_099_000L,
                sessionEndMs = 1_792_541_101_000L,
                nowMs = 1_792_541_108_000L
            )
        )

        // Aynı PID=29747 için ise doğrulama geçmeli
        assertTrue(
            NativeCrashBridge.matchesSession(
                meta = meta,
                expectedPid = 29747,
                sessionStartMs = 1_791_889_100_000L,
                sessionEndMs = 1_791_889_109_000L,
                nowMs = 1_791_889_115_000L
            )
        )
    }

    @Test
    fun `KitsugiSessionSupervisor post-mortem analyzer identifies AndroidRuntime VerifyError instead of SEBEP BULUNAMADI`() {
        val sampleLines = listOf(
            "10-21 03:05:00.059 20612 20667 E AndroidRuntime: FATAL EXCEPTION: WM.task-1",
            "10-21 03:05:00.059 20612 20667 E AndroidRuntime: Process: com.kitsugi.animelist, PID: 20612",
            "10-21 03:05:00.059 20612 20667 E AndroidRuntime: java.lang.VerifyError: Verifier rejected class androidx.room.r",
            "10-21 03:05:00.059 20612 20667 E AndroidRuntime: \tat b2.x.invokeSuspend(Unknown Source:35)"
        )

        val result = KitsugiSessionSupervisor.analyzePostMortemLines(
            all = sampleLines,
            pkg = "com.kitsugi.animelist",
            prevPid = 20612,
            started = 0L,
            lastAlive = 0L
        )

        assertTrue(result.looksLikeCrash)
        assertTrue("Expected JAVA / ART FATAL HATA but got: ${result.headline}", result.headline.contains("JAVA / ART FATAL HATA"))
        assertTrue(result.headline.contains("java.lang.VerifyError"))
    }
}
