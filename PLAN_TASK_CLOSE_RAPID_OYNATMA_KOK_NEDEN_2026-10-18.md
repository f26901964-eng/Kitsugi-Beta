# PLAN / TASK — Close & Rapid Kaynakları Tam Oynatma Desteği (Kök Neden + Çözüm)

**Tarih:** 2026-10-18
**Dal:** `arena/72da1e55-kitsugi-beta`
**Şikâyet:** HDFilmCehennemi ve FilmMakinesi'nden gelen video verileri uygulamada hâlâ
oynatılmıyor. İki site de videoları aynı iki gömülü kaynaktan sunuyor: **Close**
(`closeload.*`) ve **Rapid** (`rapidrame.*` / `rplayer.*`). Video + ses + altyazının
bu kaynaklarda adam akıllı desteklenip oynatılması istendi.

---

## 1. Kök nedenler

| # | Kök neden | Belirti | Kanıt |
|---|---|---|---|
| KN-1 | **Bayat eklenti döngüsü:** `plugins.json`'daki `version`/`fileHash` alanları depo `builds` dalındaki GERÇEK `.cs3` dosyalarıyla eşleşmiyordu (FilmMakinesi json v60 + prebuilt hash, sunulan kök dosya v62; HDFilmCehennemi json v50 + prebuilt hash, sunulan kök dosya v52). `CsPluginUpdateChecker` "sürüm eşit + hash eşit → güncelleme yok" dediği için cihazlar yeni çözücüyü HİÇBİR ZAMAN almadı. | "Halen oynatılmıyor" — v2.4.303 fix'leri cihaza inen eklentiye hiç girmedi | `plugins.json` vs `Kitsugi-Plugins@builds` sha256 karşılaştırması; `CsPluginUpdateChecker.decide` |
| KN-2 | **FilmMakinesi "Rapid" düğmesi sıfır link üretiyordu:** eklenti `rapidrame` iframe'ini CS `loadExtractor`'a bırakıyor ama hiçbir eklenti rapidrame için extractor kaydetmiyordu. | FM'de yalnızca "Close" kaynağı geliyor, "DUAL Rapid" boş | `FilmMakinesi.loadLinks` (v62) + `loadExtractor` eşleşme mantığı (`ExtractorApi.kt` L919+) |
| KN-3 | **`isHlsUrl` zayıf belirteç sırası:** `/hls/` gibi zayıf yol belirteçleri progressive uzantı kontrolünden ÖNCE koşuyordu; closeload/rapidrame CDN'lerinin `/hls/.../1080p.mp4` biçimindeki düz dosyaları HLS MIME'ı (`application/x-mpegURL`) ile ExoPlayer'a verilip çözülemiyordu (KN-1'in v2.4.258'de düzeltilen halinin yol-biçimi varyantı). | Kaynak listede var, oynatıcı "çözülemiyor" hatası veriyor | `PlayerMediaSourceFactory.isHlsUrl` adım sırası |
| KN-4 | **Canlılık doğrulaması eksik başlıkla:** HTTP HEAD doğrulaması kaynağın yalnız `Referer`'ını taşıyordu; `Origin` isteyen closeload/rapidrame CDN'lerinde HEAD yanlış kod dönebiliyor, 404'te kaynak sorgusuz "ölü" ilan edilip düşürülüyordu. | Çalışan kaynak listeden sessizce kayboluyor | `CsStreamRunner` HEAD doğrulama bloğu |
| KN-5 | **Oynatma zinciri eklenti sürümüne bağımlıydı:** uygulamanın kendi çalışma zamanında closeload/rapidrame ailesi için yerleşik çözücü yoktu; eklenti bayat kalınca veya host değişince (`closeload.top`, `closeload.com`…) zincir kopuyordu. | Host/değişiklik dönemlerinde toplu oynatma kaybı | `extractorApis` kayıt listesi boş (yalnızca eklentiler) |

---

## 2. Görevler

- [x] T1 — `KitsugiCdnJwExtractor`: Close/Rapid (JWPlayer `dc_` şifreli) CDN ailesi için
  uygulama yerleşik extractor'ı. `dc_` native deşifre (atob/reverse/rot sırası + modulo
  unmix), JSON-LD `contentUrl` ve `master.txt/.m3u8` fallback'leri, master listesinden en
  yüksek çözünürlüklü varyantı seçme, `Origin`/`Referer`'ı GERÇEK yayın host'una sabitleme
  (CDN 5004 düşmesi), JWPlayer `tracks:` altyazılarını ayıklama (KN-2, KN-5)
