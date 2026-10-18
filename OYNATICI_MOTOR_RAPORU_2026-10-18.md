# Kitsugi Oynatıcı & Motor Düzeltmeleri — Durum Raporu (FINAL)

**Tarih:** 2026-10-18
**Dal:** `arena/13680ce5-kitsugi-beta`
**Temel commit:** `3bef456`
**Kapsam:** `core/player`, fullscreen/inline oynatıcı, eklenti (CloudStream/Stremio) akış zinciri, TV oynatıcı

> **Önemli:** Bu ortamda JDK / Android SDK / Gradle bulunmadığı için derleme,
> lint ve cihaz testi çalıştırılamadı. Aşağıdaki maddeler statik kod incelemesi
> ve `git diff` doğrulamasına dayanır.

## 1. Bu Turda Kapatılan Açık Maddeler

| # | Açık madde | Yapılan |
|---|------------|---------|
| 1 | `GainAudioProcessor` yalnız 16-bit/float | 8-bit (işaretsiz), 24-bit (little-endian, işaretli), 32-bit PCM kazanç yolları eklendi; kare sınırına uymayan artık baytlar aynen taşınıyor. |
| 2 | VM `tryNextSource` ile `PlayerSourceController` çiftliği | Ölü `PlayerSourceController` ve `PlayerTrackController` kaldırıldı; orchestrator yalnız gerçekten kullanılan controller'ları kuruyor. |
| 3 | "MPV" tercihi çelişkisi | Doğrulandı: tercih modeli `INTERNAL` (ExoPlayer), `MPV` (dahili libmpv), `EXTERNAL` (harici uygulama), `ASK`. Akış ekranı `MPV`'yi dahili Activity'ye yönlendiriyor; çelişki yok. TV'de `ASK` artık gerçek diyalog açıyor. |
| 4 | Media3 `prepare()` hızı sıfırlıyor | `currentPlaybackSpeed` alanı eklendi; kaynak/bölüm değişiminde kullanıcı hızı korunuyor. |
| 5 | Media3 `release()` ASS handler temizliği | `clearAssHandlerCompat()` eklendi ve `release()` içinde çağrılıyor. |
| 6 | Hata ekranı retry'ı yeniden çözümleme yapmıyor | `retryCurrentSource()` eklendi: eklenti kaynağını yeniden çözümler; URL değişmezse motoru mevcut konumdan yeniden hazırlar. |
| 7 | `MpvPlayerEngine.play()/pause()` zorla READY | Dosya yüklenmeden READY yayılmıyor; buffering göstergesi erken kapanmıyor. |
| 8 | Okunmamış Screen/VM aralıkları | Screen ve ViewModel'in kalan bölümleri gözden geçirildi; ek düzeltmeler aşağıda. |

## 2. Ek Düzeltmeler

- **Ekran görüntüsü (MPV):** `screenshot-to-file` asenkron çalıştığı için dosya
  oluşana kadar en fazla 800 ms bekleniyor; `renameTo` başarısız olursa geçici
  dosya doğrudan döndürülüyor (eskiden `null` dönüyordu).
- **Ekran görüntüsü (Media3):** `TextureView.bitmap` + altyazı/ASS katmanı
  çizimi; `kitsugi_media3_screenshot_<ms>.png`.
- **Gömülü altyazı:** Ekran görüntüsü panelindeki "altyazıları dahil et"
  anahtarı artık MKV içi (gömülü) altyazıları da görüyor.
- **"Hâlâ izliyor musunuz?"** diyaloğu onaylandığında, sayaç nedeniyle
  duraklatılmış oynatma kaldığı yerden devam ediyor.
- **Kullanıcı etkileşimi:** Dokunma/çift dokunma hareketleri still-watching
  hareketsizlik sayacını sıfırlıyor (daha önce sayaç hiç sıfırlanmıyordu).
- **EXTERNAL tercihi:** Activity kapanmadan dahili motorun hazırlanması
  engellendi (kısa süreli çift oynatma riski).
- **Ses gecikmesi işlemcisi:** Kare hizalı örnek atlama ve atlama sonrası
  tampon boyutu düzeltildi.
- **Hata toleransı:** `retryCurrentSource` ve `onRetry` içindeki prepare
  çağrıları `runCatching` ile korunuyor; coroutine çökmesi engellendi.
- **Paneller:** Ses/altyazı gecikmesi, ikincil altyazı gecikmesi, altyazı hızı
  ve video filtreleri gerçek motor state'ine bağlı; Media3'te desteklenmeyen
  özellik seçildiğinde MPV'ye geçiş ve konum koruma.
- **Ses gecikmesi kaydı:** Kaydırıcı sürüklenirken DataStore'a her 50 ms'de
  yazmak yerine 400 ms debounce.

## 3. Silinen Dosyalar

- `ui/screens/fullscreen/runtime/PlayerSourceController.kt`
- `ui/screens/fullscreen/runtime/PlayerTrackController.kt`

Her iki sınıf da hiçbir yerden çağrılmıyordu; VM'deki gerçek uygulamayla
çakışan ölü koddu.

## 4. Değişen Dosyalar

**core/player**
- `AudioDelayProcessor.kt` (YENİ)
- `GainAudioProcessor.kt`
- `PlayerLibassCompat.kt`
- `PlayerMediaSourceFactory.kt`
- `engine/PlayerEngine.kt`
- `engine/Media3PlayerEngine.kt`
- `engine/MpvPlayerEngine.kt`
- `engine/PlayerFallbackCoordinator.kt`

**fullscreen / runtime**
- `KitsugiFullscreenPlayerScreen.kt`
- `KitsugiFullscreenPlayerActivity.kt`
- `KitsugiPlayerViewModel.kt`
- `runtime/PlayerErrorRecoveryController.kt`
- `runtime/PlayerRuntimeOrchestrator.kt`
- `runtime/PlayerStillWatchingController.kt`
- `controls/GestureHandler.kt`
- `controls/components/panels/AudioDelayPanel.kt`
- `controls/components/panels/SubtitleDelayPanel.kt`

**akış / TV / inline**
- `ui/screens/stream/KitsugiStreamScreen.kt`
- `ui/tv/stream/TvStreamScreen.kt`
- `ui/screens/detail/KitsugiDetailPlayerComponents.kt`
- `ui/screens/detail/KitsugiDetailThemesTrailerComponents.kt`

## 5. Doğrulama Durumu

- `git diff --check`: temiz.
- Tüm değişen Kotlin dosyalarında parantez/süslü parantez dengesi kontrol edildi.
- Derleme / lint / cihaz testi: **yapılmadı** (SDK yok).
- Önerilen cihaz testleri: eklenti kaynağı oynatma, Media3→MPV fallback,
  altyazı gecikmesi/hızı, video filtreleri, ekran görüntüsü (iki motor),
  TV "Her zaman sor", hata sonrası "Tekrar dene".

## 6. Bilinen Sınırlar

- Media3'te altyazı zaman kaydırma yerleşik olmadığı için bu özellik seçildiğinde
  motor MPV'ye geçer (bilinçli tasarım).
- Media3 ekran görüntüsü `texture_view` yüzeyine bağlıdır; yüzey hazır değilse
  `null` döner (panel kullanıcıya başarısızlık bildirir).
- MPV ekran görüntüsünde dosya oluşması için en fazla 800 ms beklenir.
