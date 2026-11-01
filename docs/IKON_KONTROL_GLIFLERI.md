# Kontrol glifleri (2B → 3B dönüşümü)

Bu belge, uygulamada **tek renkli Material vektörü** olarak kalan bütün kontrol
işaretlerinin nasıl 3B sete taşındığını belgeler. Hedef tek cümle: *uygulamada 3B
ailenin dışında tek bir ikon kalmayacak.*

## Özet

| Ölçüm | Değer |
|---|---|
| Kontrol glifi varlığı | **46** (`app/src/main/res/drawable-nodpi/ic_kitsugi_glyph_*.png`) |
| 3dicons'tan gelen kontrol varlığı | 2 (`ic_kitsugi_plus.png`, `ic_kitsugi_thumb_down.png`) |
| Eşlenen Material ikon adı | 164 |
| Önce mono olan çağrı | 385 |
| Şimdi mono olan çağrı | **0** |

## Üretim reçetesi

3dicons paketinde kapat / chevron / filtre / sırala / görünüm modu işaretleri yok.
Glifler `tools/iconpack/control_glyphs.js` ile **setin kendi paleti** kullanılarak çizilir:

| Adım | Kural |
|---|---|
| Gradyan | `#E9A57E → #CF6A86 → #B04F9B → #8A35AB → #7043B2` (5 örneklemden ölçüldü) |
| Üst parlama | y < %48 bölgesinde beyaz mask, %26 → %0 |
| Gölge | `#3A0F4C`, %50 saydam, 0.85 ofset, 0.5 blur |
| Alt çizgi | `#5C2481`, kontur genişliği + 0.75 |
| Kontur | 2.75 birim (harflerde 700 ağırlık, 16–17 boyut) |
| Render | 384 px → kırp → `1/√(alan oranı)` büyüt (üst sınır 1.45) → 192 px kutunun %92'si |
| Çıktı | 128×128 saydam PNG (palet, sıkıştırma 9) |
| Alfa tabanı | < 26 → 0 (PNG gürültüsü temizlenir) |

Yeniden üretmek için:

```bash
cd tools/iconpack && npm install --no-audit --no-fund
cd ../.. && node tools/iconpack/control_glyphs.js   # PNG'ler
node tools/iconpack/control_previews.js             # docs/ikon-kontrol-*.png
node tools/iconpack/coverage_report.js              # kapsam denetimi
```

## Eşleme tablosu

