# Bangumi VAULT — İlerleme / Devam Planı

> **Bu dosya devir teslim dosyasıdır.** Yeni bir dal/şube işi buradan devralır.
> Son güncelleme: **2026-07-08** · Chunk **001** tamamlandı, uygulamaya işlendi.
> PR: https://github.com/f26901964-eng/Kitsugi-Beta/pull/15 (merge edildiyse `main`'de)
> Drive klasörü: https://drive.google.com/drive/folders/1pZAOYsAnMjveNK6grdXjqV9IDM1TFaJp

---

## 0. YENİ DAL / YENİ ORTAM KURULUMU — ÖNCE BURAYI OKU

Yeni bir dalda işe başlayan ajan SIFIRDAN araştırma yapmaz; şu sırayı izler:

### a) Dal kurulumu (KRİTİK)
- Chunk 001 işi commit `5c13f8e` dal `arena/a3fae7fe-kitsugi-beta` üzerinde ve **PR #15**'te.
- **Yeni dalı `main`'den açmadan ÖNCE PR #15'i merge edin** (veya yeni dalı
  `arena/a3fae7fe-kitsugi-beta`'dan türetin). Aksi hâlde chunk 001 (strings +
  sözlükler + kararlar) yeni dala GİRMEZ ve iş tekrarlanır.

### b) Veri restorasyonu (yalnız çalışma alanı boşsa)
Vault JSON'ları bilerek git'e girmez (`.gitignore`). Dosyalar çalışma alanında
varsа (`diag/bangumi_vault/*.json`) bu adımı ATLA. Yoksa Drive'dan indir:

| Drive dosyası | Hedef |
|---|---|
| tags.json, studios.json, producers.json, publishers.json, magazines.json, networks.json, committees.json, vault.json, manifest.json, genres.json | `diag/bangumi_vault/` |
| translations_pending.tsv, translations_done.tsv, progress.json | `diag/bangumi_vault/` |
| canon.tsv, map_chunk_001_tags.tsv, map_chunk_001_orgs.tsv, org_global_names.tsv | `diag/bangumi_vault/decisions/` |
| PROGRESS.md | `diag/bangumi_vault/` |

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

---

## 1. İşin amacı (kullanıcı talebi)

Bangumi VAULT yığınlarındaki (tags, genres, producers, publishers, studios, networks,
magazines, committees, vault, translations_pending.tsv) her etiket/kurum için:

1. Uygulamada **global karşılığı** varsa ona bağla (adı global gelenekle uyumlu),
2. Yoksa **TR + EN çevirisi** üret ve dil dosyalarına ekle,
3. Sonunda **uygulamaya tam entegrasyon** (strings.xml + BangumiTagDictionary +
   BangumiOrgDictionary + vault JSON karar alanları).
4. Çalışma **parça parça (chunk)** ilerler; her turdan sonra bu dosya güncellenir.

## 2. Mevcut durum — CHUNK 001 (TAMAM ✓)

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

`python3 diag/bangumi_vault/scripts/verify_vault.py` → **TÜM KONTROLLER GEÇTİ**
(2127 bangumi_* anahtar, 714 vault string kaydı).

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
- [ ] **Chunk 002 — SIRADAKİ İŞ.** Etiketler count 200–499 (~230 kayıt).
  Nasıl: `python3 scripts/worklist.py --dump tag 300` → ilk 230 kararsız etiket
  (count≤499) elle karar → `canon.tsv` + `map_chunk_002_tags.tsv` → pipeline (Bölüm 0d).
- [ ] **Chunk 003** — etiketler count 100–199 (~330 kayıt), aynı yöntem.
- [ ] **Chunk 004** — kurumlar count 20–99 (tüm roller, ~1.200 kayıt).
  `python3 scripts/draft_org_map.py --chunk 004 --min-count 20` taslak üretir;
  `org_global_names.tsv` yeni tanınan adlarla zenginleştirilip gözden geçirilir.
- [ ] **Chunk 005+** — etiket count 20–99, sonra kuyruk; kurum 1–19;
  committees (1.229) + üretici kişileri (`as_is`/bilinen romanizasyonlar).
- [ ] **Final** — kalan `translations_pending.tsv` tükendi mi? `verify_vault.py` ✅?
  PROGRESS.md + Drive + PR güncelle.

Her chunk aynı pipeline'ı izler (Bölüm 0d). Bilinmeyen kişi/kurumlar `as_is` kalır
(kural: romanizasyon uydurulmaz).

## 6. Bilinçli kararlar / notlar

- **Roller arası birleşim**: aynı marka farklı rollerde geçiyorsa tek string
  anahtarında toplanır (canon satırı yine rola özeldir). Ör. producer_madhouse →
  `bangumi_studio_madhouse`; producer 東映 → `bangumi_studio_toei_animation`.
- **Kişi romanizasyonları** yalnızca bilinenlerde: 丸山正雄→Masao Maruyama,
  志倉千代丸→Chiyomaru Shikura, 李旎→Ni Li vb. (`org_global_names.tsv`).
- **Yapımcı üreticiler** (PoRO, pixy, ViViAN, EDGE…) romanize edilmedi → as_is
  (muhtemel yetişkin içerik; repo riski).
- `44℃梅毒`, `Inc`, `GOLD`, `ワーナー`, `エンタテインメント`… gibi ad parçaları SKIP.
- vault.json + committees.json = yinelenen kopyalar; `apply_map.py` tüm kopyalara
  damgalar (mükerrer karar yok).
- İzlenebilirlik: `map_chunk_*.tsv` notlarında gerekçe; `translations_done.tsv`'de
  origin + TR/EN.

## 7. Bilinen teknik notlar

- Sandbox'ta doğrudan ağ YOK; Drive I/O yalnız google_drive connector
  (dosya değiştirmek = `delete_file` + `upload_file`).
- Kotlin derleyici yok; doğrulama `verify_vault.py` + CI ile.
- **Repo boyutu uyarısı**: vault JSON'ları git'e girmez (`.gitignore`). İleride
  benzer yığınlar `zip → Drive/R2` olarak saklanmalı; repo yalnız script + karar
  dosyalarını tutar.
- Master RAW'da 91 yinelenen etiket temizlendi (2026-07-08); davranış değişmedi
  (byLabel ilk yazım kazanır).

## 8. Kapanış kontrol listesi (chunk sonlarında)

- [ ] `apply_map.py --check` temiz
- [ ] `verify_vault.py` ✅
- [ ] `PROGRESS.md` + `progress.json` güncellendi
- [ ] Drive'a yüklendi: PROGRESS.md, progress.json, translations_pending.tsv,
      translations_done.tsv, decisions/canon.tsv, map_chunk_*.tsv, org_global_names.tsv
- [ ] Git commit (yalnız scripts/ + decisions/ + PROGRESS; veri git'te yok)
