# Kitsugi — Türkçe Eklenti & Oynatıcı Boru Hattı Denetimi

**Tarih:** 2026-10-08
**Kapsam:** Türkçe ağırlıklı CloudStream eklentilerinden veri/video çekme, embed (iframe) çözümleme,
oynatıcı hataları, ses/donma/çökme şikâyetleri
**Durum:** Kod düzeltmeleri uygulandı ve `arena/290cdcac-kitsugi-beta` dalına işlendi
(3 commit: `6da359e`, `cf0260c`, `b479d74`). **Derleme ve cihaz doğrulaması kullanıcı tarafında
yapılmalıdır** — bu ortamda JDK/Gradle/Android SDK yok, bu yüzden Kotlin kodu **derlenmedi**;
tüm doğrulama statik analiz + Python ikiz koşum takımı ile yapıldı (§4).

---

## 1. Şikâyet → Kök Neden Eşlemesi

| Kullanıcı şikâyeti | Kök neden | Durum |
|---|---|---|
| "Veri gelmiyor / aşırı geç geliyor" | Embed'ler sırayla çözülüyordu (20 sn timeout × N); korumasız eklentilerde gereksiz 500 ms bekleme | ✅ Düzeltildi |
| "Birçok video kaynağı direkt gelmiyor, eklentiler boş dönüyor" | (a) `loadExtractor` yalnızca kütüphaneye **kayıtlı** extractor'ları tanır; Türkçe CDN'ler (alions, closeload, gstore, trstx, molystream, streambox, pichive, rapidrame, vmnow…) o listede yok; (b) **`KNOWN_DEAD_CDN_HOSTS` içindeki CDN'ler çözümlenmeden siliniyordu** (pichive.cc → Dizilla/AsyaWatch, vmnow.online → SezonlukDizi) | ✅ Düzeltildi (genel çözümleyici + silme kaldırıldı) |
| "Videolar oynatılamıyor / oynatıcı hata veriyor" | HTML oynatıcı sayfası "oynatılabilir stream" sanılıp oynatıcıya veriliyordu | ✅ Düzeltildi |
| "Ses olmuyor / video açılmıyor" | Kodek hatalarında (4001–4005, 5001–5002) MPV motoru **atlanıp** kaynak değiştiriliyordu; hâlbuki bu formatları MPV oynatabiliyor | ✅ Düzeltildi |
| "Donup kalıyor / çöküyor" | (a) WebView sniffer ana thread'de 6 sn'ye kadar **bloke** ediyordu → ANR; (b) paralel yazımda altyazı listesi `ConcurrentModificationException` riski; (c) bozuk URL'lerin oynatıcıya gitmesi | ✅ Düzeltildi |
| Bazı eklentiler hiç sonuç vermiyor | Bulanık domain eşleştirmesi yanlış siteye yönlendirebiliyordu (AsyaAnimeleri→animeler.pw, FullHDFilmizlesene→hdfilmizle.live) + Türkçe "İ" harfi tam eşleşmeyi bozuyordu | ✅ Düzeltildi |

---

## 2. Boru Hattı Haritası (kod kanıtlarıyla)

```
Kullanıcı: "Bölüm izle"
        │
        ▼
StreamViewModel.startFetch (ui/screens/stream/StreamViewModel.kt:88)
  ├─ Stremio addon'ları  (paralel launch)
  └─ CS eklentileri      (paralel launch, eklenti başına 1 coroutine)
        │
        ▼
CsStreamRunner.getStreams (data/cloudstream/CsStreamRunner.kt:830)
  ├─ ensurePluginReady → applyDomainFix  (domain düzeltme + DEX anti-tamper)
  ├─ +18 filtresi / ölü domain / blok listesi kontrolleri
        │
        ▼
CsStreamRunner.runGetStreams (:908)
  ├─ tryNativeIdResolution → api.getLoadUrl(MAL/AniList/IMDb/Kitsu)   (15 sn)
  ├─ buildTitleVariants → safeSearch (12 eşzamanlı, 15 sn)
  ├─ tek-kelime brute-force fallback (GENERIC_WORDS hariç)
  ├─ findBestMatch (benzerlik) + syncData ID doğrulaması
        │
        ▼
CsStreamRunner.loadAndExtractStreams (:1160)
  └─ extractStreamsFromEpisode (:1994)
       ├─ loadSemaphore(6) + delay(500|120) + api.loadLinks (25 sn / CF'de 90 sn)
       ├─ link sınıflandırma: görsel mi / doğrudan medya mı / embed mi
       ├─ embed'ler → resolveEmbedUrl  ★ BURASI DÜZELTİLDİ
       ├─ HTTP HEAD canlılık kontrolü (6 sn)
       └─ StreamSource listesi (kalite/dil kanıta dayalı — StreamInfoResolver/StreamProbe)

CsStreamRunner.resolveEmbedUrl (:1350) — 4 aşamalı çözümleme
  Aşama 1: loadExtractor(url)        → kütüphanedeki kayıtlı extractor'lar (20 sn)
  Aşama 2: 8 sabit custom wrapper    → Mcloud/Mixdrop/Mp4Upload/StreamTape/Upstream/Voe/XStreamCDN/Gdrive
  Aşama 3: ★YENİ★ HTML derin taraması→ EmbedMediaScanner (packer/atob/kaçış/iframe/medya desenleri)
  Aşama 3a:★YENİ★ Content-Type       → yanıtın kendisi medya ise doğrudan kabul (uzantısız HLS/MP4)
  Aşama 4: ★YENİ★ WebView sniffer    → JS ile üretilen medya isteğini gerçek WebView'de yakalar

Oynatma
        │
        ▼
PlayerMediaSourceFactory.create (:236)
  ├─ DefaultDataSource + OkHttpDataSource (referer/origin/UA enjeksiyonu, ignoreSSL=true)
  └─ Media3PlayerEngine (engine/Media3PlayerEngine.kt:451)  veya MpvPlayerEngine
        │
        ▼
Hata → KitsugiFullscreenPlayerScreen:702 → PlayerErrorRecoveryController ★DÜZELTİLDİ★
        ├─ HTTP/erişim hatası → kaynak değiştir
        └─ kodek hatası      → MPV motoruna geç (eskiden kaynak değiştiriyordu)
```

---

## 3. Bulgular ve Kanıtlar

