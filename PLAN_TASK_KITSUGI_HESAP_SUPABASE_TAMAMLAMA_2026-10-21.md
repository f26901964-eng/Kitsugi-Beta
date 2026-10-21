# Plan / Task — Kitsugi hesabı ve Supabase eşitlemesi

**Tarih:** 21 Ekim 2026  
**Uygulama sürümü:** 2.4.328 (kaynak kodundaki sürüm)  
**Çalışma dalı:** `arena/a41f6fd0-kitsugi-beta`  
**Başlangıç commit'i:** `795cf822ca745229944d90eed6cc9bd7bf55778d`  
**Kod commit'i:** `6ffa0ed2d2a0792b48cb14a555320f3a962b73c3`  
**Teslim:** Değişen kaynak dosyaları + bu plan/task + SQL referansları + birleşik patch. APK değildir.

## 1. Amaç ve bulunan nedenler

Kitsugi hesabına giriş, e-posta doğrulaması, şifreli bağlı hesap yedeği, arama geçmişi ve bildirim arşivi eşitlemesini uygulama ile mevcut Supabase projesi arasında tamamlamak.

Canlı incelemede:

- `public.user_data` tablosu ve kullanıcıya özel RLS politikaları vardı; ancak uygulamanın kasa oluşturma/güncellemede kullandığı `public.kitsugi_vault_cas(text,jsonb,jsonb)` fonksiyonu yoktu.
- `authenticated` için normal tablo erişimi daha önce açılmıştı. Buna ek olarak `anon` ve `authenticated` rollerinde **TRUNCATE / REFERENCES / TRIGGER** gibi gereksiz yetkiler kalmıştı. RLS, TRUNCATE işlemini engellemez.
- İzinli mobil callback `kitsugi://account-confirm` idi; Auth **Site URL** ise `http://localhost:3000` olarak kalmıştı.
- Hesap ekranında kasa kilidi arama geçmişi/bildirim eşitlemesini de engelliyordu. Bildirim eşitlemesi başarısız olsa bile genel başarı mesajı gösterilebiliyordu.
- Bildirim arşivinin yerel yazımı ve buluta gönderimi ayrı coroutine'lerde paralel başlatılıyor; önceki dosya sürümünün yedeklenmesi mümkün oluyordu.
- Kasa durumu Compose tarafından Keystore okunarak kontrol ediliyordu; kilitli kasa için ilk durum metni yanıltıcı biçimde kalabiliyordu.

## 2. Canlı Supabase üzerinde TAMAMLANAN işler

Proje: `kitsugi` — `kjcxyaqwuzjgbeiowqte`.

- [x] Mevcut tablo, RLS, izinler, fonksiyonlar ve Auth yapılandırması incelendi.
- [x] `kitsugi_vault_cas` kuruldu. Sahiplik istemciden alınmaz; `auth.uid()` kullanılır.
- [x] Kasa yazmaları atomik karşılaştır-ve-değiştir (CAS) kontrolünden geçirildi. Eski snapshot yeni yedeği doğrudan ezemez.
- [x] `vault_meta` ve `linked_accounts_vault` satırlarına istemciden doğrudan INSERT/UPDATE/DELETE restrictive RLS politikalarıyla kapatıldı.
- [x] `public` ve `anon` rollerinin `user_data` tablo yetkileri kaldırıldı. `authenticated` için yalnız SELECT/INSERT/UPDATE/DELETE bırakıldı; satır sahipliği RLS ile korunuyor.
- [x] Timestamp trigger fonksiyonunun `search_path` değeri sabitlendi ve gereksiz EXECUTE yetkileri kaldırıldı.
- [x] Auth **Site URL** `kitsugi://account-confirm` olarak değiştirildi. Mevcut mobil redirect izin listesi korundu.
- [x] E-posta doğrulaması zorunlu kalıyor; doğrulamayı atlama açılmadı. Kullanıcı şifresi, session veya anahtarları sıfırlanmadı.
- [x] Değişikliklerden sonra canlı rol izinleri/RLS/fonksiyonlar yeniden okundu. `anon` tabloyu okuyamıyor ve kasa RPC'sini çalıştıramıyor; `authenticated` için TRUNCATE/REFERENCES/TRIGGER kapalı.

