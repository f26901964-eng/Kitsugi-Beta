# Keşfet vitrini — yatay görsel ve kararlı kaydırma

## Seçim sözleşmesi

- **Tümü:** mevcut her **kaynak × bölüm** için bir rastgele öğe.
- **Tek kaynak:** mevcut her bölüm için iki rastgele öğe.
- Ortak **Yakında Yayında** havuzu Tümü modunda bir kez eklenir; tek kaynakta o kaynağın geri sayım havuzu kullanılır.
- Popüler, trend, en yüksek puanlı, yayındaki, yaklaşan, film, sezon, yeni eklenen ve kaynakların dizi/film rafları adaydır. Eski 12/10 toplam sınırı kaldırıldı.
- Manga, manhwa/manhua ve romanlar vitrinde yoktur; katalog rafları silinmez. Eleme gerçek medya türüne göre yapılır: TMDB/Simkl'in video taşıyan eski `*_MANGA` alanları yanlışlıkla elenmez.
- Aynı kaynak/tür/kimlik bir kez gösterilir. Ortak öğeler için alternatif ataması yapılır; bir raf diğerini gereksiz yere boş bırakmaz. Yeterli benzersiz/yatay görselli aday yoksa eksik kontenjan başka rafa aktarılmaz, posterle doldurulmaz.
- Rastgele sıra yenileme/kaynak seçimi için sabittir; recomposition, logo yüklenmesi ve ekran döndürme yeniden kura çekmez. Geç tamamlanan kaynaklar mevcut sayfa sırasını bozmaz.

## Görsel kabulü

`HomeHeroArtworkLoader` önce kaynağın yatay görselini, gerekirse ortak TMDB/Fanart çözümünü dener. Aday küçük bir Coil decode işleminden geçirilir: gerçek genişlik yükseklikten büyük olmalı, görsel yüklenebilmelidir. Poster URL'si, kare/dikey görsel veya yüklenmeyen görsel vitrinde yer almaz. Başarısız adaydan sonra aynı bölümdeki diğer rastgele adaylar denenir.

İstekler en fazla üç eşzamanlı iş ile yürür, ortak adaylar tekrar çözülmez ve eski kaynak isteği iptal edildiğinde sonucu yeni seçime yazamaz. Kabul edilen görsel URL'si sayfa açıkken değiştirilmez; sağlayıcı kimlikleri ve kütüphane posteri korunur. Bangumi kaynağına ait geçerli TMDB/Fanart yatay görsellerini yanlışlıkla reddeden kaynak-adı kontrolü de düzeltildi.

## Kaydırma / yerleşim

- Görünmez pager + özel `dispatchRawDelta` sürüklemesi + üst üste görsel/metin katmanları kaldırıldı.
- Gerçek `HorizontalPager`: her sayfanın resmi, başlığı, logosu ve metası birlikte kayar; kararlı kaynak/tür/kimlik anahtarı kullanılır.
- Bant yüksekliği yalnızca cihaz geometrisine bağlıdır. Görsel oranına bağlı yükseklik animasyonu ve poster/backdrop mod değişimi yoktur. Shimmer aynı yüksekliği kullanır.
- Yatay görsel sabit `Fit` katmanında tam görünür; arkadaki düşük opaklıklı dolgu boş alanı kapatır. Görsel yüklenmesi kırpma/hizalamayı değiştirmez.
- Logo ve metin aynı sabit başlık yuvasını paylaşır. Logo başarıyla yüklenene kadar metin görünür; başarısız logo boş başlık bırakmaz. Alt başlık ve meta yuvaları da sabittir.
- Veri güncellemeleri sürükleme/snap bitene kadar bekler. Otomatik geçiş dokunma, sürükleme ve TV odağı sırasında durur; sonda tüm listeyi hızla başa sarmak yerine tek sayfalık ters yöne devam eder.
- Gösterge en fazla yedi okunabilir bölüm gösterir; onlarca öğe telefonda birer piksele sıkışmaz.
- Detay sayfalarının bağımsız poster/galeri davranışı korunmuştur; eski, kullanılmayan ana sayfa poster/yükseklik politikası kaldırılmıştır.

## Doğrulama

- Kotlin 2.0.21 / JRE 21 / gerçek JUnit 4.13.2 ile SDK'sız sözleşme paketi: **66 test başarılı**.
- `scripts/run_hero_contract_checks.sh`: gerçek seçim/raf/politika kodu ve JUnit testleri; Android ve Compose değer tipleri için küçük derleme shim'leri kullanır. **Gerçek Compose çizimini, Coil ağ/decode entegrasyonunu veya ViewModel'i çalıştırmaz.**
- Yedi cihaz testi eklendi: dikey/yatay kaydırmada sabit yükseklik, kaydırma sonrası doğru öğeye tıklama, başlık dokunuşunun recomposition ile iptal olmaması, ilk sayfada geri sarmama, logo yükleme/hata yuvası, yeni kaynak eklendiğinde etkin anahtarın korunması.
- Mevcut Android build workflow'una ilgili JVM testleri eklendi.
- Tam Android derleme denemesi Gradle dağıtımı indirilirken ağ/TLS engeline takıldı. Android SDK/emülatörüyle cihaz testleri bu ortamda çalıştırılmadı.

Android geliştirme ortamında:

```bash
./gradlew :app:testFossDebugUnitTest \
  --tests 'com.kitsugi.animelist.ui.screens.explore.HeroSelectionTest' \
  --tests 'com.kitsugi.animelist.ui.screens.explore.AllSourcesExploreTest' \
  --tests 'com.kitsugi.animelist.ui.components.HomeHeroPolicyTest' \
  --tests 'com.kitsugi.animelist.ui.components.HeroImageSelectionTest' \
  --tests 'com.kitsugi.animelist.ui.components.HomeHeroGestureModifiersTest'

./gradlew :app:connectedFossDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kitsugi.animelist.ui.components.KitsugiHeroSectionTest
```
