# Kitsugi Beta v2.4.300

## 🇹🇷 Türkçe

### 🎭 Çok Dilli Spoiler Motoru & Tür/Etiket Paritesi (AniList, Bangumi, MAL, Shikimori, Kitsu, Simkl, TMDB, TVMaze)
- **AniList ile Birebir İki Katmanlı Spoiler Mantığı:** Desteklenen tüm kaynaklarda (AniList, Bangumi, MAL, Shikimori, Kitsu, Simkl, TMDB, TVMaze) hem Etiketler hem de Türler bölümleri AniList'in spoiler standartlarına tam uyumlu hâle getirildi.
- **Ortak Çok Dilli Doğal Spoiler Tespiti (`KitsugiSpoilerRules`):** Bangumi Tag Sözlüğü (907 grup, CJK/EN/TR) ve çok dilli anahtar kümesi üzerinden hikâye sonunu, karakter ölümünü, ihanet/NTR'yi veya kritik ters köşeleri açığa vuran temalar (*Ölüm/Death/便当, İntihar/Suicide/自杀, İhanet/Betrayal/背叛, Sürpriz Gelişme/Plot Twist/神展开, Zaman Döngüsü/Time Loop/轮回, Hafıza Kaybı/Amnesia/失忆, Trajedi/Tragedy/悲剧, Kötü Son/Bad Ending/全灭, Yandere/病娇* vb.) anında spoiler olarak işaretlenir.
- **Türler (Genres) Bölümünde Spoiler Kontrolü & Gizleme:** Türler listesine sızan alt temalar (Tragedy, Time Loop vb.) varsayılan olarak gizlenir. Kart başlığında `🔒 Spoiler'ları Göster / Spoiler'ları Gizle` anahtarı yer alır; spoiler türler açıldığında kırmızı (`KitsugiColors.AccentRed`) çip olarak çizilir. *Aksiyon, Komedi, Dram, Romantik, Fantezi, Bilim Kurgu* gibi ana türler asla yanlışlıkla gizlenmez.
- **Etiketler (Tags) Kartı İyileştirmesi:** Kart daraltılmış (`!expanded`) moddayken bile "Spoiler'ları Göster" tıklandığında 10. sıradan sonraki tüm kırmızı spoiler etiketler anında görünür hale gelir. Mükerrer etiket birleştirmesinde `isSpoiler` bayrağı ve AniList `% rank` alaka düzeyi korunur.
- **AniList GraphQL & Çapraz Zenginleştirme:** AniList sorgusuna `isGeneralSpoiler`, `id`, `description`, `category` eklendi. Bangumi, MAL, Shikimori, Kitsu, Simkl ve TMDB detayları AniList'in yapıma özel spoiler etiketleriyle zenginleştirilir; birincil kaynağın özgün etiket adı korunarak çipe tıklandığında doğru kaynak araması yapılması sağlanır.
- **Kitsu & TMDB Etiket Desteği:** Kitsu anime/manga isteklerine `?include=categories` eklenerek kategori ve etiketler çekildi; TMDB istemcisine `keywords` eklenerek anahtar kelimeler spoiler denetimli etiketlere dönüştürüldü.

---

### 🖼️ Yenilenen Keşfet Vitrini (Yatay Görsel Doğrulama & Akıcı HorizontalPager Kaydırma)
- **Kitap & Manga Türleri Vitrinden Ayrıldı:** Manga, manhwa/manhua ve romanlar vitrin havuzundan elendi; normal katalog ve keşfet raflarındaki yerleri aynen korundu.
- **Dengeli Kota Dağılımı:** "Tümü" görünümünde mevcut her kaynak/bölümden 1 rastgele öğe; "Tek Kaynak" görünümünde ise her bölümden 2 rastgele öğe seçilir ("Yakında Yayında", popüler, trend, en yüksek puanlı, vizyondaki ve sezon bölümleri dahil).
- **Gerçek Yatay Görsel Şartı (`HomeHeroArtworkLoader`):** Yalnızca gerçek boyutları yatay (`width > height`) olan ve hatasız yüklenebilen görseller kabul edilir. Kare, dikey afiş veya bozuk görseller elenerek aynı bölümün diğer rastgele adayları denenir; vitrin dikey posterle doldurulmaz.
- **Sabit Yükseklik & Zıplamasız Yerleşim:** Vitrinin yüksekliği artık görsele göre değişmez; cihaz bazlı sabit yükseklik kullanılır. Görsel, başlık, logo ve meta veriler gerçek `HorizontalPager` ile tek bir sayfada birlikte pürüzsüzce kayar; eski katman değiştirme ve sıçramalı sürükleme tamamen kaldırıldı.
- **Kararlı Logo Yuvası:** Logo yüklenirken alanı sabit kalır; yüklenemezse veya logo kapalıysa başlık metni bozulmadan ekranda kalmaya devam eder.
- **Akıllı Otomatik Geçiş:** Otomatik kaydırma dokunma, sürükleme ve TV kumanda odağı sırasında duraklar; sona gelindiğinde başa zıplamak yerine tek adımlı ters yön geçişiyle akıcılığı korur.

