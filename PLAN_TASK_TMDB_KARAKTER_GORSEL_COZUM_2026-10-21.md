# PLAN & TASK — TMDB Anime/Animasyon Detay Sayfasında Eksik Karakter Görselleri (2026-10-21)

## 1. Sorun Özeti ve Gözlem

- **Belirti (`image-1.png` — *your name. / 君の名は。* TMDB Film & `image-2.png` — *Doraemon* TMDB Kaydı):**
  - TMDB kaynaklı anime/animasyon yapımlarının **Detay → Karakterler** sekmesinde kurgusal karakter kutuları yalnızca 2 harflik yer tutucu baş harfleri (`TA` Taki Tachibana, `MI` Mitsuha Miyamizu, `KA` Katsuhiko Teshigawara, `SA` Sayaka Natori; `NO` Nobita Nobi, `DO` Doraemon, `SH` Shizuka Minamoto, `SU` Suneo Honekawa, `TA` Takeshi Gouda, `DO` Dorami) gösterirken sağ taraftaki seslendirmen (seiyuu) fotoğrafları normal biçimde yükleniyordu.
  - Buna karşın listedeki herhangi bir karaktere tıklayıp **Karakter Detay** sayfasına girildiğinde kurgusal karakterin görseli sorunsuz yüklenip görüntüleniyordu.

---

## 2. Tespit Edilen 10 Kök Neden Zinciri

1. **ARM v2 Uç Nokta Hatası (`HTTP 400`) + Önbellek Zehirlenmesi (`KitsugiIdResolver.kt`, `KitsugiEpisodeRatingsRepository.kt`):**
   - `arm.haglund.dev` OpenAPI şemasına göre `GET /api/v2/ids?source=...` yalnızca `["anilist", "anidb", "animecountdown", "animenewsnetwork", "anisearch", "kitsu", "livechart", "myanimelist", "anime-planet"]` kaynaklarını kabul eder; `source=themoviedb`, `thetvdb`, `imdb`, `simkl` sorgularına **%100 `HTTP 400 Bad Request`** döner.
   - Doğru uç noktalar `GET /api/v2/themoviedb?id=<int>`, `GET /api/v2/thetvdb?id=<int>`, `GET /api/v2/imdb?id=<tt...>` (JSON `Relation[]` dizisi döner) ve Simkl için `POST /api/v2/ids` (`{"simkl": <int>}`) şeklindedir.
   - Ayrıca `themoviedb` sütunu hem film hem dizi kimliklerini tek tamsayı alanında tuttuğundan `media == "MOVIE"` ile `TV/ONA/OVA/SPECIAL` satırları ayrıştırılmazsa ID çakışması yaşanıyordu; başarısız sorgu sonucunda da `resolvedIdsCache` içine `ResolvedIds(malId = null, tmdbId = ...)` kalıcı olarak yazılıp önbellek zehirleniyordu.

2. **Çalışmayan `resolveShikimoriIdFromMal` (`KitsugiIdResolver.kt`):**
   - ARM yanıtında `"shikimori"` alanı bulunmadığı için `json.optInt("shikimori", -1)` her zaman `-1` dönüyor, böylece Shikimori karakter listesi yedeği uygulama genelinde tamamen devre dışı kalıyordu (oysa Shikimori anime/manga ID uzayı MAL ID ile birebir aynıdır ve `/api/animes/{malId}/roles` tek istekte tüm karakter portrelerini döner).

