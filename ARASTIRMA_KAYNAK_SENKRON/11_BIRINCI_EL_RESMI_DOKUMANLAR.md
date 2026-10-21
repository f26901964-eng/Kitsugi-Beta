# Tur-3 — Birincil Resmi Kaynak Okumaları (2026-10-21)
_Yöntem: doküman sayfalarının kendisi okundu (fetch), madenlenmemiş repo issue'ları listelendi. Her madde birincil URL'li._

## A. Simkl — resmi `api-rules` + `rate-limits` (OKUNDU, tam sayılar)

1. **Saniyelik sert limitler:** **10 GET/sn ve 1 POST/sn** — `client_id` VE kullanıcı token'ı başına eşzamanlı.
   - GET aşımı → `429` (backoff). **POST aşımı → token/client_id üzerine GEÇİCİ THROTLE-BLOĞU; tekrarlayan aşım bloğu UZATIR.**
   - Cloudflare-önbellekli uçlar muaf (paralel istek sadece onlarda).
   - "50 öğe tek batch POST = 1 istek; tek tek = 50 istek → throttling."
   https://api.simkl.org/resources/rate-limits
2. **Aşımın cezası uyarı-sız:** "Apps that sustain overage get their **client_id suspended without warning, no appeal**."
   https://api.simkl.org/api-rules
   - Pratik kanıtı: NuvioDesktop PR #9 — kullanıcı **günlük kotayı doldurdu**; `date_from`'suz tüm kütüphane indirmeleri tekrarlanınca "client ids which keep re-downloading the whole library get suspended" (resmi referansı uyguluyorlar).
     https://github.com/UmbraProjects/NuvioDesktop/pull/9
3. **Günlük kota iki farklı sistem:**
   - **AUTH V2 = kullanıcı-başına:** Free **500/gün**, PRO **1.000**, VIP **10.000**. Kullanıcının **BAĞLI TÜM uygulamaları** aynı havuzu paylaşır → "senin uygulaman senkronlamıyor" sebebi başkasının yediği kota olabilir (Connected Apps sayfası bunu gösteriyor).
   - **AUTH V1 = client_id-başına:** tüm kullanıcılar tek havuz; havuz büyüme duvarı; V1 client_id'si çalınıp harcanabilir.
   - `X-RateLimit-Limit/Remaining`, günlük 429'da `Retry-After`. Çok-bölgeli dağılım yüzünden sayı "yaklaşık" — yayına göre yazılmalı.
4. **AUTH V2 (2026-09-18, BREAKING):** **V1 client_id yükseltilemez; V1 ~Nisan 2027'de emekli** (`/oauth2/*` V1'e `401 invalid_client`). Yeni kayıt: **PKCE S256 zorunlu**, `/oauth/*`→`/oauth2/*`, scope + refresh. V2'de anonim erişim daraltıldı (**arama bile token ister**). Günlük kota artık kullanıcı-başına (yukarıdaki tablo).
   https://api.simkl.org/changelog (2026-09-18 girişi) ; `Migrating from V1 to V2`
   - **Kitsugi denetimi:** `ExternalAuthManager` hâlâ **V1 akışları** (`api.simkl.com/oauth/token`, `/oauth/pin` V1 client_id/secret) → **Nisan 2027 göçü açık madde (A15).**
5. **Sahte-başarı #2 (resmi itiraf):** premium-özelliklere `HTTP 200` + gövdede `{"error":"premium_only"}` döner; `items` yok. "Status'a branching yapan istemci gerçek içerik gibi render eder" → **200'de `error` anahtarı TARA.**
   https://api.simkl.org/changelog (2026-09-18 custom-lists)
