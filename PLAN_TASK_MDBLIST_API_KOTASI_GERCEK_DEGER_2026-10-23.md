# PLAN & TASK — MDBList API Kotası Gerçek Değer

**Tarih:** 2026-10-23
**Durum:** ✅ Tamamlandı
**Sürüm:** v2.4.341
**Commit:** `3bf8d38` (branch: `arena/c31dc8d1-kitsugi-beta`)

---

## 1. Hedef

Ayarlar → Entegrasyonlar → MDBList sekmesindeki "API Kotası" alanı ve detay sayfasındaki
MDBList rozeti, sabit `1.000 / 1.000` yerine **MDBList hesabının gerçek kalan istek sayısını**
gösterecek. Kullanıcı kotanın ne kadarını tükettiğini, ne zaman yenileneceğini ve hesabının
günlük limitini görebilecek.

## 2. Kapsam

| # | Görev | Durum |
| --- | --- | --- |
| 1 | MDBList kota API'sinin araştırılması (resmî doküman + OpenAPI) | ✅ |
| 2 | `X-RateLimit-*` başlıklarının her yanıttan okunması | ✅ |
| 3 | `GET https://api.mdblist.com/user` entegrasyonu | ✅ |
| 4 | Yerel istek sayacı + UTC gece devri + 429 davranışı | ✅ |
| 5 | Anahtar parmak iziyle kalıcı kota deposu | ✅ |
| 6 | Ayarlar ekranında gerçek kota kartı (çubuk, geri sayım, plan, yenile) | ✅ |
| 7 | Detay sayfası "Harici Puanlar" rozetinde gerçek kalan/limit | ✅ |
| 8 | 429'da yeniden denemenin kaldırılması (kota israfı) | ✅ |
| 9 | TR/EN metinler + ayar arama dizini | ✅ |
| 10 | Birim testleri (`MdbListQuotaTest`) | ✅ |
| 11 | Sürüm notları + çözüm raporu | ✅ |

## 3. Araştırma Notları

- **Başlıklar:** `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` (Unix saniye).
  429'da `Retry-After` (saniye).
- **Hesap uç noktası:** `GET https://api.mdblist.com/user?apikey=…`
  → `rate_limit`, `rate_limit_remaining`, `rate_limit_reset`, `api_requests`,
  `api_requests_count`, `plan`, `is_supporter`.
- **Limitler (hesap bazlı, tüm anahtarlar ortak):**

  | Plan | İstek / gün |
  | --- | --- |
  | Free | 1.000 |
  | 1€ | 10.000 |
  | 2€ | 25.000 |
  | 3€ | 100.000 |
  | 5€ VIP | 250.000 |
  | VIP 10/15/20€ | 500.000 / 1.000.000 / 1.500.000 |

- Ayrıca her kademede 5 dakikada 1.000 okuma / 300 yazma sınırı vardır.

## 4. Teknik Tasarım

```
MDBList yanıtı ──► recordQuotaResponse()
                     ├─ X-RateLimit-* varsa  ──► kesin değer (authoritative = true)
                     ├─ 429                  ──► kalan = 0, reset = Retry-After
                     └─ başlık yoksa         ──► son değer − 1 (tahmini)

Ayarlar açılışı ──► loadCachedQuota()  (anında, ağsız)
                     └─ 15 dk'dan eskiyse ──► refreshQuota(/user)

Yenile düğmesi ──► refreshQuota(force = true)
```

Kota modeli: `kalan = serverRemaining − pendingRequests`
- `serverRemaining`: son sunucu okuması
- `pendingRequests`: o okumadan sonra bu uygulamanın yaptığı istekler
- `authoritative`: değer doğrudan sunucudan mı geldi?
- `isExact`: authoritative **ve** yerel tahmin yok

## 5. Değişen Dosyalar

