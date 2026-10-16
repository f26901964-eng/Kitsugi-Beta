# Wiki Açılır Sayfaları — Eksiksiz Otomatik Çeviri + Konum Koruması Raporu
**Tarih:** 2026-10-16 · **Dal:** `arena/ef4bb12a-kitsugi-beta` · **Bağlı plan:**
`PLAN_TASK_WIKI_OKUYUCU_TAM_CEVIRI_VE_KONUM_2026-10-16.md`

## İstenen davranış

Otomatik çeviri (Google Translate) açıkken uygulama içi **wiki okuyucusu** ("açılır wiki
sayfaları") bütün olarak seçili dile çevrilmeli; **mavi tıklanır bağlantılar** (sayfa adları,
kart başlıkları, bağlantı çipleri, satır içi bağlantı etiketleri) çevrilmemeli ve
**konumlar aynen korunmalı**. Sayfa çeviriye gidip gelirken (Orijinal ↔ Çeviri) okuma
konumu kaymamalı.

## Bulunan gerçek hatalar ve çözümleri

### 1) Giriş bölümü hiç çevrilmiyordu (en kritik hata)
`FandomWikiReader.kt` → `ReaderLeadCard`, `ReaderSectionContent`'e `translations` haritasını
GEÇMİYORDU (varsayılan `emptyMap()`). Bölümler blok blok çevrildiği için sonuç bölüm
anahtarında değil **çocuk blok anahtarlarında** durur:

- yapı korumalı çeviri: `withTranslatedBlocks(...)` → `translations` boş olduğu için `null`,
- düz metin yedeği: bölüm anahtarındaki değer `COMPLETE` (`"1"`) işareti olduğu için
  ayıklanıyor → **orijinal metin** çiziliyordu.

Yani okuyucuda (maddenin en büyük bölümü olan) giriş kısmı çevrilmiyor, buna rağmen
"Orijinal" düğmesi görünüyordu; düğmeye basmak metni değiştirmiyordu.
**Çözüm:** `translations = state.translations` geçilir; giriş bölümü de orijinaliyle AYNI
yapılandırılmış çiziciden geçer. Görseller/kartlar/bağlantılar yerinde kalır.

### 2) İçindekiler satırları karma dildi
Başlıklar yalnızca sözlükten yerelleştiriliyordu; sözlükte karşılığı olmayan başlık
(ör. "Powers and Abilities", "Critical Response") İngilizce kalıyordu.
**Çözüm:** yeni `reader-toc:<wiki>:<başlık>` anahtarı; sayfa açıldığında başlıklar **tek
istekte** çevrilir (`translateRowsPreservingStructure`: `⟪§⟫` satır jetonu + satır sayısı
kanıtı, kanıtlanmazsa satır satır yedek → hiçbir başlık yanlış bölümün karşısına düşmez).
Satır başlığı çözüm sırası: bölümün kendi başlık çevirisi → İçindekiler toplu çevirisi →
sözlük. İçindekiler satırı bir **bağlantı etiketi değildir** (hedef yine aynı bölümdür),
bu yüzden çevrilmesi mavi bağlantı kuralını ihlal etmez.

### 3) Orijinal ↔ Çeviri geçişinde okuma konumu kayıyordu
Çeviri farklı satır sayısı ürettiği için eski piksel kaydırma değeri başka bir paragrafa
denk geliyordu.
**Çözüm:** yeni `WikiReaderLayoutAnchor.kt` — kaydırılan sütunun kök koordinatı ve her
bloğun ölçümü ile **içerik koordinatı** hesaplanır (ölçüm Compose state'i yazmaz; layout
sırasında toplanır). Geçişten önce **görünümün üstünü içeren blok** çapa alınır; geçişten
sonra aynı bloğun okuma oranı geri kurulur:

- blok ekranda ise → oran korunur (paragrafın yarısındaysanız yine yarısında),
- blok görünümün altında başlıyorsa (araya kart/boşluk girmişse) → mesafe korunur,
- içerik kısaldıysa → `scrollState.maxValue` sınırına kırpılır,
- ölçüm yoksa → hiç dokunulmaz (konum bozulmaz).
Blok `DisposableEffect` ile ekrandan çıktığında ölçümü bırakılır; çapa her zaman
gerçekten çizilen bloklar arasından seçilir.

