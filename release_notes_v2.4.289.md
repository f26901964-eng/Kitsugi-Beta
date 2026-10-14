# Kitsugi Beta v2.4.289

## 🇹🇷 Türkçe

### Vikipedi (Wikipedia) Entegrasyonu Baştan Sona Düzeltildi — Yanlış Maddeler, Ana Sayfa Açılması ve Çeviri/Orijinal Tutarsızlığı

Bu sürüm, yapım detay sayfasındaki **Wiki** sekmesinin Vikipedi ayağında kullanıcıya yansıyan
tüm hataları kök nedenden çözer.

#### 1) "İlgili wiki sayfaları" şeridi yapımla ilgisiz maddeler gösteriyordu
- **Sorun ve Kök Neden:** İlgili sayfalar, kaynak wiki'nin **ANA SAYFASININ** 0/1 numaralı
  bölümlerindeki bağlantılardan toplanıyordu. Fandom'da ana sayfa bir "hub" olduğu için bu
  yaklaşım çalışıyordu; Vikipedi'de ise ana sayfa `Anasayfa`'dır ve bağlantıları günün seçkin
  maddeleridir. Sonuç: Attack on Titan için "Tarım, Hukuk, Ahlat, Türkçe" gibi 20 alakasız madde.
- **Çözüm:** Vikipedi kaynağında ilgili maddeler artık **yapımın kendi maddesinden** türetilir:
  maddenin girişi + ilk bölümünün wiki içi bağlantıları (normalize başlık tekilleştirme ile).
- **Ansiklopedi iskelet filtresi:** `Anasayfa`, `Portal`, `Yardım`, `Vikipedi:`, `Şablon:`,
  `Dosya:`, `Kategori:`, `Özel:`, `Taslak` gibi ansiklopedi altyapı sayfaları listeye alınmaz.
  Ayrıca maddenin kendisi ve 20 madde sınırı korunur.
- Vikipedi'de artık ana sayfa tabanlı ilgili sayfa akışı hiç çalışmaz (kart "İlgili maddeler"
  başlığıyla çizilir, kart altı etiket "Vikipedi sayfası" olur).

#### 2) "Vikipedi'de aç" düğmesi ve okuyucu, madde yerine ansiklopedinin ana sayfasını açıyordu
- **Sorun ve Kök Neden:** `openReaderMainPage()`, `siteinfo` içindeki `mainpage` alanını (TR için
  `Anasayfa`) açıyordu; bu Vikipedi için anlamsızdır ve kullanıcı aradığı maddeyi bulamıyordu.
- **Çözüm:** Vikipedi kaynağında üst başlıktaki düğme **çözülen maddeyi** açar (madde başlığı
  madde kartından, yoksa çözümleme sonucundan alınır). Ana sayfa yalnızca hiçbir madde
  bulunamadığında gösterilen "Wiki ana sayfasını aç" düğmesinde kalır.
- Üst kaynak kartı Vikipedi'de hangi **maddenin** açıldığını göstermek için madde başlığını da
  yazar; "Wikipedia · Açık Ansiklopedi" yerine dil sürümünün gerçek adı kullanılır
  (TR: "Vikipedi · Açık Ansiklopedi").

#### 3) "Wiki özeti" kartında Orijinal ↔ Çeviri geçişi içeriği tamamen değiştiriyordu
Üç ayrı kök neden vardı ve üçü de giderildi:
- **Çeviriler metne bağlı değildi:** Uygulama içi çeviriler yalnızca anahtarla (`desc`, `sec:1`…)
  saklanıyordu. Kaynak (Fandom ↔ Vikipedi) veya madde (Vikipedi TR ↔ EN) değiştiğinde aynı
  anahtar başka bir metni işaret ettiği hâlde **eski çeviri** ekranda kalıyordu; "Çeviri" düğmesi
  tamamen farklı bir metin gösteriyordu.
  → Her çeviri artık üretildiği **kaynak metne** bağlanır (`translationBases`); kaynak metin
  değişmişse çeviri, çeviri işi ve Orijinal/Çeviri durumu otomatik düşürülür
  (`pruneStaleTranslations`: madde, seri özeti, bölüm, madde içi sayfa, okuyucu bölümü).
- **Kaynak değişiminde türev durum sıfırlanmıyordu:** `switchSource` artık çevirileri, orijinal
  anahtarları, madde bölümlerini (içindekiler), arama durumunu, madde içi sayfayı ve okuyucu
  ilgili listesini tamamen temizler ve madde nesli sayacını (`articleGeneration`) artırır.
  Böylece yeni kaynak, önceki kaynağın içeriğiyle karışamaz.
