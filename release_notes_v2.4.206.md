## 🚀 Kitsugi Beta v2.4.206

### 1. 🛡️ ÇÖKME RAPORLAMA SİSTEMİ SIFIRDAN YENİLENDİ (Sessiz Kapanmaların Sonu)
- **Neden hiç çökme ekranı çıkmıyordu?** Java `UncaughtExceptionHandler` yalnızca *Java istisnalarında* çalışır. Native çökmeler (SIGSEGV/SIGABRT — oynatıcı, decoder, GPU), LMKD/bellek yetersizliği, ANR sonrası sistem öldürmeleri ve `SIGKILL` bu handler'a **hiç uğramaz**. Bu yüzden uygulama "pat diye" kapanıyor ve HİÇBİR rapor üretilmiyordu. Artık bu ölümler de yakalanıyor.
- **Native Çökme Yakalayıcı (YENİ):** `libkitsugi_crash_handler.so` SIGSEGV/SIGABRT/SIGBUS/SIGILL/SIGFPE/SIGTRAP sinyallerini yakalayıp ham geri izi (backtrace, sinyal, hatalı adres, thread adı) `native_crash.txt` dosyasına yazar; ardından sinyali yeniden yükseltir (tombstone davranışı bozulmaz).
- **Sessiz Ölüm Dedektörü (YENİ — KitsugiSessionSupervisor):** Her oturum diske işaretlenir. Uygulama temiz kapanmadan öldüyse, sonraki açılışta logcat arabelleği kazınarak **Fatal signal / ANR in / lowmemorykiller / am_kill / OutOfMemoryError** kanıtları bulunur ve `unclean_exit.txt` raporu üretilir. Rapor bir sonraki açılışta pencere olarak gösterilir.
- **Eylem İzi (breadcrumbs):** Son 40 kullanıcı eylemi + son ekran kaydedilir. Rapor artık "hangi ekrandaydı, ne yapıyordu" sorusunu da cevaplar.
- **Çökme Ekranı Artık Gerçekten Açılıyor:** Ana süreç artık ana thread'i uyutmuyor (`Thread.sleep(800)` kaldırıldı), çökme ekranı KENDİ GÖREVİNDE (`taskAffinity`) ve ayrı süreçte açılıyor; ölü süreç çökme ekranı tarafından kapatılıyor. Böylece "görev temizliği" çökme penceresini yok edemiyor.
- **Rapor her koşulda kaydedilir:** Rapor üretiminden ÖNCE bayrak dosyası + SharedPreferences yazılır; `catch (e: Exception)` yerine `Throwable` yakalanır (OutOfMemoryError/StackOverflowError artık raporu engelleyemez).
- **`catch` ağı erken kuruldu:** Yakalayıcı `attachBaseContext()` içinde kurulur — ContentProvider/Hilt/WorkManager init çökmeleri de raporlanır.
- **İndirilenler'e kaydetme düzeltildi:** Android 10+ için MediaStore kullanılır (`İndirilenler/Kitsugi/...`); eski kod Android 11+ üzerinde EACCES ile sessizce başarısız oluyordu.
- **Teşhis paylaşımı genişletildi:** "Geliştiriciye Gönder" artık `crash_log`, `crash_history`, `unclean_exit`, `native_crash`, `logcat_at_crash`, `post_mortem_logcat`, `breadcrumbs` dosyalarını birlikte paylaşır.

### 2. 🐞 Sessiz Çökmeyi Gizleyen "Cockroach" Koruyucusu Düzeltildi
- **Kök neden:** Ana thread koruması, stack trace'in HERHANGİ bir yerinde `com.lagradost.cloudstream3` geçen her hatayı yutuyordu. Bu uygulamada arayüz yollarının çoğu Cloudstream katmanından geçtiği için **gerçek çökmeler sessizce yutuluyor**, uygulama bozuk durumda çalışmaya devam edip sonra sistem tarafından öldürülüyordu.
- Artık yalnızca çağrı zincirinin İLK kareleri eklenti/Cloudstream içindeyse kurtarılır, her kurtarma rapora yazılır ve oturum başına 5 kurtarma sınırı vardır.
- `Looper.loop()` geri döndüğünde (kuyruk kapanırken) döngü artık sonlanıyor — eski kod burada **%100 CPU sonsuz döngüye** girip ANR ürettiriyordu ("anlık donup pat diye kapanma").

### 3. 🧨 Liste Çökmeleri Giderildi (Duplicate Lazy Key)
- **`IllegalArgumentException: Key ... was already used`** hatası veren 7 liste düzeltildi: ana sayfa grid'i, modern ana sayfa satırları, yayın takvimi (liste + grid), genişletilmiş medya grid penceresi, manga kaynak listesi, özel liste editörü, oynatma hızı listesi. Anahtarlar artık benzersizdir (index ile desteklenir).

