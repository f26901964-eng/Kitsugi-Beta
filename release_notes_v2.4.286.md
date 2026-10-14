# Kitsugi Beta v2.4.286

## 🇹🇷 Türkçe

### 1. Wikipedia (Vikipedi) & Wikidata 2. Açık Kaynak Ansiklopedi Entegrasyonu
- **Asya Dizileri ve TV Şovları İçin Wikipedia Desteği:** Fandom'da bağımsız sayfası bulunmayan Asya dizileri (K-Drama, C-Drama, J-Drama, Tayland yapımları), TV programları, Türk dizileri ve bağımsız filmler için ikinci açık kaynaklı ansiklopedi kaynağı olarak Wikipedia (Vikipedi) sisteme entegre edildi.
- **Wikidata Sitelinks ile Anında ve Kesin Eşleşme:** TMDb, IMDb veya AniList/MAL kimliğine sahip tüm yapımlar (*Crash Landing on You, Squid Game, Queen of Tears, The Glory, Itaewon Class, Goblin* vb.) Wikidata'nın doğrudan sitelinks verileri üzerinden sıfır gecikmeyle Türkçe (`trwiki`) ve İngilizce (`enwiki`) Wikipedia sayfalarına bağlandı.
- **Yedek Wikipedia Arama Motoru (`WikipediaResolver`):** Wikidata bağlantısı bulunmayan yapımlar için dizi, sezon yılı ve format varyantlarıyla (`(TV series)`, `(South Korean TV series)`, `(film)`, `(drama)` vb.) MediaWiki arama ve akıllı benzerlik doğrulaması eklendi.
- **Wikipedia Bilgi Kutusu & Giriş Ayrıştırıcı:** Standart Wikipedia HTML bilgi kutusu tabloları (`table.infobox`, `table.vevent`), yayın kanalı (tvN, JTBC, Netflix vb.), bölüm sayısı, yönetmen, senarist, orijinal ad (Hangul, Hanja, Kanji) ve afiş görselleri doğrudan tanınıp bilgi kutusu kartlarına dönüştürüldü.
- **Arayüzde Çift Kaynak Çipleri (`[ 🌐 Fandom Wiki ] [ 📖 Wikipedia ]`):** Fandom önceliği %100 korundu. Her iki kaynağı da bulunan yapımlarda başlık kartında şık geçiş butonları yer alıyor; tek dokunuşla kaynaklar arasında geçiş yapılabiliyor. Tek kaynak olduğunda ise kaynağın adı ve açık ansiklopedi alt başlığı gösteriliyor.
- **Tam Uyumluluk:** Google Translate (Türkçe Çeviri), İçindekiler Tablosu ve Sayfa İçi Wiki Okuyucu (`FandomWikiReader`) tüm kaynaklarla kusursuz çalışıyor.

### 2. Fandom Wiki Tam Sayfa / Ana Sayfa Yükleme & Gelişmiş Dönüştürücü
- **Ana Sayfa ve Giriş Bölümü (Section 0) Desteği:** Ana sayfaların hero alanı, öne çıkan makale, video oyunu/film/animasyon kartları, Featured Characters, Contents kutuları, Calendar, Did You Know gibi modüllerinin yüklenmeme ve sayfanın boş görünme sorunu giderildi; giriş bölümü (section 0) açılışta otomatik yükleniyor.
- **Kartlar ve Bağlantı Grupları (CARD & LINK_GROUP):** Görselli hücreler zengin kartlar (başlık, alt satır, kapak görseli, hedef) olarak; navbox ve bağlantı kümeleri ise tüm bağlantılarıyla derli toplu bağlantı grupları halinde korunuyor.
- **Tıklanabilir Satır İçi Bağlantılar:** Paragraf ve liste maddelerindeki tüm makale bağlantıları tıklanabilir hale getirildi. Bağlantılı görseller doğrudan hedef sayfayı açıyor. Aynı wiki makaleleri okuyucu içinde yeni sekmede açılırken; şablon, kategori, dosya ve harici wiki bağlantıları sistem tarayıcısına yönlendiriliyor.
- **"Madde Bulunamadı" Kök Neden Çözümü:** Düz arama başlıklarının beklenmedik bir medyaya çözülmesi (ör. düz "Resident Evil" başlığının 2002 oyununa çözülmesi nedeniyle 2026 dizisinin bulunamaması) önlendi. Yayın yılı biliniyorsa önce `Resident Evil (2026)` gibi yıllı başlıklar deneniyor. Eski negatif önbellek `v5`'e yükseltilerek temizlendi. Ayrıca bulunamayan makale kartına "Wiki ana sayfasını aç" kısayolu eklendi.
- **Genişletilmiş Sınırlar:** Büyük wiki ana sayfalarının eksiksiz işlenmesi için blok ve görsel limitleri yükseltildi (`MAX_BLOCKS = 4000`, `MAX_SECTION_IMAGES = 300`).

### 3. Wiki Özetlerinde "Daha Fazla / Daha Az" Daraltma & Genişletme
- **Detay Sayfası Açıklama Standardı:** Wiki özet kartlarına detay sayfasındaki açıklama standardı getirildi (`WikiCollapsibleText`).
- **110 dp Yumuşak Gradyan:** Karakter/ekip wiki özeti, medya wiki özeti ve seçili wiki sayfası özet kartında 200 karakter veya 4 satırdan uzun metinler 110 dp'de yumuşak geçişle sınırlandırılıyor.
- **Genişletme Butonu & Çeviri Koruması:** "Daha fazla / Daha az" butonuyla metin akıcı bir şekilde açılıp kapanabiliyor. Orijinal metin ile çeviri arasında geçiş yapıldığında açık/kapalı durumu korunuyor.

