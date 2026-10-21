# Kitsugi Beta v2.4.332

## 🇹🇷 Türkçe

### 🗑️ İndirilenler — Altyazı ve Resim Silme Kök Neden Onarımı
- **Kök neden:** Görseller ve altyazılar Android 10+ üzerinde `MediaStore` ile paylaşılan
  depolamaya yazılıyor. Bu dosyalarda `File.delete()` **hata fırlatmadan `false` dönüyor**;
  İndirilenler ekranındaki sil düğmeleri bu yüzden hiçbir şey silmiyordu (kart tazelenince
  geri geliyordu). Videolar farklı bir API (`AnimeDownloadManager`) kullandığı için sorun
  yalnızca altyazı/resim sekmelerinde görünüyordu.
- **Yeni `MediaStoreFileDeleter`:** Kayıt `DISPLAY_NAME` + `RELATIVE_PATH` ile bulunup
  `ContentResolver.delete` ile siliniyor (`DATA` sütununa dayalı, Android 11+'da çalışmayan
  arama kaldırıldı). Sahiplik `OWNER_PACKAGE_NAME` ile denetleniyor.
- **Görsel silme:** Tekli kart ve grup silmesi tek akışta birleşti
  (`KitsugiImageDownloadHelper.deleteImageFile`): index kaydı (MediaStore URI / SAF) →
  aynı adlı tüm kopyalar → gerekirse MediaStore-by-name → **sonuç doğrulaması**.
  Artık "sildim" denmeden önce dosyanın gerçekten gittiği kontrol ediliyor.
- **Altyazı silme:** `DownloadStorage.deleteSubtitle` aynı adlı tüm kopyaları
  (`Kitsugi/Subtitles`, `Kitsugi/Video`, video alt klasörleri, `cacheDir/subtitles`) siler;
  tek bir kopya kaldığında öğe listede kalıyordu — o davranış bitti.
- **Sistem onayı:** Uygulama yeniden kurulduğunda eski UID ile oluşmuş kayıtlar için
  Android 11+ `MediaStore.createDeleteRequest` onay penceresi açılıyor. Onaylanırsa dosya
  sistem tarafından silinir, iptal edilirse kullanıcı bilgilendirilir.
- **Geri bildirim:** Silme başarısız olduğunda artık sessiz kalınmıyor; nedeni toast ile
  gösteriliyor ve liste diskteki gerçek duruma göre tazeleniyor.
- **Grup silme onayı:** Bir içeriğin tüm resimlerini silen düğme artık onay penceresi açıyor
  (yanlışlıkla 15–20 resmi tek dokunuşta silmeyi önler).
- **Galeri:** İndirilenler ekranından açılan galeride de sistem onayı akışı destekleniyor.
- **Ayarlar → Depolama:** "Altyazıları sil" işlemi de yeni sağlam silme yolunu kullanıyor.

### 🧪 Doğrulama
- Silme sonucu her adımda doğrulanır (`file.exists()` kontrolü) — başarısız silme artık
  başarı gibi raporlanamaz.
- Ağır disk/MediaStore işleri `Dispatchers.IO` üzerinde; ana iş parçacığında dosya işlemi yok.
- Ayrıntılı kök neden analizi: `KOK_NEDEN_RAPORU_INDIRMELER_SILME_2026-10-21.md`

---

## 🇬🇧 English

### 🗑️ Downloads — Root-cause fix for subtitle & image deletion
- **Root cause:** images and subtitles are written to shared storage through `MediaStore`
  on Android 10+. For such files `File.delete()` silently returns `false`, so the delete
  buttons on the Downloads screen removed nothing and cards reappeared after a refresh.
  Videos were unaffected because they use a different API (`AnimeDownloadManager`).
- **New `MediaStoreFileDeleter`:** rows are located via `DISPLAY_NAME` + `RELATIVE_PATH` and
  removed with `ContentResolver.delete` (the unreliable `DATA`-based lookup for Android 11+
  is gone). Ownership is checked with `OWNER_PACKAGE_NAME`.
- **Image deletion:** single-card and group deletion now share one flow
  (`KitsugiImageDownloadHelper.deleteImageFile`): download index (MediaStore URI / SAF tree)
  → every same-name copy on disk → MediaStore-by-name fallback → **result verification**.
- **Subtitle deletion:** `DownloadStorage.deleteSubtitle` removes every same-name copy
  (`Kitsugi/Subtitles`, `Kitsugi/Video`, per-episode sub folders, `cacheDir/subtitles`).
- **System consent:** rows created by a previous install trigger the Android 11+
  `MediaStore.createDeleteRequest` dialog instead of an unactionable message.
- **Feedback:** failed deletions now explain why, and the list always resyncs with disk.
- **Group delete** asks for confirmation before removing a whole title's images.
- **Gallery** inside Downloads supports the same consent flow.
