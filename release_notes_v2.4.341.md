# Kitsugi Beta v2.4.341

## 🇹🇷 Türkçe

### 🖼️ Detay Vitrini Yön Seçimi Düzeltildi (Dikey Modda Doğru Görsel, Yüklemeden Önce)
- **0.75 eşiği geri alındı (0.55):** v2.4.338'de dikey mod poster eşiği `0.55 → 0.75` yükseltilmişti; sonuçta neredeyse tüm dikey ekranlar (16:9 ve genişliği) ekranı yutan **tam boy dikey poster** gösteriyordu. Eşik orijinal `0.55` değerine geri alındı: uzun-dar telefonlar (≤ 0.55) dikey poster, 16:9 ve daha geniş dikey ekranlar yatay arka plan bandı gösterir.
- **Büyük ekran koruması (`PORTRAIT_TABLET_MIN_WIDTH_DP = 600`):** Dikey modda tablet/katlanabilir genişliğindeki ekranlar (≥ 600 dp) oranına bakılmaksızın yatay arka plan gösterir; dev posterin ekranı yutması tamamen engellendi.
- **Karar sayfa yüklenmeden verilir:** Tercih yalnızca ekran yönü + oranı + mutlak genişliğinden türetilir (ağ/görsel boyutu beklemez). Ayrıca ViewModel, vitrin için **tercihe uygun yöndeki** TMDB/Fanart adaylarını sayfa açılmadan decode eder — ilk kareden itibaren doğru yön basılır, "önce yanlış yön sonra flip" yok.
- **Tek kural, iki vitrin:** `MediaEntryDetailPage` vitrini (`DetailHero`) de artık `ApiResultDetailPage` ile aynı `resolveHeroArtworkPreference` kuralını kullanıyor; tüm detay sayfalarında tutarlı davranış.

### 🧭 Kaydırınca Beliren Üst Şerit Geri Geldi (Detay + Karakter + Ekip)
- **Kök neden:** Yapışkan sekme barı viewport tepesine sabitlendiğinde `firstVisibleItemIndex` 1'de kilitleniyordu; eski `showAtIndex = 3` (detay) / `2` (karakter, ekip) eşiklerine hiç ulaşılamıyordu — aşağı kaydırınca geri/galeri/paylaşım/favori şeridi bir daha belirmiyordu.
- **Düzeltme:** Eşik hero sınırının ta kendisine taşındı (`showAtIndex = 1, hideAtIndex = 0`): hero tamamen ekran dışına çıkar çıkmaz şerit, `KitsugiStickyHeaderReveal` akıcı kayma + solma animasyonuyla sekme barının hemen üstünde belirir; hero'ya geri kaydırınca yumuşakça saklanır.

---

## 🇬🇧 English

### 🖼️ Detail Hero Orientation Selection Fixed (Correct Portrait-Mode Artwork, Decided Before Load)
- **0.75 threshold reverted to 0.55:** v2.4.338 raised the portrait poster threshold from `0.55` to `0.75`, making almost every portrait screen (16:9 and wider) show a screen-swallowing full-height poster. Restored the original `0.55`: tall-narrow phones (≤ 0.55) get the vertical poster, 16:9-and-wider portrait screens get the landscape backdrop band.
- **Large-screen guard (`PORTRAIT_TABLET_MIN_WIDTH_DP = 600`):** Portrait layouts at tablet/foldable width (≥ 600 dp) always prefer the landscape backdrop regardless of ratio, so a giant poster can never consume the screen.
- **Decision made before the page loads:** The preference derives purely from orientation + ratio + absolute width (no network/image-size wait). The ViewModel additionally pre-decodes the **preference-matching orientation** candidates before the page renders — the first frame already shows the correct direction, with no wrong-orientation flash.
- **One rule, both heroes:** The `MediaEntryDetailPage` hero (`DetailHero`) now shares the exact same `resolveHeroArtworkPreference` rule as `ApiResultDetailPage`, giving consistent behavior across all detail pages.

### 🧭 Scroll-Revealed Top Bar Restored (Detail + Character + Staff)
- **Root cause:** With the sticky tab bar pinned to the viewport top, `firstVisibleItemIndex` stays locked at 1; the old `showAtIndex = 3` (detail) / `2` (character, staff) thresholds could never be reached, so the back/gallery/share/favorite bar stopped appearing after scrolling down.
- **Fix:** The threshold now sits at the hero boundary itself (`showAtIndex = 1, hideAtIndex = 0`): as soon as the hero fully leaves the viewport, the bar slides in above the tab bar with the smooth `KitsugiStickyHeaderReveal` motion, and hides softly when scrolling back to the hero.

### ✅ Tests
- `DetailHeroGallerySelectionTest` updated for the restored rules: tall-narrow phones → POSTER; 16:9/wide portrait (incl. the reported 462×800 dp device) → BACKDROP; ≥ 600 dp portrait screens → BACKDROP regardless of ratio.
