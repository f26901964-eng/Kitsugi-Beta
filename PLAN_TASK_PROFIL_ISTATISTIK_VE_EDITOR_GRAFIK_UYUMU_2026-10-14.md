# PLAN/TASK — Profil istatistikleri ve düzenleyici panellerinde grafik stili uyumu

**Tarih:** 2026-10-14  
**Durum:** Kod değişiklikleri tamamlandı; Android derlemesi ortamda JDK olmadığı için doğrulanamadı.

## Amaç ve kapsam

- Profil istatistiklerinin detay sayfasındaki grafik kartlarının görsel dilini yakından izlemesi.
- Anime/manga istatistiklerinin farklı profil kaynaklarında ortak bir sunumla gösterilmesi.
- Yerel istatistik ekranında daha kapsamlı tür, durum, puan ve yıl dağılımlarının sunulması.
- Kaynağı ne olursa olsun liste düzenleme panellerindeki ortak satırların detay grafik kartlarıyla uyumlu görünmesi.
- Grafiklerin ve panellerin mevcut ekran sırası/akışının korunması.

## Uygulanan değişiklikler

1. **Ortak istatistik görsel bileşenleri**
   - `KitsugiStatsCard`, başlık/alt başlık, ölçüm kartı, durum dağılımı, dikey grafik ve yatay ilerleme çubukları eklendi veya ortak stile geçirildi.
   - Ortak `DetailedProfileStatsOverview`, özet metrikleri ve puan, uzunluk, durum, format, ülke, yayın yılı ve listeye başlama yılı dağılımlarını aynı kart dilinde sunuyor.

2. **Profil istatistikleri**
   - AniList ve diğer kullanıcı profili ortak detaylı genel görünümü kullanıyor.
   - MAL’ın sunduğu dağılımlar ortak grafik kartlarına geçirildi.
   - Kitsu, Shikimori ve Simkl için ayrıntılı anime/manga verileri ortak görünüme bağlandı; ayrıntılı veri yoksa eldeki özet/durum bilgilerini kullanan yedek görünüm korunuyor.
   - Yerel Kitsu kayıtlarından puan, durum, format, uzunluk, yayın yılı ve başlangıç yılı istatistikleri hesaplanıyor.

3. **Yerel istatistik ekranı**
   - Anime/manga seçimiyle durum, puan, format ve yayın yılı grafikleri gösteriliyor.
   - Özet metriklere manga bölümleri ve seçili medya türüne göre ortalama puan ile standart sapma eklendi.
   - Sadece manga bulunan bir kitaplıkta manga seçimi otomatik yapılıyor; boş durum yalnızca her iki liste de boşsa gösteriliyor.

4. **Liste düzenleme satırları**
   - Ortak `SheetRow`, yuvarlatılmış yüzey, ince kenarlık ve tema vurgu rengindeki ikon kutusuyla detay istatistik kartlarının görsel diline yaklaştırıldı.
   - Ortak bileşen kullanıldığı için AniList, MAL, manuel ve diğer kaynak düzenleyicilerine aynı stil uygulanıyor.

## Değişen kaynak dosyaları

- `app/src/main/java/com/kitsugi/animelist/ui/app/KitsugiProfileViewModel.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiEditorSharedComponents.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/stats/KitsugiAnalyticsComponents.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/profile/KitsuProfileContent.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/profile/MalProfileExtras.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/profile/ProfileSharedComponents.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/profile/ShikimoriProfileContent.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/profile/SimklProfileContent.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/profile/tabs/AniListStatsTab.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/profile/tabs/DetailedProfileStatsOverview.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/profile/tabs/UserProfileStatsTab.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/stats/StatsScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/stats/StatsViewModel.kt`

## Doğrulama

- Değişen Kotlin dosyalarındaki `R.string` başvuruları tarandı; karşılık gelen kaynakların tümü mevcut.
- `git diff --check` başarılı.
- Derleme komutu çalıştırılamadı: `./gradlew` çalıştırılabilir değil; `bash ./gradlew :app:compileDebugKotlin` ise `JAVA_HOME` ayarlı olmadığını ve Java bulunmadığını bildirdi.
- Bu nedenle otomatik test ve Android cihaz doğrulaması yapılmadı.
