# PLAN / TASK — Fandom wiki ilgili kartlar ve sayfa yükleme (v2.4.289)

**Tarih:** 14.10.2026

**Durum:** Kod tamamlandı; cihaz/işlev testleri kullanıcıya bırakıldı.

**Dal / kod commit'i:** `arena/74cf06bb-kitsugi-beta` / `9c22fa9`.

## Sorun

Attack on Titan Wiki’de “İlgili Wiki sayfaları” şeridindeki karakter, seslendiren ve ekip kartlarından bazılarına dokununca (ekran görüntüsünde örnek: **Tetsurō Araki**) okuyucu “Sayfa yüklenemedi” diyordu. Bazı geçerli sayfalar da API veya içindekiler isteği kesilince eksik/boş kalabiliyordu.

## Kodda saptanan nedenler

1. `FandomApi.lookupTitles`, Wikipedia’ya özgü `prop=extracts` modülünü Fandom’a da gönderiyordu. Fandom API’si bu modülü her wikide desteklemiyor; isteğe bağlı modülün hatası tüm sayfa sorgusunu başarısız gösterebilir.
2. `FandomApiParsers.parseRelatedPages`, `missing` / `invalid` değerini `optBoolean` ile okuyordu. MediaWiki’nin eski `missing: ""` biçiminde boş metin `false` sayılıyor ve gerçekte var olmayan başlık tıklanabilir kart olabiliyordu. Sayfa kimliği (`pageid`) olmayan sonuçlar da kabul ediliyordu. Canlı Fandom yanıtı bu ortamda doğrulanamadığı için bu mekanizmanın ekran görüntüsündeki *tek* neden olduğu iddia edilmez; kodda somut bir hatadır.
3. Ana sayfanın bağlantı adayları oluşturulurken kırmızı (oluşturulmamış) bağlantılar ve başka wiki alanına giden bağlantılar da aynı wikiye ait sayılıyordu.
4. Kart önizlemesinde doğrulanmış sayfa, okuyucu açıldığında isteğe bağlı görsel/özet modülleriyle **yeniden** sorgulanıyordu; ikinci istek hata verirse geçerli kart da açılmıyordu.
5. `parseSections` içinde `parse.sections` alanının hiç bulunmaması “sıfır bölüm” sanılıyordu. Kısmi API cevabı günlerce içindekiler boşmuş gibi önbelleğe girebiliyordu. Karakter/ekip okuyucusu ayrıca `section 0` (giriş) bölümünü otomatik yüklemiyordu.

## Yapılanlar

- Fandom başlık sorgularından desteklenmeyen `extracts` çıkarıldı; Wikipedia için korundu. İsteğe bağlı modüllerin JSON hata cevabında daha yalın sayfa sorgusu denenir.
- `missing` / `invalid` işaretinin **varlığı** ve pozitif `pageid` kontrol edilir; olmayan kartlar ayıklanır. Çok adımlı yönlendirmeler çözülür, görsel adresleri HTTPS’ye normalleştirilir.
- İlgili bağlantı adaylarında kırmızı bağlantılar ve farklı Fandom topluluklarının URL’leri elenir.
- Doğrulanmış kart bilgisi kısa süreli yeniden kullanılır; okuyucu yalnızca içindekileri ayrıca çeker. Önizleme modülleri başarısızsa yalın MediaWiki `query&titles` ile gerçek sayfa kimliği teyit edilir; bulunmayan başlığa sahte makale üretilmez.
- İçindekiler isteği başarısızsa mevcut sayfa/giriş gösterilir, içindekiler için çalışan bir **Tekrar dene** düğmesi sunulur; kısmi içindekiler 7 gün saklanmaz. Karakter ve ekip okuyucusu artık giriş bölümünü otomatik çeker. Sayfa açılamazsa hata açıklaması ile yeniden dene / tarayıcıda aç eylemleri vardır.
- Bayat olumsuz önbelleği atlamak için ilgili sürüm anahtarları yükseltildi (`wiki:v4`, `char:v5`, `media-page:v6`, `article:v2`, `sec:v7`); olumsuz kalıcı TTL 1 gündür.
- Regresyon birim testleri `FandomApiParsersTest` ve `FandomHtmlTextTest` dosyalarına eklendi. Sürüm `2.4.289` ve sürüm notları güncellendi.

## Kullanıcı doğrulama listesi (testler kullanıcıya bırakıldı)

1. Attack on Titan Wiki → ilgili sayfalar: Yuki Kaji / Yui Ishikawa gibi **gerçekten var olan** kartlara dokununca sayfa ve girişi açılmalı. `pageid` bulunmayan ekip bağlantıları kart olarak görünmemeli; wiki’de yayımlanmamış kişiye uygulama makale uyduramaz.
2. Karakter ve ekip detayındaki wiki okuyucusunu aç: kısa açıklama yoksa da `Giriş` otomatik yüklenmeli.
3. Yavaş/kesilen ağda içindekiler hata verirse okuyucu tamamen kapanmamalı; `Tekrar dene` taze istek atmalı. Bağlantı düzelince içindekiler görünmeli.
4. Açılmayan bir bağlantıda `Tekrar dene` ve `Tarayıcıda aç` seçeneklerini, tab/geri gezintisini kontrol et.
5. Wikipedia kaynaklı bir medya maddesinde özet ve bölüm yüklemesinin bozulmadığını kontrol et.
6. İsteğe bağlı yerel birim testi ve derleme: `./gradlew :app:testFossDebugUnitTest --tests 'com.kitsugi.animelist.data.fandom.*' :app:assembleFossDebug`. Bu ortamda Android SDK/JDK bulunmadığı için Gradle/cihaz testi tamamlandı diye işaretlenmedi. Önceki kod gönderiminin otomatik CI akışı kullanıcı beklemeksizin başlamıştır; sonucu bu paketin onayı olarak alınmamalıdır.

## Sınır

Fandom’da olmayan bir madde, Fandom’un ağ kesintisi veya cihazdan erişimi uygulama tarafından tamamen giderilemez. Böyle durumlarda yanlış/boş kart üretmemek ve kullanıcıya gerçek yeniden deneme ile tarayıcı alternatifi sunmak amaçlandı.

## ZIP içeriği

Bu belge + `d5040344` tabanına göre değişen uygulama kodu, TR/EN string kaynakları, birim testleri, `app/build.gradle.kts`, `RELEASE_NOTES.md`, `release_notes_v2.4.289.md`. Yalnızca değişen dosyalar; tüm depo, `.git`, derleme çıktıları veya ekran görüntüleri dahil değildir. Dizin yolları aynen korunmuştur.
