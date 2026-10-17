# Keşfet Vitrini — Nuvio Tarzı Yerleşim Uygulama Notu

**Tarih:** 17 Ekim 2026 · **Sürüm:** v2.4.301 · **Dal:** `arena/d0199659-kitsugi-beta`

Bu not, `PLAN_TASK_VITRIN_NUVIO_YERLESIM_2026-10-17.md` planının uygulama
detaylarını içerir.

---

## 1. Referans kod nereden geldi

| Kaynak | Dosya | Alınan karar |
|--------|-------|---------------|
| NuvioMobile | `composeApp/src/commonMain/kotlin/com/nuvio/app/features/home/components/HomeHeroSection.kt` | Tek `ContentScale.Crop` backdrop; bandın tamamında dikey gradyan; altta `bottomFadeHeight` şeridi; logo için sabit oranlı `ContentScale.Fit` yuvası; göstergelerin içerik bloğunun içinde olması |
| NuvioTV | `app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeHero.kt` | Sol tarafta yatay gradyan + alttaki dikey şerit; `ContentScale.Crop` + `Alignment.TopEnd` |
| Kitsugi TV (yerleşik) | `ui/tv/home/TvModernHero.kt` | Aynı fikir zaten TV tarafında vardı: tam kaplayan `Crop` + gradyan + sol-alt metin |

> Not: Nuvio lisansı GPL-3.0'dur. Burada **kopyalanan kod değil, tasarım kararı**
> (katman sırası, gradyan değerleri, kutu oranı mantığı) uygulanmıştır.

## 2. Ölçü modeli

Dikey telefon (ör. 393×851 dp):

```
bant yüksekliği      = heroBandHeightDp(851)            = 468 dp   (değişmedi)
metin yuvası         = 28 + 6 + 84 + 6 + 40 + 6 + 20 + 44 = 234 dp
görsel şeridi        = bant − metin yuvası              = 234 dp   (1.68 oran)
```

Kutu oranı **görselden değil cihazdan** gelir. Bu yüzden:

- 16:9 (1.78) görsel → yatayda %6 kırpılır, neredeyse tamamı görünür.
- 3:1 banner → yatay ortadan kırpılır (~%56'sı kalır) ama **ince şeride düşmez**.
- 1.5:1 görsel → üstten hafif kırpılır, yüz/baş korunur (`Alignment.TopCenter`).

Yatay / TV / tablet modunda metin görselin üstüne bindirilir; bu yüzden
`OVERLAY` modunda görsel şeridi bandın tamamıdır.

## 3. Katman sırası (dikey telefon)

```
1. Ambient   → aynı görsel, Crop, bitmap blur (30 dp), bandın tamamı
2. Koyuluk   → arka plan rengi @ %55 (keskin görselin ALTINDA)
3. Keskin    → aynı görsel, Crop, TopCenter, şerit yüksekliğinde
4. Scrim     → dikey gradyan (şerit alt kenarı → metin yuvasına yumuşak geçiş)
5. Üst şerit → durum çubuğu için hafif koyulaştırma (48 dp)
6. Metin     → kaynak rozeti / başlık+logo / alt başlık / meta (sabit yuvalar)
7. Göstergeler→ alt-solda, 7'ye kadar okunabilir çubuk
```

## 4. Coil tarafı — neden ek indirme yok

Coil 3.5.0 `NetworkFetcher`:

```kotlin
private val diskCacheKey: String
    get() = options.diskCacheKey ?: url
```

Disk cache anahtarı **yalnızca URL**'dir; boyut/ölçek/dönüşümler dönüşüm
öncesi orijinal baytlar olarak diskte saklanır. Bu yüzden:

- Keskin katman (HQ, `memoryCacheKey = url`) ve ambient katman (bulanık, 960×540)
  **aynı dosyayı** paylaşır → ikinci bir ağ isteği oluşmaz.
- Maliyet yalnızca küçük bir decode + stack-blur'dur (240×135 üzerinde çalışır).

`Modifier.blur` (RenderEffect) **kullanılmaz**; repo'da MIUI/HyperOS cihazlarda
belgelenen `libhwui.so` RenderThread SIGSEGV (`fault_addr=0x20`) riski bu yüzden
bu katmana bulaşmaz. Bulanıklık `BlurTransformation` ile bitmap seviyesinde
uygulanır.

## 5. NSFW etkileşimi

`KitsugiNsfwImage` içinde iki bulanıklık yolu vardır:

| Durum | Sonuç |
|-------|-------|
| `ambientBlur` verilmiş (ambient katmanı) | Bitmap bulanıklığı (30 dp) |
| NSFW + RenderEffect destekli cihaz | `Modifier.safeBlur` (GPU) |
| NSFW + RenderEffect desteklemeyen cihaz | Bitmap bulanıklığı (24 dp) |

Bulanıklık yarıçapı `max(ambient, nsfw)` olarak birleştirilir; yetişkin içerik
için daha fazla bulanıklık her zaman daha güvenlidir.

## 6. Neden "Fit" tamamen kaldırıldı

`ContentScale.Fit`, görseli kutuya sığdırırken **en-boy oranını korur**; kutu
oranı görselden farklıysa zorunlu olarak boşluk bırakır. Vitrinin kutu oranı
cihazdan geldiği için `Fit` bir kaynakta boş kutu, diğerinde ince şerit üretir —
yani görüntülenen sonuç kaynağa göre değişir. `Crop` ise kutuya **her zaman**
tamamen kaplar ve kırpma miktarı cihazdan bağımsız olarak sabittir.

Alternatif (bulanık arka plan + net `Fit` görsel) Nuvio'da yoktur ve v2'de
bulunurdu; v2.4.300'de bulanıklık düşürülünce "ikinci görsel" hissi doğdu.
Yeni düzende arka plan katmanı **bulanık** olduğu için bu his tamamen giderildi.

## 7. Sürdürülebilirlik notları

- Ölçü politikası tek yerde (`HeroVitrinLayout.kt`); vitrin, iskelet ve testler
  aynı fonksiyonu kullanır → yükleme bitince zıplama olmaz.
- `KitsugiNsfwImage`'ın yeni parametresi listenin sonundadır; 67 çağrının
  tamamı isimlî argüman kullanıyor, konumsal çağrı etkilenmez.
- Yeni dosyalar Android/Compose dışında saf Kotlin olduğu için JVM test
  paketine (`scripts/run_hero_contract_checks.sh`) eklenebilir.
