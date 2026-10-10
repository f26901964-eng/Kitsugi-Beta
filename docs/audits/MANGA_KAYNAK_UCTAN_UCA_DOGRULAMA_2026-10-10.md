# Manga Kaynakları — Uçtan Uca Doğrulama Durumu ve Kök Neden Raporu

**Tarih:** 2026-10-10
**Branch:** `arena/8307d362-kitsugi-beta`
**Kapsam:** Türkçe manga kaynaklarının "arama → eşleşme → bölüm listesi → sayfa içeriği" zinciri

---

## 0. Özet (dürüst durum)

Önceki turda "düzelttim" denen şeyler **kod değişikliğiydi, cihazda doğrulanmış sonuç değildi.**
Bu turda iki şey yapıldı:

1. **Doğrulanabilir olan her şey doğrulandı** (kaynak kod üzerinden, satır numaralarıyla).
   Sonuç: kaynakların büyük kısmını *hiç çalıştırmayan* iki somut kök neden bulundu ve kanıtlandı.
2. **Bu sandbox'ta doğrulanamayan şeyler açıkça işaretlendi** (derleme + canlı site testleri).
   Neden doğrulanamadığı komut çıktısıyla belgelendi (bkz. §5).

**"Bütün Türkçe kaynaklar hazır ve sorunsuz" demiyoruz.** Diyoruz ki: 72/77 TR eklentisini
ilk istekte öldüren iki blokaj vardı, ikisi de giderildi; kalan doğrulama cihazda yapılmalı.

---

## 1. Envanter — "121 kaynak" sayısı yanlış

Kaynak: Keiyoushi V2 index (`index.json`), `keiyoushi/extensions` branch `repo`,
commit `68807878a7149e943025b2723e2d5b9b01815371`, dosya **1.486.066 bayt**.

| Ölçü | Değer |
|---|---|
| Toplam eklenti | **1431** |
| Toplam kaynak (eklenti başına birden fazla olabilir) | **2419** |
| **Türkçe (lang=tr) kaynak** | **93** |
| — `tr.*` paket | 77 |
| — `all.*` paket (MangaDex, NamiComi, GlobalComix, Akuma, ...) | 15 |
| — `en.*` paket (MangaDot) | 1 |
| NSFW işaretli eklenti içindeki TR kaynak | **20** |
| `jarUrl` ve `homeUrl` eksik TR kaynak | **0** |

> `MangaRepoClient.kt:54` içindeki "1396 eklenti, 1.45 MB" yorumu bugün **1431 eklenti / 1.42 MB**.
> `TurkishSourceRegistry.kt` içindeki "Keiyoushi 77 TR eklentisinin tamamı" yorumu
> `src/tr/*` **eklenti** sayısıyla uyumlu (77), ama **TR dilinde 93 kaynak** var;
> 16 tanesi `all.`/`en.` paketlerde ve token listesinde karşılığı yok.

**Envanter çıkarmak ≠ çalıştıklarını doğrulamak.** Bu rapor da öyle bir iddia taşımıyor.

---

## 2. KÖK NEDEN 1 — KeiSource host client'ından 3 interceptor istiyor, hiçbiri yoktu

`keiyoushi/extensions-source` → `core/src/main/kotlin/keiyoushi/source/KeiSource.kt`
(extensionLib **1.6**), `client` lazy initializer'ı:

```kotlin
with(interceptors()) {
    check(this.any { it.javaClass.simpleName == "UncaughtExceptionInterceptor" }) {
        "UncaughtExceptionInterceptor must be present in default client" }
    check(this.any { it.javaClass.simpleName == "UserAgentInterceptor" }) { ... }
    check(this.any { it.javaClass.simpleName == "CloudflareInterceptor" }) { ... }
}
```

Kontrol **`javaClass.simpleName`** üzerinden yapılıyor; yani sınıfın *adı* önemli, davranışı değil.

Kitsugi'da `NetworkHelper.client` zinciri düzeltmeden önce şuydu
(`app/src/main/java/eu/kanade/tachiyomi/network/NetworkHelper.kt`):

1. isimsiz lambda interceptor (domain remap + UA fallback)
2. `com.lagradost.cloudstream3.network.CloudflareKiller`
3. `com.lagradost.cloudstream3.network.DdosGuardKiller`

Üç zorunlu addan **hiçbiri yoktu** (`CloudflareKiller` ≠ `CloudflareInterceptor`).
Kitsugi'da `com.kitsugi.animelist.core.network.CloudflareInterceptor` sınıfı **zaten yazılmıştı**
(WebView ile `cf_clearance` çözer, 200 satır) ama yalnızca `NuvioOkHttpProvider`'da kullanılıyordu —
`NetworkHelper`'a hiç eklenmemişti.

