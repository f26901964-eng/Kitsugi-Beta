# PLAN/TASK — Vitrin Barı, 5 sn Otomatik Kaydırma ve Dikey Modda Poster (2026-10-21)

## Kullanıcı Şikayetleri (2 ekran görüntüsü ile)

1. **Vitrin barı içerik sayısını yanlış gösteriyor / "buga giriyor"** (1. resimde turuncu kutu).
2. **Vitrin kendi kendine 5 sn'de bir kaymalıydı, kaymıyor.**
3. **Dikey telefonda vitrin bozuk** (2. resim): yatay görsel üstte ince şerit, bandın geri kalanı
   boş siyah. İstek: ekran boyutuna bakarak yatay görsel sığmıyorsa **otomatik olarak kaliteli
   poster (dikey)** gösterilsin.

---

## Kök Neden Analizi

### 1) Bar sayısı
`HomeHeroPolicy.homeHeroIndicatorIndices(itemCount, currentPage, windowSize = 7)` kayan bir
pencereyle her zaman **en fazla 7 bar** çiziyordu. Tek kaynak modunda vitrinde 18'e kadar
(2 × 9 raf), TÜMÜ modunda çok daha fazla içerik olabildiği için bar toplam sayıyı hep 7
gösteriyordu; sayfa ilerledikçe pencere kaydığı için barların konumu da "bug" gibi görünüyordu.

### 2) Otomatik kaydırma
`KitsugiHeroSection` içindeki oto-kaydırma `LaunchedEffect`'inin anahtarları arasında
`pagerState.isScrollInProgress` ve `pagerState.settledPage` vardı. `animateScrollToPage`
başlar başlamaz `isScrollInProgress == true` oluyor → anahtar değiştiği için effect **iptal
ediliyor** → animasyon ilk karelerde ölüyor → sayaç baştan başlıyor. Net etki: vitrin hiç
kendi kendine kayamıyor (en fazla mikro titreşim).

### 3) Dikey mod
Dikey telefon `HeroVitrinMode.STACKED` kullanıyor: bant 430-530 dp, yatay backdrop üst şeritte,
metin altında. Bant en-boy oranı ≈0,78 (dar ve uzun) olduğu için 16:9 görsel şeride eziliyor ve
bandın altı kararılmış ambient üzerinde boş duruyordu. Eski politika posteri vitrinde **bilerek**
yasaklıyordu ("A poster is NOT a fallback even on a portrait phone").

---

## Yapılan Değişiklikler

### A) Bar birebir gösteriyor — `HomeHeroPolicy.kt`, `KitsugiHeroSection.kt`
- `homeHeroIndicatorIndices(itemCount)`: kayan pencere kaldırıldı; `(0 until itemCount)`.
  **Bar sayısı = gerçek sayfa sayısı.**
- Yeni: `HERO_INDICATOR_SLOT_DP = 34f`, `heroIndicatorSpacingDp(count)` (≤10→6 dp, ≤18→4 dp,
  üstü→3 dp), `heroIndicatorRowWidthDp(count, available)`: az içerikte eski doğal görünüm
  (34 dp yuva) aynen; çok içerikte satır kullanılabilir genişliği aşmaz, yuvalar `weight(1f)`
  ile eşit incelir.
- Aktif bar hâlâ `heroPageVisibility` (sürükleme ile sürekli) üzerinden; `settledPage`
  bağımlılığı kalktı.

### B) 5 sn otomatik kaydırma — `HomeHeroPolicy.kt`, `KitsugiHeroSection.kt`
- `HERO_AUTOPLAY_DELAY_MS = 5_000`, `HERO_AUTOPLAY_TICK_MS = 200` sabitleri.
- Efekt tek bir döngüye çevrildi; anahtarlar yalnızca **kararlı** girdiler:
  `(heroItemCount, isVisible)`. Pager/etkileşim durumu anahtar değil, döngü içinde okunuyor —
  animasyon artık kendi effect'i tarafından iptal edilemez.
- Sayaç yalnızca **5 sn kesintisiz boşta** kalınırsa dolar: dokunma / sürükleme / basma /
  TV odağı / kaydırma sürüyorsa veya kullanıcı sayfa değiştirmişse sayaç sıfırlanır.
- Yön politikası korundu (`homeHeroAutoDirection`): her seferde tek sayfa, kenarda ping-pong,
  son→ilk çok sayfalı sarma yok. Kullanıcı animasyonu yarıda yakalarsa `CancellationException`
  yutulup döngü devam eder (gerçek iptalde `ensureActive` ile yeniden fırlatılır).

### C) Dikeyde kaliteli poster — `HeroVitrinLayout.kt`, `HomeHeroPolicy.kt`,
   `HomeHeroArtworkLoader.kt`, `KitsugiHeroSection.kt`
- `HERO_POSTER_BAND_MAX_ASPECT = 1.0f`: bant kareden uzunsa (dikey telefon ≈0,75-0,85) yatay
  görsel "sığmıyor" sayılır → `HeroVitrinLayout.prefersPoster = true` (yalnızca STACKED/dikey
  telefon). Yatay/TV/tablet (OVERLAY) her zaman yatay backdrop kullanır. Yeni `bandAspect`
  hesabı eklendi; hiçbir ölçü değişmedi (bant yüksekliği/geometrisi aynı sözleşme).
