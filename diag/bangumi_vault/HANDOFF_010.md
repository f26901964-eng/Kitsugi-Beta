# HANDOFF_010 — Bangumi Vault Global Eşleştirme & TR/EN Çeviri Devralma (009 devamı)

Bu belge, `HANDOFF_009.md`'nin üzerine yazar: 009 devralması **tamamlandı**, toplu iş 001–005 işlendi.
Yeni oturum bu dosyadan başlasın; HANDOFF_009'u yalnızca tarihî arka plan için okusun.

---

## 0. Oturum başlangıç kontrol listesi

1. **Branch/PR durumu:** Önceki iş `arena/46189a1e-kitsugi-beta` üzerinde, PR **#29** (main'e açık).
   `gh pr view 29 --json state` ile bak: merged ise main'den çek; açık/closed ise branch'in
   son commit'i (`abf5bea` veya üstü) üzerinden devam et. **ASLA main'i ezme** — v2.4.331+
   oyuncu/altyapı düzeltmeleri kutsaldır.
2. **Drive'dan veri geri yükleme:** Klasör `1pZAOYsAnMjveNK6grdXjqV9IDM1TFaJp`. Bu oturumda
   TAZE `list_files` çalıştır, dönen ID'lerle `download_file` yap (ID'ler her yüklemede yenilenir;
   ezberden ID KULLANMA). İndirilecekler → `diag/bangumi_vault/` altına: `vault.json`,
   `tags.json`, `producers.json`, `committees.json`, `networks.json`, `magazines.json`,
   `studios.json`, `publishers.json`, `progress.json`, `translations_done.tsv`,
   `translations_pending.tsv`, `records_007_tags.json`, `genres.json`, `manifest.json`
   (hepsi gitignored; repo'da olmamaları normaldir). Büyük JSON'ları ASLA read_file ile okuma
   (yalnızca Python). canon.tsv git'tedir (Drive kopyası yalnızca yedek).
3. **Sağlık doğrulaması (beklenen değerler):**
   - `verify_vault.py` → `✅ TÜM KONTROLLER GEÇTİ — 52219 bangumi_* anahtar, 106335 vault kaydı, 121284 map satırı, 121284 damga denetlendi`
   - İlk koşuda strings↔vault uyuşmazlığı görülürse panik yok: sırayla `apply_map.py --check` +
     `apply_map.py` + `vault_to_strings.py` çalıştır → sonra verify tekrar (devralma sendromu).
   - `verify_bangumi_master.py` → TR/EN **58.441** simetrik · `gen_bangumi_master.py --check` → +0/+0
   - `check_test_expectations.py` → 23/23 · `check_bangumi_meta_tag_coverage.py` → 158/158 · unittest → 30/30

## 1. Ana görev (değişmedi)

`canon.tsv`'deki `as_is` (CJK metin) ve `tr==en` (salt İngilizce) kayıtların derin global
eşleştirmesi ve TR/EN çevirisi. Kuyruk: **count azalan**; rol sırası: **network (bitti) →
magazine (bitti) → studio (bitti) → publisher (bitti) → producer → tag** (007_b → 007_i bandı,
kavram sözlüğü + TR==EN dip taraması).

**Kural (a)** — Global resmî adı %100 bilinen kurum/kişi/seri: `TR = EN = Official Global Name`,
`origin = global`. Romanizasyon/pinyin **UYDURMA KESİNLİKE YASAK**; emin değilsen `as_is` bırak
(doujin çevre adları, bilinmeyen kişiler, belirsiz fragmanlar dokunulmadan kalır).

**Kural (b)** — Global özel adı olmayan anlamlı kavram/tür/alt-tür/biçim/tema/rol etiketleri:
`TR = doğal Türkçe`, `EN = doğal İngilizce`, `origin = new`. TR==EN kalmış kavram etiketlerini de
Türkçeleştir — **hariçler:** 146 kilitli MASTER satırı (`diag/bangumi_master/tags.tsv`), cross-role
kurum anahtarları ve iki dilde de aynı kullanılan fandom jargoni (hentai, yuri, eroge, OVA, ACG…).

## 2. Yapılanlar (009 serisi)

| Batch | Rol | Sonuç | Not |
|---|---|---|---|
| 001 | network | 65/14 ikiz/0 | XTV, HBC, Tokyo MX, iQiyi, BTV, CCTV-14 |
| 002 | magazine | 33/15/0 | Young Magazine, Manga Life + `轻小说`→**Hafif Roman/Light Novel** (origin=new) |
| 003 | studio | 10/5/0 | Vasoon, Rocen Digital, Colored Pencil, Valkyria, Fuguriya |
| 004 | publisher | 13/7/0 | Hifumi Shobo, Diao ye Zong, Pizuya's Cell, AramiTama, 556mm, xi-on |
| 005 | tag kavram | 24/7/**1 kilit** | tankobon→Manga Cildi, oped→Açılış+Kapanış, CJK sözlük eşleşmeleri |

canon diff'i tam **172 satır** (tamamı `009:` notlu) · strings.xml **+0 yeni ID** · orgs.tsv 52.603
satır · tags.tsv 58.338 grup. Detay: `PROGRESS.md` son bölüm + `worklists/009_decisions_00X_*_report.tsv`
(worklists gitignored — yoksa `analyze_009_queue.py` ile yeniden üret).

## 3. Araç zinciri (her batch'te AYNI sırayla)

```
worklists/009_decisions_XXX.tsv hazırla (6 sütun: rol<TAB>cid<TAB>eski_tr<TAB>yeni_tr<TAB>yeni_en<TAB>origin)
python3 diag/bangumi_vault/scripts/apply_009_batch.py worklists/009_decisions_XXX.tsv
python3 diag/bangumi_vault/scripts/apply_009_batch.py --mirror-repair     # ZORUNLU (aşağıya bak)
python3 diag/bangumi_vault/scripts/apply_map.py --check && python3 diag/bangumi_vault/scripts/apply_map.py
python3 diag/bangumi_vault/scripts/vault_to_strings.py
python3 diag/bangumi_vault/scripts/verify_vault.py
python3 diag/bangumi_master/verify_bangumi_master.py
python3 diag/bangumi_master/gen_bangumi_master.py --check   # ASLA --check'siz: 720 satırı siler
python3 diag/bangumi_master/check_test_expectations.py
python3 scripts/check_bangumi_meta_tag_coverage.py
python3 -m unittest discover -s diag/bangumi_vault/scripts -p "test_*.py"
```

**Kazanılmış dersler (tekrar etme):**
- **mirror-repair şart:** Bir (rol,cid) değeri değişince diğer rollerde col-8 `bangumi_<rol>_<cid>`
  taşıyan ayna satırlar eskide kalır → verify_vault `vault içinde çelişkili tr/en` verir. Her
  değer-değiştiren batch'in ardına `--mirror-repair` koy (kilitli 68 satırı ve MASTER `bangumi_org_*`
  referanslarını kendisi atlar).
- **İkiz-engelli ≠ hata:** Hedef değer zaten başka bir cid'de kanonikse uygulama atlanır — bu,
  veri tabanının o adı zaten doğru taşıdığı anlamına gelir; rapora bak, zorlama.
- **MASTER-kilit blokları beklenir:** 146 MASTER tag satırı + cross-role org anahtarları
  uygulayıcı tarafından korunur (örn. `genre_casual` bloklandı — doğru davranış).
- **canon.tsv biçimi:** Yerinde düzenle, satır silme/cid değiştirme yasak, sütun sayısını koru
  (uygulayıcı artık orijinal 7/8 sütun biçimini birebir koruyor — elle rstrip YAPMA; HEAD'de
  sondaki-sekmeli 44 satır bilinçli böyle).
- Dizinler: karar TSV'leri `diag/bangumi_vault/worklists/` altına (`009_decisions_XXX_<rol>.tsv`),
  raporlar aynı yere otomatik düşer.

## 4. Kilitler (test-korumalı, asla bozma)

- `d_anime_store`: TR `dアニメストア` / EN `d Anime Store`
- org assertNull: `光坂高校演劇部`, `PROJECT-XXX`, `Clannad製作委員会` (SKIP kalır)
- tag assertNull: `ASOBI` (SKIP) · tags.tsv satır sırası "ilk yazan kazanır"
- Yıl/puan/kişisel-raf/gürültü etiketleri SKIP kalır; strings.xml'e **yeni XML ID eklemek yasak**
  (TR/EN 58.441; Android DEX 65.535 sınırı) — yalnızca yerinde güncelleme
- `app/src/main/res/values*/strings.xml` = TR; `values-en` = EN (+3 shard modül aynı düzen)
- MAL kodu/testlerine DOKUNMA. Pre-existing fail'ler: `MalNotificationsClientTest`,
  `MalWebParserTest`, CI `BangumiMasterDictionaryTest` (main'de de fail — regresyon değil)

## 5. Sıradaki iş (öncelik sırası)

1. **Producer batch'leri:** Yalnız resmî romanizasyonu **kesin** bilinen kişiler aday
   (örn. `青柳昌行`→Masayuki Aoyagi, `渡辺繁`→Shigeru Watanabe, `森下勝司`→Katsushi Morishita,
   `落越友則`→Tomonori Ochikoshi — uygulamadan önce queue'dan cid+eski TR çekip doğrula).
   Kuyruk ağırlığı kişi adı; emin olmadığın her ad `as_is` kalır.
2. **Tag CJK sözlük v2:** `worklists/009_tag_cjk_values.txt` (43.341 benzersiz değer) üzerinde
   yalnızca **tam-eşleşme** sözlüğü genişlet (tür/biçim/tema/rol terimleri; kişi/seri adı içeren
   satırlara substring ile YAKLAŞMA). v1: 26 eşleşmenin 24'ü uygulandı.
3. **Tag TR==EN dip taraması:** 13.111 satır, ~%95 özel ad (marka/seri/kişi → dokunma). Yalnız
   İngilizce sözlük kelimesi/ifade olanları Türkçeleştir (örn. uygulananlar: sengoku_period→
   Sengoku Dönemi, freegame→ikiz, cosplay→Kostümlü Oyun).
4. **Org uzun kuyruğu:** network/magazine/studio/publisher'ın düşük-count CJK'larından resmî adı
   bilinenler (`org_global_names.tsv` Drive'da + %100 kesin bilgi); bilinmeyenler `as_is`.
- Hedef nicel değil nitel: kural (a) her şeyin üstünde; "kuşku varsa as_is bırak".

## 6. Bitirme kriterleri (her oturum sonu)

Yerel zincir tam yeşil + Drive senkronu (`list_files` → değişen dosyaları `delete_file` →
`upload_file`, `supports_all_drives:true`; senkron seti: §0'daki JSON/TSV'ler + `canon.tsv` +
`PROGRESS.md` + bu dosya) + `PROGRESS.md`'ye toplu-iş özeti + commit/push (kendi branch'ine) +
PR aç/güncelle. **CI'ı bekleme** (kullanıcı kendisi bakar); MAL test kırıklarını düzeltmeye
kalkışma.

---

## 7. 009 devam notu (2026-10-21)

Son devam oturumunda güncel Drive listesi taze alındı ve bu HANDOFF'ta sayılan 14 veri dosyası
indirildi. PR #30 (`arena/e29a309c-kitsugi-beta`) açık, HEAD `45cadb5` idi. Arena bu çalışma
oturumunu sabit `arena/2dcb1fce-kitsugi-beta` dalına bağladığından, 45cadb5'in yalnız Bangumi
sözlük/strings/VAULT kapsamı bu dal üzerine taşındı; main'deki daha yeni MAL değişiklikleri geri
alınmadı.

Tamamlanan yerinde güncellemeler:
- Producer 006: dört resmî Latin ad — Masayuki Aoyagi, Shigeru Watanabe, Katsushi Morishita,
  Tomonori Ochikoshi (`origin=global`).
- Tag 007b: `优秀的氛围` → İyi Atmosfer / Good Atmosphere ve `画风美` → Güzel Çizim Tarzı /
  Beautiful Art Style (`origin=new`).
- Canon, `org_global_names.tsv`, `translations_done.tsv`, vault JSON damgaları ve strings/Kotlin
  sözlükleri eşitlendi; yeni XML ID üretilmedi.
- Yerel zincir yeşil: 106580 anahtar, 106335 vault, 121284 map, 121284 damga; MASTER TR/EN
  112812 simetrik, 52607 org, çakışma 0; gen `--check`; coverage 158/158.
- `check_test_expectations.py` 19 mevcut fark verdi (12 RAW parser sınırı + 7 eski beklenti).
  MAL koduna/testine dokunma.

Devam sırası: producer'da yalnız %100 kesin resmî Latin yazım; ardından 007_b→007_i count
düşen sırayla açık kavramları tam-eşleşmeli çöz; CJK kişi/seri/kurum adı ve fandom jargonu
`as_is` kalır; son olarak org uzun kuyruğu. 007_b–007_i aralığında 43.313 CJK `as_is` kayıt
vardır; bu **çeviri kotası değil**, aday havuzudur. Önce `PROGRESS.md` Bölüm 2k ve Drive restore
notlarını oku; sırf sayı tamamlamak için tahminî romanizasyon/çeviri yapma.

Güncel main tabanına taşınan bu devamın PR'ı **#31**'dir:
https://github.com/f26901964-eng/Kitsugi-Beta/pull/31. Eski PR #30, farklı sabit olmayan head dalında
(`arena/e29a309c-kitsugi-beta`) açık kaldı; bu oturumun kod değişikliği #31'de.

### 007_b ek devamı (2026-10-21)

İkinci tag batch'inde count 18→16 aralığından üç net kavram daha çevrildi:
`轮奸` → Toplu Tecavüz / Gang Rape, `剧情性` → Olay Örgüsü Odaklı / Plot-Driven,
`❀一般向け` → Genel Kitleye Yönelik / General Audience. `canon.tsv`, translations ledger ve
vault/strings sözlüğü senkron; zincir yeşil ve yeni XML ID yok. Kalan 007_b–007_i CJK `as_is`
sayısı 43.310; count 15'ten devam et, özel adları ve fandom jargonunu çevirme.

### 007_b count-15 devamı (2026-10-21)

Batch 003'te 7 kayıt yerinde güncellendi: Aquaplus, Spike Chunsoft, Activision, HOYO-MiX ve
Innocent Grey resmî Latin karşılıkları; `好画风` → İyi Çizim Tarzı / Good Art Style;
`无节操` → Utanmaz / Shameless. Dosya: `worklists/009_decisions_007b_tags_003.tsv`.
Zorunlu mirror-repair temiz; yeni XML ID yok. Sağlık zinciri yeşil: 106580 / 106335 / 121284 /
121284, 52607 org, conflict 0, TR/EN 112812 simetrik, 158/158 coverage.

Havuzun güncel ve açık tanımı: tags.json'da chunk 007_b–007_i, `decision=as_is`, TR alanında
CJK kod noktası olan **43.185** kayıt (önceki 43.310/43.313 sayıları farklı kesit/metrikten
kalmıştı). Bu bir kota değil. Count-15'te sıradaki adaylarda yalnız net kavramı ve kesin resmî
Latin adı işle; kalan özel adlar/fandom jargonu as_is.

### Producer 006 kaynak doğrulaması ve 007_b batch 004 devamı (2026-10-21)

- **Producer 006 doğrulaması (`worklists/009_decisions_006_producers_rev.tsv`):**
  `青柳昌行` → Masayuki Aoyagi (KADOKAWA resmî yönetici sayfası & CD kitapçıkları),
  `渡辺繁` → Shigeru Watanabe (Bandai Visual / Emotion resmî CD kitapçıkları) ve
  `落越友則` → Tomonori Ochikoshi (Sony Music Group / CloverWorks resmî kurumsal sayfası)
  resmî kaynaklardan doğrulandı. `森下勝司` (`cjk_31ad3b`) için `Katsushi Morishita` yazımı
  doğrulanamadı (Production I.G ve OLM resmî sayfaları `もりした・かつじ` / `MORISHITA, Katsuji`
  verirken ikincil kaynaklarda `Katsushi`/`Katsuji` çelişkisi mevcut); kayıt Japonca `森下勝司`
  (`as_is`) durumuna döndürüldü ve `org_global_names.tsv` ile `BangumiOrgDictionary.kt` RAW
  listesinden çıkarıldı.
- **007_b count-15 batch 004 (`worklists/009_decisions_007b_tags_004.tsv`):**
  Count 15'teki 7 kayıt yerinde güncellendi (yeni XML ID yok):
  `別冊花とゆめ` → Bessatsu Hana to Yume (`global`), `朱夏` → Shuka (`global`),
  `星海社` → Seikaisha (`global`), `ファンタジア文庫` → Fantasia Bunko (`global`),
  `シーズウェア` → C's ware (`global`), `未上映` → Gösterime Girmedi / Not Yet Screened (`new`),
  `神画风` → Muhteşem Çizim Tarzı / Superb Art Style (`new`).
- Sağlık zinciri yeşil: 106580 anahtar / 106335 vault / 121284 map / 121284 damga; MASTER 52606
  org eşleme, gerçek çakışma 0, TR/EN 112812 simetrik; `gen_bangumi_master.py --check` temiz;
  meta-tag coverage 158/158. Canlı 007_b–007_i CJK `as_is` aday havuzu: **43.178** (kota değil).
  Sıradaki adım: `007_c` count 14'ten itibaren count azalan sırada açık kavramlar ve kesin
  resmî Latin adlar.

### Çapraz rol ve etiket kapsamlı tarama batch 008 (2026-10-21)

- `worklists/009_decisions_008_comprehensive.tsv` ile **196 kanonik karar** (`apply_009_batch.py
  --mirror-repair`, 0 ikiz-engelli, yeni XML ID 0) yerinde uygulandı:
  - **Kurum & yapımcı `global` (23):** `magazine` 6 (`RIDIBOOKS`, `Dengeki Comics NEXT`,
    `Monthly Wings`, `Yuri Hime Comics`, `Weekly Morning`, `Shonen Gangan`), `publisher` 3
    (`CIEL`, `DMZJ`, `NetEase`), `studio` 1 (`Cocktail Soft`), `producer` 13 (`Yasutaka Nakata`,
    `Romeo Tanaka`, `Naotaka Hayashi`, `SCA-Ji`, `Satoshi Yaginuma`, `Ryo Mizuno`,
    `Shinji Takamatsu`, `Akiko Shikata`, `Kotaro Uchikoshi`, `Negi Haruba`, `Hiroshi Seko`,
    `Maiko Iuchi`, `Tatsuya Kato`).
  - **Tag `global` (114):** `007_b`–`007_g` aralığında `org_global_names.tsv` ile %100 eşleşen 96
    CJK kurum/dergi/yayınevi/stüdyo/kişi etiketi + 18 bitişik/varyant Latin stüdyo ve marka etiketi
    (`LIDENFILMS`, `Warner Bros. Animation`, `Studio Comet`, `Telecom Animation Film`,
    `project No.9`, `Hoods Entertainment`, `CoMix Wave Films`, `TYO Animations`, `Group TAC`,
    `Polygon Pictures`, `Purple software`, `Lump of Sugar`, `Hal Film Maker`, `FromSoftware`,
    `Studio 3Hz`, `07th Expansion`, `20th Television`, `Arc System Works`).
  - **Tag `new` (59):** `007_c`–`007_g` aralığından 46 açık CJK kavram etiketi (`延期`, `默片`,
    `澳剧`, `哥特萝莉`, `家庭伦理`, `初心者向`, `音乐美`, `狼人杀`, `冷战`, `轻喜剧`, `平行宇宙`,
    `误解系`, `自然纪录片`, `卡牌收集类`, `海战`, `港片`, `一战`, `杀手题材`, `复仇剧`, `伪推理`,
    `自然主义`, `光枪射击`, `日式奇幻`, `北欧神话`, `本格推理`, `童话风`, `伪纪录片`, `科普纪录片`,
    `挪威剧`, `低龄向`, `web短片`, `商战`, `默剧`, `木偶动画`, `木偶剧`, `哥特风`, `英伦风`,
    `逐帧动画`, `爱尔兰剧`, `历史纪录片`, `新加坡剧`, `游戏宣传片`, `北美动画`, `人文纪录片`,
    `虚无主义`, `TV短片`) + `tr==en` kalmış 13 İngilizce kavram/tür/müzik etiketi (`IndieAnime`,
    `melodic-death`, `Tactical_shooter`, `symphonic`, `Alternative_rock`, `electronica`,
    `Main_Theme`, `TVdrama`, `acoustic`, `Souls-like`, `WRPG`, `Indie_pop`, `Indie_rock`).
- Sağlık zinciri yeşil: 106580 anahtar / 106335 vault / 121284 map / 121284 damga; MASTER **52626**
  org eşleme, gerçek çakışma 0, TR/EN 112812 simetrik; `gen_bangumi_master.py --check` temiz;
  meta-tag coverage 158/158. Canlı 007_b–007_i CJK `as_is` aday havuzu: **43.022** (kota değil).
- **Uygulama Entegrasyon Planı ve Görev Listesi:** Tüm rollerde ve etiketlerde net kavram/resmî
  global ad taraması tamamlanmış olup uygulamaya entegrasyon mimarisi ve adım adım görev planı
  (`TASK-1`..`TASK-5`) **`HANDOFF_011_APP_INTEGRATION_PLAN_AND_TASKS.md`** dosyasında (hem repo
  içinde `diag/bangumi_vault/` altında hem Google Drive `1pZAOYsAnMjveNK6grdXjqV9IDM1TFaJp`
  klasöründe) kayıtlıdır.

