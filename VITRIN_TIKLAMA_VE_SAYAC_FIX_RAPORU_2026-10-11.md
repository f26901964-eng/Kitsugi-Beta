# Vitrin Tıklama + Sayaç Göstergesi Fix Raporu — 2026-10-11

## Şikayet
1. Vitrinde (Keşfet hero karuseli) tıklanan içerik yerine **bir sonraki vitrin
   sayfasındaki** içerik açılıyordu.
2. Sayfa göstergesi (nokta/çizgi sırası) silikti; o an kaçıncı sıradaki içeriğin
   gösterildiği anlaşılmıyordu.

## Kök Neden (Tıklama)
`KitsugiHeroSection` görünür sayfaları `[currentPage, currentPage-1,
currentPage+1]` sırasıyla üst üste bindirir. Compose'ta **son çizilen kardeş
en üstte** durur ve dokunmayı ilk o yakalar. `graphicsLayer { alpha }` yalnızca
çizimi etkiler, **hit-test'i etkilemez**. Dolayısıyla görsel olarak tamamen
şeffaf olan `currentPage + 1` katmanı tüm dokunmaları yutuyor ve kendi öğesini
(açık olanın sıradakini) açıyordu.

## Çözüm (Tıklama)
`KitsugiHeroSection.kt`:
- `openVisibleHeroItem()` eklendi: tıklama anında
  `round(currentPage + currentPageOffsetFraction)` ile ekranda **görsel olarak
  baskın** öğeyi çözer ve onu açar. Hangi katman dokunmayı yakalarsa yakalasın
  açık olan içerik açılır; geçiş animasyonunun ortasında bile doğru öğe seçilir.
- Arka plan katmanı, başlık/metin sütunu ve TV Enter tuşu artık bu ortak
  lambda'yı kullanıyor.

## Çözüm (Sayaç Göstergesi)
- Noktalar kalınlaştırıldı (6dp → 7dp); etkin sayfa **tam opak** (alpha 1.0),
  pasifler 0.30 taban ile net; etkin genişlik oranı büyütüldü (scaleX 0.40→1.0).
- Noktaların yanına sayısal sayaç rozeti eklendi: `"N / M"`
  (örn. `3 / 11`) — o an vitrinde kaçıncı sıradaki içeriğin gösterildiğini
  açıkça belirtir. Değer `derivedStateOf` ile yalnızca yarısayfa eşiği
  geçilince güncellenir; 60 Hz recompose yükü yaratmaz (mevcut performans
  kuralına uygun).

## Etkilenen Dosya
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiHeroSection.kt`

## Not
Aynı bileşeni kullanan Eklenti Ana Sayfası (AddonHomeParity) vitrini de
bu fix'ten yararlanır.
