# 🎯 Logo Kimliği & Dil Doğruluğu Fix Raporu — 2026-10-10

## 👁️ Kullanıcı Gözlemi
*Chainsaw Man – The Movie: Reze Arc* (film) detay sayfasında hero alanında **filmin kendi logosu yerine
TV dizisinin sarı "CHAINSAW MAN" logosu** gösteriliyordu. Oysa aynı filmin galerisinde
(Logo sekmesi) doğru film logoları mevcuttu. İstenen: **hangi kaynaktan gelirse gelsin, içeriğin
kendisine ait logo; doğru dilde (TR öncelikli)**.

## 🔬 Kök Neden (canlı API verisiyle doğrulandı)
Kimlik zinciri: MAL `57555` ↔ AniList `171627` ↔ Kitsu `48323` ↔ TMDB **movie** `1218925` ↔ TVDB `397934`.

1. **TVDB tuzağı:** TVDB anime filmlerini *dizinin 0. sezonu* olarak tutar. ARM/animeapi bu yüzden
   film satırında `thetvdb: 397934` verir — bu ID **dizinin** TVDB kaydıdır
   (fanart.tv `tv/397934` → name: "Chainsaw Man", `hdtvlogo[0]` = sarı dizi logosu).
2. **Tür sinyali güvensiz:** Liste senkronları (MAL/AniList/Kitsu) filmleri `Anime/TV` türünde taşır
   (ekran görüntüsünde film için "TV | 1 Bölüm" görünmesi bundan). `isMovie=false` olunca logo hattı
   TV yoluna giriyordu: `resolveTvdbIdFromTmdb(1218925) → 397934 → fanart-TV` → **dizi logosu**,
   ve fanart TVDB logosu TMDB film logosuna tercih edildiği için hero'ya o basılıyordu.
3. **Galerinin "doğru" görünmesi tesadüftü:** `getTmdbGalleryItems`'ta `/tv/1218925` 404 dönüp
   movie fallback'i devreye girdiği için film görselleri geliyordu.
4. **Dil:** Fanart.tv çağrıları sabit `language="en"` ile yapılıyordu; TR tercihi yoktu.
   TMDB logo seçimi dil dilimi içinde API dizi sırasına bakıyordu (oy kalitesi yok).

## 🌍 Açık Kaynak Araştırması
- **Kometa** (Plex medya yöneticisi): logo için `source: tmdb` + `language` önceliği kullanır;
  anime kimliklendirme için **Kometa-Team/Anime-IDs** haritasında `tmdb_movie_id` ile `tmdb_show_id`
   *ayrı alanlar* olarak tutulur — film/dizi ayrımının endüstri standardı budur.
  TVDB ID'sinin "dizinin 0. sezonu dahil tüm kaydı" olduğu açıkça belgelenmiştir
  (bkz. github.com/Kometa-Team/Anime-IDs README).
- **HAMA / anime-lists (Fribb & ScudLee):** AniDB→TVDB/TMDB eşlemesinde sezon numarası taşır;
  sezon bazlı *clearlogo* hiçbir açık API'de **yoktur** — sezon sanatı = sezon posteri/banner'ı.
- **Sonuç:** Sezon girişimleri için doğru davranış, tüm sezonlarda dizi (show) clearlogo'su +
  galeride sezon posteri/banner'ı göstermektir (galeri zaten `seasonposter/seasonbanner` çekiyor).
  Asıl hata, *filmin* dizi zincirine sokulmasıydı.

## 🛠️ Çözüm (uygulanan)
1. **`DetailCache.tmdbMediaCache`** — tmdbId → `"movie" | "tv" | null` önbelleği.
   ARM (`media`) ve animeapi (`themoviedb_type`) yanıtlarından doldurulur
   (`cacheTmdbMediaKind`), negatif önbellekli (`queryMediaKindFromArm`).
2. **`isTmdbMovie(tmdbId)`** — tür bilgisine güvenilmeyen her yerde kesin film tespiti.
3. **`getLogoUrl`**: `effectiveIsMovie = isMovie || isTmdbMovie(...)` → film **asla**
   TVDB/fanart-TV zincirine girmez; yalnızca `fanart movies` + `TMDB movie` uç noktaları.
4. **`resolveTvdbIdFromTmdb` film koruması** + çözücülerde (MAL/AniList/Kitsu × ARM/animeapi)
   film kaydının `thetvdb`'sinin `tmdbToTvdbCache`'e **yazılmaması** (sızıntı kaynağı kurutuldu).
5. **Dil doğruluğu:** `FanartApiClient.resolveLanguage` — sabit "en" yerine uygulama dili
   (örn. `tr`); öncelik: tercih edilen dil → nötr → en.
6. **Kalite sıralaması:** TMDB logolarında dil dilimi içinde `vote_count*10+vote_average` skoru;
   fanart'ta `likes` — hatalı/düşük oylu yüklemeler (filme yüklenen dizi logosu vb.) elenir.
7. **Galeri:** `galleryIsMovie` artık `isTmdbMovie(tmdbId)` ile de kesinleşiyor
   (fallback tesadüfüne bağımlılık bitti).

## 📁 Değişen Dosyalar
- `data/remote/DetailCache.kt` — `tmdbMediaCache` + clear()
- `data/remote/KitsugiEpisodeRatingsRepository.kt` — media-kind altyapısı, `isTmdbMovie`,
  `getLogoUrl` film yönlendirmesi, TVDB film koruması, oy-skorlu logo seçimi
- `data/remote/FanartApiClient.kt` — `resolveLanguage`, likes sıralamalı `extractBestUrl`
- `ui/screens/detail/ApiResultDetailViewModel.kt` — `galleryIsMovie` kesinleşmesi

## ✅ Doğrulama
- Kök neden canlı uç noktalarla kanıtlandı: ARM (mal/kitsu/themoviedb yönleri), animeapi.my.id,
  TMDB `movie/1218925` & `tv/114410` & `tv/1218925(404)` images, fanart.tv `movies/1218925` &
  `tv/397934`, seriesgraph (`Show not found`).
- Sandbox'ta **JVM/Android SDK bulunmadığı için Gradle derlemesi yapılamadı** (bağımlılıklar da
  engelli hostlarda). Bunun yerine parantez/parantez dengesi kontrolü ve tüm diff'lerin satır
  satır incelenmesiyle sözdizim doğrulaması yapıldı; derleme cihaz/CI tarafında denenmelidir.
