# PLAN_TASK — Keşfet Kaynak Yükleme & Kompozisyon Performansı (2. Tur)

**Tarih:** 2026-10-10 · **Dal:** `arena/26be3434-kitsugi-beta` · **Commit:** `87d7135`
**Kapsam:** Keşfet sayfasının TÜM kaynaklarında (AniList, MAL, Simkl/MyList, Kitsu, Shikimori, TMDB, Bangumi) kaynağa göre az/çok kasması.
**İlgili rapor:** `KESFET_KAYNAK_YUKLEME_PERFORMANS_RAPORU_2026-10-10.md`

---

## Plan (kök neden → çözüm eşlemesi)

| # | Kök neden | Kaynak | Çözüm |
|---|---|---|---|
| P1 | 14 raf = 14 ayrı GraphQL isteği, 700 ms kuyrukta sıralı (~11 sn) | AniList | GraphQL **alias toplu sorgu**: 6+6+2 = 3 istek; öbek patlarsa raf bazlı tekil fallback |
| P2 | 13 raf JikanGateway 350 ms kuyruğu + per-raf `withTimeoutOrNull(5000L)` kuyruktaki süreyi de sayıp son rafları iptal ediyordu | MAL | Timeout **20 sn**; raflar artık dolu gelir |
| P3 | `withLatinBangumiNames` raf başına 20 subject detayı × 14 raf ≈ 56 eşzamanlı istek → bgm.tv 429/retry cehennemi | Bangumi | Önbellek-öncelikli + uçuşta subject tekilleştirme + **küresel Semaphore(3)** |
| P4 | Payload dönmeden önce vitrin için 5 × TMDB backdrop araması kritik yolu bloke ediyordu | Kitsu/Shikimori/MAL/Bangumi | Blokaj kaldırıldı; `enrichHeroBackdrops()` arka planda çözer |
| P5 | "Yakında Yayında" ortak takvim sorgusu her kaynakta tekrarlanıyordu (Tümü modunda 7×) | Tümü | `fetchUpcomingSchedule` **90 sn TTL önbellek + uçuşta tekilleştirme** |
| P6 | `allSourceSections()` her kompozisyonda yeniden hesaplanıyor (forSource kopyaları + distinctBy string hesapları), sonuç listeleri kimlik değiştirince tüm raflar baştan kompoze oluyordu | UI | `remember` ile veri değişiminde bir kez hesaplanır; `allSourcesExploreSections` hesaplanmış bölümler alır |
| P7 | `SourceHeader` sayacı her kompozisyonda `flatMap+distinctBy`; `filteredSourcePayload` her kompozisyonda yeniden üretiliyordu | UI | Önceden hesaplanmış `distinctCounts`; `filteredSourcePayload` `remember` |
| P8 | Geri sayım kartları saniyede bir tick (şerit = 15 timer → sürekli yeniden kompozisyon) | UI | Tick 1sn/10sn/60sn → **15sn/60sn/5dk** (metin dakika çözünürlüğünde) |

## Task'lar (tamamlandı)

- [x] T1 — `AniListSearchClient`: `ExploreShelfSpec` + `aniListExploreShelves` (alias öbekleri, raf fallback)
- [x] T2 — `JikanApiClient`: `aniListExploreShelves` delegasyonu
- [x] T3 — `ExploreViewModel.loadAniListData`: toplu sorgu + tekil fallback (`shelf()`)
- [x] T4 — `ExploreViewModel.loadMalData`: timeout 5sn → 20sn (13 raf)
- [x] T5 — `ExploreViewModel`: Kitsu/Bangumi/Shikimori/MAL'den bloke eden hero-backdrop zenginleştirmesini kaldır
- [x] T6 — `KitsugiAiringCalendarClient.fetchUpcomingSchedule`: TTL önbellek + uçuşta tekilleştirme
- [x] T7 — `KitsugiBangumiClient.withLatinBangumiNames`: önbellek-öncelikli, tekilleştirilmiş, Semaphore(3)
- [x] T8 — `ExploreScreen` + `TvAllSourcesHomeContent`: bölüm/payload memoizasyonu (`remember`)
- [x] T9 — `AllSourcesExplore(Content)`: `allSourceHeroes(sections)` + `allSourcesExploreSections(sectionsByPlatform, distinctCounts)` imzaları (test uyumluluk overload'ı korundu)
- [x] T10 — `ExploreComponents.AiringSoonCountdownText`: tick aralıkları
- [x] T11 — Statik doğrulama (tip/imza/parantez dengesi, test referansları) + rapor

## Değişen dosyalar

```
KESFET_KAYNAK_YUKLEME_PERFORMANS_RAPORU_2026-10-10.md   (yeni — teşhis raporu)
app/src/main/java/com/kitsugi/animelist/data/remote/AniListSearchClient.kt
app/src/main/java/com/kitsugi/animelist/data/remote/JikanApiClient.kt
app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiAiringCalendarClient.kt
app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiBangumiClient.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/explore/AllSourcesExplore.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/explore/AllSourcesExploreContent.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/explore/ExploreComponents.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/explore/ExploreScreen.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/explore/ExploreViewModel.kt
app/src/main/java/com/kitsugi/animelist/ui/tv/home/TvAllSourcesHomeContent.kt
```

## Beklenen etki

- AniList: ~11 sn → ~2–3 sn · MAL: boş raf hatası kalktı · Bangumi: 429 fırtınası kalktı
- Kitsu/Shikimori: +3–10 sn TMDB bekleme kalktı · Tümü: takvim 7→1, kare düşüşü olmadan kademeli doluş

## Doğrulama / QA

- [ ] CI derlemesi
- [ ] Cihazda her kaynak + Tümü turu (ilk açılış + önbellekli açılış)
- [ ] MAL raflarının dolu geldiğini kontrol et
- [ ] Bangumi başlıklarının Latin adla geldiğini/sonradan düzeldiğini kontrol et

> Not: Bu oturumda sandbox'ta JDK/Android SDK yoktu; derleme çalıştırılamadı. Tüm düzenlemeler statik doğrulandı.
