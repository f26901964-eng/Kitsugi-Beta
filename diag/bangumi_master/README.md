# diag/bangumi_master — Bangumi şirket / etiket sözlüğü üretim hattı

Bu klasör, **Bangumi (bgm.tv)** kaynağından detay sayfalarına gelen **stüdyo / yapımcı /
yayıncı ağ / yayımcı / dergi** adları ile **etiket** adlarının **TR + EN** karşılıklarını
üreten hattı barındırır. Kaynak belge:
`BANGUMI_STUDYO_YAPIMCI_YAYINCI_ETIKET_MASTER_2026-10-11.md` (tam sürüm Drive'da; bu klasördeki
`*.tsv` dosyaları makine-okur asıl kaynaktır).

## Dosyalar

| Dosya | İçerik |
|---|---|
| `orgs.tsv` | 365 şirket satırı — 133 stüdyo · 90 yapımcı · 96 yayıncı ağ · 24 yayımcı · 21 dergi |
| `tags.tsv` | 146 etiket satırı — 78 yeni anahtar + 68 mevcut gruba etiket eklemesi |
| `gen_bangumi_master.py` | **Üretici**: TSV → Kotlin sözlükleri + `values/` & `values-en/` kayıtları + `master.json` |
| `verify_bangumi_master.py` | **Doğrulayıcı**: Kotlin arama mantığını simüle eder, gölgelenme/simetri/kapsama denetler |
| `master.json` | Üretilen makine-okur döküm (uygulama tarafından okunmaz; arşiv/denetim içindir) |
| `PLAN.md` | Uygulama planı (Faz 1-4) ve kabul kriterleri |
| `BANGUMI_STUDYO_YAPIMCI_YAYINCI_ETIKET_MASTER_2026-10-11.md` | Kaynak MASTER belgesinin repo kopyası (özet; §7.6/§8 dahil edilmedi — repo dosyaları asıl kaynaktır) |
| `harness/compile_check.sh` + `harness/Main.kt` | Gerçek `kotlinc` derleme + çalıştırma kontrolü (JUnit testinin bağımlılıksız kopyası) |

## TSV biçimi

`orgs.tsv` — `key <TAB> role <TAB> TR <TAB> EN <TAB> durum <TAB> etiketler`

* `role`: `studio` · `producer` · `network` · `publisher` · `magazine`
* `durum`: `new` (yeni kayıt) · `existing:<mevcut anahtar>` (etiketler mevcut gruba işaret eder) ·
  `pending` (**⚠ doğrulanmadı — dil dosyasına ve sözlüğe ALINMAZ**)
* `etiketler`: `;` ile ayrık CJK/Japonca varyantlar ve takma adlar

`tags.tsv` — `key <TAB> TR <TAB> EN <TAB> durum <TAB> etiketler`

* `key` daima `bangumi_tag_` önekiyle yazılır; üretici `g()` çağrısı için **kısa anahtarı** türetir.
* `durum`: `new` · `existing:<mevcut kısa anahtar>`

## Kullanım

```bash
# Üret (idempotent — ikinci çalıştırma hiçbir şey eklemez)
python3 diag/bangumi_master/gen_bangumi_master.py

# Denetle (yazmaz; gölgelenme, simetri, kapsama, boş değer)
python3 diag/bangumi_master/gen_bangumi_master.py --check
python3 diag/bangumi_master/verify_bangumi_master.py

# Gerçek derleme + çalıştırma kontrolü (kotlinc 2.x + JDK 17+ gerekir)
KOTLINC=/yol/kotlinc bash diag/bangumi_master/harness/compile_check.sh
```

> **Neden derleme kontrolü?** Python doğrulayıcı Kotlin **sözdizimini** görmez. 2026-10-11'de
> `instrumental` satırına virgül eklenmediği için (yeni blok listSon elemanın hemen ardına
> eklenmiş) sözlük derlenemiyordu ve bunu yalnızca `kotlinc` yakaladı. Üretici artık ekleme
> öncesi önceki elemanın sonuna virgülü otomatik koyar; yine de her üretim sonrası bu harness
> çalıştırılmalıdır (JUnit `BangumiMasterDictionaryTest` ile aynı kontroller).

Üretici şu dosyalara dokunur:

* `app/src/main/java/com/kitsugi/animelist/utils/BangumiTagDictionary.kt` — yeni `g(...)` grupları
  listenin sonuna, eksik etiketler mevcut grupların `labels` listesine eklenir.
* `app/src/main/java/com/kitsugi/animelist/utils/BangumiOrgDictionary.kt` — **üretilmiş dosya**,
  elle düzenlenmez (şirket etiketi → `bangumi_*` anahtarı + küresel Latin ad).
* `app/src/main/res/values/strings.xml` ve `values-en/strings.xml` — `bangumi_tag_*` bloğunun
  ardına MASTER işaretli blok eklenir; iki dosyanın anahtar kümesi **birebir simetrik** kalır.
  `values/` **TR**, `values-en/` **EN** sütununu okur. Blokta var olan bir kaydın değeri TSV'de
  değiştirilmişse üretici değeri **senkronlar** (`sync_values`) — elle düzenleme gerekmez,
  işaretçi yorumundan sonraki satırlara dokunulur.

## Politikalar (değişmez kurallar)

1. **Uydurma çeviri yok.** Marka adlarında TR = EN; karşılığı olmayan özel ad (üretim komiteleri
   `PROJECT-XXX`, `光坂高校演劇部`) olduğu gibi kalır ve sözlüğe **alınmaz**.
2. **⚠ `pending` satırlar** kaynakta doğrulanmadan dil dosyalarına girmez.
3. **İlk yazım kazanır.** `BangumiTagDictionary` bir etiketi ilk tanımlayan gruba bağlar; bu yüzden
   `verify_bangumi_master.py` gölgelenmeyi (bir etiketin başka gruba gitmesini) hata olarak bildirir.
4. **Anahtar simetrisi.** `values/` ve `values-en/` arasında anahtar farkı olamaz.
5. **Arama/dedup Latin adla yürür.** Yerelleştirme yalnızca **gösterim** katmanındadır
   (`toLocalizedOrgName()`), eşleştirme `toLatinStudioName()` + `BangumiOrgDictionary` üzerinden
   Latin/küresel adla yapılır.

## Bilinen sınırlar

* Bangumi veritabanı dump'ları bu ortamdan indirilemediği için liste; mevcut sözlük + resmî meta
  etiketler + wiki alan tanımları + elle derleme ile oluşturulmuştur. Nadir kullanıcı etiketi
  görüldükçe `tags.tsv`'ye eklenip üretici yeniden çalıştırılmalıdır.
* Aynı şirket birden çok rolde listelenebilir (`Production I.G`, `bilibili`, `白泉社` …). Sözlük
  tek anahtara bağlar; ekranda görünen ad aynı olduğu için bu durum zararsızdır.
