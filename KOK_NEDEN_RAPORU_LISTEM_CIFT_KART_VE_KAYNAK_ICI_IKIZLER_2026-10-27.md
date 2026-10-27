# KÖK NEDEN RAPORU — Listem'de çift kartlar (kaynak içi ikizler + birleşmeyen kopyalar)

**Tarih:** 2026-10-27
**Sürüm:** v2.4.341 (önceki: v2.4.340)
**Ekran:** Listem → **Tümü** ve **platform sekmeleri** (AniList · MAL · Simkl · Kitsu · Shikimori · Bangumi) + Android TV Kütüphanesi
**İlgili geçmiş rapor:** `KOK_NEDEN_RAPORU_LISTEM_TUMU_SAYI_SISMESI_BANGUMI_2026-10-13.md` (o rapor yalnızca **kaynaklar arası** şişmeyi çözdü; bu rapor **kaynak içi** ikizleri ve tür etiketi farkını çözer)

---

## 1. Kullanıcı şikâyeti (ekran görüntüleriyle)

"Tümü" ve platform sekmelerinde aynı yapım **iki kart** olarak görünüyor:

| Ekran görüntüsü | Çift | Kartların rozetleri |
|---|---|---|
| image-1 (Tümü) | **Kaiju No. 8 Season 2** · 6/11 | sol: yalnız Kitsu · sağ: AniList + Bangumi + Simkl + Shikimori + MAL |
| image-2 (Tümü) | **Attack on Titan** · 1/25 | sol: Kitsu · sağ: AniList + Bangumi + Simkl + Shikimori + MAL |
| image-2 (Tümü) | **Kaiju No. 8 Season 2** · 6/11 | aynı desen (iki karta bölünmüş) |
| image-3 (**Simkl** sekmesi) | **Demon Slayer: Kimetsu no Yaiba Infinity Castle** · 1/1 | **iki kart da yalnız Simkl** (aynı kaynak, aynı başlık, aynı puan) |

Kritik fark: image-3'te iki kartın rozeti **aynı** (`Simkl`). Yani bu çift, kaynaklar arası
birleştirme hatası değil: **aynı kaynakta iki satır** var. image-1/2'de ise kaynaklar farklı
ama **birleşme olmamış**.

Bu tek görüntü, iki ayrı kök nedeni işaret ediyor.

---

## 2. Kök neden A — Simkl kategori çakışması (aynı kaynakta iki satır)

`SimklImportManager.fetchAllLists()` üç ucu birleştiriyordu:

```kotlin
val movies = fetchList(token, "movies")
val shows  = fetchList(token, "shows")
val anime  = fetchList(token, "anime")
movies + shows + anime          // ← tekilleştirme YOK
```

Simkl sözleşmesinde **aynı öğe birden fazla kategoride** yer alabilir:

* anime **filmi** → `movies` + `anime`
* anime **dizisi** → `shows` + `anime`

Aynı `simklId` iki kez geliyor, fakat tür etiketi farklı (`Movie` / `Anime` veya
`TvShow` / `Anime`). Yerel eşleştirme `MediaIdentity.sameMedia(...)` **tür eşitliği** istediği
için ikinci kayıt "yeni içerik" sayılıyor ve `smartImport` onu **EKLİYOR**:

```kotlin
val matches = existing.filter { MediaIdentity.sameMedia(it.toDomain(), imported) }
if (matches.isEmpty()) inserts.add(imported)   // ← ikinci satır yazılır
```

