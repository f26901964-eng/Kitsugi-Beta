# Plan / Task — Bangumi TMDB ve Fanart eşleşme iyileştirmesi

**Tarih:** 2026-10-21  
**Kapsam:** Bangumi dizi/film/TV Show kayıtlarında TMDB çapraz kimlik çözümü ve buna bağlı Fanart/TMDB galerisi ile ek detay verilerinin eşleşmesi.

## Kullanıcı bildirimi

Bangumi detaylarında TV, film ve dizi kayıtlarının TMDB/Fanart ile tutarlı eşleşmediği; görsel galerisinin ve diğer çapraz kaynaklı verilerin eksik kalabildiği bildirildi. Ekran görüntülerinde Bangumi ayrıntı ekranı ve poster galerisi örneği paylaşıldı.

## İnceleme bulgusu

Bangumi `REAL` kayıtları TMDB eşleştirme yoluna yönlendiriliyor. TMDB/Fanart görselleri ve tamamlayıcı veriler çapraz kimlik çözümlemesine bağlı; eşleşme bulunamazsa sayfa Bangumi-yerel verilerle kalıyor. `KitsugiBangumiDetailClient` içindeki arama sorgusu oluşturucu yalnızca belirli infobox anahtarlarını (`别名`, `英文名`) dikkate alıyor, ilk Latin alternatifiyle yetiniyor ve en fazla üç sorgu yapıyordu. Bangumi infobox'ındaki İngilizce/alternatif ad başka bir anahtar altında veya sonraki değerlerdeyse TMDB'de aranmıyor; başlık doğrulaması da bu adları kullanmıyordu.

## Yapılan değişiklik

`app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiBangumiDetailClient.kt`:

- Infobox içindeki tüm alanların değerleri arama adayı olarak alınıyor.
- Latin harfli alternatif adlar öncelikli sorgulanıyor.
- Arama sorgusu üst sınırı 3'ten 8'e çıkarıldı.
- Başlık eşleşmesinin doğrulama kümesi de tüm infobox değerleriyle genişletildi.
- Mevcut medya türü ve yıl doğrulaması korunarak yanlış pozitif riski azaltıldı.

## Doğrulama / kalan iş

- `git diff --check`: başarılı.
- Birim testleri çalıştırılamadı: sandbox'ta Java/JAVA_HOME mevcut değil (`./gradlew :app:testFossDebugUnitTest --tests '*KitsugiBangumiDetailClientTest'`).
- Cihaz/CI doğrulamasında farklı Bangumi infobox biçimlerine sahip dizi, film ve TV kayıtları açılmalı; TMDB ID, TMDB/Fanart galeri görselleri ve tamamlayıcı alanlar gözlenmeli.
- TMDB'de başlığı bulunmayan veya yıl/tür doğrulamasını geçemeyen kayıtların eşleşmemesi beklenen güvenli davranıştır.
