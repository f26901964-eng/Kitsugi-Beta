# Kök neden + çözüm — Wiki okuyucu şeridi ve OP/ED videolarında siyah ekran

**Tarih:** 2026-10-21
**Sürüm:** v2.4.337
**Kapsam:** (1) Uygulama içi Fandom wiki okuyucusunun üst denetimleri, (2) detay sayfası "Videolar" sekmesindeki açılış/kapanış (OP/ED) oynatıcısı.

---

## 1. Wiki okuyucusu: dağınık beş satır → tek sekme şeridi + `⋮`

### 1.1 Ekrandaki sorun (önce)

Özgün (WebView) wiki görünümünde sayfanın üstünde **art arda beş ayrı denetim satırı** vardı:

| Satır | İçerik | Yükseklik |
|---|---|---|
| Üst şerit | geri, ileri, başlık, dil, yeni sekme, kapat | ~52 dp |
| Sekme şeridi | **tek sekme olsa bile** çizilen sekme çubuğu | ~44 dp |
| Mod çipleri | `Özgün sayfa` + `Sade okuyucu` + `Masaüstü düzeni` + `Yenile` (yatay kaydırmalı) | ~48 dp |
| İpucu | `swordartonline.fandom.com · Özgün tema; çeviri ve reklam engeli…` | ~20 dp |
| Sayfa araçları | `Orijinal göster` + `Reklam engeli: açık (15)` (48 dp yüksekliğinde iki tam genişlik çip) + `⋮` | ~56 dp |

Toplam **~220 dp**. 1080×2400 bir telefonda içeriğin neredeyse beşte biri, çoğu tekrar eden
kontrol için harcanıyordu.

### 1.2 Yeni düzen (sonra): ekranda yalnızca sekme şeridi

```
┌──────────────────────────────────────────────────────────┐
│ [ Sword Art Online × ] [ Aincrad × ] [ + ]            ⋮   │  ~41 dp
├──────────────────────────────────────────────────────────┤
│                        WebView içeriği                    │
└──────────────────────────────────────────────────────────┘
```

- **Başlık çubuğu tamamen kaldırıldı.** Geriye yalnızca Chrome/Brave mantığındaki sekme
  şeridi kaldı: 32 dp sekmeler, sekme kapatma 22 dp daire, şeridin sonunda `+`.
- **`+` (yeni sekme)** şeridin içinde, hep aynı yerde; 12 sekme sınırında pasifleşiyor
  (kaybolmuyor, böylece düzen zıplamıyor).
- **Şerit tek sekmede de görünür.** Kullanıcı "sadece sekme kısmı kalsın" dedi; sekme
  şeridi artık tarayıcının tek görünür denetim grubu.
- **Sağ köşedeki `⋮`** (34 dp daire) kalan her şeyi barındırıyor.
- Şeridin altında tek bir 1 dp ayırıcı; toplam şerit yüksekliği **~41 dp** (önce ~220 dp).
  Kazanç ~179 dp — 1080×2400 ekranda içeriğe %7'den fazla ek alan.

### 1.3 `⋮` menüsünün içeriği (hiçbir işlev kaybolmadı)

| Bölüm | Öğeler |
|---|---|
| Gezinme | **Geri**, **İleri** (geçmiş yoksa pasif) |
| Görünüm | **Özgün sayfa** ✓, **Sade okuyucu** ✓ (seçili olanın yanında onay işareti) |
| Sayfa (yalnızca özgün görünümde) | **Sayfa araçları**, **Masaüstü düzeni / Mobil düzen**, **Yenile** |
| Tarayıcı | **Tarayıcıda aç**, **Yeni sekme**, **Kapat** |

