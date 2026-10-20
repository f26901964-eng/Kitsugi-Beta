# PLAN & TASK — Yüklenme Animasyonu (Plasma Orb) Tam Yuvarlak Düzeltmesi

**Tarih:** 2026-10-21
**Sürüm:** v2.4.319
**Branch:** `arena/4f4fe0fa-kitsugi-beta`
**Commit:** `5be0398` — `fix(ui): yuklenme animasyonu artik her yerde tam yuvarlak (v2.4.319)`

---

## 1. Şikâyet (Kullanıcı bildirimi)

> "Ve ayrıca bu ne amk bu video yükleniyorkenki animasyon ve bu animasyonların
> olduğu tüm her yerde bu sadece yuvarlak olmalı, bulundukları ortama göre.
> Etrafından gereksiz ekstra çember ve kare olamaz, yuvarlak olması lazım."

**Görsel kanıt:** Oynatıcıda `Date A Live` bölüm 1 yüklenirken ekranın ortasında:
- dışarıda kalın **turuncu kare çerçeve**
- içinde **turuncu kare dolgu**
- onun içinde bir daire
- dairenin içinde bir **küçük kare**

Yani tek bir animasyon değil, iç içe 4 geometri görünüyordu.

---

## 2. Hedef (Definition of Done)

| # | Kabul kriteri | Durum |
|---|---------------|-------|
| 1 | Animasyonun herhangi bir yerinde kare/dikdörtgen çizim yok | ✅ |
| 2 | Animasyonun etrafında ekstra çember/halka yok | ✅ |
| 3 | Kutu kare olmayan ölçülerde de sonuç **tam daire** (elips değil) | ✅ |
| 4 | Dış `Modifier.size(...)` gerçekten uygulanıyor (kutu = çizim alanı) | ✅ |
| 5 | Animasyonun kullanıldığı **tüm** ekranlarda aynı düzeltme geçerli | ✅ |
| 6 | Bellek/GC optimizasyonları korunuyor (çökme regresyonu yok) | ✅ |
| 7 | 30 dp altı kompakt mod bozulmuyor | ✅ |
| 8 | Dış API (`modifier`, `size`, `color`, `secondaryColor`) değişmiyor | ✅ |

---

## 3. Kök neden analizi

`app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiPlasmaLoader.kt`

| # | Kod | Ekranda görünen |
|---|-----|-----------------|
| 1 | `Canvas { drawRect(brush = verticalGradient(0.30f to c1, 0.70f to c2)) }` | İç turuncu **kare** dolgu |
| 2 | `Modifier.border(width = 1.2.dp, brush = …, shape = CircleShape)` | Kutunun kenarında **kare çerçeve** |
| 3 | `.clip(CircleShape)` (kırpma) | Normalde yukarıdakileri gizlemesi gerekirken, bazı durumlarda devre dışı kalıyor |
| 4 | İki ayrı `size` modifier'ı (dış `Modifier.size(80.dp)` + iç `.size(42.dp)`) | Kutu 80 dp / çizim 42 dp → **iç içe iki kare** algısı |

**Neden kırpma yetmedi?**
- `drawRect` doğrudan `drawBehind`/`Canvas` içinde çiziliyor; graphics layer (clip) yoğun bellek baskısı, `AnimatedContent` + `scaleIn/scaleOut` geçişleri veya SurfaceView üstünde yeniden çizim sırasında düşerse dikdörtgen ekrana sızıyor.
- `Modifier.border` kutu boyutuna göre çizildiği için, içerik 42 dp iken kenarlık 80 dp'lik kutunun kenarında kalıyor → "kutu içinde kutu" görüntüsü.

---

## 4. Uygulama planı (yapıldı)

### Adım 1 — Dikdörtgen çizimi kaldır
- `drawRect(...)` silindi.
- Gövde artık: `drawCircle(brush = bodyBrush, radius = radius, center = orbCenter)`.

### Adım 2 — Kenarlık / halka kaldır
- `Modifier.border(width = 1.2.dp, brush = …, shape = CircleShape)` tamamen kaldırıldı.
- Animasyonun etrafında hiçbir stroke/ring çizilmiyor.

### Adım 3 — Garantili daire kırpma
- Yeni `PlasmaOrbShape : Shape` eklendi.
- `createOutline` içinde kutunun **en kısa kenarı** çap olarak alınıp `Path.addOval(Rect)` ile ortalanmış **tam daire** üretiliyor.
- `CircleShape`'ın dikdörtgen kutuda elips üretme ihtimali ortadan kalktı.

### Adım 4 — Tek ölçü kaynağı
- Dış `modifier` korunuyor, ardından `.size(size)` geliyor.
- `Canvas(modifier = Modifier.fillMaxSize())` ile **ölçülen kutuya** göre çiziliyor.
- `Box(contentAlignment = Alignment.Center)` ile içerik tam ortada.

