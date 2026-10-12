package com.kitsugi.animelist.ui.screens.detail

import com.kitsugi.animelist.data.remote.GalleryCategory
import com.kitsugi.animelist.data.remote.GalleryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class StaffGalleryItemsTest {

    @Test
    fun `hero image is available as first item before aggregated gallery arrives`() {
        val result = ensureStaffHeroGalleryItem(
            galleryItems = emptyList(),
            imageUrl = "https://cdn.example/bryan-cranston.jpg",
            source = "TMDB",
            description = "Bryan Cranston"
        )

        assertEquals(1, result.size)
        assertEquals("https://cdn.example/bryan-cranston.jpg", result.first().url)
        assertEquals(GalleryCategory.PERSON, result.first().category)
        assertEquals("TMDB", result.first().source)
        assertEquals("Bryan Cranston", result.first().description)
    }

    @Test
    fun `existing hero is moved to the front without adding a duplicate`() {
        val other = GalleryItem(
            url = "https://cdn.example/other.jpg",
            source = "Jikan",
            category = GalleryCategory.PERSON
        )
        val hero = GalleryItem(
            url = "https://cdn.example/bryan-cranston.jpg",
            source = "TMDB",
            category = GalleryCategory.PERSON
        )

        val result = ensureStaffHeroGalleryItem(
            galleryItems = listOf(other, hero),
            imageUrl = hero.url,
            source = "TMDB",
            description = "Bryan Cranston"
        )

        assertEquals(listOf(hero, other), result)
        assertSame(hero, result.first())
    }

    @Test
    fun `blank hero leaves the existing gallery unchanged`() {
        val existing = GalleryItem(
            url = "https://cdn.example/other.jpg",
            source = "Jikan",
            category = GalleryCategory.PERSON
        )

        val existingItems = listOf(existing)
        val result = ensureStaffHeroGalleryItem(
            galleryItems = existingItems,
            imageUrl = " ",
            source = "TMDB",
            description = null
        )

        assertSame(existingItems, result)
    }
}