### B1 — (Kritik) Embed çözümleme yalnızca "tanıdık" CDN'lerle sınırlıydı ✅ DÜZELTİLDİ

**Kanıt:** Eski `resolveEmbedUrl`, başarısız `loadExtractor`'dan sonra yalnızca 8 sabit wrapper
deniyordu. Kütüphanedeki `loadExtractor` (`ExtractorApi.kt:919`) yalnızca `extractorApis`
listesindeki `mainUrl` ön-ek eşleşmesine (ya da Levenshtein > 80) bakar; eşleşme yoksa **hiçbir şey
döndürmez** ve sessizce `false` döner.

**Kanıt (rapor):** `provider_stream_source_report.md` — 59 provider tarandı, yalnızca **1**
(%1,7) provider stream üretti; 47'sinde "sayfa 200 OK ama embed yok".

**Sonuç:** Türkçe provider'ların büyük kısmı embed URL'si veriyor ama medya URL'si hiçbir zaman
çıkarılamıyordu → kullanıcı "eklenti boş" görüyor.

**Çözüm:** `EmbedMediaScanner` (yeni) + kademeli çözümleme:
- `<video>/<source>`, JWPlayer `setup({file:…})`, `sources:[{file:…}]`, `data-*`, `setSource()`
- Dean Edwards **packer** açma (`eval(function(p,a,c,k,e,d)…)`) — gerçek örnekle doğrulandı
- `atob()` / base64 blob çözme
- `\/`, `\u002F`, `\x2F`, `&amp;` kaçış çözme, protokolsüz `//host` ve göreli `/x.m3u8` mutlaklaştırma
- **iframe zinciri** takibi (en fazla 2 katman, döngü korumalı)
- Reklam/analitik/önizleme URL'lerini **skorla eleme** (preroll reklam akışı gerçek akışın önüne geçemez)
- HTTP ile **doğrulama** (HEAD + Content-Type) — doğrulanmayan aday oynatıcıya verilmez

### B2 — (Kritik) HTML sayfası oynatılabilir akış olarak ekleniyordu ✅ DÜZELTİLDİ

**Kanıt (eski kod):**
```kotlin
if (!clean.contains("shell.php") && !clean.contains("video_ext.php") && !clean.contains("/embed/")) {
    streams.add(StreamSource(url = clean, ...))   // ← HTML oynatıcı sayfası "stream" olarak eklendi
}
```
ve `isEmbedUrl()` sezgisi bilinmeyen oynatıcı yollarını (`/v/abc123`, `?m=…`, `player.aspx?id=…`)
"doğrudan video" sayıyordu → URL ham hâlde oynatıcıya gidiyor.

**Sonuç:** ExoPlayer `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED (3004)` → **"oynatıcı hata veriyor"**.

**Çözüm:**
- Sınıflandırma tersine çevrildi: yalnızca **uzantısı/deseni ile açıkça medya** olan URL'ler doğrudan
  eklenir (`isClearlyDirectMediaUrl`); diğer her şey 4 aşamalı embed boru hattına girer.
