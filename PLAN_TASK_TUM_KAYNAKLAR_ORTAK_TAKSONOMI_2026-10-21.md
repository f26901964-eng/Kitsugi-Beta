# PLAN & TASK — Tüm kaynaklar için ortak tür / etiket / kurum katmanı (v2.4.337)

**Tarih:** 2026-10-21 · **Dal:** `arena/a610f459-kitsugi-beta` · **Taban:** `971abf1`
**Kök neden raporu:** [KOK_NEDEN_RAPORU_TUR_ETIKET_KURUM_EKSIKLERI_2026-10-21.md](KOK_NEDEN_RAPORU_TUR_ETIKET_KURUM_EKSIKLERI_2026-10-21.md)

---

## 1) Hedef (kullanıcı isteği)

1. **Bangumi'de yaptığımızın aynısı** tüm detay kaynakları için: AniList, MAL/Jikan, Shikimori,
   Kitsu, Simkl, TMDB.
2. Türler **ve** etiketler **birleştirilecek** (biri diğerini ezmeyecek); Bangumi'nin hazır
   etiketleri/türleri diğer kaynaklarla birlikte görünecek.
3. Eksik kalan tür/etiket varsa **tamamlanacak**, TR/EN dil dosyalarıyla uyumlu çalışacak.
4. Aynı şekilde **yayıncı / yapımcı / stüdyo / kanal / dergi** bilgileri tüm kaynaklardan
   eksiksiz toplanacak.
5. **Rusça ve Çince metin ekranda görünmeyecek.**

---

## 2) Kabul ölçütleri

| # | Ölçüt | Doğrulama |
|---|---|---|
| A1 | İki kaynakta da tür varsa **ikisi de** listede | `TaxonomyLabelsTest` + `KitsugiTaxonomyMerge.mergeGenres` |
| A2 | `Экшен` · `Action` · `アクション` · `动作` · `Aksiyon` ekranda **tek çip** | `taxonomyCanonicalKey` testleri |
| A3 | Shikimori'nin 79 resmî türünün tamamı sözlükte | `check_taxonomy_master.py` K5 |
| A4 | TR/EN değerlerinde tek bir Kiril harf yok | K4 |
| A5 | Bilinmeyen Kiril etiket Latin harfine çevrilir, CJK etiket gizlenir | `transliterateCyrillic`, `isUnresolvedForeignLabel` |
| A6 | Aynı kurum stüdyo+yapımcı olarak iki kez görünmez | `dropProducersThatAreStudios` |
| A7 | `bangumi_tag_*` + `taxonomy_tag_*` kaynakları tr/en dosyalarında eşit | `check_taxonomy_master.py` K6, `check_localization_resource_parity.py` |
| A8 | İki üreteç betiği art arda çalıştırılabilir (idempotent) | `sync_*_tag_strings.py --check` |
| A9 | Kurum alanındaki çöp (kaomoji, `;` notları, tırnak artığı) ekranda görünmez | `sanitizeOrgName` + `KitsugiStudiosCard` filtresi |
| A10 | Değiştirilen Kotlin dosyaları sözdizimsel olarak geçerli | `tree-sitter-kotlin` ayrıştırması |

---

## 3) Mimari

```
detay açılışı (herhangi bir kaynak)
        │
        ├─ fetchPrimaryDetail ──► kaynağın kendi tür/etiket/kurum verisi
        │
        └─ enrichDetail (zaman tavanlı, arka plan)
                 │
                 ├─ TMDB zenginleştirmesi ──► trMeta (tür + keyword + şirket/kanal)
                 ├─ Bangumi enrich (yalnız Bangumi kaynağı) ──► MAL/AniList birleşimi
                 ├─ AniList hafif sorgu ──► genres + tags(+spoiler/rank) + studios
                 └─ KitsugiTaxonomySources.collect()
                          ├─ Jikan  : genres/themes/demographics/studios/producers/licensors
                          └─ AniList: (yukarıdaki yanıtla aynı)
                 │
                 ▼
        KitsugiTaxonomyMerge  ──► mergeGenres / mergeTags / mergeOrgs / dropProducersThatAreStudios
                 │
                 ▼
        gösterim katmanı: toLocalizedTaxonomyLabel / toDisplayOrgName
                 │
                 ▼
        dil dosyası override'ı: taxonomy_tag_* , bangumi_tag_*
```

