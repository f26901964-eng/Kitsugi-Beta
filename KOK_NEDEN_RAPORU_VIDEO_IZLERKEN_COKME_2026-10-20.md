# KÖK NEDEN — “Video izlerken uygulama çöktü (2 kez)” — `NullPointerException` oynatıcı OSD

**Tarih:** 2026-10-20
**Sürüm:** v2.4.312-beta.29874090 → düzeltme v2.4.313
**Cihaz:** Xiaomi 23043RP34C (pipa) · Android 13 (API 33) · arm64-v8a · 4 GB RAM sınıfı
**Şikâyet:** “uygulama video izlerken çöktü iki sefer”
**Kaynak dosyalar:** Drive klasörü `1l6ZZkrbCYbdVk_KyIDtfU0PzcRrjhxiR`
(`crash_log (8).txt`, `logcat_at_crash (4).txt`, `live_logcat_report (5).txt`,
`device_info (6).txt`, `kitsugi_debug (5).log`, `crash_history (4).txt`)

---

## 1. Çökme imzası

```
java.lang.NullPointerException
    at b7.cc.invoke(Unknown Source:1666)                      ← AnimatedVisibility içerik lambda'sı
    at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke
    at androidx.compose.animation.AnimatedVisibilityKt.AnimatedEnterExitImpl
    at androidx.compose.animation.AnimatedVisibilityKt.AnimatedVisibilityImpl
    at g7.b.a(Unknown Source:533)                             ← BottomLeftPlayerControls
    at g7.j0.invoke(Unknown Source:772)                       ← PlayerControls alt bar içerik lambda'sı
    at androidx.compose.animation.AnimatedVisibilityKt.AnimatedVisibility
    at g7.o0.a(Unknown Source:3687)                           ← PlayerControls
    at e7.k4.a(Unknown Source:9222)                           ← KitsugiFullscreenPlayerScreen
    at e7.i3.invoke(Unknown Source:145)
    ... AndroidUiDispatcher / Choreographer.doFrame
```

* Ana UI iş parçacığı, ön plan, uptime **10 sn** (uygulama açılır açılmaz oynatıcıda).
* `crash_history` kuyruğu, çökmeden hemen sonra **`KitsugiFullscreenPlayerActivity`** ve
  `PlayerPipHelper: PiP mode değişti: true` satırlarını içeriyor → çökme tam ekran oynatıcıda,
  `MainActivity` değil.
* Bellek: kullanılan 47 MB / 512 MB, **serbest ~0 MB** → bellek tükenmesi değil,
  saf `null` referans hatası.

## 2. Kök neden (tek satır)

`BottomLeftPlayerControls` içindeki **aktif chapter (bölüm) göstergesi**:

```kotlin
AnimatedVisibility(
    visible = currentChapter != null && showChapters,   // null kontrolü BURADA
    ...
) {
    CurrentChapter(
        chapter = currentChapter!!,                     // ama `!!` BURADA — ertelenmiş bestelenme
        ...
    )
}
```

Compose’da `AnimatedVisibility` **çıkış animasyonu boyunca içeriği kompozisyonda tutmaya devam eder**.
`visible` koşulu `false` olduğunda içerik hemen silinmez; `fadeOut` süresince yeniden
bestelenir. Bu pencerede `currentChapter` `null`’a düştüğünde içerik lambda’sı
(`b7.cc.invoke`) `null` değerle çalışır ve `!!` **NullPointerException** fırlatır.

`currentChapter` hangi anlarda `null`’a düşer:

| Tetikleyici | Kod |
|---|---|
| Bölüm listesi yeniden kurulması | `KitsugiPlayerViewModel.updateChapters()` → `updateChapter(list.lastOrNull { … } ?: list.firstOrNull())` → liste boşsa **null** |
| Konum güncellemesi | `onPositionChanged()` → aynı `lastOrNull … ?: firstOrNull` → **null** |
| Yeni bölüm / kaynak değişimi | `playEpisode()` → akışlar yüklenirken chapter listesi sıfırlanır |
| Medya yüklenirken | `crash_history` kuyruğunda mpv hâlâ demuxer aşamasında (`Trying demuxers for level=normal`) — chapter listesi henüz boş |

