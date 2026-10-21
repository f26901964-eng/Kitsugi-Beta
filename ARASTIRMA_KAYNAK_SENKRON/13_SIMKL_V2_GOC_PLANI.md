# Simkl AUTH V1 → V2 Göç Planı (A15)

> Kaynak: api.simkl.org/changelog (2026-09-18), api.simkl.org/resources/rate-limits — tur-3
> birinci-el derlemesi (`11_BIRINCI_EL_RESMI_DOKUMANLAR.md` §A2-3). Bu belge GÖÇ'ün tek
> uygulama-kılavuzudur; durum satırları iş bitince güncellenir.

## Neden şimdi? (son tarih)

- Simkl V1 client_id'leri **~Nisan 2027'de emekli** olacak: V1 ile alınan token'lar
  `/oauth2/*` uçlarında 401 dönecek (changelog 2026-09-18).
- Kitsugi hâlâ V1'de — `ExternalAuthManager.kt`: `POST oauth/token` (~L690),
  `POST oauth/pin` (~L973). Kanıt: bu turda grep'lendi.
- **Ek risk (kota):** V1 günlük kota TÜM kullanıcıların **ortak havuzu**; V2'de kota
  **kullanıcı-başına** (Free 500 / PRO 1.000 / VIP 10.000) ve bağlı uygulamalar arasında
  paylaşılır. Yani göç = hem zorunluluk hem kota-iyileştirme fırsatı.

## V2 ne değiştiriyor? (bilinenler)

| Konu | V1 (bugün) | V2 (Nisan-2027'ye kadar) |
|---|---|---|
| Kayıt | mevcut client_id | **yeni uygulama kaydı zorunlu** (simkl.com/developers) |
| Token alımı | `oauth/token` + `oauth/pin` | **PKCE S256 zorunlu** (code_verifier → S256 challenge) |
| Kotalar | günlük ortak havuz | kullanıcı-başına 500/1k/10k, bağlı uygulamalar arası paylaşım |
| `premium_only` uçlar | — | **HTTP 200 + gövdede `error` anahtarı** (401 DEĞİL!) → ayrıştırıcıya eklenecek |
| Scope | — | açık scope listesi (read/write ayrımı) |
| Pin akışı | `oauth/pin` var | V2'de pin-akışının akıbeti dokümanda henüz kesinleşmedi → yayın-günü teyidi |

Kesin uç-uyarıları ve parametre adları V2 açık-dokümanı yayınlandıkça bu tabloya işlenecek
(şimdiden uydurulan değer yazılmayacak — aynı disiplin: kanıt yoksa kod yok).

## Göç kontrol listesi (sırasıyla)

1. **[KULLANICI — ben yapamam]** simkl.com/developers → yeni V2 uygulaması kaydet,
   `client_id` al (ve gerekiyorsa secret'ı). Mevcut V1 anahtarını AYNI anda öldürme —
   göç-öncesi iki anahtar yan-yana çalışsın.
2. Redirect URI / custom scheme'ı güncelle (Android deep-link şeması — mevcut scheme'i
   koruyup yenisini ekle; geri-uyumluluk için).
3. **PKCE:** `code_verifier` üret + `S256` challenge gönder. Şablon zaten repoda:
   MAL akışı PKCE kullanıyor (`MalAuthManager`/MAL uçları) — aynı deseni Simkl'e taşı.
4. **Scope** deklarasyonu (hesap + read/write) — V2 kayıt formundaki alanla birebir.
5. `BuildConfig.SIMKL_CLIENT_ID` → yeni anahtar (V1 anahtarını ayrı bayrakla tut).
6. `checkResponseAndThrow`/Simkl ayrıştırıcıya **`premium_only` 200+`error`** kuralı ekle
   (yoksa "başarılı" sanılır; bkz. 11.md §A2 — AniList sınıfı "200-ama-hata" tuzağı).
7. Günlük-kota davranışı: kullanıcı-başına kota A14 saniyelik kilidiyle birlikte
   izlenecek; 429 → kuyruk zaten var (kota hatası `errors` içine düşer).
8. Test planı: (a) yeni-pair ile canlı yetkilendirme, (b) pin/V2-akış teyidi,
   (c) Açık Liste A1-A6 senaryolarının tekrarı (ekle/düzenle/sil/puan/tarih/bölüm),
   (d) `premium_only` uçta 200+error ayrıştırma, (e) V1 anahtarının 401'e
   düşürülüp düşürülmediği (retro testi — göçten SONRA).

## Durum

- [x] Risk + son tarih tespiti (11.md §A2-3)
- [x] Kod envanteri: V1 uçları, çağrı noktaları, depolama (`ExternalAuthManager`)
- [x] Bu plan yazıldı
- [ ] **client_id V2 kaydı (KULLANICI hesabıyla)**
- [ ] PKCE + scope + anahtar değişimi (kod — kayıt sonrası)
- [ ] premium_only ayrıştırıcı güncellemesi
- [ ] Canlı test + V1-kapanış retro-testi

> Not: Göçü bugünden kodlayıp kullanıcıyı bekletmek anlamsız — V2 uçları anahtarsız
> doğrulanamaz. Doğru sıra: kayıt → kod → test. Hedef: Nisan-2027'den en az 6 ay önce
> (yani 2026 sonunda) canlıda.
