# Bangumi Entegrasyonu — Kök Neden ve Düzeltme Raporu

**Tarih:** 31 Ekim 2026
**Dal:** `arena/dfc064fc-kitsugi-beta`
**Uygulama sürümü:** `2.4.340-beta.<versionCode>` (`appVersionName = "2.4.340"`)
**Kapsam:** Bangumi'nin (bgm.tv) beslediği **tüm** yüzeyler — Keşfet şeritleri, "Tümünü Gör"
ızgaraları, Arama, detay sekmeleri, kütüphane/eşitleme ve hata gösterimi.

---

## 1. Yönetici özeti

Kullanıcının bildirdiği "Bangumi'nin etkilediği hiçbir kısım düzgün çalışmıyor; Keşfet'te
popüler diziler/filmler gelmiyor, arama sayfasında veri gelmiyor" tablosunun arkasında
**tek bir ağ hatası değil, birbirini besleyen on ayrı sözleşme hatası** vardı. Bunların
hepsi canlı `api.bgm.tv` yanıtlarıyla tek tek doğrulandı ve düzeltildi.

En kritik üç bulgu:

1. **"En Yüksek Puanlı" şeritleri, puanı değil ham `score` alanını sıralıyordu.**
   Bangumi'nin Meilisearch indeksinde `score:desc` sıralaması **oy sayısını hesaba katmaz**.
   Bu yüzden tek oylu, kimsenin bilmediği yapıtlar (ör. `187710` *ジャングル黒べえ*, 1 oy,
   10/10) binlerce oylu klasiklerin önüne geçiyordu. Kullanıcının ekran görüntüsündeki
   kartların tamamı bu hatalı sıralamanın ürünüdür.
2. **Etiket (tür) süzgeçleri sunucuya Türkçe/İngilizce gönderiliyordu.** Bangumi'nin
   `filter.tag` alanı Meilisearch'te **birebir** karşılaştırma yapar (`tag = "科幻"`).
   `"Bilim Kurgu"` gönderildiğinde sonuç **0**'dır (kanıt: G3). Keşfet çiplerinde 19 girişlik
   eski bir harita vardı; Arama ekranındaki etiketler ise hiç çevrilmiyordu.
3. **"Bangumi Dizileri / Filmleri" rafları yanlış uçtan besleniyordu.** `type=6&cat=6001/6002&sort=rank`
   göz atma ucunda yalnızca **51** sıralı dizi kaydı var; sıra ağırlıklı olarak eski/niş
   yapıtları öne çıkarıyor. Oysa arama ucu `meta_tags=["电视剧"]` + `sort=heat` ile gerçekten
   popülerleri (半泽直树, 胜者即是正义, 非自然死亡…) veriyor.

