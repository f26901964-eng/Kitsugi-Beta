# Çözüm Raporu — Sonraki Bölüm Önbelleği (Prefetch) ve Engellemeyen Arama Göstergesi

**Tarih:** 2026-10-23
**Sürüm:** v2.4.341
**Branch:** `arena/bacf9190-kitsugi-beta`
**Durum:** Uygulandı (statik doğrulama tamam; derleme cihaz/CI tarafında)

---

## 1. Kullanıcı Bildirimi (Belirti)

1. Tam ekran oynatıcıda bölüm ortasında (ör. 15:12 / 24:18) **"Sonraki Bölüm Kaynakları Aranıyor..."**
   yazısı ve plazma yüklenme küresi görünürken **video %85 siyah bir katmanla karartılıyor**,
   oynatma devam etse bile kullanıcıya "video engellendi/durdu" hissi veriyordu.
2. Bölüm geçişlerinde her seferinde tüm eklentiler/addon'lar sıfırdan sorgulanıyor;
   kullanıcı "izlediğim kaynak üzerinden diğer bölümlerin verisi izlerken hazır tutulsun,
   geçişim anında olsun" istiyordu.

## 2. Kök Neden

- `KitsugiFullscreenPlayerScreen` içinde `nextEpisodeLoading` / `isResolvingStream` durumları
  `PlayerInlineLoadingOverlay` bileşenine bağlıydı; bu bileşen `fillMaxSize()` +
  `Color.Black.copy(alpha = 0.85f)` arka planla **tüm video yüzeyini kaplıyor** ve görsel olarak
  oynatmayı bloke ediyordu (oysa arama arka planda, oynatma kesintisiz).
- `PlayerControls.showLoadingCircle` ayrıca `isLoadingEpisode`/`isLoading` true iken orta
  "buffering" dairesini gösteriyor; video aslında arabelleğe almıyorken sahte "donma" algısı yaratıyordu.
- `playEpisode()` her çağrıda `fetchStreamsForEpisode()` ile **tüm** Stremio addon'larını +
  tüm CS eklentilerini paralel sorguluyordu; önceki bölümlerden öğrenilen hiçbir veri
  yeniden kullanılmıyor, hiçbir önbellekleme yoktu.

## 3. Çözüm

### 3.1 Engellemeyen durum çipi (UI)
- Yeni bileşen: `PlayerNonBlockingLoadingChip` (`components/PlayerLoadingViews.kt`).
  - Tam ekran kaplamaz, karartma arka planı yok, tıklanabilirlik yok (dokunmatiği yutmaz).
  - Üst-orta küçük pill: mini plazma göstergesi + mesaj.
- `KitsugiFullscreenPlayerScreen`: `nextEpisodeLoading || isResolvingStream` artık bu çipi
  gösteriyor; eski iki tam ekran overlay çağrısı kaldırıldı.
- `PlayerControls.showLoadingCircle`: yalnızca **gerçek** `PlaybackState.Buffering` durumunda
  orta daireyi gösteriyor (`isLoading` / `isLoadingEpisode` çıkarıldı).

### 3.2 Sıralı kaynak prefetch + bellek önbelleği (yeni çekirdek sınıf)
- Yeni dosya: `core/player/EpisodeStreamPrefetcher.kt`
  - Bölüm listesi geldikten sonra sıradaki **2 bölüm** (`DEFAULT_PREFETCH_DEPTH`) için
    **sırayla (aynı anda tek istek, istekler arası 400 ms nefes)** arka plan işi.
  - Sorgular **yalnızca kullanıcının o an izlediği kaynak** (`preferredSource = currentAddonName`)
    üzerinden: CS eklentisiyse sadece o eklenti, Stremio addon'ıysa `AddonStreamRepository`'ye
    yeni eklenen `onlyAddonName` filtresiyle sadece o addon.
  - TTL'li bellek önbelleği: kaynak listesi 30 dk, çözümlenmiş URL 10 dk.
  - Yalnızca meta veri tutulur (video baytı indirilmez) → bellek maliyeti ihmal edilebilir.
  - `viewModelScope` üzerinde koşar; oyuncu kapanınca kendiliğinden iptal.
- Tetikleyiciler (`KitsugiPlayerViewModel`):
  - `loadEpisodes()` başarıyla dolunca,
  - `playEpisode()` başarılı geçişten sonra,
  - `changeStreamSource()` sonrası (önbellek tümden geçersiz + yeni kaynakla yeniden kurulum),
  - `initialize()` yeni oturum anahtarında (eski seri önbelleği temizlenir).
- Tüketim:
  - `playEpisode(targetEp)` önce `peekStreams(targetEp)` dener → doluyse **arama fazı tümden
    atlanır** ("Sonraki Bölüm Kaynakları Aranıyor..." hiç görünmez).
  - Bölüm sonuna **≤ 120 sn** kalınca (`onPositionChanged` → `maybePreResolveNextEpisode`)
    sıradaki bölümün tercih edilen kaynağı için URL **arka planda önceden çözümlenir**;
    geçişte çözümleme fazı da atlanır → **anında geçiş**.
  - `fetchStreamsForEpisode(nextEp, onlySourceName)` yeni isteğe bağlı parametreyle tek kaynak
    sorgusu destekler; CS3-direkt-URL modu da kısıtlamaya saygı duyar.

## 4. Değişen Dosyalar

| Dosya | Değişiklik |
|---|---|
| `core/player/EpisodeStreamPrefetcher.kt` | YENİ — sıralı prefetch + TTL'li önbellek + ön-çözümleme kaydı |
| `ui/screens/fullscreen/components/PlayerLoadingViews.kt` | YENİ bileşen `PlayerNonBlockingLoadingChip` |
| `ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt` | Tam ekran overlay → engellemeyen çip |
| `ui/screens/fullscreen/controls/PlayerControls.kt` | `showLoadingCircle` yalnızca gerçek buffering |
| `ui/screens/fullscreen/KitsugiPlayerViewModel.kt` | Prefetch orkestrasyonu, önbellekten oynatma, ön-çözümleme, `onlySourceName` desteği |
| `data/repository/AddonStreamRepository.kt` | `getStreamsForEpisode(..., onlyAddonName)` filtresi |

## 5. Davranış Özeti

| Durum | Eski | Yeni |
|---|---|---|
| Arama sürerken video | %85 siyah katman, sahte buffering dairesi | Video kesintisiz oynar; üstte küçük çip |
| Bölüm geçişi (önbellek dolu) | Tüm kaynaklar sıfırdan aranır | Arama + çözümleme atlanır, anında geçiş |
| Bölüm geçişi (önbellek boş) | Aynı | Aynı akış ama engellemeyen çiple |
| Ağ/eklenti yükü | Her geçişte tüm addon+eklentiler | İzlemede: yalnızca izlenen kaynak, sırayla, arka planda |

## 6. Riskler ve Geri Alma

- Önbellek yalnızca bellekte ve TTL'li; bayat veri riski TTL'lerle (30 dk / 10 dk) sınırlı.
- Çözümlenmiş URL yalnızca **aynı kaynak anahtarı** (`infoHash`/`url`) eşleşirse kullanılır.
- Tüm özellik tek bayrak olmadan, mevcut akışa eklemeli kuruldu; geri almak için
  `restartEpisodePrefetch` çağrıları ve çip bloğu çıkarılabilir — oynatma akışı eski
  davranışa birebir döner.
