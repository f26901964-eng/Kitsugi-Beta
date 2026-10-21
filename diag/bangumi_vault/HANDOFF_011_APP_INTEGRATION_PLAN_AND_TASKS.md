# HANDOFF 011 — Bangumi VAULT & Sözlük Uygulama Entegrasyon Planı ve Görev Listesi (2026-10-21)

Bu belge, **Chunk 001–009** ve **Batch 008 kapsamlı çapraz rol/etiket taraması (`8a5c267` / `4262d84`)** sonunda tamamlanan Bangumi etiket, tür, stüdyo, yapımcı, yayıncı ağ, yayımcı ve dergi yerelleştirme/küresel ad verilerinin **PR #27 + PR #29 S1–S3 çok modüllü dil kaynağı + classpath TSV veri deposu mimarisiyle** ve güncel **`main` (`971abf1`, `v2.4.336`)** tabanıyla birleştirilerek **Kitsugi-Beta Android uygulamasına tam entegrasyonunu** belgeler.

---

## 0. Güncel Durum Özeti (Tek Bakışta — Tam Entegrasyon Tamamlandı)

- **Aktif Git Dalı:** `arena/80e5e83a-kitsugi-beta` (`main` @ `971abf1` tabanlı, tüm MAL/oynatıcı düzeltmeleri %100 korunur)
- **Birleştirilen Kaynaklar:**
  1. `main` (`971abf1` — `v2.4.336`: MAL bildirim sayfalama, oynatıcı düzeltmeleri ve yeni arayüz metinleri %100 korundu)
  2. `origin/arena/46189a1e-kitsugi-beta` (**PR #29** @ `c5e32eb` ve **PR #27** @ `dabff0f`: 4 modüllü Android dil kaynakları + `bangumi/tags.tsv` & `bangumi/orgs.tsv` classpath veri deposu + 30 birim testi)
  3. `origin/arena/284d08ba-kitsugi-beta` (**PR #31** @ `4262d84` / `8a5c267`: Batch A + Batch B + Producer 006 rev + Tag 007b 001–004 + Batch 008 kapsamlı 196 karar dahil tüm güncel `canon.tsv`, `org_global_names.tsv` ve `map_chunk_*.tsv` verileri)
- **Google Drive Otoriter Klasörü:** `1pZAOYsAnMjveNK6grdXjqV9IDM1TFaJp` (**48 aktif dosya**, güncel damgalı JSON/TSV ve dokümanlarla senkron)
- **Doğrulanmış Metrikler (`2026-10-21`):**
  - `apply_009_batch.py --mirror-repair`: ✅ **`0` bekleyen cross-role ayna farkı**
  - `apply_map.py --check`: ✅ **`111.310` canon satırı · `121.284` map satırı (`117.289` canon + `3.995` skip)**
  - `vault_to_strings.py --check`: ✅ **`+0` yeni XML ID · `tags.tsv`: `58.338` grup · `orgs.tsv`: `52.626` eşleme**
  - `verify_vault.py`: ✅ **`52.219` `bangumi_*` XML anahtarı · `106.335` vault kaydı · `121.284` map satırı · `121.284` damga**
  - `verify_bangumi_master.py`: ✅ **`BangumiTagDictionary (tags.tsv)`: `58.338` grup · `BangumiOrgDictionary (orgs.tsv)`: `52.626` eşleme · GERÇEK çakışma: `0` · 4 modül toplam `strings.xml`: TR `58.451` / EN `58.451` simetrik** (`app`: `17.524`, `shard-1`: `13.565`, `shard-2`: `13.782`, `shard-3`: `13.580`)
  - `gen_bangumi_master.py --check`: ✅ **`+0` grup, `+0` etiket, `+0` kayıt**
  - `check_test_expectations.py`: ✅ **`23 / 23` test beklentisi veriyle tam uyumlu**
  - `check_bangumi_meta_tag_coverage.py`: ✅ **`158 / 158` resmî Bangumi meta etiketi kapsanıyor**
  - `python3 -m unittest discover -s diag/bangumi_vault/scripts -p "test_*.py"`: ✅ **`30 / 30` birim testi geçti**

---

## 0.1. Bu Oturumda Yakalanan Kritik Derleme Kök Nedeni ve Çözümü

PR #30 (`arena/e29a309c-kitsugi-beta`) ve PR #31 (`arena/284d08ba-kitsugi-beta`) dalları doğrudan eski `main` (`PR #23`) üzerinden açıldığı için PR #24 / PR #27 / PR #29'daki mimari dosyaları düşürmüş ve GitHub Actions üzerinde iki sert derleyici hatasına çarpmıştı:
1. **`kotlinc` (`compile_check.sh` — Run `39645302876`):** `58.338` `g(...)` çağrısı tek `BangumiTagDictionary.class` içinde `groupChunk00()`..`groupChunk145()` olarak yazıldığında JVM 64 KB metod sınırını geçse bile sınıf başlığındaki **`u2 constant_pool_count` (sınıf başına en fazla 65.535 sabit)** sınırını aşıp derlemeyi kırıyordu.
2. **Android `aapt2` & Kaynak Birleştirme (`:app:mergeFossDebugResources` — Run `39645302880`):** `bangumi-vault-resources-1..3` modülleri düşmüş, `112.812` `<string>` tek `app/src/main/res/values/strings.xml` içine yazılarak hem mükerrer anahtar (`bangumi_studio_a1_pictures`) üretmiş hem de Android `resources.arsc` tablosunun **APK genelinde tür başına 16-bit (`0x7fTTFFFF` = en fazla 65.535 `<string>`)** sınırını aşmıştı.

**Uygulanan Kalıcı Çözüm (`arena/80e5e83a-kitsugi-beta`):**
- PR #27 + PR #29'daki **4 modüllü Android kaynak yapısı** (`app` + `bangumi-vault-resources-1..3` + `bangumi_vault_keep.xml`) ve **S1–S3 classpath UTF-8 TSV veri deposu** (`app/src/main/resources/bangumi/tags.tsv` + `orgs.tsv`) güncel `main` (`971abf1`, `v2.4.336`) üzerine eksiksiz geri yüklendi.
- PR #30 ve PR #31'de üretilen **tüm `227` kanonik güncelleme** (`canon.tsv`, `org_global_names.tsv`, `map_chunk_*.tsv`) bu mimari üzerinden yeniden üretildi (`+0` yeni XML ID; mevcut shard anahtarlarında `~59 TR / ~56 EN` yerinde güncelleme + `tags.tsv` `58.338` grup + `orgs.tsv` `52.626` satır).
- `KitsugiTranslations.kt` içinde `BangumiOrgDictionary.localizedNameFor(cleaned, turkish)` yedeği ve `"コミックス・ウェーブ・フィルム" -> "CoMix Wave Films"` eşlemesi aktif edildi.
- `KitsugiBangumiDetailClient.kt` içinde oyun geliştirici stüdyoları (`开发`/`開發`), yayımcılar (`发行`/`發行`), Japonca/Çince dergi alanları (`連載雑誌`/`雜誌`/`雑誌`), yayın satırı kanal yerelleştirmesi (`station.toLocalizedOrgName()`) ve dergi kişi/logo zenginleştirmesi (`withStudioPersonIds` içinde `serializations`) tamamlandı.

---

## 1. Uygulama Entegrasyon Mimarisi ve 5 Teknik Sınırlama Koruması

1. **Android `R$string` 64K DEX Alan Sınırı VE `aapt2` 65.535 Kaynak ID Sınırı:**
   - **Yöntem:** `58.451` adet TR/EN `<string>` kaynağı **4 ayrı Gradle modülüne** bölünür (`app`: `17.524`, `bangumi-vault-resources-1`: `13.565`, `bangumi-vault-resources-2`: `13.782`, `bangumi-vault-resources-3`: `13.580`), R8 kaynak küçültmesine karşı `app/src/main/res/raw/bangumi_vault_keep.xml` (`tools:keep="@string/bangumi_*"`) korunur, çalışma zamanında `getIdentifier` + `bangumiTagResourceCache` (`ConcurrentHashMap`) ile okunur ve **yeni XML ID üretilmez (`+0 XML ID`)**. Böylece hem modül başına `R$string` alan sayısı (~13,5K–17,5K) hem de APK genelindeki toplam `string` tablosu (`58.451 < 65.535`) güvenli tavanın altında kalır; kalan `54.116` VAULT anahtarı doğrudan `tags.tsv` / `orgs.tsv` üzerinden sıfır XML ID maliyetiyle çözülür.
2. **JVM 64 KB Metod Boyutu VE 65.535 Sınıf Sabit Havuzu Sınırı (`BangumiTagDictionary.kt`):**
   - **Yöntem:** `58.338` etiket grubu `app/src/main/resources/bangumi/tags.tsv` (`<key>\t<tr>\t<en>\t<etiket>...`) UTF-8 classpath kaynağında tutulur ve `BangumiTagDictionary.kt` tarafından `Context` gerektirmeden `by lazy` ile yüklenir; ayrıca `vault_to_strings.py` içindeki 400'lük `groupChunk` yardımcıları geriye dönük birim test uyumluluğu için korunur.
3. **JVM Constant-Pool 65.535 Bayt UTF-8 Sınırı (`BangumiOrgDictionary.kt`):**
   - **Yöntem:** `52.626` kurum eşlemesi `app/src/main/resources/bangumi/orgs.tsv` (`<etiket>\t<anahtar>\t<en>[\t<tr>]`) UTF-8 classpath kaynağında tutulur; sınıf sabit havuzunda dev string sabiti oluşturulmaz.
4. **Sınıf Yükleme (`<clinit>`) ve Bellek Sınırı (Lazy Çözümleme):**
   - **Yöntem:** Hem `BangumiTagDictionary` hem `BangumiOrgDictionary` veriyi ilk erişimde `by lazy` ile tek geçişte `HashMap` indekslerine (`byLabel`, `byCompact`, `byKey`) açar; uygulama açılışında `<clinit>` maliyeti sıfırdır.
5. **Git / Arena 128 MB Yama Sınırı ve `gen_bangumi_master.py` Koruması:**
   - **Yöntem:** ~128 MB tutan 14 ham JSON/TSV veri dosyası `.gitignore` altında tutulup **Google Drive (`1pZAOYsAnMjveNK6grdXjqV9IDM1TFaJp`)** üzerinden senkronize edilir; `diag/bangumi_master/gen_bangumi_master.py` **daima `--check` bayrağıyla** çalıştırılır.

---

## 2. Görev Listesi Durumu (`TASK-1` .. `TASK-5`)

- [x] **TASK-1 · Google Drive Veri Dosyalarını İndirme:** `1pZAOYsAnMjveNK6grdXjqV9IDM1TFaJp` klasöründeki **48 aktif dosya** taze listelendi ve 14 VAULT veri dosyası indirildi.
- [x] **TASK-2 · Yerel Bütünlük ve Sözlük Senkronizasyon Doğrulaması:** `apply_009_batch.py --mirror-repair`, `apply_map.py --check`, `vault_to_strings.py --check`, `verify_vault.py`, `verify_bangumi_master.py`, `gen_bangumi_master.py --check`, `check_test_expectations.py` (23/23), `check_bangumi_meta_tag_coverage.py` (158/158) ve `unittest` (30/30) sıfır hatayla geçti.
- [ ] **TASK-3 · PR Birleştirme (`main`):** Bu daldan (`arena/80e5e83a-kitsugi-beta`) açılan güncel entegrasyon PR'ı kullanıcı onayıyla `main` dalına merge edilecek; eski ara PR'lar (#31, #30, #29, #27, #26, #25, #24) kapatılabilir.
- [x] **TASK-4 · Android Uygulama Derleme, `kotlinc` ve Runtime Doğrulaması:** `compile_check.sh` (`Main.kt`), `BangumiMasterDictionaryTest` (TR/EN/as_is JUnit testleri), `KitsugiBangumiDetailClientTest` ve 4 modüllü Debug + R8 Release APK + DEX bytecode doğrulaması CI iş akışlarına bağlandı.
- [x] **TASK-5 · Bakım İş Akışı Hazırlığı:** `apply_009_batch.py` hem `--mirror-repair` tekil modunu hem `[--dry-run] [--mirror-repair] <worklist>` modunu destekleyecek şekilde birleştirildi.
