# PLAN / TASK — Simkl Puanları, Bangumi Hız & Dil Uyumu ve Ortak Çapraz Kaynak Videolar Sekmesi (`v2.4.341`)

**Tarih:** 2026-10-23  
**Dal:** `arena/65294e01-kitsugi-beta`  
**Taban Commit:** `26bb0444028e1209ffa7cf64d9c270a330471363` (`main` / `v2.4.340`)  
**Mimari Kısıt Uyumluluğu:** `+0 XML <string> ID` (`TR 58466 · EN 58466` simetrisi %100 korundu), R8 & ART `<= 255` yazmaç sınırı gözetildi.

---

## 1. Kapsam & Kullanıcı Talepleri

1. **Simkl Puan Sorunu (`Arama`, `Keşfet`, `Tümünü Gör`):**
   - Arama ekranındaki Simkl şeridinde (`— · Simkl #196710`), Keşfet vitrin/şeritlerinde ve Tümünü Gör sayfalarında puanların görünmemesi.
2. **Bangumi 5 Dakikalık Kilitlenme / Döngü Hisleri & Seçili Dil (`ENGLISH` / `ROMAJI` / `NATIVE`) Koruması:**
   - Başlık dili İngilizce (`ENGLISH`) seçildiğinden beri Bangumi ekranlarında verilerin dakikalarca gelmemesi, döngüye girmiş gibi takılı kalması; **bunu çözerken seçilen başlık ve karakter/ekip dili uyumunun Bangumi'nin hiçbir yerinde kesinlikle bozulmaması**.
3. **Detay Sayfası — Tüm Kaynaklar Arası Ortak "Videolar" Sekmesi:**
   - `Videolar` sekmesinin yalnızca tek bir fragman satırı göstermek yerine tüm kaynaklar (`MyAnimeList / Jikan`, `AnimeThemes`, `TMDB`, `AniList`, `Shikimori`, `Simkl`, `Kitsu` ve eklenti yedekleri) arasında ortak çalışarak fragmanları, PV'leri, OP/ED tema videolarını, resmi müzik videolarını ve özel/kamera arkası klipleri tek sekmede birleştirmesi.

---

## 2. Kök Neden Analizi

### 2.1 Simkl Puanlarının Görünmemesi
| # | Kök Neden | Etkisi |
|---|---|---|
| 1 | `SimklApiClient` içindeki `searchType`, `getBestMedia`, `getExploreGenrePage`, `getPremieresPage`, `getAiringMedia`, `searchAdvanced`, `getDvdMovies` uçlarında `extended=full` parametresi eksikti | Simkl API `extended=full` olmadan yalnızca özet nesne (`title`, `year`, `poster`, `ids`) döndürür; `ratings` alanı yanıt gövdesinde hiç gelmez (`— · Simkl #196710`) |
| 2 | `parseBestMediaArray` içinde `ratings?.optJSONObject("simkl")?.optDouble("rating", 0.0) ?: ratings?.optJSONObject("mal")...` yazılıydı | Kotlin'de `optDouble("rating", 0.0)` `null` değil `0.0` döndürdüğü için Elvis (`?:`) operatörü `mal` puanına **hiçbir zaman** geçmiyordu |
| 3 | `parseSimklArray` ve `searchType` yalnızca `simkl` ve `imdb` alanlarına bakıyor, `mal` puanını hiç okumuyordu | Anime kayıtlarında `imdb` bulunmadığı için `ratings.mal.rating` dolu olsa bile puan `null` kalıyordu |
| 4 | Tek parametreli `search(query)`, `getDvdMovies`, `getUserWatchlist` metodlarında `score = null` sabitlenmişti ve `rawScoreDouble` hiç doldurulmuyordu | Liste görünümündeki `KitsugiRankingMediaCard` ondalık puan çizemiyor; `STARS`/`POINT_5` formatlarında 0–100 ölçeği risk oluşturuyordu |
| 5 | `MediaTitleResolver.VERSION = 3` anahtarıyla Room `explore_cache` tablosuna puansız Simkl verisi yazılmıştı | Önbellek temizlenmedikçe eski puansız kartlar gösterilmeye devam ediyordu |

