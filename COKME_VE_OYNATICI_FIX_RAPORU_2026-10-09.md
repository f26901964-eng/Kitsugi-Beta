# KİTSUGİ — 2026-10-09 Sessiz Çökme + Oynatıcı/Arayüz Düzeltme Raporu

İncelenen raporlar (Google Drive):
- `Kitsugi_Crash_Report (5).txt` — 1.420.465 bayt, tespit 2026-10-09 03:07:45, önceki pid=18716
- `Kitsugi_Crash_Report (4).txt` — 765.922 bayt, tespit 2026-10-09 03:03:47, önceki pid=15790

Her iki rapor da aynı sürümde: `2.4.211-beta.29858387`. Depodaki mevcut sürüm: `2.4.214`
(`app/build.gradle.kts:31`).

---

## 1. Çökmenin kimliği (native iz `/proc/self/maps` ile çözüldü)

İki raporda toplam **7 native çökme bloğu** var; hepsi aynı imzayı taşıyor:

| Alan | Değer |
|---|---|
| Sinyal | `SIGSEGV (11)`, `si_code=1` (SEGV_MAPERR), bir blokta ardından `SIGABRT (6)` |
| `fault_addr` | `0x20` (null + 0x20 → null gösterici üzerinden alan okuma) |
| Çöken thread | **`RenderThread`** (pid 18716 → tid 18792; pid 15790 → tid 15854; pid 28270 → tid 28354) |
| Java istisnası | **Yok** → `UncaughtExceptionHandler` çalışmaz, bu yüzden "sessiz" çökme |

Adresler maps ile çözüldüğünde karelerin **43/48'i `libhwui.so`** içinde ve `0x309550 → 0x253590
→ 0x23bba0 → 0x23c2dc` dörtlüsü 8–9 kez tekrar ediyor: bu, HWUI'nin **display-list (RenderNode)
ağacını özyinelemeli oynatımı**. Yani çökme uygulama kodunda değil, Android'in çizim iş parçacığında,
çizilecek içerik zaten serbest bırakılmışken gerçekleşiyor (use-after-free).

Örnek (crash5, pid 18716):
```
#0  libkitsugi_crash_handler.so+0x3b98   (bizim sinyal yakalayıcımız)
#1  libsigchain.so+0x225c
#2  [vdso]+0x5dc
#3  libhwui.so+0x3ff650
#4  libhwui.so+0x2779c4
#5..#37  0x309550 / 0x253590 / 0x23bba0 / 0x23c2dc  (tekrarlayan display-list yürüyüşü)
#45 libutils.so+0x1359c  → #46 libc.so+0xf55cc (thread giriş noktası)
```

## 2. Logcat'teki belirleyici kanıt

`crash5.txt` logcat bölümündeki **W/E/F etiketlerinin dağılımı**:

```
 84  cr_AwContents      ← "Application attempted to call on a destroyed WebView"
  9  Looper
  4  CsCfWarmup
  2  libc               ← "exiting due to SIG_DFL handler for signal 11"
  2  RenderInspector    ← DequeueBuffer / QueueBuffer time out (MainActivity)
  2  OnBackInvokedCallback
  1  perf_hint
  1  KitsugiSession
```

**84 satırın tamamı** şu uyarı ve onun Java yığını:

```
W cr_AwContents: Application attempted to call on a destroyed WebView
    at android.webkit.WebView.stopLoading(WebView.java:929)
    at com.kitsugi.animelist.data.cloudstream.CsCfWarmupManager$warmupSite$2.invokeSuspend$lambda$3(...)
