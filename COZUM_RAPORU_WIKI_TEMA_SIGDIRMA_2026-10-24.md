# Wiki açılır sayfası — tema gerçekten çalışmıyordu + sayfa ekrana sığmıyordu

**Tarih:** 2026-10-24
**Sürüm:** v2.4.341
**Kapsam:** Uygulama içi özgün (WebView) wiki görünümü — "Görünüm ve yazı boyutu" penceresi, sayfa açılışındaki ölçek, yazı okunabilirliği.

---

## 1. Bildirilen sorun

Ekran görüntüsünde "Görünüm ve yazı boyutu" penceresi açık, **"Uygulama temasını izle"** seçili; arka planda
Fandom sayfası. Kullanıcı iki şey söylüyor:

1. "Siyah beyaz tema hiç çalışmıyor" — Açık/Koyu seçimi sayfayı değiştirmiyor.
2. "Sayfa ilk açıldığı anda kendini sığdırsın, elle ekrana tam oturtmak zorunda kalmayayım."

İkisi de kodda doğrulandı; ikisinin de kök nedeni farklı.

---

## 2. Kök nedenler

### 2.1 Tema: yalnızca site sınıfı değiştiriliyordu

Eski `page-appearance.js`, Fandom'da `body` üzerindeki `theme-fandomdesktop-light/dark` sınıfını, Wikipedia'da
`html` üzerindeki `skin-theme-clientpref-*` sınıfını değiştiriyordu. Üç ayrı nedenle bu, kullanıcı için
"hiçbir şey olmuyor" demekti:

| Neden | Sonuç |
|---|---|
| Büyük wikiler (Attack on Titan Wiki dâhil) renkleri **topluluk CSS'inde sabit kodlar** (`#content`, `.portable-infobox`, tablo zeminleri) | Sınıf değişse bile arka plan aynı kalır |
| Sınıf `html` üzerindeyse yalnızca `body`'ye yazılıyordu | Tema hiç uygulanmaz |
| Sınıf sözleşmesi tanınmayan (eski/özel) görünümlerde `supported: false` | Kullanıcıya "desteklenmiyor" uyarısı; tema yok |

Ayrıca eski kod **hiçbir stil enjekte etmiyordu**; yani wiki kendi renklerini sabitlediğinde yapacak bir şeyi yoktu.

### 2.2 Sığdırma: viewport yazılıyordu, ölçek yazılmıyordu

Masaüstü düzeninde sayfa 1200 CSS px'e yerleşiyor, fakat viewport meta yalnızca `width=1200` olarak ve
**`onPageFinished` sonrasında** yazılıyordu:

```js
meta.content = 'width=1200';   // ölçek yok
```

Sonuç: sayfa %100 ölçekle açılıyor, ekranın yalnızca ~1/3'ü görünüyor; kullanıcı her sayfada iki parmakla
küçültmek zorunda kalıyor. `loadWithOverviewMode` tek başına yetmiyor çünkü meta geç yazılıyor ve ölçek
yeniden hesaplanmıyor.

### 2.3 Sığdırma tek başına okunamaz sayfa üretir

1200 px genişlik 360 dp ekrana sığdırıldığında ölçek ≈ **0.30** olur; 16 px gövde metni ekranda ~4.8 dp'ye
düşer. Yani "sığdır ama yazıyı okunamaz yap" tuzağı. Ölçek ile yazı boyutu birlikte çözülmeli.

---

## 3. Çözüm

### 3.1 `page-appearance.js` v2 — "önce site, sonra uyumlu renk katmanı"

Yeni API: `window.__kitsugiWikiAppearanceV2(mode, palette)`.

- **SITE:** belgeye hiç dokunulmaz; enjekte edilen katman ve kök öznitelik kaldırılır, kaynak tema sınıfları
  ve `color-scheme` birebir geri yüklenir. Varsayılan budur.
