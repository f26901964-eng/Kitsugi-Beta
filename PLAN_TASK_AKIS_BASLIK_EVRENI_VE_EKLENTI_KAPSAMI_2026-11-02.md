# PLAN & TASK — Akış aramasında kaynak bağımlı başlık daralması + eklenti kapsamı

**Tarih:** 2026-11-02
**Dal:** `arena/19bffd86-kitsugi-beta`
**Taban:** `3e09f9d` (main)
**Durum:** kod tamamlandı, cihaz doğrulaması bekliyor

---

## 1. Problem (kullanıcı bildirimi)

1. Aynı anime **AniList** kaydından açıldığında Anizm / TrAnimeIzle / AnimeciX akış veriyor;
   **MAL** kaydından açıldığında bu üçü "akış bulunamadı" diyor.
2. Bir eklentinin kendi keşfet/arama akışından "İzle" denildiğinde sonuç yalnızca o eklentiden
   gelmeli; ancak bazen diğer kaynaklarla karışıyor.

## 2. Kök nedenler

| # | Kök neden | Kanıt |
|---|---|---|
| R1 | Akış ekranı arama varyantlarını detay nesnesinin başlık alanlarından kuruyor; **her kaynak istemcisi bu alanları farklı dolduruyor** (MAL'da `titleRomaji`/`titleNative` boş, Simkl'de `titleRomaji` sabit `null`, Shikimori `synonyms` okunmuyor). | `KitsugiMalDetailClient`, `KitsugiSimklDetailClient`, `KitsugiShikimoriClient` vs. `CsStreamRunner.effectiveAlts` |
| R2 | Eklentinin doğrudan bölüm URL'si boş dönerse ekran sessizce **tüm kaynakların ortak aramasına** düşüyordu. | eski `StreamViewModel.fetchStreams` → `_addonStates = emptyList()` + ortak arama |
| R3 | Aynı davranış oynatıcının sonraki bölüm akışında da vardı (`mediaType` hiç geçilmiyordu). | `KitsugiPlayerViewModel.fetchStreamsForEpisode` |

## 3. Görev listesi

- [x] **T1** `CanonicalTitleAliases` (yeni): kimliklerden (MAL ve/veya AniList) AniList GraphQL
      `title{romaji,english,native}` + `synonyms` ve Jikan v4 `titles[]` birleşimini üret.
      Önbellekli (400), tek uçuşlu (`Mutex`), 4 sn tavanlı, hata/timeout önbelleğe yazılmaz.
- [x] **T2** `CsStreamRunner`: `effectiveAlts` bu birleşimle zenginleşsin; varyant logu
      `mal/anilist/tmdb` kimliklerini de yazsın (teşhis).
- [x] **T3** `MediaDetailTitleHarmonizer` (yeni): `fetchPrimaryDetail` + `enrichDetail` **tek çıkış
      noktasında** tüm başlık varyantlarını `synonyms`'e topla, `titleNative` ↔ `titleJapanese`
      tamamla, normalize ederek tekilleştir, 12 ile sınırla.
- [x] **T4** `KitsugiMalDetailClient`: Jikan + resmî MAL v2 yolunda `titleRomaji`/`titleNative` doldur.
- [x] **T5** `KitsugiSimklDetailClient`: `titleRomaji = null` → `simklRomaji`.
- [x] **T6** `KitsugiShikimoriClient`: `synonyms` dizisini oku.
- [x] **T7** `StreamViewModel`: `runScopedPluginFetch` (doğrudan URL → aynı eklentinin kendi
      başlık araması), `scopedSourceName`, `searchAllSources()`, `startFetch(allSources)`.
- [x] **T8** `StreamSourceScope` (yeni): "tek eklenti mi, ortak arama mı" kuralı — tek yerde, test edilebilir.
- [x] **T9** `StreamScreenContent` + `KitsugiStreamScreen`: "Yalnızca ⚡ X eklentisinde arandı" şeridi
      ve "Tüm kaynaklarda ara" düğmesi (dikey + yatay düzen).
- [x] **T10** `KitsugiPlayerViewModel`: sonraki bölümde önce aynı eklenti (URL → başlık araması),
      `mediaType` geçir; yalnızca o eklenti veremezse ortak aramaya düş (logla).
- [x] **T11** TR/EN kaynak metinleri + `scripts/check_localization_resource_parity.py` doğrulaması.
- [x] **T12** Birim testleri: `CanonicalTitleAliasesTest` (3), `StreamSourceScopeTest` (3),
      `MediaDetailTitleHarmonizerTest` (5).

## 4. Cihazda doğrulama (yapılacak)

| # | Senaryo | Beklenen |
|---|---|---|
| D1 | Aynı animeyi **MAL** kaydından İzle → Anizm/TrAnimeIzle/AnimeciX | AniList kaydıyla **aynı** sonuç |
| D2 | Aynı animeyi **AniList** kaydından İzle | Sonuç değişmemeli (regresyon yok) |
| D3 | Simkl / Shikimori / TMDB kaydından İzle | Başlık varyantı sayısı AniList ile aynı bantta |
| D4 | Eklenti keşfet → bölüm → İzle | Yalnızca o eklenti + şerit; "Tüm kaynaklarda ara" ile tüm kaynaklar |
| D5 | Bozuk/eski sezon URL'si olan eklenti | Liste sessizce diğer kaynaklarla dolmamalı; aynı eklentinin başlık araması denenmeli |
| D6 | Oynatıcıda sonraki bölüm (eklenti içinden açılmış içerik) | Önce aynı eklenti, olmazsa ortak arama (log: "ortak aramaya düşülüyor") |

**Not:** Bu ortamda Android/Gradle yok → `assembleDebug` ve `testDebugUnitTest` çalıştırılamadı.

## 5. Riskler / geri alma

| Risk | Etki | Önlem |
|---|---|---|
| Jikan + AniList ek istek | İlk eklenti çağrısında ~1-2 ek istek | Oturum önbelleği + 4 sn tavan + negatif önbellek |
| `synonyms` şişmesi → çok varyant | Arama süresi | 12 sınır + normalize tekilleştirme |
| Harmonizer'ın detay nesnesini değiştirmesi | Önbelleklenen detay alanları | Yalnızca başlık alanları; `title`/`titleEnglish` dokunulmaz |
| Kapsam modu kullanıcıyı kilitler | Diğer kaynaklar görünmez | "Tüm kaynaklarda ara" düğmesi + şerit |

Geri alma: `git revert e84732a 58fec46 7dac11a 8f38a7a` (dal: `arena/19bffd86-kitsugi-beta`).

## 6. Commit'ler

| Commit | İçerik |
|---|---|
| `8f38a7a` | Tek eklenti (kapsamlı) arama + kaynak bağımsız başlık evreni (ilk tur) |
| `7dac11a` | MAL `titleRomaji`/`titleNative` + yön düzeltmesi (rapor) |
| `58fec46` | `CanonicalTitleAliases` (AniList + Jikan birleşimi) |
| `e84732a` | `MediaDetailTitleHarmonizer` + Simkl/Shikimori boşlukları |