Canlı migration geçmişinde kayıtlı uygulamalar:

| Sunucu sürümü | Migration adı | Kaynak karşılığı |
|---|---|---|
| `20261021093756` | `kitsugi_vault_compare_and_swap` | `supabase/migrations/20261009_vault_compare_and_swap.sql` |
| `20261021093808` | `kitsugi_account_privilege_hardening` | `supabase/migrations/20261021_account_privilege_hardening.sql` |

**Veri koruması:** İşlemler şema/izin/yönlendirme düzenlemesidir. Mevcut kullanıcı verileri silinmedi, okunup dışarı aktarılmadı veya yeniden yazılmadı. Öncesi/sonrası toplu sayımda `notifications` ve `search_history` için birer satır korunuyordu; mevcut bir şifreli kasa satırı bulunmuyordu. Kullanıcı token'ları veya şifreler incelenmedi.

**Bu proje için SQL'leri tekrar çalıştırmak gerekmiyor.** Başka bir projeye kurulum yapılırsa sıralama: `schema.sql` → `20261009_vault_compare_and_swap.sql` → `20261010_user_data_authenticated_grants.sql` → `20261021_account_privilege_hardening.sql`. Eski iki migration arşive referans olarak ayrıca konmuştur; bu oturumda dosya içerikleri değiştirilmedi.

## 3. Uygulama kodunda TAMAMLANAN işler

### Giriş ve e-posta doğrulaması

- [x] E-posta/şifre girişleri repository sınırında kontrol ediliyor; kayıt için en az 8 karakter ve şifre tekrarı var. Eski 6 karakterli hesapların giriş yolu kapatılmadı.
- [x] Başarısız kayıt isteği artık mevcut yerel kasa anahtarını önceden silmiyor.
- [x] E-posta doğrulama bağlantısında tek PKCE kodu bekleniyor; kod alışverişi coroutine içinde **beklenerek** hata yakalanıyor.
- [x] Özel şemalı URL'den gelen bearer token'lar otomatik oturuma dönüştürülmüyor. Eski/yeniden gönderilen, PKCE kodu içermeyen bağlantılarda kullanıcı normal e-posta/şifre girişine yönlendiriliyor.
- [x] Başka hesabın doğrulama bağlantısı mevcut hesabı sessizce değiştiremiyor.
- [x] Bağlantı bir kez tüketiliyor; callback URL'si/kodu/ham ağ hatası loga yazılmıyor.
- [x] “Doğrulama e-postasını yeniden gönder” ve 60 saniyelik arayüz bekleme süresi eklendi. Bu süre sunucunun saatlik e-posta sınırını kaldırmaz.
- [x] Form alanları işlem sırasında kilitleniyor; şifreler `rememberSaveable`/diske yazılmıyor ve işlem sonunda temizleniyor.

### Şifreli kasa ve eşitleme

- [x] Kasa açık/kapalı durumu owner'a bağlı StateFlow ile gösteriliyor; UI her çizimde Keystore çözmüyor.
- [x] Kasa kilitliyken arama geçmişi ve bildirim arşivi eşitlemesi çalıştırılabiliyor. Kasa kategorisi ayrıca kendi hatasını bildiriyor.
- [x] “Şimdi eşitle” yalnızca açık kategorileri çalıştırıyor. Kapalı kategori başarılı eşitleme gibi raporlanmıyor.
- [x] Kategoriler bağımsız raporlanıyor; bildirim hatası genel başarı mesajı içinde kaybolmuyor.
- [x] Auth değişimleri ile aktarım işlemleri aynı oturum kilidi üzerinden sıralanıyor; bekleyen A hesabı işi B hesabına taşınamıyor.
- [x] Disk/ağ işleri IO dispatcher üzerinde. Bildirim birleştirmesindeki `runBlocking` kaldırıldı.
- [x] Bildirim bulut işlemi yerel arşivin yazımı bittikten sonra planlanıyor.
- [x] Hatalı bulut JSON'u “boş yedek” sayılarak üzerine yazılmıyor. Yerel arşiv yazımı başarısızsa önceki dosya silinmiyor.
- [x] WorkManager işi çalışırken ve oturum kilidini aldıktan sonra kalıcı yedekleme tercihini yeniden kontrol ediyor; kapanmış anahtar eski kuyruk tarafından geçersiz kılınmıyor.
- [x] Çıkış öncesi etkin şifreli yedekleme tamamlanamazsa yerel kasa anahtarı silinmiyor. Yedekleme kapalıysa çıkış gizlice yükleme yapmıyor.
- [x] AES-GCM / PBKDF2 kasa biçimi ve iki aşamalı şifre değiştirme yaklaşımı korunuyor; yeni bir kasa biçimine zorunlu geçiş yok.
- [x] Eksik RPC, yanlış şifre, doğrulanmamış e-posta, e-posta gönderim sınırı ve ağ sorunları için açıklayıcı hata mesajları eklendi; URL/JWT/JSON kimlik bilgileri maskeleniyor.
- [x] Hesap ekranı kenar boşlukları düzenlendi; eklenen arayüz metinleri Türkçe ve İngilizce kaynaklarda bulunuyor.
- [x] Kaynak sürümü `2.4.328` yapıldı.

