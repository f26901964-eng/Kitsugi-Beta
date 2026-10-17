# PLAN/TASK — "Tümünü Gör" Sayfalarına Hızlı "Listeye Ekle" Kısayolu (Grid + Liste)

**Tarih:** 2026-10-17
**Durum:** Kod tamamlandı; cihazda/Gradle doğrulama bekleniyor.

## Kullanıcı isteği
1. Her kaynağın "Tümünü Gör" (see-all) sayfasında, ürünleri doğrudan listeye ekleme kısayolu olsun.
2. Buton sayfanın **her iki görünümüyle** (grid + liste) çok uyumlu olmalı.
3. **Fazla yer kaplamamalı** ve mevcut görsel stile uymalı.
4. Kapsam: tüm kaynaklar (tek sayfa = `FullScreenMediaGridPage`).

Kullanıcı seçimleri (ask_user):
- **Hem** kart üzerinde hızlı ekleme düğmesi **hem** başlıkta toplu ekleme butonu.
- Ürün zaten listede ise: **yeşil tik** gösterilsin; tıklama **düzenleme panelini** açsın.

## Yapılanlar

### 1. Yeni ortak bileşen: `KitsugiQuickAddButton`
- Dosya: `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiQuickAddButton.kt`
- 30dp daire, poster'in **sağ üst (TopEnd)** köşesine biner — layout yer kaplamaz.
- Listededeğilse: accent zemin + beyaz `+` ikonu; listede ise: `AccentGreen` zemin + beyaz tik.
- Kenarlık: 1dp beyaz %45; `tvClickable` ile TV odağı/odak-çerçevesi destekli.
- `contentDescription`: listede değilken `listeye_ekle`, listeyken `duzenle`.

### 2. Üç kart composable'ına `onQuickAddClick: (() -> Unit)? = null`
- `KitsugiExploreMediaCard` (grid): TV / yatay / dikey üç poster varyantının hepsine TopEnd rozeti eklendi. Rozet konumları: sıra TopStart, kaynak BottomStart, durum BottomEnd → **TopEnd boştuydu**, çakışma yok.
- `KitsugiRankingMediaCard` (liste): 86×120 poster TopEnd.
- `DetailedSeasonalMediaCard` (sezonluk liste): 80×120 poster TopEnd.
- Param varsayılan `null` → diğer 14 çağrı noktasında buton gizli, hiçbir çağrı kırılmadı (tümü isimli argüman kullanıyor).

### 3. `FullScreenMediaGridPage` — yeni paramlar + üç header
- `onQuickAddToList: ((JikanSearchResult) -> Unit)? = null`
- `onAddAllToList: ((List<JikanSearchResult>) -> Unit)? = null`
- Kart çağrılarına `onQuickAddClick` bağlandı (grid + liste + sezonluk).
- **Toplu ekleme butonu** (`Icons.Rounded.PlaylistAdd`, yeni import) üç header'da filtre butonunun solunda:
  - grid header (`grid_header`), list header (`list_header`): `RoundedCornerShape(12)` + Surface zemin + 6dp boşluk.
  - yüzen header: şeffaf zemin + 4dp boşluk (mevcut filtre stiliyle aynı).
  - `enabled = displayedResults.isNotEmpty()`; contentDescription = `tumunu_listeye_ekle`.

### 4. `AppViewModel` — tekil ve toplu ekleme aynı çekirdeği paylaşıyor
- Eski 190 satırlık `addApiSelectionToList` gövdesi `private suspend fun insertApiSelection(...): ApiInsertOutcome` olarak çıkarıldı:
  - `ApiInsertStatus` = `ADDED / DUPLICATE / FAILED`, `ApiInsertOutcome(status, message, entry?)`.
  - Bangumi dalı, kaynak doğrulamaları, `primarySource` seçimi, `matchesInSource` mükerrer kontrolü, ARM `resolveIds`, çözülmüş-kimlik ikinci kontrol, Simkl `lookupSimklId`, `MediaEntry` inşası, `repository.insert` — hepsi aynen korundu; snackbar çağrıları outcome döndürücüne çevrildi.
- `addApiSelectionToList(...)` — **imza değişmedi** (TvRootScreen:509 ve AppRoot sarmalayıcısı aynen çalışıyor); outcome mesajını tek bildirim olarak gösterir.
- Yeni `addApiSelectionsToList(...)`:
  - Başta ilerleme mesajı (`toplu_listeye_ekleniyor`), sonda **tek özet** (`toplu_listeye_ekleme_sonuc` = "N eklendi • M zaten listede • K eklenemedi").
  - Tümü listede ise `tumu_zaten_listede`; hiç eklenemediyse ilk hata mesajı gösterilir.
  - Kalem bazlı `try/catch` (CancellationException dışarı fırlatılır) — tek kalem hatası toplu işlemi öldürmez.
  - `accumulated` listesiyle grup içi mükerrer engellenir.

### 5. `AppRoot` bağlantıları (FullScreenMediaGridPage çağrı yeri)
- `onQuickAddToList`: `getMediaEntry(result) != null` → `onEditEntry(entry)` (yeşil tik → düzenleme paneli), değilse → `onAddApiSelectionToList(ApiSearchSelection(result, null))` (Bangumi diyaloğu dahil tekil akış; ExploreScreen uzun-basma ile aynı yol).
- `onAddAllToList`: `appViewModel.addApiSelectionsToList(...)` — bağlantılar `authViewModel`'den, `mediaEntries`/`mediaRepository` mevcut akıştan.

### 6. String'ler (`values` + `values-en`)
- `tumunu_listeye_ekle` — "Tümünü listeye ekle" / "Add all to list"
- `toplu_listeye_ekleniyor` — "%1$d içerik listeye ekleniyor…" / "Adding %1$d items to your list…"
- `toplu_listeye_ekleme_sonuc` — "%1$d eklendi • %2$d zaten listede • %3$d eklenemedi" / "%1$d added • %2$d already in list • %3$d could not be added"
- `tumu_zaten_listede` — "Tüm içerikler zaten listenizde." / "All items are already in your list."

## Değişen dosyalar
- `app/src/main/java/com/kitsugi/animelist/AppRoot.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/app/AppViewModel.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiQuickAddButton.kt` **(yeni)**
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiExploreMediaCard.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiRankingMediaCard.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/DetailedSeasonalMediaCard.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/FullScreenMediaGridPage.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`

## Doğrulama / kalan işler
- Parantez/imsa dengesi, import, imza ve tip kontrolleri elle yapıldı (7 Kotlin dosyası dengeli; XML parse edildi).
- Bu ortamda Android SDK / kotlinc olmadığından Gradle derlemesi çalıştırılamadı: uygun ortamda build alınıp cihazda kontrol edilmeli.
- Cihazda kontrol listesi:
  1. Herhangi bir kaynağın "Tümünü Gör" sayfasında grid ve liste görünümünü geçiş yap → kart köşesindeki `+` her iki görünümde aynı yerde mi?
  2. `+`'a bas → ürün listeye eklensin, rozet **yeşil tik** olsun; tik'e bas → düzenleme paneli açılsın.
  3. Başlıktaki toplu ekleme butonu → tek özet bildirim; tümü zaten listede ise "Tüm içerikler zaten listenizde."
  4. Filtre/arama sonucu boşken buton devre dışı mı?
  5. TV modunda D-pad ile kart → rozet odağı gezinebiliyor mu?

Not: ZIP yalnızca değişen kaynak dosyaları ve bu plan/task dosyasını içerir; tek başına APK veya tam proje değildir. Dosyaları mevcut depo köküne aynı dizin yapısıyla yerleştirin.
