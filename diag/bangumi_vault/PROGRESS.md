# Bangumi VAULT — İlerleme / Devam Planı

> **Bu dosya devir teslim dosyasıdır.** Yeni bir dal/şube işi buradan devralır.
> Son güncelleme: **2026-10-21** · Chunk **001 + 002** tamamlandı, uygulamaya işlendi.
> Chunk 002 PR'ı: https://github.com/f26901964-eng/Kitsugi-Beta/pull/16 (**bu dal — chunk 001'i de taşır**)
> Chunk 001 PR'ı: https://github.com/f26901964-eng/Kitsugi-Beta/pull/15 (hâlâ AÇIK; #16 merge edilirse kapatılabilir)
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
| canon.tsv, map_chunk_001_tags.tsv, map_chunk_001_orgs.tsv, map_chunk_002_tags.tsv, org_global_names.tsv | `diag/bangumi_vault/decisions/` |
| PROGRESS.md | `diag/bangumi_vault/` |

İndirme ~74 MB tutar (yalnız `vault.json` 37 MB, `tags.json` 17,5 MB). `download_file`
çağrısı içeriği bağlama dökmeden çalışma alanına yazar → paralel çağrılarla birkaç dakikada iner.
`decisions/*.tsv` dosyaları git'te de var; Drive kopyasıyla **birebir aynı** olmalı
(chunk 002 başında doğrulandı: 4 dosya da IDENTICAL). Fark varsa git sürümü esastır,
Drive kopyası chunk sonunda yenilenir.

Drive'daki JSON'lar karar alanlarıyla (`decision/canon/string_key/chunk`) **damgalıdır**.
Elinizde damgasız (orijinal) kopya varsa tek komutla damgalanır:

```bash
python3 diag/bangumi_vault/scripts/apply_map.py    # map_chunk_*.tsv + canon.tsv → JSON'lara kararları işler (idempotent)
```

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

> `diag/bangumi_vault/PROGRESS.md` dosyasını oku — Bangumi VAULT çeviri işinin devir
> teslim dosyasıdır. (1) Bölüm 0'daki kurulumu izle: `verify_vault.py` sağlık kontrolü
> ✅ olmalı; veri dosyaları yoksa Bölüm 0b'deki tabloyla Drive'dan indir. (2) Bölüm 5'teki
> ilk ☐ kutucuğundaki chunk'ı yap (karar ver → `apply_map.py` → `vault_to_strings.py` →
> `verify_vault.py`). (3) Bitince tik at (☐→☑), Bölüm 2 tablosunu güncelle, PROGRESS.md +
> progress.json + karar dosyalarını Drive'a yükle, commit + push + PR aç.
> Kurallar: global karşılık varsa kullan; yoksa TR+EN çevir; bilinmeyen CJK'ya
> romanizasyon uydurma (as_is kalır); SKIP girdileri dil dosyasına girmez.

Kısa sürüm: **\"PROGRESS.md'yi oku, Bölüm 0 kurulumunu yap, Bölüm 5'teki sıradaki
chunk'ı bitir.\"** — bu kadar.

---

## 1. İşin amacı (kullanıcı talebi)

Bangumi VAULT yığınlarındaki (tags, genres, producers, publishers, studios, networks,
magazines, committees, vault, translations_pending.tsv) her etiket/kurum için:

1. Uygulamada **global karşılığı** varsa ona bağla (adı global gelenekle uyumlu),
2. Yoksa **TR + EN çevirisi** üret ve dil dosyalarına ekle,
3. Sonunda **uygulamaya tam entegrasyon** (strings.xml + BangumiTagDictionary +
   BangumiOrgDictionary + vault JSON karar alanları).
4. Çalışma **parça parça (chunk)** ilerler; her turdan sonra bu dosya güncellenir.

## 2. Mevcut durum — CHUNK 001 ✓ + CHUNK 002 ✓

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
- `decisions/canon.tsv`: **+162 satır** (733 → 895) · `decisions/map_chunk_002_tags.tsv`: **297 satır**
- `decisions/org_global_names.tsv`: **+73 satır** (42 kişi + 27 marka + bölüm başlıkları) → 469
- `translations_done.tsv`: 874 → **1.171** satır · `translations_pending.tsv`: 63.745 → **63.448**

### 2c) Kümülatif durum (chunk 001 + 002)

```
python3 diag/bangumi_vault/scripts/verify_vault.py
✅ TÜM KONTROLLER GEÇTİ — 2242 bangumi_* anahtar, 855 vault kaydı, 1171 map satırı
```

| Rol | Toplam | Kararlı | SKIP |
|---|---|---|---|
| tag | 64.499 | **471** | 151 |
| studio | 12.391 | 63 | 1 |
| producer | 24.825 | 126 | 3 |
| publisher | 13.227 | 373 | 18 |
| magazine | 3.995 | 127 | 0 |
| network | 2.347 | 11 | 0 |
| committee | 1.229 | 0 | 0 |

Etiket kalan iş yükü (kararsız): count 100–199 → **491**, count 20–99 → **4.027**,
count <20 → **59.510** (toplam 63.284). `translations_pending.tsv` = 63.448 satır.

