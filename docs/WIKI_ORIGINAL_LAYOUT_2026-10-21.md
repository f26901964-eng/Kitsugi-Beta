# Wiki açılır sayfası: özgün düzen mimarisi

Tarih: 2026-10-21

> **İlk fazın tarihsel raporu.** Özgün görünümde çeviri ve reklam engelleme, sonraki çalışmada kodlandı.
> Güncel davranış, sınırlar ve testler: [DOM çevirisi ve reklam engelleme raporu](WIKI_DOM_TRANSLATION_ADBLOCK_2026-10-21.md).
> Aşağıdaki “henüz çeviri yok” ifadeleri yalnızca ilk fazın durumunu anlatır.

## Durum — tamamlanan ile doğrulanmayanı ayırıyoruz

**Kod uygulandı.** Üretim Kotlin kodu üzerinde 12 bağımsız JVM kontrolü ve üretimde kullanılan viewport JavaScript’i üzerinde 5 DOM testi geçti. Değişen Kotlin dosyalarının sözdizimi, TR/EN kaynakları ve `git diff --check` kontrol edildi.

**Android APK derlemesi ve cihazda görsel kabul testi tamamlanmadı.** Gradle dağıtımı bu ortamın ağ sınırları nedeniyle indirilemedi (`services.gradle.org`, SSL handshake hatası). Android SDK/emülatör de hazır değil. Fandom sitelerine bu ortamdan canlı erişim yok. Dolayısıyla bu rapor “bütün wikiler cihazda birebir doğrulandı” iddiasında bulunmaz.

## 1. Kök neden

Eski yol:

```text
MediaWiki API HTML’i
  → FandomHtmlText.blocks(...)
  → paragraf / liste / tablo satırı / görsel / bağlantı kartı
  → FandomWikiReader içindeki Compose bileşenleri
```

`FandomHtmlText` sade okuma için tasarlanmış: `style`, `script` ve bazı yapısal parçalar çıkarılıyor; tablo, liste ve görseller uygulamanın blok modeline indirgeniyor. Bu yol **bir web sayfası render motoru değil**. CSS grid/flex, tablo hücresi birleşimleri, float, TemplateStyles, ResourceLoader, tema değişkenleri ve site scriptleri bu modele taşınmıyor.

Ekran görüntülerindeki önemli farklar:

- WELCOME / SYNOPSIS gibi dekoratif harf başlıkları satır satır listeye dönüşmüş.
- Büyük görseller, yanına sarılan metinler ve karakter ızgaraları tek tip yatay kartlara dönüşmüş.
- Wikiye ait pembe çerçeve, renkler, görsel boyutları ve kolonlar kaybolmuş.
- Görünür/gizli veya alternatif içeriklerin birbirinden ayrılmaması tekrar üretebilir; geri sayımın scriptle seçilen durumları düz metne dönüşebilir. Bu iki semptomun canlı HTML üzerindeki tek tek kaynak düğümleri bu ortamda doğrulanmadı.

Bu kayıpları birkaç Compose renk/kenarlık ayarıyla geri almak mümkün değil. Yalnızca You-Zitsu’ya özel kurallar da diğer wikiler için genel çözüm olmaz.

## 2. Seçilen yaklaşım

### Varsayılan: Özgün sayfa

```text
Sekme adresi
  → gerçek HTTPS wiki URL’si
  → uygulama içindeki Android WebView
  → sitenin HTML + CSS + JS + görselleri
```

- Sayfa, aynı tam ekran wiki penceresinin içinde açılır. Başlangıçta harici tarayıcıya atılmaz.
- Yerel `file://` belge veya uydurma CSS ile giydirilmiş API HTML’i kullanılmaz.
- Wiki kendi tema renklerini, yazılarını, tablo/kolon düzenini, başlıklarını, görsel boyutlarını ve etkileşimlerini yönetir.
- Makale içerikleri yeniden kartlara bölünmez; site stilleri silinmez ve yerine uygulama teması zorlanmaz.
- Site CSS/JS/CDN isteklerine makale URL filtresi uygulanmaz. Aksi halde gerekli tema kaynakları da engellenirdi.
- Açılışta masaüstü düzeni seçilidir. Gerçek WebView sürümünü koruyan masaüstü UA ve 1200 CSS px viewport kullanılır. Bu, ekran görüntülerindeki geniş masaüstü düzenine yönelik bir tercihtir; ekran görüntüsüyle piksel eşitliği garantisi değildir.
- Mobil seçildiğinde sistem WebView UA’sı ve sayfanın kendi viewport’u kullanılır. Yakınlaştırma desteklenir.
- Masaüstü için yapılan tek DOM müdahalesi viewport meta bilgisidir. Makale düğümlerine, tema stillerine veya site olay işleyicilerine dokunulmaz.
- Android’in zorunlu koyulaştırması kapalıdır; aksi halde wiki renkleri ikinci kez değişirdi. Sitenin kendi açık/koyu tema seçimi ve sistem tercihleri yine site tarafından değerlendirilebilir.

