# Kitsugi — "Video Eklentilerinde İsim Araması Sıfır Dönüyor" Kök Neden & Fix Raporu

**Tarih:** 2026-10-11
**Sürüm adayı:** v2.4.240 (temel: v2.4.239 / `8478940`)
**Dal:** `arena/f7b6058d-kitsugi-beta`

**Şikâyet (kullanıcı):** *"İsim araması ile hiçbir video eklentisi çalışmıyor; sonuç
döndürmeden kapanıyorlar. Sadece ID destekli kaynaklar (Torrentio, Anizium) çalışıyor.
Eklenti veri bulma sayfasında ve detay sayfasındaki **İzle / Bölümler** akışında veri
gelmiyor."*

---

## 0. Teşhis özeti (neden tam olarak bu tablo görülüyor?)

Arama motorunun iki ayrı yolu var:

| Yol | Kim kullanıyor | Sonuç |
|---|---|---|
| `tryNativeIdResolution()` → `getLoadUrl(id)` | IMDb/TMDB/MAL/AniList **ID**'si olan kaynaklar (Torrentio, Anizium, Sinezy…) | ✅ çalışıyordu |
| `safeSearch(api, başlık)` → `api.search()` → `safeLoad()` → `loadLinks()` | **isimle arama yapan 1300+ eklenti** | ❌ hepsi sıfır |

Yani şikâyet "bazı siteler ölü" değil, **isim aramasının kendisi** bir darboğazda
boğuluyordu. Kodda bu yolu kitleyen beş ayrı kusur bulundu; hiçbiri tek başına bu kadar
kapsamlı değildi, **birlikte** hepsini sıfırlıyorlardı.

---

## 1. Kök nedenler (satır satır kod kanıtıyla)

### KN-A (KRİTİK) — `safeSearch`/`safeLoad` CF sabır bütçesini hiç görmüyordu
`CF_STAGE_TIMEOUT_MS` sabitinin kendi dokümantasyonu:

> `/** CF şüpheli hostlarda aşama bazlı sabır süresi (120 sn) — safeSearch / safeLoad / loadLinks */`

Ama koda yalnızca **loadLinks** bağlanmıştı (`stageTimeoutMs(api, 25_000L)`).
`safeSearch` ve `safeLoad` **sabit `withTimeoutOrNull(15_000L)`** kullanıyordu.

Cloudflare / Turnstile / vDDoS çözümü (`CloudflareKiller` → `WebViewResolver`) tek başına
**90-120 sn** sürüyor (`CF_SOLVE_BUDGET_MS = 120_000L`). Yani:

```
eklenti.search() → ağ isteği → 403 challenge → WebView çözüm başlar (90 sn)
                                 ↑
                    15. saniyede arama kutusu patlar → null → `?: emptyList()`
```

Kutu patlayınca **çözüm de iptal edilir** → çerez asla kaydedilemez → sonraki 17 varyantın
her biri aynı 15 sn'lik duvara çarpar. Sonuç: **kalıcı, geri dönüşsüz "0 sonuç"**.
ID yolu aramaya hiç girmediği için bundan etkilenmiyordu — kullanıcının
"Torrentio ve Anizium çalışıyor, gerisi yok" demesinin sebebi budur.

Ek kusur: `?: emptyList()` yüzünden **zaman aşımı ile "sitede gerçekten içerik yok"
ayırt edilemiyordu**; ne `CsTrace`e ne `CsPluginStatusTracker`a hiçbir şey yazılıyordu.
Kullanıcı "eklenti sonuç döndürmeden kapanıyor" diye tarif ediyor çünkü tam olarak öyle.

**Fix:** `SEARCH_STAGE_TIMEOUT_MS` / `LOAD_STAGE_TIMEOUT_MS` (taban 15 sn) artık
`stageTimeoutMs()` üzerinden CF kanıtlı hostlarda 120 sn'ye genişliyor; timeout durumunda
`Log.e(SERR)` + `CsTrace.warn("search-timeout"/"load-timeout")` + `recordSkip` yazılıyor.
`load` tarafında `null` iki anlam taşıdığı için süre ölçümüyle ayrıştırıldı.

