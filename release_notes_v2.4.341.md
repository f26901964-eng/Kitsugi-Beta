# Kitsugi Beta v2.4.341

## 🇹🇷 Türkçe

### 🐞 Bölüm Listesi: İşaretlediğin ⇄ Butonu ve "1'den Başlayan" Sıralama Düzeltildi
- **⇄ düğmesi artık çalışıyor:** Bölümler başlığındaki ok ikonu daha önce yalnızca süstü (tıklanınca hiçbir şey olmuyordu). Artık **tek dokunuş** sıralamayı çevirir (En yeni ⇄ 1→137 ⇄ 137→1), **uzun basış** tüm sıralama seçeneklerini açar.
- **Sağlam bölüm numarası çözümü (`MangaChapterNumberResolver`):** "Bölüm 137 - Chu Chu…", "Bolum 107", "Böl.105", "Ch. 4", "Chapter 12.5" gibi adlar sayıya çevrilir. Eklenti numara vermezse (birçok Türkçe kaynakta `-1`) numara **addan** çıkarılır — Mihon'un `ChapterRecognition` mantığı + Türkçe ekler.
- **Numara rozeti:** Her satırın solunda sabit genişlikli sayı kutusu var; numara bilinmeyen "Duyuru" gibi kayıtlar kitap ikonuyla gösterilir.
- **Sıralama seçenekleri:** En yeni eklenen üstte / Numara 137→1 / Numara 1→137 / A–Z. Numarası bilinmeyen kayıtlar her iki yönde de en sonda kalır. "Baştan Oku" her zaman gerçekten en eski bölümü açar (ekran sırasından bağımsız).
- **Arama:** "Bölüm ara (ör. 42)" alanı artık gerçekten var; numara, başlık veya çeviri grubuna göre filtreler (ağ isteği yok).
- **Filtre çipleri:** Tümü / Okunmamış / Okunmuş / Ekstraları gizle + **çeviri grubu (scanlator) çipleri** ve okunmamış sayacı.
- **Tek dokunuşla okundu:** Satır sonundaki halkaya dokun → okundu; ✓ simgesine dokun → geri al. "Tümünü okundu yap" düğmesi eklendi.

### 📚 Manga Türleri + Light Novel Desteği (araştırma sonrası)
Açık kaynak okuyucular (Mihon, Kotatsu-Redo, TachiyomiSY, LNReader, Nyora, Uchiyomi) incelendi; eksik olanlar uygulandı:
- **Tür otomatik algılama (`MangaKindDetector`):** Kaynak adı + tür etiketleri + seri adından **Manga / Manhwa / Manhua / Webtoon / Light Novel** çıkarılır ve detay ekranında rozet olarak gösterilir.
- **Doğru mod, doğru içerik:** Manhwa → **dikey webtoon akışı**, Manhua → **soldan sağa**, Manga → sağdan sola. Seçim **seri bazında** kalıcıdır (Mihon "Viewer for this series").
- **Light Novel okuyucu (`NovelReaderScreen`):** Novel kaynağı algılanınca bölüm metin olarak açılır — reklam/menü temizlenir, paragraflar ayrılır. Yazı boyutu, satır aralığı, satır genişliği, **Gece / Sepya / Gündüz / Sistem** temaları, iki yana yaslama ve **RTL (Arapça/Farsça)** desteği. Kelime sayısı + tahmini okuma süresi ve kaydırma yüzdesi gösterilir. Tercihler kalıcıdır.
- **Novel kaynak listesi (`TurkishNovelSources`):** `ArazNovel`, `NovelTR` gibi kaynaklar tanınır; "Ön Söz / SSS / Yazarın Notu" bölümleri meta olarak işaretlenir.

### 📖 Okuyucu: Mihon Paritesi Jest ve Görüntü Ayarları
- **Dokunma alanları (tap zones):** Sol / orta / sağ için **Önceki · Sonraki · Menü · Hiçbir şey** seçilebilir; seçim okuma yönüne göre otomatik uyarlanır.
- **İki parmakla parlaklık:** İki parmakla yukarı/aşağı kaydırınca parlaklık değişir (tek parmak pager/zoom'da kalır, istenirse kapatılır).
- **Sayfaya git:** Sayfa numarasına dokun → numara gir, ışınlan.
- **Uzun basış araçları:** Sayfayı **JPG olarak Galeri'ye kaydet** (MediaStore, `Pictures/Kitsugi`) veya **paylaş**.
- **Ses tuşlarıyla sayfa çevirme** (+ ters çevirme), **okurken ekranı açık tut**, **tam ekran (immersive)**, **son sayfada sonraki bölüme geç**.
- **Görüntü işleme:** Kenar kırpma, geniş sayfaları bölme/döndürme, yan boşluk, sayfa numarası rozeti, **e-ink flaş**.
- **Webtoon sayfa aralığı** 0–48 dp arası ayarlanabilir; **ciltlere göre gruplama** uzun serilerde listeyi katlanabilir başlıklara böler.

## 🇬🇧 English

### 🐞 Chapter list: the ⇄ button now works, numbers start from 1
- The header arrow was decorative; it now **cycles sort order** on tap and opens the full sort sheet on long press.
- **Robust chapter number resolution** (Turkish "Bölüm 137" + English "Ch. 4" / "12.5"), fixed-width number badges, sort by newest / number asc-desc / A–Z, and "Read from start" always picks the true oldest chapter.
- **Local chapter search** ("e.g. 42"), filter chips (All / Unread / Read / Hide extras), **scanlator chips**, unread counter, one-tap mark read + "Mark all as read".

### 📚 Manga kinds + light novel support
- **Kind detection** (source + genres + title) with a badge: Manga / Manhwa / Manhua / Webtoon / Light Novel.
- **Right viewer per kind** and **per-series** persisted mode (manhwa → webtoon, manhua → LTR).
- **Novel reader**: sanitised HTML text extraction, font/line-height/width, 4 themes, justify, RTL, word count + reading time, progress percentage; preferences persisted.
- **Turkish novel source registry** (ArazNovel, NovelTR…) and meta-chapter detection (prologue, FAQ, author notes).

### 📖 Reader parity with Mihon
- Configurable **tap zones**, **two-finger brightness gesture**, **jump to page**, long-press **save to gallery / share**, **volume-key paging**, keep-screen-on, immersive fullscreen, auto next chapter.
- **Image processing:** crop borders, split/rotate wide pages, side padding, page-number badge, e-ink flash, webtoon page gap, **volume grouping**.
