# Kitsugi bağlı hesap yedeği — 2026-10-09

## Kapsam

Amaç: Kitsugi hesabına girişle AniList, MAL, Kitsu, Shikimori, Bangumi ve Simkl oturumlarını geri getirmek. Bu hizmetlerin sunucularındaki listeler ve ilerleme tekrar hizmetlerden alınır; ikinci bir liste/favori/ilerleme veya eklenti yedeği eklenmedi. Önceki değişiklikte eklenen 22 taşınabilir ayar ve mevcut arama geçmişi eşitlemesi korunuyor.

## Dağıtım: zorunlu sunucu adımı

1. Mevcut Supabase projesinde `supabase/schema.sql` kurulmuş olmalı.
2. Supabase SQL Editor'da **`supabase/migrations/20261009_vault_compare_and_swap.sql`** uygulanmalı.
3. Mevcut kurulumlarda PostgREST tablo izinleri eksikse **`supabase/migrations/20261010_user_data_authenticated_grants.sql`** de uygulanmalı. `authenticated` rolü için RLS policy tanımlamak, `SELECT`/`INSERT`/`UPDATE`/`DELETE` tablo grant'lerinin yerine geçmez.
4. Ardından yeni APK tüm cihazlara kurulmalı. SQL, eski istemcilerin kasa satırlarını doğrudan ezmesini engeller; eski istemcide kasa yazma hatası beklenir.

Migration otomatik olarak canlı sunucuya uygulanmadı. Yeni istemci eksik RPC durumunda güvenli olmayan upsert'e geri dönmez: hata gösterir ve uygun arka plan işini tekrar dener. Bu dosya yalnızca uygulama içine konarak sunucu özelliği etkinleşmez.

## Giriş hatasının kökü

`Failed resolution of: Lio/ktor/client/plugins/HttpTimeout;`: Supabase 3.0.3 ile Ktor OkHttp 2.3.12 birlikte tanımlanmıştı. Supabase 3.0.3 upstream kataloğu Ktor 3.0.2 kullanıyor (GitHub API ile kontrol edildi). Motor 3.0.2'ye hizalandı ve Ktor BOM eklendi. Release APK bağımlılık grafiği ve cihaz testi hâlâ gerekli.

## Kalıcı yedekleme

- Token değişikliği WorkManager'a kullanıcı ID'siyle anında kuyruklanır; iş en az 3 saniye gecikme ve ağ bağlantısı şartıyla çalışır. İş girdisinde şifre/token/anahtar bulunmaz.
- `APPEND_OR_REPLACE`: yükleme sürerken gelen bir sonraki değişiklik kaybolmaz. Ağ/sunucu hatalarında exponential backoff ile retry yapılır.
- 15 dakikalık periyodik güvenlik işi, dinleyici/kuyruk yazımı civarında süreç ölmesi gibi kaçan değişiklikleri yeniden yakalar. Bu kesin çalıştırma saati değildir; Android erteler.
- Uygulama açılışı oturum yüklenmesini bekler, periyodik işi kaydeder ve tek kurtarma işi planlar.
- İşler kullanıcıya bağlıdır. A'nın işi B'nin oturumuna yüklenmez; Auth değişimleri ve yüklemeler aynı mutex ile sıralanır. Çıkışta işler iptal edilir.
- Çıkış öncesi açık kasa eşitlenir; başarısızsa yedeklenmemiş token'ları korumak için çıkış tamamlandı denmez ve anahtar silinmez.
- Kilitli kasa/aynı-servis çakışması ağ retry döngüsüyle çözülmez: kullanıcı işlemi beklenir.
- Yedek durumu ve başarılı kontrol/yükleme zamanı gösterilir.
- Android'in zorla durdurması, uygulama verilerinin silinmesi/kaldırılması, sürekli çevrimdışı kalma veya OEM arka plan kısıtlamaları aşılamaz. Force-stop sonrası kullanıcı uygulamayı tekrar açmalıdır.

## Çok cihaz çakışmaları

