# Kitsugi — CS Eklentileri "Veri Gelmiyor" Kök Neden Raporu (v2.4.221)

**Tarih:** 2026-10-09
**Dal:** `arena/b58a7b59-kitsugi-beta` (temel: `2126157` / v2.4.220)
**Şikâyet:** *"CS eklentilerinin yarısından fazlası aktif ama neredeyse hiçbirinden
video veri linkleri gelmiyor."*

> **Not:** Kullanıcı ile paylaşılan `CS_Tani_Raporu_20261009_145128.md` dosyası bu ortama
> ulaşmadı (uploads dizini boş). Bu çalışma, depodaki önceki teşhis raporları +
> `CsStreamRunner.kt` / `StreamViewModel.kt` / `CsPluginStatusTracker.kt` /
> `CsTitleMatcher.kt` / `CsEpisodeMatcher.kt` / `CsPluginLoader.kt` kaynak kodları +
> canlı depo (Kitsugi-Plugins `builds`) doğrulaması ile yapılmıştır.

---

## 1. Kanıt Tabanı (Bu Ortamda Doğrulananlar)

| Kanıt | Sonuç |
|---|---|
| `Kitsugi-Plugins` `builds` dalı (canlı API) | 166 eklenti, 163 Türkçe, hepsi `status:1`, URL'ler tam | 
| `domain_fixes.json` (yerel = `main` dalı ile birebir aynı) | `blocked: 176`, `domains: 57` |
| `cs_deep_stream_report.md` (cihaz E2E, 201 eklenti) | ✅ stream çıkan **23 (%11)**, 🔍 arama boş (CF/WAF) **159 (%79)** |
| `plugin_diagnostic_report.md` (366 eklenti) | 112 site CF korumalı, 236 domain bilinmiyor, kalanı ölü/erişilemez |
| `video_extractor_report.md` (51 embed CDN) | 15 DNS/DEAD, 1 WAF, 11 redirect — embed katmanı da delikli |

Yani: **sitelerin çoğu ya CF/WAF arkasında ya da embed CDN'leri ölü** — ama asıl
kritik olan, uygulamanın **veri bulduğu hâlde bile onu kullanıcıya ulaştıramaması**.
Aşağıdaki 5 kök neden kodda birebir bulundu.

---

## 2. Kök Nedenler (kod kanıtlarıyla)

### KN-1 (KRİTİK) — Provider zaman aşımı, BULUNMUŞ kaynakları da çöpe atıyordu

`CsStreamRunner.getStreams()` içindeki `withTimeoutOrNull(effectiveTimeout)` süre
dolduğunda `null` dönüyor ve kod **doğrudan `emptyList()`** döndürüyordu. Oysa boru
hattı iç aşamaları şunlar:

```
arama (≤18 varyant × 15 sn) → load (15 sn) → loadLinks (25 sn)
→ embed çözümleme (20 sn × ⌈N/3⌉) → HEAD doğrulama (8 sn × N, SIRALI!)
```

Tek bir yavaş site/embed/ölü CDN zinciri 40 sn'lik bütçeyi patlatıyor; loadLinks'tan
**gerçekten gelmiş linkler bile** `withTimeoutOrNull` iptaliyle birlikte kayboluyordu.
UI'da kart "Bu anime için akış bulunamadı" gösteriyordu. *"Site açık ama veri gelmiyor"*
şikâyetinin bir numaralı açıklaması budur.

**Ölüm senaryosu (gerçekçi):** arama 18 varyant sırayla ~18 sn → load 3 sn →
loadLinks 8 sn'de 4 embed verdi → embed'lerden biri ölü CDN'de 20 sn takıldı →
toplam 49 sn > 40 sn → **her şey çöpe**.

### KN-2 (KRİTİK) — HEAD canlılık doğrulaması SIRALIYDI (8 sn × N)

`extractStreamsFromEpisode` sonunda her doğrudan medya URL'si için sıralı HTTP HEAD
(`withTimeoutOrNull(8_000L)`) yapılıyordu. 5 ölü/çözümlenemeyen URL = 40 sn sadece
doğrulama — ve bu da KN-1 ile birleşip **tüm sonuçların** imhasına yol açıyordu.

### KN-3 (YÜKSEK) — 18 arama varyantının sırayla denenmesi

