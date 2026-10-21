# HANDOFF 011 — Bangumi VAULT & Sözlük Uygulama Entegrasyon Planı ve Görev Listesi (2026-10-21)

Bu belge, **Chunk 001–009** ve **Batch 008 kapsamlı çapraz rol/etiket taraması (`8a5c267`)** sonunda tamamlanan Bangumi etiket, tür, stüdyo, yapımcı, yayıncı ağ, yayımcı ve dergi yerelleştirme/küresel ad verilerinin **Kitsugi-Beta Android uygulamasına tam entegrasyonu** ve yeni bir çalışma dalında (branch) kaldığı yerden kesintisiz devam edebilmesi için hazırlanmış tek otoriter plan ve görev (task) dosyasıdır.

---

## 0. Güncel Durum Özeti (Tek Bakışta)

- **Aktif Git Dalı & Son Commit:** `arena/284d08ba-kitsugi-beta` @ `8a5c267` (`diag(bangumi): apply 008 comprehensive org and tag global/concept batch (196 rows)`)
- **Açık Pull Request:** **PR #31** (`https://github.com/f26901964-eng/Kitsugi-Beta/pull/31`, base: `main`, head: `arena/284d08ba-kitsugi-beta`)
- **Google Drive Otoriter Klasörü:** `1pZAOYsAnMjveNK6grdXjqV9IDM1TFaJp` (**47 aktif dosya**, en son `8a5c267` durumuyla senkronize)
- **Doğrulanmış Metrikler (`2026-10-21`):**
  - `verify_vault.py`: ✅ **106.580 `bangumi_*` anahtarı · 106.335 vault kaydı · 121.284 map satırı · 121.284 damga**
  - `verify_bangumi_master.py`: ✅ **`BangumiTagDictionary`: 58.338 grup · `BangumiOrgDictionary`: 52.626 eşleme (67 RAW parçası, ≤ 50.000 bayt) · GERÇEK çakışma: 0 · `strings.xml`: TR 112.812 / EN 112.812 simetrik**
  - `gen_bangumi_master.py --check`: ✅ **`+0 grup, +0 etiket, +0 kayıt` (tam senkron)**
  - `check_bangumi_meta_tag_coverage.py`: ✅ **158 / 158 resmî Bangumi meta etiketi kapsanıyor**
  - **`canon.tsv` Karar Dağılımı (`111.310` kanonik satır):**
    - `global`: **14.398** (`magazine`: 451, `network`: 233, `producer`: 586, `publisher`: 711, `studio`: 346, `tag`: 12.071)
    - `existing`: **6.119** (`magazine`: 123, `network`: 169, `producer`: 1.533, `publisher`: 2.446, `studio`: 924, `tag`: 924)
    - `new`: **1.492** (`magazine`: 2, `network`: 50, `tag`: 1.440)
    - `as_is`: **89.301** (`magazine`: 2.783, `network`: 1.602, `producer`: 21.857, `publisher`: 8.808, `studio`: 10.348, `tag`: 43.903; bunun içinde `007_b`–`007_i` CJK `as_is` etiket havuzu: **43.022** — *çeviri kotası değildir; doğrulanmamış CJK özel adlar ve fandom jargonu uydurma romanizasyon üretilmeden korunur*)

---

## 1. Uygulama Entegrasyon Mimarisi (Android / Kotlin / XML)

Bangumi verileri uygulama içinde **4 katmanlı statik + kaynak mimarisi** ile çalışır ve tüm katmanlar mevcut dalda (`8a5c267`) üretilmiş ve doğrulanmış durumdadır:

### 1.1. Çok Modüllü Android Dil Kaynakları (`strings.xml` Parçaları)
Android `R$string` sınıfında 64K alan (DEX field) sınırına takılmamak için `112.812` adet TR/EN string kaynağı 4 modüle bölünmüştür:
1. `app/src/main/res/values/strings.xml` & `values-en/strings.xml` (Uygulama arayüz metinleri + MASTER & temel `bangumi_*` kaynakları)
2. `bangumi-vault-resources-1/src/main/res/values/strings.xml` & `values-en/strings.xml`
3. `bangumi-vault-resources-2/src/main/res/values/strings.xml` & `values-en/strings.xml`
4. `bangumi-vault-resources-3/src/main/res/values/strings.xml` & `values-en/strings.xml`

