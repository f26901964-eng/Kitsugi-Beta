# Plan / Task — AniList Kesinti Koruması (`KTS-ANL-2008`), Hızlandırma, Devre Kesici & Yedek Zincirleri

**Tarih:** 2026-10-21  
**Sürüm:** v2.4.335  
**Dal:** `arena/5507a1fb-kitsugi-beta`  
**Kapsam:** AniList GraphQL ağ katmanı, Platform Hata Kataloğu (`KTS-ANL-2008`), Bildirimler, Keşfet, Yayın Takvimi, Profil/Senkronizasyon ve Medya Detay (Anime + Manga/Manhwa) yedek zincirleri, MAL web bildirim sayfalama düzeltmesi.  
**İlgili rapor:** `COZUM_RAPORU_ANILIST_KESINTI_PERFORMANS_FALLBACK_2026-10-21.md`  
**Sürüm notları:** `release_notes_v2.4.335.md`, `RELEASE_NOTES.md`

---

## 1. Sorun Haritası (5 Ekran Görüntüsü + Genel Yavaşlama Kök Nedenleri)

| Ekran | Önceki Hatalı Belirti | Kök Neden | Yeni Davranış |
|---|---|---|---|
| **1. Bildirimler — AniList** | `KTS-COM-1020 — Sınıflandırılamayan hata` | `AniListServiceDownException` katalogda tanımlı değildi; `fetchNotifications` `null` dönüp üst katman `COM_1020` ile somut hatayı eziyordu | **`KTS-ANL-2008 — AniList API geçici olarak devre dışı`** anında gösterilir; 45 sn devre kesici kapalı sunucuya tekrar yüklenmez |
| **2. Keşfet — Tümü (AniList şeridi)** | `KTS-COM-1020 — Sınıflandırılamayan hata` | `aniListExploreShelves` 3 toplu sorgu + 14 tekil sorguyu yutup boş harita dönüyor, `ExploreViewModel` boş veriyi `RuntimeException` ile atıp `COM_1020` üretiyordu | İlk parça `AniListServiceDownException` aldığında 14 tekil istek fırtınası durdurulur; `"🌐 Tümü"` modunda doğrudan **`KTS-ANL-2008`**, tekil AniList modunda anında **Kitsu** yedeği açılır |
| **3. Keşfet — Yayın Takvimi** | Sadece Kitsu/Jikan verisi görünüyor, sessizce eksik kalıyordu | `fetchAiringSchedule` istisnayı yutup boş liste döndürüyor ve boş sonucu önbelleğe yazıyordu | Kesinti anında boş sonuç önbelleğe yazılmaz; `failOnFirstPageError = true` ile çağıran katman gerçek kesinti durumunu bilir |
| **4. Medya Detay (`OO Kouhai` vb.)** | `KTS-COM-1020 — Sınıflandırılamayan hata` (Detay açılmıyor) | (a) `fetchDetail` yakalanmamış istisna fırlatıp Step 3/4 yedeklerini atlıyordu; (b) `mediaMetaCacheDao().getByAniListId(extId)` ham ID yerine `+100_000_000` ofsetli ID ile çağrılıyordu; (c) **`MediaType.Manga` için hiçbir yedek dalı yoktu!** | `MediaType.Anime` VE `MediaType.Manga` için çapraz ID (`MAL`/`Kitsu`) + sıkı başlık doğrulamalı **MAL/Jikan → Kitsu** yedek zinciriyle detay sayfası kesintisiz açılır; tüm yedekler de başarısızsa gerçek hata kodu (`KTS-ANL-2008`) korunur |
| **5. Profil — AniList** | `HTTP 403 — Erişim izni yok / VPN deneyin / KTS-COM-1008` | `KitsugiProfileViewModel` ham JSON gövdesini atıp sadece `HTTP 403` fırlatıyor, `ProfileErrorFormatter` bunu kullanıcı yetki/bölge engeli (`PERMISSION`) sanıyordu | Gövdedeki `"temporarily disabled due to severe stability issues"` mesajı yakalanır; **`ProfileErrorKind.SERVER`** + **`KTS-ANL-2008`** ile sorunun hesapta/VPN'de değil AniList sunucularında olduğu açıkça bildirilir |

---

## 2. Görev Listesi (Tamamlandı)

