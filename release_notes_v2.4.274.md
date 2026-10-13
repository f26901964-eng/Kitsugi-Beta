# Kitsugi-Beta v2.4.274 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe (v2.4.274)

### 📊 Grafikler Sekmesi Yeniden Tasarlandı (StatsTab)
- **Tek Kart Sıralama:** AniList sıralamaları ("Popülerlik" ve "Puan") ince ayraçlı tek bir kompakt kartta toplandı. Sıra numaraları (#13) popülerlik için kırmızı, puan için turuncu tonlarında renkli rozetlerle belirginleştirildi.
- **Dengeli Durum Dağılımı:** Sağdan taşan neon kapsül butonlar kaldırıldı. Yerine 12 dp yüksekliğinde akıcı yığın gösterge çubuğu ve ekranı saran lejant çipleri (renk noktası + durum adı + adet + yüzde) eklendi; taşma ve kaydırma sorunları tamamen çözüldü.
- **Uyumlu Renk Paleti:** AniList'in çiğ renkleri yerine uygulamanın rafine paleti kullanıldı (İzleniyor `#34D399`, Tamamlanan `#60A5FA`, Planlanan `#818CF8`, Bırakılan `#FB7185`, Ara verilen `#94A3B8`).
- **Tema Uyumlu Puan Dağılımı:** Sabit gökkuşağı kaldırıldı; sütunlar aktif tema vurgusundan türetilerek en yüksek puan tam vurgu renginde, diğerleri koyudan açığa ölçeklendirildi (gradyan temalarla tam uyumlu). 10 eşit kolon kartı dengeli paylaşır; başlıkta toplam oy ve ortalama puan (ör. 725.516 oy · Ort. 81,5) ve TR yüzde formatı (%81,2) sunulur.
- **Sıfır Zıplama İskeleti:** `StatsTabLoadingSkeleton` yeni kart ve çubuk geometrisine tam uyarlandı; yükleme tamamlandığında düzen zıplamaz.

### 🌐 Fandom Evren Rehberi: "Devamını Göster" & Gerçek Kategori Sayfalaması
- **Kompakt Başlangıç & Sayfalama:** Her rehber grubu (Mekânlar, Örgütler, Kavramlar...) katlanmış halde ilk 8 çipi gösterir. "Devamını göster (+6)" tıklandığında hem gizli çipler açılır hem de Fandom API'sinden `cmcontinue` jetonuyla bir sonraki 14'lük sayfa arka planda gerçek zamanlı indirilir.
- **Yüzlerce Çipe Erişim:** Re:Zero gibi devasa evren wikilerinde yüzlerce kavram ve mekân çipine kesintisiz erişim sağlandı.
- **Akıllı Durum Yönetimi & TV Odağı:** Çipler indirilirken buton spinner ve "Yükleniyor…" durumuna geçer; hepsi yüklendiğinde "Daha az göster"e döner. Ağ hatasında mevcut çipler korunarak "Tekrar dene" ipucu sunulur. Çipler ve butonlar Android TV kumanda odağı ile tam uyumludur.
- **Sürümlü Önbellek (`guide:v2:`):** Yeni devam jetonlarını desteklemek için rehber önbellek anahtarı `guide:v2:` olarak güncellendi.

---

## 🇬🇧 English (v2.4.274)

### 📊 Redesigned Stats & Analytics Tab (StatsTab)
- **Unified Rankings Card:** Consolidated AniList popularity and score placements into a single card with subtle dividers. Rank indices (#13) are highlighted with distinctive color badges (red for popularity, orange for score).
- **Proportional Status Distribution:** Replaced overflowing neon pills with a 12dp stacked bar chart paired with wrapping status legend chips (color dot + status name + count + percentage), eliminating horizontal overflow and text clipping.
- **Harmonious Palette:** Replaced harsh upstream colors with Kitsugi's refined palette (Current `#34D399`, Completed `#60A5FA`, Planning `#818CF8`, Dropped `#FB7185`, Paused `#94A3B8`).
- **Theme-Aware Score Distribution:** Removed fixed rainbow styling; score bars now dynamically derive from the active accent color (gradient themes fully supported). Card displays total votes and average score with localized formatting.
- **Zero-Jitter Skeleton:** Synchronized `StatsTabLoadingSkeleton` with the new layout geometry to eliminate layout shifts when data finishes loading.

### 🌐 Fandom World Guide: "Show More" & Real Category Pagination
- **Collapsible Groups & Real Pagination:** Guide groups (Locations, Organizations, Lore...) start collapsed showing the first 8 chips with a "Show more (+count)" action chip. Clicking expands hidden items and fetches the next 14 entries via MediaWiki `cmcontinue` tokens.
- **Deep Lore Coverage:** Large wikis (e.g. Re:Zero) can now paginate through hundreds of categorized topic chips seamlessly.
- **Graceful States & TV Focus Support:** Features inline progress spinners during fetches, switches to "Show less" when fully expanded, and preserves loaded chips with retry hints upon network failure. Fully navigable via Android TV D-pad controllers.
- **Versioned Cache Key (`guide:v2:`):** Upgraded the guide cache key to `guide:v2:` to invalidate legacy tokenless caches cleanly.
