# Kök neden + çözüm — Fragman / OP-ED videoları kasıyor, donuyor, "sesi var görüntüsü yok"

**Tarih:** 2026-10-21
**Sürüm:** v2.4.334
**Kapsam:** Detay sayfası "Videolar" sekmesi — YouTube fragmanları ve AnimeThemes açılış/kapanış dosyaları (küçük oynatıcı + tam ekran).

---

## 1. Soru: "Kaynakta mı sorun var, yoksa fragman ve kaynak videolarını ayrı ayrı mı düzeltmek gerekiyor?"

**İkisi de.** Ölçüm/inceleme sonucu tablo şu:

| Katman | Kimin sorunu | Belirti |
|---|---|---|
| Ortak oynatıcı katmanı (MPV/Media3) | **Uygulama** | Aşırı kasma, donma, ses-only sessiz hata |
| AnimeThemes veri seçimi | **Uygulama** | Bazı OP/ED satırları hiç oynamıyor veya ağır/uyumsuz dosya seçiliyor |
| AnimeThemes dosya biçimi | **Kaynak** | Ağırlıklı VP9 `.webm` — cihazda VP9 çözücüsü yoksa Media3 ses-only oynatır |
| YouTube akışı | **Kaynak + uygulama** | İmzalı adaptif akışta video tarafı 403 dönebiliyor; Media3 hata üretmeden sesle devam ediyor |

Yani "kaynağı değiştirelim" tek başına çözüm değil: **kaynak değişse bile** sessiz ses-only hata yakalanmadığı sürece ekran yine siyah kalır. Bu sürümde üç katman birlikte düzeltildi.

---

## 2. Kök nedenler (kaynak kodda doğrulanmış)

### 2.1 Kasma / donma → saniyede ~60 konum bildirimi
`MpvPlayerEngine`, `time-pos` özelliğini gözlemliyor ve **her değişimde** dinleyicilere iletiyordu:

```kotlin
"time-pos" -> {
    if (posMs != currentPosition) { currentPosition = posMs; notifyPositionChanged() }
}
```

libmpv bu özelliği her karede değiştirir. Küçük oynatıcıdaki dinleyici doğrudan Compose state yazıyor:

```kotlin
override fun onPositionChanged(positionMs: Long, durationMs: Long) {
    currentPosition = positionMs; duration = durationMs
}
```

→ Saniyede ~60 kez `ThemePlayerContainer` / `KitsugiPlayerGestureWrapper` yeniden besteleniyor. Detay sayfası `LazyColumn` + `animateContentSize` + `AnimatedVisibility` katmanlarıyla birlikte bu, "aşırı kasıyor" tablosunun ana kaynağı.

### 2.2 Donma / siyah kare → video çıkışının iki kez kurulması
`applyInitOptions(view)` **hem** `createVideoView()` **hem** `prepare()` içinden çağrılıyordu ve içinde `vo` yazımı vardı:

```kotlin
option("vo", settings.mpvGpuRenderer.ifBlank { "gpu" })
```

libmpv'de `vo` çalışma anında yazıldığında video çıkışı **baştan kurulur**. Her video açılışında iki kez kurulum → ilk saniyelerde siyah kare/donma. Ayrıca `MpvTrustStore.install()` (Sertifika deposu → PEM çıkarma, disk I/O) ve `mpv.conf` okuması da ana iş parçacığında iki kez çalışıyordu.

### 2.3 "Sesi var görüntüsü yok" → sessiz hata (en kritik madde)
Hem Media3 hem libmpv'de şu davranış var:

* Cihaz video kodeğini çözemezse (ör. VP9), **veya** YouTube adaptif video akışı 403 dönerse,
* oynatıcı **hata üretmez**: parça seçici video parçasını atlar, ses renderer'ı çalışmaya devam eder, süre ilerler.

Mevcut bekçi yalnızca "konum ilerledi mi?" diye bakıyordu:

```kotlin
internal fun hasInlinePlaybackProgress(state, positionMs) = state == ENDED || positionMs > 0L
```

→ Ses ilerlediği için bekçi "başladı" diyor, yedek motor hiç denenmiyor, kullanıcı siyah ekran + ses ile kalıyor. Bu tam olarak bildirilen belirti.

