# Kitsugi-Beta — Sürüm Notları / Release Notes

# Kitsugi Beta v2.4.287

## 🇹🇷 Türkçe

### Keşfet "Tümünü Gör" Sayfalarında Tür ve Sıralama Artık Kaynaktan Geliyor
- **Kök Neden Çözüldü:** "Filtre ve Sıralama" sayfasında seçilen tür/sıralama eskiden yalnızca cihazda uygulanıyordu (`displayedResults`). Kaydırarak gelen her yeni sayfa filtresiz çekildiği için liste boşalıyor, otomatik sayfalama durmadan boş istek atarak döngüye giriyor ve sıralama her sayfada yeniden karışıyordu.
- **Kaynak Tarafı Sorgular:** Yeni `ExploreSourceFilterQuery` motoru seçilen tür ve sıralamayı 7 kaynağın **kendi API parametrelerine** çevirir:
  - **AniList:** Resmî `genres` veya `tags` (kanonik: `Sci-Fi`, `Slice of Life`) + `MediaSort` (`SCORE_DESC`, `POPULARITY_DESC`, `START_DATE_DESC`, `TITLE_ROMAJI` vb.).
  - **MAL / Jikan:** `genres=<id>` + `order_by`/`sort` (türsüz durumlarda resmî MAL sıralama ucu; başlık/tarih Jikan liste ucuna yönlendirilir).
  - **TMDB:** `with_genres` (+ anime raflarında `16` ve `ja` filtresi) + `sort_by` (`vote_average.*`, `popularity.desc`, `primary_release_date` vb.).
  - **Kitsu:** `filter[categories]` slug formatında (`sci-fi`, `slice-of-life`) + `sort` (`-averageRating`, `-userCount`, `-startDate` vb.).
  - **Shikimori:** Tür kimliği + `order` (`ranked`, `popularity`, `aired_on`, `name`).
  - **Simkl:** Haritalanmış tür uç noktası + `sort` (`rank`, `popular-this-week`, `release-date`).
  - **Bangumi:** Yerel etiket sözlüğü (科幻, 恋爱, 机战…) + `rank`, `heat`, `score`.
- **Liste Baştan Kurulur:** Filtre uygulandığında mevcut liste temizlenir ve **1. sayfa doğrudan filtrelenmiş istekle** kaynaktan çekilir. Kaydırma başa alınır ve sonraki her sayfa aynı filtre/sıralamayı korur.
- **Uçuştaki İstek Koruması:** `filterGeneration` jetonu sayesinde arka planda geciken eski isteklerin yeni listeyi ezmesi önlenir.
- **Kaynak Desteklemiyorsa (☁️ ve 📱 Ayrımı):** Seçim hangi kaynakta API tarafından uygulanabiliyorsa **☁️**, kaynağın desteklemediği durumlarda (ör. Simkl'de Mecha/Müzik, Shikimori'de artan puan) **📱** ile işaretlenir ve güvenli bir şekilde cihazda uygulanır.
- **Hızlı Tür Çipleri:** Izgara ve liste üstündeki hızlı tür çipleri ile "Temizle" butonu da aynı kaynak tarafı sorgu yolunu kullanır.
- **Kişisel Listeler:** Simkl izleme listesi ve TMDB "İzlemeye Devam / Planladıklarım" rafları API'de filtrelenebilir katalog olmadığından cihazda filtrelenmeye devam eder.
- **Sonsuz Döngü Koruması:** `AUTO_LOAD_BUDGET_PER_SCROLL` ve "aynı görünür öğe sayısı" emniyetleri korundu; boş gelen ilk sayfa artık sayfalamayı kontrollü bir şekilde durdurur.

---

## 🇬🇧 English

### Genre & Sort on Explore "See All" Pages Now Handled Directly by Source APIs
- **Root Cause Resolved:** Previously, genre and sort options chosen in the "Filter & Sort" sheet were only evaluated locally on the device (`displayedResults`). As pages were scrolled, subsequent pages were fetched without filters, resulting in empty lists, infinite auto-pagination loops, and broken ordering.
- **Source-Side Query Engine:** The new `ExploreSourceFilterQuery` engine translates active genres and sorts directly into each source's native API parameters:
  - **AniList:** Canonical `genres` or `tags` (e.g. `Sci-Fi`, `Slice of Life`) + `MediaSort` (`SCORE_DESC`, `POPULARITY_DESC`, `START_DATE_DESC`, `TITLE_ROMAJI`, etc.).
  - **MAL / Jikan:** `genres=<id>` + `order_by`/`sort` parameters with official endpoint routing.
  - **TMDB:** `with_genres` (+ genre 16 and Japanese language for anime shelves) + `sort_by` (`vote_average.*`, `popularity.desc`, etc.).
  - **Kitsu:** `filter[categories]` slug translation (`sci-fi`, `slice-of-life`) + `sort` parameters.
  - **Shikimori:** Native genre IDs + `order` (`ranked`, `popularity`, `aired_on`, `name`).
  - **Simkl:** Mapped genre endpoints + `sort` (`rank`, `popular-this-week`, `release-date`).
  - **Bangumi:** Native tag dictionary (科幻, 恋爱, 机战…) + `rank`, `heat`, `score`.
- **Clean List Rebuilding:** Applying a filter clears the dataset and immediately fetches **page 1 with server-side filters applied**. Scrolling resets to the top and subsequent pages retain the same parameters.
- **In-Flight Request Guard:** Generational `filterGeneration` tokens guarantee that delayed responses from superseded requests never corrupt the updated list.
- **Source Compatibility Badges (☁️ vs 📱):** Options supported natively on the remote API are badged with **☁️**, while options falling back to local client filtering are marked with **📱**.
- **Quick Genre Chips:** Top-bar genre chips and the "Clear" button now trigger full server-side filtered re-fetches.
- **Personal Watchlists:** User-specific shelves (Simkl watchlist, TMDB Continue/Plan to Watch) continue filtering client-side as they lack remote catalog filter endpoints.
- **Safety Guards Retained:** `AUTO_LOAD_BUDGET_PER_SCROLL` and duplicate-item guards prevent runaway network calls on empty filter results.

---

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

---

# Kitsugi Beta v2.4.285

## 🇹🇷 Türkçe

### Wiki Bağlantı Çiplerinde Yüzde-Kodlu Başlık Hatası Düzeltildi
- **Kök Neden:** Bölüm altı bağlantı çipleri, bağlantının görünen metninden değil ham `href` adresinden üretiliyordu (`FandomHtmlText.parseWikiLink`). Bu çözümleyici yalnızca `%20`, `%2F`, `%3A`, `%28`, `%29` kodlarını elle çözüyordu; ō gibi ASCII dışı karakterlerin UTF-8 kodları (`%C5%8D`) olduğu gibi kalıyordu. Bu yüzden çiplerde `Shinj%C5%8D, Yamagata`, `Weekly Sh%C5%8Dnen Jump`, `Tank%C5%8Dbon` gibi başlıklar görünüyordu. Düz ASCII başlıklar (`Shueisha`, `Fine-art`…) şans eseri doğru göründüğü için hata yalnızca özel karakterli başlıklarda fark ediliyordu.
- **Düzeltme:** Başlığın tamamı artık UTF-8 olarak yüzde-kodundan çözülüyor (`Shinjō, Yamagata`, `Weekly Shōnen Jump`, `Tankōbon`, `Ten de Shōwaru Cupid`). `C++` gibi başlıklardaki `+` işareti de boşluğa dönüşmeden korunuyor; bozuk yüzde dizileri uygulamayı düşürmeden eski güvenli davranışa düşüyor.
- **Tıklama da düzeldi:** Çipe tıklanınca kodlu başlık sayfa aramasında tekrar kodlanıyor (`%25…`) ve sayfa bulunamıyordu; artık doğru sayfa açılıyor. Aynı düzelme "İlgili wiki sayfaları" adaylarını da kapsıyor.
- **Eski önbellek temizliği:** Bölüm önbelleği anahtarı `sec:v4` → `sec:v5` yükseltildi; 7 günlük eski, bozuk çipli kayıtlar güncelleme sonrası anında geçersiz sayılıyor, kullanıcının önbellek temizlemesi gerekmiyor.
- **Testler:** `FandomHtmlTextTest`'e kodlanmış ō/ş başlıkları, kodlu noktalama, `+` koruması ve bozuk kaçış dizisi senaryoları eklendi.

### Wiki Sekmeleri Chrome/Brave Düzenine Geçti
- **Bağlantılar yeni sekmede:** Sayfa içi bir bağlantıya, ilgili sayfa kartına veya bölüm çipine tıklamak artık bulunduğun sayfanın üstüne gitmiyor; sayfa, aktif sekmenin **hemen sağında yeni sekme** olarak açılıyor. Kaynak sayfa bir dokunuş uzağında aynen duruyor.
- **Daralıp genişleyen sekmeler:** Sekmeler genişliği eşit paylaşır — sekme açıldıkça hepsi birlikte daralır, kapandıkça yeniden genişler. En dar genişliğe gelinince şerit yatay kaydırılabilir olur ve yeni açılan sekme otomatik görünür alana getirilir.
- **Aktif sekme görünümü:** Chrome'daki gibi aktif sekme içerikle aynı arkaplanda üst köşeleri yuvarlı durur; daralmış pasif sekmelerde kapat (×) gizlenir, sekme genişleyince geri gelir.
- **Kopya önleme:** Aynı sayfa zaten bir sekmede açıksa ikincisi açılmaz, var olan sekmeye geçilir; sayfanın kendi bağlantısına tıklamak hiçbir şey yapmaz.
- **Sekme sınırı koruması:** 12 sekme doluysa bağlantı ölü doğmaz — aynı sekmede geçmişe eklenerek açılır, geri tuşu kaynak sayfaya döndürür.
- **`+` ve kapatma düzeni:** `+` ile çoğaltılan sekme de listenin sonuna değil aktif sekmenin sağına açılır. Aktif sekmeyi kapatmak önce sağ komşuyu, yoksa solu etkinleştirir. Sekmeye **uzun basmak** diğer tüm sekmeleri kapatır.
- **Testler:** `FandomReaderBrowserTest` ile sekme sırası, sağa ekleme, kopya önleme, sınır ve kapatma davranışları kapsama alındı.

---

## 🇬🇧 English

### Fixed Percent-Encoded Titles in Wiki Link Chips
- **Root cause:** Section link chips were derived from the raw `href` instead of a decoded title (`FandomHtmlText.parseWikiLink`), and only a handful of escapes (`%20`, `%2F`, `%3A`, `%28`, `%29`) were decoded manually. UTF-8 sequences for non-ASCII characters (e.g. ō = `%C5%8D`) leaked through, rendering chips like `Shinj%C5%8D, Yamagata`, `Weekly Sh%C5%8Dnen Jump`, `Tank%C5%8Dbon`. Plain-ASCII titles looked fine by luck, masking the bug.
- **Fix:** Titles are now fully percent-decoded as UTF-8 (`Shinjō, Yamagata`, `Weekly Shōnen Jump`, `Tankōbon`, `Ten de Shōwaru Cupid`). Literal `+` (e.g. `C++`) is preserved instead of becoming a space, and malformed escapes fall back safely without crashing.
- **Navigation fixed too:** Tapping a chip previously re-encoded the encoded title (`%25…`) and the page could not be found; chips and "Related wiki pages" candidates now open the correct article.
- **Stale cache invalidated:** Section cache key bumped `sec:v4` → `sec:v5`, so old broken chips (7-day TTL) disappear immediately after updating — no manual cache clear needed.
- **Tests:** Added coverage in `FandomHtmlTextTest` for encoded ō/ş titles, encoded punctuation, plus-sign preservation, and malformed escapes.

### Wiki Tabs Reworked to Chrome/Brave Behaviour
- **Links open in a new tab:** Tapping an in-page link, related-page card or section chip no longer navigates away; the page opens in a **new tab immediately to the right** of the active tab, leaving the source page one tap away.
- **Shrinking/growing tabs:** Tabs share the available width equally — they shrink together as tabs are opened and expand again as they are closed. At minimum width the strip becomes horizontally scrollable and the newly opened tab is auto-scrolled into view.
- **Active tab look:** Like Chrome, the active tab sits on the content background with rounded top corners; on narrow inactive tabs the close (×) button hides and returns when there is room.
- **No duplicates:** If the page is already open, its tab is focused instead of opening a second copy; tapping a page's link to itself is a no-op.
- **Tab-limit guard:** When all 12 tabs are in use, a link still works — it opens in the current tab with history, so Back returns to the source page.
- **`+` and closing order:** Duplicated tabs also open to the right of the active tab. Closing the active tab activates the right neighbour first, then the left. **Long-pressing** a tab closes all other tabs.
- **Tests:** `FandomReaderBrowserTest` covers tab order, right-side insertion, deduplication, the tab limit and close behaviour.

---

# Kitsugi Beta v2.4.284

## 🇹🇷 Türkçe

### 1. Wiki Okuyucusunda Çoklu Sekme & Tarayıcı Gezinmesi (Browser Tabs)
- **Sekme Çubuğu ve Çoklu Görev:** Uygulama içi wiki okuyucu üst çubuğuna yatay kaydırılabilir modern sekme şeridi eklendi. Kullanıcılar birden fazla wiki makalesini eşzamanlı olarak ayrı sekmelerde açık tutabilir (12 sekmeye kadar).
- **Bağımsız Sekme Geçmişi:** Her sekme kendi ileri/geri sayfa geçmişini bağımsız olarak saklar. Üst bardaki geri (←) ve ileri (→) butonlarıyla sayfalar arasında tarayıcı rahatlığında gezinilebilir.
- **Sekme Yönetimi:** Sekmeler arasında tek dokunuşla geçiş yapılabilir, 'x' butonuyla sekme kapatılabilir veya '+' butonuyla yeni sekme başlatılabilir.

### 2. İçindekiler Satır İçi (In-line) Akordeon Görünümü
- **Sayfa Altı Yığılması Giderildi:** İçindekiler bölümündeki başlıklar tıklandığında, içerik artık sayfanın en altına ayrı kartlar halinde yığılmak yerine doğrudan tıklanan başlığın hemen altında akıcı bir şekilde açılır.
- **Çoklu Bölüm Desteği:** Birden fazla bölüm aynı anda açık kalabilir ve her biri bağımsız olarak katlanabilir.

### 3. Uygulama İçi Tam Ekran Galeri Entegrasyonu
- **Dokunulabilir Görseller:** Wiki okuyucu başlık görseli, yüklenen içerik bölümlerindeki tüm resimler ve ilgili wiki sayfalarının küçük kapakları dokunulabilir hale getirildi.
- **Kitsugi Galeri Deneyimi:** Herhangi bir görsele tıklandığında doğrudan uygulamanın zengin tam ekran görsel galerisi (`KitsugiImageGalleryDialog`) açılır; yakınlaştırma, kaydırma ve yüksek kaliteli görsel inceleme imkanı sunulur.

### 4. Çeviri Kontrolleri ve Orijinal Metne Dönüş (TR/EN & Çevirmen Paritesi)
- **WikiTranslateButton Entegrasyonu:** Mevcut otomatik çeviri ayarları ve tercih edilen çevirmen motoruyla (Google, DeepL, Yandex vb.) tam entegrasyon sağlandı.
- **Başlık ve Bölüm Bazlı Geçiş:** Makale özeti ve her içerik bölümü için çevrilmiş metin ile orijinal metin arasında tek dokunuşla geçiş yapılabilir.
- **Eşzamanlılık Koruması:** Eski veya iptal edilmiş çeviri isteklerinin güncel içeriğin üzerine yazmasını engelleyen nesil/iş-kimliği doğrulaması eklendi.

### 5. Sekme ve Sayfa Bazlı Akıllı Kaydırma Konumu Saklama (Scroll Restoration)
- **Kaldığın Yerden Devam Et:** Sekmeler veya sayfalar arasında geçiş yapıldığında kaydırma konumu (scroll position) kaybolmaz; sekme, wiki ve sayfa bazında bellekte saklanarak geri yüklendiğinde tam kalınan konuma odaklanır.

---

## 🇬🇧 English

### 1. In-App Wiki Reader Multi-Tab Browsing & History Navigation
- **Browser Tab Strip:** Added a horizontal tab bar to the wiki reader top bar, allowing up to 12 simultaneous reader tabs.
- **Independent Tab History:** Each tab maintains its own back and forward page stack. Easily navigate wiki articles using dedicated back (←) and forward (→) buttons.
- **Tab Management:** Seamlessly switch tabs, close individual tabs, or open fresh tabs with a single tap.

### 2. In-Line Table of Contents Accordion View
- **No More Bottom Card Stacking:** Tapping a section in the table of contents now expands and renders the section directly underneath its title instead of appending cards at the bottom of the article.
- **Simultaneous Sections:** Multiple sections can remain expanded simultaneously, each folding and unfolding independently.

### 3. Seamless In-App Fullscreen Gallery Integration
- **Tappable Imagery:** Header banners, inline section photos, and related article thumbnails are fully interactive.
- **Kitsugi Gallery Experience:** Tapping any picture instantly launches the in-app fullscreen viewer (`KitsugiImageGalleryDialog`) with zoom, pan, and swipe-through capabilities.

### 4. Translation Controls & Original Text Toggle
- **Translator Parity:** Deeply integrated with user-configured translation settings and preferred translation engines (Google, DeepL, Yandex, etc.).
- **Instant Toggle:** Quickly switch between translated and original text for both article lead blurbs and individual section contents.
- **Stale Coroutine Guards:** Generational job keys prevent lagging translation responses from overwriting updated text.

### 5. Smart Scroll Position Restoration
- **Preserved Scroll Positions:** Reading positions are remembered across tabs, wikis, and pages. Switching tabs or navigating back automatically restores exact scroll offsets.

---

# Kitsugi Beta v2.4.283

## 🇹🇷 Türkçe

### 1. Wiki Makale İçi Mavi Bağlantılar ve İlişkili Sayfalar Artık Uygulama İçinde Açılıyor
- **Kök neden:** Yapım (medya) detayındaki Wiki sekmesi, makale bölümlerinin altındaki mavi bağlantı şeridini (örn. Hunter × Hunter → *Hunters, Hunter License, Hunter Exam, Nen, Phantom Troupe, Gon Freecss, Kite...*) çizerken uygulama içi okuyucu geri çağrısını iletmiyordu. Bu yüzden bağlantılar harici tarayıcıya yönleniyordu; önceki "sıfır tarayıcı yönlendirmesi" medya makalesinin bölüm içi bağlantılarını atlamıştı.
- **Düzeltme:** Medya Wiki sekmesi artık bölüm altı mavi bağlantıları, ilişkili sayfa kartlarını ve makale alt bilgisini doğrudan uygulama içi okuyucuya (`FandomWikiReader`) bağlıyor.
- **Hedef wiki kimliği korundu:** Karakter, ekip ve seslendiren sayfalarında bir bağlantı farklı bir wiki'ye işaret ediyorsa okuyucu o hedef wiki'de açılır.
- **İlişkili sayfa kartları:** "İlgili sayfalar" kartındaki *sayfayı aç* düğmesi ve görselsiz kart dokunuşu da tarayıcı yerine uygulama içi okuyucuyu açar.
- **Okuyucu hata durumu (Tekrar dene):** Bağlanılan sayfa yüklenemezse okuyucu sonsuz yükleme animasyonunda kalmaz; sayfa başlığıyla birlikte *"Sayfa yüklenemedi · Tekrar dene"* kartı gösterilir (medya, karakter ve ekip okuyucu akışlarının tamamında).
- Harici tarayıcı yalnızca okuyucunun üst çubuğundaki dünya (🌐) simgesine bilerek dokunulduğunda açılır.

### 2. Wiki Okuyucu "Fandom sayfasını" Butonu Çökmesi Giderildi (Kritik Düzeltme)
- **Belirti:** Yapım/karakter/ekip detayındaki Wiki sekmesinde "İlgili wiki sayfaları" şeridindeki **"Fandom sayfasını"** butonuna basıldığında veya dahili wiki bağlantısı tıklandığında, okuyucu sayfası yüklenir yüklenmez uygulama çöküyordu (`IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints`).
- **Kök neden:** Detay sayfası sekme içeriği portre düzende `DetailPageScaffold` içindeki `HorizontalPager` tarafından **sınırsız yükseklik** (`Constraints.Infinity`) ile ölçülüyor; yatay/TV düzeninde ise sekme içeriği zaten bir `verticalScroll` kutusunun içinde bulunuyor. Okuyucu bu ağacın içine gömülü çizildiğinde kendi kaydırma alanı sonsuz kısıt alıyor ya da iç içe kaydırma kısıtı nedeniyle Compose yasaklı istisnayı fırlatıyordu.
- **Çözüm:** Uygulama içi wiki okuyucu artık **kendi tam ekran penceresinde** (tam ekran `Dialog`) açılıyor. Dialog penceresi sistem seviyesinde her zaman sınırlı (bounded) kısıt verdiğinden bu çökme sınıfı kökten ortadan kaldırıldı.
- **Gezinme ve Görünüm Korundu:** Başlık çubuğu, içindekiler tablosu, bölüm içerikleri, ilgili sayfalar şeridi ve geri yığını (`BackHandler` — önceki wiki sayfasına dönüş) eksiksiz çalışır; durum çubuğu ve gezinme çubuğu boşlukları (edge-to-edge) ile MIUI/HyperOS force-dark koruması pencereye uygulandı.
- **Çift çizim temizliği:** Karakter detay sayfasında kök seviyedeki mükerrer okuyucu render'ı kaldırıldı, sekme üzerinden tek noktadan pencere açılması sağlandı.

### 2. Galeri "Detaylar" Levhası Sınırsız Yükseklik Koruması
- Galeri ekranındaki "Detaylar" bilgi levhasına, uygulamanın diğer tüm alt sayfalarında bulunan `heightIn(max = ekranın %85'i)` kısıtı eklendi. Böylece alt sayfa sınırsız yükseklikle ölçülse bile aynı dikey kaydırma çökmesinin oluşması engellendi.

### 3. Plazma Küre Yenileme Animasyonu (Pull-to-Refresh)
- **Tüm yenileme göstergeleri plazma küre oldu:** Profil (MyAnimeList / AniList vb. tüm sekmeler), Keşfet, Listem, medya detay iskeleti (`DetailPageScaffold` — film/dizi detaylarının hepsi), karakter detayı, ekip detayı, kullanıcı profili ve kullanıcı listesi ekranlarındaki varsayılan Material göstergesi (siyah yuvarlak içindeki dönen ok) tamamen kaldırıldı.
- **Ortak `KitsugiPullToRefreshIndicator` bileşeni:** Tam ekran yüklenme animasyonundaki parlayan plazma kürenin (`KitsugiPlasmaLoader`) aynısı kullanıldı. Çekmeye başlayınca belirip çekiş mesafesiyle büyür, yenileme sürerken tam boyunda atmaya devam eder, boşta duran sayfalarda hiç çizilmez (sıfır performans maliyeti).

### 2. Wiki "Hayır, yanlış" Eşleşme Kararını Geri Alma & Toplu Kurtarma
- **Geri alma kartı (`WikiRejectedUndoCard`):** "Bu eşleşme doğru mu?" kartında yanlışlıkla "Hayır, yanlış"a basıldığında, Wiki sekmesinin en üstünde **"Eşleşme yanlış işaretlendi — [Geri al] / [Kapat]"** kartı belirir. Sıradaki aday bulunsa da, hiç wiki bulunamasa da kart üstte görünür kalır; "Geri al" tek dokunuşla reddi kaldırıp eşleşmeyi yeniden çözer. Hem yapım Wiki sekmesinde hem de karakter/ekip Wiki sekmelerinde çalışır.
- **Ayarlardan toplu kurtarma:** Kart kapatılsa bile kurtarma yolu mevcuttur: **Ayarlar → Fandom Wiki → "Reddedilen eşleşmeleri geri getir"** seçeneğiyle, onaylanan eşleşmelere dokunulmadan tüm yanlış retler silinir ve wiki'ler tekrar önerilir.
- **Açıklama metni güncellendi:** Onay kartının açıklaması, yanlış işaretlenen eşleşmenin sonradan geri alınabileceğini açıkça belirtir (TR + EN).

### 3. Fandom Wiki Keşif ve Veri Çekme İyileştirmeleri (Canlı Denetimle)
- **Kimliksiz (eklenti) manga/manhwa kayıtları için AniList köprüsü:** Yalnızca başlık gönderen eklenti kayıtları için başlık AniList'te birebir eşleştiğinde kimlikler ve başlangıç yılı otomatik tamamlanır; böylece Wikidata ve karakter doğrulaması devreye girer (Naruto, One Piece, Bleach, Berserk, Solo Leveling, Chainsaw Man vb. çözülür).
- **Gerçek wiki boyutu eşiği düzeltildi:** Fandom topluluk aramasının kaba `pageCount` kovası için alt sınır 10'a çekildi; Barakamon (85), Texhnolyze (74), Uchuu Kyoudai (87) gibi gerçek wikiler artık elenmez.
- **Dil tespiti `siteinfo` ile sağlandı:** Dil öneki olmayan yabancı wikilerin (The Witcher için Lehçe `wiedzmin`, LOTR için Rusça `ru.domb`) İngilizce sanılması önlendi; öncelik sırası `en` > `tr` > diğerleri olarak yapılandırıldı.
- **Karakter kanıtı PROBABLE kuralları:** Ana karakterlerin tamamı veya ezici karakter örtüşmesi olan adaylar PROBABLE olarak kabul edilir (Space Brothers, kiss×sis, Noblesse vb.).
- **Tam giriş metni ve Bölüm 0 (infobox + özet):** Fandom'da desteklenmeyen `prop=extracts` yerine Bölüm 0 tam işlenmiş HTML olarak çekilir; giriş metni kesintisi giderildi ve bağlantılı infobox metinleri (ör. Gintoki seslendirmenleri) eksiksiz alındı.
- **Genişletilmiş sınırlar:** Paragraflar 8.000 karakter, bölümler 400 blok, tablolar 150 satır, görseller 24 adede çıkarıldı; önbellek anahtarları v4'e yükseltildi.

### 4. Fandom Uygulama İçi Wiki Okuyucu, İlgili Sayfa Kartları ve Evren Rehberi Toplu Yükleme
- **Tam ekran uygulama içi wiki okuyucu:** Wiki bağlantılarına veya özet kartlarına dokunulduğunda harici tarayıcıya atlama tamamen kaldırıldı. Sayfalar, içindekiler tablosu, tembel yüklenen bölümler ve geçmiş yığını (`BackHandler`) ile doğrudan uygulama içinde okunabilir.
- **İlgili wiki sayfaları şeridi:** Yapım ve karakter wiki sekmelerinde, ayrıca okuyucu içinde sayfadan çıkarılan dahili wiki bağlantıları yatay kaydırılabilir görsel kartlar olarak sunulur.
- **Evren rehberi tek dokunuşla toplu yükleme:** "Devamını göster" butonu kategorideki kalan tüm yapımları/öğeleri tek tıklamayla yükler.
- **Sıfır tarayıcı yönlendirmesi:** Harici tarayıcı yalnızca kullanıcı üst bardaki dünya simgesine bilerek dokunduğunda açılır.

### 5. Bangumi İngilizce Başlık, Keşfet Şeritleri ve Kitaplık Düzeltmesi
- **Gerçek dil ayrımı:** `BangumiSubjectTitleResolver` ve `BangumiTitleCache` üzerinde İngilizce, Romaji ve Özgün başlıklar birbirinden ayrıldı. Latin harfli Japonca başlıkların sahte İngilizce olarak önbelleğe alınması engellendi.
- **Keşfet ve arama entegrasyonu:** Bangumi arama sonuçları, vitrin rafları, "Tümünü Gör" sayfaları ve profil yapım listeleri kullanıcının başlık dili tercihine (İngilizce / Romaji / Özgün) tam olarak uyar.
- **Kitaplık başlık zenginleştirici:** İçe aktarılan veya takip edilen Bangumi kayıtları için İngilizce başlık çözümlemesi güvenilir kaynaklardan tamamlanır.

### 6. Karakter / Ekip / Seslendiren Wiki Sekmesi: Gerçek "Tekrar Dene" ve Bayat Önbellek Düzeltmesi
- **Ölü "Tekrar dene" düğmesi düzeltildi:** Karakter ve yapım katmanlarındaki "bulunamadı" önbelleği ile süreç içi zehir listesi atlanarak (`forgetMissingWikis()`) çözümleme baştan yapılır.
- **Bayat "bulunamadı" süresi kısaltıldı:** 3 gün → 1 gün.
- **Geçici kesintiler kalıcı "bulunamadı" yazmıyor:** HTTP 403/408 ve kimlik köprüsü kesintileri geçici sayılır; geçici hatalar kalıcı önbelleğe yazılmaz.

---

## 🇬🇧 English

### 1. In-Article Blue Wiki Links and Related Entities Open In-App
- **Root cause:** The media Wiki tab rendered the blue link strip under article sections (e.g. Hunter × Hunter → *Hunters, Hunter License, Hunter Exam, Nen, Phantom Troupe, Gon Freecss, Kite...*) without forwarding the in-app reader callback, silently dropping to the external browser. The previous "zero browser jumping" pass missed section links on media pages.
- **Fix:** The media Wiki tab now routes section links, related-entity cards and the article footer directly through the in-app reader (`FandomWikiReader`).
- **Target wiki id preserved:** On character/staff/voice-actor pages, links pointing to another wiki open the reader on that target wiki.
- **Related page cards:** The *open page* button and tapping imageless entity cards open in the in-app reader instead of launching the browser.
- **Reader failure state (Retry):** If a linked page cannot be loaded, the reader no longer spins forever; it displays the page title alongside *"Page could not be loaded · Retry"* (across media, character and staff flows).
- The external browser opens strictly when explicitly tapping the globe (🌐) icon in the reader header.

### 2. Critical Fix: Wiki Reader "Open Fandom page" Unbounded Height Crash
- **Symptom:** Tapping **"Open Fandom page"** on the related-wiki-pages strip or opening an internal wiki link in the Wiki tab crashed the app the moment the reader content finished loading (`IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints`).
- **Root cause:** In portrait mode, detail tab content is measured by a `HorizontalPager` with **unbounded height** (`Constraints.Infinity`); in landscape/TV layouts, tab content already lives inside a `verticalScroll`. Rendering the reader inline gave its scrollable column an infinite height constraint (or created nested vertical scrolls), triggering Compose's fatal measurement exception.
- **Fix:** The in-app wiki reader now opens in its **own full-screen window (`Dialog`)**. Window constraints are guaranteed to be strictly bounded, permanently eliminating this entire class of crashes.
- **Preserved Navigation & Aesthetics:** The reader maintains its top bar, table of contents, lazy section loading, related pages strip, and back-stack navigation (`BackHandler` returns to previous page). Edge-to-edge system insets and MIUI/HyperOS force-dark prevention are fully integrated.
- **Duplicate render removed:** Eliminated redundant root-level reader composition on character detail pages so multiple stacked dialogs never occur.

### 2. Gallery "Details" Sheet Height Guard
- The image gallery's details sheet now enforces the same `heightIn(max = 85% of screen)` guard used across all other bottom sheets, preventing the identical vertically-scrollable unbounded crash.

### 3. Plasma Orb Pull-to-Refresh Animation
- **All pull-to-refresh indicators are now the plasma orb:** The default Material refresh indicator (spinning arrow in a dark circle) was removed from Profile (all tabs), Explore, My List, media detail scaffold (`DetailPageScaffold` — all movies/series), character detail, staff detail, user profile, and user list screens.
- **Shared `KitsugiPullToRefreshIndicator` component:** Renders the glowing plasma orb from the fullscreen loader (`KitsugiPlasmaLoader`). Smoothly scales with pull distance, keeps pulsing at full size while refreshing, and never draws when idle (zero idle overhead).

### 2. Wiki "No, wrong" Rejection Undo & Bulk Restore
- **Undo rejection card (`WikiRejectedUndoCard`):** If "No, wrong" is tapped by mistake, an **"Undo"** card appears at the top of the Wiki tab. Whether the next candidate is found or no wiki is found at all, the card stays visible; tapping "Undo" immediately clears the rejection and re-resolves the match for both media and character/staff wiki tabs.
- **Bulk recovery in Settings:** Even if the undo card was dismissed, **Settings → Fandom Wiki → "Restore rejected matches"** restores all mistakenly rejected wikis/pages while preserving user-confirmed matches.
- **Confirmation copy updated:** Clearly explains that rejected matches can be undone later (TR + EN).

### 3. Fandom Wiki Discovery & Data Extraction Fixes (Live-Audited)
- **AniList bridge for title-only plugin records:** Titles from plugin manga/manhwa screens without IDs are exact-matched against AniList to hydrate catalog IDs and release year, enabling Wikidata and character proof.
- **Adjusted wiki article count threshold:** Fandom search `pageCount` bucket lower bound reduced to 10 so real wikis (Barakamon, Texhnolyze, Space Brothers) are no longer discarded.
- **Language detection via `siteinfo`:** Disallows non-English prefixless wikis (e.g. Polish `wiedzmin`, Russian `ru.domb`) from masquerading as English; strict ranking: `en` > `tr` > others.
- **Character evidence PROBABLE rules:** Candidates with full main cast overlap or strong multi-character confirmation are accepted as PROBABLE.
- **Section 0 lead & infobox merge:** Fetches Section 0 processed HTML directly, eliminating truncated blurbs and capturing rich linked infobox values (voice actors, staff).
- **Expanded parsing limits:** Limits increased to 8,000 chars/paragraph, 400 blocks/section, 150 table rows, 24 images; cache bumped to v4.

### 4. Fandom In-App Wiki Reader, Related Page Cards & Category Batch Loader
- **Full-screen in-app wiki reader:** External browser redirects replaced with in-app reader featuring TOC, lazy-loaded sections, internal links, and backstack navigation.
- **Related wiki pages strip:** Horizontal preview cards on media and character wiki tabs with contextual internal wiki links, titles, and thumbnails.
- **Category guide batch loader:** Single-tap batch loading for remaining items in a category.
- **Zero browser jumping:** External browser opens only when explicitly tapping the globe icon.

### 5. Bangumi English Title Discovery, Explore Shelves & Library Hydrator Fix
- **Strict title categorization:** Differentiates English, Romaji, and Native titles, preventing Romaji titles from being mistakenly cached as English.
- **Explore & search parity:** Explore shelves, "See All" grids, search results, and profile lists strictly honor user display title preferences.
- **Library title hydrator:** Reliable English title resolution for tracked and imported Bangumi entries.

### 6. Character / Staff / Voice-Actor Wiki: True Retry & Stale Negative Cache Fix
- **True Retry:** Retrying now clears in-memory poison entries (`forgetMissingWikis()`) and force-refreshes bypassing all cached negative states.
- **Stale negative cache reduced:** 3 days → 1 day.
- **Transient error resilience:** HTTP 403/408 and bridge errors are treated as transient and not persisted as permanent misses.

---

# Kitsugi-Beta v2.4.281

## 🇹🇷 Türkçe (v2.4.281)

### 🌟 Fandom Wiki Keşif, Film Türü & Çoklu Kimlik Doğrulamaları

- **Film Formatı ve Tür İyileştirmesi:** Medya detaylarından gelen `format`, `rawFormat`, alt başlık ve film başlığı sinyalleri (örn. *The Movie*, *Reze Arc*, *劇場版*, *映画*, *Film*) tür belirlemeye dahil edildi. Böylece TMDB/Wikidata aramasında dizi (`P4983`) yerine film (`P4947`) kimlikleri önceliklendirildi.
- **Karakter & Ekip Görünümlerinde Tür Normalizasyonu:** Karakter, seslendirmen ve yapım ekibi görünümlerindeki yapım başlıklarına da aynı sinyal tabanlı tür normalizasyonu uygulandı (`normalizedMediaKind()`).
- **Makale Doğrulaması (Article Proof) ve Akışa Aktarım:** Wiki adayları, yapımın Fandom makalesi ve sağlayıcı dış bağlantılarıyla güçlü doğrulamadan (`FandomMediaIdentityProof.hasStrongArticleProof`) geçiriliyor. Eşleşen makale başlığı doğrudan sonraki yükleme akışına aktarılıyor.
- **Genişletilmiş Kimlik Bağlantıları (10 URL Sınırı):** Birebir kimlik bağlantısı arama limiti 10 URL'ye yükseltildi. Bangumi (`bgm.tv/subject/`) ve Simkl (`simkl.com/`) dahil tüm sağlayıcı kimlikleri liste dışı kalmadan taranabiliyor.
- **Wikidata Arama Dayanıklılığı:** Wikidata üzerindeki tek bir sağlayıcı sorgusu (örn. zaman aşımı veya hata) diğer tüm kimliklerin taranmasını engellemiyor; tüm aramalar başarısız olduğunda sonuç geçici hata olarak korunuyor.
- **MediaWiki Dış Bağlantı Kapsamı:** `list=exturlusage` sorgusunda protokol kısıtlaması esnetilerek hem HTTP hem HTTPS bağlantıları eksiksiz taranıyor.
- **Regresyon & Birim Testleri:** Film türü çözümlemesi, karakter/ekip görünüm normalizasyonu, güçlü makale kanıtı ve tüm katalog sağlayıcı kimlik bağlantıları için kapsamlı testler eklendi.

---

## 🇬🇧 English (v2.4.281)

### 🌟 Fandom Wiki Discovery, Movie Format Resolution & Multi-Provider Identity Links

- **Movie Format & Kind Resolution:** Media detail format, rawFormat, subtitle, and movie title signals (e.g. *The Movie*, *Reze Arc*, *劇場版*, *映画*, *Film*) now participate in media kind determination. Movie identifiers in TMDB (`P4947`) and Wikidata are correctly prioritized over TV series (`P4983`).
- **Normalized Media Kinds for Character & Staff Appearances:** Character, staff, and voice actor appearance queries apply the same signal-based normalization (`normalizedMediaKind()`).
- **Article Match Proof & Downstream Passthrough:** Candidate wikis are verified using strong article proof (`FandomMediaIdentityProof.hasStrongArticleProof`) matching provider external links, passing the verified article title into the downstream page loading flow.
- **Expanded Identity Links (10 URLs Limit):** Exact external link lookup boundary increased to 10 URLs, ensuring Bangumi (`bgm.tv/subject/`) and Simkl (`simkl.com/`) identity links are fully evaluated.
- **Resilient Wikidata Lookups:** A failure or timeout on a single provider property lookup does not abort remaining lookups; transient negative errors are cached only if all lookups fail.
- **MediaWiki External Link Protocol Flexibility:** Removed protocol restriction in `list=exturlusage` queries so both HTTP and HTTPS legacy links are matched reliably.
- **Unit & Regression Tests:** Added comprehensive test coverage for theatrical work normalizations, multi-source external identity links, and article proof matching.

---

# Kitsugi-Beta v2.4.280

## 🇹🇷 Türkçe (v2.4.280)

Fandom Wiki entegrasyonu, zengin karakter sayfaları (örn. Eren Yeager), hikâye yayları (Jujutsu Kaisen, My Hero Academia, Kimetsu no Yaiba), manga/anime tabloları ve infobox API yapılarına uyumlu olarak genişletildi.

### 🌟 Yeni Özellikler ve İyileştirmeler

1. **Uygulama İçi Makale Okuyucusu (Fandom Article Reader)**
   - Evren rehberi çiplerine veya wiki içi arama sonuçlarına tıklandığında harici tarayıcıya yönlendirmek yerine doğrudan uygulama içinde açılan zengin makale okuyucu sayfası (`WikiItemSheet`) eklendi.
   - Makale başlığı, wiki kaynak adı, kısa açıklama, bilgi kutusu (infobox), görseller, açılır ve tembel yüklenen bölümler (lazy sections), spoiler koruması ve harici "Fandom'da aç" bağlantısı entegre edildi.
   - Manga bölümleri, anime bölümleri ve hikâye yaylarındaki içerik tabloları taranarak satır yapısını koruyan okunaklı bloklara dönüştürüldü; gereksiz navigasyon tabloları temizlendi.
   - Bölüm içindeki figür ve galeri görselleri güvenli HTTPS URL'leri ve resim alt yazılarıyla birlikte yakalanarak zengin görsel blokları olarak sunuldu.

2. **Genişletilmiş Evren Rehberi & 50 Başlıklı Grup Taraması**
   - Evren rehberine Karakterler (`Characters`), Hikâye Yayları (`Story Arcs`), Anime Bölümleri (`Episodes`) ve Manga Bölümleri (`Chapters`) kategorileri eklendi.
   - Türkçe ve İngilizce kategori adayları `categoryinfo` sorgularında 50 başlıklı API gruplarına bölünerek (`clean.chunked(MAX_TITLES_PER_REQUEST)`) sınırda kesilme sorunu tamamen giderildi.

3. **Hiyerarşik Spoiler ve Gizli Bölüm Kalıtımı**
   - İçindekiler tablosunda (TOC) alt bölümler artık üst bölümlerin spoiler ve gizlilik durumunu kalıtım yoluyla devralır. Gizli galeri veya navigasyon bölümlerinin alt başlıkları içindekilerde görünmez.
   - İçindekiler isteği başarısız olduğunda kullanıcıya yeniden deneme sunulur; eksik içindekiler önbelleğe alınmadığı için yeniden deneme gerçekten API'ye gider.

---

## 🇬🇧 English (v2.4.280)

Expanded Fandom Wiki integration to cater to diverse article structures, including comprehensive character pages (e.g. Eren Yeager), story arcs, manga/anime chapter/episode tables (Jujutsu Kaisen, My Hero Academia, Demon Slayer), and modern infobox API structures.

### 🌟 New Features & Improvements

1. **In-App Article Reader**
   - Tapping any universe guide chip or search hit opens a complete in-app article sheet (`WikiItemSheet`) instead of forcing an external browser jump.
   - Full support for article title, wiki source attribution, lead summary, infobox card, lazy expandable sections, spoiler gating, and external "Open on Fandom" link.
   - Formatted content tables for chapters, episodes, and story arcs while omitting extraneous navigation navboxes.
   - Extracted figure and gallery images with secure HTTPS URLs and captions displayed as rich inline image blocks.

2. **Expanded Universe Guide & 50-Title Chunked Lookups**
   - Added Characters, Story Arcs, Episodes, and Chapters to the guide categories.
   - Category existence checks are now chunked in 50-title batches (`clean.chunked(MAX_TITLES_PER_REQUEST)`), ensuring category scans never truncate at the API limit.

3. **Hierarchical Spoiler & Hidden Section Inheritance**
   - Subsections in the table of contents now inherit spoiler and hidden flags from ancestor sections. Child sections of hidden galleries or navigation blocks no longer leak into the TOC.
   - Incomplete table of contents requests display a retry button and avoid negative caching so retries actually re-query the MediaWiki API.

---

# Kitsugi-Beta v2.4.279

## 🇹🇷 Türkçe (v2.4.279)

### 🎬 Mini Oynatıcı Kontrolleri, Arka Plan Duraklatma & Video Paylaşımı
- **Mini Oynatıcı Kontrol Paneli:** Detay sayfasındaki Videolar sekmesinde ve gömülü oynatıcılarda modern kontroller eklendi: Ortada oynat/duraklat düğmesi, alt kısımda dokunarak ve sürükleyerek sarılabilen seek bar, dakika:saniye / toplam süre etiketi (`01:23 / 03:45`), oynat/duraklat ve tam ekran (fullscreen) düğmesi.
- **Akıllı Otomatik Gizleme & Çift Dokunma:** Video oynatılırken kontroller 3 saniye sonra yumuşakça gizlenir; ekrana dokunulduğunda tekrar açılır. Ekrana çift dokunma (double tap) ile ±5 saniye ileri/geri sarma desteği sağlandı.
- **Arka Plana Alınınca Otomatik Duraklatma:** Videolar sekmesinden başlatılan video oynatımı, uygulama arka plana (`ON_PAUSE` / `ON_STOP`) geçtiğinde otomatik olarak durur. Kullanıcı uygulamaya geri döndüğünde video aniden oynamaya devam etmez; oynatma kontrolü kullanıcıya bırakılır.
- **Videolar Sekmesinde Paylaş Butonu:** Video listesindeki her satırın sağına paylaşım ikonu eklendi. Paylaşımda `"Yapım Adı · Video Başlığı"` ve video bağlantısı (bağlantı bulunmuyorsa YouTube arama bağlantısı) paylaşılır.

### 🌐 Evrensel Orijinal / Çeviri Geçişi (Original / Translation Toggle)
- **Yeni Geçiş Düğmesi:** Çeviri gösterilen her ekranda, çeviri ikonunun hemen solunda "Orijinal" / "Çeviri" geçiş butonu (`KitsugiOriginalToggleButton`) yer alır.
- **Çift Yönlü Görünüm:** Metin çevrilmiş haldeyken buton "Orijinal" metnini gösterir; dokunulduğunda orijinal dile döner ve buton "Çeviri" haline gelir.
- **Kapsam:** Anime ve manga açıklamaları, API arama detayları, karakter ve ekip biyografileri, stüdyo "Hakkında" kartı, Fandom Wiki serisi özeti, wiki öğe panelleri (sheet), karakter/kişi wiki bilgi kutuları ve tüm açılır wiki bölümleri.

### 📚 Fandom Wiki: Wikidata P6262 Entegrasyonu, İlişkili Sayfalar & Orijinal Logo
- **Doğrudan P6262 Başlık Eşleme:** Wikidata P6262 kodu üzerinden doğrudan makale başlığı okunur; catalog ve arama yedekleriyle kusursuz eşleşme sağlanır.
- **Tüm Sağlayıcılarla Uyumluluk:** IMDb (`tt...`), Wikidata (`Q...`), Simkl, TMDB, AniList ve MyAnimeList kimlikleri tek çatı altında desteklenir.
- **İlişkili Sayfalar ve Kadro:** Yapıma ait ilişkili sayfalar, karakterler ve ekip listesi wiki sekmesine entegre edildi (`FandomMediaArticleMatcher`, `FandomRelationClassifier`).
- **Wikidata Karakter Oylaması:** Wikidata'da doğrudan P4073 bulunmadığında karakterlerin Fandom sayfaları üzerinden oylama yapılarak doğru wiki seçilir.
- **Galeride Orijinal Fandom Logosu:** Resim galerisinde kaynak rozetinde düz "Fandom" yazısı yerine yerel `#FA005A` Fandom marka vektör logosu (`ic_logo_fandom.xml`) gösterilir.

### 📋 Listem: Animasyonlu Başlık Barı & Premium Kategori Paneli
- **Jest Tabanlı Animasyonlu Başlık:** "Listem" ve kullanıcı listesi ekranlarında üst bar, aşağı kaydırınca yumuşakça çekilir, yukarı kaydırınca geri gelir (`KitsugiStickyHeaderReveal` + `rememberScrollAwareHeaderState`). Titreme ve zıplama olmadan akıcı jest takibi.
- **Premium Kategori Paneli:** Durum filtresi (Tümü / İzleniyor / Tamamlandı / Planlanan...) paneli "Filtrele & Sırala" paneliyle birebir aynı `KitsugiPremiumSheet` yüzey diline kavuşturuldu (aurora saç çizgisi, gradyan sürükleme kulpu, ikon karosu, rozet, yay fiziği, "Tümü / Bitti" butonları).

### ✨ Işık Küresi (KitsugiPlasmaLoader) & GC / RenderThread Optimizasyonu
- **Tek Noktadan Yüklenme Dili:** Uygulama genelindeki 52 dairesel yüklenme göstergesi (`CircularProgressIndicator`) tek tip akıcı plazma ışık küresi (`KitsugiPlasmaLoader`) ile birleştirildi.
- **Hafif Çizim Yolu & 32 Adım Renk Kuantalaması:** 30 dp ve altındaki küçük göstergeler için hafif çizim yolu (3 gradyan, dönen poligonlar devre dışı) eklendi; renk döngüsü 32 adıma kuantalanarak bellek ve GPU yükü düşürüldü.

---

## 🇬🇧 English (v2.4.279)

### 🎬 Mini Player Controls, Background Pause & Video Sharing
- **Mini Player Controls:** Inline videos in the Videos tab and detail screens now feature center Play/Pause, a bottom drag-and-seek bar, elapsed/total time (`01:23 / 03:45`), play/pause, and a working Fullscreen button.
- **Auto-Hide & Double Tap Seek:** Controls smoothly fade out after 3 seconds of continuous playback and reappear on tap. Double-tapping left/right seeks ±5 seconds.
- **Background Pause:** Inline playback automatically pauses whenever the app goes to the background (`ON_PAUSE` / `ON_STOP`). Playback does not resume automatically upon returning, keeping control with the user.
- **Share Button on Video Rows:** Added a share icon to each video entry in the Videos tab, sharing `"Media Title · Video Title"` with the direct video URL or a YouTube search fallback.

### 🌐 Universal Original / Translation Toggle
- **Toggle Button:** Everywhere translated text is displayed, an "Original" / "Translation" toggle button (`KitsugiOriginalToggleButton`) appears right beside the translation icon.
- **Bidirectional Switch:** Shows "Original" when viewing translated text; tapping reverts to the original language and changes the button text to "Translation".
- **Scope:** Anime/Manga synopsis cards, API result detail synopsis, Character & Staff biographies, Studio "About" sections, Fandom Wiki media summary, item detail sheets, character/staff wiki infoboxes, and all expandable wiki sections.

### 📚 Fandom Wiki: Wikidata P6262 Integration, Related Entities & Official Logo
- **Direct P6262 Article Matching:** Resolves article titles directly from Wikidata P6262 before catalog/name fallback paths.
- **Multi-Provider Support:** Supports IMDb (`tt...`), Wikidata (`Q...`), Simkl, TMDB, AniList, and MyAnimeList seamlessly.
- **Related Entities Extraction:** Surfaces related pages, cast, voice actors, and crew directly in the wiki tab (`FandomMediaArticleMatcher`, `FandomRelationClassifier`).
- **Wikidata Character Voting:** Elects the matching wiki through character page voting when direct P4073 is missing on the media entry.
- **Official Fandom Logo in Gallery:** Replaces plain "Fandom" text on image gallery source badges with the official `#FA005A` vector logo (`ic_logo_fandom.xml`).

### 📋 MyList: Scroll-Aware Animated Header & Premium Category Sheet
- **Gesture-Based Animated Header:** MyList and User Media List headers now smoothly slide out on downward scroll and return on upward scroll (`KitsugiStickyHeaderReveal` + `rememberScrollAwareHeaderState`) without flicker or jumps.
- **Premium Category Sheet:** Redesigned the status filter sheet (All / Watching / Completed / Planned...) to match the "Filter & Sort" sheet using `KitsugiPremiumSheet` (aurora hairline, gradient drag handle, icon tile, badge, spring physics, and "All / Done" buttons).

### ✨ Plasma Orb Loader & GC / RenderThread Optimizations
- **Unified Loading Language:** Replaced 52 circular progress spinners across 40 files with the unified glowing plasma orb (`KitsugiPlasmaLoader`).
- **Compact Draw Path & 32-Step Color Quantization:** Added a lightweight draw path for spinners <= 30 dp and quantized the color cycle to 32 steps to minimize churn and protect the RenderThread and garbage collector.


---

# Kitsugi-Beta v2.4.278

## 🇹🇷 Türkçe (v2.4.278)

### 🪟 Ayarlar Seçim Menüleri AniHyou Stili Modal Pencerelere Dönüştürüldü
- **Ortalanmış Modal Pencereler:** Ayarlar ekranındaki açılır seçimler artık satırların altında/üstünde taşarak açılmıyor; ekranın ortasında, arka planı karartan şık ve modern modal pencereler olarak açılıyor (`KitsugiSettingsDropdownDialog`).
- **Radyo Butonlu Seçenekler:** Tüm seçenekler net radyo düğmeleri ve tema renkleriyle vurgulanıyor.
- **Başlık ve Kapat Butonu:** Her pencereye ilgili ayarın açık başlığı ve sağ altta dokunulabilir / TV odaklanabilir **Kapat** eylemi eklendi.
- **Kaydırılabilir Uzun Listeler:** Uzun dil, format veya filtre listeleri modal pencere içinde kendi alanında dikey kaydırılabiliyor.
- **36 Seçim Menüsü Kapsandı:** Tema modu, uygulama dili, başlangıç sekmesi, anime/dizi başlık dili, ekip/karakter dili, liste düzeni, puanlama adımları, manga okuyucu filtre ve yönleri, varsayılan oynatıcı, intro atlama, MPV ayarları, altyazı stilleri, Stremio ve P2P profilleri dahil 36 aktif ayar menüsü uyarlandı.
- **İzole Davranış:** Arama, liste ve detay sayfalarındaki standart açılır menüler (`AnchoredPopup`) korunarak yalnızca ayarlar ağacı (`SettingsDialog`) dönüştürüldü.
- **Telefon, Tablet ve TV Uyumlu:** Genişlik (en fazla 560 dp) ve yükseklik (%86) sınırlarıyla tüm form faktörlerinde kusursuz görüntü.

### 🛡️ RenderThread Native Çökmesi (MIUI/HyperOS Force Dark) ve Genel Kararlılık Paketi
- **MIUI / HyperOS Force Dark (`TransformCanvas`) Çökmesi:** `values`, `values-v31` ve yeni `values-night` içindeki tüm temalarda `android:forceDarkAllowed="false"` zorlandı; Activity ve Dialog/BottomSheet pencere decorView'larında programatik olarak kapatıldı.
- **AngleLinearGradientBrush Yarışları & 0x0 Boyut Koruması:** `ShaderBrush` singleton yarışları thread-safe LRU shader cache ile çözüldü; Offscreen + SrcIn bileşenlerine `size > 0` kontrolleri eklendi.
- **HorizontalPager & Re-entrant Layout İyileştirmesi:** 9 `HorizontalPager` noktasında `beyondViewportPageCount = 0` yapıldı; profil sekme yükseklikleri `KitsugiProfileViewModel` içinde kalıcı saklanarak re-measure fırtınaları durduruldu.
- **GPU Buffer (AHardwareBuffer) & Bitmap Yaşam Döngüsü:** Coil `.allowHardware(false)` yapılandırılarak galeri kaynaklı GPU buffer tıkanmaları önlendi; boyut sınırları ve bitmap korumaları getirildi.
- **Detay Sayfası Animasyon & State Senkronizasyonu:** Detay sayfalarından çıkış animasyonu tamamlanmadan ekran temizlenmesi engellendi (`delay(350L)`); başlangıç sekmesi disk gecikmesiz senkron yüklendi (Profil sekmesi eklendi).
- **Güvenli Çağrılar & Benzersiz Lazy Anahtarlar:** Lambda/coroutine içi `!!` çağrıları güvenli null kontrollerine çevrildi; listelerde yinelenen anahtar hataları `kitsugiLazyKey` ile engellendi.

---

## 🇬🇧 English (v2.4.278)

### 🪟 Settings Selection Menus Redesigned into AniHyou-Style Modal Dialogs
- **Centered Modal Dialogs:** Settings dropdown panels no longer expand awkwardly below rows; they now present as centered, backdrop-dimmed modal dialogs (`KitsugiSettingsDropdownDialog`).
- **Radio-Button Option Rows:** Options now feature distinct radio buttons colored dynamically by the active theme.
- **Dialog Header & Close Button:** Each modal includes its descriptive setting title and an accessible **Close** action button.
- **Internal Vertical Scrolling:** Long lists (languages, score formats, filters) smoothly scroll within the modal container.
- **Comprehensive Scope (36 Menus):** Covers Theme Mode, App Language, Default Tab, Anime/Drama Title Languages, Staff/Character Name Languages, List Layout, Scoring Steps, Manga Reader Orientation/Color, Default Video Player, Intro Skip, MPV options, Subtitle Styles, Stremio Player, and P2P options.
- **Scoped to Settings:** Keeps search, library, and detail popups compact and unaffected via `LocalKitsugiDropdownPresentation`.
- **Responsive Across Phone, Tablet & TV:** Fitted with responsive maxWidth (560 dp) and maxHeight (86% screen) constraints.

### 🛡️ RenderThread Native Crash (MIUI/HyperOS Force Dark) & Comprehensive Stability Fixes
- **MIUI / HyperOS Force Dark Crash:** Disabled `android:forceDarkAllowed="false"` across all themes (`values`, `values-v31`, `values-night`) and decor views, resolving `libhwui.so` SIGSEGV (0x20).
- **AngleLinearGradientBrush Race Fix & 0x0 Size Protections:** Rewrote gradient brush with thread-safe LRU caching and added `size > 0` checks on Offscreen + SrcIn components.
- **HorizontalPager & Layout Stabilization:** Set `beyondViewportPageCount = 0` across 9 pagers and cached profile tab heights in `KitsugiProfileViewModel` to eliminate layout storms.
- **GPU Hardware Buffer & Bitmap Lifecycle:** Applied `.allowHardware(false)` to prevent `/dev/kgsl-3d0` buffer exhaustion in image galleries, added bounded size caps, and eliminated unsafe bitmap recycling.
- **Detail Screen Transition & Boot Synchronization:** Deferred `SaveableStateHolder` cleanup until the 280ms exit animation finishes (`delay(350L)`) and ensured instant boot tab resolution (Profile tab added).
- **Null-Safety & Unique Lazy Keys:** Replaced unsafe `!!` operators with safe null checks and ensured unique lazy list keys with `kitsugiLazyKey`.

---

# Kitsugi-Beta v2.4.277

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

# Kitsugi-Beta v2.4.276

## 🇹🇷 Türkçe (v2.4.276)

### 🔤 Wiki Çeviri Butonları Detay Sayfasıyla Birebir Aynı Oldu
- **Tek Mantık, Üç Yerde:** Karakter/kişi wiki sekmesi (Wiki özeti, Bilgi kutusu, bölümler) ve yapım wiki sekmesi (seri özeti, çip/arama kartı) çeviri butonlarının tamamı, detay sayfasındaki **Açıklama** kartının çeviri butonuyla aynı bileşene (`WikiTranslateButton`) geçti.
- **Buton Artık Kaybolmuyor:** Çeviri tamamlandığında "Orijinali göster" yazı düğmesine dönüşmüyor; ikon her zaman yerinde kalıyor — tıpkı detay sayfasındaki gibi.
- **Harici Çeviri Uygulaması:** İkona dokununca metin, ayarlardaki tercihe göre (Google Translate / DeepL / TranslateYou) **harici çeviri uygulamasında** açılıyor; uygulama kurulu değilse Google Translate web'e düşüyor.
- **Yüklenme Animasyonu:** Uygulama içi (otomatik) çeviri sürerken ikon nabız gibi yanıp sönüyor ve etrafında dönen halka görünüyor; buton o süre dokunulamıyor. Animasyon hem wiki çevirisi hem sayfanın açıklama/biyografi çevirisi sürerken çalışıyor.
- **Parça Parça Akan Çeviri:** Metinler çeviri tamamlanmasını beklemeden geldikçe ekranda tamamlanıyor (detay sayfası açıklamasıyla aynı akış).

### ⚙️ Otomatik Çeviri Wiki'de Uçtan Uca Çalışıyor
- **Ayar Açıkken:** Sayfa çözümlenir çözülmez özet ve **bilgi kutusu** kendiliğinden çevriliyor; bölümler açıldıkça çeviriliyor; medya wiki kartı açıklamaları da otomatik çevriliyor.
- **Anında Tepki:** Çeviri sekmesi açıkken ayar açılırsa yüklü metinler hemen çevriliyor, kapanırsa orijinale dönülüyor, **hedef dil** değiştirilirse tüm çeviriler yeni dile tazeleniyor.
- **Gizlilik Koruması:** Bilgi kutusu harici çeviriye gönderilirken spoiler (gizli) alanların değerleri **gönderilmiyor**.

---

## 🇬🇧 English (v2.4.276)

### 🔤 Wiki Translate Buttons Now Match the Detail Page Exactly
- **One Behavior, Everywhere:** All wiki translate buttons — character/staff wiki tab (summary, infobox, sections) and media wiki tab (series summary, chip/search card) — now use the very same component (`WikiTranslateButton`) as the detail page's **Description** translate button.
- **The Button Never Disappears:** Once a text is translated, the icon no longer swaps to a "Show original" text button; it stays in place exactly like the detail page.
- **External Translator App:** Tapping the icon opens the text in an **external translator app** (Google Translate / DeepL / TranslateYou depending on your setting), falling back to Google Translate web when no app is installed.
- **Loading Animation:** While in-app (automatic) translation runs, the icon pulses and a spinning ring appears around it; the button is disabled meanwhile. The animation also triggers while the page's own description/biography translation is running.
- **Streaming Translation:** Text fills in progressively as translation chunks arrive — the same streaming behavior as the detail page description.

### ⚙️ End-to-End Automatic Translation for Wikis
- **When the Setting Is On:** The summary and **infobox** are translated as soon as the page resolves, sections are translated as they are expanded, and media wiki card blurbs are auto-translated too.
- **Instant Reaction:** Toggling the setting while the tab is open translates the loaded texts immediately, disabling it reverts to the original, and changing the **target language** refreshes every translation.
- **Privacy Guard:** Spoiler (hidden) infobox values are **never** sent to the external translator.

---

# Kitsugi-Beta v2.4.275

## 🇹🇷 Türkçe (v2.4.275)

### 🗂️ "Listem → Tümü" Çift Kart ve Sayı Şişkinliği Düzeltmesi (Bangumi Kimlik Köprüsü)
- **Kök Neden Giderildi:** Bangumi satırları yalnızca kendi kaynak kimliğini (500M ofseti) ve Çince/Japonca katalog adını (`name_cn`) taşıdığı için, AniList/MAL kayıtlarıyla ID veya İngilizce başlık üzerinden eşleşemiyordu ve "Tümü" sekmesinde her Bangumi kaydı ayrı bir kart olarak sayılıyordu (1502 − 716 = 786 şişkinliği).
- **Sıfır Ağ İstekli Yerel Kimlik Köprüsü (`MyListIdentityCrosswalk`):** Çapraz eşitlemenin yerelde sakladığı `cross_provider_identity_v1` ve `bangumi_id_mapping` (MAL↔条目) kayıtları okunarak satırların uzayına dokunulmadan kimlik haritası kurulur. Sıfır ağ isteğiyle çalışır.
- **Konsolidasyon Turu & Union-Find (`MyListLibraryGrouping`):** Sıra bağımlılığı giderildi; üye üye doğrulama ve birleştirme turu eklendi. Kimlik anahtarları satır başına bir kez hesaplanarak binlerce satırlık listelerde gruplama performansı optimize edildi.
- **Çift Yönlü Kalıcı Öğrenme (`CrossProviderIdentity`):** Bangumi satırından MAL'e inildiğinde de MAL↔Bangumi eşlemesi yerel önbelleğe iki yönlü yazılır; içe aktarılmış kayıtlar sonraki turlarda ağa çıkmadan birleştirilir.
- **Zıplamasız Arayüz:** Kimlik köprüsü arka planda kurulur; eski liste korunarak zıplama olmadan akıcı bir şekilde birleşik kartlara dönüştürülür.

---

## 🇬🇧 English (v2.4.275)

### 🗂️ "My List → All" Duplicate Tile & Count Inflation Fix (Bangumi Identity Crosswalk)
- **Root Cause Eliminated:** Bangumi rows exclusively carry their own provider ID namespace (500M offset) and catalog name (`name_cn`), preventing them from intersecting with AniList/MAL records by ID or Latin title. Consequently, every Bangumi entry was rendered as an isolated duplicate tile in "All" tab (1502 − 716 = 786 row inflation).
- **Zero-Network Local Crosswalk (`MyListIdentityCrosswalk`):** Reads locally persisted cross-provider identity cache (`cross_provider_identity_v1`) and Bangumi mappings (`bangumi_id_mapping`, MAL↔Subject) to bridge provider IDs without modifying stored rows or issuing network requests.
- **Order-Independent Consolidation (`MyListLibraryGrouping`):** Replaced order-dependent grouping with a union-find consolidation pass that verifies and merges groups. Identity keys are memoized once per row to ensure optimal performance across large libraries.
- **Bidirectional Cache Learning (`CrossProviderIdentity`):** Bidirectionally persists MAL↔Bangumi mappings when resolving from Bangumi rows, permanently resolving future offline grouping.
- **Jitter-Free UI:** Builds identity extras on an IO dispatcher while maintaining previous states, avoiding layout jumps.

---

# Kitsugi-Beta v2.4.274

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

---

# Kitsugi-Beta v2.4.273

## 🇹🇷 Türkçe (v2.4.273)

### 📚 Fandom Wiki Faz 3 (Bilgi Kutusu Çevirileri, Görsel Dayanıklılığı, Kopyalama & Tüm Sayfalarda Wiki)
- **Bilgi Kutusu Etiket & Değer Çevirileri:** `FandomInfoboxLabels` ile ~90 bilgi kutusu etiketi (Name, Species, Birthday, Affiliation, Occupation...), grup başlıkları ve kısa durum değerleri (Male/Female, Alive, Deceased...) arayüz diline çevrilir. TR/EN dil dosyalarına 178 yeni anahtar eklendi.
- **Toplu Çeviri & Kopyalama Düğmeleri:** Bilgi kutusuna tek tıkla toplu çeviri ("Orijinal" ile geri dönüş) ve "Etiket: Değer" formatında panoya kopyalama eklendi. Wiki özeti ve her açılan bölüm kartına 36 dp'lik, değer satırlarına ise 13 dp'lik minik kopyalama düğmeleri yerleştirildi.
- **Görsel Yükleme Dayanıklılığı:** Fandom CDN'e yönelik `Referer` ve `Accept` başlık filtreleri (`FandomImageHeaders`), ölçekli → özgün → format=original yedek zinciri (`FandomWikiImage`) ve kırık görsel ikonu eklendi.
- **Tüm Sayfalarda Wiki Desteği:**
  - **Ekip / Seslendirme Sanatçısı:** `StaffDetailPage` ekranına 4. sekme olarak "Wiki" eklendi; sanatçı, seslendirdiği ana yapımların evren wiki'lerinde aranır.
  - **Eklenti Kaynaklı Manga/Manhwa:** `MangaDetailScreen` üzerine açılır "Wiki" kartı eklendi; başlık bazlı çözümleme sağlandı.
  - **Android TV:** TV detay ekranının sonuna kumanda uyumlu açılır "Wiki" kartı eklendi.

### ✨ Detay Sayfalarında Ortak Akıcı Animasyonlar (Smooth Detail Transitions)
- **Akıcı Video Açılışı & Liste Genişlemesi:** Video önizlemeleri ve açılış/kapanış (OP/ED) tema listeleri pat diye açılmak yerine yumuşak yükseklik ve görünürlük geçişiyle (`animateDetailContentSize`, `KitsugiDetailReveal`) genişleyip daralır. "Daha fazla" ile eklenen satırlar da animasyonla belirir.
- **Hafif Oynatıcı Temizliği:** Video satırı kapatılırken oynatıcı motoru gecikmesiz derhal serbest bırakılır; yalnızca satır yüksekliği yumuşakça küçülür.
- **Kaydırmayla Gelen Yapışkan Üst Çubuklar:** Karakter, ekip/seslendiren, medya ve stüdyo sayfalarındaki kaydırma başlıkları (`rememberDetailStickyHeaderVisible`) ortak fade ve kayma hareketiyle akıcı bir şekilde belirip kaybolur.
- **Akordeon & Wiki Bölümleri:** Wiki açılır kartları, sezon akordeonları ve uzun açıklama metinleri aynı merkezi hareket token'larıyla standartlaştırıldı.

### 🎬 TMDB Keşfet Başlık Dil Önceliği (Türkçe → İngilizce → Romaji)
- **Otomatik Keşfet Yenileme:** Ayarlar'dan "Dizi & Film Başlık Dili" (`tmdbLanguage`) değiştirildiğinde Keşfet sayfası artık otomatik olarak tam yeniden yüklenir; önceden eski dildeki başlıklar önbellekte kalıyordu.
- **Takvim Önbelleğinde Dil Desteği:** "Yakında Yayında" takvim şeridi önbellek anahtarına aktif TMDB dili eklendi (`tmdb|anon|tr-tr`), dil değişiminde 90 saniye eski dilde kalması önlendi.
- **Kanji Sızması Engellendi & Backoff:** İngilizce (`en-US`) yedek başlık isteklerine backoff'lu (300ms/700ms) 3 deneme mekanizması eklendi. Paralel isteklerde oluşan 429/zaman aşımı kaynaklı CJK/kanji sızması giderildi.
- **Kırılganlık Giderildi & Önbellek Geçersiz Kılma:** Bozuk tekil JSON kayıtlarının tüm şeridi düşürmesi `optJSONObject` ile engellendi. Eski hatalı başlıklı disk önbelleklerini geçersiz kılmak için `MediaTitleResolver.VERSION` 2'den 3'e yükseltildi.

### 🎌 Bangumi "Listem" Artık Başlık Dili Ayarına Uyuyor
- **Sorun:** Bangumi koleksiyonu içe aktarılırken API yalnızca özgün adı (`name`, çoğu zaman Japonca) döndürüyor; İngilizce/Romaji karşılığı **hiç gelmiyordu**. Bu yüzden "Listem → Bangumi" listesi, Ayarlar → Görünüm & Tercihler → **Anime & Manga Başlık Dili** ne seçilirse seçilsin CJK (Japonca/Çince) adla çiziliyordu.
- **Kalıcı Başlık Tamamlama:** Yeni `BangumiLibraryTitleHydrator`, kütüphanedeki eksik Latin adları arka planda tamamlar. Sıra: bellek önbelleği → Bangumi subject infobox'ı (`英文名` / `罗马字` / `别名`) → AniList birebir eşleşmesi. Sonuç veritabanına yazılır, yani **bir sonraki açılışta hiç ağ isteği yapılmaz**.
- **Anında Uygulama:** Latin ad öğrenildiği anda önbellekten okunur; liste, detay sayfası veya keşfet vitrininden hangisi önce yüklenirse yüklensin başlık aynı dile döner.
- **Özgün Ad Kaybolmaz:** Yeni Latin ad ana başlığa yazılırken özgün ad `titleJapanese` alanına taşınır; "Ana Dilde" seçeneği bozulmaz.
- **Bangumi Profil Listesi:** Profildeki koleksiyon satırları da seçilen başlık diline uyar.
- **Ağ/Performans Koruması:** Tamamlama yalnızca Latin adı gerçekten eksik satırlar için ve yalnızca "Listem" açıldığında çalışır; eşzamanlılık sınırlı, sonuçlar önbelleğe yazıldığı için tekrar denenmez.

### ⭐ Harici Puanlar (MDBList) Kartı Yeniden Tasarlandı
- **Uygulamanın Kendi Dili:** IMDb / Rotten Tomatoes / Metacritic / Letterboxd / TMDB / Trakt kutuları artık tam doygun renkli bloklar değil; "İstatistikler" ve "Türler" bloklarıyla aynı zemin, köşe yarıçapı ve tipografiyi kullanan bir kart. Puanlar marka rengine hafifçe boyanmış yastıklarda, büyük ve okunur biçimde.
- **Kesilme Yok:** Puanlar yatay kaydırılan şeride sığmadığı için kesilmiyor; ekran genişliğine eşit dağılan ızgaraya yerleşiyor (5–7 puan iki satıra dengeli biçimde bölünür).
- **"0 Metacritic" Hatası Giderildi:** MDBList bazı kayıtlar için "puan yok" yerine **0** döndürüyordu ve ekranda `0` puanlı anlamsız kutular görünüyordu. Artık 0 değerleri "puan yok" kabul edilir; hiç puan yoksa kart tamamen gizlenir.
- **Yükleme İskeleti:** Kart iskeleti yeni ızgara geometrisine uyarlandı; yükleme bitince düzen zıplamaz.

### 🗳️ Wikidata Karakter Oylaması ile Akıllı Fandom Wiki Keşfi & Simkl Desteği
- **Karakter Sayfalarından Wiki Oylaması:** Wikidata üzerinde yapımın doğrudan Fandom bağlantısı (P4073) bulunmadığında (yapımların %99'unda eksik), medyanın QID'i bulunur → ters P1441 ("present in work") ile karakterler taranır → karakterlerin P6262 Fandom sayfa kimlikleri üzerinden wiki oylaması (`WikidataCharacterVotes`) yapılır.
- **Güvenli Eşik & Doğrulama:** Bir wiki'nin seçilmesi için en az 2 oy alması, ikinciden kesin fazla olması ve `siteinfo` ile doğrulanması (canlı ve ≥20 makale) şartı getirildi. Dil wikileri ve engelli wikiler elenir.
- **Simkl Entegrasyonu:** `FandomSourceIds` modeline `simklId` eklendi; `fromStableId("simkl", ...)` ofset dönüşümü tamamlandı ve detay sayfalarından Fandom sorgusuna iletildi.
- **Yeniden Deneme:** Wiki kartında "Tekrar dene" tıklandığında eski olumsuz önbellekler temizlenerek çözümleme sıfırdan koşturulur.

---

## 🇬🇧 English (v2.4.273)

### 📚 Fandom Wiki Phase 3 (Infobox Translations, Image Resilience, Copy Actions & Global Wiki Tabs)
- **Infobox Label & Value Localization:** Integrated `FandomInfoboxLabels` supporting ~90 infobox keys (Species, Birthday, Affiliation, Occupation...), group headers, and finite status values (Male/Female, Alive, Deceased...) localized via strings.xml.
- **Batch Translation & One-Tap Copy:** Added batch translation for character infoboxes alongside structured full-infobox copy actions. Added 36dp copy icon buttons for summary & section cards and compact 13dp copy buttons for individual attribute rows.
- **Resilient Image Pipeline:** Introduced `FandomImageHeaders` (`Referer` & AVIF-safe headers), `FandomWikiImage` multifold fallback chain (scaled → original → `format=original`), and broken-image indicator.
- **Ubiquitous Wiki Integration:**
  - **Staff / Voice Actor Screens:** Added a dedicated 4th "Wiki" tab to `StaffDetailPage` querying media wikis for the person's voiced characters.
  - **Extension Manga/Manhwa:** Added a collapsible "Wiki" card on `MangaDetailScreen` with title-fallback cache resolution.
  - **Android TV:** Added a remote-friendly collapsible "Wiki" section card at the end of TV detail screens.

### ✨ Smooth Detail Animations & Transitions
- **Fluid Video & List Expansions:** Video preview panels and opening/ending theme song lists now expand and collapse smoothly (`animateDetailContentSize`, `KitsugiDetailReveal`) rather than popping into view abruptly. Additional loaded rows animate in seamlessly.
- **Instant Media Player Release:** Closing a video row instantly releases the media player engine while the container height gracefully shrinks.
- **Sticky Header Transitions:** Scrolling floating top bars across character, staff/voice actor, media, and studio detail screens (`rememberDetailStickyHeaderVisible`) smoothly fade and slide into view without jitter.
- **Standardized Accordion & Wiki Sections:** Wiki collapsible cards, season accordions, and synopsis expanders now utilize centralized, unified motion tokens.

### 🎬 TMDB Explore Title Language Priority (Turkish → English → Romaji)
- **Automatic Explore Refresh:** Changing the "Series & Movie Title Language" (`tmdbLanguage`) now automatically refreshes the Explore screen, eliminating stale in-memory cached titles.
- **Language-Aware Airing Calendar:** Added the active TMDB language to the upcoming calendar cache key (`tmdb|anon|tr-tr`), preventing stale language serving during the 90-second TTL.
- **Kanji Fallback Elimination:** Implemented 3 retries with backoff (300ms/700ms) for `en-US` fallback title fetching, preventing inadvertent CJK/kanji fallbacks under rate limits (429) or timeouts.
- **Parsing Resilience & Cache Invalidation:** Switched to `optJSONObject` so a single corrupted item no longer breaks entire discover rows. Bumped `MediaTitleResolver.VERSION` from 2 to 3 to invalidate stale legacy title caches on disk.

### 🎌 Bangumi "My List" Now Follows the Title Language Setting
- **Root Cause:** The Bangumi collection API returns only the original name (`name`, usually Japanese) and never provides the English/Romaji counterpart. As a result, the Bangumi tab of My List always rendered CJK titles regardless of **Settings → Appearance & Preferences → Anime & Manga Title Language**.
- **Permanent Title Completion:** A new `BangumiLibraryTitleHydrator` fills in missing Latin titles for library rows in the background. Order: in-memory cache → Bangumi subject infobox (`英文名` / `罗马字` / `别名`) → exact AniList match. Results are written to the database, so **no network request happens on the next launch**.
- **Immediate Application:** Once a Latin title is known, it is served from cache — the list reflects the chosen language no matter which screen (list, detail, or explore) resolved it first.
- **Original Name Preserved:** The original name is moved into the `titleJapanese` field, so the "Native" option keeps working.
- **Bangumi Profile List:** Collection rows on the profile screen now follow the selected title language too.
- **Network/Performance Guard:** Completion only targets rows that actually lack a Latin title and only runs when My List is opened; concurrency is limited and results are cached so nothing is retried.

### ⭐ External Ratings (MDBList) Card Redesigned
- **Native App Language:** IMDb / Rotten Tomatoes / Metacritic / Letterboxd / TMDB / Trakt tiles are no longer fully saturated color blocks. They now use the same surface, corner radius, and typography as the "Statistics" and "Genres" blocks, with large readable scores on brand-tinted tiles.
- **No More Cut-off:** Ratings no longer overflow a horizontally scrolled strip; they are laid out in a grid that spans the screen width (5–7 ratings split across two balanced rows).
- **"0 Metacritic" Fixed:** MDBList returned **0** instead of "no score" for some entries, which rendered meaningless zero boxes. Zero is now treated as "no score", and the card hides entirely when nothing is available.
- **Loading Skeleton:** The skeleton mirrors the new grid geometry so the layout no longer jumps when loading finishes.

### 🗳️ Smart Fandom Wiki Discovery via Wikidata Character Voting & Simkl Support
- **Character Page Wiki Voting:** When an entry lacks a direct Fandom wiki property (P4073) on Wikidata (missing in ~99% of media), media QIDs are looked up → reverse P1441 ("present in work") queries characters → character P6262 Fandom article claims vote on the correct wiki (`WikidataCharacterVotes`).
- **Strict Consensus & Validation:** A wiki candidate must achieve at least 2 votes with a strict plurality over runner-ups, and is validated via `siteinfo` (active and ≥20 articles). Language variants and blocked wikis are filtered out.
- **Simkl Integration:** Added `simklId` to `FandomSourceIds`, integrated Simkl stable ID offset resolution, and piped Simkl IDs from media detail views into wiki queries.
- **Cache Invalidation on Retry:** Tapping "Retry" on wiki cards now flushes negative query caches to perform a clean re-resolution.

---

# Kitsugi-Beta v2.4.272

## 🇹🇷 Türkçe (v2.4.272)

### ⚡ AniList GraphQL Senkronizasyon Düzeltmesi (Direct Interpolation)
- **Doğrudan Dolar İşareti İnterpolasyonu:** AniList `SaveMediaListEntry` GraphQL mutasyon sorgusu artık metin yerine doğrudan Kotlin `${'$'}` interpolasyonu ile derlenir; kırılgan ardışık `.replace()` zinciri kaldırıldı.
- **Puan Güvenliği:** Puanı bulunmayan kayıtlarda `scoreRaw` değişkeni GraphQL mutasyonuna dahil edilmez. Bu sayede AniList'teki mevcut puanların silinmesi veya API'nin hata vermesi engellendi.
- **Kullanıcı Aitlik Doğrulaması:** AniList liste kayıtlarının (`aniListEntryId`) güncellenmesi veya silinmesi öncesinde, kaydın gerçekten oturum açmış kullanıcıya (`Viewer.id`) ait olduğu ve hedeflenen medyayla eşleştiği doğrulanır.

### 🛡️ Çapraz Senkronizasyon Güvenlik Denetimi & Kimlik Koruma Kalkanı (Sync Safety Audit)
- **Resmî Katalog Doğrulama Kapısı:** Farklı hesaplar arası (cross-account) yazmalarda, hedef platforma kayıt eklenmeden önce resmî MAL kataloğundan başlık, medya türü ve yıl doğrulanır; üçüncü parti tahminler ve belirsiz eşleşmeler engellenir.
- **Bangumi Çapraz Yazma Koruması (Fail-Closed):** Desteklenen API yolunda yetkili MAL↔Bangumi kimlik eşlemesi bulunmadığından, yanlış içerik yazılmasını önlemek amacıyla çapraz eklemeler güvenli tarafta kalarak atlanır; yalnızca aynı hesaptaki doğrulanmış kayıtlar güncellenir.
- **Kimlik Uzayı (Namespace) Ayrımı & Taşma Koruması:** Kitsu, Shikimori ve Bangumi sağlayıcı kimliklerinin birbirine ve MAL ID uzayına karışması engellendi. Kitsu ve Bangumi ID dönüşümlerine 32-bit tamsayı taşma kontrolü eklendi.
- **Doğrulama Birim Testleri:** `CrossSyncEntryFactoryTest`, `KitsuMediaIdentityTest`, `KitsuIdNamespaceTest` ve `CrossSyncIdentityGuardTest` ile senkronizasyon sınırlarının tam gerileme testi sağlandı.

### 📚 Fandom Wiki Faz 2 (Anında Sekme, Başlık Çevirileri & Medya Wiki Sekmesi)
- **Sıfır Zıplama & Anında Sekme:** Karakter detay sayfasında wiki sekmesi sayfa açılır açılmaz shimmer iskeletiyle belirir. Çözümleme tamamlandığında düzen hiç zıplamadan içerik doldurulur.
- **24+ Başlık Çeviri Tablosu:** "Role in the Series", "Appearances in Other Media", "Creation and Design", "Legacy", "About" gibi sık karşılaşılan Fandom başlıkları Türkçeleştirildi.
- **Medya Detayında Wiki Sekmesi:** Anime ve manga detay sayfalarına genel özet, içindekiler ve bilgi kutusu içeren Fandom Wiki sekmesi entegre edildi.

---

## 🇬🇧 English (v2.4.272)

### ⚡ AniList GraphQL Mutation Hardening (Direct Interpolation)
- **Direct Variable Interpolation:** The AniList `SaveMediaListEntry` GraphQL mutation now uses direct Kotlin `${'$'}` variable interpolation instead of error-prone string `.replace()` calls.
- **Score Integrity:** The `scoreRaw` variable and mutation argument are only emitted when a valid score exists, preventing inadvertent score resets or schema rejections on unscored media.
- **Viewer Ownership Verification:** List mutations and deletions confirm that the targeted `aniListEntryId` belongs to the authenticated viewer (`Viewer.id`) and matches the requested media identity before mutating.

### 🛡️ Cross-Account Sync Safety Hardening (Audit Implementation)
- **Authoritative Identity Gate:** Cross-account writes strictly require identity proof verified against the authoritative MAL catalog (type, exact title alias compatibility, and release year) before mutating target accounts.
- **Bangumi Fail-Closed Protection:** Because the supported Bangumi API lacks an authoritative MAL crosswalk, cross-account writes fail closed to protect user libraries from mismatched writes. Same-account native updates remain fully supported.
- **Strict Namespace Isolation & Overflow Safeguards:** Provider-native IDs (Kitsu, Shikimori, Bangumi) are strictly segregated from the 1..99M MAL range. 32-bit integer overflow protection applied to ID offsets.
- **Comprehensive Regression Tests:** New regression test suites (`CrossSyncEntryFactoryTest`, `KitsuMediaIdentityTest`, `KitsuIdNamespaceTest`, `CrossSyncIdentityGuardTest`) guarding identity resolution and boundary mutations.

### 📚 Fandom Wiki Phase 2 (Instant Tab Skeleton, Section Translation & Media Wiki)
- **Zero-Layout-Shift Tab:** The wiki tab appears instantly upon screen open with a shimmering placeholder skeleton, eliminating layout jumps and flickering.
- **Expanded Section Title Localization:** Added a dedicated dictionary mapping 24+ common Fandom wiki section headers to Turkish ("Role in the Series", "Appearances in Other Media", "Creation/Design", etc.).
- **Media Detail Wiki Tab:** Integrated encyclopedic Fandom wiki overviews, table of contents, and infoboxes into Anime and Manga detail screens.

---

## 🇹🇷 Türkçe (v2.4.271)

### 📚 Fandom Wiki Entegrasyonu (Faz 1 — Karakter Detayı & Ayarlar)
- **Karakter Sayfasında Wiki Sekmesi:** Karakter detay ekranına Fandom Wiki sekmesi eklendi. Biyografi, görünüş, kişilik, yetenekler ve ilişkiler gibi zengin ansiklopedik bilgiler artık doğrudan karakter sayfasında.
- **Güvenilir Kimlik Eşleştirme (Wikidata Köprüsü):** Eşleştirme rastgele isim araması yerine öncelikle Wikidata ID'leri üzerinden yapılır. Doğrulanamayan kayıtlar gösterilmez.
- **Doğrulama & Bildirim Kartları:** "Bu eşleşme doğru mu? (Evet / Hayır)" onay kartı eklendi. "Yanlışsa bildir" tıklandığında hatalı wiki kaydı kalıcı olarak engellenir ve sıradaki aday denenir.
- **Spoiler Koruması & Türkçe Önceliği:** Karakterin durumu ve hikâye bölümleri istek üzerine spoiler örtüsüyle gizlenir; dokunulduğunda açılır. Türkçe fandom sayfası mevcutsa öncelikli olarak gösterilir.
- **TV & Mobil Ayar Yönetimi:** Hem mobil hem Android TV Ayarlar menüsüne Fandom Wiki kontrol paneli ve tek tıkla önbellek temizleme seçeneği eklendi.

### 🎬 Detay Sayfalarında "Videolar" Sekmesi & Hızlı Oynatıcı
- **Ayrı Videolar Sekmesi:** Fragmanlar, açılış (OP) ve kapanış (ED) müzik videoları "Bilgi" sekmesinden ayrılarak "Resimler" sekmesinin yanındaki yeni "Videolar" sekmesine taşındı.
- **Kesintisiz Oynatıcı Yönetimi:** Videolar arasında hızlı geçiş yapıldığında eski oynatıcı ve devam eden YouTube istekleri anında temizlenir; oynatıcının donması veya çakışması önlenir.

### 🏢 MAL Manga Stüdyo, Yapımcı & Yazar Düzeltmeleri
- **Doğru Rol Eşlemesi:** Manga yazarları (`Kenji Inoue` vb.) yanlışlıkla stüdyo/yapımcı listesinde yer almak yerine artık doğru kişi (Staff) sayfasına yönlenir.
- **Dergi Filtreli Arama:** Manga dergileri (`good! Afternoon` vb.) Jikan `magazines={id}` filtresiyle o dergiye ait tüm mangaları listeler.
- **Yayım Durumu Çevirisi:** MAL manga sayfalarındaki `currently_publishing` ham değeri "Yayımlanıyor" olarak Türkçeleştirildi.

### 🎴 Neon Kart Çerçeveleri & Vitrin İyileştirmesi
- **Opsiyonel Kart Çerçeveleri:** Ayarlar'dan açılabilen neon degrade çerçeveler Keşfet vitrinine, takvim kartlarına ve kaynak kutularına bağlandı.
- **Akıcı Vitrin:** Ana sayfa vitrininde yükseklik sıçramaları ve kaydırma sarsıntıları giderildi.

## 🇬🇧 English (v2.4.271)

### 📚 Fandom Wiki Integration (Phase 1 — Character Detail & Settings)
- **Wiki Tab on Character Pages:** Introduced a dedicated Fandom Wiki tab for character details, providing in-depth infoboxes, biographies, appearances, abilities, personality, and relationships.
- **Verified Identity Matching (Wikidata Bridge):** Matches rely primarily on Wikidata IDs rather than ambiguous name searches, ensuring accurate fandom resolution.
- **Verification & Feedback Cards:** Added "Is this match correct? (Yes / No)" cards. Tapping "Report wrong match" permanently disassociates the incorrect wiki and advances to the next candidate.
- **Spoiler Guard & Turkish Priority:** Character status and story plot sections can be kept hidden behind a spoiler barrier until tapped. Prefers Turkish wikis when localized content is available.
- **TV & Mobile Settings Integration:** Comprehensive Fandom Wiki toggle, spoiler controls, and cache wipe action added to both Mobile and Android TV Settings.

### 🎬 Dedicated "Videos" Tab & Rapid Player Switching
- **Separated Videos Tab:** Trailers, opening themes (OP), and ending themes (ED) have moved from Overview into a new "Videos" tab adjacent to Gallery.
- **Seamless Player Transitions:** Rapid switching between videos cleanly terminates existing playback and pending YouTube network requests, preventing lockups and overlapping audio.

### 🏢 MAL Manga Studio, Producer & Author Resolution Fixes
- **Accurate Role Mapping:** Manga authors are properly attributed with the `AUTHOR` role and link directly to person (Staff) detail pages.
- **Magazine Facet Search:** Manga serialization chips redirect to filtered searches targeting all manga published under that magazine (`magazines={id}`).
- **Publishing Status Localization:** Raw MAL status string `currently_publishing` is now properly localized.

### 🎴 Neon Card Frames & Showcase Polish
- **Neon Glow Frames:** Optional neon border modifiers integrated across Explore hero cards, calendar items, and source tiles.
- **Smooth Showcase:** Stabilized Explore hero dimensions to eliminate layout shifts and stutter during scrolling.

---

## 🇹🇷 Türkçe (v2.4.270)

### 🔄 Çapraz Eşitleme Artık Altı Kaynakta: Kitsu · Shikimori · Bangumi Dahil
- **Tek Kimlik Köprüsü (`CrossProviderIdentityResolver`):** Bir kayıt hangi kaynaktan eklenirse eklensin (AniList, MAL, Simkl, Kitsu, Shikimori, Bangumi) altı kimlik birden çözülür: MAL, AniList, Kitsu, Shikimori, Bangumi `subject_id` ve TMDB/Simkl. Eski zincir yalnızca gerçek MAL ID’yi ve AniList offset’ini tanıyordu; Simkl (150M), Shikimori (400M) ve Bangumi (500M) satırlarında kimlik çözülemediği için Kitsu/Shikimori/Bangumi eşitlemesi sessizce atlanıyordu.
- **Bangumi’den Diğer Kaynaklara Yayılım:** Bangumi koleksiyonuna eklenen/düzenlenen kayıt, doğrulanmış MAL/AniList kimliği çözülebildiğinde artık AniList · MAL · Simkl · Kitsu · Shikimori listelerine de yazılır. Kimlik güvenle çözülemezse yalnızca Bangumi kalır ve gerekçe ekranda gösterilir — yanlış yapım başka listeye eklenmez.
- **„Atlandı” Artık Hata Değil:** Katalogda karşılık bulunamayan kaynak, işlemi başarısız saymıyor; bekleme kuyruğu sonsuz deneme döngüsüne girmiyor. Hata yalnızca ağ/oturum/sunucu reddinde sayılır.
- **Görünür Sonuç:** Snackbar ve rapor artık kaynak bazlı özet gösterir (ör. „AniList: eşitlendi · Kitsu: kimlik çözülemedi · Bangumi: hata”), „neden eşitlenmedi” sorusu raporu açmadan cevaplanır.
- **Kalıcı Eşleme Önbelleği:** Çözülen kimlikler `cross_provider_identity_v1` içinde saklanır; ilerleme her değiştiğinde yeniden katalog araması yapılmaz. MAL↔Bangumi ve MAL↔Kitsu köprüleri başarıdan sonra öğrenilir.
- **Yerel Satırın Kimliği Korunur:** Çözülen gerçek MAL ID yalnızca uzak çağrılarda kullanılır; Kitsu/Shikimori/Bangumi satırları kendi stableId’sini kaybetmediği için o kaynağın liste ekranında görünmeye devam eder.
- **Hız Sınırları ve Ekleme Kapısı:** Canlı eşitleme de `PlatformRateLimiter` kullanır (6 kaynak art arda yazıldığında 429’ları önler); „en az bir hesap bağlı” kontrolü artık Kitsu/Shikimori/Bangumi’yi de tanır, Keşfet’ten ekleme bu kaynaklardan da yapılabilir.
- **Doğrulama & Test:** 13 birim testi ile doğrulandı (`CrossProviderIdentityTest`); `R.string` anahtarları TR/EN için eklendi. Detaylar: `CROSS_SYNC_ALTI_KAYNAK_FIX_RAPORU_2026-10-12.md`.

## 🇬🇧 English (v2.4.270)

### 🔄 Cross-Sync Now Covers All Six Sources: Kitsu · Shikimori · Bangumi Included
- **Single Identity Bridge (`CrossProviderIdentityResolver`):** No matter which source an entry was added from (AniList, MAL, Simkl, Kitsu, Shikimori, Bangumi), all six IDs are resolved — MAL, AniList, Kitsu, Shikimori, Bangumi `subject_id` and TMDB/Simkl. The previous chain only understood a real MAL ID and the AniList offset, so Simkl (150M), Shikimori (400M) and Bangumi (500M) rows silently skipped Kitsu/Shikimori/Bangumi.
- **Fan-Out from Bangumi:** Entries added or edited in the Bangumi collection now also reach AniList · MAL · Simkl · Kitsu · Shikimori whenever a verified MAL/AniList identity can be resolved. If it cannot, only Bangumi is written and the reason is shown — a wrong title is never pushed into another list.
- **„Skipped” is No Longer an Error:** A provider without a catalogue match no longer fails the whole operation, so the pending queue stops retrying forever. Only network/session/server rejections count as errors.
- **Visible Outcome:** The snackbar and report now show a per-source summary (e.g. „AniList: synced · Kitsu: identity unresolved · Bangumi: failed”), answering „why was it not synced?” without opening logs.
- **Persistent Mapping Cache:** Resolved identities are stored in `cross_provider_identity_v1`; progress updates no longer trigger new catalogue searches. MAL↔Bangumi and MAL↔Kitsu bridges are learned after a successful write.
- **Local Row Identity Preserved:** The resolved real MAL ID is used only for remote calls, so Kitsu/Shikimori/Bangumi rows keep their own stableId and stay visible in that provider’s list screen.
- **Rate Limits and the Add Gate:** The live path now respects `PlatformRateLimiter` (prevents 429s when six writes fire back-to-back) and the „at least one account connected” check accepts Kitsu/Shikimori/Bangumi, so adding from Explore works with those sources alone.
- **Verification & Tests:** Verified by 13 unit tests (`CrossProviderIdentityTest`); `R.string` keys added for TR/EN. Details: `CROSS_SYNC_ALTI_KAYNAK_FIX_RAPORU_2026-10-12.md`.

---

## 🇹🇷 Türkçe (v2.4.269)

### 🌸 Yeni Premium Açılır Panel (KitsugiPremiumSheet)
- **Yay Fiziği & Aurora Glow:** Açılır panellere hafifçe sekerek açılan yay (spring) animasyonlu giriş ve yumuşak kayarak kapanış eklendi. Vurgu rengiyle uyumlu parlayan gradyan kenarlık ve renkli aurora gölgesi entegre edildi.
- **Modern Detaylar:** Gradyan sürükleme kulpu, gradyan ikon karosu, saç çizgisi ayracı, aktif rozet ve modern kapatma butonu. Skrim (karartma) dışına dokunulduğunda akıcı animasyonla kapanır; Android TV'de ortalanmış dialog görünümüne otomatik uyum sağlar.
- **Tema Entegrasyonu:** `LocalKitsugiAccent`, `Brush` ve `OnAccent` ile hem düz hem de gradyan temalarda renk uyumu korunur.

### 📋 Listem Filtre Alanı Panele Taşındı (KitsugiMyListFilterSheet)
- **Zıplamasız Liste Deneyimi:** Ekrandaki satır içi filtre alanı kaldırıldı; sağ üstteki filtre butonu artık yeni premium açılır paneli tetikler. Liste boyutu kaybolmaz ve zıplama yaşanmaz.
- **Tüm Filtreler Tek Panelde:** İçerik türü hapları, Durum, Medya Türü, Puan, Yıl, Ekstra ve Sıralama seçenekleri gradyan noktalı modern başlıklarla panele toplandı. "N aktif" rozeti, Temizle ve gradyan Bitti butonları eklendi.
- **Profil Listesi Uyumu:** Profil sekmesindeki kullanıcı listesi de aynı başlığı ve paneli paylaşır.

### ⚙️ Ayarlar Sekmelerinde Yenilenen Açılır Menüler
- **KitsugiDropdownMenu:** Yay animasyonlu "pop" açılış, gradyan kenarlık, aurora ışık bandı ve renkli glow ile premium yüzeye geçirildi.
- **KitsugiMenuItem:** Seçili satırda yay animasyonlu gradyan seçim çubuğu ve sekerek beliren onay işareti entegre edildi (layout kayması engellendi).
- **Kapsam:** Tercihler, Oynatıcı, Eklentiler, P2P, Stremio ve sistem ayarlarındaki tüm açılır menüler güncellendi.

## 🇬🇧 English (v2.4.269)

### 🌸 New Premium Sheet Architecture (KitsugiPremiumSheet)
- **Spring Physics & Aurora Glow:** Bottom sheets now enter with a delightful spring bounce and exit with smooth damping. Features gradient borders and ambient aurora glow tinted by the active theme accent.
- **Polished Sheet Controls:** Features a gradient drag handle, gradient icon tile, hairline divider, active badge, and modern dismiss button. Tap outside triggers animated dismissal; automatically adapts into a centered dialog on Android TV.
- **Theme Native:** Built with `LocalKitsugiAccent`, `Brush`, and `OnAccent` for seamless harmony across solid and gradient themes.

### 📋 MyList Filters Relocated to Premium Sheet (KitsugiMyListFilterSheet)
- **Stable Layout:** Replaced the inline filter section with a dedicated premium filter sheet opened via the top-right filter icon, eliminating list jumps and viewport shifting.
- **All Filter Categories in One Sheet:** Status, Media Type, Score, Year, Extra, and Sorting options organized under gradient-dotted section headers. Includes "N active" indicator, Reset, and Done actions.
- **Profile User List Parity:** Profile screen user list automatically inherits the new header and filter sheet.

### ⚙️ Overhauled Dropdown Menus Across Settings
- **KitsugiDropdownMenu:** Upgraded to the premium surface language featuring a spring "pop" entrance, gradient borders, aurora highlights, and ambient glow.
- **KitsugiMenuItem:** Features an animated spring selection bar and bouncing checkmark on selected items without layout shift.
- **Universal Application:** Applies across Preferences, Player, Extensions, P2P, Stremio, and all settings tabs.

---

## 🇹🇷 Türkçe (v2.4.268)

### 🔄 Çeviri Butonlarında Dönen Halka & Nabız Yüklenme Animasyonu
- **Yeni Ortak Bileşen (`KitsugiTranslateIconButton`):** Galeri butonu (`KitsugiGalleryIconButton`) ile birebir aynı animasyon dili uyarlandı: Çeviri devam ederken ikon nabız gibi yanıp söner (0.30 → 1.0 alpha, 700 ms) ve etrafında tema vurgu renginde dönen bir yüklenme halkası belirir; yükleme sırasında buton dokunulamaz. 28dp, 32dp ve 36dp butonlar için orantılı halka çapları tanımlandı.
- **Kök Sağlayıcı ile Durum Paylaşımı (`LocalKitsugiTranslationLoading`):** Medya, Karakter, Ekip ve Stüdyo detay sayfalarının kök sağlayıcısına `LocalKitsugiTranslationLoading` eklendi. Sayfa içerisindeki tüm çeviri butonları durumu otomatik tüketirken, alt sayfalar ve pencereler (sheet) kendi yerel durumunu geçirerek bağımsız kalır.
- **Akış Uyumlu ViewModel Durumu:** Detay ViewModel'lerine (`ApiResult`, `MediaEntry`, `Character`, `Staff`, `Studio`) `translationLoading: StateFlow<Boolean>` eklendi. Kısmi metin akışlarında halka erken durmaz; metin akarken döner ve çeviri bittiğinde kapanır. Nesil sayacı ile eski iptal edilen isteklerin yeni durumu etkilemesi engellendi.
- **Genişletilmiş Kapsam:** Açıklama kartı, stüdyo hakkında kartı, karakter ve ekip biyografi kartları, spoiler pencereleri, inceleme, konu ve aktivite pencereleri, tüm liste görünümleri, forum yorum kartları ve profil biyografilerindeki çeviri alanlarına entegre edildi.

## 🇬🇧 English (v2.4.268)

### 🔄 Loading Spinner & Pulse Animation for Translation Buttons
- **New Reusable Component (`KitsugiTranslateIconButton`):** Implemented identical animation styling to `KitsugiGalleryIconButton`: While translation is in progress, the icon pulses (0.30 → 1.0 alpha, 700 ms) accompanied by an accent-colored spinning progress ring, and clicks are disabled during translation. Tailored ring dimensions are provided for 28dp, 32dp, and 36dp button variants.
- **Centralized Composition Local (`LocalKitsugiTranslationLoading`):** Added `LocalKitsugiTranslationLoading` to root page providers (Media, Character, Staff, Studio). Descendant translation buttons consume the state automatically, while bottom sheets and dialogs pass their own isolated states to prevent cross-component indicator bleed.
- **Stream-Aware ViewModel State:** Added `translationLoading: StateFlow<Boolean>` to detail ViewModels (`ApiResult`, `MediaEntry`, `Character`, `Staff`, `Studio`). The spinner stays active during partial/streaming text delivery and only stops once the full translation has resolved. Generation counters prevent cancelled requests from clearing newer tasks.
- **Comprehensive Coverage:** Applied to media synopsis cards, studio about cards, character/staff biography cards, spoiler sheets, review/topic/activity detail dialogs and lists, forum comment cards, and profile biography translate buttons.

---

## 🇹🇷 Türkçe (v2.4.267)

### 🛡️ RenderThread (libhwui.so) SIGSEGV ve Bellek İyileştirmeleri
- **GPU RenderNode Yarış Önlemi:** `AsyncImage` üzerinde `onImageAspectResolved` ve `onLoadingFailed` durum güncellemeleri, çizim evresinde RenderNode yıkılmasını/yeniden oluşmasını önlemek amacıyla `postAfterFrame` ile bir sonraki ana iş parçacığı karesine ertelendi.
- **Sorunlu OEM Cihaz Koruması:** Xiaomi/Redmi/POCO (MIUI/HyperOS), Realme, OPPO/OnePlus (ColorOS), Vivo/iQOO (OriginOS/FunTouch), Meizu, Honor, Infinix ve düşük bellekli (<256MB) cihazlarda `libhwui.so` çökmesini önlemek için GPU RenderEffect bulanıklığı otomatik olarak devre dışı bırakılıp optimize edilmiş yazılımsal bitmap stack-blur'a geçirildi. Güvenli azami bulanıklık yarıçapı 12dp olarak sınırlandırıldı.
- **Büyük Görsel Bellek Optimizasyonu:** `BlurTransformation` yüksek çözünürlüklü posterlerde 2×/4×/6× otomatik ölçek küçültme (scale-down) ve ara bitmap temizliği (recycle) ile bellek yükünü ~%80 oranında düşürdü.
- **Donanım Çizici Güvenli Tahliyesi:** Activity sonlanırken (ekran döndürme, dil değişimi veya arka plana atılma anında) bekleyen GPU çizim komutlarının katmanları yıkmadan önce tamamlanmasını sağlayan `safeFreeHardwareRenderer` reflektif koruması devreye alındı.

### 🌟 MyAnimeList Profil Genişletmesi & Oturum İyileştirmeleri
- **Zenginleştirilmiş Profil Ekranı:** MAL kullanıcı profili; Hakkında sekmesi ve harici bağlantılar, etkinlik ve geçmiş akışı, anime/manga ayrıntılı istatistikleri ve durum dağılımı, puan dağılımı, kulüpler, incelemeler, öneriler ve sayfalama destekli arkadaş listesi ile donatıldı.
- **Bildirimler İçin Ayarlar Yönlendirmesi:** Bildirim ekranında MAL web oturumu eksik veya uyuşmadığında doğrudan Ayarlar → MyAnimeList bölümüne yönlendirme eklendi; eski ayrı giriş penceresi kaldırıldı.
- **Yeniden Bağlan / Oturumu Tamamla Eylemi:** MAL ayarlarına, bağlı hesabı koparmadan yalnızca web oturum çerezlerini yenileyip yetkilendirmeyi tazeleyen bağımsız "Yeniden Bağlan" seçeneği eklendi.

## 🇬🇧 English (v2.4.267)

### 🛡️ RenderThread (libhwui.so) SIGSEGV & Memory Optimizations
- **GPU RenderNode Race Prevention:** Deferred `AsyncImage` aspect and failure callbacks using `postAfterFrame`, preventing state mutations during the draw phase from triggering RenderNode tear-down races in `libhwui.so`.
- **Targeted OEM ROM Workaround:** Automatically disables GPU RenderEffect blur and falls back to optimized bitmap stack-blur on known problematic Android OEM builds (MIUI/HyperOS, ColorOS, OriginOS, RealmeUI, etc.) and low-RAM devices (<256MB heap), clamping max safe blur radius to 12dp.
- **Bitmap Blur Allocation Reduction:** Added automatic 2×/4×/6× scale-down and intermediate bitmap recycling to `BlurTransformation`, reducing GC pressure by ~80% on high-res artworks.
- **HardwareRenderer Drain on Teardown:** Added reflective `safeFreeHardwareRenderer` inside `onActivityDestroyed` to drain pending GPU fences before layers are destroyed during rotations, theme changes, or activity exits.

### 🌟 MyAnimeList Profile Expansion & Unified Session Enhancements
- **Enriched Profile Interface:** MAL user profiles now feature external links, activities and history, full anime/manga status and score distributions, clubs, reviews, recommendations, and paginated friend lists.
- **Seamless Notification Reconnect Flow:** Notification views now route directly to Settings → MyAnimeList when the session is missing or mismatched, removing legacy dialog prompts.
- **Session Refresh Action:** Added a dedicated "Complete / Reconnect MAL Session" action in settings to refresh website cookies without unlinking the OAuth account.

---

## 🇹🇷 Türkçe (v2.4.266)

### 🌐 Tüm Hesap Girişi Ekranlarında Her Zaman Görünür 'Tarayıcıda Aç' Butonu
- **OAuth WebView Ekranı (AniList, MAL, Simkl):** "Tarayıcıda Aç" butonu daha önce yalnızca hata oluştuğunda çıkıyordu. Artık taze ekranda da her zaman "Kapat" butonuyla yan yana eşit genişlikte yer alıyor. Butona basıldığında yetki URL'si taze PKCE anahtar çiftiyle yeniden üretilir, varsayılan harici tarayıcı açılır ve dialog kapanır. Tarayıcıda onay verildikten sonra `malapp://auth` deep link şeması sayesinde otomatik olarak uygulamaya dönülüp token takası tamamlanır.
- **TV / QR Giriş Yolu (KitsugiWebViewDialog):** `KitsugiWebViewDialog` bileşenine `onOpenInBrowser` desteği eklendi; TV/QR dialogunda da harici tarayıcı butonu aktif hale getirildi.
- **Kitsu Giriş Ekranı:** API ile kullanıcı adı/şifre alanının altına hesap doğrulama veya şifre sıfırlama işlemleri için `kitsu.app/login` adresini açan "Tarayıcıda Aç" butonu ve bilgilendirme metni eklendi.

## 🇬🇧 English (v2.4.266)

### 🌐 Always-Visible 'Open in Browser' Button on All Account Login Screens
- **OAuth WebView Dialog (AniList, MAL, Simkl):** The "Open in Browser" button is now permanently visible alongside the dismiss button instead of only showing up on network errors. Tapping it re-generates the authorization URL with a fresh PKCE key pair, opens the default external browser, and closes the dialog. Returning via the `malapp://auth` deep link scheme automatically completes token exchange.
- **TV / QR Login Flow:** Added optional `onOpenInBrowser` support to `KitsugiWebViewDialog`, enabling the external browser button in TV/QR authorization dialogs.
- **Kitsu Dialog:** Added an "Open in Browser" button directing to `kitsu.app/login` beneath credentials fields for password resets and verification.

---

## 🇹🇷 Türkçe (v2.4.265)

### 🖼️ Ana Sayfa Vitrini: Dikey Modda Sabit Bant Yüksekliği & Kırpılmayan Poster
- **Sabit Bant Yüksekliği:** Dikey modda vitrin yüksekliği artık gösterilen görselin oranına göre zıplayıp kutuyu uzatıp kısaltmaz; cihazın dikey bant yüksekliğine sabitlendi.
- **Kırpılmayan Poster & Bulanık Dolgu:** Dikey modda poster gösterildiğinde kutuya sığması için kırpılıp zoomlanması engellendi. Tıpkı yatay modda olduğu gibi arkada yumuşak atmosferik bulanık dolgu, ön planda ise kırpılmamış tam boy poster ortalanarak gösterilir.
- **Yatay Mod Uyumu:** Yatay moddaki mevcut ekran-duyarlı dinamik sunum ve manga öncelikleri aynen korundu.

## 🇬🇧 English (v2.4.265)

### 🖼️ Home Hero Showcase: Fixed Portrait Band Height & Uncropped Dual-Layer Poster
- **Stable Container Height:** In portrait mode, the showcase height is now locked to the standard portrait band height rather than recalculating per slide aspect ratio, preventing jumping heights.
- **Uncropped Poster with Atmospheric Fill:** In portrait mode, vertical posters are no longer aggressively cropped or zoomed to fill the frame. They now display using a dual-layer presentation (blurred background fill with centered, uncropped artwork), matching the clean landscape behavior.
- **Landscape Consistency:** Responsive band calculations and manga cover handling in landscape mode remain fully preserved.

---

## 🇹🇷 Türkçe (v2.4.264)

### 🔔 MyAnimeList Bildirimleri: Gerçek Medya Adı, Doğru Detay ve Sonsuz Kaydırma
- **Detay Sayfası Artık Doğru Medyayla Açılıyor:** Bildirim kartının başlığı MAL'ın kategori adıydı (`categoryName` = "New Related Anime") ve tıklamada bu metin detay sayfasına başlık olarak veriliyordu; `ApiResultDetailPage` çağıranın başlığını API başlığına tercih ettiği için ekranda kategori adı kalıyordu. Yönlendirme artık medyanın gerçek adı (`mediaTitle`) ve MAL kimliği ile yapılıyor. Kimlik yoksa ad üzerinden `searchMALOnly` ile çözülüp açılıyor.
- **Daha Okunur Kartlar:** Medya bildirimlerinde başlık artık medyanın adı yapıldı; MAL kategori adı ve format türü ikinci satıra alındı (`Umayuru: Full Gate!` / `New Related Anime • ONA`).
- **Tür Karışması Düzeltildi:** Novel, Light Novel, Manhwa, Manhua, One-shot, Doujin kayıtları artık manga evreninde doğru sınıflandırılıyor; manga kimliğinin anime aramasına gidip yanlış yapımı açması engellendi. Tür payload'da yoksa URL'deki `/manga/` yolundan tespit ediliyor.
- **Sonsuz Kaydırma & Geçmiş:** `historyItems` altındaki okunmuş/eski bildirimler `items` ile birleştirilip id ile tekilleştiriliyor. Liste sonuna geldikçe akışın sonraki sayfası deneniyor (`?page=N`); yeni kayıt gelmezse akış temiz bir şekilde sonlanıyor. MAL web akışı yerel arşive de kaydediliyor.
- **Hızlı Yükleme Bildirimi:** Arama sürerken kısa bir "Detay sayfası hazırlanıyor…" göstergesi eklendi.

## 🇬🇧 English (v2.4.264)

### 🔔 MyAnimeList Notifications: Accurate Detail Routing, Media Title & Infinite Scroll
- **Accurate Media Navigation:** Notification cards previously used MAL category titles (`categoryName` = "New Related Anime") as the detail title. Navigation now passes the true `mediaTitle` and MAL ID. When ID is missing, it dynamically resolves via `searchMALOnly`.
- **Readable Card Layout:** Card headers now display the real media title, moving category and format info to the secondary line (`Umayuru: Full Gate!` / `New Related Anime • ONA`).
- **Media Type Disambiguation:** Novel, Light Novel, Manhwa, Manhua, One-shot, and Doujin items are strictly routed to the manga catalog, preventing anime ID collisions. Fallback detection inspects `/manga/` in the URL.
- **Infinite Scrolling & Deduplication:** Merges and deduplicates read notifications from `historyItems` with live `items`. Supports pagination (`?page=N`) and persists the stream to local archive.
- **Loading Feedback:** Added a clean preparation indicator while resolving media details.

---

## 🇹🇷 Türkçe (v2.4.263)

### 🌐 1. Google Translate Otomatik Çeviri Hattı Baştan Aşağı Yenilendi
Detay sayfası açıklamalarında, karakter ve ekip biyografilerinde, stüdyo sayfalarında, spoiler bloklarında ve inceleme/tartışma/aktivite pencerelerinde çevirinin bazen çalışıp bazen sessizce durmasına yol açan tüm kök nedenler çözüldü:

- **4 Kademeli Yedekli Uç Nokta Zinciri:** Tek bir uç noktaya bağlı kalmak yerine 4 farklı Google Translate uç noktası (`client=gtx&dj=1`, `client=at&dj=1`, `client=dict-chrome-ex&dj=1`, `clients5.google.com/translate_a/t`) tanımlandı. HTTP 429/403 hız sınırında beklemeden anında sıradaki yedek uç noktaya geçiliyor.
- **İptal Edilebilir İstekler & Kilitlenme Koruması:** Bloklayıcı çağrılar yerine `suspendCancellableCoroutine` kullanıldı ve eşzamanlı istek semaforu 4 izne çıkarıldı. Sayfa değiştirildiğinde veya zaman aşımında devam eden istek anında iptal edilip kota serbest bırakılıyor.
- **Parça Bazlı (Chunk-Level) Room Önbelleği:** Uzun biyografi ve incelemelerde her parça çevrildiği anda kendi hash anahtarıyla veritabanına kaydediliyor; çeviri yarıda kesilse bile hazır kısımlar kaybolmuyor.
- **CJK (Çince / Japonca / Korece) Cümle Bölme:** Boşluksuz yazılan Çince/Japonca metinlerde noktalama işaretlerinin (`。！？；…`) cümle sınırı sayılması sağlandı; parça boyutu 1400 karaktere optimize edildi.
- **Bozulmayan Markdown & Spoiler Maskelemesi:** Eski yer tutuculardaki `START`/`END` gibi kelimelerin Türkçeye çevrilerek linkleri ve yapıyı bozması engellendi. Harf içermeyen sembolik yer tutuculara (`⟪#0⟫`, `⟪@0⟫`) geçildi; `img(...)` medya etiketleri ve `~!...!~` spoiler blokları tam korumaya alındı.
- **`isLikelyTurkish` Yanlış Pozitif Koruması:** Başında Türkçe künye ("Merkez: ...", "Ülke: ...") bulunan CJK veya Kiril açıklamaların yanlışlıkla "Türkçe" sanılıp çevrilmeden bırakılması önlendi.
- **Hedef Dil İyileştirmesi:** Sistem dili İngilizce olan cihazlarda hedef dilin hatalı çözümlenmesi düzeltildi.

### 🔔 2. MyAnimeList Bildirimleri: Gerçek Medya Adı, Doğru Detay ve Sonsuz Kaydırma
- **Detay Sayfası Artık Normal MAL Detayı:** Bildirim kartının başlığı MAL'ın kategori adıydı (`categoryName` = "New Related Anime") ve detay sayfasına kategori adı başlık olarak gidiyordu. Yönlendirme artık medyanın gerçek adı (`mediaTitle`) ve MAL kimliği ile yapılıyor; kimlik yoksa `searchMALOnly` ile çözülüp açılıyor.
- **Daha Okunur Kartlar:** Medya bildirimlerinde kart başlığı artık medyanın adı, MAL kategori adı ve türü ikinci satıra indirildi (`Umayuru: Full Gate!` / `New Related Anime • ONA`).
- **Tür Karışması Düzeltildi:** Novel, Light Novel, Manhwa, Manhua, One-shot kayıtları artık manga evreninde doğru sınıflandırılıyor; manga kimliğinin anime aramasına gidip yanlış yapımı açması engellendi.
- **Sonsuz Kaydırma & Geçmiş:** `historyItems` altındaki okunmuş bildirimler `items` ile birleştirilip id ile tekilleştiriliyor. Liste sonuna geldikçe sonraki sayfa deneniyor (`?page=N`); MAL web akışı yerel arşive de kaydediliyor.

### 🖼️ 3. Vitrin (Keşfet Hero Carousel) Otomatik Görsel Boyutlandırması
Detay sayfasındaki akıllı oran mantığı Keşfet ana sayfasındaki vitrine taşındı:
- **Dinamik Kutu:** Vitrin bandı artık sabit bir yükseklik değil, o an gösterilen görselin gerçek en-boy oranına kilitlenen esnek bir kutudur.
- **Dikey Poster:** Dikey poster gösterilirken uzun-dar kutu (ekranın %62'si tavan olacak şekilde) açılır.
- **Yatay Arka Plan:** 16:9 görsel gösterilirken geniş-kısa bant (ekranın %34'ü taban olacak şekilde) oturur; karakterlerin kafasının veya görselin kırpılması önlendi.
- **Yatay Mod & Manga Uyumu:** Manga ve manhwa yapımlarında dikey kapak önceliği korunur; yatay modda arka plan bulunamadığında poster merkezlenmiş yumuşak dolguyla taşmadan oturur.

### 👤 4. MyAnimeList Tek Oturum (Unified Auth) ve Gelişmiş Profil Desteği
- **Tek Giriş Sistemi (`MalUnifiedAuthManager`):** OAuth sayfası uygulama içi WebView ile açılarak tek bir girişle hem Bearer API jetonu hem de web scraping oturum çerezleri (`MALHLOGSESSID`) aynı anda elde edilir; kullanıcının iki kez giriş yapma zorunluluğu ortadan kalktı.
- **5 Yeni MAL Web Uç Noktası:**
  - `/profile/{username}/reviews` — Sayfalanabilir kullanıcı incelemeleri
  - `/profile/{username}/recommendations` — Kullanıcının önerileri
  - `/profile/{username}/clubs` — Üye olunan kulüpler
  - `/profile/{username}/statistics/anime` — Puan, format, tür, stüdyo ve seslendirmen dağılımları
  - `/profile/{username}/statistics/manga` — Manga puan ve format istatistikleri
- **Profil Banner & Dağılım Grafikleri:** MAL profil sayfasına banner desteği, puan grafikleri ve seslendirme/stüdyo sıralama state'leri eklendi.

### 🎭 5. Karakter, Ekip, Stüdyo ve TMDB Veri Entegrasyonu
- **Gereksiz Shikimori Çeviri Fırtınasının Önlenmesi:** Jikan/MAL zaten biyografi döndürdüğünde arka planda atılan onlarca Rusça isim/biyografi çevirisi engellendi. Kiril isimler 12'lik gruplar halinde tek istekte toplu çevriliyor.
- **TMDB İngilizce Biyografi Yedeği:** TMDB oyuncu ve ekip üyelerinde Türkçe biyografi bulunmadığında doğrudan `en-US` yedeğine düşülüyor.
- **Stüdyo Meta ve Açıklama Zenginleştirmesi:** AniList ve Shikimori'de boş gelen stüdyo açıklamaları, logoları ve kuruluş tarihleri Jikan/MAL üzerinden otomatik tamamlanıp çeviriye veriliyor.
- **Eksik Kaynak Tamamlama:** AniList karakter/ekip biyografileri boşsa Jikan üzerinden tamamlanıyor; `KitsugiDetailClient.fetchSynopsis` içine TMDB dalı eklendi.

### ⚡ 6. Anında Çeviri & Canlı Ayar Tepkisi
- **Galeri Beklemesini Kaldırma:** Karakter, ekip ve medya detay sayfalarında çeviri artık 5 farklı kaynaktan görsel toplayan galeri işlemlerini beklemeden sayfa açılır açılmaz anında paralel başlatılıyor.
- **Sayfa İçi Ayar Değişimi:** Detay ekranındayken otomatik çeviri açılıp kapatıldığında veya hedef dil değiştirildiğinde sayfa anında yenilenmeden güncelleniyor.
- **Pencerelerde Otomatik Çeviri:** Spoiler sayfası (`InteractiveSpoilerSheet`), inceleme, konu ve aktivite bottom sheet'leri (ve TV sürümleri) otomatik çeviri sekmesiyle açılıyor ve parçalı akışı (`onPartial`) canlı yansıtıyor. Arama, Hero önizleme ve kütüphane diyaloglarına da çeviri desteği getirildi.

### 🧩 7. Eklenti Yönetimi ve Arayüz Düzeltmeleri
- **Cloudstream Eklenti Başlık Düzeni:** Uzun başlıklı eklentilerde sağdaki butonların başlığı sıkıştırıp harf harf dikey yazdırması sorunu `FlowRow` ve tam genişlikli başlık düzeniyle giderildi.
- **Eklenti Kaldırma:** Kaldırılan eklentilerin dosya sistemindeki kalıntılarının temizlenmesi sağlandı.

## 🇬🇧 English (v2.4.263)

### 🌐 1. Complete Overhaul of Google Translate Engine
- **4-Tier Fallback Endpoints:** 4 Google Translate endpoints (`gtx`, `at`, `dict-chrome-ex`, `clients5`) fail over instantly upon 429/403.
- **Cancellable Requests:** Non-blocking `suspendCancellableCoroutine` with 4-permit semaphore frees concurrency quotas immediately on cancellation.
- **Chunk-Level Room Persistence:** Partially translated paragraphs are preserved in Room cache across interruptions.
- **CJK Sentence Splitting:** Proper CJK punctuation splitting at 1400-char chunks.
- **Symbolic Markdown & Spoiler Masking:** Pure symbolic tokens prevent corrupted links, image tags, and broken `~!...!~` spoilers.
- **`isLikelyTurkish` Accuracy & Language Resolution:** Eliminated false positives on prefixed CJK/Cyrillic texts.

### 🔔 2. MyAnimeList Notifications: Real Title, Detail Routing & Infinite Scroll
- **Accurate Detail Navigation:** Navigates using true `mediaTitle` and MAL id; resolves via `searchMALOnly` if id is absent, eliminating "New Related Anime" category title collisions.
- **Readable Notification Cards:** Card headers prioritize the media name; category and release type reside on the second line.
- **Manga / Light Novel Universe Disambiguation:** Light novels, manhwa, manhua, and one-shots are correctly mapped to manga search queries instead of anime.
- **Infinite Scrolling & Read History:** Merges and deduplicates `historyItems` with live feed, supporting paginated queries (`?page=N`) and local archival.

### 🖼️ 3. Dynamic Hero Carousel Aspect-Ratio Scaling
- **Adaptive Box Height:** Hero carousel container dynamically locks to the resolved aspect ratio of the active page artwork instead of a fixed height.
- **Portrait & Landscape Balance:** Automatically extends up to 62% screen height for portrait posters and narrows to 34% screen height minimum for 16:9 backdrops.
- **Manga & Multi-Platform Fit:** Preserves portrait cover priority for manga/manhwa while maintaining centered contain presentation in landscape mode.

### 👤 4. MyAnimeList Unified Authentication & Expanded Profile Data
- **Single Sign-On (`MalUnifiedAuthManager`):** In-app WebView handles both OAuth token exchange and web session cookie extraction (`MALHLOGSESSID`) in a single login flow.
- **5 New Web Scraping Endpoints:** Integrated paginated user reviews, recommendations, clubs, and detailed anime/manga score/format/genre/studio/voice actor distribution statistics.
- **Banner & Breakdown State:** Added profile banner support and extended state modeling in `KitsugiProfileViewModel`.

### 🎭 5. Character, Staff, Studio & TMDB Enhancements
- Prevented redundant Shikimori translation requests when Jikan provides bios; batched Cyrillic translations.
- TMDB English biography fallback, AniList/Shikimori studio enrichment via Jikan/MAL, and missing AniList bio fallbacks.

### ⚡ 6. Instant Parallel Translation & Reactive Settings
- Immediate translation start decoupled from multi-source gallery aggregation.
- Live reactive updates on in-page settings toggle without reload.
- Full auto-translation and streaming support across spoiler, review, topic, activity sheets, and preview dialogs.

### 🧩 7. Extension Management & UI Layout Fixes
- Fixed extension header layout overflow using `FlowRow`.
- Guaranteed file deletion on extension uninstallation.

## 🇹🇷 Türkçe (v2.4.262)

### 🔔 1. MyAnimeList Bildirimleri Artık Gerçekten Geliyor (Kök Neden Düzeltmesi)
- **Belirti:** MAL hesabı bağlı olmasına rağmen Bildirimler > MyAnimeList sekmesi sürekli "MAL web oturumunu bağla" diyor ve gerçek bildirimler yerine 7 günlük yayın takvimi gösteriyordu.
- **Kök Neden:** Uygulama, MAL'ın hiç yazmadığı `is_signed_in=1` çerezini arıyordu. Bu yüzden WebView'de geçerli bir MAL oturumu dursa bile oturum "yok" sayılıyor ve bildirim isteği hiç atılmıyordu.
- **Düzeltme:** Gerçek MAL oturum çerezleri tanınıyor: `MALHLOGSESSID` ve `MALSESSIONID`. `is_logged_in` / `is_signed_in` bayrakları tek başına oturum sayılmıyor (çıkış kalıntıları eleniyor).
- **Kanıtlı Karar:** Oturum çerez adına bakılarak tahmin edilmiyor; MAL'a istek atılır ve giriş sayfasına düşülürse oturum istenir.

### 🔐 2. MyAnimeList: OAuth Hesabı ⇄ Web Oturumu Eşleşme Kontrolü
- **Sorun:** MAL bildirimleri iki ayrı kimlikle çalışır: bağlı OAuth hesabı (`users/@me`) ve WebView'deki web oturumu. WebView'de başka bir MAL hesabı açık kaldığında bildirim sayfası o yabancı hesabın bildirimlerini döndürüyordu — sessiz kimlik karışması.
- **Çözüm:** Her yüklemede iki kimlik karşılaştırılıyor: Web oturumunun sahibi bildirim HTML'indeki `header-profile` bağlantısından okunuyor; OAuth kullanıcı adı `MalImportManager.fetchUserName()` ile alınıyor.
- **Eşleşmeme Durumu:** Hesaplar eşleşmezse bildirimler gizleniyor, her iki hesabın adı gösteriliyor ve **"Oturumu temizle ve yeniden giriş yap"** butonu sunuluyor.
- **Güvenli Oturum Temizleme:** Yalnızca `myanimelist.net` çerezleri süresi geçmiş olarak silinir; OAuth jetonları ve diğer platformların oturumlarına dokunulmaz.

### ⚡ 3. Otomatik Oturum Penceresi ve Teşhis Ekranı
- MAL OAuth ile bağlı olduğu hâlde web oturumu gerçekten yoksa giriş penceresi **bir kez kendiliğinden** açılır. Giriş tamamlandığında pencere kapanır ve bildirimler otomatik yüklenir.
- MAL tanı satırı artık bulunan çerez adlarını (`MALHLOGSESSID`, vb.) ve kimlik özetini (`hesap: X` / `UYARI: OAuth=X ≠ web=Y`) raporlar; çerez değerleri asla gösterilmez.

### 🔍 4. Arama Ekranı: Dikey ve Yatay Uyumlu Shimmer İskeletleri
- **Duyarlı Yükleme İskeleti:** `KitsugiShimmerResponsiveMediaRow` bileşeni eklendi.
  - Dikey ekranda gerçek sonuç kartlarıyla birebir uyumlu `2:3` poster oranı.
  - Yatay ekranda solda poster ve sağda başlık/meta bilgi düzeni.
  - Android TV için dikey poster düzeni korundu.
- **Yatay Kaydırılabilir Raflar:** Sonuçlar gelmeden önce iskelet rafları `LazyRow` ile yatay kaydırılabiliyor.
- **Bağımsız Kaynak Geçişi:** Çoklu kaynak aramasında her kaynağın kendi sonucu geldikçe yalnızca o raf iskeletten gerçek kartlara geçiyor.

### 🧪 5. Birim Testleri
- `MalWebSessionStoreTest`: Gerçek çerez (`MALHLOGSESSID`), bayrak kalıntıları ve çerez adı ayrıştırma vakaları test edildi.
- `MalAccountMatchTest`: Tam eşleşme, eşleşmeme, büyük/küçük harf toleransı ve kimlik okunamama durumları test edildi.
- `MalNotificationsClientTest`: Oturum sahibinin başlık bağlantısından okunması ve bildirim payload profillerinin dışlanması doğrulandı.

## 🇬🇧 English (v2.4.262)

### 🔔 1. MyAnimeList Notifications Actually Load Now (Root-Cause Fix)
- **Symptom:** With a connected MAL account, the Notifications > MyAnimeList tab kept asking to "Connect MAL web session" and showed the 7-day airing calendar instead of real notifications.
- **Root Cause:** The app looked for an `is_signed_in=1` cookie that MAL never sets. Even with a valid MAL session in the in-app WebView, the session was considered missing, so the notification request was never sent.
- **Fix:** Real MAL session cookies are recognized (`MALHLOGSESSID`, `MALSESSIONID`). The `is_logged_in` / `is_signed_in` flags never count on their own, and stale sign-out leftovers are ignored.
- **Evidence-Based Decision:** The session is verified directly via HTTP requests to MAL rather than guessing cookie names.

### 🔐 2. MyAnimeList: OAuth Account ⇄ Web Session Identity Matching
- **Problem:** MAL notifications rely on two separate identities: the linked OAuth account (`users/@me`) and the WebView web session. A different MAL account left logged in inside WebView resulted in displaying foreign notifications — silent identity collision.
- **Fix:** Both identities are validated on every load: Web session owner parsed from notification page HTML (`header-profile` link); linked OAuth username obtained raw via `MalImportManager.fetchUserName()` from `users/@me`.
- **Mismatch Protection:** Notifications are concealed on mismatch; both usernames are displayed along with a **"Clear session and sign in again"** action.
- **Targeted Session Reset:** Safely expires only `myanimelist.net` cookies without clearing OAuth credentials or other platform sessions.

### ⚡ 3. Automatic Session Prompt & Enhanced Diagnostics
- If MAL OAuth is connected but no website session exists, the sign-in dialog opens automatically once, flushing cookies upon login.
- MAL diagnostic row now reports cookie presence (`MALHLOGSESSID`, etc.) and identity status (`account: X` / `WARNING: OAuth=X ≠ web=Y`) without ever exposing cookie values.

### 🔍 4. Search Screen: Adaptive Portrait & Landscape Shimmer Skeletons
- **Responsive Loading Skeletons:** Introduced `KitsugiShimmerResponsiveMediaRow`.
  - Portrait orientation matches true `2:3` poster aspect ratio and metadata structure.
  - Landscape orientation matches true wide card layout (left poster, right metadata block).
  - Preserved vertical poster orientation for Android TV.
- **Scrollable Placeholders:** Loading shelves now utilize `LazyRow` for fluid horizontal scrolling.
- **Independent Shelf Transitions:** Each provider independently transitions from shimmer to resolved media cards as results stream in.

### 🧪 5. Unit Tests
- `MalWebSessionStoreTest`: Validates session cookie presence (`MALHLOGSESSID`), flag handling, and cookie name sanitization.
- `MalAccountMatchTest`: Verifies exact matching, mismatches, case tolerance, and unreadable identity fallbacks.
- `MalNotificationsClientTest`: Validates session owner extraction from headers while excluding nested notification sender profiles.

## 🇹🇷 Türkçe (v2.4.261)

### 🎌 1. Bangumi: Listeye Ekleme / Güncelleme Düzeltildi (HTTP 415 Çözümü)
- **Belirti:** Bangumi kaynaklı "Listeye Ekle", durum/puan güncellemesi ve bölüm ilerlemesi istekleri `HTTP 415 Unsupported Media Type` hatasıyla reddediliyordu.
- **Kök Neden:** Bangumi sunucusu `Content-Type` başlığını tam dize eşleşmesiyle kontrol eder (`application/json` veya `application/json; charset=UTF-8`). OkHttp `String.toRequestBody()` çağrısında küçük harfli `; charset=utf-8` parametresi ekliyor ve elle verilen başlığı eziyordu.
- **Düzeltme:** `BangumiApiClient.buildJsonRequestBody()` metodu ham UTF-8 bayt dizisi üzerinden tam `application/json` başlığıyla inşa edildi. Tüm koleksiyon, bölüm ve yazma sondası istekleri tek noktadan düzeltildi.
- **Tanı ve Test:** 415 hataları için özel tanı metinleri eklendi ve `BangumiApiClientWriteTest` birim testi ile doğrulandı.

### 🎭 2. Oyuncu ve Kişi Detayı: Galeri Yükleme Animasyonu & Vitrin Tıklaması
- **Galeri Butonu Yükleme Durumu:** Ek görseller yüklenirken galeri butonu artık gizlenmiyor; yüklenme sürecini gösteren dönen animasyon (`KitsugiGalleryIconButton`) yer alıyor.
- **Vitrin Görseli Tıklaması:** Vitrin fotoğrafına diğer görseller henüz yüklenirken de dokunulabiliyor. Galeri mevcut görselle hemen açılıyor ve arka planda yeni görseller geldikçe liste dinamik olarak güncelleniyor.
- **Duyarlı Kontroller:** Dikey ve yatay ekran modlarında galeri kontrolleri optimize edildi (`StaffGalleryItems`).

### 💬 3. Yorum ve İnceleme Profilleri: Platform Bazlı Yönlendirme & Sosyal Önbellek
- **Doğru Platform Yönlendirmesi:** Yorum ve inceleme kartlarındaki profil tıklamalarında kaynak (`source`) ayrımı yapıldı:
  - `mal` / `jikan` → Uygulama içi yerel MAL profili
  - `kitsu` → Uygulama içi yerel Kitsu profili
  - `simkl` → Uygulama içi yerel Simkl profili
  - `anilist` → Uygulama içi yerel AniList profili
  - `TMDB` / `Shikimori` / `Bangumi` → İlgili platformun web profili
- **PlatformUserProfileScreen:** Yeni kullanıcı profili görüntüleyici ile harici kullanıcıların istatistik, favori, galeri ve arkadaş verileri gösteriliyor.
- **Sosyal Akış Önbelleği:** `SocialFeedCache` ile incelemeler ve forum yorumları önbelleğe alınarak sayfalar arası geçişlerde ve geri dönüşlerde sayfa/scroll konumu korunuyor.

### 🛡️ 4. Profil Sayfası: Yükseklik Çökmesi Giderildi & Başlangıç Sekmesi İyileştirmesi
- **Sınırsız Yükseklik Hatası Çözümü:** `FavoritesExpandedBottomSheet` içindeki lazy grid için sınırlandırılmış yükseklik (`height(favGridHeight)`) tanımlanarak Jetpack Compose sınırsız yükseklik çökmesi önlendi.
- **Null Güvenliği:** Profil ve karakter detay sayfalarındaki potansiyel null işaretçi (`!!`) force-unwrap kullanımları güvenli çağrılara dönüştürüldü.
- **Başlangıç Sekmesi Yarış Durumu:** DataStore'dan son kullanılan sekme okunurken yaşanan yarış durumu düzeltildi; uygulama açılışında son kullanılan sekme tutarlı şekilde geri yükleniyor.

### 🖼️ 5. Detay Vitrini: Dinamik Poster / Backdrop Seçimi
- `HeroArtworkPreference` (POSTER / BACKDROP) ve `resolveHeroArtworkPreference(screenWidthDp, screenHeightDp)` mimarisi eklendi.
- Yatay ekranlar ve dar/uzun telefonlar için (oran ≤ 0.55) tam dikey poster önceliği; tablet ve geniş ekranlar için yatay arka plan optimizasyonu getirildi.

### 🔔 6. MyAnimeList: Web Oturumu Destekli Yerel Bildirimler
- WebView çerez deposu üzerinden MAL bildirimlerini çekebilen `MalNotificationsClient` ve `MalWebSessionStore` altyapısı entegre edildi.
- MAL bildirimleri uygulama içindeki yerel bildirim listesinde görüntülenip okunma durumları güncellenebiliyor.

## 🇬🇧 English (v2.4.261)

### 🎌 1. Bangumi: Add to List / Updates Fixed (HTTP 415 Solution)
- **Symptom:** Bangumi "Add to list", status/score updates, and episode progress writes failed with `HTTP 415 Unsupported Media Type — request with body must set "content-type" header to "application/json"`.
- **Root Cause:** Bangumi's server checks `Content-Type` using exact string equality (`application/json` or `application/json; charset=UTF-8`). OkHttp's `String.toRequestBody()` injected lowercase `; charset=utf-8` and overrode explicit headers.
- **Fix:** `BangumiApiClient.buildJsonRequestBody()` now builds requests from raw UTF-8 bytes with exact `application/json` content type. All collection, episode, and probe endpoints are fixed.
- **Diagnostics & Testing:** Added distinct 415 diagnostic messages and verified with `BangumiApiClientWriteTest`.

### 🎭 2. Voice Actor & Staff Detail: Gallery Loading Spinner & Showcase Tap
- **Loading Spinner:** Extra artwork loading no longer hides the gallery button; an active loading indicator (`KitsugiGalleryIconButton`) is displayed.
- **Showcase Image Interaction:** Showcase hero image is clickable even while extra images are loading. The gallery opens instantly with the current photo and appends new images dynamically as they arrive (`StaffGalleryItems`).
- **Responsive Controls:** Updated gallery controls across both portrait and landscape screen modes.

### 💬 3. Comment & Review Profiles: Platform-Aware Routing & Social Cache
- **Accurate Routing:** Profile clicks on comments and reviews now respect the entry's `source`:
  - `mal` / `jikan` → In-app native MAL profile
  - `kitsu` → In-app native Kitsu profile
  - `simkl` → In-app native Simkl profile
  - `anilist` → In-app native AniList profile
  - `TMDB` / `Shikimori` / `Bangumi` → Platform web profile via browser
- **PlatformUserProfileScreen:** View external user statistics, favorites, galleries, and friends natively.
- **SocialFeedCache:** Caches reviews and forum topics so sheet state and scroll positions are retained when returning from user profiles.

### 🛡️ 4. Profile Screen: Unbounded Height Crash Fix & Initial Tab Restoration
- **Height Constraint Fix:** Wrapped `FavoritesExpandedBottomSheet` lazy grid with explicit bounded height (`height(favGridHeight)`), preventing unbounded height Compose runtime crashes.
- **Null Safety:** Cleaned up unsafe force unwraps (`!!`) across profile and character sheets to safe calls.
- **Startup Race Condition:** Fixed race condition where initial default DataStore emissions overwrote `lastUsedTab` before stored settings finished loading.

### 🖼️ 5. Detail Hero: Dynamic Poster vs. Backdrop Preference
- Introduced `HeroArtworkPreference` (POSTER / BACKDROP) and `resolveHeroArtworkPreference(screenWidthDp, screenHeightDp)`.
- Automatically selects vertical poster for narrow portrait devices (ratio ≤ 0.55) and landscape orientations, reserving backdrops for tablets and wide screens.

### 🔔 6. MyAnimeList: Web Session Native Notifications
- Integrated `MalNotificationsClient` and `MalWebSessionStore` using in-app WebView session cookies.
- Fetch and display MAL notifications natively in-app with read status updates.

## 🇹🇷 Türkçe (v2.4.260)

### 🧭 1. Ortak Üst Sekme Bandı (Görünüm & Tercihler Mimarisi)
- "Ayarlar > Görünüm & Tercihler" bandı ortak bileşene taşındı: `KitsugiSettingsSegmentedTabs`.
- **Oynatıcı Ayarları:** Ana menü listesi yerine 8 kaydırmalı sekme (`HorizontalPager`).
- **İndirmeler:** Videolar / Altyazılar / Resimler için segmentli ortak bant.
- **Platform Hata Kodları:** 11 sekme + platform logoları; her kategori yalnızca kendi kodlarını filtreler.
- **Hesap Bağlantıları:** Platformlar ve Senkronizasyon sekmeleri.
- **Duyarlı & TV Uyumlu:** Taşmayan, otomatik ortalanan, kenar solmalı ve D-pad odak takipli bant yapısı.

### 🔢 2. İlerleme Sayacı: Büyütülmüş Dokunma Alanı
- Manga ve anime ilerleme sayacında doğrudan değer girebilmek için dokunma alanı büyütüldü (`48dp × 32dp`) ve tıklamayı yutan katman kaldırıldı.

### 🔲 3. Kart Çerçeveleri
- Keşfet "Yakında Yayında" kartlarına ve detaylı sezonluk medya kartlarına çerçeve eklendi.

### 👤 4. Profil ve Listem: Birleşik Bağlantısız Hesap Durumu
- `KitsugiAccountNotConnectedState` bileşeni ile Listem ve Profil durumları eşitlendi; süresi dolmuş Simkl oturumu uyarısı eklendi.

### 🔔 5. Bildirim Ayarları ve Hesap Kapıları
- Hesapsız kaynak bildirimleri kapalı ve devre dışı; genel Airing en az bir hesap gerektirir.

### 🎯 6. Vitrin Tıklamaları ve Sayfa Katmanı
- Ana sayfa vitrininde öndeki sayfa tıklamayı doğru alır; tek dokunuş detay sayfasına gider, çift dokunuş kopyalar.

### 💀 7. Detay Ekranları: İçerikle Birebir Eşleşen Shimmer İskeletleri
- Detay sayfalarındaki tüm yükleme durumları (ilişkiler, öneriler, karakterler, ekip, istatistikler, bölümler, galeri, puanlar, incelemeler, sosyal akış) içerikle eşleşen shimmer iskeletlerine dönüştürüldü; dikey/yatay/TV uyumlu.

---

## 🇬🇧 English (v2.4.260)

### 🧭 1. Unified Segmented Top Tab Bar
- Responsive segmented tab bar `KitsugiSettingsSegmentedTabs` used in Player Settings (8 swipeable tabs), Downloads, Platform Error Codes (11 tabs with logos), and Account Connections.

### 🔢 2. Progress Counter Direct Input
- Touch targets expanded to `48dp × 32dp`; passive click blocker removed.

### 🔲 3. Card Borders
- Added borders to Explore Airing Soon and detailed seasonal media cards.

### 👤 4. Unified Not-Connected Account States
- Shared `KitsugiAccountNotConnectedState` across My List and Profile for all providers.

### 🔔 5. Notification Gates
- Source notifications disabled without accounts; global airing requires at least one provider.

### 🎯 6. Home Hero Click Improvements
- Visually dominant carousel items capture clicks reliably (`HomeHeroGestureModifiers`).

### 💀 7. Detail Screens: Content-Matching Shimmer Skeletons
- Replaced generic spinners across detail tabs with content-matching shimmer skeletons; fully adaptive across portrait, landscape, and TV dialogs.

---

## 🇹🇷 Türkçe (v2.4.259)

### 🖼️ 1. Detay Vitrini: 220dp Sabit Boşluk Kaldırıldı
- Vitrinin altındaki sabit 220dp boş alan kaldırıldı; başlık, meta bilgileri ve rozetler doğrudan görselin alt bölümüne bindirildi (overlay).

### 🔍 2. Vitrin Görseli Tıklaması ve Galeri Eşleşmesi
- Vitrindeki görsele dokunulduğunda kaynak posteri yerine ekranda o anda gösterilen gerçek görsel (backdrop/poster) galeriye aktarılır.
- Galeri henüz yükleniyor olsa bile tıklanan görsel listenin başına eklenerek beklemeden açılır.
- TMDB, Kitsu, Bangumi, AniList, MAL ve Shikimori CDN çözünürlük ve boyut varyantları (`w500` vs `original`, `medium` vs `large`) aynı görsel olarak eşleştirilir.
- `DetailHeroGallerySelectionTest` ile 4 birim test eklendi.

---

## 🇬🇧 English (v2.4.259)

### 🖼️ 1. Detail Hero: 220dp Blank Gap Removed
- Removed the hardcoded 220dp blank spacing below the hero visual; titles, metadata, and badges are now cleanly overlaid onto the bottom of the visual.

### 🔍 2. Hero Visual Click & Gallery Synchronization
- Tapping the hero visual now opens the exact artwork rendered on screen in the gallery rather than the upstream source poster.
- Clicks open instantly even if the gallery is still loading.
- TMDB, Kitsu, Bangumi, AniList, MAL, and Shikimori CDN quality and size variants are canonicalized and accurately matched.
- 4 unit tests added in `DetailHeroGallerySelectionTest`.

---

## 🇹🇷 Türkçe (v2.4.258)

### 🐙 1. Bangumi: "Listeye hiçbir şey ekleyemiyorum" — Hata Artık Görünür ve Ölçülebilir
- **Gerçek Hata Metni:** `upsertCollection()` istisnayı yutmayı bıraktı. `BangumiSyncManager.describeWriteFailure()` ile HTTP kodu ve sunucunun ham hata mesajı kullanıcıya gösterilir.
- **401 Otomatik Kurtarma:** 401 yanıtında token bir kez otomatik yenilenir ve istek tekrarlanır.
- **400 Önleyici Temizleme:** Etiketler ve notlar sunucu kurallarına göre filtrelenir; boş etiket dizisi (`tags: []`) sunucudaki mevcut etiketleri sileceği için `null` gönderilir.
- **Yan Etkisiz Yazma Testi:** Giriş diyaloğuna "Bağlantıyı ve yazma iznini test et" butonu eklendi (var olmayan yapıta boş PATCH testi).
- **Kişisel Erişim Anahtarı (PAT) Girişi:** Uygulama kaydı gerektirmeyen ve Bangumi sunucusundaki OAuth kapsam uyumsuzluğunu aşan PAT desteği eklendi.

---

## 🇬🇧 English (v2.4.258)

### 🐙 1. Bangumi: "Cannot add anything to list" — Failures Are Now Visible and Measurable
- **Real Error Text:** Exceptions are no longer swallowed; actual HTTP code and server error descriptions are displayed.
- **Self-Healing on 401:** Token is automatically refreshed once upon 401 error.
- **Pre-Flight Sanitizing:** Tags and comments are sanitized; empty tag lists are dispatched as `null` to avoid remote tag deletion.
- **Side-Effect-Free Write Test:** Added connection and write probe in login dialog.
- **Personal Access Token (PAT) Support:** Added direct PAT login bypassing OAuth server scope parsing issues.

---

## 🇹🇷 Türkçe (v2.4.257)

### 🌐 1. Güncellenmiş Domain Tablosu (57 → 88 Domain)
- `domain_fixes.json` tablosu eklenti deposundaki güncel tabloyla eşitlendi: 31 yeni domain eklendi, 7 domain güncellendi.
- **hdfilmcehennemi / hdfilmcehennemi2:** `.nl` yerine doğrudan `.biz` adreslerine güncellendi.
- **recTV:** URL sonundaki mükerrer `/api` eki temizlendi (`/api/api/...` hatası giderildi).

### 🛡️ 2. HDFilmCehennemi2 Zorla Yönlendirme Düzeltmesi
- **Kök Neden:** `KNOWN_BROKEN_DOMAINS` ve `SAFE_NAME_SUFFIXES` içindeki `"2"` eki yüzünden HDFilmCehennemi2 (`hdfilmcehennemi2.site`), ana HDFC sitesine zorla yönlendiriliyordu. Sıfır sonuç dönüyor ve 75 saniyelik zaman aşımına (`KTS-CSP-9104`) yol açıyordu.
- **Çözüm:** `hdfilmcehennemi2.site` ölü domainler listesinden çıkarıldı ve numaralı eklentilerin ana eklenti domainini miras alması engellendi.

### 🔄 3. Eklenti Bazlı "Yeniden Dene" Butonu
- Yayın arama sonuçları ekranında hata veren CloudStream eklentileri için kartın üzerine **"Yeniden Dene"** butonu eklendi.
- Tüm aramayı baştan başlatmadan, yalnızca hata veren eklenti sıfırlanarak yeniden çalıştırılabilir.

### 🧪 4. Testler
- `CsStreamRunnerLogicTest` birim testleri güncellendi (`testKnownBrokenDomains_DoesNotContain_HdFilmCehennemi2Site`, `testResolveBuiltinDomain_NumberedVariant_DoesNotInheritParentDomain`).

---

## 🇬🇧 English (v2.4.257)

### 🌐 1. Expanded Domain Mapping (57 → 88 Domains)
- Synchronized `domain_fixes.json` with the latest plugin repository mapping: 31 new domains added, 7 domains updated.
- **HDFilmCehennemi / HDFilmCehennemi2:** Updated from `.nl` to `.biz` matching active provider sources.
- **recTV:** Removed duplicate trailing `/api` path suffix that caused `/api/api/...` malformed URLs.

### 🛡️ 2. HDFilmCehennemi2 Forced Domain Redirect Fix
- Removed `hdfilmcehennemi2.site` from `KNOWN_BROKEN_DOMAINS` and prevented numbered variant plugins from inheriting parent domains, preventing timeout errors (`KTS-CSP-9104`).

### 🔄 3. Per-Plugin "Retry" Button
- Added a dedicated **"Retry"** button on error cards for failed CloudStream providers in the stream screen.

### 🧪 4. Tests
- Added unit tests in `CsStreamRunnerLogicTest` for domain resolution and numbered variant independence.

---

## 🇹🇷 Türkçe (v2.4.256)

### 🖼️ 1. Detay Vitrini: Yön ⇄ Görsel Otomatik Eşleşmesi ve TAM Kapsama (Kaynak & Listem Detay)
- **Yatay Mod → Dikey POSTER:** TMDB/Fanart kalite sırası → kaynak poster → dikey galeri (2:3 uzun-dar kutu). Dar sol panele yatay görsel sıkıştırma kaldırıldı.
- **Dikey Mod → Yatay ARKA PLAN:** TMDB/Fanart kalite sırası → kaynak arka plan → yatay galeri (16:9 geniş-kısa bant).
- **Otomatik Oran Kilitlenmesi:** Görsel yüklendiğinde `onImageAspectResolved` kutunun en-boy oranını görselin gerçek oranına kilitler.
- **TAM Kapsama (`ContentScale.Crop`):** Bulanık dolgu / boşluk katmanı kaldırıldı. Kutu görsele kilitlendiği için kırpma kaybı olmaksızın alanı tam kaplar.
- **Yedek Koruması:** Eşlerden biri yoksa diğeri güvenli oran sınırlarında ([0.5, 1.6] yatay, [1.0, 2.2] dikey) gösterilir.

---

## 🇬🇧 English (v2.4.256)

### 🖼️ 1. Detail Hero: Orientation ⇄ Artwork Auto-Pairing & Full Coverage (Source & Library Detail)
- **Landscape Mode → Vertical POSTER:** Quality ranked poster candidate in a 2:3 container fitting the tall/narrow left panel.
- **Portrait Mode → Horizontal BACKDROP:** Quality ranked backdrop candidate in a 16:9 cinematic band.
- **Aspect Ratio Locking:** Container locks to the image's exact dimensions via `onImageAspectResolved`.
- **FULL Coverage (`ContentScale.Crop`):** Blurred ambient margins removed; image fills container seamlessly with zero crop loss.
- **Fallback:** Missing pairs safely fall back within clamped bounds ([0.5, 1.6] landscape, [1.0, 2.2] portrait).

---

## 🇹🇷 Türkçe (v2.4.255)

### 🚀 1. Detay Sayfasından Eklenti Video Hattı (Süre, CF Sabrı ve Tip Eşleştirme)
- **15 sn Arama Kutusu ve CF Çözüm İptali:** CloudflareKiller meydan okumayı görünce 90–120 sn'lik WebView başlatıyordu; 15 sn'lik arama kutusu patlayınca çözüm iptal oluyor, çerez hiç kaydedilemiyor ve eklenti pat diye boş dönüyordu. Çerezi olmayan ve sağlıklı diye doğrulanmamış her host için 120 sn aşama ve 240 sn sağlayıcı sabır bütçesi tanındı (`needsCfPatience`). Sağlıklı siteler yine 1–3 sn'de döner, üst sınır beklenmez.
- **Detay İzle Hattında `searchWithCfRetry`:** Detay İzle hattı bot koruması yeniden denemesine bağlandı. İlk arama grubu boş dönüp CF kanıtı tespit edilirse gate açılıp sabırlı tek yeniden deneme yapılıyor.
- **Yanlış Tip Etiketinde Güçlü Eşleşmelerin Korunması:** Yerli eklentilerin dizi sonucunu `Movie` (veya filmi `TvSeries`) olarak etiketlemesi durumunda güçlü başlık benzerliğinde ($\ge 0.85$) aday artık elenmiyor (-0.20 ceza alır); hem film hem dizi varyantı varsa doğru tip kazanır, tek başına gelen mükemmel başlık korunur.

### 🧪 2. Testler
- `CsTitleMatcherTest`'e yalnız Movie etiketli dizi sonucunun korunduğunu doğrulayan birim testi eklendi (`misTaggedExactTitleIsKeptWhenProviderOnlyReturnsTheOtherType`).

---

## 🇬🇧 English (v2.4.255)

### 🚀 1. Detail Page Provider Video Pipeline (Timeout, CF Patience & Type Matcher)
- **15s Search Window Aborting Cloudflare Resolution:** CloudflareKiller launches a 90–120s background WebView when challenged. Uncached/challenged hosts now receive 120s stage patience and 240s overall provider timeout (`needsCfPatience`). Healthy sites still return in 1–3s without waiting.
- **`searchWithCfRetry` in Detail Pipeline:** Detail watch flow now triggers a patient CF retry when the initial batch returns empty with challenge evidence.
- **Preserving Mis-tagged Exact Matches:** Strong similarity ($\ge 0.85$) matches are retained with a slight penalty (-0.20), allowing valid content with incorrect metadata tags to resolve smoothly while prioritizing correctly-typed siblings if available.

### 🧪 2. Tests
- Added unit test in `CsTitleMatcherTest` verifying mis-tagged exact titles are retained (`misTaggedExactTitleIsKeptWhenProviderOnlyReturnsTheOtherType`).

---

## 🇹🇷 Türkçe (v2.4.254)

### 🔄 1. Eklenti Güncellemelerinin Görünmemesi Düzeltildi
- **Kök Neden (depo tarafı):** Uygulama yalnızca `Kitsugi-Plugins` deposunun `builds` dalını okur; bu dal 5 Ekim'den beri güncellenmiyordu (yayında `RoketDizi v15`, kaynakta `v19`). CI'daki yayın adımı yalnızca `main` dalında çalışıyordu ve prebuilt kopyalama/manifest üretimi erken `exit 0` ile atlanıyordu.
- **Kök Neden (uygulama tarafı):** Güncelleme kararı 6 ayrı yerde yalnızca `version > version` karşılaştırmasıyla veriliyordu; sürüm numarası artırılmadan yeniden derlenen eklentiler görünmüyordu. Ayrıca depoya ulaşılamadığında APK içindeki bayat liste sessizce "canlı" sanılıp "Tüm eklentiler güncel" deniyordu.
- **Çözüm:** `CsPluginUpdateChecker` + `CsInstalledFingerprint` ile sürüm **veya** dosya içerik parmak izi (SHA-256) farkı güncelleme sayılıyor. Manifest çekiminde raw → api.github.com → jsDelivr → Codeberg ayna zinciri (20 sn bütçe) kullanılıyor, yedek kaynaktan gelen veriler işaretleniyor ve "denetlenemedi" uyarısı gösteriliyor. Tireli depo adı (`Kitsugi-Plugins`) kontrolü düzeltildi (yedekler artık gerçekten deneniyor).
- **UI:** İçerik güncellemesinde rozet **"⟳ Yeniden derlendi (v15)"**, düğme **"⟳ Yeniden kur (v15)"** olarak gösterilir.
- **Testler:** `CsPluginUpdateCheckerTest`, `CloudstreamRepoMirrorTest`.

---

## 🇬🇧 English (v2.4.254)

### 🔄 1. Plugin Updates Not Appearing — Fixed
- **Root cause (repo side):** The app only reads the `builds` branch of `Kitsugi-Plugins`, which had not been updated since Oct 5 (published `RoketDizi v15` vs source `v19`): the CI publish step ran on `main` only, and prebuilt copying/manifest generation were skipped by an early `exit 0`.
- **Root cause (app side):** Update detection was copy-pasted `version > version` in six places, so rebuilds without a version bump were invisible; and when the repo was unreachable the bundled (frozen) plugin list was treated as live, falsely reporting "everything up to date".
- **Fix:** `CsPluginUpdateChecker` + `CsInstalledFingerprint` treat a VERSION or a CONTENT (SHA-256) mismatch as an update. Manifest fetching uses a robust mirror chain (raw → api.github.com → jsDelivr → Codeberg, 20 s budget); fallback data is flagged and the UI reports "could not check for updates" instead of "all up to date". The hyphenated repo name (`Kitsugi-Plugins`) check was fixed so the fallbacks actually run.
- **UI:** Content-only updates show "⟳ Rebuilt (v15)" / "⟳ Reinstall (v15)".
- **Tests:** `CsPluginUpdateCheckerTest`, `CloudstreamRepoMirrorTest`.

---

## 🇹🇷 Türkçe (v2.4.253)

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

## 🇬🇧 English (v2.4.253)

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

---

## 🇹🇷 Türkçe (v2.4.252)

### 🖼️ 1. Detay Sayfası Vitrin Görseli — Tam Kalite + Sayfa Açılmadan Önce Hazır
- **RGB_565 decode kapatıldı (asıl kalite sebebi):** Uygulamanın genel Coil `ImageLoader`'ı bellek tasarrufu için `allowRgb565(true)` ile kuruludur. RGB_565 kanal başına 5/6/5 bit olduğu için gökyüzü, sakura, ışık geçişleri gibi **yumuşak gradyanlarda bantlaşma (banding)** ve renk kaybı oluşuyordu — anime key visual'ları neredeyse tamamen gradyan olduğu için hasar gözle görülür seviyedeydi. Yeni `HeroArtworkImagePolicy` vitrin görsellerini `allowRgb565(false)` (tam 8-bit renk) ve ekrandan türetilmiş **sınırlı** ölçüde (en fazla 2560×1440 px) decode eder. Liste/grid kartları eskisi gibi RGB_565 ile devam eder.
- **Görsel planı sayfa açılmadan ÖNCE çözülüyor:** `KitsugiDetailHero` görsel planını kendi içinde ağ çağrısıyla çözüyordu; sayfa bu çözüm bitmeden render ediliyordu → kullanıcı önce düşük çözünürlüklü kaynak görselini görüp sonra TMDB/Fanart görseliyle **değişim** yaşıyordu. `ApiResultDetailViewModel.resolveHeroArtwork()` artık planı `_detailLoading = false` olmadan önce çözüyor (5 sn zaman tavanı, iptal güvenli) ve en kaliteli adayı decode ederek önbelleğe koyuyor; `KitsugiDetailHero` bunu `preResolvedArtwork` olarak **ilk kareden** kullanıyor.
- **Yatay modda vitrin büyütüldü:** Sol panel dar olduğu için 16:9 bant küçük kalıyordu. Yatayda bant artık 250dp taban ve ekran yüksekliğinin %72'si tavan ile hesaplanıyor.
- **Tam görünürlük garantisi (dikey + yatay):** `heroArtworkFit(..., HERO_BANNER_FIT_TOLERANCE = 1.20)` → 16:9 görsel + 16:9 bant kayıpsız; dikey poster veya ultra geniş afiş banttan saparsa **KIRPILMIYOR**, "tam görünür + yumuşak dolgu" ile gösteriliyor.

### ✅ 2. Testler
- `HeroImageSelectionTest`'e 3 yeni test: hedef ölçü sınırları (4K decode'ı bellek taşmasını engeller), 16:9 bant oranı ve dikey posterin hiç kırpılmaması.

---

## 🇬🇧 English (v2.4.252)

### 🖼️ 1. Detail Page Hero Artwork — Full Quality + Resolved BEFORE the Page Renders
- **RGB_565 decode disabled (primary quality killer):** App-wide Coil `ImageLoader` uses `allowRgb565(true)` for memory savings. RGB_565 is 5/6/5 bits per channel without alpha, causing visible banding in smooth anime gradients. `HeroArtworkImagePolicy` now forces `allowRgb565(false)` (full 8-bit color) with bounded screen size (max 2560×1440 px) for the hero. Grid/list cards remain on RGB_565.
- **Hero artwork plan resolved BEFORE page renders:** Previously resolved in-composition via `produceState`, causing a jarring pop/swap from low-res source image to TMDB/Fanart artwork. `ApiResultDetailViewModel.resolveHeroArtwork()` now resolves and preloads the best artwork prior to `_detailLoading = false`.
- **Enlarged landscape hero:** Landscape mode now uses a 250dp floor and up to 72% of screen height ceiling. Non-standard aspect ratios use soft ambient fill rather than cropping.
- **Full-visibility guarantee:** 16:9 artwork fits cleanly with `COVER`, while vertical posters or ultra-wide art adapt cleanly with `CONTAIN_WITH_FILL`.

### ✅ 2. Tests
- 3 new unit tests in `HeroImageSelectionTest` covering target size bounds, 16:9 cinematic ratio calculation, and zero cropping on vertical posters.

---

## 🇹🇷 Türkçe (v2.4.251)

### 🇹🇷 1. TMDB Türkçe Başlık Varyantı (TR-Only Boşluğu Kapandı)
- **Kök Neden:** Site SADECE Türkçe başlık taşıyorsa (`Örümcek Adam Eve Dönüş Yok`), detay sayfasının EN/Romaji/Native başlıklarıyla eşleşme sağlanamıyor ve `EŞLEŞME YOK` ile sonuçlanıyordu.
- **Çözüm:** `runGetStreams`, çözümlenmiş TMDB kimliğiyle `tr-TR` başlığı çekip (`TmdbTitleLocalizer`) arama varyantlarına ve eşleştirici adaylarına ekler.
- **Önbellek & Performans:** 164 eklenti tek bir TMDB isteğini paylaşır; sonuçlar ve negatif yanıtlar oturum boyunca önbelleğe alınır. Hata/ağ kopması durumunda normal listeyle sessizce devam edilir.

### ⚡ 2. Paralel ID Çözümleme Fırtınasının Engellenmesi
- **Kök Neden:** Her eklenti için (164 paralel) ARM+TMDB ID çözümlemesi yapılıyordu — eklenti başına 8'e kadar ardışık HTTP isteği gidiyor, İzle'ye her basışta ~1000 istek oluşuyor ve 75 saniyelik sağlayıcı bütçesi arama başlamadan tükeniyordu.
- **Çözüm:** `StreamViewModel` üzerinden çözülen kimlikler tüm eklentilere doğrudan aktarılır (`preResolvedIds`). `KitsugiIdResolver.resolveIds` için sonuç önbelleği (`resolvedIdsCache`) eklendi. Ana akışta eklenti başına mükerrer ağ isteği sıfıra indirildi.

### 🎬 3. Anime Tip Filtresi Esnetildi
- Anime detay sayfasında dizi/film eklentileri *"Bu eklenti anime içeriklerini desteklemiyor"* denilerek eleniyordu; oysa yerli dizi siteleri dublajlı anime, film siteleri anime filmleri barındırabiliyor. Filtre gevşetildi; alakasız sonuçlar zaten `CsTitleMatcher` tarafından elenir.

### 🛡️ 4. Başlık Eşleştirici (CsTitleMatcher) & ProGuard Düzeltmesi
- **CsTitleMatcher 1.00 Reddi Giderildi:** Bilinen başlığa benzerliği $\ge 0.90$ olan adayların token filtresi tarafından elenmesi engellendi. Çok dilli veya Japonca ek içeren takma adların (ör. *SUPERNATURAL スーパーナチュラル*) net Latin eşleşmelerini (`Supernatural`) elemesi çözüldü.
- **ProGuard / Gson Keep Kuralı:** CS3 eklentilerinin (ör. Torrential) Gson sınıflarını doğrudan isimle araması nedeniyle R8'in yeniden adlandırmasından kaynaklanan `NoClassDefFoundError: Lcom/google/gson/Gson` hatası keep kurallarıyla giderildi.

### 🧪 5. Eklenti Tanı Koşucusu — Faz 2g (Gerçek Akış / Flow Testi)
- Tanı ekranına her eklentinin KENDİ döndürdüğü başlığı otomatik bulma ve sezon/bölüm eşleştirme hattından geçiren Faz 2g eklendi. Tanı tablosuna `Flow` sütunu eklendi (`🔁✅` / `🔁❌`).

---

## 🇬🇧 English (v2.4.251)

### 🇹🇷 1. TMDB Turkish Title Variant (Closing the TR-Only Gap)
- **Root Cause:** If a Turkish provider carries ONLY the local Turkish title (e.g. `Örümcek Adam Eve Dönüş Yok`), matching against English/Romaji/Native titles was failing (`NO MATCH`).
- **Fix:** `runGetStreams` retrieves the `tr-TR` title using the resolved TMDB ID via `TmdbTitleLocalizer` and injects it into query variants and matcher candidates.
- **Caching:** 164 plugins share a single TMDB request with session caching for both hits and misses, falling back safely without request storms.

### ⚡ 2. Eliminating the Parallel ID Resolution Storm
- **Root Cause:** 164 parallel plugins were each triggering separate ARM+TMDB ID lookups (up to 8 consecutive HTTP calls per plugin), wasting the 75-second timeout budget before searches began.
- **Fix:** Pre-resolved IDs (`preResolvedIds`) are now passed directly from `StreamViewModel`. Added `resolvedIdsCache` in `KitsugiIdResolver`. Duplicate per-plugin network requests dropped to zero.

### 🎬 3. Flexible Anime Type Filter
- Allowed Turkish TV series and movie providers to participate in anime searches, enabling access to Turkish dubbed anime and anime movies hosted on general streaming sites.

### 🛡️ 4. Title Matcher Precision & ProGuard Gson Keep
- **Exact Match Guard:** High-confidence candidate matches ($\ge 0.90$) are now protected from being discarded by multi-script alias noise (e.g. bilingual titles).
- **Gson ProGuard Rules:** Added `-keep` rules for `com.google.gson.**` to prevent `NoClassDefFoundError: Lcom/google/gson/Gson` in plugins like Torrential.

### 🧪 5. Plugin Diagnostics — Phase 2g Real Flow Testing
- Diagnostic runner now tests the complete real stream discovery pipeline using the plugin's own returned title, adding a `Flow` column (`🔁✅` / `🔁❌`) in the report and screen.

---

## 🇹🇷 Türkçe (v2.4.250)

### 🎬 1. Detay Sayfaları — Her İki Yönde de 16:9 Sinematik Bant (POSTER VİTRİN YOK)
- **Vitrin artık her zaman 16:9 sinematik banttır:** Detay sayfası dikey modda da, yatay modda da **aynı tasarımı** kullanır. Poster artık vitrin paneli olarak kullanılmaz.
- **Bant kırpılmaz:** En kaliteli **YATAY** görsel (TMDB / Fanart.tv / galeri arka planları) 16:9 bantta **kesilmeden** gösterilir. Görselin oranı banttan belirgin farklıysa (~%20'den fazla kırpma gerekiyorsa) kırpmak yerine "tam görünür + yumuşak dolgu" devreye girer — ör. çok geniş bir sinema afişi veya dikey bir poster bandın içinde tamamı görünür.
- **En kalitelisi otomatik seçilir:** Aday sırası kalite sıralıdır → çözümleyicinin TMDB/Fanart.tv sıralaması (4K > 2K > 1080p, yazısız, oylu) → kaynağın kendi arka planı → galeri yatay görselleri (çözünürlüğe göre).
- **Boyut uyumu:** Bant yüksekliği panel genişliğinden türetilir (`genişlik × 9/16`) ve ekran yüksekliğinin %62'sini geçmez → telefon, tablet, TV ve hem dikey hem yatay yönde aynı görünüm.
- **Başlık/meta bandın altında:** Logo/başlık, format-tür sayaç satırı, yayın bilgisi ve rozetler (kaynak, yıl, puan, favori, listede, +18) bandın altındaki bilgi alanına akar.
- **Kütüphane (MediaEntry) detay sayfası** da birebir aynı mantığa geçti: en kaliteli yatay galeri görseli bantta, yoksa poster bandın içinde kesilmeden.

### 🖼️ 2. Keşfet Vitrini (Hero) Yatay/Dikey Uyumu
- **Yatay modda bant büyütüldü:** Yatay telefon vitrini ekran yüksekliğinin ~%74'ü (tablet ~%62). 16:9 kapak görselleri artık ince bir şeride kırpılmıyor; görselin yaklaşık %86'sı görünüyor.
- **Dinamik kırpma hizalaması:** Görselin **gerçek** en-boy oranı okunup kırpma penceresi ona göre hesaplanıyor; görselin üstünden yalnızca ~%5 pay kesiliyor ve yatayda tam ortalanıyor → karakterlerin kafası/yüzü kesilmiyor, görsel fazla yukarı kaymıyor.
- **Karartma (scrim) azaltıldı:** Üst üste binen gradyanlar yumuşatıldı; görsel net ve canlı.
- **Manga yatayda tam ortada:** Dikey poster (manga) geniş yatay vitrinde sağa yaslanmıyor; ekranın **tam ortasında**, olabildiğince büyük ve kesilmeden gösteriliyor (arkasında yumuşak atmosferik dolgu).

### ✅ 3. Testler
- `HeroImageSelectionTest`: dinamik hizalama, oran/uyum (COVER ↔ CONTAIN_WITH_FILL) ve cihaz/yön bazlı bant yüksekliği testleri.

---

## 🇬🇧 English (v2.4.250)

### 🎬 1. Detail Pages — 16:9 Cinematic Band in BOTH Orientations (NO poster hero)
- **The hero is always a 16:9 cinematic band:** the detail page uses the **same layout** in portrait and landscape. Vertical posters are no longer used as the hero panel.
- **The band is never sliced:** the highest-quality **WIDE** artwork (TMDB / Fanart.tv / gallery backdrops) is shown **uncropped**. When the artwork ratio differs too much from the band (would require >~20% cropping) the app switches to "fully visible + soft fill" — e.g. an ultra-wide key visual or a vertical poster is shown complete inside the band.
- **Best quality auto-selected:** candidates are quality-ordered → resolver's TMDB/Fanart.tv ranking (4K > 2K > 1080p, textless, voted) → native backdrop → wide gallery images (by resolution).
- **Device fit:** band height derives from the panel width (`width × 9/16`) and never exceeds 62% of screen height → identical look on phones, tablets, TV, portrait and landscape.
- **Title/metadata below the band:** logo/title, format & count row, airing info and badges (source, year, score, favourite, in-list, +18) flow into the info area under the band.
- **Library (MediaEntry) detail page** follows the exact same rule: best wide gallery artwork in the band, otherwise the poster shown complete inside the band.

### 🖼️ 2. Explore Hero Landscape/Portrait Fit
- **Taller landscape band:** landscape phone hero now uses ~74% of the screen height (tablet ~62%); 16:9 key visuals are no longer cropped into a sliver — about 86% of the artwork is visible.
- **Dynamic crop alignment:** the artwork's **real** aspect ratio drives the crop window; only ~5% is trimmed from the top and the image is horizontally centred.
- **Lighter scrims:** overlapping gradients softened; the artwork is crisp and vivid.
- **Manga centred in landscape:** vertical posters are no longer pushed to the right — they sit **dead centre**, as large as possible and uncropped, over a soft ambient fill.

### ✅ 3. Tests
- `HeroImageSelectionTest`: dynamic alignment, fit (COVER ↔ CONTAIN_WITH_FILL) and device/orientation band-height rules.

---

## 🇹🇷 Türkçe (v2.4.249)

### 🖼️ 1. Simkl Görsel Logo Mantığı (Vitrin / Hero & Detay Ekranları)
- **Eksik Çapraz Kimlik (Cross-ID) Çözümlemesi:** `SimklApiClient` keşfet şeritlerindeki Simkl öğelerini `malId = simklId, tmdbId = null, realMalId = null` olarak ürettiğinde vitrin ve detay sayfalarında logoların bulunamaması giderildi. `KitsugiHeroSection.fetchHeroLogo`, `KitsugiHeroArtworkResolver.computeArtworkInternal`, `ApiResultDetailViewModel.fetchLogo` ve `MediaEntryDetailViewModel.fetchLogo` içine Simkl kimliğinden TMDB / MAL / AniList / Kitsu kimliklerini çözen `KitsugiSimklDetailClient.resolveSimklCrossIds` çağrısı entegre edildi (`tmdbId` → `realMalId` → `aniListId` → `kitsuId` zinciri).
- **Hero Artwork Kimliklerinin Vitrine Aktarımı:** `ExploreScreen.kt` (`displayHeroItems`), `HeroArtwork` üzerinden çözülen 16:9 `backdropUrl` ve `posterUrl` görsellerini öğeye kopyalarken `artwork.resolvedTmdbId` ve `artwork.resolvedMalId` alanlarını da vitrin öğesine aktarır. Ayrıca `KitsugiHeroSection` içindeki `logos` durumu `pagerKey` ile bağlandı ve boş logoların `enrichHeroBackdrops` tamamlandıktan sonra otomatik olarak yeniden denenmesi sağlandı.
- **Romaji Başlık & Çok Sezonlu Yıl Toleransı:** Simkl yalnızca Romaji başlık döndürdüğünde (ör. *Re:Zero kara Hajimeru Isekai Seikatsu*, 2026) TMDB kaydı İngilizce `name` + Japonca (CJK) `original_name` ve ilk sezon yılı (2016) taşıdığı için eşleşmenin reddedilmesi sorunu çözüldü. CJK orijinal başlıklı animasyon kayıtları için Romaji başlık uyumluluğu eklendi ve çok sezonlu TV animelerinde ilk sezon yılı ile devam sezonu yılı arasındaki fark için tolerans tanımlandı.
- **Detay Ekranları Çözümleme Zinciri:** Detay sayfası `fetchLogo` akışında Simkl çapraz kimlik çözümlemesi ve `explicitTmdb` eşleşmediğinde kaynak bazlı yedek logo arama zinciri (`TMDB` → `MAL` → `AniList` → `Kitsu`) aktif edildi.

### 📜 2. Kitsu Keşfet ("Tümünü Gör") Sayfalama ve Sonsuz Kaydırma Sorunu
- **Typesense 1-Tabanlı Sayfa Kayması:** Kitsu'nun sunucu arka ucu (`hummingbird-me/kitsu-server`), `filter[...]` parametresi içeren tüm `/anime` ve `/manga` sorgularını (`filter[status]=current`, `filter[status]=upcoming`, `filter[subtype]=movie`, `filter[season]=...`) `TypesenseSearchService` üzerinden çalıştırır. Sunucudaki `_page = (@_offset / @_limit).floor` hesabı `+ 1` eklemediği için 1-tabanlı Typesense'de `page[offset]=0` ve `page[offset]=20` aynı 1. sayfayı döndürüyordu ve liste 20. öğede kilitleniyordu. `KitsuExploreClient.kt` içine `offset > 0` için Typesense 1-tabanlı sayfa kaymasını telafi eden `adjustKitsuTypesenseQuery` (`offset + limit`) eklendi; artık 20. öğe sonrasında yeni sayfalar kesintisiz yükleniyor.
- **Trend Animeler Ucu:** Kitsu'nun `/trending/anime` ucunun sayfalama parametrelerini yok sayıp sabit 10 kayıt döndürmesi problemi çözüldü: ilk sayfada `/trending/anime` sonuçları popüler yayındaki animelerle (`airingAnime` / `topAnime`) 20 öğeye tamamlanır ve sonraki sayfalarda (`offset > 0`) kesintisiz devam eder.
- **KitsuApiHost ve Sonsuz Kaydırma Bekçisi:** Kanonik `https://kitsu.app/api/edge` adresi kalıcı olarak birincil tutuldu; `kitsu.io` yalnızca istek bazında yedek olarak bırakıldı. `FullScreenMediaGridPage.kt` ve `SearchScreen.kt` içinde filtre/sıralama değişimlerinde `autoLoadBudget` ile `lastAutoLoadVisibleCount` sıfırlaması ve `LaunchedEffect` tetikleyici anahtarları düzeltildi.

### 🧪 3. Birim Testleri
- `KitsuPaginationAndSimklLogoTest` ile Kitsu Typesense offset telafisi, kanonik sayfa anahtarları, `KitsuApiHost` önceliği ve Simkl vitrin logo politikası test edilip doğrulandı.

---

## 🇬🇧 English (v2.4.249)

### 🖼️ 1. Simkl Hero & Detail Visual Logos
- **Cross-ID Resolution:** When `SimklApiClient` outputs shelf items with `malId = simklId, tmdbId = null, realMalId = null`, logos were previously failing to resolve. Integrated `KitsugiSimklDetailClient.resolveSimklCrossIds` across `KitsugiHeroSection.fetchHeroLogo`, `KitsugiHeroArtworkResolver.computeArtworkInternal`, `ApiResultDetailViewModel.fetchLogo`, and `MediaEntryDetailViewModel.fetchLogo` (`tmdbId` → `realMalId` → `aniListId` → `kitsuId` chain).
- **Passing Artwork IDs to Hero:** `ExploreScreen.kt` (`displayHeroItems`) now copies `artwork.resolvedTmdbId` and `artwork.resolvedMalId` from `HeroArtwork` into hero items. Furthermore, `logos` state in `KitsugiHeroSection` is keyed to `pagerKey` and automatically retries blank logos once `enrichHeroBackdrops` completes.
- **Romaji Titles & Multi-Season TV Year Discrepancy:** When Simkl returns Romaji-only titles (e.g. *Re:Zero kara Hajimeru Isekai Seikatsu*, 2026), TMDB records carry English `name` + Japanese CJK `original_name` and the initial season release year (2016). Added CJK animation fallback in `TmdbArtworkIdentity.matches` and multi-season year tolerance in `KitsugiHeroArtworkResolver.kt` so sequels match TMDB entries reliably.
- **Detail Screen Logo Fallbacks:** Full fallback chain (`TMDB` → `MAL` → `AniList` → `Kitsu`) activated in `ApiResultDetailViewModel` and `MediaEntryDetailViewModel` when explicit TMDB entries are absent.

### 📜 2. Kitsu Explore "View All" Pagination & Infinite Scroll
- **Typesense 1-Based Page Offset Shift:** Kitsu's backend (`hummingbird-me/kitsu-server`) routes all `/anime` and `/manga` queries containing `filter[...]` to `TypesenseSearchService`. Due to missing `+ 1` in its offset-to-page calculation, `page[offset]=0` and `page[offset]=20` returned the exact same page 1, causing infinite scroll to stop at item 20. Added `adjustKitsuTypesenseQuery` (`offset + limit`) for `offset > 0` on filtered endpoints in `KitsuExploreClient.kt`, enabling seamless infinite scrolling past item 20.
- **Trending Anime Pagination:** Addressed Kitsu's `/trending/anime` endpoint ignoring pagination by backfilling page 1 with currently airing/popular anime to 20 items and cleanly paging subsequent offsets without interruption.
- **Canonical Host Priority & Scroll Budget Reset:** Preserved `https://kitsu.app/api/edge` as the permanent primary host. Fixed `autoLoadBudget` and `lastAutoLoadVisibleCount` resetting upon filter/sort modifications in `FullScreenMediaGridPage.kt` and `SearchScreen.kt`.

### 🧪 3. Unit Tests
- Added `KitsuPaginationAndSimklLogoTest` validating Typesense query adjustment, canonical query anchors, `KitsuApiHost` priority, and hero logo display policies.

---

## 🇹🇷 Türkçe (v2.4.248)

### 🖼️ 1. Simkl Vitrin (Hero) Logoları ve Kimlik Ayrıştırması
- **Kök neden:** Simkl liste/şerit kayıtları anime için yalnızca **romaji başlık** taşır (`Sousou no Frieren`, `Re:Zero kara Hajimeru Isekai Seikatsu`); TMDB kayıtları ise İngilizce/Japonca adlandırılır. `KitsugiEpisodeRatingsRepository.getLogoUrl`, `expectedTitles` ile TMDB kaydını başlık üzerinden doğruluyor ve iki türden (film + dizi) hiçbiri eşleşmezse logoyu vermiyordu. Romaji başlıklar TMDB başlıklarıyla eşleşmediği için Simkl öğeleri bu doğrulamaya takılıp vitrinde düz metin başlık gösteriyordu (logo ayarı açık olsa bile).
- **Düzeltme (vitrin):** `KitsugiHeroSection.fetchHeroLogo` Simkl dalında Simkl'ın kendi çözdüğü kimlikler (`ids.tmdb` / `ids.mal`) otorite kabul edilir ve başlık doğrulaması uygulanmaz — `KitsugiHeroArtworkResolver`'daki `canonicalSource == "tmdb"` kuralıyla aynı yaklaşım.
- **Çapraz kimlik yedeği:** Simkl liste yanıtı kimlik taşımıyorsa vitrin artık Simkl `extended=full` kaydından MAL/TMDB/AniList/Kitsu kimliklerini çözer (Bangumi `resolveCrossIds` deseninin Simkl karşılığı). Çözülen kaydın kendi başlık alanları öğe başlıklarıyla kesişmiyorsa logo istenmez (uydurma/yanlış kimlik koruması).
- **Kimlik okuma düzeltmesi (`SimklApiClient`):** Keşfet raflarını besleyen `getBestMedia` yanıtlarında Simkl kimliği `ids.simkl_id` veya `ids.simkl`, TMDB kimliği `ids.tmdb` veya `ids.tmdb_id`, MAL kimliği sayı veya metin (`"mal": "52991"`) olabilir. Ayrıştırma tek bir test edilebilir işleve taşındı (`parseBestMediaArray`) ve tüm varyantlar okunuyor.
- **Detay sayfaları:** Aynı yanlış negatif Simkl detay sayfası logo aramasını da engelliyordu; `ApiResultDetailViewModel` ve `MediaEntryDetailViewModel` Simkl dalı vitrindeki kuralla hizalandı. Diğer kaynakların (MAL/AniList/Kitsu/Bangumi/TMDB) davranışı değişmedi.
- **Testler:** `SimklExploreParsingTest` (6/6 geçti) — `simkl_id`/`simkl`, `tmdb`/`tmdb_id`, metin `mal`, CJK başlığın Latin alternatife çevrilmesi, anime film "Film" ipucu, poster/fanart CDN adresleri ve kimliksiz kayıtta geçici kimliğin MAL/TMDB sanılmaması.

### 🔍 2. Ayarlar Ekranı Canlı Arama ve Hızlı Gezinme (`SettingsSearch.kt`)
- **Akıllı Arama:** Ayarlar ana sayfasına gerçek zamanlı arama alanı eklendi (`SettingsSearchField`). Türkçe karakterler (`ı`/`i`, `ğ`/`g`, `ş`/`s`) ve aksanlar normalize edilir.
- **Derin Bağlantı:** Arama sonucu yalnızca ilgili ayar sayfasına değil, doğrudan hedef alt sekmeye (ör. *DoH / DNS*, *Manga Okuyucu*, *Altyazı Ayarları*) yönlendirir.
- **Mantıksal Kategoriler:** Ayarlar ekranı Hesap, Uygulama, Kaynaklar & Entegrasyonlar, Veri & Depolama ve Destek & Tanılama bölümlerine ayrıldı.

### 🎨 3. Ortak Sekme Kapsülleri (`KitsugiSettingsSegmentedTabs`) ve Alt Sayfa Uyumlaması
- Görünüm, Eklentiler, Entegrasyonlar ve Sistem sekmeleri modern, ikonlu ve vurgulu `KitsugiSettingsSegmentedTabs` bileşeninde birleştirildi.
- Eklenti sekme adları İngilizce ve Türkçe kaynaklardan alınacak şekilde yerelleştirildi.
- Alt sayfalarda sistem gezinme çubuğu (`navigationBarsPadding`) güvenli alanı eklendi; oynatıcı alt sayfalarında başlık seçili ekrana göre dinamikleşti.
- Depolama ekranındaki iç içe geçmiş kart yapısı sadeleştirildi; İndirilenler (Video, Görsel, Altyazı) yönetimi güvenle korundu.

### 🎬 4. Detay Sayfaları Sinematik Yüklenme Ekranı
- Medya, karakter, ekip/personel, stüdyo ve Android TV detay sayfalarında tam ekran sinematik yüklenme deneyimi yeniden bağlandı (`KitsugiCinematicLoadingScreen`).
- Başlık/ad, kapak görseli ve kaynak rozeti (`KitsugiSourceNamePill`) anında gösterilir. Medya logosu varsa getirilir.
- TMDB stüdyo/ağ varyantları (`tmdb_network`, `tmdb-network`, `tmdbnetwork`) TMDB logosu ve adıyla doğru eşleştirildi.
- Yatay kompakt ekran boyutlandırması ve GPU çökmesini önleyen katman sadeleştirmesi yapıldı.

### 🌄 5. Detay Vitrini Akıllı Görsel Seçimi ve Çözünürlük Sıralaması
- API detay sayfalarında üst görsel ekran boyutuna/yönüne göre seçilir (dikey telefonlarda poster, yatay telefon/tablet/TV'de arka plan, mangada poster).
- TMDB, Fanart.tv ve galeri adayları dikey/yatay yön havuzlarına ayrılıp çözünürlüğe göre sıralanır.
- Görsel yüklenemezse sıradaki adaya otomatik geçilir; geniş ekranda dikey görsel için kesmek yerine çift katmanlı contain kullanılır.

### 📋 6. Platform Hata Kodları Kataloğu (Ayarlar > Geliştirici & Tanılama)
- Ayarlar'daki "Platform Hata Kodları" ekranı (`PlatformErrorCatalogScreen`) artık AniList, MyAnimeList, Kitsu, Shikimori, Bangumi, TMDB, Simkl, Manga/CloudStream ve ortak (`KTS-COM`) hata kodlarının tamamını kapsıyor; filtre çipleri ve arama desteği sunuyor.

### 🌐 7. Anında Dil Değişimi ve Kitsugi Hesabı İyileştirmeleri
- `AndroidManifest.xml`'den `locale|layoutDirection` configChanges girdisi kaldırıldı; dil değiştiğinde Activity anında yeniden kurulur (API 33+ güvenlik tetikleyicili).
- Kitsugi hesabı ekranına 3 bağımsız aç/kapa anahtarı eklendi: "Şifreli hesap yedeği", "Arama geçmişi eşitleme", "Bildirim arşivi eşitleme".
- Kasa açılamazsa şifre bellekte tutulur, tek tıkla tekrar denenebilir.

---

## 🇬🇧 English (v2.4.248)

### 🖼️ 1. Simkl Hero Logos and Identity Parsing
- **Root cause:** Simkl list/shelf records carry **romaji-only** anime titles (`Sousou no Frieren`, `Re:Zero kara Hajimeru Isekai Seikatsu`) while TMDB records are named in English/Japanese. `KitsugiEpisodeRatingsRepository.getLogoUrl` verifies the TMDB record through `expectedTitles` and refuses to return a logo when neither the movie nor the TV record matches. Romaji titles never matched, so Simkl entries always fell back to plain text titles in the hero (even with the logo setting enabled).
- **Fix (hero):** In the Simkl branch of `KitsugiHeroSection.fetchHeroLogo`, identities resolved by Simkl itself (`ids.tmdb` / `ids.mal`) are treated as authoritative and skip the title verification — the same approach as the existing `canonicalSource == "tmdb"` rule in `KitsugiHeroArtworkResolver`.
- **Cross-ID fallback:** When a Simkl shelf record carries no IDs, the hero resolves MAL/TMDB/AniList/Kitsu identities from the Simkl `extended=full` record. A synthetic/wrong-ID guard rejects the record unless its own title fields intersect the item titles.
- **ID parsing fix (`SimklApiClient`):** In `getBestMedia` responses the Simkl ID may be `ids.simkl_id` or `ids.simkl`, the TMDB ID `ids.tmdb` or `ids.tmdb_id`, and the MAL ID a number or a string (`"mal": "52991"`). Parsing now lives in one testable function (`parseBestMediaArray`) that reads every variant.
- **Detail pages:** The same false negative also blocked the Simkl detail-page title logo; the Simkl branch of `ApiResultDetailViewModel` and `MediaEntryDetailViewModel` now follows the hero rule. Every other source (MAL/AniList/Kitsu/Bangumi/TMDB) keeps its current behaviour.
- **Tests:** `SimklExploreParsingTest` (6/6 passing) covers `simkl_id`/`simkl`, `tmdb`/`tmdb_id`, string `mal`, CJK-to-Latin title resolution, anime movie "Film" hint, Simkl CDN poster/fanart URLs, and placeholder ID safety.

### 🔍 2. Real-Time Settings Search and Deep-Linking (`SettingsSearch.kt`)
- **Smart Search:** Live search bar added to Settings (`SettingsSearchField`) with accent- and case-insensitive normalization across Turkish and English.
- **Deep Navigation:** Results navigate directly to target tabs or subpages (e.g. *DoH / DNS*, *Manga Reader*, *Subtitle Settings*).
- **Logical Organization:** Settings categorized cleanly into Account, App, Sources & Integrations, Data & Storage, and Support & Diagnostics.

### 🎨 3. Unified Segmented Tabs (`KitsugiSettingsSegmentedTabs`) & Subpage Polish
- Preferences, Addons, Integrations, and System tabs unified under the modern, icon-accented `KitsugiSettingsSegmentedTabs` component.
- Addon tab labels localized in English and Turkish.
- Subpages respect navigation bar insets (`navigationBarsPadding`), player subscreen headers dynamically update, and redundant embedded "OK" footers removed.
- Storage card hierarchy simplified while preserving Downloads management.

### 🎬 4. Detail Pages Cinematic Loading Screen
- Fullscreen cinematic loading experience restored across media, character, staff, studio, and Android TV detail screens (`KitsugiCinematicLoadingScreen`).
- Title/name, cover artwork, and provider badge (`KitsugiSourceNamePill`) appear immediately while detail queries load.
- TMDB network identifiers correctly map to TMDB logo and brand.
- Responsive scaling for compact landscape screens and duplicate blur layers removed to prevent GPU crashes.

### 🌄 5. Detail Hero Adaptive Visual Resolution
- Top visual on API detail pages selects between poster and backdrop based on screen size and orientation (portrait phone: poster; landscape phone/tablet/TV: backdrop; manga: poster).
- Candidates aggregated from TMDB, Fanart.tv, and gallery items, ranked strictly by resolution.
- Automatic fallback to next candidate if an image fails to load. Dual-layer contain prevents aggressive cropping of vertical artwork on wide screens.

### 📋 6. Complete Platform Error Codes Catalog
- The "Platform Error Codes" screen (`PlatformErrorCatalogScreen`) catalogs AniList, MyAnimeList, Kitsu, Shikimori, Bangumi, TMDB, Simkl, Manga/CloudStream, and common (`KTS-COM`) error codes with filter chips and search in TR/EN.

### 🌐 7. Instant Language Switching & Account UX
- Removed `locale|layoutDirection` from `AndroidManifest.xml` so the activity immediately recreates upon language change. Safety recreate watchdog added for API 33+.
- Kitsugi account screen adds 3 local toggle switches for Encrypted backup, Search history sync, and Notification archive sync.
- In-memory vault password retry and locale-agnostic verification tracking.

---

## 🇹🇷 Türkçe (v2.4.247)

### 🌐 1. Kapsamlı İngilizce Dil Desteği & Arayüz Metinlerinin Yerelleştirilmesi
- **Gömülü (Hardcoded) Türkçe Metinlerin Temizlenmesi:** Kod içerisine doğrudan gömülü kalmış olan yüzlerce Türkçe arayüz metni, Android kaynak sistemine (`R.string`) taşındı. İngilizce dil seçildiğinde Türkçe kalan diyaloglar, ayarlar ve profil sekmeleri tamamen giderildi.
- **41 Dosyada Yerelleştirme Entegrasyonu:** `AuthViewModel`, `KitsugiProfileViewModel`, `AccountSettingsSubPages`, `DownloadsScreen`, `MangaExtensionTab`, `MalProfileExtras`, profil sayfaları (`AniList`, `Bangumi`, `MAL`, `Shikimori`, `Kitsu`, `Simkl`), CrossSync tanı raporları ve bildirim bileşenleri arayüz diline duyarlı hale getirildi.
- **Dinamik Tarih & Göreli Zaman Desteği:** Profil geçmişlerindeki "az önce", "5 dk önce", "3 saat önce", "Dün" gibi zaman ifadeleri ve tarih biçimlendirmeleri (`DateTimeFormatter`) kullanıcının seçtiği yerel ayarlara (`KitsugiStrings.locale()`) bağlandı.
- **Dil Dosyaları Paritesi:** `values/strings.xml` ve `values-en/strings.xml` kaynaklarına 381 yeni simetrik anahtar eklendi; dize kaçış karakterleri (`\'`) AAPT uyumlu olarak standardize edildi.

### 🏢 2. Dinamik Bangumi Kurum Çözümleyici (`BangumiOrgResolver.kt`)
- **API Tabanlı Kurum Adı Çözümleme:** Bangumi infobox alanlarından serbest metin olarak gelen şirket adları (`株式会社マッパ`, `集英社`, vb.), Bangumi'nin resmi `search/persons` (type=2 / Şirket) API ucu üzerinden otomatik olarak küresel adına (`MAPPA`, `Shueisha`, vb.) çözülür.
- **Kesintisiz & Süre Korumalı Mimari:** Kurum çözümleme işlemi 2,5 saniye tavan süre ve negatif önbellek desteğiyle arka planda yürütülür; ağ gecikmelerinde veya bilinmeyen kayıtlarda orijinal ad korunur, sayfa açılışı asla takılmaz.
- **Arama Türü Filtresi (`BangumiApiClient.kt`):** `searchPersons` fonksiyonuna `type` (1=kişi, 2=şirket, 3=grup) filtreleme parametresi eklendi.

### 🎭 3. Bangumi Kadro ve Karakter Rolleri Sözlüğü (`BangumiRoleDictionary.kt`)
- **Kadro ve Rol Çevirisi:** Bangumi API'lerinden ham Çince/Japonca gelen görev ve karakter etiketleri (`主角`, `配角`, `原作`, `监督`, `脚本`...) arayüz diline (TR/EN) uygun olarak yerelleştirildi.
- **Simetrik Rol Kaynakları:** `bangumi_role_*` ve `bangumi_org_*` tanımları için 154 yeni kaynak anahtarı dil dosyalarına eklendi (toplam 6.944 dize %100 parite).

### 🖼️ 4. Vitrin (Hero Section) TMDB + Fanart.tv Görsel Çözümleyici & Akıllı Ekran Uyumu
- **Tüm Kaynaklar İçin TMDB + Fanart.tv Çözümleyici (`KitsugiHeroArtworkResolver.kt`):** Bangumi, AniList, MyAnimeList (Jikan), Kitsu, Shikimori, Simkl, TMDB, Tümü modu, TV vitrini ve Eklenti (Addon) vitrinleri dahil her vitrin öğesi için hem TMDB (`images`, `external_ids`, `alternative_titles`) hem de Fanart.tv (`tvposter`/`movieposter`, `showbackground`/`moviebackground`) paralel olarak sorgulanır.
- **En Kaliteli Dikey Poster & Yatay Arka Plan Otomatik Seçimi:**
  - **Dikey Poster:** TMDB (/original 2000x3000 / 1000x1500) ve Fanart.tv (1000x1426) adayları çözünürlük, 2:3 dikey oran, dil tercihi, sezon uyumu ve topluluk oylarına göre puanlanarak en yüksek kaliteli dikey poster seçilir.
  - **Yatay Arka Plan:** TMDB (/original 4K 3840x2160 / 1080p) ve Fanart.tv adayları çözünürlük (4K > 2K > 1080p), 16:9 sinematik oran, yazısız (textless) temizlik ve oylara göre puanlanarak en yüksek kaliteli yatay arka plan seçilir.
- **Kaynak Bazlı Hatalı/Düşük Çözünürlüklü Görsel Düzeltmeleri:**
  - **Bangumi:** API'nin yalnızca dikey kapak döndürmesine rağmen bunun backdropUrl sanılması düzeltildi; tam boy large (`/pic/cover/l/`) kapak birincil yapıldı.
  - **Kitsu & Shikimori & TMDB:** Kitsu'da medium yerine large/original poster ve cover önceliklendirildi; Shikimori vitrin posterleri fullUrl (originalUrl) sürümüne yükseltildi; TMDB /w500 ve /w1280 adresleri vitrinde /original tam çözünürlüğe yükseltildi.
- **Cihaz Boyutu ve Ekran Yönü Uyumu (`KitsugiHeroSection.kt`):**
  - **Dikey Telefon (`aspect < 1.10`, dikey mod):** Vitrinde birincil olarak en kaliteli dikey poster (`posterUrl`), yedek olarak yatay arka plan kullanılır.
  - **Yatay Telefon, Tablet ve TV (`aspect >= 1.10` veya yatay mod):** Vitrinde birincil olarak en kaliteli 16:9 yatay arka plan (`backdropUrl`), yedek olarak dikey poster kullanılır.
- **Akıllı Hizalama & Çift Katmanlı Kesilme Koruması:**
  - Geniş ekranlarda 16:9 arka planlar ve dikey posterler karakterlerin başını kesmeyecek şekilde üst-orta odaklama (`BiasAlignment(0f, -0.32f)` / `BiasAlignment(0f, -0.25f)`) ile konumlandırılır.
  - Geniş vitrin alanında yalnızca dikey görsel bulunan yapımlarda arkada yumuşak atmosferik dolgu (`alpha 0.30`) + önde `ContentScale.Fit` ile kesilmemiş tam boy dikey görsel sergilenir.
- **Berrak Görünüm (Scrim İyileştirmesi):** Üst üste binen 3 katmanlı koyu gradyan kaldırıldı; görselin üst %60'lık alanı tamamen berrak ve açık bırakıldı. Dikey tablette sol yarıyı karartan yatay gradyan kaldırıldı (yalnızca yatay modda metin okunurluğu için hafifletilmiş geçiş uygulanır).

### 🧩 5. CloudStream Eklenti Çalışma Zamanı ve Tanı Düzeltmeleri
- **Dinamik eklenti ABI'si:** Serialization runtime'ı artık CS3 eklentilerinin bağlandığı host API ile aynı classloader parent'ı üzerinden görünür. `kotlinx.serialization-core` doğrudan bağımlılık olarak eklenip R8 tarafından silinmeye/yeniden adlandırılmaya karşı korunur; bu, raporlarda görülen `KSerializer` `NoClassDefFoundError` sorununu hedefler.
- **Yükleme ön kontrolü:** Eklenti çalıştırılmadan önce `MainAPI`, `KSerializer` ve Serialization `Json` sınıflarının plugin classloader'ından görülebildiği doğrulanır; eksikler canlı tanı izine yazılır.
- **Daha doğru hata tanısı:** Linkage/çalışma zamanı uyumsuzluğu (`KTS-CSP-9110`) ve hesap gereksinimi (`KTS-CSP-9109`) ayrı hata kodları ve tanı sayaçları aldı. HTTP 401 ile raporda görülen giriş gereksinimi metinleri artık yanlışlıkla boş arama olarak gösterilmez; genel HTTP 403 ise Cloudflare kanıtı sayılmaz.
- **Parser imzaları:** Yanıttaki bozuk/uyumsuz içerik belirtileri ve HLS başlık hataları parser kategorisine eklenmiştir.
- **Testler:** Error classifier ve plugin diagnostic runner için çalışma zamanı uyumsuzluğu, kimlik doğrulama ve özet sayaç testleri eklendi.

> Alan adı/DNS erişilebilirliği ve sağlayıcı hesabı gereksinimleri uzak sitelerin durumuna bağlıdır; doğrulanmamış eklenti alan adları bu sürümde tahmin edilerek değiştirilmemiştir.

### 💾 6. Depolama Ekranına "İndirilenler" Bölümü & Toplu Silme
- **Görünmez Dosyalar Artık Ölçülüyor:** Ayarlar > Depolama ekranı yalnızca önbellekleri gösteriyordu; indirdiğiniz videolar, görseller ve altyazılar hiçbir yerde görünmüyordu. Yeni **İndirilenler** bölümü üçünü ayrı ayrı boyut ve dosya sayısı olarak listeler:
  - **İndirilen videolar:** `Download/Kitsugi/Video` altındaki bölümler (uygulama içi kopyalar + dışa aktarılan `.mp4` dosyaları) ve eski `cacheDir/Kitsugi_downloads` klasörü.
  - **İndirilen görseller:** Galeriden cihaza kaydettiğiniz resimler (`Download/Kitsugi/Images`, `Pictures/Kitsugi/Images`, uygulama dış klasörü, eski `Download/Kitsugi`).
  - **İndirilen altyazılar:** Video indirmelerinin yanındaki `subs/` klasörleri + `Download/Kitsugi/Subtitles`.
- **Toplu Silme ve Yıkıcı Onay Penceresi:** Her satır ve yeni **"Tüm indirilenleri sil"** seçeneği, geri alınamaz işlem olduğu için detaylı onay penceresi açar (neyin silineceği, hesap/liste/geçmişin korunacağı açıkça belirtilir). İşlem bittiğinde boyutlar anında tazelenir ve bildirim gösterilir.
- **Güvenli Ayrım:** İndirilen dosyalar önbellek listesine dahil edilmemiştir; "Güvenli önbellekleri temizle" düğmesi ve otomatik bakım döngüsü çevrimdışı arşivinize asla dokunmaz.
- **Kalan "Hayalet" Kopyaların Temizlenmesi (`DownloadExportNaming.kt`):** Tek bölüm silindiğinde dışa aktarılan `.mp4` ve altyazı dosyalarının diskte kalarak gizli şişme yaratması engellendi. Video, altyazı kopyaları ve MediaStore kayıtları tek elden eksiksiz temizlenir.

### 📅 7. Bu Hafta Yayında Takvimi — Izgara Kartı Çerçeve Taşması Düzeltmesi
- **İki Satır Esnek Metin:** Izgara görünümündeki kartların alt satırında yer alan tarih/saat bilgisi (`2026-10-11 01:00 • Yayında`), kart genişliğine sığmadığında `Yayı...` şeklinde kesiliyordu. `AiringTimeText` bileşenine `maxLines = 2` ve esnek ağırlık (`weight(1f)`) eklenerek metnin iki satıra kadar düzgün sarılması sağlandı.
- **Sabit Yükseklik & Düzgün Hizalama:** Izgara bilgi satırı sabit `34.dp` yüksekliğe alınarak tüm kartların aynı boyda kalması garanti edildi; saat ikonu üst satırla hizalandı.

---

## 🇬🇧 English (v2.4.247)

### 🌐 1. Comprehensive English Localization & Hardcoded String Extraction
- **Hardcoded String Elimination:** Migrated hundreds of hardcoded Turkish UI strings across the codebase into standard Android string resources (`R.string`), resolving lingering Turkish text when English is selected.
- **Deep 41-File Integration:** Fully localized dialogs, settings, and profile tabs across `AuthViewModel`, `KitsugiProfileViewModel`, `AccountSettingsSubPages`, `DownloadsScreen`, `MangaExtensionTab`, `MalProfileExtras`, provider profiles (`AniList`, `Bangumi`, `MAL`, `Shikimori`, `Kitsu`, `Simkl`), CrossSync diagnostic summaries, and notification cards.
- **Localized Dates & Relative Timestamps:** Dynamic relative time labels ("just now", "5 min ago", "yesterday") and calendar dates now format using the active user locale (`KitsugiStrings.locale()`).
- **Resource Parity:** Added 381 symmetric string resources across both `values/strings.xml` and `values-en/strings.xml`, ensuring 100% parity and valid AAPT apostrophe escaping.

### 🏢 2. Dynamic Bangumi Organization Resolver (`BangumiOrgResolver.kt`)
- **API-Driven Organization Resolution:** Resolves raw free-text company names from Bangumi infoboxes (e.g. `株式会社マッパ`, `集英社`) to their canonical global names (`MAPPA`, `Shueisha`) via Bangumi's `search/persons` API (`type = 2`, company).
- **Non-Blocking Architecture:** Operates with a strict 2.5-second timeout and negative cache; network failures or unresolvable entries gracefully preserve the original name without blocking page loads.
- **Entity Type Filtering (`BangumiApiClient.kt`):** Added `type` filtering (1=person, 2=company, 3=group) to `searchPersons`.

### 🎭 3. Bangumi Cast & Staff Role Dictionary (`BangumiRoleDictionary.kt`)
- **Role Localization:** Localizes raw Chinese/Japanese role labels from Bangumi endpoints (`主角`, `配角`, `原作`, `监督`, `脚本`...) according to the app UI language.
- **Symmetric Role Resources:** Integrated 154 new `bangumi_role_*` and `bangumi_org_*` string resources (scaling to 6,944 strings with 100% TR/EN symmetry).

### 🖼️ 4. Showcase (Hero Section) TMDB + Fanart.tv Artwork Resolver & Smart Aspect Adaptation
- **Universal TMDB + Fanart.tv Resolver (`KitsugiHeroArtworkResolver.kt`):** Across all providers (Bangumi, AniList, MyAnimeList, Kitsu, Shikimori, Simkl, TMDB, All-in-One mode, TV showcase, and Addon showcases), every hero entry concurrently queries TMDB (`images`, `external_ids`, `alternative_titles`) and Fanart.tv (`tvposter`/`movieposter`, `showbackground`/`moviebackground`).
- **Automated High-Resolution Artwork Ranking:**
  - **Vertical Poster:** Evaluates candidates from TMDB (/original 2000x3000 / 1000x1500) and Fanart.tv (1000x1426) based on resolution, 2:3 ratio, preferred language, season matching, and community votes.
  - **Horizontal Backdrop:** Evaluates candidates from TMDB (4K 3840x2160 / 1080p) and Fanart.tv based on resolution (4K > 2K > 1080p), 16:9 cinematic aspect ratio, textless clarity, and community scores.
- **Provider Resolution Upgrades & Fixes:**
  - **Bangumi:** Resolved issue where vertical covers were treated as horizontal backdrops; prioritized full-resolution large (`/pic/cover/l/`) vertical covers.
  - **Kitsu, Shikimori & TMDB:** Prioritized large/original over medium in Kitsu; upgraded Shikimori showcase posters to fullUrl; upgraded TMDB /w500 and /w1280 assets to /original full fidelity.
- **Orientation & Form-Factor Adaptation (`KitsugiHeroSection.kt`):**
  - **Portrait Phone (`aspect < 1.10`, portrait mode):** Prioritizes vertical poster (`posterUrl`), falling back to backdrop.
  - **Landscape Phone, Tablet & TV (`aspect >= 1.10` or landscape):** Prioritizes cinematic 16:9 backdrop (`backdropUrl`), falling back to vertical poster.
- **Smart Alignment & Dual-Layer Vertical Containment:**
  - Biased crop alignment (`BiasAlignment(0f, -0.32f)` / `BiasAlignment(0f, -0.25f)`) protects character faces and head levels from being cut off on ultra-wide bands.
  - In wide showcase containers with only vertical artwork (e.g. manga covers or titles without backdrops), dual-layer presentation displays an atmospheric background fill with `ContentScale.Fit` uncropped foreground presentation.
- **Crystal-Clear Scrim Presentation:** Removed the 3-layer stacking dark gradients, keeping the top 60% of showcase imagery crisp and bright. Removed the harsh horizontal scrim on portrait tablets.

### 🧩 5. CloudStream Plugin Runtime & Diagnostics Fixes
- **Dynamic plugin ABI:** The serialization runtime is now visible to CS3 plugins through the same host API classloader parent. `kotlinx.serialization-core` is an explicit dependency and is kept from R8 removal/renaming, targeting the reported `KSerializer` `NoClassDefFoundError`.
- **Load preflight:** Before plugin code runs, the loader verifies that `MainAPI`, `KSerializer`, and Serialization `Json` are visible through the plugin classloader; missing classes are recorded in the live diagnostic trace.
- **More accurate classification:** Linkage/runtime incompatibility (`KTS-CSP-9110`) and account requirements (`KTS-CSP-9109`) now have separate codes and diagnostic counters. HTTP 401 and reported login prompts no longer appear as empty searches; a generic HTTP 403 is still not treated as proof of Cloudflare protection.
- **Parser signatures:** Added malformed/unexpected response markers and HLS header errors to the parser category.
- **Tests:** Added classifier and diagnostic-runner coverage for runtime incompatibility, authentication requirements, and summary counts.

> Domain/DNS availability and provider-account requirements depend on remote services. This release does not guess or substitute unverified plugin domains.

### 💾 6. New "Downloads" Section on the Storage Screen & Bulk Deletion
- **Invisible Files Are Now Measured:** Settings > Storage previously only listed caches; downloaded videos, images, and subtitles were unlisted. The new **Downloads** section lists all three with dedicated file size and item counts:
  - **Downloaded videos:** Episode directories under `Download/Kitsugi/Video` (in-app copies + exported `.mp4` files) and legacy `cacheDir/Kitsugi_downloads`.
  - **Downloaded images:** Gallery images saved locally (`Download/Kitsugi/Images`, `Pictures/Kitsugi/Images`, app external folder, legacy `Download/Kitsugi`).
  - **Downloaded subtitles:** `subs/` folders next to video downloads plus `Download/Kitsugi/Subtitles`.
- **Bulk Delete with Destructive Confirmation:** Every download row and the new **"Delete all downloads"** action open an explicit confirmation dialog detailing exactly what will be removed while guaranteeing accounts, lists, and history remain untouched. Sizes refresh dynamically upon completion.
- **Safe Isolation:** Downloaded items are kept strictly separate from the cache lists; "Clear safe caches" and 6-hour automated maintenance routines never touch offline downloads.
- **Fixed "Ghost" Copies (`DownloadExportNaming.kt`):** Single-episode deletion previously left exported `.mp4` files and companion subtitles in public folders, causing silent disk bloat. File naming and lifecycle management are now unified, ensuring video copies, subtitles, and MediaStore records are cleanly expunged together.

### 📅 7. Airing Calendar Grid Card — Frame Overflow & Text Wrap Fix
- **Two-Line Flexible Wrapping:** In the weekly airing calendar's grid layout, the airing date/time stamp (`2026-10-11 01:00 • Airing`) previously clipped with ellipsis (`Air...`) when card widths were narrow. `AiringTimeText` now supports `maxLines = 2` and flexible weighting (`weight(1f)`), wrapping gracefully across two lines without text truncation.
- **Consistent Heights & Alignment:** The metadata info row now uses a fixed `34.dp` height so all grid cards maintain perfectly uniform dimensions; the clock icon aligns with the top text baseline.

---

## 🇹🇷 Türkçe (v2.4.246)

### 🏢 1. Bangumi Master Şirket & Etiket Yerelleştirmesi (TR / EN)
- **Kapsamlı Şirket Sözlüğü (`BangumiOrgDictionary.kt`):** 1013 eşleme ve 365 kayıt ile stüdyo (133), yapımcı (90), yayıncı ağ (96), yayımcı (24) ve dergi (21) adları için küresel Latin ve yerelleştirilmiş kaynak eşlemeleri eklendi.
- **Genişletilmiş Etiket Sözlüğü (`BangumiTagDictionary.kt`):** 907 gruba ulaşıldı (78 yeni anahtar, 95 yeni etiket varyantı). `催淚`/`催泪` (tearjerker), `京都` (Kyoto Animation), `AS`/`After Story` gibi özel etiket boşlukları tamamen kapatıldı.
- **Detay Sayfaları & Çip Entegrasyonu:** Stüdyo, yapımcı ve yayıncı ağ adları detay sayfası bilgi kartlarında, filtre çiplerinde ve stüdyo profil sayfalarında (`toLocalizedOrgName()`) arayüz diline (TR/EN) uygun gösterilir. Monogram avatar, erişilebilirlik etiketi, paylaşım metni ve üst çubuk yerelleştirildi.
- **Dil Dosyaları & Doğrulama:** `strings.xml` ve `values-en/strings.xml` içine 436 yeni simetrik kaynak eklendi. İngilizce dosyadaki 58 hatalı Türkçe değer düzeltildi ve "Çinçe" yazımı "Çince Konuşulan Bölge" olarak güncellendi.
- **Doğrulama & Test:** `BangumiMasterDictionaryTest` birim testleri ve `verify_bangumi_master.py` doğrulama altyapısı eklendi (0 rol çakışması, tam TSV uyumu).

---

## 🇬🇧 English (v2.4.246)

### 🏢 1. Bangumi Master Org & Tag Localization (TR / EN)
- **Comprehensive Org Dictionary (`BangumiOrgDictionary.kt`):** Implemented 1013 mapping lines across 365 distinct organizations: studios (133), producers (90), broadcast networks (96), publishers (24), and magazines (21) with standardized global Latin names.
- **Expanded Tag Dictionary (`BangumiTagDictionary.kt`):** Scaled to 907 canonical tag groups (78 new keys, 95 new variant aliases). Resolved missing Chinese/Japanese aliases such as `催淚`/`催泪` (tearjerker), `京都` (Kyoto Animation), and `AS`/`After Story`.
- **Detail Pages & Chip Integration:** Organization names in anime details, chips, and dedicated studio detail pages now render through `toLocalizedOrgName()` matching the UI language (TR/EN). Monogram initials, accessibility tags, share descriptions, and top bars are fully localized.
- **Resource Symmetry & Cleanups:** Added 436 symmetric string entries across both `values/strings.xml` and `values-en/strings.xml`. Fixed 58 Turkish entries that previously leaked into English resources, and corrected "Çinçe" to "Çince Konuşulan Bölge".
- **Harness & Verification:** Added `BangumiMasterDictionaryTest` JUnit suite and `verify_bangumi_master.py` validator ensuring zero ambiguous role conflicts and 1:1 TSV parity.


---

## 🇹🇷 Türkçe (v2.4.245)

### 🎨 1. Yenilenmiş Duyarlı Vitrin Göstergesi & Çerçeve Tasarımı
- **Sayısal Rozet Kaldırıldı:** Ana sayfa vitrininde (hero slider) yer alan "8 / 10" formatındaki sayısal sayaç rozeti ve `heroCounterIndex` kaldırıldı; arayüz görsel olarak sadeleştirildi ve recomposition yükü azaltıldı.
- **Dinamik Çerçeve Göstergesi (`drawBehind`):** Hangi vitrin sayfasında olunduğunu belirtmek için aktif çizginin etrafını saran yuvarlatılmış estetik bir çerçeve eklendi. Çerçevenin opaklığı sayfa kaydırma geçişinde (`heroPageVisibility`) doğrudan GPU çizim katmanında akıcı şekilde animasyonlanır.
- **Her Ekrana Uyumlu (Responsive) Çizgiler:** Çizgiler `Modifier.weight(1f)` ile yatay alanı eşit paylaşır. `fillMaxWidth` ve `widthIn(max)` kısıtlamaları sayesinde dikey ve yatay telefonlarda veya tabletlerde ekrandan taşma yapmaz; geniş ekranlarda çizgilerin aşırı uzaması engellenir.
- **Çizim Optimizasyonu:** Renk getter'ları (`indicatorFrameColor`) composition kapsamında yakalanarak `drawBehind` içerisindeki çizim güvenliği ve performansı artırıldı.

---

## 🇬🇧 English (v2.4.245)

### 🎨 1. Responsive Hero Slider Indicator & Sleek Active Frame
- **Numeric Counter Removal:** Removed the static "8 / 10" badge and `heroCounterIndex` state from the main hero carousel, delivering a clean, modern aesthetic without recomposition overhead.
- **Active Frame Indicator (`drawBehind`):** Introduced a rounded boundary frame wrapping the active page line. Frame opacity animates smoothly based on page scroll offset (`heroPageVisibility`) directly in the draw phase without recomposing layouts.
- **Fully Responsive Bar:** Indicator lines now evenly distribute width using `Modifier.weight(1f)`. Combined with `fillMaxWidth` and responsive `widthIn(max)`, the indicator perfectly scales across portrait/landscape phones and tablets without overflowing or stretching excessively on large screens.
- **Draw Phase Optimization:** Cleanly captures theme color getters (`indicatorFrameColor`) within the composition scope prior to `drawBehind` canvas execution.


---

## 🇹🇷 Türkçe (v2.4.244)

### 🌐 1. Birleşik Logo Seçim Politikası ve Dil / Sezon Önceliği
- **Ortak Dil ve Eşleşme Politikası (`LogoSelectionPolicy.kt`):** TMDB ve Fanart.tv logo adayları artık tek ve tutarlı bir dil sıralama politikasından geçirilir. Öncelik sırasıyla: seçili uygulama dili → İngilizce → romanize/metinsiz görseller → Japonca/Çince.
- **Sezon Bazlı Logo Uyumu:** Dizi logolarında, aynı dil kademesi içerisinde o an izlenen sezon ile tam eşleşen logo adayları önceliklendirilir.
- **Afiş/Logo İzolasyonu:** Film ve dizi logo sorguları kesin sınırlarla ayrıldı; sezon afişlerinin (posters) logo zannedilip seçilmesi engellendi.
- **Önbellek & Galeri Düzenlemesi:** Galeri sıralamaları ve görsel önbellekleri (`DetailCache.kt`, `GalleryItem.kt`) seçili dil ve sezon bağlamını koruyacak şekilde güncellendi.

### ⚡ 2. P2P Torrent Önbellek Tahliyesi & Otomatik Disk Temizliği
- **Korunan Torrent Tahliyesi Fix (`P2pStreamingEngine.kt`):** `libnuvio_engine` motoru korunan torrent verisini tahliye etmediği için biriken disk boyutu sınırı aşıyordu ("disk cache reclaim limited by protected torrent data"). Oynatıcı kapatıldığında veya akış durduğunda torrent motordan `removeTorrent(id)` ile siliniyor ve `reclaimDiskCache(0L)` ile önbellek anında sıfırlanıyor.
- **Bağlantı Hatası Temizliği:** Akış bağlanamadan hata aldığında eklenen torrent nesnesi de motordan kaldırılarak diskte artık kalması önlendi.
- **Açılışta Artık Süpürme:** Uygulama açılışında, motor boşta ve aktif akış yokken önceki oturumlardan kalan artık payload dosyaları otomatik olarak temizlenir.
- **Önbellek Yönetim Anahtarı (`P2pSettingsSection.kt`):** P2P Depolama ayarları altına *"Video kapanınca önbelleği temizle"* seçeneği eklendi.

### 🏢 3. Bangumi Kurum/Yapımcı Arama Dayanıklılığı & Negatif Önbellek Koruması
- **Genişletilmiş Arama Limiti (`KitsugiBangumiDetailClient.kt`):** Kurum arama limiti 5'ten 10'a yükseltildi, aynı ada sahip birden çok kayıt olduğunda doğru kurumun bulunması sağlandı.
- **Geçici Hata Koruması (Negatif Önbellek Koruması):** Ağ veya zaman aşımı hataları artık "bulunamadı (0)" olarak önbelleğe yazılmaz; geçici hatalar atlatıldığında kurum sayfaları sorunsuz açılır. Bulunamayan geçerli sonuçlar için 10 dakikalık TTL önbelleği (`STUDIO_MISS_TTL_MS`) uygulandı.

### 🔄 4. Profil Hata Ekranı 'Yeniden Dene' Desteği
- **Hızlı Yeniden Deneme (`KitsugiProfileScreen.kt` / `ProfileSharedComponents.kt`):** Profil hata ekranlarına doğrudan tetiklenebilir "Yeniden Dene" butonu eklendi.
- **Hız Sınırı Mesajı:** AniList HTTP 429 hız aşımı yanıtları için net ve anlaşılır uyarı mesajı eklendi.

---

## 🇬🇧 English (v2.4.244)

### 🌐 1. Unified Logo Selection Policy & Language / Season Priority
- **Shared Language Ranking (`LogoSelectionPolicy.kt`):** TMDB and Fanart.tv candidate logos now run through a unified language ranking algorithm: User's selected language → English → Romanized/Textless artwork → Japanese/Chinese.
- **Season-Aware Matching:** For series logos, season-specific artwork matching the current season is preferred within the same language tier.
- **Poster/Logo Isolation:** Strict endpoint and role separation ensures season posters are never mistakenly picked as title logos.
- **Cache & Gallery Sync:** Image gallery ordering and detail caches (`DetailCache.kt`, `GalleryItem.kt`) now take language and season context into account.

### ⚡ 2. P2P Torrent Cache Eviction & Automatic Disk Cleanup
- **Protected Torrent Eviction Fix (`P2pStreamingEngine.kt`):** Addressed root cause where `libnuvio_engine` refused to reclaim disk cache due to active torrent protection ("disk cache reclaim limited by protected torrent data"). Stopping a stream or closing the player now disposes the torrent via `removeTorrent(id)` and calls `reclaimDiskCache(0L)`.
- **Startup & Failure Cleanup:** Any torrent created during a failed connection attempt is proactively unmounted. Leftover cache payloads from previous sessions are swept on application launch.
- **Toggleable Cache Policy (`P2pSettingsSection.kt`):** Added a "Clear cache when video closes" toggle in P2P settings.

### 🏢 3. Bangumi Studio/Person Search Robustness & Cache Safety
- **Expanded Search Limit (`KitsugiBangumiDetailClient.kt`):** Increased studio search limit from 5 to 10 candidates to resolve homonym records accurately.
- **Transient Error Resilience:** Network glitches and timeouts are no longer cached as permanent misses (0 ID), preventing studio pages from staying blocked throughout the session. Legitimate misses use a 10-minute TTL.

### 🔄 4. Profile Error Screen 'Retry' Action
- **Interactive Retry (`KitsugiProfileScreen.kt` / `ProfileSharedComponents.kt`):** Added an explicit Retry button on profile error cards to quickly re-fetch state without navigating away.
- **Rate Limit Messages:** Refined AniList HTTP 429 explanations with dedicated bilingual catalog strings.


---

## 🇹🇷 Türkçe (v2.4.243)

### 🖼️ 1. Shikimori Vitrin Logoları & Doğrudan Görsel Eşleştirme
- **Vitrin Logo Desteği (`HeroLogoPolicy.kt`):** Genel logo ayarı kapalı olsa dahi Shikimori kaynaklı anime içerikleri için vitrin logosu araması etkinleştirildi. Logo bulunamadığında mevcut başlık metni şık bir yedek olarak kalır.
- **MAL Kimliği Eşlemesi (`KitsugiShikimoriClient.kt`):** Arama ve keşfet sonuçlarındaki `myanimelist_id` bilgisi doğrudan `realMalId` alanına aktarıldı. Bu sayede gereksiz aracı sorgular azaltılarak logo eşleştirmesi çok daha hızlı ve doğrudan çalışır.

### 🛡️ 2. Kullanıcı Dostu Profil Hata Ekranı & Hata Sınıflandırması
- **Anlaşılır Hata Kartı (`ProfileErrorFormatter.kt` / `ProfileSharedComponents.kt`):** Ham hata mesajları ve sunucu JSON gövdeleri yerine; HTTP 429 (hız sınırı/rate-limit), oturum, yetki, 404 bulunamadı, zaman aşımı, ağ ve sunucu problemleri için net Türkçe ve İngilizce açıklamalar gösteriliyor.
- **HTTP Durum Rozeti:** Teknik hata detayları kullanıcıyı boğmadan, varsa HTTP kodu küçük ve şık bir durum rozeti olarak arayüzde yer alır.

---

## 🇬🇧 English (v2.4.243)

### 🖼️ 1. Shikimori Hero Logos & Direct Visual Matching
- **Hero Logo Policy (`HeroLogoPolicy.kt`):** Enabled anime hero logo lookups for Shikimori media even when the global logo toggle is disabled. Gracefully falls back to title text if no logo asset exists.
- **Direct MAL ID Mapping (`KitsugiShikimoriClient.kt`):** Mapped `myanimelist_id` to `realMalId` across Shikimori search and explore results, streamlining logo matching without unnecessary intermediary queries.

### 🛡️ 2. User-Friendly Profile Error UI & Classification
- **Clean Error Card (`ProfileErrorFormatter.kt` / `ProfileSharedComponents.kt`):** Replaced raw provider JSON/trace dumps with friendly, localized explanations for HTTP 429 (rate limiting), auth expired, 404 not found, timeouts, network interruptions, and server outages.
- **HTTP Status Badge:** Retains the HTTP status code as a clean, minimal badge alongside the localized description.

---

## 🇹🇷 Türkçe (v2.4.242)

### 🎨 Eklentiler & Kaynaklar Sayfası Tema Uyumu ve Kompakt Arayüz
- **Üst Eklenti Sekmeleri:** Torrent & Akış, Video Kaynakları ve Manga Kaynakları sekmelerinin seçili durumu, kullanıcının ayarladığı özel tema rengi ve iki renkli degrade fırçası (`accentBrushWithAlpha`) ile entegre edildi.
- **Tek Tip Alt Sekme Seçicisi (`AddonSegmentedControl`):** Video ve Manga sayfalarındaki "Repolar" ve "Kurulu" geçişleri için ortak, modern ve kompakt bir seçici bileşeni oluşturuldu.
- **P2P ve Eklenti Anahtarları:** Torrent/P2P ayarları, eklenti açma/kapatma anahtarları, bölüm ikonları ve durum göstergeleri temayla tam uyumlu hale getirildi. Hata ve silme gibi semantik uyarı renkleri Material tema renklerine bağlanarak korundu.
- **Kompakt Kart Ergonomisi & Temalı İkonlar:** Boşluklar, satır yükseklikleri ve ikon boyutları sadeleştirildi; simgesi bulunamayan eklentiler için temalı modern yedek ikonlar getirildi.

---

## 🇬🇧 English (v2.4.242)

### 🎨 Addons & Sources UI Theme Alignment & Compact Layout
- **Top Addon Tabs:** The active tab indicator across Torrent & Stream, Video Sources, and Manga Sources now dynamically adapts to the user's selected accent color and gradient brush (`accentBrushWithAlpha`).
- **Unified Sub-Tab Selector (`AddonSegmentedControl`):** Implemented a shared, compact, and polished segmented selector for switching between "Repositories" and "Installed" across both Video and Manga sources.
- **P2P & Extension Controls:** Switches, section headers, badges, and update actions now align with the current theme palette while preserving semantic error and delete styling.
- **Compact Card Ergonomics & Themed Fallbacks:** Streamlined card paddings, row heights, and icon sizes; added elegant theme-accented placeholder icons when extension icons cannot be loaded.

---

## 🇹🇷 Türkçe (v2.4.241)

### 👤 1. AniList Standartlarında Detaylı MyAnimeList (MAL) Profili
- **Cihaz Üzeri MAL Ayrıştırıcı (`MalWebParser` / `MalWebSource`):** Doğrudan cihaz üzerinden MAL profil, geçmiş ve arkadaş sayfalarını okuyan bağımsız motor. Client ID veya token gerektirmez, IP ban riskine karşı aralıklı istek (rate-limit), akıllı TTL önbelleği ve otomatik bekleme süresi (cooldown) içerir.
- **Veri Akış Önceliği:** Öncelik cihaz üzeri okumada; başarısız olursa miribyou (Vercel), ardından Tenrai devreye girer.
- **Hakkında Sekmesi:** Cinsiyet, doğum günü (yaş hesabı ile), konum, katılım tarihi, son görülme süresi, toplam izleme/okuma gün ve bölüm sayıları, katlanabilir biyografi alanı.
- **Aktivite Sekmesi:** Son güncellemeler (ör. *"12. bölümü izledi • 3 saat önce"*) ve 40 kayda kadar detaylı geçmiş listesi. Kayıtlara dokunulduğunda ilgili anime veya manga sayfası doğrudan açılır.
- **Arkadaşlar & Favoriler:** Profil resimleriyle birlikte arkadaş listesi ve favori anime/manga/karakter listesi.

### 🔑 2. MAL Girişi ve TV QR PKCE Güvenlik Düzeltmesi
- **Giriş İstemcisi Ayrımı:** Tarayıcıda temel kimlik doğrulama ("Oturum açın" / Basic Auth) penceresinin açılması engellendi; `malapp://auth` ile kayıtlı Android OAuth istemcisi (`MAL_OAUTH_CLIENT_ID`) bağlandı.
- **TV QR & Doğrudan Giriş PKCE Fix:** Android TV QR giriş kodunda ve doğrudan tarayıcı oturumunda eksik olan MAL PKCE `code_challenge` ve `codeVerifier` oluşturma/saklama mekanizması düzeltildi.

### ⏱️ 3. Haftalık Yayın Takvimi ("Bu Hafta Yayında") Yenilikleri
- **Dinamik Ekran Uyumu:** Ekran genişliğine göre sütun sayısını ayarlayan `rememberKitsugiGridColumns()` yapısı.
- **Yatay Liste Kartları:** "Tümünü Gör" düzenine uyumlu afiş ve detayları öne çıkaran `AiringEntryWideCard`.
- **Takvim İskelet Animasyonu (`AiringCalendarSkeleton`):** Görünüm türüne (ızgara/liste) göre şekil alan yumuşak yükleme yer tutucuları.
- **İlk Açılış ve Yeniden Deneme:** İlk veriler gelene kadar yaşanan anlık boş ekran parlaması (`hasLoaded`) giderildi; hata durumunda yeniden deneme seçili veri kaynağını koruyarak çalışır.

### 🖼️ 4. Detay Sayfaları İçin Ortak Yükleme İskeletleri ve Kayan Başlık
- **Ortak İskelet (`DetailPageLoadingSkeleton`):** Tüm detay sayfalarında (Stüdyo, Karakter, Personel, Medya Girişi ve API Sonuç) tam ekran dönen çark yerine sayfa düzenini birebir taklit eden shimmer iskeleti.
- **Stüdyo Kayan Üst Çubuk:** Filtre ve görünüm butonları ekrandan çıktıktan sonra 260ms/220ms yumuşak solma animasyonuyla devreye girer; üst çubuğa paylaş butonu eklendi.

### 🌐 5. Kapsamlı Yerelleştirme (i18n) & Arama Motoru Entegrasyonu
- Arama filtreleri, motorlar, kapsamlar ve arama diyalogları (`SearchLocalization.kt`) dahil olmak üzere Türkçe ve İngilizce dil dosyalarında 5.941 dize anahtarı %100 eşleşmeyle korundu.

### ⚡ 6. Eklenti Arama & Cloudflare / DDoS-Guard Motoru (v2.4.240)
- Eklenti isim aramalarında sıfır sonuç ve zaman aşımı sorunları giderildi. 120 sn CF bütçesi, DDOS-Guard negatif önbellek ve CF Gate mekanizması korunmuştur.

---

## 🇬🇧 English (v2.4.241)

### 👤 1. Detailed MyAnimeList (MAL) Profile
- **On-Device MAL HTML Parser (`MalWebParser` / `MalWebSource`):** Self-contained on-device scraping for MAL profile, history, and friends pages. Requires no client ID or token, features rate limiting, TTL caching, cooldowns, and automatic failover.
- **Data Source Hierarchy:** On-device scraping takes priority; falls back to miribyou (Vercel) and then Tenrai.
- **About Tab:** Gender, birthday (with calculated age), location, join date, last online, total watch/read time and episodes/chapters, collapsible about/bio card.
- **Activity Stream:** Recent updates (e.g., *"Watched ep 12 • 3 hours ago"*) and up to 40 history records. Tapping an activity directly navigates to the media entry.
- **Friends & Favorites:** Friends list with profile avatars, favorites for anime, manga, and characters loaded directly.

### 🔑 2. MAL Authentication & TV QR PKCE Security Fix
- **Dedicated Android OAuth Client:** Fixed browser basic authentication popup by routing OAuth logins through the registered Android client (`MAL_OAUTH_CLIENT_ID` with `malapp://auth`).
- **TV QR PKCE Fix:** Resolved missing PKCE parameters (`code_challenge` / `codeVerifier`) in Android TV QR codes and direct device login dialogs.

### ⏱️ 3. Weekly Airing Calendar ("Airing This Week")
- **Adaptive Screen Layout:** Dynamic column layout with `rememberKitsugiGridColumns()`.
- **Wide List Cards:** Redesigned `AiringEntryWideCard` with prominent posters and airtime countdowns.
- **Calendar Skeleton Placeholder (`AiringCalendarSkeleton`):** Shimmer placeholders dynamically matching list or grid view modes.
- **Flicker-Free Initial Load & Retry:** Eliminated initial empty screen flash (`hasLoaded`) and preserved selected data source across retries.

### 🖼️ 4. Shared Detail Skeletons & Floating Sticky Headers
- **Universal Detail Skeleton (`DetailPageLoadingSkeleton`):** Replaced full-screen progress spinners across Studio, Character, Staff, Media, and API detail pages with layout-accurate shimmering skeletons.
- **Studio Floating Header:** Smooth 260ms/220ms fade trigger once controls exit viewport, plus direct share action.

### 🌐 5. Comprehensive Localization (i18n) & Search Parity
- Full parity across 5,941 string keys in `values/strings.xml` and `values-en/strings.xml`, covering search engines, filters, chips, dialogs (`SearchLocalization.kt`), and Explore.

### ⚡ 6. Plugin Search & Cloudflare / DDoS-Guard Resilience (v2.4.240)
- Full CF-aware timeouts (120s), DDoS-Guard host locking, negative caching, and pre-search CF Gate validations.

---

## 🇹🇷 Türkçe (v2.4.238)

### 📊 1. Geliştirici Günlükleri (Developer Logs) Yenilendi
- **Tam Genişlik Başlık:** Başlık alanı tam genişliğe yayıldı, dikey harf kırılması sorunu çözüldü; kapat butonu sağa yerleştirildi.
- **Yatay Kaydırılabilir Araç Çubuğu:** Yenile, Kopyala, Kaydet, Paylaş ve Temizle butonları kompakt ve yatay kaydırılabilir modern bir satıra toplandı.
- **Log Seviye Filtreleri:** Hata, Uyarı, Bilgi ve Hata Ayıklama (Debug) seviyeleri anlık sayaçlarıyla filtrelenebilir hale getirildi.
- **Gelişmiş Seçenekler:** "Sadece Bu Uygulama" filtresi, "Satır Kaydır" ve "Otomatik Yenile" anahtarları ile toplam/hata/uyarı özet satırı eklendi.
- **Düz Metin Paylaşım:** Log paylaşımı doğrudan okunabilir düz metin formatına geçirildi.

### 📐 2. Duyarlı Medya Izgaraları (Responsive Grid Columns)
- **Tek Merkezden Dinamik Sütun Hesabı (`KitsugiGridColumns`):** Tüm medya ızgaraları (Tümünü Gör, Stüdyo, Yapımcı, Favoriler, Koleksiyon Klasörleri, Manga Kaynakları ve Eklenti Izgaraları) standartlaştırıldı.
- **Cihaz & Yön Uyumu:** Dar ekranlarda (telefon dikey, < 600dp) 2 sütun, orta ekranlarda (telefon yatay / küçük tablet, 600–839dp) 3 sütun, geniş ekranlarda (tablet yatay, ≥ 840dp) 4 sütun; dikey/yatay geçişte otomatik yeniden hesaplanır.

### 🌄 3. Vitrin (Hero Section) Karartma ve Okunabilirlik İyileştirmesi
- **Derin Alt Karartma:** Telefon dikey modunda alt karartma yüksekliği 220dp'den 300dp'ye uzatıldı ve degrade opaklığı artırıldı; başlık ve açıklamaların altındaki görsel parlamaları engellendi.
- **Belirgin Meta Satırı:** Tür, yıl ve bölüm bilgisi içeren meta satırının rengi TextSecondary yapılarak koyu zemin üzerinde çok daha rahat okunabilir hale getirildi.

### 🌐 4. GitHub Vekil Sunucu (jsDelivr) Anahtarı Düzeltildi
- **Kök Neden Giderildi:** Eklenti ve akış ayarlarındaki diyalog çağrılarında (akış seçici, manga kaynakları) useGithubProxy / onUseGithubProxyChanged parametreleri geçilmiyordu; diyalog varsayılan (false) değerle açıldığı için switch açılamıyordu. Ayrıca repo katmanı ayarı DataStore'dan okumuyordu.
- **Düzeltme:** Hem telefon hem Android TV diyaloglarına bağlandı (TV için özel ayar satırı eklendi). CloudstreamRepoRepository artık ayarı okur; anahtar açıkken eklenti ve repo listeleri doğrudan jsDelivr CDN üzerinden çekilir. Doğrudan GitHub ve hata durumunda jsDelivr yedeği aynen korunur.

### ⚡ 5. Akış Listesi Kasması Tamamen Çözüldü (4 Ayrı İyileştirme)
- **Animasyon Yükü Kaldırıldı:** Akış kartları her kaydırmada AnimatedVisibility (fadeIn + slideInVertically) ile yeniden çiziliyordu; gereksiz animasyon kaldırılarak liste kaydırması pürüzsüzleştirildi.
- **Akıllı ve Gecikmeli Ölçüm:** Eskiden her kart compose edildiğinde hemen HLS/HEAD ağ ölçümü başlıyordu (60 sonuçta onlarca paralel istek). Artık ölçüm yalnızca **görünür kartlarda** ve **350 ms yerleşme gecikmesinden sonra** başlar; kaynak kalite/ses/altyazı bilgilerini zaten sunuyorsa ölçüm hiç yapılmaz (isProbeNeeded).
- **İptal Edilebilir Ölçüm Kapısı:** Bloklayıcı OkHttp.execute() yerine Call.enqueue ve suspendCancellableCoroutine kullanıldı; kart görüş alanından çıkınca istek gerçekten iptal edilir ve eşzamanlılık izni hemen serbest kalır (eşzamanlılık kapasitesi 3 → 2).
- **Filtresiz Hızlı Yol:** Arama veya etiket filtresi aktif değilken 60+ akış için çalıştırılan regex tabanlı kalite/dil çözümlemesi atlanır; çipler key ile optimize edildi.

### 🖼️ 6. MAL ve MAL'a Bağlı Kaynaklar Galeriye Ortak Edildi
- **Tüm Detay Sayfaları:** Anime, film ve dizi detaylarında gerçek MAL kimliği çözülebiliyorsa MAL (Jikan) /pictures görselleri Fanart.tv, TMDB ve Shikimori görselleriyle **paralel** çekilip galeriye eklenir.
- **Manga Galerisi Zenginleşti:** Manga detay sayfaları eskiden sadece kapak görseliyle sınırlıydı; artık MAL ek görselleri de galeriye katılır.
- **Kimlik Güvenliği:** AniList/Kitsu/Bangumi stable ID'leri ve TMDB/Simkl ID'leri MAL kimliği olarak kullanılmaz; yanlış yapımın görselleri kesinlikle galeriye karışmaz.
- **Önbellek:** 80 kayıtlık LRU önbellek; geçici hataların kalıcı olmaması için boş sonuçlar önbelleğe yazılmaz.
- **MAL OAuth Client Secret Desteği & Harici Tarayıcı:** Web/özel MAL uygulamaları için mal_client_secret desteği local.properties ve BuildConfig'e eklendi; MAL girişi harici tarayıcıya yönlendirilerek giriş güvenliği sağlandı.
- **Çapraz Eşitleme Logoları:** Çapraz eşitleme ayar satırlarında resmi marka logoları (AniList, MAL, Simkl, Kitsu, Shikimori, Bangumi) yerleştirildi.

### 🎌 7. Bangumi Liste Üyeliği ve Sağlayıcı İzolasyonu
- **Yalnızca Gerçek Bangumi Kayıtları:** Bangumi sekmesinde ve sayaçlarında yalnızca kullanıcının Bangumi hesabına ait (source = bangumi ve geçerli Bangumi stable ID'li) kayıtlar gösterilir.
- **Sağlayıcı Çapraz Karışması Engellendi:** Diğer sağlayıcıların (Simkl, AniList, MAL) kayıtları veya yerel kayıtlar Bangumi üyeliği sayılmaz; bgm.tv koleksiyonuyla birebir sayı ve liste tutarlılığı sağlandı.

### 🔄 8. Bangumi Uzak Öncelikli Ekleme ve Düzenleme Akışı
- **Önce Uzak Koleksiyon Güncellemesi:** Bangumi arama sonuçlarından ekleme veya düzenleme yapıldığında istek önce doğrudan Bangumi API'sine iletilir (BangumiSyncManager.syncEntryToBangumi).
- **Veri Güvenliği Garantisi:** Uzak koleksiyon güncellemesi başarılı olmadan (collectionUpdated) yerel veritabanına kayıt eklenmez veya değiştirilmez. Başarılı olunca Bangumi subject ID'si stable alanda saklanır.
- **Doğrudan Bangumi Bağlamı:** Bangumi kayıtları düzenleyicide kendi yerel durumları, puanı ve ilerlemesiyle açılır.

### 🛡️ 9. Bangumi Senkronizasyon İzolasyonu
- **Dış Dağıtım Engeli:** Bangumi üzerindeki koleksiyon işlemleri diğer sağlayıcılara (AniList / MAL / Simkl / Shikimori) fan-out yapmaz; sağlayıcıya özel temiz senkronizasyon korunur.

### 🎬 10. Bangumi REAL İçeriklerde Film / Dizi Ayrımı ve Tür Güvenliği
- **Platform Tabanlı Eşleme:** Bangumi REAL kategorisindeki canlı çekim (live-action) yapımlar platform bilgisine göre doğru bir şekilde Film (Movie) ve Dizi (TV) türlerine dönüştürülür.
- **Anime Varsayılanı Kaldırıldı:** Desteklenmeyen (müzik, oyun vb.) veya belirsiz türdeki Bangumi kayıtlarının otomatik olarak Anime şeklinde içe aktarılması engellendi.
### 🌍 11. Kapsamlı İngilizce Dil Tamamlama (Batch 2)
- **825 Yeni Dil Kaynağı:** `values/strings.xml` ve `values-en/strings.xml` 4.234'ten 5.052 anahtara çıkarıldı; TR/EN tam parite sağlandı.
- **1.685 Kod Noktası Kaynağa Bağlandı:** Kod içine gömülü sabit metinler temizlenerek 891 `stringResource` ve Composable olmayan katmanlar için 794 `KitsugiStrings.get` çağrısına bağlandı (288 dosya).
- **Yeni KitsugiStrings Altyapısı:** ViewModel, servis ve veri katmanlarında güvenli ve erken aşamada başlatılan metin çözücü mimarisi devreye alındı.

---

## 🇬🇧 English (v2.4.238)

### 📊 1. Redesigned Developer Logs Dialog
- **Full Width Header:** Full horizontal title bar avoids awkward vertical text breaking, with close button pinned cleanly to the right.
- **Scrollable Toolbar:** Refresh, Copy, Save, Share, and Clear buttons organized into a sleek horizontal row.
- **Level Filters:** Filter logs by Error, Warning, Info, and Debug with live badge counters.
- **Enhanced Utilities:** "Only This App" process filter, text wrapping, auto-refresh toggles, and total/error/warn summary header.
- **Plain Text Sharing:** Direct plain-text sharing for immediate readability in external apps.

### 📐 2. Responsive Media Grid Layout (KitsugiGridColumns)
- **Centralized Column Calculation:** Standardized across See All, Studios, Producers, Favourites, Folder Collections, Manga Sources, and Addon Grids.
- **Breakpoint Optimization:** 2 columns on phones portrait (<600dp), 3 columns on landscape/foldables (600–839dp), 4 columns on tablets (>=840dp); adapts dynamically on rotation.

### 🌄 3. Hero Section Contrast & Readability
- **Extended Gradient Vignette:** Height expanded to 300dp on phone portrait with heavier gradient stops, preventing background image bleed behind titles.
- **High-Contrast Metadata:** Meta information row styled in TextSecondary for clear legibility over dark backdrop fades.

### 🌐 4. GitHub Proxy (jsDelivr) Toggle Fixed
- **Root Cause Resolved:** Dialog callers across stream picker and manga browse omitted useGithubProxy / onUseGithubProxyChanged, causing the switch to always render off and ignore user input. The repository layer also neglected to read the setting.
- **Fix:** Fully wired on phone and Android TV (with a dedicated TV settings row). CloudstreamRepoRepository now reads the setting; when enabled, repo and plugin requests prefer jsDelivr CDN directly. Fallback mechanisms remain intact.

### ⚡ 5. Stream Picker Performance & Stutter Resolution
- **Removed Replay Animations:** Eliminated per-card AnimatedVisibility enter animations that re-ran on every scroll pass, restoring silky-smooth LazyColumn scrolling.
- **Debounced Smart Probing:** Probing is now gated exclusively to visible cards after a 350 ms settle delay (rapid scrolling incurs zero probe requests). If a provider already supplies quality, audio, and subtitles, probing is bypassed (isProbeNeeded).
- **Cancellable Probing Queue:** Converted blocking OkHttp calls to cancellable Call.enqueue with suspendCancellableCoroutine. Abandoned requests are immediately cancelled, releasing concurrency permits (capacity 3 -> 2).
- **Filterless Fast Path:** Regex-based quality and audio resolution is skipped when no active filters are set; filter chips are keyed.

### 🖼️ 6. MAL & Linked Sources Shared Gallery
- **Unified Media Galleries:** Detail pages for anime, TV series, and movies now resolve genuine MAL IDs and fetch MAL (Jikan) /pictures in parallel with Fanart.tv, TMDB, and Shikimori.
- **Enriched Manga Galleries:** Manga details, previously limited to their single cover, now pull in additional MAL artwork.
- **Strict ID Namespace Safety:** AniList/Kitsu/Bangumi stable IDs and TMDB/Simkl IDs are never forwarded to MAL, guaranteeing no cross-media artwork contamination.
- **LRU Caching:** 80-entry LRU cache prevents duplicate fetches; empty results are never cached to avoid locking out transient network issues.
- **MAL OAuth Client Secret Support & External Browser:** Added mal_client_secret configuration support to local.properties and BuildConfig; directs MAL authorization to external browser for dependable OAuth completion.
- **Cross-Sync Platform Logos:** Official vector logos added to cross-sync toggles for AniList, MAL, Simkl, Kitsu, Shikimori, and Bangumi.

### 🎌 7. Bangumi List Membership & Provider Isolation
- **Pure Bangumi Scope:** The Bangumi tab and collection counters exclusively display authentic Bangumi records (source = bangumi with stable Bangumi subject IDs).
- **Cross-Provider Isolation:** Records from other providers (Simkl, AniList, MAL) and local-only items are never treated as Bangumi members, maintaining exact parity with bgm.tv.

### 🔄 8. Remote-First Add & Edit Flow for Bangumi
- **Remote Update Precedence:** Adding or editing Bangumi search results updates the remote Bangumi collection first (BangumiSyncManager.syncEntryToBangumi).
- **Data Integrity Assurance:** If the remote API call fails (collectionUpdated == false), the local database is not altered. Upon success, the entry is committed with its stable Bangumi subject ID.
- **Native Bangumi Context:** Bangumi entries open directly in Bangumi editor mode, cleanly handling native collection statuses, progress, and scoring.

### 🛡️ 9. Bangumi Isolated Synchronization
- **No External Leakage:** Bangumi updates remain isolated and do not fan out to other tracking services (AniList / MAL / Simkl / Shikimori).

### 🎬 10. REAL Media Type Classification & Safety
- **Platform-Aware Mapping:** Bangumi REAL entries are accurately mapped to Movie or TV based on platform metadata.
- **No False Anime Defaults:** Unsupported categories (music, games) or unknown types are rejected rather than incorrectly defaulting to Anime.
### 🌍 11. Comprehensive English Localization (Batch 2)
- **825 New Localization Keys:** Resource files expanded to 5,052 strings with 100% TR/EN parity across all screens.
- **1,685 Hardcoded Call Sites Cleaned:** Migrated 891 `stringResource` and 794 `KitsugiStrings.get` points across 288 files.
- **KitsugiStrings Architecture:** Non-Composable string resolver providing robust localization across ViewModels and background services.

---

## 🇹🇷 Türkçe (v2.4.237) — Hesap Kasma Çözümü, Vitrin Tıklama, Grid & Navigasyon Düzeltmeleri, MAL Uygulama İçi Giriş, Arama Logoları, Eklenti Bildirimi, Galeri İzolasyonu

### ⚡ 1. Bağlı Hesap Kaynaklı Kasma & Takılma Tamamen Çözüldü (`ExploreMapping`)
- **Kök Neden Giderildi:** Keşfet, "Tümünü Gör" ve Arama ekranlarındaki listede olmayan her bir kart için kullanıcının bağlı hesabındaki TÜM kütüphane taranıyor ve başlıklar ana iş parçacığında (UI thread) her karede yeniden normalize ediliyordu. Hesapta kayıt sayısı arttıkça yaşanan ağırlaşmanın asıl sebebi buydu.
- **Doğrudan İndeks Arama:** Tam liste taraması tamamen kaldırıldı. Eşleşme O(1) karma indeks adaylarıyla sınırlandırıldı. `realMalId` indekse eklenerek doğru eşleşmeler korundu, kaydırma ve yüklemeler yağ gibi akıcı hale getirildi.

### 🎯 2. Vitrin (Hero Section) Tıklama Yönlendirmesi ve Belirgin Sayaç Göstergesi
- **Baskın Öğeyi Çözümleme (`openVisibleHeroItem`):** Vitrinde üst üste binen katmanlarda görünmez komşu sayfaların tıklamaları yutması çözüldü; tıklama anında ekranda görsel olarak baskın olan içerik (`round(currentPage + offset)`) doğrudan açılıyor (One Piece'e tıklayınca One Piece, Frieren'e tıklayınca Frieren).
- **Belirgin Noktalar ve Sayısal Sayaç:** Sayfa gösterge noktaları kalınlaştırılıp opaklaştırıldı (7dp); yanına kaçıncı sıradaki içeriğin gösterildiğini belirten `N / M` (örn. 3 / 11) sayısal sayaç rozeti eklendi. `derivedStateOf` ile performans korundu.

### 🚀 3. "Tümünü Gör" Grid Yarış Durumu (Race Condition) ve İç İçe Geçme Düzeltildi
- **Snapshot Birleştirme Mantığı:** Hızlı kaydırma sırasında 1. sayfa zenginleştirilirken (enrich) 2. sayfa geldiğinde oluşan yarış durumu çözüldü; enrich tamamlandığında mevcut liste snapshot ile akıllıca birleştirilerek (`merge`) kartların kaybolması ve zıplaması önlendi.
- **Benzersiz & Stabil Kimlik (`exploreIdentity`):** `source:type:malId:tmdbId:cs3UrlHash` tabanlı stabil anahtarlama sağlandı. TMDB film/dizi ve eklenti ID çakışmaları tamamen engellendi.
- **Stabil Compose Durumu:** Grid ve listelerdeki `rememberSaveable` kaynaklı Bundle restore çakışması `rememberLazyGridState()` ve `rememberLazyListState()` ile çözüldü; Compose kartları sorunsuz yeniden kullanılıyor.

### 🔑 4. MyAnimeList (MAL) Girişinde Uygulama İçi WebView ve Basic Auth Çözümü
- **Uygulama İçi WebView (`KitsugiOAuthWebViewDialog`):** Harici tarayıcıya yönlendirme yerine uygulama içi modern WebView ile doğrudan giriş sağlandı.
- **HTTP Basic Auth Popup Engellendi:** Brave gibi tarayıcılarda 401 Basic Auth popup çıkmasına sebep olan sorun giderildi; `onReceivedHttpAuthRequest` engellenerek kullanıcının doğrudan standart HTML giriş formuna erişmesi sağlandı.
- **Otomatik Yönlendirme:** `malapp://auth` yönlendirmesi yakalanarak token takası sorunsuz tamamlanıyor. `KitsugiWebViewDialog` için de aynı Basic Auth koruması uygulandı.

### 📖 5. Manga "Oku" Navigasyonu Düzeltildi
- **Detay Sayfası Üstünde Açılış:** Manga detay sayfasından (örn. Berserk) "Oku" butonuna basıldığında kaynak arama ekranı artık detay sayfasının üzerinde açılıyor.
- **Geri Dönüş Korundu:** `detailBackStack` temizlenmesi engellendi; kaynak arama ekranında geri tuşuna basıldığında Keşfet sekmesine düşmek yerine doğruca ilgili manganın detay sayfasına dönülüyor.

### 📐 6. Keşfet Raf Başlıklarına 20dp Kenar Boşluğu
- **Hizalama Düzenlendi:** "Yayındaki Animeler", "Yaklaşan Animeler", "Tümünü Gör" dahil tüm raf başlıklarına ve üst kısımlara kartlarla birebir aynı 20dp iç boşluk (`padding`) verildi; ekran kenarına yapışık görünüm giderildi.

### 🖼️ 7. "Tümünü Gör" Grid Görünümü ve 2:3 Poster Oranı
- **Tam Poster Görünümü:** Poster alanı sabit 210dp yerine 2:3 dikey en-boy oranına (`aspectRatio(2f / 3f)`) geçirildi; afişlerin kırpılması önlendi.
- **Uyumlu Kolon Düzeni:** Stüdyo sayfasıyla eşitlendi: standart telefonlarda 3 sütun, geniş ekranlarda 4 sütun, tabletlerde 5 sütun.

### 🔔 8. Eklenti Kurulumunda Bildirim Çubuğu (`AddonInstallNotifier`)
- **Tam Ekran Engelleyici Panel Kaldırıldı:** Keşfet ekranını kaplayan büyük "Eklentiler Kuruluyor" paneli tamamen kaldırıldı. İlerleme sessizce Android bildirim çubuğunda gösteriliyor ("10 / 165 · İndiriliyor: Anizm"). Ayarlar ekranındaki repo kartı ilerlemesi korundu.

### 🎨 9. TMDB ve Fanart Galeri/Logo İzolasyonu (Uncharted / Family Full House Düzeltmesi)
- **Film ve Dizi ID Alanı Ayrıldı:** TMDB'de film ve dizi ID'leri birbirinden izole edildi; film sayfasına yalnızca film afiş/arkaplanları, dizi sayfasına yalnızca dizi görselleri bağlandı.
- **Başlık Eşleşme Doğrulaması:** TMDB başlığı medya başlığıyla uyuşmadığında logonun yanlış yapıma uygulanması engellendi.

### 🏷️ 10. Arama Sayfasında Orijinal Marka Logoları (Emoji Yerine Vektör Logolar)
- **Orijinal Marka Logoları Entegre Edildi:** Kaynağa özel arama başlıklarındaki emojiler (AniList, MyAnimeList, TMDB, Shikimori, Kitsu, Simkl, Bangumi) kaldırılarak `KitsugiPlatformLogo` vektör çizimleri (`ic_logo_*`) yerleştirildi.
- **Filtre ve Sıralama Panelleri:** Başlıklar ve sonuç kartlarındaki rozetler resmi marka logolarıyla zenginleştirildi; uluslararasılaşma (i18n) tamamlandı.

### 🛡️ 11. "Çok Yönlü Eşitleme" Diyaloğu Uyarı Kartı Sadeleştirildi
- **Katlanabilir Güvenlik Notu (`SafetyNote`):** Tekrarlanan turuncu uyarı kutusu tek bir katlanabilir karta dönüştürüldü; güvenlik ikonu, tek cümle özet ve animasyonlu yasal metinle kompakt hale getirildi.

### 🧩 12. Eklentiler ve Kaynaklar Sayfası Düzenlendi
- **Segment Seçici & Ortak Kartlar:** Mobil ekranda RealDebrid giriş alanı temizlendi; sekmeler segment seçiciye geçirildi ve ortak boş-durum bileşenleriyle zenginleştirildi.

### 🌸 13. Shikimori Stüdyo Kapakları ve Bangumi Düzenleme Düzeltmeleri
- **GraphQL Poster Çözümleme:** Stüdyo yapımlarındaki eksik kapaklar 50'lik gruplarla GraphQL üzerinden çekildi.
- **Bangumi Düzenle Desteği (`editingSourceHint`):** Bangumi detayından düzenleme açıldığında Simkl'e düşme engellendi; Bangumi durum pill'leri ve rozetleri gösterildi.

### 🌐 14. Çeviri Motoru İyileştirmeleri & Harici Çeviri Uygulaması
- **Dinamik Dil:** Ayarlanan hedef dile göre duyarlı etiketleme yapıldı; 429 rate limit koruması sağlandı.
- **Harici Uygulama Açılışı:** Çeviri düğmeleri kullanıcının harici çeviri uygulamasına (`openTranslator`) bağlandı.

---

## 🇬🇧 English (v2.4.237)

### ⚡ 1. Library Mapping Performance Bottleneck Fixed (`ExploreMapping`)
- **Root Cause Eliminated:** For every card not in the user list, the code performed an O(N) full scan over the user's entire connected account library and normalized titles on the UI thread.
- **Direct Index Resolution:** Eliminated full library fallbacks. Candidate searches are strictly index-driven. Added `realMalId` index keys to preserve genuine matches, eliminating frame drops.

### 🎯 2. Hero Section Click Routing & Prominent Counter Badge
- **Dominant Item Resolution (`openVisibleHeroItem`):** In the multi-layer parallax hero pager, invisible neighboring pages no longer intercept touch events. Taps resolve the visually dominant item (`round(currentPage + offset)`) directly (tapping One Piece opens One Piece, tapping Frieren opens Frieren).
- **Thicker Dots & Numerical Counter Badge:** Pagination dots thickened and emphasized (7dp); added an `N / M` (e.g. 3 / 11) numerical badge indicator updating via `derivedStateOf`.

### 🚀 3. "See All" Grid Race Condition & Visual Overlap Fix
- **Enrich Snapshot Merging:** Resolved the race condition during fast scrolling where page 1 enrichment overwritten newly loaded page 2 results. Snapshot merge logic ensures persistent, smooth data flow without flickering or jumping cards.
- **Unique & Stable Identity (`exploreIdentity`):** Switched to robust composite keying (`source:type:malId:tmdbId:cs3UrlHash`), preventing TMDB movie/TV and extension collisions.
- **Stable Compose State:** Replaced `rememberSaveable` state restores with `rememberLazyGridState()` and `rememberLazyListState()` to eliminate Bundle index drift and overlapping layouts.

### 🔑 4. In-App MyAnimeList (MAL) OAuth & Basic Auth Elimination
- **In-App OAuth Dialog (`KitsugiOAuthWebViewDialog`):** Replaced external browser redirection with an optimized in-app WebView for seamless authentication.
- **HTTP Basic Auth Dialog Suppressed:** Intercepted and blocked 401 Basic Auth browser dialogs (`onReceivedHttpAuthRequest`), presenting MAL's native HTML login form directly.
- **Redirect Capture:** Seamlessly catches `malapp://auth` redirects and performs the OAuth token exchange automatically.

### 📖 5. Manga "Read" Navigation Fixed
- **Open on Top of Detail:** Tapping "Read" from a manga detail screen (e.g. Berserk) now presents the source search page on top of the media detail view.
- **Back Navigation Restored:** Prevented clearing `detailBackStack`; pressing the Android Back button now reliably returns to the manga detail screen instead of falling back to Explore.

### 📐 6. Explore Shelf Header 20dp Padding
- **Uniform Alignment:** Added 20dp horizontal padding to shelf headers ("Airing Anime", "Upcoming Anime", "See All", etc.) matching the media cards, resolving edge-clipping.

### 🖼️ 7. "See All" Grid & 2:3 Poster Aspect Ratio
- **Full Poster Presentation:** Switched fixed 210dp height to 2:3 vertical aspect ratio (`aspectRatio(2f / 3f)`), completely preventing poster cropping.
- **Responsive Columns:** Standardized column count to 3 columns on phones, 4 on wide phones, and 5 on tablets.

### 🔔 8. System Notification for Bulk Addon Installs
- **Removed Full-Screen Overlay:** The blocking "Installing Addons" overlay was removed. Real-time progress is now presented quietly via Android notification bar ("10 / 165 · Downloading: Anizm").

### 🎨 9. TMDB & Fanart Artwork Isolation (Uncharted / Family Full House Fix)
- **ID Namespace Separation:** Film and TV show IDs are strictly separated; film views only receive movie artwork, TV views only receive show artwork.
- **Strict Title Verification:** Vector and Fanart logos are rejected if the TMDB title does not match the media title.

### 🏷️ 10. Official Brand Logos in Search (Vector Logos)
- **Official Brand Logos:** Replaced emojis across search engine headers (AniList, MyAnimeList, TMDB, Shikimori, Kitsu, Simkl, Bangumi) with official vector brand icons (`ic_logo_*`).
- **Sheets & Result Badges:** Filter sheets, chips, and result badges now feature platform logos.

### 🛡️ 11. Streamlined Cross-Sync Safety Disclaimer
- **Collapsible Safety Card (`SafetyNote`):** Compacted disclaimer text into a collapsible surface with security icon and expandable legal text.

### 🧩 12. Addons & Sources UI Modernization
- **Segmented Tabs:** Removed mobile RealDebrid token input; added segmented tab selector, counter badges, and shared empty state components.

### 🌸 13. Shikimori Studio Posters & Bangumi Edit Context
- **GraphQL Poster Batching:** Batched 50 items per query to retrieve missing studio production covers.
- **Bangumi Edit Routing:** Preserved Bangumi source context to display native status pills and badges instead of Simkl.

### 🌐 14. Translation Engine & External App Integration
- **Target Language Responsive:** Dynamic target language chip and 429 backoff protection.
- **Direct App Launch:** Universal translator action opens preferred external translation app via `openTranslator`.

---

## 🇹🇷 Türkçe (v2.4.236) — Depolama Şişmesi, Performans & Dil Düzeltmeleri

### 🗄️ 1. Merkezî Depolama Bekçisi (`StorageJanitor`) & Depolama Ayarları (~4 GB Şişme Çözümü)
- **Kök Neden Çözüldü:** P2P torrent önbelleği (2 GB), görsel önbelleği (512 MB), manga önbelleği (~150 MB), video önizlemeleri ve sınırsız büyüyen veritabanı kayıtları merkezî bir temizlik mekanizması olmadığı için kurulumu ~4 GB'a kadar şişiriyordu.
- **Merkezî Bekçi (`StorageJanitor` & `StorageMaintenanceWorker`):** Disk kullanımını kalem kalem ölçen, bütçe ve yaş kurallarına göre sessizce budayan sistem eklendi. Ağ şartı olmayan günlük WorkManager bakımı ve 6 saatte bir hafif açılış bakımı devreye alındı.
- **Ayarlar > Depolama Ekranı (`StorageSettingsContent`):** Kullanıcıların disk kullanımını kalem kalem görmesini ve tek dokunuşla güvenli önbellekleri temizlemesini sağlayan modern depolama paneli eklendi (hesap, indirmeler ve geçmiş asla silinmez).
- **Atıl Temizlik Kodları Canlandırıldı:** Mobil veride çalışmayan detay önbelleği budaması koşulsuz hale getirildi; `video_cache` 30 gün, `translation_cache` 5.000 satır ve arama geçmişi 500 kayıt tavanıyla sınırlandı. Altyazı arşivleri 14 günde bir temizleniyor.

### ⚡ 2. Kasma ve Donma Zinciri Kırıldı (Kare Gecikmesi %96 İyileştirildi)
- **Manga Okuyucu Tarama Yükü Kaldırıldı:** Her manga sayfası yazıldığında tüm klasörün taranıp boyut toplanması yerine süreç genelinde paylaşılan artımlı sayaç getirildi.
- **Kare Gecikmesi ve Jank Azaltıldı:** Logcat/gfxinfo analizlerindeki 1200 ms'lik takılmalar giderildi; p99 kare süresi 1200 ms'den 46 ms'ye düşürüldü, takılma oranı %30'dan %8'e indirildi.
- **İç İçe Looper ve Disk Logcat Kaldırıldı:** `KitsugiApplication` içindeki ek ana looper döngüsü (`Looper.loop()`) ve arka planda diske sürekli yazan `logcat -f` süreci temizlendi.
- **Soğuk Açılış & Oynatma İlerlemesi:** İndirme listesi ve izleme geçmişinin ana iş parçacığında JSON ayrıştırması IO'ya taşındı; oynatma sırasındaki disk yazımları 2,5 sn'lik toplulaştırmaya alındı.
- **Degrade Shader & Brush Önbelleği (`GradientBrushCache`):** Her çizim karesinde yeni shader/fırça tahsisatı engellendi, AniList 429 rate limit bekleme koruması eklendi.

### 🛡️ 3. GPU RenderThread Çökme Koruması (`Modifier.safeBlur`)
- **Donanımsal Bulanıklık Güvenliği:** API 31 altındaki cihazlarda ve riskli GPU sürücülerinde `libhwui` SIGSEGV çökmelerine yol açan koşulsuz `.blur()` çağrıları `Modifier.safeBlur()` ile korumaya alındı; aşırı yarıçap değerleri sınırlandı.
- **Üst Üste Binen GPU Katmanları Temizlendi:** `KitsugiCinematicLoadingScreen` içindeki çift GPU bulanıklık katmanı teke indirildi; `MangaDetailScreen` ve eklenti diyaloglarındaki ağır bulanıklıklar optimize edildi.

### 🌐 4. Android 13+ Dil Değiştirme ve Çökme Düzeltmesi
- **Aktif Dil Senkronizasyonu (`locales_config.xml`):** Android 13+ `LocaleManager` için Türkçe ve İngilizce dilleri açıkça tanımlandı (`locales_config.xml`), manifest entegrasyonu tamamlandı. "İngilizce seçili ama Türkçe görünüyor" sorunu giderildi.
- **API 33+ Modern Dil API'si:** Dil geçişi `AppCompatDelegate.setApplicationLocales()` ile modernize edildi; dil değişimi sonrası çağrılan `recreate()` kuyruğun sonuna alınarak döngü ve çökme engellendi.
- **Dize Formatı Hataları Düzeltildi:** Dil dosyalarındaki hatalı yüzde formatları (`Normal (%0)` -> `Normal (0%)`) düzeltildi.

### 📐 5. Kart Düzeni, Metin Kesilmesi ve Geri Sayım Optimizasyonu
- **Rozet ve Başlık Kesilmesi Çözüldü:** 2 satırlı başlık, alt başlık ve sonraki bölüm rozetinin alt kısımdan taşmasını engellemek için portre kart metin alanı 182 dp'ye (`EXPLORE_CARD_PORTRAIT_TEXT_HEIGHT`) yükseltildi.
- **10 Saniyelik Donma Döngüsü Kırıldı:** `NextAiringChip` içindeki her 10 saniyede bir ekranı tetikleyen sayaç 30–60 saniyeye çıkarıldı. `KitsugiUpcomingAirEnricher` iptal istisnaları (`CancellationException`) doğru yönetildi.

### 🧲 6. Yatay Satırlarda Akıcı Kart Yaslama (Snap Scrolling)
- **Sola Yapışan Kaydırma Geri Getirildi:** Keşfet vitrinleri, arama sonuçları, detay rafları ve profil sayfalarındaki yatay kart listelerine `KitsugiScrollDefaults.rememberRowSnapFling(state)` eklendi; kartlar kaydırma bitiminde sola akıcı ve muntazam biçimde oturuyor.

### 🔑 7. Liste Anahtarları (Key Collision) & "Tümünü Gör" Sonsuz Yükleme Düzeltmesi
- **Tür Bazlı Kimlik Ayrımı (`KitsugiLazyKeys.kt`):** Keşfet, arama ve ızgara listelerinde aynı sayısal kimliğe sahip anime ve mangaların çakışmasını engellemek için anahtarlara medya türü (`type`) dahil edildi.
- **Sonsuz İstek Döngüsü Sınırlandı:** `FullScreenMediaGridPage` sayfasında kullanıcı kaydırmadan tetiklenen sürekli sayfa çekme döngüsüne kaydırma başına bütçe eklendi.

---

## 🇬🇧 English (v2.4.236) — Storage Bloat, Performance & Localization Fixes

### 🗄️ 1. Central Storage Janitor (`StorageJanitor`) & Settings > Storage (~4 GB Bloat Fix)
- **Root Cause Resolved:** P2P torrent cache (2 GB), image cache (512 MB), manga cache (~150 MB), video previews, and unconstrained Room cache rows accumulated to ~4 GB installations due to lack of central lifecycle management.
- **Central Storage Janitor (`StorageJanitor` & `StorageMaintenanceWorker`):** Added a unified storage engine measuring disk usage per bucket, pruning data via age and size thresholds. Added network-unconstrained daily WorkManager jobs and 6-hour interval launch maintenance.
- **Settings > Storage Screen (`StorageSettingsContent`):** Added an interactive storage dashboard showing per-category sizes, item counts, and safe one-tap cleanup (user accounts, downloads, and history are strictly preserved).
- **Revived Dormant Cleanup Routines:** Unblocked detail cache pruning by removing Wi-Fi/battery-only restrictions; pruned `video_cache` beyond 30 days, capped `translation_cache` to 5,000 rows, limited search history to 500 rows, and auto-removed subtitle archives older than 14 days.

### ⚡ 2. Broken Jank Chains & Systemwide Frame Latency Fix
- **Manga Reader Cache Overhead Eliminated:** Switched from costly whole-directory walks on every written page to an incremental memory-backed byte counter.
- **96% Frame Latency Reduction:** Eliminated 1200ms frame stalls seen in logcat/gfxinfo; p99 latency dropped from 1200ms to 46ms, and jank rate fell from ~30% to 8.13%.
- **Removed Nested Looper & Disk Logcat:** Removed redundant nested `Looper.loop()` in `KitsugiApplication` and background `logcat -f` disk redirection.
- **Cold Start & Playback Progress:** Deferred download list and watch history JSON parsing off the main thread; debounced video playback watch progress writes to 2.5s with background flush.
- **Cached Gradient Shaders & Brushes (`GradientBrushCache`):** Cached linear gradients and draw node brushes, stopping continuous per-frame shader allocations. Added AniList 429 rate limit cooldown.

### 🛡️ 3. GPU RenderThread Crash Safety (`Modifier.safeBlur`)
- **Hardware Blur Guard:** Replaced unconditional `.blur()` modifiers across screens with `Modifier.safeBlur()`, skipping blur on API < 31 and clamping blur radius to prevent `libhwui` RenderThread SIGSEGV crashes on vulnerable GPUs.
- **Eliminated Redundant GPU Layers:** Removed nested double GPU blur pass in `KitsugiCinematicLoadingScreen`; optimized heavy blur layers in `MangaDetailScreen` and addon dialogs.

### 🌐 4. Android 13+ Per-App Language Switching & Crash Fix
- **Explicit Locale Configuration (`locales_config.xml`):** Declared English and Turkish locales in `res/xml/locales_config.xml` and manifest to satisfy Android 13+ `LocaleManager` requirements, resolving "English selected but Turkish shown" issues.
- **Modernized Locale API for API 33+:** Switched to `AppCompatDelegate.setApplicationLocales()`. Deferred activity `recreate()` call via main looper handler post to prevent activity recreation crashes.
- **Fixed Malformed String Templates:** Corrected invalid percentage token placements in localized resources (`Normal (%0)` -> `Normal (0%)`).

### 📐 5. Card Layout, Text Clipping & Countdown Timer Optimization
- **Badge & Title Overflow Fix:** Increased portrait card text container height to 182dp (`EXPLORE_CARD_PORTRAIT_TEXT_HEIGHT`), ensuring two-line titles, subtitles, and next-airing chips render cleanly without clipping.
- **Eliminated 10-Second Frame Freeze:** Re-tuned `NextAiringChip` ticker interval from 10s to 30-60s. Handled `CancellationException` properly in `KitsugiUpcomingAirEnricher` to avoid negative caching loops.

### 🧲 6. Horizontal Shelf Snap Scrolling
- **Restored Left-Edge Snap Fling:** Added `KitsugiScrollDefaults.rememberRowSnapFling(state)` (`SnapPosition.Start`) across Explore shelves, search rows, detail recommendations, and profile collections.

### 🔑 7. Lazy Column/Row Key Collision & Infinite Load Loop Fix
- **Type-Aware Identity Keys (`KitsugiLazyKeys.kt`):** Included media type (`type`) in Compose item keys across 11 shelves and grids, preventing collision when anime and manga share the same ID.
- **Bounded Pagination Budget:** Fixed continuous unprompted pagination in `FullScreenMediaGridPage` by requiring active scroll gestures.

---

## 🇹🇷 Türkçe (v2.4.235)

### 🎨 1. Premium Açılır Menü Tasarımı (`KitsugiDropdownMenu`)
- **Özel Popup & Yumuşak Animasyon:** Material3'ün varsayılan menüsü yerine uygulamanın koyu yüzeyine uyumlu, 20dp yuvarlak köşeli, kenarlıklı, gölgeli ve fade + hafif büyüme animasyonlu özel menü bileşeni uygulandı.
- **Vurgulu Seçim & Onay İkonu:** Seçili öğeler yarı saydam vurgu rengi ve onay işaretiyle belirginleştirildi; akıllı konumlandırma ile ekrandan taşma ve klavye çakışmaları engellendi.
- **Evrensel Entegrasyon:** Sezonluk filtreler, Listem sıralama paneli, altyazı gecikmesi, profil etkinlik menüsü ve arama filtreleri dahil tüm açılır menüler yeni bileşene taşındı.

### ⚡ 2. Keşfet, Arama ve Tümünü Gör Kaydırma Performansı Düzeltmesi (v2)
- **Kaydırma Recomposition Döngüsü Kırıldı (`RetainedLazyListState`):** Kaydırma konumu Compose State yerine düz bir nesnede saklanarak her pikselde tüm ekranın yeniden çizilmesi kökten engellendi.
- **Shimmer İçerik Ağacı Kararlılığı (`KitsugiShimmer`):** Yükleme durumu değiştiğinde tüm içerik ağacının ve Hero Pager durumunun sökülüp sıfırlanması durduruldu; dinamik fırça tek bir sabit provider içine alındı.
- **Hero Backdrop Toplu Yayın:** Vitrin arka planlarının TMDB'den çekilmesi 250 ms'lik pencerede gruplandırılarak gereksiz ekran yenilemeleri sonlandırıldı.
- **Fonksiyon Referansı Optimizasyonu:** `AppRoot` içerisindeki liste kontrol fonksiyonları `remember` ile sabitlenerek çocuk kartların fazladan çizilmesi engellendi.

### 🕷️ 3. TMDB & Simkl Kurgusal Karakter Detay Düzeltmesi (Peter Parker / Spider-Man)
- **Doğrulanmış Karakter Eşleşmesi:** Jikan (MAL) yedek aramasında ilk sonucun körlemesine alınması engellendi; yalnızca isim gerçekten eşleştiğinde kabul ediliyor.
- **Birincil Ad Ayrımı:** TMDB'deki "Gerçek Ad / Kurgusal Ad" kalıplarında arama birincil isimle yapılarak genel lakapların (ör. "Spider" -> Makishima Yuusuke) yanlış anime profillerini açması çözüldü.

### 📺 4. TMDB Yayıncı Ağları ve Yapım Şirketi Ayrımı
- TV yayıncı ağları (`/network/{id}`) ile stüdyo/yapım şirketleri (`/company/{id}`) farklı uç noktalara ayrılarak yanlış kimlik yönlendirmeleri düzeltildi.

---

## 🇬🇧 English (v2.4.235)

### 🎨 1. Premium Dropdown Menu Redesign (`KitsugiDropdownMenu`)
- **Custom Popup & Smooth Animations:** Replaced Material default dropdowns with a sleek, dark-themed popup featuring 20dp rounded corners, borders, shadows, and subtle fade-scale animations.
- **Active State Highlights:** Selected items feature accent semi-transparent backgrounds and check icons, with collision-aware screen positioning and vertical scrollability.
- **Universal Migration:** Upgraded seasonal filter sheets, My List sort dialogs, subtitle delay panels, profile activity menus, and search filter chips.

### ⚡ 2. Explore, Search & See All Scroll Jank Fix (v2)
- **Eliminated Scroll Recomposition Loop (`RetainedLazyListState`):** Shifted scroll position tracking from Compose State to a plain object read only during state saving, stopping per-frame full-screen recompositions.
- **Static Shimmer Tree (`KitsugiShimmer`):** Kept the composition hierarchy stable across loading transitions without tearing down the hero pager and shelf tree.
- **Debounced Hero Backdrops:** Grouped backdrop resolution flushes to 250ms windows in `ExploreViewModel`, preventing thrashing during initial loads.
- **Stable Function References:** Stabilized `AppRoot` lambda references with `remember` to preserve composable skippability across grids and cards.

### 🕷️ 3. TMDB & Simkl Fictional Character Details Fix (Spider-Man / Peter Parker)
- **Verified Character Matching:** Replaced unvalidated first-result Jikan fallback with explicit name matching and ambiguity checks.
- **Primary Name Splitting:** TMDB dual-identity names ("Real Name / Fictional Name") now search by primary identity, preventing common alias words like "Spider" from pulling unrelated anime characters (Makishima Yuusuke).

### 📺 4. TMDB TV Networks vs Production Companies Endpoint Separation
- Dedicated `/network/{id}` routing for TV networks and separate company endpoints for studios/producers.

---

## 🇹🇷 Türkçe (v2.4.234)

### 🏢 1. Bangumi Yapımcı / Yayıncı Çipleri Tıklanabilirlik ve Kimlik Çözümleme Düzeltmesi
- **Ad Tabanlı Kurum Çözümleme (`resolveCompanyPersonId`):**
  - Bangumi infobox verilerinde kimliği olmayan kurumlar için çip tıklanabilirliği açıldı. Kimliği olmayan çipler artık tıklanarak hedef sayfaya kurum adını taşır (`KitsugiDetailComponents.kt`).
  - Hedef stüdyo/yapımcı sayfası kurum adıyla Bangumi API'de (`/v0/search/persons`, `type=2`) arama yapar; şirket bulunamazsa yapımcı şahıslar (`type=1`) kabul edilerek kişinin yapım listesi açılır (`KitsugiStudioClient.kt`).
- **Enrich Kapasitesi ve Zaman Aşımı Artırıldı:**
  - `KitsugiBangumiDetailClient.kt`: Ön kimlikleme limiti 8'den 24'e, toplu sorgu zaman aşımı 6 saniyeden 10 saniyeye çıkarıldı. Steins;Gate gibi 15+ kurumlu zengin detay sayfalarında tüm yayıncı ve yapımcılar önceden kimliklenir.
- **Kimliksiz Sayfalarda Yeniden Deneme:**
  - `StudioDetailViewModel.kt`: Sadece `studioId > 0` şartı yerine `name != null` durumu da yeniden deneme mekanizmasına dahil edildi.

### 🌐 2. Kapsamlı UI Yerelleştirmesi (i18n Tamamlama — Batch 1 & Bangumi)
- **Tam TR / EN Dil Dosyası Paritesi:**
  - `values/strings.xml` ve `values-en/strings.xml` dosyalarına 446 yeni kaynak eklendi; toplam **4194 anahtar** ile %100 parite sağlandı.
- **1000'den Fazla Gömülü Türkçe Metnin Taşınması:**
  - Detay sayfası: Stüdyolar, Yapımcılar, Yayıncı Ağlar, Etiketler, Türler, sekmeler, bilgi kartları, puanlama ve spoiler kontrolleri.
  - Stüdyo & Yapımcı sayfası: "Yapımlar", "X içerik", filtreleme çipleri (Tümü/Anime/Manga/Film/Dizi), sıralama seçenekleri, boş durum ve hero rozetleri.
  - Eklenti "Tümünü Gör" sayfası, tam ekran medya ızgarası ve görsel galeri diyaloğu.
- **Merkezi Yardımcılar:**
  - `MediaType.localizedLabel()`, `GalleryCategory.localizedLabel()`, `WatchStatus.localizedLabel()` ile tüm etiketler seçili uygulama diline (`isTurkish()`) göre dinamik olarak üretilir.

---

## 🇬🇧 English (v2.4.234)

### 🏢 1. Bangumi Producer / Network Chips Clickability & Identity Resolution Fix
- **Name-Based Entity Resolution (`resolveCompanyPersonId`):**
  - Unlocked clickability for producer and network chips originating from Bangumi infoboxes without upfront IDs (`KitsugiDetailComponents.kt`).
  - Target studio/producer screens now resolve the company by name via Bangumi API (`/v0/search/persons`, `type=2`); falls back to person records (`type=1`) for individual producers, displaying their production list (`KitsugiStudioClient.kt`).
- **Increased Enrichment Capacity & Timeout:**
  - `KitsugiBangumiDetailClient.kt`: Increased lookup limit from 8 to 24 and batch timeout from 6s to 10s. Large entries like Steins;Gate (15+ organizations) now resolve all producers and TV networks in advance.
- **Retry Support for Name-Resolved Pages:**
  - `StudioDetailViewModel.kt`: Allows retrying failed loads when `name != null`, even if the initial entity ID was zero.

### 🌐 2. Comprehensive UI Localization (i18n Completion — Batch 1 & Bangumi)
- **100% TR / EN String Parity:**
  - Added 446 new string resources across `values/strings.xml` and `values-en/strings.xml`, bringing total synchronized keys to **4194**.
  - Replaced over 1000 hardcoded Turkish UI strings across 185+ Kotlin files with `stringResource` and `context.getString`.
- **Extended Area Coverage:**
  - Detail screens: Studios, Producers, Networks, Tags, Genres, tabs, info grids, ratings, and spoiler controls.
  - Studio & Producer screens: Productions header, item counts, filter chips (All/Anime/Manga/Movie/TV Series), sorting options, empty states, and hero pills.
  - Addon "See All" full-screen grids, global media grids, and image gallery viewer.
- **Centralized Label Helpers:**
  - Introduced `MediaType.localizedLabel()`, `GalleryCategory.localizedLabel()`, and `WatchStatus.localizedLabel()` ensuring all models reflect the active language dynamically.

---

## 🇹🇷 Türkçe (v2.4.233)

### 🚀 1. Kaydırma Takılmaları (Scroll Jank) ve Recomposition Optimizasyonları
- **`derivedStateOf` ile Gereksiz Yeniden Çizimlerin Önlenmesi:**
  - `ExploreScreen.kt`: `lazyListState.firstVisibleItemIndex` doğrudan okunması yerine `derivedStateOf` içine alındı (`isHeroVisible`), her scroll pikselinde tüm vitrin ve alt ağacının yeniden recompose olması engellendi (`ExploreScreen.kt`).
  - `FullScreenMediaGridPage.kt`: Kayan başlık görünürlüğü (`showFloatingHeader`), `derivedStateOf` içine alınarak liste veya ızgara kaydırılırken her karede tetiklenen recomposition döngüsü kesildi (`FullScreenMediaGridPage.kt`).
- **Sabit Identity Tabanlı Lazy Key'ler (Index Kaldırıldı):**
  - Grid ve listelerdeki key yapılarından pozisyon indeksi (`$idx`, `_g$idx`, `_l$idx`) kaldırıldı. Filtreleme, sıralama ve sayfalama esnasında kartların sıfırdan yeniden oluşturulması engellendi; stabil identity (`${source}_${malId}_${tmdbId}_${cs3Url.hashCode()}`) ile kart composable yeniden kullanımı sağlandı (`KitsugiHorizontalMediaSection.kt`, `FullScreenMediaGridPage.kt`, `SearchScreen.kt`, `KitsugiMediaGridDialog.kt`).
- **Coil Crossfade Kaldırıldı (Akıcı Fling Deneyimi):**
  - `KitsugiApplication.kt`: Hızlı kaydırma (fling) anında ekrana giren her kartın 150ms crossfade animasyonu başlatarak ana iş parçacığını (UI thread) kilitlemesi engellendi; görsel yükleme akışı doğrudan hazırlandığında render edilir (`KitsugiApplication.kt`).
- **Mobil "Tümünü Gör" Diyaloğu LazyVerticalGrid Dönüşümü:**
  - `KitsugiMediaGridDialog.kt`: 40+ kartı aynı anda oluşturan ve bellek baskısı yaratan `FlowRow + verticalScroll` yapısı, sadece ekranda görünen öğeleri oluşturan yüksek performanslı `LazyVerticalGrid` bileşenine dönüştürüldü (`KitsugiMediaGridDialog.kt`).
- **`contentType` ile Composable Havuzlama (Item Pooling):**
  - Tüm LazyColumn, LazyRow ve LazyVerticalGrid yapılarına açık `contentType` tanımları eklendi (`media_card`, `h_media_card`, `seasonal_card`, `ranking_card`, `search_row`, `char_staff_row`); ekran dışına çıkan öğelerin Compose tarafından verimli bir şekilde havuzlanıp geri dönüştürülmesi sağlandı.
- **Kart İçi Bellek ve Çizim Tahsislerinin (Allocations) Azaltılması:**
  - `KitsugiExploreMediaCard.kt`, `KitsugiRankingMediaCard.kt`, `DetailedSeasonalMediaCard.kt`, `SearchResultRow.kt`: `getDisplayTitle(titleLanguage)` ve `Shape` nesneleri `remember` ile önbelleğe alındı.
  - `KitsugiNsfwImage.kt`: `AsyncImage` için `onError` lambda'sı `remember` ile sabitlenerek her recomposition'da gereksiz yeniden sorgulama engellendi.

### 🛡️ 2. Sayfa Geri Çekilmesi (Scroll Bounce), Kilitlenme ve Zıplama Düzeltmeleri
- **Dokunmatik Cihazlarda Odak Geri Çekilmesinin Engellenmesi:**
  - `TvFocusModifier.kt`: Telefon ve tabletlerde kartların odak alması kapatıldı (`.focusProperties { canFocus = false }`). Kartların dokunmatik kaydırma sırasında odak alıp `BringIntoView` ile listeyi parmağın tersine geri çekmesi engellendi.
  - `KitsugiScrollDefaults.kt` & `AppRoot.kt`: Telefon ve tablet için no-op `rememberTouchPassthroughSpec()` eklendi; TV cihazları D-pad odak davranışını korurken dokunmatik cihazlarda kaydırma hareketi tamamen parmağa devredildi.
- **Neon Çerçevelerden Yazılım Bulanıklığı Gölgesinin Kaldırılması:**
  - `KitsugiNeonGlowCard.kt`: RenderNode katmanında yazılım tabanlı GPU blur düşmesine ve kaydırmanın kilitlenmesine neden olan `Modifier.shadow` kaldırıldı; kart çerçevesi yalnızca yüksek performanslı degrade kenarlık olarak çizilir.
- **Nested LazyRow'ların Düz `horizontalScroll` Satırlarına Dönüştürülmesi:**
  - `FullScreenMediaGridPage.kt`, `StudioDetailPage.kt`, `AddonFullScreenGridPage.kt`: Ana dikey ızgara/liste kaydırılırken ölçüm döngüsünü tetikleyip kartları ileri-geri zıplatan iç içe tür çipler `Row + horizontalScroll(rememberScrollState())` yapısına geçirildi (`GridGenreChipRow`).
- **Başlık & Sekme Geçişlerinde Histerezis ve Solma Animasyonu:**
  - `DetailPageScaffold.kt`, `CharacterDetailPage.kt`, `StaffDetailPage.kt`, `StreamScreenContent.kt`: Yüzen başlıkların yükseklik değiştirerek viewport'u daraltması engellendi (`fadeIn`/`fadeOut`). Sınır geçişinde histerezis eklendi; sekme değişimindeki `scrollToItem(2)` zıplaması kaldırıldı.
  - `MyListComponents.kt`: Piksel bazlı yükseklik hesabı yerine eşik aşıldığında bir kez kapanan (`headerCollapsed`) histerezisli yapıya geçildi.
- **Yatay Satırlardan Snap Fling Davranışının Kaldırılması:**
  - `ModernHomeRows.kt`, `ExploreComponents.kt`, `ReviewsTab.kt`, `KitsugiHorizontalMediaSection.kt`, `SearchScreen.kt`, `KitsugiUserProfileScreen.kt`, `UserProfileStatsTab.kt`, `AniListProfileContent.kt`, `MalProfileContent.kt`: İç içe satırlardaki `rememberSnapFlingBehavior` kaldırılarak dikey kaydırma hareketinin çalınması ve kilitlenmesi önlendi.
- **Non-Observable Kaydırma Konumu Saklama (Recomposition Döngüsü Kesildi):**
  - `KitsugiProfileViewModel.kt` & `KitsugiUserProfileViewModel.kt`: Kaydırma konumları `mutableIntStateOf` yerine düz `Int` değişkenlere çevrildi; `snapshotFlow` ile toplanarak her pikselde sayfanın baştan kurulması önlendi.
  - `RetainedLazyListState.kt`: `RetainedScrollPosition` ve `listSaver` mimarisiyle yeniden yazılarak sayfa sıfırlanmaları giderildi.
  - `recordStablePageHeight`: Pager ölçümlerindeki 4px altındaki ölçüm gürültüsü filtrelenerek sonsuz yeniden ölçüm önlendi.
- **Teknik Dokümantasyon:** `PLAN_TASK_KAYDIRMA_ZIPLAMA_FIX_2026-10-10.md`, `PLAN_TASK_SCROLL_JANK_PERFORMANS_FIX_2026-10-10.md`, `SCROLL_JANK_FIX_RAPORU_2026-10-10.md`.

---

## 🇬🇧 English (v2.4.233)

### 🚀 1. Scroll Jank Elimination & Recomposition Optimizations
- **`derivedStateOf` Scroll State Decoupling:**
  - `ExploreScreen.kt`: Decoupled `lazyListState.firstVisibleItemIndex` via `derivedStateOf` (`isHeroVisible`), eliminating redundant recompositions of the hero section and entire view tree on every scroll frame (`ExploreScreen.kt`).
  - `FullScreenMediaGridPage.kt`: Wrapped floating header visibility (`showFloatingHeader`) in `derivedStateOf`, preventing frame-by-frame recomposition cascades during list and grid scrolling (`FullScreenMediaGridPage.kt`).
- **Stable Identity-Based Lazy Keys (Eliminated Index Dependency):**
  - Replaced index-dependent keys (`$idx`, `_g$idx`, `_l$idx`) with pure, stable identity keys across all lists and grids (`${source}_${malId}_${tmdbId}_${cs3Url.hashCode()}`). Filtering, sorting, and pagination no longer invalidate composable item state, maximizing reuse (`KitsugiHorizontalMediaSection.kt`, `FullScreenMediaGridPage.kt`, `SearchScreen.kt`, `KitsugiMediaGridDialog.kt`).
- **Disabled Coil Crossfade for Butter-Smooth Fling:**
  - `KitsugiApplication.kt`: Removed 150ms image crossfade transitions during rapid scrolling/flinging, eliminating main UI thread bottlenecks caused by concurrent fade animators on entry cards (`KitsugiApplication.kt`).
- **Converted Mobile "See All" Dialog to `LazyVerticalGrid`:**
  - `KitsugiMediaGridDialog.kt`: Replaced the legacy `FlowRow + verticalScroll` layout (which instantiated 40+ cards simultaneously) with a virtualized `LazyVerticalGrid`, rendering only visible items and drastically cutting composition overhead (`KitsugiMediaGridDialog.kt`).
- **Composable Recycling with `contentType`:**
  - Added explicit `contentType` tags across all LazyColumn, LazyRow, and LazyVerticalGrid items (`media_card`, `h_media_card`, `seasonal_card`, `ranking_card`, `search_row`, `char_staff_row`), enabling Compose item pooling for off-screen cards.
- **Card Allocation and Render Optimization:**
  - `KitsugiExploreMediaCard.kt`, `KitsugiRankingMediaCard.kt`, `DetailedSeasonalMediaCard.kt`, `SearchResultRow.kt`: Memoized `getDisplayTitle(titleLanguage)` and `Shape` instances with `remember`.
  - `KitsugiNsfwImage.kt`: Stabilized the `onError` lambda callback using `remember`, preventing `AsyncImage` from treating callback instantiations as model changes.

### 🛡️ 2. Scroll Jitter, Lockup & Viewport Retraction Fixes
- **Eliminated Focus Retraction on Touch Devices:**
  - `TvFocusModifier.kt`: Disabled focus on phones and tablets (`.focusProperties { canFocus = false }`), preventing clickable cards from taking focus and pulling the viewport backward via `BringIntoView`.
  - `KitsugiScrollDefaults.kt` & `AppRoot.kt`: Injected no-op `rememberTouchPassthroughSpec()` on mobile devices; TV keeps D-pad centering while touch scrolling is entirely restored to user gesture control.
- **Removed Software-Blurred Shadow on Neon Glow Cards:**
  - `KitsugiNeonGlowCard.kt`: Removed `Modifier.shadow` with colored ambient/spot tints that forced software-fallback blurs during rapid scrolling; cards now render crisp, high-performance gradient borders only.
- **Replaced Nested LazyRows with Standard `horizontalScroll`:**
  - `FullScreenMediaGridPage.kt`, `StudioDetailPage.kt`, `AddonFullScreenGridPage.kt`: Converted nested genre chips in grid/list headers to `Row + horizontalScroll(rememberScrollState())` (`GridGenreChipRow`), eliminating the parent grid remeasurement loop.
- **Hysteresis Thresholds & Fade-Only Header Transitions:**
  - `DetailPageScaffold.kt`, `CharacterDetailPage.kt`, `StaffDetailPage.kt`, `StreamScreenContent.kt`: Replaced height-animating headers with fixed-height `fadeIn`/`fadeOut` transitions; added index hysteresis and eliminated tab-jump `scrollToItem(2)`.
  - `MyListComponents.kt`: Replaced continuous pixel-based height calculation with a thresholded collapse boolean (`headerCollapsed`).
- **Removed Snap Fling Behaviors from Nested Rows:**
  - `ModernHomeRows.kt`, `ExploreComponents.kt`, `ReviewsTab.kt`, `KitsugiHorizontalMediaSection.kt`, `SearchScreen.kt`, `KitsugiUserProfileScreen.kt`, `UserProfileStatsTab.kt`, `AniListProfileContent.kt`, `MalProfileContent.kt`: Removed `rememberSnapFlingBehavior` across all horizontal carousels to prevent stealing vertical scroll gestures.
- **Non-Observable Scroll Position Persistence:**
  - `KitsugiProfileViewModel.kt` & `KitsugiUserProfileViewModel.kt`: Converted scroll positions to plain `Int` properties, reading them via `snapshotFlow` without triggering full screen recompositions.
  - `RetainedLazyListState.kt`: Refactored with `RetainedScrollPosition` and `listSaver` to prevent position jumping on restore.
  - `recordStablePageHeight`: Dampened 4px height fluctuations in pager tabs to stop infinite layout loops.
- **Technical Documentation:** `PLAN_TASK_KAYDIRMA_ZIPLAMA_FIX_2026-10-10.md`, `PLAN_TASK_SCROLL_JANK_PERFORMANS_FIX_2026-10-10.md`, `SCROLL_JANK_FIX_RAPORU_2026-10-10.md`.

---

## 🇹🇷 Türkçe (v2.4.232)

### 🎨 1. Gradyan Vurgu Rengi (Accent Gradient) Tam Uygulama Entegrasyonu
- **Vurgu Rengi Ezilmesinin Giderilmesi:** `AppRoot.kt` içinde hazır tema kimliğinin (`mint`) kullanıcının seçtiği özel rengi veya iki renkli gradyanı ezmesi engellendi; `LocalKitsugiAccent` doğrudan korunarak gradyanın tüm telefon/tablet arayüzünde aktif kalması sağlandı (`AppRoot.kt`).
- **Küresel Gradyan Bileşen Sarmalayıcıları (`KitsugiGradientMaterial.kt`):** Uygulama genelindeki tüm bileşenler için açılı lineer gradyan fırçası (`LocalKitsugiAccentBrush`) ve pürüzsüz siyah/beyaz otomatik kontrast desteği (`LocalKitsugiOnAccent`) getirildi:
  - `Modifier.background` ve `Modifier.border`: Non-composable `ModifierNodeElement` mimarisiyle vurgu rengi veya yarı saydam tonları (`AccentMuted`) otomatik olarak gradyan fırçasıyla çizilir.
  - `Text` ve `Icon`: Vurgu renkli metinler `TextStyle.copy(brush = accentBrush)` ile, ikonlar ise `CompositingStrategy.Offscreen` + `BlendMode.SrcIn` ile gradyan boyanır; vurgulu dolguların üzerinde otomatik kontrast rengini alır.
  - `Switch`, `Slider`, `RangeSlider`, `RadioButton`, `Checkbox`, `CircularProgressIndicator`, `LinearProgressIndicator`, `TabRow`, `ScrollableTabRow`, `FilterChip`, `Button`, `FloatingActionButton`: Seçili ve aktif durumlarda tema gradyanını kullanır.
- **Renk Seçici Diyaloğunda Canlı Gradyan Önizlemesi:** `KitsugiAccentColorPickerDialog` içindeki "İki Renkli Gradyan" anahtarı, açı slider'ı, yön ön ayar butonları (→, ↘ vb.), sekme alt çizgileri ve "Uygula" butonu canlı düzenlenen gradyan fırçası ve kontrastıyla dinamik olarak güncellenir (`KitsugiAccentColorPickerDialog.kt`).
- **Gezinme Çubuğu ve Oynatıcı Kontrolleri:** `AppBottomBar` ve `AppNavigationRail` seçili sekme hapı, seçili ikon ve "Kitsugi" başlığı; `PlayerAccentTheme`, `AutoPlaySwitch` ve dikey kaydırıcılar tam gradyan desteğine kavuşturuldu (`AppBottomBar.kt`, `AutoPlaySwitch.kt`, `PlayerAccentTheme.kt`).
- **Teşhis & Entegrasyon Belgeleri:** `docs/PLAN_TASK_GRADYAN_VURGU_RENGI_TAM_ENTEGRASYON_2026-10-10.md` ve `docs/RENG_SECICI_GRADYAN_KONTRAST_RAPORU_2026-10-10.md`.

---

## 🇬🇧 English (v2.4.232)

### 🎨 1. Full Accent Gradient Integration Across the Entire App
- **Preserved Custom Accent & Gradient State:** Eliminated theme-override bug in `AppRoot.kt` where preset themes (`mint`) repeatedly overwrote `LocalKitsugiAccent`, ensuring custom two-color gradients persist across all phone and tablet layouts (`AppRoot.kt`).
- **Global Gradient Material Wrappers (`KitsugiGradientMaterial.kt`):** Bound all primary UI primitives to the active angled linear gradient brush (`LocalKitsugiAccentBrush`) and automatic smooth black/white contrast (`LocalKitsugiOnAccent`):
  - `Modifier.background` & `Modifier.border`: Implemented via non-composable `ModifierNodeElement` architecture to seamlessly render gradient backgrounds and borders whenever the color matches the active accent or muted accent.
  - `Text` & `Icon`: Renders text with gradient brush and icons with offscreen compositing; automatically switches to high-contrast `onAccent` when drawn inside accent surfaces.
  - `Switch`, `Slider`, `RangeSlider`, `RadioButton`, `Checkbox`, `CircularProgressIndicator`, `LinearProgressIndicator`, `TabRow`, `ScrollableTabRow`, `FilterChip`, `Button`, `FloatingActionButton`: Now fully support gradient active tracks, thumbs, chips, and indicators.
- **Live In-Dialog Gradient Preview:** The "Two-Color Gradient" switch, angle slider, direction preset buttons, tab indicators, and the "Apply" button inside `KitsugiAccentColorPickerDialog` now react in real time using the live editing gradient brush and contrast colors (`KitsugiAccentColorPickerDialog.kt`).
- **Navigation Bar & Video Player Polish:** Styled `AppBottomBar` and `AppNavigationRail` selected pill, icons, labels, and the Kitsugi title with the accent gradient; updated `PlayerAccentTheme`, `AutoPlaySwitch`, and player controls (`AppBottomBar.kt`, `AutoPlaySwitch.kt`, `PlayerAccentTheme.kt`).
- **Audit & Architecture Docs:** `docs/PLAN_TASK_GRADYAN_VURGU_RENGI_TAM_ENTEGRASYON_2026-10-10.md` and `docs/RENG_SECICI_GRADYAN_KONTRAST_RAPORU_2026-10-10.md`.

---

## 🇹🇷 Türkçe (v2.4.231)

### 🌸 1. Bangumi Kişi ve Karakter Detayları — Uygulama Dilinde Başlık ve Rol Yerelleştirmesi
- **Üç Kademeli Latin Başlık Çözümleyicisi (`BangumiSubjectTitleResolver`):** Bangumi kişi ve karakter listelerinin yalnızca Japonca/Çince isim döndürmesi nedeniyle "Yapımlar" ve "Karakterler" sekmelerinde başlıkların CJK kalması sorunu giderildi:
  1. `BangumiTitleCache` kalıcı önbelleği (daha önce çözülen başlıklar için sıfır ağ isteği).
  2. Bangumi v0 subject infobox (`英文名` / `罗马字` / `别名`).
  3. **AniList Aliased Toplu Arama:** Tek GraphQL isteğinde çoklu aliased sorgu ile `native`/`synonyms` alanlarında birebir normalize eşleşme doğrulaması; yanlış eşleşme reddedilir ve sonuçlar kalıcı önbelleğe işlenir (`BangumiSubjectTitleResolver.kt`).
- **Karakter ve Kadro Zenginleştirme Hatları:** Karakter detayı için `enrichCharacterDetailNames()` eklendi; `enrichStaffDetailNames()` ortak çözümleyiciye bağlandı. Senkron geçiş `cacheOnly` ile anında çizim sağlarken, ViewModel arka planda kalan başlıkları çözerek ekranı akıcı şekilde günceller (`KitsugiBangumiDetailClient.kt`, `CharacterDetailViewModel.kt`, `StaffDetailViewModel.kt`).
- **Arayüz Diline Duyarlı Rol ve Tür Sözlüğü (`BangumiRoleDictionary`):** Çince ve Japonca rol etiketleri (`主角`, `配角`, `客串`, `主題歌演出`, `原作`, `监督` vb.) uygulamanın diline (TR / EN) çevrilir. Veri katmanındaki sabit Türkçe etiketler kaldırılarak İngilizce arayüzde İngilizce ("Main Character", "Staff Member", "Male"), Türkçe arayüzde Türkçe gösterim sağlandı (`BangumiRoleDictionary.kt`, `KitsugiTranslations.kt`, `KitsugiBangumiCreditsClient.kt`).
- **Birim Testleri & Kararlılık:** `BangumiSubjectTitleResolverTest` ve `KitsugiTranslationsTest` testleri ile stableId/rawId dönüştürücüleri ve rol sözlüğü tam kapsamlı test edildi.

---

## 🇬🇧 English (v2.4.231)

### 🌸 1. Bangumi Staff & Character Details — Localized Titles and Roles
- **Three-Tier Latin Title Resolver (`BangumiSubjectTitleResolver`):** Fixed CJK (Japanese/Chinese) titles in the "Works" and "Characters" tabs of Bangumi person and character details:
  1. Persistent `BangumiTitleCache` (zero network calls for previously resolved items).
  2. Bangumi v0 subject infobox (`英文名` / `罗马字` / `别名`).
  3. **AniList Aliased Batch Search:** Executes batched GraphQL searches in a single request with strict normalized matching on `native` titles and synonyms; incorrect candidates are safely rejected and valid matches are stored in persistent cache (`BangumiSubjectTitleResolver.kt`).
- **Character & Staff Enrichment Pipelines:** Added `enrichCharacterDetailNames()` for character details and routed `enrichStaffDetailNames()` through the shared title resolver. Synchronous paths use `cacheOnly` for instant rendering, while ViewModels resolve background queues asynchronously (`KitsugiBangumiDetailClient.kt`, `CharacterDetailViewModel.kt`, `StaffDetailViewModel.kt`).
- **Locale-Aware Role & Type Dictionary (`BangumiRoleDictionary`):** Chinese/Japanese role and credit labels (`主角`, `配角`, `客串`, `主題歌演出`, `原作`, `监督`, etc.) are now translated according to the active app language (TR / EN). Hardcoded Turkish fallback strings in data models were eliminated in favor of clean English and Turkish labels (`BangumiRoleDictionary.kt`, `KitsugiTranslations.kt`, `KitsugiBangumiCreditsClient.kt`).
- **Unit Testing & Reliability:** Validated with comprehensive tests in `BangumiSubjectTitleResolverTest` and `KitsugiTranslationsTest` verifying ID mapping, alias alignment, and role localization.

---

## 🇹🇷 Türkçe (v2.4.230)

### ⚡ 1. Keşfet Kaynak Yükleme ve Arayüz Performansı İyileştirmeleri
- **AniList Çoklu Raf Toplu GraphQL Sorgusu (4× Hızlanma):** 14 rafın tek tek 700ms hız sınırlı kuyrukta ~11 saniye beklemesi yerine, 3 toplu GraphQL sorgusuyla paralel çekilmesi sağlandı (~2-3 saniyeye indi) (`AniListSearchClient.kt`).
- **MyAnimeList (MAL) Zaman Aşımı ve Raf Güvencesi:** 13 istek ve 350ms kuyruk gecikmesinde 5 saniyelik timeout kuyruktaki süreyi de tükettiğinden son rafların boş kalması engellendi; zaman aşımı 20 saniyeye çıkarılarak tüm rafların eksiksiz dolması sağlandı (`JikanApiClient.kt`).
- **Bangumi İstek Sınırı ve Önbellek Önceliği:** 14 raf için ~56 eşzamanlı istek atılarak bgm.tv tarafından 429/retry döngüsüne ve dakikalarca takılmaya yol açan yapı düzenlendi; paylaşılan 3 eşzamanlılık sınırı, kayıt tekilleştirmesi ve önbellek öncelikli mimari uygulandı (`KitsugiBangumiClient.kt`).
- **Asenkron Vitrin Arka Planı:** Kitsu, Shikimori, MAL ve Bangumi yüklenirken vitrin arka planı için atılan 5 TMDB aramasının sayfayı bloke etmesi engellendi; arka planda asenkron çözülüyor (`ExploreViewModel.kt`).
- **Takvim Sorgusu Tekilleştirmesi:** "Tümü" modunda aynı "Yakında Yayında" takvim sorgusunun 7 kez atılması yerine 90 saniyelik önbellek ve tekilleştirme sağlandı (`KitsugiAiringCalendarClient.kt`).
- **Kompozisyon ve Çizim Hızlandırması:** `allSourceSections()` 7 kaynak filtre ve string hesaplamaları her ekranda baştan hesaplanmak yerine `remember` ile önbelleğe alındı; geri sayım kartlarının saniyede bir tetiklenen 15 zamanlayıcısı 15 saniyelik aralığa optimize edildi (`ExploreComponents.kt`, `AllSourcesExploreContent.kt`).
- **Teşhis Raporu:** `KESFET_KAYNAK_YUKLEME_PERFORMANS_RAPORU_2026-10-10.md`.

---

## 🇬🇧 English (v2.4.230)

### ⚡ 1. Explore Source Loading & Rendering Performance Optimizations
- **AniList Batch GraphQL Queries (~4× Faster):** Replaced 14 sequential queries (which took ~11s over the 700ms rate-limited queue) with 3 batched GraphQL queries, reducing loading time to ~2-3s (`AniListSearchClient.kt`).
- **MyAnimeList (MAL) Shelf Reliability & Timeout Extension:** Increased request timeout from 5s to 20s so queued requests on the 350ms rate limiter don't prematurely time out, preventing missing or empty shelves (`JikanApiClient.kt`).
- **Bangumi Concurrency Throttling & Cache-First Strategy:** Eliminated severe 429/retry loops on bgm.tv caused by up to 56 concurrent detail requests across 14 shelves; added shared 3-concurrency limiting, request deduplication, and cache-first fetching (`KitsugiBangumiClient.kt`).
- **Non-blocking Hero Backdrop:** Decoupled 5 blocking TMDB searches from initial shelf loading in Kitsu, Shikimori, MAL, and Bangumi; hero images are now resolved asynchronously in the background (`ExploreViewModel.kt`).
- **Airing Calendar Deduplication:** Consolidated 7 redundant airing calendar queries in "All" mode into a single query with 90s in-memory caching (`KitsugiAiringCalendarClient.kt`).
- **UI Composition & Frame Drops Elimination:** Cached `allSourceSections()` via `remember` across 7 sources to stop expensive re-computations on every recomposition pass; lowered countdown chip timer ticks from 1s to 15s to eliminate 15 separate per-second render timers (`ExploreComponents.kt`, `AllSourcesExploreContent.kt`).
- **Diagnostic Report:** `KESFET_KAYNAK_YUKLEME_PERFORMANS_RAPORU_2026-10-10.md`.

---

## 🇹🇷 Türkçe (v2.4.229)

### 📖 1. Manga KeiSource & OkHttp 5 Uyumluluk Onarımı (72/77 TR Eklentisi Kurtarıldı)
- **3 Zorunlu KeiSource Interceptor'ı Eklendi:** Keiyoushi `KeiSource` tabanlı eklentilerin host istemcisinden sınıf adına göre şart koştuğu `UncaughtExceptionInterceptor`, `UserAgentInterceptor` ve `CloudflareInterceptor` sınıfları `NetworkHelper` varsayılan zincirine bağlandı. İsimsiz lambda veya sınıf uyuşmazlığı nedeniyle oluşan `IllegalStateException` ve arama/bölüm listesinin boş kalması sorunu giderildi (`NetworkHelper.kt`, `UncaughtExceptionInterceptor.kt`, `UserAgentInterceptor.kt`).
- **OkHttp 5.4 & Brotli/Zstd Uyumluluğu:** Keiyoushi lib 1.6 eklentilerinin talep ettiği `CompressionInterceptor(Brotli, Gzip, Zstd)` ve `zstd-kmp-okio` çağrıları için eksik Brotli / Zstd köprüleri bağlandı; `okhttp-android:5.3.2` ile ikili çakışmalar giderildi.
- **ProGuard Keep Kuralları:** R8 derlemesi sırasında interceptor sınıf adlarının ve OkHttp 5 köprülerinin korunması sağlandı (`proguard-rules.pro`).
- **Bölüm Listesi Hata Şeffaflığı:** `MihonSourceWrapper.fetchChapterList` içerisindeki hatalar artık sessizce yutulup boş liste döndürülmüyor; hata açıkça yüzeye çıkarılarak hatalı kaynak istatistikleri ve sahte "bölüm yok" durumları engellendi (`MihonSourceWrapper.kt`).
- **Kaynak Sağlık Sorgusu Çeşitlendirmesi:** `SourceHealthService` içinde sabit tek sorgu ("one piece") yerine dile göre 3 aday arama sorgusu tanımlandı (`SourceHealthService.kt`).
- **Uçtan Uca Doğrulama & Envanter Raporu:** 1431 eklenti, 2419 kaynak ve 93 Türkçe kaynağın incelendiği teknik rapor ve otomatik tarama script'i eklendi (`docs/audits/MANGA_KAYNAK_UCTAN_UCA_DOGRULAMA_2026-10-10.md`, `scripts/audit_manga_sources.py`).

### 🎬 2. Video Eklenti Kurtarma, Güvenli Arama & Doğru CF Tespiti
- **Otomatik Eklenti Devre Dışı Bırakma Kaldırıldı:** Tanı aracının ağ veya sağlayıcı hatası alan eklentileri veritabanında otomatik kapatarak akış aramasının tamamen boş dönmesine yol açan mekanizma kaldırıldı (`CsPluginDiagnosticRunner.kt`).
- **Tüm Eklentilerin Kapalı Olması Durumu:** Kurulu eklentilerin tamamı kapalıysa akış ekranında net bilgilendirme gösteriliyor; eklenti ayarlarına tüm devre dışı eklentileri tek dokunuşla geri açma seçeneği eklendi (`CloudstreamExtensionTab.kt`, `KitsugiStreamScreen.kt`).
- **Kesintisiz Arama (Failover Search):** Doğrudan seçilen bir eklentinin URL'sinden kaynak çıkmazsa arama orada kesilmiyor; aynı başlık, sezon ve bölüm bilgileriyle etkin tüm eklentilerde aramaya devam ediliyor (`StreamViewModel.kt`).
- **Daraltılmış ve Doğru Cloudflare Tespiti:** Arama sonucu boş olan her eklenti artık CF/WAF engeli sayılmıyor. Yalnızca doğrulanmış bot/WAF koruma imzası saptandığında "Doğrula" butonu gösteriliyor; genel 403/503, DNS veya zaman aşımı hataları ayrı sınıflandırılıyor (`CsProtectionClassifier.kt`).
- **Sunucu Cooldown Süreleri Korunuyor:** Tanı aracı artık yeniden deneme bekleme sürelerini zorla devre dışı bırakmıyor; sunuculara aşırı istek yüklenmesi önlendi.
- **Güvenli Sezon Seçimi:** Sezon aramasında başlıkla eşleşen sonuç bulunamazsa listedeki ilk ilgisiz adayın yüklenmesi engellendi (`CsStreamRunner.kt`).

### 🔐 3. Kullanıcı Hesap Güvenliği ve Veri Erişim Yetkileri
- **Kimliği Doğrulanmış Kullanıcı Veri Erişimi:** Supabase `user_data` tablosuna kimliği doğrulanmış kullanıcılar için CRUD yetkileri ve RLS izinleri tanımlandı (`20261010_user_data_authenticated_grants.sql`).
- **Yedekleme ve Vault CAS Güvenliği:** Hesap senkronizasyonunda Compare-and-Swap güvenlik kontrolleri ve kullanıcı dostu hata mesajı biçimlendiricisi eklendi (`AccountErrorFormatter.kt`, `KitsugiAccountContent.kt`).

---

## 🇬🇧 English (v2.4.229)

### 📖 1. Manga KeiSource & OkHttp 5 Compatibility Fixes (Unblocked 72/77 TR Extensions)
- **Added 3 Required KeiSource Interceptors:** Keiyoushi `KeiSource` extensions enforce strict class-name checks on the host client for `UncaughtExceptionInterceptor`, `UserAgentInterceptor`, and `CloudflareInterceptor`. All three are now properly provided by `NetworkHelper`, eliminating `IllegalStateException` crashes that caused silent empty search and chapter results (`NetworkHelper.kt`, `UncaughtExceptionInterceptor.kt`, `UserAgentInterceptor.kt`).
- **OkHttp 5.4 & Brotli/Zstd Compatibility:** Provided bridge stubs for `CompressionInterceptor(Brotli, Gzip, Zstd)` and `zstd-kmp-okio` APIs expected by extensionLib 1.6; resolved duplicate binary symbols against `okhttp-android:5.3.2`.
- **ProGuard Keep Rules:** Added ProGuard keep entries ensuring interceptor class names and OkHttp 5 bridge classes are preserved during R8 optimization (`proguard-rules.pro`).
- **Chapter Fetch Error Transparency:** `MihonSourceWrapper.fetchChapterList` no longer swallows exceptions into an empty list, ensuring genuine error surfacing and accurate source health metrics (`MihonSourceWrapper.kt`).
- **Localized Health Check Queries:** Enhanced `SourceHealthService` with language-specific candidate query pools instead of a hardcoded single query (`SourceHealthService.kt`).
- **End-to-End Audit & Inventory Report:** Added comprehensive analysis and audit tooling covering 1,431 extensions, 2,419 sources, and 93 Turkish sources (`docs/audits/MANGA_KAYNAK_UCTAN_UCA_DOGRULAMA_2026-10-10.md`, `scripts/audit_manga_sources.py`).

### 🎬 2. Video Plugin Source Recovery & Accurate Protection Classification
- **Removed Auto-Disabling of Plugins:** Diagnostic test runs no longer auto-disable plugins upon network or provider errors, which previously caused all stream sources to go silent (`CsPluginDiagnosticRunner.kt`).
- **Disabled Plugins Safeguard:** Stream screen clearly notifies when all installed plugins are disabled; added a one-tap action in extension settings to re-enable all installed providers (`CloudstreamExtensionTab.kt`, `KitsugiStreamScreen.kt`).
- **Failover Search Across Active Plugins:** If a directly selected plugin URL returns no streams, search continues across all active plugins for the matching title, season, and episode (`StreamViewModel.kt`).
- **Narrowed Cloudflare Classification:** Empty search results are no longer misclassified as Cloudflare/WAF blocks. Verification triggers only on verified bot/WAF challenge signatures; generic 403/503, DNS, and timeouts are reported accurately (`CsProtectionClassifier.kt`).
- **Respected Server Cooldowns:** Diagnostic tool no longer overrides cooldown timers (`ignoreCooldowns = false`), preventing server-side bans.
- **Safe Season Matching:** Prevents loading arbitrary non-matching candidates when season title matching yields no hits (`CsStreamRunner.kt`).

### 🔐 3. Authenticated User Data Access & Account Vault Security
- **User Data Table Permissions:** Added explicit CRUD grants and RLS policies for authenticated users on the `user_data` table (`20261010_user_data_authenticated_grants.sql`).
- **Account Backup Vault CAS:** Integrated compare-and-swap checks and user-friendly error formatting for account sync and backup (`AccountErrorFormatter.kt`, `KitsugiAccountContent.kt`).

---

## 🇹🇷 Türkçe (v2.4.228)

### ⚡ 1. Keşfet & Arama Sayfaları Performans İyileştirmeleri
- **Scroll Geri Besleme Döngüsü Çözüldü (Keşfet & Listem):** Keşfet ve Listem ekranlarında kaydırma yapılırken her pikselde `AppViewModel` üzerindeki durum değişkenlerine (`mutableIntStateOf`) yazılması tüm sekmenin baştan aşağı recomposition'a girmesine ve 100+ öğelik listenin her karede yeniden kurulmasına yol açıyordu. Pozisyonlar düz değişkenlere (`var exploreScrollIndex: Int`) dönüştürüldü; sekmeden ayrılıp geri dönüldüğünde scroll konumu korunurken kaydırma esnasındaki kasma ve takılmalar tamamen ortadan kaldırıldı (`AppViewModel.kt`, `ExploreScreen.kt`).
- **Sonsuz Shimmer Animasyonu Optimizasyonu:** `KitsugiShimmerProvider` bileşenine `active` kontrolü eklendi. Sayfada veri yüklemesi tamamlandığında ve içerik geldiğinde sonsuz animasyon saati (`InfiniteTransition`) tamamen kapatılıyor; statik fırça sağlanarak boşta dururken bile arka planda çalışan ve CPU/pil tüketen gereksiz recomposition döngüleri durduruldu (`KitsugiShimmer.kt`, `ExploreScreen.kt`, `TvAllSourcesHomeContent.kt`).
- **Arama Sayfası Shimmer & Filtreleme Hızlandırması:**
  - "Tümü" aramasında 7 platform aynı anda sorgulanırken her platform rafının kendi sonsuz shimmer animasyonunu kurması yerine sayfa geneli tek paylaşımlı `KitsugiShimmerProvider(active = anySearchLoading)` bağlandı.
  - `filteredResults` (yetişkin içerik ve listemdekiler süzgeci) LazyColumn her çizildiğinde yüzlerce sonucu baştan filtrelemek yerine `remember(...)` ile önbelleğe alındı (`SearchScreen.kt`).
- **Teşhis Raporu:** Keşfet performans analizi ve optimizasyon detayları belgelendi (`KESFET_PERFORMANS_FIX_RAPORU_2026-10-10.md`).

### 📖 2. Manga Hattı Düzeltmeleri & Kompakt Arayüz İyileştirmeleri
- **Kompakt "Oku" Butonu:** Detay sayfasındaki "Oku" butonu, "İzle" butonu gibi kompakt yapıya kavuşturuldu; ekranın tüm satırını kaplaması engellendi (`ApiDetailLeftPanel.kt`, `KitsugiDetailInfoSection.kt`).
- **Siyah Okuyucu Ekranı Giderildi:** Görsel hazır olduğunda ekranın bunu anında gözlemleyebilmesi sağlandı; durum güncelleme (state observation) gecikmelerinden kaynaklanan siyah ekran sorunu çözüldü (`MangaReaderViewModel.kt`, `MangaBrowseViewModel.kt`, `MangaBrowseScreen.kt`).
- **Mihon Görsel İndirme Hattı:** Mihon görselleri artık eklentinin kendi resim isteği (image request), HTTP istemcisi ve oturum çerezleriyle doğrudan indiriliyor (`MihonSourceWrapper.kt`).
- **Kotatsu Önizleme ve Hata İyileştirmeleri:** Kotatsu parser'larında önizleme görsellerinin gerçek sayfa sanılması engellendi, kaybolan bölüm metadataları, bölüm sıralaması ve arka planda gizlenen hatalar giderildi (`KotatsuExtensionAdapter.kt`, `MangaModels.kt`).
- **Doğrudan Kaynak Eşleşmesi:** Manga arama sonuçları artık doğrudan kendi kaynak kimliğiyle açılıyor; aynı isimdeki eklentiler arasında hatalı tahmin ve yanlış kaynak ataması yapılmıyor (`MangaSourceRepository.kt`).
- **HTML Temizliği ve Önbellek Koruması:** Bozuk veya HTML hata yanıtlarının resim olarak önbelleğe alınması engellendi; manga açıklamalarındaki ham HTML etiketleri (`<p>`, `<b>`, `&nbsp;` vb.) arayüzde temizlendi (`MangaCache.kt`, `MangaPageLoaderV2.kt`).
- **121 Türkçe Parser Denetimi & Envanteri:** 121 Türkçe parser statik envantere çıkarıldı ve durum raporu belgelendi (`docs/MANGA_PIPELINE_AUDIT_2026-10-10.md`, `docs/MANGA_TR_PARSER_INVENTORY.md`, `scripts/audit_manga_parsers.py`).
- **Test Kapsamı:** Manga sayfa durumu ve önbellek mekanizmaları için birim testleri entegre edildi (`MangaPageStateTest.kt`, `MangaCacheTest.kt`).

### 🌐 3. Kapsamlı Dil Altyapısı (i18n) Genişletmesi & 3.745 Çift Dilli Kaynak (`strings.xml`, `values-en/strings.xml`, `values-en/arrays.xml`)
- **3.745 Anahtarlık %100 TR / EN Simetrisi:** Kaynak sayısı 2.195'ten 3.745 anahtara çıkarıldı; Türkçe ve İngilizce dil kaynakları arasında birebir anahtar, sıra ve `%1$s` biçim yer tutucusu (placeholder) eşitliği sağlandı.
- **AAPT XML Kaçış & Standardizasyon Onarımları:**
  - 665 adet geçersiz `xml:space="preserve"` attribute'u temizlendi (Android AAPT derleyicisinin `CantBindXML` hatası çözüldü).
  - Metin içi kaçışsız apostroflar (`zoom'u`, `provider's` vb.) Android standartlarına uygun `\'` haline getirildi.
  - Yüzde karakterleri içeren metinler için `formatted="false"` etiketleri düzenlendi.
  - Bangumi etiket senkronizasyon sözlüğü (`sync_bangumi_tag_strings.py`) bozulmadan korundu.

### 📱 4. 14 Yeni Ekran & Diyalog Kaynaklara Bağlandı
- **Sistem ve İndirme Ayarları (`KitsugiSystemSettingsDialog.kt`):**
  - İndirme hız limitleri ("Sınırsız", "50 KB/s", "10 MB/s" vb.), eşzamanlı indirme sayıları.
  - Okuma sonrası otomatik silme ("Devre Dışı", "Son okunan bölüm", "Son 1-10 bölüm").
  - Okurken/İzlerken otomatik ön indirme ("Sonraki 1-10 bölüm / video").
  - Kategori filtreleme & hariç tutma diyalogları ("CURRENT", "PLANNING", "COMPLETED", "ON_HOLD", "DROPPED").
  - DNS sağlayıcıları (Sistem Varsayılanı, Google, Cloudflare, AdGuard, DNS.WATCH, Quad9, DNS.SB, Canadian Shield açıklamaları).
  - Harici indirici tercihleri (Dahili, 1DM, ADM algılama ve durum metinleri).
- **Gelişmiş Arama Filtreleme (`SourceEngineFilterSheet.kt`):** Arama motoru, kaynak filtreleri ve sıralama seçeneklerinde 49 metin yerelleştirildi.
- **Akış ve Video Sayfası (`StreamScreenContent.kt`):** Akış kaynakları, oynatıcı seçimi, hata durumları ve çözünürlük etiketlerinde 24 metin dinamik kaynaklara bağlandı.
- **Eklenti ve Geliştirici Günlükleri (`CsPluginDiagnosticScreen.kt`, `DeveloperLogsDialog.kt`):** Eklenti tanılama raporları, test adımları, log filtreleme ve panoya kopyalama butonları yerelleştirildi.
- **Geri Bildirim (`FeedbackDialog.kt`):** E-posta gönderimi, şablon metinleri ve hata uyarıları.
- **Android TV Ekranları:**
  - TV Aktivite Detayı (`TvActivityDetailDialog.kt`), İnceleme Detayı (`TvReviewDetailDialog.kt`), Manga Eklentileri (`TvMangaExtensionScreen.kt`), QR Giriş Ekranı (`TvQrLoginScreen.kt`), TV Akış Ekranı (`TvStreamScreen.kt`), TV Eklenti Yöneticisi (`TvAddonsScreen.kt`), TV Yardımcı Eşleşme Onayı (`TvCompanionApprovalDialog.kt`) ve QR Kod Ekranı (`TvCompanionQrScreen.kt`).

### 🛠️ 5. Yeni Yerelleştirme Denetim Araçları (`scripts/`)
- **`scripts/audit_hardcoded_turkish.py`:** Kod tabanında kalan Türkçe karakterli hardcoded literal metinleri tarayıp dosya bazında raporlayan denetim aracı eklendi.
- **`scripts/check_localization_resource_parity.py`:** `values` ve `values-en` arasındaki string ve string-array anahtarlarını, format yer tutucularını (`%1$s`) otomatik doğrulayan CI aracı entegre edildi.

---

## 🇬🇧 English (v2.4.228)

### ⚡ 1. Explore & Search Performance Optimizations
- **Resolved Scroll Recomposition Loop (Explore & My List):** Eliminated the feedback loop where updating `mutableIntStateOf` during scrolling triggered full recompositions of the entire tab and rebuilt 100+ items per frame. Replaced scroll positions with plain Kotlin properties (`var exploreScrollIndex: Int`), preserving scroll state upon navigation while delivering stutter-free scrolling (`AppViewModel.kt`, `ExploreScreen.kt`).
- **Infinite Shimmer Animation Optimization:** Added an `active` control parameter to `KitsugiShimmerProvider`. Once content is loaded and placeholders are replaced, the infinite transition clock is completely disabled, substituting a static gradient brush and eliminating wasteful CPU/battery drain (`KitsugiShimmer.kt`, `ExploreScreen.kt`, `TvAllSourcesHomeContent.kt`).
- **Search Screen Shimmer & Filter Caching:**
  - Consolidated multi-platform shimmer instances: wrapped search results with a single shared `KitsugiShimmerProvider(active = anySearchLoading)` across all 7 platform shelves instead of 8 separate infinite transitions.
  - Cached `filteredResults` (adult filter and "in my list" filter) using `remember(...)` to avoid recalculating hundreds of items on every layout pass (`SearchScreen.kt`).
- **Diagnostic Report:** Documented comprehensive analysis and profiling findings (`KESFET_PERFORMANS_FIX_RAPORU_2026-10-10.md`).

### 📖 2. Manga Pipeline Fixes & Compact UI Enhancements
- **Compact "Read" Button:** The "Read" action button on the anime/manga detail screen is now compact like the "Watch" button instead of spanning the entire line (`ApiDetailLeftPanel.kt`, `KitsugiDetailInfoSection.kt`).
- **Resolved Black Reader Screen:** Fixed state update / observation race condition where page states failed to trigger UI rendering upon image readiness (`MangaReaderViewModel.kt`, `MangaBrowseViewModel.kt`, `MangaBrowseScreen.kt`).
- **Mihon Image Pipeline:** Manga page images from Mihon extensions are now fetched using the extension's dedicated image requests, HTTP client, and session headers (`MihonSourceWrapper.kt`).
- **Kotatsu Improvements:** Prevented preview thumbnails from being mistaken as chapter pages, restored missing metadata, fixed chapter ordering, and surfaced previously suppressed error details (`KotatsuExtensionAdapter.kt`, `MangaModels.kt`).
- **Direct Source Mapping:** Manga search results now open strictly with their originating source provider, preventing ambiguous name-based guesses (`MangaSourceRepository.kt`).
- **HTML Stripping & Cache Protection:** Blocked corrupted or HTML error responses from being cached as images; stripped raw HTML tags (`<p>`, `<b>`, `&nbsp;` vb.) from manga synopsis text (`MangaCache.kt`, `MangaPageLoaderV2.kt`).
- **121 Turkish Parser Inventory:** Conducted a comprehensive audit and static inventory of all 121 Turkish manga parsers (`docs/MANGA_PIPELINE_AUDIT_2026-10-10.md`, `docs/MANGA_TR_PARSER_INVENTORY.md`, `scripts/audit_manga_parsers.py`).
- **Unit & Regression Testing:** Added tests for manga page states and cache integrity (`MangaPageStateTest.kt`, `MangaCacheTest.kt`).

### 🌐 3. Comprehensive i18n Expansion & 3,745 Bilingual Resources (`strings.xml`, `values-en/strings.xml`, `values-en/arrays.xml`)
- **Full 100% TR / EN Symmetry with 3,745 Keys:** Resource collection expanded from 2,195 to 3,745 keys, maintaining perfect 1-to-1 key parity and matching `%1$s` positional format tokens across English and Turkish.
- **AAPT XML Escape & Standardization Fixes:**
  - Stripped 665 invalid `xml:space="preserve"` attributes to resolve AAPT compiler XMLStreamException (`CantBindXML`).
  - Escaped raw single quotes (`\'`) for proper Android string compilation (`zoom\'u`, `provider\'s`).
  - Applied `formatted="false"` attributes to strings containing unescaped percentage symbols.
  - Fully maintained Bangumi dictionary generator parity (`sync_bangumi_tag_strings.py --check`).

### 📱 4. Migration of 14 UI Screens & Dialogs to Localized Resources
- **System & Download Settings (`KitsugiSystemSettingsDialog.kt`):**
  - Download speed limits ("Unlimited", "50 KB/s", "10 MB/s", etc.) and concurrent download counts.
  - Auto-removal options ("Disabled", "Last read chapter", "Last 1-10 chapters").
  - Pre-download while watching/reading options.
  - Category inclusion/exclusion dialogs ("Current", "Planning", "Completed", "On Hold", "Dropped").
  - DoH DNS providers and descriptions (System Default, Google, Cloudflare, AdGuard, DNS.WATCH, Quad9, DNS.SB, Canadian Shield).
  - External downloader preferences (Internal, 1DM, ADM detection).
- **Search Engine Filters (`SourceEngineFilterSheet.kt`):** 49 string resources wired for source provider and filter settings.
- **Streaming Screen (`StreamScreenContent.kt`):** 24 string resources wired for stream lists, server states, and resolution tags.
- **Diagnostics & Logs (`CsPluginDiagnosticScreen.kt`, `DeveloperLogsDialog.kt`):** Diagnostic test suites, log filtering, and clipboard actions.
- **Feedback Dialog (`FeedbackDialog.kt`):** Feedback form, email triggers, and templates.
- **Android TV Screens:**
  - TV Activity Detail (`TvActivityDetailDialog.kt`), Review Detail (`TvReviewDetailDialog.kt`), Manga Extensions (`TvMangaExtensionScreen.kt`), TV QR Login (`TvQrLoginScreen.kt`), TV Stream (`TvStreamScreen.kt`), TV Addons (`TvAddonsScreen.kt`), Companion Approval (`TvCompanionApprovalDialog.kt`), and Companion QR (`TvCompanionQrScreen.kt`).

### 🛠️ 5. Localization Verification & Audit Tools (`scripts/`)
- **`scripts/audit_hardcoded_turkish.py`:** Automated scanner identifying remaining hardcoded strings across Kotlin files.
- **`scripts/check_localization_resource_parity.py`:** Automated validator verifying structural, key, and format token parity between locale resource files.

---

## 🇹🇷 Türkçe (v2.4.227)

### 🎯 1. Kaynak Bazlı Arama & Bölüm Mekaniği: Film ve Dizi için Ayrı Boru Hatları (`CsStreamRunner.kt`, `CsTitleMatcher.kt`)
- **Temel İlke:** Tüm kaynaklarda içerik araması artık YALNIZCA çıplak eser adıyla yapılır — sezon/bölüm bilgisi arama sorgusuna ASLA karışmaz. Hiçbir site (Türkçe/yabancı) "X 1. Sezon 1. Bölüm" sorgusunu çözemez; doğru akış oturtuldu: başlık ara → içerik sayfasına gir → (dizi ise) sezon+bölüm sayfasına in → video verilerini çek.
- **Film Mekaniği (ayrı boru hattı):** Filmler sezon mantığıyla depolanmaz; eşleşen içerik sayfası doğrudan video sayfası olarak kullanılır (dataUrl/url → loadLinks), bölüm eşleştirme yapılmaz.
- **Dizi/Anime Mekaniği (ayrı boru hattı):** Yüklenen sayfanın bölüm listesinden hedef S+E seçilir; eklenti bölüm listesi doldurmuyorsa (sayfa-tipli plugin) sayfa URL'si bölüm kapsayıcısı olarak kullanılır.
- **Sezon Sayfası Gezinmesi:** Bölüm listesi dolu ama hedef sezon sayfada yoksa site sezonu ayrı tutuyordur; eklentinin KENDİ search+load akışıyla sezon sayfasına inilir — kaynak-bağımsız, tüm eklentilerde ortak genel adım.
- **Eski Hata Düzeltildi:** Bölüm listesi dolu olup eşleşme yoksa loadLinks'e DİZİ sayfası URL'si veriliyordu — 25 sn'lik link çıkarma bütçesi çöpe gidiyor, kart "akış bulunamadı" diyordu. Artık gerçek sebep tracker'a yazılır ve sezon gezinmesi denenir.
- **Sezon Girdisi Araması (güvenlik ağı):** Sezon ekli sorgular ("X 2. Sezon") asla birincil arama değildir; yalnızca çıplak başlık araması sıfır sonuç döndürürse (sezonları ayrı indeksleyen siteler için) denenir.
- **Sayfa Tipine Göre Boru Hattı:** Yanlış sınıflandırma durumunda (film bayrağıyla gelip kaynak "tek bölüm" dizi girdisi döndürürse — Türkçe sitelerde çok yaygın) boru hattı yüklenen sayfanın gerçek tipine göre seçilir; mekanik bayrağa değil kaynağa bakar.

### 🛡️ 2. Cloudflare WebView Bellek Sızıntısı ve Çökme Riski (`CloudflareInterceptor.kt`, `KitsugiApplication.kt`, `CsCfWarmupManager.kt`)
- **Başarılı Challenge Sonrası WebView Sızıntısı Giderildi:** `cf_clearance` bulunduğunda bekleme sayacı tamamlanıyor, fakat eski timeout kolu yalnızca sayaç hâlâ açıkken `destroy()` çağırıyordu. Başarılı çözüm yolunda WebView açık kalıyor; sonraki challenge'larda native Chromium/RenderThread kaynaklarının birikmesine yol açabiliyordu. Başarı, zaman aşımı ve iptal artık ana thread'de tek seferlik temizliğe gidiyor.
- **Başlangıçta Toplu WebView Açılması Kaldırıldı:** Açılışta 14 adede kadar gizli WebView ile Cloudflare warmup yapılmıyor. Challenge çözümü yalnızca ihtiyaç duyulan host için tembel (lazy) başlatılıyor.
- **Güvenli Teardown:** WebView yok edilmeden hemen önce `about:blank` yüklenmesi kaldırıldı; view hiyerarşisinden sökme, yükü durdurma, client referanslarını kesme ve tek `destroy()` sırası kullanılıyor.

### 🌐 3. Kaynak Bazlı Liste Eşleştirme & Yetişkin İçerik Bulanıklığı (`KitsugiModels.kt`, `AppRoot.kt`, `AppRootDetailPages.kt`)
- **Kaynak Bazlı Ayrım (`matchesInSource`, `firstMatchingInSource`):** Liste aidiyeti ve detay sayfası kontrolleri artık platform kaynağına duyarlıdır. Bangumi (`bgm`, `bgm.tv`) kayıtları Bangumi düzenleyicisine yönlendirilir; Simkl veya AniList eşleşmeleri Bangumi öğelerinin listede görünmesine sebep olmaz.
- **Profil Favorilerinde Başa Dön Butonu:** Profil favorilerindeki "başa dön" butonu, kaydırma öncesinde ve sonrasında paylaşılan alt çubuk görünürlüğünü sıfırlar.
- **Yetişkin İçerik Bulanıklığı Taşıması:** AniList profil favorilerindeki yetişkin medya durumu detay navigasyonuna taşınır; sinematik yükleme posterleri ilk kareden itibaren bulanıklık ayarına uyar.

### 🏷️ 4. Ortak Küresel Etiket ve Tür Çevirileri (`BangumiTagDictionary.kt`, `SearchTranslation.kt`, `GenreLabelFormatter.kt`, `KitsugiTranslations.kt`)
- **Tüm Kaynaklarda Birlik:** AniList, MAL, Kitsu, Shikimori ve Bangumi için etiket ve tür çevirileri tek ortak BangumiTagDictionary ve SearchTranslation tablosu üzerinden hizalandı.
- **Çevrilen ve İyileştirilen Etiketler:** Heteroseksüel, Erkek/Kadın Baş Karakter, Ters Harem, Ağırlıklı Kadro etiketleri, Klon/Klonlar, Uzaylılar, Poliamori, Kuudere, Çıplaklık, Ansambl Kadro, Fantastik, Günlük Yaşam, Romantik, Isekai, Cyberpunk, Kıyamet Sonrası, Orta Çağ ve diğer tüm jargonsal karşılıklar standartlaştırıldı.

### 🗑️ 5. Görsel Galerisinde Üçüncü Buton: Sil (`KitsugiImageGalleryDialog.kt`, `KitsugiImageDownloadHelper.kt`, `DownloadsScreen.kt`)
- **Silme Butonu Eklendi:** Galeri eylem sırası artık **İndir → Paylaş → Sil → Kapat** şeklindedir; kardeşleriyle aynı cam kutu dilinde kırmızı uyarı tonuyla çizilir ve Android TV kumandalarında odaklanabilir.
- **Dikey ve Yatay Eşitliği:** Dikey başlıkta 40.dp, yatay yan panelde 36.dp — boyut/köşe parametreleriyle yönetilen tek `KitsugiGalleryDeleteButton` bileşeni kullanılır.
- **Gerçek Silme:** MediaStore kaydını (`content://`), kullanıcının seçtiği SAF klasöründeki dosyayı ve `Download/Kitsugi/Images` altındaki kopyaları kaldırır; ardından harici galerilerin ölü kareler göstermemesi için `MediaScanner`'ı bilgilendirir.
- **İndirilenler Ekranı Entegrasyonu:** İndirilen resimler galerisinden silme işlemi kareyi anında kaldırır ve listeyi yeniden tarar; yeni grup düzeyinde silme butonu bir başlığın indirilen tüm resimlerini tek işlemde temizler.

### 🖼️ 6. GIF ve Çoklu Format Desteği (`KitsugiImageDownloadHelper.kt`)
- **Yerel Kaynaklar Paylaşılabilir:** `file://` ve `content://` öğeleri doğrudan FileProvider üzerinden iletilir; MediaStore/SAF satırları çözücü üzerinden okunur.
- **Genişletilmiş Format Tespiti:** GIF87a/89a, PNG + APNG, JPEG/MPO, WebP (VP8X), AVIF/AVIS, HEIF/HEIC, BMP ve TIFF formatları tanınır ve hareketli içerikler bozulmadan saklanır.

### 🔞 7. +18 Blur Zorunluluğu Tüm Kaynaklarda (`AdultFlagBackfillMigration.kt`, `ShikimoriAdultResolver.kt`, `TmdbAdultResolver.kt`, `MediaEntryRepository.kt`, `KitsugiApplication.kt`, `AiringNotificationWorker.kt`)
- **Simkl Listesi Onarımı:** Simkl senkronizasyonunun eksik bıraktığı yetişkin bayrağı kanonik kimlik üzerinden `AdultFlagBackfillMigration` ile tamamlanır.
- **Kaynaklar Arası +18 Yayılımı:** `MediaEntryRepository.entriesFlow` satırları `MediaIdentity.keys()` üzerinden eşleştirir; aynı yapımın AniList satırı +18 işaretliyse Simkl/Shikimori/"Tümü" sekmesindeki satırı da bulanıklaşır.
- **İşaretler Asla Silinmez:** `replaceSourcePreservingAdultFlags()` sayesinde otomatik senkron ve bildirim işçisi işaretli bayrakları korur.

### 🧪 8. Testler
- `ImageFormatDetectionTest`, `AdultFlagBackfillMigrationTest`, `MediaEntryRepositoryAdultPropagationTest`.

### 🎨 9. Tam Renk Seçici, Gradyan Vurgu Mekaniği & Otomatik Siyah-Beyaz Kontrast (`KitsugiAccentColorPickerDialog.kt`, `KitsugiAccentSupport.kt`, `Theme.kt`, `KitsugiColors.kt`, `AppSettings.kt`, `SettingsDataStore.kt`)
- **"+" Butonu Tam Panel Açıyor:** Eski "hex kodu girin" dialoğu kaldırıldı; yerine 3 sekmeli zengin panel geldi:
  - **Palet:** 19 renk ailesi × 10 ton (50→900) + siyah/beyaz = **192 renk**.
  - **Özel:** Tam **HSV doygunluk–değer karesi + ton şeridi** (sınırsız renk, isteğe bağlı hex alanı).
  - **Gradyan:** İki renkli açılı lineer gradyan: açı slider'ı + 8 yön ön ayarı (→ ↘ ↓ ↙ ← ↖ ↑ ↗), canlı şerit önizleme, başlangıç/bitiş renk seçimi.
- **Kalıcı Gradyan Mekaniği:** `customAccentColor2` (bitiş rengi) ve `customAccentGradientAngle` (açı) kalıcı olarak saklanır; tüm vurgu zeminlerinde iki renk arasında seçilen açıyla lineer akış uygulanır.
- **Otomatik Siyah ↔ Beyaz Kontrast (`onAccentColor`):** Vurgu zeminindeki TÜM yazı ve ikonlar, seçilen rengin koyuluğuna göre yumuşak (smoothstep) geçişle otomatik siyah veya beyaz kontrast alır (ayar ikonları, aksiyon butonları, oynatıcı overlayleri, FAB'lar vb.).

### 🔄 10. Bangumi Çapraz Eşitleme (Cross-Sync) Tam Entegrasyonu (`AuthViewModel.kt`, `BangumiSyncManager.kt`, `PlatformRateLimiter.kt`, `CrossSyncDisclaimer.kt`)
- **6/6 Platform Desteği:** AniList, MyAnimeList, Simkl, Kitsu, Shikimori ve Bangumi artık tam çapraz senkronizasyon havuzunda birlikte çalışır.
- **Hız Sınırı & Aday İndeksi:** Bangumi API için 400ms hız sınırı tanımlandı; ad eşleştirme ve çoklu başlık aramasında Japonca/orijinal ad desteği eklendi.
- **Akıllı İçe Aktarma:** Senkronizasyon sonrası Bangumi verisi `smartImport` ile yerel veritabanıyla doğrulanır.

### 🈯 11. TMDB/Simkl Kadro Adlarında Yanlış Alfabe Düzeltmesi & Hayali Karakter Görsel Zinciri (`PreferenceHelpers.kt`, `TmdbCreditsClient.kt`, `KitsugiCharacterClient.kt`)
- **Yanlış Alfabe Kökten Çözüldü:** `pickLatinAlias()` artık CJK-olmayan her alfabeyi Latin sanmak yerine `PreferenceHelpers.isLatinText()` ile harflerin gerçekten Latin (Türkçe harfler dahil) olduğunu doğrular; Arapça veya Tayca çeviriler elenerek doğru Latin/Romaji adlar seçilir.
- **Çapraz Kaynak Görsel Zinciri:** TMDB/Simkl animasyon içeriklerinde AniList'in görsel bulamadığı karakterler için sırasıyla MAL/Jikan → Shikimori → Kitsu kaynaklarından doğrulanmış gerçek MAL ID ile görsel çekilir.

### 🏢 12. Stüdyo & Yapımcı Yapım Listesi Sonsuz Sayfalama & Limit Düzeltmesi (`KitsugiStudioClient.kt`, `JikanGateway.kt`, `StudioDetailViewModel.kt`, `StudioDetailPage.kt`, `KitsugiModels.kt`)
- **Tenrai 50 İstek Limiti Koruması:** Tenrai `limit > 50` isteklerinde 400 hatası döndürüyordu; `TENRAI_MAX_LIMIT = 50` ile `JikanGateway.tenraiRest` içinde otomatik sınırlandırılarak yapım listesinin boş gelmesi önlendi.
- **Tüm Kaynaklarda Sayfalama Desteği:** 50'den fazla yapımı olan stüdyolar için MAL/Jikan (`has_next_page`), Shikimori (`page` ve `limit=50`), AniList (`pageInfo.hasNextPage`), TMDB (`movie` ve `tv` discover için `total_pages`) sayfalama desteği entegre edildi.
- **Akıcı Sonsuz Kaydırma & Hata Kurtarma:** Liste ve grid görünümünde sona yaklaşıldığında sonraki sayfa otomatik çekilir; yükleme anında alt ortada minimal yükleme çemberi, ağ hatasında "Devamı yüklenemedi · Tekrar dene" butonu sunulur.

### 🎬 13. Fragman (Ön İzleme) Satır Kalıcılığı & Oynatıcı Kontrolü (`KitsugiDetailThemesTrailerComponents.kt`)
- **Kalıcı Satır Kartı:** Ön İzleme kartında oynatıcı açıldığında "Fragmanı İzle" satırının kaybolması sorunu giderildi; açılış/kapanış müziklerindeki gibi oynatıcının üzerinde görünür kalır.
- **Aktif Durum & Toggle:** Oynatıcı açıkken daire içinde yukarı ok (chevron) ikonu, vurgulu arka plan ve renk gösterilir; satıra tekrar dokunulduğunda oynatıcı kapanır (veya altındaki "Kapat" düğmesi kullanılabilir).
- **Tüm Detay Sayfalarında Geçerli:** Ortak `KitsugiTrailerCard` bileşeni üzerinden hem kütüphaneden açılan medya detayında hem de arama/API detay sayfalarında (anime ve manga) aktif.

---

## 🇬🇧 English (v2.4.227)

### 🎯 1. Per-Source Search & Episode Mechanics: Separate Pipelines for Movies vs Series (`CsStreamRunner.kt`, `CsTitleMatcher.kt`)
- **Core principle:** Content search across ALL sources now uses ONLY the bare work title — season/episode info never enters the search query. No site (Turkish or foreign) can resolve "X Season 1 Episode 1"; the correct flow is established: search title → enter content page → (series) descend to the season+episode page → extract video data.
- **Movie pipeline (separate):** Movies carry no season logic; the matched content page is used directly as the video page (dataUrl/url → loadLinks), with no episode matching.
- **Series/anime pipeline (separate):** The target S/E is picked from the loaded page's episode list; when a plugin fills no episode list (page-style plugin), the page URL is used as the episode container.
- **Season page navigation:** When the episode list is populated but the target season is absent, the site keeps seasons on separate entries; the plugin's OWN search+load flow navigates to the season page — a generic, source-agnostic step applied for every addon.
- **Old bug fixed:** When episodes existed but none matched, the SERIES page URL was fed to loadLinks — the 25s link-extraction budget was wasted and the card showed "no streams found". The real reason is now recorded and season navigation is attempted instead.
- **Season-entry search (safety net):** Season-suffixed queries ("X Season 2") are never primary searches; they run only when the bare-title search returns zero results (sites that index seasons as separate entries).
- **Page-type-driven pipeline:** On misclassification (movie flag but the source returns a "single episode" series entry — very common on Turkish sites), the pipeline is chosen by the loaded page's real type; the mechanics follow the source, not the flag.

### 🛡️ 2. Cloudflare WebView Leak & Crash-Risk Fix (`CloudflareInterceptor.kt`, `KitsugiApplication.kt`, `CsCfWarmupManager.kt`)
- **WebView Leak After Successful Challenges:** The old timeout destroyed the WebView only while the latch was still pending. Once `cf_clearance` succeeded, the latch completed and the timeout skipped destruction, potentially allowing Chromium/RenderThread resources to accumulate. Success, timeout, and cancellation now share one main-thread, exactly-once teardown.
- **No Batch of Startup WebViews:** Startup no longer launches up to 14 hidden WebViews to pre-warm Cloudflare cookies. Challenges are solved lazily for the host that actually needs them.
- **Safer Teardown:** Removed the immediate `about:blank` navigation before destruction; teardown removes the view, stops loading, clears client references, and destroys the WebView once.

### 🌐 3. Source-Scoped List Membership & Adult Content Blur (`KitsugiModels.kt`, `AppRoot.kt`, `AppRootDetailPages.kt`)
- **Source-Scoped Membership (`matchesInSource`, `firstMatchingInSource`):** List membership and detail lookups are scoped by platform source. Bangumi entries open Bangumi's editor, and Simkl or AniList records do not falsely mark Bangumi items as listed.
- **Scroll-to-Top Bottom Bar Reset:** Profile favorites scroll-to-top button now resets the shared bottom-bar visibility before and after scrolling.
- **Adult Content Blur Propagation:** Adult status from AniList profile favorites now reaches detail navigation so cinematic loading animation posters blur properly from the first frame.

### 🏷️ 4. Unified Global Tag & Genre Translations (`BangumiTagDictionary.kt`, `SearchTranslation.kt`, `GenreLabelFormatter.kt`, `KitsugiTranslations.kt`)
- **Consistency Across Providers:** Unified tag and genre definitions for AniList, MAL, Kitsu, Shikimori, and Bangumi across dictionaries, XML string resources, and search maps.
- **Standardized Terms:** Aligned translations for Heterosexual, Protagonist variants, Reverse Harem, Cast types, Kuudere, Nudity, Ensemble Cast, Fantasy, Slice of Life, Romance, Isekai, Cyberpunk, Post-Apocalyptic, and Medieval.

### 🗑️ 5. Third Gallery Button: Delete Downloaded Media (`KitsugiImageGalleryDialog.kt`, `KitsugiImageDownloadHelper.kt`, `DownloadsScreen.kt`)
- **Delete Button Added:** Action row now includes delete with two-step confirmation, MediaStore/SAF cleanup, and index synchronization.
- **Downloads Screen Integration:** Seamless tile removal and group-level bulk deletion.

### 🖼️ 6. Full GIF / Image Format Support (`KitsugiImageDownloadHelper.kt`)
- **Local File Sharing:** FileProvider sharing for local files and raw preservation for animated GIFs, animated WebP, and APNG.

### 🔞 7. Universal +18 Blur Enforcement (`AdultFlagBackfillMigration.kt`, `MediaEntryRepository.kt`, etc.)
- **Cross-Source Propagation:** Canonical identity matching propagates adult flags across all providers and ensures flags are never dropped during sync.

### 🧪 8. Tests
- `ImageFormatDetectionTest`, `AdultFlagBackfillMigrationTest`, `MediaEntryRepositoryAdultPropagationTest`.

### 🎨 9. Full Color Picker, Gradient Accent Mechanics & Auto Black-White Contrast (`KitsugiAccentColorPickerDialog.kt`, `KitsugiAccentSupport.kt`, `Theme.kt`, `KitsugiColors.kt`, `AppSettings.kt`, `SettingsDataStore.kt`)
- **"+" Button Opens Full Panel:** Replaced plain hex dialog with a 3-tab color picker:
  - **Palette:** 19 color families × 10 shades + black/white = **192 colors**.
  - **Custom:** Full **HSV saturation-value square + hue slider** (infinite colors, optional hex input).
  - **Gradient:** Two-color angled linear gradient with slider + 8 direction presets, live preview, start/end color targets.
- **Persistent Gradient Mechanics:** Persisted `customAccentColor2` and `customAccentGradientAngle`; renders gradient flows across buttons, setting icon circles, badges, and previews.
- **Automatic Black ↔ White Contrast (`onAccentColor`):** Contrast on accent backgrounds automatically transitions smoothly between black and white based on luminance.

### 🔄 10. Bangumi Full Cross-Sync Integration (`AuthViewModel.kt`, `BangumiSyncManager.kt`, `PlatformRateLimiter.kt`, `CrossSyncDisclaimer.kt`)
- **6/6 Connected Platform Support:** Full parity across AniList, MyAnimeList, Simkl, Kitsu, Shikimori, and Bangumi.
- **Rate Limiting & Japanese Title Matching:** 400ms rate limiter and candidate title resolution for Bangumi subjects.
- **Post-Sync Verification:** Automated `smartImport` verification for Bangumi library after sync.

### 🈯 11. Fixed Wrong-Script Cast Names & Character Image Fallback Chain (`PreferenceHelpers.kt`, `TmdbCreditsClient.kt`, `KitsugiCharacterClient.kt`)
- **Latin Script Validation:** Replaced naive non-CJK check with `PreferenceHelpers.isLatinText()` to prevent Thai/Arabic aliases from overriding English/Romaji names.
- **Cross-Source Character Image Chain:** For TMDB/Simkl anime entries missing AniList images, resolves portraits in sequence across MAL/Jikan → Shikimori → Kitsu using verified MAL IDs and name tokens.

### 🏢 12. Studio & Producer Works Infinite Pagination & Limit Fix (`KitsugiStudioClient.kt`, `JikanGateway.kt`, `StudioDetailViewModel.kt`, `StudioDetailPage.kt`, `KitsugiModels.kt`)
- **Tenrai 50 Request Limit Safeguard:** Tenrai returned HTTP 400 for `limit > 50`; capped automatically via `TENRAI_MAX_LIMIT = 50` in `JikanGateway.tenraiRest`.
- **Full Cross-Source Works Pagination:** Added continuous pagination for studios with >50 works across MAL/Jikan (`has_next_page`), Shikimori (`page` and `limit=50`), AniList (`pageInfo.hasNextPage`), and TMDB (`total_pages` across movie and tv discover).
- **Infinite Scrolling & Failure Recovery:** Seamless pagination as the user scrolls near the end of grid/list layouts, with a minimal loading indicator and retry button on network failures.

### 🎬 13. Trailer Preview Row Persistence & Player Toggle (`KitsugiDetailThemesTrailerComponents.kt`)
- **Persistent Header Row:** The "Watch Trailer" row remains visible above the expanded player, mimicking the opening/ending theme music player layout.
- **Active State & Tap Toggle:** Features an upward chevron icon and accent highlighting when active. Tapping the row toggles the player closed, alongside the bottom close button.
- **Universal Scope:** Powered by the shared `KitsugiTrailerCard`, functioning seamlessly across both library entries and API search details for anime and manga.

---

## 🇹🇷 Türkçe (v2.4.226)

### 📝 1. Liste Düzenleme Ekranı & Platform Eşitlemesi (`KitsugiEditMediaSheet.kt`, `BangumiSyncManager.kt`, `KitsuSyncManager.kt`, `ShikimoriSyncManager.kt`)
- **Bangumi Platform Entegrasyonu:** Bangumi kayıtları artık "Yerel/Manuel" kitaplık yerine gerçek Bangumi platform şablonuyla açılır; gereksiz manuel başlık ve +18 alanları yerine "Gizli" ve "Etiketler" alanları gösterilir. Notlar ve etiketler Bangumi API'sine senkronize edilir.
- **Kitsu Gizlilik ve Tarih Senkronizasyonu:** Kitsu düzenleme sayfasına "Gizli" seçeneği eklendi. Başlangıç/bitiş tarihleri, kişisel notlar ve gizlilik ayarları hem import edilir hem de Kitsu API'sine gönderilir. Kitsu'da bulunmayan manga cilt satırı gizlendi.
- **Shikimori "Yeniden İzleniyor" ve Tekrar Sayacı:** Shikimori için "Yeniden İzleniyor" durumu ve tekrar sayısı kontrolü eklendi; kişisel metin notu, tekrar sayısı ve cilt bilgileri hem içe aktarılır hem de uzaktaki Shikimori profiline iletilir.
- **Platform Destek Rozetleri & Güvenli Güncelleme:** İlgili platform API'sinin desteklemediği alanlar için düzenleme sayfasında şeffaf bilgilendirme rozetleri gösterilir (örn. Simkl'de not/tarih/favori, MAL'de favori). Yalnızca doldurulan alanlar gönderilir, boş bırakılan alanlar uzaktaki veriyi ezmez.

### 🚀 2. Eklenti Arama & Yayın Sağlayıcı İyileştirmeleri (`CsStreamRunner.kt`, `CsRuntimeInit.kt`)
- **Canlı Domain Koruması & Tablo Güvenlik Ağı:** Yerleşik ve uzak domain tablolarının, eklentinin kendi sağlıklı canlı adresini veya çalışma zamanında bulduğu aynayı (örn. FilmMakinesi, Dizipal, SezonlukDizi) ezmesi engellendi. Eski tablolar artık yalnızca boş, zehirli veya bilinen ölü adresleri kurtarmak için devreye girer; sağlıklı domain'e yalnızca açılışta güncellenen uzak tablo geri-alma korumasıyla dokunabilir.
- **Title-Search Fallback Koruması:** `getLoadUrl()` boş veya tanımsız döndüğünde arama sürecinin kilitlenmesi önlendi; boş dönen native sonuçlar otomatik olarak başlık bazlı aramaya yönlendirilir.
- **Scraper Disk Önbelleği Kaldırıldı:** 50 MB'lık OkHttp disk önbelleği kaldırıldı. Bazı sitelerin arama sayfalarında döndürdüğü `max-age` başlıkları nedeniyle Cloudflare engeline veya boş sonuca takılan aramaların diske kalıcı olarak yazılıp süresiz "0 sonuç" dönmesi sorunu çözüldü (Resmi CloudStream mimarisine hizalandı).
- **Arama Bütçesi & Eşzamanlılık Artışı:** Global `searchSemaphore` bütçesi 12'den 24'e çıkarıldı. 30+ eklenti paralel sorgulanırken sıradaki eklentilerin 20 saniyelik zaman aşımına uğraması engellendi.

### 🎬 3. Kitsu Film Ayrıştırması & TMDB Çakışma Önlemi (`KitsugiEpisodeRatingsRepository.kt`, `ApiResultDetailViewModel.kt`, `MediaEntryDetailViewModel.kt`)
- **Film ve Dizi Kimlik Çakışması Giderildi:** TMDB üzerinde aynı kimlik numarasını paylaşan film ve dizi kayıtları (örneğin *Howl's Moving Castle* filmi ile *Roar* dizisi) birbirinden tamamen ayrıldı. Film kayıtları yalnızca film uç noktalarını sorgular ve TV/SeriesGraph/TVDB yollarına düşmez.
- **Kitsu Alt Tür Tespiti:** Kitsu listelerinde tür bilgisi bulunmayan kayıtlar için Kitsu API üzerinden `subtype` ("movie") sorgusu yapılıp kimlik başına önbelleklenir. Hero, detay logosu ve galeriler doğru türü kullanır.
- **Temiz Medya Galerisi:** Film kayıtlarının Fanart galerilerine TV dizilerinin afiş ve ekran görüntülerinin karışması engellendi.

### 🔔 4. Profil ve Listem Bildirim Butonları & Keşfet Zarı (`AppRootTabPages.kt`, `ExploreScreen.kt`, `MyListComponents.kt`, `MyListScreen.kt`, `KitsugiProfileScreen.kt`)
- **Profil ve Listem Bildirim Entegrasyonu:** AniList, MAL veya Simkl hesaplarından biri bağlı olduğunda, Profil ekranında istatistik butonunun soluna ve Listem ekranında zar butonunun soluna bildirim butonu eklendi.
- **Keşfet Zarı Bağımsızlaştırıldı:** Keşfet ekranında bildirim butonu açıkken de rastgele keşfet (🎲) zarı görünür kalır ve bildirimin solunda konumlandırılır.

### 🌐 5. Bangumi "Diğer Adlar" & CJK Varyantları (`BangumiLocalizedName.kt`, `KitsugiSimklDetailClient.kt`)
- **Japonca, Çince ve Korece Alternatif Adlar Geri Getirildi:** "Diğer Adlar" / eşanlamlılar listesindeki Latin-harf zorunluluğu kaldırılarak Japonca, Çince ve Korece özgün varyantlar yeniden görünür hale getirildi. Yalnızca ana başlıkla birebir aynı olan tekrarlar elenir.

### 🏷️ 6. Bangumi Etiket & Tür Sözlüğü (`BangumiTagDictionary.kt`, `KitsugiTranslations.kt`, `strings.xml`)
- **%100 Resmî Meta Etiket Kapsamı:** Bangumi'nin 158 resmî meta etiketi (kaynak, tip, bölge, tema, hedef kitle, oyun türü, platform, kitap, müzik) eksiksiz olarak Türkçe ve İngilizceye çevrildi.
- **817 Kullanıcı Etiketi Desteği:** En popüler 817 kullanıcı etiketi Türkçe ve İngilizce karşılıklarıyla sözlüğe eklendi. Yazım varyantları (ör. `催泪` / `催涙` / `Duygusal`) tekilleştirildi.
- **Okunabilirlik Sıralaması:** Çevrilmemiş CJK etiketler listenin sonuna ötelenerek önce kullanıcının dilindeki etiketlerin görünmesi sağlandı.

### 🎨 7. Bangumi ve Simkl Vitrin Logoları & Çok Kaynaklı Zengin Galeriler (`HeroLogoPolicy.kt`, `MediaGalleryIdentity.kt`, `KitsugiPersonImageAggregator.kt`)
- **Vitrin Görsel Logoları:** Bangumi ve Simkl vitrinlerinde grafik logo varsa metin başlık yerine logo gösterilir.
- **Güvenli Çapraz Kimlik Eşleştirme:** Bangumi subject ID'si asla MAL ID olarak kullanılmaz; Simkl sonuçlarında TMDB kimliği korunur.
- **Karakter, Ekip ve Seslendirmen Galerileri:** MAL ID yoksa ad ve alternatif adlarla Jikan eşleşmesi yapılarak zengin galeriler oluşturulur.

### 🌟 8. Keşfet: "Yakında Yayında" Şeridi & Mantıksal Kaynak Rafları (`ExploreScreen.kt`, `AllSourcesExplore.kt`, `ExploreCategories.kt`)
- **Şerit Konumlandırma Standardı:** "Yakında Yayında" geri sayımlı şeridi, tüm kaynak sayfalarında kategori kısayollarının hemen altına taşındı.
- **Mantıksal Raf Hiyerarşisi:** Anime Rafları → Manga Rafları → Kaynağa Özgü Raflar (Manhwa, Novel, Simkl) düzenine kavuşturuldu.
- **Eksik Kısayollar:** "En Yüksek Puanlı Animeler" ve "En Yüksek Puanlı Mangalar" kısayolları eklendi.

### 🗓️ 9. Tüm Kaynaklarda Birleşik Yayın Tarihi ve Geri Sayım Mantığı (`NextAiringFormat.kt`, `DetailSharedComponents.kt`, `NextAiringChip.kt`)
- **Tek Ortak Biçim Katmanı (`NextAiringFormat`):** AniList, MAL/Jikan, TMDB, Shikimori, Bangumi, Simkl ve Kitsu kaynaklarında yayın tarihi ve geri sayım tek standart formata getirildi.
- **Detay Sayfaları:** *"Yaklaşan Yayın: Bölüm 2, 2026-10-15 tarihinde yayında (6 gün sonra)"* şeklinde hem tarih hem geri sayım gösterilir.

### 📋 10. Kullanıcı Profili Anime/Manga Listesi ve "Listem" Tam Paritesi (`KitsugiUserMediaListScreen.kt`, `MyListFilterHelpers.kt`)
- **Tasarım ve Üst Kontrol Paritesi:** Başka bir kullanıcının listesi ana Listem ekranıyla aynı başlık, görünüm değiştirici, arama ve filtre kontrollerine kavuştu.
- **Birleşik Anime & Manga Veri Kümesi:** Anime ve Manga listeleri tek havuzda birleştirilerek filtreleme seçenekleri zenginleştirildi.
- **Gelişmiş Filtreler:** Durum, medya türü, puan, yıl ve özel filtreler eklendi; poster önizleme ve rastgele seçim desteklendi.

### 🔐 11. Kalıcı Kasa Yedekleme, Çoklu Cihaz Çakışma Yönetimi & Android Keystore (`VaultBackupWorker.kt`, `VaultMerge.kt`, `LocalVaultKeyStore.kt`)
- **WorkManager Kalıcı Kuyruk:** Ağ kesintilerinde ve süreç kapanmalarında kasa yedekleri kaybolmaz.
- **3 Yönlü Akıllı Birleştirme:** Farklı servis token'ları çakışmasız birleşir; aynı servis token çakışmalarında kullanıcıya seçim sunulur.
- **Supabase CAS Koruması:** `kitsugi_vault_cas` veritabanı fonksiyonu ile yarış durumları engellenir.
- **Kesintiye Dayanıklı Şifre Değiştirme & Keystore Şifreleme:** Kasa anahtarı işlem yarıda kalsa dahi korunur ve yerel anahtar Android Keystore ile şifrelenir.
- **22 Taşınabilir Ayar:** Tema, liste düzeni ve dil ayarları şifreli yedek kapsamına alındı.

### 🧩 12. Eklenti Keşfet Portalı Ana Sayfa Paritesi & Stüdyo Detay İyileştirmeleri (`AddonExploreDialog.kt`, `AddonFullScreenGridPage.kt`, `AddonHomeParity.kt`, `StudioDetailPage.kt`, `StudioDetailComponents.kt`, `StudioDetailViewModel.kt`)
- **Eklenti Vitrin ve Raf Paritesi:** Eklenti portalı içerisindeki eklenti keşfet sayfaları ana sayfa keşfet sayfasıyla birebir aynı görsel dile ve etkileşim standartlarına kavuştu (`KitsugiHeroSection` öne çıkan vitrini, otomatik kayan carousel, çizgi-nokta göstergeleri, neon çerçeveli `KitsugiExploreMediaCard`, snap-fling kaydırma ve "Tümünü Gör" sözleşmesi).
- **"Tümünü Gör" Grid ve Liste Paritesi:** Eklenti keşfet ekranındaki "Tümünü Gör" açılır sayfası (`AddonFullScreenGridPage`), ana stüdyo ve keşfet grid sayfalarıyla aynı tasarım diline geçirildi: emojili tür filtre çipleri (✨ Tümü / 🎌 Anime / 🎥 Film / 📺 Dizi), Filtre+Sıralama alt sayfası (`KitsugiStudioFilterBottomSheet`), grid ↔ liste görünüm geçişi (kalıcı tercih hafızasıyla), içerik sayacı, kaydırınca beliren üst şerit ve hızlı yukarı FAB.
- **CS3 Ortak Model Köprüsü & Temiz Eklenti Rozetleri:** CloudStream 3 arama sonuçları ortak `JikanSearchResult` modeline dönüştürülerek bilinmeyen kaynaklarda sahte `#id` gösterimi kaldırıldı; genel eklenti rozeti (`Icons.Default.Extension`) ve eklenti çipi ile vitrin çipinin boş kalması önlendi.
- **Stüdyo Kuruluş Tarihi Formatı:** Stüdyo detay sayfasındaki ham ISO formatlı kuruluş tarihi (`1998-10-01T00:00:00+00:00`) `KitsugiDateUtils` ile *"Kuruluş: 1 Ekim 1998"* biçiminde okunabilir Türkçe tarihe dönüştürüldü (hem vitrin başlığında hem sol bilgi panelinde).
- **Stüdyo "Hakkında" Kartı Tam Detay Paritesi:** Stüdyo sayfası "Hakkında" kartı medya detay sayfasındaki `DetailSynopsisCard` ile tam pariteye kavuşturuldu: otomatik Türkçe çeviri desteği (`autoTranslateEnabled` veya Rusça metin tespiti), `DetailCache` önbellekleme, uygulama içi çeviri ve 3. parti çevirmen butonu, tek dokunuşla panoya kopyalama ("Panoya kopyalandı") ve uzun metinlerde "Daha fazla / Daha az" genişletme kontrolü.

### 🎯 13. "Listeye Ekle" & "Zaten Listede" Çelişkisi ve Arama Başlık Eşleşmesi (`KitsugiModels.kt`, `AppViewModel.kt`, `ApiResultDetailPage.kt`, `MyListFilterHelpers.kt`, `MyListLibraryGrouping.kt`, `MyListScreen.kt`)
- **Buton ve Kontrol Tutarlılığı:** Detay sayfasındaki "Listeye Ekle" butonu ile ekleme akışındaki mükerrer kontrolü aynı eşleme sonucuna bağlandı (`findExistingEntry`). Kayıt kütüphanede zaten mevcutsa buton dinamik olarak **"✎ Düzenle"** durumuna geçer ve tıklandığında mevcut kaydı doğrudan açar.
- **Açıklayıcı ve Hedef Gösteren Uyarı Mesajı:** Ekleme akışındaki uyarı mesajı artık gelen sorgu adını değil, listede bulunan asıl kaydın adını ve bulunduğu sekmeyi bildirir (*"\"Clannad: After Story\" zaten listende var (AniList sekmesi)."*).
- **Çoklu Başlık Varyantı Eşleşmesi:** `MediaEntry.matches` kontrolü tüm başlık türevlerini (özgün ad, İngilizce, Japonca, Romaji) `MediaIdentity.normalizedTitle` standardıyla karşılaştırarak dil tercihlerinden kaynaklanan eşleşme kayıplarını giderir.
- **Listem Gelişmiş Arama:** Listem ekranında arama yapılırken alternatif İngilizce/Japonca başlıklar ve noktalama/boşluk duyarsız normalleştirilmiş sorgular taranır ("clannad after story", "CLANNAD 〜AFTER STORY〜", "クラナド" gibi tüm yazım varyantları doğru kaydı bulur).
- **Çözülmüş Kimliklerle Güvenlik Ağı:** Erken tıklama durumlarında çift kayıt oluşmaması için kimlik çözümleme sonrasında ikinci bir kontrol eklenerek veri tabanı bütünlüğü korundu (6 yeni birim testiyle doğrulandı).

### 🏢 14. Stüdyo & Yapımcı Kaynak Kimliği Ayrıştırması & Şirket Arama Yönlendirmesi (`KitsugiStudioClient.kt`, `StudioSourceSupport.kt`, `JikanSearchClient.kt`, `SearchScreen.kt`, `SearchResultRow.kt`, `StudioDetailPage.kt`, `StudioDetailViewModel.kt`)
- **Sağlayıcıya Özgü Stüdyo Kimlik Ayrıştırması:** Stüdyo ve yapımcı kimliklerinin farklı platformlarda çakışması (örneğin MAL üzerindeki Aniplex'in AniList ID çakışması nedeniyle Bones olarak açılması) engellendi; her stüdyo ve yapımcı öncelikle kendi kaynak kimliğiyle (`source`) sorgulanır.
- **İsim Doğrulaması & Akıllı Yeniden Arama:** Jikan/MAL üzerinde dönen stüdyo adı tıklanan isimle uyuşmazsa şirket adıyla otomatik doğrulama araması yapılır; eşleşme teyit edilemeyen yanıltıcı stüdyo kayıtları önlenir.
- **Çoklu Kaynak Desteği:** TMDB, Shikimori ve Bangumi stüdyo yolları normalize edildi; Bangumi için yalnızca şirket türündeki (`type = 2`) kayıtlar stüdyo olarak eşleştirilir.
- **Doğrudan Stüdyo Detayına Yönlendirme:** Arama ekranında "Stüdyo" filtresiyle aranan kayıtlar anime/medya detayına değil, doğrudan ilgili sağlayıcının `StudioDetailPage` sayfasına yönlendirilir; stüdyo arama satırında yanıltıcı "listeye ekle" butonu gizlenir.
- **Kesintisiz Yükleme Deneyimi & Görsel Aktarımı:** Stüdyo logosu ve adı navigasyon rotası üzerinden yükleme ekranına taşınarak beyaz/boş ekran titremesi engellenir.

### 📱 15. Profil Favorilerinde Alt Bar (BottomBar) ile Uyumlu Kategori Butonu Hizalaması (`AniListProfileContent.kt`, `KitsugiProfileScreen.kt`, `AppRootTabPages.kt`)
- **Dinamik Alt Gezinme Çubuğu Hizalaması:** Profil ekranı "Favoriler" sekmesinde yer alan yüzen kategori ("Anime" / `☰`) filtre butonu ve hızlı yukarı çıkma FAB'ı, alt gezinme çubuğunun (BottomBar) açık/kapalı durumuna göre dinamik olarak konumlandırılır (`isBottomBarVisible` etkinken `96.dp + insets`, kapalıyken veya yatay modda `16.dp / 20.dp + insets`).
- **Görsel Çakışma ve Dokunma Engeli Giderildi:** Kategori butonunun "Keşfet" sekmesi üzerine binmesi, menü öğelerinin birbirini perdelemesi ve listenin son satırının alt barın altında kalması tamamen çözüldü (`Spacer(140.dp)`).

### 📐 16. Dikey Mod Kenar Taşması (Peek) Düzeltmesi (`DetailPageScaffold.kt`)
- **Kenar Boşluğu Temizliği:** Dikey mod detay sayfalarında (`DetailPageScaffold`), `HorizontalPager` üzerindeki `contentPadding = PaddingValues(horizontal = 16.dp)` kaldırıldı ve dolgu doğrudan sayfa `Box` bileşeni içine taşındı (`padding(horizontal = 16.dp)`).
- **Komşu Sekmelerin Ekrana Taşması Engellendi:** Ekran kenarlarından komşu sekmelerin köşelerinin görünmesi ("peek" sorunu) tamamen giderildi; sekmeler ekran genişliğini tam kaplayarak kenarlardan taşma yapmaz.

### 🌐 17. Cümle Sınırlarından Bölmeli ve Kademeli Uzun Biyografi & Özet Çevirisi (`TranslationManager.kt`, `CharacterDetailViewModel.kt`, `StaffDetailViewModel.kt`, `ApiResultDetailViewModel.kt`, `MediaEntryDetailViewModel.kt`)
- **4 Kademeli Akıllı Metin Bölme:** 2000+ karakterlik uzun metinler (özellikle HTML'den gelen ve satır sonu içermeyen dev tek paragraf karakter/personel biyografileri) sırasıyla paragraf (`\n\n`) → satır (`\n`) → cümle (`. ! ? … 。！？；`) → kelime sınırlarından bölünür. Noktalama sonrası boşluk şartı aranarak `3.14` ve `Mr.` gibi kısaltmalar korunur.
- **Kademeli Çeviri Akışı (Streaming UI):** Uzun metinlerde her parça çevrildikçe ekrana "çevrilen kısım + henüz çevrilmemiş orijinal kuyruk" anında yansıtılır; metin boyutu sıçramaz, dil kullanıcının gözü önünde akıcı şekilde Türkçeye dönüşür.
- **Uygulama Seviyesinde Retry & Sessiz Hata Koruması:** Her parça için 3 denemeli üstel geri çekilme (`delay`) eklendi; HTTP durum kodları loglanır ve `fetchSingleChunk` hata durumunda `null` dönerek başarısızlıkları açıkça bildirir.
- **Önbellek Hijyeni:** Room ve `DetailCache`'e yalnızca %100 eksiksiz tamamlanan çeviriler yazılır; yarım çeviriler önbelleğe alınmaz.

### 🛡️ 18. Medya Detay Sosyal Eşleşme Güvenliği & Keşfet Vitrin Backdrop Önceliği (`KitsugiMediaSocialClient.kt`, `DetailCache.kt`, `KitsugiHeroSection.kt`, `TmdbDiscoverClient.kt`, `ExploreViewModel.kt`)
- **Kaynak ve Tür Bazlı Sosyal Eşleşme:** Detay sayfalarındaki inceleme, forum ve aktivite akışlarında yabancı platform kimlikleri (TMDB, Simkl vb.) doğrudan MAL veya AniList ID'si olarak kabul edilmez. Yalnızca açık ve doğrulanmış çapraz kimlikler kullanılır.
- **Aktivite ve İnceleme Filtresi:** Anime/Manga olmayan içeriklerde Jikan/AniList sosyal sorguları yapılmaz. Aktiviteler hedef AniList ID'si ve türüyle birebir eşleşmek zorundadır; medyasız serbest metin aktiviteleri filtrelenir.
- **Review Cache Tür İzolasyonu:** `DetailCache.getMediaReviews` artık `source + ID + mediaType` üçlüsüyle izole edilir; dizi/anime incelemeleri birbirine taşmaz.
- **Keşfet Vitrininde Yatay Backdrop Önceliği:** Anime, TV ve filmlerde dikey ekranlarda da mevcut yatay backdrop görselleri posterden önce denenir. TMDB movie/TV kayıtları exact ID ile, diğerleri tür ve yıl uyumlu başlık aramasıyla çözülür (Manga oran mantığını korur).

### 🖼️ 19. Fanart API Galeri Detayları, Kalıcı Bildirim Arşivi ve Bangumi Bildirim Kaynağı (`FanartApiClient.kt`, `GalleryItem.kt`, `KitsugiImageGalleryDialog.kt`, `NotificationArchiveStore.kt`, `KitsugiNotificationsViewModel.kt`, `NotificationDiagnostics.kt`)
- **Fanart API Detayları Galeride:** Fanart.tv v3 API'sinden gelen `name` ve `iMDb` alanları galeride "Ad" ve "IMDb ID" satırları olarak sunulur. Yatay modda sağ panelde yer alırken, dikey modda üst bara eklenen ℹ️ "Detaylar" butonuyla açılan alt levhada gösterilir.
- **Kalıcı Bildirim Arşivi (`NotificationArchiveStore`):** MAL, Simkl, Kitsu ve Bangumi gibi API'sinde kişisel bildirim ucu bulunmayan kaynaklar için üretilen yayın takvimi ve izleme listesi bildirimleri yerel `notifications_archive.json` dosyasında (kaynak başına 300 kayıt) ve Kitsugi hesabı bulut yedeğinde (`user_data.notifications`) kalıcı olarak saklanır; 7 günlük takvim penceresinden düşen bildirimler kaybolmaz.
- **Bangumi Bildirim Kaynağı:** Bildirim ekranı ve seçici paneline 6. kaynak olarak Bangumi eklendi. İzleme listesi (在看/DOING) + önbellekli MAL çapraz çözümü + yayın takvimi eşleşmesiyle bildirimler üretilir; hesap bağlı olmadığında yerel kayıtlar gösterilir. Bildirim Teşhis paneli 6 kaynağı canlı olarak test eder.

### 🔞 20. Shikimori Liste Kartları +18 (Adult) Blur Düzeltmesi & GraphQL Çözümlemesi (`ShikimoriAdultResolver.kt`, `ShikimoriImportManager.kt`, `ShikimoriAdultFlagMigration.kt`)
- **Kök Neden Tespiti:** Shikimori'nin `user_rates` REST uç noktası gömülü anime/manga verilerini `AnimeSerializer` ve `MangaSerializer` ile döndürür; bu serializer'lar `rating` ve `genres` alanlarını hiç taşımaz. Eski `fetchAdultMediaIds` fonksiyonu da aynı kısıttan ötürü her zaman boş dönüyor ve Shikimori kayıtları `isAdult = false` olarak içe aktarılıyordu (blur ayarı açık olsa bile liste kartlarında uygulanmıyordu).
- **Yeni `ShikimoriAdultResolver` (GraphQL):** 50'lik gruplar halinde `{ animes(ids: "...", limit: 50, censored: false) { id rating genres { name } } }` sorgusu koşturulur (`censored: false` ile hentai filtresi aşılır, manga için şemada `rating` bulunmadığından tür listesi kullanılır). Sonuçlar önbelleğe alınır ve hız sınırlarına uyulur.
- **Mevcut Veritabanı Kayıtlarının Onarımı (`ShikimoriAdultFlagMigration`):** Listem ekranı açıldığında yerel veritabanında `isAdult = false` kalmış Shikimori kayıtları GraphQL üzerinden taranır ve +18 olanlar (rx/hentai) arka planda otomatik olarak `isAdult = true` yapılır.

### 📦 21. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.226-foss.apk`).

---

## 🇬🇧 English (v2.4.226)

### 📝 1. Media Entry Editor & Provider Parity (`KitsugiEditMediaSheet.kt`, `BangumiSyncManager.kt`, `KitsuSyncManager.kt`, `ShikimoriSyncManager.kt`)
- **Bangumi Platform Alignment:** Bangumi entries open with genuine Bangumi platform templates rather than generic "local library" fields (title, +18, total counts); introduces "Private" and "Tags" fields with bidirectional sync to Bangumi API.
- **Kitsu Privacy & Date Sync:** Added "Private" toggle to Kitsu editor. Start/finish dates, personal notes, and privacy settings are imported and synced to Kitsu; unsupported manga volume counter is hidden.
- **Shikimori Rewatching & Counters:** Added "Rewatching" status and rewatch count field; personal notes, rewatch counts, and manga volume progress are fully synced bidirectionally.
- **Platform Capability Indicators:** Explicit informational labels indicate fields unsupported by remote APIs (e.g. notes/dates on Simkl, favorites on MAL). Only populated values are synced to prevent remote data erasure.

### 🚀 2. Extension Search & Stream Runner Overhaul (`CsStreamRunner.kt`, `CsRuntimeInit.kt`)
- **Live Domain Protection & Table Safety Net:** Hardcoded and remote domain tables no longer overwrite healthy plugin domains or runtime-discovered mirrors (e.g., FilmMakinesi, Dizipal, SezonlukDizi). Stale tables are now only used to rescue empty, poisoned, or dead URLs.
- **Title-Search Fallback Preservation:** Empty native `getLoadUrl()` resolution results no longer swallow the search pipeline; they cleanly fall back to title-based search.
- **Scraper Disk Cache Removed:** Removed the 50 MB OkHttp disk cache from the scraper client. Websites serving `max-age` headers on search queries previously caused Cloudflare challenges or 0-result pages to be cached indefinitely on disk.
- **Concurrency & Budget Increase:** Increased global `searchSemaphore` from 12 to 24 permits, preventing queued plugins from exhausting their 20-second timeout budgets during 30+ plugin parallel searches.

### 🎬 3. Kitsu Movie Subtype Disambiguation & TMDB Conflict Prevention (`KitsugiEpisodeRatingsRepository.kt`, `ApiResultDetailViewModel.kt`, `MediaEntryDetailViewModel.kt`)
- **Movie vs. TV ID Collision Prevention:** Media sharing identical TMDB IDs between movies and TV shows (e.g. *Howl's Moving Castle* film vs *Roar* series) are strictly isolated. Movie entries query only movie endpoints and avoid TV/SeriesGraph fallbacks.
- **Kitsu Subtype Caching:** Queries and caches the Kitsu API `subtype` attribute per entry, allowing hero, detail logos, and galleries to use movie endpoints.
- **Clean Media Galleries:** Prevents TV series artwork from polluting anime movie Fanart galleries.

### 🔔 4. Profile & My List Notification Buttons & Explore Dice Button (`AppRootTabPages.kt`, `ExploreScreen.kt`, `MyListComponents.kt`, `MyListScreen.kt`, `KitsugiProfileScreen.kt`)
- **Notification Navigation in Profile & My List:** When AniList, MAL, or Simkl is connected, a notification icon button appears in Profile (left of stats) and My List (left of the dice button).
- **Independent Explore Dice:** The random picker dice button (🎲) in Explore remains visible even when notifications are active.

### 🌐 5. Bangumi Alternate Titles & CJK Variants Restored (`BangumiLocalizedName.kt`, `KitsugiSimklDetailClient.kt`)
- **CJK Alternate Titles Restored:** Lifted the Latin-only restriction on alternative titles in Bangumi and Simkl, re-enabling Japanese, Chinese, and Korean title variants in the "Other Names" section while discarding exact duplicates of the main title.

### 🏷️ 6. Bangumi Tag & Genre Dictionary (`BangumiTagDictionary.kt`, `KitsugiTranslations.kt`, `strings.xml`)
- **100% Official Meta Tag Coverage:** All 158 official Bangumi meta tags (sources, types, genres, themes, platforms, etc.) are localized in Turkish and English.
- **817 User Tags Dictionary:** 817 popular user tags localized with spelling variant deduplication (e.g. `催泪` / `催涙` / `Tearjerker`).
- **Readability Tag Sorting:** Unmapped CJK tags sort to the end of the tag cloud, prioritizing readable tags.

### 🎨 7. Hero Logos & Multi-Source Galleries (`HeroLogoPolicy.kt`, `MediaGalleryIdentity.kt`, `KitsugiPersonImageAggregator.kt`)
- **Hero Graphical Logos:** Displays graphic logos in the Hero spotlight for Bangumi and Simkl items when available.
- **Cross-Source ID Segregation:** Bangumi subject IDs are never used as MAL IDs; Simkl entries retain valid TMDB IDs.
- **Character, Staff & Voice Actor Galleries:** Resolves people missing MAL IDs using strict name matching against Jikan.

### 🌟 8. Explore: Airing Soon Shelf & Ordered Categories (`ExploreScreen.kt`, `AllSourcesExplore.kt`, `ExploreCategories.kt`)
- **Repositioned Airing Soon Banner:** Relocated immediately below category shortcuts across all views.
- **Logical Shelf Ordering:** Standardized order: Anime shelves → Manga shelves → Source-specific shelves.
- **Category Shortcuts:** Added "Top Rated Anime" and "Top Rated Manga" shortcuts.

### 🗓️ 9. Universal Airing Dates & Countdowns (`NextAiringFormat.kt`, `DetailSharedComponents.kt`, `NextAiringChip.kt`)
- **Unified Formatting Layer:** Standardized air dates and countdowns across AniList, MAL, TMDB, Shikimori, Bangumi, Simkl, and Kitsu.
- **Detail Screens:** Unified label: *"Yaklaşan Yayın: Bölüm 2, 2026-10-15 tarihinde yayında (6 gün sonra)"*.

### 📋 10. User Profile List & "My List" Full Parity (`KitsugiUserMediaListScreen.kt`, `MyListFilterHelpers.kt`)
- **Full Header & Filter Parity:** Viewing another user's list now provides the complete header, view modes, search, and filtering capabilities of the main "My List" screen.
- **Combined Anime & Manga Pool:** Both media lists are loaded concurrently and filterable on the fly.

### 🔐 11. Hardened Account Backup, Multi-Device CAS & Keystore (`VaultBackupWorker.kt`, `VaultMerge.kt`, `LocalVaultKeyStore.kt`)
- **WorkManager Persistent Queue:** Protects pending backups against network drops, process death, and reboots.
- **3-Way Merge & CAS:** Automatic merging for distinct accounts and server-side compare-and-swap on Supabase.
- **Keystore Encryption & Interruption-Proof Password Change:** Hardware-backed encryption and dual-wrap transitions.
- **22 Portable Settings:** Encrypted alongside tokens.

### 🧩 12. Extension Explore Portal Home Parity & Studio Detail Polish (`AddonExploreDialog.kt`, `AddonFullScreenGridPage.kt`, `AddonHomeParity.kt`, `StudioDetailPage.kt`, `StudioDetailComponents.kt`, `StudioDetailViewModel.kt`)
- **Extension Explore Home Screen Parity:** Extension explore pages within the Addon Portal now match the primary Home Explore screen in aesthetics and interaction (`KitsugiHeroSection` featured hero carousel, auto-advancing slides, line-dot indicators, neon-framed `KitsugiExploreMediaCard`, snap-fling physics, and standard "See All" contracts).
- **"See All" Fullscreen Grid & List Parity:** The "See All" destination (`AddonFullScreenGridPage`) adopts the studio/explore grid architecture: emoji-annotated type filter chips (✨ All / 🎌 Anime / 🎥 Movie / 📺 Series), Filter & Sort bottom sheet (`KitsugiStudioFilterBottomSheet`), grid ↔ list view toggle (with persistent layout preference), item count indicator, sticky header on scroll, and scroll-to-top FAB.
- **CS3 Unified Model Bridge & Clean Addon Badges:** CloudStream 3 search results map to standard `JikanSearchResult` models; redundant fake `#id` labels are removed from addon cards, and fallback extension badges/pills (`Icons.Default.Extension`) prevent blank source pills in hero headers.
- **Studio Foundation Date Formatting:** Replaced raw ISO timestamps (`1998-10-01T00:00:00+00:00`) with localized date formatting (*"Founded: 1 October 1998"* via `KitsugiDateUtils`) across both hero and side panels.
- **Studio "About" Synopsis Card Full Parity:** The studio "About" section now matches the media detail `DetailSynopsisCard`: automatic Turkish translation (`autoTranslateEnabled` or Cyrillic auto-detection) backed by `DetailCache`, in-app translation toggle with 3rd-party translator fallback, one-tap copy to clipboard toast, and expandable "Read more / Read less" controls.

### 🎯 13. "Add to List" & "Already in List" Discrepancy & Search Title Matching (`KitsugiModels.kt`, `AppViewModel.kt`, `ApiResultDetailPage.kt`, `MyListFilterHelpers.kt`, `MyListLibraryGrouping.kt`, `MyListScreen.kt`)
- **Button and Action Parity:** The detail page "Add to List" button now checks the exact same identity/duplicate match (`findExistingEntry`) as the add action. If the media entry already exists in the library, the button seamlessly turns into **"✎ Edit"** and opens the existing entry directly upon click.
- **Descriptive Duplicate Notification:** The duplicate snackbar notification now references the existing library entry's title and its tab (*"\"Clannad: After Story\" already in your list (AniList tab)."*), eliminating confusion when names differ across providers.
- **Multi-Variant Title Matching:** `MediaEntry.matches` checks all title variants (canonical, English, Japanese, Romaji) using `MediaIdentity.normalizedTitle` to prevent mismatches caused by display language choices.
- **Robust "My List" Search:** Searching in "My List" scans English and Japanese alternative titles as well as punctuation- and spacing-insensitive normalized strings (e.g. queries for "clannad after story", "CLANNAD 〜AFTER STORY〜", and "クラナド" all find the target entry).
- **Resolved ID Double-Check Safety Net:** Early clicks before cross-platform ID resolution completes are captured by a second check using resolved IDs, preventing duplicate entries (verified by 6 unit tests).

### 🏢 14. Studio & Producer Provider Identity Isolation & Search Routing (`KitsugiStudioClient.kt`, `StudioSourceSupport.kt`, `JikanSearchClient.kt`, `SearchScreen.kt`, `SearchResultRow.kt`, `StudioDetailPage.kt`, `StudioDetailViewModel.kt`)
- **Provider-Scoped Studio ID Resolution:** Cross-provider studio ID collisions (such as Aniplex opening Bones due to mismatched MAL and AniList numeric ID namespaces) are completely resolved; each studio is queried using its origin provider namespace.
- **Name Verification & Fallback Search:** When Jikan returns a company name differing from the clicked label, an automatic name search verification is triggered, preventing incorrect company data from being shown.
- **Multi-Source Support:** Normalized studio endpoints across TMDB, Shikimori, and Bangumi (filtering strictly for company records with `type = 2` on Bangumi).
- **Direct Studio Search Routing:** Searching under the "Studio" scope routes directly to `StudioDetailPage` rather than media details, with "Add to List" buttons disabled for company entries.
- **Seamless Loading Transition:** Studio logo and name are carried across navigation into the loading screen to prevent blank screen flicker.

### 📱 15. Profile Favorites Bottom Navigation Bar Adaptive Alignment (`AniListProfileContent.kt`, `KitsugiProfileScreen.kt`, `AppRootTabPages.kt`)
- **Bottom Bar Adaptive Floating Controls:** The floating category filter button ("Anime" / `☰`) and scroll-to-top FAB on the Profile "Favorites" tab now adapt dynamically to bottom navigation bar visibility (`isBottomBarVisible` sets `96.dp + insets` when visible, animating to `16.dp / 20.dp + insets` when hidden or in landscape).
- **Eliminated Overlap & Click Blocking:** Resolves the visual collision where the floating button sat directly over the "Keşfet" tab, ensuring clean separation and sufficient list bottom padding (`Spacer(140.dp)`).

### 📐 16. Portrait Mode Horizontal Tab Peek Elimination (`DetailPageScaffold.kt`)
- **Clean Margins Without Leakage:** Removed `contentPadding = PaddingValues(horizontal = 16.dp)` on portrait `HorizontalPager` and applied 16.dp padding directly inside the page `Box`. Adjacent tabs no longer peek from the left/right screen edges.

### 🌐 17. Long Biography & Synopsis Progressive Translation (`TranslationManager.kt`, `CharacterDetailViewModel.kt`, `StaffDetailViewModel.kt`, `ApiResultDetailViewModel.kt`, `MediaEntryDetailViewModel.kt`)
- **4-Stage Text Chunking:** Texts exceeding 2000 characters are progressively split along paragraph (`\n\n`) → newline (`\n`) → sentence (`. ! ? … 。！？；`) → word boundaries, with punctuation-whitespace validation protecting numbers (e.g. `3.14`) and titles (e.g. `Mr.`).
- **Progressive Streaming UI:** Translations stream chunk-by-chunk to the UI ("translated head + original tail"), avoiding layout jumps and translating smoothly before the user's eyes.
- **Application-Level Retries & Error Visibility:** 3 attempts with exponential backoff on HTTP/translation failures; `null` return on failure avoids silent degradation, and only 100% complete translations are cached in Room and `DetailCache`.

### 🛡️ 18. Media Detail Social Matching Safety & Explore Backdrop Prioritization (`KitsugiMediaSocialClient.kt`, `DetailCache.kt`, `KitsugiHeroSection.kt`, `TmdbDiscoverClient.kt`, `ExploreViewModel.kt`)
- **Source & Type Isolated Social Mapping:** Forum topics, activities, and reviews no longer conflate third-party IDs (TMDB, Simkl) with MAL or AniList IDs; only verified cross-platform mappings are accepted.
- **Strict Activity Verification:** Social queries are disabled for non-anime/manga media. Activities strictly match the destination AniList ID and media type; unlinked text posts are discarded.
- **Review Cache Type Isolation:** `DetailCache.getMediaReviews` is strictly scoped by `source + ID + mediaType`, preventing cross-medium cache leakage.
- **Explore Hero Backdrop Prioritization:** For anime, TV, and movies, horizontal backdrop artwork is prioritized over posters even on portrait screens. TMDB titles query exact TMDB IDs, while other sources use type- and year-aware matching (Manga preserves aspect-ratio priority).

### 🖼️ 19. Gallery Fanart API Details, Persistent Notification Archive & Bangumi Notification Source (`FanartApiClient.kt`, `GalleryItem.kt`, `KitsugiImageGalleryDialog.kt`, `NotificationArchiveStore.kt`, `KitsugiNotificationsViewModel.kt`, `NotificationDiagnostics.kt`)
- **Fanart API Metadata in Gallery:** Displays `name` and `iMDb` fields from the Fanart.tv v3 API as "Name" and "IMDb ID" metadata. Integrated into the landscape right inspector panel and accessible via a new ℹ️ "Details" top-bar button on portrait screens.
- **Persistent Notification Archive (`NotificationArchiveStore`):** Airing and watchlist notifications generated for platforms without dedicated personal notification endpoints (MAL, Simkl, Kitsu, Bangumi) are stored persistently in `notifications_archive.json` (up to 300 items per provider) and backed up to Supabase (`user_data.notifications`). Notifications no longer vanish once they slide out of the 7-day airing window.
- **Bangumi Notification Provider:** Added Bangumi as the 6th notification source with custom icon, login status, and username pill. Queries the user's DOING (在看) collection, resolves cross-platform IDs against the airing schedule, and displays local entries with helpful notes when unauthenticated. Diagnostics panel upgraded to verify all 6 providers live.

### 🔞 20. Shikimori List Card +18 (Adult) Blur Fix & GraphQL Resolution (`ShikimoriAdultResolver.kt`, `ShikimoriImportManager.kt`, `ShikimoriAdultFlagMigration.kt`)
- **Root Cause Resolution:** Shikimori's `user_rates` endpoint serializes embedded anime and manga models without `rating` or `genres` fields, causing all imported entries to save with `isAdult = false` and bypassing the adult blur setting on library cards.
- **New `ShikimoriAdultResolver` (GraphQL):** Batches up to 50 items per query using `{ animes(ids: "...", limit: 50, censored: false) { id rating genres { name } } }` (`censored: false` lifts the hentai filter; manga resolves via genre tags). In-memory caching, rate-limit awareness, and automatic retries ensure fast, resilient resolution.
- **Database Backfill Migration (`ShikimoriAdultFlagMigration`):** Automatically migrates and flags existing library entries in the background upon opening "My List", immediately applying cover blur to adult media without requiring a manual re-sync.

### 📦 21. Packaging
- Built exclusively as **FOSS** release (`assembleFossRelease`) (`Kitsugi-Beta-v2.4.226-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.225)

### 🌸 1. Bangumi Başlık Dili & Latin Ad Zenginleştirmesi (`KitsugiBangumiClient.kt`, `BangumiLocalizedName.kt`)
- **Liste Kartlarında Latin Ad Desteği:** Bangumi liste ve arama uçları (`/v0/search`, `/v0/subjects`, sıralamalar vb.) infobox dönmediğinden Korece ve Çince dizi/anime başlıkları (ör. "김비서가 왜 그럴까", "눈물의 여왕") yerel dilde görünüyordu. Başlığı CJK olan kayıtlar için `withLatinBangumiNames` mekanizması devreye girerek arka planda (Semaphore 4 sınırlayıcı ile) subject detayını tek seferlik çeker, İngilizce/Romaji adları `BangumiTitleCache`'e yazar ve kart başlıklarını uygulama dil tercihine göre günceller.
- **Tüm Bangumi Keşfet & Arama Listeleri:** En İyi Animeler/Mangalar, Popüler Listeler, Sezonluk Keşif, Real/Dizi Şeritleri ve Normal Arama dahil 15 farklı liste çağrısına uygulandı.
- **Alternatif Adlar (Diğer İsimler) Filtresi:** Detay sayfalarındaki "Diğer Adlar" alanında Japonca, Çince ve Korece karakterli varyantlar gizlendi; kullanıcıya yalnızca temiz Latin harfli adlar sunulur.

### 👤 2. Karakter & Kişi Detay Sayfası İyileştirmeleri (`CharacterDetailPage.kt`, `StaffDetailPage.kt`)
- **Seçilen Dile Bağlı Japonca Alt Satır:** Karakter ve ekip detay sayfalarında adın altındaki Japonca yerel ad satırı, artık yalnızca kullanıcı uygulama dilini "Japonca/Yerel" seçtiğinde gösterilir. Diğer dillerde (İngilizce/Romaji) gereksiz tekrar engellendi.

### 📱 3. Profil & Favoriler UI Düzeltmeleri (`AniListProfileContent.kt`, `ProfileFavoritesListemStyle.kt`)
- **AniList Profil Kategori Butonu Hizalaması:** AniList profil favorilerindeki kategori filtre butonu (`bottomOffset = 96.dp`), kullanıcı profiliyle uyumlu olacak şekilde `20.dp`'ye indirilerek ekranın alt kenarıyla hizalandı.
- **Hızlı Yukarı Kaydırma Butonu:** Favoriler listesinde yukarı dön butonu, kullanıcının 3 öğe beklemesine gerek kalmadan ilk öğeden çıkıldığı anda (~240px kaydırmada) anında görünür hale getirildi.

### 📦 4. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.225-foss.apk`).

---

## 🇬🇧 English (v2.4.225)

### 🌸 1. Bangumi Title Localization & Latin Name Enrichment (`KitsugiBangumiClient.kt`, `BangumiLocalizedName.kt`)
- **Latin Name Resolution for List Cards:** Bangumi list and search endpoints do not return infoboxes, previously causing Korean/CJK titles (e.g. "김비서가 왜 그럴까", "눈물의 여왕") to render in native script. The new `withLatinBangumiNames` pipeline queries subject details for CJK titles (rate-limited via Semaphore 4), caches English/Romaji names in `BangumiTitleCache`, and updates list cards to match user language preferences.
- **Universal Coverage:** Covers Top Anime/Manga, Trending, Seasonal Browse, Real Drama/Movies, and Search lists (15 endpoints).
- **Clean Alternative Names:** Filtered non-Latin scripts from "Other Names" on detail pages; CJK variants are excluded from alternative aliases.

### 👤 2. Character & Staff Detail Localization (`CharacterDetailPage.kt`, `StaffDetailPage.kt`)
- **Language-Aware Native Subtitle:** The Japanese native name subtitle below character and staff headings is now only shown when the user has selected "Native / Japanese" as their display language.

### 📱 3. Profile & Favorites Layout Polish (`AniListProfileContent.kt`, `ProfileFavoritesListemStyle.kt`)
- **AniList Profile Category Button Position:** Adjusted `bottomOffset` from `96.dp` to `20.dp` on the AniList profile tab, matching the user profile layout.
- **Responsive Scroll-to-Top:** The floating scroll-to-top button now activates immediately after passing the first item (~240px) rather than waiting for item 4.

### 📦 4. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.225-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.224)

### 🔐 1. Kitsugi Hesap Senkronizasyonu & Güvenli Kasa (`LinkedAccountVault.kt`, `KitsugiAccountRepository.kt`, `KitsugiAccountContent.kt`)
- **Uçtan Uca Şifreli Bağlı Hesap Yedekleme:** AniList, MyAnimeList, Kitsu, Shikimori, Bangumi ve Simkl bağlantı token'ları (erişim ve yenileme anahtarları ile kullanıcı ID'leri) cihazdan çıkmadan önce istemci tarafında AES-256-GCM ile şifrelenir.
- **Sıfır Bilgi Güvenlik Modeli:** Supabase ve sunucu tarafı yalnızca şifreli veriyi (`CipherBlob`) görür; düz metin token'lar sunucuya asla iletilmez.
- **Şifreden Türetilen Kasa Anahtarı:** Rastgele 256-bit kasa anahtarı üretilir ve kullanıcının Kitsugi hesap şifresinden PBKDF2-SHA256 (210.000 iterasyon) ile türetilen anahtarla sarılarak saklanır (`vault_meta`). Kitsugi şifresi hiçbir yerde saklanmaz.
- **Tek Girişle Otomatik Geri Yükleme:** Yeni bir cihazda veya yeniden kurulumda Kitsugi hesabına giriş yapıldığında, bağlı tüm servis hesapları otomatik olarak çözülüp geri yüklenir; servislere tek tek tekrar giriş yapma ihtiyacı ortadan kalkar.
- **Otomatik Canlı Yedekleme:** Bağlı bir serviste token yenilendiğinde (`refresh_token` rotasyonu dahil), `LinkedAccountVault` değişikliği algılar ve 3 saniyelik güvenli gecikmeyle bulut yedeğini otomatik günceller.
- **Geçici/Hassas Alan İzolasyonu:** OAuth geçici PKCE doğrulayıcıları (`*_code_verifier`, `*_pending_redirect_uri`) ve hesap şifreleri yedekleme kapsamı dışında tutulur.

### 🔍 2. Bulut Arama Geçmişi & Kompakt Arama Çipleri (`SearchHistoryRepository.kt`, `SearchHistorySection.kt`)
- **Cihazlar Arası Geçmiş Senkronizasyonu:** Arama yapıldığında, silindiğinde veya temizlendiğinde arama geçmişi otomatik olarak Kitsugi hesabına aktarılır (`user_data` tablosu `search_history` anahtarı).
- **Zaman Damgalı İki Yönlü Birleştirme:** Giriş yapıldığında yerel ve bulut geçmişi zaman damgalarına göre akıllıca harmanlanır (`pullAndMergeSearchHistory`).
- **Kompakt Arama Geçmişi Tasarımı:** Arama ekranındaki geçmiş çipleri modernize edildi; tek harfli/gürültülü sorgular filtrelenir, ilk 8 çip kompakt blokta gösterilir ve fazla sorgular "+N / Daha az" butonuyla açılıp kapanabilir.

### ⚙️ 3. Ayarlar Menüsü Entegrasyonu (`SettingsScreen.kt`)
- **"Kitsugi Hesabı" Menü Sayfası:** Ayarlar > Hesap altına "Kitsugi Hesabı" alt sayfası eklendi.
- **Hesap Yönetimi:** E-posta ve şifre ile doğrudan kayıt olma, giriş yapma, anlık eşitleme ("Şimdi eşitle") ve güvenli çıkış yapma kontrolleri sağlandı.

### 🖼️ 4. Orijinal Formatında Medya İndirme & Paylaşma (`KitsugiImageDownloadHelper.kt`)
- **Magic Number Format Tespiti:** İndirilen görsellerin gerçek MIME ve formatı bayt başlıklarından (magic number) tespit edilir.
- **Hareketli GIF & Şeffaf PNG Koruması:** Animasyonlu GIF'lerin düz JPEG'e dönüştürülmesi önlendi; GIF'ler `.gif`, PNG'ler `.png`, WebP'ler `.webp` olarak orijinal kalitesinde kaydedilir ve paylaşılır.

### 📦 5. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.224-foss.apk`).

---

## 🇬🇧 English (v2.4.224)

### 🔐 1. Kitsugi Account Synchronization & Secure Vault (`LinkedAccountVault.kt`, `KitsugiAccountRepository.kt`, `KitsugiAccountContent.kt`)
- **End-to-End Encrypted Linked Account Backup:** Access/refresh tokens and user IDs for connected platforms (AniList, MyAnimeList, Kitsu, Shikimori, Bangumi, Simkl) are encrypted on-device via AES-256-GCM before syncing to the cloud.
- **Zero-Knowledge Security Architecture:** Supabase stores only ciphertext blobs; raw authentication tokens are never exposed to the server.
- **Key Wrapping with PBKDF2:** A random 256-bit vault key encrypts token data and is wrapped using a key derived from the user's Kitsugi account password via PBKDF2-SHA256 (210,000 iterations). Passwords are never stored.
- **Seamless Single-Sign-On Restoration:** Logging into a Kitsugi account on a new device automatically unwraps the vault and restores all connected service integrations without manual re-authentication.
- **Real-Time Auto-Backup:** When service tokens refresh, changes trigger an automatic debounced backup (3-second window) to keep cloud vaults up-to-date.
- **Transient Field Isolation:** Ephemeral OAuth data (`*_code_verifier`, `*_pending_redirect_uri`) and user passwords are strictly excluded from backups.

### 🔍 2. Cloud Search History & Compact History Chips (`SearchHistoryRepository.kt`, `SearchHistorySection.kt`)
- **Cross-Device Search Synchronization:** Search additions, deletions, and purges automatically sync with cloud storage under `search_history`.
- **Timestamped Bidirectional Merge:** Account sign-in performs a smart conflict-free merge of local and remote search entries (`pullAndMergeSearchHistory`).
- **Compact Search UI Redesign:** Replaced full-size chips with streamlined, responsive chips. Single-character queries are omitted, queries collapse to 8 items with "+N / Show less" expansion.

### ⚙️ 3. Settings Interface Integration (`SettingsScreen.kt`)
- **"Kitsugi Account" Section:** Added a dedicated "Kitsugi Account" settings destination under Settings > Account.
- **Account Actions:** Sign-up, sign-in, manual forced synchronization ("Sync now"), and secure sign-out.

### 🖼️ 4. True Format Media Preservation (`KitsugiImageDownloadHelper.kt`)
- **Magic Number File Type Detection:** Inspects binary headers to accurately detect image MIME types and extensions.
- **Animated GIF & PNG Preservation:** Prevents animated GIFs and transparent PNGs from being flattened into JPEGs; saves and shares media in its native format (.gif, .png, .webp).

### 📦 5. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.224-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.223)

### 👤 1. Profil Favorileri & Listem Görünüm Uyarlaması (`ProfileFavoritesListemStyle.kt`, `AniListFavoritesTab.kt`, `KitsugiUserMediaListScreen.kt`)
- **Listem Kart Düzenleri:** Kendi profilindeki ve diğer kullanıcıların profillerindeki favori kartları (Anime, Manga, Karakter, Kişi, Stüdyo) artık Listem ekranının kart düzenlerini (`compact`, `comfortable`, `minimalist`, `large`, `grid_2col`) ve kullanıcının Listem ayarlarını kullanır.
- **Otomatik Sayfalama (Sonsuz Kaydırma):** Eski "Daha Fazla Yükle" butonu kaldırıldı; sayfa aşağı kaydırıldıkça sonraki sayfalar akıcı bir şekilde otomatik yüklenir.
- **Dinamik Alt Kontroller:** Sol altta kategori seçici çipi (Anime, Manga, Karakterler, Ekip, Stüdyolar) ve sağ altta yukarı kaydırma butonu eklendi; aşağı kaydırırken kontroller otomatik gizlenir.
- **Diğer Kullanıcıların Medya Listesi:** Diğer kullanıcıların anime/manga listeleri Listem'in yerel kart ve durum başlığı bileşenlerine geçirildi; kart düzeni Listem tercihlerine bağlandı.
- **Stüdyo Görsel Yedekleri:** Görseli bulunmayan yapımcı/stüdyo kartlarında boş kutular yerine stüdyo adının baş harflerini içeren avatar rozetleri gösterilir.

### 🌊 2. Cloudstream Akış Boru Hattı & Kısmi Sonuç Kurtarma (`CsStreamRunner.kt`, `StreamViewModel.kt`)
- **Kısmi Sonuç Kurtarma (Partial Sink):** Eklentiler video linklerini bulmuş olsa bile işlem süresi zaman aşımına (timeout) uğradığında bulunan tüm verilerin silinmesi sorunu kökten çözüldü. Artık zaman aşımı gerçekleşse bile o ana kadar elde edilen kaynaklar (`partialSink`) kurtarılarak kullanıcıya sunulur.
- **Paralel HEAD Canlılık Doğrulaması:** Medya linklerinin canlılık kontrolü eski sıralı (8 sn × N) yapıdan kurtarılarak 6 eşzamanlı worker ve 5 sn zaman aşımıyla paralel hale getirildi. 5 ölü CDN'in 40 saniye boyunca tüm aramayı kilitleyip bütçeyi patlatması engellendi.
- **Zaman Kutulu & Paralel Varyant Araması:** 18 arama varyantı tek tek taranmak yerine 3'lü gruplar halinde paralel taranır. Katı zaman kutusu (normalde 20 sn, Cloudflare korumalı sitelerde 40 sn) uygulanarak bütçenin kalan kısmı `load` ve `loadLinks` (video çıkarma) aşamalarına saklanır.
- **Üst Bütçe Uyarlaması:** Tek sağlayıcı zaman aşımı 40 sn'den 75 sn'ye, Cloudflare korumalı eklentiler için 90 sn'den 120 sn'ye çıkarıldı. Eklentiler paralel çalıştığı için kullanıcı fazladan bekletilmez.
- **Doğrulama Sonrası Gerçek Temizleme:** `CsPluginStatusTracker` kayıtları `api.name` ile tutulduğundan, Cloudflare WebView doğrulamasından sonra sadece `plugin.id` değil; eklenti id'si, eklenti adı ve yüklenen tüm alt API adları temizlenerek eklentinin tekrar denemede bloklu kalması önlendi.
- **Şeffaf Durum Bildirimi:** Arama başarısızlıklarında jenerik "akış bulunamadı" yerine gerçek nedenler kartlara yansıtılır (ör. *"Zaman aşımı — 3 kaynak kurtarıldı"*, *"S1E1 bölümü bulunamadı"*, *"Arama sonuç vermedi (site erişilemiyor veya CF korumalı)"*).

### 🖼️ 3. Tüm Kaynaklardan Birleşik Karakter & Kişi Galerisi (`KitsugiPersonImageAggregator.kt`, `CharacterDetailViewModel.kt`, `StaffDetailViewModel.kt`)
- **Evrensel Galeri Toplayıcı:** Karakter ve seslendirmen/personel detay sayfalarındaki galeri tek kaynak sınırından kurtarıldı.
- **Katkı Sağlayan Kaynaklar:** MAL (Jikan `/pictures` çoklu görsel), AniList, Shikimori, Kitsu (karakterler), Bangumi ve TMDB (kişiler — 12'ye kadar profil fotoğrafı). Her görsel kendi kaynak rozetiyle galeri modalında gösterilir.
- **Sıkı Kimlik & İsim Doğrulaması:** İsim bazlı çapraz aramalarda katı eşleşme (normalize eşitlik veya Doğu/Batı ad sırası jeton-kümesi) aranır; yanlış kişi/karakter görseli riski engellendi.
- **Paralel & Dayanıklı:** Tüm kaynaklar eşzamanlı ~9 sn bütçeyle sorgulanır; süre dolarsa kısmi sonuçlar gösterilir.

### 📅 4. "Yakında Yayında" Her Yerde & TMDB Haftalık Takvim Düzeltmesi (`KitsugiAiringCalendarClient.kt`, `ExploreViewModel.kt`, `AllSourcesExploreContent.kt`)
- **TMDB Günlük Yayın Düzeltmesi:** Uzun soluklu dizilerin ilk yayın yıllarının gününe düşmesi hatası giderildi; `discover/tv?air_date` filtresi haftanın her günü için ayrı sorgulanarak yapımlar gerçek yayın günlerine yerleştirildi. Bilinmeyen bölüm numaraları için "Dizi" etiketi gösterilir.
- **Ortak Geri Sayımlı Şerit (Tümü Keşfet Sayfası):** Keşfet ana sayfasında ("Tümü" modu) tüm kaynakların en üstünde tek ortak "Yakında Yayında" geri sayımlı şerit gösterilir.
- **Tüm Platformlarda Kesintisiz Geri Sayım:** Kitsu, Shikimori, Bangumi ve Simkl sekmelerine de gerçek bölüm saatli takvim verisi entegre edildi. Şerit oku doğrudan haftalık yayın takvimini açar.

### 📦 5. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.223-foss.apk`).

---

## 🇬🇧 English (v2.4.223)

### 👤 1. Profile Favorites & "My List" Layout Parity (`ProfileFavoritesListemStyle.kt`, `AniListFavoritesTab.kt`, `KitsugiUserMediaListScreen.kt`)
- **"My List" Card Layouts:** Profile favorites (Anime, Manga, Characters, Staff, Studios) for both personal and public user profiles now mirror "My List" layout styles (`compact`, `comfortable`, `minimalist`, `large`, `grid_2col`) driven by the user's persistent display preferences.
- **Infinite Scrolling:** Removed legacy "Load More" pagination buttons in favor of smooth automatic infinite scroll.
- **Dynamic Floating Controls:** Added bottom floating category selector (Anime, Manga, Characters, Staff, Studios) and quick scroll-to-top button with scroll-aware auto-hiding.
- **Public User Media Lists:** Third-party user anime/manga lists adopted "My List" native card layouts and section headers.
- **Studio Avatar Fallbacks:** Studios lacking banner artwork display stylized monogram initials rather than blank tiles.

### 🌊 2. Cloudstream Stream Pipeline & Partial Result Rescue (`CsStreamRunner.kt`, `StreamViewModel.kt`)
- **Partial Result Rescue (Partial Sink):** Fixed a critical pipeline issue where found media streams were discarded if provider execution exceeded timeout. When a timeout occurs, any streams extracted up to that point (`partialSink`) are preserved and delivered to the player/user.
- **Parallel HEAD Liveness Verification:** Replaced sequential HTTP HEAD checks (8s × N) with a 6-worker parallel pool (5s timeout). Prevents dead CDNs from burning the provider budget and triggering false negatives.
- **Time-Boxed Parallel Search:** Query variants are now dispatched concurrently in batches of 3 within a strict time-box (20s standard, 40s CF). Early pruning preserves remaining execution budget for page loading and stream extraction.
- **Extended Provider Budget:** Adjusted provider execution budget to 75s (standard) and 120s (Cloudflare-protected). As providers execute concurrently, overall user latency is unaffected.
- **Complete Verification Reset:** Properly evicts `api.name`, `plugin.name`, and `plugin.id` from `CsPluginStatusTracker` following successful Cloudflare WebView verification, preventing false "blocked" state loops on retries.
- **Diagnostic UI Badging:** Cards now articulate the exact underlying cause instead of generic "no streams found" (e.g., *"Timeout — 3 streams rescued"*, *"Episode S1E1 not found"*, *"Search returned 0 results (unreachable or CF/WAF)"*).

### 🖼️ 3. Multi-Source Aggregated Character & Person Gallery (`KitsugiPersonImageAggregator.kt`, `CharacterDetailViewModel.kt`, `StaffDetailViewModel.kt`)
- **Universal Image Aggregator:** Character and staff/voice actor gallery drawers aggregate photos across all connected databases.
- **Integrated Sources:** MAL (Jikan `/pictures`), AniList, Shikimori, Kitsu (characters), Bangumi, and TMDB (persons — up to 12 profile stills). Every image features its respective platform badge.
- **Strict Identity Matching:** Strict normalization and Japanese naming order token-set matching prevent cross-entity image misattribution.
- **Concurrent & Resilient:** All sources query concurrently with a ~9s budget; partial results are gracefully rendered upon deadline.

### 📅 4. "Airing Soon" Everywhere & TMDB Weekly Schedule Fix (`KitsugiAiringCalendarClient.kt`, `ExploreViewModel.kt`, `AllSourcesExploreContent.kt`)
- **TMDB Per-Day Schedule Fix:** Fixed long-running series falling onto old historical premiere dates by querying `discover/tv?air_date` day-by-day for the current week. Unknown episode numbers clearly display "TV" badge.
- **Unified Countdown Shelf on All Explore:** A shared, authoritative "Airing Soon" countdown shelf is rendered directly above sources on the "All" explore tab.
- **Cross-Platform Parity:** Kitsu, Shikimori, Bangumi, and Simkl now feature genuine countdown airing data. Shelf header navigates directly to the weekly calendar view.

### 📦 5. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.223-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.222)

### 🌟 1. Evrensel Sosyal Birleştirme & Platform Rozetleri (`KitsugiMediaSocialClient.kt`, `ReviewsTab.kt`, `KitsugiReviewCard.kt`)
- **Evrensel İncelemeler & Konular:** Hangi kaynakta olunursa olunsun (AniList, MAL, TMDB, SIMKL, Kitsu, Shikimori, Bangumi), çapraz kimlikler çözülerek (ARM / KitsugiIdResolver / Bangumi cross-id) ilgili tüm platformlardaki incelemeler, forum konuları ve aktiviteler tek havuzda birleştirilir.
- **Platform Rozetleri:** Yorum kartları, forum konuları ve aktivitelerde kullanıcı adının yanında platform logosu (`KitsugiPlatformLogo`) gösterilir.
- **Akıllı Beğeni & Profil Yönlendirmesi:** AniList kullanıcıları doğrudan uygulama içi profile yönlendirilir; diğer platform kullanıcıları dış web profillerine açılır. Beğeni desteği AniList kaynaklı gönderiler için sorunsuz çalışır.
- **Çok Dilli İncelemeler:** TMDB incelemelerindeki dil kilidi (`language=tr-TR`) kaldırıldı; tüm dillerdeki incelemeler orijinal dil rozetiyle (TR, EN, JA, DE...) gelir, sayfalama ve çeviri butonu entegre çalışır.

### 🎬 2. Stüdyo & Yapımcı Sayfalarında Keşfet Paritesi (`StudioDetailPage.kt`, `StudioDetailComponents.kt`)
- **Keşfet Tasarım Dili:** Stüdyo detay sayfaları ana keşfet açılır sayfalarıyla birebir görsel ve işlevsel pariteye kavuşturuldu.
- **Grid ↔ Liste Modu & Kalıcı Tercih:** Keşfet poster kartları ile degrade şeritli ızgara modu ve detaylı liste modu arasında geçiş desteği.
- **Duyarlı Kolon Düzeni:** Dikey ve yatay ekran modlarında genişliğe göre akıllı 3–5 kolon hesabı.
- **Gelişmiş Filtreleme:** Üst çip şeridi (Tümü / Anime / Manga / Film / Dizi) ve "Filtre ve Sıralama" bottom sheet penceresi.

### 🎭 3. TMDB Kişi Adlarında Romaji & "Yönetmenlik" Çevirisi (`TmdbCreditsClient.kt`, `strings.xml`)
- **Romaji Önceliği:** TMDB'den Japonca (CJK) gelen yönetmen, oyuncu ve ekip adları için `also_known_as` listesinden Latin/romaji isimler otomatik seçilir (`romanizedName`).
- **Sınırlı Bellek Önbelleği:** Kişi sorguları `BoundedCache` (LRU 500) ile tutulur, yanıt başına en fazla 12 sorgu yapılarak ağ yükü korunur.
- **Rol Çevirisi:** "Directing" görevi için `staff_role_directing` ("Yönetmenlik") çevirisi eklendi.

### 🧩 4. Kitsu Karakter Detayı & MAL İlişki Kapak İyileştirmeleri (`KitsugiCharacterClient.kt`, `KitsugiMediaRelationsClient.kt`)
- **Kitsu Karakter Kurtarma:** Kitsu API'sinden 404/429 dönen veya eşleşmesi eksik yeni yapımdaki karakterlerde kart görseli, AniList isim araması ve Jikan yedekleri devreye girerek "Detaylar yüklenemedi" hatası engellendi.
- **MAL İlişki Kapakları:** Jikan ilişkilerindeki eksik kapak görselleri AniList üzerinden toplu sorguyla çekilerek gri placeholder'lar kaldırıldı ve afişler eklendi.

### 🏮 5. Bangumi Etiket Sözlüğü, Başlık Önbelleği & Tıklanabilir Stüdyolar (`BangumiTagDictionary.kt`, `BangumiTitleCache.kt`, `KitsugiBangumiDetailClient.kt`)
- **Kapsamlı Etiket Sözlüğü:** 523 grup ve 2182 yazım içeren otoriter sözlük; takvim etiketleri regex ile çözülür, arayüz diline göre gösterilir.
- **Kalıcı Latin Başlık Önbelleği:** Keşfet şeritleri ve ilişkilerdeki CJK başlıklar için kalıcı Latin önbellek (`BangumiTitleCache`, LRU 1500).
- **Tıklanabilir Kurumlar & Kanonik Adlar:** Bangumi yapımcı/stüdyoları kişi arama API'siyle eşlenip tıklanabilir hale getirildi; tanınan stüdyolar için 117 kanonik Latin yazım eşlemesi uygulandı.

### 🛡️ 6. Bellek Sızıntısı & Şişme Koruması (`BoundedCache.kt`, `KitsugiMemoryGuard.kt`)
- **LRU Sınırlı Önbellekler:** 18 dosyadaki 30'dan fazla sınırsız harita LRU sınırlı `BoundedCache`'e geçirildi.
- **Bellek Bekçisi:** Android'in `onTrimMemory`/`onLowMemory` geri çağrıları dinlenir; 15 saniyelik bekçi %80 heap doluluğunda %50, %90 doluluğunda %100 tahliye uygular.
- **Negatif Fragman Önbelleği & Çökme Raporu:** Boş fragman sonuçlarının önbelleklenmesi düzeltildi; çökme raporlarına önbellek doluluk metrikleri eklendi.

### ⚡ 7. Derleme Performansı & Gradle İyileştirmeleri (`gradle.properties`)
- **Kotlin Incremental Build:** `kotlin.incremental=true` ve `kotlin.incremental.useClasspathSnapshot=true` aktif edildi.
- **Paralel Görevler:** `kotlin.parallel.tasks.in.project=true` ve `android.r8.optimizedResourceShrinking=true` eklendi.

### 📦 8. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.222-foss.apk`).

---

## 🇬🇧 English (v2.4.222)

### 🌟 1. Universal Social Merge & Platform Badges (`KitsugiMediaSocialClient.kt`, `ReviewsTab.kt`, `KitsugiReviewCard.kt`)
- **Cross-Platform Reviews & Topics:** Regardless of current media source (AniList, MAL, TMDB, SIMKL, Kitsu, Shikimori, Bangumi), cross-ID resolution (ARM / KitsugiIdResolver / Bangumi cross-id) aggregates reviews, discussion topics, and activities from all connected databases into a unified feed.
- **Platform Badges:** Display platform logos (`KitsugiPlatformLogo`) beside author usernames in review cards, discussion topics, and activity feeds.
- **Intelligent Likes & Profile Navigation:** In-app navigation for AniList profiles; external browser intent for third-party platforms. Native liking enabled for AniList-sourced items.
- **Multilingual Reviews:** Unlocked TMDB review language restrictions; reviews in all languages load with original language badges (TR, EN, JA, DE...) and integrated pagination/translation.

### 🎬 2. Studio & Producer Explore Page Parity (`StudioDetailPage.kt`, `StudioDetailComponents.kt`)
- **Explore Design Parity:** Studio detail screens upgraded to mirror explore category sheets with consistent typography and padding.
- **Grid ↔ List View Persistence:** Toggle between poster gradient cards and detailed list rows with persistent user preferences.
- **Responsive Layout:** Dynamic 3-5 column layout calculation for portrait and landscape orientations.
- **Filters & Sorting:** Top chip filter row (All / Anime / Manga / Movie / TV) plus comprehensive sorting bottom sheet.

### 🎭 3. TMDB Romaji Person Names & Directing Translation (`TmdbCreditsClient.kt`, `strings.xml`)
- **Romaji Priority:** Automatic Latin/romaji name selection from `also_known_as` for CJK cast and crew in TMDB (`romanizedName`).
- **Memory-Bounded Cache:** Cached in `BoundedCache` (LRU 500), limited to 12 lookups per credits response.
- **Directing Role:** Added `staff_role_directing` translation ("Yönetmenlik").

### 🧩 4. Kitsu Character Detail & MAL Relations Covers (`KitsugiCharacterClient.kt`, `KitsugiMediaRelationsClient.kt`)
- **Kitsu Character Resiliency:** Fallback to card artwork, AniList name search, and Jikan search on Kitsu 404/429 errors.
- **MAL Relations Cover Art:** Bulk AniList lookup for Jikan relation items to replace grey placeholders with posters.

### 🏮 5. Bangumi Tag Dictionary & Title Cache (`BangumiTagDictionary.kt`, `BangumiTitleCache.kt`)
- **Tag Dictionary:** Authoritative dictionary of 523 groups / 2182 variants with calendar regex translation.
- **Persistent Latin Title Cache:** High-speed LRU-1500 caching for titles across explore and relations.
- **Interactive Studios:** Resolution of Bangumi company entities into clickable studio profiles with 117 canonical name mappings.

### 🛡️ 6. Memory Guard & Leak Protections (`BoundedCache.kt`, `KitsugiMemoryGuard.kt`)
- **Bounded LRU Caches:** Replaced 30+ unbounded maps across 18 files with `BoundedCache`.
- **System Memory Hooks:** Active hooks into `onTrimMemory`/`onLowMemory` with 15s heap watchdog.

### ⚡ 7. Build Performance & Gradle Tuning (`gradle.properties`)
- **Kotlin Incremental Build:** Enabled `kotlin.incremental=true` and `kotlin.incremental.useClasspathSnapshot=true`.
- **Parallel Tasks:** Added `kotlin.parallel.tasks.in.project=true` and `android.r8.optimizedResourceShrinking=true`.

### 📦 8. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.223-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.221)

### 🌐 1. Çok Dilli İncelemeler & Orijinal Dil Rozetleri (`TmdbCreditsClient.kt`, `KitsugiReviewCard.kt`)
- **Tüm Dillerde İncelemeler:** TMDB inceleme isteklerindeki sabit `language=tr-TR` filtresi kaldırıldı. İncelemeler hangi dilde yazıldıysa o dilde eksiksiz listelenir.
- **TMDB İnceleme Sayfalaması:** "Tümünü Gör" ekranında TMDB incelemeleri için sayfalama desteği (`page`) eklendi.
- **Orijinal Dil Rozeti:** İnceleme kartlarına orijinal dil kodu rozeti (EN, JA, DE vb.) eklendi ve çeviri motoruna kaynak dil olarak aktarıldı.

### 💬 2. Evrensel Tartışma Konuları & Aktiviteler (`KitsugiMediaSocialClient.kt`, `ReviewsTab.kt`)
- **TMDB & SIMKL Kısıtlaması Kaldırıldı:** Kaynak fark etmeksizin (TMDB, SIMKL, Kitsu, Shikimori, Bangumi, MAL) gerçek MAL/AniList kimlik eşlemesi çözülebildiğinde forum konuları ve aktiviteler dinamik olarak yüklenir; çözülemezse bölüm gizlenir ("varsa görünür" kuralı).

### 🌍 3. Akıllı Çok Dilli Çeviri Motoru (`KitsugiTranslateUtils.kt`, `SettingsDataStore.kt`)
- **Dinamik Çeviri:** Sabit `en -> tr` kuralı kaldırıldı; kaynak dil = otomatik algılama veya incelemenin dili, hedef dil = kullanıcının seçtiği hedef dil veya uygulamanın aktif dili.
- **Evrensel Web Fallback:** Google Translate uygulaması kurulu olmadığında tüm dilleri destekleyen `translate.google.com/?sl=auto&tl=...` web arayüzü açılır.
- **130+ Dil Desteği:** Ayarlar ekranındaki dil listeleri Google Translate'in 130+ diline genişletildi (kaynakta "Otomatik (Algıla)" seçeneği dahil).

### 📚 4. Keşfet: Yeni Yerel Ek Tür Rafları (`NativeKindExploreSections.kt`, `ExploreViewModel.kt`)
- **MAL:** "Manhwa & Manhua" ve "Noveller & Light Novel" rafları (Jikan filtreli).
- **Shikimori:** "Manhwa & Manhua" (`kinds=manhwa,manhua`) ve "Noveller & Light Novel" (`kinds=light_novel,novel`) rafları.
- **AniList:** "Noveller & Light Novel" (`format: NOVEL`) rafı.
- Desteklemeyen kaynaklarda (Kitsu) raflar temiz bir şekilde gizlenir. Hem "Tümü" hem tek kaynak modunda "Tümünü Gör" sayfalaması ve vitrin puanlaması entegre edildi.

### 🎨 5. Stüdyo & Yapımcı Sayfası Keşfet Paritesi (`StudioDetailPage.kt`, `StudioDetailComponents.kt`)
- **Keşfet Sayfası Uyumlu Düzen:** Keşfet açılır sayfalarıyla ("Shikimori · En İyi Animeler") birebir aynı kolon hesabı, dikey/yatay mod duyarlılığı ve akıcı sayfa yapısı.
- **Gelişmiş Filtreleme & Görünüm:** Emojili tür filtre çipleri (Tümü, Anime, Manga, Film, Dizi), Filtre ve Sıralama bottom sheet'i, içerik sayacı, yukarı kaydırma FAB'ı, kalıcı Grid ↔ Liste geçişi ve stüdyo açıklaması ("Hakkında") desteği.

### 🏷️ 6. Ortak Kaynak Rozetleri & Logo Gösterimi (`KitsugiSourceBadge.kt`, `DetailSharedComponents.kt`)
- **Logo + Doğru İsim Rozeti:** Vitrin (hero), detay açılışındaki yükleme ekranı ve detay sayfası başlığında ham metinler yerine logo + okunabilir isim gösteren `KitsugiSourceNamePill` ve `DetailSourcePill` entegrasyonu (AniList, MAL, TMDB, Kitsu, Shikimori, Simkl, Bangumi).

### 🧪 7. Birim Testleri
- `AllSourcesExploreTest.kt` ile yerel ek tür raflarının filtreleme ve görünürlük mantığı doğrulandı.

### 📦 8. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.221-foss.apk`).

---

## 🇬🇧 English (v2.4.221)

### 🌐 1. Multilingual Reviews & Original Language Badges (`TmdbCreditsClient.kt`, `KitsugiReviewCard.kt`)
- **Unrestricted Reviews:** Removed language lock (`language=tr-TR`) from TMDB review requests so reviews in all languages load intact.
- **TMDB Review Pagination:** Added pagination (`page`) support for TMDB reviews in the "See all" sheet.
- **Language Badge:** Display original language tag (EN, JA, DE, etc.) on review cards, passed as source language to translators.

### 💬 2. Universal Forum Topics & Activities (`KitsugiMediaSocialClient.kt`, `ReviewsTab.kt`)
- **Broadened Social Tabs:** Lifted TMDB/SIMKL restrictions; whenever MAL/AniList cross-mappings resolve, discussions and activities appear seamlessly across all sources.

### 🌍 3. Smart Multilingual Translation Engine (`KitsugiTranslateUtils.kt`, `SettingsDataStore.kt`)
- **Dynamic Language Pairs:** Replaced fixed `en -> tr` with dynamic auto-detection / original review language to user-selected target (or active app locale).
- **Web Fallback:** Added graceful fallback to `translate.google.com/?sl=auto&tl=...` when Google Translate app is absent.
- **130+ Languages:** Expanded language settings catalog to over 130 Google Translate locales.

### 📚 4. Explore: Native Media Subcategory Shelves (`NativeKindExploreSections.kt`, `ExploreViewModel.kt`)
- **MAL:** "Manhwa & Manhua" and "Novels & Light Novels" shelves.
- **Shikimori:** "Manhwa & Manhua" and "Novels & Light Novels" shelves.
- **AniList:** "Novels & Light Novels" shelf.
- Automatically hidden on unsupported catalogs (Kitsu). Full support for "See all" pagination and hero scoring.

### 🎨 5. Studio Detail Page Explore Parity (`StudioDetailPage.kt`, `StudioDetailComponents.kt`)
- **Unified Explore Layout:** Matches explore category grid responsive columns, portrait/landscape continuity, and layout flow.
- **Filtering & Views:** Added emoji chips (All, Anime, Manga, Movies, Series), Filter/Sort sheet, count indicator, scroll-to-top FAB, persistent Grid ↔ List toggle, and studio "About" section.

### 🏷️ 6. Unified Source Badges & Logos (`KitsugiSourceBadge.kt`, `DetailSharedComponents.kt`)
- **Logo + Friendly Name:** Hero showcase, loading screens, and detail headers now render unified `KitsugiSourceNamePill` and `DetailSourcePill` with crisp logos and standardized labels.

### 🧪 7. Unit Tests
- Verified with `AllSourcesExploreTest.kt`.

### 📦 8. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.221-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.220)

### 🧹 1. Kitsu Bozuk Özet (Synopsis) Tespiti ve Temizliği (`KitsuSynopsisValidator.kt`, `KitsugiDetailClient.kt`)
- **Akıllı Bozuk Özet Tespiti:** Kitsu veritabanında vandalize edilmiş veya hatalı birleştirilmiş kayıtlar (örneğin 2004 yapımı anime olan "ROAR" kaydında 1997 yapımı Fox TV dizisi "Roar"ın özetinin bulunması) tespit edilir:
  - Anime veya film türündeki kayıtlarda Amerikan/İngiliz canlı-aksiyon TV dizisi tarif eden ifadeler (`is an American television show`, `aired on the Fox network` vb.) şüpheli olarak yakalanır.
  - Özet yayın tarihi bağlamında ("aired/premiered/released in 19XX") kayıt yılından 2 yıldan fazla sapan tarihler şüpheli kabul edilir.
  - Canlı-aksiyon diziler (`TvShow`), Kore dizileri ve hikaye içi geçen geçmiş yıllar yanlış-pozitif üretmeyecek şekilde korunur.
- **Doğrulanmış Kaynak İle Değiştirme:** Şüpheli Kitsu özetleri MAL/Jikan üzerinden doğrulanmış temiz özetle (`CrossSyncIdentityGuard` sıkı başlık kontrolüyle) otomatik değiştirilir; doğrulanamazsa özet boşaltılır ("Açıklama bulunamadı" gösterilir, alakasız özet engellenir).
- **Önbellek Sürümleme & Çeviri Temizliği:** Kitsu detay önbellek anahtarı `_ks1` olarak sürümlendi; eski bozuk önbellekler geçersiz kılındı. Bozuk özetlerin çevirileri `DetailCache.removeTranslation` ile bellekten temizlenir.
- **Yedek Zinciri Koruması:** AniList/MAL → Kitsu arama zinciri ve `fetchSynopsis` fonksiyonuna (listeye ekleme penceresi, hero önizleme) Kitsu özet doğrulama mantığı entegre edildi.

### 🌐 2. MyAnimeList & Jikan Çoklu Host Zinciri (`JikanGateway.kt`, `JikanSearchClient.kt`)
- **Yeni Kaynak Sıralaması:** Jikan genel API kesintileri ve kapanışına karşı arama ve keşfet zinciri optimize edildi:
  1. Resmi MAL v2 (`api.myanimelist.net`)
  2. miribyou (`https://miribyou-topaz.vercel.app` / `MIRIBYOU_BASE_URL`)
  3. Tenrai (`https://api.tenrai.org/v1`)
  4. AniList (son çare yedek)
- **Çoklu Host Desteği & Devre Kesici:** `JikanGateway` üzerinden host bazında bağımsız hız sınırlaması, 429 soğuması ve 3 ardışık hatada devre kesici (circuit breaker) eklendi.
- **Tenrai Uyumluluğu:** Tenrai API sorgu parametreleri (`sfw=true` → `sfw`) otomatik dönüştürülür.
- **İstek Tekilleştirme & Özel İstemci:** `KitsugiHttpClient.jikanClient` ile harici `RetryInterceptor` devre dışı bırakılarak gereksiz kota tüketimi ve 429 tetiklemeleri engellendi.
- **Detay ve Görsel Entegrasyonu:** `KitsugiMalDetailClient`, `CharacterDetailViewModel` ve `StaffDetailViewModel` `/pictures` istekleri merkezi `JikanGateway` üzerinden yönlendirildi.

### 🧪 3. Birim Testleri
- `KitsuSynopsisValidatorTest.kt` ile 15 birim testi tamamlandı ve başarıyla doğrulandı.

### 📦 4. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.220-foss.apk`).

---

## 🇬🇧 English (v2.4.220)

### 🧹 1. Kitsu Corrupted Synopsis Detection & Sanitization (`KitsuSynopsisValidator.kt`, `KitsugiDetailClient.kt`)
- **Smart Anomaly Detection:** Identifies vandalized or incorrectly merged Kitsu catalog entries (e.g., 2004 anime "ROAR" containing synopsis of the 1997 Fox TV live-action show "Roar"):
  - Flags western live-action TV show phrasing (`is an American television show`, `aired on the Fox network`, etc.) for Anime and Movie media types.
  - Flags broadcasting year discrepancies exceeding 2 years in release verbs (`aired/premiered/released in 19XX`).
  - Safe against false-positives for legitimate `TvShow` entries, K-dramas, and in-universe fictional dates.
- **Verified Replacement Fallback:** Suspicious synopses are replaced by verified summaries fetched from MAL/Jikan guarded by `CrossSyncIdentityGuard.titlesLookRelated`; unverified synopses are safely cleared to avoid misleading descriptions.
- **Cache Versioning & Translation Eviction:** Kitsu detail cache keyed with `_ks1` to bypass contaminated cache entries. Stale synopsis translations are evicted via `DetailCache.removeTranslation`.
- **Fallback Chain Protection:** Integrated into the AniList/MAL → Kitsu fallback chain (15s timeout) and `fetchSynopsis` (add-to-list dialog, hero preview).

### 🌐 2. MyAnimeList & Jikan Multi-Host Chain (`JikanGateway.kt`, `JikanSearchClient.kt`)
- **Optimized Fallback Hierarchy:** Resilient routing against public Jikan downtime:
  1. Official MAL v2 (`api.myanimelist.net`)
  2. miribyou (`https://miribyou-topaz.vercel.app` / `MIRIBYOU_BASE_URL`)
  3. Tenrai (`https://api.tenrai.org/v1`)
  4. AniList (ultimate fallback)
- **Multi-Host Gateway & Circuit Breakers:** `JikanGateway` now coordinates host-level rate limiting, 429 cool-down, and circuit breakers (tripping on 3 consecutive server/network failures).
- **Tenrai Compatibility:** Seamless parameter translation (`sfw=true` → `sfw`).
- **Quota Leak Fix:** Introduced `KitsugiHttpClient.jikanClient` without `RetryInterceptor` to prevent quota burning outside the gateway.
- **Detail & Asset Gateway Routing:** `KitsugiMalDetailClient`, `CharacterDetailViewModel`, and `StaffDetailViewModel` `/pictures` requests now traverse `JikanGateway`.

### 🧪 3. Unit Tests
- 15 unit tests in `KitsuSynopsisValidatorTest.kt` passed successfully.

### 📦 4. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.220-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.219)

### 🔑 1. Harici API Entegrasyonları Doğrulama & Yönetim (`KitsugiIntegrationsSettingsDialog.kt`)
- **Canlı Anahtar Doğrulama:** TMDB ve Fanart.tv kişisel API anahtarları için anlık servis bağlantı testi eklendi; yalnızca doğrulanmış anahtarlar kaydedilir.
- **Güvenli Temizleme:** Alan temizlendiğinde uygulamanın yerleşik ortak anahtarına geri dönüş sağlanır.
- **MDBList Anahtar Desteği:** Giriş sırasında boşluk kırpma ve doğrulama eklendi.
- **Şifre / Maske Görünürlük Kontrolü:** API anahtarlarını gizleme ve gösterme kontrolleri entegre edildi.

### 📊 2. MDBList TMDB ID Yedek Araması & Akıllı Ayrıştırma (`MdbListClient.kt`)
- **Otomatik TMDB Yedeği:** IMDb kimliği bulunmayan içeriklerde pozitif TMDB ID ile otomatik puan sorgulama desteği eklendi.
- **Genişletilmiş Yanıt Formatı:** Hem yeni dizi hem de eski üst-seviye alan formatları eksiksiz ayrıştırılır ve önbelleğe alınır.
- `ApiResultDetailViewModel` ve `MediaEntryDetailViewModel` üzerinde IMDb/TMDB puan çağrıları bu yedek mekanizmasına bağlandı.

### 🎬 3. TMDB Özellik Kapıları & Televizyon Ağları (`TmdbMediaDetailClient.kt`)
- **Granüler İstek Kapıları:** Ayrıntı, afiş/arka plan, fragman, yapım şirketi, ağlar, oyuncu/ekip ve koleksiyon ayar kapıları API isteklerine ve model alanlarına bağlandı.
- **Yayın Ağları (Networks):** TV içeriklerinde yapımcı televizyon kanalları ve yayın ağları detay kartlarına eklendi.
- **Dinamik Keşfet Senkronizasyonu:** Keşfet sayfasındaki modern TMDB içerik rafları ayar değişikliğinde anında güncellenir (`tmdbModernHomeEnabled`).

### 🛡️ 4. Hassas URL Maskeleme & İstek Güvenliği (`SensitiveUrlRedactor.kt`, `KitsugiApiBase.kt`)
- **Günlük Güvenliği:** Ağ isteklerinde ve hata kayıtlarında API anahtarları, token'lar ve hassas parametreler otomatik olarak maskelenir (`***`).

### 🧪 5. Test Kapsamı
- `MdbListClientTest.kt` ve `TmdbMediaDetailClientTest.kt` ile MDBList URL/ayrıştırma ve TMDB özellik filtreleme mantığı için kapsamlı birim testleri eklendi.

### 📦 6. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.219-foss.apk`).

---

## 🇬🇧 English (v2.4.219)

### 🔑 1. External API Integration Validation & Management (`KitsugiIntegrationsSettingsDialog.kt`)
- **Live Key Validation:** Added in-place connection verification for personal TMDB and Fanart.tv API keys; unverified keys are never saved.
- **Safe Key Reset:** Clearing key fields safely falls back to the app's built-in shared credentials.
- **MDBList Trimming & Checks:** Key whitespace trimmed and sanitized automatically.
- **Masking Controls:** Added show/hide toggle buttons for sensitive key fields.

### 📊 2. MDBList TMDB ID Fallback & Smart Response Parsing (`MdbListClient.kt`)
- **Automatic TMDB Fallback:** When IMDb identifiers are absent, ratings are queried via positive TMDB IDs (`/ratings/tmdb/...`).
- **Unified Schema Support:** Smoothly parses both modern list schemas and legacy top-level rating structures with caching.
- Integrated into `ApiResultDetailViewModel` and `MediaEntryDetailViewModel`.

### 🎬 3. TMDB Feature Gating & TV Networks (`TmdbMediaDetailClient.kt`)
- **Granular Feature Toggles:** Respects user toggles for basic info, release dates, artwork, trailers, productions, networks, credits, more-like-this, and collections.
- **Broadcast Networks:** Added TV broadcast networks to detail metadata cards.
- **Instant Explore Sync:** Modern TMDB rails in Explore react immediately to setting changes (`tmdbModernHomeEnabled`).

### 🛡️ 4. Sensitive URL Redaction & Logging Safety (`SensitiveUrlRedactor.kt`, `KitsugiApiBase.kt`)
- **Log Sanitization:** Automatically masks API keys, secrets, and authorization tokens in URLs across logs and exceptions (`***`).

### 🧪 5. Test Suite
- Added regression unit tests in `MdbListClientTest.kt` and `TmdbMediaDetailClientTest.kt`.

### 📦 6. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.219-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.218)

### 🔍 1. Cloudstream Canlı İzleme & Tanı Raporlama Sistemi (`CsTrace`)
- **Otomatik Canlı Kayıt (`CsTrace.kt`, `CsTraceCore.kt`):** Eklenti yükleme, arama, başlık varyantları, eşleşme, bölüm bulma, doğrudan link ve embed çıkarma, `loadLinks` aşamaları `filesDir/cs_trace/` altında otomatik olarak kalıcı günlüğe yazılır.
- **Eşleşme Reddi Şeffaflığı:** Hiçbir adayın eşleşme eşiğini geçemediği durumlarda hedef başlık, sezon/bölüm, yıl ve en iyi 5 adayın benzerlik skorları kaydedilir ("neden 0 sonuç" durumu netleşti).
- **Hata & Çökme Takibi:** `loadLinks` zaman aşımları, DEX yükleme hataları ve kritik istisnalar izleme sistemine aktarılır.
- **Yanıltıcı Durum Mesajı Düzeltildi:** `domain_fixes.json` engelli listesindeki eklentiler denenirken tracker'a yazılan yanıltıcı "skip" yerine durum yalnızca izleme günlüğüne yazılır.
- **Canlı Rapor Paylaşımı:** Ayarlar > Eklenti Tanı ekranına **"Canlı İzleme Raporunu Paylaş (.md)"** düğmesi eklendi; oluşturulan Markdown raporu Android paylaşım penceresiyle doğrudan iletilebilir.

### 🎬 2. Kapsamlı Fragman Zinciri (`DetailTrailerFallback.kt`)
- **Çok Katmanlı Yedek Mekanizması:** Kaynak kendi YouTube fragmanını sağlamadığında arka planda sırayla **TMDB → Diğer Metadata Kaynakları (AniList, Jikan, Kitsu) → Cloudstream Eklentileri** (arama ve load yanıtındaki fragmanlar) taranır.
- **Dinamik Ön İzleme Kartı:** Bulunan fragman detay durumuna ve önbelleğe yazılır; `ApiResultDetailViewModel` ve `MediaEntryDetailViewModel` üzerinden "Ön izleme" kartı otomatik olarak belirir.

### 🔘 3. İzle Butonu Tasarım Hizalaması (`KitsugiUiverseGlowButton.kt`)
- **Tek Tip Eylem Butonları:** `KitsugiDetailActionButton` artık `ApiActionButton` ve `ActionButton` ile tam uyumlu: 999dp hap form, 14×10dp dolgu, `labelMedium` tipografi ve 16dp ikon ile görsel bütünlük sağlandı.

### ❌ 4. Açılır Sayfa ve Oynatıcı Kapatma Butonu Düzenlemeleri
- **Player Dışındaki Yüzen Çarpılar Temizlendi (`KitsugiSheetOrDialog.kt`):** v2.4.215'te eklenen evrensel yüzen çarpı kaldırıldı; sayfaların kendi doğal başlık yapıları korundu.
- **Player İçi Açılır Sayfalara Çarpı Butonu:** `PlayerSheet` sürükleme kolu satırına sağa hizalı kapatma çarpısı eklendi (Altyazılar, Ses, Kalite/Kaynak, Bölümler, Hız, Ekran Görüntüsü vb.).
- `EpisodeListDialog` ve `QualityProfileDialog` için tek dokunuşluk kapatma butonları eklendi.

### 🔙 5. Oynatıcı Geri Tuşu ve Jest Gezinme Zinciri (`KitsugiFullscreenPlayerActivity.kt`)
- **Deterministik Çıkış Zinciri:** Jest gezinme, 3 tuşlu çubuk ve donanım geri tuşlarının tamamında çalışan `OnBackPressedDispatcher` callback'i kuruldu:
  - `dialog` → `panel` → `sheet` → `kontrolleri gizle` → `oynatıcıdan çık`.

### 📦 6. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.218-foss.apk`).

---

## 🇬🇧 English (v2.4.218)

### 🔍 1. Cloudstream Live Trace & Diagnostic Reporting (`CsTrace`)
- **Automated Live Tracing (`CsTrace.kt`, `CsTraceCore.kt`):** Plugin loading, search query variants, title matching, episode extraction, direct links, embed scrapers, and `loadLinks` phases are persisted to `filesDir/cs_trace/`.
- **Match Rejection Transparency:** When candidate items fail matching thresholds, the target title, season, episode, year, and similarity scores of top 5 candidates are logged to reveal the exact reason behind empty results.
- **Fail-Safe Exception Recording:** `loadLinks` timeouts, DEX loading exceptions, and critical errors are automatically captured.
- **Accurate Status Attribution:** Suppressed misleading "skip" tracker entries for plugins on domain blocklists that are still actively probed.
- **One-Tap Report Sharing:** Added **"Share Live Trace Report (.md)"** button in Settings > Plugin Diagnostics to export markdown traces via Android share sheet.

### 🎬 2. Universal Trailer Fallback Pipeline (`DetailTrailerFallback.kt`)
- **Multi-Source Fallback Chain:** If the primary source lacks a YouTube trailer, a fallback ladder queries **TMDB → Metadata Sources (AniList, Jikan, Kitsu) → Cloudstream Plugins**.
- **Dynamic Preview Card:** Resolved trailers are written to detail state and cached, automatically displaying the "Preview" card across `ApiResultDetailViewModel` and `MediaEntryDetailViewModel`.

### 🔘 3. Action Button Visual Alignment (`KitsugiUiverseGlowButton.kt`)
- **Unified Action Styling:** `KitsugiDetailActionButton` now aligns with `ApiActionButton` and `ActionButton`: 999dp pill form factor, 14×10dp padding, `labelMedium` typography, and 16dp icon.

### ❌ 4. Sheet & Player Close Button Refinements
- **Removed Extraneous Floating Close Buttons (`KitsugiSheetOrDialog.kt`):** Reverted universal floating close button outside the player, preserving native sheet headers.
- **Player Sheet Close Buttons:** Added unified close buttons to the `PlayerSheet` header bar across all bottom sheets (Subtitles, Audio Tracks, Quality/Sources, Episodes, Playback Speed, Screenshot).
- Added close buttons to `EpisodeListDialog` and `QualityProfileDialog`.

### 🔙 5. Player Back Gesture & Navigation Dispatcher Chain (`KitsugiFullscreenPlayerActivity.kt`)
- **Deterministic Back Key Handling:** Integrated an `OnBackPressedDispatcher` callback supporting gesture navigation, 3-button navigation, and hardware back keys:
  - `dialog` → `panel` → `sheet` → `hide controls` → `exit player`.

### 📦 6. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.218-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.217)

### 🚪 1. Merkezi Jikan Gateway (`JikanGateway.kt`) & MAL Trafik Optimizasyonu
- **Merkezi Kota ve Hız Sınırı (Rate Limiting):** Tüm Jikan trafiği tek bir kapıdan yönetilir; saniyede 3 ve dakikada 55 istek sınırları aşılmaz.
- **Toplu 429 Koruması:** Jikan'dan 429 (Too Many Requests) yanıtı geldiğinde tüm Jikan istekleri birlikte beklemeye geçer ve `Retry-After` başlığına uyulur.
- **Akıllı Bellek Önbelleği (In-Memory Cache):** Detay uçları 6 saat, liste ve arama uçları 30 dakika boyunca önbelleğe alınır; gereksiz ağ istekleri önlenir.
- **Eşzamanlı İstek Birleştirme (Single-Flight Deduplication):** Aynı URL'ye aynı anda gelen istekler tek bir ağ çağrısını paylaşır; sekmelerin eşzamanlı yüklenmesinde sunucuya tek istek gider.
- **Öncelikli Trafik Yönetimi:** Arka plan görevleri (profil senkronizasyonu, kimlik eşleştirme) kullanıcının aktif gezindiği ekranların kotasını tüketmez.
- **Keşfet & Arama MAL Önceliği:** MAL için Jikan önce denenir, resmi MAL API ikinci, AniList ise son yedek olarak çalışır. Karakter, ekip, ilişki, öneri ve bölüm istekleri gateway üzerinden güvenle akar.

### 🎯 2. AniList Manuel Kayıtlarında Yanlış Öneri ve Kimlik Çakışması Düzeltmesi
- **Kök Neden Giderildi (`MediaEntryDetailViewModel.kt`):** Manuel eklenen AniList kayıtlarında `malId` boş kaldığında yerel veritabanı satır numarasının (`entry.id`) dış servis kimliği gibi okunması ve başka bir yapımın MAL ID'siyle çakışarak alakasız öneriler getirmesi (ör. Rent-a-Girlfriend sayfasında farklı animelerin önerilerinin çıkması) engellendi.
- **Yalnızca Gerçek Kimlik Kullanımı:** AniList kaynaklı kayıtlarda yerel satır numarası (`entry.id`) dış kimlik olarak kabul edilmez; yalnızca geçerli bir `malId` varsa dış ID olarak kullanılır.
- **Başlık Tabanlı Arama Fallback'i (`KitsugiMediaRelationsClient.kt`):** Dış kimliği bulunmayan AniList kayıtlarında temizlenmiş başlık üzerinden doğrudan AniList API'sinde arama yapılarak (`fetchRecommendationsFromAniListBySearch`) doğru öneriler getirilir.

### 🖼️ 3. Vitrin Kaplama & Responsive Görsel Sunumu (`KitsugiHeroSection.kt`)
- **Tam Kaplayan Vitrin (Full Bleed Cover/Crop):** Bulanık arka plan dolgusu ve sığdırma (fit) yapısı kaldırılarak resmin tüm vitrin alanını kaplaması sağlandı (`ContentScale.Crop`).
- **Responsive Kaynak Seçimi (`heroImageCandidates`):**
  - Geniş vitrin bandında (en/boy $\ge$ 1.1, yatay ekran veya tablet): yatay dikdörtgen fanart/backdrop öncelikli.
  - Dikey telefon modunda (en/boy < 1.1): dikey poster öncelikli.
- **Akıllı Hizalama (`BiasAlignment(0f, -0.2f)`):** Yatay arka planlar üst gövde ve yüzleri koruyacak şekilde yukarıdan hafif odaklı hizalanır; dikey posterler ise üstten kırpılır.
- **Yedek Görsel Zinciri:** Birincil görsel yüklenemediğinde otomatik olarak alternatif adaya (backdrop $\leftrightarrow$ poster) geçilir.

### 🌸 4. Bangumi Keşfet, REAL Diziler/Filmler ve Yerelleştirme Güncellemeleri
- **Canlı Çekim (REAL) Rafları (`BangumiRealExploreSections.kt`):** Bangumi TV dizileri/dramaları ve filmleri için Keşfet bölümüne özel raflar eklendi.
- **Filtreleme & Sayfalama:** Yetişkin içerik filtreleme, vitrin ve rastgele yapım seçimi, kaynak belirtimi ve "Tümünü Gör" sayfalaması entegre edildi.
- **Genişletilmiş İsim ve Çeviri Seçimi:** Karakter, seslendirmen ve ekip adları AniList'te eşleşmediğinde doğrudan Bangumi infobox yedeklerine başvurulur; AniList köprüsü 2. sayfayı da denetler.

### 🌐 5. Türkçe Akış & Başlık Temizleme Düzeltmeleri
- **Site Gürültüsü Temizleme (`stripTurkishSiteNoise`):** Türkçe sitelerin başlıklara eklediği gürültü kalıpları 16 özel regex ile ayıklandı.
- **Genişletilmiş Sorgular:** Arama sorguları 12'den 18'e çıkarıldı, geniş arama fallback'i ve Türkçe CDN/Range optimizasyonları korundu.

### 📦 6. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.217-foss.apk`).

---

## 🇬🇧 English (v2.4.217)

### 🚪 1. Centralized Jikan Gateway (`JikanGateway.kt`) & MAL Traffic Pipeline
- **Unified Rate Limiting:** All Jikan traffic routes through a single gateway strictly bounded by 3 req/sec and 55 req/min token-bucket limits.
- **Coordinated 429 Backoff:** Upon receiving HTTP 429 (Too Many Requests), all queued and concurrent Jikan requests hold together, respecting `Retry-After`.
- **In-Memory Caching:** Detail endpoints cached for 6 hours; list and search endpoints cached for 30 minutes.
- **Single-Flight Request Deduplication:** Concurrent identical URL requests share a single underlying network call, eliminating duplicate fetches during multi-tab screen initialization.
- **Priority-Aware Traffic Budgeting:** Background workloads (profile sync, identity mapping) do not exhaust quota allocated for active user UI navigation.
- **Explore & Search MAL Priority:** Jikan is tried first for MAL, with the official MAL API and AniList serving as reliable fallbacks.

### 🎯 2. AniList Manual Entry False ID & Recommendation Mismatch Fix
- **Resolved Database Row ID Leak (`MediaEntryDetailViewModel.kt`):** Fixed an issue where manual AniList entries without a `malId` mistakenly defaulted to using their local database row index (`entry.id`) as an external API identifier, causing erroneous recommendation lookups when clashing with other titles' MAL IDs.
- **Strict External ID Resolution:** Explicitly decoupled internal database row IDs from external identifier logic for AniList sources; only valid `malId` values are passed.
- **Title-Based Recommendation Fallback (`KitsugiMediaRelationsClient.kt`):** When external IDs are absent for AniList entries, recommendations are safely retrieved by title query (`fetchRecommendationsFromAniListBySearch`) instead of leaking erroneous IDs.

### 🖼️ 3. Full Bleed Hero Cover & Responsive Presentation (`KitsugiHeroSection.kt`)
- **Full Bleed Cover/Crop:** Replaced blur-filled letterboxing with edge-to-edge `ContentScale.Crop` presentation.
- **Responsive Media Selection (`heroImageCandidates`):**
  - Wide hero banners (aspect ratio $\ge$ 1.1, landscape & tablet): landscape backdrop/fanart first.
  - Portrait mobile phone viewports: high-res vertical poster first.
- **Smart Subject Framing (`BiasAlignment(0f, -0.2f)`):** Horizontal backdrops align with a slight upward bias to keep heads and focal subjects in frame; portrait posters align to top.
- **Seamless Fallback Chain:** If the primary candidate fails to render, the hero automatically cascades to the alternate candidate (backdrop $\leftrightarrow$ poster).

### 🌸 4. Bangumi Explore REAL Shelves & Localization Updates
- **Live-Action REAL Sections (`BangumiRealExploreSections.kt`):** Introduced dedicated Explore shelves for Bangumi TV dramas and films.
- **Filtering & Pagination:** Integrated adult content filters, hero & random selection, source attribution, and "See all" pagination.
- **Expanded Infobox & Fallbacks:** Direct Bangumi fallback for character, voice-actor, and staff names when AniList lacks a match; AniList bridge checks second-page search results.

### 🌐 5. Turkish Stream Scraper & Noise Cleaning
- **Noise Stripping (`stripTurkishSiteNoise`):** Strips 16 regex noise patterns commonly found on Turkish streaming websites.
- **Broad Search & Query Expansion:** Query variants expanded to 18 with intelligent fallback matching and CDN handling.

### 📦 6. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.217-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.216)

### 🖼️ 1. Vitrin Cover / Baştan Sona Kaplama & Akıllı Görsel Seçimi
- **Tam Ekran Kaplayan Vitrin (`ContentScale.Crop`):** Önceki "Fit + bulanık dolgu" katmanları tamamen kaldırıldı. Vitrin görseli artık ekranı baştan sona kaplar, kenarlarda boşluk veya bulanık zemin kalmaz.
- **Ekran Boyutu ve Oranına Göre Akıllı Seçim (`heroImageCandidates`):**
  - Geniş vitrin bandı (yatay telefon, tablet, TV — en/boy $\ge$ 1.1) $\rightarrow$ yatay dikdörtgen fanart / backdrop öncelikli.
  - Dikey telefon vitrini (kareye yakın kutu) $\rightarrow$ dikey poster öncelikli.
- **Dinamik Hata Yedekleme Zinciri (`onLoadingFailed`):** Birincil görsel yüklenemezse anında sıradaki adaya (backdrop $\leftrightarrow$ poster) geçilir.
- **Kırpma Odak Hizalaması (`heroImageAlignment`):** Geniş vitrinde poster kullanılmak zorunda kalındığında karakter yüzü ve başlık bandının korunması için hafif yukarı bias (`BiasAlignment(0f, -0.2f)`), backdrop görsellerde merkez hizalama uygulandı.
- **Çok Yönlü TMDB Backdrop Zenginleştirmesi:** Sadece yatay mod değil, dikey tablet ve geniş vitrin bantları da TMDB fanartlarından otomatik yararlanır.
- **Yumuşatılmış Gradyanlar:** Metin okunabilirliğini korurken görselin merkezini ve detaylarını kapatmayan yumuşak renk geçişleri.
- **HeroImageSelectionTest:** 13 birim testi %100 başarıyla doğrulandı.

### 🇹🇷 2. Türkçe Akış & Eklenti Pipeline İyileştirmeleri
- **Site Başlık Kirliliği Temizleme (`stripTurkishSiteNoise`):** Türkçe dizi/film sitelerinin başlıklara eklediği reklam ve gürültü metinleri ("Türkçe Dublaj 1080p izle" vb.) 16 regex kuralı ile filtrelenerek temiz başlık eşleştirmesi sağlandı.
- **Genişletilmiş Dil & Arama Varyantları:** Başlık varyantları 8'den 12'ye, arama sorguları 12'den 18'e çıkarıldı; Türkçe karakter transliterasyonu (ğ $\rightarrow$ g, ş $\rightarrow$ s, ı $\rightarrow$ i) eklendi.
- **Geniş Arama Fallback'i (`buildBroadSearchQueries`):** Tüm varyantlar boş döndüğünde anlamlı ilk 2-3 kelime üzerinden genişletilmiş arama yapılarak farklı Türkçe adlandırmalar yakalanır.
- **45+ Yeni Türkçe CDN Tanıma:** AlionsPlayer, VidMoly, TRsTX, CloseLoad, Pichive, Rapidrame, VidGuard, TurboVid, HDVid, PlayTube, VidHide, Embedy gibi 45+ sağlayıcı `isEmbedUrl` kapsamına eklendi.
- **Gelişmiş Stream Canlılık Doğrulaması (`tryGetRangeValidation`):** HEAD 400 hatası veren akışlarda doğrudan başarısız saymak yerine GET Range header ile doğrulama yapılır; zaman aşımı süresi 6s $\rightarrow$ 8s'ye yükseltildi.
- **Türkçe Başlık Model Alanı (`titleTurkish`):** TMDB üzerinden Türkçe başlıklar çekilerek modele ve arama motoruna entegre edildi.

### 📦 3. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.216-foss.apk`).

---

## 🇬🇧 English (v2.4.216)

### 🖼️ 1. Vitrin Cover Presentation & Responsive Hero Asset Selection
- **Full Bleed Cover (`ContentScale.Crop`):** Completely removed letterboxed "Fit + ambient blur" layers. Hero artwork now stretches end-to-end across the banner container without blurred sidebars or margins.
- **Aspect-Aware Asset Prioritization (`heroImageCandidates`):**
  - Wide hero containers (landscape, tablets, TV — aspect $\ge$ 1.1) prioritize horizontal fanart / backdrop artwork.
  - Portrait mobile phone boxes prioritize vertical posters.
- **Resilient Fallback Chain (`onLoadingFailed`):** Automatically cascades to the alternative candidate (backdrop $\leftrightarrow$ poster) upon asset load failure.
- **Focal Alignment Preservation (`heroImageAlignment`):** Applies slight top-vertical bias (`BiasAlignment(0f, -0.2f)`) when vertical posters are cropped in widescreen containers to prevent cropping faces and title text.
- **Omni-Directional TMDB Backdrop Enrichment:** TMDB backdrop enrichment now activates across all orientations, including portrait tablets.
- **Softened Gradients:** Refined horizontal and vertical gradient scrims to preserve artwork visibility while ensuring text readability.
- **Unit Tests (`HeroImageSelectionTest`):** 13 unit tests passed with 100% success.

### 🇹🇷 2. Turkish Stream & Extension Scraper Pipeline Optimization
- **Stripped Turkish Title Noise (`stripTurkishSiteNoise`):** Cleans up title noise and metadata suffixes ("Türkçe Dublaj 1080p izle", etc.) via 16 regex patterns for accurate content matching.
- **Expanded Query & Language Variants:** Increased title variants from 8 to 12 and search queries from 12 to 18 with ASCII transliteration (ğ $\rightarrow$ g, ş $\rightarrow$ s, ı $\rightarrow$ i).
- **Broad Search Fallback (`buildBroadSearchQueries`):** Secondary search pass utilizing the first 2-3 significant title tokens when exact matches return empty.
- **45+ Turkish Embed & CDN Patterns:** Integrated detection for AlionsPlayer, VidMoly, TRsTX, CloseLoad, Pichive, Rapidrame, VidGuard, TurboVid, HDVid, PlayTube, VidHide, Embedy, and others.
- **Enhanced Stream Probing (`tryGetRangeValidation`):** Mitigates HEAD 400 rejections by falling back to GET requests with HTTP Range headers; increased probe timeout from 6s to 8s.
- **Turkish Title Model Field (`titleTurkish`):** Added `titleTurkish` to `KitsugiMediaDetail` populated directly from TMDB localized payloads.

### 📦 3. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.216-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.215)

### 🛡️ 1. Sessiz Çökme Kök Neden Çözümü (RenderThread SIGSEGV & WebView Teardown)
- **Çifte Destroy ve Use-After-Free Giderildi (`CsCfWarmupManager.kt`):** Çökme raporlarındaki `cr_AwContents: Application attempted to call on a destroyed WebView` uyarıları ve ardından `RenderThread` display-list oynatımı sırasında oluşan `SIGSEGV` (null+0x20) hatası tamamen çözüldü.
- **Güvenli Tek Seferlik Teardown (`releaseWebView`):** `AtomicBoolean` korumasıyla WebView yalnızca bir kez sökülür, durdurulur, `about:blank` yüklenir ve güvenle yok edilir.
- **Açılış Rahatlatması (`KitsugiApplication.kt`):** Proaktif Cloudflare warmup işlemi ilk açılışta arayüzle yarışıp frame atlamalarına (`Skipped 35 frames`) ve `DequeueBuffer` zaman aşımlarına yol açmaması için 12 saniye gecikmeli başlatıldı.

### 🎬 2. Oynatıcı, Altyazı ve PiP İyileştirmeleri
- **Dahili Türkçe Altyazı Seçim Döngüsü (`Media3PlayerEngine.kt`):** Harici altyazılar bağlanmadan önce ilk `onTracksChanged` olayında seçimin sonlanması engellendi. Hedef Türkçe parça tespit edilene kadar (maksimum 8 deneme) otomatik seçim politikası çalışır. Kullanıcının elle yaptığı altyazı seçimi korunur (`userSelectedTextTrack`).
- **Altyazı Taşma Koruması:** Dikey konum kaydırıldığında altyazının ekran dışına veya alt çubuğun altına kaçması engellendi; 4dp kenar payı ile görünür alana sabitlendi.
- **PiP Kapatıldığında Sesin Devam Etmesi Engellendi (`KitsugiFullscreenPlayerActivity.kt`):** Mini pencere kapatıldığında veya odak kaybolduğunda sesin arkadan çalmaya devam etmesi çözüldü; `invokePipAction` yedek mekanizmasıyla doğrudan ViewModel'e iletildi, `KeepAliveService.stop()` ve temiz Activity sonlandırması (`finish()`) sağlandı.
- **Kalıcı Tam Ekran (Immersive Mode):** MIUI ve HyperOS gibi arayüzlerde PiP veya odak değişimi sonrası alt gezinme çubuğunun ekranda asılı kalması giderildi (`onResume` ve `onWindowFocusChanged` üzerinde dinamik yenileme).

### 📑 3. Açılır Sayfalar (Sheet/Dialog) ve Çarpı Butonu
- **Kaydırma Kilidi Kaldırıldı (`KitsugiSheetOrDialog.kt`):** Açılır sayfalarda içerik aşağı kaydırıldığında kapatma hareketinin kilitlenmesi sorunu giderildi.
- **Evrensel Kapatma Çarpısı (`KitsugiSheetCloseButton`):** Tüm açılır sayfalara sağ üstte şık ve her koşulda çalışan kapatma çarpısı eklendi.

### 🌐 4. Bangumi Kapsamlı Türkçe Çeviri ve Metadata Zenginleştirmesi
- **95+ Tür ve Etiket Çevirisi:** Çince ve Japonca tür/etiketler (恋爱 → Romantik, 日常 → Günlük Yaşam, 京阿尼 → Kyoto Animation, 神作 → Başyapıt vb.) Türkçe'ye çevrildi (`strings.xml` ve `values-en/strings.xml`). Tür etiketine tıklanarak arama yapıldığında İngilizce API terimine dönüştürme eklendi.
- **Stüdyo Adları:** Çince/Japonca stüdyo adları Latin karşılıklarına dönüştürüldü (京都アニメーション → Kyoto Animation vb.).
- **AniList Kişi & Karakter Köprüsü (`KitsugiAniListPersonBridge`):** Bangumi'de kanji kalan karakter ve seslendirmen isimleri AniList köprüsü üzerinden otomatik olarak Romaji ve İngilizce isimlerle eşleştirildi.
- **Kadro Rolleri Çevirisi:** Episode Direction → Bölüm Yönetmeni, Animation Direction → Animasyon Yönetmeni, 監督 → Yönetmen vb. kadro rolleri eksiksiz Türkçeleştirildi.
- **İlişki ve Öneri Başlıkları:** Önerilen ve ilişkili yapımların Japonca başlıkları İngilizce/Romaji karşılıklarıyla zenginleştirildi.

### 📋 5. Çökme Raporu Dosya (.txt) Paylaşımı
- **FileProvider ile Doğrudan Dosya Paylaşımı (`KitsugiCrashRecoveryDialog.kt`):** Çökme ekranındaki "Paylaş" butonu artık düz metin yerine doğrudan `crash_log.txt` dosyasını paylaştırır.

### 📦 6. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.215-foss.apk`).

---

## 🇬🇧 English (v2.4.215)

### 🛡️ 1. Silent Crash Resolution (RenderThread SIGSEGV & WebView Teardown)
- **Resolved Double-Destroy Use-After-Free (`CsCfWarmupManager.kt`):** Fixed the root cause of `cr_AwContents: Application attempted to call on a destroyed WebView` warnings and subsequent `RenderThread` display-list recursive traversal crashes (`SIGSEGV` at `fault_addr=0x20`).
- **One-Shot Teardown Routine (`releaseWebView`):** Enforced atomic, single teardown guarded by `AtomicBoolean` ensuring WebView is unattached, halted, pointed to `about:blank`, and cleanly destroyed without race conditions.
- **Warmup Startup Throttle (`KitsugiApplication.kt`):** Deferred proactive Cloudflare warmup by 12 seconds to prevent resource contention during initial frame rendering, eliminating frame drops and buffer timeouts.

### 🎬 2. Player, Subtitle & PiP Architecture Fixes
- **Embedded Subtitle Selection Convergence (`Media3PlayerEngine.kt`):** Resolved race condition where initial `onTracksChanged` fired before sideloaded/embedded Turkish subtitle tracks arrived. Retries up to 8 times until preferred language is settled; preserves manual user track overrides (`userSelectedTextTrack`).
- **Subtitle Bounds Clamping:** Prevented vertical subtitle offset adjustments from translating text offscreen or underneath navigation chrome (clamped with 4dp margins).
- **PiP Background Audio Leak Elimination (`KitsugiFullscreenPlayerActivity.kt`):** Fixed issue where closing the mini-player left playback audio active. Replaced dangling Compose callbacks with `invokePipAction` ViewModel fallbacks, stopped `KeepAliveService`, and terminated ghost player activities.
- **Persistent Immersive Mode:** Re-applied system bar hiding on `onResume` and `onWindowFocusChanged` to fix persistent navigation bar overlays on MIUI/HyperOS devices.

### 📑 3. Sheets & Universal Close Button
- **Fixed Sheet Drag Lock (`KitsugiSheetOrDialog.kt`):** Removed bottom-sheet dismissal locking when scrolling content down.
- **Universal Close Button (`KitsugiSheetCloseButton`):** Added persistent top-right close buttons across all sheets and dialogs.

### 🌐 4. Bangumi Full Turkish Localization & Metadata Enrichment
- **95+ Tag & Genre Translations:** Localized Chinese/Japanese Bangumi genres and tags into Turkish and English with fallback dictionaries and search query translation.
- **Studio Name Latinization:** Converted Kanji studio names into recognized Latin branding (e.g. Kyoto Animation, Pony Canyon).
- **AniList Person & Character Bridge (`KitsugiAniListPersonBridge`):** Automatically matches and hydrates Kanji character and voice actor names with Romaji/English names via AniList.
- **Staff Role Translations:** Fully translated production credits (Episode Director, Animation Director, Script, etc.) into Turkish.
- **Relation & Recommendation Title Hydration:** Hydrates Japanese titles in related and recommended lists with English/Romaji metadata.

### 📋 5. Direct Crash Log File Sharing
- **Crash Log File Provider (`KitsugiCrashRecoveryDialog.kt`):** "Share" action on the crash recovery dialog now shares the actual `crash_log.txt` file via FileProvider instead of raw text.

### 📦 6. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.215-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.214)

### 🌟 1. Vitrin v2: Akıllı Seçim ve Skorlama Motoru (`HeroSelection.kt`)
- **Metrik Tabanlı Puanlama:** Vitrin (Hero Section) içerik seçimi rastgelelikten çıkarılıp ağırlıklı metrik algoritmasına geçirildi:
  $$\text{Skor} = (\text{Puan} \times 10) + (\log_{10}(\text{Üye} + 1) \times 8) + (\log_{10}(\text{Favori} + 1) \times 6) + \text{Rank} + \text{Trend (15)} + \text{Sezon (10)} + \text{Manga (8)}$$
- **Katalog ve Format Çeşitliliği:** Vitrinin tek bir türe veya kaynağa boğulması engellendi; en fazla 2 manga, en az 5 anime/dizi/film ve kaynak başına tavan sınırı (en fazla 3) ile dengeli bir vitrin akışı sağlandı.
- **Kaynaklar Arası Metrik Normalizasyonu:** TMDB gibi üye/favori bilgisi içermeyen kaynaklarda oy sayısı (`voteCount`) ve oy ortalaması logaritmik olarak dengelenerek haksız puan kaybı önlendi.
- **Görsel Kalite Filtresi:** Arka planı (backdrop) veya yüksek çözünürlüklü yatay görseli bulunmayan içerikler vitrin önceliğinde elenir.

### 🖼️ 2. Fit + Bulanık Dolgu Katmanlı Görsel Mimarisi (Kırpma Düzeltmesi)
- **Görsel Kırpma / Kesilme Sorunu Çözüldü (`KitsugiHeroSection.kt`):** Eski zorunlu kırpma (`ContentScale.Crop`) nedeniyle dikey afişlerin ve karakter yüzlerinin kesilme problemi tamamen ortadan kaldırıldı.
- **Çift Katmanlı Sunum:**
  - **Ön Plan:** Orijinal en-boy oranını tam koruyan (`ContentScale.Fit`) net ve keskin ana görsel.
  - **Arka Plan:** Ekranı boşluk bırakmadan dolduran, ortam rengini yansıtan bulanık dolgu katmanı (`HeroAmbientBackground` — Crop + Blur + Karartma).
- **Yumuşatılmış Gradyanlar:** Başlık ve meta metinlerinin okunabilirliğini artırırken görseli boğmayan yumuşak alt ve üst geçiş katmanları uygulandı.

### 🔄 3. TMDB Arka Plan Entegrasyonu ve Birleştirme (`ExploreViewModel.kt`)
- **Eksik Arka Planları Tamamlama (`enrichHeroBackdrops`):** MAL, Bangumi veya diğer kaynaklardan gelen popüler animelerde yatay backdrop bulunmadığında, TMDB üzerinden otomatik olarak yatay afiş temin edilir ve `heroBackdropOverrides` ile vitrinde kullanılır.
- **Tüm Bölümlerden Havuz Oluşturma (`ExploreScreen.kt` & `AllSourcesExplore.kt`):** Trend, Popüler, Sezonluk ve Manga listelerinin tamamından tekilleştirilmiş zengin havuz toplanarak yeni vitrin motoruna teslim edildi.

### 📊 4. Zengin Meta ve İstatistik Satırı (`HeroSectionComponents.kt`)
- **Kompakt Metrik Bilgileri:** Vitrin kartlarının altındaki meta satırına "1.2M üye • 45.3K favori" şeklinde biçimlendirilmiş topluluk metrikleri eklendi.

### 🧪 5. Testler ve Doğrulama (`HeroSelectionTest.kt`)
- Vitrin seçim ve skorlama motoru için 10 adet birim testi eklendi; kaynak çeşitliliği, skor hesaplamaları, TMDB normalizasyonu ve fallback senaryoları %100 başarıyla doğrulandı.

### 📦 6. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.214-foss.apk`).

---

## 🇬🇧 English (v2.4.214)

### 🌟 1. Vitrin / Hero Section v2: Intelligent Selection Engine (`HeroSelection.kt`)
- **Weighted Metric Scoring:** Vitrin items are now selected through a multi-factor ranking algorithm:
  $$\text{Score} = (\text{Score} \times 10) + (\log_{10}(\text{Members} + 1) \times 8) + (\log_{10}(\text{Favorites} + 1) \times 6) + \text{Rank} + \text{Trending Bonus (15)} + \text{Seasonal Bonus (10)} + \text{Manga Bonus (8)}$$
- **Format & Source Diversity:** Guaranteed balanced representation across the carousel: max 2 manga, min 5 anime/shows, and provider caps (max 3 per provider) preventing single-source domination.
- **Cross-Source Metric Normalization:** Sources lacking member/favorite counts (e.g. TMDB) are normalized using vote counts and ratings to maintain fair scoring.
- **Backdrop Quality Gate:** Automatically prioritizes titles with high-resolution backdrops or landscape banners over raw vertical posters.

### 🖼️ 2. Layered Fit + Ambient Blur Presentation (Cropping Fix)
- **Eliminated Poster Cropping (`KitsugiHeroSection.kt`):** Fixed the aggressive `ContentScale.Crop` behavior that cropped character heads and vertical poster artwork.
- **Dual-Layer Rendering:**
  - **Foreground:** Pristine uncropped asset rendered with `ContentScale.Fit`, preserving the original aspect ratio.
  - **Background:** Ambient blur fill (`HeroAmbientBackground` — Crop + Blur + Dim) that seamlessly expands to fill widescreen or ultra-wide viewport edges.
- **Refined Gradients:** Softened multi-stop linear gradients ensuring text readability without obscuring artwork.

### 🔄 3. TMDB Backdrop Enrichment (`ExploreViewModel.kt`)
- **Backdrop Fallback Hydration (`enrichHeroBackdrops`):** When popular entries from MAL or Bangumi lack native horizontal backdrops, the ViewModel dynamically queries TMDB for matching fanart/backdrops, merging them via `heroBackdropOverrides`.
- **Multi-Shelf Pool Aggregation (`ExploreScreen.kt` & `AllSourcesExplore.kt`):** Merged items across Trending, Popular, Seasonal, and Manga shelves into an enriched pool fed into the selection engine.

### 📊 4. Compact Community Statistics (`HeroSectionComponents.kt`)
- **Rich Meta Display:** Added formatted community engagement badges (e.g. "1.2M members • 45.3K favorites") to the hero meta bar.

### 🧪 5. Testing & Validation (`HeroSelectionTest.kt`)
- Added 10 unit tests covering selection diversity, score calculation, TMDB fallback normalization, and edge cases with 100% pass rate.

### 📦 6. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.214-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.213)

### 🔍 1. Arama Ekranı: Yükleme Animasyonu Tutarlılığı (Shimmer Fix)
- **Kopya Shimmer Kaldırıldı:** "Tümü" sekmesinde verisi gelmiş rafların üstüne binen 5 sabit shimmer rafı tamamen kaldırıldı. Artık raf başına tek görsel durum var: Verisi gelmeyen raf kendi shimmer'ını gösterir, verisi gelen raf anında karta dönüşür.
- **Titreme Giderildi:** Arama başladığında yükleme bayrakları kapsama göre anında yazılır; ara "boş + yüklenmiyor" karesi ve rafların kaybolup geri gelmesi (titreme) engellendi.
- **Sayfalama Shimmer Çakışması:** Diğer sekmelerde shimmer yalnızca henüz hiç sonuç yokken çizilir; sayfalama ve yenilemede mevcut sonuçların üstüne binmez.

### 🎯 2. Veri Doğruluğu, Kapsam Süzme ve Alakalılık (`SearchRelevance`)
- **Doğru Kapsam Planlaması (`planAllSources`):**
  - Manhwa / Manga / Manhua / Light Novel kapsamında TMDB ve Simkl'e istek atılmaz; çizgi roman aramalarında alakasız live-action filmler çıkmaz.
  - Diziler / Filmler / K-Drama aramaları anime veritabanlarına düşürülmez; yalnızca TMDB ve Simkl sorgulanır.
  - Karakter / Personel araması "Tümü" motorunda medya raflarını tetiklemez.
- **Türe Duyarlı Tekilleştirme:** Aynı ID'ye sahip anime ve manga kayıtlarının birbirini ezmesi (`kaynak_tur_kimlik`) önlendi; Manhwa ve Manga kayıtları anime altında kaybolmaz (dönüşümlü `interleave` birleştirme).
- **Yeni Alakalılık Motoru (`SearchRelevance`):** Sorguyla doğrudan eşleşen başlıklar en öne alınır, kayıt silinmez. Türkçe karakter ve aksan indirgeme ("İstanbul" ↔ "istanbul") ve yabancı takma ad koruması eklendi.
- **Jikan & AniList İyileştirmesi:** Arama sonuçlarını bozan zorunlu popülerlik sıralaması kaldırıldı, metin alakalılığı ön plana çıkarıldı.

### ⌨️ 3. Onaysız Arama Kaldırıldı (Onaylı & Butonlu Arama)
- **Yazarken Kendiliğinden Arama Engellendi:** Her tuş vuruşunda sorgu atan debounce kaldırıldı. Arama artık yalnızca klavyedeki "Ara" onay tuşuna veya arama butonuna basıldığında tetiklenir.
- **Arama Onay Butonu:** Arama çubuğuna metin yazıldığında görünen arama butonu eklendi.
- **Kompakt Motor Hapı:** Küçük ekranlarda metin alanının daralmaması için yazma esnasında motor seçici kompakt moda geçer.

### 📦 4. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.213-foss.apk`).

---

## 🇬🇧 English (v2.4.213)

### 🔍 1. Search Screen: Shelf Shimmer Consistency (Shimmer Fix)
- **Eliminated Duplicate Shimmers:** Removed the redundant 5-shelf shimmer overlay on the "All" tab. Each provider shelf now owns its visual state: displaying shimmer until its data arrives, then immediately transforming into content.
- **Flicker Elimination:** Loading flags are populated immediately upon search initiation, removing the intermediate "empty/idle" flicker frame.
- **Pagination Shimmer Overlap:** Shimmer placeholders in single-engine tabs only render when the result set is completely empty, preventing overlay during pagination.

### 🎯 2. Data Accuracy, Scope Routing & Relevance (`SearchRelevance`)
- **Scoped Provider Planning (`planAllSources`):**
  - Manga / Manhwa / Manhua / Light Novel scopes bypass TMDB and Simkl completely, eliminating live-action film bleed into print searches.
  - TV Shows / Movies / K-Drama queries route exclusively to TMDB and Simkl without querying anime databases.
  - Character / Staff scopes never trigger media shelves.
- **Type-Aware Deduplication:** Multi-engine keys now include media type (`source_type_id`), preventing legitimate manga records from being dropped when sharing an ID with an anime. Anime and manga are interleaved in mixed searches.
- **Search Relevance Engine (`SearchRelevance`):** Query matches are ranked to the top without pruning non-exact matches (preserving synonyms, romaji, and alternate titles). Added Turkish diacritic folding ("İstanbul" ↔ "istanbul").
- **Jikan & AniList Ranking Fix:** Stripped artificial popularity overrides on search queries in favor of pure text relevance.

### ⌨️ 3. Explicit Search Triggering (Debounce Removed)
- **No Keystroke Auto-Search:** Eliminated automatic debounced searching while typing. Searches only execute upon tapping the keyboard action button or the search icon.
- **Search Action Button:** Added an explicit search trigger button in the search bar when query text is present.
- **Compact Engine Selector:** Engine selector switches to compact mode while typing on small screens to preserve input field width.

### 📦 4. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.213-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.212)

### 📥 1. Android 10 (API 29) ve Üzeri İndirme / Kaydetme Düzeltmesi (ENOENT Fix)
- **Kök Neden Giderildi:** Galeri ve detay ekranlarından görsel indirirken Android 10 ve üzeri Scoped Storage kısıtlamaları nedeniyle meydana gelen `open failed: ENOENT (No such file or directory)` hatası çözüldü.
- **Doğru MediaStore Koleksiyonu:** `MediaStore.Images` yerine Android 10 standartlarına uygun olarak `MediaStore.Downloads.EXTERNAL_CONTENT_URI` ile `Download/Kitsugi/Images` yolu kullanıldı; alternatif olarak `Pictures/Kitsugi/Images` desteği eklendi.
- **Kademeli Güvenli Kayıt (Fallback):** Scoped Storage veya özel ROM kısıtlaması olan cihazlarda doğrudan dosya yazımı engellense dahi `getExternalFilesDir` yedekleme mekanizmasıyla görsellerin her koşulda sıfır hatayla kaydedilmesi sağlandı.
- **Depolama İzinleri & Bayraklar:** `AndroidManifest.xml` içerisine `requestLegacyExternalStorage="true"` eklendi ve `WRITE_EXTERNAL_STORAGE` izni Android 10 (`maxSdkVersion=29`) için de geçerli kılındı.
- **Galeri Otomatik Tarama:** İndirilen resimler `MediaScannerConnection` ile anında taranarak cihazın yerel Galeri ve Fotoğraflar uygulamalarında hemen görünür hale getirildi.

### 📦 2. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.212-foss.apk`).

---

## 🇬🇧 English (v2.4.212)

### 📥 1. Android 10+ (API 29) Image Download & Storage Fix (ENOENT Fix)
- **Root Cause Resolved:** Fixed `open failed: ENOENT (No such file or directory)` occurring when downloading gallery artwork on Android 10+ due to Scoped Storage directory restrictions.
- **Proper MediaStore Target:** Switched from `MediaStore.Images` (which rejects `DIRECTORY_DOWNLOADS` on Android 10) to `MediaStore.Downloads.EXTERNAL_CONTENT_URI` for `Download/Kitsugi/Images`, with `Pictures/Kitsugi/Images` fallback.
- **Graceful Multi-Tier Fallback:** Implemented seamless fallback to app-specific external storage (`getExternalFilesDir`) if public storage creation is restricted on customized OEM ROMs, guaranteeing zero download failures.
- **Manifest Storage Compatibility:** Added `requestLegacyExternalStorage="true"` and extended `WRITE_EXTERNAL_STORAGE` to `maxSdkVersion=29`.
- **Immediate Gallery Indexing:** Invoked `MediaScannerConnection` after write so downloaded media shows up immediately in system Gallery and Photos apps.

### 📦 2. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.212-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.211)

### 🎌 1. Kapsamlı Bangumi Başlık ve İsim Yerelleştirmesi (Bangumi Localization)
- **Merkezi İsim Yerelleştirici (`BangumiNameLocalizer`):** Bangumi'den gelen İngilizce (English), Romaji, Japonca/Özgün (Native), Çince ve alternatif adları (`alias`) koruyan merkezi yerelleştirme katmanı eklendi.
- **Kullanıcı Başlık Dili Tercihi:** Başlık dili tercihi (İngilizce / Romaji / Japonca-Özgün) Bangumi için de tam olarak uygulanır; eksik alanlarda güvenli geri dönüş zinciri çalışır.
- **Çince Önceliği Kaldırıldı:** Bölüm isimlerinde ve varsayılan konu başlıklarında Çince ismin zorunlu önceliği kaldırıldı; orijinal ve İngilizce başlıklar ön plana çıkarılır.
- **Bağımsız İngilizce İsim Desteği:** Karakter ve yapım ekibi modellerinde İngilizce ve Romaji adlar birbirinden ayrıldı.
- **Tüm Ekranlarda Aktif:** Arama, keşfet, medya detayları, karakterler, seslendirmenler, ekip, ilişkiler, öneriler, profil/favoriler, içe aktarma ve Android TV ekranlarının tamamında yerelleştirilmiş isimler uygulandı.

### 🎮 2. Oynatıcı Arayüzü, Tema Uyumu ve Media3 ↔ MPV Paritesi
- **Sabit Gösterge Konumları:** Parlaklık göstergesi ekranın sağında, ses göstergesi solunda sabitlendi (kaydırma jest bölgeleri kullanıcının tercihine göre çalışmaya devam eder).
- **Yenilenen Altyazı Stil Paneli:** Yatay modda ekran dışına taşma sorunu giderildi; panel ekran sınırlarına sabit, kaydırılabilir ve çentik/sistem çubuklarını (safe-area) dikkate alacak şekilde yeniden yapılandırıldı.
- **Uygulama Teması ve Vurgu Rengi (`PlayerAccentTheme`):** Oynatıcı artık seçili tema rengini ve AMOLED modunu alıyor; diyalog, panel, kaydırıcı, buton ve sekmelerdeki sabit renkler seçili tema vurgu rengine uyarlandı.
- **Dahili Oynatıcı Yedekleme Zinciri:** Ana oynatıcıda (Media3) oynatma hatası meydana geldiğinde doğrudan ikinci dahili motor olan MPV'ye geçilir (harici uygulamaya düşme kaldırıldı). Motor geçişinde video sıfırdan başlamaz, kalınan saniyeden devam eder.
- **Ortak Altyazı Öncelik Politikası (`SubtitleSelectionPolicy`):** Hem Media3 hem MPV için tek kural: Gömülü Türkçe > Harici Türkçe > Tercih edilen diğer diller. `tr-TR` ve `tr_TR` gibi bölgesel etiketler Türkçe olarak tanınır.
- **Altyazı ve Ses Düzeltmeleri:** Önbellekteki harici altyazıların bozuk `file://$sub.url` yolu düzeltildi; harici altyazılar benzersiz ID ile eşleştirilir. MPV motoruna ayrı ses akışı (`audioUrl`) harici parça olarak eklendi.

### 🌐 3. CloudStream & Akış İyileştirmeleri
- `domain_fixes.json` blocked listesi temizlendi; Türk eklentileri üzerindeki sessiz atlama kapıları kaldırılarak gerçek hata durumu kartlarda gösterildi.

### 📦 4. Dağıtım
- Kullanıcı talimatı doğrultusunda **yalnızca FOSS varyantı** (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.211-foss.apk`). GMS kesinlikle hariç tutuldu.

---

## 🇬🇧 English (v2.4.211)

### 🎌 1. Comprehensive Bangumi Title and Name Localization
- **Central Name Localizer (`BangumiNameLocalizer`):** Dedicated localization engine preserving Bangumi English, Romaji, native/Japanese, Chinese, and alias records.
- **User Language Preference:** The global title language preference (English, Romaji, Native/Japanese) is now fully applied across all Bangumi surfaces with graceful fallback logic.
- **Removed Chinese Default Bias:** Forced Chinese title precedence on episode titles and main subjects has been removed in favor of user preferences.
- **Distinct English Names:** Added dedicated English naming fields for character and staff models so English and Romaji are no longer conflated.
- **Omnipresent UI Support:** Applied across search, explore, media details, characters, voice actors, staff credits, relations, recommendations, profiles/favorites, imports, and Android TV screens.

### 🎮 2. Player UI, Theme Accent & Media3 ↔ MPV Parity
- **Fixed Indicator Placement:** Brightness indicator is fixed on the right screen edge, volume indicator on the left (swipe gesture regions remain configurable).
- **Redesigned Subtitle Style Panel:** Eliminated horizontal overflow in landscape mode; panel is now bounded, scrollable, and aware of notches and system bars.
- **Dynamic Theme Accent (`PlayerAccentTheme`):** Player now inherits the active app theme color and AMOLED settings; hardcoded colors in sheets, dialogs, sliders, and buttons replaced with the chosen accent color.
- **Internal Engine Fallback Chain:** Playback failures on the primary engine (Media3) automatically switch to the secondary internal engine (MPV) rather than dropping to an external player. Playback resumes from the exact failure position.
- **Shared Subtitle Selection Policy (`SubtitleSelectionPolicy`):** Unified policy across both Media3 and MPV: Embedded Turkish > External Turkish > Other preferred languages. Regional language tags (e.g. `tr-TR`, `tr_TR`) properly recognized.
- **Track & Path Fixes:** Resolved broken `file://$sub.url` paths for cached external subtitles; external tracks identified by unique IDs. Added separate audio stream (`audioUrl`) loading support to MPV.

### 🌐 3. CloudStream & Streaming Refinements
- Cleared broken provider blocklists in `domain_fixes.json`, removed silent skip gates, and surfaced diagnostic error messages.

### 📦 4. Distribution
- Strictly built and released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.211-foss.apk`). GMS is completely excluded.

---

## 🇹🇷 Türkçe (v2.4.210)

### 🎌 1. Bangumi Profil Sekmesi ve Koleksiyon Yönetimi
- **Profil Seçicisine Eklendi:** Profil kaynağı seçicisine (Profile Source Picker) "Bangumi" seçeneği eklendi. Hesap bağlıysa `@kullanıcıadı` görüntülenir.
- **Kapsamlı Profil Başlığı:** Kullanıcı avatarı, takma adı, kullanıcı adı, kişisel imzası (`sign`) ve tek tıkla açılan "bgm.tv profili" butonu.
- **Koleksiyon Listesi ve Filtreler:** Tür (Anime, Kitap/Manga, Oyun, Müzik, Dizi/Film) ve izleme durumu filtreleri, özet koleksiyon sayaçları, bölüm/cilt ilerleme göstergesi ve kullanıcı puanı.
- **Favori Karakterler ve Kişiler:** Kullanıcının Bangumi'de favorilediği karakterler ve kişiler profil ekranında listelenir.
- **Doğrudan Detay Ekranı:** Anime ve manga koleksiyon kartlarına dokunulduğunda doğrudan uygulama içi detay ekranı açılır.
- **Bildirimler:** Resmi Bangumi API'sinde bildirim ucu bulunmadığı için uygulama içi WebView ile doğrudan ve güvenli `bgm.tv/notify/all` sayfası açılır.

### 👤 2. Uygulama İçi Karakter ve Kişi Detayları & Favori Eşitleme
- **Uygulama İçi Detay Sayfaları:** Bangumi karakter ve kişi (yapım ekibi/seiyuu) sayfaları artık tarayıcıya yönlendirilmeden doğrudan uygulama içinde açılır (`KitsugiBangumiCreditsClient` ve `KitsugiBangumiDetailClient`).
- **Bangumi Favori (Kalp) Entegrasyonu:** Bangumi hesabı bağlıyken karakter veya kişi sayfasındaki favori durumu canlı olarak Bangumi sunucusuyla eşitlenir; ekleme ve çıkarma anında yazılır, ağ hatasında güvenli bir şekilde eski haline döner.
- **Dolu Karakter ve Ekip Sekmeleri:** Bangumi konularında "Karakterler" ve "Ekip" sekmeleri v0 OpenAPI ve p1 web uçlarıyla eksiksiz doldurulur.
- **Doğru Paylaşım Bağlantıları:** Karakter ve kişi paylaş butonları artık `bgm.tv/character/{id}` ve `bgm.tv/person/{id}` bağlantılarını üretir.

### 🎮 3. Oynatıcı Jest Bölgeleri, PiP Butonları ve Ses Yönetimi
- **Kaydırıcı ve Dokunma Hizalaması:** Ekrandaki kaydırıcı yerleşimiyle dokunma bölgeleri eşitlendi: Sol yarı parlaklık, sağ yarı ses (varsayılan). Ayar menüsündeki yön metinleri düzeltildi.
- **Çalışan PiP Tuşları:** Mini oynatıcı (PiP) penceresindeki Oynat, Duraklat ve Sonraki Bölüm tuşları Compose oynatıcı köprüsüne (`PipPlayerCallback`) bağlandı.
- **Arka Plan Ses Kesilmesi:** PiP kapatıldığında veya uygulama arka plana alındığında sesin çalmaya devam etmesi `onStop` ve `onDestroy` yaşam döngüsü kontrolleriyle engellendi.

### 🌐 4. CloudStream Kaynak Akışı ve Domain İyileştirmeleri
- **Sessiz Atlama Kapıları Kaldırıldı:** `KNOWN_BROKEN_DOMAINS` ve `domain_fixes.json` blocked listesi eşleştiğinde eklentiler artık peşinen atlanıp sıfır sonuca düşürülmüyor; her zaman denenir ve gerçek hata (DNS/Cloudflare/timeout vb.) UI kartında kullanıcıya bildirilir (`CsPluginStatusTracker.recordSkip`).
- **Domain Listesi Çelişkileri Çözüldü:** `4kfilmizlesene.nl` ve `666filmizle.site` ölü domainler listesinden çıkarıldı.
- **Temizlenen Blocked Listesi:** `domain_fixes.json` dosyasındaki gereksiz yere TR eklentilerini öldüren blocked listesi boşaltıldı (+18 filtreleme uygulamada `ADULT_PLUGINS` ile ayrı olarak yönetilir).
- **Gerçek Hata Gösterimi:** Boş akış döndüğünde "Bu anime için akış bulunamadı" yerine `CsPluginStatusTracker`'dan gelen gerçek sebep gösterilir.
- **Taze Domain Önceliği:** Dinamik güncel domain tablosundan gelen taze domainler, sabit `KNOWN_BROKEN_DOMAINS` listesi tarafından engellenmez.
- **Revert Kararlılığı:** Özgün domaine dönüldükten sonra aynı oturumda yerleşik tablonun tekrar zorlanması engellendi.
- **Güncelleme Geri Çekilme (Backoff):** 404 veren eklentiler için 12 saatlik bekleme süresi uygulandı (`CsAutoUpdateBackoff`).

### 🛠️ 5. Gelişmiş Çökme Teşhisi ve Oturum Denetimi (Crash Diagnostics Overhaul)
- **Native İz Genişletmesi:** Native çökme yığın izi sınırı 48'den 96 çerçeveye çıkarıldı; PC (Program Counter) ve LR (Link Register) bilgileri eklendi.
- **Bellek Haritası Desteği (`/proc/self/maps`):** Çökme anında bellek haritası kaydedilerek native adreslerin ilgili `.so` dosya adına ve ofsetine çözülebilmesi sağlandı.
- **Doğru Süreç ve Ekran Tespiti:** Teşhis raporundaki "Ölüm Sebebi" artık sadece çöken oturumun PID'sine odaklanır; "Son Ekran" bilgisi eylem izinden doğrulanarak gösterilir.
- **Genişletilmiş Kanıt Penceresi:** Sessiz kapanmalarda native iz okuma penceresi 30 dakikadan 7 güne çıkarıldı.

### 📦 6. Dağıtım
- Kullanıcı talimatı doğrultusunda **yalnızca FOSS varyantı** (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.210-foss.apk`). GMS kesinlikle hariç tutuldu.

---

## 🇬🇧 English (v2.4.210)

### 🎌 1. Bangumi Profile Tab & Collection Management
- **Added to Profile Picker:** Bangumi is now available in the profile source selector, displaying `@username` when connected.
- **Full Profile Header:** Displays user avatar, nickname, username, personal signature (`sign`), and a direct link to open the bgm.tv web profile.
- **Collections & Filters:** Category (Anime, Manga/Book, Game, Music, Real/Drama) and status filters, collection counters, episode/volume progress, and user ratings.
- **Favorite Characters & People:** Highlights the user's favorited Bangumi characters and staff members directly on the profile screen.
- **In-App Navigation:** Tapping anime or manga entries seamlessly opens the existing in-app media detail page.
- **Notifications:** Since Bangumi does not provide a public notification API endpoint, notifications open via an in-app WebView targeting `bgm.tv/notify/all`.

### 👤 2. In-App Character & Staff Detail Pages & Bangumi Favorites
- **Native In-App Views:** Bangumi character and person/staff pages now render natively in-app (`KitsugiBangumiCreditsClient` & `KitsugiBangumiDetailClient`) rather than opening externally.
- **Live Favorite Toggle:** When logged in with Bangumi, tapping the favorite heart on character/person screens syncs directly to Bangumi with rollback on error.
- **Populated Credits Tabs:** Subjects on Bangumi now populate both "Characters" and "Staff" tabs via official v0 OpenAPI with p1 fallbacks.
- **Accurate Share URLs:** Sharing characters or staff members now correctly generates `bgm.tv/character/{id}` and `bgm.tv/person/{id}` links.

### 🎮 3. Player Gesture Zones, PiP Controls & Audio Lifecycle
- **Slider & Touch Zone Alignment:** Touch regions now match on-screen sliders: Left half controls brightness, right half controls volume (default). Settings labels aligned.
- **Functional PiP Actions:** Mini player (PiP) Play, Pause, and Skip Next buttons are wired to the player engine via `PipPlayerCallback`.
- **Background Audio Stop:** Playback reliably pauses upon closing PiP or backgrounding the activity via `onStop` and `onDestroy`.

### 🌐 4. CloudStream Stream & Domain Enhancements
- **Eliminated Silent Skip Gates:** Extensions matching `KNOWN_BROKEN_DOMAINS` or `domain_fixes.json` blocked list are no longer prematurely skipped; all extensions are executed and real errors (DNS, Cloudflare, timeouts) are surfaced directly on stream cards (`CsPluginStatusTracker.recordSkip`).
- **Domain Contradictions Resolved:** `4kfilmizlesene.nl` and `666filmizle.site` removed from broken domain blocklist.
- **Cleared Blocked List:** Cleared `domain_fixes.json` blocked list that previously incapacitated legitimate Turkish streaming providers (+18 filtering is handled strictly via `ADULT_PLUGINS`).
- **Real Diagnostic Error Display:** When stream discovery yields empty results, the actual root cause from `CsPluginStatusTracker` is displayed instead of a generic "No streams found" placeholder.
- **Fresh Domain Priority:** Fresh remote domains override the hardcoded `KNOWN_BROKEN_DOMAINS` blocklist.
- **Stable Revert Protection:** Restored domains are protected against immediate builtin overrides in the same session.
- **Update Backoff:** 12-hour retry backoff on 404/missing extensions (`CsAutoUpdateBackoff`).

### 🛠️ 5. Enhanced Crash Diagnostics & Session Supervisor
- **Extended Native Trace:** Increased native stack frames limit from 48 to 96, capturing PC and LR registers.
- **Memory Maps Dump (`/proc/self/maps`):** Recorded address maps allow translating raw crash addresses to `.so` library names and offsets.
- **Precise PID & Last Screen Tracking:** Root cause attribution strictly targets the crashing PID session; the last visited screen is inferred from the action breadcrumbs.
- **Extended Evidence Window:** Silent crash native trace retention window prolonged to 7 days.

### 📦 6. Distribution
- Strictly built and released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.210-foss.apk`). GMS is completely excluded.

---

## 🇹🇷 Türkçe (v2.4.209)

### 🎌 1. Bangumi Liste İçe Aktarma ve Eşitleme 404 Kök Neden Çözümü
- **Kullanıcı Adı Çözümlemesi:** Bangumi sunucusunda `"-"` takma adının yalnızca yazma uçlarında geçerli olması ve okuma ucunda (`GET /v0/users/-/collections`) 404 vermesi sorunu çözüldü. Artık saklanan kullanıcı adı, yoksa `/v0/me`, o da yoksa sayısal ID dinamik olarak çözümlenerek gerçek kullanıcı adına istek atılıyor.
- **Koleksiyon Uçları Güvenliği:** `getUserCollections`, `getAllUserCollections` ve `getUserCollection` fonksiyonlarındaki hatalı `"-"` varsayılan değeri kaldırılarak yanlışlıkla 404 hatasına düşülmesi engellendi.
- **Durum Kontrolü:** `BangumiSyncManager.fetchRemoteStatus` içerisindeki aynı hata giderilerek uzaktaki izleme/okuma durumu güvenle alınır hale getirildi.

### 🔍 2. Bangumi Arama Motoru İyileştirmeleri ve Yedek Uç
- **Yetişkin İçerik (NSFW) Filtresi:** Yetişkin içerik açıkken sunucuya metin (`"include"`) olarak gidip 400 hatası oluşturan `filter.nsfw` parametresi, sunucunun beklediği JSON boolean formatına dönüştürüldü (`false` veya filtresiz).
- **Sayfa Boyutu ve Atlama Düzeltmesi:** Sunucu limiti 20 olduğu halde istemcinin 24 kayıt istemesi ve offset hesaplamasında her sayfada 4 kaydın atlanması sorunu giderildi; tüm Bangumi aramalarında sayfa boyutu 20'ye sabitlendi.
- **Eski Uç Yedeği (Legacy Search Fallback):** v0 deneysel arama ucu hata verirse veya boş dönerse `GET /search/subject/{q}` eski arama ucu otomatik devreye girer.
- **Şeffaf Hata Bildirimi:** Arama hataları artık "Sonuç bulunamadı" ardına gizlenmez; sunucudan dönen gerçek hata mesajı ekranda gösterilir ("Tümü" karma rafında da anime ve manga hataları yüzeye çıkarılır).

### 🎬 3. Kapsamlı Bangumi Detay Sayfası ve Çapraz Kimlik Eşleştirme (`KitsugiBangumiDetailClient`)
- **Doğrudan Yerel Veri Doldurma:** Bilgi, karakterler (seslendirmenli), yapım ekibi, ilişkiler, öneriler, puan dağılım grafikleri, incelemeler/yorumlar ve bölümler doğrudan Bangumi'nin kendi verisinden (v0 ve p1 uçları) anında doldurulur.
- **Karakter ve Kişi Detay Sayfaları:** Bangumi kaynaklı karakter ve yapım ekibi detay sayfaları p1 uçları üzerinden eksiksiz çalışır hale getirildi.
- **Akıllı Çapraz Kimlik Eşleme:** Bangumi kaydı başlık, yıl, bölüm sayısı ve format puanlamasıyla AniList, MAL ve ARM üzerinden TMDB/Kitsu/TVDB/IMDb ile güvenli şekilde eşleştirilir.
- **Zenginleştirilmiş Galeri:** Resimler sekmesine Fanart.tv, TMDB, Shikimori, MAL ve AniList görselleri dahil edildi. `lain.bgm.tv` kaynaklı aynı görselin farklı boyut kopyaları kanonik anahtar ile tekil görsele indirildi.
- **Room Önbellek Sürümlemesi:** Önceki sürümlerin eksik/hatalı Kitsu aramasıyla önbelleğe aldığı satırların okunmasını engellemek için önbellek anahtarına `_bgm1` sürüm eki getirildi.
- **Bölüm Puanları ve Logolar:** MDBList, logo ve bölüm puanları eşleşen çapraz kimlikler üzerinden kusursuz çalışır.

### 📦 4. Dağıtım
- Kullanıcı talimatı doğrultusunda **yalnızca `foss` varyantı** derlendi ve GitHub'a yüklendi (`Kitsugi-Beta-v2.4.209-foss.apk`). GMS kesinlikle hariç tutuldu.

---

## 🇬🇧 English (v2.4.209)

### 🎌 1. Bangumi Collection Import & Sync 404 Root Cause Fix
- **Dynamic Username Resolution:** Resolved the issue where Bangumi rejected the `"-"` alias on read endpoints (`GET /v0/users/-/collections`) with 404 Not Found. Real username is now dynamically determined via stored name, `/v0/me`, or numeric ID.
- **Safe Collection Endpoints:** Removed the fallback `"-"` default in `getUserCollections`, `getAllUserCollections`, and `getUserCollection` to prevent accidental regressions.
- **Sync Status Audit:** Fixed identical issue in `BangumiSyncManager.fetchRemoteStatus` so remote progress sync works reliably.

### 🔍 2. Bangumi Search Improvements & Legacy Fallback
- **NSFW Boolean Filtering:** Fixed `filter.nsfw` sending string `"include"` which caused HTTP 400 Bad Request; now correctly sends JSON boolean or omits when inactive.
- **Page Size Alignment:** Standardized page size and pagination offsets to 20 to match Bangumi's hard limit, preventing 4-item skipping and false "no more pages" triggers.
- **Legacy Fallback Endpoint:** If experimental v0 search fails or returns empty, fallback to legacy `GET /search/subject/{q}` seamlessly engages.
- **Surfaced Error Messages:** API errors are no longer silently masked as "No results found"; descriptive server messages are displayed across both isolated and mixed search views.

### 🎬 3. Full Native Bangumi Media Details & Cross-ID Enrichment (`KitsugiBangumiDetailClient`)
- **Native Details & Tabs:** Information, characters with voice actors, staff, relations, recommendations, score distributions, reviews/comments, and episodes load immediately using native Bangumi APIs (v0 and p1).
- **Character & Staff Pages:** Native Bangumi character and person profile pages are fully supported via p1 endpoints.
- **Cross-ID Resolution:** Bangumi subjects are matched with AniList, MAL, and ARM (TMDB/Kitsu/TVDB/IMDb) using strict title, year, episode count, and format scoring.
- **Enhanced Gallery & Dedup:** Gallery tabs are enriched with fanart.tv, TMDB, Shikimori, MAL, and AniList images. Duplicate covers from `lain.bgm.tv` across various size buckets are deduplicated into single items.
- **Room Cache Busting:** Added `_bgm1` suffix to Bangumi detail cache keys, preventing stale/erroneous legacy Kitsu cache entries from loading.
- **Episode Ratings & Logos:** MDBList, clearlogos, and episode rating curves now correctly resolve using the linked cross-IDs.

### 📦 4. Distribution
- Built and published strictly as a **FOSS release** (`Kitsugi-Beta-v2.4.209-foss.apk`). GMS flavor is omitted.

---

## 🇹🇷 Türkçe (v2.4.208)

### 🏷️ 1. AniList İsim Dili (Romaji / İngilizce / Japonca) Yerelleştirmesi
- **Karakter, Seslendirmen & Yapım Ekibi (Staff) İsimleri:** AniList'ten gelen kişi ve karakter adları artık kullanıcının tercih ettiği isim diline (`ROMAJI`, `ENGLISH`, `NATIVE`, `JAPANESE_STAFF`) göre dinamik olarak gösteriliyor.
- **Latin & Orijinal Ad Uyumu (`AniListPersonName.kt`):** AniList sorgularında `userPreferred` yerine `full`, `native` ve alternatif adlar çekilerek Romaji/İngilizce tercihinde Latin harfli isimler önceliklendirildi. Latin harfli karşılık bulunamadığında orijinal ad korunur.
- **Temiz Başlık & Alt Başlık:** Japonca/Yerel ad seçildiğinde alt başlıktaki mükerrer isimler temizlenir ("Karakter" veya doğrudan rol açıklaması gösterilir).
- **Kapsam:** Arama sonuçları, yapım detay sekmeleri (`CharactersTab`), API detay sayfası, yerel kütüphane detay sayfası, karakter detayı ve yapım ekibi detay sayfalarının tümü güncellendi.

### 📜 2. Akıllı Kaydırma Konumu Koruma (Scroll Retention)
- **Detaydan Geri Dönüş Güvencesi:** Bir listenin ortasından detay sayfasına girilip geri dönüldüğünde ekranın başa sarması ve kaydırma konumunun sıfırlanması tamamen önlendi.
- **Listem (My List):** 7 kütüphane sekmesinin (Tümü, İzlediklerim, İzleyeceklerim, Bıraktıklarım vb. ve 🎌 Bangumi) her biri için kaydırma konumu ekran kapansa dahi bağımsız saklanır (`savedTabIndices`, `savedTabOffsets`, `restoredTabs`).
- **Arama & Çoklu Platform Rafları:** Arama sayfası ve kaynağa özel arama (`SourceSearchPage`) için `rememberRetainedLazyListState` entegre edildi. Sonuçlar henüz yüklenirken veya yenilenirken listenin 0'a sıfırlanması engellendi. `SourceSearchOwner` ile izole ViewModel durumu geri dönüşte korunur.
- **Keşfet & Tam Ekran Izgaralar:** `FullScreenMediaGridPage` ve `AddonFullScreenGridPage` `SaveableStateProvider` ve derinlik takibi (`openingStackDepth`) ile korunarak detaydan dönüşte tam kaldığı noktaya döner.

### ✨ 3. Kartlarda Neon Degrade Çerçeve ve Parıltı (List Grid Glow)
- **Kare Izgara Kartları (`KitsugiMediaEntryCard`):** Kare poster görünümündeki kartlara zarif degrade çerçeve ve neon parıltı efekti (`kitsugiNeonGlow`) uygulandı. Listem sekmelerine ve Favorilerim ekranına modern, canlı bir görünüm kazandırıldı.
- **Kullanıcı Listeleri (`UserMediaListCards`):** Kaynak kullanıcı profil listelerindeki hem kare (grid) hem yatay satır (row) kartlarına degrade çerçeve ve parıltı stili dahil edildi.

### 🛠️ 4. MyAnimeList / Jikan Detay Sekmeleri Kök Neden Çözümü (`MalJikanMediaSupport`)
- **Sekme Donması ve Boş Kalma Çözümü:** MAL kaynaklı detay sayfalarında Karakterler, Ekip, Öneriler, İlişkiler, İstatistikler ve Yorumlar sekmelerindeki iskelet takılması ve boş kalma sorunu giderildi.
- **Tek Ortak Kural:** `mal`, `jikan`, `myanimelist` kimlik uzayları `MalJikanMediaSupport.canonicalSource` altında birleştirildi; `Movie` ve `TvShow` tiplerinin Jikan uç noktalarında (`anime`) veya AniList tipinde (`ANIME`) yanlışlıkla `MANGA`ya düşmesi engellendi.
- **Akıllı Hiyerarşi:** Jikan birincil kaynak yapıldı; AniList'ten toplu zenginleştirme için 25 saniye bekleme kaldırıldı (veriler anında ekrana gelir). Jikan boş döndüğünde ise karmaşık ARM araması öncesi doğrudan aynı MAL ID'si ile AniList denenerek anında sonuç üretilir.

### 🎬 5. Çökme ve Kurtarma Pencerelerinde Canlı Animasyon (`KitsugiCrashAnimation`)
- **Canlı Çökme Görseli:** Çökme ekranı (`KitsugiCrashActivity`) ve başlangıç kurtarma penceresinde (`KitsugiCrashRecoveryDialog`) statik hata ikonu yerine `res/raw/crash_animation.mp4` döngüsel animasyonu eklendi.
- **Sıfır Ek Yük ve Güvenlik:** Android'in yerel `TextureView` ve `MediaPlayer` altyapısı kullanılarak sessiz ve kesintisiz döngü sağlandı; donanım düzeyinde hata koruması sayesinde çökme ekranının kendisinin hata vermesi tamamen engellendi.

### 📦 6. Dağıtım
- Kullanıcı talimatı doğrultusunda **yalnızca `foss` varyantı** derlendi ve GitHub'a yüklendi (`Kitsugi-Beta-v2.4.208-foss.apk`). GMS kesinlikle hariç tutuldu.

---

## 🇬🇧 English (v2.4.208)

### 🏷️ 1. AniList Person Name Localization (Romaji / English / Native)
- **Characters, Voice Actors & Staff:** AniList person and character names now dynamically conform to the user's selected name/title language preference (`ROMAJI`, `ENGLISH`, `NATIVE`, `JAPANESE_STAFF`).
- **Latin & Native Fallback (`AniListPersonName.kt`):** Queries now request `full`, `native`, and alternate names instead of solely relying on `userPreferred`. When Romaji or English is selected, Latinized names take precedence; if no Latin name exists, the original name is safely preserved.
- **Clean Subtitles:** Redundant name prefixes in subtitles when native names are active are cleanly stripped (displaying "Character" or role details).
- **Universal Coverage:** Applied across Search results, Media Detail tabs (`CharactersTab`), API and Local library detail pages, Character Detail, and Staff Detail pages.

### 📜 2. Smart Scroll Retention Across All Lists
- **Seamless Navigation Return:** Returning from a detail screen to any list or search view now accurately preserves scroll position instead of snapping to top.
- **My List Tabs:** Each of the 7 library tabs (including 🎌 Bangumi) retains its scroll index and pixel offset independently across tab switching and detail navigation (`savedTabIndices`, `savedTabOffsets`, `restoredTabs`).
- **Search & Source Search:** Integrated `rememberRetainedLazyListState` to prevent intermediate empty/loading states from clamping scroll offsets to 0. `SourceSearchPage` leverages `SourceSearchOwner` to keep isolated ViewModel state alive during nested navigation.
- **Explore & Full-Screen Grids:** Wrapped `FullScreenMediaGridPage` and `AddonFullScreenGridPage` in `SaveableStateProvider` with depth tracking (`openingStackDepth`) for accurate back-stack restoration.

### ✨ 3. Neon Gradient Border & Glow on Media Cards
- **Grid Cards (`KitsugiMediaEntryCard`):** Added subtle neon glow and gradient border accents (`kitsugiNeonGlow`) to poster grid media cards across My List and Favorites.
- **User Profile Lists (`UserMediaListCards`):** Extended neon gradient styling to both grid and row cards within remote user profile lists.

### 🛠️ 4. MyAnimeList / Jikan Detail Tabs Unification (`MalJikanMediaSupport`)
- **Stall & Empty Tab Resolution:** Fixed root causes where Characters, Staff, Recommendations, Relations, Stats, and Reviews tabs on MAL-sourced items remained stuck on skeletons or empty.
- **Unified Identity Space:** Normalized `mal`, `jikan`, and `myanimelist` under `MalJikanMediaSupport.canonicalSource`. Corrected `Movie` and `TvShow` mapping to prevent accidental `MANGA` endpoints.
- **Instant Response Flow:** Made Jikan the primary responsive source without waiting 25s for AniList batch cover enrichment. If Jikan is empty, AniList is queried with the exact MAL ID before escalating to ARM/Shikimori resolvers.

### 🎬 5. Smooth Looping Animation for Crash & Recovery Screens (`KitsugiCrashAnimation`)
- **Dynamic Error Graphic:** Replaced static bug icons in `KitsugiCrashActivity` and `KitsugiCrashRecoveryDialog` with a sleek looping video animation (`res/raw/crash_animation.mp4`).
- **Zero Overhead & Robust Fallbacks:** Built on Android's native `TextureView` and `MediaPlayer` for seamless looping, zero audio intrusion, and complete crash immunity.

### 📦 6. Distribution
- Per user specification, **only the `foss` release APK** was built and released (`Kitsugi-Beta-v2.4.208-foss.apk`). GMS is strictly excluded.

---

## 🇹🇷 Türkçe (v2.4.207)

### 🎌 1. Bangumi (bgm.tv) Tam Platform Entegrasyonu (7. Kaynak)
- **OAuth 2.0 & Kimlik Doğrulama:** `kitsugi://bangumi-auth` ve `aniyomi://bangumi-auth` yönlendirme şemalarıyla modern yetkilendirme altyapısı kuruldu. `BangumiAuthStore` ve `BangumiAuthManager` ile "1-Tık Otomatik Giriş" ve manuel yetki kodu girme seçenekleri sağlandı.
- **Kütüphane & İçe Aktarma:** Bangumi koleksiyon durumları (想看/在看/看过/搁置/抛弃 - İzlemek İstiyorum / İzliyorum / İzledim / Beklemede / Bıraktım), 0-10 puanlama sistemi ve bölüm bazlı ilerleme (打格子) Kitsugi kütüphanesine içe aktarma (`BangumiImportManager`) ve iki yönlü senkronizasyon (`BangumiSyncManager`) ile tam entegre edildi.
- **Listem Sekmesi (7. Sekme):** Listem ekranına 7. sekme olarak 🎌 Bangumi kütüphanesi eklendi (`MyListLibraryGrouping`, `MyListScreen`, `MyListComponents`, `MyListEmptyState`). Özel marka rengi (`#F09199`), rozetler, sayaçlar ve doğrudan sekme içinden açılan hesap bağlama diyaloğu eklendi.
- **Keşfet & Arama Motoru:** Keşfet ekranında 7. platform olarak Bangumi desteği (`AllSourcesExplore`, `ExplorePlatformToggle`, `ExploreViewModel`), Arama motorlarında Bangumi motoru (`KitsugiBangumiClient`, `SearchViewModel`), anime/manga gelişmiş filtreleme, sıralama, karakter ve personel arama kabiliyeti kazandırıldı.
- **Görsel Rozet & Marka:** Gerçek Bangumi vektör logosu (`ic_logo_bangumi.xml`), Hero rozetleri, detay sayfası başlıkları ve galeri rozetleri güncellendi.

### ⚠️ 2. Çapraz Eşitleme (Cross-Sync) Güvenlik & Hukuki Sorumluluk Reddi (Disclaimer)
- **Merkezi Sorumluluk Reddi (`CrossSyncDisclaimer.kt`):** Çapraz eşitlemenin deneysel (beta) bir özellik olduğunu, eşleştirmelerin otomatik yapıldığını ve hiçbir eşleştirmenin %100 doğruluk garantisi taşımadığını; yanlış eşleşme, liste karışması veya platform yaptırımlarından (kısıtlama, askıya alma, hesap yasağı vb.) sorumluluk kabul edilmediğini belirten açık ve kapsamlı uyarı metinleri eklendi.
- **Görünür Uyarılar:**
  - Hesap bağlantıları Cross-Sync satırına "Deneysel ·" etiketi ve kısa uyarı eklendi.
  - Çok Yönlü Eşitleme ve Cross-Sync ayar sayfasına bilgi kartı içine ayrıntılı uyarı, Hızlı İşlem bölümü altına kısa not eklendi.
  - Eşitleme penceresinde (`KitsugiCrossSyncDialog`) güvenlik notunun altına ayrıntılı uyarı yerleştirildi.
- **Çoklu Aday Grup Akrabalık Koruması (`AuthViewModel`):** Birden fazla aday grup bulunduğunda, tekil ortak kimlikle seçilen grup için yıl uyumu (`CrossSyncIdentityGuard.yearsCompatible`) ve başlık akrabalığı kontrolü eklendi; uyuşmayan kayıtların yanlış birleştirilmesi ve başka hesaplardaki doğru kayıtların bozulması engellendi (`identityReviewRequired = true`).
- **Rapor Teşhis Etiketleri:** Eşitleme teşhisinde iç kimlik alanları (`300M+` Kitsu, `100M+` AniList) açıkça etiketlenerek (`describeMalIdField`) MAL kimliği sanılması önlendi.

### ⚡ 3. Jikan Hız Limiti, Shikimori Başlık Dili & Kitsu Paralel İstek İyileştirmeleri
- **Jikan Hız Limiti & 429 Koruması (`KitsugiApiBase`):** Jikan için saniyede 3 ve dakikada 55 istek (kayan pencere) limiti getirildi. 429 yanıtı alındığında host geçici olarak bekletilir ve yeniden deneme sayısı 3'e çıkarılarak sekme/karakter boş kalma sorunları giderildi.
- **Shikimori Rusça Başlık Çözümü (`ShikimoriTitleResolver`):** Shikimori GraphQL API üzerinden tek istekte İngilizce ve Japonca başlıklar çekilerek, kullanıcının ayarladığı tercih diline (Romaji / İngilizce / Japonca) göre gösterilmesi sağlandı.
- **İlişki Tipi Çevirileri (`KitsugiTranslations`):** Shikimori ve Jikan ilişkilerindeki "Sequel", "Side story", "Adaptation", "Prequel" gibi ilişki türleri Türkçeye çevrildi. Jikan ilişkilerine AniList'ten İngilizce/Japonca başlık desteği eklendi.
- **Kitsu Karakter & Seslendirmen Paralel İstekleri (`KitsuClient`):** Karakter ve seslendirmen sayfaları sıralı yerine en fazla 3 eşzamanlı istekle paralel çekilerek sayfa açılış hızı büyük oranda artırıldı.
- **Seslendirmen Birleştirme Zaman Aşımı Optimizasyonu (`KitsugiCharacterClient`):** Kitsu ve Shikimori için seslendirmen bekleme süresi 8 saniyeden 5 saniyeye indirilerek sayfa kilitlenmeleri önlendi.

### 🔍 4. Arama & "Tümünü Gör" Devamlılığı
- `SearchViewModel` ve `SourceSearchPage` arasındaki `openSourceSearch` köprüsü Bangumi motorunu da kapsayacak şekilde yenilendi; raflardan "Tümünü Gör"e tıklandığında sorgu, kapsam ve görünen sonuçlar eksiksiz aktarılır.

### 📦 5. Dağıtım
- Kullanıcı talimatı doğrultusunda **yalnızca `foss` varyantı** derlendi ve yayınlandı (`Kitsugi-Beta-v2.4.207-foss.apk`). GMS kesinlikle hariç tutuldu.

---

## 🇬🇧 English (v2.4.207)

### 🎌 1. Full Bangumi (bgm.tv) Platform Integration (7th Source)
- **OAuth 2.0 & Authentication:** Implemented redirect schemes `kitsugi://bangumi-auth` and `aniyomi://bangumi-auth` with `BangumiAuthStore` and `BangumiAuthManager`, enabling 1-click automatic browser login and manual authorization code entry.
- **Library & Smart Sync:** Full support for Bangumi collections (想看/在看/看过/搁置/抛弃 - Wish/Do/Collect/On Hold/Dropped), 0-10 rating scale, and episode progress grid via `BangumiImportManager` and `BangumiSyncManager`.
- **My List 7th Tab:** Added dedicated 🎌 Bangumi tab to My List (`MyListLibraryGrouping`, `MyListScreen`, `MyListComponents`), complete with signature brand color (`#F09199`), item counters, status chips, and in-tab account connection prompt.
- **Explore & Multi-Engine Search:** Integrated Bangumi as the 7th platform in All Sources Explore (`AllSourcesExplore`, `ExploreViewModel`) and Global Search (`SearchViewModel`, `KitsugiBangumiClient`) with anime/manga advanced filtering, tags, years, and character/staff lookups.
- **Brand Assets & Polish:** Added official vector logo (`ic_logo_bangumi.xml`), hero badges, detail screen headers, and gallery chips.

### ⚠️ 2. Cross-Sync Safety Guards & Legal Disclaimers
- **Centralized Disclaimer (`CrossSyncDisclaimer.kt`):** Clear legal notice that Cross-Sync is an experimental beta feature with no 100% match guarantee, disclaiming liability for mismatched lists, progress desyncs, or upstream platform sanctions (bans, suspensions).
- **Surface Visibility:**
  - Added "Experimental ·" tag and summary disclaimer to Account Connections.
  - Injected full disclaimer inside the Cross-Sync settings info card and quick actions section.
  - Embedded disclaimer directly into the synchronization dialog (`KitsugiCrossSyncDialog`).
- **Multi-Candidate Guard (`AuthViewModel`):** Added year compatibility (`yearsCompatible`) and title kinship checks for single-evidence selections when multiple candidate groups exist, preventing erroneous mergers and cross-account data overwrite (`identityReviewRequired = true`).
- **Diagnostic Clarity:** Explicitly labeled synthetic IDs (`300M+` for Kitsu, `100M+` for AniList) in diagnostics to prevent confusion with real MAL IDs.

### ⚡ 3. Jikan Rate Limiting, Shikimori Titles & Kitsu Concurrency
- **Jikan 429 Prevention (`KitsugiApiBase`):** Enforced a rate limit of 3 req/sec and 55 req/min (sliding window) for Jikan API, with automatic backoff cooldown and 3 retries on HTTP 429.
- **Shikimori English/Japanese Titles (`ShikimoriTitleResolver`):** Resolves English and Japanese titles via Shikimori's GraphQL API instead of defaulting to Russian titles, strictly adhering to user's title language preference.
- **Relation Type Translations (`KitsugiTranslations`):** Translated relation types (Sequel, Side story, Adaptation, etc.) to Turkish. Added AniList title fallback for Jikan relations.
- **Kitsu Concurrency (`KitsuClient`):** Characters and voice actors are now fetched concurrently (up to 3 parallel requests), significantly accelerating page loads.
- **Voice Actor Timeout Optimization (`KitsugiCharacterClient`):** Reduced VA fallback timeout from 8s to 5s for Kitsu and Shikimori to prevent UI stalls.

### 🔍 4. Search Shelf Continuity
- Extended `openSourceSearch` handoff between `SearchViewModel` and `SourceSearchPage` to seamlessly support Bangumi without dropping active query, scope, or cached shelf results.

### 📦 5. Release Distribution
- Built strictly as **FOSS Release** (`Kitsugi-Beta-v2.4.207-foss.apk`) per user instruction. No GMS variant generated.

---

## 🇹🇷 Türkçe (v2.4.206)

### 🛡️ 1. Native & Java Çökme Teşhisi, ANR/OOM Raporlama & Cockroach Çözümü
- **Native Çökme Yakalayıcı (C++ - libkitsugi_crash_handler.so):** Java `UncaughtExceptionHandler`'ın yakalayamadığı native çökmeler (SIGSEGV, SIGABRT, SIGBUS, SIGILL, SIGFPE, SIGTRAP) `_Unwind_Backtrace` ile asenkron sinyal güvenliğinde yakalanır; ham geri iz `native_crash.txt` dosyasına yazılır.
- **Sessiz Ölüm Dedektörü (KitsugiSessionSupervisor):** Uygulama temiz çıkış yapamadan kapandığında (LMKD bellek öldürmesi, ANR sonrası sonlandırma, SIGKILL), bir sonraki açılışta logcat taranarak `unclean_exit.txt` raporu üretilir ve kullanıcıya "Beklenmedik Kapanma" penceresi gösterilir.
- **Eylem İzi (Breadcrumbs):** Son 40 kullanıcı hareketi ve son bulunulan ekran anlık kaydedilir; çökme raporlarında hatanın hangi ekranda ve hangi eylem sırasında gerçekleştiği açıkça görülür.
- **Bağımsız Görevde Açılan Çökme Ekranı:** `Thread.sleep(800)` ana thread bloklaması kaldırıldı. Çökme ekranı (`KitsugiCrashActivity`) ayrı görev (`taskAffinity`) ve süreçte açılarak Android görev temizliğinin çökme ekranını yok etmesi engellendi.
- **Cockroach Sonsuz CPU Döngüsü & Kör Hata Yutma Düzeltildi:** `com.lagradost.cloudstream3` hatalarını körlemesine yutup arayüzü kilitli bırakan mantık düzeltildi; `Looper.loop()` sonlandığında oluşan %100 CPU kilitlenmesi (ANR donmaları) giderildi.
- **Erken Kurulum:** Çökme yakalayıcı `attachBaseContext()` aşamasına çekilerek açılış ve provider çökmeleri de kapsama alındı.
- **Genişletilmiş Teşhis Paylaşımı:** "Geliştiriciye Gönder", tüm teşhis kütüklerini (`crash_log`, `crash_history`, `unclean_exit`, `native_crash`, `breadcrumbs` vb.) tek pakette paylaşır.

### 📋 2. Duplicate Lazy Key Liste Çökmeleri Düzeltildi
- `IllegalArgumentException: Key ... was already used` hatasına yol açabilen 7 arayüz listesi düzeltildi: Ana sayfa ızgarası, modern ana sayfa satırları, yayın takvimi (liste ve ızgara), genişletilmiş medya ızgarası, manga eklenti kaynak listesi, özel liste düzenleyici ve oynatıcı hız seçim sayfası.

### 🖼️ 3. Resim İndirme & Bellek Güvenliği (OOM Önleme)
- **Bellek Koruma (Downsampling):** İndirilen görsellerin bildirim önizlemesi tam boy yerine `inSampleSize` ile küçültülerek decode edilir; 48 MB üzeri devasa görseller filtrelenir.
- **Android 10+ MediaStore Kaydı:** Görseller doğrudan `İndirilenler/Kitsugi/Images` altına MediaStore API ile yazılır (Android 11+ dosya yazma engeli aşıldı).
- Galeri ve indirme adımları eylem izine (breadcrumbs) dahil edildi.

### 🎭 4. Karakter Görselleri & Oyuncu Fotoğrafları İyileştirmeleri
- **Akıllı Başlık Adayları:** TMDB içeriklerinde tüm başlık varyantları (Türkçe, Romaji, İngilizce, Japonca, eşanlamlılar) taranarak doğru anime eşleşmesi bulunur.
- **AniList Karakter Araması Yedeği:** Yapım düzeyinde eşleşmeyen karakterler için doğrudan AniList karakter araması yapılır (`realMalId` doğrulamasıyla).
- **Canlı Çekim Kadrolarında Oyuncu Fotoğrafları Korundu:** Live-action yapımlarda oyuncu fotoğrafları silinmez; `isRealMediaRole` bayrağı ile doğrudan TMDB kişi profili açılır.
- **Detay Sayfasında Anında Görsel (Hint Image):** Karakter sayfasına girildiğinde karttaki görsel anında galeriye ve arka plana alınır, sayfa resimsiz kalmaz.
- **Jikan /pictures Optimizasyonu:** Galeri resim isteği yalnızca MyAnimeList kimlik uzayında çalıştırılır (TMDB 404 çağrıları bitti).

### ⚡ 5. Shikimori Detay Sekmeleri & Hız Optimizasyonları
- **Yerel İlişkiler ve Öneriler:** Shikimori `/similar` ve `/related` uç noktalarıyla tek istekte saniyeler içinde yüklenir; kapaklar toplu çözülür.
- **Ekip Sekmesi Boş Kalma Koruması:** Shikimori personel kaydı bulunmayan yapımlarda gerçek MAL ID'si üzerinden Jikan personel listesi devreye girer.
- **Sonsuz Skeleton Koruması (25 sn Zaman Aşımı):** Sekme yüklemelerine 25 saniye (bölümler için 45 saniye) zaman aşımı ve tek-uçuş (single-flight) koruması eklendi.
- **ID İzolasyonu:** Shikimori ID'sinin MAL ID sanılması engellendi; gerçek MAL ID'si önbellekli çözülür.

### 🎬 6. Detay Sayfası Tasarım & "Bilgiler" Kopyalama Butonları Birleştirmesi
- **Ortak Bilgiler Bileşeni (`DetailInfoValueRow`):** Kütüphane girdisi ve API detay ekranlarındaki "Bilgiler" kartı ortak bileşene kavuşturuldu; her iki ekranda da tüm satırlara (Durum, Başlangıç, Bitiş, Stüdyo, Süre, Yayın, Yaş Sınırı, İngilizce, Japonca vb.) kopyalama butonları eklendi.
- **"Diğer Adlar" Tekil Etiketler & Bağımsız Kopyalama:** "Diğer Adlar" satırında tüm isimleri tek metin halinde birleştirip kopyalama sorunu giderildi. Artık her isim kendi etiketinde (chip) ve bağımsız kopyalama butonuyla yer alır; virgül içeren adlar bozulmaz, boş ve tekrar edenler filtrelenir.
- **Kompakt İzle Butonu:** Sol panelde ve özet bilgi alanında tüm genişliği kaplayan devasa buton yerine içerikle uyumlu (`wrapContent`) şık ve kompakt bir "İzle" butonu tasarlandı.

### 🔍 7. Arama & "Tümünü Gör" Raf Tutarlılığı
- **Raf ile Kaynak Sayfası Uyumu:** Arama ekranında bir raftan "Tümünü Gör"e tıklandığında, raftaki sorgu, arama kapsamı (`SearchScope`) ve mevcut sonuçlar doğrudan `SourceSearchPage`'e aktarılır; sonuçlar kaybolmaz.
- **Tümü Rafından Açıldığında Çift Kapsam (Anime + Manga):** "Tümü" rafında hem anime hem manga listelendiği için, kaynak sayfası açıldığında her iki kapsam da birlikte taranır; rafta görünen içerikler sayfada silinmez.
- **Yenileme Başarısız Olsa Bile İçerik Koruma:** Ağ hatasında raftaki mevcut sonuçlar temizlenmez.
- **MAL Alakasız Popülerler Düşüşü Kaldırıldı:** MAL kaynağında spesifik sorgu sonuçsuz kalınca kullanıcıyı yanıltan genel popülerler listesine düşme engellendi.

### 🧾 Ek
- Sürüm adı `v2.4.206` olarak güncellendi.
- Kullanıcı talimatı doğrultusunda **yalnızca `foss` varyantı** derlendi ve yayınlandı (GMS derlenmedi).

---

## 🇬🇧 English (v2.4.206)

### 🛡️ 1. Crash Diagnostics & Reporting Overhaul (No More Silent Deaths)
- **Native Crash Handler (C++ - libkitsugi_crash_handler.so):** Catches SIGSEGV, SIGABRT, SIGBUS, SIGILL, SIGFPE, SIGTRAP using `_Unwind_Backtrace` in an async-signal-safe manner, writing raw backtraces to `native_crash.txt`.
- **Silent-Death Detector (KitsugiSessionSupervisor):** Discovers unhandled terminations (LMKD, post-ANR kills, SIGKILL) by scraping logcat on the subsequent launch, generating `unclean_exit.txt` and alerting the user with an "Unexpected Shutdown" dialog.
- **Action Breadcrumbs:** Tracks the last 40 user interactions and active screen keys to pinpoint the exact failure location.
- **Isolated Crash Screen:** Removed `Thread.sleep(800)` UI freezes. The crash screen (`KitsugiCrashActivity`) opens with its own `taskAffinity` in a dedicated process, protecting it from OS task teardown.
- **Cockroach Loop & Error Swallowing Fix:** Fixed unchecked swallowing of Cloudstream exceptions and eliminated the 100% CPU spinning loop on `Looper.loop()` exit.
- **Early Setup:** Initialized in `attachBaseContext()` to catch startup and ContentProvider faults.
- **Comprehensive Sharing:** "Send to Developer" bundles all diagnostic artifacts (`crash_log`, `crash_history`, `unclean_exit`, `native_crash`, `breadcrumbs`).

### 📋 2. Duplicate Lazy Key List Crashes Fixed
- Resolved `IllegalArgumentException: Key ... was already used` across 7 lists: Home grid, modern home rows, airing calendar (list & grid), expanded media grid dialog, manga extensions, custom list editor, and playback speed picker.

### 🖼️ 3. Image Downloads & Memory Hardening (OOM Prevention)
- **Sampled Decoding:** Notification icons downsample byte streams using `inSampleSize` instead of full-size bitmap decoding; capped downloads at 48 MB.
- **Android 10+ MediaStore Integration:** Saves downloaded images via MediaStore to `Downloads/Kitsugi/Images`, bypassing scoped storage issues on Android 11+.
- Logged gallery and download interactions into breadcrumbs.

### 🎭 4. Character Images & Live-Action Cast Photos
- **Candidate Title Resolver:** Resolves character images across all title variants (Turkish, Romaji, English, Japanese, synonyms).
- **Direct AniList Character Search Fallback:** Unmatched character records fall back to AniList character search with strict `realMalId` verification.
- **Preserved Cast Photos in Live-Action:** Actor photos are preserved, and live-action characters route directly to TMDB person profiles via `isRealMediaRole`.
- **Instant Detail Image:** Re-uses the list card thumbnail as an immediate placeholder on the detail page.
- **MAL-Only Jikan /pictures:** Prevents wasteful 404 network requests with TMDB IDs.

### ⚡ 5. Shikimori Tabs & Speed Optimization
- **Native Relations & Recommendations:** High-speed single-request fetching with batch cover resolution via Shikimori `/similar` and `/related`.
- **Staff Shelf Fallback:** Automatically queries Jikan staff data when Shikimori returns empty staff records.
- **25s Timeout & Single-Flight Protection:** Eliminates perpetual loading skeletons on unstable networks.
- **ID Resolution Safety:** Distinct caching and resolution between Shikimori IDs and MyAnimeList IDs.

### 🎬 6. Detail Screen Polish & Unified Info Row Copy Buttons
- **Unified Info Component (`DetailInfoValueRow`):** Both library entry and API result info cards now share the same row component with individual copy buttons on every row (Status, Start Date, End Date, Studio, Duration, Broadcast, Rating, English, Japanese).
- **Synonyms Chips with Individual Copy:** "Other Names" are displayed as individual chips, each with its own copy button. Prevents comma concatenation issues and ignores null/blank duplicates.
- **Compact Watch Button:** Replaced full-width watch buttons with clean, content-fitting buttons.

### 🔍 7. Search & "See All" Shelf Consistency
- **Handoff from Shelf to Source Page:** Clicking "See All" preserves the shelf's query, search scope (`SearchScope`), and pre-rendered results directly into `SourceSearchPage`.
- **Dual Scope for "All" Shelves:** Searching from "All" carries over both Anime and Manga scopes so items found on the shelf are never wiped upon opening the full source page.
- **Failure Resilience:** Network refresh failures preserve previously visible shelf results.
- **Removed Irrelevant MAL Popular Fallbacks:** Fixed MAL queries returning unrelated popular anime lists when search yielded zero matches.

### 🧾 Extras
- Version bumped to `v2.4.206`.
- Built and published exclusively as the `foss` release variant.

---

## 🇹🇷 Türkçe (v2.4.200)

### 🌍 TMDB & Simkl Başlık Dili Düzeltmesi (Türkçe → İngilizce → Romaji)

**Sorun:** TMDB sekmesindeki "Trend Animeler", "En Yüksek Puanlı Animeler" ve "Yakında Yayında" şeritlerinde, Türkçe başlık dili seçili olmasına rağmen Türkçesi bulunmayan içerikler Japonca/Çince (CJK) başlıklarla görünüyordu.

**Kök neden:** İngilizce yedek başlık isteği üretilirken `language=` parametresi `Regex("language=[^&]+")` ile değiştiriliyordu. Bu regex `with_original_language=ja` parametresinin içindeki `language=ja` kısmını da yakalayıp `with_original_language=en-US` yapıyordu. Sonuç: anime şeritlerinin (hepsi `with_original_language=ja` kullanır) İngilizce yedek isteği **tamamen farklı bir içerik listesi** döndürüyor, ID eşleşmesi tutmuyor ve Japonca başlıklar ekranda kalıyordu.

**Çözüm:**

- **Güvenli URL üretimi (`TmdbUrlUtils`):** `language` parametresi artık yalnızca tam parametre olarak değiştiriliyor; `with_original_language` ve diğer filtreler korunuyor. Yedek istek aynı içerik listesini döndürdüğü için ID eşleşmesi birebir tutuyor.
- **Merkezi başlık çözümleyici (`MediaTitleResolver`):** Türkçe → İngilizce → Romaji zinciri tek yerde tanımlandı. CJK başlıklar ilk üç adımda asla kabul edilmiyor; yalnızca hiçbir Latin alternatif yoksa (son çare) gösteriliyor.
- **Akıllı yedek istek (`TmdbTitleFallback`):** TMDB istenen dilde başlık bulamazsa orijinal (Japonca) başlığı döndürür. Uygulama bunu "yerelleştirilmiş başlık == orijinal başlık" karşılaştırmasıyla ve CJK taramasıyla yakalıyor; yalnızca gerektiğinde tek bir `en-US` isteği yapıp başlıkları birleştiriyor. Gereksiz ağ trafiği yok.
- **Kapsam:** Keşfet şeritleri (trend/popüler/en yüksek puanlı/yakında yayında + "Tümü" ekranları), arama sonuçları, medya detay sayfası (TMDB `alternative_titles` desteğiyle TR/US/JP alternatifleri), bölüm adları, yayın takvimi, yapım şirketi sayfaları, öneriler/ilişkiler ve oyuncu-ekip çalışmaları.
- **Simkl:** Simkl kayıtlarında önce TMDB üzerinden Türkçe başlık denenir; yoksa Simkl'in İngilizce/romaji alanlarına inilir. Simkl `title` alanı CJK geldiğinde ekrana düşmez.
- **Gösterim güvencesi:** TMDB/Simkl kaynaklı öğeler için kart ve liste başlıkları, seçilen başlık dilinden bağımsız olarak daima Latin alfabesinde bir alternatif bulur (Japonca seçilmediği sürece).
- **Önbellek tazeleme:** Bellek içi ve kalıcı keşfet önbelleği anahtarları artık dil + başlık çözümleme sürümü içeriyor. Böylece eski sürümden kalan hatalı (Japonca) kayıtlar servis edilmiyor, düzeltme anında görünür oluyor.

### 🧾 Ek
- Sürüm adı `2.4.200` olarak güncellendi.

---

## 🇬🇧 English (v2.4.200)

### 🌍 TMDB & Simkl Title Language Fix (Turkish → English → Romaji)

**Problem:** In the TMDB tab, shelves such as "Trend Animeler", "En Yüksek Puanlı Animeler" and "Yakında Yayında" showed Japanese/Chinese (CJK) titles for entries that had no Turkish translation, even though Turkish was the selected title language.

**Root cause:** When building the English fallback request, the `language=` parameter was rewritten with `Regex("language=[^&]+")`. That regex also matched the `language=ja` fragment inside `with_original_language=ja` and turned it into `with_original_language=en-US`. As a result, the English fallback request for anime shelves (all of which use `with_original_language=ja`) returned a **completely different result set**, ID matching failed, and raw CJK titles stayed on screen.

**Fix:**

- **Safe URL building (`TmdbUrlUtils`):** the `language` parameter is now replaced only as a standalone parameter; `with_original_language` and other filters are preserved, so the fallback request returns the same list and IDs match one-to-one.
- **Single central resolver (`MediaTitleResolver`):** the Turkish → English → Romaji chain is defined in one place. CJK titles are never accepted in the first three steps and appear only as a last resort when no Latin alternative exists.
- **Smart fallback fetch (`TmdbTitleFallback`):** TMDB returns the original (Japanese) title when it has no translation for the requested language. The app detects this by comparing localised vs. original titles and scanning for CJK, then performs a single `en-US` request only when needed.
- **Coverage:** explore shelves (trending/popular/top-rated/upcoming + "see all" screens), search results, media detail pages (via TMDB `alternative_titles`, TR/US/JP), episode names, airing calendar, production company pages, recommendations/relations and person credits.
- **Simkl:** Simkl entries try a TMDB lookup first for a Turkish title, then fall back to Simkl's English/romaji fields; CJK `title` values never reach the UI.
- **Display guarantee:** for TMDB/Simkl items, card and list titles always resolve to a Latin alternative regardless of the selected title language (unless Japanese is explicitly selected).
- **Cache refresh:** in-memory and persistent explore cache keys now include the language plus the title-resolution version, so stale (Japanese) entries from previous builds are ignored and the fix is visible immediately.

---

## 🇹🇷 Türkçe (v2.4.198)
### 🔞 +18 Bulanıklık & Yetişkin İçerik Güvenliği (Her Koşulda Tam Koruma)
- **46 Arayüz Yüzeyinde Eksiksiz Uygulama:** Ana sayfa, Keşfet şeritleri, Arama sonuçları, Karakter/Personel detayları, Galeri pencereleri ve Android TV arayüzlerinde +18 afiş ve görseller ayara tam uyumlu olarak maskelenir.
- **Eski Android Sürümleri İçin Yedek Motor (API < 31 Fallback):** Android 12 altındaki cihazlarda sistem düzeyindeki `Modifier.blur` sessizce devre dışı kaldığı için afişlerin açık kalması sorunu, Coil tabanlı `BlurTransformation` ve akıllı görsel boru hattı (`KitsugiNsfwImage`) ile çözüldü. Artık her cihazda bulanıklık garantilidir.
- **Platform Başına Doğrulanmış +18 Kuralları:**
  - **Kitsu:** R18 derecelendirmesi, `nsfw=true` veya hentai alt türü (`KitsuAdultFlags`).
  - **Shikimori:** Yalnızca `rx`/`hentai` yapımlar (`r`/`r_plus` ayrıldı).
  - **Simkl:** `adult=true` bayrağı ve resmi sertifikalar (`NC-17`, `XXX`, `18+`...) (`SimklAdultFlags`).
  - **AniList & TMDB:** `isAdult` ve `adult` alanları keşif takvimleri ve vitrinlere eksiksiz aktarılır.
  - **Manga Eklentileri:** API bayrağı olmayan kaynaklar için etiket ve tür analizi (`hentai`, `doujin`, `xxx`... ecchi hariç tutularak).
- **Kişisel Liste İçe Aktarımı:** Kitsu, Shikimori ve Simkl içe aktarımları `isAdult` bilgisini ilk andan itibaren kaydeder.

### 📌 Kaynak Seçimi Kalıcılığı & Kitsu Kimlik İzolasyonu
- **Keşfet:** Son seçili kaynak (Tümü / AniList / MAL / TMDB / Simkl / Kitsu / Shikimori) `SettingsDataStore` üzerinde saklanır ve uygulama yeniden açıldığında otomatik geri yüklenir. Otomatik kaynak düşüşleri seçim sayılmaz, kaydedilmez.
- **Arama:** Kaynak motoru + kapsam (sekme, platform, medya türü) birlikte hatırlanır; sekme çipleri ile motor seçicisi senkron kalır ve açılışta gereksiz arama tetiklenmez.
- **Listem:** Kaynak sekmesi `tab_index` ile kalıcıdır; Keşfet/Arama ile aynı davranış sergilemesi korunur.
- **Açılış maliyeti:** Son seçim TMDB/Tümü dışında bir kaynaksa splash sırasındaki 11 istekli TMDB önbellek ön-yüklemesi atlanır.
- **Kitsu Alakasız Ayrıntı Düzeltmesi:** Kitsu içe aktarmasında ham MAL ID'lerinin yazılmasından kaynaklanan sayısal çakışma (ör. Keep Your Hands Off Eizouken!) çözüldü. `KitsuIdNamespace` (`300M+1..399M+1`), stableId çözümleyici ve `KitsuIdentityMigration` eklendi.

### ♾️ Simkl Gerçek Sayfalama & Akış İyileştirmeleri (20'şer Sonsuz Kaydırma)
- **100 Sınırı Kaldırıldı:** Simkl artık statik bir sınırda durmak yerine, diğer kaynaklar gibi aşağı kaydırdıkça 20'şer 20'şer yeni içerik getirmeye devam eder.
- **Resmî Simkl API Endpoint Mimarisi:**
  - **Tür & Popülerlik Listeleri:** `genres/...` endpoint'leri üzerinden `page` ve `limit` parametreleriyle 20 sayfaya kadar (~1200 içerik) gerçek sayfalama.
  - **Yakında Yayında:** `anime/premieres/soon` ve `premieres/new` ile 20'şer içeriklik sayfalı akış.
  - **Trend Listeleri:** Sayfa parametresi bulunmayan Simkl trend JSON'ları için 500'lük Top snapshot (`today_500.json`, `week_500.json`) 1 saat boyunca önbelleğe alınır ve 20'şer dilimler halinde kaydırılır.
  - **Yayında Olanlar (Airing):** Takvim endpoint'inden tek seferde eksiksiz liste çekilir.
- **Arama Tarafında Sayfalama:** Simkl arama ve gelişmiş arama (`search`, `searchAdvanced`) `page` desteğine kavuştu. Hatalı path parçaları dokümandaki `all` standartlarına getirildi.
- **Zorunlu API Parametreleri & User-Agent:** Tüm isteklere `client_id`, `app-name=kitsugi`, `app-version` ve `KitsugiApp/... (Android)` User-Agent başlığı eklendi.
- **Kişisel Listeler:** "İzlemeye Devam" ve "Planladıklarım" listeleri tek seferde yüklendiği için gereksiz yere tekrar çekilmez.

---

## 🇹🇷 Türkçe (v2.4.196)
### 🛡️ Yanlış İçerik Eklenmesine Karşı Kimlik Güvencesi (CrossSyncIdentityGuard)
- **6 Sızıntı Vektörünün Kapatılması:**
  - **V1 (TMDB - ARM):** ARM servisine TMDB kimliği aktarımı kaldırıldı (film/dizi ID çakışması ve tüm franchise'ı kapsayan dizi ID'lerinin yanlış anime MAL ID'si üretmesi engellendi; yalnızca birebir MAL/AniList/Kitsu kimlikleri kullanılır).
  - **V2 (Simkl Anime - TMDB):** Simkl'a anime kayıtları için TMDB kimliği gönderilmesi tamamen engellendi. Simkl ID varsa yalnızca o gönderilir; dizi/film çakışmaları önlendi.
  - **V3 (Simkl Başlık Eşleme):** Güvenli kimliği (Simkl/MAL) olmayan animeler için Simkl'da başlık eşleştirmesi kapatıldı (`titleMatchingAllowed = false`). Benzer adlı film veya dizilerin listeye sızması engellendi.
  - **V4 (Kitsu Başlık Eşleme):** Kitsu başlık aramasında kısmi eşleşmeler (ör. "Oni Chichi" ⊂ "Oni Chichi 2") kapatıldı; birebir alias ve uyumlu yıl şartı getirildi.
  - **V5 & V6 (Tek Kaynak Kimlik Doğrulama):** Yeni `CrossSyncIdentityGuard` ile en az iki bağımsız kaynakça doğrulanmayan veya tek bir sağlayıcı eşlemesinden (Simkl `ids.mal`, Kitsu `mappings`, AniList `idMal`) gelen kimlikler için resmi MAL API / Jikan kataloğundan başlık ve yıl doğrulaması yapılır.
- **"Doğrulanamayan Kimlikle Asla Yeni Kayıt Eklenmez" Kuralı:** Başlık akrabalığı ve yıl kontrolünden geçemeyen şüpheli kimliklerle hiçbir platforma (AniList, MAL, Kitsu, Shikimori, Simkl) yeni kayıt eklenmez; atlanır ve raporda gerekçesiyle gösterilir. Mevcut kayıtların güncellenmesi platformun kendi kimliğiyle yapıldığı için bu kısıtlamadan etkilenmez.

### 📊 Platform Bazlı Eşitleme ve Hata Sayımı Düzeltmeleri
- **Simkl Sayım ve Makbuz İyileştirmesi:** 35'lik grupta tek bir eşleşmeyen öğe yüzünden tüm grubun "200 hata" olarak sayılması engellendi; kayıt bazlı makbuz (`Receipt.unmatched`) ile başarılı olanlar "eklendi", Simkl'de bulunamayanlar "atlandı" olarak işlenir. TV dizi ilerlemesi sınırı ve kısmi puan/geçmiş uyarıları artık grup hatası değil; eşitleme sonunda başlık listesiyle tek bir özet uyarı olarak raporlanır. 400M+ Shikimori kimliklerinin yanlışlıkla Kitsu ID olarak Simkl'e gönderilmesi düzeltildi (`300M..400M` aralık kontrolü).
- **Kitsu Resmi Mappings & 429 Yönetimi:** Resmi Kitsu `/mappings` API uç noktası (MAL ve AniList eşlemeleri) entegre edildi; manga ve ARM'da olmayan animeler için ID çözümleme oranı artırıldı. Kitsu istekleri için merkezi limiter ve 429 durumlarında 3 tekrarlı yeniden deneme mekanizması eklendi; API hata gövdeleri (`KitsuWriteResult`) okunarak ayrıntılı teşhis sağlandı. Kitsu içe aktarımına yayın yılı (`startYear`) eklendi.
- **AniList Kota ve Hız Limiti Optimizasyonu:** İstek aralığı 2100 ms'ye ayarlanarak (~28 req/dk) AniList'in fiili 30/dk hız sınırına tam uyum sağlandı. HTTP 429 durumlarında `Retry-After` başlığına duyarlı en fazla 3 kez otomatik yeniden deneme eklendi. İçe aktarımda zaten var olan `idMal` için her kayıtta atılan gereksiz sorgu kaldırılarak kota tasarrufu yapıldı.
- **MyAnimeList & Shikimori Özet Raporlama:** Çözülemeyen her kayıt için tek tek uyarı basılması yerine platform başına tek satırlık özet uyarı eklendi. Shikimori içe aktarımına `aired_on`/`released_on` yayın yılı ve İngilizce başlık eklendi. AniList içe aktarımında `seasonYear` boşsa `startDate.year` yedeği devreye alındı.

### 🧩 Kimlik Eşleştirme ve Çakışma Yönetimi
- **Aynı İsimli Yapımlar & Çelişen Kimlikler:** Berserk (1997/2016), Hunter x Hunter (1999/2011), Golden Time gibi aynı başlığa sahip yapımların tamamen izole edilip hiçbir yere yazılmaması sorunu çözüldü; artık ayrı gruplar halinde kendi kimlikleriyle yazılır.
- **Sahte "Kimlik Doğrulaması Gerekli" Gevşetmesi:** Farklı platformlardaki alias uyumsuzlukları yalnızca her iki tarafta da yıl biliniyor ve yıl farkı 1'den büyükse incelemeye düşer; aksi halde güvenle birleştirilir.
- **Durum ve İlerleme Doğruluğu:** Simkl'in toplam bölüm (`total`) sayısından "Tamamlandı" çıkarımı yapılması engellendi. Tek kaynaklı gruplarda gereksiz güncellemeler engellendi.
- **Temiz Rapor:** "Eşitleme kısmen tamamlandı" satırı hata yerine bilgi formatına dönüştürüldü.

### 📱 Yenilenen Tam Ekran Çapraz Eşitleme Paneli
- **Tam Ekran Dialog & Çentik Uyumu:** Panel artık `fullScreen = true` olarak açılır; durum çubuğu ve ekran çentikleri (`displayCutoutPadding`) ile kusursuz uyum sağlar.
- **Dikey ve Yatay Yönlendirme:** Dikey modda tek sütunlu akıcı hiyerarşi; yatay modda sol panelde ilerleme/hesaplar/eylemler, sağ panelde filtreler ve canlı günlük gösterilir.
- **Filtre Çipleri & Kompakt Hesap Kartları:** Hata ve uyarı sayıları filtre çiplerine rozet olarak eklendi; platform logolu kartlar ve 40 dp optimize butonlarla daha kompakt bir görünüm sunar.

---

## 🇹🇷 Türkçe (v2.4.195)
### 🌐 Keşfet "Tümü" — Kaynak Bazlı Keşif Alanları & Kategori Gruplaması
- **Kaynaklara Özel Bağımsız Alanlar:** AniList, MyAnimeList, TMDB, Simkl, Kitsu ve Shikimori platformları birbirine karıştırılmadan kendi logolu, renk vurgulu bağımsız başlıklarına kavuştu.
- **Kategori Ayrımı:** Trendler, en iyiler, popülerler, sezon içerikleri ve ilgili platformun desteklediği kategoriler doğrudan kendi kaynağının altında listelenir.
- **Sabit Kaynak & Kategori Çubuğu:** Ekranın üstündeki sabit çubukla doğrudan istenilen platforma veya kategori şeridine anında atlanabilir; uzun sayfalarda sayfa başına dönüş düğmesi eklendi.
- **Bağımsız Daraltma & Dayanıklı Durum:** Her kaynak alanı ayrı ayrı daraltılıp genişletilebilir; daraltma durumları ekran döndürmelerinde veya gezinmede korunur.
- **Akıllı "Tümünü Gör":** Çoklu görünümdeki "Tümünü Gör" butonu doğru kaynağın ve kategorinin devam sayfasını açar.
- **İzole Hata Yönetimi & Yeniden Deneme:** Bir kaynak yüklenemezse veya hata verirse diğer kaynaklar görüntülenmeye devam eder; yalnızca sorunlu kaynak tek dokunuşla yeniden denenebilir.
- **Büyük Ekran & TV Uyumu:** Ortak kaynak alanları mobil, tablet ve Android TV D-pad gezinmesiyle tam uyumlu hale getirildi.

### 📋 Listem "Tümü" Sekmesi, Temsilci Kaynak & Simkl TMDB Detay Geçişi
- **Sola Kaydırma & Sekme Sırası Düzeltmesi:** "Tümü" sekmesi gerçekten ilk sıraya (`index 0`) taşındı. Pager ile üst buton dizilimi eşitlenerek Tümü sayfasından sola kaydıramama sorunu giderildi.
- **Öncelikli Temsilci Kaynak:** Birleşik kütüphanede aynı yapımın farklı servislerdeki kayıtları birleştirilirken öncelik zinciri uygulandı: `AniList > MAL > Kitsu > Shikimori > Simkl > TMDB`. Animelerde otomatik olarak AniList öne çıkarken, dizi ve filmlerde doğal olarak Simkl/TMDB temsilci kalır.
- **Hızlı Simkl Detayları (TMDB Entegrasyonu):** Simkl kayıtlarının ayrıntı sayfaları doğrudan TMDB üzerinden açılır (`tmdbId`, ARM ID çözücü veya arama). Simkl'in yavaş API yanıtı detay ekranını geciktirmez.
- **Varsayılan Manuel Kayıt:** Tümü sekmesinde manuel kayıt eklerken varsayılan hedef AniList olarak ayarlandı.

### 🎬 Akış ve Kalite Bilgi Doğruluğu (StreamInfoFix)
- **"400p" Kök Neden Düzeltmesi:** CloudStream eklentilerinin bilinmeyen kalite döndürdüğünde (`Qualities.Unknown = 400`) ekrana "400p" basması sorunu giderildi.
- **Sahte Dil ve Rozet Çıkarmalarının Kaldırılması:** "Subaru" kelimesinden "Altyazılı", "Dubai" kelimesinden "Dublaj" basan hatalı alt dize filtreleri kaldırıldı. "Türkçe eklenti ⇒ Altyazılı" varsayımı ve kanıtsız "🎬 Standart" etiketi temizlendi.
- **Gerçek Ölçüm ve HLS Çözümleme (`StreamProbe`):** HLS master playlist'i üzerinden `#EXT-X-STREAM-INF` (çözünürlük) ve `#EXT-X-MEDIA` (ses/altyazı dilleri) etiketleri incelenerek gerçek veriler rozetlere yansıtılır. Yüklenen altyazılar `CC TR` / `CC EN` olarak gösterilir.
- **Arayüz ve Filtre Uyumu:** Kart rozetleri, alt sayfa akış seçici, filtre çipleri ve TV arayüzü yeni `StreamInfoResolver` verisiyle senkronize edildi.

### 🔐 Shikimori Yetkilendirme & Gradle Bellek Optimizasyonu
- **Shikimori 401 Otomatik Yenileme:** Sunucu tarafında geçersiz kılınan token'larda liste çekimi sırasında otomatik refresh yapılarak kullanıcı oturumunun kopması önlenir.
- **Otomatik RAM Temizliği:** `gradle.properties` içine 60 saniyelik daemon zaman aşımı eklendi; derleme bittikten sonra arkada asılı kalan Gradle/JDK süreçlerinin bellek işgali önlendi.

---

## 🇬🇧 English (v2.4.195)
### 🌐 Explore "All" — Dedicated Source Sections & Grouped Categories
- **Dedicated Platform Sections:** AniList, MyAnimeList, TMDB, Simkl, Kitsu, and Shikimori each receive dedicated branded headers with custom accent colors and descriptions instead of intermixed rails.
- **Source-Scoped Rails:** Trending, top-rated, popular, seasonal, upcoming, and other platform categories stay strictly grouped under their respective provider section.
- **Sticky Platform Navigation:** Sticky header bar allows immediate jumping directly to any provider (AniList, MAL, TMDB, etc.) or category rail, with a quick return-to-top shortcut.
- **Collapsible Sections & Saved State:** Each provider section can be independently collapsed or expanded, preserving state across screen rotations and navigation.
- **Accurate "See All" Routing:** Tapping "See All" navigates to the dedicated category view matching the exact source and filter.
- **Isolated Provider Failures & Retry:** If a single provider fails to load or times out, all other providers remain intact; users can retry individual failed providers independently.
- **TV & Large Screen Support:** Unified source sections fully support Android TV D-pad focus, navigation, and tablet landscape layouts.

### 📋 My List "All" Tab, Representative Hierarchy & Fast Simkl Details
- **Tab Ordering & Swiping Fix:** The unified "All" tab is now index 0. Synchronized pager and pill tabs eliminate the gesture lock where users could not swipe left from the "All" page.
- **Smart Representative Provider Hierarchy:** Multi-provider entries deduplicate using prioritized representative selection: `AniList > MAL > Kitsu > Shikimori > Simkl > TMDB`. Anime defaults to AniList, while TV shows and movies naturally maintain Simkl/TMDB representation.
- **Lightning Fast Simkl Details via TMDB:** Simkl entries now route details through TMDB (`tmdbId`, ARM resolver, or fallback search), removing slow Simkl API bottlenecks.
- **Default Manual Entry:** Manual additions on the "All" tab default to AniList.

### 🎬 Accurate Stream Information (StreamInfoFix)
- **"400p" False Badge Root Fix:** Resolved issue where `Qualities.Unknown = 400` in CloudStream plugins resulted in a "400p" label.
- **Removed Heuristic Audio/Subtitle Inferences:** Fixed substring matching bugs (e.g., "Subaru" triggering Subtitle, "Dubai" triggering Dubbed). Eliminated automatic "Turkish Plugin ⇒ Subtitled" assumption and baseless "🎬 Standard" labels.
- **Real Stream Probing (`StreamProbe`):** Analyzes HLS playlists for actual `#EXT-X-STREAM-INF` resolutions and `#EXT-X-MEDIA` audio/subtitle tracks. Verified subtitle files display as `CC TR` / `CC EN`.
- **Synchronized UI & Filters:** Stream cards, bottom sheets, filter chips, and TV screens are fully wired to the accurate `StreamInfoResolver` output.

### 🔐 Shikimori 401 Silent Token Refresh & Memory Optimizations
- **Shikimori 401 Auto-Recovery:** Automatic token refresh on 401 errors during rate syncing prevents unneeded session expirations.
- **Gradle/JDK Daemon Idle Cleanup:** Added 60s idle timeout to `gradle.properties` and post-build cleanup scripts to stop lingering Java daemons from holding system RAM.

---

## 🇹🇷 Türkçe (v2.4.194)
### ⚡ Çapraz Eşitleme (Cross-Sync) %0 Takılma Düzeltmesi & Gelişmiş Tanılama
- **Aday İndeksleme ve Performans Optimizasyonu:** Eşleştirme aşamasında her yeni içerik için tüm birleştirilmiş kayıtların baştan sona taranması ($O(N^2)$) kaldırıldı. Kimlik ve başlık indeksleriyle (`CrossSyncCandidateIndex`) aday arama daraltıldı; binlerce kayıtta donma ve takılmalar engellendi.
- **Canlı İlerleme Sayacı:** Eşleştirme aşamasında sayaç her 25 kaynak kaydında güncellenir ve coroutine iptal kontrolleri düzenli çalışır.
- **Dinamik Durum ve Duraklama Uyarıları:** Ağ beklerken sahte %0 yerine belirsiz (indeterminate) ilerleme göstergesi sunulur. Ekrana geçen süre, tahmini kalan süre ve uzun süren duraklamalarda uyarı eklendi.
- **Gelişmiş Günlük & Rapor Paylaşımı:** Günlükte Tümü / Sorunlar / Hatalar / Uyarılar filtreleri ve genişletilebilir teknik ayrıntılar yer alır. Eşitlemeyi onaylayarak durdurabilir, sürerken kısmi raporu paylaşabilir veya tamamlanınca raporu dışa aktarabilirsiniz.
- **Otomatik Rapor ve Güvenlik:** Raporlar otomatik olarak `Downloads/Kitsugi/CrossSyncReports` dizinine kaydedilir; cihaz ve ortam bilgileri eklenir, Bearer ve API anahtarları maskelenir (`[REDACTED]`). Hesaplardan içerik silinmez.
- **İstem Dışı Eşitleme Koruması:** "Son Eşitleme Raporu"na dokunulduğunda yanlışlıkla yeni bir eşitleme başlatılması engellendi.

### 🌐 Listem Sayfası Birleşik "Tümü" Görünümü & Dinamik Platform Rozetleri
- **Tüm Kaynakları Tek Ekranda Birleştirme:** Listem sekmesine AniList, MyAnimeList, Simkl, Kitsu ve Shikimori kütüphanelerini tek ekranda toplayan "Tümü" seçeneği eklendi.
- **Tekil Gösterim & Yinelenme Koruması:** Farklı servislerde bulunan aynı yapım yinelenmek yerine tek kartta birleştirilir (`groupMyListEntries`).
- **Dinamik Kaynak Rozetleri:** Kartların altında yapımın hangi platformlarda bulunduğunu gösteren orijinal platform logoları (`FlowRow` ile dar kartlarda taşmadan) sergilenir.
- **Tüm Düzenlerle Uyumlu:** Kompakt, rahat, ayrıntılı, 2 sütunlu grid ve minimalist kart düzenleriyle tam uyumludur.
- **Tek Dokunuşla Yenileme:** Birleşik görünümde aşağı çekip yenileme yapıldığında bağlı tüm aktif sağlayıcılar sırayla eşitlenir.

---

## 🇬🇧 English (v2.4.194)
### ⚡ Cross-Sync 0% Stall Fix & Advanced Diagnostics
- **Candidate Indexing & Performance:** Eliminated $O(N^2)$ quadratic scanning during cross-platform media grouping. Added identity & title candidate index (`CrossSyncCandidateIndex`), speeding up grouping on multi-thousand entry libraries.
- **Live Progress Reporting:** Progress counters update every 25 source records during grouping, with periodic cooperative coroutine cancellation checks.
- **Indeterminate Progress & Stall Warnings:** Displays indeterminate progress indicators during network/verification steps instead of a false 0% determinate bar. Added elapsed time, estimated time, and long-stall warnings.
- **Advanced Log Filtering & Report Sharing:** Logs feature All / Issues / Errors / Warnings filters and expandable technical stacktraces. Allows confirmed cancellation, sharing partial reports while sync is active, and exporting final reports.
- **Auto Report Storage & Credential Redaction:** Diagnostic reports are automatically saved to `Downloads/Kitsugi/CrossSyncReports/` with environment details and redacted credentials (`[REDACTED]`). No items are deleted from upstream accounts.
- **Accidental Sync Trigger Prevention:** Viewing the "Latest Sync Report" no longer accidentally launches a new synchronization.

### 🌐 My List Unified "All" View & Dynamic Platform Badges
- **Unified Library Tab:** Added an "All" option to the My List screen consolidating libraries across AniList, MyAnimeList, Simkl, Kitsu, and Shikimori in one view.
- **Deduplicated Media Grouping:** Identical media titles across multiple connected providers are unified into a single card representation.
- **Dynamic Platform Badges:** Cards display responsive badges (`FlowRow`) indicating all connected platforms hosting the entry.
- **Layout Compatibility:** Fully supported across compact, comfortable, large, 2-column grid, and minimalist card designs.
- **Unified Pull-to-Refresh:** Pulling to refresh in the combined view sequentially syncs all active connected providers.

---

## 🇹🇷 Türkçe (v2.4.193)
### 🔍 Arama Deneyimi (UX) Yenilemesi & "Tümünü Gör" Ayrık Kaynak Sayfası

- **Arama Sayfası Sadeleştirmesi:** Arama çubuğunun altındaki kalabalık kategori çipleri, sıralama seçici, filtreler ve platform bilgi satırı ana sayfadan kaldırılarak sağ üstteki filtre butonunun açtığı `SourceEngineFilterSheet` altında toplandı. Sayfa temizlendi.
- **Kompakt Filtre Özeti:** Varsayılandan farklı bir filtre/kapsam seçildiğinde tek satırlık kompakt özet çipi görüntülenir ve tıklandığında ayar panelini açar.
- **"Tümünü Gör" Artık Ayrık Sayfa (`SourceSearchPage`):** Çoklu arama raflarındaki "Tümünü Gör" artık aynı sayfada sekme değiştirmek yerine geri butonu, başlık ve dikey sonuç listesi içeren bağımsız bir sayfada açılır.
- **İzole ViewModel:** `SourceSearchPage` kendi izole `SearchViewModel` örneğini kullanır; burada yapılan filtre ve arama değişiklikleri ana çoklu arama durumunu etkilemez.
- **Shikimori 1-Tık OAuth Callback Düzeltmesi (`redirect_uri` Uyuşmazlığı):** Shikimori OAuth yetkilendirme isteğindeki callback adresinin uyuşmaması ("The requested redirect uri is malformed or doesn't match client redirect URI") sorunu giderildi. Varsayılan 1-tık girişi resmî `aniyomi://shikimori-auth` callback'ine bağlandı; özel kayıtlı uygulamalar için `kitsugi://shikimori-auth` alternatifi korundu ve regresyon birim testleri eklendi.

---

## 🇬🇧 English (v2.4.193)
### 🔍 Search UX Overhaul, Dedicated "See All" Screen & Shikimori 1-Tap Fix

- **Clean Search Screen:** Cluttered chips, sorting dropdowns, and info banners removed from under the search bar and unified into the top-right `SourceEngineFilterSheet`.
- **Compact Active Filter Summary:** An unobtrusive summary chip appears only when non-default filters or scopes are active.
- **Dedicated "See All" Screen (`SourceSearchPage`):** Tapping "See All" opens a full standalone screen with back navigation, search bar, filters, and vertical results.
- **Isolated ViewModel:** Uses an independent `SearchViewModel` instance so altering filters on source search does not corrupt multi-search state.
- **Shikimori 1-Tap OAuth Callback Fix (`redirect_uri` Mismatch):** Fixed the OAuth authorization rejection ("The requested redirect uri is malformed or doesn't match client redirect URI"). Default 1-tap flow now uses the official registered `aniyomi://shikimori-auth` callback with `kitsugi://shikimori-auth` preserved for custom apps, verified via unit tests.

---

## 🇹🇷 Türkçe (v2.4.194)
### 🔔 Bildirim Altyapısı Denetimi — 5 Kaynak (AniList · MAL · Simkl/TMDB · Kitsu · Shikimori)

- **Bildirim Teşhisi (yeni):** Bildirimler ekranının sağ üstüne 🪲 butonu eklendi. Tek dokunuşla 5 kaynağa **gerçek istek** atılır; hesap bağlı mı, hangi uç nokta kullanılıyor, **HTTP durum kodu**, dönen kayıt sayısı, süre ve varsa hata + çözüm ipucu gösterilir. Artık "geliyor mu gelmiyor mu" tahmine kalmaz.
- **AniList — sessiz hata yutma kaldırıldı:** İstek başarısız olduğunda istemci boş liste döndürüp "bildirim yok" gösteriyordu; artık GraphQL hataları gerçek mesajla gösterilir. Okunmamış bildirim sayacı eklendi ve sekmede rozet olarak görünür.
- **Shikimori — gerçek bildirim akışı:** Uygulama bugüne kadar **izleme geçmişini** bildirim gibi gösteriyordu. Resmî uçlar (`GET /api/users/:id/messages?type=notifications|news` ve `/unread_messages`) eklendi; `messages` izni yoksa ekran bunu ve nasıl verileceğini açıklar, geçmiş listesine güvenli düşer. OAuth izni artık client_id'ye göre seçilir (kendi uygulamanızı girerseniz `user_rates messages`).
- **Simkl — deprecated takvim yolu düzeltildi (kritik):** Eski kod, **1 Şubat 2027'de güncellenmeyi bırakacak** olan v2 olmayan dosyaları okuyor ve sonucu **ilk 50 öğeyle** kesiyordu; eşleşme bu yüzden neredeyse hiç tutmuyordu. Yeni `SimklCalendarClient` resmî `data.simkl.in/calendar/v2/…` dosyalarını (`{calendar, metadata}`) ve kullanıcının `/sync/all-items/*/watching` listesini kullanır (simkl/tmdb/mal ID eşleşmesi, ±7 gün penceresi).
- **MyAnimeList — takvim haftası tuzağı:** Akış, içinde bulunulan takvim haftasına bağlı olduğu için hafta başında boşalıyor ve `id`'ye göre sıralanıyordu. Artık kayan **son 7 gün** penceresi kullanılır, doğru sıralanır ve listenin ne olduğu (MAL API'sinin kişisel bildirim sunmadığı) bilgi bandında yazılır.
- **Kitsu — sonraki bölüm tarihleri:** Kitsu'nun genel API'sinde bildirim ucu yoktur; buna karşılık anime kaynağındaki `nextRelease` alanı okunup "Bölüm N • yayınlandı/yaklaşan" akışı üretilir.
- **Arka plan işçisi güçlendirildi:** Simkl'de bölüm başına ayrı bildirim (eskiden dizi başına 1), yalnızca son 24 saatte yayınlananlar; MAL için hafta sınırına takılmayan pencere; **Kitsu ve Shikimori için yeni arka plan desteği** ve Ayarlar > Bildirimler'de iki yeni anahtar.
- **Ayrıntılı rapor:** `docs/audits/NOTIFICATION_INFRASTRUCTURE_AUDIT_2026-10-07.md`

---

## 🇬🇧 English (v2.4.194)

### 🔔 Notification Infrastructure Audit — All 5 Sources (AniList · MAL · Simkl/TMDB · Kitsu · Shikimori)

- **Notification Diagnostics (new):** a 🪲 action in the Notifications screen fires **real requests** at all five sources and reports connection state, endpoint, **HTTP status**, item count, duration, plus an error and a fix hint. No more guessing whether notifications actually arrive.
- **AniList — silent errors removed:** on failure the client returned an empty list (looking like "no notifications"); GraphQL errors are now surfaced with their real message. The unread notification count is fetched and shown as a tab badge.
- **Shikimori — real notification feed:** the app used to present the **watch history** as notifications. Official endpoints (`GET /api/users/:id/messages?type=notifications|news`, `/unread_messages`) are now used, with a clear in-app explanation and a safe history fallback when the `messages` scope is missing. The OAuth scope is now chosen per client id (use your own app to get `user_rates messages`).
- **Simkl — deprecated calendar path fixed (critical):** the old code read the pre-v2 files that **stop updating on 1 February 2027** and truncated results to the **first 50 items**, which is why matches almost never appeared. The new `SimklCalendarClient` reads the official `data.simkl.in/calendar/v2/…` files (`{calendar, metadata}`) and joins them with the user's `/sync/all-items/*/watching` list (simkl/tmdb/mal ID match, ±7 day window).
- **MyAnimeList — calendar-week trap:** the feed was bound to the current calendar week (empty at the start of a week) and sorted by string id. It now uses a rolling **last 7 days** window, correct ordering, and an info banner explaining that the MAL API provides no personal notifications.
- **Kitsu — next episode dates:** Kitsu's public API has no notification endpoint, but the anime resource's `nextRelease` field is now parsed into an "Episode N • aired/upcoming" feed.
- **Hardened background worker:** per-episode Simkl notifications (previously one per show), last-24-hours only; a week-boundary-free window for MAL; **new background support for Kitsu and Shikimori** with two new toggles in Settings > Notifications.
- **Full report:** `docs/audits/NOTIFICATION_INFRASTRUCTURE_AUDIT_2026-10-07.md`

---

## 🇹🇷 Türkçe (v2.4.194)

### 🛡️ Kitsu Senkronizasyon & Çoklu Kayıt Çökme Koruması (Crash Fix)

- **Kitsu ve Platform Eşitleme Çökmesi Giderildi (`IllegalArgumentException: Belirsiz kitsu eşlemesi...`):** `MediaEntryRepository.smartImport` içerisinde yer alan katı `require(matches.size <= 1)` koşulu kaldırıldı. Veritabanında aynı yapıma ait birden fazla yerel kayıt veya benzer başlık bulunduğunda uygulamanın `KitsugiCrashActivity` ile çökmesi engellendi; ID, ana başlık ve güncellik sırasına göre en uygun kaydı seçip güvenle güncelleyen akıllı çözümleyici uygulandı.
- **Franchise & Çoklu Film Başlık İzolasyonu:** `MediaIdentity.sameMedia` fonksiyonu güçlendirildi. `Date A Bullet: Dead or Bullet` ve `Date A Bullet: Nightmare or Queen` gibi aynı seriye ait yapımların yalnızca genel Japonca franchise adı (`デート・ア・バレット`) paylaşıldığı için yanlışlıkla aynı yapım sanılması engellendi.
- **Yedek Geri Yükleme ve Çapraz Eşitleme Koruması:** `MediaEntryBackup.mergeAndSyncEntries` ve `AuthViewModel.clusterEntry` adımlarındaki tüm fırlatıcı `require` kontrolleri güvenli eşleme mekanizmasıyla değiştirildi.
- **Hata Yakalama & İzolasyon Güvencesi:** `AuthViewModel` içerisindeki tüm harici platform import ve sunucu doğrulama adımları `runCatching` kalkanına alındı; bir platformda beklenmeyen bir durum oluşsa dahi diğer platformlar ve kullanıcı arayüzü kesintiye uğramadan çalışmaya devam ediyor.

### 🚀 Shikimori OAuth Girişi Kök Neden Düzeltmesi (Yönlendirme + Authorization Header Kaybı)

- **Giriş Başarısızlığının Kök Nedeni Çözüldü:** Shikimori production ortamında diğer domain'ler (örn. `shikimori.one`) `shikimori.io` adresine 301 ile yönlendiriliyor. OkHttp, host değiştiren yönlendirmelerde güvenlik nedeniyle `Authorization` header'ını otomatik SİLİYORDU. Bu yüzden token takası başarılı olup geçerli jeton elde edilmesine rağmen, hemen ardından yapılan `whoami` doğrulaması "oturumsuz" istek olarak ulaşıp `200 + null` döndürüyor ve uygulama **geçerli jetonu çöpe atarak** "jeton geçersiz görünüyor" hatasıyla kullanıcıyı yanıltıyordu (kullanıcı aynı kodla yeniden denediğinde kod tükendiği için `invalid_grant` alıyordu).
- **Başlıklar Korunan Yönlendirme Takibi:** Shikimori istemcisine (`ShikimoriApiClient`) özel bir HTTP istemci eklendi; 301/302/303/307/308 yönlendirmeleri, orijinal `Authorization` header'ı korunarak manuel takip ediliyor (en fazla 3 atlayış, RFC 7231'e uygun POST→GET dönüşümüyle). Artık hangi Shikimori domain'ine istek atılırsa atılsın oturum bilgisi kaybolmuyor.
- **Kanonik Host'a Doğrudan İstek:** Tüm API/token uçları artık production'un resmi host'u olan `shikimori.io` adresine doğrudan gider (`shikimori.one` yedek olarak korunur). Gereksiz yönlendirme atlayışı ortadan kalktı.
- **whoami Artık Girişi Düşürmüyor:** Giriş akışında (hem 1-tık deep-link hem manuel kod) yetki kodu takas edilir edilmez token derhal saklanıyor; profil bakımı (whoami, 3 denemeli) geçici olarak başarısız olsa bile oturum korunuyor ve kullanıcı kimliği ilk senkronizasyon/profil/aktarım işleminde tembel olarak çözümleniyor (`ensureShikimoriUserResolved`). Tekrar tekrar "yeniden giriş" döngüsü ve tüketilmiş kod hataları bitti.
- **Ölü Refresh Token Temizliği:** Sunucu refresh token'ı net biçimde reddediyorsa (400/401 `invalid_grant`/`invalid_client`) oturum sessizce saklanıp 401 döngüsü yerine temizleniyor ve kullanıcı yeniden girişe yönlendiriliyor.

### 📊 Kapsamlı Çapraz Eşitleme Hata Raporu ve Eşleştirme Güvenliği

- **Tam Eşitleme ve Hata Raporlama:** Eşitleme penceresindeki 500 satırlık canlı görünüm sınırından bağımsız olarak, tüm oturum boyunca gerçekleşen işlemler, API hataları, eşleştirme kararları ve platform istatistikleri eksiksiz bir rapora kaydedilir.
- **Otomatik Rapor Dosyası ve Dışa Aktarma:** Android 10+ cihazlarda raporlar otomatik olarak `İndirilenler/Kitsugi/CrossSyncReports/` altına `.txt` formatında kaydedilir. Ayrıca eşitleme penceresine eklenen "Raporu Kaydet" düğmesiyle rapor istenen herhangi bir konuma dışa aktarılabilir.
- **Sağlayıcı Kimlik Çatışması Koruması:** Aynı tür ve başlık için farklı servislerden gelen kimlikler birbiriyle çelişiyorsa (`MediaIdentity.conflictingIdentityKeys`) veya belirsiz birden fazla aday grup çözülemiyorsa kayıt diğer platformlara yazılmadan güvenlik için atlanır ve nedeni detaylarıyla rapora yazılır.
- **Hassas Veri İzolasyonu:** Oluşturulan raporda Bearer token, access/refresh token ve client secret gibi tüm hassas kimlik doğrulama anahtarları otomatik olarak maskelenir (`[REDACTED]`).

---

## 🇬🇧 English (v2.4.194)

### 🛡️ Kitsu Sync & Disambiguation Crash Fix

- **Resolved Fatal Crash in Kitsu & Multi-Platform Sync (`IllegalArgumentException: Belirsiz kitsu eşlemesi...`):** Replaced hard-failing `require(matches.size <= 1)` check in `MediaEntryRepository.smartImport` with graceful disambiguation. If multiple local records exist for an entry (or during complex imports), the system prioritizes exact provider key, exact primary title, English title, and latest update timestamp without throwing unhandled exceptions.
- **Franchise Japanese Title Bleed Prevention:** Enhanced `MediaIdentity.sameMedia` comparison so franchise movies sharing generic Japanese series titles (e.g. `Date A Bullet: Dead or Bullet` vs `Nightmare or Queen`) are not conflated as the same media when primary titles differ.
- **Safe Backup Merge & Cross-Sync Clustering:** Replaced assertion throws in `MediaEntryBackup.mergeAndSyncEntries` and `AuthViewModel.clusterEntry` with deterministic matching fallbacks.
- **Fault-Tolerant Coroutine Safety:** Guarded all platform import invocations in `AuthViewModel` with `runCatching` to prevent background coroutine cancellations from bringing down the UI or interrupting remaining platforms.

### 🚀 Shikimori OAuth Login Root-Cause Fix (Redirect + Authorization Header Loss)

- **Login Failure Root Cause Resolved:** In Shikimori production, other domains (e.g. `shikimori.one`) 301-redirect to `shikimori.io`. OkHttp automatically STRIPS the `Authorization` header on host-changing redirects for security. As a result, the token exchange succeeded and produced a valid token, but the immediately-following `whoami` verification arrived as an unauthenticated request, received `200 + null`, and the app **discarded the valid token** with a misleading "token appears invalid" error (retries with the same code then failed with `invalid_grant` because the code was already consumed).
- **Header-Preserving Redirect Following:** The Shikimori client (`ShikimoriApiClient`) now uses a dedicated HTTP client that manually follows 301/302/303/307/308 redirects while preserving the original `Authorization` header (max 3 hops, RFC 7231-compliant POST→GET conversion). The session no longer gets lost regardless of which Shikimori domain the request lands on.
- **Direct Requests to the Canonical Host:** All API/token endpoints now hit `shikimori.io` (the production canonical host) directly; `shikimori.one` is kept as a fallback. Unnecessary redirect hops are eliminated.
- **whoami No Longer Kills Login:** In both the 1-tap deep-link and manual code flows, the token is saved immediately after the authorization-code exchange. If the profile lookup (whoami, with 3 retries) temporarily fails, the session is preserved and the user identity is resolved lazily on first sync/profile/import (`ensureShikimoriUserResolved`). The repeated re-login loop and consumed-code errors are gone.
- **Dead Refresh Token Cleanup:** When the server explicitly rejects the refresh token (400/401 `invalid_grant`/`invalid_client`), the session is cleared and the user is prompted to reconnect instead of silently looping on 401s.

### 📊 Comprehensive Cross-Sync Diagnostic Report & Matching Safety

- **Full Sync & Error Reporting:** Independent of the live dialog's 500-entry limit, complete session operations, API error chains, disambiguation decisions, and platform statistics are written to an exhaustive diagnostic report.
- **Automatic Report Storage & Export:** On Android 10+, reports are automatically persisted to `Downloads/Kitsugi/CrossSyncReports/` as `.txt` files. A "Save Report" button in the cross-sync dialog also allows saving the report to any user-selected location.
- **Provider Conflict Prevention:** If entries of the same type share titles but have conflicting provider IDs (`MediaIdentity.conflictingIdentityKeys`) or cannot be safely disambiguated across multiple candidates, writing to target platforms is skipped for safety and logged to the report.
- **Credential Redaction:** Bearer headers, access/refresh tokens, and client secrets are automatically redacted (`[REDACTED]`) before reports reach disk.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 MyAnimeList Çapraz Senkronizasyon & Shikimori OAuth Doorkeeper Düzeltmesi

- **MyAnimeList Çapraz Eşitleme URL Hatası Çözüldü (`Illegal character in query at index 193`):** MAL API v2 kullanıcı listesi (`/animelist` ve `/mangalist`) isteklerindeki `fields` parametresi içinde yer alan iç içe alan seçicisi (`list_status{...}`) süslü parantezleri (`%7B` ve `%7D`) olarak URL encode edildi. Böylece `java.net.URI`'nin parantezleri reddedip çapraz eşitlemeyi %0'da durdurması tamamen engellendi.
- **Shikimori Doorkeeper Token İstek Düzeltmesi:** Token takas isteğine (`POST /oauth/token`) `redirect_uri` parametresi geri eklendi; `400 Missing required parameter: redirect_uri` hatası tamamen giderildi.
- **Dinamik Pending Redirect URI Eşleştirmesi:** Tarayıcının açıldığı yönlendirme adresi (`kitsugi://`, `urn:ietf:wg:oauth:2.0:oob` veya `aniyomi://`) hafızaya alınarak token takası sırasında sunucuya birebir aynı adres gönderilir.
- **Ham JSON Hatası Engellendi:** Shikimori'nin geçersiz oturumlarda döndürdüğü `null` yanıtının parse edilmesiyle oluşan ham `A JSONObject text must begin with '{'` hatası giderildi; tüm yanıtlar null/HTML kontrolleriyle korumaya alındı.
- **Gelişmiş Giriş Diyaloğu & Alternatif Şema:** Giriş penceresine "Shikimori OAuth Uygulamalarım" yönetim bağlantısı ve eski şemayı kullananlar için "Alternatif Tek Tık (aniyomi://)" butonu eklendi.

---

## 🇬🇧 English (v2.4.194)

### 🚀 MyAnimeList Cross-Sync Query Encoding & Shikimori OAuth Doorkeeper Fix

- **MyAnimeList Cross-Sync Query Parsing Fix (`Illegal character in query at index 193`):** Percent-encoded nested field selector braces (`%7B` and `%7D`) in MAL `/animelist` and `/mangalist` endpoints. Fixed `java.net.URI` query syntax crash that aborted cross-account sync at 0%.
- **Shikimori Doorkeeper Token Payload Fix:** Restored `redirect_uri` parameter in token exchange requests, eliminating `400 Missing required parameter: redirect_uri`.
- **Dynamic Pending Redirect URI Matching:** Pairs the authorization redirect URI (`kitsugi://`, `urn:ietf:wg:oauth:2.0:oob`, or `aniyomi://`) with the token exchange step as required by Doorkeeper OAuth.
- **Guarded JSON Parsing:** Eliminated raw `A JSONObject text must begin with '{'` exceptions when encountering `null` or non-JSON payloads from expired session endpoints.
- **Enhanced Login Dialog & Fallback Flows:** Added direct link to Shikimori OAuth management dashboard and an "Alternative 1-Tap (aniyomi://)" button in the login dialog.

---

---

- **Nuvio Libtorrent P2P Motor Entegrasyonu (libtorrent + Boost.Asio):** Nuvio'nun yüksek performanslı C++ libtorrent ve Boost.Asio çekirdeği (`lib-nuvio-engine-android-0.1.2.aar`) Kitsugi'ye entegre edildi. Artık Debrid üyeliği (RealDebrid, AllDebrid vb.) olmadan da Torrentio ve diğer torrent tabanlı Stremio eklentileri doğrudan cihaz üzerinden eşler arası (P2P) ve yüksek hızla oynatılabilir.
- **Gelişmiş Seeding & Yükleme (Upload) Kontrolleri:** Orijinal Nuvio uygulamasında bulunmayan, arka planda sınırsız çalışan gönderme (seeding) davranışı tamamen kullanıcı denetimine açıldı. Stremio eklentileri ayarlarından seed modu tek tıkla kapatılabilir ya da internet kotasını korumak için 256 KB/s, 512 KB/s, 1 MB/s, 2 MB/s hız sınırları atanabilir.
- **P2P Gizlilik & Swarm Güvenlik Bilgilendirmesi:** P2P motoru ilk kez aktif edildiğinde veya Debrid olmadan bir torrent bağlantısına tıklandığında açılan tek seferlik güvenlik diyaloğu eklendi. IP adresinin torrent swarm havuzunda görünebileceği, telif hakları sorumluluğu ve VPN tavsiyeleri net biçimde sunuldu.
- **Tam Ekran Oynatıcı Canlı Torrent Paneli (TorrentOverlay):** Video oynatıcıya bağlanan canlı gösterge paneli sayesinde anlık indirme hızı, yükleme hızı, bağlı eşler (peers), seed sayısı ve önbellek tampon durumu gerçek zamanlı takip edilebilir. Oynatıcı kapatıldığında veya bölüm değiştiğinde P2P motoru gereksiz arka plan trafiğini önlemek için otomatik sonlandırılır.
- **Simkl Toplu Eşitleme (Batch Sync) & 429 Hız Sınırı Koruması:** Simkl API'sinin saniyede 1 istek kuralına ve `title`/`year` zorunluluklarına tam uyum sağlandı. 35'lik akıllı paketler ve 1.1 saniye gecikme ile yüzlerce animenin tek seferde sorunsuz senkronize edilmesi sağlandı; senkronizasyon sonrası Room DB'ye gerçek sunucu ID'leri işlenerek öğelerin sayfa yenilenince kaybolması engellendi.
- **Simkl Medya Detay Sayfası & 404 Koruması:** Simkl kütüphanesindeki öğelerin üzerine tıklandığında oluşan "Veri Yok" ve 404 hataları giderildi; kimlik çözümleyici Simkl ID'lerini doğru yöne kanalize ederek Jikan ve Simkl arama fallback mekanizmalarıyla güçlendirildi.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Nuvio Libtorrent P2P Streaming Engine, Full Seeding & Upload Controls, Simkl Batch Sync & Media Detail Fixes

- **Nuvio Libtorrent P2P Engine Integration (libtorrent + Boost.Asio):** Embedded Nuvio's high-performance C++ libtorrent and Boost.Asio core (`lib-nuvio-engine-android-0.1.2.aar`) directly into Kitsugi. Users can now stream torrent-based Stremio add-ons (such as Torrentio) entirely for free via peer-to-peer swarms without requiring a Debrid subscription.
- **Full Seeding & Upload Bandwidth Controls:** Addressed a critical privacy and bandwidth shortcoming in the original Nuvio app (which seeded indefinitely with no UI controls). Users can now toggle seeding on/off or limit upload bandwidth to 256 KB/s, 512 KB/s, 1 MB/s, or 2 MB/s directly from the Stremio settings tab.
- **P2P Privacy & Swarm Consent Dialog:** Implemented a one-time transparent consent dialog detailing public swarm IP visibility, copyright compliance responsibilities, and VPN usage recommendations before initiating any P2P torrent stream.
- **Real-Time Video Player Torrent Overlay:** Connected fullscreen player metrics to live engine telemetry, displaying instant download speeds, upload speeds, seeders, peers, and buffering progress, with automatic stream teardown on player exit.
- **Simkl Batch Synchronization & 429 Rate-Limit Prevention:** Adhered strictly to Simkl's 1 request/second policy and mandatory title/year metadata requirements. Synchronizes large libraries in 35-item batches with 1.1s throttling, followed by server-verified Room DB persistence to eliminate phantom entries and list reverts.
- **Simkl Media Detail Navigation & 404 Fallback:** Resolved missing detail screens and 404 errors when opening Simkl entries by prioritizing Simkl external IDs and adding Jikan & Simkl title search fallback strategies.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 İzole Süreç Çökme Ekranı (:crash), Canlı Sistem Teşhisi, Çapraz Senkronizasyon & Kararlılık Güncellemeleri

- **İzole Süreç Tabanlı Çökme & Teşhis Ekranı (Isolated :crash Process):** AndroidManifest'e `:crash` ayrılmış süreci entegre edildi. Ana uygulama süreci beklenmedik bir kritik hatayla veya bellek yetersizliğiyle karşılaşsa dahi, çökme ekranı (`KitsugiCrashActivity`) tamamen izole ve temiz bir süreçte anında açılır. Uygulamanın aniden sessizce kapanması ("pat diye kapanma") önlendi.
- **Hakkında Ekranından Doğrudan Teşhis & Rapor Erişimi:** Ayarlar altındaki Hakkında (About) ekranına yeni "Çökme & Hata Teşhis Raporu" butonu eklendi. Kullanıcılar ve test ekibi istedikleri an geçmiş çökme kayıtlarını, logcat çıktılarını ve cihaz donanım teşhisini inceleyebilir veya tek tıkla panoya kopyalayabilir.
- **Çoklu Platform Çapraz Senkronizasyon (Cross-Sync) Güçlendirmesi:** AniList, MyAnimeList, Simkl, Kitsu ve Shikimori platformları arasındaki kütüphane ve izleme geçmişi senkronizasyonu geliştirildi; diyalog akışı ve durum göstergeleri optimize edildi.
- **Kitsu Bildirim & Detay Entegrasyonu:** Kitsu bildirimlerine dokunulduğunda anime ve manga detay sayfalarının kesintisiz, doğru kimlik çözümlemesiyle ve önbellekten anında kapak aktarımıyla açılması garantilendi.
- **Shikimori 1-Tık Giriş ve Güvenlik Sertleştirmesi:** Shikimori OAuth2 bağlantısında yaşanan 403 ve token çözümleme problemleri kalıcı olarak giderildi.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Isolated :crash Process Architecture, Live System Diagnostics, Cross-Sync & Core Stability

- **Isolated Process Crash & Diagnostics Screen (:crash Process):** Dedicated isolated process architecture (`:crash`) wired into AndroidManifest. Even if the primary application process experiences an unhandled fatal error or OutOfMemory condition, `KitsugiCrashActivity` launches reliably within a clean sandbox without hangs or freeze loops, preventing abrupt app disappearance.
- **Direct Diagnostics & Crash Hub Access:** Added a dedicated "Crash & Diagnostics Report" button to the About screen. Users and testers can review past crash logs, live logcat output, and device hardware diagnostics on demand, or copy full reports to clipboard in one tap.
- **Multi-Platform Cross-Sync Enhancements:** Streamlined library cross-synchronization across AniList, MyAnimeList, Simkl, Kitsu, and Shikimori, complete with dialog feedback and state reconciliation.
- **Kitsu Notification & Media Detail Navigation:** Guaranteed smooth detail page transitions when launching from Kitsu push notifications, handling both raw and offset entity identifiers with instant poster caching.
- **Shikimori 1-Tap OAuth & Authorization Polish:** Strengthened Shikimori authentication flow against 403 Forbidden edge cases and token renewal hiccups.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 Shikimori 1-Tık Otomatik Giriş Çözümü, Kitsu Bildirim Detay Sayfası Düzeltmesi & Kararlılık Güncellemeleri

- **Shikimori 1-Tık Otomatik Giriş ve Deep Link Desteği:** Shikimori OAuth girişinde yaşanan 403 erişim engelleri ve yetkisiz istemci (invalid_client) sorunları kalıcı olarak çözüldü. Resmi `aniyomi://shikimori-auth` deep link yönlendirmesi entegre edildi. Kullanıcı tarayıcıda tek dokunuşla izin verdiğinde uygulama otomatik açılır ve oturumu başlatır.
- **Alternatif Manuel Giriş ve Otomatik Sıfırlama:** Tarayıcıda doğrulama kodunu doğrudan görüntüleyen `urn:ietf:wg:oauth:2.0:oob` protokolü korundu. Gelişmiş ayarlar menüsüne "Varsayılan Anahtarları Geri Yükle" butonu eklenerek cihaz hafızasında kalmış eski/hatalı anahtarların tek tıkla temizlenmesi sağlandı.
- **Kitsu Bildirimlerinden Medya Detayına Geçiş Sorunu Giderildi:** Bildirimler ekranındaki Kitsu bildirimlerine dokunulduğunda oluşan boş ekran veya çökme problemi düzeltildi. `KitsuExploreClient` ve `KitsugiDetailClient` içerisindeki kimlik çözümleme motoru güncellenerek hem ham Kitsu ID'leri hem de ofsetli (`+300,000,000`) ID'ler eksiksiz tanınır hale getirildi.
- **Görsel Bildirim Geçişleri:** Bildirimlerden detay sayfasına giderken anime/manga başlığı ve kapak görseli önbellekten anında aktarılarak sayfa açılışında görsel süreklilik ve akıcı bir deneyim sunuldu.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Shikimori 1-Tap OAuth & Deep Link Fix, Kitsu Notification Detail Resolution & Stability Improvements

- **Shikimori 1-Tap OAuth & Deep Link Integration:** Fixed 403 Forbidden and invalid_client issues during Shikimori authentication. Configured official `aniyomi://shikimori-auth` deep link callback scheme; tapping "Allow" in browser immediately returns to Kitsugi and authenticates seamlessly.
- **Alternative Out-of-Band Auth & Credential Reset:** Preserved manual code generation via `urn:ietf:wg:oauth:2.0:oob`. Added a "Reset to Default Keys" action in advanced settings to purge any corrupted client keys from app storage.
- **Kitsu Notification Media Detail Page Fix:** Resolved an issue where tapping on Kitsu notifications resulted in empty detail screens or failed navigation. Harmonized ID offset mapping in `KitsuExploreClient` and `KitsugiDetailClient` to reliably resolve both raw numeric IDs and offset stable IDs (`+300,000,000`).
- **Seamless Notification Visual Transition:** Titles and cover posters from incoming notifications are now forwarded directly to detail screens, ensuring instantaneous visual feedback without flicker.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 Gelişmiş Arama Filtre Paneli, İzole Arama Motorları, Resmi MAL API Entegrasyonu, Shikimori 1-Tık Giriş & Modern Çökme Raporlama

- **Yeni Nesil Arama Kaynak ve Filtre Seçici Paneli (Source Engine Filter Sheet):** Arama sayfasında motorların durumunu, aktifliklerini ve sonuç sayılarını gösteren, kategorilere (Anime, Dizi, Film, Manga, Roman vb.) göre filtrelenebilen yepyeni bir modal alt panel (`SourceEngineFilterSheet`) eklendi.
- **İzole Edilmiş ve Bağımsız Arama Sağlayıcıları:** Çoklu arama motorları birbirinden tamamen izole edildi; bir sağlayıcıda gecikme veya hata olduğunda diğer motorların (AniList, Kitsu, Shikimori, TMDB, Simkl vb.) arama sonuçları kesintisiz olarak anında ekrana gelir.
- **Resmi MyAnimeList (MAL) API Entegrasyonu:** Eski veya kararsız Jikan uç noktaları yerine MyAnimeList'in resmi OAuth2 API altyapısına geçildi. Arama hızı ve kütüphane güvenilirliği en üst seviyeye çıkarıldı.
- **Shikimori 1-Tık Giriş & 403 Forbidden Çözümü:** Shikimori OAuth girişinde yaşanan 403 erişim engeli ve token sorunları giderildi. Tek tıkla tarayıcı yetkilendirmesiyle sorunsuz profil bağlantısı sağlandı.
- **Kitsu & Shikimori Kapsamlı Profil ve Liste Yönetimi:** Profil ekranında Kitsu ve Shikimori hesapları için kütüphane durumları (İzleniyor/Okunuyor, Tamamlandı, Beklemede, Bırakıldı, Planlandı), puan dağılımı, favoriler ve biyografi eksiksiz görüntülenebilir ve güncellenebilir hale getirildi.
- **Gelişmiş Teşhis ve Çökme Raporlama Ekranı (KitsugiCrashActivity & Logger):** Uygulama beklenmeyen bir hatayla karşılaştığında kapanmak yerine modern bir arayüzle hatanın detayını, cihaz donanım bilgilerini ve bellek durumunu gösterir; logları tek tıkla kopyalama veya uygulamayı güvenle yeniden başlatma imkanı sunar.
- **Ayarlar Ekranı Modüler Hesap Sayfaları:** Ayarlar menüsündeki hesap yönetimi ayrıştırılarak AniList, MAL, Simkl, Kitsu ve Shikimori için özel alt sayfalar (`AccountSettingsSubPages`) oluşturuldu.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Advanced Search Filter Sheet, Isolated Search Engines, Official MAL API, Shikimori 1-Tap OAuth & Modern Crash Activity

- **Next-Gen Source Engine Filter Bottom Sheet:** Introduced a comprehensive modal bottom sheet (`SourceEngineFilterSheet`) on the Search screen with per-source toggles, status indicators, and categories (Anime, Movies, TV Series, Manga, Light Novels).
- **Isolated Multi-Search Provider Pipeline:** Search engines are now completely isolated; network timeouts or throttles on one provider no longer affect parallel search results from others (AniList, Kitsu, Shikimori, TMDB, Simkl).
- **Official MyAnimeList (MAL) API Migration:** Fully migrated search and library operations to official MyAnimeList OAuth2 endpoints, eliminating third-party rate limits and improving result speed.
- **Shikimori 1-Tap OAuth & 403 Forbidden Fix:** Fixed 403 Forbidden errors during Shikimori authorization, enabling an effortless 1-tap browser auth flow.
- **Kitsu & Shikimori Full Profile & Library Management:** Enriched profile screens with full status tracking (Watching/Reading, Completed, On Hold, Dropped, Plan to Watch/Read), score charts, favorites, and profile updates.
- **Advanced Diagnostics & Modern Crash Screen (KitsugiCrashActivity & Logger):** Replaced default unhandled exception crashes with a modern diagnostics screen showing breadcrumbs, system specifications, memory status, stacktrace copying, and 1-tap safe app restart.
- **Modular Account Settings Subpages:** Restructured account management into modular subpages (`AccountSettingsSubPages`) for AniList, MAL, Simkl, Kitsu, and Shikimori with clear connection status and sync actions.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 Zenginleştirilmiş Sistem Bildirimleri, Kitsu & Shikimori Profilleri, Profil Kaynak Seçici Paneli ve Orijinal Logolar

- **Zengin ve Görsel Sistem Bildirimleri (Drawer & Status Bar):** Android bildirim çekmecesi bildirimleri artık çok daha ayrıntılı ve yüksek çözünürlüklü kapak/avatar görselleriyle (`LargeIcon` & `BigPictureStyle`) donatıldı. Bildirimler artık hangi kaynaktan geldiğini açıkça belirtir (`[AniList]`, `[MyAnimeList]`, `[Simkl]`). Sistem çekmecesi bildirimleri ile uygulama içi bildirim ekranı metin ve biçimlendirme açısından birebir senkronize edildi.
- **Profil Sayfası Açılır Kaynak Seçim Paneli (Profile Source Picker Sheet):** Arama ve Keşfet sayfalarında olduğu gibi profil sayfasına da açılır seçim paneli (`ProfileSourcePickerSheet`) ve orijinal logolu hap butonu eklendi. Tek dokunuşla tüm bağlı/bağlantısız profiller arasında geçiş yapılabilir.
- **Kitsu ve Shikimori Profil Desteği Tamamlandı:** Profil sayfasına eksik olan Kitsu ve Shikimori sekmeleri eklendi. Kullanıcı biyografisi, waifu/husbando, takipçi verileri, anime/manga durum istatistikleri ve kütüphane kayıtları eksiksiz görüntülenir.
- **Orijinal Platform Logoları (Sıfır Emoji Kuralı):** Tüm sayfalardaki kaynak butonları, seçim panelleri ve sekmelerdeki emojiler veya boş kutular kaldırılarak yerini yüksek kaliteli orijinal platform logoları (`KitsugiPlatformLogo`) aldı.
- **Yeni GitHub Hesabı & Depoları Entegrasyonu:** Kod tabanı yeni `f26901964-eng/Kitsugi-Beta` ve `f26901964-eng/Kitsugi-Plugins` depolarına uyarlandı. Eklenti deposundan tüm +18 içerikler temizlenerek güvenli havuz devreye alındı.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Rich System Notifications, Kitsu & Shikimori Profiles, Source Picker Sheet & Original Logos

- **Rich Visual System Drawer Notifications:** Android status bar and drawer notifications now include high-resolution poster artwork and avatars via `LargeIcon` and `BigPictureStyle`. Every notification clearly highlights its origin source (`[AniList]`, `[MyAnimeList]`, `[Simkl]`), completely harmonized with the in-app notification center.
- **Profile Source Picker Bottom Sheet:** Added a modal selection sheet and original logo pill button to the Profile screen, matching the Search and Explore user experience.
- **Kitsu & Shikimori Full Profile Integration:** The Profile screen now fully supports Kitsu and Shikimori tabs, displaying bio, social metrics, anime/manga status breakdown charts, score distributions, and library records.
- **Original Platform Logos (Strictly No Emojis):** Replaced all emoji badges and generic colored boxes across buttons, sheets, and tabs with authentic, high-res platform vector logos (`KitsugiPlatformLogo`).
- **New GitHub Ecosystem Integration:** Fully wired the application and plugin repositories to the new `f26901964-eng` GitHub account, with an adult-free safe plugins pool and updated in-app update mechanisms.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 Kitsu Düzeltmeleri, Grid/Liste Hatırlama, Yapışkan Bar Filtresi, TMDB İngilizce Fallback, Simkl Dizi/Film Keşfeti ve Çökme Korumaları

- **Grid / Liste Görünümü Kalıcı Olarak Hatırlanıyor:** Popüler, Trend vb. tam ekran medya sayfalarındaki liste veya grid görünüm seçimi artık kaydedilir; sayfadan çıkıp tekrar girildiğinde kullanıcının son seçtiği görünüm modu korunur.
- **Yapışkan Üst Çubukta Filtre Butonu Düzeltildi:** Sayfa aşağı kaydırıldığında beliren kompakt üst çubukta filtre butonunun kaybolması sorunu çözüldü; filtreleme ve sıralama butonu artık her kategoride ve kaydırma durumunda görünür durumdadır.
- **TMDB Keşfet'te Türkçe Başlığı Olmayan İçerikler İçin Otomatik İngilizce Desteği:** TMDB'den çekilen içeriklerde Türkçe başlık bulunmadığında Japonca/Kanji başlık yerine otomatik olarak İngilizce başlık (örn. "Souryo to Majiwaru Shikiyoku no Yoru ni...", "Puella Magi Madoka Magica...") gösterilir.
- **Simkl Keşfet'e Dizi ve Film Desteği Eklendi:** Simkl platformu artık sadece animelerle kısıtlı kalmayıp TMDB gibi Trend Diziler, Trend Filmler, Popüler Diziler, Popüler Filmler ve en yüksek puanlı içerikleri eksiksiz gösterir.
- **Kitsu Anime Ekleme / Düzenleme Sayfası Düzeltildi:** Kitsu üzerinden açılan animelerde "Listeye Ekle / Düzenle" alt sayfasının MyAnimeList olarak açılması ve MAL alanlarının görünmesi sorunu çözüldü. Artık Kitsu renkleri (turuncu), Kitsu kimlik etiketi ve Kitsu'ya özgü liste durumu seçenekleri açılır.
- **Kitsu Karakterler Sekmesi Takılma / Yüklenmeme Sorunu Giderildi:** Anime detay sayfasında "Resimler" sekmesi eklendikten sonra meydana gelen indeks kayması giderildi; "Karakterler" sekmesine tıklandığında artık iskelet ekranda takılı kalmadan tüm karakterler eksiksiz yüklenir.
- **Kitsu Kişi Bilgisi ve Galeri Kaynak Çelişkisi Düzeltildi:** Kitsu'dan açılan yönetmen/seslendirmen/ekip detayında "ANILIST" yerine doğru platform ("Kitsu") etiketi gösterilir; profil fotoğrafı tam ekran açıldığında da "Jikan" ve "🎭 Karakter" yerine doğru kaynak ve "👤 Kişi" kategorisi kullanılır. Kitsu ID ile kişi adı önce doğrudan Kitsu API'den çözümlenerek doğru eşleştirme sağlanır.
- **Rastgele Uygulama Çökmeleri Engellendi (Crash Shield & Looper Koruması):** Sayfa geçişlerinde, video izlerken veya eklentilerden veri gelmesini beklerken arka plan iş parçacıklarında, ağ zaman aşımlarında ya da harici eklenti kodlarında oluşan beklenmedik istisnaların uygulamayı aniden kapatması engellendi. Ana iş parçacığı döngü koruyucusu (Cockroach Looper Recovery) ve `SupervisorJob` takviyesiyle donatıldı.
- **Eklenti Deposu (Kitsugi Plugins) Sorunsuz Yükleme Garantisi:** "Kitsugi Eklentileri (Önerilen)" deposu eklenirken yaşanan ISS engelleri ve ağ zaman aşımları tamamen çözüldü. Hem doğrudan Codeberg bağlantısı hem REST API hem de dahili çevrimdışı katalog yedeği devreye alındı.
- **Shikimori Giriş Akışı Düzeltildi:** "The requested redirect uri is malformed" hatası giderilerek RFC 6749 uyumlu URL-encode OAuth2 giriş akışı sağlandı.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Grid Persistence, Sticky Filter Button, TMDB English Fallback, Simkl TV/Movies & Crash Shield

- **Grid / List Mode Persistent Memory:** The layout toggle preference on full-screen media grid pages is now saved and remembered across visits.
- **Sticky Top Bar Filter Button Fix:** Fixed the filter and sorting button disappearing from the compact sticky header upon scrolling down; it is now always visible and positioned properly next to the layout toggle.
- **TMDB Explore English Title Fallback:** When Turkish titles are not localized on TMDB, titles automatically fall back to their English/Romaji counterparts instead of Japanese Kanji/Kana.
- **Simkl Explore TV & Movie Expansion:** Simkl explore is no longer limited to anime; it now features full TV show and movie sections (trending, popular, top-rated) matching the TMDB experience.
- **Kitsu Media Editor Sheet Fix:** Fixed Kitsu anime entries opening the edit sheet with "MyAnimeList" branding and MAL status fields. Proper Kitsu branding, theme color, and sync properties are now respected.
- **Kitsu Characters Tab Loading Fix:** Resolved tab index offset bug causing the Characters tab to freeze with skeleton loaders indefinitely; characters now load immediately.
- **Person / Staff Detail & Gallery Attribution Fix:** Resolved source attribution conflicts when opening staff from Kitsu (showing "ANILIST" badge or "Jikan / Character" in fullscreen gallery). Now correctly attributes Kitsu and "Person" category, with pre-resolution against Kitsu's `/people` API.
- **Random Crash Shield & Main Looper Protection:** Prevented random crashes during navigation, video playback, and extension scraping. Installed a resilient main Looper recovery loop and `SupervisorJob` scopes across extension loaders and prefetch coroutines to isolate non-fatal exceptions.
- **Plugin Repository Bulletproof Loading:** Resolved repository manifest loading failures caused by ISP blocks with Codeberg API fallback and bundled 174-plugin offline catalog.
- **Shikimori OAuth Redirect URI Fix:** Fixed "The requested redirect uri is malformed" error with proper RFC 6749 URL encoding and streamlined OAuth flow.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 Keşfet Arayüzü, "Tümü" Butonu, Kitsu & Shikimori Girişleri, Bildirim Önizleme ve Çapraz Eşitleme

- **Keşfet Sayfasında Çift Bar Fazlalığı Giderildi:** Keşfet sayfasında hem açılır seçim menüsü hem de alttaki yatay çip çubuğunun oluşturduğu karmaşa giderildi; gereksiz yatay çip barı kaldırılarak tekil, şık ve fonksiyonel kaynak seçici korundu.
- **Listem Sayfası "Tümü" Butonu Alt Bar ile Uyumlu Hale Getirildi:** "Tümü" kategori seçim butonu artık alt gezinti çubuğunun (Bottom Navigation Bar) üzerinde taşma veya örtüşme yapmaz. Diğer sayfalardaki FAB butonlarıyla birebir aynı biçimde, alt çubuğun tam üzerinde hizalanır ve kaydırma hareketleriyle pürüzsüzce animasyonlanır.
- **Kitsu Giriş Hatası ("Client authentication failed") Çözüldü:** Kitsu API OAuth2 token uç noktasına yönelik istemci anahtarları ve OAuth2 standart form-urlencoded yapısı güncellendi. Kullanıcılar artık e-posta/kullanıcı adı ve şifreleriyle doğrudan ve hatasız giriş yapabilir.
- **Shikimori Giriş Akışı Sadeleştirildi:** Kullanıcıdan karmaşık Client ID, Client Secret oluşturması isteme zorunluluğu kaldırıldı. Dahili kimlik bilgileriyle tek dokunuşla tarayıcıda Shikimori onay sayfası açılır, ekrandaki kod yapıştırılarak anında bağlantı kurulur.
- **AniList Bildirimlerine Tıklayınca Doğrudan Aktivite/Beğeni Detayı Açma:** Beğeni veya yorum bildirimlerine tıklandığında yalnızca körü körüne kullanıcı profiline gitmek yerine, tam olarak hangi paylaşımın/içeriğin beğenildiği veya yorumlandığını gösteren detay sayfası ve ilgili anime/dizi bağlantıları açılır.
- **Tüm Hesapları Birbiriyle Eşitle (5 Platform Çapraz Senkronizasyon) Düzeltildi:** AniList, MyAnimeList, Simkl, Kitsu ve Shikimori hesapları arasında anime, dizi ve film içerikleri eksiksiz olarak birbirine eşitlenir; her bağlı platformun kütüphanesindeki eksik içerikler tespit edilip hem yerel listelere hem de ilgili servislere sorunsuz aktarılır.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Explore UI, "All" FAB, Kitsu & Shikimori Auth, Activity Notifications & Cross-Sync Matrix

- **Explore Top Bar Deduplication:** Removed the redundant horizontal chip row below the platform selector pill on the Explore screen, keeping a clean and unified interface.
- **MyList "All" FAB Bottom Bar Alignment:** The category button now sits properly above the Material3 navigation bar without overlapping, animating smoothly on scroll just like floating action buttons on other screens.
- **Kitsu Authentication Fix:** Resolved the "Client authentication failed" error by adopting official Kitsu public OAuth2 credentials and urlencoded form specifications.
- **Simplified Shikimori OAuth Flow:** Users no longer need to create developer applications or enter client credentials. A 1-tap browser authorization with built-in keys allows pasting the code directly.
- **Notification Activity Preview:** Clicking activity or like notifications now opens the exact activity/post modal showing what was liked/commented with direct media links, instead of merely navigating to the user profile.
- **Full Cross-Platform Synchronization Matrix:** The "Sync All Accounts" feature now equalizes anime, TV shows, and movies across all connected accounts (AniList, MAL, Simkl, Kitsu, Shikimori), properly updating local tabs and cross-syncing missing library items.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 Eklenti Deposu ve Eklenti Yükleme Sorunları Tamamen Giderildi

- **Codeberg Depo ve Eklenti Yüklenememe Sorunu Çözüldü:** Repo URL doğrulayıcı (`RepoVerifier`), `codeberg.org` alan adını artık varsayılan olarak güvenilir listeye aldı. Ek olarak `.git`, depo kök dizini veya eski GitHub bağlantıları otomatik olarak optimize edilmiş Codeberg manifestine yönlendirildi.
- **"Eklentiler Yüklenemedi" Hatası & Otomatik Yenileme:** Ağ gecikmesi veya ilk açılışta başarısız olan depo isteklerinde eklenti listesinin takılı kalması engellendi; otomatik yeniden deneme ve akıllı önbellek yenileme mekanizması devreye alındı.
- **Ağ Zaman Aşımı & Proxy Desteği:** Yavaş bağlantılarda ve ISS kısıtlamalarında eklenti ve repo indirmelerinin zaman aşımına uğramaması için okuma/bağlantı süreleri 30 saniyeye çıkarıldı, jsDelivr CDN yedeği güçlendirildi.
- **Eski ve Bozuk Depoların Temizliği:** Veritabanındaki eski/çift veya hatalı depo adresleri açılışta otomatik olarak temizlenerek resmi `Kitsugi Plugins (Önerilen)` deposu altında birleştirildi.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Extension Repository & Plugin Loading Fixes

- **Codeberg Domain Trust & Repo Normalization:** `codeberg.org` is now unconditionally trusted in `RepoVerifier`. All repository formats (root URLs, `.git`, legacy GitHub links) now seamlessly normalize to canonical Codeberg endpoints.
- **Automatic Retry for Extension Listings:** Fixed an issue where temporary network failures left repo cards stuck on "Eklentiler yüklenemedi". Repos now auto-retry upon opening or refreshing.
- **Extended Timeouts & CDN Fallback:** Connect and read timeouts increased to 30s to prevent dropouts on throttled networks.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 Eklenti Kurulum Hatası Giderildi & Codeberg Eklenti Havuzu Canlıya Alındı

- **Eklenti Kurulumu ve İndirme Hatası Tamamen Çözüldü:** Yeni Codeberg `KitsugiPlugins` deposundaki tüm 174 eklenti (DiziPal, RecTV, FilmMakinesi, InatBox, TurkAnime vb.) artık sorunsuz, doğrudan ve tek tıkla kurulmaktadır. Dosya bütünlüğü ZIP doğrulamasıyla garanti altına alınmıştır.
- **Doğrudan Codeberg URL Koruması:** Eklenti indirme ve repo yenileme sırasında Codeberg URL'lerinin bozulması engellenmiş, eski GitHub bağlantıları otomatik olarak yeni depoya yönlendirilmiştir.
- **Sürüm Güncellemesi:** Önceki derlemelerdeki önbellek çakışmalarını gidermek amacıyla v2.4.194 olarak paketlenmiştir.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Plugin Installation Error Fixed & Codeberg Plugin Pool Live

- **Extension Download & Installation Completely Fixed:** All 174 plugins (DiziPal, RecTV, FilmMakinesi, InatBox, TurkAnime, etc.) in the new Codeberg `KitsugiPlugins` repository now install seamlessly with 1-click installation.
- **Direct Codeberg URL Preservation:** Plugin download URLs are safely preserved and legacy GitHub endpoints are automatically routed to the new repository.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 Codeberg Entegrasyonu & Eklenti Havuzu Güvenliği (+18 Temizlendi)

- **Codeberg Otomatik Güncelleme Entegrasyonu:** Uygulama içi otomatik güncelleyici (`KitsugiUpdateRepository`) artık birincil güncelleme kaynağı olarak Codeberg Releases API'sini (`BlackDamage/Kitsugi-Beta`) kullanır. Olası bir durumda GitHub API'sine yedek (fallback) olarak bağlanır.
- **Güvenli ve Temiz Eklenti Deposu (+18 / NSFW Kaldırıldı):** Eklenti deposu GitHub'dan Codeberg'e (`BlackDamage/Kitsugi-Plugins`) taşındı. Topluluk ve mağaza kurallarına tam uyum için tüm yetişkin (+18/NSFW), ifşa ve deepfake eklentileri havuzdan kalıcı olarak temizlendi; 170+ popüler dizi, film, anime ve belgesel eklentisi (DiziPal, RecTV, FilmMakinesi, InatBox, TurkAnime vb.) optimize edilerek korundu.
- **Eski Depo URL'lerini Otomatik Yönlendirme:** Uygulama açılışında ve eklenti taramasında eski GitHub bağlantıları (`gameras1010-afk`, `KitsugiBeta-dev`, `Kekik-cloudstream`) otomatik olarak yeni Codeberg deposuna yönlendirilir.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Codeberg Migration & Clean Plugin Repository (+18 Excluded)

- **Codeberg In-App Auto-Updates:** Primary update checking and release asset downloading has been migrated to the Codeberg Releases API (`BlackDamage/Kitsugi-Beta`) with GitHub fallback.
- **Clean & Safe Plugin Repository (+18 Excluded):** Migrated cloudstream plugins to Codeberg (`BlackDamage/Kitsugi-Plugins`), completely removing 116+ NSFW / adult / deepfake plugins to guarantee longevity and platform TOS compliance. All 170+ mainstream movie, anime, TV, and documentary sources remain fully available and updated.
- **Automatic Legacy URL Redirection:** Legacy GitHub URLs for plugins are seamlessly redirected to the new Codeberg repository.

---

## 🇹🇷 Türkçe (v2.4.194)

### 📚 Türkçe Manga & Webtoon Altyapısı Kökten Yenilendi (Keiyoushi V2, 77 TR Kaynağı, MangaDex 3.450 TR Başlık, Hotlink Çözümü)

- **Keiyoushi V2 Repo Şeması & Decoy Koruması:** Keiyoushi'nin modern `index.json` V2 şeması (`extensionList.extensions[]`) tam olarak desteklendi. 2 sahte kayıtlı decoy `index.min.json` otomatik bypass edilerek doğrudan `index.json` üzerinden tüm eklentiler listelenir hale getirildi. 404 veren ölü katalog URL'leri temizlendi.
- **2026 Keiyoushi İmza Anahtarı:** Yeni SHA-256 parmak izi (`9add655a...`) `TrustManager`'a eklendi ve repodan gelen eklentiler için dinamik güven zinciri entegre edildi.
- **Modern Eklenti API'sine Geçiş (extensionLib 1.6+):** 77 Türkçe eklentinin 71'inde detay ve bölüm listelerinin boş gelmesine sebep olan eski deprecated API çağrıları kaldırıldı; modern `getMangaUpdate` suspend API'sine geçildi.
- **MangaDex & Global Katalog Aramaya Dahil Edildi (3.450 TR Başlık):** `MangaSearchCoordinator` filtresindeki mantıksal engel kaldırılarak MangaDex gibi çok dilli global kaynaklar arama adaylarına eklendi. TR kaynaklarda bulunamayan mangalar MangaDex'teki 3.450 Türkçe çeviri üzerinden anında bulunabilir hale geldi.
- **Sayfa Görsellerinde Hotlink Koruması Çözüldü:** Manga-TR, TRManga, WebtoonHattı gibi hotlink korumalı CDN'lerin 403 Forbidden dönmesi engellendi. `MangaPageLoaderV2`, görselleri kaynağın kendi `Referer`, özel User-Agent ve Cloudflare interceptor'ları ile indirip doğrudan disk önbelleğine yazar ve Telephoto okuyucuya sunar.
- **Arama Eşzamanlılık Kapısı (Semaphore 6) & Akıllı Zaman Aşımı:** Aynı anda 70+ kaynağa istek atıp 429 veya bağlantı kopması yaşanmasını engellemek için 6'lı eşzamanlılık kapısı eklendi. Yeni nesil Keiyoushi motoru tanınarak zaman aşımı 25 saniyeye optimize edildi.
- **İki Geçişli Arama & Doujinshi Filtresi:** Arama sonuçlarının ilk turda gereksiz doujinshi veya spin-off'larla dolması engellendi. Önce sıkı skorlama yapılır, sonuç yoksa gevşek skorlama devreye girerek doğru manga ilk sıraya yerleştirilir.
- **Kaynak Sağlık Sınıflandırması İyileştirildi:** Arama teriminin bulunamaması (404) veya geçici DNS hataları ("unable to resolve host") artık kaynakları 2 saatlik "Broken" cezasına sokmaz; "Degraded" seviyesinde tutularak kaynakların kaybolması engellendi.
- **Eksik Host Shim Sınıfları Eklendi:** Eklentilerin ihtiyaç duyduğu `AppInfo`, `UnmeteredSource`, `RateLimitInterceptor` gibi sınıflar shim katmanına eklenerek sınıf yükleme çökmeleri giderildi.

---

## 🇬🇧 English (v2.4.194)

### 📚 Turkish Manga & Webtoon Engine Complete Overhaul (Keiyoushi V2, 77 TR Sources, MangaDex 3,450 TR Titles, Hotlink Bypass)

- **Keiyoushi V2 Schema & Decoy Bypass:** Added full support for the modern Keiyoushi V2 `index.json` schema (`extensionList.extensions[]`). Automatically bypasses the decoy 2-entry `index.min.json` and loads extensions directly from `index.json`. Removed dead 404 catalog URLs.
- **2026 Signing Key Trust:** Added the latest Keiyoushi SHA-256 fingerprint (`9add655a...`) to `TrustManager` and established dynamic signature trust for repository extensions.
- **Modern Extension API (extensionLib 1.6+):** Migrated from deprecated `fetchMangaDetails`/`fetchChapterList` stub methods to the modern `getMangaUpdate` suspend API, restoring detail and chapter lists across 71+ Turkish extensions.
- **MangaDex & Global Catalogs in Search (3,450 TR Titles):** Fixed a boolean logic barrier in `MangaSearchCoordinator`, enabling MangaDex and trusted global catalogs to participate in manga searches alongside Turkish sources.
- **Hotlink Protection Bypass in Image Loader:** Resolved 403 Forbidden errors on hotlink-protected CDNs (Manga-TR, TRManga, WebtoonHattı). `MangaPageLoaderV2` now prioritizes the extension's native `source.getImage()` pipeline with correct `Referer`, User-Agent, and Cloudflare cookies directly into disk cache for the Telephoto reader.
- **Concurrency Gate (Semaphore 6) & Smart Timeouts:** Prevented network congestion and 429 rate limits by bounding concurrent searches to 6 parallel requests. Added modern `KEI_SOURCE` engine detection and increased timeout to 25s for stable scraping on mobile networks.
- **Two-Pass Search Scoring & Doujinshi Guard:** Searches now execute a strict first-pass match before relaxing filters, preventing doujinshi and loose spinoffs from polluting main search results.
- **Softened Health Classifier:** Network DNS errors ("unable to resolve host") and search 404s now mark sources as Degraded rather than Broken, preventing false 2-hour cooldown lockouts.
- **Missing Extension Host Shims:** Added missing `AppInfo`, `UnmeteredSource`, and `RateLimitInterceptor` host shims to prevent ClassNotFound exceptions when loading extensions.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🎭 TMDB Kurgusal Karakter Detayları, Seslendirmen Ayrımı, AL Arama ve Kesintisiz Keşfet (Kitsu Fallback)

- **TMDB Kurgusal Karakter Detay Sayfası Düzeltildi:** TMDB yapımlarındaki anime/kurgusal karakterlere (ör. Nobita Nobi, Doraemon, Rudeus Greyrat) tıklandığında seslendirmenin (ör. Megumi Oohara, Wasabi Mizuta) biyografi ve doğum günü yerine doğrudan karakterin kurgusal biyografisi, anime rolleri ve tüm seslendirmenlerinin listelendiği sayfa açılacak şekilde düzeltildi.
- **Karakter Görsel Karışıklığı Kökten Çözüldü:** Mushoku Tensei gibi aynı soyadını ("Greyrat") taşıyan karakterlerin (Rudeus, Former Self, Eris) ve rol varyantlarının yanlış token eşleşmesiyle aynı görseli alması engellendi. Öz isim doğrulaması (given-name guard) ve varyant filtreleri eklendi.
- **AniList (AL) Arama Sorunu Giderildi:** AniList GraphQL sorgusunda eksik olan `$isLicensed: Boolean` tanımı eklenerek sunucudan HTTP 400 Bad Request dönmesi ve aramalarda AL satırının kaybolması düzeltildi.
- **AniList Keşfet Sayfası & Kitsu Otomatik Fallback:** AniList keşfet sayfasındaki genel erişim iyileştirildi; sunucu hatası, hız sınırı veya boş liste durumlarında Kitsu keşfet motorunun otomatik devreye girerek listeleri eksiksiz doldurması sağlandı.

---

## 🇬🇧 English (v2.4.194)

### 🎭 TMDB Fictional Character Details, Voice Actor Separation, AL Search & Seamless Kitsu Fallback

- **Fictional Character Resolution in TMDB:** Tapping anime characters in TMDB entries now reliably opens the character's fictional profile (biography, appearances, and voice actors) via AniList/Jikan rather than displaying the real voice actor's personal biography and birthday.
- **Character Image Pollution Fixed:** Resolved an issue where characters with shared family surnames (e.g., Greyrat in Mushoku Tensei) or role variants ("Former Self") mistakenly received the same image. A robust given-name guard and variant modifier matching algorithm ensures exact individual character mapping.
- **AniList Search Restored:** Fixed an HTTP 400 Bad Request caused by a missing `$isLicensed: Boolean` definition in the AniList GraphQL search query. AniList results now reliably appear in both "All" and "Anime" search tabs.
- **AniList Explore & Seamless Kitsu Fallback:** Restored public AniList explore browsing without token requirements. Whenever AniList returns empty data or encounters issues, Kitsu automatically and seamlessly populates the explore carousels.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🎬 TMDB Film/Dizi ID Eşleme Düzeltmesi & Yorumlardaki Resim Büyüme Sorunu Çözüldü

- **TMDB Anime Filmleri Diziyle Karışma Sorunu Düzeltildi:** TMDB keşfet sayfalarında "Howl's Moving Castle" (Yürüyen Şato) gibi anime filmlerine tıklandığında film yerine aynı TMDB ID numarasına sahip "Roar" adlı TV dizisinin açılması sorunu giderildi. TMDB anime filmlerinin türü doğru şekilde `MediaType.Movie` olarak belirlendi, başlık doğrulaması eklendi ve önbellek film/dizi ayrımıyla güçlendirildi.
- **Yorumlar & Tartışmalardaki Resimlerin Yavaşça Büyümesi Düzeltildi:** Konu detayı ve yorumlarda paylaşılan görsellerin ve GIF'lerin sayfa açıldıkça veya render edildikçe yavaşça kendi kendine büyüyüp genişlemesi (Compose `animateContentSize` döngüsü) durduruldu. Görseller artık anında sabit orantıyla yüklenir, maksimum yükseklik sınırı (240dp) ile kart düzenini bozmaz ve tıklandığında tam ekran galeri açılır.
- **İndirme Ayarları Veri Yönetiminden Ayrıldı:** Veri & Yedekleme içindeki "İndirmeler" sekmesi oradan çıkarıldı; Ayarlar menüsünde doğrudan "İndirme Ayarları" bağımsız bir sayfa olarak yer aldı. Veri & Yedekleme ekranı sadeleştirildi.
- **İndirmeler Ekranı Sağa/Sola Kaydırılabilir Yapıldı:** İndirmeler sayfasındaki Videolar, Altyazılar ve Resimler sekmeleri arasında parmakla sağa ve sola kaydırarak (swipe gesture) geçiş desteği eklendi.
- **Arama Sayfası Yukarı Kaydırma Butonu & Alt Bar Senkronizasyonu:** Arama sayfasında aşağı kaydırırken alt bar ile yukarı kaydırma butonunun (FAB) üst üste binmesi sorunu giderildi. Buton alt barın durumuna göre dinamik olarak barın üzerinde konumlanır, butona tıklandığında alt bar anında geri gelir ve sayfa tepeye ulaştığında alt barın görünür kalması sağlandı.

---

## 🇬🇧 English (v2.4.194)

### 🎬 TMDB Movie/TV ID Mapping Fix, Comment Image Auto-Expansion Resolved & Downloads Navigation

- **TMDB Anime Movie/TV Confusion Resolved:** Fixed an issue where clicking anime movies on TMDB explore (such as *Howl's Moving Castle*) opened a TV show (*Roar*) with the same TMDB ID. Anime movies are now accurately typed as `MediaType.Movie`, verified by title matching, and differentiated in Room cache.
- **Forum & Comment Image Auto-Growing Fixed:** Fixed the issue where inline images and GIFs in discussion threads and comments slowly expanded on their own due to Compose `animateContentSize` layout passes. Inline images now display immediately with fixed bounds (max 240dp height), preserving clean comment layouts.
- **Dedicated Download Settings Subpage:** Removed the download settings tab from inside "Data & Backup". Created a clean, standalone "Download Settings" item in the Settings menu for quick access.
- **Horizontal Swipe for Downloads Screen:** The Downloads screen (Videos, Subtitles, Images) is now horizontally swipeable with smooth paging gestures.
- **Search Screen Scroll-to-Top & Bottom Bar Sync:** Fixed the overlap between the scroll-to-top floating button and the bottom navigation bar on the Search screen. The button dynamically floats above the bottom bar, and tapping it immediately restores the bottom bar while scrolling to the top.
