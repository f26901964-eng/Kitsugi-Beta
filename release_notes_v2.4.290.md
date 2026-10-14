# Kitsugi Beta v2.4.290

## 🇹🇷 Türkçe

### Hesap Girişi (AniList / MyAnimeList / Simkl) Uygulama İçi Ekran Kök Neden Düzeltmesi
- **Uygulama İçi Giriş Artık Çalışıyor:** Hesap bağlantıları ekranından açılan uygulama içi tarayıcı benzeri OAuth penceresinde giriş yapılamıyor, Cloudflare/MAL WAF "Gerçek kişi misiniz?" doğrulamasında takılıyor ve izin verilmiyordu; yalnızca harici tarayıcıdan giriş çalışıyordu. Bu sürümde uygulama içi akış kök nedeninden düzeltildi.
- **Sahte User-Agent Kaldırıldı (Kök Neden):** OAuth WebView'ine sabit "Chrome/124 (Android 13, Pixel 7)" sahte User-Agent'ı gönderiliyordu. Cloudflare ve MAL WAF, isteğin gerçek TLS + JavaScript parmak izini beyan edilen UA ile karşılaştırır; uyumsuzluk bot sinyalidir ve doğrulama döngüsü üretir. Artık sistem WebView'inin GERÇEK varsayılan UA'sı (cihazın gerçek WebView sürümü ve modeli) kullanılır — parmak iziyle tutarlı olduğu için harici tarayıcıdakine yakın geçiş sağlar. Aynı düzeltme genel uygulama içi web penceresine (TV QR girişi, eklenti/bildirim/profil sayfaları) de uygulandı.
- **OAuth Geri Dönüşü Tek Noktadan, Çift Takas Korumasıyla:** `malapp://auth` geri dönüşü daha önce iki ayrı kanalda (`onPageStarted` + `shouldOverrideUrlLoading`), korumasız ve kopyala-yapıştır kodla işleniyordu; çift tetiklenmede kod iki kez takas edilip "kod zaten kullanılmış" hatası üretebiliyordu. Artık tek bir korumalı işleyici (`handleAuthRedirect`) üzerinden yürür.
- **JS Yönlendirmeleri de Yakalanıyor:** `doUpdateVisitedHistory` kancası eklendi; bazı WebView sürümlerinde `window.location` ile yapılan OAuth geri dönüşleri `shouldOverrideUrlLoading`'e düşmüyordu ve izin sonrası bağlantı sessizce başarısız oluyordu.
- **Yanlış Hata Mesajı Engellendi:** `malapp://` gibi özel şemalı geri dönüşlerde WebView'in ürettiği `ERR_UNKNOWN_URL_SCHEME` artık "Sayfa yüklenemedi" hatası olarak gösterilmez; dönüş yakalanır, yükleme durdurulur ve token takası yapılır.
- **Etkilenen Kaynaklar:** AniList, MyAnimeList, Simkl (OAuth penceresi) ve TV QR girişinin telefon tarafındaki uygulama içi ekranı. Kitsu (kullanıcı adı/şifre), Shikimori, Bangumi ve Simkl PIN akışı zaten harici tarayıcıyı kullandığı için etkilenmiyordu.
- **MAL Tek Oturum Akışı Korundu:** Giriş sonrası OAuth jetonu + web oturumu doğrulaması ve bildirimler için MAL çerez yakalama mekanizması aynen çalışır; çerezler WebView CookieManager'dan okunmaya devam eder.

---

## 🇬🇧 English

### Root-Cause Fix for In-App Account Login (AniList / MyAnimeList / Simkl)
- **In-App Login Now Works:** Signing in from the browser-like in-app OAuth window (opened from the account connections screen) used to fail — Cloudflare/MAL WAF human-verification got stuck and permission was never granted; only the external browser worked. This release fixes the in-app flow at its root cause.
- **Spoofed User-Agent Removed (Root Cause):** The OAuth WebView sent a hardcoded fake User-Agent ("Chrome/124, Android 13, Pixel 7"). Cloudflare and MAL's WAF compare the client's real TLS + JavaScript fingerprint against the declared UA; the mismatch is a bot signal that triggers a verification loop. The system WebView's REAL default UA (device's actual WebView version and model) is now used — consistent with the fingerprint, closely matching the external-browser behavior. The same fix was applied to the generic in-app web window (TV QR login, add-on/notification/profile pages).
- **Single-Point OAuth Redirect Handling with Double-Exchange Guard:** The `malapp://auth` return was previously handled in two separate hooks (`onPageStarted` + `shouldOverrideUrlLoading`) with duplicated, unguarded code; on double firing the code could be exchanged twice, producing an "authorization code already used" error. It now flows through one guarded handler (`handleAuthRedirect`).
- **JS Redirects Are Now Caught:** A `doUpdateVisitedHistory` hook was added; on some WebView versions, OAuth returns performed via `window.location` never reached `shouldOverrideUrlLoading`, silently failing the connection after permission was granted.
- **No More Misleading Error:** The `ERR_UNKNOWN_URL_SCHEME` produced by special-scheme returns (`malapp://`) is no longer shown as a "page failed to load" error; the return is intercepted, loading is stopped, and the token exchange proceeds.
- **Affected Sources:** AniList, MyAnimeList, Simkl (OAuth window) and the phone-side in-app screen of the TV QR login. Kitsu (username/password), Shikimori, Bangumi, and the Simkl PIN flow already used the external browser and were unaffected.
- **MAL Unified Session Preserved:** Post-login OAuth token + web-session verification and the MAL cookie capture for notifications keep working; cookies are still read from the WebView CookieManager.
