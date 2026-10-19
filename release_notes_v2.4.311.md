# Kitsugi Beta v2.4.311

## 🇹🇷 Türkçe

### 🎬 Aniyomi Oynatıcı Paritesi & Gelişmiş MPV / Media3 Entegrasyonu
- **Ses Odağı ve Kulaklık Çıkarılınca Duraklatma (`MpvAudioFocusController`):** Sistem ses odağı (audio focus) değişimleri dinlenir; kulaklık veya Bluetooth bağlantısı kesildiğinde (`BECOMING_NOISY`) oynatma otomatik olarak güvenle duraklatılır.
- **Güvenli TLS & CA Sertifika Deposu (`MpvTrustStore`):** Android CA sertifika zinciri güvenli şekilde libmpv'ye aktarılır, geçersiz/boş CA kaynaklı çökmeler engellenir.
- **Bölüm (Chapter) ve Seek Önizlemesi (`PlayerChapter`, `PreviewGenerator`):** Video dosyaları içerisindeki bölümler (chapters) algılanarak seekbar üzerinde görselleştirilir; arama çubuğunda thumbnail / seek önizleme desteği getirildi.
- **Çift Altyazı & İkinci Altyazı Seçimi:** Birincil ve ikincil altyazı bağımsız olarak seçilebilir; ikinci altyazının birinci gibi seçili görünmesi hatası giderildi.
- **Geçici Oynatma Hızı Koruması:** Geçici olarak artırılan oynatma hızının ayar güncellemeleri veya ekran döndürme esnasında sıfırlanması önlendi.
- **Asenkron Ekran Görüntüsü Hattı:** Ekran görüntüsü alma, kodlama, diske kaydetme ve paylaşma işlemleri ana iş parçacığından arka plana (`Dispatchers.IO`) taşınarak arayüz takılmaları giderildi.
- **PiP & MediaSession Köprüsü:** Picture-in-Picture modunda ve sistem MediaSession kontrollerinde durum senkronizasyonu mükemmelleştirildi; MPV Lua betik köprüsü (`aniyomi.lua`) dahil edildi.

### 🧪 Birim Testleri
- `MpvPlaybackPolicyTest`, `MpvPlayerEngineTest` ve `MpvTlsPolicyTest` testleri eklendi/güncellendi.

---

## 🇬🇧 English

### 🎬 Aniyomi Player Parity & Advanced MPV / Media3 Integration
- **Audio Focus & Headphone Disconnect Pause (`MpvAudioFocusController`):** Full Android audio focus management. Playback automatically pauses safely when headphones or Bluetooth audio disconnects (`ACTION_AUDIO_BECOMING_NOISY`).
- **Resilient TLS Trust Store (`MpvTrustStore`):** Safely injects system CA certificates into libmpv without ghost file paths or handshake drops.
- **Chapters & Seek Preview (`PlayerChapter`, `PreviewGenerator`):** Video embedded chapters are parsed and rendered onto the timeline; added seek thumbnail preview rendering.
- **Dual Subtitles & Secondary Track Selection:** Independent secondary subtitle selection with resolved active-state display parity.
- **Temporary Playback Speed Retention:** Fixed temporary playback speed being reset upon setting updates or screen rotation.
- **Asynchronous Screenshot Pipeline:** Screenshot capture, encoding, disk storage, and sharing operations migrated to background I/O coroutines, eliminating UI thread hitching.
- **PiP & MediaSession Sync:** Enhanced Picture-in-Picture lifecycle, system MediaSession notification metadata, and MPV Lua script bridge (`aniyomi.lua`).

### 🧪 Unit Tests
- Added and updated `MpvPlaybackPolicyTest`, `MpvPlayerEngineTest`, and `MpvTlsPolicyTest`.
