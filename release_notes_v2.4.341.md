# Kitsugi Beta v2.4.341

## 🇹🇷 Türkçe

### 🔑 MDBList API Kotası Artık Gerçek
- **Sabit "1.000 / 1.000" bitti:** Kota kutusu artık hesabın gerçek kalan istek sayısını gösteriyor. Değer, MDBList'in her yanıtta döndürdüğü `X-RateLimit-Limit` / `X-RateLimit-Remaining` / `X-RateLimit-Reset` başlıklarından ve `GET https://api.mdblist.com/user` uç noktasından okunuyor.
- **Yenile düğmesi:** Ayarlar → Entegrasyonlar → MDBList sekmesindeki "API Kotası" kartından tek dokunuşla sunucudan güncel değer alınır; sıfırlanmaya kalan süre canlı geri sayılır.
- **Doğru plan bilgisi:** Ücretsiz (1.000/gün) ve destekçi kademeleri (10.000–1.500.000/gün) sunucudan gelen `plan` ve `rate_limit` alanlarıyla gösterilir.
- **Detay sayfası rozeti:** "Harici Puanlar" başlığındaki MDBList rozeti artık gerçek kalan hakkı yazar (ör. `987/1.000`). Sunucudan doğrulanmış değer yoksa sayı uydurulmaz, rozet gizlenir.
- **Kota dostu istekler:** 429 yanıtlarında yeniden deneme kaldırıldı; her yeniden deneme günlük kotadan bir istek daha harcıyordu. Kota yenileme istekleri 15 dakikada bir ile sınırlandırıldı.

### 🧮 Sayım ve Kalıcılık
- Kota; sunucudan okunan kesin değer + o okumadan sonra uygulamanın yaptığı istekler olarak izlenir. Uç nokta başlık döndürmezse değer "tahmini" olarak etiketlenir, kesin gibi gösterilmez.
- Son değer API anahtarının SHA-256 parmak iziyle cihazda saklanır; uygulama yeniden açıldığında anında gösterilir. Anahtarın kendisi bu depoya yazılmaz.
- Gece yarısı (UTC) geçişinde kota otomatik olarak günlük limite döner; kullanıcı hiçbir şey yapmak zorunda değildir.

---

## 🇬🇧 English

### 🔑 Real MDBList API Quota
- **No more hard-coded "1,000 / 1,000":** the quota card now shows the account's actual remaining requests, read from MDBList's `X-RateLimit-Limit` / `X-RateLimit-Remaining` / `X-RateLimit-Reset` response headers and from `GET https://api.mdblist.com/user`.
- **Refresh button:** one tap on the "API Quota" card (Settings → Integrations → MDBList) reads the live value; the time until reset counts down in real time.
- **Correct plan information:** Free (1,000/day) and supporter tiers (10,000–1,500,000/day) are displayed from the server's `plan` and `rate_limit` fields.
- **Detail page badge:** the MDBList chip next to "External Ratings" now shows the real remaining quota (e.g. `987/1,000`) and stays hidden until an authoritative value exists — no invented numbers.
- **Quota-friendly requests:** retries on HTTP 429 were removed (each retry burned another daily request); quota refreshes are limited to once every 15 minutes.

### 🧮 Accounting and Persistence
- The quota combines the last authoritative server reading with the requests this app made afterwards. When an endpoint returns no rate-limit headers the value is labelled "estimated" instead of being presented as exact.
- The last value is stored per SHA-256 API-key fingerprint on the device and shown immediately after a restart; the key itself is never written to that store.
- At UTC midnight the quota rolls over to the daily limit automatically.

### 🧪 Tests
- `MdbListQuotaTest`: header parsing (including `RateLimit-*` variants, epoch-millis and delta-seconds resets), `/user` JSON parsing, local decrement, UTC rollover, 429 handling and the on-disk format.
