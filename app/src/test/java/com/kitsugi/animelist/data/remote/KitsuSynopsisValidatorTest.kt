package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaType
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class KitsuSynopsisValidatorTest {

    private val roarFoxSynopsis =
        "Roar is an American television show that originally aired on the Fox network in July 1997. " +
            "In the year AD 400, a young Irish man, Conor, sets out to rid his land of the invading Romans, " +
            "but in order to accomplish this, he must unite the Celtic clans."

    @Test
    fun flagsFoxShowSynopsisOnAnimeEntry() {
        // Gerçek olay: "ROAR" (2004, anime) Kitsu kaydı 1997 Fox TV dizisinin özetini taşıyor.
        val reason = KitsuSynopsisValidator.suspiciousReason(roarFoxSynopsis, entryYear = 2004, mediaType = MediaType.Anime)
        assertNotNull(reason)
    }

    @Test
    fun flagsFoxShowSynopsisOnMovieEntry() {
        val reason = KitsuSynopsisValidator.suspiciousReason(roarFoxSynopsis, entryYear = 2004, mediaType = MediaType.Movie)
        assertNotNull(reason)
    }

    @Test
    fun flagsAiringYearContradiction() {
        // Özet "aired/premiered" bağlamında farklı bir yıl söylüyor → çelişki.
        val reason = KitsuSynopsisValidator.suspiciousReason(
            "The film was released in 1997 and became a classic.",
            entryYear = 2004,
            mediaType = MediaType.Anime
        )
        assertNotNull(reason)
    }

    @Test
    fun allowsTvShowEntryDescribingItself() {
        // TvShow türünde "American television show" ifadesi makuldur (canlı-aksiyon katalogu).
        assertNull(
            KitsuSynopsisValidator.suspiciousReason(roarFoxSynopsis, entryYear = 1997, mediaType = MediaType.TvShow)
        )
    }

    @Test
    fun allowsHealthyAnimeSynopsis() {
        assertNull(
            KitsuSynopsisValidator.suspiciousReason(
                "A young ninja seeks revenge against the clan that betrayed him.",
                entryYear = 2004,
                mediaType = MediaType.Anime
            )
        )
    }

    @Test
    fun allowsSynopsisWithMatchingAirYear() {
        assertNull(
            KitsuSynopsisValidator.suspiciousReason(
                "The series aired from April 2010 to June 2010.",
                entryYear = 2010,
                mediaType = MediaType.Anime
            )
        )
    }

    @Test
    fun allowsStoryYearWithoutAiringVerb() {
        // Hikâye içinde geçen yıl ("In July 1997, ...") şüpheli sayılmaz — fiil yok.
        assertNull(
            KitsuSynopsisValidator.suspiciousReason(
                "The story takes place in July 1997, when a boy discovers a mysterious door.",
                entryYear = 2004,
                mediaType = MediaType.Anime
            )
        )
    }

    @Test
    fun allowsKoreanDramaStyleSynopsis() {
        // Kitsu anime uç noktasında K-dizileri makul ölçüde bulunur; "Korean" kapsam dışı.
        assertNull(
            KitsuSynopsisValidator.suspiciousReason(
                "This is a South Korean television series about a chef.",
                entryYear = 2020,
                mediaType = MediaType.Anime
            )
        )
    }

    @Test
    fun allowsJapaneseAnimeDescribingItself() {
        assertNull(
            KitsuSynopsisValidator.suspiciousReason(
                "Elfen Lied is a Japanese anime television series.",
                entryYear = 2004,
                mediaType = MediaType.Anime
            )
        )
    }

    @Test
    fun blankSynopsisIsNotSuspicious() {
        assertNull(KitsuSynopsisValidator.suspiciousReason(null, entryYear = 2004, mediaType = MediaType.Anime))
        assertNull(KitsuSynopsisValidator.suspiciousReason("", entryYear = 2004, mediaType = MediaType.Anime))
        assertNull(KitsuSynopsisValidator.suspiciousReason("   ", entryYear = 2004, mediaType = MediaType.Anime))
    }

    @Test
    fun flagsAmericanLiveActionPhrase() {
        assertNotNull(
            KitsuSynopsisValidator.suspiciousReason(
                "An American live-action television series follows a family in the 1950s.",
                entryYear = 2015,
                mediaType = MediaType.Anime
            )
        )
    }

    @Test
    fun flagsPremieredYearContradiction() {
        assertNotNull(
            KitsuSynopsisValidator.suspiciousReason(
                "The anime premiered in 2018.",
                entryYear = 2004,
                mediaType = MediaType.Anime
            )
        )
        assertNull(
            KitsuSynopsisValidator.suspiciousReason(
                "The anime premiered in 2018.",
                entryYear = 2018,
                mediaType = MediaType.Anime
            )
        )
    }
}
