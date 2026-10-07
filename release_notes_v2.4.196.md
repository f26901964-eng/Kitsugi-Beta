## 🔄 Çapraz Eşitleme Teşhisi, Platform API Optimizasyonları & Yenilenen Panel (v2.4.196) (TR)

### 📊 Platform Bazlı Eşitleme ve Hata Sayımı Düzeltmeleri
1. **Simkl Sayım ve Makbuz İyileştirmesi:**
   - 35'lik grupta tek bir eşleşmeyen öğe yüzünden tüm grubun "200 hata" olarak sayılması engellendi; kayıt bazlı makbuz (`Receipt.unmatched`) ile başarılı olanlar "eklendi", Simkl'de bulunamayanlar "atlandı" olarak işlenir.
   - TV dizi ilerlemesi sınırı ve kısmi puan/geçmiş uyarıları artık grup hatası değil; eşitleme sonunda başlık listesiyle tek bir özet uyarı olarak raporlanır.
   - 400M+ Shikimori kimliklerinin yanlışlıkla Kitsu ID olarak Simkl'e gönderilmesi düzeltildi (`300M..400M` aralık kontrolü).
2. **Kitsu Resmi Mappings & 429 Yönetimi:**
   - Resmi Kitsu `/mappings` API uç noktası (MAL ve AniList eşlemeleri) entegre edildi; manga ve ARM'da olmayan animeler için ID çözümleme oranı artırıldı.
   - Kitsu istekleri için merkezi limiter ve 429 durumlarında 3 tekrarlı yeniden deneme mekanizması eklendi; API hata gövdeleri (`KitsuWriteResult`) okunarak ayrıntılı teşhis sağlandı.
   - Başlık aramasında eşit puanlı belirsiz adaylar yanlış kayda yazılmak yerine güvenle atlanır.
   - Kitsu içe aktarımına yayın yılı (`startYear`) eklendi.
3. **AniList Kota ve Hız Limiti Optimizasyonu:**
   - İstek aralığı 2100 ms'ye ayarlanarak (~28 req/dk) AniList'in fiili 30/dk hız sınırına tam uyum sağlandı.
   - HTTP 429 durumlarında `Retry-After` başlığına duyarlı en fazla 3 kez otomatik yeniden deneme eklendi.
   - İçe aktarımda zaten var olan `idMal` için her kayıtta atılan gereksiz sorgu kaldırılarak kota tasarrufu yapıldı.
4. **MyAnimeList & Shikimori Özet Raporlama:**
   - Çözülemeyen her kayıt için tek tek uyarı basılması yerine platform başına tek satırlık özet uyarı eklendi.
   - Shikimori içe aktarımına `aired_on`/`released_on` yayın yılı ve İngilizce başlık eklendi.
   - AniList içe aktarımında `seasonYear` boşsa `startDate.year` yedeği devreye alındı.

### 🧩 Kimlik Eşleştirme ve Çakışma Yönetimi
1. **Aynı İsimli Yapımlar & Çelişen Kimlikler:** Berserk (1997/2016), Hunter x Hunter (1999/2011), Golden Time gibi aynı başlığa sahip yapımların tamamen izole edilip hiçbir yere yazılmaması sorunu çözüldü; artık ayrı gruplar halinde kendi kimlikleriyle yazılır.
2. **Sahte "Kimlik Doğrulaması Gerekli" Gevşetmesi:** Farklı platformlardaki alias uyumsuzlukları yalnızca her iki tarafta da yıl biliniyor ve yıl farkı 1'den büyükse incelemeye düşer; aksi halde güvenle birleştirilir.
3. **Durum ve İlerleme Doğruluğu:** Simkl'in toplam bölüm (`total`) sayısından "Tamamlandı" çıkarımı yapılması engellendi (devam eden dizilerin yanlış bitirilmesi önlendi). Tek kaynaklı gruplarda gereksiz güncellemeler engellendi.
4. **Temiz Rapor:** "Eşitleme kısmen tamamlandı" satırı hata yerine bilgi formatına dönüştürüldü ("N işlem yazılamadı, M kayıt atlandı").

