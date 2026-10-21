# Özgün wiki sayfasında yerinde çeviri ve reklam engelleme

Tarih: 2026-10-21

## Durum

**Uygulama koduna bağlandı:** Özgün WebView görünümünden çıkmadan, mevcut otomatik çeviri ayarı ve kaynak/hedef dil tercihiyle makale metinlerini yerinde çevirme; orijinale dönüş; wiki alan adına göre kalıcı reklam engelleme aç/kapat kontrolü.

**Henüz cihazda doğrulanmış bir APK değildir.** Android derlemesi yeniden denendi; Gradle 8.13 dağıtımı `services.gradle.org` adresinden indirilirken ağ/SSL handshake hatasıyla durdu. Bu ortamda Fandom/Google uçlarına canlı erişim ve Android emülatör testi yapılamadı. Aşağıdaki otomatik testler gerçek üretim algoritmalarını/JS dosyalarını çalıştırır fakat Android görsel/kullanıcı deneyimi ve canlı sağlayıcı doğrulamasının yerine geçmez.

Bu belge [ilk özgün render çalışmasının](WIKI_ORIGINAL_LAYOUT_2026-10-21.md) devamıdır. Önceki rapordaki “özgün DOM üzerinde çeviri henüz yok” sınırı artık bu kod çalışmasıyla ele alınmıştır.

## 1. Kullanıcıya görünen davranış

- Uygulamadaki **otomatik çeviri açıksa**, özgün wiki görünümünde makale alanı çevrilmeye başlanır. Kapalıysa kullanıcı **Sayfayı çevir** ile başlatabilir.
- Kaynak/hedef dil `SettingsDataStore` akışından alınır. Dil/otomatik çeviri tercihi değişince eski manuel görünüm tercihi sıfırlanır ve yeni ayar uygulanır.
- **Orijinali göster**, aynı belgedeki orijinal metinleri ağ isteği olmadan geri koyar. Yeniden çevirili görünüme geçiş aynı DOM içindeki önbelleği kullanır.
- **Otomatik ayarı izle**, o sayfa için yapılan manuel geçişi bırakır; global tercihi değiştirmez.
- Yeni makaleye gezinildiğinde otomatik ayar yeniden uygulanır. Aynı sayfayı yeniden yüklemek veya sekmesini çoğaltmak mevcut manuel tercihi korur.
- Görünen durumu saklamamak için hedef dil, çevrilen metin sayısı, çevirinin sürüp sürmediği, kısmi hata ve güvenli sınır uyarıları bulunur. Sayfa tamamen çevrilmiş gibi yanıltıcı bir “%100” mesajı gösterilmez.
- **Reklam engeli** ilk kullanımda açıktır. Anahtar bu wiki **host’u** için açıp kapatır. Tercih yerel DataStore’da saklanır; uygulama kapanınca kaybolmaz. Aynı host’un dil alt dizinleri aynı tercihi paylaşır.
- Engelleme değiştirildiğinde sayfa yeniden yüklenir: önceden engellenmiş bir script yalnızca CSS’i kaldırarak geri getirilemez. Kaydırma koordinatları geri yüklenmeye çalışılır.
- Butondaki sayı engellenen **kaynak isteklerinin** sayısıdır; benzersiz reklam veya gizlenen kutu sayısı değildir.
- “Sayfa araçları hakkında” içinde veri paylaşımı, kapsam, filtre sürümü ve tam MIT lisansı gösterilir.
- Sade okuyucu hâlâ isteğe bağlıdır; çeviri için ona geçmek artık zorunlu değildir.

## 2. Çeviri servisi ve gizlilik

Uygulamanın mevcut otomatik çeviri altyapısı `TranslationManager` ile Google servisini kullanıyordu. Yeni katman da aynı servis, coroutine istek kapısı ve Room önbelleğine bağlandı.