> **Önemli Kural:** Mevcut XML anahtarları (`bangumi_tag_*`, `bangumi_studio_*`, `bangumi_producer_*`, `bangumi_publisher_*`, `bangumi_magazine_*`, `bangumi_network_*`) kilitlidir; yeni XML ID üretilmez, güncellemeler mevcut anahtarların TR/EN değerleri üzerinde yerinde yapılır.

### 1.2. Etiket & Tür Sözlüğü (`BangumiTagDictionary.kt`)
- **Konum:** `app/src/main/java/com/kitsugi/animelist/utils/BangumiTagDictionary.kt`
- **Yapı:** **58.338** kanonik etiket grubu. JVM 64 KB metod boyutu sınırını aşmamak için gruplar `groupChunk00()` .. `groupChunk145()` yardımcı fonksiyonlarına (≤ 400 giriş/parça) bölünmüştür.
- **Çalışma Zamanı (Runtime) Akışı (`KitsugiTranslations.kt`):**
  1. `String.toLocalizedTagLabel()` / `String.toLocalizedBangumiTagOrNull()` çağrılır.
  2. `BangumiTagDictionary.entryFor(label)` ile gelen Çince/Japonca/İngilizce etiket kanonik `key` değerine çözülür (`ilk yazım kazanır` kuralı).
  3. Arayüz dili Türkçe (`tr`) veya İngilizce (`en`) ise `getStringResourceByName("bangumi_tag_${tag.key}")` ile `strings.xml` kaynağı (`bangumiTagResourceCache` `ConcurrentHashMap` önbelleği üzerinden) okunur.
  4. Dil dosyasında bulunamazsa veya farklı bir arayüz dilindeyse `tag.turkish` / `tag.english` yedek değeri döner.
  5. Eşleşme yoksa orijinal metin bozulmadan döner (**uydurma çeviri üretilmez**).
- **Spoiler Koruması (`KitsugiSpoilerRules.kt`):** `BangumiTagDictionary.entryFor(...)` üzerinden doğal spoiler etiketlerini CJK/EN/TR dillerinin tamamında yakalar.

### 1.3. Kurum, Yapımcı, Yayıncı Ağ, Yayımcı ve Dergi Sözlüğü (`BangumiOrgDictionary.kt`)
- **Konum:** `app/src/main/java/com/kitsugi/animelist/utils/BangumiOrgDictionary.kt`
- **Yapı:** **52.626** eşleme satırı (`etiket|bangumi_<rol>_<key>|Küresel Latin Ad`). JVM constant-pool UTF-8 sınırını (65.535 bayt) aşmamak için `RAW` listesi **67 parçaya** (her biri ≤ 50.000 modified-UTF-8 bayt) bölünmüştür.
- **Çalışma Zamanı (Runtime) Akışı (`KitsugiTranslations.kt` & `BangumiOrgResolver.kt`):**
  1. `String.toLocalizedOrgName()` / `String.toLocalizedOrgNameOrNull()` çağrılır.
  2. `BangumiOrgDictionary.resourceKeyFor(cleaned)` (veya `toLatinStudioName()` sonrası ikinci tur lookup) ile `bangumi_<rol>_<key>` kaynak adı bulunur.
  3. TR/EN arayüzde `getStringResourceByName(key)` ile `strings.xml` değeri okunur.
  4. Kaynak yoksa `BangumiOrgDictionary.latinNameFor(cleaned)` → `toLatinStudioName()` → orijinal metin döner. Üretim komiteleri (`製作委員会` vb.) ve doğrulanmamış CJK özel adlar olduğu gibi korunur.

### 1.4. Aşılan 5 Garip Teknik Sınırlama ve Korunması Zorunlu Çözüm Yöntemlerimiz
Uygulamaya bu ölçekte (`106.580` `bangumi_*` anahtarı, `58.338` etiket grubu, `52.626` kurum eşlemesi) veri entegre edilirken karşılaşılan derleyici/런타임/depo sınırlamaları ve uyguladığımız kalıcı çözümler şunlardır (**yeni oturumda bu yapıların hiçbirini bozma**):

