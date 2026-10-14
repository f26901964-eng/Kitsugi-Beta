# KÖK NEDEN RAPORU — Uygulama İçi Hesap Girişi (AniList / MAL / Simkl) Başarısız, Harici Tarayıcı Çalışıyor

**Tarih:** 2026-10-14
**Sürüm:** v2.4.290
**Kullanıcı Bildirimi:** Hesap bağlantıları, uygulama içinde tarayıcı benzeri bir sayfada açılıyor; hesap girişi yapılmaya çalışılınca hata veriyor, giriş yapılmıyor ve izin alınmıyor. Yalnızca harici tarayıcıdan girince izin düzgün alınıyor. AniList ve MyAnimeList için bildirildi; aynı mekanizmayı kullanan tüm kaynaklar için geçerli olduğu belirtildi.

---

## 1. Belirti

- Ayarlar → Hesap Bağlantıları (veya profil sekmesi) → AniList / MyAnimeList / Simkl bağla:
  uygulama içi OAuth penceresi (`KitsugiOAuthWebViewDialog`) açılıyor.
- Kullanıcı giriş formunu doldurup izin vermeye çalıştığında hata alıyor; oturum kurulmuyor,
  izin verilmiyor. Cloudflare "Gerçek kişi olduğunuzu doğrulayın" kontrolü takılıyor.
- Aynı URL harici tarayıcıda ("Tarayıcıda Aç" butonu) sorunsuz çalışıyor; `malapp://auth`
  deep link'i ile uygulamaya dönüp token takası başarıyla tamamlanıyor.

## 2. Kanıt Zinciri ve Teşhis

### 2.1 Akışın eşlenmesi (hangi kaynak hangi mekanizmayı kullanıyor)

| Kaynak | Giriş yolu | Etkilenme |
|---|---|---|
| AniList | `AuthViewModel.startExternalAuth("anilist")` → **uygulama içi `KitsugiOAuthWebViewDialog`** | ✅ Etkileniyordu |
| MyAnimeList | `startMalOAuthWebSession()` → **uygulama içi `KitsugiOAuthWebViewDialog`** (tek oturum: jeton + web çerezi) | ✅ Etkileniyordu |
| Simkl (OAuth) | `startExternalAuth("simkl")` → **uygulama içi `KitsugiOAuthWebViewDialog`** | ✅ Etkileniyordu |
| TV QR girişi (telefon tarafı) | `KitsugiTvQrLoginDialog` → **uygulama içi `KitsugiWebViewDialog`** | ✅ Etkileniyordu |
| Kitsu | Kullanıcı adı/şifre (API), WebView yok | Etkilenmiyor |
| Shikimori | Harici tarayıcı (`Intent.ACTION_VIEW` + deep link) | Etkilenmiyor |
| Bangumi | Harici tarayıcı ("1-Tık Otomatik Giriş") / manuel kod | Etkilenmiyor |
| Simkl (PIN) | Harici tarayıcı + polling | Etkilenmiyor |

### 2.2 Öncül bulgu (depo içi kanıt)

`TASK_TUM_KAYNAKLAR_TARAYICI_GIRIS_BUTONU_2026-10-12.md` belgesi, AniList girişinin
uygulama içi WebView'da **Cloudflare doğrulamasında takılı kaldığını** ve o tarihte
yalnızca "Tarayıcıda Aç" kaçış butonu eklendiğini kaydediyor. Yani belirli o günden beri
bilinen ve çözülmemiş bir sorun.

### 2.3 Kök neden: SABİT SAHTE USER-AGENT

`KitsugiOAuthWebViewDialog` içinde:

```kotlin
// ESKİ (kaldırıldı):
userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
    "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
```

`KitsugiWebViewDialog` içinde de benzer sabit UA (`Chrome/124.0.0.0`, `Android 10; K`) vardı.

Neden bu giriş sayfalarını kırıyor:

1. **UA ↔ parmak izi uyumsuzluğu:** Cloudflare (AniList, Simkl) ve MAL WAF, istemcinin
   TLS Client Hello (JA3/JA4), HTTP/2 ayarları ve JS ortam telemetrisinden oluşan
   **gerçek parmak izini** beyan edilen User-Agent ile karşılaştırır. Cihazdaki WebView
   motoru gerçekte örneğin Chrome/14x iken ve cihaz gerçekte farklı bir modelken UA
   "Chrome/124, Android 13, Pixel 7" diyorsa bu uyumsuzluk **bot sinyalidir** → etkileşimli
   doğrulama (Turnstile) gelir.
2. **Doğrulama döngüsü:** Sahte UA ile çözülmeye çalışılan Turnstile challenge'ı
   WebView'da tamamlanamaz; kontrol tekrar tekrar istenir. Kullanıcı "hata veriyor,
   girmiyor, izni almıyor" olarak yaşıyor.
3. **Eski sürüm imzası:** Chrome/124, Nisan 2024 sürümüdür (2026'da çok eski); eski
   sürüm beyanları ayrıca daha agresif challenge tetikler.
4. Harici tarayıcıda aynı URL çalışır çünkü orada UA ve parmak izi **gerçektendir ve
   tutarlıdır**. Bu, kök nedenin istemci ortamı (WebView yapılandırması) olduğunu
   izole eden decisive testtir.

Not: UA spoof'unu originally tetikleyen problem (MAL authorize ucunun 401 Basic Auth
döndürmesi) daha önce **doğru istemci kimliğiyle** (`MAL_OAUTH_CLIENT_ID`, "Android"
tipi uygulama) çözülmüştü; sahte UA gereksizdi ve ters tepti.

### 2.4 İkincil kusurlar (aynı pencerede bulundu, düzeltildi)

