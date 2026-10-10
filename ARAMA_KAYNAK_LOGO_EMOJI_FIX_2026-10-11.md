# Arama Sayfası — Kaynak Emojileri → Orijinal Logolar (2026-10-11)

## Sorun
Kaynağa özel arama sayfalarının başlıklarında platform adı **emoji** ile gösteriliyordu:

| Sayfa | Önceki başlık |
|---|---|
| AniList | `🅰️ AniList Arama` |
| MyAnimeList | `Ⓜ️ MyAnimeList Arama` |
| TMDB | `🎬 TMDB Arama` |
| Shikimori | `🌸 Shikimori Arama` |
| Kitsu | `🦊 Kitsu Arama` |
| Simkl | `📺 Simkl Arama` |
| Bangumi | `🎌 Bangumi Arama` |

Aynı sayfadaki filtre/sıralama başlıkları ile filtre özeti çipinde de emoji kullanılıyordu.
Uygulamanın geri kalanında (Keşfet, Listem, Detay, kaynak çipleri) zaten
`KitsugiPlatformLogo` ile **orijinal marka logoları** kullanıldığı için arama sayfası
tutarsız kalıyordu.

## Çözüm
`KitsugiPlatformLogo` (ve `KitsugiPlatformLogos`) bileşeni arama sayfasına da bağlandı.
Mevcut `ic_logo_anilist`, `ic_logo_mal`, `ic_logo_tmdb`, `ic_logo_shikimori`,
`ic_logo_kitsu`, `ic_logo_simkl`, `ic_logo_bangumi` çizimleri kullanıldı —
**yeni bir varlık eklenmedi**, emoji yerine var olan marka logoları gösteriliyor.

### Değişen dosyalar
1. **`SourceSearchPage.kt`** — Sayfa başlığı artık `logo + "<Kaynak> Arama"`.
   `Stil` başlık satırı `Row`'a alındı (`Arrangement.spacedBy(10.dp)`).
   "Arama" kelimesi `R.string.arama` üzerinden okunuyor → İngilizce arayüzde
   artık "AniList Arama" yerine **"AniList Search"** görünüyor (i18n düzeltmesi).
2. **`SourceEngineFilterSheet.kt`** — `"🅰️ AniList Filtreleri"` →
   `logo + "<Kaynak> Filtreler"` (`R.string.filtreler`, EN: "Filters").
   Başlık satırına `weight(1f)` + `Spacer(8.dp)` eklendi; uzun kaynak adlarında
   "Sıfırla" butonu artık taşmıyor.
3. **`SourceSpecificFilterChipsRow.kt`** — `SortOptionsContent` başlığı
   `"🌸 Sıralama Seçenekleri"` → `logo + "<Kaynak> Sıralama Seçenekleri"`
   (`R.string.inline_1_s_sorting_options_f94d7e1` şablonu, EN: "%1$s Sorting Options").
4. **`SearchScreen.kt`** — Filtre özeti çipindeki `🎛️ 🌸 SHI` kısaltması
   `logo + "Shikimori"` biçimine çevrildi (kısaltma yerine tam kaynak adı;
   taşma durumunda metin zaten ellipsis ile kırpılıyor).
5. **`SearchResultRow.kt`** — Her sonuç kartının sağ-alt köşesindeki kaynak
   rozeti artık **logo + kaynak adı** (Örn. 🦊 Kitsu logosu + "Kitsu").
   Logo bulunmayan kaynaklarda (CS3 eklentileri) yalnızca ad kalır.

### Notlar
- **"Tümü" motoru:** `all` kimliği için marka logosu olmadığından
  `KitsugiPlatformLogo` küre (`Icons.Rounded.Public`) simgesine düşer — boş
  görünüm yok.
- Kapsam (Anime/Manga/Dizi…) emojileri korunmuştur; bu görev yalnızca **kaynak**
  logolarını kapsıyor.
- Derleme doğrulaması: kod tabanı bu ortamda Android SDK olmadan derlenemiyor;
  değişiklikler yalnızca statik olarak (import/parantez denge kontrolü) doğrulandı.
  Cihazda `Arama → kaynak seç → Tümünü Gör` akışıyla görsel kontrol önerilir.

## Sürüm notu önerisi (v2.4.236)
- **[TR]** Arama sayfasında kaynak emojileri kaldırıldı; tüm kaynak başlıkları,
  filtre/sıralama panelleri ve sonuç rozetleri artık platformun orijinal logosunu
  kullanıyor. Kaynağa özel arama başlıkları İngilizce arayüzde de yerelleşti.
- **[EN]** Replaced source emojis with the original platform logos across the
  search page: page titles, filter/sort sheets and result badges. Localized the
  per-source search titles for the English UI.
