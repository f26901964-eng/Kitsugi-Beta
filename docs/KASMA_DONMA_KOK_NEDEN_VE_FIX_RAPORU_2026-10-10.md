# Genel Kasma / Donma — Kök Neden ve Düzeltme Raporu

**Tarih:** 2026-10-10 · **Dal:** `arena/b8c1a175-kitsugi-beta` · **Sürüm:** 2.4.235 sonrası

## 1. Şikâyet

Kullanıcı raporu: uygulama sabahki hâline göre "dağlar kadar fark"la feci
kasıyor, donuyor, buga giriyor. Ekte logcat gönderildi ancak dosya bu
sandbox'a ulaşmadı (`/home/user/uploads` dizini oluşmadı — önceki oturumlarda
crash raporlarında da aynı durum yaşandı). Teşhis, bugünkü commit zincirinin
statik analiziyle yapıldı.

## 2. Kök Nedenler

### A) "Cockroach" iç içe ana Looper + debug logcat yazıcı (ANA NEDEN)
`KitsugiApplication.onCreate` içinde ana mesaj döngüsünün İÇİNE ikinci bir
`Looper.loop()` kuruluyordu. Bu yapı:

- ActivityThread'in gerçek döngüsünü kalıcı olarak iç içe döngünün arkasına
  gizliyor,
- UI callback'lerinden kaçan hataları **sessizce yutup** uygulamayı yarım
  kalmış Compose/Window durumuyla canlı tutuyordu — kullanıcı bunu çökme
  değil, **bütün uygulamada donma/jank** olarak yaşıyordu,
- Kurtarma limiti dolduğunda bile iç içe döngü kalıntıları mesaj kuyruğu
  davranışını bozabiliyordu.

Ayrıca debug APK'da uygulama içinden `logcat -f *:D` süreci başlatılıyordu:
yüzlerce ayrıntılı kayıt sürekli diske yazılıyor, görsel decode ve Room
trafiğiyle I/O yarışına girip kaydırma karelerini düşürüyordu.

Bu kod bugün eklenmedi; ancak bu akşamki büyük UI değişiklikleri
(3745 anahtarlı i18n geçişi + ~300 dosyalık gradyan entegrasyonu + yeni
açılır menüler) UI callback'lerinde hata/exception yüzeyini büyüttü ve yutma
mekanizması çok daha sık devreye girer hâle geldi → "sabah iyiydi, şimdi
donuyor" algısı.

**Durum:** `main` üzerinde `fc953a4` ile kaldırılmıştı ama 2.4.235
kullanıcının kurduğu sürümde hâlâ duruyordu. Bu dal `main` ile
birleştirilerek düzeltme içeri alındı.

### B) Gradyan entegrasyonunun kare-başı shader tahsisatı (YENİ, bu akşam)
`e7b9f42` ile ~300 UI dosyası `ui.theme.gradient.*` sarmalayıcılarına
bağlandı. İki renkli gradyan seçiliyken (veya hazır temada DataStore'da eski
bir `customAccentColor2` kalmışken):

- `resolveAccentBrush` / `AccentAwareBackground|BorderNode.draw()` **her
  çizimde yeni** `AngleLinearGradientBrush` üretiyordu,
- `ShaderBrush` shader önbelleği **instance başına** tutulduğundan her yeni
  instance = çizim başına yeni `android.graphics.LinearGradient` shader'ı,
- Kaydırma sırasında yüzlerce gradyan bileşen her karede shader tahsis
  ediyordu → CPU + GC + RenderThread baskısı.

Ek olarak `Theme.kt`'daki koşul gevşetilmişti (`customAccentColor2 != 0`
tek başına yeterliydi): özel vurgu rengi seçili değilken eski bir
`customAccentColor2` değeri hazır temada gradyan modunu yanlışlıkla aktive
edip TÜM yazı/ikon/arka planı pahalı çizim yoluna sokuyordu.

### C) Kaydırma durumu izolasyonu
`bc9f662` (main): Keşfet/Arama scroll state'i ayrılmış, hero kare-başı
tahsisatları giderilmişti; bu da 2.4.235'te yoktu, birleştirmeyle alındı.

## 3. Bu Dalda Yapılan Düzeltmeler

