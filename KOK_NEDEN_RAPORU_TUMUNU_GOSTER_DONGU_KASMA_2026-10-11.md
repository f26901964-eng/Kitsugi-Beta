# KÖK NEDEN RAPORU — "Tümünü Göster" sayfaları döngüye giriyor + kutucuklar üst üste biniyor + arama/keşfet kasması

Tarih: 2026-10-11
Kapsam: AniList / MAL(Jikan) / Kitsu / Simkl / Shikimori / Bangumi + Explore ("Tümü" ve kaynak sayfaları) + Arama rafları ve "Tümünü Göster" tam ekran sayfaları
Durum: Kod düzeltmeleri yapıldı. **Bu ortamda Kotlin/Android SDK yok** → derleme ve cihaz testi yapılamadı (statik inceleme + cihaz logu kanıtı ile doğrulandı).

---

## TL;DR — 4 ayrı kök neden, hepsi "tek satırlık" sınıfta

| # | Kök neden | Görünen belirti |
|---|-----------|-----------------|
| 1 | Lazy liste **anahtarlarında `type` alanı eksik** → aynı kaynağın anime/manga (veya film/dizi) kaydı aynı anahtarı alıyor | Kutucuklar üst üste biniyor, liste/scroll bozuluyor, "Tümünü Göster" karışıyor |
| 2 | Sonsuz kaydırma tetikleyicisi **yükleme bayrağını anahtarına dâhil ediyor** → her sayfa bitişinde yeniden ateşleniyor | Sayfa kendi kendine döngüye giriyor, sürekli veri çekiyor (429 fırtınası) |
| 3 | Shimmer fırçası **her karede yeniden üretilip CompositionLocal ile dağıtılıyor** | Yükleme sürerken tüm liste her karede yeniden kompoze → GC fırtınası + %24 jank |
| 4 | Kart üstü "yaklaşan bölüm" bilgisi **başlık tahminiyle yanlış yapıma** bağlanabiliyor + AniList takvimi boş sonucu önbelleğe almıyor | Kutucuklarda olmaması gereken/yanlış tarih; her yüklemede ekstra AniList isteği |

---

## 1) ASIL KÖK NEDEN — Lazy anahtarlarında `type` yok

### Mekanizma
Kaynakların id uzayları **medya türüne göre ayrıdır**: aynı sayısal kimlik hem anime hem manga
(MAL/Jikan, Kitsu, Shikimori, Simkl) veya hem film hem dizi (TMDB) olabilir.
Örn. MAL `/anime/21` (One Piece anime) ile MAL `/manga/21` (One Piece manga) **farklı iki yapımdır**.

Kod tabanında bu kural zaten öğrenilmiş ve **kimlik/dedup** tarafında uygulanmış:
- `AllSourcesExplore.kt:60` → `exploreIdentity() = "${source}:$type:$malId"` ✅ (tip var)
- `SearchResultMerge.kt` → `"${source}_${type}_${malId}"` ✅ (tip var)
- `SearchScreen.kt` ana sonuç listesi → `res_${source}_${type}_${malId}_…` ✅ (tip var)

**Ama Compose Lazy anahtarlarında `type` UNUTULMUŞ** (11 nokta):
```
KitsugiHorizontalMediaSection.kt:143/181   key = "tv_/h_${source}_${malId}_${tmdbId}_${cs3Url.hashCode()}"   ❌ tip yok
FullScreenMediaGridPage.kt:766/945         key = "grid_/list_${source}_${malId}_…"                         ❌ tip yok
SearchScreen.kt:1088/1122                  key = "sr_tv_/sr_${source}_${malId}_…"                          ❌ tip yok
KitsugiMediaGridDialog.kt:256              key = "dlg_${source}_${malId}_…"                                 ❌ tip yok
TvSearchScreen.kt:191 / TvGridHomeContent.kt:279   key = "${source}_${malId}"                                ❌ tip yok
TvClassicHomeContent.kt:93 / TvModernHomeContent.kt:95  yerel "mix" tekilleştirme anahtarı                 ❌ tip yok
```