### İsteğe bağlı: Sade okuyucu · Çeviri

- Mevcut Compose okuyucusu silinmedi.
- Mevcut çeviri tercihleri, bölüm çevirileri, orijinal/çeviri metin geçişi ve uygulama galerisi burada kullanılmaya devam eder.
- **Sade okuyucu özgün düzen garantisi vermez.** Eski ayrıştırıcıdaki dekoratif başlık/listelendirme sınırları bu çalışmada yeniden tasarlanmadı; varsayılan özgün yol bu ayrıştırıcıyı kullanmayarak sorunu mimari olarak aşar.
- Özgün görünüm kaynak sitenin dilindedir. Üst açıklama bunu belirtir ve çeviri için sade okuyucuyu gösterir. Kullanıcının çeviri tercihi sessizce değiştirilmez.
- Kategori/dosya/özel sayfa gibi makale olarak çözülemeyen bir adres için alakasız eski makaleye düşmek yerine sade okuyucunun o özel sayfayı desteklemediği bildirilir.

### Diğer yaklaşımlar neden seçilmedi?

| Yaklaşım | Sorun |
| --- | --- |
| Daha fazla Compose kart türü | Her wikinin özel CSS/JS semantiğini yeniden uygulamak gerekir; özgün tarayıcı davranışına genel olarak eşit olmaz. |
| API HTML’i + elle toplanan CSS | Tema değişkenleri, body sınıfları, ResourceLoader modülleri ve sayfa scriptlerinin çalışma bağlamı eksik kalabilir. İleride kontrollü içerik modu olabilir, birebir özgün mod diye sunulmamalı. |
| iframe | Mobil uygulamada gereksiz ek çerçeve katmanı; kaynak sitenin CSP/X-Frame kısıtlarıyla çatışabilir. |
| Her wikiyi elle taklit etmek | Site değişiklikleriyle bozulur ve binlerce wikiye ölçeklenmez. |
| Gerçek belgeyi uygulama içi web motorunda açmak | Özgün düzen için en doğrudan yol; bedeli site reklamları, izin ekranları, ağ yükü ve WebView uyumluluğudur. |

Yeni bir üçüncü taraf render kütüphanesi veya başka projeden kod kopyası eklenmedi. Platform WebView’i kullanılıyor.

## 3. Gezinme, yaşam döngüsü ve veri akışı

- Medya okuyucusu, karakter wiki slotu ve karakter detay sayfası aynı özgün/sade seçim sözleşmesine bağlandı.
- WebView açılması API’nin HTML ayrıştırmasının başarılı olmasına bağlı değil. Özgün modda view-model’ler sade sayfa/bölüm yükleme işlerini başlatmıyor. Native okuyucu seçildiğinde mevcut veri yolu devreye giriyor.
- Sayfa içi bağlantı, fragment ve JavaScript gezinmesi WebView’in kendi geçmişinde kalıyor. Üst geri/ileri kontrolleri önce bu geçmişi kullanıyor; gerektiğinde mevcut native gezinme geçmişine düşüyor.
- Adres değişikliği, uygulamadaki sekme kimliğini de güncelliyor. Başka wiki/dil sürümüne geçiş native okuyucuya doğru kaynakla taşınıyor.
- Eski veya kapatılmış sekmeden gelen callback aktif sekmenin adresini değiştiremez.
- Yeni sekme özgün modda URL’yi query/fragment dahil kopyalar, kaynak sekmenin geçmişini kopyalamaz.
- En fazla 12 sekme korunur. Aktif olmayan sekmeler için canlı WebView tutulmaz: geçmiş `Bundle`, adres, başlık ve kaydırma koordinatları saklanır.
- Sekme/mod değişiminde WebView bırakılır; yükleme durdurulur ve `destroy()` çağrılır. Uygulama arka plana geçince aktif görünüm duraklatılır.
- Kaydırma geri yüklemesi yükleme tamamlanınca uygulanır. Sonradan boyutu değişen reklam/lazy görseller konumu kaydırabilir; dinamik sayfalarda piksel hassasiyeti henüz cihazda doğrulanmadı.
- WebView motoru yoksa/kurulamıyorsa veya render süreci çökerse hata ve yeniden deneme/sade okuyucu seçenekleri bulunur. Tekrar deneme yeni motor oluşturabilir.
- Oturumdaki sekme geçmişi uygulama süreci öldürüldüğünde kalıcı değildir. Mevcut native controller’ın süreç içi oturum modeli korunmuştur.

## 4. Güvenlik sınırları