3. **Soğuk `DetailCache` Yarışı ve Erken `isRealMedia` Kararı (`KitsugiCharacterClient.kt`, `TmdbMediaDetailClient.kt`, `ApiResultDetailViewModel.kt`, `MediaEntryDetailViewModel.kt`):**
   - `TmdbCreditsClient.fetchCredits` çoğu zaman `TmdbMediaDetailClient.fetchMediaDetail` tamamlanmadan bittiğinde `DetailCache.getMediaDetail("tmdb", effectiveTmdbId)` henüz `null` oluyordu.
   - `isRealMedia` kontrolü çapraz kimlik çözümünden **önce** çalıştığı için yapım canlı çekim sanılıyor, görselsiz karakter listesi `DetailCache.mediaCharacters` içine yazılıyordu.
   - Ardından detay yüklendiğinde `loadTab(2, ...)` içindeki `currentSuccess.data.isEmpty()` kontrolü 15 görselsiz karakter kaydı boş olmadığı için yeniden zenginleştirmeyi tamamen atlıyordu.

4. **TMDB Dizi / `(voice)` Etiketsiz Kayıtlarda Seslendirmen Fotoğrafının Karakter Görseli Sanılması (`TmdbCreditsClient.kt`, `KitsugiCharacterClient.kt`):**
   - TMDB dizi kayıtlarında `(voice)` ibaresi bulunmadığında `char.imageUrl = actorImageUrl` (`== voiceActors[0].imageUrl`) atanıyor, tüm zenginleştirme adımları `!char.imageUrl.isNullOrBlank()` kontrolü yüzünden bu karakterleri "görseli var" sanarak atlıyordu.

5. **AniList Kesintisi (`HTTP 403/503`) + `realMalId == null` Kapısı (`KitsugiCharacterClient.kt`):**
   - AniList erişilemediğinde ve ARM `HTTP 400` yüzünden `realMalId == null` kaldığında Jikan, Shikimori, Kitsu, Bangumi, Simkl, Wikidata ve Fandom zincirlerinin tamamı devre dışı kalıyordu.

6. **Uzun Soluklu Seri Filmlerinde Yanlış Reddetme — *Doraemon* (`searchAniListCharacterByName`):**
   - Karakter araması yalnızca `media(perPage: 8, sort: [POPULARITY_DESC])` çekip hedef filmin `idMal` değeri ilk 8 popüler yapım arasında yoksa `continue` ile karakteri tamamen çöpe atıyordu; bu da 40+ filmlik *Doraemon* serisinde tüm ana kadronun elenmesine yol açıyordu.

7. **Referans Birleştirmede CJK ve Soyadı Önce İsimlerin Atlanması (`mergeImagesFromReferenceCharacters`):**
   - Yalnızca `ref.name` kontrol ediliyor, `englishName`, `romanizedName`, `nativeName` alanları ve CJK karakterleri yok sayılıyordu.

8. **Aynı Seslendirmenin Birden Fazla Rolünde `personId` Çakışması:**
   - TMDB kadrosunda `char.id` seslendirmenin `personId` değeri olduğundan `HashMap<Int, ...>` kullanımı aynı oyuncunun seslendirdiği ikinci karakterin ezilmesine neden oluyordu.

9. **`KitsugiPersonImageAggregator.resolveMalIdByName` İçindeki `exactMatches.size == 1` Kısıtı:**
   - `"Doraemon"` gibi birden fazla tam eşleşme döndüren popüler karakterlerde `exactMatches.size == 1` koşulu `null` döndürerek Jikan karakter görselini düşürüyordu.

10. **Karakter Detay Sayfası ile Sekme Önbelleği Arasında Geri Yayılım Eksikliği (`DetailCache.kt`, `CharacterDetailViewModel.kt`):**
    - Karakter detay sayfasında bulunan portre `DetailCache.mediaCharacters` ve açık sekme `StateFlow` akışlarına geri yazılmıyordu.

---

## 3. Uygulanan Çözüm ve Değişen Dosyalar

