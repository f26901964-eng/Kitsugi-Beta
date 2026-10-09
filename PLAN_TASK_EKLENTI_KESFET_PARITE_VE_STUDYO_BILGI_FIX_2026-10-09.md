# PLAN / TASK — Eklenti Keşfet Paritesi + Stüdyo Bilgi Kartı Düzeltmeleri (2026-10-09)

## 🎯 Görev Özeti
Kullanıcı talebi (ekran görüntüleriyle):
1. **Eklenti Portalı** içinden açılan eklenti keşfet sayfaları, uygulamanın ana sayfa
   keşfet sayfasıyla **birebir aynı** olacak: vitrin (hero carousel), kutucuklar
   (kartlar), kutucuk kaydırma mekanikleri, kutucuk çerçevesi, raflar, "Tümünü Gör"
   açılır sayfaları dahil — eksiksiz.
2. **Stüdyo detay sayfası** bilgi sunumu düzeltilecek:
   - "Kuruluş: 1998-10-01T00:00:00+00:00" gibi ham ISO tarih yerine okunabilir tarih.
   - "Hakkında" bölümü, medya detay sayfasındaki "Açıklama" kartıyla aynı davranacak:
     otomatik çeviri açıksa çevrilecek, 3. parti çeviri butonu, kopyala butonu ve
     "Daha fazla / Daha az" genişletme dahil.

## 📸 Referans Ekran Görüntüleri
- `image-1.png` → Eklenti keşfet sayfası (eski basit vitrin/raflar)
- `image-3.png` / `image-4.png` → Ana sayfa keşfet vitrini + "Tümünü Gör" grid sayfası (hedef tasarım)
- `image-5.png` → Ham ISO "Kuruluş" chip'i (sorun)
- `image-6.png` → Çeviri/kopyala butonsuz "Hakkında" kartı (sorun)
- `image-7.png` → Medya detay "Açıklama" kartı (hedef davranış: çeviri + kopyala + daha fazla)

## 🔧 Uygulama Planı ve Yapılan Değişiklikler

### 1) CS3 → ortak model köprüsü (YENİ DOSYA)
`ui/screens/search/components/AddonHomeParity.kt`
- `SearchResponse.toAddonSearchResult(): JikanSearchResult` — eklenti içeriğini ana
  sayfanın kullandığı ortak modele çevirir (`source`/`cs3ApiName` = eklenti adı,
  `cs3Url` = tıklama hedefi, kararlı pozitif `malId`, yansımalı güvenli `year` okuması).
- `TvType.toAddonMediaType()` / `TvType.toAddonTypeLabel()` — tür eşleme + Türkçe etiket.
- `ADDON_TYPE_FILTERS` — "Tümünü Gör" sayfası için emojili tür çipleri
  (✨ Tümü / 🎌 Anime / 🎥 Film /  Dizi).

### 2) Eklenti keşfet sayfası = ana sayfa dili
`ui/screens/search/components/AddonExploreDialog.kt` (`AddonExplorePage`)
- Eski özel `AddonHeroBannerCarousel` / `AddonCategoryRow` kaldırıldı.
- Vitrin: `KitsugiHeroSection` (ÖNE ÇIKAN + kaynak çipi + meta + indicator + otomatik
  kaydırma + paralaks) — `onInfoClick` eklenti detay diyaloğunu açar.
- Raflar: `KitsugiHorizontalMediaSection` — aynı `KitsugiExploreMediaCard`, aynı snap
  fling kaydırma, aynı "Tümünü Gör" sözleşmesi.