- `preferredTranslator` mevcut projede Google/DeepL/Translate You **harici uygulama intent** tercihidir; arka planda kullanılabilir çoklu sağlayıcı API’si değildir. Bu tercih sessizce değiştirilmedi ve DeepL seçiliyken arka planda DeepL çalışıyor gibi gösterilmedi. Araç durumunda Google adı açıkça görünür.
- Yeni `translateWebPageText` metni BBCode temizleyicisinden geçirmez; literal web metnini korur. Servis başarısızlığını `null` ile bildirir; ağ hatasında orijinal metnin dönmesini “başarılı çeviri” saymaz.
- Uzun metinde maskeleme/korunan-token kontrolü parça bazındadır. Başarısız parça varsa o metin düğümü bütünüyle orijinal kalır.
- Çeviri açıkken görünür/yakın makale metinleri mevcut Google servisine gönderilir. Form, input, textarea, düzenlenebilir alan, textbox, script/style/code ve gizli alternatif içerik gönderilmez.
- Link hedefleri, resim URL’leri ve sayfanın HTML belgesi çeviri için toplanmaz. Yalnızca uygun metin düğümleri işlenir.
- `addJavascriptInterface` eklenmedi. Uzak sayfa yerel API’leri çağıramaz; native taraf sınırlı `evaluateJavascript` sonuçlarını kendisi çeker.
- Ayarlar okunamazsa otomatik çeviri kapalı tutulur ve hata bildirilir; reklam engeli varsayılan açık durumuna döner. Ayarları tekrar yükleme kontrolü vardır.
- Yeni ayrı/sınırsız disk önbelleği oluşturulmadı. Mevcut `translation_cache` tablosu, yeni `webdom-v1` anahtar alanı ve mevcut StorageJanitor temizliği kullanılır.

## 3. DOM yapısının korunması

`assets/wiki/dom-translator.js` şu modeli uygular:

```text
Makale kökü (.mw-parser-output / #mw-content-text)
  → sınırlı TreeWalker taraması
  → görünür/yakın, uygun Text düğümleri
  → {düğüm id, revizyon, kaynak metin}
  → Kotlin çeviri paketi
  → token/sıra doğrulaması
  → aynı Text.nodeValue alanına güvenli metin yazımı
```

**Yapılmayanlar:** `innerHTML` ile yeniden çizim, öğe silme/yeniden oluşturma, bağlantı hedefini değiştirme, resim veya tabloyu kartlara çevirme, site event listener’larını yeniden bağlama, kaynak CSS’i silme.

Yakın metin yuvaları `⟪K0⟫…⟪/K0⟫` benzeri kimliklerle bir isteğe paketlenebilir. Dönen işaretlerin sayısı/sırası/içeriği doğrulanmadan hiçbir sonuç düğümlere dağıtılmaz. İşaretler bozulursa sınırlı tekil çeviriye düşülür. Servis tamamen erişilemiyorsa sekiz ayrı isteğe açılarak hata büyütülmez.

### Gezinme ve dinamik içerik

- Her yeni belge için ayrı kimlik, her kaynak metin değişikliği için revizyon tutulur.
- Gezinti, görünüm/dil değişikliği, kapatma ve arka plana geçiş eski coroutine işini iptal eder.
- Eski belge veya eski metin revizyonunun sonucu yeni içeriği ezemez.
- Site bir metin düğümünü istek sürerken editöre taşıdıysa sonuç yazılmaz; orijinale dönüş de kullanıcı taslağını ezmez.
- MutationObserver sonradan eklenen/değişen içerikleri izler. Kendi çeviri yazımları ayırt edilir; sonsuz yeniden çeviri döngüsü oluşturulmaz.
- Görünür hale gelen sekme/panel metinleri sonraki taramada alınır.
- Site tarafından kaldırılıp yeniden kullanılan düğümlerde, temizleme sırasında orijinal korunur; çevrilmiş metin yeni kaynak sanılmaz.
- İlk görünür belge commit’inden itibaren araçlar çalışabilir; bütün dış kaynakların bitmesi beklenmez. Makale kökü geç oluşursa sınırlı aralıklarla yeniden kontrol edilir.

