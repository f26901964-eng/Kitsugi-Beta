# PLAN & TASK: `crash_log (5).txt` RenderThread SIGSEGV (`0x20`) ve Tüm Kod Tabanı Çökme/Çakışma Çözümü (2026-10-13)

## 1. `crash_log (5).txt` Raporunun Satır Satır Teknik Teşhisi

### A. Cihaz ve Oturum Özeti
- **Tarih / Saat:** `2026-10-13 13:58:30`
- **Uygulama Sürümü:** `2.4.275-beta.29864801` (`pid=29747`)
- **Cihaz / Sistem:** Xiaomi (MIUI, Android 12 / API 31), Snapdragon 865 (`kona`), Adreno GPU (`/dev/kgsl-3d0`), 8 GB RAM
- **Oturum Süresi:** `13:52:02` – `13:58:28` (`375 sn`, Ön Planda: `EVET`)
- **Son Ekran:** `Tab(tab=Profile)`
- **Ekran İzi (Breadcrumb):**
  1. `13:52:04` — `Tab(tab=Explore)`
  2. `13:52:16` — `ApiResultDetail(malId=100181444, title=Toumei Otoko to Ningen Onna..., source=anilist, depth=1)`
  3. `13:55:44` — `Tab(tab=Explore)`
  4. `13:55:45` — `Tab(tab=Profile)` *(1. Profil ziyareti sorunsuz açılıyor)*
  5. `13:55:53` — `ApiResultDetail(malId=100175977, title=Shikanoko Nokonoko Koshitantan / My Deer Friend Nokotan, source=anilist, depth=1)`
  6. `13:56:33` – `13:57:34` — `Galeri: My Deer Friend Nokotan (127 görsel)` açılıyor ve `sayfa 2/47` .. `12/47` arasında geziliyor
  7. `13:58:27` — `Tab(tab=Profile)` *(Detay sayfasından çıkılıp 2. kez Profile dönüldüğü anda çöküyor)*

### B. Çökme Öncesi Logcat Zaman Çizelgesi
- `13:58:05.541` – `13:58:08.468` (Galeri kapanışı):
  - `E RenderInspector: DequeueBuffer time out on com.kitsugi.animelist/com.kitsugi.animelist.MainActivity, count=1, time=2102 ms`
  - `E RenderInspector: QueueBuffer time out on com.kitsugi.animelist/com.kitsugi.animelist.MainActivity, count=1, time=694 ms`
  - `E RenderInspector: DequeueBuffer time out on com.kitsugi.animelist/com.kitsugi.animelist.MainActivity, count=1, time=1902 ms`
  - `I jit : Allocated 4889KB forjit code of void d7.tf.b(...)` (`KitsugiApiDetailScreen`)
- `13:58:20.768`:
  - `I kitsugi.animelist: Background concurrent copying GC freed 331999(22MB) AllocSpace objects, 60(12MB) LOS objects, 34% free, 45MB/69MB`
- `13:58:27.669` – `13:58:28.278` (2. kez `Tab(tab=Profile)` geçişi):
  - `13:58:27.673` — `I Choreographer: Skipped 39 frames! The application may be doing too much work on its main thread.`
  - `13:58:27.775` — `I jit : Allocated 8535KB forjit code of void x7.j.a(u6.r, long, int, int, int, int, int, int, int, kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function4, androidx.compose.runtime.Composer, int, int)`
    - Obfuscated imza eşleşmesi: `x7.j.a` = **`AniListStatsTab`** (`AniListStatsTab.kt`). Kullanıcı Bilgi/Aktivite sekmesindeyken bile `beyondViewportPageCount = 1` ve `Constraints.Infinity` yüzünden devasa `AniListStatsTab` arka planda sonsuz yükseklikle kompoze edilip ölçülmeye zorlanıyor.
  - `13:58:28.278` — `F libc : exiting due to SIG_DFL handler for signal 11, ucontext 0x7f61cc4e20`

