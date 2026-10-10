# Plan / Task — Logo dili, doğru eser logosu ve sezon logosu

Tarih: 2026-10-09  
Kapsam: Detay sayfası hero logosu, vitrin logosu, galeri dil/sezon rozeti  
Kaynaklar: Fanart.tv, TMDB, AniList, MAL

## Sorun

1. Hero, filmin kendi logosu yerine ana dizi logosunu seçebiliyordu. Anime filmleri çoğu kaynakta `MediaType.Anime` / “TV” olarak gelir; logo isteği her zaman TVDB dizi logosunu önce deniyordu. Ordinal Scale örneğinde galeride İngilizce film logosu varken hero’da dizi logosu duruyordu.
2. Logo seçimi dil etiketini yok sayıyordu. Galeri görsellerinde dil zaten var (`iso_639_1` / Fanart `lang`); hero bunu kullanmıyordu. Eski Room/bellek önbelleği tek URL sakladığı için dil değişse de eski logo dönüyordu.
3. ClearART, logo fallback’i olarak kullanılıyordu. ClearART ayrı bir galeri kategorisidir.
4. Sezon logosu yoktu. Fanart’ta bazı logolar `season` etiketli; TMDB sezon images ucunda nadiren `logos` dizisi olur. Sezon posteri logo yerine konmamalı.

## Karar

- Dil kaynağı: ayarlardaki **TMDB dili** (`tmdbLanguage`). Kısa kod: `en`, `tr`, `ja`…
- Sıra: tercih edilen dil → metinsiz (`00` / boş) → İngilizce → diğer. Aynı dilde en çok beğenilen / oy alan.
- Film: Fanart `hdmovielogo` / `movielogo` + TMDB `movie` images önce. Dizi logosu ancak film logosu yoksa.
- Dizi/anime: Fanart `hdtvlogo` / `clearlogo` + TMDB `tv` images önce. Film logosu ancak dizi logosu yoksa.
- Film tespiti: `MediaType.Movie`, format/rawFormat (`MOVIE`, MAL `Movie`), alt yazı veya başlıkta movie/film. AniList `format` artık detaya yazılıyor; TMDB linki `/movie/` ise film TMDB kimliği tercih edilir.
- Sezon > 1: önce o sezonun **tercih edilen dildeki** logosu (TMDB sezon logos + Fanart `season` etiketi). Yoksa ana logo. Sezon posteri logo slotuna konmaz.
- SeriesGraph yalnızca başka logo yoksa (dil etiketi taşımaz).
- Eski `logoUrl` Room önbelleği seçimde kullanılmaz. Yeni bellek anahtarı: `tmdbId + dil + film mi + sezon`.
- Galeri: aynı dil sırasıyla sıralanır. Fanart sezon etiketi “Sezon N” rozeti olarak gösterilir. Dil rozeti `en` / `eng` / `ing` için İngilizce.

## Değişen dosyalar

| Dosya | Ne yaptı |
| --- | --- |
| `data/remote/GalleryItem.kt` | `season` alanı |
| `data/remote/FanartApiClient.kt` | ClearART logo fallback kaldırıldı. Dil + likes + sezon etiketli logo seçimi. Sezon-only logo. Galeri öğelerinde dil/sezon |
| `data/remote/KitsugiEpisodeRatingsRepository.kt` | `getLogoUrl(..., isMovie)`. Film/dizi/sezon sırası. Dil ayarı. Sezon posteri logo değil. Eski tek-logo önbelleği devre dışı |
| `data/remote/DetailCache.kt` | Dil+tür+sezon logo önbelleği. Fanart galeri anahtarına dil |
| `data/remote/KitsugiAniListDetailClient.kt` | `format` çekilir. `/movie/` TMDB kimliği filmde tercih edilir |
| `data/remote/KitsugiMalDetailClient.kt` | MAL `type` → format |
| `data/remote/TmdbMediaDetailClient.kt` | Film/TV formatı yazılır |
| `ui/screens/detail/ApiResultDetailViewModel.kt` | Logo ve galeri film/sezon/dil ile. Detay gelince logo yenilenir. Sezon değişince logo yenilenir |
| `ui/screens/detail/MediaEntryDetailViewModel.kt` | Aynı |
| `ui/components/KitsugiHeroSection.kt` | Vitrin logosu da film/sezon bilir |
| `ui/components/KitsugiImageGalleryDialog.kt` | Dil kodu normalizasyonu. Sezon rozeti |

## Doğrulama

- [ ] TMDB dili İngilizce iken Ordinal Scale hero’sunda “THE MOVIE / Ordinal Scale” İngilizce logosu, dizi logosu değil.
- [ ] Aynı eserin galeri Logo sekmesinde İngilizce rozeti ve tercih edilen dil önde.
- [ ] Dizi sayfasında (ör. ana SAO) dizi logosu; film logosu onu ezmesin.
- [ ] Sezon 2+ bir anime’de sezona özel logo varsa o, yoksa ana logo. Sezon posteri hero’da logo olarak görünmesin.
- [ ] Dil Türkçe iken Türkçe logo varsa o, yoksa İngilizce.
- [ ] Manga sayfasında logo çekilmesin.
- [ ] Fanart kapalıyken yalnızca TMDB; TMDB artwork kapalıyken yalnızca Fanart.

## Bilinen sınır

Fanart.tv’nin ayrı bir `seasonlogo` dizisi yok. Sezon logosu yalnızca logo nesnesindeki `season` alanı veya TMDB sezon `logos` dizisi varsa gelir. Çoğu anime sezonu bu veriyi taşımaz; o zaman ana logo doğrudur.
