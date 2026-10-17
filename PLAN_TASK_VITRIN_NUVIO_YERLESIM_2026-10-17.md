# Plan / Task — Keşfet Vitrini: Nuvio Tarzı Yeniden Yerleşim

**Tarih:** 17 Ekim 2026
**Proje:** Kitsugi-Beta
**Çalışma dalı:** `arena/d0199659-kitsugi-beta`
**Sürüm:** v2.4.301
**Durum:** Kod değişiklikleri tamamlandı. Bu ortamda JDK/Gradle bulunmadığı için tam
Android derlemesi ve cihaz testi **yapılamadı**; doğrulama kaynak incelemesi ve
yapısal kontrollerle sınırlıdır (bkz. §5).

---

## 1. Bildirilen sorun

Kullanıcı ekran görüntüleriyle Keşfet ekranındaki vitrinin bozuk göründüğünü
bildirdi: "bu vitrin normalde nasıl görünmesi gerekiyorsa öyle yap ... gerekirse
tıpatıp nuvio açık kaynak ya tv yada mobil sürümün".

Beş ekran görüntüsünde görülen gerçek hatalar:

| # | Kaynak | Görülen bozulma |
|---|--------|-----------------|
| 1 | Shikimori (Naruto) | Keskin görsel ortada küçülmüş, **yanlarda boş kutu** var; altında büyük boş alan |
| 2 | AniList (Attack on Titan) | Görsel ince bir şeride düşmüş, gerisini **bulanık olmayan hayalet kopya** dolduruyor |
| 3 | Shikimori (Dandadan) | Görsel üstten kesilmiş, başlık/logo görselin üstüne biniyor |
| 4 | Simkl (Steins;Gate) | Aynı: üstten kesik + başlık görselin üstünde |
| 5 | AniList (Black Clover) | Aynı: üstten kesik + başlık görselin üstünde |

## 2. Kök neden (analiz)

`KitsugiHeroSection.kt` içinde vitrin görseli **iki** katmanla çiziliyordu:

```kotlin
// 1) düşük opaklıklık "arka dolgu"
KitsugiNsfwImage(..., contentScale = ContentScale.Crop,
                 modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.28f })
// 2) keskin katman
KitsugiNsfwImage(..., contentScale = ContentScale.Fit,
                 modifier = Modifier.fillMaxSize().padding(bottom = 174.dp))
```

Sorun iki ayrı yerleşim hatasından kaynaklanıyordu:

1. **Keskin katman `Fit` ile çok geniş bir kutuya sığdırılıyordu.**
   Dikey telefonda kutu ≈ 2.1 oranlıydı (ekran genişliği × bant−174 dp). Kaynak
   görsellerin gerçek oranları birbirinden çok farklı:
   - 16:9 (1.78) görsel → **yükseklikten sıkışır**, yanlarda boş kutu bırakır (1. ekran görüntüsü),
   - 3:1 banner → **ince şeride düşer**, gerisi hayalet kopyayla dolar (2. ekran görüntüsü).
2. **Metin bloğu kendi yuvasına sığmıyordu.** Metin bloğu ≈190 dp, ayrılan yer
   174 dp idi; bu yüzden başlık/logo görselin üstüne biniyordu (3–5. ekran görüntüleri).

