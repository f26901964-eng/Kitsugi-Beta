# Plan / Task — Gölge Bahçesi manga entegrasyonu

Tarih: 2026-10-26
Dal: `arena/2809aa13-kitsugi-beta`

## Hedef

Kitsugi manga bölümüne Gölge Bahçesi'ni yerleşik ve varsayılan olarak etkin kaynak olarak entegre etmek. Diğer Türkçe manga eklentilerini depoda tutup varsayılan profilde devre dışı bırakmak; eski Kotatsu Gölge Bahçesi parser'ını yüklememek. Arama, eser detayları, bölümler, normal görseller ve güvenli/şifreli sayfalar için uygulama entegrasyonu sağlamak.

## Yapılan kaynak kodu değişiklikleri

- [x] Gölge Bahçesi için yerleşik kaynak, API DTO/parsing ve arama/detay/bölüm akışları eklendi.
- [x] Alternatif eser adları arama/eşleştirme akışına dahil edildi.
- [x] Güvenli bölüm sayfaları için WebView tabanlı oturum/yükleme yakalama ve sayfa akışı eklendi.
- [x] Görsel yüklemeleri için kaynak özelinde Referer/Accept header politikası ve önbellek entegrasyonu eklendi.
- [x] Gölge Bahçesi varsayılan kaynak profiline alındı; diğer harici Türkçe kaynakların dosyaları korunarak başlangıçta devre dışı olması sağlandı.
- [x] Eski Kotatsu Gölge Bahçesi parser'ı uygulamanın etkin kaynak yüklemesinden çıkarıldı.
- [x] Manga eklenti/repo ekranı, varsayılan Kitsugi manga repo bağlantısı ve katalog fallback'i entegre edildi.
- [x] Kaynak seçimi için telefon/TV ayar yüzeyleri ve R8/WebKit yapılandırması güncellendi.
- [x] Parser, profil politikası, alternatif başlık ve görsel header davranışları için test kaynakları eklendi.

## Doğrulama durumu

Bu belge kaynak kod değişikliklerini ve test hazırlığını kaydeder; cihazda çalıştığını kanıtlamaz.

- [x] `git diff --check`: geçti.
- [x] Okuyucu JavaScript kancaları için `node --check`: geçti.
- [ ] Gradle unit testleri: çalıştırılamadı; ortamda `java`/JDK ve `JAVA_HOME` yok.
- [ ] Android derlemesi / APK üretimi: yapılmadı.
- [ ] Cihazda kapak, arama, detay, bölüm, normal sayfa ve güvenli/şifreli sayfa testi: yapılmadı.
- [ ] Turnstile doğrulama etkileşimi ve CDN görsellerinin uçtan uca testi: yapılmadı.

**Sonuç:** Bu teslim kaynak kodu değişikliklerinden oluşur. APK değildir; cihaza yüklenmemiştir. Güvenli sayfa yakalama/çözme ve Turnstile davranışı doğrulanana kadar kaynak için uçtan uca çalışma garantisi verilmez.

## Paketin içeriği

ZIP, bu plan dosyasını ve bu dalda değiştirilmiş/yeni eklenmiş depo dosyalarını, depo içindeki göreli yollarını koruyarak içerir. Tam depo yedeği veya APK değildir. ZIP içindeki `PACKAGE_MANIFEST.json`, paketlenen dosyaların SHA-256 özetlerini listeler.
