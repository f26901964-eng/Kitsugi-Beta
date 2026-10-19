# Aniyomi ↔ Kitsugi: oynatıcı ve video motoru özellik karşılaştırması

> **Tarihsel başlangıç değerlendirmesi:** Aşağıdaki karşılaştırma uygulama öncesi durumu anlatır; sonraki kod değişiklikleri için [güncel plan ve doğrulama durumu](PLAN_TASK_ANIYOMI_PLAYER_PARITY_2026-10-19.md) esas alınmalıdır. Kaynak satır numaraları sonraki düzenlemelerle kaymış olabilir.


**Tarih:** 2026-10-19  
**Aniyomi referansı:** `97414446b8a95994c72dd33c41c971a89d4d25b8`  
**Kitsugi:** `arena/d8cd8be8-kitsugi-beta`, taban `2170f8b7e708fa6ca0faf2ebda0a39f895280e50`, önceki turdaki düzeltmeler dahil çalışma ağacı.

## 1. Net cevap

**Hayır, Kitsugi'nin oynatıcısı şu an Aniyomi ile özellik bakımından tamamen eşit değil.** Ortak MPV temeli ve benzer kontrol panelleri var; ancak bazı özelliklerin yalnız görünümü, tercihi veya motor fonksiyonu taşınmış. Eksikler sadece kozmetik değil: chapter/önbellek/önizleme veri akışı, ikinci altyazı seçimi, gelişmiş altyazı seçeneklerinin uygulanması, ses odağı, Lua köprü dosyası, kalıcı ayarların geri yüklenmesi ve PiP davranışında somut farklar var.

Bu belge, önceki ilk düzeltme raporunun yerine **özellik karşılaştırmasının ana kaydıdır**. Önceki rapor yapılan kod değişikliklerinin geçmişi olarak kalır. Bu turda yeni oynatıcı davranışı eklenmedi; karşılaştırma, kanıt envanteri ve önceliklendirme tamamlandı.

### Kapsam ve yöntem

- Referanstaki `ui/player` motor/görünüm, Activity, Observer, ViewModel, loader, utils, controls, sheets, panels, settings ve oyuncu ayar ekranlarının envanteri çıkarıldı.
- Referansın altı preference sınıfındaki **88 tercih** aşağıdaki matriste tek tek sınıflandırıldı. Torrent ve özel düğme/editör ekranları ayrıca kapsandı.
- Kitsugi'de UI callback → ViewModel → motor → MPV property yolları ve DataStore okuma/yazma bağlantıları incelendi. Fonksiyon/ayar bulunması tek başına tamamlanma kabul edilmedi.
- “Eksik” sonuçları yalnız aynı isimle aramaya dayanmıyor: eşdeğer fonksiyonlar, ayar tüketicileri, gerçek çağıranlar ve ekran bağlantıları da kontrol edildi. Otomatik sembol envanteri yardımcı kanıttır, davranış testi değildir.
- Kaynak matrisi native kütüphanenin binary içeriğinin veya her satırın manuel denetiminin yerine geçmez. Dış servislerin kalitesi, tüm eklenti sağlayıcılarının çalışması ve cihaz kararlılığı bu kaynak karşılaştırmasından kanıtlanamaz.
- Dosya, sembol, satır ve SHA-256 kayıtları: [Kaynak envanteri](ANIYOMI_PLAYER_SOURCE_INVENTORY_2026-10-19.md). Satır numaraları çalışma ağacının bu anına aittir.

### Durum kodları

- **B — Bağlı:** İlgili özellik için kaynak kodunda çalışan giriş → işlem bağlantısı mevcut. “Cihazda geçti” anlamına gelmez.
- **K — Kısmi:** UI, ayar veya motor desteği var fakat uçtan uca zincir eksik/çelişkili.
- **E — Eksik:** Referanstaki davranış için uygulama kaynaklarında eşdeğer bağlantı bulunamadı.
- **F — Farklı:** İşlevin karşılığı var ancak politika/mimari aynı değil; otomatik olarak hata sayılmamalı.
- **? — Doğrulanamadı:** Native binary, paketleme veya cihaz davranışı kaynakla belirlenemiyor.

**B dahil bütün satırlarda cihaz doğrulaması bekliyor.** Kaynaktan bir eşitlik yüzdesi veya sağlamlık puanı hesaplanmadı. Bir büyük özelliği küçük alt maddelere bölerek yanıltıcı yüzde üretmek doğru değil.

## 2. Motor, codec, ağ, yaşam döngüsü

