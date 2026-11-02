# Kitsugi Beta v2.4.342

## 🇹🇷 Türkçe

### 🎚️ Kalite Okuma Kapsamı Genişledi: HLS **+ DASH** + kaynak bildirimleri

v2.4.341'de HLS master playlist'ten gerçek kalite listesi çıkarılıyordu. Bu sürüm kalan
boşluğu kapatıyor: **DASH (`.mpd`)** akışları.

#### Yeni
- **`DashManifest` ayrıştırıcısı:** manifest içindeki video `AdaptationSet` →
  `Representation` düğümlerinden `height / width / bandwidth / codecs` okunur.
  - `mimeType="audio/..."` olan AdaptationSet'ler ve `mp4a / ac-3 / ec-3 / opus…`
    kodekli Representation'lar **video kalitesi sayılmaz**.
  - Aynı yükseklikte en yüksek bit hızı kalır; liste yüksekten alçağa sıralanır.
  - Bozuk/eksik XML'de bile çalışsın diye satır bazlı toleranslı ayrıştırma kullanılır
    (XML parser tüm manifesti reddedebiliyordu).
- **`QualityOption` modeli:** kalite sayfası artık iki uygulama biçimini tek listede taşır:
  - `switchUrl` dolu → HLS varyantı; seçimde **URL değiştirilir** (kaldığın saniyeden).
  - `switchUrl` boş → DASH; seçim **motorun çözünürlük tavanı** ile uygulanır.
- **`PlayerEngine.setMaxVideoHeight(height)`** (varsayılan no-op, Media3 uygular):
  `TrackSelectionParameters.maxVideoSize` ile tavan konur; motor yeniden kurulsa bile korunur.
  HLS varyantına geçildiğinde tavan otomatik kaldırılır.
- **`StreamProbe.qualityOptions()`:** HLS + DASH için tek giriş noktası, URL bazlı LRU önbellek
  (`clearCache()` hepsini temizler).
- DASH manifestleri kart listesinde rozet amacıyla indirilmez (`isProbeable = false`),
  yalnızca kalite sayfası açıldığında bir kez okunur.

#### Özet tablo
| Kaynak | Kalite okunur mu? | Uygulama |
|--------|-------------------|----------|
| HLS master (`master.m3u8`, `master.txt`) | ✅ | Varyant URL'sine geçiş |
| DASH (`.mpd`) | ✅ | Çözünürlük tavanı (Media3) |
| Tek kaliteli HLS / progressive mp4 | ➖ | Seçenek yok, kaynak rozeti kalır |
| Eklentinin ayrı HD/SD linkleri | ✅ | "Kaynak" bölümünde |

### 🧪 Doğrulama
- `DashManifestTest` (6 test): sıralama, ses ayrımı, kodek filtresi, tekilleştirme, DASH olmayan
  gövde, kararlı anahtar.
- `HlsMasterPlaylistTest` (9 test) korunuyor.

---

## 🇬🇧 English

### 🎚️ Quality reading now covers HLS **+ DASH** + source metadata
- New `DashManifest` parser reads video `Representation` heights/bandwidth from `.mpd`, skips
  audio adaptation sets and audio-only codecs, dedupes per resolution and sorts high→low.
- New `QualityOption` model: HLS variants switch URL; DASH qualities apply a resolution cap via
  `PlayerEngine.setMaxVideoHeight` (Media3 `maxVideoSize`, survives player re-creation).
- `StreamProbe.qualityOptions()` is the single entry point for both, with URL-keyed LRU caching;
  DASH manifests are not fetched for list badges.
- Tests: `DashManifestTest` (6), `HlsMasterPlaylistTest` (9).
