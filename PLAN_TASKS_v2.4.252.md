# Kitsugi CS Plan & Görevler — v2.4.252 (2026-10-12)

> Bu oturumun kaynağı: 6 ekran görüntüsü + Drive'daki canlı tanı raporu (2.4.251-beta.29862550).
> Kullanıcı bildirimleri: (1) altyazı görüntü modunda kayıyor, (2) Diziyou'da 3 ses seçeneği
> hiçbir şeyi değiştirmiyor, (3) Dizipal reklamı (KRALBET) video olarak veriliyor,
> (4) Watch2Movies'te yanlış sezon/bölüm oynuyor, (5) tanı sonuçlarına güven yok
> ("birçok eklenti patlak ya da sorunlu").

## ✅ TAMAMLANAN (bu oturum — kod `5ff4228` commit'i, 12 dosya, +881/−47)

### 1. Altyazı konumu — görüntü modundan bağımsız hâle getirildi
- [x] **Kök neden:** Media3 `exo_subtitles` görünümünü `exo_content_frame` (AspectRatioFrameLayout)
      İÇİNE koyar; Doldur/Sığdır/16:9/Yakınlaştır seçilince çerçeve yeniden ölçeklenir ve altyazı
      onunla birlikte kayar/gerilir. (Media3 1.8.0 `exo_player_view.xml` ile doğrulandı.)
- [x] `Media3PlayerEngine.detachSubtitleViewFromContentFrame`: altyazı PlayerView kök katmanına
      `MATCH_PARENT` olarak taşınır; başarısız olursa eski davranış korunur (çökme yok).
- [x] `SUBTITLE_BOTTOM_PADDING_FRACTION = 0.08` → sabit alt konum; dikey kaydırma altyazı alanının
      içine sabitlenir (ekran dışına/bar arkasına itilemez).

### 2. Ses parçası seçimi (Diziyou) — sessiz başarısızlık bitti
- [x] **mpv:** `userSelectedAudioId` (yeni yüklemede sıfırlanır) → `alang`/`aid=auto` yolları kullanıcı
      seçimini EZMEZ. Seçim üç yolla uygulanır (`setPropertyInt` → `command set aid` → metinsel `aid`),
      her adımda `getPropertyString("aid")` ile DOĞRULANIR; uygulanamazsa önceki parçaya dönülür.
- [x] **mpv:** Elle seçim sonrası `initialSelectionDone/selectionForced = true` → ilk otomatik seçim
      kullanıcının seçimini geri alamaz.
- [x] **Media3:** Override sonrası 450 ms beklenip `Tracks.Group.isTrackSelected()` ile kontrol;
      uygulanmadıysa `disable → enable + override` zorlaması.
- [x] **Media3:** Kullanıcı sesi elle seçtiyse otomatik ses politikası tamamen kapanır (seçim anındaki
      "hiç ses seçili değil" geçişi seçimi geri alamıyor).
- [x] Cihazın çözemeyeceği parçalar `⚠️ (cihaz desteklemiyor)` olarak etiketlenir; seçim yine
      uygulanmazsa kullanıcıya bildirim gösterilir ("Ses parçası değişmedi…").

### 3. Watch2Movies — yanlış sezon/bölüm
- [x] `CsEpisodeMatcher.pageSeasonOf`: sezon hem BAŞLIKtan hem URL'den okunur; URL kalıpları iki yönlü
      (`/supernatural-2-sezon` ve `/sezon-2/bolum-3`); `-2-sezon-3-bolum` artık "sezon 3" sayılmaz.
- [x] `CsEpisodeMatcher.episodeSeasonsOf`: sayfanın bölüm listesindeki ham sezonlar → **yanlış pozitif
      koruması** (sayfa istenen sezonu içeriyorsa çelişki yok).
- [x] `CsStreamRunner.extractSeriesStreams`: sezon çelişkisinde doğrudan bölüm eşlemesi atlanır,
      sezon gezinmesi önceliklidir; gezinme sonuçsuz + liste boşsa sayfa URL'si son çare;
      `recordSkip` mesajı "yanlış sezon oynatılmadı" olarak ayrıştı.

### 4. Dizipal — reklam akışı (KRALBET)
- [x] Yeni `CsStreamSanitizer`: bahis/reklam markaları (43 marka) hem URL hem görünen ad üzerinden,
      açık reklam kalıpları (reklam/preroll/bonus…) yalnız URL'de elenir.
