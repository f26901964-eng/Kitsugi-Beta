# PLAN & TASK — Listem "Tümü": çift kart + sayı şişkinliği (Bangumi sonrası) (v2.4.273)

**Tarih:** 2026-10-13 · **Dal:** `arena/d97dd4ba-kitsugi-beta` · **Önceki sürüm:** v2.4.272
**Ayrıntılı teknik rapor:** `KOK_NEDEN_RAPORU_LISTEM_TUMU_SAYI_SISMESI_BANGUMI_2026-10-13.md`
**Sürüm notu:** `release_notes_v2.4.273.md`

## Kullanıcı isteği

> „Bangumi ile çapraz veri eşitlemesi yaptıktan ve ~800 öğeyi eşitledikten sonra diğer kaynaklarla
> aynı veriyi kapsaması gerekirken **Tümü** kısmında ayrı ayrı gösteriyor. Tümü'ndeki içerik sayısı
> taş çatlasa 900 civarı olması gerekirken 1502 oluyor. Sorun tam olarak ne, bulup düzelt.”

**Tanı (ekran görüntüsüyle doğrulandı):** AniList 783 · MAL 799 · Simkl 629 · Kitsu 797 ·
Shikimori 799 · Bangumi 716 → **Tümü 1502**. `1502 − 716 = 786 ≈ AniList` → Bangumi satırlarının
**tamamı** ayrı kart sayıyor; tek bir kayıt bile birleşmiyor.

## Kök neden (özet)

1. `MediaEntry.malId` **satırın kendi kaynağının** kimliğini taşır (AniList 100M / Simkl 150M /
   Kitsu 300M / Shikimori 400M / **Bangumi 500M**). `MediaIdentity.keys()` uzay etiketli ürettiği
   için `Anime:bangumi:<sid>` ile `Anime:mal:<id>` **hiç kesişmez**.
2. ID eşleşmeyince başlık yedeğine düşülür; Bangumi `name`/`name_cn` (**Çince**) döndürür →
   `葬送のフリーレン` ≠ `Sousou no Frieren` → eşleşme yok → **ayrı kart**.
3. (İkincil) `groupMyListEntries` açgözlü tek geçişti: gruplar birbirine hiç eklenmediği için
   sonuç **içe aktarma sırasına bağlıydı**; aynı yapım iki gruba düşebiliyordu.

## Kabul kriterleri

- [x] **K1** — "Tümü" tekil içerik sayısını gösterir (786±), çift kart kalmaz.
- [x] **K2** — Birleşme **sıra bağımsızdır** (karışık/ters/blok sıraların hepsinde aynı sonuç).
- [x] **K3** — Ağ isteği **yok**: yalnızca çapraz eşitlemenin zaten kalıcı yazdığı kimlik verileri okunur.
- [x] **K4** — Kayıt verisi değişmez: satır kendi kaynağının kimliğini korur (sekmeler, detay sayfası,
  dış API yazmaları, `isBangumiListMember` aralık kontrolü aynen çalışır).
- [x] **K5** — Güvenlik: belirsiz eşleme kullanılmaz, satırın **sahip olduğu** uzay ezilmez,
  yanlış köprü iki farklı yapımı birleştirmez, tür farkı (Anime ≠ Manga) birleşmeyi engeller.
- [x] **K6** — Kalıcı öğrenme: `MAL ↔ Bangumi` çifti Bangumi satırından çözülünce de yazılır.
- [x] **K7** — Performans: kimlik anahtarları satır başına 1 kez; konsolidasyon tek tarama.
- [x] **K8** — Birim testleri kök nedenin tüm senaryolarını kapsar.

## Görev listesi

