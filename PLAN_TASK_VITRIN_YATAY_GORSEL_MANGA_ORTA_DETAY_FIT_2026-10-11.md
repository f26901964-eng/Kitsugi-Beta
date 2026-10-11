# PLAN / TASK — Vitrin (Hero) Yatay Mod Uyumu, Manga Orta Hizalama ve Detay Sayfası Yön Bazlı Görsel Seçimi

**Tarih:** 2026-10-11  
**Sürüm:** v2.4.250  
**Dal (branch):** `arena/f2e286c7-kitsugi-beta`  
**Durum:** Kod + dokümantasyon tamamlandı. **Cihaz testi kullanıcıda.**

> **REVİZYON (v2.4.250):** Kullanıcı geri bildirimi: *"Detay sayfası yatayda hep aynı, dikey moddayken de poster değil —
> 16:9 sinematik bantta kırpılmadan bu olacak bu; en kalitelisi otomatik seçilecek."*
> Buna göre **detay sayfası vitrini her iki yönde de 16:9 sinematik bant** olarak sabitlendi (Görev 8).

---

## 1. Sorunlar (Kullanıcı Bildirimi — Ekran Görüntüleri 1-4)

| # | Sorun | Ekran görüntüsü |
|---|-------|-----------------|
| 1 | Keşfet vitrini yatay modda çirkin: 16:9 kapak görseli çok ince şeride kırpılıyor, gradyanlarla kararmış görünüyor; telefon/tablet uyumu yok | 1 |
| 2 | Yatay modda manga (Berserk): poster sağa yaslanmış, **tam ortada** olması gerekiyor | 2 |
| 3 | Detay sayfası yatay modda en kaliteli **geniş** görselin kullanılması | 3 |
| 4 | Detay sayfası: her iki yönde de **16:9 sinematik bant, kırpılmadan**, en kalitelisi otomatik (poster vitrin paneli DEĞİL) | 3-4 |

---

## 2. Kök Nedenler

1. `KitsugiHeroSection`: yatay telefon bandı `(screenHeight*0.65).coerceIn(230,295)` dp → 2.5–3.1:1 şerit.
2. `heroImageAlignment`: sabit bias'lar (`-0.32f` / `-0.25f`) → görselin gerçek oranı bilinmediği için ya üstten kesiyor ya fazla yukarı kaydırıyor.
3. Scrim'ler: yatay sol gradyan `alpha=0.54f` + dikey gradyanın 3 koyu durağı üst üste → görsel çamurlu/karanlık.
4. `KitsugiHeroSection`: manga posterinin "tam görünür" katmanı `BiasAlignment(0.42f, -0.04f)` ile sağa yaslı.
5. `KitsugiDetailHero`: vitrin **sol panel** içinde olmasına rağmen kutu oranı `screenWidthDp/470dp` ile hesaplanıyordu (gerçek oran ~0.63, sanılan ~1.67) → yatay görsel seçilip dikey panele sığdırılmak için %65'i kırpılıyordu.
6. Görsel aday sırası ekran yönüne göre değil, yanlış kutu oranına göre belirleniyordu.
8. (v2.4.250) Dikey modda ve yatay görseli olmayan kayıtlarda vitrin **tam boy poster paneline** düşüyordu; vitrin tasarımı kayıttan kayıda değişiyordu. Kullanıcı bunun yerine **her durumda 16:9 sinematik bant** istedi.
7. `DetailHero` (kütüphane detayı) yalnızca posteri (`entry.imageUrl`) `Crop` ile gösteriyordu; galerideki yatay görseller hiç kullanılmıyordu.

---

## 3. Görevler (Tasks)

### [x] Görev 1 — Ortak oran/uyum yardımcıları (`ui/components/HeroArtworkAspect.kt`, YENİ)
- [x] `HeroArtworkFit { COVER, CONTAIN_WITH_FILL }` + `heroArtworkFit(boxAspect, imageAspect, tolerance = 1.38f)`
- [x] `heroBandHeightDp(screenHeightDp, isLandscape, isTablet, isTv)` — yatay telefon %74 (280–420), yatay tablet %62 (360–520), dikey telefon %55 (430–530), dikey tablet %46 (440–560), TV %68 (380–500)
- [x] `isWideHeroBox(boxAspect, minAspect = 1.15f)`

### [x] Görev 2 — Dinamik kırpma hizalaması (`heroImageAlignment`)
- [x] `imageAspect: Float? = null` parametresi eklendi (varsayılan `null` → eski davranış korunur)
- [x] `ratio = boxAspect / imageAspect`; `ratio > 1` → üstten yalnızca %5 (%2 poster) pay bırakılır, bias `[-0.36, 0.08]`; `ratio < 1` → yatayda tam orta
- [x] `KitsugiNsfwImage.onImageAspectResolved` geri bildirimi vitrinde kullanılmaya başlandı