### T1 — Resmi Platform Hata Kodu (`KTS-ANL-2008`) ve Sınıflandırıcı
- [x] `scripts/platform_error_codes/error_table.py` ve `PlatformErrorCode.kt`: `ANL_2008` (`KTS-ANL-2008`) eklendi (`generate.py` + yerelleştirme eşlik kontrolü: **65 hata kodu, 17.634 TR/EN string senkron**).
- [x] `app/src/main/res/values/strings.xml` ve `values-en/strings.xml`: `pf_err_anl_2008_title`, `pf_err_anl_2008_why`, `pf_err_anl_2008_fix` ve `pf_err_anl_2008_profile_short` metinleri eklendi.
- [x] `PlatformErrorCatalog.kt`: `isAniListServiceDownText` ile hem İngilizce (`temporarily disabled`, `stability issues`) hem Türkçe kesinti mesajları `HTTP 403` ve `GENERIC` platform bağlamında bile **`ANL_2008`** olarak sınıflandırıldı; `500..599` HTTP hataları `ANL_2003` ile eşleştirildi; `conciseBilingual(ANL_2008)` eklendi.
- [x] `PlatformFailureRegistry.kt`: Ağ katmanının kaydettiği taze somut hata kodunun (`ANL_2008`, `ANL_2001`, vb.), üst katmanın `null` veya belirsiz sarmalayıcı hatasıyla `COM_1020` tarafından ezilmesi engellendi.
- [x] `ProfileErrorFormatter.kt`: AniList geçici API kapatma mesajlarının `statusCode == 403` kontrolünden **önce** `ProfileErrorKind.SERVER` olarak sınıflandırılması sağlandı.

### T2 — Hızlı, Bloklamayan `aniListClient`, Uyarlanabilir Hız Bütçesi ve Devre Kesici
- [x] `KitsugiHttpClient.kt`: `RetryInterceptor` içermeyen ve sıkı zaman tavanlarına sahip (`10s connect / 12s read / 12s write / 15s callTimeout`) özel **`aniListClient`** eklendi (çift katmanlı `4 × 3 = 12` yeniden deneme çarpanı tamamen kaldırıldı).
- [x] `KitsugiApiBase.kt`:
  - Tüm uygulamayı tek sıraya dizip ağ G/Ç bitene kadar kilitleyen global `aniListMutex` kaldırıldı; yerine `<1ms` atomik slot rezervasyonu + **`Semaphore(3)`** eşzamanlılık getirildi.
  - Yanıttaki `X-RateLimit-Limit` / `X-RateLimit-Remaining` başlıkları okunarak normal modda **350 ms**, AniList "degraded mode"a (`limit <= 35` veya `remaining <= 5`) indiğinde otomatik **1.500 ms** aralık uygulanması sağlandı.
  - Tüm HTTP kodlarında (`403`, `503`, `500`, `200 + errors[]`) sunucu şalteri algılandığında **45 sn `anilistServiceDownUntil` devre kesicisi** ve ardışık 2 ağ/gateway hatasında **20 sn `anilistGatewayDownUntil` devre kesicisi** devreye alındı.
  - `extractAniListServiceDownMessage` hem Android çalışma zamanında hem saf JVM birim testlerinde birebir çalışan `kotlinx.serialization.json.Json` ayrıştırıcısıyla yazıldı.
  - `throwOnServiceDown: Boolean = false` parametresi ile mevcut 35 `?: return null` çağrı noktasında yakalanmamış istisna çökmesi sıfırlandı; kesintiyi özel ele alan istemcilerde `throwOnServiceDown = true` aktif edildi.

### T3 — Keşfet, Bildirimler ve Yayın Takvimi İyileştirmeleri
- [x] `AniListSearchClient.kt`: `aniListExploreShelves` içindeki 3 toplu GraphQL parça sorgusu `coroutineScope + async + awaitAll` ile paralel hale getirildi; sunucu kapalı/429/gateway kesintisinde 14 ayrı tekil sorgu fırtınası başlatması durduruldu (`216` seri deneme → **1 hızlı deneme + anında devre kesici**).
- [x] `ExploreViewModel.kt`: `"🌐 Tümü"` modunda `serviceError` yutulmadan üst katmana iletilerek `KTS-ANL-2008` gösterilmesi, tekil AniList modunda ise anında Kitsu yedeğine düşülmesi sağlandı.
- [x] `KitsugiAniListNotificationClient.kt`: `throwOnServiceDown = true` ile `AniListServiceDownException` ve `KTS-ANL-2008` koruması bağlandı.
- [x] `KitsugiAiringCalendarClient.kt`: Kesinti anında boş gelecek takvim verisinin önbelleğe yazılması engellendi ve ilk sayfa hatası korundu.
- [x] `MalNotificationsClient.kt`: Başlangıç HTML akışında 20'den az öğe geldiğinde devam sayfalamasının kesilmesine yol açan `items.size >= PAGE_SIZE` regresyonu düzeltilerek `items.isNotEmpty()` davranışı geri yüklendi.

