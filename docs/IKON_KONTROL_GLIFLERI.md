# İkon ↔ işlev eşlemesi

Uygulamada kullanılan **268 farklı Material ikon adının** her biri, işlevine en yakın
3B varlığa bağlıdır. Eşleme tek tabloda durur: `KitsugiIconAssets.kt` → `NAME_MAP`.

| Ölçüm | Değer |
|---|---|
| 3B varlık (enum) | **143** |
| Kontrol glifi | 46 (`ic_kitsugi_glyph_*.png`, 128×128) |
| Nesne glifi (3dicons'ta olmayan kavramlar) | 9 (`ic_kitsugi_obj_*.png`, 192×192) |
| 3dicons render'ı | 88 (`ic_kitsugi_*.png`, 192×192) |
| Eşlenen Material adı | 428 satır / 268 benzersiz ad |
| Eşlenmemiş (mono kalan) ad | **0** |

## Neden tek tablo?

Önceki turlarda eşleme üç ayrı yerdeydi (enum listeleri, `EXTRA_ALIASES`,
`CONTROL_ALIASES`). Sonuç: aynı görsel birbirinden alakasız satırlarda tekrar ediyordu
(kitap/liste satırı yer imi, indirme satırı bavul, eşitleme satırı wifi, TV satırı monitör…).
Artık **her işlev kendi görseline** sahip; tablo tek yerde ve denetlenebilir.

## Kontrol glifleri — gerçek 3B render (SDF + ışın yürütme)

3dicons render'ları **gerçek 3B nesneler**: yuvarlatılmış kenarlı hacim, normal'e göre
gölgeleme, geniş spekülar parlama, fresnel kenar ışığı, temas gölgesi. SVG ile
"gradyan + kaydırılmış kopya" yaklaşımı poster gibi duruyordu; bu yüzden hattı
değiştirdik.

Akış (`tools/iconpack/sdf3d.js`):

| # | Adım | Ayrıntı |
|---|---|---|
| 1 | Siluet | SVG path → 768 px maske (`glyph3d.maskSvg`) |
| 2 | Mesafe alanı | kesin Öklid mesafe dönüşümü (Felzenszwalb), 4 kez yumuşatma |
| 3 | Hacim | siluet, kalınlığı `2×1.30` birim, kenar yarıçapı `0.62` olan yuvarlatılmış levhaya ekstrüde |
| 4 | Yüzey | her piksel için yuvarlatılmış kenar **analitik** olarak çözülür (ışın yürütme yok → tırtıl yok) |
| 5 | Normal | 2B dış normal + fillet açısı → 3B normal |
| 6 | Malzeme | Lambert gölge + iki loblu Blinn-Phong spekülar + fresnel + gökyüzü ışığı + geniş parlama lekesi |
| 7 | AO | siluet sınırına yaklaştıkça karartma |
| 8 | Renk | nesne sınırları içinde sol-üst `#FFC47C` → sağ-alt `#684AD5` rampası |
| 9 | Gölge | ışığın tersine kaydırılmış, bulanık temas gölgesi (%30) |
| 10 | Çıktı | 512 px render → kırp → LANCZOS3 ile 128 px (glif) / 192 px (nesne) |

İç ayrıntılar (kamera merceği, klavye tuşları, kek mumları, kaydırıcı düğmeleri)
render sonrası **oyma** katmanıyla eklenir: koyu çizgi + altında ince ışık.

Kanıt: **`docs/ikon-malzeme-karsilastirma.png`** — üst sıra orijinal 3dicons,
alt sıra aynı hatla üretilen glifler.

## Nesne glifleri

Pakette karşılığı olmayan ama farklı işlevleri temsil eden kavramlar da aynı hatla üretildi:

`sync` (eşitleme), `sliders` (ince ayar), `download`, `upload`, `timer` (kronometre),
`code` (açılı parantez), `tv`, `tag` (etiket), `backup` (bulut yedeği) — 192×192.

## Eşleme tablosu

| 3B varlık | İşlev | Dosya | Bağlı Material adları |
|---|---|---|---|
| `CtlList` | liste görünümü / çalma listesi | `ic_kitsugi_glyph_list` | `ViewList`, `List`, `FormatListBulleted`, `FormatListNumbered`, `ListAlt`, `PlaylistAdd`, `PlaylistAddCheck`, `PlaylistPlay`, `QueuePlayNext`, `LibraryAdd`, `Toc` |
| `Network` | ağ / DNS | `ic_kitsugi_wifi` | `Wifi`, `WifiOff`, `NetworkCheck`, `SettingsEthernet`, `Dns`, `Lan`, `Web`, `RssFeed`, `AddLink`, `SignalCellularAlt`, `CloudOff` |
| `Sync` | eşitleme / yenile | `ic_kitsugi_obj_sync` | `Sync`, `SyncAlt`, `Refresh`, `Cached`, `Autorenew`, `Repeat`, `RepeatOne`, `RestartAlt`, `SwapHoriz`, `CompareArrows`, `CloudSync` |
| `Timer` | zamanlayıcı / alarm | `ic_kitsugi_obj_timer` | `Timer`, `Schedule`, `AccessTime`, `HourglassEmpty`, `HourglassFull`, `HourglassBottom`, `MoreTime`, `AvTimer`, `Upcoming`, `Alarm`, `AlarmOn` |
| `Picture` | görsel / fotoğraf | `ic_kitsugi_picture` | `Image`, `Photo`, `InsertPhoto`, `Wallpaper`, `ImageSearch`, `BrokenImage`, `ImageNotSupported`, `Slideshow`, `Preview`, `PictureInPictureAlt`, `PhotoLibrary` |
| `Palette` | renk & tema | `ic_kitsugi_colour_palette` | `Palette`, `ColorLens`, `FormatPaint`, `Gradient`, `InvertColors`, `Opacity`, `Style`, `FormatColorFill`, `FormatColorReset` |
| `Download` | indir | `ic_kitsugi_obj_download` | `Download`, `DownloadForOffline`, `CloudDownload`, `Downloading`, `FileDownload`, `SystemUpdate`, `SystemUpdateAlt`, `SaveAlt`, `Save` |
| `Player` | oynatıcı / video | `ic_kitsugi_video_camera` | `Movie`, `PlayCircle`, `PlayCircleFilled`, `VideoSettings`, `SlowMotionVideo`, `Videocam`, `VideoLibrary`, `ControlCamera` |
| `Warning` | uyarı / hata | `ic_kitsugi_sheild` | `BugReport`, `Warning`, `WarningAmber`, `ErrorOutline`, `Error`, `Report`, `PriorityHigh`, `ReportProblem` |
| `Gestures` | hareketler | `ic_kitsugi_magic_trick` | `TouchApp`, `Swipe`, `SwipeLeft`, `SwipeRight`, `SwipeUp`, `SwipeDown`, `Gesture`, `PanTool` |
| `Sun` | aydınlık tema | `ic_kitsugi_sun` | `BrightnessMedium`, `Brightness7`, `BrightnessLow`, `BrightnessHigh`, `Brightness6`, `WbSunny`, `LightMode`, `Contrast` |
| `NavProfile` | profil (sekme) | `ic_kitsugi_boy` | `Person`, `People`, `Groups`, `Group`, `PersonOutline`, `PersonAdd`, `Person2` |
| `CheckCircle` | onay / tamam | `ic_kitsugi_tick` | `Check`, `CheckCircle`, `CheckCircleOutline`, `Done`, `DoneAll`, `TaskAlt` |
| `Integrations` | entegrasyon / dil | `ic_kitsugi_sphere` | `Hub`, `Language`, `Public`, `TravelExplore`, `Cloud`, `Translate` |
| `Storage` | sil / çöp | `ic_kitsugi_trash_can` | `Delete`, `DeleteOutline`, `DeleteForever`, `DeleteSweep`, `CleaningServices`, `RemoveCircle` |
| `Decoder` | kod çözücü / donanım | `ic_kitsugi_tool` | `Memory`, `DeveloperBoard`, `Hd`, `HighQuality`, `Handyman`, `HomeRepairService` |
| `Chart` | istatistik | `ic_kitsugi_chart` | `BarChart`, `DonutLarge`, `Insights`, `ShowChart`, `TrendingUp`, `Analytics` |
| `Lock` | güvenlik / kilit | `ic_kitsugi_lock` | `Lock`, `LockOpen`, `Security`, `HealthAndSafety`, `VerifiedUser`, `PrivacyTip` |
| `Calendar` | takvim | `ic_kitsugi_calender` | `CalendarToday`, `DateRange`, `Event`, `EventAvailable`, `CalendarMonth`, `Today` |
| `CtlClose` | kapat / temizle | `ic_kitsugi_glyph_close` | `Close`, `Cancel`, `Clear`, `ClearAll`, `FormatClear` |
| `Audio` | ses | `ic_kitsugi_headphone` | `VolumeUp`, `VolumeDown`, `VolumeMute`, `VolumeOff`, `Hearing` |
| `Mic` | mikrofon / ses kaydı | `ic_kitsugi_mic` | `Mic`, `MicNone`, `MicOff`, `KeyboardVoice`, `RecordVoiceOver` |
| `Moon` | karanlık tema | `ic_kitsugi_moon` | `DarkMode`, `NightsStay`, `Bedtime`, `Nightlight`, `ModeNight` |
| `Key` | şifre / anahtar | `ic_kitsugi_key` | `Key`, `VpnKey`, `Login`, `Logout`, `Password` |
| `Folder` | klasör | `ic_kitsugi_folder` | `Folder`, `FolderOpen`, `FolderSpecial`, `FolderCopy`, `FolderFilled` |
| `Mobile` | mobil cihaz | `ic_kitsugi_mobile` | `PhoneAndroid`, `Tablet`, `Devices`, `Smartphone`, `PhoneIphone` |
| `CtlChevronUp` | yukarı / daralt | `ic_kitsugi_glyph_chevron_up` | `KeyboardArrowUp`, `ExpandLess`, `ArrowDropUp`, `UnfoldLess` |
| `CtlChevronDown` | aşağı / genişlet | `ic_kitsugi_glyph_chevron_down` | `KeyboardArrowDown`, `ExpandMore`, `ArrowDropDown`, `UnfoldMore` |
| `CtlArrowLeft` | geri | `ic_kitsugi_glyph_arrow_left` | `ArrowBack`, `ArrowBackIos`, `West`, `KeyboardBackspace` |
| `CtlExternal` | dışa aç | `ic_kitsugi_glyph_external` | `OpenInNew`, `OpenInBrowser`, `Launch`, `ExitToApp` |
| `CtlGrid` | ızgara görünümü | `ic_kitsugi_glyph_grid` | `GridView`, `ViewQuilt`, `ViewWeek`, `ViewModule` |
| `CtlCrop` | kırp / odakla | `ic_kitsugi_glyph_crop` | `Crop`, `CropFree`, `CenterFocusWeak`, `FilterCenterFocus` |
| `CtlTextTitle` | başlık / boyut | `ic_kitsugi_glyph_textTitle` | `Title`, `FormatSize`, `FormatColorText`, `TextFields` |
| `Pause` | duraklat / durdur | `ic_kitsugi_pause` | `Pause`, `PauseCircle`, `StopCircle`, `Stop` |
| `Next` | sonraki | `ic_kitsugi_next` | `SkipNext`, `FastForward`, `Forward10`, `Forward` |
| `Rewind` | geri sar | `ic_kitsugi_back` | `SkipPrevious`, `FastRewind`, `Replay`, `Restore` |
| `Extensions` | eklenti | `ic_kitsugi_puzzle` | `Extension`, `ExtensionOff`, `Widgets`, `Apps` |
| `Database` | veritabanı / depolama | `ic_kitsugi_locker` | `Storage`, `SdCard`, `Archive`, `SdStorage` |
| `Tune` | ince ayar / kaydırıcılar | `ic_kitsugi_obj_sliders` | `Tune`, `Equalizer`, `GraphicEq`, `SettingsApplications` |
| `Feedback` | geri bildirim | `ic_kitsugi_megaphone` | `Feedback`, `RateReview`, `Campaign`, `Announcement` |
| `Memo` | not / düzenle | `ic_kitsugi_pencil` | `Edit`, `EditNote`, `Notes`, `Create` |
| `Clipboard` | kopyala / yapıştır | `ic_kitsugi_copy` | `ContentCopy`, `ContentPaste`, `FileCopy`, `Assignment` |
| `Buttons` | özel buton / oyun | `ic_kitsugi_toggle` | `SmartButton`, `SportsEsports`, `Gamepad`, `Vibration` |
| `Code` | kod / geliştirici | `ic_kitsugi_obj_code` | `Code`, `CodeOff`, `Terminal`, `DataObject` |
| `Speed` | hız / performans | `ic_kitsugi_rocket` | `Speed`, `Bolt`, `FlashOn`, `RocketLaunch` |
| `Tv` | TV / yayın | `ic_kitsugi_obj_tv` | `Tv`, `ConnectedTv`, `LiveTv`, `SmartDisplay` |
| `Star` | puan / yıldız | `ic_kitsugi_star` | `Star`, `StarBorder`, `StarHalf`, `Grade` |
| `Trophy` | başarı / ödül | `ic_kitsugi_trophy` | `EmojiEvents`, `WorkspacePremium`, `MilitaryTech`, `Stars` |
| `Bell` | bildirim | `ic_kitsugi_bell` | `Notifications`, `NotificationsNone`, `NotificationsActive`, `NotificationsOff` |
| `Pin` | konum | `ic_kitsugi_pin` | `LocationOn`, `Place`, `PinDrop`, `MyLocation` |
| `NavList` | listem (sekme) | `ic_kitsugi_bookmark` | `Bookmark`, `BookmarkBorder`, `Bookmarks`, `BookmarkAdded` |
| `NavSearch` | arama | `ic_kitsugi_zoom` | `Search`, `ManageSearch`, `ZoomIn`, `ZoomOut` |
| `Chat` | sohbet / yorum | `ic_kitsugi_chat_bubble` | `ChatBubble`, `ChatBubbleOutline`, `Comment`, `Forum` |
| `Plus` | ekle | `ic_kitsugi_plus` | `Add`, `AddCircle`, `AddCircleOutline` |
| `CtlArrowUp` | yukarı ok | `ic_kitsugi_glyph_arrow_up` | `ArrowUpward`, `ArrowCircleUp`, `North` |
| `CtlFunnel` | filtre | `ic_kitsugi_glyph_funnel` | `FilterList`, `FilterAlt`, `FilterAltOff` |
| `CtlSort` | sırala | `ic_kitsugi_glyph_sort` | `Sort`, `SortByAlpha`, `SwapVert` |
| `CtlFullscreen` | tam ekran | `ic_kitsugi_glyph_fullscreen` | `Fullscreen`, `FitScreen`, `SettingsOverscan` |
| `CtlRotate` | döndür | `ic_kitsugi_glyph_rotate` | `ScreenRotation`, `RotateLeft`, `RotateRight` |
| `Account` | hesap | `ic_kitsugi_card` | `AccountCircle`, `ManageAccounts`, `Badge` |
| `Connections` | bağlantı / paylaş | `ic_kitsugi_link` | `Link`, `LinkOff`, `Share` |
| `Backup` | yedek / bulut | `ic_kitsugi_obj_backup` | `CloudUpload`, `CloudDone`, `Backup` |
| `History` | geçmiş | `ic_kitsugi_clock` | `History`, `Update`, `WatchLater` |
| `Help` | yardım | `ic_kitsugi_chat` | `Help`, `HelpOutline`, `QuestionMark` |
| `Notebook` | kitap / defter | `ic_kitsugi_notebook` | `MenuBook`, `Book`, `AutoStories` |
| `Subtitles` | altyazı | `ic_kitsugi_chat_text` | `Subtitles`, `SubtitlesOff`, `ClosedCaption` |
| `Sparkles` | yenilik | `ic_kitsugi_flash` | `AutoAwesome`, `NewReleases`, `AutoFixHigh` |
| `Tag` | etiket | `ic_kitsugi_obj_tag` | `Label`, `Sell`, `Tag` |
| `Mail` | e-posta | `ic_kitsugi_mail` | `Mail`, `Email`, `MailOutline` |
| `Phone` | arama / destek | `ic_kitsugi_phone_ringing` | `PhoneInTalk`, `Phone`, `Call` |
| `CtlChevronRight` | sağ ok | `ic_kitsugi_glyph_chevron_right` | `ChevronRight`, `NavigateNext` |
| `CtlChevronLeft` | sol ok | `ic_kitsugi_glyph_chevron_left` | `ChevronLeft`, `NavigateBefore` |
| `CtlArrowRight` | ileri | `ic_kitsugi_glyph_arrow_right` | `ArrowForward`, `East` |
| `CtlArrowDown` | aşağı ok | `ic_kitsugi_glyph_arrow_down` | `ArrowDownward`, `South` |
| `CtlEye` | göster | `ic_kitsugi_glyph_eye` | `Visibility`, `RemoveRedEye` |
| `CtlMore` | diğer (⋮) | `ic_kitsugi_glyph_more` | `MoreVert`, `MoreHoriz` |
| `CtlBorder` | kenarlık | `ic_kitsugi_glyph_border` | `BorderOuter`, `BorderColor` |
| `CtlKeyboard` | klavye | `ic_kitsugi_glyph_keyboard` | `Keyboard`, `KeyboardAlt` |
| `CtlInput` | girişler | `ic_kitsugi_glyph_input` | `SettingsInputComponent`, `SettingsInputHdmi` |
| `CtlLeaf` | bitki / doğa | `ic_kitsugi_glyph_leaf` | `LocalFlorist`, `Forest` |
| `Play` | oynat | `ic_kitsugi_play` | `PlayArrow`, `PlayCircleOutline` |
| `Info` | bilgi | `ic_kitsugi_bulb` | `Info`, `InfoOutline` |
| `Document` | belge / metin dosyası | `ic_kitsugi_file_text` | `Description`, `Article` |
| `Music` | müzik | `ic_kitsugi_music` | `MusicNote`, `Audiotrack` |
| `Advanced` | gelişmiş | `ic_kitsugi_lab` | `DeveloperMode`, `Construction` |
| `Heart` | favori / kalp | `ic_kitsugi_heart` | `Favorite`, `FavoriteBorder` |
| `NavExplore` | keşfet (sekme) | `ic_kitsugi_explorer` | `Explore`, `SearchOff` |
| `NavSettings` | ayarlar (sekme) | `ic_kitsugi_setting` | `Settings`, `Build` |
| `Fire` | trend / popüler | `ic_kitsugi_fire` | `Whatshot`, `LocalFireDepartment` |
| `Gift` | hediye / davet | `ic_kitsugi_gift` | `CardGiftcard`, `Redeem` |
| `Brush` | çizim | `ic_kitsugi_paint_brush` | `Brush`, `Draw` |
| `Cube` | katman / 3B | `ic_kitsugi_cube` | `ViewInAr`, `Layers` |
| `CtlMinus` | azalt | `ic_kitsugi_glyph_minus` | `Remove` |
| `CtlEyeOff` | gizle | `ic_kitsugi_glyph_eyeOff` | `VisibilityOff` |
| `CtlStream` | akış görünümü | `ic_kitsugi_glyph_stream` | `ViewStream` |
| `CtlCarousel` | karusel görünümü | `ic_kitsugi_glyph_carousel` | `ViewCarousel` |
| `CtlAspect` | en-boy oranı | `ic_kitsugi_glyph_aspect` | `AspectRatio` |
| `CtlBlur` | bulanıklık | `ic_kitsugi_glyph_blur` | `BlurOn` |
| `CtlAlignLeft` | sola hizala | `ic_kitsugi_glyph_alignLeft` | `FormatAlignLeft` |
| `CtlAlignCenter` | ortala | `ic_kitsugi_glyph_alignCenter` | `FormatAlignCenter` |
| `CtlAlignJustify` | iki yana yasla | `ic_kitsugi_glyph_alignJustify` | `FormatAlignJustify` |
| `CtlSelectAll` | tümünü seç | `ic_kitsugi_glyph_selectAll` | `SelectAll` |
| `CtlDrag` | sürükle / sırala | `ic_kitsugi_glyph_drag` | `DragHandle` |
| `CtlDensity` | yoğunluk | `ic_kitsugi_glyph_density` | `DensityMedium` |
| `CtlDoubleArrow` | aralık / genişlet | `ic_kitsugi_glyph_doubleArrow` | `DoubleArrow` |
| `CtlHome` | ana sayfa | `ic_kitsugi_glyph_home` | `Home` |
| `CtlSnow` | kar / soğuk | `ic_kitsugi_glyph_snow` | `AcUnit` |
| `CtlCake` | pasta / doğum Günü | `ic_kitsugi_glyph_cake` | `Cake` |
| `CtlTextBold` | kalın | `ic_kitsugi_glyph_textBold` | `FormatBold` |
| `CtlTextItalic` | italik | `ic_kitsugi_glyph_textItalic` | `FormatItalic` |
| `CtlTextHash` | numara / # | `ic_kitsugi_glyph_textHash` | `Numbers` |
| `CtlTextStrike` | üstü çizili | `ic_kitsugi_glyph_textStrike` | `FormatStrikethrough` |
| `CtlTextQuote` | alıntı | `ic_kitsugi_glyph_textQuote` | `FormatQuote` |
| `Upload` | yükle | `ic_kitsugi_obj_upload` | `Upload` |
| `NewFolder` | yeni klasör | `ic_kitsugi_new_folder` | `CreateNewFolder` |
| `ThumbUp` | beğen | `ic_kitsugi_thumb_up` | `ThumbUp` |
| `ThumbDown` | beğenme | `ic_kitsugi_thumb_down` | `ThumbDown` |
| `Target` | hedef | `ic_kitsugi_target` | `TrackChanges` |
| `Flag` | işaretle | `ic_kitsugi_flag` | `OutlinedFlag` |
| `Calculator` | hesaplama | `ic_kitsugi_calculator` | `Calculate` |
| `Scissor` | kes | `ic_kitsugi_scissor` | `ContentCut` |
| `Eyedropper` | renk seçici | `ic_kitsugi_eyedropper` | `Colorize` |
| `Hash` | etiket / # | `ic_kitsugi_hash` | `AlternateEmail` |
| `Text` | metin denetimi | `ic_kitsugi_text` | `Spellcheck` |
| `Wallet` | cüzdan / ödeme | `ic_kitsugi_wallet` | `AccountBalanceWallet` |
| `Battery` | pil / güç | `ic_kitsugi_battery` | `BatteryFull` |
| `HeartBell` | favori bildirimi | `ic_kitsugi_notify_heart` | `NotificationAdd` |
| `Circle` | durum | `ic_kitsugi_circle` | `Circle` |

## Görseller

- `docs/ikon-islev-haritasi.png` — 128 varlık, işlev etiketi ve bağlı adlarla
- `docs/ikon-kontrol-sayfasi.png` — 46 kontrol glifi
- `docs/ikon-kontrol-baglam.png` — glifler gerçek ekran bağlamında
- `docs/ikon-kontrol-boyut.png` — 12–30 dp okunabilirlik
- `docs/ikon-seti-onizleme.png` — 65 anahtar, 3B set
- `docs/ikon-tema-uyumu.png` — vurgu rengine otomatik ton uyumu

## Malzeme sistemi (v7 — glif başına 3B stil)

3dicons setindeki her nesne kendi malzemesiyle render edilmiş: kulaklık mat deri +
koyu ped, kalp yumuşak mat seramik, anahtar parlak emaye, laboratuvar camı + sıvı,
kasa fırçalanmış metal. Tek malzemeyle üretilen glifler bu yüzden "hepsi aynı
plastik" görünüyordu. Artık **her glif kendi malzemesini ve gövde kurgusunu**
seçiyor; ışık yönü, renk rampası ve temas gölgesi ortak olduğu için set tek aile
olarak kalıyor.

| Malzeme | Kalınlık / kenar | Karakter | Kullanıldığı işlevler |
|---------|------------------|----------|------------------------|
| `clay` — mat seramik | 1.52 / 0.98 | geniş yumuşak kenar, düşük spekülar, yüksek ortam | ev, kek, ızgara, eksi, oran, ortala, tırnak, A, zamanlayıcı |
| `gloss` — parlak emaye | 1.10 / 0.50 | sıkı parlak spekülar, canlı doygunluk | kapat, sırala, kırp, döndür, girdi, karusel, kamera, eşitle |
| `gel` — jöle | 1.34 / 0.88 | kalın, iç ışımalı, yarı saydam | chevron'lar, daha fazla, çift ok, yaprak, S, indir, bulut |
| `glass` — cam | 1.10 / 0.72 | ince parlak kenar, kalınlaşan koyu duvar | göz, gizle, huni, damla, kar tanesi |
| `obsidian` — koyu cam | 1.18 / 0.56 | koyu gövde, çok keskin spekülar, parlak kenar | dışa aç, tam ekran, kenarlık, #, B, kod, TV ekranı |
| `rubber` — mat kauçuk | 1.46 / 0.74 | en koyu ve en mat, ince gren | akış, tutamak, iki yana yasla, tümünü seç, klavye, TV gövdesi |
| `paper` — ince kâğıt | 0.56 / 0.22 | çok ince levha, geniş temas gölgesi | ok'lar, liste, yoğunluk, sola hizala, etiket, yükle |

### Yüzey derinliği (v8 — "gerçek nesne" hissi)

Orijinal render'larda eksik olan üç ipucu eklendi:

1. **Alt koyulaşma** — nesne zemine yaklaştıkça %20'ye kadar koyulaşır (`bottomAO`).
2. **Pah dibi gölgesi** — düz yüz ile yuvarlatılmış kenarın birleştiği yerde ince koyu
   kırışıklık (`crease`, radius'un %62'sinde).
3. **Stüdyo ışık bandı** — üst yarıda yumuşak softbox yansıması (`band`).
4. **Siluet kenarı** — dış kenarda ince koyu hat (`rimDark`), parlak fresnel halkası
   yerine. "Neon boru" görüntüsü bu şekilde tamamen kalktı.

### Krom aksanlar

Orijinallerdeki gümüş parçalar (mikrofon çemberi, kasa kadranı, para banknotu)
gibi küçük metal aksanlar 8 ikonda kullanılıyor — `chrome` malzemesi
(metal yansıma, %10 doygunluk, çift ölçekli stüdyo yansıması):

| Glif | Krom parça |
|------|-----------|
| kamera | mercek çerçevesi |
| zamanlayıcı | tepe düğmesi + kasnak |
| kaydırıcılar | üç düğme |
| TV | ayak (sap + taban) |
| etiket | perçin halkası |
| huni | üst ağız çemberi |
| girdi alanı | yuva çerçevesi |
| klavye | (krom kaldırıldı — fazla ağır duruyordu; boşluk tuşu emaye) |

Glif başına renk yerleşimi de farklı: her ad için `phase` (0–0.34) ve `span`
(0.78–0.96) hesaplanır, böylece gradyan her ikonda başka yere düşer.

### Parçalı gövdeler (çok malzemeli glifler)

| Glif | Gövde | Parçalar |
|------|-------|----------|
| kamera | emaye | obsidyen mercek + oyuk halka |
| göz | cam | obsidyen bebek |
| klavye | kauçuk | emaye tuş takımı (4 + 1 tuş) |
| kek | kil | emaye mumlar (3) |
| karusel | emaye | yan kartlar (2) |
| kaydırıcılar | emaye ray | kil düğmeler (3) |
| zamanlayıcı | kil gövde | emaye tepe düğmesi (2) |
| TV | kauçuk gövde | obsidyen ekran |
| kilit (3dicons) | — | mevcut orijinal render |

Üretici: `tools/iconpack/render_glyph.js` (gövde + parçalar + oyma katmanı),
malzemeler: `tools/iconpack/sdf3d.js` (`MATERIALS`), stil tablosu:
`tools/iconpack/style_report.js` → `docs/ikon-malzeme-haritasi.md`,
`docs/ikon-stil-haritasi.png`.

### Parçalı kurulum (v9 — ikon ikon elden geçirme)

Her glif tek levha değil; işlevine göre gerçek parçalara ayrıldı. Her parça kendi
malzemesi, kendi kalınlığı ve **altındaki yüzeye düşen kendi gölgesi** ile render
ediliyor (`render_glyph.js` → `shadowOffset [-0.16, 0.20]`, alfa 0.34).

| Glif | Gövde | Üstüne oturan parçalar |
|------|-------|------------------------|
| ev | kil gövde | kaldırılmış çatı mahyası (kil) + obsidyen kapı |
| kek | kil sünger | krom tabak + jöle krema bandı + 3 emaye mum |
| kamera | emaye gövde | obsidyen mercek + krom mercek çerçevesi + krom flaş + obsidyen vizör |
| ızgara | obsidyen taban plakası | 4 kil karo (her biri ayrı renk fazında) |
| göz | cam gövde | obsidyen bebek + krom iris halkası |
| huni | cam huni | jöle sıvı + oyma ızgara çizgileri |
| tutamak | kauçuk çubuklar | 4 krom pim |
| yaprak | jöle yaprak | kil sap |
| girdi | emaye gövde | oyma yuva + krom düğme |
| kar tanesi | cam taneler | krom merkez |
| karusel | emaye orta kart | 2 yan kart |
| klavye | kauçuk gövde | 4 + 1 emaye tuş |
| TV | kauçuk gövde | obsidyen ekran + krom ayak |
| zamanlayıcı | kil gövde | krom tepe düğmesi + krom kasnak |
| kaydırıcılar | emaye raylar | 3 krom düğme |
| indir | jöle ok | krom tepsi |
| yükle | kâğıt ok | emaye tepsi |
| etiket | kâğıt etiket | krom perçin |
| yedek | jöle bulut | emaye ok |
| dışa aç | obsidyen çerçeve | emaye ok (ayrı katman) |
