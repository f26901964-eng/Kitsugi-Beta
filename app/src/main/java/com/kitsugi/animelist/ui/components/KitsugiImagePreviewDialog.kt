package com.kitsugi.animelist.ui.components

import androidx.compose.runtime.Composable

@Composable
fun KitsugiImagePreviewDialog(
    imageUrl: String,
    title: String,
    isAdult: Boolean = false,
    onDismiss: () -> Unit
) {
    KitsugiImageGalleryDialog(
        imageUrls = listOf(imageUrl),
        initialIndex = 0,
        title = title,
        isAdult = isAdult,
        onDismiss = onDismiss
    )
}