**Sonuç:** extensionLib 1.6 tabanlı her eklenti, **ilk ağ isteğinde**
`IllegalStateException: UncaughtExceptionInterceptor must be present in default client` fırlatıyordu.
Arama boş döner, `MihonSourceWrapper.fetchChapterList` exception'ı yutup `emptyList()` döndürür
(`MihonSourceWrapper.kt`, catch bloğu) → kullanıcı **boş ekran** görür. Bildirilen semptom bu.

### Etkilenen eklenti sayısı (doğrulandı)

`src/tr/*` altındaki 77 eklentinin taban sınıf zinciri çözüldü:

| Taban | Adet |
|---|---|
| `KeiSource` (doğrudan veya `Madara`/`MangaThemesia`/`ZeistManga` üzerinden) | **72** |
| eski `HttpSource` (extensionLib 1.4) | **5** |

`lib-multisrc` tarafında da doğrulandı: `MadaraBase : KeiSource()`, `MangaThemesia : KeiSource()`,
`ZeistManga : KeiSource()` — yani Madara tabanlı 23 site de 1.6 yolunda.

1.4'te kalan 5 eklenti: `alucardscans`, `golgebahcesi`, `hattorimanga`, `mangadusleri`, `mangaship`.

**Yani 72/77 TR eklentisi bu tek satırlık kontrolden dolayı çalışmıyordu.**
Etkilenenler arasında önceliği en yüksek olanlar var: `mangatr`, `mangadenizi`, `juratempest`,
`mangaportali`, `mangadiyari`, `trmanga`, `webtoonoku`, `toontaku`.

---

## 3. KÖK NEDEN 2 — KeiSource OkHttp 5.4 API'si istiyor, Kitsugi'da OkHttp 4.12 var

Keiyoushi'nin beklediği sürümler (`keiyoushi/extensions-source` → `gradle/libs.versions.toml`):

```
okhttp = "5.4.0"
okhttp-core / okhttp-brotli / okhttp-zstd  -> 5.4.0
zstd-kmp-okio = 0.4.0
```

Kitsugi (`app/build.gradle.kts:332-333`):

```kotlin
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")
```

`okhttp-brotli`, `okhttp-zstd`, `zstd-kmp-okio` **yok**. `app/libs/*.aar` dosyalarının
13'ünün de `classes.jar` içinde **0 adet `okhttp3/` sınıfı** var (tek tek sayıldı) — yani
OkHttp 5 sınıflarını dolaylı getiren bir yerel AAR da yok.

KeiSource'un kullandığı ve OkHttp 4.12'de **var olmayan** semboller:

| Sembol | Kullanım yeri |
|---|---|
| `okhttp3.CompressionInterceptor` | `KeiSource.kt:27,117` |
| `okhttp3.Gzip` | `KeiSource.kt:28,117` |
| `okhttp3.brotli.Brotli` | `KeiSource.kt:34,117` |
| `okhttp3.brotli.BrotliInterceptor` | `KeiSource.kt:35,98` (`is` kontrolü) |
| `okhttp3.zstd.Zstd` | `KeiSource.kt:36,117` |
| `com.squareup.zstd.okio.zstdCompress/zstdDecompress` | `KeiSource.kt:4-5,291,305` (filtre önbelleği) |

`ChildFirstPathClassLoader` `okhttp3.` önekini **parent-first** yüklediği için
(`loader/ChildFirstPathClassLoader.kt`, `parentFirstPrefixes`) eklenti bu sınıfları uygulamanın
OkHttp 4.12'sinde arar → **`NoClassDefFoundError`**.

Bu blokaj §2'deki kontrolden *sonra* tetiklenir; yani §2 çözülmeden §3 görünmez bile.

---

## 4. Bu turda yapılan değişiklikler

