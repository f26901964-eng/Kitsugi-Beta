package com.kitsugi.animelist.utils

import com.kitsugi.animelist.data.remote.KitsugiMediaDetail
import com.kitsugi.animelist.ui.screens.detail.buildUpcomingReleaseInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class KitsugiReleaseDatesTest {

    // Sabit "şimdi": 2026-10-09 12:00 UTC  (TR: 15:00, Cuma)
    private val now = 1_791_547_200L
    private val todayTr: LocalDate = LocalDate.of(2026, 10, 9)

    @Test
    fun formatTr_handlesAllSourceDateShapes() {
        assertEquals("15 Ekim 2026", KitsugiReleaseDates.formatTr("2026-10-15"))
        assertEquals("15 Ekim 2026", KitsugiReleaseDates.formatTr("2026-10-15T00:00:00+00:00"))
        assertEquals("Ekim 2026", KitsugiReleaseDates.formatTr("2026-10"))
        assertEquals("2026", KitsugiReleaseDates.formatTr("2026"))
        assertNull(KitsugiReleaseDates.formatTr(null))
        assertNull(KitsugiReleaseDates.formatTr("   "))
    }

    @Test
    fun formatTrWithWeekday_requiresFullDate() {
        assertEquals("15 Ekim 2026 Perşembe", KitsugiReleaseDates.formatTrWithWeekday("2026-10-15"))
        assertNull(KitsugiReleaseDates.formatTrWithWeekday("2026-10"))
    }

    @Test
    fun utcMidnightEpoch_isDateOnlyMarker() {
        val epoch = KitsugiReleaseDates.utcMidnightEpoch("2026-10-15")
        assertEquals(1_792_022_400L, epoch)
        assertTrue(KitsugiReleaseDates.isDateOnlyEpoch(epoch!!))
        assertFalse(KitsugiReleaseDates.isDateOnlyEpoch(epoch + 1_800L))
    }

    @Test
    fun isoToLocalDateString_respectsZone() {
        assertEquals("2026-10-15", KitsugiReleaseDates.isoToLocalDateString("2026-10-15"))
        // 15:00Z = 00:00 JST ertesi gün (anime tarihi JST referanslı)
        assertEquals(
            "2026-10-05",
            KitsugiReleaseDates.isoToLocalDateString("2026-10-04T15:00:00Z", KitsugiReleaseDates.JST_ZONE)
        )
        assertEquals(
            "2026-10-04",
            KitsugiReleaseDates.isoToLocalDateString("2026-10-04T15:00:00Z")
        )
    }

    @Test
    fun countdownText_formatsEpisodeAndDateOnlyTargets() {
        assertEquals("3 gün sonra yayında", KitsugiReleaseDates.countdownText(now + 3 * 86_400L, now))
        assertEquals("Bölüm 5, 2 saat sonra yayında", KitsugiReleaseDates.countdownText(now + 7_200L, now, 5))
        assertEquals("Bölüm 5 yayınlandı!", KitsugiReleaseDates.countdownText(now - 1L, now, 5))
        assertEquals("Yayınlandı!", KitsugiReleaseDates.countdownText(now - 1L, now))
    }

    @Test
    fun buildUpcomingReleaseInfo_episodeSlotIsUpcoming() {
        val detail = KitsugiMediaDetail(
            synopsis = null,
            nextAiringEpisode = "3|${now + 2 * 86_400L}",
            status = "Currently Airing"
        )
        val info = buildUpcomingReleaseInfo(detail, nowEpochSec = now, todayTr = todayTr)
        assertNotNull(info)
        assertEquals("Yaklaşan Yayın", info!!.title)
        assertTrue(info.primaryText.startsWith("Bölüm 3 · "))
        assertEquals(now + 2 * 86_400L, info.targetEpochSec)
    }

    @Test
    fun buildUpcomingReleaseInfo_tmdbDateOnlySlotHasNoEpisodeLabel() {
        // TMDB: bölüm -1 (yalnız tarih), UTC gece yarısı
        val detail = KitsugiMediaDetail(synopsis = null, nextAiringEpisode = "-1|1792022400")
        val info = buildUpcomingReleaseInfo(detail, nowEpochSec = now, todayTr = todayTr)
        assertNotNull(info)
        assertTrue(info!!.dateOnly)
        assertFalse(info.primaryText.contains("Bölüm"))
        assertFalse(info.primaryText.contains("-1"))
    }

    @Test
    fun buildUpcomingReleaseInfo_futureStartDateWithoutSlot() {
        val detail = KitsugiMediaDetail(synopsis = null, startDate = "2030-01-01", status = "Yakında")
        val info = buildUpcomingReleaseInfo(detail, nowEpochSec = now, todayTr = todayTr)
        assertNotNull(info)
        assertEquals("Yayın Tarihi", info!!.title)
        assertTrue(info.targetEpochSec != null)
    }

    @Test
    fun buildUpcomingReleaseInfo_notYetAiredWithoutDates_saysUnknown() {
        val detail = KitsugiMediaDetail(synopsis = null, status = "NOT_YET_RELEASED")
        val info = buildUpcomingReleaseInfo(detail, nowEpochSec = now, todayTr = todayTr)
        assertNotNull(info)
        assertEquals("Yayın tarihi henüz açıklanmadı", info!!.primaryText)
        assertNull(info.targetEpochSec)
    }

    @Test
    fun buildUpcomingReleaseInfo_finishedTitleIsNotUpcoming() {
        val detail = KitsugiMediaDetail(
            synopsis = null,
            startDate = "2010-04-01",
            status = "Finished Airing",
            nextAiringEpisode = "5|${now - 100L}"
        )
        assertNull(buildUpcomingReleaseInfo(detail, nowEpochSec = now, todayTr = todayTr))
    }
}
