# Stüdyo / Yapımcı Detay: Yüklenememe + Yapım Listesi Sayfalama — 2026-10-10

**Dal:** `arena/45d08c3b-kitsugi-beta`
**Commit'ler:** `adf28da` (yüklenememe düzeltmesi), `a3a681c` (sayfalama)

## 1. Sorun

- MAL (Jikan/Tenrai) ve diğer kaynaklarda stüdyo/yapımcı sayfası açılmıyor, "Stüdyo detayları yüklenemedi" hatası veriyor.
- Yapım listesi yalnızca ilk sayfayı gösteriyor; 50'den fazla yapımı olan stüdyolarda devamı gelmiyor.

## 2. Kök nedenler (canlı doğrulama ile)

| Kaynak | Bulgu | Doğrulama |
|---|---|---|
| Jikan/Tenrai | Tenrai `limit` üst sınırı 50; uygulama `limit=80` gönderiyordu → 400, yapım listesi boş | `api.tenrai.org` canlı test |
| Jikan/Tenrai | Stüdyo bilgi/yapım istekleri tek denemede vazgeçiyordu (429/5xx'te sayfa düşüyordu) | Kod incelemesi |
| Jikan | `api.jikan.moe` doğrudan 500 döndürüyor; uygulama zincirde Tenrai/miribyou'ya düşüyor | `fetch_page` ile test |
| Şikimori | `/api/studios/{id}` tekil ucu 404 (id 1 ve 2 ile test) → stüdyo detayı hiç yüklenemiyordu | `shikimori.io` canlı test |
| AniList | Stüdyo sorgusu `perPage: 80` kullanıyordu (diğer sorgular 50) | Kod incelemesi |
| Tüm kaynaklar | Yapım listesi için sayfalama/sonsuz kaydırma yoktu | Kod incelemesi + API `page` testleri |

## 3. Yapılan değişiklikler

### 3.1 Yüklenememe (commit `adf28da`)
- `JikanGateway.kt`: Tenrai'ye giden isteklerde `limit` > 50 ise 50'ye indirilir (`TENRAI_MAX_LIMIT`). Diğer Jikan çağrılarındaki `limit=100` de bu sayede düzelir.
- `KitsugiStudioClient.kt`:
  - Jikan stüdyo bilgi ve yapım istekleri `executeGetRequestResilient` ile yapılıyor (429/5xx'te yeniden deneme).
  - Şikimori: tekil `/api/studios/{id}` çağrısı kaldırıldı; ad tıklanan chip'ten alınıyor, yapımlar `/api/animes?studio=` ucundan çekiliyor. Şikimori yer tutucu görselleri (`/assets/globals/missing_*`) yok sayılıyor.
  - AniList `perPage` 80 → 50.

### 3.2 Sayfalama / sonsuz kaydırma (commit `a3a681c`)
- `KitsugiModels.kt`: `KitsugiStudioDetail.hasMoreWorks`, yeni `KitsugiStudioWorksPage`.
- `KitsugiStudioClient.kt`:
  - `fetchStudioWorksPage(source, studioId, page)` — kaynak bazlı sayfa çekimi.
  - Jikan/Tenrai: `pagination.has_next_page` (canlı doğrulandı: `page=2` çalışıyor).
  - Şikimori: sayfa dolu ise (`>= 50`) devam var kabul edilir (canlı doğrulandı: `page=2` çalışıyor).
  - AniList: `pageInfo { hasNextPage }` (sorgu canlı test edilemedi).
  - TMDB: film ve dizi discover uçları `page` ile; `total_pages` ile devam kontrolü (canlı test edilemedi, API anahtarı gerekli).
  - Bangumi: sayfalama ucu doğrulanamadığı için tek sayfa olarak bırakıldı.
  - Ortak `STUDIO_WORKS_PAGE_SIZE = 50`.
- `JikanApiClient.kt`: `fetchStudioWorksPage` delegesi.
- `StudioDetailViewModel.kt`: `loadMoreWorks()` — sonraki sayfayı ekler, tekrarları ayıklar; `isLoadingMore` / `loadMoreFailed` durumları. Önbelleğe yalnızca ilk sayfa yazılır.
- `StudioDetailPage.kt`: Liste/grid sonuna yaklaşınca otomatik yükleme; alt ortada yükleme göstergesi; hata durumunda "Tekrar dene" düğmesi.

## 4. Değişen dosyalar

- `app/src/main/java/com/kitsugi/animelist/data/remote/JikanApiClient.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/JikanGateway.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiModels.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiStudioClient.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/StudioDetailPage.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/StudioDetailViewModel.kt`

## 5. Doğrulama durumu

- Canlı API testleri: Tenrai (`producers/314`, `anime?producers=`, `page=2`, `limit` sınırı), Şikimori (`/api/studios/{id}` 404, `/api/animes?studio=` ve `page=2`), Jikan doğrudan (500).
- **Yapılmadı:** Android derlemesi (Gradle bu ortamda indirilemiyor) ve cihazda çalışma testi. Yerelde `./gradlew :app:assembleDebug` ve cihazda MAL, Şikimori, AniList, TMDB stüdyo sayfalarının açılıp kaydırıldığının kontrolü gerekli.
- Doğrulanamayanlar: AniList ve TMDB sayfalama yanıtları, Bangumi stüdyo detayları.

## 6. Açık konular / riskler

- Yapım listesi her sayfada ağ isteği üretir; çok büyük stüdyolarda Jikan dakikalık limitine yaklaşılabilir. Yükleme yalnızca kaydırmayla tetiklendiği için kontrollü.
- Filtre (ör. "Film") seçiliyken eşleşme az ise uygulama eşleşme bulunana kadar sonraki sayfaları çeker.
- Bangumi için sayfalama ucu (`p1` `/persons/{id}/works` offset/limit) doğrulanmalı.
- Shikimori stüdyo açıklaması ve görseli artık gösterilmiyor (tekil uç 404 olduğu için zaten gelmiyordu); istenirse `/api/studios` listesinden önbellekli çekilebilir.
