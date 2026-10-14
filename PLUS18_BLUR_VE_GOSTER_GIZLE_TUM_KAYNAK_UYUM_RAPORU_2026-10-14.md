# +18 (Yetişkin İçeriği) — Blur & Göster/Gizle Tüm Kaynak Uyum Denetimi Raporu

Tarih: 2026-10-14 · Branş: `arena/ba8e1ce0-kitsugi-beta` · Sürüm bandı: v2.4.289

## 1. Amaç

Ayarlar → Görünüm & Tercihler ekranındaki iki anahtarın uygulamanın desteklediği
**tüm medya kaynaklarında** tutarlı çalıştığını doğrulamak ve eksikleri kapatmak:

1. **Yetişkin İçeriği (+18)** — `AppSettings.showAdultContent`
   Kapalıyken +18 öğeler hiçbir listede/rafta/arama sonucunda görünmemeli.
2. **Yetişkin İçeriğini Bulanıklaştır (+18)** — `AppSettings.blurAdultMedia`
   Açıkken +18 öğelerin **tüm görselleri** (poster, backdrop, vitrin) bulanık gösterilmeli;
   ham görsel hiçbir koşulda sızmamalı (API < 31 dahil → bitmap stack-blur).

## 2. Mekanizma (mevcut sözleşme)

- **Blur:** `KitsugiNsfwImage` merkezi composable'ı `LocalBlurAdultMedia` (kökte sağlanır)
  + `isAdult` bayrağını okur; API 31+ GPU `safeBlur`, altı API'de Coil `BlurTransformation`.
- **Gizle:** Her liste/rafa veriyi veren katman `showAdultContent || !it.isAdult` filtresi uygular
  (arama ve keşfet ekranları bunu zaten yapıyordu) + sunucu tarafı parametreler
  (`sfw`, `isAdult:false`, `censored`, `nsfw=false`, `include_adult`).
- **YENİ:** `LocalShowAdultContent` composition-local'ı eklendi (Theme.kt) ve kökte
  (`AppRoot.kt`) sağlanıyor. Böylece derin ekranlar (eklenti portalı, manga uzantıları,
  ilişkiler sekmesi, takvim) parametre delmeden gizleme uygulayabiliyor.

## 3. Kaynak uyum matrisi (denetim sonucu)

| Kaynak | +18 bayrağı | Göster/Gizle | Blur | Durum |
|---|---|---|---|---|
| AniList | GraphQL `isAdult` | sorgu `isAdult:false` + UI filtresi | kart/vitrin ✓ | UYUMLU |
| MAL (Jikan) | `rated Rx/Hentai` + `nsfw=black` | `sfw=true` + UI filtresi | ✓ | UYUMLU |
| Kitsu | `KitsuAdultFlags` (R18+) | anonim istekte R18 gizli + UI filtresi | ✓ | UYUMLU |
| Shikimori | `isShikimoriAdultContent` (rx/hentai) | `censored=true` + UI filtresi | ✓ | UYUMLU |
| Bangumi | `nsfw` alanı | `filter.nsfw=false` + yerel filtre | ✓ | UYUMLU |
| Simkl | `SimklAdultFlags` (adult/cert) | UI filtresi | ✓ | UYUMLU |
| TMDB | `adult` alanı / `TmdbAdultResolver` | UI filtresi | ✓ | UYUMLU |
| **CloudStream eklentileri** | **YOKTU (`isAdult=false` sabitti)** | **YOKTU** | **YOKTU** | **BU RAPORDA DÜZELTİLDİ** |
| **Manga uzantıları (TR/global)** | tür/source token sezgiseli | **gözat/aramada YOKTU** | ✓ vardı | **BU RAPORDA DÜZELTİLDİ** |
| **Yayın takvimi (airing)** | `AiringEntry.isAdult` | **YOKTU** | ✓ vardı | **BU RAPORDA DÜZELTİLDİ** |
| **İlişkiler / Benzer başlıklar** | `KitsugiRelation.isAdult` | **YOKTU** | ✓ vardı | **BU RAPORDA DÜZELTİLDİ** |

## 4. Tespit edilen açıklar ve yapılan düzeltmeler

### 4.1 CloudStream (eklenti) kaynakları — bayrak yoktu
- `AddonHomeParity.toAddonSearchResult()` tüm eklenti sonuçlarını `isAdult = false` ile
  üretiyordu → ne blur ne gizle eklentilere uygulanabiliyordu.
  **Fix:** `isAddonAdultResponse()` yardımcıları eklendi: `TvType.NSFW` VEYA
  `CsStreamRunner.ADULT_PLUGINS_SET` üyesi eklenti adı → `isAdult = true`.
  Bu tek nokta; eklenti keşfet rafları, vitrin, arama, "Tümünü Gör" ızgarası ve
  kart rozetlerine (+18 çipi) otomatik yansır.
- `AddonExplorePage` (eklenti portalı): arama sonuçları, vitrin ve kategori rafları
  `LocalShowAdultContent` ile filtrelenir; tamamen yetişkin raflar çizilmez.