### 📱 Yenilenen Tam Ekran Çapraz Eşitleme Paneli
1. **Tam Ekran Dialog & Çentik Uyumu:** Panel artık `fullScreen = true` olarak açılır; durum çubuğu ve ekran çentikleri (`displayCutoutPadding`) ile kusursuz uyum sağlar.
2. **Dikey ve Yatay Yönlendirme:** Dikey modda tek sütunlu akıcı hiyerarşi; yatay modda sol panelde ilerleme/hesaplar/eylemler, sağ panelde filtreler ve canlı günlük gösterilir.
3. **Filtre Çipleri & Kompakt Hesap Kartları:** Hata ve uyarı sayıları filtre çiplerine rozet olarak eklendi; platform logolu kartlar ve 40 dp optimize butonlarla daha kompakt bir görünüm sunar.

---

## 🔄 Cross-Sync Diagnostics, Platform API Optimizations & Redesigned Dialog (v2.4.196) (EN)

### 📊 Provider-Specific Sync & Error Accounting Fixes
1. **Simkl Batch Receipts & Accurate Accounting:**
   - Prevented single unmatched items from failing entire 35-item batches (eliminating false "200 errors"); item-level receipts (`Receipt.unmatched`) accurately mark confirmed items as "added" and missing items as "skipped".
   - TV show progress limitations and partial rating/history responses are converted from batch failures to aggregated summary warnings.
   - Restricted Kitsu synthetic ID range (`300M..400M`) to prevent 400M+ Shikimori IDs from misrouting to Simkl.
2. **Kitsu Official Mappings Endpoint & 429 Handling:**
   - Integrated official `/mappings` API endpoint (MAL and AniList external mappings), drastically increasing resolution for manga and unmapped anime.
   - Added rate limiter and 3-attempt exponential backoff for HTTP 429 responses; surfaced server error payloads via `KitsuWriteResult`.
   - Ambiguous title search candidates with equal relevance scores safely skip instead of risking incorrect writes.
   - Added `startYear` to Kitsu library import.
3. **AniList Rate Limiting & Quota Optimization:**
   - Tuned rate limiter to 2100 ms (~28 req/min) to respect AniList's enforced 30 req/min limit.
   - Added 3-attempt retry loop respecting `Retry-After` headers on 429 rate limit errors.
   - Removed redundant per-entry `idMal` queries during sync, preserving API quota.
4. **MyAnimeList & Shikimori Aggregated Reporting:**
   - Replaced hundreds of individual unmapped ID warnings with consolidated provider summary warnings.
   - Added release year (`startYear`) and English titles to Shikimori import.
   - Added `startDate.year` fallback in AniList import for manga entries missing `seasonYear`.

### 🧩 Media Identity Matching & Conflict Resolution
1. **Identical Titles & Provider ID Disambiguation:** Titles sharing names across different adaptations/seasons (e.g., Berserk 1997/2016, Hunter x Hunter 1999/2011, Golden Time) are no longer isolated; they sync cleanly as separate clusters with their respective provider IDs.
2. **Eliminated False "Verification Required" Skips:** Cross-platform alias differences only trigger manual review if both years are known and differ by more than 1 year.
3. **Accurate Status Inference:** Excluded Simkl's `total` aired count from "Completed" status heuristics to prevent ongoing shows from falsely closing.
4. **Informational Report Summary:** Converted "Sync partially completed" from an error state to an informative summary indicating exact processed and skipped counts.

### 📱 Redesigned Fullscreen Cross-Sync Dialog
1. **Fullscreen & Display Cutout Aware:** Dialog opens in full screen with native status bar and display cutout padding.
2. **Responsive Portrait & Landscape Layouts:** Fluid vertical column in portrait; split two-column layout in landscape (left: progress/accounts/actions, right: filters and log viewer).
3. **Log Filter Badges & Compact Account Cards:** Badges with error/warning counts on filter chips, compact platform cards with official logos, and 40 dp action buttons.
