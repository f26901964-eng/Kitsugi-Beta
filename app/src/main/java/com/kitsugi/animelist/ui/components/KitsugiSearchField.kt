package com.kitsugi.animelist.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Kitsugi genel arama alanı bileşeni.
 * Uiverse (Lakshay-art) Cosmic Arama Barına bağlanarak tüm arama alanlarında
 * dönen konik degrade ışık huzmelerini ve dokunmatik uyumlu siber estetiği sunar.
 */
@Composable
fun KitsugiSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    KitsugiCosmicSearchBar(
        query = value,
        onQueryChange = onValueChange,
        onSearch = {},
        onClearQuery = { onValueChange("") },
        placeholder = placeholder,
        height = 52.dp,
        modifier = modifier.fillMaxWidth()
    )
}