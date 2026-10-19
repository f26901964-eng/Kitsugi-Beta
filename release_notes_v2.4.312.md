# Kitsugi Beta v2.4.312

## 🇹🇷 Türkçe

### 📺 Oynatıcı İçi Harici Oynatıcı Butonu & Konum Eşitleme
- **Doğrudan Harici Oynatıcıda Açma Butonu (`TopRightPlayerControls`, `MoreSheet`):** Oynatıcının sağ üst araç çubuğuna (Altyazı, Ses, Kalite ikonlarının yanına) ve "Daha Fazla" menüsünün üst aksiyon çubuğuna doğrudan harici oynatıcıda aç (`OpenInNew`) butonu eklendi.
- **Kesintisiz Konum, Başlık & Geri Dönüş Senkronizasyonu (`ExternalPlayerResultContract`):** Harici oynatıcıya geçildiğinde dahili oynatıcı duraklatılır; oynatma konumu, HTTP başlıkları (Origin, Referer, User-Agent), altyazılar ve kullanıcının tercih ettiği varsayılan oynatıcı paketi (MX Player, VLC, mpv-android, Just Player vb.) aktarılır. Harici oynatıcı kapatılıp uygulamaya dönüldüğünde kalınan konum ve tamamlanma durumu otomatik olarak oynatıcıya, izleme geçmişine ve scrobble denetleyicisine işlenir.

### 🎬 Aniyomi Oynatıcı Tam Paritesi & Gelişmiş Arayüz/Davranış
- **Media3 Yerel Bölüm (Chapter) Desteği:** ID3 `ChapterFrame` ve VorbisComment / Matroska (`CHAPTER001`, `CHAPTER001NAME`) bölüm metaverileri ayrıştırılarak seekbar ve Chapters menüsüne bağlandı.
- **OSD, Panel & Arayüz Tercihleri:** Kontrollerin otomatik gizlenme süresi (`playerTimeToDisappearMs`), panel/menü opaklığı (`panelOpacity`), hareket azaltma (`reducePlayerMotion`), sistem durum çubuğu görünürlüğü (`showSystemStatusBar`), ekran çentiği kullanımı (`playerFullscreenCutout`), yükleme halkası (`showLoadingCircle`) ve aktif chapter başlığı görünürlüğü (`showCurrentChapter`) ayarları eklendi.
- **Süre Göstergesi & Jest Özelleştirme:** Seekbar sağ süre göstergesine tıklanınca kalan süre (-DK:SN) ile toplam süre arasında kalıcı geçiş (`invertDurationTimer`); sol, orta ve sağ çift dokunma bölgeleri için bağımsız eylem seçimi (SEEK, PLAY_PAUSE, SWITCH_SUB vb.); son kullanılan parlaklık ve ses seviyesini hatırlama.
- **AniSkip Netflix Tarzı Geri Sayım & Otomatik Atlatma:** Kontroller gizliyken sağ altta yüzen "Giriş'i Atla" butonu; Netflix tarzı geri sayım (`enableNetflixStyleIntroSkip`, `waitingTimeIntroSkip`) ile süre sonunda otomatik atlama; videoda gömülü chapter varsa AniSkip çakışmasını engelleme politikası.
- **Seek Önizleme Optimizasyonu & Bölüm Küçük Resmi:** HLS/DASH/P2P akışlarında gereksiz ağ yükü engellendi; `content://` URI geçişlerinde `ParcelFileDescriptor` sızıntısı kapatıldı; ekran görüntüsünden kaydedilen bölüm küçük resmi hem oynatıcı içi hem de detay sayfası bölüm listesine bağlandı.
- **MPV Güvenlik & Ses Odağı İyileştirmesi:** AndroidCAStore sertifikaları PEM formatına aktarılarak MPV güven zincirine bağlandı (`MpvTrustStore`); duraklatma sırasında ses odağının diğer uygulamalara eksiksiz bırakılması sağlandı (`abandonFocus`).