### C. Native Çökme (`libhwui.so`) Backtrace Çözümlemesi
- **Sinyal:** `SIGSEGV (11)`, `si_code=1` (`SEGV_MAPERR`), `fault_addr=0x20`
- **Çöken Thread:** `tid=29835 (RenderThread)`
- **Kütüphane Haritası:** `/system/lib64/libhwui.so` (`base = 0x7f59e00000`)
- **Tam Çağrı Zinciri:**
  - `#37..#10` (Seviye 1..7): 7 kademeli iç içe `RenderNodeDrawable::forceDraw` (`+0x23c2dc`) → `RenderNodeDrawable::drawContent` normal `else` dalı (`+0x23bba0`) → `SkCanvas::drawDrawable` (`+0x253590`) → `SkLiteDL::draw` (`+0x309550`)
  - `#9` `libhwui.so+0x23c2dc`: `RenderNodeDrawable::forceDraw` (Seviye 8)
  - `#8` `libhwui.so+0x23bb88`: `RenderNodeDrawable::drawContent` — **`+0x23bba0`'dan 24 byte öncesi: `if (usageHint == UsageHint::ForceDark)` dalı!**
  - `#7` `libhwui.so+0x253590`: `SkCanvas::drawDrawable`
  - `#6` `libhwui.so+0x256618`: `SkNWayCanvas::onDrawDrawable` (`android::uirenderer::TransformCanvas` — MIUI/Android 12 Force Dark kanvası)
  - `#5` `libhwui.so+0x309550`: `SkLiteDL::draw(&transformCanvas)`
  - `#4` `libhwui.so+0x2779c4` (`lr`): `TransformCanvas::onDraw...` / `filterPaint`
  - `#3` `libhwui.so+0x3ff650` (`pc`): Skia shader/image metodu içinde `null` nesneden `+0x20` ofsetli okuma → **`SEGV_MAPERR` @ `0x20`**

---

## 2. Tamamlanan Görevler (Tasks)

- [x] **Görev 1 — MIUI / HyperOS / Android 10+ Force Dark (`TransformCanvas`) Kapatılması:**
  - `values/themes.xml`, `values-v31/themes.xml` ve yeni `values-night/themes.xml` içindeki tüm uygulama, splash ve diyalog temalarına `android:forceDarkAllowed="false"` eklendi.
  - `KitsugiApplication.kt` (`ActivityLifecycleCallbacks`), `MainActivity.kt`, `Theme.kt` (`KitsugiAnimeListTheme`), `KitsugiSheetOrDialog.kt` ve `KitsugiImageGalleryDialog.kt` içinde tüm `Activity` ve `Dialog`/`ModalBottomSheet` pencerelerinin `decorView` ve `LocalView` ağaçlarında `isForceDarkAllowed = false` programatik olarak zorlandı.
- [x] **Görev 2 — `AngleLinearGradientBrush` (`ShaderBrush`) Yarışı ve `0x0` Boyutlu Offscreen Katman Koruması:**
  - `KitsugiAccentSupport.kt` içinde `AngleLinearGradientBrush`, boyut bazlı (`(wPx shl 32) or hPx`) thread-safe LRU `shaderCache` ve `[1f, 16384f]` sonlu boyut kelepçesiyle yeniden yazıldı; farklı boyutlardaki bileşenlerin tek bir `ShaderBrush` örneğini paylaşırken her karede `Shader` yeniden yaratması ve `0x0` boyutta `paint.shader = null` üretmesi engellendi.
  - `KitsugiGradientMaterial.kt` içinde `AccentAwareBackgroundNode` ve tüm `CompositingStrategy.Offscreen` + `BlendMode.SrcIn` kullanan bileşenlere (`Icon`, `RadioButton`, `CircularProgressIndicator`, `LinearProgressIndicator`) `size.width > 0f && size.height > 0f` kontrolü, `TabRow` / `ScrollableTabRow` göstergelerine ise `selectedTabIndex in tabPositions.indices` sınır kontrolü eklendi.
