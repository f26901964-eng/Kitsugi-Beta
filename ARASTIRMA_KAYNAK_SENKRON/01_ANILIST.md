# AniList — Araştırma Notları (2026-10-21)

## 1. Rate limit: doküman ≠ gerçek
- Resmi doküman: **90 istek/dakika** + ayrı bir burst limiter; aşınca 1 dakika timeout, `Retry-After`/`X-RateLimit-Reset` header'ları.
  Kaynak: https://docs.anilist.co/guide/rate-limiting
- **Gerçek canlı limit 30/dk** ve "bozuk halde (degraded)" yıllardır sürüyor — bağımsız doğrulamalar:
  - upbrr #255 (canlı API'de `X-Ratelimit-Limit: 30` ölçümü): https://github.com/autobrr/upbrr/issues/255
  - Mitchures PR #15: "AniList has been running degraded for a long time": https://github.com/Mitchures/animitchures/pull/15
  - apis.io kaydı: "90 requests per minute under normal conditions; **currently degraded to 30**": https://apis.io/providers/anilist/
- Resmi "Considerations" sayfası: ağır kesintilerde limiti düşüreceklerini / **API'yi tamamen kapatacaklarını** yazıyor:
  Kaynak: https://docs.anilist.co/guide/considerations
  - Kapatıldığında dönen şey: HTTP **403** + `"The AniList API has been temporarily disabled due to severe stability issues..."` (JSON).
  - Ayrıca **elle IP bloğu** uygulayabildiklerini yazıyor (tek IP'den aşırı istek).

## 2. Kapanma olayları (gerçek zaman çizelgesi)
- **2026-09-10**: herkese 403 — syncsekai #1 "No degraded mode when a tracker's API goes down": https://github.com/mr4r3n/syncsekai/issues/1
- **2026-10-21 (BUGÜN)**: PHP güvenlik açığı nedeniyle site+API kapatıldı, ETA yok — resmi Bluesky duyurusu: https://bsky.app/profile/anilist.co/post/3myf6gucn5i2f
- Sezon dönemi çöküşleri: "API issues occur at the end/beginning of new season... few hours" — r/anilist: https://www.reddit.com/r/anilist/comments/1s74bj0/
- 2024-03: "429'a oblivion'a kadar" şikâyeti: https://www.reddit.com/r/anilist/comments/1b42pjq/
- Trafik: 2,5 ayda API trafiği 2× (~36 milyon istek/gün) — nazo duyurusu (pesky.moe derlemesi): https://pesky.moe/links/2026-09-11-anilist-website-performance-issues-api-outages/
- Bölgesel Cloudflare sorunları: https://www.reddit.com/r/anilist/comments/1qf0l77/

## 3. 429 yanıt formatı tuzağı
- 429 gövdesi **JSON değil, Cloudflare HTML'i** gelebiliyor → istemciler "Unexpected token '<'" gibi saçma parse hataları üretiyor, limit hatası hiç görünmüyor.
  Kaynak: Mitchures PR #15 (yukarıda) + upbrr #255 ("silent fallout").
- Retry-After header'ı CORS'da okunamayabiliyor (expose listesi dar) → istemci kendi backoff'unu kurmalı (anipv PR öyle yapıyor: https://github.com/kazie/anipv/pull/3).

## 4. Token modeli
- **Refresh token YOK**; token ~1 yıl geçerli, elle yeniden auth gerekir — polyfin #394: https://github.com/moodiness/polyfin/issues/394
- Dönemsel 401 "Invalid token" (jellyfin-ani-sync #229 logları): https://github.com/vosmiic/jellyfin-ani-sync/issues/229
- Uygulama-tarafı standart çözüm şikâyeti: **çıkış yap → tekrar giriş yap** (Mihon deseni) — pawakalabs analizi: https://pawakalabs.com/2026/09/30/manga-reader-anilist-myanimelist-sync/

## 5. Liste/istatistik/tarih tuhafıkları
- **İstatistikler (bölüm sayısı) 24 saatte bir toplanır**, elle "Update Stats" butonu var — forum: https://anilist.co/forum/thread/19324 ; https://anilist.co/forum/thread/71872
- **start_date yoksa tamamlananlar istatistiğe sayılmıyor** — bu yüzden 3. parti araçlar yazılmış: eccos/aniListStartDateUpdater: https://github.com/eccos/aniListStartDateUpdater
- Liste boyutu sert limiti şikâyeti (eski 5000, sonra artırıldı ama sınırsız değil): https://anilist.co/forum/thread/77233
- 429/Mihon sabit döngüsü: https://anilist.co/forum/thread/71872
- Token/skor sorunu: r/anilist "stats not updating" (Saikou/Dantotsu): https://www.reddit.com/r/anilist/comments/1o0r71j/

## 6. Tek taraflı bağımlılık faciaları
- AnimeXTV: "AniList API gitti, 6 saat sitemiz gitti" → sonra MAL eklediler: https://www.reddit.com/r/StreamingAnime/comments/1th5w85/
- syncsekai #1: "AniList kapandığında sync + kapaklar da duruyor, azaltılmış mod yok, arayüz nedenini söylemiyor" (2026-09-10).

## 7. Kitsugi'deki karşılığı (kod denetimi 2026-10-21)
- `AniListSyncManager` L876-892: **"AniList'in fiili limiti 30 istek/dk"** yorumu + 429'da `Retry-After` (yoksa 60 sn) bekleme, 1-120 sn clamp, denemeli → bilinçli olarak güncel.
- `KitsugiApiBase` L171: "AniList şu anda kapalı, 429 limitinde ya da gateway kesintisinde mi?" ayrımı + global 429 cooldown (L126-134) + `5xx/429` için `retryAfterMs` (L249).
- Tarihler: `SaveMediaListEntry` değişkenlerine `startedAt`/`completedAt` gönderilir (L80-137) — sadece null değilse eklenir (ele-overwrite yok).
- 403-disable olayı: HTTP hatası → `SyncResult.errors` → bekleyen-senkron kuyruğuna girer (oturumda 5 deneme bütçesi) → API dönünce kendi kendine temizlenir.
- Kuyruk sadece `errors` üzerinden çalışır; kimlik-çözülemedi→skip ayrımı korunur (round 1-5 doğrulanmış).
