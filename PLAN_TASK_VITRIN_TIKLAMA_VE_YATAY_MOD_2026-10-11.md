# PLAN & TASK — Vitrin Tıklama Ölü Bölgesi + Yatay Mod (Manga Ortalama / Vitrin Boyutu)

**Tarih:** 2026-10-11 · **Dal:** `arena/630216c3-kitsugi-beta` · **Commit:** `cb59335`
**Kapsam:** `ui/components/KitsugiHeroSection.kt`, `ui/components/KitsugiShimmer.kt`

---

## 1. Şikayetler

| # | Şikayet |
|---|---|
| 1 | Vitrindeki içeriğe tıklayınca **bazen** detay sayfası açılıyor, **bazen hiçbir şey olmuyor**. |
| 2 | Yatay modda **manga içerik görselleri tam ortalanmıyor**, kapak ince bir şerit hâlinde kesiliyor. |
| 3 | Yatay modda vitrin gereğinden fazla kısıtlanmış; ekran ve arka plan görseli boyutuna göre genişlemeli. |

---

## 2. Kök Neden Analizi

### 2.1 Tıklama (kritik)

`KitsugiHeroSection` görünür sayfaları (`current-1`, `current`, `current+1`) üst üste
bindirir. Compose'ta **son çizilen kardeş en üstte** durur ve dokunmayı ilk o yakalar.

Bunun üstüne iki ayrı hata vardı:

1. **Ölü bölge (asıl sebep):** Başlık metni ve logo görseli kendi üzerinde
   `Modifier.copyOnDoubleTap(...)` taşıyordu (`utils/CopyUtils.kt`). Bu modifier
   `pointerInput { detectTapGestures { ... } }` kullanır ve `detectTapGestures`
   **dokunmayı tüketir (consume)**. `PointerEventPass.Main` çocuk → ebeveyn
   sırasıyla çalıştığı için başlık/logo üstüne gelen dokunuşlar orada yutuluyor,
   vitrinin `clickable` katmanına hiç ulaşmıyordu.
   → **Afişe basınca açılıyor, başlığa/logoya basınca açılmıyordu.**
2. **Katman kısıtı:** Tıklama yalnızca "ön" katmana (`frontPageIndex`) bağlıydı.
   Değer `derivedStateOf` ile her animasyon karesinde yeniden hesaplanıyordu →
   hem kare başı recompose yükü hem de geçiş animasyonu sırasında boşa giden
   tıklamalar.

### 2.2 Yatay modda manga kapağı

Geniş bantta (yatay mod ≈ 2.4:1) görsel tek katman hâlinde `ContentScale.Crop`
ile çiziliyordu. 2:3 dikey kapak yatayda tam doldurulup **dikeyde kesiliyor**,
kapağın ortasından ince bir şerit kalıyordu (`heroImageAlignment` bias'ı -0.2
bunu telafi edemiyordu).

### 2.3 Yatay modda vitrin boyutu

Yükseklik sabit dp bandına sıkıştırılmıştı: telefon `0.60 × h` (210–265 dp),
tablet `0.48 × h` (300–390 dp). Ekran büyüdükçe vitrin büyümüyordu.

---

## 3. Görev Listesi

### ✅ Tamamlananlar

- [x] **T1 — Ölü bölge kaldırıldı.** Başlık/logo üzerindeki `copyOnDoubleTap`
      kaldırıldı. Kopyalama hareketi metin bloğunun kendi `tvClickable` düğümüne
      taşındı: **kısa dokunuş = detay**, **basılı tutma (long press) = panoya kopya**.
      Tek düğüm → dokunma yutma yok.
- [x] **T2 — Tıklama tüm katmanlarda.** Her görünür katman artık aynı
      `openVisibleHeroItem()` lambda'sını kullanıyor; lambda tıklama anında
      `round(currentPage + currentPageOffsetFraction)` ile görsel olarak baskın
      öğeyi çözüyor. Hangi katman yakalarsa yakalasın doğru içerik açılır.
- [x] **T3 — `frontPageIndex` kaldırıldı.** Kare başı `derivedStateOf`
      recompose yükü gitti.