**Sayfa araçları** ayrı bir sayfa (dialog) olarak açılıyor: çeviri anahtarı (durum + çevrilen
düğüm sayısı), reklam engeli anahtarı (engellenen istek sayısı), **Görünüm** (tema + yazı
boyutu), **Sayfada bul**, **Hakkında** ve durum/uyarı listesi. Uyarı varsa altında **Yenile**
görünüyor. Bu sayfa artık WebView'in üstünde hiç satır kaplamıyor; eskiden dört ayrı yerde
duran bilgi tek ekranda toplandı.

Menü öğeleri 48 dp satır yüksekliğinde (erişilebilirlik korunuyor), `⋮` 34 dp daire.

### 1.4 Tarayıcı içindeki bağlantılar otomatik olarak YENİ sekmede

Eskiden WebView'de bir bağlantıya dokunmak **okuduğun sayfayı kaybettiriyordu**: aynı sekmede
yeni belgeye gidiliyor, geri dönmek için tarayıcı geçmişine güvenmek gerekiyordu.

Artık `shouldOverrideUrlLoading` ana çerçevede, kullanıcı dokunuşuyla gelen **düz makale
bağlantılarını** yakalıyor ve `FandomMediaWikiViewModel.openReaderWebLink` /
`FandomCharacterWikiViewModel.openReaderWebLink` üzerinden **aktif sekmenin hemen sağında**
yeni sekmede açıyor (Chrome davranışı). Kaynak sayfa yerinde kalır, tek dokunuşla geri dönülür.

Kasıtlı olarak **yerinde** kalanlar:

| Durum | Neden |
|---|---|
| `#bölüm` çapaları, aynı belge | Sekme kimliği yok; sayfa içi gezinme bölünmemeli |
| `?action=edit/history`, `?diff=`, `?oldid=` | Sekme modeli wiki + başlık ile çalışır; diff/düzenleme görünümü kaybolurdu |
| `Special:` / `File:` / `Category:` gibi makale olmayan sayfalar | Aynı sebep; ayrıca geri tuşu geçmişi korur |
| JS / form / otomatik yönlendirmeler (`hasGesture()` yok) | Her otomatik yönlendirme sekme üretmemeli |
| 12 sekme sınırı dolu | Bağlantı ölü doğmasın diye geçmişli gezinmeye düşülür |
| Aynı sayfa zaten aktif | Gereksiz yeniden yükleme yapılmaz |

Aynı sayfa başka bir sekmede açıksa yeni sekme üretilmez, o sekmeye geçilir.

### 1.5 Dokunulan dosyalar

| Dosya | Değişiklik |
|---|---|
| `ui/screens/detail/wiki/FandomWikiReader.kt` | `ReaderTopBar` kaldırıldı; sekme şeridi `trailing` yuvasıyla `⋮` menüsünü taşıyor (`ReaderBrowserMenu`). Menüde geri/ileri, mod seçimi, sayfa araçları, masaüstü/mobil, yenile, tarayıcıda aç, yeni sekme, kapat. |
| `ui/screens/detail/wiki/WikiPageTools.kt` | Görünür satır yok: `WikiPageToolsController` + tek `WikiPageToolsDialog` (çeviri, reklam engeli, görünüm, bul, hakkında, durum listesi). |
| `ui/screens/detail/wiki/WikiOriginalPage.kt` | Yalnızca WebView + isteğe bağlı bul çubuğu; `shouldOverrideUrlLoading` dokunuşla gelen makale bağlantısını yeni sekmeye yönlendiriyor. |
| `ui/screens/detail/wiki/WikiChromeComponents.kt` | Sadeleşti: `WikiIconAction` + `WikiChromeHairline`. |
| `FandomMediaWikiViewModel.kt`, `FandomCharacterWikiViewModel.kt` | `openReaderWebLink(url): Boolean` — bağlantıyı sağdaki yeni sekmeye açar, açılamazsa `false` döndürüp yerinde gezinmeye izin verir. |
| `MediaWikiTabContent.kt`, `CharacterWikiTabSlot.kt`, `CharacterDetailPage.kt` | `onOpenLinkInNewTab = viewModel::openReaderWebLink` bağlantısı. |
| `res/values/strings.xml`, `res/values-en/strings.xml` | `wiki_page_tools`, `wiki_mobile_layout`, `wiki_desktop_layout`, `wiki_tools_translate_title`, `wiki_tools_ad_block_title`, `wiki_tools_ad_block_blocked` ve oynatıcı metinleri — iki dilde birebir aynı anahtar kümesi. |

