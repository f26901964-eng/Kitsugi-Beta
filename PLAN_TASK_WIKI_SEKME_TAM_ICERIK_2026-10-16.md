# PLAN/TASK — Wiki Sekmesi: İçeriğin Tamamını Sekme İçinde Gösterme

**Tarih:** 2026-10-16
**Sürüm:** 2.4.290 → **2.4.291**
**Durum:** Uygulandı. Android derlemesi bu ortamda Java/JDK bulunmadığından çalıştırılamadı; değişiklikler elle incelendi (parantez dengesi, imza uyumu, referans kontrolleri).

## Kullanıcı isteği (ekran görüntüleriyle)

Yapım detayının **Wiki** sekmesi:
1. İçerikle ilgili asıl ayrıntıyı (karakter / ekip / seslendirmen) göstermiyor; wiki sayfasında yalnızca
   **"Fandom sayfasını"** düğmesi görüyor.
2. *Kimi no Na wa. Wiki* gibi bazı wiki'lerde sekme bomboş bir "başlık + kapak" kartı gösteriyor; asıl
   içerik ancak okuyucuya tıklayınca görünüyordu.
3. "Oraya tıklamadan da içerikle ilgili verinin tamamını eksiksiz görebilmeliyim."

## Kök nedenler

1. **Boş madde:** `resolveMediaArticle` sayfayı buluyor ama bilgi kutusu alanları / özet / bölümler
   ayrıştırılamıyor (`FandomInfobox` yalnızca kapak görseli taşıyor). `CharacterWikiTabContent`
   bu hâliyle boş bir bilgi kutusu kartı çiziyordu.
2. **İlgili sayfalar yüzeysel:** v2.4.290'da tüm ilişkili kartlar 280×144 dp önizleme kartına
   (`WikiPagePreviewCard`) çevrilmişti; kart "Fandom sayfasını aç" eylemiyle okuyucuyu açıyor,
   ayrıntı göstermiyordu.

## Yapılanlar

### Veri katmanı (`data/fandom/`)
- `FandomModels.kt`: `FandomCharacterPage.hasWikiContent` — madde için gösterilebilecek gerçek
  içerik (bilgi kutusu alanı / özet / bölüm) olup olmadığı.
- `FandomRepository.kt`:
  - `enrichRelatedPagesWithLead(context, wikiId, pages, maxPages = 12)`: her ilgili sayfa için
    giriş bölümünü (section 0) ve eksiksiz bilgi kutusunu çeker, kartın profiline işler.
  - `mergeRelatedPageLead(...)`: giriş metnini öne geçirir (`FandomLeadParser.mergeDescription`),
    bilgi kutusunda HTML daha eksiksizse onu temel alıp eksik etiketleri JSON önizlemesinden
    tamamlar, görselleri birleştirir. Ağ hatasında sayfa önizleme verisiyle kalır.

### ViewModel
- `FandomMediaWikiViewModel.kt`:
  - `InlinePageState` (Idle / Loading / Ready / Unavailable) + `inlinePage`, `inlineSections` akışları.
  - `ensureInlinePage()`: madde içeriği boşken wiki'nin kendi sayfasını (Wikidata madde başlığı →
    wiki ana sayfası → wiki adı) giriş bölümüyle sekme içine yükler.
  - `loadInlineSection(index)` / `retryInlinePage()`; `KEY_INLINE_LEAD` çeviri anahtarı ve
    `translateInlinePageLead()` (otomatik çeviri uyumu).
  - `enrichRelatedPages(...)`: ilgili sayfaları 3'erli gruplar hâlinde zenginleştirir ve her
    gruptan sonra listeyi günceller.
  - `bind()` / `switchSource()` / `retryArticle()` içinde inline durum + zenginleştirme işi sıfırlanır.
- `FandomCharacterWikiViewModel.kt`: aynı `enrichRelatedPages(...)` akışı (karakter/ekip/seslendirmen
  sayfalarının ilgili sayfaları da tam ayrıntıyla).

### Arayüz
- `FandomInlineProfile.kt`: yeni paylaşılan `WikiRelatedPagesInlineSection(...)` — başlık + madde
  sayısı + her sayfa için `WikiRelatedPageDetailCard` (kapak, giriş metni, tüm bilgi kutusu alanları,
  spoiler koruması, ikincil "Fandom sayfasını aç" eylemi).
- `MediaWikiTabContent.kt`:
  - `RelatedPagesStrip` (yatay önizleme şeridi) kaldırıldı → `WikiRelatedPagesInlineSection`.
  - Madde boşken: boş bilgi kutusu kartı bastırılır (`suppressEmptyInfobox`), yerine
    `WikiInlineArticleCard` (kapak + açıklama + giriş metni + İçindekiler, bölümler açılırken
    yüklenir, çeviri düğmesi, hâlde okuyucuda aç) çizilir.
  - Madde eşleşmese bile aynı tam sayfa kartı gösterilir; o da yoksa eski "bulunamadı" kartı ve
    "wiki ana sayfasını aç" eylemi korunur.
- `CharacterWikiTabContent.kt`:
  - `CharacterRelatedStrip` kaldırıldı → `WikiRelatedPagesInlineSection`.
  - `WikiRelatedEntitiesCard` içindeki önizleme kartları → `WikiRelatedEntityDetailCard`
    (karakter / oyuncu kadrosu / seslendirenler / ekip / müzik grupları tam ayrıntıyla).
  - `suppressEmptyInfobox` parametresi.
- `FandomWikiReader.kt`: `ReaderRelatedCard` artık aynı tam ayrıntı kartlarını gösterir;
  `FandomReaderRenderState.spoilerGuard` eklendi (medya/karakter slotları ve karakter detay
  sayfası geçiriyor).
- `WikiPagePreviewCard.kt`: kullanılmadığı için kaldırıldı.

## Bilinen sınırlar / sonradan geliştirilebilecekler
- İlgili sayfaların ilk 12'si giriş bölümüyle zenginleştirilir; kalan sayfalar önizleme verisiyle
  (kısa açıklama + bilgi kutusu) gösterilir. Tam sayfanın TÜM bölümleri yine okuyucudadır.
- Tam sayfa kartındaki giriş metni düz metin çevirisiyle çevrilir; yapı koruyan blok çevirisi
  yalnızca okuyucudadır.

## Doğrulama
- `git diff --check`: başarılı.
- Değişen dosyalarda parantez/ayraç dengesi kontrol edildi (10 dosya, 30 kontrol).
- `bash gradlew :app:compileDebugKotlin`: çalıştırılamadı; ortamda `JAVA_HOME` ve `java` bulunmuyor.

## Kabul ölçütleri
1. Madde içeriği boş wiki'lerde sekme, wiki'nin tam sayfasını (kapak + açıklama + giriş +
   İçindekiler) tıklamadan gösterir.
2. "İlgili wiki sayfaları" ve karakter/ekip/seslendirmen maddeleri giriş metni + bilgi kutusu
   alanlarıyla kart içinde listelenir.
3. Sayfayı açma yalnızca ikincil eylemdir; hiçbir kart "göster" düğmesine dönüşmez.
4. Ağ hatasında kartlar önizleme verisiyle kalır, liste boşalmaz.
5. Madde doluysen davranış değişmez; kaynak değişiminde inline durum sıfırlanır.
