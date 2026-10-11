# PLAN/TASK — Kaynak hata kodu sistemi (KTS kodları + TR/EN "neden oldu" açıklamaları)

Tarih: 2026-10-11
Durum: **Liste/platform kaynakları tamamlandı** (kod, dil dosyaları ve arayüz entegrasyonları). Manga kaynakları ve CloudStream eklentileri için genişletme sırada. Derleme doğrulaması bu ortamda yapılamadı (Java/Gradle/Android SDK yok); CI `assembleFossRelease` ile doğrulanacak.

Kapsam: Kullanıcı isteği — "hangi kaynaktan bilinen bilinmeyen tüm hatalar" uygulamaya uygun yerlere yerleştirilsin; her hatanın **neden** çıktığı kısa bir açıklamayla, **Türkçe ve İngilizce** olarak, dil dosyalarıyla (`values/strings.xml` + `values-en/strings.xml`) uyumlu şekilde gösterilsin. Hata açıklamaları yalnızca yazı olarak değil, aranabilir/ filtrelenebilir bir kod kataloğu ve kod→açıklama çözümleyicisi olarak da kurulsun ki kalan özellikler bu kodu kullanabilsin.

Kapsamdaki kaynaklar (kullanıcı onayıyla genişletildi):
1. **Liste/platform servisleri**: AniList, MyAnimeList, Kitsu, Shikimori, Bangumi, TMDB, Simkl
2. **Manga kaynakları** (Tachiyomi tarzı): `data/manga` + `ui/.../manga` (sırada)
3. **CloudStream eklentileri**: `data/cloudstream` + eklenti arayüzleri (sırada)

---

## 1) Kod şeması

Biçim: `KTS-<PLATFORM>-<NNNN>` — Kitsugi, platform kısaltması, 4 haneli numara. Numaralar platform başına ayrı bloklardadır ki yeni kod eklemek çakışma yaratmasın.

| Platform | Ön ek | Aralık | Adet |
|---|---|---|---|
| Ortak (tüm platformlar) | `COM` | 1001–1025 | 25 |
| AniList | `ANL` | 2001–2007 | 7 |
| MyAnimeList | `MAL` | 3001–3007 | 7 |
| Kitsu | `KIT` | 4001–4004 | 4 |
| Shikimori | `SHK` | 5001–5006 | 6 |
| Bangumi | `BGM` | 6001–6006 | 6 |
| TMDB | `TMD` | 7001–7004 | 4 |
| Simkl | `SMK` | 8001–8005 | 5 |
| **Toplam** | | | **64** |

Her kodun üç metni vardır: **başlık** (ne oldu), **neden** (kök neden açıklaması), **çözüm** (kullanıcı ne yapmalı). Üçü de TR ve EN olarak dil dosyalarında tutulur (`pf_err_<platform>_<numara>_title|why|fix`).

Örnek kodlar:
- `KTS-COM-1006` — Hız sınırı (HTTP 429): "Kısa sürede çok istek gönderildi; sunucu geçici olarak bekletiyor."
- `KTS-ANL-2003` — AniList GraphQL 429: "AniList dakikalık istek kotasını düşürdü; yeniden deneme gecikmeli yapılır."
- `KTS-BGM-6001` — Bangumi 400 / NSFW: "İçerik işaretlemesi nedeniyle istek reddedildi."
- `KTS-SMK-8003` — Simkl kimlik eşleşmesi bulunamadı.
- `KTS-COM-1020` — Bilinmeyen hata (hiçbir kalıba uymayan durumların güvenli çıpası).

`KTS-COM-1020` kasıtlı olarak "bilinmeyen" çıpasıdır: sınıflandırıcı emin olmadığında yanlış kod göstermek yerine bunu gösterir; böylece kullanıcı yanıltılmaz.

---

## 2) Yeni dosyalar

| Dosya | Görev |
|---|---|
| `data/platform/PlatformErrorCode.kt` | 64 kodlu enum + `PlatformErrorSeverity`; `fromCode()` (toleranslı çözümleme), `forPlatform()`, `applicableTo()`; `byCode`/`byPlatform` haritaları `by lazy` (enum başlatma sırası güvenliği). |
| `data/platform/PlatformErrorCatalog.kt` | `classify()` / `classifyMessage()` / `classifyText()`: platform imzaları → HTTP durum haritası → anahtar kelime kalıpları → bilinmeyen. Ayrıca `explainCode()`, `explain()`, `bilingual()` (TR+EN), `reportSection()`, `shortLabel()`, `PlatformHttpException`. |
| `data/platform/ListPlatform.kt` | Etiket/tanıma çözümleyici; `matches()`, `fromLabel()`, `detect()`, `fromErrorCode()`. Alias eşleşmesi **kelime sınırı** ile yapılır (ör. "al" kısaltması "Analiz" kelimesini yanlış yakalamaz). |
| `ui/components/PlatformErrorCodeReferenceDialog.kt` | Aranabilir/filtrelenebilir kod referans ekranı (64 kod, platform + önem derecesi süzgeci). Ayrıca kısa kullanım için `PlatformErrorCodeHint(message, platform, tint)` bileşeni. |
| `scripts/platform_error_codes/error_table.py` | Kod tablosu (TR/EN metinler + etiketler). Yeni kod eklenince buraya kayıt girilir. |
| `scripts/platform_error_codes/generate.py` | Dil dosyalarına + enum'a kod üreten idempotent script (var olan anahtarı atlar, dosya değişmezse md5 aynı kalır). |