Ayrıca `python3 diag/bangumi_master/verify_bangumi_master.py` → **✅ Tüm kontroller geçti**
(1.724 eşleme · aynı-ad rol çakışması 62 · **GERÇEK çakışma 0**). Chunk 002 öncesinde bu
doğrulayıcı 10 hata veriyordu; bkz. Bölüm 7'deki düzeltme notu.


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
#    - python3 scripts/draft_org_map.py --chunk XXX --min-count N   (kurum taslağı, bilgi tabanı ile)

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
- [ ] **Chunk 003 — SIRADAKİ İŞ.** Etiketler count 100–199 (**491** kayıt — plandaki
      ~330 tahmininden fazla).
      Nasıl: `python3 scripts/worklist.py --min-count 100` → `worklists/tags_pending.tsv`
      içinde count 100–199 aralığındaki ilk satırlar → elle karar → `canon.tsv` +
      `map_chunk_003_tags.tsv` → pipeline (Bölüm 0d).
      Chunk 002'nin karar kalıpları birebir yeniden kullanılabilir (aşağıdaki "hazır şablon").
- [ ] **Chunk 004** — kurumlar count 20–99 (tüm roller, **3.082** kayıt: studio 682,
      producer 1.138, publisher 844, magazine 335, network 82, committee 1).
      `python3 scripts/draft_org_map.py --chunk 004 --min-count 20` taslak üretir;
      `org_global_names.tsv` yeni tanınan adlarla zenginleştirilip gözden geçirilir.
      ⚠ Bu chunk 3.000+ kayıt olduğu için alt parçalara bölmek (004a/004b/…) önerilir.
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

## 7. Bilinen teknik notlar

- Sandbox'ta doğrudan ağ YOK (yalnız `github.com` + `api.github.com` + paket depoları);
  Drive I/O yalnız **google_drive connector** ile.
  ✅ **İşleyen indirme yolu**: `download_file` çağrısı dosyayı **çalışma alanına** yazar
  (`google_drive/<ad>`), içeriği bağlama DÖKMEZ → 37 MB `vault.json` bile sorunsuz iner.
  (`read_file_text` BÜYÜK dosyalarda KULLANMA — tüm içeriği bağlama basar.)
  Dosya değiştirmek = `delete_file` + `upload_file`; indirdikten sonra `google_drive/`
  klasörünü sil (`.gitignore`'da zaten).
- Kotlin derleyici yok; doğrulama `verify_vault.py` + `verify_bangumi_master.py` + CI ile.
- **Repo boyutu uyarısı**: vault JSON'ları git'e girmez (`.gitignore`). İleride
  benzer yığınlar `zip → Drive/R2` olarak saklanmalı; repo yalnız script + karar
  dosyalarını tutar. Chunk 002'de `worklists/` da `.gitignore`'a alındı: tam iş listesi
  63.284 satır üretip repoyu şişiriyordu (chunk 001 onu yanlışlıkla 963 satırda commit
  etmişti). `worklists/` yeniden üretilebilir ara çıktıdır → `worklist.py --min-count N`.
- Master RAW'da 91 yinelenen etiket temizlendi (2026-07-08); davranış değişmedi
  (byLabel ilk yazım kazanır).
- ⚠ **`gen_bangumi_master.py` VAULT satırlarını SİLER (ÇÖZÜLMEDİ).** Üreteç
  `BangumiOrgDictionary.kt`'yi şablondan **baştan yazar** (`%RAW%` ← yalnız `orgs.tsv`),
  yani `--check` olmadan çalıştırılırsa VAULT'un eklediği **711 RAW satırı kaybolur**
  (1.724 → 1.013). CI'daki `Üreteç güncellik denetimi` adımı `--check` ile çalıştığı ve
  `--check` yazmadığı için **CI şu an YEŞİL**; tehlike yalnız elle çalıştırmada.
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
- **Chunk 002'de yapılan iki araç düzeltmesi** (davranış iyileştirmesi, geriye dönük uyumlu):
  1. `scripts/worklist.py` — tüm kurum rolleri artık **tek** `orgs_pending.tsv`'de
     toplanıyor. Önceki sürüm her rolde dosyanın üzerine yazıyordu → yalnız son rol
     (committee, 1 kayıt) kalıyor, 3.081 kurum kayboluyordu.
  2. `diag/bangumi_master/verify_bangumi_master.py` — çok-rollü kurum adları artık
     **dil dosyasından** da çözülüyor. Chunk 001'in RAW tekilleştirmesi 4 anahtarın
     (`bangumi_studio_ponoc`, `bangumi_producer_bilibili`, `bangumi_producer_cbc`,
     `bangumi_producer_disney_plus`) tüm satırlarını ikizine kaptırmıştı; doğrulayıcı
     bu yüzden Latin adı çözemeyip 10 zararsız çakışmayı "GERÇEK çakışma" diye
     raporluyordu (exit 1 → CI kırmızı). Düzeltme sonrası: **✅ 0 gerçek çakışma**,
     ayrıca `main`'de zaten var olan `神作 → masterpiece (beklenen classic)` hatası da
     gitti (VAULT `masterpiece` grubu etiketi sahiplendi).
     Not: bu dosya chunk 001 öncesinde `main`'de de exit 1 veriyordu.

## 8. Kapanış kontrol listesi (chunk sonlarında)

Chunk 002 için durumu (2026-10-21) → her satır **✅ tamam**:

- [x] `apply_map.py --check` temiz (879 canon · 1.171 map · 998 canon + 173 skip)
- [x] `verify_vault.py` ✅ (2.242 `bangumi_*` anahtar · 855 vault kaydı · 1.171 map satırı)
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
