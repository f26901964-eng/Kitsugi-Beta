# PLAN_TASK — Keşfet "Tümünü Gör" Sayfalarında Kaynak Tarafı Tür ve Sıralama

**Tarih:** 2026-10-14
**Sürüm:** v2.4.286
**Kapsam:** `ui/screens/fullscreen/FullScreenMediaGridPage.kt`, `ui/screens/explore/ExploreSourceFilterQuery.kt` (yeni), `ui/components/KitsugiMediaFilterBottomSheet.kt`, `data/remote/JikanApiClient.kt`, `data/remote/JikanSearchClient.kt`

---

## 1) Kullanıcı bildirimi

"Filtre ve Sıralama" sayfasından bir **tür** ya da **sıralama** seçildiğinde istek kaynağa göre atılmıyordu. Sayfa yalnızca elindeki listeyi cihazda süzüp sıralıyordu; aşağı kaydırdıkça gelen her yeni sayfa da aynı yerel işleme giriyordu. Sonuç:

- Seçilen tür kaynağın API'sine hiç gitmiyordu → "Tür Filtresi" çalışmıyor gibi görünüyordu,
- Kaydırma başına gelen yeni sayfa sırayı bozuyordu ("her kaydırmada yeniden sıralıyor" hatası),
- Filtreyle eşleşmeyen sayfalar geldiğinde liste boşalıyor, otomatik sayfalama boş yere istek atmaya devam ediyordu (döngü / ısınma).

Kullanıcı isteği: **tüm filtreler (tür + sıralama) kaynakların kendi API'lerine gönderilmeli**; "Tümü" dâhil 7 kaynak (AniList, MAL, Simkl, Kitsu, Shikimori, TMDB, Bangumi) bunu desteklemeli.

## 2) Kök neden (özet)

- `displayedResults` (remember) → `KITSUGI_MEDIA_GENRES` anahtar kelimelerini `item.genres`/`item.subtitle` üzerinde arıyor, ardından puan/popülerlik/yıl/başlık sıralarını `members ?: favorites` gibi alanlarla **cihazda** uyguluyordu.
- Filtre sayfasının `onApply`'ı yalnızca state yazıyordu; `loadNextPage()` / `bangumiPage()` / `fetchSimklPage()` **tür ve sıralama parametresi almıyordu** (yalnız mevsimsel yol `seasonalSort` geçiyordu).
- Bu yüzden her yeni sayfa filtresiz geliyordu.

## 3) Çözüm

### 3.1 Yeni motor: `ExploreSourceFilterQuery.kt`

`fetchFilteredExplorePage(platform, categoryType, genreId, sort, page, showAdultContent, clients, …)` seçilen seçenekleri kaynak parametrelerine çevirir:

| Kaynak | Tür | Sıralama |
|---|---|---|
| AniList | resmî `genres` ya da `tags` (kanonik yazım: `Sci-Fi`, `Slice of Life`) | `SCORE_DESC`, `SCORE` (artan), `POPULARITY_DESC`, `START_DATE(_DESC)`, `TITLE_ROMAJI(_DESC)` |
| MAL/Jikan | `genres=<MAL id>` (tür kimliği eşlemesi); roman → `novel`+`lightnovel`, manhwa → `manhwa`+`manhua` | `order_by=score/popularity/start_date/title` + `sort=asc/desc`; **tür yoksa** resmî MAL sıralama ucu (başlık/tarih sıraları Jikan liste ucuna zorlanır: `applySortWithoutFacets`) |
| TMDB | `with_genres` (+ anime raflarında 16 + `with_original_language=ja`) | `vote_average.*`, `popularity.desc`, `primary_release_date.*`/`first_air_date.*` (başlık sırası yalnız filmlerde) |
| Kitsu | `filter[categories]=<slug>` (`slice-of-life`) | `-averageRating`, `averageRating`, `-userCount`, `-startDate`, `startDate`, `canonicalTitle(-)` |
| Shikimori | tür kimliği (Anime/MAL eşlemesi) | `ranked`, `popularity`, `aired_on`, `name` |
| Simkl | tür uç noktası `genres/<slug>` (haritalı 12 tür) | `rank`, `popular-this-week`, `release-date` |
| Bangumi | yerel etiket sözlüğü (`科幻`, `恋爱`, `机战`, …) | `rank`, `heat`, `score` (yalnız etiketli aramada) |

Ek yetenekler:
- `ExploreSourceFilters.supportsGenre / supportsSort / serverGenreIds / serverSorts` → sayfa ve filtre sayfası hangi seçeneğin sunucuda çalıştığını bilir.
- Kaynakta karşılığı olmayan raflar (`null` spec) hiç sorgulanmaz; sayfa eski davranışa (cihazda filtreleme) düşer.
- "Tümü" modu: seçilen türü/sıralamayı **destekleyen tüm kaynaklara paralel** istek atar ve sonuçları dönüşümlü (round-robin) birleştirir.
- İptal edilen coroutine'ler yutulmaz (`CancellationException` yeniden fırlatılır).

