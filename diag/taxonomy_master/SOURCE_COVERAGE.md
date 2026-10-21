# Kaynak Kapsama Raporu — tür / etiket / kurum

**Sürüm:** v2.4.338 · **Tarih:** 2026-10-21 · **Doğrulayan:** `scripts/audit_taxonomy_sources.py --strict`

Bu rapor, desteklenen **yedi detay kaynağının** her biri için hangi alanların okunduğunu,
çapraz toplayıcının hangi **altı kaynağı** paralel çağırdığını ve hangi tavanların geçerli
olduğunu gösterir. Denetim kodu tarar: bir alan veya tavan sessizce düşerse CI kırılır.

---

## 1) Kapsama matrisi

| Kaynak | Tür | Etiket | Stüdyo | Yapımcı | Kanal | Yayıncı | Dergi |
|---|---|---|---|---|---|---|---|
| **AniList** | `genres` | `tags` (rank + spoiler) | `studios` (`isMain` dahil, hepsi) | — | — | — | — |
| **MAL/Jikan** | `genres`, `explicit_genres` | `themes`, `demographics` | `studios` | `producers`, `licensors` (`LICENSOR`) | — | `publishers` (manga) | `serializations` |
| **Bangumi** | 类型 (meta etiketler) | `tags` (**400'e kadar**) | 动画制作 · アニメーション制作 | 製作 · 製作委員会 · 制作協力 · 音楽制作 · レーベル · 配給 | 播放电视台 · 放送局 | 出版社 | 连载杂志 · 掲載誌 |
| **Shikimori** | `genres` (`kind=genre`) | `kind=theme` / `demographic` | `studios` | — | — | — | — |
| **Kitsu** | `categories` | `categories` | `animeProductions` (`role=studio`) | `role=producer` / `licensor` | `role=network` | `role=publisher` | `role=magazine` |
| **Simkl** | `genres` | `genres` | — *(API vermiyor)* | — | — | — | — |
| **TMDB** | `genres` | `keywords` | `production_companies[0]` | `production_companies[1…]` | `networks` | — | — |

**Neden Simkl'de kurum yok:** Simkl API'si (`/{type}/{id}?extended=full`) yalnızca `genres`,
`ids`, `ratings` gibi alanlar döndürür; stüdyo/yapımcı alanı **yayınlamaz**. Simkl'den açılan
sayfalar bu açığı çapraz toplayıcıdan (MAL + AniList + Shikimori + Kitsu + Bangumi + TMDB) kapatır.

---

## 2) Çapraz toplayıcı — **6 kaynak, tek havuz**

`KitsugiTaxonomySources.collect()` her detay açılışında altı servisi **paralel** sorgular
(kaynak başına 5,5 sn, toplam 8 sn tavan):

| Kaynak | Getirilen | Kimlik çözümü |
|---|---|---|
| Jikan | genres · explicit_genres · themes · demographics · studios · producers · licensors | MAL kimliği |
| AniList | genres · tags (rank, spoiler) · studios (isMain) | AniList/MAL kimliği |
| Shikimori | genres (`kind`) · studios | MAL kimliği + `myanimelist_id` doğrulaması |
| Kitsu | categories · productions (rol) | `mappings` → `myanimelist/{anime,manga}` |
| **Bangumi** | tags + infobox kurumları | **normalize tam başlık** + yıl ±1 |
| **TMDB** | genres · keywords · production_companies · networks | TMDB kimliği |

Cihaz günlüğünde kaynak bazlı sayaç basılır:

```
KitsugiTaxonomySources: Kaynak bazlı: jikan=5t/12e/4k | anilist=4t/38e/2k |
shikimori=6t/9e/1k | kitsu=6t/4e/3k | bangumi=5t/120e/9k | tmdb=4t/15e/7k
Taksonomi toplandı: tür=12 etiket=185 stüdyo=4 yapımcı=14
```

`yok` = o kaynakta kimlik/başlık çözülemedi; diğerleri etkilenmez.

**Bangumi sayfaları** da aynı havuzu kullanır (`enrich` → `collect(includeBangumi = false)`),
yani sayfanın hangi kaynaktan açıldığı sonucu değiştirmez.

---

## 3) Tavanlar (v2.4.338)

| Alan | v2.4.337 | v2.4.338 |
|---|---|---|
| Bangumi etiketleri | 40 | **400** |
| Bangumi türleri | 12 | **64** |
| Birleşik tür | 48 | **160** |
| Birleşik etiket | 96 | **600** |
| Birleşik kurum | 64 | **256** |
| Tema / hedef kitle | 24 / 12 | **64 / 32** |
| Etiket kartı (daraltılmış) | 10 | **30** |

Bu değerlerin altına inilmesi `audit_taxonomy_sources.py` tarafından **hata** sayılır.

---

## 4) Kurum bütünlüğü kuralları

1. **Hiçbir kurum rolden dolayı silinmez.** `dropProducersThatAreStudios` kaldırıldı; aynı
   şirket hem stüdyo hem yapımcı ise iki listede de görünür.
2. **Tekilleştirme anahtarı `rol + ad`.** `MAPPA` (STUDIO) ile `MAPPA` (PRODUCER) ayrı kayıttır.
3. **Kimliği olan kayıt tercih edilir** — aynı kurum iki kaynakta geçiyorsa tıklanabilir olan
   (MAL/AniList/Bangumi person-id) korunur.
4. **Kiril/Çince sızıntısı yok**: `toLocalizedOrgName` → sözlük; sözlükte yoksa Latin harflerine
   çevrilir; kaomoji/infobox artığı adlar (`っ´Д`)っゼロ式の家`) gösterilmez.
5. **Dergi/yayınevi ayrı bölüm**: "Yayıncı Ağlar" ile karışmaz.

---

## 5) Otomatik denetimin kapsadığı üç eksen

1. **Alan kapsaması** — 28 alan + 6 çapraz kaynak.
2. **Tavan alt sınırları** — tür ≥160, etiket ≥600, kurum ≥256, Bangumi etiketi ≥400.
3. **Kurum eksilten çağrı yasağı** — `dropProducersThatAreStudios` geri gelirse CI kırılır.

```
KAYNAK       ALAN                      DURUM
------------------------------------------
AniList     genres                    ✔ okundu
...
TMDB        keywords                  ✔ okundu

Çapraz kaynak toplayıcı (KitsugiTaxonomySources):
  Jikan      ✔
  AniList    ✔
  Shikimori  ✔
  Kitsu      ✔
  Bangumi    ✔
  TMDB       ✔

Toplam 28 alan denetlendi · 7 kaynak
TÜM KAYNAKLAR BEKLENEN ALANLARI OKUYOR — eksik yok.
```

---

## 6) Cihazda nasıl doğrulanır

1. `adb logcat -s KitsugiTaxonomySources KitsugiDetailClient KitsugiBangumiDetailClient`
2. Aynı yapımı iki farklı kaynaktan açın (MAL ve Bangumi). **Tür/etiket/kurum listeleri aynı
   olmalı** — çünkü ikisi de aynı altı kaynağı birleştiriyor.
3. `KitsugiDetailClient: Birleşik taksonomi: …` satırı nihai sayıları verir.
4. Etiket sayısı beklediğinizden düşükse ilgili kaynağın `yok` yazıp yazmadığına bakın.