---

## 🇬🇧 English

### 🎭 Multilingual Spoiler Engine & Genre/Tag Parity (AniList, Bangumi, MAL, Shikimori, Kitsu, Simkl, TMDB, TVMaze)
- **Two-Tier AniList Spoiler Standard Across All Sources:** Unified spoiler logic adhering to AniList's standards across AniList, Bangumi, MyAnimeList, Shikimori, Kitsu, Simkl, TMDB, and TVMaze for both Genres and Tags sections.
- **Intrinsic Multilingual Spoiler Detection (`KitsugiSpoilerRules`):** Powered by Bangumi Tag Dictionary (907 canonical groups, CJK/EN/TR) and multilingual patterns detecting major plot disclosures (*Death, Suicide, Betrayal, Plot Twist, Time Loop, Amnesia, Tragedy, Bad Ending, Yandere*, etc.) across all languages.
- **Genres Card Spoiler Protection & Toggle:** Sub-themes mixed into genres (such as Tragedy or Time Loop) are hidden by default. A `🔒 Show Spoilers / Hide Spoilers` button appears in the card header, revealing spoiler genres as highlighted red chips. Core genres (*Action, Comedy, Drama, Romance, Fantasy, Sci-Fi*) are guaranteed to remain visible.
- **Tags Card Expansion & Deduplication Fix:** Clicking "Show Spoilers" now reveals all spoiler tags immediately even when the card is collapsed. Deduplication preserves `isSpoiler = true` and AniList percentage rank.
- **AniList GraphQL Expansion & Cross-Enrichment:** Added `isGeneralSpoiler`, `id`, `description`, `category` to AniList queries. Bangumi, MAL, Shikimori, Kitsu, Simkl, and TMDB items are cross-enriched with AniList spoiler tags while preserving original source tag names for native search clicks.
- **Kitsu & TMDB Keywords Integration:** Added `?include=categories` to Kitsu anime/manga detail queries and added `keywords` to TMDB to parse spoiler-checked tags.

---

### 🖼️ Revamped Explore Showcase (Horizontal Artwork Validation & Smooth Pager)
- **Books & Manga Excluded from Showcase:** Manga, manhwa, manhua, and light novels are filtered out of the showcase carousel while preserving their standard catalog shelves.
- **Balanced Showcase Quotas:** "All Sources" selects 1 random item per source/shelf; "Single Source" selects 2 random items per shelf (including "Coming Soon", Trending, Top Rated, Airing, and Seasonal).
- **Strict Landscape Artwork Validation (`HomeHeroArtworkLoader`):** Only images decoded with `width > height` are accepted. Square, vertical posters, or failing images are skipped in favor of alternative candidates from the same shelf without vertical poster fallbacks.
- **Fixed Height & Seamless Motion:** Viewport height remains constant regardless of image aspect ratio. Artwork, title, logo, and meta information glide together in a unified native `HorizontalPager`.
- **Stable Logo Slot:** Reserved logo space prevents layout shifts; failure to load or disabled logos seamlessly preserves the text title.
- **Interactive Auto-Scroll:** Pauses during user drag, touch, or TV focus, reversing direction smoothly at endpoints instead of hard rewinding.
