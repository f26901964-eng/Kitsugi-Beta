# Plan / Task — Keşfet Vitrini: Yatay Görsel ve Yumuşak Kaydırma

**Tarih:** 17 Ekim 2026  
**Proje:** Kitsugi-Beta  
**Çalışma dalı:** `arena/55d368b0-kitsugi-beta`  
**Başlangıç commit'i:** `4f2f3713b2f28fefef53244e23d24860f7497a06`  
**Durum:** Kod değişiklikleri tamamlandı; 66 SDK'sız JVM sözleşme testi geçti. Tam Android derlemesi ve cihaz doğrulaması bekliyor.

## 1. İstenen davranış

- Vitrinde manga, manhwa/manhua veya roman bulunmayacak; normal katalog rafları korunacak.
- **Tümü:** mevcut her **kaynak × bölüm** için bir rastgele öğe.
- **Tek kaynak:** mevcut her bölüm için iki rastgele öğe.
- Yakında Yayında, popüler, trend, en yüksek puanlı, yayındaki, yaklaşan, film, sezon, yeni eklenen ve kaynakların dizi/film bölümleri dahil edilecek.
- Yalnızca yüklenebilen, gerçek boyutları yatay olan görseller kabul edilecek. Posterle yedekleme yapılmayacak.
- Vitrin yüksekliği görsele göre değişmeyecek. Görsel, başlık, logo ve meta aynı sayfayla birlikte kayacak.
- Görselli logo yüklenmesi/hatası yerleşimi veya doğru öğeye tıklamayı bozmayacak.

## 2. Uygulama planı ve tamamlanan işler

### A — İçerik seçimi

- [x] Eski puan ağırlıklı 12/10 toplam kontenjanı kaldırıldı.
- [x] Kaynak/bölüm başına Tümü için 1, tek kaynak için 2 kontenjan uygulandı.
- [x] Yakında Yayında havuzu eklendi; Tümü modunda ortak havuz bir kez kullanılıyor.
- [x] Kitap türleri gerçek medya türüne göre elendi. TMDB/Simkl'in video içeren eski `*_MANGA` alanları korundu.
- [x] Ortak adaylar tekilleştirildi; küçük bölümler için alternatif ataması yapıldı.
- [x] Yeterli benzersiz/yatay görselli aday yoksa başka bölümlerin kotası artırılmıyor ve posterle doldurulmuyor.
- [x] Yenileme/kaynak seçimi bazlı rastgelelik eklendi; yeniden çizim ve logo yüklenmesi yeniden seçim yapmıyor.

### B — Görsel kabulü

- [x] `HomeHeroArtworkLoader` ile vitrinden önce yatay görsel hazırlığı eklendi.
- [x] Native yatay görsel, gerekirse mevcut TMDB/Fanart çözümü deneniyor.
- [x] Küçük Coil decode işleminin sonucunda `width > height` şartı kontrol ediliyor; kare/dikey/bozuk görseller eleniyor.
- [x] Başarısız adaydan sonra aynı bölümün diğer rastgele adayları deneniyor.
- [x] En fazla üç eşzamanlı hazırlama işi, ortak sonuç kullanımı ve iptal koruması uygulandı.
- [x] Kabul edilen URL sayfa açıkken sabit tutuluyor; kaynak kimlikleri ve kütüphane posteri değiştirilmiyor.
- [x] Bangumi öğesinin geçerli TMDB/Fanart görselini kaynak adından dolayı reddeden kontrol düzeltildi.

### C — Yerleşim ve kaydırma

- [x] Görünmez pager, özel ham sürükleme ve üst üste sayfa katmanları kaldırıldı.
- [x] Kararlı anahtarlı gerçek `HorizontalPager` kullanıldı; resim, logo ve metin aynı sayfanın çocukları oldu.
- [x] Görsel oranına bağlı yükseklik animasyonu kaldırıldı; bant ve shimmer aynı cihaz-bazlı yüksekliği kullanıyor.
- [x] Görseli kesmeden gösteren sabit `Fit` katmanı ve düşük opaklıklı arka dolgu eklendi.
- [x] Başlık/logo, alt başlık ve meta için sabit yuvalar kullanıldı.
- [x] Logo başarıyla yüklenene kadar metin korunuyor; logo hatası boş başlık bırakmıyor. Logo decode boyutu sınırlandı, RGB_565 kapatıldı.
- [x] Kaynak güncellemesi sürükleme/snap sırasında sayfa indekslerini değiştirmiyor.
- [x] Otomatik geçiş dokunma/sürükleme/TV odağı sırasında bekliyor; sonda çok sayfalık başa sarma yerine tek sayfalık ters yön geçişi yapıyor.
- [x] En fazla yedi okunabilir gösterge kullanıldı.
- [x] Başlık dokunuşu callback'i kararlı hale getirildi; dokunma kaynaklı yeniden çizim çift dokunma/tek dokunma işlemini iptal etmiyor.
- [x] Kullanılmayan eski ana sayfa poster/yükseklik/sayfa indeks kodu temizlendi. Detay sayfalarının bağımsız görsel davranışı korundu.

