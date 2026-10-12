# Görev: Tüm hesap girişi ekranlarında "Tarayıcıda Aç" butonu

**Tarih:** 2026-10-12
**Durum:** Tamamlandı

---

## 1. Sorun

Kullanıcı, hangi kaynak üzerinden hesap bağlama yaparsa yapsın, açılan **uygulama içi
tarayıcı (WebView) giriş ekranında** bir çıkış butonu olmasını ve bu butona basıldığında
**3. parti (varsayılan) tarayıcıya otomatik yönlendirilmesini** istiyor.

Ekran görüntüsünde görülen durum: AniList girişi uygulama içi WebView'de açılıyor ve
Cloudflare "Gerçek kişi olduğunuzu doğrulayın" kontrolü takılı kalıyor. Eski davranışta
"Tarayıcıda Aç" butonu **yalnızca hata oluştuğunda** (`errorMessage != null`) görünüyordu;
yani kullanıcı taze bir ekranda hiçbir seçenek göremiyor, WebView'a hapsoluyordu.

## 2. Yapılan değişiklikler

### 2.1 `KitsugiOAuthWebViewDialog.kt` (AniList / MyAnimeList / Simkl) — ana düzeltme

- Footer'a **her zaman görünür** bir `OutlinedButton` eklendi: **"Tarayıcıda Aç"**
  (ikonlu, `weight(1f)` ile "Kapat" butonuyla eşit genişlikte).
- Butona basıldığında:
  1. `ExternalAuthManager.getAuthUrlAndPrepare(context, serviceName)` ile yetki URL'si
     **taze PKCE code_verifier / code_challenge** çiftiyle yeniden üretilir.
     (WebView açılırken üretilen verifier'ı kullanmak yerine taze üretmek kritik:
     tarayıcı yeni challenge'a gider, token takası da yeni verifier ile yapılır.)
  2. `Intent.ACTION_VIEW` + `FLAG_ACTIVITY_NEW_TASK` ile varsayılan tarayıcı açılır.
  3. `CookieManager.flush()` çağrılır, kullanıcıya bilgi toast'ı gösterilir ve dialog kapanır.
- `malapp://auth` geri dönüş şeması `AndroidManifest.xml` içinde kayıtlı olduğu için
  kullanıcı tarayıcıda girişi bitirince **otomatik olarak uygulamaya döner** ve
  `MainActivity.handleAuthIntent()` → `ExternalAuthManager.handleAuthIntent()` token'ı takas eder.
- Footer, "Güvenli bağlantı • <host>" satırının altına yeni bir açıklama satırı da aldı:
  *"Uygulama içi ekranda sorun yaşıyorsanız 'Tarayıcıda Aç' ile varsayılan tarayıcınızda
  giriş yapın; işlem bitince Kitsugi'ye otomatik olarak dönersiniz."*
- Eski, hatada gizli kalan buton ve tekrar eden `when(serviceName)` ifadesi
  (`serviceHost` değişkenine taşındı) temizlendi.

### 2.2 `KitsugiWebViewDialog.kt` + `KitsugiTvQrLoginDialog.kt` (TV / QR giriş yolu)

- `KitsugiWebViewDialog`'a geriye dönük uyumlu, opsiyonel `onOpenInBrowser: (() -> Unit)?`
  parametresi eklendi. Verildiğinde footer'da "Tarayıcıda Aç" butonu çıkar.
- `KitsugiTvQrLoginDialog` bu parametreyi dolduruyor: AniList / MAL için uygulama içi WebView
  yerine varsayılan tarayıcıda giriş açılır. (Bu dialog telefon ayarlar sayfasından da
  erişilebildiği için telefon kullanıcıları da bu yoldan etkileniyordu.)
- Diğer `KitsugiWebViewDialog` kullanımları (eklenti sayfaları, bildirimler, profil, yayın)
  değişmedi — parametre `null` olduğunda hiçbir görsel değişiklik olmaz.

### 2.3 `KitsugiKitsuLoginDialog.kt` (Kitsu)

- Kitsu kimlik doğrulaması API üzerinden kullanıcı adı/şifre ile yapıldığı için web tabanlı
  bir OAuth akışı yok; bu yüzden "Giriş Yap" satırının altına **"Tarayıcıda Aç"** butonu
  eklendi (`https://kitsu.app/login`). Amaç: hesabı tarayıcıda doğrulama / şifre sıfırlama.
- Butonun altına bunu açıklayan kısa bir bilgi metni eklendi.

### 2.4 Zaten tarayıcıya açan kaynaklar (değişiklik gerekmedi)

| Kaynak | Mevcut davranış |
|---|---|
| MyAnimeList | `AuthViewModel.startExternalAuth("mal")` doğrudan harici tarayıcıya atıyor |
| Bangumi | "1-Tık Otomatik Giriş (Önerilen)" + "Alternatif şema" → `Intent.ACTION_VIEW` |
| Shikimori | "1-Tık Otomatik Giriş (Önerilen)" + "Tarayıcıda Aç (Kodu Manuel Al)" → `Intent.ACTION_VIEW` |
| Simkl (PIN) | "Kodu Kopyala & Tarayıcıyı Aç" |

## 3. Yeni metin kaynakları

`app/src/main/res/values/strings.xml` ve `values-en/strings.xml`:

| Anahtar | TR | EN |
|---|---|---|
| `oauth_webview_tarayiciya_devam_ettir` | Uygulama içi ekranda sorun yaşıyorsanız 'Tarayıcıda Aç' ile... | If the in-app screen is giving you trouble, use 'Open in Browser'... |
| `oauth_webview_tarayici_toast` | Tarayıcıda girişi tamamlayın. İşlem bitince Kitsugi'ye otomatik olarak döneceksiniz. | Finish signing in your browser... |
| `kitsu_login_tarayici_aciklamasi` | Hesabınızı tarayıcıda doğrulamak veya şifrenizi sıfırlamak istiyorsanız... | Use 'Open in Browser' if you want to verify your account... |

Buton etiketi için mevcut `inline_open_in_browser_eed20ab` ("Tarayıcıda Aç" / "Open in Browser")
yeniden kullanıldı.

## 4. Doğrulama

- Android SDK bu geliştirme ortamında bulunmadığı için APK derlenemedi.
- Yapılan kontroller:
  - Değişen tüm `.kt` dosyalarda parantez/süslü parantez dengesi tarayıcı ile doğrulandı.
  - `strings.xml` (TR + EN) XML olarak ayrıştırıldı, 6985 kayıt, tekrar eden anahtar yok.
  - Apostrophe kaçışları (`\'`) Android kaynak kuralına uygun yazıldı.
  - `KitsugiWebViewDialog`'un BOM'u korundu.
  - Yeni string referanslarının tamamı her iki dil dosyasında mevcut.

## 5. Değişen dosyalar

```
app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiOAuthWebViewDialog.kt
app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiWebViewDialog.kt
app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiTvQrLoginDialog.kt
app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiKitsuLoginDialog.kt
app/src/main/res/values/strings.xml
app/src/main/res/values-en/strings.xml
```
