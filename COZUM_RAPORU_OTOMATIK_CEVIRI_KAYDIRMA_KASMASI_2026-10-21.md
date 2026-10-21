# Otomatik Çeviri Açıkken Wiki Sayfası Kaydırmada Kasıyordu — Performans Raporu

**Tarih:** 2026-10-21
**Dal:** `arena/6e0c648a-kitsugi-beta`
**Şikâyet:** Fandom wiki açılır sayfalarında otomatik çeviri çalışırken sayfa kaydırılırken
çok kasıyor/takılıyordu. İstek: bu performans sorununun giderilmesi — ve otomatik
çevirinin etkilediği **her yüzey** için geçerli olması.

---

## 1. Kök nedenler

### R1 — Okuyucu her KAYDIRMA KARESİNDE yeniden bileşime giriyordu
`FandomWikiReader.kt`: kaydırma konumları `mutableStateMapOf` (snapshot state) içinde
tutuluyordu; `snapshotFlow { scrollState.value }` konumu **her karede** bu haritaya
yazıyor, `initialScrollPosition` okuması ise bileşim gövdesindeydi. Sonuç: harita yazımı
`FandomWikiReaderSheet` scope'unu her kaydırma karesinde geçersizleştiriyordu.
Otomatik çeviri açıkken bu yeniden bileşim ağır işler (tam harita taramaları, blok
yeniden çizimi) içerdiği için kaydırma doğrudan kasıyordu.

**Düzeltme:** Kaydırma konumları artık Compose state değildir — düz `HashMap`.
Yalnızca sekme değişiminde okunur; invalidasyon üretmez.

### R2 — Çeviri akışı TÜM bölümlerin yeniden hesaplanmasına yol açıyordu
Otomatik çeviri blok blok akıyor (`onBlock` → her blokta `_translations` haritası
değişiyor). Çiziciler (`ReaderSectionContent`, `ArticleSectionBody`, `SectionBody`)
**tam haritayı** `remember` anahtarı alıyordu; harita her güncellemede değiştiği için
başka bir bölümün çevirisi gelirken de **her bölümün** `withTranslatedBlocks` hesabı
(keepsLinks → wikiLinkRanges döngüleri) yeniden çalışıyordu. Birçok bölüm görünürken
bu, çeviri + kaydırma birleşince yığılan ms'ler üretiyordu.

**Düzeltme:** Yeni `wikiTranslationsFor(baseKey)` ile her çizici yalnızca **kendi
bölümünün** alt haritasını alır. Alt harita yapısal eşitlik korur → o bölümün çevirisi
değişmediyse `remember` önbellekli sonucu verir; yalnızca değişen bölüm yeniden hesaplanır.
Tarama maliyeti O(tam harita) → O(bölüm)'e iner.

### R3 — `hasWikiTranslation` taramaları tahsisat üretiyordu
`isChildOf` her girişte `baseKey + SEPARATOR` birleştiriyordu (String tahsisatı).
`translatedReaderKeys` (okuyucunun ~bölüm sayısı anahtarı × harita boyutu) her
bileşimde bu taramayı yapıyordu — GC baskısı ve bileşim gecikmesi.

**Düzeltme:** `isChildOf` iki `startsWith` ile **sıfır tahsisat**; sayfa düzeyindeki
türetilen değerler (`translatedReaderKeys`, `translatingReaderKeys`) `remember` ile
yalnızca harita değiştiğinde hesaplanıyor; buton `hasTranslation` kontrolleri alt
harita üzerinde çalışıyor.

### R4 — Bağlantı açıcısı kimliği tüm mavi metinleri yeniden kurduruyordu
`openTarget` lambası her bileşimde yeniden oluşuyordu; `LocalFandomLinkOpener` değeri
değişince okuyucudaki **her** `ReaderLinkedText` geçersizleşiyor ve
`remember(..., openTarget)` anahtarı sayesinde tüm `AnnotatedString`ler (wikiLinkRanges +
link ekleme) yeniden kurulup metinler yeniden ölçülüyordu. Ayrıca açıcı kimliği
`ReaderLinkedText` remember anahtarıydı.

