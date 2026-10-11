# PLAN/TASK — Cloudflare Sabır Bütçesi + Otomatik "Arama → Sezon Barı → Bölüm" Zinciri (v2.4.239)

Tarih: 2026-10-11
Dal: `arena/18e5161f-kitsugi-beta`
Durum: Uygulandı (derleme doğrulaması cihaz/CI'da yapılacak — sandbox'ta Android SDK yok)

---

## 1. Kullanıcının istediği akış (ekran görüntüleriyle birebir)

| Adım | Ekran görüntüsü | Uygulamadaki karşılığı |
|------|-----------------|------------------------|
| 0 | **Eklenti Portalı** (192 eklenti) → örn. `Anizium (https://api.anizium.co)` seçili | `CsPluginLoader` + `APIHolder.allProviders` |
| 1 | Eklenti içi arama kutusuna **çıplak isim**: `mushoku tensei` | `runGetStreams` → `buildPlainTitleVariants` → `safeSearch` (sezon/bölüm sorguya KARIŞMAZ) |
| 2 | Sonuç kartı: `Mushoku Tensei: Jobless Reincarnation` | `findBestMatch` + `CsTitleMatcher.isHighConfidenceMatch` |
| 3 | Detay sayfası → **üst sezon barı**: `1. Sezon / 2. Sezon / 3. Sezon` | `AnimeLoadResponse.episodes` sezon kovaları + `navigateToSeasonPage` (yeni: sezon-jetonlu URL doğrudan yüklemesi) |
| 4 | **Alt bölüm listesi**: `Bölümler (24)` → `1. Bölüm`, `2. Bölüm` … | `findEpisodeData` → `extractStreamsFromEpisode` → `loadLinks` |

İstek: bu zincir **tek otomatik hareket** gibi çalışsın; aradaki Cloudflare bekleme süreleri
kullanıcıdan/akıştan gizlensin ("kaynak bunları otomatik yapıyormuş gibi") ve Cloudflare
killer'a **en az 90–120 saniye** sabır tanınsın.

---

## 2. Kök neden: süre bütçeleri birbirini boğuyordu

| Katman | ESKİ bütçe | Sorun |
|--------|-----------|-------|
| `CloudflareKiller.trySolveWithSavedCookies` fallback | **10 sn** | Kanıt gelmese bile 10 sn'de "ne bulduysam kabul" → WebView erken kapanıyor, PoW/Turnstile (90–120 sn) hiç çözülemiyordu |
| AAR `WebViewResolver` varsayılan `timeout` | **60 sn** | Interceptor'ın WebView'ı 60 sn'de imha ediliyordu |
| `safeSearch` / `safeLoad` | **15 sn** sabit | OkHttp çağrısı, interceptor'daki WebView çözümü çerez üretemeden IPTAL oluyordu → "arama sıfır" |
| Arama zaman kutusu | CF'de 40 sn | 18 varyant + ağ → kutu doluyor, load/extract aşamasına sıra gelmiyordu |
| `getStreams` provider bütçesi | CF'de **120 sn** | Gate/çözüm tek başına tüketince zincirin son halkaları (sezon/bölüm/loadLinks) kesiliyordu |
| `CsCfWarmupManager` warmup | 15 sn (molystream 25 sn) | Aynı erken-vazgeçme; ayrıca 9 sn'de "herhangi bir çerez" kabulü |
| Başarısız çözüm sonrası cooldown | 90 sn | Erken kapanan çözüm "başarısız" sayılıp host'u 90 sn kilitliyordu → "hiçbir kaynak veri vermiyor" |

Sonuç: CF korumalı hostlarda zincir daha 2. halkada kopuyor, kullanıcı "eklenti çalışmıyor" görüyordu.

---

## 3. Yeni mimari (v2.4.239)

### 3.0 CF Gate — zincirin 0. halkası (`CsCfWarmupManager.ensureClearance`)
- `runGetStreams` ve `getStreamsForUrl` **aramadan ÖNCE** gate'i çağırır.
- Hızlı yol: host'un kayıtlı clearance çerezi varsa veya site "sağlıklı" doğrulanmışsa **0 ms** döner
  → sağlıklı kaynaklara hiçbir gecikme EKLENMEZ.
- Yavaş yol: gizli WebView açılır, meydan okuma **≤90 sn** (`CF_GATE_BUDGET_MS`) içinde çözülür;
  çerezler `CloudflareKiller.savedCookies`'e yazılır ve zincirin TÜM halkaları bunları yeniden kullanır.
- Gate süresi arama zaman kutusundan **düşülmez** (`searchStartMs` gate sonrasında kurulur).
- Aynı anda en fazla 2 gate WebView'ı (`gateSemaphore`) — bellek baskısı sınırlı.
- **Temiz sayfa yoklaması** (`CHALLENGE_PROBE_JS`): sayfa meydan okuma değilse gate anında kapanır
  ve host 30 dk "sağlıklı" işaretlenir. Yani 90 sn'lik sabır yalnızca GERÇEKTEN korumalı hostlarda harcanır.