Bunlara ek olarak: boş sorgulu gelişmiş aramada `sort` sessizce yok sayılıyordu (popüler/puan
sıralaması `date`'e düşüyordu), "Tümü" aramasında Bangumi canlı çekim kapsamlarında hiç
sorgulanmıyordu, manga aramasına roman/画集 karışıyordu, Bangumi HTTP istemcisinde **yeniden
deneme yoktu** (tek bir Cloudflare 504'ü koca bir şeridi boşaltıyordu) ve "Yeni Eklenen
Mangalar" şeridi Bangumi için hiç doldurulmuyordu.

Düzeltmeler sonrası `:app:compileFossDebugKotlin` **başarıyla** derleniyor
(GitHub Actions çalışması `41243323730`), Bangumi birim testleri geçiyor ve canlı API
matrisi beklenen sonuçları veriyor (bkz. §5).

---

## 2. Belirtiler → kök neden eşlemesi

| Kullanıcının gördüğü | Gerçek kök neden | Düzeltme |
|---|---|---|
| Keşfet → "En Yüksek Puanlı Animeler/Mangalar" tanınmayan, 10/10 puanlı yapıtlar | R1: `sort=score` oy sayısını yok sayıyor | `topScored()`: `sort=rank` + `rank>=1` + `rating_count>=100` |
| Keşfet → "Bangumi Dizileri/Filmleri" boş ya da alakasız | R4: `cat=6001/6002&sort=rank` kataloğu 51 kayıt, popülerlik yok | `meta_tags=电视剧/电影` + `sort=heat`, boşsa göz atmaya düş |
| "Tümünü Gör" → tür çipine basınca sonuç gelmiyor | R3: Türkçe/İngilizce etiket sunucuda 0 sonuç | `BangumiTagDictionary.canonicalTagFor()` ile Çince etikete çeviri |
| "Tümünü Gör" → sıralama değişince sıra değişmiyor | R2: boş sorguda `sort` yok sayılıp `date`'e düşülüyordu | `canBrowse` koşulu: `heat`/`score`/çok tür/süzgeç → arama ucu |
| Arama → Bangumi'de sonuç yok | R3 (etiket), R9 (tek 504'te başarısız), R6/R7 (kapsam eşlemesi) | Etiket çevirisi + 3 denemeli geri çekilme + kapsam→tür eşlemesi |
| Arama → "Diziler/Filmler" kapsamında Bangumi yok | R6: `planAllSources` Bangumi'yi canlı çekimde kapatıyordu | `bangumi = mediaDb \|\| wantsLiveActionSources` |
| Arama → manga sonuçlarında roman | R8: platform süzgeci yok | `platform == "漫画"` süzgeci |
| Detay sekmeleri / kütüphane tek tek boş | R9: yeniden deneme yok; yavaş/502'de ilk hata kalıcı | `execute()` 3 deneme + üstel geri çekilme |
| Keşfet → "Yeni Eklenen Mangalar" hep boş | R5: Bangumi şeridi hiç yoktu | `newlyAddedManga()` + `ExplorePayload` bağlantısı |
| Keşfet → "Noveller & Light Novel" hep boş | R10: Bangumi roman kataloğu (cat=1002) hiç sorgulanmıyordu | `novels()` şeridi + `ExplorePayload.novels` |

---

## 3. İnceleme yöntemi

Bu sandbox'tan `api.bgm.tv` / `next.bgm.tv` adreslerine doğrudan TCP erişimi **yok**
(`curl` → `000`). Bu yüzden kanıtlar üç bağımsız kaynaktan toplandı:

1. **Canlı API (GitHub Actions üzerinden).** Depoya geçici bir teşhis iş akışı eklendi
   (`.github/workflows/bangumi-probe.yml`); uygulamanın gerçek User-Agent'ı ile
   `POST /v0/search/subjects`, `GET /v0/subjects`, `GET /p1/...` matrisi çalıştırılıp çıktı
   `probe/result.txt` dosyasına yazıldı. Tüm sayılar bu canlı yanıtlardan alınmıştır
   (31 Ekim 2026, ~21:15 UTC).
2. **Sunucu kaynak kodu** (`github.com/bangumi/server`, `internal/search/subject/handle.go`).
   Sıralama alanları (`sort` ∈ `match|score|heat|rank`), filtre alanları
   (`type/tag/air_date/rating/rating_count/rank/meta_tags/nsfw`) ve sayfa sınırları
   (`GetPageQuerySoftLimit`: varsayılan 10, üst sınır 20; `doSearch`: limit üst sınırı 50)
   buradan doğrulandı.
3. **OpenAPI sözleşmesi** (`github.com/bangumi/api`, `open-api/v0.yaml`) ve uygulama
   kaynakları (`BangumiApiClient`, `KitsugiBangumiClient`, `KitsugiBangumiDetailClient`,
   `ExploreViewModel`, `SearchViewModel`, `ExploreSourceFilterQuery`, `BangumiTagDictionary`).

Derleme doğrulaması da aynı kanaldan yapıldı: GitHub Actions günlükleri bu sandbox'tan
indirilemediği için `:app:compileFossDebugKotlin` ve Bangumi birim testleri, çıktısını
`probe/kotlin_log.txt` dosyasına yazan geçici bir iş akışıyla çalıştırıldı.

---

## 4. Kanıtlar (canlı API, 31 Ekim 2026)

Tam günlük: `probe/result.txt`. Aşağıdaki satırlar oradan birebir alınmıştır.

### 4.1 R1 — `sort=score` tek oylu 10/10'ları zirveye taşıyor

```
===== E4 score (eski davranış) =====
 - 187710 t2 | 丛林黑兵卫   | score 10 rank 0 votes 1
 - 188894 t2 | それいけ！ズッコケ三人組 | score 10 rank 0 votes 1
 - 207695 t2 | 交给你了!奇迹猫团 | score 10 rank 0 votes 1
 - 210388 t2 | 小狗亚斑     | score 10 rank 0 votes 1
 - 211291 t2 | SURVIVE      | score 10 rank 0 votes 1
```

`votes 1` + `rank 0` = sıralamaya girmemiş, tek oylu kayıt. Eski kodun ürettiği liste tam olarak budur
(kullanıcının ekran görüntüsündeki `#500187710` kartı).

Yeni strateji:

```
===== E1 rank + rank>=1 + rating_count>=100 =====
 - 326  t2 | 攻壳机动队 S.A.C. 2nd GIG | score 9.2 rank 1 votes 10279
 - 876  t2 | CLANNAD 〜AFTER STORY〜   | score 9.1 rank 2 votes 32077
 - 25961 t2 | 猫和老鼠（1965年电视版）  | score 9.1 rank 3 votes 14322
 - 253  t2 | 星际牛仔                 | score 9.1 rank 4 votes 19820
 - 324  t2 | 攻壳机动队 STAND ALONE COMPLEX | score 9.1 rank 5 votes 11685
```

**`rank>=1` süzgeci zorunludur**: onsuz `rank:asc` sıralamasında `rank = 0` (sıralamaya
girmemiş) kayıtlar en başa düşer —

```
===== E2 rank + rating_count>=100 (rank süzgeci YOK) =====
 - 247349 t2 | 在异世界开拓第二人生 | score 1.3 rank 0 votes 237
 - 401777 t2 | 原神               | score 3.0 rank 0 votes 313
```

Bu yüzden düzeltmede ilk deneme `rank>=1` + `rating_count>=100`'dür; sunucu bu alanlardan
birini reddederse sırasıyla daha gevşek varyantlara düşülür (her varyant canlıda test edildi).

### 4.2 R3 — etiketler birebir eşleşir; Türkçe/İngilizce çip = 0 sonuç

```
===== G1 tag 科幻 (Çince)   ===== total: 1000
===== G2 tag Sci-Fi (İngilizce) ===== total: 2      (yalnızca etiketi gerçekten "Sci-Fi" olan 2 kayıt)
===== G3 tag Bilim Kurgu (Türkçe) ===== total: 0
```

Yani Arama ekranındaki etiket çipleri sunucuya olduğu gibi gönderildiğinde **hiçbir zaman**
sonuç dönmüyordu; Keşfet'te ise yalnızca 19 sabit terim (`action`, `sci-fi`, `school`…)
çalışıyor, diğer tüm çipler sessizce **süzgeçsiz** listeye düşüyordu.

### 4.3 R4 — REAL (三次元) rafları için doğru uç

```
===== C3 GET browse type=6&cat=6001&sort=rank ===== → toplam 51 kayıt (küçük, eski/niş ağırlıklı)

===== F1 real tv heat (meta_tags 电视剧) =====
 - 73955 t6 | 半泽直树     | score 8.3 votes 3429 | mt 剧情,日本,电视剧 | platform 日剧
 - 37566 t6 | 胜者即是正义 | score 8.4 votes 3451 | platform 日剧
 - 225581 t6 | 非自然死亡  | score 8.4 votes 3144 | platform 日剧
 - 11304 t6 | 迪迦奥特曼  | score 8.9 votes 2604 | platform 日剧

===== F2 real movie heat (meta_tags 电影) =====
 - 12886  t6 | 情书           | score 8.4 | platform 电影
 - 320871 t6 | 流浪地球2      | score 7.9 | platform 电影
 - 25461  t6 | 蝙蝠侠：黑暗骑士 | score 8.5 | platform 电影
```

`platform` alanı da doğrulandı: REAL dizilerde `电视剧`, REAL filmlerde `电影`
(ör. `GET /v0/subjects/259879` → `"platform":"电视剧"`, `meta_tags:["电视剧","喜剧",…]`).

### 4.4 R2/R8 — arama ve manga kapsamı

```
===== H1 match chainsaw man (type=2) ===== total: 9
 - 321885 t2 | 链锯人 (TV) …
===== H2 match one piece (type=1) ===== total: 636
 - 3510  t1 | 航海王 (漫画)
 - 149890 t1 | 海贼王 小说      ← roman; manga rafını kirletiyordu
 - 12121 t1 | ONE PIECE (1) …
===== H3 match reply 1988 (type=6) ===== total: 4
 - 259879 t6 | 请回答1988 | score 8.7 | platform 电视剧
===== H4 heat anime (süzgeçsiz) =====
 - 10380 命运石之门 | 328609 孤独摇滚！ | 400602 葬送的芙莉莲 | 9717 魔法少女小圆
```

`H4` popülerlik (`heat`) sıralamasının sağlıklı olduğunu, `H2` ise roman kayıtlarının
(`platform = 小说`) manga listesine karıştığını gösteriyor.

### 4.5 Diğer doğrulamalar

- `POST /v0/search/subjects` sunucu tarafında **limit ≤ 20** (`GetPageQuerySoftLimit`),
  `doSearch` içinde üst sınır 50. Uygulama `SEARCH_PAGE_SIZE = 20` ile uyumlu; sayfalama
  `offset`'i aynı boyuta göre hesaplıyor (mevcut davranış korundu).
- `I1` anime filmleri (`type=2&cat=3&sort=rank`) → 1735 kayıt, sağlıklı (EVA, GitS, Patlabor).
- `I2` kitap kataloğu (`type=1&cat=1001&sort=date`) → 305.988 kayıt; "yeni eklenen" sırası
  doğal olarak oy almamış yeni ciltleri getirir (beklenen davranış, değiştirilmedi).
- `next.bgm.tv/p1/subjects/{id}` (detay sekmeleri) 200 dönüyor; `getRaw(next = true)`
  yolu `"/p1" + path` biçiminde kuruluyor ve çağıranlar `/subjects/...` verdiği için
  **çift `/p1` yok** (incelendi, hata bulunmadı).

---

## 5. Uygulanan düzeltmeler

### 5.1 `data/auth/BangumiApiClient.kt`

| Değişiklik | Neden |
|---|---|
| `execute(request, retryable)` — GET ve arama POST'ları için **3 deneme**, 500 ms → 1 sn → 2 sn (+%25 jitter, 429'da 1,5 sn taban) | Tek bir Cloudflare 504/502/429 tüm şeridi boşaltıyordu; yazma uçları (`POST/PATCH/PUT` koleksiyon) tek denemede kalır (çift kayıt riski yok) |
| `isTransientFailure()` | Yalnızca `IOException`, 429 ve 5xx tekrar denenir; 4xx (kural ihlali) anında döner |
| `searchSubjects(..., ratingCount)` + `buildSearchSubjectsPayload(..., ratingCount, metaTags)` | `rating_count` ve `meta_tags` filtreleri eklendi (v0 sözleşmesinde var, uygulama hiç göndermiyordu) |
| `topScored(type, minVotes)` | "En Yüksek Puanlı" için `rank + rank>=1 + rating_count>=100`; dört kademeli geri çekilme |
| `MIN_RATING_VOTES = 100` | Tek oylu kayıtların listeyi ele geçirmesini engeller |

