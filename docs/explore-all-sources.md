# Keşfet — Tümü / kaynak alanları

## Kullanıcı deneyimi

Kaynak seçicide **Tümü** yedinci bir API değil, altı gerçek kaynağın birlikte gösterildiği bir görünüm modudur. Tek kaynak modları ve başlangıçtaki TMDB tercihi korunur.

- Üst vitrin, kullanılabilir her kaynaktan bir içerik seçer (en fazla altı).
- Sabit kaynak kısayolları AniList, MyAnimeList, TMDB, Simkl, Kitsu ve Shikimori alanlarına kaydırır; seçili modu değiştirmez.
- Her kaynak kendi logosu, renk vurgusu, kategori/içerik sayısı ve açıklamasıyla ayrılır.
- Trend, popüler, puan sıralaması, sezon, yaklaşan içerik, film ve manga şeritleri **ilgili kaynağın başlığının altında** kalır. Kaynaklar arasında şerit karıştırılmaz.
- Başlıktaki kategori kısayolları doğrudan o şeride gider. Kaynak alanları ayrı ayrı daraltılabilir; daraltma durumu Compose saved state ile korunur.
- “Tümünü Gör” kaynak, kategori ve başlangıç sonuçlarını birlikte taşır. Keşfet ekranının seçimini değiştirmeden ilgili kaynağın devam sayfasını açar.
- Telefon/tablet ve TV aynı kaynak alanlarını kullanır. TV'nin mevcut “Tümünü Gör” diyaloğu başlangıç listesini gösterir; mobildeki gibi API sayfalaması yapmaz.

## Kategori doğruluğu

Eski `ExplorePayload` alanları platformlar arasında aynı anlamı taşımaz: örneğin `topManga`, TMDB'de popüler dizilerdir. `sourceSections` bu eşlemeyi tek yerde tutar; ham alanları birleştirmek yanlış başlıklar üretir.

- MAL ve Shikimori'nin `topAnime/topManga` sıralamaları “En İyi” olarak adlandırılır.
- AniList ve Kitsu'da popülerlik ile puan sıralamaları ayrıdır; yeni `topRatedAnime/topRatedManga` alanları gerçek puan sıralamalarından gelir. Eski Gson kayıtları için bu alanlar nullable'dır.
- AniList trend, sezon ve anime filmi listeleri doğrudan AniList'ten yüklenir.
- Tek kaynak modundaki eski AniList → Kitsu fallback'i, **Tümü modunda kullanılmaz**. Bellek/disk önbelleğinde eskiden kalan fallback öğeleri de gerçek `source` alanına göre ayrılır; Kitsu içeriği AniList başlığı altında gösterilmez.
- Aynı sayısal ID'ye sahip film/dizi veya anime/manga tek içerik sayılmaz. Kimlik: kaynak + medya türü + ID. Ayrı kaynakların aynı eseri göstermesi bilinçlidir; amaç kaynakların listelerini karşılaştırılabilir tutmaktır.
- Tüm şeritler, vitrin ve rastgele seçim yetişkin içerik filtresine uyar.

## Yükleme ve hatalar

Her kaynak ViewModel scope'unda bağımsız yüklenir. Başarılı kaynaklar diğerlerini beklemeden görünür. Kaynak başına 60 saniyelik coroutine zaman sınırı vardır; alttaki HTTP istemcilerinin kendi timeout davranışları da geçerlidir.

Bellek önbelleği kaynak geçişlerinde yeniden kullanılır. Zorunlu yenileme mevcut içeriği ekranda tutarak yeniden veri ister. Bir kaynak tamamen boş/hatalı dönerse eski bellek verisi, yoksa Room önbelleği denenir. Hata ve önbellek bilgisi ilgili kaynak başlığında gösterilir; “Yeniden dene” yalnızca o kaynağı yeniler. Hiçbir hata Tümü modundan otomatik çıkışa neden olmaz.

Kaynak değiştirme veya yenileme eski işleri iptal eder. İptal kontrolü ve istek nesli kontrolü eski sonuçların yeni ekrana yazmasını engeller. Ayar değişikliklerinde birleşik durum da temizlenir. Boş/hatalı sonuçlar başarılı önbellek gibi kaydedilmez.

Simkl listeleri ve Kitsu trend endpoint'i numaralı sayfalama sağlamadığından devam sayfasında aynı grafik tekrar tekrar eklenmez. Shikimori yayındaki anime devam sayfası artık Kitsu'ya gitmez. TMDB'nin en iyi dizi kategorisi de anime sezon filtresi açmaz.

## Doğrulama

`app/src/test/java/com/kitsugi/animelist/ui/screens/explore/AllSourcesExploreTest.kt`:

- 6 gerçek kaynak / ALL ayrımı;
- kaynak altında kategori gruplaması ve kaynak başına vitrin;
- ID çakışmaları, MAL/Jikan adları, yanlış kaynak fallback verisi;
- yetişkin filtresi, puan/popülerlik ayrımı, dizi/manga etiketleri;
- daraltılmış/boş gruplarda kaynak kaydırma indeksleri;
- bellek önbelleği, yenileme, boş cevap, disk fallback ve bozuk disk verisi;
- bağımsız kaynak tamamlanması, timeout ve iptal.

Sandbox'ta Kotlin 2.0.21 ile üretimdeki saf model/yükleme kodu ve aynı test gövdeleri izole JVM harness'inde çalıştırıldı: **18/18 geçti**. Harness, Android bağımlı model dosyalarından yalnızca gerçek `JikanSearchResult` ve `MediaType` tanımlarını aldı; JUnit assertion çağrılarını Kotlin test assertion'larına uyarladı. Bu kontrol bir Android/Compose derlemesinin yerine geçmez.

Tam Android derlemesi denendi ancak Gradle 8.13 indirmesi `services.gradle.org` TLS/erişim hatasıyla durdu. Android SDK/emülatör ve canlı sağlayıcı erişimi bu ortamda doğrulanamadı.

Android geliştirme ortamında:

```sh
./gradlew :app:testFossDebugUnitTest --tests '*AllSourcesExploreTest'
./gradlew :app:assembleFossDebug
```

Cihaz kontrolü: Tümü seçimi; 6 kaynak başlığı ve alt kategoriler; kaynak/kategori kısayolları; aç/daralt; “Tümünü Gör” ve geri dönüş; aşağı çekerek yenileme; uçak modu; tek sağlayıcı hatası/yeniden deneme; hızlı kaynak değiştirme; yetişkin filtresi; yatay telefon/tablet ve TV D-pad gezinmesi. TMDB içerikleri için geçerli API anahtarı ve sağlayıcılara ağ erişimi gerekir.
