# PLAN / TASK — Aniyomi oynatıcı eşitliği

Tarih: 2026-10-19  
Dal: `arena/d8cd8be8-kitsugi-beta`  
Temel commit: `2170f8b7e708fa6ca0faf2ebda0a39f895280e50`  
Referans: Aniyomi `97414446b8a95994c72dd33c41c971a89d4d25b8`

## Güncel durum: TAM EŞİTLİK / YAYIN ONAYI YOK

Kullanıcının güncel talebi: **mevcut değişen dosyaları ve plan/task dosyasını birlikte ZIP olarak Drive üzerinden teslim et**. Bu talep üzerine güncel çalışma paketi hazırlanmıştır; bu, bütün özelliklerin tamamlandığı veya yayın onayı alındığı anlamına gelmez. Önceki ZIP yalnız tarihsel ilk pakettir ve güncel kodun yerine kullanılmamalıdır.

Aşağıdaki `[x]` işaretleri kaynak kodun bağlandığını belirtir; APK'nın derlendiği veya özelliğin cihazda doğrulandığı anlamına gelmez. İlk karşılaştırma raporları uygulama öncesi fotoğraftır; bu dosya sonraki çalışmaların güncel durumudur.

## A. Kaynakta uygulananlar

### P0 — Güvenlik ve oynatma yaşam döngüsü
- [x] Volume/mute, boost sınırı, NaN/Infinity koruması; CPU gradfun + YUV420P birleşik zincir.
- [x] AndroidCAStore sertifikalarının uygulama özel dizinine PEM dışa aktarımı ve MPV init bağlantısı. TLS doğrulaması daima açık; CA yokluğunda trust-all yok.
- [x] MPV AudioFocus request/loss/transient/gain/duck, noisy receiver, kullanıcı pause kararının korunması, release temizliği.
- [x] Ayrı `onPlayingChanged` olayı; progress/cache akışının pause sırasında da güncellenmesi.
- [x] Native callback'lerin main-thread'e taşınması, release ve kuyruğa alınmış eski generation callback koruması.
- [x] Aynı URL için zorunlu yeniden yükleme; START_FILE bekleme, eski END/FILE_LOADED ve timestamp korumaları; mpv 0.38+ loadfile argüman sırası.
- [x] Harici ses/altyazı eklemenin FILE_LOADED'a taşınması; manuel altyazı kapatmanın geciken otomatik seçimden korunması.
- [ ] Hızlı A→B→A ve aynı URL retry olay sırasını gerçek JNI/kaynaklarla doğrula. Generation koruması tek başına native olayların yükleme kimliğini kanıtlamaz.

### P1 — Motor → VM → gerçek UI bağlantıları
- [x] Native MPV chapter listesi, aktif chapter, normalize/sıralama/duplicate temizliği, AniSkip aralıklarıyla birleştirme ve kaynak değişiminde temizleme.
- [x] MPV cache süresi ve Media3 bufferedPosition'ın seekbar'a taşınması; chapter segmentlerinin iki kez eklenmesinin kaldırılması.
- [x] Seek başladığında tembel preview üretimi ve IO bitmap decode; content/http/file kaynak ve header'a bağlı cache anahtarı.
- [x] MPV secondary track ID seçimi ve kapatma UI'ı; primary/secondary seçili durumlarını ayırma; aynı track'in iki kez seçilmesini önleme; ikinci gecikme.
- [x] Media3 external/secondary yetenek kapıları ve MPV'ye geçiş UI'ı.
- [x] Font, scale, italic/bold, renk, border/background/shadow, position/justify/ASS native stil seçenekleri; sıfır/no resetlerinin uygulanması.
- [x] Kanal, statistics page, filtre, gecikme/hız/preset kalıcılığı; geçici hızın kalıcı hızdan ayrılması ve ilgisiz DataStore emisyonlarında korunması.
- [x] Bağımsız audio language, ortak subtitle whitelist/blacklist politikası ve pitch correction tercihleri/UI bağlantısı.
- [x] Aniyomi Lua köprüsünün lisansı/atıflarıyla eklenmesi, init öncesi kurulumu, generated custom buttons require/load-once bağlantısı ve 14 yaprak komut gözlemi.
- [x] Mevcut mpv.conf/input.conf/scripts/script-opts editörleri korundu. Bunlar ilk raporun ima ettiği gibi tamamen yok değildi.
- [x] PiP gerçek aspect bilgisi, previous/forward, paused giriş koruması; MediaSession previous/seek/speed/progress callback'leri.
- [x] Gizli OSD'de uygulamanın tüketmediği klavye tuşlarının native MPV'ye modifier ile aktarılması.
- [x] Screenshot suspend akışı: Media3 view capture main, PNG encode IO; MPV screenshot IO; cancellation/bitmap temizliği ve sheet hata yönetimi.
- [x] Galeri/share dosya yazımını IO'ya taşıma; MediaStore pending transaction ve başarısız kayıt temizliği; paylaşım için benzersiz dosya + URI izni.
- [x] Skip isteğinde iptal/generation koruması: eski istek yeni aralıkları veya loading durumunu ezmesin.
- [x] Oynatıcı içi doğrudan Harici Video Oynatıcıda Açma butonu (`TopRightPlayerControls` üst araç çubuğu + `MoreSheet` eylem satırı) ve `ExternalPlayerResultContract` ile konum/scrobble geri dönüş senkronizasyonu.
- [x] Media3 yerel (ID3 `ChapterFrame` + `VorbisComment` / Matroska) chapter metadata ayrıştırma ve seekbar/sheet bağlantısı (`PlayerChapterPolicy.parseVorbisComments`).
- [x] OSD gizlenme süresi, panel opaklığı, hareket azaltma (reduce-motion), sistem durum çubuğu, çentik (cutout), yükleme halkası, aktif chapter görünürlüğü, kalan süre ters gösterimi (`invertDurationTimer`) ve ses yüzdesi tercihlerinin uçtan uca bağlanması.
- [x] Sol/orta/sağ çift dokunma bölgesi eylem özelleştirmesi (`SEEK`, `PLAY_PAUSE`, `CUSTOM`, `SWITCH_SUB`, `NONE`) ve parlaklık/ses seviyesi hatırlama (`rememberPlayerBrightness`, `rememberPlayerVolume`).
- [x] AniSkip Netflix tarzı geri sayımlı otomatik atlama (`enableNetflixStyleIntroSkip`, `waitingTimeIntroSkip`), OSD gizliyken yüzen "Giriş'i Atla" butonu ve gömülü chapter algılandığında API skip devre dışı bırakma (`aniSkipDisableOnChapters`).
- [x] HLS/DASH/P2P akışlarında `MediaMetadataRetriever` ağ yükü koruması, sürüklenen konuma öncelikli kare çıkarma ve ardışık hata erken durdurma (`PreviewGenerator`).
- [x] Yerel `content://` geçişlerinde eski `ParcelFileDescriptor` temizliği ve ekran görüntüsünden kaydedilen bölüm küçük resminin (`ArtType.Thumbnail`) bölüm listesine/tüketicisine (`KitsugiMediaTabsClient` + `KitsugiPlayerViewModel`) bağlanması.
- [x] Ayarlanabilir izleme/scrobble eşiği (`scrobbleThreshold`, varsayılan %85) ve izleme konumunu koruma (`preserveWatchingPosition`) politikası.

