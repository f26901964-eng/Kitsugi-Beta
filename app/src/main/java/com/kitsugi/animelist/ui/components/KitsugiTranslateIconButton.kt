package com.kitsugi.animelist.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Icon

/**
 * Otomatik (uygulama içi) çevirinin sürüp sürmediği.
 *
 * Detay sayfalarının kökünde `CompositionLocalProvider` ile sağlanır: sayfa otomatik
 * çeviri yaparken (açıklama / biyografi / stüdyo "Hakkında" metni) içerideki tüm çeviri
 * butonları, tıpkı galeri butonunun görsel yüklenirken gösterdiği gibi, etrafında dönen
 * halka + nabız animasyonu gösterir. Böylece kullanıcı metnin hâlâ çevrildiğini anlar.
 *
 * Butonların kendi yerel yüklenme durumu varsa ([KitsugiTranslateIconButton]'un
 * `isLoading` parametresi) bu değer yerel değeri geçersiz kılar.
 */
val LocalKitsugiTranslationLoading = compositionLocalOf { false }

/**
 * Çeviri ikon butonu — galeri ikon butonuyla ([KitsugiGalleryIconButton]) birebir aynı
 * yüklenme animasyonunu kullanır.
 *
 * - Yükleniyor ([isLoading] = true): ikon nabız gibi yanıp söner, etrafında dönen bir halka
 *   görünür ve buton varsayılan olarak dokunulamaz ([enabled] = false).
 * - Hazır: ikon normal parlaklıkta, dokunulabilir.
 *
 * Boyutlar çağrı yerine göre ayarlanır: `iconSize` ikon boyutu, `ringSize` yüklenme
 * halkasının çapıdır (buton kutusunu büyütmemesi için kutudan küçük seçilmelidir).
 */
@Composable
fun KitsugiTranslateIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = LocalKitsugiTranslationLoading.current,
    tint: Color = LocalKitsugiAccent.current,
    icon: ImageVector = Icons.Rounded.Translate,
    contentDescription: String? = null,
    iconSize: Dp = 24.dp,
    ringSize: Dp = 32.dp,
    strokeWidth: Dp = 2.dp,
    enabled: Boolean = !isLoading
) {
    val resolvedDescription = contentDescription ?: stringResource(R.string.action_translate)
    val transition = rememberInfiniteTransition(label = "translateLoadingTransition")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.30f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "translateLoadingPulse"
    )

    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(ringSize)) {
            Icon(
                imageVector = icon,
                contentDescription = resolvedDescription,
                tint = tint,
                modifier = Modifier
                    .size(iconSize)
                    .alpha(if (isLoading) pulseAlpha * 0.6f else 1f)
            )
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(ringSize),
                    color = tint,
                    strokeWidth = strokeWidth
                )
            }
        }
    }
}

/**
 * Uygulama içi (otomatik) çeviri ekranda gösterilirken orijinal metne geçiş düğmesi.
 *
 * Çeviri gösterilirken "Orijinal" yazar ve dokununca orijinal metin geri gelir; orijinal
 * gösterilirken "Çeviri" yazar ve dokununca çeviri tekrar gösterilir. Çeviri çoğunlukla
 * otomatik olarak yapıldığı için, bu düğme kullanıcıya her zaman orijinal metne dönme
 * imkânı verir. Yalnızca çeviri mevcutken çağrılmalıdır.
 *
 * @param showingOriginal şu an orijinal metin gösteriliyor mu.
 * @param onClick geçişi yapan callback (durumu çağıran taraf tutar).
 */
@Composable
fun KitsugiOriginalToggleButton(
    showingOriginal: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalKitsugiAccent.current
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = stringResource(
                if (showingOriginal) R.string.translate_show_translation else R.string.translate_show_original
            ),
            color = tint,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
