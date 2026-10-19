# Oynatıcı karşılaştırması — kaynak envanteri

> **Tarihsel başlangıç değerlendirmesi:** Aşağıdaki karşılaştırma uygulama öncesi durumu anlatır; sonraki kod değişiklikleri için [güncel plan ve doğrulama durumu](PLAN_TASK_ANIYOMI_PLAYER_PARITY_2026-10-19.md) esas alınmalıdır. Kaynak satır numaraları sonraki düzenlemelerle kaymış olabilir.


Referans commit: `97414446b8a95994c72dd33c41c971a89d4d25b8`
Kitsugi tabanı: `2170f8b7e708fa6ca0faf2ebda0a39f895280e50` (çalışma ağacı değişiklikleri dahil).

Aniyomi: 78 Kotlin dosyası / 15677 satır.
Kitsugi seçili oynatıcı dizinleri: 142 Kotlin dosyası / 36801 satır.
Referans oynatıcı tercihlerinin 88/88 tanesi ana matriste isim bazında yer alıyor.

Bu sayılar otomatik envanter kapsamıdır; her satırın manuel incelendiğini veya test edildiğini ifade etmez.

## Tercih kapsama listesi

- [`mpvUserFiles`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/AdvancedPlayerPreferences.kt#L8)
- [`mpvConf`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/AdvancedPlayerPreferences.kt#L9)
- [`mpvInput`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/AdvancedPlayerPreferences.kt#L10)
- [`playerStatisticsPage`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/AdvancedPlayerPreferences.kt#L14)
- [`preferredAudioLanguages`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/AudioPreferences.kt#L11)
- [`enablePitchCorrection`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/AudioPreferences.kt#L12)
- [`audioChannels`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/AudioPreferences.kt#L13)
- [`volumeBoostCap`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/AudioPreferences.kt#L14)
- [`audioDelay`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/AudioPreferences.kt#L18)
- [`tryHWDecoding`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/DecoderPreferences.kt#L10)
- [`gpuNext`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/DecoderPreferences.kt#L11)
- [`videoDebanding`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/DecoderPreferences.kt#L12)
- [`useYUV420P`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/DecoderPreferences.kt#L13)
- [`brightnessFilter`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/DecoderPreferences.kt#L17)
- [`saturationFilter`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/DecoderPreferences.kt#L18)
- [`contrastFilter`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/DecoderPreferences.kt#L19)
- [`gammaFilter`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/DecoderPreferences.kt#L20)
- [`hueFilter`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/DecoderPreferences.kt#L21)
- [`gestureVolumeBrightness`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/GesturePreferences.kt#L11)
- [`swapVolumeBrightness`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/GesturePreferences.kt#L15)
- [`gestureHorizontalSeek`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/GesturePreferences.kt#L19)
- [`showSeekBar`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/GesturePreferences.kt#L20)
- [`defaultIntroLength`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/GesturePreferences.kt#L21)
- [`skipLengthPreference`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/GesturePreferences.kt#L22)
- [`playerSmoothSeek`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/GesturePreferences.kt#L23)
- [`leftDoubleTapGesture`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/GesturePreferences.kt#L27)
- [`centerDoubleTapGesture`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/GesturePreferences.kt#L28)
- [`rightDoubleTapGesture`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/GesturePreferences.kt#L29)
- [`mediaPreviousGesture`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/GesturePreferences.kt#L33)
- [`mediaPlayPauseGesture`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/GesturePreferences.kt#L34)
- [`mediaNextGesture`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/GesturePreferences.kt#L35)
- [`preserveWatchingPosition`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L11)
- [`progressPreference`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L15)
- [`defaultPlayerOrientationType`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L16)
- [`allowGestures`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L23)
- [`showLoadingCircle`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L24)
- [`showCurrentChapter`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L25)
- [`rememberPlayerBrightness`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L26)
- [`playerBrightnessValue`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L27)
- [`rememberPlayerVolume`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L28)
- [`playerVolumeValue`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L29)
- [`showFailedHosters`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L33)
- [`showEmptyHosters`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L34)
- [`playerFullscreen`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L38)
- [`hideControls`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L39)
- [`displayVolPer`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L40)
- [`showSystemStatusBar`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L41)
- [`reduceMotion`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L42)
- [`playerTimeToDisappear`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L43)
- [`panelOpacity`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L44)
- [`enableSkipIntro`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L48)
- [`autoSkipIntro`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L49)
- [`enableNetflixStyleIntroSkip`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L50)
- [`waitingTimeIntroSkip`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L54)
- [`aniSkipEnabled`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L55)
- [`disableAniSkipOnChapters`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L56)
- [`enablePip`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L60)
- [`pipEpisodeToasts`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L61)
- [`pipOnExit`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L62)
- [`pipReplaceWithPrevious`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L63)
- [`alwaysUseExternalPlayer`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L67)
- [`externalPlayerPreference`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L71)
- [`playerSpeed`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L75)
- [`speedPresets`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L76)
- [`invertDuration`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L80)
- [`aspectState`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L81)
- [`autoplayEnabled`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L85)
- [`preferredSubLanguages`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L18)
- [`subtitleWhitelist`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L19)
- [`subtitleBlacklist`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L20)
- [`screenshotSubtitles`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L24)
- [`subtitleFont`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L26)
- [`subtitleFontSize`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L27)
- [`subtitleFontScale`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L28)
- [`subtitleBorderSize`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L29)
- [`boldSubtitles`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L30)
- [`italicSubtitles`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L31)
- [`textColorSubtitles`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L33)
- [`borderColorSubtitles`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L35)
- [`borderStyleSubtitles`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L36)
- [`shadowOffsetSubtitles`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L40)
- [`backgroundColorSubtitles`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L41)
- [`subtitleJustification`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L46)
- [`subtitlePos`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L47)
- [`overrideSubsASS`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L49)
- [`subtitlesDelay`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L51)
- [`subtitlesSpeed`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L52)
- [`subtitlesSecondaryDelay`](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L53)

## Aniyomi

Dosya SHA-256 değerleri incelenen anı sabitler; sembol taraması davranış doğrulaması değildir.

### `app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsAdvancedScreen.kt`

54 satır · SHA-256 `1efad45e9ad2ae3ef1fa8771342b9482b1292acea230a2082cc9f2d04201cefa`

[`getTitleRes` L20](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsAdvancedScreen.kt#L20) · [`getPreferences` L23](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsAdvancedScreen.kt#L23)

### `app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsAudioScreen.kt`

102 satır · SHA-256 `d00b73a26917329624ee987ea7ae76cc17dd23fe6040d6fc49b6b311ac4e32e3`

[`getTitleRes` L24](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsAudioScreen.kt#L24) · [`getPreferences` L27](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsAudioScreen.kt#L27)

### `app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsDecoderScreen.kt`

56 satır · SHA-256 `ce4ed95d4efba54a63e85d88816a1c6c0c5fc0bdc42b9b29e566b398f0503d34`

[`getTitleRes` L20](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsDecoderScreen.kt#L20) · [`getPreferences` L23](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsDecoderScreen.kt#L23)

### `app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsGesturesScreen.kt`

269 satır · SHA-256 `7cdc23091072f128c957d78ed2bb98cc78bd426bc8f7244ade24c19a968366dd`

[`getTitleRes` L39](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsGesturesScreen.kt#L39) · [`getPreferences` L42](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsGesturesScreen.kt#L42) · [`getSlidersGroup` L54](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsGesturesScreen.kt#L54) · [`getSeekingGroup` L74](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsGesturesScreen.kt#L74) · [`getDoubleTapGroup` L133](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsGesturesScreen.kt#L133) · [`getMediaControlsGroup` L180](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsGesturesScreen.kt#L180) · [`SkipIntroLengthDialog` L227](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsGesturesScreen.kt#L227)

### `app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsMainScreen.kt`

240 satır · SHA-256 `0cace43c99bbd4fd7940f4f6eccdbd14df2f57291b9fdd9716a3b181321391ff`

[`Content` L58](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsMainScreen.kt#L58) · [`getPalerSurface` L63](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsMainScreen.kt#L63) · [`Content` L79](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsMainScreen.kt#L79)

### `app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsPlayerScreen.kt`

346 satır · SHA-256 `adfe4ea0efc905d51c0d168ebdf8991712a835c9a4cda896f7ad738c1c742628`

[`getTitleRes` L40](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsPlayerScreen.kt#L40) · [`getPreferences` L43](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsPlayerScreen.kt#L43) · [`getControlsGroup` L86](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsPlayerScreen.kt#L86) · [`getHosterGroup` L122](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsPlayerScreen.kt#L122) · [`getDisplayGroup` L142](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsPlayerScreen.kt#L142) · [`getIntroSkipGroup` L200](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsPlayerScreen.kt#L200) · [`getPipGroup` L262](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsPlayerScreen.kt#L262) · [`getExternalPlayerGroup` L297](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsPlayerScreen.kt#L297)

### `app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsSubtitleScreen.kt`

84 satır · SHA-256 `ccada60f5e765cfc5351b7f2eb73191d453f0f7d2ccc41ea5b38bc3a5fde0b1d`

[`getTitleRes` L20](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsSubtitleScreen.kt#L20) · [`getPreferences` L23](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsSubtitleScreen.kt#L23)

### `app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsTorrentScreen.kt`

188 satır · SHA-256 `5c41a8908bbc04956dd1686c36c9c2dcfaea82396bb1cd7c86e17dc0bb67d11c`

[`getTitleRes` L37](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsTorrentScreen.kt#L37) · [`getPreferences` L40](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsTorrentScreen.kt#L40)

### `app/src/main/java/eu/kanade/presentation/more/settings/screen/player/custombutton/PlayerSettingsCustomButtonScreen.kt`

100 satır · SHA-256 `bbf5a56bd153714bce0526217f7898df003f089f9014b9e9c80f2bdb363e0ae6`

[`Content` L27](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/custombutton/PlayerSettingsCustomButtonScreen.kt#L27)

### `app/src/main/java/eu/kanade/presentation/more/settings/screen/player/custombutton/PlayerSettingsCustomButtonScreenModel.kt`

166 satır · SHA-256 `b73f712c1139ac45076ba56112176d7132119822798bca6eb9363fd153109b8e`

[`createCustomButton` L51](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/custombutton/PlayerSettingsCustomButtonScreenModel.kt#L51) · [`togglePrimaryButton` L62](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/custombutton/PlayerSettingsCustomButtonScreenModel.kt#L62) · [`editCustomButton` L73](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/custombutton/PlayerSettingsCustomButtonScreenModel.kt#L73) · [`deleteCustomButton` L84](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/custombutton/PlayerSettingsCustomButtonScreenModel.kt#L84) · [`changeOrder` L95](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/custombutton/PlayerSettingsCustomButtonScreenModel.kt#L95) · [`showDialog` L106](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/custombutton/PlayerSettingsCustomButtonScreenModel.kt#L106) · [`dismissDialog` L115](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/custombutton/PlayerSettingsCustomButtonScreenModel.kt#L115)

### `app/src/main/java/eu/kanade/presentation/more/settings/screen/player/custombutton/components/CustomButtonDialogs.kt`

245 satır · SHA-256 `d92a19774ba927f1800aaa19da4dcca2a09afe9710eb1ed0e7674b0d38be6104`

[`CustomButtonButtonDialog` L40](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/custombutton/components/CustomButtonDialogs.kt#L40) · [`CustomButtonCreateDialog` L185](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/custombutton/components/CustomButtonDialogs.kt#L185) · [`CustomButtonEditDialog` L201](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/custombutton/components/CustomButtonDialogs.kt#L201) · [`CustomButtonDeleteDialog` L218](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/custombutton/components/CustomButtonDialogs.kt#L218)

### `app/src/main/java/eu/kanade/presentation/more/settings/screen/player/custombutton/components/CustomButtonListItem.kt`

100 satır · SHA-256 `4a5c3e201ae374ef5586af8dddb1a07d297e85aa1473f700189becaa479d161c`

Fonksiyon bildirimi yok (model / enum / sabitler olabilir).

### `app/src/main/java/eu/kanade/presentation/more/settings/screen/player/custombutton/components/CustomButtonScreen.kt`

140 satır · SHA-256 `ea858009e4f584934d7d6d123aaa60d66bbbee386678c0d2e95201d324b582ce`

[`CustomButtonScreen` L36](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/custombutton/components/CustomButtonScreen.kt#L36) · [`CustomButtonContent` L91](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/custombutton/components/CustomButtonScreen.kt#L91)

### `app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/PlayerSettingsEditorScreen.kt`

74 satır · SHA-256 `05677fc007e2ed015eebf644f108ac3e7cdfdce4e0d279a9729c819088d90092`

[`Content` L19](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/PlayerSettingsEditorScreen.kt#L19)

### `app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/PlayerSettingsEditorScreenModel.kt`

181 satır · SHA-256 `0e2209e89d6e484353f0e700f158a5f0a184edc80716eaa39dc126022d846ad0`

[`updateItems` L46](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/PlayerSettingsEditorScreenModel.kt#L46) · [`createFile` L54](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/PlayerSettingsEditorScreenModel.kt#L54) · [`editFile` L66](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/PlayerSettingsEditorScreenModel.kt#L66) · [`deleteFile` L78](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/PlayerSettingsEditorScreenModel.kt#L78) · [`isValidName` L90](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/PlayerSettingsEditorScreenModel.kt#L90) · [`showDialog` L108](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/PlayerSettingsEditorScreenModel.kt#L108) · [`dismissDialog` L112](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/PlayerSettingsEditorScreenModel.kt#L112) · [`onSelectType` L116](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/PlayerSettingsEditorScreenModel.kt#L116) · [`getFilePath` L120](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/PlayerSettingsEditorScreenModel.kt#L120) · [`getEditorListItems` L124](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/PlayerSettingsEditorScreenModel.kt#L124)

### `app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/codeeditor/CodeEditScreen.kt`

194 satır · SHA-256 `a9074bcc1627d103246b4d8dae441a268ff02cd527fc485279a99599038a6a37`

[`Content` L57](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/codeeditor/CodeEditScreen.kt#L57) · [`CodeEditorContent` L131](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/codeeditor/CodeEditScreen.kt#L131)

### `app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/codeeditor/CodeEditScreenModel.kt`

154 satır · SHA-256 `fa5552bb27676e786dde0e78b18dac06370a14b82bfe09fa2b3fad83fa4196e0`

[`showDialog` L76](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/codeeditor/CodeEditScreenModel.kt#L76) · [`dismissDialog` L80](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/codeeditor/CodeEditScreenModel.kt#L80) · [`onEdit` L84](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/codeeditor/CodeEditScreenModel.kt#L84) · [`save` L108](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/codeeditor/CodeEditScreenModel.kt#L108)

### `app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/codeeditor/Highlight.kt`

61 satır · SHA-256 `4d918658f13f720b513336a7551e60f116a673b51b667e7b1a48f7493ed6b9da`

Fonksiyon bildirimi yok (model / enum / sabitler olabilir).

### `app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/codeeditor/SyntaxHighlight.kt`

137 satır · SHA-256 `2feacab45392c3f4cf4f6835187ac99f3e657b118203a4cf454e6abca285a56a`

[`luaHighlight` L27](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/codeeditor/SyntaxHighlight.kt#L27) · [`confHighlight` L98](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/codeeditor/SyntaxHighlight.kt#L98)

### `app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/components/EditorDialogs.kt`

166 satır · SHA-256 `90684e1db162b1e333e8267227e3bfedbcc82b9716efa8fd08ff4d9a1fca5680`

[`FileCreateDialog` L26](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/components/EditorDialogs.kt#L26) · [`FileDeleteDialog` L110](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/components/EditorDialogs.kt#L110) · [`UnsavedChangesDialog` L140](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/components/EditorDialogs.kt#L140)

### `app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/components/EditorScreen.kt`

190 satır · SHA-256 `3d12106aaf236f63412abd2bb33313fc544379fe5b576ccd915fa20d15084a12`

[`EditorScreen` L40](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/components/EditorScreen.kt#L40) · [`EditorListContent` L99](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/components/EditorScreen.kt#L99) · [`FileListItem` L133](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/components/EditorScreen.kt#L133)

### `app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/components/EditorTypeDropdown.kt`

68 satır · SHA-256 `28e282e14952941356a0a387ae454648a2726a049772045d8963a5a33def2519`

[`EditorTypeDropDown` L25](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/components/EditorTypeDropdown.kt#L25)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/AniyomiMPVView.kt`

290 satır · SHA-256 `8813c7d41c839b1d0f75c4e0fd8c97a1b44e5f3db7b9accec76f8d2b2523a0fe`

[`getPropertyInt` L52](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/AniyomiMPVView.kt#L52) · [`getPropertyBoolean` L56](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/AniyomiMPVView.kt#L56) · [`getPropertyDouble` L60](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/AniyomiMPVView.kt#L60) · [`getPropertyString` L64](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/AniyomiMPVView.kt#L64) · [`getVideoOutAspect` L88](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/AniyomiMPVView.kt#L88) · [`getValue` L96](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/AniyomiMPVView.kt#L96) · [`setValue` L101](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/AniyomiMPVView.kt#L101) · [`initOptions` L114](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/AniyomiMPVView.kt#L114) · [`observeProperties` L158](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/AniyomiMPVView.kt#L158) · [`postInitOptions` L162](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/AniyomiMPVView.kt#L162) · [`onKey` L171](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/AniyomiMPVView.kt#L171) · [`setupAudioOptions` L254](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/AniyomiMPVView.kt#L254) · [`setupSubtitlesOptions` L261](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/AniyomiMPVView.kt#L261)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/ExternalIntents.kt`

610 satır · SHA-256 `9efd2785db19934d4f27bc1e64265f5a5a61788ea4fffa95e0a19f2c495c5bcd`

[`getExternalIntent` L80](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/ExternalIntents.kt#L80) · [`initAnime` L120](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/ExternalIntents.kt#L120) · [`getVideoUrl` L137](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/ExternalIntents.kt#L137) · [`getLastSecondSeen` L173](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/ExternalIntents.kt#L173) · [`makeErrorToast` L190](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/ExternalIntents.kt#L190) · [`getIntentForPackage` L202](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/ExternalIntents.kt#L202) · [`webVideoCasterIntent` L209](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/ExternalIntents.kt#L209) · [`standardIntentForPackage` L242](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/ExternalIntents.kt#L242) · [`addExtrasAndFlags` L280](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/ExternalIntents.kt#L280) · [`addVideoHeaders` L297](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/ExternalIntents.kt#L297) · [`getMime` L317](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/ExternalIntents.kt#L317) · [`getComponent` L332](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/ExternalIntents.kt#L332) · [`isPackageInstalled` L355](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/ExternalIntents.kt#L355) · [`onActivityResult` L371](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/ExternalIntents.kt#L371) · [`saveEpisodeHistory` L441](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/ExternalIntents.kt#L441) · [`saveEpisodeProgress` L457](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/ExternalIntents.kt#L457) · [`deleteEpisodeIfNeeded` L495](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/ExternalIntents.kt#L495) · [`updateTrackEpisodeSeen` L524](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/ExternalIntents.kt#L524) · [`enqueueDeleteSeenEpisodes` L570](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/ExternalIntents.kt#L570) · [`newIntent` L592](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/ExternalIntents.kt#L592)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/PipActions.kt`

113 satır · SHA-256 `ebcf4c6a6ff0931ff5e5439e73272247200fff07754e5a1a55093ae57187691c`

[`createPipActions` L32](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PipActions.kt#L32) · [`createPipAction` L84](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PipActions.kt#L84)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt`

1413 satır · SHA-256 `6b86af938f4f79071485aa48391d687d7f9986b19683e7d44f56db788f31febb`

[`onReceive` L149](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L149) · [`newIntent` L158](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L158) · [`onNewIntent` L183](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L183) · [`onCreate` L233](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L233) · [`onDestroy` L297](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L297) · [`onPause` L325](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L325) · [`onStop` L344](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L344) · [`onUserLeaveHint` L358](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L358) · [`onBackPressed` L366](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L366) · [`onStart` L379](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L379) · [`executeMPVCommand` L412](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L412) · [`setupPlayerMPV` L424](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L424) · [`copyUserFiles` L450](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L450) · [`copyAssets` L490](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L490) · [`copyFontsDirectory` L517](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L517) · [`setupCustomButtons` L535](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L535) · [`setupPlayerAudio` L584](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L584) · [`onResume` L634](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L634) · [`onConfigurationChanged` L650](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L650) · [`showToast` L659](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L659) · [`onObserverEvent` L665](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L665) · [`onObserverEvent` L681](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L681) · [`onObserverEvent` L692](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L692) · [`onObserverEvent` L731](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L731) · [`onObserverEvent` L745](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L745) · [`event` L753](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L753) · [`createPipParams` L764](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L764) · [`onPictureInPictureModeChanged` L799](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L799) · [`onReceive` L813](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L813) · [`setupPlayerOrientation` L835](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L835) · [`onKeyDown` L854](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L854) · [`onKeyUp` L881](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L881) · [`setupMediaSession` L886](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L886) · [`onPlay` L894](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L894) · [`onPause` L911](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L911) · [`onSkipToPrevious` L928](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L928) · [`onSkipToNext` L945](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L945) · [`onStop` L962](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L962) · [`onSaveInstanceState` L990](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L990) · [`changeEpisode` L1002](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L1002) · [`setVideo` L1064](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L1064) · [`setInitialEpisodeError` L1143](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L1143) · [`torrentLinkHandler` L1153](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L1153) · [`parseVideoUrl` L1199](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L1199) · [`setHttpOptions` L1204](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L1204) · [`onShareImageResult` L1229](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L1229) · [`onSaveImageResult` L1244](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L1244) · [`onSetAsArtResult` L1259](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L1259) · [`fileLoaded` L1280](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L1280) · [`setMpvOptions` L1310](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L1310) · [`setupTracks` L1337](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L1337) · [`setupChapters` L1361](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L1361) · [`setMpvMediaTitle` L1386](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L1386) · [`endFile` L1408](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt#L1408)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerEnums.kt`

171 satır · SHA-256 `e0ef5393cc9035ae1bc678a9eacd16a400665f31ef6be17cbf7a068f9e987042`

[`getDecoderFromValue` L89](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerEnums.kt#L89)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerObserver.kt`

61 satır · SHA-256 `db0b4b5c18d84cb36610ac77008fa35b4bb039870946e1257f7ff8ea91878882`

[`eventProperty` L13](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerObserver.kt#L13) · [`eventProperty` L17](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerObserver.kt#L17) · [`eventProperty` L21](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerObserver.kt#L21) · [`eventProperty` L25](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerObserver.kt#L25) · [`eventProperty` L29](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerObserver.kt#L29) · [`event` L33](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerObserver.kt#L33) · [`efEvent` L37](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerObserver.kt#L37) · [`logMessage` L51](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerObserver.kt#L51)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerUtils.kt`

55 satır · SHA-256 `577b00fb4292c85b9bbe11ef583d55291f9e6ab01c2b16ba21b5c9b6a7e998b6`

Fonksiyon bildirimi yok (model / enum / sabitler olabilir).

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt`

2177 satır · SHA-256 `358daf7a0bc53372e07a018159e6d0d281d73db6df7c2f28483046724f2ebb3b`

[`create` L148](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L148) · [`removeEldestEntry` L260](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L260) · [`startTimer` L352](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L352) · [`isEpisodeOnline` L366](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L366) · [`updateIsLoadingEpisode` L377](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L377) · [`updateEpisodeList` L381](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L381) · [`getDecoder` L385](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L385) · [`updateDecoder` L389](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L389) · [`loadTracks` L419](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L419) · [`onFinishLoadingTracks` L455](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L455) · [`loadChapters` L479](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L479) · [`updateChapters` L496](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L496) · [`selectChapter` L500](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L500) · [`updateChapter` L505](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L505) · [`addAudio` L510](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L510) · [`selectAudio` L523](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L523) · [`updateAudio` L527](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L527) · [`addSubtitle` L531](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L531) · [`selectSub` L544](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L544) · [`updateSubtitle` L563](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L563) · [`updatePlayBackPos` L567](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L567) · [`updateSeekPos` L574](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L574) · [`createThumbnail` L610](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L610) · [`updateIsSeeking` L615](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L615) · [`updateReadAhead` L622](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L622) · [`updatePausedState` L626](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L626) · [`setPausedState` L632](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L632) · [`pauseUnpause` L644](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L644) · [`pause` L652](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L652) · [`unpause` L660](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L660) · [`showControls` L666](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L666) · [`hideControls` L679](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L679) · [`hideSeekBar` L684](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L684) · [`showSeekBar` L688](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L688) · [`lockControls` L693](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L693) · [`unlockControls` L697](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L697) · [`dismissSheet` L701](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L701) · [`resetDismissSheet` L705](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L705) · [`showSheet` L709](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L709) · [`showPanel` L721](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L721) · [`showDialog` L732](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L732) · [`seekBy` L743](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L743) · [`seekTo` L747](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L747) · [`changeBrightnessTo` L752](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L752) · [`displayBrightnessSlider` L761](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L761) · [`changeVolumeBy` L766](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L766) · [`changeVolumeTo` L779](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L779) · [`changeMPVVolumeTo` L789](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L789) · [`setMPVVolume` L793](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L793) · [`displayVolumeSlider` L798](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L798) · [`setAutoPlay` L802](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L802) · [`changeVideoAspect` L813](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L813) · [`cycleScreenRotations` L839](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L839) · [`handleLuaInvocation` L856](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L856) · [`showButton` L934](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L934) · [`forceShowSoftwareKeyboard` L966](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L966) · [`forceHideSoftwareKeyboard` L970](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L970) · [`seekToWithText` L978](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L978) · [`seekByWithText` L986](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L986) · [`updateSeekAmount` L994](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L994) · [`updateSeekText` L998](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L998) · [`leftSeek` L1002](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1002) · [`rightSeek` L1011](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1011) · [`resetState` L1023](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1023) · [`changeEpisode` L1034](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1034) · [`handleLeftDoubleTap` L1051](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1051) · [`handleCenterDoubleTap` L1067](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1067) · [`handleRightDoubleTap` L1081](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1081) · [`onCleared` L1097](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1097) · [`filterEpisodeList` L1146](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1146) · [`getCurrentEpisodeIndex` L1187](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1187) · [`getAdjacentEpisodeId` L1191](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1191) · [`updateHasNextEpisode` L1201](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1201) · [`updateHasPreviousEpisode` L1205](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1205) · [`showEpisodeListDialog` L1209](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1209) · [`onSaveInstanceStateNonConfigurationChange` L1219](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1219) · [`updateIsLoadingHosters` L1228](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1228) · [`needsInit` L1235](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1235) · [`init` L1252](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1252) · [`updateEpisode` L1330](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1330) · [`initEpisodeList` L1336](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1336) · [`cancelHosterVideoLinksJob` L1359](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1359) · [`loadHosters` L1366](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1366) · [`loadVideo` L1459](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1459) · [`updateVideo` L1528](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1528) · [`onVideoClicked` L1532](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1532) · [`onHosterClicked` L1558](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1558) · [`loadThumbnails` L1580](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1580) · [`loadEpisode` L1621](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1621) · [`onSecondReached` L1658](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1658) · [`updateEpisodeProgressOnComplete` L1688](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1688) · [`downloadNextEpisodes` L1712](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1712) · [`deleteEpisodeIfNeeded` L1740](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1740) · [`saveCurrentEpisodeWatchingProgress` L1756](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1756) · [`saveWatchingProgress` L1763](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1763) · [`saveEpisodeProgress` L1774](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1774) · [`saveEpisodeHistory` L1792](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1792) · [`bookmarkEpisode` L1805](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1805) · [`fillermarkEpisode` L1819](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1819) · [`takeScreenshot` L1830](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1830) · [`saveImage` L1847](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1847) · [`shareImage` L1886](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1886) · [`setAsArt` L1915](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1915) · [`updateTrackEpisodeSeen` L1947](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1947) · [`enqueueDeleteSeenEpisodes` L1963](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1963) · [`deletePendingEpisodes` L1975](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1975) · [`getAnimeSkipIntroLength` L1984](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1984) · [`setAnimeSkipIntroLength` L1999](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L1999) · [`generateFilename` L2013](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L2013) · [`aniSkipResponse` L2031](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L2031) · [`setChapter` L2063](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L2063) · [`updateSkipIntroButton` L2104](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L2104) · [`showSkipIntroButton` L2117](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L2117) · [`onSkipIntro` L2133](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L2133) · [`getCurrentChapter` L2150](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L2150) · [`setPrimaryCustomButtonTitle` L2156](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt#L2156)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/BottomLeftPlayerControls.kt`

86 satır · SHA-256 `acb0f2626e7ef326f1a21722720ff8f1347ebc03579661cefea7e3e5682094f6`

[`BottomLeftPlayerControls` L43](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/BottomLeftPlayerControls.kt#L43)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/BottomRightPlayerControls.kt`

70 satır · SHA-256 `d4c7e541a77ce3c64f101cd54ff90c75e35b14f42b84d9f058327afdc9333d38`

[`BottomRightPlayerControls` L33](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/BottomRightPlayerControls.kt#L33)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/GestureHandler.kt`

363 satır · SHA-256 `875d76458cab76443a2d0f52cd84db9e1a9c9d431ad6138f3b4f4c5fbcf9805d`

[`GestureHandler` L77](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/GestureHandler.kt#L77) · [`DoubleTapToSeekOvals` L306](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/GestureHandler.kt#L306) · [`calculateNewVerticalGestureValue` L349](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/GestureHandler.kt#L349) · [`calculateNewVerticalGestureValue` L353](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/GestureHandler.kt#L353) · [`calculateNewHorizontalGestureValue` L357](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/GestureHandler.kt#L357) · [`calculateNewHorizontalGestureValue` L361](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/GestureHandler.kt#L361)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/MiddlePlayerControls.kt`

144 satır · SHA-256 `967bb47353f3ec513d1db816ab95788ead5e9322efdeb539d17eb910abab2c48`

[`MiddlePlayerControls` L43](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/MiddlePlayerControls.kt#L43)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/PlayerControls.kt`

682 satır · SHA-256 `7140ecac85fc1e271f78fdee751d48c0f18949c02ad5398fca4aa7ac74562a60`

[`PlayerControls` L95](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/PlayerControls.kt#L95) · [`playerControlsExitAnimationSpec` L674](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/PlayerControls.kt#L674) · [`playerControlsEnterAnimationSpec` L679](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/PlayerControls.kt#L679)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/PlayerDialogs.kt`

54 satır · SHA-256 `ca2b270e5439c1b552f467cef67c309f290da4fb75727b0e2b77171211d98b4f`

[`PlayerDialogs` L11](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/PlayerDialogs.kt#L11)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/PlayerPanels.kt`

92 satır · SHA-256 `d09bb5530b1b11f57da896fca28494c42b7a91b77b80d50f00038f65eaf23841`

[`PlayerPanels` L46](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/PlayerPanels.kt#L46)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/PlayerSheets.kt`

198 satır · SHA-256 `c328714086c72fc9ca8ba10886b27dc4da50a6a7ca64826e8a6bdeda44e25a37`

[`PlayerSheets` L44](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/PlayerSheets.kt#L44)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/TopLeftPlayerControls.kt`

91 satır · SHA-256 `191eb6436517c6c3398fb7a5f82f8949521279078542172b1bd0c69a1369ac07`

[`TopLeftPlayerControls` L40](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/TopLeftPlayerControls.kt#L40) · [`TopLeftPlayerControlsPreview` L84](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/TopLeftPlayerControls.kt#L84)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/TopRightPlayerControls.kt`

101 satır · SHA-256 `5a2d498dd190f86a7f864a04619e56dcb57d21e92d075d98b5e5b78c75bae1bf`

[`TopRightPlayerControls` L39](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/TopRightPlayerControls.kt#L39)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/AutoPlaySwitch.kt`

94 satır · SHA-256 `35a8892f6280333fc8aab7bda52f70c8b16b90bc13448037fc13d9a056890958`

[`AutoPlaySwitch` L33](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/AutoPlaySwitch.kt#L33)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/BrightnessOverlay.kt`

33 satır · SHA-256 `b420154d5066f0ee2bc6e9cfe147c6b5e145f8ed5c7e1cc1ab527a31ec7e090c`

[`BrightnessOverlay` L14](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/BrightnessOverlay.kt#L14)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/ControlsButton.kt`

171 satır · SHA-256 `fc37f390b9aec34f66db9138bf4698a752ca42792dca7edfcd85409768785664`

[`ControlsButton` L48](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/ControlsButton.kt#L48) · [`ControlsButton` L95](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/ControlsButton.kt#L95) · [`FilledControlsButton` L133](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/ControlsButton.kt#L133) · [`PreviewControlsButton` L166](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/ControlsButton.kt#L166)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/CurrentChapter.kt`

122 satır · SHA-256 `21f241ddb31f63d81ae2d5a6b34f562c08a3e56e78bf0f8f8b7c0182325569d5`

[`CurrentChapter` L53](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/CurrentChapter.kt#L53)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/DoubleTapSeekTriangles.kt`

62 satır · SHA-256 `bad2cec04007159c7d191ca033e326f8b8ce881224bf11c767cccf31337a2421`

[`DoubleTapSeekTriangles` L21](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/DoubleTapSeekTriangles.kt#L21) · [`DoubleTapArrow` L51](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/DoubleTapSeekTriangles.kt#L51)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/PlayerUpdates.kt`

58 satır · SHA-256 `f259502383210719764394691bed9829ea9a279344f59e40501be196ea593384`

[`PlayerUpdate` L36](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/PlayerUpdates.kt#L36) · [`TextPlayerUpdate` L51](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/PlayerUpdates.kt#L51)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/SeekBar.kt`

190 satır · SHA-256 `e3d83eea709101123d48d6f4bd5bbbdeb82816467a62637a5295a1948c3bfe73`

[`toSegment` L65](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/SeekBar.kt#L65) · [`SeekbarWithTimers` L69](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/SeekBar.kt#L69) · [`VideoTimer` L151](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/SeekBar.kt#L151) · [`PreviewSeekBar` L175](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/SeekBar.kt#L175)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/ThumbnailPreview.kt`

158 satır · SHA-256 `d7882e9d8c86d86031d61455d1ab9a9b5295fdd91220d90d41695c813ab74c70`

[`ThumbnailPreview` L46](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/ThumbnailPreview.kt#L46)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/VerticalSliders.kt`

257 satır · SHA-256 `2a557bdc2803882b35af4f229afaf71aed9eea88cfe87351b4240c71c4966c97`

[`percentage` L58](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/VerticalSliders.kt#L58) · [`percentage` L62](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/VerticalSliders.kt#L62) · [`VerticalSlider` L67](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/VerticalSliders.kt#L67) · [`VerticalSlider` L83](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/VerticalSliders.kt#L83) · [`VerticalSliderInternal` L99](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/VerticalSliders.kt#L99) · [`BrightnessSlider` L140](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/VerticalSliders.kt#L140) · [`VolumeSlider` L176](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/VerticalSliders.kt#L176)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/dialogs/EpisodeListDialog.kt`

244 satır · SHA-256 `228d10b28406706cee16349cffebaaa47f5bca4fd5d7fea99a9e00ccef633c82`

[`EpisodeListDialog` L52](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/dialogs/EpisodeListDialog.kt#L52) · [`EpisodeListItem` L125](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/dialogs/EpisodeListDialog.kt#L125)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/dialogs/IntegerPickerDialog.kt`

46 satır · SHA-256 `f777018f678d81d35185607e3e121cd98a756322990c28eb974f341a74bcd899`

[`IntegerPickerDialog` L12](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/dialogs/IntegerPickerDialog.kt#L12)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/dialogs/PlayerDialog.kt`

74 satır · SHA-256 `3b8ee690ef214bfe9d792efc0395b4ec431c9bcfb43c9ad9943991eb150c936a`

[`PlayerDialog` L21](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/dialogs/PlayerDialog.kt#L21)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/panels/AudioDelayPanel.kt`

105 satır · SHA-256 `4d8a288fdda9f3a646a15145301d1d64772cf13a0da529d87466708f45ecdbb2`

[`AudioDelayPanel` L51](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/panels/AudioDelayPanel.kt#L51) · [`AudioDelayCardTitle` L84](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/panels/AudioDelayPanel.kt#L84)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/panels/SubtitleDelayPanel.kt`

373 satır · SHA-256 `0bab7e32675e002a2ba09f6dbed79b90b357ec70c52434c59c9d1fe5256864fe`

[`SubtitleDelayPanel` L75](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/panels/SubtitleDelayPanel.kt#L75) · [`SubtitleDelayCard` L149](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/panels/SubtitleDelayPanel.kt#L149) · [`DelayCard` L204](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/panels/SubtitleDelayPanel.kt#L204) · [`SubtitleDelayTitle` L321](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/panels/SubtitleDelayPanel.kt#L321)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/panels/SubtitleSettingsColorsCard.kt`

251 satır · SHA-256 `5a09dd17402fa24810b6493e3248af368198d646884e730328276f686f2ab557`

[`SubtitleSettingsColorsCard` L71](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/panels/SubtitleSettingsColorsCard.kt#L71) · [`resetColors` L180](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/panels/SubtitleSettingsColorsCard.kt#L180) · [`SubtitlesColorPicker` L209](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/panels/SubtitleSettingsColorsCard.kt#L209)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/panels/SubtitleSettingsMiscellaneousCard.kt`

156 satır · SHA-256 `454bec7524a668d0032cbf418a0fad902e5fb00f2da2b23b79c8c870fe730879`

[`SubtitlesMiscellaneousCard` L58](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/panels/SubtitleSettingsMiscellaneousCard.kt#L58)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/panels/SubtitleSettingsPanel.kt`

143 satır · SHA-256 `dda0e45e80ca0cd0a6d0732590a1fe398f400062cec546581010675a7b136911`

[`SubtitleSettingsPanel` L59](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/panels/SubtitleSettingsPanel.kt#L59)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/panels/SubtitleSettingsTypographyCard.kt`

375 satır · SHA-256 `23dedf04beaaf2e4b595c1339d68d0926b0a73feeacb1778179cbd51ecc7c0d2`

[`SubtitleSettingsTypographyCard` L85](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/panels/SubtitleSettingsTypographyCard.kt#L85) · [`resetTypography` L356](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/panels/SubtitleSettingsTypographyCard.kt#L356)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/panels/VideoFiltersPanel.kt`

149 satır · SHA-256 `851666baa8ac8822bcbdef0ad253c69a07463abcb1dbdc32c0456e65c7c1e56c`

[`VideoFiltersPanel` L60](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/panels/VideoFiltersPanel.kt#L60) · [`FiltersCard` L82](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/panels/VideoFiltersPanel.kt#L82)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/AudioTracksSheet.kt`

113 satır · SHA-256 `dbb4f48486a3f70a28ce6387722ba4f01be42635283fea1cb5c54bcd8895239d`

[`AudioTracksSheet` L44](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/AudioTracksSheet.kt#L44) · [`AudioTrackRow` L89](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/AudioTracksSheet.kt#L89)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/ChaptersSheet.kt`

99 satır · SHA-256 `4d1f455e5fc1d1a46b292779d8a69d4af09e6b87219848699835420ca119ee83`

[`ChaptersSheet` L40](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/ChaptersSheet.kt#L40) · [`ChapterTrack` L71](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/ChaptersSheet.kt#L71)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/GenericTracksSheet.kt`

159 satır · SHA-256 `3b3fa9a87507837431af8406faa1f383d8535cacb7579edcd62f1a7330bbd2ab`

[`GenericTracksSheet` L49](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/GenericTracksSheet.kt#L49) · [`AddTrackRow` L74](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/GenericTracksSheet.kt#L74) · [`getTrackTitle` L109](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/GenericTracksSheet.kt#L109) · [`TrackSheetTitle` L132](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/GenericTracksSheet.kt#L132)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/MoreSheet.kt`

354 satır · SHA-256 `517c9e045485e3bb3ee1da5a94239234d0f12390e222fa2889644a8de98be89d`

[`MoreSheet` L85](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/MoreSheet.kt#L85) · [`TimePickerDialog` L268](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/MoreSheet.kt#L268)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/PlaybackSpeedSheet.kt`

181 satır · SHA-256 `4a6e86d25ee13bd9e2818b7927a9d04185917f9228a39f4443ab10d35ea931e6`

[`PlaybackSpeedSheet` L62](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/PlaybackSpeedSheet.kt#L62)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/QualitySheet.kt`

438 satır · SHA-256 `d14be111575c12e162759314530219a7c007caf1e9b53c3d81627781587fa6e8`

[`QualitySheet` L75](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/QualitySheet.kt#L75) · [`QualitySheetVideoContent` L165](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/QualitySheet.kt#L165) · [`QualitySheetHosterContent` L186](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/QualitySheet.kt#L186) · [`HosterTrack` L282](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/QualitySheet.kt#L282) · [`VideoTrack` L348](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/QualitySheet.kt#L348) · [`VideoIcon` L390](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/QualitySheet.kt#L390) · [`VideoText` L419](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/QualitySheet.kt#L419)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/ScreenshotSheet.kt`

128 satır · SHA-256 `5f4592234a6f7daadaab60293700d4557ef66374c8a23864bbc264fa5d195179`

[`ScreenshotSheet` L32](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/ScreenshotSheet.kt#L32)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/SubtitleTracksSheet.kt`

145 satır · SHA-256 `420ef36ab1bf9d3efb1af397e7e1787cb48c661ad5bd32b0e74b66cb8d81426c`

[`SubtitlesSheet` L48](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/SubtitleTracksSheet.kt#L48) · [`SubtitleTrackRow` L114](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/SubtitleTracksSheet.kt#L114)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/loader/EpisodeLoader.kt`

206 satır · SHA-256 `a818e194927a86e7d29a9aebc4deda744a28425a4d105af4c26409acd36b0705`

[`getHosters` L33](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/loader/EpisodeLoader.kt#L33) · [`isDownload` L49](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/loader/EpisodeLoader.kt#L49) · [`checkHasHosters` L60](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/loader/EpisodeLoader.kt#L60) · [`getHostersOnHttp` L86](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/loader/EpisodeLoader.kt#L86) · [`getHostersOnDownloaded` L105](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/loader/EpisodeLoader.kt#L105) · [`getHostersOnLocal` L124](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/loader/EpisodeLoader.kt#L124) · [`getVideos` L153](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/loader/EpisodeLoader.kt#L153) · [`getVideosOnHttp` L174](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/loader/EpisodeLoader.kt#L174) · [`loadHosterVideos` L189](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/loader/EpisodeLoader.kt#L189)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/loader/HosterLoader.kt`

170 satır · SHA-256 `620f24170131cc9ac1df1a18ae7efa0b2e9a250a24d782e82ebdd3ac33070294`

[`selectBestVideo` L27](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/loader/HosterLoader.kt#L27) · [`getBestVideo` L78](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/loader/HosterLoader.kt#L78) · [`getResolvedVideo` L152](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/loader/HosterLoader.kt#L152)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/AdvancedPlayerPreferences.kt`

15 satır · SHA-256 `f219e77fbc5b47ec10bbaae2bc4e5d68dd1b5372080276f5510b7fd0452ff01d`

[`mpvUserFiles` L8](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/AdvancedPlayerPreferences.kt#L8) · [`mpvConf` L9](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/AdvancedPlayerPreferences.kt#L9) · [`mpvInput` L10](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/AdvancedPlayerPreferences.kt#L10) · [`playerStatisticsPage` L14](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/AdvancedPlayerPreferences.kt#L14)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/AudioPreferences.kt`

27 satır · SHA-256 `7849e3469d128c203d24825c53c7c2811c1411f7b766078cbaaa84cbf23a604f`

[`preferredAudioLanguages` L11](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/AudioPreferences.kt#L11) · [`enablePitchCorrection` L12](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/AudioPreferences.kt#L12) · [`audioChannels` L13](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/AudioPreferences.kt#L13) · [`volumeBoostCap` L14](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/AudioPreferences.kt#L14) · [`audioDelay` L18](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/AudioPreferences.kt#L18)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/DecoderPreferences.kt`

22 satır · SHA-256 `d3553e8d9dc8480d6970b7848c34c5ecc76e91d220b4f63d8cb9f47dc509832c`

[`tryHWDecoding` L10](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/DecoderPreferences.kt#L10) · [`gpuNext` L11](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/DecoderPreferences.kt#L11) · [`videoDebanding` L12](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/DecoderPreferences.kt#L12) · [`useYUV420P` L13](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/DecoderPreferences.kt#L13) · [`brightnessFilter` L17](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/DecoderPreferences.kt#L17) · [`saturationFilter` L18](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/DecoderPreferences.kt#L18) · [`contrastFilter` L19](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/DecoderPreferences.kt#L19) · [`gammaFilter` L20](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/DecoderPreferences.kt#L20) · [`hueFilter` L21](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/DecoderPreferences.kt#L21)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/GesturePreferences.kt`

36 satır · SHA-256 `2471fd79e3308cc8d5c62c0af6367d6a30c2c1a9fbb6e5679127b93ef46a7ba6`

[`gestureVolumeBrightness` L11](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/GesturePreferences.kt#L11) · [`swapVolumeBrightness` L15](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/GesturePreferences.kt#L15) · [`gestureHorizontalSeek` L19](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/GesturePreferences.kt#L19) · [`showSeekBar` L20](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/GesturePreferences.kt#L20) · [`defaultIntroLength` L21](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/GesturePreferences.kt#L21) · [`skipLengthPreference` L22](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/GesturePreferences.kt#L22) · [`playerSmoothSeek` L23](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/GesturePreferences.kt#L23) · [`leftDoubleTapGesture` L27](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/GesturePreferences.kt#L27) · [`centerDoubleTapGesture` L28](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/GesturePreferences.kt#L28) · [`rightDoubleTapGesture` L29](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/GesturePreferences.kt#L29) · [`mediaPreviousGesture` L33](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/GesturePreferences.kt#L33) · [`mediaPlayPauseGesture` L34](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/GesturePreferences.kt#L34) · [`mediaNextGesture` L35](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/GesturePreferences.kt#L35)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt`

86 satır · SHA-256 `d31353e45530cd48cefe00964970daf4d038485cdb73ef7c151e4e55a53c1ad6`

[`preserveWatchingPosition` L11](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L11) · [`progressPreference` L15](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L15) · [`defaultPlayerOrientationType` L16](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L16) · [`allowGestures` L23](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L23) · [`showLoadingCircle` L24](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L24) · [`showCurrentChapter` L25](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L25) · [`rememberPlayerBrightness` L26](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L26) · [`playerBrightnessValue` L27](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L27) · [`rememberPlayerVolume` L28](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L28) · [`playerVolumeValue` L29](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L29) · [`showFailedHosters` L33](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L33) · [`showEmptyHosters` L34](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L34) · [`playerFullscreen` L38](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L38) · [`hideControls` L39](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L39) · [`displayVolPer` L40](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L40) · [`showSystemStatusBar` L41](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L41) · [`reduceMotion` L42](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L42) · [`playerTimeToDisappear` L43](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L43) · [`panelOpacity` L44](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L44) · [`enableSkipIntro` L48](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L48) · [`autoSkipIntro` L49](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L49) · [`enableNetflixStyleIntroSkip` L50](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L50) · [`waitingTimeIntroSkip` L54](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L54) · [`aniSkipEnabled` L55](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L55) · [`disableAniSkipOnChapters` L56](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L56) · [`enablePip` L60](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L60) · [`pipEpisodeToasts` L61](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L61) · [`pipOnExit` L62](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L62) · [`pipReplaceWithPrevious` L63](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L63) · [`alwaysUseExternalPlayer` L67](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L67) · [`externalPlayerPreference` L71](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L71) · [`playerSpeed` L75](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L75) · [`speedPresets` L76](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L76) · [`invertDuration` L80](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L80) · [`aspectState` L81](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L81) · [`autoplayEnabled` L85](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt#L85)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt`

64 satır · SHA-256 `64a9772194952646f2b1ec3e0abe8ffa24cfb07968744fcf0ac1859ea2c158e0`

[`preferredSubLanguages` L18](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L18) · [`subtitleWhitelist` L19](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L19) · [`subtitleBlacklist` L20](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L20) · [`screenshotSubtitles` L24](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L24) · [`subtitleFont` L26](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L26) · [`subtitleFontSize` L27](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L27) · [`subtitleFontScale` L28](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L28) · [`subtitleBorderSize` L29](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L29) · [`boldSubtitles` L30](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L30) · [`italicSubtitles` L31](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L31) · [`textColorSubtitles` L33](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L33) · [`borderColorSubtitles` L35](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L35) · [`borderStyleSubtitles` L36](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L36) · [`shadowOffsetSubtitles` L40](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L40) · [`backgroundColorSubtitles` L41](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L41) · [`subtitleJustification` L46](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L46) · [`subtitlePos` L47](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L47) · [`overrideSubsASS` L49](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L49) · [`subtitlesDelay` L51](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L51) · [`subtitlesSpeed` L52](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L52) · [`subtitlesSecondaryDelay` L53](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt#L53)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/utils/AniSkipApi.kt`

117 satır · SHA-256 `9ccb4f2b840433abd8daf4f1b52d9f4ef7c95d4e96d1dc46302360a66f32c2d2`

[`getResult` L22](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/utils/AniSkipApi.kt#L22) · [`getMalIdFromAL` L46](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/utils/AniSkipApi.kt#L46) · [`getString` L94](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/utils/AniSkipApi.kt#L94) · [`toChapterType` L103](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/utils/AniSkipApi.kt#L103)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/utils/ChapterUtils.kt`

105 satır · SHA-256 `fd8d0d36d49fc74176fbf620660f9aa4e910258655f5e89c7ae4f7ba5362095d`

[`mergeChapters` L21](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/utils/ChapterUtils.kt#L21)

### `app/src/main/java/eu/kanade/tachiyomi/ui/player/utils/TrackSelect.kt`

65 satır · SHA-256 `07b1079168e9c9f0dc7a3b9f02be83bf1339f2538d4d4520c3aa173cb16c0a7a`

[`getPreferredTrackIndex` L16](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/utils/TrackSelect.kt#L16) · [`containsLang` L56](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/player/utils/TrackSelect.kt#L56)

## Kitsugi

Dosya SHA-256 değerleri incelenen anı sabitler; sembol taraması davranış doğrulaması değildir.

### `app/src/main/java/com/kitsugi/animelist/core/p2p/P2pModels.kt`

228 satır · SHA-256 `159d0f99903649aeffb821e29efde0df74b25e1dde33028a1809e5bccfb846fc`

[`normalizeInfoHashToHex` L123](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pModels.kt#L123) · [`base32ToHex` L144](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pModels.kt#L144) · [`canonicalP2pInfoHash` L163](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pModels.kt#L163) · [`buildP2pMagnetUri` L168](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pModels.kt#L168) · [`formatP2pSpeed` L200](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pModels.kt#L200) · [`formatP2pMegabytes` L208](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pModels.kt#L208) · [`formatP2pBytes` L211](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pModels.kt#L211)

### `app/src/main/java/com/kitsugi/animelist/core/p2p/P2pSettingsRepository.kt`

155 satır · SHA-256 `43269da61a213872038cfe7c1570ca84421cedc486a526137bb487c7ea2fb2a2`

[`initialize` L34](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pSettingsRepository.kt#L34) · [`ensureLoaded` L41](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pSettingsRepository.kt#L41) · [`isP2pEnabled` L46](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pSettingsRepository.kt#L46) · [`isConsentGranted` L51](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pSettingsRepository.kt#L51) · [`setP2pEnabled` L56](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pSettingsRepository.kt#L56) · [`setEnableUpload` L64](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pSettingsRepository.kt#L64) · [`setUploadLimitKbps` L72](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pSettingsRepository.kt#L72) · [`setHideTorrentStats` L80](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pSettingsRepository.kt#L80) · [`setTorrentProfile` L88](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pSettingsRepository.kt#L88) · [`setCacheSize` L96](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pSettingsRepository.kt#L96) · [`isClearCacheOnCloseEnabled` L104](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pSettingsRepository.kt#L104) · [`setClearCacheOnClose` L109](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pSettingsRepository.kt#L109) · [`setConsentGranted` L117](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pSettingsRepository.kt#L117) · [`loadFromDisk` L125](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pSettingsRepository.kt#L125) · [`publish` L143](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pSettingsRepository.kt#L143)

### `app/src/main/java/com/kitsugi/animelist/core/p2p/P2pStreamingEngine.kt`

679 satır · SHA-256 `32523ee599eb4114a9e9b050b1cda2066bedaab94753cab7a7467f773be1ea46`

[`initialize` L80](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pStreamingEngine.kt#L80) · [`sweepLeftoverCacheOnLaunch` L91](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pStreamingEngine.kt#L91) · [`startStream` L102](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pStreamingEngine.kt#L102) · [`clearCache` L106](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pStreamingEngine.kt#L106) · [`startStreamLocked` L141](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pStreamingEngine.kt#L141) · [`stopStream` L274](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pStreamingEngine.kt#L274) · [`shutdown` L278](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pStreamingEngine.kt#L278) · [`scheduleStop` L282](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pStreamingEngine.kt#L282) · [`stopStreamNow` L291](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pStreamingEngine.kt#L291) · [`detachActiveStream` L297](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pStreamingEngine.kt#L297) · [`detachGenerationIfCurrent` L312](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pStreamingEngine.kt#L312) · [`cleanupDetachedStream` L331](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pStreamingEngine.kt#L331) · [`reclaimCacheAfterStream` L350](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pStreamingEngine.kt#L350) · [`cleanupFailedStart` L363](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pStreamingEngine.kt#L363) · [`stopPreparedStream` L385](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pStreamingEngine.kt#L385) · [`ensureEngine` L395](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pStreamingEngine.kt#L395) · [`closeEngine` L447](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pStreamingEngine.kt#L447) · [`observeEngineEvents` L465](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pStreamingEngine.kt#L465) · [`startStatsPolling` L506](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pStreamingEngine.kt#L506) · [`updateCacheState` L551](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pStreamingEngine.kt#L551) · [`startStartupStatsPolling` L559](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pStreamingEngine.kt#L559) · [`beginStreamGeneration` L580](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pStreamingEngine.kt#L580) · [`updateConnectingIfCurrent` L586](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pStreamingEngine.kt#L586) · [`nextDiagnosticRequestSequence` L606](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pStreamingEngine.kt#L606) · [`attachStreamIfCurrent` L611](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pStreamingEngine.kt#L611) · [`publishStreamingIfCurrent` L622](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pStreamingEngine.kt#L622) · [`updateStreamingIfCurrent` L633](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pStreamingEngine.kt#L633) · [`isCurrentGeneration` L642](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pStreamingEngine.kt#L642) · [`ensureCurrentGeneration` L645](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pStreamingEngine.kt#L645) · [`requireContext` L651](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pStreamingEngine.kt#L651) · [`diagnosticId` L654](../app/src/main/java/com/kitsugi/animelist/core/p2p/P2pStreamingEngine.kt#L654)

### `app/src/main/java/com/kitsugi/animelist/core/player/AudioDelayMediaSource.kt`

86 satır · SHA-256 `9e1879d3111bb0f9b9e9828a969229ecdcab5c532e106d615ab8bd73a10a6dcd`

[`setDelayForRoute` L36](../app/src/main/java/com/kitsugi/animelist/core/player/AudioDelayMediaSource.kt#L36) · [`setActiveRoute` L45](../app/src/main/java/com/kitsugi/animelist/core/player/AudioDelayMediaSource.kt#L45) · [`getCurrentDelay` L55](../app/src/main/java/com/kitsugi/animelist/core/player/AudioDelayMediaSource.kt#L55) · [`getAllDelays` L60](../app/src/main/java/com/kitsugi/animelist/core/player/AudioDelayMediaSource.kt#L60) · [`loadFromString` L66](../app/src/main/java/com/kitsugi/animelist/core/player/AudioDelayMediaSource.kt#L66) · [`saveToString` L84](../app/src/main/java/com/kitsugi/animelist/core/player/AudioDelayMediaSource.kt#L84)

### `app/src/main/java/com/kitsugi/animelist/core/player/AudioDelayProcessor.kt`

137 satır · SHA-256 `f549390d2fddc2dae79eac8aa6c2b90126dfca3ee509b5b333d83944dcdb0f6e`

[`setDelayMs` L44](../app/src/main/java/com/kitsugi/animelist/core/player/AudioDelayProcessor.kt#L44) · [`getDelayMs` L48](../app/src/main/java/com/kitsugi/animelist/core/player/AudioDelayProcessor.kt#L48) · [`onConfigure` L50](../app/src/main/java/com/kitsugi/animelist/core/player/AudioDelayProcessor.kt#L50) · [`onFlush` L64](../app/src/main/java/com/kitsugi/animelist/core/player/AudioDelayProcessor.kt#L64) · [`onReset` L69](../app/src/main/java/com/kitsugi/animelist/core/player/AudioDelayProcessor.kt#L69) · [`queueInput` L74](../app/src/main/java/com/kitsugi/animelist/core/player/AudioDelayProcessor.kt#L74) · [`obtainZeroFill` L131](../app/src/main/java/com/kitsugi/animelist/core/player/AudioDelayProcessor.kt#L131)

### `app/src/main/java/com/kitsugi/animelist/core/player/AudioDelayRouteConfig.kt`

66 satır · SHA-256 `140bce45c1a6214f9b9d84c03c30c7b9f67add46cbb24b15fe122c412c1a970b`

[`fromJson` L38](../app/src/main/java/com/kitsugi/animelist/core/player/AudioDelayRouteConfig.kt#L38) · [`toJson` L49](../app/src/main/java/com/kitsugi/animelist/core/player/AudioDelayRouteConfig.kt#L49) · [`getDelayFor` L59](../app/src/main/java/com/kitsugi/animelist/core/player/AudioDelayRouteConfig.kt#L59)

### `app/src/main/java/com/kitsugi/animelist/core/player/AudioOutputRouteDetector.kt`

148 satır · SHA-256 `09045a4e6fab78532fa83679a6f15f6b6993ee77ddb0139eeed68af8610bcd41`

[`toJsonKey` L23](../app/src/main/java/com/kitsugi/animelist/core/player/AudioOutputRouteDetector.kt#L23) · [`getCurrentRoute` L37](../app/src/main/java/com/kitsugi/animelist/core/player/AudioOutputRouteDetector.kt#L37) · [`observeRouteChanges` L91](../app/src/main/java/com/kitsugi/animelist/core/player/AudioOutputRouteDetector.kt#L91) · [`onAudioDevicesAdded` L97](../app/src/main/java/com/kitsugi/animelist/core/player/AudioOutputRouteDetector.kt#L97) · [`onAudioDevicesRemoved` L101](../app/src/main/java/com/kitsugi/animelist/core/player/AudioOutputRouteDetector.kt#L101) · [`onReceive` L112](../app/src/main/java/com/kitsugi/animelist/core/player/AudioOutputRouteDetector.kt#L112) · [`getDelayForRoute` L133](../app/src/main/java/com/kitsugi/animelist/core/player/AudioOutputRouteDetector.kt#L133)

### `app/src/main/java/com/kitsugi/animelist/core/player/BitrateAwareLoadControl.kt`

68 satır · SHA-256 `ba9376fefc555f10e27eda6475ea475eed2badcc08ffc08e67d145bd42ffb7d6`

[`setBackBufferDurationOverrideMs` L42](../app/src/main/java/com/kitsugi/animelist/core/player/BitrateAwareLoadControl.kt#L42) · [`setBudgetBytesOverride` L50](../app/src/main/java/com/kitsugi/animelist/core/player/BitrateAwareLoadControl.kt#L50) · [`getBackBufferDurationUs` L54](../app/src/main/java/com/kitsugi/animelist/core/player/BitrateAwareLoadControl.kt#L54) · [`calculateTargetBufferBytes` L59](../app/src/main/java/com/kitsugi/animelist/core/player/BitrateAwareLoadControl.kt#L59)

### `app/src/main/java/com/kitsugi/animelist/core/player/DisplayCapabilities.kt`

117 satır · SHA-256 `31edf20dd283abc5cfe8a83af82f6bd021fe714098bb09994fd2f10a266d307d`

[`detect` L48](../app/src/main/java/com/kitsugi/animelist/core/player/DisplayCapabilities.kt#L48) · [`logSummary` L69](../app/src/main/java/com/kitsugi/animelist/core/player/DisplayCapabilities.kt#L69) · [`deriveSupport` L105](../app/src/main/java/com/kitsugi/animelist/core/player/DisplayCapabilities.kt#L105) · [`roundedMilliHz` L116](../app/src/main/java/com/kitsugi/animelist/core/player/DisplayCapabilities.kt#L116)

### `app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionBaseLayerPolicy.kt`

253 satır · SHA-256 `ff49490c9f0333cae850a2b61aed9885f00eb47fe581d90dde3db3ea0686508d`

[`resolveFromCapabilities` L73](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionBaseLayerPolicy.kt#L73) · [`resolve` L144](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionBaseLayerPolicy.kt#L144) · [`queryDvDecoderProfileSupport` L212](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionBaseLayerPolicy.kt#L212)

### `app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionCodecFallback.kt`

174 satır · SHA-256 `4cf91c96cbbedb7702a43455994daf9d0fd3a53d6d3ee1161ce7e05a06bbe481`

[`createDolbyVisionFallbackCodecSelector` L54](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionCodecFallback.kt#L54) · [`findDvDecodersIgnoringProfile` L83](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionCodecFallback.kt#L83) · [`probeCodecInstantiation` L162](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionCodecFallback.kt#L162)

### `app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionExtractorsFactory.kt`

411 satır · SHA-256 `6d6706b077197f0c7f2f383a011a7e3fc32496f7e6200aae341a9b8aedb47a98`

[`createExtractors` L48](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionExtractorsFactory.kt#L48) · [`createExtractors` L51](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionExtractorsFactory.kt#L51) · [`wrap` L57](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionExtractorsFactory.kt#L57) · [`nalFormatFor` L77](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionExtractorsFactory.kt#L77) · [`reset` L105](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionExtractorsFactory.kt#L105) · [`recordSourceProfile` L112](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionExtractorsFactory.kt#L112) · [`recordConversionMode` L116](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionExtractorsFactory.kt#L116) · [`recordCodecStringRewrite` L120](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionExtractorsFactory.kt#L120) · [`getCodecStringRewriteCount` L124](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionExtractorsFactory.kt#L124) · [`getLastSourceProfile` L125](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionExtractorsFactory.kt#L125) · [`getLastSelectedConversionMode` L126](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionExtractorsFactory.kt#L126) · [`shouldConvert` L154](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionExtractorsFactory.kt#L154) · [`conversionMode` L166](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionExtractorsFactory.kt#L166) · [`init` L187](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionExtractorsFactory.kt#L187) · [`sniff` L192](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionExtractorsFactory.kt#L192) · [`read` L195](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionExtractorsFactory.kt#L195) · [`seek` L198](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionExtractorsFactory.kt#L198) · [`release` L200](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionExtractorsFactory.kt#L200) · [`getUnderlyingImplementation` L202](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionExtractorsFactory.kt#L202) · [`track` L215](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionExtractorsFactory.kt#L215) · [`endTracks` L230](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionExtractorsFactory.kt#L230) · [`seekMap` L232](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionExtractorsFactory.kt#L232) · [`ensurePendingCapacity` L255](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionExtractorsFactory.kt#L255) · [`ensureInputScratch` L264](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionExtractorsFactory.kt#L264) · [`durationUs` L272](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionExtractorsFactory.kt#L272) · [`format` L274](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionExtractorsFactory.kt#L274) · [`sampleData` L299](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionExtractorsFactory.kt#L299) · [`sampleData` L324](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionExtractorsFactory.kt#L324) · [`sampleMetadata` L338](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionExtractorsFactory.kt#L338) · [`parseDvProfile` L384](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionExtractorsFactory.kt#L384) · [`rewriteDvCodecString` L391](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionExtractorsFactory.kt#L391) · [`stripDvCodecString` L397](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionExtractorsFactory.kt#L397) · [`parseNalLengthFieldLength` L404](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionExtractorsFactory.kt#L404)

### `app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionMatroskaTransformer.kt`

359 satır · SHA-256 `4ca37f0e2842f611f430848f5d33c2a620135d713551d690b7753e85b2eb6894`

[`onDolbyVisionBlockAdditionalData` L40](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionMatroskaTransformer.kt#L40) · [`shouldTransform` L52](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionMatroskaTransformer.kt#L52) · [`onHevcSample` L61](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionMatroskaTransformer.kt#L61) · [`lastTransformedSampleLength` L69](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionMatroskaTransformer.kt#L69) · [`transformHevcSample` L71](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionMatroskaTransformer.kt#L71) · [`finishScratch` L132](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionMatroskaTransformer.kt#L132) · [`onDolbyVisionCodecString` L137](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionMatroskaTransformer.kt#L137) · [`stripHdr10PlusIfEnabled` L161](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionMatroskaTransformer.kt#L161) · [`convertRpuNal` L177](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionMatroskaTransformer.kt#L177) · [`appendConvertedRpuToScratch` L201](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionMatroskaTransformer.kt#L201) · [`rewriteMp4HevcSampleInto` L215](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionMatroskaTransformer.kt#L215) · [`appendLengthDelimitedNalToScratch` L271](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionMatroskaTransformer.kt#L271) · [`normalizeDolbyVisionCodecString` L288](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionMatroskaTransformer.kt#L288) · [`resolveProfile` L302](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionMatroskaTransformer.kt#L302) · [`resolveProfileFromCodecString` L312](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionMatroskaTransformer.kt#L312) · [`nalUnitTypeAt` L322](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionMatroskaTransformer.kt#L322) · [`nuhLayerIdAt` L325](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionMatroskaTransformer.kt#L325) · [`readLengthField` L332](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionMatroskaTransformer.kt#L332) · [`writeLengthField` L340](../app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionMatroskaTransformer.kt#L340)

### `app/src/main/java/com/kitsugi/animelist/core/player/DoviBridge.kt`

349 satır · SHA-256 `b45c06eb3adba3e7659c35a6105fd19aeeaf1fb59ede911cfffb28143b303a51`

[`isAvailable` L40](../app/src/main/java/com/kitsugi/animelist/core/player/DoviBridge.kt#L40) · [`getBridgeVersionOrNull` L42](../app/src/main/java/com/kitsugi/animelist/core/player/DoviBridge.kt#L42) · [`probeRealtimeConversionSupport` L49](../app/src/main/java/com/kitsugi/animelist/core/player/DoviBridge.kt#L49) · [`runStartupSelfTest` L126](../app/src/main/java/com/kitsugi/animelist/core/player/DoviBridge.kt#L126) · [`resetRuntimeCounters` L180](../app/src/main/java/com/kitsugi/animelist/core/player/DoviBridge.kt#L180) · [`getConversionCallCount` L185](../app/src/main/java/com/kitsugi/animelist/core/player/DoviBridge.kt#L185) · [`getConversionSuccessCount` L187](../app/src/main/java/com/kitsugi/animelist/core/player/DoviBridge.kt#L187) · [`convertDv7RpuToDv81` L189](../app/src/main/java/com/kitsugi/animelist/core/player/DoviBridge.kt#L189) · [`convertDv7RpuToDv81NonAllocating` L216](../app/src/main/java/com/kitsugi/animelist/core/player/DoviBridge.kt#L216) · [`processVideoSampleNonAllocating` L249](../app/src/main/java/com/kitsugi/animelist/core/player/DoviBridge.kt#L249) · [`loadNativeLibrary` L300](../app/src/main/java/com/kitsugi/animelist/core/player/DoviBridge.kt#L300) · [`nativeGetBridgeVersion` L319](../app/src/main/java/com/kitsugi/animelist/core/player/DoviBridge.kt#L319) · [`nativeIsConversionPathReady` L322](../app/src/main/java/com/kitsugi/animelist/core/player/DoviBridge.kt#L322) · [`nativeConvertDv7RpuToDv81` L325](../app/src/main/java/com/kitsugi/animelist/core/player/DoviBridge.kt#L325) · [`nativeConvertDv7RpuToDv81NonAllocating` L328](../app/src/main/java/com/kitsugi/animelist/core/player/DoviBridge.kt#L328) · [`nativeProcessVideoSample` L337](../app/src/main/java/com/kitsugi/animelist/core/player/DoviBridge.kt#L337)

### `app/src/main/java/com/kitsugi/animelist/core/player/ExternalAutoNextPolicy.kt`

39 satır · SHA-256 `24055e73476084039eafe3d3ec34dbced12b7b4756f2363050a4f78366b9c4fb`

[`shouldAutoNext` L20](../app/src/main/java/com/kitsugi/animelist/core/player/ExternalAutoNextPolicy.kt#L20)

### `app/src/main/java/com/kitsugi/animelist/core/player/ExternalPlaybackTracker.kt`

97 satır · SHA-256 `6af6e2f64148481d649713fac65aef1f22c3021d0d467a0b4294b83893e47c1a`

[`prefs` L25](../app/src/main/java/com/kitsugi/animelist/core/player/ExternalPlaybackTracker.kt#L25) · [`recordLaunch` L32](../app/src/main/java/com/kitsugi/animelist/core/player/ExternalPlaybackTracker.kt#L32) · [`recordReturn` L51](../app/src/main/java/com/kitsugi/animelist/core/player/ExternalPlaybackTracker.kt#L51) · [`getLastPosition` L63](../app/src/main/java/com/kitsugi/animelist/core/player/ExternalPlaybackTracker.kt#L63) · [`getLastSession` L81](../app/src/main/java/com/kitsugi/animelist/core/player/ExternalPlaybackTracker.kt#L81) · [`clear` L94](../app/src/main/java/com/kitsugi/animelist/core/player/ExternalPlaybackTracker.kt#L94)

### `app/src/main/java/com/kitsugi/animelist/core/player/ExternalPlayerLauncher.kt`

316 satır · SHA-256 `622be308e1939bb3dfdb76dca978cb6d31a825dc28cabc2603ce44c4bc64ec83`

[`launch` L70](../app/src/main/java/com/kitsugi/animelist/core/player/ExternalPlayerLauncher.kt#L70) · [`createInput` L101](../app/src/main/java/com/kitsugi/animelist/core/player/ExternalPlayerLauncher.kt#L101) · [`buildIntent` L120](../app/src/main/java/com/kitsugi/animelist/core/player/ExternalPlayerLauncher.kt#L120) · [`createIntent` L247](../app/src/main/java/com/kitsugi/animelist/core/player/ExternalPlayerLauncher.kt#L247) · [`parseResult` L260](../app/src/main/java/com/kitsugi/animelist/core/player/ExternalPlayerLauncher.kt#L260) · [`parsePosition` L301](../app/src/main/java/com/kitsugi/animelist/core/player/ExternalPlayerLauncher.kt#L301) · [`parseDuration` L304](../app/src/main/java/com/kitsugi/animelist/core/player/ExternalPlayerLauncher.kt#L304) · [`firstPositiveExtra` L307](../app/src/main/java/com/kitsugi/animelist/core/player/ExternalPlayerLauncher.kt#L307)

### `app/src/main/java/com/kitsugi/animelist/core/player/ExternalPlayerPackages.kt`

77 satır · SHA-256 `8af39e9405fd9ca1657b2198d6c22dfd7bdf19b92aa1f96a9f34840039d3be8a`

[`isInstalled` L18](../app/src/main/java/com/kitsugi/animelist/core/player/ExternalPlayerPackages.kt#L18) · [`getPlayerByPackage` L74](../app/src/main/java/com/kitsugi/animelist/core/player/ExternalPlayerPackages.kt#L74)

### `app/src/main/java/com/kitsugi/animelist/core/player/FrameRateUtils.kt`

654 satır · SHA-256 `05ca84ccf706cc2af5d2e34565582d68baf3533c5756c3ede1be851a922d670c`

[`removeEldestEntry` L50](../app/src/main/java/com/kitsugi/animelist/core/player/FrameRateUtils.kt#L50) · [`sanitizeHeaders` L55](../app/src/main/java/com/kitsugi/animelist/core/player/FrameRateUtils.kt#L55) · [`buildCacheKey` L69](../app/src/main/java/com/kitsugi/animelist/core/player/FrameRateUtils.kt#L69) · [`getCachedFrameRate` L83](../app/src/main/java/com/kitsugi/animelist/core/player/FrameRateUtils.kt#L83) · [`cacheFrameRate` L101](../app/src/main/java/com/kitsugi/animelist/core/player/FrameRateUtils.kt#L101) · [`matchesTargetRefresh` L116](../app/src/main/java/com/kitsugi/animelist/core/player/FrameRateUtils.kt#L116) · [`pickBestForTarget` L121](../app/src/main/java/com/kitsugi/animelist/core/player/FrameRateUtils.kt#L121) · [`refreshWeight` L127](../app/src/main/java/com/kitsugi/animelist/core/player/FrameRateUtils.kt#L127) · [`recordOriginalMode` L142](../app/src/main/java/com/kitsugi/animelist/core/player/FrameRateUtils.kt#L142) · [`refineFrameRateForDisplay` L152](../app/src/main/java/com/kitsugi/animelist/core/player/FrameRateUtils.kt#L152) · [`chooseBestModeForFrameRate` L192](../app/src/main/java/com/kitsugi/animelist/core/player/FrameRateUtils.kt#L192) · [`hasValidVideoSize` L204](../app/src/main/java/com/kitsugi/animelist/core/player/FrameRateUtils.kt#L204) · [`normalizedSize` L208](../app/src/main/java/com/kitsugi/animelist/core/player/FrameRateUtils.kt#L208) · [`resolutionDistanceSquared` L212](../app/src/main/java/com/kitsugi/animelist/core/player/FrameRateUtils.kt#L212) · [`selectModesForVideoResolution` L219](../app/src/main/java/com/kitsugi/animelist/core/player/FrameRateUtils.kt#L219) · [`matchFrameRateAndWait` L230](../app/src/main/java/com/kitsugi/animelist/core/player/FrameRateUtils.kt#L230) · [`cleanupDisplayListener` L337](../app/src/main/java/com/kitsugi/animelist/core/player/FrameRateUtils.kt#L337) · [`clearOriginalDisplayMode` L341](../app/src/main/java/com/kitsugi/animelist/core/player/FrameRateUtils.kt#L341) · [`restoreOriginalDisplayMode` L345](../app/src/main/java/com/kitsugi/animelist/core/player/FrameRateUtils.kt#L345) · [`snapToStandardRate` L369](../app/src/main/java/com/kitsugi/animelist/core/player/FrameRateUtils.kt#L369) · [`snapProbeRateByFrameDuration` L384](../app/src/main/java/com/kitsugi/animelist/core/player/FrameRateUtils.kt#L384) · [`isValidVideoFrameRate` L401](../app/src/main/java/com/kitsugi/animelist/core/player/FrameRateUtils.kt#L401) · [`detectFrameRateFromSource` L405](../app/src/main/java/com/kitsugi/animelist/core/player/FrameRateUtils.kt#L405) · [`detectFrameRateFromNextLib` L414](../app/src/main/java/com/kitsugi/animelist/core/player/FrameRateUtils.kt#L414) · [`detectFrameRateFromExtractor` L422](../app/src/main/java/com/kitsugi/animelist/core/player/FrameRateUtils.kt#L422) · [`detectFrameRateWithNextLib` L436](../app/src/main/java/com/kitsugi/animelist/core/player/FrameRateUtils.kt#L436) · [`detectFrameRateWithExtractor` L484](../app/src/main/java/com/kitsugi/animelist/core/player/FrameRateUtils.kt#L484) · [`shouldUseNextLibProbe` L605](../app/src/main/java/com/kitsugi/animelist/core/player/FrameRateUtils.kt#L605) · [`isLiveStreamUrl` L625](../app/src/main/java/com/kitsugi/animelist/core/player/FrameRateUtils.kt#L625) · [`isMkvSource` L630](../app/src/main/java/com/kitsugi/animelist/core/player/FrameRateUtils.kt#L630) · [`isResolveProxyUrl` L635](../app/src/main/java/com/kitsugi/animelist/core/player/FrameRateUtils.kt#L635) · [`extractEmbeddedResolveUrl` L640](../app/src/main/java/com/kitsugi/animelist/core/player/FrameRateUtils.kt#L640)

### `app/src/main/java/com/kitsugi/animelist/core/player/GainAudioProcessor.kt`

115 satır · SHA-256 `dd4adcdde17f5d5e22928dc801e8d4b814fbe56ccf6c69de8bd5daeefc88f8bd`

[`setGainDb` L14](../app/src/main/java/com/kitsugi/animelist/core/player/GainAudioProcessor.kt#L14) · [`getGainDb` L21](../app/src/main/java/com/kitsugi/animelist/core/player/GainAudioProcessor.kt#L21) · [`onConfigure` L23](../app/src/main/java/com/kitsugi/animelist/core/player/GainAudioProcessor.kt#L23) · [`queueInput` L34](../app/src/main/java/com/kitsugi/animelist/core/player/GainAudioProcessor.kt#L34)

### `app/src/main/java/com/kitsugi/animelist/core/player/HevcDvRpuStripper.kt`

165 satır · SHA-256 `981ff52c02619fbc901f5b1723f977c431286dc93ff216cc0b380eeb9e473764`

[`backingArray` L7](../app/src/main/java/com/kitsugi/animelist/core/player/HevcDvRpuStripper.kt#L7) · [`stripRpuLengthDelimited` L25](../app/src/main/java/com/kitsugi/animelist/core/player/HevcDvRpuStripper.kt#L25) · [`stripRpuLengthDelimited` L72](../app/src/main/java/com/kitsugi/animelist/core/player/HevcDvRpuStripper.kt#L72) · [`stripRpuAnnexB` L87](../app/src/main/java/com/kitsugi/animelist/core/player/HevcDvRpuStripper.kt#L87) · [`stripRpuAnnexB` L139](../app/src/main/java/com/kitsugi/animelist/core/player/HevcDvRpuStripper.kt#L139) · [`findStartCode` L145](../app/src/main/java/com/kitsugi/animelist/core/player/HevcDvRpuStripper.kt#L145) · [`startCodeLength` L157](../app/src/main/java/com/kitsugi/animelist/core/player/HevcDvRpuStripper.kt#L157)

### `app/src/main/java/com/kitsugi/animelist/core/player/HevcHdr10PlusStripper.kt`

269 satır · SHA-256 `bace89980f1a7a64896836aa7bf7f49c02b766c535e6786d5c6c486a934e8ea3`

[`stripHdr10PlusLengthDelimited` L35](../app/src/main/java/com/kitsugi/animelist/core/player/HevcHdr10PlusStripper.kt#L35) · [`stripHdr10PlusAnnexB` L91](../app/src/main/java/com/kitsugi/animelist/core/player/HevcHdr10PlusStripper.kt#L91) · [`filterSeiNal` L137](../app/src/main/java/com/kitsugi/animelist/core/player/HevcHdr10PlusStripper.kt#L137) · [`findStartCode` L243](../app/src/main/java/com/kitsugi/animelist/core/player/HevcHdr10PlusStripper.kt#L243) · [`matchesSignature` L255](../app/src/main/java/com/kitsugi/animelist/core/player/HevcHdr10PlusStripper.kt#L255) · [`startCodeLength` L263](../app/src/main/java/com/kitsugi/animelist/core/player/HevcHdr10PlusStripper.kt#L263)

### `app/src/main/java/com/kitsugi/animelist/core/player/KeepAliveService.kt`

111 satır · SHA-256 `51d8f73b7634190dfc1a5f9114ac499da390162e4023d09adecc91389c129e80`

[`getService` L27](../app/src/main/java/com/kitsugi/animelist/core/player/KeepAliveService.kt#L27) · [`onCreate` L30](../app/src/main/java/com/kitsugi/animelist/core/player/KeepAliveService.kt#L30) · [`onStartCommand` L36](../app/src/main/java/com/kitsugi/animelist/core/player/KeepAliveService.kt#L36) · [`onBind` L50](../app/src/main/java/com/kitsugi/animelist/core/player/KeepAliveService.kt#L50) · [`onDestroy` L54](../app/src/main/java/com/kitsugi/animelist/core/player/KeepAliveService.kt#L54) · [`createNotification` L59](../app/src/main/java/com/kitsugi/animelist/core/player/KeepAliveService.kt#L59) · [`createNotificationChannel` L70](../app/src/main/java/com/kitsugi/animelist/core/player/KeepAliveService.kt#L70) · [`start` L89](../app/src/main/java/com/kitsugi/animelist/core/player/KeepAliveService.kt#L89) · [`stop` L102](../app/src/main/java/com/kitsugi/animelist/core/player/KeepAliveService.kt#L102)

### `app/src/main/java/com/kitsugi/animelist/core/player/KitsugiAssMatroskaExtractor.kt`

327 satır · SHA-256 `f3464ed1cff6490b2976a10ec0985df9154a98714c7adc46d93b86300a34e81a`

[`getElementType` L57](../app/src/main/java/com/kitsugi/animelist/core/player/KitsugiAssMatroskaExtractor.kt#L57) · [`isLevel1Element` L68](../app/src/main/java/com/kitsugi/animelist/core/player/KitsugiAssMatroskaExtractor.kt#L68) · [`startMasterElement` L71](../app/src/main/java/com/kitsugi/animelist/core/player/KitsugiAssMatroskaExtractor.kt#L71) · [`endMasterElement` L91](../app/src/main/java/com/kitsugi/animelist/core/player/KitsugiAssMatroskaExtractor.kt#L91) · [`stringElement` L113](../app/src/main/java/com/kitsugi/animelist/core/player/KitsugiAssMatroskaExtractor.kt#L113) · [`binaryElement` L121](../app/src/main/java/com/kitsugi/animelist/core/player/KitsugiAssMatroskaExtractor.kt#L121) · [`clearAttachment` L138](../app/src/main/java/com/kitsugi/animelist/core/player/KitsugiAssMatroskaExtractor.kt#L138) · [`track` L201](../app/src/main/java/com/kitsugi/animelist/core/player/KitsugiAssMatroskaExtractor.kt#L201) · [`format` L222](../app/src/main/java/com/kitsugi/animelist/core/player/KitsugiAssMatroskaExtractor.kt#L222) · [`sampleMetadata` L231](../app/src/main/java/com/kitsugi/animelist/core/player/KitsugiAssMatroskaExtractor.kt#L231) · [`parseTimecodeUs` L268](../app/src/main/java/com/kitsugi/animelist/core/player/KitsugiAssMatroskaExtractor.kt#L268) · [`findTokenIndex` L278](../app/src/main/java/com/kitsugi/animelist/core/player/KitsugiAssMatroskaExtractor.kt#L278) · [`maybeInflate` L300](../app/src/main/java/com/kitsugi/animelist/core/player/KitsugiAssMatroskaExtractor.kt#L300)

### `app/src/main/java/com/kitsugi/animelist/core/player/LinkGenerator.kt`

26 satır · SHA-256 `a25e8af0fe3be58599300b380e4be9107f3e8e6c5d691acfef2dafd82a43c4b8`

[`getNextEpisodeLink` L20](../app/src/main/java/com/kitsugi/animelist/core/player/LinkGenerator.kt#L20) · [`getPrevEpisodeLink` L25](../app/src/main/java/com/kitsugi/animelist/core/player/LinkGenerator.kt#L25)

### `app/src/main/java/com/kitsugi/animelist/core/player/OfflinePlaybackHelper.kt`

355 satır · SHA-256 `6a7f036c0841d1bcba4df8cb65297705d3212c2f74283158ad3ac4def593d5ec`

[`getDownloadsDir` L83](../app/src/main/java/com/kitsugi/animelist/core/player/OfflinePlaybackHelper.kt#L83) · [`listDownloadedMedia` L103](../app/src/main/java/com/kitsugi/animelist/core/player/OfflinePlaybackHelper.kt#L103) · [`getLocalMedia` L123](../app/src/main/java/com/kitsugi/animelist/core/player/OfflinePlaybackHelper.kt#L123) · [`buildPlayerParams` L140](../app/src/main/java/com/kitsugi/animelist/core/player/OfflinePlaybackHelper.kt#L140) · [`deleteDownload` L163](../app/src/main/java/com/kitsugi/animelist/core/player/OfflinePlaybackHelper.kt#L163) · [`totalDiskUsageBytes` L190](../app/src/main/java/com/kitsugi/animelist/core/player/OfflinePlaybackHelper.kt#L190) · [`isPartialDownload` L199](../app/src/main/java/com/kitsugi/animelist/core/player/OfflinePlaybackHelper.kt#L199) · [`readLocalMedia` L206](../app/src/main/java/com/kitsugi/animelist/core/player/OfflinePlaybackHelper.kt#L206) · [`findVideoFile` L225](../app/src/main/java/com/kitsugi/animelist/core/player/OfflinePlaybackHelper.kt#L225) · [`readSubtitles` L232](../app/src/main/java/com/kitsugi/animelist/core/player/OfflinePlaybackHelper.kt#L232) · [`readFlatSubtitles` L246](../app/src/main/java/com/kitsugi/animelist/core/player/OfflinePlaybackHelper.kt#L246) · [`toLocalSubtitle` L257](../app/src/main/java/com/kitsugi/animelist/core/player/OfflinePlaybackHelper.kt#L257) · [`flatVideoFiles` L270](../app/src/main/java/com/kitsugi/animelist/core/player/OfflinePlaybackHelper.kt#L270) · [`readFlatMedia` L281](../app/src/main/java/com/kitsugi/animelist/core/player/OfflinePlaybackHelper.kt#L281) · [`readFlatMetadata` L295](../app/src/main/java/com/kitsugi/animelist/core/player/OfflinePlaybackHelper.kt#L295) · [`readMetadata` L311](../app/src/main/java/com/kitsugi/animelist/core/player/OfflinePlaybackHelper.kt#L311) · [`extractJsonString` L330](../app/src/main/java/com/kitsugi/animelist/core/player/OfflinePlaybackHelper.kt#L330) · [`extractJsonLong` L336](../app/src/main/java/com/kitsugi/animelist/core/player/OfflinePlaybackHelper.kt#L336)

### `app/src/main/java/com/kitsugi/animelist/core/player/OpenSubtitlesHasher.kt`

93 satır · SHA-256 `6629bee98307b573ef4ec30f69127413e520a166aaa111ecea42fef20c6107f8`

[`compute` L14](../app/src/main/java/com/kitsugi/animelist/core/player/OpenSubtitlesHasher.kt#L14)

### `app/src/main/java/com/kitsugi/animelist/core/player/ParallelRangeDataSource.kt`

364 satır · SHA-256 `833700d6b278f68cc3d54504b4fb035b872c85317d98a750cf8beb557dcf4dd3`

[`acquireBuffer` L60](../app/src/main/java/com/kitsugi/animelist/core/player/ParallelRangeDataSource.kt#L60) · [`releaseBuffer` L70](../app/src/main/java/com/kitsugi/animelist/core/player/ParallelRangeDataSource.kt#L70) · [`createDataSource` L116](../app/src/main/java/com/kitsugi/animelist/core/player/ParallelRangeDataSource.kt#L116) · [`addTransferListener` L128](../app/src/main/java/com/kitsugi/animelist/core/player/ParallelRangeDataSource.kt#L128) · [`open` L134](../app/src/main/java/com/kitsugi/animelist/core/player/ParallelRangeDataSource.kt#L134) · [`openSingleMode` L166](../app/src/main/java/com/kitsugi/animelist/core/player/ParallelRangeDataSource.kt#L166) · [`openParallelMode` L177](../app/src/main/java/com/kitsugi/animelist/core/player/ParallelRangeDataSource.kt#L177) · [`dispatchPrefetchChunks` L236](../app/src/main/java/com/kitsugi/animelist/core/player/ParallelRangeDataSource.kt#L236) · [`read` L281](../app/src/main/java/com/kitsugi/animelist/core/player/ParallelRangeDataSource.kt#L281) · [`readSingle` L291](../app/src/main/java/com/kitsugi/animelist/core/player/ParallelRangeDataSource.kt#L291) · [`readParallel` L301](../app/src/main/java/com/kitsugi/animelist/core/player/ParallelRangeDataSource.kt#L301) · [`readFromUpstream` L328](../app/src/main/java/com/kitsugi/animelist/core/player/ParallelRangeDataSource.kt#L328) · [`getUri` L338](../app/src/main/java/com/kitsugi/animelist/core/player/ParallelRangeDataSource.kt#L338) · [`getResponseHeaders` L342](../app/src/main/java/com/kitsugi/animelist/core/player/ParallelRangeDataSource.kt#L342) · [`close` L348](../app/src/main/java/com/kitsugi/animelist/core/player/ParallelRangeDataSource.kt#L348)

### `app/src/main/java/com/kitsugi/animelist/core/player/PlaybackState.kt`

76 satır · SHA-256 `d9238ac24bde234a4fe1fe7e91d03e385b383acc3eec92327b88ee9d703e0317`

[`onPlaybackStateChanged` L53](../app/src/main/java/com/kitsugi/animelist/core/player/PlaybackState.kt#L53) · [`onPositionChanged` L54](../app/src/main/java/com/kitsugi/animelist/core/player/PlaybackState.kt#L54) · [`onDurationChanged` L55](../app/src/main/java/com/kitsugi/animelist/core/player/PlaybackState.kt#L55) · [`onBufferedPositionChanged` L56](../app/src/main/java/com/kitsugi/animelist/core/player/PlaybackState.kt#L56) · [`onError` L57](../app/src/main/java/com/kitsugi/animelist/core/player/PlaybackState.kt#L57) · [`onEnded` L58](../app/src/main/java/com/kitsugi/animelist/core/player/PlaybackState.kt#L58) · [`onPlayerSwitched` L71](../app/src/main/java/com/kitsugi/animelist/core/player/PlaybackState.kt#L71) · [`onFatalError` L75](../app/src/main/java/com/kitsugi/animelist/core/player/PlaybackState.kt#L75)

### `app/src/main/java/com/kitsugi/animelist/core/player/PlayerAspectMode.kt`

10 satır · SHA-256 `547d28111607adef4fe4aea1fe220a57675e652abfddd9aee5677b29e86de7a6`

Fonksiyon bildirimi yok (model / enum / sabitler olabilir).

### `app/src/main/java/com/kitsugi/animelist/core/player/PlayerAspectScaleUtils.kt`

35 satır · SHA-256 `63bb25ff1b1ca46950c6e2ead440fb20ce7bb86f0822993562109c49ae9b33b6`

[`getMedia3ResizeMode` L6](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerAspectScaleUtils.kt#L6) · [`getMedia3AspectRatioOverride` L17](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerAspectScaleUtils.kt#L17) · [`getMpvAspectProperty` L25](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerAspectScaleUtils.kt#L25)

### `app/src/main/java/com/kitsugi/animelist/core/player/PlayerAutoplaySessionRules.kt`

48 satır · SHA-256 `4447fb5e985a10118f7798b5ece03ee75e5e505d0489679084bb99ed127daf38`

[`onEpisodeCompleted` L23](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerAutoplaySessionRules.kt#L23) · [`resetSession` L33](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerAutoplaySessionRules.kt#L33) · [`watchedCount` L38](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerAutoplaySessionRules.kt#L38) · [`isLimited` L41](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerAutoplaySessionRules.kt#L41) · [`episodesUntilPrompt` L44](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerAutoplaySessionRules.kt#L44)

### `app/src/main/java/com/kitsugi/animelist/core/player/PlayerDisplayModeUtils.kt`

107 satır · SHA-256 `a99712a22f0a7e6d5251af105507e9ae1b287896fa69f9e90c6354a86f50f7d7`

[`applyBestDisplayMode` L29](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerDisplayModeUtils.kt#L29) · [`resetDisplayMode` L67](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerDisplayModeUtils.kt#L67) · [`normalizeContentFps` L83](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerDisplayModeUtils.kt#L83) · [`isRefreshRateCompatible` L100](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerDisplayModeUtils.kt#L100)

### `app/src/main/java/com/kitsugi/animelist/core/player/PlayerFrameRateHeuristics.kt`

62 satır · SHA-256 `9fadb05a94781a7abe90f17b94133ac5ebd166ae76839c2a94af1bffbd7e0f55`

[`isAmbiguousCinema24` L34](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerFrameRateHeuristics.kt#L34) · [`shouldProbeOverrideTrack` L46](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerFrameRateHeuristics.kt#L46)

### `app/src/main/java/com/kitsugi/animelist/core/player/PlayerLibassCompat.kt`

127 satır · SHA-256 `37ca7d4a18766f132349fa0a0a92419ec805ad3ab54cdd19d3fe30392f79a177`

[`supportsFormat` L85](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerLibassCompat.kt#L85) · [`getCueReplacementBehavior` L89](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerLibassCompat.kt#L89) · [`create` L93](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerLibassCompat.kt#L93) · [`normalizeSsaFormat` L97](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerLibassCompat.kt#L97)

### `app/src/main/java/com/kitsugi/animelist/core/player/PlayerLoadingDiagnostics.kt`

152 satır · SHA-256 `dc52bd17a4318a23e8a5dfee3f4f9026c120d70e9e7df42d7d36b9e18ef1a8b7`

[`toLogString` L33](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerLoadingDiagnostics.kt#L33) · [`startSession` L67](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerLoadingDiagnostics.kt#L67) · [`onFirstFrame` L82](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerLoadingDiagnostics.kt#L82) · [`onBufferingStarted` L90](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerLoadingDiagnostics.kt#L90) · [`onBufferingEnded` L96](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerLoadingDiagnostics.kt#L96) · [`onBitrateEstimate` L105](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerLoadingDiagnostics.kt#L105) · [`onPlaybackError` L111](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerLoadingDiagnostics.kt#L111) · [`onPlaybackEnded` L119](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerLoadingDiagnostics.kt#L119) · [`onStateChanged` L124](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerLoadingDiagnostics.kt#L124) · [`dump` L131](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerLoadingDiagnostics.kt#L131)

### `app/src/main/java/com/kitsugi/animelist/core/player/PlayerLogger.kt`

215 satır · SHA-256 `4d7f79184cecc2838a003c7f70ce5a35fdd181b80f5143f84cf60a4dfeee0b5c`

[`formatDate` L37](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerLogger.kt#L37) · [`write` L43](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerLogger.kt#L43) · [`logPlaybackStart` L81](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerLogger.kt#L81) · [`logPlaybackError` L104](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerLogger.kt#L104) · [`logSourceChange` L123](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerLogger.kt#L123) · [`logExternalPlayerLaunch` L138](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerLogger.kt#L138) · [`logInfo` L154](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerLogger.kt#L154) · [`logWarn` L161](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerLogger.kt#L161) · [`getLogFile` L168](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerLogger.kt#L168) · [`getLogFilePath` L171](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerLogger.kt#L171) · [`clearLogs` L174](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerLogger.kt#L174) · [`getOrCreateLogFile` L192](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerLogger.kt#L192) · [`rotatIfNeeded` L201](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerLogger.kt#L201)

### `app/src/main/java/com/kitsugi/animelist/core/player/PlayerMediaSessionHelper.kt`

269 satır · SHA-256 `d3438315f9837f555fda926b0df2241156d9e45a313d77ede6768dfd8ba6532f`

[`onPlay` L60](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerMediaSessionHelper.kt#L60) · [`onPause` L64](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerMediaSessionHelper.kt#L64) · [`onSkipToNext` L68](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerMediaSessionHelper.kt#L68) · [`onSkipToPrevious` L72](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerMediaSessionHelper.kt#L72) · [`onStop` L76](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerMediaSessionHelper.kt#L76) · [`createNotificationChannel` L89](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerMediaSessionHelper.kt#L89) · [`setMetadata` L115](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerMediaSessionHelper.kt#L115) · [`updatePlaybackState` L143](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerMediaSessionHelper.kt#L143) · [`showNotification` L180](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerMediaSessionHelper.kt#L180) · [`cancelNotification` L252](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerMediaSessionHelper.kt#L252) · [`release` L263](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerMediaSessionHelper.kt#L263)

### `app/src/main/java/com/kitsugi/animelist/core/player/PlayerMediaSourceFactory.kt`

413 satır · SHA-256 `2bb53b4b6dafa0b98cf713d4ca76b5bed5b42de148c0edd767c365efc31b59a1`

[`configureSubtitleParsing` L45](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerMediaSourceFactory.kt#L45) · [`buildExtractorsFactory` L58](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerMediaSourceFactory.kt#L58) · [`isHls` L86](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerMediaSourceFactory.kt#L86) · [`buildMediaItem` L92](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerMediaSourceFactory.kt#L92) · [`buildDataSourceFactory` L141](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerMediaSourceFactory.kt#L141) · [`create` L234](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerMediaSourceFactory.kt#L234) · [`guessSubtitleMimeType` L298](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerMediaSourceFactory.kt#L298) · [`guessLocalSubtitleMime` L307](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerMediaSourceFactory.kt#L307) · [`isHlsUrl` L338](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerMediaSourceFactory.kt#L338) · [`originOfUrl` L400](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerMediaSourceFactory.kt#L400)

### `app/src/main/java/com/kitsugi/animelist/core/player/PlayerNextEpisodeRules.kt`

89 satır · SHA-256 `00fe578b229e050e9c6bb2e072bb9dfd1952025df3e5797512d5d1e02cb6087d`

[`evaluate` L28](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerNextEpisodeRules.kt#L28)

### `app/src/main/java/com/kitsugi/animelist/core/player/PlayerPipHelper.kt`

224 satır · SHA-256 `41f78e7f48cb6d54743d7ff705a55d41070773578facf9e0225ae62f82eed6ef`

[`isPipSupported` L44](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerPipHelper.kt#L44) · [`enterPip` L59](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerPipHelper.kt#L59) · [`enterPipSafe` L95](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerPipHelper.kt#L95) · [`updatePipActions` L112](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerPipHelper.kt#L112) · [`updatePipActionsSafe` L137](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerPipHelper.kt#L137) · [`onPipModeChanged` L151](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerPipHelper.kt#L151) · [`buildRational` L158](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerPipHelper.kt#L158) · [`buildRemoteActions` L164](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerPipHelper.kt#L164)

### `app/src/main/java/com/kitsugi/animelist/core/player/PlayerPlaybackAnalytics.kt`

143 satır · SHA-256 `dc5835df7a9d1820afc147cfcd3ddfa9917c0eab4f4cdcc9a7a2fd36f7d53e46`

[`toString` L41](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerPlaybackAnalytics.kt#L41) · [`startSession` L72](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerPlaybackAnalytics.kt#L72) · [`onPlayingChanged` L88](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerPlaybackAnalytics.kt#L88) · [`onPlaybackTick` L96](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerPlaybackAnalytics.kt#L96) · [`onSeek` L107](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerPlaybackAnalytics.kt#L107) · [`onStalledMs` L112](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerPlaybackAnalytics.kt#L112) · [`onQualityChanged` L118](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerPlaybackAnalytics.kt#L118) · [`endSession` L123](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerPlaybackAnalytics.kt#L123)

### `app/src/main/java/com/kitsugi/animelist/core/player/PlayerSubtitleHelper.kt`

64 satır · SHA-256 `1b54702e9ba3184e56bda1211efe3e96f8d1a98a2be6dba331df6440d3fb17a4`

[`getId` L25](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerSubtitleHelper.kt#L25) · [`matchesLanguageCode` L32](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerSubtitleHelper.kt#L32) · [`getIETF_tag` L39](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerSubtitleHelper.kt#L39) · [`getFixedUrl` L61](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerSubtitleHelper.kt#L61)

### `app/src/main/java/com/kitsugi/animelist/core/player/PlayerSubtitleUtils.kt`

370 satır · SHA-256 `f7e0a1e727270131cf6d952cae12fde93b72d895c5346141f308076aae2881cf`

[`matchesLanguageCode` L55](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerSubtitleUtils.kt#L55) · [`sortByPreference` L89](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerSubtitleUtils.kt#L89) · [`sortSubtitlesByPreference` L109](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerSubtitleUtils.kt#L109) · [`autoSelect` L150](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerSubtitleUtils.kt#L150) · [`getFriendlyLanguageName` L166](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerSubtitleUtils.kt#L166) · [`isTurkish` L190](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerSubtitleUtils.kt#L190) · [`matchesTrackLanguage` L210](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerSubtitleUtils.kt#L210) · [`primarySubtag` L232](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerSubtitleUtils.kt#L232) · [`isTurkishLang` L239](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerSubtitleUtils.kt#L239) · [`findBestSubtitleInput` L255](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerSubtitleUtils.kt#L255) · [`findBestMpvSubtitleTrack` L302](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerSubtitleUtils.kt#L302) · [`findBestMpvAudioTrack` L353](../app/src/main/java/com/kitsugi/animelist/core/player/PlayerSubtitleUtils.kt#L353)

### `app/src/main/java/com/kitsugi/animelist/core/player/PostPlayMode.kt`

38 satır · SHA-256 `a7073f5e5e10eafa58e9d8253ed323ce7d70ad7f9e21c37f8da933b7219061e0`

[`fromOrdinal` L32](../app/src/main/java/com/kitsugi/animelist/core/player/PostPlayMode.kt#L32) · [`fromString` L35](../app/src/main/java/com/kitsugi/animelist/core/player/PostPlayMode.kt#L35)

### `app/src/main/java/com/kitsugi/animelist/core/player/QualityProfile.kt`

109 satır · SHA-256 `2b600427b95fec6e9acc6973457ecbb663b0064458661437c716b01e2e3e1eb2`

[`fromString` L27](../app/src/main/java/com/kitsugi/animelist/core/player/QualityProfile.kt#L27) · [`serialize` L48](../app/src/main/java/com/kitsugi/animelist/core/player/QualityProfile.kt#L48) · [`deserialize` L52](../app/src/main/java/com/kitsugi/animelist/core/player/QualityProfile.kt#L52) · [`sortByProfile` L73](../app/src/main/java/com/kitsugi/animelist/core/player/QualityProfile.kt#L73) · [`filterByBitrate` L99](../app/src/main/java/com/kitsugi/animelist/core/player/QualityProfile.kt#L99)

### `app/src/main/java/com/kitsugi/animelist/core/player/StreamAutoPlayPolicy.kt`

34 satır · SHA-256 `8af95a725dac46e678ee56eb64a694750b8033deeaad23420abb4afa271939d6`

[`fromOrdinal` L31](../app/src/main/java/com/kitsugi/animelist/core/player/StreamAutoPlayPolicy.kt#L31)

### `app/src/main/java/com/kitsugi/animelist/core/player/StreamAutoPlaySelector.kt`

65 satır · SHA-256 `6dd07c6732b046788c0e3c599dd92193e255fe28c497f22da9b18c564eca6fca`

[`selectBestStream` L28](../app/src/main/java/com/kitsugi/animelist/core/player/StreamAutoPlaySelector.kt#L28)

### `app/src/main/java/com/kitsugi/animelist/core/player/SubtitleFetchRequest.kt`

20 satır · SHA-256 `1593ab58ed8034644f02b0510aff790e431ec51f98d149285f2c9d9323429c66`

Fonksiyon bildirimi yok (model / enum / sabitler olabilir).

### `app/src/main/java/com/kitsugi/animelist/core/player/SubtitleFileCache.kt`

127 satır · SHA-256 `5debee4bd9075311b218fa4992d69c60bf0d8f40ba7e39de3e10da265f3a18fd`

[`getCacheDir` L15](../app/src/main/java/com/kitsugi/animelist/core/player/SubtitleFileCache.kt#L15) · [`getCachedFile` L23](../app/src/main/java/com/kitsugi/animelist/core/player/SubtitleFileCache.kt#L23) · [`cacheSubtitle` L33](../app/src/main/java/com/kitsugi/animelist/core/player/SubtitleFileCache.kt#L33) · [`guessExtension` L83](../app/src/main/java/com/kitsugi/animelist/core/player/SubtitleFileCache.kt#L83) · [`md5` L101](../app/src/main/java/com/kitsugi/animelist/core/player/SubtitleFileCache.kt#L101) · [`runLruCleanup` L111](../app/src/main/java/com/kitsugi/animelist/core/player/SubtitleFileCache.kt#L111)

### `app/src/main/java/com/kitsugi/animelist/core/player/SubtitleSelectionPolicy.kt`

67 satır · SHA-256 `f0a74ff454cc2a3a8a22c58184264e15c08d60c1c9d0d43668605ad9c968fbba`

[`effectiveLanguages` L33](../app/src/main/java/com/kitsugi/animelist/core/player/SubtitleSelectionPolicy.kt#L33) · [`pick` L43](../app/src/main/java/com/kitsugi/animelist/core/player/SubtitleSelectionPolicy.kt#L43) · [`isTr` L46](../app/src/main/java/com/kitsugi/animelist/core/player/SubtitleSelectionPolicy.kt#L46)

### `app/src/main/java/com/kitsugi/animelist/core/player/TvTrailerPlayerPool.kt`

168 satır · SHA-256 `e56e37054d5787224b793d3a16ffcf72c8f433f6dfd6eab2c527b07c0bdd3416`

[`acquire` L42](../app/src/main/java/com/kitsugi/animelist/core/player/TvTrailerPlayerPool.kt#L42) · [`stop` L52](../app/src/main/java/com/kitsugi/animelist/core/player/TvTrailerPlayerPool.kt#L52) · [`yield` L66](../app/src/main/java/com/kitsugi/animelist/core/player/TvTrailerPlayerPool.kt#L66) · [`reclaim` L82](../app/src/main/java/com/kitsugi/animelist/core/player/TvTrailerPlayerPool.kt#L82) · [`release` L92](../app/src/main/java/com/kitsugi/animelist/core/player/TvTrailerPlayerPool.kt#L92) · [`buildPlayer` L111](../app/src/main/java/com/kitsugi/animelist/core/player/TvTrailerPlayerPool.kt#L111) · [`get` L158](../app/src/main/java/com/kitsugi/animelist/core/player/TvTrailerPlayerPool.kt#L158) · [`release` L164](../app/src/main/java/com/kitsugi/animelist/core/player/TvTrailerPlayerPool.kt#L164)

### `app/src/main/java/com/kitsugi/animelist/core/player/ZidooPlayerMonitor.kt`

51 satır · SHA-256 `feec020d3c4d9ff03ef6be436f4e3a9faf57ed1526b130004619a013506f01ae`

[`isZidooDevice` L19](../app/src/main/java/com/kitsugi/animelist/core/player/ZidooPlayerMonitor.kt#L19) · [`resolveZidooPackage` L36](../app/src/main/java/com/kitsugi/animelist/core/player/ZidooPlayerMonitor.kt#L36)

### `app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt`

908 satır · SHA-256 `faa43a5f8de3c8ca900750e7afc5c15e25001b1ca3e4533a6b740d735d85bd06`

[`ensureInitialized` L54](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L54) · [`setMedia` L64](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L64) · [`surfaceCreated` L106](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L106) · [`setMediaUsingLoadfile` L125](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L125) · [`ensureSurfaceAttachedIfAlreadyAvailable` L143](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L143) · [`applyDefaultTrackSelectionForNewLoad` L157](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L157) · [`setPaused` L170](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L170) · [`isPlayingNow` L175](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L175) · [`isPausedForCacheNow` L180](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L180) · [`isCoreIdleNow` L185](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L185) · [`seekToMs` L190](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L190) · [`currentPositionMs` L196](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L196) · [`durationMs` L204](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L204) · [`hasVideoTrackSelectedNow` L210](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L210) · [`setPlaybackSpeed` L216](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L216) · [`applyAudioAmplificationDb` L221](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L221) · [`applyAudioLanguagePreferences` L233](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L233) · [`applyHardwareDecodeMode` L257](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L257) · [`setSubtitleDelayMs` L267](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L267) · [`applyAspectMode` L276](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L276) · [`onSizeChanged` L283](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L283) · [`applyAspectModeInternal` L293](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L293) · [`scheduleAspectModeRefresh` L312](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L312) · [`applySubtitleStyle` L325](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L325) · [`isAssOrSsaSubtitleSelectedNow` L347](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L347) · [`selectAudioTrackById` L373](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L373) · [`applyAudioSelection` L386](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L386) · [`currentAid` L389](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L389) · [`selectSubtitleTrackById` L428](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L428) · [`disableSubtitles` L440](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L440) · [`addAndSelectExternalSubtitle` L452](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L452) · [`applySubtitleLanguagePreferences` L479](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L479) · [`readTrackSnapshot` L519](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L519) · [`releasePlayer` L598](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L598) · [`initOptions` L610](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L610) · [`postInitOptions` L652](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L652) · [`observeProperties` L656](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L656) · [`applyHeaders` L660](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L660) · [`buildMediaRequestKey` L677](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L677) · [`toMpvColor` L695](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L695) · [`applyCoverAspectScale` L699](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L699) · [`readVideoAspectRatio` L723](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L723) · [`aspectModeNeedsVideoAspect` L783](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L783) · [`readViewAspectRatio` L796](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L796) · [`resolveAspectScale` L804](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L804) · [`mpvTlsOptions` L891](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L891) · [`applyMpvTlsOptions` L903](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L903)

### `app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt`

1353 satır · SHA-256 `4eb79af01d7ce8197a5d249ae883a9d5cc2f782d3b0be7ceb1c02005b89794a4`

[`runOnMainThread` L87](../app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt#L87) · [`onIsPlayingChanged` L225](../app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt#L225) · [`onPlaybackStateChanged` L235](../app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt#L235) · [`onPlayerError` L257](../app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt#L257) · [`buildDetailedPlaybackMessage` L270](../app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt#L270) · [`onVideoSizeChanged` L299](../app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt#L299) · [`onTracksChanged` L313](../app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt#L313) · [`initializePlayer` L552](../app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt#L552) · [`buildAudioSink` L565](../app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt#L565) · [`addListener` L657](../app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt#L657) · [`removeListener` L661](../app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt#L661) · [`prepare` L665](../app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt#L665) · [`play` L912](../app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt#L912) · [`pause` L918](../app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt#L918) · [`seekTo` L924](../app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt#L924) · [`setPlaybackSpeed` L930](../app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt#L930) · [`setVolume` L938](../app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt#L938) · [`setSubtitleDelay` L952](../app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt#L952) · [`setAudioDelay` L958](../app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt#L958) · [`setSubtitleStyle` L967](../app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt#L967) · [`setResizeMode` L972](../app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt#L972) · [`setAspectMode` L977](../app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt#L977) · [`applyAspectMode` L983](../app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt#L983) · [`trackLabelWithSupport` L1008](../app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt#L1008) · [`selectTrack` L1011](../app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt#L1011) · [`applyTrackOverride` L1068](../app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt#L1068) · [`isTrackOverrideApplied` L1082](../app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt#L1082) · [`disableSubtitles` L1095](../app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt#L1095) · [`takeScreenshot` L1113](../app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt#L1113) · [`drawSubtitleOverlays` L1154](../app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt#L1154) · [`createVideoView` L1183](../app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt#L1183) · [`detachSubtitleViewFromContentFrame` L1222](../app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt#L1222) · [`applySubtitleStylesToView` L1245](../app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt#L1245) · [`guessLocalMimeType` L1302](../app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt#L1302) · [`release` L1336](../app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt#L1336)

### `app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlaybackPolicy.kt`

26 satır · SHA-256 `9aa05944b55d5e066c34a93b63f5769b8a1de54a5389df608d203381ec0d7136`

[`volume` L13](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlaybackPolicy.kt#L13) · [`videoFilters` L17](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlaybackPolicy.kt#L17) · [`isReady` L23](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlaybackPolicy.kt#L23)

### `app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt`

908 satır · SHA-256 `ea63f1a30fe3f58ed14402d87c2fb63dbb11be4a8bcf711a9b86ff7df1c53e70`

[`addListener` L190](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L190) · [`removeListener` L191](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L191) · [`prepare` L193](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L193) · [`play` L229](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L229) · [`pause` L236](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L236) · [`seekTo` L242](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L242) · [`setPlaybackSpeed` L260](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L260) · [`setVolume` L265](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L265) · [`setSubtitleDelay` L276](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L276) · [`setSecondarySubtitleDelay` L284](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L284) · [`setSubtitleSpeed` L292](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L292) · [`setVideoFilters` L302](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L302) · [`selectSecondarySubtitleTrack` L314](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L314) · [`setAudioDelay` L325](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L325) · [`setSubtitleStyle` L332](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L332) · [`setResizeMode` L361](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L361) · [`setAspectMode` L371](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L371) · [`selectTrack` L387](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L387) · [`disableSubtitles` L419](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L419) · [`createVideoView` L427](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L427) · [`release` L455](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L455) · [`eventProperty` L466](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L466) · [`eventProperty` L472](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L472) · [`eventProperty` L506](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L506) · [`eventProperty` L534](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L534) · [`eventProperty` L559](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L559) · [`eventProperty` L573](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L573) · [`event` L575](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L575) · [`applyInitOptions` L613](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L613) · [`option` L616](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L616) · [`applySecondarySubtitle` L635](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L635) · [`beginLoad` L651](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L651) · [`applyExternalAudio` L668](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L668) · [`handleEndFile` L682](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L682) · [`updateState` L707](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L707) · [`checkBufferingState` L714](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L714) · [`notifyPositionChanged` L725](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L725) · [`updateTracks` L729](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L729) · [`createDummyGroup` L813](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L813) · [`takeScreenshot` L824](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L824) · [`setupCustomButtons` L852](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L852) · [`executeCommand` L902](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L902)

### `app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngine.kt`

120 satır · SHA-256 `5cb697def66085f99b6a7f96375a051f9deba8bc0498774c4f8a56ff3c495071`

[`onStateChanged` L20](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngine.kt#L20) · [`onPlaybackError` L21](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngine.kt#L21) · [`onPositionChanged` L22](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngine.kt#L22) · [`onTracksChanged` L23](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngine.kt#L23) · [`onVideoSizeChanged` L24](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngine.kt#L24) · [`onEngineEvent` L25](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngine.kt#L25) · [`addListener` L54](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngine.kt#L54) · [`removeListener` L55](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngine.kt#L55) · [`prepare` L62](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngine.kt#L62) · [`play` L74](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngine.kt#L74) · [`pause` L75](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngine.kt#L75) · [`seekTo` L76](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngine.kt#L76) · [`setPlaybackSpeed` L77](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngine.kt#L77) · [`setVolume` L78](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngine.kt#L78) · [`setSubtitleDelay` L79](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngine.kt#L79) · [`setAudioDelay` L80](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngine.kt#L80) · [`setSecondarySubtitleDelay` L83](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngine.kt#L83) · [`setSubtitleSpeed` L86](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngine.kt#L86) · [`setVideoFilters` L92](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngine.kt#L92) · [`setSubtitleStyle` L93](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngine.kt#L93) · [`setResizeMode` L94](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngine.kt#L94) · [`setAspectMode` L95](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngine.kt#L95) · [`selectTrack` L97](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngine.kt#L97) · [`disableSubtitles` L98](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngine.kt#L98) · [`createVideoView` L103](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngine.kt#L103) · [`release` L108](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngine.kt#L108) · [`takeScreenshot` L113](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngine.kt#L113) · [`executeCommand` L118](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngine.kt#L118)

### `app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngineSelector.kt`

17 satır · SHA-256 `38391771baf27731a9c9c320c74245b03241a099845334d3a9b2275c7f30c662`

[`selectEngine` L6](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngineSelector.kt#L6)

### `app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngineType.kt`

7 satır · SHA-256 `3f452b8a0146948209cfa1922449a432e1881a7e7a52ff1c9df78c4d2fd37c20`

Fonksiyon bildirimi yok (model / enum / sabitler olabilir).

### `app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerFactory.kt`

83 satır · SHA-256 `661af3553d1907d4718062ba5b37a35b1c69e4c6385a615c479b711861950896`

[`create` L20](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerFactory.kt#L20) · [`create` L31](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerFactory.kt#L31) · [`create` L41](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerFactory.kt#L41) · [`getFactory` L67](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerFactory.kt#L67) · [`createEngine` L74](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerFactory.kt#L74)

### `app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerFallbackCoordinator.kt`

122 satır · SHA-256 `2a6ff0bf076df1998f98b8b0551dff4c6f97eb15d90f76588e540239d480f4ab`

[`getFallbackEngine` L38](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerFallbackCoordinator.kt#L38) · [`reset` L74](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerFallbackCoordinator.kt#L74) · [`wasTried` L84](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerFallbackCoordinator.kt#L84) · [`markTried` L87](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerFallbackCoordinator.kt#L87) · [`nextEngine` L98](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerFallbackCoordinator.kt#L98) · [`nextInternalEngine` L109](../app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerFallbackCoordinator.kt#L109)

### `app/src/main/java/com/kitsugi/animelist/core/player/live/LiveHelper.kt`

99 satır · SHA-256 `5990043e7aaf936ed1ae5b013a80bdab09d489e47b4028e762f8e9d8c0ece8ac`

[`isLikelyLive` L31](../app/src/main/java/com/kitsugi/animelist/core/player/live/LiveHelper.kt#L31) · [`computeStartPositionMs` L48](../app/src/main/java/com/kitsugi/animelist/core/player/live/LiveHelper.kt#L48) · [`shouldShowGoToLive` L79](../app/src/main/java/com/kitsugi/animelist/core/player/live/LiveHelper.kt#L79) · [`maxSeekPositionMs` L97](../app/src/main/java/com/kitsugi/animelist/core/player/live/LiveHelper.kt#L97)

### `app/src/main/java/com/kitsugi/animelist/core/player/live/LiveManager.kt`

98 satır · SHA-256 `7f4b77396eb2ca1ecbfa02fc43ae5c5637c8c69038eb47ccf890c36065d5b652`

[`start` L56](../app/src/main/java/com/kitsugi/animelist/core/player/live/LiveManager.kt#L56) · [`stop` L82](../app/src/main/java/com/kitsugi/animelist/core/player/live/LiveManager.kt#L82) · [`onGoToLiveClicked` L92](../app/src/main/java/com/kitsugi/animelist/core/player/live/LiveManager.kt#L92)

### `app/src/main/java/com/kitsugi/animelist/core/player/live/LivePreviewTimeBar.kt`

69 satır · SHA-256 `a5a7d662dfdd000eae5ddd32d09ddc66f661b5a9b30a3ffdb0dca12e353f9778`

[`badgeText` L40](../app/src/main/java/com/kitsugi/animelist/core/player/live/LivePreviewTimeBar.kt#L40) · [`progress` L47](../app/src/main/java/com/kitsugi/animelist/core/player/live/LivePreviewTimeBar.kt#L47)

### `app/src/main/java/com/kitsugi/animelist/core/player/model/Subtitle.kt`

20 satır · SHA-256 `661bc35789dc0ac07a58ff0a8d892c150c30f6d16db5c53d298397bb13140d55`

Fonksiyon bildirimi yok (model / enum / sabitler olabilir).

### `app/src/main/java/com/kitsugi/animelist/core/player/subtitle/CustomSrtParser.kt`

102 satır · SHA-256 `4ee16085549742d1cd95f7f615afc4c3b3da119ef1e321b75f2d8db1f72e32e3`

[`parse` L30](../app/src/main/java/com/kitsugi/animelist/core/player/subtitle/CustomSrtParser.kt#L30) · [`parseTimecode` L53](../app/src/main/java/com/kitsugi/animelist/core/player/subtitle/CustomSrtParser.kt#L53) · [`stripHtmlTags` L68](../app/src/main/java/com/kitsugi/animelist/core/player/subtitle/CustomSrtParser.kt#L68) · [`formatTimecode` L74](../app/src/main/java/com/kitsugi/animelist/core/player/subtitle/CustomSrtParser.kt#L74) · [`serialize` L85](../app/src/main/java/com/kitsugi/animelist/core/player/subtitle/CustomSrtParser.kt#L85) · [`applyOffset` L94](../app/src/main/java/com/kitsugi/animelist/core/player/subtitle/CustomSrtParser.kt#L94)

### `app/src/main/java/com/kitsugi/animelist/core/player/subtitle/OutlineSpan.kt`

75 satır · SHA-256 `9617c39d06b78608f291ec1ea81709b2a11b47add2e3333164c5d21c37aaec07`

[`getSize` L21](../app/src/main/java/com/kitsugi/animelist/core/player/subtitle/OutlineSpan.kt#L21) · [`draw` L31](../app/src/main/java/com/kitsugi/animelist/core/player/subtitle/OutlineSpan.kt#L31) · [`solidBlack` L67](../app/src/main/java/com/kitsugi/animelist/core/player/subtitle/OutlineSpan.kt#L67) · [`softDark` L70](../app/src/main/java/com/kitsugi/animelist/core/player/subtitle/OutlineSpan.kt#L70) · [`solidWhite` L73](../app/src/main/java/com/kitsugi/animelist/core/player/subtitle/OutlineSpan.kt#L73)

### `app/src/main/java/com/kitsugi/animelist/core/player/subtitle/RoundedBackgroundColorSpan.kt`

90 satır · SHA-256 `4e70df8708724eac079d7842f3671dfdf1365733f1728d7560d420586e82ee20`

[`getSize` L31](../app/src/main/java/com/kitsugi/animelist/core/player/subtitle/RoundedBackgroundColorSpan.kt#L31) · [`draw` L41](../app/src/main/java/com/kitsugi/animelist/core/player/subtitle/RoundedBackgroundColorSpan.kt#L41) · [`netflixStyle` L69](../app/src/main/java/com/kitsugi/animelist/core/player/subtitle/RoundedBackgroundColorSpan.kt#L69) · [`youtubeStyle` L77](../app/src/main/java/com/kitsugi/animelist/core/player/subtitle/RoundedBackgroundColorSpan.kt#L77) · [`tinted` L85](../app/src/main/java/com/kitsugi/animelist/core/player/subtitle/RoundedBackgroundColorSpan.kt#L85)

### `app/src/main/java/com/kitsugi/animelist/data/settings/AppSettings.kt`

321 satır · SHA-256 `8495eb88680920ca2fd913fc0890f33575c283ddf90a5d96f04e6772b7aa5d30`

Fonksiyon bildirimi yok (model / enum / sabitler olabilir).

### `app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt`

1738 satır · SHA-256 `4188e2c9274ecd830b2056149789b83e938f811dd0f8df3999707173f252c34d`

[`restoreCloudSettings` L45](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L45) · [`setLastExplorePlatform` L334](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L334) · [`setLastSearchSelection` L342](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L342) · [`setLastSearchEngineAndScope` L364](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L364) · [`defaultTranslateTarget` L627](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L627) · [`setCustomImageDownloadUri` L635](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L635) · [`setVideoDownloadUri` L641](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L641) · [`setDownloaderPreference` L647](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L647) · [`setAutoUpdateCheckEnabled` L653](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L653) · [`setSearchHistoryEnabled` L659](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L659) · [`setSelectedThemeId` L665](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L665) · [`setShowAdultContent` L671](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L671) · [`setBlurAdultMedia` L677](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L677) · [`setSelectedListLayoutId` L683](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L683) · [`setProfileInfo` L689](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L689) · [`setAniListProfileInfo` L701](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L701) · [`setMalProfileInfo` L713](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L713) · [`setSimklProfileInfo` L725](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L725) · [`clearAniListProfileInfo` L737](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L737) · [`clearMalProfileInfo` L745](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L745) · [`clearSimklProfileInfo` L753](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L753) · [`setProfileImageUri` L761](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L761) · [`setBannerImageUri` L767](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L767) · [`clearProfileImageUri` L773](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L773) · [`clearBannerImageUri` L780](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L780) · [`setTitleLanguage` L787](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L787) · [`setScoreFormat` L794](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L794) · [`setScoreStep` L801](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L801) · [`setStaffNameLanguage` L808](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L808) · [`setSeparatedListStyle` L815](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L815) · [`setShowLowPriority` L822](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L822) · [`setPriorityColorsJson` L829](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L829) · [`setFuzzySearchEnabled` L836](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L836) · [`setSeparateNovelsManga` L843](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L843) · [`setHideScores` L850](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L850) · [`setShowAnimeLogos` L856](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L856) · [`setDnsChoice` L862](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L862) · [`setUseGithubProxy` L868](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L868) · [`saveSearchHistory` L881](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L881) · [`serializeHistory` L887](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L887) · [`deserializeHistory` L899](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L899) · [`setPlayerPreference` L923](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L923) · [`setAutoplayEnabled` L929](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L929) · [`setSkipIntroDurationSec` L935](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L935) · [`setDefaultSubtitleSize` L941](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L941) · [`setDefaultSubtitleColor` L947](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L947) · [`setSubtitleBold` L953](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L953) · [`setSubtitleOutlineEnabled` L959](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L959) · [`setDefaultAudioBoost` L965](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L965) · [`setDefaultAudioDelayMs` L971](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L971) · [`setBufferSettings` L977](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L977) · [`setMangaReadingMode` L993](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L993) · [`setMangaColorFilter` L999](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L999) · [`setMangaFitMode` L1005](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1005) · [`setMangaBrightness` L1011](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1011) · [`setTmdbUserApiKey` L1019](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1019) · [`setMdbListApiKey` L1027](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1027) · [`setMdbListEnabled` L1033](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1033) · [`setMdbListShowImdb` L1039](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1039) · [`setMdbListShowTomatoes` L1043](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1043) · [`setMdbListShowAudience` L1047](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1047) · [`setMdbListShowMetacritic` L1051](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1051) · [`setMdbListShowLetterboxd` L1055](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1055) · [`setMdbListShowTmdb` L1059](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1059) · [`setMdbListShowTrakt` L1063](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1063) · [`setFanartTvApiKey` L1067](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1067) · [`setFanartTvEnabled` L1071](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1071) · [`setAniSkipEnabled` L1075](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1075) · [`setAniSkipAutoSkip` L1081](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1081) · [`setTmdbEnabled` L1087](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1087) · [`setTmdbModernHomeEnabled` L1091](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1091) · [`setTmdbEnrichContinueWatching` L1095](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1095) · [`setTmdbLanguage` L1099](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1099) · [`setTmdbUseArtwork` L1107](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1107) · [`setTmdbUseBasicInfo` L1111](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1111) · [`setTmdbUseDetails` L1115](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1115) · [`setTmdbUseReleaseDates` L1119](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1119) · [`setTmdbUseCredits` L1123](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1123) · [`setTmdbUseProductions` L1127](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1127) · [`setTmdbUseNetworks` L1131](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1131) · [`setTmdbUseEpisodes` L1135](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1135) · [`setTmdbUseTrailers` L1139](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1139) · [`setTmdbUseMoreLikeThis` L1143](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1143) · [`setTmdbUseCollections` L1147](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1147) · [`setAnimeSkipClientId` L1151](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1151) · [`setAutoTranslateEnabled` L1155](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1155) · [`setPreferredTranslator` L1159](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1159) · [`setTranslateSourceLanguage` L1163](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1163) · [`setTranslateTargetLanguage` L1167](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1167) · [`setSelectedHomeLayoutId` L1171](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1171) · [`setFrameRateMatchingMode` L1175](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1175) · [`setResolutionMatchingEnabled` L1179](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1179) · [`setDecoderPriority` L1183](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1183) · [`setDv7HandlingMode` L1187](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1187) · [`setStripHdr10PlusSei` L1191](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1191) · [`setThemeMode` L1195](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1195) · [`setAmoledBlack` L1201](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1201) · [`setCardFramesEnabled` L1207](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1207) · [`setCustomAccentColor` L1213](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1213) · [`setCustomAccentColor2` L1219](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1219) · [`setCustomAccentGradientAngle` L1225](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1225) · [`setDefaultTab` L1231](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1231) · [`setLastUsedTab` L1243](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1243) · [`setAppLanguage` L1255](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1255) · [`setAccountBackupEnabled` L1261](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1261) · [`setAccountSearchSyncEnabled` L1267](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1267) · [`setAccountNotificationSyncEnabled` L1273](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1273) · [`setFixedNavBar` L1279](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1279) · [`setAspectMode` L1285](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1285) · [`setGainBoostDb` L1291](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1291) · [`setSubtitleDelayMs` L1297](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1297) · [`setPreferredSubtitleLanguages` L1303](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1303) · [`setAddonSubtitleStartupMode` L1309](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1309) · [`setSubtitleDownloadLanguages` L1315](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1315) · [`setGestureVolumeEnabled` L1321](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1321) · [`setGestureBrightnessEnabled` L1325](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1325) · [`setGestureZoomEnabled` L1329](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1329) · [`setGestureScrollSensitivity` L1334](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1334) · [`setDoubleTapSeekSeconds` L1338](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1338) · [`setHoldSpeedMultiplier` L1342](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1342) · [`setPlayerSpeed` L1346](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1346) · [`setPipEnabled` L1350](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1350) · [`setAudioDelayPerRouteJson` L1354](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1354) · [`setQualityProfileJson` L1359](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1359) · [`setAiringNotificationsEnabled` L1364](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1364) · [`setAniListNotificationsEnabled` L1368](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1368) · [`setMalNotificationsEnabled` L1372](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1372) · [`setSimklNotificationsEnabled` L1376](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1376) · [`setKitsuNotificationsEnabled` L1381](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1381) · [`setShikimoriNotificationsEnabled` L1386](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1386) · [`setNotificationInterval` L1390](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1390) · [`setStillWatchingEnabled` L1395](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1395) · [`setStillWatchingThresholdMinutes` L1399](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1399) · [`setPostPlayMode` L1403](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1403) · [`setAutoplaySessionLimit` L1407](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1407) · [`setParallelRangeEnabled` L1412](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1412) · [`setPreviewSeekbarEnabled` L1417](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1417) · [`setPreferredExternalPlayerPackage` L1422](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1422) · [`setShowPlayerTitle` L1427](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1427) · [`setShowPlayerResolution` L1431](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1431) · [`setShowMediaInfo` L1435](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1435) · [`setTitleLimitType` L1439](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1439) · [`setSplashAnimationEnabled` L1443](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1443) · [`setSplashSoundEnabled` L1451](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1451) · [`setLiveHelperEnabled` L1459](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1459) · [`setEnableAssExtractor` L1463](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1463) · [`setSyncEnabledAnilist` L1467](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1467) · [`setSyncEnabledMal` L1471](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1471) · [`setSyncEnabledSimkl` L1475](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1475) · [`setSyncEnabledKitsu` L1479](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1479) · [`setSyncEnabledShikimori` L1483](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1483) · [`setSyncEnabledBangumi` L1487](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1487) · [`saveKitsuProfileInfo` L1491](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1491) · [`clearKitsuProfileInfo` L1498](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1498) · [`saveShikimoriProfileInfo` L1505](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1505) · [`clearShikimoriProfileInfo` L1512](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1512) · [`saveBangumiProfileInfo` L1519](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1519) · [`clearBangumiProfileInfo` L1526](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1526) · [`setMpvGpuRenderer` L1535](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1535) · [`setMpvHwdecMode` L1539](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1539) · [`setMpvDebandMode` L1543](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1543) · [`setMpvForceYuv420p` L1547](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1547) · [`setMpvDemuxerCacheMb` L1551](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1551) · [`setSecondarySubtitleTrackId` L1557](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1557) · [`setSecondarySubtitleDelayMs` L1561](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1561) · [`setSubtitleItalic` L1567](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1567) · [`setSubtitleJustification` L1571](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1571) · [`setSubtitleBackgroundColor` L1575](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1575) · [`setSubtitleShadowOffset` L1579](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1579) · [`setSubtitleBorderColor` L1583](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1583) · [`setSubtitleBorderSize` L1587](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1587) · [`setSubtitleFont` L1591](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1591) · [`setSubtitleBorderStyle` L1595](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1595) · [`setSubtitleFontScale` L1599](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1599) · [`setSubtitlePos` L1603](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1603) · [`setSubtitleOverrideAss` L1607](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1607) · [`setSleepTimerSeconds` L1613](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1613) · [`setVolumeBoostCap` L1619](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1619) · [`setSwipeVolumeBrightnessSides` L1625](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1625) · [`setHorizontalSeekGestureEnabled` L1629](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1629) · [`setPreciseSeeking` L1633](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1633) · [`setAudioChannels` L1637](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1637) · [`setPlayerStatisticsPage` L1641](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1641) · [`setDownloadOnlyOverWifi` L1647](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1647) · [`setDownloadSpeedLimit` L1651](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1651) · [`setSaveChaptersAsCBZ` L1655](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1655) · [`setSplitTallImages` L1659](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1659) · [`setNumberOfDownloads` L1663](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1663) · [`setRemoveAfterMarkedAsRead` L1667](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1667) · [`setRemoveAfterReadSlots` L1671](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1671) · [`setRemoveBookmarkedChapters` L1675](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1675) · [`setDownloadFillermarkedItems` L1679](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1679) · [`setRemoveExcludeCategories` L1683](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1683) · [`setRemoveExcludeAnimeCategories` L1687](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1687) · [`setDownloadNewEpisodes` L1691](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1691) · [`setDownloadNewChapters` L1695](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1695) · [`setDownloadNewEpisodeCategories` L1699](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1699) · [`setDownloadNewChapterCategories` L1703](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1703) · [`setDownloadNewEpisodeCategoriesExclude` L1707](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1707) · [`setDownloadNewChapterCategoriesExclude` L1711](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1711) · [`setAutoDownloadWhileWatching` L1715](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1715) · [`setAutoDownloadWhileReading` L1719](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1719) · [`setUseExternalDownloader` L1723](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1723) · [`setExternalDownloaderSelection` L1727](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1727) · [`setDownloadNewUnreadChaptersOnly` L1731](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1731) · [`setDownloadNewUnseenEpisodesOnly` L1735](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L1735)

### `app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt`

333 satır · SHA-256 `c96739b0f5038782fb5660bf744c9e2d3c3f5133a0095975f890c0e2bd3a1baa`

[`updatePlayerPreference` L19](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L19) · [`updateAutoplayEnabled` L26](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L26) · [`updateSkipIntroDurationSec` L33](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L33) · [`updateDefaultSubtitleSize` L40](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L40) · [`updateDefaultSubtitleColor` L47](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L47) · [`updateSubtitleBold` L54](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L54) · [`updateSubtitleOutline` L61](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L61) · [`updateDefaultAudioBoost` L68](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L68) · [`updateDefaultAudioDelayMs` L75](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L75) · [`updateBufferSettings` L82](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L82) · [`updateDv7HandlingMode` L101](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L101) · [`updateStripHdr10PlusSei` L108](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L108) · [`updatePreferredSubtitleLanguages` L115](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L115) · [`updateAddonSubtitleStartupMode` L122](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L122) · [`updateQualityProfileJson` L129](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L129) · [`updateParallelRangeEnabled` L137](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L137) · [`updateFrameRateMatchingMode` L147](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L147) · [`updateResolutionMatchingEnabled` L154](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L154) · [`updateGestureVolumeEnabled` L164](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L164) · [`updateGestureBrightnessEnabled` L171](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L171) · [`updateGestureZoomEnabled` L178](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L178) · [`updateDoubleTapSeekSeconds` L185](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L185) · [`updateHoldSpeedMultiplier` L192](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L192) · [`updateGestureScrollSensitivity` L200](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L200) · [`updatePreviewSeekbarEnabled` L213](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L213) · [`updatePreferredExternalPlayerPackage` L223](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L223) · [`updateShowPlayerTitle` L231](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L231) · [`updateShowPlayerResolution` L237](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L237) · [`updateShowMediaInfo` L243](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L243) · [`updateTitleLimitType` L249](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L249) · [`updateAspectMode` L256](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L256) · [`updateLiveHelperEnabled` L263](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L263) · [`updateEnableAssExtractor` L270](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L270) · [`updateStillWatchingEnabled` L278](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L278) · [`updateStillWatchingThresholdMinutes` L285](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L285) · [`updatePostPlayMode` L292](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L292) · [`updateAutoplaySessionLimit` L299](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L299) · [`updateGainBoostDb` L307](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L307) · [`updateSubtitleDelayMs` L314](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L314) · [`updateDecoderPriority` L322](../app/src/main/java/com/kitsugi/animelist/ui/app/PlayerSettingsViewModel.kt#L322)

### `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiPlayerSettingsDialog.kt`

2037 satır · SHA-256 `60258f708d8cce34b4d55d9239b0faadafda69251687dff264dde819df695e46`

[`KitsugiPlayerSettingsDialog` L84](../app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiPlayerSettingsDialog.kt#L84)

### `app/src/main/java/com/kitsugi/animelist/ui/components/player/PlayerBufferTab.kt`

259 satır · SHA-256 `9edb8a200d32c9ff0f3eee302010f05577305227c0431ebb075ed89024f84a04`

[`PlayerBufferTab` L34](../app/src/main/java/com/kitsugi/animelist/ui/components/player/PlayerBufferTab.kt#L34)

### `app/src/main/java/com/kitsugi/animelist/ui/components/player/PlayerGeneralTab.kt`

1044 satır · SHA-256 `48f62991db4b85e731b70557dc5575ffde3bd49d34f853b16b6f48a8ae208c2b`

[`PlayerGeneralTab` L41](../app/src/main/java/com/kitsugi/animelist/ui/components/player/PlayerGeneralTab.kt#L41)

### `app/src/main/java/com/kitsugi/animelist/ui/components/player/PlayerSubtitleAudioTab.kt`

515 satır · SHA-256 `c603485b1a57e3d09c49236ee652fe533c2dae9fda31446db0d55d3f8685d62c`

[`PlayerSubtitleAudioTab` L40](../app/src/main/java/com/kitsugi/animelist/ui/components/player/PlayerSubtitleAudioTab.kt#L40)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/AddonFullScreenGridPage.kt`

711 satır · SHA-256 `a4bb049c46a31f8783d696f49625a821ed1ac2a898f6ab31154095612ed39a62`

[`AddonFullScreenGridPage` L110](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/AddonFullScreenGridPage.kt#L110) · [`resolveApi` L185](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/AddonFullScreenGridPage.kt#L185) · [`loadNextPage` L187](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/AddonFullScreenGridPage.kt#L187)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/FullScreenMediaGridPage.kt`

1452 satır · SHA-256 `7136b07b4a32daf4c7d652092d08a3903def0a2fcff09ebea8d17c4d85608cae`

[`FullScreenMediaGridPage` L117](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/FullScreenMediaGridPage.kt#L117) · [`fetchSeasonalPage` L289](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/FullScreenMediaGridPage.kt#L289) · [`bangumiPage` L324](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/FullScreenMediaGridPage.kt#L324) · [`fetchSimklPage` L349](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/FullScreenMediaGridPage.kt#L349) · [`baseExplorePage` L390](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/FullScreenMediaGridPage.kt#L390) · [`fetchExplorePage` L489](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/FullScreenMediaGridPage.kt#L489) · [`loadNextPage` L509](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/FullScreenMediaGridPage.kt#L509) · [`applySeasonalFilter` L568](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/FullScreenMediaGridPage.kt#L568) · [`applyExploreFilter` L668](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/FullScreenMediaGridPage.kt#L668) · [`scrollToTopOfGrid` L677](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/FullScreenMediaGridPage.kt#L677) · [`reloadForActiveFilter` L688](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/FullScreenMediaGridPage.kt#L688) · [`GridGenreChipRow` L1413](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/FullScreenMediaGridPage.kt#L1413)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerActivity.kt`

633 satır · SHA-256 `6ec3ebcf82ed09a203911f43eff6ec167ac7b4b6009a1379e7713e2f4d9c6553`

[`onPipPlay` L65](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerActivity.kt#L65) · [`onPipPause` L66](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerActivity.kt#L66) · [`onPipSkipNext` L67](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerActivity.kt#L67) · [`setPipPlayerCallback` L70](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerActivity.kt#L70) · [`onReceive` L75](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerActivity.kt#L75) · [`invokePipAction` L92](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerActivity.kt#L92) · [`onPipPlay` L100](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerActivity.kt#L100) · [`onPipPause` L101](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerActivity.kt#L101) · [`onPipSkipNext` L104](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerActivity.kt#L104) · [`stopPlaybackEverywhere` L111](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerActivity.kt#L111) · [`applyImmersiveMode` L124](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerActivity.kt#L124) · [`onWindowFocusChanged` L133](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerActivity.kt#L133) · [`onResume` L138](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerActivity.kt#L138) · [`startWithYouTubeId` L181](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerActivity.kt#L181) · [`startWithStreamUrls` L191](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerActivity.kt#L191) · [`launchExternalPlayer` L278](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerActivity.kt#L278) · [`onCreate` L310](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerActivity.kt#L310) · [`handleOnBackPressed` L449](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerActivity.kt#L449) · [`onNewIntent` L537](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerActivity.kt#L537) · [`onUserLeaveHint` L542](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerActivity.kt#L542) · [`onPictureInPictureModeChanged` L554](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerActivity.kt#L554) · [`onStop` L574](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerActivity.kt#L574) · [`updateMediaSession` L595](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerActivity.kt#L595) · [`onDestroy` L613](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerActivity.kt#L613)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt`

1579 satır · SHA-256 `5f22f5a9ef5de37f41b81e078ef903d3261cbc8c16523136eb749eadb26ad3c2`

[`KitsugiFullscreenPlayerScreen` L110](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt#L110) · [`playEpisode` L403](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt#L403) · [`playNextEpisode` L419](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt#L419) · [`switchToMpvForFeature` L576](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt#L576) · [`onPipPlay` L592](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt#L592) · [`onPipPause` L593](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt#L593) · [`onPipSkipNext` L594](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt#L594) · [`onStateChanged` L757](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt#L757) · [`onPlaybackError` L779](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt#L779) · [`onTracksChanged` L795](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt#L795) · [`onEngineEvent` L806](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt#L806) · [`PlaybackEndedOverlay` L1458](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt#L1458) · [`P2pTorrentStatsOverlayHost` L1530](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt#L1530)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt`

2417 satır · SHA-256 `13a6ec2bc81a3f08df49888e3fa028f659c11b79a4426533dba18b6879967ba9`

[`cancelBingeCard` L174](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L174) · [`onEngineSwitched` L216](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L216) · [`switchEngineForFeature` L228](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L228) · [`takePendingResumeAfterEngineSwitch` L235](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L235) · [`showControls` L248](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L248) · [`hideControls` L253](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L253) · [`toggleControls` L257](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L257) · [`startSleepTimer` L283](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L283) · [`stopSleepTimer` L296](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L296) · [`showSheet` L309](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L309) · [`dismissSheet` L321](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L321) · [`resetDismissSheet` L325](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L325) · [`showPanel` L332](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L332) · [`showDialog` L346](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L346) · [`showSeekBar` L360](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L360) · [`hideSeekBar` L365](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L365) · [`lockControls` L372](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L372) · [`unlockControls` L376](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L376) · [`updateReadAhead` L383](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L383) · [`updateDecoder` L390](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L390) · [`updateStatisticsPage` L401](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L401) · [`updateAudioChannels` L417](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L417) · [`startTimer` L439](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L439) · [`updateChapters` L457](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L457) · [`updateChapter` L464](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L464) · [`updateSeekAmount` L483](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L483) · [`updateSeekText` L487](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L487) · [`handleLeftDoubleTap` L491](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L491) · [`handleRightDoubleTap` L499](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L499) · [`handleCenterDoubleTap` L507](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L507) · [`seekBy` L511](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L511) · [`changeVolumeTo` L519](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L519) · [`changeMPVVolumeTo` L529](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L529) · [`changeBrightnessTo` L534](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L534) · [`getSystemBrightness` L542](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L542) · [`displayVolumeSlider` L562](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L562) · [`displayBrightnessSlider` L566](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L566) · [`updatePlayerState` L579](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L579) · [`setPlayerError` L590](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L590) · [`enrichPlayerError` L604](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L604) · [`initialize` L667](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L667) · [`loadPlaybackSource` L747](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L747) · [`runAfrPreflightIfEnabled` L793](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L793) · [`loadEpisodes` L927](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L927) · [`fetchStreamsForEpisode` L949](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L949) · [`playEpisode` L1047](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1047) · [`playNextEpisode` L1130](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1130) · [`takePendingSourceSwitchResume` L1141](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1141) · [`changeStreamSource` L1147](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1147) · [`retryCurrentSource` L1179](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1179) · [`tryNextSource` L1249](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1249) · [`resetAutoSwitch` L1338](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1338) · [`startScrobbleSessionIfNeeded` L1360](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1360) · [`setActiveEngine` L1386](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1386) · [`startRouteObserver` L1486](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1486) · [`stopRouteObserver` L1490](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1490) · [`onPositionChanged` L1494](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1494) · [`play` L1502](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1502) · [`pause` L1507](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1507) · [`togglePlay` L1512](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1512) · [`seekTo` L1516](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1516) · [`setPlaybackSpeed` L1521](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1521) · [`setAutoPlay` L1527](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1527) · [`setAspectMode` L1531](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1531) · [`onEpisodeEnded` L1547](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1547) · [`onCleared` L1585](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1585) · [`updateSkipSettings` L1590](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1590) · [`loadSkipIntervals` L1594](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1594) · [`fetchAutoSubtitles` L1598](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1598) · [`onPlaybackTick` L1803](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1803) · [`onStillWatchingConfirmed` L1811](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1811) · [`onStillWatchingDismissed` L1821](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1821) · [`onEpisodeCompleted` L1830](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1830) · [`onUserInteraction` L1838](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1838) · [`applyStillWatchingSettings` L1845](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1845) · [`getResolveMediaId` L1849](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1849) · [`getSavedPosition` L1853](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1853) · [`saveProgress` L1867](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1867) · [`toggleScreenshotShowSubtitles` L1895](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1895) · [`takeScreenshot` L1905](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1905) · [`saveImage` L1909](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1909) · [`shareImage` L1940](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1940) · [`setAsArt` L1974](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L1974) · [`formatMs` L2033](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L2033) · [`setPrimaryCustomButtonTitle` L2045](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L2045) · [`executeCustomButton` L2050](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L2050) · [`executeCustomButtonLongPress` L2054](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L2054) · [`seekToWithText` L2058](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L2058) · [`seekByWithText` L2067](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L2067) · [`changeEpisode` L2078](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L2078) · [`handleLuaInvocation` L2084](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L2084) · [`showButton` L2173](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L2173) · [`getAnimeSkipIntroLength` L2214](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L2214) · [`setAnimeSkipIntroLength` L2219](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L2219) · [`addAudio` L2231](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L2231) · [`addSubtitle` L2244](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L2244) · [`downloadSubtitleToStorage` L2285](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L2285)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/PlayerAccentTheme.kt`

82 satır · SHA-256 `6b70868dc7c48e1814b983d0c7f207a1727d725fc5eb4b5b04e1ec76b4ceb947`

[`PlayerAccentTheme` L23](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/PlayerAccentTheme.kt#L23) · [`playerOnAccentColor` L67](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/PlayerAccentTheme.kt#L67) · [`playerSurfaceColor` L74](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/PlayerAccentTheme.kt#L74) · [`playerSurfaceContainerColor` L81](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/PlayerAccentTheme.kt#L81)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/PlayerEnums.kt`

128 satır · SHA-256 `5e7514422b261a0658595b3ade729fa542a966b64a272b7e348ac3c3a22b5aac`

[`getDecoderFromValue` L33](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/PlayerEnums.kt#L33)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/OvalBox.kt`

56 satır · SHA-256 `e7379224c471fe943e877d531cd04240b5d3e9539f9481034a9033050c9419bf`

[`createOutline` L11](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/OvalBox.kt#L11) · [`createOutline` L35](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/OvalBox.kt#L35)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PauseOverlay.kt`

296 satır · SHA-256 `8930530a6f75cbd8ac8579f023e31c6f2c69e67519d9e2bcbea547f0f95b3057`

[`PauseOverlay` L41](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PauseOverlay.kt#L41) · [`CastChip` L248](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PauseOverlay.kt#L248) · [`PauseOverlayClock` L281](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PauseOverlay.kt#L281)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PlaybackEndedOverlay.kt`

101 satır · SHA-256 `7abef8321434fa3abc43e81c493a6256063c613577b1a30e6b52c0cefd4abbc0`

[`PlaybackEndedOverlay` L34](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PlaybackEndedOverlay.kt#L34)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PlayerLoadingViews.kt`

224 satır · SHA-256 `c9c21569d2727a43661e8f4d75e644b9f830526d3482b05680467474d1a97ba8`

[`PlayerLoadingView` L38](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PlayerLoadingViews.kt#L38) · [`PlayerBufferingView` L57](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PlayerLoadingViews.kt#L57) · [`PlayerInlineLoadingOverlay` L79](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PlayerLoadingViews.kt#L79) · [`PlayerErrorView` L101](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PlayerLoadingViews.kt#L101)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PlayerModels.kt`

103 satır · SHA-256 `ec839c6404152ad20185c5e607ea7618971ebb9f5c2f702cf052495f9f375044`

[`copyWithNextEpisode` L50](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PlayerModels.kt#L50) · [`blocksNaturalCompletion` L58](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PlayerModels.kt#L58)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PlayerUtils.kt`

88 satır · SHA-256 `f7393fa97e879d9c60c2a75d76731ded45b6041f7d3da36198ede525fa93bb27`

[`FeedbackBubble` L36](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PlayerUtils.kt#L36) · [`formatMs` L81](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PlayerUtils.kt#L81)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PostPlayBingeCard.kt`

169 satır · SHA-256 `96870d43a424d019a324ef890d87d2c1e57cd0601e49ec54de9a2fdf275b2a6c`

[`PostPlayBingeCard` L30](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PostPlayBingeCard.kt#L30)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PreviewGenerator.kt`

216 satır · SHA-256 `fef8f2afc91c90882a9087eee558156951dfa80df174fba5e811d323c6cb312b`

[`start` L51](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PreviewGenerator.kt#L51) · [`getPreviewImage` L152](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PreviewGenerator.kt#L152) · [`cleanMemoryCache` L182](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PreviewGenerator.kt#L182) · [`release` L195](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PreviewGenerator.kt#L195) · [`md5` L206](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PreviewGenerator.kt#L206)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/QualityProfileDialog.kt`

342 satır · SHA-256 `d6bf4a70e42b967950d17a477ca4c079fab2f3c21cce3de10b023bbeafea8611`

[`QualityProfileDialog` L63](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/QualityProfileDialog.kt#L63) · [`QualityOptionRow` L268](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/QualityProfileDialog.kt#L268) · [`formatBitrate` L335](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/QualityProfileDialog.kt#L335)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/SkipIntroButton.kt`

100 satır · SHA-256 `b07afef9c1e9b0f117a2102a70c8ac5cb63d6497a66bbc5eb23e673b45f3b519`

[`SkipIntroButton` L27](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/SkipIntroButton.kt#L27)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/TorrentOverlay.kt`

159 satır · SHA-256 `31e984e3c477b8071f4d0a21fdcea97ceca949d1ea9920d79ec661590fd0c9e4`

[`TorrentOverlay` L36](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/TorrentOverlay.kt#L36)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/BottomLeftPlayerControls.kt`

61 satır · SHA-256 `0f85df339996b1b60e8bc3c0b4631d0131872db7e6cad15a9ceecad8507142b1`

[`BottomLeftPlayerControls` L20](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/BottomLeftPlayerControls.kt#L20)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/BottomRightPlayerControls.kt`

75 satır · SHA-256 `45d36a23aaae02c0a67d7d8cffae9929ff9ef730f53aa05f5cd7df8c90e8930b`

[`BottomRightPlayerControls` L18](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/BottomRightPlayerControls.kt#L18)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/GestureHandler.kt`

397 satır · SHA-256 `a994e7c960a99115b5514afa49fda2388c4c4a75a298a9f16a6bac0c5932ad53`

[`GestureHandler` L72](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/GestureHandler.kt#L72) · [`DoubleTapToSeekOvals` L341](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/GestureHandler.kt#L341) · [`calculateNewVerticalGestureValue` L387](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/GestureHandler.kt#L387) · [`calculateNewVerticalGestureValue` L390](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/GestureHandler.kt#L390) · [`calculateNewHorizontalGestureValue` L393](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/GestureHandler.kt#L393) · [`calculateNewHorizontalGestureValue` L396](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/GestureHandler.kt#L396)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/LocalPlayerButtonsClickEvent.kt`

11 satır · SHA-256 `6455e5fcb7ee791f2c02c030fd1777a795b9562e4c72afe728724821132dc6dc`

Fonksiyon bildirimi yok (model / enum / sabitler olabilir).

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/MiddlePlayerControls.kt`

192 satır · SHA-256 `580fb6ad0934076c7b9c954ec1485658135bc361f6741efb506957d4d8dbbfd0`

[`MiddlePlayerControls` L53](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/MiddlePlayerControls.kt#L53) · [`prettyTime` L182](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/MiddlePlayerControls.kt#L182)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/PlayerControls.kt`

615 satır · SHA-256 `a73298a53b09e226515154ab51e99fa1b5f8dc8d3ccc95fb8f2b5d0ae6f6484f`

[`PlayerControls` L84](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/PlayerControls.kt#L84) · [`resetTimer` L210](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/PlayerControls.kt#L210)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/PlayerDialogsHost.kt`

262 satır · SHA-256 `c5d324b76f5e03a0fdd082b54766f9384a8958996a532e39b46182d47dfc735a`

[`PlayerDialogsHost` L55](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/PlayerDialogsHost.kt#L55) · [`EpisodeListDialog` L84](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/PlayerDialogsHost.kt#L84) · [`IntegerPickerDialog` L209](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/PlayerDialogsHost.kt#L209)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/PlayerPanelsHost.kt`

94 satır · SHA-256 `9ed077df9dddad82fb9a6c23a63eb81a191a16d17890c7186d48a7f2418b8ea9`

[`PlayerPanelsHost` L23](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/PlayerPanelsHost.kt#L23)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/PlayerSheetsHost.kt`

244 satır · SHA-256 `6936614250d680fe62f4147a6fddf665049062480b45d0020a877aff5287eda8`

[`PlayerSheetsHost` L44](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/PlayerSheetsHost.kt#L44)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/TopLeftPlayerControls.kt`

61 satır · SHA-256 `d2171088e0a3f418d64a3c0622dca824eb4e7655fac3445bc4fd3efac7e8617d`

[`TopLeftPlayerControls` L22](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/TopLeftPlayerControls.kt#L22)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/TopRightPlayerControls.kt`

72 satır · SHA-256 `e731ecf790a1effcf83180dad14fc17ddcb94f2095c7fdb3973911352c562516`

[`TopRightPlayerControls` L20](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/TopRightPlayerControls.kt#L20)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/AutoPlaySwitch.kt`

144 satır · SHA-256 `d45c0efff360b19f9195e8ccf9cb24dd1b81ac3c567bcdba8e2b75e08f3c6fd4`

[`AutoPlaySwitch` L37](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/AutoPlaySwitch.kt#L37)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/BrightnessOverlay.kt`

27 satır · SHA-256 `c5a4462716b6c7ded470e104d898fa467e33d02705de3821392cc4b6f8091361`

[`BrightnessOverlay` L16](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/BrightnessOverlay.kt#L16)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/ControlsButton.kt`

138 satır · SHA-256 `53fd6d1b4ba4391a5b8e8b29e9d09a5703b021dd30d85c191d3c8367ee8c9562`

[`ControlsButton` L34](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/ControlsButton.kt#L34) · [`ControlsButton` L75](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/ControlsButton.kt#L75) · [`FilledControlsButton` L109](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/ControlsButton.kt#L109)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/CurrentChapter.kt`

115 satır · SHA-256 `d82644546f38db83195aa0ec23dcc9368636fd73548029c2b8cc6aaa1912d06d`

[`CurrentChapter` L34](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/CurrentChapter.kt#L34) · [`prettyTime` L105](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/CurrentChapter.kt#L105)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/DoubleTapSeekTriangles.kt`

69 satır · SHA-256 `3aa714a74012326c26a3e0f393960e57769ce8deed3f6077bbc50498e30aa20b`

[`DoubleTapSeekTriangles` L20](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/DoubleTapSeekTriangles.kt#L20) · [`DoubleTapArrow` L50](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/DoubleTapSeekTriangles.kt#L50)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/OutlinedNumericChooser.kt`

182 satır · SHA-256 `868915b5534c9254899aab57ef384eab5de508defa260a08afdaeb44c7df4581`

[`OutlinedNumericChooser` L31](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/OutlinedNumericChooser.kt#L31) · [`OutlinedNumericChooser` L107](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/OutlinedNumericChooser.kt#L107)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/PlayerUpdates.kt`

44 satır · SHA-256 `f4378f91febce521220a8e7cba2453bf71a0fec4448f469940abba40ac4871d6`

[`PlayerUpdate` L18](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/PlayerUpdates.kt#L18) · [`TextPlayerUpdate` L33](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/PlayerUpdates.kt#L33)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/RepeatingIconButton.kt`

57 satır · SHA-256 `7babcc9897210cf1f1da036fea9cf2d2cbe5c9a5378995427f9969dda11c1632`

[`RepeatingIconButton` L20](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/RepeatingIconButton.kt#L20)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/SeekBar.kt`

146 satır · SHA-256 `7f074f4aa7edb5ed8bd7c63050f4ebda18b2bcc958f43e95c4563a42701e0cf3`

[`toSegment` L45](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/SeekBar.kt#L45) · [`SeekbarWithTimers` L49](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/SeekBar.kt#L49) · [`VideoTimer` L112](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/SeekBar.kt#L112) · [`prettyTime` L135](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/SeekBar.kt#L135)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/VerticalSliders.kt`

235 satır · SHA-256 `1119b2bc15894e4d2b04778f1d40017ade7a70f1e18484d6ed146bffc70c2751`

[`percentage` L45](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/VerticalSliders.kt#L45) · [`percentage` L48](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/VerticalSliders.kt#L48) · [`VerticalSlider` L52](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/VerticalSliders.kt#L52) · [`VerticalSlider` L67](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/VerticalSliders.kt#L67) · [`VerticalSliderInternal` L82](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/VerticalSliders.kt#L82) · [`BrightnessSlider` L121](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/VerticalSliders.kt#L121) · [`VolumeSlider` L158](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/VerticalSliders.kt#L158)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/panels/AudioDelayPanel.kt`

100 satır · SHA-256 `d9007a0997b94691b56dbd38d7dc285a4019ecf1929c920239a566a382c50682`

[`AudioDelayPanel` L34](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/panels/AudioDelayPanel.kt#L34) · [`AudioDelayCardTitle` L82](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/panels/AudioDelayPanel.kt#L82)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/panels/DelayCard.kt`

181 satır · SHA-256 `1dbc178b34d93c2b221f3f1beda5695dda8cf7a2b05a3b50a4a45f68c425c149`

[`panelCardsColors` L50](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/panels/DelayCard.kt#L50) · [`DelayCard` L57](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/panels/DelayCard.kt#L57)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/panels/SubtitleDelayPanel.kt`

202 satır · SHA-256 `fd8b298aa48389f29934edd6db2454241485fe80d1a89ca9bb92cbaec4c3d24c`

[`SubtitleDelayPanel` L46](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/panels/SubtitleDelayPanel.kt#L46) · [`SubtitleDelayTitle` L163](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/panels/SubtitleDelayPanel.kt#L163)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/panels/SubtitleSettingsPanel.kt`

184 satır · SHA-256 `174f2896de33647da288161253ff257a900855280365e4ab4891b3ea70980de9`

[`SubtitleSettingsPanel` L52](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/panels/SubtitleSettingsPanel.kt#L52) · [`SubtitleTypographyCard` L109](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/panels/SubtitleSettingsPanel.kt#L109) · [`SubtitleMiscCard` L154](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/panels/SubtitleSettingsPanel.kt#L154)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/panels/VideoFiltersPanel.kt`

114 satır · SHA-256 `2e91e47884995281c69fa947e329af029e91305562c221c0d89ca4eda6ad1eb1`

[`VideoFiltersPanel` L35](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/panels/VideoFiltersPanel.kt#L35)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/AudioTracksSheet.kt`

102 satır · SHA-256 `31d60a9efe0fb22ee115ece1769e9840486547677092ddd6e818a9ba52ad6256`

[`AudioTracksSheet` L31](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/AudioTracksSheet.kt#L31) · [`AudioTrackRow` L72](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/AudioTracksSheet.kt#L72) · [`buildAudioTitle` L95](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/AudioTracksSheet.kt#L95)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/ChaptersSheet.kt`

89 satır · SHA-256 `e2909a0324c0a636c584d5a0254ff207ae9a04514933b72b5c2b2158f0e3de8c`

[`ChaptersSheet` L22](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/ChaptersSheet.kt#L22) · [`ChapterTrack` L53](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/ChaptersSheet.kt#L53) · [`prettyTime` L83](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/ChaptersSheet.kt#L83)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/GenericTracksSheet.kt`

96 satır · SHA-256 `c285ae21518b6b19dec59f380062e0b183d7977be8203b67199823b8e2d3fc90`

[`GenericTracksSheet` L26](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/GenericTracksSheet.kt#L26) · [`AddTrackRow` L47](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/GenericTracksSheet.kt#L47) · [`TrackSheetTitle` L78](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/GenericTracksSheet.kt#L78)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/MoreSheet.kt`

302 satır · SHA-256 `addea6234ea43003a5ff7c571df74e6b5d5084215c0fe7e4b3bd67fc95c8bb33`

[`MoreSheet` L60](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/MoreSheet.kt#L60) · [`TimePickerDialog` L217](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/MoreSheet.kt#L217)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/PlaybackSpeedSheet.kt`

134 satır · SHA-256 `f583dc75da591cb35628abf7eebbdcf1de361a4b82daf28f40f51b1177711406`

[`PlaybackSpeedSheet` L42](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/PlaybackSpeedSheet.kt#L42)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/PlayerSheet.kt`

108 satır · SHA-256 `844c37ed55f06451e3311dccd097c40c4314f1bf85fc297aa5dc69a0380f38b0`

[`PlayerSheet` L35](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/PlayerSheet.kt#L35)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/QualitySheet.kt`

102 satır · SHA-256 `e16b83c68da9b08b6af024ef2d9ca76a361eeec7ab5b79d0953ebd62a090a9ca`

[`QualitySheet` L27](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/QualitySheet.kt#L27) · [`QualitySourceRow` L68](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/QualitySheet.kt#L68)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/ScreenshotComponents.kt`

75 satır · SHA-256 `8a0eaf51c0500a227a0d9bb7643c680251a0f453eb0598d25dfd740a2c60bcd4`

[`ActionButton` L23](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/ScreenshotComponents.kt#L23) · [`SwitchPreference` L50](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/ScreenshotComponents.kt#L50)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/ScreenshotSheet.kt`

152 satır · SHA-256 `732ffd34e9517eccca37c5c820b0bec5ec5d3c1c69692b420689c75f62840660`

[`ScreenshotSheet` L29](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/ScreenshotSheet.kt#L29)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/SubtitleTracksSheet.kt`

206 satır · SHA-256 `09fa989fb5bd860d8594914d50bd1c715cd5893c2add720d4fe2a370442c5769`

[`SubtitleTracksSheet` L40](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/SubtitleTracksSheet.kt#L40) · [`SubtitleTrackRow` L150](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/SubtitleTracksSheet.kt#L150) · [`buildTrackTitle` L198](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/SubtitleTracksSheet.kt#L198)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerAudioController.kt`

68 satır · SHA-256 `5ff9359b40b2c95d7703590ee15167f297bcd5b634463aa1f0b2a2c0649fd15c`

[`startObserving` L45](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerAudioController.kt#L45) · [`stopObserving` L64](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerAudioController.kt#L64)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerAutoplayController.kt`

149 satır · SHA-256 `2b9b5da5f1811756e153d0b046296749747ebfcc9a53bf6079ff9cc591d52f02`

[`onEpisodeEnded` L54](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerAutoplayController.kt#L54) · [`startCountdown` L115](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerAutoplayController.kt#L115) · [`cancelCountdown` L133](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerAutoplayController.kt#L133) · [`onStillWatchingConfirmed` L140](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerAutoplayController.kt#L140) · [`resetSession` L145](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerAutoplayController.kt#L145)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerErrorRecoveryController.kt`

224 satır · SHA-256 `e46b20ff20388c190988f56f1dd5217e38e0affe990bb90d1686e2d2418c03cb`

[`onPlayerSwitched` L64](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerErrorRecoveryController.kt#L64) · [`onFatalError` L69](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerErrorRecoveryController.kt#L69) · [`onPlaybackError` L81](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerErrorRecoveryController.kt#L81) · [`triggerSourceFallbackOrFatal` L171](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerErrorRecoveryController.kt#L171) · [`onPlaybackReady` L198](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerErrorRecoveryController.kt#L198) · [`reset` L210](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerErrorRecoveryController.kt#L210) · [`showToastOnMain` L218](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerErrorRecoveryController.kt#L218)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerRuntimeOrchestrator.kt`

118 satır · SHA-256 `dcf9bcf4494957f3d567999404165f97aa739cf169b4f6924b9bf75694518248`

[`start` L90](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerRuntimeOrchestrator.kt#L90) · [`stop` L109](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerRuntimeOrchestrator.kt#L109)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerScrobbleController.kt`

112 satır · SHA-256 `22ea2fff855b2679204e954256c53c004e208192dc42e8a6fc3b1c21521a8d26`

[`onEpisodeStarted` L42](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerScrobbleController.kt#L42) · [`onPositionUpdate` L56](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerScrobbleController.kt#L56) · [`forceScrobble` L69](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerScrobbleController.kt#L69) · [`scrobble` L71](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerScrobbleController.kt#L71) · [`clear` L107](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerScrobbleController.kt#L107)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerSkipController.kt`

117 satır · SHA-256 `dedafdfed8af329cbb7def6eed9f00fcf84ac783677cab4782f0241e9c6d15ea`

[`observeSettings` L49](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerSkipController.kt#L49) · [`loadIntervals` L62](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerSkipController.kt#L62) · [`updateSettings` L102](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerSkipController.kt#L102)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerStillWatchingController.kt`

170 satır · SHA-256 `4105b54316816912dff74d3d9243d3a0729d47579c95b647cd9b98ffdc54562e`

[`onPlaybackTick` L51](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerStillWatchingController.kt#L51) · [`showPromptNow` L71](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerStillWatchingController.kt#L71) · [`onEpisodeCompleted` L84](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerStillWatchingController.kt#L84) · [`onConfirmed` L99](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerStillWatchingController.kt#L99) · [`onDismissed` L112](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerStillWatchingController.kt#L112) · [`onUserInteraction` L123](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerStillWatchingController.kt#L123) · [`applySettings` L133](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerStillWatchingController.kt#L133) · [`resetGating` L140](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerStillWatchingController.kt#L140) · [`startCountdown` L147](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerStillWatchingController.kt#L147)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadedPlayerActivity.kt`

92 satır · SHA-256 `2a0c5caa7db2881c419bb0f26ccf7e63b38080254f1b30bb3251cbf721ac47b8`

[`onNewIntent` L23](../app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadedPlayerActivity.kt#L23) · [`onCreate` L29](../app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadedPlayerActivity.kt#L29) · [`handleIntent` L34](../app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadedPlayerActivity.kt#L34) · [`isUriSafe` L58](../app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadedPlayerActivity.kt#L58)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadsActivity.kt`

52 satır · SHA-256 `15fd35c3999c45a158050f12f376b20b70c906fffe5bb260b843b117e9b90bd3`

[`onCreate` L22](../app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadsActivity.kt#L22)

### `app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadsScreen.kt`

1486 satır · SHA-256 `0fae570a4f23ed9ec1ccf4646559539e6e30cf503a1f0d3460826fd6d423002d`

[`normalizeImageGroupKey` L103](../app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadsScreen.kt#L103) · [`DownloadsScreen` L108](../app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadsScreen.kt#L108) · [`DownloadedImagesTab` L393](../app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadsScreen.kt#L393) · [`DownloadedImageCard` L618](../app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadsScreen.kt#L618) · [`DownloadItemRow` L739](../app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadsScreen.kt#L739) · [`SubtitleCardItem` L1061](../app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadsScreen.kt#L1061) · [`parseSubtitleFileInfo` L1193](../app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadsScreen.kt#L1193) · [`loadAllDownloadedSubtitles` L1222](../app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadsScreen.kt#L1222) · [`loadAllDownloadedImages` L1316](../app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadsScreen.kt#L1316) · [`openFolderInFileManager` L1363](../app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadsScreen.kt#L1363) · [`formatBytes` L1481](../app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadsScreen.kt#L1481)

### `app/src/main/java/com/kitsugi/animelist/ui/tv/player/TvPlayerActivity.kt`

185 satır · SHA-256 `a512bdc91b2b042e35a6715732f4ec4ee1ad0e118ff6cf75d7bca77fd1955dbf`

[`start` L43](../app/src/main/java/com/kitsugi/animelist/ui/tv/player/TvPlayerActivity.kt#L43) · [`onCreate` L102](../app/src/main/java/com/kitsugi/animelist/ui/tv/player/TvPlayerActivity.kt#L102) · [`onDestroy` L180](../app/src/main/java/com/kitsugi/animelist/ui/tv/player/TvPlayerActivity.kt#L180)

### `app/src/main/java/com/kitsugi/animelist/ui/tv/player/TvPlayerScreen.kt`

119 satır · SHA-256 `0c5612e10f3cfc0bbb97b8453ef4365e9588437dad56385cacaa4d9570ce5ffe`

[`TvPlayerScreen` L50](../app/src/main/java/com/kitsugi/animelist/ui/tv/player/TvPlayerScreen.kt#L50)

## Zincir arama kanıtları

Aşağıdakiler tüm main Kotlin kaynaklarında isim bazlı referanslardır. Tanım/import, çağrı anlamına gelmez. Satır başı yorumları dışlanır; bu bir Kotlin AST/call-graph analizi değildir.

### `updateChapters` — 1 referans

- [app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt:457](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L457): `fun updateChapters(chapterList: List<com.kitsugi.animelist.ui.screens.fullscreen.controls.components.IndexedSegment>) {`

### `updateChapter` — 1 referans

- [app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt:464](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L464): `fun updateChapter(chapter: com.kitsugi.animelist.ui.screens.fullscreen.controls.components.IndexedSegment?) {`

### `updateReadAhead` — 1 referans

- [app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt:383](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt#L383): `fun updateReadAhead(value: Long) {`

### `selectSecondarySubtitleTrack` — 1 referans

- [app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt:314](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L314): `fun selectSecondarySubtitleTrack(trackId: Int) {`

### `PreviewGenerator` — 4 referans

- [app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt:102](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt#L102): `import com.kitsugi.animelist.ui.screens.fullscreen.components.PreviewGenerator`
- [app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PreviewGenerator.kt:14](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PreviewGenerator.kt#L14): `class PreviewGenerator(`
- [app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PreviewGenerator.kt:20](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PreviewGenerator.kt#L20): `private const val TAG = "PreviewGenerator"`
- [app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PreviewGenerator.kt:203](../app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PreviewGenerator.kt#L203): `Log.d(TAG, "Released PreviewGenerator for $urlHash")`

### `applyMpvTlsOptions` — 1 referans

- [app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt:903](../app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt#L903): `internal fun applyMpvTlsOptions(`

### `subtitleItalic` — 4 referans

- [app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt:338](../app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt#L338): `if (style.bold || settings.subtitleItalic) {`
- [app/src/main/java/com/kitsugi/animelist/data/settings/AppSettings.kt:240](../app/src/main/java/com/kitsugi/animelist/data/settings/AppSettings.kt#L240): `val subtitleItalic: Boolean = false,`
- [app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt:564](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L564): `subtitleItalic = preferences[Keys.SubtitleItalic] ?: false,`
- [app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiPlayerSettingsDialog.kt:984](../app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiPlayerSettingsDialog.kt#L984): `val italicSub = appSettings?.subtitleItalic ?: false`

### `subtitleBackgroundColor` — 3 referans

- [app/src/main/java/com/kitsugi/animelist/data/settings/AppSettings.kt:244](../app/src/main/java/com/kitsugi/animelist/data/settings/AppSettings.kt#L244): `val subtitleBackgroundColor: Int = 0,`
- [app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt:566](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L566): `subtitleBackgroundColor = preferences[Keys.SubtitleBackgroundColor] ?: 0,`
- [app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiPlayerSettingsDialog.kt:1047](../app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiPlayerSettingsDialog.kt#L1047): `val currentBg = appSettings?.subtitleBackgroundColor ?: 0`

### `subtitleBorderColor` — 3 referans

- [app/src/main/java/com/kitsugi/animelist/data/settings/AppSettings.kt:248](../app/src/main/java/com/kitsugi/animelist/data/settings/AppSettings.kt#L248): `val subtitleBorderColor: Int = 0xFF000000.toInt(),`
- [app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt:568](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L568): `subtitleBorderColor = preferences[Keys.SubtitleBorderColor] ?: 0xFF000000.toInt(),`
- [app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiPlayerSettingsDialog.kt:1096](../app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiPlayerSettingsDialog.kt#L1096): `val currentBorderColor = appSettings?.subtitleBorderColor ?: 0xFF000000.toInt()`

### `subtitleFontScale` — 2 referans

- [app/src/main/java/com/kitsugi/animelist/data/settings/AppSettings.kt:256](../app/src/main/java/com/kitsugi/animelist/data/settings/AppSettings.kt#L256): `val subtitleFontScale: Float = 1.0f,`
- [app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt:572](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L572): `subtitleFontScale = preferences[Keys.SubtitleFontScale] ?: 1.0f,`

### `subtitleOverrideAss` — 2 referans

- [app/src/main/java/com/kitsugi/animelist/data/settings/AppSettings.kt:260](../app/src/main/java/com/kitsugi/animelist/data/settings/AppSettings.kt#L260): `val subtitleOverrideAss: Boolean = false,`
- [app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt:574](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L574): `subtitleOverrideAss = preferences[Keys.SubtitleOverrideAss] ?: false,`

### `playerStatisticsPage` — 2 referans

- [app/src/main/java/com/kitsugi/animelist/data/settings/AppSettings.kt:268](../app/src/main/java/com/kitsugi/animelist/data/settings/AppSettings.kt#L268): `val playerStatisticsPage: Int = 0,`
- [app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt:586](../app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt#L586): `playerStatisticsPage = preferences[Keys.PlayerStatisticsPage] ?: 0,`

### `preferredAudioLanguages` — 0 referans

Main Kotlin kaynaklarında bu isimde referans yok; bu sonuç eşdeğer davranışın yokluğunu tek başına kanıtlamaz.

### `requestAudioFocus` — 0 referans

Main Kotlin kaynaklarında bu isimde referans yok; bu sonuç eşdeğer davranışın yokluğunu tek başına kanıtlamaz.

### `onSeekTo` — 0 referans

Main Kotlin kaynaklarında bu isimde referans yok; bu sonuç eşdeğer davranışın yokluğunu tek başına kanıtlamaz.

### Lua asset dosyaları

app/src altında .lua dosyası bulunmadı. Runtime üretilen custombuttons.lua bu listeye dahil değildir.