| ID | Özellik | Aniyomi | Kitsugi ve sonuç | Durum / kanıt |
|---|---|---|---|---|
| M01 | Dahili video motoru | `aniyomi-mpv-lib:1.18.n`, MPVLib tabanlı | `mpv-android-lib:0.1.12` + ayrıca Media3. Aynı native dağıtım değil. | F, [A01], [K01] |
| M02 | MPV / FFmpeg / libass sürümleri | Ayrı MPV paketi; ayrıca FFmpeg-kit 1.18 | Maven koordinatından gerçek mpv/FFmpeg commit'i ve tüm codec flag'leri çıkarılamaz. Binary eşleştirmesi yapılmadı. | ?, [A01], [K01] |
| M03 | GPU / gpu-next | init'te renderer seçimi | Tercih UI/DataStore'dan `vo` yazımına bağlı; Kitsugi bunu init sonrasında uygular. Aynı başlatma sırası değil. | K, [A02], [K02] |
| M04 | HW decoding / yazılım | hwdec ve runtime decoder seçimi | hwdec ayarı ve runtime seçim mevcut; gerçek MediaCodec/FFmpeg fallback native teste bağlı. | B, [K02], [K04] |
| M05 | CPU / GPU debanding + YUV420P | gradfun, deband, vf seçenekleri | Önceki turda CPU yolu ve birleşik filtre zinciri düzeltildi. | B, [K02], [K03] |
| M06 | Demuxer cache | Android sürümüne göre 32/64 MiB ileri ve geri cache | Sabit 64 MiB init; tercihte ileri limit ve yaklaşık dörtte bir geri limit. 0 tercihi uygulamada açıkça sınırsız yapmıyor. | F/K, [A02], [K02], [K05] |
| M07 | TLS / CA güven zinciri | `tls-verify=yes`, CA dosyası kopyalama kodu | Açıklama/helper var ama `applyMpvTlsOptions` çağrısı yok; güvenilir CA kurulumu kanıtlanmadı. Mevcut native varsayılanını “doğrulama kapalı” diye varsaymamak gerekir. | K, [A02], [A03], [K05] |
| M08 | HTTP headers / Referer | kaynak/video header'ları MPV'ye aktarılır | Header'lar sıralanıp MPV listesi için escape edilir; boş kaynakta temizlenir. Sağlayıcı başına doğrulama gerekli. | B, [A04], [K06] |
| M09 | Kaynağın mpvArgs bilgisi | Video seçenekleri loadfile'a; indirilen medyanın metadata seçenekleri tekrar uygulanır | `PlayerEngine.prepare` sözleşmesi keyfi kaynak MPV seçeneklerini taşımıyor; metadata geri uygulama eşdeğeri bulunamadı. | E, [A05], [K07] |
| M10 | Harici ses ve altyazı URL'leri | fileLoaded → setupTracks | Önceki turdan sonra Kitsugi'de FILE_LOADED içinde ekleniyor. Hızlı geçiş/başarısız parça testi bekliyor. | B, [A05], [K08] |
| M11 | Dosya yöneticisinden video | content/file URI çözümleme | DownloadedPlayerActivity yönlendiriyor; ana video URI'sinin MPV fd çözümleme yolu, dışarıdan eklenen altyazı/ses yolu kadar açık değil. URI izin aktarımı ayrıca test edilmeli. | K, [A04], [K09], [K10] |
| M12 | Ses odağı / telefon kesintisi / duck | AudioFocus request, loss/transient/gain ve duck/restore | Media3 `handleAudioFocus=true`. MPV engine/fullscreen Activity için eşdeğer açık AudioFocus request ve duck/restore bağlantısı bulunamadı. Kütüphanenin dahili davranışı ayrıca incelenmeli. | K, [A06], [K11] |
| M13 | Kulaklık çıkınca durdurma | noisy receiver pause çağırır | Rota dedektörü noisy yayınını dinliyor ama rotayı/gecikmeyi güncellemek, MPV'yi pause etmekle aynı değil. MPV için açık pause bağlantısı bulunamadı. | K, [A06], [K12] |
| M14 | Native olaylarının thread'i | PlayerObserver tüm olayları UI thread'e taşır | MpvPlayerEngine doğrudan listener çağırıyor; yalnız bazı hata callback'leri main'e post ediliyor. Kütüphanenin callback thread garantisi doğrulanmadı. | ?, [A07], [K08] |
| M15 | Dosya sonu / pause / buffering | eof, pause, cache, seeking gözlenir | Erken READY/ENDED ezilmesi önceki turda düzeltildi; END_FILE nedenini yalnız sayaç/eof ile yorumlayan yolun yarış testi yok. | K, [K08], [K13] |
| M16 | Aynı URL retry / kaynak değiştirme | video/hoster yükleme akışı | Kitsugi dedup yapıyor. Aynı anahtarla prepare BUFFERING yapıp loadfile'ı atlayabilir; yeniden FILE_LOADED gelmeyebilir. Retry koşulları kesinleştirilmeli. | K, [K06], [K08] |
| M17 | Yapılandırma değişimi / geri dönüş | Activity onPause/onSaveInstanceState ve VM | Kitsugi Activity onStop'te PiP değilse pause/finish; Screen disposal'da motor release. Rotasyon/yeniden oluşturma aynı davranış değil. | F, [A06], [K14] |
| M18 | Ekran açık tutma / immersive | Activity window flags/cutout | Kitsugi fullscreen Activity immersive mod ve ekran açık tutma koduna sahip; kullanıcıya açık cutout/çubuk tercihleri eşit değil. | K, [K14] |
| M19 | Native log ve HTTP hata ayrıntısı | MPV LogObserver HTTP hatasını toplayıp kullanıcı hatasına ekler | MPV 9001/9002 hataları çoğunlukla genel metin. Media3 analytics/PlayerLogger var ama MPV native HTTP hata ayrıntısının aynısı değil. | K, [A07], [K08] |
| M20 | ABI / 16 KB page-size / HDR / codec | Aniyomi paketleme + native bağımlılıklar | APK/AAR/ELF ve gerçek cihaz olmadan eşitlik iddiası yok; Media3 DV politikası MPV native pipeline'ına otomatik uygulanmaz. | ?, [K01], [K11] |
| M21 | Play/pause sinyalinin UI'a taşınması | Referans pause olayını doğrudan VM paused state'ine taşır | Kitsugi motor isPlaying'i günceller ama READY→READY için onStateChanged yaymaz. Fullscreen listener isPlayingState'i yalnız onStateChanged içinde güncelliyor; ayrı playing callback'i yok. PiP/session ve progress döngüsü bayat kalabilir. | K, [K07], [K08], [K40] |


## 3. Aniyomi'nin 88 tercihinin tamamı

Tercih adları referanstaki gerçek sembollerdir. Kaynak bağlantıları envanterde her tercih için ayrı bulunur. “Yok” davranışın kullanıcı tarafından ayarlanabilir eşdeğerinin bulunmadığı anlamındadır; örneğin sabit bir animasyonun varlığı reduce-motion tercihi sayılmaz.

### 3.1 DecoderPreferences — 9/9

| Referans tercihi | Kitsugi karşılığı / fark | Durum |
|---|---|---|
| `tryHWDecoding` | `mpvHwdecMode`, `updateDecoder` → hwdec. | B, [K02], [K04] |
| `gpuNext` | `mpvGpuRenderer` UI → store → runtime vo; init sırası farklı. | K, [K02], [K15] |
| `videoDebanding` | `mpvDebandMode` → GPU deband / CPU gradfun. | B, [K02], [K03] |
| `useYUV420P` | `mpvForceYuv420p` → birleşik vf zinciri. Varsayılanları farklı. | B, [K02], [K03] |
| `brightnessFilter` | VideoFiltersPanel → Screen map → motor brightness; kalıcı geri yükleme yok. | K, [K16] |
| `saturationFilter` | Aynı runtime bağlantı; yalnız ekran state'i. | K, [K16] |
| `contrastFilter` | Aynı runtime bağlantı; yalnız ekran state'i. | K, [K16] |
| `gammaFilter` | Aynı runtime bağlantı; yalnız ekran state'i. | K, [K16] |
| `hueFilter` | Aynı runtime bağlantı; yalnız ekran state'i. | K, [K16] |

### 3.2 AudioPreferences — 5/5

