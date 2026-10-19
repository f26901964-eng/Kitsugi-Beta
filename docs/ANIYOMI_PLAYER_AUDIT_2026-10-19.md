# Aniyomi → Kitsugi oynatıcı karşılaştırması — ilk düzeltme paketi

> **Tarihsel başlangıç değerlendirmesi:** Aşağıdaki karşılaştırma uygulama öncesi durumu anlatır; sonraki kod değişiklikleri için [güncel plan ve doğrulama durumu](PLAN_TASK_ANIYOMI_PLAYER_PARITY_2026-10-19.md) esas alınmalıdır. Kaynak satır numaraları sonraki düzenlemelerle kaymış olabilir.


> **Güncel karşılaştırma:** [88 tercih + 53 motor/runtime maddesinin tam matrisi](ANIYOMI_KITSUGI_PLAYER_FULL_COMPARISON_2026-10-19.md). Bu dosya ilk düzeltme paketinin geçmiş kaydıdır; güncel eşitlik değerlendirmesi yeni rapordadır.

Tarih: 2026-10-19  
Kitsugi dalı: `arena/d8cd8be8-kitsugi-beta`  
Kitsugi başlangıç commit'i: `2170f8b7e708fa6ca0faf2ebda0a39f895280e50`  
Referans: https://github.com/aniyomiorg/aniyomi  
Referans commit: `97414446b8a95994c72dd33c41c971a89d4d25b8`

## Sonuç ve kapsam sınırı

**Oynatıcıların tamamen eşit olduğu doğrulanmış değildir. Bu rapor tamamlanmış bir baştan sona depo denetimi değildir.** Aniyomi kaynakları ayrı bir referans checkout'una indirildi. MPV görünümü, motor seçenekleri, gözlemci listesi, harici parça yükleme, Lua seek komutları ve oynatıcı tercihleri karşılaştırıldı. Kitsugi'nin motor, görünüm, ViewModel ve fullscreen listener bağlantılarındaki ilgili yollar incelendi. Tüm UI panelleri, Media3, TV, offline, eklenti çözümleme ve cihaz yaşam döngüleri uçtan uca test edilmedi.

Aniyomi'nin MPV mimarisini kullanmak tek başına aynı native motor sürümü, codec desteği, HDR davranışı, ağ güvenliği veya aynı kararlılık anlamına gelmez. Kitsugi ayrıca Media3 motoru ve kendi kaynak/izleme zincirini kullanıyor. `mpv-android-lib:0.1.12` ile referanstaki MPVLib API'si aynı API değildir; PlayerActivity'yi doğrudan kopyalamak uygun değildir.

## Kodda düzeltilen somut farklar

| Alan | Önce | Değişiklik |
|---|---|---|
| Ses / mute | `setVolume(0..1)` sonunda `applyAudioAmplificationDb(0)` tekrar volume=100 yazıyordu. | İkinci yazım kaldırıldı; normal seviye ve mute korunur. Boost değeri gerçek cap'e göre sınırlandırılır. NaN/sonsuz JNI'ye gönderilmez. |
| CPU debanding | CPU seçimi de GPU `deband=yes` açıyordu. | CPU için `vf=gradfun=radius=12`; GPU için `deband=yes`. YUV420P ile tek filtre zinciri kurulur; sonraki hazırlıkta eski filtre temizlenir. |
| Seçenek hata izolasyonu | Tüm seçenekler tek `runCatching` içinde; ilk hata kalan ayarları atlayabiliyordu. | Her yazım ayrı korunur. Çalışma zamanındaki `gpu-api` yazımı kaldırıldı; görünümün init yolunda zaten OpenGL ES seçiliyor. |
| Lua köprüsü | Parent `user-data/aniyomi` string olarak gözleniyordu. ViewModel ise yaprak komut adları bekliyordu. | Aniyomi'deki 14 yaprak komut yolu ayrı string özellikleri olarak gözlenir ve listener'a iletilir. Boş reset olayları iletilmez. |
| Lua seek | ViewModel `seek_by`, `seek_to` ve metinli karşılıklarını işlemiyordu. | Var olan seek fonksiyonlarına yönlendirme eklendi. Hatalı sayılar göz ardı edilir, `|` sonrası metin korunur. |
| Track ID | `sid/aid/secondary-sid` yalnız INT64 olarak dinleniyordu; MPV'nin `no` değeri sayısal değildir. | Referanstaki gibi STRING gözlemi; kapalı secondary track `-1` olur. |
| Buffering durumu | İlk pause bildirimi dosya yüklenmeden READY yapabiliyor; pause bildirimi ENDED'ı da ezebiliyordu. | READY için dosya yüklenmesi, seek/cache durumları kontrol edilir; ENDED korunur. |
| Harici ses/altyazı yüklemesi | `loadfile` kuyruğa alınır alınmaz parça ekleniyordu; yüzey hazır değilse veya eski dosya kapanmamışsa erken kalabiliyordu. | Parçalar yeni dosyanın FILE_LOADED olayında eklenir; ikincil altyazı da burada uygulanır. Gerçek cihazla doğrulama gerekir. |
| Altyazı kapatma | Geciken otomatik seçim kullanıcının kapatma kararını ezebilirdi. | Kapatma da tamamlanmış manuel seçim olarak işaretlenir. |
| Stream bilgisi | `file-size`, video bitrate alanına aktarılıyordu. | `video-bitrate` okunur. Özellik mevcut değilse değer null kalır. |
| Init seçenekleri | YTDL kapatma ve referanstaki AV1 film-grain workaround'u yoktu. | `ytdl=no` ve `vd-lavc-film-grain=cpu` init seçenekleri eklendi; native sürüm uyumu cihazda kontrol edilmeli. |