### Neden "veri yüklendikçe" ortaya çıkıyor?
Çakışma, **aynı listenin içinde aynı kaynak+id'li iki farklı tür** bulunduğunda oluşur:
- "Tümü" araması anime + manga sonuçlarını **bilerek** birleştirir (2026-10-08'de dedup anahtarına
  `type` eklendi, ikisi de listede kaldı) → ama row/grid anahtarı hâlâ tipsiz.
- Explore'da TMDB "Trend Her Şey" (film+dizi), "Tümü" modunda karma bölümler, 2. sayfa sonrası
  eşleşen çift ancak o zaman aynı listede görünür.

### Sonuç neden "üst üste binme / döngü" gibi görünüyor?
Compose aynı anahtarlı iki öğeyi **tek slot** kabul eder: ikinci öğe birincinin konumunda çizilir
(kutucuklar üst üste biner), anahtar→indeks haritası bozulur, kaydırma konumu ve öğe geri dönüşümü
şaşar; sürüm/ölçüm durumuna göre `Key "…" was already used` çökmesi de üretir
(bu çökme sınıfı v2.4.206'da 7 listede düzeltilmişti — Lazy anahtarları atlanmış).

### Çözüm
Tek merkez: `ui/utils/KitsugiLazyKeys.kt` → `JikanSearchResult.kitsugiLazyKey(prefix)`:
```
"${prefix}_${source}_${type}_${malId}_${tmdbId?:0}_${cs3Url.hashCode()}"
```
11 kullanım noktası bu yardımcıya geçirildi (anime/manga/film/dizi ayrımı artık anahtarın parçası).

---

## 2) Sayfayı döngüye sokan mekanizma — otomatik sayfalama tetikleyicisi

```kotlin
LaunchedEffect(shouldLoadMoreList, isLoadingMore, hasMorePages, isGridView) { … }
//                   ^^^^^^^^^^^^^^  yükleme bayrağı anahtarın içinde!
```
- `shouldLoadMore` koşulu `!isLoadingMore` içerdiği için **her sayfa yüklemesi bittiğinde**
  (true→false) efekt yeniden başlıyor; liste yerleşimi henüz güncellenmediği için "sona ulaşıldı"
  koşulu hâlâ `true` okunuyor → **kullanıcı hiç kaydırmasa bile** arka arkaya sayfa çekiliyor.
- Aktif filtre görünen öğe sayısını artırmadığında bu zincir **hiç bitmiyor** (döngü).
- Aynı hata `SearchScreen.kt` `LaunchedEffect(shouldLoadMore)` içinde de var (Tümünü Göster → kaynak arama sayfası bu ekranı kullanır).

**Kanıt (cihaz logu):** `graphql.anilist.co` için 22:54:17–22:54:33 arasında 429 seli
(`RetryInterceptor: Retry 1/2, 2/2`, `Retry-After: 5000ms`) + aynı pencerede başlayan GC fırtınası.

**Çözüm:** (a) aynı görünür öğe sayısı için ikinci kez tetiklenmez, (b) kaydırma başına en fazla
3 sayfa, (c) kullanıcı kaydırdığında bütçe yenilenir → normal sonsuz kaydırma birebir korunur.

---

## 3) Kasmanın kaynağı — her karede yeni shimmer fırçası (GC fırtınası)

Eski `rememberKitsugiShimmerBrush()` animasyon değerini **kompozisyonda** okuyup her karede
`Brush.linearGradient` üretiyor, `KitsugiShimmerProvider` bunu CompositionLocal ile dağıtıyordu:
her karede → değeri okuyan **tüm iskelet bileşenleri yeniden kompoze**, her kart için yeni shader.

`SearchScreen` bu sağlayıcıyı `active = isLoading || isLoadingMore || multiResults.isAnyLoading`
ile sarıyor; yani **arama sürdükçe tüm liste her karede yeniden kompoze ediliyordu** ("veri
geldikçe kasıyor" tam olarak bu). Aynı sağlayıcı `ExploreScreen` ve TV "Tümü" sayfasında da var.

**Kanıt:** logcat'te ~1.000.000 nesne / ~32 MB serbest bırakan arka plan GC, **~1.2 saniyede bir,
4 dakika boyunca** (≈20–30 MB/s çöp) — 22:54:23.540'tan itibaren (AniList 429 penceresiyle aynı an).
`dumpsys gfxinfo`: 1454 kare / 352 janky (%24.21), p90 1200 ms.

**Çözüm:** fırça sabit (dosya düzeyinde, `remember`), tarama konumu `LocalShimmerOffset: State<Float>`
olarak dağıtılıyor ve **yalnızca `drawBehind` içinde** okunuyor → her karede yalnızca yeniden çizim
(recomposition ve shader üretimi yok). Yüklenme yokken animasyon saati hiç kurulmuyor.
TV manga iskeleti (`TvMangaBrowseScreen`) de statik `background(brush)` yerine animasyonlu bloğa geçti.

---

## 4) Kutucuklardaki yanlış/ekstra veri — yaklaşan bölüm eşleşmesi

- `KitsugiUpcomingAirEnricher.lookupAniList`, öğenin **kendi** yayın değeri varken (süresi geçmiş
  olsa bile) **başlık tahminine** düşüyordu → aynı adlı farklı yapımın (remake / aynı adlı dizi)
  bölüm tarihi kutucuğa basılabiliyordu. Artık: kimlik (malId) eşleşmesi her zaman güvenli,
  başlık tahmini **yalnızca öğenin hiç verisi yoksa**.
- `anilistScheduleMap()` yalnızca **dolu** haritayı önbelleğe alıyordu → AniList boş döndürdüğünde
  veya 429 verdiğinde **her sayfa yüklemesi yeni istek** atıyordu (kota fırtınası + gereksiz JSON).
  Artık: başarılı çekim (boş dâhil) 10 dk TTL, hata durumunda 60 sn backoff.

---

## Değişen dosyalar
```
app/src/main/java/com/kitsugi/animelist/ui/utils/KitsugiLazyKeys.kt            (YENİ — tek merkez anahtar)
app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiShimmer.kt        (fırça/offset ayrımı)
app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiHorizontalMediaSection.kt
app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiMediaGridDialog.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/FullScreenMediaGridPage.kt (anahtar + sayfalama emniyeti)
app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchScreen.kt      (anahtar + sayfalama emniyeti)
app/src/main/java/com/kitsugi/animelist/ui/screens/explore/...                 (değişmedi — anahtarlar tek merkezden)
app/src/main/java/com/kitsugi/animelist/ui/tv/home/TvClassicHomeContent.kt
app/src/main/java/com/kitsugi/animelist/ui/tv/home/TvModernHomeContent.kt
app/src/main/java/com/kitsugi/animelist/ui/tv/home/TvGridHomeContent.kt
app/src/main/java/com/kitsugi/animelist/ui/tv/search/TvSearchScreen.kt
app/src/main/java/com/kitsugi/animelist/ui/tv/manga/TvMangaBrowseScreen.kt
app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiUpcomingAirEnricher.kt
```

## Cihazda doğrulanacak senaryolar
1. "Tümü" aramasında anime+manga aynı anda listelenen bir başlık (örn. aynı adlı seri) → raf ve "Tümünü Göster" kaydırmasında kartlar üst üste binmemeli, çökme olmamalı.
2. Explore → herhangi bir kaynak → bir bölümün "Tümünü Göster"i → sayfa sonuna kadar kaydır; sayfa kendiliğinden sayfa çekmeye devam etmemeli (istekler yalnızca kaydırmayla gelmeli).
3. Aynı senaryoda bir tür filtresi (Tür/format) açıkken kaydırma → görünür öğe artmasa bile istek zinciri durmalı.
4. Arama sürerken (7 raf + liste yüklenirken) kare düşüşü ve pil/ısınma belirgin azalmalı; `gfxinfo` janky oranı ve logcat'teki `concurrent copying GC` sıklığı düşmeli.
5. Kart üzerindeki "Bölüm N · tarih" bilgisi yalnızca doğru yapımda görünmeli (özellikle remake / aynı adlı diziler).
