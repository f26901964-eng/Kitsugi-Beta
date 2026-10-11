# Kitsugi Beta v2.4.238 — Eklenti Vekil Sunucu, Akış Performansı, MAL Galerisi, Geliştirici Günlükleri, Duyarlı Izgara ve Bangumi Liste Akışı

## 🇹🇷 Türkçe (v2.4.238)

### 📊 1. Geliştirici Günlükleri (Developer Logs) Yenilendi
- **Tam Genişlik Başlık:** Başlık alanı tam genişliğe yayıldı, dikey harf kırılması sorunu çözüldü; kapat butonu sağa yerleştirildi.
- **Yatay Kaydırılabilir Araç Çubuğu:** Yenile, Kopyala, Kaydet, Paylaş ve Temizle butonları kompakt ve yatay kaydırılabilir modern bir satıra toplandı.
- **Log Seviye Filtreleri:** Hata, Uyarı, Bilgi ve Hata Ayıklama (Debug) seviyeleri anlık sayaçlarıyla filtrelenebilir hale getirildi.
- **Gelişmiş Seçenekler:** "Sadece Bu Uygulama" filtresi, "Satır Kaydır" ve "Otomatik Yenile" anahtarları ile toplam/hata/uyarı özet satırı eklendi.
- **Düz Metin Paylaşım:** Log paylaşımı doğrudan okunabilir düz metin formatına geçirildi.

### 📐 2. Duyarlı Medya Izgaraları (Responsive Grid Columns)
- **Tek Merkezden Dinamik Sütun Hesabı (`KitsugiGridColumns`):** Tüm medya ızgaraları (Tümünü Gör, Stüdyo, Yapımcı, Favoriler, Koleksiyon Klasörleri, Manga Kaynakları ve Eklenti Izgaraları) standartlaştırıldı.
- **Cihaz & Yön Uyumu:** Dar ekranlarda (telefon dikey, < 600dp) 2 sütun, orta ekranlarda (telefon yatay / küçük tablet, 600–839dp) 3 sütun, geniş ekranlarda (tablet yatay, ≥ 840dp) 4 sütun; dikey/yatay geçişte otomatik yeniden hesaplanır.

### 🌄 3. Vitrin (Hero Section) Karartma ve Okunabilirlik İyileştirmesi
- **Derin Alt Karartma:** Telefon dikey modunda alt karartma yüksekliği 220dp'den 300dp'ye uzatıldı ve degrade opaklığı artırıldı; başlık ve açıklamaların altındaki görsel parlamaları engellendi.
- **Belirgin Meta Satırı:** Tür, yıl ve bölüm bilgisi içeren meta satırının rengi TextSecondary yapılarak koyu zemin üzerinde çok daha rahat okunabilir hale getirildi.

### 🌐 4. GitHub Vekil Sunucu (jsDelivr) Anahtarı Düzeltildi
- **Kök Neden Giderildi:** Eklenti ve akış ayarlarındaki diyalog çağrılarında (akış seçici, manga kaynakları) useGithubProxy / onUseGithubProxyChanged parametreleri geçilmiyordu; diyalog varsayılan (false) değerle açıldığı için switch açılamıyordu. Ayrıca repo katmanı ayarı DataStore'dan okumuyordu.
- **Düzeltme:** Hem telefon hem Android TV diyaloglarına bağlandı (TV için özel ayar satırı eklendi). CloudstreamRepoRepository artık ayarı okur; anahtar açıkken eklenti ve repo listeleri doğrudan jsDelivr CDN üzerinden çekilir. Doğrudan GitHub ve hata durumunda jsDelivr yedeği aynen korunur.