- [x] **Görev 3 — Profil (2. Giriş) ve Detay Sayfalarında `HorizontalPager` + `Constraints.Infinity` + Re-entrant Layout Fırtınasının Giderilmesi:**
  - `AniListProfileContent.kt`, `MalProfileContent.kt`, `SimklProfileContent.kt`, `KitsuProfileContent.kt`, `ShikimoriProfileContent.kt`, `KitsugiUserProfileScreen.kt`, `DetailPageScaffold.kt`, `CharacterDetailPage.kt` ve `StaffDetailPage.kt` içindeki 9 `HorizontalPager` noktasının tamamında `beyondViewportPageCount = 0` yapıldı ve yükseklik `.coerceIn(minPagerHeightPx, 16_000_000)` ile sınırlandı.
  - `KitsugiProfileViewModel.kt` içine platform bazlı kalıcı `aniListPageHeights`, `malPageHeights`, `simklPageHeights`, `kitsuPageHeights`, `shikimoriPageHeights` eklendi.
  - `KitsugiScrollDefaults.recordStablePageHeight` içindeki `SnapshotStateMap` yazımı `mainHandler.post { ... }` ile `performLayout()` (`onGloballyPositioned`) aşamasının dışına taşındı.
- [x] **Görev 4 — GPU `AHardwareBuffer` (`dmabuf` / `/dev/kgsl-3d0`) Tükenmesi ve `Bitmap.recycle()` Yarışlarının Önlenmesi:**
  - `KitsugiApplication.kt` ve `HeroArtworkImagePolicy.kt` içinde `.allowHardware(false)` etkinleştirildi.
  - `KitsugiImageGalleryDialog.kt` içinde tam ekran galeri sayfalarına `.size(1440, 2160)` + `Precision.INEXACT`, `KitsugiNsfwImage.kt` içinde `useBitmapBlur` dalına `.size(480, 720)` + `Precision.INEXACT` sınırı eklendi.
  - `BlurTransformation.kt` içinde küçültülmüş bulanık görselin tekrar orijinal boyuta büyütülmesi ve `.recycle()` çağrıları kaldırıldı; `stackBlur` içine `w <= 1 || h <= 1 || radius < 1` koruması eklendi.
  - `PreviewGenerator.kt` içinde `MediaMetadataRetriever` `try/finally` bloğuna alındı ve UI'da kullanılan bitmap'ler üzerinde `.recycle()` çağrılması kaldırıldı.
  - `KitsugiSplashActivity.kt` içinde `MediaPlayer` yaşam döngüsü güvenli hale getirildi.
- [x] **Görev 5 — Detay Ekranı Geçişlerinde `SaveableStateHolder` Erken Silme / Bellek Sızıntısı ve Son Sekme (`Profile`) Kalıcılığı:**
  - `AppNavigationState.kt` içindeki `SaveableStateProvider` anahtarları `AppRootDetailPages.kt` ile birebir eşitlendi ve `AppRoot.kt` içinde `clearPreviousScreens()` çağrısı 280ms'lik çıkış animasyonu bittikten sonra çalışacak şekilde `delay(350L)` ile zamanlandı.
  - `SettingsDataStore.kt`, `AppViewModel.kt` ve `AppRoot.kt` içinde `default_tab` ve `last_used_tab` değerleri `kitsugi_boot_prefs` üzerinden ilk karede senkron okunacak şekilde güncellendi; `KitsugiPreferencesSettingsDialog.kt` içine `Profile` seçeneği eklendi.
- [x] **Görev 6 — Ertelenmiş Lambda `!!` (`NullPointerException`) ve `LazyColumn` Duplicate Key Çökmelerinin Giderilmesi:**
  - `AppRoot.kt`, `KitsugiNotificationsScreen.kt`, `KitsugiActivityDetailBottomSheet.kt`, `KitsugiAllActivitiesBottomSheet.kt`, `KitsugiAllReviewsBottomSheet.kt`, `KitsugiAllTopicsBottomSheet.kt`, `PlatformUserProfileScreen.kt` ve `ReviewsTab.kt` içindeki güvensiz `!!` ifadeleri yerel immutable yakalama (`?.let`) ile değiştirildi.
  - `KitsugiLazyKeys.kt`, `KitsugiAiringCalendarComponents.kt`, `GridHomeContent.kt`, `ModernHomeRows.kt`, `KitsugiHorizontalMediaSection.kt`, `KitsugiMediaGridDialog.kt`, `FullScreenMediaGridPage.kt`, `SearchScreen.kt`, `TvGridHomeContent.kt` ve `TvSearchScreen.kt` içinde tüm liste/grid anahtarlarına `index` dahil edildi.

---

