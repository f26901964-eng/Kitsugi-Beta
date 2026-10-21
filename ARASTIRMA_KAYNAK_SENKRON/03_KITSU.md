# Kitsu — Araştırma Notları (2026-10-21)

## 1. Domain karmaşası (kritik — "wrong host = provider dead on arrival")
- Resmi dokümanlar hâlâ **`https://kitsu.io/api/edge`** diyor (Kitsu'nun kendi JSON:API docs + Apiary aynası):
  https://hummingbird-me.github.io/api-docs/ ; https://kitsu.docs.apiary.io/
- Yeni kod tabanları **`kitsu.app`** kullanıyor ve bunun resmi olup olmadığı doğrulanamıyor — EverythingBox #330 (2026-09):
  "The code says kitsu.app, published docs still say kitsu.io... a wrong host makes the whole provider dead on arrival rather than subtly wrong."
  https://github.com/cubman3134/EverythingBox/issues/330
- Kullanıcılar "kitsu logins dead mi?" döngüsü: r/animeapp "somehow now cannot log into app? Is it taken down?" (2025-09): https://www.reddit.com/r/animeapp/comments/1npslqs/
- **Risk:** domain lerden biri kapanırsa Kitsu sağlayıcısı tamamen ölü; Shikimori'deki domain-kaçak senaryosunun Kitsu versiyonu.

## 2. Proje canlılığı / bakım
- **Android uygulaması Nisan 2024'te kaldırıldı**, geliştirme durmuş sayılır — "Kitsu is effectively inactive... migrating not recommended in 2026": https://www.achriom.com/blog/anilist-to-kitsu/
- Gönüllü ekip yavaşlığı şikâyeti: "all of the devs are volunteers... updates don't go out nearly as often": https://www.reddit.com/r/AnimeAnonymous/comments/1mdiejl/
- Yine de API ayakta ve "tam işlevsel tracker" övgüsü alıyor (aynı thread + Taiga kullanıcıları).
- Doküman dönüşümü 2021'de takılmış: API Blueprint→OpenAPI #71 hâlâ "in progress": https://github.com/hummingbird-me/api-docs

## 3. API davranış tuzakları
- Alan adları depreşmiş durumda: `rating` → `ratingTwenty`; `mediaId`→`anime_id` filtreleri; statü `current/onHold` — resmi Apiary "DEPRECATED" damgaları: https://kitsu.docs.apiary.io/
- **`include=media` ile library-entries 500** dönen gerçek bug (Flexget #3483, 2022): https://github.com/Flexget/Flexget/issues/3483
- **`NotAuthenticatedError: You don't have permission to create this library entry`** — find-or-create sırasındaki gerçek şikâyet (MALSync #2705): https://github.com/MALSync/MALSync/issues/2705
- Hummingbird→Kitsu geçişi yıllarca Taiga'nın servisini öldürdü (Taiga #341): https://github.com/erengy/taiga/issues/341
- Eski `X-Client-Id` zorunluluğu/kafa karışıklığı (kitsu-tools #705): https://github.com/hummingbird-me/kitsu-tools/issues/705

## 4. Kitsu'nun başkalarını bozması
- Taiga→Kitsu→MAL zincirinde **Kitsu, MAL profilindeki tarihleri eziyor** + bir ara senkron kendiliğinden durdu; MAL şifresini Kitsu servisine verme güven sorunu — Taiga #601: https://github.com/erengy/taiga/issues/601

## 5. Kitsugi'deki karşılığı (kod denetimi)
- `KitsuApiClient`: `BASE_URL = https://kitsu.app/api/edge` + `OAUTH_URL = kitsu.app/...` → **EverythingBox'un uyardığı kampa giriyor; kitsu.io fallback'i YOK → açık madde.**
- Eşleştirme: find-or-create + **422 doğrulama-hatasını yeniden-yazma** (clamp) — MALSync #2705 sınıfındaki izni/çakışmayı karşılar.
- Tarihler: `startedAt` + `finishedAt` yazılır (`KitsuSyncManager` L166-167; `KitsuApiClient` L511) ✓ tam.
- Library-entry handle'ı lokal önbelleğe alınır (library-id cache) — tekrar-arama yok.
