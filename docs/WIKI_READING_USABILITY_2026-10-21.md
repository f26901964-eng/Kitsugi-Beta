# Wiki okuyucu — tema ve kullanılabilirlik denetimi

Tarih: 2026-10-21

Önceki çalışma: [Sayfa içinde çeviri ve reklam engelleme](WIKI_DOM_TRANSLATION_ADBLOCK_2026-10-21.md).

## Durum

**“Eksik kalmadı” veya “cihazda sorunsuz çalışıyor” sonucu çıkarılmadı.** Bu aşama kaynak uygulaması ve çevrimdışı regresyon doğrulamasıdır. Android/Compose tam derlemesi, APK, emülatör ve gerçek cihaz testi yapılmadı. Önceki derleme denemesi Gradle 8.13 dağıtımını indirirken SSLHandshakeException/EOF ile durdu; Android SDK da bu ortamda hazır değil. Kotlin sözdizimi kontrolü tip/API çözümlemesi veya Compose derlemesi değildir.

## Tespit edilip ele alınan eksikler

### 1. Kaynak görünümünü bozmayan tema tercihi

- Varsayılan **Site teması / SITE**: kaynak wiki görünümü korunur. Uygulama koyu olsa bile açık bir kaynak sayfa açık kalabilir; bu bilinçli bir özgünlük tercihidir.
- **Uygulamayı izle / APP**: sistemin açık/koyu bayrağını değil, Kitsugi'nin etkin arka plan paletini esas alır. Böylece uygulamanın bağımsız tema seçimi dikkate alınır.
- **Açık / LIGHT**, **Koyu / DARK**: açık kullanıcı tercihi.
- Fandom/Wikia'nın bilinen `theme-fandomdesktop-*` ve modern Wikipedia'nın `skin-theme-clientpref-*` sınıfları kullanılır. Bu sözleşmeleri taşımayan özel/eski görünümlere genel CSS zorlanmaz; kullanıcıya destek/uygulama sınırlaması gösterilir.
- Resim tersleme, filtre, yeni makale kabı veya toplu renk ezme yok. Tema sınıfları ve kök `color-scheme` dışında içerik değiştirilmez. SITE seçilince saklanan kaynak tema sınıfları ve kök renk şeması geri gelir; ilgisiz sınıflar korunur.
- Site çerezlerine/localStorage'a tema yazılmaz. Seçim uygulamanın yerel DataStore'unda saklanır, tüm wiki sayfalarına uygulanır.
- Araç çipleri, arama alanı, menü ve ayar penceresi uygulama paletine bağlandı. Ana eylem/metin kontrastı özel vurgu renginden bağımsızlaştırıldı; ayar pencerelerinde platform Force Dark koruması kullanıldı.
- WebView ve kapsayıcısının ilk boş zemini uygulama rengine ayarlandı; belge hazır olduğunda hesaplanmış opak kaynak arka planı kullanılır. **Uzak HTML/CSS yüklenirken beyaz parlamanın tamamen yok edildiği iddia edilmiyor.**

### 2. Yazı boyutu ve erişilebilirlik

- Yüzde 80–180, 10 puanlık adımlar; açık yüzde etiketi ve özgün ayarlara dönüş.
- Sistem yazı ölçeği de dikkate alınır. WebView motoruna verilen birleşik değer güvenlik için yüzde 50–300 arasında tutulur; geçersiz/sonsuz ölçekler güvenle varsayılana döner.
- Uygulama, WebView'ı yeniden yaratmadan `settings.textZoom` değiştirir; çeviri anahtarı, manuel orijinal seçimi ve reklam ayarı değişmez.
- Büyük yazı tablo/kutu yerleşimini değiştirebilir; sabit boyutlu kaynak öğeler değişmeyebilir. Bunlar ayar ekranında açıklanır. Mevcut iki parmakla yakınlaştırma korunur.

### 3. Sayfa içinde arama

- Yerel `findAllAsync` / `findNext`: sorgu, eşleşme sayacı, önceki/sonraki ve kapatma.
- Arama için DOM öğeleri veya bağlantılar değiştirilmez; çeviriden sonra görünen metin aranır.
- 256 karakter sınırı, sorguda 300 ms bekleme. DOM çevirisi/orijinale dönüş sonrası sonuç yenilemeleri birleştirilir; içerik değişimi kaynaklı tarama en fazla 600 ms'de bir yapılır. Sürekli hızlı çeviri aramayı süresiz ertelemez.
- Sayfa geçişinde temizlenir; WebView bırakılırken dinleyici ayrılır. Sistem geri/diyalog kapatma isteği, açık arama varken önce aramayı kapatır; üstteki okuyucu kapatma düğmesi okuyucuyu kapatmaya devam eder.
- Tam genişlikte giriş ve ayrı gezinme satırı, dar ekranda metin alanının üç ikon tarafından ezilmesini önler. IME alanı için okuyucuya klavye padding'i eklendi.
- Yerel WebView araması sorgu kimliği taşıyan bir sonuç API'si sunmaz. Çok hızlı sorgu değişiminde geçici eski sayaç/yer değişimi ihtimali ve çeviri sürerken aktif eşleşmenin değişmesi cihazda sınanmalıdır. Kapalı wiki sekmeleri, henüz yüklenmemiş içerik ve resimdeki yazılar için arama garantisi yoktur.