| Referans tercihi | Kitsugi karşılığı / fark | Durum |
|---|---|---|
| `preferredAudioLanguages` | Bağımsız ses dili tercihi yok. MPV otomatik ses seçimi altyazı dil listesini kullanıyor ve Türkçe önceliği uyguluyor. | F/K, [K17] |
| `enablePitchCorrection` | Ayrı ayar ve `audio-pitch-correction` property bağlantısı bulunamadı; native varsayılanı “kullanıcı kontrolü var” sayılmaz. | E, [K02], [K18] |
| `audioChannels` | MoreSheet → VM → audio-channels/af mevcut; DataStore'daki değer VM başlarken geri yüklenmiyor. Media3'te aynı seçim no-op. | K, [K04], [K19] |
| `volumeBoostCap` | AppSettings yüzde 100–200; motor bu değeri direkt volume-max yapıyor. Aniyomi cap'i +100 ekleyerek yorumluyor; aynı sayısal model değil. | F, [K08], [K20] |
| `audioDelay` | Default delay + rota gecikmesi → engine.setAudioDelay. Negatif Media3 gecikmesinde MPV'ye geçiş var. | B, [K16], [K12] |

### 3.3 SubtitlePreferences — 21/21

| Referans tercihi | Kitsugi karşılığı / fark | Durum |
|---|---|---|
| `preferredSubLanguages` | `preferredSubtitleLanguages` → ortak seçim politikası. Türkçe/gömülü/harici önceliği Aniyomi'nin TrackSelect politikasıyla aynı değil. | F, [A08], [K17] |
| `subtitleWhitelist` | İsim bazlı kullanıcı whitelist tercihi/selection bağlantısı yok. | E, [A08], [K17] |
| `subtitleBlacklist` | İsim bazlı kullanıcı blacklist tercihi/selection bağlantısı yok. | E, [A08], [K17] |
| `screenshotSubtitles` | Screenshot sheet toggle motora bağlı; başlangıç state'i true, kalıcı tercih geri yükleme eşdeğeri yok. | K, [K21] |
| `subtitleFont` | AppSettings/Store alanı var. MPV init Roboto sabit; settings.subtitleFont motorda tüketilmiyor. | K, [K05], [K20] |
| `subtitleFontSize` | Basic style.size → MPV sub-scale, Media3 text size. Aniyomi sub-font-size ve ayrıca scale ayırıyor. | F, [K22] |
| `subtitleFontScale` | Store/model alanı var; kullanıcı değerinin motora uygulanması yok. Basic style size'dan türetilen scale bunu karşılamıyor. | K, [K20], [K22] |
| `subtitleBorderSize` | Motor settings.subtitleBorderSize okuyor. Ama view outline aç/kapat değerinden sonra tekrar border size yazılması kenarlık kapatmayı bozabilir. | K, [K22] |
| `boldSubtitles` | Style.bold panel → view sub-bold. Bold açılınca ayrıca ASS override zorlanıyor. | B/F, [K22] |
| `italicSubtitles` | UI/Store var. Motor yalnız ASS override kararında okuyor; `sub-italic` yazımı yok. | K, [K22], [K23] |
| `textColorSubtitles` | DefaultSubtitleColor → basic style → sub-color bağlantısı mevcut; ARGB/MPV renk yorumunun görsel testi gerekiyor. | B, [K22] |
| `borderColorSubtitles` | Model/Store/UI var; settings.subtitleBorderColor motorda tüketilmiyor. | K, [K23] |
| `borderStyleSubtitles` | Model/Store var; MPV border-style yazımı yok. | K, [K20], [K22] |
| `shadowOffsetSubtitles` | Yalnız değer >0 ise yazılıyor. 0'a dönüş gölgeyi açıkça sıfırlamıyor. | K, [K22] |
| `backgroundColorSubtitles` | UI/Store var; MPV sub-back-color bağlantısı yok. | K, [K23] |
| `subtitleJustification` | sub-align-x yazılıyor; Aniyomi sub-justify + ASS justify kullanıyor. Auto seçeneği eşit değil. | F/K, [K22] |
| `subtitlePos` | Store değeri kullanılmıyor; basic style.verticalOffset üzerinden farklı hesap var. | K, [K20], [K22] |
| `overrideSubsASS` | Store alanı var ama okunmuyor. Bold/italic nedeniyle force açıldıktan sonra no'ya dönüş yolu yok. | K, [K22] |
| `subtitlesDelay` | Runtime panel MPV'ye bağlı. Screen başlangıcı 0; kaydedilmiş subtitleDelayMs geri yükleme/panel değişikliğini kalıcılaştırma zinciri eksik. | K, [K16], [K24] |
| `subtitlesSpeed` | Runtime panel → sub-speed var; kalıcı bağımsız ayar/geri yükleme yok. | K, [K16] |
| `subtitlesSecondaryDelay` | Model/Store, Screen başlangıç okuması ve MPV yazımı var; panel değişikliğini store'a yazma yok. İkinci parça seçimi de eksik. | K, [K16], [K24], [K25] |

### 3.4 GesturePreferences — 13/13

| Referans tercihi | Kitsugi karşılığı / fark | Durum |
|---|---|---|
| `gestureVolumeBrightness` | Ses/parlaklık ayrı açık-kapalı ayarlarıyla GestureHandler'da okunur. | B, [K26] |
| `swapVolumeBrightness` | swipeVolumeBrightnessSides kullanılır; isim/true anlamına göre taraflar test edilmeli. | B, [K26] |
| `gestureHorizontalSeek` | horizontalSeekGestureEnabled → drag handler. | B, [K26] |
| `showSeekBar` | Seekbar görünürlük state'i var; Aniyomi'nin gesture sırasında seekbar göster tercihinin ayarlanabilir eşdeğeri bulunamadı. | K, [K26] |
| `defaultIntroLength` | Anime başına SharedPrefs ve 85 saniye fallback var; global skipIntroDurationSec varsayılanı 5 ve kontrol intro uzunluğu ile aynı zincir değil. | K/F, [K27] |
| `skipLengthPreference` | doubleTapSeekSeconds okunur. | B, [K26], [K27] |
| `playerSmoothSeek` | preciseSeeking → MPV exact seek / hızlı seek. | B, [K08], [K26] |
| `leftDoubleTapGesture` | Sol gesture sabit seek; Aniyomi PlayPause/Seek/Switch/Custom seçtirebiliyor. | K, [K27] |
| `centerDoubleTapGesture` | Merkez sabit play/pause; kullanıcı action seçimi yok. | K, [K27] |
| `rightDoubleTapGesture` | Sağ sabit seek; kullanıcı action seçimi yok. | K, [K27] |
| `mediaPreviousGesture` | Activity session'ın onSkipPrevious callback'i bağlanmamış; yapılandırılabilir gesture yok. | E/K, [K28] |
| `mediaPlayPauseGesture` | Play/pause callback'leri bağlı; Aniyomi action/custom-key özelleştirmesi yok. | K, [K28] |
| `mediaNextGesture` | Next callback bağlı; action/custom-key özelleştirmesi yok. | K, [K28] |

