# PLAN / TASK — Oyuncu Detayında Galeri Yüklenme Durumu ve Görsel Tıklama Düzeltmesi

**Tarih:** 2026-10-12  
**Dal:** `arena/800d75bf-kitsugi-beta`

## Sorun

Oyuncu/kişi detayına girildiğinde ana profil görseli görünürken ek kişi görselleri farklı kaynaklardan arka planda toplanıyor. Galeri listesi toplama tamamlanana kadar boş kaldığı için:

- Galeri düğmesi yükleniyor durumunda görünmüyor.
- Yükleme sürerken vitrin görseline dokunma, sayfa geri çağrısındaki `galleryLoading` kontrolü nedeniyle galeriyi açmıyor.

## Uygulama

- [x] Galeri düğmesini yükleme sürerken de göster; yükleniyor animasyonu devam etsin ve düğme yükleme süresince devre dışı kalsın.
- [x] Manşet/profil görselini ek kaynaklar tamamlanmadan galerinin ilk öğesi olarak yayımla.
- [x] Profil görseline dokunulduğunda galeri önizlemesini hemen aç; yükleme sürerken dokunmayı engelleme.
- [x] Galeri penceresi önizlemeyle açık kaldıysa ek görseller geldikçe pencereyi güncel listeye bağla.
- [x] Dikey, yatay ve kayan başlık galeri düğmelerinde yükleme durumunu göster.
- [x] İlk görselin galeride ilk sırada kalması ve yinelenmemesi için yardımcı mantığı ve birim testlerini ekle.

## Değişen Dosyalar

- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiGalleryIconButton.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/GalleryLoadTracker.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/StaffDetailComponents.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/StaffDetailPage.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/StaffDetailViewModel.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/StaffGalleryItems.kt`
- `app/src/test/java/com/kitsugi/animelist/ui/screens/detail/StaffGalleryItemsTest.kt`

## Kabul Kriterleri

- Ek kişi görselleri yüklenirken galeri simgesi ve yüklenme animasyonu görünür olmalı.
- Ana görsele yükleme devam ederken dokunulduğunda galeri, hazır olan ana görselle açılmalı.
- Ek görseller geldiğinde açık galeri listesi de güncellenmeli.
- Yükleme tamamlandığında galeri normal şekilde kullanılabilmeli.

## Doğrulama

- `git diff --check`: **Başarılı**
- `StaffGalleryItemsTest`: **Eklendi, çalıştırılmadı**
- `:app:testDebugUnitTest`: **Bu ortamda çalıştırılamadı — Java/JAVA_HOME kurulu değil**
