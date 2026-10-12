# Kitsugi-Beta v2.4.262 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🔔 1. MyAnimeList Bildirimleri Artık Gerçekten Geliyor (Kök Neden Düzeltmesi)
- **Belirti:** MAL hesabı bağlı olmasına rağmen Bildirimler > MyAnimeList sekmesi sürekli "MAL web oturumunu bağla" diyor ve gerçek bildirimler yerine 7 günlük yayın takvimi gösteriyordu.
- **Kök Neden:** Uygulama, MAL'ın **hiç yazmadığı** `is_signed_in=1` çerezini arıyordu. Bu yüzden WebView'de geçerli bir MAL oturumu dursa bile oturum "yok" sayılıyor ve bildirim isteği hiç atılmıyordu.
- **Düzeltme:** Gerçek MAL oturum çerezleri tanınıyor: `MALHLOGSESSID` ve `MALSESSIONID`. `is_logged_in` / `is_signed_in` bayrakları tek başına oturum sayılmıyor (çıkış kalıntıları eleniyor).
- **Kanıtlı Karar:** Oturum artık çerez adına bakılarak tahmin edilmiyor; MAL'a istek atılır ve giriş sayfasına düşülürse oturum istenir. MAL giriş formunu 200 ile döndürdüğünde de bu doğru şekilde "oturum gerekli" olarak yorumlanıyor.

### 🔐 2. MyAnimeList: OAuth Hesabı ⇄ Web Oturumu Eşleşme Kontrolü
- **Sorun:** MAL bildirimleri iki ayrı kimlikle çalışır: bağlı OAuth hesabı (`users/@me`) ve WebView'deki web oturumu. WebView'de başka bir MAL hesabı açık kaldığında bildirim sayfası o yabancı hesabın bildirimlerini döndürüyordu — sessiz kimlik karışması.
- **Çözüm:** Her yüklemede iki kimlik karşılaştırılıyor:
  - Web oturumunun sahibi bildirim HTML'indeki `header-profile` bağlantısından (bulunamazsa sayfadaki profil sıklığı yedeğinden) okunuyor. `window.MAL.notification` verisi taramadan çıkarılarak göndericilerin oturum sahibi sanılması önlendi.
  - Bağlı OAuth hesabının adı `MalImportManager.fetchUserName()` ile `users/@me` uç noktasından ham olarak alınıyor.
- **Eşleşmeme Durumu:** Hesaplar eşleşmezse bildirimler gizleniyor, her iki hesabın adı gösteriliyor ve **"Oturumu temizle ve yeniden giriş yap"** butonu sunuluyor.
- **Güvenli Oturum Temizleme:** Yalnızca `myanimelist.net` çerezleri süresi geçmiş olarak silinir; OAuth jetonları ve diğer platformların oturumlarına dokunulmaz.

### ⚡ 3. Otomatik Oturum Penceresi ve Teşhis Ekranı
- MAL OAuth ile bağlı olduğu hâlde web oturumu gerçekten yoksa giriş penceresi **bir kez kendiliğinden** açılır. Giriş tamamlandığında pencere kapanır ve bildirimler otomatik yüklenir.
- MAL tanı satırı artık bulunan çerez adlarını (`MALHLOGSESSID`, vb.) ve kimlik özetini (`hesap: X` / `UYARI: OAuth=X ≠ web=Y`) raporlar; çerez değerleri asla gösterilmez.

### 🔍 4. Arama Ekranı: Dikey ve Yatay Uyumlu Shimmer İskeletleri
- **Duyarlı Yükleme İskeleti:** `KitsugiShimmerResponsiveMediaRow` bileşeni eklendi.
  - Dikey ekranda gerçek sonuç kartlarıyla birebir uyumlu `2:3` poster oranı.
  - Yatay ekranda solda poster ve sağda başlık/meta bilgi düzeni.
  - Android TV için dikey poster düzeni korundu.
