# Manga-TR / Manga Kaynakları Tam Denetim ve Çözüm Raporu

**Tarih:** 3 Kasım 2026
**Dal:** `arena/0a58b6d5-kitsugi-beta`
**Kapsam:** `app/src/main/java/com/kitsugi/animelist/` manga katmanı, ayarlar/TV ekranları, repo & imza güveni, okuyucu, arama/popüler liste, özel `Kitsugi-Manga-Plugins` deposu ve ekran görüntüsündeki `Disabled` + kapalı anahtar sorunu.
**Referans girdi:** `/tmp/arena-coding-uploads/image-1.png` — `Manga Kaynakları` ekranı; `Repositories (1)`, `Installed (1)`, `Kaynak sağlık raporu`, `Manga-TR`, `https://manga-tr.com`, `Disabled`, kapalı switch.

> **ÖNEMLİ / DÜRÜSTLÜK NOTU:** Bu çalışma ortamında **JDK, Android SDK, `local.properties`, `kotlinc`, Gradle çalıştırılabilir değil**. Bu yüzden **derleme, birim testi, APK üretimi ve cihaz testi YAPILAMADI**. Yapılan doğrulama: tüm değişen Kotlin dosyalarında tree-sitter ile sözdizimi kontrolü (30 dosya / 0 hata), XML geçerliliği, statik sembol/çağrı sitesi taraması, katalog ve APK içeriğinin Python ile incelenmesi, GitHub üzerinden canlı katalog doğrulaması. Aşağıdaki "çözüldü" ifadeleri **kod düzeyinde** çözüldü anlamındadır; çalışma zamanı davranışı cihazda doğrulanmalıdır.

---

## 1. Yönetici Özeti

Ekran görüntüsündeki tek belirti (`Disabled` + kapalı anahtar) aslında **birbirinden bağımsız 9 ayrı kök nedenin** görünen ucuydu:

| # | Kök neden | Sonuç |
|---|---|---|
| 1 | "Aktif/pasif" tercihi `SourceHealthStatus.Disabled` alanına yazılıyordu | Kaynak bir kez kapatılınca arama/listeden **tamamen kayboluyor**, telefonda tekrar açacak anahtar yoktu |
| 2 | Ayarlar `getAvailableSources()` (filtrelenmiş liste) kullanıyordu | Kapalı kaynak ayarlarda görünmüyor → açılamıyor |
| 3 | 5 KB'dan küçük **her** repo数组 yanıtı "Keiyoushi decoy" sanılıyordu | 487 baytlık geçerli özel Manga-TR kataloğu yerine ~1396 Keiyoushi eklentisi listeleniyordu |
| 4 | V1 katalogda mutlak `url` / string `versionCode` okunmuyordu | `builds/apk/...` adresi yanlış türetiliyor, güncelleme karşılaştırması `0` ile yapılıyordu |
| 5 | Sadece `raw.github.com` yansısı deneniyordu | Yansı/redirect bozulunca özel katalog ve APK indirme **sessizce** boş dönüyordu |
| 6 | Özel repo APK'sı debug anahtarıyla imzalı; `TrustManager` sadece resmî Keiyoushi parmak izlerini tanıyordu | `Signature untrusted` → kurulum/güncelleme düşüyor |
| 7 | Otomatik güncelleme **sadece** Keiyoushi index'ine bakıyordu; paket adı dosya adından türetiliyordu | Özel Manga-TR hiç güncellenmiyor, `versionCode` `-1` dönüyordu |
| 8 | Motor tespiti sadece `classes.dex`'e bakıyordu | Gerçek Manga-TR kodu `classes10.dex` içinde → yanlış motor (`CUSTOM_HTML`) |
| 9 | Okuyucu: paralel detay+bölüm isteği, tek seferlik restore, bölümle yenilenmeyen pager, Webtoon'da ilerlemenin 0'a yazılması, arşiv sayfasının UI'a verilmemesi | Boş bölüm listesi, yanlış sayfada açılış, boş/bozuk görseller |