### 3.5 PlayerPreferences — 36/36

| Referans tercihi | Kitsugi karşılığı / fark | Durum |
|---|---|---|
| `preserveWatchingPosition` | HistoryRepository/WatchHistory ve resume diyaloğu var; aynı kapatılabilir tercih değil. | F, [K29] |
| `progressPreference` | Scrobble controller sabit %80; Aniyomi ayarlanabilir eşik, varsayılan %85. Bu yalnız AniList scrobble akışını gösterir; tüm hesap eşitlemesinin eşdeğerliği değildir. | F/K, [K30] |
| `defaultPlayerOrientationType` | Screen orientation state ve rotate kontrolü var; Aniyomi'nin kalıcı başlangıç yönü seçimi bulunamadı. | K, [K31] |
| `allowGestures` | Sheet/panel açıkken Kitsugi gesture'ları engelliyor; kullanıcı istisna tercihi yok. | F, [K26] |
| `showLoadingCircle` | Buffering spinner bağlı; kullanıcı kapatma tercihi bulunamadı. | K, [K19] |
| `showCurrentChapter` | CurrentChapter bileşeni/state var ama updateChapter çağıranı bulunamadı. | K, [K32] |
| `rememberPlayerBrightness` | Runtime brightness var; oturumlar arası hatırlama anahtarı/geri yükleme bulunamadı. | E/K, [K27] |
| `playerBrightnessValue` | currentBrightness runtime state'i var; kalıcı değer eşdeğeri değil. | K, [K27] |
| `rememberPlayerVolume` | Sistem stream sesini kullanır; Aniyomi'nin MPV sesini hatırlama tercihi yok. | E/K, [K27] |
| `playerVolumeValue` | currentVolume/currentMPVVolume runtime state'i var; kullanıcı tercihi olarak MPV sesini restore zinciri yok. | K, [K27] |
| `showFailedHosters` | Kitsugi farklı source repository kullanıyor; başarısız hoster'ları kullanıcı tercihiyle gösterme eşdeğeri bulunamadı. | F/E, [K33] |
| `showEmptyHosters` | Aynı şekilde boş hoster görünürlük tercihi yok. | F/E, [K33] |
| `playerFullscreen` | Immersive var; cutout/çentik alanını kullanmayı tercihle seçmek aynı değil. | K, [K14] |
| `hideControls` | Runtime göster/gizle var; kalıcı kontrolleri gizleme tercihi eşdeğeri bulunamadı. | K, [K19] |
| `displayVolPer` | Volume slider/overlay var; yüzde/başka sunum seçimi bulunamadı. | K, [K26] |
| `showSystemStatusBar` | Immersive sabit sistem bar gizleme; kullanıcı tercihi yok. | F, [K14] |
| `reduceMotion` | Player'a özel hareket azaltma tercihi/animation bağlantısı yok. | E, [K19] |
| `playerTimeToDisappear` | OSD hide süresi sabit `CONTROLS_HIDE_DELAY_MS`; ayarlanabilir değil. | K, [K19] |
| `panelOpacity` | Player panellerinin kullanıcıya açık ortak opaklık tercihi yok. | E, [K16] |
| `enableSkipIntro` | Intro skip kontrolü mevcut; Aniyomi'nin ayrı global göster/gizle tercihiyle aynı değil. | K, [K19], [K27] |
| `autoSkipIntro` | aniSkipAutoSkip → skipIntervals/position tick bağlantısı var. | B, [K34] |
| `enableNetflixStyleIntroSkip` | Aniyomi countdown/tarz seçimiyle birebir kullanıcı tercihi yok. | E, [K34] |
| `waitingTimeIntroSkip` | Aniyomi countdown bekleme süresi kullanıcı tercihi eşdeğeri yok. | E, [K34] |
| `aniSkipEnabled` | Ayar → controller → API → skip kontrolü var; MAL kimliği ve gerçek cevap testi gerekli. | B, [K34] |
| `disableAniSkipOnChapters` | Chapter bulunduğunda AniSkip'i devre dışı bırakma tercihi yok; chapter veri zinciri de eksik. | E, [K32], [K34] |
| `enablePip` | pipEnabled → Activity PiP girişi. | B, [K14], [K35] |
| `pipEpisodeToasts` | PiP bölüm değişim tostu için ayrı tercih yok. | E, [K35] |
| `pipOnExit` | Kitsugi PiP açıkken onUserLeaveHint'te paused olsa da giriş deniyor; ayrı on-exit tercihi yok. | F, [K14] |
| `pipReplaceWithPrevious` | PiP yalnız play/pause + next; previous/10 saniye skip seçimi yok. | E/K, [K35] |
| `alwaysUseExternalPlayer` | playerPreference=EXTERNAL ve ASK/INTERNAL/MPV mimarisi. | F, [K36] |
| `externalPlayerPreference` | preferredExternalPlayerPackage → intent. | B, [K36] |
| `playerSpeed` | UI → VM → motor ve Store; geçici hız değişiklikleri de kalıcı yazılıyor. | B/F, [K18] |
| `speedPresets` | Chip ekle/sil UI var ama sadece remember listesi; yeniden açılışta kaybolur. Referans kalıcı string set. | K, [K18] |
| `invertDuration` | Seekbar timer inversion state'i var; kalıcı tercih restore yok. | K, [K19] |
| `aspectState` | aspectMode → VM/motor bağlantısı var; görünümde en/boy oranı ve döndürülmüş video hesabı aynı değil. | B/F, [K37] |
| `autoplayEnabled` | isAutoplayEnabled + post-play/session kuralları. Kitsugi daha farklı/kapsamlı politika kullanıyor. | B/F, [K38] |

### 3.6 AdvancedPlayerPreferences — 4/4

| Referans tercihi | Kitsugi karşılığı / fark | Durum |
|---|---|---|
| `mpvUserFiles` | Aniyomi scripts/script-opts/shaders klasörlerini kullanıcı depolamasından kopyalar; Kitsugi yalnız kendi custombuttons.lua dosyasını üretir. | E/K, [A03], [K39] |
| `mpvConf` | Aniyomi editör → tercih → mpv.conf oluşturma → initialize. Kitsugi'de bu kullanıcı editörü/yazma zinciri yok. | E, [A03], [K05] |
| `mpvInput` | input.conf editörü/kurulum ve MPV onKey yönlendirme zinciri yok. Compose temel key handling bunun karşılığı değil. | E, [A03], [K31] |
| `playerStatisticsPage` | Runtime stats script-binding çağrıları ve Store yazımı var; saved page geri yüklenmiyor, native stats script varlığı doğrulanmadı. | K/?, [K04], [K20] |

