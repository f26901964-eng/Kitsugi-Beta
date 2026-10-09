# Kitsugi — Stüdyo/Yapımcı Detay Keşfet Paritesi + Kaynak İsim Rozetleri: Teşhis & Düzeltme

**Tarih:** 2026-10-09 · **Dal:** `arena/daa716db-kitsugi-beta` · **Temel commit:** `2126157` (v2.4.220) · **Sürüm notu:** v2.4.221
**Durum:** Kod değişiklikleri TAMAM, derleme yapılamadı (sandbox'ta JDK/Gradle yok) → doğrulama kullanıcı tarafında.

---

## 1. Şikâyet (kullanıcı raporu, 6 ekran görüntüsüyle)

1. **Stüdyo/yapımcı detay sayfalarında stüdyoya ait açıklamalar (about) gösterilmiyor** — "eğer varsa dahil edilmeli".
2. **Stüdyo/yapımcı detay sayfası**, diğer keşfet açılır sayfalarına ("Shikimori · En İyi Animeler" gibi `FullScreenMediaGridPage`) **tıpatıp benzemeli**: ekran boyutu ve dikey/yatay moda göre uyarlanan düzen, filtreleme, grid/liste (kara/yatay) görünümler, aşağı kaydırınca çıkan **yukarı anlık kaydırma tuşu**, kutucukları kaplayan **şerit** (çip şeridi + kaydırınca beliren üst şerit) dahil.
3. **Kaynak isimlerini görsel gösteren özellik bazı kaynaklarda düzgün çalışmıyor** — hem **vitrin (hero)**, hem **detay açılırkenki yüklenme ekranı**, hem **detay sayfasında**; tüm kaynaklarda logo+isim tutarlı görünmeli (3–6. görsellerde Bangumi/Simkl/Shikimori çipleri referans).

---

## 2. Kök Nedenler

| # | Kök neden | Etki |
|---|---|---|
| A | `StudioDetailPage` yatay modda ayrı "sol panel + sağ grid" düzeni kullanıyor; keşfet sayfalarındaki duyarlı kolon hesabı, çip şeridi, filtre sheet'i, üst şerit ve yukarı FAB yoktu. | Keşfet paritesi eksik |
| B | Stüdyo `about` alanı dikey modda gösteriliyordu ancak yatay modda ve yeni düzen hedefinde tutarsızdı; kartlar `StudioMediaGridItem` ile keşfet kartlarından farklı görünüyordu. | Görsel dil farklı |
| C | `toFriendlySourceLabel()` **bangumi/bgm, shiki, themoviedb, myanimelist, al, fanart** eşlemelerini içermiyordu → örn. vitrin meta satırında ham `bangumi` yazıyordu. | İsim görsel/dostça değil |
| D | `KitsugiCinematicLoadingScreen` kaynak rozeti **hiç göstermiyordu**. | Yükleme ekranında kaynak ismi yok |
| E | Detay sayfaları kaynak pill'ini **yalnızca metin** (`...uppercase()`) çiziyordu; logo yoktu. Vitrin çipi (`heroSourceLabel`) ayrı bir özel harita kullanıyordu. | Logo+isim tutarsız |

---

## 3. Çözüm (değişen dosyalar)

| Dosya | Değişiklik |
|---|---|
| `ui/screens/detail/StudioDetailPage.kt` | **Yeniden yazım:** `FullScreenMediaGridPage` ile birebir aynı davranış — duyarlı kolon sayısı (dikey/yatay + genişlik), grid/liste toggle'ı (kalıcı tercih), emojili tür çipleri (✨ Tümü/🎬 Anime/📖 Manga/🎥 Film/📺 Dizi), `KitsugiStudioFilterBottomSheet` (sıralama + tür), "N içerik • filtre" sayacı + Temizle, kaydırınca beliren üst şerit (geri+başlık+galeri+favori+filtre+toggle), yukarı kaydırma FAB'ı, `KitsugiExploreMediaCard`/`KitsugiRankingMediaCard` kartları (degrade şeritli kutucuklar). `about` her iki yönde de başlık altında gösterilir. |
| `ui/screens/detail/StudioDetailComponents.kt` | `StudioHeroHeader`'a `height` parametresi; kaynak pill'i → `DetailSourcePill` (logo+isim); yeni `STUDIO_TYPE_FILTERS`, `StudioSortOption`, `toSearchResult()`, `studioTypeMatches()`, `KitsugiStudioFilterBottomSheet`. |
| `ui/components/KitsugiSourceBadge.kt` | Yeni ortak çip **`KitsugiSourceNamePill`** (logo + dostça isim) — vitrin/yükleme/detay ortak görsel dil. |
| `ui/components/KitsugiHeroSection.kt` | `heroSourceLabel` özel haritası kaldırıldı; vitrin çipi `KitsugiSourceNamePill` kullanıyor → tüm kaynaklarda logo+isim. |
| `ui/components/KitsugiCinematicLoadingScreen.kt` | `source` parametresi + logo+isim çipi gösterimi. |
| `ui/screens/detail/MediaEntryDetailPage.kt`, `ApiResultDetailPage.kt`, `CharacterDetailPage.kt`, `StaffDetailPage.kt` | Yükleme ekranına `source` aktarımı. |
| `ui/screens/detail/DetailSharedComponents.kt` | Yeni `DetailSourcePill` (renkli pill içinde logo + dostça isim). |
| `ui/screens/detail/KitsugiDetailHeroSection.kt`, `KitsugiDetailHero.kt`, `ApiResultDetailComponents.kt` | Metin-tek kaynak pill'i → `DetailSourcePill`. |
| `utils/KitsugiTranslations.kt` | `toFriendlySourceLabel` genişletildi: bangumi/bgm→Bangumi, shiki→Shikimori, themoviedb→TMDB, myanimelist/jikan (mal) varyantları→MyAnimeList, al→AniList, fanart→Fanart.tv. |

---

## 4. Davranış Notları

- **Tür çipleri** stüdyo yapımlarını gerçek veriyle filtreler (MediaType); keşfet'teki tür (genre) çiplerinin stüdyo verisinde karşılığı olmadığı için tür-bazlı eşdeğer uyarlandı, görsel dil birebir aynı.
- **about**: Jikan/MAL ve TMDB açıklama sağlar ve artık her iki yönde de gösterilir; AniList API'sinde stüdyo açıklama alanı hiç yoktur ("eğer varsa" kuralı gereği kaynak sağlamadığında gösterilemez).
- Grid/liste tercihi `kitsugi_ui_prefs` → `studio_detail_is_grid_view` anahtarıyla kalıcıdır.
- TV genişlik sınırı (960.dp) keşfet sayfalarıyla aynı şekilde uygulanır.

---

## 5. Doğrulama

- Sandbox'ta JDK/Gradle bulunmadığından derleme yapılamadı; sözdizimi dosya bazında ayrıştırıcıyla doğrulandı (parantez/kaşeli dengesi, import/çözünürlük kontrolleri).
- Kullanıcı tarafında: stüdyo sayfası (dikey+yatay), çip/filtre/sıralama, yukarı FAB, üst şerit; vitrin + yükleme + detayda Bangumi dahil tüm kaynaklarda logo+isim çipi kontrol edilmeli.
