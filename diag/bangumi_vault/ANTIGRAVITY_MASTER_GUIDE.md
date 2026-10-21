# 🚀 GEMINI ANTIGRAVITY — TAM UYGULAMA ENTEGRASYON & DOĞRULAMA KILAVUZU (`Kitsugi-Beta`)

Bu arşiv, **Kitsugi-Beta** projesinin **Bangumi VAULT (Chunk 001–009 + Batch 008 kapsamlı tarama)** yerelleştirme/küresel ad sistemi ile **4 modüllü Android kaynak mimarisi (`aapt2` & `R$string` 64K DEX koruması)** ve **classpath UTF-8 TSV sözlük depolarının (`kotlinc` sabit havuzu koruması)** **%100 kendi kendine yeten (self-contained)** tam paketidir.

> **ÖNEMLİ — EK İNDİRME VEYA GOOGLE DRIVE BAĞLANTISI GEREKMEZ:**  
> Bu `.zip` arşivinin içinde **hem Git'te izlenen tüm uygulama/kaynak/Gradle/test/doküman dosyaları (`116` dosya)** **hem de `.gitignore` kapsamındaki `14` adet büyük VAULT JSON/TSV veri dosyası (`diag/bangumi_vault/*.json`, `translations_*.tsv`)** eksiksiz yer almaktadır. Hiçbir haricî Google Drive indirmesi yapmadan tüm Python doğrulayıcıları, `kotlinc` derleme testi, JUnit testleri ve Debug/Release APK derlemeleri doğrudan çalıştırılabilir.

---

## 1. Hızlı Kurulum (Projeye Uygulama)

Hedef depo: `https://github.com/f26901964-eng/Kitsugi-Beta`  
Taban dal: `main` (`971abf19016428465d19c74dba3eb379feffc10c`, `v2.4.336`)  
Hazır entegrasyon dalı / PR: `arena/80e5e83a-kitsugi-beta` — **PR #32** (`https://github.com/f26901964-eng/Kitsugi-Beta/pull/32`)

### Yöntem A — Doğrudan ZIP Arşivini Proje Köküne Açma (Önerilen — %100 Çevrimdışı Tam Veri Dahil)

`Kitsugi-Beta` depo kök dizinindeyken (`settings.gradle.kts` ve `app/` klasörünün bulunduğu dizin):

```bash
# 1) Önce deponun güncel main (971abf1 veya üstü) üzerinde olduğundan emin olun
git status

# 2) ZIP arşivini doğrudan proje köküne açın (tüm dosyalar tam göreli yollarıyla yerine oturur)
unzip -o Kitsugi_Bangumi_Vault_Antigravity_Full_Package_2026-10-21.zip -d .

# 3) Kök dizine bilgilendirme amaçlı çıkan kılavuz/manifest dosyalarını isterseniz silin veya saklayın:
rm -f 00_GEMINI_ANTIGRAVITY_MASTER_GUIDE.md PACKAGE_MANIFEST.txt
```

### Yöntem B — Git Dalından Çekme + Yalnızca 14 VAULT Veri Dosyasını Açma

Eğer kodu Git üzerinden çekmek isterseniz:

```bash
git fetch origin arena/80e5e83a-kitsugi-beta
git checkout arena/80e5e83a-kitsugi-beta
# Yalnızca .gitignore'daki 14 VAULT JSON/TSV veri dosyasını arşivden çıkarın:
unzip -o Kitsugi_Bangumi_Vault_Antigravity_Full_Package_2026-10-21.zip "diag/bangumi_vault/*" -d .
```

---

## 2. Mutlak Mimari Kurallar (Dokunulmaması Gereken 5 Kısıt)

1. **`+0 XML ID` ve 4 Modüllü Kaynak Dağılımı (`aapt2` 65.535 & `R$string` 64K DEX Sınırı):**
   - Toplam **`58.451` TR / `58.451` EN** `<string>` kaydı 4 Gradle modülüne bölünmüştür:
     - `app`: `17.524` TR / `17.524` EN
     - `bangumi-vault-resources-1`: `13.565` TR / `13.565` EN
     - `bangumi-vault-resources-2`: `13.782` TR / `13.782` EN
     - `bangumi-vault-resources-3`: `13.580` TR / `13.580` EN
   - `app/src/main/res/raw/bangumi_vault_keep.xml` (`tools:keep="@string/bangumi_*"`) R8 kaynak küçültmesinde dinamik `getIdentifier` anahtarlarının silinmesini önler — **asla silmeyin**.
   - **Kesinlikle yeni XML `<string>` ID üretmeyin**; yeni/güncel VAULT kararları `app/src/main/resources/bangumi/{tags.tsv,orgs.tsv}` üzerinden sıfır XML ID maliyetiyle çözülür.