### 3.2 Sayfa: `FullScreenMediaGridPage.kt`

- `appliedGenreId` / `appliedSortOption` / `filterGeneration` (jeton) / `forceFreshFetch` state'i eklendi.
- `baseExplorePage(np)`: eski `loadNextPage()` gövdesi birebir korunarak ayrıldı (her rafın kendi ucu).
- `fetchExplorePage(np)`: sunucu filtresi etkinken `fetchFilteredExplorePage`, aksi halde `baseExplorePage`.
- `applyExploreFilter(genreId, sort)`: çipler, "Temizle" ve sayfa `onApply`'ı artık buradan geçer; jeton artar.
- `reloadForActiveFilter(...)`: liste baştan kurulur (`currentPage=1`, `loadedResults=empty`) ve **1. sayfa filtreli** çekilir; kaydırma başa alınır. Cihazda filtrelenen seçimlerde ağa çıkılmaz, tohum listeye dönülür.
- `applySeasonalFilter(...)`: mevsim/yıl/sıra artık aynı jetonu kullanır (`forceFreshFetch = true`) → mevsim değişince liste kaynaktan yenilenir.
- `loadNextPage()`: uçuştaki istek jetonu karşılaştırılır; eski sonuç listeyi ezmez.
- `displayedResults`: yerel süzme/sıralama **yalnızca kaynağın uygulayamadığı** seçenekler için çalışır (`localGenreFilter` / `localSort`).
- `AUTO_LOAD_BUDGET_PER_SCROLL=3` + "aynı görünür öğe sayısı" emniyeti korunur; boş ilk sayfa `hasMorePages=false` yapar.
- Başlıktaki "N içerik" satırı seçimin nerede uygulandığını gösterir: **☁️** (kaynak) / **📱** (cihaz).

### 3.3 Filtre sayfası: `KitsugiMediaFilterBottomSheet.kt`

- `sourceLabel`, `serverGenreIds`, `serverSorts` opsiyonel parametreleri eklendi (**varsayılan `null`** → diğer çağrı yerleri etkilenmez).
- Kaynağın uygulayamadığı çipler `📱` ile soluklaştırılır; alt bilgi satırı hangi seçimin cihazda kalacağını yazar.
- İki yeni metin (TR + EN): `filtre_kaynak_sunucu_bilgi`, `filtre_kaynak_yerel_bilgi`.

### 3.4 Kaynak istemcisi

- `JikanSearchClient.searchMalAdvanced(...)` ve `JikanApiClient.searchMalAdvanced(...)`: `applySortWithoutFacets` parametresi (tür facet'i yokken başlık/tarih sıralarını Jikan liste ucunda uygular).

## 4) Kişisel listeler ve özel durumlar

- Simkl izleme listeleri ile TMDB "İzlemeye Devam"/"Planladıklarım" rafları API'de filtrelenebilir katalog değildir → sunucu filtresi kapalı, yerel yol korunur.
- Sayfa cihaz döndürme/süreç ölümü sonrası geri yüklendiğinde uygulanmış sunucu filtresi varsa liste 1. sayfadan yeniden çekilir (tohum liste gösterilmez).

## 5) Doğrulama

- Sandbox'ta Android derlemesi yapılamıyor (JDK/Gradle/Android SDK yok) → derleme kullanıcıda: `build-apk.ps1`.
- Statik doğrulama: parantez/blok dengesi, tüm çağrı yerlerinin imzaları (7 istemci fonksiyonu + `CanonicalFilterBridge` eşlemeleri) tek tek kontrol edildi.
- Canlı uç kontrolü: Kitsu `filter[categories]=sci-fi` ve `sort=canonicalTitle` → 200; Shikimori `?order=name&kind=tv` → 200. Jikan uç noktası sandbox'tan 500 verdiği için MAL davranışı kod üzerinden doğrulandı.

## 6) Bilinen sınırlar / takip

- Bangumi etiket sözlüğü 19 çipin yaygın karşılıklarını içerir; topluluk sözlüğünde olmayan bir etiket için sonuç boş dönebilir.
- Kitsu'da bulunmayan bir kategori slug'ı (`filter[categories]`) boş liste döndürebilir; bu durumda sayfa "içerik yok" gösterir.
- Simkl'in resmî tür uç noktası bazı türleri (mecha, music, tarih, askeri, okul, psikolojik, gerilim) desteklemez → bu çipler Simkl sayfalarında `📱` olarak işaretlenir.
- Studio detay ve eklenti tam ekran sayfaları aynı filtre sayfasını kullanır; yeni parametreler opsiyonel olduğu için davranışları değişmez (ileride kaynak tarafı filtreye taşınabilir).
