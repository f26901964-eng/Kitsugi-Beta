# PLAN / TASK — Vikipedi "İlgili Maddeler" Alakasızlığı ve Vikipedi Entegrasyonu Denetimi

**Tarih:** 2026-10-16
**Dal:** `arena/0cec6a3b-kitsugi-beta`
**Sürüm:** 2.4.292 → **2.4.293**
**İlgili rapor:** `KOK_NEDEN_RAPORU_VIKIPEDI_ENTEGRASYONU_2026-10-14.md` (ilk entegrasyon), bu belge onun devamıdır.

## Amaç

Vikipedi (TR) kaynağında detay ekranının Wiki sekmesindeki **"İlgili maddeler"** şeridinde çıkan
alakasız kartları (Amerika Birleşik Devletleri, Kanada, Ani(m)…) tamamen kaldırmak ve kullanıcının
bildirmediği diğer Vikipedi entegrasyonu eksiklerini kapatmak.

## Kök nedenler (canlı tr.wikipedia ölçümleri)

Ölçüm maddesi: **"Titana Saldırı"** (pageid 1757970, Q586025; kaynakta "Attack on Titan" buraya
yönlendiriyor). Şeritte görülen üç kart da aynı kusurun ürünüydü:

1. **Bilgi kutusu şablonları ülke/bölge bağlantısı ÜRETİYOR.** `{{Animanga lisans|NA=…|TR=…}}`
   şablonu ürettiği satırlarda `[[Kuzey Amerika|NA]]`, `[[Amerika Birleşik Devletleri|US]]`,
   `[[Türkiye|TR]]` gibi bağlantılar oluşturuyor. Bu bağlantılar maddenin **Vikimetin'inde yok**;
   yalnızca bilgi kutusunun işlenmiş HTML'inde var. Yani ham metin/template parametresi süzgeci
   ile yakalanamaz — kutu düzeyinde dışlama gerekir.
   Canlı kanıt: `action=parse&page=Titana Saldırı&section=0&prop=links` → 39 ns0 bağlantısı
   (Kodansha, MAPPA, Wit Studio, Eren Yeager ile birlikte Amerika Birleşik Devletleri, Avustralya,
   Birleşik Krallık, Kuzey Amerika, Türkiye…).
2. **Yalnızca görselden oluşan bağlantılar (bayrak/ikon) gövde sayılıyordu.** Bayrak bağlantısının
   metni boş olduğu için etiketi hedef başlığa düşüyordu; "Kanada" kartı tam olarak bu yoldan
   geldi.