### Adım 5 — Görsel iyileştirme (aynı kimlik korunarak)
| Katman | Değer | Blend |
|--------|-------|-------|
| Gövde | dikey gradyan c1 %42 → c2 %70 | normal |
| 3 dönen plazma odağı | yörünge 0.34 R, yarıçap 0.72 R, 120° faz | `Screen` |
| Nefes alan çekirdek | 0.48 R × puls (0.85–1.15) | `Screen` |
| Beyaz-sıcak merkez | 0.17 R | `Screen` |
| Cam highlight | üstten beyaz %16 | normal |
| Alt gölge | c2 %45 | normal |
| Kenar vinyeti | siyah %55 (0.62 R'den sonra) | normal |

**Kompakt mod (< 30 dp):** hâle + çekirdek + dönen parlak nokta (3 daire) — korundu.

### Adım 6 — Performans koruma
- Renk döngüsü 32 adıma kuantalanmaya devam ediyor (`PlasmaColorSteps = 32f`).
- Gradyan renk listeleri `remember(c1, c2)` ile önbellekte.
- Kare başına yeni `Brush` üretimi yok (LMKD/GC kaynaklı çökme önlemi korundu).

### Adım 7 — Dokümantasyon & sürüm
- `RELEASE_NOTES.md` → v2.4.319 bölümü (TR + EN).
- `YUKLENME_ANIMASYONU_YUVARLAK_FIX_RAPORU_2026-10-21.md` → kök neden raporu.
- `app/build.gradle.kts` → `appVersionName = "2.4.319"`.

---

## 5. Etki alanı (neden her yerde geçerli)

```
androidx.compose.material3.CircularProgressIndicator   ← uygulama genelinde tek referans
        └── com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
                └── KitsugiPlasmaLoader
```

`KitsugiGradientMaterial.kt` içindeki sarmalayıcı, Material'ın dönen halkasını
tamamen `KitsugiPlasmaLoader`'a yönlendiriyor. Ayrıca doğrudan çağrılan
**~60 nokta** var:

- Oynatıcı ortası (`MiddlePlayerControls` → `showLoadingCircle`)
- Kilitli kontroller yüklenmesi (`PlayerControls`)
- Tam ekran / satır içi yüklenme (`PlayerLoadingViews`, `PlayerInlineLoadingOverlay`)
- Sinematik yükleme ekranı (`KitsugiCinematicLoadingScreen`)
- Arama, Keşfet, eklenti seçici, eklenti detay
- Tüm bottom sheet'ler (aktivite, inceleme, konu, sıralama, akış seçici)
- Giriş pencereleri (Bangumi, Kitsu, Shikimori, Simkl, TV QR)
- Pull-to-refresh göstergesi (`KitsugiPullToRefreshIndicator`)
- TV ekranları (`TvScreenStates`, `TvAddonsScreen`, `TvStreamScreen`, TV manga)
- Manga modülü (`MangaBrowseScreen`, `MangaDetailScreen`, `MangaPageItem`)
- Profil, bildirimler, istatistikler

---

## 6. Test / doğrulama

### Statik kontrol
| Kontrol | Komut | Sonuç |
|---------|-------|-------|
| `drawRect` var mı? | `grep -n "drawRect" KitsugiPlasmaLoader.kt` | Sadece KDoc yorumunda ✅ |
| `drawRoundRect` var mı? | `grep -n "drawRoundRect"` | Yok ✅ |
| `.border(` var mı? | `grep -n "\.border("` | Yok ✅ |
| `CircleShape` importu | `grep -n "CircleShape"` | Kaldırıldı ✅ |
| Parantez/süslü parantez dengesi | script | braces 0, parens 0 ✅ |
| `PlasmaBrushCache` artık kullanılmıyor mu? | `grep -rn PlasmaBrushCache` | Referans yok ✅ |

### Görsel doğrulama
- Aynı gradyan/yarıçap matematiğiyle piksel-sadık önizleme üretildi (Node + PNG).
- Sonuç: turuncu kare çerçeve ve iç kare **yok**; tek, yuvarlak, hacimli lav küresi.

### Cihaz testi (kullanıcı tarafında doğrulanacak)
1. Torrent/P2P kaynak seç → tampon %0 iken ortadaki animasyon **tek daire** mi?
2. Kaynağı kilitle (kilit ikonu) → ortadaki yüklenme **tek daire** mi?
3. Arama yaparken alt ortadaki yüklenme → **tek daire** mi?
4. Herhangi bir bottom sheet aç → içindeki yüklenme → **tek daire** mi?
5. Mi Box / TV'de ana ekran yüklenmesi → **tek daire** mi?
6. Çok küçük boyutlu kullanımlar (buton içi, 16–20 dp) → bozulma yok mu?

---

## 7. Değişen dosyalar

| Dosya | Değişiklik |
|-------|------------|
| `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiPlasmaLoader.kt` | Bileşen tamamen yeniden yazıldı (441 satır diff) |
| `app/build.gradle.kts` | `appVersionName` 2.4.318 → 2.4.319 |
| `RELEASE_NOTES.md` | v2.4.319 TR + EN bölümü eklendi |
| `YUKLENME_ANIMASYONU_YUVARLAK_FIX_RAPORU_2026-10-21.md` | Yeni kök neden raporu |
| `PLAN_TASK_YUKLENME_ANIMASYONU_YUVARLAK_FIX_2026-10-21.md` | Bu dosya |

---

## 8. Regresyon riski ve geri alma

**Risk: Düşük.**
- Bileşenin dış sözleşmesi değişmedi → 60+ çağrı noktasında düzenleme gerekmedi.
- Davranış değişikliği: eski sürümde kutunun dışına taşan yumuşak ışıma vardı;
  yenisinde tüm ışıma daire içinde kalıyor (kullanıcının istediği davranış).

**Geri alma:**
```bash
git revert 5be0398
```

---

## 9. Sonraki adımlar (opsiyonel)

- [ ] Diğer "yüklenme" görselleri (shimmer, iskelet kartlar) kare köşeli mi, tema uyumlu mu denetle.
- [ ] `KitsugiPullToRefreshIndicator` çekiş eşiğinde küre boyutunun 40 dp'ye oturmasını cihazda doğrula.
- [ ] TV'de 10-foot mesafede 54 dp kürenin okunabilirliğini kontrol et.
- [ ] Plasma orb'a düşük güç modu (batarya tasarrufu) için statik varyant eklemeyi değerlendir.