Yeni `MpvPlaybackPolicy.kt`, ses sınırı, filtre zinciri, hazır olma koşulu ve script yollarını Android/JNI'den bağımsız tutar.

## Henüz kapanmamış açıklar / sonraki denetim

1. **Chapter zinciri:** MPV `chapter-list` gözlemi yalnız `updateTracks()` çağırıyor. ViewModel'in `updateChapters()` fonksiyonu var ancak main kaynak ağacında çağrısı bulunamadı. Bölüm işaretlerinin gerçekten motor → ViewModel → seekbar/sheet zincirinden taşınması ayrıca uygulanmalı. Chapter UI'sinin bulunması özelliğin çalıştığını kanıtlamıyor.
2. **TLS / CA:** `mpvTlsOptions` ve `applyMpvTlsOptions` yardımcıları mevcut fakat main kaynaklarda çağrıları bulunamadı. Önceki raporlardaki “TLS politikası uygulandı” iddiası çalışma yoluyla örtüşmüyor. Mevcut init içinde açıklama var, gerçek CA kurulum zinciri yok. Bu pakette trust-all eklenmedi; güvenli çözüm, gerçek ve güncellenebilir CA paketi, doğru dosya yolu ve sertifika doğrulamasıyla cihaz testidir. Media3'ün TLS davranışı da ayrıca denetlenmeli.
3. **Native sürüm / ABI / lisans:** Paketlenen libmpv, FFmpeg, libass ve decoder sürümleri ile Aniyomi'nin dağıttığı native bileşenler birebir karşılaştırılmadı. 16 KB page-size/ABI ve codec lisans yükümlülükleri ayrıca kontrol edilmeli. Kaynak kopyalamadan önce dosya başlıkları ve dağıtım bildirimleri korunmalı.
4. **Gelişmiş mpv.conf / input.conf / kullanıcı dosyaları:** Aniyomi'de config, script-opts, shaders ve fonts kopyalama zinciri var. Kitsugi'nin özel Lua düğmeleri bunun tamamının karşılığı değildir. Tercih editörü, depolama, güvenlik sınırları ve init sıralaması ayrı iş kalemi.
5. **Track yükleme/yeniden deneme:** Aynı URL'nin tekrar hazırlanması, harici track'in yüklenememesi, hızlı kaynak değiştirme ve eski END_FILE olayları gerçek MPV üzerinde test edilmeli. Mevcut dedup ve stale-event sayaçları tam durum makinesi denetiminden geçmedi.
6. **Ses ve altyazı tercihleri:** Pitch correction, kanal düzeninin yeniden yüklemede korunması, whitelist/blacklist, ASS override'ın geri kapatılması, font kurulumu ve tüm tercihlerin DataStore → runtime zinciri tek tek doğrulanmadı.
7. **UI/runtime:** Gestures, donanım klavyesi/input.conf, PiP, ekran döndürme, audio focus, kulaklık çıkarma, MediaSession, arka plan/ekran kilidi, autoplay, AniSkip, scrobble ve screenshot cihaz matrisi tamamlanmadı.
8. **Motorlar ve ekranlar:** Fullscreen MPV düzeltmeleri otomatik olarak inline/TV/offline/Media3 eşitliği sağlamaz. Her giriş noktası bağımsız test edilmeli.

## Doğrulama

- `git diff --check`: başarılı.
- `bash gradlew :app:testDebugUnitTest --no-daemon`: **başlatılamadı**. Ortamda `java` yok ve `JAVA_HOME` tanımlı değil. Kotlin/Android derlemesinin başarılı olduğu iddia edilmiyor.
- `MpvPlaybackPolicyTest`: 8 test eklendi (volume/mute, boost sınırları, sonlu olmayan değerler, CPU/GPU/YUV filtre kombinasyonları, 32 hazır olma kombinasyonu, Lua yaprak yolları).
- `MpvPlayerEngineTest`: gerçek varsayılan `AppSettings` kullanacak şekilde düzeltildi; dosya öncesi pause beklentisi güncellendi; Lua iletimi, secondary `no`, volume sınırı ve ENDED koruması için 4 test eklendi.
- **Bu testler çalıştırılmadı.** Bunlar native codec, ağ veya cihaz kararlılığı testi değildir.

## Çıkış kriterleri

JDK 17+ ve projeye uygun Android SDK bulunan ortamda:

```sh
bash gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon
# Cihaz/emülatör bağlıyken:
bash gradlew :app:connectedDebugAndroidTest --no-daemon
```

Gerçek cihazda en az: HLS (segment/header doğrulaması), DASH, MP4, MKV çoklu ses/ASS, harici ses + altyazı, AV1 film-grain, donanım/yazılım çözme, mute/boost, CPU/GPU debanding, kaynak değişimi/yeniden deneme, 404/TLS/ağ kesilmesi, PiP/rotasyon/arka plan ve uzun oynatma/bellek ölçümleri yapılmalı. Başarısız kaynakta bounded retry/fallback ve izleme konumunun korunması ayrıca doğrulanmalı.

**Bu kriterler sağlanmadan “Aniyomi ile tamamen aynı özellik ve sağlamlık” onayı verilmemeli.**
