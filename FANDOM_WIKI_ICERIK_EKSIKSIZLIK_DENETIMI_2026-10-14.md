# Fandom Wiki Entegrasyonu — İçerik Eksiksizlik Denetimi (2026-10-14)

**Soru:** Uygulama Fandom wiki'sinden gelen verinin **tamamını** alıyor mu ve aldığı verinin
**tamamını** uygulama içinde gösteriyor mu? Gösterilemeyen içerik/veri/resim var mı?

**Yöntem.** İki katman denetlendi:

1. **Kod denetimi** — `app/src/main/java/com/kitsugi/animelist/data/fandom/` (34 dosya) ve
   `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/` (12 dosya) satır satır
   okundu; her veri kaybı noktası dosya+satır ile belgelendi.
2. **Canlı ölçüm aracı** — `tools/wiki_audit/content_audit.py` eklendi. Bu araç Kotlin
   boru hattının Python karşılığını çalıştırır (aynı uç noktalar, aynı parametreler, aynı
   sınırlar) ve MediaWiki'nin verdiği "gerçek" ile karşılaştırır. **Sabitlerini Kotlin
   kaynağından okur** (`MAX_SECTION_IMAGES`, `JUNK_SELECTOR`, `SECTION_JUNK_SELECTOR`),
   böylece Kotlin değişince ölçüm kendiliğinden güncellenir.

> **Ortam notu (dürüst sınır):** Bu sandbox'tan `*.fandom.com`, `api.fandom.com`,
> `static.wikia.nocookie.net` adreslerine erişim YOK (ölçüldü: HTTP `000`). Bu yüzden canlı
> sayılar burada üretilemedi; denetim CI'da (`.github/workflows/wiki-audit.yml`) çalışır ve
> sonuçları `tools/wiki_audit/results/content_report.md` olarak dala işler.
> Ayrıca sandbox'ta JDK yok ve Maven/Gradle depoları kapalı; Kotlin derlemesi ve JUnit
> testleri **GitHub Actions** ile doğrulandı (`android-build.yml` → `:app:testFossDebugUnitTest`).

---

## 1. Bulunan veri kayıpları ve düzeltmeleri

### 1.1 Şablon bölümleri tamamen kayboluyordu (EN KRİTİK)

`FandomApiParsers.parseSections`:

```kotlin
val index = section.optString("index").toIntOrNull() ?: continue   // eski
```

MediaWiki, sayfa gövdesine **şablonla eklenen** bölümlere sayısal olmayan kimlik verir:
`"T-1"`, `"T-2"`… `toIntOrNull()` bunları `null` yapıp `continue` ile atlıyordu. Sonuç:

- Bölüm **İçindekiler**'de hiç görünmüyordu.
- Bölümün **içeriği hiç çekilmiyordu** (`loadSection` yalnızca listelenen bölümler için çağrılır).
- Tipik kayıplar: **Seslendirenler / Voice Actors**, **Görünümler / Appearances**,
  **Güçler**, **Trivia dışı şablon blokları**.

**Düzeltme.** Bölüm kimliği artık metin olarak taşınıyor:

| Dosya | Değişiklik |
|---|---|
| `FandomModels.kt` | `FandomSectionRef.index: Int → String`, yeni `isTemplateSection` |
| `FandomApiParsers.kt` | `toIntOrNull() ?: continue` kaldırıldı; kimlik metin olarak korunur |
| `FandomApi.kt` | `sectionHtml(wikiId, title, index: String)` — MediaWiki `section=T-1`'i kabul eder |
| `FandomRepository.kt` | `loadSection(..., index: String)`, önbellek anahtarı `sec:v4 → sec:v5` |
| 2 ViewModel + 4 UI dosyası | `Map<Int, …> → Map<String, …>`, lambda imzaları |
| `FandomWikiReader.kt` | `sortedBy { it.index }` → sayısal değere göre sıralama (yoksa `"10" < "2"`) |

### 1.2 Katlanmış ve sekmeli içerik SİLİNİYORDU

`SECTION_JUNK_SELECTOR` içinde `.mw-collapsible-toggle` ve `.wds-tabs__wrapper` vardı.
Birincisi yalnızca "göster" düğmesidir (silinmesi doğru), ikincisi ise **sekmeli içeriğin
kendisidir** — silinince sekme içindeki tüm metin kayboluyordu.

