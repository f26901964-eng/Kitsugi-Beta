# Kitsugi Beta v2.4.343

## 🇹🇷 Türkçe

### 🔍 Kalite Okumada Kalan 4 Boşluk Kapatıldı

v2.4.341–342'de HLS ve DASH eklendi. Bu sürüm "atladığımız bir şey kaldı mı?" denetiminin
sonuçlarını uygular.

| # | Boşluk | Çözüm |
|---|--------|-------|
| 1 | Eklenti kaliteyi **zaten bildiriyor** ama manifest ayrıştırılamıyor → sayfa "kalite yok" diyordu | Ölçüm boşsa kaynağın bildirdiği kalite (`ExtractorLink.quality`, Stremio başlığı) **tek satır** olarak gösterilir: *"Kaynağın bildirdiği kalite"*. Seçim yapılamaz, ama bilgi kaybolmaz. |
| 2 | Uzantısız/garip linkler (`/play/abc`) manifest olabilir ama hiç denenmiyordu | **İçerik koklama:** böyle linklerde gövde okunur; `<MPD` → DASH, `#EXTM3U` → HLS olarak ayrıştırılır. Progressive `mp4/mkv/webm` koklanmaz (gereksiz istek yok). |
| 3 | Aynı çözünürlükte H.264 + HEVC varsa yüksek bit hızlı HEVC seçiliyordu (HEVC çözücüsü olmayan cihazda ses var görüntü yok) | **AVC tercih edilir:** eşit yükseklikte `avc1/avc3` varyantı kazanır; AVC yoksa yüksek bit hızı kalır. |
| 4 | DASH kalite sabitleme MPV'de sessizce yutuluyordu | Seçim Media3 dışındayken kullanıcı bilgilendirilir (*"kalite sabitleme şu an yalnızca Media3'te"*); HLS varyant seçimi MPV'de de çalışır (URL değişir). |

### ➕ Ek iyileştirme
- **Kaynak listesinde kalite rozetleri:** "Kaynak" bölümündeki her satır artık eklentinin bildirdiği
  kaliteyi (`1080p`, `720p`…) gösterir; aynı eklentinin HD/SD linkleri arasında seçim kolaylaşır.

### 🧪 Doğrulama
- `HlsMasterPlaylistTest` +1 test (AVC/HEVC eşit yükseklikte AVC kazanır) → 10 test.
- `DashManifestTest` 7 test (minified MPD dahil).
- `QualityOption.isSelectable` / `declaredBySource` ile arayüz tarafında seçilemez satır desteği.

---

## 🇬🇧 English

### 🔍 Four remaining quality-reading gaps closed
1. When a manifest cannot be parsed but the **provider already declares** a quality, it is shown as a
   single non-selectable row ("Quality declared by source") instead of an empty state.
2. Extension-less links (`/play/abc…`) are **content-sniffed**: `<MPD` → DASH, `#EXTM3U` → HLS.
   Progressive `mp4/mkv/webm` is never sniffed.
3. At equal height **AVC wins over HEVC** (avoids "audio only, no video" on devices without an HEVC decoder).
4. DASH quality pinning is Media3-only; the user is informed instead of the selection being silently dropped.
   HLS variant switching works on both engines.
- Source rows now display the provider-declared quality label.
