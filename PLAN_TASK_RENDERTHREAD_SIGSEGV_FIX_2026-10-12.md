# 🧯 PLAN / TASK — Kitsugi v2.4.264: RenderThread SIGSEGV (libhwui.so) Çökme Düzeltmesi

**Tarih:** 2026-10-12
**Kaynak rapor:** `Kitsugi_Crash_Report (20).txt`
**Hedef sürüm:** v2.4.264
**Durum:** 🔧 KOD DÜZELTMELERİ TAMAMLANDI, derleme/test bekleniyor

---

## 1. Kök Özet

```
signal=SIGSEGV (11)  fault_addr=0x20  thread=RenderThread
pc/lib: libhwui.so (+0x1ff650 / +0x779c4 / +0x109550 ...)
```

- RenderThread'de RenderNode/SkImageFilter üzerinde **sonsuz özyineleme** + NULL+0x20 erişim
- Stack'te 4 adres 30+ kez tekrar ediyor → stack taşması → NULL pointer
- Eşlikçi bulgular: 34 MB'lık GC döngüleri, 83-91 frame skip, 1.2+ sn Davey süreleri
- Sonuç: "pat diye sessiz kapanma" — Java handler'ı hiç görmüyor

## 2. Kök Nedenler (5 zincir)

| # | Neden | Dosya | Şiddet |
|---|---|---|---|
| 1 | AsyncImage `onSuccess` çizim evresinde state değiştiriyor → vitrin aynı kare içinde yeniden boyutlanıyor → RenderNode yarışı | `KitsugiNsfwImage.kt`, `KitsugiHeroSection.kt` | 🔥 KRİTİK |
| 2 | GPU RenderEffect (blur) MIUI/HyperOS/ColorOS/OriginOS/RealmeUI'de katman imhasında patlıyor; tespit/kapatma yoktu | `KitsugiNsfwImage.kt` | 🔥 KRİTİK |
| 3 | Bitmap stack-blur tam çözünürlükte çalışıyor (96 MB+ allocation) → GC storm → LMKD / yarış | `BlurTransformation.kt` | 🟠 YÜKSEK |
| 4 | Activity destroy'da HardwareRenderer senkronize boşaltılmıyor (dil/ekran dönüşü) | `KitsugiApplication.kt` | 🟠 YÜKSEK |
| 5 | TV haze + blur katmanları da aynı OEM tespitine tabi olmalı | `ModernSidebarBlurPanel.kt` (dolaylı) | 🟡 ORTA |

## 3. Yapılan Düzeltmeler

### 3.1 `ui/components/KitsugiNsfwImage.kt`
- [x] `KitsugiBlurSupport.isProblematicOem` OEM tespiti (Xiaomi/Redmi/POCO/Realme/OPPO/OnePlus/Vivo/iQOO/Meizu/Infinix/Tecno/iTEL/Honor/HMD + `ro.miui.*`, `ro.vivo.*`, `ro.build.version.opporom`, `ro.rom.version` sistem özellikleri)
- [x] `hasRenderEffectBlur` sorunlu OEM'lerde ve düşük RAM (<256 MB heap) cihazlarda `false`
- [x] `MAX_SAFE_BLUR_RADIUS` 16dp → **12dp**
- [x] `successCallback` ve `errorCallback` state değişimleri `Handler(Looper.getMainLooper()).post { }` ile **bir kare sonrasına** ertelendi
- [x] `Handler/Looper` import'u eklendi

### 3.2 `ui/components/KitsugiHeroSection.kt`
- [x] `postAfterFrame { ... }` yardımcıcısı eklendi (mainHandler lazy)
- [x] İKİ adet `onImageAspectResolved` çağrısı (dual-layer contain kolu ve tam ekran cover kolu) `postAfterFrame` içine alındı
- [x] `android.os.Handler, Looper` import'ları eklendi

### 3.3 `ui/utils/BlurTransformation.kt`
- [x] Büyük görseller için otomatik `scaleDown` (maxDim ≤ 400 → 1×; ≤ 800 → 2×; ≤ 1600 → 4×; >1600 → 6×)
- [x] Blur yarıçapı aynı oranda küçültülüyor (`scaledR = r/scaleDown`)
- [x] Ara bitmap'ler `recycle()` ile belleğe geri veriliyor
- [x] Gerekirse orijinal boyuta `createScaledBitmap` ile geri ölçekleme

### 3.4 `KitsugiApplication.kt`
- [x] `onActivityDestroyed` içinde `safeFreeHardwareRenderer(activity)` çağrısı
- [x] `safeFreeHardwareRenderer()` yardımcıcısı: DecorView'dan `mAttachInfo.mThreadedRenderer`/`mHardwareRenderer` reflektif olarak bulunup `pause()`, `syncFrame()`, `drainFence()`, `notifyFramePending()` çağrılıyor; `destroyDrawingCache()` temizliği
- [x] Herhangi bir platform sürümünde çalışmazsa `runCatching` ile güvenli biçimde yutuluyor

### 3.5 `ui/components/KitsugiPlasmaLoader.kt`
- [x] `PlasmaBrushCache` data class iskeleti eklendi (sonraki iterasyonda brush cache'lemesi için; şu an veri sınıfı olarak yer alıyor)

## 4. Değişmeyen / Bekleyen Öğeler

- [ ] **Derleme / cihaz testi** — bu ortamda Android SDK/JDK bulunmadığı için yapılamadı. Statik parantez/süslü denge doğrulandı (tüm dosyalarda 0).
- [ ] **TV `hazeChild` katmanı** — TV cihazları çoğunlukla Xiaomi telefon ROM'u değil (Google TV/Fire TV/Chromecast); şimdilik dokunulmadı. Yeni çökme raporunda TV tarafı da görülürse `blurEnabled` bayrağını OEM tespitine bağla.
- [ ] **PlasmaLoader brush cache** — `remember(c1, c2, coreGlowPulse)` ile 8 brush'ı cache'lemek GC churn'unu daha da azaltacak (kritik değil, sonraki adım).
- [ ] Version code/name artışı (release notlarında v2.4.264 olarak duyurulmalı).

## 5. Test Planı (cihazda)

1. [ ] APK'yı kur (MIUI/HyperOS cihazda özellikle).
2. [ ] NSFW ayarı AÇIK: ana sayfa vitrini 30-60 saniye gez, anime ↔ manga arasında geçiş yap (poster ↔ backdrop).
3. [ ] NSFW KAPALI: aynı vitrin testini tekrarla.
4. [ ] Detay sayfası → galeri aç → kapat → geri dön.
5. [ ] Dil değiştir (TR ↔ EN), ekranı döndür.
6. [ ] Arama → sonuç detayına tıkla → geri gel.
7. [ ] Cinematic loading ekranının göründüğü geçişleri (detay açılışı) zorla.
8. [ ] 2-3 dakika normal kullanımdan sonra logcat incele: "Skipped X frames" > 30 olmamalı, "Davey!" 500 ms altında olmalı.

## 6. Çıkış Kriteri

Yeni derlemede:
- RenderThread SIGSEGV raporu gelmemeli
- "Choreographer: Skipped X frames" X < 30 olmalı
- GC "freed X MB" döngüleri <5 MB/seviyeye inmeli (blur scale-down sonrası)
- MIUI test cihazında 10 dakikalık kesintisiz gezinme sonrası çökme olmamalı

---

**İlgili rapor:** `COKME_RAPOR_20_KOK_NEDEN_VE_COZUM_2026-10-12.md`