- `homeHeroPosterUrl(item)`: kaynağın posteri `upgradeNativePosterUrl` ile en yüksek varyanta
  yükseltilir (TMDB→`/original`, Bangumi→`/pic/cover/l/`, AniList→`/cover/large/`,
  Kitsu→`/large`, MAL→orijinal path, Shikimori→`/original/`). Backdrop ile aynı görsel
  kimliğine sahipse (normalize identity — aynı görsel iki alanda) poster sayılmaz.
- `HomeHeroArtworkLoader.withHeroArtwork`: hazırlanan öğe, doğrulanmış backdrop'a ek olarak en
  kaliteli posteri taşır: önce resolver'ın puanlanmış adayları (TMDB/Fanart `original`),
  yoksa yükseltilmiş yerli poster. Ek ağ işi yok (resolver sonucu zaten ücretli değildi/önbellekli).
- Sayfa içi (`KitsugiHeroSection`): `prefersPoster && poster var` ise poster bandı **tam kaplar**
  (Crop, `heroImageAlignment` ile üst-yarı odak — yüzler kesilmez; gerçek oran decode olunca
  kırpma inceltilir), metin OVERLAY tarzı dikey scrim üzerinde + üstte durum çubuğu koyulaştırması.
  **Kendi kendini onarma:** poster yüklenemezse (`onLoadingFailed`) veya decode edilen oran
  dikey değilse (`onImageAspectResolved` + `isVerticalHomeHeroPosterAspect`) sayfa o öğe için
  backdrop şeridine (ambient + şerit) geri döner. Posteri olmayan öğelerde davranış eski STACKED.
- Cihaz yataya dönerse `layout` OVERLAY olur ve sayfalar otomatik backdrop'a geçer (kabul
  kapısı zaten her öğede doğrulanmış yatay backdrop şartını koruyor; poster yalnızca dikey
  telefonda **gösterim** değişikliği, kabul kuralı değil).

### Testler (birim)
- `HomeHeroPolicyTest`: pencere testi → `indicatorsAlwaysMatchTheRealItemCount` (0/1/2/7/18/70
  birebir), `indicatorRowFitsTheAvailableWidth...`, poster yükseltme (TMDB/Bangumi/AniList),
  backdrop-kimliği reddi, dikey boyut/oran doğrulamaları, `autoplayWaitsFiveSecondsBetweenPages`.
- `HeroVitrinLayoutTest`: `portraitPhoneBandsPreferPosterArtwork` (telefon dikeyinde
  `bandAspect < 1` + `prefersPoster`, yatay/TV/tablette değil). Sınıf dokümanı güncellendi.

---

## Doğrulama Durumu (testler kullanıcıda)

- Değişiklikler `arena/f1a642f9-kitsugi-beta` branch'ine commit/push edildi
  (`55d4ca1 Vitrin düzeltmeleri...`).
- CI (🏗️ Android Derleme Doğrulama, run `39518526805`) çalıştırıldı ancak kullanıcı testleri
  üstlendiği için hata günlüğü incelemesi yarıda bırakıldı — **kendi ortamında doğrula.**
  Bilinen ipucu: run kısa sürede bitti ve "git exit code 128" bildirimi vardı; derleme hatası mı
  altyapı mı teyit edilmedi. Elle gözden geçirmede bilinen derleme riski yok (hepsi aynı modül
  içi `internal` üyeler; yeni importlar: `currentCoroutineContext`, `ensureActive`).

## Manuel Test Listesi (önerilen)

1. Tek kaynak (örn. AniList/Bangumi): vitrindeki gerçek sayfa sayısını say → bar sayısı aynı;
   aktif bar sürüklerken yumuşakça taşınsın; barlar artık kaymasın.
2. Dokunmadan 5 sn bekle → tek sayfa avtomatik kaysın; kenara gelince yön dönsün.
   Parmağı basılı tut / yarıda sürükle → 5 sn, bırakılan andan itibaren yeniden saysın.
3. Dikey telefon: poster bandı tam kaplasın, başlık/logo okunur olsun, yüzler kesilmesin.
   Posteri olmayan veya yamuk (yatay etiketli) öğede şerit görünümüne dönsün.
4. Cihazı yataya çevir → yatay backdrop + overlay görünümü; TV/tablette değişiklik yok.
5. TÜMÜ kaynağı: bar sayısı çoksa barlar ince ama doğru sayıda; performans/tıklanabilirlik gözlemi.

## Not / Bilinen Sınırlama

- Bar sayısı içerik sınırsızken (TÜMÜ modu onlarca içerik üretebilir) çok ince barlar görülebilir;
  talep "birebir göster" olduğu için bilinçli tercih. İstenirse vitrin üst sınırı (örn. 10)
  sonra eklenebilir — bu, raf temsiliyeti tasarımını etkileyeceği için bu pakete dahil edilmedi.
