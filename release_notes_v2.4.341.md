# Kitsugi Beta v2.4.341

## 🇹🇷 Türkçe

### 🎚️ Kalite Sayfası Artık Gerçek Kaliteleri Gösteriyor (rapidrame / closeload)

**Şikâyet:** *"Kalite sayfası 'Bu video veya oynatıcı motoru seçebilir kalite sunmuyor' diyor; HDFilmCehennemi ve FilmMakinesi'nden gelen rapidrame kaynağının kaliteleri hiç görünmüyor."*

**Kök neden:** Bu eklentiler tek bir **`master.m3u8`** linki veriyor ve kaliteyi `Qualities.Unknown` olarak bildiriyor:

```
Rapid CDN  : sNNN.rapidrame.com/hls2/.../master.m3u8?t=<token>
Close CDN  : srvN.cdnimagesNNNN.cyou/hls/<ad>.mp4/txt/master.txt
```

Gerçek kalite listesi yalnızca playlist'in **içinde** duruyor (`#EXT-X-STREAM-INF:...RESOLUTION=1920x1080`). Uygulama bu satırları hiç okumadığı için liste boş kalıyordu.

#### Yapılanlar
- **Yeni `HlsMasterPlaylist` ayrıştırıcısı** (`core/player/HlsMasterPlaylist.kt`): `BANDWIDTH`, `AVERAGE-BANDWIDTH`, `RESOLUTION`, `CODECS`, `NAME` alanlarını okur; her varyantın **oynatılabilir** URL'sini master'a göre mutlaklaştırır.
  - İmzalı CDN'lerde (`...master.m3u8?t=<token>`) göreli varyant satırı sorgu taşımıyorsa **master'ın token'ı korunur** (aksi hâlde CDN 403 döner ve seçim "oynatıcı hatası" gibi görünür).
  - `CODECS` yalnızca ses kodeği içeren varyantlar (`mp4a`, `ac-3`, `ec-3`…) kalite listesine **girmez**.
  - Aynı çözünürlükte birden fazla varyant varsa **en yüksek bit hızlısı** kalır; liste yüksekten alçağa sıralanır.
  - `RESOLUTION` yoksa yükseklik `NAME` veya URI ipucundan (`index_1080.m3u8`) çıkarılır; hiçbiri yoksa **uydurma etiket basılmaz**.
- **`StreamProbe` varyant ölçümü:** master playlist bir kez indirilir, varyantlar URL bazlı önbelleğe alınır (`clearCache()` ikisini birlikte temizler). Uzantısı `m3u8` olmayan **`master.txt`** (closeload) adresleri de içerik `#EXTM3U` ise ayrıştırılır.
- **CS kaynakları oynatıcıyla aynı istemciyi kullanır:** ölçüm artık eklentinin `getVideoInterceptor()` başlıklarını, paylaşılan çerezleri (Cloudflare) ve SSL bypass'ı kullanan istemciyle yapılır. Liste sayfasında başarısız olmuş eski ölçüm, oynatıcı bağlamında **bir kez yeniden denenir**.
- **Kalite sayfası yenilendi:**
  - **Kalite** bölümü: `Otomatik (en iyi kalite)` + ölçülen her varyant (`1080p`, `720p`, `480p`) ve bit hızı.
  - **Kaynak** bölümü: eklenti kaynak listesi (eski davranış korunur).
  - Ölçülebilir varyant yoksa uydurma liste basılmaz; nedeni açıklayan metin gösterilir.
- **Seçim anında uygulanır:** seçilen varyantın URL'si ile aynı başlıklar/altyazılar kullanılarak oynatma **mevcut saniyeden** sürer (`changeStreamSource` + `pendingSourceSwitchResumeMs`). "Otomatik" seçildiğinde master (adaptif) akışa dönülür.

### 🧪 Doğrulama
- `HlsMasterPlaylistTest`: gerçek rapidrame master örneği, medya playlist (boş sonuç), token devralma, mutlak URL, yalnızca-ses varyantı eleme, aynı çözünürlük tekilleştirme, isimden yükseklik çıkarma, bit hızı biçimlendirme.
- Statik denge/parantez taraması tüm değişen dosyalarda yapıldı (7 dosya değişti, 2 yeni dosya).

---

## 🇬🇧 English

### 🎚️ Quality sheet now lists real qualities (rapidrame / closeload)

- New `HlsMasterPlaylist` parser extracts real `#EXT-X-STREAM-INF` variants (resolution, bandwidth, codecs, playable URL) from master playlists; signed CDN tokens are preserved when a relative variant URI has no query string.
- Audio-only renditions are filtered out; duplicates per resolution collapse to the highest bandwidth; heights fall back to `NAME`/URI hints and are never invented.
- `StreamProbe` now measures variants with the **same HTTP client as the player** (plugin video interceptor, shared cookies, SSL bypass) and re-tries a previously failed probe once in player context. `master.txt` (closeload) playlists are parsed when their body is `#EXTM3U`.
- The sheet lists `Auto (best quality)` plus every measured variant, and keeps the source list below. Selecting a quality reloads the same source at the current position.
