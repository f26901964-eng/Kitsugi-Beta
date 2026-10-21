# KÖK NEDEN RAPORU — Tüm kaynaklarda eksik tür / etiket / kurum ve Rusça–Çince sızıntısı

**Tarih:** 2026-10-21 · **Dal:** `arena/a610f459-kitsugi-beta` · **Taban:** `971abf1` (v2.4.336-beta)
**Kapsam:** AniList · MAL/Jikan · Bangumi · Shikimori · Kitsu · Simkl · TMDB detay sayfalarındaki
**tür (genres)**, **etiket (tags)**, **stüdyo / yapımcı / yayıncı / kanal / dergi** alanları.

---

## 1) Kullanıcı belirtisi

> “Birçok kaynakta bu türler ve etiketler kısmında eksiğimiz gözden kaçmış. Bangumi için yaptığımız
> gibi diğer tüm kaynakların türleri, etiketleri, yayıncı/yapımcı/stüdyo kısımları eksiksiz
> toplanacak; **Rusça veya Çince görünmesini istemiyorum**.”

Somut gözlemler:

| # | Gözlem | Nerede görülüyor |
|---|---|---|
| 1 | Bir kaynakta tür listesi doluysa diğer kaynakların türleri **hiç gelmiyor** | MAL/Jikan, Kitsu, Simkl, TMDB, Bangumi detayları |
| 2 | Shikimori türleri **Rusça** görünüyor (`Экшен`, `Повседневность`) | Shikimori detay sayfası |
| 3 | Bangumi'nin kendi stüdyo/yapımcı/kanal bilgisi diğer kaynakta varsa **siliniyor** | Bangumi detay sayfası |
| 4 | Aynı kurum hem “Stüdyolar” hem “Yapımcılar” altında iki kez | MAL/TMDB kaynaklı sayfalar |
| 5 | Sözlükte olmayan Çince etiket çip olarak ekranda kalıyor | Bangumi detay sayfası |

---

## 2) Kök nedenler (kod kanıtlı)

### KN-1 — Birleştirme değil, **yerine koyma** mantığı

`KitsugiDetailClient.enrichDetail` (TMDB zenginleştirmesi):

```kotlin
val updatedGenres = if (currentDetail.genres.isNotEmpty()) currentDetail.genres else trMeta.genres
val mergedStudios = if (currentDetail.studios.isNotEmpty()) currentDetail.studios else trMeta.studios
val mergedProducers = if (currentDetail.producers.isNotEmpty()) currentDetail.producers else trMeta.producers
val mergedNetworks  = if (currentDetail.networks.isNotEmpty())  currentDetail.networks  else trMeta.networks
```

`KitsugiBangumiDetailClient.mergeDetail`:

```kotlin
genres = other.genres.ifEmpty { base.genres },
studios = other.studios.ifEmpty { base.studios },
producers = other.producers.ifEmpty { base.producers },
serializations = other.serializations.ifEmpty { base.serializations },
```

Bu üçlü operatör (`if dolu ise onu, değilse diğerini`) **iki kaynağın verisini asla birleştirmez.**
Birincil kaynak doluysa ikincil kaynağın türleri/kurumları kaybolur; birincil boşsa ikincilin
tamamı gelir. Kullanıcının “eksik kalmış” dediği tablo tam olarak budur.

### KN-2 — Çapraz kaynak toplama yalnızca **AniList etiketleri** için vardı

`enrichDetail` içinde tek çapraz çağrı `KitsugiAniListDetailClient.fetchTagsAndAiringInfo` idi ve
sorgu **yalnızca `tags` + `nextAiringEpisode`** alanlarını istiyordu:

```graphql
tags { id name description category rank isMediaSpoiler isGeneralSpoiler }
nextAiringEpisode { episode timeUntilAiring airingAt }
```

`genres`, `studios` **hiç istenmiyordu**. MAL/Jikan'ın `themes` / `demographics` / `licensors`
alanları, AniList'in stüdyo düğümleri ve TMDB'nin yapım şirketleri detay sayfasına hiç ulaşmıyordu.

### KN-3 — Shikimori verisi **Rusça** taşınıyor, çözümleyici yok

Shikimori REST yanıtındaki `genres[].name` alanı Rusçadır:

```kotlin
val gName = gObj.optString("name")   // "Экшен", "Повседневность", "Махо-сёдзё"
genresList.add(gName)
...
genres = genresList.toTurkishGenres(),
```

`toTurkishGenre()` yalnızca **Bangumi (Çince/Japonca)** sözlüğüne ve **İngilizce** tabloya bakıyordu:

```kotlin
cleaned.toLocalizedBangumiTagOrNull()?.let { return it }
if (!isTurkish()) return this
val mapped = genreMap[cleaned] ?: ...                 // İngilizce anahtarlar
return SearchTranslation.displayLabel(cleaned)        // bilinmeyen → olduğu gibi
```