- Her servis bir atomik gruptur: access token, refresh token, süre ve kullanıcı bilgileri farklı cihazlardan karıştırılmaz. Ayarlar anahtar bazında gruplandırılır.
- Yerel son gözlenen durum ile uzak son kullanılan durumun SHA-256 özetleri kullanıcıya bağlı olarak saklanır. Token'ın kendisi baseline içine yazılmaz.
- Yerelde değişiklik yoksa uzak veri korunur. Uzaktaki grup değişmemişse yerel değişiklik alınır. Farklı servislerdeki değişiklikler birleşir.
- Aynı servis iki tarafta değişmişse yükleme durur; hangi kopyanın korunacağı hesap ekranında onayla seçilir. Bağlantı kaldırılması da değişikliktir.
- Bulutta korunan ama cihazın canlı oturumuna kurulmamış token, kurulmuş gibi kabul edilmez; sonradan yalnızca kullanıcı adı değişmesi eski token'ı sessizce yeniden yükleyemez.
- Seçim ekranından sonra uzak veri değişmişse eski seçim uygulanmaz; yeniden eşitleme istenir.
- **RPC atomik compare-and-swap** yapar. Okuma ile yazma arasında başka cihaz kazanmışsa eski yazma reddedilir, güncel veriyle en fazla dört kez birleştirme denenir; sonra WorkManager tekrar dener.
- RLS restrictive politikaları eski istemcilerin RPC'yi atlayarak insert/update/delete yapmasını engeller. RPC SECURITY DEFINER'dır ama kullanıcıyı parametreden almaz: yalnızca `auth.uid()` sahibinin iki izinli kasa satırına erişir. Anonymous/PUBLIC execute yetkisi yoktur.
- Yalnızca bulutta değişen hesap bilgileri çalışan servis oturumuna arka planda zorla yazılmaz. Yeni/temiz cihaz girişinde geri yüklenir. Kullanıcı çakışmada bulutu seçerse bu cihazda kullanmak için çıkış/giriş yönergesi gösterilir.
- Servis iki cihazın aynı refresh token'ı eşzamanlı kullanmasını iptal ediyorsa bunu Kitsugi veritabanı çözemez. Hangi token'ın hizmet tarafından geçerli sayıldığının kesin testi hizmet API'sidir; gerektiğinde o servise yeniden giriş gerekir.

## Şifre değiştirme

Hesap ekranında mevcut/yeni/yeni-tekrar şifreli bir diyalog eklendi. Şifreler `remember` belleğinde, saved-state dışında tutulur; diyalog kapatılınca temizlenir.

1. Mevcut şifreyle Auth yeniden doğrulanır.
2. Aynı rastgele kasa anahtarı yeni şifreyle sarılır. Eski wrap korunarak `pending` wrap CAS ile kaydedilir.
3. Bu kayıt başarılı olmadan Auth şifresi değiştirilmez.
4. Auth güncellenince yeni wrap CAS ile aktif yapılır, eski wrap kaldırılır.

**Kesinti:** Auth güncellemesinin gerçekleşip gerçekleşmediği belirsizse eski wrap silinmez ve kör rollback yapılmaz. Kullanıcı yeni şifreyle giriş yapabiliyorsa pending wrap açılır ve geçiş tamamlanabilir. Eski şifre hâlâ geçerliyse aynı hedef yeni şifreyle işlem tekrar edilir. Yarım kalmış değişiklik varken farklı hedef şifreye geçiş engellenir; iki cihaz birbiri üzerine farklı şifre geçişi başlatamaz.

Eski şifreyle giriş pending wrap'i silmez: başka cihazdaki Auth isteği henüz sürüyor olabilir. Yalnızca yeni şifreyle başarılı doğrulama veya başarılı Auth güncellemesi geçişi sonlandırır. Payload'ın yeniden şifrelenmesi gerekmez.

Bu bir **şifre sıfırlama/kurtarma** özelliği değildir. Eski kasayı açabilen şifre kaybolmuşsa yedek kurtarılamaz. Supabase güvenli şifre değişimi için ek doğrulama/OTP isterse hata gösterilir; değişiklik başarılı gibi sunulmaz.

