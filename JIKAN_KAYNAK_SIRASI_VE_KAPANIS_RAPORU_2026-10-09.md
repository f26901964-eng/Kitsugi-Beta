# Jikan Durumu ve MAL Kaynak Sırası — 2026-10-09

**Dal:** `arena/6d256183-kitsugi-beta`

## 1. Jikan neden çalışmıyor?

- Jikan (`api.jikan.moe`) kamuya açık API'si kapatma sürecindedir. Duyurulan takvim: 14 Haziran 2026 bakım modu, 1 Eylül 2026 kesintili çalışma, **1 Ekim 2026 tamamen kapatma**. Bu tarih bugünden (9 Ekim 2026) önce geçmiştir.
  - Kaynak: [Jikan kapanış duyurusu — Ervie/jikan.net](https://github.com/Ervie/jikan.net) (dormancy notu)
  - Kaynak: [rubb3rDucc/showshowshow #221](https://github.com/rubb3rDucc/showshowshow/issues/221) — 6 Ekim 2026 itibarıyla API host'u bağlantıyı reddediyor; `jikan.moe` ve `docs.api.jikan.moe` ise açılıyor.
  - Kaynak: [jikan-me/jikan-rest #612](https://github.com/jikan-me/jikan-rest/issues/612) — 504 hataları (Ağustos 2026'dan itibaren).
- Sandbox'tan `api.jikan.moe` erişimi allowlist dışında olduğu için canlı test yapılamadı. Sonuç dış kaynaklara dayanıyor.
- Uygulama tarafında ek bir sorun da bulundu: `RetryInterceptor` Jikan isteklerini `JikanGateway` kota kapısının **dışında** en fazla 2 kez daha gönderiyordu. Bu hem dakikalık limiti gizli şekilde tüketiyor hem de 429 oranını artırıyordu.

## 2. Yapılan değişiklikler

| Dosya | Değişiklik |
|---|---|
| `data/remote/JikanSearchClient.kt` | Keşfet (Top/Airing/Upcoming/Trending/Movie/Publishing/Completed/Newly added/Seasonal), `search`, `searchMALOnly`, `searchMalAdvanced`: **sıra = resmi MAL v2 → Jikan → AniList**. |
| `core/network/KitsugiHttpClient.kt` | `jikanClient` eklendi: `RetryInterceptor` içermez. |
| `data/remote/JikanGateway.kt` | `jikanClient` kullanılır. Devre kesici eklendi: 3 art arda sunucu/ağ hatasında Jikan 5 dk boyunca istek atmaz (her ekranın zaman aşımı beklemesini önler). |

### Kaynak sırası (özet)

- **Genel:** 1) Resmi MAL v2 → 2) Jikan → 3) AniList.
- **MAL'in vermediği veriler** (karakter, ekip, ilişki, bölüm, stüdyo, istatistik vb.): 1) Jikan → 2) AniList. Bu bölümlerin mevcut kodu zaten bu sırayla çalışıyordu; değiştirilmedi.
- MAL detay ana alanları (`KitsugiMalDetailClient.fetchDetail`) zaten önce resmi MAL v2'den çekiliyordu.

## 3. Doğrulanmadı / dikkat

- Android derlemesi bu ortamda yapılamadı (JDK/Gradle yok). `./gradlew :app:assembleDebug` ile derleme gerekli.
- Jikan public API'si kapandığı için **Jikan adımı büyük olasılıkla boş dönecek**. Bu durumda MAL verisi resmi MAL v2 ve AniList'ten gelir. Karakter/ekip gibi MAL'a özgü veriler kısmen eksik kalabilir.
- Uzun vadeli çözüm için kaynak sağlayıcısının değiştirilmesi gerekir (ör. Tenrai, resmi MAL v2'ye ağırlık verme, veya kendi Jikan örneğini barındırma). Bu bir ürün kararıdır.
