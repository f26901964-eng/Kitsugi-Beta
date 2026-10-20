# Wiki Okuyucu — Mavi (Bağlantı) Metinlerin Otomatik Çevirisi

**Tarih:** 2026-10-21
**Sürüm:** v2.4.318
**Kapsam:** Fandom/Vikipedi okuyucu (açılır wiki sayfaları), detay sayfası wiki bölümleri, karakter wikisi
**Ana dosya:** `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/WikiTranslationProtection.kt`
**Test:** `app/src/test/java/com/kitsugi/animelist/ui/screens/detail/wiki/WikiTranslationProtectionTest.kt`

---

## 1. Şikâyet ve ekran görüntüsünün okunması

Gelen görüntüde (Date A Live Wiki — ana sayfa) otomatik çeviri açıkken **beş ayrı bozulma** var:

| # | Görünen | Kök neden |
|---|---------|-----------|
| 1 | “This wiki is dedicated to **everything related to the series Date A Live, that anyone can edit!**” cümlesi hiç çevrilmemiş | Kart başlığı bir sayfaya bağlı (`href` dolu) olduğu için `shouldTranslateBlockText` **tüm başlığı** çeviri dışında bırakıyordu. Oysa bu bir sayfa adı değil, tam bir cümle. |
| 2 | “oluşturduk 678 **articlesve** yaklaşık olarak yüklenmiş **10,857 images** …” | Mavi kelimeler (`articles`, `images`, `stubs`) çevrilmiyordu. Paragraf, bağlantıların arasındaki parçalara bölünüp **her parça ayrı istekte** çevriliyordu. Sağlayıcı parçaların başındaki/sonundaki boşlukları kırptığı için `678` + `articles` + `ve` kelimeleri boşluksuz yapıştı. |
| 3 | “Characters — Terminology — Media (Light Novel · Anime · Music · Game)” satırı İngilizce kalmış | Aynı sebep: satır içi bağlantı etiketleri çeviriye hiç gönderilmiyordu. |
| 4 | “**Bu har sayfası:** https://store.steampowered.com/…” | Kart alt satırındaki “Steam page” metni tek başına çeviriye gidince Google “Steam”i marka olarak değil sözcük olarak çevirdi (“Buhar”). Parça parça çeviri bağlamı koparıyor. |
| 5 | “Hikaye” başlığı ve kartı **iki kez** | Bu, çeviriden bağımsız ayrı bir konu: ana sayfa modülleri hem “giriş” (lead) kartında hem de İçindekiler’den açılan bölümde çiziliyor (bkz. §7). |

Kullanıcı isteği net: **mavi kısımlar da çevrilsin**, ama (a) tam konumunu korusun, (b) tıklanabilir kalsın, (c) tıklanınca gidilecek adres arkada aynı kalsın, (d) uygulama kasmadan/donmadan ve çeviri yavaşlamadan yapılsın.

---

## 2. Eski sözleşme ve neden değişti

Önceki tasarım (v2.3.x → v2.4.317) bilinçli olarak **etiketleri çevirmiyordu**:

```kotlin
// ESKİ: yalnızca etiketler ARASINDAKİ metni çevir; bağlantıları kendi yuvalarında tut.
val protectedRanges = linkLabelRanges(text, links)
for (range in protectedRanges) { … translateTextSegment(…) … }
```

Bu yaklaşım bağlantıyı koruyordu ama üç bedeli vardı:

1. **Yarı İngilizce cümle** — kullanıcının şikâyeti.
2. **Boşluk kaybı** — her parça ayrı istek olduğu için sağlayıcı kenar boşluklarını kırpıyor, kelimeler yapışıyor.
3. **İstek patlaması** — bir paragrafta 5–8 bağlantı varsa 5–9 ayrı ağ isteği. Yavaşlığın ve 429 (rate limit) hatasının ana kaynağı buydu.

Çizici (`FandomWikiReader.buildReaderLinkedText` → `wikiLinkRanges`) tıklanabilir alanı **metnin içinde etiket arayarak** buluyor. Metin çevrilip etiket çevrilmezse eşleşme kopar; bu yüzden eski çözüm etiketi hiç çevirmemekti.

---

## 3. Yeni yaklaşım: “yuvalı çeviri” (slot-based translation)

Anahtar fikir: **etiket çeviriye gitsin ama kimliği kaybolmasın.**

```
Kaynak blok : Read the Characters and Terminology pages.
Yuvalanmış  : Read the ⟦L0⟧Characters⟦/0⟧ and ⟦L1⟧Terminology⟦/1⟧ pages.
Sağlayıcı   : ⟦L0⟧Karakterler⟦/0⟧ ve ⟦L1⟧Terminoloji⟦/1⟧ sayfalarını okuyun.
Ekranda     : [Karakterler]  ve  [Terminoloji]  sayfalarını okuyun.   ← ikisi de tıklanabilir
```

