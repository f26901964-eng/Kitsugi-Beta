# Kaynak Kapsama Raporu — tür / etiket / kurum

**Sürüm:** v2.4.337 · **Tarih:** 2026-10-21 · **Doğrulayan:** `scripts/audit_taxonomy_sources.py --strict`

Bu rapor, desteklenen **yedi detay kaynağının** her biri için hangi alanların okunduğunu ve
hangi rollerin üretildiğini gösterir. Denetim, kodun kendisini tarar: bir alan refactor
sırasında düşerse CI kırılır.

---

## 1) Kapsama matrisi

| Kaynak | Tür | Etiket | Stüdyo | Yapımcı | Kanal | Yayıncı | Dergi |
|---|---|---|---|---|---|---|---|
| **AniList** | `genres` | `tags` (rank + spoiler) | `studios` (`isMain` dahil, hepsi) | — | — | — | — |
| **MAL/Jikan** | `genres`, `explicit_genres` | `themes`, `demographics` | `studios` | `producers`, `licensors` | — | `publishers` (manga) | `serializations` |
| **Bangumi** | meta etiketler | `tags` | 动画制作 · アニメーション制作 | 製作 · 製作委員会 · 制作協力 · 音楽制作 · レーベル · 配給 | 播放电视台 · 放送局 | 出版社 | 连载杂志 · 掲載誌 |
| **Shikimori** | `genres` (`kind=genre`) | `kind=theme` / `demographic` | `studios` | — | — | — | — |
| **Kitsu** | `categories` | `categories` | `animeProductions` (`role=studio`) | `role=producer` | `role=network` | `role=publisher` | `role=magazine` |
| **Simkl** | `genres` | `genres` (resmî olmayan) | — *(API vermiyor)* | — | — | — | — |
| **TMDB** | `genres` | `keywords` | `production_companies[0]` | `production_companies[1…]` | `networks` | — | — |

**Neden Simkl'de kurum yok:** Simkl API'si (`/anime/{id}?extended=full`) yalnızca `genres`,
`ids`, `ratings` gibi alanlar döndürür; stüdyo/yapımcı alanı **yayınlamaz**. Simkl'den açılan
sayfalar bu açığı çapraz toplayıcıdan (MAL + AniList + Shikimori + Kitsu) kapatır.

---

## 2) Çapraz toplayıcı

`KitsugiTaxonomySources.collect()` her detay açılışında **dört servisi paralel** sorgular
(kaynak başına 4,5 sn, toplam 6 sn tavan):

| Kaynak | Getirilen |
|---|---|
| Jikan (`anime/{malId}`) | genres · explicit_genres · themes · demographics · studios · producers · licensors |
| AniList (`Media(id/idMal)`) | genres · tags (rank, isMediaSpoiler) · studios (isMain) |
| Shikimori (`animes/{malId}`) | genres (`kind` ayrımıyla) · studios |
| Kitsu (`anime/{id}` via `mappings`) | categories · animeProductions (rol ile) |

Cihaz günlüğünde kaynak bazlı sayaç basılır:

```
KitsugiTaxonomySources: Kaynak bazlı: jikan=5t/12e/4k | anilist=4t/28e/2k |
shikimori=6t/9e/1k | kitsu=yok
Taksonomi toplandı: tür=9 etiket=41 stüdyo=3 yapımcı=4
```

`yok` = o kaynak kimlik çözümünde bulunamadı; diğerleri etkilenmez.

---

## 3) Kimlik güvenliği

| Kaynak | Kimlik çözümü | Koruma |
|---|---|---|
| Jikan | yalnız `1..99.999.999` | Bangumi'nin 500M+ stable id'leri ve Shikimori/Kitsu kimlikleri buraya girmez |
| AniList | `id` veya `idMal` | 100M+ stable id normalize edilir |
| Shikimori | `animes/{malId}` | yanıttaki `myanimelist_id` farklıysa **veri kullanılmaz** |
| Kitsu | `mappings?filter[externalSite]=myanimelist/anime` | deterministik eşleme; bulunamazsa atlanır |

---

## 4) Birleştirme tavanları

| Alan | Eski | Yeni |
|---|---|---|
| Tür | 24 | **48** |
| Etiket | 48 | **96** |
| Kurum (stüdyo/yapımcı/kanal/dergi) | 24 | **64** |
| Tema / hedef kitle | 12 / 8 | **24 / 12** |

Tavanlar yalnızca **gösterim** içindir; modelde daha fazlası varsa kesilmez — birleştirme
sırasında spoiler etiketleri her zaman korunur.

---

## 5) Otomatik denetim çıktısı

```
KAYNAK       ALAN                      DURUM
------------------------------------------
AniList     genres                    ✔ okundu
AniList     tags                      ✔ okundu
AniList     studios                   ✔ okundu
MAL/Jikan   genres                    ✔ okundu
MAL/Jikan   themes                    ✔ okundu
MAL/Jikan   demographics              ✔ okundu
MAL/Jikan   studios                   ✔ okundu
MAL/Jikan   producers                 ✔ okundu
MAL/Jikan   licensors                 ✔ okundu
MAL/Jikan   serializations            ✔ okundu
MAL/Jikan   publishers                ✔ okundu
MAL/Jikan   authors                   ✔ okundu
Bangumi     动画制作                    ✔ okundu
Bangumi     製作                      ✔ okundu
Bangumi     播放电视台                  ✔ okundu
Bangumi     出版社                    ✔ okundu
Bangumi     连载杂志                    ✔ okundu
Bangumi     tags                      ✔ okundu
Shikimori   genres                    ✔ okundu
Shikimori   kind                      ✔ okundu
Shikimori   studios                   ✔ okundu
Kitsu       categories                ✔ okundu
Kitsu       animeProductions          ✔ okundu
Simkl       genres                    ✔ okundu
TMDB        genres                    ✔ okundu
TMDB        production_companies      ✔ okundu
TMDB        networks                  ✔ okundu
TMDB        keywords                  ✔ okundu

Çapraz kaynak toplayıcı (KitsugiTaxonomySources):
  Jikan      ✔
  AniList    ✔
  Shikimori  ✔
  Kitsu      ✔

Toplam 28 alan denetlendi · 7 kaynak
TÜM KAYNAKLAR BEKLENEN ALANLARI OKUYOR — eksik yok.
```

---

## 6) Cihazda nasıl doğrulanır

1. Aynı animeyi iki farklı kaynaktan açın (ör. MAL ve Kitsu). **Stüdyo/yapımcı/kanal listeleri
   aynı olmalı**; fark varsa logcat'te `Kaynak bazlı:` satırına bakın.
2. Logcat filtresi: `adb logcat -s KitsugiTaxonomySources`.
3. Kitsu kaynaklı bir sayfada stüdyo görünmüyorsa `kitsu=yok` yazar → `mappings` eşlemesi
   bulunamamıştır (Kitsu'da kayıt yok demektir; sayfa yine MAL/AniList verisiyle açılır).
4. Shikimori kaynaklı bir sayfada Kiril metin görünmemeli; Rusça türler
   `RussianTaxonomyDictionary` üzerinden TR/EN'e çözülür.
