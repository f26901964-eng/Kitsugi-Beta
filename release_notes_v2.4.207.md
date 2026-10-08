# Kitsugi Beta v2.4.207

## 🇹🇷 Türkçe (v2.4.207)

### 🎌 1. Bangumi (bgm.tv) Tam Platform Entegrasyonu (7. Kaynak)
- **OAuth 2.0 & Kimlik Doğrulama:** `kitsugi://bangumi-auth` ve `aniyomi://bangumi-auth` yönlendirme şemalarıyla modern yetkilendirme altyapısı kuruldu. `BangumiAuthStore` ve `BangumiAuthManager` ile "1-Tık Otomatik Giriş" ve manuel yetki kodu girme seçenekleri sağlandı.
- **Kütüphane & İçe Aktarma:** Bangumi koleksiyon durumları (想看/在看/看过/搁置/抛弃 - İzlemek İstiyorum / İzliyorum / İzledim / Beklemede / Bıraktım), 0-10 puanlama sistemi ve bölüm bazlı ilerleme (打格子) Kitsugi kütüphanesine içe aktarma (`BangumiImportManager`) ve iki yönlü senkronizasyon (`BangumiSyncManager`) ile tam entegre edildi.
- **Listem Sekmesi (7. Sekme):** Listem ekranına 7. sekme olarak 🎌 Bangumi kütüphanesi eklendi (`MyListLibraryGrouping`, `MyListScreen`, `MyListComponents`, `MyListEmptyState`). Özel marka rengi (`#F09199`), rozetler, sayaçlar ve doğrudan sekme içinden açılan hesap bağlama diyaloğu eklendi.
- **Keşfet & Arama Motoru:** Keşfet ekranında 7. platform olarak Bangumi desteği (`AllSourcesExplore`, `ExplorePlatformToggle`, `ExploreViewModel`), Arama motorlarında Bangumi motoru (`KitsugiBangumiClient`, `SearchViewModel`), anime/manga gelişmiş filtreleme, sıralama, karakter ve personel arama kabiliyeti kazandırıldı.
- **Görsel Rozet & Marka:** Gerçek Bangumi vektör logosu (`ic_logo_bangumi.xml`), Hero rozetleri, detay sayfası başlıkları ve galeri rozetleri güncellendi.

### ⚠️ 2. Çapraz Eşitleme (Cross-Sync) Güvenlik & Hukuki Sorumluluk Reddi (Disclaimer)
- **Merkezi Sorumluluk Reddi (`CrossSyncDisclaimer.kt`):** Çapraz eşitlemenin deneysel (beta) bir özellik olduğunu, eşleştirmelerin otomatik yapıldığını ve hiçbir eşleştirmenin %100 doğruluk garantisi taşımadığını; yanlış eşleşme, liste karışması veya platform yaptırımlarından (kısıtlama, askıya alma, hesap yasağı vb.) sorumluluk kabul edilmediğini belirten açık ve kapsamlı uyarı metinleri eklendi.
- **Görünür Uyarılar:**
  - Hesap bağlantıları Cross-Sync satırına "Deneysel ·" etiketi ve kısa uyarı eklendi.
  - Çok Yönlü Eşitleme ve Cross-Sync ayar sayfasına bilgi kartı içine ayrıntılı uyarı, Hızlı İşlem bölümü altına kısa not eklendi.
  - Eşitleme penceresinde (`KitsugiCrossSyncDialog`) güvenlik notunun altına ayrıntılı uyarı yerleştirildi.
- **Çoklu Aday Grup Akrabalık Koruması (`AuthViewModel`):** Birden fazla aday grup bulunduğunda, tekil ortak kimlikle seçilen grup için yıl uyumu (`CrossSyncIdentityGuard.yearsCompatible`) ve başlık akrabalığı kontrolü eklendi; uyuşmayan kayıtların yanlış birleştirilmesi ve başka hesaplardaki doğru kayıtların bozulması engellendi (`identityReviewRequired = true`).
- **Rapor Teşhis Etiketleri:** Eşitleme teşhisinde iç kimlik alanları (`300M+` Kitsu, `100M+` AniList) açıkça etiketlenerek (`describeMalIdField`) MAL kimliği sanılması önlendi.

### ⚡ 3. Jikan Hız Limiti, Shikimori Başlık Dili & Kitsu Paralel İstek İyileştirmeleri
- **Jikan Hız Limiti & 429 Koruması (`KitsugiApiBase`):** Jikan için saniyede 3 ve dakikada 55 istek (kayan pencere) limiti getirildi. 429 yanıtı alındığında host geçici olarak bekletilir ve yeniden deneme sayısı 3'e çıkarılarak sekme/karakter boş kalma sorunları giderildi.
- **Shikimori Rusça Başlık Çözümü (`ShikimoriTitleResolver`):** Shikimori GraphQL API üzerinden tek istekte İngilizce ve Japonca başlıklar çekilerek, kullanıcının ayarladığı tercih diline (Romaji / İngilizce / Japonca) göre gösterilmesi sağlandı.
- **İlişki Tipi Çevirileri (`KitsugiTranslations`):** Shikimori ve Jikan ilişkilerindeki "Sequel", "Side story", "Adaptation", "Prequel" gibi ilişki türleri Türkçeye çevrildi. Jikan ilişkilerine AniList'ten İngilizce/Japonca başlık desteği eklendi.
- **Kitsu Karakter & Seslendirmen Paralel İstekleri (`KitsuClient`):** Karakter ve seslendirmen sayfaları sıralı yerine en fazla 3 eşzamanlı istekle paralel çekilerek sayfa açılış hızı büyük oranda artırıldı.
- **Seslendirmen Birleştirme Zaman Aşımı Optimizasyonu (`KitsugiCharacterClient`):** Kitsu ve Shikimori için seslendirmen bekleme süresi 8 saniyeden 5 saniyeye indirilerek sayfa kilitlenmeleri önlendi.

