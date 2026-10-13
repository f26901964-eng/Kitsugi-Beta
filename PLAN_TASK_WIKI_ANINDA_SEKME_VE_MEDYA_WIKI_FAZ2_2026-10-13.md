# PLAN/TASK — Wiki sekmesi: anında görünürlük + yapım (anime/manga/dizi/film) Wiki sekmesi (Faz 2)

Tarih: 2026-10-13
Durum: **Kodlandı.** Derleme + cihaz testi kullanıcıda (sandbox'da JDK/Android SDK yok).
Önceki adım: `PLAN_TASK_FANDOM_WIKI_ENTEGRASYONU_2026-10-12.md` (Faz 1).

## Kullanıcı geri bildirimi (özet)

1. Karakter sayfasında **Wiki sekmesi çok geç beliriyor** (çözümleme bitince ekleniyordu);
   sekme açılıştan itibaren sabit durmalı, içerik yerinde/yavaş yavaş yüklenmeli.
2. İçindekiler listesinde **Türkçe olmayan başlıklar** kalıyordu:
   "Role in the Movie", "Appearances in Other Media" …
3. Wiki **sadece karakter sayfasında** olmamalı; dizi/film/anime/manga detayında da olmalı.
4. Mümkün olan diğer yerlere de entegre edilsin; eksik bölüm/alan varsa eklensin.

## Yapılanlar

### 1. Sekme artık sayfa açılır açılmaz var (karakter + yapım)

- `CharacterDetailPage.kt`: sekme görünürlüğü `wikiPage != null` yerine
  `wikiTabPossible = prefs.enabled && FandomCharacterQueries.from(...) != null`.
  Çözümleme sürerken sekmenin içinde **iskelet** (`WikiTabSkeleton`), bulunamazsa
  **boş durum + "Tekrar dene"** (`WikiUnavailableCard`) gösterilir. Sekme sonradan
  belirip/kaybolmadığı için sekme şeridi ve sayfa düzeni hiç kaymaz.
- Bölüm metinleri eskisi gibi **kullanıcı açtıkça** çekilir (sırayla, tek tek önbelleklenir);
  genel bilgi (bilgi kutusu + özet + içindekiler) tek çözümlemede geldiği için ilk çizim hızlıdır.
- `FandomCharacterWikiViewModel.retry()` eklendi ("Tekrar dene").

### 2. Başlık çevirileri genişletildi

- Yeni saf eşleyici: `ui/.../wiki/FandomSectionTitles.kt` (eski `when` bloğunun yerine).
  Eklenen kalıplar: `Role in the Movie/Film`, `Role in the Series/Anime/Manga`,
  `Appearances in Other Media / In Other Media / Other Media`, `Appearances`,
  `Legacy`, `Creation/Conception/Design/Development`, `Name origin / Meaning of the name`,
  `About/Profile/Summary`, `Techniques`, `Early life` …
- Bölüm **içindeki** alt başlıklar (HEADING blokları) da aynı eşleyiciden geçiyor.
- Birim test: `FandomSectionTitlesTest` (ekran görüntüsündeki iki başlık dahil).

### 3. Yapım detayında "Wiki" sekmesi (Faz 2)

- Yeni VM: `wiki/FandomMediaWikiViewModel.kt`
  - `bind(FandomMediaQuery?)`: `FandomRepository.resolveMediaWiki` (kimlik önce, isim son çare).
  - Sekme görünür olunca `ensureContentLoaded()`: **seri özeti** (wiki ana sayfası
    `fandomdescription`) ve **evren rehberi** ayrı ayrı, aşamalı çekilir.
  - Wiki içi **arama** (350 ms debounce, 5 dk bellek önbelleği).
  - Çip/sonuç kartı: `loadPageBlurb` → alttan sheet (KitsugiSheetOrDialog, TV'de dialog),
    Türkçe çeviri düğmesi + "Tamamını oku" (tarayıcı).
  - "Yanlışsa bildir" → `FandomRepository.rejectMediaWiki` (wiki reddi + önbellek düşürme).
- Yeni UI: `wiki/MediaWikiTabContent.kt`
  - Başlık kartı (wiki adı + "Wiki'de aç"), seri özeti kartı (çevrilebilir),
    arama kartı, evren rehberi çipleri (FlowRow), kaynak/lisans + bildirim satırı.
  - Çip isimleri **orijinal** kalır ("Limitless" → "Sınırsızlık" yapılmaz); açıklamalar çevrilir.
- Veri katmanı:
  - `FandomApi.categoryInfo / categoryMembers / searchHits` + ayrıştırıcılar
    (kategori var/yok kararı **tek** `categoryinfo` isteğiyle; grup başına 1 `categorymembers`).
  - `FandomGuideCatalog.kt`: grup → kategori adayları (TR wiki için Türkçe adaylar önce).
  - `FandomRepository.loadWorldGuide / loadPageBlurb / loadMainPageBlurb / searchWiki /
    rejectMediaWiki`; rehber ve açıklamalar `persistent_details_cache`'te 7 gün.
  - `FandomMediaQueries.fromMedia(...)`: kaynak kimlikleri (offset'li) + gerçek MAL/TMDB
    birleşimi; başlıklar isim yolunu besler (saf, testli).
- Sayfalar: `MediaEntryDetailPage.kt` ve `ApiResultDetailPage.kt`
  - Sekme **en sonda**: `… Bölümler · Wiki`. `wikiPageIndex` hesabıyla
    `loadTab` wiki sayfasında çağrılmaz (gereksiz veri yükü yok).
  - Sekme prefs kapalıyken hiç eklenmez; açıkken ilk kompozisyondan itibaren vardır.

### 4. Dizgi

- `values/strings.xml` + `values-en/strings.xml`: 24 yeni anahtar
  (iskelet, boş durum, yeni bölüm başlıkları, rehber etiketleri, arama metinleri …).
- Ayarlar açıklaması güncellendi: "Karakter **ve yapım** detay sayfalarında…".

## Test rehberi (cihazda)

1. Karakter detayı (ör. Miyamizu Mitsuha): sekme açılışta görünür → önce iskelet,
   sonra içerik; içindekilerde "Filmdeki rolü", "Diğer medyalarda görünümler" Türkçe.
2. Anime detayı (ör. Jujutsu Kaisen / Kimi no Na wa.): sonda **Wiki** sekmesi;
   seri özeti + arama + Organizasyonlar/Yetenekler/Mekânlar/Terimler çipleri;
   çipe dokun → alttan kart → "Tamamını oku".
3. Ayarlar → Fandom Wiki kapat → sekme her iki sayfada da yok; açınca geri gelir.
4. "Yanlışsa bildir" → wiki reddedilir, sekme boş duruma düşer, "Tekrar dene" çalışır.
5. Uçak modu: sekme iskeletten boş duruma düşer; "Tekrar dene" bağlantı gelince doldurur.
6. Birim testler: `FandomSectionTitlesTest`, `FandomGuideCatalogTest`,
   `FandomMediaQueriesTest`, `FandomApiParsersGuideTest`.

## Sonraki adımlar (Faz 2b / 3)

- TV anime detayı (`TvDetailScreen.kt`): satır bazlı düzen için **Wiki satırı**
  (kumandayla gezilen çipler) — bu turda telefon sayfalarına öncelik verildiği için bırakıldı.
- Bilgi sekmesine "Bu yapımın wiki'si var →" kısayol kartı (sekme artık hep görünür
  olduğu için önceliği düştü).
- Faz 3: Bölümler sekmesinde "Önceki bölümde…", oynatıcıda X-Ray, mangaya devam kartı.
