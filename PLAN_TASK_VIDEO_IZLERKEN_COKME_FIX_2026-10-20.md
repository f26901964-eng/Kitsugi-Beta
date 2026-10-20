# PLAN / TASK — “Video izlerken çöküyor (2 kez)” — Oynatıcı NPE + MPV Parça Listesi (Kök Neden + Çözüm)

**Tarih:** 2026-10-20
**Dal:** `arena/83631619-kitsugi-beta`
**Sürüm:** v2.4.312-beta.29874090 → **v2.4.313**
**Şikâyet:** “uygulama video izlerken çöktü iki sefer”
**Kanıt paketi (Drive):** klasör `1l6ZZkrbCYbdVk_KyIDtfU0PzcRrjhxiR`
(`crash_log (8).txt`, `logcat_at_crash (4).txt`, `live_logcat_report (5).txt`,
`device_info (6).txt`, `kitsugi_debug (5).log`, `crash_history (4).txt`)
**Cihaz:** Xiaomi 23043RP34C (pipa) · Android 13 (API 33) · arm64-v8a

---

## 1. Kök nedenler

| # | Kök neden | Belirti | Kanıt |
|---|---|---|---|
| KN-1 | **`AnimatedVisibility` içinde `!!`:** `BottomLeftPlayerControls` aktif bölüm (chapter) etiketini `visible = currentChapter != null` ile gösteriyor ama içerik lambda'sında `currentChapter!!` kullanıyordu. Compose çıkış animasyonu süresince içeriği kompozisyonda TUTAR; `currentChapter` null'a düştüğünde içerik lambda'sı null ile yeniden bestelenip NPE fırlatıyor. | Video izlerken / bölüm değişirken uygulama anında düşüyor | `crash_log (8).txt` → `b7.cc.invoke` (içerik lambda) ← `g7.b.a` (BottomLeftPlayerControls) ← `g7.o0.a` (PlayerControls) ← `e7.k4.a` (KitsugiFullscreenPlayerScreen) |
| KN-2 | **`currentChapter` sık sık null'a düşüyor:** `updateChapters()` ve `onPositionChanged()` içinde `list.lastOrNull { … } ?: list.firstOrNull()` → liste boşken null. Bölüm değişimi, kaynak değişimi ve medya yüklenirken (chapter listesi henüz boş) tetikleniyor. | Çökme özellikle oynatma başlangıcında / geçişlerde | `KitsugiPlayerViewModel` L503-504, L1591; logda mpv hâlâ “Trying demuxers” aşamasında |
| KN-3 | **Gereksiz mpv özellik sorgusu:** `readTrackSnapshot()` kanal sayısını video ve altyazı parçaları için de okuyordu; `track-list/$i/channels` yalnızca SES parçalarında var. | Logcat seli: 17 ms içinde 27 “property not found” satırı | `logcat_at_crash (4).txt`, `live_logcat_report (5).txt` |
| KN-4 | **Parça listesi olay seli (coalesce yok):** mpv; `slang`, `sid`, `aid`, harici altyazı ekleme gibi her yazımda ayrı `track-list` değişim olayı tetikliyor; her olay ~50 JNI sorgusu yapıyordu. Üstelik `sid`/`aid` hem `Long` hem `String` overload'unda yakalanıyordu → tek değişiklik iki yenileme. | Ana iş parçacığı 1,2 sn bloke: `PerfMonitor longMsg wall=1222ms`, `Skipped 30 frames` | `logcat_at_crash (4).txt` |
| KN-5 | **Teşhis körlüğü:** oynatıcı ayrı Activity olduğu için `AppRoot` içindeki `noteScreen()` izleyicisi hiç çalışmıyordu. | Her çökme raporunda “SON BİLİNEN EKRAN: baslangic”, “eylem izi yok” | `crash_log (8).txt` |
| KN-6 | **Aynı sınıf riskler:** `AlertDialog` içerik lambda'larında `selectedCastMember!!` / `setArtTypeAs!!`; `SkipIntroButton` geri sayımında 0'a bölme; `Seeker` sıfır genişlikli aralık (`0f..0f`); `percentage()` NaN; `_currentVideoUrl.value!!`, `malId!!`, `errorMessage!!`, `thumbnailUrl!!`. | Potansiyel çökmeler (aynı mekanizma) | Kaynak taraması (statik analiz) |

---

## 2. Görevler

### 2.1 Tamamlananlar

- [x] **T1 — ÇÖKME (`BottomLeftPlayerControls.kt`):** `currentChapter!!` kaldırıldı;
      değer yerel değişkene (`chapterToShow`) alındı, içerik lambda'sı içinde
      `if (chapterToShow != null)` ile korundu. (KN-1, KN-2)
- [x] **T2 — Logcat seli (`KitsugiMpvSurfaceView.kt`):** `channelCount` yalnızca
      `type == "audio"` parçalarında okunuyor; var olmayan `track-list/$i/channels`
      sorgusu tamamen kaldırıldı. (KN-3)
- [x] **T3 — Olay birleştirme (`MpvPlayerEngine.kt`):** `updateTracks()` →
      `performUpdateTracks()` + `scheduleUpdateTracks()`; istekler ana Looper'da
      birleştiriliyor (N olay → 1 anlık görüntü). (KN-4)
