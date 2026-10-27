# PLAN & TASK — Listem çift kartları: kaynak içi ikizler + birleşmeyen kopyalar (v2.4.341)

**Tarih:** 2026-10-27 · **Dal:** `arena/9be57d71-kitsugi-beta` · **Önceki sürüm:** v2.4.340
**Kök neden raporu:** `KOK_NEDEN_RAPORU_LISTEM_CIFT_KART_VE_KAYNAK_ICI_IKIZLER_2026-10-27.md`
**Sürüm notu:** `release_notes_v2.4.341.md`

## Kullanıcı isteği

> „Tümü sayfası da dahil böyle sorunlar var, noluyor? Listem sayfasında bu duplicateler niye
> oluyor? Adam akıllı araştırıp bu sorunları tüm Listem sayfaları üzerinden düzelt.“

Ekran görüntüleri: **Tümü**'de ve **Simkl sekmesinde** aynı yapım iki kart
(Kaiju No. 8 S2 · Attack on Titan · Demon Slayer: Infinity Castle).

## Kök neden (özet)

1. **Simkl kategori çakışması:** `movies` + `shows` + `anime` listeleri tekilleştirilmeden
   birleştiriliyordu; aynı `simklId` `Movie`/`TvShow` + `Anime` olarak iki kez geliyor,
   `sameMedia` tür eşitliği istediği için `smartImport` ikinci satırı **ekliyordu**.
2. **Eşzamanlı yazma:** `smartImport` (oku→yaz) ile `replaceSourcePreservingAdultFlags`
   (sil→yaz) aynı kaynakta iç içe girebiliyordu (repository örnek düzeyinde kilit yoktu).
3. **Bayat kimlik ipuçları:** `groupMyListEntries` köprü ipuçlarını **çakışma** denetimine
   sokuyordu; önbellekteki bayat bir `shikimori:`/`mal:` değeri aynı başlığı iki gruba bölüyordu.
4. **Platform sekmeleri hiç gruplanmıyordu:** kaynak içi kopyalar sekmede de çift karttı.

## Kabul kriterleri

- [x] **K1** — Simkl `movies` + `anime` kopyası tek satır (kaynak + "Tümü").
- [x] **K2** — Tür etiketi farklı olsa da kanıtlanmış kimlik (`simklId`, Bangumi `subject_id`)
      birleştirir; `tmdb` kanıt sayılmaz; Anime ↔ Manga asla birleşmez.
- [x] **K3** — Bayat çapraz kimlik ipucu birleşmeyi **engellemez** (yalnızca aday + olumlu kanıt).
- [x] **K4** — Platform sekmeleri yalnızca **kendi kaynağının** kopyalarını eritir.
- [x] **K5** — Sekme sayaçları görünen kart sayısıyla eşleşir (Tümü · AniList · MAL · Simkl ·
      Kitsu · Shikimori · Bangumi).
- [x] **K6** — Var olan çift kayıtlar bir kez koşan onarımla birleşir; kullanıcı verisi
      (ilerleme/durum/puan/favori/+18/not) korunur.
- [x] **K7** — Yazma yolları kaynak başına sıraya girer (eşzamanlı yenileme + içe aktarma).
- [x] **K8** — TV kütüphanesi aynı eritmeyi kullanır.
- [x] **K9** — Birim testleri kök nedenlerin tüm senaryolarını kapsar.
- [x] **K10** — Performans: kimlik anahtarları satır başına bir kez; eritme tek arka plan
      hesabında (iki geçiş: birleşik + kaynak-içi).

## Görev listesi

- [x] **T1** `data/local/ProviderImportDedupe.kt` (**yeni**) — `collapse`, `isSameProviderRecord`,
      `prefer` (tür önceliği + eksik alan devralma).
- [x] **T2** `data/remote/SimklSyncContract.kt` — `mergeCategories(movies, shows, anime)`.
- [x] **T3** `data/auth/SimklImportManager.kt` — kategoriler `mergeCategories` ile birleştirilir.
- [x] **T4** `model/MediaIdentity.kt` — `sameProduction`, `crossTypeProof`, `preferredTypeRank`,
      `compatibleTypes`.
- [x] **T5** `ui/screens/mylist/MyListLibraryGrouping.kt` — `ownKeys` / `keys` ayrımı,
      `collapseAcrossSources`, `xid:` aday kovaları, tür önceliği.
- [x] **T6** `ui/screens/mylist/MyListScreen.kt` — `sourceDedupedEntries`; sekmeler/arama/filtre
      ve sayaçlar bu listeden beslenir.
- [x] **T7** `ui/screens/mylist/MyListComponents.kt` — sayaçlar `allEntries` parametresinden; çağrı yeri eritilmiş listeyi geçer.
- [x] **T8** `data/local/MyListDuplicateRepair.kt` (**yeni**) — sürüm damgalı onarım.
- [x] **T9** `data/local/LibraryWriteLocks.kt` (**yeni**) — kaynak başına mutex.
- [x] **T10** `data/local/MediaEntryRepository.kt` — `smartImport` / `replaceSource...` kilit +
      tekilleştirme; eşleştirme `sameProduction`.
- [x] **T11** `ui/screens/mylist/MyListViewModel.kt` — onarım `init` içinde.
- [x] **T12** `ui/tv/library/TvLibraryScreen.kt` — TV kütüphanesi eritmesi.
- [x] **T13** Testler — `MediaProductionIdentityTest`, `MyListLibraryGroupingTest` (+4),
      `ProviderImportDedupeTest`, `MyListDuplicateRepairTest`.
- [x] **T14** Dokümantasyon + sürüm notu + `appVersionName` 2.4.340 → **2.4.341**.

## Doğrulama

Android SDK/JDK bu ortamda yok; `MediaIdentity` + gruplama Python'a birebir port edilerek
8 senaryo ölçüldü (rapor §5). Değişen dosyalar parantez/blok dengesi taramasından geçti.
`./gradlew :app:testFossDebugUnitTest` CI/yerel ortamda koşturulmalıdır.
