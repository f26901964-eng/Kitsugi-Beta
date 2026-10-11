# PLAN/TASK — Anında Dil Değişimi + Ayarlarda Tüm Hata Kodları (TR/EN) + Hesap Yedekleme Anahtarları

Tarih: 2026-10-11
Dal: `arena/4ef8f130-kitsugi-beta`
Commit: `ac445c9`
İlgili ekran görüntüleri: Cross-Sync "Error Code Reference" dialogu, Ayarlar "Platform Error Codes" ekranı, "Görünüm & Tercihler > Uygulama Dili", "Kitsugi Hesabı" ekranı.

---

## 1) Kullanıcı Talepleri

1. **Hata kodları:** Ayarlar kısmındaki hata kodu alanında yalnızca Manga/CloudStream kodları vardı; AniList/MyAnimeList (ve diğer liste kaynaklarına) özgü kodlar da aynı alanda bulunsun, İngilizce + Türkçe şekilde.
2. **Dil değişimi:** "Uygulama Dili" değiştirildiğinde uygulama kapatılıp açılmadan dil değişmiyordu; anında uygulanmalı.
3. **Hesap deneyimi:** Hesap oluşturup/girince gerekli her şey otomatik aktif olsun; yedekleme gibi özellikler elle aç/kapa yapılabilsin; ekran kullanıcı dostu ve kolay olsun.

---

## 2) Kök Nedenler

### 2.1 Dil anında değişmiyor
- `AndroidManifest.xml` içinde `MainActivity` için `android:configChanges` listesinde `locale` (ve `layoutDirection`) bildirilmişti.
- Bu bildirim, dil değişiminde Activity'nin **yeniden kurulmasını engeller**; sistem yalnızca `onConfigurationChanged` verir.
- `MainActivity.attachBaseContext`, `LocaleCache.localeTag` ile `createConfigurationContext(...)` sarıcı bir bağlam ürettiği için çalışma anındaki yapılandırma güncellemesi bu bağlama işlemez → eski dil, ancak süreç yeniden başlatılınca (uygulama kapat-aç) yenilenir.
- Ek risk: bazı OEM/çerçeve davranışlarında `AppCompatDelegate.setApplicationLocales()` sonrası yeniden kurma gerçekleşmeyebilir.

### 2.2 Ayarlardaki hata kodu ekranı eksik
- `PlatformErrorCatalogScreen` yalnızca `data.diagnostics.PlatformErrorCode` (Manga/CloudStream, KTS-SRC-9xxx / KTS-CSP-9xxx) katalogunu basıyordu.
- Liste kaynakları kataloğu (`data.platform.PlatformErrorCode`, KTS-COM/ANL/MAL/KIT/SHK/BGM/TMD/SMK) yalnızca Cross-Sync dialogunda vardı.

