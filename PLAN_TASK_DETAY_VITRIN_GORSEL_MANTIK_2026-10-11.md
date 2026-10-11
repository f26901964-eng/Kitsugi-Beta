# PLAN_TASK: Detay Sayfası Vitrin Görseli — Vitrinle Aynı Poster/Arka Plan Mantığı

- **Tarih:** 2026-10-11
- **Dal:** `arena/0767f655-kitsugi-beta`

## Sorun

API kaynaklı (TMDB, AniList, MAL, Kitsu, Simkl, Bangumi vb.) detay sayfasının üst görseli
(`KitsugiDetailHero`) her zaman `imageUrl` (dikey poster) değerini sabit 470dp yüksekliğe
`ContentScale.Crop` ile gösteriyordu. Yatay arka plan (`backdropUrl`) ve `KitsugiHeroArtworkResolver`
(TMDB + Fanart.tv) sonuçları detay sayfasında kullanılmıyordu. Sonuç: tablet/yatay ekranda
dikey poster geniş alanda kırpılıp karakterler kesiliyordu.

## Çözüm

- `KitsugiDetailHero` yeni parametreler aldı: `backdropUrl`, `mediaType`, `artworkItem`.
- Görsel seçimi vitrinle aynı: `heroImageCandidates` ekran genişliği (`screenWidthDp`) ve yön
  (`ORIENTATION_LANDSCAPE`) ile çalışır.
  - Dikey telefon: en kaliteli dikey poster birincil, arka plan yedek.
  - Yatay telefon, tablet (dikey/yatay), TV: en kaliteli yatay arka plan birincil, poster yedek.
  - Manga her zaman poster.
- `KitsugiHeroArtworkResolver.resolveArtwork` (önbellekli) ile TMDB/Fanart en kaliteli
  poster ve arka plan adayları eklenir; yükleme hatasında sıradaki adaya geçilir.
- Hizalama `heroImageAlignment` ile yapılır; geniş alanda dikey görsel kesilmeden
  (`ContentScale.Fit`) yumuşak arka plan dolgusuyla gösterilir.
- `ApiDetailLeftPanel` yeni parametreleri geçirir.

## Değişen Dosyalar

1. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailHero.kt`
2. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiDetailLeftPanel.kt`

## Doğrulama

- Sandbox'ta JDK/Android SDK yok; derleme GitHub CI (`android-build.yml`) ile doğrulanmalı.
- Manuel: telefon dikey/yatay, tablet dikey/yatay — poster/backdrop seçimi ve karakter kırpılmaması.
- Koleksiyon kaydı detayı (`EntryDetailLeftPanel` → `KitsugiDetailHeroSection.kt`) bu değişiklikte
  kapsam dışı; `MediaEntry` arka plan verisi taşımıyor.

## Ek: Galeri Adayları

- `KitsugiDetailHero` artık `galleryItems` alır (`ApiDetailLeftPanel` geçirir).
- Galeri öğeleri kategoriye (`POSTER`, `BACKDROP`, `BANNER`) ve gerçek `width/height` oranına göre
  dikey/yatay olarak ayrılır; logo, clearart, karakter, kişi, küçük resim ve kare kategoriler hariç tutulur.
- Her yön içinde en yüksek çözünürlük önce gelir.
- Sıralama: önce kaynak/TMDB/Fanart vitrin adayları (resolver), sonra galeri adayları; yükleme hatasında sıradaki adaya geçilir.
