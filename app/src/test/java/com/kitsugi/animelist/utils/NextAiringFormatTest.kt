package com.kitsugi.animelist.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NextAiringFormatTest {

    @Test
    fun isoDateToEpochRoundTripsToSameCalendarDate() {
        val epoch = requireNotNull(NextAiringFormat.isoDateToEpoch("2024-02-01"))
        // Yerel gece yarısı çözümlemesi → formatDate aynı takvim tarihini döndürmeli
        assertEquals("2024-02-01", NextAiringFormat.formatDate(epoch))
    }

    @Test
    fun isoDateToEpochRejectsInvalidDates() {
        assertNull(NextAiringFormat.isoDateToEpoch(""))
        assertNull(NextAiringFormat.isoDateToEpoch("not-a-date"))
        assertNull(NextAiringFormat.isoDateToEpoch("2024-13-45"))
    }

    @Test
    fun parseMachineFormatWithKnownEpisode() {
        val parsed = NextAiringFormat.parse("25|1706745600")
        assertTrue(parsed.isMachineFormat)
        assertEquals(25, parsed.episode)
        assertEquals(1706745600L, parsed.epoch)
        assertNull(parsed.legacyText)
    }

    @Test
    fun parseMachineFormatWithMovieMarker() {
        val parsed = NextAiringFormat.parse("0|12345")
        assertEquals(0, parsed.episode)
        assertEquals(12345L, parsed.epoch)
    }

    @Test
    fun parseMachineFormatWithUnknownEpisodeMarker() {
        val parsed = NextAiringFormat.parse("-1|12345")
        assertEquals(-1, parsed.episode)
        assertEquals(12345L, parsed.epoch)
    }

    @Test
    fun parseLegacyTmdbTextIsNormalized() {
        val parsed = NextAiringFormat.parse("Bölüm 2, 2026-10-15 tarihinde yayında")
        assertTrue(parsed.isMachineFormat)
        assertEquals(2, parsed.episode)
        assertEquals(NextAiringFormat.isoDateToEpoch("2026-10-15"), parsed.epoch)
    }

    @Test
    fun parseLegacyTmdbTextWithoutEpisodeDefaultsToDizi() {
        val parsed = NextAiringFormat.parse("2026-10-15 tarihinde yayında")
        assertEquals(NextAiringFormat.UNKNOWN_EPISODE, parsed.episode)
        assertEquals(NextAiringFormat.isoDateToEpoch("2026-10-15"), parsed.epoch)
    }

    @Test
    fun parseUnparseableTextKeepsLegacy() {
        val parsed = NextAiringFormat.parse("bir şeyler olacak")
        assertTrue(!parsed.isMachineFormat)
        assertEquals("bir şeyler olacak", parsed.legacyText)
    }

    @Test
    fun parseNullOrBlankReturnsEmpty() {
        assertTrue(!NextAiringFormat.parse(null).isMachineFormat)
        assertNull(NextAiringFormat.parse("").legacyText)
    }

    @Test
    fun detailTextShowsDateAndCountdown() {
        // Kullanıcının işaretlediği biçim: "Bölüm 2, 2026-10-15 tarihinde yayında (…)"
        val epoch = requireNotNull(NextAiringFormat.isoDateToEpoch("2026-10-15"))
        val now = epoch - 6L * 86400
        assertEquals(
            "Bölüm 2, 2026-10-15 tarihinde yayında (6 gün sonra)",
            NextAiringFormat.detailText("2|$epoch", nowEpoch = now)
        )
    }

    @Test
    fun detailTextForMovieUsesVizyonWording() {
        val epoch = 1000L + 2 * 86400 + 3600
        val date = NextAiringFormat.formatDate(epoch)
        assertEquals(
            "Film, $date tarihinde vizyonda (2 gün sonra)",
            NextAiringFormat.detailText("0|$epoch", nowEpoch = 1000L)
        )
    }

    @Test
    fun detailTextForUnknownEpisodeUsesDiziLabel() {
        val epoch = 1000L + 2 * 86400 + 3600
        val date = NextAiringFormat.formatDate(epoch)
        assertEquals(
            "Dizi, $date tarihinde yayında (2 gün sonra)",
            NextAiringFormat.detailText("-1|$epoch", nowEpoch = 1000L)
        )
    }

    @Test
    fun detailTextAfterAiringShowsAired() {
        val epoch = requireNotNull(NextAiringFormat.isoDateToEpoch("2026-10-15"))
        assertEquals(
            "Bölüm 2 yayınlandı!",
            NextAiringFormat.detailText("2|$epoch", nowEpoch = epoch + 60)
        )
    }

    @Test
    fun detailTextFallsBackToLegacyRawText() {
        assertEquals(
            "özel açıklama",
            NextAiringFormat.detailText("özel açıklama", nowEpoch = 0L)
        )
    }

    @Test
    fun chipTextShowsLabelDateAndCountdown() {
        val epoch = requireNotNull(NextAiringFormat.isoDateToEpoch("2026-10-15"))
        val now = epoch - 6L * 86400
        assertEquals(
            "Bölüm 2 · 2026-10-15 · 6 gün sonra yayında",
            NextAiringFormat.chipText("2|$epoch", nowEpoch = now)
        )
    }

    @Test
    fun chipTextForMovieAndUnknownEpisode() {
        val epoch = 1000L + 2 * 86400 + 3600
        val date = NextAiringFormat.formatDate(epoch)
        assertEquals(
            "Film · $date · 2 gün sonra vizyonda",
            NextAiringFormat.chipText("0|$epoch", nowEpoch = 1000L)
        )
        assertEquals(
            "Dizi · $date · 2 gün sonra yayında",
            NextAiringFormat.chipText("-1|$epoch", nowEpoch = 1000L)
        )
    }

    @Test
    fun chipTextAfterAiring() {
        val epoch = requireNotNull(NextAiringFormat.isoDateToEpoch("2026-10-15"))
        assertEquals(
            "Bölüm 2 yayınlandı",
            NextAiringFormat.chipText("2|$epoch", nowEpoch = epoch + 60)
        )
        assertEquals(
            "Film vizyonda",
            NextAiringFormat.chipText("0|$epoch", nowEpoch = epoch + 60)
        )
    }

    @Test
    fun countdownTextScales() {
        assertEquals("az sonra", NextAiringFormat.countdownText(30))
        assertEquals("30 dakika sonra", NextAiringFormat.countdownText(1800))
        assertEquals("2 saat 5 dakika sonra", NextAiringFormat.countdownText(2 * 3600 + 5 * 60))
        assertEquals("6 gün sonra", NextAiringFormat.countdownText(6 * 86400))
        assertEquals("2 hafta sonra", NextAiringFormat.countdownText(15 * 86400))
        assertEquals("2 ay sonra", NextAiringFormat.countdownText(65 * 86400))
    }
}
