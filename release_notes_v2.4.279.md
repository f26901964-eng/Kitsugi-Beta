# Kitsugi-Beta v2.4.279

## 🇹🇷 Türkçe (v2.4.279)

### 🎬 Mini Oynatıcı Kontrolleri, Arka Plan Duraklatma & Video Paylaşımı
- **Mini Oynatıcı Kontrol Paneli:** Detay sayfasındaki Videolar sekmesinde ve gömülü oynatıcılarda modern kontroller eklendi: Ortada oynat/duraklat düğmesi, alt kısımda dokunarak ve sürükleyerek sarılabilen seek bar, dakika:saniye / toplam süre etiketi (`01:23 / 03:45`), oynat/duraklat ve tam ekran (fullscreen) düğmesi.
- **Akıllı Otomatik Gizleme & Çift Dokunma:** Video oynatılırken kontroller 3 saniye sonra yumuşakça gizlenir; ekrana dokunulduğunda tekrar açılır. Ekrana çift dokunma (double tap) ile ±5 saniye ileri/geri sarma desteği sağlandı.
- **Arka Plana Alınınca Otomatik Duraklatma:** Videolar sekmesinden başlatılan video oynatımı, uygulama arka plana (`ON_PAUSE` / `ON_STOP`) geçtiğinde otomatik olarak durur. Kullanıcı uygulamaya geri döndüğünde video aniden oynamaya devam etmez; oynatma kontrolü kullanıcıya bırakılır.
- **Videolar Sekmesinde Paylaş Butonu:** Video listesindeki her satırın sağına paylaşım ikonu eklendi. Paylaşımda `"Yapım Adı · Video Başlığı"` ve video bağlantısı (bağlantı bulunmuyorsa YouTube arama bağlantısı) paylaşılır.

### 🌐 Evrensel Orijinal / Çeviri Geçişi (Original / Translation Toggle)
- **Yeni Geçiş Düğmesi:** Çeviri gösterilen her ekranda, çeviri ikonunun hemen solunda "Orijinal" / "Çeviri" geçiş butonu (`KitsugiOriginalToggleButton`) yer alır.
- **Çift Yönlü Görünüm:** Metin çevrilmiş haldeyken buton "Orijinal" metnini gösterir; dokunulduğunda orijinal dile döner ve buton "Çeviri" haline gelir.
- **Kapsam:** Anime ve manga açıklamaları, API arama detayları, karakter ve ekip biyografileri, stüdyo "Hakkında" kartı, Fandom Wiki serisi özeti, wiki öğe panelleri (sheet), karakter/kişi wiki bilgi kutuları ve tüm açılır wiki bölümleri.

### 📚 Fandom Wiki: Wikidata P6262 Entegrasyonu, İlişkili Sayfalar & Orijinal Logo
- **Doğrudan P6262 Başlık Eşleme:** Wikidata P6262 kodu üzerinden doğrudan makale başlığı okunur; catalog ve arama yedekleriyle kusursuz eşleşme sağlanır.
- **Tüm Sağlayıcılarla Uyumluluk:** IMDb (`tt...`), Wikidata (`Q...`), Simkl, TMDB, AniList ve MyAnimeList kimlikleri tek çatı altında desteklenir.
- **İlişkili Sayfalar ve Kadro:** Yapıma ait ilişkili sayfalar, karakterler ve ekip listesi wiki sekmesine entegre edildi (`FandomMediaArticleMatcher`, `FandomRelationClassifier`).
- **Wikidata Karakter Oylaması:** Wikidata'da doğrudan P4073 bulunmadığında karakterlerin Fandom sayfaları üzerinden oylama yapılarak doğru wiki seçilir.
- **Galeride Orijinal Fandom Logosu:** Resim galerisinde kaynak rozetinde düz "Fandom" yazısı yerine yerel `#FA005A` Fandom marka vektör logosu (`ic_logo_fandom.xml`) gösterilir.

### 📋 Listem: Animasyonlu Başlık Barı & Premium Kategori Paneli
- **Jest Tabanlı Animasyonlu Başlık:** "Listem" ve kullanıcı listesi ekranlarında üst bar, aşağı kaydırınca yumuşakça çekilir, yukarı kaydırınca geri gelir (`KitsugiStickyHeaderReveal` + `rememberScrollAwareHeaderState`). Titreme ve zıplama olmadan akıcı jest takibi.
- **Premium Kategori Paneli:** Durum filtresi (Tümü / İzleniyor / Tamamlandı / Planlanan...) paneli "Filtrele & Sırala" paneliyle birebir aynı `KitsugiPremiumSheet` yüzey diline kavuşturuldu (aurora saç çizgisi, gradyan sürükleme kulpu, ikon karosu, rozet, yay fiziği, "Tümü / Bitti" butonları).

