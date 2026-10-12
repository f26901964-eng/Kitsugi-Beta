# Kitsugi-Beta v2.4.259 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🖼️ 1. Detay Vitrini: 220dp Sabit Boşluk Kaldırıldı
- **Eski Durum:** Vitrin görsel alanının altına sabit `DETAIL_HERO_INFO_AREA_HEIGHT = 220.dp` yüksekliğinde boş bir blok ekleniyordu. Bu alan hem dikey hem yatay yönelimde gereksiz bir boşluk bırakarak içeriğin aşağı kaymasına neden oluyordu.
- **Düzeltme:** 220dp sabit boşluk tamamen kaldırıldı. Başlık, meta bilgiler ve rozetler artık doğrudan görselin alt bölümüne bindirilerek (overlay) kompakt ve modern bir görünüm sağlandı.

### 🔍 2. Vitrin Tıklaması: Ekranda Gösterilen Gerçek Görsel Doğrudan Galeride Açılıyor
- **Eski Durum:** Vitrindeki görsele dokunulduğunda daima varsayılan kaynak posteri (`imageUrl`) açılmaya çalışılıyordu. Ekranda yüksek kaliteli TMDB backdrop veya dikey alternatif görsel gösterilse bile, galeride tıklanan görsel değil bambaşka bir poster çıkıyordu. Ayrıca galeri yüklenirken yapılan tıklamalar engelleniyordu.
- **Düzeltme:**
  - `onPosterClick(displayedImageUrl, displayedCategory)` artık vitrinde o anda gerçekten render edilen görsel URL'sini ve kategorisini (`POSTER` veya `BACKDROP`) galeriye aktarır.
  - `selectHeroArtworkInGallery()` fonksiyonu eklendi:
    - **Akıllı URL Eşleme (`heroArtworkUrlsMatch`):** TMDB (`original`, `w500`, `w1280`), Kitsu (`tiny`, `small`, `medium`, `large`, `original`), Bangumi (`lain.bgm.tv`), AniList, MAL ve Shikimori gibi CDN çözünürlük/boyut varyantları normalize edilerek galerideki doğru görselle birebir eşleştirilir.
    - **Anında Açılış & Sıfır Bekleme:** Galeri ağdan yüklenmeye devam ediyor olsa bile (`galleryLoading = true`), tıklanan görsel galerinin başına eklenerek gecikmesiz açılır.
- **Birim Testleri:** `DetailHeroGallerySelectionTest` altında 4 birim test eklenerek eşleştirme, boyut varyantları ve yükleme önceliği doğrulandı.

---

## 🇬🇧 English

### 🖼️ 1. Detail Hero: 220dp Blank Gap Removed
- **Previous Behavior:** A hardcoded `DETAIL_HERO_INFO_AREA_HEIGHT = 220.dp` was appended underneath the banner height, creating an unnecessary empty dark block and pushing downstream sections down.
- **Fix:** Removed the 220dp fixed spacing entirely. Title, metadata strings, and badges are now cleanly overlaid onto the bottom section of the hero artwork for a compact and immersive look across both portrait and landscape modes.

### 🔍 2. Hero Click: Opens the Exact Displayed Image in Gallery
- **Previous Behavior:** Tapping the hero artwork opened the raw upstream source poster rather than the artwork actually rendered on screen (e.g. high-resolution TMDB backdrops or vertical alternates). Furthermore, clicks were discarded if the gallery was still loading.
- **Fix:**
  - `onPosterClick(displayedImageUrl, displayedCategory)` now propagates the exact URL and category (`POSTER` vs `BACKDROP`) currently displayed on screen.
  - Implemented `selectHeroArtworkInGallery()`:
    - **Smart Canonical Resolution Matching (`heroArtworkUrlsMatch`):** Canonicalizes and matches CDN quality variants across TMDB (`w500` vs `original`), Kitsu (`medium` vs `large`), Bangumi, AniList, MAL, and Shikimori.
    - **Instant Opening & Pre-loading:** Even if the full gallery payload is still being fetched (`galleryLoading = true`), the clicked artwork is placed at the front of the list and opened immediately without delay.
- **Unit Tests:** 4 unit tests added in `DetailHeroGallerySelectionTest` covering existing backdrop index matching, fallback insertion, TMDB variant resolution, and CDN safety checks.