### 2.2 Bangumi 5 Dakikalık Kilitlenme / Sonsuz Döngü
| # | Kök Neden | Etkisi |
|---|---|---|
| 1 | **Semafor Kilitlenmesi (Deadlock):** `BangumiSubjectTitleResolver.runInfoboxPass` dışarıda `infoboxSemaphore.withPermit` alıp içeride tekrar `infoboxSemaphore.withPermit` alan `loadLocalizedSubject`'i çağırıyordu | `Semaphore(8)` yeniden-girişli (reentrant) olmadığı için ≥ 8 kayıt geldiği anda **8 iznin tamamı kilitleniyor**, hiçbir istek çıkmadan her dilim 8 sn zaman aşımına kadar tamamen donuyordu |
| 2 | **Sonsuz Yeniden Sorgulama Döngüsü (`english == romaji`):** `hasReliableEnglish` fonksiyonu `!english.equals(romaji, ignoreCase = true)` şartı arıyor ve `applyLocalizedBangumiTitles` eşit olan `english` alanını `null`'a siliyordu | `Jujutsu Kaisen`, `One Piece`, `Chainsaw Man`, `Death Note`, `Violet Evergarden` gibi İngilizce ve Romaji adı aynı olan yüzlerce yapım **hiçbir zaman çözülmüş sayılmıyor**, her ekran açılışında ve her kütüphane taramasında sonsuza dek yeniden sorgulanıyordu |
| 3 | **AniList Slot Saatinin Dakikalarca İleri Fırlaması:** `KitsugiApiBase.reserveAniListSlotMs()` bekleme başlamadan önce `nextAniListSlotAtMs`'yi ileri sarıyor, zaman aşımıyla iptal olan korutinler slotunu iade etmiyordu | 14 Keşfet şeridi + kütüphane taraması aynı anda çalıştığında `nextAniListSlotAtMs` **3–5 dakika ileriye fırlıyor**; vitrin görselleri ve başlık istekleri 5 dakika boyunca kilitleniyordu |
| 4 | **Keşfet Şeritlerinde Senkron Bloklama + `CancellationException` Yutulması:** `loadBangumiData()` 14 şeridin tamamını `.withLatinBangumiNames()` bitene kadar bekletiyor; 15 sn dolduğunda `KitsugiBangumiClient`'taki `runCatching` `TimeoutCancellationException`'ı yutup indirilmiş tüm şerit verisini `emptyList()`'e çeviriyordu | Bangumi Keşfet ekranı boş kalıyor, vitrin iskeleti (shimmer) dakikalarca dönüyordu |
| 5 | **`next.bgm.tv/p1` Yedeksiz Çağrıları & `SystemClock.elapsedRealtime()`:** `fetchRelations`, `fetchEpisodes`, `fetchCharacterDetail`, `fetchStaffDetail` p1 takıldığında v0 yedeğine geçmiyordu | `next.bgm.tv` yavaşladığında sekmeler ve karakter/ekip sayfaları geç açılıyor veya boş dönüyordu |

### 2.3 Videolar Sekmesinin Tek Kaynakla Sınırlı Kalması
- `Videolar` sekmesi (`KitsugiMediaVideosTab`) yalnızca birincil detay nesnesindeki tekil `trailerUrl`, `openings` ve `endings` alanlarını okuyordu; ayrıca `MediaEntryDetailViewModel` ("Listem"den açılan kayıtlar) `apiClient.enrichDetail(...)` adımını hiç çağırmadığı için Bangumi/Simkl kayıtlarında OP/ED listeleri tamamen boş kalıyordu.

---

## 3. Yapılan Düzenlemeler

### 3.1 Simkl Puan Sistemi (`SimklApiClient.kt`, `KitsugiSimklDetailClient.kt`, `PreferenceHelpers.kt`, Kart Bileşenleri)
- [x] Tüm Simkl okuma/arama/keşfet/tür/prömiyer/yayın/DVD uçlarına `extended=full` eklendi.
- [x] Ortak `extractSimklScoreInfo(obj, entryObj)` yazıldı: `simkl → mal → imdb → tmdb → anilist → trakt` sırasıyla 0'dan büyük ilk geçerli puanı, toplam oy sayısını (`members`), sıralamayı (`rank`) ve bölüm sayısını (`total`) çıkarır.
- [x] `simklScoreCache` + paralel `hydrateSimklScores(items)` eklendi: herhangi bir uçta `ratings` alanı eksik gelen kayıtlar `KitsugiSimklDetailClient.fetchSimklRawJson` üzerinden paralel tamamlanıp önbelleğe alınır.
- [x] `PreferenceHelpers.formatScore`, `KitsugiExploreMediaCard`, `KitsugiRankingMediaCard` ve `SearchResultRow` hem 0–10 hem 0–100 ölçeğini ve listede henüz kullanıcının puanlamadığı içeriklerin topluluk puanı yedeğini tam destekleyecek şekilde güncellendi.
- [x] `MediaTitleResolver.VERSION` `3 → 4` yapılarak eski puansız Room keşfet önbellekleri geçersiz kılındı.