**Düzeltme:** `openTarget` sayfa kimliğine (scrollKey) bağlı `remember` ile sabitlendi
(güncel `onOpenLink` → `rememberUpdatedState`); `ReaderLinkedText` yalnızca
**metin + bağlantılar** değişince AnnotatedString kuruyor, açıcıyı tıklama anında okuyor.

### R5 — Çeviri ikonu butonları BOŞTA bile sonsuz animasyon çalıştırıyordu
`KitsugiTranslateIconButton` içindeki `rememberInfiniteTransition` koşulsuz oluşturuluyordu:
uygulamadaki **her** çeviri butonu (boşta bile) sürekli animasyon karesi üretiyordu;
`isLoading` sırasında ise `pulseAlpha` okuması butonun **tüm scope'u** her karede
yeniletiyordu (dönen halka + ikon + buton). Otomatik çeviri açıkken ve kaydırırken
çok sayıda buton birlikte kare üretiyordu.

**Düzeltme:** Nabız animasyonu yalnızca `isLoading` iken oluşturulur (boşta sıfır maliyet)
ve ayrı bir yaprak composable (`PulsingLoadingTranslateIcon`) içine alındı; karede yalnızca
o küçük alt ağaç yenilenir, buton üst scope'u atlanır.

### Ek — küçük maliyetler
- `wikiTranslationSourceText()` (tüm blokların joinToString'i) her bileşimde yeniden
  üretiliyordu → `remember(content)` ile sabitlendi.
- VM'lerde blok akışı her adımda **iki** tam harita kopyası üretiyordu → tek kopyaya
  indirildi (`it + mapOf(childKey to value, key to COMPLETE)`).

---

## 2. Etkilenen yüzeyler (otomatik çevirinin değdiği her yer)

| Yüzey | Dosya |
|-------|-------|
| Açılır wiki okuyucu (şikâyetin ekran görüntüsü — örn. Image Gallery sayfaları) | `ui/screens/detail/wiki/FandomWikiReader.kt` |
| Yapım detay → Wiki sekmesi + makale kartı/İçindekiler/açılır bölümler | `ui/screens/detail/wiki/MediaWikiTabContent.kt` |
| Karakter wiki sekmesi (açıklama, bilgi kutusu, bölümler) | `ui/screens/detail/wiki/CharacterWikiTabContent.kt` |
| Paylaşılan çeviri koruma katmanı (anahtar taramaları + alt harita) | `ui/screens/detail/wiki/WikiTranslationProtection.kt` |
| Her iki wiki ViewModel'i (blok akışı güncellemeleri) | `FandomMediaWikiViewModel.kt`, `FandomCharacterWikiViewModel.kt` |
| Uygulama geneli çeviri ikon butonu (harici/uç iç çeviri göstergesi) | `ui/components/KitsugiTranslateIconButton.kt` |

Davranış değişmedi: çeviri kalitesi, mavi bağlantı sözleşmesi, Orijinal/Çeviri geçişi ve
okuma konumu koruma aynı; yalnızca **ne sıklıkla ve ne ölçütte** yeniden hesaplandığı
değişti.

## 3. Testler

`WikiTranslationProtectionTest.kt`:
- `wikiTranslationsForScopesToOneSectionWithStructuralEquality` — alt harita kapsamı +
  yapısal eşitlik (remember atlamasının dayanağı),
- `childKeyCheckMatchesSeparatorBoundary` — tahsisatsız `isChildOf` aynı sözleşmeyi korur
  (`sec:01`, `sec:0-extra` gibi yanlış eşleşmeler dahil).

Mevcut sözleşme testleri (`withoutTranslation…`, `toggleAvailability…`) aynı davranışla
geçmeye devam eder.
