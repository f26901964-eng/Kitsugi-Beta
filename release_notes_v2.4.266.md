# Kitsugi-Beta v2.4.266 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🌐 Tüm Hesap Girişi Ekranlarında Her Zaman Görünür 'Tarayıcıda Aç' Butonu
- **OAuth WebView Ekranı (AniList, MAL, Simkl):** "Tarayıcıda Aç" butonu daha önce yalnızca hata oluştuğunda çıkıyordu. Artık taze ekranda da her zaman "Kapat" butonuyla yan yana eşit genişlikte yer alıyor. Butona basıldığında yetki URL'si taze PKCE anahtar çiftiyle yeniden üretilir, varsayılan harici tarayıcı açılır ve dialog kapanır. Tarayıcıda onay verildikten sonra `malapp://auth` deep link şeması sayesinde otomatik olarak uygulamaya dönülüp token takası tamamlanır.
- **TV / QR Giriş Yolu (KitsugiWebViewDialog):** `KitsugiWebViewDialog` bileşenine `onOpenInBrowser` desteği eklendi; TV/QR dialogunda da harici tarayıcı butonu aktif hale getirildi.
- **Kitsu Giriş Ekranı:** API ile kullanıcı adı/şifre alanının altına hesap doğrulama veya şifre sıfırlama işlemleri için `kitsu.app/login` adresini açan "Tarayıcıda Aç" butonu ve bilgilendirme metni eklendi.

---

## 🇬🇧 English

### 🌐 Always-Visible 'Open in Browser' Button on All Account Login Screens
- **OAuth WebView Dialog (AniList, MAL, Simkl):** The "Open in Browser" button is now permanently visible alongside the dismiss button instead of only showing up on network errors. Tapping it re-generates the authorization URL with a fresh PKCE key pair, opens the default external browser, and closes the dialog. Returning via the `malapp://auth` deep link scheme automatically completes token exchange.
- **TV / QR Login Flow:** Added optional `onOpenInBrowser` support to `KitsugiWebViewDialog`, enabling the external browser button in TV/QR authorization dialogs.
- **Kitsu Dialog:** Added an "Open in Browser" button directing to `kitsu.app/login` beneath credentials fields for password resets and verification.