- [x] **T4 — `heroUsesFitPresentation()`.** Kutu oranı ≥ 1.15 **ve** seçilen
      görsel poster ise iki katmanlı sunum:
      1. Arka dolgu: aynı görsel `Crop` + 1.14x + `safeBlur(26.dp)` (API < 31'de
         bulanıklık yok, %60 koyu perde devrede).
      2. Ön plan: `ContentScale.Fit` + `Alignment.Center`, yüksekliğin %82'si,
         dikey bias -0.45 → kapak **kırpılmadan ve tam ortalanmış**.
      Yatay backdrop varsa davranış değişmedi (tam ekran Crop).
- [x] **T5 — `heroLandscapeHeightDp()`.** Yatay vitrin yüksekliği artık
      **ekran genişliği ÷ 16:9**; tek sınır ekran yüksekliğinin oranı
      (telefon %90, tablet %80) + güvenli alt sınır (`coerceIn` min > max olsa
      bile patlamıyor).
- [x] **T6 — Shimmer yer tutucusu** (`KitsugiShimmerHeroSection`) aynı formülü
      kullanıyor → veri gelince vitrin zıplaması yok.
- [x] **T7 — Birim testleri:** `HeroLandscapeSizingTest.kt` (7 test).

### ❯ Sende kalanlar

- [ ] **Derleme kontrolü:** `./gradlew :app:compileDebugKotlin`
- [ ] **Birim testleri:**
      ```bash
      ./gradlew :app:testDebugUnitTest --tests "*HeroLandscapeSizingTest" \
                                       --tests "*HeroImageSelectionTest" \
                                       --tests "*HeroSelectionTest"
      ```
- [ ] **Manuel QA:**
  - [ ] Dikey/yatay: vitrin görseline **ve** başlığa/logoya bas → detay açılıyor mu?
  - [ ] Başlığa basılı tutunca başlık panoya kopyalanıyor mu (toast)?
  - [ ] Sayfa geçişi animasyonunun ortasında tıklayınca **doğru** içerik açılıyor mu?
  - [ ] Yatay manga vitrini: kapak tam + ortalanmış mı, arka dolgu boşluk bırakıyor mu?
  - [ ] Yatay anime/film (backdrop var): görsel ekranı baştan sona kaplıyor mu?
  - [ ] Yatay modda vitrin ekranı dolduruyor mu; altındaki raflardan parça görünüyor mu?
  - [ ] Shimmer → gerçek vitrin geçişinde zıplama var mı?
  - [ ] TV: Enter / D-pad ile vitrin öğesi açılıyor mu?
  - [ ] Swipe sonrası istem dışı detay açılması var mı? (olmamalı)

---

## 4. Dosya Bazında Değişiklikler

| Dosya | Değişiklik |
|---|---|
| `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiHeroSection.kt` | T1–T5: tıklama güvenliği, `heroUsesFitPresentation`, `heroLandscapeHeightDp`, fit sunum katmanları |
| `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiShimmer.kt` | T6: yer tutucu yüksekliği gerçek vitrinle aynı formülden |
| `app/src/test/java/com/kitsugi/animelist/ui/components/HeroLandscapeSizingTest.kt` | **YENİ** — 7 birim testi |
| `VITRIN_TIKLAMA_VE_YATAY_MOD_FIX_RAPORU_2026-10-11.md` | **YENİ** — kök neden + çözüm raporu |

## 5. Uygulama Notu

Zip içindeki `app/...` yol yapısı korunmuştur: repo köküne açıp doğrudan üzerine
kopyalayabilirsin. `changes.patch` takip edilen dosyaların diff'ini içerir.

## 6. Ölçüm Tablosu (yatay mod vitrin yüksekliği)

| Cihaz (yatay dp) | Eski | Yeni | Oran |
|---|---|---|---|
| Telefon 914×411 | 247 dp | 370 dp | ekranın %90'ı |
| Telefon 850×392 | 235 dp | 353 dp | %90 |
| Telefon 800×360 | 216 dp | 324 dp | %90 |
| Tablet 960×600 | 300 dp | 480 dp | %80 |
| Tablet 1280×800 | 384 dp | 640 dp | %80 |
