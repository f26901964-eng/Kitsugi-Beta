# Kitsugi Beta v2.4.319

## 🇹🇷 Türkçe

### 🎬 Video kaynak ekranı: görselli kartlar, akan üst bar, tek dokunuşta her şey

Bu sürüm, v2.4.318'de "kompakt" yapılırken fazla soyutlanan video kaynak ekranını
yeniden düzenler: kartlara **görsel** geri geldi, alanlar **her zaman görünür**,
üst bar kaydırmayla **yumuşakça** gizlenip geri geliyor ve "İzle"ye basınca oluşan
"video açılmıyor / uygulama ana sayfaya atıyor" hatası için çok katmanlı düzeltme yapıldı.

- **Görselli kartlar (dikey + yatay aynı düzen):**
  - Her kartta solda sabit 74×104 dp (2:3) kapak görseli var. Görsel önce eklentinin
    bölüm/video kapağından (`thumbnailUrl`), yoksa dizinin/filmin posterinden gelir.
  - Görsel yoksa vurgu renginde gradyan + film ikonu gösterilir; boş kutu görünmez.
  - Yetişkin içerik bulanıklığı (`KitsugiNsfwImage`) korunur.
- **Ayrıntılar artık gizli değil:** kalite, ses/altyazı türü, ses ve altyazı dilleri,
  dosya boyutu, Debrid/P2P durumu ve kaynak host'u tek bakışta görünür. v2.4.318'de
  her kartı tek tek açtıran "Ayrıntılar" akordiyonu kaldırıldı.
- **Yumuşak üst bar:** başlık barı kaydırma mesafesine birebir bağlı olarak yukarı kayar
  (parmakla birlikte akar, `graphicsLayer` translation + alpha). Listenin en başına
  dönüldüğünde kendini geri getirir. Eski `firstVisibleItemIndex >= 2` + `AnimatedVisibility`
  yaklaşımı sticky başlığın yüksekliğini aniden değiştirdiği için liste zıplıyordu.
- **Arayüz cilası:** vurgu renginde üst ışıma, daha yumuşak arama alanı, pill etiketler,
  kaynak seçim kartı ve grup başlıkları (dikey vurgu çubuğu + canlı video sayacı).
- **Daha az dokunuş:** kartın herhangi bir yerine dokunmak videoyu başlatır; sağdaki
  yeşil/vurgulu yuvarlak buton oynat, altındaki buton indir olarak ayrıldı.

### 🐞 "İzle'ye basınca video açılmıyor, ana sayfaya atıyor" düzeltmeleri

- **Binder taşması (TransactionTooLargeException):** oynatıcıya 125 kaynak + altyazı
  yedeği tek Intent'te gidiyordu; önceki eşikler (250 KB + 450 KB karakter) toplamda
  ~1 MB Binder sınırını aşabiliyordu. Yedek veri 96 KB'a indirildi, taşma durumunda
  **yedeksiz yeniden deneme** eklendi (video açılır, sadece "kaynak değiştir" listesi boş kalır).
- **Oynatıcı kurulumu artık süreci düşürmüyor:** `KitsugiFullscreenPlayerActivity.onCreate`
  try/catch ile sarıldı; veri yoksa/ kurulum patlarsa kullanıcıya sebep gösterilip
  ekran düzgün kapanıyor (stream ekranına dönülüyor), uygulama ana sayfaya atmıyor.
- **İzleme geçmişi yazımı oynatmayı engellemiyor:** `WatchHistoryManager.record`
  çağrısı try bloğunun dışındaydı; disk/veri hatası fırlarsa doğrudan çökmeye yol
  açıyordu. Artık `runCatching` içinde.
- **Sessiz başarısızlık bitti:** `startWithStreamUrls` artık `Boolean` döndürür; başarısızsa
  stream ekranında "Oynatıcı açılamadı" diyaloğu (sebebiyle birlikte) gösterilir.
- **Harici oynatıcı hatası:** harici oynatıcıya devir başarısız olduğunda tam ekran
  oynatıcı artık kapanmıyor (önceden açılıp hemen kapanıyor, "video açılmadı" görünüyordu);
  sebep toast ile söylenir ve kullanıcı başka kaynak seçebilir.
- **Receiver/servis güvenliği:** `pipReceiverRegistered` bayrağı ile kaydedilmemiş
  receiver'ı kaldırma çökmesi engellendi; `KeepAliveService` başlatma/durdurma korundu.

## 🇬🇧 English

### 🎬 Stream screen: thumbnail cards, smooth scrolling top bar, everything visible

- **Thumbnail cards (same in portrait & landscape):** fixed 74×104 dp (2:3) cover on the
  left, taken from the provider's episode thumbnail and falling back to the media poster.
  A gradient placeholder is used when no image exists; adult-content blur is preserved.
- **Details always visible:** quality, audio/subtitle type, audio & subtitle languages,
  file size, Debrid/P2P state and source host are shown without tapping. The
  "Details" accordion from v2.4.318 was removed.
- **Smooth top bar:** the header now translates with the scroll distance (graphicsLayer
  translation + alpha) instead of toggling `AnimatedVisibility`, which made the sticky
  header jump. It returns automatically at the top of the list.
- **UI polish:** accent glow, softer search field, pill tags, source picker card and
  richer group headers (accent bar + live video counter).
- **Fewer taps:** tapping the card plays; the round accent button plays and the button
  below downloads.

### 🐞 "Watch opens nothing and kicks me to the home screen" fixes

- **Binder overflow:** the player Intent could exceed the ~1 MB Binder limit with large
  source lists; backup payloads were capped at 96 KB and a retry **without** extras was added.
- **Player bootstrap can no longer kill the process:** `onCreate` is guarded; failures are
  reported and the screen closes back to the stream picker.
- **History write no longer blocks playback** (`runCatching` around `record`).
- **No more silent failures:** `startWithStreamUrls` returns a `Boolean`; the stream screen
  shows a "Player could not start" dialog with the reason.
- **Receiver/service lifecycle hardened** (`pipReceiverRegistered`, guarded `KeepAliveService`).