**Önemli:** Kaynak dosyalarını değiştirmek kurulu APK'yı değiştirmez. Bu uygulama iyileştirmelerini kullanmak için yeni APK derlenip kurulmalıdır. Supabase düzeltmeleri ise canlıda zaten geçerlidir.

## 4. Test / doğrulama devri

Kullanıcı “testleri bana bırak” dediği için bu noktadan sonra yeni uygulama testi/derlemesi çalıştırılmıyor.

### Talimattan önce gerçekten yapılanlar

- Yalıtılmış PostgreSQL/PGlite üzerinde **14 backend regresyon testi geçti**: owner izolasyonu, anonim erişim reddi, ilk kasa oluşturma, stale CAS, şifre wrap geçişi, doğrudan kasa yazmasının engellenmesi, tablo ayrıcalıkları, timestamp ve migration tekrar uygulanabilirliği.
- Canlı Supabase'de sadece yapılandırma/izin denetimi ve belirtilen migration'lar yapıldı; gerçek kullanıcıyla uçtan uca giriş veya iki cihaz testi yapılmadı.
- XML kaynakları ve değişikliklerin dosya/boşluk kontrolleri yapıldı.
- Android birim testleri + Debug/R8 Release derleme iş akışı başlatıldı: https://github.com/f26901964-eng/Kitsugi-Beta/actions/runs/39579958888

### Kullanıcı devraldığında durum

- [x] Yerel CI izleme süreci durduruldu.
- [ ] Uzak GitHub çalışmasının iptali doğrulanamadı: iptal isteği **403 Resource not accessible by integration** döndürdü. Gerekirse kullanıcı yukarıdaki sayfadan iptal edebilir.
- [ ] Android/Kotlin derlemesinin veya APK testlerinin başarılı olduğu **iddia edilmiyor**. Kullanıcı devraldığı anda çalışma devam ediyordu; bu paket test tamamlanmış APK teslimi değildir.
- [x] Test kaynakları ve CI tanımı pakette bırakıldı; kullanıcı istediğinde çalıştırabilir.

### Kullanıcının tamamlayacağı kontrol listesi

- [ ] `:app:testFossDebugUnitTest` içinde `com.kitsugi.animelist.data.account.*` ve `NotificationArchiveStoreTest` çalıştır.
- [ ] FOSS Debug ve R8 Release APK derle; gerekiyorsa mevcut DEX doğrulama script'ini çalıştır.
- [ ] Mevcut hesapta şifreyle kasayı aç; “Şimdi eşitle” sonucunda her seçili kategoriyi kontrol et.
- [ ] Kasa kilitli, yalnız arama/bildirim açıkken eşitlemeyi dene.
- [ ] Bir kategori kapalıyken onun gönderilmediğini ve başarılı olarak sayılmadığını kontrol et.
- [ ] Kayıt → doğrulama bağlantısı → kasa açma; geçersiz/süresi dolmuş/tekrar açılmış bağlantı senaryolarını dene.
- [ ] Doğrulama e-postasını yeniden gönderme, 60 saniyelik bekleme ve sunucu e-posta sınırını kontrol et.
- [ ] Çevrimdışı durum, uygulama yeniden açılışı ve yedekleme kapalıyken bekleyen WorkManager işi senaryolarını dene.
- [ ] Çıkış ve hesap değişimi sırasında yanlış hesaba veri aktarılmadığını kontrol et.
- [ ] İki cihazda bağlı hesap çakışması ve şifre değişikliği sırasında bağlantı kesilmesini dene.
- [ ] Bildirim arşivine yeni kayıt geldikten sonra en son dosyanın eşitlendiğini kontrol et.

