# Kitsugi Beta v2.4.304

## 🇹🇷 Türkçe

### 🔊 Gerçek Ses Gecikmesi & Çoklu Format Ses Kazancı (`AudioDelayProcessor`, `GainAudioProcessor`)
- **Media3 İçin Donanımsal Ses Gecikmesi (Lip-Sync):** Media3/ExoPlayer arayüzünde eksik olan ses gecikmesi işlevi `AudioDelayProcessor` ile çözüldü. PCM (8/16/24/32-bit ve float) formatında kare hizalı örnek ekleme ve atlama ile ±5000 ms aralığında gecikme doğrudan ses hattında uygulanır.
- **Genişletilmiş PCM Kazanç Desteği:** `GainAudioProcessor` artık 8-bit (işaretsiz), 24-bit (little-endian) ve 32-bit PCM formatlarını destekler; kare sınırına uymayan artık baytlar kayıpsız taşınır.
- **Kaydırıcı Debounce Koruması:** Ses gecikmesi kaydırıcısı sürüklenirken diske sürekli yazma engellendi, 400 ms debounce ile DataStore performansı optimize edildi.

### ⚡ Media3 & MPV Motor İyileştirmeleri ve Akıllı Geçiş
- **Oynatma Hızının Korunması:** Kaynak veya bölüm değiştirildiğinde kullanıcının seçtiği oynatma hızı (`currentPlaybackSpeed`) sıfırlanmaz, yeni akışta aynen korunur.
- **ASS Handler Temizliği:** Media3 oynatıcı kapatıldığında (`release`) ASS altyazı işleyicisi bellekten temizlenir, sızıntılar önlenir.
- **Özellik Bazlı MPV'ye Akıllı Geçiş (`switchToMpvForFeature`):** Media3'ün desteklemediği gelişmiş özellikler (negatif ses gecikmesi, altyazı gecikmesi, altyazı hızı, video filtreleri) seçildiğinde oynatıcı mevcut konumu koruyarak otomatik olarak MPV motoruna geçer.
- **MPV Ekran Görüntüsü & Hazır Olma Koruması:** `screenshot-to-file` komutunun dosya yazması asenkron olarak 800 ms'ye kadar beklenir; dosya tam yüklenmeden `READY` durumu yayılmaz, arabelleğe alma göstergesi erken kapanmaz.
- **Gömülü Altyazı Tespiti:** Ekran görüntüsü alma panelinde MKV içi gömülü altyazılar da algılanır ve ekran görüntüsüne dahil edilebilir.

### 🛡️ Hata Toleransı & "Hâlâ İzliyor Musunuz?" Oturum Kontrolü
- **Kaynak Yeniden Çözümleme (`retryCurrentSource`):** Oynatıcı hata ekranındaki "Tekrar dene" butonu süresi dolmuş linklerde kaynağı eklenti üzerinden sıfırdan yeniden çözümler.
- **Etkileşimli "Hâlâ İzliyor Musunuz?" Uyarısı:** Oturum limiti dolduğunda ekranda şık bir onay diyaloğu açılır. Onaylandığında oynatma kaldığı yerden devam eder; dokunma ve çift dokunma jestleri hareketsizlik sayacını sıfırlar.
- **EXTERNAL & TV İyileştirmeleri:** Harici oynatıcı (`EXTERNAL`) seçildiğinde arka planda dahili motorun hazırlanması engellendi. Android TV için "Her zaman sor" tercihi diyalogla motor seçimini kullanıcıya bırakır.
- **Ölü Kod Temizliği:** Kullanılmayan `PlayerSourceController` ve `PlayerTrackController` sınıfları kaldırılarak ViewModel mimarisi sadeleştirildi.

---

## 🇬🇧 English

### 🔊 True Audio Delay & Multi-Format Audio Gain (`AudioDelayProcessor`, `GainAudioProcessor`)
- **Native Lip-Sync Audio Delay for Media3:** Media3/ExoPlayer lack a native audio delay API. Implemented `AudioDelayProcessor` operating directly in the PCM audio pipeline (supporting 8/16/24/32-bit & float) with sample padding and frame-aligned skipping within a ±5000 ms range.
- **Expanded PCM Gain Formats:** `GainAudioProcessor` now supports 8-bit unsigned, 24-bit little-endian, and 32-bit PCM audio streams while preserving residual fractional bytes across buffer boundaries.
- **Debounced Audio Delay Persistence:** Dragging the audio delay slider now debounces DataStore writes by 400 ms to eliminate unnecessary disk I/O.

### ⚡ Media3 & MPV Engine Enhancements & Smart Hand-off
- **Playback Speed Retention:** User-selected playback speed (`currentPlaybackSpeed`) is consistently retained across source, server, and episode switches.
- **ASS Handler Cleanup:** Explicitly unregisters ASS subtitle handler callbacks on player `release()`, preventing memory leaks.
- **Feature-Driven MPV Handoff (`switchToMpvForFeature`):** Selecting features unsupported by Media3 (negative audio delay, subtitle delay, subtitle speed, video filters) automatically switches the active engine to MPV while preserving playback position.
- **MPV Screenshot & Ready State Guards:** Waits up to 800 ms asynchronously for screenshot disk flush; prevents premature `READY` state broadcast before files finish loading.
- **Embedded Subtitle Detection:** Screenshot capture dialog now recognizes embedded MKV text tracks for subtitle overlay rendering.

### 🛡️ Resilient Error Recovery & Interactive Still-Watching
- **Fresh Source Re-Resolution (`retryCurrentSource`):** Clicking "Retry" on player error screens fully re-resolves the underlying stream link via its addon provider.
- **Interactive Still-Watching Dialog:** Replaced silent timeout behavior with a full dialog prompt; tapping confirm resumes playback, and user touch/double-tap gestures reset the inactivity timer.
- **EXTERNAL & Android TV Parity:** Prevents internal engine preparation when `EXTERNAL` player is requested. Android TV "Always Ask" displays a native engine selection dialog.
- **Dead Code Removal:** Deleted unused legacy `PlayerSourceController` and `PlayerTrackController` classes, streamlining the ViewModel orchestration pipeline.