Sonuç: Simkl sekmesinde ve "Tümü"nde aynı film/dizi iki kart (image-3'ün tam tanımı).
`Demon Slayer: Infinity Castle` bir anime filmi olduğu için `movies` + `anime` çiftinin
tipik örneğidir; `1/1 bölüm` metni de iki satırda aynıdır.

### Ayrıca: eşzamanlı yazma yarışı

Kitaplığa aynı anda iki yol yazabiliyordu:

1. Açılıştaki arka plan yenilemesi → `replaceSourcePreservingAdultFlags(...)` (**sil + baştan yaz**)
2. Hesap/oturum içe aktarımı → `smartImport(...)` (**önce oku, sonra yaz**)

`smartImport` sil-yeniden-yaz döngüsünün ortasında okuyunca "satır yok" görüp aynı kaydı
ikinci kez ekliyordu. Repository birden fazla yerde **ayrı örneklerle** oluşturulduğu için
nesne düzeyinde bir kilit de yoktu.

---

## 3. Kök neden B — "Tümü" sekmesi bayat kimlik ipuçları yüzünden birleşmiyor

Birleşik kütüphane (`groupMyListEntries`) satırları çapraz kimlik köprüsüyle
(`MyListIdentityCrosswalk`) gruplar. Köprü anahtarları **öğrenilmiş** bilgidir: çapraz kimlik
önbelleği + `bgm_a_<malId> → subject_id` tablosu.

Eski kod bu ipuçlarını **çakışma denetimine de** sokuyordu:

```kotlin
MediaIdentity.conflictingIdentityKeys(typeA, typeB, keysA = existing.keys, keysB = identity.keys)
//                                                          ^^^^^^^^^^^^ ipuçları dahil
```

Bir satırın önbelleğinde kalan **bayat** bir `Anime:shikimori:` / `Anime:mal:` değeri, karşı
gruptaki üyenin **gerçek** kimliğiyle uyuşmadığı anda "kimlik çakışması" doğuruyor ve
birleşmeyi **engelliyordu**. Kullanıcının gördüğü tablo tam olarak buydu: bir kart tek
kaynağa (Kitsu) sıkışmış, diğer kart beş kaynağı toplamış — aynı yapım iki kart.

---

## 4. Düzeltmeler

### 4.1 `ProviderImportDedupe` (yeni) — `data/local/ProviderImportDedupe.kt`

İçe aktarım listesi yazılmadan **önce** aynı kaynağın kanıtlanmış kimlikle aynı yapımı
gösteren kayıtlarını tek satıra indirir:

* anahtar: **aynı kaynak + aynı kimlik kanıtı** (`simklId`, Bangumi `subject_id`, aynı
  uzayda aynı ID). **Başlık benzerliği kanıt sayılmaz** (`allowTitle = false`).
* çakışan kopyada **Anime** etiketi kazanır (bölüm ilerlemesi + çapraz eşitleme anime ekseninde).
* seçilen kayıt diğerinin eksik alanlarını devralır (afiş, özet, `year`, `total`, ilerleme,
  favori, +18, notlar) — hiçbir kullanıcı verisi gerilemez.

### 4.2 `SimklSyncContract.mergeCategories()` (yeni)

Simkl'in üç kategori listesini `simklId` (yoksa başlık + yıl) üzerinden tekilleştirerek
birleştirir; `SimklImportManager.fetchAllLists()` artık bunu kullanır.

### 4.3 `MediaIdentity.sameProduction()` + `crossTypeProof()` (yeni)

`sameMedia` tür eşitliği ister. Aynı yapımın farklı tür etiketli kopyaları için:

* türler **aynıysa** mevcut `sameMedia` sözleşmesi aynen işler,
* türler **farklıysa** yalnızca **kanıtlanmış ortak kimlik** birleştirir,
* kanıt uzayları bilinçli olarak dar: **`simkl`** ve **`bangumi`** (tek ID uzayı).
  **`tmdb` hariç** — TMDB'de film 123 ile dizi 123 alakasız yapımlardır.
* **Anime ↔ Manga asla** birleşmez (MAL anime 52991 ≠ MAL manga 52991).

### 4.3b `MediaIdentity.titleMatch` — ±1 yıl toleransı

AniList **sezon** yılını, Kitsu/MAL **prömiyer** yılını saklar. Aralık/Ocak sınırındaki bir
yapım iki kaynakta 1 yıl farklı göründüğü için başlık yedeği tutmuyor ve aynı yapım iki kart
kalıyordu. Başlık karşılaştırması artık ±1 yıl toleranslı; **gerçek remake** (ör. Hunter x
Hunter 1999 / 2011) ayrı kalır. Bu, "listeye ekle → zaten listende var" yolunun
(`KitsugiModels.matchesTitleVariant`) uzun süredir kullandığı kuralın aynısıdır; iki yol
artık aynı şeyi söyler.

### 4.4 `groupMyListEntries(..., collapseAcrossSources)` — yeniden yazıldı

* **Çakışma yalnızca satırın KENDİ kimlikleriyle** hesaplanır (`ownKeys`); köprü ipuçları
  yalnızca **aday bulma** ve **olumlu kanıt** olarak kullanılır → bayat ipucu artık
  birleşmeyi bölmez.
* Tür etiketinden bağımsız `xid:` aday kovaları (`simkl`, `bangumi`) eklendi; böylece
  `Movie` ↔ `Anime` kopyaları ilk turda yan yana gelebiliyor.
* Temsilci seçimine **tür önceliği** eklendi (Anime > TvShow > Movie > Manga) — yalnızca
  aynı kaynağın iki kopyası yarıştığında devreye girer.
* `collapseAcrossSources = false` ile aynı motor **platform sekmelerinde** de kullanılır:
  bir sekmede yalnızca o kaynağın kopyaları erir, başka kaynağın satırı sekmede **asla**
  belirmez.

### 4.5 `MyListDuplicateRepair` (yeni) — var olan çiftlerin onarımı

Sürüm damgalı, bir kez koşan DB onarımı:

* aynı kaynak + **kanıtlanmış kimlik** (başlık benzerliği yok) → satırlar birleştirilir,
* kullanıcı verisi toplanır: ilerleme (maks.), durum (en ileri), puan, favori, +18, notlar,
  tarihler, kimlik alanları; tür etiketi Anime'ye normalize edilir,
* silinen kopyaların **bekleyen senkron işleri** düşürülür (bayat UPDATE kopyayı diriltmesin),
* yazma `LibraryWriteLocks` altında yapılır (eşzamanlı içe aktarma ile yarışmaz).

### 4.6 `LibraryWriteLocks` (yeni) — kaynak başına yazma kilidi

`smartImport` (oku → yaz) ile `replaceSourcePreservingAdultFlags` (sil → yaz) aynı kaynak
için sıraya girer. Kilit **nesne düzeyinde** tutulur; farklı kaynaklar birbirini beklemez.

### 4.7 Ekran katmanı

| Dosya | Değişiklik |
|---|---|
| `MyListScreen.kt` | `CombinedMyListLibrary` artık `sourceDedupedEntries` taşıyor; platform sekmeleri, arama, filtre/sıralama ve **sekme sayaçları** bu listeden besleniyor — sayaç ile görünen kart sayısı eşleşir. |
| `MyListComponents.kt` | `MyListTabBar` sayaçları `allEntries` parametresinden hesaplar; çağrı yeri artık eritilmiş listeyi geçer (sayaç = görünen kart). |
| `TvLibraryScreen.kt` | Android TV kütüphanesi aynı kaynak-içi eritmeyi kullanır (kartlar + istatistik rozetleri). |

---

## 5. Doğrulama (bu ortamda Android SDK/JDK yok)

`MediaIdentity` + `groupMyListEntries` Python'a **birebir** port edilip senaryolar ölçüldü:

| Senaryo | Girdi | Sonuç |
|---|---|---|
| Simkl film kopyası (Tümü) | Movie + Anime, aynı `simklId` | **1 kart** (temsilci Anime) |
| Simkl film kopyası (Simkl sekmesi) | aynı | **1 kart** |
| Attack on Titan (4 anime kaynağı + bayat `shikimori:` ipucu) | 5 satır | **1 kart** (öncesi: 2) |
| Bayat `mal:`/`kitsu:` ipucu | AniList + MAL + Simkl | **1 kart**, rozetler `anilist, mal, simkl` |
| Aynı başlık, farklı `simklId` | 2 satır | **2 kart** (korunur) |
| Anime vs Manga (aynı MAL ID) | 2 satır | **2 kart** (korunur) |
| Remake (aynı başlık, 1999 vs 2011) | 2 satır | **2 kart** (korunur) |
| Aynı başlık, ±1 yıl fark (sezon vs prömiyer yılı) | AniList 2025 + Kitsu 2026 | **1 kart** |
| Kaynak sekmesi eritmesi | Simkl(2) + AniList | **2 kart**, her biri tek kaynak |

Ek olarak değişen tüm Kotlin dosyaları parantez/blok dengesi açısından tarandı (0 hata).
Yeni birim testleri (JUnit4) eklendi:

* `MediaProductionIdentityTest` (7 test) — tür etiketi farkı, TMDB istisnası, Anime↔Manga yasağı
* `MyListLibraryGroupingTest` (+4 test) — Simkl kopyası, kaynak-içi eritme, bayat ipucu, Bangumi
* `ProviderImportDedupeTest` (6 test) — kategori çakışması, veri kaybı yok
* `MyListDuplicateRepairTest` (5 test) — onarım güvenliği, veri toplama

## 6. Değişen dosyalar

```
app/src/main/java/com/kitsugi/animelist/model/MediaIdentity.kt                 (+sameProduction, crossTypeProof, preferredTypeRank)
app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListLibraryGrouping.kt  (yeniden yazıldı)
app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListScreen.kt       (kaynak-içi eritilmiş liste)
app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListComponents.kt   (sayaçlar)
app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListViewModel.kt    (onarım çağrısı)
app/src/main/java/com/kitsugi/animelist/data/local/ProviderImportDedupe.kt      (YENİ)
app/src/main/java/com/kitsugi/animelist/data/local/MyListDuplicateRepair.kt     (YENİ)
app/src/main/java/com/kitsugi/animelist/data/local/LibraryWriteLocks.kt         (YENİ)
app/src/main/java/com/kitsugi/animelist/data/local/MediaEntryRepository.kt      (kilit + tekilleştirme + sameProduction)
app/src/main/java/com/kitsugi/animelist/data/remote/SimklSyncContract.kt        (+mergeCategories)
app/src/main/java/com/kitsugi/animelist/data/auth/SimklImportManager.kt         (kategori birleştirme)
app/src/main/java/com/kitsugi/animelist/ui/tv/library/TvLibraryScreen.kt        (TV kütüphanesi)
```
