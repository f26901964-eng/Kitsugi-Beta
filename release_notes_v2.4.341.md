# Kitsugi Beta v2.4.341

## 🇹🇷 Türkçe

### 🧲 Torrent Kaynaklarında Seeder / Peer Rozetleri (Sayısal + İkonlu)
- **Tek ayrıştırıcı (`TorrentSwarmStats.kt`):** Torrentio ve türevi eklentilerin swarm bilgisi artık gerçekten okunuyor: `👤 124`, `👥 124`, `🌱 124`, `S:124`, `Seeders: 124`, `124↑` gibi biçimler ve `seeders` / `peers` / `leechers` JSON alanları desteklenir.
- **Kompakt sayı biçimi:** 1.200 → `1.2K`, 1.500.000 → `1.5M`. Sayı yoksa **hiçbir rozet basılmaz** (uydurma "0 seeder" yok).
- **Sağlık rengi:** Seeder rozeti yeşil (≥20) → sarı (≥5) → turuncu (≥1) → kırmızı (0) olarak renklenir; peer rozeti mavi ve aşağı ok ikonludur. İkon tek başına bırakılmaz, sayı her zaman yazılır.
- **Sıralama:** Aynı kalitedeki torrent'ler arasında yüksek seeder öne çıkar; seeder bildirmeyen kaynaklar cezalandırılmaz. Yeni **"Tohum sayısı"** sıralama modu eklendi (Varsayılan → Kalite → Tohum).

### 💾 Her Kaynakta İndirme Boyutu — Butona Basmadan Önce
- **`StreamSource` artık gerçek bayt taşıyor:** Stremio `behaviorHints.videoSize` ve CloudStream doğrulama aşamasında yapılan HTTP `HEAD` yanıtındaki `Content-Length` yakalanıp `videoSizeBytes` alanına yazılıyor. Ek ağ isteği yapılmaz — zaten yapılan canlılık kontrolü kullanılır.
- **Dosya adı önceliği:** `behaviorHints.filename` saklanıyor; kalite/boyut/codec/dil çıkarımı artık dosya adı → başlık → isim sırasıyla yapılıyor. Kısaltılmış başlıklarda kaybolan bilgiler kurtarılıyor (ör. `...S06E17.1080p.WEB-DL.1.72GB.mkv`).
- **Görünür kapsam:** Liste başlığında `30 video · 3 kaynak · 12/30 boyut biliniyor`, kaynak seçicide bilinen boyutların toplamı (`18.4 GB`) gösteriliyor.
- **Yeni filtre:** `İndirme boyutu` çipi yalnızca boyutu gerçekten bilinen kaynakları listeler. Arama alanı da boyut etiketiyle (`1.7 gb`) eşleşir.
- **Bilinmeyen boyut dürüstçe yazılır:** Torrent kartlarında boyut yoksa "Boyut bilinmiyor" yazar; uydurma GB/MB basılmaz. HLS yayınlarında toplam boyut playlist'te olmadığı için tahmin edilmez.

### 🗣️ Ses & Altyazı Rozetleri Tüm Eklentilerden
- **Tek rozet bileşeni (`StreamInfoChips.kt`):** Kalite, ses türü (Dublaj/Altyazılı/Çift dil), boyut, seeder/peer, ses dilleri, altyazı dilleri, codec (`HEVC`, `H.264`, `AV1`, `VP9`), HDR (`DV`, `HDR10+`, `HDR10`), kaynak türü (`REMUX`, `BluRay`, `WEB-DL`) ve önbellek/P2P durumu tek yerden üretilir.
- **Nerede görünür:** İzle sayfası kartları, TV (Android TV) kaynak satırları, detay sayfası kaynak seçici ve **oynatıcı içi "Kalite / Kaynak" sayfası** — hepsi aynı bileşeni kullanır.
- **Yeni kanıt kaynakları:** CloudStream `ExtractorLink.audioTracks` sayısı kaydediliyor (≥2 ayrı ses parçası = "Çift dil"), HLS master playlist'indeki `#EXT-X-MEDIA:TYPE=AUDIO/SUBTITLES` dilleri ve `CODECS` alanı okunuyor (ek istek yok).
- **Kural korunuyor:** Kanıt yoksa rozet basılmaz. "Türkçe eklenti ⇒ altyazılı" gibi eski varsayımlar geri getirilmedi.

