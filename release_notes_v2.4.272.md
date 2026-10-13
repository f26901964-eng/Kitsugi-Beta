# Kitsugi-Beta v2.4.272

## 🇹🇷 Türkçe (v2.4.272)

### ⚡ AniList GraphQL Senkronizasyon Düzeltmesi (Direct Interpolation)
- **Doğrudan Dolar İşareti İnterpolasyonu:** AniList `SaveMediaListEntry` GraphQL mutasyon sorgusu artık metin yerine doğrudan Kotlin `${'$'}` interpolasyonu ile derlenir; kırılgan ardışık `.replace()` zinciri kaldırıldı.
- **Puan Güvenliği:** Puanı bulunmayan kayıtlarda `scoreRaw` değişkeni GraphQL mutasyonuna dahil edilmez. Bu sayede AniList'teki mevcut puanların silinmesi veya API'nin hata vermesi engellendi.
- **Kullanıcı Aitlik Doğrulaması:** AniList liste kayıtlarının (`aniListEntryId`) güncellenmesi veya silinmesi öncesinde, kaydın gerçekten oturum açmış kullanıcıya (`Viewer.id`) ait olduğu ve hedeflenen medyayla eşleştiği doğrulanır.

### 🛡️ Çapraz Senkronizasyon Güvenlik Denetimi & Kimlik Koruma Kalkanı (Sync Safety Audit)
- **Resmî Katalog Doğrulama Kapısı:** Farklı hesaplar arası (cross-account) yazmalarda, hedef platforma kayıt eklenmeden önce resmî MAL kataloğundan başlık, medya türü ve yıl doğrulanır; üçüncü parti tahminler ve belirsiz eşleşmeler engellenir.
- **Bangumi Çapraz Yazma Koruması (Fail-Closed):** Desteklenen API yolunda yetkili MAL↔Bangumi kimlik eşlemesi bulunmadığından, yanlış içerik yazılmasını önlemek amacıyla çapraz eklemeler güvenli tarafta kalarak atlanır; yalnızca aynı hesaptaki doğrulanmış kayıtlar güncellenir.
- **Kimlik Uzayı (Namespace) Ayrımı & Taşma Koruması:** Kitsu, Shikimori ve Bangumi sağlayıcı kimliklerinin birbirine ve MAL ID uzayına karışması engellendi. Kitsu ve Bangumi ID dönüşümlerine 32-bit tamsayı taşma kontrolü eklendi.
- **Doğrulama Birim Testleri:** `CrossSyncEntryFactoryTest`, `KitsuMediaIdentityTest`, `KitsuIdNamespaceTest` ve `CrossSyncIdentityGuardTest` ile senkronizasyon sınırlarının tam gerileme testi sağlandı.

### 📚 Fandom Wiki Faz 2 (Anında Sekme, Başlık Çevirileri & Medya Wiki Sekmesi)
- **Sıfır Zıplama & Anında Sekme:** Karakter detay sayfasında wiki sekmesi sayfa açılır açılmaz shimmer iskeletiyle belirir. Çözümleme tamamlandığında düzen hiç zıplamadan içerik doldurulur.
- **24+ Başlık Çeviri Tablosu:** "Role in the Series", "Appearances in Other Media", "Creation and Design", "Legacy", "About" gibi sık karşılaşılan Fandom başlıkları Türkçeleştirildi.
- **Medya Detayında Wiki Sekmesi:** Anime ve manga detay sayfalarına genel özet, içindekiler ve bilgi kutusu içeren Fandom Wiki sekmesi entegre edildi.

---

## 🇬🇧 English (v2.4.272)

### ⚡ AniList GraphQL Mutation Hardening (Direct Interpolation)
- **Direct Variable Interpolation:** The AniList `SaveMediaListEntry` GraphQL mutation now uses direct Kotlin `${'$'}` variable interpolation instead of error-prone string `.replace()` calls.
- **Score Integrity:** The `scoreRaw` variable and mutation argument are only emitted when a valid score exists, preventing inadvertent score resets or schema rejections on unscored media.
- **Viewer Ownership Verification:** List mutations and deletions confirm that the targeted `aniListEntryId` belongs to the authenticated viewer (`Viewer.id`) and matches the requested media identity before mutating.

### 🛡️ Cross-Account Sync Safety Hardening (Audit Implementation)
- **Authoritative Identity Gate:** Cross-account writes strictly require identity proof verified against the authoritative MAL catalog (type, exact title alias compatibility, and release year) before mutating target accounts.
- **Bangumi Fail-Closed Protection:** Because the supported Bangumi API lacks an authoritative MAL crosswalk, cross-account writes fail closed to protect user libraries from mismatched writes. Same-account native updates remain fully supported.
- **Strict Namespace Isolation & Overflow Safeguards:** Provider-native IDs (Kitsu, Shikimori, Bangumi) are strictly segregated from the 1..99M MAL range. 32-bit integer overflow protection applied to ID offsets.
- **Comprehensive Regression Tests:** New regression test suites (`CrossSyncEntryFactoryTest`, `KitsuMediaIdentityTest`, `KitsuIdNamespaceTest`, `CrossSyncIdentityGuardTest`) guarding identity resolution and boundary mutations.

### 📚 Fandom Wiki Phase 2 (Instant Tab Skeleton, Section Translation & Media Wiki)
- **Zero-Layout-Shift Tab:** The wiki tab appears instantly upon screen open with a shimmering placeholder skeleton, eliminating layout jumps and flickering.
- **Expanded Section Title Localization:** Added a dedicated dictionary mapping 24+ common Fandom wiki section headers to Turkish ("Role in the Series", "Appearances in Other Media", "Creation/Design", etc.).
- **Media Detail Wiki Tab:** Integrated encyclopedic Fandom wiki overviews, table of contents, and infoboxes into Anime and Manga detail screens.
