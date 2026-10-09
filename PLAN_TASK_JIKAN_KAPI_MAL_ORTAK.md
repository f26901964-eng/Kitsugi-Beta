# PLAN / TASK — Jikan Merkezi Kapı (JikanGateway) + MAL Ortak Veri Akışı

**Tarih:** 2026-10-09
**Dal:** `arena/eb95a1ef-kitsugi-beta`
**Durum:** Kod uygulandı. Bu sandbox'ta JDK/Gradle/Android SDK yok; derleme **çalıştırılamadı**. Doğrulama: değişen dosyaların diff denetimi, sembol ve parantez kontrolü. Cihaz üzerinde test aşağıda.

---

## 1. Kullanıcı talebi

- MAL kaynaklı içerikte **Keşfet, Arama, Karakterler, Ekip, İlişkiler, Öneriler** Jikan ile ortak çalışsın.
- AniList koruması (yedek zincir) **kalsın**.
- Jikan'a özgü **limit/kota, veri çekme zamanlaması ve mekaniği** her yerde uygulansın; ekran kilitlenmesin.
- Jikan API'si hakkında detaylı araştırma.

---

## 2. Jikan API araştırması (resmi dokümantasyon: https://docs.api.jikan.moe/)

| Konu | Resmi bilgi | Uygulamaya etkisi |
|---|---|---|
| Günlük limit | **Sınırsız** | Günlük bütçe yok; sınır dakika/saniye bazlı. |
| Dakika limiti | **60 istek/dk** | Güvenlik payı ile **55/dk** (UI), arka plan **40/dk** tavanı. |
| Saniye limiti | **3 istek/sn** | İstekler arası **350 ms** aralık (≈2.85/sn). |
| Önbellek | Tüm istekler sunucuda **24 saat** önbelleğe alınır; `Expires`, `Last-Modified`, `X-Request-Fingerprint` başlıkları var | İstemci tarafında detay uçları 6 sa, liste/arama 30 dk. |
| ETag | Her yanıtta `ETag` (MD5); `If-None-Match` ile `304 Not Modified` alınabilir | **Henüz uygulanmadı** (bkz. §6). |
| 404 | Kaynak yok veya MAL 404 verdi | Kalıcı; yeniden denenmez. |
| 429 | `RateLimitException`: Jikan veya MAL tarafında limit aşıldı | Tüm Jikan istekleri için soğuma (Retry-After yoksa 2 sn, en fazla 10 sn). |
| 500 / 503 | Geçici hata / bakım | Sınırlı yeniden deneme (üstel bekleme). |
| MAL tarafı | Dokümanda: "MyAnimeList kendi tarafında da hız sınırlayabilir" | 429 mantığı Jikan'ın kendi limitini ve MAL kaynaklı 429'u aynı şekilde ele alır. |
| Yetki | Yalnızca **GET**, salt okunur; MAL hesabı yazma yok | Yazma işlemleri Jikan'a gönderilmez. |

**Not:** Dokümanda `Retry-After` başlığından söz edilmiyor. Başlık gelirse kullanılır, gelmezse varsayılan soğuma uygulanır.

---

## 3. Yeni mimari: `JikanGateway` (`data/remote/JikanGateway.kt`)

Tüm Jikan trafiği tek kapıdan geçer:

1. **Kota kapısı:** Kayan 60 sn penceresinde en fazla 55 (UI) / 40 (BACKGROUND) istek, aralık 350 ms. Bekleme `Mutex`/senkronize slot rezervasyonu ile yapılır; ana iş parçacığı bloklanmaz (`admit` suspend, `admitBlocking` yalnızca IO).
2. **429 soğuması (`reportRateLimited`):** Tüm Jikan çağrıları ortak bekler. Eski yapıda her çağrı bağımsız yeniden denediği için kotayı tekrar tüketiyordu.
3. **Önbellek:** LRU, 300 kayıt; ID'li detay uçları (`/anime/{id}/full`, `/characters`, `/staff`, `/relations`, `/recommendations`, `/pictures`, `/episodes`) 6 sa; `/reviews`, `/forum`, `/news`, `/statistics` ve arama/top/seasons uçları 30 dk.
4. **Tekilleştirme (singleflight):** Aynı URL için eşzamanlı istekler tek ağ çağrısını paylaşır. MAL detay açılışında 5-6 sekmenin aynı `/full` ucuna aynı anda vurması engellenir.
5. **Öncelik:** `UI` (kullanıcının açık ekranı) tüm kotayı kullanabilir; `BACKGROUND` (kimlik doğrulama, profil, senkron) pencerenin son 15 hakkına dokunmaz. Böylece arka plan işleri kullanıcı ekranlarını kilitleyemez.
6. **Yeniden deneme:** 404 kalıcıdır. 429/5xx/ağ hatası en fazla `maxRetries` kez denenir. Eski `KitsugiApiBase` çağıranlarında yeniden deneme `executeGetRequestResilient` içinde kalır (çarpan olmasın diye gateway `maxRetries = 0`).

---

## 4. Değişiklikler