### [x] Görev 3 — Keşfet vitrini (`KitsugiHeroSection.kt`)
- [x] Yatay band yükseklikleri `heroBandHeightDp` ile büyütüldü (16:9 görselin ~%86'sı görünür)
- [x] Dikey/sol gradyanlar yumuşatıldı (`0.14→0.10`, `0.38→0.28`, `0.78→0.70`, sol `0.54→0.34`)
- [x] Manga/dikey poster geniş bantta `Alignment.Center` + simetrik yatay boşluk + altta 96dp metin payı
- [x] Kapak yolu `heroImageAlignment(..., imageAspect = loadedImageAspect)` kullanıyor

### [x] Görev 4 — Detay vitrini (`KitsugiDetailHero.kt`)
- [x] Adaylar: `wideArtworkCandidates` (kalite sıralı: `artwork.backdropCandidates` → `nativeBackdropUrl` → galeri yatayları) + `tallArtworkCandidates` (YEDEK: posterler)
- [x] `BoxWithConstraints` ile gerçek panel ölçümü (ekran genişliği yerine)
- [x] **16:9 sinematik bant her iki yönde:** `bannerHeight = (maxWidth * 9/16).coerceAtMost(screenHeight * 0.62)` → kapak görseli **kırpılmadan**; başlık/meta bandın altındaki 220dp bilgi alanına akar
- [x] Kırpma yok: `heroArtworkFit(..., tolerance = HERO_BANNER_FIT_TOLERANCE = 1.20f)` ile `CONTAIN_WITH_FILL` (tam görünür + yumuşak dolgu)
- [x] Poster vitrin paneli olarak **kullanılmıyor**; yalnızca hiç yatay görsel yoksa bandın içinde kesilmeden gösteriliyor

### [x] Görev 5 — Kütüphane detay vitrini (`KitsugiDetailHeroSection.kt` + `EntryDetailLeftPanel.kt`)
- [x] `DetailHero`'ya `galleryItems: List<GalleryItem>` parametresi; `EntryDetailLeftPanel` iletiyor
- [x] Aynı kural: **her iki yönde 16:9 bant**, `imagePlan = galeri yatayları (çözünürlük sırası) + poster`
- [x] `onLoadingFailed` ile yedek aday zinciri, aynı ölçüm/kırpma kuralları

### [x] Görev 6 — Birim testleri (`HeroImageSelectionTest.kt`)
- [x] Dinamik hizalama testi (16:9 + 2.47:1 bant → bias ≈ -0.09; poster + dikey telefon → ≈ -0.08; poster + geniş bant → ≤ -0.30)
- [x] `heroArtworkFit` uyum testi (COVER/CONTAIN_WITH_FILL/oran bilinmiyor)
- [x] Bant yüksekliği testi (476dp yatay telefon → 352dp; tablet ≥ 480dp; dikey telefon 440dp)

### [x] Görev 7 — Ortam doğrulaması
- [x] Parantez/süslü parantez dengesi, import tamlığı ve üye adları kontrol edildi
- [x] GitHub Actions derleme doğrulaması: çalıştırıldı (v2.4.249 turu başarılı, v2.4.250 revizyonu yeniden doğrulanır)

### [ ] Görev 8 — Cihaz testi (KULLANICIDA)
- [ ] Detay sayfası **dikey**: 16:9 bant + altında başlık/meta/rozetler (poster paneli YOK)
- [ ] Detay sayfası **yatay**: aynı 16:9 bant, kenarlardan kesilmemiş
- [ ] Manga/Bangumi kayıtları: poster bandın içinde kesilmeden, ortada
- [ ] Yatay telefonda Keşfet vitrini (bant büyük, görsel çoğu görünür)
- [ ] Yatay telefonda manga vitrini (poster tam ortada)
- [ ] Oran uyuşmazlığında "tam görünür + yumuşak dolgu"
- [ ] Tablet yatay/dikey kontrolü

---

## 4. Değişen / Eklenen Dosyalar

| Dosya | Durum |
|-------|-------|
| `app/src/main/java/com/kitsugi/animelist/ui/components/HeroArtworkAspect.kt` | YENİ |
| `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiHeroSection.kt` | DEĞİŞTİ |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailHero.kt` | DEĞİŞTİ |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailHeroSection.kt` | DEĞİŞTİ |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/EntryDetailLeftPanel.kt` | DEĞİŞTİ |
| `app/src/test/java/com/kitsugi/animelist/ui/components/HeroImageSelectionTest.kt` | DEĞİŞTİ |
| `app/build.gradle.kts` (sürüm 2.4.250) | DEĞİŞTİ |
| `release_notes_v2.4.250.md`, `RELEASE_NOTES.md` | DEĞİŞTİ / YENİ |
| `VITRIN_YATAY_MANGA_DETAY_GORSEL_FIX_RAPORU_2026-10-11.md` | YENİ (rapor) |

---

## 5. Notlar / Riskler

- Bu ortamda **derleme yapılamadı** (JDK/Android SDK yok). Depoya push edilen `arena/f2e286c7-kitsugi-beta` dalında GitHub Actions "Android Derleme Doğrulama" iş akışı otomatik çalışıyor; sonuç ve APK artifact'i oradan alınabilir.
- **Bilinen sınırlama:** Yatay telefonda sol panel ~300dp iken 16:9 bant ~170dp yüksekliğinde olur. Görselin **tamamı** görünür, ama görsel alanı eski 470dp'lik panelden küçüktür — "geniş resmi kesmeden göster" gereksiniminin doğal sonucu.
- Yatay modda detay sayfasında bilgi alanı 220dp sabit ayrılır; çok uzun başlıklarda metin bandın alt kenarına doğru taşabilir (okunurluk gradyanla korunur).
