# PLAN / TASK — HDFilmCehennemi & FilmMakinesi "ne yaparsak yapalım oynatılmıyor"

**Tarih:** 2026-10-18 (2. tur — MPV TLS kök nedeni eklendi)
**Uygulama dalı:** `arena/084e7315-kitsugi-beta`
**Taban commit:** `9a70fee` (v2.4.304)
**Eklenti deposu:** `f26901964-eng/Kitsugi-Plugins` (`main` = `5632bc5`)
**Şikâyet:** HDFilmCehennemi ve FilmMakinesi video kaynakları ne olursa olsun oynatılmıyor; oynatıcı hata veriyor.

---

## 1. Kök nedenler (4 adet, üst üste binmiş)

| # | Katman | Kök neden | Belirti | Durum |
|---|---|---|---|---|
| **KN-D** | Uygulama — MPV motoru | `tls-verify=yes` + `tls-ca-file=<filesDir>/cacert.pem`; o dosya **hiçbir zaman oluşturulmuyordu** → CA paketi boş → **her HTTPS el sıkışması başarısız** | Varsayılan motorda (`playerPreference="MPV"`) **hiçbir kaynak** oynamıyor, kod 9001 | ✅ **DÜZELTİLDİ** |
| **KN-A** | Eklenti (`.cs3`) | `linkTypeFor()` else dalında `INFER_TYPE!!`. Kütüphanede `val INFER_TYPE: ExtractorLinkType? = null` → dal **her zaman NPE**; NPE eklentinin `try/catch`'inde yutulduğu için `callback` hiç çağrılmıyor | Bu iki kaynak 0 link üretiyor | ⚠️ yama hazır, **push yetkisi yok** |
| **KN-A2** | Eklenti (`.cs3`) | HDFC çöp-adres filtresi `contains(".ts")`; `".ts"` alt dizesi **`master.txt`** içinde de geçiyor → geçerli kaynaklar sessizce eleniyor | Kaynak sayısı 0–1 | ⚠️ aynı yamada |
| **KN-B** | Uygulama — `CsStreamRunner` | Kurtarma yolları `Origin` hiç doldurmuyor, `Referer`'ı **provider** sitesine sabitliyor. rapidrame/playmix.uno/closeload ise **embed origin'ini** doğruluyor | Bulunan akış "ölü" sayılıp eleniyor | ✅ **DÜZELTİLDİ** |
| **KN-C** | Uygulama — `CsStreamSanitizer` | Eklentilerin `substringAfter("https")` kısayolunun ürettiği `httpshttp://…` adresi oynatıcıya kadar gidiyor; ExoPlayer şemayı çözemiyor | "Kaynak görünüyor ama oynatmıyor" | ✅ **DÜZELTİLDİ** |

### KN-D neden "ne yaparsak yapalım" hissi veriyordu

`AppSettings.playerPreference` varsayılanı **`"MPV"`** → `PlayerEngineSelector.selectEngine` → `PlayerEngineType.MPV` → `MpvPlayerEngine`. Yani uygulamanın **ana** oynatma yolu dahili libmpv.

Kanıt zinciri:
1. `KitsugiMpvSurfaceView.initOptions()` → `tls-verify=yes`, `tls-ca-file="${context.filesDir.path}/cacert.pem"`
2. `app/src/main/assets` içinde `.pem` dosyası **yok** (yalnızca `companion/`, `fandom/`, `plugins/` + logo/splash)
3. `Utils.copyAssets(context)` (mpv-android-lib `io.github.abdallahmehiz:mpv-android-lib:0.1.12`) mpv'nin kendi asset'lerini kopyalar, CA paketi **üretmez**
4. `grep -rn "cacert"` → depoda **tek** satır: bu ayarın kendisi. Dosyayı yazan/indiren hiçbir kod yolu yok
5. mpv açıkça verilen CA yolunu yükleyemezse CA paketi boş kalır; `tls-verify=yes` ile her HTTPS bağlantısı düşer
6. Türkçe CDN'lerin **tamamı** HTTPS → MPV'de hiçbir kaynak açılmaz → `handleEndFile` → kod 9001 "MPV dosyayı açamadı"

**Karşılaştırma (asimetri kanıtı):** Media3 yolu `CsVideoInterceptorFactory.buildClient(ignoreSSL = true)` kullanıyor — trust-all `X509TrustManager` + `hostnameVerifier { _, _ -> true }`. O dosyanın kendi yorumu: *"NeonSpor gibi geçersiz/süresi dolmuş SSL sertifikası kullanan Türkçe yayın sunucularından kaynaklanan SSLHandshakeException hatalarını engeller."* Yani bu kaynaklar için sertifika doğrulaması **bilinçli olarak zaten kapatılmıştı**; MPV yolu aynı politikaya uymuyordu.

