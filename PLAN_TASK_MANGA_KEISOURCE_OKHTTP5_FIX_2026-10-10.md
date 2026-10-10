# PLAN & TASK — Manga Kaynakları: extensionLib 1.6 (KeiSource) + OkHttp 5 Uyumluluğu

**Tarih:** 2026-10-10
**Branch:** `arena/8307d362-kitsugi-beta`
**Commitler:** `698cf48`, `c86ae0e`, `1ba0076`
**Detaylı kanıt raporu:** `docs/audits/MANGA_KAYNAK_UCTAN_UCA_DOGRULAMA_2026-10-10.md`

---

## 1. Problem tanımı

Türkçe manga kaynaklarının önemli bir kısmı arama/bölüm/sayfa zincirinde **boş ekran** veriyor.
Kullanıcı hedefi: *"Hangi kaynakta varsa doğru bulunsun ve okunabilsin; çalışmayan kaynak boş
ekran yerine açık hata versin."*

## 2. Teşhis (kaynak kod ile doğrulandı)

### 2.1 Envanter — sayılar düzeltildi

`scripts/audit_manga_sources.py` ile upstream'den hesaplandı (bu ortamda çalıştırıldı):

| Ölçü | Değer |
|---|---|
| Keiyoushi eklenti | 1431 |
| Toplam kaynak | 2419 |
| **TR dilinde kaynak** | **93** (77 `tr.*` + 15 `all.*` + 1 `en.*`) |
| NSFW eklenti içindeki TR kaynak | 20 |
| `src/tr/*` eklenti dizini | 77 |
| **extensionLib 1.6 (KeiSource)** | **72** |
| eski (HttpSource, lib 1.4) | 5 (`alucardscans`, `golgebahcesi`, `hattorimanga`, `mangadusleri`, `mangaship`) |

> Önceki "121 kaynak" ve "77 TR eklentisinin tamamı" ifadeleri yanlıştı/bayatlamıştı.

### 2.2 Kök neden 1 — host client'ta zorunlu 3 interceptor yoktu

`keiyoushi/extensions-source → core/src/main/kotlin/keiyoushi/source/KeiSource.kt`,
`client` lazy initializer'ı **sınıf adına göre** `check(...)` yapıyor:

- `UncaughtExceptionInterceptor`
- `UserAgentInterceptor`
- `CloudflareInterceptor`

`NetworkHelper.client` zincirinde üçü de yoktu → ilk istekte
`IllegalStateException: ... must be present in default client`.
Etkilenen: **72/77 TR eklentisi** (`MadaraBase : KeiSource()`, `MangaThemesia : KeiSource()`,
`ZeistManga : KeiSource()` olduğu için Madara tabanlı siteler dahil).

### 2.3 Kök neden 2 — OkHttp 5.4 API'si yok

| Taraf | Sürüm |
|---|---|
| Keiyoushi lib 1.6 (`gradle/libs.versions.toml:12`) | okhttp **5.4.0** + okhttp-brotli + okhttp-zstd + zstd-kmp-okio 0.4.0 |
| Kitsugi (`app/build.gradle.kts:332`) | okhttp **4.12.0** (brotli/zstd yok) |

`KeiSource.kt:117` → `addInterceptor(CompressionInterceptor(Brotli, Gzip, Zstd))`.
`ChildFirstPathClassLoader` `okhttp3.` önekini parent-first yüklediği için eklenti bu sınıfları
uygulamanın 4.12'sinde arar → `NoClassDefFoundError`.
(OkHttp 4.12.0 core ağacında bu sınıfların olmadığı `git ls-tree` ile doğrulandı → çakışma yok.)

### 2.4 Boş ekranın mekanizması

`MihonSourceWrapper.fetchChapterList` exception'ı yakalayıp `emptyList()` döndürüyordu:
- `MangaReaderViewModel.loadChapterList` hata görmediği için `recordOperationSuccess()` çağırıyordu
  → bozuk kaynak istatistikte **başarılı** kaydediliyordu.
- Kullanıcı hata mesajı yerine boş bölüm listesi görüyordu.

## 3. Yapılan işler