### KN-B (KRİTİK) — `DdosGuardKiller` **küresel** bir kilit + önbelleksiz probe taşıyordu
`CsRuntimeInit`, scraper OkHttp istemcisine `DdosGuardKiller(alwaysBypass = false)` ekliyor;
yani **tüm eklentilerin tüm istekleri** bu interceptor'dan geçiyor. Eski gövde:

```kotlin
val response = chain.proceed(request)
if (response.code == 403) { response.close(); getOrFetchCookies(request) … }
// getOrFetchCookies:
return bypassLock.withLock {                 // ← TEK global ReentrantLock
    runBlocking {                            // ← timeout YOK
        app.get("https://check.ddos-guard.net/check.js")   // ← bloklayan ağ isteği
        Requests().get(bypassUrl).cookies
    }                                        // ← başarısızlık HİÇBİR YERE not edilmiyor
}
```

403, bot koruması olan Türk sitelerinde **sıradan** bir yanıttır (Cloudflare/vDDoS da 403
döner) ve DDoS-Guard ile ilgisi olmayabilir. Yine de her 403, tüm istemcileri o tek kilidin
arkasına dizecek bir `check.ddos-guard.net` turu başlatıyordu; `skipHosts` diye bir şey
olmadığı için **aynı ölü probe her istekte tekrar** deneniyordu. 24-32 paralel aramanın
her biri 15 sn'lik kutusunda bu beklemeyle ölüyordu (bkz. KN-A ile ölümcül ikili).

**Fix (üç katman):**
1. **Kanıt yoksa deneme yok:** `Server: ddos-guard` başlığı, `Set-Cookie: __ddg*` veya
   gövdede `ddos-guard`/`__ddg` imzası yoksa yanıt **OLDUĞU GİBİ** döner — tek bir ekstra
   istek bile atılmaz.
2. **Negatif önbellek:** kanıtsız 403 ve başarısız probe host'u oturum boyunca `skipHosts`'a
   yazar → bir daha denenmez.
3. **Host başına kilit + 8 sn tavan:** `bypassLock` (global) → `lockFor(host)`; probe
   `withTimeout(PROBE_BUDGET_MS = 8 sn)` içinde ve `Dispatchers.IO`'ya devredilmiş.
   Çerezler artık instance alanı değil, companion düzeyinde paylaşılıyor (CloudflareKiller ile aynı desen).

`alwaysBypass = true` kullanımı (bilinçli çağrılar) eski davranışı korur.

### KN-C (YÜKSEK) — CF clearance gate'i akışa hiç girmiyordu
`runCfGateIfNeeded(api)` çağrıldıktan sonra arama, çerez **varsa** hızlı gider. Ama gate'i
çağıran tek yer `searchWithCfRetry`/`ensureCfClearance` idi ve bunları yalnızca
`AddonExploreDialog` / `AddonExploreInline` kullanıyordu. Şikâyetçi olunan iki yüzey —
detay sayfası **İzle/Bölümler** (`runGetStreams`) ve **eklenti arama** sayfaları —
`safeSearch`'i çıplak çağırıyordu: çerez kabı boş, ilk istek challenge'a çarpıyor.

**Fix:** gate artık `safeSearch` içinde, **arama zaman kutusunun dışında** ve
eklenti başına oturumda **bir kez** koşuyor (`runSearchGateOnce`, bütçe
`min(CF_GATE_BUDGET_MS, SEARCH_GATE_BUDGET_MS = 40 sn)`). Sağlıklı hostlarda
`cfGateNeeded()` false döner → **0 ms** ek maliyet, tek istek bile yok.

### KN-D (YÜKSEK) — Eklenti→API eşleştirmesi dört ayrı yerde dört farklı yazılmıştı
`searchAllAddons` `lowercase(Locale.ROOT)` kullanıyordu; `AddonSearchDialog` ve
`PluginPickerBottomSheet` **hiç küçültmüyordu**:

```kotlin
val pluginId = java.io.File(api.sourcePlugin ?: "").nameWithoutExtension
enabledIds.contains(pluginId)            // id = DB'deki "WFilmİzle", dosya = "wfilmizle"
```

Türkçe noktalı İ/ş/ğ ve büyük/küçük harf farklarında filtre **boş liste** döndürüyor →
"arama tek eklenti bile denemeden sonuç yok" (ürünün "eklenti veri bulma sayfası"
şikâyetinin ikinci ayağı).

