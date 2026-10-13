# Kitsugi Beta v2.4.283

## 🇹🇷 Türkçe

### 1. Wiki Okuyucu "Fandom sayfasını" Butonu Çökmesi Giderildi (Kritik Düzeltme)
- **Belirti:** Yapım, karakter veya ekip detayındaki Wiki sekmesinde "İlgili wiki sayfaları" şeridindeki **"Fandom sayfasını"** butonuna basıldığında veya dahili wiki bağlantısı tıklandığında, okuyucu sayfası yüklenir yüklenmez uygulama çöküyordu (`IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints`).
- **Kök neden:** Detay sayfası sekme içeriği portre düzende `DetailPageScaffold` içindeki `HorizontalPager` tarafından **sınırsız yükseklik** (`Constraints.Infinity`) ile ölçülüyor; yatay/TV düzeninde ise sekme içeriği zaten bir `verticalScroll` kutusunun içinde bulunuyor. Okuyucu bu ağacın içine gömülü çizildiğinde kendi kaydırma alanı sonsuz kısıt alıyor ya da iç içe kaydırma kısıtı nedeniyle Compose yasaklı istisnayı fırlatıyordu.
- **Çözüm:** Uygulama içi wiki okuyucu artık **kendi tam ekran penceresinde** (tam ekran `Dialog`) açılıyor. Dialog penceresi sistem seviyesinde her zaman sınırlı (bounded) kısıt verdiğinden bu çökme sınıfı kökten ortadan kaldırıldı.
- **Gezinme ve Görünüm Korundu:** Başlık çubuğu, içindekiler tablosu, bölüm içerikleri, ilgili sayfalar şeridi ve geri yığını (`BackHandler` — önceki wiki sayfasına dönüş) eksiksiz çalışır; durum çubuğu ve gezinme çubuğu boşlukları (edge-to-edge) ile MIUI/HyperOS force-dark koruması pencereye uygulandı.
- **Çift çizim temizliği:** Karakter detay sayfasında kök seviyedeki mükerrer okuyucu render'ı kaldırıldı, sekme üzerinden tek noktadan pencere açılması sağlandı.

### 2. Galeri "Detaylar" Levhası Sınırsız Yükseklik Koruması
- Galeri ekranındaki "Detaylar" bilgi levhasına, uygulamanın diğer tüm alt sayfalarında bulunan `heightIn(max = ekranın %85'i)` kısıtı eklendi. Böylece alt sayfa sınırsız yükseklikle ölçülse bile aynı dikey kaydırma çökmesinin oluşması engellendi.

### 3. Plazma Küre Yenileme Animasyonu (Pull-to-Refresh)
- **Tüm yenileme göstergeleri plazma küre oldu:** Profil, Keşfet, Listem, medya detay iskeleti (`DetailPageScaffold` — tüm film ve dizi detayları), karakter detayı, ekip detayı, kullanıcı profili ve kullanıcı listesi ekranlarındaki varsayılan Material dönen ok göstergesi tamamen kaldırıldı.
- **Ortak `KitsugiPullToRefreshIndicator` bileşeni:** Tam ekran yüklenme animasyonundaki parlayan plazma kürenin (`KitsugiPlasmaLoader`) birebir aynısı kullanıldı. Çekmeye başlayınca belirip çekiş mesafesiyle büyür, yenileme sürerken tam boyunda atmaya devam eder, boşta duran sayfalarda hiç çizilmez (sıfır performans maliyeti).

### 4. Wiki "Hayır, yanlış" Eşleşme Kararını Geri Alma & Toplu Kurtarma
- **Geri alma kartı (`WikiRejectedUndoCard`):** "Bu eşleşme doğru mu?" kartında yanlışlıkla "Hayır, yanlış"a basıldığında, Wiki sekmesinin en üstünde **"Eşleşme yanlış işaretlendi — [Geri al] / [Kapat]"** kartı belirir. Sıradaki aday bulunsa da, hiç wiki bulunamasa da kart üstte görünür kalır; "Geri al" tek dokunuşla reddi kaldırıp eşleşmeyi yeniden çözer.
- **Ayarlardan toplu kurtarma:** Kart kapatılsa bile kurtarma yolu mevcuttur: **Ayarlar → Fandom Wiki → "Reddedilen eşleşmeleri geri getir"** seçeneğiyle, onaylanan eşleşmelere dokunulmadan tüm yanlış retler silinir ve wiki'ler tekrar önerilir.
- **Açıklama metni güncellendi:** Onay kartının açıklaması, yanlış işaretlenen eşleşmenin sonradan geri alınabileceğini açıkça belirtir (TR + EN).

