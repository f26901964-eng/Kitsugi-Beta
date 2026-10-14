# PLAN/TASK — Otomatik çeviri sonrası yapı ve bağlantı koruması

Tarih: 2026-10-14
Durum: **Kodlandı.** Saf mantık katmanı Python'a birebir port edilerek doğrulandı; Kotlin kaynakları
Tree-sitter sözdizimi denetiminden geçti. Ortamda Java/JDK olmadığı için Gradle derlemesi ve
Android/JUnit testleri çalıştırılamadı.

## Kullanıcı isteği

Otomatik çeviri açıldığında wiki sayfalarında:

1. Bağlantılar bozuluyor (URL'ler / sayfa bağlantıları düz yazıya dönüyor, yanlış hedefe gidiyor),
2. Sayfa düzeni değişiyor (madde işaretleri, kartlar, başlıklar kayboluyor; görseller metnin altına yığılıyor),
3. Bilgi kutusu etiket/değer eşleşmesi kayıyor (değerler birbirine karışıyor),

İstenen: "Otomatik çevirinin etki ettiği her yerde bu ve benzeri durumlar yaşanmasın" — mantıksal
önlemler ve koruma mekanizmaları.

## Kök nedenler (kodda doğrulandı)

| # | Kök neden | Sonuç |
|---|-----------|-------|
| R1 | Çeviri geldiğinde **yapılandırılmış bloklar atılıyordu**: `if (translated != null)` dalı `content.plainText()` benzeri tek bir düz metin çizip görselleri metnin ALTINA ekliyordu (5 ayrı çizim noktası). | Düzen bozulması, görsellerin yığılması, madde/kart/başlık kaybı, bağlantı çiplerinin kaybolması |
| R2 | Metin tüm bölüm için tek parça çevriliyordu; blok sınırları Google'a taşınmıyordu. | Satır/paragraf birleşmesi, sıra kayması |
| R3 | Bilgi kutusunda tüm değerler `"\n"` ile birleştirilip tek istekte çevriliyor, yanıt satır sayısına göre geri bölünüyordu. | Google satır birleştirdiğinde etiket-değer eşleşmesi kayıyordu |
| R4 | Bağlantı etiketleri çeviriye gönderiliyordu (etiket = wiki sayfa adı). | Etiket ile dokunma hedefi tutarsızlaşıyor, tıklanabilir alan kayboluyordu |
| R5 | Teknik değerler (`https://…/File:01_-_Yume_Tourou.ogg`, `File:`, dosya adları) korunmuyordu; maskeleme yalnızca URL ve markdown linklerini kapsıyordu. | URL bozulması, erişilemeyen bağlantı |
| R6 | Google bir korunan jetonu düşürdüğünde çeviri yine de kabul ediliyordu. | Metinden tamamen silinen URL / kopuk bağlantı |

## Çözüm — tek merkezden koruma katmanı

### 1) Yapı sözleşmesi: `WikiTranslationProtection.kt` (yeni)

- `WikiStructuredTranslationKeys`: bölüm çevirisi **blok çocuk anahtarlarına** yazılır
  (`"sec:0\u0000b3"`). Bölüm anahtarına yalnızca "tamam" işareti (`"1"`) konur.
  Anahtar ayırıcısı denetim karakteri olduğu için wiki başlıklarıyla çakışamaz.
- `translateSectionBlocks(...)`: bölümü **blok blok** çevirir.
  - Görseller hiç çevrilmez, yerlerinde kalır.
  - `LINK_GROUP` çipleri (sayfa adları) orijinal kalır; yalnızca grup başlığı çevrilir.
  - Kart başlığı bir sayfaya bağlıysa (`href` dolu) çevrilmez; yalnızca alt satır çevrilir.
  - Teknik değerler çeviriye hiç gönderilmez.
- `WikiLinkLabelMask`: bağlantı etiketleri jetonlanır (`⟪L0⟫`), çeviri sonrası **birebir** geri
  konur. Jeton kaybolur/çoğalırsa `restore` `null` döner ve **blok orijinal hâliyle kalır** —
  kopuk bağlantı hiçbir koşulda gösterilmez.
- `withTranslatedBlocks(...)`: çevrilmiş metinleri orijinal blok **sırasına, türüne** ve
  `image`/`href`/`links` alanlarına geri oturtur. Blok sayısı ve tür dizisi birebir aynıdır.
- `translateRowsPreservingStructure(...)`: çok satırlı çeviride satır sayısını `⟪§⟫` ayırıcı
  jetonuyla **kanıtlar**; kanıtlanamazsa satır satır yedeğe düşer. Dönen liste her zaman girdiyle
  aynı uzunluktadır — etiket/değer eşleşmesi asla kaymaz.
- `withoutTranslation(...)`: "Orijinal" geçişi ve hata temizliği, bölümün **tüm** blok çocuklarını
  birlikte siler (yarım çeviri ekranda kalmaz).
- `copyTextFor(...)`: kopyalanan metin de yapıyı korur (madde işaretleri, kart sırası).
- `isTechnicalWikiValue(...)`: karar mantığı çeviri katmanıyla ortaktır.

### 2) Çeviri katmanı: `TranslationManager` + `KitsugiTranslateUtils`

- `isTechnicalTranslationValue(...)` (ortak, utils): URL, `www.`, dosya adları (`*.ogg`, `*.png`…),
  `File:`/`Category:`/`Şablon:` ad alanı değerleri, `[[…]]` / `{{…}}` wikitext'i, e-posta,
  rakam/işaret ağırlıklı boşluksuz jetonlar. Metnin tamamı teknikse **hiç istek yapılmaz**.
- `maskMarkdown` artık `[[Sayfa|Etiket]]`, `{{şablon}}`, `File:`/`Image:`/`Category:` değerleri ve
  e-postaları da jetonlar.
- `MaskedMarkdown.isIntact(...)`: çeviri yanıtı korunan jetonlardan birini kaybettiyse o **parça
  reddedilir** ve orijinal metniyle gösterilir (`fullyTranslated = false` → tam çeviri olarak
  önbelleğe de yazılmaz).

### 3) Görünüm katmanı: çevrilmiş içerik artık AYNI çiziciden geçer

| Dosya | Değişiklik |
|-------|------------|
| `FandomWikiReader.kt` | `ReaderSectionContent` yapısal çeviriyle beslenir; `translations` parametresi eklendi. Çevrilmiş ve orijinal görünüm **aynı** `ReaderBlock` dizisini kullanır. |
| `MediaWikiTabContent.kt` | `ArticleSectionBody` artık blokları `ArticleBlock` ile çizer (ortak çizici); eski düz metin dalı yalnızca yapı bilgisi olmayan eski önbellek kayıtları için yedek. |
| `CharacterWikiTabContent.kt` | `SectionBody` + `WikiSectionRow` yapısal çeviriye bağlandı; kopyala düğmesi `copyTextFor` kullanır. |
| `FandomMediaWikiViewModel.kt` | `sectionStructure()` + `translateSectionStructured()`; `sec:`, `item-sec:`, `reader-section:` anahtarları blok blok çevrilir; `showOriginal` çocukları da siler; iki bilgi kutusu yolu satır korumalı çeviriye geçti. |
| `FandomCharacterWikiViewModel.kt` | Aynı yapısal akış (`sec:`, `reader-section:`); `clearTranslation` çocukları da siler; bilgi kutusu satır korumalı çeviriye geçti. |

Akış (her bölüm için): ilk blok tamamlanır → blok anahtarına yazılır + bölüm anahtarına işaret
konur (düğme anında "çevrildi" durumuna geçer) → kalan bloklar aktıkça yansır → hiçbir blok
çevrilemezse çeviri tamamen geri alınır (metin zaten hedef dildeyse gereksiz "çevrildi" görünümü
oluşmaz).

## Eklenen testler

`app/src/test/java/com/kitsugi/animelist/ui/screens/detail/wiki/WikiTranslationProtectionTest.kt`

- Blok sırası/türü/görsel konumu ve `href`/`links` alanları korunur; kart başlığı bağlıysa çevrilmez.
- Bağlantı etiketi jetonu kaybolursa blok orijinal kalır (`restore` → `null`).
- Teknik değerler çeviriciye hiç gitmez; satır sayısı her durumda korunur.
- Ayırıcı jetonu korunursa tek istek, kaybolursa satır satır yedek; çevirici hata verirse orijinal.
- Kopyalama metni madde işaretini korur, görselleri atlar, "tamam" işaretini panoya taşımaz.
- `withoutTranslation` bölüm + blok çocuklarını siler.

## Kontroller ve sınırlar

- Saf mantık katmanı Python'a birebir port edilerek doğrulandı: teknik değer sınıflaması
  (13 pozitif/negatif vaka), etiket maskesi (geri kurma / düşen jeton / çoğalan jeton), satır
  koruması (tek istek, yedek yol, teknik satır filtresi) — tümü beklenen sonucu verdi.
- Değişen Kotlin dosyaları Tree-sitter Kotlin ayrıştırıcısından hatasız geçti.
- Gradle derlemesi ve JUnit testleri çalıştırılamadı (ortamda Java/JDK yok); cihaz doğrulaması
  ayrıca gereklidir.
- Çeviri çıktısı kalitesi (Google'ın ürettiği metin) bu koruma katmanının kapsamı dışındadır;
  katman yalnızca **yapı, bağlantı ve değer eşleşmesinin** bozulmamasını garanti eder.

## Manuel doğrulama planı

1. Bir yapım wiki'sinde bölüm aç; çeviri açıkken madde işaretleri, kartlar, başlıklar ve görsel
   sırasının orijinalle **birebir aynı** olduğunu doğrula (görseller artık metnin altına yığılmaz).
2. Bilgi kutusunda "Track · https://…/File:01_-_Yume_Tourou.ogg" satırının çeviriden sonra
   değişmediğini ve diğer alanların etiketleriyle eşleştiğini kontrol et.
3. Bölüm içindeki tıklanabilir bağlantılara dokun; doğru sayfaya gittiğini doğrula (hem orijinal
   hem çevrilmiş görünümde).
4. "Orijinal" düğmesine bas: bölümün tek hamlede orijinal yapısına döndüğünü doğrula.
5. Kopyala düğmesi: madde işaretlerinin korunduğunu, "1" gibi bir işaretin kopyalanmadığını gör.
6. Uygulama içi okuyucuda içindekilerden bölüm aç; aynı kontrolleri okuyucuda da tekrarla.
7. Ağ kesintisi senaryosu: çeviri başarısız olduğunda bölümün orijinal hâliyle kaldığını doğrula.