- [x] **T4 — Mükerrer yenileme (`MpvPlayerEngine.kt`):** `eventProperty(String, Long)`
      içindeki `sid`/`aid` kolları kaldırıldı (ikisi de `MPV_FORMAT_STRING` olarak
      gözleniyor → yalnızca `String` overload'unda işleniyor). (KN-4)
- [x] **T5 — Güvenli kapatma (`MpvPlayerEngine.kt`):** `release()` içinde
      `mainHandler.removeCallbacks(tracksUpdateRunnable)` + `tracksUpdatePending = false`;
      `performUpdateTracks()` başına `if (released) return`.
- [x] **T6 — Ekran izi (`KitsugiFullscreenPlayerScreen.kt`):** oynatıcı açıldığında ve
      bölüm/akış değiştiğinde `KitsugiSessionSupervisor.noteScreen("Oynatıcı: …")`. (KN-5)
- [x] **T7 — Ertelenmiş lambda korumaları:** `PauseOverlay` (oyuncu dialog'u),
      `ScreenshotSheet` (“resmi ayarla” dialog'u) → yerel null güvenli kopya. (KN-6)
- [x] **T8 — 0'a bölme / NaN korumaları:** `SkipIntroButton` geri sayım oranı
      (`durationSeconds > 0` + `coerceIn(0f,1f)`), `SeekBar` (`safeDuration`, süre 0 iken
      sıfır genişlikli `Seeker` aralığı), `VerticalSliders.percentage()` (sıfır genişlikli
      aralıkta NaN). (KN-6)
- [x] **T9 — StateFlow/`var` korumaları:** `KitsugiPlayerViewModel`
      (`_currentVideoUrl.value`, `malId`), `KitsugiStreamSelectorBottomSheet`
      (`errorMessage`), `StreamCard` (`thumbnailUrl`) → null güvenli yerel kopya. (KN-6)
- [x] **T10 — Sürüm & dokümantasyon:** `app/build.gradle.kts` 2.4.312 → **2.4.313**,
      `release_notes_v2.4.313.md`, `RELEASE_NOTES.md` başına eklendi,
      `KOK_NEDEN_RAPORU_VIDEO_IZLERKEN_COKME_2026-10-20.md` yazıldı.
- [x] **T11 — Commit & push:** dal `arena/83631619-kitsugi-beta`.

### 2.2 Bekleyen / doğrulama gerektiren

- [ ] **T12 — Derleme doğrulaması:** `./gradlew :app:assembleDebug`
      (sandbox'ta Android SDK + JDK yok; CI `android-build.yml` doğrulayacak).
- [ ] **T13 — Cihaz testi (zorunlu senaryolar):**
      1. Bölüm değiştirirken (sonraki bölüm / otomatik geçiş) çökme olmuyor.
      2. Kaynak (addon) değiştirirken çökme olmuyor.
      3. Oynatma başlarken (süre henüz 0 iken) seekbar/controls hatasız.
      4. Bölüm listesi boş bir akışta chapter etiketi sessizce kayboluyor.
- [ ] **T14 — Logcat doğrulaması:**
      `adb logcat | grep -E "mpv_get_property\(track-list|ComposeInternal|KitsugiCrash|NullPointerException"`
      → `track-list/.../channels` satırı yok, `PerfMonitor longMsg wall=` < 100 ms, NPE yok.
- [ ] **T15 — Çökme raporu kontrolü:** bilerek üretilen bir çökmede
      “SON BİLİNEN EKRAN: Oynatıcı: …” görünüyor mu?
- [ ] **T16 — (Öneri, ayrı görev)** `AndroidManifest.xml`'e
      `android:enableOnBackInvokedCallback="true"` eklenmesi — Android 13+ uyarısı
      (`OnBackInvokedCallback is not enabled…`); geri tuşu akışı
      (`OnBackPressedDispatcher`) gözden geçirilip ayrı bir dalda test edilmeli.

---

## 3. Değişen dosyalar

| Dosya | Değişiklik |
|---|---|
| `ui/screens/fullscreen/controls/BottomLeftPlayerControls.kt` | **Çökme düzeltmesi** (NPE) |
| `core/player/engine/MpvPlayerEngine.kt` | Olay birleştirme, mükerrer yenileme, güvenli kapatma |
| `core/player/engine/KitsugiMpvSurfaceView.kt` | Gereksiz mpv sorguları kaldırıldı |
| `ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt` | `noteScreen` ekran izi |
| `ui/screens/fullscreen/KitsugiPlayerViewModel.kt` | Null güvenli kopyalar |
| `ui/screens/fullscreen/components/PauseOverlay.kt` | Null güvenli kopya |
| `ui/screens/fullscreen/components/SkipIntroButton.kt` | 0'a bölme koruması |
| `ui/screens/fullscreen/controls/components/SeekBar.kt` | Sıfır genişlikli aralık koruması |
| `ui/screens/fullscreen/controls/components/VerticalSliders.kt` | NaN koruması |
| `ui/screens/fullscreen/controls/components/sheets/ScreenshotSheet.kt` | Null güvenli kopya |
| `ui/components/KitsugiStreamSelectorBottomSheet.kt` | `errorMessage ?: ""` |
| `ui/screens/stream/StreamCard.kt` | `listOfNotNull(...)` |
| `app/build.gradle.kts` | 2.4.313 |
| `release_notes_v2.4.313.md`, `RELEASE_NOTES.md`, `KOK_NEDEN_RAPORU_...md` | Dokümantasyon |

## 4. Zip paketi içeriği

`Kitsugi_v2.4.313_cokme_fix.zip`:
- `PLAN_TASK_VIDEO_IZLERKEN_COKME_FIX_2026-10-20.md` (bu dosya)
- `KOK_NEDEN_RAPORU_VIDEO_IZLERKEN_COKME_2026-10-20.md`
- `release_notes_v2.4.313.md`
- `degisiklikler.patch` — tüm değişikliklerin birleşik diff'i (`git apply` ile uygulanabilir)
- `degisen_dosyalar/` — küçük hacimli değişen dosyaların tamamı
- `OKU_BENI.txt` — nasıl uygulanır / nasıl test edilir