**Kullanım kolaylığı notları:** menü öğeleri tek elle erişilebilen sağ üst köşede, seçili mod
onay işaretiyle işaretli, dokunma hedefleri en az 34 dp (`⋮`) / 48 dp (menü satırı).

---

## 2. OP/ED videolarında "ses var, görüntü yok"

### 2.1 Üç ayrı kök neden bulundu

#### (A) Motor değişiminden sonra oynatıcı yüzeyi hiç yeniden kurulmuyordu — **asıl hata**

`ThemePlayerContainer` ve `KitsugiYouTubePlayer` şu deseni kullanıyordu:

```kotlin
val playerEngine = remember(context, activeEngineType, url) { … }   // motor değişince YENİ örnek

AndroidView(factory = { ctx -> playerEngine.createVideoView(ctx) })  // ❌ factory bir kez çalışır
```

Compose'da `AndroidView`'ın `factory`'si **düğüm ilk oluşturulurken bir kez** çalışır.
`activeEngineType` değişip `playerEngine` yeni bir örnek olduğunda aynı konumdaki düğüm
**eski motorun görünümünü** tutmaya devam ediyordu. Eski motor `release()` ile serbest
bırakıldığı için elinde kalan yüzey ölü; yeni motorun ise hiç yüzeyi yok.

Sonuç tam olarak bildirilen tablo: **ses çalıyor, süre ilerliyor (00:02 / 01:30), ekran
siyah.** Bu, "ses-only bekçisi" bir kez motor değiştirdikten sonra her videoyu kalıcı olarak
siyah bırakıyordu.

**Çözüm:** oynatıcı görünümü motora anahtarlanır:
```kotlin
key(playerEngine) { AndroidView(factory = { ctx -> playerEngine.createVideoView(ctx) }) }
```
Aynı düzeltme tam ekran oynatıcıya da uygulandı (`KitsugiFullscreenPlayerScreen.kt`), çünkü
orada da Media3 ↔ MPV geçişi aynı deseni kullanıyordu.

#### (B) Kompakt oynatıcı varsayılan olarak MPV'nin SurfaceView'ını kullanıyordu

Genel ayar varsayılanı `playerPreference = "MPV"`. Fragman satırı Media3'ün **TextureView**'ını
kullanırken OP/ED satırı MPV'nin **SurfaceView**'ını kullanıyordu. SurfaceView, Compose'un
kırpılmış/ölçüsü animasyonla değişen kartında (kart `clip()` + `animateDetailContentSize()`)
görünür bir yüzeye bağlanamıyor; mpv sesi çalıyor, kare üretmiyor.

**Çözüm:** kompakt oynatıcı **Media3 (TextureView) ile başlar**
(`initialCompactThemeEngineType(url)`). Kullanıcının genel motor tercihi **tam ekranda**
aynen korunur. Media3 başarısız olursa MPV bir kez yedek olarak denenir. Ayrıca MPV yedeği
devreye girdiğinde kart ve oynatıcı kabuğu artık kırpma katmanı kullanmıyor
(`background(color, shape)`), böylece SurfaceView kırpılmıyor.

**VP9 ön kontrolü:** kaynak `.webm` ise ve cihazda kayıtlı VP9 çözücüsü yoksa
(`MediaCodecList`) kompakt oynatıcı doğrudan MPV ile başlar. Böylece bekçinin motoru
değiştirmesini bekleyen **~6 saniyelik siyah ekran da atlanır**. Çözücü listesi
okunamazsa güvenli taraf seçilir (`Media3`).