### 📖 Manga Okuyucu Arşiv Desteği (ZIP / CBZ)
- **Yerel Arşiv Sayfa Yükleyici (`ArchivePageLoader`, `MangaPageLoaderV2`):** Çevrimdışı indirilen veya cihazda bulunan ZIP ve CBZ formatındaki manga arşivlerinden doğrudan sayfa okuma ve görüntüleme desteği eklendi.

### 💾 Veri Yedekleme ve Geri Yükleme (`BackupManager`)
- **Eksiksiz Uygulama Yedekleme:** Room veritabanı (izleme geçmişi, manga ilerlemesi, eklentiler), DataStore tercihleri ve indirme listesi tek bir ZIP arşivi halinde yedeklenebilir ve geri yüklenebilir hale getirildi.

### 📱 Ana Ekran İndirmeler Widget'ı (`DownloadWidgetProvider`)
- **Canlı İndirme Sayacı:** Ana ekrana eklenebilen ve anlık aktif indirme durumunu gösteren yeni sistem widget'ı eklendi.

---

## 🇬🇧 English

### 📺 In-Player External Player Launch & Playback Sync
- **Dedicated External Player Launcher (`TopRightPlayerControls`, `MoreSheet`):** Added a prominent "Open in External Player" icon (`OpenInNew`) to the top-right player toolbar (adjacent to Subtitles, Audio, and Quality) as well as the quick-actions row in the "More" bottom sheet.
- **Seamless Position, Headers & Return State Synchronization (`ExternalPlayerResultContract`):** Tapping the external button pauses internal playback and launches the chosen player (MX Player, VLC, mpv-android, Just Player) with full streaming headers (Origin, Referer, User-Agent), active subtitles, and the exact timestamp. Upon returning to Kitsugi, resumed position and completion states are seamlessly merged into playback history and anime scrobbler.

### 🎬 Complete Aniyomi Player Parity & UX Enhancements
- **Native Media3 Chapters Support:** Extracted and mapped ID3 `ChapterFrame` and VorbisComment / Matroska chapters to the timeline seekbar and chapters sheet.
- **OSD, Panels & Visibility Preferences:** Comprehensive controls timeout (`playerTimeToDisappearMs`), panel opacity (`panelOpacity`), reduced player motion (`reducePlayerMotion`), status bar toggling (`showSystemStatusBar`), display cutout handling (`playerFullscreenCutout`), loading spinner visibility (`showLoadingCircle`), and active chapter overlay (`showCurrentChapter`).
- **Time Display & Gesture Customization:** Tap right-hand duration display to toggle remaining time (-MM:SS) vs. total time (`invertDurationTimer`); independent left, center, and right double-tap zones (SEEK, PLAY_PAUSE, SWITCH_SUB, etc.); restore last-used volume and brightness levels.
- **AniSkip Netflix-Style Auto-Skip Countdown:** Floating "Skip Intro" badge displayed even when player controls are hidden; configurable Netflix-style auto-skip countdown (`enableNetflixStyleIntroSkip`); automatic AniSkip suppression when native chapters exist.
- **Seek Preview Optimization & Chapter Thumbnails:** Throttled network probes on HLS/DASH/P2P live streams; fixed `ParcelFileDescriptor` resource leaks; tied screenshot-saved thumbnails to both in-player and anime detail episode rows.
- **MPV Security & Audio Focus:** AndroidCAStore trusted roots exported to PEM for libmpv TLS verification (`MpvTrustStore`); proper audio focus abandonment on pause (`abandonFocus`).

### 📖 Manga Reader Local Archive Support (ZIP / CBZ)
- **Local Archive Page Loader (`ArchivePageLoader`, `MangaPageLoaderV2`):** Direct page reading and rendering from offline ZIP and CBZ manga archive files.

### 💾 Data Backup & Restore (`BackupManager`)
- **Full App Data Migration:** Safe backup and restoration of Room database, DataStore settings, and downloads manifest as a unified ZIP archive.

### 📱 Home Screen Downloads Widget (`DownloadWidgetProvider`)
- **Live Downloads Counter:** Added home screen widget displaying active and stored downloads count.