Yani **bölüm değişimi, kaynak değişimi veya oynatma başlarken** OSD kontrolleri ekrandaysa
uygulama düşüyordu — kullanıcı bunu “video izlerken çöktü” olarak deneyimledi. Aynı imza
iki çökmede de geçerli (aynı dosya/aynı kod yolu).

## 3. İkinci bulgu — ana iş parçacığının 1 saniyeden uzun bloke edilmesi ( kasılma + çökmeyi tetikleyen zemin )

`logcat_at_crash` ve `live_logcat_report`:

```
W/Looper : PerfMonitor longMsg … wall=1222ms running=16ms
W/Looper : PerfMonitor doFrame : latency=1224ms historyMsgCount=9
E/mpv    : mpv_get_property(track-list/0/channels) format 4 returned error property not found
E/mpv    : mpv_get_property(track-list/2/channels) …     (17 ms içinde 27 satır)
```

İki ayrı hata:

1. **`readTrackSnapshot()` kanal sayısını HER parça için sorguluyordu.**
   `track-list/$i/channels` yalnızca ses parçalarında var; video ve altyazı parçaları için
   mpv “property not found” döndürüyor. 5 parçalı bir akışta her anlık görüntüde
   4 parça × 3 sorgu = **12 boş JNI çağrısı** + 12 hata satırı.
2. **`updateTracks()` olay seline karşı korumasızdı.**
   mpv; `slang`, `sid`, `aid`, harici altyazı ekleme gibi her yazımda ayrı bir
   `track-list` değişim olayı tetikler. Her olay `readTrackSnapshot()` ile ~50 JNI
   sorgusu yapıyordu. Üstelik `sid`/`aid` hem `Long` hem `String` overload’unda
   yakalandığı için tek değişiklik **iki kez** yenileme üretiyordu.
   Sonuç: 17 ms içinde ~7 tam anlık görüntü → ana iş parçacığı 1,2 saniye bloke →
   `Choreographer: Skipped 30 frames`.

## 4. Üçüncü bulgu — teşhis körlüğü

Çökme raporunda:

```
▶ SON BİLİNEN EKRAN
  baslangic
```

Oynatıcı ayrı bir `Activity` olduğu için `AppRoot` içindeki `noteScreen()` izleyicisi
oynatıcıyı hiç görmüyordu; her çökmede “son ekran: baslangic” yazılıyordu. Bu yüzden
hatanın oynatıcıda olduğu rapordan okunamıyordu (aynı nedenden “eylem izi” de boştu).

---

## 5. Yapılan düzeltmeler (v2.4.313)

### 5.1 Çökme — `BottomLeftPlayerControls.kt`
`!!` kaldırıldı; gösterilecek değer yerel değişkene alınıp içerik lambda’sı içinde
`if (chapterToShow != null)` ile korundu. Artık `currentChapter` ne zaman `null`’a
düşerse düşsün kompozisyon güvenle tamamlanır.

### 5.2 Logcat seli + ana iş parçacığı bloke — `KitsugiMpvSurfaceView.kt`
`channelCount` yalnızca `type == "audio"` parçalarında okunuyor; var olmayan
`track-list/$i/channels` sorgusu tamamen kaldırıldı (2 yerine 3. sorgu da düşürüldü).

### 5.3 Olay seli — `MpvPlayerEngine.kt`
* `updateTracks()` → `performUpdateTracks()` + yeni `scheduleUpdateTracks()`:
  istekler ana Looper’da **birleştirilir** (coalesce). N olay için N değil **1** anlık görüntü.
* `release()` içinde bekleyen geri çağrı iptal ediliyor
  (`mainHandler.removeCallbacks`) — serbest bırakılmış mpv örneğine JNI erişimi engellendi.
* `performUpdateTracks()` başına `if (released) return` güvenlik kilidi.
* `eventProperty(String, Long)` içindeki mükerrer `sid`/`aid` kolları kaldırıldı
  (ikisi de `MPV_FORMAT_STRING` olarak gözleniyor → `String` overload’unda işleniyor).