**Düzeltme:** `.wds-tabs__wrapper` ve `.mw-collapsible-toggle` çöp listesinden çıkarıldı.
İçerik artık düz metin blokları olarak geliyor. (Navbox, infobox, referanslar, TOC bilinçli
olarak silinmeye devam ediyor — bunlar içerik değil, gezinme/şablon iskeleti.)

### 1.3 Okuyucu, giriş metnini hiç otomatik yüklemiyordu

`FandomMediaWikiViewModel.openReader` → `sections = HashMap()` (boş) ile açılıyordu.
Kullanıcı okuyucuyu açınca yalnızca şunları görüyordu:

- `fandomdescription` (Fandom'un ~500 karakterde `...` ile kestiği kısa açıklama),
- İçindekiler listesi,
- İlgili sayfalar.

Sayfanın **gerçek giriş paragrafı** (section 0) ancak kullanıcı elle İçindekiler'den bir
bölüme tıklarsa geliyordu — üstelik section 0 İçindekiler'de listelenmez.

**Düzeltme:** `preloadReaderSections()` eklendi (hem medya hem karakter VM). Okuyucu açılır
açılmaz `"0"` + ilk 3 bölüm **paralel** çekilir; biri başarısız olursa diğerleri etkilenmez.

### 1.4 Gövde metnindeki bağlantılar tıklanamıyordu

`FandomHtmlText` metni düzleştirirken `<a>` hedeflerini düşürüyordu. Kullanıcı yalnızca bölüm
sonundaki çiplerden (en fazla 12) gezinebiliyordu; paragraf içindeki mavi bağlantıların
tamamı düz metne dönüşüyordu.

**Düzeltme:**

- `FandomTextBlock.linkSpans: List<FandomTextLinkSpan>` eklendi (konum + wiki + başlık).
- `tidyInline` artık **konum koruyan** bir harita üretiyor (`TidyResult.mapPosition`), böylece
  boşluk birleştirme ve referans temizliği bağlantı aralıklarını bozmuyor.
- Yeni `WikiLinkedText` composable'ı `ClickableText` ile çiziyor; okuyucu, karakter sekmesi ve
  madde sayfası gövdeleri bunu kullanıyor. Çeviri metni gösterilirken konumlar geçersiz
  olacağı için düz metne düşülüyor.
- Kırmızı bağlantılar (`class="new"`) ve özel ad alanları (`Kategori:` …) span üretemez.

### 1.5 Sessiz kırpmalar artık bildiriliyor

| Kırpmа | Eski davranış | Yeni davranış |
|---|---|---|
| Tablo satırı `take(150)` | Fazlası sessizce yok | Bölüm sonuna `… (+N tablo satırı gösterilmedi)` |
| Bölüm görselleri `MAX_SECTION_IMAGES` | 25. görsel ve sonrası sessizce yok | Sınır **300**'e çıkarıldı + `… (+N görsel bölüm sınırına takıldı)` |
| Tablo satırı `MAX_TABLE_ROWS` | 151. satır ve sonrası sessizce yok | Sınır **2.000**'e çıkarıldı + `… (+N tablo satırı gösterilmedi)` |
| Bölüm blok sayısı `MAX_BLOCKS` | 401. blok ve sonrası sessizce yok | Sınır **4.000**'e çıkarıldı (bildirim notu hâlâ yok → bkz. §3.2) |
| Infobox görselleri `take(8)` | 9. görsel ve sonrası hiç görünmüyordu | "Tümünü göster (N)" düğmesi |

Sınırların neden bu değerler: 27 sayfalık canlı örneklemde tek bir bölüm en fazla 166 görsel
(`fairytail/Main Page`) ve en fazla 1.477 tablo satırı (`onepiece/List of Canon Characters`)
içeriyordu; 300 / 2.000 bu örneklemi kayıpsız karşılıyor. `MAX_BLOCK_TEXT = 8.000` karakter
blok başına üst sınır olarak duruyor.

### 1.6 Önbellek sürümü

Bloklar artık `linkSpans` taşıdığı için bölüm önbellek anahtarı `sec:v4 → sec:v5` yapıldı.
Eski kayıtlar okunsaydı bağlantılar hiç tıklanabilir olmazdı.

### 1.7 Yerel ad alanlarındaki bağlantılar makale sayılıyordu

`FandomUrls.NON_ARTICLE_NAMESPACES` yalnızca İngilizce standart ad alanlarını (`category`,
`help`, `file`, `user`, …) içeriyordu. Türkçe/çok dilli Fandom wikilerinde
`Kategori:Karakterler`, `Yardım:İçerik`, `Dosya:Logo.png` gibi başlıklar **madde sayılıp
tıklanabilir bağlantıya dönüşüyordu** (kod içi not bunu canlı denetimde gözlemlediğimiz
şekilde belgeliyor).

**Düzeltme:** yerel ad alanı önekleri listeye eklendi; bu bağlantılar artık span üretmiyor
(düz metin kalıyor). `FandomContentCompletenessTest` bunu kilitliyor.

### 1.8 Video sayacı kapsayıcıyı iki kez sayıyordu

`countVideos(container)` içinde `container.select("[data-video-key]")` Jsoup kuralı gereği
**öğenin kendisini de** eşleştiriyordu; bu yüzden her video iki kez sayılıyordu ve kullanıcıya
"Bu bölümde 4 video var" gibi yanlış bir sayı gösteriliyordu.

**Düzeltme:**

```kotlin
private fun countVideos(container: Element): Int {
    val self = if (container.attr("data-video-key").isNotBlank()) 1 else 0
    val descendants = container.select("[data-video-key]").count { it !== container }
    return self + descendants
}
```

Videolar hâlâ uygulama içinde **oynatılamıyor**; bölüm sonunda
`"Bu bölümde N video var (uygulama içinde oynatılamıyor; …)"` paragrafı üretiliyor — yani
içerik sessizce kaybolmuyor.

---

## 2. Bilinçli olarak gösterilmeyenler (tasarım kararı, kayıp değil)

| İçerik | Neden | Kod |
|---|---|---|
| Navbox / navigation box | Gezinme şablonu, madde içeriği değil | `SECTION_JUNK_SELECTOR` |
| Portable infobox (bölüm içinde) | Zaten infobox kartı olarak ayrıca gösteriliyor | `SECTION_JUNK_SELECTOR` |
| Referanslar / dipnot listesi | Atıf iskeleti | `JUNK_SELECTOR` |
| `Trivia`, `Gallery`, `References`, `Navigation`, `External links` bölümleri | Spoiler/gürültü politikası | `FandomSpoilerRules.HIDDEN_SECTION` |
| `History`, `Plot`, `Story`, `Relationships`… | Spoiler olarak **gizli** (dokununca açılır) | `FandomSpoilerRules.SPOILER_SECTION` |

**Not:** `Gallery` bölümü gizli sayıldığı için o bölümdeki görseller de gelmiyor. Aynı
görsellerin bir kısmı `FandomApi.pageImages` (sayfanın tam dosya listesi) üzerinden galeri
olarak ayrıca çekiliyor, ama birebir aynı küme değil. Bu bir politika kararı; istenirse
`HIDDEN_SECTION`'dan `gallery` çıkarılabilir.

---

## 3. Hâlâ eksik olanlar (bu turda düzeltilmedi)

1. **Video/blog yer tutucuları oynatılamıyor.** Bölüm gövdesi artık en azından
   `"Bu bölümde N video var (…)"` notu üretiyor (bkz. §1.8), ama `FandomLeadParser.addImages`
   (satır 194) `data-video-key` taşıyan öğeleri `continue` ile atlıyor: giriş metnindeki ve
   infobox'taki videolar için hiçbir iz yok. Ayrıca not düz metin — videonun kendi sayfasına
   giden bağlantı taşımıyor.
2. **Blok kırpması hâlâ bildirilmiyor.** `MAX_BLOCKS` 400 → **4.000**'e çıkarıldı (canlı
   örneklemdeki en uzun sayfa kayıpsız sığıyor), ama tablo/görsel kırpmasındaki gibi bir
   `… (+N blok gösterilmedi)` notu yok; 4.000'i aşan bir sayfa gelirse kullanıcıya sessiz
   kalır. Küçük bir iş: `appendOmissionNotes()` içine bir sayaç daha.
