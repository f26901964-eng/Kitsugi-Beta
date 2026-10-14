# Kitsugi Beta v2.4.286

## 🇹🇷 Türkçe

### Keşfet "Tümünü Gör" Sayfalarında Tür ve Sıralama Artık Kaynaktan Geliyor
- **Kök neden:** "Filtre ve Sıralama" sayfasında seçilen tür/sıralama yalnızca **cihazda** uygulanıyordu (`displayedResults`). Kaydırarak gelen her yeni sayfa filtreye uymadığı için liste boşalıyor, otomatik sayfalama durmadan istek atıyor (döngü) ve sıralama her sayfada yeniden karışıyordu.
- **Kaynak tarafı sorgular:** Yeni `ExploreSourceFilterQuery` motoru seçilen tür ve sıralamayı 7 kaynağın **kendi API parametrelerine** çevirir: AniList (`genres`/`tags` + `MediaSort`), MAL/Jikan (`genres` + `order_by`/`sort`, resmî sıralama ucu), TMDB (`with_genres` + `sort_by`), Kitsu (`filter[categories]` + `sort`), Shikimori (tür kimliği + `order`), Simkl (tür uç noktası + `sort`), Bangumi (yerel etiket + `rank`/`heat`/`score`).
- **Liste baştan kurulur:** Filtre uygulanınca liste temizlenir ve **1. sayfa filtrelenmiş istekle** çekilir; sonraki her sayfa aynı türü/sırayı korur. "Uygula"dan sonra kaydırırken sıralama artık bozulmaz.
- **Uçuştaki istek koruması:** Her filtre uygulamasında artan bir jeton (generation) sayesinde geç dönen eski istekler yeni listeyi ezmez.
- **Kaynak desteklemiyorsa:** Kaynağın API'sinde karşılığı olmayan seçenekler filtre sayfasında **📱** ile işaretlenir ve eskisi gibi cihazda uygulanır; kaynakta uygulananlar **☁️** olarak gösterilir (örn. Shikimori'de artan puan, Simkl'de başlık sıralaması yoktur).
- **Hızlı çipler:** Izgara/liste üstündeki tür çipleri ve "Temizle" de aynı yolu kullanır — çipe dokunmak listeyi o türle baştan çeker.
- **Kişisel listeler:** Simkl izleme listeleri ile TMDB "İzlemeye Devam"/"Planladıklarım" rafları API'de filtrelenebilir bir katalog olmadığı için eskisi gibi cihazda filtrelenir.
- **Emniyet korundu:** `AUTO_LOAD_BUDGET_PER_SCROLL` ve "aynı görünür öğe sayısı" korumaları yerinde; boş ilk sayfa artık sonsuz istek üretmez, sayfalama durur.
- **Eşleşme kalitesi:** AniList'e tür/etiket adları kanonik yazımıyla (`Sci-Fi`, `Slice of Life`), Bangumi'ye yerel etiket sözlüğüyle gönderilir; Kitsu kategori adları slug'a çevrilir (`slice-of-life`).

---

## 🇬🇧 English

### Genre & Sort on Explore "See All" Pages Now Come From the Source
- **Root cause:** The genre/sort picked in the "Filter & Sort" sheet was applied **on device only** (`displayedResults`). Every page appended by scrolling ignored the filter, so the list emptied out, auto-pagination kept firing (loop) and the order was reshuffled on each page.
- **Source-side queries:** The new `ExploreSourceFilterQuery` engine translates the selection into each source's own API parameters: AniList (`genres`/`tags` + `MediaSort`), MAL/Jikan (`genres` + `order_by`/`sort`), TMDB (`with_genres` + `sort_by`), Kitsu (`filter[categories]` + `sort`), Shikimori (genre id + `order`), Simkl (genre endpoint + `sort`), Bangumi (native tag + `rank`/`heat`/`score`).
- **The list is rebuilt:** Applying a filter clears the list and fetches **page 1 with the filter applied**; every later page keeps the same genre/order.
- **In-flight guard:** A generation token per apply keeps late responses from older requests overwriting the new list.
- **Unsupported options:** Options a source cannot serve are marked **📱** in the sheet and keep working on device; source-side ones are shown as **☁️**.
- **Quick chips:** The genre chips above the grid/list and "Clear" use the same path.
- **Personal lists:** Simkl watchlists and the TMDB "Continue/Planned" shelves are not a filterable catalogue, so they keep filtering on device.
- **Safety kept:** `AUTO_LOAD_BUDGET_PER_SCROLL` and the same-visible-count guards stay; an empty first page no longer triggers an endless fetch loop.
