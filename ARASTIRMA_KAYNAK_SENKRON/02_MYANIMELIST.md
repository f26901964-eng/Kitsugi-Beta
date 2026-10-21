# MyAnimeList — Araştırma Notları (2026-10-21)

## 1. API v2 modeli
- Liste yazma: `PATCH /anime/{id}/my_list_status` (status, score, num_watched_episodes, start_date, finish_date...), silme: `DELETE` aynı yol.
  Kaynak (3. parti OpenAPI derlemesi): https://jentic.com/apis/myanimelist.net/myanimelist
- **Resmi favori endpoint'i YOK** (MAL→AniList yönünde favoriler yazılamaz) — bigspawn/anilist-mal-sync "API Limitations" tablosu: https://github.com/bigspawn/anilist-mal-sync
- `start_date`/`finish_date` **yalnızca TAM tarih** kabul eder (`yyyy-MM-dd`) — yarım tarih reddedilir/seyrek kalır (Kitsugi kod notu ile uyumlu: `MalSyncManager` L20).

## 2. Rate limit: resmen YAZILMAMIŞ
- Resmi spec'te limit yok; gateway'de uygulanıyor. Uygulayıcıların ölçümü: **~1 istek/saniye** (kuru geçme, 429'da backoff).
  Kaynaklar: https://lib.rs/crates/myanimelist ("Rate limiting is unspecified in mal api, but in practice you should do no more than 1 query/s") ; jentic FAQ ("Stay below a few requests per second per client; bulk syncs sequentially").
- Toplu senkronlar paralel koşarsa 429 yer — "Sync appears frozen" çözümü: "bekle, --verbose" (bigspawn troubleshooting).

## 3. Tarihsel travmalar (ekosistemin neden kırılgan olduğunun kanıtı)
- **2018 Mayıs**: güvenlik açığı sonrası API "süresiz" kapatıldı → 3+ gün tamamen üçüncü parti uygulama ölü; Forbes: https://www.forums.com... (Forbes) https://www.forbes.com/sites/laurenorsini/2018/05/30/myanimelist-passes-third-day-of-unexpected-downtime/
  - Kullanıcı hafızası hâlâ canlı: "4-5 yıl önce API sorunu çoğu sync aracını bozdu" — r/MyAnimeList: https://www.reddit.com/r/anilist/comments/1mrptcq/
- **2024-03-31**: anormel istek artışı sonrası **tüm `/v2/*` endpoint'i geçici bloklandı**; sonra çoğu kaldırıldı ama `/v2/manga/*` bir süre kapalı kaldı — MAL forum duyurusu (tachiyomiJ2K #1763'te alıntılanmış): https://github.com/Jays2Kings/tachiyomiJ2K/issues/1763
  - 3. parti site "MAL API unavailable indefinitely" issue'si (2018): https://github.com/Carleslc/MyAnime/issues/1
- Periyodik "MAL API down" günleri (2024-01: "MyAnimeList api was down hence that wasn't working" — yukarıdaki #1763).

## 4. Jikan (MAL okuma aynası) — Kitsugi'nin de kullandığı katman
- **Salt-okunur** (kimlikli write yok), limit **60/dk, 3/sn**, kota MAL'den de gelebilir (429 ikisinden biri): https://docs.api.jikan.moe/
- Jikan cache'i 24 saat → yeni liste değişiklikleri bir gün gecikir (cognee #5505 notu: https://github.com/topoteretes/cognee/issues/5505).
- 2026: Jikan'ın MAL'e erişiminde sorunlar ("Jikan currently has a lot of issues specifically reaching the MAL website" — r/PleX: https://www.reddit.com/r/PleX/comments/1v1u36a/).

## 5. Uygulama seviyesi şikâyetler
- MAL 2018 hack + API kapatma → kullanıcılar AniList'e kaçtı (r/anilist "MAL got hacked" yorumları: https://www.reddit.com/r/anilist/comments/1mrptcq/).
- TachiyomiJ2K/Mihon deseni: tracker bağlantısı kırılır → logout/login; MAL API DOWN olayları bunlarla karıştırılıyor (#1763).

## 6. Kitsugi'deki karşılığı (kod denetimi)
- `MalSyncManager`: form-POST `PATCH my_list_status` + **progressKey** (tip-bazlı alan adı) + `DELETE my_list_status` (**404 sessiz-sayılır**: zaten listede yok) + tam tarih `start_date`/`finish_date`.
- Jikan sadece okuma (kimlik çözümleme/metadata) — write yok, Jikan_gateway üzerinden 60/dk bütçe ayrı yönetiliyor (`KitsugiApiBase` L36 yorumu: JikanGateway kotası bilinçli olarak ayrı).
- 429/5xx: `KitsugiApiBase` retryAfterMs + host bütçesi cezası.
