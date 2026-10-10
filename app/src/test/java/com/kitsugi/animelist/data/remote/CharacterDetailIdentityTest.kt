package com.kitsugi.animelist.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CharacterDetailIdentityTest {

    @Before
    fun clearCache() {
        DetailCache.clear()
    }

    @Test
    fun fetchIdentityIncludesPersonIdRoleNameAndPersonVersusRoleKind() {
        val roleKey = CharacterDetailIdentity.fetchKey("tmdb", 501, "Peter Parker", false)

        assertTrue(roleKey != CharacterDetailIdentity.fetchKey("tmdb", 502, "Peter Parker", false))
        assertTrue(roleKey != CharacterDetailIdentity.fetchKey("tmdb", 501, "Spider-Man", false))
        assertTrue(roleKey != CharacterDetailIdentity.fetchKey("tmdb", 501, "Peter Parker", true))
    }

    @Test
    fun tmdbCacheIdentitySeparatesDifferentRolesForTheSamePersonId() {
        val peter = detail(501, "Peter Parker")
        val makishima = detail(501, "Makishima Yusuke")
        val peterIdentity = CharacterDetailIdentity.cacheDiscriminator("tmdb", "Peter Parker", false)
        val makishimaIdentity = CharacterDetailIdentity.cacheDiscriminator("tmdb", "Makishima Yusuke", false)

        assertTrue(peterIdentity != makishimaIdentity)
        DetailCache.putCharacterDetail("tmdb", 501, peter, peterIdentity)
        DetailCache.putCharacterDetail("tmdb", 501, makishima, makishimaIdentity)

        assertEquals(peter, DetailCache.getCharacterDetail("tmdb", 501, peterIdentity))
        assertEquals(makishima, DetailCache.getCharacterDetail("tmdb", 501, makishimaIdentity))
        assertNull("A named TMDB role must not fall back to a shared actor-ID entry", DetailCache.getCharacterDetail("tmdb", 501))
    }

    @Test
    fun tmdbRoleAliasesMatchWholeNamesAndRejectUnrelatedCharacters() {
        assertTrue(CharacterDetailIdentity.roleNameMatches("Peter Parker / Spider-Man", listOf("Spider-Man")))
        assertTrue(CharacterDetailIdentity.roleNameMatches("Peter Parker / Spider-Man", listOf("Peter Parker")))
        assertFalse(CharacterDetailIdentity.roleNameMatches("Peter Parker / Spider-Man", listOf("Makishima Yusuke")))
    }

    @Test
    fun expectedTmdbRoleIdentityRejectsWrongCharacterDetails() {
        assertTrue(
            CharacterDetailIdentity.matchesExpectedDetail(
                source = "tmdb",
                expectedName = "Peter Parker / Spider-Man",
                isRealMediaRole = false,
                detail = detail(501, "Spider-Man")
            )
        )
        assertFalse(
            CharacterDetailIdentity.matchesExpectedDetail(
                source = "tmdb",
                expectedName = "Peter Parker / Spider-Man",
                isRealMediaRole = false,
                detail = detail(501, "Makishima Yusuke")
            )
        )
    }

    @Test
    fun realPersonAndFictionalRoleCacheEntriesAreDifferent() {
        val fictionalRole = CharacterDetailIdentity.cacheDiscriminator("tmdb", "Peter Parker", false)
        val realPerson = CharacterDetailIdentity.cacheDiscriminator("tmdb", "Peter Parker", true)

        assertTrue(fictionalRole != realPerson)
        assertNull(CharacterDetailIdentity.cacheDiscriminator("jikan", "Peter Parker", false))
    }

    @Test
    fun translatedBiosAreSeparatedByTmdbRoleIdentity() {
        DetailCache.putTranslation("bio_char", "tmdb", 501, "Peter biography", "role:peter parker")
        DetailCache.putTranslation("bio_char", "tmdb", 501, "Makishima biography", "role:makishima yusuke")

        assertEquals("Peter biography", DetailCache.getTranslation("bio_char", "tmdb", 501, "role:peter parker"))
        assertEquals("Makishima biography", DetailCache.getTranslation("bio_char", "tmdb", 501, "role:makishima yusuke"))
        assertNull(DetailCache.getTranslation("bio_char", "tmdb", 501))
    }

    private fun detail(id: Int, name: String) = KitsugiCharacterDetail(
        id = id,
        name = name,
        nativeName = null,
        alternativeNames = emptyList(),
        imageUrl = null,
        gender = null,
        age = null,
        birthday = null,
        bloodType = null,
        biography = null
    )
}
