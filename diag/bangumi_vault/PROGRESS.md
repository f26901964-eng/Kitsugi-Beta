# Bangumi VAULT — İlerleme / Devam Planı

> **Bu dosya devir teslim dosyasıdır.** Yeni bir dal/şube işi buradan devralır.
> Son güncelleme: **2026-10-21** · **S1–S3 Classpath TSV Mimari Geçişi + Chunk 008 (376 count=0 kurum) + 838 CJK Canon Düzeltmesi + Chunk 007 (`007_a`–`007_j` · 59.510 etiket)** TAMAMLANDI (%100).
> **GÜNCEL DAL:** **`arena/b4827842-kitsugi-beta`** (`main` `v2.4.331` tabanlı; 3 resource shard modülü + `-Xss16m` korundu).
> **Sıradaki iş: YOK (`translations_pending.tsv = 0` satır · kararsız kurum = `0` · kararsız etiket = `0`).**
> **Güncel Otorite Sağlık Hedefi:** **`52.219 bangumi_* anahtar · 106.335 vault kaydı · 121.284 map satırı · 121.284 damga`** (`tags.tsv`: **58.338 grup**, `orgs.tsv`: **52.474 satır**, `strings.xml`: **TR 58.398 · EN 58.398** — Android 65.535 DEX ve JVM constant pool sınırı aşılmadan sıfır yeni XML ID ile tamamlandı).
> Aşağıdaki eski PR/dal açıklamaları TARİHÇEDİR; başka dala geçme/reset yapma.
> Chunk 005b, aynı dalda olduğu için **PR #23** içinde taşınıyor (005a ile birlikte):
> https://github.com/f26901964-eng/Kitsugi-Beta/pull/23
> Chunk 005a PR'ı: https://github.com/f26901964-eng/Kitsugi-Beta/pull/23 (dal `arena/1e0c5ecd-kitsugi-beta`).
> Chunk 004c PR'ı: https://github.com/f26901964-eng/Kitsugi-Beta/pull/22 — **`main`'e MERGE EDİLDİ** (2026-10-21).
> Dal `arena/c54b58d5-kitsugi-beta`; chunk 001+002+003+004a+004b+004c'nin tamamını taşır. Artık yeni dal
> `main`'den açıldığında tüm dosyalar **hazır gelir** (fetch/reset gerekmez).
> Chunk 004b PR'ı: https://github.com/f26901964-eng/Kitsugi-Beta/pull/21 (dal `arena/fbf31f34-kitsugi-beta`; chunk 001+002+003+004a'yı da taşır, `main` v2.4.326 ile birleşik)
> Chunk 004a PR'ı: https://github.com/f26901964-eng/Kitsugi-Beta/pull/20 (**dal `arena/6131dd54-kitsugi-beta`; chunk 001+002+003'ü de taşır**)
> Chunk 003 PR'ı: https://github.com/f26901964-eng/Kitsugi-Beta/pull/18 (dal `arena/6cb51a64-kitsugi-beta`)
> ⚠ `main` bu dalın tabanından sonra ilerledi (2.4.321–324); yeni dal **`origin/main` ile birleştirilmiş** hâlde
> `arena/6131dd54-kitsugi-beta` üzerinden devam eder. Chunk 003 dalı (`6cb51a64`) artık geride kaldı.
> Chunk 002 PR'ı: https://github.com/f26901964-eng/Kitsugi-Beta/pull/16 (önceki kaynak dal)
> Chunk 001 PR'ı: https://github.com/f26901964-eng/Kitsugi-Beta/pull/15
> Drive klasörü: https://drive.google.com/drive/folders/1pZAOYsAnMjveNK6grdXjqV9IDM1TFaJp
> ⚠ **Drive dosya kimlikleri sabit değildir:** içerik her güncellendiğinde
> `delete_file` → `upload_file` yapıldığı için **yeni bir kimlik** oluşur. Aşağıda/2k'da
> yazan kimlikler **tarihli**dir; güncel kimliği her zaman **klasör listesinden ada göre**
> doğrula, kimliği ezberden kullanma.

---

## 0. YENİ DAL / YENİ ORTAM KURULUMU — ÖNCE BURAYI OKU

Yeni bir dalda işe başlayan ajan SIFIRDAN araştırma yapmaz; şu sırayı izler:

### a) Dal kurulumu (KRİTİK)
- **005c sonrası güncel:** çalışma dalı `arena/06f200e9-kitsugi-beta`; yeni oturumda
  yalnız kendi sabit dalını kullan. Eski PR/dal notları aşağıda tarihçe olarak korunur.
  Sağlık hedefi ve sıradaki iş için üstteki güncel devir + Bölüm 0e / 2d esastır.
- **Güncel durum (2026-10-21, chunk 004c sonrası):** chunk 001 + 002 + 003 + 004a + 004b + **004c**'nin
  tamamı **`main`'e merge edildi** (PR #22). `main` artık bu işi içerir → yeni oturum dalı `main`'den
  açıldığında `diag/bangumi_vault/` **hazır gelir**; fetch/reset GEREKMEZ.
  Yalnız eski/boş bir checkout'ta (dal `main`'in merge öncesi hâlinden açıldıysa) Bölüm 0e'deki
  **iki komut** (explicit refspec + `git reset --hard`) yedek yol olarak kullanılır.
- Önceki kaynak: chunk 001–004a işi dal `arena/6131dd54-kitsugi-beta` · **PR #20**'de
  (HEAD `1bc9cbd`; chunk 004b bunun üzerine kuruludur). PR #21 bunların tümünü taşır.
- Eski kaynaklar: Chunk 001 işi commit `5c13f8e` dal `arena/a3fae7fe-kitsugi-beta` üzerinde ve **PR #15**'te
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

✅ **Chunk 005c sonrası güncel restore hedefi**: Drive ham JSONları eski damgalar
  taşıyabilir. Git'teki tüm map dosyalarıyla `apply_map.py` çalıştırılınca
  `verify_vault.py` **49.561 anahtar · 48.382 kayıt · 57.371 map · 121.284 damga** vermelidir.

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

### e) Yeni ajanı BAŞLATMA KOMUTU — güncel (005c sonrası)

1. Yalnız bu devir dosyasını kaynak al. Kendi Arena oturum dalında kal; başka dala geçme,
   reset/merge yapma. 001–005c tamamlandı; yeniden işleme. `git fetch --no-tags origin` yeterli.
2. `decisions/*.tsv` git'te esastır. Ignore JSONlar yoksa Bölüm 0b'den indir; ham yedek al;
   `apply_map.py` → `vault_to_strings.py` → `verify_vault.py` çalıştır.
3. Güncel sağlık hedefi **49561 anahtar · 48382 kayıt · 57371 map · 121284 damga**.
4. Sıradaki açık kutu **006: etiket count 20–99 (4.027)**. Bölüm 4 / karar sözlüğüyle elle
   gözden geçir. Kurum taslak üreticisi etiket çevirisi yerine GEÇMEZ.
5. Bilinmeyen özel adlara Latin okunuş uydurma. Her iki dil alanı dolu olmalı; özel adlar
   as_is kalabilir. SKIP dil dosyasına girmez. Yeni değer eskiyle aynıysa eski anahtara bağla;
   kısa cid / hash eşitliği varlık kanıtı DEĞİLDİR.