### 3.2 Bangumi Hız, Kilitlenme Çözümü ve %100 Dil Uyumu
- [x] `BangumiSubjectTitleResolver.runInfoboxPass` içindeki çift `infoboxSemaphore.withPermit` kilitlenmesi (deadlock) giderildi; `ANILIST_BATCH_SIZE` `10 → 25` yapıldı.
- [x] `BangumiTitleCache.LatinTitles` modeline kalıcı `englishVerified: Boolean` (`"ev"`) bayrağı eklendi; İngilizce ve Romaji adı aynı olan yapımlar (`Jujutsu Kaisen`, `One Piece` vb.) ile hem AniList hem Bangumi infobox kontrolü tamamlanmış yapımlar kalıcı önbelleğe yazılıp sonsuz döngü tamamen kapatıldı.
- [x] `KitsugiApiBase.executeAniListQuery` hız sınırlayıcısı iptal edilebilir jeton kovasına (`awaitAniListRatePermit`) dönüştürüldü; iptal edilen istekler kredi tüketmez, arka plan taramaları etkileşimli isteklere her zaman jeton bırakır.
- [x] `ExploreViewModel.loadBangumiData()` iki aşamalı (progressive) hale getirildi: 14 şerit `cacheOnlyTitles = true` ile **~1–2 sn içinde anında ekrana çizilir**, hemen ardından `launchBangumiExploreTitleHydration` tüm benzersiz yapımların İngilizce/Romaji başlıklarını çözüp ekranı ve Room önbelleğini canlı günceller.
- [x] `FullScreenMediaGridPage` ("Tümünü Gör") için de aşamalı arka plan başlık zenginleştirmesi eklendi.
- [x] `KitsugiBangumiClient` genelinde `CancellationException` yutulması düzeltildi; karakter ve kişi aramalarına (`searchCharacters`, `searchPeople`) Latin ad zenginleştirmesi eklendi.
- [x] `KitsugiBangumiDetailClient` içinde `fetchRelations`, `fetchEpisodes`, `fetchCharacterDetail` ve `fetchStaffDetail` metodlarına tam `/v0/...` yedekleri ve `BangumiTitleCache` anlık okuması eklendi; `needsLatinTitle` seçili İngilizce dilini tam karşılayacak şekilde güncellendi.

### 3.3 Ortak Çapraz Kaynak "Videolar" Sekmesi (`KitsugiCrossSourceVideoAggregator.kt`, `KitsugiMediaVideosTab`)
- [x] Yeni `KitsugiCrossSourceVideoAggregator` yazıldı: çapraz kimlikleri (`MAL`, `AniList`, `TMDB`, `Kitsu`, `Simkl`, `Bangumi`, `Shikimori`) çözüp **8 kaynaktan paralel** video toplar:
  1. Birincil `KitsugiMediaDetail`
  2. **MyAnimeList / Jikan** (`/v4/anime/{malId}/videos` → tüm PV tanıtımları + resmi müzik videoları)
  3. **AnimeThemes** (`api.animethemes.moe` → doğrudan oynatılabilir HD OP/ED videoları)
  4. **TMDB** (`include_video_language=tr,en,ja,ko,zh,null` ve başlık doğrulaması ile Türkçe öncelikli Fragman, Teaser, Kamera Arkası, Özel Yapım, Klip)
  5. **AniList** (`trailer` YouTube / Dailymotion + küçük resim)
  6. **Shikimori** (`/api/animes/{id}/videos` → PV, Karakter Tanıtımı, OP, ED, CM, Klip)
  7. **Simkl** (`trailers[]` dizisi — `KitsugiSimklDetailClient.extractSimklTrailers`)
  8. **Kitsu** (`youtubeVideoId`) + **Cloudstream Eklenti Yedeği** (`DetailTrailerFallback`)
