# PLAN / TASK — HDFilmCehennemi & FilmMakinesi Oynatıcı, Kaynak Sayısı, Harici Ses/Altyazı

**Tarih:** 2026-10-18
**Dal:** `arena/09ef3908-kitsugi-beta`
**Şikâyet:** HDFilmCehennemi ve FilmMakinesi kaynakları oynatıcıda çalışmıyor; kaynak sayısı 0–1 çıkıyor; harici altyazı/ses ayrı görünmüyor; dublaj ↔ orijinal geçişi sorunsuz değil.

---

## 1. Kök nedenler

| # | Kök neden | Belirti | Kanıt |
|---|---|---|---|
| KN-1 | `PlayerMediaSourceFactory.isHlsUrl` host listesini (`closeload`, `rapidrame`, `hdfilmcehennemi`, `filmmakinesi` …) `.mp4` uzantı kontrolünden ÖNCE çalıştırıyordu. Düz MP4 HLS MIME ile (`application/x-mpegURL`) oynatılıyor → ExoPlayer çözemiyor. | Kaynak var, oynatılmıyor | `isHlsUrl`, `Media3PlayerEngine` MIME ataması |
| KN-2 | `loadLinks` aşaması non-CF eklentilerde 25 sn. HDFC/FilmMakinesi her sunucu/iframe için sıralı istek atıyor; süre dolunca kalanlar düşüyor. | 2+ kaynak yerine 1 veya 0 | `extractStreamsFromEpisode`, `stageTimeoutMs` |
| KN-3 | CS `ExtractorLink.audioTracks` (harici ses) uygulamada tamamen atılıyordu. | Harici ses hiç görünmüyor | `CsStreamRunner` callback'leri |
| KN-4 | Kaynak değişiminde (`changeStreamSource`) yeni URL 0. saniyeden başlıyordu. | Dublaj ↔ orijinal geçişi "sorunsuz" değil | `KitsugiFullscreenPlayerScreen` startPos mantığı |
| KN-5 | `CsLanguageDetector.detectLanguageCode` tanınmayan her dili `tr` sayıyordu. | Almanca/Arapça altyazılar Türkçe tercihine karışıyor | `CsLanguageDetector` |

---

## 2. Görevler

- [x] T1 — `isHlsUrl`: yol düz progressive dosyaya (`.mp4/.mkv/.webm/.avi/.m4v`) bitiyorsa HLS değil; açık HLS belirteçleri (`m3u8`, `master.txt`, `/hls/` …) önceliklidir (KN-1)
- [x] T2 — `loadLinks` aşama bütçesi 25 → 60 sn; provider bütçesi 75 → 100 sn (KN-2)
- [x] T3 — `StreamSource.externalAudioUrl` eklendi; CS `link.audioTracks.firstOrNull()` ile dolduruluyor (KN-3)
- [x] T4 — `KitsugiStreamScreen` → `startWithStreamUrls(audioUrl = source.externalAudioUrl)` (KN-3)
- [x] T5 — `changeStreamSource` / bölüm geçişi: `externalAudioUrl` taşınır; kaynak değişiminde konum `pendingSourceSwitchResumeMs` ile korunur (KN-4)
- [x] T6 — `CsLanguageDetector`: `tr`/`en` dışındaki diller `und`; `Altyazı`/`Dublaj` → `tr` (KN-5)
- [x] T7 — JVM testleri: `PlayerHlsDetectionTest`, `CsLanguageDetectorTest`
- [ ] T8 — Derleme: `./gradlew :app:testDebugUnitTest --tests "*PlayerHlsDetectionTest*" --tests "*CsLanguageDetectorTest*"` (sandbox'ta Android SDK yok, derlenmedi)
- [ ] T9 — Cihaz: HDFilmCehennemi bölümü aç → kaynak sayısı ≥ 2; MP4 kaynak oynuyor
- [ ] T10 — Cihaz: dublaj kaynağından orijinale geç → konum korunuyor, ses değişiyor, altyazı listesinde dil adları doğru
- [ ] T11 — Cihaz: `adb logcat -s CsStreamRunner CS_SEARCH_ERR` → `loadLinks zaman aşımı` satırı görünmemeli

---

## 3. Bilinen sınırlar / açık riskler

- **HDFilmCehennemi ve FilmMakinesi ayrı ses akışı bildirmiyor.** Harici ses düzeltmesi yalnızca `audioTracks` bildiren eklentilerde etkili. Dublaj/orijinal bu iki sitede ayrı **kaynak** olarak geliyor; geçiş kaynak değişimiyle yapılıyor (konum korunarak).
- **Birden fazla harici ses seçilemiyor.** Yalnızca ilk `audioTracks` kaydı kullanılıyor. Oynatıcı içinde çoklu ses seçimi için motor düzeyinde iş gerekir.
- **Eklenti sürümleri eski.** `builds` dalındaki `.cs3` dosyaları kaynaktan geride: FilmMakinesi served v58 / kaynak v60; HDFilmCehennemi served v48 / kaynak v50. Eklenti deposunda `builds` yeniden üretilmeli.
- **HDFilmCehennemi domain çakışması.** Eklenti kaynağı `hdfilmcehennemi.nl`, uzak `domain_fixes.json` `.biz` diyor. `.biz` park sayfası dönerse arama sıfır sonuç verir.
- **Altyazı URL'leri** HDFC tarafında sonuna `/` eklenerek üretiliyor; MIME tahmini uzantıya dayanıyor. Cihazda altyazı yüklenmiyorsa bu ilk şüpheli.

---

## 4. Değişen dosyalar

| Dosya | Ne |
|---|---|
| `core/player/PlayerMediaSourceFactory.kt` | `isHlsUrl` yeniden sıralandı (T1) |
| `data/cloudstream/CsStreamRunner.kt` | loadLinks/provider bütçesi (T2); `externalAudioUrl` (T3) |
| `data/repository/AddonStreamRepository.kt` | `StreamSource.externalAudioUrl` (T3) |
| `ui/screens/stream/KitsugiStreamScreen.kt` | `audioUrl` iletimi (T4) |
| `ui/screens/fullscreen/KitsugiPlayerViewModel.kt` | kaynak değişimi, konum koruma, harici ses (T5) |
| `ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt` | `takePendingSourceSwitchResume` ile startPos (T5) |
| `data/cloudstream/CsLanguageDetector.kt` | dil kodu (T6) |
| `app/src/test/.../core/player/PlayerHlsDetectionTest.kt` | yeni (T7) |
| `app/src/test/.../data/cloudstream/CsLanguageDetectorTest.kt` | yeni (T7) |
| `PLAN_TASK_HDFC_FILMMAKINESI_OYNATICI_2026-10-18.md` | bu dosya |
