# PLAN/TASK — Fandom Wiki entegrasyonu (önce kimlik, son çare isim)

Tarih: 2026-10-12
Durum: **Faz 1 kodlandı (2026-10-13).** Derleme, birim testleri ve cihaz testi kullanıcıda.
Güncelleme: İsimle bulma, kimlikle doğrulanan **son çare** olarak eklendi.
Uygulanan dosyalar ve test rehberi: en alttaki "Faz 1 — Uygulama notları" bölümü.

## Amaç

Detay sayfalarında yapımın ve karakterin Fandom wiki içeriğini (bilgi kutusu, bölümler, evren terimleri) Kitsugi içinde göstermek.
Önce kimlikle eşleştirilir. Kimlik yolu tükenirse isimle aday bulunur, ama bu aday **kimlikten gelen bağımsız kanıtla** doğrulanmadan gösterilmez.

## Temel kararlar

1. **Kimlik zinciri.** Fandom'da AniList/MAL/TMDB kimliği için standart alan yok. Köprü Wikidata: aynı kayıtta kaynak kimlikleri ile Fandom wiki kimliği (P4073) ve Fandom sayfa kimliği (P6262) birlikte tutuluyor.
2. **Arama sırası:**
   1. Eşleme tablosu
   2. Önbellek
   3. Wikidata
   4. İlişki yürüyüşü
   5. **Son çare: isimle bulma**
   6. Hiçbiri tutmazsa Wiki sekmesi görünmez.
3. **İsim yalnızca aday üretir, kabul kararını kanıt verir.** Kanıt, kaynaktan kimlikle zaten bildiğimiz şeylerdir:
   - Ana karakterler ve onların Japonca/Korece adları
   - Yazar ve stüdyo
   - Varsa wiki'nin AniList/MAL linki

   Yapay zekâ kullanılmaz.
4. **Kendi eşleme tablomuz.** Wikidata'da bağlantısı eksik popüler yapımlar için küçük, elle doğrulanmış tablo: `assets/fandom/fandom_overrides.json`.
   - Anahtar: AniList/MAL kimliği **veya** Wikidata QID'si. Seri/franchise QID'si verilirse tüm sezonları kapsar.
   - İleride GitHub'dan güncellenebilir.
5. **Kotlin'e port, yeni bağımlılık yok.** OkHttp (`KitsugiHttpClient`), Jsoup, org.json ve Room zaten var. Python gömülmez.
6. **Tembel yükleme.**
   - Sayfa açılınca yalnızca "wiki var mı?" sorulur.
   - İçerik, sekme açılınca çekilir.
   - Arka planda toplu tarama yapılmaz.

## Wiki nerede görünecek?