- [x] Akıllı tekilleştirme (`mergeCrossSourceVideos`): aynı YouTube ID'sine veya aynı OP/ED şarkısına sahip kayıtları kopya satır oluşturmadan birleştirir, tüm platform rozetlerini (`allSources`) aynı satırda gösterir ve düz metin OP/ED satırlarını oynatılabilir `AnimeThemes` HD akışıyla yükseltir.
- [x] Hem `ApiResultDetailViewModel` hem `MediaEntryDetailViewModel` ("Listede" kayıtlar dahil) çapraz kaynak video toplayıcısına bağlandı; `MediaEntryDetailViewModel`'e eksik olan `apiClient.enrichDetail(...)` adımı da eklendi.
- [x] `KitsugiMediaVideosTab` yenilendi: üstte platform logolu dinamik **Kaynak Filtre Çipleri** (`Tümü`, `MyAnimeList`, `AnimeThemes`, `TMDB`, `Shikimori`, `Simkl`, `AniList`, `Kitsu` + canlı tarama göstergesi), kategorize edilmiş kartlar (`Fragmanlar ve Tanıtımlar`, `Açılış Müzikleri`, `Kapanış Müzikleri`, `Müzik Videoları`, `Özel Videolar ve Kamera Arkası`), 16:9 küçük resim önizlemeleri, platform rozetleri ve uygulama içi oynatıcı.

---

## 4. Arşivdeki Dosyalar (`30` Dosya)

### Yeni Eklenen Dosyalar (`7`)
- `PLAN_TASK_SIMKL_PUAN_BANGUMI_HIZ_DIL_ORTAK_VIDEOLAR_2026-10-23.md` *(bu plan ve görev belgesi)*
- `release_notes_v2.4.341.md`
- `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiCrossSourceVideoAggregator.kt`
- `app/src/test/java/com/kitsugi/animelist/data/remote/SimklScoreAndFormattingTest.kt`
- `app/src/test/java/com/kitsugi/animelist/data/remote/BangumiTitleVerificationAndPerfTest.kt`
- `app/src/test/java/com/kitsugi/animelist/data/remote/CrossSourceVideoAggregatorTest.kt`
- `app/src/test/java/com/kitsugi/animelist/data/remote/LiveThreeFixesVerificationTest.kt`

### Güncellenen Dosyalar (`23`)
- `.github/workflows/android-build.yml`
- `app/build.gradle.kts`
- `app/src/main/java/com/kitsugi/animelist/data/auth/BangumiApiClient.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/BangumiSubjectTitleResolver.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/BangumiTitleCache.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiApiBase.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiBangumiClient.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiBangumiDetailClient.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiModels.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiSimklDetailClient.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/SimklApiClient.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiExploreMediaCard.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiRankingMediaCard.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailPage.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailViewModel.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailThemesTrailerComponents.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/MediaEntryDetailPage.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/MediaEntryDetailViewModel.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/explore/ExploreViewModel.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/FullScreenMediaGridPage.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchResultRow.kt`
- `app/src/main/java/com/kitsugi/animelist/utils/MediaTitleResolver.kt`
- `app/src/main/java/com/kitsugi/animelist/utils/PreferenceHelpers.kt`

---

## 5. Projeye Uygulama & Yerel Test Komutları

ZIP arşivini doğrudan `Kitsugi-Beta` kök dizinine açın:

```bash
unzip -o Kitsugi_v2.4.341_Simkl_Bangumi_UnifiedVideos_2026-10-23.zip -d .
```

Ardından yerel birim + canlı doğrulama testleri ve APK derlemesi:

```bash
./gradlew :app:testFossDebugUnitTest \
  --tests 'com.kitsugi.animelist.data.remote.SimklExploreParsingTest' \
  --tests 'com.kitsugi.animelist.data.remote.SimklScoreAndFormattingTest' \
  --tests 'com.kitsugi.animelist.data.remote.BangumiSubjectTitleResolverTest' \
  --tests 'com.kitsugi.animelist.data.remote.BangumiTitleCacheTest' \
  --tests 'com.kitsugi.animelist.data.remote.BangumiLibraryTitleHydratorTest' \
  --tests 'com.kitsugi.animelist.data.remote.KitsugiBangumiClientTitleTest' \
  --tests 'com.kitsugi.animelist.data.remote.BangumiTitleVerificationAndPerfTest' \
  --tests 'com.kitsugi.animelist.data.remote.CrossSourceVideoAggregatorTest' \
  --tests 'com.kitsugi.animelist.data.remote.LiveThreeFixesVerificationTest' \
  :app:assembleFossDebug
```