Rusça bir tür `genreMap`'te yok → `displayLabel` değiştirmeden döndürüyor → **Kiril metin ekrana
düşüyor**. Depoda `[А-Яа-яЁё]` araması yapıldığında Rusça çeviri tablosunun **hiç olmadığı**
doğrulandı (`grep` yalnızca Türkçe yorum satırlarında Kiril buluyor).

### KN-4 — Çözülemeyen Çince etiket “listenin sonuna atılıyor”, **gösteriliyor**

```kotlin
if (isUntranslatedCjkTag(value)) untranslated += value else translated += value
...
return (translated + untranslated).take(limit)
```

Kullanıcı Çince metin görmek istemiyor; ancak kod onu bilinçli olarak listenin sonuna ekliyordu.
(Kullanıcı isteği: “eksiksiz **ve** Rusça/Çince görünmesin”.)

### KN-5 — Kurum adları gösterim katmanında çözülüyor, **modelde ham** kalıyor

`toLocalizedOrgName()` sözlükte olmayan adı olduğu gibi döndürüyordu. Rusça bir kurum adı
(`Студия Гибли`) hiçbir katmanda Latin harfine çevrilmiyordu. Ayrıca aynı kurum `studios` ve
`producers` listelerinde birlikte gelebildiği için **iki kez** görünüyordu (MAL/TMDB).

### KN-6 — Yan bulgu: `sync_bangumi_tag_strings.py` **veri kaybettiren** bir betikti

```python
head = text.split(marker, 1)[0]
updated = f"{head}{block}\n\n</resources>\n"   # işaretçiden sonrası SİLİNİR
```

Betik, `bangumi_tag_*` bloğunun dosyanın **son** bloğu olduğunu varsayıyor. Oysa blok 3.099.
satırda başlıyor; arkasında kurum kaynakları (`bangumi_studio_*`, `bangumi_magazine_*`), wiki ve
stream kaynakları var (~8.800 satır). Betik yazma modunda çalıştırılsaydı **bu satırların tamamı
silinecekti**. `--check` bu yüzden sürekli kırmızıydı; kimse yazma modunu çalıştırmaya cesaret
edemediği için sözlükteki **494 etiket** dil dosyalarına hiç yazılmamıştı (ayrıca sözlükte olmayan
`bangumi_tag_chinese_speaking` artığı dosyada duruyordu).

---

### KN-7 — Kurum alanında **veri olmayan değerler** çip olarak çiziliyordu

Bangumi infobox'ı ve kullanıcı katkıları zaman zaman kurum alanına ad değil çöp yazıyor:

