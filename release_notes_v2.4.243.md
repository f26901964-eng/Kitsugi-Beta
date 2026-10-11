# Kitsugi v2.4.243-beta Sürüm Notları / Release Notes

---

## 🇹🇷 Türkçe (TR)

### 🌟 Öne Çıkan Yenilikler ve Hata Düzeltmeleri

#### 1. 🖼️ Shikimori Vitrin Logoları & Doğrudan Görsel Eşleştirme
- **Vitrin Logo Desteği (`HeroLogoPolicy.kt`):** Genel logo ayarı kapalı olsa dahi Shikimori kaynaklı anime içerikleri için vitrin logosu araması etkinleştirildi. Logo bulunamadığında mevcut başlık metni şık bir yedek olarak kalır.
- **MAL Kimliği Eşlemesi (`KitsugiShikimoriClient.kt`):** Arama ve keşfet sonuçlarındaki `myanimelist_id` bilgisi doğrudan `realMalId` alanına aktarıldı. Bu sayede gereksiz aracı sorgular azaltılarak logo eşleştirmesi çok daha hızlı ve doğrudan çalışır.

#### 2. 🛡️ Kullanıcı Dostu Profil Hata Ekranı & Hata Sınıflandırması
- **Anlaşılır Hata Kartı (`ProfileErrorFormatter.kt` / `ProfileSharedComponents.kt`):** Ham hata mesajları ve sunucu JSON gövdeleri yerine; HTTP 429 (hız sınırı/rate-limit), oturum, yetki, 404 bulunamadı, zaman aşımı, ağ ve sunucu problemleri için net Türkçe ve İngilizce açıklamalar gösteriliyor.
- **HTTP Durum Rozeti:** Teknik hata detayları kullanıcıyı boğmadan, varsa HTTP kodu küçük ve şık bir durum rozeti olarak arayüzde yer alır.

#### 3. 🧪 Doğrulama ve Testler
- Yeni `ProfileErrorFormatterTest` ve güncellenen `HeroSelectionTest` birim testleri başarıyla doğrulandı.
- 5.972 dize anahtarı ve 4 dize dizisi ile %100 TR/EN yerelleştirme uyumu sağlandı.

---

## 🇬🇧 English (EN)

### 🌟 Key Highlights & Bug Fixes

#### 1. 🖼️ Shikimori Hero Logos & Direct Visual Matching
- **Hero Logo Policy (`HeroLogoPolicy.kt`):** Enabled anime hero logo lookups for Shikimori media even when the global logo toggle is disabled. Gracefully falls back to title text if no logo asset exists.
- **Direct MAL ID Mapping (`KitsugiShikimoriClient.kt`):** Mapped `myanimelist_id` to `realMalId` across Shikimori search and explore results, streamlining logo matching without unnecessary intermediary queries.

#### 2. 🛡️ User-Friendly Profile Error UI & Classification
- **Clean Error Card (`ProfileErrorFormatter.kt` / `ProfileSharedComponents.kt`):** Replaced raw provider JSON/trace dumps with friendly, localized explanations for HTTP 429 (rate limiting), auth expired, 404 not found, timeouts, network interruptions, and server outages.
- **HTTP Status Badge:** Retains the HTTP status code as a clean, minimal badge alongside the localized description.

#### 3. 🧪 Testing & Validation
- Verified with `ProfileErrorFormatterTest` and updated `HeroSelectionTest` suites.
- 100% localization parity across 5,972 strings and 4 arrays in English and Turkish.