- `AddonExploreInline` (satır-içi eklenti keşfet): hero banner + kategori satırları +
  "Tümünü Gör" başlangıç listesi filtrelenir; banner posteri blur sözleşmesine bağlandı.
- `AddonSearchDialog` (çoklu eklenti araması): sonuçlar filtrelenir; `ResultItemCard`
  artık `isAdult` alır ve görseli `KitsugiNsfwImage` ile çizer (Referer/User-Agent
  başlıklı özel `ImageRequest` korunur — bkz. 4.4).
- `AddonFullScreenGridPage` ("Tümünü Gör"): tür filtresi öncesi +18 filtresi uygulandı.
- `KitsugiAddonDetailDialog`: backdrop + hero posteri `isAdultResponse`
  (`loadResponse.type == TvType.NSFW` veya yetişkin eklenti) ile bulanıklaştırılır.

### 4.2 Manga uzantıları (Mihon/Kotatsu tabanlı TR & global kaynaklar)
- `MangaBrowseScreen` ve `TvMangaBrowseScreen`: gözat/arama sonuçları yetişkin
  başlıkları (`MangaDetails.isAdultContent`: hentai/doujin/porn/18+ tür veya kaynak adı)
  ayar kapalıyken eler; sayaç/boş durum/backdrop görünürlüklerle tutarlı.
- Backdrop görselleri (mobil + TV) `KitsugiNsfwImage`'e geçirildi → blur da geçerli.

### 4.3 Detay sekmeleri & takvim
- `RelationsTab` / `RecommendationsTab`: +18 ilişkili/benzer başlıklar ayar kapalıyken gizlenir.
- `KitsugiAiringCalendarScreen`: gün rafları `AiringEntry.isAdult` ile filtrelenir
  (blur zaten uygundu).

### 4.4 Merkezi blur altyapısı güçlendirme
- `KitsugiNsfwImage`: bitmap-blur (API < 31) yolunda `model` bir `ImageRequest` ise
  istek kimliği (başlıklar) korunup yalnızca dönüşüm ekleniyor → hotlink korumalı
  site görsellerinde blur yolu 403'e düşmüyor, ham görsel sızmıyor.
- `LocalShowAdultContent` composition-local'ı eklendi (Theme.kt + AppRoot sağlayıcı).

## 5. Bilinen sınırlamalar (bilinçli olarak kapsam dışı)

- **İndirilenler / İzleme geçmişi / bildirim kapakları:** `AnimeDownload`,
  `WatchHistoryEntry` ve bildirim modellerinde `isAdult` bayrağı yok (kalıcılık şeması
  migrasyon gerektirir). Bu yüzeyler kullanıcının kendi geçmişidir; kaynak uyumu
  kapsamında değildir. İstenirse takip görevi: modele varsayılanlı `isAdult` alanı +
  oynatıcıya `EXTRA_IS_ADULT` extra'ı zinciri.
- `PauseOverlay`, `PostPlayBingeCard`, `CollectionFolderCardMedia`: çağranı olmayan
  (ölü) bileşenler — risk yok.
- Oyuncu aktiviteleri/yorum avatarları gibi kişi görselleri medya görseli olmadığından
  blur kapsamı dışındadır (mevcut ürün kararıyla aynı).

## 6. Test kontrol listesi (cihazda)

1. Ayarlar → +18 KAPALI: Keşfet (tüm kaynak sekmeleri), Arama, Tümünü Gör ızgaraları,
   eklenti portalı & satır-içi eklenti keşfet, manga uzantı gözat/arama, yayın takvimi,
   detay → İlişkiler/Benzer: hiçbir +18 başlık görünmemeli.
2. +18 AÇIK: aynı yüzeylerde +18 başlıklar görünür; kartlarda "+18" rozeti.
3. Blur AÇIK + +18 AÇIK: AniList/MAL/Kitsu/Shikimori/Bangumi/Simkl/TMDB/eklenti/manga
   kaynaklı +18 öğelerin poster+backdrop+vitrin görselleri bulanık (API < 31 cihazda dahil).
4. Blur KAPALI: görseller normal.
5. Eklenti detay diyaloğu ve çoklu eklenti arama diyaloğunda NSFW eklenti sonuçları:
   gizleme kapalıyken görünmez, açıkken bulanık poster.

## 7. Değişen dosyalar

`Theme.kt`, `AppRoot.kt`, `KitsugiNsfwImage.kt`, `AddonHomeParity.kt`,
`AddonExploreDialog.kt`, `AddonExploreInline.kt`, `AddonSearchDialog.kt`,
`KitsugiAddonDetailDialog.kt`, `AddonFullScreenGridPage.kt`, `MangaBrowseScreen.kt`,
`TvMangaBrowseScreen.kt`, `RelationsTab.kt`, `RecommendationsTab.kt`,
`KitsugiAiringCalendarScreen.kt`