1. **Android `R$string` 64K DEX Alan Sınırı (`TooManyFieldsException` / 65.535 Field Limit):**
   - **Sorun:** `112.812` adet TR/EN `<string>` kaynağı tek `app` modülünde toplandığında Android derleyicisi `R$string` sınıfında 65.535 alan sınırını aşıp derlemeyi kırıyordu.
   - **Uygulanan Yöntem:** Dil kaynakları **4 ayrı Gradle modülüne (shard)** bölündü (`app` + `bangumi-vault-resources-1` + `bangumi-vault-resources-2` + `bangumi-vault-resources-3`), `nonTransitiveRClass` korundu ve çalışma zamanında `R.string.*` sabit referansı yerine `context.resources.getIdentifier(name, "string", context.packageName)` + `ConcurrentHashMap` önbelleği (`bangumiTagResourceCache`) kullanıldı. Ayrıca **yeni XML ID üretmeme (`+0 XML ID`)** kuralıyla modül başına alan sayısı sabitlendi.
2. **JVM 64 KB Metod Boyutu Sınırı (`MethodTooLargeException` — `BangumiTagDictionary.kt`):**
   - **Sorun:** `58.338` etiket grubu tek bir `listOf(...)` veya `<clinit>` metodunda tanımlandığında JVM 64 KB bytecode sınırına takılıyordu.
   - **Uygulanan Yöntem:** `vault_to_strings.py`, `BangumiTagDictionary.kt` içindeki grupları her biri en fazla **400 grup** içeren `groupChunk00()` .. `groupChunk145()` (`146` ayrı yardımcı metod) parçalarına böler ve `buildList` ile birleştirir.
