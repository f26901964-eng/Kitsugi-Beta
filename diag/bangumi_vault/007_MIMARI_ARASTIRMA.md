# 007 MİMARİ ARAŞTIRMASI — "Sığmıyor" sorununun kalıcı çözümü

> **Durum:** karar belgesi · 2026-10-21 · dal `arena/ab577289-kitsugi-beta` · PR **#26**
> **Sonuç:** 59.510 kaydın **hiçbiri** kırpılmayacak. `as_is` politikası aynen korunur.
> Tek yapılması gereken, etiket sözlüğünü **Kotlin kaynağından** çıkarıp **veri dosyasına** taşımak.

---

## 1. Özet — 30 saniyede

Bugün iki ayrı sert tavan var ve 007 ikisine de çarpıyor:

| Tavan | Nerede | Sınır | Bugün | 007 sonrası |
|---|---|---:|---:|---:|
| Android kaynak ID'si | `R$string` / kaynak tablosu | **65.535** / tür | 58.398 | +52.476 → **taşar** |
| JVM sınıf sabit havuzu | `BangumiTagDictionary.kt` | **65.535** / sınıf | 14.623 literal | +60.000 → **taşar** |

Sektörün cevabı net: **büyük veri kaynak dosyasına değil, veri dosyasına yazılır ve çalışma zamanında okunur.**
Bunu yaptığımızda iki tavan da tamamen ortadan kalkar — 007 dahil hiçbir bant kırpılmaz.

Ölçtük: bugünkü 3.983 grup + 9.397 etiket, 377 KB Kotlin yerine **258 KB TSV (gzip 101 KB)**.
007'nin en kötü durumu (59.510 kayıt, hepsi `as_is`) **~3,7 MB TSV / ~1,0 MB gzip** — bir APK için hiçbir şey.

---

## 2. Sorun tam olarak ne?

### 2.1 Android kaynak tablosu
Kaynak ID'si `0xPPTTEEEE` biçimindedir: paket (8 bit), tür (8 bit), giriş (16 bit).
16 bit giriş → **tür başına 65.535**. Bu, toplam string sayısının tavanıdır; dil/yoğunluk
varyantları aynı ID'yi paylaştığı için sayıya eklenmez. ([SO #8218285](https://stackoverflow.com/questions/8218285/what-limits-are-there-on-the-number-of-android-resources))
Modül sayısını artırmak `R$string` **sınıfını** küçültür ama bu ortak tabloyu değiştirmez.
Bu yüzden 3 shard (13.565 / 13.782 / 13.580) çözüm değil, erteleme.

