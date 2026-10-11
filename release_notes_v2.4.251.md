# Kitsugi-Beta v2.4.251 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🇹🇷 1. TMDB Türkçe Başlık Varyantı (TR-Only Boşluğu Kapandı)
- **Kök Neden:** Site SADECE Türkçe başlık taşıyorsa (`Örümcek Adam Eve Dönüş Yok`), detay sayfasının EN/Romaji/Native başlıklarıyla eşleşme sağlanamıyor ve `EŞLEŞME YOK` ile sonuçlanıyordu.
- **Çözüm:** `runGetStreams`, çözümlenmiş TMDB kimliğiyle `tr-TR` başlığı çekip (`TmdbTitleLocalizer`) arama varyantlarına ve eşleştirici adaylarına ekler.
- **Önbellek & Performans:** 164 eklenti tek bir TMDB isteğini paylaşır; sonuçlar ve negatif yanıtlar oturum boyunca önbelleğe alınır. Hata/ağ kopması durumunda normal listeyle sessizce devam edilir.

### ⚡ 2. Paralel ID Çözümleme Fırtınasının Engellenmesi
- **Kök Neden:** Her eklenti için (164 paralel) ARM+TMDB ID çözümlemesi yapılıyordu — eklenti başına 8'e kadar ardışık HTTP isteği gidiyor, İzle'ye her basışta ~1000 istek oluşuyor ve 75 saniyelik sağlayıcı bütçesi arama başlamadan tükeniyordu.
- **Çözüm:** `StreamViewModel` üzerinden çözülen kimlikler tüm eklentilere doğrudan aktarılır (`preResolvedIds`). `KitsugiIdResolver.resolveIds` için sonuç önbelleği (`resolvedIdsCache`) eklendi. Ana akışta eklenti başına mükerrer ağ isteği sıfıra indirildi.

### 🎬 3. Anime Tip Filtresi Esnetildi
- Anime detay sayfasında dizi/film eklentileri *"Bu eklenti anime içeriklerini desteklemiyor"* denilerek eleniyordu; oysa yerli dizi siteleri dublajlı anime, film siteleri anime filmleri barındırabiliyor. Filtre gevşetildi; alakasız sonuçlar zaten `CsTitleMatcher` tarafından elenir.

### 🛡️ 4. Başlık Eşleştirici (CsTitleMatcher) & ProGuard Düzeltmesi
- **CsTitleMatcher 1.00 Reddi Giderildi:** Bilinen başlığa benzerliği $\ge 0.90$ olan adayların token filtresi tarafından elenmesi engellendi. Çok dilli veya Japonca ek içeren takma adların (ör. *SUPERNATURAL スーパーナチュラル*) net Latin eşleşmelerini (`Supernatural`) elemesi çözüldü.
- **ProGuard / Gson Keep Kuralı:** CS3 eklentilerinin (ör. Torrential) Gson sınıflarını doğrudan isimle araması nedeniyle R8'in yeniden adlandırmasından kaynaklanan `NoClassDefFoundError: Lcom/google/gson/Gson` hatası keep kurallarıyla giderildi.

### 🧪 5. Eklenti Tanı Koşucusu — Faz 2g (Gerçek Akış / Flow Testi)
- Tanı ekranına her eklentinin KENDİ döndürdüğü başlığı otomatik bulma ve sezon/bölüm eşleştirme hattından geçiren Faz 2g eklendi. Tanı tablosuna `Flow` sütunu eklendi (`🔁✅` / `🔁❌`).

---

## 🇬🇧 English

### 🇹🇷 1. TMDB Turkish Title Variant (Closing the TR-Only Gap)
- **Root Cause:** If a Turkish provider carries ONLY the local Turkish title (e.g. `Örümcek Adam Eve Dönüş Yok`), matching against English/Romaji/Native titles was failing (`NO MATCH`).
- **Fix:** `runGetStreams` retrieves the `tr-TR` title using the resolved TMDB ID via `TmdbTitleLocalizer` and injects it into query variants and matcher candidates.
- **Caching:** 164 plugins share a single TMDB request with session caching for both hits and misses, falling back safely without request storms.

### ⚡ 2. Eliminating the Parallel ID Resolution Storm
- **Root Cause:** 164 parallel plugins were each triggering separate ARM+TMDB ID lookups (up to 8 consecutive HTTP calls per plugin), wasting the 75-second timeout budget before searches began.
- **Fix:** Pre-resolved IDs (`preResolvedIds`) are now passed directly from `StreamViewModel`. Added `resolvedIdsCache` in `KitsugiIdResolver`. Duplicate per-plugin network requests dropped to zero.

### 🎬 3. Flexible Anime Type Filter
- Allowed Turkish TV series and movie providers to participate in anime searches, enabling access to Turkish dubbed anime and anime movies hosted on general streaming sites.

### 🛡️ 4. Title Matcher Precision & ProGuard Gson Keep
- **Exact Match Guard:** High-confidence candidate matches ($\ge 0.90$) are now protected from being discarded by multi-script alias noise (e.g. bilingual titles).
- **Gson ProGuard Rules:** Added `-keep` rules for `com.google.gson.**` to prevent `NoClassDefFoundError: Lcom/google/gson/Gson` in plugins like Torrential.

### 🧪 5. Plugin Diagnostics — Phase 2g Real Flow Testing
- Diagnostic runner now tests the complete real stream discovery pipeline using the plugin's own returned title, adding a `Flow` column (`🔁✅` / `🔁❌`) in the report and screen.
