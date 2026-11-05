# Kitsugi Beta v2.4.342

## 🇹🇷 Türkçe

### 📖 Çift Sayfa (2-Up) Modu — Tablet & Yatay Ekran
Poster gibi dikine uzun sayfalar artık yatay ekranda **ikişer yan yana** okunabiliyor. Mantık Mihon/TachiyomiSY'deki *dual page* davranışından uyarlandı ve bizim okuyucuya göre yeniden kuruldu.

**Nasıl açılır?**
- Okuyucu alt barında **yeni buton** (çift sütun ikonu): tek dokunuş açar/kapatır, açıkken mor renkte görünür.
- Okuyucu ayarlarında **Çift sayfa** bölümü: `Kapalı` / `Her zaman` / `Geniş ekranda`.
- Seçim **seriye özel** hatırlanır (okuma moduyla aynı desen) ve genel varsayılan olarak da saklanır.

**Neden "güzel bir mantık"?**
- **Taşma yok:** Ekran iki eşit sütuna bölünür, her sayfa kendi sütununa `Fit` ile oturur. Telefon, tablet, küçük/büyük ekran — hangisi olursa olsun görsel ekran dışına çıkmaz.
- **Sıralama farketmez:** Sayfalar önce **akıllı eşleştirilir** (`MangaPagePairing.smartSpreads`): yalnızca **dikine** iki sayfa yan yana gelir; araya giren **geniş / çift sayfa tarama** tek başına tam genişlikte gösterilir. Böylece sayfa sırası hiçbir durumda bozulmaz, tek kalan sayfa (kapak/son sayfa) yalnız kalır.
- **Kaydırma yönü farketmez:** RTL manga'da çiftin ilk sayfası **sağ** sütuna, kaydırma sağdan sola; LTR'de soldan sağa. Dikey kaydırma/webtoon etkilenmez.
- **Yatay/geniş sayfalar** asla ikiye sıkıştırılmaz — tek başına, kırpılmadan gösterilir.
- **Jestler aynı:** Her sayfa mevcut `MangaPageItem` ile çizildiği için **zoom (çift dokunma/pinch), dokunma alanları, iki parmakla parlaklık, uzun basış araçları** birebir korunur.
- **İlerleme doğru:** Pager konumu çift indeksi ile gerçek sayfa indeksi arasında eşlenir; kayıtlı ilerleme, "sayfaya git", kaydırıcı ve bölüm geçişi tek sayfa moduyla **aynı** çalışır. Kaldığın yerden açtığında doğru çift gelir.
- **Webtoon uyumu:** Sürekli dikey akışta çift sayfa anlamsız olduğu için mod otomatik kapalı kalır (buton soluk görünür).

### ✨ Yanında
- Sayfa en-boy oranları sayfa indirilirken bir kez okunur (bellek dostu, tam çözme yapılmaz) ve eşleştirme buna göre anında güncellenir.
- `MangaPagePairingTest`: dikine/geniş eşleştirme, tek kalan sayfa, RTL gösterim sırası, sayfa→çift dönüşümü ve ekran eşiği testleri eklendi.

---

## 🇬🇧 English

### 📖 Dual Page (2-Up) Mode — Tablets & Landscape
Portrait, poster-like pages can now be read **two side by side** in landscape. The logic is adapted from Mihon/TachiyomiSY's *dual page* behaviour and rebuilt for our reader.

**How to enable:** the new **bottom-bar button** (two-column icon) toggles it instantly and turns purple when active; or use **Dual page** in reader settings (`Off` / `Always` / `On wide screens`). The choice is remembered **per series**.

**Why the logic is "smart":**
- **No overflow:** the screen splits into two equal columns and each page uses `Fit`, so images never leave the viewport on any screen size.
- **Order-independent:** pages are matched by `MangaPagePairing.smartSpreads` — only **portrait** pages pair up; a **wide / double-page scan** stays alone at full width, so reading order is never broken and a leftover page stays single.
- **Direction-independent:** RTL puts the first page in the **right** column and scrolls right-to-left; LTR mirrors it. Vertical scrolling is untouched.
- **Gestures unchanged:** pages still render through `MangaPageItem` (zoom, tap zones, two-finger brightness, long-press tools).
- **Progress stays correct:** pager position is mapped to real page indices, so resume, jump-to-page, slider and chapter switching behave exactly like single-page mode.
- **Webtoon-safe:** dual page is disabled in continuous vertical scrolling.