3. **Bilgi kutusu dışında da coğrafya sızıyor.** `action=parse&page=Titana Saldırı&section=1&prop=links`
   ("Konusu") → Eren Yeager ile birlikte **Nördlingen** (Almanya'da bir şehir).
4. **Önem sırası yoktu.** Eski akış giriş (0) + ilk bölüm (1) bağlantılarını sırasıyla alıp
   `take(30)` yapıyor, sıralamayı API'ye bırakıyordu. Ölçüm: `action=query&titles=…` çoklu başlıkta
   `query.pages` dizisi **istek sırasını değil pageid sırasını** döndürüyor → şeride önce "en eski"
   maddeler (ülkeler, kıtalar, "Anime") düşüyordu.
5. **Devam anahtarı yok sayılıyordu.** `prop=extracts` tek istekte en çok 20 sayfa verir, fazlası
   `continue`/`excontinue` ile bildirilir; uygulama bunu okumadığı için önizlemeler sessizce
   yarıda kesiliyordu (canlı yanıt: `"continue":{"excontinue":20,…}`).

## Uygulanan görevler

- [x] `FandomHtmlText`'e MediaWiki'ye özel gövde dışı kutu seçicisi ekle
      (`MEDIAWIKI_JUNK_SELECTOR`: `.infobox`, `table.infobox`, `.navbox`, `.vertical-navbox`,
      `.sidebar`, `.side-box`, `.metadata`, `.mw-authority-control`, `.reflist`,
      `.mw-references-wrap`, `ol.references`, `.catlinks`, `.mw-indicators`, `.printfooter`…).
      Seçici yalnızca `FandomUrls.isWikipedia()` true iken uygulanır; **Fandom davranışı değişmez**.
- [x] `FandomHtmlText.blocks(html, maxBlocks, wikiId)` ve yeni `internalWikiLinkCounts(html, wikiId)`
      bu seçiciyi uygular (`wikiLinks` da bağlantı çipleri için uygular).
- [x] Vikipedi'de yalnızca görselden oluşan bağlantıları (bayrak/logo) atla (`isImageOnlyLink`).
- [x] `internalWikiLinkCounts` ile bağlantıların **geçiş sıklığını** çıkar; `FandomSectionContent.linkCounts`
      alanına yaz (serileştirilebilir, eski JSON varsayılanla çözülür).
- [x] Bölüm önbelleği sürümünü `sec:v7` → `sec:v8` yükselt (eski kirli bağlantı listeleri kullanılmasın).
- [x] `FandomApi.pagePreviews`: `continue`/`excontinue` döngüsü, çağıranın sırasını geri kurma,
      normalize başlıkla tekilleştirme, `exlimit` (Vikipedi), 320 karakterlik özet
      (`RELATED_EXCERPT_CHARS`), yedek `barePagePreviews` yolunda da sıra koruma.
- [x] `FandomApiParsers.parseRelatedPageBatch` + `resolveTitle`: `normalized`/`redirects` zincirini
      çözer, `continue` nesnesini ham parametre olarak döndürür.
- [x] `FandomApi.previewProps`: Vikipedi'de `description` (kısa tanım) modülü de istenir;
      Fandom'da istenmez (modül yok, istek reddedilmesin).
- [x] Yeni `WikipediaRelatedSelection`: genel başlık listesi (ülke/bölge/dil/yıl/"Anime"/"Manga"/
      hedef kitle kavramları…), kısa tanım ve özet metin süzgeçleri, sıklık + isim örtüşmesi
      puanlaması (`lead×4 + body×2 + örtüşme×6`).
- [x] `FandomRepository.loadWikipediaRelatedPages`: giriş + **yaprak** bölümleri tara (ara
      başlıklı "Medya"/"Anime" bölümleri gövde metni içermez), puanla, süz, seçilen adaylar için
      önizleme iste, seçim sırasını koru.
- [x] Dört çağrı noktasını bağla: medya sekmesi ilgili maddeler, medya okuyucusu, karakter sekmesi
      ve karakter okuyucusu (karakter yolunda maddenin kendisi de elenir).
- [x] `FandomLeadParser.parseIntro(html, wikiId)`: giriş metni çözümüne de `wikiId` geçir
      (açıklama kartına bilgi kutusu kırıntısı düşmesin).
- [x] `FandomRelatedPage.shortDescription` alanı + kart metninde kısa tanım tercihi.
- [x] Regresyon testleri: `FandomApiParsersTest` (devam anahtarı, yönlendirme çözümü),
      `FandomHtmlTextTest` (Vikipedi kutu/bayrak süzgeci, Fandom davranışının korunması),
      yeni `WikipediaRelatedSelectionTest` (canlı kısa tanımlarla süzgeç ve sıralama).

## Değişen dosyalar

1. `app/src/main/java/com/kitsugi/animelist/data/fandom/FandomHtmlText.kt`
2. `app/src/main/java/com/kitsugi/animelist/data/fandom/FandomApi.kt`
3. `app/src/main/java/com/kitsugi/animelist/data/fandom/FandomApiParsers.kt`
4. `app/src/main/java/com/kitsugi/animelist/data/fandom/FandomLeadParser.kt`
5. `app/src/main/java/com/kitsugi/animelist/data/fandom/FandomModels.kt`
6. `app/src/main/java/com/kitsugi/animelist/data/fandom/FandomRepository.kt`
7. `app/src/main/java/com/kitsugi/animelist/data/fandom/WikipediaRelatedSelection.kt` **(yeni)**
8. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomMediaWikiViewModel.kt`
9. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomCharacterWikiViewModel.kt`
10. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/WikiPagePreviewCard.kt`
11. `app/src/test/java/com/kitsugi/animelist/data/fandom/FandomApiParsersTest.kt`
12. `app/src/test/java/com/kitsugi/animelist/data/fandom/FandomHtmlTextTest.kt`
13. `app/src/test/java/com/kitsugi/animelist/data/fandom/WikipediaRelatedSelectionTest.kt` **(yeni)**
14. `app/build.gradle.kts` (sürüm 2.4.293), `release_notes_v2.4.293.md` **(yeni)**

## Doğrulama

```bash
./gradlew :app:testDebugUnitTest --tests "com.kitsugi.animelist.data.fandom.*"
./gradlew :app:compileFossDebugKotlin
```

Elle kontrol (Vikipedi TR kaynaklı bir yapım, ör. Titana Saldırı):
1. Wiki sekmesi → "İlgili maddeler": ülke/bölge kartı **olmamalı**; karakter, stüdyo, yayınevi,
   sanatçı ve uyarlama maddeleri önce görünmeli.
2. Kart altı metin kısa tanım olmalı ("Japon animasyon stüdyosu" gibi), 1200 karakterlik özet değil.
3. Aynı şerit okuyucuda (madde açıldığında) ve karakter sekmesinde de aynı olmalı.
4. Fandom kaynaklı bir wiki'de şerit **eskisi gibi** davranmalı (Fandom'da bilgi kutusu bağlantıları
   gövde sayılmaya devam eder).

## Kapsam dışı bırakılanlar (bilinçli)

- `FandomApi.sections()` hâlâ `prop=sections` kullanır (Wikimedia'da "deprecated" uyarısı verir).
  `prop=tocdata` HTML döndürdüğü için yapılandırılmış İçindekiler listesi yerine geçemez; davranış
  korundu ve yalnızca önbellek/verimlilik için ayrıca ele alınmalıdır.
- `RELEASE_NOTES.md` güncellenmedi: son iki sürüm de (2.4.291, 2.4.292) yalnızca
  `release_notes_v2.4.NNN.md` dosyasıyla yayımlandı, aynı düzen sürdürüldü.
