# PLAN / TASK — HDFilmCehennemi & FilmMakinesi "oynatmıyor / hata veriyor"

**Tarih:** 2026-10-18
**Uygulama dalı:** `arena/084e7315-kitsugi-beta`
**Taban commit:** `9a70fee` (v2.4.304)
**İş commit'i:** `4342b3e`
**Eklenti deposu:** `f26901964-eng/Kitsugi-Plugins` (`main` = `5632bc5`)
**Şikâyet:** Ne HDFilmCehennemi'den ne FilmMakinesi'nden gelen video verisi oynatılmıyor; oynatıcı hata veriyor.

---

## 1. Kök nedenler

| # | Katman | Kök neden | Belirti | Kanıt |
|---|---|---|---|---|
| **KN-A** | Eklenti (`.cs3`) | `linkTypeFor()` else dalında `INFER_TYPE!!`. Kütüphanede `val INFER_TYPE: ExtractorLinkType? = null` → dal **her zaman NPE** fırlatıyor; NPE eklentinin `try/catch`'inde yutulduğu için `callback` hiç çağrılmıyor. | Kaynak yok / oynatıcı hata | `ExtractorApi.kt:447` (paketlenen `library-android-1.0.2-local`); `HDFilmCehennemi.kt:208`, `CloseLoadExtractor.kt:142` |
| **KN-A2** | Eklenti (`.cs3`) | HDFC çöp-adres filtresi `norm.contains(".ts")` kullanıyor. `".ts"` alt dizesi **`master.txt`** içinde de geçiyor → geçerli kaynaklar sessizce eleniyor. | Kaynak sayısı 0–1 | `HDFilmCehennemi.kt:781,802` |
| **KN-B** | Uygulama | `CsStreamRunner` kurtarma yolları `Origin` hiç doldurmuyor, `Referer`'ı **provider** sitesine sabitliyor. rapidrame / playmix.uno / closeload ise **embed origin'ini** doğruluyor. | Bulunan akış "ölü" sayılıp eleniyor | FilmMakinesi eklenti yorumu: *"eksik kalınca uygulama onu playmix.uno yapıyor ve yayın 5004 ile düşüyordu"* |
| **KN-C** | Uygulama | Eklentilerin `substringAfter("https")` kısayolunun ürettiği `httpshttp://…` adresi oynatıcıya kadar gidiyor; ExoPlayer şemayı çözemiyor. | "Kaynak görünüyor ama oynatmıyor" | eski `HDFilmCehennemi.kt` `substringAfter("https")` kullanımı |

**KN-A asıl nedendir.** KN-B, KN-A yüzünden devreye geren yedeğin de çalışmamasının nedenidir — uygulama kendini kurtarma şansını da kaybediyordu.

### KN-A hangi adreslerde tetikleniyor

Uzantısı `.m3u8 / master.txt / .m3u / .mp4 / .mkv / .webm / .mov / .avi` **olmayan her gerçek akış**: `master.txt` çözülüp içinden seçilen varyantlar, JSON gövdeden (`file`/`src`/`url`/`hls`) çözülen uzantısız CDN adresleri, token'lı `/play`, `/stream`, `index-v1-a1` uçları. Bugünkü "kör M3U8 düzeltmesi" tam da bu adresleri üretmeye başladığı için `else` dalı sık çalışır oldu ve hata görünür hâle geldi.

---

## 2. Görevler

### Eklenti deposu (KN-A, KN-A2) — **kod hazır, YAYINLANMADI (push yetkisi yok)**