### ⚡ 5. Akış Listesi Kasması Tamamen Çözüldü (4 Ayrı İyileştirme)
- **Animasyon Yükü Kaldırıldı:** Akış kartları her kaydırmada AnimatedVisibility (fadeIn + slideInVertically) ile yeniden çiziliyordu; gereksiz animasyon kaldırılarak liste kaydırması pürüzsüzleştirildi.
- **Akıllı ve Gecikmeli Ölçüm:** Eskiden her kart compose edildiğinde hemen HLS/HEAD ağ ölçümü başlıyordu (60 sonuçta onlarca paralel istek). Artık ölçüm yalnızca **görünür kartlarda** ve **350 ms yerleşme gecikmesinden sonra** başlar; kaynak kalite/ses/altyazı bilgilerini zaten sunuyorsa ölçüm hiç yapılmaz (isProbeNeeded).
- **İptal Edilebilir Ölçüm Kapısı:** Bloklayıcı OkHttp.execute() yerine Call.enqueue ve suspendCancellableCoroutine kullanıldı; kart görüş alanından çıkınca istek gerçekten iptal edilir ve eşzamanlılık izni hemen serbest kalır (eşzamanlılık kapasitesi 3 → 2).
- **Filtresiz Hızlı Yol:** Arama veya etiket filtresi aktif değilken 60+ akış için çalıştırılan regex tabanlı kalite/dil çözümlemesi atlanır; çipler key ile optimize edildi.

### 🖼️ 6. MAL ve MAL'a Bağlı Kaynaklar Galeriye Ortak Edildi
- **Tüm Detay Sayfaları:** Anime, film ve dizi detaylarında gerçek MAL kimliği çözülebiliyorsa MAL (Jikan) /pictures görselleri Fanart.tv, TMDB ve Shikimori görselleriyle **paralel** çekilip galeriye eklenir.
- **Manga Galerisi Zenginleşti:** Manga detay sayfaları eskiden sadece kapak görseliyle sınırlıydı; artık MAL ek görselleri de galeriye katılır.
- **Kimlik Güvenliği:** AniList/Kitsu/Bangumi stable ID'leri ve TMDB/Simkl ID'leri MAL kimliği olarak kullanılmaz; yanlış yapımın görselleri kesinlikle galeriye karışmaz.
- **Önbellek:** 80 kayıtlık LRU önbellek; geçici hataların kalıcı olmaması için boş sonuçlar önbelleğe yazılmaz.
- **MAL OAuth Client Secret Desteği & Harici Tarayıcı:** Web/özel MAL uygulamaları için mal_client_secret desteği local.properties ve BuildConfig'e eklendi; MAL girişi harici tarayıcıya yönlendirilerek giriş güvenliği sağlandı.
- **Çapraz Eşitleme Logoları:** Çapraz eşitleme ayar satırlarında resmi marka logoları (AniList, MAL, Simkl, Kitsu, Shikimori, Bangumi) yerleştirildi.

