# Kitsugi Beta v2.4.287

## 🇹🇷 Türkçe

### Keşfet "Tümünü Gör" Sayfalarında Tür ve Sıralama Artık Kaynaktan Geliyor
- **Kök Neden Çözüldü:** "Filtre ve Sıralama" sayfasında seçilen tür/sıralama eskiden yalnızca cihazda uygulanıyordu (`displayedResults`). Kaydırarak gelen her yeni sayfa filtresiz çekildiği için liste boşalıyor, otomatik sayfalama durmadan boş istek atarak döngüye giriyor ve sıralama her sayfada yeniden karışıyordu.
- **Kaynak Tarafı Sorgular:** Yeni `ExploreSourceFilterQuery` motoru seçilen tür ve sıralamayı 7 kaynağın **kendi API parametrelerine** çevirir:
  - **AniList:** Resmî `genres` veya `tags` (kanonik: `Sci-Fi`, `Slice of Life`) + `MediaSort` (`SCORE_DESC`, `POPULARITY_DESC`, `START_DATE_DESC`, `TITLE_ROMAJI` vb.).
  - **MAL / Jikan:** `genres=<id>` + `order_by`/`sort` (türsüz durumlarda resmî MAL sıralama ucu; başlık/tarih Jikan liste ucuna yönlendirilir).
  - **TMDB:** `with_genres` (+ anime raflarında `16` ve `ja` filtresi) + `sort_by` (`vote_average.*`, `popularity.desc`, `primary_release_date` vb.).
  - **Kitsu:** `filter[categories]` slug formatında (`sci-fi`, `slice-of-life`) + `sort` (`-averageRating`, `-userCount`, `-startDate` vb.).
  - **Shikimori:** Tür kimliği + `order` (`ranked`, `popularity`, `aired_on`, `name`).
  - **Simkl:** Haritalanmış tür uç noktası + `sort` (`rank`, `popular-this-week`, `release-date`).
  - **Bangumi:** Yerel etiket sözlüğü (科幻, 恋爱, 机战…) + `rank`, `heat`, `score`.
- **Liste Baştan Kurulur:** Filtre uygulandığında mevcut liste temizlenir ve **1. sayfa doğrudan filtrelenmiş istekle** kaynaktan çekilir. Kaydırma başa alınır ve sonraki her sayfa aynı filtre/sıralamayı korur.
- **Uçuştaki İstek Koruması:** `filterGeneration` jetonu sayesinde arka planda geciken eski isteklerin yeni listeyi ezmesi önlenir.
- **Kaynak Desteklemiyorsa (☁️ ve 📱 Ayrımı):** Seçim hangi kaynakta API tarafından uygulanabiliyorsa **☁️**, kaynağın desteklemediği durumlarda (ör. Simkl'de Mecha/Müzik, Shikimori'de artan puan) **📱** ile işaretlenir ve güvenli bir şekilde cihazda uygulanır.
- **Hızlı Tür Çipleri:** Izgara ve liste üstündeki hızlı tür çipleri ile "Temizle" butonu da aynı kaynak tarafı sorgu yolunu kullanır.
- **Kişisel Listeler:** Simkl izleme listesi ve TMDB "İzlemeye Devam / Planladıklarım" rafları API'de filtrelenebilir katalog olmadığından cihazda filtrelenmeye devam eder.
- **Sonsuz Döngü Koruması:** `AUTO_LOAD_BUDGET_PER_SCROLL` ve "aynı görünür öğe sayısı" emniyetleri korundu; boş gelen ilk sayfa artık sayfalamayı kontrollü bir şekilde durdurur.

---

## 🇬🇧 English

### Genre & Sort on Explore "See All" Pages Now Handled Directly by Source APIs
- **Root Cause Resolved:** Previously, genre and sort options chosen in the "Filter & Sort" sheet were only evaluated locally on the device (`displayedResults`). As pages were scrolled, subsequent pages were fetched without filters, resulting in empty lists, infinite auto-pagination loops, and broken ordering.
- **Source-Side Query Engine:** The new `ExploreSourceFilterQuery` engine translates active genres and sorts directly into each source's native API parameters:
  - **AniList:** Canonical `genres` or `tags` (e.g. `Sci-Fi`, `Slice of Life`) + `MediaSort` (`SCORE_DESC`, `POPULARITY_DESC`, `START_DATE_DESC`, `TITLE_ROMAJI`, etc.).
  - **MAL / Jikan:** `genres=<id>` + `order_by`/`sort` parameters with official endpoint routing.
  - **TMDB:** `with_genres` (+ genre 16 and Japanese language for anime shelves) + `sort_by` (`vote_average.*`, `popularity.desc`, etc.).
  - **Kitsu:** `filter[categories]` slug translation (`sci-fi`, `slice-of-life`) + `sort` parameters.
  - **Shikimori:** Native genre IDs + `order` (`ranked`, `popularity`, `aired_on`, `name`).
  - **Simkl:** Mapped genre endpoints + `sort` (`rank`, `popular-this-week`, `release-date`).
  - **Bangumi:** Native tag dictionary (科幻, 恋爱, 机战…) + `rank`, `heat`, `score`.
- **Clean List Rebuilding:** Applying a filter clears the dataset and immediately fetches **page 1 with server-side filters applied**. Scrolling resets to the top and subsequent pages retain the same parameters.
- **In-Flight Request Guard:** Generational `filterGeneration` tokens guarantee that delayed responses from superseded requests never corrupt the updated list.
- **Source Compatibility Badges (☁️ vs 📱):** Options supported natively on the remote API are badged with **☁️**, while options falling back to local client filtering are marked with **📱**.
- **Quick Genre Chips:** Top-bar genre chips and the "Clear" button now trigger full server-side filtered re-fetches.
- **Personal Watchlists:** User-specific shelves (Simkl watchlist, TMDB Continue/Plan to Watch) continue filtering client-side as they lack remote catalog filter endpoints.
- **Safety Guards Retained:** `AUTO_LOAD_BUDGET_PER_SCROLL` and duplicate-item guards prevent runaway network calls on empty filter results.
