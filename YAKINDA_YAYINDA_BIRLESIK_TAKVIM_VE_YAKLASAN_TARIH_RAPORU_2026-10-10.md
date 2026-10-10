# Yakında Yayında: Birleşik Takvim + Liste Kartlarında Yaklaşan Tarih — 2026-10-10

## Sorun (kullanıcı bildirimi)
1. **Tümü keşfet sayfası**: "Yakında Yayında" şeridi ve şeridin okuyla açılan
   "Bu Hafta Yayında" takvim sayfası tek kaynağın verisini gösteriyordu;
   Tümü modunda tüm kaynakların **birleşimi** olarak çalışması istendi.
   (Kaynak modundaki keşfet sayfaları kaynağa özgü kalmaya devam eder.)
2. **TMDB · Yaklaşan Film ve Diziler** liste sayfasında kartlarda yaklaşan yayın
   tarihi yoktu; bilgi yalnızca detay sayfasında görünüyordu (resim 3-4-5).
3. **Shikimori · Yayındaki Animeler** (ve benzeri liste sayfaları) için aynı eksik:
   detaya girmeden "Bölüm X · tarih · N gün sonra" görülemiyordu (resim 6-7-8).

## Çözüm
### 1. Birleşik "Yakında Yayında" (Tümü modu)
- `ExploreScreen.sharedAiringSoon`: artık tek kaynak önceliği yerine **tüm kaynak
  payload'larının birleşimi** — AniList/MAL bölüm takvimi + TMDB vizyon/bölüm
  tarihleri tek şeritte; kimlik (`exploreIdentity`) + normalize başlıkla
  tekilleştirme, yayın epoch'una göre sıralama.
- `AppRootTabPages`: Tümü modunda takvim `preferredSource = "all"` ile açılır.
- `KitsugiAiringCalendarClient.fetchWeeklySchedule("all")`: AniList haftalık
  takvimi + TMDB haftalık takvimi paralel çekilip `mergeWeeklySchedules` ile
  gün bazlı birleştirilir (aynı gün içinde kimlik + başlık tekilleştirme;
  öncelik AniList'te çünkü kesin bölüm numarası orada).
- `AiringEntry.source` alanı eklendi ("anilist" / "tmdb"): birleşik takvimde
  öğeye tıklandığında `toJikanSearchResult("all")` doğru kimlik uzayını
  (tmdb id / mal-anilist id) kullanır.
- Takvim başlığı sayaç etiketi "all" modunda "içerik" olur (film+dizi karışık).

### 2. Liste kartlarında yaklaşan tarih (TMDB + Shikimori + diğerleri)
- Yeni `KitsugiUpcomingAirEnricher` (data/remote):
  - AniList `airingSchedules` 14 günlük pencere (tek istek, 10 dk süreç-içi
    önbellek) → MAL kimliği + normalize başlık eşleşmesiyle Shikimori/Kitsu/
    Bangumi/MAL/AniList öğelerine `nextAiringEpisode = "bölüm|epoch"` ekler.
  - TMDB `next_episode_to_air` (öğe başına tek istek, 6 eşzamanlı, 6 saat
    önbellek, negatif sonuçlar da önbellekte) → yayındaki TMDB dizilerinin
    gelecek bölüm tarihi çözülür. Filmler vizyon tarihini zaten taşır.
  - Eşleşme yoksa öğe değişmez; manga öğeleri atlanır; tüm çağrılar IO'da.
- `FullScreenMediaGridPage`: `LaunchedEffect(loadedResults)` ile liste verisi
  zenginleştirilir → grid (`KitsugiExploreMediaCard`) ve liste
  (`KitsugiRankingMediaCard`) modlarındaki mevcut `NextAiringChip` yaklaşan
  tarihi detay sayfasına girmeden gösterir.

## Dokunulan dosyalar
- `app/.../data/remote/KitsugiUpcomingAirEnricher.kt` (yeni)
- `app/.../data/remote/KitsugiAiringCalendarClient.kt`
- `app/.../data/remote/KitsugiAiringModels.kt`
- `app/.../ui/screens/explore/ExploreScreen.kt`
- `app/.../ui/screens/explore/KitsugiAiringCalendarScreen.kt`
- `app/.../ui/screens/fullscreen/FullScreenMediaGridPage.kt`
- `app/.../AppRootTabPages.kt`

## Davranış özeti
| Mod | Şerit | Takvim (ok) | Kategori liste sayfaları |
|---|---|---|---|
| Tümü | tüm kaynakların birleşimi | birleşik (AniList+TMDB) | zenginleştirilmiş tarih çipleri |
| Kaynak (TMDB, Shikimori, ...) | kaynağa özgü | kaynağa özgü | zenginleştirilmiş tarih çipleri |
