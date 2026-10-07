# Görev — Tüm Çapraz Eşitleme Hata ve Eşleştirme Kayıtlarını Dışa Aktar

## İstek
Eşitleme sırasında meydana gelen tüm hatalar ve sorunlar ayrı bir dosyada saklanabilmeli ve kullanıcı tarafından destek ekibine gönderilebilmelidir. Rapor; hatanın nerede/nasıl oluştuğunu ve bir anime, dizi veya filmin yanlış içerikle eşleştirilip hesaplara yazılması ihtimalini incelemeye yetecek bağlamı içermelidir.

## Kabul ölçütleri
- Eşitleme bittiğinde rapor, canlı penceredeki 500 kayıt sınırından bağımsız olarak tam işlem günlüğünü içermeli.
- Hata/uyarı kayıtlarında platform, işlem, medya başlığı/türü/yılı, sağlayıcı kimlikleri ve hata ayrıntıları bulunmalı.
- Her eşleştirme grubunun kaynak ve birleştirilmiş değerleri incelenebilmeli.
- Kimlik çakışması veya çözülemeyen belirsiz adaylarda otomatik dış platform yazımı durmalı ve sebep raporlanmalı.
- Rapor otomatik olarak ayrı `.txt` dosyasına yazılmalı ve arayüzden kullanıcı seçtiği konuma kaydedilebilmeli.
- OAuth erişim/yenileme anahtarları rapora sızmamalı.

## Gerçekleştirilenler
- Tam rapor günlüğü ile canlı ekranda gösterilen sınırlı günlük ayrıldı.
- Hatalar, uyarılar, bütün eşleştirme kararları ve platform istatistiklerini içeren rapor üretimi eklendi.
- Android 10+ için `İndirilenler/Kitsugi/CrossSyncReports/` otomatik kayıt; eski sürümlerde uygulama belgeleri dizinine yedek kayıt ve sistem dosya seçicisiyle dışa aktarma eklendi.
- Bearer/access/refresh/client-secret biçimindeki değerler raporda maskeleniyor.
- Çelişen/ambiguous eşleştirmeler diğer platformlara gönderilmeden atlanıyor.
- Rapor biçimi ve kimlik çakışması testleri eklendi.

## Test durumu
Statik `git diff --check` geçti. Android Gradle testleri bu ortamda Java/JDK eksikliğinden çalıştırılamadı.
