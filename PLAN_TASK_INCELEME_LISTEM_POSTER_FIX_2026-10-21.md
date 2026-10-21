# Plan / Task: İnceleme penceresi, Listem kart yoğunluğu ve detay posteri

Tarih: 2026-10-21

## Sorunlar
1. Detay sayfasındaki inceleme kartları görünürken “Tüm İncelemeler” penceresi boş kalabiliyor.
2. Listem'in kare kart düzeni dar telefon ekranlarında üç sütun nedeniyle aşırı sıkışık.
3. Dikey ekranda bazı detay sayfaları poster mevcut olsa bile yatay artwork tercihi yapıyor.

## Yapılan değişiklikler
- `KitsugiAllReviewsBottomSheet` artık açıldığı anda detay sekmesinde zaten yüklenmiş incelemelerle başlıyor; mevcut tam liste önbelleği varsa o öncelikli.
- `ReviewsTab` mevcut `reviewsList` verisini yeni `initialReviews` parametresiyle pencereye iletiyor.
- Listem için dar, dikey ekranlarda sütun sayısı 3'ten 2'ye düşürüldü. Tablet ve yatay geniş ekran eşikleri korunuyor.
- Detay hero artwork tercihi için dikey ekran poster eşiği 0.55'ten 0.75'e yükseltildi; bu, daha fazla dikey cihazda posterin tercih edilmesini sağlıyor.

## Değişen dosyalar
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiAllReviewsBottomSheet.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ReviewsTab.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/DetailHeroGallerySelection.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListComponents.kt`

## Doğrulama
- `git diff --check`: başarılı.
- `./gradlew :app:compileDebugKotlin`: çalıştırılamadı; ortamda Java/JAVA_HOME bulunmuyor.

## Kabul kriterleri
- İnceleme önizlemesi olan bir detayda “Tüm İncelemeler” açıldığında içerik anında görünür.
- Dar telefon görünümünde Listem kare kartları satır başına iki kart gösterir.
- Dikey ekranlarda artwork poster tercihi, önceye göre daha geniş cihaz oranı aralığında uygulanır.
