# PLAN_TASK — Uygulama İçi Hesap Girişi (AniList / MAL / Simkl) WebView Kök Neden Düzeltmesi

**Tarih:** 2026-10-14
**Sürüm:** v2.4.290
**Kapsam:** `ui/components/KitsugiOAuthWebViewDialog.kt`, `ui/components/KitsugiWebViewDialog.kt`, `app/build.gradle.kts`
**Durum:** Tamamlandı (kod tarafı) — cihaz testi kullanıcıda

---

## 1) Kullanıcı bildirimi

"Hesap bağlantılarının uygulama içinde tarayıcı gibi bir sayfada açılınca ve hesap girişi
yapmaya çalışınca hata veriyor, girmiyor, izni almıyor. Yalnızca harici tarayıcıdan girince
izni doğru dürüst alıyor. AniList ve MyAnimeList için; başka kaynaklara o mekaniği
bağladıysan onlar için de geçerli."

## 2) Kök neden (özet)

- AniList / MAL / Simkl girişleri `KitsugiOAuthWebViewDialog` içindeki **uygulama içi
  WebView**'de açılıyordu ve WebView'e **sabit sahte User-Agent** gönderiliyordu:
  `"Chrome/124.0.0.0 (Linux; Android 13; Pixel 7)"` (genel pencerede `Chrome/124, Android 10; K`).
- Cloudflare (AniList/Simkl) ve MAL WAF, istemcinin **gerçek TLS + JS parmak izini** beyan
  edilen UA ile karşılaştırır. Cihazın WebView motoru gerçekte daha yeni bir Chrome ve farklı
  bir model iken UA "Chrome/124, Pixel 7" dediği için uyumsuzluk **bot sinyali** olur →
  "Gerçek kişi misiniz?" doğrulaması gelir ve WebView'de **döngüye takılır** → giriş
  yapılamaz, izin alınamaz. Harici tarayıcıda UA ve parmak izi gerçek ve tutarlı olduğu
  için aynı URL sorunsuz çalışır (decisive test).
