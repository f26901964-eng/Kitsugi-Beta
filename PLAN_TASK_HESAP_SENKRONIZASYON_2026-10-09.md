# Kitsugi Hesabı — Veri Senkronizasyonu ve Bağlı Hesap Yedekleme (2026-10-09)

Branch: `arena/a52a566e-kitsugi-beta`

## 1. Hedef
- Tek bir Kitsugi hesabıyla giriş yapınca:
  - Arama geçmişi cihazlar arasında eşitlensin.
  - Bağlı servis hesapları (AniList, MyAnimeList, Kitsu, Shikimori, Bangumi, Simkl) otomatik geri gelsin.
- Hiçbir Kitsugi şifresi veya düz metin token buluta gitmesin.

## 2. Teknik tasarım
- **Backend:** Supabase (ücretsiz katman). Proje: `kitsugi` (Frankfurt, eu-central-1).
- **Tablo:** `public.user_data (user_id, key, value jsonb, updated_at, deleted)` — RLS ile satır sahibi erişimi.
  - `search_history` → arama geçmişi (düz JSON, sunucuda okunabilir).
  - `vault_meta` → kasa anahtarının Kitsugi şifresiyle sarılmış hali (PBKDF2-SHA256, 210.000 iterasyon).
  - `linked_accounts_vault` → bağlı servis token'ları, kasa anahtarıyla AES-256-GCM şifreli.
- **Kasa modeli:**
  1. İlk giriş: rastgele 256-bit kasa anahtarı üretilir → şifreden türetilen anahtarla sarılır → `vault_meta`'ya yazılır.
  2. Token'lar kasa anahtarıyla şifrelenip `linked_accounts_vault`'a yazılır.
  3. Yeni cihazda giriş: şifreden anahtar türetilir → kasa açılır → token'lar geri yüklenir.
  4. Şifre değişirse yalnızca kasa anahtarı yeniden sarılır (`rewrapWithNewPassword`); veri yeniden şifrelenmez.
- **Otomatik yedek:** `MyWebViewPrefs` içindeki izinli anahtarlardan biri değişince 3 sn gecikmeyle yedek alınır (refresh token rotasyonu dahil).
- **Yedeklenmeyen:** `*_code_verifier`, `*_pending_redirect_uri` (geçici PKCE/OAuth alanları). Kitsugi şifresi.

## 3. Değişen dosyalar
| Dosya | Açıklama |
|---|---|
| `gradle/libs.versions.toml` | Supabase 3.0.3 → 2.6.1 (Kotlin 2.0.21 / Ktor 2.3.12 ile uyumlu) |
| `app/.../data/account/KitsugiBackendConfig.kt` | Proje URL + publishable anahtar |
| `app/.../data/account/KitsugiAccountClient.kt` | Supabase istemcisi (Auth + Postgrest) |
| `app/.../data/account/KitsugiAccountRepository.kt` | Kayıt, giriş, çıkış, arama geçmişi push/pull |
| `app/.../data/account/LinkedAccountVault.kt` | Token şifreleme, yedek, geri yükleme, otomatik yedek |
| `app/.../data/repository/SearchHistoryRepository.kt` | Ekleme/silme/temizlemede buluta yazma |
| `app/.../ui/screens/account/KitsugiAccountContent.kt` | Giriş/kayıt/profil ekranı |
| `app/.../ui/screens/settings/SettingsScreen.kt` | "Kitsugi Hesabı" menü satırı ve alt sayfa |
| `app/.../KitsugiApplication.kt` | Otomatik yedek dinleyicisinin başlatılması |
| `app/.../utils/KitsugiImageDownloadHelper.kt` | GIF/PNG/WEBP orijinal formatında indirme ve paylaşım |
| `app/.../ui/screens/search/SearchHistorySection.kt` | Kompakt arama geçmişi çipleri |
| `supabase/schema.sql` | `user_data` tablosu ve RLS politikaları |

## 4. Test planı (cihazda)
1. Hesap oluştur / giriş yap → Supabase `user_data` tablosunda `vault_meta` ve `linked_accounts_vault` satırları oluşmalı.
2. Bir servise bağlan (örn. Bangumi) → birkaç saniye sonra `linked_accounts_vault` güncellenmeli.
3. Uygulamayı kaldır/yeniden kur (veya başka cihaz) → aynı hesapla giriş → bağlı servisler görünmeli.
4. Arama yap → yeni cihazda geçmiş görünmeli.
5. Yanlış şifreyle giriş → hesap girişi başarısız olmalı (kasa açılamaz).
6. Çıkış yap → yerel kasa anahtarı silinmeli; token'lar cihazda kalır.

## 5. Bilinen sınırlar / riskler
- Kitsugi şifresi unutulursa yedek çözülemez; servislere yeniden giriş gerekir.
- Şifre değiştirme ekranı henüz yok (`rewrapWithNewPassword` hazır, UI bağlanacak).
- Token'lar yerel olarak hâlâ düz `SharedPreferences`'ta duruyor. Sonraki adım: yerel depolamayı Android Keystore ile şifrelemek.
- Arama geçmişinde silinen kayıtlar, başka cihaz eski veriyle giriş yaparsa geri gelebilir (silme işaretleme sonraki adım).
- Bu ortamda derleme yapılamadı; Android Studio'da derleme ve test gerekiyor.

## 6. Sonraki adımlar
- Eklenti ve ayar senkronizasyonu (`installed_plugins`, `settings` anahtarları).
- Şifre değiştirme ekranı + `rewrapWithNewPassword` bağlantısı.
- Yerel token depolamasının Keystore ile şifrelenmesi.
- Profil sayfasında bağlı hesapların durumu.