Ayrıca v2.4.300 ile "bulanık arka dolgu" yerine **bulanık olmayan** düşük opaklıklı
kopya konmuştu (`PLAN_TASK_VITRIN_V2.md`'deki bulanık dolgu kaybolmuş), bu yüzden
arka plan "ikinci bir görsel" gibi okunuyordu.

## 3. Referans: açık kaynak Nuvio

İstenen doğrultuda Nuvio'nun gerçek kaynak kodu incelendi:

- **NuvioMobile** — `composeApp/src/commonMain/kotlin/com/nuvio/app/features/home/components/HomeHeroSection.kt`
  - Tek, tam kaplayan `ContentScale.Crop` backdrop (`Alignment.Center` / tablet'te `TopCenter`).
  - Bandın tamamında dikey gradyan scrim (0.02 → 0.12 → 0.34 → 0.78).
  - Altta `bottomFadeHeight` (mobilde 220 dp) yüksekliğinde saydamdan arka plana şerit.
  - İçerik bloğu bandın altında; logo `aspectRatio(2.6f)` + `ContentScale.Fit` ile.
  - Gösterge (dot) satırı içerik bloğunun içinde; aktif olan uzatılıyor.
- **NuvioTV** — `app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeHero.kt`
  - `ContentScale.Crop` backdrop (`Alignment.TopEnd`), **yatay** sol gradyanı +
    alttaki dikey şerit, sol-altta başlık bloğu.

Ortak ilke: **görsel kutuya tamamen kaplar (Crop), kutu oranı cihazdan gelir,
metin gradyan scim'in üstündedir.** "Fit" ve yarım kutu yoktur.

## 4. Yapılan değişiklikler

### 4.1 Yeni yerleşim politikası — `ui/components/HeroVitrinLayout.kt` (YENİ)

Saf, test edilebilir politika katmanı:

- `HeroVitrinMode.STACKED` — **dikey telefon**: üstte oranı sabit **görsel şeridi**,
  altta metin için ayrılmış sabit yuva. Görsel asla yüklemez, metin asla görselin
  üstüne binmez.
- `HeroVitrinMode.OVERLAY` — **yatay / TV / tablet**: görsel bandın tamamını kaplar,
  metin Nuvio TV'deki gibi gradyan scim'in üstüne bindirilir.
- `heroTextZoneHeightDp(...)` — metin bloğunun ihtiyaç duyduğu sabit yükseklik
  (kaynak rozeti + başlık/logo + alt başlık + meta + aralıklar + alt boşluk).
  `KitsugiHeroSection` içindeki yuvalarla **birebir aynı** değerleri kullanır.
- `heroVitrinLayout(...)` — bant yüksekliği, görsel şeridi yüksekliği ve metin
  yuvası **yalnızca cihaz geometrisinden** türetilir. Görselin oranı, decode durumu,
  sayfa değişimi veya kaynak tamamlanması hiçbir ölçüyü değiştiremez.

### 4.2 `ui/components/KitsugiHeroSection.kt`

- **Tek keskin katman, `ContentScale.Crop`, `Alignment.TopCenter`.** Görsel kutuya
  tamamen kaplar; `Fit` ve 174 dp'lik alt boşluk kaldırıldı.
- **Bulanık ambient katman** (yalnızca `STACKED` modunda): aynı görselin bitmap
  seviyesinde bulanıklaştırılmış kopyası bandın tamamını kaplar. Keskin görselin
  altında kalan alan "kasıtlı ortam" gibi okunur; boş kutu veya hayalet kopya yoktur.
  (Coil 3.5'te disk cache anahtarı yalnızca URL'dir → **ek indirme yapılmaz**.)
- **Nuvio scrim'leri:** dikey modda görsel şeridinin alt kenarını metin yuvasına
  yumuşak geçiştiren dikey gradyan + durum çubuğu için üst şerit; yatay/TV'de
  Nuvio TV'nin sol yatay gradyanı + alt dikey şerit.
- Metin bloğu, kaynak rozeti, logo yuvası, alt başlık ve meta satırları **olduğu
  gibi** korundu; sadece artık ayrılmış yuvaya oturuyor.

### 4.3 `ui/components/KitsugiNsfwImage.kt`

- Yeni **`ambientBlur`** parametresi: dekoratif arka plan katmanı için bitmap
  (Coil `BlurTransformation`) bulanıklığı. `Modifier.blur` / RenderEffect
  kullanılmaz → repo'da bilinen RenderThread SIGSEGV riski taşınmaz.
- NSFW bitmap bulanıklığı ile birleşir; daha büyük yarıçap seçilir.
- Ambient istek küçük çözünürlükte (960×540) decode edilir.
- Parametre listenin **sonuna** eklendi → mevcut konumsal çağrılar etkilenmez.

### 4.4 `ui/components/KitsugiShimmer.kt`

İskelet (`KitsugiShimmerHeroSection`) artık gerçek vitrinle **aynı** yerleşim
politikasını kullanıyor: dikey telefonda üstte görsel şeridi, altta metin
placeholder'ları. Yükleme bitince görselin yerine girerken zıplama olmaz.

### 4.5 Testler

- **YENİ:** `app/src/test/java/com/kitsugi/animelist/ui/components/HeroVitrinLayoutTest.kt`
  (6 test): mod seçimi, şerit + metin yuvasının bandı tamamen doldurması, ölçülerin
  görselden bağımsızlığı, metin yuvasının yuvaları aşması, yazı ölçeği etkisi,
  şerit oranının makul aralıkta kalması.
- Mevcut `HomeHeroPolicyTest` / `HeroImageSelectionTest` değişmeden geçer:
  `heroBandHeightDp` sözleşmesi korunmuştur.

## 5. Doğrulama durumu

| Kontrol | Sonuç |
|---------|-------|
| Delimiter/şablon denetimi (5 değişen dosya) | ✅ dengeli |
| `KitsugiNsfwImage` konumsal çağrı taraması (67 çağrı) | ✅ parametre sırası bozulmadı |
| Coil 3.5 disk-cache anahtarı doğrulaması (GitHub kaynak kodu) | ✅ yalnızca URL → ek indirme yok |
| JUnit birim testleri | ⚠️ **çalıştırılamadı** (ortamda JDK/Gradle yok) |
| Tam Android derlemesi | ⚠️ **çalıştırılamadı** (JDK + Gradle dağıtımı ağ kısıtlı) |
| Cihazda görsel doğrulama | ⚠️ **yapılamadı** |

**Ortam engeli:** Bu sandbox'ta `java`/`kotlinc`/`gradle` bulunmuyor; JDK ve Kotlin
derleyicisi yalnızca GitHub *release asset* olarak dağıtıldığı için indirilemiyor
(`objects.githubusercontent.com` ağ kısıtında). Gradle'ın kendisi de
`services.gradle.org` üzerinden indirilemiyor.

## 6. Bekleyen cihaz QA

- [ ] `./gradlew :app:compileFossDebugKotlin` ile tam derleme.
- [ ] `./gradlew :app:testFossDebugUnitTest --tests '*HeroVitrinLayoutTest' --tests '*HomeHeroPolicyTest' --tests '*HeroImageSelectionTest'`.
- [ ] Dikey telefonda 5 ekran görüntüsündeki kaynaklarla (Shikimori/AniList/Simkl)
      görselin tam kapladığını, yanlarda boş kutu olmadığını doğrula.
- [ ] 3:1 banner (ör. geniş AniList banner'ı) için görselin ince şeride düşmediğini
      ve yatay ortadan kırpıldığını doğrula.
- [ ] Başlık/logo ile meta satırının görselin üstüne binmediğini doğrula.
- [ ] Bulanık ambient'ın metni okunur bıraktığını doğrula (parlak bir banner'da).
- [ ] Yatay mod, tablet ve TV'de `OVERLAY` modunun doğru çalıştığını doğrula.
- [ ] Yetişkin bulanıklığı açıkken ambient katmanının da bulanık olduğunu doğrula.
- [ ] Büyük yazı ölçeğinde (1.3×–2×) bandın büyüyüp görsel şeridinin küçülmesini
      doğrula; sayfa değişiminde ölçü sıçraması olmamalı.

## 7. Dosyalar

| Dosya | Durum |
|-------|-------|
| `app/src/main/java/com/kitsugi/animelist/ui/components/HeroVitrinLayout.kt` | YENİ |
| `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiHeroSection.kt` | Değişti |
| `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiNsfwImage.kt` | Değişti |
| `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiShimmer.kt` | Değişti |
| `app/src/test/java/com/kitsugi/animelist/ui/components/HeroVitrinLayoutTest.kt` | YENİ |
| `app/build.gradle.kts` | Sürüm 2.4.300 → 2.4.301 |
| `docs/VITRIN_NUVIO_YERLESIM_2026-10-17.md` | YENİ (uygulama notu) |