#### (C) "Video var" kontrolü kare çizildiğini kanıtlamıyordu

`hasVideoTrack` yalnızca *parça seçildi* diyordu. Media3 `currentTracks` içinde video
parçasını, çözücü hiç kare üretemese bile `isSelected = true` gösterir; MPV'de `vo-configured`
görüntü çıkışının kurulduğunu söyler, karenin görünür bir yüzeye gittiğini söylemez.

**Çözüm:** `PlayerEngine`'e `hasRenderedVideoFrame` yeteneği eklendi.
- Media3: `Player.Listener.onRenderedFirstFrame()` ile işaretlenir, her yeni medyada sıfırlanır.
- MPV: `vo-configured` + `isShown` + `width/height > 0` + `Surface.isValid()`.
- Bekçi artık `inlineVideoVisible(engine) = hasVideoTrack && hasRenderedVideoFrame` okuyor.

#### (D) Son durak artık dürüst

İki motor da görüntü üretemezse eskiden yalnızca "Tekrar Dene" vardı; kullanıcı aynı siyah
ekrana dönüyordu. Yeni hata kartı birincil eylem olarak **"Tam ekranda aç"** sunuyor (MPV
orada yazılım çözücüyle VP9 dahil açabilir), ikincil eylem yeniden deneme.

### 2.2 Akış (yeni)

```
OP/ED URL ─► .webm + VP9 çözücüsü yok ──► MPV (kırpmasız SurfaceView)
          └─ diğer ─► Media3 (TextureView)
                        ├─ hata ─────────────────► MPV (bir kez)
                        │                            └─ hata ─► "Tam ekranda aç" kartı
                        └─ 6 sn içinde görünür kare yok ─► MPV (bir kez)
```

Her motor değişiminde görünüm yeniden oluşturulur (`key(playerEngine)`), dolayısıyla
yeni motor her zaman kendi yüzeyine sahiptir.

### 2.3 Videolar sekmesi görsel düzeni

- Grup kartları tek yüzeyde: ikon, başlık, **parça sayısı rozeti** ve genişletme düğmesi
  aynı satırda.
- Satırlar 44 dp, köşe 12 dp; seçili satır vurgunun %14'ü ile işaretli, başlık tek satır.
- Oynatıcı satırın hemen altında, kartın içinde 16:9; ayrı "Kapat" satırı yerine sağa yaslı
  küçük bir kapatma düğmesi.
- Oynatıcı ortasındaki büyük "duraklat" simgesi oynarken gizlendi (video artık kapanmıyor).
- Başlıklar sadeleşti: "Açılış Müzikleri / Videolar" → **"Açılış Müzikleri"**, "Kapanış
  Müzikleri" aynı dille.

### 2.4 Dokunulan dosyalar (oynatıcı)

| Dosya | Değişiklik |
|---|---|
| `core/player/engine/PlayerEngine.kt` | Yeni `hasRenderedVideoFrame` yeteneği (varsayılan `hasVideoTrack`). |
| `core/player/engine/Media3PlayerEngine.kt` | `onRenderedFirstFrame` takibi, yeni medyada sıfırlama. |
| `core/player/engine/MpvPlayerEngine.kt` | `hasRenderedVideoFrame` = VO + görünür yüzey. |
| `core/player/engine/KitsugiMpvSurfaceView.kt` | `hasVisibleVideoSurface()` (isShown, ölçü, `Surface.isValid`). |
| `ui/screens/detail/InlinePlayerSupport.kt` | `initialCompactThemeEngineType(url)` + VP9 çözücü ön kontrolü, `inlineVideoVisible()`, bekçide kare kontrolü, duraklatılmış bekçiye 30 sn üst sınır. |
| `ui/screens/detail/KitsugiDetailThemesTrailerComponents.kt` | Media3-first motor seçimi, `key(playerEngine)` ile yüzey yenileme, kırpmasız MPV kabuğu, yeni hata kartı, videolar sekmesi tasarımı. |
| `ui/screens/detail/KitsugiDetailPlayerComponents.kt` | YouTube küçük oynatıcıda `key(playerEngine)`, ortadaki duraklat simgesinin oynarken gizlenmesi. |
| `ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt` | Motor değişiminde oynatıcı görünümünün yeniden kurulması. |
| `test/.../InlinePlayerSupportTest.kt` | Yeni motor politikası ve kare/parça ayrımı testleri. |

