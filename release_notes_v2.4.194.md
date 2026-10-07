## ⚡ Çapraz Eşitleme (Cross-Sync) %0 Takılma Düzeltmesi, Gelişmiş Tanılama & Listem "Tümü" Görünümü (v2.4.194) (TR)

### ⚡ Çapraz Eşitleme (Cross-Sync) %0 Takılma Düzeltmesi & Gelişmiş Tanılama
1. **Aday İndeksleme ve Hız:** Eşleştirme aşamasında her yeni içerik için tüm birleştirilmiş kayıtların baştan sona taranması ($O(N^2)$) kaldırıldı. Kimlik ve başlık indeksleriyle (`CrossSyncCandidateIndex`) aday kayıtlar anında daraltılır; binlerce içerikte yaşanan donma ve takılma sorunu tamamen çözüldü.
2. **Canlı İlerleme Sayacı:** Gruplama esnasında ilerleme sayacı her 25 kaynak kaydında bir canlı güncellenir ve coroutine iptal kontrolleri düzenli çalışır.
3. **Dinamik İlerleme & Duraklama Uyarıları:** Ağ ve hazırlık adımlarında sahte %0 yerine belirsiz (indeterminate) ilerleme çubuğu gösterilir. Ekrana geçen süre, tahmini kalan süre ve uzun süren duraklamalarda uyarı eklendi.
4. **Gelişmiş Günlük & Rapor Paylaşımı:** Günlükte Tümü / Sorunlar / Hatalar / Uyarılar filtreleri ve genişletilebilir teknik detaylar yer alır. Eşitlemeyi onaylayarak durdurabilir, sürerken kısmi raporu paylaşabilir veya tamamlanınca raporu dışa aktarabilirsiniz.
5. **Otomatik Rapor ve Güvenlik:** Raporlar otomatik olarak `Downloads/Kitsugi/CrossSyncReports` dizinine kaydedilir; cihaz ve ortam bilgileri eklenir, Bearer ve API anahtarları maskelenir (`[REDACTED]`). Hesaplardan içerik silinmez.
6. **İstem Dışı Eşitleme Koruması:** "Son Eşitleme Raporu"na tıklandığında yanlışlıkla yeni bir eşitleme başlatılması engellendi.

### 🌐 Listem Sayfası Birleşik "Tümü" Görünümü & Dinamik Platform Rozetleri
1. **Tüm Kaynakları Tek Ekranda Birleştirme:** Listem sekmesine AniList, MyAnimeList, Simkl, Kitsu ve Shikimori kütüphanelerini tek ekranda toplayan "Tümü" seçeneği eklendi.
2. **Tekil Gösterim:** Farklı servislerde bulunan aynı içerik yinelenmek yerine tek kartta birleştirilir (`groupMyListEntries`).
3. **Dinamik Kaynak Rozetleri:** Kartların altında yapımın hangi platformlarda bulunduğunu gösteren orijinal platform logoları (`FlowRow` ile taşma yapmadan) sergilenir.
4. **Tüm Düzenlerle Uyumlu:** Kompakt, rahat, ayrıntılı, 2 sütunlu grid ve minimalist kart düzenleriyle tam uyumludur.
5. **Tek Dokunuşla Yenileme:** Birleşik görünümde aşağı çekip yenileme yapıldığında bağlı tüm aktif sağlayıcılar sırayla eşitlenir.

---

## ⚡ Cross-Sync 0% Stall Fix, Advanced Diagnostics & My List Unified "All" View (v2.4.194) (EN)

### ⚡ Cross-Sync 0% Stall Fix & Advanced Diagnostics
1. **Candidate Indexing & Performance:** Eliminated $O(N^2)$ quadratic scanning during cross-platform media grouping. Added identity & title candidate index (`CrossSyncCandidateIndex`), speeding up grouping on multi-thousand entry libraries.
2. **Live Progress Reporting:** Progress counters update every 25 source records during grouping, with periodic cooperative coroutine cancellation checks.
3. **Indeterminate Progress & Stall Warnings:** Displays indeterminate progress indicators during network/verification steps instead of a false 0% determinate bar. Added elapsed time, estimated time, and long-stall warnings.
4. **Advanced Log Filtering & Report Sharing:** Logs feature All / Issues / Errors / Warnings filters and expandable technical stacktraces. Allows confirmed cancellation, sharing partial reports while sync is active, and exporting final reports.
5. **Auto Report Storage & Credential Redaction:** Diagnostic reports are automatically saved to `Downloads/Kitsugi/CrossSyncReports/` with environment details and redacted credentials (`[REDACTED]`). No items are deleted from upstream accounts.
6. **Accidental Sync Trigger Prevention:** Viewing the "Latest Sync Report" no longer accidentally launches a new synchronization.

### 🌐 My List Unified "All" View & Dynamic Platform Badges
1. **Unified Library Tab:** Added an "All" option to the My List screen consolidating libraries across AniList, MyAnimeList, Simkl, Kitsu, and Shikimori in one view.
2. **Deduplicated Media Grouping:** Identical media titles across multiple connected providers are unified into a single card representation.
3. **Dynamic Platform Badges:** Cards display responsive badges (`FlowRow`) indicating all connected platforms hosting the entry.
4. **Layout Compatibility:** Fully supported across compact, comfortable, large, 2-column grid, and minimalist card designs.
5. **Unified Pull-to-Refresh:** Pulling to refresh in the combined view sequentially syncs all active connected providers.
