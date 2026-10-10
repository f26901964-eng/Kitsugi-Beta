# PLAN & TASK — Simkl Detay Sekmeleri Zenginleştirme + MAL Forum Konu Detayı & Çoklu Kaynak Yorumlar (2026-10-10)

**Branch:** `arena/9022f1ad-kitsugi-beta`  
**Tarih:** 2026-10-10

---

## 1) Triyaj ve Kök Neden Analizi

### Görev 1: Simkl Detay Sayfası Sekmeleri (`Ekip`, `İlişkiler`, `Grafikler`, `Yorumlar`)
1. **Simkl Çapraz ID Kaybı (`KitsugiDetailClient.kt` & `KitsugiSimklDetailClient.kt`):**
   - `fetchPrimaryDetail("simkl")` çağrısı önce TMDB üzerinden (`fetchSimklDetailViaTmdb`) detay çektiği için dönen `KitsugiMediaDetail` nesnesinde `realMalId = null` ve `type = null` kalıyordu.
   - Simkl'in kendi uç noktasındaki (`/anime/{id}?extended=full`, `/tv/{id}?extended=full`, `/movies/{id}?extended=full`) `ids.mal`, `ids.tmdb`, `ids.anilist`, `ids.kitsu` alanları çözümlenmiyordu.
2. **ViewModel `loadTab` ve `ReviewsTabContent` İletim Eksikliği (`ApiResultDetailViewModel.kt`, `MediaEntryDetailViewModel.kt`, `ApiResultDetailPage.kt`, `MediaEntryDetailPage.kt`):**
   - Sekmeler yüklenirken yalnız başlangıçtaki `result.realMalId` (Simkl kartlarında `null`) gönderiliyor; detay yüklendikten sonra çözülen `detailState.realMalId` sekmelere ve `ReviewsTabContent` bileşenine aktarılmıyordu.
   - `Grafikler` (Tab 6) sekmesi ilk açılışta `null` döndüyse `realMalId`/`tmdbId` çözümlendiğinde yeniden tetiklenmiyordu.
3. **`cachedSocialDetail` Önbellek Kontrolü Hatası (`KitsugiMediaSocialClient.kt`):**
   - `DetailCache.getMediaDetail(...)?.takeIf { it.type == mediaType }` kontrolü, `KitsugiMediaDetail.type` alanı varsayılan olarak `null` kaldığı için tüm kaynaklarda önbellekten `realMalId` ve `tmdbId` okunmasını engelliyordu.
4. **TMDB ve Simkl `Ekip` (Staff) Zayıflığı (`TmdbCreditsClient.kt` & `KitsugiStaffClient.kt`):**
   - Dizi/Anime serilerinde TMDB `/tv/{id}/credits` uç noktası çoğunlukla yalnızca yürütücü yapımcıları döndürüyor; yönetmen, senarist, besteci ve karakter tasarımcıları `/tv/{id}/aggregate_credits` uç noktasında yer alıyor.
   - TMDB'den CJK (Japonca/Çince/Korece) karakterlerle dönen ekip üyelerinde seslendirme sanatçıları 20 kişilik romaji sorgu kotasını doldurduğu için ekip üyelerine romaji sorgusu kalmıyor ve fotoğrafsız/Japonca isimlerle listeleniyordu.

### Görev 2: MyAnimeList (MAL) Tartışma Konuları ve Konu Detayı (`Tüm Tartışma Konuları` & `Konu Detayı`)
1. **`fetchForumTopicReplies` Yalnızca AniList Sorgusu Çalıştırıyordu (`KitsugiMediaSocialClient.kt`):**
   - MAL'dan gelen bir tartışma konusuna (`source = "jikan"`) tıklandığında MAL `topicId` değeri AniList GraphQL `threadComment(threadId: $threadId)` sorgusuna gönderiliyor ve boş liste (`Henüz yanıt yok.`) dönüyordu.
2. **Jikan `/v4/anime/{id}/forum` Kısıtları:**
   - Jikan konu listesinde yazarın avatarını (`avatarUrl = null`) ve konunun ana mesaj gövdesini (`body`) döndürmüyor; ayrıca varsayılan sıralama en eski konuları en üste getiriyordu.
3. **`Konu Detayı` Arayüzünde Kaynak Ayrımı Eksikliği (`KitsugiTopicDetailBottomSheet.kt` & `TvTopicDetailDialog.kt`):**
   - MAL konularında bile AniList logosu gösteriliyor, konu gövdesi (`body`) çizilmiyor ve yalnız AniList'te çalışan `Takip Et` / `Beğen` / `Yanıtla` butonları MAL konularında da aktif görünüyordu.
4. **MAL Forum BBCode Etiketleri (`KitsugiMarkdownUtils.kt`):**
   - `[quote=Kullanıcı message=123]...[/quote]`, iç içe spoiler/quote, `[yt]`, `[code]`, `[list]`/`[*]`, `[hr]` etiketleri Markdown'a dönüştürülmüyordu.

---

## 2) Uygulanan Çözümler ve Görev Listesi (Tasks)

