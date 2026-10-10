# Plan / Task — Detay Sayfası Filtrelerini Tüm Uyumlu Kaynaklarda Arama

**Tarih:** 2026-10-09  
**Dal:** `arena/cc3b8d51-kitsugi-beta`  
**Durum:** Uygulama tamamlandı; derleme/test doğrulaması ortamda Java bulunmadığı için yapılamadı.

## İstek

Anime, manga, film ve dizi detay sayfalarında görünen tür, etiket ve sezon bilgilerine dokunulduğunda Search ekranı açılsın; seçim, detay sayfasının kaynağına kilitlenmeden ilgili filtreyi destekleyen kataloglarda aransın. Desteklenmeyen filtreler filtresiz sonuç döndürmek yerine güvenle atlanmalı.

## Yapılanlar

- Kayıtlı medya ve API sonucu detay sayfalarındaki tür, etiket ve sezon tıklamaları typed `DetailSearchFilterRequest` ile `AppRoot` üzerinden Search’e bağlandı.
- Detail filtresi Search durumuna uygulanırken motor `ALL` seçiliyor, medya kapsamı korunuyor ve filtreye uygun sağlayıcılar planlanıyor.
- Etiket tıklamalarında adın yanında kaynak ve kimlik de taşınıyor. TMDB keyword filtresi yalnızca tanınan TMDB kaynağı ve pozitif keyword ID varsa kullanılıyor; çıplak etiket metni TMDB keyword ID gibi gönderilmiyor.
- Sezon/yıl bilgisi farklı kaynakların ham ve görüntülenen değerlerinden normalize ediliyor; anime başlangıç tarihinden sezon türetilebiliyor. Sezon araması için geçerli sezon ve yıl yoksa satır arama tetiklemiyor.
- AniList, MAL/Jikan, TMDB, Shikimori, Kitsu, Simkl ve Bangumi all-source arama dallarına ilgili yerel filtreler aktarıldı. Her kaynak yalnızca filtreyi desteklediğinde çalıştırılıyor.
- Jikan boş sorguda tür, sezon/tarih, durum, format, puan, yapımcı/dergi ve sıralama parametreleriyle filtreli arama yapıyor; filtre varken filtresiz sıralama yedeğine düşmüyor.
- Bangumi boş sorguda etiket/tarih/puan filtresi varsa browse yerine gelişmiş arama kullanıyor. Kitsu kategori parametreleri URL-encode ediliyor.
- Seçili motor `ALL` iken filtre sıfırlama tüm kaynakların filtre paketlerini de temizliyor.
- Sezon çözümleme, kaynak uyumluluğu, TMDB keyword doğrulaması ve boş sorgudaki aktif filtre sayımı için birim testleri eklendi.

## Kaynak desteği ve güvenli eşleme

- Türler: yalnızca her kaynakta karşılığı bulunan taxonomy/ID ile uygulanır.
- Etiketler: AniList, Kitsu ve Bangumi metin etiketlerini kendi arama alanlarında kullanır; MAL, Shikimori ve Simkl yalnızca tanınan yerel eşleme varsa dahil edilir; TMDB keyword araması doğrulanmış TMDB ID ister.
- Sezon: sezon + yıl anlamını koruyabilen AniList, MAL/Jikan, Shikimori ve Kitsu çalıştırılır. Simkl, Bangumi ve TMDB sezon filtresini uygulayamadıkları için bu aramada atlanır.
- İçerik kapsamı mevcut arama planıyla korunur; örneğin anime aramasına canlı-aksiyon TMDB sonuçları karıştırılmaz.

## Değişen uygulama/test dosyaları

- `app/src/main/java/com/kitsugi/animelist/AppRoot.kt`
- `app/src/main/java/com/kitsugi/animelist/AppRootDetailPages.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/JikanApiClient.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/JikanSearchClient.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/KitsuExploreClient.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiBangumiClient.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiDetailTabContents.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailComponents.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailPage.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/EntryDetailTabContents.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailComponents.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailInfoSection.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/MediaEntryDetailPage.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/search/DetailSearchFilterRequest.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchUiState.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchViewModel.kt`
- `app/src/test/java/com/kitsugi/animelist/ui/screens/search/DetailSearchFilterRequestTest.kt`

## Doğrulama

- `git diff --check`: başarılı.
- `bash ./gradlew :app:testDebugUnitTest`: çalıştırılamadı; ortamda `JAVA_HOME` ayarlı değil ve `java` komutu yok. Android SDK (`ANDROID_HOME`) da ortamda tanımlı değil.
- Bu nedenle derleme ve birim test sonuçları henüz doğrulanmış değildir.

## Arşiv

Bu plan/task dosyası ve yukarıdaki değişen dosyaların çalışma ağacındaki son halleri, depo dizin yapısı korunarak ZIP arşivine eklenmiştir.
