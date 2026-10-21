# Bangumi VAULT — İlerleme / Devam Planı

> **Bu dosya devir teslim dosyasıdır.** Yeni bir dal/şube işi buradan devralır.
> Son güncelleme: **2026-10-21** · Chunk **001 + 002 + 003 + 004a** tamamlandı, uygulamaya işlendi.
> Chunk 004a PR'ı: bu dal `arena/6131dd54-kitsugi-beta` (chunk 001+002+003'ü de taşır; PR numarası Bölüm 8'de)
> Chunk 003 PR'ı: https://github.com/f26901964-eng/Kitsugi-Beta/pull/18 (dal `arena/6cb51a64-kitsugi-beta`)
> ⚠ `main` bu dalın tabanından sonra ilerledi (2.4.321–324); yeni dal **`origin/main` ile birleştirilmiş** hâlde
> `arena/6131dd54-kitsugi-beta` üzerinden devam eder. Chunk 003 dalı (`6cb51a64`) artık geride kaldı.
> Chunk 002 PR'ı: https://github.com/f26901964-eng/Kitsugi-Beta/pull/16 (önceki kaynak dal)
> Chunk 001 PR'ı: https://github.com/f26901964-eng/Kitsugi-Beta/pull/15
> Drive klasörü: https://drive.google.com/drive/folders/1pZAOYsAnMjveNK6grdXjqV9IDM1TFaJp

---

## 0. YENİ DAL / YENİ ORTAM KURULUMU — ÖNCE BURAYI OKU

Yeni bir dalda işe başlayan ajan SIFIRDAN araştırma yapmaz; şu sırayı izler:

