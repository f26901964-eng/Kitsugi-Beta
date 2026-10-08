# 📋 PLAN / TASK — Çökme Teşhis & Raporlama Onarımı (v2.4.206)

**Durum:** Kod tarafı TAMAMLANDI ✅ · Cihaz doğrulaması BEKLİYOR ⏳
**Dal:** `arena/fb29b37f-kitsugi-beta` · **Commit:** `e1d5a1f` · **Sürüm:** 2.4.206
**İlgili rapor:** `COKME_TESHIS_VE_COZUM_RAPORU.md`

---

## 🎯 Hedef

Uygulamanın "pat diye" kapanması ve **hiçbir çökme raporunun üretilmemesi** sorununu çözmek.
Kök neden: Java `UncaughtExceptionHandler`'ın native çökmeleri (SIGSEGV/SIGABRT), LMKD/OOM kill,
ANR sonrası öldürme ve SIGKILL durumlarını **hiç görememesi** + rapor hattındaki 7 ayrı tasarım hatası.

---

## ✅ TAMAMLANAN İŞLER (Kod)

### TASK-1 · Çökme yakalayıcı altyapısı
- [x] `core/diagnostics/KitsugiCrashHandler.kt` (YENİ) — tek giriş noktası; bayrak→rapor→logcat→ekran sırası
- [x] `attachBaseContext()` içinde kurulum (provider/erken init çökmeleri artık raporlanıyor)
- [x] `catch (Throwable)` kullanımı (OOM/StackOverflowError raporu engelleyemiyor)
- [x] Ana thread'i bloklamama (eski `Thread.sleep(800)` kaldırıldı) + bekçi thread
- [x] `applicationContext` null olabilir tuzağı (attachBaseContext) giderildi

### TASK-2 · Native çökme yakalayıcı
- [x] `cpp/kitsugi_crash_handler.cpp` (YENİ) — SIGSEGV/SIGABRT/SIGBUS/SIGILL/SIGFPE/SIGTRAP
- [x] `CMakeLists.txt` → `libkitsugi_crash_handler.so` hedefi (16 KB sayfa hizası korundu)
- [x] `core/diagnostics/NativeCrashBridge.kt` (YENİ) — JNI köprüsü + yaş sınırlı okuma
- [x] `proguard-rules.pro` → R8 sınıfı yeniden adlandırıp JNI'yi bozmasın

### TASK-3 · Sessiz ölüm dedektörü (post-mortem)
- [x] `core/diagnostics/KitsugiSessionSupervisor.kt` (YENİ) — oturum defteri + kalp atışı
- [x] Eylem izi (breadcrumbs, son 40 eylem) + "son ekran" kaydı
- [x] Sonraki açılışta logcat kazısı: `Fatal signal` / `ANR in` / `lowmemorykiller` / `am_kill` / OOM
- [x] `unclean_exit.txt` raporu + "Beklenmedik Kapanma" penceresi (arka plan kapanmaları rahatsız etmez)
- [x] Ölüm sebebi sınıflandırması (NATIVE / ANR / LMKD / JAVA OOM / SİSTEM KILL / BİLİNMİYOR)

### TASK-4 · Çökme ekranının gerçekten açılması
- [x] Manifest: çökme ekranı **kendi görevinde** (`taskAffinity`) + `excludeFromRecents`
- [x] Ölü süreci çökme ekranı kapatıyor (PID doğrulaması: `/proc/<pid>/cmdline`)
- [x] Compose çizilemezse düz-Android son çare ekranı
- [x] `:crash` süreci kendisi çökerse: kanıt + bayrak + süreç kapatma

### TASK-5 · Raporlama kalitesi
- [x] `KitsugiCrashLogger` — 3 kanallı "gösterilmemiş çökme" kontrolü (prefs + bayrak dosyası + rapor dosyası)
- [x] Raporlara eylem izi + son ekran + native iz gömme
- [x] `SimpleDateFormat` thread-safety (kilit) — çok thread'li kullanım düzeltmesi
- [x] İndirilenler'e kayıt: Android 10+ için **MediaStore** (`İndirilenler/Kitsugi/...`)
- [x] AboutScreen: `crash_log`, `crash_history`, `unclean_exit`, `native_crash`, `logcat_at_crash`, `post_mortem_logcat`, `breadcrumbs` paylaşımı
- [x] MainActivity: çökme kontrolü IO thread'inde + 2.5 sn gecikmeli ikinci kontrol

### TASK-6 · Gerçek çökme sebepleri (statik analizde bulundu)
- [x] **7 listede duplicate Lazy key** (`IllegalArgumentException: Key ... was already used`):
      ana sayfa grid'i · modern ana sayfa satırları · yayın takvimi (liste+grid) ·
      medya grid penceresi · manga kaynak listesi · özel liste editörü · oynatma hızı listesi