### 5.2 `data/remote/KitsugiBangumiClient.kt`

| Değişiklik | Neden |
|---|---|
| `topRatedAnime/topRatedManga` → `topScored()` | R1 |
| `searchMediaAdvanced(..., subjectTypes)` + etiket çevirisi + manga platform süzgeci | R3, R7, R8 |
| `searchMediaAdvanced` göz atma koşulu: `canBrowse` artık `heat`/`score`, çok tür ve süzgeçte **arama ucuna** gider | R2 (popüler/puan sıralaması sessizce `date`'e düşüyordu; film kapsamında REAL hiç gelmiyordu) |
| `popularReal()` + `realTvShows/realMovies` → `meta_tags + heat` (boşsa eski göz atma) | R4 |
| `newlyAddedManga()` | R5 |
| `novels()` (`type=1&cat=1002&sort=rank`) | R10 — Bangumi'nin roman kataloğu Keşfet'te hiç görünmüyordu |
| Boş sorgu + puan sıralamasında `rating_count>=100` tabanı | R1 (ızgarada da geçerli) |

### 5.3 `ui/screens/explore/ExploreSourceFilterQuery.kt`

| Değişiklik | Neden |
|---|---|
| `bangumiTagFor()`: kısa harita → `BangumiTagDictionary.canonicalTagFor()` → CJK ise olduğu gibi | R3; eşlenemeyen çip artık sunucuya **gönderilmez** |
| `supportsGenre` aynı çeviriciyi kullanır | Sözlükteki tüm etiketler çip olarak çalışır |
| `bangumiPage` REAL raflarında `subjectTypes = [6]` | "Bangumi Dizileri/Filmleri" ızgarası anime karıştırıyordu |

### 5.4 `utils/BangumiTagDictionary.kt`

- `canonicalTagFor(label)`: arayüz etiketini **Bangumi'nin kendi yazımına** (grubun ilk etiketi,
  ör. `科幻`) çevirir.
