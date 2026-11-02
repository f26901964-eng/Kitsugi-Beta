# ÇÖZÜM RAPORU — Kalite/Kaynak Ayrımı + Eklenti Arama Paritesi (2026-11-02)

Sürüm: **v2.4.341** · Kapsam: oynatıcı içi sheet'ler ve eklenti arama ekranları

---

## 1) Oyuncu: "Kalite" ve "Kaynak" yanlış şekilde birleştirilmişti

### Kök neden
`app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/QualitySheet.kt`
tek bir sheet olarak yazılmıştı:

- Başlık: **"Kalite / Kaynak"**
- İçerik: **doğrudan `List<StreamSource>`** (sunucu/eklenti akışları) + seçim radyoları

Yani *kalite seçimi* diye sunulan ekran aslında *kaynak (sunucu) seçimi* yapıyordu.
Kullanıcı ekran görüntülerinde (1–3) gördüğü ayrı "Kalite" ve "Kaynak" bölümleri bu
sürümde kaybolmuştu; ikisi tek listeye karışmıştı. Ayrıca kaynak çözümlenemediğinde
açılan fallback listeleri de bu yanlış sheet'i açıyordu.

### Yapılan düzeltme

| Dosya | Değişiklik |
|---|---|
| `PlayerEnums.kt` | `KitsugiSheets.SourceTracks` eklendi (Kalite = `QualityTracks`). |
| `sheets/QualitySheet.kt` | Yeniden yazıldı: **yalnızca kalite**. `Otomatik (en iyi kalite)` + kaynağın **açıkça bildirdiği** çözünürlükler. Seçim, o çözünürlükteki en iyi kaynağı oynatır. Bildirilen çözünürlük yoksa liste boş kalır, nedenini anlatan kart + "Kaynak listesi" kısayolu gösterilir. |
| `sheets/SourceSheet.kt` (yeni) | **Yalnızca kaynak (sunucu) listesi.** Eklenti adı + akış adı + bildirilen çözünürlük rozeti. Kalite sheet'ine geçiş aksiyonu var. |
| `PlayerSheetsHost.kt` | `QualityTracks` → `QualitySheet`, yeni `SourceTracks` → `SourceSheet`; sheet'ler arası geçiş için `onShowSheet` parametresi. |
| `TopRightPlayerControls.kt` | Kalite butonuna **uzun basma** artık `onQualityLongClick` (Kaynak) çağırıyor; eskiden ikisi de aynı sheet'i açıyordu. |
| `PlayerControls.kt` | Kısa dokunma → Kalite, uzun dokunma → Kaynak. |
| `KitsugiFullscreenPlayerScreen.kt` | Yedek kaynak / çözümleme başarısız fallback'leri (5 çağrı) artık `SourceTracks` açıyor. |
| `res/values(-en)/strings.xml` | Kalite/Kaynak başlıkları, otomatik seçenek açıklaması, "kaynak sayısı", "seçilebilir kalite yok" metni. |

### Kalite listesinin üretim kuralı (uydurma yok)
`buildQualityOptions(streamSources)`:

1. Her kaynak için bildirilen çözünürlük: `StreamInfoResolver.csQualityHeightOrNull(qualityValue)`
   → yoksa `StreamSorter.parseQualityValue(quality)` (akış adındaki açık "1080p" gibi ibareler).
2. Bilinmeyenler listeye **girmez**; hiçbiri bilinmiyorsa liste yalnızca "Otomatik" olur ve
   arayüz "seçilebilir kalite yok" durumunu gösterir.
3. Seçenek başına hedef kaynak: o çözünürlükteki mevcut seçili kaynak, yoksa listedeki ilk kaynak.
4. `Otomatik` = en yüksek bildirilen çözünürlüğe sahip kaynak.

---

## 2) Eklenti arama ekranları ana arama sayfasıyla hizalanmadı

### Kök neden
Ana arama sayfası (`SearchScreen`) `KitsugiCosmicSearchBar` + `SearchResultRow`
(tek sütun, 56×80 posterli kompakt yatay satır) kullanırken, eklenti tarafı:

- `components/AddonExploreDialog.kt` → üstte **yarı saydam overlay** arama çubuğu
  (`AddonFloatingSearchBar`), içeriği `padding(top = 88.dp)` ile aşağı itiyor;
  sonuçlar **3'lü poster ızgarası** (`chunked(3)` + `KitsugiExploreMediaCard`).
- `PluginPickerScreen.kt` ve `components/PluginPickerBottomSheet.kt` → kendi
  `BasicTextField` tabanlı arama kutuları (farklı yükseklik/köşe/ikon düzeni).
- `components/AddonSearchDialog.kt` → sonuçlar eklenti başına yatay poster şeritleri.

Sonuç: dikey/yatay geçişte ve farklı ekran genişliklerinde eklenti arama kartları
ana aramayla aynı okunmuyordu.

### Yapılan düzeltme

| Dosya | Değişiklik |
|---|---|
| `components/AddonResultRow.kt` (yeni) | Ana aramadaki `SearchResultRow` ile **birebir aynı ölçüler**: 56×80 poster, 16 dp satır köşesi + neon çerçeve, başlık 2 satır, alt bilgi 1 satır, kalite/yıl/tür/kaynak çipleri, Referer başlıklı poster yükleme. |
| `components/AddonExploreDialog.kt` | Overlay çubuk kaldırıldı. Yeni düzen: (1) ana aramayla hizalı başlık satırı (geri + eklenti ikonu + eklenti adı + sonuç sayısı + yenile), (2) **`KitsugiCosmicSearchBar`**, (3) sonuçlar `AddonResultRow` tek sütun listesi. Vitrin + kategori rafları (Keşfet paritesi) korundu. |
| `PluginPickerScreen.kt` | Arama kutusu `KitsugiCosmicSearchBar` ile değiştirildi; `TextFieldState` senkronizasyonu kaldırıldı. |
| `components/PluginPickerBottomSheet.kt` | Aynı değişiklik (sheet içi arama çubuğu). |
| `components/AddonSearchDialog.kt` | Eklenti başına yatay poster şeridi → `AddonResultRow` satır listesi (grup başlıkları korunur). |
| `components/AddonHomeParity.kt` | `safeYear()` `private` → `internal` (satır çipinde yıl göstermek için). |
| `res/values(-en)/strings.xml` | `plugin_search_in_addon`, `plugin_search_results`, `plugin_search_hint_home`, `plugin_search_in_addon_generic`. |

**Korunanlar:** arama mantığı (Cloudflare clearance, boş sonuç yeniden denemesi),
+18 filtreleme, `SearchResponse` → `JikanSearchResult`/detay eşlemesi, "Tümünü Gör"
sözleşmesi, alfabetik kaydıraç, kategori çipleri.

---

## 3) Riskler / dikkat edilmesi gerekenler

- `KitsugiSheets` enum'una yeni değer eklendiği için `when(sheetShown)` tek noktada
  (`PlayerSheetsHost`) güncellendi; başka exhaustive `when` yok (doğrulandı).
- Eklenti keşfet sayfasında içerik artık `Column` akışında: LazyColumn'lar
  `Modifier.weight(1f)` kullanır (overlay çubuğun bıraktığı 88 dp boşluk kaldırıldı).
- Bu ortamda JDK/Android SDK bulunmadığı için `./gradlew assembleDebug` çalıştırılamadı;
  değişiklikler kaynak düzeyinde (import/brace/imza) gözden geçirildi.
