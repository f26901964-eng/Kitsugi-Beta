# Kitsugi Beta v2.4.334

## 🇹🇷 Türkçe

### 🎬 Fragman ve Açılış/Kapanış Videoları: Kasma, Donma ve "Sesi Var Görüntüsü Yok"

Bu sürümde sorun **iki ayrı hatta** ayrı ayrı ele alındı: (1) YouTube fragmanları, (2) AnimeThemes kaynaklı açılış/kapanış dosyaları. Aşağıdaki maddelerin tamamı kaynak kodda doğrulanan hatalardır.

#### ⚡ Performans — "aşırı kasıyor / donuyor"
- **Konum bildirimi fırtınası (`MpvPlayerEngine.kt`):**
  - libmpv `time-pos` değerini her karede değiştiriyor ve dinleyicilere aynen iletiliyordu; küçük oynatıcı saniyede ~60 kez Compose durumu yazıyordu. Detay sayfası kaydırma/oynatma sırasında ana iş parçacığı boğuluyordu.
  - Bildirimler **4 Hz**'e indirildi. Seek, yükleme ve süre değişimi gibi anlık geri bildirim gereken yerlerde bildirim hemen (force) gönderilir.
  - Aynı düzeltme tam ekran oynatıcıyı da rahatlatır.
- **Media3 zaman çizgisi darbesi (`Media3PlayerEngine.kt`):**
  - Media3 konum değişimini hiç bildirmiyordu; küçük oynatıcı sayacı Media3 ile oynayan YouTube fragmanlarında `00:00`'da donmuş görünüyordu. Oynatma sırasında 4 Hz'lik tek darbe eklendi.
- **Çift video çıkışı kurulumu (`MpvPlayerEngine.kt`):**
  - `applyInitOptions` hem `createVideoView` hem `prepare()` içinden çağrılıyordu; `vo` her videoda iki kez yazılıyor, libmpv video çıkışını baştan kuruyordu. Bu, küçük oynatıcı açılırken siyah kare / ilk saniyelerde donma üretiyordu. Görüntü çıkışını kuran ağır blok artık motor ömründe **yalnızca bir kez** uygulanıyor (TLS/CA kurulumu ve `mpv.conf` okuması da bu sayede tekrar tekrar çalışmıyor).
- **Yükleme halkası titremesi:** İlk kare geldikten sonra ara bellek dalgalanmaları yükleme animasyonunu açıp kapatmıyor; akıcı oynayan video "donuyor" gibi görünmüyor.

#### 🔊 "Sesi var, görüntüsü yok" — sessiz hata artık yakalanıyor
- Sorunun kökü: **hem Media3 hem libmpv** video parçası çözülemediğinde hata üretmez. Ses çalar, süre ilerler, ekran siyah kalır. Eski bekçi yalnızca "konum ilerliyor mu?" diye baktığı için bu durumu başarı sanıyordu.
- `PlayerEngine.hasVideoTrack` eklendi:
  - **Media3:** gerçekten seçili (`isSelected`) bir video parçası var mı?
  - **MPV:** seçili video parçası **ve** `vo-configured` — yani görüntü çıkışı gerçekten kuruldu mu? Android'de VO kurulamazsa mpv tam olarak "ses var, görüntü yok" davranışı gösterir.
- Küçük oynatıcılar 6 saniyelik tanıma süresinden sonra video parçası hâlâ yoksa **tek seferlik** diğer dahili motora geçiyor (Media3 → MPV veya MPV → Media3). İki motor da başaramazsa artık sessiz siyah ekran yerine "Tekrar Dene / Kaynağı Aç" kartı görünüyor.

#### 🎵 AnimeThemes kaynağı (`KitsugiAnimeThemesClient.kt`)
- Eski kod yalnızca `animethemeentries[0]` içine bakıyordu. Bir temanın birden çok kaydı olabiliyor (TV / BD / bölüm aralıkları) ve ilk kaydın `videos` dizisi çoğu zaman **boş** oluyordu; bu yüzden satır "Oynat" yerine "YouTube'da ara"ya düşüyordu.
- Artık **tüm kayıtlar ve tüm videolar** taranıyor, seçim şu sırayla yapılıyor:
  1. Altyazısı gömülü olmayan (temiz) dosya,
  2. MP4/H.264 (her cihazda donanım çözülür — VP9 `.webm` sorunlarını tamamen atlar),
  3. Creditsiz (`nc`) sürüm,
  4. WEB kaynağı (BD/DVD dosyalarından çok daha küçük ve akıcı),
  5. 1080p'yi aşmayan en yüksek çözünürlük (2K/4K dosyalar küçük oynatıcıyı boğmaz).

### ✅ Testler
- `InlinePlayerSupportTest`: ses-only algılama eşikleri, tanıma süresi ve sağlıklı oynatımın yanlış yedek geçişi tetiklememesi.
- `KitsugiAnimeThemesClientTest` (yeni): temiz/MP4/creditsiz/WEB/çözünürlük öncelikleri, 1080p üstü dosyaların sona atılması, boş aday listesi.

---

## 🇬🇧 English

### 🎬 Trailers & OP/ED Videos: Stutter, Freezes and "Audio Without Picture"

Two separate pipelines were fixed: (1) YouTube trailers, (2) AnimeThemes OP/ED files.

#### ⚡ Performance
- **Position callback storm (`MpvPlayerEngine.kt`):** libmpv changes `time-pos` every frame and every change was forwarded verbatim, writing Compose state ~60×/s in the inline player. Notifications are now throttled to 4 Hz (seek/load/duration still notify immediately).
- **Media3 timeline tick (`Media3PlayerEngine.kt`):** Media3 never reported position changes, so the inline counter stayed frozen at `00:00`. A single 4 Hz tick was added while playing.
- **Double video-output setup (`MpvPlayerEngine.kt`):** `applyInitOptions` ran from both `createVideoView` and `prepare()`, writing `vo` twice and re-initialising libmpv's video output on every video. Renderer/decoder/cache options now apply exactly once per engine (TLS/CA installation and `mpv.conf` parsing are no longer repeated either).
- **No more loading-ring flicker once the first frame is up.**

#### 🔊 Audio-only playback is now detected
- Neither bound engine raises an error when no video track can be decoded: audio plays, the timeline advances, the screen stays black. The previous watchdog treated that as success.
- New `PlayerEngine.hasVideoTrack`: Media3 reports whether a video group is actually selected; MPV additionally checks `vo-configured`, which is exactly the "audio plays but no picture" signature on Android.
- After a 6 s grace period the inline players switch engines once; if both fail, the user gets a "Retry / Open source" card instead of a silent black player.

#### 🎵 AnimeThemes source
- The parser only looked at `animethemeentries[0]`, whose `videos` array is frequently empty, so rows fell back to "search on YouTube". All entries and all videos are now scanned and ranked: clean (non-subbed) → MP4/H.264 → creditless → WEB → highest resolution up to 1080p.
