# Plan / Task — Listem tüm görünümler ve rozet uyumu

## Amaç
Listem > Tümü sayfasındaki kompakt, rahat, büyük, minimalist ve ızgara görünümlerini uygulamanın tema ve kaynak rozeti diliyle uyumlu hale getirmek.

## Plan
1. Ortak liste kartını ve beş görünümün rozet yerleşimini incele.
2. Ek kaynak rozetlerini kartın içine taşı; ortak, dar alanda sarılabilen bir kaynak şeridi oluştur.
3. Durum ve puan rozetlerini ortak, tema uyumlu kapsüllere dönüştür.
4. Izgara başlıklarını ve aynı satırdaki kartları hizala.
5. Ana kaynak ve takma ad tekrarlarını temizle; Türkçe yerel ayarda doğru normalleştirme sağla.
6. Birim ve Android arayüz testlerini ekle, doğrulama sınırlarını belgele.

## Görev durumu
- [x] Kart ve görünüm analizi.
- [x] Beş görünüm için tek dış kart/çerçeve.
- [x] Tema yüzeylerini kullanan ortak ek kaynak şeridi.
- [x] Ana kaynak logosunu poster köşesinde koruma.
- [x] Ortak durum ve puan kapsülleri.
- [x] Kompakt, rahat ve minimalist bilgi satırlarında sarılma.
- [x] Izgarada iki satırlık başlık ve eşit satır yüksekliği.
- [x] Kaynak takma adlarını tekilleştirme; Locale.ROOT kullanımı.
- [x] Kaynak normalleştirme birim testleri.
- [x] Beş görünüm için rozet sınırı ve kart tıklama cihaz testleri.
- [x] Teknik rapor: docs/LISTEM_ROZET_GORUNUM_UYUMU_2026-10-21.md.
- [x] git diff --check kontrolü.
- [ ] Java/Android SDK bulunan ortamda derleme ve birim testlerini çalıştırma.
- [ ] Android cihaz/emülatörde arayüz testlerini çalıştırma.
- [ ] Açık/koyu tema, farklı vurgu renkleri, dar ekran ve TV odağında görsel doğrulama.

## Test durumu
Gradle birim test komutu denendi; ortamda Java ve JAVA_HOME bulunmadığı için Gradle başlamadı. Testler eklenmiştir ancak çalıştırılmış veya başarılı olmuş sayılmamalıdır. APK oluşturulmamıştır.

## Paketi uygulama
ZIP içindeki app/ ve docs/ yolları depo köküne göre korunmuştur. İlgili dosyaları aynı yolların üzerine kopyalayın. Plan/task belgesi de depo kökünde yer alır. Paket yalnızca bu düzenlemeyle değişen/eklenen dosyaları içerir; tam proje veya APK değildir.
