# PLAN/TASK — Wiki Faz 3: etiket/başlık çevirileri, görsel yükleme, kopyala+çevir, tüm sayfalara Wiki

Tarih: 2026-10-13
Durum: **Kodlandı.** Derleme + cihaz testi kullanıcıda (sandbox'da JDK/Android SDK yok;
python/grep ile kaynak-anahtar, XML geçerliliği, parantez dengesi ve `when` tekrarları denetlendi).
Önceki adımlar: `PLAN_TASK_FANDOM_WIKI_ENTEGRASYONU_2026-10-12.md` (Faz 1),
`PLAN_TASK_WIKI_ANINDA_SEKME_VE_MEDYA_WIKI_FAZ2_2026-10-13.md` (Faz 2).

## Kullanıcı geri bildirimi (5 ekran görüntüsü — Eren Yeager / Attack on Titan Wiki)

1. **Görsel 1, 2, 5:** Bilgi kutusu etiketleri (Name, Alias, Species…), bölüm/İçindekiler
   başlıkları ("People killed" İngilizce kalmış) ve bilgi kutusu değer alanları dil
   dosyalarına EN + TR olarak eklenip çevrili gösterilmeli.
2. **Görsel 3:** Wiki görselleri yüklenmiyor (portre + 5 küçük görsel kutusu boş).
3. **Görsel 4, 5:** "Wiki özeti" kartı ve bilgi kutusu değerleri için küçük **kopyala** ve
   **çevir** düğmeleri — normal detay sayfalarındaki açıklama bölümüyle aynı mantık.
4. Wiki **her** detay sayfasında olmalı: anime, film, dizi, manga, manhwa, light novel,
   +18, karakter, ekip/seslendirme sanatçısı… ve sayfa tipine uygun içerik sunmalı.

## Yapılanlar

### 1. Bilgi kutusu etiketleri / grup başlıkları / kısa değerler → dil dosyaları

- **Yeni** `ui/screens/detail/wiki/FandomInfoboxLabels.kt` (saf eşleyici, birim testli):
  - `labelResId(label, source)`: ~90 etiket kalıbı (Name, Alias(es), Kanji, Rōmaji, Species,
    Gender, Age, Birthday, Height, Weight, Blood type, Status, Affiliation, Former affiliation,
    Occupation, Rank, Family/Relatives, Debut, Voice actor (JP/EN), Titan/Form, Weapon, Kills…).
    Etiket bilinmiyorsa Portable Infobox `source` anahtarına (ör. `occupation`) bakılır.
  - `headerResId(header)`: grup başlıkları (Biological/Professional/Other Information, Debut,
    Voice actors, Relatives, Abilities, Affiliation, Production, Chronology, Gallery).
  - `valueResId(value)`: yalnızca kısa, sayılabilir değerler (Male/Female, Unknown, Alive,
    Deceased, Presumed dead, Missing, Active/Retired, Human/Titan vb.). Serbest metin **çevrilmez**
    (çeviri düğmesine bırakılır) — yanlış çeviri riski yok.
- `FandomSectionTitles.kt` genişletildi: "People killed / Kills / Titans killed / Victims",
  "Status" (artık ayrı `fandom_section_status`), "Quotes", "Development", "In other media",
  "Battles", "Techniques", "Sinopsis"… (ekran görüntülerindeki tüm başlıklar kapsandı).
- `values/strings.xml` (TR) ve `values-en/strings.xml`: **178 yeni** `fandom_ib_*` /
  `fandom_section_*` anahtarı + 12 arayüz metni; iki dosyada da 244 `fandom_*` anahtar,
  birebir parite. Kullanılan her `R.string.fandom_*` iki dosyada da mevcut (python ile doğrulandı).
- `CharacterWikiTabContent.kt`: etiket, grup başlığı ve değer artık `stringResource` ile çizilir;
  bilinmeyenler olduğu gibi kalır.

### 2. Görsellerin yüklenmemesi — teşhis + dayanıklı yükleme

