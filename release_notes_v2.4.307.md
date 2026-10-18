# Kitsugi Beta v2.4.307

## 🇹🇷 Türkçe

### 🔒 Asıl Kök Neden (KN-D) — MPV TLS & HTTPS Akış Düzeltmesi
- **MPV Motorunda Hayalet CA Dosyası Kaldırıldı (`KitsugiMpvSurfaceView`):** Uygulamanın varsayılan oynatıcısı olan MPV motoru (`playerPreference = "MPV"`), `initOptions()` içinde `tls-verify = yes` ve `tls-ca-file = <filesDir>/cacert.pem` ayarlıyordu. Ancak `cacert.pem` dosyası hiçbir zaman diske yazılmadığından veya paketlenmediğinden, libmpv'nin CA sertifika deposu boş kalıyordu. Sonuç olarak Türkçe yayın sunucularının tamamı HTTPS olduğu için tüm bağlantılar TLS el sıkışmasında başarısız olup 9001 ("MPV dosyayı açamadı") hatası veriyordu.
- **Media3 İle Tutarlı TLS Politikası:** Media3 motorunun Türkçe yayın CDN'leri için kullandığı esnek SSL politikası (`ignoreSSL = true`) ile uyumlu hale getirildi: `tls-verify = no` yapıldı ve var olmayan `cacert.pem` referansı kaldırıldı.
- **Saf TLS Politika Fonksiyonları & Koruma:** MPV TLS mantığı `mpvTlsOptions` ve `applyMpvTlsOptions` saf yardımcılarına taşındı. Doğrulama yalnızca diskte gerçek ve geçerli bir CA dosyası bulunursa açılacak şekilde mimarilendirildi.

### 🔌 Eklenti Senkronizasyonu (HDFilmCehennemi v55, FilmMakinesi v65)
- **Kitsugi-Plugins Güncellemeleri:** Eklenti tarafındaki `linkTypeFor()` kaynaklı `INFER_TYPE!!` NullPointerException hatası ve meşru stream adreslerini eleyen dar filtreler giderildi.
- **`plugins.json` Manifest Senkronizasyonu:** `app/src/main/assets/plugins/plugins.json` içinde FilmMakinesi v65 (SHA-256: `1dc3aafa...`), HDFilmCehennemi v55 (SHA-256: `4f4876b8...`) güncel hash ve boyutlarıyla güncellendi.

### 🧪 Birim Testleri
- `MpvTlsPolicyTest`: CA yokluğunda `tls-verify=no` davranışı, eksik dosya koruması, boş/whitespace yol kontrolü ve seçenek izolasyonu 11 birim test ile kilitlendi.

---

## 🇬🇧 English

### 🔒 Primary Root Cause (KN-D) — MPV TLS & HTTPS Playback Fix
- **Eliminated Ghost CA File in MPV Engine (`KitsugiMpvSurfaceView`):** The app's default player engine, libmpv (`playerPreference = "MPV"`), previously configured `tls-verify = yes` alongside `tls-ca-file = <filesDir>/cacert.pem`. Because `cacert.pem` was never generated or packaged on Android, mpv's certificate store remained empty, causing all HTTPS TLS handshakes to fail against Turkish CDN hosts (resulting in error 9001 "MPV could not open file").
- **Parity with Media3 SSL Policy:** Aligned MPV with Media3's SSL handling for regional CDN hosts (`ignoreSSL = true`), setting `tls-verify = no` and removing the dangling CA file path.
- **Pure Function TLS Policy Extraction:** Extracted MPV TLS configuration into `mpvTlsOptions` and `applyMpvTlsOptions` pure functions, ensuring certificate verification is only enabled when a valid, accessible CA bundle actually exists on disk.

### 🔌 Addon Sync (HDFilmCehennemi v55, FilmMakinesi v65)
- **Kitsugi-Plugins Upstream Fixes:** Fixed the silent `INFER_TYPE!!` NullPointerException in `linkTypeFor()` and broadened stream pattern filters in `Kitsugi-Plugins`.
- **`plugins.json` Manifest Alignment:** Updated bundled `plugins.json` manifests to FilmMakinesi v65 (SHA-256: `1dc3aafa...`) and HDFilmCehennemi v55 (SHA-256: `4f4876b8...`).

### 🧪 Unit Tests
- `MpvTlsPolicyTest`: Verified 11 test cases covering CA-absent defaults, invalid/blank path handling, and strict option isolation.
