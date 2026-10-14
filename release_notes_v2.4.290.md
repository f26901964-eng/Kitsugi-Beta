# Kitsugi Beta v2.4.290

## 🇹🇷 Türkçe

### Depolama Ekranına "Wiki Önbelleği" Kalemi
- **Görünür Wiki Verisi:** Ayarlar > Depolama ekranında artık **Wiki önbelleği** kalemi de yer alır; Fandom ve Wikipedia maddeleri, bilgi kutuları, karakter sayfaları ve bölüm içerikleri için tutulan kayıtların boyutu ve dosya sayısı buradan görülür.
- **Güvenli Temizlik:** Kalemin "Temizle" eylemi yalnızca **yeniden üretilebilir** wiki kayıtlarını (Room `persistent_details_cache` tablosundaki `fandom:v1:` kayıtları) siler; kullanıcının "doğru / hayır, yanlış" eşleşme kararları korunur. Silinen içerik bir sonraki açılışta ağdan yeniden çekilir.
- **Doğru Ölçüm:** SQLite dosyası silme sonrası küçülmediği için (VACUUM gerekir) kalem, disk dosyası yerine **mantıksal veri boyutunu** raporlar; "Temizle" sonrası geri kazanılan miktar da bu ölçümden hesaplanır. 30 günden eski kayıtlar arka plan bakımında otomatik atılmaya devam eder.

### Uygulama Hakkında → Sürüm Notları
- **GitHub'dan Son Sürüm Notları:** "Uygulama Hakkında" ekranına **Sürüm Notları** kartı eklendi. Kart, GitHub Releases üzerindeki **en son** sürümün başlığını, yayın tarihini ve değişiklik notlarını markdown işaretlerinden arındırılmış biçimde gösterir.
- **Kurulu Sürüm Güncelken de Görünür:** Güncelleme akışı yalnızca yeni sürüm varsa pencere açtığı için, uygulama güncelken sürüm notları hiçbir yerde görünmüyordu. Yeni kart, yeni sürüm koşulu aramaz; "Yeni sürüm hazır" / "Kurulu sürüm en güncel" rozetiyle durumu belirtir.
- **İstek Sınırı Koruması:** Son başarılı yanıt 6 saat süreyle diskte önbelleklenir (GitHub kimliksiz istek sınırı saatte 60 istek); ekran her açıldığında ağa gidilmez. "Yenile" düğmesi önbelleği atlayarak zorla tazeler, "GitHub'da tüm sürümler" bağlantısı tarayıcıda sürüm listesini açar.
- **Ortak Görünüm:** Sürüm notları, güncelleme penceresindeki biçimlendirmenin aynısıyla (başlık, madde imi, ayraç sadeleştirmesi) gösterilir.

### Wiki'de "Orijinal / Çeviri" Geçişi Her Yere
- **Açılır Wiki Sayfası Bilgi Kutusu:** Bilgi kutusunda artık uygulama içi çeviri varsa, harici çeviri uygulamasını açan düğmenin **hemen solunda** "Orijinal" düğmesi çıkar; dokununca bilgi kutusunun tüm alanları wiki'nin özgün diline döner ("Çeviri" ile geri alınır). Bu, karakter wiki sekmesindeki davranışla birebir aynıdır.
- **Açılır Wiki Sayfası Açıklaması ve Bölümleri:** Seçili wiki maddesinin açıklaması ile açılan her madde bölümünde de aynı geçiş düğmesi eklendi; "Orijinal" seçiliyken bölüm gövdesi çevrilmemiş metni gösterir (çeviri bellekte kalır, ağa tekrar gidilmez).
- **Wikipedia Kaynakları Dahil:** Kaynak Fandom ya da Vikipedi (TR) olsun aynı bileşenler kullanıldığı için geçiş düğmesi her iki kaynakta da çalışır; kaynak değiştirildiğinde durum ilgili anahtara göre ayrı tutulur.
- **Bilgi Kutusunu Kopyala:** Açılır wiki sayfasının bilgi kutusuna "bilgi kutusunu kopyala" düğmesi eklendi. Kopyalanan metin yalnızca ekranda görünen alanları içerir, spoiler koruması açıkken gizli alanların değeri `[gizli]` ile maskelenir ve "Orijinal" seçiliyken çeviriler değil özgün metin kopyalanır.

