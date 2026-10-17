# 📋 PLAN / TASK — Tüm Kaynaklarda Etiketler (Tags) ve Türler (Genres) Spoiler Mantığı Eşitlemesi

**Tarih:** 2026-10-17  
**Dal:** `arena/da1693a5-kitsugi-beta` · **Taban commit:** `4f2f3713b2f28fefef53244e23d24860f7497a06`  
**Durum:** TAMAMLANDI ✅ (20 dosya doğrulandı, birim testleri eklendi)

---

## 1 · Kullanıcı Talebi ve Hedef

Uygulama içindeki tüm desteklenen kaynakların (**AniList, Bangumi, MyAnimeList / Jikan, Shikimori, Kitsu, Simkl, TMDB, TVMaze**) detay sayfalarında yer alan **Etiketler (Tags)** ve **Türler (Genres)** bölümlerinde tıpkı AniList'te olduğu gibi iki katmanlı spoiler mantığına (doğal/genel spoiler + yapıma özel spoiler) uyması, varsayılan olarak spoiler etiket/türleri gizlemesi ve **"Spoiler'ları Göster / Spoiler'ları Gizle"** anahtarıyla kırmızı vurguyla göstermesi.

---

## 2 · Tespit Edilen Kök Nedenler

| # | Kaynak / Katman | Kök Neden |
|---|---|---|
| 1 | `KitsugiAniListDetailClient.kt` | GraphQL sorgusu yalnızca `tags { name rank isMediaSpoiler }` çekiyor, AniList'in genel spoiler bayrağı olan `isGeneralSpoiler` alanını hiç okumuyordu. |
| 2 | `KitsugiBangumiDetailClient.kt` | `buildNativeDetail` içinde tüm Bangumi etiketleri sabit `isSpoiler = false` ile üretiliyordu. `fetchCompanionDetail` içinde `cross.malId` bulunduğunda yalnızca MAL çekildiği için (`tags = emptyList()`) `cross.aniListId` bilinse dahi AniList spoiler etiketleri alınmıyordu. Ayrıca `mergeDetail` içinde `(base.tags + other.tags)` birleştirmesi ilk kaydı (`isSpoiler = false, rank = null`) tutup AniList'in `isSpoiler = true` ve `rank` bilgisini düşürüyordu. |
| 3 | `KitsuClient.kt` & `KitsuExploreClient.kt` | Kitsu anime ve manga detay uçlarına `?include=categories` gönderilmediği için Kitsu kayıtlarında `genres` ve `tags` boş dönüyordu. |
| 4 | `TmdbMediaDetailClient.kt` | `append_to_response` parametresinde `keywords` istenmediği için TMDB anahtar kelimeleri (`tags`) çekilmiyordu. |
| 5 | `KitsugiMalDetailClient.kt`, `KitsugiShikimoriClient.kt`, `KitsugiSimklDetailClient.kt`, `TvMazeClient.kt` | Alt temalar (`themes`) ve demografiler yalnızca `genres` listesine ekleniyor, `tags` alanı boş bırakılıyordu ve doğal spoiler kontrolü yapılmıyordu. |
| 6 | `KitsugiDetailClient.kt` & `ApiResultDetailViewModel.kt` | `enrichDetail` aşamasında Manga kayıtları erken çıkıyordu (`mediaType == MediaType.Manga`) ve AniList dışındaki kaynaklar için AniList'in yapıma özel spoiler etiketleri çekilip birleştirilmiyordu. |
| 7 | `ApiGenresChipRow` & `EntryGenresChipRow` | **Türler** kartında hiçbir spoiler denetimi veya "Spoiler'ları Göster / Gizle" anahtarı yoktu; `genres` içine karışan *Trajedi (Tragedy)*, *Zaman Döngüsü (Time Loop)* veya yapıma özel spoiler temalar doğrudan görünüyordu. |
| 8 | `KitsugiTagsCard` (`KitsugiDetailComponents.kt`) | `distinctByLocalizedLabel()` aynı çeviriye sahip etiketlerden ilkini tutup ikincideki `isSpoiler = true` ve `rank` bilgisini kaybediyordu. Ayrıca kart daraltılmış (`!expanded`, ilk 10 etiket) durumdayken "Spoiler'ları Göster" tıklandığında 10. sıradan sonraki spoiler etiketler ekranda görünmüyordu. |

---

## 3 · Yapılan Değişiklikler

### 3.1 · Ortak Çok Dilli Spoiler Motoru (`utils/KitsugiSpoilerRules.kt` — YENİ)
- **`isIntrinsicSpoiler(label)`:** `BangumiTagDictionary` (907 grup, CJK/EN/TR), `SearchTranslation` ve çok dilli anahtar kümesi üzerinden doğal spoiler etiket/temaları (*Death/Ölüm/便当*, *Suicide/İntihar/自杀*, *Betrayal/İhanet/背叛*, *NTR/寝取られ*, *Plot Twist/Sürpriz Gelişme/神展开*, *Time Loop/Zaman Döngüsü/轮回*, *Amnesia/Hafıza Kaybı/失忆*, *Tragedy/Trajedi/悲剧*, *Bad Ending/Kötü Son/全灭*, *Depressing/致郁/胃痛* vb.) tespit eder.
- **`isGenreSpoiler(genre, mediaTags)`:** Temel ana türleri (*Aksiyon, Komedi, Dram, Romantik, Fantezi, Bilim Kurgu* vb.) koruma altına alarak asla gizlemez; `genres` listesine eklenmiş alt temaların doğal spoiler olup olmadığını veya o yapımın `mediaTags` listesinde spoiler olarak işaretlenip işaretlenmediğini denetler.
- **`normalizeAndMergeTags(tags)` & `mergeTagsPreservingSpoilers(primary, secondary, limit)`:** Birincil kaynağın özgün etiket adını ve `source` alanını korurken, eşleşen AniList etiketlerinden `isSpoiler = true` ve `rank` (`%`) bilgilerini aktarır; `limit` uygulanırken spoiler etiketlerin kesilmemesini garanti eder.