- Arama sonuçları da `KitsugiExploreMediaCard` (forceVertical) ile çiziliyor.
- Yeni parametreler: `titleLanguage`, `scoreFormat`, `hideScores`, `blurAdultMedia`
  (AppRoot'tan `appSettings` değerleri beslenir).

### 3) "Tümünü Gör" açılır sayfası = keşfet grid dili
`ui/screens/fullscreen/AddonFullScreenGridPage.kt`
- `ResultItemCard` yerine `KitsugiExploreMediaCard` (grid) + `KitsugiRankingMediaCard` (liste).
- Grid ↔ Liste toggle'ı (kalıcı tercih), Filtre+Sıralama bottom sheet'i
  (`KitsugiStudioFilterBottomSheet` artık `typeFilters` parametresi alıyor),
  emojili tür çip satırı, "N içerik • filtre" sayacı, Temizle,
  kaydırınca beliren üst şerit ve yukarı FAB — stüdyo/keşfet sayfalarıyla aynı.
- Sayfalama (getMainPage + hasNext) korunuyor.

### 4) Kaynak rozeti/çipi — bilinmeyen kaynak (eklenti) desteği
`ui/components/KitsugiSourceBadge.kt`
- `KitsugiSourceBadge`: bilinmeyen kaynak için köşe rozetinde genel eklenti simgesi.
- `KitsugiSourceNamePill`: bilinmeyen kaynak için eklenti simgesi + eklenti adı
  (vitrin çipi artık hiçbir kaynakta boş kalmaz).

### 5) Kart meta metni
`ui/components/KitsugiExploreMediaCard.kt`
- CS3 sonuçlarında (`cs3Url` dolu) sahte `#id` basılmaz; yalnızca eklenti adı gösterilir.

### 6) Stüdyo: Kuruluş tarihi biçimi
`ui/screens/detail/StudioDetailComponents.kt`
- `formatStudioEstablished()` — `KitsugiDateUtils` ile "1 Ekim 1998" biçimi;
  hero ve sol paneldeki "Kuruluş" chip'leri ham ISO göstermez.

### 7) Stüdyo: "Hakkında" = detay "Açıklama" kartı davranışı
- `KitsugiDetailInfoSection.kt`: `DetailSynopsisCard`'a `title` parametresi ("Hakkında").
- `StudioDetailComponents.kt`: `StudioAboutSection` artık `DetailSynopsisCard` üzerine
  kurulu — çeviri butonu, kopyala butonu, Daha fazla/Daha az, SelectionContainer,
  markdown + görsel galeri isteği korunur.
- `StudioDetailViewModel.kt`: `translatedAbout` StateFlow + `translateAbout()` +
  yüklemede otomatik çeviri (`autoTranslateEnabled` veya Rusça metin; `DetailCache`
  "studio_about" çeviri önbelleği) — `ApiResultDetailViewModel` ile aynı sözleşme.
- `StudioDetailPage.kt`: eylem kablolaması — metin hâlâ hamsa uygulama içi çeviri,
  çevrilmişse 3. parti çevirmen (`openTranslator`, `preferredTranslator`);
  kopyala → pano + toast. `AppRootDetailPages` `preferredTranslator` besler.

## ✅ Doğrulama
- Sandbox'ta Java/Android SDK ve Google Maven erişimi olmadığı için Gradle derlemesi
  çalıştırılamadı (ortam kısıtı).
- Değiştirilen 11 dosyanın tamamı tree-sitter Kotlin grameriyle ayrıştırıldı → sözdizimi OK.
- Tüm çapraz dosya referansları (parametre adları/varsayılanlar, internal görünürlük,
  import'lar) elle denetlendi; eski özel bileşenlere başka referans kalmadığı grep ile doğrulandı.

## 📦 Etkilenen Dosyalar
```
app/src/main/java/com/kitsugi/animelist/AppRoot.kt
app/src/main/java/com/kitsugi/animelist/AppRootDetailPages.kt
app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiExploreMediaCard.kt
app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiSourceBadge.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailInfoSection.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/detail/StudioDetailComponents.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/detail/StudioDetailPage.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/detail/StudioDetailViewModel.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/AddonFullScreenGridPage.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/search/components/AddonExploreDialog.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/search/components/AddonHomeParity.kt (YENİ)
PLAN_TASK_EKLENTI_KESFET_PARITE_VE_STUDYO_BILGI_FIX_2026-10-09.md (BU DOSYA)
```