1. **Korumasız çift yakalama:** `malapp://auth` dönüşü hem `onPageStarted` hem
   `shouldOverrideUrlLoading`'de (eski + yeni imza) kopyala-yapıştır bloklarla, tekrar
   işleme koruması olmadan ele alınıyordu. Çift tetiklenmede authorization code İKİ KEZ
   takas edilir; ikincisi "kod zaten kullanılmış" hatası üretir ve başarılı girişin
   üzerine hata gösterilir.
2. **JS yönlendirme boşluğu:** `window.location` ile yapılan dönüşler bazı WebView
   sürümlerinde `shouldOverrideUrlLoading`'e düşmez; `doUpdateVisitedHistory` kancası yoktu.
3. **Yanlış hata görüntüleme:** Özel şemalı dönüş (`malapp://auth?code=...`) WebView
   yükleyicisinde `ERR_UNKNOWN_URL_SCHEME` üretir; `onReceivedError` bunu ana çerçeve
   hatası sanıp kırmızı "Sayfa yüklenemedi" banner'ı gösteriyordu — izin verilmiş
   olsa bile kullanıcı hatayla karşılaşıyordu.

## 3. Yapılan Düzeltmeler

### 3.1 `KitsugiOAuthWebViewDialog.kt` (AniList / MAL / Simkl)

- **`userAgentString` ataması tamamen kaldırıldı** → sistem WebView'inin gerçek varsayılan
  UA'sı kullanılır. (Kök neden düzeltmesi.)
- **Tek işleyici + çift takas koruması:** `handleAuthRedirect(rawUrl)` tek fonksiyon;
  `authRedirectHandled` bayrağı ile aynı dönüş en fazla bir kez işlenir. Tüm yakalama
  kanalları buna bağlandı: `onPageStarted`, `doUpdateVisitedHistory`,
  `shouldOverrideUrlLoading` (eski + yeni imza).
- **`stopLoading()`:** Dönüş yakalandığında WebView özel şemayı yüklemeye devam edip
  hata sayfası üretmesin diye yükleme durdurulur.
- **`onReceivedError` filtresi:** `malapp://` ve `intent:` şemalı ana çerçeve hataları
  artık "Sayfa yüklenemedi" olarak gösterilmez.
- Korumalı davranışlar aynen korundu: JS + DOM storage + üçüncü parti çerezler,
  `CookieManager.flush()`, OkHttp cookie senkronu, Basic Auth popup iptali,
  "Tarayıcıda Aç" fallback butonu.

### 3.2 `KitsugiWebViewDialog.kt` (genel uygulama içi web penceresi)

- **`userAgentString` ataması kaldırıldı** (aynı kök neden). TV QR girişinin telefon
  tarafındaki AniList/MAL ekranı ve diğer sayfalar (eklenti, bildirim, profil, yayın)
  gerçek UA ile açılır.
- **`interceptRedirect` + `lastRedirectHandledUrl` koruması:** `onUrlRedirect` tüketimi
  tek noktadan, çift işlemeye karşı korumalı; `onPageStarted` ve `doUpdateVisitedHistory`
  kanalları da eklendi.

### 3.3 Etkilenmeyen ama doğrulanan yollar

- Kitsu, Shikimori, Bangumi, Simkl PIN: harici tarayıcı / API — değişiklik gerekmedi.
- MAL tek oturum (jeton + web çerezi) doğrulaması: çerezler WebView CookieManager'dan
  okunmaya devam eder (`MalWebSessionStore`), UA değişikliğinden etkilenmez.

## 4. Doğrulama

- Android SDK bu ortamda bulunmadığı için APK derlemesi yapılamadı.
- Yapılan kontroller:
  - İki dosyada da süslü/normal parantez dengesi programatik olarak doğrulandı
    (`{`=66/`}`=66 ve `{`=47/`}`=47; parantezler eşit).
  - Aktif kodda `userAgentString =` ataması kalmadığı; `Chrome/124`-`Chrome/120`
    dizgilerinin yalnızca açıklama yorumlarında geçtiği doğrulandı.
  - Tüm yeni string/kaynak referansları mevcut kaynakları kullanır (yeni `strings.xml`
    girdisi gerekmedi).
  - `handleAuthIntent` çağrı imzası (`context, intent, onSuccess, onError`) korundu.
- Cihaz testi (kullanıcı): AniList ve MAL girişi uygulama içi pencereden denenmeli;
  Cloudflare kontrolü gelirse artık tamamlanabilir. Gerekirse "Tarayıcıda Aç" hâlâ
  tek dokunuşluk çıkış yoludur.

## 5. Artık Riskler / Notlar

- **Kaynak sitesi Cloudflare bypass zinciri bilinçli olarak değiştirilmedi:**
  `WebViewResolver`/`KitsugiUserAgent`/`NuvioOkHttpProvider` (manga/eklenti kaynakları)
  kendi tutarlı UA çiftini kullanmaya devam ediyor. Orada OkHttp UA ile WebView UA'sı
  eşleştiği için `cf_clearance` düzeni korunur. İleride orası da gerçek UA'ya
  taşınmak istenirse OkHttp tarafıyla BİRLİKTE değiştirilmelidir.
- Sağlayıcı tarafı agresif challenge moduna alınırsa (IP itibarı vb.) hiçbir WebView
  geçmeyebilir; bu durumda "Tarayıcıda Aç" devreye girer (her zaman görünür buton).
- MAL web oturumu doğrulaması, girişten sonra tek geçişli yeniden giriş akışını
  (mismatch durumunda) aynen korur.

## 6. Değişen Dosyalar

```
app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiOAuthWebViewDialog.kt
app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiWebViewDialog.kt
app/build.gradle.kts                       (sürüm: 2.4.289 → 2.4.290)
release_notes_v2.4.290.md                  (yeni)
```