2. **Classpath UTF-8 TSV Sözlük Depoları (`kotlinc` Constant-Pool & `<clinit>` Koruması):**
   - `app/src/main/resources/bangumi/tags.tsv`: **`58.338` kanonik etiket grubu** (`BangumiTagDictionary.kt` tarafından `by lazy` ile yüklenir; `神作` → `Başyapıt` / `Masterpiece`).
   - `app/src/main/resources/bangumi/orgs.tsv`: **`52.626` kurum/yapımcı eşlemesi**, `0` gerçek çakışma (`BangumiOrgDictionary.kt` tarafından `by lazy` ile yüklenir).

3. **`.gitignore` Koruması (`128 MB` GitHub Dosya Sınırı):**
   - `diag/bangumi_vault/` içindeki 14 büyük JSON/TSV dosyası (`vault.json` ~53 MB, `tags.json` ~25 MB vb., toplam ~131 MB) `.gitignore` ile dışlanmıştır. **Asla `git add -f` ile Git'e eklemeyin.**

4. **Üreteç Koruması (`gen_bangumi_master.py`):**
   - `python3 diag/bangumi_master/gen_bangumi_master.py` komutunu **ASLA `--check` bayrağı olmadan çalıştırmayın** (`--check` olmadan çalıştırılırsa 51.600+ VAULT satırını siler). Güncelleme gerekiyorsa yalnızca şu sıra kullanılır:
     `apply_009_batch.py --mirror-repair` → `apply_map.py --check` → `apply_map.py` → `vault_to_strings.py`.

5. **`main` (`v2.4.336`) Kod Korunumu:**
   - MAL istemcisi/testleri, oynatıcı düzeltmeleri ve yeni arayüz dizgeleri (`downloads_delete_*`, `pf_err_anl_2008_*`) birebir korunmuştur.

---

## 3. Gemini Antigravity İçin Adım Adım Test ve Doğrulama Görev Listesi

### Görev A — Python Veri & Sözlük Bütünlük Zinciri (9/9 Sıfır Hata Beklenir)

```bash
python3 diag/bangumi_vault/scripts/apply_009_batch.py --mirror-repair
python3 diag/bangumi_vault/scripts/apply_map.py --check
python3 diag/bangumi_vault/scripts/vault_to_strings.py --check
python3 diag/bangumi_vault/scripts/verify_vault.py
python3 diag/bangumi_master/verify_bangumi_master.py
python3 diag/bangumi_master/gen_bangumi_master.py --check
python3 diag/bangumi_master/check_test_expectations.py
python3 scripts/check_bangumi_meta_tag_coverage.py
python3 -m unittest discover -s diag/bangumi_vault/scripts -p "test_*.py"
```

**Beklenen Çıktılar:**
- `verify_vault.py`: `✅ TÜM KONTROLLER GEÇTİ — 52219 bangumi_* anahtar, 106335 vault kaydı, 121284 map satırı, 121284 damga denetlendi`
- `verify_bangumi_master.py`: `58338` grup (`tags.tsv`), `52626` eşleme (`orgs.tsv`), `GERÇEK çakışma 0`, `strings.xml: TR 58451 · EN 58451`
- `check_test_expectations.py`: `23/23` uyumlu
- `check_bangumi_meta_tag_coverage.py`: `158/158` (`eksik: 0`)
- `unittest`: `Ran 30 tests ... OK`

---

### Görev B — Bağımsız `kotlinc` Sözlük Derleme & Çalışma Zamanı Testi

```bash
bash diag/bangumi_master/harness/compile_check.sh
```

**Beklenen Çıktı:**
- `✅ TÜM KONTROLLER GEÇTİ (0 hata · tag=58338 · org=52626)`

---

### Görev C — Android JUnit Birim Testleri (TR & EN Locale + Detay İstemcisi)