## 4. Tercih listesi dışında kalan runtime / arayüz işlevleri

| ID | İşlev | Karşılaştırma / sonuç | Durum / kanıt |
|---|---|---|---|
| R01 | Oynat/duraklat, pozisyon ve süre | MPV ve Media3 engine contract'ına bağlı. | B, [K07], [K08], [K11] |
| R02 | Gömülü çoklu ses seçimi | Track snapshot → sheet → selectTrack bağlı. Decoder ve seçimin gerçekten değişmesi cihaz testi. | B, [K17], [K40] |
| R03 | Gömülü/harici birincil altyazı seçimi/kapat | Bağlı; önceki tur manuel kapatma koruması eklendi. | B, [K17], [K40] |
| R04 | İkinci altyazıyı aynı anda seçme | Aniyomi selectSub ikinci seçim ve selected pair taşır. Kitsugi selectSecondarySubtitleTrack yalnız tanımlı; UI çağıranı yok. İkinci gecikme slider'ı tek başına çift altyazı değildir. | K, [A09], [K25], [K40] |
| R05 | SAF ile ses/altyazı dosyası ekleme | Picker → VM openContentFd → audio/sub-add var; Media3 executeCommand no-op olduğu için bu yol MPV'ye özgü. UI'de motor kapısı/geçiş yok. | K, [K10], [K11] |
| R06 | Chapter okuma / seekbar / sheet | chapter-list sadece updateTracks çağırıyor; VM chapter listesi boş kalabiliyor. UI'nin olması veri akışını tamamlamıyor. | K, [A10], [K32] |
| R07 | Kaynak timestamp + AniSkip chapter birleştirme | Referans ChapterUtils birleşim/çakışma/boş segment yönetimi var. Kitsugi skipIntervals ayrı; chapter merge ve type bilgisi eşdeğeri yok. | E, [A10], [K34] |
| R08 | Read-ahead/buffer seekbar | Aniyomi demuxer-cache-time gözler. Kitsugi VM updateReadAhead tanımlı ama çağıranı yok; engine listener'da cache zaman bilgisi yok. | K, [K32], [K07] |
| R09 | Video thumbnail / tilemap önizlemesi | Referans source.getVideoThumbnails/getImageTile → VM → ThumbnailPreview. Kitsugi PreviewGenerator var fakat fullscreen'de yalnız import; aktif Seeker'a bitmap bağlanmıyor. | K, [A11], [K41] |
| R10 | Hızlı/hassas seek ve gösterge | Gestures, seekbar ve motor seek bağlı. Key/gesture tercihleri ve thumbnail eşit değil. | B/K, [K26], [K19] |
| R11 | Uzun basınca hız, bırakınca geri al | Runtime davranışı var; VM setPlaybackSpeed her iki adımda DataStore'a da yazıyor. Aniyomi geçici MPV speed değişimi yapar. | F, [K26], [K18] |
| R12 | Kontrol kilidi | VM lock/unlock ve GestureHandler kontrolü mevcut. | B, [K26], [K27] |
| R13 | Kalite / kaynak değiştirme | Sheet → resolveStreamUrl → changeStreamSource bağlı. Referanstaki hoster/video index modeli yerine farklı repository. | B/F, [K33], [K40] |
| R14 | Bölüm listesi / önceki / sonraki | Dialog/VM playEpisode bağlantıları var; MediaSession previous ve PiP previous ayrı eksik. | B/K, [K33], [K28] |
| R15 | Uyku zamanlayıcı | MoreSheet → startTimer → pause bağlı; VM'de ayrıca legacy sleep timer var. Süre/iptal/Activity lifecycle testi gerekli. | B, [K04] |
| R16 | Screenshot: altyazılı/altyazısız | İki motorda da yöntem var; MPV senkron arayüz içinde 800 ms'ye kadar Thread.sleep yapabiliyor. Referans capture akışıyla thread eşitliği yok. | K, [K21], [K08], [K11] |
| R17 | Screenshot kaydet/paylaş/kapak | Sheet → VM → MediaStore/FileProvider/DB bağlı. Thumbnail dosyası yazılıyor ama bölüme metadata bağı kanıtlanmadı; Background seçimi sistem duvar kâğıdı yapıyor. | B/K/F, [K21] |
| R18 | Özel Lua düğmeleri | Room → custombuttons.lua → load-script → script-message var. `aniyomi.lua` asset/kopyalama zinciri yok; aniyomi.* kullanan script uyumluluğu tamamlanmış değil. | K, [A12], [K39] |
| R19 | Lua kullanıcı-verisi köprüsü | 14 yaprak gözlemi ve VM komutları önceki turda düzeltildi; köprü dosyası kurulumu hâlâ ayrı eksik. | K, [K03], [K39] |
| R20 | Özel düğme düzenleyicisi / long press | CustomButton veri modeli/DAO ve UI listesi var; önceki startup ve script reload semantiği native teste muhtaç. | K, [K39] |
| R21 | Donanım klavyesi / media custom tuşları | Temel Compose DPAD/back/play işleme var; referanstaki MPV KeyMapping keydown/keyup/modifier forwarding yok. | K, [A02], [K31] |
| R22 | MediaSession | Play/pause/next bağlı; previous constructor default boş. ACTION_SEEK_TO ilan ediliyor ama onSeekTo callback'i yok. Gerçek playbackSpeed update'e aktarılmıyor. | K, [K28] |
| R23 | PiP aspect ve kontroller | Helper buildRational daima 16:9 döndürüyor; Activity engine yerine null yolluyor. Önceki/skip, başlık, kaynak rect/rotation oranı eşit değil. | K, [A13], [K35], [K14] |
| R24 | Torrent/magnet oynatma | Aniyomi TorrServer, Kitsugi ayrı P2pStreamingEngine backend'i kullanıyor. Torrent oynatma var diye aynı servis/proxy/port özellikleri var denemez. | F, [A14], [K42] |
| R25 | Torrent ayarları | Aniyomi enable/port/trackers/reset/proxyMode/proxyUrl. Kitsugi consent/upload/cache/profile/clear-on-close ve tracker birleşimi var; TorrServer port/proxy mode eşdeğeri yok. | F/K, [A14], [K42] |
| R26 | Harici oynatıcı handoff/result | Kitsugi package/header/subtitle/resume extras ve result contract taşıyor. Her üçüncü parti uygulamanın dönüşü/izleme eşitlemesi aynı değil. | B/F, [K36] |
| R27 | İzleme/history/hesaplar | Yerel history ve AniList scrobble bağlantıları var. Aniyomi tracker manager, delayed tracking ve download cleanup ile aynı işlem zinciri değil. | F/K, [A15], [K29], [K30] |
| R28 | Retry / motor ve kaynak fallback | Kitsugi çift dahili motor + bounded retry + kaynak geçişi/watchdog ekliyor. Referanstan farklı; sıfırlanan sayaçlar ve dedup/lifecycle yarışları test edilmeli. | F/K, [K13], [K33] |
| R29 | TV | TvPlayerScreen aynı fullscreen composable'ı kullanıyor; TV Activity, PiP ve session entegrasyonu telefon Activity'si ile otomatik ortak değil. | K, [K43] |
| R30 | Offline/dış URI | DownloadedPlayerActivity ana fullscreen Activity'ye yönlendiriyor. İç indirme metadata, altyazı dosyaları ve yerel URI yetkileri ayrıca test edilmeli. | K, [K09] |
| R31 | Inline/trailer | Ayrı player/lifecycle yolu var; fullscreen MPV özelliklerinin tamamının inline'a taşındığı söylenemez. | F, [K44] |
| R32 | MPV script medya bilgisi | Referans force-media-title ve current-anime/episode-title yazar. Kitsugi streamTitle bilgisi UI'da var ama aynı MPV user-data/title yazımı yok. | K, [A05], [K08] |