## 3. Yapılan doğrulamalar

- [x] Kotlin 2.0.21 + JRE 21 + gerçek JUnit 4.13.2 ile **66 test başarılı**.
- [x] Değişen 18 Kotlin dosyası Kotlin PSI ayrıştırıcısından sözdizimi hatası olmadan geçti. Bu, tam tip/API derlemesi değildir.
- [x] `git diff --check` ve `bash -n scripts/run_hero_contract_checks.sh` başarılı.
- [x] İlgili JVM testleri mevcut Android build workflow'una eklendi; bu oturumda uzak CI çalışması başlatılmadı.
- [x] Yedi Android cihaz testi eklendi: dikey/yatay sabit yükseklik, kaydırma sonrası doğru öğeye tıklama, başlık dokunuşu, kenarda geri sarmama, logo yuvası ve yeni kaynak sonrası etkin anahtarın korunması.

**Sınır:** SDK'sız sözleşme paketi gerçek seçim/raf/politika kodunu çalıştırır; Android ve Compose değer tipleri için küçük derleme shim'leri kullanır. Gerçek Compose çizimi, Coil ağ/decode entegrasyonu ve ViewModel bu paketle çalıştırılmış değildir.

## 4. Bekleyen task / cihaz QA

- [ ] Tam Android/Kotlin derlemesi: `./gradlew :app:compileFossDebugKotlin`
- [ ] Gerçek Android Gradle ortamında ilgili JVM testlerini çalıştır.
- [ ] Yedi cihaz testini `connectedFossDebugAndroidTest` ile çalıştır.
- [ ] Dikey telefon ve yatay telefon/tablette art arda sağ/sol kaydırma, kısa sürükleyip geri bırakma ve hızlı yön değiştirme kontrolü.
- [ ] Parmağı vitrinde tutarken otomatik geçişin ve kaynak güncellemelerinin beklediğini doğrula.
- [ ] Tümü ve yedi tek kaynak görünümünde mevcut bölümlerin 1/2 kotalarını kontrol et; manga/romanların yalnızca vitrin dışında kaldığını doğrula.
- [ ] Yatay görseli olmayan, kare/dikey görsel döndüren, 404/hata veren öğelerin vitrinde bulunmadığını gerçek ağla doğrula.
- [ ] Logo açık/kapalı, geç yüklenen logo, bozuk logo ve başlık dokunuşunu kontrol et.
- [ ] Ekran döndürme, uygulamadan geri dönme, yetişkin gizleme/bulanıklaştırma ve büyük yazı boyutu kontrolü.

**Derleme engeli:** Bu ortamda tam Android derleme denemesi `services.gradle.org` üzerinden Gradle 8.13 indirilirken ağ/TLS hatasına takıldı. Tam derleme veya cihaz testleri başarılı gibi işaretlenmemiştir.

## 5. Dosyalar ve teslim

- Ayrıntılı uygulama notu: `docs/VITRIN_YATAY_GORSEL_VE_KAYDIRMA_2026-10-17.md`
- SDK'sız kontrol komutu: `scripts/run_hero_contract_checks.sh` (Kotlin, JUnit ve Hamcrest yolları ortam değişkenleriyle verilir).
- ZIP içinde değişen ve yeni dosyalar **repo köküne göre özgün klasör yollarıyla** bulunur.
- `README_PAKET.txt` uygulama yönergelerini, `PAKET_ICERIGI.json` dosya listesini/SHA-256 değerlerini, `changes.patch` izlenen ve yeni dosyaların birleşik patch'ini içerir.
- ZIP, APK veya bütün proje yedeği değildir; Gradle önbellekleri, derleyici/JRE indirmeleri, `.git`, `local.properties`, imza anahtarları ve API anahtarı dosyaları dahil edilmez.

### Android ortamında hedefli doğrulama

```bash
./gradlew :app:testFossDebugUnitTest \
  --tests 'com.kitsugi.animelist.ui.screens.explore.HeroSelectionTest' \
  --tests 'com.kitsugi.animelist.ui.screens.explore.AllSourcesExploreTest' \
  --tests 'com.kitsugi.animelist.ui.components.HomeHeroPolicyTest' \
  --tests 'com.kitsugi.animelist.ui.components.HeroImageSelectionTest' \
  --tests 'com.kitsugi.animelist.ui.components.HomeHeroGestureModifiersTest'

./gradlew :app:connectedFossDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kitsugi.animelist.ui.components.KitsugiHeroSectionTest
```