### KN-A hangi adreslerde tetikleniyor

Uzantısı `.m3u8 / master.txt / .m3u / .mp4 / .mkv / .webm / .mov / .avi` **olmayan her gerçek akış**: `master.txt` çözülüp içinden seçilen varyantlar, JSON gövdeden (`file`/`src`/`url`/`hls`) çözülen uzantısız CDN adresleri, token'lı `/play`, `/stream`, `index-v1-a1` uçları.

---

## 2. Görevler

### Uygulama — KN-D (MPV TLS) ✅

- [x] **T1** — `tls-verify` → `no`; var olmayan dosyaya işaret eden `tls-ca-file` ayarı **tamamen kaldırıldı**.
- [x] **T2** — Politika saf fonksiyona çıkarıldı: `mpvTlsOptions(caFilePath): List<Pair<String,String>>` + `applyMpvTlsOptions(caFilePath, setter)`. Kural: **doğrulama yalnızca gerçekten var olan bir CA dosyası gösterilebildiğinde açılır.**
- [x] **T3** — `initOptions()` artık `applyMpvTlsOptions(caFilePath = null) { … }` çağırıyor.
- [x] **T4** — `MpvTlsPolicyTest` (YENİ, 11 test): CA yokken `tls-verify=no` + `tls-ca-file` **hiç yazılmaz**; boş/whitespace yol = eksik sayılır; `tls-verify=yes` yalnızca dolu yolla; `applyMpvTlsOptions` yalnızca `tls-*` seçeneklerine dokunur; varsayılan argüman "CA yok".
- [x] **T5** — Aynı hata sınıfı denetlendi: MPV tarafında başka "olmayan dosya" referansı var mı? → **yok** (`filesDir/scripts` oluşturuluyor, `sub-font=Roboto` sistem fontu, `configDir`/`cacheDir` gerçek dizinler).
- [ ] **T6** — (opsiyonel, ayrı iş) Gerçek doğrulama istenirse: `cacert.pem` (ör. curl se ca-bundle) `app/src/main/assets/` altına konur, açılışta `filesDir`'e kopyalanır, sonra `applyMpvTlsOptions(caFilePath = <gerçek yol>)`. Dosya yokken doğrulama **asla** açılmamalı.

### Uygulama — KN-B / KN-C ✅ (önceki tur)

- [x] **T7** — `CsStreamRunner.originOfUrl(url)`: `scheme://host[:port]`; varsayılan portlar (80/443) atılır.
- [x] **T8** — `CsStreamRunner.applyEmbedOriginHeaders(headers, embedPageUrl)`: eksikse `Origin` + `Referer`'ı **embed sayfasının origin'i** ile doldurur. Çağıranın (eklentinin) verdiği değer **asla ezilmez** — CDN token'ı o başlıklara bağlı. Anahtar karşılaştırması büyük/küçük harf duyarsız.
- [x] **T9** — Üç kurtarma yolu bağlandı: `selfHeaders` (embed kendisi medya), `scannedHeaders` (HTML derin tarama), `sniffHeaders` (WebView son çare).
- [x] **T10** — `verifyMediaCandidate` artık `Origin` de gönderiyor (403/5004 yüzünden çalışan aday "ölü" sayılmıyor).
- [x] **T11** — `CsStreamSanitizer.isBrokenStreamUrl()` + `sanitize` **adım 0**: bozuk adres oynatıcıya hiç ulaşmaz. Kapsam kasıtlı olarak dar. Tüm kaynaklar bozuksa orijinal listeye **dönülmez**. URL'si olmayan (infoHash/magnet) kaynaklar takılmaz.
- [x] **T12** — JVM testleri: `CsEmbedOriginHeadersTest`, `CsStreamSanitizerTest`, `PlayerHlsDetectionTest` (gerçek rapidrame/playmix/vmeas adresleri).

### Eklenti deposu — KN-A / KN-A2 ⚠️ **YAYINLANMADI (push yetkisi yok)**

