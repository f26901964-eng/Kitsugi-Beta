# PLAN / TASK — MAL (MyAnimeList) + Jikan Detay Sekmeleri Ortak ve Sorunsuz Çalışma

**Tarih:** 2026-10-08
**Dal:** `arena/f9bac5de-kitsugi-beta`
**Durum:** Uygulandı (bu sandbox'ta Android SDK/JVM ve Gradle bağımlılık erişimi olmadığı için derleme çalıştırılamadı; değişiklikler satır satır diff denetimi + yapı kontrolü ile doğrulandı)

---

## Kullanıcının bildirdiği sorun

MAL kaynaklı ayrıntı sayfasında (ör. Frieren) **Karakterler, Ekip, Öneriler, İlişkiler**
sekmeleri veri getirmiyor; iskelet (skeleton) / spinner durumunda kalıyor ya da boş
dönüyor. MAL ve Jikan'ın aynı kimlik uzayı olmasına rağmen istemci katmanında ayrı ayrı
ve tutarsız kurallarla ele alınması kök neden.

## Teşhis (kod denetimi bulguları)

1. **Kaynak adı parçalanması:** `source.lowercase()` dallanması bazı istemcilerde
   `"jikan", "mal"` beklerken kullanıcı listesi kayıtları / bazı akışlar `"myanimelist"`
   gibi etiketler taşıyabiliyor; bu değerler `else -> emptyList()`'e düşüyordu.
2. **AniList-tip eşleme hatası:** Jikan/MAL kayıtlarında `MediaType.Movie` ve
   `MediaType.TvShow` değerleri AniList GraphQL sorgularına `MANGA` olarak, bazı Jikan
   uçlarına `manga` olarak gönderiliyordu → AniList yedeği hiçbir zaman sonuç üretemiyor,
   Jikan uç noktası 404/boş dönüyordu.
3. **AniList'in MAL'in önüne geçmesi:**
   - `fetchStats("mal")` önce AniList'i bekliyor, ancak AniList dolu/iyi ise dönüyordu;
     AniList yavaş/429 iken MAL istatistikleri 25 sn'lik sekme zaman aşımına takılıyordu.
   - `fetchRelationsFromJikan` / `fetchRecommendationsFromJikan`, Jikan listesi
     çözüldükten **sonra** AniList'ten toplu kapak/başlık sorgusunu (`idMal_in`)
     bekliyordu; AniList gecikmesi tüm sekmeyi kitliyordu.
4. **Yedek zincir sırası:** Jikan boş döndüğünde doğrudan Shikimori ID çözümlemesine
   (ARM + Shikimori API) gidiliyor; aynı MAL ID'siyle sorgulanabilecek AniList
   (`idMal`) köprüsü hiç denenmiyordu.

## Yapılan değişiklikler

### Yeni ortak kural modülü
- `data/remote/MalJikanMediaSupport.kt` (**yeni**)
  - `isMalSource`: `mal`, `jikan`, `myanimelist` → aynı uzay.
  - `canonicalSource`: üçünü de `jikan` dalına normalize eder (tüm `when` dallanmaları
    artık ortak kapıdan geçer).
  - `resolveMalId`: MAL/Jikan sonuç kimliğinin zaten MAL ID olduğunu kabul eder
    (< 100M aralığı); diğer kaynaklarda yalnızca doğrulanmış `realMalId` kullanılır.
  - `jikanEndpoint` / `aniListMediaType`: Movie/TvShow → `anime` / `ANIME` ortak kuralı.

### İstemci düzeltmeleri
- `KitsugiMediaRelationsClient.kt`
  - `fetchRelations`/`fetchRecommendations` MAL dalı: kimlik `resolveMalId` ile
    doğrulanır; **Jikan birincil**, AniList yalnız aynı MAL ID ile yedek, TMDB yalnız
    verilen `tmdbId` varsa son çare (ARM/bulanık arama yok).
  - Jikan ilişki/öneri listelerinden AniList toplu zenginleştirme bekleme **kaldırıldı**
    (Jikan verisi hemen ekrana gelir).
  - Tüm Jikan/AniList tip eşlemeleri ortak kurala bağlandı.
- `KitsugiCharacterClient.kt` / `KitsugiStaffClient.kt`
  - MAL dalı kimlik doğrulaması + endpoint ortak kural.
  - Jikan boşsa **önce AniList (aynı MAL ID, `idMal`)**, sonra Shikimori çözümlemesi.
- `KitsugiMediaSocialClient.kt`
  - `fetchStats` MAL dalı: **Jikan birincil** (izleme/tamam/plan/puan dağılımı doluysa),
    AniList yalnız yedek.
  - `fetchReviews` MAL dalı: Jikan → AniList (aynı MAL ID).
  - Endpoint/tip eşlemeleri ortak kural.
- `KitsugiMediaTabsClient.kt` — MAL bölüm listesi kimlik doğrulaması.
- `KitsugiDetailClient.kt`, `KitsugiStudioClient.kt`, `KitsugiMalDetailClient.kt` —
  kaynak dallanması `canonicalSource` üzerinden; endpoint kuralı ortak.

## Beklenen davranış

- MAL/Jikan/MyAnimeList kaynaklı ayrıntı sayfalarında Karakterler/Ekip/Öneriler/
  İlişkiler/Grafikler/Yorumlar/Bölümler sekmeleri **Jikan (MAL) birincil** veriyle dolar;
  AniList ve TMDB yalnız hızlı ve güvenli yedeklerdir.
- Film/Dizi türündeki MAL kayıtları artık `manga`/`MANGA` uçlarına gönderilmez.
- AniList yavaşlığı veya 429'u MAL sekmelerini iskelette bırakmaz.

## Değişen dosyalar (9)

| # | Dosya |
|---|-------|
| 1 | `app/src/main/java/com/kitsugi/animelist/data/remote/MalJikanMediaSupport.kt` (yeni) |
| 2 | `KitsugiMediaRelationsClient.kt` |
| 3 | `KitsugiCharacterClient.kt` |
| 4 | `KitsugiStaffClient.kt` |
| 5 | `KitsugiMediaSocialClient.kt` |
| 6 | `KitsugiMediaTabsClient.kt` |
| 7 | `KitsugiDetailClient.kt` |
| 8 | `KitsugiStudioClient.kt` |
| 9 | `KitsugiMalDetailClient.kt` |

## Doğrulama notu

Bu sandbox'ta Java/Android SDK kurulu değil ve Gradle bağımlılıkları ağ kısıtı dışındaki
sunucularda olduğundan `./gradlew` derlemesi/test çalıştırılamadı. Bunun yerine:
- tüm değişikliklerin `git diff` ile satır satır denetimi,
- değiştirilen bölgelerin tam dosya görüntüleriyle karşılaştırmalı okuması,
- yeni sembollerin aynı paket içinde çözümlediğinin `rg` ile doğrulanması yapıldı.
Cihaz üzerinde doğrulama için: MAL kaynaklı bir animede (Frieren) sekmelerin dolu
geldiğini; film türündeki MAL kayıtlarında sekmelerin boş/404'e düşmediğini kontrol edin.
