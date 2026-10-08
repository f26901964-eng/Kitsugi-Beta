# Kitsugi-Beta v2.4.206 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🛡️ 1. Native & Java Çökme Teşhisi, ANR/OOM Raporlama & Cockroach Çözümü
- **Native Çökme Yakalayıcı (C++ - libkitsugi_crash_handler.so):** Java `UncaughtExceptionHandler`'ın yakalayamadığı native çökmeler (SIGSEGV, SIGABRT, SIGBUS, SIGILL, SIGFPE, SIGTRAP) `_Unwind_Backtrace` ile asenkron sinyal güvenliğinde yakalanır; ham geri iz `native_crash.txt` dosyasına yazılır.
- **Sessiz Ölüm Dedektörü (KitsugiSessionSupervisor):** Uygulama temiz çıkış yapamadan kapandığında (LMKD bellek öldürmesi, ANR sonrası sonlandırma, SIGKILL), bir sonraki açılışta logcat taranarak `unclean_exit.txt` raporu üretilir ve kullanıcıya "Beklenmedik Kapanma" penceresi gösterilir.
- **Eylem İzi (Breadcrumbs):** Son 40 kullanıcı hareketi ve son bulunulan ekran anlık kaydedilir; çökme raporlarında hatanın hangi ekranda ve hangi eylem sırasında gerçekleştiği açıkça görülür.
- **Bağımsız Görevde Açılan Çökme Ekranı:** `Thread.sleep(800)` ana thread bloklaması kaldırıldı. Çökme ekranı (`KitsugiCrashActivity`) ayrı görev (`taskAffinity`) ve süreçte açılarak Android görev temizliğinin çökme ekranını yok etmesi engellendi.
- **Cockroach Sonsuz CPU Döngüsü & Kör Hata Yutma Düzeltildi:** `com.lagradost.cloudstream3` hatalarını körlemesine yutup arayüzü kilitli bırakan mantık düzeltildi; `Looper.loop()` sonlandığında oluşan %100 CPU kilitlenmesi (ANR donmaları) giderildi.
- **Erken Kurulum:** Çökme yakalayıcı `attachBaseContext()` aşamasına çekilerek açılış ve provider çökmeleri de kapsama alındı.
- **Genişletilmiş Teşhis Paylaşımı:** "Geliştiriciye Gönder", tüm teşhis kütüklerini (`crash_log`, `crash_history`, `unclean_exit`, `native_crash`, `breadcrumbs` vb.) tek pakette paylaşır.

### 📋 2. Duplicate Lazy Key Liste Çökmeleri Düzeltildi
- `IllegalArgumentException: Key ... was already used` hatasına yol açabilen 7 arayüz listesi düzeltildi: Ana sayfa ızgarası, modern ana sayfa satırları, yayın takvimi (liste ve ızgara), genişletilmiş medya ızgarası, manga eklenti kaynak listesi, özel liste düzenleyici ve oynatıcı hız seçim sayfası.

### 🖼️ 3. Resim İndirme & Bellek Güvenliği (OOM Önleme)
- **Bellek Koruma (Downsampling):** İndirilen görsellerin bildirim önizlemesi tam boy yerine `inSampleSize` ile küçültülerek decode edilir; 48 MB üzeri devasa görseller filtrelenir.
- **Android 10+ MediaStore Kaydı:** Görseller doğrudan `İndirilenler/Kitsugi/Images` altına MediaStore API ile yazılır (Android 11+ dosya yazma engeli aşıldı).
- Galeri ve indirme adımları eylem izine (breadcrumbs) dahil edildi.

### 🎭 4. Karakter Görselleri & Oyuncu Fotoğrafları İyileştirmeleri
- **Akıllı Başlık Adayları:** TMDB içeriklerinde tüm başlık varyantları (Türkçe, Romaji, İngilizce, Japonca, eşanlamlılar) taranarak doğru anime eşleşmesi bulunur.
- **AniList Karakter Araması Yedeği:** Yapım düzeyinde eşleşmeyen karakterler için doğrudan AniList karakter araması yapılır (`realMalId` doğrulamasıyla).
- **Canlı Çekim Kadrolarında Oyuncu Fotoğrafları Korundu:** Live-action yapımlarda oyuncu fotoğrafları silinmez; `isRealMediaRole` bayrağı ile doğrudan TMDB kişi profili açılır.
- **Detay Sayfasında Anında Görsel (Hint Image):** Karakter sayfasına girildiğinde karttaki görsel anında galeriye ve arka plana alınır, sayfa resimsiz kalmaz.
- **Jikan /pictures Optimizasyonu:** Galeri resim isteği yalnızca MyAnimeList kimlik uzayında çalıştırılır (TMDB 404 çağrıları bitti).

