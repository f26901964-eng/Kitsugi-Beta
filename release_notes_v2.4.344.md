# Kitsugi Beta v2.4.344

## 🇹🇷 Türkçe

### ⚙️ Ayarlardaki "Kalite Profili" Artık Gerçekten Uygulanıyor

**Denetimde çıkan gerçek eksik:** `QualityProfile` (1080p+ / 720p / 480p / Veri Tasarrufu +
maks. bit hızı) yalnızca **kaydediliyor ve ekranda gösteriliyordu** — oynatıcı bu değeri hiç
okumuyordu. Yani kullanıcı "720p tercih et" diyor, video yine 1080p açılıyordu.

#### Yapılanlar
- **`QualityPreference.maxVideoHeight` / `QualityProfile.effectiveMaxVideoHeight`**:
  tercih → gerçek çözünürlük tavanı eşlemesi (`AUTO/P1080 → sınırsız`, `720p → 720`,
  `480p → 480`, `Veri tasarrufu → 360`).
- **Media3:** `TrackSelectionParameters.maxVideoSize` + `maxVideoBitrate` artık kalite profilinden
  türetiliyor. `prepare()` her çağrıldığında tazelenir → ayar değişikliği uygulamayı yeniden
  başlatmadan geçerli olur. Öncelik: **elle seçilen kalite → kalite profili → sınırsız**.
- **MPV:** `hls-bitrate` MPV seçeneği profile göre yazılır (`Veri tasarrufu/480p → min`,
  `1080p+ → max`, diğer → auto).
- **Kalite sayfası:** "Otomatik (en iyi kalite)" satırının altında **"Kalite profili: 720p"**
  ipucu görünür — kullanıcı neden daha düşük çözünürlük aldığını görür.

### 🔎 Denetim özeti — kalite okuma zinciri artık uçtan uca kapalı
| Katman | Durum |
|--------|-------|
| HLS master (`master.m3u8`, `master.txt`) | ✅ varyant URL'siyle seçim |
| DASH (`.mpd`, minified) | ✅ Media3 tavanıyla seçim |
| Uzantısız linkler | ✅ içerik koklama (`<MPD` / `#EXTM3U`) |
| Eklentinin HD/SD linkleri | ✅ "Kaynak" bölümü + kalite rozeti |
| Kaynağın bildirdiği tek kalite | ✅ bilgi satırı (seçilemez) |
| Eşit yükseklikte kodek seçimi | ✅ AVC öncelikli (HEVC çözücü riski yok) |
| **Ayarlardaki kalite profili** | ✅ **artık motora uygulanıyor** (Media3 + MPV) |

### 🧪 Doğrulama
- `QualityProfileTest` +2 test (tercih → tavan eşlemesi, etkin limitler).
- Toplam: `HlsMasterPlaylistTest` 10, `DashManifestTest` 7, `QualityProfileTest` +2.

---

## 🇬🇧 English

### ⚙️ The "Quality Profile" setting is now actually applied
- `QualityProfile` was stored and displayed but never read by the player. Added
  `maxVideoHeight`/`effectiveMaxVideoHeight`/`effectiveMaxBitrateKbps` mappings.
- Media3: `maxVideoSize` + `maxVideoBitrate` derived from the profile, refreshed on every
  `prepare()`; priority is manual pick → profile → unlimited.
- MPV: `hls-bitrate` (`min` for data saver/480p, `max` for 1080p+, `auto` otherwise).
- Quality sheet shows "Quality profile: 720p" under the Auto row.
