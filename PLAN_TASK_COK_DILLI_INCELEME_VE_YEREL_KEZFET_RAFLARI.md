# PLAN & TASK: Çok Dilli İnceleme/Aktivite Desteği + Yerel Ek Tür Keşfet Rafları

Tarih: 2026-10-09
Dal: `arena/c48baa0a-kitsugi-beta`
Commit: `d1cb771`
İlgili ekranlar: Detay → Yorumlar sekmesi (mobil + TV), Keşfet (Explore) ekranı, Ayarlar → Çeviri

---

## 1. Problem tanımı

### 1.1 İnceleme/Yorum/Aktivite bölümleri tek dile kilitliydi
- Kullanıcı bildirimi: The Mentalist detay sayfasında (TMDB ve SIMKL kaynağı) "İncelemeler"
  bölümü "Henüz inceleme yazılmamış." gösteriyordu; oysa sitede diğer dillerde incelemeler mevcut.
- Kök neden: `TmdbCreditsClient.fetchReviews` isteği `language=<uygulama dili>` (örn. `tr-TR`)
  ile gönderiliyordu. İncelemeler kullanıcı içeriği olduğu ve TMDB bunları çevirmediği için
  dile sabitleme diğer dillerdeki incelemelerin gelmesini engelliyordu.
- Ek olarak: TMDB/SIMKL kaynaklarında "Tartışma Konuları" ve "Aktiviteler" bölümleri kod
  düzeyinde tamamen devre dışıydı (`isTmdbOrSimkl` kapısı); oysa kimlik eşlemesi çözülebildiğinde
  bu veriler (AniList/MAL üzerinden) mevcuttu.
- Çeviri intent'i (`KitsugiTranslateUtils.openTranslator`) Google Translate'e sabit
  `en → tr` çiftiyle gidiyordu; kaynak dil otomatik algılanmıyor, hedef dil tek dildi.

### 1.2 Keşfet'te yerel ek tür rafları eksikti
- Bangumi için "Bangumi Dizileri / Bangumi Filmleri" (REAL, canlı aksiyon) rafları eklenmişti.
- Aynı fikir diğer kaynaklarda uygulanmamıştı: MAL / Shikimori / AniList'in kendi yerel
  ek tür katalogları (manhwa, manhua, novel, light novel) Keşfet'te görünmüyordu.

---

## 2. Araştırma bulguları

