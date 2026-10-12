# Kitsugi-Beta v2.4.262 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🔐 1. MyAnimeList: OAuth Hesabı ⇄ Web Oturumu Eşleşme Kontrolü
- **Sorun:** MAL bildirimleri iki ayrı kimlikle çalışır: bağlı OAuth hesabı (`users/@me`) ve WebView'deki web oturumu (`is_signed_in=1`). WebView'de başka bir MAL hesabı açık kaldığında bildirim sayfası o yabancı hesabın bildirimlerini döndürüyordu — sessiz kimlik karışması.
- **Çözüm:** Her yüklemede iki kimlik karşılaştırılıyor:
  - Web oturumunun sahibi bildirim HTML'indeki `header-profile` bağlantısından (bulunamazsa sayfadaki profil sıklığı yedeğinden) okunuyor. `window.MAL.notification` verisi taramadan çıkarılarak göndericilerin oturum sahibi sanılması önlendi.
  - Bağlı OAuth hesabının adı `MalImportManager.fetchUserName()` ile `users/@me` uç noktasından ham olarak alınıyor.
- **Eşleşmeme Durumu:** Hesaplar eşleşmezse bildirimler gizleniyor, her iki hesabın adı gösteriliyor ve **"Oturumu temizle ve yeniden giriş yap"** butonu sunuluyor.
- **Güvenli Oturum Temizleme:** Yalnızca `myanimelist.net` çerezleri süresi geçmiş olarak silinir; OAuth jetonları ve diğer platformların oturumlarına dokunulmaz.
- **Teşhis Ekranı:** Bildirim teşhis satırına kimlik özeti (`hesap: X` / `UYARI: OAuth=X ≠ web=Y`) eklendi.

### 🔍 2. Arama Ekranı: Dikey ve Yatay Uyumlu Shimmer İskeletleri
- **Duyarlı Yükleme İskeleti:** `KitsugiShimmerResponsiveMediaRow` bileşeni eklendi.
  - Dikey ekranda gerçek sonuç kartlarıyla birebir uyumlu `2:3` poster oranı.
  - Yatay ekranda solda poster ve sağda başlık/meta bilgi düzeni.
  - Android TV için dikey poster düzeni korundu.
- **Yatay Kaydırılabilir Raflar:** Sonuçlar gelmeden önce iskelet rafları `LazyRow` ile yatay kaydırılabiliyor.
- **Bağımsız Kaynak Geçişi:** Çoklu kaynak aramasında her kaynağın kendi sonucu geldikçe yalnızca o raf iskeletten gerçek kartlara geçiyor.

### 🧪 3. Birim Testleri
- `MalAccountMatchTest`: Tam eşleşme, eşleşmeme, büyük/küçük harf toleransı ve kimlik okunamama durumları test edildi.
- `MalNotificationsClientTest`: Oturum sahibinin başlık bağlantısından okunması ve bildirim payload profillerinin dışlanması doğrulandı.

---

## 🇬🇧 English

### 🔐 1. MyAnimeList: OAuth Account ⇄ Web Session Identity Matching
- **Problem:** MAL notifications rely on two separate identities: the linked OAuth account (`users/@me`) and the WebView web session (`is_signed_in=1`). A different MAL account left logged in inside WebView resulted in displaying foreign notifications — silent identity collision.
- **Fix:** Both identities are validated on every load:
  - Web session owner is parsed from notification page HTML (`header-profile` link, with frequency fallback). The `window.MAL.notification` payload is excluded to avoid mistaking notification senders for the owner.
  - Linked OAuth username is obtained raw via `MalImportManager.fetchUserName()` from `users/@me`.
- **Mismatch Protection:** Notifications are concealed on mismatch; both usernames are displayed along with a **"Clear session and sign in again"** action.
- **Targeted Session Reset:** Safely expires only `myanimelist.net` cookies without clearing OAuth credentials or other platform sessions.
- **Diagnostics:** Identity summary added to notification diagnostics (`account: X` / `WARNING: OAuth=X ≠ web=Y`).

### 🔍 2. Search Screen: Adaptive Portrait & Landscape Shimmer Skeletons
- **Responsive Loading Skeletons:** Introduced `KitsugiShimmerResponsiveMediaRow`.
  - Portrait orientation matches true `2:3` poster aspect ratio and metadata structure.
  - Landscape orientation matches true wide card layout (left poster, right metadata block).
  - Preserved vertical poster orientation for Android TV.
- **Scrollable Placeholders:** Loading shelves now utilize `LazyRow` for fluid horizontal scrolling.
- **Independent Shelf Transitions:** Each provider independently transitions from shimmer to resolved media cards as results stream in.

### 🧪 3. Unit Tests
- `MalAccountMatchTest`: Verifies exact matching, mismatches, case tolerance, and unreadable identity fallbacks.
- `MalNotificationsClientTest`: Validates session owner extraction from headers while excluding nested notification sender profiles.