### 🧩 Tutarlılık Düzeltmeleri
- **Tek Stremio dönüşümü (`toStreamSource`):** Detay sayfası ile izleme ekranı iki ayrı eşleme kullanıyordu; birinde boyut/seed bilgisi geliyor, diğerinde kayboluyordu. Artık tek fonksiyon var ve iki yol da onu kullanıyor.
- **TV rozetleri genişletildi:** Android TV satırında yalnızca eklenti adı + boyut vardı; artık tüm rozetler görünüyor.
- **Ölçüm verimliliği:** Boyut bilgisi eksik olan progressive (mp4/mkv) kaynaklarda `HEAD` ölçümü tetiklenir; HLS'te boyut üretilemeyeceği için gereksiz istek yapılmaz. Görüş alanı dışındaki kartlar hâlâ ölçüm yapmaz (kaydırma kasması yok).

---

## 🇬🇧 English

### 🧲 Seeders / Peers on Torrent Sources (Numeric + Icon)
- **Single parser (`TorrentSwarmStats.kt`):** Swarm data from Torrentio-style addons is parsed for real: `👤 124`, `👥 124`, `🌱 124`, `S:124`, `Seeders: 124`, `124↑` formats plus `seeders` / `peers` / `leechers` JSON fields.
- **Compact numbers:** 1,200 → `1.2K`, 1,500,000 → `1.5M`. No number ⇒ **no badge** (never a fabricated "0 seeders").
- **Health colours:** Green (≥20) → yellow (≥5) → orange (≥1) → red (0); peers are blue with a down-arrow icon. Icons never stand alone — the number is always printed.
- **Sorting:** Within the same quality, higher seeders rank first while sources that omit the number are not penalised. New **"Seeders"** sort mode (Default → Quality → Seeders).

### 💾 Download Size for Every Source — Before Tapping Download
- **`StreamSource` now carries real bytes:** Stremio `behaviorHints.videoSize` and the `Content-Length` header of the existing HTTP `HEAD` liveness check are captured into `videoSizeBytes`. No extra request is made.
- **Filename priority:** `behaviorHints.filename` is stored; quality/size/codec/language extraction now uses filename → title → name, recovering data lost in truncated titles (e.g. `...S06E17.1080p.WEB-DL.1.72GB.mkv`).
- **Visible coverage:** `30 videos · 3 sources · 12/30 sizes known` in the list header and total known size (`18.4 GB`) in the source picker.
- **New filter:** the `Download size` chip keeps only sources with a known size; search matches size labels too (`1.7 gb`).
- **Honest unknowns:** torrent cards show "Size unknown" when nothing is known — never an invented GB/MB. HLS totals are not guessed because the playlist lacks them.

### 🗣️ Audio & Subtitle Badges From Every Extension
- **Single badge component (`StreamInfoChips.kt`):** quality, audio kind (Dubbed/Subbed/Dual), size, seeders/peers, audio languages, subtitle languages, codec (`HEVC`, `H.264`, `AV1`, `VP9`), HDR (`DV`, `HDR10+`, `HDR10`), source type (`REMUX`, `BluRay`, `WEB-DL`) and cache/P2P state come from one place.
- **Where it shows:** watch page cards, Android TV rows, the detail-page source picker and the **in-player "Quality / Source" sheet** — all share the same component.
- **New evidence sources:** CloudStream `ExtractorLink.audioTracks` count is stored (≥2 separate audio tracks ⇒ "Dual"), and HLS `#EXT-X-MEDIA:TYPE=AUDIO/SUBTITLES` languages plus `CODECS` are read from the master playlist (no extra requests).
- **Rule preserved:** no evidence ⇒ no badge. Legacy guesses like "Turkish addon ⇒ subtitled" were not reintroduced.

### 🧩 Consistency Fixes
- **One Stremio mapping (`toStreamSource`):** the detail page and the watch screen used different mappings, so size/seed information appeared on one and vanished on the other. Both paths now share a single function.
- **TV badges expanded:** Android TV rows previously showed only the addon name and size; all badges are now visible.
- **Probe efficiency:** missing-size progressive (mp4/mkv) sources trigger the `HEAD` probe, while HLS avoids pointless requests; off-screen cards still do not probe (no scroll jank).
