package com.kitsugi.animelist.utils

import com.kitsugi.animelist.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ShareLinks] sözleşmesi — "paylaş butonu bozuk link üretiyor" şikâyetinin regresyon testleri.
 *
 * Her test, kullanıcıya **gönderildiğinde açılan** bir adresi doğrular; offset'li stableId'lerin
 * (`+100M`, `+150M`, `+300M`, `+400M`, `+500M`) sızmadığını ve Kitsu/Simkl gibi sayfası olmayan
 * kaynaklarda uydurma yol yazılmadığını garanti eder.
 */
class ShareLinksTest {

    // ─── Yapım bağlantıları ─────────────────────────────────────────────────

    @Test
    fun `AniList offset'i cozulur`() {
        assertEquals(
            "https://anilist.co/anime/21",
            ShareLinks.mediaUrl("anilist", 100_000_021, MediaType.Anime)
        )
        assertEquals(
            "https://anilist.co/manga/21",
            ShareLinks.mediaUrl("anilist", 100_000_021, MediaType.Manga)
        )
    }

    @Test
    fun `AniList satirinda gercek MAL kimligi MAL sayfasina gider`() {
        // AniList kaydı `idMal` bilindiğinde ham MAL kimliği saklar (<100M).
        assertEquals(
            "https://myanimelist.net/anime/21",
            ShareLinks.mediaUrl("anilist", 21, MediaType.Anime)
        )
    }

    @Test
    fun `MAL ve jikan ayni sayfaya gider`() {
        assertEquals("https://myanimelist.net/anime/16498", ShareLinks.mediaUrl("jikan", 16498, MediaType.Anime))
        assertEquals("https://myanimelist.net/anime/16498", ShareLinks.mediaUrl("mal", 16498, MediaType.Anime))
        assertEquals("https://myanimelist.net/manga/13", ShareLinks.mediaUrl("myanimelist", 13, MediaType.Manga))
    }

    @Test
    fun `Shikimori ham ve offsetli kimligi kendi sayfasina gider`() {
        // Eski hata: ham Shikimori kimliği MAL kimliği sanılıp myanimelist.net'e yazılıyordu.
        assertEquals(
            "https://shikimori.io/animes/20",
            ShareLinks.mediaUrl("shikimori", 20, MediaType.Anime)
        )
        assertEquals(
            "https://shikimori.io/animes/20",
            ShareLinks.mediaUrl("shikimori", 400_000_020, MediaType.Anime)
        )
        assertEquals(
            "https://shikimori.io/mangas/20",
            ShareLinks.mediaUrl("shikimori", 20, MediaType.Manga)
        )
    }

    @Test
    fun `Bangumi stabil ve ham kimligi subject sayfasina gider`() {
        assertEquals(
            "https://bgm.tv/subject/123456",
            ShareLinks.mediaUrl("bangumi", 500_123_456, MediaType.Anime)
        )
        assertEquals(
            "https://bgm.tv/subject/123456",
            ShareLinks.mediaUrl("bangumi", 123456, MediaType.Anime)
        )
    }

    @Test
    fun `Simkl turune gore movies tv anime bolumunu kullanir`() {
        assertEquals("https://simkl.com/anime/1885096", ShareLinks.mediaUrl("simkl", 1885096, MediaType.Anime))
        assertEquals("https://simkl.com/movies/53536", ShareLinks.mediaUrl("simkl", 53536, MediaType.Movie))
        assertEquals("https://simkl.com/tv/17465", ShareLinks.mediaUrl("simkl", 17465, MediaType.TvShow))
        // Offset'li stableId (Simkl içe aktarması: 150M + simklId) de çözülür.
        assertEquals("https://simkl.com/anime/1885096", ShareLinks.mediaUrl("simkl", 151_885_096, MediaType.Anime))
    }

    @Test
    fun `Simkl manga bolumu olmadigi icin uydurma adres uretmez`() {
        assertNull(ShareLinks.mediaUrl("simkl", 1234, MediaType.Manga))
    }