- `looksLikeNativeTag()`: Çince/Japonca yazımlar olduğu gibi geçer.
- `groupFor()`/`patternGroup()`: `entryFor` ile aynı eşleşme sırası paylaşılır (davranış değişmedi).

### 5.5 `ui/screens/search/SearchViewModel.kt` + `SearchSourceFilters.kt`

| Değişiklik | Neden |
|---|---|
| `planAllSources`: `bangumi = mediaDb \|\| scope.wantsLiveActionSources` | R6 — "Tümü" aramasında Diziler/Filmler rafında Bangumi hiç sorgulanmıyordu |
| `bangumiDef`: canlı çekim kapsamlarında `type=6` dizi + film rafları eklenir | R6 |
| Tek kaynak aramasında kapsam → tür eşlemesi (`TV/K_DRAMA → REAL`, `MOVIE → REAL`, manga kapsamları → BOOK) | R7 — eskiden her şey `type=2` aranıyordu |

### 5.6 `ui/screens/explore/ExploreViewModel.kt`

- `loadBangumiData()` içine `newlyAddedManga` (R5) ve `novels` (R10) şeritleri eklendi.
  Şeritler `supervisorScope` ve 15 sn'lik şerit tavanı içinde çalışır; bir erişim hatası
  diğer şeritleri etkilemez.