- Depo içi kanıt: `TASK_TUM_KAYNAKLAR_TARAYICI_GIRIS_BUTONU_2026-10-12.md` belgesi aynı
  belirtiyi (Cloudflare kontrolünün WebView'de takılması) kaydetmişti; o tarihte yalnızca
  "Tarayıcıda Aç" kaçış butonu eklenmiş, kök neden çözülmemişti.
- İkincil kusurlar: (a) `malapp://auth` dönüşü iki ayrı kancada korumasız işleniyordu
  (çift token takası → "kod zaten kullanılmış"), (b) `window.location` ile yapılan dönüşler
  bazı WebView sürümlerinde yakalanmıyordu (`doUpdateVisitedHistory` yoktu), (c) özel şemalı
  dönüşlerde `ERR_UNKNOWN_URL_SCHEME` yanlışlıkla "Sayfa yüklenemedi" hatası olarak gösteriliyordu.

## 3) Çözüm

### 3.1 `KitsugiOAuthWebViewDialog.kt` (AniList / MAL / Simkl OAuth penceresi)

| Değişiklik | Açıklama |
|---|---|
| `userAgentString` ataması **kaldırıldı** | Sistem WebView'inin GERÇEK varsayılan UA'sı kullanılır; parmak iziyle tutarlı olduğu için Cloudflare/MAL WAF geçişi harici tarayıcıya yakın olur. **(Kök neden düzeltmesi)** |
| Tek işleyici: `handleAuthRedirect(rawUrl)` | `malapp://auth` dönüşü tek fonksiyondan geçer; `authRedirectHandled` bayrağı aynı dönüşü en fazla bir kez işler (çift takas koruması). |
| Yeni kanca: `doUpdateVisitedHistory` | JS `window.location` ile yapılan OAuth geri dönüşleri de yakalanır. |
| `stopLoading()` | Dönüş yakalandığında WebView özel şemayı yükleyip hata sayfası üretmesin. |
| `onReceivedError` filtresi | `malapp://` ve `intent:` şemalı ana çerçeve hataları artık hata banner'ı göstermez. |
| Korunan davranışlar | JS + DOM storage + 3. parti çerezler, `CookieManager.flush()`, OkHttp cookie senkronu, Basic Auth popup iptali, her zaman görünür "Tarayıcıda Aç" butonu. |

### 3.2 `KitsugiWebViewDialog.kt` (genel uygulama içi web penceresi)

- Aynı UA düzeltmesi (sahte `Chrome/124` kaldırıldı → varsayılan gerçek UA).
- `interceptRedirect()` + `lastRedirectHandledUrl` koruması: `onUrlRedirect` tüketimi tek
  noktadan; `onPageStarted` ve `doUpdateVisitedHistory` kanalları da eklendi.
- TV QR girişinin telefon tarafındaki AniList/MAL ekranı bu pencereyi kullanır → aynı düzeltmeden yararlanır.

### 3.3 Etkilenen / etkilenmeyen kaynaklar

| Kaynak | Giriş yolu | Durum |
|---|---|---|
| AniList | Uygulama içi OAuth WebView | ✅ Düzeltildi |
| MyAnimeList | Uygulama içi OAuth WebView (tek oturum: jeton + web çerezi) | ✅ Düzeltildi |
| Simkl (OAuth) | Uygulama içi OAuth WebView | ✅ Düzeltildi |
| TV QR (telefon tarafı) | `KitsugiWebViewDialog` | ✅ Düzeltildi |
| Kitsu | Kullanıcı adı/şifre (API) | Etkilenmiyordu |
| Shikimori | Harici tarayıcı (`ACTION_VIEW` + deep link) | Etkilenmiyordu |
| Bangumi | Harici tarayıcı / manuel kod | Etkilenmiyordu |
| Simkl (PIN) | Harici tarayıcı + polling | Etkilenmiyordu |

- **MAL tek oturum akışı korundu:** bildirimler için MAL web çerezleri WebView
  CookieManager'dan okunmaya devam eder (`MalWebSessionStore`); UA değişikliği bu yolu etkilemez.

## 4) Doğrulama

- Android SDK bu geliştirme ortamında yok → APK derlemesi cihazda/CI'da. GitHub Actions
  `android-build.yml` (arena/** push tetiklemeli) derleme doğrulaması için tetiklendi.
- Parantez dengesi iki dosyada da programatik doğrulandı; aktif kodda `userAgentString =`
  ataması kalmadığı doğrulandı (Chrome/124-120 yalnızca açıklama yorumlarında).
- **Kullanıcı testi:** Ayarlar → Hesap Bağlantıları → AniList / MyAnimeList / Simkl bağla →
  uygulama içi pencerede giriş yap ve izin ver. Cloudflare kontrolü gelirse artık
  tamamlanabilir; istenirse "Tarayıcıda Aç" hâlâ tek dokunuşluk çıkış yoludur.

## 5) Artık riskler

- Manga/eklenti kaynaklarının Cloudflare bypass zinciri (`WebViewResolver` /
  `KitsugiUserAgent` / `NuvioOkHttpProvider`) bilinçli olarak değiştirilmedi; orada OkHttp
  ve WebView UA'ları kendi aralarında eşleşiyor. İleride orası da gerçek UA'ya taşınırsa
  **OkHttp tarafıyla birlikte** değiştirilmelidir.
- Sağlayıcı IP itibarına göre agresif challenge gelirse hiçbir WebView geçmeyebilir;
  bu durumda mevcut "Tarayıcıda Aç" butonu devreye girer.

## 6) Değişen dosyalar

```
app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiOAuthWebViewDialog.kt
app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiWebViewDialog.kt
app/build.gradle.kts                       (sürüm: 2.4.289 → 2.4.290)
release_notes_v2.4.290.md                  (yeni)
KOK_NEDEN_RAPORU_HESAP_GIRISI_WEBVIEW_UA_2026-10-14.md  (yeni, detaylı rapor)
```