| Dosya | Değişiklik |
|---|---|
| `data/remote/JikanGateway.kt` (**yeni**) | Merkezi kapı: kota, 429 soğuması, önbellek, singleflight, öncelik. |
| `data/remote/KitsugiApiBase.kt` | `api.jikan.moe` eski host bütçesinden çıkarıldı; `performGet` ve `executeGetRequestOrThrow` Jikan isteklerini gateway'e yönlendirir. |
| `data/remote/JikanSearchClient.kt` | **Keşfet (Top / Airing / Upcoming / Trending / Movie / Publishing / Completed / Newly added / Seasonal):** Jikan birincil, resmi MAL ikincil, AniList son yedek. **Arama:** `search` ve `searchMALOnly` Jikan birincil. `searchMalAdvanced`: sezon, arama ve sıralamada Jikan birincil. `requestAndParseJikan` artık ham OkHttp yerine gateway kullanır (eskiden kota dışıydı). |
| `data/auth/CrossSyncIdentityGuard.kt` | Kimlik doğrulama Jikan çağrısı `BACKGROUND` önceliğiyle gateway'den geçer. Ayrı `PlatformRateLimiter("jikan")` bekleme kaldırıldı (çift bekleme yoktu, ama tek kaynak olsun diye). |
| `data/auth/PlatformRateLimiter.kt` | `jikan` anahtarı kaldırıldı (artık gateway yönetir). |
| `ui/app/AuthViewModel.kt` | `searchMALOnly` öncesi çift limiter çağrısı kaldırıldı (gateway zaten uyguluyor). |
| `ui/app/KitsugiProfileViewModel.kt` | Kullanıcı istatistik / favori / arkadaş uçları: `BACKGROUND` kota kapısı + 429 raporu. |
| `ui/screens/detail/CharacterDetailViewModel.kt`, `StaffDetailViewModel.kt` | Görsel (`/pictures`) yükleyicisi: kota kapısı + 429 raporu (IO thread). |

**Değişmeyen (zaten Jikan birincil, artık gateway üzerinden):** MAL detay (`KitsugiMalDetailClient`), karakter (`KitsugiCharacterClient`), ekip (`KitsugiStaffClient`), ilişki/öneri (`KitsugiMediaRelationsClient`), istatistik/yorum (`KitsugiMediaSocialClient`), bölüm listesi (`KitsugiMediaTabsClient`), stüdyo (`KitsugiStudioClient`), Bangumi detay yardımcısı.

**AniList koruması:** Hiçbir yedek zincirinden çıkarılmadı. Jikan boş/hatalı döndüğünde AniList (ve resmi MAL) çalışmaya devam eder. AniList'in kendi kotası (`executeAniListQuery`, 700 ms aralık) değişmedi.

---

## 5. Bilinen sınırlamalar / yaklaşımlar

- **Sıralama yaklaşımları:** Jikan'da bazı AniList sıralamalarının birebir karşılığı yok.
  - `completedManga` → `manga?status=complete&order_by=members`
  - `newlyAddedManga` → `manga?status=publishing&order_by=start_date`
  - `newlyAddedAnime` → `seasons/now`
  Bunlar yaklaşık "yeni eklenenler/popüler" listesidir.
- **Sayfa boyutu:** Jikan top/seasons uçları sayfa başına 25 kayıt döner; önceki resmi MAL yolu 20 kayıt idi. Sayfa numarası bağımsız çalışır, ancak sayfa başına öğe sayısı artabilir.
- **Sezon sıralaması:** Jikan `seasons` ucu `sort` parametresi kabul etmez. Uygulama sıralaması (SCORE/START_DATE) bu aşamada yapılmadı; sezon sırası Jikan'ın varsayılanıdır.
- **Adult filtresi:** `top/*` ve `seasons` uçlarına `sfw` gönderilmedi (uç noktaların desteklediği parametre garantisi olmadığı için). Adult içerik filtresi mevcut `isAdult` kontrolüyle devam eder.
- **ETag/304:** Henüz uygulanmadı. İstemci önbelleği (TTL) yeterli görüldü; yenileme gerektiğinde eklenebilir.
- **Derleme:** Bu ortamda derlenemedi. Cihaz/CI'da `./gradlew :app:assembleDebug` çalıştırılmalı.

---

## 6. Sonraki adımlar

- [ ] `./gradlew :app:assembleDebug` ile derleme.
- [ ] Frieren (MAL): Karakterler, Ekip, İlişkiler, Öneriler, Grafikler ve Bölümler birkaç saniyede dolmalı; skeleton kalmamalı.
- [ ] Keşfet → MAL sekmesi: Top/Airing/Seasonal listeleri Jikan'dan gelmeli (log: `JikanGateway`). 429 durumunda skeleton yerine kısa soğumadan sonra veri gelmeli.
- [ ] Arama: MAL kaynaklı sonuçlarda `source` MAL/Jikan kimliği taşımalı.
- [ ] `adb logcat -s JikanGateway KitsugiApiBase` ile 429 sayısını kontrol edin; `HTTP 429` sık görülüyorsa `UI_PER_MINUTE` değerini düşürün.
- [ ] ETag/304 desteği (isteğe bağlı).