## 5. En önemli kırık zincirler — kesin yerler ve kabul kriterleri

### P0 — Güvenlik / ses / oynatma doğruluğu

1. **TLS gerçek çalışma yolu:** `applyMpvTlsOptions` yalnız tanımlı; çağıranı yok. Önceki rapordaki trust-all açıklamasını gerçek davranış sayma. CA üretimi/kopyalama + init seçenekleri bağlanmalı. Kabul: geçerli HTTPS oynar, geçersiz CA/hostname reddedilir, aynı kaynak Media3/MPV'de ölçülür; sessiz trust-all eklenmez.
2. **MPV AudioFocus/noisy:** Media3'teki otomatik odak MPV'yi kapsamıyor. Kabul: çağrı/başka medya, geçici odak, duck/restore, Bluetooth/kablolu çıkarma; kullanıcı pause'u odak dönüşünde yanlışlıkla play olmasın.
3. **Retry/dedup durum makinesi:** Aynı URL+headers+position için setMedia erken dönerken prepare BUFFERING yapıyor. Kabul: aynı kaynağa “Tekrar dene” gerçek bir yeni yükleme oluşturur veya mevcut state doğru korunur; sonsuz spinner yok; hızlı kaynak değişiminde eski END_FILE yeni kaynağı düşürmez.
4. **Play/pause ayrı sinyali:** State READY kalırken isPlaying değişmesi listener/UI tarafına açıkça bildirilmelidir. Kabul: UI/pause, Lua pause, PiP ve dış sistem pause sonrası MediaSession/ikon/progress loop aynı anda doğru durumu gösterir.
5. **Native callback thread garantisi:** UI/model/track listesi main thread sözleşmesiyle ele alınmalı veya lib callback garantisi belgelenmeli; dispose ve eşzamanlı listener değişimi test edilmeli.

### P1 — Aniyomi ile işlev eşitliğini doğrudan engelleyenler

(Aşağıdaki numaralar bu alt grubun iş kalemleridir.)

1. **Chapter:** engine listener'a chapter listesi + aktif chapter taşınmalı; metadata ve skip interval'ları birleştirilmeli; kaynak değişince temizlenmeli. Kabul: gerçek MKV chapter sheet/seekbar'da görünür, seçilen zaman oynar. Mevcut Seeker segment yapısında listenin `+ it` ile tekrar eklenmesi de giderilmeli.
2. **Read-ahead:** `demuxer-cache-time`/Media3 bufferedPosition → listener → VM → Seeker. Kabul: buffer çizgisi gerçekten hareket eder ve seek/source değişiminde sıfırlanır.
3. **Önizleme:** Kaynağın tile/VTT/thumbnail modeli ya da PreviewGenerator aktif seek UI'a bağlanmalı. Cache iptali, header, HLS desteği ve RAM bütçesi ölçülmeli. Import edilmiş sınıf “özellik var” değildir.
4. **Çift altyazı:** Sheet primary/secondary seçimi ayrı MPV ID ile taşımalı; `selectSecondarySubtitleTrack` gerçekten çağrılmalı. Kabul: iki farklı altyazı eşzamanlı ve ayrı delay ile görünür; Media3 yolu desteklemiyorsa MPV'ye açık geçiş yapılır.
5. **Gelişmiş altyazı:** italic/font/background/border color/style/ASS override/scale/position gerçek property'lere uygulanmalı; kapatma/sıfırlama da uygulanmalı. Kabul: her UI ayarı tek tek görsel test; uygulamayı yeniden açınca değer ve görünüm tutarlı.
6. **Kalıcı tercihler:** channel/stats/filter/sub-delay/speed presets restore zinciri kurulmalı. Speed “geçici”, “şimdiki”, “varsayılan” ayrılmalı. Kabul: paneli kapat/aç, kaynak/bölüm değiştir, uygulamayı yeniden başlat; aynı anlam korunur.
7. **Lua uyumluluğu:** Bridge asset/modülü, startup/reload, hata raporu ve izin sınırları tamamlanmalı. Kabul: aniyomi.show_text, seek, pause, button title ve integer picker scriptleri gerçekten çalışır. Referansın da potansiyel hataları körlemesine taşınmamalı: referans yaprakları gözlerken Activity string branch'i parent'a eşitlik kontrolü yapıyor.
8. **PiP/session:** gerçek görüntü oranı + rotation, paused giriş politikası, previous/skip ve session seek callback'leri. Kabul: dikey/4:3 video, Home/back/lock, PiP kapat, headset previous/next/seek.

### P2 — Tam kullanıcı deneyimi eşitliği

1. Config/input editörü ve script-opts/shader/font dosya kurulumları; başlangıçta doğru klasör ve doğru seçenek sırası.
2. Double-tap/media action özelleştirmeleri, loading/chapter/opacity/hide süreleri, reduce-motion, status bar/cutout ve hatırlanan brightness/volume tercihleri.
3. Hoster başarısız/boş görünürlük seçenekleri, chapter-AniSkip tercihleri ve torrent port/proxy seçenekleri: farklı Kitsugi mimarisine göre ürün kararı verilmeli; gereksiz backend kopyalanmamalı.

## 6. Kitsugi'de bulunan, Aniyomi ile aynı olmak zorunda olmayan ekler