### 4) Sayfa geneli "çeviriye gidip gelme" yoktu
Yalnızca bölüm başına düğme vardı.
**Çözüm:** okuyucunun en üstünde `ReaderTranslationBar` (yalnızca ekranda çeviri varken
görünür): tüm yüzeyleri (başlık açıklaması + İçindekiler + açılmış tüm bölümler) tek
dokunuşla Orijinal'e döndürür; "Çeviri"ye dönüldüğünde **ağ isteği yapılmaz** — çeviri
bellekte durur (`setReaderOriginal` çeviriyi silmez). Orijinal moddayken açılan bölümler
çevrilmemiş olacağı için "Çeviri"ye dönerken yalnızca çevirisi olmayan yüzeyler çeviriye
verilir (otomatik çeviri açıksa).

### 5) Görsel altı yazıları (figcaption) çevrilmiyordu
IMAGE blokları tamamen çeviri dışıydı; beyaz metin olan `figcaption` İngilizce kalıyordu.
**Çözüm:** `WikiStructuredTranslationKeys.caption()` çocuk anahtarı; yalnızca görselin
altındaki yazı çevrilir. Görselin URL'si, dosya adı, sırası ve konumu değişmez;
`File:`/`Category:` gibi teknik değerler çevrilmez.

### 6) Karakter sekmesinde hedef dil kontrolü yoktu
Yapım wiki sekmesinde olan "kaynak zaten hedef dilde" kontrolü karakter sekmesinde yoktu
(ör. Vikipedi TR maddesi + Türkçe hedef boşuna çeviriye gidiyor, kullanıcıya iki farklı
metin varmış izlenimi veriyordu).
**Çözüm:** `FandomCharacterWikiViewModel.isTargetLanguageSource()`; okuyucu bağlantıları
başka vikiye gidebildiği için kontrol **gösterilen sayfanın** vikisine göre yapılır.

## Neden mavi bağlantılar bozulmuyor?

- Bölüm çevirisi blok blok yapılır; blok sırası/türü korunur, görsel ve kartlar yerinde kalır.
- Satır içi bağlantı etiketleri jetonla maskelenir; maske geri kurulamazsa **paragrafın
  yalnızca bağlantı dışı parçaları** ayrı ayrı çevrilir — bağlantı hiçbir durumda,
  kopmaz/çevrilmez.
- Kart başlığı bir sayfaya bağlıysa (`href`/`links` dolu) hiç çevrilmez.
- Bağlantı çipleri, "İlgili sayfalar" kartları ve İçindekiler dışı sayfa adları çevrilmez.

## Değişen dosyalar

| Dosya | Değişiklik |
|---|---|
| `wiki/FandomWikiReader.kt` | giriş bölümüne yapılandırılmış çeviri, İçindekiler başlık çevirisi, sayfa geneli Orijinal/Çeviri şeridi, okuma konumu çapası, blok ölçüm sarmalayıcıları |
| `wiki/WikiReaderLayoutAnchor.kt` (yeni) | çapa seçimi + kaydırma geri kurma (saf, test edilebilir) |
| `wiki/FandomReaderBrowser.kt` | `FandomReaderTranslationKeys.contents()` (`reader-toc:`), `isReaderKey` kapsamı |
| `wiki/WikiTranslationProtection.kt` | görsel alt yazısı çevirisi (`caption` anahtarı) + `withTranslatedBlocks` desteği |
| `wiki/FandomMediaWikiViewModel.kt` | `translateReaderContents`, `setReaderOriginal`, İçindekiler çevirisi tetikleyicileri |
| `wiki/FandomCharacterWikiViewModel.kt` | aynı eklemeler + hedef dil kaynağı kontrolü |
| `wiki/MediaWikiTabContent.kt`, `wiki/CharacterWikiTabSlot.kt`, `CharacterDetailPage.kt` | `onSetReaderOriginal` bağlantısı |
| `res/values/strings.xml`, `res/values-en/strings.xml` | 2 yeni çift: `fandom_reader_translated_notice`, `fandom_reader_original_notice` |
| `test/.../WikiReaderLayoutAnchorTest.kt` (yeni) | çapa/geri kurma matematiği (13 test) |
| `test/.../WikiTranslationProtectionTest.kt` | görsel alt yazısı + teknik değer + okuyucu anahtarı testleri |

## Doğrulama

