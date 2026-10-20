# Kitsugi Beta v2.4.319

## 🇹🇷 Türkçe

### 🎬 Görüntü Modu ("Doldur" / "Yakınlaştır") Alt Yazıyı Artık Etkilemiyor

"Doldur" veya "Yakınlaştır" seçildiğinde harici altyazıların video ile birlikte esnemesi, büyümesi ve ekranın altına taşması sorunu kökten çözüldü. Alt yazı katmanı video ölçeklendirmesinden tamamen ayrıldı. Ayrıntılı analiz: `COZUM_RAPORU_ALT_YAZI_GORUNTU_MODU_2026-10-21.md`.

- **MPV motoru (varsayılan):**
  - Görüntü modu artık `View.scaleX/scaleY` ile değil, doğrudan mpv'nin `video-scale-x/y` özellikleriyle uygulanıyor; böylece yalnızca **video** katmanı esner/zoomlanır, altyazı katmanına dokunulmaz.
  - Video pencereyi taşıdığında (`Doldur`, `Yakınlaştır`, `16:9`, `4:3`) altyazı çizim alanı pencereye sabitleniyor (`sub-use-margins` / `sub-ass-force-margins` kapatılır). Bu sayede libass'ın negatif kenar boşluğu hesaplayıp altyazıyı ekran dışına taşırması engellenir.
  - Altyazı yazı boyutu pencereye göre ölçekleniyor (`sub-scale-by-window`), esnetilen görüntüde yazı boyutu bozulmuyor.
  - Video en-boy oranı artık kaynak videodan (`video-params/*`) okunuyor; çıkış katmanından okuma "Doldur" modunda ölçeğin kendi kendini büyütmesine yol açabiliyordu.
  - `video-aspect-override` için geçersiz olan `yes` değeri kaldırıldı.
- **Media3 motoru:**
  - Görüntü modu her değiştiğinde altyazı görünümünün içerik çerçevesinden (`exo_content_frame`) ayrık kaldığı doğrulanıp konumu yeniden sabitleniyor.
- **Sonuç:** Altyazı konumu, genişliği ve yazı boyutu hangi görüntü modu seçilirse seçilsin sabit kalır; alt satırlar ekran dışına taşmaz.

### 🔵 Wiki Okuyucuda Mavi (Bağlantı) Metinlerin Otomatik Çevirisi

Wiki açılır sayfalarında otomatik çevirinin **mavi/bağlantı metinlerini atlaması** yüzünden oluşan cümle bölünmeleri ve yarı Türkçe/İngilizce metin sorunları tek kökten çözüldü. Ayrıntılı analiz: `MAVI_BAGLANTI_OTOMATIK_CEVIRI_RAPORU_2026-10-21.md`.

- **Mavi metin artık çevriliyor, konumu birebir korunuyor:** Her bağlantı etiketi çeviriye gönderilmeden önce `⟦L0⟧Characters⟦/0⟧` biçiminde bir yuvaya alınır. Google cümleyi **tek parça** olarak çevirir; etiket tam olarak eski yerinde, cümlenin içinde kalır.
- **Dokunma hedefi (URL) hiç değişmiyor ve hiç gönderilmiyor:** Çeviri yalnızca `label` alanına dokunur; `url` alanı blok verisinde olduğu gibi kalır.
- **Kelimelerin birbirine yapışması çözüldü:** Cümle bağlantıların arasına parçalanıp ayrı ayrı çevrilmediği için "678 articlesve" gibi yapışmalar ortadan kalktı.
- **Çevrilmiş etiket metinle birlikte saklanıyor (`…:l<indeks>` çocuk anahtarı):** Çeviri haritası önbellekten/akıştan geri geldiğinde mavi alan tıklanabilirliğini asla kaybetmez.
- **Jeton bozulursa güvenli yedek:** Sağlayıcı bir yuvayı düşürür veya bozarsa metin eski güvenli yoldan (parça parça, etiketler orijinal) çevrilir; yarım jeton veya kaymış bağlantı ekranda görünmez.
- **Hız ve performans:** Eskiden her bağlantı arası ayrı istek atılıyordu (bir paragrafta 5-8 istek). Artık cümle ve tüm etiketler **tek istekte** gidiyor; 40 tekrarlı bir bağlantı listesi bile tek istek harcıyor.
- **Cümle görünümlü kart başlıkları:** Ana sayfa kartlarındaki cümle niteliğindeki başlıklar çevrilirken `href` korunur.
- **Görsel altı yazıları (figcaption):** Açıklamalardaki mavi bağlantılar da aynı yuvalı akıştan geçer.

