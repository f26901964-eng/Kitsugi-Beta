# Kitsugi-Beta — 9 Ekran Görüntesi / 4 Hata Düzeltme Raporu (2026-10-10)

Kullanıcı sıralaması korunmuştur: görsel numaraları `uploads/image-1.png` … `image-9.png`.

| Görsel | Şikâyet | Kök neden | Durum |
|---|---|---|---|
| 1 | Bangumi: canlı çekim "The Dark Knight" için animasyon karakter çizimi + "Seslendirici (Japonca)" | partly upstream (Bangumi) + etiket hatası (v0 yedek akışı) | etiket düzeltildi, sanat verisi için soru ↓ |
| 2–6 | Kitsu Keşfet "Tümünü Gör"de devam gelmiyor (Anime Filmleri, Sonbahar 2026, Yaklaşan, Yayındaki, Trend) | `kitsu.io/api/edge` uyumluluk katmanı `sort` ve `page[offset]` yok sayıyor | düzeltildi |
| 7–8 | Shikimori stüdyo detay sayfası "Stüdyo detayları yüklenemedi." | var olmayan uç: `GET /api/studios/{id}` → 404 | düzeltildi |
| 9 | Totoro galerisinde Popeye görselleri (TMDB) | film/dizi türü yanlış biliniyordu + TMDB kimliği doğrulanmıyordu | düzeltildi |

---

## 1) Kitsu keşfet sayfalaması (görsel 2–6)

**Kanıt (canlı API, bu oturumda doğrulandı):**

- `kitsu.io/api/edge/anime?filter[subtype]=movie&sort=-userCount&page[limit]=20&page[offset]=20`
  → listenin başı offset 0 ile **birebir aynı** (11614, 10028, 176, …), `meta.count=3940`.
- `kitsu.app/api/edge/anime?…page[offset]=20` → `13723`, `1517` — yani kitsu.io başlığının
  **ger devamı**; `links.next` = `offset=22`.
- `kitsu.app/api/edge/trending/anime?page[limit]=2&page[offset]=2` → `7442`, `11469`
  → trending ucu da `page[limit]/page[offset]` dinliyor.
- `kitsu.io/api/edge/anime?page[limit]=100` → `400 Limit exceeds maximum page size of 20.`
  → "büyük tek sayfa çekip kesme" çözümü imkânsız.

Aynı davranış mihon #1104 ("Kitsu.io domain changed to Kitsu.app") ve güncel üçüncü taraf
Kitsu entegrasyonlarının `base_url: https://kitsu.app/api/edge` kullanmasıyla örtüşüyor.

**Değişiklikler**

- `data/remote/KitsuApiHost.kt` (yeni): kanonik adres `kitsu.app`, yedek `kitsu.io`;
  `base`, `candidates()`, `degrade()`, `url(pathAndQuery, host)`. tek adres üreticisi;
  yapıştırılacak tam URL'yi kabul etmiyor (yeniden-yazım hatası olasılığını kaldırır).
- `data/remote/KitsuExploreClient.kt`:
  - `private const val BASE` kaldırıldı; 14 raf + arama uçları artık **yol+sorgu** veriyor.
  - `fetchList(url)` → `fetchPaged(pathAndQuery, mediaType)`: host denemesi, yedek host'a
    geçiş, başarıda `degrade()`.
  - **Sayfa bekçisi**: `offset > 0` isteği, aynı anahtardaki 1. sayfanın baş kaydıyla birebir
    aynı kaydı döndürürse o host atlanır; tüm hostlar aynısını verirse liste bitmiş sayılır.
    Bu, `page[offset]`'i yok sayan bir adreste sonsuz "aynı 20 kayıt" döngüsünü imkânsız kılar.
  - `KITSU_MAX_PAGE_LIMIT = 20` (sunucu tavanı) tek noktada tanımlı.
  - `trendingAnime(limit, offset)` eklendi — eskiden `?limit=N` ile **hiç sayfalaması yoktu**,
    "Trend Animeler" bu yüzden 20 kayıtta kilitliydi.
- `ui/screens/fullscreen/FullScreenMediaGridPage.kt`:
  - KITSU `TRENDING_ANIME` dalı `trendingAnime(20, offset = (np - 1) * 20)` oldu.
  - `finiteChart` (Kitsu trendini yapay olarak sonlu sayan) mantığı kaldırıldı; durma koşulu
    yalnızca "sayfa boş/kopya çıktı".
  - `hasMorePages` başlangıcı `session?.cachedHasMore ?: !isSimklPersonalList`.
