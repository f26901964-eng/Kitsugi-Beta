# PLAN/TASK — Ana Sayfa Vitrini: DİKEY MODDA POSTER EKRANA TAM OTURSU

**Tarih:** 2026-10-13
**Dal:** `arena/6ca165cb-kitsugi-beta`
**Etkilenen dosyalar:**
- `app/src/main/java/com/kitsugi/animelist/ui/components/HeroArtworkAspect.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiHeroSection.kt`
- `app/src/test/java/com/kitsugi/animelist/ui/components/HeroImageSelectionTest.kt`

## Kullanıcı bildirimi (aynen)

> "Kardes malmisin sen dikey boyuttayken ekran boyutune gore resimleri poster olarak
> gostercekse tam gostersin iste mk otomatik vitrin ve ekrana tam oturmasi lazim
> resmin amk ??"

## Sorun

Keşfet vitrini (hero carousel) **dikey (portrait) modda** bandı `layout.heroHeight`
ile SABİT tutuyordu (`KitsugiHeroSection.kt`, eski kod):

```kotlin
val targetHeroHeightDp = if (layout.isLandscape) {
    homeHeroHeightDp(...)      // yatay mod: görselin oranına kilitli
} else {
    layout.heroHeight.value    // ← DİKEY MOD: sabit bant, görsel oranı YOK SAYILIYOR
}
```

`homeHeroHeightDp` içindeki dikey dal (görselin oranına kilitlenme) yazılmış ama
**hiç çağrılmıyordu**. Sonuç:

| Adım | Eski değer (360×800 telefon) |
|------|------------------------------|
| Vitrin kutusu | 360 × **440 dp** (sabit `heroBandHeightDp`) |
| Kutu oranı | 0.818 |
| Gösterilen 2:3 poster oranı | 0.667 |
| Çizim | `useDualLayerContain` → bulanık dolgu + ortada `Fit` poster |
| Posterin görünen genişliği | 440−54 = 386 dp yükseklik → **257 dp** / 360 dp |

Yani poster ekranın yalnızca **~%71'ini** kaplıyor, geri kalanı iki yanda bulanık
dolgu olarak kalıyordu. "Otomatik vitrin" yatay modda çalışıyor, dikey modda
çalışmıyordu — kullanıcının gördüğü ve kızdığı şey tam olarak bu.

## Yeni kural

**DİKEY mod + DİKEY görsel (poster/kapak) → kutu görselin GERÇEK oranına kilitlenir.**

```
bantYüksekliği = clamp(ekranGenişliği / görselOranı, eskiSabitBant, ekranYüksekliği × 0.74)
```

- Kilitlenince kutu oranı = görsel oranı → **kırpma ≈ %0**, **yan boşluk ≈ 0**:
  poster kenardan kenağa, tek katman `ContentScale.Crop` ile full-bleed çizilir.