```bash
./gradlew :app:testFossDebugUnitTest \
  --tests 'com.kitsugi.animelist.utils.BangumiMasterDictionaryTest' \
  --tests 'com.kitsugi.animelist.utils.KitsugiTranslationsTest' \
  --tests 'com.kitsugi.animelist.data.remote.KitsugiBangumiDetailClientTest' \
  --tests 'com.kitsugi.animelist.utils.BangumiDisplayTitleLanguageTest'
```

**Doğrulanan Başlıca Senaryolar:**
- Kavram etiketleri (TR / EN): `神作` → `Başyapıt` / `Masterpiece`, `默片` → `Sessiz Film` / `Silent Film`, `哥特萝莉` → `Gotik Lolita` / `Gothic Lolita`, `自然纪录片` → `Doğa Belgeseli` / `Nature Documentary`, `Tactical_shooter` → `Taktiksel Nişancı` / `Tactical Shooter`, `Souls-like` → `Souls Benzeri` / `Souls-like`, `轻小说` → `Hafif Roman` / `Light Novel`
- Küresel kurum/yapımcı/yayımcı/dergi adları: `京アニ` → `Kyoto Animation`, `コミックス・ウェーブ・フィルム` → `CoMix Wave Films`, `青柳昌行` → `Masayuki Aoyagi`, `落越友則` → `Tomonori Ochikoshi`, `オーバーラップ文庫` → `Overlap Bunko`, `月刊ウィングス` → `Monthly Wings`
- Uydurma romanizasyon yasağı (`as_is` & komite koruması): `森下勝司` → `森下勝司`, `Clannad製作委員会` → dokunulmadan kalır
- `KitsugiBangumiDetailClient.buildNativeDetail`: Çoklu infobox yayımcı/imprint (`出版社` + `發行`), Japonca/Çince dergi (`連載雑誌`), oyun geliştiricisi (`开发`/`開發`) ve yayın kanalı (`Cumartesi (Fuji TV)` / `Saturdays (Fuji Television)`)

---

### Görev D — 4 Modüllü Debug & R8 Release APK Derlemesi + DEX 64K Sınır Kontrolü

```bash
./gradlew assembleFossDebug assembleFossRelease
```

İsteğe bağlı DEX alan sayısı doğrulaması (`apkanalyzer` veya `dexdump` ile):
- Her bir `.dex` dosyasındaki alan/metod sayısının `<= 65.535` olduğunu,
- APK içinde `bangumi/tags.tsv` ve `bangumi/orgs.tsv` kaynaklarının yer aldığını doğrulayın.

---

### Görev E — Cihaz / Emülatör Üzerinde Canlı UI Kontrolü (İsteğe Bağlı Son Kontrol)

1. Uygulama dilini **Türkçe (`tr`)** yapıp bir Bangumi Anime ve Manga/Hafif Roman detay sayfası açın:
   - Türler, etiket çipleri (`Başyapıt`, `Hafif Roman Uyarlaması`, `İyileştirici`, `Sessiz Film` vb.), Stüdyo, Yapımcı/Yayımcı (`Overlap Bunko`, `Shinshokan`), Dergi (`Monthly Wings`), Yayın Ağı (`Fuji TV`) çiplerinin doğru görüntülendiğini kontrol edin.
2. Uygulama dilini **İngilizce (`en`)** yapıp aynı sayfaları kontrol edin (`Masterpiece`, `Light Novel Adaptation`, `Fuji Television` vb.).
3. Tüm testler onaylandığında PR #32 (`https://github.com/f26901964-eng/Kitsugi-Beta/pull/32`) `main` dalına birleştirilebilir.

---

## 4. Arşivdeki Temel Plan ve Mimari Belgeleri

- `00_GEMINI_ANTIGRAVITY_MASTER_GUIDE.md` *(bu kılavuz)*
- `diag/bangumi_vault/HANDOFF_011_APP_INTEGRATION_PLAN_AND_TASKS.md` *(detaylı mimari plan & görev raporu)*
- `diag/bangumi_vault/HANDOFF_010.md` *(VAULT 001–009 + Batch 008 karar kuralları ve geçmişi)*
- `diag/bangumi_vault/PROGRESS.md` *(tüm chunk/batch ilerleme kaydı)*
- `PACKAGE_MANIFEST.txt` *(arşivdeki tüm dosyaların tam listesi ve SHA-256 özetleri)*
