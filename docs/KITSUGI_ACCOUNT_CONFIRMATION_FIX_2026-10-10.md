# Kitsugi hesap kaydı ve e-posta doğrulaması — 2026-10-10

## Belirtiler

- Kayıt isteği Supabase'e ulaşıp doğrulama e-postasını gönderiyor, fakat uygulama Auth yanıtındaki kullanıcı tarihlerini çözerken `kotlinx.datetime.serializers.InstantIso8601Serializer` sınıfını bulamıyor. Bu nedenle hesap oluşturma tamamlanmış olsa bile istemci hata verebiliyor; aynı eksik sınıf giriş yanıtını da etkiliyor.
- Kayıt isteği yönlendirme adresi vermediğinden Supabase proje `Site URL` değerine dönüyor. Projede bu değer `http://localhost:3000` ise e-posta bağlantısı tarayıcıda bağlantı reddedildi sayfasına gidiyor.

## Kod değişiklikleri

- Supabase 3.0.3'ün beklediği `kotlinx-datetime:0.6.1` çalışma zamanı doğrudan bağımlılık olarak eklendi ve sürümü zorlandı; Auth timestamp serializer'ları R8'de korunuyor.
- Auth kullanıcı modelinin tarih alanlarını gerçekten çözdüğünü doğrulayan JVM testi eklendi.
- Kayıt yönlendirmesi `kitsugi://account-confirm` olarak açıkça ayarlandı. Android manifest bu callback'i `MainActivity`'ye yönlendiriyor ve Supabase SDK deep link oturumunu içe aktarıyor.
- Auth akışı PKCE kullanıyor; bağlantıdaki tek kullanımlık kod yerel doğrulayıcıyla uygulamada değiştirilir. E-posta bağlantısını kaydı başlattığın aynı cihazda açmalısın.
- E-posta doğrulamasından sonra güvenli hesap kasası açılmadıysa kullanıcıdan Kitsugi şifresini yeniden doğrulaması isteniyor; şifre e-posta bağlantısında saklanmıyor.

## Zorunlu Supabase Dashboard ayarı

Uygulama kodu, Supabase Dashboard'daki izinli URL listesini değiştiremez. Kayıt bağlantısının uygulamaya dönebilmesi için kullanılan Supabase projesinde:

1. **Authentication → URL Configuration → Redirect URLs** bölümüne `kitsugi://account-confirm` ekleyin.
2. E-posta doğrulama şablonunun bağlantısının `{{ .ConfirmationURL }}` kullandığını doğrulayın; bağlantı doğrudan `localhost:3000` gibi sabit bir adrese gitmemeli.
3. `Site URL` değerini localhost yerine üretim web adresine ayarlayın. İstemcinin açıkça verdiği mobil yönlendirme kullanılır; Site URL yine de varsayılan/masaüstü geri dönüşüdür.

Daha önce gönderilmiş e-posta bağlantısı eski yönlendirmeyi taşıyor olabilir. Supabase bağlantıya tıklandığında önce e-postayı doğrulayıp sonra `localhost:3000`'e yönlendiriyor olabilir; boş/bağlantı reddedildi sayfası hesabın doğrulanmadığını tek başına göstermez. Yeni APK'yı kurduktan sonra ikinci bir hesap oluşturmadan önce mevcut e-posta/şifreyle giriş yapmayı deneyin. Hesap hâlâ doğrulanmamışsa eski e-posta yeniden yönlendirme ayarını değiştirmez; backend'den yeni doğrulama e-postası gönderilmesi gerekir.

## Doğrulama durumu

- `KitsugiAuthDateTimeSerializerTest` Auth `UserInfo` içindeki `created_at` ISO-8601 alanının okunmasını sınar.
- Bu ortamda Java/JDK bulunmadığı için Gradle derlemesi ve testleri çalıştırılamadı. Release APK'da (R8 açık) kayıt, e-posta callback'i ve giriş cihazda ayrıca doğrulanmalı.
