# PLAN — Detay Vitrini Boşluk ve Tıklanan Görseli Galeride Açma Düzeltmesi

**Tarih:** 2026-10-12  
**Dal:** `arena/a5a14949-kitsugi-beta`  
**Ana uygulama commit'i:** `4daa511`

## 1. Sorunlar

### 1.1. Vitrin görselinin altında gereksiz boşluk

Detay sayfasındaki iki hero bileşeni, görsel yüksekliğine ek olarak sabit `220.dp` bilgi alanı ekliyordu. Başlık, meta bilgiler ve rozetler bu ayrı koyu alanda gösterildiği için:

- dikey ekranda yatay arka planın altında büyük boşluk oluşuyordu,
- yatay ekranda dikey poster panelinin alt bölümü gereksiz yere uzuyordu,
- hızlı işlem düğmeleri ve sekmeler aşağı itiliyordu.

### 1.2. Vitrinde görünen görsele tıklanınca yanlış galeri öğesinin açılması

API detay hero'su tıklama sırasında ekranda gerçekten gösterilen `heroImageModel` yerine kayıt kaynağındaki `displayResult.imageUrl` değerini kullanıyordu. Yön değiştiğinde vitrin poster ile backdrop arasında geçiş yaptığı için galeride farklı bir görsel açılabiliyordu.

Ayrıca galeri yüklenirken `activeGalleryItems` güncellenmediğinden erken tıklamalar hiçbir şey açmayabiliyordu.

## 2. Çözüm Planı

1. Her iki detay hero bileşenindeki sabit `220.dp` bilgi alanını kaldır.
2. Hero kutusunun yüksekliğini doğrudan gösterilen görsel yüksekliğine eşitle.
3. Başlık, meta bilgiler ve rozetleri mevcut koyu gradyan üzerinde görselin alt bölümüne bindir.
4. Hero tıklama callback'ine ekranda gösterilen gerçek URL ile görsel kategorisini (`POSTER`/`BACKDROP`) aktar.
5. Tıklanan URL'yi galeri listesinde bul ve galeriyi o indeksle aç.
6. Kalite/CDN varyantlarını aynı görsel olarak eşleştir:
   - TMDB `w500` / `original`,
   - Bangumi kapak boyutları,
   - Kitsu poster/cover boyutları,
   - AniList, MAL ve Shikimori kalite yolları.
7. Görsel galeride henüz yoksa tıklanan URL'yi geçici olarak listenin başına ekle.
8. Galeri arka planda yüklenmeye devam ederken de hero tıklamasını hemen işle.
9. Seçim ve URL eşleme davranışları için birim testleri ekle.

## 3. Etkilenen Dosyalar

- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailHero.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailHeroSection.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiDetailLeftPanel.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/EntryDetailLeftPanel.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailPage.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/MediaEntryDetailPage.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/DetailHeroGallerySelection.kt`
- `app/src/test/java/com/kitsugi/animelist/ui/screens/detail/DetailHeroGallerySelectionTest.kt`

## 4. Kabul Kriterleri

- Dikey detay görünümünde yatay vitrinin altında sabit boş alan bulunmamalı.
- Yatay detay görünümünde dikey poster panelinin altı gereksiz yere uzamamalı.
- Başlık ve bilgiler görselin alt kısmındaki gradyan üzerinde okunabilir olmalı.
- Dikey görünümde backdrop'a tıklanınca aynı backdrop galeride açılmalı.
- Yatay görünümde poster tıklanınca aynı poster galeride açılmalı.
- Galeri verisi henüz yüklenmemiş olsa bile tıklanan hero görseli açılmalı.
- URL kalite varyantları galeride yinelenen farklı öğeler oluşturmamalı.

## 5. Doğrulama Planı

- Kotlin sözdizimi taraması.
- `git diff --check` ile boşluk/patch kontrolü.
- URL eşleme ve başlangıç indeksi için birim testleri.
- Java/Android Gradle ortamı bulunan makinede `testDebugUnitTest` çalıştırılması.
- Telefon/tablet üzerinde dikey ve yatay manuel görünüm kontrolü.