### 4. 🖼️ Resim İndirme Sağlamlaştırıldı
- **OOM koruması:** Bildirim görseli artık tam boy yerine örneklemeli (inSampleSize) decode edilir; indirme boyutu 48 MB ile sınırlıdır (dev boyutlu görseller bellek tüketip süreci sessizce öldürebiliyordu).
- **Android 10+ kaydetme:** Görseller MediaStore ile `İndirilenler/Kitsugi/Images` klasörüne yazılır (önceden Android 11+ üzerinde hiç kaydedilemiyordu).
- Galeri gezinmesi ve indirme adımları eylem izine kaydedilir (çökme raporunda görünür).

### 5. 🧾 Teşhis
- Yeni raporlar: `files/crash_log.txt`, `files/unclean_exit.txt`, `files/native_crash.txt`, `files/post_mortem_logcat.txt`, `files/breadcrumbs.txt`
- Uygulama içi: **Ayarlar → Hakkında → Çökme & Hata Teşhis Raporu** (detaylı ekran) veya açılışta çıkan "Beklenmedik Kapanma" penceresi.

---

### 1. 🛡️ Crash Reporting Rebuilt From Scratch (No More Silent Deaths)
- **Why was no crash screen ever shown?** The Java `UncaughtExceptionHandler` only runs for *managed Java exceptions*. Native crashes (SIGSEGV/SIGABRT — player, decoder, GPU), LMKD/memory kills, post-ANR kills and plain `SIGKILL` never reach it. The app vanished instantly and produced **no report at all**. These are now captured.
- **Native Crash Handler (NEW):** `libkitsugi_crash_handler.so` catches SIGSEGV/SIGABRT/SIGBUS/SIGILL/SIGFPE/SIGTRAP, writes a raw backtrace (signal, fault address, thread name) to `native_crash.txt`, then re-raises the signal so tombstone behaviour is preserved.
- **Silent-Death Detector (NEW — KitsugiSessionSupervisor):** Every session is journaled to disk. If the app died without a clean exit, the next launch scrapes the logcat buffer for **Fatal signal / ANR in / lowmemorykiller / am_kill / OutOfMemoryError** evidence and produces `unclean_exit.txt`, shown to the user on the next start.
- **Breadcrumbs:** Last 40 user actions plus the last screen are persisted, so reports answer "where exactly was the user?".
- **Crash Screen Actually Appears Now:** The main process no longer sleeps its main thread (`Thread.sleep(800)` removed); the crash screen runs in its own task (`taskAffinity`) and process, and the dead process is killed by the crash screen — task teardown can no longer wipe the crash window.
- **Report is always written:** A pending flag file + SharedPreferences are written BEFORE report generation, and `Throwable` is caught instead of `Exception` (OOM/StackOverflow can no longer break the reporter).
- **Handler installed earlier:** Installed in `attachBaseContext()`, so ContentProvider/Hilt/WorkManager init crashes are reported too.
- **Downloads export fixed:** Uses MediaStore on Android 10+ (`Downloads/Kitsugi/...`); the old code silently failed with EACCES on Android 11+.
- **Diagnostics sharing expanded:** "Send to developer" now shares `crash_log`, `crash_history`, `unclean_exit`, `native_crash`, `logcat_at_crash`, `post_mortem_logcat` and `breadcrumbs`.

### 2. 🐞 The "Cockroach" Guard That Was Hiding Silent Crashes
- **Root cause:** The main-thread guard swallowed every exception whose stack trace contained `com.lagradost.cloudstream3` *anywhere*. Since most UI paths traverse the embedded Cloudstream layer, **real app crashes were silently swallowed**, leaving a broken UI that later got killed by the system.
- Now only exceptions whose TOP frames are inside plugin/Cloudstream code are rescued, every rescue is written to the crash history, and the rescue budget is capped at 5 per session.
- When `Looper.loop()` returns (queue shutting down) the loop now terminates — the old code **spun at 100% CPU**, producing the "freezes then dies instantly" ANR.

### 3. 🧨 Duplicate Lazy Key List Crashes Fixed
- Fixed 7 lists that could throw `IllegalArgumentException: Key ... was already used`: home grid, modern home rows, airing calendar (list + grid), media grid dialog, manga source list, custom list editor and playback speed list. Keys are now guaranteed unique (index-suffixed).

### 4. 🖼️ Image Download Hardening
- **OOM protection:** notification thumbnails are decoded with `inSampleSize` instead of full-size, and downloads are capped at 48 MB (huge images could silently kill the process via memory pressure).
- **Android 10+ saving:** images are written through MediaStore into `Downloads/Kitsugi/Images` (previously never saved on Android 11+).
- Gallery navigation and download steps are recorded as breadcrumbs (visible in crash reports).

### 5. 🧾 Diagnostics
- New reports: `files/crash_log.txt`, `files/unclean_exit.txt`, `files/native_crash.txt`, `files/post_mortem_logcat.txt`, `files/breadcrumbs.txt`
- In-app: **Settings → About → Crash & Diagnostics Report**, or the "Unexpected Shutdown" dialog shown on launch.