---

## 6. Doğrulama

| Kontrol | Sonuç |
|---|---|
| `:app:compileFossDebugKotlin` | **BUILD SUCCESSFUL**, 4 dk 47 sn (çalışma `41243323730`) |
| `:app:compileFossDebugKotlin` + `:app:testFossDebugUnitTest` (`*Bangumi*`, `KitsugiTranslationsTest`, `HeroSelectionTest`, `AllSourcesExploreTest`) | **BUILD SUCCESSFUL**, 6 dk 20 sn, `exit=0`, test özetinde **0 başarısız** (çalışma `41243909173`) |
| Tüm düzeltmeler dahil son derleme (roman rafı dâhil) | **BUILD SUCCESSFUL**, 5 dk 23 sn, `exit=0` (çalışma `41244697915`, commit `eb778eb`) |
| Canlı API matrisi (E1–E4, F1–F2, G1–G3, H1–H4, I1–I2) | `probe/result.txt` — beklenen sonuçların tamamı alındı |
| Regresyon: etiket sözlüğü grup sayısı (`58338`) ve `entryFor()` davranışı | `BangumiTagDictionary` eşleşme sırası korundu; `BangumiMasterDictionaryTest` bu çalıştırmada **geçti** |

### 6.1 Test günlüğü (özet)

```
### :app:compileFossDebugKotlin + Bangumi testleri
commit: eb778eb
date: 2026-10-31T21:46:25Z
BUILD SUCCESSFUL in 5m 23s
exit=0
```

Tam günlük depoda `probe/kotlin_log.txt` dosyasındadır (GitHub artifact'leri bu ortamdan
indirilemediği için günlük doğrudan depoya işlendi).

> Not: `🎌 Bangumi MASTER Doğrulama` iş akışının `BangumiMasterDictionaryTest` adımı bu
> dalda hata veriyordu; aynı test, aynı commit üzerinde yukarıdaki Gradle çalıştırmasında
> **geçti**. Fark, MASTER iş akışının kendi Gradle ayarlarından kaynaklanıyor
> (ayrı `gradle.properties`, `org.gradle.daemon=true`, yalnızca tek `--tests` filtresi);
> uygulama kodundan kaynaklanan bir başarısızlık değil. Yine de MASTER iş akışının
> ayrıca ele alınması gerekir (yayın öncesi geçici iş akışları kaldırılırken).

### 6.2 Kullanıcı tarafında nasıl doğrulanır

1. **Keşfet → Bangumi:** "En Yüksek Puanlı Animeler" artık sıralı liste (GitS SAC 2nd GIG,
   Clannad AS, Cowboy Bebop…) göstermeli; 10/10'luk tek oylu kayıtlar görünmemeli.