| Kaynak    | Canlı aksiyon (dizi/film) kataloğu | Yerel ek tür rafları                       |
|-----------|------------------------------------|--------------------------------------------|
| Bangumi   | VAR (REAL type=6)                  | zaten uygulamada                           |
| TMDB/SIMKL| VAR (zaten canlı aksiyon DB'leri)  | zaten uygulamada                           |
| MAL       | YOK                                | VAR: `top/manga?type=manhwa/manhua/novel/lightnovel` |
| Shikimori | YOK                                | VAR: `/mangas?kinds=manhwa,manhua` / `light_novel,novel` |
| AniList   | YOK                                | KISMEN: `format: NOVEL` (manhwa ayrımı yok) |
| Kitsu     | YOK                                | YOK (tür sınıflandırması filtrelenemiyor)  |

Sonuç: "Bangumi Dizileri/Filmleri"nin birebir karşılığı diğer dört kaynakta yok; bunun yerine
her kaynağın DESTEKLEDİĞİ yerel ek tür rafları eklendi, desteklemeyende (Kitsu) raf gizlenir.

TMDB API notu: `/movie|tv/{id}/reviews` uç noktası `language` ve `page` parametreleri alır;
`language` yalnızca çeviri alanları filtresidir ve incelemeler çevrilmez. Bu nedenle istekten
çıkarıldı; `page` korunarak sayfalama eklendi.

---

## 3. Yapılan değişiklikler

### 3.1 Çok dilli inceleme/aktivite (Bölüm 1)
1. `KitsugiModels.kt` — `KitsugiReview.languageCode: String?` alanı eklendi (orijinal dil).
2. `TmdbCreditsClient.kt` — `fetchReviews`: `language` parametresi GÖNDERİLMİYOR (tüm diller),
   `page` parametresi eklendi, `iso_639_1` → `languageCode` olarak parse ediliyor.
3. `TmdbApiClient.kt` — `fetchReviews(tmdbId, isMovie, page)` sayfalama iletiliyor.
4. `KitsugiMediaSocialClient.kt`
   - TMDB/SIMKL inceleme çağrılarına `page` iletiliyor.
   - Yeni yardımcılar: `resolveMalIdForSource()` ve `resolveAniListIdForSource()`.
     TMDB/SIMKL/Kitsu/Shikimori/Bangumi kimlikleri MAL id'si olmadığı için önce gerçek
     MAL/AniList eşlemesi çözülüyor (DetailCache → KitsugiIdResolver → ARM/Shikimori/Bangumi cross).
   - `fetchForumTopics`: tüm kaynaklar için genellendi (AniList başlıkları → yoksa MAL forumu;
     eşleme yoksa boş liste).
   - `fetchActivities`: tüm kaynaklar için genellendi (AniList eşlemesi çözülebilirsen gösterilir).
5. `ReviewsTab.kt` + `ui/tv/detail/TvReviewsTabContent.kt` — `isTmdbOrSimkl` kapısı kaldırıldı;
   forum/aktivite artık her kaynakta denenir, boşsa bölüm gizlenir ("varsa görünür" kuralı).
6. `KitsugiReviewCard.kt` — inceleme dil rozeti (örn. EN/JA) istatistik satırında.
7. `KitsugiReviewDetailBottomSheet.kt`, `KitsugiAllReviewsBottomSheet.kt` — çeviri butonları
   incelemenin orijinal dilini kaynak dil olarak iletiyor.

### 3.2 Çeviri: oto kaynak + tüm diller hedef
8. `KitsugiTranslateUtils.kt`
   - `KitsugiTranslatePrefs`: ayarların süreç içi anlık görüntüsü (suspend olmayan intent'ler için).
   - `openTranslator(text, preferredTranslator, sourceLanguage)`: kaynak = incelemenin dili veya
     kullanıcı ayarı ("auto" varsayılan); hedef = kullanıcı ayarı → yoksa uygulama dili.
   - Google intent'leri artık sabit en→tr DEĞİL; `key_language_from/to` dinamik.
   - Yeni web fallback: `translate.google.com/?sl=auto&tl=<hedef>` (uygulama kurulu olmasa bile
     tüm diller + otomatik algılama).
   - `googleLanguageCatalog`: Google Translate'in ~130 dilinin tam kataloğu.
9. `SettingsDataStore.kt` — ayar parse'ında `KitsugiTranslatePrefs.update(...)`; hedef dil
   hiç seçilmediyse varsayılan = uygulamanın aktif dili (`defaultTranslateTarget()`).
10. `KitsugiPreferencesSettingsDialog.kt` — kaynak/hedef dil listeleri tam katalogdan üretiliyor
    (kaynakta "Otomatik (Algıla)" başta).

### 3.3 Keşfet: yerel ek tür rafları (Bölüm 2)
11. `ExploreCategoryType.kt` — `MANHWA_MANHUA`, `NOVELS`.
12. `ExploreModels.kt` — `ExplorePayload.manhwaManhua`, `ExplorePayload.novels` (null = kaynak
    desteklemiyor → bölüm gizli).
13. `JikanSearchClient.kt` — `manhwaManhua()`, `novelsShelf()` (Jikan type filtrelerinin
    skor sıralı birleşimi); `JikanApiClient.kt` sarmalayıcıları.
14. `AniListSearchClient.kt` — `aniListNovels()` (`format: NOVEL`, popülarite sıralı).
15. `ExploreViewModel.kt` — MAL / Shikimori / AniList yükleyicilerine yeni raflar; state,
    `applyPayload`, `clearPayload`.
16. `AllSourcesExplore.kt` — `hasCatalogContent`, `forSource`, `sourceSections` (raflar kendi
    kaynağının altında; boşsa filtrelenir).
17. `NativeKindExploreSections.kt` (YENİ) — tek kaynak modunda rafların çizimi
    (`BangumiRealExploreSections` ile aynı model).
18. `ExploreScreen.kt` — filtreli listeler, vitrin havuzu ve raf çizim bloğu.
19. `FullScreenMediaGridPage.kt` — "Tümünü Gör" sayfalama durumları (MAL/AniList/Shikimori).
20. `HeroSelection.kt` — yeni kategoriler vitrin puan çarpanına dahil.
21. `strings.xml` (values + values-en) — `explore_manhwa_manhua`, `explore_novels`.
22. `AllSourcesExploreTest.kt` — yeni test: raflar yalnızca kaynak destekliyorsa görünür.

---

## 4. Doğrulama
- Sandbox'ta JDK/Android SDK yok → derleme yapılamadı; bunun yerine:
  - Tüm değiştirilen dosyalar için özel lexer ile parantez/kaşlı denge kontrolü (string template,
    raw string, char literal ve yorum farkındalıklı) — hepsi dengeli.
  - Mevcut birim testlerin yeni alan/raflarla uyumu elle doğrulandı (varsayılan `null` alanlar
    ve boş-raf filtreleri sayesinde kırılma yok).
- Önerilen: cihazda/CI'da `./gradlew :app:compileGmsDebugKotlin testDebugUnitTest` çalıştırılması.

## 5. Davranış özeti (kullanıcı diliyle)
- İncelemeler artık hangi dilde yazıldıysa o dilde listelenir; dil rozeti görünür; tek tuşla
  (oto algılama + seçilen hedef dil) çevrilir.
- Forum konuları ve aktiviteler, kaynak hangi veritabanı olursa olsun, eşleme çözülebildiğinde
  görünür; çözülemezse bölüm gizlenir.
- Keşfet'te MAL/Shikimori/AniList kaynaklarında yeni yerel raflar; Kitsu'da gizli (destek yok).