### ⚡ 5. Shikimori Detay Sekmeleri & Hız Optimizasyonları
- **Yerel İlişkiler ve Öneriler:** Shikimori `/similar` ve `/related` uç noktalarıyla tek istekte saniyeler içinde yüklenir; kapaklar toplu çözülür.
- **Ekip Sekmesi Boş Kalma Koruması:** Shikimori personel kaydı bulunmayan yapımlarda gerçek MAL ID'si üzerinden Jikan personel listesi devreye girer.
- **Sonsuz Skeleton Koruması (25 sn Zaman Aşımı):** Sekme yüklemelerine 25 saniye (bölümler için 45 saniye) zaman aşımı ve tek-uçuş (single-flight) koruması eklendi.
- **ID İzolasyonu:** Shikimori ID'sinin MAL ID sanılması engellendi; gerçek MAL ID'si önbellekli çözülür.

### 🎬 6. Detay Sayfası Tasarım & "Bilgiler" Kopyalama Butonları Birleştirmesi
- **Ortak Bilgiler Bileşeni (`DetailInfoValueRow`):** Kütüphane girdisi ve API detay ekranlarındaki "Bilgiler" kartı ortak bileşene kavuşturuldu; her iki ekranda da tüm satırlara (Durum, Başlangıç, Bitiş, Stüdyo, Süre, Yayın, Yaş Sınırı, İngilizce, Japonca vb.) kopyalama butonları eklendi.
- **"Diğer Adlar" Tekil Etiketler & Bağımsız Kopyalama:** "Diğer Adlar" satırında tüm isimleri tek metin halinde birleştirip kopyalama sorunu giderildi. Artık her isim kendi etiketinde (chip) ve bağımsız kopyalama butonuyla yer alır; virgül içeren adlar bozulmaz, boş ve tekrar edenler filtrelenir.
- **Kompakt İzle Butonu:** Sol panelde ve özet bilgi alanında tüm genişliği kaplayan devasa buton yerine içerikle uyumlu (`wrapContent`) şık ve kompakt bir "İzle" butonu tasarlandı.

### 🔍 7. Arama & "Tümünü Gör" Raf Tutarlılığı
- **Raf ile Kaynak Sayfası Uyumu:** Arama ekranında bir raftan "Tümünü Gör"e tıklandığında, raftaki sorgu, arama kapsamı (`SearchScope`) ve mevcut sonuçlar doğrudan `SourceSearchPage`'e aktarılır; sonuçlar kaybolmaz.
- **Tümü Rafından Açıldığında Çift Kapsam (Anime + Manga):** "Tümü" rafında hem anime hem manga listelendiği için, kaynak sayfası açıldığında her iki kapsam da birlikte taranır; rafta görünen içerikler sayfada silinmez.
- **Yenileme Başarısız Olsa Bile İçerik Koruma:** Ağ hatasında raftaki mevcut sonuçlar temizlenmez.
- **MAL Alakasız Popülerler Düşüşü Kaldırıldı:** MAL kaynağında spesifik sorgu sonuçsuz kalınca kullanıcıyı yanıltan genel popülerler listesine düşme engellendi.

### 🧾 Ek
- Sürüm adı `v2.4.206` olarak güncellendi.
- Kullanıcı talimatı doğrultusunda **yalnızca `foss` varyantı** derlendi ve yayınlandı (GMS derlenmedi).

---

## 🇬🇧 English