- **Vikipedi'de dil sürümü sessizce değiştiriliyordu:** "Türkçe wiki önceliği" açıkken İngilizce
  Vikipedi maddesi, kullanıcıya haber verilmeden TR sürümüne çevriliyordu; "Orijinal" Türkçe
  metni, "Çeviri" ise eski İngilizce metnin çevirisini gösteriyordu.
  → Vikipedi'de bu sessiz geçiş kaldırıldı: dil sürümleri artık **ayrı kaynak çipleri** olarak
  sunulur (Vikipedi TR / Wikipedia EN) ve kullanıcı hangi dili okuduğunu bilir.
- **Zaten hedef dilde olan kaynakta çeviri başlatılmaz:** Vikipedi TR maddesi + Türkçe hedef gibi
  durumlarda uygulama içi çeviri hiç çalışmaz (metin değişmediği için "iki farklı içerik"
  yanılsaması oluşmaz).

#### 4) Kaynak listesi: Vikipedi TR ↔ Wikipedia EN geçişi
- Vikipedi birincil kaynak olduğunda artık **kardeş dil sürümü** de kaynak çipinde listelenir
  (öncelik kullanıcı tercihine göre TR→EN veya EN→TR). TR/EN sitelink'i olmayan yapımlarda ilk
  geçerli dil deterministik sırayla gösterilir (`Ad (DİL)` biçiminde).
- Kaynak çipleri artık **yatay kaydırılabilir**: üç kaynak (Fandom + Vikipedi TR + Wikipedia) dar
  ekranlarda taşmaz. Birincil kaynak Vikipedi olduğunda isim araması ikinci kez çalıştırılmaz
  (gereksiz ağ trafiği önlenir).

#### 5) Yanlış madde riskine karşı kimlik denetimi
- İsimle arama yolunda (`WikipediaResolver`) maddenin Wikidata kimliği (`pageprops.wikibase_item`)
  okunur; madde, yapımın (ve üst kayıtlarının) bilinen kimliklerinden birine ait değilse eşleşme
  "kesin" sayılmaz ve kullanıcıya onay kartı gösterilir. Kanıt yoksa (ağ hatası/kimliksiz madde)
  mevcut puan tabanlı davranış korunur — yanlış "bulunamadı" üretilmez.

#### 6) Vikipedi'de anlamsız kartlar kapatıldı
- **Evren rehberi (kategori çipleri)** Vikipedi kaynağında hiç gösterilmez
  (`GuideState.Unsupported`): `Category:Karakterler`, `Category:Kuruluşlar` gibi genel kategoriler
  yapım dışı çipler üretirdi.
- **"Seri özeti"** kartı Vikipedi'de gösterilmez; Vikipedi ana sayfasının özeti yapımın özeti
  değildir (yapım özeti zaten "Wiki özeti" kartındadır).

#### 7) Kaynak/lisans etiketleri ve küçük iyileştirmeler
- Alt bilgiler artık kaynağa göre doğru: `Kaynak: Vikipedi` (Fandom eki yok) ve
  **CC BY-SA 4.0** lisans notu; madde içi sayfa (öğe) alt bilgisi de aynı şekilde düzeltildi.
- Galeri penceresindeki kaynak adı wiki ailesine göre gösterilir ("Fandom" / "Vikipedi" /
  "Wikipedia") — Vikipedi görsellerinde artık "Fandom" yazmaz.
- Vikipedi maddelerinde `prop=images` (madde başına onlarca ikon/logo dosyası) isteği atlanır;
  bu liste arayüzde kullanılmıyordu. Performans ve veri tasarrufu.
- Okuyucuda Vikipedi sayfası açıldığında kısa açıklama satırı artık maddenin giriş metninden
  (`extracts`) doldurulur; Fandom'da kullanılan alan Vikipedi'de bulunmadığı için boş kalıyordu.
- Madde önbellek sürümü `v6`'ya yükseltildi (dil sürümü davranışı değiştiği için eski kayıtlar
  yanlış dilde madde döndürebilirdi).
- `FandomUrlsTest` içine kaynak adı eşlemesini doğrulayan yeni birim testi eklendi.

---

## 🇬🇧 English

### Wikipedia Integration Fixed End to End — Wrong Articles, Main Page Opening, and Original/Translation Inconsistency

This release fixes every user-visible defect in the **Wiki** tab's Wikipedia path at the root cause.

#### 1) The "Related wiki pages" strip showed articles unrelated to the title
- **Issue & Root Cause:** Related pages were collected from sections 0/1 of the source wiki's
  **MAIN PAGE**. That works for Fandom (main pages are hubs) but on Wikipedia the main page is
  `Anasayfa`/`Main Page`, whose links are the day's featured articles. Result: 20 irrelevant
  articles ("Tarım, Hukuk, Ahlat, Türkçe") for Attack on Titan.
- **Fix:** For Wikipedia sources, related pages are now derived from the **resolved article**
  itself (lead + first section wiki links, deduplicated by normalized title).