- `data/remote/KitsuClient.kt`: `BASE` artık `KitsuApiHost.base` getter'ı → karakter
  sayfalaması (v2.4.205'teki tekilleştirme yaması) da devam üretebiliyor.
- Kalan okuma uçları da sekici adresi kullanıyor: `KitsugiCharacterClient` (karakter detayı),
  `KitsugiStaffClient` (personel), `KitsugiPersonImageAggregator`,
  `KitsugiEpisodeRatingsRepository.isKitsuMovie`.
- Kapsam dışı bilinçli: `data/auth/KitsuApiClient.kt` zaten `kitsu.app` adresinde (OAuth ve
  liste yazma akışları değişmedi); `MappingSyncWorker` içindeki `kitsu.io/anime/` metni web
  bağlantısı ayrıştırmasıdır.

## 2) Shikimori stüdyo detayı (görsel 7–8)

`shikimori.io/api/studios/12` → **HTTP 404** (Rusça HTML). Shikimori'de tek kayıtlık stüdyo ucu
yok; `/api/studios` tüm dizini döndürüyor ve `studios[].image` bir **dize** (`/system/studios/original/2.png?…`).

`KitsugiStudioClient.fetchShikimoriStudioDetail(studioId, expectedName)` artık:

1. `GET /api/animes?studio={id}&limit=50&order=aired_on` → "Yapımlar" listesi (poster, tür, yıl, puan).
2. `GET /api/animes/{ilkYapimId}` → `studios[]` satırından **kanonik ad + logo** (`image` dizesi
   `absoluteShikimoriImageUrl` ile mutlaklaştırılıyor).
3. Ad oradan da gelmezse detay sayfasındaki çipin adı kullanılır; hiçbir şey gelmezse hata durumu.
4. `about = null` — Shikimori stüdyo açıklaması vermiyor, boş metin uydurulmuyor.

Ek olarak `StudioDetailViewModel.loadStudio(..., imageUrl)` çipten gelen logoyu yedek olarak
alıyor ve galeri kaynağını URL'den ayırt ediyor (Shikimori/AniList/Bangumi/TMDB) — eskiden
her logo "Jikan" etiketiyle gösteriliyordu.

## 3) TMDB galeri kimliği (görsel 9)

İki katmanlı hata:

- **Tür:** Shikimori detayında `KitsugiMediaDetail.type` hiç set edilmiyordu; `kind = "movie"`
  olmasına kayıt listeden "Anime" olarak geliyor, galeri `tv/8390` istiyordu ve TMDB'de `tv`
  ile `movie` kimlik alanları ayrı olduğu için aynı sayı bambaşka yapıma (Popeye) ait olabiliyor.
  - `KitsugiShikimoriClient.fetchDetail` → `type = kindToMediaType(kind)` (manga hariç).
  - `ApiResultDetailPage.displayResult` → `type = detail.type ?: result.type`; böylece
    hero etiketi "FİLM" oluyor ve `isMovie` galeri/film akışlarını doğru türe çeviriyor.
- **Doğrulama:** `KitsugiEpisodeRatingsRepository.getTmdbGalleryItems` artık her aday tür için
  `GET /{type}/{id}?append_to_response=images` çağırıyor, **TMDB kaydının başlığını** beklenen
  başlıklarla (arama + detay: yerel/İngilizce/Japonca/romaji) karşılaştırıyor ve eşleşmeyen
  kaydın görsellerini galeriye hiç karıştırmıyor. Karşılaştırma `data/remote/MediaGalleryIdentity.kt`
  içindeki `TmdbArtworkIdentity` (normalizasyon + kapsama; taraf eksikse fail-open). Yıl
  kasıtlı olarak kullanılmıyor (festival/vizyon yılı farkı).

**Etiket cilası:** film kaydında hero "Film · 1 Bölüm" gösteriyordu; `KitsugiDetailHero`
artık format "Film" iken bölüm/cilt satırını göstermiyor.

## 4) Bangumi etiketi (görsel 1)

Karakter **sanatı** Bangumi'nin kendi veri tabanından geliyor (三次元 kayıtlarında topluluk
çizimleri kullanılabiliyor) — uygulamada yanlış bir eşleme bulunamadı; film, Bangumi'de doğru
kayıt olarak görünüyor. Dolayısıyla "animasyon karakter" kısmı kaynak veri tercihidir.

Bizim hatamız etiketteydi: v1/p1 akışı `casts[].relation` üzerinden dili doğru çözerken
(`castLanguage`: 2 = Oyuncu, 4 = Japonca dublaj …), **v0 yedek akışı her kişiyi**
`language = "Japonca"` sayıyordu; Morgan Freeman "Seslendirici (Japonca)" olarak görünüyordu.

- `KitsugiBangumiCreditsClient.parseVoiceActor` → `language = castLanguageHint(item)`:
  yanıtta gerçek bir `language`/`relation` ipucu varsa dil söylenir, yoksa **boş** bırakılır
  (sayısal kod etiket olarak kullanılmaz).