Sandbox'tan Fandom CDN'e çıkış olmadığı için kök neden canlıda kesinleştirilemedi; gerçek bir
Fandom `pageprops.infoboxes` yanıtı (GitHub'daki bir fixture) ile parser girdisi doğrulandı
(`url` daima mutlak `https://static.wikia.nocookie.net/...`, dosya adı `.webp` olabilir).
En olası nedenler (hepsine karşı önlem alındı):

| Olası neden | Önlem |
|---|---|
| CDN, `Referer`/`Accept` olmayan isteklere 403/404 dönüyor | `core/network/FandomImageHeaders.kt` (yeni) + `KitsugiHttpClient` interceptor: yalnızca `*.wikia.nocookie.net` / `*.wikia.com` için `Referer: https://www.fandom.com/` ve AVIF içermeyen `Accept`. Coil, `KitsugiHttpClient.client`'ı kullandığı için otomatik devreye girer. |
| `scale-to-width-down/N` yolu `.webp.webp` gibi adlarda hata veriyor | `FandomWikiImage.kt` (yeni): **ölçekli → özgün → `format=original`** zinciri; her adayın hatası logcat'e `FandomWikiImage` etiketiyle yazılır. |
| İçerik pazarlığı (CDN AVIF döndürüyor, cihaz çözemiyor) | `Accept` AVIF'siz + `FandomUrls.withOriginalFormat()` (yeni) son çare. |
| Tümü düşerse boş kutu kalıyordu | Kırık görsel simgesi (`BrokenImage`) + `fandom_image_failed`. |

Portre ve küçük görseller `AsyncImage` yerine `FandomWikiImage` ile çizilir.
**Cihazda doğrulama:** `adb logcat -s FandomWikiImage` — başarısızlık varsa adres + hata mesajı görünür.

### 3. Kopyala + çevir düğmeleri (açıklama kartıyla aynı düzen)

- **Wiki özeti kartı:** mevcut çeviri düğmesinin yanına 36 dp kopyala düğmesi
  (`fandom_copy_summary`); çeviri varsa çevrilmiş metin kopyalanır; `toast_copied_clipboard`.
- **Bilgi kutusu:** yeni araç satırı (`fandom_infobox_title`): **toplu çeviri** düğmesi
  (`FandomCharacterWikiViewModel.translateInfobox()` — yerelleştirilmiş kısa değerler atlanır,
  kalanlar tek istekte satır satır çevrilir, satır sayısı tutmazsa tek tek; "Orijinal" ile geri)
  + **tümünü kopyala** (`fandom_copy_infobox`, "Etiket: değer" satırları, grup başlıklarıyla,
  gizli spoiler alanları `[Spoiler]`).
- **Her değer satırı:** 13 dp minik kopyala simgesi (`fandom_copy_value`); spoiler gizliyken yok.
- **Bölümler:** açılan her bölümde çeviri düğmesinin yanında kopyala (`fandom_copy_section`).
- ViewModel: `KEY_INFOBOX`, `infoboxFieldKey(i)` (`ib:<index>`), `showOriginal(KEY_INFOBOX)`
  tüm `ib:*` çevirilerini temizler; otomatik çeviri tercihi bölüm/özet için eskisi gibi.

### 4. Wiki her sayfada

| Sayfa | Durum | Nasıl |
|---|---|---|
| Anime / film / dizi / manga / manhwa / light novel / +18 (MAL, AniList, Kitsu, Shikimori, Simkl, TMDB…) | Faz 2'den **var** (son sekme "Wiki") | `MediaEntryDetailPage`, `ApiResultDetailPage` → `MediaWikiTabSlot` |
| Karakter | Var; bu fazda çeviri/kopyala/görsel iyileştirmeleri | `CharacterWikiTabSlot` (artık `wiki/CharacterWikiTabSlot.kt`, paylaşımlı) |
| **Ekip / seslendirme sanatçısı** | **YENİ 4. sekme "Wiki"** (`fandom_staff_tab_wiki`) | `StaffDetailPage` → `FandomCharacterWikiViewModel.bindStaff()`; sorgu `FandomCharacterQueries.fromStaff()`: kişinin adı + seslendirdiği **ana roller önce**, sonra diğer roller ve ekip çalışmaları (en çok 6 yapım, tekrarsız). Wiki'de kişi sayfası yoksa `fandom_unavailable_staff`. TMDB (gerçek kişi) atlanır. |
| **Eklenti kaynaklı manga/manhwa** (`MangaDetailScreen`) | **YENİ açılır "Wiki" kartı** (açıklama/bölümler arasında) | Kimlik yok → **başlıkla** çözülür. `FandomMediaQuery.cacheKey()` artık kimliksiz sorguda `title:<m|a>:<normalize>` üretir (önceden `null` → `resolveMediaWiki` hiç çalışmıyordu). İçerik yalnızca kullanıcı kartı açınca yüklenir. |
| **TV (Android TV) detay** | **YENİ açılır "Wiki" kartı** listenin sonunda (`fandom_tv_wiki_title`) | `MediaWikiSectionCard` + `FandomMediaQueries.fromMedia(displayResult…)`; D-pad ile açılır. |
| Stüdyo sayfası | **Bilinçli atlandı** | Fandom'da stüdyo (ör. MAPPA) için tutarlı bir "evren wiki'si" yok; yanlış eşleşme riski yüksek. İstenirse sonraki faz. |

Yeni paylaşımlı bileşen: `wiki/MediaWikiSectionCard.kt` (sekmesi olmayan ekranlar için
açılır-kapanır Wiki kartı; Fandom ayarı kapalıysa hiç çizilmez).

## Değişen / eklenen dosyalar

**Yeni**
- `core/network/FandomImageHeaders.kt`
- `ui/screens/detail/wiki/FandomInfoboxLabels.kt`
- `ui/screens/detail/wiki/FandomWikiImage.kt` (+ `FandomImageCandidates`)
- `ui/screens/detail/wiki/CharacterWikiTabSlot.kt` (CharacterDetailPage'den taşındı, staff ile paylaşılır)
- `ui/screens/detail/wiki/MediaWikiSectionCard.kt`
- Testler: `FandomInfoboxLabelsTest`, `FandomImageCandidatesTest`, `FandomImageHeadersTest`

**Güncellenen**
- `core/network/KitsugiHttpClient.kt` — Fandom görsel başlıkları
- `data/fandom/FandomUrls.kt` (`withOriginalFormat`), `FandomWikiResolver.kt` (`cacheKey` başlık yedeği),
  `FandomCharacterQuery.kt` (`fromStaff`)
- `ui/screens/detail/wiki/CharacterWikiTabContent.kt`, `FandomCharacterWikiViewModel.kt`, `FandomSectionTitles.kt`
- `ui/screens/detail/CharacterDetailPage.kt`, `StaffDetailPage.kt`
- `ui/screens/manga/MangaDetailScreen.kt`, `ui/tv/detail/TvDetailScreen.kt`
- `res/values/strings.xml`, `res/values-en/strings.xml`
- Testler: `FandomSectionTitlesTest`, `FandomUrlsTest`, `FandomCharacterQueriesTest`, `FandomGuideAndQueryTest`

## Kullanıcıda doğrulama listesi

1. `./gradlew :app:testDebugUnitTest --tests "*Fandom*"` — yeni/güncel testler.
2. Eren Yeager → Wiki: etiketler/gruplar/kısa değerler Türkçe; "İnsanlar öldürüldü" vb. başlıklar;
   bilgi kutusu üstünde çevir + kopyala; satır sonlarında minik kopyala.
3. Görseller: portre + küçükler geliyor mu? Gelmiyorsa `adb logcat -s FandomWikiImage` çıktısı.
4. Bir seslendirme sanatçısı (ör. Yūki Kaji) → 4. sekme "Wiki".
5. Eklenti mangası → "Wiki" kartı açılınca seri özeti/evren rehberi.
6. Android TV'de bir dizi → listenin sonunda "Wiki" kartı.
