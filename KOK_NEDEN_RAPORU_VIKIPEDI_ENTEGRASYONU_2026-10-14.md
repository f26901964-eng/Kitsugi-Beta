# KÖK NEDEN RAPORU — Vikipedi (Wikipedia) Entegrasyonu Baştan Sona İnceleme ve Düzeltme

**Tarih:** 2026-10-14
**Sürüm:** 2.4.289 (önceki: 2.4.288)
**Kapsam:** Yapım detay sayfası → **Wiki** sekmesi → Vikipedi (`wikipedia.tr` / `wikipedia.en` …) yolu:
çözümleme (`FandomWikiResolver`, `WikipediaResolver`, `WikidataClient`), veri katmanı
(`FandomRepository`, `FandomApi`, `FandomApiParsers`), sekme mantığı (`FandomMediaWikiViewModel`),
arayüz (`MediaWikiTabContent`, `CharacterWikiTabContent`, `FandomWikiReader`) ve metin kaynakları.

---

## 1) Kullanıcının bildirdiği belirtiler

1. "İlgili wiki sayfaları" şeridi yapımla ilgisiz maddeler gösteriyordu (Attack on Titan için
   **Tarım, Hukuk, Ahlat, Türkçe** — 20 madde).
2. Vikipedi sekmesindeki kartlara dokunulduğunda **alakasız sayfalar** açılıyordu; okuyucu
   ansiklopedinin **"Anasayfa"** içeriğini gösteriyordu.
3. Üstteki **"Vikipedi'de aç"** düğmesi maddeyi değil **Wikipedia ana sayfasını** açıyordu.
4. "Wiki özeti" kartında **Orijinal ↔ Çeviri** geçişi içeriği **tamamen değiştiriyordu**.

---

## 2) Kök nedenler ve düzeltmeler

### 2.1 İlgili sayfalar ana sayfadan toplanıyordu (belirti 1)

**Kök neden.** `FandomMediaWikiViewModel.ensureContentLoaded()` içindeki ilgili sayfa akışı,
kaynak wiki'nin **ana sayfasının** 0 ve 1 numaralı bölümlerindeki bağlantıları topluyordu
(`FandomWikiResolver.siteInfoCached(wikiId)?.mainPage`). Fandom wiki'lerinde ana sayfa bir "hub"
olduğu için doğru çalışan bu varsayım, Vikipedi'de ana sayfanın `Anasayfa` olması nedeniyle
günün seçkin maddelerini (Tarım, Hukuk, Ahlat, Türkçe…) listeye taşıyordu.

**Düzeltme.** Vikipedi kaynağında ana sayfa tabanlı akış hiç çalıştırılmaz
(`if (!wikipediaSource && _relatedPages.value.isEmpty())`). Yerine yeni
`loadRelatedPagesForWikipedia()` devreye girer: yapımın **kendi maddesinin** giriş + ilk bölüm
bağlantıları toplanır, normalize başlıkla tekilleştirilir, ansiklopedi iskelet sayfaları elenir
(`isEncyclopediaBoilerplatePage`: `Anasayfa`, `Main Page`, `Portal`, `Yardım`, `Vikipedi:`,
`Şablon:`, `Template:`, `Dosya:`, `File:`, `Kategori:`, `Category:`, `Özel:`, `Special:`,
`Taslak`…), maddenin kendisi çıkarılır ve en fazla 20 önizleme gösterilir.

Kart da kaynağa göre çizilir: başlık **"İlgili maddeler"** (`fandom_related_pages_title_wikipedia`),
kart altı etiket **"Vikipedi sayfası"** (`fandom_open_page_wikipedia_page`).

### 2.2 "Vikipedi'de aç" ve okuyucu ana sayfayı açıyordu (belirti 2 ve 3)

**Kök neden.** `FandomMediaWikiViewModel.openReaderMainPage()` — üst başlıktaki düğmenin ve
"Wiki ana sayfasını aç" çağrısının hedefi — `siteinfo.mainpage` alanını (TR için `Anasayfa`)
açıyordu. Vikipedi'de bu sayfa yapımla ilgisizdir.