- [x] **T13** — `linkTypeFor` → `ExtractorLinkType?`; uzantısız adreste `null` (`newExtractorLink(type = null)` kütüphanede zaten adresten çıkarım yapıyor). `playlist.txt` → M3U8, `.mpd` → DASH, `.m4v` → VIDEO eklendi.
- [x] **T14** — Kullanılmayan `import …INFER_TYPE` iki dosyadan kaldırıldı.
- [x] **T15** — `isFragmentUrl()` + `isGarbageStreamUrl()`: sorgu/fragman atıldıktan sonra gerçek uzantı (`.ts/.m4s/.cmfv/.cmfa`) ve çift şema önekleri.
- [x] **T16** — HDFC'deki 4, FM'deki 2 `.ts` filtresi `isGarbageStreamUrl()` ile değiştirildi.
- [x] **T17** — Sürüm: HDFilmCehennemi `53 → 54`, FilmMakinesi `63 → 64`.
- [x] **T18** — Commit `ef99469` + `git format-patch`: `KITSUGI_PLUGINS_HDFC54_FM64_INFER_TYPE_FIX.patch`
- [x] **T19** — Yama doğrulandı: güncel `main` (`5632bc5`) üzerine `git apply --check` temiz, `git am` sorunsuz (test klonu, sonra silindi).
- [ ] **T20 — ⚠️ SENİN YAPMAN GEREKEN:** yamayı `Kitsugi-Plugins` `main`'e push'la. Bu oturumun token'ı yalnızca `Kitsugi-Beta`'ya yazabiliyor; `Kitsugi-Plugins` push'u `403 Permission denied` verdi.
- [ ] **T21** — Push sonrası CI (`Derleyici.yml`) `builds/HDFilmCehennemi.cs3` (v54) + `builds/FilmMakinesi.cs3` (v64) üretir, `builds/plugins.json` sürüm/`fileHash`/`fileSize` güncellenir. İkisi de `enabled_plugins.txt` içinde → derleme atlanmaz.
- [ ] **T22** — Uygulamada eklentileri güncelle (kurulu v53/v63 ise düzelme görünmez).
- [ ] **T23** — CI v54/v64'ü bastıktan sonra gömülü `app/src/main/assets/plugins/plugins.json` senkronlanmalı (şu an bayat: HDFilmCehennemi `version 50`, FilmMakinesi `version 60`).

### Doğrulama

