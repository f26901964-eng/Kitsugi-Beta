# Keşfet Kaynak Yükleme & Kompozisyon Performansı — Teşhis & Çözüm (2. Tur)

**Tarih:** 2026-10-10 · **Dal:** `arena/26be3434-kitsugi-beta`

## 1. Şikâyet

Keşfet sayfası **tüm kaynaklarda** (AniList, MAL, MyList/Simkl, Kitsu, Shikimori, TMDB,
Bangumi) kaynağa göre az/çok kasıyor; sayfa kullanılamaz halde.

1. tur (`KESFET_PERFORMANS_FIX_RAPORU_2026-10-10.md`) scroll geri besleme döngüsünü ve
kalıcı shimmer'ı çözmüştü. Bu tur **veri yolundaki** ve **yükleme sırasındaki yeniden
kompozisyon** maliyetlerini hedefler.

## 2. Kök Nedenler

### A) Kaynağa göre değişen "ağ kuyruğu" maliyeti (yüklenirken kasmanın kaynağı)

| Kaynak | Sorun | Etki |
|---|---|---|
| **AniList** | 14 raf = 14 ayrı GraphQL isteği; hepsi `aniListMutex` içinde **700 ms aralıkla sıralı** | ~10–11 sn kuyruk |
| **MAL** | 13 raf JikanGateway'de **350 ms aralıkla sıralı** (~4,5 sn kuyruk) + per-raf `withTimeoutOrNull(5000L)` kuyrukta geçen süreyi de sayıyordu → **son raflar iptal/boş** | ~5–10 sn + eksik raflar |
| **Bangumi** | `withLatinBangumiNames` her raf için 20 subject detayı çekerdi; 14 raf paralel = **~56 eşzamanlı istek** → bgm.tv 429/retry döngüsü | dakikalarca takılma |
| **Kitsu / Shikimori / MAL / Bangumi** | Payload dönmeden önce vitrin için **5 × TMDB backdrop araması kritik yolu bloke ediyordu** | +3–10 sn |
| **Tümü modu** | "Yakında Yayında" şeridi her kaynakta AYNI AniList takvim sorgusunu çekerdi (**7 tekrar**, 700 ms kuyruğunda) | +5 sn |

### B) Yükleme sırasındaki yeniden kompozisyon fırtınası (çizim kare düşüşleri)

- `allSourceSections(states, showAdultContent)` **her kompozisyonda** yeniden hesaplanıyordu:
  `forSource()` 18 liste filtresi + kopya, her bölümde `distinctBy { exploreIdentity() }`
  string üretimi. LazyListScope içerik lambda'sı her `allSourceStates` / `heroBackdropOverrides`
  değişiminde (7 kaynak + ~12 backdrop) yeniden çalıştığı için bu onlarca kez tekrarlanıyor;
  sonuç listeleri her seferinde yeni kimlik aldığı için görünür tüm raflar/kartlar baştan
  kompoze oluyordu.
- `SourceHeader` başlık rozeti her kompozisyonda `flatMap + distinctBy` ile içerik sayıyordu.
- `filteredSourcePayload` her kompozisyonda 19 alanla yeniden üretiliyordu.
- "Yakında Yayında" kartlarındaki geri sayım **saniyede bir** tick atıyordu (15 kart = 15 timer).

## 3. Çözüm

| Dosya | Değişiklik |
|---|---|
| `AniListSearchClient.kt` | **`aniListExploreShelves`**: 14 keşfet rafı GraphQL **alias öbekleriyle 3 istekte** çekilir (6+6+2). Bir öbek patlarsa o raflar eski tekil yoldan tamamlanır. Yanıt `data.<alias>.media` → mevcut `parseAniListResponsePaged` ile parse edilir. |
| `JikanApiClient.kt` | `aniListExploreShelves` delegasyonu. |
| `ExploreViewModel.kt` | `loadAniListData` toplu sorguyu kullanır (tekil çağrılar fallback). MAL per-raf timeout **5 sn → 20 sn** (kuyrukta geçen süre artık rafları öldürmüyor). Kitsu/Bangumi/Shikimori/MAL yükleyicilerinden **bloke eden TMDB vitrin zenginleştirmesi kaldırıldı** — backdrop'lar `enrichHeroBackdrops()` ile arka planda çözülür. |
| `KitsugiAiringCalendarClient.kt` | `fetchUpcomingSchedule` için **90 sn TTL önbellek + uçuşta tekilleştirme**: Tümü modunda 7× yerine 1× AniList/TMDB takvim turu. |
| `KitsugiBangumiClient.kt` | `withLatinBangumiNames`: **önbellek-öncelikli** (bilinen Latin ad = 0 istek), **uçta subject tekilleştirme** (aynı kayıt raflar arasında tek çekilir), **küresel `Semaphore(3)`** (raf başına Semaphore(4) yerine). |
| `ExploreScreen.kt` + `TvAllSourcesHomeContent.kt` | `allSourceSections` sonuçları **`remember` ile önbelleklenir**; `allSourcesExploreSections` artık hesaplanmış bölümleri alır. `SourceHeader` içeriği önceden hesaplanmış sayımla çalışır. `filteredSourcePayload` `remember`'da. |
| `AllSourcesExplore.kt` / `AllSourcesExploreContent.kt` | İmza değişikliği: `allSourceHeroes(sections)` + uyumluluk overload'ı; `allSourcesExploreSections(sectionsByPlatform, distinctCounts, ...)`. |
| `ExploreComponents.kt` | Geri sayım tick aralıkları 1sn/10sn/60sn → **15sn/60sn/5dk** (metin zaten dakika çözünürlüğünde). |

## 4. Beklenen etki

- **AniList**: ~11 sn → **~2–3 sn** (3 istek × 700 ms + RTT)
- **MAL**: boş raf hatası kalktı; ~5–10 sn → kuyruğun doğal süresi (~5–8 sn), dolu raflarla
- **Bangumi**: 429 fırtınası kalktı; ilk açılış nazik akışla, sonraki açılışlar başlık önbelleği sayesinde hızlı
- **Kitsu/Shikimori**: +3–10 sn TMDB bekleme kalktı
- **Tümü modu**: takvim sorgusu 7→1; kaynak yüklendikçe kare düşüşü olmadan kademeli doluş
- **Kaydırma/etkileşim**: bölüm hesapları artık state değişimlerinde değil yalnızca veri değişiminde yapılır

## 5. Doğrulama notu

Sandbox'ta Android SDK/JDK bulunmadığından derleme çalıştırılamadı; tüm düzenlemeler
statik olarak doğrulandı (tip/imza/parantez dengesi, çağıran–çağrılan eşleşmesi,
`AllSourcesExploreTest` / `HeroSelectionTest` uyumluluğu overload ile korundu).
CI derlemesi + cihazda Keşfet (her kaynak + Tümü) turu önerilir.
