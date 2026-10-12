# PLAN/TASK — Fandom Wiki entegrasyonu (yalnızca kimlikle eşleştirme)

Tarih: 2026-10-12
Durum: Plan — onay bekliyor. Henüz kod yazılmadı.

## Amaç

Detay sayfalarında yapımın ve karakterin Fandom wiki içeriğini (bilgi kutusu, bölümler, evren terimleri) Kitsugi içinde göstermek.
Eşleştirme **isimle değil, kimlikle** yapılır. Emin olunmayan hiçbir şey gösterilmez.

## Temel kararlar

1. **Kimlik zinciri.** Fandom'da AniList/MAL/TMDB kimliği için standart alan yok. Köprü Wikidata: aynı kayıtta kaynak kimlikleri ile Fandom wiki kimliği (P4073) ve Fandom sayfa kimliği (P6262) birlikte tutuluyor.
2. **Katı mod (varsayılan).** Kimlik zinciri wiki bulamazsa Wiki sekmesi hiç görünmez. İsimle arama, adres (slug) tahmini, yapay zekâ ve bulanık puanlama (`verify`) **kullanılmaz**.
3. **Kendi eşleme tablomuz.** Wikidata'da bağlantısı eksik popüler yapımlar için küçük, elle doğrulanmış tablo: `assets/fandom/fandom_overrides.json`.
   - Anahtar: AniList/MAL kimliği **veya** Wikidata QID'si. Seri/franchise QID'si verilirse tüm sezonları kapsar.
   - İleride GitHub'dan güncellenebilir.
4. **Kotlin'e port, yeni bağımlılık yok.** OkHttp (`KitsugiHttpClient`), Jsoup, org.json ve Room zaten var. Python gömülmez.
5. **Tembel yükleme.**
   - Sayfa açılınca yalnızca "wiki var mı?" sorulur (küçük Wikidata istekleri).
   - İçerik, sekme açılınca çekilir.
   - Arka planda toplu tarama yapılmaz.

## Wiki nerede görünecek?

| Ekran | Dosya | Eklenecek | Faz |
|---|---|---|---|
| Karakter detayı (telefon + TV aynı sayfa) | `ui/screens/detail/CharacterDetailPage.kt` | Sona **Wiki** sekmesi: Hakkında · Yapımlar · Seslendirenler · **Wiki** | 1 |
| Ayarlar | `ui/screens/settings/SettingsScreen.kt` | Fandom Wiki: aç/kapat · spoiler koruması · Türkçe wiki önceliği | 1 |
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
- Wiki sekmesi ancak wiki kesinleşince belirdiği için sona eklenmesi diğer sekmeleri oynatmaz.

## Veri akışı

```
Detay sayfası açılır
  → FandomRepository.resolve…()   (arka planda; önbellekte varsa 0 istek)
      eşleme tablosu → Wikidata kaydı → P4073 / P6262
  → wiki bulunduysa Wiki sekmesi belirir; bulunamadıysa hiçbir şey değişmez
Kullanıcı Wiki sekmesini açar
  → yalnızca giriş bölümü + bilgi kutusu çekilir (action=parse&section=0)
  → diğer bölümler kullanıcı açtıkça yüklenir (section=N)
  → İngilizce metin → mevcut çeviri düğmesi (TranslationManager)
```

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
2. **Wikidata kaydını bul.** `KitsugiIdResolver.resolveIds(...)` bir `wikidataId` döndürdüyse o kullanılır. Döndürmediyse şu arama yapılır:
   `https://www.wikidata.org/w/api.php?action=query&list=search&srsearch=haswbstatement:P8729=<id>&format=json`
   - Anime için sıra: P8729 → P4086 → P11495 → P5732 → P4983/P4947.
   - Manga için sıra: P8731 → P4087 → P11494. Manga'da `resolveIds` ARM'yi atladığı için `wikidataId` gelmez.