Bunlara ek olarak **arama/popüler liste** tarafında 4 hata daha bulundu: `LaunchedEffect(mergedMangas.size)` sonsuz sayfalama, `patchState()` hata kodunu düşürmesi, kaynak hatalarının boş liste olarak yutulması ve "Tümü" seçiliyken sayfalama yapılmaması.

---

## 2. Ekran Görüntüsündeki Anahtar Hakkında Açıklama

Bu depoda (`a6dda9d`, tek commit) **telefon arayüzünde** kaynak açma/kapama anahtarı **yoktu**: `MangaViewModel.toggleMangaSource()` doğrudan sağlık durumunu `Healthy`/`Disabled` yazıyor, TV companion sunucusu `health != Disabled` ile `isEnabled` üretiyordu. Dolayısıyla ekran görüntüsündeki anahtar ya farklı bir derlemeden / farklı bir UI yolundan (ör. sağlık raporu satırı) geliyor ya da uygulamaya sonradan eklenmiş bir arayüz öğesidir. **Kesin olarak hangi sürümden geldiği bu depodan belirlenemedi** — git geçmişi tek commit olduğu için karşılaştırma yapılamadı.

Çözüm olarak anahtar **artık gerçek bir duruma bağlı**: hem `MangaExtensionTab` kurulu kaynak satırında hem kaynak sağlık raporu satırlarında hem `MangaSourceHealthScreen` kartlarında hem TV companion `isEnabled` alanında aynı `manga_source_enabled` tercihi kullanılıyor. `Disabled` rozeti artık kullanıcı tercihi değil, yalnızca geçmiş sürümlerden kalmış teşhis artığıdır ve migrasyonda temizlenir.

---

## 3. Yapılan Değişiklikler

### 3.1 Aktivasyon modeli (yeni dosyalar)