- **Yatay Kaydırılabilir Raflar:** Sonuçlar gelmeden önce iskelet rafları `LazyRow` ile yatay kaydırılabiliyor.
- **Bağımsız Kaynak Geçişi:** Çoklu kaynak aramasında her kaynağın kendi sonucu geldikçe yalnızca o raf iskeletten gerçek kartlara geçiyor.

### 🧪 5. Birim Testleri
- `MalWebSessionStoreTest`: Gerçek çerez (`MALHLOGSESSID`), bayrak kalıntıları ve çerez adı ayrıştırma vakaları test edildi.
- `MalAccountMatchTest`: Tam eşleşme, eşleşmeme, büyük/küçük harf toleransı ve kimlik okunamama durumları test edildi.
- `MalNotificationsClientTest`: Oturum sahibinin başlık bağlantısından okunması ve bildirim payload profillerinin dışlanması doğrulandı.

---

## 🇬🇧 English

### 🔔 1. MyAnimeList Notifications Actually Load Now (Root-Cause Fix)
- **Symptom:** With a connected MAL account, the Notifications > MyAnimeList tab kept asking to "Connect MAL web session" and showed the 7-day airing calendar instead of real notifications.
- **Root Cause:** The app looked for an `is_signed_in=1` cookie that MAL never sets. Even with a valid MAL session in the in-app WebView, the session was considered missing, so the notification request was never sent.
- **Fix:** Real MAL session cookies are recognized (`MALHLOGSESSID`, `MALSESSIONID`). The `is_logged_in` / `is_signed_in` flags never count on their own, and stale sign-out leftovers are ignored.
- **Evidence-Based Decision:** The session is verified directly via HTTP requests to MAL rather than guessing cookie names.

### 🔐 2. MyAnimeList: OAuth Account ⇄ Web Session Identity Matching
- **Problem:** MAL notifications rely on two separate identities: the linked OAuth account (`users/@me`) and the WebView web session. A different MAL account left logged in inside WebView resulted in displaying foreign notifications — silent identity collision.
- **Fix:** Both identities are validated on every load:
  - Web session owner is parsed from notification page HTML (`header-profile` link, with frequency fallback). The `window.MAL.notification` payload is excluded to avoid mistaking notification senders for the owner.
  - Linked OAuth username is obtained raw via `MalImportManager.fetchUserName()` from `users/@me`.
- **Mismatch Protection:** Notifications are concealed on mismatch; both usernames are displayed along with a **"Clear session and sign in again"** action.
- **Targeted Session Reset:** Safely expires only `myanimelist.net` cookies without clearing OAuth credentials or other platform sessions.

### ⚡ 3. Automatic Session Prompt & Enhanced Diagnostics
- If MAL OAuth is connected but no website session exists, the sign-in dialog opens automatically once, flushing cookies upon login.
- MAL diagnostic row now reports cookie presence (`MALHLOGSESSID`, etc.) and identity status (`account: X` / `WARNING: OAuth=X ≠ web=Y`) without ever exposing cookie values.

### 🔍 4. Search Screen: Adaptive Portrait & Landscape Shimmer Skeletons
- **Responsive Loading Skeletons:** Introduced `KitsugiShimmerResponsiveMediaRow`.
  - Portrait orientation matches true `2:3` poster aspect ratio and metadata structure.
  - Landscape orientation matches true wide card layout (left poster, right metadata block).
  - Preserved vertical poster orientation for Android TV.
- **Scrollable Placeholders:** Loading shelves now utilize `LazyRow` for fluid horizontal scrolling.
- **Independent Shelf Transitions:** Each provider independently transitions from shimmer to resolved media cards as results stream in.

### 🧪 5. Unit Tests
- `MalWebSessionStoreTest`: Validates session cookie presence (`MALHLOGSESSID`), flag handling, and cookie name sanitization.
- `MalAccountMatchTest`: Verifies exact matching, mismatches, case tolerance, and unreadable identity fallbacks.
- `MalNotificationsClientTest`: Validates session owner extraction from headers while excluding nested notification sender profiles.
