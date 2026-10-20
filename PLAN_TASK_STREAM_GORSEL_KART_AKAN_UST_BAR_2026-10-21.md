# PLAN / TASK — Video kaynak ekranı: görselli kartlar, akan üst bar, "İzle" hatası

**Tarih:** 2026-10-21
**Sürüm:** 2.4.318 → **2.4.319**
**Şikâyet (kullanıcı):**
1. Üst bar kaydırırken yumuşak gidip gelmeli.
2. Eklentilerden video görselleri gelmeli; hangi veriye ait olduğu belli olsun, uygun
   boyutlu kutularda (dikey/yatay fark etmeden) gösterilmeli. Görseller iki tarafta kalsın,
   sadece sığacak şekilde boyutlandırılsın.
3. Arayüz daha güzel olsun.
4. "İzle"ye basınca video açılmıyor, uygulama ana sayfaya atıyor / çökmüş gibi oluyor.
5. Video verileri (ayrıntılar) hep görünsün; her kartı tek tek açmak zorunda kalınmasın.
   Daha kompakt ama kullanıcı dostu olsun.

## 1. Yapılanlar

| # | Değişiklik | Dosya |
|---|---|---|
| 1 | Kartlara 74×104 dp (2:3) kapak görseli: `thumbnailUrl` → medya posteri → gradyan placeholder. Yetişkin bulanıklığı `KitsugiNsfwImage` ile korunur. | `StreamCard.kt` |
| 2 | Ayrıntı akordiyonu kaldırıldı; kalite, dil türü, ses/altyazı dilleri, boyut, Debrid/P2P ve host her zaman görünür. | `StreamCard.kt` |
| 3 | Kart tıklaması oynatır; oynat (46 dp) ve indir (38 dp) ayrı hedefler. | `StreamCard.kt` |
| 4 | Üst bar kaydırmaya birebir bağlı `NestedScrollConnection` + `graphicsLayer` ile yumuşak gizlenip geri geliyor; sticky başlık zıplaması kaldırıldı. | `StreamScreenContent.kt` |
| 5 | Dikey ve yatay düzen aynı üst bar modelini kullanır; yatayda sol ray bağımsız kaydırılır. | `StreamScreenContent.kt` |
| 6 | Arayüz cilası: vurgu ışıması, yumuşak arama alanı, pill etiketler, kaynak kartı, zengin grup başlığı, sonuç özeti. | `StreamScreenContent.kt`, `StreamResultsList.kt` |
| 7 | Oynatıcıya giden Intent'in Binder yükü küçültüldü (250→48 KB, 450→96 KB) ve taşma için yedeksiz yeniden deneme eklendi. | `KitsugiFullscreenPlayerActivity.kt` |
| 8 | `startWithStreamUrls` artık `Boolean` döndürür; başarısızlık sebebiyle birlikte gösterilir (sessiz yutma bitti). | `KitsugiFullscreenPlayerActivity.kt`, `KitsugiStreamScreen.kt` |
| 9 | Oynatıcı `onCreate` gövdesi `bootstrapPlayer()` içine alındı ve `runCatching` ile sarıldı; `videoUrl`/`videoId` yoksa bilgilendirip kapanır. | `KitsugiFullscreenPlayerActivity.kt` |
| 10 | `WatchHistoryManager.record` artık `runCatching` içinde — geçmiş yazımı oynatmayı düşüremez. | `KitsugiStreamScreen.kt` |
| 11 | Receiver/servis yaşam döngüsü sertleştirildi (`pipReceiverRegistered`, korumalı `KeepAliveService`). | `KitsugiFullscreenPlayerActivity.kt` |
| 12 | TR/EN yeni metinler; `values-en/player_parity_strings.xml` eksik çevirileri tamamlandı. | `res/values*` |
| 13 | Harici oynatıcı devri artık `Boolean` döner; oynatıcı gerçekten açılmadıysa tam ekran ekranı **kapanmaz**, sebep toast ile söylenir. | `KitsugiFullscreenPlayerActivity.kt`, `KitsugiFullscreenPlayerScreen.kt` |

## 2. "İzle → ana sayfa" şikâyeti: kök neden analizi

Sandbox'ta cihaz/logcat yok; bu nedenle kesin tek bir imza doğrulanamadı. Buna karşılık
kodda **oynatma yolunu düşürebilecek** dört somut kusur bulundu ve kapatıldı:

| # | Kusur | Neden çökme/ana sayfa? | Düzeltme |
|---|---|---|---|
| B1 | `WatchHistoryManager.record(...)` `try` bloğunun **dışında** | Disk/veri hatası → ana thread'de yakalanmamış istisna → süreç ölür, kullanıcı ana sayfada bulur | `runCatching` |
| B2 | Yedek JSON eşikleri 250 KB + 450 KB karakter | Tek `startActivity` ~1 MB Binder sınırını aşabilir → `TransactionTooLargeException`; 125 kaynaklı listede eşiğe çok yakın | 48 KB + 96 KB + taşmada yedeksiz retry |
| B3 | `KitsugiFullscreenPlayerActivity.onCreate` korumasız | Intent çözümleme/servis/Compose sırasında herhangi bir istisna → süreç ölür | `runCatching` + sebep gösterme + düzgün `finish()` |
| B4 | Başlatma hatası sessiz | Kullanıcı "hiçbir şey olmadı" görür, geri tuşuna basınca uygulamadan çıkar | `Boolean` dönüş + diyalog |
| B5 | Harici oynatıcı devri başarısız olsa bile `onBack()` çağrılıyordu | Tam ekran oynatıcı açılır ve hemen kapanır → kullanıcı "video açılmadı, geri attı" görür | `launchExternalPlayer` → `Boolean`; başarısızlıkta ekran açık kalır + toast |

Ek gözlem: önceki sürümde (v2.4.318) kart görselleri ve ayrıntılar kaldırıldığı için
kullanıcı "hangi veriye ait belli olmuyor" diyordu; kart görseli + host etiketi artık her
kartta görünür.

## 3. Doğrulama

- [x] Değişen Kotlin dosyalarında tree-sitter-kotlin sözdizimi kontrolü (hata yok).
- [x] `values/strings.xml` ve `values-en/strings.xml` XML parse + yinelenen anahtar kontrolü.
- [x] `scripts/check_localization_resource_parity.py` → **in sync (7743 string, 4 array)**.
- [x] Kullanılan tüm `R.string.*` kimlikleri kaynaklarda mevcut (otomatik tarama).
- [ ] Android derleme: ortamda Java/Android SDK yok — yapılmadı.
- [ ] Cihaz: dikey/yatay, 125 kaynaklı liste, oynat/indir, hızlı kaydırma.
- [ ] Cihaz: çökme sürerse `KitsugiCrashHandler` raporu (`Ayarlar > Tanı Raporu`) paylaşılmalı.

## 4. Değişen dosyalar

```
app/src/main/java/com/kitsugi/animelist/ui/screens/stream/StreamCard.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/stream/StreamResultsList.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/stream/StreamScreenContent.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/stream/KitsugiStreamScreen.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerActivity.kt
app/src/main/res/values/strings.xml
app/src/main/res/values-en/strings.xml
app/src/main/res/values-en/player_parity_strings.xml   (yeni)
app/build.gradle.kts                                   (2.4.319)
docs/STREAM_SELECTION_UX.md
release_notes_v2.4.319.md                              (yeni)
```
