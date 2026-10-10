# Kitsugi Beta v2.4.237 — Arama Logoları, Eklenti Bildirimi, Uncharted & Çeviri Düzeltmeleri

## 🇹🇷 Türkçe (v2.4.237)

### 🏷️ 1. Arama Sayfasında Orijinal Marka Logoları (Emoji Yerine Vektör Logolar)
- **Orijinal Marka Logoları Entegre Edildi:** Kaynağa özel arama başlıklarındaki emojiler (🅰️ AniList, Ⓜ️ MyAnimeList, 🎬 TMDB, 🌸 Shikimori, 🦊 Kitsu, 📺 Simkl, 🎌 Bangumi) kaldırılarak `KitsugiPlatformLogo` vektör çizimleri (`ic_logo_*`) yerleştirildi.
- **Filtre ve Sıralama Panelleri:** `SourceEngineFilterSheet` ve `SourceSpecificFilterChipsRow` başlıkları ilgili platformun logosuyla güncellendi; "Sıfırla" butonunun taşması önlendi.
- **Sonuç Kartı Rozetleri & Filtre Özeti:** Arama sonuç kartlarındaki rozetlere ve filtre özet çipine platformun gerçek marka logosu eklendi; logo bulunmayan kaynaklarda metin rozet korundu.
- **Uluslararasılaşma (i18n):** Başlıklar `R.string.arama` ve `R.string.filtreler` üzerinden okunarak İngilizce arayüzde doğru yerelleşme ("AniList Search", "Filters") sağlandı.

### 🛡️ 2. "Çok Yönlü Eşitleme" Diyaloğu Uyarı Kartı Sadeleştirildi
- **Katlanabilir Güvenlik Notu (`SafetyNote`):** Tekrarlanan turuncu uyarı kutusu tek bir katlanabilir karta dönüştürüldü; güvenlik ikonu, tek cümle özet ve "Uyarı/Kapat" butonuyla kompakt hale getirildi. Dokunulduğunda yasal metin kaydırma alanına taşmadan animasyonla açılıyor.

### 🧩 3. Eklentiler ve Kaynaklar Sayfası Düzenlendi
- **RealDebrid Giriş Alanı Kaldırıldı:** Mobil arayüzdeki gereksiz RealDebrid token alanı kaldırıldı (TV arayüzünde korundu); kayıtlı token Torrentio ön ayarına otomatik bağlanmaya devam ediyor.
- **İkonlu Segment Seçici & Ortak Kartlar:** Sekmeler modern segment seçiciye geçirildi ("Torrent & Akış / Video Kaynakları / Manga Kaynakları"). Bilgi şeritleri, adet rozetli başlıklar ve ortak boş-durum kartları uygulandı. Gömülü moddaki gereksiz alt şerit temizlendi.

### 🎨 4. Shikimori Stüdyo Yapımlarında Eksik Kapaklar Çözüldü
- **GraphQL Poster Çözümleme (`ShikimoriPosterResolver`):** REST API'sinde taşınmış veya eksik dönen kapaklar 50'lik gruplarla tek istekte GraphQL üzerinden çekilerek baş harf yer tutucusu yerine gerçek posterler yüklendi. Stüdyo logoları için aday arama derinliği artırıldı.

### 🎌 5. Bangumi "Düzenle" Diyaloğunun Simkl Açılması Düzeltildi
- **Kaynak Bağlamı Taşıma (`editingSourceHint`):** Bangumi sayfasında "Düzenle" tıklandığında kaydın Simkl görünümüne düşmesi engellendi; Bangumi rozeti, Bangumi durum pill'leri (İzliyorum/İzledim/Bekletiyorum...), "İzlenen Bölüm" ve Bangumi Koleksiyonu satırı doğru biçimde açılıyor.

### 🔔 6. Eklenti Toplu Kurulumunda Android Sistem Bildirimi (`AddonInstallNotifier`)
- **Tam Ekran Engelleyici Panel Kaldırıldı:** Ekranı kaplayan `AppBulkInstallProgress` paneli yerine sistem bildirim çubuğunda sessiz, kalıcı ilerleme bildirimi ("10/165 · İndiriliyor: Anizm") ve tamamlanma bildirimi devreye alındı.

