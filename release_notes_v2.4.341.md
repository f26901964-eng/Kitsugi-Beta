# Kitsugi Beta v2.4.341

## 🇹🇷 Türkçe

### 🎚️ Kalite ve Kaynak Artık AYRI — Yanlış Yere Yapılan Birleştirme Geri Alındı
- **Sorun:** Oyuncudaki sheet "Kalite / Kaynak" adıyla tek ekrana indirgenmişti. Aynı listede hem *kalite* seçenekleri (Otomatik / 720p …) hem de *sunucu (kaynak)* satırları vardı. İki farklı karar tek ekrana karıştığı için kullanıcı hangi satırın videoyu değiştirdiğini, hangisinin sadece çözünürlük seçtiğini ayırt edemiyordu.
- **Düzeltme — iki ayrı sheet:**
  - **Kalite** sheet'i (kalite butonuna **kısa dokunma**): yalnızca `Otomatik (en iyi kalite)` + kaynağın **açıkça bildirdiği** çözünürlükler (2160p / 1080p / 720p …). Seçilen kalitede o çözünürlüğe sahip en iyi kaynak oynatılır.
  - **Kaynak** sheet'i (aynı butona **uzun dokunma** veya Kalite sheet'indeki "Kaynak" aksiyonu): yalnızca sunucu (eklenti akışı) listesi. Satırda eklenti adı + akış adı + bildirilen çözünürlük rozeti bulunur.
  - İki sheet arasında başlıktaki aksiyonla geçilir; geri tuşu davranışı değişmez.
- **Uydurma yok:** Kalite listesi yalnızca `qualityValue` veya akış adında **açıkça** yazan çözünürlüklerden üretilir. Hiçbir kaynak çözünürlük bildirmiyorsa kalite listesi boş kalır ve neden boş olduğunu anlatan bir bilgi kartı + "Kaynak listesi" kısayolu gösterilir (eski davranış: alakasız kaynak satırları "kalite" gibi listeleniyordu).
- **Yedek kaynak akışları:** Bir kaynak çözümlenemediğinde / aynı kaynak bulunamadığında açılan liste artık doğrudan **Kaynak** sheet'i (kaynak listesi) — kalite sheet'i değil.

### 🔎 Eklenti Arama Ekranları Ana Arama Sayfasıyla Birebir Aynı Görünümde
- **Eklenti içi arama (eklenti keşfet sayfası):** 3'lü poster ızgarası kaldırıldı. Artık ana arama sayfasındaki gibi **tek sütun, kompakt yatay sonuç satırları** (56×80 poster, 16 dp köşe, neon çerçeve, kalite/yıl/tür/kaynak çipleri) kullanılıyor. Dikey/yatay dönüşte ve tablet/TV genişliklerinde kart boyutları artık bozulmuyor.
- **Arama çubuğu:** Eklenti keşfet sayfası, Eklenti Portalı ve eklenti seçim sheet'i artık ana aramadaki **`KitsugiCosmicSearchBar`** bileşenini kullanıyor — aynı yükseklik, aynı köşe yarıçapı, aynı odak rengi, aynı temizle/ara butonları. Üstte sabit duran yarı saydam overlay çubuk (içeriği 88 dp aşağı iten) kaldırıldı; çubuk artık sayfa akışında ve başlıkla aynı hizada.
- **Başlık hizası:** Eklenti arama sayfasında geri + eklenti simgesi + eklenti adı + sonuç sayısı + yenile satırı ana arama başlığıyla aynı hizada.
- **Tüm eklentilerde ara (dialog):** Sonuçlar poster şeridi yerine aynı satır düzeninde, eklenti başlıkları korunarak listeleniyor.
- **Arama mantığı korundu:** Cloudflare clearance, boş sonuç yeniden denemesi, +18 filtreleme ve `SearchResponse` → detay eşlemesi değişmedi; yalnızca sunum katmanı hizalandı.

---

## 🇬🇧 English

### 🎚️ Quality and Source Are Separate Again
- **Problem:** The in-player sheet had been collapsed into a single "Quality / Source" list that mixed resolution options with plugin stream rows, so it was unclear which row changes the server and which only picks a resolution.
- **Fix — two dedicated sheets:**
  - **Quality** (short tap on the quality button): `Auto (best quality)` plus only resolutions a source explicitly declares. Picking a quality plays the best source of that resolution.
  - **Source** (long tap, or the "Source" action in the Quality sheet): only the plugin stream list, with addon name, stream name and declared-resolution badge.
  - The two sheets cross-link from their headers; back behaviour is unchanged.
- **No invented values:** the quality list is built strictly from `qualityValue` or a resolution written verbatim in the stream name. When nothing declares a resolution the list stays empty and an explanatory card with a "Source list" shortcut is shown.
- **Fallback paths** (source failed / same source unavailable) now open the **Source** sheet, not the quality sheet.

### 🔎 Plugin Search Screens Match the Main Search Page
- **In-plugin search:** the three-column poster grid is gone; results are now the same compact single-column rows as the main search (56×80 poster, 16 dp corners, neon frame, quality/year/type/source chips), so cards keep their proportions in portrait, landscape and on tablets/TV.
- **Search bar:** the plugin explore page, the plugin portal and the plugin picker sheet now all use the shared `KitsugiCosmicSearchBar` — identical height, corner radius, focus colour and clear/search buttons. The floating translucent overlay bar is removed; the bar sits in the page flow, aligned with the header.
- **Header alignment:** back + plugin icon + plugin name + result count + refresh, aligned with the main search header.
- **"Search in all plugins" dialog** now lists rows in the same layout, keeping plugin group headers.
- **Search logic untouched:** Cloudflare clearance, empty-result retry, +18 filtering and `SearchResponse` → detail mapping are unchanged; only the presentation layer was aligned.

## Doğrulama / Validation
- Koddaki tüm `QualitySheet` çağrıları yeni ayırıma göre güncellendi; `KitsugiSheets.SourceTracks` durumu `PlayerSheetsHost` içinde karşılandı.
- Derleme bu ortamda çalıştırılamadı (JDK/Android SDK yok); değişiklikler kaynak düzeyinde gözden geçirildi.
