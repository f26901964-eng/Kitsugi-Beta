# Plan / Task — Özgün wiki, sayfa içi çeviri, reklam engeli ve kullanılabilirlik

Tarih: 2026-10-21
Depo: f26901964-eng/Kitsugi-Beta
Çalışma dalı: arena/9615ba68-kitsugi-beta
Baz commit: 7db552159f1c581cd2c7d379e04faba801617c19

## Amaç ve kapsam

Fandom/Wikia/Wikipedia sayfalarını özgün yerleşim, görsel, tablo ve bağlantılarıyla uygulama içi okuyucuda göstermek; otomatik çeviriyi aynı özgün sayfa üzerinde sunmak; açılıp kapatılabilir reklam engeli ve okunabilirlik araçları sağlamak. Kaynak görünümünü tek bir wikiye benzeten sabit şablon veya toplu resim tersleme kullanılmaz.

## Uygulanan işler

- [x] Gerçek kaynak HTTPS belgesini WebView'da gösteren özgün görünüm; isteğe bağlı sade okuyucu.
- [x] Mobil/masaüstü görünümü, sekme/geçmiş, geri/ileri, dış bağlantı onayı ve yükleme/hata yönetimi.
- [x] Kaynak/ hedef dil ve otomatik çeviri ayarlarının özgün sayfaya bağlanması.
- [x] Makale metin düğümleri üzerinde çeviri; öğe, görsel, bağlantı ve olay işleyicilerini değiştirmeden orijinale dönüş.
- [x] Sınırlı çeviri paketleri, iptal/geç sonuç kontrolleri, hassas düzenleme alanlarının korunması ve kısmi hata uyarıları.
- [x] Açılıp kapatılabilen host tabanlı reklam engeli ve dar kapsamlı kozmetik kurallar; yerel wiki istisnaları.
- [x] Sabit sürümlü StevenBlack kendi katkı listesi, MIT lisansı, kaynak manifesti ve checksum güncelleme aracı.
- [x] SITE / APP / LIGHT / DARK tema tercihi; desteklenen kaynak tema sınıflarını kullanma ve geri alma.
- [x] Desteklenmeyen tema sözleşmeleri için uyarı; resimlere renk tersleme uygulanmaması.
- [x] Yüzde 80–180 yazı boyutu, sistem yazı ölçeği ve varsayılana dönüş.
- [x] Sayfada arama, sonuç sayacı, önceki/sonraki eşleşme, çeviri/orijinal geçişinde yenileme.
- [x] Kompakt araç menüsü, açık/koyu araç renkleri, kaydırılabilir ayar penceresi ve klavye boşluğu.
- [x] Türkçe/İngilizce kullanıcı metinleri ve yerel tercihlerin saklanması.
- [x] Ayrıntılı teknik raporlar, çevrimdışı testler ve Gradle hattı için JUnit regresyon testleri.

## Doğrulananlar

Toplam **78 çevrimdışı kontrol/test geçti**:

| Grup | Adet |
| --- | ---: |
| URL / geçmiş / sekme politikaları — JVM | 12 |
| Reklam / çeviri paketleri / tercih politikaları — JVM | 19 |
| DOM çeviri ve kozmetik kurallar — jsdom | 26 |
| Masaüstü viewport — jsdom | 5 |
| Görünüm ve yazı ölçeği politikası — JVM | 6 |
| Tema sınıfları ve restorasyon — jsdom | 10 |

Kotlin sözdizimi, JS sözdizimi, XML/TR-EN kaynak-format eşleşmesi, filtre/lisans checksum ve `git diff --check` kontrolleri de geçti.

**Bu sonuçlar Android/Compose derlemesi veya gerçek cihaz doğrulaması değildir.** JUnit test dosyaları eklendi ancak Gradle/JUnit çalıştırılmadı; 78 sayısına dahil değiller.

## Kalan görevler — tamamlanmadı

- [ ] Android SDK/Gradle erişimi hazır ortamda `:app:compileDebugKotlin`, birim testler ve debug APK üretimi.
- [ ] APK kurulumu ve You-Zitsu + farklı Fandom wikileri + Wikipedia üzerinde canlı görsel karşılaştırma.
- [ ] SITE/APP/LIGHT/DARK geçişleri; uygulama ve sistem teması farklıyken davranış; özel/eski skin uyarısı.
- [ ] 320 dp genişlik, yatay ekran, klavye ve büyük sistem yazısında yerleşim/erişilebilirlik testi.
- [ ] Canlı çeviri kalitesi, servis erişimi/kota hataları, kaynak/hedef dil değişimi ve orijinale dönüş.
- [ ] Arama sırasında hızlı sorgu/çeviri değişimleri, eşleşme sayacı, geri tuşu ve sekme değişimleri.
- [ ] Canlı reklam engeli, anti-adblock kurtarma, CSS/CDN/görsellerin korunması.
- [ ] Arka plan/dönüş, renderer kaybı, uzun sayfada bellek/CPU profili, TalkBack ve D-pad testleri.

Mevcut engel: önceki Gradle 8.13 indirmesi SSLHandshakeException/EOF ile derleme başlamadan durdu; ortamda Android SDK hazır değil. APK üretilmedi.

## Bilinen sınırlar

- Tema desteği kaynak skinin sınıf/CSS sözleşmesine bağlıdır; bütün wikilerde kusursuz koyu tema veya piksel eşitliği garantisi yoktur.
- Yazı büyütme ve çeviri metni uzaması sayfayı yeniden akıtabilir.
- Reklam engeli tam ABP/EasyList/AdBlocker Ultimate motoru değildir; bazı reklam/servis worker/ilk taraf yolları kaçabilir.
- Görsellerdeki yazılar için OCR, çevrimdışı sayfa indirme ve evrensel özel-skin tema motoru eklenmedi.
- Yerel WebView aramasında sorgu kimliği içeren sonuç API'si yoktur; hızlı değişimlerde geçici sayaç/aktif eşleşme farkları cihazda doğrulanmalıdır.

## Ayrıntılı raporlar

- `docs/WIKI_ORIGINAL_LAYOUT_2026-10-21.md` — ilk özgün görünüm aşaması (tarihsel).
- `docs/WIKI_DOM_TRANSLATION_ADBLOCK_2026-10-21.md` — çeviri, reklam engeli, lisans ve kabul listesi.
- `docs/WIKI_READING_USABILITY_2026-10-21.md` — tema, yazı boyutu, arama, güncel test sonucu ve açık kabul listesi.

## Teslim paketi

ZIP, baz committen bu oturum boyunca değişen ve yeni eklenen dosyaların son hallerini depo içindeki yollarını koruyarak içerir. Tam depo veya APK değildir. Bu plan/task dosyası, teknik raporlar, testler, gerekli wiki assetleri ve lisans dosyaları dahildir. Paket içindeki `PACKAGE_MANIFEST.json` dosya listesini ve SHA-256 özetlerini içerir.

Uygulamadan önce hedef projeyi yedekleyin; farklı bir sürüme uygulanacaksa dosyaları karşılaştırarak birleştirin. Aynı baz sürümde dosyalar proje köküne yolları korunarak aktarılabilir. `.git`, derleme çıktıları, araç indirmeleri, önbellekler, yerel yapılandırmalar ve kimlik bilgileri pakete dahil edilmez.
