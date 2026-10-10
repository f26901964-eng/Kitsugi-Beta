# PLAN/TASK — Manga Oku, Keşfet başlık boşluğu, Tümünü Gör posterleri, eklenti overlay, alakasız galeri

Tarih: 2026-10-11
Durum: Kod düzeltmeleri tamamlandı; cihazda/Gradle'da doğrulama bekliyor.

## Hata (ekran görüntülerine göre)

1. **Manga detayındaki "Oku" butonu bozulmuş.** Berserk (MAL manga) sayfasında Oku'ya basınca manga kaynak tarama / veri çekme sayfası açılmak yerine geri gidiyor gibi hissediliyor.
2. **Keşfet raf başlıkları kenara yapışık.** "Yayındaki Animeler", "Yaklaşan Animeler", "Tümünü Gör" solda/sağda 0 boşlukla duruyor; kartların 20dp inset'iyle uyumsuz.
3. **Tümünü Gör kutucukları posteri tam göstermiyor.** Dikey modda görsel alanı kısa/yanlış oranda; afiş Crop ile kırpılıyor. Kolon sayısı stüdyo detayından farklı (telefonda 2).
4. **Toplu eklenti kurulumu keşfeti kapatıyor.** "Eklentiler Kuruluyor / Kitsugi Eklentileri (Resmi) / 10 / 165" kayan kartı ekranın altını kaplıyor ve silinmiyor. Sistem bildirimi yeterli.
5. **Uncharted sayfasında alakasız görseller.** Hero'da UNCHARTED logosu, galeride "Family Full House with Rohit Sharma" poster/backdrop'ları. TMDB film/dizi kimlik çakışması.

## Kök nedenler

1. **Oku:** `currentAppStateKey` içinde `mangaBrowseOpen`, `ApiResultDetail` / `MediaDetail`'den *sonra* geliyordu. Browse açılınca detay hâlâ öncelikli olduğu için görünmüyordu. Geçici çözüm `detailBackStack = emptyList()` idi → detay siliniyor, kullanıcı Keşfet'e düşüyor (geri gitmiş gibi).
2. **Başlıklar:** `KitsugiHorizontalMediaSection` başlık satırında yatay padding yoktu; `LazyRow` kartları `contentPadding = 20.dp` kullanıyordu.
3. **Tümünü Gör:** `FullScreenMediaGridPage` kolon hesabı telefonda 2 (stüdyo 3/4/5). `KitsugiExploreMediaCard` dikey poster için sabit 210dp yükseklik kullanıyordu → 2:3 afiş `ContentScale.Crop` ile üst/alt veya yan kırpılıyor.
4. **Overlay:** `AppRoot` içindeki `AppBulkInstallProgress` keşfet üzerinde kalıcı bir Card. Kullanıcı Android bildirim çubuğunu istiyor.
5. **Galeri/logo:** TMDB `movie` ve `tv` kimlik alanları ayrıdır; aynı sayı bambaşka yapıma aittir.
   - `getTmdbGalleryItems` karşı türü de deniyordu (`movie` + `tv`).
   - `getFanartGalleryItems` film boşsa TVDB/TV Fanart'a (veya tersi) düşüyordu.
   - `resolveTvdbIdFromTmdb` önce `/tv/{id}/external_ids` sonra `/movie/{id}` sorguluyordu.
   - `selectLogoUrl` dizi logosuz kalınca `movieLogoUrl` fallback'i Uncharted logosunu Family Full House üzerine basıyordu.
   - `TmdbArtworkIdentity` TMDB başlığı boşsa fail-open'dı.

## Yapılan işler

- `mangaBrowseOpen` önceliği `mangaDetail` / `mangaReader` ile aynı seviyeye alındı (detayın üstü).
- `detailBackStack = emptyList()` Oku yollarından kaldırıldı. Geri tuşu detaya döner.
- Raf başlığı + boş durum metnine `padding(horizontal = 20.dp)`.
- Tümünü Gör kolonları stüdyo ile birebir: dikey 3/4/5, yatay 3/4/5.
- Dikey poster kutusu `.aspectRatio(2f / 3f)` — afiş tam görünür.
- `AppBulkInstallProgress` silindi. `AddonInstallNotifier` CS + manga toplu kurulum/güncellemeyi `NotificationCompat` ile gösteriyor.
- Fanart film↔dizi fallback kaldırıldı. TMDB galeri yalnızca beklenen türü çeker.
- TVDB çözümlemesi yalnızca `/tv/{id}/external_ids`.
- Logo: beklenen başlıklarla TMDB movie/tv kaydı doğrulanır; uyuşmazsa logo uygulanmaz. Dizi→film logo fallback'i kaldırıldı.
- `TmdbArtworkIdentity`: beklenen başlık varken TMDB başlığı boşsa fail-closed.

## Değişen dosyalar

Yeni:
- `app/src/main/java/com/kitsugi/animelist/core/notifications/AddonInstallNotifier.kt`

Silinen:
- `app/src/main/java/com/kitsugi/animelist/ui/app/AppBulkInstallProgress.kt`

Güncellenen:
- `app/src/main/java/com/kitsugi/animelist/AppRoot.kt`
- `app/src/main/java/com/kitsugi/animelist/AppRootDetailPages.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiEpisodeRatingsRepository.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/MediaGalleryIdentity.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/app/AddonViewModel.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/app/MangaViewModel.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiExploreMediaCard.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiHeroSection.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiHorizontalMediaSection.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailViewModel.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/MediaEntryDetailViewModel.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/FullScreenMediaGridPage.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`

## Doğrulama / kalan işler

- Statik inceleme: Oku artık back stack'i silmiyor; browse detayın üstünde; overlay referansı yok; Fanart karşı-tür fallback yok.
- Bu ortamda tam Android SDK derlemesi yok → Gradle/cihaz testi burada çalıştırılmadı.
- Cihazda bakılacak senaryolar:
  1. MAL/AniList manga detayı (Berserk) → Oku → kaynak tarama açılmalı; geri → aynı detay.
  2. Keşfet raf başlıkları kartlarla hizalı 20dp boşluk.
  3. Tümünü Gör dikey: telefonda 3 kolon, poster 2:3 kırpılmadan.
  4. Toplu eklenti kurulumu keşfeti kapatmamalı; bildirim çubuğunda `n / total` + eklenti adı.
  5. Uncharted (TMDB film) galeri/logo yalnızca Uncharted; Family Full House karışmamalı.

Not: ZIP yalnızca değişen kaynak dosyaları ve bu plan/task dosyasını içerir; tek başına APK veya tam proje değildir. Dosyaları mevcut depo köküne aynı dizin yapısıyla yerleştirin. Silinen `AppBulkInstallProgress.kt` zip içinde `DELETED.txt` olarak işaretlenir.
