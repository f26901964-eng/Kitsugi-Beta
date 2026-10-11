# Kitsugi-Beta v2.4.255 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🚀 1. Detay Sayfasından Eklenti Video Hattı (Süre, CF Sabrı ve Tip Eşleştirme)

Detay sayfasındaki **İzle / Bölüm** butonuna basıldığında başlık + alternatif adlar + yıl + sezon/bölüm eklentiye gönderilir:
1. Çıplak isimle arama (`search`) — sorguya sezon/bölüm karışmaz.
2. İçerik sayfasını yükle (`load`).
3. **Film:** Sayfa URL'si doğrudan `loadLinks`.
4. **Dizi:** Bölüm listesinden S/E seçilir; yoksa sezon sayfasına inilir; sonra `loadLinks`.

#### Kök Nedenler & Çözümler
- **15 sn Arama Kutusu ve CF Çözüm İptali:** CloudflareKiller meydan okumayı görünce 90–120 sn'lik WebView başlatıyordu; 15 sn'lik arama kutusu patlayınca çözüm iptal oluyor, çerez hiç kaydedilemiyor ve eklenti pat diye boş dönüyordu.
  - **Çözüm:** Çerezi olmayan ve sağlıklı diye doğrulanmamış her host için 120 sn aşama ve 240 sn sağlayıcı sabır bütçesi tanındı (`needsCfPatience`). Sağlıklı siteler yine 1–3 sn'de döner, üst sınır beklenmez.
- **Detay İzle Hattında `searchWithCfRetry` Eksikliği:** Önceden sadece eklenti arama UI'sında çalışan bot koruması yeniden denemesi, detay İzle hattına bağlandı. İlk arama grubu boş dönüp CF kanıtı tespit edilirse gate açılıp sabırlı tek yeniden deneme yapılıyor.
- **Yanlış Tip Etiketi Yüzünden 1.00 Eşleşmelerin Reddedilmesi:** Yerli eklentiler dizi sonucunu `Movie` (veya filmi `TvSeries`) olarak etiketleyebiliyordu. Eşleştirici mükemmel başlığı bile tip yüzünden reddediyordu.
  - **Çözüm:** Güçlü başlık benzerliği ($\ge 0.85$) durumunda yanlış Movie/TvSeries etiketi elenmez (-0.20 ceza alır); hem film hem dizi varyantı varsa doğru tip kazanır, tek başına gelirse mükemmel başlık korunur.

### 🧪 2. Testler
- `CsTitleMatcherTest`'e yalnız Movie etiketli dizi sonucunun korunduğunu doğrulayan birim testi eklendi (`misTaggedExactTitleIsKeptWhenProviderOnlyReturnsTheOtherType`).

---

## 🇬🇧 English

### 🚀 1. Detail Page Provider Video Pipeline (Timeout, CF Patience & Type Matcher)

When tapping **Watch / Episode** from the detail page, clean title + aliases + year + season/episode are passed to providers:
1. Clean name search (`search`) — season/episode is not injected into the query.
2. Load content page (`load`).
3. **Movie:** page URL goes directly to `loadLinks`.
4. **Series:** select S/E from episode list; if not found, navigate to season page, then `loadLinks`.

#### Root Causes & Fixes
- **15s Search Window Aborting Cloudflare Resolution:** CloudflareKiller launches a 90–120s background WebView when challenged. The rigid 15s search window was timing out inside the challenge, aborting clearance and leaving no cookies.
  - **Fix:** Uncached/challenged hosts now receive 120s stage patience and 240s overall provider timeout (`needsCfPatience`). Healthy sites still return in 1–3s without waiting.
- **Missing `searchWithCfRetry` in Detail Pipeline:** The bot protection retry mechanism was previously only hooked into the manual search UI. Detail watch flow now triggers a patient CF retry when the initial batch returns empty with challenge evidence.
- **False Negative Rejection from Mis-tagged Media Types:** Many providers tag TV shows as `Movie` or vice versa. Previously, a 1.00 exact title match was discarded on type mismatch.
  - **Fix:** High similarity ($\ge 0.85$) matches are retained with a slight penalty (-0.20), allowing valid content with incorrect metadata tags to resolve smoothly while prioritizing correctly-typed siblings if available.

### 🧪 2. Tests
- Added unit test in `CsTitleMatcherTest` verifying mis-tagged exact titles are retained (`misTaggedExactTitleIsKeptWhenProviderOnlyReturnsTheOtherType`).
