# Kitsugi Beta v2.4.329

## 🇹🇷 Türkçe

### 🎬 Detay Videoları YouTube HLS Düzeltmesi & Çift Dokunma Kısayolu
- **YouTube HLS Akış Algılama (`PlayerMediaSourceFactory.kt`):**
  - YouTube'un `.m3u8` uzantısı içermeyen `manifest.googlevideo.com/.../hls_playlist/...` biçimli HLS akışları otomatik olarak HLS formatı olarak tanınır.
  - Media3'ün akışı yanlışlıkla progressive gibi açıp `00:00 / 00:00` süresinde takılı kalma sorunu kökten çözüldü.
- **HLS Önceliği & Geri Dönüş Hattı (`InAppYouTubeExtractor.kt`):**
  - YouTube çıkarımında geçerli bir HLS akışı mevcutsa video/ses akışı yerine HLS öncelenir; HLS bulunamazsa adaptive/progressive akışlar yedek olarak korunur.
- **Küçük Oynatıcı Çift Dokunma Kısayolu (`KitsugiDetailPlayerComponents.kt`):**
  - Detay sayfasındaki küçük oynatıcıya ekranın sağına/soluna çift dokunarak 10 saniye ileri/geri sarma özelliği ve görsel animasyon geri bildirimi eklendi.
- **Regresyon Testleri (`PlayerHlsDetectionTest.kt`):**
  - YouTube HLS ve DASH manifest ayrıştırması için birim testleri eklendi ve başarıyla doğrulandı.

### ☁️ Kitsugi Hesap & Supabase Entegrasyon Tamamlaması
- **Güvenli E-Posta Doğrulama Hattı (`AccountConfirmationLink.kt` & `MainActivity.kt`):**
  - Supabase tek kullanımlık e-posta onay kodları güvenli biçimde ayrıştırılır; Activity yaşam döngüsü yenilenmelerinde kodun mükerrer çalıştırılması engellenir.
  - Hata mesajları hassas bağlantı ve belirteç bilgilerini gizleyecek şekilde `AccountErrorFormatter` ile filtrelenir.
- **Hesap & Senkronizasyon Altyapısı:**
  - Form doğrulamaları (`AccountFormValidation`), oturum kilitleme (`AccountSessionLock`), bağlı hesap kasası (`LinkedAccountVault`) ve arka plan yedekleme işçisi (`VaultBackupWorker`) tamamlandı.
  - Bildirim arşivi bulut yedeklemesi, yerel depolama yazma işlemi bittikten hemen sonra tetiklenecek şekilde `NotificationArchiveStore` yapısına entegre edildi.
  - Hesap ayrıcalık sertleştirme migrasyonu (`20261021_account_privilege_hardening.sql`) eklendi.

---

## 🇬🇧 English

### 🎬 YouTube HLS Stream Detection & Player Double-Tap Seek
- **YouTube HLS URL Recognition (`PlayerMediaSourceFactory.kt`):**
  - Correctly detects YouTube's extensionless HLS manifests (`manifest.googlevideo.com/.../hls_playlist/...`) as HLS MIME type.
  - Fixes the `00:00 / 00:00` stalled playback bug caused by Media3 misidentifying HLS streams as progressive media.
- **HLS Prioritization (`InAppYouTubeExtractor.kt`):**
  - Prioritizes HLS playback when available, while gracefully falling back to adaptive/progressive streams if unavailable.
- **Mini-Player Double-Tap Seeking (`KitsugiDetailPlayerComponents.kt`):**
  - Double-tap on left/right halves of the detail player to seek 10 seconds backward or forward with ripple animation feedback.
- **HLS Detection Regression Tests (`PlayerHlsDetectionTest.kt`):**
  - Unit tests verifying YouTube HLS manifest parsing and DASH exclusion.

### ☁️ Kitsugi Account & Supabase Integration
- **Hardened Email Confirmation (`AccountConfirmationLink.kt` & `MainActivity.kt`):**
  - Single-use confirmation codes are safely parsed and consumed once across Android Activity recreations.
  - Error messages are sanitized through `AccountErrorFormatter` to prevent leaking auth parameters in logs or toasts.
- **Account & Vault Synchronization:**
  - Added robust form validation, session locking, encrypted linked account vault, and background sync worker (`VaultBackupWorker`).
  - Streamlined notification archive cloud sync via `NotificationArchiveStore`.
  - Added account privilege hardening migration (`20261021_account_privilege_hardening.sql`).
