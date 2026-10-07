# Kitsugi-Beta — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe (v2.4.189)

### 🚀 MyAnimeList Çapraz Senkronizasyon & Shikimori OAuth Doorkeeper Düzeltmesi

- **MyAnimeList Çapraz Eşitleme URL Hatası Çözüldü (`Illegal character in query at index 193`):** MAL API v2 kullanıcı listesi (`/animelist` ve `/mangalist`) isteklerindeki `fields` parametresi içinde yer alan iç içe alan seçicisi (`list_status{...}`) süslü parantezleri (`%7B` ve `%7D`) olarak URL encode edildi. Böylece `java.net.URI`'nin parantezleri reddedip çapraz eşitlemeyi %0'da durdurması tamamen engellendi.
- **Shikimori Doorkeeper Token İstek Düzeltmesi:** Token takas isteğine (`POST /oauth/token`) `redirect_uri` parametresi geri eklendi; `400 Missing required parameter: redirect_uri` hatası tamamen giderildi.
- **Dinamik Pending Redirect URI Eşleştirmesi:** Tarayıcının açıldığı yönlendirme adresi (`kitsugi://`, `urn:ietf:wg:oauth:2.0:oob` veya `aniyomi://`) hafızaya alınarak token takası sırasında sunucuya birebir aynı adres gönderilir.
- **Ham JSON Hatası Engellendi:** Shikimori'nin geçersiz oturumlarda döndürdüğü `null` yanıtının parse edilmesiyle oluşan ham `A JSONObject text must begin with '{'` hatası giderildi; tüm yanıtlar null/HTML kontrolleriyle korumaya alındı.
- **Gelişmiş Giriş Diyaloğu & Alternatif Şema:** Giriş penceresine "Shikimori OAuth Uygulamalarım" yönetim bağlantısı ve eski şemayı kullananlar için "Alternatif Tek Tık (aniyomi://)" butonu eklendi.

---

## 🇬🇧 English (v2.4.189)

### 🚀 MyAnimeList Cross-Sync Query Encoding & Shikimori OAuth Doorkeeper Fix

- **MyAnimeList Cross-Sync Query Parsing Fix (`Illegal character in query at index 193`):** Percent-encoded nested field selector braces (`%7B` and `%7D`) in MAL `/animelist` and `/mangalist` endpoints. Fixed `java.net.URI` query syntax crash that aborted cross-account sync at 0%.
- **Shikimori Doorkeeper Token Payload Fix:** Restored `redirect_uri` parameter in token exchange requests, eliminating `400 Missing required parameter: redirect_uri`.
- **Dynamic Pending Redirect URI Matching:** Pairs the authorization redirect URI (`kitsugi://`, `urn:ietf:wg:oauth:2.0:oob`, or `aniyomi://`) with the token exchange step as required by Doorkeeper OAuth.
- **Guarded JSON Parsing:** Eliminated raw `A JSONObject text must begin with '{'` exceptions when encountering `null` or non-JSON payloads from expired session endpoints.
- **Enhanced Login Dialog & Fallback Flows:** Added direct link to Shikimori OAuth management dashboard and an "Alternative 1-Tap (aniyomi://)" button in the login dialog.

---

---

- **Nuvio Libtorrent P2P Motor Entegrasyonu (libtorrent + Boost.Asio):** Nuvio'nun yüksek performanslı C++ libtorrent ve Boost.Asio çekirdeği (`lib-nuvio-engine-android-0.1.2.aar`) Kitsugi'ye entegre edildi. Artık Debrid üyeliği (RealDebrid, AllDebrid vb.) olmadan da Torrentio ve diğer torrent tabanlı Stremio eklentileri doğrudan cihaz üzerinden eşler arası (P2P) ve yüksek hızla oynatılabilir.
- **Gelişmiş Seeding & Yükleme (Upload) Kontrolleri:** Orijinal Nuvio uygulamasında bulunmayan, arka planda sınırsız çalışan gönderme (seeding) davranışı tamamen kullanıcı denetimine açıldı. Stremio eklentileri ayarlarından seed modu tek tıkla kapatılabilir ya da internet kotasını korumak için 256 KB/s, 512 KB/s, 1 MB/s, 2 MB/s hız sınırları atanabilir.
- **P2P Gizlilik & Swarm Güvenlik Bilgilendirmesi:** P2P motoru ilk kez aktif edildiğinde veya Debrid olmadan bir torrent bağlantısına tıklandığında açılan tek seferlik güvenlik diyaloğu eklendi. IP adresinin torrent swarm havuzunda görünebileceği, telif hakları sorumluluğu ve VPN tavsiyeleri net biçimde sunuldu.
- **Tam Ekran Oynatıcı Canlı Torrent Paneli (TorrentOverlay):** Video oynatıcıya bağlanan canlı gösterge paneli sayesinde anlık indirme hızı, yükleme hızı, bağlı eşler (peers), seed sayısı ve önbellek tampon durumu gerçek zamanlı takip edilebilir. Oynatıcı kapatıldığında veya bölüm değiştiğinde P2P motoru gereksiz arka plan trafiğini önlemek için otomatik sonlandırılır.
- **Simkl Toplu Eşitleme (Batch Sync) & 429 Hız Sınırı Koruması:** Simkl API'sinin saniyede 1 istek kuralına ve `title`/`year` zorunluluklarına tam uyum sağlandı. 35'lik akıllı paketler ve 1.1 saniye gecikme ile yüzlerce animenin tek seferde sorunsuz senkronize edilmesi sağlandı; senkronizasyon sonrası Room DB'ye gerçek sunucu ID'leri işlenerek öğelerin sayfa yenilenince kaybolması engellendi.
- **Simkl Medya Detay Sayfası & 404 Koruması:** Simkl kütüphanesindeki öğelerin üzerine tıklandığında oluşan "Veri Yok" ve 404 hataları giderildi; kimlik çözümleyici Simkl ID'lerini doğru yöne kanalize ederek Jikan ve Simkl arama fallback mekanizmalarıyla güçlendirildi.

---

## 🇬🇧 English (v2.4.189)

### 🚀 Nuvio Libtorrent P2P Streaming Engine, Full Seeding & Upload Controls, Simkl Batch Sync & Media Detail Fixes

- **Nuvio Libtorrent P2P Engine Integration (libtorrent + Boost.Asio):** Embedded Nuvio's high-performance C++ libtorrent and Boost.Asio core (`lib-nuvio-engine-android-0.1.2.aar`) directly into Kitsugi. Users can now stream torrent-based Stremio add-ons (such as Torrentio) entirely for free via peer-to-peer swarms without requiring a Debrid subscription.
- **Full Seeding & Upload Bandwidth Controls:** Addressed a critical privacy and bandwidth shortcoming in the original Nuvio app (which seeded indefinitely with no UI controls). Users can now toggle seeding on/off or limit upload bandwidth to 256 KB/s, 512 KB/s, 1 MB/s, or 2 MB/s directly from the Stremio settings tab.
- **P2P Privacy & Swarm Consent Dialog:** Implemented a one-time transparent consent dialog detailing public swarm IP visibility, copyright compliance responsibilities, and VPN usage recommendations before initiating any P2P torrent stream.
- **Real-Time Video Player Torrent Overlay:** Connected fullscreen player metrics to live engine telemetry, displaying instant download speeds, upload speeds, seeders, peers, and buffering progress, with automatic stream teardown on player exit.
- **Simkl Batch Synchronization & 429 Rate-Limit Prevention:** Adhered strictly to Simkl's 1 request/second policy and mandatory title/year metadata requirements. Synchronizes large libraries in 35-item batches with 1.1s throttling, followed by server-verified Room DB persistence to eliminate phantom entries and list reverts.
- **Simkl Media Detail Navigation & 404 Fallback:** Resolved missing detail screens and 404 errors when opening Simkl entries by prioritizing Simkl external IDs and adding Jikan & Simkl title search fallback strategies.

---

## 🇹🇷 Türkçe (v2.4.189)

### 🚀 İzole Süreç Çökme Ekranı (:crash), Canlı Sistem Teşhisi, Çapraz Senkronizasyon & Kararlılık Güncellemeleri

- **İzole Süreç Tabanlı Çökme & Teşhis Ekranı (Isolated :crash Process):** AndroidManifest'e `:crash` ayrılmış süreci entegre edildi. Ana uygulama süreci beklenmedik bir kritik hatayla veya bellek yetersizliğiyle karşılaşsa dahi, çökme ekranı (`KitsugiCrashActivity`) tamamen izole ve temiz bir süreçte anında açılır. Uygulamanın aniden sessizce kapanması ("pat diye kapanma") önlendi.
- **Hakkında Ekranından Doğrudan Teşhis & Rapor Erişimi:** Ayarlar altındaki Hakkında (About) ekranına yeni "Çökme & Hata Teşhis Raporu" butonu eklendi. Kullanıcılar ve test ekibi istedikleri an geçmiş çökme kayıtlarını, logcat çıktılarını ve cihaz donanım teşhisini inceleyebilir veya tek tıkla panoya kopyalayabilir.
- **Çoklu Platform Çapraz Senkronizasyon (Cross-Sync) Güçlendirmesi:** AniList, MyAnimeList, Simkl, Kitsu ve Shikimori platformları arasındaki kütüphane ve izleme geçmişi senkronizasyonu geliştirildi; diyalog akışı ve durum göstergeleri optimize edildi.
- **Kitsu Bildirim & Detay Entegrasyonu:** Kitsu bildirimlerine dokunulduğunda anime ve manga detay sayfalarının kesintisiz, doğru kimlik çözümlemesiyle ve önbellekten anında kapak aktarımıyla açılması garantilendi.
- **Shikimori 1-Tık Giriş ve Güvenlik Sertleştirmesi:** Shikimori OAuth2 bağlantısında yaşanan 403 ve token çözümleme problemleri kalıcı olarak giderildi.

---

## 🇬🇧 English (v2.4.189)

### 🚀 Isolated :crash Process Architecture, Live System Diagnostics, Cross-Sync & Core Stability

- **Isolated Process Crash & Diagnostics Screen (:crash Process):** Dedicated isolated process architecture (`:crash`) wired into AndroidManifest. Even if the primary application process experiences an unhandled fatal error or OutOfMemory condition, `KitsugiCrashActivity` launches reliably within a clean sandbox without hangs or freeze loops, preventing abrupt app disappearance.
- **Direct Diagnostics & Crash Hub Access:** Added a dedicated "Crash & Diagnostics Report" button to the About screen. Users and testers can review past crash logs, live logcat output, and device hardware diagnostics on demand, or copy full reports to clipboard in one tap.
- **Multi-Platform Cross-Sync Enhancements:** Streamlined library cross-synchronization across AniList, MyAnimeList, Simkl, Kitsu, and Shikimori, complete with dialog feedback and state reconciliation.
- **Kitsu Notification & Media Detail Navigation:** Guaranteed smooth detail page transitions when launching from Kitsu push notifications, handling both raw and offset entity identifiers with instant poster caching.
- **Shikimori 1-Tap OAuth & Authorization Polish:** Strengthened Shikimori authentication flow against 403 Forbidden edge cases and token renewal hiccups.

---

## 🇹🇷 Türkçe (v2.4.189)

### 🚀 Shikimori 1-Tık Otomatik Giriş Çözümü, Kitsu Bildirim Detay Sayfası Düzeltmesi & Kararlılık Güncellemeleri

- **Shikimori 1-Tık Otomatik Giriş ve Deep Link Desteği:** Shikimori OAuth girişinde yaşanan 403 erişim engelleri ve yetkisiz istemci (invalid_client) sorunları kalıcı olarak çözüldü. Resmi `aniyomi://shikimori-auth` deep link yönlendirmesi entegre edildi. Kullanıcı tarayıcıda tek dokunuşla izin verdiğinde uygulama otomatik açılır ve oturumu başlatır.
- **Alternatif Manuel Giriş ve Otomatik Sıfırlama:** Tarayıcıda doğrulama kodunu doğrudan görüntüleyen `urn:ietf:wg:oauth:2.0:oob` protokolü korundu. Gelişmiş ayarlar menüsüne "Varsayılan Anahtarları Geri Yükle" butonu eklenerek cihaz hafızasında kalmış eski/hatalı anahtarların tek tıkla temizlenmesi sağlandı.
- **Kitsu Bildirimlerinden Medya Detayına Geçiş Sorunu Giderildi:** Bildirimler ekranındaki Kitsu bildirimlerine dokunulduğunda oluşan boş ekran veya çökme problemi düzeltildi. `KitsuExploreClient` ve `KitsugiDetailClient` içerisindeki kimlik çözümleme motoru güncellenerek hem ham Kitsu ID'leri hem de ofsetli (`+300,000,000`) ID'ler eksiksiz tanınır hale getirildi.
- **Görsel Bildirim Geçişleri:** Bildirimlerden detay sayfasına giderken anime/manga başlığı ve kapak görseli önbellekten anında aktarılarak sayfa açılışında görsel süreklilik ve akıcı bir deneyim sunuldu.

---

## 🇬🇧 English (v2.4.189)

### 🚀 Shikimori 1-Tap OAuth & Deep Link Fix, Kitsu Notification Detail Resolution & Stability Improvements

- **Shikimori 1-Tap OAuth & Deep Link Integration:** Fixed 403 Forbidden and invalid_client issues during Shikimori authentication. Configured official `aniyomi://shikimori-auth` deep link callback scheme; tapping "Allow" in browser immediately returns to Kitsugi and authenticates seamlessly.
- **Alternative Out-of-Band Auth & Credential Reset:** Preserved manual code generation via `urn:ietf:wg:oauth:2.0:oob`. Added a "Reset to Default Keys" action in advanced settings to purge any corrupted client keys from app storage.
- **Kitsu Notification Media Detail Page Fix:** Resolved an issue where tapping on Kitsu notifications resulted in empty detail screens or failed navigation. Harmonized ID offset mapping in `KitsuExploreClient` and `KitsugiDetailClient` to reliably resolve both raw numeric IDs and offset stable IDs (`+300,000,000`).
- **Seamless Notification Visual Transition:** Titles and cover posters from incoming notifications are now forwarded directly to detail screens, ensuring instantaneous visual feedback without flicker.

---

## 🇹🇷 Türkçe (v2.4.189)

### 🚀 Gelişmiş Arama Filtre Paneli, İzole Arama Motorları, Resmi MAL API Entegrasyonu, Shikimori 1-Tık Giriş & Modern Çökme Raporlama

- **Yeni Nesil Arama Kaynak ve Filtre Seçici Paneli (Source Engine Filter Sheet):** Arama sayfasında motorların durumunu, aktifliklerini ve sonuç sayılarını gösteren, kategorilere (Anime, Dizi, Film, Manga, Roman vb.) göre filtrelenebilen yepyeni bir modal alt panel (`SourceEngineFilterSheet`) eklendi.
- **İzole Edilmiş ve Bağımsız Arama Sağlayıcıları:** Çoklu arama motorları birbirinden tamamen izole edildi; bir sağlayıcıda gecikme veya hata olduğunda diğer motorların (AniList, Kitsu, Shikimori, TMDB, Simkl vb.) arama sonuçları kesintisiz olarak anında ekrana gelir.
- **Resmi MyAnimeList (MAL) API Entegrasyonu:** Eski veya kararsız Jikan uç noktaları yerine MyAnimeList'in resmi OAuth2 API altyapısına geçildi. Arama hızı ve kütüphane güvenilirliği en üst seviyeye çıkarıldı.
- **Shikimori 1-Tık Giriş & 403 Forbidden Çözümü:** Shikimori OAuth girişinde yaşanan 403 erişim engeli ve token sorunları giderildi. Tek tıkla tarayıcı yetkilendirmesiyle sorunsuz profil bağlantısı sağlandı.
- **Kitsu & Shikimori Kapsamlı Profil ve Liste Yönetimi:** Profil ekranında Kitsu ve Shikimori hesapları için kütüphane durumları (İzleniyor/Okunuyor, Tamamlandı, Beklemede, Bırakıldı, Planlandı), puan dağılımı, favoriler ve biyografi eksiksiz görüntülenebilir ve güncellenebilir hale getirildi.
- **Gelişmiş Teşhis ve Çökme Raporlama Ekranı (KitsugiCrashActivity & Logger):** Uygulama beklenmeyen bir hatayla karşılaştığında kapanmak yerine modern bir arayüzle hatanın detayını, cihaz donanım bilgilerini ve bellek durumunu gösterir; logları tek tıkla kopyalama veya uygulamayı güvenle yeniden başlatma imkanı sunar.
- **Ayarlar Ekranı Modüler Hesap Sayfaları:** Ayarlar menüsündeki hesap yönetimi ayrıştırılarak AniList, MAL, Simkl, Kitsu ve Shikimori için özel alt sayfalar (`AccountSettingsSubPages`) oluşturuldu.

---

## 🇬🇧 English (v2.4.189)

### 🚀 Advanced Search Filter Sheet, Isolated Search Engines, Official MAL API, Shikimori 1-Tap OAuth & Modern Crash Activity

- **Next-Gen Source Engine Filter Bottom Sheet:** Introduced a comprehensive modal bottom sheet (`SourceEngineFilterSheet`) on the Search screen with per-source toggles, status indicators, and categories (Anime, Movies, TV Series, Manga, Light Novels).
- **Isolated Multi-Search Provider Pipeline:** Search engines are now completely isolated; network timeouts or throttles on one provider no longer affect parallel search results from others (AniList, Kitsu, Shikimori, TMDB, Simkl).
- **Official MyAnimeList (MAL) API Migration:** Fully migrated search and library operations to official MyAnimeList OAuth2 endpoints, eliminating third-party rate limits and improving result speed.
- **Shikimori 1-Tap OAuth & 403 Forbidden Fix:** Fixed 403 Forbidden errors during Shikimori authorization, enabling an effortless 1-tap browser auth flow.
- **Kitsu & Shikimori Full Profile & Library Management:** Enriched profile screens with full status tracking (Watching/Reading, Completed, On Hold, Dropped, Plan to Watch/Read), score charts, favorites, and profile updates.
- **Advanced Diagnostics & Modern Crash Screen (KitsugiCrashActivity & Logger):** Replaced default unhandled exception crashes with a modern diagnostics screen showing breadcrumbs, system specifications, memory status, stacktrace copying, and 1-tap safe app restart.
- **Modular Account Settings Subpages:** Restructured account management into modular subpages (`AccountSettingsSubPages`) for AniList, MAL, Simkl, Kitsu, and Shikimori with clear connection status and sync actions.

---

## 🇹🇷 Türkçe (v2.4.189)

### 🚀 Zenginleştirilmiş Sistem Bildirimleri, Kitsu & Shikimori Profilleri, Profil Kaynak Seçici Paneli ve Orijinal Logolar

- **Zengin ve Görsel Sistem Bildirimleri (Drawer & Status Bar):** Android bildirim çekmecesi bildirimleri artık çok daha ayrıntılı ve yüksek çözünürlüklü kapak/avatar görselleriyle (`LargeIcon` & `BigPictureStyle`) donatıldı. Bildirimler artık hangi kaynaktan geldiğini açıkça belirtir (`[AniList]`, `[MyAnimeList]`, `[Simkl]`). Sistem çekmecesi bildirimleri ile uygulama içi bildirim ekranı metin ve biçimlendirme açısından birebir senkronize edildi.
- **Profil Sayfası Açılır Kaynak Seçim Paneli (Profile Source Picker Sheet):** Arama ve Keşfet sayfalarında olduğu gibi profil sayfasına da açılır seçim paneli (`ProfileSourcePickerSheet`) ve orijinal logolu hap butonu eklendi. Tek dokunuşla tüm bağlı/bağlantısız profiller arasında geçiş yapılabilir.
- **Kitsu ve Shikimori Profil Desteği Tamamlandı:** Profil sayfasına eksik olan Kitsu ve Shikimori sekmeleri eklendi. Kullanıcı biyografisi, waifu/husbando, takipçi verileri, anime/manga durum istatistikleri ve kütüphane kayıtları eksiksiz görüntülenir.
- **Orijinal Platform Logoları (Sıfır Emoji Kuralı):** Tüm sayfalardaki kaynak butonları, seçim panelleri ve sekmelerdeki emojiler veya boş kutular kaldırılarak yerini yüksek kaliteli orijinal platform logoları (`KitsugiPlatformLogo`) aldı.
- **Yeni GitHub Hesabı & Depoları Entegrasyonu:** Kod tabanı yeni `f26901964-eng/Kitsugi-Beta` ve `f26901964-eng/Kitsugi-Plugins` depolarına uyarlandı. Eklenti deposundan tüm +18 içerikler temizlenerek güvenli havuz devreye alındı.

---

## 🇬🇧 English (v2.4.189)

### 🚀 Rich System Notifications, Kitsu & Shikimori Profiles, Source Picker Sheet & Original Logos

- **Rich Visual System Drawer Notifications:** Android status bar and drawer notifications now include high-resolution poster artwork and avatars via `LargeIcon` and `BigPictureStyle`. Every notification clearly highlights its origin source (`[AniList]`, `[MyAnimeList]`, `[Simkl]`), completely harmonized with the in-app notification center.
- **Profile Source Picker Bottom Sheet:** Added a modal selection sheet and original logo pill button to the Profile screen, matching the Search and Explore user experience.
- **Kitsu & Shikimori Full Profile Integration:** The Profile screen now fully supports Kitsu and Shikimori tabs, displaying bio, social metrics, anime/manga status breakdown charts, score distributions, and library records.
- **Original Platform Logos (Strictly No Emojis):** Replaced all emoji badges and generic colored boxes across buttons, sheets, and tabs with authentic, high-res platform vector logos (`KitsugiPlatformLogo`).
- **New GitHub Ecosystem Integration:** Fully wired the application and plugin repositories to the new `f26901964-eng` GitHub account, with an adult-free safe plugins pool and updated in-app update mechanisms.

---

## 🇹🇷 Türkçe (v2.4.189)

### 🚀 Kitsu Düzeltmeleri, Grid/Liste Hatırlama, Yapışkan Bar Filtresi, TMDB İngilizce Fallback, Simkl Dizi/Film Keşfeti ve Çökme Korumaları

- **Grid / Liste Görünümü Kalıcı Olarak Hatırlanıyor:** Popüler, Trend vb. tam ekran medya sayfalarındaki liste veya grid görünüm seçimi artık kaydedilir; sayfadan çıkıp tekrar girildiğinde kullanıcının son seçtiği görünüm modu korunur.
- **Yapışkan Üst Çubukta Filtre Butonu Düzeltildi:** Sayfa aşağı kaydırıldığında beliren kompakt üst çubukta filtre butonunun kaybolması sorunu çözüldü; filtreleme ve sıralama butonu artık her kategoride ve kaydırma durumunda görünür durumdadır.
- **TMDB Keşfet'te Türkçe Başlığı Olmayan İçerikler İçin Otomatik İngilizce Desteği:** TMDB'den çekilen içeriklerde Türkçe başlık bulunmadığında Japonca/Kanji başlık yerine otomatik olarak İngilizce başlık (örn. "Souryo to Majiwaru Shikiyoku no Yoru ni...", "Puella Magi Madoka Magica...") gösterilir.
- **Simkl Keşfet'e Dizi ve Film Desteği Eklendi:** Simkl platformu artık sadece animelerle kısıtlı kalmayıp TMDB gibi Trend Diziler, Trend Filmler, Popüler Diziler, Popüler Filmler ve en yüksek puanlı içerikleri eksiksiz gösterir.
- **Kitsu Anime Ekleme / Düzenleme Sayfası Düzeltildi:** Kitsu üzerinden açılan animelerde "Listeye Ekle / Düzenle" alt sayfasının MyAnimeList olarak açılması ve MAL alanlarının görünmesi sorunu çözüldü. Artık Kitsu renkleri (turuncu), Kitsu kimlik etiketi ve Kitsu'ya özgü liste durumu seçenekleri açılır.
- **Kitsu Karakterler Sekmesi Takılma / Yüklenmeme Sorunu Giderildi:** Anime detay sayfasında "Resimler" sekmesi eklendikten sonra meydana gelen indeks kayması giderildi; "Karakterler" sekmesine tıklandığında artık iskelet ekranda takılı kalmadan tüm karakterler eksiksiz yüklenir.
- **Kitsu Kişi Bilgisi ve Galeri Kaynak Çelişkisi Düzeltildi:** Kitsu'dan açılan yönetmen/seslendirmen/ekip detayında "ANILIST" yerine doğru platform ("Kitsu") etiketi gösterilir; profil fotoğrafı tam ekran açıldığında da "Jikan" ve "🎭 Karakter" yerine doğru kaynak ve "👤 Kişi" kategorisi kullanılır. Kitsu ID ile kişi adı önce doğrudan Kitsu API'den çözümlenerek doğru eşleştirme sağlanır.
- **Rastgele Uygulama Çökmeleri Engellendi (Crash Shield & Looper Koruması):** Sayfa geçişlerinde, video izlerken veya eklentilerden veri gelmesini beklerken arka plan iş parçacıklarında, ağ zaman aşımlarında ya da harici eklenti kodlarında oluşan beklenmedik istisnaların uygulamayı aniden kapatması engellendi. Ana iş parçacığı döngü koruyucusu (Cockroach Looper Recovery) ve `SupervisorJob` takviyesiyle donatıldı.
- **Eklenti Deposu (Kitsugi Plugins) Sorunsuz Yükleme Garantisi:** "Kitsugi Eklentileri (Önerilen)" deposu eklenirken yaşanan ISS engelleri ve ağ zaman aşımları tamamen çözüldü. Hem doğrudan Codeberg bağlantısı hem REST API hem de dahili çevrimdışı katalog yedeği devreye alındı.
- **Shikimori Giriş Akışı Düzeltildi:** "The requested redirect uri is malformed" hatası giderilerek RFC 6749 uyumlu URL-encode OAuth2 giriş akışı sağlandı.

---

## 🇬🇧 English (v2.4.189)

### 🚀 Grid Persistence, Sticky Filter Button, TMDB English Fallback, Simkl TV/Movies & Crash Shield

- **Grid / List Mode Persistent Memory:** The layout toggle preference on full-screen media grid pages is now saved and remembered across visits.
- **Sticky Top Bar Filter Button Fix:** Fixed the filter and sorting button disappearing from the compact sticky header upon scrolling down; it is now always visible and positioned properly next to the layout toggle.
- **TMDB Explore English Title Fallback:** When Turkish titles are not localized on TMDB, titles automatically fall back to their English/Romaji counterparts instead of Japanese Kanji/Kana.
- **Simkl Explore TV & Movie Expansion:** Simkl explore is no longer limited to anime; it now features full TV show and movie sections (trending, popular, top-rated) matching the TMDB experience.
- **Kitsu Media Editor Sheet Fix:** Fixed Kitsu anime entries opening the edit sheet with "MyAnimeList" branding and MAL status fields. Proper Kitsu branding, theme color, and sync properties are now respected.
- **Kitsu Characters Tab Loading Fix:** Resolved tab index offset bug causing the Characters tab to freeze with skeleton loaders indefinitely; characters now load immediately.
- **Person / Staff Detail & Gallery Attribution Fix:** Resolved source attribution conflicts when opening staff from Kitsu (showing "ANILIST" badge or "Jikan / Character" in fullscreen gallery). Now correctly attributes Kitsu and "Person" category, with pre-resolution against Kitsu's `/people` API.
- **Random Crash Shield & Main Looper Protection:** Prevented random crashes during navigation, video playback, and extension scraping. Installed a resilient main Looper recovery loop and `SupervisorJob` scopes across extension loaders and prefetch coroutines to isolate non-fatal exceptions.
- **Plugin Repository Bulletproof Loading:** Resolved repository manifest loading failures caused by ISP blocks with Codeberg API fallback and bundled 174-plugin offline catalog.
- **Shikimori OAuth Redirect URI Fix:** Fixed "The requested redirect uri is malformed" error with proper RFC 6749 URL encoding and streamlined OAuth flow.

---

## 🇹🇷 Türkçe (v2.4.189)

### 🚀 Keşfet Arayüzü, "Tümü" Butonu, Kitsu & Shikimori Girişleri, Bildirim Önizleme ve Çapraz Eşitleme

- **Keşfet Sayfasında Çift Bar Fazlalığı Giderildi:** Keşfet sayfasında hem açılır seçim menüsü hem de alttaki yatay çip çubuğunun oluşturduğu karmaşa giderildi; gereksiz yatay çip barı kaldırılarak tekil, şık ve fonksiyonel kaynak seçici korundu.
- **Listem Sayfası "Tümü" Butonu Alt Bar ile Uyumlu Hale Getirildi:** "Tümü" kategori seçim butonu artık alt gezinti çubuğunun (Bottom Navigation Bar) üzerinde taşma veya örtüşme yapmaz. Diğer sayfalardaki FAB butonlarıyla birebir aynı biçimde, alt çubuğun tam üzerinde hizalanır ve kaydırma hareketleriyle pürüzsüzce animasyonlanır.
- **Kitsu Giriş Hatası ("Client authentication failed") Çözüldü:** Kitsu API OAuth2 token uç noktasına yönelik istemci anahtarları ve OAuth2 standart form-urlencoded yapısı güncellendi. Kullanıcılar artık e-posta/kullanıcı adı ve şifreleriyle doğrudan ve hatasız giriş yapabilir.
- **Shikimori Giriş Akışı Sadeleştirildi:** Kullanıcıdan karmaşık Client ID, Client Secret oluşturması isteme zorunluluğu kaldırıldı. Dahili kimlik bilgileriyle tek dokunuşla tarayıcıda Shikimori onay sayfası açılır, ekrandaki kod yapıştırılarak anında bağlantı kurulur.
- **AniList Bildirimlerine Tıklayınca Doğrudan Aktivite/Beğeni Detayı Açma:** Beğeni veya yorum bildirimlerine tıklandığında yalnızca körü körüne kullanıcı profiline gitmek yerine, tam olarak hangi paylaşımın/içeriğin beğenildiği veya yorumlandığını gösteren detay sayfası ve ilgili anime/dizi bağlantıları açılır.
- **Tüm Hesapları Birbiriyle Eşitle (5 Platform Çapraz Senkronizasyon) Düzeltildi:** AniList, MyAnimeList, Simkl, Kitsu ve Shikimori hesapları arasında anime, dizi ve film içerikleri eksiksiz olarak birbirine eşitlenir; her bağlı platformun kütüphanesindeki eksik içerikler tespit edilip hem yerel listelere hem de ilgili servislere sorunsuz aktarılır.

---

## 🇬🇧 English (v2.4.189)

### 🚀 Explore UI, "All" FAB, Kitsu & Shikimori Auth, Activity Notifications & Cross-Sync Matrix

- **Explore Top Bar Deduplication:** Removed the redundant horizontal chip row below the platform selector pill on the Explore screen, keeping a clean and unified interface.
- **MyList "All" FAB Bottom Bar Alignment:** The category button now sits properly above the Material3 navigation bar without overlapping, animating smoothly on scroll just like floating action buttons on other screens.
- **Kitsu Authentication Fix:** Resolved the "Client authentication failed" error by adopting official Kitsu public OAuth2 credentials and urlencoded form specifications.
- **Simplified Shikimori OAuth Flow:** Users no longer need to create developer applications or enter client credentials. A 1-tap browser authorization with built-in keys allows pasting the code directly.
- **Notification Activity Preview:** Clicking activity or like notifications now opens the exact activity/post modal showing what was liked/commented with direct media links, instead of merely navigating to the user profile.
- **Full Cross-Platform Synchronization Matrix:** The "Sync All Accounts" feature now equalizes anime, TV shows, and movies across all connected accounts (AniList, MAL, Simkl, Kitsu, Shikimori), properly updating local tabs and cross-syncing missing library items.

---

## 🇹🇷 Türkçe (v2.4.189)

### 🚀 Eklenti Deposu ve Eklenti Yükleme Sorunları Tamamen Giderildi

- **Codeberg Depo ve Eklenti Yüklenememe Sorunu Çözüldü:** Repo URL doğrulayıcı (`RepoVerifier`), `codeberg.org` alan adını artık varsayılan olarak güvenilir listeye aldı. Ek olarak `.git`, depo kök dizini veya eski GitHub bağlantıları otomatik olarak optimize edilmiş Codeberg manifestine yönlendirildi.
- **"Eklentiler Yüklenemedi" Hatası & Otomatik Yenileme:** Ağ gecikmesi veya ilk açılışta başarısız olan depo isteklerinde eklenti listesinin takılı kalması engellendi; otomatik yeniden deneme ve akıllı önbellek yenileme mekanizması devreye alındı.
- **Ağ Zaman Aşımı & Proxy Desteği:** Yavaş bağlantılarda ve ISS kısıtlamalarında eklenti ve repo indirmelerinin zaman aşımına uğramaması için okuma/bağlantı süreleri 30 saniyeye çıkarıldı, jsDelivr CDN yedeği güçlendirildi.
- **Eski ve Bozuk Depoların Temizliği:** Veritabanındaki eski/çift veya hatalı depo adresleri açılışta otomatik olarak temizlenerek resmi `Kitsugi Plugins (Önerilen)` deposu altında birleştirildi.

---

## 🇬🇧 English (v2.4.189)

### 🚀 Extension Repository & Plugin Loading Fixes

- **Codeberg Domain Trust & Repo Normalization:** `codeberg.org` is now unconditionally trusted in `RepoVerifier`. All repository formats (root URLs, `.git`, legacy GitHub links) now seamlessly normalize to canonical Codeberg endpoints.
- **Automatic Retry for Extension Listings:** Fixed an issue where temporary network failures left repo cards stuck on "Eklentiler yüklenemedi". Repos now auto-retry upon opening or refreshing.
- **Extended Timeouts & CDN Fallback:** Connect and read timeouts increased to 30s to prevent dropouts on throttled networks.

---

## 🇹🇷 Türkçe (v2.4.189)

### 🚀 Eklenti Kurulum Hatası Giderildi & Codeberg Eklenti Havuzu Canlıya Alındı

- **Eklenti Kurulumu ve İndirme Hatası Tamamen Çözüldü:** Yeni Codeberg `KitsugiPlugins` deposundaki tüm 174 eklenti (DiziPal, RecTV, FilmMakinesi, InatBox, TurkAnime vb.) artık sorunsuz, doğrudan ve tek tıkla kurulmaktadır. Dosya bütünlüğü ZIP doğrulamasıyla garanti altına alınmıştır.
- **Doğrudan Codeberg URL Koruması:** Eklenti indirme ve repo yenileme sırasında Codeberg URL'lerinin bozulması engellenmiş, eski GitHub bağlantıları otomatik olarak yeni depoya yönlendirilmiştir.
- **Sürüm Güncellemesi:** Önceki derlemelerdeki önbellek çakışmalarını gidermek amacıyla v2.4.189 olarak paketlenmiştir.

---

## 🇬🇧 English (v2.4.189)

### 🚀 Plugin Installation Error Fixed & Codeberg Plugin Pool Live

- **Extension Download & Installation Completely Fixed:** All 174 plugins (DiziPal, RecTV, FilmMakinesi, InatBox, TurkAnime, etc.) in the new Codeberg `KitsugiPlugins` repository now install seamlessly with 1-click installation.
- **Direct Codeberg URL Preservation:** Plugin download URLs are safely preserved and legacy GitHub endpoints are automatically routed to the new repository.

---

## 🇹🇷 Türkçe (v2.4.189)

### 🚀 Codeberg Entegrasyonu & Eklenti Havuzu Güvenliği (+18 Temizlendi)

- **Codeberg Otomatik Güncelleme Entegrasyonu:** Uygulama içi otomatik güncelleyici (`KitsugiUpdateRepository`) artık birincil güncelleme kaynağı olarak Codeberg Releases API'sini (`BlackDamage/Kitsugi-Beta`) kullanır. Olası bir durumda GitHub API'sine yedek (fallback) olarak bağlanır.
- **Güvenli ve Temiz Eklenti Deposu (+18 / NSFW Kaldırıldı):** Eklenti deposu GitHub'dan Codeberg'e (`BlackDamage/Kitsugi-Plugins`) taşındı. Topluluk ve mağaza kurallarına tam uyum için tüm yetişkin (+18/NSFW), ifşa ve deepfake eklentileri havuzdan kalıcı olarak temizlendi; 170+ popüler dizi, film, anime ve belgesel eklentisi (DiziPal, RecTV, FilmMakinesi, InatBox, TurkAnime vb.) optimize edilerek korundu.
- **Eski Depo URL'lerini Otomatik Yönlendirme:** Uygulama açılışında ve eklenti taramasında eski GitHub bağlantıları (`gameras1010-afk`, `KitsugiBeta-dev`, `Kekik-cloudstream`) otomatik olarak yeni Codeberg deposuna yönlendirilir.

---

## 🇬🇧 English (v2.4.189)

### 🚀 Codeberg Migration & Clean Plugin Repository (+18 Excluded)

- **Codeberg In-App Auto-Updates:** Primary update checking and release asset downloading has been migrated to the Codeberg Releases API (`BlackDamage/Kitsugi-Beta`) with GitHub fallback.
- **Clean & Safe Plugin Repository (+18 Excluded):** Migrated cloudstream plugins to Codeberg (`BlackDamage/Kitsugi-Plugins`), completely removing 116+ NSFW / adult / deepfake plugins to guarantee longevity and platform TOS compliance. All 170+ mainstream movie, anime, TV, and documentary sources remain fully available and updated.
- **Automatic Legacy URL Redirection:** Legacy GitHub URLs for plugins are seamlessly redirected to the new Codeberg repository.

---

## 🇹🇷 Türkçe (v2.4.189)

### 📚 Türkçe Manga & Webtoon Altyapısı Kökten Yenilendi (Keiyoushi V2, 77 TR Kaynağı, MangaDex 3.450 TR Başlık, Hotlink Çözümü)

- **Keiyoushi V2 Repo Şeması & Decoy Koruması:** Keiyoushi'nin modern `index.json` V2 şeması (`extensionList.extensions[]`) tam olarak desteklendi. 2 sahte kayıtlı decoy `index.min.json` otomatik bypass edilerek doğrudan `index.json` üzerinden tüm eklentiler listelenir hale getirildi. 404 veren ölü katalog URL'leri temizlendi.
- **2026 Keiyoushi İmza Anahtarı:** Yeni SHA-256 parmak izi (`9add655a...`) `TrustManager`'a eklendi ve repodan gelen eklentiler için dinamik güven zinciri entegre edildi.
- **Modern Eklenti API'sine Geçiş (extensionLib 1.6+):** 77 Türkçe eklentinin 71'inde detay ve bölüm listelerinin boş gelmesine sebep olan eski deprecated API çağrıları kaldırıldı; modern `getMangaUpdate` suspend API'sine geçildi.
- **MangaDex & Global Katalog Aramaya Dahil Edildi (3.450 TR Başlık):** `MangaSearchCoordinator` filtresindeki mantıksal engel kaldırılarak MangaDex gibi çok dilli global kaynaklar arama adaylarına eklendi. TR kaynaklarda bulunamayan mangalar MangaDex'teki 3.450 Türkçe çeviri üzerinden anında bulunabilir hale geldi.
- **Sayfa Görsellerinde Hotlink Koruması Çözüldü:** Manga-TR, TRManga, WebtoonHattı gibi hotlink korumalı CDN'lerin 403 Forbidden dönmesi engellendi. `MangaPageLoaderV2`, görselleri kaynağın kendi `Referer`, özel User-Agent ve Cloudflare interceptor'ları ile indirip doğrudan disk önbelleğine yazar ve Telephoto okuyucuya sunar.
- **Arama Eşzamanlılık Kapısı (Semaphore 6) & Akıllı Zaman Aşımı:** Aynı anda 70+ kaynağa istek atıp 429 veya bağlantı kopması yaşanmasını engellemek için 6'lı eşzamanlılık kapısı eklendi. Yeni nesil Keiyoushi motoru tanınarak zaman aşımı 25 saniyeye optimize edildi.
- **İki Geçişli Arama & Doujinshi Filtresi:** Arama sonuçlarının ilk turda gereksiz doujinshi veya spin-off'larla dolması engellendi. Önce sıkı skorlama yapılır, sonuç yoksa gevşek skorlama devreye girerek doğru manga ilk sıraya yerleştirilir.
- **Kaynak Sağlık Sınıflandırması İyileştirildi:** Arama teriminin bulunamaması (404) veya geçici DNS hataları ("unable to resolve host") artık kaynakları 2 saatlik "Broken" cezasına sokmaz; "Degraded" seviyesinde tutularak kaynakların kaybolması engellendi.
- **Eksik Host Shim Sınıfları Eklendi:** Eklentilerin ihtiyaç duyduğu `AppInfo`, `UnmeteredSource`, `RateLimitInterceptor` gibi sınıflar shim katmanına eklenerek sınıf yükleme çökmeleri giderildi.

---

## 🇬🇧 English (v2.4.189)

### 📚 Turkish Manga & Webtoon Engine Complete Overhaul (Keiyoushi V2, 77 TR Sources, MangaDex 3,450 TR Titles, Hotlink Bypass)

- **Keiyoushi V2 Schema & Decoy Bypass:** Added full support for the modern Keiyoushi V2 `index.json` schema (`extensionList.extensions[]`). Automatically bypasses the decoy 2-entry `index.min.json` and loads extensions directly from `index.json`. Removed dead 404 catalog URLs.
- **2026 Signing Key Trust:** Added the latest Keiyoushi SHA-256 fingerprint (`9add655a...`) to `TrustManager` and established dynamic signature trust for repository extensions.
- **Modern Extension API (extensionLib 1.6+):** Migrated from deprecated `fetchMangaDetails`/`fetchChapterList` stub methods to the modern `getMangaUpdate` suspend API, restoring detail and chapter lists across 71+ Turkish extensions.
- **MangaDex & Global Catalogs in Search (3,450 TR Titles):** Fixed a boolean logic barrier in `MangaSearchCoordinator`, enabling MangaDex and trusted global catalogs to participate in manga searches alongside Turkish sources.
- **Hotlink Protection Bypass in Image Loader:** Resolved 403 Forbidden errors on hotlink-protected CDNs (Manga-TR, TRManga, WebtoonHattı). `MangaPageLoaderV2` now prioritizes the extension's native `source.getImage()` pipeline with correct `Referer`, User-Agent, and Cloudflare cookies directly into disk cache for the Telephoto reader.
- **Concurrency Gate (Semaphore 6) & Smart Timeouts:** Prevented network congestion and 429 rate limits by bounding concurrent searches to 6 parallel requests. Added modern `KEI_SOURCE` engine detection and increased timeout to 25s for stable scraping on mobile networks.
- **Two-Pass Search Scoring & Doujinshi Guard:** Searches now execute a strict first-pass match before relaxing filters, preventing doujinshi and loose spinoffs from polluting main search results.
- **Softened Health Classifier:** Network DNS errors ("unable to resolve host") and search 404s now mark sources as Degraded rather than Broken, preventing false 2-hour cooldown lockouts.
- **Missing Extension Host Shims:** Added missing `AppInfo`, `UnmeteredSource`, and `RateLimitInterceptor` host shims to prevent ClassNotFound exceptions when loading extensions.

---

## 🇹🇷 Türkçe (v2.4.189)

### 🎭 TMDB Kurgusal Karakter Detayları, Seslendirmen Ayrımı, AL Arama ve Kesintisiz Keşfet (Kitsu Fallback)

- **TMDB Kurgusal Karakter Detay Sayfası Düzeltildi:** TMDB yapımlarındaki anime/kurgusal karakterlere (ör. Nobita Nobi, Doraemon, Rudeus Greyrat) tıklandığında seslendirmenin (ör. Megumi Oohara, Wasabi Mizuta) biyografi ve doğum günü yerine doğrudan karakterin kurgusal biyografisi, anime rolleri ve tüm seslendirmenlerinin listelendiği sayfa açılacak şekilde düzeltildi.
- **Karakter Görsel Karışıklığı Kökten Çözüldü:** Mushoku Tensei gibi aynı soyadını ("Greyrat") taşıyan karakterlerin (Rudeus, Former Self, Eris) ve rol varyantlarının yanlış token eşleşmesiyle aynı görseli alması engellendi. Öz isim doğrulaması (given-name guard) ve varyant filtreleri eklendi.
- **AniList (AL) Arama Sorunu Giderildi:** AniList GraphQL sorgusunda eksik olan `$isLicensed: Boolean` tanımı eklenerek sunucudan HTTP 400 Bad Request dönmesi ve aramalarda AL satırının kaybolması düzeltildi.
- **AniList Keşfet Sayfası & Kitsu Otomatik Fallback:** AniList keşfet sayfasındaki genel erişim iyileştirildi; sunucu hatası, hız sınırı veya boş liste durumlarında Kitsu keşfet motorunun otomatik devreye girerek listeleri eksiksiz doldurması sağlandı.

---

## 🇬🇧 English (v2.4.189)

### 🎭 TMDB Fictional Character Details, Voice Actor Separation, AL Search & Seamless Kitsu Fallback

- **Fictional Character Resolution in TMDB:** Tapping anime characters in TMDB entries now reliably opens the character's fictional profile (biography, appearances, and voice actors) via AniList/Jikan rather than displaying the real voice actor's personal biography and birthday.
- **Character Image Pollution Fixed:** Resolved an issue where characters with shared family surnames (e.g., Greyrat in Mushoku Tensei) or role variants ("Former Self") mistakenly received the same image. A robust given-name guard and variant modifier matching algorithm ensures exact individual character mapping.
- **AniList Search Restored:** Fixed an HTTP 400 Bad Request caused by a missing `$isLicensed: Boolean` definition in the AniList GraphQL search query. AniList results now reliably appear in both "All" and "Anime" search tabs.
- **AniList Explore & Seamless Kitsu Fallback:** Restored public AniList explore browsing without token requirements. Whenever AniList returns empty data or encounters issues, Kitsu automatically and seamlessly populates the explore carousels.

---

## 🇹🇷 Türkçe (v2.4.189)

### 🎬 TMDB Film/Dizi ID Eşleme Düzeltmesi & Yorumlardaki Resim Büyüme Sorunu Çözüldü

- **TMDB Anime Filmleri Diziyle Karışma Sorunu Düzeltildi:** TMDB keşfet sayfalarında "Howl's Moving Castle" (Yürüyen Şato) gibi anime filmlerine tıklandığında film yerine aynı TMDB ID numarasına sahip "Roar" adlı TV dizisinin açılması sorunu giderildi. TMDB anime filmlerinin türü doğru şekilde `MediaType.Movie` olarak belirlendi, başlık doğrulaması eklendi ve önbellek film/dizi ayrımıyla güçlendirildi.
- **Yorumlar & Tartışmalardaki Resimlerin Yavaşça Büyümesi Düzeltildi:** Konu detayı ve yorumlarda paylaşılan görsellerin ve GIF'lerin sayfa açıldıkça veya render edildikçe yavaşça kendi kendine büyüyüp genişlemesi (Compose `animateContentSize` döngüsü) durduruldu. Görseller artık anında sabit orantıyla yüklenir, maksimum yükseklik sınırı (240dp) ile kart düzenini bozmaz ve tıklandığında tam ekran galeri açılır.
- **İndirme Ayarları Veri Yönetiminden Ayrıldı:** Veri & Yedekleme içindeki "İndirmeler" sekmesi oradan çıkarıldı; Ayarlar menüsünde doğrudan "İndirme Ayarları" bağımsız bir sayfa olarak yer aldı. Veri & Yedekleme ekranı sadeleştirildi.
- **İndirmeler Ekranı Sağa/Sola Kaydırılabilir Yapıldı:** İndirmeler sayfasındaki Videolar, Altyazılar ve Resimler sekmeleri arasında parmakla sağa ve sola kaydırarak (swipe gesture) geçiş desteği eklendi.
- **Arama Sayfası Yukarı Kaydırma Butonu & Alt Bar Senkronizasyonu:** Arama sayfasında aşağı kaydırırken alt bar ile yukarı kaydırma butonunun (FAB) üst üste binmesi sorunu giderildi. Buton alt barın durumuna göre dinamik olarak barın üzerinde konumlanır, butona tıklandığında alt bar anında geri gelir ve sayfa tepeye ulaştığında alt barın görünür kalması sağlandı.

---

## 🇬🇧 English (v2.4.189)

### 🎬 TMDB Movie/TV ID Mapping Fix, Comment Image Auto-Expansion Resolved & Downloads Navigation

- **TMDB Anime Movie/TV Confusion Resolved:** Fixed an issue where clicking anime movies on TMDB explore (such as *Howl's Moving Castle*) opened a TV show (*Roar*) with the same TMDB ID. Anime movies are now accurately typed as `MediaType.Movie`, verified by title matching, and differentiated in Room cache.
- **Forum & Comment Image Auto-Growing Fixed:** Fixed the issue where inline images and GIFs in discussion threads and comments slowly expanded on their own due to Compose `animateContentSize` layout passes. Inline images now display immediately with fixed bounds (max 240dp height), preserving clean comment layouts.
- **Dedicated Download Settings Subpage:** Removed the download settings tab from inside "Data & Backup". Created a clean, standalone "Download Settings" item in the Settings menu for quick access.
- **Horizontal Swipe for Downloads Screen:** The Downloads screen (Videos, Subtitles, Images) is now horizontally swipeable with smooth paging gestures.
- **Search Screen Scroll-to-Top & Bottom Bar Sync:** Fixed the overlap between the scroll-to-top floating button and the bottom navigation bar on the Search screen. The button dynamically floats above the bottom bar, and tapping it immediately restores the bottom bar while scrolling to the top.
