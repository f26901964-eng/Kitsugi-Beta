# Kitsugi-Beta v2.4.251 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🖼️ 1. Detay Sayfası Vitrin Görseli — Tam Kalite + Sayfa Açılmadan Önce Hazır

Kullanıcı geri bildirimi: *"detay sayfalarında hem dikey hem yatay da ... TMDB'den ya da Fanart
üzerinden ... bu resim tamamen görünmeli, en kaliteli hali seçilmeli otomatik, sayfa yüklenmeden
önce"*. Bu sürüm o isteğin karşılığıdır.

#### 1.1 Kök Neden — RGB_565 decode (görsel kalitesini düşüren asıl sebep)

Uygulamanın genel Coil `ImageLoader`'ı bellek tasarrufu için `allowRgb565(true)` ile kuruludur
(`KitsugiApplication`). Liste/grid kartlarında bu doğru tercihtir, ANCAK vitrin gibi ekranda tek
ve büyük duran görselde **görsel kalitesini düşürür**:

- `Bitmap.Config.RGB_565` kanal başına 5/6/5 bittir (ARGB_8888'de 8/8/8/8) ve alfa içermez.
  Gökyüzü, sakura, ışık geçişleri gibi **yumuşak gradyanlarda bantlaşma (banding)** ve renk
  kaybı oluşur. Anime key visual'ları neredeyse tamamen gradyan olduğu için hasar gözle
  görülür seviyededir.
- Ayrıca `AsyncImage`, istekte ölçü tanımlı değilse `SizeResolver.ORIGINAL` seçer; yani TMDB
  `/original` 4K kapaklarının tamamı decode edilirdi (~33 MB ARGB_8888).

**Çözüm — `HeroArtworkImagePolicy` (yeni):** Vitrin görselleri artık
`allowRgb565(false)` (tam 8-bit renk), ekrandan türetilmiş **sınırlı** ölçü (en fazla
2560×1440 px) ve sabit `memoryCacheKey` ile decode edilir. Gradyanlar pürüzsüz, bellek tavanı
korunur. Liste/grid kartları eskisi gibi RGB_565 ile devam eder.

#### 1.2 Kök Neden — Görsel planı sayfa açıldıktan SONRA çözülüyordu

`KitsugiDetailHero`, vitrin görsel planını kendi içinde `produceState` ile ağ çağrısı yaparak
çözüyordu. Sayfa ise bu çözüm bitmeden render ediliyordu → kullanıcı **önce düşük çözünürlüklü
kaynak görselini** (ör. Bangumi dikey kapağı) görüyor, sonra TMDB/Fanart görseli gelince
**değişim** oluyordu.

**Çözüm:** `ApiResultDetailViewModel.resolveHeroArtwork()` artık görsel planını
`_detailLoading = false` olmadan **önce** çözüyor (5 s zaman tavanı, iptal güvenli) ve en
kaliteli adayı `HeroArtworkImagePolicy.preload()` ile **decode ederek** önbelleğe koyuyor.
`KitsugiDetailHero` bu planı `preResolvedArtwork` olarak alıp **ilk kareden** kullanıyor.

#### 1.3 Yatay modda vitrin küçük kalıyordu

Yatay modda sol panel dar olduğu için 16:9 bant küçük kalıyordu. Artık yatayda bant bir taban
yüksekliğe (250dp) ve daha yüksek tavana (ekran yüksekliğinin %72'si) kavuşuyor. Görsel
banttan belirgin saparsa **KIRPILMIYOR**, "tam görünür + yumuşak dolgu" ile gösteriliyor.

#### 1.4 Tam görünürlük garantisi (her iki yönde)

`heroArtworkFit(..., HERO_BANNER_FIT_TOLERANCE = 1.20)` kuralı korunarak genişletildi:
16:9 görsel + 16:9 bant → `COVER` (kayıpsız); dikey poster veya ultra geniş afiş + 16:9 bant →
`CONTAIN_WITH_FILL` (kesilmeden, tamamı görünür).

### 🧪 2. Testler

`HeroImageSelectionTest`'e 3 yeni test:
- `hero target size scales with screen width and never decodes 4K into memory`
- `hero target height keeps the cinematic 16-9 band ratio`
- `hero fit never slices a vertical poster inside the cinematic band`

---

## 🇬🇧 English

### 🖼️ 1. Detail Page Hero Artwork — Full Quality + Resolved BEFORE the Page Renders

#### 1.1 Root cause — RGB_565 decode (the real image-quality killer)

The app-wide Coil `ImageLoader` is configured with `allowRgb565(true)` for memory savings
(`KitsugiApplication`). That is the right trade-off for list/grid cards, but for the hero — a
single, large, on-screen image — it **degrades visual quality**:

- `Bitmap.Config.RGB_565` is 5/6/5 bits per channel (vs 8/8/8/8 in ARGB_8888) with no alpha.
  Smooth gradients (sky, sakura, light falloff) **band** and lose colour. Anime key visuals are
  almost entirely gradients, so the damage is clearly visible.
- Additionally, `AsyncImage` falls back to `SizeResolver.ORIGINAL` when the request has no
  explicit size, so TMDB `/original` 4K art was decoded in full (~33 MB ARGB_8888).

**Fix — `HeroArtworkImagePolicy` (new):** hero artwork now decodes with
`allowRgb565(false)` (full 8-bit colour), a **bounded** size derived from the screen
(max 2560×1440 px) and a stable `memoryCacheKey`. Smooth gradients, bounded memory. List/grid
cards keep RGB_565.

#### 1.2 Root cause — the image plan was resolved AFTER the page rendered

`KitsugiDetailHero` resolved its artwork plan itself via `produceState` (a network call), while
the page rendered immediately. The user therefore saw the **low-resolution native source image
first** (e.g. a Bangumi vertical cover), then the TMDB/Fanart artwork **swapped in**.

**Fix:** `ApiResultDetailViewModel.resolveHeroArtwork()` now resolves the plan **before**
`_detailLoading = false` (5 s ceiling, cancellation-safe) and preloads/predecodes the best
candidate into the cache. `KitsugiDetailHero` consumes it via the new `preResolvedArtwork`
parameter and uses it on the very first frame.

#### 1.3 Landscape hero was too small

In landscape the narrow left panel produced a tiny 16:9 band. The band now has a floor
(250 dp) and a higher ceiling (72% of screen height) in landscape. When the artwork ratio
diverges from the band it is **never cropped** — "fully visible + soft fill" is used.

#### 1.4 Full-visibility guarantee (both orientations)

`heroArtworkFit(..., HERO_BANNER_FIT_TOLERANCE = 1.20)` is preserved and reinforced:
16:9 artwork + 16:9 band → `COVER` (lossless); vertical poster or ultra-wide key art + 16:9
band → `CONTAIN_WITH_FILL` (complete, uncropped).

### 🧪 2. Tests

Three new tests in `HeroImageSelectionTest`:
- `hero target size scales with screen width and never decodes 4K into memory`
- `hero target height keeps the cinematic 16-9 band ratio`
- `hero fit never slices a vertical poster inside the cinematic band`
