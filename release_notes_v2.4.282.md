# Kitsugi Beta v2.4.282

## 🇹🇷 Türkçe

### 1. Fandom Uygulama İçi Wiki Okuyucu, İlgili Sayfa Kartları ve Evren Rehberi Toplu Yükleme
- **Tam ekran uygulama içi wiki okuyucu:** Wiki bağlantılarına veya özet kartlarına dokunulduğunda harici tarayıcıya atlama tamamen kaldırıldı. Sayfalar, içindekiler tablosu, tembel yüklenen bölümler ve geçmiş yığını (`BackHandler`) ile doğrudan uygulama içinde okunabilir.
- **İlgili wiki sayfaları şeridi:** Yapım ve karakter wiki sekmelerinde, ayrıca okuyucu içinde sayfadan çıkarılan dahili wiki bağlantıları yatay kaydırılabilir görsel kartlar olarak sunulur.
- **Evren rehberi tek dokunuşla toplu yükleme:** "Devamını göster (+6)" butonu artık sayfayı parça parça çekmek yerine kategorideki kalan tüm yapımları/öğeleri tek tıklamayla yükler.
- **Sıfır tarayıcı yönlendirmesi:** "Wiki'de aç" butonları yerini uygulama içi okuyucuya bıraktı; harici tarayıcı yalnızca kullanıcı üst bardaki dünya simgesine bilerek dokunduğunda açılır.

### 2. Bangumi İngilizce Başlık, Keşfet Şeritleri ve Kitaplık Düzeltmesi
- **Gerçek dil ayrımı:** `BangumiSubjectTitleResolver` ve `BangumiTitleCache` üzerinde İngilizce, Romaji ve Özgün başlıklar birbirinden ayrıldı. Latin harfli Japonca başlıkların sahte İngilizce olarak önbelleğe alınması engellendi.
- **Keşfet ve arama entegrasyonu:** Bangumi arama sonuçları, vitrin rafları, "Tümünü Gör" sayfaları ve profil yapım listeleri kullanıcının başlık dili tercihine (İngilizce / Romaji / Özgün) tam olarak uyar.
- **Kitaplık başlık zenginleştirici:** İçe aktarılan veya takip edilen Bangumi kayıtları için İngilizce başlık çözümlemesi güvenilir kaynaklardan tamamlanır.

### 3. Karakter / Ekip / Seslendiren Wiki Sekmesi: Gerçek "Tekrar Dene" ve Bayat Önbellek Düzeltmesi
- **Ölü "Tekrar dene" düğmesi düzeltildi:** Karakter, ekip ve seslendirme sanatçısı detayındaki Wiki sekmesinin "Tekrar dene" düğmesi yalnızca yeniden bağlanıyordu; karakter ve yapım katmanlarındaki "bulunamadı" önbelleği ile süreç içi zehir listesi aynı sonucu ağa çıkmadan geri veriyordu. Artık yeniden deneme; önbellekleri (olumsuz kayıtlar dahil) atlayıp, zehir kayıtları temizleyerek çözümlemeyi baştan yapar.
- **Bayat "bulunamadı" süresi kısaltıldı:** 3 gün → 1 gün.
- **Geçici kesintiler kalıcı "bulunamadı" yazmıyor:** HTTP 403/408 (Cloudflare/WMF bot koruması, zaman aşımı) ve kimlik köprüsü (AniList/Kitsu/Simkl) istisnaları geçici hata sayılır; Wikidata karakter yedeğinde tek kimliğin ağ hatası tüm zinciri kırmaz.
- **Sonuç:** Fandom sayfası var olan karakterlerde (ör. Date A Live → Kotori Itsuka) geçici bir kesinti sonrası günlerce süren "Wiki bulunamadı" durumu ve tepkisiz yeniden deneme düğmesi giderildi.

---

## 🇬🇧 English

### 1. Fandom In-App Wiki Reader, Related Page Cards & Category Batch Loader
- **Full-screen in-app wiki reader:** Replaced external browser redirects with an integrated reader featuring table of contents, lazy-loaded section content, internal link chips, and full backstack navigation.
- **Related wiki pages strip:** Horizontal preview cards on media and character wiki tabs as well as inside the reader, displaying contextual internal wiki links with titles and cover images.
- **Category guide batch loader:** "Show more (+6)" button now loads all remaining items in the category with a single click instead of requiring repetitive page taps.
- **Zero browser jumping:** Header and summary buttons open inside the in-app reader; external browser is only launched when the user explicitly taps the globe action button.

### 2. Bangumi English Title Discovery, Explore Shelves & Library Hydrator Fix
- **Strict title categorization:** Differentiates English, Romaji, and Native titles in `BangumiSubjectTitleResolver` and `BangumiTitleCache`, preventing Romaji titles from being mistakenly cached as English.
- **Explore & search parity:** Explore shelves, "See All" grids, search results, and profile lists strictly honor user display title preferences.
- **Library title hydrator:** Reliable English title resolution for tracked and imported Bangumi entries.

### 3. Character / Staff / Voice-actor Wiki: True Retry & Stale Negative Cache Fix
- **Dead "Retry" button resolved:** Wiki tab retry on character, staff, and voice-actor pages now clears poison entries (`forgetMissingWikis()`) and force-refreshes bypassing all cached negative states.
- **Stale negative cache shortened:** Reduced from 3 days to 1 day.
- **Transient outages no longer persist "not found":** HTTP 403/408 and identity-bridge errors are treated as transient; Wikidata character fallback no longer aborts upon a single failure.
- **Result:** Characters with live Fandom articles (e.g. Date A Live → Kotori Itsuka) recover immediately upon retry after transient network hiccups.
