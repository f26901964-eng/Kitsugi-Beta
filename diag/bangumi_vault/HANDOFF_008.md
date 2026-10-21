# DEVİR METNİ — Bangumi VAULT 007 · MİMARİ GEÇİŞ (S1→S4)

> Bu metni yeni daldaki ajana **yapıştır**. Sıfırdan araştırma yapmasın; her şey burada.
> Son güncelleme: **2026-10-21** · Kaynak dal: `arena/ab577289-kitsugi-beta` · **PR #26 (AÇIK, merge edilmedi)**

---

## 0. KİMLİK / DURUM

- Repo: `f26901964-eng/Kitsugi-Beta`
- Kaynak dal (bu metni taşıyan): **`arena/ab577289-kitsugi-beta`**
- Son commit: **`6a508a7`** (önceki: `dc4ab1a`, taban `cbb54c7` = main v2.4.331)
- PR: **#26** https://github.com/f26901964-eng/Kitsugi-Beta/pull/26 — açık, **MERGE EDİLMEDİ**
- Çalışma ağacı temiz. 001–006 tamam ve uygulanmış.
- **Kendi oturum dalında çalış**; başka dala geçme, dal adını değiştirme, `git reset --hard` yasak.
- **Kullanıcı istemeden merge yok.**

---

## 1. İLK 10 DAKİKA (atlanmaz)

### 1a. Dal kurulumu
```bash
git status --short          # kullanıcı değişiklikleri varsa EZME
git fetch --no-tags origin
```
Yeni dalın `main`'den açıldıysa **005c + 006 işi YOKTUR**. Taşı:
```bash
git merge --no-edit origin/arena/ab577289-kitsugi-beta
# "refusing to merge unrelated histories" ALIRSAN → aşağıdaki dosya bazlı taşıma
```