- [x] **T1** — `linkTypeFor` → `ExtractorLinkType?`; uzantısız adreste `null` (kütüphanede `newExtractorLink(type = null)` zaten adresten çıkarım yapıyor). `playlist.txt` → M3U8, `.mpd` → DASH, `.m4v` → VIDEO eşlemeleri eklendi.
- [x] **T2** — Kullanılmayan `import ...INFER_TYPE` iki dosyadan kaldırıldı.
- [x] **T3** — `isFragmentUrl()` + `isGarbageStreamUrl()` yardımcıları: sorgu/fragman atıldıktan sonra gerçek uzantı (`.ts/.m4s/.cmfv/.cmfa`) ve çift şema önekleri (`httpshttp://`, `httpshttps://`, `httphttps://`).
- [x] **T4** — HDFC'deki 4 `.ts` filtresi ve FM'deki 2 `.ts` filtresi `isGarbageStreamUrl()` çağrısıyla değiştirildi.
- [x] **T5** — Sürüm: HDFilmCehennemi `53 → 54`, FilmMakinesi `63 → 64`.
- [x] **T6** — Commit `ef99469` + `git format-patch` çıktısı: `KITSUGI_PLUGINS_HDFC54_FM64_INFER_TYPE_FIX.patch`
- [x] **T7** — Yama doğrulandı: güncel `main` (`5632bc5`) üzerine `git apply --check` temiz, `git am` sorunsuz uygulandı (test klonu `/tmp/verify`, sonra silindi).
- [ ] **T8 — ⚠️ SENİN YAPMAN GEREKEN:** yamayı `Kitsugi-Plugins` `main`'e push'la. Bu oturumun token'ı yalnızca `Kitsugi-Beta`'ya yazabiliyor; `Kitsugi-Plugins` push'u `403 Permission denied` verdi. **Bu adım yapılmadan iki kaynak çalışmaya başlamaz.**
- [ ] **T9** — Push sonrası CI (`Derleyici.yml`) `builds/HDFilmCehennemi.cs3` (v54) + `builds/FilmMakinesi.cs3` (v64) üretir, `builds/plugins.json` sürüm/`fileHash`/`fileSize` ile güncellenir. İki eklenti de `enabled_plugins.txt` içinde → derleme atlanmaz. CI işinin `success` olduğunu doğrula.
- [ ] **T10** — Uygulamada eklentileri güncelle (kurulu v53/v63 ise düzelme görünmez).
- [ ] **T11** — CI v54/v64'ü bastıktan sonra gömülü `app/src/main/assets/plugins/plugins.json` senkronlanmalı (şu an bayat: HDFilmCehennemi `version 50`, FilmMakinesi `version 60`; `fileHash`/`fileSize` da eski). Uzak manifest birincil kaynak olduğu için normal akışta kritik değil, ama uzak erişilemezse bu bayat liste devreye giriyor.

### Uygulama (KN-B, KN-C) — **commit'lendi ve push'landı**