### 4. Daha sade araç alanı

- Çeviri/orijinal ve reklam engeli ana eylemleri görünür; ikincil eylemler üç nokta menüsünde.
- Ana çiplerde ağırlıklı genişlik, en az 48 dp yükseklik, iki satır ve gerektiğinde kısaltma.
- Kaydırılabilir görünüm penceresi: tema, boyut, sıfırla, uygula. Tercihler atomik kaydedilir; kayıt başarısızlığı sessizce yutulmaz.
- Kaynak/lisans bilgisi ve otomatik çeviri ayarına dönüş menüden erişilebilir kalır.
- Yeni kullanıcı metinleri Türkçe ve İngilizce eklendi.

## Doğrulama

Gerçek üretim politika sınıfları ve paketlenmiş JavaScript dosyaları kullanıldı:

| Grup | Sonuç |
|---|---:|
| Wiki URL, geçmiş, sekme politikaları (JVM) | 12 geçti |
| Reklam, çeviri paketleri ve tercih politikaları (JVM) | 19 geçti |
| DOM çeviri / kozmetik reklam scriptleri (jsdom) | 26 geçti |
| Masaüstü viewport scripti (jsdom) | 5 geçti |
| Yeni görünüm/yazı politikası (JVM) | 6 geçti |
| Yeni kaynak tema sınıfları / restorasyon (jsdom) | 10 geçti |
| **Toplam** | **78 geçti** |

Ek kontroller: Kotlin sözdizimi, TR/EN XML kaynak ve format eşleşmesi, JS sözdizimi, filtre/lisans checksum, `git diff --check`. Yeni `WikiReadingAppearanceTest` JUnit testleri eklendi; **Gradle/JUnit çalıştırılmadı**, yukarıdaki 78'e dahil değil.

Yeni kontrolleri tekrar çalıştırma (Java 21, Kotlin 2.0.21, jsdom 26.1.0):

```bash
kotlinc \
  app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/WikiReadingAppearance.kt \
  app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/WikiPageToolPolicy.kt \
  scripts/tests/WikiReadingAppearanceChecks.kt \
  -include-runtime -d .cache/wiki-checks/wiki-appearance.jar
java -jar .cache/wiki-checks/wiki-appearance.jar
NODE_PATH=.cache/wiki-checks/node_modules node --test \
  scripts/tests/wiki-dom-tools.cjs \
  scripts/tests/wiki-original-viewport.cjs \
  scripts/tests/wiki-page-appearance.cjs
```

jsdom kontrolleri sınıf/DOM/kimlik/olay işleyicisi/restorasyon sözleşmesini doğrular; gerçek Fandom veya Wikipedia CSS'inin ekrandaki renklerini doğrulamaz.

## Açık kabul testleri — henüz yapılmadı

- [ ] Android debug derlemesi ve APK kurulumu; Compose API/tip doğrulaması.
- [ ] You-Zitsu, farklı renkli en az iki Fandom, modern Wikipedia ve eski/özel skin: SITE/APP/LIGHT/DARK geçişleri, infobox/tablo/görseller, uyarı.
- [ ] Uygulama açık/koyu/AMOLED, sistemden farklı tema, beyaz/siyah özel vurgu: araçlar, menü, ayar penceresi ve klavye kontrastı.
- [ ] Tercihlerden SITE'a dönüş, sayfa yeniden yükleme, sekme değişimi, site kendi tema kontrolünün kullanımı. Site scripti sonradan sınıfları değiştirebilir; sürekli zorlayıcı gözlemci eklenmedi.
- [ ] Sistem yazısı yüzde 100/150/200; wiki yazısı yüzde 80/100/180; 320 dp genişlik, yatay ekran ve klavye. Görsel taşma/yerleşim garantisi henüz yok.
- [ ] Arama: Türkçe karakter, sonuç yok, uzun sorgu, hızlı yaz/sil, önceki/sonraki, çeviri sürerken arama, orijinale dönme, geri tuşu, sekme kapatma ve renderer kaybı.
- [ ] TalkBack odak sırası, D-pad, ekran okuyucu etiketleri ve minimum dokunma hedefleri cihaz üzerinde.
- [ ] DataStore hata/retry, yeniden başlatma sonrası tercih, arka plana geçiş/dönüş, uzun sayfada performans.
- [ ] Önceki rapordaki canlı çeviri, reklam engeli ve kaynak görsel sadakat kabul listesi.

Çevrimdışı sayfa indirme, OCR, evrensel özel-skin koyu tema motoru ve tam tarayıcı/AdBlocker Ultimate eşdeğeri bu çalışmaya eklenmedi. Kapsamı genişletip kaynak görünümünü bozmak yerine mevcut okuyucunun kontrollü ve geri alınabilir araçları iyileştirildi.
