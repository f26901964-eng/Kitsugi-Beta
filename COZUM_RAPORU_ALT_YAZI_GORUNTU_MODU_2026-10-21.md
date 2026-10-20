# Alt Yazı / Görüntü Modu Ayrıştırma Çözüm Raporu — 2026-10-21

**Sürüm:** v2.4.319
**Kullanıcı raporu:** "'Doldur' seçeneğini seçtiğim zaman harici altyazılar video ile birlikte otomatik genişliyor gibi oluyor, sonra ekran dışına / altına taşıyor. Video boyutlandırmasından etkilenmesin."

---

## 1. Kök neden

İki ayrı ölçekleme katmanı aynı yüzeye uygulanıyordu:

### 1.1 MPV motoru (varsayılan motor)

`KitsugiMpvSurfaceView.applyAspectModeInternal()` görüntü modunu **`View.scaleX` / `View.scaleY`** ile uyguluyordu:

```kotlin
scaleX = scale.scaleX
scaleY = scale.scaleY
```

`KitsugiMpvSurfaceView` bir `SurfaceView`'dur ve mpv **video ile birlikte altyazı/OSD katmanını da aynı yüzeye** çizer.
Dolayısıyla:

- `Doldur` (STRETCH) → `scaleY > 1` (veya `scaleX > 1`) → altyazı da esnetilir, yazı büyür.
- View merkezden ölçeklendiği için alt kenardaki altyazı satırları ekranın **altına taşar**.
- Üstelik `readVideoAspectRatio()` önce `video-out-params/*` (çıkış katmanı) okuyordu; bu değer
  `video-scale` uygulandıktan sonraki geometriyi de yansıtabildiği için "kendi kendini büyüten"
  (feedback) ölçek hatası riski vardı.

### 1.2 libass / OSD alanı (ikinci tetikleyici)

mpv, altyazı çizim alanını "pencereden videoya kalan boşluk" (margin) olarak hesaplar
(`video/out/aspect.c` → `osd.ml/mr/mt/mb`). Video pencereyi taşıdığında bu boşluk **negatif**
olur ve **libass negatif margin ile çizim alanını pencereden büyük sanar**; altyazıyı ekranın
altına yerleştirir. Uygulama `sub-use-margins=yes` ve `sub-ass-force-margins=yes` kullandığı için
bu yol aktifti.

### 1.3 Media3 motoru

`Media3PlayerEngine` altyazı görünümünü içerik çerçevesinden ayırıyordu (`detachSubtitleViewFromContentFrame`)
ancak bu kontrol **yalnızca `createVideoView()` sırasında** yapılıyordu. Görüntü modu sonradan
değiştiğinde görünümün hâlâ kök katmanda olduğu doğrulanmıyor ve konum tazelenmiyordu.

---

## 2. Uygulanan çözüm

### `KitsugiMpvSurfaceView.kt`

| Değişiklik | Amaç |
|---|---|
| `View.scaleX/scaleY` yerine **`video-scale-x` / `video-scale-y`** mpv özellikleri | Yalnızca video katmanı ölçeklenir; altyazı katmanı ölçeklenmez |
| Kalıntı view ölçeğinin `1.0`'a zorlanması | Eski davranıştan kalan ölçek varsa temizlenir |
| `applySubtitleWindowPolicy(scale)` | Video pencereyi taşıyorsa `sub-use-margins=no`, `sub-ass-force-margins=no` → libass çizim alanı **tam pencere**; taşmıyorsa eski davranış (`yes`) korunur |
| `sub-scale-by-window=yes`, `sub-scale-with-window=yes`, `sub-ass-scale-with-window=no` | Yazı boyutu pencereye göre; esnetilen görüntüde yazı büyümez |
| `readVideoAspectRatio()` artık önce `video-params/*` okuyor | Ölçek geri beslemesi (feedback) engellenir |
| `MIN_VIDEO_SCALE` / `MAX_VIDEO_SCALE` sınırları | Aşırı/geçersiz ölçek değerlerine karşı koruma |

### `Media3PlayerEngine.kt`

`applyAspectMode()` sonunda `detachSubtitleViewFromContentFrame(pv)` + `applySubtitleStylesToView()`
çağrıları eklendi: her görüntü modu değişiminde altyazı görünümünün içerik çerçevesinden ayrık
kaldığı doğrulanır ve konumu/alt boşluğu pencereye göre tazelenir.

### `PlayerAspectScaleUtils.kt`

`PlayerAspectMode.FILL` için `video-aspect-override` değeri `"yes"` → `"no"` yapıldı.
mpv bu özellikte yalnızca `no` veya `X:Y` kabul eder; `"yes"` geçersizdi ve sessizce yok sayılıyordu.
Esnetme zaten `video-scale` ile yapılıyor.

---

## 3. Doğrulama adımları

1. Harici bir `.srt` altyazı yükleyip oynat.
2. Görüntü modlarını sırayla değiştir: `Orijinal → Sığdır → Doldur → 16:9 → 4:3 → Yakınlaştır`.
3. Her modda beklenen: altyazı **aynı yazı boyutunda**, **ekranın alt kısmında**, taşma/kayma yok.
4. MPV ve Media3 motorlarında ayrı ayrı test et (Ayarlar → Oynatıcı).
5. Logcat kontrolü: `KitsugiMpvSurfaceView` → "Altyazı çizim alanı: pencereye sabitlendi".

## 4. Notlar

- Video pencereye sığdığında (Orijinal/Sığdır) altyazı hâlâ video alanı içinde konumlanır; bu
  modlarda görsel davranış değişmez.
- `sub-pos` değeri 100 üzerine çıkarıldığında libass kaynaklı kırpılma riski mpv dokümantasyonunda
  belirtilmiştir; bu değişiklikle ilgisi yoktur, mevcut davranış korunur.