1. **`app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiIdResolver.kt`**
   - ARM v2 uç noktaları (`/api/v2/themoviedb?id=`, `/api/v2/thetvdb?id=`, `/api/v2/imdb?id=`, Simkl için `POST /api/v2/ids`) düzeltildi.
   - `parseArmRelations(...)` ile Film (`MOVIE`) ve Dizi (`TV/ONA/OVA/SPECIAL`) çakışma koruması eklendi.
   - Başarısız sorguların `resolvedIdsCache` önbelleğini zehirlemesi engellendi (`hasCrossMapping`).
   - `resolveShikimoriIdFromMal` düzeltildi; Wikidata (`WikidataClient`) üzerinden MAL/AniList/Kitsu/Bangumi/IMDb köprüsü ve `resolveAllTmdbAnimeIds(...)` eklendi.

2. **`app/src/main/java/com/kitsugi/animelist/data/fandom/WikidataParsers.kt`**
   - `WikidataEntityClaims` ve `parseEntities` içine `P4086` (`malAnimeId`), `P8729` (`aniListAnimeId`), `P11495` (`kitsuAnimeId`) ve `P12095`/`P5646` (`bangumiSubjectId`) desteği eklendi.

3. **`app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiEpisodeRatingsRepository.kt`**
   - `queryMediaKindFromArm` içindeki bozuk `/api/v2/ids?source=themoviedb&id=$tmdbId` çağrısı `/api/v2/themoviedb?id=$tmdbId` olarak düzeltildi.

4. **`app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiModels.kt` & `TmdbCreditsClient.kt` & `TmdbMediaDetailClient.kt`**
   - `KitsugiCharacter` modeline `isVoiceRole: Boolean = false` eklendi.
   - `TmdbCreditsClient` içinde `(voice)`, `(uncredited voice)`, `(seslendirme)`, `(ses)` etiketleri tanınıp temizlendi ve `isVoiceRole` aktarıldı.
   - `TmdbMediaDetailClient` içinde ek HTTP isteği yapmadan mevcut JSON'dan `countryOfOrigin` ve `originalLanguage` alanları çıkarıldı.

5. **`app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiPersonImageAggregator.kt`**
   - Tek kelimeli/popüler karakterlerdeki `exactMatches.size == 1` düşürme hatasını aşan paralel `firstPortrait(...)` + tek istekli `jikanDirectCharacterPortrait(...)` yarış mekanizması eklendi.

6. **`app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiCharacterClient.kt`**
   - Bağımlılıkları enjekte edilebilir çok-katmanlı `enrichTmdbCastCharacters(...)` boru hattı kuruldu:
     - ARM + Wikidata + Simkl çapraz ID çözümü (soğuk önbellekte bile çalışır),
     - `isActorDuplicateImage` ile seslendirmen kopya fotoğrafının temizlenmesi,
     - AniList + Jikan başlık/ID + hızlı Shikimori `/roles` + Kitsu + Bangumi paralel referans birleştirme,
     - Doğrulanmış medya wikisi üzerinden toplu Fandom infobox/thumbnail yedek katmanı (`fetchFandomBatchPortraits`),
     - İndeks tabanlı (`personId` çakışmasız) çoklu kaynak karakter portre araması,
     - Seri filmlerinde ana kadroyu elemeyen `parseBestAniListCharacterSearchHit(...)`.

7. **`app/src/main/java/com/kitsugi/animelist/data/remote/DetailCache.kt`, `CharacterDetailViewModel.kt`, `ApiResultDetailViewModel.kt`, `MediaEntryDetailViewModel.kt`**
   - `DetailCache.propagateCharacterPortrait(...)` ve `characterPortraitVersion: StateFlow<Long>` ile bulunan portrelerin anında sekme listelerine geri yayılması sağlandı.
   - Medya detayı sonradan yüklendiğinde eksik portreli karakter listesinin iskelet titremesi olmadan arka planda otomatik tamamlanması sağlandı.

8. **Birim Testleri**
   - `app/src/test/java/com/kitsugi/animelist/data/remote/TmdbAnimeCharacterPortraitEnrichmentTest.kt`
   - `app/src/test/java/com/kitsugi/animelist/data/remote/TmdbMediaDetailClientTest.kt`
