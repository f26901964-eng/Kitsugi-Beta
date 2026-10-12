# Kitsugi-Beta v2.4.262 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🔐 1. MyAnimeList: OAuth Hesabı ⇄ Web Oturumu Eşleme Kontrolü (Yanlış Hesap Bildirimleri Önlendi)
- **Sorun:** MAL bildirimleri iki ayrı kimlikle çalışır: bağlı OAuth hesabı (`users/@me`) ve
  WebView'deki web oturumu (`is_signed_in=1`). WebView'de **başka bir MAL hesabı** açık kalmışsa
  bildirim sayfası o yabancı hesabın bildirimlerini döndürüyordu ve kullanıcı bunu kendi listesi
  sanıyordu — sessiz kimlik karışması.
- **Çözüm:** Her yüklemede iki kimlik karşılaştırılıyor.
  - Web oturumunun sahibi, bildirim sayfasının HTML'indeki profil bağlantısından okunuyor
    (önce sayfa başlığındaki `header-profile` bağlantısı, bulunamazsa sayfada en sık geçen
    profil bağlantısı). `window.MAL.notification` verisi taramadan çıkarılıyor; böylece
    bildirimi gönderen kişiler asla oturum sahibi sanılmıyor.
  - Bağlı OAuth hesabının adı `users/@me` çağrısından **ham** olarak alınıyor
    (`MalImportManager.fetchUserName`); profilde kullanılan yerelleştirilmiş yedek ad
    bilinçli olarak kullanılmıyor.
- **Eşleşmezse:** Bildirimler **gösterilmez**; "Bağlı OAuth hesabı ile web oturumu eşleşmiyor,
  lütfen doğru hesapla giriş yapın." uyarısı, iki hesabın kullanıcı adları ve
  **"Oturumu temizle ve yeniden giriş yap"** düğmesi gösterilir.
- **Oturum temizleme:** Düğme yalnızca `myanimelist.net` çerezlerini temizler
  (`MalWebSessionStore.clearSession`); OAuth jetonları ve diğer platformların oturumları
  silinmez. Ardından giriş ekranı doğru hesapla açılır.
- **Doğrulanamazsa web oturumu okunamadığında (ör. MAL sayfa yapısı değişirse) liste
  gösterilmeye devam eder**, yalnızca bilgi notu düşülür. Özellik tek bir ayrıştırıcı
  değişikliğiyle tamamen kilitlenmesin diye bu durumda katı davranılmıyor.
- **Teşhis ekranı:** MAL satırı artık kimlik özetini de yazıyor (`hesap: X` /
  `UYARI: OAuth=X ≠ web=Y`); eşleşmeme durumunda satır hatalı işaretlenip çözüm öneriliyor.

### 🧪 2. Testler
- `MalNotificationsClientTest`: oturum sahibinin başlık bağlantısından okunması, sıklık
  yedeği, bildirim JSON'undaki yabancı profillerin dışlanması, ayrılmış yol (`/profile/login`)
  ve salt sayısal değerlerin yok sayılması.
- `MalAccountMatchTest`: eşleşme/eşleşmeme, büyük-küçük harf ve boşluk toleransı, jeton
  yokken ve kimlik okunamadığındaki durumlar.

---

## 🇬🇧 English

### 🔐 1. MyAnimeList: OAuth Account ⇄ Web Session Matching (Wrong-Account Notifications Prevented)
- **Problem:** MAL notifications rely on two separate identities: the linked OAuth account
  (`users/@me`) and the WebView website session (`is_signed_in=1`). If another MAL account was
  left signed in inside the WebView, the notification page returned **that** account's
  notifications while the user assumed they were their own — a silent identity mix-up.
- **Fix:** Both identities are compared on every load.
  - The session owner is read from the notification page HTML (first the header `header-profile`
    link, then the most frequent profile link on the page as a fallback). The
    `window.MAL.notification` payload is excluded from the scan, so senders, friends and
    commenters can never be mistaken for the session owner.
  - The linked OAuth account name is taken **raw** from `users/@me`
    (`MalImportManager.fetchUserName`); the localized fallback name used by the profile screen is
    deliberately not used for identity checks.
- **On mismatch:** Notifications are **not shown**. The screen shows "The linked OAuth account
  does not match the web session, please sign in with the correct account.", both usernames,
  and a **"Clear session and sign in again"** button.
- **Clearing the session:** The button only expires `myanimelist.net` cookies
  (`MalWebSessionStore.clearSession`); OAuth tokens and other platforms' sessions are untouched.
  The sign-in screen then opens for the correct account.
- **If the session owner cannot be read** (e.g. MAL changes its markup) the list keeps working
  and only an informational note is added — a single parser change should not disable the whole
  feature.
- **Diagnostics:** The MAL row now reports the identity summary (`account: X` /
  `WARNING: OAuth=X ≠ web=Y`) and is flagged as failed with a suggested fix on mismatch.

### 🧪 2. Tests
- `MalNotificationsClientTest`: reading the session owner from the header link, the frequency
  fallback, excluding foreign profiles from the notification JSON, and ignoring reserved paths
  (`/profile/login`) and numeric-only values.
- `MalAccountMatchTest`: match/mismatch, case and whitespace tolerance, and the no-token /
  unreadable-identity states.