### 5. Fandom Wiki Keşif ve Veri Çekme İyileştirmeleri (Canlı Denetimle)
- **Kimliksiz eklenti kayıtları için AniList köprüsü:** Yalnızca başlık gönderen eklenti kayıtları için başlık AniList'te birebir eşleştiğinde kimlikler ve başlangıç yılı otomatik tamamlanır (Naruto, One Piece, Bleach, Berserk, Solo Leveling, Chainsaw Man vb. sorunsuz çözülür).
- **Gerçek wiki boyutu eşiği düzeltildi:** Fandom topluluk aramasının kaba `pageCount` alt sınırı 10'a çekilerek niş/küçük animelerin gerçek wikilerinin elenmesi önlendi.
- **Dil tespiti `siteinfo` ile sağlandı:** Dil öneki olmayan yabancı wikilerin İngilizce sanılması önlendi; öncelik sırası `en` > `tr` > diğerleri olarak yapılandırıldı.
- **Tam giriş metni ve Bölüm 0:** Fandom'da desteklenmeyen `prop=extracts` yerine Bölüm 0 tam işlenmiş HTML olarak çekilir; giriş metni kesintisi giderildi ve bağlantılı infobox metinleri eksiksiz alındı.
- **Genişletilmiş sınırlar:** Paragraflar 8.000 karakter, bölümler 400 blok, tablolar 150 satır, görseller 24 adede çıkarıldı; önbellek anahtarları v4'e yükseltildi.

---

## 🇬🇧 English

### 1. Critical Fix: Wiki Reader "Open Fandom page" Unbounded Height Crash
- **Symptom:** Tapping **"Open Fandom page"** on the related-wiki-pages strip or opening an internal wiki link in the Wiki tab crashed the app the moment the reader content finished loading (`IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints`).
- **Root cause:** In portrait mode, detail tab content is measured by a `HorizontalPager` with **unbounded height** (`Constraints.Infinity`); in landscape/TV layouts, tab content already lives inside a `verticalScroll`. Rendering the reader inline gave its scrollable column an infinite height constraint (or created nested vertical scrolls), triggering Compose's fatal measurement exception.
- **Fix:** The in-app wiki reader now opens in its **own full-screen window (`Dialog`)**. Window constraints are guaranteed to be strictly bounded, permanently eliminating this entire class of crashes.
- **Preserved Navigation & Aesthetics:** The reader maintains its top bar, table of contents, lazy section loading, related pages strip, and back-stack navigation (`BackHandler` returns to previous page). Edge-to-edge system insets and MIUI/HyperOS force-dark prevention are fully integrated.
- **Duplicate render removed:** Eliminated redundant root-level reader composition on character detail pages so multiple stacked dialogs never occur.

### 2. Gallery "Details" Sheet Height Guard
- The image gallery's details sheet now enforces the same `heightIn(max = 85% of screen)` guard used across all other bottom sheets, preventing the identical vertically-scrollable unbounded crash.

### 3. Plasma Orb Pull-to-Refresh Animation
- **All pull-to-refresh indicators are now the plasma orb:** The default Material refresh indicator (spinning arrow in a dark circle) was removed across Profile, Explore, My List, media detail scaffold (`DetailPageScaffold`), character detail, staff detail, user profile, and user list screens.
- **Shared `KitsugiPullToRefreshIndicator` component:** Features the glowing plasma orb from the fullscreen loader (`KitsugiPlasmaLoader`). Smoothly scales with pull distance, pulses at full size while refreshing, and never draws when idle (zero idle overhead).

### 4. Wiki "No, wrong" Rejection Undo & Bulk Restore
- **Undo rejection card (`WikiRejectedUndoCard`):** If "No, wrong" is tapped by mistake, an **"Undo"** card appears at the top of the Wiki tab. Whether the next candidate is found or no wiki is found at all, tapping "Undo" immediately clears the rejection and re-resolves the match.
- **Bulk recovery in Settings:** **Settings → Fandom Wiki → "Restore rejected matches"** restores all mistakenly rejected wikis/pages while preserving user-confirmed matches.
- **Confirmation copy updated:** Clearly explains that rejected matches can be undone later (TR + EN).

### 5. Fandom Wiki Discovery & Data Extraction Fixes (Live-Audited)
- **AniList bridge for title-only plugin records:** Exact-matches catalog titles to AniList to hydrate catalog IDs and release years, enabling Wikidata and character verification.
- **Adjusted wiki article count threshold:** Fandom search `pageCount` lower bound reduced to 10 so smaller dedicated anime wikis are no longer filtered out.
- **Language detection via `siteinfo`:** Disallows non-English prefixless wikis from masquerading as English; strict ranking: `en` > `tr` > others.
- **Section 0 lead & infobox merge:** Fetches Section 0 processed HTML directly, eliminating truncated blurbs and capturing rich linked infobox values.
- **Expanded parsing limits:** Limits increased to 8,000 chars/paragraph, 400 blocks/section, 150 table rows, 24 images; cache bumped to v4.