## 3. Değişen ve Eklenen Dosyalar (48 Kod/Kaynak Dosyası + Bu Plan/Task Dosyası)

1. `app/src/main/res/values/themes.xml`
2. `app/src/main/res/values-v31/themes.xml`
3. `app/src/main/res/values-night/themes.xml` *(Yeni)*
4. `app/src/main/java/com/kitsugi/animelist/KitsugiApplication.kt`
5. `app/src/main/java/com/kitsugi/animelist/MainActivity.kt`
6. `app/src/main/java/com/kitsugi/animelist/KitsugiSplashActivity.kt`
7. `app/src/main/java/com/kitsugi/animelist/AppRoot.kt`
8. `app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt`
9. `app/src/main/java/com/kitsugi/animelist/ui/app/AppNavigationState.kt`
10. `app/src/main/java/com/kitsugi/animelist/ui/app/AppViewModel.kt`
11. `app/src/main/java/com/kitsugi/animelist/ui/app/KitsugiProfileViewModel.kt`
12. `app/src/main/java/com/kitsugi/animelist/ui/theme/Theme.kt`
13. `app/src/main/java/com/kitsugi/animelist/ui/theme/KitsugiAccentSupport.kt`
14. `app/src/main/java/com/kitsugi/animelist/ui/theme/gradient/KitsugiGradientMaterial.kt`
15. `app/src/main/java/com/kitsugi/animelist/ui/components/HeroArtworkImagePolicy.kt`
16. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiActivityDetailBottomSheet.kt`
17. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiAllActivitiesBottomSheet.kt`
18. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiAllReviewsBottomSheet.kt`
19. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiAllTopicsBottomSheet.kt`
20. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiHorizontalMediaSection.kt`
21. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiImageGalleryDialog.kt`
22. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiMediaGridDialog.kt`
23. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiNsfwImage.kt`
24. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiPreferencesSettingsDialog.kt`
25. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiSheetOrDialog.kt`
26. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/CharacterDetailPage.kt`
27. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/DetailPageScaffold.kt`
28. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ReviewsTab.kt`
29. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/StaffDetailPage.kt`
30. `app/src/main/java/com/kitsugi/animelist/ui/screens/explore/KitsugiAiringCalendarComponents.kt`
31. `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/FullScreenMediaGridPage.kt`
32. `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PreviewGenerator.kt`
33. `app/src/main/java/com/kitsugi/animelist/ui/screens/home/components/GridHomeContent.kt`
34. `app/src/main/java/com/kitsugi/animelist/ui/screens/home/components/ModernHomeRows.kt`
35. `app/src/main/java/com/kitsugi/animelist/ui/screens/notifications/KitsugiNotificationsScreen.kt`
36. `app/src/main/java/com/kitsugi/animelist/ui/screens/profile/AniListProfileContent.kt`
37. `app/src/main/java/com/kitsugi/animelist/ui/screens/profile/KitsuProfileContent.kt`
38. `app/src/main/java/com/kitsugi/animelist/ui/screens/profile/KitsugiUserProfileScreen.kt`
39. `app/src/main/java/com/kitsugi/animelist/ui/screens/profile/MalProfileContent.kt`
40. `app/src/main/java/com/kitsugi/animelist/ui/screens/profile/PlatformUserProfileScreen.kt`
41. `app/src/main/java/com/kitsugi/animelist/ui/screens/profile/ShikimoriProfileContent.kt`
42. `app/src/main/java/com/kitsugi/animelist/ui/screens/profile/SimklProfileContent.kt`
43. `app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchScreen.kt`
44. `app/src/main/java/com/kitsugi/animelist/ui/tv/home/TvGridHomeContent.kt`
45. `app/src/main/java/com/kitsugi/animelist/ui/tv/search/TvSearchScreen.kt`
46. `app/src/main/java/com/kitsugi/animelist/ui/utils/BlurTransformation.kt`
47. `app/src/main/java/com/kitsugi/animelist/ui/utils/KitsugiLazyKeys.kt`
48. `app/src/main/java/com/kitsugi/animelist/ui/utils/KitsugiScrollDefaults.kt`
49. `PLAN_TASK_CRASH_LOG_5_VE_TUM_COKMELER_COZUM_2026-10-13.md`