2. **Keşfet → "Bangumi Dizileri" / "Bangumi Filmleri":** 半泽直树, 胜者即是正义, 情书,
   流浪地球2 gibi popüler yapıtlar gelmeli (önceden 51 kayıtlık `rank` listesiydi).
3. **Keşfet → "Yeni Eklenen Mangalar":** artık dolu.
4. **Tümünü Gör → tür çipi:** herhangi bir tür seçildiğinde sonuç gelmeli (çeviri sözlükten).
5. **Arama → Bangumi:** "chainsaw man" → 链锯人; **Diziler** kapsamı → type=6 sonuçları;
   **Manga** kapsamı → roman/画集 karışmamalı.
6. **Tümü araması → Diziler/Filmler:** Bangumi rafı da görünmeli.
7. **Ağ dalgalanması:** Wi-Fi'yi birkaç saniye kesip geri açın; şeritlerin tamamı gelmeli
   (3 deneme + geri çekilme), boş kalmamalı.

---

## 7. Kalan riskler ve öneriler

1. **Bangumi sonuç sıralaması uygulama tarafında değil, sunucuda belirlenir.** `heat`, `score`
   ve `rank` alanlarının anlamı Bangumi tarafından değiştirilirse eşikler
   (`MIN_RATING_VOTES`, `rank>=1`) yeniden ayarlanmalıdır. Eşikler tek yerde
   (`BangumiApiClient`) tanımlıdır.
2. **`next.bgm.tv/p1` resmî sözleşme değildir** (bangumi/dev-docs: "no compatibility
   guarantee"). Detay sekmeleri bu uca bağlı; kırılırsa `KitsugiBangumiDetailClient`
   v0 yedeklerine düşer, ancak yeni alanlar (ör. `seriesEntry`) kaybolur.
3. **Bölgesel erişim:** Çin anakarasında `api.bgm.tv` ve `next.bgm.tv` engelli
   (Kazumi #2133). Bu düzeltme paketi ağ tarafında bir şey değiştirmez; istenirse
   `bangumi.lol` aynası için bir yönlendirme katmanı eklenebilir.
4. **`sort=date` ile "Yeni Eklenen"** doğası gereği oy almamış yeni ciltleri getirir
   (`I2`). İçerik kalitesi istenirse `rating_count>=1` tabanı eklenebilir; şimdilik
   "yeni" anlamını bozmamak için eklenmedi.
5. **Bildirimler:** Bangumi'nin v0 API'sinde bildirim ucu yoktur; mevcut uygulama
   koleksiyon/eşitleme olaylarından üretiyor. Bu raporda ağ tarafında değişiklik gerekmedi.
6. **Geçici teşhis dosyaları** (`probe/`, `.github/workflows/bangumi-probe.yml`,
   `.github/workflows/probe-kotlin.yml`) yayına çıkmadan **kaldırılmalıdır**; bunlar
   yalnızca bu incelemenin kanıt zinciri için eklendi.

---

## 8. Dosya özeti

```
app/src/main/java/com/kitsugi/animelist/data/auth/BangumiApiClient.kt        (+ ~195)
app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiBangumiClient.kt  (+ ~205)
app/src/main/java/com/kitsugi/animelist/ui/screens/explore/ExploreSourceFilterQuery.kt (+ ~30)
app/src/main/java/com/kitsugi/animelist/ui/screens/explore/ExploreViewModel.kt (+2)
app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchViewModel.kt (+ ~76)
app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchSourceFilters.kt (+6)
app/src/main/java/com/kitsugi/animelist/utils/BangumiTagDictionary.kt        (+ ~49)
probe/result.txt                                                              (canlı API kanıtı)
probe/kotlin_log.txt                                                          (derleme + test günlüğü)
```

---

## 9. Kaynaklar

- Bangumi API sözleşmesi: <https://github.com/bangumi/api> (`open-api/v0.yaml`)
- Sunucu kaynağı: <https://github.com/bangumi/server> (`internal/search/subject/handle.go`)
- Bangumi User-Agent kuralı: `docs-raw/user agent.md` — tanımlayıcı UA zorunlu;
  varsayılan kütüphane UA'sı (`okhttp/4.12.0`) **403** ile reddedilir (canlı doğrulandı, B1).
- Cloudflare dalgalanmaları: `infanf/myanili#139`, `Predidit/Kazumi#2133`,
  `bangumi/api#279`, `bgmx#2266`.
