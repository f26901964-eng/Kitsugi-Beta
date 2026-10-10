package com.kitsugi.animelist.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.ui.theme.KitsugiColors

/**
 * Çapraz eşitleme yüzeylerinde gösterilen yasal uyarılar.
 *
 * Metinler tek kaynak olarak burada tutulur; ekranlar bu sabitleri kullanır.
 * Not: Bu metinler hukuki danışmanlık yerine geçmez; yayın öncesi bir hukukçu tarafından gözden geçirilmelidir.
 */
object CrossSyncDisclaimer {

    /** Tek satırlık kısa uyarı (hesap bağlantıları satırı gibi dar alanlar için). */
    const val SHORT =
        "Deneysel özellik. Yanlış eşleşme, liste karışması veya platform yaptırımlarından (ban vb.) sorumluluk kabul edilmez."

    /** Ayrıntılı uyarı (eşitleme sayfası ve eşitleme penceresi için). */
    const val FULL =
        "Çapraz eşitleme DENEYSEL bir özelliktir ve beta aşamasındadır. Eşleştirmeler otomatik yapılır; " +
            "hiçbir eşleştirme %100 doğruluk garantisi taşımaz. Yanlış eşleşme, liste karışması, yanlış " +
            "ilerleme/puan/durum bilgisi veya AniList, MyAnimeList, Simkl, Kitsu, Shikimori ve Bangumi tarafından uygulanan " +
            "kısıtlama, askıya alma ya da hesap yasağı gibi sonuçlardan Kitsugi ve geliştiricileri sorumlu tutulamaz. " +
            "Özellik kendi sorumluluğunuzda kullanılır; önemli listelerinizi eşitlemeden önce yedekleyin. " +
            "Kanunen sınırlandırılamayan haller (ağır kusur gibi) saklıdır. Kullanım, ilgili platformların " +
            "kullanım koşullarına tabidir."
}

/**
 * Uyarı metnini turuncu bir ikonla birlikte gösterir.
 *
 * @param full true ise ayrıntılı metin, false ise kısa metin kullanılır.
 */
@Composable
fun CrossSyncDisclaimerText(
    modifier: Modifier = Modifier,
    full: Boolean = true
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Rounded.WarningAmber,
            contentDescription = null,
            tint = KitsugiColors.AccentOrange,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(
                text = if (full) CrossSyncDisclaimer.FULL else CrossSyncDisclaimer.SHORT,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                color = KitsugiColors.TextMuted
            )
        }
    }
}