- [x] **Task 1 — Simkl Çapraz ID Çözümleyici ve Detay Zenginleştirme (`KitsugiSimklDetailClient.kt`, `KitsugiDetailClient.kt`)**
  - `SimklCrossIds` veri yapısı ve önbellekli `resolveSimklCrossIds` fonksiyonu eklendi (`ids.mal`, `ids.tmdb`, `ids.anilist`, `ids.kitsu`, `relations`, `users_recommendations`, `ratings`, `users_stars`).
  - `fetchPrimaryDetail("simkl")` ve `enrichDetail` içinde `realMalId`, `tmdbId` ve `type = mediaType` alanlarının korunması sağlandı.
  - Simkl detay önbellek anahtarı `_s2` olarak güncellendi.

- [x] **Task 2 — Ekip (Staff) Sekmesi Çoklu Kaynak Birleştirme ve TMDB Romaji/Aggregate Credits (`KitsugiStaffClient.kt`, `TmdbCreditsClient.kt`, `KitsugiCharacterClient.kt`)**
  - `TmdbCreditsClient`: Dizi/Anime serileri için `/tv/{id}/aggregate_credits` desteği eklendi; `original_name` Latin kontrolü, `/person/{id}?language=en-US` üzerinden `also_known_as` romaji çözümlemesi ve seslendirme sanatçılarından bağımsız ekip romaji kotası eklendi.
  - `KitsugiStaffClient`: Simkl ve TMDB kaynaklarında çapraz ID'ler (`malId`, `aniListId`, `tmdbId`) çözümlenerek AniList + Jikan/MAL + TMDB ekip listelerini birleştiren, eksik fotoğraf ve Latin isimleri tamamlayan `mergeAndEnrichStaffLists` eklendi.

- [x] **Task 3 — İlişkiler ve Öneriler Sekmesi (`KitsugiMediaRelationsClient.kt`)**
  - Simkl ve TMDB için Jikan/MAL ilişkileri, AniList ilişkileri, Simkl yerel `relations` / `users_recommendations` verileri ve TMDB ilişkileri/önerileri birleştirildi.

- [x] **Task 4 — Grafikler (Stats) ve Çoklu Kaynak Harmanlanmış Yorumlar (`KitsugiMediaSocialClient.kt`, `JikanApiClient.kt`)**
  - `cachedSocialDetail` içindeki `it.type == null || it.type == mediaType` kontrolü düzeltildi.
  - `fetchStats`: Simkl ve TMDB için AniList + Jikan/MAL istatistik birleştirmesi, TMDB yedek istatistikleri ve Simkl `fetchSimklFallbackStats` eklendi.
  - `fetchReviews`: Tüm kaynaklarda (**AniList, MyAnimeList/Jikan, Kitsu `/edge/reviews` + `/edge/media-reactions`, Shikimori, Bangumi, TMDB**) paralel inceleme çekimi ve `interleaveReviewLists` ile round-robin harmanlama eklendi.

- [x] **Task 5 — Resmi MyAnimeList v2 Forum API Entegrasyonu ve Konu Detayı (`KitsugiMediaSocialClient.kt`, `KitsugiModels.kt`, `KitsugiTopicDetailBottomSheet.kt`, `TvTopicDetailDialog.kt`, `KitsugiMarkdownUtils.kt`)**
  - `KitsugiForumTopic` modeline `body: String = ""` alanı eklendi.
  - `BuildConfig.MAL_CLIENT_ID` (`X-MAL-CLIENT-ID`) ile `https://api.myanimelist.net/v2/forum/topic/{topic_id}` uç noktası entegre edildi.
  - `fetchForumTopicsFromJikan`: Konular son aktiviteye göre sıralandı; ilk konularda paralel olarak MAL v2 API üzerinden yazar avatarı (`forum_avator`), `userId`, açılış mesajı (`posts[0].body`) ve anket (`poll`) verisi zenginleştirildi.
  - `enrichForumTopicIfNeeded` ve `fetchMalForumTopicReplies`: MAL konusuna tıklandığında açılış mesajı konu gövdesi olarak, `posts[1..N]` mesajları ise yanıt listesi olarak yüklendi.
  - `KitsugiTopicDetailBottomSheet` & `TvTopicDetailDialog`: Gerçek platform logosu (`KitsugiPlatformLogo`), konu gövdesi (`KitsugiMarkdownText` + Türkçe çeviri) ve yalnız AniList konularında aktif olan `Takip Et` / `Beğen` / `Yanıtla` kontrolleri eklendi.
  - `KitsugiAllTopicsBottomSheet` & `KitsugiAllReviewsBottomSheet`: Kaynak filtre hapları (`Tümü`, `AniList`, `MAL`, `Kitsu`, `TMDB`, `Shikimori`, `Bangumi`) eklendi.

- [x] **Task 6 — ViewModel ve Detay Sayfası Parametre İletimi (`ApiResultDetailViewModel.kt`, `MediaEntryDetailViewModel.kt`, `ApiResultDetailPage.kt`, `MediaEntryDetailPage.kt`, `ReviewsTab.kt`, `TvReviewsTabContent.kt`)**
  - Çözümlenen `effectiveRealMalId` ve `tmdbId` değerlerinin tüm sekmelere ve `ReviewsTabContent` bileşenine aktarılması sağlandı.

---

## 3) Değişen Dosyalar Listesi (20 Kod Dosyası + 2 Plan/Task Dosyası)

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
