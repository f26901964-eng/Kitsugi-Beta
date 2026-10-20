# Kitsugi Beta v2.4.315

## 🇹🇷 Türkçe

### 🐞 Tam Ekran Oynatıcı Çökmesi (NPE) & Ana İş Parçacığı Donma Düzeltmesi

Bu sürüm, video izlerken veya bölüm/kaynak değiştirirken tam ekran oynatıcının aniden kapanmasına yol açan NullPointerException çökmesini ve parça listesi güncellemelerinden kaynaklanan 1.2 saniyelik arayüz takılmasını tamamen giderir. Ayrıntılı analiz: `KOK_NEDEN_RAPORU_VIDEO_IZLERKEN_COKME_2026-10-20.md`.

- **AnimatedVisibility Çıkış Animasyonu NPE Koruması (`BottomLeftPlayerControls`):** Bölüm başlığı gösteriminde `currentChapter!!` kullanımı, kontroller gizlenirken veya bölüm değişimi sırasında animasyon devam ederken nesne `null`'a düştüğünde çökmeye yol açıyordu (`b7.cc.invoke` NPE). Güvenli yerel kopya yapısına geçildi (`val chapter = currentChapter ?: return@AnimatedVisibility`).
- **Ana İş Parçacığı Donması & JNI Logcat Seli Engellendi (`KitsugiMpvSurfaceView`, `MpvPlayerEngine`):**
  - MPV'de ses kanalı sayısı (`track-list/N/channels`) yalnızca ses parçalarında mevcut olmasına rağmen video ve altyazı parçalarında da sorgulanıyor ve 17 ms'de 27 JNI hatası oluşturuyordu. Artık yalnızca ses parçalarında okunuyor.
  - Parça listesi güncellemeleri (`updateTracks`) ana Looper üzerinde debounced (birleştirilmiş) hale getirildi; N adet parça olayında tek bir yenileme yapılarak Choreographer 30+ kare atlama ve 1222 ms Looper blokajı ortadan kaldırıldı.
  - Mükerrer `sid`/`aid` tetiklemeleri temizlendi; motor serbest bırakıldığında (`release`) bekleyen tüm geri çağrılar iptal edildi.
- **Sıfıra Bölme & NaN Layout Çökme Korumaları:**
  - `SeekBar` ve `VerticalSliders`: Video süresi veya aralık 0 iken oluşan `NaN` değerinin `Modifier.fillMaxWidth(NaN)` / `fillMaxHeight(NaN)` çökmelerine sebep olması engellendi (`durationMs > 0` güvencesi).
  - `SkipIntroButton`: İntro aralığı 0 iken geri sayım çubuğunun çökmesi önlendi.
- **Null Güvenlik Sertleştirmeleri:**
  - `PauseOverlay`, `ScreenshotSheet`, `KitsugiPlayerViewModel`, `KitsugiStreamSelectorBottomSheet` ve `StreamCard` içindeki potansiyel güvensiz `!!` referansları güvenli yerel kopyalar ve elvis operatörleriyle güçlendirildi.
- **Çökme Analitiği Oynatıcı Ekran İzi:**
  - `KitsugiFullscreenPlayerScreen` açıldığında çökme geçmişine `Oynatıcı: <Başlık>` kaydı düşülerek olası arıza tespitleri şeffaflaştırıldı.

### ⚡ Listem Ekranı Kaydırma & Performans Optimizasyonu

Büyük listelerde (1000+ kayıt) hesaplar bağlandığında kaydırmayı kilitleyen ve takılmalara yol açan kök nedenler çözüldü. Ayrıntılı analiz: `KOK_NEDEN_RAPORU_LISTEM_KAYDIRMA_KASMA_2026-10-20.md`.

- **Kare Başı Tüm Ekran Recomposition'ı Kaldırıldı (`MyListScreen`):** Kaydırma konumu Compose State yerine düz `IntArray` üzerinde tutuldu; `LazyListState.Saver` ile tam kalıcılık ve akıcı 60-120 FPS kaydırma sağlandı.
- **Birleşik Kütüphane ("Tümü") Arka Plana Taşındı (`Dispatchers.Default`):** Kimlik köprüsü ve gruplama ana iş parçacığından çıkarıldı, `MyListViewModel` üzerinde önbelleklenerek detay dönüşlerindeki boşalıp dolma önlendi.
- **Arama & Sıralama Optimizasyonları:** Arama filtrelemesinde NFKC normalizasyonu tembel değerlendirmeye alındı; başlık sıralamasında Schwartzian dönüşümü ile on binlerce gereksiz String tahsisi ortadan kaldırıldı.

### 🔗 Paylaşım Bağlantıları — Tüm Kaynaklarda Kalıcı Düzeltme

Bu sürüm, anime/dizi/film/karakter/seslendirmen/ekip/stüdyo sayfalarındaki **paylaş** butonlarının her kaynakta **bozuk veya yanlış** bağlantı üretmesini 12 kök neden üzerinden çözer. Detaylı analiz: `KOK_NEDEN_RAPORU_PAYLASIM_LINKLERI_2026-10-20.md`.

