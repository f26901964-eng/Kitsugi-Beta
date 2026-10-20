# Yüklenme Animasyonu (Plasma Orb) — Kare Çerçeve Kök Neden ve Çözüm Raporu

**Tarih:** 2026-10-21
**Sürüm:** v2.4.319
**Etkilenen dosya:** `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiPlasmaLoader.kt`
**Şikâyet:** Video yüklenirken ekranın ortasında çıkan animasyonun etrafında turuncu
**kare çerçeve** ve içinde **ikinci bir kare** var. Animasyonun olduğu her yerde
sadece **yuvarlak** olmalı; etrafında ekstra çember/kare olmamalı.

---

## 1. Kök neden

`KitsugiPlasmaLoader` içinde iki ayrı dikdörtgen çizim vardı:

| # | Kaynak | Ekranda görünen |
|---|--------|-----------------|
| 1 | `Canvas { drawRect(brush = Brush.verticalGradient(0.30f to c1, 0.70f to c2)) }` | Ortadaki **turuncu kare** dolgu |
| 2 | `Modifier.border(width = 1.2.dp, brush = ..., shape = CircleShape)` | Kutunun kenarındaki **turuncu kare çerçeve** |

Bunlara ek olarak kutu `Modifier.clip(CircleShape)` ile kırpılıyordu; yani kare
şekiller *normalde* daireye kırpılıp görünmemeleri gerekiyordu. Ancak:

* `direct draw` (drawRect) çağrısı `clip` katmanına **dahil edilmediği** durumlarda
  (yoğun bellek/GC baskısı altında graphics layer'ın düşmesi, oynatıcıdaki
  `AnimatedContent` + `scaleIn/scaleOut` geçişleri sırasında katman yeniden
  kurulması, SurfaceView/TextureView üstünde yeniden çizim) dikdörtgen doğrudan
  ekrana düşüyordu.
* `Modifier.border(...)` ise **kutu boyutuna** göre çizilir; kutu 80 dp, içerik
  42 dp olduğunda kenarlık içerikten çok uzakta kalıp "iç içe iki kare" görüntüsü
  üretiyordu (ekran görüntüsündeki büyük kare çerçeve + merkezdeki küçük kare).
* Ayrıca bileşen iki ayrı `size` modifier'ı kullanıyordu: dışarıdan gelen
  `Modifier.size(80.dp)` ile içerideki `.size(size)` çakışıyor, kutunun ölçüsü
  ile `Canvas`'ın ölçüsü farklı oluyordu (`Canvas(Modifier.size(size))`).

## 2. Çözüm

`KitsugiPlasmaLoader` tamamen daire tabanlı yeniden yazıldı:

1. **Dikdörtgen çizim yok.** `drawRect` kaldırıldı; gövde tek bir
   `drawCircle(brush = bodyBrush, radius = radius)`.
2. **Kenarlık/halka yok.** `Modifier.border(...)` çağrısı kaldırıldı. Ekstra
   çember (eski 1.2 dp halka) de artık yok.
3. **Garantili daire kırpma.** `PlasmaOrbShape` adında özel bir `Shape` eklendi:
   kutunun **en kısa kenarına** oturan, ortalanmış tam daire keser. Böylece kutu
   kare/dikdörtgen olsa veya genişlik ≠ yükseklik olsa bile sonuç asla oval ya da
   kare olmaz (`CircleShape` dikdörtgende elips üretir; bu şekil üretmez).
4. **Tek ölçü kaynağı.** Dış `modifier` korunur, iç kutu `.size(size)` ile
   tamamlanır ve `Canvas` artık `Modifier.fillMaxSize()` ile **ölçülen kutuya**
   göre çizer. `Centre` hizalama ile içerik kutunun tam ortasında kalır.
5. **Görsel iyileştirme (aynı kimlik, daha hacimli):**
   * gövde: dikey gradyan (c1 %42 → c2 %70)
   * 3 dönen plazma odağı (metaball, `BlendMode.Screen`), yörünge 0.34 R, yarıçap 0.72 R
   * nefes alan çekirdek (0.48 R × nabız, `Screen`)
   * beyaz-sıcak merkez (0.17 R, `Screen`)
   * cam highlight, alt gölge ve kenar vinyeti (siyah %55)
   * kompakt mod (< 30 dp): hâle + çekirdek + dönen parlak nokta (3 daire)
6. Tüm katmanlar kürenin **kendi yarıçapı içinde** çizildiği için dışarıya ışıma
   taşmaz; "etrafında ekstra halka" algısı oluşmaz.

## 3. Neden tüm ekranlarda geçerli?

Uygulamadaki bütün belirsiz yüklenme göstergeleri tek noktadan geçiyor:

```
androidx.compose.material3.CircularProgressIndicator  (uygulama genelinde import edilen tek referans)
        └── com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
                └── KitsugiPlasmaLoader
```

`KitsugiGradientMaterial.kt` içindeki sarmalayıcı, Material'ın dönen halkasını
tamamen `KitsugiPlasmaLoader`'a yönlendiriyor. Ayrıca doğrudan çağrılan ~60
nokta var (oynatıcı ortası, tam ekran yüklenme, arama, listeler, sheet'ler,
giriş pencereleri, pull-to-refresh, TV ekranları, manga). Hepsi aynı bileşeni
kullandığı için düzeltme **her yerde** geçerli.

## 4. Doğrulama

* `drawRect`, `drawRoundRect`, `Modifier.border` → bileşende **0 adet**.
* `CircleShape` importu kaldırıldı; kırpma `PlasmaOrbShape` ile yapılıyor.
* Piksel-sadık önizleme (aynı gradyan/yarıçap matematiği) ile görsel doğrulandı:
  turuncu kare çerçeve ve iç kare yok, tek bir yuvarlak, hacimli lav küresi.
* Bellek/GC notu korundu: renk adımları 32'ye kuantalanıyor, gradyan listeleri
  `remember` ile önbellekte; kare başına yeni `Brush` üretimi yok.

## 5. Regresyon riski

* Bileşenin dış sözleşmesi (`modifier`, `size`, `color`, `secondaryColor`)
  **değişmedi**; çağrı noktalarında düzenleme gerekmedi.
* Eski davranışta kutunun dışına taşan yumuşak ışıma vardı; yeni davranışta
  ışıma daire içinde kalıyor (istenen davranış). Bu, koyu arka planlarda
  görünümü sadeleştirir.