### 🧪 Birim Testleri
- `WikiTranslationProtectionTest`: Mavi etiketlerin yerinde çevrilmesi, URL'lerin hiç değişmemesi, jeton bozulduğunda güvenli yedeğe düşülmesi, etiket anahtarlarının metinden önce akması, 40 tekrarlı bağlantıda tek istek, cümle görünümlü kart başlığının çevrilip kısa sayfa adının çevrilmemesi doğrulandı (34 test tamamı başarılı).

---

## 🇬🇧 English

### 🎬 Aspect Modes ("Fill" / "Zoom") No Longer Distort Subtitles

Resolves the issue where selecting "Fill" or "Zoom" stretched, oversized, and pushed subtitles off-screen. Subtitle rendering has been fully decoupled from video transformation layers. Detailed report in `COZUM_RAPORU_ALT_YAZI_GORUNTU_MODU_2026-10-21.md`.

- **MPV Engine (default):**
  - Aspect modes are now applied directly via MPV's `video-scale-x/y` properties instead of Android `View.scaleX/scaleY`, transforming only the video surface while leaving subtitles untouched.
  - Subtitle bounding is pinned to the window (`sub-use-margins` / `sub-ass-force-margins = no`) whenever the video overflows the display bounds (`Fill`, `Zoom`, `16:9`, `4:3`), preventing libass from calculating negative margins and clipping subtitles off the bottom edge.
  - Subtitle font size is anchored to the window (`sub-scale-by-window`), preventing stretched typography.
  - Video aspect ratio is read directly from container properties (`video-params/*`) rather than output dimensions to avoid recursive magnification.
  - Removed invalid `"yes"` argument from `video-aspect-override`.
- **Media3 Engine:**
  - Re-anchors and refreshes subtitle layout upon aspect mode changes to ensure subtitle views remain decoupled from the video content frame (`exo_content_frame`).
- **Result:** Subtitle placement, scale, and line wrapping remain invariant regardless of selected aspect mode.

### 🔵 Automatic Translation of Blue (Link) Text in the Wiki Reader

Fixes three separate breakages caused by auto-translation skipping **blue / linked text** inside wiki reader pages. Detailed report in `MAVI_BAGLANTI_OTOMATIK_CEVIRI_RAPORU_2026-10-21.md`.

- **Link labels are now translated in place:** Each label is wrapped in a `⟦L0⟧Characters⟦/0⟧` slot before being sent, so the provider translates the whole sentence at once and the label stays exactly where it was.
- **Tap targets (URLs) never change and are never sent:** Only `label` is touched; `url` stays in the block data, so tapping a blue word opens exactly the same page as before.
- **Resolved word agglutination:** Sentences are translated holistically rather than fragmented around links, avoiding awkward spacing and gluing ("678 articlesand").
- **Translated labels are persisted next to the text (`…:l<index>` child keys):** Text and link list always agree after a cache/stream round trip; the blue area never loses its click target.
- **Safe fallback when slots are damaged:** If the provider drops a slot marker, the block falls back to the old per-segment path with original labels.
- **Fewer requests, not more:** One request per block (sentence + labels) instead of one per segment; even a paragraph with 40 repeated links costs a single request.
- **Sentence-like linked card titles are translated:** Fandom main-page card sentences are translated while `href` is preserved; short page titles stay untouched.
- **Figure captions (figcaption):** Blue link text in image captions is translated in place through the same slot pipeline.

### 🧪 Unit Tests
- `WikiTranslationProtectionTest`: Verified in-place label translation, invariant URLs, slot recovery fallback, pre-flight label key emissions, single request on 40 links, and sentence vs title card discrimination (all 34 tests passing).