`runGetStreams` içinde varyantlar **tek tek** (300 ms throttle + ağ gecikmesi) deneniyordu.
Ağır sitelerde arama tek başına bütçeyi yutuyor; load/extract aşamalarına zaman kalmıyordu.
Ayrıca yalnızca `isHighConfidenceMatch` (≥0.90 benzerlik) erken çıkışı vardı — Türkçe
başlık çeviri farklılıklarında bu skor nadiren yakalanır, dolayısıyla 18 sorgunun tamamı
"boşuna" çalışıyordu.

### KN-4 (YÜKSEK) — "Doğrula" (CF WebView) sonrası tekrar deneme hep başarısızdı

`CsPluginStatusTracker` kayıtları **`api.name`** anahtarıyla tutulur
(`recordFailure(api.name, ...)`). Ama `StreamViewModel`:
- blok kontrolünü `isBlocked(plugin.id)` ile yapıyordu (asla eşleşmez),
- "Doğrula" sonrası temizliği `clearPluginStatus(plugin.id)` ile yapıyordu
  (**gerçek anahtarı temizlemiyordu** → kullanıcı doğrulama yaptı, eklenti yine boş döndü).

### KN-5 (ORTA) — UI gerçek sebebi hiç göstermiyordu

Zaman aşımı, "tüm varyantlar boş", "bölüm bulunamadı", "loadLinks timeout" gibi
nedenler `CsPluginStatusTracker`'a **hiç yazılmıyordu**; kart hep "Bu anime için akış
bulunamadı" diyordu. Kullanıcı "eklenti bozuk" sanıyordu; hâlbuki sebep bambaşka olabiliyordu.

### Ek bağlam (kod-dışı, düzeltilmeyecek durumlar)

- **CF/WAF savaşı:** cihaz E2E'sinde eklentilerin %79'u aramada sıfır — sitelerin
  büyük kısmı Cloudflare arkasında. `CsCfWarmupManager` + `CloudflareKiller` +
  "Doğrula" WebView akışı bunun için var; ama bot koruması geliştikçe bu oran
  kalıcı bir gerilim olmaya devam eder.
- **Embed CDN kirliliği:** 51 embed hostun 27'si ölü/redirect (video_extractor_report).
  `EmbedMediaScanner` + 4 aşamalı çözümleme bu boşluğu kapatmaya çalışıyor.
