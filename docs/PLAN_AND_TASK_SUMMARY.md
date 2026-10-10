# Kitsugi-Beta Değişiklik ve Görev Planı (Task Summary — 2026-10-10)

## 📌 1. Görev: Simkl Detay Sayfası Sekmeleri Zenginleştirme (Ekip, İlişkiler, Grafikler, Yorumlar)
### Yapılan İşlemler:
1. **Simkl Çapraz ID Çözümleyici (`KitsugiSimklDetailClient.kt` & `KitsugiDetailClient.kt`):**
   - Simkl API uç noktasından (`/anime/{id}?extended=full`, `/tv/{id}?extended=full`, `/movies/{id}?extended=full`) `ids.mal`, `ids.tmdb`, `ids.anilist`, `ids.kitsu`, `relations`, `users_recommendations`, `ratings` ve `users_stars` verilerini önbellekli olarak çözümleyen `resolveSimklCrossIds` eklendi.
   - `fetchPrimaryDetail("simkl")` ve `enrichDetail` aşamalarında `realMalId`, `tmdbId` ve `type = mediaType` alanlarının korunması sağlandı.
2. **Ekip Sekmesi Çoklu Kaynak Birleştirme ve TMDB Romaji/Aggregate Credits (`KitsugiStaffClient.kt` & `TmdbCreditsClient.kt`):**
   - TMDB dizi/anime serileri için `/tv/{id}/aggregate_credits` desteği eklendi.
   - CJK (Japonca/Çince/Korece) karakterli ekip üyeleri için `original_name`, `/person/{id}?language=en-US` `also_known_as` romaji çözümlemesi (seslendirme sanatçılarından bağımsız kota ile) ve AniList + Jikan/MAL + TMDB ekip birleştirme (`mergeAndEnrichStaffLists`) eklendi.
3. **İlişkiler, Grafikler ve Çoklu Kaynak Yorumlar (`KitsugiMediaRelationsClient.kt`, `KitsugiMediaSocialClient.kt`):**
   - İlişkiler ve öneriler sekmesinde Jikan/MAL, AniList, Simkl yerel `relations`/`users_recommendations` ve TMDB verileri birleştirildi.
   - Grafikler (`fetchStats`) sekmesinde AniList + Jikan/MAL istatistikleri, TMDB yedek istatistikleri ve Simkl `fetchSimklFallbackStats` birleştirildi.
   - Yorumlar (`fetchReviews`) sekmesinde **AniList, MyAnimeList/Jikan, Kitsu (`/edge/reviews` + `/edge/media-reactions`), Shikimori, Bangumi ve TMDB** incelemeleri paralel çekilerek `interleaveReviewLists` ile harmanlandı.
4. **ViewModel ve Detay Sayfası Parametre İletimi (`ApiResultDetailViewModel.kt`, `MediaEntryDetailViewModel.kt`, `ApiResultDetailPage.kt`, `MediaEntryDetailPage.kt`):**
   - Çözümlenen `effectiveRealMalId` ve `tmdbId` değerlerinin tüm sekmelere ve `ReviewsTabContent` bileşenine aktarılması sağlandı.

---

## 📌 2. Görev: MAL (MyAnimeList) Tartışma Konuları ve Konu Detayı Düzeltmeleri
### Yapılan İşlemler:
1. **Resmi MyAnimeList v2 Forum API Entegrasyonu (`KitsugiMediaSocialClient.kt`):**
   - `BuildConfig.MAL_CLIENT_ID` (`X-MAL-CLIENT-ID`) kullanılarak `https://api.myanimelist.net/v2/forum/topic/{topic_id}` uç noktası entegre edildi.
   - Jikan'dan gelen konular son yanıta göre sıralandı ve paralel olarak MAL v2 API üzerinden yazar avatarı (`forum_avator`), `userId`, açılış mesajı (`posts[0].body`) ve varsa anket (`poll`) verisi zenginleştirildi.
   - MAL konusuna tıklandığında `enrichForumTopicIfNeeded` ve `fetchMalForumTopicReplies` ile açılış mesajı konu gövdesine, `posts[1..N]` mesajları ise yanıt listesine aktarıldı.
2. **Konu Detayı ve BBCode İyileştirmeleri (`KitsugiTopicDetailBottomSheet.kt`, `TvTopicDetailDialog.kt`, `KitsugiMarkdownUtils.kt`):**
   - `Konu Detayı` penceresinde gerçek platform logosu (`KitsugiPlatformLogo`), konu gövdesi (`KitsugiMarkdownText` + Türkçe çeviri) ve yalnız AniList konularında aktif olan `Takip Et` / `Beğen` / `Yanıtla` kontrolleri eklendi.
   - `KitsugiMarkdownUtils.kt` içine MAL forum BBCode etiketleri (`[quote=Yazar message=...]`, iç içe quote/spoiler, `[yt]`, `[code]`, `[list]`/`[*]`, `[hr]`) desteği eklendi.
   - `KitsugiAllTopicsBottomSheet.kt` ve `KitsugiAllReviewsBottomSheet.kt` pencerelerine kaynak filtre hapları (`Tümü`, `AniList`, `MAL`, `Kitsu`, `TMDB`, `Shikimori`, `Bangumi`) eklendi.

---

## 📂 Değişen Dosyalar Listesi
1. `app/src/main/java/com/kitsugi/animelist/utils/KitsugiMarkdownUtils.kt`
2. `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiModels.kt`
3. `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiSimklDetailClient.kt`
4. `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiDetailClient.kt`
5. `app/src/main/java/com/kitsugi/animelist/data/remote/TmdbCreditsClient.kt`
6. `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiCharacterClient.kt`
7. `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiStaffClient.kt`
8. `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiMediaRelationsClient.kt`
9. `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiMediaSocialClient.kt`
10. `app/src/main/java/com/kitsugi/animelist/data/remote/JikanApiClient.kt`
11. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailViewModel.kt`
12. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/MediaEntryDetailViewModel.kt`
13. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailPage.kt`
14. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/MediaEntryDetailPage.kt`
15. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ReviewsTab.kt`
16. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiTopicDetailBottomSheet.kt`
17. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiAllTopicsBottomSheet.kt`
18. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiAllReviewsBottomSheet.kt`
19. `app/src/main/java/com/kitsugi/animelist/ui/tv/detail/TvTopicDetailDialog.kt`
20. `app/src/main/java/com/kitsugi/animelist/ui/tv/detail/TvReviewsTabContent.kt`
21. `PLAN_TASK_SIMKL_DETAY_VE_MAL_FORUM_FIX_2026-10-10.md`
22. `PLAN_AND_TASK_SUMMARY.md`
