# Fandom wiki keşfi — Wikidata tabanlı altyapı araştırması (2026-10-13)

Amaç: Bir yapımın (anime/manga) Fandom wiki'sini **Wikidata üzerinden** güvenilir biçimde bulmak;
tek tek elle eşleme tablosuna bağımlılığı azaltmak. Bu rapor ölçümlere dayanır (canlı Wikidata
Action API / SPARQL ve Fandom `api.php` / topluluk araması yanıtları). Android derlemesi ve cihaz testi
bu ortamda yapılamadı.

## 1. Sorun: Gintama neden bulunamadı

| Adım | Durum (ölçüm) |
|---|---|
| Wikidata'da Gintama anime öğesi (Q709716) | AniList P8729 ve MAL P4086=918 var |
| Aynı öğede Fandom wiki ID (P4073) | **Yok** (`wbgetclaims P4073` → boş) |
| `haswbstatement:P4073=gintama` | 0 sonuç (tüm Wikidata'da yok) |
| Üst kayıt yürüyüşü (P179/P144/P8345) | Manga Q764135'te de P4073 yok |
| Fandom topluluk araması "Gintama" | `gintama.fandom.com`, en, hub=anime, ~1000 sayfa |
| Wiki canlı mı? (`siteinfo`) | Evet: 1.871 makale, MediaWiki 1.43.9 |
| Karakter sayfaları (infobox, Japonca ad) | Evet (Shinpachi örneğinde doğrulandı) |

Sonuç: Wikidata'nın medya öğesinde Fandom bağlantısı hiç yok. Uygulama isim yoluna düşüyor. İsim
yolunun AniList doğrulama verisine (karakter parmak izi) bağımlı olması ve AniList hatasının
"bulunamadı" olarak 3 gün önbelleğe yazılması, bu durumu kalıcı hale getiriyordu (önceki düzeltmede
giderildi).

Wikidata'nın kendi özelliği P4073 için "expected completeness: always incomplete" notu düşüyor
(wikidata.org/wiki/Property:P4073). Yani tek başına "P4073'ten oku" yaklaşımı yapısal olarak eksik.

## 2. Ölçülen kapsam

- AniList anime kimliği (P8729) olan öğe: **7.087**
- Bunlardan P4073 doğrudan tanımlı: **45** (%0,6). Plan belgesindeki 45 rakamıyla tutarlı.
- Karakter kayıtlarında Fandom sayfa kimliği (P6262) yaygın: Gintoki Q2337951 →
  `gintama:Sakata_Gintoki` (ayrıca `es.doblaje:...` gibi çapraz wikiler de var; bunlar zaten engelleniyor).

## 3. Yeni Wikidata yolu: "eserde yer alan karakterlerin P6262 oyu"

Medya öğesinden karakterlere **ters P1441 ("present in work")** bağlantısı ile ulaşılabiliyor:

| Yapım (Wikidata QID) | `haswbstatement:P1441=<QID>` sonucu | Karakterlerin P6262'si |
|---|---|---|
| Gintama (Q709716) | 7 (Gintoki, Shinpachi, Kagura, Hijikata…) | `gintama:Gintoki…`, `gintama:Shimura_Shinpachi`, `gintama:Kagura` ✔ |
| Chainsaw Man (Q104211858) | 8 (Denji, Makima, Power…) | (karakter sayfaları var; wiki kimliği oy ile doğrulanacak) |
| Date A Live (Q86525119) | 36 (Tohka, Shido…) | (oy ile doğrulanacak) |
| Solo Leveling (Q125221706) | **0** | — |

Öneri (karar kuralı): Ana karakterlerin P6262 değerlerinden wiki kimliklerini çıkar, **kök dil (en)**
ve engellenmemiş wikilere oy ver; en çok oyu alan wiki için `siteinfo` ile doğrula. Çoğunluk eşiği
ve belirsizlik marjı uygulanmalı.

Sınırlar: Solo Leveling gibi P1441 bağlantısı olmayan yapımlar bu yolla bulunamaz; bunlar hâlâ
Wikidata'da karakter kayıtları eksik olabilir. Bu durumda Fandom topluluk araması + AniList/MAL dış
bağlantı doğrulaması (`exturlusage`, mevcut kodda var) devreye girer.

## 4. Açık kaynak ekosistemi (kod tabanlı taraması)

- **anime-and-manga/lists** (github.com/anime-and-manga/lists): AniList ↔ MAL ↔ AniDB ID eşlemeleri JSON.
  Fandom içermiyor, ama kimlik çapraz eşlemesi için sağlam bir kaynak.
- **PlexAniBridge-Mappings** (github.com/eliasbenb/PlexAniBridge-Mappings): AniList anahtarlı eşlemeler
  (AniDB, MAL, TVDB, IMDb, TMDB), Wikidata SPARQL'den de beslenir. Fandom alanı yok.
- **BreezeWiki** (gitdab.com/cadence/breezewiki): Fandom için alternatif ön yüz; wiki **keşfi** yapmaz,
  wiki adını zaten bilmek gerekir. Kullanılabilir bir keşif kaynağı değil.
- **fandom-cli** (Go, pkg.go.dev/github.com/tamnd/fandom-cli): Fandom'un f2 JSON akışı + arama sayfası
  kazıması ile wiki arar; yani aynı topluluk aramasına benzer bir yaklaşım. Kimlik tabanlı değil.

Sonuç: Açık kaynakta "AniList/MAL → Fandom wiki" için hazır, bakımlı bir veri seti bulunamadı. Bu
nedenle en güvenilir yol Wikidata'nın P1441/P6262 ilişkisi + doğrulama olmalı; mapping setleri yalnızca
kimlik çapraz eşlemesi (AniList↔MAL) için yardımcı olabilir.

## 5. Önerilen mimari (sıralı)

1. **Wikidata P4073 (medya + üst kayıt)** — mevcut yol, korunur.
2. **Wikidata karakter oyu** — medya QID'inden P1441 ters sorgu → karakterlerin P6262 → wiki oyları.
   (Yeni; AniList'e bağımlı değil. Aynı yanıt zincirine `WikidataClient` içinde eklenebilir.)
3. **Doğrulama** — aday wiki için `siteinfo` (canlı mı, makale sayısı), karakter sayfalarının varlığı,
   ve `exturlusage` ile AniList/MAL bağlantısı (mevcut kod).
4. **İsim yolu (son çare)** — mevcut akış; AniList hatası artık geçici sayılıyor.
5. **Elle eşleme tablosu** — yalnızca yukarıdakilerin bulamadığı, kullanıcı tarafından doğrulanmış
   istisnalar için.

## 6. Açık sorular ve sonraki adımlar

- Karakter oyu için sorgu başına kaç karakter? (Öneri: en fazla 10 ana karakter; P1441 sonuçları
  `srlimit=50` ile sınırlı, sayfalama gerekebilir.)
- Solo Leveling gibi P1441'i olmayan yapımlar için Wikidata'da karakter bağlantısı eklenmesi
  (Wikidata'ya katkı) düşünülebilir; bu uygulamanın dışında bir iş.
- Uygulama tarafında uygulanması: yeni `WikidataClient.characterWikiVotes(qid)` + birim testleri;
  Android build (`arena/**` dalındaki GitHub Actions) ile doğrulanmalı.

Bu rapor yalnızca araştırmadır; uygulama değişikliği bu dosyayı izleyen bir sonraki adımdır.

## 7. Tüm kaynaklar (AniList, MAL, Kitsu, Shikimori, Bangumi, TMDB, Simkl …)

### 7.1 Kodda bugünkü durum
- `CrossProviderIdentityResolver.resolve(context, entry)` (data/auth/CrossProviderIdentity.kt) zaten
  malId, aniListId, kitsuId, shikimoriId, bangumiSubjectId, tmdbId, simklId alanlarını çözüyor
  (önbellekli, ağ kapılı).
- Fandom tarafı (`FandomMediaQueries` / `FandomMediaIds.fromStableId`) bu çözümü kullanmıyor:
  - Simkl ofseti (`SIMKL_OFFSET = 150_000_000`) hiç tanınmıyor → Simkl kaynaklı kayıtlar kimlikle aranamıyor,
    yalnızca başlıkla (son çare) deneniyor.
  - `FandomSourceIds` içinde simkl alanı yok.
  - `wikidataLookups` yalnızca AniList/MAL/Kitsu/Bangumi/TMDB özelliklerine bakıyor.

### 7.2 Wikidata özellikleri (doğrulama durumu)
| Kaynak | Wikidata özelliği | Durum |
|---|---|---|
| AniList anime | P8729 | doğrulandı (7.087 öğe) |
| MyAnimeList anime | P4086 | doğrulandı (Gintama Q709716 = 918) |
| Kitsu anime | P11495 | doğrulandı (wikidata.org/wiki/Property:P11495) |
| Bangumi subject | P5732 | doğrulandı (wikidata.org/wiki/Property:P5732) |
| TMDB TV / film | P4983 / P4947 | kodda kullanılıyor (kapsam ölçülmedi) |
| IMDb | P345 | Gintama Q709716'da mevcut (tt0988818) — köprü olarak kullanılabilir |
| Shikimori | **doğrulanamadı** | P11419 yanlış çıktı (CASA oyun kimliği). Shikimori anime kimliği çoğu zaman MAL kimliğiyle aynıdır (shikimori.one/animes/z{malId}); MAL üzerinden köprülenebilir. |
| Simkl | **Wikidata özelliği bulunamadı** | Simkl → TMDB/IMDb/MAL köprüsü (CrossProviderIdentity) ile çözülmeli. |

### 7.3 Önerilen yaklaşım
1. Yapımın **tüm bilinen kimliklerini** tek seferde topla (CrossProviderIdentityResolver; Simkl dahil).
2. Her Wikidata özelliği için (P8729, P4086, P11495, P5732, P4983/P4947, P345 IMDb) ayrı arama yap;
   ilk eşleşen medya QID'ini al. Çoklu eşleşmede en çok karakter oyu alan kayıt.
3. Medya QID'inden 1. bölümdeki karakter-oy yolu (P1441 ters bağlantı → P6262) ile wiki çıkar.
4. Kimlik hiç yoksa (örn. yalnızca Simkl) → TMDB/IMDb üzerinden köprüle; o da yoksa mevcut isim yolu.
5. Doğrulama: `siteinfo`, karakter sayfaları, `exturlusage` (AniList/MAL bağlantısı) — mevcut kod.

### 7.4 Uygulama planı (henüz kodlanmadı)
- `FandomSourceIds`'e `simklId` ve `imdbId` ekle; `FandomMediaIds.fromStableId`'ye Simkl ofsetini ekle.
- `FandomMediaQueries.fromMedia` çağrı noktalarında (MediaEntryDetailPage / ApiResultDetailPage)
  `CrossProviderIdentityResolver` sonuçlarını birleştir.
- `wikidataLookups`'a IMDb (P345) ve Shikimori→MAL köprüsünü ekle.
- Birim testleri: her kaynak için kimlik → (özellik, değer) üretimi.
- Derleme ve gerçek cihaz testi `arena/**` dalındaki Actions iş akışıyla yapılmalı.
