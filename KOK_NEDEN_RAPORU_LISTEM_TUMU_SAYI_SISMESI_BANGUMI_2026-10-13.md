# KÖK NEDEN RAPORU — Listem → "Tümü": aynı içerik ayrı kart + sayı şişkinliği (Bangumi sonrası)

**Tarih:** 2026-10-13
**Sürüm:** v2.4.273 (önceki: v2.4.272)
**Ekran:** Listem (Android + Android TV) → "Tümü" sekmesi / "Kütüphane Kaynağı Seç" sayacı
**İlgili:** `CROSS_SYNC_ALTI_KAYNAK_FIX_RAPORU_2026-10-12.md`, `KOK_NEDEN_RAPORU_HESAP_KAYNAKLI_KASMA_2026-10-11.md`

---

## 1. Kullanıcı şikâyeti

Bangumi ile çapraz veri eşitlemesi yapıldı (~800 öğe). Beklenti: altı kaynak aynı
içeriği kapsadığı için **"Tümü" ≈ 786 tekil içerik** göstermeli. Gösterilen: **1502**.

Ekran görüntüsündeki sayımlar:

| Kaynak | İçerik |
|---|---|
| AniList | 783 |
| MyAnimeList | 799 |
| Simkl | 629 |
| Kitsu | 797 |
| Shikimori | 799 |
| Bangumi | 716 |
| **Tümü** | **1502** ← olması gereken ≈ 786 |

**Kritik ipucu:** `1502 − 716 = 786 ≈ AniList (783) + birkaç tekil kayıt`. Yani Bangumi
satırlarının **tamamı** birleşik listede ayrı bir kart olarak sayılıyor; diğer beş kaynak
birbirinin içinde eriyor.

---

## 2. Kök neden (iki katman)

### 2.1 Kimlik anahtarları kaynak-uzaylı; satırda çapraz kimlik YOK

`MediaEntry.malId` alanı **satırın kendi kaynağının** kimliğini taşır (ofset penceresi:
AniList 100M, Simkl 150M, Kitsu 300M, Shikimori 400M, **Bangumi 500M**). `MediaIdentity.keys()`
bu kimlikleri uzay etiketiyle üretir:

```
Bangumi satırı  → { "Anime:bangumi:<subject_id>" }
AniList satırı  → { "Anime:mal:52991" }  (veya "Anime:anilist:<id>")
MAL satırı      → { "Anime:mal:52991" }
```

Kesişim **boş** → `sameMedia()` ID ile eşleştiremez ve başlık eşleşmesine düşer.

### 2.2 Başlık yedeği Bangumi'de çalışmaz

Bangumi liste API'si `name` / `name_cn` döndürür (`BangumiImportManager` bunu
`BangumiNameLocalizer` ile yerelleştirir): `葬送のフリーレン` / `动画世界…`. AniList/MAL
satırının `title`/`titleEnglish` alanlarında romaji/İngilizce başlık var. Normalizasyon
(noktalama+vaka) farklı alfabeleri eşitlemez → **eşleşme yok** → aynı yapım iki kart.

> Kitsu/Shikimori/Simkl satırları `titleEnglish` (romaji/İngilizce) taşıdığı için bugün
> başlıkla eriyor; Bangumi satırları taşımıyor. Bu yüzden şişkinlik tam olarak Bangumi
> sayısı kadar.

### 2.3 (İkincil, ama aynı şikâyeti üreten) Gruplama sıra bağımlı

`groupMyListEntries` açgözlü tek geçiş: satır, aday gruplardan **ilk** uyanın içine konur ve
**gruplar birbirine asla eklenmez**. Kayıtların geliş sırası (içe aktarma sırası = DB `id`
sırası) iki ayrı grubun açılmasına yol açabiliyor; o iki grup hiç birleşmediği için aynı
içerik yine iki kart. Simülasyonda ölçüldi:

| Sıra | Eski davranış | Sadece çapraz kimlik | Çapraz kimlik + konsolidasyon |
|---|---|---|---|
| iç içe | 1572 | 786 | 786 |
| ters sıralı | 1572 | **1572** ← sadece köprü yetmez | 786 |
| kaynak blokları | 1572 | 786 | 786 |
| karışık | 1572 | 921 | 786 |

---

## 3. Düzeltme

### 3.1 `MyListIdentityCrosswalk` (yeni) — `data/local/MyListIdentityCrosswalk.kt`

Çapraz eşitlemenin **zaten çözüp kalıcı yazdığı** iki kaynaktan köprü okur; **ağa çıkmaz**:

1. `CrossProviderIdentityCache` (`cross_provider_identity_v1`) → satırın cache anahtarı
   (`CrossProviderIds.cacheKey(...)`) ile altı kimlik birden.
2. `BangumiLocalMappingCache` (`bangumi_id_mapping`, `bgm_a_<malId> → subject_id`) →
   **tersine çevrilir** (`subject_id → malId`). Bangumi'nin resmî MAL eşleme ucu olmadığı için
   birleşik listeyi kapatan tek güvenli köprü budur.

Kurallar:

- **Belirsiz eşleme kullanılmaz:** aynı条目 iki farklı MAL kaydına bağlıysa o çift atılır.
- **Sahip olunan uzay ezilmez:** satır kendi `malId` alanında zaten bir MAL ID taşıyorsa,
  önbellekten gelen ikinci `mal:` değeri eklenmez (aynı uzayda iki değer `conflictingIdentityKeys`
  üzerinden tüm birleşmeyi bloklardı).
- **Satır değiştirilmez:** ek anahtarlar yalnızca eşitleme anında verilir → platform sekmeleri,
  detay sayfaları ve dış API yazmaları kendi kaynağının kimliğini kullanmaya devam eder.
- Film/dizi satırlarında MAL ekseni olmadığından eksen TMDB/Simkl üzerinden kurulur.

### 3.2 `groupMyListEntries(entries, identityExtras)` — `MyListLibraryGrouping.kt`

- Kimlik anahtarları **satır başına bir kez** hesaplanır (eskiden eşitleme döngüsünde tekrar
  tekrar `MediaIdentity.keys()` çağrılıyordu → binlerce satırda bariz maliyet).
- Aday grup bulma, üye kontrolü ve çakışma denetimi aynı anahtar kümeleriyle yapılır.
- **2. tur (konsolidasyon):** aday anahtarı çakışan gruplar union-find ile birleştirilir.
  Birleşme, tek bir satırın gruba katılmasından daha gevşek değildir: **tüm** üye çiftleri
  çakışmasız olmalı ve **en az bir** çift `sameMedia` olmalı. Sonuç artık sıra bağımsız.

### 3.3 `MediaIdentity` — köprü anahtarlarıyla aynı sözleşme

- `keys(entry, extras)` ek anahtar kümesini birleştirir.
- `conflictingIdentityKeys(typeA, typeB, keysA, keysB)` ve `sameMedia(a, b, keysA, keysB, allowTitle)`
  yeni overload'lar; mevcut imzalar (ve tüm çağıranlar) aynen durur.
- **Sertleştirme:** bir uzay artık tek değere indirgenmiyor (`associateBy` son değeri tutuyordu).
  Aynı uzayda birden fazla ID bulunabilen (bayat önbellek + kendi ID'si) satırlarda **tek bir
  ortak değer bile** çakışmayı kaldırır.
- `titleMatch(...)` ayrıldı; karar ağacı birebir eski davranışı korur.

### 3.4 `CrossProviderIdentity.resolve` — köprüyü iki yönde de öğren

MAL↔Bangumi çifti bundan sonra **Bangumi satırından MAL'e inilirken de** kalıcı yazılır.
İçe aktarılmış (henüz eşitlenmemiş) Bangumi kayıtları böylece bir sonraki eşitlemede
ağ harcanmadan kapanır.

### 3.5 `MyListScreen` bağlantısı

`entriesAfterAdultFilter` değiştiğinde köprü IO iş parçacığında kurulur
(`MyListIdentityCrosswalk.build`) ve `groupMyListEntries`'e verilir. Harita yeniden
hesaplanırken önceki değer korunduğu için sayı yalnızca **ilk** açılışta bir kare
bekleyebilir; sonraki güncellemelerde zıplama olmaz.

---

## 4. Doğrulama

- **Simülasyon:** `MediaIdentity` + `groupMyListEntries` Python'a birebir port edildi;
  786 tekil yapım × 6 kaynak (4716 satır) → **öncesi 1572 / sonrası 786**, beş farklı
  sıralamanın hepsinde. Kullanıcının gerçek sayılarıyla: 786 + 716 = **1502** ✔
- **Güvenlik senaryoları:** yanlış/bayat köprü iki farklı yapımı birleştiremiyor; belirsiz
  `subject_id → malId` çifti atılıyor; sahip olunan `mal:` uzayı ezilmiyor; tür farkı
  (Anime ≠ Manga) birleşmeyi engellemeye devam ediyor.
- **Birim testleri (JVM):**
  `MyListLibraryGroupingTest` (+2 test), `MyListIdentityCrosswalkTest` (yeni, 7 test),
  `MediaIdentityDisambiguationTest` (+2 test).
  ⚠️ Bu sandbox'da JDK/Android SDK yok → `gradlew test` çalıştırılamadı; derleme ve cihaz
  testi kullanıcıda.

## 5. Kullanıcı için sonuç ve kalan sınır

- "Tümü" sayacı ve kartlar tekilleşir; rozetler (her iki kaynağın logosu) bir kartta toplanır.
- **Hiçbir kayıt silinmez veya değiştirilmez** — yalnızca görüntülemedeki birleşim düzelir;
  ilerleme/puan hâlâ kendi platformuna yazar.
- Kalıcı sınır: eşitlemesi **hiç yapılmamış** ve başlığı da diğer kaynaklarla birebir eşmeyen
  Bangumi kayıtları için kanonik kimlik ağa çıkmadan bulunamaz. Bunlar ayrı kart kalır; bir
  tur çapraz eşitleme çalıştırıldığında (veya `CrossProviderIdentity` o satırı çözdüğünde)
  köprü yazılır ve bir dahaki açılışta birleşir.
- Sonraki adım olarak aynı köprü `MediaEntryRepository.withCrossSourceAdultFlags` (+18 yayılımı)
  ve profil tarafındaki `KitsugiUserMediaListScreen` birleşimlerine de verilebilir — bugün
  onlar da yalnızca `MediaIdentity.keys()` tabanlı.