3. **Wiki kimliğini oku.** `action=wbgetentities&ids=<QID>&props=claims` → P4073 değerleri.
4. **Üst kayıtlara çık.** P4073 yoksa en fazla 2 adım yukarı çıkılır:
   - P179 (dizinin parçası)
   - P144 (uyarlandığı eser)
   - P8345 (medya franchise'ı)
   - P361 (parçası olduğu)
   - P155/P156 (önceki/sonraki sezon)

   Üst kayıtlar `wbgetentities` ile toplu okunur (≤50). Bu adımda eşleme tablosunda QID anahtarı da aranır.
5. **Aday seç.** Saf fonksiyon olarak yazılır ve test edilir.
   - **Çapraz/genel wikiler elenir:** `hero`, `villains`, `isekai`, `cosplay`, `dubbing`, `*.doblaje`, `*-dubber`, `dubdb`, `netflix`, `tropedia`, `animanga`, `anime`, `manga`, `vsbattles`, … Liste, Python'daki `CROSSOVER_BLOCKLIST` ve Wikidata'da görülenlerden oluşur.
   - **Ana wiki öncelikli.** Dil öneki olmayan wiki seçilir; `tr.` sürümü ayrıca not edilir (Türkçe wiki özelliği için).
   - **Birden fazla ana aday varsa** makale sayısı büyük olan seçilir (`meta=siteinfo&siprop=statistics`). Örnekler: AoT için `attackontitan` / `shingekipedia`, MHA için `bokunoheroacademia` / `myheroacademia`.
6. **Önbelleğe al.** Sonuç, QID ve kaynak kimlik anahtarıyla saklanır: bulunduysa 30 gün, bulunamadıysa 1 gün.

**URL notu:**
- P4073 `tr.onepiece` → `https://onepiece.fandom.com/tr/api.php`
- `jujutsu-kaisen` → `https://jujutsu-kaisen.fandom.com/api.php`

### Karakter → wiki sayfası

1. AniList karakter kimliği (P11736) veya MAL karakter kimliği (P4085) ile `haswbstatement` araması → P6262 değerleri (ör. `jujutsu-kaisen:Satoru_Gojo`). `CharacterDetailViewModel` bu iki kimliği ve `nativeName`'i zaten hesaplıyor.
2. P6262 değeri, karakterin yapımının wiki'siyle aynı wiki'de olmalı. Çapraz wikiler elenir.
3. Wikidata'da kayıt yoksa iki koşul birlikte aranır:
   - Karakterin ana yapımının wiki'sinde (yukarıdaki zincirle bulunur) **birebir** başlık eşleşmesi: `action=query&titles=<ad>&redirects=1` (wiki'nin kendi yönlendirmeleri kullanılır).
   - Bilgi kutusundaki `Kanji` alanının AniList/MAL `nativeName` ile aynı olması (boşluk ve `・` temizlenerek).

   Kesin eşleşme yoksa sekme görünmez.
4. TMDB kaynaklı karakter sayfalarında Faz 1'de Wiki sekmesi yok.

## Sayfa ayrıştırma (Jsoup)

Örnek: `jujutsu-kaisen:Satoru_Gojo`, `action=parse&section=0` (2026-10-12).
- **Bilgi kutusu grupları:**
  - Biographical Information: Kanji, Rōmaji, Alias
  - Personal Description: Species, Birthday, Age, Gender, Height, Hair/Eye Color
  - Professional Status: Status, Relatives, Occupation, Affiliation, Grade
  - First Appearance: Manga/Anime Debut
  - Portrayal: Japanese/English Voice
- **Bölümler** (`prop=sections`): Appearance, Personality, Abilities and Powers (alt bölümlerle), Battles & Events, Trivia, References, Navigation.

Kurallar:
- **Bilgi kutusu yapısı:** `aside.portable-infobox` → `section.pi-group` (`h2.pi-header`) → `div.pi-data` (`h3.pi-data-label`, `div.pi-data-value`).
  - Görseller: `figure.pi-image img` (`data-src`/`src`).
  - Sekmeli görseller: `.pi-image-collection`.
- **Giriş metni:** `.mw-parser-output > p`, ilk başlığa kadar.
- **Temizlenecekler:** `sup.reference`, `ol.references`, `.mw-references-wrap`, `table` (bakım uyarıları), `.navbox`, `.notice`, `.mbox`, `.toc`, yorum düğümleri.
- **Gizlenecek bölümler:** References, Navigation, Gallery/Image Gallery, External Links, See Also ve diğer dillerdeki karşılıkları.
- **Spoiler koruması (varsayılan açık):** Aşağıdakiler bulanık gösterilir, dokununca açılır.
  - Bilgi kutusunda Status/Durum/Estado alanları
  - History, Synopsis, Plot, Story, Battles & Events, Geçmiş, Hikâye bölümleri
- **Saklanan veri:** Ham HTML değil, sadeleştirilmiş JSON (alanlar + düz metin). Böylece veritabanı şişmez.

## Fazlar ve görevler

### Faz 1 — Altyapı + karakter Wiki sekmesi

- [ ] `data/fandom/FandomModels.kt` — `FandomWikiRef`, `FandomInfobox`, `FandomSection`, `FandomCharacterPage`.
- [ ] `data/fandom/WikidataClient.kt` — `haswbstatement` araması ve `wbgetentities` claim okuma (org.json).
- [ ] `data/fandom/FandomCandidateSelector.kt` — çapraz wiki filtresi, dil ayrımı, makale sayısıyla seçim, URL üretimi (saf fonksiyonlar).
- [ ] `data/fandom/FandomWikiResolver.kt` — yapım kimlikleri → `FandomWikiRef` (eşleme tablosu → Wikidata → üst kayıtlar).
- [ ] `data/fandom/FandomCharacterResolver.kt` — karakter kimlikleri → sayfa (P6262; yoksa birebir başlık + Kanji teyidi).
- [ ] `data/fandom/FandomApi.kt` — MediaWiki çağrıları: `siteinfo`, `parse` (section 0 / N / sections), `query&titles&redirects`.
- [ ] `data/fandom/FandomPageParser.kt` — bilgi kutusu, giriş, bölüm metni, temizlik, spoiler işaretleri.
- [ ] `data/fandom/FandomRepository.kt` — UI'nin tek giriş noktası.
  - Kalıcı önbellek: `persistent_details_cache` (`fandom:` önekli anahtar).
  - Bellek önbelleği: `BoundedCache`.
  - Aynı isteği tekilleştirme ve negatif önbellek.
