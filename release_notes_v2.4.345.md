# Kitsugi Beta v2.4.345

## 🇹🇷 Türkçe

### 🛡️ Kalite Zinciri Toplu Sağlamlaştırma (tek tek değil, uçtan uca)

v2.4.341–344 arasında kalite okuma zinciri tamamlandı. Bu sürüm, zincirin **oluşabilecek
her kırılma noktasını** önceden kapatır.

#### 1. Ayrı ses grubu olan HLS: ses kaybı engellendi
Master'da `#EXT-X-MEDIA:TYPE=AUDIO,…,URI="audio/tr.m3u8"` varsa varyant playlist'ine geçmek
**videoyu getirir, sesi kaybeder**. Artık böyle varyantlarda seçim **URL değiştirmez**;
oynatıcı çözünürlük tavanıyla uygular → master adaptif kalır, ses korunur.
(`HlsVariant.requiresMasterAudio` → `QualityOption.switchUrl = null`)

#### 2. Süresi dolmuş varyant linki: kilitlenme yok
İmzalı CDN token'ı (`?t=…`) süresi dolduğunda veya 403 geldiğinde oynatıcı **bir kez**
otomatik olarak kaynağın master akışına döner, aynı çözünürlük tavanını korur, hata ekranını
temizler ve kullanıcıyı bilgilendirir. Aynı URL'de döngüye girmez.

#### 3. Geçici hata artık kalıcı "kalite yok"a dönüşmüyor
Boş/başarısız ölçüm sonuçları önbelleğe **yazılmaz**; kalite sayfası açıldığında liste boşsa
yeniden ölçülür. (Eskiden tek bir 403/kopuntu, uygulama yeniden açılana kadar listeyi boş
bırakıyordu.)

#### 4. Gereksiz istek / gecikme yok
Manifest "koklama" artık P2P yerel sunucusunda (`127.0.0.1`, `localhost`), segment
(`.ts/.m4s`) ve medya/görsel uzantılarında (`mp4/mkv/mp3/jpg…`) çalışmaz.

#### 5. MPV'de uygulanamayan seçim işaretlenmiyor
DASH ya da ayrı ses gruplu HLS gibi **yalnızca motor tavanıyla** uygulanabilen kaliteler
MPV'de seçili gösterilmez; nedeni açıklanır. HLS varyantları MPV'de URL değişimiyle çalışmaya
devam eder.

#### 6. Motor değişiminde tavan korunur
Media3 ↔ MPV geçişinde seçilen çözünürlük tavanı yeni motora yeniden uygulanır.

#### 7. Hata durumu temizliği
`KitsugiPlayerViewModel.clearPlayerError()` eklendi: otomatik kaynak dönüşünde hata ekranı
arkada kalmaz.

### 📋 Denetim çıktısı
`KALITE_ZINCIRI_TAM_DENETIM_2026-11-02.md`: 17 bulgu (hepsi düzeltildi), 12 doğrulanmış
" sorun değil" noktası, 4 bilinçli sınır (tek kaliteli kaynak, DRM, MPV+DASH, canlı radyo).

### 🧪 Testler
- `HlsMasterPlaylistTest` **12 test** (+2: ayrı ses grubu URL değiştirmez, ses grubu yoksa değiştirir)
- `DashManifestTest` 7 test
- `QualityProfileTest` +2 test

---

## 🇬🇧 English

### 🛡️ End-to-end hardening of the quality pipeline
1. **HLS with a separate audio rendition** — switching to a variant URL used to lose audio;
   such variants now apply a resolution cap instead (master + audio preserved).
2. **Expired variant token/403** — one-shot automatic fallback to the master stream with the
   same cap, error state cleared, user informed, no loop.
3. **No more permanently empty lists** — empty/failed probes are not cached and the sheet
   re-probes on open.
4. **Sniffing is filtered** — skipped for localhost/P2P, segments and media/image extensions.
5. **MPV** — cap-only qualities are no longer marked as selected; the reason is explained.
6. **Engine switch** — the resolution cap is re-applied to the new engine.
7. **`clearPlayerError()`** — error state is reset on automatic source fallback.

See `KALITE_ZINCIRI_TAM_DENETIM_2026-11-02.md` for the full audit (17 findings fixed).
