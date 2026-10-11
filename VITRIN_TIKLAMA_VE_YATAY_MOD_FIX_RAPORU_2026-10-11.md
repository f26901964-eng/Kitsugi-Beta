# Vitrin Tıklama + Yatay Mod (Manga Ortalama / Vitrin Boyutu) Fix Raporu — 2026-10-11

## Şikayetler

1. Vitrindeki (Keşfet hero karuseli) içeriğe tıklayınca **bazen** detay sayfası
   açılıyor, **bazen hiçbir şey olmuyor**.
2. Yatay modda **manga içerik görselleri tam ortalanmıyor** — kapak ince bir
   şerit hâlinde kesilmiş duruyor.
3. Yatay modda vitrin gereğinden fazla kısıtlanmış; ekran/arka plan görseli
   boyutuna göre genişlemesi gerekiyor.

## 1) Kök Neden — Tıklama

`KitsugiHeroSection` üç görünür sayfayı (`current-1`, `current`, `current+1`)
üst üste bindiriyor ve Compose'ta **en son çizilen kardeş en üstte** olduğu için
dokunmayı ilk o yakalıyor. Bunun üstüne iki ayrı problem vardı:

* **Ölü bölge (asıl sebep):** Başlık metni ve logo görseli kendi üzerinde
  `Modifier.copyOnDoubleTap(...)` taşıyordu. Bu modifier
  `pointerInput { detectTapGestures { ... } }` kullanıyor ve
  `detectTapGestures` **dokunmayı tüketiyor (consume)**. Main pass'te çocuk
  düğüm ebeveyninden önce çalıştığı için, başlık/logo üstüne gelen dokunuşlar
  orada yutuluyor ve vitrinin `clickable` katmanına hiç ulaşmıyordu.
  → Afişe/görsele basınca açılıyor, **başlığa/logoya basınca açılmıyordu**.
* **Katman kısıtı:** Tıklama yalnızca "ön" katmana (`frontPageIndex`) bağlıydı.
  Bu değer `derivedStateOf` ile her animasyon karesinde yeniden hesaplanıyordu
  (hem 60 Hz recompose yükü hem de geçiş animasyonu sırasında katman
  değiştiği için boşa giden tıklamalar).

### Çözüm

* Başlık/logo üzerindeki `copyOnDoubleTap` **kaldırıldı**. Kopyalama hareketi
  artık metin bloğunun kendi `tvClickable` düğümünde:
  **kısa dokunuş = detay sayfası**, **basılı tutma (long press) = başlığı
  panoya kopyala**. Tek düğüm = dokunma yutma yok.
* Tıklama artık **tüm görünür katmanlarda** aynı `openVisibleHeroItem()`
  lambda'sına bağlı. Lambda tıklama anında
  `round(currentPage + currentPageOffsetFraction)` ile ekranda görsel olarak
  baskın olan öğeyi çözüyor; **hangi katman dokunmayı yakalarsa yakalasın
  doğru içerik açılır** (geçiş animasyonunun ortasında bile).
* `frontPageIndex` ve beraberindeki kare başı `derivedStateOf` tamamen
  kaldırıldı → hem tıklama güvenilir hem de kaydırma sırasında gereksiz
  recompose yok.

## 2) Kök Neden — Yatay modda manga kapağı

Geniş bantta (yatay mod ≈ 2.4:1) görsel tek katman hâlinde
`ContentScale.Crop` ile çiziliyordu. 2:3 dikey kapak bu banda sığdırılmak için
yatayda tamamen doldurulup **dikeyde kesiliyordu**; geriye kapağın ortasından
ince bir şerit kalıyor, üstteki logo ve alttaki bilgiler kayboluyordu.

### Çözüm — `heroUsesFitPresentation()`

Kutu en-boy oranı **≥ 1.15** (geniş bant) **ve** seçilen görsel dikey/poster
ise iki katmanlı sunum:

