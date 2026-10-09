# Kitsugi — "Eklentilerden Veri Gelmiyor / Arama Sıfır" Kök Neden & Fix Raporu (v2.4.226 adayı)

**Tarih:** 2026-10-09
**Dal:** `arena/3485fc3d-kitsugi-beta` (temel: `cb1612d` / v2.4.225)
**Şikâyet:** *"Normalde birçok sitede olması gereken diziden bile 1 tane video verisi
gelmiyor. Hiçbir eklenti düzgün çalışmıyor; eklentilerden dizi/film/anime adı ile veri
çekemiyoruz. CloudStream yabancı kaynaklarda sonuç alıyor, ben Türkçe kaynaklardan
alamıyorum."* + ekran görüntüsü (Supernatural S01E01 — yalnızca Torrentio sonuçlu).

---

## 1. Yöntem

- `CsStreamRunner.kt` / `CsRuntimeInit.kt` / `CsPluginLoader.kt` / `CsTitleMatcher.kt` /
  `StreamViewModel.kt` satır satır incelendi.
- Paketli CloudStream kütüphanesi (`repo/com/lagradost/api/library-android/1.0.2-local`)
  AAR + **sources.jar** çıkarılarak gerçek API yüzeyi doğrulandı (`app` = NiceHttp
  `Requests`, `search(query,page)` → `SearchResponseList` vb.).
- Canlı eklenti davranışı için `f26901964-eng/Kitsugi-Plugins` (GitHub) kaynakları ve
  `Animeler/classes.dex` **bytecode düzeyinde** (androguard) söküldü:
  `AnimelerHelper.setup()` = `(paket ∈ hataliPaketAdlari) ∨ (imza ∈ izinliImzaHashleri)`;
  `search()/load()` başında `isAllowedVersion == false` ise **anında boş liste**.
  Mevcut `applyHelperPatches` (alan + setter + universalSet) bu semantikle UYUMLU —
  anti-leech katmanı bozan değil, bu oturumda doğrulanan taraf.
- Sonuç: anti-leech ve UI akışı sağlam; **uygulamanın kendi kendini sabote eden dört
  davranışı** aşağıda.

## 2. Kök Nedenler (kod kanıtlarıyla)