- **Tek Üretici (`utils/ShareLinks.kt`):** Kaynak tanıma, offset çözümleme ve doğrulanmış URL şemaları tek bir motorda birleştirildi.
- **`/anime/0` Kabusu Bitti:** Vitrin paylaş butonu kimlik yerine sabit `0` gönderiyordu. Gerçek çözülmüş kimliğe bağlandı.
- **Offset'li Kimlikler Çözülüyor:** AniList +100M, Simkl +150M, Kitsu +300M, Shikimori +400M, Bangumi +500M offset'leri doğru kaynak şemalarına çevriliyor.
- **Güvenlik Ağı & Doğrulama:** `isTrustworthy` ile kırık bağlantı gönderimi önlendi; çözülemeyen durumlarda güvenli bağlantısız paylaşım yapılıyor.

### 🧪 Birim Testleri
- `ShareLinksTest`: 18 test — altı kaynağın offset çözümlemesi, kaynak-duyarlı ham kimlik, karakter/ekip/stüdyo şemaları ve URL güvenlik ağı.

---

## 🇬🇧 English

### 🐞 Fullscreen Player Crash (NPE) & Main Thread Jank Optimization

This release resolves a critical NullPointerException that caused crashes during playback or episode transitions, while eliminating a 1.2-second main-thread block caused by excessive JNI property queries during track changes. Detailed root-cause breakdown in `KOK_NEDEN_RAPORU_VIDEO_IZLERKEN_COKME_2026-10-20.md`.

- **AnimatedVisibility Exit Transition Crash Fix (`BottomLeftPlayerControls`):** Fixed a fatal NPE (`b7.cc.invoke`) caused by `currentChapter!!` being accessed during Compose exit animations after the chapter had been cleared by episode or source transitions. Protected with safe local extraction (`val chapter = currentChapter ?: return@AnimatedVisibility`).
- **Main Thread Unblock & JNI Query Flooding Resolved (`KitsugiMpvSurfaceView`, `MpvPlayerEngine`):**
  - Constrained `channels` property queries exclusively to audio tracks; previously queried on video and subtitle tracks, triggering rapid repeated JNI error logs.
  - Debounced `updateTracks()` invocations on the main Looper, coalescing multiple property events into a single pass and resolving a 1,222ms Choreographer frame drop.
  - Deduplicated redundant `sid`/`aid` event dispatches and ensured pending track runnables are cancelled during `release()`.
- **Division-by-Zero & NaN Layout Protection:**
  - `SeekBar` & `VerticalSliders`: Guarded against zero duration or volume ranges producing `NaN` dimensions inside `Modifier.fillMaxWidth()` / `fillMaxHeight()`.
  - `SkipIntroButton`: Added safeguards against division by zero in intro countdown progress.
- **Null-Safety Hardening:**
  - Replaced unsafe `!!` operators with guarded local bindings across `PauseOverlay`, `ScreenshotSheet`, `KitsugiPlayerViewModel`, `KitsugiStreamSelectorBottomSheet`, and `StreamCard`.
- **Accurate Player Screen Breadcrumbs:**
  - `KitsugiFullscreenPlayerScreen` now logs active media playback breadcrumbs to crash reports, improving diagnostics for video session failures.

### ⚡ MyList Screen Scrolling & Performance Optimization

Resolved main-thread blocking and frame drops when scrolling large lists (1000+ entries) with connected external providers. Detailed analysis: `KOK_NEDEN_RAPORU_LISTEM_KAYDIRMA_KASMA_2026-10-20.md`.

- **Eliminated Per-Frame Whole-Screen Recomposition (`MyListScreen`):** Stored tab scroll positions in a plain `IntArray` backed by a custom `Saver`, preventing state mutations on every scroll frame from recomposing the entire screen body.
- **Combined Library Grouping Offloaded to Background (`Dispatchers.Default`):** Moved identity crosswalk, grouping, and badge map generation off the main thread, caching results in `MyListViewModel` to eliminate flickering when returning from detail pages.
- **Search & Sort Optimizations:** Deferred expensive string normalizations in search filtering and applied Schwartzian transforms to title sorting.

### 🔗 Share Links — Permanent Fix Across All Sources

This release fixes the **share** buttons on anime/TV/movie/character/voice-actor/staff/studio pages producing **broken or wrong** links for every source, traced to 12 root causes. Full analysis: `KOK_NEDEN_RAPORU_PAYLASIM_LINKLERI_2026-10-20.md`.

- **Single Producer (`utils/ShareLinks.kt`):** Every share URL is now built by `ShareLinks` — source-aware ID decoding, offsets and verified page schemas in one place.
- **No More `/anime/0`:** Fixed the hero share button sending `0` instead of the resolved entry ID.
- **Offset Decoding:** Accurately unmaps AniList, Simkl, Kitsu, Shikimori, and Bangumi namespaced IDs.
- **Safety Net:** `isTrustworthy` blocks broken links from ever reaching external share targets.

### 🧪 Unit Tests
- `ShareLinksTest`: 18 tests covering six-source offset decoding, source-aware raw IDs, character/staff/studio schemas, and the URL safety net.
