# Bangumi VAULT — İlerleme / Devam Planı

> **Bu dosya devir teslim dosyasıdır.** Yeni bir dal/şube işi buradan devralır.
> Son güncelleme: **2026-10-21** · Chunk **001 + 002 + 003 + 004a + 004b + 004c + 005a + 005b** tamamlandı, uygulamaya işlendi.
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

---

## 0. YENİ DAL / YENİ ORTAM KURULUMU — ÖNCE BURAYI OKU

Yeni bir dalda işe başlayan ajan SIFIRDAN araştırma yapmaz; şu sırayı izler:

### a) Dal kurulumu (KRİTİK)
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

✅ **Chunk 004c sonrası güncel restore hedefi**: Drive JSONları hâlâ yalnız chunk 001
damgası taşır; güncel tüm map dosyalarıyla `apply_map.py` çalıştırılınca
`verify_vault.py` **7.651 anahtar · 6.339 kayıt · 7.640 map · 121.284 damga** vermelidir.

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

> `diag/bangumi_vault/PROGRESS.md` dosyasını oku — Bangumi VAULT kurum/etiket çeviri işinin
> devir teslim dosyasıdır; hiçbir şeyi sıfırdan araştırma, her şey orada yazılı.
>
> **(0) Dal:** Chunk 001 + 002 + 003 + 004a + 004b + 004c çalışmasının tamamı **`main`'e merge edildi**
> (PR #22, 2026-10-21). Yeni oturum dalın `main`'den açıldıysa `diag/bangumi_vault/` **hazır gelir**;
> bu adımı atla ve doğrudan (1)'e geç. Yalnız `ls diag/bangumi_vault/PROGRESS.md` **YOK** diyorsa
> (eski/stale checkout) **kendi oturum dalında**, dal adını değiştirmeden şu iki komutu çalıştır
> (fetch refspec kısıtlı olabilir, bu yüzden açık `src:dst` biçimi şart):
>
> ```bash
> git fetch --no-tags origin \
>   refs/heads/arena/c54b58d5-kitsugi-beta:refs/remotes/origin/arena/c54b58d5-kitsugi-beta
> git reset --hard refs/remotes/origin/arena/c54b58d5-kitsugi-beta
> ```
>
> Bu uç `main`'in 2.4.326 sürümünü (MAL URL düzeltmeleri dâhil) zaten içerir; ayrıca
> `git merge origin/main` gerekmez. Dal **adını değiştirme**; yalnız kendi dalının içeriğini
> bu uca eşitle. Doğrulama: `ls diag/bangumi_vault/PROGRESS.md` görünmeli. Hiçbir chunk'ı
> yeniden araştırma/üretme.
> `git reset --hard` kullanmak istemiyorsan alternatif: `git merge --ff-only` çalışmazsa
> `git checkout refs/remotes/origin/arena/c54b58d5-kitsugi-beta -- .` + `git commit`.
>
> **(1) Kurulum:** Bölüm 0b tablosuyla veri dosyalarını Drive klasöründen indir
> (`1pZAOYsAnMjveNK6grdXjqV9IDM1TFaJp`, ~74 MB; `download_file` paralel çağrılarla,
> `google_drive/` içine yazar, içeriği bağlama dökmez). `decisions/*.tsv` git'te zaten var.
> Drive'daki vault JSON'ları **yalnız chunk 001 damgalıdır** → indirdikten sonra **mutlaka**
> `python3 diag/bangumi_vault/scripts/apply_map.py` çalıştır (`--check` DEĞİL; chunk 002+003+004a+004b+004c
> kararlarını damgalar, kayıpsız). Sonra `verify_vault.py` ✅ olmalı:
> **`7651 bangumi_* anahtar, 6339 vault kaydı, 7640 map satırı, 121284 damga`**.
>
> **(2) İş:** Bölüm 5'teki **ilk ☐** kutucuğu = **Chunk 005b — kurum count 5–9 (4.311 kayıt)**.
> Sıra: `python3 diag/bangumi_vault/scripts/draft_org_map.py --chunk 005b --min-count 5 --max-count 9`
> (`--min-count` ZORUNLU; varsayılan 100'dür, verilmezse bant boş döner)
> → taslağı gözden geçir, `org_global_names.tsv`'ye **yalnız resmî Latin adı bilinen** kurum/kişileri
> ekle → `apply_map.py --check && apply_map.py` → `vault_to_strings.py` → `verify_vault.py`.
> Sıradaki bantlar Bölüm 5'te: 005c (1–4 · 45.420 → alt bantlara böl), 006 (etiket 20–99 · 4.027),
> 007 (etiket <20 · 59.510 + committees). Etiket işleri `scripts/worklist.py` + elle karar (Bölüm 4).
> Karar kalıpları için "Chunk 002 karar şablonu"nu (6 adım) ve Bölüm 2e/2f/2g'yi (kurum chunk örnekleri) kullan.
> Not: `committee` rolü `draft_org_map.py` ORG_FILES'ta yok; kayıtlar `producers.json` ile aynı key'i
> taşıyorsa taslak yine kapsar (004c'de böyle oldu), aksi hâlde satır elle eklenir.
>
> **(3) Kapanış (Bölüm 8):** tik at (☐→☑), Bölüm 2 tablosunu + kümülatif sayıları güncelle,
> PROGRESS.md + progress.json + karar dosyalarını (canon.tsv, org_global_names.tsv,
> map_chunk_<chunk>_*.tsv) Drive'a yükle (aynı ad varsa `delete_file` + `upload_file`),
> `verify_bangumi_master.py` + `gen_bangumi_master.py --check` + `git diff --quiet` ile CI'yı
> yerelde simüle et, commit + push + PR aç (yeni PR'ın numarasını PROGRESS.md'ye yaz).
>
> **Kurallar:** global karşılık varsa kullan; yoksa TR+EN çevir; bilinmeyen CJK'ya romanizasyon
> **uydurma** (`as_is` kalır); kişi adlarında yalnız resmî Latin yazımı bilinenler `global`;
> SKIP girdileri dil dosyasına **girmez**.
> **Yasak:** `gen_bangumi_master.py`'yi `--check` olmadan çalıştırma (VAULT RAW satırlarını siler,
> Bölüm 7 ⚠).
> **Kapsam dışı:** CI'daki `MalWebParserTest` / `MalNotificationsClientTest` hataları Bangumi işiyle
> ilgisiz; MAL koduna dokunma (kullanıcı istemedikçe).
> **Not:** ortamda JDK/kotlinc kurulu gelmez; yerel Kotlin derlemesi yoksa `verify_bangumi_master.py`
> + `gen --check` + RAW parça bayt denetimi (her parça < 50.000 modified-UTF-8 bayt) yeterlidir;
> asıl derleme CI'da koşar.

Kısa sürüm (dosya zaten çalışma alanında ve damgalıysa yeterli):
**"`diag/bangumi_vault/PROGRESS.md`'yi oku, Bölüm 0 kurulumunu yap, Bölüm 5'teki sıradaki
☐ chunk'ı (005c) bitir, Bölüm 8 kapanış listesini uygula."**

Sıradaki chunk: **005c — kurum count 1–4 (45.420 kayıt, alt bantlara bölünür)**. Chunk 005b
(5–9 · 4.311 kayıt) tamamlandı; `translations_pending.tsv` 62.958 satır (kurum kayıtları
pending'te yok). Chunk 005 üç parçaya bölündü: **005a 10–19 ✓**, **005b 5–9 ✓**, 005c 1–4;
kurumda count ≥5 kalmadı. Etiket tarafı sırada: **006 (20–99 · 4.027)**, **007 (<20 · 59.510)**.

---

## 1. İşin amacı (kullanıcı talebi)

Bangumi VAULT yığınlarındaki (tags, genres, producers, publishers, studios, networks,
magazines, committees, vault, translations_pending.tsv) her etiket/kurum için:

1. Uygulamada **global karşılığı** varsa ona bağla (adı global gelenekle uyumlu),
2. Yoksa **TR + EN çevirisi** üret ve dil dosyalarına ekle,
3. Sonunda **uygulamaya tam entegrasyon** (strings.xml + BangumiTagDictionary +
   BangumiOrgDictionary + vault JSON karar alanları).
4. Çalışma **parça parça (chunk)** ilerler; her turdan sonra bu dosya güncellenir.

## 2. Mevcut durum — CHUNK 001 ✓ + 002 ✓ + 003 ✓ + 004a ✓ + 004b ✓ + 004c ✓ + 005a ✓ + 005b ✓

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

### 2d) Kümülatif durum (chunk 001 + 002 + 003 + 004a + 004b + 004c + 005a + 005b)

```
python3 diag/bangumi_vault/scripts/verify_vault.py
✅ TÜM KONTROLLER GEÇTİ — 11292 bangumi_* anahtar, 10009 vault kaydı, 11951 map satırı, 121284 damga denetlendi
```

| Rol | Toplam kayıt | Karar satırı (canon + SKIP) | Kararlı canon | SKIP |
|---|---:|---:|---:|---:|
| tag | 64.499 | **962** | **675** | 287 |
| studio | 12.391 | 2.445 | 2.404 | 41 |
| producer | 24.825 | 4.214 | 4.139 | 75 |
| publisher | 13.227 | 2.995 | 2.918 | 77 |
| magazine | 3.995 | 968 | 945 | 23 |
| network | 2.347 | 367 | 357 | 10 |
| committee | 1.229 | 0 (1 kopya) | 0 | 0 |
| **Toplam** | | **11.951** | **11.438** | **513** |

`committee` satırındaki tek kayıt `producers.json` ile aynı key'i taşıdığı için map satırı
producer altında sayılır (`progress.json`'da her iki rolde de "decided" görünür → toplam 11.952).

Kurum kalan iş yükü (kararsız): count ≥20 → **0** (60–99 ✓, 35–59 ✓, 20–34 ✓),
count 10–19 → **0** (005a ✓), count 5–9 → **0** (005b ✓); kalan **1–4 (45.420)**.
Etiket kalan iş yükü: count 20–99 → **4.027**, count <20 → **59.510**
(toplam **63.537**). `translations_pending.tsv` = **62.958** satır.

`python3 diag/bangumi_master/verify_bangumi_master.py` → **✅ Tüm kontroller geçti**
(**11.693** eşleme · aynı-ad rol çakışması 62 · gerçek çakışma 0; TagDictionary 1.325 grup;
TR/EN **17.471/17.471**; RAW **14 parça**, en büyük **49.999** bayt). `gen_bangumi_master.py --check`
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
- [ ] **Chunk 005c — kurum count 1–4** (**45.420** kayıt — çok büyük; alt bantlara böl:
      `--min-count 3 --max-count 4`, `--min-count 2 --max-count 2`, `--min-count 1 --max-count 1`)
- [ ] **Chunk 006 — etiket count 20–99** (**4.027**): `scripts/worklist.py --min-count 20 --max-count 99`
      + elle karar (Bölüm 4)
- [ ] **Chunk 007 — etiket kuyruğu <20** (**59.510**); committees (1.229) + üretici kişileri
      (`as_is`/bilinen romanizasyonlar).
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

## 2j) HANDOFF_010 oturumu (arena/e29a309c-kitsugi-beta · 2026-10-21)

PR #29 (`arena/46189a1e-kitsugi-beta`) açık. Bu oturum yeni dal `arena/e29a309c-kitsugi-beta`
üzerinde çalışır. Veri Drive klasöründen (1pZAOYs…) taze indirildi; **canon.tsv + map_chunk_005c_*
+ 006_* + 007_* + 008_orgs** Drive kopyasından senkronlandı (git sürümü chunk 005b'de kalmıştı).

Sağlık zinciri sonrası taban durum:
- `verify_vault.py`: ✅ **106.580** `bangumi_*` anahtar · **106.335** vault kaydı ·
  **121.284** map satırı · **121.284** damga (HANDOFF_010 hedefi birebir).
- `verify_bangumi_master.py`: ✅ 52.603 org eşleme · gerçek çakışma 0 · TR/EN 112.812
  simetrik · RAW 67 parça, en büyük 50.000 bayt.
- `gen_bangumi_master.py --check`: ✅ +0/+0 (yazma yok).
- `check_bangumi_meta_tag_coverage.py`: ✅ 158/158.
- `check_test_expectations.py`: 19 uyuşmazlık. 12'si bilinen betik sınırı (eski `o(...)`
  biçimini aradığı için RAW döneminde org satırlarını 0 okuyor — PROGRESS Bölüm 7 / PR #23
  notu). 7'si MASTER verisi ile test beklentisi uyuşmazlığı (`华语`/`華語` →
  `area_chinese_speaking` cid'i ve "Chinese-speaking" EN değeri MASTER'da bu şekilde
  tanımlı; `check_test_expectations.py` eski `chinese_speaking` bekliyor). Bu oturumda
  MAL/Master koduna dokunulmadan bırakıldı (pre-existing regresyon değil; HANDOFF_010 §4
  uyarınca MAL testleri kutsal).
- Unittest: 0 test (dizinde `test_*.py` yok); exit 0.

Araç değişiklikleri:
- **Yeni betik: `scripts/apply_009_batch.py`** — HANDOFF_010 §3'te belirtilen 009 serisi
  batch uygulayıcısı yazıldı: 6-sütun TSV (`rol<TAB>cid<TAB>eski_tr<TAB>yeni_tr<TAB>
  yeni_en<TAB>origin`), eski-tr eşleşme denetimi (ikiz-engelli raporu), `--mirror-repair`
  ile col-8 `bangumi_<rol>_<cid>` ayna satırlarını eşitleme, MASTER-kilit koruması
  (`tags.tsv` new/existing ve `bangumi_org_*` cross-role), boş-son-sekme dahil orijinal
  7/8-sütun biçimini koruma.
- **`scripts/vault_to_strings.py` performans düzeltmesi**: 106K+ `<string>` için tek tek
  regex `pat.search`/`recompile` çağrısı O(N·M) ~5+ dakikalık kasmaya yol açıyordu
  (121.284 map satırıyla görüldü). Blok içeriği tek geçişte satır satır ayrıştırılarak
  `<string>` güncellemesi ~5 saniyeye indi; `patch_tag_dict` için de tek geçişli ayrıştırma
  uygulandı. Biçim/çıktı aynı kalır (geriye dönük uyumlu; eski küçük veri setlerinde de
  çalışır).

009 batch uygulamaları:
- **009 Batch A (kavram çevirileri)** — 3 kayıt: `anime_op_ed` → "Anime Açılış ve Kapanış
  Şarkıları"/"Anime OP/ED" (new), `hacker` → "Bilgisayar Korsanı"/"Hacker" (new),
  `tank_combat` → "Tank Savaşı"/"Tank Combat" (new). Yeni XML ID eklenmedi (yerinde
  güncelleme); mirror-repair temiz geçti.
- **009 Batch B (veri düzeltmesi)** — 1 kayıt: `female_harem` TR "Ters Harem" →
  "Kadın Haremi" (reverse_harem ile karışmıştı; MASTER kilitli değil).
- Not: `area_chinese_speaking` "Çinçe" yazım hatası Drive canon'unda zaten "Çince" olarak
  düzeltilmişti (EN "Chinese-speaking" MASTER tarafından belirlendiği için Türkçesi
  düzeltildi, EN'ye dokunulmadı — MASTER-lock `apply_009_batch.py` tarafından korundu).

Kalan iş (HANDOFF_010 §5 önceliği):
1. Producer CJK kişi adları (ancak kesin bilinen resmî Latin yazımı olanlar; bu oturumda
   üst 100 tarandı, neredeyse tümü küçük doujin çevresi/bilinmeyen kişi adı → `as_is`).
2. Tag CJK sözlük v2 (`records_007_tags.json` üzerinde tam-eşleşme sözlük genişletmesi).
3. Tag `tr==en` dip taraması (13K satır; çoğu özel ad/fandom jargonu, yalnız anlamlı
   kavramlar Türkçeleştirilecek).
4. Org uzun kuyruğu (düşük-count CJK kurum/kişi — yalnız %100 resmî Latin adı bilinenler).

Drive senkron seti bu oturumda güncellendi: vault JSON'ları, `translations_done.tsv`,
`translations_pending.tsv`, `progress.json`, `PROGRESS.md`, `HANDOFF_010.md`, `canon.tsv`,
`org_global_names.tsv`, tüm `map_chunk_*.tsv`, `records_007_tags.json`.

## 2k) 009 serisi devam oturumu — producer 006 + tag 007b (2026-10-21)

HANDOFF_010 sırası korunarak producer kişi adları → 007_b etiketleri işlendi. Her iki batch
mevcut canon satırlarını yerinde güncelledi; **yeni XML ID yok**.

- **Producer 006 (4 kayıt, origin=global):** `青柳昌行`→Masayuki Aoyagi,
  `渡辺繁`→Shigeru Watanabe, `森下勝司`→Katsushi Morishita,
  `落越友則`→Tomonori Ochikoshi. CID ve eski TR değerleri `canon.tsv`/Drive restore verisinden
  eşleştirildi; aynı Latin adlar `org_global_names.tsv`'ye kaynak notuyla eklendi.
- **Tag 007b (2 kayıt, origin=new):** `优秀的氛围`→İyi Atmosfer / Good Atmosphere;
  `画风美`→Güzel Çizim Tarzı / Beautiful Art Style. Bunlar açık anlamlı betimleyici kavramlar;
  özel adlar, belirsiz ifadeler ve fandom jargonu `as_is` bırakıldı.
- `translations_done.tsv` 6 mevcut satırda güncellendi; `translations_pending.tsv` 0 satır.
- `apply_009_batch.py --mirror-repair` zorunlu adımı her batch sonrası çalıştırıldı.
  Bu altı canon'un cross-role ayna kaydı yoktu; mirror-repair temiz/no-op.
- Sağlık zinciri: `apply_map.py --check` + `apply_map.py` ✅ · `vault_to_strings.py` ✅ ·
  `verify_vault.py` ✅ **106580 anahtar / 106335 vault / 121284 map / 121284 damga** ·
  `verify_bangumi_master.py` ✅ **52607 org eşleme, çakışma 0, TR/EN 112812 simetrik** ·
  `gen_bangumi_master.py --check` ✅ · meta tag coverage **158/158**.
- `check_test_expectations.py`: 19 bilinen uyuşmazlık (12 RAW eski-parser sınırı, 7 eski test
  beklentisi); MAL/Master test ve kodlarına dokunulmadı. Unittest keşfi 0 test buldu, OK.

**Sıradaki iş:** producer kuyruğunda sadece resmî Latin yazımı kesin doğrulanabilen kişi kalemleri;
sonra 007_b→007_i'yi count azalan sırada sürdür. Drive `records_007_tags.json` içinde bu aralıktaki
kararsız `as_is` CJK toplamı 43.313; bu sayı çeviri hedefi değildir (özel ad/fandom terimleri
korunur), tam-eşleşmeli anlam sözlüğüyle açık kavramlar seçilir. Sonrasında düşük-count org uzun
kuyruğu. Yeni araştırma turu/romanizasyon tahmini yok.

Bu oturumun çalışma ağacı, Arena'nın sabit aktif dalı `arena/2dcb1fce-kitsugi-beta` üzerinde
ilerletildi; PR #30 başlığı/HEAD'i ayrı `arena/e29a309c-kitsugi-beta` dalında açık kaldı.

PR #30 (`arena/e29a309c-kitsugi-beta`) açık bırakıldı. Güncel main tabanındaki bu port ve yeni
batch'ler için aktif dal üzerinden **PR #31** açıldı: https://github.com/f26901964-eng/Kitsugi-Beta/pull/31.

## 2l) 009 serisi devam — tag 007b batch 002 (2026-10-21)

Count azalan sırada 007_b'den üç açık kavram işlendi; hepsi yerinde `origin=new` güncellemesi,
yeni XML ID yok:
- `轮奸` → **Toplu Tecavüz / Gang Rape** (açık içerik kavramı).
- `剧情性` → **Olay Örgüsü Odaklı / Plot-Driven**.
- `❀一般向け` → **Genel Kitleye Yönelik / General Audience**.

Batch dosyası `worklists/009_decisions_007b_tags_002.tsv`; zorunlu mirror-repair ayrı repair
worklist'iyle çalıştırıldı (ayna satırı farkı yok). Apply-map → strings → vault → MASTER → gen
`--check` → meta coverage zinciri tamamı yeşil; son sayılar değişmedi:
106580 / 106335 / 121284 / 121284, MASTER 0 çakışma ve TR/EN 112812 simetrik, coverage 158/158.
`translations_done.tsv` üç mevcut satırda güncellendi; pending 0. 007_b–007_i havuzunda
kalan CJK `as_is`: **43.310** (hedef/kota değil; adlar ve fandom jargonu korunur).

## 2m) 009 serisi devam — tag 007b batch 003 (2026-10-21)

Count azalan sırayı koruyarak 007_b'deki açık global adlar ve iki net betimleyici kavram yerinde
uygulandı (7 satır, yeni XML ID yok):
- `アクアプラス` → **Aquaplus / Aquaplus** (`global`; `org_global_names.tsv` ile eşleşti).
- `スパイク・チュンソフト` → **Spike Chunsoft / Spike Chunsoft** (`global`; mevcut resmî Latin adı).
- `动视` → **Activision / Activision** (`global`; mevcut `org_global_names.tsv` karşılığı).
- `□HOYO-MiX` → **HOYO-MiX / HOYO-MiX** (`global`; mevcut resmi yapımcı yazımı).
- `Innocent_Grey` → **Innocent Grey / Innocent Grey** (`global`; resmi Latin marka boşluğu düzeltildi).
- `好画风` → **İyi Çizim Tarzı / Good Art Style** (`new`).
- `无节操` → **Utanmaz / Shameless** (`new`).

Batch `worklists/009_decisions_007b_tags_003.tsv` ile uygulandı; mandatory `--mirror-repair`
0 ayna farkı buldu. `translations_done.tsv`'de yedi mevcut satır güncellendi. `apply_map.py`
+ `vault_to_strings.py` + `verify_vault.py` + MASTER verifier + generator `--check` + meta
coverage zinciri yeşil: 106580 key / 106335 vault / 121284 map ve damga; 52607 org mapping,
gerçek çakışma 0, TR/EN 112812 simetrik, coverage 158/158.

Havuz sayısı artık canlı `tags.json` taramasıyla tanımlanıyor: 007_b–007_i içinde `decision=as_is`
ve TR alanında Han/Kana CJK kod noktası olan **43.185** kayıt. Önceki 43.310/43.313 yaklaşık
sayımı farklı kesit/metrik kullandığından bu tanım baz alındı; sayı kota değildir. Özel adlar ve
fandom jargonu `as_is` kalır.

## 2n) 009 serisi devam — producer 006 kaynak doğrulaması + tag 007b batch 004 (2026-10-21)

1. **Producer 006 bağımsız/resmî kaynak doğrulaması (`worklists/009_decisions_006_producers_rev.tsv`):**
   - `青柳昌行` (`cjk_d62d1f`) → **Masayuki Aoyagi**: KADOKAWA Group resmî yönetici profili
     (`group.kadokawa.co.jp/global/company/officer/masayuki_aoyagi.html`) ve KADOKAWA/KanColle
     resmî CD kitapçık jenerikleri (KA2C-0009, KA2C-0013, KA2C-0016) ile doğrulandı; korundu.
   - `渡辺繁` (`cjk_f82404`) → **Shigeru Watanabe**: Bandai Visual / Emotion resmî CD kitapçık
     jenerikleri (*Ghost in the Shell* BVCR-729, *Change!! Getter Robot* BCBA-2857) ve Wikidata
     Q62601586 (`わたなべ しげる`) ile doğrulandı; korundu.
   - `落越友則` (`cjk_82cd54`) → **Tomonori Ochikoshi**: Sony Music Group CloverWorks Inc. resmî
     kurumsal sayfası (`sme.co.jp/en/company/groupcompanies/anx/clw/`) ve Aniplex resmî CD
     kitapçık jenerikleri (SVWC-7291, SVWC-7554~5) ile doğrulandı; korundu.
   - `森下勝司` (`cjk_31ad3b`) → `Katsushi Morishita`: **doğrulanamadı** — Production I.G resmî
     röportaj sayfası (`production-ig.co.jp`, `もりした・かつじ`) ve OLM Inc. resmî yönetim kurulu
     sayfası (`olm.co.jp/about`, `MORISHITA, Katsuji`) `Katsuji` okunuşunu verirken ikincil
     kaynaklarda `Katsushi`/`Katsuji` çelişkisi bulunduğundan `Katsushi Morishita` kaldırıldı;
     kayıt `apply_009_batch.py --mirror-repair` ile Japonca **`森下勝司` (`as_is`)** durumuna
     döndürüldü, `org_global_names.tsv` ve `BangumiOrgDictionary.kt` RAW listesinden çıkarıldı.

2. **Tag 007_b count-15 tamamlayıcı batch 004 (`worklists/009_decisions_007b_tags_004.tsv`):**
   Count 15'teki kalan açık kavramlar ve `org_global_names.tsv` / `canon.tsv`'de mevcut kesin
   resmî Latin adlar yerinde güncellendi (7 satır, yeni XML ID yok):
   - `別冊花とゆめ` (`cjk_1b3e03`) → **Bessatsu Hana to Yume / Bessatsu Hana to Yume** (`global`)
   - `朱夏` (`cjk_3793dc`) → **Shuka / Shuka** (`global`)
   - `星海社` (`cjk_38fa71`) → **Seikaisha / Seikaisha** (`global`)
   - `ファンタジア文庫` (`cjk_be565a`) → **Fantasia Bunko / Fantasia Bunko** (`global`)
   - `シーズウェア` (`cjk_ba1c79`) → **C's ware / C's ware** (`global`)
   - `未上映` (`cjk_221417`) → **Gösterime Girmedi / Not Yet Screened** (`new`)
   - `神画风` (`cjk_86d5f8`) → **Muhteşem Çizim Tarzı / Superb Art Style** (`new`)

3. **Doğrulama ve senkron:**
   - Zorunlu `--mirror-repair` her iki batch'te çalıştırıldı (0 ikiz-engelli, 0 ayna farkı).
   - `translations_done.tsv` üzerinde 8 mevcut satır güncellendi; `translations_pending.tsv` 0.
   - `apply_map.py --check` + `apply_map.py` ✅ · `vault_to_strings.py` ✅ · `verify_vault.py` ✅
     **106580 anahtar / 106335 vault kaydı / 121284 map satırı / 121284 damga** ·
     `verify_bangumi_master.py` ✅ **52606 org eşleme, gerçek çakışma 0, TR/EN 112812 simetrik** ·
     `gen_bangumi_master.py --check` ✅ · `check_bangumi_meta_tag_coverage.py` ✅ **158/158**.
   - Canlı `tags.json` CJK `as_is` aday havuzu (007_b–007_i): **43.178** (kota değildir).
   - Aktif Arena oturum dalı: `arena/284d08ba-kitsugi-beta`.

## 2o) 009 serisi devam — çapraz rol (`magazine`, `publisher`, `studio`, `producer`) ve `tag` (`007_b`–`007_g` + `tr==en`) kapsamlı batch 008 (2026-10-21)

Tüm rollerde (`magazine`, `publisher`, `studio`, `producer`, `network`, `tag`) `as_is` veya
`tr==en` kalmış kayıtlar `org_global_names.tsv` bilgi tabanı, MASTER sözlükleri ve tam-eşleşmeli
kavram sözlüğüyle tarandı; ikiz çakışması (`tr_twins` / `en_twins`) oluşturmayan **196 kanonik
karar** (`worklists/009_decisions_008_comprehensive.tsv`) `apply_009_batch.py --mirror-repair` ile
yerinde uygulandı (yeni XML ID **0**):

1. **Kurum ve Yapımcı Resmî Global Adlar (`23` satır, `origin=global`):**
   - **`magazine` (6):** `ridibooks_2` → **RIDIBOOKS**, `next_2` (`電撃コミックスNEXT`) →
     **Dengeki Comics NEXT**, `cjk_7ab80f` (`月刊ウィングス`) → **Monthly Wings**, `cjk_8e8fa7`
     (`百合姫コミックス`) → **Yuri Hime Comics**, `cjk_d179ae` (`週刊モーニング`) → **Weekly Morning**,
     `gangan_3` (`少年GANGAN`) → **Shonen Gangan**.
   - **`publisher` (3):** `ciel` → **CIEL**, `cjk_75a8f1` (`动漫之家`) → **DMZJ**, `cjk_f396b4`
     (`网易`) → **NetEase**.
   - **`studio` (1):** `cjk_72beb8` (`カクテル・ソフト`) → **Cocktail Soft**.
   - **`producer` (13):** `cjk_4d6da4` (`中田ヤスタカ`) → **Yasutaka Nakata**, `cjk_7f2cdc`
     (`田中ロミオ`) → **Romeo Tanaka**, `cjk_87b65d` (`林直孝`) → **Naotaka Hayashi**, `sca`
     (`SCA-自`) → **SCA-Ji**, `cjk_563ea2` (`八木沼悟志`) → **Satoshi Yaginuma**, `cjk_6d852e`
     (`水野良`) → **Ryo Mizuno**, `cjk_86bf04` (`高松信司`) → **Shinji Takamatsu**, `cjk_9bf5f8`
     (`志方あきこ`) → **Akiko Shikata**, `cjk_a25018` (`打越鋼太郎`) → **Kotaro Uchikoshi**,
     `cjk_e5c795` (`春場ねぎ`) → **Negi Haruba**, `cjk_eb9272` (`瀬古浩司`) → **Hiroshi Seko**,
     `cjk_edbaff` (`井内舞子`) → **Maiko Iuchi**, `cjk_f2aca0` (`加藤達也`) → **Tatsuya Kato**.

2. **Etiket (`role=tag`) Resmî Global Adlar (`114` satır, `origin=global`):**
   - **CJK kurum/dergi/yayınevi/stüdyo/kişi etiketleri (`96` satır, `007_b`–`007_g`):**
     `Overlap Bunko`, `Wani Books`, `France Shoin`, `Niconico Seiga`, `Minato Soft`,
     `Shinji Mikami`, `Shoeisha`, `Nippon Columbia`, `Manga Time Kirara Carat`, `Unison Shift`,
     `Kazuki Nakashima`, `Naoya Shimokawa`, `Satoru Akahori`, `Monthly Comic Cune`,
     `Monthly Princess`, `Kuaikan Comics`, `Dengeki Maoh`, `Kadokawa Bunko`,
     `Hironobu Sakaguchi`, `Seasun Games`, `Takarajimasha`, `Tencent Animation & Comics`,
     `Kachidoki Studio`, `Karin Entertainment`, `Manga Time Kirara MAX`, `Himeya Soft`,
     `JVC Entertainment`, `Silky's SAKURA`, `Silky's Plus`, `Psikyo`, `Kadokawa Games`,
     `Jitsugyo no Nihon Sha`, `Bessatsu Friend`, `Akihiro Hino`, `Actas`, `Bunkasha`,
     `Chingwin Publishing`, `Light Chaser Animation Studios`, `Holp Shuppan`, `Bishoujo Bunko`,
     `Tudou`, `Heluo Studio`, `Hidetaka Miyazaki`, `Jive`, `Dessert`, `Bessatsu Shojo Comic`,
     `Perfect World`, `Ichijinsha Bunko`, `Weekly Shojo Comic`, `Ever Glory Publishing`,
     `Monthly Shonen Sirius`, `ARMS`, `East Press`, `Monthly Morning Two`,
     `Monthly Comic @Bunch`, `Bungeishunju`, `Masao Maruyama`, `Weekly Comic Bunch`,
     `Chuokoron Shinsha`, `Gallop`, `Columbia Music Entertainment`, `Evening`, `Sanzigen`,
     `Bandai Namco Entertainment`, `Fukkan.com`, `Genkosha`, `Jump Kai`, `Sharp Point Press`,
     `Kadokawa Taiwan`, `Young Animal Arashi`, `Media Works Bunko`, `Katsura Hashino`,
     `Monthly Comic Zenon`, `Asuka Shinsha`, `Monthly Young Magazine`, `Monthly Dragon Age`,
     `Super Dash Bunko`, `Shincho Bunko nex`, `Jump SQ.19`, `Cycomi`,
     `Yamaha Music Communications`, `Kodansha Box`, `Jade Dynasty`, `Kindai Mahjong`,
     `TV Aichi`, `Onsen`, `XD Network`, `Monthly Shonen Jump`, `Manga Action`,
     `Monthly Spirits`, `Nippon Cultural Broadcasting`, `Seibido Publishing`, `Eiji Tsuburaya`,
     `Gakken Holdings`, `Monthly Hero's`, `Weekly Manga Times`.
   - **Latin bitişik/varyant stüdyo ve marka etiketleri (`18` satır):**
     `LIDENFILMS`, `Warner Bros. Animation`, `Studio Comet`, `Telecom Animation Film`,
     `project No.9`, `Hoods Entertainment`, `CoMix Wave Films`, `TYO Animations`, `Group TAC`,
     `Polygon Pictures`, `Purple software`, `Lump of Sugar`, `Hal Film Maker`, `FromSoftware`,
     `Studio 3Hz`, `07th Expansion`, `20th Television`, `Arc System Works`.

3. **Etiket (`role=tag`) Kavram / Tür / Biçim Çevirileri (`59` satır, `origin=new`):**
   - **CJK açık kavramlar (`46` satır, `007_c`–`007_g`):**
     `延期` (Ertelendi / Postponed), `默片` (Sessiz Film / Silent Film), `澳剧` (Avustralya Dizisi /
     Australian Drama), `哥特萝莉` (Gotik Lolita / Gothic Lolita), `家庭伦理` (Aile Etiği / Family
     Ethics), `初心者向` (Yeni Başlayanlara Yönelik / Beginner-Friendly), `音乐美` (Güzel Müzik /
     Beautiful Music), `狼人杀` (Kurt Adam Oyunu / Werewolf Game), `冷战` (Soğuk Savaş / Cold War),
     `轻喜剧` (Hafif Komedi / Light Comedy), `平行宇宙` (Paralel Evren / Parallel Universe),
     `误解系` (Yanlış Anlaşılma Komedisi / Misunderstanding Comedy), `自然纪录片` (Doğa Belgeseli /
     Nature Documentary), `卡牌收集类` (Kart Koleksiyonu / Card Collection), `海战` (Deniz Savaşı /
     Naval Warfare), `港片` (Hong Kong Filmi / Hong Kong Film), `一战` (I. Dünya Savaşı / World
     War I), `杀手题材` (Suikastçı Temalı / Hitman Theme), `复仇剧` (İntikam Draması / Revenge
     Drama), `伪推理` (Sözde Polisiye / Pseudo-Mystery), `自然主义` (Natüralizm / Naturalism),
     `光枪射击` (Işık Tabancası Nişancı / Light Gun Shooter), `日式奇幻` (Japon Usulü Fantazi /
     Japanese-Style Fantasy), `北欧神话` (İskandinav Mitolojisi / Norse Mythology), `本格推理`
     (Klasik Kurallı Polisiye (Honkaku) / Orthodox Mystery (Honkaku)), `童话风` (Masalsı Tarz /
     Fairy-Tale Style), `伪纪录片` (Sahte Belgesel (Mockumentary) / Mockumentary), `科普纪录片`
     (Popüler Bilim Belgeseli / Popular Science Documentary), `挪威剧` (Norveç Dizisi / Norwegian
     Drama), `低龄向` (Küçük Yaş Grubuna Yönelik / For Young Children), `web短片` (Web Kısa Filmi /
     Web Short), `商战` (Ticaret Savaşı / Corporate Warfare), `默剧` (Pandomim / Mime), `木偶动画`
     (Kukla Animasyonu / Puppet Animation), `木偶剧` (Kukla Oyunu / Puppetry), `哥特风` (Gotik
     Tarz / Gothic Style), `英伦风` (İngiliz Tarzı / British Style), `逐帧动画` (Kare Kare
     Animasyon / Frame-by-Frame Animation), `爱尔兰剧` (İrlanda Dizisi / Irish Drama), `历史纪录片`
     (Tarih Belgeseli / Historical Documentary), `新加坡剧` (Singapur Dizisi / Singaporean Drama),
     `游戏宣传片` (Oyun Tanıtım Videosu / Game Promotional Video), `北美动画` (Kuzey Amerika
     Animasyonu / North American Animation), `人文纪录片` (Beşerî Bilimler Belgeseli / Humanities
     Documentary), `虚无主义` (Nihilizm / Nihilism), `TV短片` (TV Kısa Filmi / TV Short).
   - **İngilizce (`tr==en`) kalmış kavram/tür/müzik etiketleri (`13` satır):**
     `IndieAnime` (Bağımsız Anime / Indie Anime), `melodic-death` (Melodik Death Metal / Melodic
     Death Metal), `Tactical_shooter` (Taktiksel Nişancı / Tactical Shooter), `symphonic`
     (Senfonik / Symphonic), `Alternative_rock` (Alternatif Rock / Alternative Rock), `electronica`
     (Elektronika / Electronica), `Main_Theme` (Ana Tema Müziği / Main Theme), `TVdrama` (TV Dizisi /
     TV Drama), `acoustic` (Akustik / Acoustic), `Souls-like` (Souls Benzeri / Souls-like), `WRPG`
     (Batı Tarzı RYO / Western RPG), `Indie_pop` (Bağımsız Pop / Indie Pop), `Indie_rock`
     (Bağımsız Rock / Indie Rock).

4. **Doğrulama çıktıları:**
   - `verify_vault.py`: ✅ **106580 anahtar / 106335 vault kaydı / 121284 map satırı / 121284 damga**
   - `verify_bangumi_master.py`: ✅ **52626 org eşleme (+20), gerçek çakışma 0, TR/EN 112812 simetrik**
   - `gen_bangumi_master.py --check`: ✅ temiz (`+0 grup, +0 etiket, +0 kayıt`)
   - `check_bangumi_meta_tag_coverage.py`: ✅ **158/158**
   - Canlı `tags.json` CJK `as_is` aday havuzu (007_b–007_i): **43.022** (kota değildir).

## 2p) Tam Uygulama Entegrasyonu — `main` (`v2.4.336`) + PR #27/#29 S1–S3 Mimarisi + Batch 008 Birleşimi (`arena/80e5e83a-kitsugi-beta` · 2026-10-21)

- PR #30/PR #31'de düşen **4 Gradle dil modülü** (`app` + `bangumi-vault-resources-1..3` + `bangumi_vault_keep.xml`) ve **S1–S3 classpath TSV veri depoları** (`app/src/main/resources/bangumi/tags.tsv` + `orgs.tsv`), güncel `main` (`971abf1`, `v2.4.336`) ve Batch 001–009 + Batch 008 (`4262d84`) kararlarının tamamıyla birleştirildi (`+0` yeni XML ID; hiçbir çeviri/global ad kaybı yok).
- `KitsugiTranslations.kt` (`localizedNameFor` + `コミックス・ウェーブ・フィルム` → `CoMix Wave Films`) ve `KitsugiBangumiDetailClient.kt` (oyun geliştiricileri `开发`/`開發`, yayımcılar `发行`/`發行`, Japonca/Çince dergiler `連載雑誌`/`雜誌`/`雑誌`, yayın istasyonu yerelleştirmesi ve dergi ID/logo zenginleştirmesi) tamamlandı; `Main.kt`, `BangumiMasterDictionaryTest` ve `KitsugiBangumiDetailClientTest` ile TR/EN/`as_is` çalışma zamanı doğrulaması eklendi.
- Doğrulama zinciri: `verify_vault.py` ✅ (`52.219` XML anahtarı · `106.335` vault kaydı · `121.284` map · `121.284` damga) · `verify_bangumi_master.py` ✅ (`58.338` grup · `52.626` org eşleme · çakışma `0` · `strings.xml` TR `58.451` / EN `58.451`) · `gen_bangumi_master.py --check` ✅ · `check_test_expectations.py` ✅ `23/23` · `check_bangumi_meta_tag_coverage.py` ✅ `158/158` · `unittest` ✅ `30/30`.



