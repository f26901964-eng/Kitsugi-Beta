# Uygulama Seviyesi Şikâyet Arşivi — "Senkron hep yarım yamalak" (2026-10-21)

Amaç: altı kaynağı kullanan GERÇEK uygulamalarda kullanıcıların/geliştiricilerin neye yandığını kanıtlarıyla toplamak.

## 1. MALSync (tarayıcı uzantısı — 4 kaynağa birden yazan en büyük open-source senkron)
- Changelog tarihi bir "senkron-kırık" arşivi (malsync.moe):
  - "BUGFIX Fix MAL manga updates failing #343"
  - "BUGFIX Fix MAL start date sometimes not set"
  - "BUGFIX Fix Anilist bookmarks sending too many api requests"
  - "BUGFIX Fix canceling rewatching setting episode to 1"
  - "BUGFIX Episode sync not working"
- **Troubleshooting wiki — ekosistemin en itirafkar cümlesi:**
  "MyAnimeEntry / Anilist / Kitsu / Simkl entry is not correct → **Data could be corrupt through another sync utilities. Please remove it from Mal and re add it.**"
  (Başka bir sync aracı veriyi bozduysa sil-ve-yeniden-ekle!) https://github.com/MALSync/MALSync/wiki/Troubleshooting
- Issue arşivi: Kitsu'da "permission to create this library entry" (#2705), AniList/Kitsu auth kırılmaları (#89), gogoanime episode sync her zaman başarısız (#251).
- "MAL ve Kitsu'yu AYNI ANDA güncelleyebilir miyim?" tartışması (#71) → çift-yazma çakışması tarihsel sorun.

## 2. jellyfin-ani-sync (Jellyfin ↔ MAL/AniList — alan-senkronda en zengin issue listesi)
https://github.com/vosmiic/jellyfin-ani-sync/issues
- #229: "Episodes aren't synced to Anilist due to a bad request" — MAL yazımı BAŞARILI, AniList `Invalid token 400` (token döngüsü).
- #186: "New anime does not get synced to myanimelist" — "kullanıcı authenticated değil, Skipping" sessiz atlamalar.
- #234: "Plan to Watch seçeneği yok → eklenecek anime yoksa güncelleme ATLANIYOR" (ekleme-silme akışının kopukluğu).
- #202: MAL'den fazla bölümü olan anime → yanlış rewatch durumu; #201: TvDB vs AniList sezon yapıştırma farkı.
- hepsinin ortak teması: **id-mapping + status-geçiş + token**, üçü bir arada kırılıyor.

## 3. Taiga (masaüstü — en eski istemci)
- #341: Hummingbird→Kitsu geçişi "in a couple of days Taiga's service will stop working" (platform-isim değişimi bile istemcileri öldürüyor).
- #601 "Life after MyAnimeList": "Kitsu, MAL'deki **tarihlerini eziyor**; bir ara MAL profilimi güncellemeyi kesti, yeniden sync lazım; MAL şifresini Kitsu'ya veriyorsun (güven)."

## 4. Mihon/Tachiyomi ekosistemi (mobil okuyucular — milyonlarca kullanıcı)
- Desen: "tracking birkaç hafta çalışıyor, sonra chapters AniList'e gitmiyor; çözüm: çıkış-yap-giriş-yap, sonra tekrar" — pawakalabs analizi (429/403 + token süresi): https://pawakalabs.com/2026/09/30/manga-reader-anilist-myanimelist-sync/
- tachiyomiJ2K #1763: "MyAnimeList bağlanmıyor" → sebep **MAL API'nin tüm `/v2`'si bloklandı** (uygulama sucsuz; API suçlu) — kullanıcıya yansıyan şey "senkron bozuk".
- r/anilist forum 71872: Mihon'da sürekli 429; "buglar API tarafında".

## 5. ID-mapping / bölüm-sırası faciaları (alan eşleşmesi neden kırılıyor)
- AniSync (Stremio): "Cinemeta(IMDb/TMDB) üzerinden sync **MAL geçmişini BOZAR** — MAL her arcs'ı ayrı anime sayar, bölüm numaraları hizalanmaz; sadece Kitsu-ID üzerinden güvenli": https://www.reddit.com/r/StremioAddons/comments/1tyslue/
- NuvioMobile #1526 (özellik talebi gerekçesi): "AniDB sezon/split-cours/OVA farkları → yanlış bölüm ilerlemesi, kırık sezonlar, duplicate kayıtlar... indirect yöntemler title-matching/sync'te patlıyor": https://github.com/NuvioMedia/NuvioMobile/issues/1526
- bigspawn/anilist-mal-sync çözüm tablosu (open-source en dikkatli tasarım):
  - Çakışma kuralı: **kaynak servis her zaman kazanır** (source wins) — https://github.com/bigspawn/anilist-mal-sync
  - "nil kaynak-tarihi asla hedef-tarihüzerine yazmaz"; finish_date sadece Completed'da.
  - "Sync dondu → İKİ servis de rate-limitli; bekle" (built-in bilinen).
  - ARM (`arm.haglund.dev`) + Jikan ile ID mapping — **Kitsugi ile aynı harita-stack'i**.
- eccos/aniListStartDateUpdater: AniList'te start_date'siz tamamlananlar sayılmıyor → MAL XML'den tarih düzeltme aracı icat edilmiş.

## 6. Token/oturum ömrü şikâyetleri
- polyfin #394: "AniList tokenları 1 yıl sürer ve **refresh edilemez**; süre dolunca bağlantı `Connection stopped` olarak raporlanır"; MAL = PKCE (refresh var): https://github.com/moodiness/polyfin/issues/394
- alibaba-lifeTips: "AniList 90 günde veya şüpheli aktivitede token revoke eder — aylık rotasyon önerisi" (ikincil kaynak): https://lifetips.alibaba.com/tech-efficiency/streamline-anime-watchlists-across-mal-anilist-kitsu
- anime tracker app (r/animeapp): "sync layer bug'ı AniList kullanıcılarını vuruyor → disconnect/reconnect" (2026-01): https://www.reddit.com/r/animeapp/comments/1qnkqet/

## 7. "Aşağı-bağımlılık" faciaları (tek API'ye gömülen ürünler)
- AnimeXTV: "6 saatlik kesintinin kaynağı **AniList API'nin kendisi**; platform ona gömülüydü → site çöktü; şimdi MAL sync eklediler": https://www.reddit.com/r/StreamingAnime/comments/1th5w85/
- Stremio AniSync yazarı: "ayrı addon'lar gidiyordu, sync kırılıyordu, manuel düzeltiyordum — unified 'just works' istedim".
- r/anilist "Is Anilist API still down? Can't use any tracking app" (2024-11): API kısıtı yüzünden **uygulamaların yarısı çalışmıyor** algısı: https://www.reddit.com/r/anilist/comments/1gnu0yx/

## 8. Yeni-senkron araçlarında bile aynı hatalar (2026 kanıtı)
- AL-Additions #8: auto-sync, manga hiç senkronlanmamışsa **anime senkronunu da hiç başlatmıyor** (Math.min tuzağı).
- syncsekai #1: "tracker API'si çökünce degraded mod yok, arayüz nedeni söylemiyor" (2026-09-10).
- CrossWatch #1334/#1316: Simkl'e episode-history YAZILIYOR ama okumada görünmüyor / `simkl_not_found:episodes` hataları (2026-10).
- anisync #16: "Simkl, final bölüm ping'inde Completed'a geçmiyor (MAL/AniList'in aksine) → seriler Watching'da takılıyor" (2026-09).
- Silo-Server #5/#7/#2426: silmeler her senkronda GERİ GELİYOR; add-to-list Watching'ı PlanToWatch'a taşıyor (2026-10).
