# PLAN / TASK — AniList kişi adları ve geri dönüşte kaydırma konumu

Tarih: 2026-10-08  
Dal: `arena/5f066ff8-kitsugi-beta`

## İstek

1. AniList karakter, seslendirmen ve ekip/personel adları, kullanıcı tarafından seçilen isim/başlık diline göre gösterilmeli. Karakter ve personel detay sayfaları ile arama sonuçları da kapsamda.
2. Listem ve diğer uzun liste/arama ekranlarından bir detaya girilip geri dönüldüğünde, kullanıcı kaldığı öğeye ve kaydırma ofsetine dönmeli; veri yükleme sırasında geçici boş listeler konumu sıfırlamamalı.

## Uygulama planı ve gerçekleştirilen işler

- [x] AniList kişi adı sorgularına `full`, `first`, `middle`, `last`, `native`, `alternative` alanlarını ekle; Latin harfli ve yerel yazımları veri modellerinde ayrı tut.
- [x] `ROMAJI` / `ENGLISH` için eldeki Latin harfli adı; `NATIVE` / `JAPANESE_STAFF` için yerel adı kullan. İstenen sürüm yoksa mevcut isme geri dön. İsimleri önbellekte dil bağımsız tut, gösterimde seçili dile göre seç.
- [x] AniList arama sonuçlarına, karakter ve personel sekmelerine, seslendirmenlere, detay başlıklarına ve personelin karakter rollerine aynı gösterim mantığını uygula.
- [x] Listem'in her sekmesi için öğe indeksi ve piksel ofsetini ayrı ayrı sakla. Geçici boş/yükleniyor görünümünde kaydırma konumunu ezme; gerçek öğeler geldikten sonra geri yükle.
- [x] Keşfet ve Arama için yükleme boşluklarından bağımsız, kaydedilebilir bir liste-konumu yardımcı fonksiyonu kullan.
- [x] Kaynağa özel arama ekranının ViewModel durumunu alt detay ekranı açıkken elde tut; ekrandan çıkılınca temizle.
- [x] Yatay raflar ve tam ekran listeler için kaydırma durumunu koru. Tam ekran sayfalarda sayfalanmış öğeleri ve sayfa bilgisini de geri dönüş boyunca bellekte tut.
- [x] Kişi adının dil seçimi için birim testleri ekle.

## Kontrol / devretme

- `git diff --check`: temiz.
- Gradle testleri **çalıştırılamadı**: bu çalışma ortamında Java/JAVA_HOME yok. Derleme ve cihaz doğrulaması gereklidir.
- Cihazda kontrol: ROMAJI, ENGLISH, NATIVE ve JAPANESE_STAFF seçenekleriyle AniList karakter/personel arama, yapım sekmeleri ve detay başlıkları; Listem'de farklı sekmelerde çok aşağı kaydırıp detaya girme/geri dönme; Keşfet, Arama, kaynağa özel arama ve tam ekran gridde aynı geri dönüş akışı; donanım geri tuşu.

## Paket notu

Bu dosya ile birlikte ZIP'teki `app/src/...` yolları depo köküne göredir. Paket, tam proje/APK değil; bu görevde değişen veya yeni eklenen kaynak ve test dosyalarını içerir.
