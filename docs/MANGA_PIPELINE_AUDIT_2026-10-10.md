# Manga hattı incelemesi — 10 Ekim 2026

## Kapsam ve dürüst durum

Bu değişiklik ortak manga hattını onarır; **121 sitenin tamamının çalıştığına dair canlı onay değildir**.
Uygulamadaki Kotatsu bağımlılığının sabitlendiği `f287c414a6` kaynak arşivi GitHub'dan indirildi.
Bu sürümdeki **121 Türkçe parser bildiriminin tamamı statik olarak envanterlendi**:
[MANGA_TR_PARSER_INVENTORY.md](MANGA_TR_PARSER_INVENTORY.md).
Envanter; sitelere girip arama/bölüm/görsel test edilmesi veya her parser'ın satır satır doğrulanması anlamına gelmez.
Cihaza sonradan kurulan Mihon APK'larının tam listesi ve sürümleri bu checkout'tan bilinemez.

Bu ortamın dış ağ izni manga sitelerini kapsamıyor. Java/Android SDK/emülatör de yok;
Gradle testi `JAVA_HOME is not set` hatasıyla başlayamadı. Dolayısıyla derleme, APK,
canlı kaynak ve cihaz testi **bekliyor**.

## Bulunan somut sorunlar ve değişiklikler

| Katman | Önce | Değişiklik |
|---|---|---|
| Detay aksiyonu | Oku `fillMaxWidth()` ile tüm satırı kaplıyor | API ve kayıtlı medya detaylarında İzle gibi içeriğe göre genişlik; eşleşen manga adı butona taşmıyor |
| Okuyucu durumu | Aynı mutable sayfalarla `StateFlow.value.toList()` eşitlik nedeniyle yeni olay üretmiyor | Sayfa durumu Compose snapshot state; nesne kimliği korunuyor |
| İndirme işleri | Eski işin `finally` bloğu aynı index'teki yeni işi silebiliyor | İş başlamadan kaydı yapılır, bitişte yalnız kendi kaydı kaldırılır |
| Bölüm geçişi | Eski sayfa listesi isteği yeni bölüme yazabiliyor | Önceki yükleme iptal edilir, aktiflik kontrolü, kayıtlı sayfa aralığı doğrulaması, ilk sayfa prefetch |
| Mihon görsel | Genel istemci ile yeniden kurulan istek; `imageRequest` atlanıyor | `HttpSource.getImage(Page)` ile kaynağın kendi istek/istemci/çerez/interceptor zinciri |
| Mihon adresi | Sadece alt sınıftaki `baseUrl` alanına reflection | Public `HttpSource.baseUrl` sözleşmesi |
| Kotatsu ağ | NO_COOKIES, parser interceptor'ı çağrılmıyor | WebView cookie jar kullanan mevcut NetworkHelper; request source tag'ine göre parser interceptor yönlendirmesi |
| Kotatsu sayfa | `preview` gerçek görsel gibi işleniyor | Önizleme kullanılmaz, `getPageUrl` çalıştırılır; özgün sayfa metadata'sı sınırlı cache'te korunur |
| Kotatsu detay/bölüm | Aramadan sonra sentetik id/title; bölüm metadata'sı kayboluyor | Sınırlı manga/bölüm/sayfa cache'leri; orijinal parser nesnelerini yeniden kullanma |
| Kotatsu bölüm sırası | Kotatsu'nun eski→yeni sırası okuyucunun yeni→eski kontratına ters | Adapter çıkışında ters sıra; bölüm adı boşsa sayı etiketi, scanlator korunur |
| Kotatsu sayfalama | Her sayfa için sabit 20 offset ve 20'den azsa bitti varsayımı | Ardışık isteklerde gerçek sonuç sayısından offset; desteklenen sıralama seçimi |
| Kaynak hataları | Kotatsu istisnaları boş başarılı listeye dönüşüyor; Mihon detay/bölüm hata gizliyor | İstisnalar üst katmana taşınır; iptal mirror/fallback işine dönüşmez |
| Arama | Aynı adlı kaynak durumları çakışıyor; bazı işler uzun süre bekliyor | Durum anahtarları stableSourceKey; kaynak başına 30 saniye bütçe; yeni sorguda eski pagination işleri iptal |
| Sonuç açma | Kaynak adı üzerinden tahmin, bulunamazsa rastgele ilk kaynak | Kart gerçek `(source, manga)` çiftini taşır; stable key ile ayırma; ikinci UI skor filtresi kaldırıldı |
| Önbellek | HTML/boş veri de hazır sayılıyor; yazma hatası yutuluyor | Decode bounds doğrulama, eski geçersiz cache'in tahliyesi, benzersiz geçici dosya, başarısız yazmada hata |
| Görsel fallback | Coil başarılı olsa bile kaynak ikinci kez çağrılıyor, yerel dosya oluşmayabiliyor | İki ayrı ağ hattı yerine kaynak üzerinden doğrulanmış dosya |
| Sağlık kontrolü | URL boş değilse Healthy | İlk görsel indirilip decode sınırları doğrulanmadan Healthy verilmez |
| Açıklama/durum | HTML etiketleri görünür; Kotatsu durum/tür bilgisi düşüyor | Jsoup text dönüşümü, durum ve tür eşlemesi |