- [ ] `assets/fandom/fandom_overrides.json` — ilk tablo.
  - AniList 127230 → `chainsaw-man`, 140960 → `spy-x-family`, 151807 → `solo-leveling`. Üçü de 2026-10-12'de siteinfo ile doğrulandı.
  - `series_glossary.json` içindeki `anilist:` anahtarları gözden geçirilerek eklenecek.
- [ ] `ui/screens/detail/wiki/FandomCharacterWikiViewModel.kt` + `CharacterWikiTabContent.kt`:
  - Başlık ve bilgi kutusu kartı
  - Açılır bölümler
  - Galeri şeridi (mevcut galeri diyaloğuna bağlanır)
  - Çeviri düğmesi
  - Kaynak satırı
- [ ] `CharacterDetailPage.kt` — Wiki sekmesi hem telefon hem geniş/TV düzeninde. `when (page)` sekme anahtarına göre çalışır (TMDB'de sekme sayısı farklı).
- [ ] Ayarlar: `AppSettings` / `SettingsDataStore` / `SettingsScreen` / `SettingsSearch`. Üç anahtar da varsayılan açık: `fandomWikiEnabled`, `fandomSpoilerGuard`, `fandomPreferTurkish`.
- [ ] Metinler: `values/strings.xml` + `values-en/strings.xml`.
- [ ] Testler (aşağıda) + CI'ya `--tests 'com.kitsugi.animelist.data.fandom.*'` eklenmesi.

### Faz 2 — Anime Wiki sekmesi + TV satırı + Türkçe wiki + isim koruması

- [ ] `FandomGlossaryFetcher.kt` — `fetcher.py` portu:
  - Kategori grupları: karakter, organizasyon, yetenek, mekân, eşya, terim (çok dilli kategori adlarıyla)
  - `categorymembers` ile üye listesi
  - Yönlendirmelerden takma adlar
  - Oyuncu/ekip sayfalarını eleme
  - Toplu kısa özetler
- [ ] `MediaWikiTabContent.kt` — evren rehberi çipleri ve "wiki'de ara". Çipe dokununca alt kart açılır: kısa açıklama + "Tamamını oku".
- [ ] `MediaEntryDetailPage.kt` + `ApiResultDetailPage.kt` — Wiki sekmesi sona eklenir (indeks hesaplanır). Bilgi sekmesine kısayol kartı.
- [ ] `TvDetailScreen.kt` — Wiki satırı.
- [ ] Türkçe wiki: P4073'te `tr.` sürümü varsa öncelik verilir (ayara bağlı).
- [ ] `FandomTermMatcher.kt` (PATF portu) — `TranslationManager.maskMarkdown` yer tutucularıyla isim koruması + Türkçe ek (ünlü uyumu) düzeltici.
- [ ] "Bu wiki yanlış" bildirimi → yerel geçersiz kılma.

### Faz 3 — Bölüm / oynatıcı / manga

- [ ] "Önceki bölümde…" kartı. İzlenmemiş bölümler `progress`'e göre gizli.
- [ ] Oynatıcıda X-Ray (telefon + TV).
- [ ] "Mangaya buradan devam et".
- Not: Bölüm ve chapter sayfa adları wiki'den wiki'ye değişir. Bu özellikler yalnızca güvenilir eşleşmede gösterilir.

## Testler (JVM)

- `FandomPageParserTest` — portable infobox HTML örneğinden gruplar/alanlar/görsel çıkarımı; temizlik kuralları; spoiler işaretleri.
- `FandomCandidateSelectorTest` — çapraz wiki eleme, ana/dil sürümü ayrımı, makale sayısıyla seçim, `tr.` tespiti, URL üretimi (`tr.onepiece` → `onepiece.fandom.com/tr`).
- `WikidataClaimsParserTest` — `wbgetentities` JSON örneğinden P4073/P6262/üst kayıt çıkarımı.
- `FandomCharacterMatchTest` — Kanji normalizasyonu (`五条 悟` = `五条悟`), birebir başlık/yönlendirme kuralı.

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
  - Appearance / Personality / Abilities and Powers / Battles & Events / Trivia bölümleri listelenir; References / Navigation gizli.
  - Kaynak satırı ve link var.
- Kimlik zinciri veya birebir eşleşme olmayan karakterde Wiki sekmesi hiç görünmez, hata da gösterilmez.
- Wiki sekmesi açılmadan wiki içeriği indirilmez; yalnızca küçük Wikidata istekleri yapılır.
- İkinci açılış önbellekten, ağ isteği olmadan gelir.
- Ayar kapalıyken sekme de istek de yok.
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