---

## 3. Doğrulama durumu (dürüst rapor)

- **Kod incelemesi + statik denge kontrolü:** tamamlandı.
  - String kaynakları: `values` ve `values-en` **17.538 anahtar**, çift kayıt yok; değişen
    tüm Kotlin dosyalarındaki `R.string.*` başvurularının tamamı iki dilde de tanımlı.
  - XML dosyaları ayrıştırıldı (`xml.dom.minidom`) — hata yok.
  - Değişen Kotlin dosyalarında süslü parantez/parantez dengesi doğrulandı (yorum ve
    string sabitleri ayıklanarak).
  - Kullanılan ikonların (`Tune`, `Search`, `OpenInNew`, `Check`, `Add`, `MoreVert`,
    `Security`, `Translate`, `Info`, `Web`, `Refresh`) `material-icons-extended`
    içinde çözümlendiği ve projede başka dosyalarda da import edildiği doğrulandı.
  - Çağrı imzaları çapraz kontrol edildi: `tvClickable`, `KitsugiButton`,
    `KitsugiPlayerGestureWrapper`, `onAccentColor`, `WikiIconAction`, `WikiPageTools`,
    `WikiPageToolsController.consume`, `PlayerEngine.Listener`, `PlayerEngine.prepare`.
  - **Kullanılmayan kod temizlendi:** `WikiSegmentedSwitch`, `WikiToolPill`,
    `WikiSegmentHeight`, `WikiPillHeight` kaldırıldı (hiçbir çağrı yeri kalmamıştı);
    `WikiPageTools`'daki `Refresh` importu ve `FandomWikiReader`'daki eski şerit artıkları
    silindi.
- `./gradlew :app:testDebugUnitTest`: **çalıştırılamadı** — bu ortamda JDK yok
  (`java: command not found`). Android SDK/Gradle önbelleği de bulunmuyor.
- **Gerçek cihaz testi yapılmadı.** Sahada doğrulanması gerekenler:
  1. OP/ED satırı: `adb logcat | grep -E "ThemePlayerContainer|InlinePlayer"` —
     `engine=MEDIA3 visibleVideo=true` satırı ilk 6 saniyede görünmeli.
  2. Bekçi tetiklenirse `did not start (audio-only playback (track=…, rendered=…))` logu
     ve ardından MPV denemesi görünür; MPV de başarısızsa hata kartı çıkar.
  3. Wiki okuyucusu: WebView'in üstünde yalnızca ~41 dp'lik sekme şeridi + sağ köşede `⋮`
     görünmeli; menüde tüm komutlar çalışmalı; "Kapat" hem menüde hem geri tuşunda.
  4. Bağlantı testi: bir makale bağlantısına dokun → yeni sekme açılmalı, kaynak sekme
     yerinde kalmalı; `Special:`/diff bağlantısı aynı sekmede açılmalı; 12 sekme doluyken
     bağlantı ölü doğmamalı (aynı sekmede gezinmeli).

## 4. Bilinen sınırlar

- Cihazda VP9 donanım çözücüsü yoksa Media3 kare üretemez. Bu durumda MPV yedeği ya da
  tam ekran oynatıcı gerekir; uygulama artık bunu sessizce yutmuyor, açıkça sunuyor.
- `lib-decoder-ffmpeg-release.aar` **ses** çözücüleri içerir (VP9 video çözücüsü yoktur);
  VP9 için yazılım yolu yalnızca MPV'dir.