- **Encyclopedia boilerplate filter:** `Main Page`, `Portal`, `Help`, `Wikipedia:`, `Template:`,
  `File:`, `Category:`, `Special:`, `Stub` style pages are excluded, as is the article itself,
  with a 20-item cap.
- The main-page based related flow no longer runs for Wikipedia at all (the card is titled
  "Related articles" and its action label reads "Wikipedia page").

#### 2) The "Read on Wikipedia" button and the reader opened the encyclopedia main page
- **Issue & Root Cause:** `openReaderMainPage()` opened `siteinfo`'s `mainpage` field (`Anasayfa`
  for tr.wikipedia), which is meaningless on Wikipedia and hid the article the user wanted.
- **Fix:** For Wikipedia sources the header button now opens the **resolved article** (title taken
  from the article card, falling back to the resolution result). The main page remains only as the
  explicit "Open wiki main page" fallback when no article could be matched.
- The source card also shows the article title so users can confirm what they are reading, and the
  subtitle uses the language edition's real name (TR: "Vikipedi · Açık Ansiklopedi").

#### 3) Toggling Original ↔ Translation in the "Wiki summary" card swapped the whole text
Three distinct root causes, all fixed:
- **Translations were not bound to their source text:** In-app translations were stored by key
  only (`desc`, `sec:1`…). When the source (Fandom ↔ Wikipedia) or article (Wikipedia TR ↔ EN)
  changed, the old translation stayed on screen although the key pointed at different text.
  → Every translation is now bound to the **source text it was produced from** (`translationBases`);
  when the text changes, the translation, its job and the Original/Translation state are dropped
  (`pruneStaleTranslations` covering article, series summary, sections, item pages, reader sections).
- **Derived state was not cleared on source switch:** `switchSource` now clears translations,
  original keys, article sections (table of contents), search state, selected item and reader
  related list, and bumps the article generation counter so the new source cannot be mixed with
  the previous one's content.
- **Wikipedia silently switched language editions:** with "Turkish wiki priority" on, an English
  Wikipedia article was silently replaced by its Turkish edition; "Original" showed Turkish text
  while "Translation" showed a translation of the old English text.
  → That silent switch is removed for Wikipedia: language editions are surfaced as **separate
  source chips** (Vikipedi TR / Wikipedia EN) so users know which language they read.
- **No translation is started when the source is already in the target language** (e.g. a Turkish
  Wikipedia article with a Turkish target), removing the "two different contents" illusion.

#### 4) Source list: Vikipedia TR ↔ Wikipedia EN switching
- When Wikipedia is the primary source, its **sibling language edition** is now listed as a source
  chip (TR→EN or EN→TR depending on the user preference). Titles without TR/EN sitelinks fall back
  to the first available language in deterministic order (rendered as `Name (LANG)`).
- The source chip row is now **horizontally scrollable**, so three sources (Fandom + Vikipedi TR +
  Wikipedia) do not overflow on narrow screens. When Wikipedia is already the primary source, the
  name search is not repeated (avoiding redundant network traffic).

#### 5) Identity verification against wrong articles
- On the name-search path (`WikipediaResolver`) the article's Wikidata identity
  (`pageprops.wikibase_item`) is read; when the article does not belong to any known identity of
  the title (or its parent records), the match is no longer treated as "certain" and a confirmation
  card is shown. Without evidence (network error / identity-less article) the existing score-based
  behaviour is preserved so no false "not found" appears.

#### 6) Meaningless cards disabled on Wikipedia
- The **universe guide (category chips)** is never shown for Wikipedia sources
  (`GuideState.Unsupported`): generic categories such as `Category:Characters` would produce
  unrelated chips.
- The **"Series summary"** card is not shown on Wikipedia; the encyclopedia main page blurb is not
  the title's summary (the title summary already lives in the "Wiki summary" card).

#### 7) Source/license labels and small improvements
- Footers are now source-correct: `Source: Wikipedia` (no Fandom suffix) and the **CC BY-SA 4.0**
  license note; the in-tab item sheet footer was fixed the same way.
- The gallery dialog's source name follows the wiki family ("Fandom" / "Vikipedi" / "Wikipedia") —
  Wikipedia images no longer say "Fandom".
- The `prop=images` request (dozens of icon/logo files per article) is skipped for Wikipedia
  articles; the list was unused by the UI. Performance and data saving.
- When the reader opens a Wikipedia page, its short description line is now filled from the
  article lead (`extracts`); the field used for Fandom does not exist on Wikipedia and used to
  stay empty.
- Article cache key bumped to `v6` (the language-edition behaviour changed, so old entries could
  return an article in the wrong language).
- Added a unit test to `FandomUrlsTest` covering the gallery source mapping.