---

## 3) Dil dosyaları

- `values/strings.xml` (TR) ve `values-en/strings.xml` (EN) dosyalarına **227'şer** yeni anahtar eklendi (`pf_err_*`).
- İki dosya arasındaki anahtar kümeleri **birebir aynıdır**: 4461 = 4461.
- Yer tutucular yalnızca geçerli biçimlerde (`%1$s`, `%2$s`): rapor satırı, rapor nedeni, rapor çözümü ve kısa kod biçimi.
- Repoda var olan `scripts/check_localization_resource_parity.py` denetimi temiz geçer:
  `Localization resources are in sync: 4461 strings and 4 arrays (including matching format placeholders).`

Not: Dil dosyalarına elle metin yazmak yerine üretici script kullanılmalıdır; aksi hâlde TR/EN eşliği bozulabilir.

---

## 4) Arayüz entegrasyonları (liste/platform)

| Yüzey | Dosya | Ne gösteriliyor |
|---|---|---|
| Çok yönlü eşitleme canlı günlüğü | `ui/components/KitsugiCrossSyncDialog.kt` | Her hata/uyarı satırında renkli kod çipi + açılınca başlık/neden/çözüm bloğu. |
| Eşitleme kaydı (rapor) | `data/auth/CrossSyncReportFormatter.kt`, `CrossSyncReportStore.kt` | Her işlem satırında `Kod:` alanı + sonda ayrı "=== HATA KODLARI (n) ===" bölümü (TR+EN çift dilli). |
| Bildirim teşhis ekranı | `data/notifications/NotificationDiagnostics.kt`, `ui/screens/notifications/KitsugiNotificationsScreen.kt` | Başarısız bildirim kontrol satırlarına kod atanır; satır altında kod açıklaması gösterilir. |
| Giriş diyalogları | `KitsugiShikimoriLoginDialog.kt`, `KitsugiBangumiLoginDialog.kt`, `KitsugiKitsuLoginDialog.kt` | Ham hata metninin altında kompakt kod + neden/çözüm bloğu. |
| Ayarlar | `ui/screens/settings/AccountSettingsSubPages.kt` | "Hata Kodu Referansı" kartı → aranabilir referans ekranı. |
| Veri yolu | `ui/app/AuthViewModel.kt`, `model/CrossSyncState.kt` | `CrossSyncLogEntry.errorCode` alanı; `logEvent` hata/uyarı satırlarını otomatik sınıflandırır (sınıflandırılamayan uyarıya kod basılmaz). |

---

## 5) Doğrulama (bu ortamda yapılanlar)

1. Kotlin'de referans verilen 216 `pf_err_*` anahtarının tamamı hem TR hem EN'de mevcut (eksik yok).
2. Dil dosyası eşliği: reponun kendi denetim scripti temiz (4461 = 4461, yer tutucular dâhil).
3. Üretici idempotency: arka arkaya iki çalıştırmada `strings.xml`, `values-en/strings.xml` ve `PlatformErrorCode.kt` md5'leri değişmedi.
4. Dokunulan tüm `.kt` dosyalarında parantez/süslü parantez dengesi HEAD'e göre kontrol edildi (delta 0).
5. `lint` bu projede derlemeyi kesmiyor (`checkReleaseBuilds=false`, `abortOnError=false`) — yeni string'ler derlemeyi bozmaz.

---

## 6) Sıradaki adımlar

1. **Manga kaynakları**: `SourceErrorCategory` (Timeout/RateLimited/Captcha/NotFound/Network/Auth/Unknown) ve sağlık durumları için `KTS-SRC-*` kodları; `MangaExtensionTab` "Son neden" alanı, "Manga Source Raporu" diyaloğu, TV kaynak sağlığı ekranları.
2. **CloudStream eklentileri**: `CsProtectionClassifier` (Cloudflare/WAF/DDoS-Guard) ve `CsPluginStatusTracker` mesajları için `KTS-CSP-*` kodları; eklenti rozetleri, keşfet diyalogları, akış hata satırları.
3. CI'da `assembleFossRelease` ile derleme doğrulaması, ardından commit.