| Dosya | İçerik |
|---|---|
| `data/manga/MangaSourceEnabledStore.kt` **(+)** | Ayrı `manga_source_enabled` SharedPreferences. Varsayılan `true`; `isEnabled`, `setEnabled`, `hasExplicitPreference`, `ensureExplicitDefault`. |
| `data/manga/MangaTrCompat.kt` **(+)** | Paket/id, kanonik ve bilinen domainler, bot/üye kapısı metin tespiti, `readerUrl()` (domain'siz `/manga-.../bolum...` → mutlak URL). Kaynak kodunu taklit etmez, sadece uygulama tarafı uyumluluk bilgisidir. |

`MangaSourceStateStore.clearLegacyDisabledStatus()` eklendi; `MangaSourceRepository` içinde `getInstalledSources()` (kapalılar **dahil**), `isSourceEnabled()`, `setSourceEnabled()`, `migrateLegacyDisabledStates()`, `awaitExtensionsLoaded()` eklendi. `MangaViewModel` artık 16 yerde `getAvailableSources()` yerine `getInstalledSources()` kullanıyor; startup'ta eklenti yüklemesini `Job.join()` ile bekliyor, eski `Disabled` kayıtlarını migrate ediyor ve `MangaExtensionLoader.sourceChanges` akışını dinliyor.

**Güvenlik notu:** `clearAll()` (tanılama temizliği) eski prefs dosyasını temizler; aktivasyon ayrı dosyada olduğu için **kullanıcı tercihi silinmez**.

### 3.2 Ayarlar / TV geçişi

`SettingsScreenParameters.MangaSettings` → `SettingsScreen` → `KitsugiAddonsSettingsDialog` → `MangaExtensionsTab` zincirine `onGetMangaSourceEnabled` / `onToggleMangaSource` eklendi (varsayılanlı parametreler, `KitsugiStreamScreen` gibi diğer çağrı yerleri etkilenmedi). `AppRootMangaPages` → `MangaSourceHealthScreen` → `HealthReportTab` aynı şekilde bağlandı. `MangaBrowseScreen` kendi ayarlar penceresine de iletiyor. `TvRootScreen` companion `isEnabled` alanı artık `mangaViewModel.isMangaSourceEnabled(source)`.

`MangaSourceRepository.setSourceEnabled()` değişimde `MangaExtensionLoader.notifySourceChanged()` yayınlıyor; `MangaBrowseScreen`'in `LaunchedEffect` anahtarı artık `key:enabled` biçiminde olduğu için toggle sonrası browse listesi de tazeleniyor.

Yeni stringler (`values/strings.xml`, `values-en/strings.xml`): `manga_source_disabled`, `manga_source_verify_in_browser`, `manga_source_verify_hint`.

### 3.3 Repo / katalog / indirme

`data/remote/MangaRepoClient.kt`:
- `isDecoyResponse(body)` → **`shouldUseKeiyoushiFallback(repoUrl, body)`**: decoy kontrolü artık yalnızca Keiyoushi / `index.min.json` için. 487 baytlık üçüncü parti katalog geçerli veri olarak ayrıştırılır.
- `fetchBodyWithMirrorFallback(url)`: önce `applyMirror(...)` (raw.github.com), başarısız/hatalı ise **orijinal URL**. Hem `fetchExtensionsAutoDetect` hem `fetchExtensions` hem `fetchRepoMeta` buna geçti.
- V1: `code` / `versionCode`, mutlak `url` / `apkUrl` desteği; V2: string veya int `versionCode`.
- `fetchRepoMeta(repoJsonUrl)` overload'u: her repo için `repo.json` → `meta.signingKeyFingerprint` güveni.
- Bozuk/decoy olmayan array'lerde `parseKeiyoushiV1Index` / `parseNativePluginList` ayrımı.

`MangaExtensionLoader.downloadExtension()`: yansı + orijinal aday sırası, her aday için 3 deneme, aday değişirken temp dosya temizliği, exception yutulup diğer adaya geçilmesi.

**Canlı doğrulanan katalog adresleri** (3 Kasım 2026):
- V1: `https://raw.githubusercontent.com/f26901964-eng/Kitsugi-Manga-Plugins/builds/index.json` → 487 bayt, tek `Manga-TR`, `pkg=eu.kanade.tachiyomi.extension.tr.mangatr`, `code=106024`, `version=1.6.24`, mutlak `url` `.../builds/apk/tachiyomi-tr.mangatr-v1.6.24.apk`.
- V2: `https://raw.githubusercontent.com/f26901964-eng/Kitsugi-Manga-Plugins/main/builds/index.v2.json` → `extensionList.extensions[0]`, string `versionCode:"106024"`, `resources.apkUrl`, `sources[0].language=tr`, `type=manga`.
- `.../builds/index.v2.json` (builds dalı kökü) **404** — doğru adres `main/builds/index.v2.json`'dur; V1 için `builds` dalındaki `index.json` kullanılmalı.

İkisi de yeni ayrıştırıcıyla uyumludur. `main` dalı katalogları otomatik güncellenmez (repo README'si).

### 3.4 İmza güveni ve kapsamı

`TrustManager.TRUSTED_SIGNATURES`'a özel Manga-TR debug sertifikası eklendi:
`a53115764ff5e259e0e5acec3c58a85166e88aad110cd8a20674eefea5d6c016`
(`CN=Android Debug`, 2026-07-03 → 2056-06-25, APK v2 imza bloğundan `openssl` ile doğrulandı).

`MangaExtensionLoader.loadExtension(..., trustSignature: Boolean = false)` eklendi. **Kapsam bilinçli olarak daraltıldı:**
- Resmî Keiyoushi kataloglarında `trustSignature` **asla** `true` olmaz (`MangaViewModel.allowCustomSignature()` `keiyoushi` içeren URL'leri reddeder). Ele geçirilmiş bir yansıdan gelen farklı imzalı APK sessizce kabul edilmez.
- Yalnızca (a) kullanıcının **elle eklediği** üçüncü parti repo katalogları, (b) kullanıcının **dosya seçiciyle seçtiği** APK, (c) özel repodan gelen otomatik güncelleme için `true`.
- Tekli katalog kurulumundaki `untrustedSignatureToConfirm` onay akışı korundu.

`MangaViewModel.addMangaRepo()` ayrıca repo `repo.json`'ını okuyup `TrustManager.trustRepoSigningKey()` ile anahtar rotasyonunu destekliyor. Özel repoda `repo.json` yoksa iki 404 denemesi yapılır; zararsızdır (en fazla küçük gecikme).

### 3.5 Otomatik güncelleme

`MangaExtensionAutoUpdater`:
- Keiyoushi + `manga_repos/repo_urls` içindeki özel repolar birlikte taranır; ikisi de boşsaa `REPO_FETCH_FAILED`.
- Aynı paket iki katalogdaysa **kullanıcının reposu kazanır** (`customEntries + keiyoushi` sırası hem `distinctBy` hem `associateBy` için aynı).
- Kurulu APK'ların paket adı `readPackageName()` ile **manifestten** okunur (`tachiyomi-tr.mangatr-v1.6.24.apk` gibi dosya adlarından türetme yanlıştı).
- Yeni `getInstalledVersionCode(context, file: File)` overload'u ile versionCode doğrudan dosyadan okunur; `-1` düşmesi engellendi.
- Özel paketlerde güncelleme öncesi yeni imza parmak izi güven listesine eklenir, `trustSignature` ile yeniden yüklenir.
- `MangaSourceRepository.installExtensionFromUri()` elle kurulan APK dosyasını paket adına normalize eder → aynı eklenti iki kez "kurulu" görünmez.

### 3.6 Motor tespiti ve domain

`MangaExtensionLoader.detectEngineFromApk()` artık **tüm `classes*.dex`** dosyalarını tarar. Gerçek APK'da (`/tmp/mangatr_research/mangatr.apk`, 304707 bayt) 11 dex var; `KeiSource`, `MangaTR`, `DDoSGuardInterceptor`, `ReaderGateDto` **yalnızca `classes10.dex`** içinde. Ek olarak Manga-TR extension id'si için doğrudan `KEI_SOURCE` kısa yolu var.

`SourceMirrorResolver`:
- Manga-TR için rastgele TLD/`manga-tr-com` türetimi kaldırıldı; yalnızca `MangaTrCompat.KNOWN_DOMAINS` denenir.
- `tryResolveAndActivateMirror()` Manga-TR için **erken `false`** döner: DDoS-Guard + tek kullanımlık gate token'ı nedeniyle hata sonrası domain değiştirip isteği tekrar çalıştırmak oturumu geçersiz kılar; `isReachable()` 403/503'ü "erişilebilir" saydığı için koruma sayfasını mirror sanabilirdi. Kullanıcı domain'i **elle** değiştirebilir.

### 3.7 Okuyucu (en kritik kullanıcı yolu)

| Dosya | Düzeltme |
|---|---|
| `MangaDetailViewModel` | `refreshDetails()` artık `suspend`; init'te detay → bölüm listesi **sıralı** çalışıyor (Manga-TR detay HTML'i imzalı `listKey` taşıyor, paralel istek rate-limit/gate çakışması yaratıyordu). |
| `MangaReaderViewModel` | `loadChapterList()` `suspend`; init'te bölüm listesi → `restoreProgress()` sıralı. `verificationUrl` eklendi; `CaptchaRequiredException` ve üye girişi/bot koruması metinleri için WebView doğrulama adresi üretilir (uygulamanın `WebViewCookieJar.syncFromWebView()`'i `KitsugiWebViewDialog.onPageFinished` içinde çağrıldığı için cookie'ler OkHttp'e gerçekten aktarılır). |
| `MangaReaderScreen` | `pagerState`/`listState` `key(currentChapter.url)` ile bölüm başına yeniden oluşturulur; restore `restoredChapterUrl` + `isLoadingPages` ile tek seferlik; **Webtoon modunda pager ilerleme yazımı engellendi** (eskiden sürekli 0. sayfaya geri yazılıyordu); "Tarayıcıda doğrula" butonu + `KitsugiWebViewDialog`, kapanınca `retryLoadPages()`; boş sayfa ekranına "Tekrar Dene". |
| `TvMangaReaderScreen` | Aynı restore/pager/Webtoon düzeltmeleri TV okuyucusuna da uygulandı. |
| `MangaModels.kt` | `MangaPage.cachedFile: File?`. |
| `MangaPageLoaderV2` | Arşiv (ZIP/CBZ) sayfası `archive://...` anahtarıyla disk cache'e yazılır, `cachedFile`/`imageUrl`/`stream` set edilir, decode hatasında `Error`; HTTP cache-hit ve indirme yollarında da `cachedFile` set edilir. |
| `MangaPageItem` | `page.cachedFile` kullanır; URL'den dosya türetmeye **geri düşüm** olarak kalır. Arşiv sayfaları ve fragment/query içeren özel görsel URL'leri artık doğru dosyayı gösterir. |
| `MangaReaderViewModel` | `MangaPageLoaderV2(...)` **named argument** ile `archiveFile` alır (4. parametre `preloadAhead` olduğu için konumsal geçiş arşiv yolunu preload sayısına yazıyordu). `file://`/mutlak `.zip`/`.cbz` bölüm URL'lerinden arşiv otomatik çözülür; `Factory` opsiyonel `archiveFile` alır. |

**Not:** `MangaPageLoader.kt` (V1 loader) projede hiçbir çağrı sitesine sahip değil; ölü kod olduğu için değiştirilmedi. `MangaCache.putImageToCache()` geçersiz görselde `IOException` fırlatır; arşiv yolu artık bunu yakalayıp sayfayı `Error` yapıyor.

### 3.8 Arama / popüler liste

- `MangaSourceResult.error: String?` eklendi; `MangaSourceRepository.fetchPopular()` hatayı **yutmuyor**. `MangaBrowseViewModel.popularError` UI'da gösteriliyor + "Tekrar Dene".
- `patchState()` artık `errorCode` parametresini gerçekten yazıyor (eski kod `it.errorCode`'u koruduğu için `KTS-SRC-...` hiç UI'a ulaşmıyordu).
- `MangaBrowseScreen`: `LaunchedEffect(mergedMangas.size)` kaldırıldı → `LazyGridState` + `derivedStateOf` ile yalnızca listenin sonuna yaklaşınca `loadNextPage()`. Boş sayfa + `hasNextPage=true` sonsuz döngüsü `result.mangas.isNotEmpty()` / `matched.isNotEmpty()` kontrolüyle kapatıldı. "Tümü" filtresinde de sayfalama çalışıyor; `lastAutoLoadKey` aynı sayfanın tekrar istenmesini engelliyor.
- `refreshSources()` silinmiş/devre dışı kaynağı seçili bırakmıyor; `LaunchedEffect(installedSourceKeys)` kurulum/silme/toggle sonrası browse listesini tazeliyor.
- `SourceFailureClassifier`: Manga-TR'nin "üye girişi", "bot korumasını", `verify.php` gibi mesajları `Auth` olarak sınıflanıyor (genel `Broken/Unknown` yerine). `MangaTrCompat` marker listesinden `ddos-guard` **çıkarıldı** çünkü site bunu her yanıta ekler; genel 403'ü "üye girişi" sanmak yanlış olurdu.

---

## 4. Doğrulama

| Kontrol | Sonuç |
|---|---|
| `git status` / dal | 30 değişen Kotlin + 2 yeni Kotlin + 2 XML, dal `arena/0a58b6d5-kitsugi-beta` |
| tree-sitter Kotlin sözdizimi (30 dosya) | **0 hata** |
| `values/strings.xml`, `values-en/strings.xml` XML parse | **Geçerli**, 17.544 string, **0 duplicate** |
| Yeni sembollerin tanım + çağrı sitesi taraması (30 sembol) | **Tamamı bulundu** |
| `isDecoyResponse` / `hasRestoredProgress` artıkları | **Yok** (TV okuyucu dahil temizlendi) |
| `MangaSourceResult(...)`, `loadExtension(...)`, `getInstalledVersionCode(...)`, `fetchRepoMeta(...)` çağrı ariteleri | **Uyumlu** |
| `Switch` importları (`gradient.Switch` / Material3) | **Uyumlu** |
| Özel APK DEX analizi (Python/zipfile) | 11 dex; Manga-TR kodunun `classes10.dex`'te olduğu **doğrulandı** |
| V1/V2 katalog içeriği (canlı GitHub) | Yukarıdaki adreslerle **doğrulandı** |
| APK v2 imza sertifikası | SHA-256 `a5311576...d6c016`, `CN=Android Debug` |
| Gradle / `kotlinc` / Android SDK / cihaz testi | **YAPILAMADI** — ortamda `java`, `javac`, SDK, `local.properties` yok |
| Canlı site (manga-tr.com) HTTP/oturum/gate/strip sırası | **Test edilmedi** — ortamda siteye erişim yok; `Kitsugi-Manga-Plugins` dokümanları da bunu açıkça "yapılmadı" olarak belirtiyor |

### Kullanıcı tarafında yapılması gereken manuel test (cihaz)

1. Ayarlar → Manga Kaynakları → Repo ekle: `https://raw.githubusercontent.com/f26901964-eng/Kitsugi-Manga-Plugins/builds/index.json`
2. `Repositories (1)` altında **yalnızca Manga-TR** görünmeli (1396 Keiyoushi eklentisi değil).
3. Kur → `Installed (1)`, anahtar **açık**, sağlık `Healthy`/`Unknown` ( **`Disabled` yazılmamalı** ).
4. Anahtarı kapat → satır **kaybolmamalı**, `Devre dışı` chip'i + kapalı anahtar görünmeli; tekrar aç → arama/popüler listeye geri dönmeli. Aynı davranış `Kaynak sağlık raporu` ve TV companion listesinde kontrol edilmeli.
5. Keşfet → Manga → popüler liste ve arama (`/arama.html` sonuçları) dolmalı; aşağı kaydırınca yalnızca **bir sonraki** sayfa gelmeli (sonsuz istek olmamalı).
6. Bir seri → detay → bölüm listesi dolu gelmeli → bölüm açılınca görseller görünmeli; ilerleme kaydedilip bölüm değişince doğru sayfada açılmalı; Webtoon modunda ilerleme 0'a düşmemeli.
7. Üye/bot koruması çıkarsa "Tarayıcıda doğrula" → WebView'da doğrula → kapat → sayfalar yeniden denenmeli.
8. `Ayarlar → Otomatik güncellemeyi zorla` özel repo için de çalışmalı.

---

## 5. Bilinen Kalan Riskler / Yapılmayanlar

1. **Derleme ve cihaz doğrulaması yok.** Kotlin tip çözümlemesi ( overload çakışması, Compose `@Composable` çağrı kısıtları, import çakışması ) yalnızca statik olarak gözden geçirildi; gerçek `gradlew :app:assembleDebug` bir JDK/SDK olan ortamda çalıştırılmalı.
2. **Canlı site doğrulaması yok.** Manga-TR'nin `/cek/fetch_pages_manga.php` imzalı POST'u, tek kullanımlık reader token'ı, iki aşamalı XOR config, karıştırılmış şerit sırası ve `/cek/verify.php` kapısı **APK'nın kendi kodu**; bu depoda taklit edilmedi. Site yeniden tasarım değiştirirse eklenti tarafında yeni APK gerekir — Kotlin kaynağını değiştirmek kurulu APK'yı güncellemez.
3. **WebView → OkHttp cookie köprüsü** `KitsugiWebViewDialog.onPageFinished` içinde `Injekt.get(NetworkHelper::class.java)` ile çalışır. `KitsugiApplication` bunu singleton olarak kaydediyor; eğer bir derlemede Injekt kaydı atlanırsa fallback `NetworkHelper(context)` **farklı** bir cookieJar oluşturur ve doğrulama cookie'si OkHttp'e geçmez. Bu fallback'in kaldırılması/tekil örneğe zorlanması ayrı bir iyileştirme olarak kalıyor.
4. **Elle kurulan APK'da `trustSignature = true`** kullanıcı eylemine dayalı bir güven kararıdır ve parmak izi kalıcı olarak `security_trust` içine yazılır. Debug anahtarı paylaşımlı olabileceğinden, ileride "repo → parmak izi" eşlemesiyle **kapsam başına** güven daha sıkı hale getirilebilir.
5. **`MangaReaderNavState` / TV navigasyonunda `archiveFile` alanı yok.** Yerel ZIP/CBZ okuma yalnızca `chapter.url` `file://...` veya mutlak `.zip`/`.cbz` yol ise otomatik etkinleşir; uygulamanın indirme/arşiv yöneticisi bu biçimde URL üretmiyorsa arşiv okuyucu hâlâ erişilemez. Bu bir "altyapı hazır, UI bağlantısı yok" durumudur.
6. **Ekran görüntüsündeki anahtarın kökeni** bu depodaki tek commit nedeniyle kesin belirlenemedi; farklı bir derleme/UI yolundan geldiği varsayıldı. Yeni anahtarlar gerçek duruma bağlı olduğu için işlevsel fark kapatılmıştır.
7. `addMangaRepo()` özel repolarda var olmayan `repo.json` için iki 404 isteği atar; işlevsel değil, yalnızca küçük gecikme. İstenirse Keiyoushi dışı repolarda atlanabilir.
8. `MangaSourceStateEntity` (Room) içinde `enabled` kolonu **yok**; sağlık raporu anahtarı `installedSources` üzerinden besleniyor. Rapor dışa aktarımında aktivasyon bilgisi yer almaz.

---

## 6. Değişen Dosya Listesi

**Yeni:**
- `app/src/main/java/com/kitsugi/animelist/data/manga/MangaSourceEnabledStore.kt`
- `app/src/main/java/com/kitsugi/animelist/data/manga/MangaTrCompat.kt`
- `MANGA_TR_TAM_DENETIM_VE_COZUM_RAPORU_2026-11-03.md` (bu dosya)

**Değişen (30 Kotlin + 2 XML):** `TrustManager`, `MangaExtensionAutoUpdater`, `MangaExtensionLoader`, `MangaModels`, `MangaSearchCoordinator`, `MangaSource`, `MangaSourceRepository`, `MangaSourceStateStore`, `SourceFailureClassifier`, `SourceMirrorResolver`, `loader/MangaPageLoaderV2`, `MangaRepoClient`, `MangaViewModel`, `KitsugiAddonsSettingsDialog`, `addons/MangaExtensionTab`, `MangaBrowseScreen`, `MangaBrowseViewModel`, `MangaDetailViewModel`, `MangaReaderScreen`, `MangaReaderViewModel`, `MangaSourceHealthScreen`, `components/MangaPageItem`, `SettingsScreen`, `SettingsScreenParameters`, `TvRootScreen`, `tv/manga/TvMangaReaderScreen`, `AppRootMangaPages`, `AppRootSettingsExtras`, `values/strings.xml`, `values-en/strings.xml`.

---

## 7. Dış Kaynaklar

- `keiyoushi/extensions-source@main` `src/tr/mangatr` ve birleşen PR #19168 (17 Eyl 2026) — site yeniden tasarımı sonrası arama/bölüm/okuyucu düzeltmelerinin karşılaştırma temeli. Keiyoushi repo URL'i `https://github.com/keiyoushi/extensions/raw/repo/index.pb`; `CONTRIBUTING.md` `@Source` → `keiyoushi.source.Generated` / `SourceFactory`, `KeiSource` `libVersion=1.6`.
- `f26901964-eng/Kitsugi-Manga-Plugins` README + `docs/mangatr/PLAN-VE-DURUM.md` + `TEST-RAPORU.md` — yalnızca Manga-TR etkin, `builds` dalı kullanılmalı, `main` katalogları otomatik güncellenmez, 4 Python snapshot testi geçti; **canlı site/oturum/gate/görsel/strip sırası, JDK/Gradle derlemesi, yeni APK ve Kitsugi-Beta cihaz testi yapılmadı**.
- `Kitsugi-Plugins/main` deposunda manga yolu **yok** (Cloudstream/video); doğru repo `Kitsugi-Manga-Plugins`'tir.
- GitHub issue/geçmiş: #16816 (arama sonuç yok), #12185 (domain `https://manga-tr.com/`), #9657 (görsel/WebView 404), #221 (403).
