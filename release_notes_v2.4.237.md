# Kitsugi Beta v2.4.237 — Hesap Kasma Çözümü, Vitrin Tıklama, Grid & Navigasyon Düzeltmeleri, MAL Uygulama İçi Giriş, Arama Logoları, Eklenti Bildirimi, Galeri İzolasyonu

## 🇹🇷 Türkçe (v2.4.237)

### ⚡ 1. Bağlı Hesap Kaynaklı Kasma & Takılma Tamamen Çözüldü (`ExploreMapping`)
- **Kök Neden Giderildi:** Keşfet, "Tümünü Gör" ve Arama ekranlarındaki listede olmayan her bir kart için kullanıcının bağlı hesabındaki TÜM kütüphane taranıyor ve başlıklar ana iş parçacığında (UI thread) her karede yeniden normalize ediliyordu. Hesapta kayıt sayısı arttıkça yaşanan ağırlaşmanın asıl sebebi buydu.
- **Doğrudan İndeks Arama:** Tam liste taraması tamamen kaldırıldı. Eşleşme O(1) karma indeks adaylarıyla sınırlandırıldı. `realMalId` indekse eklenerek doğru eşleşmeler korundu, kaydırma ve yüklemeler yağ gibi akıcı hale getirildi.

### 🎯 2. Vitrin (Hero Section) Tıklama Yönlendirmesi Düzeltildi
- **Şeffaf Katman Hit-Test Engeli:** Vitrinde paralaks için arka arkaya çizilen katmanlarda, görünmez komşu sayfaların (`alpha = 0`) dokunma olaylarını yutması (One Piece'e tıklayınca Frieren detayının açılması) çözüldü.
- **Dinamik Öncü Sayfa (`frontPageIndex`):** Yalnızca o an ekranda önde duran aktif sayfa tıklama alacak şekilde kısıtlandı.

### 🚀 3. "Tümünü Gör" Grid Yarış Durumu (Race Condition) ve İç İçe Geçme Düzeltildi
- **Snapshot Birleştirme Mantığı:** Hızlı kaydırma sırasında 1. sayfa zenginleştirilirken (enrich) 2. sayfa geldiğinde oluşan yarış durumu çözüldü; enrich tamamlandığında mevcut liste snapshot ile akıllıca birleştirilerek (`merge`) kartların kaybolması ve zıplaması önlendi.
- **Benzersiz & Stabil Kimlik (`exploreIdentity`):** `source:type:malId:tmdbId:cs3UrlHash` tabanlı stabil anahtarlama sağlandı. TMDB film/dizi ve eklenti ID çakışmaları tamamen engellendi.
- **Stabil Compose Durumu:** Grid ve listelerdeki `rememberSaveable` kaynaklı Bundle restore çakışması `rememberLazyGridState()` ve `rememberLazyListState()` ile çözüldü; Compose kartları sorunsuz yeniden kullanılıyor.

### 🔑 4. MyAnimeList (MAL) Girişinde Uygulama İçi WebView ve Basic Auth Çözümü
- **Uygulama İçi WebView (`KitsugiOAuthWebViewDialog`):** Harici tarayıcıya yönlendirme yerine uygulama içi modern WebView ile doğrudan giriş sağlandı.
- **HTTP Basic Auth Popup Engellendi:** Brave gibi tarayıcılarda 401 Basic Auth popup çıkmasına sebep olan sorun giderildi; `onReceivedHttpAuthRequest` engellenerek kullanıcının doğrudan standart HTML giriş formuna erişmesi sağlandı.
- **Otomatik Yönlendirme:** `malapp://auth` yönlendirmesi yakalanarak token takası sorunsuz tamamlanıyor. `KitsugiWebViewDialog` için de aynı Basic Auth koruması uygulandı.

### 📖 5. Manga "Oku" Navigasyonu Düzeltildi
- **Detay Sayfası Üstünde Açılış:** Manga detay sayfasından (örn. Berserk) "Oku" butonuna basıldığında kaynak arama ekranı artık detay sayfasının üzerinde açılıyor.
- **Geri Dönüş Korundu:** `detailBackStack` temizlenmesi engellendi; kaynak arama ekranında geri tuşuna basıldığında Keşfet sekmesine düşmek yerine doğruca ilgili manganın detay sayfasına dönülüyor.

### 📐 6. Keşfet Raf Başlıklarına 20dp Kenar Boşluğu
- **Hizalama Düzenlendi:** "Yayındaki Animeler", "Yaklaşan Animeler", "Tümünü Gör" dahil tüm raf başlıklarına ve üst kısımlara kartlarla birebir aynı 20dp iç boşluk (`padding`) verildi; ekran kenarına yapışık görünüm giderildi.

### 🖼️ 7. "Tümünü Gör" Grid Görünümü ve 2:3 Poster Oranı
- **Tam Poster Görünümü:** Poster alanı sabit 210dp yerine 2:3 dikey en-boy oranına (`aspectRatio(2f / 3f)`) geçirildi; afişlerin kırpılması önlendi.
- **Uyumlu Kolon Düzeni:** Stüdyo sayfasıyla eşitlendi: standart telefonlarda 3 sütun, geniş ekranlarda 4 sütun, tabletlerde 5 sütun.

### 🔔 8. Eklenti Kurulumunda Bildirim Çubuğu (`AddonInstallNotifier`)
- **Tam Ekran Engelleyici Panel Kaldırıldı:** Keşfet ekranını kaplayan büyük "Eklentiler Kuruluyor" paneli tamamen kaldırıldı. İlerleme sessizce Android bildirim çubuğunda gösteriliyor ("10 / 165 · İndiriliyor: Anizm"). Ayarlar ekranındaki repo kartı ilerlemesi korundu.

### 🎨 9. TMDB ve Fanart Galeri/Logo İzolasyonu (Uncharted / Family Full House Düzeltmesi)
- **Film ve Dizi ID Alanı Ayrıldı:** TMDB'de film ve dizi ID'leri birbirinden izole edildi; film sayfasına yalnızca film afiş/arkaplanları, dizi sayfasına yalnızca dizi görselleri bağlandı.
- **Başlık Eşleşme Doğrulaması:** TMDB başlığı medya başlığıyla uyuşmadığında logonun yanlış yapıma uygulanması engellendi.

### 🏷️ 10. Arama Sayfasında Orijinal Marka Logoları (Emoji Yerine Vektör Logolar)
- **Orijinal Marka Logoları Entegre Edildi:** Kaynağa özel arama başlıklarındaki emojiler (AniList, MyAnimeList, TMDB, Shikimori, Kitsu, Simkl, Bangumi) kaldırılarak `KitsugiPlatformLogo` vektör çizimleri (`ic_logo_*`) yerleştirildi.
- **Filtre ve Sıralama Panelleri:** Başlıklar ve sonuç kartlarındaki rozetler resmi marka logolarıyla zenginleştirildi; uluslararasılaşma (i18n) tamamlandı.

### 🛡️ 11. "Çok Yönlü Eşitleme" Diyaloğu Uyarı Kartı Sadeleştirildi
- **Katlanabilir Güvenlik Notu (`SafetyNote`):** Tekrarlanan turuncu uyarı kutusu tek bir katlanabilir karta dönüştürüldü; güvenlik ikonu, tek cümle özet ve animasyonlu yasal metinle kompakt hale getirildi.

### 🧩 12. Eklentiler ve Kaynaklar Sayfası Düzenlendi
- **Segment Seçici & Ortak Kartlar:** Mobil ekranda RealDebrid giriş alanı temizlendi; sekmeler segment seçiciye geçirildi ve ortak boş-durum bileşenleriyle zenginleştirildi.

### 🌸 13. Shikimori Stüdyo Kapakları ve Bangumi Düzenleme Düzeltmeleri
- **GraphQL Poster Çözümleme:** Stüdyo yapımlarındaki eksik kapaklar 50'lik gruplarla GraphQL üzerinden çekildi.
- **Bangumi Düzenle Desteği (`editingSourceHint`):** Bangumi detayından düzenleme açıldığında Simkl'e düşme engellendi; Bangumi durum pill'leri ve rozetleri gösterildi.

### 🌐 14. Çeviri Motoru İyileştirmeleri & Harici Çeviri Uygulaması
- **Dinamik Dil:** Ayarlanan hedef dile göre duyarlı etiketleme yapıldı; 429 rate limit koruması sağlandı.
- **Harici Uygulama Açılışı:** Çeviri düğmeleri kullanıcının harici çeviri uygulamasına (`openTranslator`) bağlandı.

---

## 🇬🇧 English (v2.4.237)

### ⚡ 1. Library Mapping Performance Bottleneck Fixed (`ExploreMapping`)
- **Root Cause Eliminated:** For every card not in the user list, the code performed an O(N) full scan over the user's entire connected account library and normalized titles on the UI thread.
- **Direct Index Resolution:** Eliminated full library fallbacks. Candidate searches are strictly index-driven. Added `realMalId` index keys to preserve genuine matches, eliminating frame drops.

### 🎯 2. Hero Section Click Routing Fix
- **Hit-Test Layering Fixed:** In the multi-layer parallax hero pager, invisible neighboring pages (`alpha = 0`) were intercepting touch events (causing taps on One Piece to open Frieren).
- **Active Front Page Guard (`frontPageIndex`):** Only the active, front-most page receives touch events.

### 🚀 3. "See All" Grid Race Condition & Visual Overlap Fix
- **Enrich Snapshot Merging:** Resolved the race condition during fast scrolling where page 1 enrichment overwritten newly loaded page 2 results. Snapshot merge logic ensures persistent, smooth data flow without flickering or jumping cards.
- **Unique & Stable Identity (`exploreIdentity`):** Switched to robust composite keying (`source:type:malId:tmdbId:cs3UrlHash`), preventing TMDB movie/TV and extension collisions.
- **Stable Compose State:** Replaced `rememberSaveable` state restores with `rememberLazyGridState()` and `rememberLazyListState()` to eliminate Bundle index drift and overlapping layouts.

### 🔑 4. In-App MyAnimeList (MAL) OAuth & Basic Auth Elimination
- **In-App OAuth Dialog (`KitsugiOAuthWebViewDialog`):** Replaced external browser redirection with an optimized in-app WebView for seamless authentication.
- **HTTP Basic Auth Dialog Suppressed:** Intercepted and blocked 401 Basic Auth browser dialogs (`onReceivedHttpAuthRequest`), presenting MAL's native HTML login form directly.
- **Redirect Capture:** Seamlessly catches `malapp://auth` redirects and performs the OAuth token exchange automatically.

### 📖 5. Manga "Read" Navigation Fixed
- **Open on Top of Detail:** Tapping "Read" from a manga detail screen (e.g. Berserk) now presents the source search page on top of the media detail view.
- **Back Navigation Restored:** Prevented clearing `detailBackStack`; pressing the Android Back button now reliably returns to the manga detail screen instead of falling back to Explore.

### 📐 6. Explore Shelf Header 20dp Padding
- **Uniform Alignment:** Added 20dp horizontal padding to shelf headers ("Airing Anime", "Upcoming Anime", "See All", etc.) matching the media cards, resolving edge-clipping.

### 🖼️ 7. "See All" Grid & 2:3 Poster Aspect Ratio
- **Full Poster Presentation:** Switched fixed 210dp height to 2:3 vertical aspect ratio (`aspectRatio(2f / 3f)`), completely preventing poster cropping.
- **Responsive Columns:** Standardized column count to 3 columns on phones, 4 on wide phones, and 5 on tablets.

### 🔔 8. System Notification for Bulk Addon Installs
- **Removed Full-Screen Overlay:** The blocking "Installing Addons" overlay was removed. Real-time progress is now presented quietly via Android notification bar ("10 / 165 · Downloading: Anizm").

### 🎨 9. TMDB & Fanart Artwork Isolation (Uncharted / Family Full House Fix)
- **ID Namespace Separation:** Film and TV show IDs are strictly separated; film views only receive movie artwork, TV views only receive show artwork.
- **Strict Title Verification:** Vector and Fanart logos are rejected if the TMDB title does not match the media title.

### 🏷️ 10. Official Brand Logos in Search (Vector Logos)
- **Official Brand Logos:** Replaced emojis across search engine headers (AniList, MyAnimeList, TMDB, Shikimori, Kitsu, Simkl, Bangumi) with official vector brand icons (`ic_logo_*`).
- **Sheets & Result Badges:** Filter sheets, chips, and result badges now feature platform logos.

### 🛡️ 11. Streamlined Cross-Sync Safety Disclaimer
- **Collapsible Safety Card (`SafetyNote`):** Compacted disclaimer text into a collapsible surface with security icon and expandable legal text.

### 🧩 12. Addons & Sources UI Modernization
- **Segmented Tabs:** Removed mobile RealDebrid token input; added segmented tab selector, counter badges, and shared empty state components.

### 🌸 13. Shikimori Studio Posters & Bangumi Edit Context
- **GraphQL Poster Batching:** Batched 50 items per query to retrieve missing studio production covers.
- **Bangumi Edit Routing:** Preserved Bangumi source context to display native status pills and badges instead of Simkl.

### 🌐 14. Translation Engine & External App Integration
- **Target Language Responsive:** Dynamic target language chip and 429 backoff protection.
- **Direct App Launch:** Universal translator action opens preferred external translation app via `openTranslator`.