* Jetonlar `⟦L<yuva>⟧ … ⟦/<yuva>⟧` biçiminde. `⟪…⟫` **bilinçli olarak kullanılmadı**:
  `TranslationManager.maskMarkdown` çeviri sonundaki tanınmayan `⟪…⟫` jetonlarını temizliyor,
  `⟦…⟧` biçimi bu temizlikten etkilenmiyor.
* Sağlayıcı cümleyi **tek parça** görür → bağlam korunur (“678 articles ve …”).
* URL **hiç gönderilmez** (teknik değer kuralı zaten vardı, artık etiket de yalnızca metin olarak gider).
* Yanıt ayrıştırılırken her yuva **tam bir kez** açılıp kapanmalı ve yuvalar üst üste binmemeli.
  Bu doğrulanamazsa **güvenli yedeğe** düşülür: metin eski yoldan (parça parça, etiketler orijinal)
  çevrilir. Yarım jeton, kaymış bağlantı veya cümlenin tamamını yutan bir mavi alan **asla** çizilmez.

### 3.1 Kalıcı eşleme: `…:l<indeks>`

Çizici, tıklanabilir aralığı **çevrilmiş metin + çevrilmiş etiket** ile bulmak zorunda. Bu yüzden
etiket çevirisi metinle **aynı çeviri haritasında** saklanır:

```
<baseKey>\u0000b<blok>\u0000b<blok>:s      → alt satır
                                  :i      → görsel altı yazısı
                                  :l<idx> → <idx>. bağlantının ÇEVRİLMİŞ etiketi
```

* `withTranslatedBlocks` bu anahtarları `FandomBlockLink.label` alanına geri yazar; **`url` hiç değişmez.**
* Etiket anahtarları metinden **önce** akıtılır (`onBlock`), böylece metin ekrana geldiği ilk karede
  mavi alan zaten tıklanabilir olur.
* Bir blokta tek bir etiket haritası vardır: paragraf metni, kart alt satırı ve görsel açıklaması
  aynı bağlantı için **aynı** çevrilmiş etiketi taşır (`preferredLabels`), yoksa `links` listesi
  bunlardan birini bulamaz.

### 3.2 Güvenlik ağları

| Durum | Davranış |
|-------|----------|
| Sağlayıcı jetonu düşürdü/bozdu | O blok için güvenli yedek: parça parça çeviri, etiketler orijinal, bağlantı korunur |
| Eski önbellekten yalnızca metin var (etiket anahtarı yok) | Metin çevirisi **reddedilir**, orijinal çizilir (kırık mavi metin gösterilmez) |
| Çevrilmiş metin kendi bağlantılarını bulamıyor | O yüzeyin çevirisi reddedilir (`keepsLinks`) |
| Etiket teknik değer (`File:…`, URL, dosya adı) | Hiç gönderilmez |
| Çeviri ağ hatası | `null` → orijinal; coroutine iptali yeniden fırlatılır |
| Kullanıcı “Orijinal”e bastı | `withoutTranslation(baseKey)` etiket anahtarlarını da birlikte siler |

### 3.3 Hız / bellek

* **İstek sayısı azaldı:** blok başına 1 istek (cümle + etiket birlikte). Eskiden blok başına
  bağlantı sayısı kadar istek vardı. 40 tekrarlı bağlantı listesi bile **tek** istek.
* Jetonlu metin deterministik üretildiği için `TranslationManager`'ın Room parça önbelleği
  aynı blok tekrar açıldığında **doğrudan isabet** eder.
* Ayrıştırma O(yuva × metin) ve yalnızca `Dispatchers.IO` içinde, blok boyutunda çalışır;
  Compose tarafında ek maliyet yok (çizici zaten `remember(text, links, …)`).
* `parseLinkSpans` üst sınır koyar: yuva açılış/kapanışı eşleşmezse iş derhal kesilir (fallback).

---

## 4. Değişen dosyalar

| Dosya | Değişiklik |
|-------|-----------|
| `ui/screens/detail/wiki/WikiTranslationProtection.kt` | Yuvalı çeviri motoru, `:l<idx>` anahtarları, `WikiTranslatedBlockText`, güvenli yedek, `keepsLinks` doğrulaması, cümle görünümlü kart başlığı kuralı, `WikiLinkRange.linkIndex` |
| `WikiTranslationProtectionTest.kt` | Sözleşme testleri güncellendi + 6 yeni test |
| `app/build.gradle.kts` | `appVersionName = "2.4.318"` |
| `release_notes_v2.4.318.md`, `RELEASE_NOTES.md` | Sürüm notları |