### ✨ Işık Küresi (KitsugiPlasmaLoader) & GC / RenderThread Optimizasyonu
- **Tek Noktadan Yüklenme Dili:** Uygulama genelindeki 52 dairesel yüklenme göstergesi (`CircularProgressIndicator`) tek tip akıcı plazma ışık küresi (`KitsugiPlasmaLoader`) ile birleştirildi.
- **Hafif Çizim Yolu & 32 Adım Renk Kuantalaması:** 30 dp ve altındaki küçük göstergeler için hafif çizim yolu (3 gradyan, dönen poligonlar devre dışı) eklendi; renk döngüsü 32 adıma kuantalanarak bellek ve GPU yükü düşürüldü.

---

## 🇬🇧 English (v2.4.279)

### 🎬 Mini Player Controls, Background Pause & Video Sharing
- **Mini Player Controls:** Inline videos in the Videos tab and detail screens now feature center Play/Pause, a bottom drag-and-seek bar, elapsed/total time (`01:23 / 03:45`), play/pause, and a working Fullscreen button.
- **Auto-Hide & Double Tap Seek:** Controls smoothly fade out after 3 seconds of continuous playback and reappear on tap. Double-tapping left/right seeks ±5 seconds.
- **Background Pause:** Inline playback automatically pauses whenever the app goes to the background (`ON_PAUSE` / `ON_STOP`). Playback does not resume automatically upon returning, keeping control with the user.
- **Share Button on Video Rows:** Added a share icon to each video entry in the Videos tab, sharing `"Media Title · Video Title"` with the direct video URL or a YouTube search fallback.

### 🌐 Universal Original / Translation Toggle
- **Toggle Button:** Everywhere translated text is displayed, an "Original" / "Translation" toggle button (`KitsugiOriginalToggleButton`) appears right beside the translation icon.
- **Bidirectional Switch:** Shows "Original" when viewing translated text; tapping reverts to the original language and changes the button text to "Translation".
- **Scope:** Anime/Manga synopsis cards, API result detail synopsis, Character & Staff biographies, Studio "About" sections, Fandom Wiki media summary, item detail sheets, character/staff wiki infoboxes, and all expandable wiki sections.

### 📚 Fandom Wiki: Wikidata P6262 Integration, Related Entities & Official Logo
- **Direct P6262 Article Matching:** Resolves article titles directly from Wikidata P6262 before catalog/name fallback paths.
- **Multi-Provider Support:** Supports IMDb (`tt...`), Wikidata (`Q...`), Simkl, TMDB, AniList, and MyAnimeList seamlessly.
- **Related Entities Extraction:** Surfaces related pages, cast, voice actors, and crew directly in the wiki tab (`FandomMediaArticleMatcher`, `FandomRelationClassifier`).
- **Wikidata Character Voting:** Elects the matching wiki through character page voting when direct P4073 is missing on the media entry.
- **Official Fandom Logo in Gallery:** Replaces plain "Fandom" text on image gallery source badges with the official `#FA005A` vector logo (`ic_logo_fandom.xml`).

### 📋 MyList: Scroll-Aware Animated Header & Premium Category Sheet
- **Gesture-Based Animated Header:** MyList and User Media List headers now smoothly slide out on downward scroll and return on upward scroll (`KitsugiStickyHeaderReveal` + `rememberScrollAwareHeaderState`) without flicker or jumps.
- **Premium Category Sheet:** Redesigned the status filter sheet (All / Watching / Completed / Planned...) to match the "Filter & Sort" sheet using `KitsugiPremiumSheet` (aurora hairline, gradient drag handle, icon tile, badge, spring physics, and "All / Done" buttons).

### ✨ Plasma Orb Loader & GC / RenderThread Optimizations
- **Unified Loading Language:** Replaced 52 circular progress spinners across 40 files with the unified glowing plasma orb (`KitsugiPlasmaLoader`).
- **Compact Draw Path & 32-Step Color Quantization:** Added a lightweight draw path for spinners <= 30 dp and quantized the color cycle to 32 steps to minimize churn and protect the RenderThread and garbage collector.
