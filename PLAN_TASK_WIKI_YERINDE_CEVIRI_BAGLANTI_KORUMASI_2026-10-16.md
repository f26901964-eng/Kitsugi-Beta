# Wiki: bağlantı dışı metinleri yerinde otomatik çeviri

Tarih: 2026-10-16

## İstenen davranış

Mevcut otomatik çeviri ayarı açıkken wiki açıklamaları, paragrafları, listeleri ve görsel altı yazıları çevrilsin. Tıklanabilir bağlantı etiketleri/hedefleri, bağlantı çipleri ve bağlı kart başlıkları orijinal kalsın. Metinler kendi bloklarında güncellensin; görseller, kartlar ve liste yapısı yeniden sıralanmasın.

## Yapılanlar

- Bağlantılı cümleyi jetonlarla birlikte topluca çevirme yolu kaldırıldı. Yalnızca bağlantılar arasındaki metin parçaları servise gider; bağlantı etiketleri kaynak sırayla yerlerinde tutulur.
- Çeviri ve okuyucudaki tıklama alanları ortak `wikiLinkRanges` yardımcısını kullanır. Uzun/kısa çakışan etiketler, tekrarlar, aynı etikete sahip farklı hedefler ve bitişik CJK yazıları için testler eklendi. Latin sözcüklerin içindeki tesadüfi alt dizeler bağlantıya dönüştürülmez.
- Yalnızca bağlantı/noktalama içeren bloklar gereksiz çeviri isteği üretmez. Parça hatası/boş yanıt yalnızca o parçayı orijinal bırakır; coroutine iptali yutulmaz.
- `IMAGE` bloklarının düz açıklamaları artık çevrilir. `text` ve `image.caption` aynı yerde güncellenir; görsel URL'si, diğer görsel metaverileri ve `href` korunur.
- Başlık ve görsel açıklaması içindeki bağlantılar HTML ayrıştırıcısında tutulur. Okuyucu açıklamalarda ve kart alt satırlarında bunları tıklanabilir olarak çizer.
- Bağlı kart başlığı korunur; bağlı olmayan kart başlığı ve ayrı bağlantı çiplerinin üzerindeki düz grup başlığı çevrilebilir.
- Görsel açıklamalarından oluşan bölümlerde de çeviri/orijinal düğmesinin kaybolmaması için düğme kaynak metnine açıklamalar dahil edildi.
- Yapılandırılmış bloklar varken eski düz metin çevirisi artık kullanılmaz. Bu koruma okuyucu, karakter wiki, yapım wiki ve kopyalama akışında ortak uygulanır.
- Çeviriyi bloklara uygularken bağlantı etiketlerinin sırası/sayısı yeniden kontrol edilir. Eski/bozuk kayıt bağlı kart başlığını veya bağlantı etiketlerini değiştiremez.
- Bölüm önbelleği `sec:v8` oldu; başlık/açıklama bağlantılarını içermeyen eski ayrıştırma kayıtları yeniden alınır.
- Yapım okuyucusunda çeviri anahtarı ve kaynak dil kontrolü ana yapımın wiki'sinden değil aktif okuyucu wiki'sinden türetilir.
- Otomatik çeviri kapatıldığında/hedef dil değiştiğinde kaynaklar arası çeviri anlık görüntüleri temizlenir; kaynak değiştirerek eski çeviri geri getirilemez.

Yeni bir çeviri ayarı eklenmedi; mevcut otomatik çeviri aç/kapat ve hedef dil akışı kullanılıyor.

## Doğrulama

- `git diff --check`: başarılı.
- Değişen Kotlin dosyaları Tree-sitter Kotlin sözdizimi kontrolünden geçti.
- `WikiTranslationProtectionTest`: **25 sözleşme testi** bağımsız Kotlin/JVM çalıştırıcısında geçti. Gerçek koruma dosyası ve veri modelleri derlendi; teknik değer yardımcısı kaynak dosyadan alındı. Android/Gradle bağımlılıklarından ayrıştırmak için test kopyasında JUnit assertion'ları Kotlin test assertion'larına uyarlandı, serialization annotation ve testlerde kullanılmayan URL üreticisi minimal bağımlılık ikameleriyle sağlandı. Bu kontrol tam Android/JUnit derlemesi yerine geçmez.
- HTML ayrıştırıcısına başlık ve görsel açıklaması bağlantıları için iki regresyon testi eklendi; bu ortamda Gradle üzerinden çalıştırılamadı.
- Gradle denemesi `gradle-8.13-bin.zip` indirilirken ağ/SSL hatasıyla durdu. APK/Compose derlemesi ve cihaz üzerindeki görsel doğrulama yapılmadı.

CI / Android geliştirme ortamında:

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'com.kitsugi.animelist.ui.screens.detail.wiki.WikiTranslationProtectionTest' \
  --tests 'com.kitsugi.animelist.data.fandom.FandomHtmlTextTest'
```

## Cihaz kabul kontrolü

1. Otomatik çeviri kapalıyken Rickipedia ana sayfasını aç; yazılar orijinal kalmalı.
2. Ayarı aç; haber açıklamaları, bölüm bilgileri, About/Links/WIKIS düz yazıları ve görsel altı açıklamalar kendi bloklarında çevrilmeli.
3. `Read more`, dizi/karakter adları, sosyal bağlantılar ve çiplerin etiketleri/hedefleri değişmemeli. Çevrilmiş paragraftan ve görsel açıklamasından bağlantıları aç.
4. Kart/görsel/liste sırası değişmemeli. Çeviri uzunsa blok yüksekliğinin doğal olarak artması beklenir; piksel yüksekliği sabitlenmez.
5. Yalnızca açıklamalı görseller bulunan bir bölümde Orijinal/Çeviri geçişini dene; görseller ve hedefleri aynı kalmalı.
6. Fandom/Vikipedi kaynaklarını değiştir; otomatik çeviri kapatıldığında eski çeviri tekrar belirmemeli. Hedef dil değişiminde eski dildeki kayıtlar geri yüklenmemeli.
7. Ağ hatasında mevcut içerik kaybolmamalı; yalnızca başarısız metin parçaları orijinal kalmalı.

## Bilinen sınır

Bağlantıları yerinde tutmak için aralarındaki ifadeler ayrı çevrilir. Bu tercih bağlantı konumunu korur, ancak tam cümleyi birlikte çevirmeye göre dilbilgisel akıcılık azalabilir. Çeviri sağlayıcısının dil kalitesi ve cihazdaki nihai yerleşim ayrıca doğrulanmalıdır.
