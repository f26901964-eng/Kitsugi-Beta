# Bangumi MASTER — eksik fazların tamamlanması ve entegrasyon (2026-10-11)

Dal: `arena/ca399573-kitsugi-beta` · taban: `9cc4eed` (v2.4.245-beta)
Durum: **kod tamam, doğrulayıcı yeşil, JVM koşumu CI'ya devredildi**

> Not: Önceki pencerenin (`arena/dadf086f-kitsugi-beta`) çalışması hiç push edilmemişti;
> bu repoda `diag/bangumi_master/`, `BangumiOrgDictionary` ve MASTER bloğu **yoktu**.
> Aşağıdaki her şey bu dalda sıfırdan kuruldu.

---

## 1) Ne yapıldı

### Altyapı — `diag/bangumi_master/`

| Dosya | Görev |
|---|---|
| `seed_orgs_from_map.py` | `orgs.tsv`'yi **repodaki gerçek veriden** üretir (`studioLatinNameMap`, 117 eşleme). Uydurma kurum adı yok. |
| `orgs.tsv` | **116 kurum kaydı / 146 yazım** (93'ü `studioLatinNameMap`'ten, 23'ü Ek 1) |
| `tags.tsv` | MASTER §7.4 boşlukları: 2 yeni grup + 3 gruba varyant |
| `gen_bangumi_master.py` | Üreteç (idempotent). `--check` ile güncellik denetimi. |
| `verify_bangumi_master.py` | 8 kontrol sınıfı (K1–K8), 13 kontrol. |
| `check_test_expectations.py` | JUnit beklentilerinin JVM'siz veri ön denetimi (32 beklenti). |
| `harness/compile_check.sh` | Bağımsız `kotlinc` derleme denetimi (Android SDK istemez). |

### Kod

| Dosya | Değişiklik |
|---|---|
| `utils/BangumiOrgDictionary.kt` | **yeni** · 116 kayıt, 5 rol, `entryFor()` + unvan eki soyma |
| `utils/BangumiTagDictionary.kt` | +2 grup (`after_story`, `chinese_speaking`), +3 varyant, `allEntries()`, `female_harem` TR düzeltmesi |
| `utils/KitsugiTranslations.kt` | `toLocalizedOrgName()`, `toOrgMonogram()`, `bangumiOrgResourceCache` |
| `values/strings.xml` · `values-en/strings.xml` | MASTER bloğu: 116 `bangumi_org_*` + 2 yeni `bangumi_tag_*` · iki dosyada 947/947 simetrik |
| `KitsugiDetailComponents.kt` | stüdyo/yapımcı/yayıncı ağ **çipleri** yerelleşti |
| `StudioDetailComponents.kt` | hero başlığı, sol panel başlığı, **2 monogram**, **2 erişilebilirlik etiketi**, **2 paylaşım metni** |
| `StudioDetailPage.kt` | **TV/üst çubuk başlığı**, galeri başlığı, paylaşım metni |
| `test/.../BangumiMasterDictionaryTest.kt` | **yeni** · 12 test, JVM-only |
| `.github/workflows/bangumi-master.yml` | **yeni** · doğrulayıcı + kotlinc + JUnit |

---

## 2) Bu turda yakalanan ve düzeltilen hatalar

| # | Hata | Nasıl yakalandı |
|---|---|---|
| 1 | **Üretecim** yeni `g(...)` bloğunu `instrumental`'ın ardına **virgülsüz** ekledi → sözlük derlenmezdi. Sebep: kapanış parantezinden önceki `\n` soyulunca `tail` boş kalıyordu. | Üretilen dosyayı satır satır okuyarak. Düzeltme: `head = text[:close].rstrip()` → son **anlamlı** karaktere bakılır. |
| 2 | **829 `bangumi_tag_*` anahtarı iki dosyada da ikişer kez** yazıldı → aapt2 mükerrer kaynak adıyla derlemeyi reddederdi. `git show HEAD` ile kanıtlandı: HEAD'de 0 mükerrer, yani hatayı ben ürettim. | Yeni eklenen **K8 mükerrer kaynak adı** kontrolü. Düzeltme: üreteç artık blok dışında zaten var olan anahtarları atlıyor. |
| 3 | `female_harem` TR değeri `reverse_harem` ile aynıydı (`Ters Harem`) → gerçek **gölgelenme**; ekranda `Female Harem` etiketi "Ters Harem" görünüyordu. | **K3 gölgelenme** kontrolü. Düzeltme: TR → `Kadın Haremi`. |
| 4 | `Asahi Production` (亜細亜堂, stüdyo) `asahi` ipucuyla **yayıncı ağ** sayılmıştı; `Dengeki Bunko` dergi sanılmıştı. | Üretilen `orgs.tsv`'nin elle denetimi. Düzeltme: `ROLE_OVERRIDES`. |
| 5 | `8bit` → kaynak adı `_8bit` (rakamla başlayan ad geçersiz), `J.C.Staff` → `j_c_staff` (çift alt çizgi aapt2'de geçersiz). | **K7 kaynak adı** kontrolü + `slugify` düzeltmesi. |
| 6 | Doğrulayıcının virgül tarayıcısı `depth=0` ile başlıyordu → `)`'de 0'a düşüp kırılmıyordu, `tv`/`web` için yanlış pozitif üretiyordu. | 831 yerine 824 grup okuması + saçma K3 çakışmaları. Düzeltme: `depth=1`. |
| 7 | Okuyucu satıra yayılmış 7 `g(...)` grubunu kaçırıyordu (831 → 824). | Üreteç/`doğrulayıcı` sayı uyuşmazlığı. Düzeltme: başlık + kuyruk ayrı taranıyor. |

**Repoda zaten var olan ve bu turla düzeltilenler:** `催淚` (geleneksel) eksikti, `京都` etiketi eksikti, `続編` eksikti, `after_story` grubu hiç yoktu, `华语`/`華語` yoktu.

---

## 3) Doğrulama — ne koşuldu, ne koşulamadı

### Koşulanlar (bu sandbox'ta, gerçek çıktılarıyla)

```
$ python3 diag/bangumi_master/gen_bangumi_master.py --check   → exit 0 · "tamam: her şey güncel"
$ python3 diag/bangumi_master/verify_bangumi_master.py        → exit 0 · "TÜM KONTROLLER GEÇTİ (13 kontrol)"
$ python3 diag/bangumi_master/check_test_expectations.py      → exit 0 · 32 beklenti uyumlu
$ python3 -m py_compile diag/bangumi_master/*.py              → 4 betik OK
$ bash -n diag/bangumi_master/harness/compile_check.sh        → OK
$ pyyaml ile bangumi-master.yml                               → 2 job / 10 adım OK
```

### Koşulamayanlar — **dürüst boşluk**

**`./gradlew test` ve `kotlinc` bu sandbox'ta çalıştırılamadı.** Nedeni izin listesi:

| Kaynak | Sonuç |
|---|---|
| `java -version` | `command not found` |
| `apt-get update` | `deb.debian.org` → Connection failed |
| GitHub release varlıkları | `release-assets.githubusercontent.com` → `SSL_ERROR_SYSCALL` |
| `drive.google.com` / `docs.google.com` | `SSL_ERROR_SYSCALL` |
| `pypi.org` | ✅ `http=200` (ama JVM içeren wheel yok) |

Yalnızca `github.com`, `registry.npmjs.org`, `pypi.org` açık. Temurin JDK ve kotlinc
GitHub **release varlığı** olduğu için CDN'e yönlendiriyor ve o CDN kapalı.

➡️ **Kotlin kodunun derlendiği ve testlerin geçtiği bu sandbox'ta kanıtlanmadı.**
Gerçek koşum CI'ya devredildi (`.github/workflows/bangumi-master.yml`): push sonrası
`verify` + `compile-and-test` job'ları kotlinc derlemesini ve
`BangumiMasterDictionaryTest`'i koşturur. Riski azaltmak için 32 test beklentisi
Python simülasyonuyla veriye karşı önceden doğrulandı.

---

## 4) Senin MASTER verin nasıl birleşir (Drive'daki 209 KB dosya)

Üreteç veri kaynağı olarak **iki TSV** okur; MASTER `.md`'yi buraya getirmeye gerek yok.
Drive'daki zip'ten (veya MASTER'dan) kendi tablolarını bu iki dosyaya dökmen yeterli:

```
diag/bangumi_master/orgs.tsv    role \t key \t tr \t en \t varyant1|varyant2|...
diag/bangumi_master/tags.tsv    key  \t tr  \t en \t varyant1|varyant2|...
```

Kurallar:
- Rol: `studio` / `producer` / `network` / `publisher` / `magazine`.
- Kaynakta doğrulanmamış isim → `pending_studio` gibi yaz; üreteç **atlar**, dil dosyasına girmez.
  (7 ⚠ isim için: `Studio Gohan`, `Studio Nagomu`, `Mouse`, `Revootoot`, `Signpost`,
  `Staple Entertainment`, `Nippon Eiga-sha`.)
- `key` `[a-z_][a-z0-9_]*` olmalı; marka adlarında `tr` = `en`.
- `tags.tsv`'de **mevcut** anahtarı yazarsan yalnızca eksik varyantlar eklenir, elle yazdığın
  TR/EN değerine dokunulmaz. Yeni anahtar yazarsan grup eklenir.

Sonra:

```bash
python3 diag/bangumi_master/gen_bangumi_master.py     # üret
python3 diag/bangumi_master/verify_bangumi_master.py  # denetle
```

Üreteç idempotenttir ve `--check` ile CI'da "elle düzenlenip üreteç çalıştırılmamış"
durumunu da yakalar.

---

## 5) Korunan bilinçli kararlar

- `onStudioClick` **ham adla** gider. Yerelleştirme yalnızca gösterim katmanında;
  `StudioDetailViewModel.matchesExpectedStudio` ve `StudioSourceSupport.namesMatch`
  ham adla karşılaştırmaya devam ediyor.
- **Uydurma çeviri yok**: sözlükte olmayan kurum/etiket olduğu gibi görünür; üretim
  komiteleri (`PROJECT-XXX`, `光坂高校演劇部`) sözlüğe alınmaz.
- Çip çiziminde kaynak taraması önbellekli (`bangumiOrgResourceCache` /
  `bangumiTagResourceCache`), negatif önbellek dahil.
- MASTER bloğu üretecin alanı: elle değil `orgs.tsv` / `tags.tsv` üzerinden düzenlenir.

---

## 6) Kapsam — dürüst durum (2026-10-11 ölçümü)

**"Bangumi'deki TÜM stüdyo/yapımcı/yayıncı ağ adı global adıyla görünüyor" demek doğru değil.**
Sözlükte **116 kurum** var (69 stüdyo · 20 yapımcı · 13 yayıncı ağ · 14 yayımcı); Bangumi'de
binlerce kurum kaydı bulunur. 28 kurumluk örneklemde eşleşme **28/28**, ama bu örneklem
bilinen büyük adlardan seçildi.

Kapsanmayan bir kurum adı **Çince/Japonca olduğu gibi görünür** — uydurma çeviri üretilmez
(tasarım kararı). Kapsamı genişletmenin tek yolu `orgs.tsv`'ye satır ekleyip üreteci
çalıştırmaktır; senin MASTER dosyan (133 stüdyo / 90 yapımcı / 96 ağ / 45 yayımcı) bu
listeyi büyük ölçüde doldurur.

Ayrıca `toLatinStudioName()` (117 eşleme) sözlükten bağımsız olarak zaten CJK → Latin
indirgemesi yapıyordu; yani çipler ve detay sayfası sözlükten önce de bu 117 ad için Latin
yazım gösteriyordu. Sözlüğün eklediği şey rol bilgisi, iki dilde ayrışabilen gösterim
(`Çince Konuşulan Bölge` / `Chinese-speaking Region`) ve doğrulanabilir bir veri hattıdır.