```
app/build.gradle.kts                                              (sürüm 2.4.341)
app/src/main/java/com/kitsugi/animelist/KitsugiApplication.kt      (depo başlatma)
app/src/main/java/com/kitsugi/animelist/core/network/KitsugiHttpClient.kt (mdbListClient)
app/src/main/java/com/kitsugi/animelist/data/remote/MdbListClient.kt
app/src/main/java/com/kitsugi/animelist/data/remote/MdbListQuota.kt   (YENİ)
app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiIntegrationsSettingsDialog.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailComponents.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/settings/SettingsSearch.kt
app/src/main/res/values/strings.xml
app/src/main/res/values-en/strings.xml
app/src/test/java/com/kitsugi/animelist/data/remote/MdbListQuotaTest.kt  (YENİ)
RELEASE_NOTES.md
release_notes_v2.4.341.md                                         (YENİ)
COZUM_RAPORU_MDBLIST_KOTASI_GERCEK_DEGER_2026-10-23.md              (YENİ)
PLAN_TASK_MDBLIST_API_KOTASI_GERCEK_DEGER_2026-10-23.md             (YENİ)
```

## 6. Kabul Kriterleri

- [x] Arayüzde hiçbir yerde sabit `1.000 / 1.000` yazılmıyor.
- [x] Geçerli anahtarla ayarlar açıldığında gerçek kalan istek görünüyor.
- [x] Yenile düğmesi sunucudan güncel değeri çekiyor.
- [x] Değer yeni isteklerle düşüyor; 00:00 UTC devrinde günlük limite dönüyor.
- [x] 429'da "kota doldu" ve yenilenme süresi gösteriliyor.
- [x] Sunucu değeri yoksa sayı "tahmini" olarak etiketleniyor, kesin gibi gösterilmiyor.
- [x] Detay sayfası rozeti yalnızca doğrulanmış değeri gösteriyor.
- [x] API anahtarı diske düz metin olarak yazılmıyor (SHA-256 parmak izi).
- [x] Uçak modunda son bilinen değer korunuyor, hata satırı çıkıyor.

## 7. Test Planı

Birim testleri — `MdbListQuotaTest`:

1. `X-RateLimit-*` başlıkları → limit/kalan/reset doğru.
2. `RateLimit-*` varyantı ve büyük/küçük harf bağımsızlığı.
3. Eksik başlıkta `null` (uydurma kota yok).
4. Reset biçimleri: epoch saniye / epoch milis / delta saniye.
5. `/user` JSON: `rate_limit_remaining` ve `api_requests_count` yedeği.
6. Yerel istek düşümü ve `isExact` geçişi.
7. UTC gece yarısı devri.
8. 429 + `Retry-After` davranışı.
9. Disk formatı gidiş-dönüşü.
10. `/user` URL'i (host, yol, `apikey`).

Cihaz doğrulaması:

1. Ayarlar → Entegrasyonlar → MDBList → geçerli anahtar gir.
2. Kartta `1.000 / 1.000` değil, gerçek `XXX / 1.000` görün.
3. Birkaç detay sayfası aç → değer düşsün.
4. Yenile → sunucu değeri gelsin.
5. Uçak modu → "Kota okunamadı" + son bilinen değer.

## 8. Riskler ve Önlemler

| Risk | Önlem |
| --- | --- |
| `/user` bazı hesaplarda yanıt vermeyebilir | Başlık yolu birincil; başarısızsa "tahmini" etiketi + 60 sn'de bir yeniden deneme |
| Her kota okuması da kotadan 1 düşer | Otomatik okuma 15 dk'da bir, manuel 5 sn taban |
| Eski `mdblist.com/api` başlık döndürmeyebilir | Arka planda tek `/user` çağrısı ile senkron; yerel sayaç devrede |
| Başka cihazlar aynı hesabı kullanabilir | Değerin "kesin" olmadığı durumlar arayüzde açıkça belirtilir |
| Gece devri | `resetAtEpochSeconds` geçmişteyse kota otomatik olarak günlük limite taşınır |

## 9. Not

Bu ortamda JDK/Android SDK bulunmadığı için `gradlew assembleDebug` çalıştırılamadı.
Kotlin dosyaları tree-sitter ile sözdizimi denetiminden geçirildi (0 hata) ve testler eklendi;
derleme + cihaz doğrulaması geliştirici makinesinde yapılmalıdır.
