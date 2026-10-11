# PLAN/TASK — Bangumi kurum / etiket / rol adlarının TR+EN yerelleştirmesi ve küresel ad çözümleme

**Tarih:** 2026-10-11 · **Dal:** `arena/ca399573-kitsugi-beta` · **Taban:** `9cc4eed` (v2.4.245-beta)
**Durum:** KOD TAMAM · doğrulayıcı 17/17 · **Kotlin derlemesi CI'da koşulacak (bu ortamda JVM yok)**

---

## 1) Hedef

Bangumi (`api.bgm.tv`) kaynağından detay sayfalarına gelen **stüdyo, yapımcı, yayıncı ağ,
yayımcı/dergi ve etiket** adları Çince/Japonca gelir. Bunların hem **çiplerde** hem
**kurum ayrıntı sayfalarında** küresel adlarıyla, hem **Türkçe hem İngilizce** arayüzde
doğru görünmesi; bilinmeyenin **uydurulmadan** olduğu gibi kalması.

## 2) İki katmanlı çözüm (neden tek başına sözlük yetmiyor)

| Katman | Ne yapar | Kapsam |
|---|---|---|
| **A) Sözlük (statik)** | `BangumiOrgDictionary` / `BangumiTagDictionary` / `BangumiRoleDictionary` + `bangumi_org_*` / `bangumi_tag_*` / `bangumi_role_*` dil dosyası kayıtları | Bilinen adlar; TR ≠ EN ayrışması burada |
| **B) Çözücü (çalışma zamanı)** | `BangumiOrgResolver` — infobox serbest metnini `POST /v0/search/persons` (`type=2` 公司) ile çözer, kurumun **kendi** `name` alanını küresel ad olarak alır | Sözlükte **olmayan** kurumlar da çözülür |

**B katmanının gerekçesi:** Bangumi'de kurumlar ayrı kayıt türü değil, 人物 kayıtlarının
`type = 2` alt kümesidir (bkz. `KitsugiStudioClient.fetchBangumiStudioDetail`). Arama
yanıtındaki `name` özgün/küresel ad, `name_cn` Çince yerelleştirmedir. Yani tek istek,
sözlükte olmayan bir kurum için bile küresel ad verir — "tamamı" ancak böyle karşılanır.

**Etiketlerde B katmanı YOKTUR:** etiketler kullanıcı üretimi serbest metindir, karşılık
gelen bir kayıt yoktur. Etiket kapsamı yalnızca sözlükle büyür (MASTER'daki 142 yeni etiket
bu yüzden gerekli).

## 3) Bu turda yapılanlar