- [x] **T24** — Statik: `git diff --check` temiz; değişen Kotlin dosyalarında süslü parantez dengesi HEAD ile birebir aynı.
- [ ] **T25** — Derleme + testler (bu sandbox'ta JDK/Android SDK/Gradle yok; `apt` deposu, `repo1.maven.org`, `api.adoptium.net` erişilemez → **yapılamadı**):

      ```
      ./gradlew :app:testDebugUnitTest \
        --tests "*MpvTlsPolicyTest*" \
        --tests "*CsEmbedOriginHeadersTest*" \
        --tests "*CsStreamSanitizerTest*" \
        --tests "*PlayerHlsDetectionTest*" \
        --tests "*CsStreamRunnerLogicTest*"
      ```

- [ ] **T26** — Cihaz: HDFilmCehennemi + FilmMakinesi filmi aç → kaynak ≥ 1 ve **oynuyor**.
- [ ] **T27** — Cihaz log:

      ```
      adb logcat -s MpvPlayerEngine KitsugiMpvSurfaceView CsStreamRunner HDCH Kekik_CloseLoad PlayerMediaSourceFactory
      ```

      | Beklenti | Anlamı |
      |---|---|
      | ❌ `MPV dosyayı açamadı (code=9001)` **görünmemeli** | KN-D kapandı |
      | ✅ `HDCH: link » https://…` / `Kekik_CloseLoad: link » https://…` görünmeli | KN-A kapandı (v53/v63'te NPE yüzünden bu satır hiç yazılmıyordu) |
      | ❌ `Hata: null` (NPE) görünmemeli | KN-A |
      | ❌ `çöp adres elendi: https://…master.txt…` görünmemeli | KN-A2 |
      | ✅ `Sonuç: N doğrulanmış stream` → N ≥ 1 | KN-B/KN-C |

---

## 3. Değişen dosyalar

### Uygulama — `Kitsugi-Beta`, dal `arena/084e7315-kitsugi-beta`

| Dosya | Durum | Ne | Kök neden |
|---|---|---|---|
| `core/player/engine/KitsugiMpvSurfaceView.kt` | M (+65/-2) | `tls-verify=no`; bozuk `tls-ca-file` kaldırıldı; `mpvTlsOptions` + `applyMpvTlsOptions` | **KN-D** |
| `app/src/test/.../core/player/engine/MpvTlsPolicyTest.kt` | A | 11 regresyon testi | **KN-D** |
| `data/cloudstream/CsStreamRunner.kt` | M (+52) | `originOfUrl`, `applyEmbedOriginHeaders`; 3 kurtarma yolu + `verifyMediaCandidate` | KN-B |
| `data/cloudstream/CsStreamSanitizer.kt` | M (+55/-2) | `isBrokenStreamUrl`, `BROKEN_SCHEME_*`, `sanitize` adım 0 | KN-C |
| `app/src/test/.../data/cloudstream/CsEmbedOriginHeadersTest.kt` | A | origin regresyonu | KN-B |
| `app/src/test/.../data/cloudstream/CsStreamSanitizerTest.kt` | A | bozuk adres regresyonu | KN-C |
| `app/src/test/.../core/player/PlayerHlsDetectionTest.kt` | M (+26) | gerçek CDN adresleri | — |
| `KITSUGI_PLUGINS_HDFC54_FM64_INFER_TYPE_FIX.patch` | A | eklenti düzeltmesi (v54/v64) | KN-A, KN-A2 |
| `KOK_NEDEN_RAPORU_…2026-10-18.md` | A/M | ayrıntılı kök neden raporu | hepsi |
| `PLAN_TASK_…2026-10-18.md` | A/M | bu dosya | hepsi |

### Eklenti deposu — `Kitsugi-Plugins`, commit `ef99469` (**push edilmedi**)

| Dosya | Ne |
|---|---|
| `HDFilmCehennemi/src/main/kotlin/com/keyiflerolsun/HDFilmCehennemi.kt` | T13–T16 |
| `HDFilmCehennemi/build.gradle.kts` | `version = 53` → `54` |
| `FilmMakinesi/src/main/kotlin/com/keyiflerolsun/CloseLoadExtractor.kt` | T13–T16 |
| `FilmMakinesi/build.gradle.kts` | `version = 63` → `64` |

---

## 4. T20 nasıl yapılır (tek engel bu)

```bash
git clone https://github.com/f26901964-eng/Kitsugi-Plugins.git
cd Kitsugi-Plugins && git checkout main
git am KITSUGI_PLUGINS_HDFC54_FM64_INFER_TYPE_FIX.patch
#   git am takılırsa:  git apply --3way KITSUGI_PLUGINS_HDFC54_FM64_INFER_TYPE_FIX.patch
git push origin main
```

Push sonrası `CloudStream Derleyici` işi otomatik çalışır (T21).

---

## 5. Bilinen sınırlar / açık riskler

1. **KN-D düzeltmesi tek başına yeterli olmayabilir.** Bu iki kaynağın link üretebilmesi için KN-A yamasının da yayınlanması gerekiyor (T20). KN-D, "kaynak listede olsa bile hiçbir şeyin oynamaması"nı çözer; KN-A, "bu iki kaynağın hiç link üretmemesi"ni çözer.
2. **Derleme/cihaz testi yapılmadı** (T25–T27). Bu ortamda JDK, Android SDK ve Gradle yok; `apt`, `repo1.maven.org` ve `api.adoptium.net` erişilemez.
3. **MPV'de sertifika doğrulaması artık kapalı.** Bu, Media3 yolunun zaten uyguladığı politika (trust-all) — asimetri giderildi. Güvenlik açısından ideal değil; kalıcı çözüm T6 (gerçek `cacert.pem` paketlemek).
4. **Gömülü `assets/plugins/plugins.json` bayat** (T23). Uzak manifest birincil; uzak erişilemezse bayat liste devreye girer.
5. **`builds` dalı tek commit tutuyor.** CI `git commit --amend` + `git push --force` yapıyor; sürüm doğrulaması `.cs3` içindeki `manifest.json` → `version` ile yapılmalı.
6. **CDN token'ları süreli.** rapidrame `e=14400` (4 saat) gibi imzalı adres veriyor; süre dolunca aynı adres tekrar oynatılmaz.
7. **User-Agent zinciri.** Eklenti token'ı Chrome/140 ile üretiyor, Media3 `link.headers`'ı aynen geçiriyor (doğru), MPV `http-header-fields` içine koyuyor. MPV'nin `user-agent` seçeneği hâlâ Chrome/119; `http-header-fields` içinde UA varsa mpv onu önceliklendirir, ama bazı mpv derlemelerinde `--user-agent` kazanabilir. Kaynak 403 vermeye devam ederse ilk şüpheli bu.