- [x] Resim indirme: 48 MB boyut sınırı + örneklemeli (inSampleSize) thumbnail decode → OOM riski
- [x] Resim kaydetme: Android 10+ MediaStore (eski kod Android 11+ üzerinde hiç kaydedemiyordu)
- [x] Cockroach: üst kare kontrolü, raporlama, 5 kurtarma limiti, `Looper.loop()` CPU döngüsü fix
- [x] Galeri gezinmesi + resim indirme adımları eylem izine eklendi

---

## ⏳ BEKLEYEN İŞLER (Cihaz doğrulaması)

### TASK-7 · APK derleme ve kurulum
- [ ] GitHub Actions → “Build & Publish Release APK” → version `2.4.206`, dal `arena/fb29b37f-kitsugi-beta`
- [ ] APK'yı telefona kur (eski sürümün üzerine)

### TASK-8 · Çökme ekranının doğrulanması
- [ ] Kasıtlı çökme üret (Ayarlar → Hakkında → teşhis ekranı yoksa: normal kullanımda çökmesini bekle)
- [ ] Çökme ekranı açılıyor mu? (yeni görev/süreç düzeni)
- [ ] Ekranda “▶ SON BİLİNEN EKRAN” ve “▶ UYGULAMA EYLEM İZİ” bölümleri dolu mu?

### TASK-9 · Sessiz ölüm dosyalarının doğrulanması
- [ ] Uygulama sessizce kapanırsa, tekrar aç → “Beklenmedik Kapanma Tespit Edildi” penceresi gelmeli
- [ ] `files/unclean_exit.txt` oluşuyor mu? (Ayarlar → Hakkında → paylaş ile de görülebilir)
- [ ] Native çökme sonrası `files/native_crash.txt` oluşuyor mu?
- [ ] Release (R8'li) derlemede native yakalayıcı yükleniyor mu? (logcat: `KitsugiNativeCrash: Native crash handler installed.`)

### TASK-10 · Rapor toplama ve kalan çökmelerin temizlenmesi
- [ ] Çökmeyi tekrar üret: gezinme · detay sayfası · galeri · resim indirme · liste→detay
- [ ] “Geliştiriciye Gönder (Tüm Dosyalar)” ile raporu paylaş
- [ ] Rapordaki `▶ ÖLÜM SEBEBİ` satırına göre dağıtım:
  - `NATIVE ÇÖKME (SIGSEGV)` → `native_crash.txt` geri izini sembolleştir → ilgili native modül (oynatıcı/decoder/GPU)
  - `ANR` → `▶ SON EKRAN` + breadcrumbs ile kilitlenen bileşeni bul
  - `BELLEK YETERSİZLİĞİ (LMKD)` → görsel/bellek yükünü azalt (Coil cache, blur, galeri sayfa boyutu)
  - `TAM STACKTRACE` → doğrudan kod satırına git

### TASK-11 · Şüpheli (kanıtlanmayı bekleyen) maddeler
- [ ] Tam ekran `Modifier.blur` (cinematic loading + NSFW bulanıklık) → native raporda
      `libhwui`/`Skia`/`libGLES` karesi çıkarsa blur'u opsiyonel yap / bitmap blur'a çevir
- [ ] `MangaExtensionTab` kaynak listesi (1300+ kayıt) performans taraması
- [ ] Broad `catch (e: Exception)` + iptal edilen coroutine etkileşimleri (NPE adayları)

---

## 🧪 Doğrulama durumu (bu ortamda yapılabilen)

| Kontrol | Durum |
|---|---|
| Kotlin sözdizimi (tree-sitter ayrıştırıcı) — 19 dosya | ✅ 0 hata |
| Parantez/blok denge kontrolü | ✅ |
| JNI sembol adı ↔ ProGuard kuralı eşleşmesi | ✅ elle doğrulandı |
| Android SDK/JDK ile derleme (APK) | ❌ ortamda yok — CI'da yapılacak |
| Cihazda çalıştırma (çökme ekranı, rapor dosyaları) | ❌ cihazda yapılacak (TASK-8/9/10) |

---

## 📦 Teslim edilen paket içeriği

- `degisen_dosyalar/` → v2.4.206'da değişen/eklenen tüm kaynak dosyalar (repo yapısıyla aynı)
- `COKME_TESHIS_VE_COZUM_RAPORU.md` → kök neden analizi (8 madde) + mimari şeması
- `PLAN_TASK_COKME_TESHISI_V2.4.206.md` → bu dosya
- `release_notes_v2.4.206.md` → sürüm notları (TR + EN)
- `Kitsugi-v2.4.206.patch` → tek dosyada `git diff` yaması (uygulanabilir)
- `OKUBENI.txt` → paket açıklaması ve uygulama talimatı
