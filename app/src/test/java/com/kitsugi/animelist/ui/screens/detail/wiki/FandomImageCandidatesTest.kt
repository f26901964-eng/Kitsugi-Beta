package com.kitsugi.animelist.ui.screens.detail.wiki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wiki görseli yüklenemezse sırayla denenecek adresler: ölçekli → orijinal → format=original. */
class FandomImageCandidatesTest {

    private val url = "https://static.wikia.nocookie.net/shingekinokyojin/images/2/2b/Eren.png/revision/latest?cb=20200101"

    @Test
    fun `builds scaled, original and forced format chain without duplicates`() {
        val chain = FandomImageCandidates.build(url, 360)
        assertEquals(3, chain.size)
        assertTrue(chain[0].contains("/scale-to-width-down/360"))
        assertEquals(url, chain[1])
        assertTrue(chain[2].endsWith("&format=original"))
        assertEquals(chain.size, chain.distinct().size)
    }

    @Test
    fun `non fandom urls yield a single candidate`() {
        val plain = "https://example.com/a.png"
        assertEquals(listOf(plain), FandomImageCandidates.build(plain, 200))
    }
}