| Dosya | Değişiklik |
|---|---|
| `eu/kanade/tachiyomi/network/interceptor/UncaughtExceptionInterceptor.kt` | **yeni** — Mihon ile aynı davranış; zincirde yakalanmayan hataları `IOException`'a çevirir |
| `eu/kanade/tachiyomi/network/interceptor/UserAgentInterceptor.kt` | **yeni** — UA yoksa varsayılanı ekler (Mihon ile aynı) |
| `eu/kanade/tachiyomi/network/NetworkHelper.kt` | 3 zorunlu interceptor zincire eklendi: `UncaughtExceptionInterceptor` (ilk), `UserAgentInterceptor`, `CloudflareInterceptor(context)` (son) |
| `okhttp3/CompressionInterceptor.kt` | **yeni stub** — imza OkHttp 5.4.0 ile birebir (`vararg DecompressionAlgorithm`); `intercept()` pass-through, gzip'i OkHttp 4'ün kendi şeffaf çözümü yapar |
| `okhttp3/Gzip.kt` | **yeni stub** — `DecompressionAlgorithm`, `encoding="gzip"` |
| `okhttp3/brotli/Brotli.kt` | **yeni stub** — `encoding="br"`, `decompress()` bilinçli olarak fırlatır |
| `okhttp3/brotli/BrotliInterceptor.kt` | **yeni stub** — `object : CompressionInterceptor(Brotli, Gzip)` (KeiSource'un `is` kontrolü için) |
| `okhttp3/zstd/Zstd.kt` | **yeni stub** — `encoding="zstd"`, `decompress()` fırlatır |
| `com/squareup/zstd/okio/Zstd.kt` | **yeni stub** — `@file:JvmName("OkioZstd")`, JVM imzaları upstream `.api` dökümüyle aynı; sıkıştırma yapmaz (yazma/okuma aynı stub'ı kullandığı için tutarlı) |
| `app/proguard-rules.pro` | `com.squareup.zstd.okio.**` için `-keep` eklendi (`okhttp3.**` zaten korunuyordu) |
| `data/manga/SourceHealthService.kt` | Sabit `"one piece"` sorgusu yerine **dile göre 3 aday sorgu** sırayla denenir; boş sonuç tek başına "bozuk" sayılmaz |
| `data/manga/MangaSourceRepository.kt` | `quickCheckSourceHealth(sampleQuery: String? = null)` — imza yeni davranışa uyarlandı |
| `data/manga/MihonSourceWrapper.kt` | `fetchChapterList` artık hatayı **yutmuyor** (`emptyList()` yerine `throw e`) — bkz. §7.1 |

Stub imzaları **tahmin edilmedi**, upstream kaynaklarından birebir alındı:
`square/okhttp@parent-5.4.0` ve `square/zstd-kmp` (`zstd-kmp-okio/api/jvm/zstd-kmp-okio.api`).

### Çakışma kontrolü (yapıldı)

`square/okhttp@parent-4.12.0` ağacında `okhttp/src` altında `CompressionInterceptor`,
`Gzip.kt` veya `zstd` **yok** (`git ls-tree -r --name-only parent-4.12.0 | grep ^okhttp/src | grep -iE ...` → boş).
Yani core `okhttp` 4.12.0 artifact'i bu sınıfları içermiyor; eklenen stub'lar **duplicate class** hatası üretmez.

Dikkat: `okhttp3.brotli.BrotliInterceptor` 4.12'de **ayrı** `okhttp-brotli` artifact'inde mevcut.
Kitsugi o artifact'i çekmiyor (yalnızca `okhttp` + `okhttp-dnsoverhttps`), bu yüzden bugün çakışma yok.
İleride `okhttp-brotli` bağımlılığı eklenirse `okhttp3/brotli/Brotli.kt` ve `BrotliInterceptor.kt`
stub'ları **silinmeli**.

---

## 5. Bu sandbox'ta DOĞRULANAMAYANLAR (kanıtlı)

### a) Derleme / birim testleri çalıştırılamadı

```
$ for t in java javac kotlinc gradle; do command -v $t || echo MISSING; done
java: MISSING   javac: MISSING   kotlinc: MISSING   gradle: MISSING
ANDROID_HOME= (boş)   ANDROID_SDK_ROOT= (boş)   local.properties yok
$ curl -s -o /dev/null -w "%{http_code}" https://repo.maven.apache.org/maven2/   -> 000
$ curl -s -o /dev/null -w "%{http_code}" https://dl.google.com/dl/android/maven2/ -> 000
$ curl -s -o /dev/null -w "%{http_code}" https://plugins.gradle.org/m2/          -> 000
```

JDK yok, Android SDK yok, Maven Central/Google Maven/Gradle Plugin Portal erişilemez.
`./gradlew assembleDebug` ve `./gradlew testDebugUnitTest` **burada çalıştırılamaz**.
Yeni yazılan Kotlin dosyaları bu yüzden **derleyiciyle doğrulanmadı**; yapılan tek mekanik
kontrol parantez/süslü parantez dengesi (10 dosya, hepsi eşit) ve imzaların upstream ile
karşılaştırılması.

### b) Canlı site testi yapılamadı

```
$ curl -s -o /dev/null -w "%{http_code}" https://raw.githubusercontent.com/... -> 000
$ curl -s -o /dev/null -w "%{http_code}" https://manga-tr.com/                -> 000
$ git ls-remote https://github.com/keiyoushi/extensions HEAD                  -> 6880787...  (ÇALIŞIYOR)
```

Çıkış yalnızca GitHub/npm/PyPI'ye açık. Hiçbir manga sitesine istek atılamadı;
bu yüzden **tek bir kaynağın bile gerçek arama/bölüm/sayfa sonucu burada test edilmedi.**

---

## 6. Cihazda yapılacak doğrulama protokolü

Zincirin tamamı zaten kodda var: `SourceHealthService.evaluate()`
`fetchSearchManga → fetchMangaDetails → fetchChapterList → fetchPageList → fetchImageUrl`
sırasıyla çalışıyor ve her aşamada zaman aşımı + hata sınıflandırması yapıyor.

1. `./gradlew assembleDebug` — derleme (yeni stub'ların ilk gerçek kontrolü).
2. Uygulamayı kur, Keiyoushi repo'sundan TR eklentilerini indir.
3. Her kaynak için `MangaSourceRepository.quickCheckSourceHealth(source)` çağır
   (artık TR kaynaklarda `solo leveling → one piece → naruto` sırasıyla dener).
4. Beklenen: 72 eklentinin artık `IllegalStateException: ... must be present in default client`
   **vermemesi**. Logcat'te bu mesaj görülürse stub/zincir düzeltmesi yerine oturmadı demektir.
5. `NoClassDefFoundError: okhttp3.CompressionInterceptor` görülürse R8 keep kuralı veya
   stub paketi yanlış demektir.
6. Sonuçları `SourceHealthStatus` bazında say: `Healthy / Degraded / Broken / CaptchaRequired / RateLimited`.
   **Hedef "hepsi Healthy" değil** — hedef: olan kaynak doğru bulunsun ve okunsun,
   olmayan kaynak boş ekran yerine açık hata versin.

---

## 7. Bilinen, hâlâ açık kalanlar

1. **WebView/oturum isteyen kaynaklar (15):** `araznovel`, `domalfansub`, `ghosthentai`,
   `hattorimanga`, `holyscans`, `laviniafansub`, `lunascans`, `mangadusleri`, `mangaship`,
   `mangatr`, `milasub`, `niverafansub`, `opiatoon`, `tonizutoon`, `trmanga`.
   Bunlar bölüm içeriği için giriş/`cf_clearance` ister; `runWebView` eklenti JAR'ının içinde
   (core lib) ve `ActivityTracker` kendi kendini `Application.ActivityLifecycleCallbacks` ile
   kaydediyor — yani host tarafında ek kurulum gerekmiyor. **Ama bu cihazda test edilmedi.**
2. **CloudflareInterceptor kullanan TR eklentileri (2):** `mangawt`, `toontaku`.
3. **Light novel ayrı iş:** `toontaku` sitesi `TEXT_CHAPTER` barındırıyor ve eklenti bunu
   `contentKind=IMAGE_CHAPTER` ile **bilinçli olarak eliyor**; `mangatr` "Novel" tipini
   `isExcludedType()` ile dışlıyor; `monomanga`/`sleptmanga`/`holyscans` novel'i filtreliyor.
   `araznovel` ise baştan sona light-novel sitesi (Turnstile'ı `runWebView` ile geçiyor).
   **Metin bölümü gösteren bir okuyucu yok** — bu, görsel sayfa yüklemeyle aynı iş değil
   ve bu turda ele alınmadı.
4. **NSFW 20 TR kaynak** `TurkishSourceRegistry`'de `-300` ceza alıyor (bilinçli tercih).
5. ~~`MihonSourceWrapper.fetchChapterList` hata hâlinde `emptyList()` döndürüyor~~ — **bu turda düzeltildi.**
   Eski davranışın iki somut zararı vardı ve ikisi de kodda doğrulandı:
   - `MangaReaderViewModel.loadChapterList` (satır 127) exception görmediği için
     `recordOperationSuccess(...)` çağırıyordu → bozuk kaynak istatistikte **başarılı** kaydediliyordu.
   - Kullanıcı hata mesajı yerine boş bölüm listesi görüyordu.

   Üç çağrı yerinin tamamı (`SourceHealthService.evaluate`, `MangaDetailViewModel.loadChapters`,
   `MangaReaderViewModel.loadChapterList`) `try/catch` içinde; `MangaDetailViewModel` iki denemeden
   sonra `error = lastError?.localizedMessage ?: "Bölümler yüklenemedi. Tekrar deneyin."` yazıyor.
   Yani `throw e` yeni bir çökme yüzeyi açmıyor, sadece gerçek hatayı görünür yapıyor.
   **Cihazda doğrulanmadı.**
6. `MihonSourceWrapper.fetchMangaDetails` hata hâlinde hâlâ `MangaDetails(url, title = url)` stub'ı
   döndürüyor (sessiz bozunma). Bilinçli olarak dokunulmadı: `MangaDetailViewModel` bu değeri
   mevcut detayla birleştiriyor (`merged`), davranış değişikliği cihazda görülmeden riskli.
