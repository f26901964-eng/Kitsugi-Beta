# PLAN & TASK — Geliştirici Günlükleri Seçim Çökmesi + Açılış Sertleştirmesi

**Tarih:** 2026-10-21
**Sürüm:** v2.4.321
**Tetikleyen rapor:** `Kitsugi_Crash_Report (31).txt` (v2.4.307, 2026-10-19 20:26:43, Xiaomi 23043RP34C / Android 13)
**İlgili kök neden raporu:** `KOK_NEDEN_RAPORU_GELISTIRICI_GUNLUKLERI_SECIM_COKMESI_2026-10-21.md`

---

## 1. Amaç

1. Rapordaki `java.util.NoSuchElementException: Cannot find value for key 1` çökmesini **kaynağında** bitirmek.
2. "Uygulama açılmadan çöküyor" şikâyetinin iki zincir hatasını (her açılışta tekrar eden uyarı + hep eski
   raporun paylaşılması) düzeltmek.
3. Bir daha aynı körlüğü yaşamamak için açılışta ölen süreçlerde **kesin kanıt** bırakmak.

## 2. Kök neden (özet)

`DeveloperLogsDialog` içinde `SelectionContainer { LazyColumn { ... } }` kullanılıyordu.
Lazy liste öğeleri kaydırılınca kompozisyondan düşüyor; Compose `SelectionManager` sürükleme sırasında
o öğenin `selectableId`'sini `LongIntMap`'te arıyor ve bulamayınca ana thread çöküyor.
Upstream: `compose-multiplatform-core#3480`. Compose 1.7.x (BOM 2024.12.01) bu hatayı içeriyor.

## 3. Görevler

| # | Görev | Dosya | Durum |
|---|-------|-------|-------|
| T1 | `SelectionContainer` kapsamını her günlük satırının içine taşı (LazyColumn'ı sarma) | `ui/screens/settings/DeveloperLogsDialog.kt` | ✅ |
| T2 | Çökme uyarısı her kapanışta "okundu" işaretlensin (rapor dosyaları korunarak) | `MainActivity.kt` | ✅ |
| T3 | Dosya tabanlı "sessiz kapanma" uyarılarına 7 gün tazelik sınırı | `core/diagnostics/KitsugiCrashLogger.kt` | ✅ |
| T4 | Paylaşılan rapor her zaman en yeni kanıt olsun (`ensureCrashLogFile` → `readCrashLog`); `crash_log.txt` arşivi ezilmesin | `core/diagnostics/KitsugiCrashLogger.kt`, `ui/screens/crash/KitsugiCrashActivity.kt` | ✅ |
| T5 | Açılış aşaması izleyicisi (`startup_stage.txt`); UI ilk karesinde temizle | `core/diagnostics/StartupStage.kt` *(yeni)*, `MainActivity.kt` | ✅ |
| T6 | `Application.attachBaseContext` / `onCreate` + tüm alt sistem init'lerini izole et ve işaretle | `KitsugiApplication.kt` | ✅ |
| T7 | Splash: `super.onCreate` öncesi prefetch ve animasyonu korumaya al; hata → doğrudan ana ekran | `KitsugiSplashActivity.kt` | ✅ |
| T8 | Sürüm 2.4.321 + sürüm notları + kök neden raporu | `app/build.gradle.kts`, `release_notes_v2.4.321.md`, `RELEASE_NOTES.md` | ✅ |

## 4. Değişen dosyalar

```
app/build.gradle.kts
RELEASE_NOTES.md
app/src/main/java/com/kitsugi/animelist/KitsugiApplication.kt
app/src/main/java/com/kitsugi/animelist/KitsugiSplashActivity.kt
app/src/main/java/com/kitsugi/animelist/MainActivity.kt
app/src/main/java/com/kitsugi/animelist/core/diagnostics/KitsugiCrashLogger.kt
app/src/main/java/com/kitsugi/animelist/core/diagnostics/StartupStage.kt          (YENİ)
app/src/main/java/com/kitsugi/animelist/ui/screens/crash/KitsugiCrashActivity.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/settings/DeveloperLogsDialog.kt
release_notes_v2.4.321.md                                                          (YENİ)
KOK_NEDEN_RAPORU_GELISTIRICI_GUNLUKLERI_SECIM_COKMESI_2026-10-21.md                (YENİ)
PLAN_TASK_GELISTIRICI_GUNLUKLERI_SECIM_COKMESI_VE_ACILIS_SERTLESTIRMESI_2026-10-21.md (YENİ)
```

## 5. Doğrulama (cihazda)

1. **T1:** Hakkında → Geliştirici Günlükleri → listeye uzun basıp sürükle, kaydır, tekrar seç.
   Çökme olmamalı; satır seçimi/kopyalama çalışmalı.
2. **T2:** Çökme uyarısını geri tuşuyla kapat → uygulamayı kapat/aç → uyarı tekrar etmemeli.
3. **T4:** "Hata dosyasını paylaş" → dosya başlığındaki `Version` **kurulu sürüm** olmalı.
4. **T5/T6:** Açılışta ölüm olursa yeni raporda `▶ BAŞLATMA AŞAMASI` bloğu son çalışan init adımını
   göstermeli; ayrıca `adb shell run-as com.kitsugi.animelist cat files/startup_stage.txt` ile
   doğrudan okunabilir (ana ekran açıldıysa dosya silinmiş olur).

## 6. Notlar / sınırlar

- Compose sürümü bu değişiklikte **yükseltilmedi** (BOM 2024.12.01). Kalıp tamamen kaldırıldığı için
  yükseltme zorunlu değil; ileride Compose 1.8+ ile `SelectionContainer + Lazy` tekrar denenebilir.
- Yerelde Android SDK/JDK olmadığı için derleme bu ortamda yapılamadı; doğrulama GitHub Actions ve
  cihaz testine bırakıldı (kullanıcı talebi).
- Tüm init sarmalamaları `runCatching` ile yapıldı; başarısız alt sistem yalnızca kendisi devre dışı
  kalır, uygulama açılmaya devam eder.

## 7. Birleştirme (merge) notu — 2026-10-21

Bu çalışma hazırlanırken `main` iki commit ilerledi (takvim "ilk açılışta boş" düzeltmesi, `85be627`
ve `3d8d6ed`). Dallar `63ced48` merge commit'i ile birleştirildi:

- **Çakışma yalnızca belgelerdeydi:** `RELEASE_NOTES.md` ve `release_notes_v2.4.321.md`. İki taraf da
  aynı sürüm numarasını (2.4.321) açtığı için aynı başlık altında **tek v2.4.321 bölümü** oluşturuldu:
  önce çökme/açılış düzeltmeleri, ardından takvim düzeltmesi (TR ve EN).
- **Kod çakışması yok:** bu çalışmanın dokunduğu 9 Kotlin/Kotlin DSL dosyası ile takvim düzeltmesinin
  dosyaları ayrık; otomatik birleşti.
- `appVersionName` iki tarafta da `2.4.321` olduğu için ek sürüm artırımı gerekmedi.
- PR #17 bu birleştirmeden sonra **MERGEABLE / CLEAN** durumda.
