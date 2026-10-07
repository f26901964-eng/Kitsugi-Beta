## 🛡️ Yanlış İçerik Eklenmesine Karşı Kimlik Güvencesi, Çapraz Eşitleme Teşhisi & Yenilenen Panel (v2.4.196) (TR)

### 🛡️ Yanlış İçerik Eklenmesine Karşı Kimlik Güvencesi (CrossSyncIdentityGuard)
1. **6 Sızıntı Vektörünün Kapatılması:**
   - **V1 (TMDB - ARM):** ARM servisine TMDB kimliği aktarımı kaldırıldı (film/dizi ID çakışması ve tüm franchise'ı kapsayan dizi ID'lerinin yanlış anime MAL ID'si üretmesi engellendi; yalnızca birebir MAL/AniList/Kitsu kimlikleri kullanılır).
   - **V2 (Simkl Anime - TMDB):** Simkl'a anime kayıtları için TMDB kimliği gönderilmesi tamamen engellendi. Simkl ID varsa yalnızca o gönderilir; dizi/film çakışmaları önlendi.
   - **V3 (Simkl Başlık Eşleme):** Güvenli kimliği (Simkl/MAL) olmayan animeler için Simkl'da başlık eşleştirmesi kapatıldı (`titleMatchingAllowed = false`). Benzer adlı film veya dizilerin listeye sızması engellendi.
   - **V4 (Kitsu Başlık Eşleme):** Kitsu başlık aramasında kısmi eşleşmeler (ör. "Oni Chichi" ⊂ "Oni Chichi 2") kapatıldı; birebir alias ve uyumlu yıl şartı getirildi.
   - **V5 & V6 (Tek Kaynak Kimlik Doğrulama):** Yeni `CrossSyncIdentityGuard` ile en az iki bağımsız kaynakça doğrulanmayan veya tek bir sağlayıcı eşlemesinden (Simkl `ids.mal`, Kitsu `mappings`, AniList `idMal`) gelen kimlikler için resmi MAL API / Jikan kataloğundan başlık ve yıl doğrulaması yapılır.
2. **"Doğrulanamayan Kimlikle Asla Yeni Kayıt Eklenmez" Kuralı:**
   - Başlık akrabalığı ve yıl kontrolünden geçemeyen şüpheli kimliklerle hiçbir platforma (AniList, MAL, Kitsu, Shikimori, Simkl) yeni kayıt eklenmez; atlanır ve raporda gerekçesiyle gösterilir.
   - Mevcut kayıtların güncellenmesi platformun kendi kimliğiyle yapıldığı için bu kısıtlamadan etkilenmez, güvenle devam eder.

### 📊 Platform Bazlı Eşitleme ve Hata Sayımı Düzeltmeleri
1. **Simkl Sayım ve Makbuz İyileştirmesi:**
   - 35'lik grupta tek bir eşleşmeyen öğe yüzünden tüm grubun "200 hata" olarak sayılması engellendi; kayıt bazlı makbuz (`Receipt.unmatched`) ile başarılı olanlar "eklendi", Simkl'de bulunamayanlar "atlandı" olarak işlenir.
   - TV dizi ilerlemesi sınırı ve kısmi puan/geçmiş uyarıları artık grup hatası değil; eşitleme sonunda başlık listesiyle tek bir özet uyarı olarak raporlanır.
   - 400M+ Shikimori kimliklerinin yanlışlıkla Kitsu ID olarak Simkl'e gönderilmesi düzeltildi (`300M..400M` aralık kontrolü).
2. **Kitsu Resmi Mappings & 429 Yönetimi:**
   - Resmi Kitsu `/mappings` API uç noktası (MAL ve AniList eşlemeleri) entegre edildi; manga ve ARM'da olmayan animeler için ID çözümleme oranı artırıldı.
   - Kitsu istekleri için merkezi limiter ve 429 durumlarında 3 tekrarlı yeniden deneme mekanizması eklendi; API hata gövdeleri (`KitsuWriteResult`) okunarak ayrıntılı teşhis sağlandı.
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
3. **Durum ve İlerleme Doğruluğu:** Simkl'in toplam bölüm (`total`) sayısından "Tamamlandı" çıkarımı yapılması engellendi. Tek kaynaklı gruplarda gereksiz güncellemeler engellendi.
4. **Temiz Rapor:** "Eşitleme kısmen tamamlandı" satırı hata yerine bilgi formatına dönüştürüldü ("N işlem yazılamadı, M kayıt atlandı").

### 📱 Yenilenen Tam Ekran Çapraz Eşitleme Paneli
1. **Tam Ekran Dialog & Çentik Uyumu:** Panel artık `fullScreen = true` olarak açılır; durum çubuğu ve ekran çentikleri (`displayCutoutPadding`) ile kusursuz uyum sağlar.
2. **Dikey ve Yatay Yönlendirme:** Dikey modda tek sütunlu akıcı hiyerarşi; yatay modda sol panelde ilerleme/hesaplar/eylemler, sağ panelde filtreler ve canlı günlük gösterilir.
3. **Filtre Çipleri & Kompakt Hesap Kartları:** Hata ve uyarı sayıları filtre çiplerine rozet olarak eklendi; platform logolu kartlar ve 40 dp optimize butonlarla daha kompakt bir görünüm sunar.

---

## 🛡️ Anti-Pollution Identity Guard, Cross-Sync Diagnostics & Redesigned Dialog (v2.4.196) (EN)

### 🛡️ Cross-Sync Identity Guard & Anti-Pollution Shield
1. **Plugging 6 Inaccuracy Leak Vectors:**
   - **V1 (TMDB - ARM):** Eliminated passing TMDB IDs to ARM resolver (preventing movie/show ID collisions and franchise-wide show IDs from generating wrong MAL anime IDs).
   - **V2 (Simkl Anime - TMDB):** TMDB IDs are never transmitted for anime entries to Simkl. If Simkl ID exists, only Simkl ID is sent.
   - **V3 (Simkl Title Matching Disabled):** Turned off title-based fallback matching in Simkl for anime lacking verified identity (`titleMatchingAllowed = false`).
   - **V4 (Strict Kitsu Title Matching):** Disallowed partial substring matches in Kitsu title lookups; requires exact alias match and compatible year.
   - **V5 & V6 (Pre-Addition Verification):** Introduced `CrossSyncIdentityGuard` to verify unconfirmed or single-source external mappings against official MAL API / Jikan catalogs.
2. **"Never Add Unverified Content" Enforcement:**
   - Any identity failing title kinship or release year checks is rejected from additions across all providers (AniList, MAL, Kitsu, Shikimori, Simkl).
   - Existing entry progress/status updates continue safely using provider-native entry IDs.

### 📊 Provider-Specific Sync & Error Accounting Fixes
1. **Simkl Batch Receipts & Accurate Accounting:** Item-level receipts (`Receipt.unmatched`) accurately distinguish "added" vs "skipped", fixing artificial "200 errors". TV show progress limits become summary warnings. Fixed 400M+ Shikimori synthetic ID leakage.
2. **Kitsu Official Mappings Endpoint & 429 Handling:** Integrated official `/mappings` endpoint (MAL/AniList), added 3-retry backoff for 429 rate limits, and surfaced detailed HTTP error bodies. Added `startYear` to Kitsu imports.
3. **AniList Rate Limiting & Quota Optimization:** Interval tuned to 2100 ms (~28 req/min), added 3x `Retry-After` retry loops, and removed redundant `idMal` lookups.
4. **MyAnimeList & Shikimori Aggregated Reporting:** Consolidated hundreds of missing ID warnings into single summary warnings. Added `startYear` and English titles to Shikimori imports.

### 🧩 Media Identity Matching & Conflict Resolution
1. **Identical Titles & Provider ID Disambiguation:** Multi-adaptation/seasonal works with conflicting IDs (e.g. Berserk, Golden Time) sync cleanly as distinct clusters.
2. **Eliminated False "Verification Required" Skips:** Cross-platform alias differences only trigger manual review if both years are known and differ by >1 year.
3. **Accurate Status Inference:** Excluded Simkl's `total` aired count from "Completed" status heuristics.

### 📱 Redesigned Fullscreen Cross-Sync Dialog
1. **Fullscreen & Display Cutout Aware:** Dialog opens in full screen with native status bar and display cutout padding.
2. **Responsive Portrait & Landscape Layouts:** Fluid vertical column in portrait; split two-column layout in landscape.
3. **Log Filter Badges & Compact Account Cards:** Badges with error/warning counts on filter chips, compact platform cards with official logos, and 40 dp action buttons.
