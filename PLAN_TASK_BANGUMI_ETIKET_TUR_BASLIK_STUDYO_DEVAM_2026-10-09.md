# 📋 PLAN / TASK — Bangumi Detay & Keşfet Yerelleştirme Devamı (Etiket/Tür · Başlık · Özet · Stüdyo Çipleri)

**Tarih:** 2026-10-09
**Dal:** `arena/dd89eab6-kitsugi-beta` · **Taban commit:** `cbf58d07` (v2.4.x, sürüm numarası artırılmadı)
**Durum:** Kod tarafı **TAMAMLANDI** ✅ · **Derleme/doğrulama BEKLİYOR** ⏳ (ortamda JDK / Android SDK yok)
**Önceki iş:** `PLAN_TASK_BANGUMI_TR_CEVIRI_CRASH_DOSYA_PAYLASIM.md` (temel Çince→Türkçe eşlemesi + romaji adlar)

**Kullanıcı talebi (ekran görüntüleriyle):**

1. CLANNAD / Steins;Gate / When They Cry detaylarındaki **Etiketler** hâlâ Çince → dil dosyası üzerinden TAMAMı çevrilsin (TR + EN).
2. **İlişkiler** ve **Öneriler** sekmelerinde İngilizce adı varken Çince başlık görünüyor (`ひぐらしのなく頃に解` vb.).
3. **Bölümler**: İngilizce ad varsa kullan, yoksa dokunma ("yapcak bişi yok o kısım").
4. Uygulama **dil ayarı** Bangumi içeriğinde "uyabildiği kadar" uygulanmalı.
5. **Açıklamalar**: İngilizce sürümü varsa o çekilsin (otomatik çeviri için daha iyi kaynak); yoksa olduğu gibi kalsın.
6. **Türler** de çevrilsin — Bangumi'nin tüm tür/etiket değerleri EN + TR karşılıklarıyla tabloya eklensin (`惊悚` vb.).
7. **Stüdyolar / Yapımcılar** çiplerine tıklanınca detay sayfası açılmıyor → açılabiliyorsa düzeltilsin.
8. Stüdyo/kişi adları sözlükle **çevrilmesin**, gerçek hâliyle gösterilsin (yaratıcı öneriye açığız).
9. Keşfet bölümündeki yeni şeritlerde (**Bangumi Dizileri, Yayındaki Anime, Yaklaşan Anime**) kartlar Çince görünüyor → her yerde düzeltilsin.

---

## 1 · Kök nedenler (kod üzerinden doğrulandı)

| # | Gözlem | Kök neden |
|---|--------|-----------|
| 1, 6 | Etiket/tür çiplerinde Çince | `utils/KitsugiTranslations.kt` içindeki `bangumiTagMap` yalnızca ~110 kayıttı; görünen etiketlerin çoğu tabloda yoktu. Ek olarak `TagChip`, `SearchTranslation.translateToTurkishForDisplay(...)` ile **her zaman Türkçeye** zorluyordu (EN arayüzünde yanlış) ve `values/strings.xml` varsayılanı Türkçe olduğu için `ja/ko/de…` dillerinde kaynak Türkçe düşüyordu. |
| 2, 9 | Liste satırlarında Çince başlık | Bangumi'nin liste/arama uçları (`POST /v0/search/subjects`, `GET /v0/subjects`, sezon, topRanked…) **infobox dönürmez**; `英文名` / `罗马字` yalnızca tekil kayıt isteğinde vardır. Liste modeli bu yüzden `titleEnglish`/`titleRomaji` boş → `getDisplayTitle()` çaresiz orijinali basıyordu. |
| 5 | Açıklama Çince | `mergeDetail` eşleşen kaydın (MAL/AniList/TMDB) İngilizce özetini hiç öne almıyordu. |
| 7 | Çipler tıklanmıyor | Bangumi infobox'ındaki şirket adları **düz metin**; `KitsugiStudio.id = 0`. `KitsugiStudiosCard` kuralı `id > 0 || source != "bangumi"` olduğu için çip pasifti. Ayrıca `KitsugiStudioClient`'ta `"bangumi"` dalı yoktu. |
| 3 | Bölümler | `/v0/subjects/{id}/episodes` yanıtında ayrı English alanı yok (yalnızca `name` ve `nameCN`). |

---

## 2 · Yapılan işler