6. `gen_bangumi_master.py` **yalnız --check**. MAL kodu/testleriyle ilgilenme. Bilinen
   `BangumiMasterDictionaryTest` CI hatasını yeni kırılma sanma (Bölüm 8 PR #23 takip).
7. Kapanış: tüm doğrulamalar → devir/kararlar ve Drive güncelle → commit/push kendi dala →
   PR. Yeni ortama bu oturumun ignore `worklists/` / `.cache/` dosyalarının gelmesini bekleme.


---

## 1. İşin amacı (kullanıcı talebi)

Bangumi VAULT yığınlarındaki (tags, genres, producers, publishers, studios, networks,
magazines, committees, vault, translations_pending.tsv) her etiket/kurum için:

1. Uygulamada **global karşılığı** varsa ona bağla (adı global gelenekle uyumlu),
2. Yoksa **TR + EN çevirisi** üret ve dil dosyalarına ekle,
3. Sonunda **uygulamaya tam entegrasyon** (strings.xml + BangumiTagDictionary +
   BangumiOrgDictionary + vault JSON karar alanları).
4. Çalışma **parça parça (chunk)** ilerler; her turdan sonra bu dosya güncellenir.

## 2. Mevcut durum — CHUNK 001–005c ✓

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

### 2f) CHUNK 004b (TAMAM ✓ · 2026-10-21) — kurum count 35–59

| Rol | Kararlı | SKIP | Toplam |
|---|---:|---:|---:|
| studio | 203 | 7 | 210 |
| producer | 358 | 4 | 362 |
| publisher | 282 | 6 | 288 |
| magazine | 114 | 2 | 116 |
| network | 24 | 0 | 24 |
| **Toplam** | **981** | **19** | **1.000** |

981 kararlı kaydın `canon.tsv` origin dağılımı: **global 238 · as_is 630**
(357 Latin + 273 bilinmeyen CJK) **· existing 113** (45 doğrudan + 68 roller arası birleşim).

- **global (238)**: `org_global_names.tsv` bilgi tabanına bu chunk'ta **284** yeni resmî Latin ad
  eklendi (toplam 1.045 veri satırı). Örnekler: `白組`→Shirogumi, `カイロソフト`→Kairosoft,
  `エウシュリー`→Eushully, `ディンプス`→Dimps, `サイバーコネクトツー`→CyberConnect2,
  `神風動画`→Kamikaze Douga, `ナツメ`→Natsume, `彩京`→Psikyo, `天美工作室群`→TiMi Studio Group,
  `扶桑社`→Fusosha, `マガジンハウス`→Magazine House, `早川书房`→Hayakawa Shobo,
  `万普`/`バンプレスト`→Banpresto, `光栄`→Koei, `艺夺`→Eidos Interactive,
  `京都动画`→Kyoto Animation; dergi kanonu: `ファミ通`→Famitsu, `ガロ`→Garo,
  `週刊コミックバンチ`→Weekly Comic Bunch, `別冊少年チャンピオン`→Bessatsu Shonen Champion,
  `月刊少年ジャンプ`→Monthly Shonen Jump … Kişilerde yalnız resmî Latin yazımı bilinenler:
  Yasushi Akimoto, Hideaki Anno, Hiroshi Yamauchi, Takashi Takeuchi, Akihiro Hino,
  Kenji Horikawa, Tetsuya Nakatake, Yuji Nunokawa, Shinji Hashimoto, Yoshinori Kitase,
  Katsura Hashino, Li Haoling, Gong Yu, Sun Zhonghuai, Shen Leping …
- **existing (113)**: mevcut string anahtarına birleşim — `戏画`→`bangumi_studio_giga`,
  `コミックバーズ`→`bangumi_magazine_monthly_birz`, `日本テレビ系`→`bangumi_network_ntv`,
  `ニコニコ生放送`→`bangumi_network_niconico`, `京都动画`→`bangumi_studio_kyoto_animation`,
  `万普`→`bangumi_publisher_banpresto`, `簑岛正佳`→`bangumi_producer_masayoshi_minoshima`,
  `天使音符`→`bangumi_producer_angel_note` … TR/EN değerleri **dil dosyasından birebir** korunur.
- **as_is (630)**: 357 Latin ad + 273 bilinmeyen CJK (romanizasyon **uydurulmadı**).
- **SKIP (19)**: ad parçası/gürültü — `株式会社`, `ソフト`, `放送`, `放送網`, `ビジュアル`,
  `マンガ`, `日本`, `メディア`, `メディアワ)`, tek kana `オー`/`エル`/`ティー`/`エヌ`/`ケー`,
  `ファースト` (SKIP_NAMES listesi bu chunk'ta genişletildi).

Uygulama çıktıları (`verify_vault.py` + `verify_bangumi_master.py` ile doğrulandı ✅):

- `values/strings.xml` + `values-en/strings.xml`: **+864 TR / +864 EN** `<string>` (toplam 9.991/9.991;
  mevcut değer üzerine yazma **0**).
- `BangumiOrgDictionary.kt` RAW: **+1.108 satır** (2.365 → 3.473; **5 parça**, en büyük 49.990 bayt < 50.000).
- `BangumiTagDictionary.kt`: **+0 grup, +0 etiket** (bu chunk yalnız kurum).
- `decisions/canon.tsv`: **+936 satır** (1.646 → 2.582) · `map_chunk_004b_orgs.tsv`: **1.000 satır**.
- `decisions/org_global_names.tsv`: **+284 satır** (761 → 1.045 veri satırı).
- `translations_done.tsv` / `translations_pending.tsv`: **değişmedi** (kurum kayıtları pending'te yok; 62.957).

Araç değişiklikleri (geriye dönük uyumlu):
- `scripts/draft_org_map.py`: `SKIP_NAMES` chunk 004b'de görülen ad parçaları/tek kana ile genişletildi
  (yukarıdaki 19 SKIP).

### 2g) CHUNK 004c (TAMAM ✓ · 2026-10-21) — kurum count 20–34

| Rol | Kararlı | SKIP | Toplam |
|---|---:|---:|---:|
| studio | 357 | 1 | 358 |
| producer | 599 | 8 | 607 |
| publisher | 353 | 12 | 365 |
| magazine | 136 | 2 | 138 |
| network | 44 | 2 | 46 |
| **Toplam** | **1.489** | **25** | **1.514** |

1.514 benzersiz vault anahtarı işlendi (Bölüm 5'teki 1.515 sayısı, `committees.json`'daki
aynı key'in kopyasını ayrı sayıyordu). 1.489 kararlı kaydın `canon.tsv` origin dağılımı:
**global 282 · as_is 1.097** (410 Latin + 687 bilinmeyen CJK) **· existing 110**
(6 doğrudan + **104 roller arası birleşim**).

- **global (282)**: `org_global_names.tsv` bilgi tabanına bu chunk'ta **+271** resmî Latin ad
  eklendi (1.045 → **1.316** veri satırı). Örnekler: `スタジオKAI`→Studio KAI,
  `プラチナゲームズ`→PlatinumGames, `ポリゴン・ピクチュアズ`→Polygon Pictures, `朱夏`→Shuka,
  `ヴァニラウェア`→Vanillaware, `ヘキサドライブ`→HexaDrive, `バイブリーアニメーションスタジオ`→
  Bibury Animation Studios, `開罗软件`→Kairosoft, `淘米网络`→Taomee, `光子工作室群`→Lightspeed
  Studios, `华特·迪士尼动画工作室`→Walt Disney Animation Studios, `黑马漫画`→Dark Horse Comics,
  `宝可梦股份有限公司`→The Pokémon Company, `游戏橘子`→Gamania, `キングレコード株式会社`→King Records,
  `ソニー・ミュージックエンタテインメント`→Sony Music Entertainment, `东立出版社有限公司`→Tong Li
  Publishing, `中央电视台`→China Central Television (CCTV), `NHK総合テレビ`→NHK General TV,
  `ABC朝日放送`→Asahi Broadcasting Corporation, `カンテレ`→Kansai TV; dergi kanonu:
  `ヤングマガジンアッパーズ`→Young Magazine Uppers, `月刊Comic Garden`→Monthly Comic Garden,
  `电击文库MAGAZINE`→Dengeki Bunko Magazine, `ヤンマガWeb`→YanMaga Web, `コミックシーモア`→Comic Cmoa.
  Kişilerde yalnız resmî Latin yazımı bilinenler (15): Shigeru Miyamoto, Shinji Mikami,
  Toshihiro Nagoshi, Takashi Tezuka, Junichi Masuda, Ryozo Tsujimoto, Masakazu Kubo,
  Yasuyoshi Tokuma, Kensuke Tanaka, Naoya Shimokawa, Takanori Aki, Hiroaki Matsuura,
  Yuito Kimura, Kenichiro Takaki, Iriya Azuma.
- **existing (110)**: mevcut string anahtarına birleşim — `東映`→`bangumi_studio_toei_animation`,
  `コーエー`→`bangumi_publisher_koei`, `フロム`→`bangumi_studio_fromsoftware`,
  `白組`→`bangumi_studio_shirogumi`, `アスリード`→`bangumi_studio_asread`,
  `레진코믹스(레진엔터테인먼트)`→`bangumi_magazine_lezhin_comics`,
  `キッズステーション`→`bangumi_network_kids_station`. Bu 104 kayıt TR/EN değerlerini
  **dil dosyasından birebir** korur.
- **as_is (1.097)**: 410 Latin ad (marka/kısaltma) + 687 bilinmeyen CJK (romanizasyon
  **uydurulmadı**). Latin `as_is` örnekleri: TYPE-MOON, CUBE, VISUAL ARTS, TGL, feng, qureate,
  Hulotte, Gameloft, Rusty Lake, Yostar Pictures, GEEKTOYS, bomtoon, Piccoma…
- **SKIP (25)**: ad parçası/gürültü — `メディア`(×2), `ジャパン`, `エム`, `ビデオ`, `エル`,
  `ユニバーサル`, `グループ`, `オー`, `エヌ`, `ティー`, `ソフトウェア`, `エー`, `エフ`,
  bölge etiketi `台湾`, `USA`, URL parçası `http:`(×2), hukuki kısaltma `LLC`, belirsiz `PC`/`SE`/`ESP`,
  `発売元:モモグレ` (yayıncı öneki), `(オトメドラマ)` (parantez gürültüsü), `インディーズ・メーカー`.

İkiz kayıt birleştirmeleri ve elle düzeltmeler:
- Aynı varlığın Latin/raw varyantları aynı `canon_id`'ye bağlandı → ikinci string anahtarı
  üretilmedi: **Konami → konami**, **Namco → namco**, **TYPE-MOON → type_moon**,
  **CHUNSOFT → chunsoft**, **CONNECT → connect**, **PINE JAM → pine_jam**,
  `ACQUIRE`/`Wea`/`COMIC HEAVEN`/`GRAND JUMP PREMIUM`/`MANGA TIME` (değer birliği).
- Yanlış otomatik birleşimler elle düzeltildi (üçü de **yeni** canon satırı aldı):
  `漫画SHOW` → artık prodüktör `Show隈部` anahtarına değil `manga_show`'a;
  `ジャンプGIGA` → studio `Giga` anahtarına değil `jump_giga` (Jump GIGA)`'ya;
  `ALcotハニカム` → `alcot_honey_comb` (ALcot ile karışmıyor; resmî Latin yazımı
  doğrulanamadığı için `as_is`).
- **committee notu**: `draft_org_map.py` `ORG_FILES`'ta `committee` yok, ancak 004c'deki tek
  committee kaydı (`MY PROJECT`, count 23) `producers.json`'da **aynı key** ile yer aldığı için
  taslak onu producer rolünde kapsadı; `apply_map.py` anahtar bazlı damgaladığı için
  `committees.json` kopyası da işlendi → ayrı elle satır **gerekmedi**.

Uygulama çıktıları (`verify_vault.py` + `verify_bangumi_master.py` ile doğrulandı ✅):

- `values/strings.xml` + `values-en/strings.xml`: **+1.320 TR / +1.320 EN** `<string>`
  (toplam 11.311/11.311; mevcut değer üzerine yazma **0**).
- `BangumiOrgDictionary.kt` RAW: **+1.540 satır** (3.473 → **5.013**; **6 parça**,
  en büyük 49.990 bayt < 50.000). `BangumiTagDictionary.kt`: **+0 grup, +0 etiket**.
- `decisions/canon.tsv`: **+1.422 satır** (2.582 → **4.004**) · `map_chunk_004c_orgs.tsv`: **1.514 satır**.
- `decisions/org_global_names.tsv`: **+271 satır** (1.045 → 1.316).
- `translations_done.tsv`: 1.662 → **4.743** · `translations_pending.tsv`: **değişmedi** (62.957;
  kurum kayıtları pending'te yok).

Araç değişiklikleri (geriye dönük uyumlu):
- `scripts/draft_org_map.py`: `SKIP_NAMES` chunk 004c'de bölge/şablon gürültüsü, URL parçası,
  hukuki kısaltma, belirsiz kısaltma ve tek kana ile genişletildi (yukarıdaki 25 SKIP).

### 2h) CHUNK 005a (TAMAM ✓ · 2026-10-21) — kurum count 10–19

PR: https://github.com/f26901964-eng/Kitsugi-Beta/pull/23 (dal `arena/1e0c5ecd-kitsugi-beta`)

| Rol | Kararlı | SKIP | Toplam |
|---|---:|---:|---:|
| studio | 708 | 13 | 721 |
| producer | 1.128 | 18 | 1.146 |
| publisher | 690 | 12 | 702 |
| magazine | 219 | 8 | 227 |
| network | 101 | 0 | 101 |
| **Toplam** | **2.846** | **51** | **2.897** |

2.897 kaydın `canon.tsv` origin dağılımı (2.743 benzersiz (rol, canon) satırı):
**as_is 2.209** · **global 310** · **existing 224** (7 doğrudan + 217 roller arası birleşim).

- **global (310)**: `org_global_names.tsv` bilgi tabanına bu chunk'ta **+392** resmî Latin ad eklendi
  (1.316 → **1.708** veri satırı). Ağırlık TV/network ve dergilerde: `日本サンライズ`→Sunrise,
  `スタジオぴえろ`→Studio Pierrot, `ボンズ`→BONES, `マッドハウス`→Madhouse, `吉卜力工作室`→Studio Ghibli,
  `虫プロダクション`→Mushi Production, `東京ムービー`→Tokyo Movie, `国際映画社`→Kokusai Eiga-sha,
  `スパイク・チュンソフト`→Spike Chunsoft, `アリスソフト`→AliceSoft, `ニトロプラス`→Nitroplus,
  `宝开游戏`→PopCap Games, `財宝公司`→Treasure, `白金工作室`→PlatinumGames, `网石游戏`→Netmarble,
  `鹰角网络`→Hypergryph, `叠纸游戏`→Papergames, `追光动画`→Light Chaser Animation Studios,
  `原力动画`→Original Force, `猫眼影业`→Maoyan Pictures, `策略第一`→Strategy First;
  dergilerde `集英社オレンジ文庫`→Shueisha Orange Bunko, `月刊ドラゴンマガジン`→Monthly Dragon Magazine,
  `別冊ヤングチャンピオン`→Bessatsu Young Champion, `娘TYPE`→Nyan Type, `ココハナ`→Cocohana;
  TV'de `毎日放送`→Mainichi Broadcasting System, `北海道放送`→Hokkaido Broadcasting,
  `テレビユー福島`→TV-U Fukushima, `ＢＳ日テレ`→BS Nippon TV, `テレビ朝日`/`フジテレビ` türevleri.
  Kişilerde yalnız resmî Latin yazımı bilinenler: Makoto Shinkai, Hironobu Sakaguchi, Keiji Inafune,
  Hideo Kojima, Shu Takumi, Gunpei Yokoi, Toshio Okada, Yoko Kanno, Gen Urobuchi, Kinoko Nasu,
  Leiji Matsumoto, Akiyuki Shinbo, Eiji Aonuma, Naoki Yoshida … (Çince adlarda yalnız çok bilinenler:
  Wong Jing, Yu Zheng, Zhang Jizhong, Yao Zhuangxian).
- **existing (224)**: mevcut string anahtarına birleşim — değerler **dil dosyasından birebir** korunur.
  Doğrulananlar: `日本テレビ系列`→`bangumi_network_ntv`, `富士电视台`/`富士台`→`bangumi_network_fuji_tv`,
  `英国广播公司`→`bangumi_network_bbc`, `福島テレビ`→`bangumi_network_ftv`, `北海道放送`→`bangumi_network_hbc`,
  `チバテレ`→`bangumi_network_chiba_tv`, `毎日放送`→MBS, `東宝株式会社`/`东宝株式会社`→`bangumi_producer_toho`,
  `テレビ朝日`→`bangumi_producer_tv_asahi`, `アニマックス`→`bangumi_network_animax`,
  `腾讯`→`bangumi_producer_tencent`, `ミヤギテレビ`→`bangumi_producer_miyagi_tv` …
  (`*講談社`→Kodansha, `*小学館`→Shogakukan, `*电击文库`→Dengeki Bunko — asterisk veri gürültüsü).
- **as_is (2.209)**: 908 Latin ad + 1.299 bilinmeyen CJK + 2 cid düzeltmesi (romanizasyon **uydurulmadı**).
- **SKIP (51)**: ad parçası/gürültü ve bölge etiketleri — `上海`, `北京`, `北美`, `韩国`, `台灣`,
  `(日本)`, `中心`, `最初版`, `新装版`, `増刊`, `半月刊`, `システム`, `パブリッシャーズ`,
  `アニメーションスタジオ`, `エンタープライズ`, `グローバル`, `文化传播有限公司`,
  `制作统括：`, `发行`, `企画协力：`, `；宣传制片人：`, `；製作協力：`, `)；制作制片人：`,
  `発売元:ひつじぐも`, `(羽根ロム+α)`, `△○□×`, `【チーム`, `10)`, `電撃「マ 王`,
  `→`, `)→`, `&フラワー`, `(パルシィ)`, `ヤング`, `コミック`, `オンライン`, `レコード`, `テレビ`,
  belirsiz/kesik Latin ad `The` … → `draft_org_map.py` `SKIP_NAMES`'e eklendi.

İkiz kayıt birleştirmeleri ve elle düzeltmeler (Bölüm 6 "cid çakışması tuzağı"):
- **Aynı varlık iki kayıtta → tek `canon_id`**: `MAHO FILM` (studio+producer → `maho_film`),
  `NUT` (studio+producer → `nut`), `DMM GAMES` (studio+publisher → `dmm_games`),
  `パイオニアLDC` (studio+producer → `pioneer_ldc`), `BANDAI`/`Bandai` (publisher×2 → `bandai`),
  `lezhin`/`Lezhin` (magazine×2 → `lezhin`), `J.J.Abrams`/`J. J. Abrams` → `j_j_abrams`.
  Bu olmasaydı `dmm_games`/`dmm_games_3`, `maho_film`/`maho_film_2`, `nut`/`nut_2` gibi ikinci anahtarlar doğardı.
- **Yanlış otomatik birleşimler düzeltildi** (4 kayıt yeni cid aldı, 1 kayıt SKIP):
  `bangumi_studio_g` (デジタルGパワー) `bangumi_magazine_g`=月刊Gファンタジー yerine `digital_g_power`;
  `bangumi_network_f` (木曜ドラマF) `bangumi_magazine_f`=ネクストF yerine `thursday_drama_f`;
  `bangumi_magazine_live` (ジャンプLIVE) `bangumi_studio_live` yerine `jump_live` (global);
  `bangumi_publisher_the` (岸田教団&THE明星ロケッツ) `bangumi_magazine_the` yerine
  `kishida_kyoudan_the_akeboshi_rockets` (global); `bangumi_studio_the` ("The", kesik/belirsiz) → SKIP.
- **Mevcut kısa anahtar tuzağı (yeni)**: kısa cid'ler başka varlıkların anahtarına denk geliyordu →
  `長崎放送` `bangumi_network_nbc`=NBC (US) yerine `nagasaki_broadcasting`;
  `びわ湖放送` `bangumi_network_bbc`=BBC yerine `biwako_broadcasting`;
  `三重テレビ放送` `bangumi_network_mtv`=MTV yerine `mie_tv`; `南日本放送` `mbc` (韩国MBC) yerine
  `minaminihon_broadcasting`. Ders: **kısa kısaltma cid'i yalnız o varlığa ait olduğu doğrulanınca kullanılmalı.**

Uygulama çıktıları (`verify_vault.py` + `verify_bangumi_master.py` ile doğrulandı ✅):

- `values/strings.xml` + `values-en/strings.xml`: **+2.519 TR / +2.519 EN** `<string>`
  (toplam **13.830 / 13.830**; mevcut değer üzerine yazma **0**).
- `BangumiOrgDictionary.kt` RAW: **+2.785 satır** (5.013 → **7.798**; **10 parça**,
  en büyük 49.999 bayt < 50.000). `BangumiTagDictionary.kt`: **+0 grup, +0 etiket**.
- `decisions/canon.tsv`: **+2.743 satır** (4.004 → **6.747**) · `map_chunk_005a_orgs.tsv`: **2.897 satır**.
- `decisions/org_global_names.tsv`: **+392 satır** (1.316 → 1.708).
- `translations_done.tsv`: 4.743 → **7.640** · `translations_pending.tsv`: **değişmedi** (62.957;
  kurum kayıtları pending'te yok).

Araç değişiklikleri (geriye dönük uyumlu):
- `scripts/draft_org_map.py`: `SKIP_NAMES` chunk 005a'da görülen bölge etiketleri/jenerik kelimeler/kesik
  adlarla genişletildi (yukarıdaki 51 SKIP). Başka davranış değişikliği yok.

### 2i) CHUNK 005b (TAMAM ✓ · 2026-10-21) — kurum count 5–9

PR: https://github.com/f26901964-eng/Kitsugi-Beta/pull/23 — başlık `v2.4.327–328 … chunk 005a + 005b`
(dal `arena/1e0c5ecd-kitsugi-beta`; 005a ile aynı PR'da, tek dal kuralı).

| Rol | Kararlı | SKIP | Toplam |
|---|---:|---:|---:|
| studio | 966 | 13 | 979 |
| producer | 1.770 | 34 | 1.804 |
| publisher | 1.055 | 21 | 1.076 |
| magazine | 272 | 7 | 279 |
| network | 165 | 8 | 173 |
| **Toplam** | **4.228** | **83** | **4.311** |

4.311 kaydın `canon.tsv` origin dağılımı (4.026 benzersiz (rol, canon) satırı):
**as_is 3.421** · **global 220** · **existing 385**.

- **global (220)**: bilgi tabanına **+276** resmî Latin ad eklendi (1.708 → **1.984** veri satırı).
  Öne çıkanlar: `セガ`→SEGA, `アクアプラス`→AQUAPLUS, `アトラス`→Atlus, `ゲームアーツ`→Game Arts,
  `グループ・タック`→Group TAC, `オーバーフロー`→Overflow, `スタジオジブリ`→Studio Ghibli,
  `東映動画`→Toei Doga, `神風動画`→Kamikaze Douga, `シリコンスタジオ`→Silicon Studio,
  `バンダイ`→Bandai, `タカラトミー`→Takara Tomy, `ディアファクトリー`→Media Factory,
  `ドワンゴ`→Dwango, `ミュージックレイン`→MusicRay\'n; TV ağları `テレビ和歌山`→TV Wakayama,
  `青森放送`→Aomori Broadcasting, `チューリップテレビ`→Tulip TV, `読売テレビ`→Yomiuri TV,
  `九州朝日放送`→Kyushu Asahi Broadcasting; Çin/Tayvan `腾讯游戏`→Tencent Games,
  `阅文集团`→China Literature, `西山居`→Seasun Games, `大宇资讯`→Softstar Entertainment,
  `快看漫画`→Kuaikan Comics, `波希米亚互动工作室`→Bohemia Interactive,
  `黑曜石娱乐`→Obsidian Entertainment, `台湾国际角川书店`→Taiwan Kadokawa;
  kişilerde yalnız resmî yazımı bilinenler (押井守→Mamoru Oshii, 新海誠→Makoto Shinkai,
  河森正治→Shoji Kawamori, 野村哲也→Tetsuya Nomura, 宮崎英高→Hidetaka Miyazaki,
  高橋哲哉→Tetsuya Takahashi, 徐克→Tsui Hark, あかほりさとる→Satoru Akahori …).
- **as_is (3.421)**: 2.173 bilinmeyen CJK (uydurma romanizasyon yok) + 1.271 Latin ad ham kaldı;
  ağırlık küçük eroge/doujin stüdyoları ve Çinli çizgi film atölyeleri.
- **existing (385)**: 227 doğrudan mevcut anahtar + 158 roller arası birleşim (`セガ`→`bangumi_producer_sega`,
  `テレビ東京`→`bangumi_network_tv_tokyo`, `ディースリー`→`bangumi_publisher_d3_publisher`).

**Bu chunk'ta yakalanan ikizler (tek cid'e bağlandı):** 30 grup / 100+ satır —
`Studio DEEN(`↔`Studio DEEN`, `Nintendo` (studio/publisher), `Tecmo`/`TECMO`, `Falcom`,
`IRODORI`, `UBISOFT Montreal`, `HBO max`, `SHOWTIME`, `BBC2`/`BBC-2`, `コーラス(`/`コーラス`,
`週刊ビッグコミックスピリッツ` (boşluklu varyant), `ビック・ウエスト`/`ビックウエスト`→`big_west`.
Ayrıca **mevcut anahtar taraması** ile 81 yeni anahtar eskisine bağlandı (ör. `D.C. Comics`→`dc_comics`,
`TV Aichi`→`tva`, `Softgarage`→`softgarage`, `Ching Win Publishing`→`chingwin_publishing`) → 005b
hiçbir eski değeri **yinelenmedi** (denetim: yeni 3.641 anahtarın tamamı benzersiz).

**cid çakışması düzeltmeleri (Bölüm 6 tuzağı — kısa cid başka varlığın anahtarıydı):**
`OH!プロダクション` (`oh` = Oh!スーパージャンプ) → `oh_production` · `プラス81` (`81` = 81プロデュース)
→ `plus_81` · `STORIA 台湾` (`storia_2` = まんがライフSTORIA) → `storia_taiwan` ·
`NHK総合テレビジョン` (`nhk_2` = NHK出版) → `nhk_general_tv` · `Wコミックス` (`w` = Wナイト)
→ `w_comics` · `富士见Fantasia文库` (`fantasia` = Studio Fantasia) → `fujimi_fantasia_bunko` ·
`Pヴァイン` (`p` = ビーボーイP!) → `p_vine` · `講談社BOX` (`box` = キャラメルBOX) → `kodansha_box` ·
`Mノベルス` (`m` = ホラーM) → `m_novels` · `オコイス8番` / `月曜プレミア8` → mevcut `8_2` /
`cjk_37361b` anahtarlarına bağlandı · `東京12チャンネル` → `tokyo_channel_12`.

**SKIP (83)**: kesik ad (`8)`, `#1-#332`, `(5`, `https:`), jenerik kelime (`アニメーション`,
`コーポレーション`, `ミュージック`, `プロジェクト`), platform (`PS2`, `iOS`, `(PC)`), zaman dilimi
(`火曜21時枠`, `土ドラ`, `土曜ドラマ24`, `テレビ東京系6局`), yayın sürümü artefaktı
(`マイクロマガジン社; A5版`, `少年画報社; B6判サイズコミックス版`, `増刊号`).

### 2j) CHUNK 005c_a + 005c_b + 005c_c (TAMAM ✓ · 2026-10-21 · PR #24)

Dal: `arena/06f200e9-kitsugi-beta` · **PR #24**: https://github.com/f26901964-eng/Kitsugi-Beta/pull/24. Ön inceleme tamamlandı;
önceki karantinadaki taslakların yerini git'teki nihai map dosyaları aldı.

| Alt bant | Count | Kararlı | SKIP | Map |
|---|---:|---:|---:|---:|
| 005c_a | 3–4 | 5.041 | 117 | 5.158 |
| 005c_b | 2 | 7.838 | 174 | 8.012 |
| 005c_c | 1 | 31.735 | 515 | 32.250 |
| **Toplam** | **1–4** | **44.614** | **806** | **45.420** |

| Rol | 005c kararlı | 005c SKIP |
|---|---:|---:|
| studio | 9.681 | 131 |
| producer | 20.210 | 303 |
| publisher | 9.980 | 226 |
| magazine | 2.907 | 99 |
| network | 1.836 | 47 |

- **CI'da bulunan gerçek ölçek sınırı:** ilk commit `030f88e` üzerinde sözlüklerin
  `kotlinc` adımı geçti fakat Android resource link `ClassTooLargeException: R$string`
  verdi. Bu hata bilinen test hatası DEĞİLDİR. Çözüm: önceki **17.471** string app'te,
  yeni **38.269** string üç kaynak-only Android library modülünde tutulur:
  `bangumi-vault-resources-{1,2,3}/src/main/res/{values,values-en}/strings.xml`.
  Modül başına **12.646 / 12.915 / 12.708** anahtar; `android.nonTransitiveRClass=true`
  zorunludur. Adlar/değerler ve Android merged-resource kimlikleri korunur; çalışma
  zamanında mevcut `getIdentifier(..., context.packageName)` yolu değişmez.
  `tools:keep="@string/bangumi_*"` release shrinker'ın dinamik adları silmesini önler.
  `resource_catalog.py` tüm TR/EN modüllerini birleştirerek yazıcı, draft, vault/master
  doğrulayıcı ve audit'e sunar; modül kapasitesi/ikiz/ad alanı/Gradle bağlantısı denetlenir.
  Android resource link yeni CI’da geçti; başarısız ilk koşu ile başarılı düzeltme
  ayrımı Bölüm 8 PR #24 takip notunda tutulur.
- TR ve EN'ye **38.269'ar yeni anahtar**; toplam **55.740 / 55.740**. Önceki
  **17.471** kaynağın görüntülenen değerleri korundu. Eski dört `@` önekli değerde yalnız
  Android kaçışı eklendi (`@OZ`, `@vitamin`, `@N-Factory`, `@can beat` → `\@...`);
  metin anlamı değişmedi. Yeni `@nifty`, `@sel_ple` için de kaynak referansı tuzağı önlendi.
- Bilgi tabanı **1.984 → 2.197 (+213)**: **85** bilinen resmî Latin ad/varyant + **128**
  önceki bilgi tabanına dayalı hukuki/parantezli ad varyantı. Örnekler: Yuke's, Atelier D,
  NHK Educational, Koei Tecmo Games, Production Reed, Nippon TV, J-Wing ve bölgesel
  Japon yayıncıları; `(株)`, `株式会社`, parantez varyantları yalnız bilinen resmî ada bağlandı.
- **Bilinmeyen özel adlar as_is kaldı**; bu bir TR/EN alan eksikliği değildir. Her iki
  dilde aynı özgün ad korunur; kişi/kurum adı romanizasyonu uydurulmadı. Map kökenleri:
  existing **4.607**, global **335**, as_is **39.672** (paylaşılan eski canon kökenleri dâhil).
- Yeni **42.471** canon satırı; `(rol, cid)` tekrarı yok. Yeni **38.269** string değerinde
  normalize ikiz yok; mevcut adlar eski anahtara bağlandı. `Voil` eski `bangumi_org_voil`
  anahtarına bağlanarak `org` ad alanının da taranması sağlandı; bilinen kişi adları
  `bangumi_tag_aki_hata` gibi mevcut özel-ad kaynaklarına da bağlanır. Çevrilmiş genel
  etiketler / UI metinleri kurum adı kanıtı sayılmaz.
- **CID güvenliği:** kısa cid veya CJK hash eşitliği tek başına birleşim değildir.
  `J･ウイング ≠ ユニバーサルJ`, `アトリエD ≠ Dモーニング`, `FOX出版 ≠ FOX`,
  `講談社X文庫 ≠ 沌x`, `SF轻小说 ≠ SFマガジン`, `コミックGENKi ≠ Genki`,
  `ユークス ≠ 風麟`. Yeni resolver yalnız normalize değer eşleşmesi / bilgi tabanı
  kanıtıyla hedef seçer; çakışan slug'a açık ad veya deterministik hash soneki verir.
- **SKIP 806:** jenerik Latin/kana parçaları (`Studio`, `Games`, `Ltd.` vb.), kredi
  başlıkları ve ayraçları, bölüm/sayı aralıkları, platformlar, bölgeler, yayın sürümleri.
  `Xbox Game Studios`, `C418`, `#define` gibi gerçek özel adlar kaba pattern ile atılmadı.
- Komite önekli kimi key'ler gerçekte studio/publisher/magazine rolündedir: rol JSON'un
  `role` alanından alınır, key'in `producer` önekinden türetilmez. Önceki chunk'lar değişmedi.
- **Koruyucular:** `--min-count` artık CLI'da zorunlu; var olan map taslağı sessizce
  ezilemez. `android_strings.py`, `org_name_resolver.py`, `audit_org_chunk.py` ve **18 birim
  testi** eklendi. Audit: kapsam + gerçek rol + hedef TR/EN + SKIP + eski değer + normalize ikiz.
- Vault/master/generator `--check` geçti; generator yazmasız **+0 / ~0** verdi.
  RAW **66 parça**, en büyük **49.999** modified-UTF-8 bayt (**<50.000**).
  Yerel JDK/kotlinc yoktur; sözlüklerin kotlinc derlemesi ilk CI koşusunda geçti.
  `d12c1bc` CI’da Android kaynak derleme/link ve uygulama derlemesi geçip test
  çalıştırmasına ulaşıldı; `R$string` taşması düzeldi. APK/release/DEX başarısı iddia edilmez.
- Ham JSON yedeği yalnız bu ortamda `/home/user/.cache/bangumi_raw/` konumundadır.
  Yeni ortamda Drive ham JSONlarını indir + git kararlarıyla `apply_map.py` çalıştır;
  git karar dosyaları Drive'dan üstündür. `google_drive/` repoda bırakılmadı.

### 2k) CHUNK 006 (TAMAM ✓ · 2026-10-21) — etiket count 20–99

Dört banda bölündü (`scratch_006/band_*.txt`, `gen.py`, `manual/*.py`); her bant
`build_tag_chunk.py` ile canon+map üretti, sonra tek kapanış zinciri koştu.

| Bant | count | Kayıt | SKIP | existing | global | new | as_is | Yeni canon | map |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 006_a | 60–99 | 625 | 148 | 54 | 221 | 126 | 76 | 386 | 625 |
| 006_b | 40–59 | 884 | 178 | 66 | 358 | 124 | 158 | 599 | 884 |
| 006_c | 30–39 | 913 | 235 | 101 | 340 | 101 | 136 | 559 | 913 |
| 006_d | 20–29 | 1.605 | 215 | 86 | 735 | 112 | 457 | 1.180 | 1.605 |
| **Toplam** | **20–99** | **4.027** | **776** | **307** | **1.654** | **463** | **827** | **2.724** | **4.027** |

- Karar dağılımı: **%41 global**, **%20,5 as_is**, **%19,3 SKIP**, **%11,5 new**, **%7,6 existing**.
- **as_is politikası (006):** bilinmeyen CJK kişi/kurum adları **SKIP yerine as_is** (`cjk_<md5[:6]>`
  anahtarı, TR=EN=ham ad). Gerekçe: kullanıcı talebi + ekranda ad **korunur**, romanizasyon
  **uydurulmaz**; 005c'de kurumlarda kurulan `cjk_<hash>` konvansiyonuyla aynı. 002'deki
  "bilinmeyen kişi → SKIP" kararı bu chunk'ta **bilinçli olarak** terk edildi (Bölüm 6).
- SKIP sınıfları: yıl/dönem/sezon (`2000里番`, `2013年10月秋アニメ`), puan/raf
  (`低于7`, `白金神作`, `听完`, `通关.`), kişisel raf (`保留`, `待定`, `购入預定`, `想补的旧番`),
  anlamsız işaret (`(｡･ｘ･)つ`, `△洗脑`, `Ⅰ类tv`, `d.短片`), hakaret/gürültü (`婊子`, `粪作`),
  belirsiz kısaltma (`OF`, `9+`, `周刊`, `全集`), siyasi/kişisel not (`反华`, `加分@题材喜欢`).
- **Kısa-cid tuzağı (006_b/006_c/006_d):** mevcut `bangumi_tag_<cid>` kaynağı olan bir ada
  `new`/`global` vermek "mevcut kaynak değeri farklı" hatası veriyor → doğru karar `existing`
  (`western`, `romance`, `kuudere`, `male_protagonist`, `short_story`, `real_sister`, `sphere`,
  `side_scrolling`, `single_volume`, `tearjerker`, `occult`, `gore`, `short_series`,
  `theme_song_collection`, `older_woman_younger_man`, `mystery_manga`, `oh_great`, `square`,
  `umineko`, `aria`…). Kanıt: `decisions/canon.tsv` + kaynak kataloğu.
- Bant içi yinelenen adlar (aynı ad 2+ kayıt) **aynı cid**'e bağlandı; `build_tag_chunk.py`
  `emitted` setiyle canon satırını **tek** yazıyor.
- Otomatik `as_is` çakışmaları (yalnız büyük/küçük harf farkı: `clear`/`Clear`, `of`/`OF`,
  `pop_rock`/`pop-rock`, `rewrite`/`Rewrite`, `august`/`AUGUST`, `kairosoft`/`Kairosoft`,
  `drrr`/`DRRR!!`, `muv-luv`/`Muv-Luv`) tek cid + tek TR/EN'e sabitlendi; `OF`/`of` SKIP.

Kapanış zinciri (006 sonrası):
```
apply_map.py --check     → canon 55.968 | map 61.398 · hata yok
apply_map.py             → 61.398 kayıt (canon 59.303, skip 2.095)
vault_to_strings.py      → string anahtarı 51.104 · TR +2.292 / EN +2.292
                           BangumiTagDictionary +2.292 grup, +252 etiket
                           BangumiOrgDictionary +17 satır
verify_vault.py          → ✅ 52.219 / 51.104 / 61.398 / 121.284
verify_bangumi_master.py → ✅ 52.176 eşleme · GERÇEK çakışma 0 · TR=EN 58.398
gen_bangumi_master.py --check → +0 grup, +0 etiket, +0 kayıt
```

### 2d) Kümülatif durum (chunk 001–006)

```
python3 diag/bangumi_vault/scripts/verify_vault.py
✅ TÜM KONTROLLER GEÇTİ — 52219 bangumi_* anahtar, 51104 vault kaydı, 61398 map satırı, 121284 damga denetlendi
```

| Rol | Karar satırı | Kararlı canon | SKIP |
|---|---:|---:|---:|
| tag | 4.989 | 3.926 | 1.063 |
| studio | 12.257 | 12.085 | 172 |
| producer | 24.727 | 24.349 | 378 |
| publisher | 13.201 | 12.898 | 303 |
| magazine | 3.974 | 3.852 | 122 |
| network | 2.250 | 2.193 | 57 |
| **Toplam** | **61.398** | **59.303** | **2.095** |

Roller JSON `role` alanına göre sayılır (komite önekli çapraz-rol kayıtlar dâhil).
Canon **55.968**, bilgi tabanı **2.197**, done **61.398** veri satırı.
Drive’daki önceki **11.951** done satırı bayt içeriği korunarak yeni **45.420** satır eklendi;
`progress.json` tarihsel metadata/özetleri korunup güncel statü/sonraki iş/sayaçları yenilendi.
`translations_pending.tsv`: **58.930 veri satırı** (006 öncesi 62.332; etiket kararları düştü).
Kurumlar bu pending tablosunda olmadığı için 005c ile sayı düşmez.

Kurum **count ≥1 kararsız: 0**. Count **0: 376** kurum kaydı ayrı kalan kuyruktur;
komite kopyaları da rol mapleri üzerinden damgalandı (**1.229/1.229**, 8 SKIP).
Count=0 kurumları 005c kapsamına eklenmedi.
Etiket **20–99 bandı bitti (4.027/4.027)**; kuyrukta yalnız **007 (<20 · 59.510)** var.
Count=0 kurum (**376**) hâlâ ayrı kuyruk.

Master: **52.176** eşleme, gerçek çakışma **0**; TR/EN **58.398 / 58.398**,
TagDictionary **3.983** grup; RAW **66** parça, en büyük **49.999** modified-UTF-8 bayt.
Kapasite (006 sonrası): app yerel `bangumi_*` **11.292** (<20.000), shard'lar
**13.565 / 13.782 / 13.580** (<16.000), toplam anahtar **52.219**.

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
- [x] **Chunk 004b** — kurum count **35–59** (1.000 kayıt: 981 kararlı + 19 SKIP) →
      uygulamaya işlendi ✅ (2026-10-21; ayrıntı Bölüm 2f). `worklist.py --min-count 35
      --max-count 59` sonrası bu aralıkta **0** kayıt kaldı.
- [x] **Chunk 004c** — kurum count **20–34** (**1.514** benzersiz kayıt: 1.489 kararlı + 25 SKIP;
      studio 358, producer 607, publisher 365, magazine 138, network 46; committee kaydı
      `producers.json` ile aynı key'de olduğu için kapsandı) → uygulamaya işlendi ✅
      (2026-10-21; ayrıntı Bölüm 2g). Taslak: `python3 scripts/draft_org_map.py --chunk 004c
      --min-count 20 --max-count 34`. Bu aralıkta **0** kayıt kaldı.
- [x] **Chunk 005a — kurum count 10–19** (**2.897** kayıt: 2.846 kararlı + 51 SKIP;
      studio 721, producer 1.146, publisher 702, magazine 227, network 101) → uygulamaya
      işlendi ✅ (2026-10-21; ayrıntı Bölüm 2h). Taslak: `python3 scripts/draft_org_map.py
      --chunk 005a --min-count 10 --max-count 19` (`--min-count` ZORUNLU).
      Bu aralıkta **0** kayıt kaldı.
- [x] **Chunk 005b — kurum count 5–9** (**4.311** kayıt: 4.228 kararlı + 83 SKIP;
      studio 979, producer 1.804, publisher 1.076, magazine 279, network 173) → uygulamaya
      işlendi ✅ (2026-10-21; ayrıntı Bölüm 2i). Taslak: `python3 scripts/draft_org_map.py
      --chunk 005b --min-count 5 --max-count 9`. Bu aralıkta **0** kayıt kaldı.
- [x] **Chunk 005c — kurum count 1–4** (**45.420** kayıt; **44.614 kararlı + 806 SKIP**) →
      uygulamaya işlendi ✅ (2026-10-21; Bölüm 2j).
  - [x] 005c_a: count 3–4 · 5.158 map
  - [x] 005c_b: count 2 · 8.012 map
  - [x] 005c_c: count 1 · 32.250 map
      Bu aralıkta **0 kararsız** kayıt kaldı.
- [x] **Chunk 006 — etiket count 20–99** (**4.027**: 3.251 kararlı + 776 SKIP) → uygulamaya
      işlendi ✅ (2026-10-21; ayrıntı Bölüm 2k). Dört bant, tek zincir:
  - [x] 006_a: count 60–99 · 625 kayıt · 386 yeni canon
  - [x] 006_b: count 40–59 · 884 kayıt · 599 yeni canon
  - [x] 006_c: count 30–39 · 913 kayıt · 559 yeni canon
  - [x] 006_d: count 20–29 · 1.605 kayıt · 1.180 yeni canon
      `python3 scripts/worklist.py --min-count 20 --max-count 99` sonrası bu aralıkta
      **0 kararsız** kaldı.
- [ ] **Chunk 007 — etiket kuyruğu <20** (**59.510**; en yüksek count 19).
      ⚠ **ÖNCE OKU: `007_MIMARI_ARASTIRMA.md`.** Kapasite ölçümü: kaynak ID tavanı
      (65.535/tür, bugün 58.398 dolu) ve JVM sabit havuzu (65.535/sınıf, bugün 14.623)
      007'yi `as_is` politikasıyla kaldırmıyor. Çözüm kararı: etiket sözlüğü **Kotlin
      kaynağından çıkıp `app/src/main/resources/bangumi/tags.tsv` verisine taşınır**
      (S0 üreteci + kayıpsız tur testi hazır: `build_tag_store.py`, `test_tag_store.py`).
      Kırpma/SKIP'e zorlama YOK; `as_is` kuralı aynen korunur. Bant planı:
      `19`, `15–18`, `11–14`, `8–10`, `6–7`, `4–5`, `3`, `2`, `1` (a…i) — her bant ayrı
      `map_chunk_007_x_tags.tsv` + kapanış zinciri. Count=0 kurum kuyruğu (**376**) ayrıca
      değerlendirilecek. Committee kopyaları 005c sonrası **1.229/1.229 damgalı**.
      Kapasite notu: 007 öncesi app **11.292**/20.000, shard'lar **13.565–13.782**/16.000,
      toplam **52.219** anahtar; 59.510 adayın çoğu SKIP/as_is olacağı için şimdiden
      **Android toplam kaynak/ID kapasitesi** ve **JVM metod boyutu** izlenmeli (Bölüm 7).
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

**006 dersleri (2026-10-21):**
- **Etiketlerde kısa-cid tuzağı daha sık:** `canon.tsv`'te veya `bangumi_tag_<cid>` kaynağında
  zaten var olan bir ada `new`/`global` vermek hataya düşer. Karar `existing` olmalı ve TR/EN
  **kaynaktan birebir** kopyalanmalı. Bant başına 3–24 vaka çıktı (Bölüm 2k listesi).
- **Bilinmeyen CJK ada as_is:** `cjk_<md5(ad)[:6]>` cid'i ile TR=EN=ham ad yazılır; böylece
  romanizasyon uydurulmaz, ekran değişmez, ama varlık kaydı kanonlaşır. Aynı ad birden çok
  kayıtta geçerse **aynı hash** → tek canon satırı (bant içi birleşme doğal).
- **Bant içi tekrar eden ad** (ör. `森見登美彦`, `欢迎来到实力至上主义教室`) ikinci kez
  `existing` ile bağlanamaz (canon henüz yazılmadı) → aynı cid + aynı TR/EN ile `global`/`new`
  verilir; yazıcı `emitted` setiyle canon'u tek satır yazar.
- **SKIP ölçütü:** puan/raf/durum, yıl-dönem, anlamsız işaret, hakaret, belirsiz kısaltma,
  kişisel not. Bir etiketin "kavram" mı "kişisel raf" mı olduğu **kayıt adının kendisinden**
  okunur; global adı bilinmeyen kişiler as_is'e gider, uydurma Latin ad **asla** yazılmaz.
- **Kotlin groups tek metotta 64 KB JVM sınırına dayanıyordu:** 3.983 grup tek `listOf`
  initializer'ında ~120 KB bytecode üretirdi. `vault_to_strings.py` artık girişleri
  `groupChunkNN()` fonksiyonlarına bölüyor (≤400 giriş/parça, sıra korunur).

**005c dersleri (2026-10-21):** slug veya CJK-hash çakışmasını isim kanıtı sayma;
`bangumi_org_*` ve özel-ad `bangumi_tag_*` kaynaklarını da karşılaştır. UI/çevrilmiş
jenerik etiketleri kurum adı sanma. Komite önekli key'de `role` alanı esastır. Android'in
başlangıç `@`/`?` kaçışını yazıcı ve okuyucularda aynı çöz; eski metin anlamını koru.
RAW sınırı **49.999** tutulur; tam 50.000'e izin veren eski üst sınır daraltıldı.


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

- **Chunk 004c — ikiz kayıt disiplini**: vault'ta aynı varlık birden çok kez geçiyor
  (Latin anahtar + `cjk_<hash>` anahtarı). Latin/raw varyantlar bilgi tabanına **aynı
  `canon_id`** ile eklenerek tek string anahtarında toplanır (Konami, Namco, TYPE-MOON,
  CHUNSOFT, CONNECT, PINE JAM). Bu yapılmazsa `konami`/`konami_2`, `pine_jam`/`pine_jam_2`
  gibi ikinci anahtarlar doğar. Her chunk sonunda "aynı ad → farklı cid" taraması yapılmalı.
- **Chunk 004c — cid çakışması tuzağı**: `key` sonekinden türetilen `canon_id` tesadüfen
  başka bir varlığın anahtarına denk gelebilir. Bu chunk'ta üç yanlış birleşim yakalandı:
  `漫画SHOW`→prodüktör `Show隈部` (`bangumi_producer_show`), `ジャンプGIGA`→studio `Giga`
  (`bangumi_studio_giga`), `ALcotハニカム`→`ALcot` (`bangumi_studio_alcot_2`). Ders: taslakta
  `mevcut anahtara birleşim` notu görülen her satırda **hedef anahtarın değeri** gözle
  doğrulanmalı (`record adı ≈ kanon değeri` denetimi).
- **Chunk 004c — `committee` rolü**: `committees.json` kayıtları `role: producer` taşıyabiliyor
  ve `producers.json`/`vault.json` ile **aynı key**'i paylaşabiliyor. Böyle kayıtlar
  `draft_org_map.py` tarafından producer rolünde kapsanır ve `apply_map.py` anahtar bazlı
  damgaladığı için tüm kopyalar işlenir; `ORG_FILES`'a `committee` eklemek gerekmez.
- **Chunk 004c — SKIP genişlemesi**: `台湾` (bölge etiketi; 004a'daki `中国大陆` ile aynı çizgi),
  `http:` (URL parçası), `LLC`/`PC`/`SE`/`ESP` (belirsiz kısaltma), `エフ` (tek kana),
  `発売元:モモグレ` (yayıncı öneki), `(オトメドラマ)` (parantez gürültüsü),
  `インディーズ・メーカー` (indies maker) SKIP; `draft_org_map.py` `SKIP_NAMES`'e eklendi.
- **Chunk 005a — kısa cid = başka varlığın anahtarı (YENİ TUZAK)**: kısaltma cid'leri
  (`nbc`, `bbc`, `mtv`, `mbc`) başka kurumların mevcut string anahtarlarına denk geliyordu →
  `長崎放送` US `NBC`'ye, `びわ湖放送` `BBC`'ye, `三重テレビ放送` `MTV`'ye, `南日本放送` `韩国MBC`'ye
  bağlanacaktı. Ders: **kısa kısaltma cid'i ancak `bangumi_<rol>_<cid>` anahtarının gerçekten o
  varlığa ait olduğu doğrulandıktan sonra kullanılmalı**; aksi hâlde tam slug
  (`nagasaki_broadcasting`, `biwako_broadcasting`, `mie_tv`, `minaminihon_broadcasting`) kullanılır.
  Kural: `mevcut anahtara birleşim` notlu her satırda hedef anahtarın değeri gözle doğrulanır.
- **Chunk 005a — bölge etiketi / jenerik kelime SKIP'i**: `上海`, `北京`, `北美`, `韩国`, `台灣`,
  `(日本)` (bölge), `中心`/`システム`/`パブリッシャーズ`/`アニメーションスタジオ`/`エンタープライズ`/
  `グローバル`/`ヤング`/`コミック`/`オンライン`/`レコード`/`テレビ` (jenerik), `制作统括：`/`发行`/
  `企画协力：`/`；宣传制片人：`/`；製作協力：` (jenerik etiketi), `△○□×`, `【チーム`, `10)`,
  `電撃「マ 王`, `→`, `)→`, `&フラワー`, `(パルシィ)`, `文化传播有限公司` (kesik/gürültü) → SKIP.
- **Chunk 005b — damga tuzağı (YENİ)**: `apply_map.py` çalıştıktan sonra vault JSON'larındaki
  kayıtlara `decision`/`canon` damgası düşer ve `draft_org_map.py` **damgalı kayıtları atlar**.
  Bu yüzden aynı bantta yeniden taslak üretmek **sessizce boş map** verir (0 satır). Çözüm:
  yeniden taslak öncesi ham JSON'ları geri yükle (`scratch_005b/raw/` yedeği veya Drive'dan
  indirme) → `git checkout -- canon.tsv` → `rm map_chunk_<id>_orgs.tsv` → taslak.
  Belirti: `map_chunk_...tsv: 0 satır` + `canon.tsv: +0 satır`.
- **Chunk 005b — yinelenen anahtar taraması (YENİ ADIM)**: bilgi tabanına eklenen Latin ad
  mevcut bir anahtarın değerini tekrar üretirse (`D.C. Comics` vs `dc_comics`, `TV Aichi` vs
  `tva`) iki ayrı string anahtarı doğar. Her turda **eski `strings.xml` değerleriyle** normalize
  karşılaştırma yapılmalı; eşleşen satırın cid'i eski anahtarın cid'ine çevrilir. 005b'de 81
  satır böyle düzeltildi, sonuçta yeni 3.641 anahtarın hiçbiri eski değeri yinelemiyor.
- **Chunk 005b — kısa cid tuzağının devamı**: `oh` (Oh!スーパージャンプ), `81` (81プロデュース),
  `storia_2` (まんがライフSTORIA), `nhk_2` (NHK出版), `w` (Wナイト), `fantasia` (Studio Fantasia),
  `p` (ビーボーイP!), `box` (キャラメルBOX), `m` (ホラーM) → hepsi tam slug'a çevrildi
  (`oh_production`, `plus_81`, `storia_taiwan`, `nhk_general_tv`, `w_comics`,
  `fujimi_fantasia_bunko`, `p_vine`, `kodansha_box`, `m_novels`).

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
- **BangumiTagDictionary parçalı üretim (006, 2026-10-21):** 3.983 `g(...)` girişi tek
  `listOf` initializer'ında tutulunca JVM **64 KB metod** sınırı riski doğdu.
  `vault_to_strings.py` artık `private val groups = groupChunk01() + groupChunk02() + …`
  üretir; her `groupChunkNN()` en çok **400** giriş taşır (sıra ve "ilk yazım kazanır"
  semantiği birebir korunur). Yeni bantlar son parçaya eklenir; dosya idempotent güncellenir.
  Elle düzenlenen bölüm (`g`, `byLabel`, pattern mantığı) değişmez.
- **Kapasite eşikleri (izlenmeli):** app yerel `bangumi_*` **11.292**/20.000 ·
  shard1 **13.565**, shard2 **13.782**, shard3 **13.580** /16.000 ·
  toplam anahtar **52.219** · RAW **66** parça, en büyük **49.999** bayt.
  007 (59.510 aday) öncesi Android toplam kaynak/ID sınırı ve JVM sabit havuzu yeniden ölçülmeli.
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

### 006 kapanış (2026-10-21)

| Kontrol | Sonuç |
|---|---|
| `apply_map.py --check` | ✅ canon 55.968 · map 61.398 · hata yok |
| `apply_map.py` | ✅ 61.398 kayıt (canon 59.303 · skip 2.095) |
| `vault_to_strings.py` | ✅ anahtar 51.104 · TR/EN +2.292 · TagDict +2.292 grup/+252 etiket · OrgDict +17 |
| `verify_vault.py` | ✅ **52.219 / 51.104 / 61.398 / 121.284** |
| `verify_bangumi_master.py` | ✅ 52.176 eşleme · GERÇEK çakışma 0 · TR=EN 58.398 |
| `gen_bangumi_master.py --check` | ✅ +0 grup · +0 etiket · +0 kayıt |
| `unittest` (scripts) | ✅ 23/23 (18 + 5 yeni `test_tag_dict_chunks.py`: parçalama / yeniden dengeleme / idempotentlik) |
| Kapasite | app 11.292/20.000 · shard 13.565/13.782/13.580 (16.000) · RAW 66 parça ≤49.999 |
| Drive | ✅ 2026-10-21: map a–d + canon + done + pending + progress + PROGRESS yüklendi |
| Yerel derleme | ⚠ YAPILMADI — sandbox'ta JDK/kotlinc yok; "derlendi" DENMEZ |

**006 Drive yüklemesi (2026-10-21, kimlikler tarihli):**

| Dosya | Drive kimliği | Bayt |
|---|---|---|
| `map_chunk_006_a_tags.tsv` | `197E0QLsPBbPUqxuk3GuyxfddAKORv5MM` | 32.880 |
| `map_chunk_006_b_tags.tsv` | `13PK1OCmqcNES3JQkplHOaR5o-66_b2if` | 49.074 |
| `map_chunk_006_c_tags.tsv` | `1-dQJMkQ01MQbxH1p0TIjkv10lnCFYafM` | 50.866 |
| `map_chunk_006_d_tags.tsv` | `1m9-1mI8fQmUFsEtpL8gEqJv89dbXyb9j` | 95.130 |
| `decisions/canon.tsv` | `1Gjc0TpzXYGgFaeeH7P13wIWMcQzJHmul` | 5.847.695 |
| `translations_done.tsv` | `1KsaLgEPckQhAH0HgE-sytCxL7Dgt1bJ2` | 7.141.787 |
| `translations_pending.tsv` | `1U_hsTZuMxBL2u0mMlrVtmQe7zZQ9C_2W` | 5.102.517 |
| `progress.json` | PROGRESS.md ile aynı gün yüklendi; kimliği **yenilenir** | — |
| `PROGRESS.md` | kendini yüklerken kimliği yenilenir → klasörden ada göre bul | — |

`005c_a/b/c` mapleri ve `org_global_names.tsv` bu turda **değişmedi**, yeniden yüklenmedi.

**CI durumu (2026-10-21, gerçek koşular — ölçüldü, varsayılmadı):**

| Koşu | Dal | Sonuç |
|---|---|---|
| `39589748851` / `39589197313` | `arena/8a37df7c-kitsugi-beta` (PR #25) | `verify` ✅ · `compile-and-test` ❌ (`🧪 BangumiMasterDictionaryTest` adımı) |
| `39583503155` (PR #24) | `arena/06f200e9-kitsugi-beta` | `verify` ✅ · `compile-and-test` ❌ — **aynı adım** |
| `39579279060` | **`main`** | `verify` ✅ · `compile-and-test` ❌ — **aynı adım** |

* `verify` işi CI'da geçti → `unittest` **23/23**, `verify_bangumi_master.py`, `gen --check` + `git diff` temiz.
* `compile-and-test` işi `verify`den bağımsız ve **`main`'de de aynı adımda kırık** → 006 kaynaklı
  regresyon **değil**; ancak **hata metni doğrulanmadı**: bu sandbox'ta ne koşu günlüğü ne de
  `bangumi-master-test-log` artefaktı indirilebiliyor (imzalı URL'ler allowlist dışında).
  `main`/PR #24/PR #25 için görünen tek ortak işaret, adımın `exit code 1` ile bitmesi.
* Yerel telafi denetimi: testin **tüm** iddiaları (`BangumiTagDictionary` 3.983 grup ve
  `BangumiOrgDictionary` 52.176 satır üzerinde "ilk yazım kazanır" + normalize/compact kuralları
  Python ile birebir taklit edilerek) tek tek denendi → **hepsi geçiyor**; yani kırık bir etiket/
  şirket eşlemesine işaret eden bir kanıt yok. Yine de **"CI yeşil" DENMEZ**.
* Ayrı iş: `🏗️ Android Derleme Doğrulama (geçici)` de `main`'de (`39585497128`) kırık.

### 005c kapanış (2026-10-21)
- [x] Üç alt bant işlendi; count 1–4 kararsız 0; önceki chunk'lar yeniden işlenmedi.
- [x] Vault + master + generator `--check` + XML + normalize ikiz / hedef / SKIP audit geçti.
- [x] 18 birim testi ve map ezme / zorunlu min-count / boş damgalı bant koruyucuları geçti.
- [x] RAW 66 parça; her parça <50.000 modified-UTF-8 bayt.
- [x] Bölüm 2j, 2d, 5, 6 ve yeni-oturum talimatları güncellendi.
- [x] Drive kapanış dosyaları güncellendi; eski 9 map değişmedi (MD5 eşit), 005c mapleri ve
      canon/bilgi tabanı/done/pending/progress/devir dosyaları senkronlandı.
- [x] Commit + `arena/06f200e9-kitsugi-beta` dalına push + PR #24; numara başlık/2j’ye işlendi.
- [x] Commit sonrası `git diff --quiet` / `git diff --cached --quiet` temiz.


Chunk 005b için durumu (2026-10-21) → son satır hariç **✅ tamam**:

- [x] `draft_org_map.py --chunk 005b --min-count 5 --max-count 9` → 4.311 map + 4.239 canon satırı
      (taslak gözden geçirildi; +276 bilgi tabanı satırı, 30 ikiz grubu birleştirildi,
      81 yinelenen anahtar mevcut anahtara bağlandı, 10 cid çakışması düzeltildi)
- [x] `apply_map.py --check` temiz (10.773 canon · 11.951 map · 11.438 canon + 513 skip)
- [x] `verify_vault.py` ✅ (11.292 `bangumi_*` anahtar · 10.009 vault kaydı · 11.951 map satırı · 121.284 damga)
- [x] `verify_bangumi_master.py` ✅ (11.693 eşleme · gerçek çakışma 0 · TR/EN 17.471/17.471)
- [x] CI üreteç denetimi: `gen_bangumi_master.py --check` yazma yapmadan temiz
- [x] `strings.xml` geçerli XML; `bangumi_*` anahtarlarında yinelenen yok, TR/EN simetrik (11.292 = 11.292)
- [x] Yeni 3.641 anahtarın **hiçbiri** eski bir anahtarın değerini yinelemiyor (denetim: 0 çakışma)
- [x] RAW parça sınırı: 14 parça, en büyük **49.999** bayt (< 50.000 modified-UTF-8)
- [x] `PROGRESS.md` + `progress.json` güncellendi
- [x] Drive'a yüklendi: PROGRESS.md, progress.json, decisions/canon.tsv,
      decisions/map_chunk_005b_orgs.tsv, decisions/org_global_names.tsv,
      translations_done.tsv, translations_pending.tsv
- [x] Git commit + push (dal `arena/1e0c5ecd-kitsugi-beta`); chunk 005b **PR #23** içinde
      (https://github.com/f26901964-eng/Kitsugi-Beta/pull/23)
- [ ] **Not (yerel derleme):** bu oturumda JDK/kotlinc kurulu değildi; Kotlin tarafı yalnız
      `verify_bangumi_master.py` + `gen --check` + RAW parça bayt denetimiyle doğrulandı;
      asıl derleme CI'da (kotlinc adımı) koşar.

Chunk 005a için durumu (2026-10-21) → her satır **✅ tamam**:

- [x] `draft_org_map.py --chunk 005a --min-count 10 --max-count 19` → 2.897 map + 2.743 canon satırı
      (taslak gözden geçirildi; +392 bilgi tabanı satırı, 4 cid çakışması + 1 SKIP düzeltildi)
- [x] `apply_map.py --check` temiz (6.747 canon · 7.640 map · 7.210 canon + 430 skip)
- [x] `verify_vault.py` ✅ (7.651 `bangumi_*` anahtar · 6.339 vault kaydı · 7.640 map satırı · 121.284 damga)
- [x] `verify_bangumi_master.py` ✅ (7.798 eşleme · gerçek çakışma 0 · TR/EN 13.830/13.830)
- [x] CI üreteç denetimi: `gen_bangumi_master.py --check` yazma yapmadan temiz; sonrasında ek değişiklik yok
- [x] `strings.xml` geçerli XML; `bangumi_*` anahtarlarında yinelenen yok, TR/EN simetrik (7.651 = 7.651)
- [x] RAW parça sınırı: 10 parça, en büyük **49.999** bayt (< 50.000 modified-UTF-8)
- [x] `PROGRESS.md` + `progress.json` güncellendi
- [x] Drive'a yüklendi: PROGRESS.md, progress.json, decisions/canon.tsv,
      decisions/map_chunk_005a_orgs.tsv, decisions/org_global_names.tsv,
      translations_done.tsv, translations_pending.tsv
- [x] Git commit + push (dal `arena/1e0c5ecd-kitsugi-beta`) + PR #23 açıldı
      (https://github.com/f26901964-eng/Kitsugi-Beta/pull/23)
- [ ] **Not (yerel derleme):** bu oturumda JDK/kotlinc kurulu değildi; `compile_check.sh`
      çalıştırılamadı. Kotlin tarafı yalnız `verify_bangumi_master.py` + `gen --check` +
      RAW parça bayt denetimiyle doğrulandı; asıl derleme CI'da (kotlinc adımı) koşar.

Chunk 004c için durumu (2026-10-21) → her satır **✅ tamam**:

- [x] `draft_org_map.py --chunk 004c --min-count 20 --max-count 34` → 1.514 map + 1.422 canon satırı
      (taslak gözden geçirildi; +271 bilgi tabanı satırı, 3 yanlış birleşim düzeltildi)
- [x] `apply_map.py --check` temiz (4.004 canon · 4.743 map · 4.364 canon + 379 skip)
- [x] `verify_vault.py` ✅ (5.132 `bangumi_*` anahtar · 3.795 vault kaydı · 4.743 map satırı · 121.284 damga)
- [x] `verify_bangumi_master.py` ✅ (5.013 eşleme · gerçek çakışma 0 · TR/EN 11.311/11.311)
- [x] CI üreteç denetimi: `gen_bangumi_master.py --check` yazma yapmadan temiz; `git diff --quiet` sonrası ek değişiklik yok
- [x] `strings.xml` geçerli XML; `bangumi_*` anahtarlarında yinelenen yok, TR/EN simetrik (11.311 = 11.311)
- [x] RAW parça sınırı: 6 parça, en büyük **49.990** bayt (< 50.000 modified-UTF-8)
- [x] `PROGRESS.md` + `progress.json` güncellendi
- [x] Drive'a yüklendi: PROGRESS.md, progress.json, decisions/canon.tsv,
      decisions/map_chunk_004c_orgs.tsv, decisions/org_global_names.tsv,
      translations_done.tsv, translations_pending.tsv
- [x] Git commit + push (dal `arena/c54b58d5-kitsugi-beta`) + PR #22 açıldı
      (https://github.com/f26901964-eng/Kitsugi-Beta/pull/22) — **`main`'e merge edildi** (2026-10-21)
- [ ] **Not (yerel derleme):** bu oturumda JDK/kotlinc kurulu değildi; `compile_check.sh`
      çalıştırılamadı. Kotlin tarafı yalnız `verify_bangumi_master.py` + `gen --check` +
      RAW parça bayt denetimiyle doğrulandı; asıl derleme CI'da (kotlinc adımı) koşar.

### PR #24 CI takip — 2026-10-21 (005c)

- PR: https://github.com/f26901964-eng/Kitsugi-Beta/pull/24 (dal değişmedi).
- İlk kod commit `030f88e`: `kotlinc` sözlük derlemesi başarılı; Android
  `processFossDebugResources` **ClassTooLargeException: R$string** ile kırıldı.
  Bu gerçek yeni hataydı; bilinen test sorunu diye geçiştirilmedi.
- Düzeltme commit `d12c1bc`: üç non-transitive resource-only modül. CI Python verify
  **başarılı (18 test dâhil)**; bağımsız sözlük `kotlinc` adımı **başarılı**.
- Android koşusu https://github.com/f26901964-eng/Kitsugi-Beta/actions/runs/39583494085:
  üç library `generateDebugRFile` + app `processFossDebugResources` **geçti**;
  uygulama derlenip `testFossDebugUnitTest` çalıştırmasına ulaşıldı. Son hata:
  **`MalNotificationsClientTest.mergesHistoryItemsWithRecentItemsAndDeduplicatesById`**
  AssertionError. Kullanıcı talimatıyla MAL kodu/testi değiştirilmedi.
- MASTER koşusu https://github.com/f26901964-eng/Kitsugi-Beta/actions/runs/39583503155:
  verify + sözlük derlemesi geçti; **BangumiMasterDictionaryTest** adımı başarısız.
  Önceki main/PR #23 bilinen hata notu korunur; bu koşunun ham test logları sandbox
  dış-ağ kısıtı nedeniyle indirilemedi, yeni bir test beklentisi değişikliği yapılmadı.
- Kaynak taşması çözüldü fakat **CI bütünü yeşil DEĞİLDİR**. Testler pipeline'ı
  durdurduğu için debug APK / release R8 / DEX adımlarının tam başarısı doğrulanmadı.
  PR merge edilmedi; bu sınır açıkça kullanıcıya bildirildi.

### PR #23 CI takip — 2026-10-21 (chunk 005a)

- `verify` işi **✅ geçti** (Python doğrulayıcı + `gen --check`): 7.798 eşleme, gerçek çakışma 0,
  TR/EN 13.830 = 13.830, RAW 10 parça; `🔨 Sözlükleri derle (kotlinc)` adımı da geçti.
- `🧪 BangumiMasterDictionaryTest` adımı **düştü** — **bu işle İLGİSİZ, önceden var olan hata**:
  aynı adım `main` üzerinde de düşüyor (PR #22 merge koşusu `39574455306`, ayrıca `39570087370`),
  yalnız dokümantasyon commit'inde de düşmüş durumda. Testin sabitlediği tüm kurum satırları
  (`集英社`, `アニプレックス`, `TBSテレビ`, `京都アニメーション`, `ボンズ`, `幽浮社`, `マッドハウス`)
  taban commit `a3b716d` ile **birebir aynı** (RAW'a dokunulmadı, gölgeleme yok).
  `diag/bangumi_master/check_test_expectations.py` eski `o(...)` biçimini aradığı için RAW
  döneminde kurum satırlarını **0** okuyor (script güncellenmeli; test sonucu değil, script sınırı).

### PR #22 CI takip — 2026-10-21

- `verify` işi **✅ geçti** (Python doğrulayıcı + `gen --check`): 5.013 eşleme, gerçek çakışma 0,
  TR/EN 11.311 = 11.311, RAW 6 parça. `🔨 Sözlükleri derle (kotlinc)` adımı da geçti.
- `🧪 BangumiMasterDictionaryTest` (Gradle `testFossDebugUnitTest`) adımı **düştü**. Bu hata
  chunk 004c içeriğiyle ilgili **değil**:
  - Aynı adım `main` 4ef8714 run `39570087370`'de de düşüyor (ayrıca orada `🐍 Python doğrulayıcı`
    da düşüyor — VAULT öncesi bilinen `神作 → masterpiece` sorunu; bu dalda o adım geçiyor).
  - Aynı adım, **yalnız `PROGRESS.md` değiştiren** docs-only commit `e4b968f` run `39571452313`'te de düştü.
  - Testin sabitlediği tüm sözlük iddiaları yerelde RAW üzerinden simüle edildi: **0 uyuşmazlık**
    (`京アニ`/`京都动画`/`ボンズ`/`木棉花`/`TOKYO MX`/`フジテレビ`/`Production I.G`/`Pony Canyon` …
    ve negatif iddialar `光坂高校演劇部`/`PROJECT-XXX`/`Clannad製作委員会` = eşleşme yok).
  - Log/artifact indirme bu ortamda kısıtlı (`results-receiver.actions.githubusercontent.com` ve
    `blob.core.windows.net` erişilemiyor) → adım çıktısı okunamadı.
- Sonuç: dalgalı/altyapı kaynaklı kabul edildi; GitHub UI'dan "Re-run failed jobs" ile yeniden
  denenebilir. Kod tarafı doğrulaması `verify_vault.py` + `verify_bangumi_master.py` +
  `gen --check` + RAW bayt denetimi + yerel RAW simülasyonu ile tamamlandı.

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
- [x] Git commit + push (dal `arena/6131dd54-kitsugi-beta`) + PR #20 açıldı
      (https://github.com/f26901964-eng/Kitsugi-Beta/pull/20)
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


---

## S1–S3 Mimari Geçişi + Chunk 008 + Chunk 007 (`007_a`–`007_j`) Kapanış Özeti — 2026-10-21

### 1. S1–S3 Classpath TSV Veri Deposu Geçişi
- `BangumiTagDictionary.kt` ve `BangumiOrgDictionary.kt` içindeki dev sabit diziler kaldırılarak `app/src/main/resources/bangumi/tags.tsv` (**58.338 grup**) ve `app/src/main/resources/bangumi/orgs.tsv` (**52.474 eşleme satırı**) classpath kaynaklarına taşındı (`TagStore` + `OrgStore`, Context bağımsız, hem APK hem JVM test uyumlu).
- `strings.xml` (app + 3 shard modülü) toplam **TR 58.398 · EN 58.398** kayıtta tutularak Android `R.string` 65.535 DEX sınırı ve JVM 65.535 constant-pool sınırı %100 korundu.
- `vault_to_strings.py`, `verify_vault.py`, `verify_bangumi_master.py` ve `gen_bangumi_master.py` eşzamanlı güncellendi; `gradle.properties`, `bangumi-master.yml` ve `android-build.yml` içinde `-Xss16m` yığın ayarı sabitlendi.

### 2. Chunk 008 (`count=0` Kurum Kuyruğu · 376 Kayıt) & CJK Sızıntı Temizliği
- `canon.tsv` içindeki önceki chunk'lardan kalan **838 CJK sızıntısı** resmî global adlara ve Türkçe/İngilizce kavram karşılıklarına yerinde güncellendi (yeni XML ID eklenmeden).
- `map_chunk_008_orgs.tsv`: **376 kayıt** (**366 kararlı canon + 10 SKIP**, MASTER pending ve `BangumiMasterDictionaryTest.kt` `assertNull` kilitleri tam korundu).

### 3. Chunk 007 (`007_a` → `007_j` · 59.510 Etiket)
- 10 bant sırayla (`007_a` [225], `007_b` [1.179], `007_c` [1.892], `007_d` [2.253], `007_e` [2.373], `007_f` [4.437], `007_g` [4.275], `007_h` [8.666], `007_i` [33.169], `007_j` [1.041]) ayrı map dosyaları (`map_chunk_007_a_tags.tsv` … `map_chunk_007_j_tags.tsv`) ve her bant sonunda tam doğrulama zinciriyle işlendi.
- **Nihai Doğrulama Çıktıları:**
  - `verify_vault.py`: `✅ TÜM KONTROLLER GEÇTİ — 52219 bangumi_* anahtar, 106335 vault kaydı, 121284 map satırı, 121284 damga denetlendi`
  - `verify_bangumi_master.py`: `✅ Tüm kontroller geçti` (`GERÇEK çakışma 0`, `TR 58398 · EN 58398`)
  - `gen_bangumi_master.py --check`: `+0 grup, +0 etiket`, `TR +0, EN +0`
  - `check_test_expectations.py`: `23/23` uyumlu · `check_bangumi_meta_tag_coverage.py`: `158/158` eksiksiz · `unittest`: `30/30 OK`
  - `translations_pending.tsv`: **0 satır** · `translations_done.tsv`: **121.284 satır**.
