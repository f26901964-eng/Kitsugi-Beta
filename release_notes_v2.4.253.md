# Kitsugi-Beta v2.4.253 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🎬 1. Altyazı Artık Görüntü Modundan Bağımsız (Doldur / Sığdır / 16:9 / Yakınlaştır)
- **Kök Neden:** Media3, altyazı görünümünü (`exo_subtitles`) videoyu taşıyan `exo_content_frame` (AspectRatioFrameLayout) **içine** yerleştirir. Görüntü modu değiştiğinde bu çerçevenin en-boy oranı ve ölçüsü değişiyor; altyazı da onunla birlikte ölçeklenip kayıyor, geriliyor/kısalıyordu.
- **Çözüm:** `Media3PlayerEngine.detachSubtitleViewFromContentFrame` altyazı görünümünü içerik çerçevesinden çıkarıp PlayerView kök katmanına `MATCH_PARENT` olarak taşır; `SUBTITLE_BOTTOM_PADDING_FRACTION = 0.08` ile sabit alt konum uygulanır. Dikey kaydırma değeri altyazı alanının içine sabitlenir (ekran dışına itilemez).
- **Sonuç:** Video nasıl ölçeklenirse ölçeklensin altyazı **hep aynı yerde, aynı genişlikte**.

### 🔊 2. Ses Parçası Seçimi Gerçekten Uygulanıyor (Diziyou: 3 seçenek, hep aynı İngilizce ses)
- **mpv:** Seçim üç yolla uygulanır (`setPropertyInt` → `command set aid` → metinsel `aid`) ve `aid` geri okunarak **doğrulanır**; uygulanamazsa önceki parçaya dönülür ve ayrıntılı log bırakılır. Kullanıcının elle seçtiği parça (`userSelectedAudioId`) dil tercihi/`aid=auto` yolları tarafından artık **ezilmez**.
- **Media3:** Override uygulandıktan 450 ms sonra parçanın gerçekten seçili olup olmadığı kontrol edilir; değilse `disable → enable + override` zorlaması yapılır.
- **Görünürlük:** Cihazın çözemeyeceği parçalar listede `⚠️ (cihaz desteklemiyor)` olarak işaretlenir; seçim yine uygulanmazsa kullanıcıya "Ses parçası değişmedi…" bildirimi gösterilir.

### 📺 3. Sezon Çelişkisi Kapısı (Watch2Movies: içerik doğru, sezon/bölüm yanlış)
- Yüklenen sayfanın başlığı/URL'si **başka bir sezonu** bildiriyorsa doğrudan bölüm eşlemesi atlanır ve önce **doğru sezon sayfasına** inilir.
- URL kalıpları iki yönlü okunur: `/supernatural-2-sezon` (anahtar sözcükten önce) ve `/sezon-2/bolum-3` (sonra). `-2-sezon-3-bolum` adresi artık yanlışlıkla "sezon 3" sayılmaz.
- **Yanlış pozitif koruması:** Sayfanın bölüm listesi istenen sezonu içeriyorsa (çok sezonlu tek sayfa) çelişki sayılmaz. Gezinme başarısız olur ve bölüm listesi boşsa sayfa URL'si son çare olarak kullanılır — kaynak tamamen kaybolmaz.

### 🚫 4. Reklam / HLS Parçası Temizliği (Dizipal: KRALBET reklamı)
- Bahis/reklam markaları ve açık reklam kalıpları hem URL hem görünen ad üzerinden elenir (`kralbet.mp4` vakası).
- Eklenti **yalnızca reklam** döndürdüyse artık orijinal liste geri getirilmez: reklam **asla** oynatılabilir akış olarak sunulmaz, kullanıcı "kaynak yok" görür.
- Aynı hostta gerçek bir playlist (.m3u8/.mpd) varken HLS parçaları (`seg-1-v1-a1.ts`) ve URL tekrarları listeden çıkarılır.

