# PLAN / TASK — Wiki Otomatik Çeviri ve Orijinal/Çeviri Geçişi Düzeltmesi

**Tarih:** 2026-10-16  
**Dal:** `arena/c3216049-kitsugi-beta`  
**Uygulama commit'i:** `eea85ba`

## Amaç

Fandom ve Wikipedia kaynaklı wiki yüzeylerinde otomatik çevirinin düz metinleri güvenilir biçimde çevirmesini, tıklanabilir bağlantıların ad ve hedeflerini korumasını ve otomatik çeviriden etkilenen her kart/bölümde `Orijinal / Çeviri` geçişinin doğru görünmesini sağlamak.

## Kök nedenler

1. Bağlantı içeren wiki paragrafları bağlantı etiketleri yerine koruma jetonları kullanılarak çevriliyordu. Çeviri servisi jetonlardan birini bozduğunda güvenlik katmanı tüm paragrafın çevirisini iptal ediyordu. Wiki paragraflarının çoğunda bağlantı bulunduğundan otomatik çeviri çalışmıyormuş gibi görünüyordu.
2. Yapılandırılmış bölüm çevirileri blok blok akarken alt blok çevirisi ana `COMPLETE` işaretinden önce belleğe düşebiliyordu. UI yalnızca ana anahtarı denetlediği için çevrilmiş metin görünmesine rağmen `Orijinal` düğmesi kısa süreli veya eski önbelleklerde görünmeyebiliyordu.
3. Seçili wiki maddesinin açıklama geçişi etkilediği açıklama kartında değil, sayfa başlığında bulunuyordu.
4. Kaynak değişiminde Fandom/Wikipedia çevirileri ve görünüm seçimi tamamen temizleniyor, kaynak başına ayrı tutulmuyordu.
5. Hedef dille aynı dildeki yapılandırılmış bölümler, dil kontrolü yapılandırılmış çeviri kolundan sonra çalıştığı için gereksiz yere çeviriye gönderilebiliyordu.

## Uygulanan görevler

- [x] Bağlantılı metinde önce tüm cümleyi bağlantı etiketi jetonlarıyla çevirmeyi dene.
- [x] Jeton bütünlüğü bozulursa tüm paragrafı iptal etmek yerine bağlantılar arasındaki normal metin parçalarını ayrı ayrı çevir.
- [x] Tıklanabilir bağlantı etiketlerini, URL'leri ve dokunma hedeflerini özgün hâliyle koru.
- [x] Tek bir metin parçasının çevirisi başarısız olduğunda paragrafın kalan çevrilebilir parçalarını işlemeye devam et.
- [x] Çeviri varlığı denetimini ana anahtar ve bütün yapılandırılmış alt blokları kapsayan ortak `hasWikiTranslation` yardımcısında birleştir.
- [x] Tam wiki okuyucusu, karakter wiki bölümleri ve seçili madde bölümlerinde ortak geçiş denetimini kullan.
- [x] Seçili wiki maddesi açıklamasının düğmesini doğrudan açıklama kartına taşı.
- [x] Fandom / Wikipedia kaynakları için çeviri, kaynak metin eşlemesi ve `Orijinal / Çeviri` tercihini ayrı bellek kovalarında sakla.
- [x] Kaynağa geri dönüldüğünde hazır çeviriyi ağ isteği yapmadan geri yükle.
- [x] Henüz yüklenmemiş metni bayat kabul edip bellekteki çeviriyi erken silme davranışını düzelt.
- [x] Kaynak dili hedef dille aynıysa kontrolü yapılandırılmış bölüm çevirisinden önce uygula.
- [x] Bağlantı koruması, kısmi hata devamlılığı ve geçiş görünürlüğü için regresyon testleri ekle.

## Değişen dosyalar

1. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/CharacterWikiTabContent.kt`
2. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomMediaWikiViewModel.kt`
3. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomWikiReader.kt`
4. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/MediaWikiTabContent.kt`
5. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/WikiTranslationProtection.kt`
6. `app/src/test/java/com/kitsugi/animelist/ui/screens/detail/wiki/WikiTranslationProtectionTest.kt`
7. `PLAN_TASK_WIKI_OTOMATIK_CEVIRI_GECIS_FIX_2026-10-16.md`

## Kabul kriterleri

- Otomatik çeviri açıkken Fandom ve Wikipedia açılır wiki sayfalarının çevrilebilir düz metinleri hedef dilde gösterilir.
- Tıklanabilir bağlantı etiketleri ve hedefleri çeviriden sonra değişmez ve çalışmaya devam eder.
- Bir bağlantı koruma jetonu bozulsa bile bağlantı dışındaki metin çevrilir.
- Uygulama içi çeviri bulunan açıklama, bilgi kutusu ve bölüm yüzeylerinde `Orijinal / Çeviri` geçişi görünür.
- Geçiş, etkilediği metnin kendi kartında/bölümünde bulunur.
- `Orijinal` görünümüne geçmek çeviriyi bellekten silmez; `Çeviri` seçildiğinde ağ isteği olmadan geri gelir.
- Fandom ve Wikipedia kaynaklarının geçiş tercihleri birbirine karışmaz.
- Hedef dildeki kaynak gereksiz otomatik çeviri isteği üretmez.

## Doğrulama

- `git diff --check`: başarılı.
- İlgili birim testleri genişletildi.
- Arena çalışma ortamında Java/JAVA_HOME bulunmadığı için Gradle test süiti bu ortamda çalıştırılamadı; CI veya JDK bulunan geliştirme ortamında aşağıdaki komut çalıştırılmalıdır:

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'com.kitsugi.animelist.ui.screens.detail.wiki.WikiTranslationProtectionTest'
```
