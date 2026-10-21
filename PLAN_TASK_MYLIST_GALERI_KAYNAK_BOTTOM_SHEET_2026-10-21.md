# Plan Task — Listem Sıralama ve Galeri Kaynak Bottom Sheet'leri

**Tarih:** 2026-10-21  
**Durum:** Tamamlandı

## İstenenler

- Listem arama alanındaki sıralama seçicisini açılır bottom sheet olarak göstermek.
- Medya detayındaki Resimler kartının kategori seçicisini bottom sheet'e çevirmek.
- Galeri seçicisinde kategori ve kaynakları ayrı bölümlerde, görsel adetleriyle sunmak.
- Galeri önizlemesindeki görselleri önce kategoriye, ardından kaynağa göre ayırmak; kaynak seçildiğinde o kaynağın görsellerini galeride açmak.

## Uygulama

- `KitsugiMyListSortMenu`, mevcut sıralama yönü ve seçim davranışını koruyarak `ModalBottomSheet` tabanlı hale getirildi.
- `DetailGalleryCard` içindeki çapa bağlı açılır menü, kaydırılabilir kategori/kaynak seçme bottom sheet'i ile değiştirildi.
- Kaynak seçenekleri mevcut galeride bulunan kaynaklardan (ör. MAL/Jikan, Fanart.tv, TMDB) oluşturuluyor; her satırda kaynak adı ve görsel sayısı yer alıyor.
- Galeri önizlemesinde kategori başlıklarının altına kaynak başlığı ve o kaynağa ait görseller eklendi.
- Kaynak seçimi, hem `MediaEntryDetailPage` hem de `ApiResultDetailPage` üzerinden kaynakla sınırlı görsel listesini açıyor.

## Değişen dosyalar

- `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/components/KitsugiMyListSortMenu.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/EntryDetailTabContents.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/MediaEntryDetailPage.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailPage.kt`

## Doğrulama

- `git diff --check`: başarılı.
- `:app:compileDebugKotlin`: çalıştırılamadı; çalışma ortamında `java`/`JAVA_HOME` bulunmuyor.