### T4 — Medya Detay (`KitsugiDetailClient.kt`) Anime + Manga/Manhwa Tam Yedek Zinciri
- [x] `mediaMetaCacheDao().getByAniListId(aniListId)` çağrısında `+100_000_000` ofsetli `extId` yerine ham `aniListId` kullanılması düzeltildi.
- [x] Birincil `"anilist"` detayı `null` döndüğünde önce `realMalId` / `KitsugiIdResolver` üzerinden **MAL/Jikan** ve **Kitsu** (`KitsuIdNamespace.stableIdFromRaw`) kimlikli yedeklerin denenmesi eklendi.
- [x] Step 3 başlık bazlı yedek zincirine **`MediaType.Manga`** dalı eklendi: önce sıkı başlık doğrulamalı **MAL Manga**, o da yoksa sıkı başlık doğrulamalı **Kitsu Manga** detayı yüklenir (`OO Kouhai` dahil tüm manga/manhwa kartları AniList kapalıyken bile açılır).

### T5 — Doğrudan `graphql.anilist.co` Çağıran Sınıflar (Profil, Senkronizasyon, İçe Aktarma, Tanılama)
- [x] `KitsugiProfileViewModel.kt`, `KitsugiUserProfileViewModel.kt`, `AniListSyncManager.kt`, `AniListImportManager.kt` ve `NotificationDiagnostics.kt`: tamamı `KitsugiHttpClient.aniListClient` + `extractAniListServiceDownMessage` / `recordAniListServiceDown` hattına geçirildi.

### T6 — Birim Testleri, CI ve Paketleme
- [x] `PlatformFailureRegistryTest.kt`: `ANL_2008` sınıflandırması (HTTP 403 + generic platform), `COM_1020` ezmeme garantisi ve GraphQL kesinti JSON ayrıştırma testleri eklendi.
- [x] `ProfileErrorFormatterTest.kt`: AniList geçici API kapatma mesajlarının `PERMISSION` yerine `SERVER` olarak sınıflandırıldığı doğrulandı.
- [x] `.github/workflows/android-build.yml`: `data.platform.*` ve `ui.screens.profile.*` birim test paketleri CI doğrulamasına dahil edildi.
- [x] Sürüm `v2.4.335` (`app/build.gradle.kts`, `release_notes_v2.4.335.md`, `RELEASE_NOTES.md`, `DEGISIKLIKLER_v2.4.335.diff`) olarak paketlendi (`v2.4.334` video oynatıcı paketiyle sıfır çakışma).

---

## 3. Paketteki Dosyalar

```text
.github/workflows/android-build.yml
app/build.gradle.kts
app/src/main/java/com/kitsugi/animelist/core/network/KitsugiHttpClient.kt
app/src/main/java/com/kitsugi/animelist/data/auth/AniListImportManager.kt
app/src/main/java/com/kitsugi/animelist/data/auth/AniListSyncManager.kt
app/src/main/java/com/kitsugi/animelist/data/notifications/NotificationDiagnostics.kt
app/src/main/java/com/kitsugi/animelist/data/platform/PlatformErrorCatalog.kt
app/src/main/java/com/kitsugi/animelist/data/platform/PlatformErrorCode.kt
app/src/main/java/com/kitsugi/animelist/data/platform/PlatformFailureRegistry.kt
app/src/main/java/com/kitsugi/animelist/data/remote/AniListSearchClient.kt
app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiAiringCalendarClient.kt
app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiAniListNotificationClient.kt
app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiApiBase.kt
app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiDetailClient.kt
app/src/main/java/com/kitsugi/animelist/data/remote/malweb/MalNotificationsClient.kt
app/src/main/java/com/kitsugi/animelist/ui/app/KitsugiProfileViewModel.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/explore/ExploreViewModel.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/profile/KitsugiUserProfileViewModel.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/profile/ProfileErrorFormatter.kt
app/src/main/res/values-en/strings.xml
app/src/main/res/values/strings.xml
app/src/test/java/com/kitsugi/animelist/data/platform/PlatformFailureRegistryTest.kt
app/src/test/java/com/kitsugi/animelist/ui/screens/profile/ProfileErrorFormatterTest.kt
scripts/platform_error_codes/error_table.py
RELEASE_NOTES.md
release_notes_v2.4.335.md
PLAN_TASK_ANILIST_KESINTI_PERFORMANS_FALLBACK_2026-10-21.md
COZUM_RAPORU_ANILIST_KESINTI_PERFORMANS_FALLBACK_2026-10-21.md
DEGISIKLIKLER_v2.4.335.diff
```

---

## 4. Yerel Test Komutu

```bash
./gradlew :app:testFossDebugUnitTest \
  --tests 'com.kitsugi.animelist.data.platform.*' \
  --tests 'com.kitsugi.animelist.ui.screens.profile.*' \
  --tests 'com.kitsugi.animelist.ui.screens.explore.AllSourcesExploreTest' \
  --tests 'com.kitsugi.animelist.data.remote.malweb.*' \
  :app:assembleFossDebug
```
