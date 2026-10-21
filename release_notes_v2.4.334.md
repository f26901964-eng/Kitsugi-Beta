# Kitsugi Beta v2.4.334

## 🇹🇷 Türkçe

### 🛡️ AniList Servis Kesintisi Koruması, Hızlandırma & Hata Sınıflandırması
- **Yeni Resmi Platform Hata Kodu (`KTS-ANL-2008`):**
  - AniList sunucuları kararlılık sorunları nedeniyle GraphQL API'sini geçici olarak kapattığında (`HTTP 403/503 + "temporarily disabled"`), ekranlarda belirsiz `KTS-COM-1020 Sınıflandırılamayan hata` veya yanıltıcı `HTTP 403 Erişim izni yok / VPN deneyin (KTS-COM-1008)` yerine doğrudan **`KTS-ANL-2008 — AniList API geçici olarak devre dışı`** açıklaması gösterilir.
  - `PlatformFailureRegistry` içinde üst katmandaki boş/sarmalanmış hataların ağ katmanının kaydettiği somut platform hatasını `COM_1020` ile ezmesi engellendi.
- **3x Hızlı Uyarlanabilir AniList Katmanı & Devre Kesici (`KitsugiApiBase.kt`, `KitsugiHttpClient.kt`):**
  - Tüm uygulamayı tek sıraya dizen bloklayıcı `Mutex` + `RetryInterceptor` çarpanı kaldırıldı; yerine `RetryInterceptor`'sız, 15 sn zaman tavanlı `aniListClient`, 3 eşzamanlı izinli `Semaphore` ve `X-RateLimit-Limit` / `X-RateLimit-Remaining` başlıklarına duyarlı uyarlanabilir hız ayarı getirildi.
  - Sunucu şalteri (`temporarily disabled`) ve ardışık gateway çökmeleri için otomatik devre kesici (circuit breaker) eklendi; kapalı sunucuya boşuna onlarca istek atılması durduruldu.
- **Keşfet, Takvim, Profil ve Medya Detay Yedek Zincirleri:**
  - Keşfet toplu raf sorguları paralel hale getirildi; AniList kesintisinde 14 tekil rafa düşüp kuyruğu tıkaması önlendi.
  - Medya detay sayfasında (`KitsugiDetailClient.kt`) AniList çöktüğünde hem Anime hem Manga/Manhwa yapımları için otomatik MAL/Jikan ve Kitsu başlık/kimlik yedek zinciri devreye alındı.

---

## 🇬🇧 English

### 🛡️ AniList Outage Resilience, Adaptive Throughput & Error Classification
- **Dedicated Platform Error Code (`KTS-ANL-2008`):**
  - Classifies AniList's emergency API shutdown (`HTTP 403/503 + "temporarily disabled due to severe stability issues"`) as **`KTS-ANL-2008 — AniList API temporarily disabled`** across Notifications, Explore, Media Detail, and Profile instead of `KTS-COM-1020 Unclassified error` or misleading `HTTP 403 Permission denied (KTS-COM-1008)`.
  - Prevents empty/wrapped upper-layer reports in `PlatformFailureRegistry` from overwriting fresh concrete platform error codes with `COM_1020`.
- **Non-Blocking Adaptive Pacing & Circuit Breakers (`KitsugiApiBase.kt`, `KitsugiHttpClient.kt`):**
  - Replaced the global blocking `Mutex` and double retry loop with a dedicated `aniListClient` (bounded 15s `callTimeout`, no `RetryInterceptor` amplification), `Semaphore(3)` concurrency, header-aware pacing (`X-RateLimit-Limit` / `X-RateLimit-Remaining`), and automatic circuit breakers for server outages and gateway failures.
- **Explore, Calendar, Profile & Detail Fallbacks:**
  - Parallelized Explore batch shelf queries and prevented cascading into 14 single-shelf retries during outages.
  - Added full cross-ID and strict title-verified MAL/Jikan + Kitsu fallback chains for both Anime and Manga/Manhwa detail pages (`KitsugiDetailClient.kt`).