**Zaman bütçesi:** toplam 6 sn (`TOTAL_TIMEOUT_MS`), kaynak başına 4,5 sn. Geciken kaynak atlanır;
sayfa asıl detayla açılmaya devam eder. Sonuç `DetailCache` + Room'a yazılır, ikinci açılışta ağ yok.

---

## 4) Değişen / eklenen dosyalar

### Yeni

| Dosya | Görev |
|---|---|
| `app/src/main/java/com/kitsugi/animelist/utils/RussianTaxonomyDictionary.kt` | 114 grup; Shikimori'nin tamamı + Rusça etiketler; MAL tür kimliği eşlemesi |
| `app/src/main/java/com/kitsugi/animelist/utils/KitsugiTaxonomyLabels.kt` | Tek çözümleyici, kanonik anahtar, Kiril transliterasyonu, kurum gösterimi |
| `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiTaxonomyMerge.kt` | Tür/etiket/kurum birleştirme motoru |
| `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiTaxonomySources.kt` | Paralel + zaman tavanlı çapraz kaynak toplayıcı (Jikan + AniList) |
| `app/src/test/java/com/kitsugi/animelist/utils/TaxonomyLabelsTest.kt` | 23 birim test (TR/EN, kanonik anahtar, transliterasyon, birleştirme) |
| `scripts/sync_taxonomy_tag_strings.py` | `taxonomy_tag_*` kaynaklarını üretir (idempotent, `--check`) |
| `scripts/check_taxonomy_master.py` | K1–K9 denetimleri + `diag/taxonomy_master/worklist.tsv` |
| `.github/workflows/taxonomy-master.yml` | CI: sözlük denetimi, üreteç `--check`, TR/EN paritesi, `TaxonomyLabelsTest` |
| `diag/taxonomy_master/README.md` | Sözlük çalışma akışı (Drive'a yükleme dahil) |

### Değişen

| Dosya | Değişiklik |
|---|---|
| `KitsugiTranslations.kt` | `toTurkishGenre` / `toLocalizedTagLabel` tek çözümleyiciye bağlandı; `taxonomyStringOverride`, `englishTaxonomyName`, `englishTaxonomyToTurkish`; `localizedDistinctTags` CJK'yı gizler; kurum adı Kiril transliterasyonu + `sanitizeOrgName()` |
| `KitsugiDetailComponents.kt` | Stüdyo/yapımcı/kanal kartı: adı temizlenince boşalan kayıtlar çizilmez; başlıklar görünür listeye göre seçilir |
| `KitsugiTaxonomyLabels` ile birlikte `KitsugiSpoilerRules.canonicalTagKey` | Rusça anahtar önceliği (`Экшен` ≡ `Action`) |
| `KitsugiAniListDetailClient.kt` | `AniListTagsAndAiring` + `genres` + `studios`; GraphQL sorgusu genişletildi |
| `KitsugiDetailClient.kt` | Tür/kurum zenginleştirmesi birleştirmeye çevrildi; çapraz kimlik çözümü AniList kapısından çıkarıldı; taksonomi toplama + normalleştirme eklendi |
| `KitsugiBangumiDetailClient.kt` | `ifEmpty` yerine `mergeGenres` / `mergeOrgs` / `dropProducersThatAreStudios` |
| `SearchTranslation.kt` | `translateToTurkishForDisplay` / `translateToEnglishForSearch` / `displayLabel` Rusça desteği |
| `scripts/sync_bangumi_tag_strings.py` | **Yıkıcı** blok yazımı düzeltildi (veri kaybı önlendi) |
| `values/strings.xml`, `values-en/strings.xml` | +114 `taxonomy_tag_*`; Bangumi bloğu tek parça, eksik 494 kayıt yazıldı, artık kaynak silindi |
| `app/build.gradle.kts` | 2.4.336 → 2.4.337 |

---

## 5) Görev listesi

- [x] T1 — Kök neden analizi (KN-1…KN-6) ve kod kanıtları
- [x] T2 — Rusça sözlük (114 grup / 79 resmî tür + MAL kimlikleri)
- [x] T3 — Tek çözümleyici + kanonik anahtar + Kiril transliterasyonu
- [x] T4 — Birleştirme motoru (tür / etiket / kurum, rol-farkında tekilleştirme)
- [x] T5 — Çapraz kaynak toplayıcı (AniList + Jikan), zaman tavanlı
- [x] T6 — AniList sorgusuna `genres` + `studios`
- [x] T7 — `enrichDetail` ve Bangumi `mergeDetail` entegrasyonu
- [x] T8 — TMDB tür ve şirketlerinin birleştirmeye katılması
- [x] T9 — `taxonomy_tag_*` üreteci + K1–K9 denetleyici + iş listesi
- [x] T10 — `sync_bangumi_tag_strings.py` veri kaybı düzeltmesi + dil dosyası onarımı
- [x] T11 — Birim testleri
- [x] T12 — Rapor + plan + sürüm notu + sürüm artırımı
- [x] T13 — Değişen dosyaların zip'i ve iş listesinin Drive'a yüklenmesi
- [x] T14 — Kurum adı temizliği (`sanitizeOrgName`) + `KitsugiStudiosCard` filtresi
- [x] T15 — `area_chinese_speaking` yazım hatası ve dil dosyası eşitlemesi
- [x] T16 — `taxonomy-master` CI iş akışı + tree-sitter sözdizimi doğrulaması
- [x] T18 — `gen_bangumi_master.py` güvenlik freni (orgs.tsv 365 satır ↔ sözlük 11.693 satır)
- [ ] T17 — CI'da `./gradlew test` + cihazda TR/EN/RU arayüzlü duman testi (bu sandbox'ta JDK yok)

