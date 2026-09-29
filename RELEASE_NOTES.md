# Kitsugi-Beta — Sürüm Notları / Release Notes

---

## 🇹🇷 Türkçe (v2.4.152)

### 🔍 Kitsu & Simkl Arama Düzeltmesi, Galeri Bildirim Çubuğu Mesafesi & Performans

- **Kitsu Arama Düzeltildi:** Kitsu API'sinin 20'den fazla sayfa boyutu isteklerinde HTTP 400 hatası vererek sonuçları boş döndürmesi sorunu giderildi. Kitsu sekmesinde artık tüm sonuçlar eksiksiz listelenir.
- **Simkl Arama Düzeltildi:** Simkl API'sinde var olmayan genel arama yerine anime, dizi ve film uç noktaları eşzamanlı taranarak birleştirildi; artık Simkl sekmesinde hem anime hem film/dizi sonuçları anında geliyor.
- **Galeri Butonları Bildirim Çubuğundan Uzaklaştırıldı:** Resim galerisi tam ekran görünümünde üst kategori butonlarının telefonun bildirim çubuğuna (saat, pil, Wi-Fi simgeleri) yapışarak basılmasını zorlaştırması sorunu `statusBarsPadding` ve güvenli dokunma mesafesiyle düzeltildi.
- **Vitrin (Hero Showcase) Performansı:** Ana sayfadaki üst vitrinde kasma ve GPU yükü oluşturan blur katmanı kaldırıldı; arayüz akıcı 60/120 FPS hızına kavuştu.
- **İndirilenler → Resimler Sekmesi:** Galeri ekranından indirilen tüm görseller İndirilenler sayfasında Resimler sekmesinde listelenir; dahili galeri görüntüleyici, klasör aç ve silme desteği mevcuttur.
- **Zengin Bildirimler:** Video indirmelerinde anime afişi `BigPictureStyle` ile, resim indirmelerinde indirilen görselin kendisi thumbnail olarak bildirimde görünür.

---

## 🇬🇧 English (v2.4.152)

### 🔍 Kitsu & Simkl Search Fix, Gallery Status Bar Insets & Performance

- **Kitsu Search Fixed:** Resolved HTTP 400 Bad Request error caused by page limit exceeding Kitsu's max limit of 20. Kitsu results now load seamlessly.
- **Simkl Search Fixed:** Resolved empty results by querying anime, tv, and movie endpoints concurrently instead of the non-existent general endpoint.
- **Gallery Status Bar Padding:** Added proper status bar insets and top margin to the fullscreen image gallery category chips, preventing accidental notification shade drags.
- **Hero Showcase Performance:** Removed heavy blur layer on home screen hero carousel to eliminate UI lag and restore smooth 60/120 FPS rendering.
- **Downloads → Images Tab:** All downloaded images organized under a dedicated Images tab with fullscreen viewer, open folder, and delete options.
- **Rich Download Notifications:** Video downloads show anime poster with `BigPictureStyle`, image downloads display image thumbnail and file size.