1. **Arka dolgu:** aynı görsel `Crop` + 1.14x büyütme + `safeBlur(26.dp)`
   (API 31 altında bulanıklık yok; yerine %60 koyu perde devrede) → kutuda
   boşluk kalmaz.
2. **Ön plan:** aynı görsel `ContentScale.Fit` + `Alignment.Center`,
   yüksekliğin %82'si, dikeyde hafif yukarı bias (-0.45) → **kapak kırpılmadan
   ve tam ortalanmış** görünür, alt metin bloğu kapağı kapatmaz.

Yatay fanart/backdrop varsa davranış değişmedi: görsel vitrini baştan sona
kaplıyor (sinema şeridi).

## 3) Kök Neden — Yatay modda vitrin boyutu

Yükseklik sabit bir dp bandına sıkıştırılmıştı:

| Ekran | Eski | Yeni |
|---|---|---|
| Yatay telefon (914×411 dp) | 247 dp (`0.60×h`, 210–265 sınırı) | **370 dp** (ekran yüksekliğinin %90'ı) |
| Yatay telefon (800×360 dp) | 216 dp | **324 dp** |
| Yatay tablet (1280×800 dp) | 384 dp (`0.48×h`, 300–390 sınırı) | **640 dp** (ekran yüksekliğinin %80'i) |
| Yatay tablet (960×600 dp) | 300 dp | **480 dp** |

### Çözüm — `heroLandscapeHeightDp()`

Yükseklik artık **ekran genişliği / 16:9** (arka plan görselinin oranı)
formülünden hesaplanıyor; tek sınır ekran yüksekliğinin belli bir oranı
(telefon %90, tablet %80) ve çok küçük pencereler için alt sınır
(`coerceIn` güvenli — min > max olsa bile patlamıyor).
Aynı hesap `KitsugiShimmerHeroSection` yer tutucusunda da kullanılıyor:
veri gelince vitrin zıplamasın.

## Etkilenen Dosyalar

| Dosya | Değişiklik |
|---|---|
| `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiHeroSection.kt` | Tıklama güvenliği (tüm katmanlar + kopyalama long-press'e taşındı), `heroUsesFitPresentation`, `heroLandscapeHeightDp`, fit sunum katmanları |
| `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiShimmer.kt` | Yer tutucu yüksekliği gerçek vitrinle aynı formülden |
| `app/src/test/java/com/kitsugi/animelist/ui/components/HeroLandscapeSizingTest.kt` | **YENİ** — 7 birim testi (yatay yükseklik + fit sunum kararları) |

## Testler (senin çalıştırman gerekiyor)

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest --tests "*HeroLandscapeSizingTest" \
                                 --tests "*HeroImageSelectionTest" \
                                 --tests "*HeroSelectionTest"
```

## Manuel QA

- [ ] Dikey/yatay: vitrin görseline **ve** başlığa/logoya bas → detay açılıyor mu?
- [ ] Başlığa basılı tutunca başlık panoya kopyalanıyor mu? (toast)
- [ ] Sayfa geçişi animasyonunun ortasında tıklayınca **doğru** içerik açılıyor mu?
- [ ] Yatay modda manga vitrini: kapak tam ve ortalanmış mı, arka dolgu boşluk bırakıyor mu?
- [ ] Yatay modda anime/film (backdrop var): görsel ekranı baştan sona kaplıyor mu?
- [ ] Yatay modda vitrin yüksekliği ekranı dolduruyor mu; altındaki raflardan bir parça görünüyor mu?
- [ ] Yükleme sırasında shimmer yer tutucusu → gerçek vitrin geçişinde zıplama var mı?
- [ ] TV: Enter/D-pad ile vitrin öğesi açılıyor mu (dokunma yolu kapalı kaldı)?
- [ ] Kaydırma (swipe) sonrası istem dışı detay açılması var mı? (olmamalı)