## İncelenen upstream sözleşmeler

Pin URL kökü: https://github.com/Kotatsu-Redo/kotatsu-parsers-redo/tree/f287c414a6/src/main/kotlin/org/koitharu/kotatsu/parsers

- `MangaParser.kt`: `getPageUrl`, `getRequestHeaders`, `Interceptor` kontratı.
- `model/MangaPage.kt`: `preview` küçük resimdir; `url` HTML veya göreli adres olabilir.
- `model/MangaChapter.kt`: id, volume, branch, source gibi metadata.
- `network/OkHttpWebClient.kt`: HTTP isteklerinde `MangaSource` tag'i.
- `site/madara/tr/TortugaCeviri.kt`: ekran görüntüsündeki kaynak Madara ailesine bağlı.
- `site/madara/MadaraParser.kt`: bölüm AJAX yolları ve ters HTML listesinden eski→yeni bölüm üretimi.
- `site/mangareader/MangaReaderParser.kt`: parser interceptor'ı ve NetShield için JS ihtiyacı.
- `core/PagedMangaParser.kt`, `util/Paginator.kt`: offset gerçek yüklenen öğe sayısıdır.

Envanteri yeniden üretmek için, aynı pin'in çıkarılmış arşiviyle:

```sh
python3 scripts/audit_manga_parsers.py /path/to/kotatsu-parsers-redo > docs/MANGA_TR_PARSER_INVENTORY.md
```

## Açık kalan işler — site bazında devam gerekli

1. **JS/WebView:** Kotatsu context'teki JS çalıştırma, istek yakalama ve görsel yeniden çizim gerçek uygulamalar değildi. Artık null/boş/işlenmemiş görsel döndürmek yerine açık destek hatası verirler. Bu altyapı henüz uygulanmadı; bu yetenekleri isteyen kaynaklar hâlâ çalışmayabilir. Özellikle NetShield yolunu kullanan MangaReader kaynakları cihaz testi ister.
2. **Canlı alan adları/selector/API değişimleri:** Envanter alan adları upstream sabitleridir, güncellik kanıtı değil. Her kaynak için bilinen mevcut bir manga ile ayrı test gerekir. Svelte/Inertia özel yedek motorlarının tam bölüm/görsel paritesi ayrıca doğrulanmalı.
3. **Kapak görselleri:** Bu patch okuyucu sayfalarının kaynağa özel indirme hattını düzeltir. Kartların Coil kapak yüklemelerinde kaynağa özel hotlink header desteği ayrıca ele alınmalı.
4. **Kimlik ve eşleşme:** Kart yanlış kaynağa gitmez; ancak orijinal metadata cache dışında kaldığında Kotatsu stub fallback'i sürer. Katalog alternatif adları ve kaynak özel arama sorguları için fixture'lar gerekir. Alaka skorunun doğru manga garantisi olmadığı unutulmamalı.
5. **Formatlar:** Cache ve sağlık doğrulaması Android BitmapFactory kullanır. Cihazın decode edemediği AVIF vb. formatlar açık hata olur; ek decoder desteği ayrıca test edilmeli.
6. **Sayfalama:** Sıralı gezintide gerçek offset saklanır; cache dışında rastgele sayfa atlamada eski 20 varsayımı yedek olarak kalır. Tek sayfalı kaynaklar ve değişken kataloglar için canlı sınır testi gerekir.

## Testler ve kabul kriterleri

Eklenen JVM testi: `MangaPageStateTest` — status okumasının snapshot tarafından izlenmesi, bölüm sayfalarının bağımsızlığı.
Eklenen Android testi: `MangaCacheTest` — boş/HTML reddi, gerçek PNG round-trip, bozuk replacement'ın sağlam dosyayı silmemesi, eski HTML cache tahliyesi.

Bu ortamda yapılanlar:
- Değiştirilen Kotlin dosyaları ve yeni testlerde tree-sitter Kotlin sözdizimi kontrolü: hata yok (derleme/typecheck değildir).
- `git diff --check`: geçti.
- Envanter üretimi: 121 TR kayıt.
- `bash gradlew :app:testDebugUnitTest --console=plain`: Java bulunamadığından başlayamadı; testler çalıştırılmış sayılmaz.

Android geliştirme ortamında:

```sh
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:assembleDebug
```

Cihaz matrisi: telefon/tablet, yatay/dikey, pager/webtoon, TV. Her kaynak için arama → doğru manga URL'si → gerçek bölüm listesi → ilk/orta/son sayfa görseli → cache'ten tekrar açma. Berserk/Tortuga özel olarak yeniden denenmeli. Yeni sorguya ve bölüme hızlı geçiş, timeout, HTTP 403/429, HTML challenge, bozuk görsel, kaydırıp geri dönme ve manuel retry ayrıca test edilmeli. Bir kaynağa **çalışıyor** demek için bu zincirin gerçek içerikle doğrulanması gerekir.