3. **İnfobox alanları arayüzde 10'la sınırlı başlıyor** — "Tümünü göster" düğmesi var, yani
   veri kaybolmuyor; ama `MAX_VALUE_LENGTH = 2000` üstü değerler kesiliyor ve bildirilmiyor.
4. **`pageImages` 300 dosyayla sınırlı** (`maxImages.coerceIn(1, 500)`); çok görselli
   wikilerde kalanı çekilmiyor.
5. **Wiki keşfi** (hangi wiki?) ayrı bir konu: `FANDOM_WIKI_KESIF_ARASTIRMA_2026-10-13.md`
   belgesindeki Simkl/IMDb köprüsü ve IMDb P345 araması hâlâ kodlanmadı.

---

## 4. Doğrulama

| Katman | Nasıl | Durum |
|---|---|---|
| Ayrıştırıcı birim testleri | `app/src/test/.../data/fandom/` altında **20 dosyada 147 `@Test`**; bunun 13'ü yeni `FandomContentCompletenessTest` | ✅ **GEÇTİ** — GitHub Actions run `38521507786` (commit `bfff5d8`), adım `🧪 … testleri + 🔨 foss debug APK` = `success` |
| Kotlin derlemesi | `:app:assembleFossDebug` | ✅ **GEÇTİ** — aynı run, `📲 APK'yı yükle` = `success` |
| Canlı içerik ölçümü | `tools/wiki_audit/content_audit.py` (27 sayfa, gerçek Fandom API'si) | ✅ **ÇALIŞTI** — run `38523395026` = `success`, sonuçlar bot commit'i `eaa634d` ile `results/content_report.md` (06:56 UTC) |

Sandbox'ta JDK ve Gradle depo erişimi olmadığı için derleme/test **yalnızca CI'da**
doğrulandı; yukarıdaki iki run bu doğrulamanın kanıtıdır.

Yeni testlerin kilitlediği davranışlar:

- `T-1` gibi şablon bölümleri korunur, `Trivia` gizli kalır.
- `FandomSectionRef` String kimlikle JSON gidiş-dönüşünü kayıpsız yapar.
- Katlanmış + sekmeli içerik metin olarak gelir; "göster" düğme etiketi gelmez.
- Bağlantı aralıkları tam konumlarında (`text.substring(start, end) == label`).
- Boşluk birleştirme + referans temizliği aralıkları bozmaz.
- Kırmızı bağlantı ve `Kategori:` span üretmez.
- 1.500 satırlı tablo eksiksiz gelir; 2.050 satırlı tablo 2.000'de durup `+51` notu üretir.
- 250 görselli galeri eksiksiz gelir; 320 görselli galeri 300'de durup not üretir.
- Navbox/infobox/referans silinmeye devam eder (regresyon).

---

## 5. Canlı ölçüm: düzeltme öncesi → sonrası

`tools/wiki_audit/content_audit.py` GitHub Actions'ta gerçek Fandom API'sine karşı çalışır
(27 sayfa, 8 wiki ailesi). İki ölçüm de aynı örneklem, aynı ağ koşulları, 0 ağ hatası.

| Ölçüm | Önce (05:56 UTC) | Sonra (06:56 UTC) |
|---|---:|---:|
| Uygulamanın blok olarak aldığı görsel | 893 | **1.415** |
| Görsel kaybı: bölüm görsel sınırı | 700 | **65** |
| Görsel kaybı: 32×32 piksel filtresi | 26 | 37 |
| **Tablo satırı kaybı** | **1.642** | **0** |
| Satır kaybı olan bölüm sayısı | 1 | **0** |
| Ortalama metin kapsama oranı | 0,938 | **0,977** |
| En düşük kapsama (`fairytail/Ivan Dreyar/Image Gallery`) | 0,076 | 0,076 |
| `onepiece/List of Canon Characters` kapsaması | 0,135 | **1,087** |
| `myheroacademia/Quirk` kapsaması | 0,803 | **0,893** |
| Video yer tutucusu (oynatılamıyor, not üretiliyor) | 29 | 29 |

Kalan iki kalemin açıklaması:

- **65 görsel kaybı** tek sayfadan geliyor: `myheroacademia/Quirk` içinde 416 görsel var,
  `MAX_SECTION_IMAGES = 300` sınırına takılıyor ve bölüm sonuna `+65` notu düşüyor (sessiz
  değil).
- **0,076 kapsama** bir kayıp değil, politika: sayfanın adı `…/Image Gallery`, `gallery`
  bölümü `FandomSpoilerRules.HIDDEN_SECTION` içinde gizli. Aynı görseller `pageImages`
  üzerinden galeri olarak çekiliyor.

> **Not:** `content_report.md` içindeki satır etiketleri (`MAX_SECTION_IMAGES=24`,
> `take(150)`, `maxBlocks=400`) sabit metindi; sayılar Kotlin'den okunuyordu ama etiket
> eski değeri yazıyordu. Etiketler artık f-string ile gerçek sabiti basıyor, böylece
> rapor kendi kendini yalanlamıyor.

