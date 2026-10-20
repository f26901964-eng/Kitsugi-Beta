# Kitsugi Beta v2.4.319

## 🇹🇷 Türkçe

### 🎬 Görüntü Modu ("Doldur" / "Yakınlaştır") Alt Yazıyı Artık Etkilemiyor

Kullanıcı raporu: "Doldur" seçildiğinde harici altyazılar video ile birlikte esniyor, büyüyor ve ekranın altına taşıyordu. Kök neden, görüntü modunun altyazının çizildiği katmanı da ölçeklemesiydi. Bu sürümde alt yazı katmanı video ölçeklendirmesinden tamamen ayrıldı.

- **MPV motoru (varsayılan):**
  - Görüntü modu artık `View.scaleX/scaleY` ile değil, doğrudan mpv'nin `video-scale-x/y` özellikleriyle uygulanıyor. Böylece yalnızca **video** katmanı esner/zoomlanır.
  - Video pencereyi taşıdığında (`Doldur`, `Yakınlaştır`, `16:9`, `4:3`) altyazı çizim alanı pencereye sabitleniyor (`sub-use-margins` / `sub-ass-force-margins` kapatılır). Bu durumda libass'ın negatif kenar boşluğu hesaplayıp altyazıyı ekran dışına yerleştirmesi engellenir.
  - Altyazı yazı boyutu pencereye göre ölçekleniyor (`sub-scale-by-window`), esnetilen görüntüde yazı büyümüyor.
  - Video en-boy oranı artık kaynak videodan (`video-params/*`) okunuyor; çıkış katmanından okuma "Doldur" modunda ölçeğin kendi kendini büyütmesine yol açabiliyordu.
  - `video-aspect-override` için geçersiz olan `yes` değeri kaldırıldı (mpv bu değeri reddediyordu).
- **Media3 motoru:**
  - Görüntü modu her değiştiğinde altyazı görünümünün içerik çerçevesinden (`exo_content_frame`) ayrık kaldığı doğrulanıp yeniden sabitleniyor ve konumu tazeleniyor.
- **Sonuç:** Altyazı konumu, genişliği ve yazı boyutu hangi görüntü modu seçilirse seçilsin sabit kalır; alt satırlar ekran dışına taşmaz.

---

## 🇬🇧 English

### 🎬 Aspect Modes ("Fill" / "Zoom") No Longer Affect Subtitles

User report: after selecting "Fill", external subtitles stretched along with the video, grew larger and slid off the bottom of the screen. Root cause: the aspect mode scaled the very layer the subtitles are drawn on. This release fully decouples the subtitle layer from video scaling.

- **MPV engine (default):**
  - Aspect modes are now applied with mpv's own `video-scale-x/y` properties instead of `View.scaleX/scaleY`, so **only the video layer** is stretched/zoomed.
  - When the video overflows the window (`Fill`, `Zoom`, `16:9`, `4:3`), the subtitle drawing area is pinned to the window (`sub-use-margins` / `sub-ass-force-margins` disabled). This stops libass from computing negative margins and placing subtitles off-screen.
  - Subtitle font size scales with the window (`sub-scale-by-window`), so text does not grow with the stretched image.
  - Video aspect ratio is now read from the source (`video-params/*`); reading it from the output layer could make the scale feed back on itself in "Fill" mode.
  - Removed the invalid `yes` value for `video-aspect-override` (mpv rejected it).
- **Media3 engine:**
  - Every aspect-mode change re-verifies that the subtitle view is detached from `exo_content_frame` and refreshes its placement.
- **Result:** Subtitle position, width and font size stay fixed regardless of the selected aspect mode; no more subtitles falling off the bottom of the screen.