- Otomatik ana çerçeve gezinmesi yalnızca HTTPS Fandom/Wikia/Wikipedia kökleri ve gerçek alt alanlarında kalır.
- `fandom.com.evil.test` ve `evilfandom.com` wiki sayılmaz.
- Kullanıcı dokunuşuyla açılan diğer HTTPS bağlantılar önce onay ister; onaydan sonra harici tarayıcıya verilir.
- Otomatik dış yönlendirme, `intent:`, `javascript:`, `file:`, `content:`, `data:`, HTTP, kullanıcı bilgisi içeren ve olağandışı portlu ana çerçeve adresleri engellenir.
- `addJavascriptInterface` kullanılmaz. Uzak sayfa uygulamanın yerel fonksiyonlarına erişemez.
- Dosya/içerik erişimi, otomatik yeni pencere, konum ve otomatik medya oynatma kapalıdır. İzin ve dosya seçme istekleri varsayılan olarak kabul edilmez.
- Mixed content engellenir; Safe Browsing açık kalır; SSL hatalarında sertifika **atlanmaz**.
- Üçüncü taraf cookie kabulü bu WebView için kapalıdır. Birinci taraf depolama/çerezler site davranışı için kullanılabilir; mevcut global çerez tercihi zorla değiştirilmez.
- İndirme istekleri uygulamanın indirme motoruna otomatik aktarılmaz; kullanıcı isterse açık tarayıcı düğmesiyle devam eder.

## 5. Bilinen sınırlar / henüz yapılmayanlar

1. **Özgün DOM üzerinde çeviri yok.** Mevcut blok çevirisini özgün sayfaya yapıştırmak aynı yapısal kaybı yeniden üretir.
2. Sitenin reklam, çerez, hesap ve erişim doğrulama ekranları gelebilir. Bu çalışma reklam engelleyici veya erişim engeli aşma mekanizması değildir.
3. Brave/Chrome ile Android WebView aynı cookie profili, kullanıcı aracısı, reklam engelleme, CSS viewport ve sürüme sahip olmayabilir. Her durumda birebir piksel sonucu vaat edilmez.
4. Üçüncü taraf cookie gerektiren gömüler, hesap akışları, indirmeler, dosya yükleme ve video tam ekranı tam tarayıcıyla aynı kapsamda değildir. Harici tarayıcıya devam etme kontrolü korunur.
5. Görseller özgün modda sitenin kendi bağlantı/galeri davranışını kullanır; Kitsugi’nin yerel galerisi bu moda enjekte edilmedi.
6. Özgün site, sade okuyucudan daha ağır olabilir. Tek canlı WebView sınırı site içindeki reklam/script maliyetini ortadan kaldırmaz.
7. Diğer bağımsız wiki alan adları şu an otomatik uygulama içi güvenilir alan listesine eklenmez; dış bağlantı olarak onaylanır.
8. Yeni seçimler oturum kapsamındadır; kalıcı site bazlı görünüm tercihleri eklenmedi.

## 6. Doğrulama

### Çalıştırılan

- `scripts/tests/WikiOriginalPageChecks.kt`: Kotlin **2.0.21** + Java 21 ile üretim `WikiOriginalPagePolicy.kt` ve `FandomReaderBrowser.kt` derlendi, **12 kontrol geçti**.
  - Wiki/dil/legacy adresleri, sahte benzer alan adları, tehlikeli şemalar.
  - Gerçek Chromium sürümünü koruyan ve tekrar uygulandığında değişmeyen masaüstü UA dönüşümü.
  - Sekme adres senkronu, cross-wiki geçiş, geç callback, bağımsız sekmeler, native geçmiş ve 12 sekme sınırı.
- `scripts/tests/wiki-original-viewport.cjs`: jsdom **26.1.0**, **5 test geçti**.
  - Üretimdeki viewport script’i doğrudan Kotlin dosyasından okunur.
  - Tema CSS’i, makale DOM’u, harf başlığı, gizli alternatif içerik, resim ölçüleri, rowspan, fragment bağlantısı ve site click handler’ı korunur.
  - Script idempotenttir; viewport yoksa oluşturulur; kullanıcı yakınlaştırması kapatılmaz.
  - Bu fixture sentetiktir. jsdom sonucu Android render/piksel veya canlı Fandom testi değildir.
- Değişen Kotlin dosyalarında Tree-sitter sözdizimi kontrolü.
- XML ayrıştırma, yinelenen kaynak adı kontrolü ve 9 yeni TR/EN kaynağın eşleşmesi.
- `git diff --check`.

### Eklenen ama Android/Gradle altında çalıştırılamayan

- `WikiOriginalPagePolicyTest` JUnit testleri.
- `FandomReaderBrowserTest` içine web adres senkronu / geç callback / native geçmiş regresyon testleri.
- `bash gradlew :app:compileDebugKotlin --console=plain` denendi; Gradle dağıtımı indirilirken SSL handshake hatasıyla durdu. APK üretilmedi.

