# PLAN / TASK — İlerleme Alanı, Kart Çerçeveleri, Hesap Durumları, Bildirim Kapıları ve Vitrin Tıklaması

- **Tarih:** 2026-10-12
- **Dal:** `arena/c3238060-kitsugi-beta`
- **Durum:** Kod değişiklikleri tamamlandı; statik kontroller geçti. Gradle/cihaz doğrulaması JDK bulunmadığı için yapılamadı.
- **Kapsam:** Kullanıcının 8 ekran görüntüsünde işaretlediği beş etkileşim ve görünüm sorunu.

## İstekler ve yapılanlar

### 1. Manga/anime ilerleme sayısını doğrudan düzenleme
- İlerleme sayısının dokunma hedefi en az `48.dp × 32.dp` yapıldı.
- `SheetRow` tıklama callback'i yokken etkisiz/disabled parent `tvClickable` kurmuyor; böylece ebeveyn pointer alanı sayısal alanın odağını engellemiyor.
- Artı/eksi kontrolleri korunuyor; düzenleme ortak medya editörü üzerinden manga ve anime için geçerli.

### 2. Yaklaşan içerik kartlarının çerçeveleri
- Keşfet yatay "Yakında Yayında" kartı ve yer tutucusuna `KitsugiColors.Border` ile çerçeve eklendi.
- Detaylı sezonluk medya kartlarında varsayılan çerçeve geri getirildi; listede olan kartın yeşil vurgusu korunuyor.

### 3. Profil/Listem bağlantısız hesap görünümü
- `KitsugiAccountNotConnectedState` ortak kartı oluşturuldu ve Profil ile Listem tarafından kullanılıyor.
- AniList, MyAnimeList, Simkl, Kitsu, Shikimori ve Bangumi için Profil stili logo/başlık/açıklama/bağlan düğmesi ve ilgili bağlantı callback'i korunuyor.
- Süresi dolmuş Simkl oturumu Listem'de yeniden bağlanma metni/eylemiyle gösteriliyor.

### 4. Bildirim ayarları ve planlama hesap bağlantısıyla sınırlandırıldı
- Hesap bağlantı durumları mevcut `AuthViewModel`/`SettingsContext` üzerinden Preferences ekranına aktarılıyor.
- Kaynağa özel bildirim anahtarları kendi hesabı bağlanmadan kapalı ve devre dışı; genel Airing için desteklenen herhangi bir kaynağa bağlantı gerekiyor.
- Etkinleştirme callback'leri de hesabı doğruluyor; kaynağa bağlı olmayan toggle programatik olarak açılamıyor.
- `NotificationScheduler.reconcile` etkin ayarları gerçek kimlik bilgileriyle eşleştiriyor; uygulama başlangıcında, cihaz yeniden açıldığında, ayar/interval değişikliklerinde ve hesap bağlama/ayırma olaylarında işi yeniden değerlendiriyor.
- Global Airing, Bangumi dâhil en az bir bağlantı gerektiriyor. Kaynağa özel iş yalnızca aynı kaynak bağlıyken planlanıyor.
- Worker her sağlayıcıyı kendi credentials bilgisiyle kapılandırıyor; etkin kaynak kalmadığında eski periyodik işi iptal ediyor.
- Planlama kuralları için birim testleri eklendi.

### 5. Vitrin kartına dokunma ve doğru detay sayfası
- Pager üst üste çizilen komşu sayfalar arasından görsel olarak önde olan sayfayı hit-test için kullanıyor; türetilen durum güncel görünür sayfa listesine göre yeniden hesaplanıyor.
- Çift dokunmayla başlık kopyalama davranışı korunurken vitrin başlık/görsel alt alanındaki tek dokunuş, ilgili görünür öğenin detayını açıyor.
- Vitrin hit-test yardımcısı için birim testleri eklendi.

## Değişen kaynaklar

- `app/src/main/java/com/kitsugi/animelist/AppRoot.kt`
- `app/src/main/java/com/kitsugi/animelist/AppRootSettingsExtras.kt`
- `app/src/main/java/com/kitsugi/animelist/KitsugiApplication.kt`
- `app/src/main/java/com/kitsugi/animelist/core/notifications/AiringNotificationWorker.kt`
- `app/src/main/java/com/kitsugi/animelist/core/notifications/NotificationScheduler.kt`
- `app/src/main/java/com/kitsugi/animelist/core/recommendations/TvChannelBootReceiver.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/app/AuthViewModel.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/DetailedSeasonalMediaCard.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/HomeHeroGestureModifiers.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiAccountNotConnectedState.kt` (**yeni**)
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiEditorSharedComponents.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiHeroSection.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiPreferencesSettingsDialog.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/explore/ExploreComponents.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListComponents.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListEmptyState.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/profile/ProfileSharedComponents.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/settings/SettingsScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/settings/SettingsScreenParameters.kt`
- `app/src/main/java/com/kitsugi/animelist/utils/CopyUtils.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`
- `app/src/test/java/com/kitsugi/animelist/core/notifications/NotificationSchedulerTest.kt` (**yeni**)
- `app/src/test/java/com/kitsugi/animelist/ui/components/HomeHeroGestureModifiersTest.kt` (**yeni**)

## Doğrulama

- [x] `git diff --check`
- [x] Türkçe ve İngilizce string resource XML parse kontrolü
- [ ] `:app:testFossDebugUnitTest` ve Gradle derlemesi — ortamda `java`/`JAVA_HOME` bulunmadığı için çalıştırılamadı.
- [ ] Cihazda manga ve anime ilerleme alanına dokunup sayı düzenleme
- [ ] Takvim/Keşfet kartlarında çerçeveleri kontrol etme
- [ ] Her desteklenen hesapta bağlantısız Listem görünümü ve bağlan eylemi
- [ ] Bildirimlerde hesapsız toggle, hesap bağla/ayır, global Airing ve per-source planlama
- [ ] Vitrinde görsel ve başlık alanlarına tek dokunuşla doğru detayın açılması; çift dokunarak kopyalama