- `CharactersTab` boş dilde jenerik "Seslendirici" / "Voice Actor" etiketi gösterir
  (`detail_role_voice_actor_plain`, `values` + `values-en`).

Kullanıcı tercihi (bu oturumda soruldu): bloğu **gizlemeyin, kaynak notu ekleyin**. Uygulandı:
`CharactersTabContent` listesi Bangumi kaynaklıysa listenin üstünde `characters_bangumi_source_note`
("Karakterler ve roller doğrudan Bangumi kaydından gelir; bu liste topluluk katkısıdır. Canlı çekim
(三次元) yapımlarda karakter sanatı ve rol bilgisi animasyon uyarlamasından gelebilir.") gösteriliyor
(TR + EN karşılığı `values/strings.xml` / `values-en/strings.xml`).

---

## 5) Önceki görevin yeniden uygulanaması (FileObserver / izleme geçmişi)

Kullanıcı "yeniden uygula" dedi; ancak bu çalışma alanı bugün `main` (94a3c7f) üzerinden
**sıfırdan klonlanmış**. Kontrol edilenler:

- `git log`: bu dalın tek ebeveyni 94a3c7f; önceki görevin commit'i (`3b56b68`) ne bu
  kopyada ne uzak `arena/76b577e4-kitsugi-beta` ref'inde (push öncesinde ref 94a3c7f'teydi).
- Dosya araması (tüm disk): `FileChangeObserver.kt` ve `KitsugiPlaybackRepository.kt` bu
  depoda **hiç yok**; `grep -r "android.os.FileObserver|FileObserver("` → 0 sonuç.
- `Kitsugi-Plugins` repo'su (gitlink) sığ klonla tarandı: aynı dosyalar orada da yok
  (geçici klon silindi). Depoda `.gitmodules` bulunmadığı için `Kitsugi-Plugins/` boş bir dizin.
- Ek kanıt: 1. görseldeki "Karakterler ve Seslendirme Sanatçıları · Kaynak: Bangumi" başlığı da
  bu checkout'ta geçmiyor → kullanıcının yerel ağacı `main`'in ilerisinde.

Bu yüzden o düzeltme **burada tekrarlanamaz**; hedef dosyalar `main`'e henüz itilmedi.
İstenirse: ilgili dosyalar (veya dalları) itilsin/buraya eklensin, aynı düzeltme orada
tekrar yazılır. Bu rapordaki 1–4 maddeleri bundan etkilenmiyor.

## Doğrulama durumu ve sınırlar

- Bu oturumda **derleme yapılamadı**: sandbox'ta JDK/Android SDK yok (`java`, `kotlinc`,
  `ANDROID_HOME` yok). Değişiklikler dosya dosya okunarak; simge erişimleri (aynı paket içindeki
  `internal` nesneler, `BoundedCache` LRU, `KitsugiApiBase.executeGetRequest*` imzaları,
  `KitsugiMediaDetail.type` alanı, `JikanSearchResult` başlık alanları, `R.string` anahtarları)
  tek tek kontrol edilerek doğrulandı. Tüm düzelenen dosyalarda parantez/süslü parantez dengesi
  ve XML geçerliliği betiklendi.
- API davranışları bu oturumda canlı uçlardan okunarak kanıtlandı (yukarıdaki yanıtlar).
- Önerilen yerel doğrulama: `./gradlew assembleDebug` ve
  `./gradlew :app:testDebugUnitTest` (bu depoda taban çizgisi 7 önceden var olan test hatası
  içeriyor; onunla karşılaştırılmalı), ardından Android tarafında: Kitsu "Anime Filmleri"
  rafında 2–3 kez kaydırma, Shikimori kaynaklı Ghibli detayında stüdyo çipi, Totoro Resimler
  sekmesi.

## Dosyalar

`KitsuApiHost.kt` (yeni) · `KitsuExploreClient.kt` · `KitsuClient.kt` ·
`FullScreenMediaGridPage.kt` · `KitsugiStudioClient.kt` · `StudioDetailViewModel.kt` ·
`StudioDetailPage.kt` · `KitsugiShikimoriClient.kt` · `ApiResultDetailPage.kt` ·
`ApiResultDetailViewModel.kt` · `MediaEntryDetailViewModel.kt` ·
`KitsugiEpisodeRatingsRepository.kt` · `MediaGalleryIdentity.kt` · `KitsugiDetailHero.kt` ·
`KitsugiBangumiCreditsClient.kt` · `CharactersTab.kt` · `KitsugiCharacterClient.kt` ·
`KitsugiStaffClient.kt` · `KitsugiPersonImageAggregator.kt` · `AboutScreen.kt` ·
`values/strings.xml` + `values-en/strings.xml` · `CharactersTab.kt` (Bangumi kaynak notu)
