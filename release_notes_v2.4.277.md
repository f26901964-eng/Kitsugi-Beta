# Kitsugi-Beta v2.4.277 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe (v2.4.277)

### 🖼️ Ana Sayfa Vitrini: Dikey Modda Poster Artık Ekrana TAM Oturuyor
- **Kök Neden Giderildi:** Vitrin bandının yüksekliğini gösterilen görselin oranına kilitleyen `homeHeroHeightDp` dikey dalı yazılmış ama daha önce çağrılmıyordu (`KitsugiHeroSection` dikey modda sabit `layout.heroHeight` kullanıyordu). Bu sebeple "otomatik vitrin" yalnızca yatay modda çalışıyor, dikey modda 2:3 bir poster 360×800 ekranda yalnızca ~257 dp genişliğinde (~%71) çiziliyor, iki yanda bulanık dolgu boşluğu kalıyordu.
- **Kutu Görsele Kilitlendi (Full-Bleed):** Dikey mod + dikey görselde bant yüksekliği artık `ekranGenişliği / görselOranı` olarak hesaplanıyor → kutu oranı görsel oranıyla birebir eşitleniyor. Sonuç: **kırpma ≈ %0**, **yan boşluk ≈ 0**; poster kenardan kenağa tek katman olarak çizilir.
- **Yeni Tavan (%74):** `HOME_HERO_PORTRAIT_VERTICAL_MAX_SCREEN_FRACTION` (0.74); 19.5:9 ve daha dar tüm telefonlarda 2:3 poster ekrana tam oturur (20:9 telefonda ekran yüksekliğinin ~%68'i).
- **Tavan Aşılırsa Koruma (Contain):** Kare veya tablet oranlı cihazlarda (veya aşırı uzun posterlerde) tavan aşılırsa hiçbir şey kırpılmaz; eski "bulanık dolgu + ortada kesilmemiş görsel" (contain) çift katman sunumu güvenle korunur.
- **Yatay Görselde Davranış Değişmez:** Dikey modda 16:9 key visual gösterilirken sabit bant korunur; başlık, rozetler, meta ve gösterge çubuğu okunur kalır.
- **Sarsıntı ve Titreme Önlemleri:**
  - Beklenen oran (poster 2:3 / arka plan 16:9) ilk kareden doğru kullanılıyor.
  - %5 ölçüm eşiği korunuyor (ufak ölçüm farklarında bant zıplamaz).
  - Full-bleed kararı animasyonlu yükseklikten değil **hedef** yükseklikten okunuyor; geçiş esnasında katman modu titreme yapmaz.
  - 404 alan bir adaydan sonraki adaya geçerken ölçülen oran sıfırlanıyor.
- **Yatay mod, tablet ve TV sunumu birebir korundu.**

---

## 🇬🇧 English (v2.4.277)

### 🖼️ Home Hero: Portrait Posters Now Fit the Screen Edge to Edge
- **Root Cause Eliminated:** The portrait branch of `homeHeroHeightDp` (locking the hero band to the artwork's aspect ratio) was defined but previously bypassed (`KitsugiHeroSection` used fixed `layout.heroHeight` in portrait). As a result, the dynamic showcase only functioned in landscape; in portrait, a 2:3 poster rendered only ~257 dp wide on a 360×800 screen (~71% width), leaving blurred padding on both sides.
- **Box Locked to Artwork (Full-Bleed):** In portrait mode with vertical artwork, hero band height is now dynamically calculated as `screenWidth / imageAspect` → box aspect matches image aspect exactly. Results: **crop ≈ 0%**, **side gaps ≈ 0%**; poster renders edge-to-edge as a single layer.
- **New Ceiling (74%):** `HOME_HERO_PORTRAIT_VERTICAL_MAX_SCREEN_FRACTION` (0.74); a 2:3 poster fits completely on all 19.5:9 or narrower phones (~68% screen height on 20:9).
- **Graceful Contain Fallback:** On square or tablet displays where the ceiling would be exceeded, artwork is never cropped; the classic dual-layer (atmospheric background + centered uncropped poster) is cleanly preserved.
- **Horizontal Artwork Preserved:** When displaying a 16:9 backdrop in portrait mode, the responsive fixed band is kept so title, badges, meta details, and indicator dots remain legible.
- **Jitter & Flicker Guards:**
  - Expected aspect ratio (2:3 for poster, 16:9 for backdrop) is applied from the very first frame.
  - 5% ratio threshold prevents band jumping on negligible size differences.
  - Full-bleed decision evaluates target height rather than the animating height, avoiding mid-transition flicker.
  - Advance to next candidate clears stale aspect ratio if an image candidate 404s.
- **Landscape, tablet, and Android TV behaviors remain fully intact.**

---

## 🧪 Verification
- Unit test suite (`HeroImageSelectionTest`):
  - `portrait poster band fills the screen edge to edge on modern phones`
  - `portrait poster falls back to cinematic contain when it cannot fit the screen`
  - `portrait horizontal backdrop keeps the legacy fixed band`
  - `full bleed detection accepts only boxes that match the artwork`
- Comprehensive architecture review: `PLAN_TASK_VITRIN_DIKEY_POSTER_TAM_OTURMA_2026-10-13.md`