### 2.2 JVM sınıf sabit havuzu
`.class` dosyasında `constant_pool_count` 16 bittir → sınıf başına **65.535** sabit.
Her `g("key", "TR", "EN", "etiket"...)` çağrısı burada 4-20 giriş harcar.
([SO #27576901](https://stackoverflow.com/questions/27576901/android-string-resource))
Aynı duvar, Compose Multiplatform'da `Res$string.<clinit>` için "Method too large" olarak
belgelenmiş ve resmî işaroundu "string'leri farklı modüllere böl" olmuş
([#4194](https://github.com/JetBrains/compose-multiplatform/issues/4194)).
Bizde 006 sonunda 14.623 literal — yani tavanın **%22'si** dolu. 007 bunu 3-4 katına çıkarır.

### 2.3 Bu projede zaten kırıldı
`PROGRESS.md` (PR #24 takibi): Android `processFossDebugResources` **`ClassTooLargeException: R$string`**
ile düştü; çözüm 3 non-transitive modül oldu. RAW sabitleri de 49.999 modified-UTF-8 baytlık
parçalara bölünmek zorunda kaldı. Yani sınır teorik değil — bu repoda iki kez ısırdı.

---

## 3. Sektör bu işi nasıl çözüyor?

| # | Yaklaşım | Kim kullanıyor / kanıt | 65.535 tavanı | Artı | Eksi |
|---|---|---|---|---|---|
| A | Kaynağa yazmaya devam + modül böl | Compose MP resmî işaroundu, Thunderbird 43 dizin ([#989](https://github.com/miguelaguiar01/corpus/issues/989)) | ❌ aşar | basit | tavan gerçekten kalkmaz |
| B | `assets/` içinde TSV/JSON + bellekte `Map` | sözlük/veri uygulamalarının klasiği | ✅ kalkar | en basit, hızlı arama | tamamı RAM'de |
| C | **Önceden doldurulmuş SQLite + `createFromAsset`** | **resmî Android API** ([docs](https://developer.android.com/training/data-storage/room/prepopulate)) | ✅ kalkar | indeksli, RAM sınırlı, delta güncelleme | ilk açılışta DB kopyası |
| D | Bellek eşlemeli (mmap) ikili indeks | ICU kaynak paketleri, StarDict türü sözlükler | ✅ kalkar | ~0 heap, O(1) | özel biçim bakımı |
| E | Play Asset Delivery / indirilebilir paket | büyük oyun/medya verisi | ✅ kalkar | APK küçük | ağ + mağaza bağımlılığı |

Ayrıca **B/C/D'nin ortak yan faydası:** `getIdentifier` ortadan kalkar. Android belgeleri bu
API'yi "discouraged" işaretler; Dan Lew ölçümünde 10.000 çağrı G1'de ~3,2 sn sürüyor ve
yansıma tabanlı yol ~5× daha hızlı ([blog](https://blog.danlew.net/2009/12/27/dynamically_retrieving_resources_in_android/)).
Yani bu geçiş yalnız kapasite değil, **performans** da kazandırır.

---

## 4. Bizim ölçümlerimiz

### 4.1 Bugünkü sözlük → veri
```
grup: 3.983 | etiket: 9.397
TSV ham: 257.847 bayt | gzip: 101.217 bayt (%39)
bugünkü BangumiTagDictionary.kt: 377.032 bayt
```
TSV'ye çevirip geri okuduk: **birebir aynı** (sıra, TR/EN, etiketler).
"İlk yazım kazanır" gölgelenmesi 63 vakada aynı sonucu veriyor — `test_tag_store.py` bunu sabitliyor.

### 4.2 007 projeksiyonu
```
en kötü durum: +59.510 grup, +61.367 etiket
yeni toplam: 70.764 etiket
TSV ~3,73 MB | gzip ~1,04 MB
```
Karşılaştırma: aynı veriyi kaynak olarak yazmak **52.476 yeni kaynak ID'si** isterdi → tavanın 7 katı.

### 4.3 Neden `as_is` artık bedava
`as_is` kaydı bugün 1 kaynak ID'si + 3-4 JVM sabiti harcıyordu. Yeni düzende **1 TSV satırı**.
Ve ekranda görünen değer değişmiyor (ham ad kalır) — politika aynen korunur.

---

## 5. Seçilen mimari

```
canon.tsv  ──►  build_tag_store.py  ──►  app/src/main/resources/bangumi/tags.tsv
(tek karar kaynağı)                        (3.9k bugün → 70k 007 sonrası, gzip ~1 MB)

çalışma zamanı:
  BangumiTagDictionary.groups = TagStore.load()      // classpath'ten TSV
  byLabel / byCompact / entryFor / patternEntry      // DEĞİŞMEZ
```

### Neden `assets/` değil, classpath kaynağı?
- `BangumiTagDictionary` bir `object`'tir ve **Context almaz**. `assets/` okumak Context ister;
  sözlüğü Context'e bağımlı kılmak `KitsugiSpoilerRules` ve `KitsugiTranslations` çağrı zincirini bozar.
- `src/main/resources` hem **APK'da** hem **JVM birim testlerinde** aynı yoldan okunur →
  `BangumiMasterDictionaryTest` (testFossDebugUnitTest) hiç değişmeden çalışır.
- R8/kaynak küçültme classpath kaynaklarını silmez; `tools:keep` gerekmez.

### Katmanlar (geri uyumluluk)
1. **Veri deposu (yeni, tek doğruluk):** `tags.tsv` — grup + etiket + TR/EN.
2. **Kaynak katmanı (korunur):** `bangumi_tag_<key>` varsa **o kazanır** (mevcut override davranışı,
   `verify_vault.py` değer eşitliği denetimi aynen sürer). Yoksa depodaki değer kullanılır.
3. **Hiçbiri yoksa:** ham etiket gösterilir (bugünkü davranış).

Yani **görüntülenen TR/EN değer değişmez**, `bangumi_tag_<canon>` gösterim kaynağı korunur.

---

## 6. Geçiş planı (her adım tek başına yeşil)

| Adım | İş | Doğrulama | Geri alma |
|---|---|---|---|
| **S0** | `build_tag_store.py` + `test_tag_store.py` (bu PR'da) | 27/27 test, TSV turu birebir | dosya sil |
| **S1** | TSV'yi `app/src/main/resources/bangumi/tags.tsv` olarak üret | `--check` birebir | dosya sil |
| **S2** | `BangumiTagDictionary.kt` → yükleyici; `groupChunkNN()` gövdeleri kaldırılır (~3.900 satır) | `verify_bangumi_master.py` TSV'yi okur, 3.983 grup | `git checkout` |
| **S3** | `vault_to_strings.py` ve `gen_bangumi_master.py` grup yazımını TSV'ye çevir | `--check` +0/+0 | `git checkout` |
| **S4** | 007 bantları: kararlar canon'a, canon TSV'ye, kaynak **hiç büyümez** | bant kapanış zinciri | map/canon geri alınır |

S2 sonrası `BangumiTagDictionary.kt` ~4.000 satırdan ~150 satıra iner; derleme süresi ve
APK da küçülür. S4'te `R$string` sayısı **58.398'de sabit kalır** → kapasite sorunu biter.

### 007 bantları (değişmiyor, sadece artık sığıyor)
`a 19` (225) · `b 15–18` (1.179) · `c 11–14` (1.892) · `d 8–10` (2.253) · `e 6–7` (2.373)
· `f 4–5` (4.437) · `g 3` (4.275) · `h 2` (8.666) · `i 1` (33.169) · `j count=0` (1.041)

---

## 7. Doğrulama kapıları (S2/S3 için)

1. `build_tag_store.py --check` → birebir.
2. `python3 -m unittest discover -s diag/bangumi_vault/scripts -p 'test_*.py'` → 27/27.
3. `verify_bangumi_master.py` → önce/sonra **aynı** grup sayısı (3.983), aynı eşleme (52.176),
   aynı TR/EN (58.398).
4. `verify_vault.py` → otorite cümlesi **birebir** (`52219 / 51104 / 61398 / 121284`).
5. `gen_bangumi_master.py --check` → +0/+0.
6. `vault_to_strings.py --check` → +0/+0.
7. CI `verify` işi yeşil + `compile-and-test` (bilinen kırık ayrı tutulur).

> Not: S2'de `verify_bangumi_master.py` ve `gen_bangumi_master.py` de TSV okuyacak şekilde
> güncellenir; **aynı anda** yapılmalı, aksi hâlde doğrulayıcı 0 grup okur.

---

## 8. Riskler ve karşılıkları

| Risk | Etki | Karşılık |
|---|---|---|
| Classpath kaynağı APK'ya girmez | sözlük boş → çeviri kaybı | S2'de `--check` + CI; ayrıca yükleyici boşsa `Log.e` + testte 3.983 grup iddiası |
| Sözlük yüklenirken ANR | ilk çizimde gecikme | `by lazy` + 258 KB için ~5-15 ms; 007'de ~1 MB, ilk çağrıda tek seferlik |
| Bellek (70k etiket) | ~6-8 MB heap | HashMap yerine ileride açık adresleme; ölçüm S4'te |
| TSV ile Kotlin sürümü kayar | sessiz fark | `--check` her kapanış zincirinde |
| R8 kaynağı atar | üretimde boş | `src/main/resources` R8 kapsamı dışı; S2'de release derlemesi CI'da |

---

## 9. Karar

- **Seçilen:** B (classpath veri dosyası) + kaynak katmanı override olarak korunur.
- **Reddedilen:** kaynağa devam (tavan), sonsuz shard (tavan), Play Asset Delivery (ağ/mağaza bağımlılığı),
  SQLite (şu an gereksiz; delta güncelleme ihtiyacı doğarsa C'ye geçiş yolu açık).
- **007 politikası:** `as_is` kuralı **aynen** kalır. Kırpma, SKIP'e zorlama, "count=1'i atla" **yok**.

---

## 10. Kaynaklar

- Android kaynak ID sınırı (65.535 / tür): https://stackoverflow.com/questions/8218285
- JVM sabit havuzu sınırı (65.535 / sınıf): https://stackoverflow.com/questions/27576901
- Compose Multiplatform `Res$string` "Method too large": https://github.com/JetBrains/compose-multiplatform/issues/4194
- Çok modüllü string yönetimi (43 dizin): https://github.com/miguelaguiar01/corpus/issues/989
- `getIdentifier` "discouraged" + ölçüm: https://blog.danlew.net/2009/12/27/dynamically_retrieving_resources_in_android/
- Önceden doldurulmuş Room DB (`createFromAsset`): https://developer.android.com/training/data-storage/room/prepopulate
- APK'ya veritabanı gömme: https://stackoverflow.com/questions/513084
- `STRING_TOO_LARGE` (32.767 karakter): https://brightinventions.pl/blog/string-too-large-in-android-resources/
- Bu repodaki kanıt: `PROGRESS.md` §7 (RAW 49.999 bayt), §8 PR #24 (`ClassTooLargeException: R$string`)
