# PLAN / TASK — Vitrin (Hero) Dikey & Yatay Görsel Seçimi, Tablet/Telefon Boyut Uyumu ve Tüm Kaynaklar İçin TMDB + Fanart.tv Entegrasyonu

**Tarih:** 2026-10-11  
**Durum:** Tamamlandı

---

## 1. Sorunların Kök Neden Analizi

1. **Bangumi dikey kapağının yatay arka plan (`backdropUrl`) sanılması (`image-1.png` ve `image-2.png`):**
   - `BangumiApiClient.BangumiImages` sınıfında `poster = common ?: medium ?: grid ?: large ?: small` (`common` 400px düşük çözünürlüklü kapak) ve `backdrop = large ?: common ?: medium` (`large` tam boy **dikey** kapak) tanımlıydı.
   - `KitsugiBangumiClient.toSearchResult` fonksiyonu `backdropUrl` alanına bu dikey kapağı yazıyordu.
   - `backdropUrl` dolu göründüğü için `ExploreViewModel.enrichHeroBackdrops` Bangumi öğelerini tamamen atlıyor, `KitsugiHeroSection` ise dikey kapağı yatay 16:9 görsel sanarak `Alignment.Center` ile ortadan kırpıp karakterlerin kafasını ve gövdesini kesiyordu.

2. **Ekran yönü ve cihaz boyutuna göre dikey/yatay görsel seçimi ve kırpma hataları:**
   - `heroImageCandidates` fonksiyonu dikey telefon ekranında bile yatay `backdrop` görselini zorluyordu.
   - `heroImageAlignment` fonksiyonu geniş/yatay şeritlerde `Alignment.Center` kullandığı için 16:9 görseller geniş tablet şeridine (`~2.5:1`) sığdırılırken üstten kesiliyor, karakterlerin baş/yüz kısmı kayboluyordu.
   - Geniş vitrin alanında yalnızca dikey poster bulunan öğelerde (ör. manga veya yatay arka planı olmayan yapımlar) dikey poster tüm genişliğe `ContentScale.Crop` ile gerilip %65'i kırpılıyordu.

3. **Üst üste binen 3 koyu gradyanın (scrim) resmi karartması:**
   - Tam boy dikey gradyan + `layout.isTablet` yüzünden dikey tablette bile sol yarıyı karartan yatay gradyan (`0.92f` alpha) + ikinci bir `bottomFadeHeight` alt gradyan kutusu üst üste binerek vitrin görselini karanlık ve belirsiz hale getiriyordu.

4. **Tüm kaynakların vitrininde TMDB + Fanart.tv poster ve arka plan verilerinin kullanılmaması:**
   - `ExploreViewModel.enrichHeroBackdrops` yalnızca `item.backdropUrl.isNullOrBlank()` olduğunda çalışıyor; AniList (`1900x400` ince şerit), Kitsu, Simkl (`_medium.webp`), Bangumi ve TMDB (`/w500` & `/w1280`) öğelerini atlıyordu.
   - Çalıştığında da yalnızca tek başlıkla TMDB araması yapıyor, **Fanart.tv** (`tvposter`/`movieposter`, `showbackground`/`moviebackground`) verilerini hiç çekmiyor ve dikey `imageUrl` (poster) kalitesini hiç yükseltmiyordu.

---

## 2. Yapılan Geliştirmeler ve Görevler (Tasks)

### Görev 1 — `KitsugiHeroArtworkResolver.kt` (TMDB + Fanart.tv Ortak Vitrin Görsel Çözümleyici)
- Tüm kaynaklar (**Bangumi, AniList, MyAnimeList/Jikan, Kitsu, Shikimori, Simkl, TMDB, Tümü modu, TV vitrini ve Eklenti vitrinleri**) için çapraz ID çözümleme (`MAL` / `AniList` / `Kitsu` / `Shikimori` / `Bangumi` / `Simkl` → `TMDB ID` & `TVDB ID`) ve çoklu başlık (`titleEnglish`, `title`, `titleRomaji`, `titleJapanese`) + yıl destekli TMDB arama yedeği eklendi.
- Hem **TMDB** (`/3/{tv|movie}/{id}?append_to_response=images,external_ids,alternative_titles` ve sezon posterleri `/original` tam çözünürlükte) hem de **Fanart.tv** (`tvposter`/`movieposter` ve `showbackground`/`moviebackground`) paralel olarak sorgulanır.
- **En Kaliteli Dikey Poster Seçimi (`scorePosterCandidate`):** Çözünürlük (`2000x3000` > `1000x1500` / `1000x1426`), `2:3` dikey oran, dil tercihi, sezon uyumu ve topluluk oyları (`vote_average`, `vote_count`, Fanart `likes`) ile en kaliteli dikey poster otomatik seçilir.
- **En Kaliteli Yatay Arka Plan Seçimi (`scoreBackdropCandidate`):** Çözünürlük (`4K 3840x2160` > `2K` > `1080p Full HD`), `16:9` sinematik oran, yazısız (`textless`) temizlik ve topluluk oyları ile en kaliteli yatay arka plan otomatik seçilir.
- Kaynakların yerel URL'leri için anlık yükseltme (`upgradeNativePosterUrl`) ve dikey kapakların yatay backdrop sanılmasını engelleyen doğrulama (`sanitizeNativeBackdropUrl`) eklendi.

