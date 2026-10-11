# Kitsugi-Beta v2.4.250 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🔬 0. Bu Sürüm Nedir? (Strateji Denetimi — 2. Tur)

v2.4.249 eklenti-uygulama karşılaştırmasından sonra gelen haklı soru: *"Sağlam eklentiden bile veri gelmiyorsa sorun BİZDE olabilir mi? Otomatik bulma + sezon/bölüm hattını test ettin mi?"* Bu sürüm o denetimin sonucudur: detay sayfasındaki İzle/Bölümler akışının tamamı (arama → eşleştirme → sezon/bölüm → link çıkarma) satır satır denetlendi, eşleştirme stratejisi 7 gerçekçi senaryoyla simüle edildi. **Sonuç: 2 yerde BİZ hatalıymışız — ikisi de düzeltildi.** Ayrıca artık her eklenti, cihazda, BİZİM hattımızdan otomatik test edilebiliyor (faz 2g).

### 🚨 1. ID Çözümleme Fırtınası Düzeltildi (ana veri kaybı)

- **Kök neden:** `runGetStreams` HER eklenti için (164 paralel!) ARM+TMDB ID çözümlemesi yapıyordu — eklenti başına 8'e kadar ardışık HTTP, önbellek YOK. İzle'ye her basışta ~1000 istek; 75 sn'lik provider bütçesi arama başlamadan eriyor, sağlıklı eklentiler bile zaman aşımına düşüyordu.
- **Düzeltme (iki katman):** (a) `StreamViewModel` zaten çözdüğü ID'leri `preResolvedIds` ile aynen geçiriyor — ana akışta eklenti başına ID ağı SIFIR; (b) `KitsugiIdResolver.resolveIds` sonuç önbelleğine alındı (yalnızca zenginleşmiş sonuçlar; geçici arızalar tekrar deneyebilir).
- **Dosyalar:** `KitsugiIdResolver.kt`, `CsStreamRunner.kt`, `StreamViewModel.kt`.

### 📺 2. Anime Tip Filtresi Gevşetildi

- **Kök neden:** Anime detay sayfasında `TvSeries`/`Movie` bildiren eklentiler ("Bu eklenti anime içeriklerini desteklemiyor") eleniyordu — oysa TR dizi siteleri dublajlı anime, film siteleri anime filmleri barındırır.
- **Düzeltme:** Anime satırı `tvseries/tv series/asiandrama/documentary/movie` türlerini de kabul ediyor. İlgisiz sonuçlar zaten başlık/tür eşleştiricide eleniyor; burada engellemek saf veri kaybıydı.
- **Dosya:** `CsPluginTypeCompatibility.kt`.

### 🔁 3. Tanı Koşucusuna Gerçek-Akış Testi (faz 2g)

- Eklenti tanı ekranındaki test artık her eklenti için 7. bir faz çalıştırıyor: eklentinin KENDİ döndürdüğü başlık, bizim `getStreams` hattımızdan (otomatik bulma + S1E1 eşleştirme) geçiriliyor.
- Tesisat çalışıp bu faz boş dönerse suçlu eklenti değil BİZİM eşleştiricimizdir — raporda `Flow` sütunu ve `flow` faz hatası olarak görünür (`EŞLEŞME YOK` = bizim hatamız).
- **Dosyalar:** `CsPluginDiagnosticRunner.kt`, `CsPluginDiagnosticScreen.kt`.

### ✅ 4. Strateji Simülasyonu (7 senaryo — hepsi belgelendi)

Kotlin eşleştiricinin birebir Python portu, gerçekçi TR eklenti çıktılarıyla test edildi: dizi S1E1 (1.00 kanarya: eski guard reddediyor, v2.4.249 kabul ediyor), dizi S2E3, anime mutlak numaralama (Shippuden eşleşiyor, çıplak Naruto eleniyor, E5 bulunuyor), film eşleşme, gürültülü başlık (`Türkçe Dublaj Full HD izle` → eşleşiyor). **Bilinen boşluk:** site SADECE Türkçe başlık taşıyorsa (`Örümcek Adam Eve Dönüş Yok`) eşleşme yok — detay sayfası Türkçe başlık göndermiyor. Çözüm (sonraki sürüm): TMDB'den `tr-TR` başlık çekip varyantlara eklemek.

## 🇬🇧 English

### 🔬 0. What Is This Release? (Strategy Audit — Round 2)

Follow-up to v2.4.249: *"If even healthy plugins return nothing, maybe WE are wrong. Did you test our auto-find + season/episode pipeline?"* The full Watch/Episodes flow was audited line by line and the matching strategy simulated across 7 realistic scenarios. **Result: WE were wrong in 2 places — both fixed.** Plus every plugin can now be self-tested on-device through OUR pipeline (phase 2g).

### 🚨 1. ID Resolution Storm Fixed (main data loss)

- **Root cause:** `runGetStreams` resolved ARM+TMDB IDs PER PLUGIN (164 parallel, up to 8 sequential HTTP each, no cache) — ~1000 requests per Watch tap, burning the 75s provider budget before search started.
- **Fix (two layers):** (a) `StreamViewModel` passes already-resolved IDs via `preResolvedIds` — zero per-plugin ID network on the main flow; (b) `KitsugiIdResolver.resolveIds` result cache (enriched results only).

### 📺 2. Anime Type Filter Relaxed

- Anime detail pages skipped `TvSeries`/`Movie` plugins, yet TR series sites host dubbed anime and movie sites host anime films. The Anime row now accepts those types; irrelevant results are still filtered by the title/type matcher.

### 🔁 3. Real-Flow Test in Diagnostic Runner (phase 2g)

- Each plugin's OWN returned title is now run through our `getStreams` pipeline. Plumbing-pass + flow-fail = OUR matcher is guilty, shown as the `Flow` column and `flow` phase errors.

### ✅ 4. Strategy Simulation (7 scenarios)

A faithful Python port of the Kotlin matcher was tested with realistic TR outputs — all pass except the known gap: Turkish-only site titles (fix planned: fetch `tr-TR` titles from TMDB into query variants).
