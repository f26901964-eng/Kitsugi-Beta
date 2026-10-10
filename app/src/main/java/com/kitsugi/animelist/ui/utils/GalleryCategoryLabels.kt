package com.kitsugi.animelist.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.data.remote.GalleryCategory

/**
 * Galeri kategori etiketini UYGULAMA DİLİNE göre çözümler.
 * Etiketler strings.xml (values / values-en) üzerinden gelir.
 */
@Composable
@ReadOnlyComposable
fun GalleryCategory.localizedLabel(): String = stringResource(labelRes)