- **`domain_fixes.json` `blocked` listesi (176 kayıt):** v2.4.210'dan beri uygulamada
  yalnızca **uyarı** üretiyor, eklentiyi atlamıyor — eski "sessiz atlama" kapısı kapalı.
  (Liste hâlâ normal eklentilerle dolu (`4kfilmizlesene`, `fullhdfilm`, `dizipaloriginal` …);
  `main`'de temizlenmesi faydalı olur ama artık veri akışını kesmiyor.)

---

## 3. Bu Oturumda Uygulanan Düzeltmeler (v2.4.221)

| # | Dosya | Değişiklik |
|---|---|---|
| 1 | `CsStreamRunner.kt` | **Kısmi sonuç kurtarma:** tüm `streams.add` noktaları `partialSink`'e de yazılır; provider zaman aşımında bulunan kaynaklar **kurtarılıp döndürülür** (`return@withContext rescued`). Sebep tracker'a yazılır → kartta görünür. |
| 2 | `CsStreamRunner.kt` | **HEAD doğrulaması paralel:** Semaphore(6) × 5 sn (eskiden sıralı × 8 sn). Ön-eleme ağsız; sıra/sonuç davranışı korundu (false-positive fallback dahil). |
| 3 | `CsStreamRunner.kt` | **Arama zaman kutusu + 3'lü paralel gruplar:** 20 sn (CF: 40 sn) sert bütçe; makul eşleşme + bütçenin yarısı geçtiyse erken çıkış; geniş arama fallback'i kalan bütçeye bağlandı. |
| 4 | `CsStreamRunner.kt` | **Provider bütçesi uyumu:** `PROVIDER_TIMEOUT_MS` 40→**75 sn** (iç aşama toplamı: 20+15+25+embed/HEAD), `CF_PROVIDER_TIMEOUT_MS` 90→**120 sn**. Eklentiler paralel çalıştığı için kartlar doldukça güncellenir; kullanıcı beklemez. |
| 5 | `StreamViewModel.kt` | **Tracker anahtar uyumu:** blok kontrolü `plugin.id` **ve** `plugin.name`; "Doğrula" sonrası temizlik `csPlugin.id`, `csPlugin.name` ve **tüm `api.name`**'leri (KN-4). |
| 6 | `CsStreamRunner.kt` | **Gerçek sebep kayıtları:** arama-sıfır, bölüm-bulunamadı, load-timeout, loadLinks-timeout, provider-timeout → `recordSkip` ile UI'a taşındı. Gerçek ağ hatası kayıtlıysa üzerine yazılmaz (spesifik mesaj korunur). |
| 7 | `CsStreamRunner.kt` | CF tespiti için `lastErr`, `recordSkip`'ten **önce** okunur — "Doğrula" butonunun tetiklenmesi korundu. |

### Davranış özeti

- Eklenti **ya kaynak getirir ya da kartta gerçek sebebi görünür** ("Zaman aşımı (75 sn)
  — 3 kaynak kurtarıldı", "Arama sonuç vermedi (12 varyant) — site erişilemiyor veya
  CF/WAF korumalı olabilir", "S1E5 bölümü bulunamadı", "Cloudflare doğrulaması gerekiyor" …).
- Zaman aşımı artık **kurtarma**tır, imha değil.
- Arama/load/extract aşamaları tek bir 40 sn'lik huniye sıkışmıyor; her aşama kendi
  bütçesiyle çalışıyor, üst sınır kaçak ağı olarak çalışıyor.
- "Doğrula" butonu artık gerçekten ikinci denemeyi temiz başlatıyor.

---

## 4. Doğrulama

- Parantez/bracket dengesi `git show HEAD` ile karşılaştırmalı doğrulandı (fark: 0).
- Tüm çağrı zinciri (`getStreams → runGetStreams → tryNativeIdResolution/loadAndExtractStreams
  → extractStreamsFromEpisode`) `partialSink` parametresiyle tutarlı; `getStreamsForUrl`
  varsayılan parametreyle uyumlu.
- `StreamViewModel` diff'i 2 hunks, Türkçe karakterler korunmuş.
- **Yapılamayan:** bu ortamda JDK/Gradle/Android SDK yok (ve Maven erişimi kapalı) →
  Kotlin **derlenemedi**; Türkçe sitelere ağ erişimi yok → canlı doğrulama yapılamadı.
  **Derleme + cihaz testi kullanıcı tarafında yapılmalıdır.**

## 5. Kullanıcı Tarafı Yapılacaklar

1. Bu dalı derle (`build-apk.ps1`) ve cihazda test et — özellikle:
   - Daha önce "boş dönüyordu" ama sitesi açık olan eklentiler (FullHDFilmizlesene,
     DiziMom, Dizilla, SezonlukDizi, HDFilmCehennemi, AnimeciX vb.)
   - Kartlarda artık **gerçek sebep** yazdığını görmen gerekiyor.
2. PR ile `main`'e birleştir.
3. (Opsiyonel) `domain_fixes.json` `blocked` listesini `main`'de boşalt — uygulama artık
   bu listeyi engel olarak uygulamıyor ama gereksiz gürültü.
4. CF bloğu gören eklentilerde "Doğrula"ya basıp yeniden dene — artık temiz oturumla
   tekrar deniyor.

## 6. Bilinçli Olarak Yapılmayanlar

- **`findBestMatch` eşikleri** (MIN_MATCH_SCORE 0.45, token kapsama 0.75): yanlış
  eşleşme şikâyetleri (önceki oturumlar) nedeniyle bilinçli olarak gevşetilmedi.
  Çok-dilli başlık çevrilmesi `alternativeTitles` (synonyms + TR başlıklar) ile
  karşılanıyor; gerekirse ayrı bir oturumda "çeviri başlık" fallback'i eklenebilir.
- **CF/WAF bypass**: bot koruması kalıcı bir yarış; warmup + WebView doğrulama
  mevcut strateji olarak korundu.
- **Eklenti derleme (Kitsugi-Plugins tarafı)**: 166 eklentinin hepsi indirilebilir
  durumda; sorun depoda değil, uygulama boru hattındaydı.