- Media3 ↔ MPV dahili motor fallback ve özellik talebinde motor geçişi.
- Ses çıkış rotasına göre gecikme (BT/kablolu/HDMI/hoparlör).
- DV7/HDR10+ politikaları, AFR/çözünürlük ön kontrolü ve progressive parallel-range altyapısı; bunlar özellikle Media3 yoluna ait, MPV'de eşdeğer varsayılamaz.
- Still-watching, post-play/binge ve autoplay session limitleri.
- Ayrı P2P backend, consent/upload/cache/profile kontrolleri.
- Birden çok eklenti/kaynak tipini çözümleyen repository ve altyazı arama/indirme akışı.

Bunlar “Aniyomi'den iyi/daha sağlam” kanıtı değildir; sadece mimari/ürün farkıdır.

## 7. Doğrulama ve tamamlanma koşulu

### Bu tur gerçekten yapılan

- Referans commit sabitlendi; yerel çalışma ağacıyla dosya/simge/tercih envanteri üretildi.
- 88/88 referans preference adının matriste açık satırla bulunduğu otomatik kontrol edildi. Bu kontrol işlev testi değildir.
- Motor, callback, UI ve tercih tüketim yollarındaki yukarıdaki kanıtlar incelendi.
- Karşılaştırma script'i çalıştırıldı; rapor dosyaları ve diff biçimi kontrol edildi.
- **APK derleme, Kotlin/JUnit, lint, instrumentation ve cihaz/native testleri yapılmadı.** Önceki Gradle girişimi ortamda Java/JAVA_HOME olmadığı için başlamamıştı. Bu tur belge/envanter çalışması bu engeli aşmış sayılmaz.

### Cihaz doğrulama matrisi

| Alan | Minimum senaryo |
|---|---|
| Format/codec | HLS/DASH/MP4/MKV, H264/HEVC/VP9/AV1, çok kanallı ses, ASS/SSA/bitmap altyazı, HW/SW, unsupported codec |
| Ağ | Geçerli/geçersiz TLS, Referer/Cookie/Auth, 401/403/404/5xx, bağlantı kesilmesi, süreli URL yenileme, seek sonrası HTTP range |
| Kaynak | Aynı URL retry, hızlı A→B→A, başka bölüm, external audio/sub hata, embedded chapters, bilinmeyen süre/live |
| UI | 88 tercih satırı için değiştir→uygula→reset→yeniden başlat; Media3/MPV yetenek kapıları; iki altyazı; önizleme |
| Android | Rotasyon, process recreation, Home/back, PiP aç/kapat, ekran kilidi, arama/odak, kulaklık/BT, session tuşları |
| Performans | Uzun süre oynatma, repeated source/episode switch, ekran görüntüsü sırasında main-thread süre, RAM/cache/FD/observer sızıntısı |
| Paketleme | AAR/APK native ABI, 16 KB alignment, mpv/FFmpeg/libass sürümü ve build flag'leri, lisans bildirimleri |

**Son hüküm:** Kaynak düzeyindeki kapsamlı karşılaştırma eksikleri belirledi; işlevlerin tamamı uyarlanmış değil. Özellik eşitliği ancak yukarıdaki kırık zincirler tamamlanıp senaryo bazlı testler geçtikten sonra, sağlamlık ise bunun yanında uzun oynatma ve cihaz ölçümleriyle onaylanabilir.

## 8. Satır bazlı kanıt bağlantıları

