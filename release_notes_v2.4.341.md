# Kitsugi Beta v2.4.341 — Manga Okuyucu: Gerçek Tam Ekran + Okuma Deneyimi

## 🇹🇷 Türkçe

### 🖥️ Tam Ekran Artık Gerçekten Tam Ekran
- **Kök neden:** Alt bardaki "tam ekran" sanılan ikon yalnızca sığdırma modunu değiştiriyordu; sistem çubuklarını gizleyen hiçbir kod yoktu. Yeni `MangaReaderSystemUi` bileşeni durum çubuğu, gezinme çubuğu ve tablet görev çubuğunu gizler (`hide(systemBars)`), kenardan kaydırınca geçici olarak gösterir, okuyucudan çıkınca geri getirir.
- **Ayrı tam ekran düğmesi:** Üst ve alt barda `Fullscreen / FullscreenExit` ikonu; basınca hem sistem çubukları hem uygulamanın kendi barları gizlenir. Sığdırma modu artık ayrı ikon.
- **Ekranı açık tut:** Okuma boyunca ekran kapanmaz (kapatılabilir).
- **Dokunma bölgeleri düzeltildi:** Merkeze dokununca barların açılmamasının nedeni, dokunuşun zoom katmanı tarafından yutulmasıydı. Olayları tüketmeyen yeni dedektör sayesinde sol/orta/sağ dokunma, uzun basma ve pinch-zoom aynı anda çalışır.

### 📖 Okuma Modları
- **"Dikey" modu artık dikey:** Önceden yatay sayfalama çiziliyordu; yeni `MangaVerticalPagerReader` ile gerçek dikey sayfa geçişi.
- **Yeni sığdırma modları:** Doldur (kırp) ve Orijinal boyut.
- **Yeni renk filtreleri:** Sıcak, Soğuk, Sepya.
- **Aynalanmış sayfalar:** "Sayfaları aynala" anahtarı ile ters servis edilen sayfalar düzeltilir.

### ⚙️ Ayar Kimlikleri Düzeltildi (sessiz ayar kaybı)
- Ayarlar ekranı `GrayScale / Inverted / Warm / Cool / WebtoonZoom` yazıyor, okuyucu `Grayscale / Invert / Webtoon` bekliyordu. Bu yüzden bu seçimler sessizce yok sayılıyordu. Tek doğruluk kaynağı (`MangaSettingIds`) eklendi; eski değerler geriye dönük uyumlu okunuyor.

### 💾 İlerleme
- Webtoon'da yanlış sayfa kaydedilmesi, bölüm değişiminde konumun sıfırlanmaması ve son sayfanın (debounce yüzünden) kaybolması düzeltildi.
- Parlaklık sürgüsü anında tepki verir; diske yazma 250 ms geciktirilir.

### 🧹 Temizlik
- Ölü `MangaPageLoader` (v1), kullanılmayan `MangaScreen` + ikinci `MangaViewModel` silindi.
- Okuyucu ekranındaki 450 satırlık UI bloğu ve 3 kez kopyalanmış chip bloğu tek bileşene indirildi.
- Bölüm listesi paneli, bölüm sonu geçiş kartı anahtarı ve ses tuşlarıyla sayfa geçişi eklendi.

---

## 🇬🇧 English

### 🖥️ Fullscreen Is Now Actually Fullscreen
- **Root cause:** The icon believed to enable fullscreen only cycled the fit mode; nothing ever hid the system bars. New `MangaReaderSystemUi` hides status/navigation/task bars, shows them transiently on edge swipes, and restores them when leaving the reader.
- **Dedicated fullscreen button** in the top and bottom bars (`Fullscreen / FullscreenExit`); it hides both system bars and the in-app bars. Fit mode is now a separate control.
- **Keep screen on** while reading (optional).
- **Tap zones fixed:** taps were swallowed by the zoom layer. A new observer runs on the Initial pass without consuming events, so left/center/right taps, long-press and pinch-zoom all work together.

### 📖 Reading Modes
- **"Vertical" mode is now vertical** (previously it silently rendered the horizontal pager).
- **New fit modes:** Fill (crop) and Original size.
- **New color filters:** Warm, Cool, Sepia.
- **Mirror pages** toggle to fix pages served mirrored/flipped.

### ⚙️ Settings IDs Fixed (silent setting loss)
- Settings wrote `GrayScale / Inverted / Warm / Cool / WebtoonZoom` while the reader expected `Grayscale / Invert / Webtoon`, so those choices were silently ignored. `MangaSettingIds` is now the single source of truth, with backward-compatible parsing.

### 💾 Progress
- Fixed wrong page saves in webtoon mode, stale position on chapter change, and lost last-page progress (debounce flush on exit).
- Brightness slider updates instantly; disk writes are debounced by 250 ms.

### 🧹 Cleanup
- Removed dead `MangaPageLoader` (v1), unused `MangaScreen` and the duplicate `MangaViewModel`.
- Extracted the reader's 450-line UI block and the 3 duplicated chip blocks into shared components.
- Added a chapter list sheet, a chapter-transition toggle and volume-key page turning.