3. **JVM Constant-Pool 65.535 Bayt UTF-8 String Sınırı (`BangumiOrgDictionary.kt` RAW):**
   - **Sorun:** `52.626` kurum eşleme satırı tek bir Kotlin string sabitinde tutulduğunda JVM `CONSTANT_Utf8_info` **65.535 modified-UTF-8 bayt** sınırını aşıyordu (CJK karakterler modified-UTF-8'de 3 bayt kaplar).
   - **Uygulanan Yöntem:** `BangumiOrgDictionary.kt` içindeki `RAW` verisi `listOf(...)` altında **67 parçaya** bölündü ve her parça kesin olarak **≤ 50.000 modified-UTF-8 bayt** sınırının altında tutuldu (`verify_bangumi_master.py` her parçanın bayt boyutunu denetler).
4. **Sınıf Yükleme (`<clinit>`) ve Bellek Tüketimi Sınırı (Sıkıştırılmış `RAW` + Lazy Çözümleme):**
   - **Sorun:** `52.626` kurum eşlemesini derleme zamanında ayrı Kotlin nesneleri olarak üretmek hem `.dex` boyutunu hem sınıf yükleme süresini şişiriyordu.
   - **Uygulanan Yöntem:** Kurum eşlemeleri `etiket|bangumi_<rol>_<key>|Latin Ad` biçiminde sıkıştırılmış metin satırları olarak tutulur ve ilk kullanımda `by lazy` ile bellekteki arama haritasına açılır.
5. **Git / Arena 128 MB Yama Sınırı ve `gen_bangumi_master.py` Üzerine Yazma Koruması:**
   - **Sorun A:** Toplamı ~130 MB olan 14 ham VAULT JSON/TSV dosyası (`vault.json`, `tags.json` vb.) Git geçmişini ve Arena yama sınırını aşıyordu.
   - **Uygulanan Yöntem A:** 14 büyük veri dosyası `.gitignore`'da tutulup **Google Drive (`1pZAOYsAnMjveNK6grdXjqV9IDM1TFaJp`)** üzerinden senkronize edilir; Git'te yalnız derlenen `.kt` ve `strings.xml` dosyaları ile `canon.tsv` karar tablosu bulunur.
   - **Sorun B:** `gen_bangumi_master.py` `--check` olmadan çalıştırılırsa `BangumiOrgDictionary.kt` şablondan yalnız `orgs.tsv` ile yeniden yazılıp VAULT'un 51.600+ RAW satırını siler.
   - **Uygulanan Yöntem B:** Tüm güncellemeler yalnız `apply_009_batch.py --mirror-repair` → `apply_map.py` → `vault_to_strings.py` boru hattıyla yapılır; `gen_bangumi_master.py` **daima `--check` bayrağıyla** çalıştırılır.


---

## 2. Uygulamaya Entegrasyon Görev Listesi (TASK Planı)

Yeni oturumda veya yerel geliştirme ortamında entegrasyonu tamamlamak için izlenecek adım adım görev planı:

### TASK-1 · Google Drive Veri Dosyalarını Yerel Çalışma Alanına İndirme (Oturum Başlangıcı)
Git deposunda yalnız kod (`*.kt`), dil kaynakları (`strings.xml`), `canon.tsv`, `org_global_names.tsv`, `PROGRESS.md` ve `HANDOFF_*.md` tutulur; büyük JSON/TSV VAULT dosyaları `.gitignore` altındadır ve Google Drive klasöründe (`1pZAOYsAnMjveNK6grdXjqV9IDM1TFaJp`) saklanır.
- **Adım 1.1:** Google Drive `1pZAOYsAnMjveNK6grdXjqV9IDM1TFaJp` klasörünü `list_files` ile taze listele.
- **Adım 1.2:** Aşağıdaki 14 veri dosyasını `diag/bangumi_vault/` dizinine indir:
  - `manifest.json`, `progress.json`, `translations_pending.tsv`, `translations_done.tsv`, `genres.json`
  - `vault.json`, `tags.json`, `records_007_tags.json`
  - `studios.json`, `producers.json`, `publishers.json`, `magazines.json`, `networks.json`, `committees.json`

### TASK-2 · Yerel Bütünlük ve Sözlük Senkronizasyon Doğrulaması
Uygulama derlemesine veya PR birleştirmesine geçmeden önce 4 doğrulayıcı betiğin tamamının yeşil (`0` hata) olduğundan emin ol:
```bash
python3 diag/bangumi_vault/scripts/apply_map.py --check
python3 diag/bangumi_vault/scripts/verify_vault.py
python3 diag/bangumi_master/verify_bangumi_master.py
python3 diag/bangumi_master/gen_bangumi_master.py --check
python3 scripts/check_bangumi_meta_tag_coverage.py
```
**Beklenen Çıktılar:**
- `verify_vault.py`: `✅ TÜM KONTROLLER GEÇTİ — 106580 bangumi_* anahtar, 106335 vault kaydı, 121284 map satırı, 121284 damga denetlendi`
- `verify_bangumi_master.py`: `BangumiTagDictionary: 58338 grup`, `BangumiOrgDictionary: 52626 eşleme satırı`, `RAW: 67 parça`, `GERÇEK çakışma 0`, `strings.xml: TR 112812 · EN 112812 kayıt (simetri OK)`
- `gen_bangumi_master.py --check`: `+0 grup, +0 etiket, +0 kayıt`
- `check_bangumi_meta_tag_coverage.py`: `kontrol edilen resmî meta etiket: 158 / eksik: 0`

> ⚠️ **KRİTİK UYARI:** `diag/bangumi_master/gen_bangumi_master.py` betiğini **asla `--check` olmadan çalıştırma**! `--check` olmadan çalıştırılırsa `BangumiOrgDictionary.kt` şablondan yalnız `orgs.tsv` baz alınarak baştan yazılır ve VAULT'un eklediği ~51.600 RAW eşleme satırı silinir.

### TASK-3 · PR #31'in `main` Dalına Birleştirilmesi (Merge) ve Dal Temizliği
- **Adım 3.1:** PR #31 (`arena/284d08ba-kitsugi-beta` @ `8a5c267`+) zaten `main` (`971abf1`) tabanlıdır ve `main` üzerindeki güncel MAL bildirim/geçmiş değişikliklerini korur.
- **Adım 3.2:** PR #31'i `main` dalına merge et (`gh pr merge 31 --merge` veya GitHub arayüzünden).
- **Adım 3.3:** Eski oturumdan kalan ve PR #31 tarafından kapsanan PR #30 (`arena/e29a309c-kitsugi-beta`) artık gereksizse kapatılabilir.

### TASK-4 · Android Uygulama Derleme ve Arayüz (Runtime) Doğrulaması
- **Adım 4.1:** Android kaynak birleştirme ve R sınıfı üretimini doğrula:
  ```bash
  ./gradlew :bangumi-vault-resources-1:compileDebugLibraryResources \
            :bangumi-vault-resources-2:compileDebugLibraryResources \
            :bangumi-vault-resources-3:compileDebugLibraryResources \
            :app:processFossDebugResources
  ```
- **Adım 4.2:** Kotlin sözlük derlemesini doğrula:
  ```bash
  bash diag/bangumi_master/harness/compile_check.sh
  ```
- **Adım 4.3:** Uygulama içi ekran kontrolleri (Detay sayfası, Arama/Filtre çipleri, Stüdyo/Yapımcı/Dergi rozetleri):
  - **Türkçe arayüzde (`Locale("tr")`):** Kavram etiketlerinin Türkçe (`Sessiz Film`, `Gotik Lolita`, `Doğa Belgeseli`, `Taktiksel Nişancı`, `Souls Benzeri` vb.), küresel kurum/kişi/marka adlarının ise resmî Latin yazımıyla (`Kyoto Animation`, `Masayuki Aoyagi`, `Tomonori Ochikoshi`, `Overlap Bunko`, `Monthly Wings`, `CoMix Wave Films` vb.) göründüğünü doğrula.
  - **İngilizce arayüzde (`Locale("en")`):** Kavram etiketlerinin İngilizce (`Silent Film`, `Gothic Lolita`, `Nature Documentary`, `Tactical Shooter`, `Souls-like` vb.), küresel kurum/kişi/marka adlarının aynı resmî Latin yazımıyla göründüğünü doğrula.
  - **Doğrulanmamış CJK özel adlar ve komiteler (`as_is`):** `森下勝司` veya `Clannad製作委員会` gibi kayıtların uydurma romanizasyon olmadan orijinal CJK metniyle korunduğunu doğrula.

### TASK-5 · İsteğe Bağlı Ek Kürasyon / Bakım İş Akışı (Yeni Veri Eklenecekse)
İleride `as_is` havuzundan yeni bir resmî global ad veya çeviri eklenecekse **yalnızca** şu standart boru hattı kullanılır:
1. Kararları `diag/bangumi_vault/worklists/009_decisions_<ad>.tsv` dosyasına `# role\tcanon_id\told_tr\tnew_tr\tnew_en\tnew_origin` formatında yaz.
2. Önce `--dry-run`, sonra gerçek çalıştır:
   ```bash
   python3 diag/bangumi_vault/scripts/apply_009_batch.py --dry-run --mirror-repair diag/bangumi_vault/worklists/009_decisions_<ad>.tsv
   python3 diag/bangumi_vault/scripts/apply_009_batch.py --mirror-repair diag/bangumi_vault/worklists/009_decisions_<ad>.tsv
   ```
3. Harita ve dil dosyalarını güncelle:
   ```bash
   python3 diag/bangumi_vault/scripts/apply_map.py --check
   python3 diag/bangumi_vault/scripts/apply_map.py
   python3 diag/bangumi_vault/scripts/vault_to_strings.py
   ```
4. TASK-2'deki doğrulayıcıları çalıştır, `translations_done.tsv` + `progress.json` + `PROGRESS.md` + `HANDOFF_011_APP_INTEGRATION_PLAN_AND_TASKS.md` dosyalarını güncelle ve Google Drive `1pZAOYsAnMjveNK6grdXjqV9IDM1TFaJp` klasörüne (`list_files` → `delete_file` → `upload_file` → `list_files`) senkronize et.
