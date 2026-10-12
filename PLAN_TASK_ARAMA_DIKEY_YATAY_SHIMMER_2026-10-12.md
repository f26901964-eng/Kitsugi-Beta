# PLAN / TASK — Arama Shimmer'ının Dikey ve Yatay Ekranlara Uyarlanması

**Tarih:** 2026-10-12  
**Durum:** Kod değişiklikleri tamamlandı; Android derlemesi ortamda Java/JDK bulunmadığı için çalıştırılamadı.

## İstek
Arama yaparken görünen yükleme animasyonunun hem dikey hem yatay ekranlarda gerçek sonuç kartlarıyla uyumlu olması; bir kaynağın verisi geldikçe o kaynağın animasyonunun sonuçlarla değişmesi.

## Teşhis
`SearchScreen` içindeki çok kaynaklı raflar, genel `KitsugiShimmerMediaRow` bileşenini kullanıyordu. Bu iskelet sabit genişlikte ve dikey poster düzenindeydi. Ancak gerçek `KitsugiExploreMediaCard`, yatay ekranda daha geniş bir kart, solda poster ve sağda metin/meta bilgileri kullanıyor. Ayrıca önceki shimmer satırı yatay kaydırılabilir bir `LazyRow` değildi. Bu nedenle yükleme görünümü, yatay modda gelen sonuç kartlarının ölçü ve düzenini takip etmiyordu.

## Uygulama
- `KitsugiShimmer.kt` içine arama rafları için `KitsugiShimmerResponsiveMediaRow` eklendi.
  - Dikey mobil görünüm: gerçek kartla aynı 2:3 poster oranı ve sabit metin alanı.
  - Yatay mobil görünüm: gerçek kartla aynı kart genişliği, poster boyutları ve poster + bilgi düzeni.
  - TV görünümü: yatay cihazda da dikey poster düzeni korunuyor.
  - Raf `LazyRow` olduğu için küçük ekranlarda placeholder kartlar yatay kaydırılabiliyor.
  - Paylaşılan shimmer brush/offset kullanılarak mevcut animasyon performans yaklaşımı korundu.
- `SearchScreen.kt` içindeki çok kaynaklı arama rafı, gerçek sonuçlarla aynı genişlik, boşluk ve yatay iç dolgu değerlerini shimmer'a iletiyor.
- Her kaynağın kendi `isLoading` ve sonuç listesi davranışı korunuyor: veri dönen kaynak kartlarını gösterirken, yüklemesi süren diğer kaynakların shimmer'ı devam ediyor.

## Değişen dosyalar
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiShimmer.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchScreen.kt`

## Doğrulama
- `git diff --check`: başarılı.
- Değişen Kotlin dosyalarında ayraç dengesi kontrolü: başarılı.
- Gradle derlemesi denenemedi: ortamda `java` komutu ve `JAVA_HOME` yok.

## Cihazda kontrol adımları
1. Dikey modda Arama → Tümü → bir sorgu çalıştır: iskeletler 2:3 poster kartı gibi görünmeli ve raf yatay kaydırılabilmeli.
2. Cihazı yatay moda alıp aramayı tekrarla: iskeletler gerçek yatay sonuç kartlarıyla aynı boyda, poster solda ve bilgi alanı sağda olmalı.
3. Birden fazla kaynakla aramada kaynaklar sırayla yanıt verirken, tamamlanan kaynağın shimmer'ı sonuçlarla değişmeli; yanıt bekleyen diğer rafların animasyonu sürmeli.
4. Android TV görünümünde kart shimmer'ı dikey poster düzeninde kalmalı.