- **A01:** [gradle/aniyomi.versions.toml:2](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/gradle/aniyomi.versions.toml#L2) — `aniyomi-mpv-lib`
- **A02:** [app/src/main/java/eu/kanade/tachiyomi/ui/player/AniyomiMPVView.kt:114](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/AniyomiMPVView.kt#L114) — `override fun initOptions`
- **A03:** [app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt:424](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L424) — `private fun setupPlayerMPV`
- **A04:** [app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt:1199](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L1199) — `fun parseVideoUrl`
- **A05:** [app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt:1280](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L1280) — `private fun fileLoaded`
- **A06:** [app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt:584](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L584) — `private fun setupPlayerAudio`
- **A07:** [app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerObserver.kt:13](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerObserver.kt#L13) — `override fun eventProperty`
- **A08:** [app/src/main/java/eu/kanade/tachiyomi/ui/player/utils/TrackSelect.kt:16](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/utils/TrackSelect.kt#L16) — `fun getPreferredTrackIndex`
- **A09:** [app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt:544](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L544) — `fun selectSub`
- **A10:** [app/src/main/java/eu/kanade/tachiyomi/ui/player/utils/ChapterUtils.kt:21](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/utils/ChapterUtils.kt#L21) — `fun mergeChapters`
- **A11:** [app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt:1580](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1580) — `suspend fun loadThumbnails`
- **A12:** [app/src/main/assets/aniyomi.lua:1](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/assets/aniyomi.lua#L1) — `aniyomi =`
- **A13:** [app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt:764](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L764) — `fun createPipParams`
- **A14:** [app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsTorrentScreen.kt:40](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsTorrentScreen.kt#L40) — `override fun getPreferences`
- **A15:** [app/src/main/java/eu/kanade/tachiyomi/ui/player/ExternalIntents.kt:60](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/ExternalIntents.kt#L60) — `class ExternalIntents`
- **K01:** [app/build.gradle.kts:410](../app/build.gradle.kts#L410) — `io.github.abdallahmehiz:mpv-android-lib`
- **K02:** [app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt:613](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L613) — `private fun applyInitOptions`
- **K03:** [app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlaybackPolicy.kt:4](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlaybackPolicy.kt#L4) — `internal object`
- **K04:** [app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt:390](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L390) — `fun updateDecoder`
- **K05:** [app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt:610](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L610) — `override fun initOptions`
- **K06:** [app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt:64](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L64) — `fun setMedia(`
- **K07:** [app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngine.kt:10](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngine.kt#L10) — `interface PlayerEngine`
- **K08:** [app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt:575](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L575) — `override fun event(eventId`
- **K09:** [app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadedPlayerActivity.kt:34](../app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadedPlayerActivity.kt#L34) — `private fun handleIntent`
- **K10:** [app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt:2231](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L2231) — `fun addAudio(`
- **K11:** [app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt:646](../app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt#L646) — `handleAudioFocus`
- **K12:** [app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerAudioController.kt:45](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerAudioController.kt#L45) — `fun startObserving`
- **K13:** [app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerErrorRecoveryController.kt:81](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerErrorRecoveryController.kt#L81) — `fun onPlaybackError`
- **K14:** [app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerActivity.kt:542](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerActivity.kt#L542) — `override fun onUserLeaveHint`
- **K15:** [app/src/main/java/com/kitsugi/animelist/ui/components/player/PlayerGeneralTab.kt:849](../app/src/main/java/com/kitsugi/animelist/ui/components/player/PlayerGeneralTab.kt#L849) — `val currentGpuName`
- **K16:** [app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt:1316](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt#L1316) — `PlayerPanelsHost(`
- **K17:** [app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt:729](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L729) — `private fun updateTracks`
- **K18:** [app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/PlaybackSpeedSheet.kt:42](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/PlaybackSpeedSheet.kt#L42) — `fun PlaybackSpeedSheet`
- **K19:** [app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/PlayerControls.kt:125](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/PlayerControls.kt#L125) — `val showLoadingCircle`
- **K20:** [app/src/main/java/com/kitsugi/animelist/data/settings/AppSettings.kt:224](../app/src/main/java/com/kitsugi/animelist/data/settings/AppSettings.kt#L224) — `val mpvGpuRenderer`
- **K21:** [app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt:1905](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1905) — `fun takeScreenshot`
- **K22:** [app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt:332](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L332) — `override fun setSubtitleStyle`
- **K23:** [app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiPlayerSettingsDialog.kt:984](../app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiPlayerSettingsDialog.kt#L984) — `val italicSub`
- **K24:** [app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt:348](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt#L348) — `var subtitleDelayMs`
- **K25:** [app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt:314](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L314) — `fun selectSecondarySubtitleTrack`
- **K26:** [app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/GestureHandler.kt:108](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/GestureHandler.kt#L108) — `val swapVolumeBrightness`
- **K27:** [app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt:491](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L491) — `fun handleLeftDoubleTap`
- **K28:** [app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerActivity.kt:424](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerActivity.kt#L424) — `mediaSessionHelper = PlayerMediaSessionHelper`
- **K29:** [app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt:1853](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1853) — `suspend fun getSavedPosition`
- **K30:** [app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerScrobbleController.kt:63](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerScrobbleController.kt#L63) — `if (progress >= 0.80f)`
- **K31:** [app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt:34](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt#L34) — `.onPreviewKeyEvent`
- **K32:** [app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt:457](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L457) — `fun updateChapters`
- **K33:** [app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt:1147](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1147) — `fun changeStreamSource`
- **K34:** [app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerSkipController.kt:62](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerSkipController.kt#L62) — `fun loadIntervals`
- **K35:** [app/src/main/java/com/kitsugi/animelist/core/player/PlayerPipHelper.kt:158](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerPipHelper.kt#L158) — `private fun buildRational`
- **K36:** [app/src/main/java/com/kitsugi/animelist/core/player/ExternalPlayerLauncher.kt:68](../app/src/main/java/com/kitsugi/animelist/core/player/ExternalPlayerLauncher.kt#L68) — `object ExternalPlayerLauncher`
- **K37:** [app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt:371](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L371) — `override fun setAspectMode`
- **K38:** [app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerAutoplayController.kt:26](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerAutoplayController.kt#L26) — `class PlayerAutoplayController`
- **K39:** [app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt:852](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L852) — `private suspend fun setupCustomButtons`
- **K40:** [app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt:1203](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt#L1203) — `PlayerSheetsHost(`
- **K41:** [app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PreviewGenerator.kt:14](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PreviewGenerator.kt#L14) — `class PreviewGenerator`
- **K42:** [app/src/main/java/com/kitsugi/animelist/core/p2p/P2pSettingsRepository.kt:9](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pSettingsRepository.kt#L9) — `object P2pSettingsRepository`
- **K43:** [app/src/main/java/com/kitsugi/animelist/ui/tv/player/TvPlayerScreen.kt:91](../app/src/main/java/com/kitsugi/animelist/ui/tv/player/TvPlayerScreen.kt#L91) — `KitsugiFullscreenPlayerScreen(`
- **K44:** [app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailPlayerComponents.kt:80](../app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailPlayerComponents.kt#L80) — `fun`

[A01]: https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/gradle/aniyomi.versions.toml#L2
[A02]: https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/AniyomiMPVView.kt#L114
[A03]: https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L424
[A04]: https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L1199
[A05]: https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L1280
[A06]: https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L584
[A07]: https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerObserver.kt#L13
[A08]: https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/utils/TrackSelect.kt#L16
[A09]: https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L544
[A10]: https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/utils/ChapterUtils.kt#L21
[A11]: https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1580
[A12]: https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/assets/aniyomi.lua#L1
[A13]: https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L764
[A14]: https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsTorrentScreen.kt#L40
[A15]: https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/ExternalIntents.kt#L60
[K01]: ../app/build.gradle.kts#L410
[K02]: ../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L613
[K03]: ../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlaybackPolicy.kt#L4
[K04]: ../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L390
[K05]: ../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L610
[K06]: ../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L64
[K07]: ../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngine.kt#L10
[K08]: ../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L575
[K09]: ../app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadedPlayerActivity.kt#L34
[K10]: ../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L2231
[K11]: ../app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt#L646
[K12]: ../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerAudioController.kt#L45
[K13]: ../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerErrorRecoveryController.kt#L81
[K14]: ../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerActivity.kt#L542
[K15]: ../app/src/main/java/com/kitsugi/animelist/ui/components/player/PlayerGeneralTab.kt#L849
[K16]: ../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt#L1316
[K17]: ../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L729
[K18]: ../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/PlaybackSpeedSheet.kt#L42
[K19]: ../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/PlayerControls.kt#L125
[K20]: ../app/src/main/java/com/kitsugi/animelist/data/settings/AppSettings.kt#L224
[K21]: ../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1905
[K22]: ../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L332
[K23]: ../app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiPlayerSettingsDialog.kt#L984
[K24]: ../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt#L348
[K25]: ../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L314
[K26]: ../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/GestureHandler.kt#L108
[K27]: ../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L491
[K28]: ../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerActivity.kt#L424
[K29]: ../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1853
[K30]: ../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerScrobbleController.kt#L63
[K31]: ../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt#L34
[K32]: ../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L457
[K33]: ../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1147
[K34]: ../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerSkipController.kt#L62
[K35]: ../app/src/main/java/com/kitsugi/animelist/core/player/PlayerPipHelper.kt#L158
[K36]: ../app/src/main/java/com/kitsugi/animelist/core/player/ExternalPlayerLauncher.kt#L68
[K37]: ../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L371
[K38]: ../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerAutoplayController.kt#L26
[K39]: ../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L852
[K40]: ../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt#L1203
[K41]: ../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PreviewGenerator.kt#L14
[K42]: ../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pSettingsRepository.kt#L9
[K43]: ../app/src/main/java/com/kitsugi/animelist/ui/tv/player/TvPlayerScreen.kt#L91
[K44]: ../app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailPlayerComponents.kt#L80
