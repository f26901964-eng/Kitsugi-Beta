# Plan / Görev — Kaynağa Özel Liste Eşleme, Bangumi Düzenleyicisi ve Profil Düzeltmeleri

**Tarih:** 2026-10-10  
**Durum:** Uygulama yapıldı; Gradle doğrulaması Java ortamı olmadığı için çalıştırılamadı.

## Kullanıcı bildirimleri

1. Profil favorilerindeki yukarı kaydırma düğmesi, sayfa tepesine dönerken alt gezinme çubuğunu geri göstermeli.
2. Bangumi detayındaki düzenleme akışı Bangumi kayıt/editörünü kullanmalı; Simkl kaydı Bangumi listesi gibi görünmemeli.
3. Liste üyeliği kaynağa özel olmalı. Başka bir servisteki aynı MAL/TMDB kimliği veya başlık, Bangumi Keşfet/arama/detay ekranında Bangumi listesi üyeliği sayılmamalı.
4. Profil favorilerinden açılan yetişkin içerik, detay yüklenirken poster blur'unu korumalı.

## Uygulananlar

- Provider takma adlarını kanonikleştiren ve kaynak eşitliğini eşleşme önkoşulu yapan `matchesInSource` / `firstMatchingInSource` yardımcıları eklendi. Bangumi ve Simkl birbirine eşitlenmez; Simkl kayıtlarında Simkl ID'si de doğrudan tanınır.
- Keşfet, arama, detay ve ekleme/tekrar kontrolü çağrıları kaynak kapsamlı eşlemeye geçirildi. Bangumi için `bgm` ve `bgm.tv` takma adları düzenleyicide Bangumi olarak çözülür.
- Profil favorilerindeki yukarı düğmesi ortak `onScrollReset` callback'ini hem animasyondan önce hem sonra çağırır.
- AniList profil favorilerinin `isAdult` değeri detay isteğine taşınır. Yüklenme animasyonundaki arka plan ve poster `KitsugiNsfwImage` üzerinden blur ayarını alır.
- Kaynak eşlemesi için birim testleri eklendi.

## Değişen dosyalar

- `app/src/main/java/com/kitsugi/animelist/AppRoot.kt`
- `app/src/main/java/com/kitsugi/animelist/AppRootDetailPages.kt`
- `app/src/main/java/com/kitsugi/animelist/AppRootTabPages.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiModels.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/app/AppDialogHost.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/app/AppViewModel.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiCinematicLoadingScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiEditMediaSheet.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/anime/AnimeScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/explore/ExploreMapping.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/manga/MangaScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/profile/AniListProfileContent.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/profile/KitsugiProfileScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/profile/KitsugiUserMediaListScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/profile/KitsugiUserProfileScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/profile/ProfileFavoritesListemStyle.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/tv/TvRootScreen.kt`
- `app/src/test/java/com/kitsugi/animelist/data/remote/MediaEntryMatchesTest.kt`
- `app/src/test/java/com/kitsugi/animelist/ui/screens/explore/ExploreMappingSourceScopeTest.kt`
- `PLAN_TASK_BANGUMI_SIMKL_KAYNAK_UYUMU_PROFIL_BLUR_2026-10-10.md` (bu görev kaydı)

## Doğrulama

- `git diff --check`: başarılı.
- `bash gradlew :app:testDebugUnitTest --no-daemon`: çalıştırılamadı; ortamda `java` komutu ve `JAVA_HOME` bulunmuyor.

## Manuel kontrol listesi

- [ ] Profil > AniList > Favoriler: hızlı yukarı düğmesi listeyi tepeye alırken alt çubuğu da gösteriyor.
- [ ] Bangumi Keşfet: aynı Simkl/TMDB kaydı Bangumi'de listeli rozeti/işlemi oluşturmuyor.
- [ ] Bangumi'de gerçekten listelenen öğe: Düzenle eylemi Bangumi kaynak editörünü açıyor.
- [ ] Arama, detay ilişkileri ve tam ekran ızgarada da kaynak dışı kayıtlar üyelik sayılmıyor.
- [ ] Blur ayarı açıkken Profil'den açılan yetişkin favorisinin yüklenme ekranındaki hem poster hem arka plan bulanık.
