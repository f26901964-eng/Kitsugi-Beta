# Kitsugi Beta v2.4.288

## 🇹🇷 Türkçe

### Wiki Okuyucu Sayfa İçi Bağlantı & Harici Tarayıcıya Çıkış Düzeltmesi (Uygulama İçi WebView Penceresi)
- **Sorun ve Kök Neden:** Wiki okuyucusunda (Fandom ve Wikipedia) sayfa içi bir bağlantıya (özellikle wiki ana sayfalarındaki logo/kuruluş görselleri, `File:` dosya sayfaları, `Category:` kategori listeleri, `Help:` yardım sayfaları, portal veya harici site bağlantıları) dokunulduğunda, bu bağlantılar standart makale ayrıştırıcısı kapsamına girmediği için doğrudan sistemin harici tarayıcısını (`Intent.ACTION_VIEW`) tetikliyor ve kullanıcı uygulamadan dışarı atılıyordu. Ayrıca bazı göreli URL'lerde dil yolu tekrarlanması veya protokol-göreli (`//`) CDN bağlantılarında adres birleştirme hataları oluşabiliyordu.
- **Uygulama İçi Web Penceresi (`KitsugiWebViewDialog`):**
  - Normal wiki makaleleri yerel sekmeli okuyucuda (`onOpenLink`) kalmaya devam eder.
  - Dosya, kategori, yardım, portal sayfaları, revizyon/düzenleme (`diff`, `oldid`, `action=edit`) ve harici web adresleri artık uygulama içerisindeki şık `KitsugiWebViewDialog` penceresinde açılır.
  - Web penceresi kapatıldığında kullanıcının bulunduğu sayfa, açık bölümler ve kaydırma konumu aynen korunur.
- **Güvenli Adres Çözümleme (`FandomUrls.readerLinkTarget`):**
  - Göreli, kök göreli, protokol göreli (`//static.wikia.nocookie.net/...`), sorgu (`?`) ve bölüm (`#`) bağlantıları `HttpUrl.resolve` ile çözümlenerek dil ön eki (`/tr/`) tekrarları ve hatalı bağlantı birleştirmeleri tamamen giderildi.
  - Web dışı şemalar (`javascript:`, `intent:`, `mailto:`, `file:`, `data:`) harici uygulamalara aktarılmadan uygulama içinde güvenle engellendi.
- **Kapsamlı Regresyon Testleri:** `FandomUrlsTest` içine normal makale ayrımı, dosya/kategori/yardım yönlendirmesi, göreli adres/CDN çözümü ve güvensiz şema engelleme senaryolarını doğrulayan 3 yeni birim testi eklendi.

---

## 🇬🇧 English

### Wiki Reader In-App Link Routing Fix (Internal WebView for Non-Article Wiki Links)
- **Issue & Root Cause:** When tapping certain links in the wiki reader (such as logos/organization badges on main pages, `File:` image pages, `Category:` listings, `Help:` guides, portals, or external site URLs), they fell outside the standard article parser and triggered `Intent.ACTION_VIEW`, launching the external browser and kicking the user out of the app. Additionally, some relative links could duplicate language prefixes or corrupt protocol-relative (`//`) CDN hosts.
- **Internal WebView Dialog (`KitsugiWebViewDialog`):**
  - Standard wiki articles continue opening smoothly in the native tabbed reader (`onOpenLink`).
  - File, Category, Help, revision/edit links (`diff`, `oldid`, `action=edit`), and external web links now open inside the application using `KitsugiWebViewDialog`.
  - Closing the web dialog keeps the originating reader state, active sections, and scroll position completely intact.
- **Robust URL Resolution (`FandomUrls.readerLinkTarget`):**
  - Relative, root-relative, protocol-relative (`//static.wikia.nocookie.net/...`), query (`?`), and fragment (`#`) links are resolved via `HttpUrl.resolve`, eliminating language prefix duplication (`/tr/`) and malformed links.
  - Non-web schemes (`javascript:`, `intent:`, `mailto:`, `file:`, `data:`) are strictly blocked internally rather than dispatched to external applications.
- **Regression Unit Tests:** Added 3 unit tests to `FandomUrlsTest` verifying article versus web-view routing, relative CDN resolution, and non-web scheme rejection.