### Hesap Girişi (AniList / MyAnimeList / Simkl) Uygulama İçi Ekran Kök Neden Düzeltmesi
- **Uygulama İçi Giriş Artık Çalışıyor:** Hesap bağlantıları ekranından açılan uygulama içi tarayıcı benzeri OAuth penceresinde giriş yapılamıyor, Cloudflare/MAL WAF "Gerçek kişi misiniz?" doğrulamasında takılıyor ve izin verilmiyordu; yalnızca harici tarayıcıdan giriş çalışıyordu. Bu sürümde uygulama içi akış kök nedeninden düzeltildi.
- **Sahte User-Agent Kaldırıldı (Kök Neden):** OAuth WebView'ine sabit "Chrome/124 (Android 13, Pixel 7)" sahte User-Agent'ı gönderiliyordu. Cloudflare ve MAL WAF, isteğin gerçek TLS + JavaScript parmak izini beyan edilen UA ile karşılaştırır; uyumsuzluk bot sinyalidir ve doğrulama döngüsü üretir. Artık sistem WebView'inin GERÇEK varsayılan UA'sı (cihazın gerçek WebView sürümü ve modeli) kullanılır — parmak iziyle tutarlı olduğu için harici tarayıcıdakine yakın geçiş sağlar. Aynı düzeltme genel uygulama içi web penceresine (TV QR girişi, eklenti/bildirim/profil sayfaları) de uygulandı.
- **OAuth Geri Dönüşü Tek Noktadan, Çift Takas Korumasıyla:** `malapp://auth` geri dönüşü daha önce iki ayrı kanalda (`onPageStarted` + `shouldOverrideUrlLoading`), korumasız ve kopyala-yapıştır kodla işleniyordu; çift tetiklenmede kod iki kez takas edilip "kod zaten kullanılmış" hatası üretebiliyordu. Artık tek bir korumalı işleyici (`handleAuthRedirect`) üzerinden yürür.
- **JS Yönlendirmeleri de Yakalanıyor:** `doUpdateVisitedHistory` kancası eklendi; bazı WebView sürümlerinde `window.location` ile yapılan OAuth geri dönüşleri `shouldOverrideUrlLoading`'e düşmüyordu ve izin sonrası bağlantı sessizce başarısız oluyordu.
- **Yanlış Hata Mesajı Engellendi:** `malapp://` gibi özel şemalı geri dönüşlerde WebView'in ürettiği `ERR_UNKNOWN_URL_SCHEME` artık "Sayfa yüklenemedi" hatası olarak gösterilmez; dönüş yakalanır, yükleme durdurulur ve token takası yapılır.
- **Etkilenen Kaynaklar:** AniList, MyAnimeList, Simkl (OAuth penceresi) ve TV QR girişinin telefon tarafındaki uygulama içi ekranı. Kitsu (kullanıcı adı/şifre), Shikimori, Bangumi ve Simkl PIN akışı zaten harici tarayıcıyı kullandığı için etkilenmiyordu.
- **MAL Tek Oturum Akışı Korundu:** Giriş sonrası OAuth jetonu + web oturumu doğrulaması ve bildirimler için MAL çerez yakalama mekanizması aynen çalışır; çerezler WebView CookieManager'dan okunmaya devam eder.

