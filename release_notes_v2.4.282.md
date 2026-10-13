# Kitsugi Beta v2.4.282

## 🇹🇷 Türkçe

### 1. Plazma Küre Yenileme Animasyonu (Pull-to-Refresh)
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

### 1. Plasma Orb Pull-to-Refresh Animation
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
