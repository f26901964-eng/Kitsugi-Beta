# PLAN/TASK — Arama rafı ↔ “Tümünü Gör” tutarlılığı

Tarih: 2026-10-08
Durum: Kod düzeltmeleri tamamlandı; cihazda/Gradle'da doğrulama bekliyor.

## Hata
- “Tümü” aramasında The Greatest Estate Developer gibi manga/manhwa sonuçları AniList/MAL rafında görünürken “Tümünü Gör” varsayılan anime araması yaptığı için sonuç kayboluyordu.
- MAL gelişmiş aramasında sorgu sonuçsuz kaldığında popüler sıralama gösteriliyor; aranan başlıkla ilgisiz kayıtlar görüntüleniyordu.
- Arama metni değişirken önceki isteğin sonuçları yeni metnin altında görünebiliyordu.

## Yapılan işler
- Raf başlığından kaynağa özel sayfaya sorgu, seçili kapsam ve raftaki kayıtları aktar. Yenileme başarısız olduğunda rafta zaten görünen kayıtları koru.
- AniList, MAL, Shikimori ve Kitsu için karma (anime + manga) kapsamı destekle; iki türü ayrı sorgulayıp kimliğe göre birleştir ve sayfala.
- MAL sorgulu aramada popüler sıralamaya düşme; resmi arama sonuçsuz/ulaşılamazsa aynı sorgu ve sayfa için Jikan arama yedeğini kullan.
- TMDB'de filtresiz başlık aramasını discover/with_keywords yerine metin arama uç noktasından geçir; Simkl karma sorguda anime/dizi/film sonuçlarını birlikte iste.
- Yeni sorgu/temizleme anında eski arama işini geçersiz kıl; aynı kaynaktaki anime ve mangayı ID çakışmasında yanlışlıkla tek kayda düşürme.
- Karma kapsam ve raftan devralınan sonuçların birleştirilmesi için birim testleri ekle.

## Değişen dosyalar
- `app/src/main/java/com/kitsugi/animelist/AppNavigation.kt`
- `app/src/main/java/com/kitsugi/animelist/AppRoot.kt`
- `app/src/main/java/com/kitsugi/animelist/AppRootTabPages.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/JikanSearchClient.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/SimklApiClient.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchSourceFilters.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchViewModel.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/search/SourceSearchPage.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchResultMerge.kt`
- `app/src/test/java/com/kitsugi/animelist/ui/screens/search/SourceSearchHandoffTest.kt`

## Doğrulama / kalan işler
- `git diff --check` ve arama akışı statik kontrolleri geçti.
- Bu ortamda Java/Android SDK olmadığından Gradle testi ve gerçek cihaz testi çalıştırılamadı. Uygun ortamda `bash gradlew :app:testFossDebugUnitTest --tests com.kitsugi.animelist.ui.screens.search.SourceSearchHandoffTest` çalıştır.
- Cihazda “the greatest estate developer” sorgusuyla Tümü → AniList/MAL → Tümünü Gör akışını; sonuçsuz MAL sorgusunu; sorgu değişimini; manga/anime karma listelemeyi ve filtreli aramayı ayrıca kontrol et.

Not: ZIP yalnızca değişen kaynak/test dosyaları ve bu plan/task dosyasını içerir; tek başına APK veya tam proje değildir. Dosyaları mevcut depo köküne aynı dizin yapısıyla yerleştirin.
