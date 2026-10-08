package com.kitsugi.animelist.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R

/**
 * Kaynak platformların orijinal logolarını gösteren ortak bileşen.
 * Emoji yerine tüm kaynak seçicilerde (Keşfet, Listem, Arama, Hesap Bağlantıları) kullanılır.
 *
 * [platformId] büyük/küçük harf duyarsızdır: "anilist", "mal", "myanimelist", "jikan",
 * "tmdb", "simkl", "kitsu", "shikimori", "bangumi", "fanart.tv". "all"/"tümü" için dünya ikonu gösterilir.
 */
object KitsugiPlatformLogos {
    @DrawableRes
    fun resFor(platformId: String?): Int? = when (platformId?.trim()?.lowercase()) {
        "anilist", "al" -> R.drawable.ic_logo_anilist
        "mal", "myanimelist", "jikan", "jikan (mal)", "mal (jikan)" -> R.drawable.ic_logo_mal
        "tmdb", "themoviedb" -> R.drawable.ic_logo_tmdb
        "simkl" -> R.drawable.ic_logo_simkl
        "kitsu" -> R.drawable.ic_logo_kitsu
        "shikimori", "shiki" -> R.drawable.ic_logo_shikimori
        "bangumi", "bgm" -> R.drawable.ic_logo_bangumi
        "fanart.tv", "fanart" -> R.drawable.ic_logo_fanart
        else -> null
    }
}

@Composable
fun KitsugiPlatformLogo(
    platformId: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    cornerRadius: Dp = size * 0.22f,
    shape: Shape = RoundedCornerShape(cornerRadius),
    fallbackTint: Color = Color.White
) {
    val res = KitsugiPlatformLogos.resFor(platformId)
    if (res != null) {
        Image(
            painter = painterResource(id = res),
            contentDescription = platformId,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(shape)
        )
    } else {
        Box(modifier = modifier.size(size).clip(shape), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.Public,
                contentDescription = platformId,
                tint = fallbackTint,
                modifier = Modifier.size(size * 0.9f)
            )
        }
    }
}
