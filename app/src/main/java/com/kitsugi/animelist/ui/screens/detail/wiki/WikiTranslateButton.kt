package com.kitsugi.animelist.ui.screens.detail.wiki

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.kitsugi.animelist.ui.components.KitsugiOriginalToggleButton
import com.kitsugi.animelist.ui.components.KitsugiTranslateIconButton
import com.kitsugi.animelist.ui.components.LocalKitsugiTranslationLoading
import com.kitsugi.animelist.utils.KitsugiTranslateUtils.openTranslator

/**
 * Wiki kartlarındaki çeviri butonu — detay sayfasındaki **Açıklama** kartının çeviri
 * butonuyla ([com.kitsugi.animelist.ui.screens.detail.DetailSynopsisCard] →
 * [KitsugiTranslateIconButton]) birebir aynı mantık:
 *
 * 1. **Her zaman görünür:** Metin çevrilmiş olsa da ikon yerinde kalır.
 * 2. **Dokununca metin HARİCİ çeviri uygulamasında açılır** (`openTranslator`): kullanıcının
 *    tercih sırasına göre Google Translate / DeepL / TranslateYou; hiçbiri yoksa Google
 *    Translate web. Uygulama içi çeviri yalnızca ayarlardaki **otomatik çeviri** ile yapılır.
 * 3. **Yüklenme animasyonu:** Uygulama içi (otomatik) çeviri sürerken ikon nabız gibi yanıp
 *    söner ve etrafında dönen halka görünür.
 * 4. **Orijinal / Çeviri geçişi:** Uygulama içi çeviri ekranda gösterildiğinde
 *    ([hasTranslation] = true) ikonun solunda "Orijinal" düğmesi çıkar; dokununca orijinal
 *    metin geri gelir ([showingOriginal] = true iken "Çeviri" yazar).
 *
 * @param text harici uygulamaya gönderilecek **orijinal** (çevrilmemiş) metin.
 * @param preferredTranslator ayarlardaki çeviri uygulaması tercihi (\"GOOGLE\", \"DEEPL\", …).
 * @param isLoading bu metnin uygulama içi çevirisi sürüyor mu (VM'in `translating` kümesi).
 * @param hasTranslation bu metnin uygulama içi çevirisi ekranda var mı.
 * @param showingOriginal şu an orijinal metin gösteriliyor mu.
 * @param onToggleOriginal orijinal/çeviri geçişi; null ise geçiş düğmesi hiç çizilmez.
 */
@Composable
internal fun WikiTranslateButton(
    text: String?,
    preferredTranslator: String,
    accentColor: Color,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
    hasTranslation: Boolean = false,
    showingOriginal: Boolean = false,
    onToggleOriginal: (() -> Unit)? = null
) {
    if (text.isNullOrBlank()) return
    val context = LocalContext.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (hasTranslation && onToggleOriginal != null) {
            KitsugiOriginalToggleButton(
                showingOriginal = showingOriginal,
                onClick = onToggleOriginal,
                tint = accentColor
            )
        }
        KitsugiTranslateIconButton(
            onClick = { context.openTranslator(text, preferredTranslator) },
            isLoading = isLoading || LocalKitsugiTranslationLoading.current,
            tint = accentColor
        )
    }
}