### 3.1 Sabır kuralı (`CloudflareKiller`)
- `CF_SOLVE_BUDGET_MS = 120_000` (sert üst sınır), `CF_SOLVE_FALLBACK_MIN_MS = 90_000` (asgari sabır).
- Sert kanıt (`cf_clearance`, `__ddg*`) → anında kabul.
- Jenerik waf/session çerezi → ancak **≥15 sn** yerleşme sonrası kabul (meydan okuma sayfaları erken
  çerez dikebiliyor).
- Kanıt yoksa WebView **90 sn açık kalır**; 90 sn'den sonra oturum çerezi son çare kabul edilir;
  120 sn'de WebView sert kapanır. AAR resolver'a `timeout = CF_SOLVE_BUDGET_MS` geçilir (60 sn → 120 sn).
- Challenge görülen hostlar `challengedHosts`'a işlenir; temiz istek geçince temizlenir.
  `isSolvingHost()` / `wasChallenged()` / `hasClearanceFor()` dışa açıldı.

### 3.2 CF farkında aşama bütçeleri (`CsStreamRunner`)
- `CF_STAGE_TIMEOUT_MS = 120_000`: host challenge gördüyse/çözüm sürüyorsa `safeSearch`, `safeLoad`
  ve `loadLinks` 15/25 sn yerine 120 sn sabırla çalışır.
- `CF_PROVIDER_TIMEOUT_MS = 240_000` (90 gate + 60 arama + 15 load + ~75 sezon/bölüm/link).
- CF şüpheli hostlarda arama kutusu 40 sn → **60 sn** (gate artık kutu dışında).
- `cfGateNeeded()`: çerez yok + (CF_PROTECTED listesi | challenge görüldü | warmup listesi | önceki hata WAF imzalı).

### 3.3 "Üst sezon barı" mimarisi
- `extractSeriesStreams` yüklenen sayfanın sezon barını loglar: sunulan sezonlar / istenen SxEy / bölüm sayısı.
- `navigateToSeasonPage` **Adım 0**: detay URL'sindeki sezon jetonunu (`sezon-2`, `season-2`, `/s2/`)
  hedef sezonla değiştirip sayfayı DOĞRUDAN yükler (`buildSeasonUrlCandidates`) — sitedeki sezon
  sekmesine tıklamanın kaynak-bağımsız karşılığı; arama sorgusu harcanmaz.
- Adım 1 (mevcut): sezon-kapsamlı arama sorgularıyla fallback.
- Bölüm listesi dolu ama hedef S/E yoksa → sezon gezinmesi; liste boşsa sayfa-tipli plugin URL fallback'i (değişmedi).

---

## 4. Değişen dosyalar

| Dosya | Değişiklik |
|-------|-----------|
| `app/src/main/java/com/lagradost/cloudstream3/network/CloudflareKiller.kt` | Sabır bütçesi (90/120 sn), challenge durumu, resolver timeout'ı, jenerik çerez yerleşme süresi |
| `app/src/main/java/com/kitsugi/animelist/data/cloudstream/CsCfWarmupManager.kt` | `ensureClearance` gate'i, temiz-sayfa yoklaması, sağlıklı-host önbelleği, başarıda anında teardown, uzun bütçeler |
| `app/src/main/java/com/kitsugi/animelist/data/cloudstream/CsStreamRunner.kt` | Gate çağrıları (arama öncesi), CF farkında aşama/provider bütçeleri, sezon barı doğrudan gezinmesi + logu |
| `app/build.gradle.kts` | `appVersionName` 2.4.238 → 2.4.239 |

---

## 5. Doğrulama planı (cihazda)

1. Logcat filtre: `CsCfWarmup | CloudflareKiller | CsStreamRunner`.
2. CF korumalı host (örn. `tranimaci.com`, `dizipal3008.com`, `asyalog.co`):
   - `CF gate başlıyor` → `Cookie alındı [kanıt|sabırlı son çare]` ≤120 sn.
   - Ardından `✓ '<çıplak başlık>' için N sonuç bulundu` → `📺 Sezon barı: 1,2,3 | istenen: S2E5`.
3. Sağlıklı host (örn. `api.anizium.co`): `Cookie alındı [temiz sayfa]` birkaç saniye içinde; tekrar
   aramada gate logu YOK (sağlıklı önbellek).
4. Bölüm oynatma: sezon barı logunda istenen sezon yoksa `sezon barı URL:` veya `season-nav` izi görünmeli.
5. `CsPluginDiagnosticRunner` ile toplu tanı: CF korumalı eklentilerde "arama sıfır" oranı düşmeli.

## 6. Bilinen riskler

- İlk kullanım CF korumalı hostta kullanıcıya ≤90 sn spinner gösterir (istenen sabır bu); tekrar
  kullanımda çerez önbelleği sayesinde akış hızlanır.
- Temiz-sayfa yoklaması yalnızca ana çerçevede çalışır; yalnızca alt-yolda (örn. sadece `/ara`)
  koruma olan sitelerde inline çözüm (120 sn aşama bütçesi) devreye kalır.
- Sezon-jetonu sezgiselidir; jeton yoksa mevcut arama-tabanlı sezon gezinmesi çalışmaya devam eder.