    @Test
    fun `Kitsu ham kimligi MAL sayfasina cevirmez`() {
        // Eski hata: 100M altındaki Kitsu kimliği MAL kimliği sanılıyordu.
        assertEquals("https://kitsu.app/anime/12611", ShareLinks.mediaUrl("kitsu", 12611, MediaType.Anime))
        assertEquals("https://kitsu.app/anime/12611", ShareLinks.mediaUrl("kitsu", 300_012_611, MediaType.Anime))
        assertEquals("https://kitsu.app/manga/1", ShareLinks.mediaUrl("kitsu", 300_000_001, MediaType.Manga))
    }

    @Test
    fun `TMDB film ve dizi yollarini ayirir`() {
        assertEquals("https://www.themoviedb.org/movie/603", ShareLinks.mediaUrl("tmdb", 603, MediaType.Movie))
        assertEquals("https://www.themoviedb.org/tv/1399", ShareLinks.mediaUrl("tmdb", 1399, MediaType.TvShow))
        // Satırın kendi kaynağı kazanır; `tmdbId` yalnızca kaynak bağlantı üretemezse kullanılır.
        assertEquals(
            "https://simkl.com/movies/53536",
            ShareLinks.mediaUrl("simkl", 53536, MediaType.Movie, tmdbId = 603)
        )
        assertEquals(
            "https://www.themoviedb.org/movie/603",
            ShareLinks.mediaUrl("tmdb", 603, MediaType.Movie)
        )
    }

    @Test
    fun `kimlik yoksa veya sifirsa baglanti uretmez`() {
        assertNull(ShareLinks.mediaUrl("mal", 0, MediaType.Anime))
        assertNull(ShareLinks.mediaUrl("anilist", null, MediaType.Anime))
        assertNull(ShareLinks.mediaUrl("manual", null, MediaType.Anime))
        // Eski hata: `myanimelist.net/anime/0`
        assertNull(ShareLinks.mediaUrl("mal", 0, MediaType.Anime))
    }

    @Test
    fun `bilinmeyen kaynak dogrulanmis bir kimlik varsa ona duser`() {
        // Bilinmeyen kaynak + geçerli tmdbId → TMDB sayfası (kırık link yok).
        assertEquals(
            "https://www.themoviedb.org/movie/603",
            ShareLinks.mediaUrl("manual", null, MediaType.Movie, tmdbId = 603)
        )
    }

    @Test
    fun `realMalId AniList sonucunda MAL baglantisini doldurur`() {
        val ids = ShareLinks.decode(
            source = "anilist",
            storedId = 100_000_021,
            realMalId = 16498
        )
        assertEquals(100_000_021 - 100_000_000, ids.aniListId)
        assertEquals(16498, ids.malId)
        assertEquals("https://anilist.co/anime/21", ShareLinks.mediaUrl(ids, "anilist", MediaType.Anime))
    }

    // ─── Karakter / ekip / stüdyo ───────────────────────────────────────────

    @Test
    fun `karakter baglantilari kaynaga ozel`() {
        assertEquals("https://myanimelist.net/character/17", ShareLinks.characterUrl("jikan", 17))
        assertEquals("https://anilist.co/character/17", ShareLinks.characterUrl("anilist", 17))
        assertEquals("https://bgm.tv/character/17", ShareLinks.characterUrl("bangumi", 17))
        assertEquals("https://shikimori.io/characters/17", ShareLinks.characterUrl("shikimori", 17))
        assertEquals("https://www.themoviedb.org/person/17", ShareLinks.characterUrl("tmdb", 17))
    }

    @Test
    fun `kitsu karakterinde MAL arama sayfasi paylasilir`() {
        // kitsu.app/characters/{id} 404 döner → uydurma yol yok, arama sayfası var.
        assertEquals(
            "https://myanimelist.net/character.php?q=Naruto+Uzumaki",
            ShareLinks.characterUrl("kitsu", 12, "Naruto Uzumaki")
        )
        assertNull(ShareLinks.characterUrl("kitsu", 12))
    }

