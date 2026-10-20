# PLAN_TASK — Alt Yazı / Görüntü Modu Ayrıştırması (v2.4.319)

**Tarih:** 2026-10-21
**Durum:** ✅ Tamamlandı
**Kullanıcı isteği:** "Doldur seçeneğini seçtiğimde harici altyazılar video ile birlikte genişliyor ve ekran dışına taşıyor. Alt yazılar video boyutlandırmasından etkilenmesin."

---

## 1. Hedefler

| # | Hedef | Durum |
|---|---|---|
| H1 | Harici altyazı görüntü modundan (Doldur / Yakınlaştır / 16:9 / 4:3) etkilenmesin | ✅ |
| H2 | Altyazı ekran dışına / alt barın altına taşmasın | ✅ |
| H3 | Altyazı yazı boyutu esnetmede büyümesin | ✅ |
| H4 | Orijinal / Sığdır modlarındaki mevcut görünüm bozulmasın | ✅ |
| H5 | Media3 motorunda da aynı davranış garanti edilsin | ✅ |
| H6 | Görüntü modu işlevi (esnetme/zoom) korunsun | ✅ |

## 2. Kök neden özeti

1. `KitsugiMpvSurfaceView.applyAspectModeInternal()` görüntü modunu `View.scaleX/scaleY` ile uyguluyordu.
   SurfaceView ölçeklendiğinde mpv'nin aynı yüzeye çizdiği altyazı/OSD katmanı da ölçekleniyordu
   (esneme + merkezden ölçek → alt satırlar ekran dışında).
2. `sub-use-margins=yes` / `sub-ass-force-margins=yes` iken video pencereyi taşıdığında libass
   negatif margin hesaplayıp altyazı alanını pencereden büyük sanıyordu.
3. `readVideoAspectRatio()` çıkış katmanını (`video-out-params/*`) öncelikli okuduğu için ölçek
   geri beslemesi riski vardı.
4. Media3 motorunda altyazı görünümü yalnızca `createVideoView()` anında içerik çerçevesinden
   ayrılıyordu; mod değişiminde doğrulanmıyordu.

## 3. Yapılan değişiklikler

### 3.1 `core/player/engine/KitsugiMpvSurfaceView.kt`
- [x] `scaleX/scaleY` (View) → `video-scale-x` / `video-scale-y` (mpv) geçişi
- [x] Kalıntı view ölçeğini 1.0'a zorlama (`applyVideoScale`)
- [x] `applySubtitleWindowPolicy(scale)`: video taşarsa `sub-use-margins=no`, `sub-ass-force-margins=no`
- [x] `sub-scale-by-window=yes`, `sub-scale-with-window=yes`, `sub-ass-scale-with-window=no`
- [x] `readVideoAspectRatio()` → önce `video-params/*`
- [x] `MIN_VIDEO_SCALE` / `MAX_VIDEO_SCALE` sınırları
- [x] Ölçek değişmediyse mpv'ye tekrar yazmama (gereksiz JNI çağrısı yok)

### 3.2 `core/player/engine/Media3PlayerEngine.kt`
- [x] `applyAspectMode()` sonunda `detachSubtitleViewFromContentFrame` + `applySubtitleStylesToView`

### 3.3 `core/player/PlayerAspectScaleUtils.kt`
- [x] `FILL` için geçersiz `video-aspect-override = "yes"` → `"no"`

### 3.4 Dokümantasyon / sürüm
- [x] `app/build.gradle.kts` → `appVersionName = "2.4.319"`
- [x] `release_notes_v2.4.319.md`
- [x] `RELEASE_NOTES.md` güncellendi
- [x] `COZUM_RAPORU_ALT_YAZI_GORUNTU_MODU_2026-10-21.md`

## 4. Test planı

| # | Senaryo | Beklenen |
|---|---|---|
| T1 | Harici SRT yükle → `Doldur` | Altyazı aynı boyutta, ekranın altında, taşma yok |
| T2 | `Doldur` → `Yakınlaştır (Kırp)` | Altyazı sabit konum/boyut |
| T3 | `16:9` / `4:3` | Altyazı sabit konum/boyut |
| T4 | `Orijinal` / `Sığdır` | Mevcut davranış (video alanı içinde) korunur |
| T5 | Media3 motorunda T1–T4 | Aynı sonuçlar |
| T6 | Logcat: `KitsugiMpvSurfaceView` | "Altyazı çizim alanı: pencereye sabitlendi" |
| T7 | ASS/gömülü altyazı | Konum ve boyut sabit, stil bozulmadan |

## 5. Riskler / geri alma

- **Risk:** `keepaspect=no` kullanıcı mpv.conf'unda açıksa `video-scale` devre dışı kalır (esnetme çalışmaz).
- **Risk:** Çok eski libmpv'de `sub-scale-with-window` / `sub-ass-scale-with-window` yoksa sessizce yok sayılır (konum düzeltmesi `sub-use-margins` üzerinden çalışmaya devam eder).
- **Geri alma:** `git revert 4d52229` — davranış eski (view ölçekli) hâle döner.