### 🛡️ 1. Crash Diagnostics & Reporting Overhaul (No More Silent Deaths)
- **Native Crash Handler (C++ - libkitsugi_crash_handler.so):** Catches SIGSEGV, SIGABRT, SIGBUS, SIGILL, SIGFPE, SIGTRAP using `_Unwind_Backtrace` in an async-signal-safe manner, writing raw backtraces to `native_crash.txt`.
- **Silent-Death Detector (KitsugiSessionSupervisor):** Discovers unhandled terminations (LMKD, post-ANR kills, SIGKILL) by scraping logcat on the subsequent launch, generating `unclean_exit.txt` and alerting the user with an "Unexpected Shutdown" dialog.
- **Action Breadcrumbs:** Tracks the last 40 user interactions and active screen keys to pinpoint the exact failure location.
- **Isolated Crash Screen:** Removed `Thread.sleep(800)` UI freezes. The crash screen (`KitsugiCrashActivity`) opens with its own `taskAffinity` in a dedicated process, protecting it from OS task teardown.
- **Cockroach Loop & Error Swallowing Fix:** Fixed unchecked swallowing of Cloudstream exceptions and eliminated the 100% CPU spinning loop on `Looper.loop()` exit.
- **Early Setup:** Initialized in `attachBaseContext()` to catch startup and ContentProvider faults.
- **Comprehensive Sharing:** "Send to Developer" bundles all diagnostic artifacts (`crash_log`, `crash_history`, `unclean_exit`, `native_crash`, `breadcrumbs`).

### 📋 2. Duplicate Lazy Key List Crashes Fixed
- Resolved `IllegalArgumentException: Key ... was already used` across 7 lists: Home grid, modern home rows, airing calendar (list & grid), expanded media grid dialog, manga extensions, custom list editor, and playback speed picker.

### 🖼️ 3. Image Downloads & Memory Hardening (OOM Prevention)
- **Sampled Decoding:** Notification icons downsample byte streams using `inSampleSize` instead of full-size bitmap decoding; capped downloads at 48 MB.
- **Android 10+ MediaStore Integration:** Saves downloaded images via MediaStore to `Downloads/Kitsugi/Images`, bypassing scoped storage issues on Android 11+.
- Logged gallery and download interactions into breadcrumbs.

### 🎭 4. Character Images & Live-Action Cast Photos
- **Candidate Title Resolver:** Resolves character images across all title variants (Turkish, Romaji, English, Japanese, synonyms).
- **Direct AniList Character Search Fallback:** Unmatched character records fall back to AniList character search with strict `realMalId` verification.
- **Preserved Cast Photos in Live-Action:** Actor photos are preserved, and live-action characters route directly to TMDB person profiles via `isRealMediaRole`.
- **Instant Detail Image:** Re-uses the list card thumbnail as an immediate placeholder on the detail page.
- **MAL-Only Jikan /pictures:** Prevents wasteful 404 network requests with TMDB IDs.

### ⚡ 5. Shikimori Tabs & Speed Optimization
- **Native Relations & Recommendations:** High-speed single-request fetching with batch cover resolution via Shikimori `/similar` and `/related`.
- **Staff Shelf Fallback:** Automatically queries Jikan staff data when Shikimori returns empty staff records.
- **25s Timeout & Single-Flight Protection:** Eliminates perpetual loading skeletons on unstable networks.
- **ID Resolution Safety:** Distinct caching and resolution between Shikimori IDs and MyAnimeList IDs.

### 🎬 6. Detail Screen Polish & Unified Info Row Copy Buttons
- **Unified Info Component (`DetailInfoValueRow`):** Both library entry and API result info cards now share the same row component with individual copy buttons on every row (Status, Start Date, End Date, Studio, Duration, Broadcast, Rating, English, Japanese).
- **Synonyms Chips with Individual Copy:** "Other Names" are displayed as individual chips, each with its own copy button. Prevents comma concatenation issues and ignores null/blank duplicates.
- **Compact Watch Button:** Replaced full-width watch buttons with clean, content-fitting buttons.

### 🔍 7. Search & "See All" Shelf Consistency
- **Handoff from Shelf to Source Page:** Clicking "See All" preserves the shelf's query, search scope (`SearchScope`), and pre-rendered results directly into `SourceSearchPage`.
- **Dual Scope for "All" Shelves:** Searching from "All" carries over both Anime and Manga scopes so items found on the shelf are never wiped upon opening the full source page.
- **Failure Resilience:** Network refresh failures preserve previously visible shelf results.
- **Removed Irrelevant MAL Popular Fallbacks:** Fixed MAL queries returning unrelated popular anime lists when search yielded zero matches.

### 🧾 Extras
- Version bumped to `v2.4.206`.
- Built and published exclusively as the `foss` release variant.
