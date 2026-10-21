# Plan / Task — Fragman ve OP/ED videoları: kasma, donma, "sesi var görüntüsü yok"

**Tarih:** 2026-10-21
**Sürüm:** v2.4.334
**Kapsam:** Detay sayfası "Videolar" sekmesi — YouTube fragmanları ve AnimeThemes açılış/kapanış dosyaları (küçük oynatıcı + tam ekran).
**İlgili rapor:** `COZUM_RAPORU_VIDEO_KASMA_VE_SES_VAR_GORUNTU_YOK_2026-10-21.md`
**Sürüm notları:** `release_notes_v2.4.334.md`

---

## 1. Kullanıcı bildirimi

> "Videolar öyle böyle çalıştı ama aşırı kasıyor, donuyor ve videonun sesi var görüntüsü yok. Müzik / açılış-kapanış müzikleri için aldığımız kaynakta mı sorun var, yoksa fragmanı ve bu kaynaktan gelen videoları ayrı ayrı mı düzeltmemiz lazım?"

**Yanıt:** İkisi de. Üç katman birlikte düzeltilmeden belirti kaybolmuyor:

| Katman | Sorun kimde | Belirti |
|---|---|---|
| Ortak oynatıcı katmanı (MPV / Media3) | Uygulama | Aşırı kasma, donma, sessiz ses-only hata |
| AnimeThemes veri seçimi | Uygulama | Satırlar "Oynat" yerine "YouTube'da ara"ya düşüyor, ağır/uyumsuz dosya seçimi |
| AnimeThemes dosya biçimi | Kaynak | VP9 `.webm` — VP9 çözücüsü olmayan cihazda Media3 ses-only oynatır |
| YouTube imzalı adaptif akış | Kaynak + uygulama | Video akışı 403 dönebiliyor; Media3 hata üretmeden sesle devam ediyor |

---

## 2. Görev listesi (tamamlandı)

### T1 — Konum bildirimi fırtınası (kasma)
- [x] `MpvPlayerEngine.notifyPositionChanged` → 4 Hz'e indirildi (`POSITION_NOTIFY_INTERVAL_MS = 250L`).
- [x] `seekTo`, `duration` değişimi ve `beginLoad` → `force = true` (anlık geri bildirim korunur).
- [x] Aynı düzeltme tam ekran oynatıcıyı da rahatlatır (`KitsugiPlayerViewModel.onPositionChanged` 60 Hz yerine 4 Hz çağrılır).

### T2 — Media3 zaman çizgisi (sayaç 00:00'da donuyordu)
- [x] `Media3PlayerEngine`: `onIsPlayingChanged(true)` iken 4 Hz `onPositionChanged` darbesi.
- [x] `release()` içinde `released` bayrağı + `stopPositionTicker()`.

### T3 — Çift video çıkışı kurulumu (siyah kare / ilk saniyelerde donma)
- [x] `applyInitOptions` ikiye ayrıldı:
  - Her yüklemede uygulanan hafif seçenekler (speed, deband, vf, volume-max, alang, af, video filtreleri…).
  - `vo` / `hwdec` / cache / `profile` / `ytdl` / `keep-open` / TLS-CA / `mpv.conf` okuması → **motor ömründe bir kez** (`initOptionsApplied`).

### T4 — "Sesi var, görüntüsü yok" sessiz hatasının yakalanması
- [x] `PlayerEngine.hasVideoTrack` (varsayılan `true`).
- [x] Media3: `currentTracks` içinde `TRACK_TYPE_VIDEO` + `isSelected`.
- [x] MPV: `KitsugiMpvSurfaceView.isVideoOutputActiveNow()` → `vid` + `vo-configured`.
- [x] `InlinePlayerSupport`: `INLINE_AUDIO_ONLY_GRACE_MS = 6_000L`, `audioOnlyPlaybackDetected(...)`, `waitForInlineVideoTrack(...)`.
- [x] `KitsugiYouTubePlayer` ve `ThemePlayerContainer`: tek seferlik diğer motora geçiş; ikisi de başarısızsa "Tekrar Dene / Kaynağı Aç" kartı.

### T5 — Yükleme animasyonu titremesi (donma algısı)
- [x] İlk kare geldikten sonra (`hasStartedPlayback`) yükleme halkası/plazma göstergesi tekrar açılmıyor.

### T6 — AnimeThemes veri seçimi
- [x] `animethemeentries[0]` yerine **tüm kayıtlar + tüm videolar** taraması.
- [x] `pickBestThemeVideoLink` sıralaması: temiz (non-subbed) → MP4/H.264 → creditsiz (`nc`) → WEB → 1080p'ye kadar en yüksek çözünürlük.
- [x] `isMp4Link()` yardımcısı (query string toleranslı).

### T7 — Testler ve dokümantasyon
- [x] `InlinePlayerSupportTest`: ses-only eşikleri, tanıma süresi, sağlıklı oynatımın yanlış yedek geçişi tetiklememesi.
- [x] `KitsugiAnimeThemesClientTest` (yeni): 6 seçim senaryosu + MP4 URL kontrolü.
- [x] `release_notes_v2.4.334.md`, `RELEASE_NOTES.md`, bu plan dosyası ve çözüm raporu.
- [x] Sürüm `2.4.334` (`app/build.gradle.kts`).

---

## 3. Değişen dosyalar

```
app/build.gradle.kts
app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt
app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt
app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt
app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngine.kt
app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiAnimeThemesClient.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/detail/InlinePlayerSupport.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailPlayerComponents.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailThemesTrailerComponents.kt
app/src/test/java/com/kitsugi/animelist/data/remote/KitsugiAnimeThemesClientTest.kt
app/src/test/java/com/kitsugi/animelist/ui/screens/detail/InlinePlayerSupportTest.kt
RELEASE_NOTES.md
release_notes_v2.4.334.md
COZUM_RAPORU_VIDEO_KASMA_VE_SES_VAR_GORUNTU_YOK_2026-10-21.md
```

---

## 4. Doğrulama

| Adım | Durum |
|---|---|
| `git diff --check` | ✅ başarılı |
| `./gradlew :app:testDebugUnitTest` | ⛔ çalıştırılamadı — ortamda `JAVA_HOME` / `java` yok |
| Gerçek cihaz + logcat | ⛔ bu oturumda yapılamadı |

### Cihaz doğrulama kontrol listesi
1. Fragman (YouTube): video oynarken `logcat | grep -E "MpvPlayerEngine|Media3PlayerEngine|audio-only|KitsugiYouTubePlayer"` — 6 sn sonra motor değişimi var mı?
2. OP/ED (AnimeThemes): satır "Oynat" mı "YouTube'da ara" mı gösteriyor? Oynatırken görüntü geliyor mu?
3. Detay sayfasında video oynarken liste kaydırma akıcılığı (önce/sonra).
4. Tam ekran oynatıcıda 60 Hz konum bildiriminin kaldırılması sonrası ilerleme çubuğu doğru ilerliyor mu?

---

## 5. Riskler / geri alma

- **4 Hz konum bildirimi:** İlerleme çubuğu ve sayaç için yeterli; seek anında güncellenir. Sorun çıkarsa `POSITION_NOTIFY_INTERVAL_MS` düşürülür.
- **`hasVideoTrack` yanlış pozitif:** 6 sn tanıma süresi + tek seferlik geçiş var; iki motor da başarısızsa kullanıcı hata kartı görür (sessiz siyah ekran yok).
- **Admin/CI kapalı:** APK üretimi merge sonrası GitHub Actions'ta yapılır.
