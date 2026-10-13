package com.kitsugi.animelist.ui.screens.detail.wiki

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.kitsugi.animelist.ui.components.KitsugiTranslateIconButton
import com.kitsugi.animelist.ui.components.LocalKitsugiTranslationLoading
import com.kitsugi.animelist.utils.KitsugiTranslateUtils.openTranslator

/**
 * Wiki kartlarındaki çeviri butonu — detay sayfasındaki **Açıklama** kartının çeviri
 * butonuyla ([com.kitsugi.animelist.ui.screens.detail.DetailSynopsisCard] →
 * [KitsugiTranslateIconButton]) birebir aynı mantık:
 *
 * 1. **Her zaman görünür:** Metin çevrilmiş olsa da ikon yerinde kalır; "Orijinali göster"
 *    gibi bir yazı düğmesiyle yer değiştirmez. Böylece wiki sekmelerindeki tüm çeviri
 *    düğmeleri detay sayfasındaki açıklama düğmesinin aynısı olur.
 * 2. **Dokununca metin HARİCİ çeviri uygulamasında açılır** (`openTranslator`): kullanıcının
 *    tercih sırasına göre Google Translate / DeepL / TranslateYou; hiçbiri yoksa Google
 *    Translate web. Uygulama içi çeviri yalnızca ayarlardaki **otomatik çeviri** ile yapılır.
 * 3. **Yüklenme animasyonu:** Uygulama içi (otomatik) çeviri sürerken ikon nabız gibi yanıp
 *    söner ve etrafında dönen halka görünür — galeri butonuyla aynı animasyon. Animasyon
 *    [isLoading] (bu metnin çevirisi) ya da sayfa düzeyindeki
 *    [LocalKitsugiTranslationLoading] (sayfanın açıklama/biyografi çevirisi) ile tetiklenir.
 *
 * @param text harici uygulamaya gönderilecek **orijinal** (çevrilmemiş) metin.
 * @param preferredTranslator ayarlardaki çeviri uygulaması tercihi ("GOOGLE", "DEEPL", …).
 * @param isLoading bu metnin uygulama içi çevirisi sürüyor mu (VM'in `translating` kümesi).
 */
@Composable
internal fun WikiTranslateButton(
    text: String?,
    preferredTranslator: String,
    accentColor: Color,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (text.isNullOrBlank()) return
    val context = LocalContext.current
    KitsugiTranslateIconButton(
        onClick = { context.openTranslator(text, preferredTranslator) },
        modifier = modifier,
        isLoading = isLoading || LocalKitsugiTranslationLoading.current,
        tint = accentColor
    )
}