**Düzeltme.** Vikipedi kaynağında düğme **çözülen maddeyi** açar: önce madde kartındaki
`page.pageTitle`, yoksa çözümleme sonucundaki `ref.pageTitle`. Ana sayfa yoluna yalnızca hiçbir
madde eşleşmediğinde düşülür. Üst kaynak kartı ayrıca madde başlığını gösterir ve dil sürümünün
gerçek adını kullanır (`fandom_media_header_subtitle_wikipedia_tr` → "Vikipedi · Açık
Ansiklopedi"), böylece kullanıcı hangi maddeyi açacağını görür.

### 2.3 Orijinal ↔ Çeviri içeriği değiştiriyordu (belirti 4) — üç ayrı kök neden

**(a) Çeviriler kaynak metne bağlı değildi.** Uygulama içi çeviriler yalnızca anahtarla (`desc`,
`sec:1`, `reader-section:…`) tutuluyordu. Kaynak (Fandom ↔ Vikipedi) veya madde (Vikipedi TR ↔ EN)
değiştiğinde aynı anahtar **başka bir metni** işaret ettiği hâlde eski çeviri ekranda kalıyordu.
→ Her çeviri, üretildiği kaynak metne bağlanır (`translationBases`). Metin değişmişse çeviri işi
iptal edilir ve çeviri/orijinal durumu düşürülür (`pruneStaleTranslations()`; madde, seri özeti,
bölüm, madde içi sayfa, okuyucu sayfası ve okuyucu bölümü yüklemesinde çağrılır).

**(b) Kaynak değişiminde türev durum sıfırlanmıyordu.** `switchSource()` yalnızca birkaç işi iptal
ediyor, çevirileri ve bölümleri taşıyordu. → Artık çeviriler, orijinal anahtarları, madde
bölümleri (İçindekiler), arama durumu, madde içi sayfa (öğe) ve okuyucu ilgili listesi tamamen
temizlenir; `articleGeneration` artırılarak geç dönen eski yanıtların ekrana yazması engellenir.

**(c) Vikipedi'de dil sürümü sessizce değiştiriliyordu.** `FandomRepository.buildMediaArticle`
içindeki "Türkçe wiki önceliği" dalı, `wikipedia.en` maddesini kullanıcıya haber vermeden
`wikipedia.tr` sürümüne çeviriyordu (`lllang=tr` dil bağlantısı üzerinden). Böylece "Orijinal"
Türkçe metni, "Çeviri" ise **eski İngilizce metnin** çevirisini gösterebiliyordu. → Vikipedi
kaynaklarında bu sessiz geçiş kaldırıldı (`!FandomUrls.isWikipedia(wikiId)` koşulu); dil
sürümleri **ayrı kaynak çipleri** olarak sunulur. Ek güvence: kaynağın içerik dili çeviri hedefiyle
aynıysa (VIkipedi TR + Türkçe hedef) uygulama içi çeviri hiç başlatılmaz
(`isTargetLanguageSource()`), yani "iki farklı içerik" yanılsaması oluşamaz.

### 2.4 Kaynak listesi: Vikipedi TR ↔ Wikipedia EN

`FandomWikiResolver.resolveWikipediaSource()` tek bir kaynak döndürüyordu (tercih TR ise TR, değilse
EN) ve `resolveAll()` Vikipedi birincil olduğunda tek elemanlı liste dönüyordu — İngilizce maddeyi
görmek isteyen kullanıcı için hiçbir yol yoktu. Kaynak çipleri satırı ayrıca yatay kaydırılabilir
yapıldı (üç kaynak dar ekranda taşmasın); birincil kaynak Vikipedi ise isim araması yinelenmez. → `resolveWikipediaSources()` sipariş korunmuş liste
döndürür: (1) kullanıcı tercihi, (2) kardeş dil sürümü, (3) TR/EN sitelink'i yoksa ilk geçerli dil
(deterministik), (4) hiçbiri yoksa `WikipediaResolver` isim araması. Sekmedeki çipler
`wiki_source_wikipedia_tr` ("Vikipedi (TR)"), `wiki_source_wikipedia` ("Wikipedia") ve diğer diller
için `wiki_source_wikipedia_other` (`Ad (DİL)`) etiketleriyle çizilir.

### 2.5 Yanlış madde riskine karşı kimlik denetimi

İsimle arama yolunda (`WikipediaResolver`, Wikidata sitelink'i olmayan yapımlar için son çare)
maddenin kimliği doğrulanmıyordu. → Yeni `FandomApi.pageWikidataItems()` (`prop=pageprops`,
`pprop=wikibase_item`) ile maddenin Wikidata kimliği okunur; madde, yapımın/üst kayıtlarının
bilinen kimliklerinden birine ait değilse eşleşme **kesin** sayılmaz ve kullanıcıya onay kartı
gösterilir (`FandomConfidence.PROBABLE`). Kanıt yoksa (ağ hatası, kimliksiz madde) mevcut puan
tabanlı davranış korunur; yanlış "bulunamadı" üretilmez.

### 2.6 Vikipedi'de anlamsız kartlar

- **Evren rehberi:** `GuideState.Unsupported` eklendi; Vikipedi kaynağında kategori çipleri hiç
  yüklenmez/gösterilmez (`Category:Karakterler`, `Category:Kuruluşlar` gibi genel kategoriler
  yapım dışı çipler üretirdi).
- **"Seri özeti":** Vikipedi'de gösterilmez — o kart `mainpage` özetini gösterir ve Vikipedi ana
  sayfası yapımın özeti değildir (yapım özeti "Wiki özeti" kartındadır).

### 2.7 Etiketler, lisans ve küçük iyileştirmeler

- Alt bilgiler kaynağa göre doğru: `Kaynak: Vikipedi` (Fandom eki yok) + **CC BY-SA 4.0** notu
  (`fandom_source_label_wikipedia`, `fandom_license_note_wikipedia`); madde içi sayfa (öğe)
  alt bilgisi de aynı şekilde düzeltildi.
- Galeri penceresindeki kaynak adı wiki ailesine göre: `FandomUrls.displaySourceName()` →
  "Fandom" / "Vikipedi" / "Wikipedia" (Vikipedi görsellerinde artık "Fandom" yazmaz).
- Vikipedi maddelerinde `prop=images` isteği atlanır: madde başına onlarca ikon/logo dosyası
  dönüyordu ve bu liste arayüzde kullanılmıyordu (galeri, bilgi kutusunun kendi görsellerini
  kullanır).
- Okuyucuda Vikipedi sayfası açıldığında başlığın altındaki kısa açıklama artık maddenin giriş
  metninden (`extracts`, `exintro`) doldurulur; Fandom'a özgü `fandomdescription` alanı
  Vikipedi'de bulunmadığı için bu satır boş kalıyordu.
- Madde önbellek anahtarı `media-page:v5` → **`v6`**: dil sürümü davranışı değiştiği için eski
  kayıtlar yanlış dilde madde döndürebilirdi.
- `fandom_open_page_wikipedia` → **"Vikipedi'de oku"**, `fandom_unavailable_media` →
  "Bu yapım için wiki bulunamadı (Fandom veya Vikipedi)."
- `FandomUrlsTest` içine kaynak adı eşlemesini doğrulayan birim test eklendi.

---

## 3) Değiştirilen dosyalar

| Dosya | Değişiklik |
| --- | --- |
| `data/fandom/FandomWikiResolver.kt` | Çok dilli Vikipedi kaynak listesi (`resolveWikipediaSources`, `wikipediaRef`, `expectedQids`) |
| `data/fandom/WikipediaResolver.kt` | `expectedQids` parametresi + Wikidata kimlik denetimi |
| `data/fandom/FandomApi.kt` | `pageWikidataItems()` (yalnızca Vikipedi için kimlik sorgusu) |
| `data/fandom/FandomApiParsers.kt` | `FandomTitleLookup.wikidataItem` alanı |
| `data/fandom/FandomRepository.kt` | Vikipedi'de sessiz dil geçişi kaldırıldı, `prop=images` atlanır, önbellek `v6` |
| `data/fandom/FandomUrls.kt` | `displaySourceName()` |
| `ui/screens/detail/wiki/FandomMediaWikiViewModel.kt` | İlgili maddeler (madde kaynaklı), `openReaderMainPage`, `GuideState.Unsupported`, `translationBases` + `pruneStaleTranslations`, `isTargetLanguageSource`, kaynak değişiminde tam sıfırlama |
| `ui/screens/detail/wiki/MediaWikiTabContent.kt` | Vikipedi başlık/çip/ilgili madde/alt bilgi etiketleri, madde başlığı satırı, öğe alt bilgisi |
| `ui/screens/detail/wiki/CharacterWikiTabContent.kt` | Kaynak/lisans/buton etiketleri, galeri kaynak adı |
| `ui/screens/detail/wiki/FandomWikiReader.kt` | İlgili madde kartı etiketi, galeri kaynak adı |
| `res/values/strings.xml`, `res/values-en/strings.xml` | 6 yeni metin, 2 metin güncellendi (TR + EN) |
| `app/build.gradle.kts`, `RELEASE_NOTES.md`, `release_notes_v2.4.289.md` | Sürüm 2.4.289 + sürüm notları |
| `app/src/test/.../FandomUrlsTest.kt` | Kaynak adı testi |

---

## 4) Doğrulama durumu

- Değişen tüm Kotlin dosyaları sözdizimi denetiminden geçirildi (tree-sitter Kotlin ayrıştırıcısı,
  46 dosya — hata yok). Ayrıştırma tip kontrolü değildir; bu ortamda JVM/Gradle bulunmadığı için
  **derleme ve birim testleri burada çalıştırılamadı**. Yayın öncesi `./gradlew test` ve
  `assembleGmsRelease` çalıştırılmalıdır.
- Ağ erişimi olmadığından canlı Vikipedi yanıtlarıyla uçtan uca denenemedi; davranış, MediaWiki
  API sözleşmeleri (`action=parse&prop=sections|text`, `action=query&prop=pageprops|extracts|
  pageimages`, `list=search`, `prop=categorymembers`) üzerinden kod okuyarak doğrulandı.
- Vikipedi kaynaklarında çalışmayan tek uç Fandom'a özgü `services.fandom.com` topluluk aramasıdır
  (`communitySearch`); diğer tüm uçlar Vikipedi ile uyumludur.

## 5) Bilinçli olarak değiştirilmeyenler

- `FandomMediaArticleMatcher` eşikleri (`MIN_SCORE 0.55`, `CERTAIN_SCORE 0.88`,
  `AMBIGUITY_MARGIN 0.06`) korundu: yakın iki adayda `null` döner, yani "yanlış madde" yerine
  "Madde bulunamadı" gösterilir. Bu, kullanıcının bildirdiği hataların kaynağı değildi.
- Fandom wiki'lerinde ilgili sayfaların ana sayfadan toplanması (hub davranışı) korundu.
- `FandomGuideCatalog` kategori listeleri korundu; Vikipedi'de yalnızca devre dışı bırakıldı.