### Oynatıcı ve Ayar Kaydırıcılarında "Varsayılana Sıfırla" Düğmesi
- **Tek Dokunuşla Fabrika Ayarı:** Oynatıcı genel ayarları (kaydırma hassasiyeti, çift dokunma arama süresi, basılı tutma oynatma hızı, parlaklık/ses hareket hassasiyeti, mini oynatıcı boyutu vb.), tercihler diyaloğu ve manga okuyucu parlaklık kaydırıcılarına doğrudan tıklanabilir sıfırlama butonu eklendi.
- **Kaydırıcı Üzerinde Çift Dokunma:** Ayar değerini hızlıca orijinal değerine döndürmek için kaydırıcı çubuğuna çift dokunma kısayolu da desteklenir.

### +18 Yetişkin İçerik Görünürlük & Bulanıklaştırma Uyumlaştırması
- **Tüm Kaynaklarda Bütünlük:** Eklenti (Cloudstream CS3), manga uzantıları, takvim ve yapım ilişkileri dahil tüm dış kaynaklarda +18 sansür/bulanıklık ve göster/gizle anahtarı tam uyumlu hale getirildi.
- **Korumalı Görsel Başlıkları:** +18 içeriklerin afiş ve görselleri yüklenirken Cloudstream ve manga sunucularının talep ettiği Referer ve User-Agent başlıkları (Coil 3 NetworkHeaders) korundu.

### MyAnimeList (MAL) Sayfalama, Kulüpler ve Ağ Hatası Dayanıklılığı
- **Hata ve Yeniden Deneme (Retry):** MAL arkadaş listesi, incelemeler ve öneriler çekilirken ağ hatası alındığında tüm listenin kesilmesi önlendi; liste altında yerinde "Devamı yüklenemedi · Tekrar dene" butonu sunuldu.
- **Kulüpler (Clubs) Sekmesi ve Sayfalama:** MAL kullanıcı profillerine kullanıcının üye olduğu kulüpleri listeleyen sekme ve çok sayfalı kulüp yükleme desteği eklendi.
- **Dayanıklı MAL Bildirimleri:** MAL web oturumu bildirimlerinde daha eski bildirimleri yüklerken bağlantı kesilirse yerinde tekrar deneme desteği getirildi; sayfa gezintisi güçlendirildi.

---

## 🇬🇧 English

### New "Wiki Cache" Entry on the Storage Screen
- **Visible Wiki Data:** Settings > Storage now lists a **Wiki cache** entry showing the size and file count of the records kept for Fandom/Wikipedia articles, infoboxes, character pages and section contents.
- **Safe Clearing:** Its "Clear" action removes only **reproducible** wiki records (`fandom:v1:` rows in the Room `persistent_details_cache` table) and preserves the user's "correct / wrong match" decisions. Cleared content is re-fetched from the network on the next visit.
- **Accurate Measurement:** Because SQLite does not shrink after deletes (VACUUM is required), the entry reports **logical data size** instead of the database file size, and the reclaimed amount is derived from the same measurement. Records older than 30 days are still pruned automatically by background maintenance.