### Bağımsız testleri tekrar çalıştırma

Kotlin 2.0.21 ve Java 21 kurulu bir ortamda:

```bash
mkdir -p .cache/wiki-checks
kotlinc \
  app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/WikiOriginalPagePolicy.kt \
  app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomReaderBrowser.kt \
  scripts/tests/WikiOriginalPageChecks.kt \
  -include-runtime -d .cache/wiki-checks/wiki-checks.jar
java -jar .cache/wiki-checks/wiki-checks.jar

npm install --prefix .cache/wiki-checks --no-save --package-lock=false jsdom@26.1.0
NODE_PATH=.cache/wiki-checks/node_modules node --test scripts/tests/wiki-original-viewport.cjs
```

Yerel doğrulama için indirilen araçlar `.cache` / `.venv` altındadır; Git değişikliklerine dahil değildir.

## 7. Cihazda kabul matrisi — henüz tamamlanmadı

Karşılaştırmalar aynı Android WebView/Chrome sürümü, site tema tercihi, dil ve viewport ile yapılmalı. Tarayıcı araç çubukları değil makale alanı karşılaştırılmalı.

| Sayfa grubu | Kontrol |
| --- | --- |
| You-Zitsu ana sayfa | WELCOME/SYNOPSIS başlıkları, pembe kutular, geri sayım, geniş görseller, karakter ızgarası, Featured Article, yayın sekmeleri |
| You-Zitsu karakter | infobox, metin/görsel sarılması, içerik başlıkları, popover, sayfa içi bağlantı |
| One Piece / Naruto / Jujutsu Kaisen ana sayfaları | birbirinden farklı tema, kolon, tablo ve navigasyon kutuları |
| TR alt dizinli Fandom | kök/dil geçişi, göreli bağlantı, uygulamadaki kaynak kimliği |
| Türkçe/İngilizce Wikipedia | native okuyucuya doğru maddeyle geçiş, görsel/infobox düzeni |
| Dosya/kategori/özel sayfalar | özgün modda kalma; sade okuyucu destek sınırının doğru açıklanması |

Her biri için:

- [ ] İlk açılış, özgün → sade → özgün geçişi, çeviri tercihlerinin korunması.
- [ ] Mobil/masaüstü geçişi ve pinch zoom.
- [ ] Link, `#fragment`, JS tab/accordion, geri/ileri; ekran döndürme.
- [ ] Yeni sekme, sekme değişimi, kapatma, diğerlerini kapatma; 12 sekme sınırı.
- [ ] Sekmeye dönüşte kaydırma; uzun sayfada lazy görseller.
- [ ] İnternet yok, 403, 404, SSL hatası, doğrulama ekranı, retry.
- [ ] Arka plan/ön plan ve WebView render sürecinin sonlandırılması.
- [ ] TV/kumanda ve ekran okuyucu ile kontrol erişilebilirliği.
- [ ] Dış bağlantı onayı, indirme, giriş akışı ve harici tarayıcıya devam etme.
- [ ] Bellek profili: sekme değişimlerinden sonra bırakılmış WebView örnekleri birikmemeli.

## 8. Sonraki aşama: özgün düzeni koruyan çeviri

Doğru takip işi, **HTML’i tekrar sade bloklara çevirmek değil**, mevcut DOM içindeki uygun metin düğümlerini yerinde çevirmek:

1. Yalnızca makale kökünü seç; script/style/input/code ve gizli/dekoratif düğümleri ayır.
2. Değişmeyen düğüm kimliği + sayfa/gezinti nesli üret; istekleri görünür içerik öncelikli ve sınırlı paketlerle yap.
3. Dönen sonucu HTML olarak değil güvenli metin olarak yalnızca hedef text node’a yaz. Bağlantı, görsel, class, tablo ve event handler değişmesin.
4. Orijinal metinleri sakla; tam orijinale dönüş ağsız ve geri alınabilir olsun.
5. Dinamik DOM değişikliklerini sınırlı MutationObserver ile takip et; observer/çeviri geri besleme döngüsünü önle.
6. Gezinme, sekme değişimi veya çeviri tercihi değişiminde eski işleri iptal et; başka belgeye sonuç yazma.
7. Mevcut çevirmen, hedef dil, gizlilik ve otomatik çeviri tercihleriyle bağla.
8. Görünür okuma konumunu düğüm/anchor bazında koru. Çevrilen metin uzayabileceğinden aynı piksel yüksekliği değil **yapısal sadakat** hedeflenmeli.

Bu aşama bu değişiklikte uygulanmadı. Önce özgün render yolunun cihaz kabul matrisi tamamlanmalı; ardından DOM çevirisi ayrı test edilebilir bir katman olarak eklenmeli.