### 1b. ⚠️ DOSYA BAZLI TAŞIMA — `app/` AĞACINI BÜTÜNÜYLE ALMA
Bu tuzak yaşandı: `origin/arena/8a37df7c-kitsugi-beta` (PR #25) dalının `app/` ağacı **eski**
(`appVersionName = 2.4.329`) ve main'in v2.4.331 oynatıcı düzeltmesini **geri alır**
(`YouTubeRequestPolicy`, `YouTubeHlsManifestParser`, `InlinePlayerSupport` **silinir**).

**Doğru taşıma (yalnız VAULT sahipliğindeki dosyalar):**
```bash
git checkout origin/arena/ab577289-kitsugi-beta -- \
  diag \
  bangumi-vault-resources-1 bangumi-vault-resources-2 bangumi-vault-resources-3 \
  app/src/main/res/values/strings.xml \
  app/src/main/res/values-en/strings.xml \
  app/src/main/res/raw/bangumi_vault_keep.xml \
  app/src/main/java/com/kitsugi/animelist/utils/BangumiTagDictionary.kt \
  app/src/main/java/com/kitsugi/animelist/utils/BangumiOrgDictionary.kt \
  gradle/libs.versions.toml \
  settings.gradle.kts
```
Sonra `app/build.gradle.kts`'e **elle** (sürüm 2.4.331 korunur) ekle:
```kotlin
dependencies {
    // Resource-only libraries keep each non-transitive R$string below JVM limits.
    implementation(project(":bangumi-vault-resources-1"))
    implementation(project(":bangumi-vault-resources-2"))
    implementation(project(":bangumi-vault-resources-3"))
    ...
```

### 1c. Oku (sırayla)
1. `diag/bangumi_vault/007_MIMARI_ARASTIRMA.md` — **BU TURUN EN ÖNEMLİ DOSYASI** (aşağıda özeti var)
2. `diag/bangumi_vault/PROGRESS.md` — §0 kurulum, §2k (006), §2d (kümülatif), §5 (kuyruk), §6 (dersler), §8
3. `diag/bangumi_vault/HANDOFF_007.md` — **ama şu sayıları güncel sayma:** orada "17.471 + 12.646/12.915/12.708 = 55.740" yazıyor, **YANLIŞ**. Gerçek: **17.471 + 13.565/13.782/13.580 = 58.398**.

### 1d. Büyük dosyaları AÇMA
`vault.json` / `tags.json` / `progress.json` **`read_file` ile açılmaz**. Python'la yalnız gerekli alanları oku.
Drive'dan indirilecekse `google_drive` connector `download_file` kullan (içeriği bağlama dökmez).

---

## 2. DEVRALDIĞIN DURUM (ölçülmüş — doğrulandı)

| Ölçü | Değer |
|---|---|
| `bangumi_*` anahtar | **52.219** |
| vault kaydı | **51.104** |
| map satırı | **61.398** |
| damga denetimi | **121.284** |
| canon satırı | **55.968** |
| BangumiTagDictionary | **3.983 grup**, 10 `groupChunkNN()` (≤400/parça), **14.623 benzersiz literal** |
| BangumiOrgDictionary | **52.176** satır (66 RAW parçası, en büyük **49.999** MUTF-8 bayt) |
| `R.string` toplam kaynak | **58.398** / 65.535 tür tavanı → **7.137 boş** |
| app modülü | 17.471 string (**11.292** `bangumi_*`) / kendi muhafızı 20.000 |
| shard'lar | **13.565 / 13.782 / 13.580** / 16.000 |
| TR≠EN olan `bangumi_*` | **1.217** (%2,3) — 1.098'i etiket, kurumlarda yalnız 8 |

**Otorite cümle (birebir bu çıkmalı):**
```
✅ TÜM KONTROLLER GEÇTİ — 52219 bangumi_* anahtar, 51104 vault kaydı, 61398 map satırı, 121284 damga denetlendi
```
Bu çıkmadan yeni işe başlama. `apply_map.py --check` **tek başına** eski/eksik damgayı yakalamaz.

> **Veri restorasyonu:** Drive klasörü `1pZAOYsAnMjveNK6grdXjqV9IDM1TFaJp`. Ham kopya 122.277 eski damga
> taşır → `apply_map.py` (damgalama) çalıştırmadan `verify_vault.py` **kırmızı** verir. Bu normaldir, bozulma değil.

---

## 3. NE BİTTİ (YENİDEN İŞLEME)

001, 002, 003, 004a/b/c, 005a/b, 005c_a/b/c, **006 (etiket count 20–99, 4.027 kayıt)** — hepsi tamam.
`diag/bangumi_vault/decisions/006_src/` yeniden üretim kaynağıdır.

**Bu turda ayrıca yapıldı (tekrar etme):**
- 005c+006 işi bu dala taşındı (`dc4ab1a`)
- Tam doğrulama zinciri koşuldu (aşağıdaki 5. bölümdeki sayılar)
- **Kapasite araştırması yapıldı** + karar verildi (`007_MIMARI_ARASTIRMA.md`)
- `build_tag_store.py` + `test_tag_store.py` yazıldı → testler **23 → 27/27**

---

## 4. 🔴 ASIL İŞ: MİMARİ GEÇİŞ (S1 → S4)

### 4.1 Sorun (özet)
İki ayrı sert tavan var ve **007 ikisine de çarpıyor**:

| Tavan | Nerede | Sınır | Bugün |
|---|---|---:|---:|
| Android kaynak ID | kaynak tablosu (`0xPPTTEEEE`) | **65.535 / tür** | 58.398 |
| JVM sabit havuzu | `BangumiTagDictionary.kt` sınıfı | **65.535 / sınıf** | 14.623 literal |

007 = **59.510 aday**; 52.476'sı yeni kayıt. `as_is` politikasıyla bu **52.476 yeni kaynak ID'si** ister → **tavanın 7 katı**.
Shard eklemek `R$string` sınıfını küçültür ama **65.535 tavanını kaldırmaz**.

### 4.2 Karar (verildi, tartışma yok)
**Etiket sözlüğü Kotlin kaynağından çıkıp VERİ dosyasına taşınır.** Sektörün yaptığı budur
(resmî Room `createFromAsset`, ICU/StarDict mmap, Compose MP işaroundları — kaynaklar `007_MIMARI_ARASTIRMA.md` §10).

```
canon.tsv ──► build_tag_store.py ──► app/src/main/resources/bangumi/tags.tsv
                                       (bugün 258 KB, 007 sonrası ~3,7 MB / gzip ~1 MB)
çalışma zamanı: BangumiTagDictionary.groups = TagStore.load()   // classpath
                byLabel / byCompact / entryFor / patternEntry   // DEĞİŞMEZ
```

**Neden `assets/` değil classpath:** `BangumiTagDictionary` bir `object`'tir ve Context almaz.
`src/main/resources` hem APK'da hem **JVM birim testlerinde** aynı yoldan okunur → `BangumiMasterDictionaryTest`
değişmeden çalışır. R8 classpath kaynaklarını silmez.

**Gösterim zinciri ZATEN uygun** (koddan doğrulandı):
- Etiket: `bangumi_tag_<key>` kaynağı → yoksa **sözlük değeri** → yoksa ham etiket
- Kurum: `bangumi_<rol>_<key>` kaynağı → yoksa **küresel Latin ad** → yoksa ham ad

Yani **görünen TR/EN değişmez**, `bangumi_tag_<canon>` gösterim kaynağı korunur, kaynaklar override katmanı olarak kalır.

### 4.3 Adımlar

| Adım | İş | Durum |
|---|---|---|
| **S0** | `build_tag_store.py` + `test_tag_store.py` | ✅ **BİTTİ** (27/27 test, TSV turu birebir) |
| **S1** | `app/src/main/resources/bangumi/tags.tsv` üret; `--check` birebir | ☐ SIRADAKİ |
| **S2** | `BangumiTagDictionary.kt` → yükleyici (≈3.900 `g()` satırı silinir) | ☐ |
| **S2b** | Aynı işlem `BangumiOrgDictionary.kt` için (52.176 satır → `orgs.tsv`) | ☐ |
| **S3** | `vault_to_strings.py` / `verify_bangumi_master.py` / `gen_bangumi_master.py` TSV okur-yazar | ☐ |
| **S4** | 007 bantları + count=0 kurumlar; kaynak **hiç büyümez** | ☐ |

**S1 komutu:**
```bash
python3 diag/bangumi_vault/scripts/build_tag_store.py --from-kotlin \
    --out app/src/main/resources/bangumi/tags.tsv --stats
python3 diag/bangumi_vault/scripts/build_tag_store.py --from-kotlin \
    --out app/src/main/resources/bangumi/tags.tsv --check   # ✅ birebir beklenir
```
Beklenen: `grup: 3,983 | etiket: 9,397 | TSV 257,847 bayt | gzip 101,217 bayt`

**TSV biçimi:** `key \t tr \t en \t etiket [\t etiket...]` — **sıra KORUNUR** ("ilk yazım kazanır"
semantiği sıraya bağlı; 63 gölgelenme vakası var, `test_tag_store.py` bunu sabitliyor).

**S2'de dikkat:** `verify_bangumi_master.py` ve `gen_bangumi_master.py` de TSV okuyacak şekilde
**aynı anda** güncellenmeli; aksi hâlde doğrulayıcı **0 grup** okur (sessiz hata).

### 4.4 Ölçülmüş gerekçe
```
bugün : 3.983 grup / 9.397 etiket → 377 KB Kotlin  vs  258 KB TSV (gzip 101 KB)
007   : 70.764 etiket            → ~3,7 MB TSV (gzip ~1,0 MB)
karşılaştırma: aynı veri kaynak olarak 52.476 yeni ID isterdi (tavan 65.535)
```

### 4.5 Geçiş doğrulama kapıları
1. `build_tag_store.py --check` → birebir
2. `python3 -m unittest discover -s diag/bangumi_vault/scripts -p 'test_*.py'` → **27/27**
3. `verify_bangumi_master.py` → **önce/sonra aynı**: 3.983 grup, 52.176 eşleme, TR/EN 58.398
4. `verify_vault.py` → otorite cümlesi **birebir**
5. `gen_bangumi_master.py --check` → +0/+0
6. `vault_to_strings.py --check` → +0/+0 (idempotent)
7. CI `verify` işi yeşil (aşağıdaki 7. bölüm)

---

## 5. 007 BANT PLANI (karar hazır — uygulanacak)

`tags.json` içinde **kararsız 59.510** kayıt (en yüksek count 19):

| Bant | count | Kayıt | Mevcut anahtara bağlanabilir | Eşleşmeyen |
|---|---|---:|---:|---:|
| a | 19 | 225 | 48 | 177 |
| b | 15–18 | 1.179 | 209 | 970 |
| c | 11–14 | 1.892 | 304 | 1.588 |
| d | 8–10 | 2.253 | 332 | 1.921 |
| e | 6–7 | 2.373 | 324 | 2.049 |
| f | 4–5 | 4.437 | 562 | 3.875 |
| g | 3 | 4.275 | 553 | 3.722 |
| h | 2 | 8.666 | 956 | 7.710 |
| i | 1 | 33.169 | 2.751 | 30.418 |
| j | **count=0** | 1.041 | 933 | 108 |

- **j ayrı bir veri kümesi:** `count`/`source` alanı yok, TR değerleri hazır (AniList/Kitsu tarzı), %90'ı kaynakta var → en ucuz bant.
- Bant sırası: **a → b → c → d → e → f → g → h → i → j**. Her bant ayrı `map_chunk_007_x_tags.tsv` + ayrı kapanış zinciri.
- Kararlar `decisions/007_src/` altında 006 ile aynı biçimde saklanır (yeniden üretilebilirlik).
- **Kırpma yok, SKIP'e zorlama yok, "count=1'i atla" yok.** `as_is` kuralı **aynen** korunur.
- `count=0` **kurumlar (376)** etiket işinden bağımsız ayrı kuyruk.
- **007 bitmeden "Bangumi bitti" deme.**

### Her bantta karar kuralları (değişmedi)
- `BangumiTagDictionary` + `canon.tsv` + **TÜM TR/EN kaynak kataloğu** karşılaştırılır
  (`scripts/resource_catalog.py`; `app/strings.xml`'e bakıp "çeviri eksik" deme).
- Global karşılık varsa mevcut kanona bağla; genel tür/tema/biçim etiketlerine TR+EN;
  bilinen özel adlarda yalnız güvenilir **resmî Latin yazım**; bilinmeyenler `as_is`
  (`cjk_<md5(value)[:6]>`); **romanizasyon uydurma**.
- Jenerik gürültü / kişisel raf / bozuk parça → **SKIP** (map'e ve `translations_done.tsv`'e yazılmaz).
- Normalize ikiz + değer tekrarı taranır; `cid`/`slug`/`hash` tek başına varlık kanıtı **değil**;
  `(rol, canon_id)` yinelenmez; görünen TR/EN değişmez; **"ilk yazım kazanır" gölgelenme denetimi atlanmaz**.
- Komite önekli key'lerde rol **JSON `role` alanından** (`bangumi_producer_` önekinden tahmin YOK).
- **Kısa cid tuzağı:** `d.e` kullan; slug eşleşmesi yanıltıcı (`机动战士高达AGE`→`age`, `Disney+`→`disney`).

---

## 6. KAPANIŞ ZİNCİRİ (her bant, bu sırayla)

```bash
python3 diag/bangumi_vault/scripts/apply_map.py --check
python3 diag/bangumi_vault/scripts/apply_map.py
python3 diag/bangumi_vault/scripts/vault_to_strings.py
python3 diag/bangumi_vault/scripts/verify_vault.py
python3 diag/bangumi_master/verify_bangumi_master.py
python3 diag/bangumi_master/gen_bangumi_master.py --check
python3 -m unittest discover -s diag/bangumi_vault/scripts -p 'test_*.py'
```

- ⚠️ **`gen_bangumi_master.py` ASLA `--check` olmadan çalıştırılmaz** — VAULT RAW satırlarını **siler** (720 satır kaybolur).
- `vault_to_strings.py --check` **idempotent** olmalı (+0/+0). Grup parçalarını kendisi dengeler.
- Bant başına: `PROGRESS.md` (durum + kümülatif + kutular + dersler), `progress.json` (tarihsel metadata korunur),
  `translations_done.tsv` (append), **Drive yükleme**, commit + push + PR; PR numarası devir dosyasına işlenir.
- `decisions/*.tsv` için **GİT esastır**; Drive kopyasıyla üzerine yazma.

---

## 7. GERÇEK CI DURUMU (ölçüldü — varsayım değil)

| Koşu | Dal | Sonuç |
|---|---|---|
| **39591611429** | `arena/ab577289-kitsugi-beta` (PR #26) | `verify` ✅ · `compile-and-test` ❌ (`🧪 BangumiMasterDictionaryTest`) |
| **39591596035** | aynı dal (Android Derleme) | `build-debug` ❌ — adım: "MAL … testleri + APK + DEX" |
| 39590769306 | PR #25 | `verify` ✅ · `compile-and-test` ❌ **aynı adım** |
| 39579279060 | **`main`** | `verify` ✅ · `compile-and-test` ❌ **aynı adım** |
| 39585497128 | `main` (Android) | `build-debug` ❌ **aynı adım** |

- `BangumiMasterDictionaryTest` kırığı **`main`'de de var** → 006/007 kaynaklı regresyon değil; **ama kanıtlanmadı**:
  job logu `productionresultssa6.blob.core.windows.net` imzalı URL'sine yönleniyor ve sandbox'ta **`EOF`** veriyor;
  `check-runs.output` **null**; `bangumi-master-test-log` artefaktı da aynı engelde. **"Aynı hatadır" deme.**
- **CI'yı "yeşil" diye devretme.**
- `MalWebParserTest` / `MalNotificationsClientTest` ile **MAL kodu değiştirilmez**.

---

## 8. ORTAM TUZAKLARI

- **Sandbox'ta JDK/kotlinc/gradle YOK** (`which` boş). "Derlendi" deme; derlemeyi CI yapar.
- Drive'a yalnız **`google_drive` connector** ile erişilir (curl/SSL çalışmıyor). Şema: `content_mime_type`
  (≠ `mime_type`), `supports_all_drives: true`; argümanları `input` ile sarmala. Değişen dosyada
  `delete_file` → `upload_file`. **Dosya kimlikleri her yüklemede yenilenir** → klasör listesinden **ada göre** al.
- `.gitignore`: `diag/bangumi_vault/*.json`, `*.tsv`, `/google_drive/`, `worklists/` **commit edilmez**;
  `google_drive/` indirme bitince silinir.
- `draft_org_map.py` yalnız **kurum** taslağı üretir (`--min-count` zorunlu); etiket çevirisinin yerine geçmez.
  **Damgalı kayıtlar taslakta atlanır** (005b tuzağı: damgalama sonrası aynı bantta boş map).
- `build_tag_chunk.py --write` aynı cid ile ikinci kez çalışırsa "yinelenen (rol, canon_id)" verir;
  `emitted` seti + `git checkout -- canon.tsv` ile geri dön.
- **Korunacaklar:** `android.nonTransitiveRClass=true`; 3 modülün `settings.gradle.kts` + `app/build.gradle.kts`
  bağlantıları; `app/src/main/res/raw/bangumi_vault_keep.xml` içindeki `tools:keep="@string/bangumi_*"`;
  çalışma zamanı `getIdentifier(name,"string",context.packageName)` yolu.
- `apply_map.py` sonrası vault JSON'larına damga düşer; **ham yedek** `diag/bangumi_vault/worklists/raw_007/`
  altında (gitignore'da).

---

## 9. RAPOR BİÇİMİ (kullanıcı şart koştu)

Üç başlık **ayrı ayrı**:
- **(a) yerel doğrulama** — bu sandbox'ta koşan komutların çıktısı
- **(b) gerçek CI** — koşu numarası + hangi iş geçti/kaldı
- **(c) kalan test engelleri**

CI'yı "yeşil" diye devretme; incelemeden "aynı hatadır" varsayma.

---

## 10. SENDEN BEKLENEN İLK ÇIKTI (hiçbir şeyi değiştirmeden)

1. `git status --short`, `git log --oneline -3`
2. `apply_map.py --check` çıktısı
3. `verify_vault.py` çıktısı (otorite cümlesi **birebir** çıkıyor mu?)
4. `verify_bangumi_master.py` + `gen_bangumi_master.py --check` + `unittest` sonucu (**27/27** beklenir)
5. **S1 planı**: `tags.tsv` üretimi + `--check` birebirliği için atacağın adımlar

Bunlar gelmeden S2'ye başlama.

---

## 11. KULLANICI TALEBİ (bağlam — bunu unutma)

Kullanıcı **kırpma/erteleme istemiyor**. Açık ifadesi: *"bu dediklerin ile olsun istemiyorum, bu konuyu
başka uygulama geliştiricileri nasıl çözmüş araştırıp bakman ve adam akıllı eksik kalmadan çözebilmek
istiyorum."* Yani: **hiçbir kayıt bırakılmayacak**, `as_is` politikası korunacak, tavan gerçekten kaldırılacak.
