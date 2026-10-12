# Kitsugi-Beta v2.4.267 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🛡️ RenderThread (libhwui.so) SIGSEGV ve Bellek İyileştirmeleri
- **GPU RenderNode Yarış Önlemi:** `AsyncImage` üzerinde `onImageAspectResolved` ve `onLoadingFailed` durum güncellemeleri, çizim evresinde RenderNode yıkılmasını/yeniden oluşmasını önlemek amacıyla `postAfterFrame` ile bir sonraki ana iş parçacığı karesine ertelendi.
- **Sorunlu OEM Cihaz Koruması:** Xiaomi/Redmi/POCO (MIUI/HyperOS), Realme, OPPO/OnePlus (ColorOS), Vivo/iQOO (OriginOS/FunTouch), Meizu, Honor, Infinix ve düşük bellekli (<256MB) cihazlarda `libhwui.so` çökmesini önlemek için GPU RenderEffect bulanıklığı otomatik olarak devre dışı bırakılıp optimize edilmiş yazılımsal bitmap stack-blur'a geçirildi. Güvenli azami bulanıklık yarıçapı 12dp olarak sınırlandırıldı.
- **Büyük Görsel Bellek Optimizasyonu:** `BlurTransformation` yüksek çözünürlüklü posterlerde 2×/4×/6× otomatik ölçek küçültme (scale-down) ve ara bitmap temizliği (recycle) ile bellek yükünü ~%80 oranında düşürdü.
- **Donanım Çizici Güvenli Tahliyesi:** Activity sonlanırken (ekran döndürme, dil değişimi veya arka plana atılma anında) bekleyen GPU çizim komutlarının katmanları yıkmadan önce tamamlanmasını sağlayan `safeFreeHardwareRenderer` reflektif koruması devreye alındı.

### 🌟 MyAnimeList Profil Genişletmesi & Oturum İyileştirmeleri
- **Zenginleştirilmiş Profil Ekranı:** MAL kullanıcı profili; Hakkında sekmesi ve harici bağlantılar, etkinlik ve geçmiş akışı, anime/manga ayrıntılı istatistikleri ve durum dağılımı, puan dağılımı, kulüpler, incelemeler, öneriler ve sayfalama destekli arkadaş listesi ile donatıldı.
- **Bildirimler İçin Ayarlar Yönlendirmesi:** Bildirim ekranında MAL web oturumu eksik veya uyuşmadığında doğrudan Ayarlar → MyAnimeList bölümüne yönlendirme eklendi; eski ayrı giriş penceresi kaldırıldı.
- **Yeniden Bağlan / Oturumu Tamamla Eylemi:** MAL ayarlarına, bağlı hesabı koparmadan yalnızca web oturum çerezlerini yenileyip yetkilendirmeyi tazeleyen bağımsız "Yeniden Bağlan" seçeneği eklendi.

---

## 🇬🇧 English

### 🛡️ RenderThread (libhwui.so) SIGSEGV & Memory Optimizations
- **GPU RenderNode Race Prevention:** Deferred `AsyncImage` aspect and failure callbacks using `postAfterFrame`, preventing state mutations during the draw phase from triggering RenderNode tear-down races in `libhwui.so`.
- **Targeted OEM ROM Workaround:** Automatically disables GPU RenderEffect blur and falls back to optimized bitmap stack-blur on known problematic Android OEM builds (MIUI/HyperOS, ColorOS, OriginOS, RealmeUI, etc.) and low-RAM devices (<256MB heap), clamping max safe blur radius to 12dp.
- **Bitmap Blur Allocation Reduction:** Added automatic 2×/4×/6× scale-down and intermediate bitmap recycling to `BlurTransformation`, reducing GC pressure by ~80% on high-res artworks.
- **HardwareRenderer Drain on Teardown:** Added reflective `safeFreeHardwareRenderer` inside `onActivityDestroyed` to drain pending GPU fences before layers are destroyed during rotations, theme changes, or activity exits.

### 🌟 MyAnimeList Profile Expansion & Unified Session Enhancements
- **Enriched Profile Interface:** MAL user profiles now feature external links, activities and history, full anime/manga status and score distributions, clubs, reviews, recommendations, and paginated friend lists.
- **Seamless Notification Reconnect Flow:** Notification views now route directly to Settings → MyAnimeList when the session is missing or mismatched, removing legacy dialog prompts.
- **Session Refresh Action:** Added a dedicated "Complete / Reconnect MAL Session" action in settings to refresh website cookies without unlinking the OAuth account.