| Commit | Değişiklik |
|---|---|
| `merge` | `main` içeri alındı: `fc953a4` (iç içe Looper + logcat yazıcı kaldırıldı), `bc9f662` (scroll state izolasyonu), `e4bcf0c` (FAB import fix) |
| `a350173` | `KitsugiAccentSupport.kt`: `GradientBrushCache` — aynı (renk, açı) için süreç genelinde TEK fırça instance'ı (`angleLinearGradientBrush` fabrikası); `resolveAccentBrush`/`accentBackgroundBrush`/`resolveCurrentAccentBrush`/`accentBrushWithAlpha` önbellek+remember ile sabitlendi |
| `a350173` | `KitsugiGradientMaterial.kt`: `AccentAwareBackgroundNode` ve `AccentAwareBorderNode` çözülen fırçayı girdiler değişene kadar node üzerinde önbellekliyor (kare-başına çözüm çağrısı yok) |
| `a350173` | `Theme.kt`: `accentColor2` yalnızca `customAccentColor != 0 && customAccentColor2 != 0` iken aktif — hazır temada eski/bayatsı ikinci renk gradyan modunu artık aktive edemez |

## 3.5 Logcat Kanıtları (diag/ — 22:51–22:58 yakalaması, v2.4.235)

`diag/Kitsugi-logcat-20261010-2337.txt`, `diag/logcat.txt`, `diag/gfxinfo.txt`,
`diag/meminfo.txt` (GitHub üzerinden alındı; doğrudan ek kanalı sandbox'a ulaşmadı).

| Kanıt | Değer | Yorum |
|---|---|---|
| `Looper PerfMonitor doFrame` | 242 kare 500–999 ms, 2 kare ≥1000 ms | Ana thread kare başına 0.5–1.4 sn çalışıyor = hissedilen donma |
| `Choreographer: Skipped` | sürekli 80–92 frame | Her donma ~1.3–1.5 sn |
| `OpenGLRenderer: Davey!` | 1237–1300 ms | Aynı donmaların render tarafı yansıması |
| GC | ~4.5 dk'da 100 background GC; her biri ~1 MİLYON nesne (34–36 MB) freed | Bellek sızıntısı DEĞİL (heap 43–48 MB sabit), devasa **allocation churn** — recomposition/çizim başına yoğun nesne üretimi |
| gfxinfo | Janky %24, p90=1200 ms, Slow UI thread=330, GPU p99=11 ms | Sorun %100 ana thread CPU; GPU sağlıklı |
| `RetryInterceptor` / `KitsugiApiBase` | 34× 429 (Retry-After 5 sn), sorgular ~10 sn'de bir tekrar | AniList rate-limit duvarına katmanlı retry (2×3) fırtınası |
| ANR / FATAL | YOK | Uygulama çökmüyor; bozuk durumda ayakta kalıyor |
| Eski crash raporu (00:21, v2.4.226) | `NoClassDefFoundError: kotlinx.datetime.serializers.InstantIso8601Serializer` | v2.4.227'de proguard keep + datetime 0.6.1 pin ile ZATEN düzeltilmiş |

Bu kanıtlar 2. bölümdeki kök nedenleri doğruluyor: iç içe Looper'ın yuttuğu
hatalarla ayakta tutulan bozuk kompozisyon durumu + akşamki ~300 dosyalık
gradyan entegrasyonunun kare-başı fırça/shader tahsisatı, ana thread'de
kare başına yüzlerce ms ve saniyede ~35 MB çöp üretimi olarak görülüyor.

## 4. Doğrulama

- [x] Değiştirilen dosyalarda dengeli parantez/kaşeli kontrolü.
- [x] `Looper.loop()` artık yalnızca uyarı yorumunda geçiyor; iç içe döngü kodu yok.
- [x] `AngleLinearGradientBrush` doğrudan constructor kullanımı yalnızca önbellek fabrikasında.
- [ ] Gradle derleme — sandbox'ta JDK/Android SDK yok; CI/APK derlemesi gerekli.
- [ ] Cihazda doğrulama: Keşfet/Arama/Listem kaydırma, gradyan tema açık ve kapalı.

## 5. Kullanıcıya Not

Logcat dosyası (`Kitsugi-logcat-20261010-2337.txt`) sandbox'a ulaşmadı;
tekrar yüklenirse ANR/Choreographer satırları üzerinden ek doğrulama yapılır.
Bu daldan derlenecek APK ile test edilmeli.
