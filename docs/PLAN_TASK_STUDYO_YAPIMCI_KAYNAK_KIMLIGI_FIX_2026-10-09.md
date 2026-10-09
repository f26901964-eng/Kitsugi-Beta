# PLAN / TASK — Stüdyo ve Yapımcı Detaylarında Doğru Kaynak Kimliği

**Tarih:** 2026-10-09  
**Dal:** `arena/5645fbdf-kitsugi-beta`  
**Durum:** Kaynak değişiklikleri tamamlandı; diff kontrolü başarılı, Gradle testleri ortamda Java/JDK bulunmadığı için çalıştırılamadı.

## İstek

Stüdyo/yapımcı adına veya çipine tıklandığında aynı sağlayıcıdaki doğru kaydın açılması; MAL ekranındaki Aniplex'in Bones gibi başka bir sağlayıcı kaydına yönelmemesi. Bu davranış yalnızca MAL'de değil, desteklenen diğer kaynaklarda da kontrol edilmeli. Detay açılırken yükleme ekranında ad ve mevcut logo/görsel korunmalı. Stüdyo arama sonuçları da yanlışlıkla anime/medya detayına değil stüdyo detayına gitmeli.

## Teşhis

Stüdyo kimlikleri sağlayıcıya özgüdür. Aynı sayısal ID Jikan/MAL, AniList, TMDB, Shikimori ve Bangumi'de farklı şirketleri gösterebilir. Önceki çözümlemede stüdyo kaynağı eksik veya göz ardı edilmiş durumlarda, üst medya kaynağına göre seçim yapmak bu ID'yi başka sağlayıcıda açabiliyordu. Ayrıca eski bir ağ yanıtının yeni açılan stüdyo durumunu ezmesi ve yanlış isimli cache kaydının gösterilmesi ihtimali vardı.

## Uygulanan değişiklikler

- `KitsugiStudio` modeline kaynağı bilinmiyorsa boş kalan `source` alanı ve mevcutsa `imageUrl` eklendi; sağlayıcı ayrıştırıcıları kimlik namespace'ini açıkça belirtiyor.
- Stüdyo çiplerinde açık sağlayıcı kaynağı parent medya kaynağından önce kullanılıyor. Jikan/MAL ID'si isimle doğrulanıyor; uyuşmazlıkta Jikan producer adıyla yeniden arıyor, eşleşmeyi doğrulamadan farklı şirketi göstermiyor ve AniList'e yanlış ID fallback'ini kaldırıyor.
- TMDB, Shikimori ve Bangumi için stüdyo/yapımcı detay yükleme yolları kullanılıyor; Bangumi eşleştirmesi yalnızca şirket türündeki kayıtlara (`type = 2`) izin veriyor.
- Detay ViewModel'i fetch/cache anahtarında adı da hesaba katıyor; yanlış isimli Jikan cache sonucunu reddediyor ve eski coroutine yanıtlarının yeni ekranı ezmesini engelliyor.
- Kaynak, ad ve varsa görsel detay rotasından yükleme ekranına taşınıyor. Jikan/TMDB/Shikimori/Bangumi verilerinde mevcut görsel/logo bilgisi de aktarılıyor.
- MAL/Jikan, AniList ve TMDB stüdyo araması sonuçları `StudioDetailPage`'e yönlendiriliyor; arama satırında stüdyo kaydını medya listesine ekleme eylemi kapatılıyor.
- Kaynak önceliği ve şirket adı eşleşmesi için birim testleri eklendi.

## Kaynak kapsamı

Doğrudan stüdyo/yapımcı ID'si veya detay endpoint'i olan yollar kontrol edildi: **Jikan/MAL, AniList, TMDB, Shikimori ve Bangumi**. Kitsu/Simkl'in mevcut detay ayrıştırıcılarında doğrudan stüdyo/yapımcı listesi yok; bu kaynaklarda gösterilebilen kayıtlar mevcut çapraz-kaynak zenginleştirmesinden gelebiliyor ve kendi açık `source` değerini koruyor.

## Değişen kod dosyaları

- `app/src/main/java/com/kitsugi/animelist/AppRoot.kt`
- `app/src/main/java/com/kitsugi/animelist/AppRootDetailPages.kt`
- `app/src/main/java/com/kitsugi/animelist/AppRootTabPages.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/JikanSearchClient.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiAniListDetailClient.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiBangumiDetailClient.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiMalDetailClient.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiModels.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiShikimoriClient.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiStudioClient.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/StudioSourceSupport.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/TmdbMediaDetailClient.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiDetailTabContents.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/EntryDetailTabContents.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/StudioDetailPage.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/StudioDetailViewModel.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchResultRow.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/search/SourceSearchPage.kt`
- `app/src/test/java/com/kitsugi/animelist/data/remote/StudioSourceSupportTest.kt`

## Doğrulama

- `git diff --check`: başarılı.
- `bash gradlew testFossDebugUnitTest --no-daemon`: çalıştırılamadı; sandbox'ta `java` komutu/JDK ve `JAVA_HOME` yok.
- Kullanıcı tarafında önerilen smoke testler: MAL/Jikan Aniplex çipi; AniList kaynaklı medya üzerindeki Jikan producer çipi; TMDB şirket logosu/yapım şirketi; Shikimori ve Bangumi şirket/stüdyo bağlantıları; MAL/AniList/TMDB stüdyo arama sonucu; ad ve görselin yükleme ekranında korunması.