- [x] **T12** — `CsStreamRunner.originOfUrl(url)`: `scheme://host[:port]`; varsayılan portlar (80/443) atılır, çözümlenemezse `null`.
- [x] **T13** — `CsStreamRunner.applyEmbedOriginHeaders(headers, embedPageUrl)`: eksikse `Origin` + `Referer`'ı **embed sayfasının origin'i** ile doldurur. Çağıranın (eklentinin) verdiği `Origin`/`Referer` **asla ezilmez** — CDN token'ı o başlıklara bağlı. Anahtar karşılaştırması büyük/küçük harf duyarsız.
- [x] **T14** — Üç kurtarma yolu bağlandı: `selfHeaders` (embed adresi kendisi medya), `scannedHeaders` (HTML derin tarama), `sniffHeaders` (WebView son çare).
- [x] **T15** — `verifyMediaCandidate` artık `Origin` de gönderiyor (403/5004 yüzünden çalışan aday "ölü" sayılmıyor).
- [x] **T16** — `CsStreamSanitizer.isBrokenStreamUrl()` + `sanitize` **adım 0**: bozuk adres oynatıcıya hiç ulaşmaz. Kapsam kasıtlı olarak dar (çift/bozuk şema, şemasız adres, host'u çözümlenemeyen adres). Tüm kaynaklar bozuksa orijinal listeye **dönülmez** — boş liste döner, çünkü bozuk adres oynatıcıyı yine çökertir. URL'si olmayan (infoHash/magnet) kaynaklar filtreye takılmaz.
- [x] **T17** — JVM testleri: `CsEmbedOriginHeadersTest` (yeni), `CsStreamSanitizerTest` (yeni), `PlayerHlsDetectionTest` (gerçek rapidrame/playmix/vmeas adresleri eklendi).
- [x] **T18** — Statik doğrulama: `git diff --check` temiz; değişen 5 Kotlin dosyasında süslü parantez dengesi HEAD ile birebir aynı.
- [ ] **T19** — Derleme + testler (bu sandbox'ta JDK/Android SDK/Gradle yok, `apt` deposu ve `api.adoptium.net` erişilemez → yapılamadı):
      `./gradlew :app:testDebugUnitTest --tests "*CsEmbedOriginHeadersTest*" --tests "*CsStreamSanitizerTest*" --tests "*PlayerHlsDetectionTest*" --tests "*CsStreamRunnerLogicTest*"`
- [ ] **T20** — Cihaz: HDFilmCehennemi filmi aç → kaynak sayısı ≥ 1, akış oynuyor.
- [ ] **T21** — Cihaz: FilmMakinesi filmi aç → CloseLoad kaynağı oynuyor.
- [ ] **T22** — Cihaz log doğrulaması:
      `adb logcat -s CsStreamRunner CsTrace HDCH Kekik_CloseLoad PlayerMediaSourceFactory`
      Beklenen: `HDCH: link » https://…` / `Kekik_CloseLoad: link » https://…` **görünmeli** (v53/v63'te NPE yüzünden bu satır hiç yazılmıyordu); `Hata: null` (NPE) **görünmemeli**; `çöp adres elendi: https://…master.txt…` **görünmemeli**; `Sonuç: N doğrulanmış stream` içinde N ≥ 1.

---

## 3. Değişen dosyalar

### Uygulama — `Kitsugi-Beta`, dal `arena/084e7315-kitsugi-beta`, commit `4342b3e`

| Dosya | Durum | Ne |
|---|---|---|
| `app/src/main/java/com/kitsugi/animelist/data/cloudstream/CsStreamRunner.kt` | M (+52) | `originOfUrl`, `applyEmbedOriginHeaders`; 3 kurtarma yolu + `verifyMediaCandidate` (T12–T15) |
| `app/src/main/java/com/kitsugi/animelist/data/cloudstream/CsStreamSanitizer.kt` | M (+55/-2) | `isBrokenStreamUrl`, `BROKEN_SCHEME_*`, `sanitize` adım 0 (T16) |
| `app/src/test/java/com/kitsugi/animelist/data/cloudstream/CsEmbedOriginHeadersTest.kt` | A (+141) | KN-B regresyonu (T17) |
| `app/src/test/java/com/kitsugi/animelist/data/cloudstream/CsStreamSanitizerTest.kt` | A (+143) | KN-C regresyonu (T17) |
| `app/src/test/java/com/kitsugi/animelist/core/player/PlayerHlsDetectionTest.kt` | M (+26) | Gerçek CDN adresleri (T17) |
| `KITSUGI_PLUGINS_HDFC54_FM64_INFER_TYPE_FIX.patch` | A (+302) | Eklenti düzeltmesi — uygulanmayı bekliyor (T6) |
| `KOK_NEDEN_RAPORU_HDFC_FILMMAKINESI_OYNATMIYOR_2026-10-18.md` | A (+287) | Ayrıntılı kök neden raporu |
| `PLAN_TASK_HDFC_FILMMAKINESI_OYNATMIYOR_KOK_NEDEN_2026-10-18.md` | A | Bu dosya |

### Eklenti deposu — `Kitsugi-Plugins`, commit `ef99469` (**push edilmedi**)

| Dosya | Ne |
|---|---|
| `HDFilmCehennemi/src/main/kotlin/com/keyiflerolsun/HDFilmCehennemi.kt` | T1–T4 |
| `HDFilmCehennemi/build.gradle.kts` | `version = 53` → `54` (T5) |
| `FilmMakinesi/src/main/kotlin/com/keyiflerolsun/CloseLoadExtractor.kt` | T1–T4 |
| `FilmMakinesi/build.gradle.kts` | `version = 63` → `64` (T5) |

---

## 4. T8 nasıl yapılır (tek engel bu)

```bash
git clone https://github.com/f26901964-eng/Kitsugi-Plugins.git
cd Kitsugi-Plugins && git checkout main
git am /yol/KITSUGI_PLUGINS_HDFC54_FM64_INFER_TYPE_FIX.patch
#   git am takılırsa:  git apply --3way <patch>
git push origin main
```

Push sonrası `CloudStream Derleyici` işi otomatik çalışır (T9).

---

## 5. Bilinen sınırlar / açık riskler

1. **Eklenti yayınlanana kadar düzelme yok.** KN-A eklenti tarafında; uygulama tek başına bu iki kaynağı çalıştırmaz (T8).
2. **Gömülü `assets/plugins/plugins.json` bayat** (T11). Uzak manifest birincil; uzak erişilemezse bayat liste devreye girer.
3. **`builds` dalı tek commit tutuyor.** CI `git commit --amend` + `git push --force` yapıyor; bu yüzden `builds` geçmişinden "ne zaman derlendi" okunamıyor. Sürüm doğrulaması `.cs3` içindeki `manifest.json` → `version` ile yapılmalı.
4. **CDN token'ları süreli.** rapidrame `e=14400` (4 saat) gibi imzalı/süreli adres veriyor; süre dolunca aynı adres tekrar oynatılmaz. Kaynak değişimi/yeniden çözümleme ile aşılır — ayrı bir iş.
5. **Çoklu varyant kartları.** Eklenti artık bir master listesinden ilk 2-3 varyantı ayrı link veriyor (`… - 1/2/3`). Kalite etiketi sıra numarasından türetiliyor (1080/720/480), gerçek ölçüm değil.
6. **Derleme/cihaz testi yapılmadı** (T19–T22). Bu ortamda JDK, Android SDK ve Gradle yok.