### Kaydırma ve sınırlar

- Çeviri uygulanmadan önce görünür bir makale öğesinin konumu yakalanır; sonrasındaki yükseklik farkı bu öğe üzerinden düzeltilir.
- Kullanıcı tekerlek/dokunma/klavye ile etkileşirken yazım ertelenir; sonradan gelen sonuç kullanıcı kaydırmasına karşı zorla kaydırma yapmaz.
- Çevrilen metin uzayabildiği için **aynı piksel yüksekliği garanti edilmez**. Hedef: aynı öğeler, aynı sıra, aynı bağlantılar, aynı tema ve mümkün olduğunca aynı okuma noktası.
- Her tarama en fazla 500 düğüm ve yaklaşık 8 ms çalışma bütçesi kullanır; görünür alanın çevresi önceliklidir.
- Paket başına en fazla 8 metin; normal paket bütçesi yaklaşık 1000 karakterdir. Tek uzun düğüm en fazla 8000 karakter olabilir ve servis tarafında parçalanır.
- Belge başına en fazla 6000 kayıt / 1 milyon kaynak karakter tutulur. Aşılırsa açık uyarı gösterilir, içerik silinmez.
- Dönen metin boyutu hem native tarafta hem DOM tarafında sınırlandırılır. Sonuç HTML olarak yorumlanmaz.
- Otomatik başarısız düğüm yeniden denemesi en fazla iki turdur (mevcut servisin kendi uç/parça tekrarları ayrıca vardır); sonrasında açık yeniden deneme kontrolü bulunur.

## 4. Reklam engelleme

### İstek filtresi

`WikiAdBlockRules` immutable ve thread-safe bir host matcher’dır. `WebViewClient.shouldInterceptRequest` arka plan callback’inde sadece eşleştirme yapılır; disk/ağ I/O, Compose state yazımı veya UI-thread JavaScript çağrısı yapılmaz.

- Paketlenmiş StevenBlack **kendi katkı listesi** ve Kitsugi’nin reklam ağı alan adları: mevcut sürümde **2884 benzersiz domain kuralı**.
- Alan adı sınırları korunur: `doubleclick.net.example.com`, `doubleclick.net` sayılmaz.
- Ana belge bu kaynak filtresine takılmaz.
- Fandom’un `/ads/` servisi gibi dar birinci taraf reklam rotaları ayrıca ele alınır.
- Wiki içerik/CDN/ResourceLoader kaynakları korunur: Fandom/Wikia ana içerik alanları, `wikia.nocookie.net`, Wikipedia, Wikimedia ve MediaWiki.
- Engellenen kaynak için boş, `no-store` yanıt döner. Hatalı bir görsel isteği bütün wiki sayfasını “yüklenemedi” durumuna düşürmez.
- Yalnızca wiki okuyucusunun WebView’ine uygulanır. Giriş, oynatıcı veya diğer uygulama WebView’lerinin istemcileri değiştirilmez.

### Kozmetik filtre

`adblock-cosmetic.js` açıkça reklam alanı olarak kullanılan dar sınıf/ID seçicileriyle kutuları ve boşluklarını gizler. Sonradan eklenen aynı tür kutular CSS sayesinde ayrıca DOM taraması olmadan kapsanır.

Genel `banner`, `video`, `table`, `gallery` gibi seçiciler kullanılmaz. Wiki kapak görseli, karakter kartları ve editoryal video sırf görsel/video olduğu için kaldırılmaz. Engelleme kapandığında stil kaldırılır ve belge yeniden yüklenir.

### Kalıcı tercih

`IntegrationSettings.wikiAdBlockDisabledHosts` / `SettingsDataStore.setWikiAdBlockEnabled` yerel, host bazlı istisnayı saklar. En fazla 128 istisna tutulur. Çeviri ve reklam engelleme tercihleri birbirinden bağımsızdır. Bu ayar cloud taşınabilir ayarlar listesine eklenmedi.

## 5. Açık kaynak araştırması ve lisanslar

