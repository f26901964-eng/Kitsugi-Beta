# Bangumi VAULT — HANDOFF 009 (S1–S3 Sonrası Derin `as_is` Global Eşleştirme & TR/EN Çeviri Rehberi)

> **Tarih:** 2026-10-21
> **Kaynak Dal & Son Commit:** `arena/b4827842-kitsugi-beta` · **PR #27** (`https://github.com/f26901964-eng/Kitsugi-Beta/pull/27`)
> **Google Drive Klasörü:** `1pZAOYsAnMjveNK6grdXjqV9IDM1TFaJp` (tüm 25 veri + map dosyası güncel)

---

## 1. Şu Ana Kadar Tamamlanan Altyapı ve Durum Özeti

1. **S1–S3 Classpath TSV Mimari Geçişi (%100 Tamamlandı & CI'da Doğrulandı):**
   - `BangumiTagDictionary.kt` ve `BangumiOrgDictionary.kt` içindeki dev sabit diziler kaldırıldı; veriler `app/src/main/resources/bangumi/tags.tsv` (**58.338 grup**) ve `app/src/main/resources/bangumi/orgs.tsv` (**52.474 satır**) classpath kaynaklarına taşındı (`TagStore` + `OrgStore`).
   - `strings.xml` (`app/src/main/res/values/strings.xml` [TR] ve `app/src/main/res/values-en/strings.xml` [EN] + `bangumi-vault-resources-1..3` shard'ları) toplam **TR 58.398 · EN 58.398** kayıtta tutuldu.
   - Gerçek GitHub Actions kanıtları:
     - `39601324650` (`🎌 Bangumi MASTER Doğrulama`): **`verify` ✓ + `compile-and-test` (`kotlinc` + Gradle + `BangumiMasterDictionaryTest`) ✓ %100 YEŞİL**.
     - `39601324622` (`🏗️ Android Derleme Doğrulama`): **`foss debug & R8 release APK + DEX 65K ve ART ikili doğrulama` ✓ + `📲 APK'ları yükle` ✓ YEŞİL** (yalnız önceden mevcut `MalNotificationsClientTest` kırmızı; **MAL koduna ve CI `gh run watch` beklemelerine DOKUNMA!**).
2. **Tüm Ham Kuyruklar Damgalandı (`translations_pending.tsv = 0` satır):**
   - `chunk_008` (`376` `count=0` kurum) ve `chunk_007` (`007_a` → `007_j` · `59.510` etiket) tamamlandı.
   - Otorite `verify_vault.py` çıktısı:
     ```
     ✅ TÜM KONTROLLER GEÇTİ — 52219 bangumi_* anahtar, 106335 vault kaydı, 121284 map satırı, 121284 damga denetlendi
     ```

---

## 2. YENİ OTURUMUN ASIL GÖREVİ: `as_is` (CJK) ve `tr == en` Kayıtların Derin Global Eşleştirmesi & TR/EN Çevirisi

Ham kuyruk bittiği için artık **yeni XML ID (`<string>`) eklemeden**, doğrudan mevcut kayıtları **yerinde (`in-place`) zenginleştirme** aşamasındayız!

Mevcut sayısal tablo:
- **`diag/bangumi_vault/decisions/canon.tsv`** (`111.310` kanon satırı) içinde hâlâ CJK (`as_is`) olarak duran **76.249 kanon** var:
  - `tag`: **43.486** CJK satırı (`app/src/main/resources/bangumi/tags.tsv` içinde 58.338 gruptan 43.486'sı CJK, 12.966'sı `tr == en` Latin, 1.886'sı `TR != EN`)
  - `producer`: **19.312** CJK satırı
  - `studio`: **5.090** CJK satırı
  - `publisher`: **4.841** CJK satırı
  - `magazine`: **2.341** CJK satırı
  - `network`: **1.179** CJK satırı
- **`strings.xml` (`values/strings.xml` TR + `values-en/strings.xml` EN + 3 shard modülü)** içindeki `52.219` `bangumi_*` anahtarın:
  - **31.404** tanesi hâlâ CJK (`as_is`) metin taşıyor (`orgs.tsv` kurum adlarını `R.string.bangumi_<role>_<cid>` üzerinden buradan okur!),
  - **19.528** tanesi `tr == en` Latin metin taşıyor (içinde özel ad olmayan İngilizce kavram/etiket kalmışsa TR karşılığı Türkçe olmalıdır!).

### Kesin Çeviri ve Eşleştirme İlkesi (KULLANICI EMRİ — HARFİYEN UYGULA!)
1. **Resmî Global Adı Olan Her Şey (`origin = global`):**
   - Stüdyolar, yapımcılar, yayınevleri, dergiler, TV/yayın ağları, plak şirketleri, oyun firmaları, anime/manga/oyun/roman serileri, karakterler, seiyuular, mangakalar, yönetmenler, senaristler, besteciler, müzik grupları:
   - Resmî global / uluslararası Latin adı **%100 biliniyorsa** hem `TR` hem `EN` alanına **resmî global adı** yazılır (`origin = global`).
   - **ASLA uydurma romanizasyon / makine pinyin-romaji üretme!** Yalnız gerçekten bilinen resmî adları veya tescilli global karşılıkları yaz; gerçekten yerel/bilinmeyen doujin daire/kişi adları `as_is` kalabilir.
2. **Global Özel Adı Olmayan Anlamlı Kavram / Tür / Tema / Biçim / Rol / Yuva Etiketleri (`origin = new`):**
   - **KESİNLİKLE İngilizce bırakılmaz ve CJK bırakılmaz!**
   - `TR` alanına **doğal, düzgün Türkçe çevirisi**, `EN` alanına **doğal, düzgün İngilizce çevirisi** yazılır (`origin = new`).
   - Aynı şekilde `tags.tsv` ve `strings.xml` içinde daha önce `TR == EN` (salt İngilizce) bırakılmış ama özel ad OLMAYAN tüm tür/kavram etiketlerinin `TR` karşılıkları da Türkçeye çevrilir (**DİKKAT:** `diag/bangumi_master/tags.tsv` içinde kilitli olan 146 MASTER satırına ve cross-role kurum anahtarlarına dokunma!).
3. **Nereye Yazılacak? (`vault_to_strings.py` Akışı — Yeni XML ID Eklemek YASAK!):**
   - **Yöntem (`diag/bangumi_vault/scripts/clean_cjk_canon_and_chunk008.py` kalıbını örnek al!):**
     1. `diag/bangumi_vault/decisions/canon.tsv` içindeki ilgili `(cid, role)` satırının `TR`, `EN`, `origin` sütunlarını yerinde (`in-place`) güncelle (`cid` ve `role` **ASLA DEĞİŞMEZ**, silinmez, yeni `strings.xml` ID'si **ASLA EKLENMEZ** — `TR 58.398 · EN 58.398` sabit kalır!).
     2. Ardından sırasıyla çalıştır:
        ```bash
        python3 diag/bangumi_vault/scripts/apply_map.py --check
        python3 diag/bangumi_vault/scripts/apply_map.py
        python3 diag/bangumi_vault/scripts/vault_to_strings.py
        python3 diag/bangumi_vault/scripts/verify_vault.py
        python3 diag/bangumi_master/verify_bangumi_master.py
        python3 diag/bangumi_master/gen_bangumi_master.py --check
        python3 diag/bangumi_vault/scripts/check_test_expectations.py
        python3 scripts/check_bangumi_meta_tag_coverage.py
        python3 -m unittest discover -s diag/bangumi_vault/scripts -p "test_*.py"
        ```
     3. `vault_to_strings.py` otomatik olarak:
        - `strings.xml` (`app` + `bangumi-vault-resources-1..3` hem `values/` TR hem `values-en/` EN) içindeki mevcut `bangumi_<role>_<cid>` metinlerini yerinde günceller (`TR +0 / ~N | EN +0 / ~N`),
        - `app/src/main/resources/bangumi/tags.tsv` ve `app/src/main/resources/bangumi/orgs.tsv` dosyalarını otomatik günceller!

---

## 3. Yeni Branch Kurulum Adımları (İlk 2 Dakika)

1. `main`'in güncel hâli üzerindeyken `arena/b4827842-kitsugi-beta` dalını ezmeden birleştir:
   ```bash
   git fetch --no-tags origin main:refs/remotes/origin/main arena/b4827842-kitsugi-beta:refs/remotes/origin/arena/b4827842-kitsugi-beta
   git merge --no-edit origin/arena/b4827842-kitsugi-beta
   ```
   *(Çakışma olursa `main` üzerindeki `v2.4.331+` oynatıcı/uygulama düzeltmelerini koru, `app/build.gradle.kts` içindeki 3 `bangumi-vault-resources-1..3` bağımlılığını koru!)*
2. `diag/bangumi_vault/*.json` çalışma alanında yoksa `google_drive` bağlayıcısı ile `1pZAOYsAnMjveNK6grdXjqV9IDM1TFaJp` klasöründen önce **taze `list_files`** çağırıp kimlikleri ada göre doğrula ve indir (`vault.json`, `tags.json`, `producers.json`, `publishers.json`, `studios.json`, `magazines.json`, `networks.json`, `committees.json`, `progress.json`, `translations_done.tsv`, `translations_pending.tsv`, `records_007_tags.json`).
3. Frekans sırasına göre (`count` yüksekten düşüğe — önce `strings.xml`'de görünen kurumlar `studio`, `publisher`, `magazine`, `network`, `producer` ve yüksek frekanslı `tag`'ler `007_b` → `007_c` → `007_d` → … → `007_i`) parti parti (`batch` hâlinde) ilerle!

---

## 4. KESİN YASAKLAR & KIRMIZI ÇİZGİLER

- **TEST VE CI İZLEME YASAĞI:** CI `gh run watch` / `sleep` ile bekleyip zaman harcama! MAL testlerine/koduna DOKUNMA! Test ve APK doğrulamasını kullanıcı kendisi yapacak; sen yalnız **yerel doğrulama zincirini** çalıştır, Google Drive'ı (`delete_file` → `upload_file`, taze ID ile) senkronla, commit + push yap!
- **`read_file` YASAĞI:** `vault.json`, `tags.json`, `producers.json`, `progress.json` dosyalarını ASLA `read_file` ile açma (yalnız Python betiğiyle oku).
- **`gen_bangumi_master.py` YASAĞI:** ASLA `--check` olmadan çalıştırma!
- **TEST KİLİTLERİNE DOKUNMA:**
  - `d_anime_store`: `TR = 'dアニメストア'`, `EN = 'd Anime Store'` olarak KİLİTLİDİR (`BangumiMasterDictionaryTest.kt`), ASLA değiştirme!
  - Kurum `assertNull` kilitleri: `["光坂高校演劇部", "PROJECT-XXX", "Clannad製作委員会"]` (`SKIP` kalmalı).
  - Etiket `assertNull` kilidi: `"ASOBI"` (`SKIP` kalmalı).
  - `tags.tsv` satır sırası ("first write wins") korunmalı (`verify_bangumi_master.py` her partide `✅ Tüm kontroller geçti` vermeli).
  - `strings.xml` içinde `values/strings.xml` = **TÜRKÇE (`TR`)**, `values-en/strings.xml` = **İNGİLİZCE (`EN`)** dosyasıdır — sakın ters karıştırma!
