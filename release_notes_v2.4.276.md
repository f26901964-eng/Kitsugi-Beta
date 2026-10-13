# Kitsugi-Beta v2.4.276 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe (v2.4.276)

### 🖼️ Ana Sayfa Vitrini: Dikey Modda Poster Artık Ekrana TAM Oturuyor
- **Kök Neden Giderildi:** Vitrin bandının yüksekliğini gösterilen görselin oranına
  kilitleyen `homeHeroHeightDp` dikey dalı yazılmış ama **hiç çağrılmıyordu**
  (`KitsugiHeroSection` dikey modda doğrudan sabit `layout.heroHeight` kullanıyordu).
  Bu yüzden "otomatik vitrin" yalnızca yatay modda çalışıyor, dikey modda 2:3 bir
  poster 360×800 ekranda yalnızca ~257 dp genişliğinde — yani ekranın ~%71'i
  kadar — çiziliyor, kalanı iki yanda bulanık dolgu olarak kalıyordu.
- **Kutu Görsele Kilitlendi (Full-Bleed):** Dikey mod + dikey görselde bant yüksekliği
  artık `ekranGenişliği / görselOranı` → kutu oranı = görsel oranı. Sonuç: **kırpma
  ≈ %0**, **yan boşluk ≈ 0**; poster kenardan kenağa tek katman olarak çizilir.
- **Yeni Tavan (%74):** `HOME_HERO_PORTRAIT_VERTICAL_MAX_SCREEN_FRACTION`; 19.5:9 ve
  daha dar tüm telefonlarda 2:3 poster birebir sığar (20:9 → ekranın ~%68'i).
  Tavan aşılırsa hiçbir şey kırpılmaz: eski "bulanık dolgu + ortada kesilmemiş görsel"
  (contain) sunumu birebir korunur.
- **Yatay Görselde Davranış Aynı:** Dikey modda 16:9 key visual gösterilirken sabit
  bant korunur; başlık, rozetler, meta ve gösterge çubuğu okunur kalır.
- **Sarsıntı / Titreme Önlemleri:** Beklenen oran (poster 2:3 / arka plan 16:9) ilk
  kareden kullanılıyor; %5 ölçüm eşiği korundu; full-bleed kararı animasyonlu değil
  **hedef** yükseklikten okunuyor; 404 alan bir adaydan sonraki adaya geçişte ölçülen
  oran sıfırlanıyor (bant eski poster oranına kilitli kalmaz).
- **Yatay mod, tablet ve TV birebir korundu.**

---

## 🇬🇧 English (v2.4.276)

### 🖼️ Home Hero: Portrait Posters Now Fit the Screen Edge to Edge
- **Root Cause Eliminated:** The portrait branch of `homeHeroHeightDp` (which locks the
  hero band to the displayed artwork's aspect ratio) was written but **never called** —
  `KitsugiHeroSection` used the fixed `layout.heroHeight` in portrait. The "auto-sizing
  showcase" therefore only worked in landscape; in portrait a 2:3 poster rendered just
  ~257 dp wide on a 360×800 screen (~71% of the width), with blurred filler on both sides.
- **Box Locked to the Artwork (Full-Bleed):** In portrait with a vertical artwork the band
  height is now `screenWidth / imageAspect` → box aspect equals image aspect, so
  **crop ≈ 0%** and **side gaps ≈ 0%**. The poster is drawn edge to edge in a single layer.
- **New Ceiling (74%):** `HOME_HERO_PORTRAIT_VERTICAL_MAX_SCREEN_FRACTION` — a 2:3 poster
  fits exactly on every 19.5:9-or-narrower phone (20:9 → ~68% of screen height). When the
  ceiling is exceeded nothing is cropped: the legacy "atmospheric filler + uncropped
  centered artwork" (contain) presentation is preserved bit for bit.
- **Horizontal Artwork Unchanged:** With a 16:9 key visual in portrait the legacy fixed
  band is kept so the title, badges, meta row and page indicator stay legible.
- **Jitter Guards:** Expected aspect (poster 2:3 / backdrop 16:9) is used from the first
  frame; the 5% measurement threshold stays; the full-bleed decision reads the **target**
  (not animated) height so the layer mode cannot flip during the 280 ms animation; when a
  candidate 404s the stale measurement is cleared before advancing to the next candidate.
- **Landscape, tablet and TV behaviour is preserved exactly.**

---

## 🧪 Doğrulama / Verification

- Yeni birim testleri (`HeroImageSelectionTest`):
  - `portrait poster band fills the screen edge to edge on modern phones`
  - `portrait poster falls back to cinematic contain when it cannot fit the screen`
  - `portrait horizontal backdrop keeps the legacy fixed band`
  - `full bleed detection accepts only boxes that match the artwork`
- Detaylı analiz: `PLAN_TASK_VITRIN_DIKEY_POSTER_TAM_OTURMA_2026-10-13.md`
