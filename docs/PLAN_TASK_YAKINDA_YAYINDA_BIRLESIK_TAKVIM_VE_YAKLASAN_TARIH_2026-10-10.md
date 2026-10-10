# PLAN_TASK: Yakında Yayında Birleşik Takvim + Liste Kartlarında Yaklaşan Tarih (2026-10-10)

## Amaç
1. `Tümü` keşfet modunda "Yakında Yayında" şeridi ve şeridin okuyla açılan
   "Bu Hafta Yayında" takvim sayfasının **tüm kaynakların birleşimi** olarak
   çalışması (kaynak modları kaynağa özgü kalır).
2. TMDB "Yaklaşan Film ve Diziler" ile Shikimori "Yayındaki Animeler" gibi tam
   ekran liste sayfalarında, detay sayfasına girmeden kartlarda yaklaşan yayın
   tarihinin ("Bölüm X · tarih · N gün sonra") görünmesi.

## Adımlar
- [x] `ExploreScreen.sharedAiringSoon` → tüm kaynak payload'larının birleşimi
      (kimlik + normalize başlık tekilleştirme, yayın epoch'una göre sıralama).
- [x] `AppRootTabPages` → Tümü modunda takvim `preferredSource = "all"`.
- [x] `KitsugiAiringCalendarClient.fetchWeeklySchedule("all")` → AniList +
      TMDB haftalık takvimlerinin gün bazlı birleşimi (`mergeWeeklySchedules`).
- [x] `AiringEntry.source` alanı + `toJikanSearchResult("all")` → birleşik
      takvimde tıklanan kayıt doğru kimlik uzayından detaya çözülür.
- [x] `KitsugiAiringCalendarScreen` başlık etiketi: "all" modunda "içerik".
- [x] Yeni `KitsugiUpcomingAirEnricher`:
      - AniList `airingSchedules` 14 gün (tek istek, 10 dk önbellek) → MAL id +
        normalize başlık eşleşmesi (Shikimori/Kitsu/Bangumi/MAL/AniList).
      - TMDB `next_episode_to_air` (öğe başına, 6 eşzamanlı, 6 sa önbellek).
      - Eşleşmeyen öğe değişmez; manga atlanır; ağ çağrıları IO'da.
- [x] `FullScreenMediaGridPage` → `LaunchedEffect(loadedResults)` ile
      zenginleştirme; grid/liste kartlarındaki mevcut `NextAiringChip` tarihi gösterir.

## Doğrulama (cihazda)
- Tümü > Yakında Yayında şeridi: anime + TMDB film/dizi kayıtları birlikte,
  yayın tarihine göre sıralı.
- Tümü > şerit oku: takvimde gün sekmeleri AniList + TMDB kayıtlarını içerir;
  TMDB kaydına tıkla → TMDB detayı açılır.
- TMDB > Yaklaşan Film ve Diziler: kartlarda "Bölüm X · yyyy-mm-dd · N gün sonra".
- Shikimori > Yayındaki Animeler: kartlarda bölüm + tarih + geri sayım.
- Kaynak modları (örn. TMDB takvimi) eskisi gibi kaynağa özgü kalır.

## İlgili rapor
`YAKINDA_YAYINDA_BIRLESIK_TAKVIM_VE_YAKLASAN_TARIH_RAPORU_2026-10-10.md`