### TASK-1 · Tek otoriter dil dosyası: `utils/BangumiTagDictionary.kt` (YENİ · 693 satır)
- **523 grup / 2182 yazım** (Çince/Japonca/İngilizce tüm yaygın biçimler). `data class BangumiTagEntry(key, turkish, english)` + `object BangumiTagDictionary`.
- Kapsam: formatlar, kaynak materyal (`游戏改`, `漫画改`, `GAL改`, `小说改`…), türler, alt temalar, ruh hâli, içerik uyarıları, ülke/distribütör, sektör sözcükleri, stüdyo/kişi takma adları, Japon dönemi adları.
- **Takvim etiketleri regex** ile: `2008年10月` → `Ekim 2008` / `Oct 2008`, `2008年` → `2008`, `2000年代`, `10月`.
- `normalize()`: tam genişlikli noktalama + `【】（）「」『』《》[]` sarımları yok sayılır, boşluklar tekilleşir, `lowercase(Locale.ROOT)`.
- Halka açık API yalnızca: `entryFor(label)`, `knownLabels()`.
- Eski `private data class BangumiTag` + `bangumiTagMap` (`KitsugiTranslations.kt` L136-252 civarı) **silindi** → tek kaynak.

### TASK-2 · Dil seçimi ve gösterim katmanı (`KitsugiTranslations.kt`)
- `fun isEnglish()` eklendi; `toLocalizedBangumiTagOrNull()` arayüz diline bakıyor.
- Sıra: `bangumi_tag_*` **dil dosyası override'ı (yalnızca tr/en)** → sözlük (TR/EN) → `null` (çağıran orijinali basar). Diğer dillerde Türkçe kaynağa düşme hatası kapandı.
- Yeni: `String.toLocalizedTagLabel()`, `List<String>.localizedDistinctTags(limit = 48)` (çevir → çevrilmiş hâline göre tekilleştir → **modelde orijinali tut**).
- `genreMap`/`statusMap`/`relationTypeMap` vb. korundu; `toTurkishGenre()` zaten dil-farkındalı (sözlük kapıyı geç açıyor), bu yüzden çağrı yerlerinde gerekısiz degişiklik yapılmadı.

### TASK-3 · Etiket/tür çipleri
- `ui/screens/detail/KitsugiDetailComponents.kt`: `TagChip` artık `tag.name.toLocalizedTagLabel()` basıyor (önceden Türkçeye zorluyordu); `KitsugiTagsCard` çeviri-sonrası **tekilleştirme** yapıyor (`京都动画 / 京阿尼 / Kyoto Animation` → tek çip).
- `KitsugiBangumiDetailClient.buildNativeDetail`: `tags = subject.tags.localizedDistinctTags(40)`, `genres = metaGenres(subject.metaTags)`.
- `mergeDetail`: etiketler `(base.tags + other.tags)` üzerinden **çevrilmiş etikete göre** tekilleştiriliyor; modelde ham değer kalıyor → çipe tıklayınca arama **orijinal Bangumi etiketiyle** yapılıyor.
- `ui/screens/search/SourceEngineFilterSheet.kt`: elle yazılmış 20'li "etiket → Türkçe açıklama" listesi kaldırıldı, görünen adlar sözlükten geliyor; seçili etiket çipleri de çevrili görünüyor.

### TASK-4 · Kalıcı Latin başlık önbelleği: `data/remote/BangumiTitleCache.kt` (YENİ · 174 satır)
- `bangumi_title_cache_v1` tercihleri + bellek aynası; kayıt başına JSON `{"r":romaji,"e":english,"n":native}`, anahtar `s<rawSubjectId>`, **kota 1500** (taşarsa depo sıfırdan kurulur — kayıp yalnızca "yeniden çözme" maliyeti).
- `usableLatin()` CJK içeren değeri yazmaz; `latinTitleFor(rawId, currentTitle)` mevcut başlık zaten Latinse **null** döner (asla gereksiz ezme).
- Yazım noktaları: `fetchDetail` (`rememberLatinTitle`), `enrich` (eşleşme olsun olmasın), ilişki/öneri zenginleştirmesi, karakter/kişi infobox çözümleri.
- Okuma noktaları: `KitsugiBangumiClient`'ın **her iki** `toSearchResult` overload'ı (liste/ızgara/şeritler), `withCachedLatinTitle` (ilişkiler), `toRelation`.
- `object KitsugiBangumiClient { init { BangumiTitleCache.warmUp() } }` → bellek aynası **IO kapsamında** ısındırılır, UI thread'i disk okumaz; yükleme tamamlanana dek `get()` null döner (bloklama yok).

