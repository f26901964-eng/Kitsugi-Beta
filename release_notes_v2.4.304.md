# Kitsugi Beta v2.4.304

## 🇹🇷 Türkçe

### 🎬 Close & Rapid Kaynakları Artık Uygulama Yerleşiki Olarak Çözülüyor
- **Yerleşik Close/Rapid Çözücüsü (`KitsugiCdnJwExtractor`):** HDFilmCehennemi ve
  FilmMakinesi videolarının geldiği gömülü CDN ailesi (`closeload.*`, `rapidrame.*`,
  `rplayer.*`) için uygulama kendi çözücüsünü kazanıyor. JWPlayer `dc_` JavaScript
  şifresi native olarak çözülüyor; JSON-LD `contentUrl` ve `master.txt`/`.m3u8`
  fallback'leri korunuyor; master listesinden en yüksek çözünürlüklü varyant seçiliyor
  ve `Origin`/`Referer` başlıkları GERÇEK yayın host'una sabitleniyor (yanlış Origin
  CDN'de 5004 düşmesine yol açıyordu). JWPlayer `tracks:` bloğundaki altyazılar da
  ayıklanıp oynatıcıya veriliyor.
- **FilmMakinesi "DUAL Rapid" Artık Link Üretiyor:** `rapidrame` iframe'leri eklenti
  tarafında `loadExtractor`'a bırakılıyor ama hiçbir eklenti bu host'u tanımıyordu;
  uygulama yerleşikleri CS `extractorApis` listesine kaydedildiği için eklenti
  sürümünden bağımsız olarak Rapid kaynakları da çözülüyor.
- **Bayat Eklenti Döngüsü Kırıldı:** `plugins.json`'daki FilmMakinesi kaydı v62'ye,
  HDFilmCehennemi kaydı v52'ye (gerçek `.cs3` boyut + SHA-256 değerleriyle) senkronlandı.
  Cihazlarda kalan v60/v50 kurulumlar artık güncelleme denetiminde takılmayıp yeni
  derlemeleri indirebiliyor.
- **`/hls/.../*.mp4` Düz Dosyalar Yeniden HLS Sanılmıyor:** `isHlsUrl` sıralaması
  "kesin manifest belirteçleri → progressive uzantı → zayıf yol/host belirteçleri"
  olarak düzeltildi; closeload/rapidrame CDN'lerinin `/hls/` altındaki düz MP4/MKV
  dosyaları artık doğru MIME ile oynatılıyor.
- **Canlılık Doğrulaması Güvenilirleşti:** HTTP HEAD doğrulaması kaynağın tüm başlıkları
  (`Origin` dahil) ile yapılıyor; 404 dönen CDN'lerde kaynak düşürülmeden önce GET+Range
  ile ikinci şans veriliyor.

### 🧪 Testler
- `KitsugiCdnJwExtractorTest`: sentetik şifre turu, varyant seçimi, altyazı filtreleme,
  origin/header üretimi.
- `PlayerHlsDetectionTest`: yeni `isHlsUrl` sıralaması regresyonları.

### ⚠️ Notlar
- Eklenti deposundaki (`Kitsugi-Plugins`) FilmMakinesi düzeltmesi kaynak düzeyinde
  hazırlandı (`arena/72da1e55-kitsugi-plugins`); `.cs3` derlemesi bu ortamda
  yapılamadığı için yama `Kitsugi-Plugins_FilmMakinesi_rapid_fix.patch` olarak bu
  depoya eklendi. Cihazlardaki çalışma zamanı açığını yukarıdaki yerleşikler kapatıyor.
- `builds` dalındaki diğer ~100 eklentinin manifest/hash sürüklenmesi bilinçli olarak
  bu sürümün kapsamına alınmadı (toplu senkron ayrı görev).

---

## 🇬 English

### 🎬 Close & Rapid Sources Now Resolved by Built-in App Extractors
- **Built-in Close/Rapid extractor (`KitsugiCdnJwExtractor`):** the embedded CDN family
  (`closeload.*`, `rapidrame.*`, `rplayer.*`) serving HDFilmCehennemi and FilmMakinesi is
  now resolved natively by the app: JWPlayer `dc_` JS decryption, JSON-LD `contentUrl`
  and `master.txt`/`.m3u8` fallbacks, highest-resolution variant picking from the master
  playlist, and `Origin`/`Referer` pinned to the real streaming host (wrong Origin caused
  CDN 5004 drops). JWPlayer `tracks:` subtitles are extracted too.
- **FilmMakinesi "DUAL Rapid" now yields links:** `rapidrame` iframes were handed to
  `loadExtractor`, which no plugin registers; the app-registered extractors close the gap
  regardless of plugin version.
- **Stale-plugin loop broken:** `plugins.json` entries synced to the real served builds
  (FilmMakinesi v62, HDFilmCehennemi v52 with true size/SHA-256), so devices stuck on
  v60/v50 finally receive updates.
- **`/hls/.../*.mp4` no longer misdetected as HLS:** `isHlsUrl` check order fixed
  (strong manifest markers → progressive extension → weak path/host markers).
- **Liveness validation hardened:** HEAD probes now carry all stream headers (including
  `Origin`); on 404 a GET+Range retry runs before dropping the source.
