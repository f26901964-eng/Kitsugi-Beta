package com.kitsugi.animelist.ui.screens.detail

import com.kitsugi.animelist.data.remote.GalleryCategory
import com.kitsugi.animelist.data.remote.GalleryItem

/**
 * Keep the staff hero image available as the first gallery item while the remaining
 * person-image sources are still being fetched. The same helper is used for the
 * progressive result and the completed gallery so the hero always opens at index 0.
 */
internal fun ensureStaffHeroGalleryItem(
    galleryItems: List<GalleryItem>,
    imageUrl: String?,
    source: String,
    description: String?
): List<GalleryItem> {
    val heroUrl = imageUrl?.takeIf { it.isNotBlank() } ?: return galleryItems
    val heroIndex = galleryItems.indexOfFirst { it.url == heroUrl }
    if (heroIndex == 0) return galleryItems

    val heroItem = if (heroIndex >= 0) {
        galleryItems[heroIndex]
    } else {
        GalleryItem(
            url = heroUrl,
            source = source,
            category = GalleryCategory.PERSON,
            description = description
        )
    }

    return buildList {
        add(heroItem)
        galleryItems.forEachIndexed { index, item ->
            if (index != heroIndex && item.url != heroUrl) add(item)
        }
    }
}