### Altyapı — `diag/bangumi_master/`
| Dosya | Görev |
|---|---|
| `seed_orgs_from_map.py` | `orgs.tsv`'yi repodaki gerçek `studioLatinNameMap`'ten (117 eşleme) üretir |
| `orgs.tsv` | 116 kurum · 146 yazım · `role \t key \t tr \t en \t variants` |
| `tags.tsv` | MASTER §7.4 boşlukları: 2 yeni grup + 3 gruba varyant |
| `roles.tsv` | 37 rol grubu (`BangumiRoleDictionary`'den birebir aktarıldı) |
| `gen_bangumi_master.py` | İdempotent üreteç · `--check` ile güncellik denetimi |
| `verify_bangumi_master.py` | 8 kontrol sınıfı (K1–K8) → 17 kontrol |
| `check_test_expectations.py` | JUnit beklentilerinin JVM'siz veri ön denetimi (32 beklenti) |
| `harness/compile_check.sh` | Bağımsız `kotlinc` derleme denetimi (Android SDK istemez) |

### Kod
| Dosya | Değişiklik |
|---|---|
| `utils/BangumiOrgDictionary.kt` | **yeni** · 116 kayıt, 5 rol, `entryFor()` + unvan eki soyma |
| `utils/BangumiRoleDictionary.kt` | artık **üretiliyor** + `key` alanı + dil dosyası desteği |
| `utils/BangumiTagDictionary.kt` | +2 grup, +3 varyant, `allEntries()`, `female_harem` TR düzeltmesi |
| `utils/KitsugiTranslations.kt` | `toLocalizedOrgName()`, `toOrgMonogram()`, `localizedBangumiRoleOrNull()`, iki önbellek |
| `data/remote/BangumiOrgResolver.kt` | **yeni** · çalışma zamanı küresel ad çözümleme + önbellek |
| `data/auth/BangumiApiClient.kt` | `searchPersons`'a `type` filtresi (1=kişi, 2=şirket, 3=grup) |
| `data/remote/KitsugiBangumiDetailClient.kt` | `fetchDetail` içinde çözücü adımı (süre tavanlı, ek katman) |
| `values/strings.xml` · `values-en/strings.xml` | MASTER bloğu: 116 `bangumi_org_*` + 37 `bangumi_role_*` + 2 `bangumi_tag_*` → **984/984 simetrik** |
| `KitsugiDetailComponents.kt` | stüdyo/yapımcı/yayıncı ağ **çipleri** (3 nokta) |
| `StudioDetailComponents.kt` | hero + sol panel başlığı, 2 monogram, 2 erişilebilirlik, 2 paylaşım |
| `StudioDetailPage.kt` | TV/üst çubuk başlığı, galeri başlığı, paylaşım |
| `test/.../BangumiMasterDictionaryTest.kt` | **yeni** · 12 test (JVM-only) |
| `.github/workflows/bangumi-master.yml` | **yeni** · doğrulayıcı + `--check` + kotlinc + JUnit |

## 4) Bu turda yakalanan hatalar

| # | Hata | Nasıl yakalandı |
|---|---|---|
| 1 | Üreteç yeni `g(...)` bloğunu `instrumental`'ın ardına **virgülsüz** ekledi → derlenmezdi | Üretilen dosyanın satır denetimi |
| 2 | **829 `bangumi_tag_*` mükerrer** yazıldı → aapt2 reddederdi (`git show HEAD` ile kanıt: HEAD'de 0) | Yeni **K8** kontrolü |
| 3 | `female_harem` TR = `reverse_harem` TR (`Ters Harem`) → gölgelenme | **K3** kontrolü |
| 4 | `Asahi Production` (亜細亜堂) yanlışlıkla yayıncı ağ sayıldı | `orgs.tsv` elle denetimi |
| 5 | `_8bit` (rakamla başlıyor) ve `j_c_staff` (çift alt çizgi) geçersiz Android kaynak adı | **K7** kontrolü |
| 6 | Doğrulayıcı virgül tarayıcısı `depth=0` ile başlıyordu → yanlış pozitif | 831 yerine 824 okuma |
| 7 | Satıra yayılmış 7 `g(...)` grubu okunamıyordu | sayı uyuşmazlığı |
| 8 | `BangumiRoleDictionary`'de **dil dosyası mekanizması hiç yoktu** (0 `bangumi_role_*`) | `grep -c` denetimi |

## 5) Kalan iş (sırayla)

- [ ] **CI doğrulaması** — push sonrası `android-build.yml` (`:app:assembleFossDebug`) ve
      `bangumi-master.yml` (kotlinc + `BangumiMasterDictionaryTest`). Bu ortamda JVM/kotlinc
      kurulamadığı için **Kotlin kodunun derlendiği henüz kanıtlanmadı.**
- [ ] **Cihazda `type=2` filtresi** — `bgm.tv` bu ortamdan erişilemiyor; `searchPersons`'ın
      `filter.type` parametresini kabul ettiği **canlı doğrulanmadı**. Dayanak: `BangumiEntity.type`
      alanı zaten "1=个人, 2=公司, 3=组合" olarak belgelenip parse ediliyor. Filtre reddedilirse
      kod çökmez, yalnızca filtre uygulanmamış olur (`namesMatch` doğrulaması devrede kalır).
      Test senaryoları: MAPPA yapımı, Clannad ~After Story~, Kyoto Animation stüdyo sayfası, TR + EN.
- [ ] **MASTER içe aktarma** — Drive'daki `BANGUMI_..._MASTER_2026-10-11.md` (135 stüdyo ·
      90 yapımcı · 96 ağ · 45 yayımcı · 142 etiket). 140 etiketlik örneklemde **101'i hâlâ eksik.**
      Yöntem: `.md` tablolarını `orgs.tsv` / `tags.tsv`'ye döken içe aktarıcı → üreteç → doğrulayıcı.
- [ ] **Anahtar öneki farkı (bilinçli sapma)** — MASTER `bangumi_studio_*` / `bangumi_producer_*` /
      `bangumi_network_*` / `bangumi_publisher_*` öngörüyor; uygulama `bangumi_org_<key>` +
      `BangumiOrgRole` kullanıyor (rol Kotlin enum'unda, önekte değil). İşlevsel olarak eşdeğer;
      MASTER öneklerine geçilmek istenirse üreteçte tek satırlık değişiklik.
- [ ] **Karakter doğum yeri** — `KitsugiBangumiCreditsClient.kt:220` ham `出生地` koyuyor;
      Shikimori aynı alanı çeviriyor (`KitsugiShikimoriClient.kt:1359`). Yer adı sözlüğü gerekir.
- [ ] **`platform` sözlüğü yok** — `剧场版` / `电视剧` yalnızca film/dizi kontrolünde kullanılıyor.
- [ ] **7 ⚠ pending isim** — `pending_studio` vb. rolle `orgs.tsv`'ye yazılır, üreteç atlar:
      Studio Gohan · Studio Nagomu · Mouse · Revootoot · Signpost · Staple Entertainment · Nippon Eiga-sha

## 6) Korunan kararlar

- `onStudioClick` ve kimlik doğrulama **ham/Latin adla** yürür (`matchesExpectedStudio`,
  `namesMatch`); yerelleştirme yalnızca gösterim katmanındadır.
- **Uydurma çeviri yok:** sözlükte olmayan kurum/etiket olduğu gibi görünür; üretim
  komiteleri (`PROJECT-XXX`, `光坂高校演劇部`) sözlüğe alınmaz.
- Çözücü **ek katmandır:** 2,5 sn tavan, negatif önbellek, hata/çözüm yoksa ad değişmez.
- MASTER bloğu üretecin alanı: elle değil `orgs.tsv` / `tags.tsv` / `roles.tsv` üzerinden.

## 7) Komutlar

```bash
python3 diag/bangumi_master/gen_bangumi_master.py          # üret (idempotent)
python3 diag/bangumi_master/gen_bangumi_master.py --check  # yazmadan denetle
python3 diag/bangumi_master/verify_bangumi_master.py       # 17 kontrol
python3 diag/bangumi_master/check_test_expectations.py     # JUnit beklenti ön denetimi
KOTLINC=/yol/kotlinc bash diag/bangumi_master/harness/compile_check.sh
./gradlew :app:testFossDebugUnitTest --tests 'com.kitsugi.animelist.utils.BangumiMasterDictionaryTest'
```
