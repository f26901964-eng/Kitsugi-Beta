# PLAN / TASK — Global Scroll GC Thrashing ve Donma Düzeltmesi

**Tarih:** 2026-10-10  
**Öncelik:** Kritik  
**Kapsam:** Keşfet, Arama, Tümünü Gör ve detaylara geçiş sırasında görülen genel jank/donma

## Telefondan Gelen Bulgular

- GPU frametime değerleri normal aralıkta (P50 4 ms, P90 7 ms, P99 9 ms).
- UI frametime P99 yaklaşık 1200 ms.
- Choreographer 81–93 kare atlandığını raporluyor.
- Yoğun allocation ve GC döngülerinde yaklaşık 35 MB / 1 milyon nesne temizleniyor.
- Sorun GPU çizim kapasitesinden çok ana thread işi, Compose invalidation ve allocation churn ile uyumlu.

## Kök Nedenler

### 1. Ana Looper içine ikinci Looper.loop kurulması

`KitsugiApplication`, ana thread'e post edilen callback içinde kalıcı bir `Looper.loop()` başlatıyordu. Bu yapı ActivityThread mesaj döngüsünü iç içe döngü arkasında bırakıyor, bazı UI/Window hatalarını yutup yarım kalmış UI durumunun yaşamasına neden oluyordu.

### 2. Debug logcat'in sürekli dosyaya yazılması

Debug süreçte `logcat -f ... *:D` çalıştırılıyor ve çok sayıdaki debug kaydı sürekli depolamaya yazılıyordu. Bu işlem görsel decode, ağ cache ve Room I/O ile yarışabiliyordu.

### 3. Bottom bar görünürlüğünün ağır ekran ağacına Boolean olarak taşınması

`bottomBarScrollState.isVisible`, `AppRoot` içinde okunup `TabPagesContext` üzerinden Keşfet ve Arama ekranlarına Boolean olarak geçiriliyordu. Görünürlük değişimleri ağır sayfa composable'larını invalidate ediyor; raf, kart, callback ve modifier yapılarının yeniden kurulmasına yol açıyordu.

### 4. Hero animasyonunda kare başına nesne üretimi

Her pager karesinde `listOf`, `distinct`, `mapNotNull`, `sortedBy` ve `HeroPageLayer` nesneleri oluşturuluyordu. Offset/visibility değerleri composition içinde okunuyordu.

### 5. Hero göstergesinde kare başına layout

Gösterge genişliği animasyon karesinde `.width(...)` ile değişiyor, tüm gösterge satırını tekrar measure/layout aşamasına sokuyordu.

## Uygulanan Çözüm

- İç içe ana Looper döngüsü kaldırıldı.
- Uygulama içi sınırsız debug logcat dosya yönlendirmesi kaldırıldı.
- `ScrollVisibilityState` stabil holder olarak geçirildi.
- Bottom bar state okuması küçük `KitsugiScrollToTopFab` restart scope'una izole edildi.
- Keşfet ve Arama ana composable'ları bottom bar yön değişimlerinde recompose olmaktan çıkarıldı.
- Hero görünür sayfa indeksleri yalnız gerçek sayfa değişiminde `remember` ile hesaplanıyor.
- Pager offset, alpha ve translation değerleri `graphicsLayer` aşamasında okunuyor.
- Gösterge genişliği sabitlendi; animasyon `scaleX` ile GPU katmanına taşındı.

## Değişen Dosyalar

1. `app/src/main/java/com/kitsugi/animelist/KitsugiApplication.kt`
2. `app/src/main/java/com/kitsugi/animelist/AppRoot.kt`
3. `app/src/main/java/com/kitsugi/animelist/AppRootTabPages.kt`
4. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiHeroSection.kt`
5. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiScrollToTopFab.kt`
6. `app/src/main/java/com/kitsugi/animelist/ui/screens/explore/ExploreScreen.kt`
7. `app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchScreen.kt`

## Doğrulama Görevleri

- [ ] Yeni APK ile Keşfet hızlı fling testi
- [ ] Yön değiştirerek bottom bar aç/kapa testi
- [ ] Hero otomatik geçişi sırasında raf kaydırma testi
- [ ] Arama sonuçları hızlı kaydırma ve sayfalama testi
- [ ] Tümünü Gör grid/liste testi
- [ ] Yeni `dumpsys gfxinfo` ile P95/P99 karşılaştırması
- [ ] Yeni logcat'te `Skipped frames` olaylarının karşılaştırılması
- [ ] GC frekansı, freed object sayısı ve freed MB değerlerinin karşılaştırılması
- [ ] Perfetto trace ile ana thread uzun task kontrolü

## Başarı Kriterleri

- 1 saniye üzerindeki UI frame sürelerinin ortadan kalkması veya belirgin biçimde azalması.
- Hızlı kaydırmada 80+ skipped frame kümelerinin tekrarlamaması.
- Hero geçişinde kare başına composition/layout oluşmaması.
- Bottom bar görünürlüğü değişirken Keşfet/Arama ana içeriklerinin yeniden compose edilmemesi.
- Aynı kullanım senaryosunda GC tarafından temizlenen geçici nesne sayısının ve frekansının belirgin biçimde düşmesi.