### CI (gerçek derleme + gerçek test koşumu)

| Koşu | Ne oldu |
|---|---|
| `38949390935` | Uyarı: kurulum aşamasında NDK indirmesi bozuldu (altyapı hatası; koda dair sinyal yok) |
| `38950075976` | `compileFossDebugKotlin` **iki gerçek hata** verdi: `readerContentsTitle` `@Composable` değildi ama `stringResource` okuyan `localizedSectionTitle`'ı çağırıyordu → düzeltildi |
| `38951424067` | Ana kaynak derlendi ✅, test kaynak kümesi derlendi ✅, wiki testleri koştu; **tek kırmızı: `blockTranslationKeepsOrderKindsImagesAndLinks`** — test "görsel bloğu hiç değişmez" diyordu; yeni davranışta figcaption (beyaz metin) çevrilir → test yeni sözleşmeye göre güncellendi |
| `38952367447` | ✅ **Tüm wiki testleri ve `data.fandom.*` testleri geçti.** Kalan iki kırmızı bizimle ilgisiz ve önceden de kırıktı: `MalNotificationsClientTest.acceptsOnlySameOriginNotificationContinuationUrls`, `MalWebParserTest.followsOnlyObservedSameOriginNextPageLinks` (aynı ikisi 14:58/15:52/16:11 tarihli, bu değişikliklerden ÖNCEKİ arena koşularında da kırıktı) |

Koşulan yeni/güncellenen testler:

- `WikiReaderLayoutAnchorTest` (13 test): ölçüm → içerik koordinatı, çapa seçimi (görünümün
  üstünü içeren blok), boşluk durumunda alt blok, oran koruma, mesafe koruma, kırpma,
  `clear()`, kimlik kararlılığı (`#b<index>`, `#desc`).
- `WikiTranslationProtectionTest` eklemeleri: `imageCaptionIsTranslatedWhileTheImageKeepsItsPlaceAndUrl`,
  `technicalImageCaptionsAreNotTranslated` (`File:Jinwoo.png` çevrilmez),
  `readerKeysSeparateDescriptionSectionsAndContents`; ayrıca blok yapısı testi yeni
  figcaption sözleşmesine göre güncellendi.
- `FandomReaderBrowserTest`, `FandomImageCandidatesTest`, `FandomInfoboxLabelsTest`,
  `FandomSectionTitlesTest` de yeşil.

CI artık wiki paketini koşuyor (`android-build.yml` içine `--tests '...ui.screens.detail.wiki.*'`
eklendi); aksi hâlde bu testler derleniyor ama hiç çalışmıyordu.

### Statik kontroller

- TR/EN string paritesi **7603 = 7603** (fark kümesi boş); iki dosya da XML olarak geçerli.
- Değişen UI dosyalarındaki tüm `R.string.*` başvuruları iki dilde de mevcut (otomatik kontrol).
- Parantez/küme parantezi dengesi ve `git diff --check` temiz.

### Ek düzeltmeler (CI sonrası)

- `readerContentsTitle` artık `@Composable` (dil değişince satır kendiliğinden yenilenir).
- Medya wiki VM'inde İçindekiler akışı ve içerik parmak izi: sayfa/başlık listesi değişirse
  `pruneStaleTranslations` eski satırları düşürür.
- Görsel bloğu sözleşmesi netleşti: tür, sıra, URL, dosya adı **değişmez**; yalnızca
  figcaption çevrilir.

### Yerel sınırlama

Bu ortamda JVM yok (`java`, `javac`, `kotlinc` bulunamadı); derleme/test yukarıdaki CI
koşularıyla doğrulandı. Yerelde tekrarlamak isteyen için:

```bash
./gradlew :app:testFossDebugUnitTest --tests 'com.kitsugi.animelist.ui.screens.detail.wiki.*'
```

## Bilinçli olarak çevrilmeyenler

- Sayfa başlığı, mavi bağlantı etiketleri, kart başlıkları, bağlantı çipleri, "İlgili
  sayfalar" kartları (hepsi bir sayfaya giden **tıklanır adlardır**).
- URL'ler, dosya adları, `File:`/`Category:` değerleri.
- Hedef dille aynı dildeki kaynaklar (çeviri hiç başlatılmaz).
- Tarih/ay adları (bilinçli olarak önceki planlarda kullanıcı onayına bırakılmıştı).
