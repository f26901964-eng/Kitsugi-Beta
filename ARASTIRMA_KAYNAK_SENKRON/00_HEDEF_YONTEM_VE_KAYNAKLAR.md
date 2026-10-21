# Kapsamlı Araştırma — Altı Liste Kaynağının Ekosistem Geneli Sorunları
**Tarih:** 2026-10-21 (İstanbul)  
**Talep:** Bu uygulamanın desteklediği altı kaynağı (AniList, MyAnimeList, Kitsu, Shikimori, Simkl, Bangumi) kullanan TÜM uygulamalarda liste senkronunun (ekleme/silme/düzenleme, tarih/puan/bölüm sayısı) neden "yarım yamalak" çalıştığının forumlardan, şikayetlerden, geliştirici notlarından araştırılması.

## Yöntem
- Web araması (forum/reddit/resmi doküman), GitHub issue madenciliği (`gh search`), resmi API dokümanı okuma (AniList docs, Simkl api.simkl.org, Jikan docs, Kitsu apiary/openapi, Bangumi api).
- Her bulgu KAYNAK URL'i ile not edildi. Çelişen bilgiler olduğu gibi yazıldı.
- Son adım: bulguların Kitsugi'deki karşılığı → `08_SISTEMIK_NEDENLER_VE_KITSUGI.md`.

## Dosyalar
| Dosya | İçerik |
|---|---|
| `01_ANILIST.md` | AniList API: gerçek rate limit, kapatma olayları, token, forum şikâyetleri |
| `02_MYANIMELIST.md` | MAL v2: limitsiz-gizli-limit, 2018 kapatma, 2024 endpoint bloklama, Jikan |
| `03_KITSU.md` | Kitsu: domain karmaşası (kitsu.app vs kitsu.io), bakımsızlık, 422/500 |
| `04_SHIKIMORI.md` | Shikimori: domain kaçakları (Rusya engelleri), yönlendirme-tuzakları, tarih yok |
| `05_SIMKL.md` | Simkl: 20 sn yazma kilidi, silme endpoint'inin OLMAYIŞI, otomatik liste taşıma |
| `06_BANGUMI.md` | Bangumi: Cloudflare 503, resmi-yazısız limitler, üç domain, 404 bug'ları |
| `07_UYGULAMALAR_VE_SIKAYETLER.md` | Uygulama seviyesi: MALSync, jellyfin-ani-sync, Taiga, Mihon... şikâyet arşivi |
| `08_SISTEMIK_NEDENLER_VE_KITSUGI.md` | Sentez: ekosistem neden kırılgan + Kitsugi'nin koruması + açık maddeler |

## Anahtar ölçüt (arşiv boyunca tekrar eden 8 sistemik neden — özet)
1. **Resmi-yazısız / yanlış-yazılmış rate limit** (AniList: doküman 90/dk, gerçek 30/dk).
2. **API'nin kendisinin kapanması / IP bloğu** (AniList 403-disable olayları, MAL 2018).
3. **Token modeli çöküşü** (AniList refresh yok; Mihon'da "çıkış-yapış" çözümü).
4. **ID haritalaması** — 6 ayrı veritabanı, bölüm sıralaması farklılıkları (Cinemeta/AniSync örneği).
5. **Tam silme endpoint'inin olmayışı** (Simkl watchlist-silme web-özel; MAL delete 404'e düşebilir).
6. **Uygulamaların birbirinin verisini bozması** (MALSync wiki: "başka sync aracı veriyi bozdu → sil tekrar ekle").
7. **Yanıt formatı tuzakları** (AniList 429 → Cloudflare HTML; Simkl var-olmayan yola 200-null döner).
8. **Domain rotasyonu / mirror bağımlılığı** (Shikimori, Bangumi aynaları, kitsu.app/kitsu.io).