### Görev 2 — Kaynak İstemcisi Görsel Kalite Düzeltmeleri
- `BangumiApiClient.kt` & `KitsugiBangumiClient.kt`: `BangumiImages.poster` için 400px `common` yerine tam boy `large` (`/pic/cover/l/`) önceliklendirildi; `toSearchResult` içinde `backdropUrl = null` yapılarak dikey kapağın yatay arka plan sanılması engellendi.
- `KitsuExploreClient.kt`: Poster için `medium` yerine `large` / `original`, cover için `original` / `large` önceliklendirildi ve `titleRomaji` alanı eklendi.
- `MediaGalleryIdentity.kt` & `KitsugiEpisodeRatingsRepository.kt`: `TmdbArtworkIdentity.matches` fonksiyonuna `alternative_titles` desteği ve `external_ids.tvdb_id` önbelleklemesi eklendi.

### Görev 3 — `KitsugiHeroSection.kt` ve `KitsugiNsfwImage.kt` Görsel & Responsive Yenilemesi
- **Dikey ve Yatay Ekran / Cihaz Boyutu Uyumu (`rememberHomeHeroLayout` & `heroImageCandidates`):**
  - **Dikey Telefon (`aspect < 1.10`, dikey mod):** En kaliteli **dikey poster (`posterUrl`)** birincil, yatay arka plan yedek olarak seçilir.
  - **Yatay Telefon, Dikey Tablet, Yatay Tablet ve TV (`aspect >= 1.10` veya yatay mod):** Anime, dizi ve filmlerde en kaliteli **yatay arka plan (`backdropUrl`)** birincil, dikey poster yedek olarak seçilir.
- **Akıllı Hizalama ve Çift Katmanlı Dikey Görsel Koruması:**
  - `heroImageAlignment` ile geniş şeritlerde üst-orta odaklama (`BiasAlignment(0f, -0.32f)` ve `BiasAlignment(0f, -0.25f)`) uygulanarak karakterlerin kafa/yüz kısmının üstten kesilmesi engellendi.
  - Geniş vitrin kutusunda (`heroBoxAspect >= 1.18f`) dikey bir görsel gösterildiğinde (`useDualLayerContain`), görseli kesmek yerine arkada yumuşak atmosferik katman (`Modifier.blur` kullanmadan MIUI/HyperOS güvenli `alpha`) + önde `ContentScale.Fit` ile kesilmemiş tam boy görsel gösterilir.
- **Berrak Scrim (Gradyan) Yapısı:**
  - Görselin üst %60'lık kısmı tamamen açık ve net bırakıldı; çift alt karartma kaldırıldı; dikey tablette sol tarafı karartan yatay gradyan yalnızca `layout.isLandscape` moduna sınırlandırıldı ve yumuşatıldı.

### Görev 4 — `ExploreViewModel.kt`, `ExploreScreen.kt`, `TvViewModel.kt` ve Birim Testleri
- `ExploreViewModel.enrichHeroBackdrops` artık yalnızca `backdropUrl` boş olanları değil, tüm kaynakların vitrin öğelerini `KitsugiHeroArtworkResolver` ile hem dikey poster hem yatay arka plan açısından zenginleştirir.
- `ExploreScreen.kt` ve `TvViewModel.kt` zenginleştirilmiş `bestPosterUrl` ve `bestBackdropUrl` verilerini vitrine aktarır.
- `HeroImageSelectionTest.kt` birim testleri yeni dikey/yatay seçim ve TMDB + Fanart.tv kalite sıralaması kurallarıyla güncellendi.

---

## 3. Değişen ve Eklenen Dosyalar

1. `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiHeroArtworkResolver.kt` *(Yeni)*
2. `app/src/main/java/com/kitsugi/animelist/data/auth/BangumiApiClient.kt`
3. `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiBangumiClient.kt`
4. `app/src/main/java/com/kitsugi/animelist/data/remote/KitsuExploreClient.kt`
5. `app/src/main/java/com/kitsugi/animelist/data/remote/MediaGalleryIdentity.kt`
6. `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiEpisodeRatingsRepository.kt`
7. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiHeroSection.kt`
8. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiNsfwImage.kt`
9. `app/src/main/java/com/kitsugi/animelist/ui/screens/explore/ExploreScreen.kt`
10. `app/src/main/java/com/kitsugi/animelist/ui/screens/explore/ExploreViewModel.kt`
11. `app/src/main/java/com/kitsugi/animelist/ui/tv/TvViewModel.kt`
12. `app/src/test/java/com/kitsugi/animelist/ui/components/HeroImageSelectionTest.kt`
13. `PLAN_TASK_VITRIN_DIKEY_YATAY_TMDB_FANART_2026-10-11.md` *(Yeni)*
