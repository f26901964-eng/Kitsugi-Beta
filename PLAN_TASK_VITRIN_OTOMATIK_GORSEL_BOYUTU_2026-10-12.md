# PLAN/TASK — Ana Sayfa Vitrini: Görsel Oranına Kilitlenen Otomatik Kutu Boyutu

**Tarih:** 2026-10-12
**Dal:** `arena/63f06cf1-kitsugi-beta`
**Etkilenen dosyalar:**
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiHeroSection.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/HeroArtworkAspect.kt`
- `app/src/test/java/com/kitsugi/animelist/ui/components/HeroImageSelectionTest.kt`

## Sorun (ekran görüntüsünden)

Keşfet vitrininde (hero carousel) bant yüksekliği `heroBandHeightDp(...)` ile
**sabit** bir orandan geliyordu (dikey telefonda ekranın ~%55'i). Gösterilen
görsel yatay bir arka plan (16:9 key visual) olduğunda kutu oranı (~0.8) ile
görsel oranı (1.78) arasındaki fark ~2.2'ye çıkıyor ve `ContentScale.Crop`
görselin ancak ortasındaki ~%45'lik dilimi gösterebiliyordu: One Piece
örneğinde koca bir gökyüzü + ortada küçücük karakter + altta dev soluk logo
bandı görünüyordu.

Detay sayfasında (`KitsugiDetailHero`) aynı sorun çözülmüştü: görsel alanının
en-boy oranı, yüklenen görselin **gerçek** oranına kilitlenir; kutu gösterdiği
görselle birebir aynı şekle gelir ve Crop kırpması ≈ sıfırdır. Kullanıcının
istediği: **aynı mantığın vitrin için de** uygulanması.

## İstenenler (kullanıcı bildirimi)

1. Vitrin; dikey mod, yatay mod ve ekran boyutuna göre, gösterdiği görselin
   tipine (dikey poster / yatay arka plan) **otomatik** boyut alsın.
2. Başlık, rozetler, meta satırı ve gösterge çubuğu (sayısal bar) görselin
   **üstünde** kalmaya devam etsin — bindirme düzeni bozulmasın.
3. Yatay modda bant zaten geniş olduğundan ve poster fallback'inde vitrin
   aşırı uzayacağından: yatay arka plan bulunamazsa (manga/manhwa vb.) dikey
   poster, mevcut "normal arka plan boyutundaki" banda **contain** mantığıyla
   oturmaya devam etsin (çift katman: atmosferik dolgu + ortada kesilmemiş
   poster). Bu mantık KORUNACAK.

## Yeni Kural

| Durum | Gösterilen görsel | Vitrin kutusunun şekli |
|-------|-------------------|------------------------|
| Dikey mod, uzun-dar telefon (w/h ≤ 0.55) | Dikey poster birincil | Görselin gerçek oranı (2:3 → uzun-dar kutu), tavan %62 ekran |
| Dikey mod, kare/tablet (w/h > 0.55) | Yatay arka plan birincil | Görselin gerçek oranı (16:9 → geniş-kısa bant), taban %34 ekran |
| Dikey mod, birincil görsel yok/başarısız | Yedek görsel | Kutu yedek görselin oranına yeniden oturur |
| Yatay mod | Yatay arka plan birincil | Mevcut ekran-duyarlı bant (tavan); poster fallback → contain |
| Manga/manhwa (her yön) | Dikey kapak birincil | Dikeyde uzun-dar kutu; yatayda contain fallback |

- Yükseklik formülü: `heroWidthDp / clamp(görçekOran, 0.55, 2.35)` ve dikey modda
  `[0.34·ekranH, 0.62·ekranH]`, yatay modda `[0.78·bant, bant]` aralığına sıkıştırılır
  (`homeHeroHeightDp`, `HeroArtworkAspect.kt`).
  - %34 tabanı: 16:9 arka plan dar telefonda bandı aşırı kısaltmasın; başlık +
    meta + gösterge çubuğu daima görselin üstünde okunur kalsın.
  - %62 tavanı: detay sayfasıyla aynı tavan; poster kutusu ekranı yutmasın.
  - Yatay modda tavan = mevcut `heroBandHeightDp` bandı → yatay sunum ve poster
    contain fallback'i BİREBİR korunur.
- Görsel tercihi (`homeHeroPrefersBackdrop`) **ekran oranından** türetilir
  (detay sayfasındaki `resolveHeroArtworkPreference` ile aynı dikey kuralı;
  yatay modda vitrine özgü olarak arka plan birincil). Kutu ölçüsünden
  türetilemezdi: kutu zaten gösterilen görselin oranına göre boyutlanıyor
  (döngüsel bağımlılık + `remember` anahtarı değişimi → titreşim/döngü).
- `heroImageCandidates(posterUrl, backdropUrl, preferBackdrop)` aşırı yükü
  eklendi; eski 6 parametreli imza aynı davranışla ona delege eder
  (mevcut birim testleri dokunulmadan geçer).
- Yüklenen oranlar sayfa başına `pageImageAspects` durumunda tutulur; bant
  yüksekliği **öndeki sayfanın** oranını takip eder (sayfa geçişinde otomatik
  eşine oturur). Oran yalnızca `onImageAspectResolved` callback'inden gelir →
  ölçüm döngüsü yok.

## Bozulan hiçbir şey (kontrol listesi)

- Bindirmeler: gradyan scrim, "ÖNE ÇIKAN" satırı, kaynak çipi, logo, başlık,
  meta ve gösterge çubuğu aynı `BottomCenter` Column'da, görselin üstünde.
- Yatay mod: sol karartma gradyanı, bant yüksekliği, poster contain çift
  katmanı (atmosferik dolgu + ortada Fit poster) değişmedi.
- TV: odak çerçevesi, tuş gezinmesi ve sabit bant korunur (yatay mod dalı).
- Pager hit-test düzeltmesi (`frontHeroPageIndex`) aynı; yalnızca yükseğe
  taşındı çünkü bant yüksekliği de öndeki sayfayı bilmek zorunda.

## Doğrulama

- Sandbox'ta Java/Gradle bulunmadığı için derleme çalıştırılamadı; bunun yerine:
  - Üç dosyada ayraç/parantez dengesi ve tanımsız referans taraması yapıldı
    (`pageImageAspects`, `frontPageIndex`, `homeHero*` yardımcıları aynı pakette).
  - `heroImageCandidates` eski imzasının davranışı değişmedi (delege ediyor);
    `HeroImageSelectionTest` mevcut beklentileri korunur.
- Yeni birim testleri:
  - `home hero backdrop preference follows screen ratio like detail page`
  - `portrait hero band locks to the shown artwork aspect`
  - `landscape hero band keeps screen responsive cap and only shrinks for ultra wide banners`
  - `candidate overload with explicit preference keeps poster and backdrop order`
- Elle test senaryosu: Keşfet vitrini dikey telefonda One Piece → 16:9 bant
  (ekranın %34'ü) tam key visual; manga rafı öğesi → uzun-dar poster kutusu;
  yatay moda dönünce bant mevcut yüksekliğinde, poster yoksa contain fallback.