| Değer | Kaynak | Sonuç |
|---|---|---|
| `っ´Д`)っゼロ式の家` | infobox | “Yayıncı” olarak kaomoji çipi (dil dosyasında `bangumi_publisher_cjk_8007fa`) |
| `"""アフタヌーン` | sözlük üretimi | Baştaki üç tırnak ekranda kalıyordu |
| `芳文社; B6版` | infobox `出版社` alanı | Baskı notu kurum adının parçası sanılıyordu |

Gösterim katmanı (`toLocalizedOrgName`) yalnızca çeviri yapıyor, **temizlik yapmıyordu**.

### KN-8 — Sözlük değeri ile dil dosyası arasında sessiz kayma

`BangumiTagDictionary` içinde `area_chinese_speaking` girdisinin Türkçesi `Çinçe Konuşulan Bölge`
(yazım hatası) iken `diag/bangumi_master/tags.tsv` doğru değeri (`Çince`) taşıyordu.
`verify_bangumi_master.py` bunu **1 UYUŞMAZLIK** olarak yakaladı; MASTER hattı daha önce
yeşil olduğu için hata sonradan girmişti.

### KN-9 — Üreteç, kurum sözlüğünü **binlerce satır** kırpabiliyordu

`diag/bangumi_master/gen_bangumi_master.py` yazma modunda `BangumiOrgDictionary.kt` dosyasını
`diag/bangumi_master/orgs.tsv`'den yeniden kuruyor. Ölçüm:

| Kaynak | Satır |
|---|---|
| `orgs.tsv` | 365 satır |
| `BangumiOrgDictionary.kt` (commit'li) | **11.693 eşleme satırı** |

Yani TSV, sözlüğün yalnızca küçük bir bölümünü taşıyor. Bu turda doğrulama amacıyla yazma modu
çalıştırıldı ve dosya **1.125 satıra düştü** (≈10.800 satır kayıp). Dosya `git checkout` ile
geri alındı; kaynak kod kaybı **oluşmadı**, ancak tuzak gerçekti.

Kalıcı çözüm: üretece **güvenlik freni** eklendi. Yeni satır sayısı mevcut satır sayısının
yarısından azsa sözlük **yazılmaz** (uyarı basılır, diğer üretimler devam eder); bilinçli
küçültme için `--force` gerekir. `orgs.tsv` mevcut sözlükten yeniden türetilene kadar bu fren
açık kalmalı.

## 3) Çözüm özeti

| Kök neden | Çözüm | Dosya |
|---|---|---|
| KN-1 | `KitsugiTaxonomyMerge`: tür/etiket/kurum için **birleşim** (union) + diller arası kanonik tekilleştirme | `data/remote/KitsugiTaxonomyMerge.kt` |
| KN-2 | `KitsugiTaxonomySources`: AniList (genres + studios) + MAL/Jikan (genres/themes/demographics/studios/producers/licensors) paralel ve zaman tavanlı toplama | `data/remote/KitsugiTaxonomySources.kt` |
| KN-2 | AniList hafif sorgusuna `genres` + `studios { edges { isMain node { id name } } }` eklendi | `KitsugiAniListDetailClient.kt` |
| KN-3 | `RussianTaxonomyDictionary`: 114 grup, Shikimori'nin **79 resmî türünün tamamı** + yaygın Rusça etiketler; MAL kimlik eşlemesi | `utils/RussianTaxonomyDictionary.kt` |
| KN-3 | Tek çözümleyici `toLocalizedTaxonomyLabel()`: Bangumi → Rusça → İngilizce → **Kiril transliterasyonu** | `utils/KitsugiTaxonomyLabels.kt` |
| KN-4 | Sözlükte olmayan Çince/Japonca etiket **gösterimden çıkarılır** (modelde korunur) | `KitsugiTranslations.localizedDistinctTags` |
| KN-5 | `toLocalizedOrgName()` Kiril adı Latin harfine çevirir; `mergeOrgs` + `dropProducersThatAreStudios` tekrarı kaldırır | `KitsugiTranslations.kt`, `KitsugiTaxonomyMerge.kt` |
| KN-6 | Betik artık bloğu **yerinde** yeniden yazar; blok tek parça hâline getirildi, artık kaynak silindi, 494 eksik etiket dil dosyalarına yazıldı | `scripts/sync_bangumi_tag_strings.py` |
| KN-7 | `sanitizeOrgName()`: tırnak artefaktı, `;` sonrası baskı notu ve kaomoji ayıklanır; boşalan kayıtlar hem modelde hem `KitsugiStudiosCard` içinde çizilmez | `KitsugiTranslations.kt`, `KitsugiDetailComponents.kt` |
| KN-8 | Sözlük değeri `Çince` olarak düzeltildi, dil dosyası eşitlendi | `BangumiTagDictionary.kt`, `values/strings.xml` |
| KN-9 | Üreteç güvenlik freni: sözlük %50'den fazla küçülecekse yazılmaz (`--force` ile aşılır) | `diag/bangumi_master/gen_bangumi_master.py` |

---

## 4) Doğrulama (bu sandbox'ta koşulanlar)

```
$ python3 scripts/check_taxonomy_master.py
TÜM KONTROLLER GEÇTİ — 114 sözlük girişi, 79 resmî Shikimori türü, 114 dil dosyası kaynağı, 0 uyarı.

$ python3 scripts/sync_taxonomy_tag_strings.py --check      → exit 0 ("Zaten güncel")
$ python3 scripts/sync_bangumi_tag_strings.py --check       → exit 0 ("Zaten güncel")   [önceden exit 1]
$ python3 scripts/check_localization_resource_parity.py     → exit 0 (17.795 string, 4 dizi eşit)
$ python3 scripts/check_taxonomy_master.py --worklist       → diag/taxonomy_master/worklist.tsv
```

**Ek doğrulama — gerçek Kotlin ayrıştırıcısı:** sandbox'ta JDK olmadığı için derleme yapılamıyor;
bunun yerine `tree-sitter-kotlin` ile **sözdizimi ağacı** kuruldu. Değiştirilen 12 dosya
`ERROR`/`MISSING` düğümü içermiyor. Depoda ayrıştırıcının hata verdiği 6 dosya var; bunların
tamamı **HEAD'de de aynı** (dil bilgisi kısıtı, bizim değişikliğimiz değil).

**Koşulamayanlar (dürüst boşluk):** `./gradlew test`, `kotlinc` derlemesi ve APK üretimi bu
sandbox'ta çalıştırılamadı (JDK yok). Cihaz/CI koşumu ve `TaxonomyLabelsTest` (24 test) CI'ya
bırakıldı; testlerin **beklediği değerler** sözlükten Python ile yeniden üretilerek tek tek
doğrulandı (79 resmî tür, `Экшен`→`Aksiyon`, `«СЁДЗЁ»`→`Shōjo`, MAL kimlikleri).