- [x] **T1** `data/local/MyListIdentityCrosswalk.kt` (**yeni**)
  - `build(context, entries)`: yalnızca kendi ekseninde kimliği olmayan satırlar için köprü kurar
    (Anime/Manga → `mal`, Film/Dizi → `tmdb`/`simkl`), `Dispatchers.IO`.
  - Kaynak 1 — `CrossProviderIdentityCache` okuması: `CrossProviderIds.cacheKey(type, malId, title,
    titleEnglish, year)` ile satır başına altı kimlik (ağ yok).
  - Kaynak 2 — `BangumiLocalMappingCache.snapshot()` → `reverseBangumiMapping()`:
    `bgm_a_<malId>/bgm_m_<malId> → subject_id` haritası `"<Tür>:<subject_id>" → malId`e çevrilir;
    birden fazla MAL'e çıkan条目 **atılır**.
  - `extraKeysFor()`: aday anahtarlar üretilir, **satırın zaten taşıdığı uzay filtrelenir**
    (bayat önbellek, kendi ID'sini ezemez).
- [x] **T2** `ui/screens/mylist/MyListLibraryGrouping.kt`
  - `groupMyListEntries(entries, identityExtras)` — ek anahtarlar `MediaIdentity.keys(entry, extras)`.
  - `IdentityKeysOf(entry, keys)` ile anahtarlar satır başına bir kez hesaplanır.
  - **2. tur konsolidasyon:** `groupIndexesByIdentity` kovaları üzerinden union-find; birleşme
    yalnızca **tüm** üye çiftleri çakışmasız **ve** en az bir çift `sameMedia` ise yapılır;
    temsilci/rütbe ve `sourceIds` rozetleri birleştirilir.
- [x] **T3** `model/MediaIdentity.kt`
  - `keys(entry, extras)` + `conflictingIdentityKeys(typeA, typeB, keysA, keysB)` +
    `sameMedia(a, b, keysA, keysB, allowTitle)` overload'ları (eski imzalar ve çağıranlar korundu).
  - **Sertleştirme:** uzay başına tek değere indirgeme (`associateBy`) kaldırıldı; aynı uzayda
    birden çok ID olabilir ve **tek ortak değer** çakışmayı kaldırır.
  - `titleMatch()` ayrıldı; karar ağacı birebir eski davranışla aynı.
- [x] **T4** `data/auth/CrossProviderIdentity.kt` — `resolve()` sonunda `BangumiLocalMappingCache.put`
  ile `MAL ↔ subject_id` **iki yönde** öğrenilir (`connected.bangumi` + `supportsBangumi` kapısı).
- [x] **T5** `data/remote/BangumiIdNamespace.kt` — `BangumiLocalMappingCache.snapshot(context)`
  (prefs adını dışarı sızdırmadan salt okunur kopya).
- [x] **T6** `ui/screens/mylist/MyListScreen.kt` — `LaunchedEffect(entriesAfterAdultFilter)` ile köprü
  kurulur, `allLibraryItems`/`allLibraryEntryCount`/rozet haritası beslenir; önceki harita korunduğu
  için sayı zıplaması yalnızca ilk karede olur.
- [x] **T7** Testler — `MyListIdentityCrosswalkTest` (7 · saf), `MyListLibraryGroupingTest` (+2),
  `MediaIdentityDisambiguationTest` (+2).
- [x] **T8** Dokümantasyon — kök neden raporu, sürüm notu, `appVersionName` 2.4.272 → **2.4.273**.

## Doğrulama (bu sandbox'ta yapılabilen)

`MediaIdentity` + gruplama Python'a birebir port edilip 786 tekil yapım × 6 kaynak (4716 satır)
üzerinde ölçüldü:

| Sıra | Öncesi | Sadece köprü | Köprü + konsolidasyon |
|---|---|---|---|
| iç içe | 1572 | 786 | **786** |
| ters | 1572 | 1572 | **786** |
| kaynak blokları | 1572 | 786 | **786** |
| karışık ×2 | 1572 | 921 / 889 | **786** |

Negatif senaryolar: belirsiz `subject_id → malId` atılıyor · sahip olunan `mal:` uzayı ezilmiyor ·
tür farkı ve çakışan MAL ID'ler birleşmeyi engelliyor · `sourceIds` altı rozeti tek kartta topluyor.

## Kullanıcı tarafında beklenen / kalan iş

- [ ] **B1** `./gradlew assembleDebug` (veya `build-apk.ps1`) — sandbox'ta JDK/Android SDK yok.
- [ ] **B2** `./gradlew testDebugUnitTest` — 16 test (11 yeni + 5 eski dosya testi) yeşil olmalı.
- [ ] **B3** Cihaz: Listem → "Tümü" ≈ 786; Bangumi sekmesi 716 kaydını **korumalı**; aynı içeriğin
  iki platformdaki ilerlemesi ayrı kalabilmeli (yalnızca kart birleşir).
- [ ] **B4** Eşitlemesi hiç yapılmamış Bangumi kayıtları köprüsüz kalabilir (doğrulansın diye bilerek
  ağa çıkılmıyor) → bir tur çapraz eşitleme çalıştır, sonraki açılışta kapanmalı.

## Sonraki adım (terkendi, kapsam dışı)

- Aynı köprü `MediaEntryRepository.withCrossSourceAdultFlags` (+18 yayılımı) ve profil tarafındaki
  `KitsugiUserMediaListScreen` birleşimlerine de verilmeli — bugün ikisi de yalnızca
  `MediaIdentity.keys()` tabanlı.
- `SimklImportManager` satırlarının `malId` alanında 150M penceresi kullanılmıyor (`simklId` kolonu
  dolu), bu yüzden `MediaIdentity.keys()`te eksik bir Simkl dalı **bilinçli olarak eklenmedi**.