| Ekran | Dosya | Eklenecek | Faz |
|---|---|---|---|
| Karakter detayı (telefon + TV aynı sayfa) | `ui/screens/detail/CharacterDetailPage.kt` | Sona **Wiki** sekmesi: Hakkında · Yapımlar · Seslendirenler · **Wiki** | 1 |
| Ayarlar | `ui/screens/settings/SettingsScreen.kt` | Fandom Wiki: aç/kapat · isimle eşleştirme · spoiler koruması · Türkçe wiki önceliği | 1 |
| Anime/manga detayı — listedeki kayıt | `ui/screens/detail/MediaEntryDetailPage.kt` | Sona **Wiki** sekmesi (Yorumlar / Bölümler'den sonra) | 2 |
| Anime/manga detayı — arama/keşfet sonucu | `ui/screens/detail/ApiResultDetailPage.kt` | Aynı **Wiki** sekmesi | 2 |
| TV anime detayı (sekme yok, satırlar var) | `ui/tv/detail/TvDetailScreen.kt` | Yeni **Wiki** satırı (kumandayla gezilen kartlar) | 2 |
| Bilgi sekmesi | `EntryDetailTabContents.kt` / `ApiDetailTabContents.kt` | "Bu yapımın wiki'si var →" kısayol kartı | 2 |
| Görünmez | `data/local/TranslationManager.kt` | Çeviride isim/terim koruması | 2 |
| Bölümler sekmesi | `KitsugiDetailEpisodesTab.kt` | "Önceki bölümde…" kartı | 3 |
| Oynatıcı (telefon + TV) | `ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt`, `ui/tv/player/TvPlayerScreen.kt` | Duraklatınca X-Ray | 3 |
| Manga detayı | `ui/screens/manga/MangaDetailScreen.kt` | "Mangaya buradan devam et" | 3 |

**Sekme neden en sonda?**
- Sekmeler kodda sıra numarasıyla bağlı (`when (page) { 0 -> … 8 -> … }`, `loadTab(tabIndex)`). Araya eklemek mevcut sekmeleri kaydırır.
- Wiki sekmesi ancak wiki bulununca belirdiği için sona eklenmesi diğer sekmeleri oynatmaz.

## Veri akışı

```
Detay sayfası açılır
  → FandomRepository.resolve…()   (arka planda; önbellekte varsa 0 istek)
      1 eşleme tablosu → 2 önbellek → 3 Wikidata (P4073/P6262)
      → 4 ilişkili yapımların kimlikleri → 5 son çare: isimle aday + kanıtla doğrulama
  → wiki bulunduysa Wiki sekmesi belirir; bulunamadıysa hiçbir şey değişmez
Kullanıcı Wiki sekmesini açar
  → yalnızca giriş bölümü + bilgi kutusu çekilir
  → diğer bölümler kullanıcı açtıkça yüklenir
  → İngilizce metin → mevcut çeviri düğmesi (TranslationManager)
```

İsimle bulunan sonuç da **kimliğe bağlanarak** önbelleğe yazılır (`anilist:<id> → wiki`). Böylece isim araması her yapım için 30 günde en fazla bir kez çalışır.

## Kimlik zinciri (teknik)

### Wikidata alanları (2026-10-12'de doğrulandı)

| Kaynak | Anime | Manga | Karakter |
|---|---|---|---|
| AniList | P8729 | P8731 | P11736 |
| MyAnimeList | P4086 | P4087 | P4085 |
| Kitsu | P11495 | P11494 | — |
| Bangumi | P5732 | P5732 | — |
| TMDB | P4983 (dizi), P4947 (film) | — | — |
| Shikimori | Alan yok. Shikimori kimliği = MAL kimliği | | |
| Simkl | Alan yok. Simkl çapraz kimlikleri kullanılır (MAL/AniList/TMDB) | | |
| **Fandom** | **P4073** wiki kimliği · **P6262** sayfa kimliği (`wiki:Sayfa_Adı`) | | |

### Yapım → wiki

1. **Eşleme tablosu.** `fandom_overrides.json` içinde kimlik varsa doğrudan kullanılır.
2. **Önbellek.** Daha önce çözülmüş sonuç varsa kullanılır.
3. **Wikidata.**
   - `KitsugiIdResolver.resolveIds(...)` bir `wikidataId` döndürdüyse o kullanılır. Döndürmediyse şu arama yapılır:
     `https://www.wikidata.org/w/api.php?action=query&list=search&srsearch=haswbstatement:P8729=<id>&format=json`
     - Anime için sıra: P8729 → P4086 → P11495 → P5732 → P4983/P4947.
     - Manga için sıra: P8731 → P4087 → P11494.
   - Wiki kimliği `action=wbgetentities&ids=<QID>&props=claims` ile okunur (P4073).
   - P4073 yoksa en fazla 2 adım yukarı çıkılır: P179 (dizinin parçası), P144 (uyarlandığı eser), P8345 (medya franchise'ı), P361 (parçası olduğu), P155/P156 (önceki/sonraki sezon). Üst kayıtlar `wbgetentities` ile toplu okunur (≤50); bu adımda eşleme tablosunda QID anahtarı da aranır.
4. **İlişki yürüyüşü (hâlâ kimlik).** AniList ilişkilerinden önceki/sonraki sezon, ana seri, kaynak manga/light novel ve yan hikâyelerin kimlikleri alınır; bunlar için 1–3. adımlar tekrarlanır. Film, OVA ve devam sezonları çoğu zaman bu adımda çözülür.
5. **Son çare: isimle bulma.** Ayrıntısı aşağıdaki bölümde.
6. **Aday seçimi ve önbellek.** Saf fonksiyon olarak yazılır ve test edilir.
   - **Çapraz/genel wikiler elenir:** `hero`, `villains`, `isekai`, `cosplay`, `dubbing`, `*.doblaje`, `*-dubber`, `dubdb`, `netflix`, `tropedia`, `animanga`, `anime`, `manga`, `vsbattles`, `powerlisting`, `*fanon*`, … Liste, Python'daki `CROSSOVER_BLOCKLIST` ve Wikidata'da görülenlerden oluşur.
   - **Ana wiki öncelikli.** Dil öneki olmayan wiki seçilir; `tr.` sürümü ayrıca not edilir.
   - **Birden fazla ana aday varsa** makale sayısı büyük olan seçilir (`meta=siteinfo&siprop=statistics`). Örnekler: AoT için `attackontitan` / `shingekipedia`, MHA için `bokunoheroacademia` / `myheroacademia`.
   - **Önbellek süreleri:** bulunduysa 30 gün; bulunamadıysa 3 gün (yayındaki yapım) veya 7 gün (diğerleri).

**URL notu:**
- P4073 `tr.onepiece` → `https://onepiece.fandom.com/tr/api.php`
- `jujutsu-kaisen` → `https://jujutsu-kaisen.fandom.com/api.php`

## Son çare: isimle bulma (güçlü doğrulama)

İlke: **isimle bul, kimlikle doğrula.** İsim yalnızca aday listesini üretir. Bir adayın kabulü, kaynaktan kimlikle bildiğimiz bilgilerin wiki'de bulunmasına bağlıdır.

### A. Aday üretme (en fazla 3 arama + 6 adres denemesi)

- **Sorgular** (kaynaktan kimlikle gelen veriden):
  - İngilizce başlık
  - Romaji başlık
  - Eş anlamlı başlıklar
  - **Başlık + yazar** (ör. `Monster Naoki Urasawa`)
  - Devam sezonlarında kök başlık ("Season 2", "2nd Season", "Part 2", "Movie" ve alt başlıklar ayıklanır)
- **Fandom topluluk araması:** `https://services.fandom.com/unified-search/community-search?query=<sorgu>&lang=en&limit=8`. Sonuçta ad, URL, `hub` (anime / games / tv / books …), sayfa sayısı, görüntülenme ve açıklama gelir.
- **Adres denemesi:** başlıktan üretilen adresler (`spy-x-family`, `spyxfamily`, …) `siteinfo` ile denenir. Python `slug.py` mantığı burada kullanılır.
- **Normalizasyon:** `×` → `x`, köşeli parantez ve noktalama temizliği (`[Oshi no Ko]`), uzatma işaretleri (`ō` → `o`), `:` ve alt başlık ayrımı.

### B. Ön eleme (ek istek yok, arama sonucundan)

- Kara liste, `fanon` / `RP` / `simulator` gibi kelimeler, 20 makaleden küçük wikiler ve dil (en/tr dışı) elenir.
- `hub = anime` / `books` / `tv` ise artı puan; `games` / `lifestyle` ise eksi puan.
- Topluluk açıklamasında ana karakter adı veya yazar geçiyorsa artı puan.

### C. Doğrulama (kalan en iyi 3 aday için birer istek)

Tek istekle karakter **parmak izi** alınır:

```
https://<wiki>.fandom.com/api.php?action=query&redirects=1
  &titles=<ana ve yan karakter adları + varyantları>   (≤50 başlık)
  &prop=pageprops&ppprop=infoboxes|fandomdescription&format=json&formatversion=2
```

- **Karakter bulunma oranı.** Kaynaktaki ana (MAIN) karakterlerin kaçının sayfası var? Wiki'nin kendi yönlendirmeleri yazım farklarını çözer (ör. `Kenzo Tenma` → `Kenzō Tenma`, `Heinrich Lunge` → `Heinrich Runge`, `Sung Jin-Woo` → `Sung Jinwoo`).
- **Yerel ad teyidi.** `pageprops.infoboxes` bilgi kutusunu hazır JSON olarak veriyor. İçindeki Kanji / Hangul / Japanese alanı, AniList/MAL `native` adıyla karşılaştırılır (boşluk ve `・` temizlenerek). Bu, isimden bağımsız en güçlü kanıttır.
- **Ek kanıtlar (gerekirse):**
  - `fandomdescription` içinde seri adının geçmesi
  - Yazar veya stüdyo sayfası/adı
  - `list=exturlusage` ile wiki'de birebir `anilist.co/anime/<id>` veya `myanimelist.net/anime/<id>` linki. Nadir bulunur ama bulunursa kesin kabul.

### D. Puanlama ve karar

| Kanıt | Ağırlık |
|---|---|
| Ana karakter bulunma oranı | 0,40 |
| Yerel ad (Kanji/Hangul) teyidi | 0,20 |
| Wiki adı ↔ başlık benzerliği (fazladan kelime cezası: "ARISE", "Simulator"…) | 0,15 |
| Hub uyumu (anime/books/tv) | 0,10 |
| Yazar/stüdyo izi | 0,10 |
| Büyüklük/etkinlik (makale, görüntülenme) | 0,05 |
| Birebir AniList/MAL linki | doğrudan kabul |

**Vetolar:**
- Kara listedeki wiki
- Kaynakta en az 2 ana karakter varken parmak izinin 0 çıkması
- Dil uyumsuzluğu

**Karar:**
- **Kesin (≥ 0,85 ve en az bir yerel ad teyidi):** Wiki sekmesi normal görünür. Altta "Otomatik eşleşme · Yanlışsa bildir" yazar.
- **Muhtemel (0,65–0,85):** Sekme görünür, üstte bir kerelik "Bu wiki doğru mu? Evet / Hayır" kartı çıkar. Cevap kimliğe bağlı saklanır.
- **Zayıf (< 0,65) veya veto:** Sekme görünmez; negatif önbelleğe yazılır.
- **Doğrulama verisi yoksa** (kaynakta karakter listesi boş, AniList'e ulaşılamadı vb.) isim yolu sonuç vermez; puan eşiğe ulaşamaz.

### E. Öğrenme ve güvenlik ağı

- Kabul edilen eşleşme kimliğe bağlanır; sonraki açılışlar kimlik yolundan gelir.
- "Bu wiki yanlış" → o kimlik için yerel kara liste + varsa sıradaki aday.
- Onaylanan eşleşmeler ileride GitHub'daki eşleme tablosuna aktarılabilir.

### F. İstek bütçesi

İsim yolu yalnızca kimlik yolu başarısız olunca çalışır:
- En fazla yaklaşık 12 küçük istek: 3 arama + 6 `siteinfo` + 3 parmak izi.
- Düşük öncelikli; aynı anda en fazla 2 istek.
- Sonuç 30 gün saklanır (negatif sonuç 3–7 gün).

### G. Canlı test (2026-10-12)

**Monster (2004) — genel isim tuzağı.**
- `Monster` araması: ilk sıradaki aday `monster.fandom.com` çıktı. Bu, genel "canavarlar" wiki'si (4000 sayfa).
- Parmak izi **0/5** (Kenzo Tenma, Johan Liebert, Nina Fortner, Heinrich Lunge, Wolfgang Grimmer yok) → **veto**.
- `Monster Naoki Urasawa` (başlık + yazar) araması: ilk sırada "Naoki Urasawa's Monster Wiki" (`obluda.fandom.com`). Bu adres tahminle asla bulunamazdı.
- Parmak izi **5/5**. `Kenzo Tenma` → `Kenzō Tenma`, `Heinrich Lunge` → `Heinrich Runge` yönlendirmeleri wiki'nin kendisinden geldi.

**Solo Leveling — aynı serinin oyun wiki'si.**
- Adaylar: `solo-leveling` (hub: anime, ~600 sayfa) ve `solo-leveling-arise` (hub: games, ~300 sayfa).
- İkisinde de Sung Jinwoo sayfası var; oyun wiki'sinde ayrıca Cha Hae-In, Yoo Jinho, Go Gunhee sayfaları da var. Parmak izi tek başına ayırmıyor.
- Hub, birebir wiki adı ("Solo Leveling Wiki" / "Solo Leveling: ARISE Wiki") ve büyüklük doğru olanı seçtiriyor.
- Bilgi kutusundaki Hangul `성진우` yerel ad teyidi için kullanılabilir. `Jinwoo Sung` (Batı sırası) yok, `Sung Jin-Woo` yönlendirmesi var → ad sırası ve tire varyantları gerekli.

## Karakter → wiki sayfası

1. **Wikidata.** AniList karakter kimliği (P11736) veya MAL karakter kimliği (P4085) ile `haswbstatement` → P6262 (ör. `jujutsu-kaisen:Satoru_Gojo`). P6262 değeri yapımın wiki'siyle aynı olmalı; çapraz wikiler elenir. `CharacterDetailViewModel` bu iki kimliği ve `nativeName`'i zaten hesaplıyor.
2. **Wiki içinde ad varyantlarıyla tek istek.** Kimlik zinciri ya da isim yoluyla bulunmuş wiki'de şu varyantlar tek `titles=` isteğinde denenir:
   - Tam ad ve ters sıra (Japon/Kore sırası ↔ Batı sırası)
   - Uzatma işaretsiz yazım (`Gojō` → `Gojo`)
   - Tire/boşluk varyantları (`Jin-Woo` ↔ `Jinwoo`)
   - AniList alternatif adları
   - Wiki'de tekse yalnızca ilk ad

   İstek `redirects=1` + `ppprop=infoboxes` ile atılır.
3. **Wiki içi arama.** Varyantlar tutmazsa `list=search&srsearch=<ad>&srlimit=5` ile en iyi 5 sayfa alınır.
4. **Kabul kuralı.** Aşağıdakilerden biri gerekir:
   - Bilgi kutusundaki yerel ad = kaynaktaki `native` ad
   - Yerel ad yoksa: tek birebir başlık eşleşmesi + sayfanın karakter kategorisinde olması

   Ad ayrım (disambiguation) sayfası çıkarsa bağlantıları açılıp aynı kuralla seçilir. Kesin değilse sekme görünmez.
5. TMDB kaynaklı karakter sayfalarında Faz 1'de Wiki sekmesi yok.

## Sayfa ayrıştırma

- **Bilgi kutusu ve kısa açıklama:** `prop=pageprops&ppprop=infoboxes|fandomdescription`.
  - `infoboxes`: portable infobox'ın hazır JSON hâli (gruplar, etiketler, değerler, görseller).
  - `fandomdescription`: kısa özet.
  - 50 sayfaya kadar tek istekte alınabilir; HTML ayrıştırmaya gerek kalmaz. Değerlerdeki küçük HTML parçaları (`<li>`, `<small>`, `<sup>`) temizlenir.
- **Bölüm başlıkları:** `action=parse&prop=sections`.
- **Bölüm metni:** kullanıcı açtıkça `action=parse&section=N&prop=text&disableeditsection=1&disablelimitreport=1`, Jsoup ile işlenir.
  - Temizlenecekler: `sup.reference`, `ol.references`, `.mw-references-wrap`, `table` (bakım uyarıları), `.navbox`, `.notice`, `.mbox`, `.toc`, yorum düğümleri.
  - Gizlenecek bölümler: References, Navigation, Gallery/Image Gallery, External Links, See Also ve diğer dillerdeki karşılıkları.
- **Örnek** (`jujutsu-kaisen:Satoru_Gojo`):
  - Bilgi kutusu grupları: Biographical Information, Personal Description, Professional Status, First Appearance, Portrayal.
  - Bölümler: Appearance, Personality, Abilities and Powers, Battles & Events, Trivia, References, Navigation.
- **Spoiler koruması (varsayılan açık):** Aşağıdakiler bulanık gösterilir, dokununca açılır.
  - Status/Durum/Estado alanları
  - History, Synopsis, Plot, Story, Battles & Events, Geçmiş, Hikâye bölümleri
- **Saklanan veri:** Ham HTML değil, sadeleştirilmiş JSON. Böylece veritabanı şişmez.

## Fazlar ve görevler

### Faz 1 — Altyapı (kimlik + son çare isim) + karakter Wiki sekmesi

- [x] `data/fandom/FandomModels.kt` — `FandomWikiRef` (kaynak: tablo / wikidata / ilişki / isim + güven), `FandomInfobox`, `FandomSection`, `FandomCharacterPage`.
- [x] `data/fandom/WikidataClient.kt` — `haswbstatement` araması ve `wbgetentities` claim okuma (org.json).
- [x] `data/fandom/FandomCandidateSelector.kt` — kara liste, dil ayrımı, makale sayısıyla seçim, URL üretimi (saf fonksiyonlar).
- [x] İlişki yürüyüşü ve topluluk araması + adres denemesi — `FandomWikiResolver.kt` içinde (`resolveViaRelations`, `searchByName`); ağ çağrıları `FandomApi.kt`.
- [x] `data/fandom/FandomNameMatcher.kt` — ön eleme, parmak izi isteği, yerel ad teyidi, puanlama, vetolar, karar (saf puanlama fonksiyonları ayrı).
- [x] `data/fandom/FandomNameVariants.kt` — başlık ve karakter adı varyantları, normalizasyon.
- [x] `data/fandom/FandomWikiResolver.kt` — 6 adımlı zinciri yönetir.
- [x] `data/fandom/FandomCharacterResolver.kt` — karakter → sayfa (Wikidata → varyantlar → wiki içi arama + yerel ad teyidi).
- [x] `data/fandom/FandomApi.kt` — MediaWiki çağrıları: `siteinfo`, `query&titles&redirects&pageprops`, `parse` (sections / section N), `list=search`, `exturlusage`.
- [x] Sayfa ayrıştırma — `FandomInfoboxParser.kt` (`infoboxes` JSON), `FandomHtmlText.kt` (bölüm HTML'i, temizlik), `FandomApiParsers.kt`, `FandomSpoilerRules.kt`.
- [x] `data/fandom/FandomRepository.kt` — UI'nin tek giriş noktası.
  - Kalıcı önbellek: `persistent_details_cache` (`fandom:` önekli anahtar).
  - Bellek önbelleği: `BoundedCache`.
  - Aynı isteği tekilleştirme, negatif önbellek, düşük öncelikli kuyruk.
- [x] `assets/fandom/fandom_overrides.json` — ilk tablo.
  - AniList 127230 → `chainsaw-man`, 140960 → `spy-x-family`, 151807 → `solo-leveling`. Üçü de 2026-10-12'de siteinfo ile doğrulandı.
  - `series_glossary.json` içindeki `anilist:` anahtarları gözden geçirilerek eklenecek.
- [x] `ui/screens/detail/wiki/FandomCharacterWikiViewModel.kt` + `CharacterWikiTabContent.kt`:
  - Başlık ve bilgi kutusu kartı
  - Açılır bölümler
  - Galeri şeridi (mevcut galeri diyaloğuna bağlanır)
  - Çeviri düğmesi
  - Kaynak satırı
  - "Bu wiki doğru mu?" kartı (muhtemel eşleşme) ve "Yanlışsa bildir" bağlantısı
- [x] `CharacterDetailPage.kt` — Wiki sekmesi hem telefon hem geniş/TV düzeninde. `when (page)` sekme anahtarına göre çalışır (TMDB'de sekme sayısı farklı).
- [x] Ayarlar: bağımsız `FandomWikiPrefs` (SharedPreferences) + `FandomWikiSettingsSection` (telefon) + TV ayarlar ekranı + `SettingsSearch` girişi. Dört anahtar da varsayılan açık: Wiki sekmesi, isimle eşleştirme, spoiler koruması, Türkçe wiki önceliği. Ek: "Wiki önbelleğini temizle".
- [x] Metinler: `values/strings.xml` + `values-en/strings.xml`.
- [x] Testler (aşağıda) + CI'ya `--tests 'com.kitsugi.animelist.data.fandom.*'` eklenmesi.

### Faz 2 — Anime Wiki sekmesi + TV satırı + Türkçe wiki + isim koruması

- [ ] `FandomGlossaryFetcher.kt` — `fetcher.py` portu:
  - Kategori grupları: karakter, organizasyon, yetenek, mekân, eşya, terim (çok dilli kategori adlarıyla)
  - `categorymembers` ile üye listesi
  - Yönlendirmelerden takma adlar
  - Oyuncu/ekip sayfalarını eleme
  - `fandomdescription` ile toplu kısa özetler
- [ ] `MediaWikiTabContent.kt` — evren rehberi çipleri ve "wiki'de ara". Çipe dokununca alt kart açılır: kısa açıklama + "Tamamını oku".
- [ ] `MediaEntryDetailPage.kt` + `ApiResultDetailPage.kt` — Wiki sekmesi sona eklenir (indeks hesaplanır). Bilgi sekmesine kısayol kartı.
- [ ] `TvDetailScreen.kt` — Wiki satırı.
- [ ] Türkçe wiki: P4073'te `tr.` sürümü varsa öncelik verilir (ayara bağlı).
- [ ] `FandomTermMatcher.kt` (PATF portu) — `TranslationManager.maskMarkdown` yer tutucularıyla isim koruması + Türkçe ek (ünlü uyumu) düzeltici.

### Faz 3 — Bölüm / oynatıcı / manga

- [ ] "Önceki bölümde…" kartı. İzlenmemiş bölümler `progress`'e göre gizli.
- [ ] Oynatıcıda X-Ray (telefon + TV).
- [ ] "Mangaya buradan devam et".
- Not: Bölüm ve chapter sayfa adları wiki'den wiki'ye değişir. Bu özellikler yalnızca güvenilir eşleşmede gösterilir.

## Testler (JVM)

- `FandomPageParserTest` — `infoboxes` JSON örneğinden gruplar/alanlar/görsel çıkarımı; bölüm HTML temizliği; spoiler işaretleri.
- `FandomCandidateSelectorTest` — kara liste, ana/dil sürümü ayrımı, makale sayısıyla seçim, `tr.` tespiti, URL üretimi (`tr.onepiece` → `onepiece.fandom.com/tr`).
- `WikidataClaimsParserTest` — `wbgetentities` JSON örneğinden P4073/P6262/üst kayıt çıkarımı.
- `FandomNameVariantsTest` — `×` → `x`, `[Oshi no Ko]`, `Kenzō` ↔ `Kenzo`, `Jin-Woo` ↔ `Jinwoo`, ad sırası, sezon eki ayıklama.
- `FandomNameMatcherTest` — bugünkü canlı test örnekleriyle:
  - Monster: genel wiki veto, `obluda` kabul
  - Solo Leveling: anime wiki'si oyun wiki'sini geçer
  - Eşik ve veto kuralları
- `FandomCharacterMatchTest` — yerel ad normalizasyonu (`五条 悟` = `五条悟`), ad ayrım sayfası, birebir başlık kuralı.

## Kurallar

- **Lisans:** Fandom metinleri CC BY-SA. Her içerikte "Kaynak: <Wiki adı> · Fandom" ve sayfa linki gösterilir.
- **İstek nezaketi:**
  - Dürüst User-Agent: `Kitsugi/<sürüm> (Android; github.com/f26901964-eng/Kitsugi-Beta)`
  - Zaman aşımı yaklaşık 5 sn
  - Aynı anda az istek, toplu tarama yok
- **Depolama:**
  - Kayıtlar `persistent_details_cache` içinde `fandom:` önekiyle tutulur.
  - Mevcut `MappingSyncWorker` (30 gün) ve `StorageJanitor` temizliği bu kayıtları da kapsar.
  - Room şeması değişmez (v29).
- **TV:** Tüm yeni öğeler `tvClickable` / odaklanabilir olur.

## Kabul kriterleri (Faz 1)

- **Satoru Gojo (AniList karakter 127691):**
  - Wiki sekmesi görünür.
  - Bilgi kutusunda Kanji, Doğum günü, Boy ve Bağlılık var; **Statü bulanık**.
  - Bölümler listelenir; References / Navigation gizli.
  - Kaynak satırı ve link var.
- **Monster (2004) karakterleri:** Kimlik yolu tutmazsa isim yolu `obluda.fandom.com`'u bulur; genel `monster.fandom.com` asla seçilmez.
- **Solo Leveling karakterleri:** `solo-leveling` seçilir, `solo-leveling-arise` seçilmez.
- Kimlik ve isim yolunda kanıt yetersizse Wiki sekmesi hiç görünmez, hata da gösterilmez.
- Wiki sekmesi açılmadan wiki içeriği indirilmez.
- İkinci açılış önbellekten, ağ isteği olmadan gelir.
- Ayar kapalıyken sekme de istek de yok. "İsimle eşleştirme" kapalıysa yalnızca kimlik yolu çalışır.
- TV'de sekme ve bölümler kumandayla açılır.

## Çalışma düzeni

1. Kod ve birim testleri `arena/97294abb-kitsugi-beta` dalında yazılır.
2. Push sonrası `.github/workflows/android-build.yml` çalışır (yalnızca `arena/**` dalında `app/**` değişince). Derler, testleri koşar ve `kitsugi-foss-debug-apk` artifact'ını üretir (7 gün saklanır).
3. APK telefonda ve TV'de denenir; geri bildirime göre düzeltilir.
4. Faz sonunda `release_notes_v2.4.27x.md` yazılır ve PR açılır.

## Ölçümler (2026-10-12, canlı)

- `haswbstatement:P8729=11757` → Q132174429 (Sword Art Online, season 1).
- 16 popüler animenin 13'ü yalnızca kimlikle wiki'ye ulaştı.
  - Bulunamayanlar: Chainsaw Man, Spy×Family, Solo Leveling.
  - Bu üçünün wikileri var: sırasıyla 589 / 749 / 704 makale.
- Wikidata'da AniList kimliği olan 7.086 anime var; yaklaşık 1.068'i P4073'e ulaşıyor. Çoğu seri/franchise üzerinden; doğrudan bağlı olan yalnızca 45.
- AniList karakter kimliği olan 6.014 karakterin 4.505'inde P6262 var. Bir kısmı çapraz wikilerde: hero, villains, isekai, cosplay, doblaje.
- Ağır SPARQL sorguları 502 / zaman aşımı verdi. Bu yüzden uygulamada Wikidata Action API (`haswbstatement` + `wbgetentities`) kullanılacak.
- İsim yolu testleri: yukarıdaki "Son çare → G. Canlı test" bölümü (Monster, Solo Leveling).

## Faz 1 — Uygulama notları (2026-10-13)

### Yeni dosyalar

`app/src/main/java/com/kitsugi/animelist/data/fandom/`

| Dosya | Görev |
|---|---|
| `FandomModels.kt` | Önbelleğe yazılan modeller (`@Serializable`): wiki eşleşmesi, bilgi kutusu, bölüm, karakter sayfası |
| `FandomUrls.kt` | Wiki kimliği ↔ URL (`tr.onepiece` → `onepiece.fandom.com/tr`), güvenli API adresi, görsel küçültme |
| `FandomJson.kt` | org.json yardımcıları (Android'de `optString` JSON `null` için "null" döndürür) |
| `FandomCandidateSelector.kt` | Çapraz/fanon/dublaj wiki kara listesi, ana wiki önceliği, makale sayısıyla seçim |
| `FandomNameVariants.kt` | Başlık/ad normalizasyonu, sezon eki ayıklama, adres ve karakter adı varyantları, yerel ad (Kanji/Hangul) teyidi |
| `FandomSpoilerRules.kt` | Spoiler alanları/bölümleri, gizlenen bölümler |
| `FandomHtmlText.kt` | Bilgi kutusu değeri ve bölüm HTML'ini sade metne/bloklara çevirme (Jsoup) |
| `FandomInfoboxParser.kt` | `pageprops.infoboxes` JSON ayrıştırma |
| `FandomNameMatcher.kt` | Son çare isim yolunun puanlaması, vetolar, karar |
| `WikidataParsers.kt` | Wikidata Action API + SPARQL yanıtları, güvenli SPARQL üretimi |
| `FandomApiParsers.kt` | MediaWiki + Fandom topluluk araması yanıtları |
| `FandomAniListParser.kt` | Doğrulama verisi (başlıklar, yaratıcılar, karakterler, ilişkiler) |
| `FandomMediaIds.kt` | Kitsugi offset'li kimliklerini çözme, Wikidata özellikleri |
| `FandomOverrides.kt` | Elle doğrulanmış eşleme tablosu |
| `FandomCharacterQuery.kt` | `KitsugiCharacterDetail` → sorgu |
| `FandomHttp.kt` | Ortak HTTP (12 sn, en fazla 4 eşzamanlı istek, dürüst User-Agent, hata sayacı) |
| `WikidataClient.kt` | SPARQL birincil, Action API yedeği |
| `FandomApi.kt` | MediaWiki çağrıları + topluluk araması |
| `FandomAniListSource.kt` | AniList doğrulama verisi (ortak, hız sınırlı AniList kapısı) |
| `FandomWikiResolver.kt` | Yapım → wiki zinciri (tablo → Wikidata → ilişkiler → isim) |
| `FandomCharacterResolver.kt` | Karakter → sayfa (P6262 → wiki içi varyantlar → wiki içi arama) |
| `FandomRepository.kt` | Arayüzün tek giriş noktası; önbellek, onay/ret, temizlik |
| `FandomWikiPrefs.kt` | Tercihler + kullanıcı kararları (`FandomUserChoices`) |

`app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/`

| Dosya | Görev |
|---|---|
| `FandomCharacterWikiViewModel.kt` | Wiki sekmesinin durumu, bölüm yükleme, çeviri, onay/ret |
| `CharacterWikiTabContent.kt` | Wiki sekmesi arayüzü |
| `FandomWikiSettingsSection.kt` | Telefon ayarlar bölümü |

Diğer:
- `app/src/main/assets/fandom/fandom_overrides.json` — Chainsaw Man, Spy×Family, Solo Leveling (siteinfo ile doğrulandı)
- `app/src/test/java/com/kitsugi/animelist/data/fandom/` — 13 JVM test dosyası

### Değişen dosyalar

- `ui/screens/detail/CharacterDetailPage.kt`
  - Sekmeler sıra numarası yerine anahtarla seçiliyor (`CharacterTabKey`).
  - Wiki sekmesi sayfa bulununca en sona ekleniyor; kaldırılırsa geçersiz sayfada kalınmıyor.
- `ui/components/KitsugiPreferencesSettingsDialog.kt` — "Liste ve puan" sekmesine Fandom Wiki bölümü (tek satır).
- `ui/tv/settings/TvSettingsScreen.kt` — TV "Görünüm" sekmesine Fandom Wiki anahtarları.
- `ui/screens/settings/SettingsSearch.kt` — ayarlar aramasına "Fandom Wiki" girişi.
- `data/local/PersistentDetailCacheDao.kt` — `deleteByPrefix` sorgusu. Room şeması değişmez (v29), göç gerekmez.
- `res/values/strings.xml` + `res/values-en/strings.xml` — 53 yeni metin, iki dilde birebir aynı anahtarlar.
- `.github/workflows/android-build.yml` — CI'ya `com.kitsugi.animelist.data.fandom.*` testleri eklendi.

### Plandan bilinçli sapmalar

- **Ayarlar** `AppSettings`/`SettingsDataStore` zincirine eklenmedi; bağımsız `FandomWikiPrefs` kullanıldı. Ayarlar ekranındaki uzun parametre zincirine (7+ dosya) dokunmamak ve başka dallarla çakışma riskini azaltmak için.
- **İlişki yürüyüşü ve topluluk araması** ayrı dosya yerine `FandomWikiResolver` içinde.
- **Sayfa ayrıştırma** dört küçük dosyaya bölündü (yukarıdaki tablo).
- **Onay/ret akışı** plandakinden ayrıntılı:
  - "Bu eşleşme doğru mu? Evet / Hayır" kartı, kesinleşmemiş (muhtemel) her eşleşmede çıkar.
  - **Evet:** Sayfa kesinleşir. Wiki isimle bulunduysa wiki de o yapım için onaylanır ve aynı yapımın diğer karakterlerinde kullanılır.
  - **Hayır / Yanlışsa bildir:** Sayfa o karakter için reddedilir.
    - Wiki isimle bulunup kesinleşmemişse wiki de o yapım için reddedilir; isim yolu sıradaki adayı dener.
    - Kimlikle bulunan wiki asla reddedilmez.
- **Kanıt yoksa isim yolu sonuç vermez** (AniList'e ulaşılamazsa veya karakter listesi boşsa).

### Cihaz test rehberi

1. **Satoru Gojo** (Jujutsu Kaisen) karakter sayfası:
   - En sonda "Wiki" sekmesi belirir.
   - Bilgi kutusu görünür; "Status" alanı gizli, dokununca açılır.
   - "Battles & Events" bölümü uyarıyla açılır.
   - "Wiki'de aç" bağlantısı çalışır.
2. **Sung Jinwoo** (Solo Leveling): wiki eşleme tablosundan gelir (`solo-leveling`), oyun wiki'si (ARISE) seçilmez.
3. **Monster (2004) karakterleri** (Kenzo Tenma, Johan Liebert):
   - Kimlik yolu yoksa isim yolu `obluda.fandom.com`'u bulmalı; genel `monster.fandom.com` asla seçilmemeli.
   - Yerel ad teyidi yoksa "Bu eşleşme doğru mu?" kartı çıkar. "Evet" ile kart kaybolur ve diğer Monster karakterlerinde de kesin sayılır.
4. **Yanlışsa bildir:** Sekme kaybolur ya da sıradaki uygun sayfa gelir; aynı sayfa bir daha gösterilmez.
5. **Ayarlar → Tercihler → Liste ve puan → Fandom Wiki:**
   - "Wiki sekmesi" kapalıyken sekme yok, istek yok.
   - "İsimle eşleştirme" kapalıyken yalnızca kimlik yolu çalışır (Monster karakterlerinde sekme görünmemeli).
   - "Türkçe wiki önceliği": sayfanın Türkçe wiki'de dolu karşılığı varsa o gösterilir (ör. One Piece karakterleri).
   - "Wiki önbelleğini temizle": önbellek ve onay/ret kararları silinir.
6. **TV:**
   - Karakter sayfasında Wiki sekmesi ve bölümler kumandayla açılır.
   - TV Ayarlar → Görünüm → Fandom Wiki anahtarları çalışır.
7. **Önbellek:** Aynı karakterin ikinci açılışı ağ beklemeden gelir.
8. **Birim testleri:** `./gradlew :app:testFossDebugUnitTest --tests 'com.kitsugi.animelist.data.fandom.*'`

### Bilinen sınırlar ve sonraki adımlar

- TMDB kaynaklı (gerçek kişi rolleri) karakter sayfalarında Wiki sekmesi yok.
- `series_glossary.json` içindeki `anilist:` eşleşmeleri tabloya eklenmedi; içinde gürültü olduğu için tek tek gözden geçirilmesi gerekiyor.
- Bölüm metnindeki tablolar (yetenek/istatistik tabloları) Faz 1'de gösterilmez.
- Faz 2: anime Wiki sekmesi + TV satırı + çeviride isim koruması (bkz. "Faz 2").
