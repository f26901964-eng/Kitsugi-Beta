# Plan Task — Keşfet: “Yakında Yayında” konumu ve kaynak raflarının sırası

**Tarih:** 2026-10-09  
**Kapsam:** Android Keşfet ekranı · Tümü ve tek kaynak görünümleri

## Amaç

“Yakında Yayında” ortak şeridini TMDB’deki örnek konumla hizalamak ve anime, manga, film/dizi ile kaynağa özgü yatay rafları tüm kaynaklarda tutarlı, anlaşılır bir sıraya koymak.

## Beklenen davranış

1. Tek kaynak görünümünde kategori kısayollarının hemen ardından “Yakında Yayında” şeridi gösterilir; kategori içerik rafları şeritten sonra gelir.
2. Tümü görünümünde ortak şerit kaynak listelerinden önce bir kez gösterilir. Veri yüklenirken şeridin yerleşimi korunur ve iskelet kartlar görünür.
3. Tek kaynak ve Tümü görünümleri aynı `sourceSections` sıralama modelini kullanır:
   - Genel kaynaklar: popüler → trend → en yüksek puanlı → yayındaki/yaklaşan → film/sezon/yeni eklenen anime → manga rafları → kaynağa özgü türler.
   - TMDB ve Simkl: kaynaklarının trend/popüler/puan veya yayın grupları kendi verilerine uygun biçimde sıralanır.
4. İçeriği bulunan trend anime/manga, yüksek puanlı ve diğer kategori rafları her kaynakta görünür; Simkl’in kişisel izleme/listeleri keşif raflarından sonra korunur.
5. Kategori kısayolları da aynı gruplama mantığına göre düzenlenir ve anime/manga için yüksek puanlı kısayolları içerir.

## Uygulama özeti

- Tek kaynak sayfalarının yatay rafları ortak `sourceSections` modelinden oluşturuldu; böylece Tümü ve tek kaynak görünümündeki kategori sırası aynı hale geldi.
- `ExploreViewModel`’a yüksek puanlı anime ve manga listelerinin UI durumları eklendi.
- Ortak yayın şeridi kategori kısayollarının altına taşındı; Tümü görünümündeki yükleme durumu ve iskelet görünümü de aynı sözleşmeye alındı.
- TMDB/Simkl kaynaklarının başlıkları ve sıralaması korunarak mantıksal gruplar halinde düzenlendi. TMDB’nin kaynak-özel yaklaşan film/dizi kataloğu, ortak geri sayım şeridiyle karışmaması için “Yaklaşan Film ve Diziler” olarak adlandırıldı.
- Simkl kişisel izleme ve planlanan içerik rafları ayrı keşif sonrası bölümü olarak korundu.

## Değişen dosyalar

- `app/src/main/java/com/kitsugi/animelist/ui/screens/explore/AllSourcesExplore.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/explore/AllSourcesExploreContent.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/explore/DefaultExploreContent.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/explore/ExploreCategories.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/explore/ExploreScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/explore/ExploreViewModel.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/explore/TmdbExploreContent.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`
- `app/src/test/java/com/kitsugi/animelist/ui/screens/explore/AllSourcesExploreTest.kt`

## Doğrulama

- Kaynak raflarının sırasını ve TMDB/Tümü sıralama eşleşmesini doğrulayan birim testleri eklendi.
- `git diff --check` başarılı.
- Türkçe ve İngilizce kaynak XML dosyaları ayrıştırma kontrolünden geçti.
- Gradle/Kotlin test derlemesi çalıştırılamadı: sandbox’ta Java/JAVA_HOME bulunmuyor (`bash gradlew ...` bu nedenle başarısız oldu).