### 5.4 Aynı sınıftaki diğer `!!` riskleri (ertelenmiş bestelenen lambda’lar)
| Dosya | Risk | Düzeltme |
|---|---|---|
| `PauseOverlay.kt` | `AlertDialog` `title/text` lambda’larında `selectedCastMember!!` | yerel `castMember` kopyası + `character ?: ""` |
| `ScreenshotSheet.kt` | `AlertDialog` içinde `setArtTypeAs!!` | yerel `artTypeCandidate` kopyası |
| `SkipIntroButton.kt` | `fillMaxWidth(remainingSec / durationSeconds)` → `durationSeconds = 0` ise 0’a bölme/NaN → `IllegalArgumentException` | `countdownFraction` + `coerceIn(0f, 1f)` koruması |
| `KitsugiPlayerViewModel.kt` | `_currentVideoUrl.value!!` (StateFlow `when` koşulu ile kullanım arasında null’a çekilebilir) | yerel `resolvedVideoUrl` kopyası |
| `KitsugiPlayerViewModel.kt` | `loadSkipIntervals(malId!!, …)` | yerel `skipMalId` kopyası |
| `KitsugiStreamSelectorBottomSheet.kt` | `errorMessage!!` (2 yer) | `errorMessage ?: ""` |
| `StreamCard.kt` | `source.thumbnailUrl!!` | `listOfNotNull(...)` |
| `SeekBar.kt` | `Seeker(range = 0f..duration)` → medya yüklenirken `duration = 0` ⇒ sıfır genişlikli aralık, 0’a bölme/NaN → düzen ölçüsü hatası | süre en az 1 sn kabul edilir (`safeDuration`) |
| `VerticalSliders.kt` | `percentage()` → `0..0` aralıkta `NaN` → `animateFloatAsState(NaN)` / `fillMaxHeight(NaN)` → `IllegalArgumentException` (bazı cihazlarda `STREAM_MUSIC` üst sınırı 0 dönebiliyor) | sıfır/genişliksiz aralık koruması |

### 5.5 Teşhis — `KitsugiFullscreenPlayerScreen.kt`
Oynatıcı açıldığında/bölüm değiştiğinde `KitsugiSessionSupervisor.noteScreen("Oynatıcı: …")`
çağrısı eklendi. Artık çökme raporunda “SON BİLİNEN EKRAN: Oynatıcı: <başlık> (Bölüm N)”
yazacak.

---

## 6. Değişen dosyalar

- `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/BottomLeftPlayerControls.kt` ← **çökme**
- `app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt`
- `app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PauseOverlay.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/SkipIntroButton.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/ScreenshotSheet.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiStreamSelectorBottomSheet.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/stream/StreamCard.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/SeekBar.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/VerticalSliders.kt`
- `app/build.gradle.kts` (2.4.312 → 2.4.313)
- `release_notes_v2.4.313.md`

## 7. Doğrulama

```bash
adb logcat | grep -E "mpv_get_property\(track-list|ComposeInternal|KitsugiCrash|NullPointerException"
```

Beklenen:
1. `track-list/.../channels` hata satırı **yok**.
2. Video oynatılırken `PerfMonitor longMsg wall=…` değerleri 100 ms altında.
3. Bölüm değiştirirken / kaynak değiştirirken `NullPointerException` **yok**;
   chapter etiketi kaybolurken titreme/çökme olmaz.
4. Çökme raporunda “SON BİLİNEN EKRAN: Oynatıcı: …” görünür.

## 8. Not — loglarda görünen ama uygulama kaynaklı OLMAYAN satırlar

| Satır | Kaynak |
|---|---|
| `E/xlog_client: xlog_open: Error in open xlog` | Xiaomi/MIUI sistem kütüphanesi |
| `W/ExtendedACodec: Failed to set frame packing format` | Android medya çerçevesi |
| `E perf_hint: Session creation failed` | Xiaomi perf hizmeti |
| `W RenderInspector: DequeueBuffer time out` | MIUI yüzey denetimi |
| `Checksum mismatch for dex FilmMakinesi.cs3` | ART profil derlemesi (eklenti dex’i) |
| `DecorViewStubImpl: try catch editor reflect exception` | MIUI yansıma tabanlı düzenleyici |

## 9. Öneri (bu sürümde değiştirilmedi — davranış değişikliği, test ister)

`W OnBackInvokedCallback: Set 'android:enableOnBackInvokedCallback="true"' in the application manifest.`
Android 13+ hedefli uygulamalarda manifest’e bu bayrak eklenmesi önerilir; geri tuşu
akışı (`OnBackPressedDispatcher`) gözden geçirilip ayrı bir görevde açılmalı.
