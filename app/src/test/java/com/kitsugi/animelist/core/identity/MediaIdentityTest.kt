package com.kitsugi.animelist.core.identity

import com.kitsugi.animelist.data.remote.ResolvedIds
import com.kitsugi.animelist.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ortak Kaynak/Altyazı Omurgası — kimlik anahtarı sözleşmesi.
 *
 * Bu testler, "aynı bölüm her giriş noktasında aynı anahtara düşer" garantisini korur.
 * Anahtar davranışı bozulursa altyazı belleği ve kimlik defteri sessizce çalışmaz hâle
 * gelir (kullanıcıya "bazı kaynaklarda altyazı yok" olarak yansır).
 */
class MediaIdentityTest {

    @Test
    fun `ID tabanli anahtar baslik dilinden bagimsizdir`() {
        val turkish = MediaIdentity(
            title = "Sihirli Kılıç",
            alternativeTitles = listOf("Frieren: Beyond Journey's End"),
            mediaType = MediaType.Anime,
            malId = 52991,
            aniListId = 154587,
            season = 1,
            episode = 3
        )
        val english = turkish.copy(title = "Frieren: Beyond Journey's End", alternativeTitles = emptyList())

        // Aynı ID'ler → aynı içerik anahtarı, başlık değişse bile.
        assertEquals(turkish.contentKey, english.contentKey)
        assertEquals(turkish.subtitleKey, english.subtitleKey)
    }

    @Test
    fun `ID yoksa baslik ve yil anahtar olur`() {
        val a = MediaIdentity(title = "Frieren", startYear = 2023, episode = 3)
        val b = MediaIdentity(title = "Frieren", startYear = 2023, episode = 3)
        val c = MediaIdentity(title = "Frieren", startYear = 2020, episode = 3)

        assertEquals(a.contentKey, b.contentKey)
        assertNotEquals(a.contentKey, c.contentKey)
        assertTrue(a.contentKey.startsWith("title="))
    }

    @Test
    fun `bolum anahtari bolume duyarlidir`() {
        val ep3 = MediaIdentity(title = "Frieren", malId = 52991, episode = 3)
        val ep4 = ep3.withEpisode(1, 4)

        assertNotEquals(ep3.subtitleKey, ep4.subtitleKey)
        assertTrue(ep3.subtitleKey.endsWith("|s=1|e=3"))
        assertTrue(ep4.subtitleKey.endsWith("|s=1|e=4"))
    }

    @Test
    fun `film tek bolum gibi davranir`() {
        val movie = MediaIdentity(title = "Koe no Katachi", isMovie = true, season = 0, episode = 0)
        assertEquals(1, movie.effectiveSeason)
        assertEquals(1, movie.effectiveEpisode)
        assertTrue(movie.subtitleKey.endsWith("|s=1|e=1"))
    }

    @Test
    fun `yil bilinmese de ayni bolum icin aday anahtar uretilir`() {
        val withYear = MediaIdentity(title = "Sousou no Frieren", startYear = 2023, malId = 52991, episode = 5)
        val withoutYear = withYear.copy(startYear = null)

        val yearKey = "title=" + MediaIdentity.titleKey("Sousou no Frieren", 2023) + "|s=1|e=5"
        val yearlessKey = "title=" + MediaIdentity.titleKey("Sousou no Frieren", null) + "|s=1|e=5"

        assertTrue(withYear.subtitleKeyCandidates.contains(yearKey))
        assertTrue(withYear.subtitleKeyCandidates.contains(yearlessKey))
        // Yıl bilmeyen giriş noktası (katalog/geçmiş) yine de yılsız anahtarı bulur.
        assertTrue(withoutYear.subtitleKeyCandidates.contains(yearlessKey))
    }

    @Test
    fun `alternatif basliklar da aday anahtar olur`() {
        val identity = MediaIdentity(
            title = "Sousou no Frieren",
            alternativeTitles = listOf("Frieren: Beyond Journey's End", "葬送のフリーレン"),
            malId = 52991,
            episode = 2
        )
        val altKey = "title=" + MediaIdentity.titleKey("Frieren: Beyond Journey's End", null) + "|s=1|e=2"
        assertTrue(identity.subtitleKeyCandidates.contains(altKey))
    }

    @Test
    fun `withResolved mevcut kimlikleri ezmez`() {
        val identity = MediaIdentity(title = "Frieren", malId = 52991, episode = 1)
        val resolved = identity.withResolved(
            ResolvedIds(malId = 999, aniListId = 154587, imdbId = "tt22248376", kitsuId = 46474)
        )

        assertEquals(52991, resolved.malId)          // mevcut korunur
        assertEquals(154587, resolved.aniListId)     // boş alan doldurulur
        assertEquals("tt22248376", resolved.imdbId)
        assertEquals(46474, resolved.kitsuId)
    }

    @Test
    fun `gecersiz ID degerleri yok sayilir`() {
        val identity = MediaIdentity(title = "Frieren", malId = 0, aniListId = -5, episode = 1)
        val resolved = identity.withResolved(ResolvedIds(malId = 0, aniListId = -5, tmdbId = 209867))
        assertEquals(0, resolved.malId)
        assertEquals(-5, resolved.aniListId)
        assertEquals(209867, resolved.tmdbId)
        // Geçersiz ID'ler anahtara girmez (0 ve negatif).
        assertFalse(resolved.contentKey.contains("mal=0"))
        assertFalse(resolved.contentKey.contains("anilist=-5"))
    }

    @Test
    fun `baslik anahtari turkce karakterleri sadelestirir`() {
        val dotted = MediaIdentity.titleKey("Kılıç Günlükleri", 2024)
        val plain = MediaIdentity.titleKey("kilic gunlukleri", 2024)
        assertEquals(plain, dotted)
    }
}