---

## 6) Cihazda denenecek senaryolar

| # | Senaryo | Beklenen |
|---|---|---|
| D1 | Shikimori kaynağından bir anime aç | Türler **Türkçe**; hiçbir çipte Kiril yok |
| D2 | Aynı animeyi Bangumi'den aç | Bangumi etiketleri + AniList etiketleri birlikte; tekrar yok |
| D3 | MAL kaynaklı bir anime | Stüdyo listesinde MAL + AniList stüdyoları birleşik, kimlikli kayıt tercih edilmiş |
| D4 | TMDB filmi | Yapım şirketleri + kanallar birleşik; yapımcıda stüdyo tekrarı yok |
| D5 | Arayüz dili EN | Türler İngilizce kanonik adlarla (`Action`, `Slice of Life`) |
| D6 | İlk açılış süresi | Enrich 6 sn tavanını aşmıyor; sayfa asıl veriyle açılıyor |
| D7 | Aynı sayfayı ikinci kez aç | Ağ isteği yok (Room/DetailCache) |

---

## 7) Drive paketi

`Kitsugi_Ortak_Taksonomi_v2.4.337.zip` içeriği:

- `PLAN_TASK_TUM_KAYNAKLAR_ORTAK_TAKSONOMI_2026-10-21.md`
- `KOK_NEDEN_RAPORU_TUR_ETIKET_KURUM_EKSIKLERI_2026-10-21.md`
- `release_notes_v2.4.337.md`
- `diag/taxonomy_master/worklist.tsv`
- `diag/taxonomy_master/README.md`
- `Kitsugi_Ortak_Taksonomi_v2.4.337.patch` (tam diff)
