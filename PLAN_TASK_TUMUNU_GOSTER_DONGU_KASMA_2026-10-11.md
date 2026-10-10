# PLAN/TASK — "Tümünü Göster" döngüsü, üst üste binen kutucuklar, arama/keşfet kasması

Tarih: 2026-10-11
Durum: Kod düzeltmeleri tamamlandı; cihazda/Gradle'da doğrulama bekliyor.

## Hata
- Keşfet ve kaynak sayfalarındaki "Tümünü Göster" bölümleri aşağı kaydırıp yeni veri çekildikten
  sonra bozuluyor; kutucuklar üst üste biniyor ve sayfa kendini döngüye sokup sürekli istek atıyor.
- Arama sayfası veri geldikçe kasıyor; kart üzerindeki "yaklaşan bölüm" bilgisi yanlış/olmaması
  gereken bir yapıma ait olabiliyor.
- AniList/MAL/Jikan/Kitsu/Simkl/Shikimori entegrasyonları ve bu kartların üzerindeki ek bilgi
  katmanları topluca etkileniyordu.

## Kök nedenler
1. **Lazy anahtarlarında `type` eksik.** Kaynakların id uzayları tür bazlı ayrı (MAL anime #21 ≠
   MAL manga #21; TMDB film/dizi id uzayı ayrı). Lazy anahtarı `source + malId` ile kurulduğu için
   aynı listenin içindeki iki farklı yapım aynı anahtarı alıyor; Compose ikisini tek slot sayıp üst
   üste çiziyor, anahtar→indeks haritası ve scroll konumu bozuluyor. Çakışma ancak iki kayıt da
   listede olunca (2. sayfa yüklendikten sonra) oluştuğu için "veri geldikçe" görünüyor. Kimlik/dedup
   tarafı (`exploreIdentity`, `mergeSourceSearchResults`) 2026-10-08'de düzeltilmiş, Compose
   anahtarları atlanmıştı.
2. **Sonsuz kaydırma tetikleyicisi yükleme bayrağını anahtarına dâhil ediyordu.** Her sayfa
   yüklemesi bittiğinde (true→false) efekt yeniden ateşleniyor, liste yerleşimi güncellenmediği
   için "sona ulaşıldı" koşulu hâlâ `true` okunuyor ve kullanıcı kaydırmasa bile arka arkaya sayfa
   çekiliyordu. Filtre görünür listeyi büyütmediğinde zincir hiç bitmiyordu (AniList 429 fırtınası).
3. **Shimmer fırçası her karede yeniden üretiliyordu.** Animasyon değeri kompozisyonda okunuyor,
   `Brush.linearGradient` her karede yeniden kuruluyor ve CompositionLocal ile dağıtılıyordu →
   yükleme sürdükçe değeri okuyan tüm iskelet bileşenleri yeniden kompoze oluyor, kart başına yeni
   shader üretiliyordu (logcat: ~1M nesne / ~32 MB, ~1.2 sn'de bir, 4 dakika GC fırtınası).
4. **Yaklaşan bölüm eşleşmesi başlık tahminine düşebiliyordu** (öğenin kendi verisi varken bile) ve
   AniList takvimi boş sonucu önbelleğe almadığı için her sayfa yüklemesi yeni istek atıyordu.

## Yapılan işler
- `ui/utils/KitsugiLazyKeys.kt` eklendi: `kitsugiLazyKey(prefix)` = `prefix + source + type + malId +
  tmdbId + cs3Url.hash`. Aynı sınıftaki 11 kullanım noktası bu tek merkeze geçirildi.
- `FullScreenMediaGridPage` ve `SearchScreen`'e otomatik sayfalama emniyeti: kaydırma başına 3 sayfa
  bütçesi + "görünür öğe sayısı değişmediyse tekrar denenmez" + kullanıcı kaydırdığında bütçe
  sıfırlanır (normal sonsuz kaydırma birebir korunur).
- `KitsugiShimmer.kt`: sabit (dosya düzeyinde) fırça + `LocalShimmerOffset: State<Float>`; değer
  yalnızca `drawBehind` içinde okunur (her karede yeniden çizim, recomposition ve shader üretimi
  yok). Yükleme yokken animasyon saati hiç kurulmaz. `rememberStaticKitsugiShimmerBrush` kaldırıldı
  (referanssız).
- `TvMangaBrowseScreen`: TV manga iskeleti statik `background(brush)` yerine animasyonlu shimmer
  bloğuna geçirildi.
- `KitsugiUpcomingAirEnricher`: kimlik (malId) eşleşmesi her zaman otoriter; başlık tahmini yalnızca
  öğenin hiç yayın verisi yoksa. AniList takvimi boş sonuç da 10 dk TTL ile önbelleğe alınır; hata
  (429/zaman aşımı) durumunda 60 sn backoff uygulanır.

## Değişen dosyalar
- `app/src/main/java/com/kitsugi/animelist/ui/utils/KitsugiLazyKeys.kt` (yeni)
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiShimmer.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiHorizontalMediaSection.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiMediaGridDialog.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/FullScreenMediaGridPage.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/tv/home/TvClassicHomeContent.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/tv/home/TvModernHomeContent.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/tv/home/TvGridHomeContent.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/tv/search/TvSearchScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/tv/manga/TvMangaBrowseScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiUpcomingAirEnricher.kt`

Detaylı kanıt zinciri ve cihaz test senaryoları: `KOK_NEDEN_RAPORU_TUMUNU_GOSTER_DONGU_KASMA_2026-10-11.md`

## Doğrulama / kalan işler
- Kod düzeltmeleri statik inceleme ile doğrulandı (parantez/süslü denge kontrolü, silinen fonksiyona
  kalan referans taraması, import kontrolü). **Bu ortamda Java/Android SDK yok** → Gradle derlemesi
  ve cihaz testi çalıştırılamadı.
- Uygun ortamda: `bash gradlew :app:assembleDebug` (veya `:app:testFossDebugUnitTest`).
- Cihazda bakılacak senaryolar:
  1. "Tümü" aramasında aynı kaynağın anime+manga çifti aynı listede → kartlar üst üste binmemeli.
  2. Explore → kaynak → bir bölümün "Tümünü Göster"i → sayfa sonuna kaydır, sayfa kendiliğinden
     istek atmaya devam etmemeli.
  3. Tür/format filtresi açıkken kaydırma → görünür öğe artmasa da istek zinciri durmalı.
  4. Arama yüklenirken (7 raf + liste) kare düşüşü ve `concurrent copying GC` sıklığı belirgin
     azalmalı; `dumpsys gfxinfo` janky oranı düşmeli.
  5. Kart üstü "Bölüm N · tarih" yalnızca doğru yapımda görünmeli.

Not: ZIP yalnızca değişen kaynak dosyaları ve bu plan/task + kök neden raporu dosyasını içerir;
tek başına APK veya tam proje değildir. Dosyaları mevcut depo köküne aynı dizin yapısıyla yerleştirin.