### 2.4 AnimeThemes verisi → yalnızca ilk kayda bakılıyordu
```kotlin
val entry = entriesArr.getJSONObject(0)          // ❌ yalnızca ilk kayıt
val videos = entry.optJSONArray("videos")        // çoğu zaman boş
```
Bir temanın birden çok kaydı olur (TV / BD / bölüm aralıkları) ve **ilk kaydın `videos` dizisi sık sık boştur**. Sonuç: video URL'i `null` kalıyor, satır "YouTube'da ara"ya düşüyor; bazı yapımlarda ise rastgele (ağır/uyumsuz) bir dosya seçiliyordu. Ayrıca seçim yalnızca "subbed değil + WEB + ≥720p" idi; **en yüksek çözünürlük, MP4/H.264 varlığı ve creditsiz (`nc`) sürüm** hiç değerlendirilmiyordu.

---

## 3. Yapılan düzeltmeler

| Dosya | Değişiklik |
|---|---|
| `core/player/engine/PlayerEngine.kt` | Yeni yetenek bayrağı `hasVideoTrack` (varsayılan `true` → bilinmeyen motorlarda yanlış yedek geçişi yok). |
| `core/player/engine/MpvPlayerEngine.kt` | Konum bildirimleri **4 Hz**'e indirildi (seek/yükleme/süre için `force`). `applyInitOptions` görüntü çıkışı bloğu **motorda bir kez** uygulanıyor. `hasVideoTrack` → `vo-configured` + seçili parça. |
| `core/player/engine/KitsugiMpvSurfaceView.kt` | `isVideoOutputActiveNow()`: seçili video parçası **ve** `vo-configured`. VO kurulamadığında mpv sesi çalar ama kare üretmez; fark tam olarak buradan okunur. |
| `core/player/engine/Media3PlayerEngine.kt` | `hasVideoTrack` (`currentTracks` içinde `TRACK_TYPE_VIDEO` + `isSelected`). Oynatma sırasında **4 Hz** zaman çizgisi darbesi (Media3 `onPositionChanged` hiç bildirmiyordu → sayaç `00:00`'da donuyordu). `release()` temizliği. |
| `ui/screens/detail/InlinePlayerSupport.kt` | `INLINE_AUDIO_ONLY_GRACE_MS`, `audioOnlyPlaybackDetected(...)`, `waitForInlineVideoTrack(...)` — ses-only durumunda tek seferlik motor değişimi. |
| `ui/screens/detail/KitsugiDetailPlayerComponents.kt` | YouTube küçük oynatıcıya ses-only bekçisi; ilk kareden sonra yükleme animasyonu titremiyor. |
| `ui/screens/detail/KitsugiDetailThemesTrailerComponents.kt` | OP/ED küçük oynatıcıya aynı bekçi; ilk kareden sonra spinner titremiyor. |
| `data/remote/KitsugiAnimeThemesClient.kt` | Tüm kayıtlar + tüm videolar taranıyor; `pickBestThemeVideoLink` sıralaması: temiz → MP4/H.264 → creditsiz → WEB → 1080p'ye kadar en yüksek çözünürlük. |
| Testler | `InlinePlayerSupportTest` genişletildi, `KitsugiAnimeThemesClientTest` eklendi (6 seçim senaryosu + URL kontrolü). |

### Akış (özet)

```
URL ──► motor seç (tercih / YouTube→Media3)
         │
         ├─ 18 sn içinde konum ilerlemedi ─► diğer motor
         │
         └─ konum ilerledi ─► 6 sn bekle ─► video parçası/çıkışı var mı?
                                   ├─ var  ─► oynat (4 Hz sayaç)
                                   └─ yok  ─► diğer motor (tek sefer)
                                                └─ o da yok ─► "Tekrar Dene / Kaynağı Aç"
```

---

## 4. Doğrulama durumu (dürüst rapor)

- `git diff --check`: **başarılı**.
- `./gradlew :app:testDebugUnitTest`: **çalıştırılamadı** — bu ortamda `JAVA_HOME`/`java` yok (önceki oturumlarla aynı kısıt).
- Gerçek cihaz + logcat: bu oturumda **yapılamadı**. Sahada doğrulanması gereken üç nokta:
  1. YouTube fragmanı: `logcat | grep -E "MpvPlayerEngine|Media3PlayerEngine|audio-only"` — motorun 6 sn sonra değişip değişmediği.
  2. AnimeThemes OP/ED: seçilen dosyanın `.webm` mi `.mp4` mı olduğu (`KitsugiAnimeThemesClient` logu eklenmedi, satır "Oynat" gösteriyorsa URL var demektir).
  3. Kaydırma akıcılığı: detay sayfası videosu oynarken listeyi kaydırma (öncesi/sonrası).
