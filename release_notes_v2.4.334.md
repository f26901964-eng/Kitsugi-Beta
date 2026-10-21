# Kitsugi Beta v2.4.334

## 🇹🇷 Türkçe

### 🚀 Video Oynatıcı Kasma/Donma, Ses-Var-Görüntü-Yok & AnimeThemes Onarımı (PR #28)
- **Saniyede 60 Kare Recomposition Boğulması Çözüldü (4 Hz Sınırlandırma):**
  - MPV'nin `time-pos` bildirimleri her karede iletildiği için küçük oynatıcı saniyede ~60 kez Compose state'ine yazıyordu; bu durum detay sayfasında aşırı kasmaya yol açıyordu. Bildirimler 4 Hz'e çekilerek akıcılık sağlandı (seek, yükleme ve süre güncellemeleri anında çalışmaya devam eder).
- **Çift Video Çıkışı (VO) Yeniden Başlatma Hatası Giderildi:**
  - `applyInitOptions` hem `createVideoView` hem `prepare()` çağrılarında çalıştığı için `vo` her videoda iki kez yazılıyor ve libmpv video çıkışı yeniden başlatılarak donma ve ilk saniyelerde siyah kareye yol açıyordu. Video çıkışı motor ömründe yalnızca bir kez kurulacak şekilde izole edildi.
- **Sesi Var Görüntüsü Yok Hatası İçin `hasVideoTrack` Denetimi:**
  - Video codec'i çözülemediğinde oynatıcılar sessizce ses çalıp siyah ekran bırakabiliyordu. `PlayerEngine.hasVideoTrack` eklendi; 6 saniye içinde video görüntüsü bağlanamazsa küçük oynatıcı otomatik olarak diğer motora (MPV ↔ Media3) geçer; ikisi de başarısız olursa "Yeniden Dene / Kaynağı Aç" kartı gösterilir.
- **Media3 YouTube Sayacı & Yükleme Titremesi:**
  - Media3 YouTube oynatımı için 4 Hz konum bildirimi eklendi (00:00 donması giderildi) ve ilk kare geldikten sonra yükleme halkası titreşimi kaldırıldı.
- **AnimeThemes Zengin Video Seçimi:**
  - Yalnızca ilk kayda bakma hatası düzeltildi; tüm kayıtlar taranarak temiz > MP4/H.264 > creditsiz (nc) > WEB > 1080p öncelik sıralamasıyla en uyumlu video seçilir.

### ⚙️ Oynatıcı Gelişmiş Ayarlar Menüsü Yenilendi
- **7 Başlıklı Modern Ayar Grubu:**
  - Tek parça dev gri form yerine, uygulamanın genel teması ve vurgu renkleriyle uyumlu 7 kategorili modern kart tasarımı.
- **Açılır Düzenleme Pencereleri & Dokunulabilir Satırlar:**
  - Sürekli açık metin kutuları yerine dokunulduğunda açılan şık diyaloglar, mevcut seçimi gösteren ayar satırları ve uzun metinlere alan bırakan tam dokunulabilir anahtar (switch) satırları. 32 ayarın kayıt entegrasyonu korundu.

### 🗑️ İndirilenler: Scoped Storage Resim ve Altyazı Silme Onarımı
- **Yeni `MediaStoreFileDeleter` Katmanı:**
  - Android 10+ Scoped Storage'da `File.delete()` çağrısının sessizce başarısız olması engellendi. Dosyalar `DISPLAY_NAME` + `RELATIVE_PATH` ile bulunup `ContentResolver.delete` ile silinir.
- **Sahiplik & Sistem Onayı:**
  - Yeniden kurulumda sahipsiz kalan dosyalar Android 11+ `MediaStore.createDeleteRequest` onay penceresiyle silinir.
- **Grup Silme Onayı & Bildirim:**
  - Yanlışlıkla toplu silmeleri önlemek için onay penceresi eklendi; silme hataları toast ile gösterilip liste diskle senkronize yenilenir.

---

## 🇬🇧 English

### 🚀 Video Player Stutter Fix, Audio-Only Recovery & AnimeThemes Selection (PR #28)
- **Eliminated 60fps Recomposition Storm (4 Hz Throttle):**
  - libmpv `time-pos` notifications throttled to 4 Hz, eliminating up to ~60 Compose state writes per second that previously caused severe UI stuttering during scrolling.
- **Single-Pass Video Output Configuration:**
  - `vo` option is now initialized only once per engine lifecycle instead of twice during view creation and preparation, resolving black-frame hitching.
- **Audio-Only No-Video Track Recovery:**
  - Added `PlayerEngine.hasVideoTrack` checks across Media3 and MPV. If no video track is established within 6s, failover to the alternative engine is triggered.
- **Media3 Progress & AnimeThemes Prioritization:**
  - Added 4 Hz progress ticker for Media3 inline playback and enhanced AnimeThemes candidate sorting (Clean > MP4/H.264 > NC > WEB > up to 1080p).

### ⚙️ Player Advanced Settings Redesign
- **7-Category Structured Architecture:**
  - Replaced legacy monochromatic form with a themed, card-based interface adhering to the app's design language.
- **Modal Editing & Polished Controls:**
  - Replaced persistent text fields with responsive modal editors and high-touch surface switch rows for all 32 settings.

### 🗑️ Downloads: Scoped Storage Deletion Fix
- **New `MediaStoreFileDeleter` Engine:**
  - Resolves silent `File.delete()` failures on Android 10+ shared storage via `DISPLAY_NAME` + `RELATIVE_PATH` and `ContentResolver.delete`.
- **System Deletion Confirmation:**
  - Integrates Android 11+ `MediaStore.createDeleteRequest` for orphaned files and adds a bulk deletion confirmation dialog.