### 2.3 Hesap ekranı
- Kasa açılamadığında gösterilen genel hata metni Kotlin içinde **sabit Türkçe** idi (EN dilinde de Türkçe görünür).
- Kasa açılamazsa şifre temizleniyor, "Şifreyi doğrula ve kasayı aç" için kullanıcı şifreyi yeniden yazmak zorunda kalıyordu.
- E-posta doğrulama akışı `message?.startsWith("Doğrulama e-postası")` gibi dil bağımlı bir string karşılaştırmasıyla izleniyordu (EN'de kırılır).
- Yedek/eşitleme özelliklerinin elle aç/kapa anahtarı yoktu.

---

## 3) Yapılan Değişiklikler

### 3.1 `app/src/main/AndroidManifest.xml`
- `MainActivity` `configChanges` listesinden `layoutDirection` ve `locale` kaldırıldı.
- Etki: dil değişiminde Activity framework tarafından yeniden kurulur; `attachBaseContext` yeni `LocaleCache` değeriyle çalışır → arayüz anında yeni dilde.

### 3.2 `KitsugiApplication.kt` (`LocaleCache`)
- API 33+ yoluna **güvenlik ağı** eklendi: `scheduleLocaleSafetyRecreate(context, prefsTag)`.
  - `setApplicationLocales` sonrası 700 ms beklenir; Activity hâlâ yaşıyorsa ve uygulanan `configuration.locales[0].language` beklenen dilden farklıysa bir kez `recreate()` edilir.
  - Framework zaten yeniden kurduysa eski örnek `isDestroyed` olur → çift recreate olmaz.
- `onCreate` içinde `KitsugiAccountRepository.startSettingsObserver(this)` başlatılır.

### 3.3 `ui/screens/settings/PlatformErrorCatalogScreen.kt` (Ayarlar > Hata Kodları)
- Ekran iki kataloğu birleştirir:
  - `data.diagnostics.PlatformErrorCode` (Manga, CloudStream)
  - `data.platform.PlatformErrorCode` (General + AniList + MyAnimeList + Kitsu + Shikimori + Bangumi + TMDB + Simkl)
- Filtre çipleri yatay kaydırmalı: Tümü / Manga / CloudStream / General / AniList / … / Simkl.
- Arama her iki katalogda kod, başlık, neden/çözüm ve platform adı üzerinden çalışır; sayaç toplamı gösterir.
- Liste kaynakları kartı (`ListSourceErrorBlock`) kod çipi + platform + önem etiketi + başlık + "Neden olur?" + "Ne yapmalı?" + yeniden denenebilirlik satırını **dil dosyasından** (`pf_err_*`) çizer.

### 3.4 Hesap: elle aç/kapa anahtarları (girişte hepsi OTOMATİK açık)
- `AppSettings` + `SettingsDataStore`:
  - `accountBackupEnabled` (varsayılan `true`)
  - `accountSearchSyncEnabled` (varsayılan `true`)
  - `accountNotificationSyncEnabled` (varsayılan `true`)
  - Anahtarlar `cloudKeys` beyaz listesinde **değildir** → cihaz yereli, buluta gönderilmez.
- `LinkedAccountVault`:
  - `autoBackupEnabled` + `setAutoBackupEnabled(context, enabled)`; kapanışta WorkManager periyodik/bekleyen işler iptal, açılışta kuyruklanır.
  - `scheduleBackup()`, `onSignedIn()` ve `startAutoBackup()` bu anahtara saygı duyar; elle "Şimdi eşitle" her zaman çalışır.
- `KitsugiAccountRepository`:
  - `searchSyncEnabled` / `notificationSyncEnabled` volatile bayraklar + `startSettingsObserver`.
  - `push/pullAndMergeSearchHistory`, `push/pullAndMergeNotificationArchive`, `onSearchHistoryChanged` bayraklarla kapılır.
- `KitsugiAccountContent` (hesap ekranı):
  - "Yedekleme ve eşitleme" bölümü + üç Switch satırı (`AccountSwitchRow`).
  - Kasa kilitli kaldıysa şifre bellekte tutulur → "Şifreyi doğrula ve kasayı aç" yeniden yazmadan dener.
  - E-posta doğrulama takibi `awaitingConfirmation` flag'i ile (dil bağımsız).

### 3.5 Yerelleştirme
- Sabit Türkçe kasa hatası `R.string.vault_operation_incomplete_generic` kaynağına taşındı (TR + EN).
- Yeni dizgiler (TR + EN, 8 adet): `vault_operation_incomplete_generic`, `account_backup_sync_section`, `account_toggle_backup_title/_desc`, `account_toggle_search_title/_desc`, `account_toggle_notif_title/_desc`.

---

## 4) Doğrulama

- `python3 scripts/check_localization_resource_parity.py` → **6417 dizgi TR/EN senkron** (placeholder eşliği dahil).
- `values/strings.xml`, `values-en/strings.xml`, `AndroidManifest.xml` XML well-formed kontrolü → OK.
- Düzenlenen Kotlin dosyalarında tüm `R.string.*` referanslarının iki dil dosyasında varlığı → eksik yok.
- Kotlin parantez/dizgi denge kontrolü (7 dosya) → dengeli.
- **Yapılamayan:** sandbox'ta JDK/Android SDK bulunmadığı için Gradle derlemesi ve cihaz testi çalıştırılamadı; APK düzeyinde doğrulama cihazda yapılacak.

## 5) Risk / Notlar

- `configChanges`'ten `locale` kaldırılması, sistem dili değişimlerinde de Activity recreate eder (standart Android davranışı).
- Anahtarlar cihaz yereli olduğu için bir cihazda kapatmak diğer cihazları etkilemez; bulut yedeğin içeriği değişmez (yalnızca yeni yedek upload'ı durur).
- Güvenlik ağı recreate'i yalnızca dil uyuşmazlığında ve bir kez tetiklenir.