## B. Gerçekten açık kalan işler (Cihaz ve Ortam Doğrulaması)

Bunlar cihazda/derleme ortamında doğrulanmadan “cihaz doğrulaması tamamlandı” denmemeli:

- [ ] Tam Gradle/Android Kotlin/Compose derlemesi, JUnit, APK, lint ve instrumentation.
- [ ] MPV JNI API ve olay sırası; TLS/hostname/CA/header testleri; focus/noisy/PiP/MediaSession cihaz testleri.
- [ ] Uzun oynatma/bellek/cache/FD sızıntı cihaz testi.
- [ ] Native mpv/FFmpeg/libass sürüm/build flags, ABI/16 KB alignment ve lisans kabul incelemesi.

## C. Çalıştırılan kontroller ve sınırları

1. **23 gerçek production Kotlin policy kontrolü geçti.** `scripts/run_player_policy_checks.sh`: volume, cache/readiness (32 kombinasyon), Lua yolları, TLS seçenekleri, mpv loadfile sürümleri, chapter/range birleştirme ve ortak subtitle dil/whitelist/blacklist davranışı. Android/JNI simülasyonu veya tam uygulama JUnit koşusu değildir.
2. `MpvTrustStore.kt` ve `MpvAudioFocusController.kt`, **gerçek Android 35 android.jar** ile Kotlin 2.0.21 kullanılarak derlendi. Android runtime testi değildir.
3. Değişen/yeni Kotlin kaynakları tree-sitter ile syntax kontrolünden geçti. Bu API/Compose/type-check kanıtı değildir.
4. `git diff --check` temiz.
5. JDK 17 kuruldu; önceki “Java yok” engeli giderildi. Tam Gradle girişimi **Gradle 8.13 indirmesinde `services.gradle.org` SSLHandshakeException/EOF** ile durdu; Android derlemesi/JUnit başlamadı. Bu nedenle tam build başarılı denemez.

Tekrar çalıştırılabilir policy komutu:

```sh
JAVA_HOME=/path/to/jdk17 KOTLINC=/path/to/kotlinc bash scripts/run_player_policy_checks.sh
```

Android build ortamında gerekli komut:

```sh
bash gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

## D. Teslim kapısı

Güncel talep doğrultusunda değişen kaynaklar + bu plan + patch + hash manifest çalışma paketi olarak teslim edilir. CRC/hash/patch uygulama kontrolü yapılır. Paket, tamamlanmış sürüm veya APK değildir; yukarıdaki açık işler ve kabul testleri yayın kapısı olarak kalır. Önceki Drive dosyası bu güncel teslimin yerine kullanılmamalıdır.
