# PLAN/TASK — Arama sayfası: yükleme animasyonu tutarlılığı, alakalılık ve "onaysız arama" düzeltmesi

Tarih: 2026-10-09
Durum: Kod düzeltmeleri ve birim testleri tamamlandı; bu ortamda JDK/Android SDK olmadığı için Gradle derlemesi ve cihaz testi çalıştırılamadı.

## Bildirilen hatalar
1. **Yükleme animasyonu tutarsızlığı:** "Tümü" aramasında toplu içerik akarken, verisi
   gelmiş rafların üstünde animasyon (shimmer) görünmeye devam ediyordu.
2. **Yanlış/alakasız veri:** Bazı kaynaklardan gelmesi gereken veriler gelmiyor,
   ilgisiz kayıtlar listeleniyordu.
3. **Onaysız arama:** Arama butonuna veya klavyedeki "Ara" onay tuşuna basmadan,
   yazarken kendiliğinden (yarım/alakasız kelimelerle) arama tetikleniyordu.

## Teşhis (kod düzeyinde kök nedenler)

### 1 — Animasyon
- `SearchScreen`, `uiState.isLoading` boyunca "Tümü" sekmesinin ÜSTÜNE 5 sabit
  `MultiSearchShelfShimmer` rafı çiziyordu. Oysa her raf zaten kendi yükleme
  bayrağını (`multiResults.isLoading*`) taşıyor ve `MultiSearchSection` verisi
  gelene kadar kendi shimmer'ını gösteriyor. Sonuç: kaynaklar tek tek
  tamamlandıkça gelmiş veriler bu placeholder'ların ALTINDA birikiyor, yani verisi
  gelmiş rafın animasyonu sürüyordu. Placeholder listesinde Kitsu ve Simkl hiç
  yoktu → 5 sahte rafa karşılık 7 gerçek raf (tutarsızlık).
- `search()`, "Tümü" aramasında `multiResults`'u tamamen boş + "yüklenmiyor"
  durumuna sıfırlıyordu; yükleme bayrakları ancak ağ isteği kurulduktan sonra
  yazıldığı için araya tüm rafların kaybolduğu bir kare giriyordu (titreme).
- Diğer sekmelerde shimmer, sonuç varken de çiziliyordu (yenileme/sayfalama
  sırasında mevcut satırların üstüne biniyordu).

### 2 — Alakasız / eksik veri
- **Kapsam (scope) TMDB ve Simkl'e hiç uygulanmıyordu.** Manga/Manhwa/Manhua/Light
  Novel kapsamında bile TMDB `search/multi` ve Simkl film+dizi+anime sorguluyordu →
  manhwa aramasının altında live-action filmler.
- **TV / MOVIE / K_DRAMA kapsamları anime veritabanlarına düşüyordu.** Bu kapsamlar
  `else` dalından `MediaType.Anime` ile sorgulanıyordu → "Diziler"/"Filmler"
  rafında anime.
- **Karakter/Personel kapsamı "Tümü" motorunda medya rafı araması yapıyordu.**
  Dal koşulu `engine == ALL` olduğu için CHARACTER/STAFF da yedi medya rafını
  tetikliyor, Karakter sekmesi bu medya kayıtlarını kişi satırı gibi listeliyordu.
- **Kimlik tekilleştirme medya türünü içermiyordu** (`"kaynak_kimlik"`). Kitsu,
  Shikimori, MAL ve TMDB'de anime ile manga (film ile dizi) AYRI kimlik uzayı
  kullanır; karma aramada aynı numaraya denk gelen meşru kayıt sessizce
  siliniyordu. Depodaki mevcut `SourceSearchHandoffTest.sameIdFromAnimeAndMangaIsNotDropped`
  testi bu niyeti zaten belgeliyordu ama "Tümü" akışında uygulanmamıştı.
- **Karma kapsamda manga, anime altında eziliyordu:** `(anime + manga).take(10)`
  sıralı birleştirme + kesme yaptığı için anime listesi dolu geldiğinde manga
  kayıtları hiç görünmüyordu ("The Greatest Estate Developer" vakası).
- **Jikan yedeği popülerliğe göre sıralıyordu:** `order_by=members&sort=desc`,
  Jikan'ın metin alakalılığını ezip gevşek eşleşen EN POPÜLER kayıtları öne
  alıyordu (resmî MAL anahtarı yokken MyAnimeList rafı alakasız doluyordu).
