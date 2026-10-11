# Kitsugi v2.4.241-beta Sürüm Notları / Release Notes

---

## 🇹🇷 Türkçe (TR)

### 🌟 Öne Çıkan Yenilikler ve İyileştirmeler

#### 1. 👤 AniList Standartlarında Detaylı MyAnimeList (MAL) Profili
- **Cihaz Üzeri MAL Ayrıştırıcı (`MalWebParser` / `MalWebSource`):** Doğrudan cihaz üzerinden MAL profil, geçmiş ve arkadaş sayfalarını okuyan bağımsız motor. Client ID veya token gerektirmez, IP ban riskine karşı aralıklı istek (rate-limit), akıllı TTL önbelleği ve otomatik bekleme süresi (cooldown) içerir.
- **Veri Akış Önceliği:** Öncelik cihaz üzeri okumada; başarısız olursa miribyou (Vercel), ardından Tenrai devreye girer.
- **Hakkında Sekmesi:** Cinsiyet, doğum günü (yaş hesabı ile), konum, katılım tarihi, son görülme süresi, toplam izleme/okuma gün ve bölüm sayıları, katlanabilir biyografi alanı.
- **Aktivite Sekmesi:** Son güncellemeler (ör. *"12. bölümü izledi • 3 saat önce"*) ve 40 kayda kadar detaylı geçmiş listesi. Kayıtlara dokunulduğunda ilgili anime veya manga sayfası doğrudan açılır.
- **Arkadaşlar & Favoriler:** Profil resimleriyle birlikte arkadaş listesi ve favori anime/manga/karakter listesi.

#### 2. 🔑 MAL Girişi ve TV QR PKCE Güvenlik Düzeltmesi
- **Giriş İstemcisi Ayrımı:** Tarayıcıda temel kimlik doğrulama ("Oturum açın" / Basic Auth) penceresinin açılması engellendi; `malapp://auth` ile kayıtlı Android OAuth istemcisi (`MAL_OAUTH_CLIENT_ID`) bağlandı.
- **TV QR & Doğrudan Giriş PKCE Fix:** Android TV QR giriş kodunda ve doğrudan tarayıcı oturumunda eksik olan MAL PKCE `code_challenge` ve `codeVerifier` oluşturma/saklama mekanizması düzeltildi.

#### 3. ⏱️ Haftalık Yayın Takvimi ("Bu Hafta Yayında") Yenilikleri
- **Dinamik Ekran Uyumu:** Ekran genişliğine göre sütun sayısını ayarlayan `rememberKitsugiGridColumns()` yapısı.
- **Yatay Liste Kartları:** "Tümünü Gör" düzenine uyumlu afiş ve detayları öne çıkaran `AiringEntryWideCard`.
- **Takvim İskelet Animasyonu (`AiringCalendarSkeleton`):** Görünüm türüne (ızgara/liste) göre şekil alan yumuşak yükleme yer tutucuları.
- **İlk Açılış ve Yeniden Deneme:** İlk veriler gelene kadar yaşanan anlık boş ekran parlaması (`hasLoaded`) giderildi; hata durumunda yeniden deneme seçili veri kaynağını koruyarak çalışır.

#### 4. 🖼️ Detay Sayfaları İçin Ortak Yükleme İskeletleri ve Kayan Başlık
- **Ortak İskelet (`DetailPageLoadingSkeleton`):** Tüm detay sayfalarında (Stüdyo, Karakter, Personel, Medya Girişi ve API Sonuç) tam ekran dönen çark yerine sayfa düzenini birebir taklit eden shimmer iskeleti.
- **Stüdyo Kayan Üst Çubuk:** Filtre ve görünüm butonları ekrandan çıktıktan sonra 260ms/220ms yumuşak solma animasyonuyla devreye girer; üst çubuğa paylaş butonu eklendi.

#### 5. 🌐 Kapsamlı Yerelleştirme (i18n) & Arama Motoru Entegrasyonu
- Arama filtreleri, motorlar, kapsamlar ve arama diyalogları (`SearchLocalization.kt`) dahil olmak üzere Türkçe ve İngilizce dil dosyalarında 5.941 dize anahtarı %100 eşleşmeyle korundu.

#### 6. ⚡ Eklenti Arama & Cloudflare / DDoS-Guard Motoru (v2.4.240)
- Eklenti isim aramalarında sıfır sonuç ve zaman aşımı sorunları giderildi. 120 sn CF bütçesi, DDOS-Guard negatif önbellek ve CF Gate mekanizması korunmuştur.

---

## 🇬🇧 English (EN)

### 🌟 Key Highlights & Improvements

#### 1. 👤 Detailed MyAnimeList (MAL) Profile
- **On-Device MAL HTML Parser (`MalWebParser` / `MalWebSource`):** Self-contained on-device scraping for MAL profile, history, and friends pages. Requires no client ID or token, features rate limiting, TTL caching, cooldowns, and automatic failover.
- **Data Source Hierarchy:** On-device scraping takes priority; falls back to miribyou (Vercel) and then Tenrai.
- **About Tab:** Gender, birthday (with calculated age), location, join date, last online, total watch/read time and episodes/chapters, collapsible about/bio card.
- **Activity Stream:** Recent updates (e.g., *"Watched ep 12 • 3 hours ago"*) and up to 40 history records. Tapping an activity directly navigates to the media entry.
- **Friends & Favorites:** Friends list with profile avatars, favorites for anime, manga, and characters loaded directly.

#### 2. 🔑 MAL Authentication & TV QR PKCE Security Fix
- **Dedicated Android OAuth Client:** Fixed browser basic authentication popup by routing OAuth logins through the registered Android client (`MAL_OAUTH_CLIENT_ID` with `malapp://auth`).
- **TV QR PKCE Fix:** Resolved missing PKCE parameters (`code_challenge` / `codeVerifier`) in Android TV QR codes and direct device login dialogs.

#### 3. ⏱️ Weekly Airing Calendar ("Airing This Week")
- **Adaptive Screen Layout:** Dynamic column layout with `rememberKitsugiGridColumns()`.
- **Wide List Cards:** Redesigned `AiringEntryWideCard` with prominent posters and airtime countdowns.
- **Calendar Skeleton Placeholder (`AiringCalendarSkeleton`):** Shimmer placeholders dynamically matching list or grid view modes.
- **Flicker-Free Initial Load & Retry:** Eliminated initial empty screen flash (`hasLoaded`) and preserved selected data source across retries.

#### 4. 🖼️ Shared Detail Skeletons & Floating Sticky Headers
- **Universal Detail Skeleton (`DetailPageLoadingSkeleton`):** Replaced full-screen progress spinners across Studio, Character, Staff, Media, and API detail pages with layout-accurate shimmering skeletons.
- **Studio Floating Header:** Smooth 260ms/220ms fade trigger once controls exit viewport, plus direct share action.

#### 5. 🌐 Comprehensive Localization (i18n) & Search Parity
- Full parity across 5,941 string keys in `values/strings.xml` and `values-en/strings.xml`, covering search engines, filters, chips, dialogs (`SearchLocalization.kt`), and Explore.

#### 6. ⚡ Plugin Search & Cloudflare / DDoS-Guard Resilience (v2.4.240)
- Full CF-aware timeouts (120s), DDoS-Guard host locking, negative caching, and pre-search CF Gate validations.