### TASK-5 · İlişkiler / Öneriler başlıkları
- `enrichRelationTitles` yeniden yazıldı: **(1)** önbellek pası (ağ yok) → **(2)** yalnızca hâlâ CJK görünen satırlar `needsLatinTitle` filtresiyle, `RELATION_TITLE_ENRICH_LIMIT = 64` ve süre bütçesiyle tekil kayıt isteği → bulunan ad hem satıra işlenir hem kalıcılanır.
- Eski davranış (tüm liste çekilmeye çalışılıyordu) bütçeyi erken bitirip İngilizce adı olan kayıtların Çince kalmasına yol açıyordu.

### TASK-6 · Özet (açıklama) tercihi
- `mergeDetail` → `synopsis = pickSynopsis(base.synopsis, other.synopsis)`: Bangumi metni CJK **ve** eşleşen kayıtta Latin açıklama varsa İngilizce olan kullanılır; aksi hâlde Bangumi korunur. Uydurma çeviri yok.

### TASK-7 · Stüdyolar / Yapımcılar: çipler tıklanabilir
- `withStudioPersonIds(detail)`: `id <= 0 && source == "bangumi"` olan adlar Bangumi `/v0/search/persons` (`type = 2` 公司) ile eşlenip `KitsugiStudio.id`'ye yazılıyor.
  - Bütçe: `STUDIO_LOOKUP_LIMIT = 8` ad, `Semaphore(4)`, tur tavanı `STUDIO_LOOKUP_BATCH_TIMEOUT_MS = 6000`; `findStudioPersonId` önce birebir/kesişen ad, yoksa `type == 2` + ad kökü eşleşmesi.
  - `ConcurrentHashMap` null kabul etmediği için "bulunamadı" `0` olarak önbellekleniyor.
