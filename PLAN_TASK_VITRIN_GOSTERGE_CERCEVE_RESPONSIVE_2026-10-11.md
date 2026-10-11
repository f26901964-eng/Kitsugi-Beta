# PLAN_TASK: Vitrin (Hero) Gösterge Çubuğu — Çerçeve Göstergesi + Responsive Genişlik

- **Tarih:** 2026-10-11
- **Dal:** `arena/308cac55-kitsugi-beta`
- **Değişen dosya:** `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiHeroSection.kt`
- **İlgili önceki rapor:** `VITRIN_TIKLAMA_VE_SAYAC_FIX_RAPORU_2026-10-11.md` (oraya eklenen sayısal sayaç, bu görevde kullanıcı isteğiyle kaldırılmıştır)

## Sorun

1. Vitrin gösterge çubuğunun yanındaki sayısal sayaç (`8 / 10` rozeti) istenmiyor; kullanıcı,
   hangi vitrin sayfasında olunduğunun doğrudan 10 çizgi üzerinden, etkin çizginin
   **çerçeve ile kapsanarak** belirtilmesini istedi.
2. Gösterge çubuğu sabit ölçülerle çiziliyordu (çizgi başına `28.dp` + `8.dp` aralık + sayaç
   rozeti). Dar ekranlarda (telefon dikey) toplam genişlik ekranı aşıyor, çubuk ekran dışına
   taşıyordu. Telefon/tablet, yatay/dikey tüm ekran ölçülerine uyum istendi.

## Çözüm

- **Sayısal sayaç kaldırıldı:** `heroCounterIndex` (derivedStateOf) tanımı ve `"N / M"` rozeti
  (Spacer + Box + Text) tamamen silindi; kullanılmayan `androidx.compose.foundation.layout.width`
  importu temizlendi.
- **Responsive genişlik:** Her gösterge çizgisi `weight(1f)` ile kullanılabilir genişliği paylaşır.
  `Row` artık `Modifier.fillMaxWidth().widthIn(max = (items.size * 34 + (items.size - 1) * 6).dp)`:
  - Dar ekranda (telefon dikey/yatay) çizgiler küçülerek **taşmadan ekrana sığar**.
  - Geniş ekranda (tablet/TV) üst sınır ile çizgilerin aşırı uzaması engellenir.
  - Aralık `8.dp` → `6.dp` yapılarak dar ekranda çizgilere daha çok yer bırakıldı.
- **Çerçeve göstergesi:** Etkin sayfanın çizgisi, dış `Box` üzerindeki `drawBehind` içinde
  `drawRoundRect(..., style = Stroke(1.5.dp))` ile çizilen yuvarlatılmış çerçeve ile kapsanır.
  Çerçeve opaklığı `heroPageVisibility(pagerState, index)` değerini izler; sayfa geçişinde
  yumuşakça belirir/kaybolur. Okuma draw aşamasında yapıldığı için 60 Hz recompose oluşmaz
  (mevcut `graphicsLayer` performans yaklaşımıyla aynı desen).
- **Derleme doğruları:**
  - `KitsugiColors.TextPrimary` bir `@Composable` getter olduğundan `drawBehind` (composable
    olmayan bağlam) içinde okunamaz; composition kapsamında `indicatorFrameColor` olarak
    yakalanıp lambda'ya kapatılır (closure).
  - `CornerRadius` tipi `androidx.compose.ui.geometry` paketindedir
    (`androidx.compose.ui.graphics` değil) — depodaki diğer `drawRoundRect` kullanımlarıyla aynı.
- Çizgi içi görünüm korunur: iç `Box` `fillMaxWidth().padding(horizontal = 3.dp)` +
  `graphicsLayer { alpha/scaleX }` ile etkin çizgi tam opak ve uzun, pasifler soluk/kısa.

## Doğrulama

- GitHub CI `android-build.yml` (`:app:compileFossDebugKotlin`, `:app:assembleFossDebug`,
  `com.kitsugi.animelist.data.remote.malweb.*` birim testleri) çalıştırıldı; ilk iki derleme
  hatası (composable bağlam + CornerRadius paketi) ikinci commit ile giderildi.
  Nihai görsel/CI doğrulaması kullanıcıya bırakıldı.
- **Manuel test maddeleri:** telefon dikey/yatay + tablet dikey/yatay: gösterge çubuğu ekran
  dışına taşmamalı; etkin çizgi çerçeve ile kapsanmalı; sayısal rozet görünmemeli; çizgiye
  dokununca ilgili vitrin sayfasına animasyonla geçilmeli.
