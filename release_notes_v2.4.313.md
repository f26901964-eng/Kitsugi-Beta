# Kitsugi Beta v2.4.313

## 🇹🇷 Türkçe

### 🐞 “Video izlerken çöküyor” — Aktif Bölüm (Chapter) Göstergesi NPE Düzeltmesi
- **Kök neden (`BottomLeftPlayerControls`):** Oynatıcı alt barındaki aktif chapter etiketi
  `AnimatedVisibility(visible = currentChapter != null …)` içinde `currentChapter!!` kullanıyordu.
  Compose’da `AnimatedVisibility` **çıkış animasyonu boyunca içeriği kompozisyonda tuttuğu** için
  `currentChapter` `null`’a düştüğünde (bölüm değişimi, kaynak değişimi, chapter listesinin
  yeniden kurulması, medya yüklenirken) içerik lambda’sı `null` ile yeniden besteleniyor ve
  `NullPointerException` fırlatıyordu. Kullanıcı bunu “video izlerken çöktü” olarak görüyordu.
- **Çözüm:** gösterilecek değer yerel değişkene alındı ve içerik lambda’sı içinde
  null güvenli `if` ile korundu. Bölüm/kaynak değişimlerinde artık çökme yok.

### ⚡ MPV Parça Listesi Optimizasyonu (logcat seli + ana iş parçacığı bloke)
- **Gereksiz mpv sorguları kaldırıldı (`KitsugiMpvSurfaceView`):** `track-list/$i/channels`
  yalnızca ses parçalarında var olmasına rağmen tüm parçalar için sorgulanıyordu; her anlık
  görüntüde onlarca “property not found” hata satırı ve boş JNI çağrısı üretiyordu.
  Artık kanal sayısı yalnızca `audio` parçalarında okunuyor.
- **Olay birleştirme (`MpvPlayerEngine`):** mpv her `slang`/`sid`/`aid`/harici altyazı yazımında
  ayrı bir `track-list` değişim olayı tetikliyor; her olay ~50 JNI sorgusuna yol açıyordu
  (logcat’te 17 ms içinde 7 tam anlık görüntü → `PerfMonitor longMsg wall=1222ms`,
  “Skipped 30 frames”). Güncellemeler artık ana Looper’da birleştiriliyor: N olay → 1 yenileme.
- **Mükerrer yenileme kaldırıldı:** `sid`/`aid` hem `Long` hem `String` olay overload’unda
  yakalanıyordu; ikisi de `MPV_FORMAT_STRING` olarak gözlendiği için yalnızca `String`
  overload’unda işleniyor.
- **Güvenlik:** `release()` sırasında bekleyen yenileme geri çağrısı iptal ediliyor;
  `performUpdateTracks()` başına `released` kontrolü eklendi.

### 🛡️ Oynatıcıdaki Diğer Null/Güvenlik Riskleri
- `PauseOverlay` oyuncu detay dialog’unda `selectedCastMember!!` → yerel null güvenli kopya.
- `ScreenshotSheet` “resmi ayarla” dialog’unda `setArtTypeAs!!` → yerel null güvenli kopya.
- `SkipIntroButton` geri sayım çubuğunda 0’a bölme/NaN riski (`fillMaxWidth`) → oran artık
  `durationSeconds > 0` kontrolüyle hesaplanıyor ve `0f..1f` aralığına sıkıştırılıyor.
- `KitsugiPlayerViewModel`: `_currentVideoUrl.value!!` ve `malId!!` → yerel null güvenli kopyalar.
- `KitsugiStreamSelectorBottomSheet` ve `StreamCard`: `errorMessage!!` / `thumbnailUrl!!` →
  null güvenli kullanım.
- `SeekBar`: medya yüklenirken süre 0 iken `Seeker` sıfır genişlikli aralık (`0f..0f`) alıyor, 0’a bölme/NaN üretiyordu → süre artık en az 1 sn kabul ediliyor.
- `VerticalSliders`: `percentage()` sıfır genişlikli aralıkta `NaN` üretip `fillMaxHeight(NaN)` ile düzen çökmesi yaratabiliyordu (bazı cihazlarda müzik sesi üst sınırı 0 dönebiliyor) → koruma eklendi.

### 🔍 Teşhis İyileştirmesi
- Oynatıcı artık çökme raporlarına **“SON BİLİNEN EKRAN: Oynatıcı: &lt;başlık&gt; (Bölüm N)”**
  olarak yazılıyor. Eskiden oynatıcı ayrı bir Activity olduğu için ekran izi hiç güncellenmiyor
  ve her raporda yanıltıcı biçimde `baslangic` görünüyordu.

---

## 🇬🇧 English

### 🐞 “Crashes while watching video” — Active Chapter Indicator NPE Fix
- **Root cause (`BottomLeftPlayerControls`):** the active chapter label used
  `AnimatedVisibility(visible = currentChapter != null …)` with `currentChapter!!` inside its
  content. `AnimatedVisibility` **keeps the content composed during the exit animation**, so when
  `currentChapter` became `null` (episode change, source switch, chapter list rebuild, media still
  loading) the content lambda recomposed with `null` and threw `NullPointerException`.
- **Fix:** the displayed value is captured locally and guarded with a null-safe `if` inside the
  content lambda.

### ⚡ MPV Track-List Optimisation (logcat flood + blocked main thread)
- Dropped the non-existent `track-list/$i/channels` query; channel count is now read for audio
  tracks only, removing dozens of “property not found” lines per snapshot.
- Track-list refreshes are now coalesced on the main Looper (N events → 1 snapshot), fixing the
  `PerfMonitor longMsg wall=1222ms` / “Skipped 30 frames” stalls during load.
- Removed the duplicate `sid`/`aid` refresh (both are observed as `MPV_FORMAT_STRING`).
- Pending refresh callbacks are cancelled in `release()`; added a `released` guard.

### 🛡️ Other Null-Safety Hardening in the Player
- `PauseOverlay` cast dialog, `ScreenshotSheet` set-art dialog, `SkipIntroButton` countdown
  (division-by-zero / NaN in `fillMaxWidth`), `KitsugiPlayerViewModel` (`_currentVideoUrl.value!!`,
  `malId!!`), `KitsugiStreamSelectorBottomSheet`, and `StreamCard`.
- `SeekBar`: guarded the seeker against a zero-width range (`0f..0f`) while the media
  duration is still 0.
- `VerticalSliders`: `percentage()` now guards against zero-width ranges that produced
  `NaN` and invalid layout fractions.

### 🔍 Diagnostics
- The player now reports itself to the crash session tracker; crash reports no longer claim the
  last known screen was `baslangic`.
