# Wiki İçi Okuyucu + İlgili Wiki Sayfaları + Devamını Göster Toplu Yükleme

Tarih: 2026-10-13

## Kullanıcının istediği

1. **"İlgili Wiki sayfaları"** bölümü hem karakter hem yapım wiki sekmesinde görünsün (ekran 1 & 2'de
   turuncu kutularla işaretli). Kartlarda küçük görsel/yer tutucu, başlık, kısa açıklama ve
   **"Fandom sayfasını"** butonu olmalı. Tema/biçim/renkler wiki sekmeleriyle birebir aynı olmalı.
2. "Fandom sayfasını" ve metin içindeki mavi bağlantılar (ör. "Walls", "Mikasa Ackermann",
   "Colossal Titan", "Feuerroter Pfeil und Bogen" — ekran 3 & 4) **tarayıcı sekmesi açmasın**,
   uygulama İÇİNDE, mevcut temayla uyumlu bir okuyucu olarak açılsın. Okuyucuda İçindekiler,
   bölüm gövdesi ve ilgili sayfalar olsun; geri tuşuyla önceki wiki sayfasına dönülebilsin.
3. **Evren rehberi "Devamını göster (+6)"** butonu (ekran 5) tek dokunuşta o kategorideki
   **TÜM** kalan sayfaları sırayla çeksin; kullanıcı her sayfa için tekrar basmak zorunda kalmasın.

## Yapılan değişiklikler

### Veri katmanı (`data/fandom/`)
- `FandomModels.kt`
  - `FandomRelatedPage` (kapak + kısa açıklama) ve `FandomFullPage` (okuyucu meta verisi) eklendi.
  - `FandomSectionContent` artık bölüm HTML'inden çıkarılmış **viki içi bağlantı** listesini de
    tutuyor (`links`).
- `FandomApi.kt` + `FandomApiParsers.kt`
  - `pagePreviews(wikiId, titles, thumbWidth)` → `prop=pageimages|pageprops` ile toplu önizleme
    (kapak + fandomdescription); ayrım (disambiguation) sayfaları ve eksik sayfalar elenir.
  - `pageMeta(wikiId, title)` → okuyucu için başlık/kapak/açıklama + bölümler bir arada.
  - `parseRelatedPages` ekledi.
- `FandomRepository.kt`
  - `loadAllRemainingGuideItems(...)`: "Devamını göster" için kategorideki tüm kalan üyeleri
    döngüyle çeker, tek bir birleşik liste döndürür.
  - `loadFullPageMeta`, `loadRelatedPages` — kısa ömürlü bellek önbelleğiyle.
  - `loadSection` artık `FandomHtmlText.internalWikiLinks(html)` ile bağlantıları da çıkarıyor.
- `FandomHtmlText.kt`
  - `internalWikiLinks(html)` → `/wiki/Başlık` biçimli viki içi bağlantıları ayıklar, özel ad
    alanlarını (Category:/Dosya:/Şablon:/…) atlar, tekrarları kaldırır.

### ViewModel
- `FandomMediaWikiViewModel.kt`
  - `ReaderState` (Closed/Loading/Ready/Failed) + `reader`, `relatedPages` akışları.
  - `openReader(title)`, `openReaderMainPage()`, `loadReaderSection(index)`,
    `openReaderLink(title)`, `readerBack()`, `closeReader()` — geri yığını (`ArrayDeque`).
  - `loadMoreGuide(group)` artık tek seferde **tüm** kalan kategori sayfalarını çekiyor.
  - Ana sayfa yüklendiğinde ilk 1–2 bölümden bağlantı toplayıp `relatedPages` (kapaklı kartlar)
    dolduruluyor.
- `FandomCharacterWikiViewModel.kt`
  - Aynı reader akışı ve `relatedPages` eklendi; karakter sayfası hazır olunca bağlantılar
    toplanıyor.

