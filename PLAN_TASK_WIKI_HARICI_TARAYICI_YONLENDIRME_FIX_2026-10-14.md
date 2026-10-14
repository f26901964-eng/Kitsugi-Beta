# Wiki bağlantılarında harici tarayıcıya çıkış düzeltmesi

Tarih: 2026-10-14

## Sorun ve neden
Wiki okuyucusunda bazı bağlantılar uygulama içinde açılırken bazıları harici tarayıcıya gönderiliyordu. `openFandomTarget`, yalnızca aynı wiki içindeki desteklenen makale bağlantılarını okuyucuya iletiyor; diğer hedeflerde `Intent.ACTION_VIEW` çalıştırıyordu. Dosya, kategori ve yardım gibi ad alanları makale ayrıştırıcısının dışında kaldığından bu yola düşüyordu. Ekran görüntüsündeki bağlantının kesin URL'si elde edilmedi; düzeltme ortak yönlendirme yolunu kapsar.

## Plan / görevler
- [x] Ortak okuyucu bağlantı yönlendirmesini incele.
- [x] Otomatik harici tarayıcı çağrısını kaldır.
- [x] Aynı wiki içindeki normal makaleleri mevcut yerel okuyucuda tut.
- [x] Desteklenmeyen makale türlerini ve diğer HTTP(S) hedeflerini uygulama içindeki KitsugiWebViewDialog ile aç.
- [x] Göreli, protokol-göreli, sorgu ve bölüm bağlantılarını HttpUrl.resolve ile çöz; dil yolu tekrarını önle.
- [x] Web dışı şemaları harici uygulamaya aktarmadan engelle.
- [x] Yönlendirme regresyon testlerini ekle.
- [x] git diff --check kontrolü.
- [ ] Gradle testlerini çalıştır: ortamda Java/JAVA_HOME bulunmadığından engellendi.
- [ ] Android derlemesi ve cihaz üzerinde doğrulama.

## Değişen dosyalar
1. app/src/main/java/com/kitsugi/animelist/data/fandom/FandomUrls.kt
   - ReaderLinkTarget ve readerLinkTarget ile ortak adres çözümleme/yönlendirme kararı.
2. app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomWikiReader.kt
   - Otomatik ACTION_VIEW çağrısının kaldırılması; uygulama içi web penceresi.
3. app/src/test/java/com/kitsugi/animelist/data/fandom/FandomUrlsTest.kt
   - Normal makale, File/Category/Help, başka wiki/site, sürüm/diff/düzenleme, bölüm bağlantısı, göreli adres ve web dışı şema testleri.

## Doğrulama
`git diff --check` başarılı.

Denenen komut:
```sh
bash ./gradlew :app:testDebugUnitTest --tests com.kitsugi.animelist.data.fandom.FandomUrlsTest
```
Sonuç: JAVA_HOME ayarlı değil ve java komutu yok; testler çalışmadı. Başarılı derleme veya cihaz testi iddia edilmemektedir. Paket APK içermez.

## Cihaz kontrol listesi
- Resident Evil Wiki ana sayfasındaki işaretlenen logo/kuruluş kartına dokun; harici tarayıcı açılmamalı.
- Normal oyun bağlantısı yerel okuyucuda açılmalı.
- Dosya/kategori/yardım ve başka site bağlantıları uygulama içindeki web penceresinde açılmalı.
- Web penceresini kapatınca kaynak okuyucu sayfası ve kaydırma konumu korunmalı.
- Dil önekli wiki ve // ile başlayan CDN bağlantıları doğru adrese gitmeli.
- Üst çubuktaki kullanıcının isteyerek bastığı tarayıcıda aç düğmesi bu değişikliğin kapsamında değildir.

## Paketi uygulama
ZIP içindeki dosyaları aynı yolları koruyarak proje köküne kopyalayın. Paket, üç değişen Kotlin dosyasının tam sürümlerini ve bu plan/görev belgesini içerir.
