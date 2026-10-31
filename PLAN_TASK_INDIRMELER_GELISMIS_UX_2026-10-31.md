# PLAN & TASK — İndirilenler Ekranı: Gelişmiş UX Paketi
**Tarih:** 2026-10-31 · **Sürüm:** v2.4.342 (beta) · **Dal:** `arena/2d31436e-kitsugi-beta`

> Bu belge, "İndirilenler" ekranı için istenen tüm geliştirmelerin **planını, uygulama
> sırasını, teknik kararlarını ve doğrulama adımlarını** içerir. Tamamlanan maddeler
> `[x]` ile işaretlidir.

---

## 1. Amaç / Kapsam

İndirilenler ekranı üç sekmeden oluşur: **Videolar**, **Altyazılar**, **Resimler**.
Hedef: resim kutucuklarının ekrana göre otomatik yerleşmesi, indirme süreçlerinin
ayrıntılı izlenmesi (hız / kalan süre / boyut) ve liste yönetiminin (arama, sıralama,
gruplama, toplu seçim) tamamen elden geçirilmesi.

**Kısıtlar**
- Mevcut davranışlar (Scoped Storage silme akışları, galeri, oynatıcı) bozulmayacak.
- Tüm metinler TR + EN kaynaklarına eklenecek (`check_localization_resource_parity.py` yeşil kalacak).
- Sabit ölçüler (sabit sütun sayısı, sabit kart genişliği) yerine **ölçüye duyarlı** düzen.
- Ağır işler `Dispatchers.IO/Default` üzerinde; ana iş parçacığında disk taraması yok.

---

## 2. Görev Listesi

### A. Resimler sekmesi — ekrana göre otomatik ızgara
- [x] Sabit `FlowRow` + `chunked(4)` + `Modifier.width(160.dp)` yapısının kaldırılması
- [x] `BoxWithConstraints` + `LazyVerticalGrid(GridCells.Fixed(n))` ile sütun sayısının
      ekran genişliğinden hesaplanması (hedef kutucuk genişliği ÷ kullanılabilir genişlik)
- [x] 2–10 sütun aralığına kenetleme (çok dar / çok geniş uçların kırpılması)
- [x] Kutucuğun hücreyi doldurması; önizlemenin `aspectRatio(4f/3f)` ile ölçeklenmesi
- [x] Kutucuk yoğunluğu seçimi: **Otomatik / Kompakt / Geniş** (kalıcı)
- [x] Grup başlığının ızgarada tam satır kaplaması (`GridItemSpan(maxLineSpan)`)
- [x] `items(count, key, span, contentType)` kullanımı (yüzlerce resimde içerik yeniden kurulmasın)
- [x] Silme sonrası hayalet kart olmaması için `hiddenPaths` (iyimser gizleme)
- [x] Grup silme onayı, çoklu seçim (uzun basış), tümünü seç, seçimi temizle
- [x] Kutucukta boyut + çözünürlük rozeti; `BitmapFactory.inJustDecodeBounds` ile piksel ölçüsü

### B. Özet kartı ve canlı ilerleme
- [x] Özet kartı: video / altyazı / resim adet + boyut, toplam alan
- [x] Devam eden indirmelerin birleşik ilerlemesi (yüzde + çubuk)
- [x] **Anlık toplam hız** (iki örnek farkı + üstel hareketli ortalama)
- [x] **Tahmini kalan süre** (kalan bayt ÷ hız)
- [x] Cihazdaki **boş alan** rozeti (`StatFs`, yalnızca liste değişince hesaplanır)
- [x] "Tümünü duraklat" / "Tümünü sürdür"
- [x] **Hız geçmişi mini grafiği** (son ~40 sn, Canvas sparkline, saniyede 1 örnek)
- [x] Satır içi ayrıntı: hız, kalan süre, indirilen/toplam, parça sayısı, eklenme tarihi

### C. Liste yönetimi (Videolar)
- [x] Durum filtreleri: Tümü / İndiriliyor / Duraklatıldı / Tamamlandı / Hatalı (adetlerle)
- [x] Sıralama: **Kuyruk sırası** (varsayılan), Başlık, Bölüm, Boyut, En yeni (kalıcı)
- [x] **İçeriğe göre gruplama + daralt/aç** (kalıcı tercih, grup başına rozet)
- [x] **Çoklu seçim** (uzun basış) + tümünü seç + onaylı toplu silme
- [x] **Kuyruk önceliği**: bekleyen işi yukarı/aşağı taşıma
      (`AnimeDownloadManager.moveQueuedDownload` — yalnızca `QUEUE` kayıtları arasında takas)
- [x] Tek bölüm silmede onay penceresi; "Tamamlananları sil (n)" toplu temizlik
- [x] Her sekmede manuel **Yenile**; ekran `ON_RESUME` olduğunda otomatik tazeleme
- [x] Arama açıkken / seçim modunda **geri tuşu** ekrandan çıkmaz

### D. Altyazılar
- [x] Sıralama: en yeni / en eski / isim / boyut
- [x] **Çoklu seçim + onaylı toplu silme** (`DownloadStorage` üzerinden, MediaStore onayı dahil)
- [x] Yenile düğmesi

### E. Ortak / altyapı
- [x] `DownloadsUiPrefs` (SharedPreferences): kutucuk boyutu, resim sıralaması,
      gruplama, video sıralaması, video gruplama