| Glif | Anlam | Varlık | Bağlanan Material adları |
|---|---|---|---|
| `close` | Kapat / temizle / iptal | `ic_kitsugi_glyph_close` | `Cancel`, `Clear`, `ClearAll`, `Close`, `CloseFullscreen` |
| `minus` | Azalt | `ic_kitsugi_glyph_minus` | `IndeterminateCheckBox`, `Minus`, `Remove`, `RemoveCircleOutline` |
| `plus` | Ekle | `ic_kitsugi_plus` | `Add`, `AddCircle`, `AddCircleOutline`, `AddRounded`, `CreateNewFolder`, `LibraryAdd`, `NoteAdd` |
| `thumb_down` | Beğenme | `ic_kitsugi_thumb_down` | `ThumbDown`, `ThumbDownAlt`, `ThumbDownOffAlt` |
| `chevron_up` | Yukarı / daralt | `ic_kitsugi_glyph_chevron_up` | `ArrowDropUp`, `ExpandLess`, `KeyboardArrowUp`, `UnfoldLess` |
| `chevron_down` | Aşağı / genişlet | `ic_kitsugi_glyph_chevron_down` | `ArrowDropDown`, `ExpandMore`, `KeyboardArrowDown`, `UnfoldMore` |
| `chevron_left` | Sol / önceki | `ic_kitsugi_glyph_chevron_left` | `ChevronLeft`, `KeyboardArrowLeft`, `NavigateBefore` |
| `chevron_right` | Sağ / sonraki | `ic_kitsugi_glyph_chevron_right` | `ChevronRight`, `KeyboardArrowRight`, `NavigateNext` |
| `arrow_up` | Yukarı ok | `ic_kitsugi_glyph_arrow_up` | `ArrowCircleUp`, `ArrowUpward`, `North` |
| `arrow_down` | Aşağı ok | `ic_kitsugi_glyph_arrow_down` | `ArrowCircleDown`, `ArrowDownward`, `South` |
| `arrow_left` | Geri | `ic_kitsugi_glyph_arrow_left` | `ArrowBack`, `ArrowBackIos`, `ArrowBackIosNew`, `ArrowBackIosNewRounded`, `KeyboardBackspace`, `West` |
| `arrow_right` | İleri | `ic_kitsugi_glyph_arrow_right` | `ArrowForward`, `ArrowForwardIos`, `ArrowForwardIosRounded`, `East` |
| `eye` | Göster / önizle | `ic_kitsugi_glyph_eye` | `Preview`, `RemoveRedEye`, `Visibility`, `VisibilityRounded` |
| `eyeOff` | Gizle | `ic_kitsugi_glyph_eyeOff` | `HideImage`, `VisibilityOff` |
| `funnel` | Filtrele | `ic_kitsugi_glyph_funnel` | `Filter`, `FilterAlt`, `FilterAltOff`, `FilterList`, `FilterListAlt`, `FilterListOff` |
| `sort` | Sırala | `ic_kitsugi_glyph_sort` | `ImportExport`, `LowPriority`, `Sort`, `SortByAlpha`, `SwapVert` |
| `grid` | Izgara görünümü | `ic_kitsugi_glyph_grid` | `GridView`, `ViewComfy`, `ViewModule`, `ViewQuilt`, `ViewWeek` |
| `list` | Liste görünümü | `ic_kitsugi_glyph_list` | `FormatListBulleted`, `Toc`, `ViewAgenda`, `ViewList` |
| `stream` | Akış görünümü | `ic_kitsugi_glyph_stream` | `ViewStream` |
| `carousel` | Karusel görünümü | `ic_kitsugi_glyph_carousel` | `ViewCarousel` |
| `more` | Diğer (⋮) | `ic_kitsugi_glyph_more` | `MoreHoriz`, `MoreVert` |
| `external` | Dışa aç | `ic_kitsugi_glyph_external` | `ExitToApp`, `Launch`, `OpenInBrowser`, `OpenInNew`, `OpenInNewOff` |
| `fullscreen` | Tam ekran | `ic_kitsugi_glyph_fullscreen` | `FitScreen`, `Fullscreen`, `FullscreenExit`, `SettingsOverscan` |
| `crop` | Kırp / odak | `ic_kitsugi_glyph_crop` | `CenterFocusStrong`, `CenterFocusWeak`, `Crop`, `CropFree`, `CropOriginal`, `CropSquare`, `FilterCenterFocus` |
| `rotate` | Döndür | `ic_kitsugi_glyph_rotate` | `Flip`, `Rotate90DegreesCcw`, `RotateLeft`, `RotateRight`, `ScreenRotation` |
| `aspect` | Oran | `ic_kitsugi_glyph_aspect` | `AspectRatio` |
| `blur` | Bulanıklık | `ic_kitsugi_glyph_blur` | `BlurLinear`, `BlurOn` |
| `alignLeft` | Sola hizala | `ic_kitsugi_glyph_alignLeft` | `FormatAlignLeft`, `FormatIndentDecrease` |
| `alignCenter` | Ortala | `ic_kitsugi_glyph_alignCenter` | `FormatAlignCenter` |
| `alignJustify` | İki yana yasla | `ic_kitsugi_glyph_alignJustify` | `FormatAlignJustify`, `FormatAlignRight`, `FormatIndentIncrease` |
| `border` | Kenarlık | `ic_kitsugi_glyph_border` | `BorderAll`, `BorderColor`, `BorderOuter`, `BorderStyle` |
| `selectAll` | Tümünü seç | `ic_kitsugi_glyph_selectAll` | `SelectAll` |
| `drag` | Sürükle / sırala | `ic_kitsugi_glyph_drag` | `DragHandle`, `DragIndicator`, `Reorder` |
| `density` | Yoğunluk | `ic_kitsugi_glyph_density` | `DensityLarge`, `DensityMedium`, `DensitySmall`, `LineWeight` |
| `keyboard` | Klavye | `ic_kitsugi_glyph_keyboard` | `Keyboard`, `KeyboardAlt`, `KeyboardReturn` |
| `input` | Giriş / girişler | `ic_kitsugi_glyph_input` | `Cable`, `SettingsInputComponent`, `SettingsInputHdmi` |
| `doubleArrow` | Genişlet / aralık | `ic_kitsugi_glyph_doubleArrow` | `DoubleArrow`, `Height`, `Width` |
| `home` | Ana sayfa | `ic_kitsugi_glyph_home` | `Cottage`, `Home`, `HomeFilled` |
| `leaf` | Bitki / doğa | `ic_kitsugi_glyph_leaf` | `Eco`, `Forest`, `LocalFlorist`, `Spa` |
| `snow` | Kar / soğuk | `ic_kitsugi_glyph_snow` | `AcUnit`, `SevereCold` |
| `cake` | Pasta / doğum günü | `ic_kitsugi_glyph_cake` | `Cake`, `CakeVariant` |
| `camera` | Kamera | `ic_kitsugi_glyph_camera` | `CameraAlt`, `ControlCamera`, `PhotoCamera`, `PhotoCameraFront` |
| `textBold` | Kalın | `ic_kitsugi_glyph_textBold` | `FormatBold` |
| `textItalic` | İtalik | `ic_kitsugi_glyph_textItalic` | `FormatItalic` |
| `textTitle` | Başlık / boyut | `ic_kitsugi_glyph_textTitle` | `FormatColorText`, `FormatSize`, `TextFields`, `Title` |
| `textHash` | Numara / etiket | `ic_kitsugi_glyph_textHash` | `FormatShapes`, `Numbers` |
| `textStrike` | Üstü çizili | `ic_kitsugi_glyph_textStrike` | `FormatClear`, `FormatColorReset`, `FormatStrikethrough` |
| `textQuote` | Alıntı | `ic_kitsugi_glyph_textQuote` | `FormatQuote` |

## Kodda nerede devreye giriyor?

`KitsugiIconAssets.kt` içindeki `fromVector` sırası:

1. `CONTROL_ALIASES` — ad bir mikro kontrolü gösteriyorsa kazanır (ör. `Close`, `ExpandMore`).
2. Enum `vectorNames` — konu ikonları (ör. `Download`, `Settings`).
3. `EXTRA_ALIASES` — ikinci tur konu eşlemeleri.

Bu sıra sayesinde 1200+ çağrı yerini tek tek değiştirmek gerekmez; proje
`Icon(imageVector = …)` sarmalayıcısı adı çözüp 3B varlığı çizer.

## Görseller

- `docs/ikon-kontrol-sayfasi.png` — 46 glif, adlarıyla
- `docs/ikon-kontrol-baglam.png` — gerçek ekran bağlamında kullanım
- `docs/ikon-kontrol-boyut.png` — 12–30 dp okunabilirlik testi
- `docs/ikon-kapsam-denetimi.png` — 1533/1533, mono 0
