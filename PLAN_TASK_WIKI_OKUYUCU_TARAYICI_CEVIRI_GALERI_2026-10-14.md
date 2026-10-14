# PLAN/TASK — Wiki okuyucuda tarayıcı sekmeleri, çeviri ve galeri

Tarih: 2026-10-14  
Durum: **Kodlandı.** Kaynak sözdizimi ve statik kontroller geçti; Java/JDK olmadığı için Gradle derlemesi ve Android/JUnit testleri çalıştırılamadı.

## Kullanıcı isteği

Uygulama içi wiki okuyucusunu normal detay sayfasındaki wiki deneyimine ve tarayıcıya yaklaştır:

1. Mevcut otomatik çeviri ayarı ve çevirmen tercihiyle uyumlu çeviri kontrolleri; çevrilmiş/orijinal metin arasında geçiş.
2. İçindekilerden seçilen bölüm, kendi satırının hemen altında açılıp yüklenmeli.
3. Okuyucudaki görseller dokunulabilir olmalı ve uygulama içi galeri açmalı.
4. Üst barda sekme, geri ve ileri gezinme kontrolleri bulunmalı.
5. Akışa uyan ek UX iyileştirmeleri.

## Uygulama özeti

- `FandomReaderBrowser.kt` ortak, UI'dan bağımsız sekme/geçmiş denetleyicisini sağlar: sekme başına geri/ileri geçmişi, sekme değiştirme/kapatma, yeni sekme ve 12 sekmelik sınır. Çeviri anahtarları wiki/sayfa/bölüm bazında sabitlenir.
- Okuyucu üst çubuğuna geri/ileri ve sekme şeridi eklendi. İçindekiler bölümleri kendi girdilerinin altında açılır; eşzamanlı birden çok bölüm açık kalabilir.
- Okuyucu başlık görseli, yüklenmiş içerik bölümlerindeki görseller ve ilgili wiki sayfalarının küçük görselleri uygulama galerisine açılır.
- `ReaderContent` kaydırma konumu sekme + wiki + sayfa anahtarına göre saklanır ve geri yüklenir; bellekte en fazla 120 konum tutulur.
- Medya ve karakter/ekip wiki view-model'leri aynı gezinme/çeviri davranışına bağlandı. Çeviri iptali ve yeniden çeviride eski coroutine'lerin güncel sonucu ezmesini önleyen nesil/iş-kimliği kontrolleri eklendi.
- Türkçe ve İngilizce arayüz dizeleri güncellendi; tarayıcı geçmişi, sekme değiştirme/kapatma ve sekme sınırı için controller testleri eklendi.

## Değiştirilen kod dosyaları

- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/CharacterDetailPage.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/CharacterWikiTabSlot.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomCharacterWikiViewModel.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomMediaWikiViewModel.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomReaderBrowser.kt` (yeni)
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomWikiReader.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/MediaWikiTabContent.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`
- `app/src/test/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomReaderBrowserTest.kt` (yeni)

## Kontroller ve sınırlar

- Değiştirilen Kotlin kaynakları Tree-sitter sözdizimi denetiminden geçti.
- `git diff --check` temiz.
- Türkçe/İngilizce string XML'leri ayrıştırıldı; yinelenen anahtar ve eksik referans bulunmadı.
- Gradle derlemesi ve testler çalıştırılamadı: ortamda Java/JDK yok. Android cihaz doğrulaması ayrıca gerekli.

## Manuel doğrulama planı

1. Medya ve karakter/ekip wiki okuyucularında bölüm aç; içeriğin ilgili İçindekiler girdisinin altında belirdiğini doğrula.
2. Otomatik çeviri açık/kapalıyken başlık ve bölüm çevirilerini, orijinale dönüşü ve tercih edilen çevirmen davranışını kontrol et.
3. Başlık, bölüm ve ilgili sayfa görsellerine dokun; galerinin doğru görselde açıldığını ve gezinmeyi doğrula.
4. Birkaç wiki içi sayfaya git; geri/ileri ve yeni gezinmenin ileri geçmişini temizlediğini kontrol et.
5. Sekme aç, değiştir ve kapat; geçmiş/konumun sekmeler arasında korunduğunu ve 12 sekmeden sonra yeni sekme düğmesinin devre dışı kaldığını doğrula.
6. Sekme/sayfa arasında gidip gel; kaydırma konumunun geri yüklendiğini kontrol et.