- **AniList rafına `POPULARITY_DESC` gönderiliyordu** (varsayılan parametre);
  sorgu varken alakalılık sıralaması kullanılmalıydı.

### 3 — Onaysız arama
- `SearchViewModel.setQuery` her tuş vuruşunda 400 ms debounce ile `search()`
  çağırıyordu; TV ekranında ayrıca 500 ms debounce'lu otomatik arama vardı.
  Kullanıcı "naruto" yazarken "n", "na", "nar"… sorguları yedi motora birden
  gidiyor, yarıda kesilen istekler raflarda eksik/alakasız sonuç bırakıyordu.

## Yapılan işler

### Animasyon
- "Tümü" sekmesindeki kopya shimmer rafları kaldırıldı; her raf yalnızca kendi
  `isLoading*` bayrağıyla shimmer gösteriyor → **raf başına tek görsel durum**.
- Kullanılmayan `MultiSearchShelfShimmer` composable'ı ve tekrarlanan import silindi.
- Diğer sekmelerde shimmer artık yalnızca `results` boşken çiziliyor.
- `MultiPlatformResults.loading(...)` eklendi; `search()` yükleme bayraklarını
  **kapsama göre ve hemen** yazıyor (ara "boş kare" titremesi yok, sorgulanmayan
  kaynak boşuna shimmer çizmiyor).

### Veri doğruluğu
- `SearchRelevance` (yeni): `keyOf` (kaynak + **tür** + kimlik), `dedupe`, `rank`,
  `refine`, `interleave`, `fold`. Alakalılık sıralaması **kayıt silmez**; sorguyla
  örtüşenleri öne alır, hiç örtüşmeyenleri sona iter. `sortedByDescending` kararlı
  olduğu için aynı kademede kaynağın kendi sıralaması korunur. Eş anlamlı/takma ad
  eşleşmeleri (romanaji ↔ İngilizce, Çince/Japonca başlık) böylece kaybolmaz.
- `fold`: `Locale.ROOT` küçültme + Türkçe/aksan indirgeme + birleşik aksanları
  (U+0300..U+036F) yok sayma → "İstanbul" ↔ "istanbul" eşleşir.
- Kapsam → kaynak planı: `SearchScope.isPrintScope/isLiveActionScope/wantsAnimeSources/
  wantsMangaSources/wantsLiveActionSources/simklTypeFilter` + `planAllSources(scope)`
  (`AllSourcePlan`). Planı `false` olan kaynak için **ağ isteği hiç yapılmaz**; rafı
  boş + yüklenmiyor kalır ve çizilmez.
  - Manga/Manhwa/Manhua/LN → yalnızca anime/manga veritabanları (TMDB, Simkl yok).
  - TV/MOVIE/K_DRAMA → yalnızca TMDB + Simkl; TMDB yanıtı istenen türe süzülür.
  - ANIME → anime veritabanları + Simkl(`type=anime`); TMDB yok (search/multi anime
    ile live-action uyarlamayı ayırt edemez).
  - CHARACTER/STAFF → medya rafı dalına hiç girmez.
- Karma kapsam: anime + manga **dönüşümlü (interleave)** birleştirilir, sonra
  alakalılığa göre sıralanıp `allShelfLimit`'e (10) indirilir; aday havuzu
  `allShelfFetchLimit` (20) → sıralamanın yeniden dizecek alanı olur.
- Tüm tekilleştirme anahtarları `SearchRelevance.keyOf`'a geçti (raflar, birleşik
  liste, `loadMore`).
- `searchJikanFallback`: `order_by=members&sort=desc` kaldırıldı → Jikan/MAL arama
  alakalılığı kullanılır.
- AniList rafı: sorgu varken `sort = emptyList()` (değişken hiç yazılmaz → AniList
  SEARCH_MATCH alakalılığı).

### Onaysız arama
- `setQuery` artık arama **başlatmıyor**; yalnızca metni günceller, eski isteği
  geçersiz kılar ve sorgu boşsa sonuçları temizler. Aramayı başlatan tek yol
  `onSearchAction()` (klavyedeki "Ara" onay tuşu veya arama butonu).