İsteğe bağlı komutlar (bu belgede yer alması, çalıştırıldıkları anlamına gelmez):

```bash
npm ci --prefix scripts/account-tests --ignore-scripts
npm test --prefix scripts/account-tests

./gradlew :app:testFossDebugUnitTest \
  --tests 'com.kitsugi.animelist.data.account.*' \
  --tests 'com.kitsugi.animelist.data.notifications.NotificationArchiveStoreTest'

./gradlew :app:assembleFossDebug :app:assembleFossRelease
```

## 5. Bilinçli sınırlar / henüz tamamlandı sayılmayanlar

- **SMTP:** Canlı projede özel SMTP sağlayıcısı yapılandırılmamıştı. Genel kullanıcı kaydında Supabase'in varsayılan gönderici kısıtları/saatlik sınırları geçerlidir. Kendi gönderici alan adı ve sağlayıcısı ayrıca yapılandırılmalıdır. E-posta doğrulaması bu kısıtı aşmak için kapatılmadı; sağlayıcı şifresi talep edilmedi veya eklenmedi.
- **Şifremi unuttum:** Yeni bir şifre sıfırlama/kasa silme akışı eklenmedi. Mevcut şifre değiştirme mevcut kasa anahtarını korur. Eski kasayı açacak şifre/anahtar olmadan şifreli verinin kurtarıldığı iddia edilmez.
- **Mobil callback:** Yeni bir web sitesi/masaüstü callback sayfası kurulmadı. `Site URL` mevcut Android callback'ine ayarlandı; eski gönderilmiş e-postaların içindeki adresler geriye dönük değişmez.
- **Arama/bildirim verisinin modeli:** Bu iki kategori RLS korumalı, uçtan uca şifrelenmemiş JSON verileridir. Bu turda kasa dışı veriler için yeni sunucu taraflı CAS/tombstone protokolü eklenmedi; çoklu cihazda aynı anda silme/yazma davranışı ayrıca değerlendirilmelidir. Şifreli kasa için CAS korunur.
- **Tamamlanma ölçütü:** Sunucu düzenlemeleri uygulanmıştır; uygulama kaynak kodu hazırlanmıştır. Derleme ve gerçek cihaz kabul kontrolleri kullanıcıya devredilmiştir.

## 6. ZIP kullanım talimatı

1. Mevcut projeyi yedekle. Paket, yukarıdaki başlangıç commit'i üzerindeki değişiklikleri içerir; daha yeni veya yerelde farklı dosyalar varsa önce karşılaştır.
2. Arşiv içindeki proje klasöründe dosya yolları korunur. `.github`, `.gitignore`, `app/`, `scripts/`, `supabase/` ve bu plan dosyasını kendi proje kökünle birleştir. `TESLIMAT/` açıklama/manifest dosyalarıdır; derleme için zorunlu değildir.
3. Alternatif olarak `TESLIMAT/DEGISIKLIKLER.patch` kullanabilirsin. **Tam dosya kopyalama ve patch uygulamayı aynı projeye art arda yapma.**
4. `TESLIMAT/DEGISEN_DOSYALAR.txt` tüm değişen/yeni dosyaların listesini; `TESLIMAT/SHA256SUMS.txt` paket dosyalarının özetlerini içerir. Bu turda silinen dosya yoktur.
5. Aynı Supabase projesinde migration'ları yeniden çalıştırman gerekmez.
6. `local.properties`, `.env`, keystore, `.git`, `node_modules`, kullanıcı verisi ve APK pakete eklenmemiştir. Değişen kaynak dosyalarındaki projede önceden bulunan entegrasyon yapılandırmaları korunur; arşivi herkese açık paylaşma.