### UI
- Yeni dosya **`FandomWikiReader.kt`**
  - `FandomWikiReaderSheet(...)` tam ekran, tema uyumlu uygulama içi okuyucu (Surface).
    - Üst bar: geri (yığın varsa) / kapat, başlık, tarayıcıda aç.
    - Kapak + başlık + özet kartı.
    - İçindekiler (tıklayınca bölümü yükler; yüklendi/açık göstergesi).
    - Yüklenen bölümler gövde + spoiler kuralları + bölüm sonu viki çipleri.
    - "İlgili wiki sayfaları" yatay kaydırılabilir kart şeridi.
  - `WikiRelatedPageCard(...)` — kartlar sayfa içinde ve dışında yeniden kullanılıyor
    (kapak/yer tutucu, başlık, kısa açıklama, "Fandom sayfasını" butonu).
- `MediaWikiTabContent.kt`
  - Header "Wiki'de aç" butonu artık okuyucuyu açıyor (eski davranış tarayıcıydı).
  - Chip/arama sonucu/özet kartı dokunuşları `openReader(title)` çağırıyor.
  - Evren rehberi sonrası `RelatedPagesStrip(...)` eklendi.
  - Alt kısımda `FandomWikiReaderSheet` render ediliyor; BackHandler ile geri yığınına düşüyor.
- `CharacterWikiTabContent.kt`
  - "Fandom sayfasını" footer butonu okuyucuyu açıyor.
  - `CharacterRelatedStrip(...)` ile ilgili wiki sayfaları kart şeridi eklendi.
  - `onOpenWikiPage`, `relatedPages` parametreleri eklendi.
- `CharacterWikiTabSlot.kt`
  - Reader state toplandı, `FandomWikiReaderSheet` render ediliyor, BackHandler bağlandı.
  - Tarayıcı açma hâli okuyucunun üst-bar "dünya" ikonunda korunuyor (fallback).

### Kaynak dizeleri
- Türkçe ve İngilizce `strings.xml` dosyalarına yeni anahtarlar eklendi:
  `fandom_related_pages_title`, `fandom_related_pages_count`, `fandom_open_page`,
  `fandom_reader_back/close/contents/open_browser/failed/empty`, `fandom_related_empty`.

## Bilinen sınırlar / sonradan geliştirilebilecekler
- İlgili wiki sayfaları şu an **ana sayfa + ilk 1–2 bölüm** bağlantılarından toplanıyor;
  ileride kategori üyeliklerini kullanmak (mevcut ekran 1-2'deki grup başlıkları gibi
  "Ekip", "Seslendirenler" gibi) için `categories` API'si eklenebilir.
- Okuyucu şu an spoiler korumasına uyar ama mevcut sekmedeki spoiler ayarıyla aynı kapıdan
  geçiyor; ayrı bir ayar eklenmedi.
- "Fandom sayfasını" kart butonunda şimdilik küçük resim + 2 satır açıklama var; görsel
  yüklenemezse başlık harfiyle mor daire yer tutucu gösteriliyor (ekranlardaki gibi).

## Test edilecek başlıca akışlar
1. Bir yapımın Wiki sekmesi aç → "Fandom sayfasını" ana butonuna bas → uygulama içi okuyucu
   açılmalı, tarayıcıya ATLMAMALI. İçindekiler'den bir bölüme tık → bölüm yüklenmeli.
2. Evren rehberi, örn. Organizasyonlar'da "Devamını göster (+6)" → tek basışta grubun
   tamamı gelmeli; düğme kategori bittiyse kaybolmalı.
3. Karakter Wiki sekmesi → sayfa sonunda "İlgili wiki sayfaları" kartları görünmeli, karta
   tıklayınca okuyucu açılmalı; geri tuşu ana karakter sayfasına dönmeli.
4. Bir karttaki "Fandom sayfasını" butonu da aynı okuyucuyu açmalı.