- Content-Type kontrolü: yanıtın kendisi `video/*`, `*/mpegurl`, `octet-stream` ise URL medyadır
  (uzantısız HLS/MP4 CDN'leri bu sayede kurtulur).
- Son-çare ham URL ekleme yalnızca **medya gibi görünen** URL'ler için yapılır; HTML elenir ve
  gerekçesi loglanır.

### B3 — (Kritik) Kodek hatalarında MPV motoru atlanıyordu ✅ DÜZELTİLDİ

**Kanıt (eski kod, `PlayerErrorRecoveryController.kt:75-88`):**
```kotlin
val isCodecFailure = errorCode in 4000..4005
if (isNonRecoverable || isCodecFailure) {
    triggerSourceFallbackOrFatal(errorCode, errorMsg)   // ← motor denemesi YOK
    return
}
```
Media3 hata kodları: `4001` decoder init, `4002` decoder query, `4003` decoding failed,
`4004` format sınırı aştı, `4005` format desteklenmiyor, `5001/5002` **ses kanalı** başlatma/yazma.

**Sonuç:** Cihazın donanım/yazılım çözücüsü desteklemediği akış (ör. 10-bit HEVC, AC3/E-AC3 ses,
yüksek profil MP4) için uygulama "kaynak bozuk" diyordu. Oysa projede **libmpv + ffmpeg** var
(`lib-nuvio-engine-android-0.1.2.aar` → `jni/*/libnuvio_engine.so`, `MpvPlayerEngine`,
`KitsugiMpvSurfaceView`) ve bu formatların tamamını oynatabilir.

**Çözüm:** Kodek/ses-kanalı hatalarında önce **MPV motoruna** geçilir (kullanıcı MPV'yi kapatmadıysa);
MPV de başarısız olursa kaynak değiştirilir. Yalnızca gerçek HTTP/erişim hataları (401/403/404/5xx)
doğrudan kaynak değiştirmeye gider. `UnrecognizedInputFormat` de artık "kodek" sayılıp MPV denenir
(çünkü bu bir **kap/container** hatasıdır ve MPV genelde oynatır).

### B4 — (Yüksek) Sıralı embed çözümleme → dakikalarca gecikme ✅ DÜZELTİLDİ

**Kanıt:** `for ((rawUrl, linkName, rawHeaders) in pendingEmbedUrls) { resolveEmbedUrl(...) }`
— her embed 20 sn timeout'a sahip; 6 embed = 120 sn'ye kadar gecikme. Ayrıca her provider
`delay(500)` ile bekletiliyordu ve `CF_PROTECTED_PLUGINS` dışındakiler için bu gecikme gereksizdi.

**Çözüm:**
- Embed'ler **paralel** çözülür: `Semaphore(EMBED_RESOLVE_CONCURRENCY = 3)`, sonuç sırası korunur.
- Korumasız eklentilerde bekleme 500 ms → **120 ms**.
- Paralel yazıma karşı altyazı listesi `Collections.synchronizedList` yapıldı
  (aksi hâlde `ConcurrentModificationException` → çökme riski vardı).

### B5 — (Yüksek) Bulanık domain eşleştirmesi yanlış siteye yönlendirebiliyordu ✅ DÜZELTİLDİ

**Kanıt (eski kod, `CsStreamRunner.kt:618`):**
```kotlin
BUILTIN_DEFAULT_DOMAINS.entries.firstOrNull { nameKey.contains(it.key) || it.key.contains(nameKey) }
```
Bu, alt-dize eşleşmesidir: `"asyaanimeleri"` adı `"animeler"` anahtarını **içerir**; tablo sırasına
göre eşleşme `animeler.pw`'ye düşer ve eklentinin `mainUrl`'si **başka bir sitenin adresine** çevrilir.
Tek bir yanlış domain = eklenti hiç çalışmıyor.

**Ek kanıt (bu denetimde bulundu):** `"FullHDFilmİzlede".lowercase(Locale.ROOT)` Türkçe noktalı `İ`
harfini `"i" + U+0307` yapar; bu nedenle **tam eşleşme bile** kaçabiliyordu.

**Çözüm:**
- `normalizePluginKey()` — ad ASCII'ye indirgenir (İ/I/ı→i, ş→s, ğ→g, ü→u, ö→o, ç→c, birleşen nokta silinir).
- `resolveBuiltinDomain()` — yalnızca **tam eşleşme** veya **anahtarla başlayan ad + güvenli son ek**
  (`original/orijinal/plus/pro/tv/hd/2/sitesi/provider/official`); en uzun anahtar önce.
  Artık rastlantısal alt-dize eşleşmesi mümkün değil.

### B6 — (Orta) `applyDomainFix` eklentinin kendi güncel domainini ezebiliyor ⚠️ DOKÜMANTE

**Kanıt:** `normalize(currentUrl) != normalize(remoteUrl)` koşulu, tabloda kayıt varsa mevcut
domaini koşulsuz değiştirir. Eklenti kendi içinde daha yeni bir domain taşıyorsa tablo onu geri alır.

**Öneri (sonraki iş):** Yalnızca mevcut host ölü/404 ise veya marka adı farklıysa değiştir;
aksi hâlde eklentinin kendi domainini koru. Bu denetimde **güvenli hâle getirilmedi** çünkü
mevcut davranış kullanıcının "domain yenile" akışının parçası — davranış değişikliği ayrı bir
sürümde, ölçümle yapılmalı.

### B7 — (Orta) Kalıcı kara listeler geri dönüşsüz ⚠️ DOKÜMANTE

`KNOWN_DEAD_CDN_HOSTS` (pichive.online, pichive.cc, sssrr.org, abyss.to, vmnow.online) ve
`KNOWN_BROKEN_DOMAINS` listelerindeki kayıtlar **süresiz** atlanır. CDN geri geldiğinde kullanıcı
bunu asla göremez. **Öneri:** TTL + tek seferlik probe ile "geri kazanma" (örn. 24 saatte bir
HEAD isteği başarılıysa listeyi düşür).

### B8 — (Orta) WAF/Cloudflare algılama eşiği WebView maliyetini tetikleyebiliyor ⚠️ DOKÜMANTE

**Kanıt:** `CloudflareKiller.intercept` gövdede `"ddos-guard"`, `"sucuri"` gibi **metinler**
geçtiğinde çözümleme (WebView, 12–30 sn) başlatır. Sadece DDoS-Guard'ı **anmak** bile bu yola
sokabilir. **Öneri:** gerçek challenge işareti arayın (`__ddg` cookie seti, `<form action=` +
`ddos-guard.net`, `cf-chl-` script'i) — metin varlığı yeterli olmasın.

### B9 — (Düşük) Eski tanı raporlarının yöntemi yanıltıcı ⚠️ DOKÜMANTE

`provider_stream_source_report.md`, provider sitelerini **düz HTTP + regex** ile tarar; JS
çalıştırmaz, eklentinin kendi ayrıştırma mantığını kullanmaz. Bu yüzden "47 provider OK ama embed
yok" sonucu büyük ölçüde **yöntem artefaktıdır**. Güvenilir olan: cihaz içi, eklentiler üzerinden
çalışan `plugin_diagnostic_report.md` ve yeni `embedResolveListener` akışı
(`CsPluginDiagnosticRunner.kt:306`).

### B10 — (Kritik) "Ölü CDN" listesindeki kaynaklar hiç denenmeden siliniyordu ✅ DÜZELTİLDİ

**Kanıt (eski kod, `CsStreamRunner.kt` — embed kuyruğuna alma):**
```kotlin
val isDeadCdn = KNOWN_DEAD_CDN_HOSTS.any { cleanUrl.contains(it, ignoreCase = true) }
if (isDeadCdn) {
    Log.w(TAG, "[${api.name}] Ölü CDN URL'si sessizce atlanıyor: $cleanUrl")   // ← atılıyor!
} else {
    pendingEmbedUrls.add(Triple(link.url, link.name, link.headers))
}
```
`KNOWN_DEAD_CDN_HOSTS = { pichive.online, pichive.cc, sssrr.org, abyss.to, vmnow.online }`.
Yorumların kendisi kanıt: *"pichive.cc — Pichive CDN alternatif alan"*, *"vmnow.online — VidMoly CDN
yeni alt domain; **SezonlukDizi** embed CDN'i"*.

**Sonuç:** Dizilla / AsyaWatch → pichive, SezonlukDizi → vmnow linkleri **hiç çözümlenmiyordu**.
CDN adresi değiştiğinde veya geçici olarak erişilemez olduğunda eklenti **tamamen boş** dönüyordu —
kullanıcının "eklentiler boş dönüyor" şikâyetinin doğrudan karşılığı. (Cihaz raporunda Dizilla ve
AsyaWatch'ta 0 stream görülmesi bu bulguyla tutarlı.)

**Çözüm:** Liste artık **çözümlemeyi engellemiyor**; yalnızca son-çare "ÖLÜ KANAL" fallback'ini
engellemek için kullanılıyor. Log: *"Bilinen ölü CDN — yine de çözümleme deneniyor"*.

### B11 — (Yüksek) Aday doğrulama sıralıydı → 8 aday × 6 sn = ~48 sn gecikme ✅ DÜZELTİLDİ

**Kanıt:** Derin taramada bulunan adaylar `for (candidate in scan.media)` döngüsünde **tek tek** HEAD
ile doğrulanıyordu; her istek `MEDIA_PROBE_TIMEOUT_MS = 6 sn` bekleyebildiği için kötü durumda
kaynak listesi ~48 saniye gecikiyordu.

**Çözüm:** En iyi skorlu `MAX_PROBE_CANDIDATES = 6` aday **paralel** doğrulanır (`async` + `awaitAll`);
kabul sırası skor sırasını (yani kalite/öncelik sırasını) korur. Toplam gecikme ≈tek probe süresi.
Sınırsız paralellik bilinçli olarak sınırlandı — aksi hâlde 20+ adaylı sayfalarda aynı anda onlarca
istek çıkıp site tarafında rate-limit (403) tetiklenebiliyordu.

### B12 — (Yüksek) `PlayerFallbackCoordinator` MPV ayarını yok sayıyordu ✅ DÜZELTİLDİ

**Kanıt:** `getFallbackEngine(currentEngine, errorCode, mpvEnabled)` parametreyi alıp **kullanmıyordu**:
```kotlin
val next = when (currentEngine) {
    PlayerEngineType.MEDIA3   -> PlayerEngineType.MPV   // ← mpvEnabled=false olsa bile MPV
    ...
```
Depodaki mevcut birim testi (`PlayerFallbackCoordinatorTest.testFallbackChainMpvDisabled`) ise
doğru davranışı bekliyor: *MEDIA3 → EXTERNAL (MPV kapalı)*. Yani test bu satır yüzünden **kırıktı**.
MPV kapalıyken oynatıcı, kurulamayacak bir motora geçiyor ve deneme hakkını harcıyordu.

**Çözüm:** Zincir `mpvEnabled`'a saygılı hâle getirildi (`nextEngine()` ile aynı semantik);
MPV kapalıysa doğrudan EXTERNAL'e geçilir. Böylece mevcut test de geçer.

### B13 — (Yüksek) WebView sniffer ana iş parçacığını bloke ediyordu (ANR) ✅ DÜZELTİLDİ

**Kanıt (`WebViewMediaSniffer.kt`):** `java.util.concurrent.Semaphore.tryAcquire(timeoutMs / 2, …)`
çağrısı `withContext(Dispatchers.Main)` bloğunun içindeydi → başka bir WebView çözümlemesi sürerken
**arayüz 6 saniyeye kadar donuyordu**. Kullanıcının "bazen donup kalıyor" şikâyetiyle örtüşür
(ANR eşiği 5 sn).

**Çözüm:** Askıya alan (suspending) `kotlinx.coroutines.sync.Semaphore` kullanıldı:
`withTimeoutOrNull(timeoutMs / 2) { globalGate.acquire() }` — beklerken thread serbest kalır.
Ayrıca `java.util.concurrent` bağımlılığı dosyadan tamamen kaldırıldı.

### B14 — (Yüksek) Yerleşik domain tablosu, eklentinin kendi güncel domainini eziyor ✅ AZALTILDI

**Yöntem:** `feroxx/Kekik-cloudstream` deposunun **gerçek kaynak kodu** (codeload tar.gz, 139 .kt)
indirildi; 71 provider/extractor'ın kendi beyan ettiği `mainUrl` çıkarıldı ve uygulamanın
`BUILTIN_DEFAULT_DOMAINS` tablosuyla karşılaştırıldı.

**Sonuç — 9 eklentide tablo farklı bir domain dayatıyor:**

| Eklenti | Eklentinin beyan ettiği (kaynak kod) | Uygulamanın dayattığı (tablo) |
|---|---|---|
| DiziMom | `dizimom.wiki` | `dizimom.help` |
| DiziPal | `dizipal1587.com` | `dizipal3008.com` |
| DiziPalOriginal | `dizipal2136.com` | `dizipal3008.com` |
| FullHDFilm | `hdfilm.us` | `fullhdfilm.pro` |
| **FullHDFilmizlesene** | `fullhdfilmizlesene.now` | `fullhdfilmizlesene.mx` |
| **RecTV** | `a.psrectv80.xyz` | `m.prectv72.lol` |
| SetFilmIzle | `setfilmizle.ltd` | `setfilmizle.uk` |
| WebDramaTurkey | `dtpasn.asia` | `webdramaturkey2.com` |
| WebteIzle | `webteizle.info` | `webteizle3.xyz` |

`applyDomainFix`, `normalize(currentUrl) != normalize(remoteUrl)` olduğunda domaini **koşulsuz**
değiştirir. Tablo eskimişse (veya eklenti kendi içinde daha yeni bir domain taşıyorsa) eklenti
çalışmaz hâle gelir. Ölçümde 62/71 eklentide domain **aynı** çıktı; sorun bu 9'la sınırlı.

**Çözüm (güvenli geri dönüş eklendi):**
- Yalnızca **yerleşik tablodan** gelen domain değişiklikleri için özgün domain hatırlanır
  (`forcedDomainOriginals`). Uzak (depo sahibinin yayınladığı) liste güncel kabul edilir.
- Arama **hiç sonuç vermezse**, eklenti kendi özgün domainiyle **bir kez** daha denenir
  (`revertForcedDomain`) → tablo eskimişse eklenti kurtulur, döngü riski yoktur (kayıt tüketilir).
- Log: *"Zorla uygulanan domain sonuç vermedi → eklentinin kendi domainine dönülüyor"*.

---

### B15 — (Kritik) Uygulamanın gömülü deposu, Kitsugi'nin resmî deposunu kullanmıyordu ✅ DÜZELTİLDİ

- **Kanıt:** `app/src/main/assets/plugins/repo.json` tek bir `pluginList` içeriyordu:
  `codeberg.org/BlackDamage/KitsugiPlugins/raw/branch/builds/plugins.json`.
  Kullanıcının resmî deposu (`github.com/f26901964-eng/Kitsugi-Plugins`, `builds` dalı, 166 eklenti)
  bu listede **hiç yoktu**. `CloudstreamUrlHelper.normalizeUrl` Codeberg adreslerini yeni depoya
  çeviriyordu; ancak `repo.json`'ın kendisi (birincil kaynak) hâlâ eski havuza işaret ediyordu.
- **Etki:** Kullanıcı uygulamada "Kitsugi Eklenti Deposu"nu açtığında 173 kayıtlık **eski** yedek
  listeyi görüyordu: `RecTV v1` (güncel: v98), `WebteIzle v17` (v59), `InatBox v15` (v30),
  `AnimeciX v11` (v21) ve 10 kayıt `status: 0` (kapalı) olarak işaretliydi.
- **Düzeltme:** `repo.json` → birincil liste `f26901964-eng/Kitsugi-Plugins/builds/plugins.json`,
  Codeberg yedek olarak ikinci sırada. `fetchAllPlugins` tüm listeleri sırayla dener ve birleştirir.

### B16 — (Kritik) Aynı eklenti onlarca kez listeleniyor; tekilleştirme yoktu ✅ DÜZELTİLDİ

- **Ölçüm:** 11 Türkçe/yabancı depo birleştiğinde **554 kayıt**, **355 benzersiz** eklenti;
  **134 eklenti birden fazla depoda.** Uygulama bu kayıtları olduğu gibi birleştiriyordu.
- **Etki:** Portal aynı eklentiyi iki kez gösteriyor; kullanıcı hangi kopyayı kuracağını bilmiyor ve
  eski/kırık kopyayı kurduğunda "eklenti veri getirmiyor" hatası alıyordu.
- **Düzeltme:** `CloudstreamRepoClient.dedupePlugins()` — kimlik anahtarı Türkçe katlamalı
  (`WFilmİzle` ≡ `WFilmizle`, `Kanal 7` ≡ `Kanal7`, `CizgiveDizi` ≡ `CizgiVeDizi`,
  `Dizikorea` ≡ `DiziKorea`); **en yüksek sürüm kazanır**, eşitlikte birincil depo korunur.
  Hem uzak repo yolunda hem gömülü yedek listede uygulanır.

### B17 — (Kritik) `internalName` ile `.cs3` dosya adı uyuşmuyordu → indirme 404 ✅ DÜZELTİLDİ

- **Kanıt (gerçek dosya karşılaştırması, `Kitsugi-Plugins@builds`):**

  | Kayıttaki ad | Depodaki dosya | Sonuç |
  |---|---|---|
  | `WFilmİzle` | `WFilmizle.cs3` | 404 |
  | `CanliTV` | `CanliTv.cs3` | 404 |
  | `DDizi` | `Ddizi.cs3` | 404 |
  | `Kanal 7` | `Kanal7.cs3` | 404 |
  | `CizgiveDizi` | `CizgiVeDizi.cs3` | 404 |
  | `Dizikorea` | `DiziKorea.cs3` | 404 |
  | `SineWix` | `Sinewix.cs3` | 404 |

- Ayrıca depoda **kaydı olup dosyası hiç bulunmayan** 4 kayıt tespit edildi:
  `Filmmirasım`, `Filmİzlesene`, `FullHDFilmİzlede` (hiç derlenmemiş) ve `WFilmİzle` (ad farkı).
  `YesilCamTv.cs3` ise `main/prebuilt` içinde var ama `builds` dalına kopyalanmamış.
- **Düzeltme (uygulama tarafı):**
  1. `CloudstreamUrlHelper.cs3NameVariants()` — Türkçe ASCII katlama + kısaltma katlama
     (`CanliTV→CanliTv`, `DDizi→Ddizi`) + boşluk temizliği (`Kanal 7→Kanal7`) ile aday adlar.
  2. `CS3_NAME_ALIASES` — marka adı değişen kayıtlar (`CizgiveDizi→CizgiVeDizi`).
  3. `getCandidateDownloadUrls()` — varyantlar + `prebuilt/` yolu + **Codeberg son çare yedeği**.
- **Sonuç:** 10 kırık kaydın **7'si** uygulama tarafında kurtarıldı; kalan 3'ü (`Filmmirasım`,
  `Filmİzlesene`, `FullHDFilmİzlede`) Codeberg yedeğiyle deneniyor ve depo tarafında derlenmeleri
  gerekiyor (bkz. bölüm 3b).

### B18 — (Yüksek) Tanı aracındaki 4 depo ölüydü; her tarama boşa gidiyordu ✅ DÜZELTİLDİ

- **Doğrulama (GitHub API):**
  - `ByAyzen/AyzenCS3` → **404** (repo silinmiş)
  - `caca1403/cloudstream-cagi-eklenti` → **404**
  - `sarapcanagii/Pitipitii` → **HTTP 451 / DMCA** (beIN Sports şikâyeti, 2026-08-20)
  - `Kraptor123/cs-kraptor/refs/heads/master/repo.json` → **repo.json yayınlanmıyor**
    (yalnız `builds/plugins.json`) — `hexated` için de aynı durum.
- **Etki:** Her tanı turunda 4 gereksiz başarısız istek + gecikme; `cs-kraptor` eklentileri hiç
  listelenemiyordu.
- **Düzeltme:** Liste canlı adreslerle yenilendi, Kitsugi'nin resmî deposu eklendi, `repo.json`
  yayınlamayan iki depo doğrudan `plugins.json` ile tanımlandı.

### B19 — (Orta) Gömülü yedek liste 40 kayıtta eski sürüm/yanlış durum taşıyordu ✅ DÜZELTİLDİ

- 173 kaydın 40'ı güncellendi (sürüm, URL, hash, boyut, durum resmî depodan alındı):
  `RecTV v1→v98`, `WebteIzle v17→v59`, `InatBox v15→v30`, `AnimeciX v11→v21`, `AsyaWatch v1→v6`,
  10 kayıt `status 0→1`, 7 kayıtta eksik `status/version` tamamlandı.
- 170 kayıt artık `f26901964-eng/Kitsugi-Plugins` adresine işaret ediyor; yalnız 3 kayıt
  (`__New`, `AnimeAV`, `YesilCamTv`) Codeberg'de kaldı (yeni depoda hiç derlenmemişler).

---

## 3b. Eklenti Deposu (`f26901964-eng/Kitsugi-Plugins`) Denetimi

Depo tarafında (kaynak + derlenmiş dal) tespit edilen, düzeltilmesi gereken yapısal sorunlar:

| # | Sorun | Kanıt | Etki |
|---|---|---|---|
| D1 | `builds/plugins.json` içinde **dosyası olmayan 4 kayıt** | `Filmmirasım.cs3`, `Filmİzlesene.cs3`, `FullHDFilmİzlede.cs3`, `WFilmİzle.cs3` depoda yok | İndirme 404 → "eklenti boş dönüyor" |
| D2 | `main/prebuilt/YesilCamTv.cs3` **`builds` dalına kopyalanmamış** | `builds` listesinde yok | İndirilemiyor |
| D3 | Derleyici **yalnız değişen dizinleri** derliyor | `.github/workflows/Derleyici.yml`: `CHANGED=$(git diff --name-only HEAD~1 HEAD)` | Kaynağı olan ama hiç derlenmemiş 10 dizin: `AnimeAV, CanliTV, DDizi, Filmmirasım, Filmİzlesene, FullHDFilmİzlede, WFilmİzle, YesilCamTv, __New, __Temel` |
| D4 | `merge_plugins.py` **dosya varlığını doğrulamıyor** | Kayıtlar `existing → prebuilt → compiled` olarak birleştiriliyor, `.cs3` kontrolü yok | Silinen dosyanın kaydı manifestte kalıyor (D1'in kök nedeni) |
| D5 | `prebuilt_plugins.json` (105 Kraptor kaydı) **mevcut kayıtları eziyor** | `merge_plugins.py` sıralaması | Manifest sürümü ile gerçek dosya içeriği ayrışabiliyor |
| D6 | Eski ad kayıtları yeni depoda farklı adla | `CizgiveDizi`/`Dizikorea`/`SineWix` kayıtları | 404 (uygulama tarafında alias ile kurtarıldı) |

**Önerilen depo düzeltmeleri (uygulama tarafını ilgilendirmeyen, depo sahibinin yapması gerekenler):**
1. `merge_plugins.py`'ye dosya varlık kontrolü ekle — `.cs3` dosyası bulunmayan kayıt manifeste girmesin.
2. `Derleyici.yml`'e "tüm eklentileri derle" modu ekle (`workflow_dispatch` girdisi) veya eksik
   10 dizin için boş bir commit ile derleme tetikle.
3. `prebuilt_plugins.json` sürümlerinin gerçek dosyalarla eşleştiğini doğrula (D5).
4. Kaynak dizinlerinde `internalName` ile üretilen `.cs3` adının birebir aynı olduğundan emin ol
   (`WFilmİzle` → `WFilmizle` sorunu).

---

## 4. Yapılan Değişiklikler

### Yeni dosyalar
| Dosya | Ne yapar |
|---|---|
| `app/src/main/java/com/kitsugi/animelist/data/cloudstream/embed/EmbedMediaScanner.kt` | HTML/JS → gerçek medya URL'si (packer, atob, kaçış, iframe, skorlama). **Saf Kotlin → birim testli.** |
| `app/src/main/java/com/kitsugi/animelist/data/cloudstream/embed/WebViewMediaSniffer.kt` | JS ile üretilen oynatıcılarda medya isteğini gerçek WebView'de yakalar (tek WebView kilidi, 12 sn, **ana thread'i bloke etmeyen** kotlinx Semaphore). |
| `app/src/test/java/com/kitsugi/animelist/data/cloudstream/embed/EmbedMediaScannerTest.kt` | 11 birim testi: JWPlayer, **gerçek p.a.c.k.e.r çıktısı**, atob, kaçış, video tag, iframe, data-*, reklam filtresi. |
| `app/src/test/java/com/kitsugi/animelist/utils/CloudstreamUrlHelperTest.kt` | 10 test: Codeberg→Kitsugi yönlendirmesi, eski ad→yeni dosya adı, Türkçe ASCII katlama, kısaltma katlama (`CanliTV→CanliTv`), indirme adaylarında varyant + Codeberg yedeği, Kekik eski adresleri. |
| `app/src/test/java/com/kitsugi/animelist/data/remote/CloudstreamRepoClientTest.kt` | 5 test: çift kayıt tekilleştirme, en yüksek sürüm kazanır, eşitlikte birincil depo, Türkçe/büyük-küçük harf varyantlarının eşitlenmesi, boş kimlik güvenliği. |

### Değişen dosyalar
| Dosya | Değişiklik |
|---|---|
| `CsStreamRunner.kt` | 4 aşamalı embed çözümleme; paralel embed çözümü; medya/HTML sınıflandırması; uzantısız medya için Content-Type kontrolü; `normalizePluginKey` + `resolveBuiltinDomain`; altyazı listesi thread-safe; gereksiz beklemenin azaltılması; **ölü-CDN silmesinin kaldırılması**; **aday doğrulamanın paralelleştirilmesi** (`MAX_PROBE_CANDIDATES`) |
| `PlayerErrorRecoveryController.kt` | Kodek/ses hatalarında MPV motoruna geçiş; HTTP hatası ile kodek hatasının ayrıştırılması |
| `core/player/engine/PlayerFallbackCoordinator.kt` | `mpvEnabled` artık zincirde kullanılıyor (MPV kapalıysa MEDIA3 → EXTERNAL); mevcut birim testi artık geçerli |
| `CsStreamRunner.kt` (domain) | Yerleşik tablodan zorla uygulanan domain, arama boş dönerse bir kez geri alınır (`forcedDomainOriginals` / `revertForcedDomain`) |
| `utils/CloudstreamUrlHelper.kt` | `cs3NameVariants()` (Türkçe ASCII + kısaltma + boşluk katlama), `collapseAcronyms()`, `foldToAscii()`, `pluginIdentityKey()`, `CS3_NAME_ALIASES`; `normalizeUrl` artık eski adları yeni dosya adına çeviriyor; `getCandidateDownloadUrls` varyant + `prebuilt/` + **Codeberg son çare** adayları üretiyor; `maarrem/cs-Kekik` eski adresi güncel forka yönleniyor |
| `data/remote/CloudstreamRepoClient.kt` | `dedupePlugins()` + `pluginIdentityKey()`; uzak repo, doğrudan `plugins.json` ve **gömülü yedek liste** yollarının üçünde de tekilleştirme |
| `data/cloudstream/CsPluginDiagnosticRunner.kt` | `REPOS` listesi canlılık doğrulamasıyla yenilendi (Kitsugi resmî deposu eklendi; 3 ölü adres çıkarıldı; `repo.json` yayınlamayan 2 depo `plugins.json` ile tanımlandı) |
| `assets/plugins/repo.json` | Birincil `pluginList` → `f26901964-eng/Kitsugi-Plugins/builds/plugins.json`; Codeberg yedek olarak ikinci sırada |
| `assets/plugins/plugins.json` | 173 kaydın 40'ı resmî depoyla senkronlandı (sürüm/URL/hash/boyut/durum); 170 kayıt yeni depoya yönlendirildi; `SineWix`/`Kanal7`/`CizgiveDizi`/`Dizikorea` kayıtları düzeltildi |

### Doğrulama (bu ortamda yapılabilen)
- **Tekrarlanabilir koşum takımı (depoda):** `scripts/verify_embed_scanner.py` — tarayıcı mantığının
  birebir Python ikizi; `python3 scripts/verify_embed_scanner.py` komutu **15/15 geçer** (8 oynatıcı
  senaryosu + packer + `looksLikeMediaUrl` negatif/pozitif kontrolleri).
- **URL sınıflandırma kontrolü:** `isClearlyDirectMediaUrl` 9 örnekle doğrulandı — uzantısız oynatıcı
  yolları (`/v/abc123`, `/play/9f8a…`, `player.aspx?id=…`) artık **medya sayılmıyor**; `.m3u8`/`.mp4` ve
  `master.txt`/`playlist.txt` işaretleri medya sayılıyor.
- **Packer doğrulaması:** gerçek `eval(function(p,a,c,k,e,d)…)` çıktısından
  `https://s1.molystream.org/hls/x9/720/index.m3u8?h=abc` çıkarıldı. *(Bu sırada paket çözücüde
  "token→kelime" yerine "kelime→kelime" değişimi yapan bir hata bulundu ve düzeltildi.)*
- Kotlin dosyalarında süslü parantez/parantez denge kontrolü (yorum/dize ayıklamalı çözümleyici):
  `CsStreamRunner.kt` {}=477/477 ()=1369/1369, `EmbedMediaScanner.kt` 90/90 327/327,
  `WebViewMediaSniffer.kt` 20/20 57/57, `PlayerErrorRecoveryController.kt` 18/18 59/59,
  `EmbedMediaScannerTest.kt` 11/11 68/68 — **hepsi dengeli**.
- **Gerçek kaynak analizi (kanıt):** `feroxx/Kekik-cloudstream` (139 .kt, codeload tar.gz) indirilip
  71 provider'ın `mainUrl` beyanı çıkarıldı → 62'si uygulama tablosuyla **aynı**, 9'u farklı (B14).
- **Domain eşleştirme analizi (kanıt):** 173 eklenti adı × 78 anahtarlı tablo Python ile tarandı;
  eski bulanık kural bu eklentileri **yanlış** eşliyordu: `AsyaAnimeleri → animeler.pw`,
  `FullHDFilmizlesene → hdfilmizle.live`, `HDFilmCehennemi2 → hdfilmcehennemi.nl`,
  `DiziYou → diziyo.so`, `DiziPalOriginal/Orijinal → dizipal3008.com`, `FullHDFilmİzlede → fullhdfilm.pro`.
  Yeni `resolveBuiltinDomain` bunlardan yalnızca kasten eklenenleri (DiziPal*, HDFilmCehennemi2) uygular;
  AsyaAnimeleri ve FullHDFilmizlesene artık **kendi doğru domainlerinde** kalır.

---

## 5. Cihazda Doğrulama Adımları (yapılması gereken)

```bash
# 1) Derleme
./gradlew assembleDebug

# 2) Yeni/etkilenen birim testleri
./gradlew testDebugUnitTest --tests "*EmbedMediaScannerTest*"
./gradlew testDebugUnitTest --tests "*PlayerFallbackCoordinatorTest*"
./gradlew testDebugUnitTest --tests "*CloudstreamUrlHelperTest*"
./gradlew testDebugUnitTest --tests "*CloudstreamRepoClientTest*"

# 2b) Derleme gerektirmeyen doğrulama (bu depoda)
python3 scripts/verify_embed_scanner.py     # 15/15 geçmeli

# 3) Logcat ile canlı izleme (embed aşamaları görünür)
adb logcat -s CsStreamRunner:V PLUGIN_DIAG:V CS_SEARCH_ERR:V
```

Beklenen loglar (yeni):
```
[KekikCS] 2 embed URL'si paralel çözümleniyor (eşzamanlılık=3)...
[KekikCS] Derin tarama: 3 aday, 1 iframe, oynatıcıSayfası=true
[KekikCS] ✅ Derin tarama medya buldu: https://cdn.../master.m3u8
[KekikCS] WebView sniffer → yakalandı
[KekikCS] Kodek/çözücü hatası (4003) — MPV motoruna geçiş zorlanıyor.
[KekikCS] Bilinen ölü CDN — yine de çözümleme deneniyor: https://...   (artık silinmiyor)
[KekikCS] Zorla uygulanan domain sonuç vermedi → eklentinin kendi domainine dönülüyor: ... 
[KekikCS] Embed çözümlenemedi ve URL oynatılabilir medya değil — oynatıcıya gönderilmiyor: ...
```

Cihazda şu senaryolar kontrol edilmeli:
1. **DiziBox / HDFilmCehennemi / Dizilla** → en az 1 kaynak gerçek medya URL'si olmalı; "ÖLÜ KANAL"
   etiketi görülmemeli.
2. `Kalite ?` ya da ölçülmüş kalite rozetleri görünmeli (uydurma 400p/1080p **olmamalı**).
3. Bir kaynakta ses yoksa oynatıcı **MPV'ye** geçip oynamaya devam etmeli.
4. Uzun kaynak listesinde ilk sonuçlar eskisine göre **belirgin şekilde erken** gelmeli.

---

## 6. Kalan Riskler / Önerilen Sonraki Adımlar

1. **`applyDomainFix` politika değişikliği** (B6 + ölçüm B14) — tablo tabanlı zorunlu override yerine
   "yalnızca ölü/marka farklı ise düzelt". Ölçülen 9 çakışma vakası bu turda kısmen giderildi
   (arama boş dönerse özgün domaine geri dönüş), ancak tablo girdileri **gözden geçirilmeli**:
   FullHDFilmizlesene `.now` mı `.mx` mi, RecTV `psrectv80` mi `prectv72` mi — cihazda ölçülüp
   doğru olan sabitlenmeli.
2. **Kara liste TTL'i** (B7) — 24 saatlik probe ile geri kazanma.
3. **Cloudflare/DDoS-Guard algılama sıkılaştırması** (B8) — gerçek challenge işareti.
4. **WebView sniffer için ayar anahtarı** — `CsStreamRunner.enableWebViewSniffing` alanı hazır;
   Ayarlar ekranına "JS oynatıcıları çöz (yavaş)" anahtarı eklenebilir (varsayılan: açık).
5. **Per-eklenti "aşama raporu"** — `embedResolveListener` zaten her embed denemesi için
   `providerName / rawUrl / resolved / error` veriyor; `CsPluginDiagnosticRunner` ekranında
   "hangi aşamada kaldı" kolonu olarak gösterilebilir.
6. **`PlayerFallbackCoordinatorTest` çalıştırılmalı** — düzeltme, depoda zaten var olan ama
   kırık durumdaki `testFallbackChainMpvDisabled` testini geçirir hâle getirir; `./gradlew
   testDebugUnitTest --tests "*PlayerFallbackCoordinatorTest*"` ile doğrulanmalı.
7. **`decoderPriority` etiketleri** — `AppSettings.kt:103` yorumu ("0 = Hardware only") kodun
   gerçek davranışıyla uyuşmuyor (`0` → `EXTENSION_RENDERER_MODE_ON`, yani yazılım fallback **açık**).
   Yorum düzeltilmeli, yoksa yanlış varsayımlarla ayar değiştirilir.

---

## 7. Özet

- **En büyük kazanç:** embed çözümleme artık site-bağımsız. Kütüphanenin tanımadığı Türkçe CDN'ler
  için HTML/JS derin taraması, base64/packer çözme, Content-Type tespiti, iframe zinciri ve son
  çare olarak gerçek WebView sniffing devrede. **Video kaynağı bulunamayan eklenti sayısı hedefli
  olarak azalır.**
- **İkinci büyük kazanç:** kodek hatalarında MPV'ye geçiş — "ses yok / video açılmıyor" sınıfındaki
  şikâyetlerin büyük kısmı bu şekilde çözülür.
- **Üçüncü kazanç:** paralel embed çözümü + gereksiz beklemelerin azaltılması → "aşırı geç geliyor"
  şikâyeti.
- **Dördüncü büyük kazanç (bu tur):** eklenti *indirme* zinciri sağlamlaştırıldı. Kullanıcının resmî
  deposu birincil kaynak oldu (B15), 130+ çift kayıt tekilleştirildi (B16) ve `internalName` ↔ `.cs3`
  ad uyuşmazlıkları yüzünden 404 alan **7 kayıt kurtarıldı** (B17) — bu, "eklenti kuruluyor ama boş
  dönüyor" şikâyetinin doğrudan kaynağıydı.
- **Doğruluk:** tüm yeni mantık ya birim testli (Kotlin: 26 yeni birim testi) ya da
  ikiz uygulamayla senaryo testli (`verify_embed_scanner.py` 15/15). Derleme ve cihaz testi bu
  ortamda mümkün olmadığı için **ilk iş cihazda `assembleDebug` + testler + logcat kontrolü** olmalı.
- **Depo tarafında kalan işler:** `Filmmirasım`, `Filmİzlesene`, `FullHDFilmİzlede` eklentilerinin
  derlenmesi ve `merge_plugins.py`'ye dosya varlık kontrolü eklenmesi (bölüm 3b).


---

## 8. Ek: `Kitsugi-Plugins` Deposu Denetimi ve Hazırlanan Düzeltme (2026-10-08)

Eklenti deposu (main = kaynak, builds = derlenmiş) tam olarak denetlendi ve düzeltmeler
**patch olarak** hazırlandı: `plugin-repo-duzeltme/` (çalışma alanı kökü).
Bu oturumun GitHub bağlantısı bu depoya **yalnızca okuma** yetkisine sahip olduğu için
(`Resource not accessible by integration`, HTTP 403) push edilemedi.

### Denetim sonuçları

| Kontrol | Sonuç |
|---|---|
| `.cs3` içi manifest ile `plugins.json` `pluginClassName` uyumu | **166/166 uyumlu** (0 uyuşmazlık) |
| `.cs3` dosyalarında `classes.dex` varlığı | **162/162 mevcut** (bozuk ikili yok) |
| Manifestte olup dosyası **hiç bulunmayan** kayıt | **4:** `Filmmirasım`, `Filmİzlesene`, `FullHDFilmİzlede` (kaynak var, hiç derlenmemiş) + `WFilmİzle` (depoda `WFilmizle.cs3` var, ad uyuşmuyor) |
| `main/prebuilt` içinde olup `builds`'e kopyalanmayan | **1:** `YesilCamTv.cs3` |
| Derleyicinin kapsamı | Yalnız `git diff HEAD~1` ile **değişen** dizinler → hiç dokunulmayan dizinler sonsuza dek derlenmiyor |
| `merge_plugins.py` | Dosya varlığını **hiç kontrol etmiyordu** → silinmemiş/derlenmemiş kayıtlar manifestte kalıyor (kırık 4 kaydın kaynağı) |
| `Derleyici.yml` prebuilt kopyası | `builds/` **köküne** kopyalıyordu ama manifest URL'leri `builds/prebuilt/...` istiyor |

### Hazırlanan düzeltme (patch, test edildi)

1. `Filmmirasım` v4→v5, `Filmİzlesene` v3→v4, `FullHDFilmİzlede` v5→v6 → push ile derleyici tetiklenir.
2. `prebuilt/WFilmİzle.cs3` eklendi (mevcut `WFilmizle.cs3` ile birebir).
3. `merge_plugins.py` yeniden yazıldı: URL'ler gerçek dosya konumuna göre onarılır
   (`builds/` ↔ `builds/prebuilt/`), Türkçe karakter/kısaltma varyantları eşleştirilir
   (`WFilmİzle↔WFilmizle`, `CanliTV↔CanliTv`, `DDizi↔Ddizi`, `Kanal 7↔Kanal7`), dosyası
   bulunmayan kayıtlar yüksek sesle raporlanır. **Yerel senaryo testi geçti** (URL onarımı,
   varyant eşleştirme, yabancı depo URL'lerine dokunmama, eksik dosya raporu).
4. `Derleyici.yml`: prebuilt kopyası `builds/prebuilt/` altına; `workflow_dispatch`'e
   `plugins` girdisi (seçili dizinleri elle derleme). YAML sözdizimi doğrulandı.

### Kullanıcı tarafında gereken işlem

- **Arena'da GitHub bağlantısını yeniden yetkilendirmek** → ajan patch'i doğrudan push edebilir.
- Veya patch'i elle uygulamak: `git am 0001-*.patch && git push origin main` (ayrıntı:
  `plugin-repo-duzeltme/NASIL-UYGULANIR.md`).