Çizim tarafında (`FandomWikiReader.kt`, `MediaWikiTabContent.kt`, `CharacterWikiTabContent.kt`)
**hiçbir değişiklik gerekmedi**: hepsi zaten `withTranslatedBlocks` üzerinden çiziyor ve
`wikiLinkRanges` ile tıklanabilir alanları buluyor.

---

## 5. Davranış matrisi (özet)

| Senaryo | Sonuç |
|---------|-------|
| `Read the Characters and Terminology pages.` | `Karakterler ve Terminoloji sayfalarını okuyun.` — 2 bağlantı da yerinde, URL'ler aynı |
| Google bir yuvayı düşürdü | Metin parça parça çevrilir, etiketler orijinal, 2 bağlantı da tıklanabilir |
| Yalnızca bağlantıdan oluşan satır (`Rick · Morty`) | Değişiklik yok; en fazla 1 istek |
| Kart başlığı = sayfa adı (`Read more`, `Kimi no Na wa. Soundtracks`) | Çevrilmez, istek gitmez |
| Kart başlığı = cümle (ana sayfa kartı) | Çevrilir, `href` korunur |
| Görsel altı yazısı | Aynı yuvalı akıştan geçer |
| “Orijinal”e bas | Metin + etiket çevirileri birlikte gizlenir |

---

## 6. Doğrulama

```bash
./gradlew :app:testDebugUnitTest --tests "*WikiTranslationProtectionTest*"
```

Yeni/güncellenen testler:

1. `blockWithLinksIsTranslatedAsOneSentenceAndLabelsKeepTheirSlot` — tek istek, jetonlar yerinde.
2. `inlineLinkLabelsAreTranslatedInPlaceAndUrlsNeverChange` — etiket çevrilir, URL aynı, aralıklar çözülür.
3. `translatedLabelsArePersistedAsChildKeysSoTheCacheRoundTripKeepsLinks` — anahtar kalıcılığı + eski önbellek reddi.
4. `brokenSlotsFallBackToUntranslatedLabelsWithoutLosingTargets` — bozuk jeton → güvenli yedek, jeton sızıntısı yok.
5. `translatedLabelKeysAreStreamedBeforeTheTranslatedText` — akış sırası.
6. `linkedCardTitleThatIsARealSentenceIsTranslatedButKeepsItsHref` / `shortLinkedCardTitleStaysAsAPageName`.
7. `manyRepeatedLinksRemainProtectedWithoutTokenLimits` — 40 tekrar, tek istek.

Manuel kontrol (Date A Live Wiki → ana sayfa, otomatik çeviri açık):

- [ ] “This wiki is dedicated to …” cümlesi Türkçe, karta dokununca hâlâ aynı sayfa açılıyor.
- [ ] “Characters — Terminology — Media …” satırı Türkçe ve her çip tıklanabilir.
- [ ] “… 678 madde ve 10.857 görsel …” cümlesinde kelimeler yapışmıyor.
- [ ] “Orijinal”e basınca metin ve mavi etiketler birlikte İngilizceye dönüyor.
- [ ] Sayfa açılışı, çeviri sırasında donmuyor; ağ trafiği (Logcat: `TranslationManager`) istek sayısı eskisinden az.

---

## 7. Kapsam dışı bırakılanlar / sıradaki adımlar

1. **Aynı bölümün iki kez çizilmesi (“Hikaye” x2).** Bu bir çeviri hatası değil: `FandomWikiReader`
   hem `ReaderLeadCard` (bölüm 0 = ana sayfanın tüm modülleri) hem de İçindekiler’den açılan bölüm
   içeriğini çiziyor. Ana sayfalarda bölüm 0 ile bölüm *n* aynı blokları taşıdığı için içerik
   tekrarlanıyor. Öneri: ana sayfa modunda İçindekiler satırı açıldığında lead kartındaki aynı
   blokları çizme (blok imzası ile tekilleştirme) veya lead kartını yalnızca ilk bölüm için gösterme.
2. **Marka adlarının çevrilmesi** (“Steam page” → “Buhar sayfası”). Cümle bütünlüğü artık korunduğu
   için bu hata büyük ölçüde azalır; kalıcı çözüm için kısa bir marka sözlüğü (Steam, Netflix,
   Crunchyroll, IMDb …) etiket çevirisinden önce korunabilir.
3. **Bağlantı çipleri (LINK_GROUP).** Çip etiketleri sayfa adıdır ve kart başlığıyla aynı kurala
   uyar: orijinal kalır. İstenirse burada da yuvalı çeviri açılabilir (tek satır değişiklik).
4. **Türkçe ek uyumu.** Sağlayıcı cümleyi tek parça çevirdiği için “Date A Live'e” gibi ekler artık
   çoğunlukla doğru gelir; yine de jeton komşuluğunda tam otomatik ek üretimi yapılmıyor.