**Fix:** tek normalize edilmiş kapı — `CsStreamRunner.isApiEnabledFor(api, enabledIds)`
(mevcut `normalizePluginKey` ile ASCII'ye indirger; hem dosya adını hem `api.name`'i
dener). Dört çağrı noktası da buna bağlandı.

### KN-D-2 (ORTA) — Oturum blokları yeni aramada temizlenmiyordu
`CsPluginStatusTracker` 3 hatada eklentiyi **oturum boyunca** bloklar
(`isBlocked → return emptyList()`, sessiz). `StreamViewModel.startFetch` bunu
`clear()` ile sıfırlıyordu; eklenti arama sayfaları ve `searchAllAddons` sıfırlamıyordu →
bir önceki denemede 3 hataya düşen eklenti sonsuza dek "Engellendi — atlanıyor".
Artık her üç yüzey de `CsPluginStatusTracker.clear()` +
`clearUnsupportedMethodsCache()` ile başlıyor.

### KN-K (ORTA) — Arama bütçesi, kuyrukta beklerken eriyordu
`searchStartMs` duvar saatiyle ölçüldüğü için, `searchSemaphore` kuyruğunda izin bekleyen
bir eklenti **daha tek sorgu atamadan** 20 sn'lik bütçesini tüketebiliyordu
(v2.4.226'da 12→24 raise edilmişti ama semptom yalnızca hafiflemişti).

**Fix:** kuyruk bekleme süresi artık ölçülüyor (`searchQueueWaitMs` sayacı) ve
`runGetStreams`'taki **tüm** bütçe kontrollerinden düşülüyor (`searchElapsedMs()`);
kapı 24 → 32'ye çıkarıldı (KN-A sonrası bir arama 8 kata kadar uzun sürebiliyor).

### KN-T (AYNI KÖK, ÖRNEK) — TrAnimeci'nin özel WebView araması asla bitemiyordu
`safeSearch` içindeki TrAnimeci yolu `latch.await(45, SECONDS)` ile WebView DOM taramasını
bekliyor; bu bekleme **15 sn'lik arama kutusunun İÇİNDEydi**. Yani 45 saniyelik sabır
penceresine 15 saniyelik bir tünel konmuştu — bu yol yapısal olarak her zaman
`"DOM scraping başarısız"` / boş sonuç üretiyordu. KN-A düzeltmesi CF korumalı eklenti
listesindeki adlar için kutuyu 120 sn'ye çıkardığından bu yol ilk kez gerçekten çalışabilir.

### KN-X (BLOKLAYICI) — HEAD derlenmiyordu
`CsStreamRunner.searchAllAddons` içinde:

```kotlin
val pluginId = java.io.File(api.sourcePlugin).nameWithoutExtension.lowercase(Locale.ROOT)
```

`MainAPI.sourcePlugin` **`String?`** (`library-android-1.0.2-local` kaynaklarında
`var sourcePlugin: String? = null`) → `java.io.File(String?)` **type mismatch**, yani
`searchAllAddons`'ı içeren derleme birimi baştan kırık. KN-D düzeltmesi bu satırı
`isApiEnabledFor` ile değiştirdiği için hata da kalktı. Önceki raporların
"derlenemedi, cihazda test edilemedi" notu ile uyumlu: bu satır hiçbir zaman
cihazda test edilememiş.

---

## 2. Değişen dosyalar

| Dosya | Değişiklik |
|---|---|
| `data/cloudstream/CsStreamRunner.kt` | `SEARCH/LOAD_STAGE_TIMEOUT_MS` + `SEARCH_GATE_BUDGET_MS` + `cfGateTriedForPlugins`; `runSearchGateOnce()`; `isApiEnabledFor()`; `searchQueueWaitMs` sayacı ve `searchElapsedMs()` bütçe düzeltmesi; `searchSemaphore` 24→32; `clearUnsupportedMethodsCache()` gate kaydını da temizler; `safeSearch`/`safeLoad` CF-aware aşama süresi + timeout izi; `searchAllAddons` normalize eşleşme + oturum temizliği |
| `com/lagradost/cloudstream3/network/DdosGuardKiller.kt` | Kanıt bazlı etkinleşme, `skipHosts` negatif önbelleği, host başına kilit, 8 sn probe tavanı, `Dispatchers.IO`'ya devir, paylaşılan çerez haritası, `clearSessionState()` |
| `ui/screens/search/components/AddonSearchDialog.kt` | `isApiEnabledFor` + oturum temizliği + `searchWithCfRetry` |
| `ui/screens/search/components/PluginPickerBottomSheet.kt` | aynı üçlü + normalize sıralama |
| `ui/screens/search/PluginPickerScreen.kt` | `isApiEnabledFor` + normalize sıralama |
| `app/src/test/…/CsStreamRunnerLogicTest.kt` | KN-D için 5 yeni JVM testi (`matchesEnabledPlugin`) |
| `app/build.gradle.kts` | `appVersionName` 2.4.239 → **2.4.240** |

## 3. Doğrulama

- `tree-sitter-kotlin` ile **9 dosyanın tamamı SYNTAX OK** (ERROR/MISSING düğümü yok);
  aynı denetleyici bozuk bir örnek üzerinde hatayı yakalıyor (negatif kontrol geçti).
- Yeni/elle dokunan her bölge satır satır geri okundu; `?:`/elvis bağlama, `withPermit`
  ve `withTimeoutOrNull` kapanışları, `withLock` dönüş tipleri ve companion/instance
  erişimleri denetlendi.
- `library-android-1.0.2-local` **sources.jar** çıkarılarak `MainAPI` yüzeyi doğrulandı:
  `search(query,page): SearchResponseList?` temel uygulama `search(query)`'e delege ediyor,
  `search(query)` → `NotImplementedError`, `sourcePlugin: String?`. Bu, hem fallback
  zincirinin (`(q,1) → (q) → quickSearch`) hem KN-X'in hem de `SearchResponseList` ↔
  `SearchResult` ABI iddiasının (paketli `.dex` dosyalarında `SearchResponseList` referansı
  var, `SearchResult` yok → uyumsuzluk **yok**) doğrulanmasını sağladı.
- KN-D için JVM unit testleri eklendi (`CsStreamRunnerLogicTest`): `matchesEnabledPlugin`
  Türkçe noktalı İ/ş/ğ, büyük/küçük harf, eksik `sourcePlugin` ve boş girdi senaryolarını
  kapsıyor (5 test). Saf çekirdek, `isApiEnabledFor`'den ayrılarak Android bağımlılığı
  olmadan test edilebilir hâle getirildi.
- **Yapılamayan:** bu ortamda JDK/Android SDK/Maven erişimi yok → Kotlin derlenemedi;
  Türkçe sitelere ağ erişimi yok → canlı arama test edilemedi.

## 4. Cihazda doğrulama planı

1. `adb logcat -s CsStreamRunner CS_SEARCH_ERR DdosGuardKiller CsCfWarmup PLUGIN_DIAG`
2. Aynı içerikle (örn. Supernatural S01E01 veya Türkçe bir dizi) **İzle**'ye bas:
   - `⏱️ SEARCH TIMEOUT` satırları **azalmalı**; CF'li kaynaklarda önce
     `🔐 safeSearch öncesi CF gate:` , ardından `search('…') → N sonuç` (N>0) gelmeli.
   - `[host] 403 yanıtı DDoS-Guard imzası taşımıyor — bypass atlandı.` satırını
     **bir kez** görmelisin (eskiden her istekte probe dönüyordu).
   - `⏱️ … kuyruk` ifadelerinin sayısı düşmeli (kapı 32 + bütçe düzeltmesi).
3. Eklenti arama sayfasında çıplak isimle dene: artık **0 API** durumu görülmemeli
   (`isApiEnabledFor`); bir eklenti engellendiyse kartta gerçek sebep yazmalı.
4. Hâlâ sıfır dönen tek bir eklenti kalırsa bu artık liste/kod sorunu değil, **veri**
   sorunudur: o eklentinin domainini `domain_fixes.json`'a tek girdi olarak eklemek yeter.
5. Kesin tanı için: Ayarlar → Tanılama → Eklenti Canlı Testi (`CsPluginDiagnosticRunner`);
   `search-timeout` / `load-timeout` / `cf-gate` aşamaları artık raporda ayrı satır olarak
   görünür.
