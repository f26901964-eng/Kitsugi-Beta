# Liste Kaynağı Hata Kodu Sistemi — 2026-10-11

## Amaç

Anime / dizi / film **liste kaynaklarında** (AniList, MyAnimeList, Kitsu, Shikimori,
Bangumi, TMDB, Simkl) oluşan **bilinen ve bilinmeyen tüm hataların** sabit bir kodu,
kısa bir başlığı ve "**neden çıktı / ne yapmalı**" açıklaması olsun. Böylece kullanıcı
"hata aldım" demek yerine "**şu kaynakta, şu nedenle, şunu yapmalıyım**" diyebilir.

Açıklamalar **dil dosyasına** yazıldı: `values/strings.xml` (TR) ve
`values-en/strings.xml` (EN). Cihaz hangi dildeyse o okunur; ek çeviri katmanı yok.

## Kod şeması

```
KTS-<PLATFORM>-<NNNN>
```

| Ön ek | Kaynak | Aralık | İçerik |
|-------|--------|--------|--------|
| `COM` | Ortak (tüm platformlar) | 1001–1025 | Ağ, DNS, TLS, timeout, 4xx/5xx, oturum, token, scope, ayrıştırma, kimlik koruması, cooldown |
| `ANL` | AniList | 2001–2007 | Kısıtlı mod (30/dk), GraphQL 429 + burst limiter, 500, oturum, gizli liste, puan formatı, kayıt yok |
| `MAL` | MyAnimeList + Jikan | 3001–3007 | Jikan kotası (3/sn, 60/dk), resmi API 429, bildirim ucu yok, gizli liste, 404, Jikan bakım, oturum |
| `KIT` | Kitsu | 4001–4004 | Host değişikliği, 429 (60/dk + burst), kütüphane kaydı yok, JSON:API ilişki hatası |
| `SHK` | Shikimori | 5001–5006 | 5 istek/sn, `messages` izni, `redirect_uri` hatası, 422, bot filtresi, oturum |
| `BGM` | Bangumi | 6001–6006 | `nsfw` 400 tuhaflığı, 401, 404, offset sınırı, subject type, User-Agent zorunluluğu |
| `TMD` | TMDB | 7001–7004 | v3/v4 anahtar karışıklığı, dil fallback, 404, 429 |
| `SMK` | Simkl | 8001–8005 | PIN süresi, PIN doğrulanamadı, client_id, 1 istek/sn, boş takvim |

**Toplam 64 kod**, her biri için 3 metin (`_title`, `_why`, `_fix`) → **227 yeni string
anahtarı × 2 dil = 454 kayıt**. TR ve EN anahtar kümeleri birebir aynı (doğrulandı:
4461 / 4461).

## Yeni dosyalar

- `data/platform/ListPlatform.kt` — platform enum'u + kelime bazlı etiket eşleştirme
  (`"Analiz"` yanlışlıkla AniList'e bağlanmaz).
- `data/platform/PlatformErrorCode.kt` — 64 kod; `severity`, `retryable`,
  `titleRes/whyRes/fixRes`; `fromCode()` / `forPlatform()` / `applicableTo()`.
- `data/platform/PlatformErrorCatalog.kt` — sınıflandırıcı + metin çözücü:
  - `classify(platform, error, httpCode, rawMessage)` → kod
  - `classifyMessage(platformLabel, message, details)` → günlük satırları için
  - `explain()/explainCode()` → başlık + neden + çözüm + önem + yeniden deneme bilgisi
  - `bilingual()` → raporlar için TR **ve** EN blok
  - `shortLabel()`, `reportSection()`, `PlatformHttpException`

## Entegrasyon noktaları

| Yüzey | Ne değişti |
|-------|-----------|
| Eşitleme günlüğü | `CrossSyncLogEntry.errorCode` alanı; `AuthViewModel.logEvent` çağrı yerlerini tek tek değiştirmeden hata/uyarı satırlarını otomatik sınıflandırır |
| Eşitleme diyaloğu | Satır altında kod çipi; açıldığında **neden / ne yapmalı / önem / yeniden deneme** bloğu |
| Rapor (paylaşım) | Satırlarda `Kod:` + açıklama; sonda "=== HATA KODLARI (n) ===" bölümü, her kod TR + EN |
| Bildirim teşhisi | Başarısız her platform satırına kod atanır ve kart içinde açıklaması gösterilir |
| Giriş diyalogları | Shikimori / Bangumi / Kitsu hata kutusunda kod + neden + çözüm satırı |
| Ayarlar → Çapraz Platform | "Hata Kodu Rehberi" — 64 kodun tamamı, arama + platform filtresi ile |

## Yeni kod ekleme

1. `error_table.py` benzeri tabloya kaydı ekleyin (`key`, `platform`, `severity`,
   `retryable`, TR ve EN `title/why/fix`).
2. Üretici, metinleri `values/` + `values-en/` içine `<string name="pf_err_<key>_title">`
   biçiminde yazar ve `PlatformErrorCode.kt` içindeki enum'u yeniden üretir.
3. EN ve TR dosyalarında anahtar kümelerinin eşit olduğunu doğrulayın.

## Notlar

- Sınıflandırma sırası: **platforma özgü imzalar → ortak HTTP kodları → istisna/mesaj
  kalıpları → `COM_1020` (sınıflandırılamayan)**. Hiçbir hata kaybolmaz; eşleşmezse
  ham ayrıntı gösterilir.
- Sınıflandırılamayan bir **uyarıya** yanıltıcı kod iliştirilmez; **hatalar** kodsuz kalmaz.
- Derleme bu ortamda yapılamadı (JDK/Android SDK yok); doğrulama CI'da
  `assembleFossRelease` ile yapılır.