```

Aynı oturumda ayrıca:
```
03:06:43  W CsCfWarmup: [www.ddizi.im] Warmup timeout (15000ms)
03:07:27  W RenderInspector: DequeueBuffer time out ... count=3
03:07:29  W RenderInspector: QueueBuffer time out ... count=1
03:07:35  I Choreographer: Skipped 35 frames!
03:07:42  F libc    : exiting due to SIG_DFL handler for signal 11
```

### Kök neden

`CsCfWarmupManager.warmupSite()` içinde **iki ayrı yerde** WebView serbest bırakılıyordu:

1. `handler.postDelayed { ... webViewRef?.stopLoading(); webViewRef?.destroy(); latch.countDown() }`
   (timeout yolu)
2. `latch.await(...)` bittikten sonra `handler.post { webViewRef?.stopLoading(); webViewRef?.destroy() }`
   (temizlik yolu)

Timeout yolu `destroy()` çağırdıktan sonra temizlik yolu **aynı nesne üzerinde tekrar**
`stopLoading()` + `destroy()` çağırıyordu. `destroy()` sonrası her çağrı Chromium'da
"destroyed WebView" uyarısı üretir ve **RenderThread içinde use-after-free** riski yaratır —
ölçülen çökme tam olarak bu. Ayrıca `WARMUP_SITES` listesinde 38 adres var (biri, `cizgimax.online`, iki kez yazılmış) ve her açılışta
14'e kadar WebView **ana thread'de** sırayla yaratılıp ağır CF/JS sayfaları yüklüyordu
(`Skipped 35 frames`, `DequeueBuffer time out` bunun sonucu).

## 3. Yapılan düzeltmeler

### A. `CsCfWarmupManager.kt` — çökmenin kök nedeni
- Yeni `releaseWebView(webViewRef, settled)`: `AtomicBoolean.compareAndSet` ile **tek seferlik**
  teardown. Sıra: parent'tan sök → `stopLoading()` → `about:blank` → client'ı sıfırla → `destroy()`.
- Timeout runnable artık **tek teardown noktası**; başarı yolunda da yıkımı o yapıyor.
- `latch.await` sonrası blok yalnızca güvenlik ağı (tek seferlik olduğu için çifte destroy imkânsız).
- Timeout koşulu `latch.count > 0` yerine `!settled.get()` — "çerez alındı ama henüz yıkılmadı"
  durumu doğru ayırt ediliyor.

### B. `KitsugiApplication.kt` — RenderThread baskısını azaltma
- Warmup, domain listesi hazır olduktan sonra **12 sn gecikmeyle** başlatılıyor; açılışta
  arayüz/ilk kare ile yarışması engellendi.

### C. `Media3PlayerEngine.kt` — Türkçe dahili altyazı varsayılan seçilmiyordu
Eski kod tek atımlıktı: `if (!initialSelectionDone) { initialSelectionDone = true; ... }`.
ExoPlayer ilk `onTracksChanged` olayını çoğu akışta **harici (sideloaded) altyazılar henüz
bağlanmadan** verir; politika o an bir kez çalışıp bayrağı `true` yaptığı için videonun kendi
Türkçe parçası listeye biraz geç düşünce **hiç seçilmiyordu**.

- Yeni alanlar: `userSelectedTextTrack`, `autoSubtitleSelectionSettled`, `autoSubtitleAttemptCount`.
- Seçim artık hedef dil **gerçekten seçilene kadar** yeniden deneniyor
  (`MAX_AUTO_SUBTITLE_ATTEMPTS = 8`, sonsuz döngü koruması).
- `currentSatisfiesPolicy`: seçili parça Türkçe ya da tercih listesindeki bir dil ise dur.
- `selectTrack()` altyazı için çağrıldığında `userSelectedTextTrack = true` → kullanıcının elle
  seçimi bir daha ezilmiyor.
- `prepare()` yeni ortamda üç bayrağı da sıfırlıyor.
- Not: MPV motoru bu hataya zaten sahip değildi; `fileLoaded && externalSubsReady` koşuluyla
  harici altyazıları bekliyor (`MpvPlayerEngine.kt:733`).

### D. `Media3PlayerEngine.kt` — harici altyazıların ekran dışına taşması
`translationY = -verticalOffset * density` sınırsızdı; "Dikey Konum" kaydırıcısı
(-200..200) eksiye çekildiğinde altyazı görünür alanın dışına itilebiliyordu. Çeviri artık
altyazı alanının içine kıstırılıyor (alt kenarda 4dp pay).

### E. `KitsugiFullscreenPlayerActivity.kt` — mini (PiP) oynatıcı
- **Sesin arka planda devam etmesi:** `onStop()` yalnızca `pipPlayerCallback?.onPipPause()`
  çağırıyordu; köprü `null` olduğunda (kaynak çözümleme/hata durumlarında Compose
  `DisposableEffect` onu söker) **hiçbir şey olmuyordu**. Yeni `invokePipAction`, köprü yoksa
  doğrudan Activity ömrüne bağlı ViewModel'e düşüyor. Ayrıca PiP'te değilken görünmez olunca
  `KeepAliveService.stop(this)` çağrılıyor ve Activity `finish()` ediliyor
  (harici oynatıcıya devredildiyse veya `isTaskRoot` değilse dokunulmuyor).
- **PiP tuşlarının çalışmaması:** aynı `invokePipAction` yedeği PiP RemoteAction ve
  MediaSession (`onPlay/onPause/onSkipNext`) yollarına da bağlandı.
- `onPictureInPictureModeChanged`: PiP'ten çıkış + `isFinishing` durumunda oynatma derhal durduruluyor.

### F. `KitsugiFullscreenPlayerActivity.kt` — video sırasında alt barın belirmesi
`hide(systemBars())` yalnızca `onCreate` içinde bir kez çağrılıyordu. MIUI/HyperOS'ta PiP
geçişi/odak değişimi sonrası alt gezinme çubuğu "geçici" olmaktan çıkıp kalıcı hâle geliyordu.
Yeni `applyImmersiveMode()` artık `onCreate` + `onResume` + `onWindowFocusChanged(hasFocus=true)`
içinde yeniden uygulanıyor.

### G. `KitsugiSheetOrDialog.kt` — açılır sayfalarda takılı kalma
- `confirmValueChange` içinde `Hidden` hedefi `enableSwipeToDismiss && isAtTop` koşuluna bağlıydı;
  içerik aşağı kaydırıldığında sürüklemeyle kapatma **tamamen kilitleniyordu**. Artık her koşulda
  izin veriliyor (çıkış garantisi öncelikli).
- Yeni `showCloseButton: Boolean = true` + `KitsugiSheetCloseButton`: **35 çağrı noktasının
  tamamı** adlandırılmış argüman kullandığı için (doğrulandı) hepsi otomatik olarak sağ üstte
  minik bir çarpı butonu kazanıyor. BottomSheet, tam ekran Dialog ve TV Dialog dallarının
  üçüne de eklendi.

## 4. Doğrulama durumu (dürüst beyan)

Bu sandbox'ta **JDK ve Android SDK yok** (`java: command not found`, `ANDROID_HOME` boş,
`local.properties` yok), dolayısıyla `./gradlew` ile derleme/test **çalıştırılamadı**.
Yapılan statik doğrulamalar:

- Düzenlenen 5 dosyada parantez/süslü parantez/köşeli parantez dengesi: **hepsi 0** (string ve
  yorum satırları ayıklanarak sayıldı).
- `KitsugiSheetOrDialog.kt` için kullanılan tüm sembollerin import kontrolü → eksik olan
  `androidx.compose.foundation.layout.size` ve `statusBarsPadding` **eklendi**.
- `KitsugiSheetOrDialog(` çağrı noktalarının tamamı tarandı: **pozisyonel argüman kullanan
  çağrı noktası yok** (yalnızca fonksiyon bildiriminin kendisi eşleşti), yani `content`
  lambda'sından önce eklenen yeni parametre hiçbir çağrıyı kırmıyor.
- `KitsugiFullscreenPlayerActivity` içinde hiçbir yaşam döngüsü metodu çift tanımlı değil
  (`onCreate/onResume/onStop/onDestroy/onWindowFocusChanged/onPictureInPictureModeChanged` → 1'er).

**Cihazda test edilmesi gerekenler:** warmup sonrası `cr_AwContents` uyarısının kaybolması,
PiP kapatınca sesin kesilmesi, dahili Türkçe altyazının otomatik gelmesi, açılır sayfalardaki
çarpı butonu.

## 5. Kapsam dışı bırakılanlar (bilinçli)

- `AndroidManifest.xml`'e `android:enableOnBackInvokedCallback="true"` **eklenmedi**. Logcat'te
  `OnBackInvokedCallback is not enabled for the application` uyarısı var, ancak bu yalnızca
  öngörülü geri animasyonlarını kapatır; `BackHandler`'ı kırmaz. Etkinleştirmek geri tuşu
  davranışını uygulama genelinde değiştireceği için, bu ortamda test edilemeden riskli bulundu.
- "Geri tuşuyla oynatıcıdan çıkamama" için ayrı bir kod kusuru **bulunamadı**: oynatıcı
  Activity'sinde geri tuşunu yutan bir `BackHandler`/`dispatchKeyEvent` yok
  (yalnızca `SubtitleSettingsPanel` ve `PlayerSheet` kendi kapanışları için kullanıyor).
  Kullanıcının çıkış garantisi ihtiyacı (G) maddesindeki çarpı butonuyla karşılandı.