    @Test
    fun `ekip baglantilari kaynaga ozel`() {
        assertEquals("https://myanimelist.net/people/5067", ShareLinks.staffUrl("mal", 5067))
        assertEquals("https://anilist.co/staff/5067", ShareLinks.staffUrl("anilist", 5067))
        assertEquals("https://bgm.tv/person/3818", ShareLinks.staffUrl("bangumi", 3818))
        assertEquals("https://shikimori.io/people/3818", ShareLinks.staffUrl("shikimori", 3818))
        assertEquals("https://www.themoviedb.org/person/3818", ShareLinks.staffUrl("tmdb", 3818))
        assertEquals(
            "https://myanimelist.net/people.php?q=Mamoru+Hosoda",
            ShareLinks.staffUrl("kitsu", 3818, "Mamoru Hosoda")
        )
    }

    @Test
    fun `studyo baglantilari kaynaga ozel`() {
        assertEquals("https://anilist.co/studio/569", ShareLinks.studioUrl("anilist", 569))
        assertEquals("https://myanimelist.net/anime/producer/569", ShareLinks.studioUrl("jikan", 569))
        assertEquals("https://bgm.tv/person/569", ShareLinks.studioUrl("bangumi", 569))
        assertEquals("https://shikimori.io/animes?studio=569", ShareLinks.studioUrl("shikimori", 569))
        assertEquals("https://www.themoviedb.org/company/569", ShareLinks.studioUrl("tmdb", 569))
        assertEquals("https://www.themoviedb.org/network/569", ShareLinks.studioUrl("tmdb_network", 569))
    }

    @Test
    fun `karakter kimligi offset sizintisini temizler`() {
        // Eski kayıtlar `malId` alanında stableId taşıyabiliyor.
        assertEquals("https://bgm.tv/character/17", ShareLinks.characterUrl("bangumi", 500_000_017))
        assertEquals("https://shikimori.io/characters/17", ShareLinks.characterUrl("shikimori", 400_000_017))
    }

    // ─── Profil ─────────────────────────────────────────────────────────────

    @Test
    fun `profil baglantilari kaynaga ozel`() {
        assertEquals("https://anilist.co/user/kitsugi", ShareLinks.profileUrl("anilist", "kitsugi"))
        assertEquals("https://myanimelist.net/profile/kitsugi", ShareLinks.profileUrl("mal", "kitsugi"))
        assertEquals("https://kitsu.app/users/kitsugi", ShareLinks.profileUrl("kitsu", "kitsugi"))
        assertEquals("https://shikimori.io/users/kitsugi", ShareLinks.profileUrl("shikimori", "kitsugi"))
        assertEquals("https://bgm.tv/user/kitsugi", ShareLinks.profileUrl("bangumi", "kitsugi"))
        assertEquals("https://www.themoviedb.org/u/kitsugi", ShareLinks.profileUrl("tmdb", "kitsugi"))
        // Simkl profilleri kök altında; `/user/` 404 döner.
        assertEquals("https://simkl.com/kitsugi", ShareLinks.profileUrl("simkl", "kitsugi"))
        assertNull(ShareLinks.profileUrl("simkl", "   "))
    }

    // ─── Güvenlik ağı ───────────────────────────────────────────────────────

    @Test
    fun `supheli adresler paylasilmaz`() {
        assertTrue(ShareLinks.isTrustworthy("https://anilist.co/anime/21"))
        assertTrue(ShareLinks.isTrustworthy("https://shikimori.io/animes?studio=569"))
        assertFalse(ShareLinks.isTrustworthy("https://myanimelist.net/anime/0"))
        assertFalse(ShareLinks.isTrustworthy("https://myanimelist.net/anime/500123456"))
        assertFalse(ShareLinks.isTrustworthy("http://myanimelist.net/anime/21"))
        assertFalse(ShareLinks.isTrustworthy(null))
    }
}