- [x] Aynı hostta gerçek playlist (.m3u8/.mpd) varken HLS parçaları (`seg-1-v1-a1.ts`) ve URL
      tekrarları listeden çıkarılır.
- [x] **Kritik:** Eklenti YALNIZCA reklam döndürdüyse artık orijinal liste geri getirilmez →
      reklam asla oynatılabilir akış olarak sunulmaz, kullanıcı "kaynak yok" görür.

### 5. Tanı güvenilirliği — "eklenti bozuk" mu, "uygulama eksik" mi?
- [x] `REQUIRED_HOST_CLASSES` (KSerializer, Json, SerialDescriptor, Gson, ObjectMapper, OkHttpClient,
      Jsoup, MainAPI, NiceResponse) çalışma zamanında yoklanır → `hostAbiMissing` yayınlanır.
- [x] Rapora **"⚠️ Host ABI Eksik (UYGULAMA TARAFI)"** bölümü; tanı ekranına kırmızı uyarı kartı.
- [x] Bağlantı hatalarının mesajına kanıt: `[EKSİK HOST ABI: …]` veya `metot imzası: (...)`.
- [x] Rapor satırları kaynağı ayırır: `🧩 Host ABI eksik (UYGULAMA tarafı)` / `🧩 Runtime incompatible
      (eklenti tarafı)`.

### Paketleme
- [x] Sürüm `2.4.252` (`app/build.gradle.kts`, satır 40)
- [x] `release_notes_v2.4.252.md` (TR + EN)
- [x] Commit `5ff4228` → dal `arena/297ee38f-kitsugi-beta` (push edildi)

## ⏳ SIRADAKİ ADIMLAR

### Kritik (kullanıcı)
- [ ] `./gradlew assembleDebug` (veya `assembleRelease`) ile derle — bu ortamda SDK/gradle yok,
      derleme yapılamadı; statik denetim + Media3 1.8.0 API karşılaştırması yapıldı.
- [ ] Altyazı testi: video oynarken Doldur / Sığdır / 16:9 / Yakınlaştır arasında geç → altyazı
      yerinden oynamamalı.
- [ ] Ses testi (Diziyou ve 3 ses parçalı başka bir kaynak): her parçayı seç → ses gerçekten
      değişmeli; değişmezse ekranda "Ses parçası değişmedi…" bildirimi çıkacak (log ile birlikte ilet).
- [ ] Yeni tanı raporunu paylaş → raporda "Host ABI Eksik" bölümü olacak. Bu bölüm boşsa
      `KSerializer` hataları gerçekten eklenti tarafındadır; doluysa uygulama paketlemesi (R8) sorunludur.

### Uygulama backlog
- [ ] `KSerializer` hatası veren 12 eklenti (KraptorPlus, YeniKaynak, IWatchOffice, OKRu, Invidious,
      KissKH, WebteIzle, Movix, powerDizi, powerSinema, vavooSpor, RecTV) → yeni raporun Host ABI
      bölümüne göre: R8 keep kuralı mı, bayat kurulum mu, eklenti mi karar ver.
- [ ] mpv tarafında `trackIndex` (liste id'si) ↔ `aid` eşlemesi: şu an mpv `id` kullanılıyor ve
      doğrulanıyor; hâlâ uygulanmıyorsa log kanıtıyla `command("set","aid",…)` yoluna ağırlık ver.
- [ ] Arama katmanı: tanı raporunda tüm arama başlıkları S/E içeriyor ama `Ep`/`Flow` çoğu satırda
      boş → sorgu üretiminin S/E ayıklaması denetlenecek.
- [ ] Orphan `prebuilt_plugins.json` metadata temizliği (önceki turdan devam).

### Not
- `CloudStreamErrorClassifier`'da `\b403\b` → `CloudStreamPluginUnsupported` eşlemesi var; sabit URL'li
  eklentilerde site/erişim kaynaklı 403 ayırt edilemiyor (ileri tur için not).

## 📦 PAKET İÇERİĞİ (bu zip)

`Kitsugi-v2.4.252-paket.zip`
- `PLAN_TASKS_v2.4.252.md` (bu dosya) + `PLAN_TASKS_v2.4.251.md` (önceki tur, referans)
- `release_notes_v2.4.252.md`
- `DEGISEN_DOSYALAR.txt` (dosya listesi + commit + derleme komutu)
- `DIFF_v2.4.252.patch` (tam diff — `git apply` ile uygulanabilir)
- `degisen-dosyalar/` (repo yolu korunarak 11 değişen dosya)