- **LIGHT / DARK:**
  1. Sitenin kendi tema sözleşmesi, **taşıdığı kökte** (html veya body) değiştirilir;
     `theme-fandomdesktop-*`, `skin-theme-clientpref-day/night/os`.
  2. `html` üzerine `data-kitsugi-wiki-theme` ve `color-scheme` yazılır (kaydırma çubuğu, form denetimleri,
     yerel widget'lar da uyar).
  3. Uyumlu renk katmanı eklenir:
     - Fandom tasarım değişkenleri (`--theme-page-background-color`, `--theme-page-text-color`,
       `--theme-page-border-color`, `--theme-link-color`, …) ve Wikimedia Vector 2022 değişkenleri
       (`--background-color-base`, `--color-base`, `--border-color-base`, …) uygulama paletine bağlanır.
     - Sayfa gövdeleri (`#content`, `.mw-parser-output`, `.page`, `.global-navigation`, …),
       kartlar (`.portable-infobox`, `.navbox`, `.toc`, tablolar, `blockquote`, `pre`, sekmeler, arama
       alanları) ve hücre/başlık renkleri `!important` ile sabitlenir.
     - **Resim, video, oynatıcı, galeri ve iframe'lere hiçbir kural uygulanmaz; filtre/tersleme yok.**
  4. Katman `document.adoptedStyleSheets` ile eklenir (CSP'den etkilenmez); desteklenmeyen motorlarda
     `<style id="kitsugi-wiki-appearance">` yedeği kullanılır.
  5. Bir `MutationObserver`, geç gelen site render'ları (`<body>` değişimi, `<head>` yenilenmesi, sınıf
     sıfırlama) sonrasında durumu yeniden uygular. Tüm işlemler **idempotenttir**; geri besleme döngüsü
     oluşmaz (sınıf/öznitelik yalnızca gerçekten değişecekse yazılır).
- Palet **Kotlin tarafında** türetilir (`WikiReadingAppearance.palette`) ve JS'e JSON olarak verilir:
  uygulama koyuysa uygulamanın kendi zemin/metin rengi, değilse nötr açık/koyu palet. Böylece "uygulama
  temasını izle" gerçekten uygulamaya uyar, zorlanan tema ise her zaman okunur kalır.
- Palet değerleri `#rrggbb` dışındaysa (boş, `red`, `url(javascript:…)`, `#GGGGGG`) güvenli varsayılana düşer;
  değerler doğrudan CSS'e yazılmadan önce doğrulanır.

### 3.2 Sayfa açılışta kendini sığdırır

- `WebSettings.loadWithOverviewMode` masaüstünde kapatıldı; ölçek artık açıkça veriliyor.
- WebView oluşturulurken ekran genişliğinden hesaplanan `setInitialScale(30)` benzeri değer ilk boyamaya
  uygulanır (ilk kare bile sığar).
- `onPageCommitVisible` ve `onPageFinished` sonrasında viewport meta
  `width=1200, initial-scale=<ölçek>, minimum-scale=0.1, maximum-scale=5` olarak yazılır; ardından
  `zoomBy(target/current)` ile ölçek kesinleştirilir (WebView'da `zoomTo` yok).
- Döndürme/pencere boyutu değişiminde yeniden sığdırılır.
- **Kullanıcı yakınlaştırmasına saygı:** programatik zoom sonrası 400 ms'lik pencere kendi değişimimizi
  işaretler; sonrasında gelen ölçek değişimi kullanıcı jesti sayılır ve otomatik sığdırma durur. Belge
  yüklenirken motorun kendi ölçek oynamaları da 2.5 s boyunca yok sayılır.
- Sayfa araçlarına **"Sayfayı ekrana sığdır"** eylemi eklendi; kullanıcı yakınlaştırmış olsa bile tek
  dokunuşla sıfırlar.

### 3.3 Yazı, sığdırmayla birlikte okunur kalır

`WikiReaderFit.readingTextZoom(base, scale)`:

```
boost = clamp(0.75 / fitScale, 1.0, 2.2)
motor textZoom = clamp(base * boost, 50, 300)
```

- Telefon (360 dp / 1200 px, ölçek 0.30) → 100% tercih, ekranda ~225%.
- Tablet (800 dp, ölçek 0.67) → 112%.
- Mobil düzen (ölçek 1.0) → değişiklik yok.

Kullanıcı tercihi **ezilmez**: kaydırıcı 180% iken motor değeri 300% tavanına oturur. Pencerede
"Ekranda etkin boyut: %225" satırı görünür, böylece etiket ile gerçek arasında sürpriz kalmaz.
Kullanıcı sistem yazı ölçeği de dikkate alınmaya devam eder.

### 3.4 Ek düzeltmeler (istenen "şunlar da olsa güzel olurdu" başlıkları)

| Ekleme | Neden |
|---|---|
| **Canlı önizleme** | Radyo düğmesine dokununca tema anında uygulanır; "Uygula"ya basılmadan sonuç görülür, "Kapat" eski tercihe döndürür |
| **A− / A+ düğmeleri** | Kaydırıcıyı tam tutturmak zorunda kalmadan 10% adımlarla büyütme/küçültme |
| **Etkin boyut satırı** | Masaüstü sığdırmasında yazının gerçekte kaçta olduğunu gösterir |
| **Beyaz parlama yok** | Tema seçiliyken WebView zemini paletin zemin rengine boyanır (önce uygulama rengiydi) |
| **Koyu temada kaydırma çubuğu/form alanları** | `color-scheme: dark` |
| **Saf siyah uygulama zemini** | Kart zeminleri otomatik olarak bir tık açılır; siyah üstüne siyah kutu oluşmaz |
| **Açıklama metinleri** | Artık ne yaptığını doğru anlatıyor (site teması + uyumlu katman; resimlere filtre yok) |

---

## 4. Dokunulan dosyalar

| Dosya | Değişiklik |
|---|---|
| `app/src/main/assets/wiki/page-appearance.js` | V1 → **V2**; tema sözleşmesi + uyumlu renk katmanı + gözlemci + palet doğrulama |
| `.../wiki/WikiReadingAppearance.kt` | `WikiAppearancePalette`, `palette()`, `mix()`, `hex()`, `WikiReaderFit` (ölçek, telafi, etkin yüzde) |
| `.../wiki/WikiOriginalPagePolicy.kt` | `DESKTOP_VIEWPORT_SCRIPT` şablonu (`__WIDTH__`, `__SCALE__`) + `desktopViewportScript()` |
| `.../wiki/WikiViewportFit.kt` | **Yeni**: canlı WebView sığdırma denetleyicisi (zoomBy, jest ayrımı, yeniden deneme) |
| `.../wiki/WikiOriginalPage.kt` | Palet, önizleme, `setInitialScale`, viewport enjeksiyonu, ölçek dinleyicisi, döndürme düzeltmesi, sığdır isteği |
| `.../wiki/WikiPageEnhancements.kt` | V2 çağrısı + palet imzası + zemin rengini paletten alma |
| `.../wiki/WikiPageTools.kt` | "Sayfayı ekrana sığdır" satırı, canlı önizleme, A−/A+, etkin boyut |
| `res/values/strings.xml`, `res/values-en/strings.xml` | 5 yeni anahtar + 2 güncellenmiş açıklama (TR/EN birebir) |
| `scripts/tests/wiki-page-appearance.cjs` | 13 test (V2 sözleşmesi, restore, palet doğrulama, gözlemci) |
| `scripts/tests/wiki-original-viewport.cjs` | 6 test (şablon + sığdırma ölçeği, zoom kilidi yok) |
| `scripts/tests/Wiki*Checks.kt`, `WikiReadingAppearanceTest.kt` | Palet, sığdırma, yazı telafisi kontrolleri |

---

## 5. Doğrulama

| Grup | Sonuç |
|---|---:|
| Görünüm JavaScript'i (jsdom 26.1.0) | **13 / 13** |
| Masaüstü viewport JavaScript'i (jsdom) | **6 / 6** |
| DOM çeviri / kozmetik reklam (jsdom, regresyon) | **26 / 26** |
| Görünüm politikası (Kotlin, gerçek üretim dosyaları) | **12 / 12** |
| Sayfa/sekme/URL politikası + viewport metni (Kotlin) | **13 / 13** |
| **Toplam** | **70 / 70** |

Ek kontroller:

- `page-appearance.js` ve tüm test dosyaları `node --check` ile sözdizimi denetiminden geçti.
- Üretim Kotlin dosyaları **kotlinc 2.4.21 + Temurin JDK 25** ile derlendi (saf politika dosyaları + test harness).
- `WikiReadingAppearanceTest.kt` JUnit imzalarına karşı tip denetiminden geçti.
- `values/strings.xml` ve `values-en/strings.xml` XML olarak ayrıştırıldı; anahtar kümeleri birebir.
- Android/Compose dosyaları için: bağımlılık sınıf yolu olmadan **sözdizimi** taraması yapıldı (0 sözdizimi hatası).

### Doğrulanmayanlar — açıkça

- **Android/Compose tam derlemesi ve APK üretilmedi.** Bu ortamda Android SDK ve Gradle dağıtımı yok
  (önceki denemede Gradle indirmesi SSLHandshakeException ile durmuştu).
- **Gerçek cihaz/emülatör görsel kabul testi yapılmadı.**
- **Canlı Fandom/Wikipedia CSS'i ile piksel doğrulaması yapılmadı.** Hangi seçicinin hangi wikide ne kadar
  etkili olduğu yalnızca bilinen Fandom Desktop / Vector 2022 yapısına göre tasarlandı; jsdom testleri
  sözleşmeyi (sınıf, öznitelik, stil varlığı, geri yükleme) doğrular, renkleri değil.
- Fandom'ın gelecekte tema sınıfı sözleşmesini değiştirmesi hâlinde katman yine çalışır (değişkenler ve
  yüzeyler bizim), yalnızca "site kendi temasını uygulasın" kısmı zayıflar.

---

## 6. Yeniden çalıştırma

```bash
# JavaScript sözleşmeleri (jsdom 26.1.0)
NODE_PATH=.cache/wiki-tests/node_modules node --test \
  scripts/tests/wiki-dom-tools.cjs \
  scripts/tests/wiki-original-viewport.cjs \
  scripts/tests/wiki-page-appearance.cjs

# Kotlin politika kontrolleri (kotlinc 2.4.21)
kotlinc \
  app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/WikiReadingAppearance.kt \
  app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/WikiPageToolPolicy.kt \
  scripts/tests/WikiReadingAppearanceChecks.kt \
  -include-runtime -d .cache/wiki-checks/appearance.jar && java -jar .cache/wiki-checks/appearance.jar

kotlinc \
  app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/WikiOriginalPagePolicy.kt \
  app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomReaderBrowser.kt \
  scripts/tests/WikiOriginalPageChecks.kt \
  -include-runtime -d .cache/wiki-checks/page.jar && java -jar .cache/wiki-checks/page.jar
```

---

## 7. Riskler ve geri alma

- **Enjeksiyon riski:** Renk katmanı `!important` kullanır. Bir wikide kutu içi gradyan/rozet renkleri
  baskılanabilir. Telafi: SITE varsayılan ve tek dokunuşla geri dönüş; katman yalnızca LIGHT/DARK seçiliyken var.
- **Yazı telafisi:** 2.2x'e kadar büyütme, sabit genişlikli tablolarda yeniden akışa yol açabilir. Bu yüzden
  tavan 300 motor yüzdesi ve kullanıcı pencerede etkin değeri görüyor.
- **Geri alma:** `page-appearance.js` + `WikiReadingAppearance.kt` geri alınırsa tema eski (yalnızca sınıf
  değiştiren) davranışa döner; sığdırma için `WikiViewportFit` bağlantısı `WikiOriginalPage.kt`ten kaldırılmalı.