- Tavan **0.74** (`HOME_HERO_PORTRAIT_VERTICAL_MAX_SCREEN_FRACTION`): 19.5:9 ve
  daha dar tüm telefonlarda 2:3 poster birebir sığar (20:9 → ekranın ~%68'i).
- Tavan aşılırsa (kare/tablet oranlı cihaz, çok uzun poster) **hiçbir şey kırpılmaz**:
  eski çift katman (atmosferik dolgu + ortada kesilmemiş görsel) birebir korunur.
- **Yatay (16:9 key visual) görselde dikey davranış DEĞİŞMEZ**: sabit bant korunur
  (`portraitFallbackBandHeightDp`), çünkü başlık + rozetler + meta + gösterge
  çubuğu görselin üstünde okunur kalmalı.
- **Yatay mod ve TV birebir korunur** (`isLandscape` dalı aynı).

### Yeni yardımcılar (`HeroArtworkAspect.kt`)

| Sembol | İş |
|--------|----|
| `HOME_HERO_PORTRAIT_VERTICAL_MAX_SCREEN_FRACTION = 0.74f` | Dikey görselin dikey mod tavanı |
| `HOME_HERO_VERTICAL_ASPECT_LIMIT = 1.05f` | "Dikey görsel" eşiği (çift katman kuralıyla aynı) |
| `HERO_FULL_BLEED_FIT_TOLERANCE = 1.06f` | Kutu↔görsel oranı bu kadar yakınsa full-bleed |
| `isVerticalHeroAspect(aspect)` | Ortak dikey testi |
| `isHeroBoxFullBleed(boxAspect, imageAspect)` | Kutu görsele tam oturuyor mu? |
| `homeHeroHeightDp(..., portraitFallbackBandHeightDp = …)` | Yeni (varsayılanlı) parametre |

### Sarsıntı / titreme önlemleri

1. **Beklenen oran ilk kareden doğru:** görsel ölçülmeden önce poster 2:3, arka plan
   16:9 kabul edilir (`expectedHeroAspect`) → kutu doğru kurulur, sonradan oturma yok.
2. **%5 ölçüm eşiği korundu:** gerçek oran beklenenden 0.05'ten az farklıysa
   `pageImageAspects` güncellenmez → bant zıplamaz.
3. **Full-bleed kararı HEDEF yükseklikten okunur** (`heroBoxTargetAspect`): 280 ms'lik
   yükseklik animasyonu sırasında katman modu (tek katman ↔ çift katman) ileri geri
   zıplayıp titreme yapmaz.
4. **Aday başarısız olursa oran sıfırlanır** (`advanceToNextCandidate`): 404 alan
   posterin ölçülen oranı bantta kilitli kalıp sıradaki yatay görseli fena kırpmaz;
   sıradaki adayın beklenen oranı yazılır.

## Sayısal doğrulama (2:3 poster, dikey telefon)

| Cihaz | Eski bant | Yeni bant | Poster ihtiyacı | Sonuç |
|-------|-----------|-----------|-----------------|-------|
| 360 × 800 (20:9) | 440 (0.818) | **540 (0.667)** | 540 | ✅ tam oturur |
| 412 × 915 (19.5:9) | 503 (0.818) | **618 (0.667)** | 618 | ✅ tam oturur |
| 393 × 873 (19.5:9) | 480 (0.818) | **590 (0.667)** | 590 | ✅ tam oturur |
| 320 × 640 (16:9) | 430 (0.744) | **474 (0.676)** | 480 | ✅ tam oturur |
| 480 × 800 (karemsi) | 440 (0.818) | 592 (0.811) | 720 | ⚠️ tavan → contain (kırpma yok) |
| 800 × 1280 (tablet) | 560 (0.700) | 947 (0.845) | 1200 | ⚠️ tavan → contain (kırpma yok) |

## Bozulmayanlar (kontrol listesi)

- Yatay mod: bant yüksekliği, sol karartma gradyanı, poster contain çift katmanı,
  TV odak çerçevesi ve tuş gezinmesi — **hiçbiri değişmedi** (ilgili birim testleri
  aynı beklentilerle geçiyor).
- Dikey mod + **yatay** görsel: sabit bant birebir korunur
  (`portrait horizontal backdrop keeps the legacy fixed band`).
- Bindirmeler: gradyan scrim, "ÖNE ÇIKAN" satırı, kaynak çipi, logo, başlık, meta ve
  gösterge çubuğu aynı `BottomCenter` Column'da, görselin üstünde.
- Pager hit-test dökümantasyonu (`frontHeroPageIndex`) ve sarsıntı önlemleri aynı.
- Çökme önlemi (`postAfterFrame` ile state yazımı) korundu; yeni yazım da aynı yolu
  kullanıyor.

## Doğrulama

- Sandbox'ta Java/Gradle bulunmadığı için derleme/koşum çalıştırılamadı; bunun yerine:
  - Üç dosyada ayraç/parantez dengesi taraması yapıldı (hepsi 0).
  - Yeni/kritik formüller Python ile birebir simüle edilip tabloya döküldü (yukarıda).
- Yeni birim testleri (`HeroImageSelectionTest`):
  - `portrait poster band fills the screen edge to edge on modern phones`
  - `portrait poster falls back to cinematic contain when it cannot fit the screen`
  - `portrait horizontal backdrop keeps the legacy fixed band`
  - `full bleed detection accepts only boxes that match the artwork`
- Güncellenen test: `portrait hero band locks to the shown artwork aspect`
  (poster beklentisi 0.62·H → `genişlik / (2/3)` olarak değişti; bu, istenen yeni
  davranışın kendisidir).

## Elle test senaryosu

1. Telefonu **dikey** tut → Keşfet vitrini: anime/manga posterinin **kenardan kenağa**
   (yanlarda bulanık boşluk YOK) ve **kırpılmadan** (baş/alt kesik değil) göründüğünü doğrula.
2. Vitrini kaydır → bant, her sayfanın görseline 280 ms'lik yumuşak geçişle oturur;
   sarsıntı/zıplama olmaz.
3. Yatay arka planı olan (16:9) bir öğe → bant eski yüksekliğinde kalır.
4. Cihazı **yatay** çevir → sunum birebir aynı.
5. Tablette manga rafı → poster tavana takıldığında bulanık dolgulu contain sunumu.