### 🎌 7. Bangumi Liste Üyeliği ve Sağlayıcı İzolasyonu
- **Yalnızca Gerçek Bangumi Kayıtları:** Bangumi sekmesinde ve sayaçlarında yalnızca kullanıcının Bangumi hesabına ait (source = bangumi ve geçerli Bangumi stable ID'li) kayıtlar gösterilir.
- **Sağlayıcı Çapraz Karışması Engellendi:** Diğer sağlayıcıların (Simkl, AniList, MAL) kayıtları veya yerel kayıtlar Bangumi üyeliği sayılmaz; bgm.tv koleksiyonuyla birebir sayı ve liste tutarlılığı sağlandı.

### 🔄 8. Bangumi Uzak Öncelikli Ekleme ve Düzenleme Akışı
- **Önce Uzak Koleksiyon Güncellemesi:** Bangumi arama sonuçlarından ekleme veya düzenleme yapıldığında istek önce doğrudan Bangumi API'sine iletilir (BangumiSyncManager.syncEntryToBangumi).
- **Veri Güvenliği Garantisi:** Uzak koleksiyon güncellemesi başarılı olmadan (collectionUpdated) yerel veritabanına kayıt eklenmez veya değiştirilmez. Başarılı olunca Bangumi subject ID'si stable alanda saklanır.
- **Doğrudan Bangumi Bağlamı:** Bangumi kayıtları düzenleyicide kendi yerel durumları, puanı ve ilerlemesiyle açılır.

### 🛡️ 9. Bangumi Senkronizasyon İzolasyonu
- **Dış Dağıtım Engeli:** Bangumi üzerindeki koleksiyon işlemleri diğer sağlayıcılara (AniList / MAL / Simkl / Shikimori) fan-out yapmaz; sağlayıcıya özel temiz senkronizasyon korunur.

### 🎬 10. Bangumi REAL İçeriklerde Film / Dizi Ayrımı ve Tür Güvenliği
- **Platform Tabanlı Eşleme:** Bangumi REAL kategorisindeki canlı çekim (live-action) yapımlar platform bilgisine göre doğru bir şekilde Film (Movie) ve Dizi (TV) türlerine dönüştürülür.
- **Anime Varsayılanı Kaldırıldı:** Desteklenmeyen (müzik, oyun vb.) veya belirsiz türdeki Bangumi kayıtlarının otomatik olarak Anime şeklinde içe aktarılması engellendi.
### 🌍 11. Kapsamlı İngilizce Dil Tamamlama (Batch 2)
- **825 Yeni Dil Kaynağı:** `values/strings.xml` ve `values-en/strings.xml` 4.234'ten 5.052 anahtara çıkarıldı; TR/EN tam parite sağlandı.
- **1.685 Kod Noktası Kaynağa Bağlandı:** Kod içine gömülü sabit metinler temizlenerek 891 `stringResource` ve Composable olmayan katmanlar için 794 `KitsugiStrings.get` çağrısına bağlandı (288 dosya).
- **Yeni KitsugiStrings Altyapısı:** ViewModel, servis ve veri katmanlarında güvenli ve erken aşamada başlatılan metin çözücü mimarisi devreye alındı.

---

## 🇬🇧 English (v2.4.238)

### 📊 1. Redesigned Developer Logs Dialog
- **Full Width Header:** Full horizontal title bar avoids awkward vertical text breaking, with close button pinned cleanly to the right.
- **Scrollable Toolbar:** Refresh, Copy, Save, Share, and Clear buttons organized into a sleek horizontal row.
- **Level Filters:** Filter logs by Error, Warning, Info, and Debug with live badge counters.
- **Enhanced Utilities:** "Only This App" process filter, text wrapping, auto-refresh toggles, and total/error/warn summary header.
- **Plain Text Sharing:** Direct plain-text sharing for immediate readability in external apps.

### 📐 2. Responsive Media Grid Layout (KitsugiGridColumns)
- **Centralized Column Calculation:** Standardized across See All, Studios, Producers, Favourites, Folder Collections, Manga Sources, and Addon Grids.
- **Breakpoint Optimization:** 2 columns on phones portrait (<600dp), 3 columns on landscape/foldables (600–839dp), 4 columns on tablets (>=840dp); adapts dynamically on rotation.

### 🌄 3. Hero Section Contrast & Readability
- **Extended Gradient Vignette:** Height expanded to 300dp on phone portrait with heavier gradient stops, preventing background image bleed behind titles.
- **High-Contrast Metadata:** Meta information row styled in TextSecondary for clear legibility over dark backdrop fades.

### 🌐 4. GitHub Proxy (jsDelivr) Toggle Fixed
- **Root Cause Resolved:** Dialog callers across stream picker and manga browse omitted useGithubProxy / onUseGithubProxyChanged, causing the switch to always render off and ignore user input. The repository layer also neglected to read the setting.
- **Fix:** Fully wired on phone and Android TV (with a dedicated TV settings row). CloudstreamRepoRepository now reads the setting; when enabled, repo and plugin requests prefer jsDelivr CDN directly. Fallback mechanisms remain intact.

### ⚡ 5. Stream Picker Performance & Stutter Resolution
- **Removed Replay Animations:** Eliminated per-card AnimatedVisibility enter animations that re-ran on every scroll pass, restoring silky-smooth LazyColumn scrolling.
- **Debounced Smart Probing:** Probing is now gated exclusively to visible cards after a 350 ms settle delay (rapid scrolling incurs zero probe requests). If a provider already supplies quality, audio, and subtitles, probing is bypassed (isProbeNeeded).
- **Cancellable Probing Queue:** Converted blocking OkHttp calls to cancellable Call.enqueue with suspendCancellableCoroutine. Abandoned requests are immediately cancelled, releasing concurrency permits (capacity 3 -> 2).
- **Filterless Fast Path:** Regex-based quality and audio resolution is skipped when no active filters are set; filter chips are keyed.

### 🖼️ 6. MAL & Linked Sources Shared Gallery
- **Unified Media Galleries:** Detail pages for anime, TV series, and movies now resolve genuine MAL IDs and fetch MAL (Jikan) /pictures in parallel with Fanart.tv, TMDB, and Shikimori.
- **Enriched Manga Galleries:** Manga details, previously limited to their single cover, now pull in additional MAL artwork.
- **Strict ID Namespace Safety:** AniList/Kitsu/Bangumi stable IDs and TMDB/Simkl IDs are never forwarded to MAL, guaranteeing no cross-media artwork contamination.
- **LRU Caching:** 80-entry LRU cache prevents duplicate fetches; empty results are never cached to avoid locking out transient network issues.
- **MAL OAuth Client Secret Support & External Browser:** Added mal_client_secret configuration support to local.properties and BuildConfig; directs MAL authorization to external browser for dependable OAuth completion.
- **Cross-Sync Platform Logos:** Official vector logos added to cross-sync toggles for AniList, MAL, Simkl, Kitsu, Shikimori, and Bangumi.

### 🎌 7. Bangumi List Membership & Provider Isolation
- **Pure Bangumi Scope:** The Bangumi tab and collection counters exclusively display authentic Bangumi records (source = bangumi with stable Bangumi subject IDs).
- **Cross-Provider Isolation:** Records from other providers (Simkl, AniList, MAL) and local-only items are never treated as Bangumi members, maintaining exact parity with bgm.tv.

### 🔄 8. Remote-First Add & Edit Flow for Bangumi
- **Remote Update Precedence:** Adding or editing Bangumi search results updates the remote Bangumi collection first (BangumiSyncManager.syncEntryToBangumi).
- **Data Integrity Assurance:** If the remote API call fails (collectionUpdated == false), the local database is not altered. Upon success, the entry is committed with its stable Bangumi subject ID.
- **Native Bangumi Context:** Bangumi entries open directly in Bangumi editor mode, cleanly handling native collection statuses, progress, and scoring.

### 🛡️ 9. Bangumi Isolated Synchronization
- **No External Leakage:** Bangumi updates remain isolated and do not fan out to other tracking services (AniList / MAL / Simkl / Shikimori).

### 🎬 10. REAL Media Type Classification & Safety
- **Platform-Aware Mapping:** Bangumi REAL entries are accurately mapped to Movie or TV based on platform metadata.
- **No False Anime Defaults:** Unsupported categories (music, games) or unknown types are rejected rather than incorrectly defaulting to Anime.
### 🌍 11. Comprehensive English Localization (Batch 2)
- **825 New Localization Keys:** Resource files expanded to 5,052 strings with 100% TR/EN parity across all screens.
- **1,685 Hardcoded Call Sites Cleaned:** Migrated 891 `stringResource` and 794 `KitsugiStrings.get` points across 288 files.
- **KitsugiStrings Architecture:** Non-Composable string resolver providing robust localization across ViewModels and background services.
