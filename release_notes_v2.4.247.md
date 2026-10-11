# Kitsugi-Beta v2.4.247 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 💾 1. Depolama Ekranına "İndirilenler" Bölümü
- **Görünmez dosyalar artık ölçülüyor:** Ayarlar > Depolama ekranı yalnızca önbellekleri gösteriyordu; indirdiğiniz **videolar, görseller ve altyazılar** hiçbir yerde görünmüyordu. Yeni **İndirilenler** bölümü üçünü ayrı ayrı boyut + dosya sayısı olarak listeler.
  - **İndirilen videolar:** `Download/Kitsugi/Video` altındaki bölümler (uygulama içi kopyalar + dışa aktarılan `.mp4` dosyaları) ve eski `cacheDir/Kitsugi_downloads` klasörü.
  - **İndirilen görseller:** galeriden cihaza kaydettiğiniz resimler (`Download/Kitsugi/Images`, `Pictures/Kitsugi/Images`, uygulama dış klasörü, eski `Download/Kitsugi`).
  - **İndirilen altyazılar:** video indirmelerinin yanındaki `subs/` klasörleri + `Download/Kitsugi/Subtitles`.
- **Toplu silme + onay penceresi:** Her satır ve yeni **"Tüm indirilenleri sil"** satırı, geri alınamaz işlem olduğu için **yıkıcı onay penceresi** açar; neyin silineceği tek tek yazılıdır. Silme bittikten sonra boyutlar otomatik tazelenir ve "X silindi" bildirimi gösterilir.
- **Güvenli ayrım:** İndirilen dosyalar bilinçli olarak önbellek listesine **eklenmedi** — "Güvenli önbellekleri temizle" düğmesi ve otomatik bakım kuralları bunlara asla dokunmaz. Üstteki "Toplam kullanım" da yalnızca yeniden üretilebilir önbellekleri kapsamaya devam eder; indirilen dosyaların toplamı kendi bölüm başlığında gösterilir.
- **Kilitli dosya koruması:** Devam eden bir indirme varsa servis durdurulur, kayıtlar temizlenir; silinemeyen dosyalar (ör. başka uygulamaya ait MediaStore kaydı) sayılır ama işlemi düşürmez. Silinen dosyalar `MediaScanner` ile sistemden de düşürülür.

### 🧹 2. Tek Bölüm Silmede Kalan "Hayalet" Kopyalar Düzeltildi
- Bir bölüm indirildiğinde video iki yere yazılıyordu: uygulama içi kopya ve `Download/Kitsugi/Video/<Başlık>_Bölüm_N.mp4` dışa aktarımı. **İndirilenler ekranından tek bölüm silindiğinde yalnızca uygulama içi klasör siliniyordu**; dışa aktarılan kopya ile `Download/Kitsugi/Subtitles` altındaki altyazılar diskte kalıyor, hiçbir ekranda görünmediği için kalıcı şişme yaratıyordu.
- Dosya adı üretimi ve temizlik tek kaynağa bağlandı: yeni `DownloadExportNaming` nesnesi hem indirirken hem silerken aynı adı hesaplar. Tek bölüm silme artık video + altyazı kopyalarının tamamını ve MediaStore kaydını temizler.

---

## 🇬🇧 English

### 💾 1. New "Downloads" Section on the Storage Screen
- **Invisible files are now measured:** Settings > Storage only listed caches; your downloaded **videos, images and subtitles** were not shown anywhere. The new **Downloads** section lists all three with per-kind size and file count.
  - **Downloaded videos:** episode folders under `Download/Kitsugi/Video` (in-app copies + exported `.mp4` files) and the legacy `cacheDir/Kitsugi_downloads` folder.
  - **Downloaded images:** gallery images saved to the device (`Download/Kitsugi/Images`, `Pictures/Kitsugi/Images`, app external folder, legacy `Download/Kitsugi`).
  - **Downloaded subtitles:** `subs/` folders next to video downloads plus `Download/Kitsugi/Subtitles`.
- **Bulk delete with confirmation:** every row and the new **"Delete all downloads"** row opens a **destructive confirmation dialog** spelling out exactly what will be removed. Sizes refresh automatically afterwards with an "X deleted" status message.
- **Safe separation:** downloaded files were deliberately **kept out of the cache list** — "Clear safe caches" and automatic maintenance never touch them. The top "Total usage" still covers only re-downloadable caches; the downloads total is shown in its own section header.
- **Locked-file resilience:** running downloads are stopped and their records cleared first; files that cannot be deleted (e.g. a MediaStore entry owned by another app) are counted without aborting the operation. Deleted files are also removed from system media records via `MediaScanner`.

### 🧹 2. Fixed "Ghost" Copies Left Behind by Single-Episode Deletion
- Downloads were written twice: the in-app copy and an export to `Download/Kitsugi/Video/<Title>_Episode_N.mp4`. **Deleting a single episode from the Downloads screen only removed the in-app folder**, so the exported copy and the subtitles under `Download/Kitsugi/Subtitles` stayed on disk forever — invisible bloat.
- File naming and cleanup now share a single source of truth: the new `DownloadExportNaming` object computes the same names while downloading and while deleting. Single-episode deletion now removes the video and subtitle copies and their MediaStore entries too.
