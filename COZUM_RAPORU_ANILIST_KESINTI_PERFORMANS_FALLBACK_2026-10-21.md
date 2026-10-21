# Çözüm Raporu — AniList Kesinti Sınıflandırması (`KTS-ANL-2008`), Ağ Hızlandırma & Yedek Zincirleri

**Tarih:** 2026-10-21  
**Sürüm:** v2.4.335  
**Plan / Task dosyası:** `PLAN_TASK_ANILIST_KESINTI_PERFORMANS_FALLBACK_2026-10-21.md`

---

## 1. Özet

AniList sunucuları acil kararlılık şalterini indirdiğinde (`HTTP 403/503` + `"The AniList API has been temporarily disabled due to severe stability issues."`) veya yavaşladığında uygulamada aynı anda üç yapısal problem tetikleniyordu:

1. **Yanlış / Silinen Hata Kodları:** `AniListServiceDownException` platform hata kataloğunda yer almıyor, üst katmandaki boş istisnalar `PlatformFailureRegistry` kaydını `KTS-COM-1020` ile eziyor, Profil ekranı ise `HTTP 403` kodunu kullanıcı yetki/VPN engeli (`KTS-COM-1008` / `PERMISSION`) olarak yorumluyordu.
2. **Kendi Kendini Kilitleyen İstek Fırtınası:** `KitsugiApiBase.executeAniListQuery` tüm uygulamayı tek bir global `Mutex` içinde sıraya diziyor, `RetryInterceptor` + iç döngü ile hatalı her çağrıyı `12` kata kadar tekrarlıyor ve `AniListSearchClient.aniListExploreShelves` 3 toplu sorgu başarısız olunca 14 ayrı tekil sorguya düşerek tek bir ekran açılışında yüzlerce seri deneme başlatıyordu.
3. **Eksik Detay Yedek Zinciri:** `KitsugiDetailClient` içinde `getByAniListId` çağrısı ofsetli kimlik gönderdiği için yerel önbelleği ıskalıyor ve `MediaType.Manga` için hiçbir başlık/kimlik yedek dalı bulunmuyordu.

---

## 2. Uygulanan Teknik Çözümler

1. **`KTS-ANL-2008` Resmi Platform Hata Kodu ve Ezilmez Kayıt Defteri:**
   - `error_table.py`, `PlatformErrorCode.kt`, `PlatformErrorCatalog.kt`, `strings.xml` (TR/EN) ve `ProfileErrorFormatter.kt` güncellendi.
   - `PlatformFailureRegistry.report` içinde taze somut bir platform hatası varken arkasından gelen boş/`COM_1020` bildiriminin mevcut kaydı ezmesi önlendi.
2. **`KitsugiHttpClient.aniListClient` + `Semaphore(3)` + Uyarlanabilir Hız Bütçesi + Devre Kesici:**
   - `RetryInterceptor`'sız, 15 sn üst zaman tavanlı `aniListClient` oluşturuldu.
   - Global `Mutex` yerine `<1ms` atomik slot rezervasyonu ve 3 eşzamanlı izinli `Semaphore` getirildi; `X-RateLimit-Limit` / `X-RateLimit-Remaining` başlıklarına göre normalde `350ms`, kısıtlı modda `1500ms` aralık uygulanıyor.
   - Sunucu şalteri algılandığında **45 sn**, ardışık 2 gateway/timeout hatasında **20 sn** boyunca tüm çağıranlar `0ms`'de fail-fast dönüyor.
3. **Keşfet, Bildirimler, Takvim, Profil ve Detay Yedek Zincirleri:**
   - Keşfet toplu raf sorguları paralel hale getirildi ve kesintide 14 tekil sorgu fırtınası kesildi.
   - `KitsugiDetailClient` hem `Anime` hem `Manga` için çapraz ID (`MAL`/`Kitsu`) ve sıkı başlık doğrulamalı **MAL/Jikan → Kitsu** yedek zincirine kavuşturuldu.
   - Tüm doğrudan `graphql.anilist.co` istemcileri (`KitsugiProfileViewModel`, `KitsugiUserProfileViewModel`, `AniListSyncManager`, `AniListImportManager`, `NotificationDiagnostics`) yeni `aniListClient` ve ortak kesinti algılayıcısına bağlandı.
