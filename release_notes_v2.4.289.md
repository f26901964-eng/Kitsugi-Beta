# Kitsugi Beta v2.4.289

## 🇹🇷 Türkçe

### Düz İndirme Mimarisi & Tek Kopya İndirme Motoru
- **Doğrudan Tek Konuma Yazma:** Video indirmeleri artık ayrı bir geçici dahili alt klasör (`<mediaId>/`) oluşturup sonradan kopyalama adımı gerçekleştirmez; video akışı doğrudan `Downloads/Kitsugi/Video/<Başlık>_Bölüm_N.mp4` dosyasına yazılır. Çift dosya yazma ve diskte gereksiz yer kaplama/kopya sorunu kökten çözüldü.
- **Yan Yana Altyazı Düzeni:** Altyazılar videonun hemen yanına `<Başlık>_Bölüm_N.<dil>_<isim>.<uzantı>` olarak kaydedilir. Ayrı `Subtitles/` klasörü ve ikinci kopyalama adımı kaldırıldı.
- **Yalnızca Tamamlanan İndirmeler:** Metadata (`<taban>.json`) dosyası yalnızca indirme %100 başarıyla tamamlandıktan sonra oluşturulur; böylece yarım kalan veya iptal edilen indirmeler çevrimdışı listesinde görünmez.
- **Hatasız Poster İndirme:** İndirilen animelerin posterleri `Downloads/Kitsugi/Images/<taban>.jpg` olarak indirilir; poster indirmesinde ağ hatası çıksa bile video indirme işlemi kesintiye uğramaz veya başarısız sayılmaz.
- **MediaScanner İle Temiz Kayıt:** `MediaStore.insert` kaldırıldı, tüm medya indeksleme doğrudan `MediaScannerConnection` ile yapılır.
- **Özel SAF Klasörlerinde `(1)` Çiftleme Engeli:** Kullanıcı tanımlı özel indirme (SAF) klasörüne aktarımda hedefte aynı ada sahip dosya varsa önce eski dosya silinir; Android'in `(1)` ekleyerek dosyaları çoğaltması engellendi.
- **Merkezi Ad Yönetimi (`DownloadExportNaming`):** Video, altyazı, metadata ve poster dosya adları tek merkezden yönetilir; bir bölüm silindiğinde tüm ilişkili dosyalar (video, altyazılar, metadata ve poster) diskten eksiksiz temizlenir.
- **Geriye Dönük Çevrimdışı Oynatma:** `OfflinePlaybackHelper` hem yeni düz yapıdaki dosyaları hem de eski sürümlerden kalan `<mediaId>/` klasörlerini otomatik olarak tanır ve sorunsuz oynatır.
- **Altyazı Eşleştirme Düzeltmesi:** `DownloadsScreen` ve `DownloadItemRow` bileşenlerindeki altyazı aramasında nokta öneki (`$baseName.`) kontrolü eklendi; böylece `Bölüm_1` araması artık `Bölüm_12` altyazılarıyla karışmaz.

---

## 🇬🇧 English

### Flat Download Architecture & Deduplicated Single-Copy Pipeline
- **Direct Single-Copy Video Pipeline:** Videos are written directly to `Downloads/Kitsugi/Video/<Title>_Episode_N.mp4` without intermediate `<mediaId>/` subdirectories or post-download copy steps, permanently eliminating duplicated disk consumption.
- **Co-Located Subtitle Layout:** Subtitles are saved alongside video files as `<Title>_Episode_N.<lang>_<name>.<ext>`, deprecating the redundant `Subtitles/` directory and secondary copies.
- **Atomic Metadata Finalization:** Metadata (`<base>.json`) is generated only upon 100% successful download completion, ensuring incomplete or cancelled downloads never display in the offline library.
- **Resilient Poster Downloads:** Posters are stored as `Downloads/Kitsugi/Images/<base>.jpg`; any network error during poster fetching is safely caught without failing the video download.
- **Clean MediaScanner Registration:** Removed `MediaStore.insert` in favor of direct `MediaScannerConnection.scanFile` calls for robust media store updates.
- **Duplicate `(1)` Prevention on SAF Storage:** When exporting to user-selected SAF folders, existing files with the same name are deleted first, preventing Android scoped storage from spawning duplicate `(1)` files.
- **Unified Export Naming Engine:** Centralized filename conventions across video, subtitles, metadata, and posters (`DownloadExportNaming`), guaranteeing full cleanup upon episode deletion.
- **Backward-Compatible Offline Playback:** `OfflinePlaybackHelper` seamlessly discovers and plays both flat-layout and legacy `<mediaId>/` directory downloads.
- **Subtitle Matching Bug Fix:** Added dot delimiter (`$baseName.`) filtering in `DownloadsScreen`, preventing `Episode_1` searches from falsely matching `Episode_12` subtitles.