### KN-1 (KRİTİK) — Domain tabloları SAĞLIKLI `mainUrl`'ü eziyordu
`applyDomainFix()`, `normalize(current) != normalize(remote)` farkında
`BUILTIN_DEFAULT_DOMAINS` (≈90 sabit) + `domain_fixes.json` (57) girdisini
**her aramada** (`ensurePluginReady` → `safeSearch` başı) zorla uyguluyordu.
Resmi CloudStream asla bunu yapmaz; plugin kendi `mainUrl`'ünü kendi yönetir
(örn. `FilmMakinesi.resolveDomain()` canlı mirror'ı kendisi bulur). Tablo eskidiğinde
eklentinin canlı domaini ölü adrese taşınıyor → tüm varyantlar aynı ölü domaine gidiyor →
**arama sıfır**. `revertForcedDomain` güvenlik ağı vardı ama yalnızca TÜM varyantlar boş
döndükten SONRA bir kez tetikleniyordu; 18 varyant × ölü domain = bütçe çöpü.

Kanıt: eski kod `val remoteUrl = dynamicUrl ?: builtinFallback; if (... || normalize(currentUrl) != normalize(remoteUrl)) { api.mainUrl = remoteUrl }`.
Tabloda çelişkili çiftler bile var: `KNOWN_DOMAIN_FIXES["fullhdfilm.pro"]→hdfilm.us` ama
`BUILTIN["fullhdfilm"]→https://fullhdfilm.pro` (builtin dalı erken döndüğü için lokal
kural hiç çalışmıyordu).

### KN-2 (YÜKSEK) — `getLoadUrl()` boş sonucu aramayı KİLİTLİYORDU
`runGetStreams`: `nativeResult != null → return nativeResult`. `getLoadUrl()` URL döndürüp
`load/extract` boş dönünce (site yapısı değişti, S01E01 eşleşmedi vb.) **title-search
fallback hiç koşmuyordu** → "ID'si olan her içerikte" eklenti sıfır.

### KN-3 (YÜKSEK) — OkHttp disk Cache'i scraper istemcisinde
`CsRuntimeInit` 50 MiB `Cache` takıyordu. WordPress tabanlı TR siteler arama/detay
sayfalarını `Cache-Control: max-age` ile servis edebiliyor → bayat (ilk istek anında
CF-challenge/boş dönmüşsa o hâliyle donmuş) cevaplar süresiz tekrar sunuluyordu.
Resmi CloudStream istemcisinde disk cache yoktur.

### KN-4 (ORTA) — GLOBAL `searchSemaphore(12)` + paralel 30+ eklenti
Stream ekranı tüm eklentileri aynı anda başlatıyor; semaphore GLOBAL olduğu için
kuyruktaki eklenti izin beklerken kendi 20 sn'lik arama bütçesi doluyordu → tek sorgu
atamadan "arama sıfır".

## 3. Uygulanan Düzeltmeler

| # | Dosya | Değişiklik |
|---|---|---|
| 1 | `CsStreamRunner.kt` | `applyDomainFix` yeniden yazıldı: sabit (builtin) tablo **yalnızca** boş/`"/"`/zehirli (`x.anizium.co` vb.) veya `KNOWN_BROKEN_DOMAINS`'teki domaini kurtarır. **Sağlıklı domain'i yalnızca açılışta tazelenen uzak tablo (`domain_fixes.json`) geçebilir** — o da `forcedDomainOriginals` revert ağıyla. `KNOWN_DOMAIN_FIXES` eski→yeni host eşlemeleri aynen korunur. |
| 2 | `CsStreamRunner.kt` | `tryNativeIdResolution` BOŞ liste dönerse title-search fallback'e düşer (`!nativeResult.isNullOrEmpty()`); boş-native artık `CsTrace`'e işlenir. |
| 3 | `CsRuntimeInit.kt` | OkHttp `Cache` **kaldırıldı** (`.cache(httpCache)` + `httpCache` + kullanılmayan importlar). Scraper istemcisi her zaman taze çeker. |
| 4 | `CsStreamRunner.kt` | `searchSemaphore` 12 → **24** (global huni genişletildi; load semaphore'una dokunulmadı). |
| 5 | `app/build.gradle.kts` | `appVersionName` 2.4.225 → **2.4.226**. |

Davranış özeti: eklenti artık **resmi CloudStream gibi** önce kendi domain'iyle aranır;
tablolar sadece kurtarma amaçlıdır. ID kısayolu boş dönerse başlık araması devreye girer;
cache'siz taze istekler; 24 kişilik arama kapısı.

## 4. CloudStream Neden Sonuç Alıyor? (kıyas)

Resmi CS: (a) `mainUrl`'e dışarıdan yazmaz — plugin domainini kendi yönetir;
(b) arama sonuçlarını HAM gösterir, katı eşik yok; (c) istemcide disk cache yok;
(d) global arama kapısı küçük ama arama bütçesi tek eklentiliktir.
Kitsugi'nin çok eklentili paralel mimarisi (b)+(d)'yi gerektirir; (a) ve (c) bu oturumda
CS davranışına hizalandı, (b)/(d) önceki oturumların eşikleriyle dengede bırakıldı.

## 5. Doğrulama

- `tree-sitter-kotlin` ile her iki düzenlenmiş dosya **SYNTAX OK** (ERROR düğümü yok).
- Düzenleme bölgeleri elle satır satır yeniden okundu; `currentUrl` tekil bildirim,
  `return` akışları ve revert ağı tutarlı.
- **Yapılamayan:** bu ortamda JDK/Gradle/Android SDK ve Maven erişimi yok → Kotlin
  **derlenemedi**; Türkçe sitelere ağ erişimi yok → canlı arama doğrulanamadı.
  Derleme + cihaz testi kullanıcı tarafında (`build-apk.ps1`).

## 6. Cihazda Doğrulama Planı

1. `adb logcat -s CsStreamRunner CS_SEARCH_ERR PLUGIN_DIAG` ile Supernatural S01E01 aç:
   - `Domain güncellendi:` log'u artık **yalnızca uzak tablo** için gelmeli;
     `Boş/ölü domain kurtarıldı:` yalnızca gerçekten boş/ölü ise gelmeli.
   - `search('Supernatural') → N sonuç` satırları DiziFilmSiteleri provider'larında
     (FilmMakinesi, SezonlukDizi, Dizipal…) N>0 olmalı.
2. Eklenti arama ekranında "Supernatural" yaz → `searchAllAddons` aynı `safeSearch`'ten
   geçtiği için sonuçlar gelmeli.
3. Kart boş kalırsa artık GERÇEK sebep yazmalı (DNS/CF/timeout) — sebebe göre
   `domain_fixes.json`'a tek girdi eklemek yeterli (kod değil veri değişikliği).
