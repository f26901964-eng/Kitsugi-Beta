# KÖK NEDEN RAPORU — "Uygulama açılmadan çöküyor" + Geliştirici Günlükleri seçim çökmesi

**Tarih:** 2026-10-21
**İncelenen rapor:** `Kitsugi_Crash_Report (31).txt` (Google Drive, 2026-10-20 23:27'de indirilmiş)
**Rapordaki sürüm:** 2.4.307-beta.29872707 (release) — 2026-10-19 20:26:43
**Cihaz:** Xiaomi 23043RP34C (pipa), Android 13 (API 33), arm64-v8a
**Düzeltilen sürüm:** 2.4.321

---

## 1. Rapordaki her satırın okunması

| Bölüm | Değer | Anlamı |
|---|---|---|
| Uygulama | v2.4.307-beta.29872707, release, uptime **47 sn** | Süreç 47 saniye yaşamış — yani uygulama AÇILMIŞ, sonra çökmüş. |
| Thread | `main`, FOREGROUND | Çökme ana UI thread'inde. |
| Bellek | 69 MB / 512 MB | OOM yok. |
| Hata | `java.util.NoSuchElementException: Cannot find value for key 1` | Bir `LongIntMap` anahtarı kayıp. |
| Stack | `SelectionManager$getSelectionLayout$$inlined$compareBy$1.compare` → `MultiWidgetSelectionDelegateKt.getDirectionById` → `appendSelectableInfo` → `SelectionManager.updateSelection` → `handleDragObserver$1.onDrag` | **Metin seçiminde parmak sürükleme** sırasında, çoklu parçalı seçim düzeninde eksik `selectableId`. |
| Son ekran | `About(depth=1)` | Hakkında ekranı açıkken. |
| Eylem izi | `Tab(Settings)` → `About(depth=1)` (20 sn sonra çökme) | Kullanıcı Ayarlar → Hakkında'ya girmiş; oradan **Geliştirici Günlükleri**ni açmış (diyalog olduğu için son ekran hâlâ "About" görünür). |

## 2. Kök neden (kanıtlanmış)

`DeveloperLogsDialog.kt` içinde günlük listesi şu şekilde sarılıydı:

```kotlin
SelectionContainer {
    LazyColumn {
        items(filteredLines) { line -> Text(line, ...) }
    }
}
```

Bu kalıp Compose Foundation'ın bilinen ölümcül hatasıdır:

1. `SelectionContainer`, her `Text` için bir `selectableId` üretir ve bunları `LongIntMap`'te tutar.
2. `LazyColumn` öğeleri ekran dışına kaydığında **kompozisyondan düşer** ve kayıtları silinir.
3. Kullanıcı metni uzun basıp sürükleyerek seçtiğinde `SelectionManager.getSelectionLayout`
   sürükleme çapasındaki eski `selectableId`'yi haritada arar.
4. Anahtar yoksa `LongIntMap.get` → `NoSuchElementException: Cannot find value for key N` fırlar ve
   **ana thread çöker**.

Bu, rapordaki stack ile **birebir** aynıdır. Upstream kaydı:
`compose-multiplatform-core#3480` — "SelectionContainer + lazy list anchor disposed".
Compose 1.7.x (projemizde BOM `2024.12.01`) bu hatayı hâlâ içerir.

> Not: Bu dosya v2.4.307 ile v2.4.320 arasında **hiç değişmedi**
> (`git diff v2.4.307 v2.4.320 -- .../DeveloperLogsDialog.kt` boş). Yani hata 2.4.320'de de aynen vardı.

## 3. "Uygulama açılmadan çöküyor" algısının iki ayrı sebebi

Raporun kendisi 19 Ekim'e ait olmasına rağmen 20 Ekim 23:27'de paylaşılması, iki zincir hatasını
ortaya çıkardı:

### 3.1 Çökme uyarısı her açılışta tekrar ediyordu
`KitsugiCrashRecoveryDialog` yalnızca kendi butonlarında `markCrashAsRead` çağırıyordu. Kullanıcı
**geri tuşuyla veya dışına dokunarak** kapattığında `has_unread_crash` bayrağı temizlenmiyordu.
Ayrıca `hasUnreadCrash`, `unclean_exit.txt` dosyasının **yaşına bakmıyordu**. Sonuç: uygulama her
açılışta kırmızı "Beklenmedik Kapanma Tespit Edildi" ekranıyla geliyor ve **hep aynı eski raporu**
gösteriyordu. Kullanıcı için bu, "uygulama açılmıyor/çöküyor"dur.

### 3.2 Paylaşılan rapor hep eski dosyaydı
`ensureCrashLogFile` sadece "`crash_log.txt` var mı?" diye bakıyordu. Daha yeni bir sessiz kapanma
(`unclean_exit.txt`, native SIGSEGV izi, yeni sürüme ait kayıt) olsa bile **eski Java çökmesini**
paylaşıma veriyordu. İndirdiğiniz dosyanın adının `Kitsugi_Crash_Report (31).txt` olması — aynı
dosyanın 31. kez indirilmesi — bunun kanıtıdır.

## 4. Yapılan düzeltmeler (v2.4.321)

| # | Dosya | Değişiklik |
|---|---|---|
| 1 | `ui/screens/settings/DeveloperLogsDialog.kt` | `SelectionContainer` artık `LazyColumn`'ı sarmıyor; **her satırın kendi içinde**. Kayıp `selectableId` oluşamaz. |
| 2 | `MainActivity.kt` | Çökme uyarısı **her kapanışta** "okundu" işaretlenir (rapor dosyaları silinmez). |
| 3 | `core/diagnostics/KitsugiCrashLogger.kt` | `ensureCrashLogFile` her zaman `readCrashLog()` ile **en yeni kanıtı** üretir; `crash_log.txt` arşivi ezilmez. Dosya tabanlı uyarılara 7 gün tazelik sınırı. |
| 4 | `core/diagnostics/StartupStage.kt` *(yeni)* | Açılışın her adımı `filesDir/startup_stage.txt`'ye yazılır; ana ekran ilk karesinde silinir. Süreç UI'dan önce ölürse rapora "son çalışan init adımı" eklenir. |
| 5 | `KitsugiApplication.kt` | Tüm alt sistem init'leri `runCatching` ile izole + aşama işaretleri. |
| 6 | `KitsugiSplashActivity.kt` | `super.onCreate` öncesi `ExploreViewModel.prefetch` ve açılış animasyonu korumaya alındı; hata olursa doğrudan ana ekrana geçilir. |
| 7 | `ui/screens/crash/KitsugiCrashActivity.kt` | "Geliştiriciye Gönder" artık `ensureCrashLogFile` kullanıyor (bayat dosya yok). |

## 5. Doğrulama adımları

1. **Seçim çökmesi:** Ayarlar → Hakkında → Geliştirici Günlükleri → günlük listesinde bir satıra uzun
   basıp aşağı/yukarı sürükleyin, listeyi kaydırın, tekrar seçin. Çökme olmamalı.
2. **Uyarı tekrarı:** Uygulamayı bir çökme sonrası açın, uyarıyı **geri tuşuyla** kapatın, uygulamayı
   kapatıp yeniden açın. Uyarı tekrar gelmemeli.
3. **Rapor tazeliği:** "Hata dosyasını paylaş" → paylaşılan dosyanın başlığındaki sürüm/tarih
   **kurulu sürümle** aynı olmalı.
4. **Açılışta ölüm:** Uygulama ana ekran açılmadan kapanırsa, bir sonraki açılıştaki raporda
   `▶ BAŞLATMA AŞAMASI` bloğu son çalışan init adımını gösterecek.

## 6. Hâlâ çöküyorsa ne gönderilmeli?

1. Uygulamayı açın → Hakkında → Geliştirici Günlükleri → **Kaydet** (tüm logcat + rapor).
2. Veya çökme ekranındaki **"Geliştiriciye Gönder (tüm dosyalar)"**.
3. Dosyaların başlığındaki `Version` satırının **kurulu sürümle** aynı olduğunu kontrol edin.
   2.4.321'den eski bir sürüm görünüyorsa rapor bayattır; güncel APK'yı kurup tekrar alın.