İncelenen kaynaklar:

- [Translate Web Pages](https://github.com/FilipePS/Traduzir-paginas-web), özellikle `src/contentScript/pageTranslator.js`: metin düğümlerini geri alma, dinamik içerik izleme ve çeviri sonucu/düğüm eşleştirme yaklaşımı. Proje MPL-2.0; kodu bu uygulamaya kopyalanmadı.
- [Ghostery Adblocker](https://github.com/ghostery/adblocker): ağ filtresi/kozmetik filtre ayrımı ve browser entegrasyonları. MPL-2.0; bu bağımlılık projeye eklenmedi. Bir WebExtension motorunu WebView’in senkron arka plan istek callback’inden UI-thread JS’e bağlamak yerine sınırlı fakat yerel/thread-safe bir matcher kullanıldı.
- [StevenBlack/hosts](https://github.com/StevenBlack/hosts): `data/StevenBlack/hosts` dosyası ve MIT lisansı doğrudan kullanıldı. **Farklı upstream lisansları içeren toplama `hosts` dosyası alınmadı.**

Paketlenmiş veri:

- Commit: `7df0d762c34cb964c46f115e17cd9a7bd11aae59`
- Kaynak yolu: `data/StevenBlack/hosts`
- Birebir dosya + tam lisans + SHA-256 manifest: `app/src/main/assets/wiki/`
- Ayrıntılı bildirim: `assets/wiki/NOTICE.md`
- Güncelleme: `scripts/update_wiki_ad_hosts.py --commit <incelenmiş commit>`
- Çevrimdışı doğrulama: `python3 scripts/update_wiki_ad_hosts.py --check`

Filtre sürümü uygulamada manifestten okunur. Uygulama açılışında sessizce yeni liste veya çalıştırılabilir üçüncü taraf script indirilmez. Güncellemeler gözden geçirilip uygulama sürümüne dahil edilmelidir.

## 6. Bilinen sınırlar

- Bu, Chrome Translate’in özel motorunun veya AdBlocker Ultimate uzantısının kendisi değildir; Android WebView içinde benzer amaca yönelik yerel entegrasyondur.
- Reklam engeli **tam EasyList/uBlock/ABP sözdizimi motoru değildir**. WebSocket, bazı yönlendirmelerin son hedefleri, service-worker/önbellek yolları, yeni alan adları, bilinmeyen birinci taraf reklamları ve anti-adblock ekranları tam kapsanmayabilir. “Her reklam kesin engellenir” iddiası yoktur.
- Filtreler bilinçli olarak bazı içerik kaynaklarını korur; aşırı engelleyerek wiki resimlerini/temasını bozmak tercih edilmedi. Site bozulursa host anahtarı kapatılabilir.
- Çeviri makale köküyle sınırlıdır; hesap formu, Fandom’un genel site chrome’u, iframe içeriği, kod, timer/canlı duyuru ve tek harfli dekoratif başlıklar korunur. Bunları harf harf çevirmek tasarımı bozacağı için yapılmaz.
- Görsellerdeki yazılara OCR uygulanmaz; `alt`/`title` gibi öznitelikler değiştirilmez.
- Karmaşık satır içi cümlelerde düğüm/bağlantı sırasını korumak çevirinin dilsel akıcılığını sınırlayabilir. Bozuk token halinde güvenli tekil fallback seçilir.
- Google servisinin canlı erişilebilirliği, kota/403/429 davranışı ve çeviri kalitesi bu ortamda test edilemedi.
- Piksel eşitliği ve uzun/dinamik sayfada kusursuz kaydırma garantisi yoktur; cihazda görsel kabul testi gerekir.

## 7. Otomatik doğrulama

Yeni testler:

- `WikiPageToolsChecks.kt`: üretim Kotlin matcher, paket protokolü ve ayar politikasına karşı **19 kontrol**.
- `wiki-dom-tools.cjs`: gerçek paketlenmiş DOM/kozmetik scriptlerine karşı **26 test**. Link/olay işleyicisi/görsel/tablo korunması; orijinale dönüş; dil değişimi; özel alanlar; geç sonuç; dynamic/hidden içerik; detached düğüm; gesture/anchor; timeout sonrası release; bounded retry/batch; XSS ve büyük çıktı reddi; kozmetik aç/kapat.
- `WikiPageToolsTest`: normal Gradle/JUnit hattına regresyonlar eklendi; Gradle hattı ortam sınırı nedeniyle çalıştırılamadı.
- Liste/lisans checksum kontrolü; Kotlin sözdizimi; JS sözdizimi; TR/EN kaynak eşleşmesi; `git diff --check`.

Önceki 12 JVM + 5 DOM regresyon kontrolü de tekrar çalıştırılarak toplam **62 bağımsız kontrol/test** doğrulandı. Bunlar tam Android derlemesi/cihaz testi değildir.

Tekrar çalıştırma (Java 21, Kotlin 2.0.21, Node ve jsdom 26.1.0):

```bash
mkdir -p .cache/wiki-checks
kotlinc \
  app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/WikiAdBlockRules.kt \
  app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/WikiDomTranslationBatch.kt \
  app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/WikiPageToolPolicy.kt \
  scripts/tests/WikiPageToolsChecks.kt \
  -include-runtime -d .cache/wiki-checks/wiki-tools-checks.jar
java -jar .cache/wiki-checks/wiki-tools-checks.jar

npm install --prefix .cache/wiki-checks --no-save --package-lock=false jsdom@26.1.0
NODE_PATH=.cache/wiki-checks/node_modules node --test \
  scripts/tests/wiki-dom-tools.cjs scripts/tests/wiki-original-viewport.cjs
python3 scripts/update_wiki_ad_hosts.py --check
```

## 8. Cihaz kabul listesi — tamamlanmadı

- [ ] You-Zitsu ana sayfası + karakter: otomatik açık/kapalı, manuel çeviri/orijinal, uygulama hedef dil değişikliği.
- [ ] Link etiketleri çevriliyken tıklama, fragment, infobox, görsel ve wiki tab/accordion davranışları.
- [ ] Sayfanın ortasında çeviri sürerken hızlı kaydırma, orijinale dönüş, mobil/masaüstü geçişi.
- [ ] Dil A → dil B → A; çeviri sürerken sayfa/sekme değişimi; geri/ileri; sekme çoğaltma/kapatma.
- [ ] Türkçe Wikipedia’da gereksiz aynı-dil çevirisinin olmaması, yabancı dil alt öğelerinin davranışı.
- [ ] Ağ yok / Google 403-429 / kısmi sonuç / yeniden dene; kaynak metin daima korunmalı.
- [ ] Reklam engeli açık/kapalı: aynı hostta yeniden yükleme, uygulama yeniden açılışında tercihin korunması, farklı wikiye taşınmaması.
- [ ] Wiki CSS/CDN görsellerinin, editoryal videonun ve makale tablolarının filtrelenmemesi.
- [ ] Fandom’un anti-adblock/çerez ekranından kapatma anahtarıyla kurtulabilme.
- [ ] Arka plana geçişte çeviri işinin iptali, dönüşte devam, renderer kapanması ve hata sonrası retry.
- [ ] Büyük wiki sayfasında bellek/CPU profili, 6000 düğüm/1M kaynak sınırının açık uyarıyla çalışması.
- [ ] Medya, karakter ve personel okuyucu giriş noktalarının her birinde aynı araçların görünmesi.

## 9. Sonraki kullanılabilirlik denetimi

Tema seçimi, yazı boyutu, sayfa içi arama ve sade araç menüsü sonraki aşamada eklendi. Güncel toplam 78 çevrimdışı kontrol ve açık cihaz kabul listesi için [tema/kullanılabilirlik raporuna](WIKI_READING_USABILITY_2026-10-21.md) bakın. Bu rapordaki 62 sayısı önceki aşamanın sonucudur.
