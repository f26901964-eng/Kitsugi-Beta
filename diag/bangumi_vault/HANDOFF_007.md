# DEVİR METNİ — Bangumi VAULT 007 (yeni dala geçecek ajana yapıştır)

> Bu metin, 006'yı bitiren oturumun **kaldığı yerden** devam etmek için yazıldı.
> Sıfırdan araştırma yapma; aşağıdaki sırayı izle.

---

## 0. Kimlik / durum

- Repo: `f26901964-eng/Kitsugi-Beta`
- Kaynak dal (006'yı taşıyan, **açık PR**): `arena/8a37df7c-kitsugi-beta`
- Son commit: `03680a8` (öncesi `dd67287`, `f7192f9`, `6f9eeb4`, `3a95702`)
- PR **#25**: https://github.com/f26901964-eng/Kitsugi-Beta/pull/25 — **açık, MERGE EDİLMEDİ**
- Çalışma ağacı temiz; 006 tamamlandı ve uygulandı.
- **Kendi oturum dalında çalış**, yalnız oraya push et; dal adını değiştirme, başka dala geçme.
  `git reset --hard` **yasak**.

## 1. İlk 10 dakika (atlanmaz)

1. `git status --short` → kullanıcı değişiklikleri varsa **ezme**.
2. `git fetch --no-tags origin`.
3. PR #25 merge edilmediği için **006 işi yeni dalda YOKTUR**; taşı:
   - Dene: `git merge --no-edit origin/arena/8a37df7c-kitsugi-beta`
   - `refusing to merge unrelated histories` alırsan (bilinen tuzak) **dosya bazlı** taşı:
     `git checkout origin/arena/8a37df7c-kitsugi-beta -- <path>...` (uygulama dosyaları +
     `diag/**` + `decisions/**`). Çakışmayı körlemesine çözme.
4. Oku: `diag/bangumi_vault/PROGRESS.md` — §0 (kurulum), §2k (chunk 006), §2d (kümülatif),
   §5 (kuyruk/açık kutular), §6 (dersler), §8 (kapanış listeleri) + `DECISIONS/` karar sözlüğü.
5. Büyük dosyaları (`vault.json`, `tags.json`, `progress.json`) **read_file ile açma**;
   Python'la yalnız gerekli alanları/sayaçları oku.

## 2. Devraldığın durum (ölçülmüş sayılar)

| Ölçü | Değer |
|---|---|
| `bangumi_*` anahtar | **52.219** |
| vault kaydı | **51.104** |
| map satırı | **61.398** |
| damga denetimi | **121.284** |
| canon satırı | 55.968 |
| BangumiTagDictionary | 3.983 grup (10 `groupChunkNN()` parçası, ≤400/parça) |
| RAW parça | 66 parça, en büyük 49.999 modified-UTF-8 bayt |
| Kapasite | app yerel 11.292/20.000 · shard 13.565 / 13.782 / 13.580 (sınır 16.000) |

**Otorite cümle (aynen bu çıkmalı):**
`✅ TÜM KONTROLLER GEÇTİ — 52219 bangumi_* anahtar, 51104 vault kaydı, 61398 map satırı, 121284 damga denetlendi`
Bu tutmadan **yeni işe başlama**. `apply_map.py --check` tek başına eski/eksik damgaları yakalamaz.

## 3. Bitti sayılan iş (YENİDEN İŞLEME)

- 001, 002, 003, 004a/b/c, 005a/b, **005c_a/b/c**, **006 (etiket count 20–99)** tamam.
- 006: 4.027 kayıt → 3.251 kararlı + **776 SKIP**; bantlar 60–99 (625), 40–59 (884),
  30–39 (913), 20–29 (1.605); canon 53.244 → 55.968; map 57.371 → 61.398.
- 006 kararlarının kalıcı kaynağı: `diag/bangumi_vault/decisions/006_src/`
  (`README.md`, `records_006_tags.json`, `band_{a,b,c,d}.txt`, `auto.py`, `manual/{a,b,c,d}.py`,
  `gen.py`, `dec_006_{a,b,c,d}.tsv`). Yeniden üretim:
  `python3 diag/bangumi_vault/decisions/006_src/gen.py a` → `build_tag_chunk.py --chunk 006_a --min-count 60 --max-count 99 --decisions …/dec_006_a.tsv --snapshot …/records_006_tags.json`.
- Komite kopyaları 1.229/1.229 damgalı (8'i SKIP) — ayrı "1.229 işlenmemiş komite" kuyruğu yok.

## 4. Sıradaki iş

1. **Chunk 007 — etiket count <20: 59.510 kayıt** (en yüksek count 19; dağılım PROGRESS.md §5).
   - **Kör otomasyonla tek seferde uygulama.** Yönetilebilir alt bantlara böl
     (ör. 19–15, 14–10, 9–7, 6–4, 3–2, 1) ve her bandı ayrı kapat.
   - 007 ölçeği 006'nın ~15 katı: **JVM sınıf/metot sınırı** yanında **Android toplam kaynak
     türü/ID kapasitesi** de değerlendirilmeli; gerekirse daha çok RAW parçası / grup parçası.
2. **count=0 kurumları: 376** (ayrı kuyruk, etiket işinden bağımsız).
3. 007 bitmeden "Bangumi bitti" **deme**.

Her bantta uyulacak karar kuralları:
- `BangumiTagDictionary` grupları + `canon.tsv` + **TÜM TR/EN kaynak kataloğu** karşılaştırılır
  (`diag/bangumi_vault/scripts/resource_catalog.py`; app 17.471 + resources-1/2/3 12.646/12.915/12.708
  = 55.740/55.740). `app/strings.xml`'e bakıp "çeviri eksik" deme.
- Global karşılık varsa mevcut kanona bağla; genel tür/tema/biçim etiketlerine TR+EN çeviri;
  bilinen özel adlarda yalnız **güvenilir resmî Latin yazım**; bilinmeyenler `as_is`
  (CJK as_is cid = `cjk_<md5(value)[:6]>`); **romanizasyon uydurma**.
- Jenerik gürültü / kişisel raf etiketi / bozuk parça → **SKIP** (SKIP map'e ve
  `translations_done.tsv`'e yazılmaz).
- Normalize ikiz + değer tekrarı taranır; `cid`/`slug`/`hash` tek başına aynı varlık kanıtı değil;
  `canon.tsv`'de `(rol, canon_id)` yinelenmez; görüntülenen TR/EN değerler değişmez;
  `bangumi_tag_<canon>` gösterim kaynağı korunur; **"ilk yazım kazanır" gölgelenme denetimi** atlanmaz.
- Komite önekli key'lerde rol JSON `role` alanından alınır (`bangumi_producer_` önekinden tahmin yok).
- Kısa cid tuzağı: `d.e` kullan; slug eşleşmesi yanıltıcı olabilir (`机动战士高达AGE`→`age`, `Disney+`→`disney`).

## 5. Kapanış zinciri (her bant, bu sırayla)

```
apply_map.py --check  →  apply_map.py  →  vault_to_strings.py  →  verify_vault.py
→  verify_bangumi_master.py  →  gen_bangumi_master.py --check
```
- `gen_bangumi_master.py` **asla** `--check` olmadan çalıştırılmaz (VAULT RAW satırlarını siler).
- Testler: `python3 -m unittest discover -s diag/bangumi_vault/scripts -p 'test_*.py'`
  (`pytest` yok). Şu an 23/23 geçiyor; `test_tag_dict_chunks.py` parçalama/yeniden dengeleme/
  idempotentlik testleridir.
- `vault_to_strings.py` grup parçalarını kendisi dengeler (`ensure_group_chunks()` /
  `rerender_group_chunks()`); parça 400 girişi aşarsa liste yeniden bölünür. `--check` **idempotent**
  olmalı (+0/+0).
- Bant başına: `PROGRESS.md` (durum + kümülatif tablo + açık kutular + dersler),
  `progress.json` (tarihsel metadata korunur), `translations_done.tsv` (append), Drive yükleme,
  commit + push + PR; **PR numarası devir dosyasına işlenir**.
- **Kullanıcı istemeden merge yok.**

## 6. Rapor biçimi (kullanıcı bunu şart koştu)

Üç başlık **ayrı ayrı**: (a) **yerel doğrulama** (bu sandbox'ta koşan komutların çıktısı),
(b) **gerçek CI** (GitHub Actions koşu numarası + hangi iş geçti/kaldı), (c) **kalan test engelleri**.
CI'yı "yeşil" diye devretme; başarısızlığı incelemeden "aynı hatadır" varsayma.

## 7. Ortam tuzakları (hepsi bu projede yaşandı)

- Sandbox'ta **JDK/kotlinc yok** → gerçek Kotlin derlemesi yapılmış gibi konuşma;
  "derlendi" deme, CI kanıtını ayrı tut.
- CI **şu an yeşil değil**: `🎌 Bangumi MASTER Doğrulama` işinin `verify` kısmı ✅,
  `compile-and-test` kısmı ❌ (`🧪 BangumiMasterDictionaryTest` adımı). Aynı kırık **`main`'de de** var
  (`39579279060`), PR #24'te de aynı adım (`39583503155`), PR #25'te `39589197313` / `39589748851`.
  Koşu günlüğü ve `bangumi-master-test-log` artefaktı sandbox'tan **indirilemiyor**
  (imzalı URL'ler allowlist dışında) → hata metni doğrulanmadı. Yerel taklitle testin tüm iddiaları
  geçiyor (3.983 tag grubu + 52.176 org satırı), yani eşleme kaynaklı kanıt yok.
- `MalWebParserTest` ve `MalNotificationsClientTest` ile **MAL kodu değiştirilmez**;
  PR #24'ten devreden kırıklar 006'da değiştirilmedi.
- Google Drive'a yalnız **`google_drive` connector**'ı ile erişilir (curl/SSL çalışmıyor).
  Şema: `content_mime_type` (≠ `mime_type`), `supports_all_drives: true`; argümanları `input`
  ile sarmalama. Değişen dosyada `delete_file` → `upload_file`.
  Klasör: `1pZAOYsAnMjveNK6grdXjqV9IDM1TFaJp`.
  **Dosya kimlikleri sabit değil**: her yüklemede yenilenir → kimliği ezberden kullanma,
  klasör listesinden ada göre al.
- `decisions/*.tsv` için **GİT esastır**; Drive kopyasıyla üzerine yazma.
- `.gitignore` gereği `diag/bangumi_vault/*.json`, `*.tsv`, `/google_drive/`, `worklists/` commit
  edilmez; `google_drive/` indirme bitince silinir. Ignore JSON'ları ve çalışma listelerini commit etme.
- Korunacaklar: `android.nonTransitiveRClass=true`; üç modülün `settings.gradle.kts` bağlantıları;
  `app/src/main/res/raw/bangumi_vault_keep.xml` içindeki `tools:keep="@string/bangumi_*"`;
  çalışma zamanı `getIdentifier(name,"string",context.packageName)` yolu.
- `draft_org_map.py` yalnız **kurum** taslağı üretir (`--min-count` zorunlu); etiket çevirisinin
  yerine geçmez. Damgalı kayıtlar taslakta atlanır (005b tuzağı: damgalama sonrası aynı bantta
  boş map üretilir).
- `build_tag_chunk.py --write` aynı cid ile ikinci kez çalıştırılırsa `yinelenen (rol, canon_id)`
  hatası verir; `emitted` seti + `git checkout -- canon.tsv` ile geri dönülür.

## 8. Bana lazım olacak ilk çıktı

Yeni dalda, hiçbir şeyi değiştirmeden:
1. `git status --short`, `git log --oneline -3`
2. `apply_map.py --check` çıktısı
3. `verify_vault.py` çıktısı (yukarıdaki otorite cümle çıkıyor mu?)
4. `verify_bangumi_master.py` + `gen_bangumi_master.py --check` + unittest sonucu
5. **007 bant planı önerisi** (band sınırları + kayıt sayıları + tahmini kapasite etkisi)

Bunlar gelmeden 007 kararlarına başlama.