- `KitsugiStudioClient.fetchStudioDetail` içine **`"bangumi"` dalı** eklendi → `KitsugiBangumiDetailClient.fetchStaffDetail(personId)` (Bangumi'de kurumlar 人物 kayıtlarıdır), `KitsugiStudioDetail`'e çevrilir (occupation/homeTown/biography → `about`, `mediaWorks` → Yapımlar listesi).
- `ApiDetailTabContents.kt` + `EntryDetailTabContents.kt`: kaynak çıkarımı tek yerde, `resolveStudioClickSource(studioSource, mediaSource, mediaType)` — çipin kaynağı `bangumi` ise jikan/tmdb varsayımına düşmüyor (yanlış kimlik numaralı sayfa açılma riski kapandı).

### TASK-8 · Stüdyo isimleri: çeviri yok, kanonikleştirme var
- `studioLatinNameMap` 45 → **117 girişe** çıkarıldı ve `toLatinStudioName()` normalize ederek eşliyor: `【…】`/`(…)` notları ve `株式会社|有限会社|有限公司|股份有限公司` ekleri yok sayılıyor; birebir → normalize → case-insensitive sıra.
- **Felsefe:** bu bir çeviri tablosu değil, bilinen resmî yazım eşlemesi (京阿尼 → Kyoto Animation, 骨头社 → BONES, 疯房子 → Madhouse, 角川書店 → Kadokawa Shoten…). Eşleşmeyen ad — üretim komisyonları dâhil — **olduğu gibi** gösterilir; uydurma isim üretilmez.
- `splitCompanies` parantez-duyarlı hale getirildi: `X製作委员会【A、B、C】` → komisyonun kendi adı **ve** üyeleri ayrı çip (60 karakter tavanı, ilk 10). `normalizeCompanyKey()` karşılaştırma anahtarını üretir.

### TASK-9 · Bölümler
- `parseEpisodes`: `name` / `nameCN` içinde **Latin olan** tercih edilir; ikisi de CJK ise özgün ad aynen kalır (kullanıcı onayıyla bundan fazlası yapılmadı).

---

## 3 · Değişen / eklenen dosyalar

```
 YENİ  app/src/main/java/com/kitsugi/animelist/utils/BangumiTagDictionary.kt          (693 satır)
 YENİ  app/src/main/java/com/kitsugi/animelist/data/remote/BangumiTitleCache.kt       (174 satır)
 DEGİS app/src/main/java/com/kitsugi/animelist/utils/KitsugiTranslations.kt           (296 ±)
 DEGİS app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiBangumiDetailClient.kt (348 ±)
 DEGİS app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiBangumiClient.kt     (23 ±)
 DEGİS app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiStudioClient.kt      (28 ±)
 DEGİS app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailComponents.kt (23 ±)
 DEGİS app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiDetailTabContents.kt    (37 ±)
 DEGİS app/src/main/java/com/kitsugi/animelist/ui/screens/detail/EntryDetailTabContents.kt   (20 ±)
 DEGİS app/src/main/java/com/kitsugi/animelist/ui/screens/search/SourceEngineFilterSheet.kt  (19 ±)
```
Toplam: **579 ekleme / 215 silme** (8 dosya) + 2 yeni dosya. `res/values*/strings.xml` **bilinçli olarak genişletilmedi** (11 dil dosyasına yüzlerce satır yazmak yerine statik sözlük otorite kabul edildi; `bangumi_tag_*` override'ları hâlâ önceliklidir).

## 4 · Yeni halka açık API yüzeyi

```kotlin
BangumiTagDictionary.entryFor(label): BangumiTagEntry?      // internal
BangumiTagDictionary.knownLabels(): Set<String>             // internal
String.toLocalizedTagLabel(): String
List<String>.localizedDistinctTags(limit = 48): List<String>
String.toLocalizedBangumiTagOrNull(): String?
String.toTurkishBangumiTag(): String                        // ad tarihi; arayüz diline göre çevirir
String.toEnglishBangumiTag(): String
fun bangumiTagEntryOrNull(label: String?): BangumiTagEntry?
fun bangumiTagEnglishOrNull(label: String): String?
fun isEnglish(): Boolean
String.toLatinStudioName(): String                          // normalize + kanonik yazım
BangumiTitleCache.get / .put / .latinTitleFor / .warmUp     // internal object
KitsugiBangumiDetailClient.metaGenres / .pickSynopsis / .normalizeCompanyKey / .splitCompanies  // internal (test edilebilir)
```

## 5 · Doğrulama durumu

- ✅ Parantez/küme/dizge duyarlısı **yapısal denge kontrolü** 11 dosyanın tamamında temiz (`()`/`{}`/`[]` eşleşmesi, iç içelik, ham satır içinde dengelenmemiş desen yok).
- ✅ Sözlük validator'ı: 523 `g(...)` satırı, **yinelenen anahtar yok**, tırnak/parantez dengeli; 2182 etiket yazımı; örnek sorgular (惊悚, 治愈系, 催泪, 游戏改, GAL改, 神作, 京阿尼, 中二病, 世界系, 泡面番, 大正, 恋爱, 推理, 百合, 耽美, 异世界, 职场…) TR + EN karşılıkla dönüyor.
- ✅ Silinen `bangumiTagMap` / `data class BangumiTag` için **teslim çağrısı taraması** temiz; `resolveStudioClickSource` tek yerde tanımlı (Entry dosyasındaki kopya kaldırıldı); `studioLatinNameMap` içinde yinelenen anahtar yok.
- ⚠️ **Derleme yapılamadı**: sandbox'ta `java` / `kotlinc` / `gradle` / Android SDK yok. `./gradlew :app:assembleDebug` (veya Android Studio) ile ilk derlemede çıkabilecek sembol/uyumluluk hataları burada yakalanamaz.

## 6 · Kalan işler / bilinen sınırlar

1. **Cihazda derleme + görsel doğrulama** (öncelik: CLANNAD, Steins;Gate 0, ひぐらしのなく頃に解; TR ve EN arayüzünde).
2. **Soğuk önbellek:** keşfet şeridi, bir kayıt hiç açılmadıysa ilk seferde yine özgün (CJK) adı basabilir; detay/ilişki turu aynı seans içinde önbelleği doldurur, sonraki ziyarette Latin ad gelir. Kalıcı oldugu için zamanla kendiliğinden iyileşir.
   - İstenirse ek adım: şerit verisi çizildikten sonra CJK-only satırlar için **düşük öncelikli, limitli** bir seed turu (şimdilik yapılmadı; 14 şerit × 20 kayıt = kabul edilemez istek sayısı).
3. **Stüdyo kimliği gecikmesi:** `KitsugiStudiosCard` Bangumi kollarını tek kaynaklı önizlemede id'siz çiziyor; id'ler `enrich` turunda geldiği için çip ancak o turdan sonra tıklanabilir.
4. Kişi adı çevirisi **bilinçli yok**: `石原立也`, `龍騎士07` gibi gerçek adlar sözlükte bırakıldığı gibi görünür (kullanıcı tercihi).
5. `bangumi_tag_*` override dosyaları (TR/EN) istenirse genişletilebilir; öncelik sırası zaten sözlüğün üstünde.