### 🧪 5. Tanı Güvenilirliği: "Eklenti Bozuk" mu, "Uygulama Eksik" mi?
- **Host ABI denetimi:** `kotlinx.serialization.KSerializer`, `json.Json`, `SerialDescriptor`, `com.google.gson.Gson`, `ObjectMapper`, `OkHttpClient`, `Jsoup`, `MainAPI`, `NiceResponse` sınıfları çalışma zamanında yoklanır; eksikse rapora **“⚠️ Host ABI Eksik (UYGULAMA TARAFI)”** bölümü eklenir ve tanı ekranında kırmızı uyarı gösterilir.
- Bağlantı (linkage) hatalarının mesajına kanıt eklenir: `[EKSİK HOST ABI: kotlinx.serialization.KSerializer]` veya `metot imzası: (...)`.
- Rapor satırları kaynağı ayırır: `🧩 Host ABI eksik: … (UYGULAMA tarafı)` / `🧩 Runtime incompatible (eklenti tarafı)`. Böylece "onlarca eklenti patlak" görüntüsünün uygulama paketlemesinden mi kaynaklandığı **kanıtla** görülür.

---

## 🇬🇧 English

### 🎬 1. Subtitles Are Now Independent of the Video Resize Mode (Fill / Fit / 16:9 / Zoom)
- **Root cause:** Media3 places the subtitle view (`exo_subtitles`) *inside* `exo_content_frame` (AspectRatioFrameLayout). When the display mode changes, that frame is re-measured/re-scaled, dragging the subtitles along with it.
- **Fix:** `Media3PlayerEngine.detachSubtitleViewFromContentFrame` reparents the subtitle view to the PlayerView root as `MATCH_PARENT`; `SUBTITLE_BOTTOM_PADDING_FRACTION = 0.08` provides a stable bottom anchor, and the manual vertical offset is clamped inside the subtitle area.
- **Result:** The subtitle stays in the same place at the same width regardless of how the video is scaled.

### 🔊 2. Audio Track Selection Actually Applies (Diziyou: 3 options, always the same English audio)
- **mpv:** Selection is applied three ways (`setPropertyInt` → `command set aid` → string `aid`) and **verified** by reading `aid` back; on failure the previous track is restored and a detailed log is written. The user's manual pick (`userSelectedAudioId`) is no longer overwritten by language-preference/`aid=auto` paths.
- **Media3:** 450 ms after the override the engine verifies the track is really selected; if not, it forces `disable → enable + override`.
- **Visibility:** Tracks the device cannot decode are labelled `⚠️ (cihaz desteklemiyor)`; if a switch still fails, the user gets an explicit notification.

### 📺 3. Season Conflict Gate (Watch2Movies: right content, wrong season/episode)
- If the loaded page declares a **different season**, the direct episode match is skipped and the correct season page is visited first.
- URL patterns are read in both directions (`/supernatural-2-sezon` and `/sezon-2/bolum-3`); `-2-sezon-3-bolum` is no longer misread as "season 3".
- **False-positive guard:** if the page's episode list already contains the requested season, no conflict is reported. If navigation fails and the list is empty, the page URL is used as a last resort.

### 🚫 4. Ad / HLS Fragment Cleanup (Dizipal: KRALBET ad)
- Betting/ad brands and explicit ad patterns are filtered by URL *and* display name (`kralbet.mp4` case).
- If a plugin returns **only** ads, the original list is no longer restored: an ad is **never** offered as a playable stream.
- HLS fragments (`seg-1-v1-a1.ts`) are dropped when a real playlist exists on the same host; duplicate URLs are collapsed.

### 🧪 5. Diagnostics Reliability: "Broken Plugin" vs "Missing App ABI"
- **Host ABI probe:** `KSerializer`, `Json`, `SerialDescriptor`, `Gson`, `ObjectMapper`, `OkHttpClient`, `Jsoup`, `MainAPI`, `NiceResponse` are probed at runtime; if any is missing, the report gains an **“⚠️ Host ABI Missing (APP SIDE)”** section and the diagnostics screen shows a red warning.
- Linkage errors now carry evidence in their message: `[EKSİK HOST ABI: kotlinx.serialization.KSerializer]` or `metot imzası: (...)`.
- Report rows now attribute the failure: `🧩 Host ABI missing … (APP side)` vs `🧩 Runtime incompatible (plugin side)`.