### 3.2 · Veri Katmanı ve Tüm Kaynak İstemcileri
- **`KitsugiAniListDetailClient.kt`:** Sorguya `isGeneralSpoiler`, `id`, `description`, `category` eklendi; diğer kaynakların tek hafif sorguyla AniList etiketlerini ve `nextAiringEpisode` bilgisini çekebilmesi için bellek önbellekli `fetchTagsAndAiringInfo(...)` eklendi.
- **`KitsugiBangumiDetailClient.kt`:** Yerel etiketlere doğal spoiler denetimi eklendi; `fetchCompanionDetail` içinde MAL çekilse bile AniList'ten spoiler etiketleri ve `rank` bilgileri alınıp `mergeTagsPreservingSpoilers` ile birleştirildi.
- **`KitsugiMalDetailClient.kt`, `KitsugiShikimoriClient.kt`, `KitsugiSimklDetailClient.kt`, `TvMazeClient.kt`:** Alt tema ve demografi etiketleri spoiler denetimli `KitsugiTag` olarak `tags` alanına eklendi.
- **`KitsuClient.kt` & `KitsuExploreClient.kt`:** Anime ve manga detay çağrılarına `?include=categories` eklendi; `parseKitsuCategories` ile `genres` ve spoiler denetimli `tags` ayrıştırıldı.
- **`TmdbMediaDetailClient.kt`:** `append_to_response=alternative_titles,keywords` ile film/dizi anahtar kelimeleri (`keywords`) spoiler denetimli `tags` olarak ayrıştırıldı.
- **`KitsugiDetailClient.kt` & `ApiResultDetailViewModel.kt`:** `enrichDetail` tüm kaynaklar ve medya türleri (Manga dahil) için AniList spoiler etiketlerini birleştiriyor; Room önbellek anahtarına `_sp1` sürüm eki eklendi.

### 3.3 · Arayüz Bileşenleri
- **`KitsugiDetailComponents.kt` (`KitsugiGenresCard` & `KitsugiTagsCard`):**
  - Ortak `KitsugiGenresCard` bileşeni eklendi; Türler arasında spoiler varsa başlıkta `🔒 Spoiler'ları Göster / Spoiler'ları Gizle` anahtarı gösterilir ve spoiler türler açıldığında kırmızı çip olarak çizilir.
  - `KitsugiTagsCard` içinde kart daraltılmış (`!expanded`) durumdayken bile "Spoiler'ları Göster" açıldığında tüm spoiler etiketlerin hemen görünmesi sağlandı.
- **`ApiResultDetailComponents.kt`, `KitsugiDetailInfoSection.kt`, `ApiDetailTabContents.kt`, `EntryDetailTabContents.kt`:** `ApiGenresChipRow` ve `EntryGenresChipRow`, `detail.tags` listesini `KitsugiGenresCard`'a iletecek şekilde güncellendi.
- **`TvDetailScreen.kt` & `KitsugiAddonDetailDialog.kt`:** TV vitrin şeridinde ve eklenti özet diyaloğunda spoiler tür/etiketlerin sızması engellendi.

---

## 4 · Değişen ve Eklenen Dosyalar (20 Dosya)

1. `app/src/main/java/com/kitsugi/animelist/utils/KitsugiSpoilerRules.kt` *(YENİ)*
2. `app/src/test/java/com/kitsugi/animelist/utils/KitsugiSpoilerRulesTest.kt` *(YENİ)*
3. `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiAniListDetailClient.kt`
4. `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiBangumiDetailClient.kt`
5. `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiDetailClient.kt`
6. `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiMalDetailClient.kt`
7. `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiShikimoriClient.kt`
8. `app/src/main/java/com/kitsugi/animelist/data/remote/KitsuClient.kt`
9. `app/src/main/java/com/kitsugi/animelist/data/remote/KitsuExploreClient.kt`
10. `app/src/main/java/com/kitsugi/animelist/data/remote/TmdbMediaDetailClient.kt`
11. `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiSimklDetailClient.kt`
12. `app/src/main/java/com/kitsugi/animelist/data/remote/TvMazeClient.kt`
13. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailViewModel.kt`
14. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailComponents.kt`
15. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailComponents.kt`
16. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailInfoSection.kt`
17. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiDetailTabContents.kt`
18. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/EntryDetailTabContents.kt`
19. `app/src/main/java/com/kitsugi/animelist/ui/tv/detail/TvDetailScreen.kt`
20. `app/src/main/java/com/kitsugi/animelist/ui/screens/search/components/KitsugiAddonDetailDialog.kt`
