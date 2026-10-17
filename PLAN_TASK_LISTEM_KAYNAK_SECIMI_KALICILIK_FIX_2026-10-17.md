# PLAN / TASK — Listem "Kütüphane Kaynağı Seç" Kalıcılığı Düzeltmesi

**Tarih:** 2026-10-17  
**Dal:** `arena/31914ab8-kitsugi-beta`  
**Uygulama commit'i:** `645a8e4`

## Amaç

Listem sayfasında "Kütüphane Kaynağı Seç" ile seçilen platform sekmesinin (Tümü / AniList / MyAnimeList / Simkl / Kitsu / Shikimori / Bangumi) uygulama kapatılıp yeniden açılmasında **aynen geri yüklenmesini** sağlamak. Kullanıcı son seçtiği kaynağı açılışta görmeye devam etmelidir; seçim hiçbir koşulda sessizce silinmemeli veya taşınmamalıdır.

## Kök nedenler

1. **Açılış yarışı (asıl hata):** `AppViewModel.loadFilters` çağrısı `AppRoot` içinde bir `LaunchedEffect(Unit)` içindeydi; yani **ilk kompozisyondan sonra** koşuyordu. Listem uygulama ilk açılışta ekrandaysa (boot sekmesi Listem) `HorizontalPager` `selectedTabIndex = 0` (varsayılan) ile kuruluyor ve `LaunchedEffect(tabPagerState.settledPage)` hemen `onTabIndexChange(0)` → `updateMyListTabIndex(context, 0)` çağrısıyla kayıtlı seçimi SharedPreferences'e **geri yazıyordu**. Sonuç: her kapanıp açılışta seçim "Tümü"'ye (0) dönüyordu.
2. **Koşulsuz eski sıra göçü:** `loadFilters` içindeki `migrateLegacyMyListTabIndex(storedTabIndex)` **her açılışta** koşulsuz uygulanıyordu. Bu göç yalnızca bir kez çalışmalıydı; her açılışta kayıtlı `tab_index` değerini bir sıra kaydırıyordu (0→1, 1→2, …, 5→0, 6/Bangumi→0). Boot sekmesi Listem değilse kullanıcı sonradan Listem'e girdiğinde "bir öncekinden farklı" bir sekme açılıyordu.

## Uygulanan görevler

- [x] `loadFilters` çağrısını `AppRoot`'ta `LaunchedEffect`'ten çıkarıp **ilk kompozisyondan önce senkron** (`remember(Unit)`) çalıştırmak; böylece pager ilk karede geri yüklenmiş `selectedTabIndex` ile kurulur.
- [x] `MyListScreen` pager `settledPage` etkisinin **ilk çalıştırmasını raporlamamasını** sağlamak (ilk an bir "değişiklik" değildir; kurulum değerinin geri yazılmasını engeller). Yalnızca sonraki gerçek kullanıcı kaydırmaları `updateMyListTabIndex` ile kaydedilir — savunma katmanı olarak yarış tamamen kapatılır.
- [x] `loadFilters` içindeki koşulsuz `migrateLegacyMyListTabIndex` göçünü kaldırmak; kayıtlı `tab_index` değerini doğrudan (0 = Tümü … 6 = Bangumi) geri yüklemek. Kayıtlar bug tarihinden beri zaten güncel sıraya göre yazılıyor.
- [x] Ölüm kodu haline gelen `migrateLegacyMyListTabIndex` fonksiyonunu ve `LEGACY_MY_LIST_ALL_TAB_INDEX` sabitini `MyListLibraryGrouping.kt`'den silmek.
- [x] Regresyon testi eklemek: `MyListTabRestoreTest` — her geçerli sekme indeksi taşınmadan ve prefs üzerine yazılmadan geri yüklenir (Bangumi 6 → 0 düşmesi kilitlenir), kapsam dışı değer kırpılır, taze kurulum varsayılanı kalıcılaşır.

## Değişen dosyalar

1. `app/src/main/java/com/kitsugi/animelist/AppRoot.kt`
2. `app/src/main/java/com/kitsugi/animelist/ui/app/AppViewModel.kt`
3. `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListScreen.kt`
4. `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListLibraryGrouping.kt`
5. `app/src/test/java/com/kitsugi/animelist/ui/app/MyListTabRestoreTest.kt` *(yeni)*
6. `PLAN_TASK_LISTEM_KAYNAK_SECIMI_KALICILIK_FIX_2026-10-17.md` *(bu dosya)*

## Kabul kriterleri

- [ ] Listem'de bir platform sekmesi seçilir, uygulama tamamen kapatılıp açılınca **aynı sekme** açılır (Tümü, Bangumi, MAL … hepsi).
- [ ] Pager ile parmakla kaydırarak sekme değiştirilince seçim kapanışta korunur.
- [ ] Açılışta Listem ilk karede açıldığında pager kayıtlı sayfada başlar; "0/Tümü"'ye geri yazma olmaz, sekmeler arasında açılış animasyonu/göç yoktur.
- [ ] `MyListTabRestoreTest` yeşil: `./gradlew testDebugUnitTest --tests "com.kitsugi.animelist.ui.app.MyListTabRestoreTest"`.

## Notlar / bilinçli takaslar

- Eski sıra göçü geri alındığı için (persistence v2.4.198, sıra düzenlemesi v2.4.195 — alan cihazlarda kayıtlar zaten yeni sıraya göre) çok eski bir "legacy" değer taşıyan cihaz varsa ilk açılışta sekmeyi bir kez elle seçmesi yeterlidir; sonrasında seçim **kalıcı** olur.
- Bu düzeltme `tab_index`'i paylaşan iki yüzeyi de kapsar: kaynak seçici hap butonu/modal ("Kütüphane Kaynağı Seç") ve pager ile kaydırma.
- Sandbox ortamında Android SDK/JDK bulunmadığından Gradle derlemesi ve testler orada koşulamadı; testler geliştirici makinede `./gradlew testDebugUnitTest` ile doğrulanmalıdır.
