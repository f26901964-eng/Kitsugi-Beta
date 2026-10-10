package com.kitsugi.animelist.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.model.MediaType

/**
 * Medya türü etiketlerini UYGULAMA DİLİNE göre çözümleyen tek merkez.
 *
 * Eskiden her ekran kendi `when (type) { Anime -> "Anime"; TvShow -> "Dizi" ... }`
 * eşlemesini sert kodluyordu; İngilizce arayüzde bile Türkçe etiketler görünüyordu.
 * Tüm ekranlar bu yardımcıyı kullanır: değerler strings.xml (values / values-en)
 * üzerinden geldiği için dil değişimi anında yansır.
 */
/** Tür → string resource kimliği (Composable olmayan bağlamlarda context.getString için). */
fun MediaType.labelRes(): Int = when (this) {
    MediaType.Anime -> R.string.media_type_anime
    MediaType.Manga -> R.string.media_type_manga
    MediaType.Movie -> R.string.media_type_movie
    MediaType.TvShow -> R.string.media_type_tv
}

@Composable
@ReadOnlyComposable
fun MediaType.localizedLabel(): String = stringResource(labelRes())
