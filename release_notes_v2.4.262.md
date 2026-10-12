# Kitsugi-Beta v2.4.262 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🔔 1. MyAnimeList Bildirimleri Artık Gerçekten Geliyor (Kök Neden Düzeltmesi)
- **Belirti:** MAL hesabı bağlı olmasına rağmen Bildirimler > MyAnimeList sekmesi sürekli
  "MAL web oturumunu bağla" diyor ve gerçek bildirimler yerine 7 günlük yayın takvimi gösteriyordu.
- **Kök Neden:** Uygulama, MAL'ın **hiç yazmadığı** `is_signed_in=1` çerezini arıyordu. Bu yüzden
  uygulama içi WebView'de geçerli bir MAL oturumu dursa bile oturum "yok" sayılıyor ve bildirim
  isteği hiç atılmıyordu.
- **Düzeltme:** Gerçek MAL oturum çerezleri tanınıyor: `MALHLOGSESSID` ve `MALSESSIONID`;
  `is_logged_in` / `is_signed_in` bayrakları yalnızca yanlarında bir oturum çerezi varsa geçerli
  sayılıyor (çıkış sonrası kalıntılar oturum sayılmıyor).
- **Kanıtlı Karar:** Oturum artık çerez adına bakılarak tahmin edilmiyor; MAL'a istek atılır ve
  giriş sayfasına düşülürse oturum istenir. MAL giriş formunu 200 ile döndürdüğünde de bu doğru
  şekilde "oturum gerekli" olarak yorumlanıyor.

### ⚡ 2. Web Oturumu Kurulumu Otomatikleşti
- MAL OAuth ile bağlı olduğu hâlde web oturumu gerçekten yoksa (örneğin giriş harici tarayıcıda
  tamamlandıysa) giriş penceresi kullanıcıyı aramaya bırakmadan **bir kez kendiliğinden** açılır.
- Giriş tamamlandığı anda pencere kapanır, çerezler diske yazılır ve bildirimler otomatik yüklenir.
- Kart metni bağlantı durumuna göre doğru gösterilir: bağlı değilse **"MAL web oturumunu bağla"**,
  bağlıysa **"MAL bildirimlerini yenile"**.

### 🩺 3. Bildirim Teşhisi: Çerez Görünürlüğü
- MAL tanı satırı artık bulunan çerez adlarını raporlar (`MALHLOGSESSID`, `is_logged_in=1`, …).
  Çerez **değerleri** hiçbir zaman gösterilmez veya kaydedilmez.
- Yanıltıcı "is_signed_in=1 cookie missing" hatası kaldırıldı; oturum geçersizse sebep ve öneri
  açıkça yazılır.
- Sayfa ayrıştırma hatası, oturum eksikliğinden ayrıldı ve kendi açıklama metnini gösteriyor.

---

## 🇬🇧 English

### 🔔 1. MyAnimeList Notifications Actually Load Now (Root-Cause Fix)
- **Symptom:** With a connected MAL account, the Notifications > MyAnimeList tab kept asking to
  "Connect MAL web session" and showed the 7-day airing calendar instead of real notifications.
- **Root Cause:** The app looked for an `is_signed_in=1` cookie that MAL never sets. Even with a
  valid MAL session in the in-app WebView, the session was considered missing, so the notification
  request was never sent.
- **Fix:** Real MAL session cookies are recognized (`MALHLOGSESSID`, `MALSESSIONID`); the
  `is_logged_in` / `is_signed_in` flags only count next to an actual session cookie, so stale
  sign-out leftovers are ignored.
- **Evidence-Based Decision:** The session is no longer guessed from cookie names — MAL is asked
  directly and a redirect to the login page is reported as a missing session. A login form returned
  with HTTP 200 is interpreted the same way.

### ⚡ 2. Web Session Setup Is Now Automatic
- If MAL OAuth is connected but no website session exists (e.g. sign-in finished in an external
  browser), the sign-in dialog now opens **once, on its own**, instead of waiting for the user.
- As soon as sign-in completes, the dialog closes, cookies are flushed and notifications load
  automatically.
- The card label now matches the connection state: **"Connect MAL web session"** vs.
  **"Refresh MAL notifications"**.

### 🩺 3. Notification Diagnostics: Cookie Visibility
- The MAL diagnostics row now reports which cookies were found (`MALHLOGSESSID`, `is_logged_in=1`,
  …). Cookie **values** are never displayed or stored.
- The misleading "is_signed_in=1 cookie missing" error was removed; invalid sessions now report a
  clear reason and hint.
- Parse failures are separated from missing sessions and show their own explanation.
