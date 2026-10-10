# Kitsugi Beta v2.4.237 — Manga Oku Navigasyonu, Keşfet & Grid Düzenlemeleri, Arama Logoları, Eklenti Bildirimi, Galeri İzolasyonu

## 🇹🇷 Türkçe (v2.4.237)

### 📖 1. Manga "Oku" Navigasyonu Düzeltildi
- **Detay Sayfası Üstünde Açılış:** Manga detay sayfasından (örn. Berserk) "Oku" butonuna basıldığında kaynak arama ekranı artık detay sayfasının üzerinde açılıyor.
- **Geri Dönüş Korundu:** `detailBackStack` temizlenmesi engellendi; kaynak arama ekranında geri tuşuna basıldığında Keşfet sekmesine düşmek yerine doğruca ilgili manganın detay sayfasına dönülüyor.

### 📐 2. Keşfet Raf Başlıklarına 20dp Kenar Boşluğu
- **Hizalama Düzenlendi:** "Yayındaki Animeler", "Yaklaşan Animeler", "Tümünü Gör" dahil tüm raf başlıklarına ve üst kısımlara kartlarla birebir aynı 20dp iç boşluk (`padding`) verildi; ekran kenarına yapışık görünüm giderildi.

### 🖼️ 3. "Tümünü Gör" Grid Görünümü ve 2:3 Poster Oranı
- **Tam Poster Görünümü:** Poster alanı sabit 210dp yerine 2:3 dikey en-boy oranına (`aspectRatio(2f / 3f)`) geçirildi; afişlerin kırpılması önlendi.
- **Uyumlu Kolon Düzeni:** Stüdyo sayfasıyla eşitlendi: standart telefonlarda 3 sütun, geniş ekranlarda 4 sütun, tabletlerde 5 sütun.

### 🔔 4. Eklenti Kurulumunda Bildirim Çubuğu (`AddonInstallNotifier`)
- **Tam Ekran Engelleyici Panel Kaldırıldı:** Keşfet ekranını kaplayan büyük "Eklentiler Kuruluyor" paneli tamamen kaldırıldı. İlerleme sessizce Android bildirim çubuğunda gösteriliyor ("10 / 165 · İndiriliyor: Anizm"). Ayarlar ekranındaki repo kartı ilerlemesi korundu.

### 🎨 5. TMDB ve Fanart Galeri/Logo İzolasyonu (Uncharted / Family Full House Düzeltmesi)
- **Film ve Dizi ID Alanı Ayrıldı:** TMDB'de film ve dizi ID'leri birbirinden izole edildi; film sayfasına yalnızca film afiş/arkaplanları, dizi sayfasına yalnızca dizi görselleri bağlandı.
- **Başlık Eşleşme Doğrulaması:** TMDB başlığı medya başlığıyla uyuşmadığında logonun yanlış yapıma uygulanması engellendi.

### 🏷️ 6. Arama Sayfasında Orijinal Marka Logoları (Emoji Yerine Vektör Logolar)
- **Orijinal Marka Logoları Entegre Edildi:** Kaynağa özel arama başlıklarındaki emojiler (AniList, MyAnimeList, TMDB, Shikimori, Kitsu, Simkl, Bangumi) kaldırılarak `KitsugiPlatformLogo` vektör çizimleri (`ic_logo_*`) yerleştirildi.
- **Filtre ve Sıralama Panelleri:** Başlıklar ve sonuç kartlarındaki rozetler resmi marka logolarıyla zenginleştirildi; uluslararasılaşma (i18n) tamamlandı.

### 🛡️ 7. "Çok Yönlü Eşitleme" Diyaloğu Uyarı Kartı Sadeleştirildi
- **Katlanabilir Güvenlik Notu (`SafetyNote`):** Tekrarlanan turuncu uyarı kutusu tek bir katlanabilir karta dönüştürüldü; güvenlik ikonu, tek cümle özet ve animasyonlu yasal metinle kompakt hale getirildi.