### 4. Listem Ekranı Profil Tarzı Sade Alt Paneller
- **Sade Tasarıma Geçiş:** Listem'deki Kategori ve Filtrele & Sırala açılır panelleri, profildeki minimalist ve şık alt panel bileşenine (`KitsugiSheetOrDialog`) uyarlandı.
- **Modern ve Yalın Görünüm:** Yoğun gradyanlar ve parlak çerçeveler yerine sade satırlar, yumuşak seçim dolgusu (%15 vurgu rengi) ve profilin sürükleme çubuğu kullanıldı.
- **Dikey Kaydırma & Akıcı Kapanış:** Panellere dikey kaydırma eklendi. Kategori seçimi ve "Bitti" eylemleri `LocalDismissAnimated` ile yumuşak animasyon eşliğinde kapanıyor.

---

## 🇬🇧 English

### 1. Wikipedia & Wikidata 2nd Open-Source Encyclopedia Integration
- **Wikipedia Support for Asian Dramas & TV Shows:** Integrated Wikipedia as the second open encyclopedia source for K-Dramas, C-Dramas, J-Dramas, Thai series, TV shows, and standalone movies that lack independent Fandom wikis.
- **Instant Wikidata Sitelinks Matching:** Media with TMDb, IMDb, or AniList/MAL IDs (*Crash Landing on You, Squid Game, Queen of Tears, The Glory, Itaewon Class, Goblin*, etc.) are accurately linked to Turkish (`trwiki`) and English (`enwiki`) Wikipedia articles via Wikidata sitelinks with zero latency.
- **Fallback Wikipedia Search (`WikipediaResolver`):** Smart title matching and disambiguation for standalone works using MediaWiki search queries with format qualifiers (`(TV series)`, `(South Korean TV series)`, `(film)`, `(drama)`).
- **Wikipedia Infobox & Lead Parsing:** Native parsing for Wikipedia HTML infoboxes (`table.infobox`, `table.vevent`), extracting broadcast networks (tvN, JTBC, Netflix), episode counts, directors, writers, original titles (Hangul, Hanja, Kanji), and posters into structured infobox cards.
- **Dual Source UI Chips (`[ 🌐 Fandom Wiki ] [ 📖 Wikipedia ]`):** Fandom priority is fully preserved. When both sources exist, interactive chips on the header card allow instant one-tap switching. Single-source items display clean encyclopedia branding.
- **Full Compatibility:** Google Translate (Turkish Translation), Table of Contents, and In-App Wiki Reader (`FandomWikiReader`) operate smoothly across all sources.

### 2. Fandom Wiki Full-Page & Home Page Rendering with Rich Converter
- **Home Page & Lead Section (Section 0) Support:** Resolved blank main/home page views (hero sections, featured articles, video game/movie/animation grids, featured characters, contents modules, calendar, Did You Know widgets). Lead Section 0 is now requested and rendered on launch.
- **Cards & Link Groups (CARD & LINK_GROUP):** Visual cells convert into interactive cards (title, subtitle, thumbnail, target); navboxes and link clusters are grouped cleanly, retaining all links.
- **Tappable Inline Links & Linked Images:** All links within paragraphs and bullet points are interactive. Linked images navigate directly to target articles. Same-wiki links open smoothly in new reader tabs, while external wikis, categories, files, and templates open in the system browser.
- **"Article Not Found" Disambiguation Fix:** Fixed general titles resolving to older entries (e.g., bare "Resident Evil" mapping to the 2002 game, hiding the 2026 series). Year-qualified titles such as `Resident Evil (2026)` are tried first when release year is known. Outdated negative cache bumped to `v5`. Added an "Open wiki home page" button.
- **Expanded Limits:** Section limits increased to parse large wiki home pages (`MAX_BLOCKS = 4000`, `MAX_SECTION_IMAGES = 300`).

### 3. Collapsible Wiki Summary Cards ("See More / See Less")
- **Standardized Detail Summary Parity:** Integrated the detail screen summary card standard into wiki summaries via `WikiCollapsibleText`.
- **110 dp Soft Gradient Fade:** Character/staff summaries, media summaries, and selected wiki sheet cards exceeding 200 chars or 4 line breaks are cleanly trimmed at 110 dp with a subtle surface fade.
- **Toggle & Translation Preservation:** Smoothly toggle between collapsed and expanded states with the "See more / See less" button. Expansion state is retained when switching between original and translated text.

### 4. "My List" Profile-Style Subdued Bottom Sheets
- **Profile Bottom Sheet Parity:** Category and Filter & Sort bottom sheets in "My List" now adopt the minimalist profile sheet design (`KitsugiSheetOrDialog`).
- **Clean Aesthetic:** Removed heavy glow borders and loud gradients in favor of refined list rows, gentle selection highlights (15% accent fill), and standard drag handle bars.
- **Vertical Scrolling & Animated Dismissal:** Added vertical scrolling to sheet contents. Category selections and the "Done" action dismiss gracefully using `LocalDismissAnimated`.