- `KitsugiCosmicSearchBar`'a metin doluyken görünen **arama (onay) butonu** eklendi.
  Küçük ekranlarda yer açmak için `SourceEngineSelectorPill`'e `compact` modu
  geldi (yazarken motor adı gizlenir, logo + ok kalır) → metin alanı daralmaz.
- `TvSearchScreen`'deki 500 ms debounce'lu otomatik arama kaldırıldı (kullanılmayan
  importlar ve `FlowPreview` opt-in'i temizlendi).
- `SearchScreen` arama çubuğu `viewModel.search()` yerine `viewModel.onSearchAction()`
  çağırıyor.

## Değişen dosyalar
- `app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchRelevance.kt` (YENİ)
- `app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchViewModel.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchSourceFilters.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchUiState.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/search/SourceEngineSelector.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiCosmicSearchBar.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/JikanSearchClient.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/tv/search/TvSearchScreen.kt`
- `app/src/test/java/com/kitsugi/animelist/ui/screens/search/SearchRelevanceTest.kt` (YENİ)
- `PLAN_TASK_ARAMA_ANIMASYON_ALAKALILIK_ONAY_FIX.md` (bu dosya)

## Doğrulama
- Bu ortamda JDK/Android SDK yok → Gradle derlemesi ve cihaz testi çalıştırılamadı.
- Yapılan statik doğrulamalar:
  - Değişen/yeni tüm `.kt` dosyalarında ayraç dengesi (string ve yorum satırları
    soyulduktan sonra `{}`/`()`/`[]`) kontrol edildi → tümü dengeli.
  - `SearchRelevance` algoritması birebir Python'a aktarılıp yeni test dosyasındaki
    13 beklenti çalıştırıldı → **13/13 PASS** (kimlik çakışması, popülerlik
    yığınının alta inmesi, İngilizce takma ad, Türkçe aksan/İ, kademe içi sıra
    korunumu, boş sorgu, silmeme garantisi, interleave).
  - Çağıran taraf uyumu: `setQuery`/`onSearchAction`/`search()` çağıran tüm noktalar
    tarandı (`AppRoot.triggerSearch`, `TvSearchScreen`, `SourceEngineFilterSheet`,
    `setTab/setPlatform/setMediaType/setScope/setEngine`, filtre setter'ları,
    `applyHistoryItem`, `applyDetailFilterRequest`) → hepsi aramayı açıkça
    tetikliyor, debounce'un kaldırılması akış bozmuyor.
  - Kullanılan API imzaları doğrulandı: `searchAniListPaged(sort/perPage/country)`,
    `searchMALOnly`, `KitsuExploreClient.searchMediaAdvanced(limit ≤ 20)`,
    `KitsugiShikimoriClient.searchAnime/searchMediaAdvanced`,
    `KitsugiBangumiClient.searchAnime/searchManga(includeAdult)`,
    `SimklApiClient.search(type,limit,page)`, `TmdbApiClient.search`,
    `BangumiApiClient.SEARCH_PAGE_SIZE`, `MediaType` üyeleri.

## Cihazda çalıştırılacak kontroller
1. `bash gradlew :app:testFossDebugUnitTest --tests "com.kitsugi.animelist.ui.screens.search.*"`
2. "Tümü" + ALL_MIXED: "naruto" → yazarken HİÇ istek gitmemeli (logcat'te
   `SearchViewModel` istekleri yalnızca Enter/buton sonrası), yedi rafın her biri
   kendi shimmer'ını gösterip verisi gelince tek seferde karta dönmeli; üstte ayrı
   bir shimmer bloğu görünmemeli.
3. Kapsam = Manhwa + "the greatest estate developer" → TMDB ve Simkl rafı hiç
   çizilmemeli; AniList/MAL/Kitsu raflarında manhwa görünmeli.
4. Kapsam = Diziler + "stranger things" → anime rafları çizilmemeli, TMDB rafında
   yalnızca dizi (film değil) olmalı.
5. Kapsam = Karakterler + motor "Tümü" + bir karakter adı → kişi satırları
   listelenmeli, medya rafları çıkmamalı.
6. Resmî MAL anahtarı yokken (Jikan yedeği) MyAnimeList rafı: alakasız popüler
   anime yerine sorguyla eşleşen kayıtlar üstte olmalı.
7. Küçük ekran (360dp) + motor "MyAnimeList": arama butonu görünürken metin alanı
   okunabilir genişlikte kalmalı.
