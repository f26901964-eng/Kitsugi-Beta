# VİTRİN (HERO) YATAY/ DİKEY GÖRSEL UYUMU ve DETAY SAYFASI GENİŞ GÖRSEL MANTIĞI — DÜZELTME RAPORU

**Tarih:** 2026-10-11  
**Sürüm:** v2.4.250  
**Durum:** Kod tamamlandı (derleme GitHub Actions'ta doğrulandı; cihaz testi kullanıcıda)

> **REVİZYON (v2.4.250):** Kullanıcı geri bildirimi sonrası **detay sayfası vitrini her iki yönde de
> 16:9 sinematik bant** olacak şekilde sabitlendi; **poster artık vitrin paneli olarak kullanılmıyor.**
> Poster yalnızca hiç yatay görsel yoksa bandın içinde kesilmeden gösterilir. Bölüm 3.4 ve 3.5
> bu karara göre güncellenmiştir.

---

## 1. Bildirilen Sorunlar

1. **Keşfet vitrini — yatay modda çirkin görünüm (ekran görüntüsü 1 & 2):** 16:9 kapak görseli ekranın tam genişliğinde çok ince bir şeride (`~2.5–3.1:1`) kırpılıyor, üst üste binen gradyanlarla görsel karanlık/çamurlu hale geliyordu. Telefon/tablet ve yatay/dikey uyumu yoktu.
2. **Keşfet vitrini — yatay modda manga (ekran görüntüsü 2, Berserk):** Dikey poster sağa yaslanıyordu; "tam ortada olması" gerekiyordu.
3. **Detay sayfaları (ekran görüntüsü 3 & 4):** Yatay modda geniş (yatay) görselin **en kalitelisi**, dikey modda en kaliteli **posterin** otomatik kullanılması gerekiyordu. Mevcut kod ekran genişliğini (panel genişliği değil) kullanıp gerçek kutu oranını yanlış hesaplıyor, yatay görseli dikey bir panele zorla sığdırıp ortadan kırpıyordu.

---

## 2. Kök Nedenler

| # | Kök neden | Dosya |
|---|-----------|-------|
| 1 | Yatay telefon vitrin yüksekliği `(screenHeightDp * 0.65).coerceIn(230f, 295f)` → 2.5–3.1:1 şerit; 16:9 görselin yalnızca ~%60'ı görünüyordu | `KitsugiHeroSection.kt` |
| 2 | Kırpma hizalaması görselin **gerçek** en-boy oranını bilmeden sabit bias (`-0.32f`) kullanıyordu → ya üstten kesiyor ya gereğinden fazla yukarı kaydırıyordu | `KitsugiHeroSection.kt` |
| 3 | Yatay modda sol gradyan `alpha = 0.54f` + dikey gradyanın 3 koyu durağı üst üste binip görseli karartıyordu | `KitsugiHeroSection.kt` |
| 4 | Dikey posterin "tam görünür" katmanı `BiasAlignment(0.42f, -0.04f)` ile sağa yaslanıyordu | `KitsugiHeroSection.kt` |
| 5 | Detay vitrini kutu oranını `screenWidthDp / 470dp` ile hesaplıyordu; oysa vitrin yatay modda **sol panel** içinde (`~ ekran genişliğinin %28–38'i`). Gerçek kutu oranı ~0.63 iken kod ~1.67 sanıp yatay görseli seçiyor, dikey panele sığdırmak için görselin ~%65'ini kırpıyordu | `KitsugiDetailHero.kt` |
| 6 | Görsel planı ekran yönüne göre değil, yanlış hesaplanan kutu oranına göre sıralanıyordu (yatayda yatay görsel önceliği garanti değildi) | `KitsugiDetailHero.kt` |
| 7 | Kütüphane (MediaEntry) detay vitrini yalnızca `entry.imageUrl` posteri ile `ContentScale.Crop` kullanıyordu; galerideki yatay görseller hiç değerlendirilmiyordu | `KitsugiDetailHeroSection.kt` |

---

## 3. Yapılan Düzeltmeler

### 3.1 Yeni ortak yardımcılar — `ui/components/HeroArtworkAspect.kt`

- `HeroArtworkFit { COVER, CONTAIN_WITH_FILL }` ve `heroArtworkFit(boxAspect, imageAspect, tolerance = 1.38f)`:
  görsel ile kutu oranı çok farklıysa (ör. yatay görsel + dikey panel, ya da dikey poster + geniş bant) **kırpmak yerine** "tam görünür + yumuşak dolgu" seçilir.
- `heroBandHeightDp(screenHeightDp, isLandscape, isTablet, isTv)`: vitrin bandı yüksekliği tek bir test edilebilir fonksiyonda toplandı.
  - yatay telefon: `%74` (280–420dp) — eskiden `%65` (230–295dp)
  - yatay tablet: `%62` (360–520dp)
  - dikey telefon: `%55` (430–530dp), dikey tablet: `%46` (440–560dp), TV: `%68` (380–500dp)
- `isWideHeroBox(boxAspect, minAspect)`.

### 3.2 `heroImageAlignment(...)` — dinamik, orana duyarlı kırpma

`heroImageAlignment(chosenIsPoster, heroAspect, imageAspect: Float? = null)`:

- Görselin **gerçek** oranı biliniyorsa `ratio = boxAspect / imageAspect` hesaplanır:
  - `ratio > 1` (görsel alttan/üstten kırpılıyor): görünen yükseklik oranı `1/ratio`; üstte yalnızca `%5` (posterlerde `%2`) pay bırakılır ve bias buna göre üretilir → **başlar/yüzler kesilmez, görsel gereğinden fazla yukarı kaymaz.**
  - `ratio < 1` (görsel yanlardan kırpılıyor): yatayda tam orta; posterlerde hafif üst odak.
- Oran henüz bilinmiyorsa (görsel yüklenmeden önce) eski kanıtlanmış davranış korunur; görsel yüklenince hizalama yumuşak şekilde düzelir.

`KitsugiNsfwImage` zaten `onImageAspectResolved` ile gerçek oranı bildiriyor — bu geri bildirim artık vitrinde de kullanılıyor.

### 3.3 `KitsugiHeroSection.kt` (Keşfet vitrini)

- Bant yükseklikleri `heroBandHeightDp(...)` üzerinden: yatayda çok daha ferah (16:9 görselin ~%86'sı görünür).
- Dikey gradyan ve yatay sol gradyan yumuşatıldı (`0.54 → 0.34`, alt duraklar `0.78 → 0.70`, `0.38 → 0.28`); görsel artık net ve canlı.
- **Manga/poster geniş bantta tam ortada:** ön katman `Alignment.Center` (eskiden yatayda `0.42f` sağa yaslı), üstte/altta simetrik boşluk + altta başlık/meta için 96dp pay → poster metnin arkasına gömülmüyor.
- Kapak (Crop) yolu artık `heroImageAlignment(..., imageAspect = loadedImageAspect)` kullanıyor.

### 3.4 `KitsugiDetailHero.kt` (API/kaynak detay vitrini) — v2.4.250 REVİZYONU

- **Vitrin her iki yönde de aynı: 16:9 sinematik bant.** Yön bazlı dallanma kaldırıldı; ekran yönü artık görsel seçimini etkilemiyor. Poster vitrin paneli olarak **kullanılmıyor**.
- **Aday sırası kalite sıralı:**
  - `wideArtworkCandidates` = `artwork.backdropCandidates` (TMDB/Fanart.tv kalite sıralaması: 4K > 2K > 1080p, yazısız, oylu) → `nativeBackdropUrl` → galeri yatay görselleri (çözünürlük sırasına göre)
  - `tallArtworkCandidates` (yalnızca YEDEK) = `artwork.posterCandidates` → `nativePosterUrl` → galeri dikey görselleri
  - `imagePlan = wide + tall` → en kaliteli yatay görsel her zaman ilk sırada.
- **Boyut:** görsel alanı `bannerHeight = (maxWidth * 9/16).coerceAtMost(screenHeightDp * 0.62)`; vitrin kutusu `bannerHeight + 220dp` (bilgi alanı). Bant genişlikten türediği için telefon, tablet, TV, dikey ve yatayda aynı görünüm; ekran yüksekliği sınırı TV/büyük tablette taşmayı engeller.
- **Kırpma yok:** `heroArtworkFit(..., tolerance = HERO_BANNER_FIT_TOLERANCE = 1.20f)` — yatay görsel banttan %20'den fazla saparsa `CONTAIN_WITH_FILL` (tam görünür + yumuşak dolgu). Böylece ultra geniş afişler ve dikey posterler de **kesilmeden** görünür.
- **Gerçek ölçüm:** kutu oranı ekran genişliğinden değil, `BoxWithConstraints` ile **gerçek panel genişliğinden** türetiliyor (eski hata: sol panel içindeki vitrin ekran oranı sanılıyordu).
- Başlık/logo, format-sayaç satırı, yayın bilgisi ve rozetler bandın altındaki bilgi alanına akar.

### 3.5 `KitsugiDetailHeroSection.kt` (`DetailHero` — kütüphane detayı) — v2.4.250 REVİZYONU

- Yeni parametre: `galleryItems: List<GalleryItem>` (çağıran: `EntryDetailLeftPanel` ile iletildi).
- **Aynı kural:** vitrin her iki yönde de 16:9 sinematik bant; `imagePlan = wideGalleryUrls (çözünürlük sırası) + posterUrl`. Poster yalnızca hiç yatay görsel yoksa bandın içinde kesilmeden gösterilir.
- Bant `(maxWidth * 9/16).coerceAtMost(screenHeight * 0.62)` + 220dp bilgi alanı; `HERO_BANNER_FIT_TOLERANCE` ile kırpma yerine "tam görünür + dolgu" (tıklanabilir katman korunur), `onLoadingFailed` ile yedek aday zinciri.

### 3.6 Testler — `HeroImageSelectionTest.kt`

Eklenen testler:

1. `dynamic crop alignment protects the top of the artwork and centres it horizontally`
   — 16:9 görsel + 2.47:1 bant → yatay bias `0`, dikey bias `≈ -0.09` (üstten yalnızca %5);
   poster + dikey telefon → `≈ -0.08`; poster + çok geniş bant → `≤ -0.30` (üst sınır).
2. `aspect mismatch falls back to contain with fill so wide art is never sliced`
   — eşleşen/küçük farklarda `COVER`, büyük farklarda `CONTAIN_WITH_FILL`, oran bilinmiyorken `COVER`.
3. `landscape band is tall enough to show wide artwork almost uncropped`
   — 476dp yatay telefon → `352dp`; tablet ≥ `480dp`; dikey telefon → `440dp`.

Mevcut testler (kaynak önceliği, Bangumi kapak reddi, TMDB/Fanart kalite sıralaması) değişmeden geçmeye devam eder — `heroImageAlignment`'ın `imageAspect` parametresi varsayılan `null` ile eski davranışı korur.

---

## 4. Değişen / Eklenen Dosyalar

1. `app/src/main/java/com/kitsugi/animelist/ui/components/HeroArtworkAspect.kt` *(Yeni)*
2. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiHeroSection.kt`
3. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailHero.kt`
4. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailHeroSection.kt`
5. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/EntryDetailLeftPanel.kt`
6. `app/src/test/java/com/kitsugi/animelist/ui/components/HeroImageSelectionTest.kt`
7. `app/build.gradle.kts` (sürüm `2.4.248 → 2.4.250`)
8. `release_notes_v2.4.250.md`, `RELEASE_NOTES.md`

---

## 5. Doğrulama ve Kalan Riskler

- **Derleme:** GitHub Actions "Android Derleme Doğrulama" iş akışı `arena/f2e286c7-kitsugi-beta` dalında çalıştı ve **başarılı** oldu (v2.4.249 turu). v2.4.250 revizyonu aynı iş akışıyla yeniden doğrulanır.
- Cihazda kontrol edilmesi gerekenler:
  1. Detay sayfası — **dikey mod**: 16:9 bant + altında başlık/meta/rozetler (poster paneli yok).
  2. Detay sayfası — **yatay mod**: aynı 16:9 bant, kenarlarından hiç kesilmemiş.
  3. Manga/Bangumi gibi yatay görseli olmayan kayıtlar: poster bandın içinde **kesilmeden** ve ortada.
  4. Keşfet vitrini yatay (bant büyük, görsel çoğu görünür) ve manga vitrini (poster tam ortada).
- **Bilinen sınırlama:** Panel genişliği küçük olduğunda (yatay telefonda sol panel ~300dp) 16:9 bant ~170dp yüksekliğinde olur. Bant her zaman genişlikten türediği için görsel oranı korunur; alan eski 470dp panelden küçüktür — "kırpılmadan göster" gereksiniminin doğal sonucu.
- **Not:** Detay vitrini artık yön fark etmeksizin bant olduğu için, çok geniş (ör. 21:9) bir kapak görseli dikey modda da tamamen görünür ama bantta üst/alt ince dolgu şeritleri oluşabilir (bilinçli tercih: kırpmama).