- [x] `DownloadsSelectionBar` ortak bileşeni
- [x] `DownloadsFilterChip`, `DownloadsEmptyState`, `DownloadsSearchField` ortak bileşenleri
- [x] `DownloadsImagesTab` ayrı dosyaya çıkarıldı (okunabilirlik)
- [x] Tüm yeni metinler TR + EN (`downloads_*` anahtar ailesi)
- [x] Sürüm notu + `RELEASE_NOTES.md`

---

## 3. Teknik Kararlar

| Konu | Karar | Gerekçe |
|---|---|---|
| Sütun sayısı | `availableWidthDp ÷ targetCellWidthDp` (2..10) | Sabit sütun, dikey/yatay ve tablet farkını yok sayıyordu; hedef kutucuk genişliği ile ekran ne olursa olsun gözle dengeli sonuç çıkıyor. |
| Izgara | `LazyVerticalGrid` + `items(count,key,span)` | `chunked(4)` + `FlowRow` tüm kartları aynı anda compose ediyordu (313 resimde jank). |
| Kart ölçüsü | `Modifier` verilmez, hücre genişliğine bırakılır | Sabit `160.dp` geniş ekranda sağda boşluk bırakıyordu. |
| Kuyruk sırası | Listenin dizilişi + takas (`swap`) | `getNextDownload()` zaten listenin başındaki `QUEUE` kaydını seçiyor; takas, araya giren diğer durumların konumunu bozmuyor. |
| Sıralama | `QUEUE_ORDER` için `compareBy(statusRank, index)` | Devam eden iş üstte kalırken kuyruk önceliği korunur (kararsız sort riski yok). |
| Hız ölçümü | 2 örnek farkı + EMA (3:1) | İndirme yöneticisi hız tutmuyor; tek karelik sıçramalar yumuşatılır. |
| Sparkline | `Canvas` + `Path` + `Stroke` | Ek bağımlılık yok, çizim maliyeti düşük (40 nokta). |
| Boş alan | `StatFs.availableBytes` | Depolama planlaması; her ilerleme karesinde okunmaz. |
| Tercihler | SharedPreferences (`kitsugi_downloads_ui`) | Ekranın ViewModel'i yok; küçük ve kalıcı görünüm tercihleri için yeterli. |
| Yenileme | `LifecycleEventObserver(ON_RESUME)` + manuel düğme | Galeriden indirip dönüldüğünde liste bayat kalıyordu. |

---

## 4. Dokunulan Dosyalar

| Dosya | Değişiklik |
|---|---|
| `ui/screens/offline/DownloadsScreen.kt` | Yeniden düzenlendi; özet kartı, arama, filtre/sıralama, gruplama, seçim, onaylar, sparkline |
| `ui/screens/offline/DownloadsImagesTab.kt` | **Yeni** — otomatik ızgara, kutucuk boyutu, sıralama, çoklu seçim |
| `ui/screens/offline/DownloadsUiPrefs.kt` | **Yeni** — kalıcı görünüm tercihleri + enum'lar |
| `data/local/AnimeDownloadManager.kt` | `moveQueuedDownload()`, `queuedCount()` |
| `res/values/strings.xml`, `res/values-en/strings.xml` | ~57 yeni metin (TR/EN) |
| `app/build.gradle.kts` | `appVersionName = 2.4.342` |
| `RELEASE_NOTES.md`, `release_notes_v2.4.341.md`, `release_notes_v2.4.342.md` | Sürüm notları |
| `PLAN_TASK_INDIRMELER_GELISMIS_UX_2026-10-31.md` | Bu belge |

---

## 5. Doğrulama (yapılan / yapılacak)

**Bu ortamda yapılanlar**
- [x] `scripts/check_localization_resource_parity.py` → *in sync* (TR/EN + format yer tutucuları)
- [x] `scripts/audit_hardcoded_turkish.py` → `ui/screens/offline/*` için bulgu yok
- [x] Kotlin dilbilgisi (tree-sitter-kotlin) → 4 dosya da hatasız
- [x] XML doğrulama (`strings.xml` TR/EN) → geçerli
- [x] `R.string.*` referans denetimi → eksik anahtar yok

**Cihaz/CI tarafında yapılması gerekenler** (bu sandbox'ta Android SDK/JDK yok)
- [ ] `./gradlew :app:assembleDebug` (veya CI `android-build.yml`)
- [ ] Dikey/yatay/tablet ve katlanabilir ekranda ızgara sütun sayısı gözle kontrolü
- [ ] 300+ resimli grupta kaydırma akıcılığı (jank yok)
- [ ] Kuyruk sırası: 3 bekleyen indirmeyi yukarı/aşağı taşıyıp servis sırasının değiştiğini doğrula
- [ ] Android 11+ / 13+ (Scoped Storage) toplu altyazı & resim silme onayı
- [ ] TV (D-pad) odak sırası: seçim çubuğu ve grup başlığı

---

## 6. Bilinen Sınırlar / Sonraki Adımlar

- Videolar sekmesinde **sürükle-bırak** sıralama yok; yukarı/aşağı düğmeleri kullanılıyor
  (dokunma/DPAD ile erişilebilir, kazara sürükleme riski yok).
- Kuyruk sırası yalnızca **Kuyruk sırası** görünümünde anlamlı; diğer sıralamalarda düğmeler gizlenir.
- Hız geçmişi oturum içidir (diske yazılmaz) — gereksiz veri birikimini önlemek için.
- Sonraki tur fikirleri: eşzamanlı indirme sayısı kısayolu, Wi-Fi'de otomatik devam,
  resimlerde "paylaş/dışa aktar", indirme geçmişi arşivi.