### 🧩 8. Eklentiler ve Kaynaklar Sayfası Düzenlendi
- **Segment Seçici & Ortak Kartlar:** Mobil ekranda RealDebrid giriş alanı temizlendi; sekmeler segment seçiciye geçirildi ve ortak boş-durum bileşenleriyle zenginleştirildi.

### 🌸 9. Shikimori Stüdyo Kapakları ve Bangumi Düzenleme Düzeltmeleri
- **GraphQL Poster Çözümleme:** Stüdyo yapımlarındaki eksik kapaklar 50'lik gruplarla GraphQL üzerinden çekildi.
- **Bangumi Düzenle Desteği (`editingSourceHint`):** Bangumi detayından düzenleme açıldığında Simkl'e düşme engellendi; Bangumi durum pill'leri ve rozetleri gösterildi.

### 🌐 10. Çeviri Motoru İyileştirmeleri & Harici Çeviri Uygulaması
- **Dinamik Dil:** Ayarlanan hedef dile göre duyarlı etiketleme yapıldı; 429 rate limit koruması sağlandı.
- **Harici Uygulama Açılışı:** Çeviri düğmeleri kullanıcının harici çeviri uygulamasına (`openTranslator`) bağlandı.

---

## 🇬🇧 English (v2.4.237)

### 📖 1. Manga "Read" Navigation Fixed
- **Open on Top of Detail:** Tapping "Read" from a manga detail screen (e.g. Berserk) now presents the source search page on top of the media detail view.
- **Back Navigation Restored:** Prevented clearing `detailBackStack`; pressing the Android Back button now reliably returns to the manga detail screen instead of falling back to Explore.

### 📐 2. Explore Shelf Header 20dp Padding
- **Uniform Alignment:** Added 20dp horizontal padding to shelf headers ("Airing Anime", "Upcoming Anime", "See All", etc.) matching the media cards, resolving edge-clipping.

### 🖼️ 3. "See All" Grid & 2:3 Poster Aspect Ratio
- **Full Poster Presentation:** Switched fixed 210dp height to 2:3 vertical aspect ratio (`aspectRatio(2f / 3f)`), completely preventing poster cropping.
- **Responsive Columns:** Standardized column count to 3 columns on phones, 4 on wide phones, and 5 on tablets.

### 🔔 4. System Notification for Bulk Addon Installs
- **Removed Full-Screen Overlay:** The blocking "Installing Addons" overlay was removed. Real-time progress is now presented quietly via Android notification bar ("10 / 165 · Downloading: Anizm").

### 🎨 5. TMDB & Fanart Artwork Isolation (Uncharted / Family Full House Fix)
- **ID Namespace Separation:** Film and TV show IDs are strictly separated; film views only receive movie artwork, TV views only receive show artwork.
- **Strict Title Verification:** Vector and Fanart logos are rejected if the TMDB title does not match the media title.

### 🏷️ 6. Official Brand Logos in Search (Vector Logos)
- **Official Brand Logos:** Replaced emojis across search engine headers (AniList, MyAnimeList, TMDB, Shikimori, Kitsu, Simkl, Bangumi) with official vector brand icons (`ic_logo_*`).
- **Sheets & Result Badges:** Filter sheets, chips, and result badges now feature platform logos.

### 🛡️ 7. Streamlined Cross-Sync Safety Disclaimer
- **Collapsible Safety Card (`SafetyNote`):** Compacted disclaimer text into a collapsible surface with security icon and expandable legal text.

### 🧩 8. Addons & Sources UI Modernization
- **Segmented Tabs:** Removed mobile RealDebrid token input; added segmented tab selector, counter badges, and shared empty state components.

### 🌸 9. Shikimori Studio Posters & Bangumi Edit Context
- **GraphQL Poster Batching:** Batched 50 items per query to retrieve missing studio production covers.
- **Bangumi Edit Routing:** Preserved Bangumi source context to display native status pills and badges instead of Simkl.

### 🌐 10. Translation Engine & External App Integration
- **Target Language Responsive:** Dynamic target language chip and 429 backoff protection.
- **Direct App Launch:** Universal translator action opens preferred external translation app via `openTranslator`.
