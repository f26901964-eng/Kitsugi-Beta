# PLAN_TASK — Otomatik Çeviri Açıkken Wiki Sayfası Kaydırmada Kasılma Fix

**Tarih:** 2026-10-21 · **Dal:** `arena/6e0c648a-kitsugi-beta` · **Commit:** `91b4203`
**Bağlı rapor:** `COZUM_RAPORU_OTOMATIK_CEVIRI_KAYDIRMA_KASMASI_2026-10-21.md`

## Şikâyet
Fandom wiki açılır sayfalarında **otomatik çeviri çalışırken** sayfa kaydırılırken çok
kasıyordu/takılıyordu. İstek: performans sorununun giderilmesi ve **otomatik çevrinin
etkilediği her yer** için geçerli olması.

## Kapsam
- Açılır wiki okuyucu (reader) — örn. `Yhwach/Image Gallery` sayfaları
- Yapım detay → Wiki sekmesi (özet, bilgi kutusu, İçindekiler, makale bölümleri)
- Karakter wiki sekmesi (açıklama, bilgi kutusu, bölümler)
- Her iki wiki ViewModel'inin çeviri akışı + paylaşılan çeviri koruma katmanı
- Uygulama geneli çeviri ikon butonu (yükleme nabız animasyonu)

## Yapılan düzeltmeler (görev listesi)
- [x] **R1** Okuyucu kaydırma konumu snapshot state → düz `HashMap` (kare başına
      bileşim invalidasyonu kalktı)
- [x] **R2** Bölüm çizicilerine tam çeviri haritası yerine bölüm bazlı alt harita
      (`wikiTranslationsFor`) — yalnızca değişen bölüm yeniden hesaplanır
- [x] **R3** `isChildOf`/`hasWikiTranslation` tahsisatsız hale getirildi; sayfa düzeyi
      türetilen değerler (`translatedReaderKeys` vb.) `remember` ile memoize edildi
- [x] **R4** `openTarget` kimliği `remember` ile sabitlendi; `ReaderLinkedText`
      AnnotatedString'i yalnızca metin/bağlantı değişince kurulur
- [x] **R5** `KitsugiTranslateIconButton` nabız animasyonu yalnızca `isLoading` iken +
      ayrı yaprak scope'ta
- [x] Ek: `wikiTranslationSourceText()` memoize; VM blok akışında tek harita kopyası
- [x] Test: kapsam + yapısal eşitlik + `isChildOf` sınır testleri eklendi

## Değişen dosyalar
1. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomWikiReader.kt`
2. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/WikiTranslationProtection.kt`
3. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/MediaWikiTabContent.kt`
4. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/CharacterWikiTabContent.kt`
5. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomMediaWikiViewModel.kt`
6. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomCharacterWikiViewModel.kt`
7. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiTranslateIconButton.kt`
8. `app/src/test/java/com/kitsugi/animelist/ui/screens/detail/wiki/WikiTranslationProtectionTest.kt`

## Kabul kriterleri
- Otomatik çeviri akarken okuyucuda kaydırma akıcı; kare düşüşü yok
- Çeviri/Orijinal geçişleri, mavi bağlantı sözleşmesi, okuma konumu koruma değişmedi
- Boşta olan çeviri butonları animasyon karesi üretmiyor
- Mevcut `WikiTranslationProtectionTest` sözleşmeleri aynen geçer

## Durum
Tamamlandı (derleme/test ortamı bu oturumda yok — CI/kendi derlemesinde doğrulanacak).
