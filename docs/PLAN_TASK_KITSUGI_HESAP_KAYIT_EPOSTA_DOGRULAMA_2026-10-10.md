# PLAN TASK — Kitsugi hesap kaydı, giriş ve e-posta doğrulama düzeltmesi

**Tarih:** 2026-10-10  
**Durum:** Kod değişiklikleri tamamlandı; JDK ve Supabase/cihaz doğrulaması bekliyor.

## Belirtiler

- Hesap kaydı sırasında doğrulama e-postası gönderiliyor ancak uygulama kapanabiliyor.
- Girişte `Failed resolution of: Lkotlinx/datetime/serializers/InstantIso8601Serializer;` hatası görünüyor.
- E-posta bağlantısı Supabase proje Site URL'si `http://localhost:3000` adresine yönleniyor.

## Kök neden ve kapsam

1. Supabase Auth modelleri `Instant` tarih alanlarında `kotlinx-datetime` serializer'ına ihtiyaç duyuyor. İstemci çalışma zamanında uyumlu sınıfın bulunması ve R8 ile korunması gerekiyor.
2. Kayıt isteğinde uygulama geri dönüş adresi belirtilmediğinden Supabase Site URL'si kullanılıyor.
3. E-posta doğrulama sonrası Supabase oturumunu uygulamaya güvenli biçimde aktarıp, şifreli hesap kasası için kullanıcıdan şifreyi yeniden istemek gerekiyor. Şifre doğrulama bağlantısına veya kalıcı depoya yazılmamalı.

## Uygulanan işler

- [x] Supabase 3.0.3 ile uyumlu `kotlinx-datetime:0.6.1` doğrudan eklendi ve sürümü sabitlendi.
- [x] `kotlinx.datetime.serializers` R8'de korundu.
- [x] Auth `UserInfo.created_at` ISO-8601 çözümlemesi için regresyon testi eklendi.
- [x] E-posta kayıt yönlendirmesi `kitsugi://account-confirm` olarak ayarlandı; PKCE etkinleştirildi.
- [x] Android manifest callback filtresi ve Supabase `handleDeeplinks` akışı eklendi.
- [x] Hesap ekranı Auth oturum değişikliklerini izliyor; kasa kilitliyse şifreyi yeniden doğrulayıp kasayı açma alanı gösteriyor.
- [x] Uygulama içi auth işlemlerinde `LinkageError` sonucu ekrana raporlanıyor; hesap akışı uygulamayı doğrudan kapatmıyor.
- [x] Supabase Dashboard gereksinimleri bu dosyanın beraberindeki düzeltme raporunda açıklandı.

## Manuel dağıtım gereksinimleri

- [ ] Supabase Authentication → URL Configuration → Redirect URLs içinde `kitsugi://account-confirm` bulunmalı.
- [ ] Varsayılan veya özelleştirilmiş Confirm signup şablonu, doğrulama bağlantısı için `{{ .ConfirmationURL }}` kullanmalı. Varsayılan şablon zaten bunu yapıyorsa düzenleme gerekmez.
- [ ] Uygulama değişiklikleri içeren yeni APK yayımlanmalı; auth akışı aynı cihazda test edilmeli.

## Kabul ölçütleri

- [ ] `KitsugiAuthDateTimeSerializerTest` geçer.
- [ ] Kayıt ve parola girişi Auth kullanıcı tarihlerini `InstantIso8601Serializer` hatası olmadan işler.
- [ ] Aynı cihazda açılan PKCE doğrulama bağlantısı uygulamayı açar ve oturumu aktarır.
- [ ] Callback sonrası kullanıcı kasayı aynı Kitsugi şifresiyle açabilir.
- [ ] Site URL eski `localhost:3000` kalsa da uygulamanın açıkça gönderdiği callback kabul edilir.

## Doğrulama kısıtları

- Bu çalışma ortamında Java/JDK olmadığı için Gradle derlemesi, unit testleri ve APK üretimi yapılamadı.
- Supabase Dashboard ayarı ve gerçek cihaz testi kullanıcı/operatör tarafından yapılmalı.
- Önceden gönderilen e-postalar eski `redirect_to` değerini taşıyabilir. Supabase doğrulamayı yönlendirmeden önce tamamlamış olabilir; mevcut kullanıcıyla giriş, tekrar kayıttan önce denenmeli.
