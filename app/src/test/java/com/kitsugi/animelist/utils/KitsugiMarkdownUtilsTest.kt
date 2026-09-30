package com.kitsugi.animelist.utils

import com.kitsugi.animelist.data.remote.cleanApiText
import com.kitsugi.animelist.utils.KitsugiMarkdownUtils.formatAniListMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KitsugiMarkdownUtilsTest {

    @Test
    fun testCleanShikimoriBbCode_characterTags() {
        val input = "Uzun maceralar ve birçok denemeden sonra arkadaşlar evlerine döndüler ama yolculuğun sonu yalnızca başlangıçtı. [character=111335]Eris[/character], [character=111245]Rudeus[/character]'dan ayrılıp kendi yolculuğuna çıktıktan sonra, yalnızlıktan bunalan o, tek bir hedefle Kuzey'e taşındı: annesini bulmak. [character=111245]Rudeus[/character] hayata olan tüm ilgisini kaybetmiştir."
        val expected = "Uzun maceralar ve birçok denemeden sonra arkadaşlar evlerine döndüler ama yolculuğun sonu yalnızca başlangıçtı. Eris, Rudeus'dan ayrılıp kendi yolculuğuna çıktıktan sonra, yalnızlıktan bunalan o, tek bir hedefle Kuzey'e taşındı: annesini bulmak. Rudeus hayata olan tüm ilgisini kaybetmiştir."
        
        assertEquals(expected, input.cleanShikimoriBbCode())
    }

    @Test
    fun testCleanShikimoriBbCode_rudeusBioAndSpoilers() {
        val input = "Possesses an exorbitant amount of magical energy, and therefore the amount of his mana is the largest in the world.\nHe married [character=111337]Sylphy[/character], [character=111341]Roxy[/character] and, in his own words, married [character=111335]Eris[/character]. After defeating the Immortal Demon Queen, [character=118161]Atoferatofe Raibaku[/character] received the title of Hero, thanks to which all the Demon Kings fear him. In an alternate timeline, Rudeus was tricked by [character=118253]Hitogami[/character] and lost his loved ones."
        val expected = "Possesses an exorbitant amount of magical energy, and therefore the amount of his mana is the largest in the world.\nHe married Sylphy, Roxy and, in his own words, married Eris. After defeating the Immortal Demon Queen, Atoferatofe Raibaku received the title of Hero, thanks to which all the Demon Kings fear him. In an alternate timeline, Rudeus was tricked by Hitogami and lost his loved ones."
        
        assertEquals(expected, input.cleanShikimoriBbCode())
    }

    @Test
    fun testCleanShikimoriBbCode_sylphietteBioAndSpoiler() {
        val input = "Childhood friend of [character=111245]Rudeus Greyrat[/character]. She met [character=111245]Rudeus[/character] when he helped her."
        val expected = "Childhood friend of Rudeus Greyrat. She met Rudeus when he helped her."
        assertEquals(expected, input.cleanShikimoriBbCode())

        val spoilerInput = "Later marries [character=111245]Rudeus[/character] and gives birth to a girl [character=115183]Lucy[/character] and a boy [character=123813]Sieghardt Saladin[/character]."
        val spoilerExpected = "Later marries Rudeus and gives birth to a girl Lucy and a boy Sieghardt Saladin."
        assertEquals(spoilerExpected, spoilerInput.cleanShikimoriBbCode())
    }

    @Test
    fun testCleanShikimoriBbCode_otherTags() {
        val input = "Voiced by [person=123]Kenjiro Tsuda[/person], anime: [anime=456]Mushoku Tensei[/anime], manga: [manga=789]Mushoku Tensei[/manga]."
        val expected = "Voiced by Kenjiro Tsuda, anime: Mushoku Tensei, manga: Mushoku Tensei."
        assertEquals(expected, input.cleanShikimoriBbCode())

        val standalone = "Test [characters ids=1,2,3 columns=4] and [character=123] done."
        val standaloneExpected = "Test  and  done."
        assertEquals(standaloneExpected, standalone.cleanShikimoriBbCode())
    }

    @Test
    fun testCleanApiText_integration() {
        val apiText = "Description with [character=111245]Rudeus[/character] and &quot;quotes&quot;<br />New line."
        val cleaned = apiText.cleanApiText()
        assertEquals("Description with Rudeus and \"quotes\"\nNew line.", cleaned)
    }

    @Test
    fun testFormatAniListMarkdown_cleansCharacters() {
        val input = "Friend of [character=111245]Rudeus[/character]."
        val formatted = input.formatAniListMarkdown()
        assertEquals("Friend of Rudeus.", formatted.trim())
    }
}