- [x] T2 — `CsRuntimeInit`: yerleşikleri `extractorApis`'e bir kez kaydet
  (`KitsugiCloseLoad`, `KitsugiRapidrame`, `KitsugiRplayer`). Eklentiler sonradan kayıt
  olup kendi host'larında öncelik almaya devam eder (KN-5)
- [x] T3 — `plugins.json` senkron: FilmMakinesi → v62 (35.792 B, `sha256-ebe8f84d…`),
  HDFilmCehennemi → v52 (48.914 B, `sha256-336f411a…`). Cihazlardaki v60/v50 kurululumlar
  artık `Reason.VERSION` ile güncelleme alır (KN-1)
- [x] T4 — `isHlsUrl` sıralaması: kesin manifest belirteçleri → progressive uzantı →
  zayıf yol/host belirteçleri (KN-3)
- [x] T5 — HEAD doğrulaması: kaynağın TÜM başlıkları (Origin dahil) ile istek; 404'te
  kaynağı düşürmeden önce GET+Range denemesi (KN-4)
- [x] T6 — JVM testleri: `PlayerHlsDetectionTest` (yeni sıralama), `KitsugiCdnJwExtractorTest`
  (deşifre sentetik tur, varyant seçimi, altyazı filtreleme, origin/header)
- [x] T7 — Eklenti deposu (`Kitsugi-Plugins`, dal `arena/72da1e55-kitsugi-plugins`):
  FilmMakinesi `loadLinks` artık `rapidrame`/`rplayer` iframe'lerini de CloseLoad çözücüsüne
  veriyor (gelecek .cs3 derlemeleri için; sandbox'ta maven erişimi olmadığından .cs3
  DERLENEMEDİ — çalışma zamanı açığını T1/T2 kapatıyor)
- [ ] T8 — Derleme: `./gradlew :app:testDebugUnitTest` (sandbox'ta Android SDK yok)
- [ ] T9 — Cihaz: FM "DUAL Rapid" ve HDFC "Rapidrame" kaynakları listede görünüp oynuyor
- [ ] T10 — Cihaz: eklenti güncelleme ekranında FilmMakinesi v62 / HDFilmCehennemi v52 teklifi

---

## 3. Bilinen sınırlar / açık riskler

- **Depo geneli manifest sürüklenmesi:** `builds` dalındaki ~100 eklentinin `.cs3` içeriği
  `plugins.json` hash'lerinden sapmış (çoğu aynı sürüm, yeniden derlenmiş). Bu görevte
  YALNIZCA şikâyete konu iki eklenti senkronlandı; toplu senkron ayrı bir görev olmalı
  (test edilmemiş 100 derlemeyi tek commit'le kullanıcıya itmek riskli).
- **.cs3 derlemesi sandbox'ta yapılamıyor:** gradle bağımlılıkları (maven central/google/
  jitpack) ağ izin listesinde yok. Eklenti tarafı değişiklikler kaynak düzeyinde kaldı;
  cihazlarda açığı uygulama yerleşikleri kapatıyor.
- **Sürüm numarası geri olan kayıtlar:** `DiziKorea` (json 30 / manifest 1), `CizgiveDizi`
  (json 50 / manifest 3) gibi kayıtlarda depo sürümlemesi bozuk; bu görev dokunmadı.
- **Çoklu harici ses:** hâlâ yalnızca ilk `audioTracks` kaydı kullanılıyor (v2.4.303 sınırı).

---

## 4. Değişen dosyalar

| Dosya | Ne |
|---|---|
| `data/cloudstream/extractors/KitsugiCdnJwExtractor.kt` | yeni — yerleşik Close/Rapid çözücü (T1) |
| `data/cloudstream/CsRuntimeInit.kt` | yerleşik kaydı (T2) |
| `app/src/main/assets/plugins/plugins.json` | FM v62 / HDFC v52 senkronu (T3) |
| `core/player/PlayerMediaSourceFactory.kt` | `isHlsUrl` sıralaması (T4) |
| `data/cloudstream/CsStreamRunner.kt` | HEAD doğrulama başlık/404 (T5) |
| `app/src/test/.../PlayerHlsDetectionTest.kt` | yeni sıralama testleri (T6) |
| `app/src/test/.../KitsugiCdnJwExtractorTest.kt` | yeni (T6) |
| `app/build.gradle.kts` | 2.4.303 → 2.4.304 |
| `release_notes_v2.4.304.md` | bu sürümün notları |
| `PLAN_TASK_CLOSE_RAPID_OYNATMA_KOK_NEDEN_2026-10-18.md` | bu dosya |