## Güvenlik ve uyumluluk

- Bulut payload: AES-256-GCM; şifreyle sarma: PBKDF2-HMAC-SHA256, 210.000 iterasyon, rastgele salt/IV. Önceki blob biçimi ve salt/iterasyonları korunur.
- Yerel kasa anahtarı Android Keystore ile şifrelenir; owner ID AAD olarak bağlıdır. Android backup/device transfer dışında tutulur.
- Token kullanan eski servis kodu hâlâ MyWebViewPrefs kullanır; bütün yerel token depoları Keystore'a taşınmış değildir.
- Eski sahipsiz düz metin kasa anahtarı kullanılmaz; tekrar giriş gerekir.
- Auth şifresi doğal olarak kimlik doğrulama sırasında TLS ile Auth sunucusuna gider; bulut yedeğine veya iş kuyruğuna yazılmaz.
- Arama geçmişi mevcut RLS korumalı, şifrelenmemiş satırdır. Bu turda yeni veri kategorisi eklenmedi.
- Çıkışta yerel servis hesapları kalır. Farklı Kitsugi hesabına girişin bu yerel hesapları o hesaba yedekleyebileceği uyarısı korunur.

## Testler ve gerçek doğrulama durumu

### Çalıştırıldı

`scripts/test_vault_cas.mjs`: izole, bellekte PostgreSQL (PGlite) üzerinde **7 test geçti**:

1. İlk kasa oluşturma yalnızca bir kez kazanır.
2. Eski snapshot, yeni payload'ı ezemez.
3. Eski istemcinin doğrudan kasa yazma/silmesi engellenir.
4. Şifre geçişi CAS ile tek sahipli ve tek finalization'lıdır.
5. İki kullanıcının RLS/RPC izolasyonu.
6. Arama geçmişi gibi diğer satırların normal erişimi bozulmaz.
7. Yanlış satır anahtarı ve anonim RPC reddedilir.

Migration aynı veritabanında iki kez uygulanarak tekrar çalıştırılabilirliği de denetlendi. Bu testler canlı Supabase Auth veya cihaz testi değildir.

```
npm install --prefix /tmp/vault-tests @electric-sql/pglite
PGLITE_MODULE=/tmp/vault-tests/node_modules/@electric-sql/pglite/dist/index.js node scripts/test_vault_cas.mjs
```

`git diff --check` geçti.

### Eklendi, çalıştırılamadı

- `VaultMergeTest`: farklı servisler, aynı servis access/refresh atomikliği, bağlantı kaldırma, yeniden deneme, eski payload uyumu, ayarlar ve eski token'ın kullanıcı adı değişikliğiyle dirilmemesi (9 test).
- `VaultCryptoTest`: kesintide iki wrap, finalize sonrası eski şifrenin reddi, payload anahtarının korunması, bozuk ciphertext reddi, rastgele salt/IV (4 test).
- `AccountBackupTest`: Android Keystore sahiplik/round-trip, legacy key reddi, Ktor ABI ve ayar izin listesi.

Gradle test komutu Java/JAVA_HOME olmadığı için başlayamadı. APK ve Kotlin derlemesi, instrumentation, gerçek WorkManager süreç ölümü, Supabase Auth şifre değişimi ve iki gerçek cihaz testi henüz yapılmadı. Yayın öncesi:

- `./gradlew :app:testFossDebugUnitTest --tests 'com.kitsugi.animelist.data.account.*'`
- Debug ve minify edilmiş release APK derleme; Ktor dependencyInsight kontrolü.
- Test projesinde migration + iki kullanıcı RLS doğrulaması.
- Çevrimdışı token değiştir → süreci öldür → bağlantıyı aç → kuyruk ve ciphertext kontrolü.
- İki cihazda farklı servis değişiklikleri ve aynı servis çakışması; arada uzak veri değişirken seçim.
- Şifre değişikliğinin staging öncesi/sonrası, Auth isteği sırasında ve finalize öncesi bağlantısını keserek eski/yeni şifreyle kurtarma.