6. **Custom lists beta tuhafıkları:** bucket `favorites` (çoğul) vs alan `favorite` (tekil); `auto` listesi içerik yenilenince **hareket etmez** (ayar değişince oynar) → günlük kendi takviminle tazele; rastgele-sıralı auto listeler `400`.
7. **Tarih/saat dilimi resmi kural sayfası (OKUNDU):**
   - Tüm kullanıcı-zaman damgaları **UTC (ISO-Z)**; gösterim kullanıcı-profil saat dilimine çevrilir.
   - **`watched_at ≈ 1970-01-01T00:00:01Z` = "Çok uzun zaman önce / hatırlamıyorum" YER-TUTUCUSU**; tespit kuralı resmen: **2000-01-01 öncesi her damga = bilinmeyen-tarih** (ekran'a 1970 basma!).
   - **"İstemciler sık sık yerel saati UTC diye etiketliyor (tersi de) → her yazımda 1-12 saat kayma"** — resmi olarak client-tarafı tarih bug'ı tanınmış.
   - Rewatch: aynı episode 48 saat içinde tekrar yazımı **aynı oturuma birleşir** (sayaç artmaz); 48 saatten farklı yeni tarih → oturum KOPARILIR ("offline uygulamaların uzun süreden sonra yakalama" senaryosu).
   - Letterboxd içe aktarımda UTC-gece-00:00 → **1 gün kayma** şikâyeti (support forumu).
   - Nisan 2026: DST düzeltmesi (watch dates 1 saat kayıyordu).
   https://api.simkl.org/conventions/dates ; /guides/rewatches ; /guides/sync
8. **Changelog son günceller (OKUNDU):** `/search/all` (2026-10-18); `finale_type` (10-14); `?min_progress=50-100` scrobble eşiği (10-09, varsayılan 80); yanlış `sort` → `400 wrong_parameter`; `episode_runtime` (10-03); custom-list `role`; Apiary Ekim-2026'da kapanıyor; **asıl duyurular Discord `#api`** (kapalı-duvar — bkz. Sınırlar).
9. **Slug ≠ kimlik:** slug'lar tekrarlı (üç Superman aynı slug); `ids.simkl` vs `ids.simkl_id` uç-ailesine göre ad değişir → cache'i slug'la kurma.

## B. AniList — resmi hata-dokümanı (OKUNDU)

1. **HTTP 200 gelse de `errors[]` gelebilir** — her yanıtta `errors` alanını TARA; validation hatalarında `validation` alanı (örnek: `"The score may not be greater than 100."`) kullanıcıya gösterilebilir.
   https://docs.anilist.co/guide/graphql/errors
   (Kitsugi: GraphQL errors ayrımı round 1-5'te mevcut ✓.)
2. **Resmi doküman-reposu issue'ları KAPALI** ("has disabled issues") → hata-raporu kanalları Discord/Reddit'e sıkışmış (araştırma sınırı).
3. **Sayfalama-kopyası bug'ı (dış analiz, Eki-2026'da düzeldi):** `sort` non-unique alanlarla (score, updatedAt) yapılıp sayfa değiştirince **aynı satır iki kez / satır atlama** — sıralamaya `MediaId` tiebreaker'i şart (anilist_moe #19).
   https://github.com/Thunder-Blaze/anilist_moe/issues/19
   - **Kitsugi denetimi:** `AniListImportManager` sorgusu **`sort: [MEDIA_ID]`** ✓ — bu tuzağa zaten düşmüyor.

## C. Kitsu — kendi repo madenleri

- `kitsu-tools #809` **"Syncronization with MAL not working" (AÇIK)** ; `#803` "Number of data and included entries mismatch" (JSON:API sayfa/`included` tutarsızlığı) ; `#791` auth endpoint 500 ; `#792` 406.
  https://github.com/hummingbird-me/kitsu-tools/issues/809 ; /803
- `api-docs #96` **"Issue with NSFW anime in library entries" (AÇIK)** — kütüphane uçlarında NSFW davranışı; `#84` OpenAPI ilişkileri hâlâ açık; `#71` Switch-to-OpenAPI3 **kapandı** (dönüşüm kısmi — düzeltme: "stalled" ifadesi "kısmi-tamamlandı" olmalı); `#113` ticari-kullanım şartları.
  https://github.com/hummingbird-me/api-docs/issues/96

## D. Bangumi — `bangumi/api` issue madeni (Çince birincil)

1. **`#219`: "api isteği başarısız olunca JSON yerine HTML dönüyor; başarılı olunca JSON"** — hata-yanıtı formatı tutarsız (aynı AniList-429-HTML sınıfı!).
   https://github.com/bangumi/api/issues/219
2. **`#268`: 560988 numaralı anime YANLIŞ NSFW değeri döndürüyor** (yetişkin-flag veri bug'ı — Kitsugi adult-flag katmanı bu yüzden veri-güvenilir-riski görür).
3. **`#212`: resmi dokümanda yanlış status code yazımı** (Typo).
4. Sayfalama/alan boşlukları: `#242` episodes offset sınırı (-1 olmalı), `#257` bölümleri ters-sırayla çekme, `#236/#215` collections'a tag-süzgeç/sıralama yok, `#214` collections'a NSFW alanı yok, `#296` 3. parti için yeni-kayıt desteği yok.
5. `bangumi/server` repo araması `rate limit/429` boş döndü → **resmi sınır-issuesu yok; limit sayıları yalnız topluluk ölçümü** (09.md §F1 ile tutarlı).

## E. Araştırma SINIRLARI ( dürüst listeler — nerelere GİREMEDİK )

- **Simkl asıl duyuru kanalı Discord `#api`** (changelog sayfası böyle diyor) — kapalı; X/@Simkl ikincil.
- **AniList**: docs-repo issue'ları kapalı; canlı olaylar Reddit/Discord + pesky.moe derlemeleri.
- **MAL**: resmi changelog/doküman-reposu YOK; forum topic'leri (2143705) yalnız 3. parti alıntılarla okunabildi.
- **Shikimori**: resmi-kulüp konuları okunabildi (clubs/950) ama forum araması kısmi; `SevereCloud/shikimori` = doküman-ayna issue'ları (uç-başına #113-137).
- 404/taşınmış thread'ler, silinen Reddit yorumları erişilemez.