### About the App → Release Notes
- **Latest Notes Straight from GitHub:** A **Release Notes** card was added to the "About the App" screen. It shows the title, publication date and markdown-stripped changelog of the **latest** GitHub release.
- **Shown Even When Up to Date:** The update flow only opens a dialog when a newer version exists, so release notes were invisible on an up-to-date install. The new card does not require a newer version and labels the state with a "New version available" / "Installed version is up to date" badge.
- **Rate-Limit Friendly:** The last successful response is cached on disk for 6 hours (GitHub's unauthenticated limit is 60 requests/hour), so the screen does not hit the network on every open. "Refresh" bypasses the cache and forces a re-fetch, while "All releases on GitHub" opens the release list in the browser.
- **Consistent Styling:** Release notes are rendered with the exact same formatter as the in-app update dialog (headings, bullets and separators are cleaned up).

### "Original / Translation" Toggle Everywhere in the Wiki
- **Popup Wiki Infobox:** When an in-app translation exists, the infobox now shows an "Original" button **immediately left of** the external-translate button; tapping it restores all infobox fields to the wiki's source language ("Translation" switches back). This matches the character wiki tab behaviour exactly.
- **Popup Article Summary and Sections:** The same toggle was added to the selected article's summary and to every expanded article section; while "Original" is selected the section body shows the untranslated text (the translation stays in memory, no extra network request).
- **Wikipedia Sources Included:** Because the same components render both Fandom and Wikipedia (TR) sources, the toggle works on either source and its state is tracked per translation key when switching sources.
- **Copy Infobox:** The popup infobox gained a "copy infobox" button. The copied text contains only the fields currently on screen, masks hidden fields as `[hidden]` while spoiler protection is on, and copies the source text rather than the translation when "Original" is selected.

### Root-Cause Fix for In-App Account Login (AniList / MyAnimeList / Simkl)
- **In-App Login Now Works:** Signing in from the browser-like in-app OAuth window (opened from the account connections screen) used to fail — Cloudflare/MAL WAF human-verification got stuck and permission was never granted; only the external browser worked. This release fixes the in-app flow at its root cause.
- **Spoofed User-Agent Removed (Root Cause):** The OAuth WebView sent a hardcoded fake User-Agent ("Chrome/124, Android 13, Pixel 7"). Cloudflare and MAL's WAF compare the client's real TLS + JavaScript fingerprint against the declared UA; the mismatch is a bot signal that triggers a verification loop. The system WebView's REAL default UA (device's actual WebView version and model) is now used — consistent with the fingerprint, closely matching the external-browser behavior. The same fix was applied to the generic in-app web window (TV QR login, add-on/notification/profile pages).
- **Single-Point OAuth Redirect Handling with Double-Exchange Guard:** The `malapp://auth` return was previously handled in two separate hooks (`onPageStarted` + `shouldOverrideUrlLoading`) with duplicated, unguarded code; on double firing the code could be exchanged twice, producing an "authorization code already used" error. It now flows through one guarded handler (`handleAuthRedirect`).
- **JS Redirects Are Now Caught:** A `doUpdateVisitedHistory` hook was added; on some WebView versions, OAuth returns performed via `window.location` never reached `shouldOverrideUrlLoading`, silently failing the connection after permission was granted.
- **No More Misleading Error:** The `ERR_UNKNOWN_URL_SCHEME` produced by special-scheme returns (`malapp://`) is no longer shown as a "page failed to load" error; the return is intercepted, loading is stopped, and the token exchange proceeds.
- **Affected Sources:** AniList, MyAnimeList, Simkl (OAuth window) and the phone-side in-app screen of the TV QR login. Kitsu (username/password), Shikimori, Bangumi, and the Simkl PIN flow already used the external browser and were unaffected.
- **MAL Unified Session Preserved:** Post-login OAuth token + web-session verification and the MAL cookie capture for notifications keep working; cookies are still read from the WebView CookieManager.

### "Reset to Default" on Player and Settings Sliders
- **One-Tap Factory Reset:** Added a direct reset button to player settings sliders (scroll sensitivity, double-tap seek duration, hold-to-boost speed, gesture sensitivity, PIP size), preference dialog sliders, and manga reader brightness.
- **Double-Tap Slider to Reset:** Double-tapping directly on any resettable slider resets it immediately to its recommended default value.

### +18 Adult Content Parity & Poster Reliability
- **Unified Across All Sources:** Adult blur and show/hide toggles are now consistently enforced across Cloudstream (CS3) add-ons, manga sources, schedule, and anime relations.
- **Protected Poster Headers:** Upgraded addon poster requests with Coil 3 NetworkHeaders to preserve critical Referer and User-Agent headers required by external image hosts.

### MyAnimeList (MAL) Resilient Pagination & Clubs Support
- **In-Place Pagination Retry:** Network failures while loading additional MAL friends, reviews, or recommendations no longer drop pagination; an in-place "Could not load more · Retry" action is displayed.
- **Clubs Tab & Pagination:** Added full MAL Clubs list support to user profiles with paginated loading.
- **Reliable Notification Pagination:** Loading older MAL notifications now handles connection errors gracefully with an inline retry option and improved next-link traversal.