| # | Dosya | İş |
|---|---|---|
| 1 | `eu/kanade/tachiyomi/network/interceptor/UncaughtExceptionInterceptor.kt` | yeni (Mihon ile aynı davranış) |
| 2 | `eu/kanade/tachiyomi/network/interceptor/UserAgentInterceptor.kt` | yeni (Mihon ile aynı) |
| 3 | `eu/kanade/tachiyomi/network/NetworkHelper.kt` | 3 zorunlu interceptor zincire eklendi |
| 4 | `okhttp3/CompressionInterceptor.kt` | stub — imza OkHttp 5.4.0 ile birebir, `intercept()` pass-through |
| 5 | `okhttp3/Gzip.kt` | stub — `encoding="gzip"`, okio.GzipSource |
| 6 | `okhttp3/brotli/Brotli.kt` | stub — `encoding="br"`, decompress fırlatır |
| 7 | `okhttp3/brotli/BrotliInterceptor.kt` | stub — KeiSource'un `is` kontrolü için |
| 8 | `okhttp3/zstd/Zstd.kt` | stub — `encoding="zstd"`, decompress fırlatır |
| 9 | `com/squareup/zstd/okio/Zstd.kt` | stub — `@file:JvmName("OkioZstd")`, JVM imzaları `.api` dökümüyle aynı |
| 10 | `app/proguard-rules.pro` | `com.squareup.zstd.okio.**` keep |
| 11 | `data/manga/SourceHealthService.kt` | sabit `"one piece"` → dile göre 3 aday sorgu |
| 12 | `data/manga/MangaSourceRepository.kt` | `quickCheckSourceHealth(sampleQuery: String? = null)` |
| 13 | `data/manga/MihonSourceWrapper.kt` | `fetchChapterList` artık `throw e` (emptyList değil) |
| 14 | `scripts/audit_manga_sources.py` | envanteri tekrar üretilebilir hale getirdi |
| 15 | `docs/audits/MANGA_KAYNAK_UCTAN_UCA_DOGRULAMA_2026-10-10.md` | kanıt raporu + protokol |

## 4. Doğrulama durumu

**Bu ortamda doğrulandı:**
- Envanter sayıları (`scripts/audit_manga_sources.py` gerçek çalıştırma çıktısı raporda)
- `python3 -m py_compile scripts/audit_manga_sources.py` → OK
- 10 Kotlin dosyasında parantez/süslü parantez dengesi eşit
- Yeni sınıf adlarında duplicate tanım yok (grep ile tarandı)
- OkHttp 4.12.0 core'da bu sınıflar yok → duplicate class riski yok

**Doğrulanamadı (ortam kısıtı, komut çıktılarıyla belgelendi):**
- Derleme / birim testleri: `java`, `javac`, `kotlinc`, `gradle` MISSING; `ANDROID_HOME` boş;
  `repo.maven.apache.org`, `dl.google.com`, `plugins.gradle.org` → HTTP `000`
- Canlı site testi: `manga-tr.com` → HTTP `000` (çıkış yalnızca github/npm/pypi)
- Kotlin compiler indirilemedi (PyPI'de yok, GitHub release asset host'u erişilemez)

## 5. Cihazda yapılacaklar (sıra ile)

1. `./gradlew assembleDebug` → stub'ların ilk gerçek derleme kontrolü.
2. Keiyoushi repo'sundan TR eklentilerini indir/kur.
3. Logcat'te şu iki mesajın **görünmediğini** doğrula:
   - `must be present in default client`
   - `NoClassDefFoundError: okhttp3.CompressionInterceptor`
4. Her kaynak için `MangaSourceRepository.quickCheckSourceHealth(source)` çalıştır
   (TR'de `solo leveling → one piece → naruto` sırasıyla dener).
5. Sonuçları `SourceHealthStatus` bazında say: `Healthy / Degraded / Broken / CaptchaRequired / RateLimited`.
   Hedef "hepsi Healthy" değil; hedef: **olan kaynak doğru bulunsun + okunsun, olmayan açık hata versin.**
6. `mangawt` ve `toontaku`'da kilitli bölüm 403'ünün "Cloudflare doğrulaması" gibi görünmediğini kontrol et
   (yeni eklenen `CloudflareInterceptor` yan etkisi olabilir).

## 6. Açık kalan işler (bu turda yapılmadı)

1. **Light novel okuyucu yok.** `toontaku` sitesi `TEXT_CHAPTER` barındırıyor ve eklenti bunu
   `contentKind=IMAGE_CHAPTER` ile bilinçli olarak eliyor; `mangatr` "Novel" tipini dışlıyor;
   `monomanga`/`sleptmanga`/`holyscans` novel'i filtreliyor; `araznovel` baştan sona light-novel sitesi.
   Metin bölümü gösteren bir okuyucu ayrı bir iş.
2. **WebView/oturum isteyen 15 kaynak** (araznovel, domalfansub, ghosthentai, hattorimanga, holyscans,
   laviniafansub, lunascans, mangadusleri, mangaship, mangatr, milasub, niverafansub, opiatoon,
   tonizutoon, trmanga) cihazda test edilmedi.
3. `MihonSourceWrapper.fetchMangaDetails` hata hâlinde hâlâ `MangaDetails(url, title=url)` stub'ı
   döndürüyor (sessiz bozunma).
4. `TurkishSourceRegistry` token listesi 93 TR kaynağın 16'sını (`all.*`/`en.*`) kapsamıyor;
   "77 TR eklentisi" yorumu güncellenmeli.
5. `okhttp-brotli` bağımlılığı ileride eklenirse `okhttp3/brotli/*.kt` stub'ları **silinmeli**.
