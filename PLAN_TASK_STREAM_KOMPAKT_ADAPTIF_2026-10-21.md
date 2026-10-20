# Plan / Task — Kompakt ve adaptif video kaynak seçimi

Tarih: 2026-10-21
Temel commit: acb2eafd1e2630204d941d35f168652bb1c6a35e

## Amaç
Çok eklenti kullanan kişiler için video kaynak ekranını daha kompakt,
aranabilir, tema uyumlu ve dikey/yatay kullanıma uygun hale getirmek.

## Tamamlanan görevler
- [x] Dikeyde tekrar eden başlık/poster alanını kaldır.
- [x] Yatayda büyük poster yerine genişliği sınırlandırılmış kaydırılabilir kontrol paneli kullan.
- [x] Yatay kontrol paneli ve sonuç listesini bağımsız kaydır.
- [x] Eklenti şeridini aranabilir kaynak seçim paneliyle değiştir.
- [x] Kaynak sayısı, sonuç, yüklenme, hata ve doğrulama durumlarını göster.
- [x] Tekrarlayan kart posterlerini kaldır; video kartlarını yoğun bilgi satırlarına dönüştür.
- [x] Ayrıntıları isteğe bağlı aç; oynat ve indir alanlarını 48 dp yap.
- [x] Listeye eklenti grup başlığı ve sonuç sayısı ekle.
- [x] Eklenti içinde kaliteye göre azalan sıralama ekle; bilinmeyen kaliteyi sona koy.
- [x] 4K filtresi ekle; 1080p+ etiketini netleştir.
- [x] Arama, etiket, sıralama ve eklenti seçimini rememberSaveable ile koru.
- [x] Tema renklerini kullan; yeni metinlere Türkçe ve İngilizce kaynak ekle.
- [x] Görünür kartlara bağlı mevcut probe mekanizmasını koru.

## Kontroller
- [x] Değişen dört Kotlin dosyasında tree-sitter-kotlin sözdizimi kontrolü.
- [x] Türkçe ve İngilizce XML parse kontrolü; stream_ kaynak adı benzersizliği.
- [x] git diff --check.
- [ ] Android derleme: ortamda Java/Android SDK yok; yapılmadı.
- [ ] Cihaz/emülatör: dikey, yatay, tablet ve büyük yazı boyutu görsel kontrolü.
- [ ] 30+ eklenti ile arama, boş sonuç, hata, doğrulama ve yeniden deneme testi.
- [ ] Ekran döndürülünce seçimlerin korunmasını cihazda doğrula.
- [ ] Oynat/indir/ayrıntı etkileşimlerini cihazda doğrula.
- [ ] Hızlı kaydırmada görünmeyen kartların ölçüm iptalini doğrula.

## Paketi uygulama
ZIP içindeki app/ ve docs/ yolları depo köküne göredir.
Önce mevcut dosyalarını yedekle; dosyaları aynı yollara kopyala.
Bu paket tam proje veya APK değildir. strings.xml dosyaları tam dosyalardır;
hedef projede sonradan farklı metin değişiklikleri varsa körlemesine üzerine
kopyalamak yerine karşılaştırarak birleştir.

Detaylı teknik notlar ve cihaz test listesi: docs/STREAM_SELECTION_UX.md.
