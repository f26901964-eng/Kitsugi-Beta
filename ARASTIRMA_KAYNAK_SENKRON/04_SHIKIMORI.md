# Shikimori — Araştırma Notları (2026-10-21)

## 1. Domain rotasyonu = kronik hastalık (araştırmanın en somut bulgusu)
- Zaman çizelgesi: `.me` → `.one` → `.io` (resmi yönlendirme zinciri) → **2026: `.tv`** (iddia, aşağıda kaynak-zayıf uyarısı).
- **Rusya engelleri (Ocak 2026):** Roskomnadzor `shikimori.one` (22-27 Ocak 2026) **ve** `shikimori.io` (Ocak 2026) engelledi; ekip domain değiştirmeye devam ediyor.
  Kaynaklar (önceki tur web_search sonuçları): dtf/meduza/ria.ntq — "Shikimori переехал..." ; shikimori.cc mirror sayfası: "current domain = shikimori.tv since Feb 2026".
  ⚠️ **Kanal-uyarısı:** `.tv` iddiası SEO-mirror kaynaklı; resmi kanal (tg/duyuru) doğrulanamadı → kodda "aday" olarak tutulmalı, tek-gerçek ilan edilmemeli.
- 2019'dan beri domain kovalamacası: 2019 `.tv`→`.one`, 2021 kısa süre `.cc` dönemi, kalıcı `.one`, sonra `.io` — shikimori.cc/wiki derlemesi.
- **Neden önemli:** bir domain ülke-ölçeğinde kapanınca o ülkedeki TÜM istemciler ölüyor; yeni domain yayınlandıkça eski kodlar kırılıyor.

## 2. Yönlendirme (redirect) tuzağı — Kitsugi'nin özel olarak çözdüğü şey
- OkHttp, host değiştiren 301/302'de **Authorization header'ını güvenlik için siler** → Shikimori'de `.one→.io` atlamasında whoami "oturumsuz" 200+null, liste uçları **403** döner; token geçerliyken bile.
  (Kitsugi `ShikimoriApiClient` içindeki dokümantasyon bu olayı birebir anlatıyor; `executeShikimori` yönlendirmeleri header koruyarak elle takip eder, `followRedirects(false)`.)

## 3. API semantiği tuzakları
- **`user_rates` oluşturma/update'te tarih parametresi YOK** — resmi doküman: create/update sadece `status/score/episodes/chapters/volumes/rewatches` alır.
  Kaynak: https://shikimori.io/api/doc/1.0/user_rates (v1) + v2 `POST /api/v2/user_rates` aynı kapsam (önceki turda shikimori.one/api/doc/2.0/user_rates/create doğrulandı).
  → Shikimori'ye "başlangıç/bitiş tarihi" yazılamaz; istemciler bunu ya atlıyor ya da `updated_at`/history üzerinden dolaylı tahmin ediyor.
- Rate limit: resmi sayı yok; topluluk ölçümü **~5 istek/sn, ~90/dk**, client-side throttle bekleniyor.
  Kaynak: https://www.freepublicapis.com/shikimori-api ; apis.io kaydı.
- Silme: `DELETE /api/v2/user_rates/{id}` (rateId handle gerekir) + toplu `DELETE /api/users/:id/history` (fetchAllUserRates ile).
- 3. parti kütüphaneler domain fallback'ini normalleştiriyor: "shikimori.io ИЛИ shikimori.one — ikinci domain sıradaki domain değişimine karşı bırakıldı" (npm sarmalayıcı notu).

## 4. Uygulama seviyesi
- 7/24 "reliability %97, hata %3" izleme kaydı (freepublicapis) — yani uçlar düzenli olarak takılıyor.
- Rusya'daki kullanıcılar 2026'da VPN'siz erişim için resmi mirror duyurularına bağımlı (mel.fm haberi: "giriş için domain (site-mirror address)").

## 5. Kitsugi'deki karşılığı (kod denetimi 2026-10-21)
- `BASE_URL = https://shikimori.io`, `LEGACY = .one` (yalnızca **token** uçlarında ikili deneme var; API uçlarında `.tv` adayı/otomatik geçiş **YOK** → açık madde, önceki turda uygulama planlanmıştı).
- Host bütçesi: `KitsugiApiBase` `.io/.one/.me` için 450ms/80-dk; **`.tv` bütçesi yok** (eğer `.tv`'ye geçilirse throttle'sız kalır).
- Yönlendirme-tuzağı çözümlü (yukarıdaki §2) ✓; tarih-yokluğu biliniyor ve kasıtlı olarak tarih gönderilmiyor (round-4 kararı: "Shikimori date params YOK").
- Kimlik çözümleme: `KitsugiIdResolver.fetchMalIdFromShikimoriApi` **doğrudan `https://shikimori.io`'ye** sabitlenmiş (L386) → domain kaçınca ARM'den sonraki geri-düşüş de kör olur → açık madde.