### a) Dal kurulumu (KRİTİK)
- Chunk 001 işi commit `5c13f8e` dal `arena/a3fae7fe-kitsugi-beta` üzerinde ve **PR #15**'te
  (2026-10-21 itibarıyla PR #15 **merge edilmemiş**, `main`'de YOK).
- Chunk 002 işi dal `arena/2419075a-kitsugi-beta` üzerinde; o dal chunk 001'i de **taşır**
  (PR #15'in 17 dosyası birebir port edildi) → yeni dalı bu daldan türetmek en kolayı.
- **Yeni dalı `main`'den açıyorsanız** chunk 001+002 (strings + sözlükler + kararlar) o dala
  GİRMEZ; iş tekrarlanır. Bunu önlemek için PR #15'i merge edin **ya da** PR'ı bekleten
  çakışmayı çözün. PR #15 `mergeable: dirty` görünüyor ama chunk dosyaları çakışmıyor:
  PR'ın 5 ortak dosyası (`.gitignore`, iki `strings.xml`, iki `*Dictionary.kt`) PR tabanından
  (`f8a86bc`) bu yana `main`'de **hiç değişmedi**. Taşınabilir dosyalar:
  `diag/bangumi_vault/**` (12 dosya) + yukarıdaki 5 ortak dosya.
- Doğrulama: taşımaktan sonra `python3 diag/bangumi_vault/scripts/verify_vault.py` ✅ olmalı.

### b) Veri restorasyonu (yalnız çalışma alanı boşsa)
Vault JSON'ları bilerek git'e girmez (`.gitignore`). Dosyalar çalışma alanında
varsа (`diag/bangumi_vault/*.json`) bu adımı ATLA. Yoksa Drive'dan indir:

| Drive dosyası | Hedef |
|---|---|
| tags.json, studios.json, producers.json, publishers.json, magazines.json, networks.json, committees.json, vault.json, manifest.json, genres.json | `diag/bangumi_vault/` |
| translations_pending.tsv, translations_done.tsv, progress.json | `diag/bangumi_vault/` |
| canon.tsv, map_chunk_001_tags.tsv, map_chunk_001_orgs.tsv, map_chunk_002_tags.tsv, map_chunk_003_tags.tsv, org_global_names.tsv | `diag/bangumi_vault/decisions/` |
| PROGRESS.md | `diag/bangumi_vault/` |

İndirme ~74 MB tutar (yalnız `vault.json` 37 MB, `tags.json` 17,5 MB). `download_file`
çağrısı içeriği bağlama dökmeden çalışma alanına yazar → paralel çağrılarla birkaç dakikada iner.
`decisions/*.tsv` dosyaları git'te de var; Drive kopyasıyla **birebir aynı** olmalı
(chunk 002 başında doğrulandı: 4 dosya da IDENTICAL). Fark varsa git sürümü esastır,
Drive kopyası chunk sonunda yenilenir.

Drive'daki JSON'lar karar alanlarıyla (`decision/canon/string_key/chunk`) **damgalıdır**.
⚠ **2026-10-21 durumu**: Drive'daki vault JSON'ları yalnız **chunk 001** damgası taşır
(chunk 002'de güncellenmedi — 74 MB'lık yeniden yükleme; Bölüm 0d listesinde de yok).
Yeni ortamda restorasyondan sonra **mutlaka** şunu çalıştır:

```bash
python3 diag/bangumi_vault/scripts/apply_map.py    # tüm chunk'ların kararlarını JSON'lara damgalar (idempotent)
python3 diag/bangumi_vault/scripts/verify_vault.py # ✅ beklenir
```

`apply_map.py` tüm chunk'ların kararlarını (001 + 002 + …) yeniden damgalar, yani Drive
kopyası ne kadar eski olursa olsun **tek çalıştırmada** güncel duruma gelir. Damgasız
(orijinal) kopya için de aynı komut yeterlidir.

✅ **2026-10-21'de kanıtlandı**: chunk 002 damgaları 594 kopya kayıttan silinip `apply_map.py`
çalıştırıldı → 8 vault JSON'ının **8'i de bayt bayt eski hâline döndü** (round-trip kayıpsız),
`translations_pending.tsv` 63.448 → 63.448 (mükerrer düşme yok).

✅ **Chunk 003 sonrası güncel restore hedefi**: Drive JSONları hâlâ yalnız chunk 001
damgası taşır; güncel tüm map dosyalarıyla `apply_map.py` çalıştırılınca
`verify_vault.py` 2.491 anahtar · 1.089 kayıt · 1.662 map · 121.284 damga vermelidir.

Not: `apply_map.py --check` bu durumu **yakalamaz** (yalnız map→canon tutarlılığına bakar).
Yakalayan şey `verify_vault.py`'nin 7. kontrolüdür (aşağıda).

### c) Sağlık kontrolü (her oturum başında 1 komut)
```bash
python3 diag/bangumi_vault/scripts/verify_vault.py   # ✅ TÜM KONTROLLER GEÇTİ beklenir
```
Sonuç temizse kaldığı yerden devam et: **Bölüm 5'teki ilk ☐ kutucuğu** sıradaki iştir.
Kimse aynı kaynakları yeniden taramaz — tüm bağlam bu dosyadadır.

### d) Biten her chunk'ta (yeni dal bunu okuyarak devralır)
1. `apply_map.py --check` + `apply_map.py` → `vault_to_strings.py` → `verify_vault.py`
2. Bu dosyada Bölüm 2 tablosunu güncelle, Bölüm 5'te ☐ → ☑ işaretle
3. Drive'a şunları yükle (aynı adla geçerse `delete_file` + `upload_file`):
   PROGRESS.md, progress.json, translations_pending.tsv, translations_done.tsv,
   decisions/canon.tsv, decisions/map_chunk_*.tsv, decisions/org_global_names.tsv
4. `git add` + commit (yalnız script/karar/PROGRESS + uygulama dosyaları)

### e) Yeni ajanı BAŞLATMA KOMUTU (kullanıcı bunu yapıştırır — başka şeye gerek yok)

Yeni Arena oturumunda, yeni dal açıldığında **birebir şu metni** yapıştır:

> `diag/bangumi_vault/PROGRESS.md` dosyasını oku — Bangumi VAULT çeviri işinin devir
> teslim dosyasıdır; hiçbir şeyi sıfırdan araştırma, her şey orada yazılı.
>
> **(0) Dal:** Chunk 001+002 **PR #16**'da; Chunk 003 bu dalın **PR #18**'inde
> (`arena/6cb51a64-kitsugi-beta`) tamamlandı; bu chunk'lar henüz `main`'de değil.
> Yeni bir daldan devam ediyorsan chunk 003 dosyalarını PR #18'den, chunk 001+002
> dosyalarını #16'dan port et; hiçbiri için araştırmayı baştan yapma. Ayrıntı: Bölüm 0a.
>
> **(1) Kurulum:** Bölüm 0b tablosuyla veri dosyalarını Drive klasöründen indir
> (`1pZAOYsAnMjveNK6grdXjqV9IDM1TFaJp`, ~74 MB; `download_file` paralel çağrılarla).
> Drive'daki vault JSON'ları **yalnız chunk 001 damgalıdır** → indirdikten sonra
> **mutlaka** `python3 diag/bangumi_vault/scripts/apply_map.py` çalıştır (chunk 002 + 003
> kararlarını damgalar, kayıpsız). Sonra `verify_vault.py` **✅** olmalı
> (beklenen: `2491 bangumi_* anahtar, 1089 vault kaydı, 1662 map satırı, 121284 damga`).
>
> **(2) İş:** Bölüm 5'teki **ilk ☐** kutucuğundaki chunk'ı yap: karar ver →
> `apply_map.py` → `vault_to_strings.py` → `verify_vault.py`. Karar kalıpları için
> "Chunk 002 karar şablonu" bölümünü (6 adım) birebir kullan.
>
> **(3) Kapanış (Bölüm 8):** tik at (☐→☑), Bölüm 2 tablosunu güncelle, PROGRESS.md +
> progress.json + karar dosyalarını Drive'a yükle (aynı ad varsa `delete_file`+`upload_file`),
> `verify_bangumi_master.py` + `gen_bangumi_master.py --check` + `git diff --quiet` ile CI'yı
> yerelde simüle et, commit + push + PR aç.
>
> **Kurallar:** global karşılık varsa kullan; yoksa TR+EN çevir; bilinmeyen CJK'ya
> romanizasyon **uydurma** (`as_is` kalır); SKIP girdileri dil dosyasına **girmez**.
> **Yasak:** `gen_bangumi_master.py`'yi `--check` olmadan çalıştırma (711 VAULT satırını siler,
> Bölüm 7 ⚠).

Kısa sürüm (dosya zaten çalışma alanında ve damgalıysa yeterli):
**"`diag/bangumi_vault/PROGRESS.md`'yi oku, Bölüm 0 kurulumunu yap, Bölüm 5'teki sıradaki
☐ chunk'ı bitir, Bölüm 8 kapanış listesini uygula."**

Sıradaki chunk: **004b — kurum count 35–59 (1.000 kayıt)**. Chunk 004a (kurum count 60–99)
tamamlandı; `translations_pending.tsv` 62.957 satır (kurum kayıtları pending'te yok).
Chunk 004 üç parçaya bölündü: **004a 60–99 ✓**, **004b 35–59 (1.000)**, **004c 20–34 (1.515)**.

---

## 1. İşin amacı (kullanıcı talebi)

Bangumi VAULT yığınlarındaki (tags, genres, producers, publishers, studios, networks,
magazines, committees, vault, translations_pending.tsv) her etiket/kurum için:

1. Uygulamada **global karşılığı** varsa ona bağla (adı global gelenekle uyumlu),
2. Yoksa **TR + EN çevirisi** üret ve dil dosyalarına ekle,
3. Sonunda **uygulamaya tam entegrasyon** (strings.xml + BangumiTagDictionary +
   BangumiOrgDictionary + vault JSON karar alanları).
4. Çalışma **parça parça (chunk)** ilerler; her turdan sonra bu dosya güncellenir.

## 2. Mevcut durum — CHUNK 001 ✓ + CHUNK 002 ✓ + CHUNK 003 ✓

### 2a) CHUNK 001 (TAMAM ✓)

| Alan | İşlenen | SKIP | Toplam |
|------|---------|------|--------|
| tag (count≥500) | 174 kayıt → 108 birleşik etiket | 66 | 64.499 |
| studio (count≥100) | 63 | 1 | 12.391 |
| producer (count≥100) | 126 | 3 | 24.825 |
| publisher (count≥100) | 373 | 18 | 13.227 |
| magazine (count≥100) | 127 | 0 | 3.995 |
| network (count≥100) | 11 | 0 | 2.347 |
| **Toplam** | **874 map satırı** (786 canon + 88 skip) | | |

Uygulama çıktıları (hepsi `verify_vault.py` ile doğrulandı ✅):

- `app/src/main/res/values/strings.xml` + `values-en/strings.xml`:
  `<!-- Bangumi VAULT 2026-10-20 -->` bloğu — **+633 TR / +633 EN** yeni `<string>`.
  (81 anahtar mevcuttu; birleşimle aynı anahtara oturdu, değerleri KORUNDU.)
- `BangumiTagDictionary.kt`: mevcut gruplara +48 etiket, yeni `g()` grupları +54
  (yalnız etiket eklendi; mevcut TR/EN değerlerine dokunulmadı).
- `BangumiOrgDictionary.kt` RAW: +801 satır (`etiket|anahtar|Latin ad`).
  Ayrıca master'dan gelen **94 gölgelenmiş yinelenen satır** temizlendi
  (lookup ilk-yazım-kazanır olduğundan gösterim değişmedi).
- vault JSON'ları: karar alanları (`decision/canon/string_key/chunk`) tüm kopyalara
  işlendi (kategori dosyaları + committees.json + vault.json arşiv kopyaları).
- `translations_done.tsv` (874 satır, append-only) + `translations_pending.tsv`
  63.919 → 63.745 (174 etiket düştü; org kayıtları pending'te yoktu).

### 2b) CHUNK 002 (TAMAM ✓ · 2026-10-21) — etiket count 200–499

| Alan | Kararlı | SKIP | Toplam |
|------|---------|------|--------|
| tag (count 200–499) | **212 kayıt** | **85** | **297** |

212 kararlı kaydın dağılımı:

| Karar türü | Kayıt | Açıklama |
|---|---|---|
| mevcut tag kanonuna birleşim | 31 | `movie`, `bl`, `jpop`, `eroge`, `guro`, `toei_animation`, `jump`, `uncut`, `fanservice` … |
| sözlük grubuna birleşim (`origin=existing`) | 45 | `BangumiTagDictionary`'de grup zaten vardı → canon satırı açıldı, TR/EN **dil dosyasından birebir korundu** (`4koma`, `completed`, `ongoing`, `light_novel`, `childhood_friends`, `platform_xbox` …) |
| çapraz rol birleşimi (`origin=existing`, 8. alan) | 18 | etiket → kurum anahtarı: `电击文库`→`bangumi_publisher_dengeki_bunko`, `AIC`→`bangumi_studio_aic`, `フジテレビ`→`bangumi_network_fuji_tv`, `TMS`→`bangumi_studio_tms`, `Falcom`→`bangumi_publisher_nihon_falcom` … |
| yeni küresel karşılık (`origin=global`) | 71 | marka/seri/yayıncı + **35 seslendirme sanatçısı** (Latin sabitleme) |
| yeni çeviri (`origin=new`) | 44 | TR+EN üretildi: `季番`→Tek Sezonluk, `赛璐珞`→Selüloit Animasyon, `黑长直`→Uzun Düz Saç, `龙傲天`→Yenilmez Baş Karakter … |

Uygulama çıktıları (`verify_vault.py` + `verify_bangumi_master.py` ile doğrulandı ✅):

- `values/strings.xml` + `values-en/strings.xml`: **+115 TR / +115 EN** yeni `<string>`
  (VAULT bloğuna eklendi; **mevcut hiçbir değerin üzerine yazılmadı** — `~0` güncelleme).
- `BangumiTagDictionary.kt`: **+115 yeni `g()` grubu**, mevcut gruplara **+62 etiket**.
- `BangumiOrgDictionary.kt` RAW: **+4 satır** (`电击文库`, `falcom`, `Falcom`, `SquareEnix`).
  RAW toplam 1.724 satır, **yinelenen etiket 0** (gölgeleme yok).
- `decisions/canon.tsv`: **879 kayıt** (Chunk 002 kapanışı; `apply_map.py --check` doğruladı) · `decisions/map_chunk_002_tags.tsv`: **297 satır**
- `decisions/org_global_names.tsv`: **454 veri satırı** (Chunk 002 kapanışı; Chunk 003 öncesi)
- `translations_done.tsv`: 874 → **1.171** satır · `translations_pending.tsv`: 63.745 → **63.448**

### 2c) CHUNK 003 (TAMAM ✓ · 2026-10-21) — etiket count 100–199

| Alan | Kararlı | SKIP | Toplam |
|------|---------|------|--------|
| tag (count 100–199) | **355 kayıt** | **136** | **491** |

491 kaydın karar dağılımı:

| Karar türü (`canon.tsv` origin) | Kayıt | Açıklama |
|---|---:|---|
| existing | 115 | mevcut tag kanonu/sözlük grubu; TR/EN mevcut değerleri korundu (53 kayıt mevcut kurum string anahtarını kullanıyor) |
| global | 134 | yerleşik global ad/terim; bilinen Latin yazımı veya fandom kanonu |
| new | 93 | yeni kavram; TR+EN karşılıkları üretildi |
| as_is | 13 | bilinmeyen CJK özel ad; romanizasyon uydurulmadan ham değer korundu |
| SKIP | 136 | tarih, puan, kişisel raf/durum, gürültü ve belirsiz kısaltmalar |

Uygulama çıktıları (`verify_vault.py` + `verify_bangumi_master.py` ile doğrulandı ✅):

- `values/strings.xml` + `values-en/strings.xml`: **+249 TR / +249 EN** string; TR/EN toplamı 8.628/8.628.
- `BangumiTagDictionary.kt`: **+249 grup**, mevcut gruplara **+106 etiket**.
- `BangumiOrgDictionary.kt` RAW: **+9 satır** (1.724 → 1.733; gerçek çakışma 0).
- `decisions/canon.tsv`: **+266 satır** (879 → 1.145) · `map_chunk_003_tags.tsv`: **491 satır**.
- `decisions/org_global_names.tsv`: **+114 satır** (77 kişi + 37 marka/seri/kurum).
- `translations_done.tsv`: 1.171 → **1.662** · `translations_pending.tsv`: 63.448 → **62.957**.

### 2e) CHUNK 004a (TAMAM ✓ · 2026-10-21) — kurum count 60–99

Chunk 004 (kurum count 20–99 · 3.082 kayıt) üç parçaya bölündü; ilk parça 004a:

| Rol | Kararlı | SKIP | Toplam |
|---|---:|---:|---:|
| studio | 108 | 6 | 114 |
| producer | 161 | 8 | 169 |
| publisher | 183 | 8 | 191 |
| magazine | 77 | 4 | 81 |
| network | 12 | 0 | 12 |
| **Toplam** | **541** | **26** | **567** |

541 kararlı kaydın `canon.tsv` origin dağılımı: **global 189 · as_is 300 · existing 52**.

- **global (189)**: resmî Latin adı bilinen marka/kurum/kişi. Örnekler: `スタジオジブリ`→Studio Ghibli,
  `ゲームフリーク`→Game Freak, `手塚プロダクション`→Tezuka Productions, `アクアプラス`→Aquaplus,
  `アークシステムワークス`→Arc System Works, `日本クラウン`→Nippon Crown, `河出書房新社`→Kawade Shobo
  Shinsha, `廣済堂出版`→Kosaido Publishing, `智冠科技`→Soft-World International, `米哈游`→miHoYo,
  `ディ앤씨미디어`→D&C Media; dergi kanonu: `月刊少年GANGAN`→Monthly Shonen Gangan,
  `くらげバンチ`→Kurage Bunch, `サンデーうぇぶり`→Sunday Webry, `ガンダムエース`→Gundam Ace …
  Kişi adları yalnız **resmî Latin yazımı bilinenlerde** alındı (Toshio Suzuki, Seiji Okuda,
  Kazuma Miki, Genki Kawamura, Shinichiro Shirakura, Kozo Morishita, Shin Unozawa, Tetsuro Satomi …).
- **existing (52)**: mevcut string anahtarına birleşim — `トムス`/`トムス・エンタテインメント`→`bangumi_studio_tms`,
  `アスキー`→`bangumi_publisher_ascii`, `コーエー`/`光荣`→`bangumi_publisher_koei`, `青文`→`bangumi_publisher_chingwin_publishing`,
  `尖端出版社`→`bangumi_publisher_sharp_point_press`, `太东`/`Taito`→`bangumi_publisher_taito_3`, `戯画`→`bangumi_studio_giga` …
  (13 kayıt başka roldeki anahtara bağlandı; TR/EN değerleri **dil dosyasından birebir** korunur.)
- **as_is (300)**: bilinmeyen CJK kurum/kişi — romanizasyon **uydurulmadı** (139'u `cjk_<hash>` anahtarı).
- **SKIP (26)**: ad parçası/gürültü (`スタジオ`, `出版`, `コミックス`, `ピクチャーズ`, `エンターテイメント`,
  `グループ`, `ソフトウェア`, `Inc.`, `INC.`, tek kana `エー`/`ジェイ`/`メリー`/`ケイ`/`エム`/`コウ`/`エス`,
  `週刊`, `中国大陆` bölge etiketi, belirsiz `BE`/`LOVE`/`スター`/`マガジンズ`).

Uygulama çıktıları (`verify_vault.py` + `verify_bangumi_master.py` ile doğrulandı ✅):

- `values/strings.xml` + `values-en/strings.xml`: **+457 TR / +457 EN** `<string>` (TR/EN toplamı 9.113/9.113;
  mevcut değer üzerine yazma **0**).
- `BangumiOrgDictionary.kt` RAW: **+632 satır** (1.733 → 2.365; hâlâ 3 parça, en büyük parça 49.976 bayt < 50.000).
- `BangumiTagDictionary.kt`: **+0 grup, +0 etiket** (bu chunk yalnız kurum).
- `decisions/canon.tsv`: **+501 satır** (1.145 → 1.646) · `map_chunk_004a_orgs.tsv`: **567 satır**.
- `decisions/org_global_names.tsv`: **+199 satır** (yeni tanınan resmî Latin adlar).
- `translations_done.tsv` / `translations_pending.tsv`: **değişmedi** (kurum kayıtları pending'te yok; 62.957).

Araç değişiklikleri (geriye dönük uyumlu):
- `scripts/draft_org_map.py`: **`--max-count`** eklendi (kurum bantlarını `--min-count 60 --max-count 99`
  gibi bölerek işlemek için; varsayılan `None` = eski davranış). `SKIP_NAMES` listesi chunk 004a'da
  görülen ad parçalarıyla genişletildi.

### 2d) Kümülatif durum (chunk 001 + 002 + 003 + 004a)

```
python3 diag/bangumi_vault/scripts/verify_vault.py
✅ TÜM KONTROLLER GEÇTİ — 2948 bangumi_* anahtar, 1568 vault kaydı, 2229 map satırı, 121284 damga denetlendi
```

| Rol | Toplam kayıt | Karar satırı (canon + SKIP) | Kararlı canon | SKIP |
|---|---:|---:|---:|---:|
| tag | 64.499 | **962** | **675** | 287 |
| studio | 12.391 | 177 | 170 | 7 |
| producer | 24.825 | 295 | 284 | 11 |
| publisher | 13.227 | 564 | 538 | 26 |
| magazine | 3.995 | 208 | 204 | 4 |
| network | 2.347 | 23 | 23 | 0 |
| committee | 1.229 | 0 | 0 | 0 |
| **Toplam** | | **2.229** | **1.894** | **335** |

Kurum kalan iş yükü (kararsız): count 60–99 → **0**, count 35–59 → **1.000**, count 20–34 → **1.515**,
count 1–19 → kalan kuyruk (chunk 001–003 kararlarından sonra). Etiket kalan iş yükü:
count 20–99 → **4.027**, count <20 → **59.510** (toplam **63.537**). `translations_pending.tsv` = **62.957** satır.

`python3 diag/bangumi_master/verify_bangumi_master.py` → **✅ Tüm kontroller geçti**
(2.365 eşleme · aynı-ad rol çakışması 62 · gerçek çakışma 0; TagDictionary 1.325 grup;
TR/EN 9.113/9.113; RAW 3 parça, en büyük 49.976 bayt). `gen_bangumi_master.py --check`
de yazma yapmadan temiz geçti.



## 3. Karar sözlüğü (her yeni dal bunu bilmeli)

- `canon.tsv` satırı: `canon_id <TAB> rol <TAB> tr <TAB> en <TAB> origin <TAB> aliases <TAB> not [ <TAB> string_key ]`
  - `origin`: `existing` (uygulamada anahtar var, değer korunur) | `global` (küresel
    karşılık, yeni anahtar) | `new` (bizim çeviriz) | `as_is` (Latin ad ya da bilinmeyen
    CJK — **uydurma romanizasyon YOK**, ham ad kalır).
  - 8. alan `string_key` = roller arası birleşim (ör. producer 東映 → `bangumi_studio_toei_animation`).
  - Tag `existing` satırlarının TR/EN değerleri **ASLA** değiştirilmez.
- `map_chunk_*.tsv`: `vault_key <TAB> canon_id|SKIP <TAB> not`
  - `SKIP` = dil dosyasına girmez (kişisel raf etiketleri, yıl/dates, fandub işaretleri,
    ad parçaları/gürültü: インディーズ, メーカー, Inc, GOLD…) — ham gösterilir.
- Ekran zinciri (korunmalı): etiket = `BangumiTagDictionary` (ilk yazım kazanır) →
  `bangumi_tag_<key>` string sürümü → ham; kurum = `resourceKeyFor` (RAW ilk yazım
  kazanır) → string kaynak → Latin ad.

## 4. Pipeline — yeni dalda çalışma sırası

```bash
cd diag/bangumi_vault
# 1) iş listesi: kararsız kayıtlar (count azalan)
python3 scripts/worklist.py --min-count N        # worklists/tags_pending.tsv + orgs_pending.tsv
python3 scripts/worklist.py --dump ROLE N        # ilk N kaydı ekrana dök (elle karar için)

# 2) KARAR: decisions/canon.tsv'e kanon satırı + decisions/map_chunk_XXX_*.tsv'e map satırı
#    - elle (önerilen: etiket chunk'ları) ya da
#    - python3 scripts/draft_org_map.py --chunk XXX --min-count N [--max-count M]
#      (kurum taslağı, bilgi tabanı ile; chunk 004+ bantları M ile bölünür)

# 3) uygula + doğrula
python3 scripts/apply_map.py --check && python3 scripts/apply_map.py
python3 scripts/vault_to_strings.py
python3 scripts/verify_vault.py
```

`apply_map.py` map + canon'dan vault JSON'larına `decision/canon/string_key/chunk`
işler, `translations_done.tsv` yazar, `translations_pending.tsv`'i düşer,
`progress.json` günceller. `vault_to_strings.py` idempotenttir.

## 5. Sıradaki chunk'lar (tikli liste — ☐ = yapılacak, ☑ = bitti)

- [x] **Chunk 001** — etiket count≥500 (174) + kurum count≥100 (700) → uygulamaya işlendi ✅
- [x] **Chunk 002** — etiket count 200–499 (**297** kayıt: 212 kararlı + 85 SKIP) →
      uygulamaya işlendi ✅ (2026-10-21; ayrıntı Bölüm 2b)
- [x] **Chunk 003** — etiket count 100–199 (**491** kayıt: 355 kararlı + 136 SKIP) →
      uygulamaya işlendi ✅ (2026-10-21; ayrıntı Bölüm 2c). `worklist.py --min-count 100`
      sonrası bu aralıkta **0** kayıt kaldı.
- [x] **Chunk 004a** — kurum count **60–99** (567 kayıt: 541 kararlı + 26 SKIP) →
      uygulamaya işlendi ✅ (2026-10-21; ayrıntı Bölüm 2e). `worklist.py --min-count 60`
      sonrası bu aralıkta **0** kayıt kaldı.
- [ ] **Chunk 004b** — kurum count **35–59** (**1.000** kayıt: studio 210, producer 362,
      publisher 288, magazine 116, network 24). Komut:
      `python3 scripts/draft_org_map.py --chunk 004b --min-count 35 --max-count 59`.
      `org_global_names.tsv` yeni tanınan resmî Latin adlarla zenginleştirilir; kişilerde
      yalnız Latin yazımı **bilinenler** alınır, gerisi `as_is`.
- [ ] **Chunk 004c** — kurum count **20–34** (**1.515** kayıt: studio 358, producer 607,
      publisher 365, magazine 138, network 46, committee 1). Komut:
      `python3 scripts/draft_org_map.py --chunk 004c --min-count 20 --max-count 34`
      (committee rolü `draft_org_map.py` ORG_FILES'ta yok → tek kayıt elle eklenir).
- [ ] **Chunk 005+** — etiket count 20–99 (**4.027**), sonra kuyruk (<20 → **59.510**);
      kurum 1–19; committees (1.229) + üretici kişileri (`as_is`/bilinen romanizasyonlar).
- [ ] **Final** — kalan `translations_pending.tsv` tükendi mi? `verify_vault.py` ✅?
      PROGRESS.md + Drive + PR güncelle.

Her chunk aynı pipeline'ı izler (Bölüm 0d). Bilinmeyen kişi/kurumlar `as_is` kalır
(kural: romanizasyon uydurulmaz).

### Chunk 002'de kullanılan karar şablonu (chunk 003+ için hazır)

1. **Önce küresel karşılık ara** — üç kaynağa birden bak (chunk 002'de 38 aday böyle yakalandı):
   `decisions/canon.tsv` (tüm roller + alias'lar) → `BangumiTagDictionary.kt` `g(...)` etiketleri
   (961 grup, 4.242 normalize etiket) → `decisions/org_global_names.tsv`.
   Eşleşme **normalize** yaparak aranmalı: NFKC + küçük harf + `「」《》（）()【】` sarımları,
   boşluk, `. - _ ·・ 〜 ~ ! ? ' " ★ ☆ ✘ ✔` işaretleri atılır (`★J-POP` ≡ `jpop`, `【BL漫画】` ≡ `bl`).
2. **Sözlükte grup varsa** canon satırını `origin=existing` aç ve TR/EN'i **dil dosyasındaki
   değerden birebir kopyala** (sözlükteki değerle xml değeri farklıysa xml kazanır) —
   `verify_vault.py` değer eşitliğini denetler.
3. **Kurum başka rolde zaten kanonsa** (publisher/network/studio/producer) canon satırının
   **8. alanına** o rolün `string_key`'ini yaz → tek string anahtarında birleşir
   (`AIC` → `bangumi_studio_aic`). `apply_map.py` bu alanı okur.
4. **Kavramsa TR+EN çevir** (`origin=new`); **marka/seri/kişi ise küresel Latin adı** kullan
   (`origin=global`).
5. **SKIP** (dil dosyasına girmez, ekranda ham kalır) — chunk 002'de 85 kayıt bu sınıfa girdi:
   yıl/onyıl/yüzyıl/sezon (`2020年`, `2010s`, `2012秋`, `20世纪`), doujin etkinlik numarası
   (`C80`, `c84`, `M3-29`), kişisel raf/durum/puan (`删`, `已整理`, `已购`, `存盘`, `7分`, `白金`,
   `七星`, `八星`, `interested`), öznel değerlendirme (`早期优秀作品`, `超高实用性`), anlamsız
   işaret/tek karakter (`「✘」`, `☆`, `「✔」`, `ʙᴋ`, `本`, `总`, `undefined`), belirsiz kısaltma
   (`RM`, `robo`, `AAVG`, `EP`, `BK`, `FD`, `TM`, `CC`, `CS`, `BG`, `EPIC`, `18N`, `3A`),
   kullanıcı imzası/adı (`Tr₇Sr₊`, `屹青`), **Latin adı doğrulanamayan kişi** (`霜月はるか`, `茶太`).
6. **Kişi adları** yalnızca yerleşik Latin adı bilinenlerde çevrildi (35 seslendirme sanatçısı +
   あだち充→Mitsuru Adachi). Bilinmeyenlerde **romanizasyon uydurulmadı** → SKIP (ham kalır).
   `origin=as_is` yerine SKIP tercih edildi çünkü `as_is` dil dosyasına ham CJK değeri yazıp
   ekran çıktısını değiştirmeden gürültü anahtar üretir.

## 6. Bilinçli kararlar / notlar

- **Roller arası birleşim**: aynı marka farklı rollerde geçiyorsa tek string
  anahtarında toplanır (canon satırı yine rola özeldir). Ör. producer_madhouse →
  `bangumi_studio_madhouse`; producer 東映 → `bangumi_studio_toei_animation`.
- **Kişi romanizasyonları** yalnızca bilinenlerde: 丸山正雄→Masao Maruyama,
  志倉千代丸→Chiyomaru Shikura, 李旎→Ni Li vb. (`org_global_names.tsv`).
  Chunk 002 bu listeyi **42 kişiyle** genişletti (35 seslendirme sanatçısı + あだち充):
  森川智之→Toshiyuki Morikawa, 下野紘→Hiro Shimono, 福山潤/福山润→Jun Fukuyama
  (basit + geleneksel tek kanonda birleşti), 田村ゆかり→Yukari Tamura, 能登麻美子→Mamiko Noto,
  遊佐浩二→Koji Yusa, 喜多村英梨→Eri Kitamura, 岸尾だいすけ→Daisuke Kishio …
  ⚠ Bu Latin adlar **çevrimdışı bilgiyle** yazıldı (sandbox'ta bgm.tv/MAL erişimi yok);
  şüphe duyulan bir ad olursa `org_global_names.tsv` + `canon.tsv`'den düzelt ve
  pipeline'ı **temelden** yeniden çalıştır (Bölüm 7'deki "yeniden üretme" notu).
- **Yapımcı üreticiler** (PoRO, pixy, ViViAN, EDGE…) romanize edilmedi → as_is
  (muhtemel yetişkin içerik; repo riski).
- `44℃梅毒`, `Inc`, `GOLD`, `ワーナー`, `エンタテインメント`… gibi ad parçaları SKIP.
- vault.json + committees.json = yinelenen kopyalar; `apply_map.py` tüm kopyalara
  damgalar (mükerrer karar yok).
- İzlenebilirlik: `map_chunk_*.tsv` notlarında gerekçe; `translations_done.tsv`'de
  origin + TR/EN.
- **Chunk 002 — 18+ içerik politikası**: chunk 001 ile aynı çizgi korundu. Müstehcen
  kavramlar çevrildi (`扶她`→Futanari, `触手`→Dokunaç, `黑丝`→Siyah Çorap,
  `步兵裡番`→Sansürsüz Hentai, `H本`→Yetişkin Kitap, `18+`→18+). Uydurma romanizasyon
  ve kişisel veri yok; üretici stüdyo adları yalnızca Latin yazımı zaten bilinenlerde
  alındı (`3DHentai`, `NEXTON`, `VisualArt's`, `Cybele`, `Appetite`).
- **Chunk 002 — `canon_id` adı kişi adıyla uyumlu olmalı**: ilk üretimde iki slug yanlış
  kişi adından türedi (`遊佐浩二`→`yuji_yasuda`, `喜多村英梨`→`kanae_ito`) ve
  `koji_yusa` / `eri_kitamura` olarak düzeltildi. Ders: canon_id'yi kişinin **Latin
  adından** üret, ham adın okunuşundan değil; düzeltme yapılınca pipeline'ı temelden
  yeniden çalıştır (yalnız `--check` değil) yoksa **bayat string anahtarı kalır**.

- **Chunk 004a — SKIP kuralı genişledi**: kurum adının *kendisi* olmayan parçalar (`スタジオ`,
  `出版`, `コミックス`, `ピクチャーズ`, `エンターテイメント`, `グループ`, `ソフトウェア`,
  `Inc.`/`INC.`), tek kana belirsizlikleri (`エー`, `ジェイ`, `ジェーン`, `メリー`, `ケイ`, `エム`,
  `コウ`, `エス`), `週刊` ve bölge etiketi `中国大陆` SKIP. Bunlar `draft_org_map.py` içindeki
  `SKIP_NAMES`'e eklendi (chunk 004b/004c'de de geçerli).
- **Chunk 004a — kişi politikası**: `producer` kayıtlarının çoğu kişi adı; yalnız resmî Latin
  yazımı **bilinen** kişiler `global` alındı (ör. Toshio Suzuki, Kozo Morishita, Shinichiro
  Shirakura). Okunuşu tahmin edilen adlar `as_is` kalır — kural: romanizasyon uydurulmaz.
  Bu chunk'ta `producer` 161 kaydın 110'u `as_is`.

## 7. Bilinen teknik notlar

- Sandbox'ta doğrudan ağ YOK (yalnız `github.com` + `api.github.com` + paket depoları);
  Drive I/O yalnız **google_drive connector** ile.
  ✅ **İşleyen indirme yolu**: `download_file` çağrısı dosyayı **çalışma alanına** yazar
  (`google_drive/<ad>`), içeriği bağlama DÖKMEZ → 37 MB `vault.json` bile sorunsuz iner.
  (`read_file_text` BÜYÜK dosyalarda KULLANMA — tüm içeriği bağlama basar.)
  Dosya değiştirmek = `delete_file` + `upload_file`; indirdikten sonra `google_drive/`
  klasörünü sil (`.gitignore`'da zaten).
- Ortamda JDK/kotlinc varsayılan olarak kurulu değil; CI hata ayıklaması için geçici JDK 17 + Kotlin 2.4.21 kurularak `compile_check.sh` yerelde çalıştırıldı.
- **Repo boyutu uyarısı**: vault JSON'ları git'e girmez (`.gitignore`). İleride
  benzer yığınlar `zip → Drive/R2` olarak saklanmalı; repo yalnız script + karar
  dosyalarını tutar. Chunk 002'de `worklists/` da `.gitignore`'a alındı: tam iş listesi
  63.284 satır üretip repoyu şişiriyordu (chunk 001 onu yanlışlıkla 963 satırda commit
  etmişti). `worklists/` yeniden üretilebilir ara çıktıdır → `worklist.py --min-count N`.
- Master RAW'da 91 yinelenen etiket temizlendi (2026-07-08); davranış değişmedi
  (byLabel ilk yazım kazanır).
- ⚠ **`gen_bangumi_master.py` VAULT satırlarını SİLER (ÇÖZÜLMEDİ).** Üreteç
  `BangumiOrgDictionary.kt`'yi şablondan **baştan yazar** (`%RAW_CHUNKS%` ← yalnız `orgs.tsv`),
  yani `--check` olmadan çalıştırılırsa VAULT'un eklediği **720 RAW satırı kaybolur**
  (1.733 → 1.013). CI'daki `Üreteç güncellik denetimi` adımı `--check` ile çalışır ve
  yazma yapmaz; ancak bu mod çıktıyla karşılaştırma yapmadığından gerçek bir güncellik
  denetimi değildir. Tehlike normal çalıştırmada sürer.
  Önerilen kalıcı çözüm: üreteci, anahtarı `orgs.tsv`'de olmayan mevcut RAW satırlarını
  koruyacak şekilde yamamak (sahiplik kuralı) — bu chunk'ta kapsam dışı bırakıldı.
  **Kural: `gen_bangumi_master.py`'yi `--check` olmadan ÇALIŞTIRMA.**
- **Yeniden üretme (rebuild) tarifi** — karar dosyalarında düzeltme yapınca:
  ```bash
  # 1) uygulama dosyalarını bilinen iyi duruma döndür (chunk N-1)
  git checkout <iyi-commit> -- app/src/main/res/values/strings.xml \
      app/src/main/res/values-en/strings.xml \
      app/src/main/java/com/kitsugi/animelist/utils/BangumiTagDictionary.kt \
      app/src/main/java/com/kitsugi/animelist/utils/BangumiOrgDictionary.kt
  # 2) kararları yeniden damgala + yeniden üret + doğrula
  python3 diag/bangumi_vault/scripts/apply_map.py          # --check DEĞİL: JSON'ları damgalar
  python3 diag/bangumi_vault/scripts/vault_to_strings.py
  python3 diag/bangumi_vault/scripts/verify_vault.py
  ```
  Sebep: `vault_to_strings.py` **yalnız ekler/günceller, silmez** → canon_id/string_key
  yeniden adlandırılırsa eski anahtar dil dosyasında ve sözlükte **artık** kalır
  (`verify_vault.py` bunu YAKALAMAZ çünkü yalnız vault→strings yönünü denetler).
- **Chunk 002 + 003'te yapılan dört araç düzeltmesi** (davranış iyileştirmesi, geriye dönük uyumlu):
  1. `scripts/worklist.py` — tüm kurum rolleri artık **tek** `orgs_pending.tsv`'de
     toplanıyor. Önceki sürüm her rolde dosyanın üzerine yazıyordu → yalnız son rol
     (committee, 1 kayıt) kalıyor, 3.081 kurum kayboluyordu.
  2. `scripts/verify_vault.py` — **7. kontrol (TERS YÖN) eklendi**: her `map_chunk_*.tsv`
     satırının kararı vault JSON'larına (kategori dosyaları + `vault.json` arşiv kopyaları)
     damgalanmış mı denetlenir. Önceden 1-6. kontroller yalnız **vault→strings** yönüne
     bakıyordu, bu yüzden damgası eksik/eski bir vault **sessizce ✅ geçiyordu**
     (kanıt: chunk 002 damgaları silindiğinde eski sürüm ✅ verdi, yeni sürüm
     "594 damga uyuşmazlığı → apply_map.py çalıştırılmalı" dedi). Çıktı artık damga
     sayısını da basar: `… 1662 map satırı, 121284 damga denetlendi`.
  3. `diag/bangumi_master/verify_bangumi_master.py` — çok-rollü kurum adları artık
     **dil dosyasından** da çözülüyor. Chunk 001'in RAW tekilleştirmesi 4 anahtarın
     (`bangumi_studio_ponoc`, `bangumi_producer_bilibili`, `bangumi_producer_cbc`,
     `bangumi_producer_disney_plus`) tüm satırlarını ikizine kaptırmıştı; doğrulayıcı
     bu yüzden Latin adı çözemeyip 10 zararsız çakışmayı "GERÇEK çakışma" diye
     raporluyordu (exit 1 → CI kırmızı). Düzeltme sonrası: **✅ 0 gerçek çakışma**,
     ayrıca `main`'de zaten var olan `神作 → masterpiece (beklenen classic)` hatası da
     gitti (VAULT `masterpiece` grubu etiketi sahiplendi).
     Not: bu dosya chunk 001 öncesinde `main`'de de exit 1 veriyordu.
  4. Chunk 003: `vault_to_strings.py` ve `verify_vault.py` tag canon id'sini kurum
     `string_key`'inden bağımsız işler; her kararlı etiket için
     `bangumi_tag_<tag.key>` kaynağı ve `BangumiTagDictionary` grubu zorunlu.
     Böylece roller arası kaynak kullanan etiketler de gerçek UI lookup zincirinde çözülür.

## 8. Kapanış kontrol listesi (chunk sonlarında)

Chunk 004a için durumu (2026-10-21) → her satır **✅ tamam**:

- [x] `draft_org_map.py --chunk 004a --min-count 60 --max-count 99` → 567 map + 501 canon satırı
- [x] `apply_map.py --check` temiz (1.646 canon · 2.229 map · 1.894 canon + 335 skip)
- [x] `verify_vault.py` ✅ (2.948 `bangumi_*` anahtar · 1.568 vault kaydı · 2.229 map satırı · 121.284 damga)
- [x] `verify_bangumi_master.py` ✅ (2.365 eşleme · gerçek çakışma 0 · TR/EN 9.113/9.113)
- [x] CI üreteç denetimi: `gen_bangumi_master.py --check` yazma yapmadan temiz
- [x] `strings.xml` geçerli XML; `bangumi_*` anahtarlarında yinelenen yok, TR/EN simetrik (9.113 = 9.113)
- [x] RAW parça sınırı: 3 parça, en büyük **49.976** bayt (< 50.000 modified-UTF-8)
- [x] `PROGRESS.md` + `progress.json` güncellendi
- [x] Drive'a yüklendi: PROGRESS.md, progress.json, decisions/canon.tsv,
      decisions/map_chunk_004a_orgs.tsv, decisions/org_global_names.tsv
- [x] Git commit + push (dal `arena/6131dd54-kitsugi-beta`) + PR
- [ ] **Not (yerel derleme):** bu oturumda JDK/kotlinc kurulu değildi; `compile_check.sh`
      çalıştırılamadı. Kotlin tarafı yalnız `verify_bangumi_master.py` + `gen --check` +
      RAW parça bayt denetimiyle doğrulandı; asıl derleme CI'da (kotlinc adımı) koşar.

Chunk 003 için durumu (2026-10-21) → her satır **✅ tamam**:

- [x] `apply_map.py --check` temiz (1.145 canon · 1.662 map · 1.353 canon + 309 skip)
- [x] `verify_vault.py` ✅ (2.491 `bangumi_*` anahtar · 1.089 vault kaydı · 1.662 map satırı · 121.284 damga)
- [x] `verify_bangumi_master.py` ✅ (1.733 eşleme · gerçek çakışma 0)
- [x] CI üreteç denetimi: `gen_bangumi_master.py --check` yazma yapmadan temiz; `git add -A` sonrası `git diff --quiet`
- [x] `strings.xml` geçerli; `bangumi_*` anahtarlarında yinelenen yok, TR/EN simetrik (8.628 = 8.628)
- [x] `PROGRESS.md` + `progress.json` güncellendi
- [x] Drive'a yüklendi: PROGRESS.md, progress.json, translations_pending.tsv, translations_done.tsv,
      decisions/canon.tsv, map_chunk_001_orgs.tsv, map_chunk_001_tags.tsv,
      map_chunk_002_tags.tsv, map_chunk_003_tags.tsv, org_global_names.tsv
- [x] Git commit + push + PR #18 açıldı (`arena/6cb51a64-kitsugi-beta`; https://github.com/f26901964-eng/Kitsugi-Beta/pull/18)

### PR #18 CI takip — 2026-10-21

- Önceki Actions run `39521496766`: `verify` geçti; `compile-and-test`, kotlinc adımında
  başarısız oldu. Yerel tekrar kök nedeni verdi: `BangumiOrgDictionary.kt` içindeki 1.733
  satırlık tek `RAW` sabiti JVM class-file modified-UTF-8 sınırını aşıyordu. Hata Chunk 003'e
  özgü değil; Chunk 002 sonrası 1.724 satırda da sınır aşılmıştı.
- Kalıcı düzeltme: RAW, 50.000 modified-UTF-8 baytı aşmayan parçalara bölünür ve çalışma
  anında birleştirilir. MASTER üreteci ve VAULT RAW yazıcısı aynı biçimi üretir; iki doğrulayıcı
  ve `vault_to_strings.py` yeni biçimi okuyup yazar. VAULT verileri/kararları değişmedi.
- RAW düzeltmesi commit'i `4cbd2e0` push edilince yeni run `39564791092`'de `verify` ve
  `Sözlükleri derle (kotlinc)` geçti; Gradle/JUnit adımı tam uygulamayı derlerken 28 eski
  `stream_*` string anahtarının TR/EN kaynaklarından eksik olduğunu buldu. Anahtarlar base
  `13349e1`'de mevcuttu; önceki kaynak dosyası değişikliğinde düşmüşler. İki dil dosyasına
  base değerleriyle geri eklendiler; `Android Derleme Doğrulama` da aynı eksikleri bildirmişti.
- String düzeltmesi commit'i `938ac40` push edildikten sonra run `39565480946`'de `verify`,
  sözlük kotlinc harness'ı ve tam uygulama Kotlin derlemesi geçti; `testFossDebugUnitTest` ise
  `MalWebParserTest.followsOnlyObservedSameOriginNextPageLinks` ve
  `MalNotificationsClientTest.acceptsOnlySameOriginNotificationContinuationUrls` testlerinde
  başarısız oldu. Android `build-debug` run `39565476427` aynı test hatalarını verdi.
  İki test ve MAL parser/client dosyaları PR base `13349e1`'den beri değişmedi; Bangumi düzeltmesi
  bunlarla ilişkili değil. Bu, ayrı bir takip konusu olarak bırakıldı.

Chunk 002 için durumu (2026-10-21) → her satır **✅ tamam**:

- [x] `apply_map.py --check` temiz (879 canon · 1.171 map · 998 canon + 173 skip)
- [x] `verify_vault.py` ✅ (2.242 `bangumi_*` anahtar · 855 vault kaydı · 1.171 map satırı · 121.284 damga)
- [x] `verify_bangumi_master.py` ✅ (1.724 eşleme · GERÇEK çakışma 0) — **yeni adım**
- [x] CI üreteç denetimi: `gen_bangumi_master.py --check` sonrası `git diff --quiet` temiz
- [x] `strings.xml` XML olarak geçerli, `bangumi_*` anahtarlarında **yinelenen yok**,
      TR/EN simetrik (8.379 = 8.379)
- [x] `PROGRESS.md` + `progress.json` güncellendi
- [x] Drive'a yüklendi: PROGRESS.md, progress.json, translations_pending.tsv,
      translations_done.tsv, decisions/canon.tsv, map_chunk_*.tsv, org_global_names.tsv
- [x] Git commit (yalnız scripts/ + decisions/ + PROGRESS + uygulama dosyaları; veri git'te yok)

Sonraki chunk'lar için aynı liste geçerli; `gen_bangumi_master.py`'yi **`--check` olmadan
çalıştırma** (Bölüm 7 ⚠).