### 🔍 4. Arama & "Tümünü Gör" Devamlılığı
- `SearchViewModel` ve `SourceSearchPage` arasındaki `openSourceSearch` köprüsü Bangumi motorunu da kapsayacak şekilde yenilendi; raflardan "Tümünü Gör"e tıklandığında sorgu, kapsam ve görünen sonuçlar eksiksiz aktarılır.

### 📦 5. Dağıtım
- Kullanıcı talimatı doğrultusunda **yalnızca `foss` varyantı** derlendi ve yayınlandı (`Kitsugi-Beta-v2.4.207-foss.apk`). GMS kesinlikle hariç tutuldu.

---

## 🇬🇧 English (v2.4.207)

### 🎌 1. Full Bangumi (bgm.tv) Platform Integration (7th Source)
- **OAuth 2.0 & Authentication:** Implemented redirect schemes `kitsugi://bangumi-auth` and `aniyomi://bangumi-auth` with `BangumiAuthStore` and `BangumiAuthManager`, enabling 1-click automatic browser login and manual authorization code entry.
- **Library & Smart Sync:** Full support for Bangumi collections (想看/在看/看过/搁置/抛弃 - Wish/Do/Collect/On Hold/Dropped), 0-10 rating scale, and episode progress grid via `BangumiImportManager` and `BangumiSyncManager`.
- **My List 7th Tab:** Added dedicated 🎌 Bangumi tab to My List (`MyListLibraryGrouping`, `MyListScreen`, `MyListComponents`), complete with signature brand color (`#F09199`), item counters, status chips, and in-tab account connection prompt.
- **Explore & Multi-Engine Search:** Integrated Bangumi as the 7th platform in All Sources Explore (`AllSourcesExplore`, `ExploreViewModel`) and Global Search (`SearchViewModel`, `KitsugiBangumiClient`) with anime/manga advanced filtering, tags, years, and character/staff lookups.
- **Brand Assets & Polish:** Added official vector logo (`ic_logo_bangumi.xml`), hero badges, detail screen headers, and gallery chips.

### ⚠️ 2. Cross-Sync Safety Guards & Legal Disclaimers
- **Centralized Disclaimer (`CrossSyncDisclaimer.kt`):** Clear legal notice that Cross-Sync is an experimental beta feature with no 100% match guarantee, disclaiming liability for mismatched lists, progress desyncs, or upstream platform sanctions (bans, suspensions).
- **Surface Visibility:**
  - Added "Experimental ·" tag and summary disclaimer to Account Connections.
  - Injected full disclaimer inside the Cross-Sync settings info card and quick actions section.
  - Embedded disclaimer directly into the synchronization dialog (`KitsugiCrossSyncDialog`).
- **Multi-Candidate Guard (`AuthViewModel`):** Added year compatibility (`yearsCompatible`) and title kinship checks for single-evidence selections when multiple candidate groups exist, preventing erroneous mergers and cross-account data overwrite (`identityReviewRequired = true`).
- **Diagnostic Clarity:** Explicitly labeled synthetic IDs (`300M+` for Kitsu, `100M+` for AniList) in diagnostics to prevent confusion with real MAL IDs.

### ⚡ 3. Jikan Rate Limiting, Shikimori Titles & Kitsu Concurrency
- **Jikan 429 Prevention (`KitsugiApiBase`):** Enforced a rate limit of 3 req/sec and 55 req/min (sliding window) for Jikan API, with automatic backoff cooldown and 3 retries on HTTP 429.
- **Shikimori English/Japanese Titles (`ShikimoriTitleResolver`):** Resolves English and Japanese titles via Shikimori's GraphQL API instead of defaulting to Russian titles, strictly adhering to user's title language preference.
- **Relation Type Translations (`KitsugiTranslations`):** Translated relation types (Sequel, Side story, Adaptation, etc.) to Turkish. Added AniList title fallback for Jikan relations.
- **Kitsu Concurrency (`KitsuClient`):** Characters and voice actors are now fetched concurrently (up to 3 parallel requests), significantly accelerating page loads.
- **Voice Actor Timeout Optimization (`KitsugiCharacterClient`):** Reduced VA fallback timeout from 8s to 5s for Kitsu and Shikimori to prevent UI stalls.

### 🔍 4. Search Shelf Continuity
- Extended `openSourceSearch` handoff between `SearchViewModel` and `SourceSearchPage` to seamlessly support Bangumi without dropping active query, scope, or cached shelf results.

### 📦 5. Release Distribution
- Built strictly as **FOSS Release** (`Kitsugi-Beta-v2.4.207-foss.apk`) per user instruction. No GMS variant generated.
