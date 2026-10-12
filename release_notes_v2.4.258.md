# Kitsugi-Beta v2.4.258 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🐙 1. Bangumi: "Listeye hiçbir şey ekleyemiyorum" — Hata Artık Görünür ve Ölçülebilir

#### 1.1 Kök Neden (İstemci Tarafı)
- `BangumiApiClient.upsertCollection()` / `patchCollection()` / `markEpisodes()` istisnayı yutuyordu (`runCatching { … }` + `Boolean` dönüş) ve `BangumiSyncManager` her başarısızlıkta sabit `account_sync_unknown_error` metnini gösteriyordu.
- Sonuç: Ekranda yalnızca **"Bangumi güncellenemedi (…): Bilinmeyen hata"** görünüyordu; sunucunun gerçek yanıtı (401 / 400 / 403 `insufficient token scope` / 404 / 429 / 5xx) tamamen kayboluyordu.

#### 1.2 Yapılan Düzeltmeler
- **Gerçek Hata Metni:** Yazma uçları artık `BangumiApiException` fırlatır; `BangumiSyncManager.describeWriteFailure()` HTTP kodunu ve sunucunun ham `title`/`description` metnini kullanıcı mesajına ekler (ör. `insufficient token scope`, `tag 最短为两个字`).
- **401'de Otomatik Kurtarma:** `writeWithAuthRetry()` 401 alınca token'ı bir kez otomatik yeniler ve isteği tekrarlar; yenileme yoksa (kişisel anahtar) kullanıcıya "yeniden bağlan" uyarısı verir.
- **400 Hatasını Önleyen Süzgeç:** Sunucu kurallarına uymayan tek bir etiket/not tüm isteği reddettiği için etiketler (≥2 karakter, boşluksuz, görünmez karaktersiz, ≤10 adet) ve not (≤380 karakter, yazdırılabilir) gönderilmeden önce temizlenir. Boş etiket listesi (`tags: []`) sunucudaki mevcut etiketleri sileceği için artık `null` gönderilir.
- **Yan Etkisiz Yazma Testi:** Giriş diyaloğuna **"Bağlantıyı ve yazma iznini test et"** düğmesi eklendi. Test, `GET /v0/me` ile token'ı doğrular ve var olmayan bir yapıta boş `PATCH` atarak yazma kapısını ölçer: `404` = kapı **açık**, `403` = kapı **kapalı** (hiçbir kayıt değişmez).
- **Kişisel Erişim Anahtarı (PAT) ile Bağlanma:** Uygulama kaydı gerektirmeyen ikinci bağlantı yolu eklendi (`next.bgm.tv/demo/access-token/create`, 7–365 gün). Bu anahtarlarda Bangumi tarafında `scope` alanı boş kaldığı için sunucu tarafındaki JSON dizisi/nesnesi kapsam uyumsuzluğuna takılmaz.

---

## 🇬🇧 English

### 🐙 1. Bangumi: "Cannot add anything to list" — Failures Are Now Visible and Measurable

#### 1.1 Root Cause (Client Side)
- `BangumiApiClient.upsertCollection()`, `patchCollection()`, and `markEpisodes()` swallowed exceptions (`runCatching { … }` returning `Boolean`) and `BangumiSyncManager` printed a constant `account_sync_unknown_error` for every failure.
- Result: The UI only ever showed **"Could not update Bangumi (…): Unknown error"**; the server's real response (401 / 400 / 403 `insufficient token scope` / 404 / 429 / 5xx) was completely lost.

#### 1.2 Fixes & Additions
- **Real Error Text:** Write endpoints now throw `BangumiApiException`; `BangumiSyncManager.describeWriteFailure()` includes the HTTP status code and server's raw `title`/`description` in the error toast.
- **Self-Healing on 401:** `writeWithAuthRetry()` refreshes the token once and retries the request automatically.
- **Pre-Flight Sanitizing:** Tags (≥2 chars, no spaces, no zero-width chars, ≤10 count) and comments (≤380 chars, printable) are sanitized before dispatch. Empty tag arrays (`tags: []`) are converted to `null` to prevent remote tag erasure.
- **Side-Effect-Free Write Test:** Added **"Test connection and write permission"** button in login dialog: probes the write gate with an empty `PATCH` against a non-existent subject id (`404` = gate open, `403` = gate closed).
- **Personal Access Token (PAT) Login:** Added direct PAT support (`next.bgm.tv/demo/access-token/create`, 7–365 days) that bypasses OAuth app registration and avoids upstream JSON array scope mismatch issues.
