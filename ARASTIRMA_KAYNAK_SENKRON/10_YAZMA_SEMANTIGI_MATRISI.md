# Yazma Semantiği Matrisi — Kaynak Bazlı Kurallar (2026-10-21)
_Geliştirici hızlı-refaransı: bir alan yazıldığında kaynak NE YAPAR / NE YAPMAZ. Kaynaklar: 01-09 dosyalarındaki URL'ler._

| Kural | AniList | MAL | Kitsu | Shikimori | Simkl | Bangumi |
|---|---|---|---|---|---|---|
| **Puan ölçeği (write)** | `scoreRaw` 0-100 (ya da `score` = hesabın formatı; **iki kez çevirme!**) | 0-10 (0=puanı sil) | `ratingTwenty` 0-20 | 1-10 | 1-10 | 1-10 |
| **Puan-null semantiği** | **Alanı OMİT et; `null`→400; `score:0`=temizle** | `score=0`/parametre yok=temizle | rating 0=temizle | score alanı yok say / 0 | ratings/remove ayrı uç | rate 0 / ayrı temizleme |
| **Tarih yazımı** | FuzzyDate (tam-olmayan OK); `null` ekleme | **Yalnız tam** `yyyy-MM-dd`; okuma `0000-00-00` görebilir | `startedAt`+`finishedAt` (tıklama-anı eğilimi) | **İMKANSIZ** (parametre yok) | `watched_at` geçmiş-zamanlı yazılabilir | **Yok** (koleksiyon tarihi okunamıyor bile) |
| **Otomatik durum-geçişi** | 2026 kaynakları: finale kendiliğinden COMPLETED **yazmaz** (istemci yazmalı); 2018 changelog tersini iddia eder → **çelişkili, istemci yazdığına emin olsun** | Yarı-yayında `completed` isteği "watching"'a düşebilir (Simkl'de); MAL'de tamamlanınca bilinir | Durum yazımı library-entry ile | Durum + `rewatching` enum'u (pull eşlemesinde Watching'a düşer) | **history call listeyi otomatik taşır**; add-to-list sonra bunu ezer (Kitsugi'de bilerek EN SON = kullanıcı isteği); final-ping completed yapmaz | Site web'de yalnız "izleme"yken ilerleme değişimine izin verir; API'de ayrı episodes-ucu |
| **Alan-korunumu (kısmi write)** | **Belgesiz** — partial update test edilmeli (aniduel #3); bilinmeyen alan bırakılırsa eski değer kalır mı yoksa silinir mi muamma | PATCH部分 alan günceller (diğerleri kalır) — en net davranış | PATCH partial | PATCH partial (422 tip-tuzak) | Batch uçlar idempotent tasarımlı (20 sn kilit altında) | POST **upsert** (PATCH değil!); 202 döner |
| **Yeniden-yazma (idempotency)** | Aynı değer tekrar OK | Aynı değer OK | 422'ye kadar OK | find-or-create/tekrar-PATCH=no-op (anime-db #42) | Aynı history tekrar-reddi/rewatch kuralı var | Aynı bölüm tekrar "zaten izlendi" → atla (Bangumi-syncer) |
| **Watermark / delta güveni** | `updatedAt` var; **429/HTML tuzakları** | `my_last_updated` (export'ta) | progressedAt/updatedAt | updated_at var | `/sync/activities` **zorunlu gate** (yoksa throttling) | **`updated_at` puan/ilerleme yazımında GÜNCELLENMEZ → watermark yalan olur** |
| **Sayfalama** | 50/说-sayfa | limit/offset | offset | **N istek → N+1 dönebilir** | all-items 20k/60k üst sınırı | limit 1-50 (resmi) |
| **Belirsiz/eklenen enum** | GraphQL union hataları | — | statuslar sabit | **sunucu yeni enum eklerse tipli istemci kırılır** (String'e açık tut) | status seti sabit (5) | type 1-5 + subject 1/2/3/4/6 |
| **Yazma limiti (ölçülmüş)** | 90/dk doküman, **30/dk fiili** + burst; quota-bitince 429 | Yazılmamış ~1/sn (id) | Yazılmamış (422/500 gözlemi) | ~5/sn-90/dk topluluk | **20 sn/kullanıcı yazma kilidi** (400 rate_limit) | **~3/dk koleksiyon, ~10/10dk ilerleme** (topluluk ölçümü) + Cloudflare 503 |
| **Sessiz-başarı riski** | 429 HTML gövdesi parse edilemez | — | — | 200+"null" (oturumsuz) | **var-olmayan uçlara 200+null**; "push success ama yazılmadı" (scrob #453) | 403/429 "Bilinmeyen hata"ya karışmasın (Kitsugi ayrımı ✓) |

## Özel-durum notları
- **Rewatch:** AniList REPEATING+0; MAL rewatching flag+ep; Kitsu status; Shikimori `rewatching` status; Simkl `allow_rewatch` (PRO/VIP, yoksa ping yok sayılır); Bangumi yok (yeniden-izleme ayrı değil).
- **Aktivite temizliği:** AniList status/progress DEĞİŞMEDİYSE aktivite oluşmaz (changelog 2.6.1) — aynı-değer-itme güvenli.
- **Kapsam (scope) darlığı:** Shikimori'de not-senkronu için ek scope gerekir (forum: "API MAL требует отдельных прав"); MAL notları `comments` alanıyla ayrı.
- **Bozuk-satır direnci:** AniList `media:null`, MALSync `undefined` kayıt → satır-atla, listeyi çökertme (MALSync PR #4141).
