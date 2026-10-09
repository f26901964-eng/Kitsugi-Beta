# Plan / Task — Detay Sosyal İçerik Eşleşmesi ve Keşfet Vitrin Backdrop'ları

**Tarih:** 2026-10-09  
**Dal:** `arena/97fb3a3b-kitsugi-beta`  
**Durum:** Kod değişiklikleri tamamlandı; Gradle derleme/test doğrulaması JDK bulunmadığı için beklemede.

## Kullanıcı talebi

1. Bir medya detay sayfasında başka başlık veya türden aktivite, inceleme ya da yorum görünmemeli. TMDB, Simkl ve diğer sağlayıcılardaki kaynak kimlikleri doğru türde MAL/AniList kimliğine eşlenmeli.
2. Keşfet vitrininde Anime, TV ve filmler için mevcut geniş artwork/poster'dan önce kullanılmalı; portre ekran bu tercihi değiştirmemeli. Manga, geniş artwork bulunmadığı için daha düşük öncelikli istisnadır.
3. Değişen dosyalar, bu plan/task belgesiyle birlikte ZIP'lenip Google Drive'a yüklenmeli.

## Kabul kriterleri

- [x] Sağlayıcının kendi ID'si başka sağlayıcının MAL/AniList ID'si gibi varsayılmaz; yalnızca açık/ doğrulanmış eşleme kullanılır.
- [x] Anime/Manga olmayan detaylarda Jikan/AniList aktivite ve forum sorguları yapılmaz.
- [x] Aktivite girdisi, hedef AniList medya ID'si ve `ANIME`/`MANGA` türü ile birebir uyuşmalıdır; medya kimliği olmayan serbest metin aktiviteleri detay akışına eklenmez.
- [x] Review cache kaydı kaynak + ID + medya türü ile okunur; tür belirtilmiş okumada türsüz cache girdisine geri düşülmez.
- [x] Anime/TV/film için backdrop varsa posterden önce denenir; Manga mevcut en-boy oranı seçimini korur.
- [x] TMDB movie/TV kayıtları bilinen TMDB ID'si ve doğru TMDB türü ile aranır; türü belirsiz Anime sonucu başlık + tür + yıl üzerinden aranır.
- [x] Review, aktivite ve forum ID'leri detay sekmelerinden ve sayfalı alt sayfalardan taşınır.

## Uygulama planı ve tamamlanan işler

### 1. Sosyal içerik kimliği ve tür güvenliği

- MAL/Jikan kaynakları ortaklaştırıldı; TMDB/Simkl/Kitsu/Shikimori/Bangumi ID'leri doğrudan MAL/AniList kabul edilmiyor.
- TMDB/Simkl'den Anime/Manga içeriklerine geçiş için yalnızca doğrulanmış TMDB veya gerçek MAL çapraz ID'si kullanılıyor. Shikimori manga için tür belirsiz eski ID fallback'i kapatıldı.
- Film/TV için TMDB review'ları doğru movie/TV endpoint'inden alınıyor. Anime/Manga Jikan/AniList içerikleri farklı türlere taşmıyor.
- Forum ve aktivite çağrıları yalnızca Anime/Manga'da çalışıyor; aktiviteler hedef AniList ID'si ve türü ile yeniden filtreleniyor.
- Detay sekmeleri ve tüm review/activity/topic alt sayfaları `tmdbId` / `realMalId` değerlerini iletiyor. Medya değişince eski sosyal satırlar temizleniyor.

### 2. Review cache izolasyonu

- `DetailCache.getMediaReviews` türü belirtilmiş çağrılarda yalnızca tür anahtarını okuyor.
- Her iki detay ViewModel'inde cache okuma/yazma/silme işlemleri `MediaType.name` ile yapılıyor.
- Türler arası fallback olmadığını doğrulayan unit test eklendi.

### 3. Keşfet vitrin artwork'u

- Anime, TV ve filmlerde backdrop adayları portre ekranda da posterden önce seçiliyor; Manga oran tabanlı mevcut tercihi koruyor.
- TMDB movie/TV kaynaklarında exact ID sorgusu; türü bilinmeyen Anime/diğer kaynaklarda tür ve yıl uyumlu başlık araması kullanılıyor.
- Simkl Explore sonuçlarında TMDB ID'si korunuyor.
- Hero seçim testleri güncellendi; cache ve MAL-ID güvenliği için iki unit test eklendi.

### 4. Doğrulama durumu

- [x] `git diff --check` temiz.
- [x] Güncellenen sosyal çağrı noktaları ve türlendirilmiş review cache erişimleri statik olarak tarandı.
- [ ] `:app:testDebugUnitTest` ve `:app:compileDebugKotlin` — çalıştırılamadı: ortamda `java` bulunmuyor ve `JAVA_HOME` ayarlı değil.
- [ ] Cihaz/önizleme üzerinde manuel görsel ve sosyal akış kontrolü.

## Değişen uygulama ve test dosyaları

- `app/src/main/java/com/kitsugi/animelist/data/remote/DetailCache.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/JikanApiClient.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiMediaRelationsClient.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiMediaSocialClient.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/SimklApiClient.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/TmdbApiClient.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/TmdbDiscoverClient.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiAllActivitiesBottomSheet.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiAllReviewsBottomSheet.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiAllTopicsBottomSheet.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiHeroSection.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailPage.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailViewModel.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/MediaEntryDetailPage.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/MediaEntryDetailViewModel.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ReviewsTab.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/explore/ExploreViewModel.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/tv/detail/TvDetailScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/tv/detail/TvReviewsTabContent.kt`
- `app/src/test/java/com/kitsugi/animelist/data/remote/DetailCacheReviewIsolationTest.kt`
- `app/src/test/java/com/kitsugi/animelist/data/remote/MalJikanMediaSupportTest.kt`
- `app/src/test/java/com/kitsugi/animelist/ui/components/HeroImageSelectionTest.kt`

Bu belge ZIP paketinde değişen dosyalarla birlikte bulunur.
