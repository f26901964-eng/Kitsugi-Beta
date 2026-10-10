# PLAN / TASK — Cloudflare WebView Sızıntısı ve RenderThread Baskısı

**Tarih:** 2026-10-10  
**Sürüm:** 2.4.227  
**Dal:** `arena/55de8e95-kitsugi-beta`  
**Durum:** Kod değişiklikleri tamamlandı · Android derleme ve cihaz doğrulaması bekliyor

## Amaç

Cloudflare challenge çözümünden sonra birikebilecek Chromium/WebView native kaynaklarını temizlemek ve uygulama açılışında gereksiz WebView/RenderThread yükü oluşturmamak.

## Kod incelemesinde bulunan neden

`NuvioOkHttpProvider.imageClient`, `CloudflareInterceptor` kullanıyor. `resolveWithWebView()` içindeki başarı yolu `cf_clearance` bulunca latch'i tamamlıyordu. Eski timeout yolu ise WebView'ı yalnızca latch hâlâ bekliyorken yok ediyordu. Böylece başarılı çözümde timeout cleanup koşulu atlanıyor ve `WebView.destroy()` hiç çağrılmıyordu. Tekrarlanan Cloudflare challenge'larında bu durum native Chromium kaynaklarının tutulmasına yol açabilirdi.

Ayrıca `KitsugiApplication` açılışta, kullanıcı isteği olmadan 14 siteye kadar gizli WebView ile proaktif warmup başlatıyordu. Challenge çözümü zaten ihtiyaç anında yapılabildiğinden bu açılış işi gereksiz native bellek ve RenderThread baskısı oluşturuyordu.

## Yapılan işler

- [x] `CloudflareInterceptor`: başarı, zaman aşımı, iptal, thread kesintisi ve son-zaman-aşımı durumlarını tek `AtomicBoolean` korumalı cleanup yoluna bağla.
- [x] WebView lifecycle işlemlerini ana thread'de tut; referansı atomik biçimde alıp boşalt; parent'tan sök, yüklemeyi durdur, client referanslarını temizle ve yalnızca bir kez `destroy()` çağır.
- [x] `shouldOverrideUrlLoading` içinde aynı yönlendirmeyi yeniden `loadUrl()` ile başlatmak yerine WebView'ın kendi redirect işlemesine izin ver.
- [x] Başarılı challenge sonrası timeout'un cleanup'ı atlaması hatasını gider.
- [x] `KitsugiApplication`: başlangıçtaki 14-site proaktif WebView warmup'ını kaldır; challenge'lar host ihtiyaç duyduğunda çözülmeye devam eder.
- [x] `CsCfWarmupManager`: destroy öncesi `about:blank` navigasyonunu kaldır; client temizliğini teardown'a ekle.
- [x] Uygulama sürümünü `2.4.227` olarak güncelle ve `RELEASE_NOTES.md`'ye not ekle.

## Doğrulama

- [x] `git diff --check` geçti.
- [x] Statik crash-regression kontrolü: Cloudflare interceptor'da tek `destroy()` çağrısı, ortak one-shot cleanup, success cleanup ve startup warmup çağrısının kaldırılması doğrulandı.
- [ ] Gradle compile/test — bu ortamda Java/JDK ve Android SDK bulunmadığından çalıştırılamadı.
- [ ] CI/APK derlemesi ve cihazda uzun süreli kullanım doğrulaması.
- [ ] Yeni crash raporlarıyla signature karşılaştırması.

> Not: Kullanıcının eklediği `Kitsugi_Crash_Report (10–12).txt` dosyaları bu sandbox'ta görünür değildi (`/home/user/uploads` dizini yoktu). Bu nedenle mevcut yamadaki teşhis depodaki gerçek WebView lifecycle hatası ve önceki crash inceleme notlarına dayanıyor. Yeni crash imzalarını ayrıca doğrulamak için raporların yeniden erişilebilir olması gerekir.

## Değişen dosyalar

- `app/src/main/java/com/kitsugi/animelist/core/network/CloudflareInterceptor.kt`
- `app/src/main/java/com/kitsugi/animelist/KitsugiApplication.kt`
- `app/src/main/java/com/kitsugi/animelist/data/cloudstream/CsCfWarmupManager.kt`
- `app/build.gradle.kts`
- `RELEASE_NOTES.md`