### 🎬 7. Film Yedek Aramasında Yanlış TMDB Dizi Eşleşmesi Çözüldü (Uncharted Hatası)
- **ID Uzayı İzolasyonu:** Film kayıtlarının dizi ad alanında (`/tv/{id}/external_ids`) çözümlenmesi ve alakasız dizi posterleri / "Yaklaşan Yayın: Bölüm 7" gibi yanlış verilerin çekilmesi engellendi; TMDB dizi eşleşmelerine başlık doğrulama şartı getirildi.

### 🌐 8. Çeviri Motoru, Dinamik Dil Etiketi ve 429 Koruması
- **Hedef Dil Duyarlılığı:** Sabit "Türkçe" etiketi yerine ayarlardaki hedef dile göre dinamik etiketleme yapıldı. Parçalı çevirilerde başarılı kısımlar korunarak kullanıcının boş/orijinal metinle kalması engellendi.
- **Eşzamanlılık & Rate Limit:** Çeviri istekleri azami 2 eşzamanlı sorgu ve 1-2 sn bekleme ile sınırlandırılarak 429 hataları engellendi.

### 📲 9. Çeviri Düğmelerinde Harici Uygulama Açılışı
- **Tek Dokunuşla Harici Çeviri:** Stüdyo "Hakkında" butonu, biyografiler, özetler, spoiler, yorumlar, konular ve TV diyaloglarındaki tüm çeviri düğmeleri doğrudan kullanıcının tercih ettiği çeviri uygulamasına (`openTranslator`) yönlendirildi.

---

## 🇬🇧 English (v2.4.237)

### 🏷️ 1. Original Brand Logos Across Search Screens (Vector Logos Instead of Emojis)
- **Official Brand Logos Integrated:** Replaced source header emojis (🅰️ AniList, Ⓜ️ MyAnimeList, 🎬 TMDB, 🌸 Shikimori, 🦊 Kitsu, 📺 Simkl, 🎌 Bangumi) with official vector brand icons (`ic_logo_*`) via `KitsugiPlatformLogo`.
- **Filter & Sort Sheets:** Updated filter and sort headers with platform logos; added flexible weight distribution to prevent action button clipping.
- **Result Badges & Filter Summary Chip:** Brand logos added to search result cards and summary chip.
- **Localization (i18n):** Engine search titles now dynamically resolve through `R.string.arama` and `R.string.filtreler` ("AniList Search", "Filters").

### 🛡️ 2. Cross-Sync Dialog Warning Streamlined
- **Collapsible Safety Card (`SafetyNote`):** Merged redundant disclaimer texts into an elegant collapsible surface card with security icon, single-line summary, and toggleable disclaimer expansion.

### 🧩 3. Addons & Sources Screen Modernization
- **Cleaned Up RealDebrid Input:** Removed redundant token input card on mobile (retained in TV UI); active tokens automatically bind to Torrentio presets.
- **Segmented Tabs & Unified Cards:** Replaced TabRow with segmented icon tabs ("Torrent & Streams / Video Sources / Manga Sources"). Added counter badges, info banners, and unified empty state cards.

### 🎨 4. Shikimori Studio Works Poster Resolution
- **GraphQL Poster Batching (`ShikimoriPosterResolver`):** Fixed missing cover art on studio production lists by batch-resolving posters in groups of 50 via GraphQL. Enhanced studio logo lookup.

### 🎌 5. Bangumi Edit Dialog Routing Fix
- **Source Context Propagation (`editingSourceHint`):** Preserved original Bangumi page context when editing entries, preventing Simkl fallback and rendering native Bangumi status pills, badge, and collection indicators.

### 🔔 6. Android Notification for Bulk Addon Installs (`AddonInstallNotifier`)
- **Background Notification Progress:** Replaced the full-screen modal with an ongoing, low-priority system notification displaying real-time install progress ("10/165 · Downloading: Anizm") and completion summary.

### 🎬 7. Fixed TMDB Movie vs TV ID Collisions (Uncharted Poster Fix)
- **Namespace Separation:** Prevented movies from querying TV external IDs, stopping wrong show posters and "Next Airing: Episode 7" metadata from contaminating film details.

### 🌐 8. Translation Engine, Dynamic Target Language & Rate Limit Defense
- **Target Language Responsive:** Replaced hardcoded "Turkish" chips with active target language. Preserved successful segments on partial failures to prevent returning untranslated text. Throttled concurrent calls to 2 with backoff to stop 429 errors.

### 📲 9. Universal External Translator Integration
- **Direct App Launch:** All translation actions across synopsis, biographies, reviews, studio about, and topics now open user's preferred external translation app via `openTranslator`.
